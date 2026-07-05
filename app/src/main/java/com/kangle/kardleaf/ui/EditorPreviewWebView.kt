package com.kangle.kardleaf.ui

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
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kangle.kardleaf.R
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import kotlin.math.roundToInt

private const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
private const val PREVIEW_SESSION_TRACE_TAG = "KardLeafPreviewTrace"
private const val PREVIEW_TABLE_TRACE_TAG = "KardLeafPreviewTableTrace"
private const val USER_PERF_TRACE_TAG = "KardLeafUserPerf"
private val toggleTaskRegex = Regex("- \\[[ xX]\\]")
private data class PreviewRenderState(
    val contentLength: Int,
    val contentHash: Int,
    val isDark: Boolean,
    val sessionKeyHash: Int,
    val typographyHash: Int,
)

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
        view.post {
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
    onCheckboxToggled: (Int, Boolean) -> Unit,
    doubleTapIntervalMs: Int = 260,
    contentFontSizeSp: Float = 16f,
    contentLineHeightMultiplier: Float = 1.55f,
    contentLetterSpacingSp: Float = 0f,
    contentParagraphSpacingDp: Float = 8f,
    contentFontFamily: String = "system",
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
    val currentOnCheckboxToggled = rememberUpdatedState(onCheckboxToggled)
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

    fun WebView.applyPreviewTypography() {
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
        evaluateJavascript("applySearchHighlight(${JSONObject.quote(currentSearchQuery.value)})", null)
    }

    fun WebView.applyPreviewHeadingScroll() {
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
        val notifyRendered = Runnable {
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
                        postDelayed(notifyRendered, 120L)
                    }
                },
            )
        } else {
            postDelayed(notifyRendered, 700L)
        }
    }

    fun WebView.renderPreviewFromAndroid(
        isDarkPreview: Boolean,
        reason: String,
        token: Long,
    ) {
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
        evaluateJavascript(script) { result ->
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

                val previewWebView = this
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun getMarkdown(): String = contentRef.get()

                        @JavascriptInterface
                        fun onCheckboxToggled(
                            index: Int,
                            checked: Boolean,
                        ) {
                            Handler(Looper.getMainLooper()).post {
                                (previewWebView.getTag(R.id.preview_control_touch_tag) as? AtomicReference<Long>)
                                    ?.set(SystemClock.elapsedRealtime())
                                currentOnCheckboxToggled.value(index, checked)
                            }
                        }

                        @JavascriptInterface
                        fun onPreviewControlTouched() {
                            Handler(Looper.getMainLooper()).post {
                                (previewWebView.getTag(R.id.preview_control_touch_tag) as? AtomicReference<Long>)
                                    ?.set(SystemClock.elapsedRealtime())
                            }
                        }

                        @JavascriptInterface
                        fun onImageClicked(index: Int) {
                            Handler(Looper.getMainLooper()).post {
                                (previewWebView.getTag(R.id.preview_control_touch_tag) as? AtomicReference<Long>)
                                    ?.set(SystemClock.elapsedRealtime())
                                currentOnImageClicked.value(index)
                            }
                        }

                        @JavascriptInterface
                        fun copyCodeBlock(text: String) {
                            Handler(Looper.getMainLooper()).post {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("KardLeaf 代码块", text))
                            }
                        }
                    },
                    "Android",
                )

                val lastTapUpMs = AtomicReference(0L)
                val lastPreviewControlTouchMs = AtomicReference(0L)
                setTag(R.id.preview_control_touch_tag, lastPreviewControlTouchMs)
                val scrollPerfHandler = Handler(Looper.getMainLooper())
                var scrollPerfStartMs = 0L
                var scrollPerfLastMs = 0L
                var scrollPerfFrames = 0
                var scrollPerfSlowFrames = 0
                var scrollPerfMaxFrameMs = 0L
                var scrollPerfStartY = 0
                val scrollPerfSettleRunnable = Runnable {
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
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                            currentOnUserInteraction.value()
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            val now = SystemClock.elapsedRealtime()
                            val controlTouchedRecently = now - lastPreviewControlTouchMs.get() < currentDoubleTapIntervalMs.value + 220L
                            val previousTap = lastTapUpMs.get()
                            val isDoubleTap = previousTap > 0L && now - previousTap <= currentDoubleTapIntervalMs.value
                            if (isDoubleTap && !controlTouchedRecently) {
                                lastTapUpMs.set(0L)
                                val density = view.resources.displayMetrics.density.coerceAtLeast(1f)
                                val tapX = event.x / density
                                val tapY = event.y / density
                                (view as? WebView)?.evaluateJavascript(
                                    "getMarkdownOffsetAtPoint(${tapX}, ${tapY})",
                                ) { result ->
                                    currentOnDoubleTap.value(result?.toIntOrNull()?.takeIf { it >= 0 })
                                } ?: currentOnDoubleTap.value(null)
                                true
                            } else {
                                lastTapUpMs.set(if (controlTouchedRecently) 0L else now)
                                false
                            }
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            view.parent?.requestDisallowInterceptTouchEvent(false)
                            false
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
                            val url = request?.url?.toString() ?: return false
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                return true
                            }
                            return false
                        }
                    }

                setTag(R.id.preview_page_ready_tag, false)
                loadUrl("file:///android_asset/preview/preview.html?dark=$isDark")
            }
        },
        update = { view ->
            controller.attach(view)
            val previewState = PreviewRenderState(
                contentLength = content.length,
                contentHash = content.hashCode(),
                isDark = isDark,
                sessionKeyHash = sessionKey.hashCode(),
                typographyHash = currentTypographyCss.value.hashCode(),
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
                    view.post { view.applyPreviewSearch() }
                }
                if (lastHeadingToken != currentHeadingScrollToken.value) {
                    view.setTag(R.id.preview_heading_scroll_token_tag, currentHeadingScrollToken.value)
                    view.post { view.applyPreviewHeadingScroll() }
                }
            }
        },
    )
}
