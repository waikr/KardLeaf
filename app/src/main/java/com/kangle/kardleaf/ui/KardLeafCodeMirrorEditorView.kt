package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.utils.KardLeafLog
import android.annotation.SuppressLint
import android.graphics.Rect
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val CODEMIRROR_TRACE_TAG = "KardLeafCodeMirror"
private const val CODEMIRROR_JS_TRACE_TAG = "KardLeafCM6"
private const val CODEMIRROR_BRIDGE_TRACE_TAG = "KardLeafCM6Bridge"
private const val CODEMIRROR_PERF_TRACE_TAG = "KardLeafCM6Perf"
private const val CODEMIRROR_SCROLL_TRACE_TAG = "KardLeafCM6Scroll"
private const val CODEMIRROR_INPUT_TRACE_TAG = "KardLeafCM6Input"
private const val CODEMIRROR_IMAGE_TRACE_TAG = "KardLeafCM6Image"
private const val CODEMIRROR_DEBUG_TRACE_TAG = "KardLeafCM6Trace"
private const val CODEMIRROR_TABLE_TRACE_TAG = "KardLeafCM6TableTrace"
private const val CODEMIRROR_IME_TRACE_TAG = "KardLeafCM6ImeTrace"
private const val USER_PERF_TRACE_TAG = "KardLeafUserPerf"
private val CODEMIRROR_TABLE_TRACE_ENABLED: Boolean
    get() = KardLeafLog.isEnabled(CODEMIRROR_TABLE_TRACE_TAG)
private val CODEMIRROR_IME_DEEP_TRACE_ENABLED: Boolean
    get() = KardLeafLog.isEnabled(CODEMIRROR_IME_TRACE_TAG)
private const val CODEMIRROR_ASSET_BASE_URL = "file:///android_asset/codemirror-editor/index.html"
private val CODEMIRROR_ASSET_URL =
    if (CODEMIRROR_TABLE_TRACE_ENABLED) "$CODEMIRROR_ASSET_BASE_URL?cmTrace=1" else CODEMIRROR_ASSET_BASE_URL
private const val CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT = 200_000
private const val CODEMIRROR_IMAGE_PREVIEW_MAX_COUNT = 24
private const val CODEMIRROR_IMAGE_PREVIEW_MAX_TOTAL_CHARS = 2_000_000

private fun Color.toCssHex(): String =
    String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)

private fun Color.toCssRgba(alpha: Float): String {
    val argb = toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return "rgba($r, $g, $b, ${alpha.coerceIn(0f, 1f)})"
}

private fun buildCodeMirrorThemeColorsScript(
    background: Color,
    foreground: Color,
    muted: Color,
    primary: Color,
    border: Color,
    soft: Color,
): String {
    val payload = JSONObject()
        .put("background", background.toCssHex())
        .put("foreground", foreground.toCssHex())
        .put("muted", muted.toCssHex())
        .put("border", border.toCssRgba(0.72f))
        .put("soft", soft.toCssRgba(0.48f))
        .put("selection", primary.toCssRgba(0.24f))
        .put("codeBackground", soft.toCssRgba(0.82f))
        .put("heading", foreground.toCssHex())
        .put("link", primary.toCssHex())
    return "if (window.KardLeafEditor && window.KardLeafEditor.setThemeColors) { " +
        "window.KardLeafEditor.setThemeColors($payload); 'ok'; " +
        "} else { 'missing'; }"
}

data class KardLeafCodeMirrorImage(
    val reference: String,
    val dataUri: String,
)

private object KardLeafCodeMirrorPayloadStore {
    private const val MAX_PAYLOADS = 32
    private const val HARD_MAX_PAYLOADS = 64
    private const val MIN_TRIM_AGE_MS = 10_000L
    private const val MAX_PAYLOAD_AGE_MS = 120_000L

    private data class Entry(
        val payload: String,
        val createdAtMs: Long,
    )

    private val payloads = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val serial = AtomicLong(0L)

    @Synchronized
    fun put(payload: String): String {
        val now = SystemClock.elapsedRealtime()
        pruneExpiredLocked(now)
        val token = "${SystemClock.elapsedRealtimeNanos()}-${serial.incrementAndGet()}"
        payloads[token] = Entry(payload, now)
        trimLocked(now)
        KardLeafLog.d(
            CODEMIRROR_BRIDGE_TRACE_TAG,
            "payload put token=${token.take(12)} len=${payload.length} pending=${payloads.size}",
        )
        return token
    }

    @Synchronized
    fun consume(token: String?): String? {
        val now = SystemClock.elapsedRealtime()
        pruneExpiredLocked(now)
        if (token.isNullOrBlank()) {
            KardLeafLog.w(CODEMIRROR_BRIDGE_TRACE_TAG, "payload missing reason=blank-token pending=${payloads.size}")
            return null
        }
        val entry = payloads.remove(token)
        if (entry == null) {
            KardLeafLog.w(CODEMIRROR_BRIDGE_TRACE_TAG, "payload missing token=${token.take(12)} pending=${payloads.size}")
            return null
        }
        KardLeafLog.d(
            CODEMIRROR_BRIDGE_TRACE_TAG,
            "payload consume token=${token.take(12)} len=${entry.payload.length} age=${now - entry.createdAtMs}ms pending=${payloads.size}",
        )
        return entry.payload
    }

    private fun pruneExpiredLocked(now: Long) {
        val iterator = payloads.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.createdAtMs > MAX_PAYLOAD_AGE_MS) {
                KardLeafLog.w(
                    CODEMIRROR_BRIDGE_TRACE_TAG,
                    "payload expired token=${entry.key.take(12)} len=${entry.value.payload.length} age=${now - entry.value.createdAtMs}ms",
                )
                iterator.remove()
            }
        }
    }

    private fun trimLocked(now: Long) {
        val iterator = payloads.entries.iterator()
        while (payloads.size > MAX_PAYLOADS && iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.createdAtMs < MIN_TRIM_AGE_MS && payloads.size <= HARD_MAX_PAYLOADS) break
            KardLeafLog.w(
                CODEMIRROR_BRIDGE_TRACE_TAG,
                "payload expired reason=trim token=${entry.key.take(12)} len=${entry.value.payload.length} age=${now - entry.value.createdAtMs}ms pending=${payloads.size}",
            )
            iterator.remove()
        }
    }
}

class CodeMirrorWebViewScrollController {
    private var webView: WebView? = null
    private var lastScrollTop: Int = 0
    private var lastScrollHeight: Int = 0
    private var lastClientHeight: Int = 0

    fun attach(view: WebView) {
        webView = view
        refreshScrollMetrics()
    }

    fun detach(view: WebView) {
        if (webView === view) {
            webView = null
        }
    }

    fun updateScrollMetrics(
        scrollTop: Int,
        scrollHeight: Int,
        clientHeight: Int,
    ) {
        lastScrollTop = scrollTop.coerceAtLeast(0)
        lastScrollHeight = scrollHeight.coerceAtLeast(0)
        lastClientHeight = clientHeight.coerceAtLeast(0)
    }

    fun getFastScrollMetrics(): EditorFastScrollMetrics {
        val maxScrollY = (lastScrollHeight - lastClientHeight).coerceAtLeast(0)
        if (maxScrollY <= 0 || lastClientHeight <= 0) return EditorFastScrollMetrics()
        return EditorFastScrollMetrics(
            canScroll = true,
            ratio = (lastScrollTop.toFloat() / maxScrollY).coerceIn(0f, 1f),
            thumbFraction = (lastClientHeight.toFloat() / lastScrollHeight.coerceAtLeast(1)).coerceIn(0f, 1f),
        )
    }

    fun fastScrollToRatio(ratio: Float) {
        val view = webView ?: return
        val safeRatio = ratio.coerceIn(0f, 1f)
        view.evaluateJavascript(
            "if (window.KardLeafEditor && window.KardLeafEditor.fastScrollToRatio) { window.KardLeafEditor.fastScrollToRatio($safeRatio); } else { 'missing'; }",
        ) { result ->
            KardLeafLog.d(CODEMIRROR_TRACE_TAG, "fast scroll ratio=$safeRatio result=$result")
            refreshScrollMetrics()
        }
    }

    fun refreshScrollMetrics() {
        val view = webView ?: return
        view.evaluateJavascript(
            "if (window.KardLeafEditor && window.KardLeafEditor.getScrollMetrics) { window.KardLeafEditor.getScrollMetrics(); } else { null; }",
        ) { result ->
            val metrics = parseCodeMirrorScrollMetrics(result) ?: return@evaluateJavascript
            updateScrollMetrics(metrics.scrollTop, metrics.scrollHeight, metrics.clientHeight)
        }
    }
}

private data class CodeMirrorScrollMetrics(
    val scrollTop: Int,
    val scrollHeight: Int,
    val clientHeight: Int,
)

private fun parseCodeMirrorScrollMetrics(result: String?): CodeMirrorScrollMetrics? {
    if (result.isNullOrBlank() || result == "null" || result == "undefined") return null
    return try {
        val json = JSONObject(result)
        CodeMirrorScrollMetrics(
            scrollTop = json.optDouble("scrollTop", 0.0).roundToInt(),
            scrollHeight = json.optDouble("scrollHeight", 0.0).roundToInt(),
            clientHeight = json.optDouble("clientHeight", 0.0).roundToInt(),
        )
    } catch (error: Throwable) {
        KardLeafLog.w(CODEMIRROR_TRACE_TAG, "parse scroll metrics failed result=$result", error)
        null
    }
}

private fun codeMirrorUserPerfNoteSizeTier(length: Int): String = when {
    length < 10_000 -> "lt_1w"
    length < 50_000 -> "1w_5w"
    length < 100_000 -> "5w_10w"
    length < 1_000_000 -> "10w_100w"
    else -> "gte_100w"
}

private fun codeMirrorSearchSnippetForLog(
    text: String,
    start: Int,
    end: Int,
): String {
    if (text.isEmpty() || start < 0 || end <= start || start >= text.length) return ""
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    return text.substring(safeStart, safeEnd)
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .take(80)
}

private class CodeMirrorImeTraceWebView(context: Context) : WebView(context) {
    var traceKey: String = ""
    var traceContentLength: () -> Int = { 0 }
    var traceSizeTier: () -> String = { codeMirrorUserPerfNoteSizeTier(traceContentLength()) }
    var traceLivePreviewEnabled: () -> Boolean = { false }
    var tracePageReady: () -> Boolean = { false }
    private var inputConnectionCount = 0
    private var focusChangedCount = 0
    private var measureCount = 0
    private var layoutCount = 0
    private var globalLayoutCount = 0
    private var lastMeasuredWidth = -1
    private var lastMeasuredHeight = -1
    private var lastLayoutWidth = -1
    private var lastLayoutHeight = -1
    private var lastGlobalVisibleHeight = -1
    private var lastGlobalImeEstimate = -1
    private var keyboardFrameTraceRunId = 0
    private val visibleFrame = Rect()
    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        traceGlobalLayout()
    }

    private fun traceCommon(): String =
        "key=$traceKey docLen=${traceContentLength()} sizeTier=${traceSizeTier()} livePreview=${traceLivePreviewEnabled()} " +
            "pageReady=${tracePageReady()} width=$width height=$height attached=$isAttachedToWindow hasFocus=${hasFocus()}"

    private fun measureSpecSummary(spec: Int): String {
        val mode = when (View.MeasureSpec.getMode(spec)) {
            View.MeasureSpec.EXACTLY -> "EXACT"
            View.MeasureSpec.AT_MOST -> "ATMOST"
            View.MeasureSpec.UNSPECIFIED -> "UNSPEC"
            else -> "UNKNOWN"
        }
        return "$mode:${View.MeasureSpec.getSize(spec)}"
    }

    private fun traceGlobalLayout() {
        globalLayoutCount += 1
        val root = rootView ?: return
        root.getWindowVisibleDisplayFrame(visibleFrame)
        val visibleHeight = visibleFrame.height().coerceAtLeast(0)
        val imeEstimate = (root.height - visibleFrame.bottom).coerceAtLeast(0)
        val changed = kotlin.math.abs(visibleHeight - lastGlobalVisibleHeight) >= 8 ||
            kotlin.math.abs(imeEstimate - lastGlobalImeEstimate) >= 8
        if (changed || globalLayoutCount <= 3) {
            KardLeafLog.d(
                CODEMIRROR_IME_TRACE_TAG,
                "root globalLayout count=$globalLayoutCount root=${root.width}x${root.height} " +
                    "visible=${visibleFrame.left},${visibleFrame.top},${visibleFrame.right},${visibleFrame.bottom} " +
                    "visibleH=$visibleHeight imeEstimate=$imeEstimate view=${width}x${height} ${traceCommon()}",
            )
            lastGlobalVisibleHeight = visibleHeight
            lastGlobalImeEstimate = imeEstimate
        }
    }

    fun startKeyboardFrameTrace(serial: Int, reason: String) {
        keyboardFrameTraceRunId += 1
        val runId = keyboardFrameTraceRunId
        val startAt = SystemClock.elapsedRealtime()
        val lastFrameNanos = longArrayOf(0L)
        val lastLogAt = longArrayOf(startAt)
        val frames = intArrayOf(0)
        val slowFrames = intArrayOf(0)
        val maxFrameMs = longArrayOf(0L)
        KardLeafLog.d(
            CODEMIRROR_IME_TRACE_TAG,
            "frameTrace start serial=$serial reason=$reason ${traceCommon()}",
        )
        val choreographer = Choreographer.getInstance()
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (runId != keyboardFrameTraceRunId) return
                val previousFrame = lastFrameNanos[0]
                if (previousFrame != 0L) {
                    val deltaMs = ((frameTimeNanos - previousFrame) / 1_000_000L).coerceAtLeast(0L)
                    frames[0] += 1
                    if (deltaMs >= 24L) slowFrames[0] += 1
                    if (deltaMs > maxFrameMs[0]) maxFrameMs[0] = deltaMs
                }
                lastFrameNanos[0] = frameTimeNanos
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - startAt
                val shouldLog = now - lastLogAt[0] >= 250L || elapsed >= 1600L
                if (shouldLog) {
                    KardLeafLog.d(
                        CODEMIRROR_IME_TRACE_TAG,
                        "frameTrace tick serial=$serial reason=$reason elapsed=${elapsed}ms frames=${frames[0]} " +
                            "slowFrames=${slowFrames[0]} maxFrame=${maxFrameMs[0]}ms ${traceCommon()}",
                    )
                    lastLogAt[0] = now
                }
                if (elapsed < 1600L) {
                    choreographer.postFrameCallback(this)
                } else {
                    KardLeafLog.d(
                        CODEMIRROR_IME_TRACE_TAG,
                        "frameTrace done serial=$serial reason=$reason elapsed=${elapsed}ms frames=${frames[0]} " +
                            "slowFrames=${slowFrames[0]} maxFrame=${maxFrameMs[0]}ms ${traceCommon()}",
                    )
                }
            }
        }
        choreographer.postFrameCallback(callback)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        KardLeafLog.d(CODEMIRROR_IME_TRACE_TAG, "webView attached ${traceCommon()}")
        traceGlobalLayout()
    }

    override fun onDetachedFromWindow() {
        runCatching {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
        }
        KardLeafLog.d(CODEMIRROR_IME_TRACE_TAG, "webView detached ${traceCommon()}")
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        KardLeafLog.d(CODEMIRROR_IME_TRACE_TAG, "windowFocus changed hasWindowFocus=$hasWindowFocus ${traceCommon()}")
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        focusChangedCount += 1
        KardLeafLog.d(
            CODEMIRROR_IME_TRACE_TAG,
            "webView focusChanged count=$focusChangedCount gain=$gainFocus direction=$direction ${traceCommon()}",
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val startAt = SystemClock.elapsedRealtime()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val elapsed = SystemClock.elapsedRealtime() - startAt
        measureCount += 1
        val changed = measuredWidth != lastMeasuredWidth || measuredHeight != lastMeasuredHeight
        if (changed || elapsed >= 8L || measureCount <= 3) {
            KardLeafLog.d(
                CODEMIRROR_IME_TRACE_TAG,
                "webView measure count=$measureCount elapsed=${elapsed}ms " +
                    "spec=${measureSpecSummary(widthMeasureSpec)}x${measureSpecSummary(heightMeasureSpec)} " +
                    "measured=${measuredWidth}x${measuredHeight} changed=$changed ${traceCommon()}",
            )
            lastMeasuredWidth = measuredWidth
            lastMeasuredHeight = measuredHeight
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val startAt = SystemClock.elapsedRealtime()
        super.onLayout(changed, left, top, right, bottom)
        val elapsed = SystemClock.elapsedRealtime() - startAt
        layoutCount += 1
        val layoutWidth = right - left
        val layoutHeight = bottom - top
        val sizeChanged = layoutWidth != lastLayoutWidth || layoutHeight != lastLayoutHeight
        if (changed || sizeChanged || elapsed >= 8L || layoutCount <= 3) {
            KardLeafLog.d(
                CODEMIRROR_IME_TRACE_TAG,
                "webView layout count=$layoutCount elapsed=${elapsed}ms changed=$changed " +
                    "bounds=$left,$top,$right,$bottom size=${layoutWidth}x$layoutHeight sizeChanged=$sizeChanged ${traceCommon()}",
            )
            lastLayoutWidth = layoutWidth
            lastLayoutHeight = layoutHeight
        }
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (height != oldHeight || width != oldWidth) {
            KardLeafLog.d(
                CODEMIRROR_IME_TRACE_TAG,
                "webView sizeChanged ${oldWidth}x$oldHeight -> ${width}x$height ${traceCommon()}",
            )
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val startAt = SystemClock.elapsedRealtime()
        val connection = super.onCreateInputConnection(outAttrs)
        val elapsed = SystemClock.elapsedRealtime() - startAt
        inputConnectionCount += 1
        KardLeafLog.d(
            CODEMIRROR_IME_TRACE_TAG,
            "inputConnection count=$inputConnectionCount elapsed=${elapsed}ms result=${connection != null} " +
                "inputType=${outAttrs.inputType} imeOptions=${outAttrs.imeOptions} ${traceCommon()}",
        )
        return connection
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KardLeafCodeMirrorEditor(
    initialTitle: String,
    initialContent: String,
    documentKey: String,
    controller: KardLeafEditorController,
    scrollController: CodeMirrorWebViewScrollController,
    onTitleChanged: () -> Unit,
    onContentChanged: () -> Unit,
    onContentEdited: () -> Unit = {},
    onUndoRedoStateChanged: () -> Unit = {},
    onUserInteraction: () -> Unit,
    onFastScrollSourceScrolled: () -> Unit = {},
    titleHint: String,
    textColor: Color,
    hintColor: Color,
    titleTextSize: TextUnit,
    contentTextSize: TextUnit,
    contentLineHeightMultiplier: Float = 1.55f,
    contentLetterSpacingSp: Float = 0f,
    contentParagraphSpacingDp: Float = 8f,
    contentFontFamily: String = "system",
    isDark: Boolean,
    showTitle: Boolean,
    livePreviewEnabled: Boolean = false,
    resolveImages: suspend (String) -> List<KardLeafCodeMirrorImage> = { emptyList() },
    userPerfOpenStartRealtimeMs: Long? = null,
    userPerfSizeTier: String = codeMirrorUserPerfNoteSizeTier(initialContent.length),
    onUserPerfBodyRendered: (Int, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val webViewRef = remember { AtomicReference<WebView?>(null) }
    val appContext = LocalContext.current.applicationContext
    val appColorScheme = MaterialTheme.colorScheme
    val codeMirrorBackgroundArgb = appColorScheme.background.toArgb()
    val codeMirrorThemeColorsScript = buildCodeMirrorThemeColorsScript(
        background = appColorScheme.background,
        foreground = appColorScheme.onBackground,
        muted = appColorScheme.onSurfaceVariant,
        primary = appColorScheme.primary,
        border = appColorScheme.outlineVariant,
        soft = appColorScheme.surfaceVariant,
    )
    val latestOnContentChanged by rememberUpdatedState(onContentChanged)
    val latestOnContentEdited by rememberUpdatedState(onContentEdited)
    val latestOnUndoRedoStateChanged by rememberUpdatedState(onUndoRedoStateChanged)
    val latestOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val latestOnFastScrollSourceScrolled by rememberUpdatedState(onFastScrollSourceScrolled)
    val latestResolveImages by rememberUpdatedState(resolveImages)
    val latestInitialContent by rememberUpdatedState(initialContent)
    val latestIsDark by rememberUpdatedState(isDark)
    val latestLivePreviewEnabled by rememberUpdatedState(livePreviewEnabled)
    val latestUserPerfBodyRendered by rememberUpdatedState(onUserPerfBodyRendered)
    var pageReady by remember(documentKey) { mutableStateOf(false) }
    var codeMirrorContentApplied by remember(documentKey) { mutableStateOf(false) }
    var lastPushedContentLength by remember(documentKey) { mutableStateOf(-1) }
    var lastPushDocumentAt by remember(documentKey) { mutableStateOf(0L) }
    var lastContentAppliedAt by remember(documentKey) { mutableStateOf(0L) }
    var contentApplyRetryCount by remember(documentKey, initialContent) { mutableStateOf(0) }
    var hasEditorSideChanges by remember(documentKey) { mutableStateOf(false) }
    var imageResolveVersion by remember(documentKey) { mutableStateOf(0) }
    var imageResolveImmediate by remember(documentKey) { mutableStateOf(false) }
    var titleValue by remember(documentKey) {
        mutableStateOf(TextFieldValue(initialTitle, selection = TextRange(initialTitle.length)))
    }
    val composePerfCount = remember(documentKey) { intArrayOf(0) }
    val composePerfLastAt = remember(documentKey) { longArrayOf(SystemClock.elapsedRealtime()) }
    val imeTraceRequestSerial = remember(documentKey) { intArrayOf(0) }
    val imeTraceLastRequestAt = remember(documentKey) { longArrayOf(0L) }
    val androidViewUpdateCount = remember(documentKey) { intArrayOf(0) }
    val androidViewUpdateLastAt = remember(documentKey) { longArrayOf(SystemClock.elapsedRealtime()) }

    SideEffect {
        composePerfCount[0] += 1
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - composePerfLastAt[0]
        if (elapsed >= 500L) {
            val webView = webViewRef.get()
            val sinceImeRequest = if (imeTraceLastRequestAt[0] > 0L) now - imeTraceLastRequestAt[0] else -1L
            val rootSize = webView?.rootView?.let { "${it.width}x${it.height}" } ?: "missing"
            val webViewSize = webView?.let { "${it.width}x${it.height}" } ?: "missing"
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "compose perf key=$documentKey count=${composePerfCount[0]} elapsed=${elapsed}ms " +
                    "contentLen=${initialContent.length} pageReady=$pageReady contentApplied=$codeMirrorContentApplied " +
                    "lastAppliedAt=$lastContentAppliedAt sinceImeRequest=${sinceImeRequest}ms " +
                    "webView=$webViewSize root=$rootSize hasFocus=${webView?.hasFocus()}",
            )
            composePerfCount[0] = 0
            composePerfLastAt[0] = now
        }
    }

    LaunchedEffect(documentKey, initialTitle) {
        controller.updateExternalTitle(initialTitle)
    }

    LaunchedEffect(pageReady, livePreviewEnabled) {
        if (pageReady) {
            val livePreviewFlag = if (livePreviewEnabled) "true" else "false"
            webViewRef.get()?.evaluateJavascript(
                "if (window.KardLeafEditor && window.KardLeafEditor.setLivePreviewEnabled) { " +
                    "window.KardLeafEditor.setLivePreviewEnabled($livePreviewFlag); 'ok'; " +
                    "} else { 'missing'; }",
                null,
            )
        }
    }

    LaunchedEffect(pageReady, isDark, codeMirrorThemeColorsScript) {
        if (pageReady) {
            val darkFlag = if (isDark) "true" else "false"
            val webView = webViewRef.get()
            webView?.setBackgroundColor(codeMirrorBackgroundArgb)
            webView?.evaluateJavascript(
                "if (window.KardLeafEditor && window.KardLeafEditor.setDarkMode) { " +
                    "window.KardLeafEditor.setDarkMode($darkFlag); 'ok'; " +
                    "} else { 'missing'; }",
                null,
            )
            webView?.evaluateJavascript(codeMirrorThemeColorsScript, null)
        }
    }

    LaunchedEffect(documentKey, pageReady, livePreviewEnabled, initialContent, imageResolveVersion, hasEditorSideChanges) {
        if (!pageReady) return@LaunchedEffect
        if (!livePreviewEnabled) {
            webViewRef.get()?.pushCodeMirrorImageDataUris(emptyList(), reason = "live preview disabled")
            return@LaunchedEffect
        }
        if (imageResolveVersion > 0 && !imageResolveImmediate) {
            delay(420L)
        }
        val markdownForImages = if (hasEditorSideChanges) {
            controller.getCachedSnapshot().content
        } else {
            initialContent
        }
        if (markdownForImages.length > CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT) {
            KardLeafLog.d(
                CODEMIRROR_DEBUG_TRACE_TAG,
                "[image] resolve skipped large doc key=$documentKey markdownLen=${markdownForImages.length} " +
                    "limit=$CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT version=$imageResolveVersion",
            )
            imageResolveImmediate = false
            return@LaunchedEffect
        }
        val markdownImageReferences = extractCodeMirrorImageReferencesForTrace(markdownForImages)
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[image] markdown scan key=$documentKey markdownLen=${markdownForImages.length} " +
                "refs=${markdownImageReferences.size} rawRefs=${markdownImageReferences.joinToString("|")}",
        )
        if (!markdownForImages.contains("![")) {
            webViewRef.get()?.pushCodeMirrorImageDataUris(emptyList(), reason = "no image references")
            return@LaunchedEffect
        }
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[image] resolve start key=$documentKey markdownLen=${markdownForImages.length} version=$imageResolveVersion " +
                "immediate=$imageResolveImmediate editorSide=$hasEditorSideChanges",
        )
        val images = runCatching { latestResolveImages(markdownForImages) }
            .onFailure { error ->
                KardLeafLog.w(
                    CODEMIRROR_DEBUG_TRACE_TAG,
                    "[error][image] resolve failed key=$documentKey markdownLen=${markdownForImages.length}",
                    error,
                )
            }
            .getOrDefault(emptyList())
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[image] resolve done key=$documentKey count=${images.size} first=${images.firstOrNull()?.reference.orEmpty()} " +
                "data=${images.take(8).joinToString("|") { "${it.reference}:${it.dataUri.length}:${it.dataUri.take(24)}" }}",
        )
        webViewRef.get()?.pushCodeMirrorImageDataUris(
            images = images,
            reason = "resolve version=$imageResolveVersion immediate=$imageResolveImmediate",
        )
        imageResolveImmediate = false
    }

    LaunchedEffect(
        documentKey,
        initialContent,
        contentTextSize,
        contentLineHeightMultiplier,
        contentLetterSpacingSp,
        contentParagraphSpacingDp,
        contentFontFamily,
        pageReady,
        hasEditorSideChanges,
        contentApplyRetryCount,
    ) {
        if (pageReady && !hasEditorSideChanges) {
            val selection = controller.getSelection()
            val pushStartedAt = SystemClock.elapsedRealtime()
            val attempt = contentApplyRetryCount + 1
            codeMirrorContentApplied = false
            lastContentAppliedAt = 0L
            lastPushedContentLength = initialContent.length
            lastPushDocumentAt = pushStartedAt
            webViewRef.get()?.pushDocumentToCodeMirror(
                content = initialContent,
                selection = selection,
                contentTextSize = contentTextSize,
                contentLineHeightMultiplier = contentLineHeightMultiplier,
                contentLetterSpacingSp = contentLetterSpacingSp,
                contentParagraphSpacingDp = contentParagraphSpacingDp,
                contentFontFamily = contentFontFamily,
                isDark = isDark,
                livePreviewEnabled = livePreviewEnabled,
                reason = if (contentApplyRetryCount > 0) "compose retry" else "compose update",
                openStartRealtimeMs = userPerfOpenStartRealtimeMs,
                sizeTier = userPerfSizeTier,
                onDone = {
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "push document eval done attempt=$attempt key=$documentKey len=${initialContent.length}; waiting content applied",
                    )
                },
            )
            delay(2000L)
            if (
                !codeMirrorContentApplied &&
                pageReady &&
                !hasEditorSideChanges &&
                lastPushDocumentAt == pushStartedAt &&
                lastPushedContentLength == initialContent.length
            ) {
                KardLeafLog.w(
                    CODEMIRROR_TRACE_TAG,
                    "content applied timeout attempt=$attempt key=$documentKey len=${initialContent.length} " +
                        "elapsed=${SystemClock.elapsedRealtime() - pushStartedAt}ms pageReady=$pageReady",
                )
                if (contentApplyRetryCount == 0) {
                    KardLeafLog.w(
                        CODEMIRROR_TRACE_TAG,
                        "content applied retry key=$documentKey len=${initialContent.length}",
                    )
                    contentApplyRetryCount = 1
                }
            }
        } else if (pageReady && hasEditorSideChanges) {
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "skip compose push after editor changes key=$documentKey contentLen=${initialContent.length}",
            )
        }
    }

    DisposableEffect(documentKey) {
        controller.setExternalContentUpdater { text, selection ->
            if (text != latestInitialContent) {
                hasEditorSideChanges = true
                if (livePreviewEnabled && text.length <= CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT && text.contains("![")) {
                    imageResolveImmediate = true
                    imageResolveVersion += 1
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "external content update request image resolve key=$documentKey len=${text.length} version=$imageResolveVersion",
                    )
                } else if (livePreviewEnabled && text.length > CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT) {
                    KardLeafLog.d(
                        CODEMIRROR_DEBUG_TRACE_TAG,
                        "[image] external image resolve skipped large doc key=$documentKey len=${text.length} " +
                            "limit=$CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT",
                    )
                }
            }
            val requestAt = SystemClock.elapsedRealtime()
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "search bridge selection request key=$documentKey len=${text.length} " +
                    "selection=${selection.start}-${selection.end} " +
                    "selectedText=${codeMirrorSearchSnippetForLog(text, selection.start, selection.end)} " +
                    "livePreview=$livePreviewEnabled pageReady=$pageReady sideChanges=$hasEditorSideChanges " +
                    "textSameInitial=${text == latestInitialContent}",
            )
            webViewRef.get()?.post {
                val buildStart = SystemClock.elapsedRealtime()
                val token = KardLeafCodeMirrorPayloadStore.put(text)
                val quotedToken = JSONObject.quote(token)
                val script =
                    "(function() { " +
                        "if (!window.KardLeafEditor || !window.KardLeafEditor.setContentFromAndroid) return 'missing-editor'; " +
                        "if (!window.KardLeafAndroid || !window.KardLeafAndroid.consumeDocumentPayload) return 'missing-bridge'; " +
                        "var content = window.KardLeafAndroid.consumeDocumentPayload($quotedToken); " +
                        "if (content == null) return 'missing-payload'; " +
                        "window.KardLeafEditor.setContentFromAndroid(content, ${selection.start}, ${selection.end}); " +
                        "return 'ok'; " +
                        "})();"
                val buildElapsed = SystemClock.elapsedRealtime() - buildStart
                val evalStart = SystemClock.elapsedRealtime()
                webViewRef.get()?.evaluateJavascript(script) { result ->
                    val evalElapsed = SystemClock.elapsedRealtime() - evalStart
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "external content update result=$result len=${text.length} selection=${selection.start}-${selection.end} " +
                            "selectedText=${codeMirrorSearchSnippetForLog(text, selection.start, selection.end)} " +
                            "queue=${buildStart - requestAt}ms build=${buildElapsed}ms eval=${evalElapsed}ms",
                    )
                    if (text.contains("![")) {
                        KardLeafLog.d(
                            CODEMIRROR_DEBUG_TRACE_TAG,
                            "[insert-image] external content applied without cursor scroll len=${text.length} " +
                                "largeDoc=${text.length > CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT}",
                        )
                    }
                }
            }
        }
        controller.updateExternalUndoRedoState(false, false)
        controller.setExternalUndoRedoActions(
            undoAction = {
                webViewRef.get()?.post {
                    webViewRef.get()?.evaluateJavascript(
                        "if (window.KardLeafEditor && window.KardLeafEditor.undo) { window.KardLeafEditor.undo(); } else { 'missing'; }",
                    ) { result ->
                        KardLeafLog.d(CODEMIRROR_TRACE_TAG, "external undo result=$result key=$documentKey")
                    }
                }
            },
            redoAction = {
                webViewRef.get()?.post {
                    webViewRef.get()?.evaluateJavascript(
                        "if (window.KardLeafEditor && window.KardLeafEditor.redo) { window.KardLeafEditor.redo(); } else { 'missing'; }",
                    ) { result ->
                        KardLeafLog.d(CODEMIRROR_TRACE_TAG, "external redo result=$result key=$documentKey")
                    }
                }
            },
        )
        controller.setExternalCommandExecutor { command, args ->
            val webView = webViewRef.get() ?: return@setExternalCommandExecutor false
            val quotedCommand = JSONObject.quote(command)
            val serializedArgs = args.joinToString(",") { arg ->
                when (arg) {
                    is Number, is Boolean -> arg.toString()
                    else -> JSONObject.quote(arg.toString())
                }
            }
            val script =
                "if (window.KardLeafEditor && window.KardLeafEditor.execCommand) { " +
                    "window.KardLeafEditor.execCommand($quotedCommand" +
                    (if (serializedArgs.isNotEmpty()) ",$serializedArgs" else "") +
                    "); 'ok'; } else { 'missing'; }"
            webView.post {
                webView.evaluateJavascript(script) { result ->
                    KardLeafLog.d(CODEMIRROR_TRACE_TAG, "external command result=$result command=$command key=$documentKey")
                }
            }
            true
        }
        controller.setExternalSnapshotRequester { callback ->
            val requestAt = SystemClock.elapsedRealtime()
            val webView = webViewRef.get()
            if (webView == null) {
                KardLeafLog.w(CODEMIRROR_TRACE_TAG, "snapshot request missing webview key=$documentKey")
                callback(controller.getCachedSnapshot())
                return@setExternalSnapshotRequester
            }
            webView.post {
                val evalStart = SystemClock.elapsedRealtime()
                KardLeafLog.d(
                    USER_PERF_TRACE_TAG,
                    "codeMirror saveGetTextStart engine=CODEMIRROR docLen=${controller.getCachedSnapshot().content.length} " +
                        "sizeTier=$userPerfSizeTier queue=${evalStart - requestAt}ms key=$documentKey",
                )
                webView.evaluateJavascript(
                    "if (window.KardLeafEditor && window.KardLeafEditor.getText) { window.KardLeafEditor.getText(); } else { null; }",
                ) { result ->
                    val evalElapsed = SystemClock.elapsedRealtime() - evalStart
                    val text = decodeJavascriptStringResult(result)
                    if (text == null) {
                        KardLeafLog.w(
                            CODEMIRROR_TRACE_TAG,
                            "snapshot request failed result=$result key=$documentKey eval=${evalElapsed}ms queue=${evalStart - requestAt}ms",
                        )
                        callback(controller.getCachedSnapshot())
                    } else {
                        val selection = controller.getSelection()
                        controller.updateExternalContentSnapshot(text, selection)
                        KardLeafLog.d(
                            CODEMIRROR_TRACE_TAG,
                            "snapshot request done key=$documentKey len=${text.length} eval=${evalElapsed}ms queue=${evalStart - requestAt}ms",
                        )
                        KardLeafLog.d(
                            USER_PERF_TRACE_TAG,
                            "codeMirror saveGetTextEnd engine=CODEMIRROR elapsed=${SystemClock.elapsedRealtime() - evalStart}ms " +
                                "docLen=${text.length} sizeTier=$userPerfSizeTier key=$documentKey",
                        )
                        callback(KardLeafEditorSnapshot(controller.getCachedSnapshot().title, text, selection))
                    }
                }
            }
        }
        onDispose {
            webViewRef.get()?.let { scrollController.detach(it) }
            controller.setExternalContentUpdater(null)
            controller.setExternalSnapshotRequester(null)
            controller.setExternalUndoRedoActions(null, null)
            controller.setExternalCommandExecutor(null)
            controller.updateExternalUndoRedoState(false, false)
        }
    }

    val bridge = remember(documentKey) {
        KardLeafCodeMirrorBridge(
            controller = controller,
            appContext = appContext,
            onEditorReady = {
                pageReady = true
                if (webViewRef.get() != null) {
                    scrollController.refreshScrollMetrics()
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "editor ready key=$documentKey contentApplied=$codeMirrorContentApplied expectedLen=${latestInitialContent.length}",
                    )
                    userPerfOpenStartRealtimeMs?.let { start ->
                        KardLeafLog.d(
                            USER_PERF_TRACE_TAG,
                            "editorOpen codeMirrorEditorReady elapsed=${SystemClock.elapsedRealtime() - start}ms " +
                                "engine=CODEMIRROR contentLen=${latestInitialContent.length} sizeTier=$userPerfSizeTier key=$documentKey",
                        )
                    }
                }
            },
            onContentApplied = { contentLength ->
                val wasApplied = codeMirrorContentApplied
                val now = SystemClock.elapsedRealtime()
                val sincePush = if (lastPushDocumentAt > 0L) now - lastPushDocumentAt else -1L
                codeMirrorContentApplied = true
                lastContentAppliedAt = now
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "content applied len=$contentLength expected=$lastPushedContentLength key=$documentKey " +
                        "sincePush=${sincePush}ms retry=$contentApplyRetryCount",
                )
                scrollController.refreshScrollMetrics()
                if (!wasApplied) {
                    val status = if (contentLength > 0) "visible" else "empty"
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "body visible after content applied len=$contentLength status=$status key=$documentKey",
                    )
                    latestUserPerfBodyRendered(contentLength, status)
                }
            },
            onEditorContentEdited = {
                latestOnContentEdited()
                hasEditorSideChanges = true
                imageResolveImmediate = false
                val currentLen = controller.getCachedSnapshot().content.length
                if (latestLivePreviewEnabled && currentLen <= CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT) {
                    imageResolveVersion += 1
                } else if (latestLivePreviewEnabled && currentLen > CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT) {
                    KardLeafLog.d(
                        CODEMIRROR_DEBUG_TRACE_TAG,
                        "[image] edit image resolve skipped large doc key=$documentKey len=$currentLen " +
                            "limit=$CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT",
                    )
                }
            },
            onContentChanged = { latestOnContentChanged() },
            onUndoRedoStateChanged = { latestOnUndoRedoStateChanged() },
            onUserInteraction = { latestOnUserInteraction() },
            onEditorScrollGesture = {
                scrollController.refreshScrollMetrics()
            },
            onEditorScrollMetricsChanged = { scrollTop, scrollHeight, clientHeight ->
                scrollController.updateScrollMetrics(scrollTop, scrollHeight, clientHeight)
                latestOnFastScrollSourceScrolled()
            },
            onEditorFocusRequest = { reason ->
                val webView = webViewRef.get()
                val requestAt = SystemClock.elapsedRealtime()
                imeTraceRequestSerial[0] += 1
                imeTraceLastRequestAt[0] = requestAt
                val requestSerial = imeTraceRequestSerial[0]
                KardLeafLog.d(
                    CODEMIRROR_IME_TRACE_TAG,
                    "focusRequest received serial=$requestSerial reason=${reason ?: "unknown"} key=$documentKey docLen=${latestInitialContent.length} " +
                        "sizeTier=$userPerfSizeTier livePreview=$latestLivePreviewEnabled pageReady=$pageReady " +
                        "hasEditorSideChanges=$hasEditorSideChanges webViewExists=${webView != null}",
                )
                webView?.post {
                    val postAt = SystemClock.elapsedRealtime()
                    val focusStart = SystemClock.elapsedRealtime()
                    if (!webView.hasFocus()) webView.requestFocus()
                    val focusElapsed = SystemClock.elapsedRealtime() - focusStart
                    KardLeafLog.d(
                        CODEMIRROR_IME_TRACE_TAG,
                        "focusRequest handled serial=$requestSerial reason=${reason ?: "unknown"} key=$documentKey " +
                            "queue=${postAt - requestAt}ms focus=${focusElapsed}ms " +
                            "docLen=${latestInitialContent.length} sizeTier=$userPerfSizeTier livePreview=$latestLivePreviewEnabled " +
                            "hasFocus=${webView.hasFocus()} width=${webView.width} height=${webView.height} attached=${webView.isAttachedToWindow}",
                    )
                    if (CODEMIRROR_IME_DEEP_TRACE_ENABLED) {
                        (webView as? CodeMirrorImeTraceWebView)?.startKeyboardFrameTrace(requestSerial, reason ?: "unknown")
                        val quotedTraceReason = JSONObject.quote(reason ?: "unknown")
                        webView.evaluateJavascript(
                            "if (window.kardleafImeDeepTraceStart) { window.kardleafImeDeepTraceStart($requestSerial, $quotedTraceReason); 'ok'; } else { 'missing'; }",
                        ) { result ->
                            KardLeafLog.d(
                                CODEMIRROR_IME_TRACE_TAG,
                                "js deepTraceStart result=$result serial=$requestSerial reason=${reason ?: "unknown"} key=$documentKey",
                            )
                        }
                    }
                }
            },
            userPerfSizeTier = userPerfSizeTier,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 12.dp, end = 12.dp, top = 6.dp),
    ) {
        if (showTitle) {
            BasicTextField(
                value = titleValue,
                onValueChange = { value ->
                    titleValue = value
                    controller.updateExternalTitle(value.text)
                    onTitleChanged()
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.merge(
                    TextStyle(color = textColor, fontSize = titleTextSize),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (titleValue.text.isEmpty()) {
                        Text(
                            text = titleHint,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = hintColor,
                                fontSize = titleTextSize,
                            ),
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                factory = { context ->
                val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                val codeMirrorWebView =
                    if (CODEMIRROR_IME_DEEP_TRACE_ENABLED) {
                        CodeMirrorImeTraceWebView(context).apply {
                            traceKey = documentKey
                            traceContentLength = { latestInitialContent.length }
                            traceSizeTier = { userPerfSizeTier }
                            traceLivePreviewEnabled = { latestLivePreviewEnabled }
                            tracePageReady = { pageReady }
                        }
                    } else {
                        WebView(context)
                    }
                codeMirrorWebView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewRef.set(this)
                    scrollController.attach(this)
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "factory create key=$documentKey initialContentLen=${initialContent.length}",
                    )
                    WebView.setWebContentsDebuggingEnabled(isDebuggable)
                    setBackgroundColor(codeMirrorBackgroundArgb)
                    alpha = 1f
                    visibility = View.VISIBLE
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isFocusable = true
                    isFocusableInTouchMode = true
                    var imeTraceLastVisible = false
                    var imeTraceLastBottom = -1
                    var imeTraceLastSettledBottom = -1
                    if (CODEMIRROR_IME_DEEP_TRACE_ENABLED) {
                        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
                            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                            val bottomChanged = kotlin.math.abs(imeInsets.bottom - imeTraceLastBottom) >= 8
                            if (imeVisible != imeTraceLastVisible || bottomChanged) {
                                val previousBottom = imeTraceLastBottom
                                val sinceRequest = if (imeTraceLastRequestAt[0] > 0L) {
                                    SystemClock.elapsedRealtime() - imeTraceLastRequestAt[0]
                                } else {
                                    -1L
                                }
                                KardLeafLog.d(
                                    CODEMIRROR_IME_TRACE_TAG,
                                    "imeInsets changed serial=${imeTraceRequestSerial[0]} sinceRequest=${sinceRequest}ms " +
                                        "visible=$imeVisible prevVisible=$imeTraceLastVisible " +
                                        "bottom=${imeInsets.bottom} prevBottom=$previousBottom navBottom=${navInsets.bottom} " +
                                        "key=$documentKey docLen=${latestInitialContent.length} sizeTier=$userPerfSizeTier " +
                                        "livePreview=$latestLivePreviewEnabled pageReady=$pageReady view=${view.width}x${view.height} " +
                                        "root=${view.rootView.width}x${view.rootView.height} hasFocus=${view.hasFocus()}",
                                )
                                imeTraceLastVisible = imeVisible
                                imeTraceLastBottom = imeInsets.bottom
                                if (imeVisible && imeInsets.bottom > 0) {
                                    val settleBottom = imeInsets.bottom
                                    view.postDelayed({
                                        if (imeTraceLastVisible && imeTraceLastBottom == settleBottom && imeTraceLastSettledBottom != settleBottom) {
                                            imeTraceLastSettledBottom = settleBottom
                                            val settleSinceRequest = if (imeTraceLastRequestAt[0] > 0L) {
                                                SystemClock.elapsedRealtime() - imeTraceLastRequestAt[0]
                                            } else {
                                                -1L
                                            }
                                            KardLeafLog.d(
                                                CODEMIRROR_IME_TRACE_TAG,
                                                "imeInsets settled serial=${imeTraceRequestSerial[0]} sinceRequest=${settleSinceRequest}ms " +
                                                    "bottom=$settleBottom key=$documentKey docLen=${latestInitialContent.length} " +
                                                    "sizeTier=$userPerfSizeTier livePreview=$latestLivePreviewEnabled " +
                                                    "view=${view.width}x${view.height} hasFocus=${view.hasFocus()}",
                                            )
                                        }
                                    }, 180L)
                                } else if (!imeVisible) {
                                    imeTraceLastSettledBottom = -1
                                }
                            }
                            insets
                        }
                    }
                    // 不在 Android 侧拦截滑动并强制隐藏输入法。
                    // CodeMirror 自己处理滚动，避免“滑动时输入法反复弹出/收起”。
                    overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    settings.javaScriptEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.domStorageEnabled = false
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.textZoom = 100
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    addJavascriptInterface(bridge, "KardLeafAndroid")
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            val message = consoleMessage?.message().orEmpty()
                            if (message.contains("KardLeafCM6Image")) {
                                KardLeafLog.d(
                                    CODEMIRROR_IMAGE_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafCM6Perf")) {
                                KardLeafLog.d(
                                    CODEMIRROR_PERF_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafCM6Scroll")) {
                                KardLeafLog.d(
                                    CODEMIRROR_SCROLL_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafCM6Input")) {
                                KardLeafLog.d(
                                    CODEMIRROR_INPUT_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafCM6Bridge")) {
                                KardLeafLog.d(
                                    CODEMIRROR_BRIDGE_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafCM6TableTrace")) {
                                KardLeafLog.d(
                                    CODEMIRROR_TABLE_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafCM6Ime")) {
                                KardLeafLog.d(
                                    CODEMIRROR_IME_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafCM6Trace")) {
                                KardLeafLog.d(
                                    CODEMIRROR_DEBUG_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else if (message.contains("KardLeafCM6")) {
                                KardLeafLog.d(
                                    CODEMIRROR_JS_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            } else {
                                KardLeafLog.d(
                                    CODEMIRROR_TRACE_TAG,
                                    "console line=${consoleMessage?.lineNumber()} level=${consoleMessage?.messageLevel()} message=$message",
                                )
                            }
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            pageReady = false
                            codeMirrorContentApplied = false
                            lastContentAppliedAt = 0L
                            KardLeafLog.d(CODEMIRROR_TRACE_TAG, "page started url=$url key=$documentKey")
                            userPerfOpenStartRealtimeMs?.let { start ->
                                KardLeafLog.d(
                                    USER_PERF_TRACE_TAG,
                                    "editorOpen codeMirrorPageStarted elapsed=${SystemClock.elapsedRealtime() - start}ms " +
                                        "engine=CODEMIRROR contentLen=${latestInitialContent.length} sizeTier=$userPerfSizeTier key=$documentKey",
                                )
                            }
                        }

                        override fun onPageCommitVisible(view: WebView?, url: String?) {
                            super.onPageCommitVisible(view, url)
                            KardLeafLog.d(
                                CODEMIRROR_TRACE_TAG,
                                "page commit visible url=$url key=$documentKey contentApplied=$codeMirrorContentApplied",
                            )
                            userPerfOpenStartRealtimeMs?.let { start ->
                                KardLeafLog.d(
                                    USER_PERF_TRACE_TAG,
                                    "editorOpen codeMirrorPageCommitVisible elapsed=${SystemClock.elapsedRealtime() - start}ms " +
                                        "engine=CODEMIRROR contentLen=${latestInitialContent.length} sizeTier=$userPerfSizeTier key=$documentKey",
                                )
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            super.onReceivedError(view, request, error)
                            KardLeafLog.e(
                                CODEMIRROR_TRACE_TAG,
                                "page error url=${request?.url} mainFrame=${request?.isForMainFrame} code=${error?.errorCode} desc=${error?.description}",
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            KardLeafLog.d(
                                CODEMIRROR_TRACE_TAG,
                                "page finished url=$url key=$documentKey contentLen=${latestInitialContent.length}",
                            )
                            userPerfOpenStartRealtimeMs?.let { start ->
                                KardLeafLog.d(
                                    USER_PERF_TRACE_TAG,
                                    "editorOpen codeMirrorHtmlReady elapsed=${SystemClock.elapsedRealtime() - start}ms " +
                                        "engine=CODEMIRROR contentLen=${latestInitialContent.length} sizeTier=$userPerfSizeTier key=$documentKey",
                                )
                            }
                            view?.visibility = View.VISIBLE
                            view?.alpha = 1f
                            view?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            scrollController.refreshScrollMetrics()
                        }
                    }
                    fun requestEditorPageLoad(reason: String) {
                        KardLeafLog.d(
                            CODEMIRROR_TRACE_TAG,
                            "CodeMirror page load requested reason=$reason key=$documentKey url=$CODEMIRROR_ASSET_URL width=$width height=$height attached=$isAttachedToWindow",
                        )
                        loadUrl(CODEMIRROR_ASSET_URL)
                    }
                    post {
                        requestEditorPageLoad("attached")
                    }
                    postDelayed({
                        if (!pageReady) {
                            KardLeafLog.w(
                                CODEMIRROR_TRACE_TAG,
                                "page load timeout 2000ms key=$documentKey url=$url progress=$progress width=$width height=$height attached=$isAttachedToWindow; reload",
                            )
                            stopLoading()
                            requestEditorPageLoad("timeout-reload")
                        }
                    }, 2000L)
                    // 不在页面加载后自动聚焦编辑器。
                    // Android WebView + CodeMirror 在大文本里自动聚焦会导致用户只想上下滑动时弹出输入法。
                    KardLeafLog.d(CODEMIRROR_TRACE_TAG, "initial focus skipped key=$documentKey pageReady=$pageReady")
                }
            },
            update = { webView ->
                webViewRef.set(webView)
                scrollController.attach(webView)
                webView.visibility = View.VISIBLE
                webView.alpha = 1f
                webView.setBackgroundColor(codeMirrorBackgroundArgb)
                webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                androidViewUpdateCount[0] += 1
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - androidViewUpdateLastAt[0]
                if (elapsed >= 500L) {
                    val sinceImeRequest = if (imeTraceLastRequestAt[0] > 0L) now - imeTraceLastRequestAt[0] else -1L
                    KardLeafLog.d(
                        CODEMIRROR_IME_TRACE_TAG,
                        "androidView update perf key=$documentKey count=${androidViewUpdateCount[0]} elapsed=${elapsed}ms " +
                            "sinceImeRequest=${sinceImeRequest}ms view=${webView.width}x${webView.height} " +
                            "root=${webView.rootView.width}x${webView.rootView.height} pageReady=$pageReady " +
                            "livePreview=$latestLivePreviewEnabled hasFocus=${webView.hasFocus()}",
                    )
                    androidViewUpdateCount[0] = 0
                    androidViewUpdateLastAt[0] = now
                }
                },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private class KardLeafCodeMirrorBridge(
    private val controller: KardLeafEditorController,
    private val appContext: Context,
    private val onEditorReady: () -> Unit,
    private val onContentApplied: (Int) -> Unit,
    private val onEditorContentEdited: () -> Unit,
    private val onContentChanged: () -> Unit,
    private val onUndoRedoStateChanged: () -> Unit,
    private val onUserInteraction: () -> Unit,
    private val onEditorScrollGesture: () -> Unit,
    private val onEditorScrollMetricsChanged: (Int, Int, Int) -> Unit,
    private val onEditorFocusRequest: (String?) -> Unit,
    private val userPerfSizeTier: String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var patchCount = 0
    private var slowPatchCount = 0
    private var lastPatchLogAt = SystemClock.elapsedRealtime()
    private var selectionCount = 0
    private var lastSelectionLogAt = SystemClock.elapsedRealtime()
    private var interactionCount = 0
    private var lastInteractionLogAt = SystemClock.elapsedRealtime()
    private var userPerfScrollStartY = 0
    private var lastContentNotifyAt = 0L
    private var pendingContentNotify = false

    private fun scheduleContentChangedNotify(): Long {
        val now = SystemClock.elapsedRealtime()
        val delay = (500L - (now - lastContentNotifyAt)).coerceAtLeast(0L)
        if (delay == 0L) {
            pendingContentNotify = false
            lastContentNotifyAt = now
            onContentChanged()
            return 0L
        }
        if (!pendingContentNotify) {
            pendingContentNotify = true
            mainHandler.postDelayed({
                pendingContentNotify = false
                lastContentNotifyAt = SystemClock.elapsedRealtime()
                onContentChanged()
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "bridge content notify delayed delay=${delay}ms",
                )
            }, delay)
        }
        return delay
    }

    private fun applyPatchOnMain(
        start: Int,
        deleteCount: Int,
        insertedText: String,
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        controller.applyExternalContentPatch(
            start = start,
            deleteCount = deleteCount,
            insertedText = insertedText,
            selection = TextRange(selectionStart, selectionEnd),
        )
    }

    @JavascriptInterface
    fun consumeImagePayload(token: String?): String {
        val payload = KardLeafCodeMirrorPayloadStore.consume(token).orEmpty()
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[image] native payload consumed token=${token?.take(12).orEmpty()} payloadLen=${payload.length}",
        )
        return payload
    }

    @JavascriptInterface
    fun consumeDocumentPayload(token: String?): String? {
        val payload = KardLeafCodeMirrorPayloadStore.consume(token)
        KardLeafLog.d(
            CODEMIRROR_TRACE_TAG,
            "document payload consumed token=${token?.take(12).orEmpty()} payloadLen=${payload?.length ?: -1}",
        )
        return payload
    }

    @JavascriptInterface
    fun onEditorReady(version: String?, contentLength: Int) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            onEditorReady.invoke()
            KardLeafLog.d(
                CODEMIRROR_BRIDGE_TRACE_TAG,
                "editor ready version=${version.orEmpty()} contentLength=$contentLength queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onContentApplied(contentLength: Int) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            onContentApplied.invoke(contentLength)
            KardLeafLog.d(
                CODEMIRROR_BRIDGE_TRACE_TAG,
                "content applied len=$contentLength queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onEditorError(message: String?, stack: String?) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            KardLeafLog.e(
                CODEMIRROR_BRIDGE_TRACE_TAG,
                "editor error message=${message.orEmpty()} stack=${stack.orEmpty().take(1200)} queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onContentPatches(
        patchesJson: String,
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            onEditorContentEdited()
            val runAt = SystemClock.elapsedRealtime()
            val applyStart = SystemClock.elapsedRealtime()
            var patchTotal = 0
            try {
                val patches = JSONArray(patchesJson)
                for (index in 0 until patches.length()) {
                    val patch = patches.getJSONObject(index)
                    val start = patch.optInt("start", 0)
                    val deleteCount = patch.optInt("deleteCount", 0)
                    val insertedText = patch.optString("inserted", "")
                    applyPatchOnMain(start, deleteCount, insertedText, selectionStart, selectionEnd)
                    patchTotal += 1
                }
            } catch (error: Throwable) {
                KardLeafLog.e(CODEMIRROR_TRACE_TAG, "bridge patches parse/apply failed len=${patchesJson.length}", error)
            }
            val applyElapsed = SystemClock.elapsedRealtime() - applyStart
            val notifyStart = SystemClock.elapsedRealtime()
            val notifyDelay = scheduleContentChangedNotify()
            val notifyElapsed = SystemClock.elapsedRealtime() - notifyStart
            val totalElapsed = SystemClock.elapsedRealtime() - receivedAt
            patchCount += patchTotal.coerceAtLeast(1)
            if (totalElapsed >= 16L || applyElapsed >= 8L || notifyElapsed >= 8L) {
                slowPatchCount += 1
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastPatchLogAt >= 500L || totalElapsed >= 16L || applyElapsed >= 8L || notifyElapsed >= 8L) {
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "bridge patches perf count=$patchCount slow=$slowPatchCount patches=$patchTotal selection=$selectionStart-$selectionEnd queue=${runAt - receivedAt}ms apply=${applyElapsed}ms notify=${notifyElapsed}ms notifyDelay=${notifyDelay}ms total=${totalElapsed}ms",
                )
                patchCount = 0
                slowPatchCount = 0
                lastPatchLogAt = now
            }
        }
    }

    @JavascriptInterface
    fun onContentPatch(
        start: Int,
        deleteCount: Int,
        insertedText: String,
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            onEditorContentEdited()
            val runAt = SystemClock.elapsedRealtime()
            val applyStart = SystemClock.elapsedRealtime()
            applyPatchOnMain(start, deleteCount, insertedText, selectionStart, selectionEnd)
            val applyElapsed = SystemClock.elapsedRealtime() - applyStart
            val notifyStart = SystemClock.elapsedRealtime()
            val notifyDelay = scheduleContentChangedNotify()
            val notifyElapsed = SystemClock.elapsedRealtime() - notifyStart
            val totalElapsed = SystemClock.elapsedRealtime() - receivedAt
            patchCount += 1
            if (totalElapsed >= 16L || applyElapsed >= 8L || notifyElapsed >= 8L) {
                slowPatchCount += 1
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastPatchLogAt >= 500L || totalElapsed >= 16L || applyElapsed >= 8L || notifyElapsed >= 8L) {
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "bridge patch perf count=$patchCount slow=$slowPatchCount start=$start delete=$deleteCount insertLen=${insertedText.length} selection=$selectionStart-$selectionEnd queue=${runAt - receivedAt}ms apply=${applyElapsed}ms notify=${notifyElapsed}ms notifyDelay=${notifyDelay}ms total=${totalElapsed}ms",
                )
                patchCount = 0
                slowPatchCount = 0
                lastPatchLogAt = now
            }
        }
    }

    @JavascriptInterface
    fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            val applyStart = SystemClock.elapsedRealtime()
            controller.updateExternalSelection(selectionStart, selectionEnd)
            val now = SystemClock.elapsedRealtime()
            selectionCount += 1
            if (now - lastSelectionLogAt >= 1000L) {
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "bridge selection perf count=$selectionCount selection=$selectionStart-$selectionEnd queue=${applyStart - receivedAt}ms apply=${now - applyStart}ms",
                )
                selectionCount = 0
                lastSelectionLogAt = now
            }
        }
    }


    @JavascriptInterface
    fun onHistoryStateChanged(canUndo: Boolean, canRedo: Boolean) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            controller.updateExternalUndoRedoState(canUndo, canRedo)
            onUndoRedoStateChanged.invoke()
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "bridge history state canUndo=$canUndo canRedo=$canRedo queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onEditorScrollPerf(
        event: String?,
        elapsedMs: Double,
        frames: Int,
        slowFrames: Int,
        maxFrameMs: Double,
        avgFrameMs: Double,
        smooth: Boolean,
        scrollTop: Int,
        scrollHeight: Int,
        clientHeight: Int,
    ) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            onEditorScrollMetricsChanged(scrollTop, scrollHeight, clientHeight)
            val contentLen = controller.getCachedSnapshot().content.length
            if (event == "start") {
                userPerfScrollStartY = scrollTop
                KardLeafLog.d(
                    USER_PERF_TRACE_TAG,
                    "editorScroll humanStart mode=codeMirror engine=CODEMIRROR contentLen=$contentLen " +
                        "sizeTier=$userPerfSizeTier scrollY=$scrollTop maxScrollY=${(scrollHeight - clientHeight).coerceAtLeast(0)}",
                )
            } else if (event == "settled") {
                val deltaPx = kotlin.math.abs(scrollTop - userPerfScrollStartY)
                val msPerPx = if (deltaPx > 0) elapsedMs / deltaPx else 0.0
                KardLeafLog.d(
                    USER_PERF_TRACE_TAG,
                    "editorScroll humanSettled mode=codeMirror engine=CODEMIRROR elapsed=${elapsedMs.toInt()}ms " +
                        "frames=$frames slowFrames=$slowFrames maxFrame=${maxFrameMs.toInt()}ms " +
                        "avgFrame=${String.format("%.1f", avgFrameMs)}ms smooth=$smooth " +
                        "contentLen=$contentLen sizeTier=$userPerfSizeTier fromY=$userPerfScrollStartY toY=$scrollTop " +
                        "deltaPx=$deltaPx msPerPx=${String.format("%.3f", msPerPx)} " +
                        "maxScrollY=${(scrollHeight - clientHeight).coerceAtLeast(0)} queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
                )
                userPerfScrollStartY = scrollTop
            } else {
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "bridge scroll metrics event=${event ?: "unknown"} scrollY=$scrollTop maxScrollY=${(scrollHeight - clientHeight).coerceAtLeast(0)} " +
                        "queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
                )
            }
        }
    }

    @JavascriptInterface
    fun onEditorScrollGesture(reason: String?) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            onEditorScrollGesture.invoke()
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "bridge scroll gesture reason=${reason ?: "unknown"} queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onEditorFocusRequest(reason: String?) {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            onEditorFocusRequest.invoke(reason)
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "bridge focus request reason=${reason ?: "unknown"} queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun openExternalUrl(rawUrl: String?) {
        val url = rawUrl?.trim().orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            KardLeafLog.w(CODEMIRROR_TRACE_TAG, "bridge open external ignored url=$url")
            return
        }
        mainHandler.post {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                KardLeafLog.d(CODEMIRROR_TRACE_TAG, "bridge open external url=$url")
            } catch (error: Throwable) {
                KardLeafLog.e(CODEMIRROR_TRACE_TAG, "bridge open external failed url=$url", error)
            }
        }
    }

    @JavascriptInterface
    fun onUserInteraction() {
        val receivedAt = SystemClock.elapsedRealtime()
        mainHandler.post {
            val now = SystemClock.elapsedRealtime()
            interactionCount += 1
            if (now - lastInteractionLogAt >= 1000L) {
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "bridge interaction perf count=$interactionCount queue=${now - receivedAt}ms",
                )
                interactionCount = 0
                lastInteractionLogAt = now
            }
            onUserInteraction.invoke()
        }
    }
}

private fun extractCodeMirrorImageReferencesForTrace(markdown: String): List<String> {
    if (markdown.isBlank()) return emptyList()
    val found = linkedSetOf<String>()
    Regex("""!\[\[([^|\]\n]+)(?:\|[^\]]*)?]]""")
        .findAll(markdown)
        .forEach { found.add(it.groupValues[1].trim().take(160)) }
    Regex("""!\[[^]\n]*]\((?!https?://|data:|file:)([^)\n]+)\)""", RegexOption.IGNORE_CASE)
        .findAll(markdown)
        .forEach { found.add(it.groupValues[1].trim().trim('"', '\'').take(160)) }
    return found.take(24)
}

private fun WebView.hideCodeMirrorKeyboard(reason: String) {
    clearFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
    setCodeMirrorKeyboardInsetPx(0, "hide:$reason")
    KardLeafLog.d(
        CODEMIRROR_TRACE_TAG,
        "android keyboard hide reason=$reason hasFocus=${hasFocus()}",
    )
}

private fun WebView.setCodeMirrorKeyboardInsetPx(keyboardInsetPx: Int, reason: String) {
    val safeInset = keyboardInsetPx.coerceAtLeast(0)
    val quotedReason = JSONObject.quote(reason)
    val script =
        "if (window.KardLeafEditor && window.KardLeafEditor.setKeyboardInsetPx) { " +
            "window.KardLeafEditor.setKeyboardInsetPx($safeInset); 'ok'; " +
            "} else { 'missing'; }"
    evaluateJavascript(script) { result ->
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[ime] set keyboard inset result=$result reason=$quotedReason keyboardInsetPx=$safeInset hasFocus=${hasFocus()}",
        )
    }
}

private fun WebView.codeMirrorKeyboardBottomInsetPx(): Int {
    val targetView = rootView ?: this
    val insets = ViewCompat.getRootWindowInsets(targetView)
        ?: ViewCompat.getRootWindowInsets(this)
        ?: return 0
    if (!insets.isVisible(WindowInsetsCompat.Type.ime())) return 0
    val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
    val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
    val inset = (imeBottom - navBottom).coerceAtLeast(0)
    KardLeafLog.d(
        CODEMIRROR_TRACE_TAG,
        "keyboard inset for cursor imeBottom=$imeBottom navBottom=$navBottom inset=$inset webView=${width}x${height}",
    )
    return inset
}

private fun WebView.ensureCodeMirrorCursorVisible(reason: String, keyboardInsetPx: Int? = null) {
    val quotedReason = JSONObject.quote(reason)
    val insetArgument = keyboardInsetPx?.coerceAtLeast(0)?.toString()
    val script =
        "if (window.KardLeafEditor && window.KardLeafEditor.ensureCursorVisible) { " +
            (if (insetArgument != null) {
                "window.KardLeafEditor.ensureCursorVisible($quotedReason, $insetArgument); 'ok'; "
            } else {
                "window.KardLeafEditor.ensureCursorVisible($quotedReason); 'ok'; "
            }) +
            "} else { 'missing'; }"
    evaluateJavascript(script) { result ->
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[cursor] ensure request result=$result reason=$reason keyboardInsetPx=${keyboardInsetPx ?: -1} hasFocus=${hasFocus()}",
        )
    }
}

private fun decodeJavascriptStringResult(result: String?): String? {
    if (result == null || result == "null" || result == "undefined" || result == "missing") return null
    return try {
        JSONArray("[$result]").getString(0)
    } catch (error: Throwable) {
        KardLeafLog.w(CODEMIRROR_TRACE_TAG, "decode javascript string failed resultLen=${result.length}", error)
        null
    }
}

private fun WebView.pushCodeMirrorImageDataUris(
    images: List<KardLeafCodeMirrorImage>,
    reason: String,
) {
    val jsonArray = JSONArray()
    var totalChars = 0
    var added = 0
    var skippedInvalidDataUri = 0
    var skippedSizeLimit = 0
    for ((index, image) in images.withIndex()) {
        if (added >= CODEMIRROR_IMAGE_PREVIEW_MAX_COUNT) break
        val reference = image.reference.takeIf { it.isNotBlank() } ?: continue
        val dataUri = image.dataUri.trim()
        if (!dataUri.startsWith("data:image/", ignoreCase = true)) {
            skippedInvalidDataUri += 1
            KardLeafLog.d(
                CODEMIRROR_DEBUG_TRACE_TAG,
                "[image] push skip index=$index reason=invalidDataUri reference=$reference " +
                    "dataUriLen=${image.dataUri.length} prefix=${image.dataUri.take(32)}",
            )
            continue
        }
        val nextTotal = totalChars + reference.length + dataUri.length
        if (nextTotal > CODEMIRROR_IMAGE_PREVIEW_MAX_TOTAL_CHARS) {
            skippedSizeLimit += 1
            KardLeafLog.d(
                CODEMIRROR_DEBUG_TRACE_TAG,
                "[image] push skip index=$index reason=sizeLimit reference=$reference dataUriLen=${dataUri.length} " +
                    "totalBefore=$totalChars limit=$CODEMIRROR_IMAGE_PREVIEW_MAX_TOTAL_CHARS",
            )
            continue
        }
        jsonArray.put(
            JSONObject()
                .put("reference", reference)
                .put("dataUri", dataUri),
        )
        totalChars = nextTotal
        added += 1
    }
    val payload = jsonArray.toString()
    val token = KardLeafCodeMirrorPayloadStore.put(payload)
    val quotedToken = JSONObject.quote(token)
    KardLeafLog.d(
        CODEMIRROR_DEBUG_TRACE_TAG,
        "[image] native payload stored token=${token.take(12)} payloadLen=${payload.length} " +
            "reason=$reason count=$added totalChars=$totalChars",
    )
    val script =
        "(function() { try { " +
            "if (!window.KardLeafEditor || !window.KardLeafEditor.setImageDataUris) return 'missing-editor'; " +
            "if (!window.KardLeafAndroid || !window.KardLeafAndroid.consumeImagePayload) return 'missing-bridge'; " +
            "var payload = window.KardLeafAndroid.consumeImagePayload($quotedToken); " +
            "if (!payload) return 'missing-payload'; " +
            "var result = window.KardLeafEditor.setImageDataUris(payload); " +
            "return result == null ? 'ok-null' : String(result); " +
            "} catch (error) { " +
            "return 'error:' + (error && (error.stack || error.message) ? (error.stack || error.message) : error); " +
            "} })();"
    evaluateJavascript(script) { result ->
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[image] push result=$result reason=$reason count=$added totalChars=$totalChars sourceCount=${images.size} " +
                "payloadLen=${payload.length} skippedInvalidDataUri=$skippedInvalidDataUri skippedSizeLimit=$skippedSizeLimit " +
                "first=${images.firstOrNull()?.reference.orEmpty()} firstDataUriLen=${images.firstOrNull()?.dataUri?.length ?: 0}",
        )
    }
}

private fun WebView.pushDocumentToCodeMirror(
    content: String,
    selection: TextRange,
    contentTextSize: TextUnit,
    contentLineHeightMultiplier: Float,
    contentLetterSpacingSp: Float,
    contentParagraphSpacingDp: Float,
    contentFontFamily: String,
    isDark: Boolean,
    livePreviewEnabled: Boolean,
    reason: String,
    openStartRealtimeMs: Long? = null,
    sizeTier: String = codeMirrorUserPerfNoteSizeTier(content.length),
    onDone: () -> Unit = {},
) {
    val start = selection.start.coerceIn(0, content.length)
    val end = selection.end.coerceIn(0, content.length)
    val fontSize = contentTextSize.value
    val fontFamily = JSONObject.quote(contentFontFamily)
    val livePreviewFlag = if (livePreviewEnabled) "true" else "false"
    val darkFlag = if (isDark) "true" else "false"
    val buildStart = SystemClock.elapsedRealtime()
    val token = KardLeafCodeMirrorPayloadStore.put(content)
    val quotedToken = JSONObject.quote(token)
    val payloadStoreElapsed = SystemClock.elapsedRealtime() - buildStart
    val script =
        "(function() { " +
            "if (!window.KardLeafEditor || !window.KardLeafEditor.setLivePreviewEnabled || !window.KardLeafEditor.setDocument) return 'missing-editor'; " +
            "if (!window.KardLeafAndroid || !window.KardLeafAndroid.consumeDocumentPayload) return 'missing-bridge'; " +
            "var content = window.KardLeafAndroid.consumeDocumentPayload($quotedToken); " +
            "if (content == null) return 'missing-payload'; " +
            "window.KardLeafEditor.setLivePreviewEnabled($livePreviewFlag); " +
            "window.KardLeafEditor.setDocument(content, $start, $end, $fontSize, $darkFlag, " +
            "{lineHeight:$contentLineHeightMultiplier,letterSpacing:$contentLetterSpacingSp,paragraphSpacing:$contentParagraphSpacingDp,fontFamily:$fontFamily}); " +
            "return 'ok'; " +
            "})();"
    val buildElapsed = SystemClock.elapsedRealtime() - buildStart
    KardLeafLog.d(
        CODEMIRROR_TRACE_TAG,
        "push document start reason=$reason len=${content.length} payloadStore=${payloadStoreElapsed}ms build=${buildElapsed}ms selection=$start-$end fontSize=$fontSize livePreview=$livePreviewEnabled dark=$isDark",
    )
    openStartRealtimeMs?.let { startMs ->
        KardLeafLog.d(
            USER_PERF_TRACE_TAG,
            "editorOpen codeMirrorSetDocumentStart elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                "engine=CODEMIRROR contentLen=${content.length} sizeTier=$sizeTier payloadStore=${payloadStoreElapsed}ms build=${buildElapsed}ms " +
                "livePreview=$livePreviewEnabled",
        )
    }
    val evalStart = SystemClock.elapsedRealtime()
    evaluateJavascript(script) { result ->
        val evalElapsed = SystemClock.elapsedRealtime() - evalStart
        KardLeafLog.d(
            CODEMIRROR_TRACE_TAG,
            "push document done reason=$reason result=$result len=${content.length} eval=${evalElapsed}ms total=${SystemClock.elapsedRealtime() - buildStart}ms",
        )
        openStartRealtimeMs?.let { startMs ->
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorOpen codeMirrorSetDocumentDone elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "engine=CODEMIRROR result=$result contentLen=${content.length} sizeTier=$sizeTier " +
                    "eval=${evalElapsed}ms total=${SystemClock.elapsedRealtime() - buildStart}ms",
            )
        }
        onDone()
    }
}
