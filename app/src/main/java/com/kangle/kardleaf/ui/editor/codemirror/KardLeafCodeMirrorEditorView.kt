package com.kangle.kardleaf.ui.editor.codemirror

import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.ui.editor.api.EditorFastScrollMetrics
import com.kangle.kardleaf.ui.editor.EditorViewportAnchor
import com.kangle.kardleaf.ui.editor.codeMirrorCrLfCount
import com.kangle.kardleaf.ui.editor.codeMirrorNormalizedLength
import com.kangle.kardleaf.ui.editor.parseEditorViewportAnchor
import com.kangle.kardleaf.ui.editor.toCodeMirrorAnchor
import com.kangle.kardleaf.ui.editor.toJson
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorController
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorSnapshot
import com.kangle.kardleaf.ui.ImageClickSource
import com.kangle.kardleaf.ui.KardLeafImageClickTarget
import com.kangle.kardleaf.ui.occurrenceIndexForImageReference
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
private const val CODEMIRROR_LIVE_PREVIEW_LARGE_DOC_LIMIT = 200_000
private const val CODEMIRROR_IMAGE_PREVIEW_MAX_COUNT = 24
private const val CODEMIRROR_IMAGE_PREVIEW_MAX_TOTAL_CHARS = 2_000_000
private val CODEMIRROR_NEUTRAL_ACCENT = Color(0xFF9A9A9A)

private fun buildCodeMirrorAssetUrl(livePreviewEnabled: Boolean): String =
    buildString {
        append(CODEMIRROR_ASSET_BASE_URL)
        append("?livePreview=")
        append(livePreviewEnabled)
        if (CODEMIRROR_TABLE_TRACE_ENABLED) append("&cmTrace=1")
    }

private fun codeMirrorNavigationBlockReason(uri: Uri): String? {
    if (uri.scheme != "file") return "scheme"
    if (!uri.authority.isNullOrEmpty()) return "authority"
    if (uri.userInfo != null) return "user_info"
    if (uri.port != -1) return "port"
    if (uri.path != "/android_asset/codemirror-editor/index.html") return "path"
    if (uri.queryParameterNames.any { it != "livePreview" && it != "cmTrace" }) return "query"
    if (uri.getQueryParameters("livePreview").let { it.size > 1 || it.any { value -> value != "true" && value != "false" } }) return "query"
    if (uri.getQueryParameters("cmTrace").let { it.size > 1 || it.any { value -> value != "1" } }) return "query"
    return null
}

private fun shouldBlockCodeMirrorMainFrameNavigation(uri: Uri): Boolean {
    val reason = codeMirrorNavigationBlockReason(uri) ?: return false
    KardLeafLog.w(
        CODEMIRROR_TRACE_TAG,
        "event=navigation_blocked scheme=${uri.scheme.orEmpty()} host=${uri.host.orEmpty()} " +
            "path=${uri.path.orEmpty()} reason=$reason",
    )
    return true
}

private class CodeMirrorWebViewLifecycleState(
    val bridgeHost: KardLeafCodeMirrorBridgeHost,
    val initialLoadRunnable: Runnable,
    val loadTimeoutRunnable: Runnable,
) {
    @Volatile
    var released: Boolean = false
    var active: Boolean? = null
}

private fun WebView.codeMirrorLifecycleState(): CodeMirrorWebViewLifecycleState? =
    getTag(R.id.codemirror_lifecycle_state_tag) as? CodeMirrorWebViewLifecycleState

private fun WebView.isCodeMirrorReleased(): Boolean =
    (this as? CodeMirrorImeTraceWebView)?.lifecycleReleased == true ||
        codeMirrorLifecycleState()?.released == true

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
    border: Color,
    soft: Color,
): String {
    val payload = JSONObject()
        .put("background", background.toCssHex())
        .put("foreground", foreground.toCssHex())
        .put("muted", muted.toCssHex())
        .put("border", border.toCssRgba(0.72f))
        .put("soft", soft.toCssRgba(0.48f))
        .put("selection", CODEMIRROR_NEUTRAL_ACCENT.toCssRgba(0.24f))
        .put("codeBackground", soft.toCssRgba(0.82f))
        .put("heading", foreground.toCssHex())
        .put("link", CODEMIRROR_NEUTRAL_ACCENT.toCssHex())
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
    private const val SNAPSHOT_INTERVAL = 32
    private const val PAYLOAD_STORE_TRACE_TAG = "KardLeafPayloadStore"

    private data class Entry(
        val payload: String,
        val createdAtMs: Long,
    )

    private data class PayloadObservation(
        val event: String,
        val payloadCount: Int,
        val totalPayloadChars: Long,
        val maxPayloadChars: Int,
        val peakPayloadChars: Int,
        val peakPayloadCount: Int,
        val peakTotalPayloadChars: Long,
        val createdCount: Long,
        val consumedCount: Long,
        val expiredCount: Long,
        val removedCount: Long,
        val payloadChars: Long,
        val payloadAgeMs: Long,
    )

    private val payloads = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val serial = AtomicLong(0L)
    private var totalPayloadChars = 0L
    private var maxPayloadChars = 0
    private var peakPayloadChars = 0
    private var peakPayloadCount = 0
    private var peakTotalPayloadChars = 0L
    private var createdCount = 0L
    private var consumedCount = 0L
    private var expiredCount = 0L
    private var removedCount = 0L
    private var eventsSinceSnapshot = 0
    private var lastLoggedPeakPayloadChars = 0

    @Synchronized
    fun put(payload: String): String {
        val now = SystemClock.elapsedRealtime()
        pruneExpiredLocked(now)
        val token = "${SystemClock.elapsedRealtimeNanos()}-${serial.incrementAndGet()}"
        payloads[token] = Entry(payload, now)
        totalPayloadChars += payload.length.toLong()
        createdCount++
        maxPayloadChars = maxOf(maxPayloadChars, payload.length)
        val previousPeak = peakPayloadChars
        peakPayloadChars = maxOf(peakPayloadChars, payload.length)
        peakPayloadCount = maxOf(peakPayloadCount, payloads.size)
        peakTotalPayloadChars = maxOf(peakTotalPayloadChars, totalPayloadChars)
        val snapshotDue = takeSnapshotIfDueLocked()
        trimLocked(now)
        val logNewPeak =
            peakPayloadChars > previousPeak &&
                (lastLoggedPeakPayloadChars == 0 ||
                    peakPayloadChars.toLong() >= lastLoggedPeakPayloadChars.toLong() * 2L)
        when {
            logNewPeak -> {
                lastLoggedPeakPayloadChars = peakPayloadChars
                logObservationLocked("item_peak", payload.length.toLong(), 0L)
            }
            snapshotDue -> logObservationLocked("snapshot", payload.length.toLong(), 0L)
        }
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
        totalPayloadChars -= entry.payload.length.toLong()
        consumedCount++
        if (entry.payload.length == maxPayloadChars) recalculateMaxPayloadCharsLocked()
        if (takeSnapshotIfDueLocked()) {
            logObservationLocked("snapshot", entry.payload.length.toLong(), now - entry.createdAtMs)
        }
        KardLeafLog.d(
            CODEMIRROR_BRIDGE_TRACE_TAG,
            "payload consume token=${token.take(12)} len=${entry.payload.length} age=${now - entry.createdAtMs}ms pending=${payloads.size}",
        )
        return entry.payload
    }

    private fun pruneExpiredLocked(now: Long) {
        val iterator = payloads.entries.iterator()
        var removedItems = 0
        var removedChars = 0L
        var maxAgeMs = 0L
        var removedCurrentMax = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.createdAtMs > MAX_PAYLOAD_AGE_MS) {
                val payloadChars = entry.value.payload.length
                val payloadAgeMs = now - entry.value.createdAtMs
                KardLeafLog.w(
                    CODEMIRROR_BRIDGE_TRACE_TAG,
                    "payload expired token=${entry.key.take(12)} len=$payloadChars age=${payloadAgeMs}ms",
                )
                totalPayloadChars -= payloadChars.toLong()
                expiredCount++
                removedItems++
                removedChars += payloadChars.toLong()
                maxAgeMs = maxOf(maxAgeMs, payloadAgeMs)
                removedCurrentMax = removedCurrentMax || payloadChars == maxPayloadChars
                iterator.remove()
            }
        }
        if (removedCurrentMax) recalculateMaxPayloadCharsLocked()
        if (removedItems > 0) logObservationLocked("expire", removedChars, maxAgeMs)
    }

    private fun trimLocked(now: Long) {
        val iterator = payloads.entries.iterator()
        var removedItems = 0
        var removedChars = 0L
        var maxAgeMs = 0L
        var removedCurrentMax = false
        while (payloads.size > MAX_PAYLOADS && iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.createdAtMs < MIN_TRIM_AGE_MS && payloads.size <= HARD_MAX_PAYLOADS) break
            val payloadChars = entry.value.payload.length
            val payloadAgeMs = now - entry.value.createdAtMs
            KardLeafLog.w(
                CODEMIRROR_BRIDGE_TRACE_TAG,
                "payload expired reason=trim token=${entry.key.take(12)} len=$payloadChars age=${payloadAgeMs}ms pending=${payloads.size}",
            )
            totalPayloadChars -= payloadChars.toLong()
            removedCount++
            removedItems++
            removedChars += payloadChars.toLong()
            maxAgeMs = maxOf(maxAgeMs, payloadAgeMs)
            removedCurrentMax = removedCurrentMax || payloadChars == maxPayloadChars
            iterator.remove()
        }
        if (removedCurrentMax) recalculateMaxPayloadCharsLocked()
        if (removedItems > 0) logObservationLocked("trim", removedChars, maxAgeMs)
    }

    private fun takeSnapshotIfDueLocked(): Boolean {
        eventsSinceSnapshot++
        if (eventsSinceSnapshot < SNAPSHOT_INTERVAL) return false
        eventsSinceSnapshot = 0
        return true
    }

    private fun recalculateMaxPayloadCharsLocked() {
        maxPayloadChars = payloads.values.maxOfOrNull { it.payload.length } ?: 0
    }

    private fun logObservationLocked(event: String, payloadChars: Long, payloadAgeMs: Long) {
        val observation =
            PayloadObservation(
                event = event,
                payloadCount = payloads.size,
                totalPayloadChars = totalPayloadChars,
                maxPayloadChars = maxPayloadChars,
                peakPayloadChars = peakPayloadChars,
                peakPayloadCount = peakPayloadCount,
                peakTotalPayloadChars = peakTotalPayloadChars,
                createdCount = createdCount,
                consumedCount = consumedCount,
                expiredCount = expiredCount,
                removedCount = removedCount,
                payloadChars = payloadChars,
                payloadAgeMs = payloadAgeMs,
            )
        val message =
            "event=${observation.event} payloads=${observation.payloadCount} " +
                "totalChars=${observation.totalPayloadChars} maxPayloadChars=${observation.maxPayloadChars} " +
                "peakPayloadChars=${observation.peakPayloadChars} peakPayloads=${observation.peakPayloadCount} " +
                "peakTotalChars=${observation.peakTotalPayloadChars} created=${observation.createdCount} " +
                "consumed=${observation.consumedCount} expired=${observation.expiredCount} " +
                "removed=${observation.removedCount} payloadChars=${observation.payloadChars} " +
                "payloadAgeMs=${observation.payloadAgeMs}"
        if (event == "expire" || event == "trim") {
            KardLeafLog.w(PAYLOAD_STORE_TRACE_TAG, message)
        } else {
            KardLeafLog.d(PAYLOAD_STORE_TRACE_TAG, message)
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
        if (view.isCodeMirrorReleased()) return
        val safeRatio = ratio.coerceIn(0f, 1f)
        view.evaluateJavascript(
            "if (window.KardLeafEditor && window.KardLeafEditor.fastScrollToRatio) { window.KardLeafEditor.fastScrollToRatio($safeRatio); } else { 'missing'; }",
        ) { result ->
            if (view.isCodeMirrorReleased()) return@evaluateJavascript
            KardLeafLog.d(CODEMIRROR_TRACE_TAG, "fast scroll ratio=$safeRatio result=$result")
            refreshScrollMetrics()
        }
    }

    fun scrollToOffset(offset: Int) {
        val view = webView ?: return
        if (view.isCodeMirrorReleased()) return
        view.evaluateJavascript(
            "if (window.KardLeafEditor && window.KardLeafEditor.scrollToOffset) { window.KardLeafEditor.scrollToOffset(${offset.coerceAtLeast(0)}); } else { 'missing'; }",
            null,
        )
    }

    internal fun getViewportAnchor(onResult: (EditorViewportAnchor?) -> Unit) {
        val view = webView
        if (view == null || view.isCodeMirrorReleased()) {
            onResult(null)
            return
        }
        view.evaluateJavascript(
            "if (window.KardLeafEditor && window.KardLeafEditor.getViewportAnchor) { window.KardLeafEditor.getViewportAnchor(); } else { null; }",
        ) { result ->
            onResult(parseEditorViewportAnchor(result))
        }
    }

    internal fun scrollViewportToAnchor(anchor: EditorViewportAnchor, onResult: (String?) -> Unit = {}) {
        val view = webView
        if (view == null || view.isCodeMirrorReleased()) {
            onResult(null)
            return
        }
        view.evaluateJavascript(
            "if (window.KardLeafEditor && window.KardLeafEditor.scrollViewportToAnchor) { window.KardLeafEditor.scrollViewportToAnchor(${anchor.toJson()}); } else { 'missing'; }",
        ) { result ->
            if (view.isCodeMirrorReleased()) return@evaluateJavascript
            parseCodeMirrorViewportAnchorScrollTop(result)?.let { lastScrollTop = it }
            view.postOnAnimation {
                if (!view.isCodeMirrorReleased()) onResult(result)
            }
        }
    }

    internal fun getScrollTop(): Int = lastScrollTop

    internal fun hasFocus(): Boolean = webView?.hasFocus() == true

    fun refreshScrollMetrics() {
        val view = webView ?: return
        if (view.isCodeMirrorReleased()) return
        view.evaluateJavascript(
            "if (window.KardLeafEditor && window.KardLeafEditor.getScrollMetrics) { window.KardLeafEditor.getScrollMetrics(); } else { null; }",
        ) { result ->
            if (view.isCodeMirrorReleased()) return@evaluateJavascript
            val metrics = parseCodeMirrorScrollMetrics(result) ?: return@evaluateJavascript
            updateScrollMetrics(metrics.scrollTop, metrics.scrollHeight, metrics.clientHeight)
        }
    }
}

internal fun parseCodeMirrorViewportAnchorScrollTop(result: String?): Int? =
    result
        ?.trim('"')
        ?.takeIf { it.startsWith("ok:") }
        ?.substringAfterLast(':')
        ?.toIntOrNull()

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
    return "range=$safeStart..$safeEnd len=${safeEnd - safeStart}"
}

private class CodeMirrorImeTraceWebView(context: Context) : WebView(context) {
    @Volatile
    var lifecycleReleased: Boolean = false
        private set
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
    @Volatile
    private var codeBlockCopyTouch = false
    private val visibleFrame = Rect()
    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        traceGlobalLayout()
    }

    private fun traceCommon(): String =
        "key=$traceKey docLen=${traceContentLength()} sizeTier=${traceSizeTier()} livePreview=${traceLivePreviewEnabled()} " +
            "pageReady=${tracePageReady()} width=$width height=$height attached=$isAttachedToWindow hasFocus=${hasFocus()}"

    fun markCodeBlockCopyTouch() {
        codeBlockCopyTouch = true
    }

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
        if (lifecycleReleased) return
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
                if (lifecycleReleased || runId != keyboardFrameTraceRunId) return
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

    fun releaseTraceCallbacks() {
        if (lifecycleReleased) return
        lifecycleReleased = true
        keyboardFrameTraceRunId += 1
        codeBlockCopyTouch = false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        KardLeafLog.d(CODEMIRROR_IME_TRACE_TAG, "webView attached ${traceCommon()}")
        traceGlobalLayout()
    }

    override fun onDetachedFromWindow() {
        codeBlockCopyTouch = false
        runCatching {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
        }
        KardLeafLog.d(CODEMIRROR_IME_TRACE_TAG, "webView detached ${traceCommon()}")
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP && codeBlockCopyTouch) {
            codeBlockCopyTouch = false
            val cancelEvent = MotionEvent.obtain(event).apply {
                action = MotionEvent.ACTION_CANCEL
            }
            return try {
                super.onTouchEvent(cancelEvent)
                true
            } finally {
                cancelEvent.recycle()
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            codeBlockCopyTouch = false
        }
        val handled = super.onTouchEvent(event)
        return handled
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
internal fun KardLeafCodeMirrorEditor(
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
    active: Boolean = true,
    requestFocusToken: Int = 0,
    onFocusRequestHandled: (Int) -> Unit = {},
    preferredFocusSelection: TextRange? = null,
    initialViewportAnchor: EditorViewportAnchor? = null,
    onInitialViewportAnchorApplied: (EditorViewportAnchor, String?) -> Unit = { _, _ -> },
    onDrawingImageClicked: (KardLeafImageClickTarget) -> Unit = {},
    wikilinkNotes: List<Note> = emptyList(),
    onInternalLinkOpen: (String) -> Unit = {},
    resolveImages: suspend (String) -> List<KardLeafCodeMirrorImage> = { emptyList() },
    userPerfOpenStartRealtimeMs: Long? = null,
    userPerfSizeTier: String = codeMirrorUserPerfNoteSizeTier(initialContent.length),
    onUserPerfBodyRendered: (Int, String) -> Unit = { _, _ -> },
    imeAnimationTargetBottomPx: Int = 0,
    modifier: Modifier = Modifier,
) {
    val webViewRef = remember { AtomicReference<WebView?>(null) }
    val codeMirrorAssetUrl = buildCodeMirrorAssetUrl(livePreviewEnabled)
    val appContext = LocalContext.current.applicationContext
    val imeViewportInset = with(LocalDensity.current) { imeAnimationTargetBottomPx.toDp() }
    val appColorScheme = MaterialTheme.colorScheme
    val codeMirrorBackgroundArgb = appColorScheme.background.toArgb()
    val codeMirrorThemeColorsScript = buildCodeMirrorThemeColorsScript(
        background = appColorScheme.background,
        foreground = appColorScheme.onBackground,
        muted = appColorScheme.onSurfaceVariant,
        border = appColorScheme.outlineVariant,
        soft = appColorScheme.surfaceVariant,
    )
    val latestOnTitleChanged by rememberUpdatedState(onTitleChanged)
    val latestOnContentChanged by rememberUpdatedState(onContentChanged)
    val latestOnContentEdited by rememberUpdatedState(onContentEdited)
    val latestOnUndoRedoStateChanged by rememberUpdatedState(onUndoRedoStateChanged)
    val latestOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val latestOnFastScrollSourceScrolled by rememberUpdatedState(onFastScrollSourceScrolled)
    val latestOnDrawingImageClicked by rememberUpdatedState(onDrawingImageClicked)
    val latestWikilinkNotes by rememberUpdatedState(wikilinkNotes)
    val latestOnInternalLinkOpen by rememberUpdatedState(onInternalLinkOpen)
    val latestOnFocusRequestHandled by rememberUpdatedState(onFocusRequestHandled)
    val latestInitialViewportAnchor by rememberUpdatedState(initialViewportAnchor)
    val latestOnInitialViewportAnchorApplied by rememberUpdatedState(onInitialViewportAnchorApplied)
    val latestResolveImages by rememberUpdatedState(resolveImages)
    val latestInitialContent by rememberUpdatedState(initialContent)
    val latestIsDark by rememberUpdatedState(isDark)
    val latestLivePreviewEnabled by rememberUpdatedState(livePreviewEnabled)
    val latestUserPerfBodyRendered by rememberUpdatedState(onUserPerfBodyRendered)
    var pageReady by remember(documentKey) { mutableStateOf(false) }
    var codeMirrorContentApplied by remember(documentKey) { mutableStateOf(false) }
    val pendingSearchState = remember(documentKey) { mutableStateOf<List<Any>?>(null) }
    val pendingSearchSelection = remember(documentKey) { mutableStateOf<TextRange?>(null) }
    var hasShownInitialContent by remember(documentKey) { mutableStateOf(false) }
    var lastPushedContentLength by remember(documentKey) { mutableStateOf(-1) }
    var lastPushDocumentAt by remember(documentKey) { mutableStateOf(0L) }
    var lastContentAppliedAt by remember(documentKey) { mutableStateOf(0L) }
    var contentApplyRetryCount by remember(documentKey, initialContent) { mutableStateOf(0) }
    var hasEditorSideChanges by remember(documentKey) { mutableStateOf(false) }
    var imageResolveVersion by remember(documentKey) { mutableStateOf(0) }
    var imageResolveImmediate by remember(documentKey) { mutableStateOf(false) }
    val composePerfCount = remember(documentKey) { intArrayOf(0) }
    val composePerfLastAt = remember(documentKey) { longArrayOf(SystemClock.elapsedRealtime()) }
    val imeTraceRequestSerial = remember(documentKey) { intArrayOf(0) }
    val imeTraceLastRequestAt = remember(documentKey) { longArrayOf(0L) }
    val androidViewUpdateCount = remember(documentKey) { intArrayOf(0) }
    val androidViewUpdateLastAt = remember(documentKey) { longArrayOf(SystemClock.elapsedRealtime()) }
    val handledFocusToken = remember(documentKey) { AtomicInteger(-1) }

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

    LaunchedEffect(pageReady, imeAnimationTargetBottomPx) {
        if (!pageReady || imeAnimationTargetBottomPx <= 0) return@LaunchedEffect
        withFrameNanos { }
        val webView = webViewRef.get() ?: return@LaunchedEffect
        if (webView.isCodeMirrorReleased()) return@LaunchedEffect
        webView.evaluateJavascript(
            "if (window.KardLeafEditor && window.KardLeafEditor.prepareImeReveal) { " +
                "window.KardLeafEditor.prepareImeReveal($imeAnimationTargetBottomPx); } else { 'missing'; }",
        ) { result ->
            if (webView.isCodeMirrorReleased()) return@evaluateJavascript
            KardLeafLog.d(
                CODEMIRROR_IME_TRACE_TAG,
                "ime viewport reveal result=$result target=$imeAnimationTargetBottomPx key=$documentKey",
            )
        }
    }

    LaunchedEffect(documentKey, initialTitle) {
        controller.updateExternalTitle(initialTitle)
    }

    LaunchedEffect(requestFocusToken, pageReady, codeMirrorContentApplied) {
        if (
            requestFocusToken <= 0 ||
            handledFocusToken.get() == requestFocusToken ||
            !pageReady ||
            !codeMirrorContentApplied
        ) {
            return@LaunchedEffect
        }
        handledFocusToken.set(requestFocusToken)
        val selection = preferredFocusSelection ?: controller.getSelection()
        val webView = webViewRef.get() ?: return@LaunchedEffect
        if (webView.isCodeMirrorReleased()) return@LaunchedEffect
        if (!webView.hasFocus()) webView.requestFocus()
        val script =
            "if (window.KardLeafEditor && window.KardLeafEditor.selectRange) { " +
                "window.KardLeafEditor.selectRange(${selection.start}, ${selection.end}); 'ok'; " +
                "} else { 'missing'; }"
        webView.evaluateJavascript(script) { result ->
            if (webView.isCodeMirrorReleased()) return@evaluateJavascript
            val keyboardShown =
                appContext.getSystemService(Context.INPUT_METHOD_SERVICE)
                    ?.let { it as? InputMethodManager }
                    ?.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT) == true
            KardLeafLog.d(
                CODEMIRROR_IME_TRACE_TAG,
                "focus token handled token=$requestFocusToken result=$result selection=${selection.start}:${selection.end} " +
                    "keyboardShown=$keyboardShown pageReady=$pageReady contentApplied=$codeMirrorContentApplied key=$documentKey",
            )
            latestOnFocusRequestHandled(requestFocusToken)
        }
    }

    LaunchedEffect(pageReady, initialTitle, titleHint, showTitle, titleTextSize) {
        if (pageReady) {
            val currentTitle = controller.getCachedSnapshot().title
            val titlePayload = JSONObject.quote(currentTitle)
            val hintPayload = JSONObject.quote(titleHint)
            val visibleFlag = if (showTitle) "true" else "false"
            val fontSize = titleTextSize.value.coerceAtLeast(1f)
            webViewRef.get()?.evaluateJavascript(
                "if (window.KardLeafEditor && window.KardLeafEditor.setTitleState) { " +
                    "window.KardLeafEditor.setTitleState($titlePayload, $hintPayload, $visibleFlag, $fontSize); 'ok'; " +
                    "} else { 'missing'; }",
                null,
            )
        }
    }

    LaunchedEffect(pageReady, livePreviewEnabled) {
        if (pageReady) {
            val livePreviewFlag = if (livePreviewEnabled) "true" else "false"
            val webView = webViewRef.get()
            webView?.evaluateJavascript(
                "if (window.KardLeafEditor && window.KardLeafEditor.setLivePreviewEnabled) { " +
                    "window.KardLeafEditor.setLivePreviewEnabled($livePreviewFlag); " +
                    "} else { 'missing'; }",
            ) { result ->
                KardLeafLog.d(CODEMIRROR_TRACE_TAG, "live preview applied enabled=$livePreviewEnabled result=$result")
                if (result?.contains("reload_required") == true) {
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "live preview deferred until next editor surface key=$documentKey",
                    )
                }
            }
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
            val selection = preferredFocusSelection ?: controller.getSelection()
            val pushStartedAt = SystemClock.elapsedRealtime()
            val attempt = contentApplyRetryCount + 1
            codeMirrorContentApplied = false
            lastContentAppliedAt = 0L
            lastPushedContentLength = initialContent.length
            lastPushDocumentAt = pushStartedAt
            val normalizedContentLength = codeMirrorNormalizedLength(initialContent)
            val crlfCount = codeMirrorCrLfCount(initialContent)
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "push document audit key=$documentKey rawLen=${initialContent.length} " +
                    "normalizedLen=$normalizedContentLength crlfCount=$crlfCount " +
                    "lfCount=${initialContent.count { it == '\n' }} attempt=$attempt",
            )
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
                "search bridge selection request bridge=setContentFromAndroid addToHistory=false key=$documentKey len=${text.length} " +
                    "selection=${selection.start}-${selection.end} " +
                    "selectedText=${codeMirrorSearchSnippetForLog(text, selection.start, selection.end)} " +
                    "livePreview=$livePreviewEnabled pageReady=$pageReady sideChanges=$hasEditorSideChanges " +
                    "textSameInitial=${text == latestInitialContent}",
            )
            val webView = webViewRef.get() ?: return@setExternalContentUpdater
            webView.post {
                if (webView.isCodeMirrorReleased()) return@post
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
                webView.evaluateJavascript(script) { result ->
                    if (webView.isCodeMirrorReleased()) return@evaluateJavascript
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
        controller.setExternalRangeReplacer { start, end, replacement, selection ->
            if (!pageReady) return@setExternalRangeReplacer false
            val webView = webViewRef.get() ?: return@setExternalRangeReplacer false
            val token = KardLeafCodeMirrorPayloadStore.put(replacement)
            val quotedToken = JSONObject.quote(token)
            webView.post {
                if (webView.isCodeMirrorReleased()) return@post
                webView.evaluateJavascript(
                    "(function() { " +
                        "if (!window.KardLeafEditor || !window.KardLeafEditor.replaceRangeFromAndroid) return 'missing-editor'; " +
                        "if (!window.KardLeafAndroid || !window.KardLeafAndroid.consumeDocumentPayload) return 'missing-bridge'; " +
                        "var replacement = window.KardLeafAndroid.consumeDocumentPayload($quotedToken); " +
                        "if (replacement == null) return 'missing-payload'; " +
                        "return window.KardLeafEditor.replaceRangeFromAndroid($start, $end, replacement, ${selection.start}, ${selection.end}); " +
                        "})();",
                ) { result ->
                    if (webView.isCodeMirrorReleased()) return@evaluateJavascript
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "external range replace result=$result addToHistory=true range=$start..$end " +
                            "insertLen=${replacement.length} key=$documentKey",
                    )
                }
            }
            true
        }
        controller.setExternalSelectionUpdater { selection ->
            val webView = webViewRef.get() ?: return@setExternalSelectionUpdater
            webView.post {
                if (webView.isCodeMirrorReleased()) return@post
                webView.evaluateJavascript(
                    "if (window.KardLeafEditor && window.KardLeafEditor.selectRange) { " +
                        "window.KardLeafEditor.selectRange(${selection.start}, ${selection.end}); 'ok'; } else { 'missing'; }",
                ) { result ->
                    if (webView.isCodeMirrorReleased()) return@evaluateJavascript
                    KardLeafLog.d(CODEMIRROR_TRACE_TAG, "external selection result=$result selection=${selection.start}-${selection.end} key=$documentKey")
                }
            }
        }
        controller.updateExternalUndoRedoState(false, false)
        controller.setExternalUndoRedoActions(
            undoAction = {
                val webView = webViewRef.get()
                webView?.post {
                    if (webView.isCodeMirrorReleased()) return@post
                    webView.evaluateJavascript(
                        "if (window.KardLeafEditor && window.KardLeafEditor.undo) { window.KardLeafEditor.undo(); } else { 'missing'; }",
                    ) { result ->
                        if (webView.isCodeMirrorReleased()) return@evaluateJavascript
                        KardLeafLog.d(CODEMIRROR_TRACE_TAG, "external undo result=$result key=$documentKey")
                    }
                }
            },
            redoAction = {
                val webView = webViewRef.get()
                webView?.post {
                    if (webView.isCodeMirrorReleased()) return@post
                    webView.evaluateJavascript(
                        "if (window.KardLeafEditor && window.KardLeafEditor.redo) { window.KardLeafEditor.redo(); } else { 'missing'; }",
                    ) { result ->
                        if (webView.isCodeMirrorReleased()) return@evaluateJavascript
                        KardLeafLog.d(CODEMIRROR_TRACE_TAG, "external redo result=$result key=$documentKey")
                    }
                }
            },
        )
        controller.setExternalCommandExecutor { command, args ->
            val webView = webViewRef.get()
            val searchCommand = command == "setSearchState" ||
                command == "clearSearchState" ||
                command == "selectRange"
            val shouldDeferSearchCommand = !pageReady ||
                (command == "selectRange" && !codeMirrorContentApplied)
            if (searchCommand) {
                when (command) {
                    "setSearchState" -> {
                        pendingSearchState.value =
                            if (webView == null || !pageReady || !codeMirrorContentApplied) args else null
                    }
                    "clearSearchState" -> {
                        pendingSearchState.value = null
                        pendingSearchSelection.value = null
                    }
                    "selectRange" -> {
                        if (webView == null || shouldDeferSearchCommand) {
                            val start = (args.getOrNull(0) as? Number)?.toInt()
                            val end = (args.getOrNull(1) as? Number)?.toInt() ?: start
                            if (start != null && end != null) {
                                pendingSearchSelection.value = TextRange(start, end)
                            }
                        } else {
                            pendingSearchSelection.value = null
                        }
                    }
                }
            }
            if (searchCommand && (webView == null || shouldDeferSearchCommand)) {
                return@setExternalCommandExecutor true
            }
            if (webView == null) return@setExternalCommandExecutor false
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
                if (webView.isCodeMirrorReleased()) return@post
                webView.evaluateJavascript(script) { result ->
                    if (webView.isCodeMirrorReleased()) return@evaluateJavascript
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
                if (webView.isCodeMirrorReleased()) {
                    callback(controller.getCachedSnapshot())
                    return@post
                }
                val evalStart = SystemClock.elapsedRealtime()
                KardLeafLog.d(
                    USER_PERF_TRACE_TAG,
                    "codeMirror saveGetTextStart engine=CODEMIRROR docLen=${controller.getCachedSnapshot().content.length} " +
                        "sizeTier=$userPerfSizeTier queue=${evalStart - requestAt}ms key=$documentKey",
                )
                webView.evaluateJavascript(
                    "if (window.KardLeafEditor && window.KardLeafEditor.getText) { window.KardLeafEditor.getText(); } else { null; }",
                ) { result ->
                    if (webView.isCodeMirrorReleased()) {
                        callback(controller.getCachedSnapshot())
                        return@evaluateJavascript
                    }
                    val evalElapsed = SystemClock.elapsedRealtime() - evalStart
                    val text = decodeJavascriptStringResult(result)
                    val cachedSnapshot = controller.getCachedSnapshot()
                    if (text == null) {
                        KardLeafLog.w(
                            CODEMIRROR_TRACE_TAG,
                            "snapshot request failed result=$result key=$documentKey eval=${evalElapsed}ms queue=${evalStart - requestAt}ms",
                        )
                        callback(cachedSnapshot)
                    } else if (text.isEmpty() && cachedSnapshot.content.isNotEmpty() && (!pageReady || !codeMirrorContentApplied)) {
                        KardLeafLog.w(
                            CODEMIRROR_TRACE_TAG,
                            "snapshot ignored empty editor result key=$documentKey cachedLen=${cachedSnapshot.content.length} " +
                                "pageReady=$pageReady contentApplied=$codeMirrorContentApplied",
                        )
                        callback(cachedSnapshot)
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
            controller.setExternalRangeReplacer(null)
            controller.setExternalSelectionUpdater(null)
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
            webViewProvider = { webViewRef.get() },
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
            onTitleEdited = { latestOnTitleChanged() },
            onContentApplied = { contentLength ->
                val wasApplied = codeMirrorContentApplied
                val now = SystemClock.elapsedRealtime()
                val sincePush = if (lastPushDocumentAt > 0L) now - lastPushDocumentAt else -1L
                val rawContentLength = latestInitialContent.length
                val normalizedContentLength = codeMirrorNormalizedLength(latestInitialContent)
                val crlfCount = codeMirrorCrLfCount(latestInitialContent)
                val matchesRawLength = contentLength == lastPushedContentLength && contentLength == rawContentLength
                val matchesNormalizedLength = contentLength == normalizedContentLength
                codeMirrorContentApplied = true
                lastContentAppliedAt = now
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "content apply audit key=$documentKey rawLen=$rawContentLength actualLen=$contentLength " +
                        "pushedLen=$lastPushedContentLength normalizedLen=$normalizedContentLength " +
                        "crlfCount=$crlfCount rawMatch=$matchesRawLength normalizedMatch=$matchesNormalizedLength " +
                        "delta=${rawContentLength - contentLength}",
                )
                if (
                    !hasShownInitialContent &&
                    (matchesRawLength || matchesNormalizedLength)
                ) {
                    val anchor = latestInitialViewportAnchor
                    if (anchor == null) {
                        hasShownInitialContent = true
                        webViewRef.get()?.alpha = 1f
                        KardLeafLog.d(
                            CODEMIRROR_TRACE_TAG,
                            "initial surface revealed key=$documentKey actualLen=$contentLength " +
                                "rawLen=$rawContentLength normalizedLen=$normalizedContentLength",
                        )
                    } else {
                        val codeMirrorAnchor = anchor.toCodeMirrorAnchor(latestInitialContent)
                        KardLeafLog.d(
                            CODEMIRROR_SCROLL_TRACE_TAG,
                            "initial anchor apply start key=$documentKey pageReady=$pageReady contentApplied=true " +
                                "rawAnchorOffset=${anchor.offset} codeMirrorAnchorOffset=${codeMirrorAnchor.offset} " +
                                "pendingAnchorEdge=${anchor.edge} rawLen=$rawContentLength " +
                                "normalizedLen=$normalizedContentLength crlfCount=$crlfCount " +
                                "scrollTopBefore=${scrollController.getScrollTop()}",
                        )
                        scrollController.scrollViewportToAnchor(codeMirrorAnchor) { result ->
                            if (result?.contains("ok:") != true) {
                                KardLeafLog.w(
                                    CODEMIRROR_SCROLL_TRACE_TAG,
                                    "initial anchor apply failed key=$documentKey rawOffset=${anchor.offset} " +
                                        "codeMirrorOffset=${codeMirrorAnchor.offset} result=$result",
                                )
                                return@scrollViewportToAnchor
                            }
                            hasShownInitialContent = true
                            webViewRef.get()?.alpha = 1f
                            KardLeafLog.d(
                                CODEMIRROR_SCROLL_TRACE_TAG,
                                "initial anchor apply result key=$documentKey rawOffset=${anchor.offset} " +
                                    "codeMirrorOffset=${codeMirrorAnchor.offset} edge=${anchor.edge} " +
                                    "anchorApplyResult=$result scrollTopAfter=${scrollController.getScrollTop()} " +
                                    "hasFocus=${webViewRef.get()?.hasFocus()}",
                            )
                            latestOnInitialViewportAnchorApplied(anchor, result)
                        }
                    }
                } else if (!hasShownInitialContent) {
                    KardLeafLog.w(
                        CODEMIRROR_TRACE_TAG,
                        "initial content confirmation rejected key=$documentKey rawLen=$rawContentLength " +
                            "actualLen=$contentLength pushedLen=$lastPushedContentLength " +
                            "normalizedLen=$normalizedContentLength crlfCount=$crlfCount",
                    )
                }
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
                val deferredSearchState = pendingSearchState.value
                val deferredSearchSelection = pendingSearchSelection.value
                if (deferredSearchState != null || deferredSearchSelection != null) {
                    pendingSearchState.value = null
                    pendingSearchSelection.value = null
                    deferredSearchState?.let { args ->
                        controller.executeCommand("setSearchState", *args.toTypedArray())
                    }
                    deferredSearchSelection?.let { selection ->
                        controller.executeCommand("selectRange", selection.start, selection.end)
                    }
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
            onDrawingImageClicked = { target -> latestOnDrawingImageClicked(target) },
            wikilinkNotesProvider = { latestWikilinkNotes },
            onInternalLinkOpen = { target -> latestOnInternalLinkOpen(target) },
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
                    if (webView.isCodeMirrorReleased()) return@post
                    val postAt = SystemClock.elapsedRealtime()
                    val focusStart = SystemClock.elapsedRealtime()
                    val hadFocus = webView.hasFocus()
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
                            if (webView.isCodeMirrorReleased()) return@evaluateJavascript
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
            .padding(start = 16.dp, end = 16.dp, top = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (active && !hasShownInitialContent) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (showTitle) {
                        Text(
                            text = initialTitle.ifBlank { titleHint },
                            color = if (initialTitle.isBlank()) hintColor else textColor,
                            fontSize = titleTextSize,
                            lineHeight = (titleTextSize.value * 1.5f).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        )
                    }
                    if (initialContent.isNotEmpty()) {
                        Text(
                            text = initialContent.take(2_000),
                            color = textColor,
                            fontSize = contentTextSize,
                            lineHeight = (contentTextSize.value * contentLineHeightMultiplier).sp,
                            letterSpacing = contentLetterSpacingSp.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = if (showTitle) 0.dp else 12.dp),
                        )
                    }
                }
            }
            key(documentKey) {
                AndroidView(
                    factory = { context ->
                val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                val codeMirrorWebView = CodeMirrorImeTraceWebView(context).apply {
                    traceKey = documentKey
                    traceContentLength = { latestInitialContent.length }
                    traceSizeTier = { userPerfSizeTier }
                    traceLivePreviewEnabled = { latestLivePreviewEnabled }
                    tracePageReady = { pageReady }
                }
                val bridgeHost = KardLeafCodeMirrorBridgeHost(bridge, documentKey)
                codeMirrorWebView.apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    webViewRef.set(this)
                    scrollController.attach(this)
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "factory create key=$documentKey initialContentLen=${initialContent.length} active=$active " +
                            "viewId=${System.identityHashCode(this)}",
                    )
                    WebView.setWebContentsDebuggingEnabled(isDebuggable)
                    setBackgroundColor(codeMirrorBackgroundArgb)
                    alpha = 0f
                    visibility = if (active) View.VISIBLE else View.INVISIBLE
                    isEnabled = active
                    importantForAccessibility =
                        if (active) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
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
                                        if ((view as? WebView)?.isCodeMirrorReleased() == true) return@postDelayed
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
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.textZoom = 100
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    addJavascriptInterface(bridgeHost, "KardLeafAndroid")
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
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            if (request?.isForMainFrame == false) return true
                            val uri = request?.url ?: return false
                            return shouldBlockCodeMirrorMainFrameNavigation(uri)
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val uri = url?.let(Uri::parse) ?: return false
                            return shouldBlockCodeMirrorMainFrameNavigation(uri)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            if (view?.isCodeMirrorReleased() == true) return
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
                            if (view?.isCodeMirrorReleased() == true) return
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
                            if (view?.isCodeMirrorReleased() == true) return
                            super.onReceivedError(view, request, error)
                            KardLeafLog.e(
                                CODEMIRROR_TRACE_TAG,
                                "page error url=${request?.url} mainFrame=${request?.isForMainFrame} code=${error?.errorCode} desc=${error?.description}",
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (view?.isCodeMirrorReleased() == true) return
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
                            scrollController.refreshScrollMetrics()
                        }
                    }
                    fun requestEditorPageLoad(reason: String) {
                        if (isCodeMirrorReleased()) return
                        KardLeafLog.d(
                            CODEMIRROR_TRACE_TAG,
                            "CodeMirror page load requested reason=$reason key=$documentKey url=$codeMirrorAssetUrl width=$width height=$height attached=$isAttachedToWindow",
                        )
                        loadUrl(codeMirrorAssetUrl)
                    }
                    lateinit var lifecycleState: CodeMirrorWebViewLifecycleState
                    val initialLoadRunnable = Runnable {
                        if (lifecycleState.released) return@Runnable
                        requestEditorPageLoad("attached")
                    }
                    val loadTimeoutRunnable = Runnable {
                        if (lifecycleState.released) return@Runnable
                        if (!pageReady) {
                            KardLeafLog.w(
                                CODEMIRROR_TRACE_TAG,
                                "page load timeout 2000ms key=$documentKey url=$url progress=$progress width=$width height=$height attached=$isAttachedToWindow; reload",
                            )
                            stopLoading()
                            requestEditorPageLoad("timeout-reload")
                        }
                    }
                    lifecycleState = CodeMirrorWebViewLifecycleState(
                        bridgeHost = bridgeHost,
                        initialLoadRunnable = initialLoadRunnable,
                        loadTimeoutRunnable = loadTimeoutRunnable,
                    )
                    setTag(R.id.codemirror_lifecycle_state_tag, lifecycleState)
                    post(initialLoadRunnable)
                    postDelayed(loadTimeoutRunnable, 2000L)
                    // 不在页面加载后自动聚焦编辑器。
                    // Android WebView + CodeMirror 在大文本里自动聚焦会导致用户只想上下滑动时弹出输入法。
                    KardLeafLog.d(CODEMIRROR_TRACE_TAG, "initial focus skipped key=$documentKey pageReady=$pageReady")
                }
            },
            update = { webView ->
                val lifecycleState = webView.codeMirrorLifecycleState() ?: return@AndroidView
                if (lifecycleState.released) return@AndroidView
                lifecycleState.bridgeHost.replace(bridge, documentKey)
                webViewRef.set(webView)
                scrollController.attach(webView)
                webView.visibility = if (active) View.VISIBLE else View.INVISIBLE
                webView.isEnabled = active
                webView.importantForAccessibility =
                    if (active) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                if (!active) webView.clearFocus()
                webView.alpha = if (active && hasShownInitialContent) 1f else 0f
                if (lifecycleState.active != active) {
                    lifecycleState.active = active
                    KardLeafLog.d(
                        CODEMIRROR_TRACE_TAG,
                        "modeSurface active=$active viewId=${System.identityHashCode(webView)} " +
                            "visibility=${webView.visibility} alpha=${webView.alpha} size=${webView.width}x${webView.height} " +
                            "pageReady=$pageReady contentApplied=$codeMirrorContentApplied",
                    )
                }
                webView.setBackgroundColor(codeMirrorBackgroundArgb)
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
                modifier = Modifier
                    .matchParentSize()
                    .padding(bottom = imeViewportInset),
                onRelease = { webView ->
                    val lifecycleState = webView.codeMirrorLifecycleState() ?: return@AndroidView
                    if (lifecycleState.released) return@AndroidView
                    KardLeafLog.d(CODEMIRROR_TRACE_TAG, "event=release_start")
                    lifecycleState.released = true
                    lifecycleState.bridgeHost.clear()?.dispose()
                    webView.removeCallbacks(lifecycleState.initialLoadRunnable)
                    webView.removeCallbacks(lifecycleState.loadTimeoutRunnable)
                    (webView as? CodeMirrorImeTraceWebView)?.releaseTraceCallbacks()
                    scrollController.detach(webView)
                    webViewRef.compareAndSet(webView, null)
                    ViewCompat.setOnApplyWindowInsetsListener(webView, null)
                    webView.stopLoading()
                    webView.removeJavascriptInterface("KardLeafAndroid")
                    webView.webChromeClient = null
                    webView.webViewClient = WebViewClient()
                    webView.setTag(R.id.codemirror_lifecycle_state_tag, null)
                    webView.destroy()
                    KardLeafLog.d(CODEMIRROR_TRACE_TAG, "event=release_done")
                },
                )
            }
        }
    }
}

private class KardLeafCodeMirrorBridgeHost(
    initialBridge: KardLeafCodeMirrorBridge,
    initialDocumentKey: String,
) {
    private val delegate = AtomicReference<KardLeafCodeMirrorBridge?>(initialBridge)
    private var documentKey: String = initialDocumentKey

    @Synchronized
    fun replace(nextBridge: KardLeafCodeMirrorBridge, nextDocumentKey: String) {
        val previous = delegate.get()
        if (previous === nextBridge && documentKey == nextDocumentKey) return
        delegate.set(nextBridge)
        documentKey = nextDocumentKey
        previous?.dispose()
        KardLeafLog.d(CODEMIRROR_BRIDGE_TRACE_TAG, "event=bridge_replace")
    }

    fun clear(): KardLeafCodeMirrorBridge? = delegate.getAndSet(null)

    @JavascriptInterface
    fun copyCodeBlock(text: String?): Boolean = delegate.get()?.copyCodeBlock(text) ?: false

    @JavascriptInterface
    fun consumeImagePayload(token: String?): String = delegate.get()?.consumeImagePayload(token).orEmpty()

    @JavascriptInterface
    fun consumeDocumentPayload(token: String?): String? = delegate.get()?.consumeDocumentPayload(token)

    @JavascriptInterface
    fun onEditorReady(version: String?, contentLength: Int) {
        delegate.get()?.onEditorReady(version, contentLength)
    }

    @JavascriptInterface
    fun onTitleChanged(title: String?) {
        delegate.get()?.onTitleChanged(title)
    }

    @JavascriptInterface
    fun onContentApplied(contentLength: Int) {
        delegate.get()?.onContentApplied(contentLength)
    }

    @JavascriptInterface
    fun onEditorError(message: String?, stack: String?) {
        delegate.get()?.onEditorError(message, stack)
    }

    @JavascriptInterface
    fun onContentPatches(patchesJson: String, selectionStart: Int, selectionEnd: Int) {
        delegate.get()?.onContentPatches(patchesJson, selectionStart, selectionEnd)
    }

    @JavascriptInterface
    fun onContentPatch(start: Int, deleteCount: Int, insertedText: String, selectionStart: Int, selectionEnd: Int) {
        delegate.get()?.onContentPatch(start, deleteCount, insertedText, selectionStart, selectionEnd)
    }

    @JavascriptInterface
    fun onSelectionChanged(selectionStart: Int, selectionEnd: Int) {
        delegate.get()?.onSelectionChanged(selectionStart, selectionEnd)
    }

    @JavascriptInterface
    fun onHistoryStateChanged(canUndo: Boolean, canRedo: Boolean) {
        delegate.get()?.onHistoryStateChanged(canUndo, canRedo)
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
        delegate.get()?.onEditorScrollPerf(
            event,
            elapsedMs,
            frames,
            slowFrames,
            maxFrameMs,
            avgFrameMs,
            smooth,
            scrollTop,
            scrollHeight,
            clientHeight,
        )
    }

    @JavascriptInterface
    fun onEditorScrollGesture(reason: String?) {
        delegate.get()?.onEditorScrollGesture(reason)
    }

    @JavascriptInterface
    fun onEditorFocusRequest(reason: String?) {
        delegate.get()?.onEditorFocusRequest(reason)
    }

    @JavascriptInterface
    fun onDrawingImageClicked(rawReference: String?, markdownFrom: Int, markdownTo: Int) {
        delegate.get()?.onDrawingImageClicked(rawReference, markdownFrom, markdownTo)
    }

    @JavascriptInterface
    fun openExternalUrl(rawUrl: String?) {
        delegate.get()?.openExternalUrl(rawUrl)
    }

    @JavascriptInterface
    fun getWikilinkItems(rawQuery: String?): String = delegate.get()?.getWikilinkItems(rawQuery) ?: "[]"

    @JavascriptInterface
    fun onUserInteraction() {
        delegate.get()?.onUserInteraction()
    }
}

private class KardLeafCodeMirrorBridge(
    private val controller: KardLeafEditorController,
    private val appContext: Context,
    private val webViewProvider: () -> WebView?,
    private val onEditorReady: () -> Unit,
    private val onTitleEdited: () -> Unit,
    private val onContentApplied: (Int) -> Unit,
    private val onEditorContentEdited: () -> Unit,
    private val onContentChanged: () -> Unit,
    private val onUndoRedoStateChanged: () -> Unit,
    private val onUserInteraction: () -> Unit,
    private val onEditorScrollGesture: () -> Unit,
    private val onEditorScrollMetricsChanged: (Int, Int, Int) -> Unit,
    private val onDrawingImageClicked: (KardLeafImageClickTarget) -> Unit,
    private val wikilinkNotesProvider: () -> List<Note>,
    private val onInternalLinkOpen: (String) -> Unit,
    private val onEditorFocusRequest: (String?) -> Unit,
    private val userPerfSizeTier: String,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val released = AtomicBoolean(false)
    private val releasedDropLogged = AtomicBoolean(false)
    private var patchCount = 0
    private var slowPatchCount = 0
    private var lastPatchLogAt = SystemClock.elapsedRealtime()
    private var selectionCount = 0
    private var lastSelectionLogAt = SystemClock.elapsedRealtime()
    private var interactionCount = 0
    private var lastInteractionLogAt = SystemClock.elapsedRealtime()
    private var userPerfScrollStartY = 0
    private var userPerfScrollMetricUpdates = 0
    private var lastContentNotifyAt = 0L
    private var pendingContentNotify = false

    fun dispose() {
        if (!released.compareAndSet(false, true)) return
        pendingContentNotify = false
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun dropIfReleased(): Boolean {
        if (!released.get()) return false
        if (releasedDropLogged.compareAndSet(false, true)) {
            KardLeafLog.d(CODEMIRROR_BRIDGE_TRACE_TAG, "event=callback_dropped_released")
        }
        return true
    }

    private fun postIfActive(action: () -> Unit) {
        if (dropIfReleased()) return
        mainHandler.post {
            if (dropIfReleased()) return@post
            action()
        }
    }

    private fun scheduleContentChangedNotify(): Long {
        if (dropIfReleased()) return 0L
        val now = SystemClock.elapsedRealtime()
        val delay = (500L - (now - lastContentNotifyAt)).coerceAtLeast(0L)
        if (delay == 0L) {
            if (dropIfReleased()) return 0L
            pendingContentNotify = false
            lastContentNotifyAt = now
            onContentChanged()
            return 0L
        }
        if (!pendingContentNotify) {
            pendingContentNotify = true
            mainHandler.postDelayed({
                if (dropIfReleased()) return@postDelayed
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
    fun copyCodeBlock(text: String?): Boolean {
        if (dropIfReleased()) return false
        val webView = webViewProvider()
        (webView as? CodeMirrorImeTraceWebView)?.markCodeBlockCopyTouch()
        val copied = runCatching {
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("KardLeaf 代码块", text.orEmpty()))
            true
        }.getOrDefault(false)
        return copied
    }

    @JavascriptInterface
    fun consumeImagePayload(token: String?): String {
        if (dropIfReleased()) return ""
        val payload = KardLeafCodeMirrorPayloadStore.consume(token).orEmpty()
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[image] native payload consumed token=${token?.take(12).orEmpty()} payloadLen=${payload.length}",
        )
        return payload
    }

    @JavascriptInterface
    fun consumeDocumentPayload(token: String?): String? {
        if (dropIfReleased()) return null
        val payload = KardLeafCodeMirrorPayloadStore.consume(token)
        KardLeafLog.d(
            CODEMIRROR_TRACE_TAG,
            "document payload consumed token=${token?.take(12).orEmpty()} payloadLen=${payload?.length ?: -1}",
        )
        return payload
    }

    @JavascriptInterface
    fun onEditorReady(version: String?, contentLength: Int) {
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
            onEditorReady.invoke()
            KardLeafLog.d(
                CODEMIRROR_BRIDGE_TRACE_TAG,
                "editor ready version=${version.orEmpty()} contentLength=$contentLength queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onTitleChanged(title: String?) {
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
            controller.updateExternalTitle(title.orEmpty())
            onTitleEdited.invoke()
            KardLeafLog.d(
                CODEMIRROR_BRIDGE_TRACE_TAG,
                "title changed len=${title?.length ?: 0} queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onContentApplied(contentLength: Int) {
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
            onContentApplied.invoke(contentLength)
            KardLeafLog.d(
                CODEMIRROR_BRIDGE_TRACE_TAG,
                "content applied len=$contentLength queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onEditorError(message: String?, stack: String?) {
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
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
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
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
            onEditorContentEdited()
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
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
            val runAt = SystemClock.elapsedRealtime()
            val applyStart = SystemClock.elapsedRealtime()
            applyPatchOnMain(start, deleteCount, insertedText, selectionStart, selectionEnd)
            val applyElapsed = SystemClock.elapsedRealtime() - applyStart
            onEditorContentEdited()
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
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
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
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
            controller.updateExternalUndoRedoState(canUndo, canRedo)
            onUndoRedoStateChanged.invoke()
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "bridge history state canUndo=$canUndo canRedo=$canRedo queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
            KardLeafLog.d(
                "KardLeafEditorUndo",
                "action=historyCallback kernel=CodeMirror externalCanUndo=$canUndo externalCanRedo=$canRedo " +
                    "controllerCanUndo=${controller.canUndo()} controllerCanRedo=${controller.canRedo()}",
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
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
            onEditorScrollMetricsChanged(scrollTop, scrollHeight, clientHeight)
            val contentLen = controller.getCachedSnapshot().content.length
            if (event == "start") {
                userPerfScrollStartY = scrollTop
                userPerfScrollMetricUpdates = 1
                KardLeafLog.d(
                    USER_PERF_TRACE_TAG,
                    "editorScroll humanStart mode=codeMirror engine=CODEMIRROR contentLen=$contentLen " +
                        "sizeTier=$userPerfSizeTier scrollY=$scrollTop maxScrollY=${(scrollHeight - clientHeight).coerceAtLeast(0)}",
                )
            } else if (event == "settled") {
                val metricUpdates = userPerfScrollMetricUpdates
                val deltaPx = kotlin.math.abs(scrollTop - userPerfScrollStartY)
                val msPerPx = if (deltaPx > 0) elapsedMs / deltaPx else 0.0
                val metricHz = if (elapsedMs > 0) metricUpdates * 1000.0 / elapsedMs else 0.0
                KardLeafLog.d(
                    USER_PERF_TRACE_TAG,
                    "editorScroll humanSettled mode=codeMirror engine=CODEMIRROR elapsed=${elapsedMs.toInt()}ms " +
                        "frames=$frames slowFrames=$slowFrames maxFrame=${maxFrameMs.toInt()}ms " +
                        "avgFrame=${String.format("%.1f", avgFrameMs)}ms smooth=$smooth " +
                        "metricUpdates=$metricUpdates metricHz=${String.format("%.1f", metricHz)} " +
                        "contentLen=$contentLen sizeTier=$userPerfSizeTier fromY=$userPerfScrollStartY toY=$scrollTop " +
                        "deltaPx=$deltaPx msPerPx=${String.format("%.3f", msPerPx)} " +
                        "maxScrollY=${(scrollHeight - clientHeight).coerceAtLeast(0)} queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
                )
                userPerfScrollStartY = scrollTop
                userPerfScrollMetricUpdates = 0
            } else if (event == "scroll") {
                userPerfScrollMetricUpdates += 1
            }
        }
    }

    @JavascriptInterface
    fun onEditorScrollGesture(reason: String?) {
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
            onEditorScrollGesture.invoke()
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "bridge scroll gesture reason=${reason ?: "unknown"} queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onEditorFocusRequest(reason: String?) {
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
            onEditorFocusRequest.invoke(reason)
            KardLeafLog.d(
                CODEMIRROR_TRACE_TAG,
                "bridge focus request reason=${reason ?: "unknown"} queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun onDrawingImageClicked(
        rawReference: String?,
        markdownFrom: Int,
        markdownTo: Int,
    ) {
        if (dropIfReleased()) return
        val reference = rawReference?.trim().orEmpty()
        if (reference.isBlank()) return
        val receivedAt = SystemClock.elapsedRealtime()
        val snapshot = controller.getSnapshot()
        val safeFrom = markdownFrom.takeIf { it >= 0 }
        val safeTo = markdownTo.takeIf { markdownFrom >= 0 && it >= markdownFrom }
        val occurrence = occurrenceIndexForImageReference(snapshot.content, reference, safeFrom)
        postIfActive {
            onDrawingImageClicked.invoke(
                KardLeafImageClickTarget(
                    reference = reference,
                    markdownStart = safeFrom,
                    markdownEndExclusive = safeTo,
                    occurrenceIndex = occurrence,
                    source = ImageClickSource.CodeMirror,
                ),
            )
            KardLeafLog.d(
                CODEMIRROR_IMAGE_TRACE_TAG,
                "bridge image clicked reference=$reference range=${safeFrom ?: -1}..${safeTo ?: -1} occurrence=$occurrence " +
                    "queue=${SystemClock.elapsedRealtime() - receivedAt}ms",
            )
        }
    }

    @JavascriptInterface
    fun openExternalUrl(rawUrl: String?) {
        if (dropIfReleased()) return
        val url = rawUrl?.trim().orEmpty()
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri?.scheme.equals("kardleaf-wikilink", ignoreCase = true)) {
            val target = url.removePrefix("kardleaf-wikilink:").trim()
            if (target.isNotBlank()) {
                postIfActive {
                    onInternalLinkOpen(target)
                    KardLeafLog.d("KardLeafWikiLinkTrace", "click targetLen=${target.length} navigationResult=internal-dispatched")
                }
            }
            return
        }
        val externalUri = uri ?: run {
            KardLeafLog.w(CODEMIRROR_TRACE_TAG, "bridge open external ignored reason=invalid-uri")
            return
        }
        if (externalUri.scheme?.lowercase() !in setOf("http", "https")) {
            KardLeafLog.w(CODEMIRROR_TRACE_TAG, "bridge open external ignored scheme=${externalUri.scheme.orEmpty()}")
            return
        }
        postIfActive {
            try {
                val intent = Intent(Intent.ACTION_VIEW, externalUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(intent)
                KardLeafLog.d(
                    CODEMIRROR_TRACE_TAG,
                    "bridge open external scheme=${externalUri.scheme.orEmpty()} host=${externalUri.host.orEmpty()} path=${externalUri.path.orEmpty()}",
                )
            } catch (error: Throwable) {
                KardLeafLog.e(
                    CODEMIRROR_TRACE_TAG,
                    "bridge open external failed scheme=${externalUri.scheme.orEmpty()} host=${externalUri.host.orEmpty()} path=${externalUri.path.orEmpty()}",
                    error,
                )
            }
        }
    }

    @JavascriptInterface
    fun getWikilinkItems(rawQuery: String?): String {
        if (dropIfReleased()) return "[]"
        val query = rawQuery?.trim().orEmpty()
        val items = wikilinkNotesProvider()
            .asSequence()
            .filter { note ->
                query.isBlank() || listOf(note.title, note.file.path, note.folder).any {
                    it.contains(query, ignoreCase = true)
                }
            }
            .sortedWith(compareBy<Note> { !it.title.equals(query, ignoreCase = true) }.thenBy { it.file.path.lowercase() })
            .take(50)
            .toList()
        val json = JSONArray().apply {
            items.forEach { note ->
                put(
                    JSONObject()
                        .put("id", note.id)
                        .put("title", note.file.path.removeSuffix(".md").replace("\\", "/"))
                        .put("description", note.file.path.replace("\\", "/"))
                        .put("commit", "replaceWithLink"),
                )
            }
        }.toString()
        KardLeafLog.d("KardLeafWikiLinkTrace", "candidate queryLen=${query.length} candidateCount=${items.size}")
        return json
    }

    @JavascriptInterface
    fun onUserInteraction() {
        if (dropIfReleased()) return
        val receivedAt = SystemClock.elapsedRealtime()
        postIfActive {
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
    if (isCodeMirrorReleased()) return
    clearFocus()
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.hideSoftInputFromWindow(windowToken, 0)
    KardLeafLog.d(
        CODEMIRROR_TRACE_TAG,
        "android keyboard hide reason=$reason hasFocus=${hasFocus()}",
    )
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
    if (isCodeMirrorReleased()) return
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
        if (isCodeMirrorReleased()) return@evaluateJavascript
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
    if (isCodeMirrorReleased()) return
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
        if (isCodeMirrorReleased()) return@evaluateJavascript
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
