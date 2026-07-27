package com.kangle.kardleaf.ui.editor.host

import com.kangle.kardleaf.ui.editor.api.EditorFastScrollMetrics
import com.kangle.kardleaf.data.utils.KardLeafLog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.kangle.kardleaf.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
private const val PREVIEW_SESSION_TRACE_TAG = "KardLeafPreviewTrace"
private const val PREVIEW_TABLE_TRACE_TAG = "KardLeafPreviewTableTrace"
private const val USER_PERF_TRACE_TAG = "KardLeafUserPerf"
private const val DOUBLE_TAP_TRACE_TAG = "KardLeafDoubleTapTrace"
private val toggleTaskRegex = Regex("- \\[[ xX]\\]")

private fun isAllowedPreviewMainFrameUri(uri: Uri): Boolean {
    if (uri.scheme != "file") return false
    if (!uri.authority.isNullOrEmpty() || uri.userInfo != null || uri.port != -1) return false
    if (uri.path != "/android_asset/preview/preview.html") return false
    if (uri.query.isNullOrEmpty()) return true
    return runCatching {
        uri.queryParameterNames == setOf("dark") &&
            uri.getQueryParameters("dark").singleOrNull() in setOf("true", "false")
    }.getOrDefault(false)
}

private fun handlePreviewMainFrameNavigation(
    context: Context,
    uri: Uri,
    onInternalLinkOpen: (String) -> Unit,
): Boolean {
    if (isAllowedPreviewMainFrameUri(uri)) return false
    when (uri.scheme?.lowercase()) {
        "http", "https" -> {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }.onFailure { error ->
                KardLeafLog.w(
                    PREVIEW_SESSION_TRACE_TAG,
                    "external navigation failed scheme=${uri.scheme.orEmpty()} host=${uri.host.orEmpty()} path=${uri.path.orEmpty()}",
                    error,
                )
            }
        }
        "kardleaf-wikilink" -> {
            val encodedTarget = buildString {
                append(uri.encodedSchemeSpecificPart.orEmpty())
                uri.encodedFragment?.let { append('#').append(it) }
            }
            runCatching { Uri.decode(encodedTarget) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let(onInternalLinkOpen)
        }
        else -> {
            KardLeafLog.w(
                PREVIEW_SESSION_TRACE_TAG,
                "event=navigation_blocked scheme=${uri.scheme.orEmpty()} host=${uri.host.orEmpty()} " +
                    "path=${uri.path.orEmpty()} reason=not_allowed",
            )
        }
    }
    return true
}

private class PreviewWebViewLifecycleState(
    val mainHandler: Handler,
    val bridge: PreviewJavascriptBridge,
) {
    @Volatile
    var released: Boolean = false
    var horizontalVelocityTracker: VelocityTracker? = null
}

private fun WebView.previewLifecycleState(): PreviewWebViewLifecycleState? =
    getTag(R.id.preview_lifecycle_state_tag) as? PreviewWebViewLifecycleState

private class PreviewJavascriptBridge(
    private val mainHandler: Handler,
    private val context: Context,
    private val contentProvider: () -> String,
    private val webViewProvider: () -> WebView?,
    private val onCheckboxToggled: (Int, Boolean) -> Unit,
    private val onImageClicked: (Int) -> Unit,
    private val onInternalLinkOpen: (String) -> Unit,
) {
    private val released = AtomicBoolean(false)
    private val releasedDropLogged = AtomicBoolean(false)

    fun dispose() {
        released.set(true)
    }

    private fun dropIfReleased(): Boolean {
        if (!released.get()) return false
        if (releasedDropLogged.compareAndSet(false, true)) {
            KardLeafLog.d(PREVIEW_SESSION_TRACE_TAG, "event=callback_dropped_released")
        }
        return true
    }

    private fun postIfActive(action: (WebView) -> Unit) {
        if (dropIfReleased()) return
        mainHandler.post {
            if (dropIfReleased()) return@post
            val webView = webViewProvider() ?: return@post
            action(webView)
        }
    }

    private fun markControlTouched(webView: WebView) {
        (webView.getTag(R.id.preview_control_touch_tag) as? AtomicReference<Long>)
            ?.set(SystemClock.elapsedRealtime())
    }

    @JavascriptInterface
    fun getMarkdown(): String = if (dropIfReleased()) "" else contentProvider()

    @JavascriptInterface
    fun onCheckboxToggled(index: Int, checked: Boolean) {
        postIfActive { webView ->
            markControlTouched(webView)
            onCheckboxToggled(index, checked)
        }
    }

    @JavascriptInterface
    fun onPreviewControlTouched() {
        postIfActive(::markControlTouched)
    }

    @JavascriptInterface
    fun onImageClicked(index: Int) {
        postIfActive { webView ->
            markControlTouched(webView)
            onImageClicked(index)
        }
    }

    @JavascriptInterface
    fun onWikilinkClicked(rawTarget: String?) {
        postIfActive { webView ->
            markControlTouched(webView)
            onInternalLinkOpen(rawTarget.orEmpty())
            KardLeafLog.d("KardLeafWikiLinkTrace", "preview click targetLen=${rawTarget?.length ?: 0}")
        }
    }

    @JavascriptInterface
    fun copyCodeBlock(text: String) {
        postIfActive {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("KardLeaf 代码块", text))
        }
    }
}

private data class PreviewRenderState(
    val contentLength: Int,
    val contentHash: Int,
    val isDark: Boolean,
    val sessionKeyHash: Int,
    val typographyHash: Int,
    val themeHash: Int,
)

private fun Color.toPreviewCssHex(): String =
    String.format(java.util.Locale.US, "#%06X", toArgb() and 0xFFFFFF)

private fun Color.toPreviewCssRgba(alpha: Float): String {
    val argb = toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgba($r, $g, $b, ${alpha.coerceIn(0f, 1f)})"
}

/**
 * Serializes the preview theme selection plus the current MaterialTheme colors for
 * `window.applyPreviewTheme` in preview.html. Named themes only need [themeId]; the
 * `app` colors drive the `follow_app` mode so the preview tracks every app theme
 * style/accent/background combination, mirroring the CodeMirror editor bridge.
 */
private fun buildPreviewThemePayload(
    themeId: String,
    isDark: Boolean,
    foreground: Color,
    muted: Color,
    border: Color,
    soft: Color,
    accent: Color,
): String =
    JSONObject()
        .put("theme", themeId)
        .put("dark", isDark)
        .put(
            "app",
            JSONObject()
                .put("fg", foreground.toPreviewCssHex())
                .put("muted", muted.toPreviewCssHex())
                .put("border", border.toPreviewCssRgba(0.72f))
                .put("soft", soft.toPreviewCssRgba(0.48f))
                .put("codeBg", soft.toPreviewCssRgba(0.82f))
                .put("quoteBg", soft.toPreviewCssRgba(0.32f))
                .put("imageBg", soft.toPreviewCssRgba(0.6f))
                .put("accent", accent.toPreviewCssHex()),
        )
        .toString()

private fun previewFontFamilyCss(fontFamily: String): String =
    when (fontFamily.trim().lowercase()) {
        "", "system" -> "sans-serif"
        else -> "\"${fontFamily.trim().replace("\"", "\\\"")}\""
    }

private fun previewTypographyCss(
    fontSizeSp: Float,
    lineHeight: Float,
    letterSpacingSp: Float,
    paragraphSpacingDp: Float,
    fontFamily: String,
): String {
    val safeFontSize = fontSizeSp.coerceIn(12f, 30f)
    val safeLineHeight = lineHeight.coerceIn(1f, 2.5f)
    val safeLetterSpacing = letterSpacingSp.coerceIn(-1f, 3f)
    val safeParagraphSpacing = paragraphSpacingDp.coerceIn(0f, 32f)
    val safeFontFamily = previewFontFamilyCss(fontFamily)
    return """
        body,#content{font-size:${safeFontSize}px;line-height:$safeLineHeight;letter-spacing:${safeLetterSpacing}px;font-family:$safeFontFamily;}
        p{margin:${(safeParagraphSpacing / 2f)}px 0 ${safeParagraphSpacing}px;}
        #content.large-plain-preview{font-family:$safeFontFamily;}
    """.trimIndent()
}

private fun previewUserPerfNoteSizeTier(length: Int): String = when {
    length < 10_000 -> "lt_1w"
    length < 50_000 -> "1w_5w"
    length < 100_000 -> "5w_10w"
    length < 1_000_000 -> "10w_100w"
    else -> "gte_100w"
}

class PreviewWebViewController {
    private var webView: WebView? = null

    fun attach(view: WebView) {
        webView = view
    }

    fun detach(view: WebView) {
        if (webView === view) {
            webView = null
        }
    }

    fun getFastScrollMetrics(): EditorFastScrollMetrics {
        val view = webView ?: return EditorFastScrollMetrics()
        val maxScrollY = view.maxPreviewScrollY()
        if (maxScrollY <= 0) return EditorFastScrollMetrics()
        val contentHeight = view.height + maxScrollY
        return EditorFastScrollMetrics(
            canScroll = true,
            ratio = (view.scrollY.toFloat() / maxScrollY).coerceIn(0f, 1f),
            thumbFraction = (view.height.toFloat() / contentHeight).coerceIn(0f, 1f),
        )
    }

    fun fastScrollToRatio(ratio: Float) {
        val view = webView ?: return
        val maxScrollY = view.maxPreviewScrollY()
        if (maxScrollY <= 0) return
        val targetScrollY = (ratio.coerceIn(0f, 1f) * maxScrollY).roundToInt()
        view.scrollTo(0, targetScrollY.coerceIn(0, maxScrollY))
    }

    fun scrollToSearchOrdinal(ordinal: Int) {
        val view = webView ?: return
        val index = (ordinal - 1).coerceAtLeast(0)
        val lifecycleState = view.previewLifecycleState() ?: return
        view.post {
            if (lifecycleState.released) return@post
            view.evaluateJavascript("scrollToSearchHighlight($index)", null)
        }
    }

    private fun WebView.maxPreviewScrollY(): Int =
        (contentHeight * scale - height).roundToInt().coerceAtLeast(0)
}

/** Toggle a markdown checkbox at [index] to [checked] state. */
fun toggleTask(
    markdown: String,
    index: Int,
    checked: Boolean,
): String {
    var matchIndex = 0
    return toggleTaskRegex.replace(markdown) { matchResult ->
        if (matchIndex++ == index) {
            if (checked) "- [x]" else "- [ ]"
        } else {
            matchResult.value
        }
    }
}

/**
 * WebView-based rendered Markdown preview.
 *
 * Two details are intentional here:
 * 1. The parent Compose side-panel gesture is kept on narrow edge handles only, so
 *    normal vertical WebView scrolling is not intercepted by the editor shell.
 * 2. The tag stores only a compact render state instead of the full Markdown string,
 *    avoiding an extra long-string copy and an O(n) Pair equality check on every update.
 */
@Composable
fun PreviewWebView(
    content: String,
    sessionKey: String,
    isDark: Boolean,
    controller: PreviewWebViewController,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    headingScrollText: String = "",
    headingScrollLevel: Int = 0,
    headingScrollToken: Int = 0,
    onDoubleTap: (Int?) -> Unit = {},
    onUserInteraction: () -> Unit = {},
    onScrollRatioChanged: (Float) -> Unit = {},
    onFastScrollSourceScrolled: () -> Unit = {},
    onContentRendered: (Int, Int) -> Unit = { _, _ -> },
    onImageClicked: (Int) -> Unit = {},
    onInternalLinkOpen: (String) -> Unit = {},
    onCheckboxToggled: (Int, Boolean) -> Unit,
    onHorizontalPagerDragStart: (() -> Unit)? = null,
    onHorizontalPagerDrag: ((deltaX: Float) -> Unit)? = null,
    onHorizontalPagerDragEnd: ((totalDeltaX: Float, velocityX: Float) -> Unit)? = null,
    onHorizontalPagerDragCancel: (() -> Unit)? = null,
    doubleTapIntervalMs: Int = 260,
    contentFontSizeSp: Float = 16f,
    contentLineHeightMultiplier: Float = 1.55f,
    contentLetterSpacingSp: Float = 0f,
    contentParagraphSpacingDp: Float = 8f,
    contentFontFamily: String = "system",
    previewTheme: String = "follow_app",
) {
    val contentRef = remember { AtomicReference(content) }
    contentRef.set(content)
    val previewTokenRef = remember { AtomicLong(1L) }
    val sessionKeyRef = remember { AtomicReference(sessionKey) }
    val currentOnDoubleTap = rememberUpdatedState(onDoubleTap)
    val currentOnUserInteraction = rememberUpdatedState(onUserInteraction)
    val currentOnScrollRatioChanged = rememberUpdatedState(onScrollRatioChanged)
    val currentOnFastScrollSourceScrolled = rememberUpdatedState(onFastScrollSourceScrolled)
    val currentOnContentRendered = rememberUpdatedState(onContentRendered)
    val currentOnImageClicked = rememberUpdatedState(onImageClicked)
    val currentOnInternalLinkOpen = rememberUpdatedState(onInternalLinkOpen)
    val currentOnCheckboxToggled = rememberUpdatedState(onCheckboxToggled)
    val currentOnHorizontalPagerDragStart = rememberUpdatedState(onHorizontalPagerDragStart)
    val currentOnHorizontalPagerDrag = rememberUpdatedState(onHorizontalPagerDrag)
    val currentOnHorizontalPagerDragEnd = rememberUpdatedState(onHorizontalPagerDragEnd)
    val currentOnHorizontalPagerDragCancel = rememberUpdatedState(onHorizontalPagerDragCancel)
    val currentDoubleTapIntervalMs = rememberUpdatedState(doubleTapIntervalMs.coerceIn(120, 600))
    val currentSearchQuery = rememberUpdatedState(searchQuery)
    val currentHeadingScrollText = rememberUpdatedState(headingScrollText)
    val currentHeadingScrollLevel = rememberUpdatedState(headingScrollLevel)
    val currentHeadingScrollToken = rememberUpdatedState(headingScrollToken)
    val currentTypographyCss = rememberUpdatedState(
        previewTypographyCss(
            contentFontSizeSp,
            contentLineHeightMultiplier,
            contentLetterSpacingSp,
            contentParagraphSpacingDp,
            contentFontFamily,
        ),
    )
    val colorScheme = MaterialTheme.colorScheme
    val currentThemePayload = rememberUpdatedState(
        buildPreviewThemePayload(
            themeId = previewTheme,
            isDark = isDark,
            foreground = colorScheme.onBackground,
            muted = colorScheme.onSurfaceVariant,
            border = colorScheme.outlineVariant,
            soft = colorScheme.surfaceVariant,
            accent = colorScheme.primary,
        ),
    )

    fun WebView.applyPreviewThemeColors() {
        val lifecycleState = previewLifecycleState() ?: return
        if (lifecycleState.released) return
        evaluateJavascript(
            "if (window.applyPreviewTheme) { window.applyPreviewTheme(${JSONObject.quote(currentThemePayload.value)}); } else { 'missing'; }",
            null,
        )
    }

    fun WebView.applyPreviewTypography() {
        val lifecycleState = previewLifecycleState() ?: return
        if (lifecycleState.released) return
        evaluateJavascript(
            """
                (function() {
                    var style = document.getElementById('kl-preview-typography');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'kl-preview-typography';
                        document.head.appendChild(style);
                    }
                    style.textContent = ${JSONObject.quote(currentTypographyCss.value)};
                    return 'ok';
                })();
            """.trimIndent(),
            null,
        )
    }

    fun WebView.applyPreviewSearch() {
        val lifecycleState = previewLifecycleState() ?: return
        if (lifecycleState.released) return
        evaluateJavascript("applySearchHighlight(${JSONObject.quote(currentSearchQuery.value)})", null)
    }

    fun WebView.applyPreviewHeadingScroll() {
        val lifecycleState = previewLifecycleState() ?: return
        if (lifecycleState.released) return
        if (currentHeadingScrollToken.value > 0 && currentHeadingScrollText.value.isNotBlank()) {
            evaluateJavascript(
                "scrollToHeading(${JSONObject.quote(currentHeadingScrollText.value)}, ${currentHeadingScrollLevel.value})",
                null,
            )
        }
    }

    fun WebView.notifyPreviewContentRendered(
        token: Long,
        renderedLength: Int,
        renderedHash: Int,
        renderStartMs: Long,
    ) {
        val lifecycleState = previewLifecycleState() ?: return
        if (lifecycleState.released) return
        val notifyRendered = Runnable {
            if (lifecycleState.released) return@Runnable
            if (previewTokenRef.get() != token) {
                KardLeafLog.d(
                    PREVIEW_SESSION_TRACE_TAG,
                    "previewUpdate dropped old token=$token current=${previewTokenRef.get()} len=$renderedLength",
                )
                return@Runnable
            }
            currentOnContentRendered.value(renderedLength, renderedHash)
            KardLeafLog.d(
                PREVIEW_SESSION_TRACE_TAG,
                "previewRender done token=$token len=$renderedLength cost=${SystemClock.elapsedRealtime() - renderStartMs}ms",
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            postVisualStateCallback(
                SystemClock.elapsedRealtimeNanos(),
                object : WebView.VisualStateCallback() {
                    override fun onComplete(requestId: Long) {
                        if (lifecycleState.released) return
                        lifecycleState.mainHandler.postDelayed(notifyRendered, 120L)
                    }
                },
            )
        } else {
            lifecycleState.mainHandler.postDelayed(notifyRendered, 700L)
        }
    }

    fun WebView.renderPreviewFromAndroid(
        isDarkPreview: Boolean,
        reason: String,
        token: Long,
    ) {
        val lifecycleState = previewLifecycleState() ?: return
        if (lifecycleState.released) return
        val pageReady = (getTag(R.id.preview_page_ready_tag) as? Boolean) == true
        val renderedContent = contentRef.get()
        val contentLength = renderedContent.length
        if (!pageReady) {
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "webview update deferred reason=$reason pageReady=false len=$contentLength",
            )
            KardLeafLog.d(
                PREVIEW_SESSION_TRACE_TAG,
                "previewUpdate deferred token=$token reason=$reason len=$contentLength",
            )
            return
        }
        val script = """
            (function() {
                if ($token !== ${previewTokenRef.get()}) {
                    return 'stale_android';
                }
                if (typeof window.updateContentFromAndroid !== 'function') {
                    console.log('[KardLeafPreview] update skipped not_ready reason=' + ${JSONObject.quote(reason)});
                    return 'not_ready';
                }
                return window.updateContentFromAndroid($isDarkPreview, $token);
            })();
        """.trimIndent()
        val renderStartMs = SystemClock.elapsedRealtime()
        applyPreviewTypography()
        applyPreviewThemeColors()
        evaluateJavascript(script) { result ->
            if (lifecycleState.released) return@evaluateJavascript
            if (previewTokenRef.get() != token) {
                KardLeafLog.d(
                    PREVIEW_SESSION_TRACE_TAG,
                    "previewUpdate dropped old token=$token current=${previewTokenRef.get()} len=$contentLength",
                )
                return@evaluateJavascript
            }
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "webview updateContent done reason=$reason result=$result len=${contentRef.get().length} hash=${contentRef.get().hashCode()}",
            )
            applyPreviewSearch()
            applyPreviewHeadingScroll()
            notifyPreviewContentRendered(token, contentLength, renderedContent.hashCode(), renderStartMs)
        }
    }

    fun WebView.clearPreviewForNewSession(token: Long) {
        val lifecycleState = previewLifecycleState() ?: return
        if (lifecycleState.released) return
        evaluateJavascript(
            "if (window.clearContentFromAndroid) { window.clearContentFromAndroid($token); 'ok'; } else { 'missing'; }",
            null,
        )
    }

    fun WebView.cancelPendingPreviewRender(): Int {
        val pending = getTag(R.id.preview_pending_render_tag) as? Runnable ?: return 0
        removeCallbacks(pending)
        setTag(R.id.preview_pending_render_tag, null)
        return 1
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "webview factory create")
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(0)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                isNestedScrollingEnabled = true
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                isFocusable = true
                isFocusableInTouchMode = true

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.loadsImagesAutomatically = true
                settings.defaultTextEncodingName = "utf-8"
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.textZoom = 100
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)

                val previewWebView = this
                val mainHandler = Handler(Looper.getMainLooper())
                val bridge = PreviewJavascriptBridge(
                    mainHandler = mainHandler,
                    context = context.applicationContext,
                    contentProvider = { contentRef.get() },
                    webViewProvider = { previewWebView },
                    onCheckboxToggled = { index, checked -> currentOnCheckboxToggled.value(index, checked) },
                    onImageClicked = { index -> currentOnImageClicked.value(index) },
                    onInternalLinkOpen = { target -> currentOnInternalLinkOpen.value(target) },
                )
                val lifecycleState = PreviewWebViewLifecycleState(mainHandler, bridge)
                setTag(R.id.preview_lifecycle_state_tag, lifecycleState)
                addJavascriptInterface(bridge, "Android")

                val lastTapUpMs = AtomicReference(0L)
                val lastPreviewControlTouchMs = AtomicReference(0L)
                var gestureDownX = 0f
                var gestureDownY = 0f
                var gestureLastX = 0f
                var horizontalPagerDragActive = false
                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                setTag(R.id.preview_control_touch_tag, lastPreviewControlTouchMs)
                val scrollPerfHandler = lifecycleState.mainHandler
                var scrollPerfStartMs = 0L
                var scrollPerfLastMs = 0L
                var scrollPerfFrames = 0
                var scrollPerfSlowFrames = 0
                var scrollPerfMaxFrameMs = 0L
                var scrollPerfStartY = 0
                val scrollPerfSettleRunnable = Runnable {
                    if (lifecycleState.released) return@Runnable
                    if (scrollPerfStartMs > 0L) {
                        val elapsed = (scrollPerfLastMs - scrollPerfStartMs).coerceAtLeast(0L)
                        val avgFrame = if (scrollPerfFrames > 0) elapsed.toFloat() / scrollPerfFrames else 0f
                        val contentLength = contentRef.get().length
                        val maxScrollY = (previewWebView.contentHeight * previewWebView.scale - previewWebView.height).roundToInt().coerceAtLeast(0)
                        val endScrollY = previewWebView.scrollY
                        val deltaPx = kotlin.math.abs(endScrollY - scrollPerfStartY)
                        val msPerPx = if (deltaPx > 0) elapsed.toFloat() / deltaPx else 0f
                        val smooth = scrollPerfSlowFrames == 0 && scrollPerfMaxFrameMs <= 32L
                        KardLeafLog.d(
                            USER_PERF_TRACE_TAG,
                            "editorScroll humanSettled mode=markdownPreview elapsed=${elapsed}ms " +
                                "frames=$scrollPerfFrames slowFrames=$scrollPerfSlowFrames " +
                                "maxFrame=${scrollPerfMaxFrameMs}ms avgFrame=${String.format("%.1f", avgFrame)}ms " +
                                "smooth=$smooth contentLen=$contentLength sizeTier=${previewUserPerfNoteSizeTier(contentLength)} " +
                                "fromY=$scrollPerfStartY toY=$endScrollY deltaPx=$deltaPx msPerPx=${String.format("%.3f", msPerPx)} " +
                                "maxScrollY=$maxScrollY",
                        )
                        scrollPerfStartMs = 0L
                        scrollPerfLastMs = 0L
                        scrollPerfFrames = 0
                        scrollPerfSlowFrames = 0
                        scrollPerfMaxFrameMs = 0L
                        scrollPerfStartY = 0
                    }
                }
                setOnScrollChangeListener { view, _, scrollY, _, _ ->
                    if (lifecycleState.released) return@setOnScrollChangeListener
                    val webView = view as? WebView ?: return@setOnScrollChangeListener
                    val now = SystemClock.elapsedRealtime()
                    if (scrollPerfStartMs <= 0L) {
                        val contentLength = contentRef.get().length
                        scrollPerfStartMs = now
                        scrollPerfLastMs = now
                        scrollPerfFrames = 0
                        scrollPerfSlowFrames = 0
                        scrollPerfMaxFrameMs = 0L
                        scrollPerfStartY = webView.scrollY
                        KardLeafLog.d(
                            USER_PERF_TRACE_TAG,
                            "editorScroll humanStart mode=markdownPreview contentLen=$contentLength " +
                                "sizeTier=${previewUserPerfNoteSizeTier(contentLength)}",
                        )
                    } else {
                        val frameMs = now - scrollPerfLastMs
                        if (frameMs > 0L) {
                            scrollPerfFrames++
                            scrollPerfMaxFrameMs = maxOf(scrollPerfMaxFrameMs, frameMs)
                            if (frameMs > 32L) scrollPerfSlowFrames++
                        }
                        scrollPerfLastMs = now
                    }
                    scrollPerfHandler.removeCallbacks(scrollPerfSettleRunnable)
                    scrollPerfHandler.postDelayed(scrollPerfSettleRunnable, 180L)
                    val maxScrollY = (webView.contentHeight * webView.scale - webView.height).coerceAtLeast(1f)
                    currentOnScrollRatioChanged.value((scrollY / maxScrollY).coerceIn(0f, 1f))
                    currentOnFastScrollSourceScrolled.value()
                }

                setOnTouchListener { view, event ->
                    if (lifecycleState.released) return@setOnTouchListener false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            val now = SystemClock.elapsedRealtime()
                            val previousTap = lastTapUpMs.get()
                            KardLeafLog.d(
                                DOUBLE_TAP_TRACE_TAG,
                                "down sincePreviousUp=${if (previousTap > 0L) now - previousTap else -1L}ms contentLen=${contentRef.get().length}",
                            )
                            gestureDownX = event.x
                            gestureDownY = event.y
                            gestureLastX = event.x
                            horizontalPagerDragActive = false
                            lifecycleState.horizontalVelocityTracker?.recycle()
                            lifecycleState.horizontalVelocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                            currentOnUserInteraction.value()
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }

                        MotionEvent.ACTION_MOVE -> {
                            currentOnUserInteraction.value()
                            lifecycleState.horizontalVelocityTracker?.addMovement(event)
                            val horizontalDrag = currentOnHorizontalPagerDrag.value
                            if (horizontalDrag != null) {
                                val totalDeltaX = event.x - gestureDownX
                                val totalDeltaY = event.y - gestureDownY
                                if (!horizontalPagerDragActive &&
                                    abs(totalDeltaX) > touchSlop &&
                                    abs(totalDeltaX) > abs(totalDeltaY)
                                ) {
                                    horizontalPagerDragActive = true
                                    gestureLastX = gestureDownX
                                    lastTapUpMs.set(0L)
                                    currentOnHorizontalPagerDragStart.value?.invoke()
                                    KardLeafLog.d(
                                        "KardLeafPreviewSwipe",
                                        "dragStart direction=${if (totalDeltaX < 0f) "left" else "right"} " +
                                            "dx=${totalDeltaX.roundToInt()} dy=${totalDeltaY.roundToInt()}",
                                    )
                                }
                                if (horizontalPagerDragActive) {
                                    val deltaX = event.x - gestureLastX
                                    gestureLastX = event.x
                                    if (deltaX != 0f) horizontalDrag(deltaX)
                                    true
                                } else {
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                    false
                                }
                            } else {
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                                false
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            lifecycleState.horizontalVelocityTracker?.addMovement(event)
                            if (horizontalPagerDragActive) {
                                lifecycleState.horizontalVelocityTracker?.computeCurrentVelocity(1000)
                                val totalDeltaX = event.x - gestureDownX
                                val velocityX = lifecycleState.horizontalVelocityTracker?.xVelocity ?: 0f
                                currentOnHorizontalPagerDragEnd.value?.invoke(totalDeltaX, velocityX)
                                KardLeafLog.d(
                                    "KardLeafPreviewSwipe",
                                    "dragEnd direction=${if (totalDeltaX < 0f) "left" else "right"} " +
                                        "dx=${totalDeltaX.roundToInt()} velocityX=${velocityX.roundToInt()}",
                                )
                                horizontalPagerDragActive = false
                                lifecycleState.horizontalVelocityTracker?.recycle()
                                lifecycleState.horizontalVelocityTracker = null
                                lastTapUpMs.set(0L)
                                true
                            } else {
                                lifecycleState.horizontalVelocityTracker?.recycle()
                                lifecycleState.horizontalVelocityTracker = null
                                val now = SystemClock.elapsedRealtime()
                                val controlTouchedRecently = now - lastPreviewControlTouchMs.get() < currentDoubleTapIntervalMs.value + 220L
                                val previousTap = lastTapUpMs.get()
                                val isDoubleTap = previousTap > 0L && now - previousTap <= currentDoubleTapIntervalMs.value
                                if (isDoubleTap && !controlTouchedRecently) {
                                    lastTapUpMs.set(0L)
                                    KardLeafLog.d(
                                        DOUBLE_TAP_TRACE_TAG,
                                        "recognized interval=${now - previousTap}ms contentLen=${contentRef.get().length}",
                                    )
                                    val density = view.resources.displayMetrics.density.coerceAtLeast(1f)
                                    val tapX = event.x / density
                                    val tapY = event.y / density
                                    val lookupStart = SystemClock.elapsedRealtime()
                                    (view as? WebView)?.evaluateJavascript(
                                        "getMarkdownOffsetAtPoint(${tapX}, ${tapY})",
                                    ) { result ->
                                        if (lifecycleState.released) return@evaluateJavascript
                                        KardLeafLog.d(
                                            DOUBLE_TAP_TRACE_TAG,
                                            "offsetReady elapsed=${SystemClock.elapsedRealtime() - lookupStart}ms valid=${result?.toIntOrNull()?.let { it >= 0 } == true}",
                                        )
                                        currentOnDoubleTap.value(result?.toIntOrNull()?.takeIf { it >= 0 })
                                    } ?: currentOnDoubleTap.value(null)
                                    true
                                } else {
                                    lastTapUpMs.set(if (controlTouchedRecently) 0L else now)
                                    KardLeafLog.d(
                                        DOUBLE_TAP_TRACE_TAG,
                                        "firstUp accepted=${!controlTouchedRecently} contentLen=${contentRef.get().length}",
                                    )
                                    false
                        }
                    }
                }
                        MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            val wasHorizontalPagerDragActive = horizontalPagerDragActive
                            if (wasHorizontalPagerDragActive) {
                                currentOnHorizontalPagerDragCancel.value?.invoke()
                            }
                            horizontalPagerDragActive = false
                            lifecycleState.horizontalVelocityTracker?.recycle()
                            lifecycleState.horizontalVelocityTracker = null
                            wasHorizontalPagerDragActive
                        }
                        else -> false
                    }
                }

                webChromeClient =
                    object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            val message = consoleMessage?.message().orEmpty()
                            if (message.contains("KardLeafPreviewTableTrace")) {
                                KardLeafLog.d(
                                    PREVIEW_TABLE_TRACE_TAG,
                                    "webview console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafPreview") || message.contains("Preview")) {
                                KardLeafLog.d(
                                    LARGE_NOTE_OPEN_TRACE_TAG,
                                    "webview console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }

                webViewClient =
                    object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?,
                            url: String?,
                            favicon: Bitmap?,
                        ) {
                            if (lifecycleState.released) return
                            view?.setTag(R.id.preview_page_ready_tag, false)
                            KardLeafLog.d(
                                LARGE_NOTE_OPEN_TRACE_TAG,
                                "webview page started url=$url contentLen=${contentRef.get().length}",
                            )
                        }

                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            if (lifecycleState.released) return
                            val webView = view ?: return
                            webView.setTag(R.id.preview_page_ready_tag, true)
                            KardLeafLog.d(
                                LARGE_NOTE_OPEN_TRACE_TAG,
                                "webview page finished url=$url contentLen=${contentRef.get().length} dark=$isDark",
                            )
                            val token = previewTokenRef.get()
                            webView.applyPreviewTypography()
                            KardLeafLog.d(
                                PREVIEW_SESSION_TRACE_TAG,
                                "previewReady flush token=$token len=${contentRef.get().length}",
                            )
                            webView.renderPreviewFromAndroid(isDark, "pageFinished", token)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            if (lifecycleState.released) return true
                            if (request?.isForMainFrame == false) return true
                            val uri = request?.url ?: return false
                            return handlePreviewMainFrameNavigation(context, uri) { target ->
                                currentOnInternalLinkOpen.value(target)
                            }
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (lifecycleState.released) return true
                            val uri = url?.let(Uri::parse) ?: return false
                            return handlePreviewMainFrameNavigation(context, uri) { target ->
                                currentOnInternalLinkOpen.value(target)
                            }
                        }
                    }

                setTag(R.id.preview_page_ready_tag, false)
                loadUrl("file:///android_asset/preview/preview.html?dark=$isDark")
            }
        },
        update = { view ->
            val lifecycleState = view.previewLifecycleState() ?: return@AndroidView
            if (lifecycleState.released) return@AndroidView
            controller.attach(view)
            val previewState = PreviewRenderState(
                contentLength = content.length,
                contentHash = content.hashCode(),
                isDark = isDark,
                sessionKeyHash = sessionKey.hashCode(),
                typographyHash = currentTypographyCss.value.hashCode(),
                themeHash = currentThemePayload.value.hashCode(),
            )
            val lastSearchQuery = view.getTag(R.id.preview_search_query_tag) as? String
            val lastHeadingToken = view.getTag(R.id.preview_heading_scroll_token_tag) as? Int ?: 0
            if (sessionKeyRef.get() != sessionKey) {
                val oldLen = (view.tag as? PreviewRenderState)?.contentLength ?: contentRef.get().length
                sessionKeyRef.set(sessionKey)
                val token = previewTokenRef.incrementAndGet()
                val removed = view.cancelPendingPreviewRender()
                contentRef.set("")
                view.tag = null
                KardLeafLog.d(
                    PREVIEW_SESSION_TRACE_TAG,
                    "previewPending cleared reason=noteChanged oldLen=$oldLen removed=$removed",
                )
                KardLeafLog.d(
                    PREVIEW_SESSION_TRACE_TAG,
                    "previewSession new noteIdHash=${sessionKey.hashCode()} token=$token",
                )
                if ((view.getTag(R.id.preview_page_ready_tag) as? Boolean) == true) {
                    view.clearPreviewForNewSession(token)
                }
            }
            if (view.tag != previewState) {
                val token = previewTokenRef.incrementAndGet()
                KardLeafLog.d(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "webview update content changed len=${content.length} hash=${content.hashCode()} dark=$isDark " +
                        "lastState=${view.tag}",
                )
                KardLeafLog.d(
                    PREVIEW_SESSION_TRACE_TAG,
                    "previewUpdate accepted token=$token len=${content.length}",
                )
                view.tag = previewState
                contentRef.set(content)
                view.cancelPendingPreviewRender()
                val pendingRender = Runnable {
                    if (lifecycleState.released) return@Runnable
                    view.setTag(R.id.preview_pending_render_tag, null)
                    if (previewTokenRef.get() != token) {
                        KardLeafLog.d(
                            PREVIEW_SESSION_TRACE_TAG,
                            "previewUpdate dropped old token=$token current=${previewTokenRef.get()} len=${content.length}",
                        )
                        return@Runnable
                    }
                    view.renderPreviewFromAndroid(isDark, "composeUpdate", token)
                    view.setTag(R.id.preview_search_query_tag, currentSearchQuery.value)
                    view.setTag(R.id.preview_heading_scroll_token_tag, currentHeadingScrollToken.value)
                }
                view.setTag(R.id.preview_pending_render_tag, pendingRender)
                view.post(pendingRender)
            } else {
                if (lastSearchQuery != currentSearchQuery.value) {
                    view.setTag(R.id.preview_search_query_tag, currentSearchQuery.value)
                    view.post {
                        if (lifecycleState.released) return@post
                        view.applyPreviewSearch()
                    }
                }
                if (lastHeadingToken != currentHeadingScrollToken.value) {
                    view.setTag(R.id.preview_heading_scroll_token_tag, currentHeadingScrollToken.value)
                    view.post {
                        if (lifecycleState.released) return@post
                        view.applyPreviewHeadingScroll()
                    }
                }
            }
        },
        onRelease = { view ->
            val lifecycleState = view.previewLifecycleState() ?: return@AndroidView
            if (lifecycleState.released) return@AndroidView
            KardLeafLog.d(PREVIEW_SESSION_TRACE_TAG, "event=release_start")
            lifecycleState.released = true
            lifecycleState.bridge.dispose()
            lifecycleState.mainHandler.removeCallbacksAndMessages(null)
            controller.detach(view)
            view.cancelPendingPreviewRender()
            view.setTag(R.id.preview_search_query_tag, null)
            view.setTag(R.id.preview_heading_scroll_token_tag, null)
            view.setTag(R.id.preview_page_ready_tag, false)
            view.setOnScrollChangeListener(null)
            view.setOnTouchListener(null)
            lifecycleState.horizontalVelocityTracker?.recycle()
            lifecycleState.horizontalVelocityTracker = null
            view.stopLoading()
            view.removeJavascriptInterface("Android")
            view.webChromeClient = null
            view.webViewClient = WebViewClient()
            view.setTag(R.id.preview_lifecycle_state_tag, null)
            view.destroy()
            KardLeafLog.d(PREVIEW_SESSION_TRACE_TAG, "event=release_done")
        },
    )
}
