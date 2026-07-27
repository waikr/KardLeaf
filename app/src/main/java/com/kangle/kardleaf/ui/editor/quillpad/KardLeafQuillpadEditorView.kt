package com.kangle.kardleaf.ui.editor.quillpad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.LineHeightSpan
import android.text.style.ReplacementSpan
import android.text.style.UpdateLayout
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.kangle.kardleaf.BuildConfig
import com.kangle.kardleaf.data.utils.EditorOpenSession
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.ui.editor.api.EditorFastScrollMetrics
import com.kangle.kardleaf.ui.editor.api.KardLeafEditorKernelView
import com.kangle.kardleaf.ui.editor.buildNoteSearchMatches
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorController
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorSnapshot
import com.kangle.kardleaf.ui.editor.native.KardLeafInlineImagePreviewResolver
import org.qosp.notes.ui.editor.markdown.MarkdownSpan
import org.qosp.notes.ui.editor.markdown.indentCurrentLine
import org.qosp.notes.ui.editor.markdown.insertCodeBlock
import org.qosp.notes.ui.editor.markdown.insertDivider
import org.qosp.notes.ui.editor.markdown.insertMarkdown
import org.qosp.notes.ui.editor.markdown.outdentCurrentLine
import org.qosp.notes.ui.editor.markdown.insertUnderline
import org.qosp.notes.ui.editor.markdown.setHeadingLevel
import org.qosp.notes.ui.editor.markdown.toggleBulletCurrentLine
import org.qosp.notes.ui.editor.markdown.toggleChecklistCurrentLine
import org.qosp.notes.ui.editor.markdown.toggleOrderedCurrentLine
import org.qosp.notes.ui.utils.views.ExtendedEditText
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

private const val QUILLPAD_PERF_TAG = "KardLeafQuillpadPerf"
private const val USER_PERF_TAG = "KardLeafUserPerf"
private const val QUILLPAD_IME_TAG = "KardLeafQuillpadIme"
private const val BETA_IMAGE_LAYOUT_TAG = "KardLeafBetaImageLayout"
private val SEARCH_HIGHLIGHT_COLOR = 0x8CFFD60A.toInt()
private val SEARCH_CURRENT_OUTLINE_COLOR = 0xD9FF9800.toInt()

private const val INLINE_IMAGE_PREVIEW_MAX_CHARS = 30_000
private const val INLINE_IMAGE_PREVIEW_MAX_COUNT = 12
private const val INLINE_IMAGE_PREVIEW_DEBOUNCE_MS = 350L

private data class QuillpadInlineImageMatch(
    val syntaxStart: Int,
    val syntaxEnd: Int,
    val syntaxLineStart: Int,
    val syntaxLineEnd: Int,
    val reference: String,
)

private data class QuillpadInlineImageItem(
    val syntaxStart: Int,
    val syntaxEnd: Int,
    val syntaxLineStart: Int,
    val syntaxLineEnd: Int,
    val reference: String,
    val bitmap: Bitmap,
    val widthPx: Int,
    val heightPx: Int,
)

internal data class QuillpadImageTextMetric(
    val offset: Int,
    val line: Int,
    val primaryHorizontal: Float,
    val lineTop: Int,
    val lineBottom: Int,
)

internal data class QuillpadImageLayoutBaseline(
    val contentLength: Int,
    val contentHash: Int,
    val measuredHeight: Int,
    val layoutHeight: Int,
    val metrics: List<QuillpadImageTextMetric>,
)

private data class QuillpadImageGeometry(
    val firstLine: Int,
    val lastLine: Int,
    val lineCount: Int,
    val syntaxTextTop: Int,
    val syntaxTextBottom: Int,
    val imageTop: Int,
    val imageBottom: Int,
    val nextLineTop: Int,
    val reservedAreaBottom: Int,
    val spanApplied: Boolean,
)

// Preserve the Markdown text width and reserve only vertical space below its final visual line.
internal class QuillpadInlineImageLineHeightSpan(
    val reference: String,
    val bitmap: Bitmap,
    val widthPx: Int,
    val heightPx: Int,
    val reservedHeightPx: Int,
    val previewGapPx: Int,
    private val lineSpacingMultiplier: Float,
    val syntaxStart: Int,
    val syntaxEnd: Int,
    val syntaxLineStart: Int,
    val syntaxLineEnd: Int,
) : LineHeightSpan, UpdateLayout {
    var hiddenForSelection: Boolean = false
    var diagnosticBaseline: QuillpadImageLayoutBaseline? = null

    override fun chooseHeight(
        text: CharSequence?,
        start: Int,
        end: Int,
        spanstartv: Int,
        v: Int,
        fm: Paint.FontMetricsInt,
    ) {
        val spanned = text as? Spanned ?: return
        val finalSyntaxOffset = spanned.getSpanEnd(this) - 1
        if (finalSyntaxOffset < start || finalSyntaxOffset >= end || hiddenForSelection) return

        val textLength = spanned.length
        val lastCharIsNewLine = textLength > 0 && spanned[textLength - 1] == '\n'
        val isLastLine =
            (end == textLength && !lastCharIsNewLine) ||
                (start == textLength && lastCharIsNewLine)
        val layoutMultiplier = if (isLastLine) 1f else lineSpacingMultiplier.coerceAtLeast(0.01f)
        val metricExtra = ceil(reservedHeightPx / layoutMultiplier.toDouble()).toInt().coerceAtLeast(1)
        fm.descent += metricExtra
        fm.bottom += metricExtra
    }

    fun shouldHideForSelection(
        text: Spanned,
        editorFocused: Boolean,
    ): Boolean {
        if (!editorFocused) return false
        val anchor = text.getSpanStart(this).takeIf { it >= 0 } ?: syntaxStart
        val paragraphStart = if (anchor <= 0) 0 else text.lastIndexOf('\n', anchor - 1) + 1
        val paragraphEnd = text.indexOf('\n', anchor).let { if (it < 0) text.length else it }
        val selectionStart = Selection.getSelectionStart(text)
        val selectionEnd = Selection.getSelectionEnd(text)
        if (selectionStart < 0 || selectionEnd < 0) return false
        val lower = minOf(selectionStart, selectionEnd)
        val upper = maxOf(selectionStart, selectionEnd)
        return if (lower == upper) {
            lower in paragraphStart..paragraphEnd
        } else {
            lower <= paragraphEnd && upper >= paragraphStart
        }
    }
}

private class QuillpadRoundedSearchHighlightSpan(
    private val backgroundColor: Int,
    private val outlineColor: Int?,
    private val horizontalInsetPx: Float,
    private val verticalInsetPx: Float,
    private val cornerRadiusPx: Float,
    private val outlineWidthPx: Float,
) : ReplacementSpan() {
    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            val source = paint.fontMetricsInt
            fm.ascent = source.ascent
            fm.descent = source.descent
            fm.top = source.top
            fm.bottom = source.bottom
            fm.leading = source.leading
        }
        return paint.measureText(text, start, end).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val textWidth = paint.measureText(text, start, end)
        val left = x + horizontalInsetPx
        val right = (x + textWidth - horizontalInsetPx).coerceAtLeast(left)
        val fontMetrics = paint.fontMetrics
        val rect = RectF(
            left,
            y + fontMetrics.ascent + verticalInsetPx,
            right,
            y + fontMetrics.descent - verticalInsetPx,
        )
        val oldColor = paint.color
        val oldStyle = paint.style
        val oldStrokeWidth = paint.strokeWidth

        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)

        if (outlineColor != null) {
            paint.color = outlineColor
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = outlineWidthPx
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
        }

        paint.color = oldColor
        paint.style = oldStyle
        paint.strokeWidth = oldStrokeWidth
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
    }
}

private class QuillpadMultilineSearchHighlightSpan(color: Int) : BackgroundColorSpan(color)

internal data class QuillpadDebugCounters(
    val androidViewUpdates: Int,
    val configureCalls: Int,
    val configureApplied: Int,
    val bindCalls: Int,
    val fullTextSnapshots: Int,
    val revealScheduled: Int,
    val revealExecuted: Int,
)

private data class QuillpadEditorConfig(
    val titleHint: String,
    val contentHint: String,
    val textColor: Int,
    val hintColor: Int,
    val titleTextSizeSp: Float,
    val contentTextSizeSp: Float,
    val contentLineHeightMultiplier: Float,
    val contentLetterSpacingSp: Float,
    val contentParagraphSpacingDp: Float,
    val contentFontFamily: String,
    val showTitle: Boolean,
    val currentFolder: String,
    val inlineImagePreviewEnabled: Boolean,
    val readOnly: Boolean,
)

private data class TextGeneration(
    val sequence: Long,
    val sessionId: Long,
    val documentKey: String,
    val contentLength: Int,
) {
    val trace: String
        get() = "generation=$sessionId.$sequence generationDocumentKey=${documentKey.hashCode()} generationContentLen=$contentLength"
}

private class KernelExtendedEditText(
    context: Context,
    private val role: String,
) : ExtendedEditText(context) {
    var diagnostic: ((String) -> Unit)? = null

    private val imagePreviewToken = AtomicInteger(0)
    private val imagePreviewRefreshRunnable = Runnable { refreshInlineImagePreviewsNow() }
    private var imagePreviewPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private val imagePreviewWatcher =
        object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                scheduleInlineImagePreviewRefresh()
            }
        }
    private var imagePreviewExecutor = if (role == "content") Executors.newSingleThreadExecutor() else null
    private var imagePreviewResolver = if (role == "content") KardLeafInlineImagePreviewResolver(context) else null
    private var imagePreviewEnabled = false
    private var imagePreviewReleased = false

    @Volatile
    private var imagePreviewDocumentKey = ""
    private var imagePreviewFolder = ""
    private var imagePreviewLineSpacingMultiplier = 1f
    private var imagePreviewItems: List<QuillpadInlineImageLineHeightSpan> = emptyList()
    private var imagePreviewAppliedFolder = ""
    private var imagePreviewAppliedWidth = -1
    private var imagePreviewAppliedLineSpacingMultiplier = -1f
    private var inlineImageClickCallback: ((String) -> Unit)? = null
    private var previewTouchReference: String? = null
    private var previewTouchClickCancelled = false
    private var previewTouchStartX = 0f
    private var previewTouchStartY = 0f
    private var previewTouchSelectionStart = 0
    private var previewTouchSelectionEnd = 0
    private val previewClipPath = Path()
    private val imagePreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val previewBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x26000000
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }

    init {
        if (role == "content") addTextChangedListener(imagePreviewWatcher)
    }

    fun configureInlineImagePreview(
        enabled: Boolean,
        currentFolder: String,
        lineSpacingMultiplier: Float,
        onClick: (String) -> Unit,
    ) {
        inlineImageClickCallback = onClick
        val normalizedFolder = currentFolder.trim().trim('/')
        val normalizedMultiplier = lineSpacingMultiplier.coerceAtLeast(0.01f)
        val changed =
            imagePreviewEnabled != enabled ||
                imagePreviewFolder != normalizedFolder ||
                imagePreviewLineSpacingMultiplier != normalizedMultiplier
        imagePreviewEnabled = enabled
        imagePreviewFolder = normalizedFolder
        imagePreviewLineSpacingMultiplier = normalizedMultiplier
        if (changed) scheduleInlineImagePreviewRefresh()
    }

    fun refreshInlineImagePreview() {
        clearInlineImagePreviewSpans()
        scheduleInlineImagePreviewRefresh()
    }

    fun bindInlineImagePreviewDocument(documentKey: String) {
        if (imagePreviewDocumentKey == documentKey) return
        imagePreviewDocumentKey = documentKey
        imagePreviewToken.incrementAndGet()
        clearInlineImagePreviewSpans()
    }

    fun updateInlineImagePreviewSelection() {
        if (role != "content" || imagePreviewItems.isEmpty()) return
        val editable = text ?: return
        val editorFocused = hasFocus() && previewTouchReference == null
        val changedSpans = mutableListOf<QuillpadInlineImageLineHeightSpan>()
        imagePreviewItems.forEach { span ->
            val hidden = span.shouldHideForSelection(editable, editorFocused)
            if (span.hiddenForSelection == hidden) return@forEach
            val start = editable.getSpanStart(span)
            val end = editable.getSpanEnd(span)
            if (start >= 0 && end > start) {
                val baseline = captureImageLayoutBaseline(span)
                logImageLayout("layoutBefore reason=selection", span, baseline)
                logImageLayout("spanRemove reason=selection", span, requestLayoutCalled = true)
                editable.removeSpan(span)
                span.hiddenForSelection = hidden
                span.diagnosticBaseline = baseline
                editable.setSpan(span, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                logImageLayout("spanApply reason=selection hidden=$hidden", span, requestLayoutCalled = true)
                changedSpans += span
            }
        }
        if (changedSpans.isEmpty()) {
            logImageSelectionChecks()
            return
        }
        requestLayout()
        invalidate()
        scheduleImagePreviewPreDraw("selection", changedSpans)
    }

    fun releaseInlineImagePreview() {
        if (imagePreviewReleased) return
        imagePreviewReleased = true
        removeCallbacks(imagePreviewRefreshRunnable)
        imagePreviewToken.incrementAndGet()
        clearInlineImagePreviewSpans()
        removeImagePreviewPreDrawListener()
        imagePreviewExecutor?.shutdownNow()
        imagePreviewExecutor = null
        imagePreviewResolver = null
        inlineImageClickCallback = null
    }

    override fun focusSearch(direction: Int): View? {
        diagnostic?.invoke("focusSearch role=$role direction=$direction constrained=true")
        return this
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        diagnostic?.invoke("createInputConnectionStart role=$role")
        val startedAt = SystemClock.elapsedRealtimeNanos()
        return super.onCreateInputConnection(outAttrs).also {
            diagnostic?.invoke("createInputConnectionDone role=$role durationUs=${(SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000}")
        }
    }

    override fun onFocusChanged(
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        diagnostic?.invoke("focusChanged role=$role focused=$focused direction=$direction")
        updateInlineImagePreviewSelection()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = layout ?: return
        if (imagePreviewItems.isEmpty()) return

        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat() - scrollX, totalPaddingTop.toFloat() - scrollY)
        imagePreviewItems.forEach { item ->
            if (item.hiddenForSelection) return@forEach
            val bounds = previewBounds(item, layout) ?: return@forEach
            val radius = resources.displayMetrics.density * 8f
            previewClipPath.reset()
            previewClipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(previewClipPath)
            canvas.drawBitmap(item.bitmap, null, bounds, imagePreviewPaint)
            canvas.restore()
            canvas.drawRoundRect(bounds, radius, radius, previewBorderPaint)
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) diagnostic?.invoke("tapStart role=$role")
        if (role == "content" && imagePreviewItems.isNotEmpty()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val hit = findPreviewAt(event.x, event.y)
                    previewTouchReference = hit?.reference
                    previewTouchClickCancelled = false
                    previewTouchStartX = event.x
                    previewTouchStartY = event.y
                    previewTouchSelectionStart = selectionStart.coerceAtLeast(0)
                    previewTouchSelectionEnd = selectionEnd.coerceAtLeast(0)
                }
                MotionEvent.ACTION_MOVE -> {
                    val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                    if (!previewTouchClickCancelled && kotlin.math.hypot(
                            (event.x - previewTouchStartX).toDouble(),
                            (event.y - previewTouchStartY).toDouble(),
                        ) > slop
                    ) {
                        previewTouchClickCancelled = true
                        previewTouchReference = null
                        updateInlineImagePreviewSelection()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val reference = previewTouchReference
                    val hit = findPreviewAt(event.x, event.y)?.reference
                    if (!previewTouchClickCancelled && !reference.isNullOrBlank() && reference == hit) {
                        super.onTouchEvent(event)
                        val length = length()
                        setSelection(
                            previewTouchSelectionStart.coerceIn(0, length),
                            previewTouchSelectionEnd.coerceIn(0, length),
                        )
                        previewTouchReference = null
                        previewTouchClickCancelled = false
                        updateInlineImagePreviewSelection()
                        inlineImageClickCallback?.invoke(reference)
                        return true
                    }
                    previewTouchReference = null
                    previewTouchClickCancelled = false
                    updateInlineImagePreviewSelection()
                }
                MotionEvent.ACTION_CANCEL -> {
                    previewTouchReference = null
                    previewTouchClickCancelled = false
                    updateInlineImagePreviewSelection()
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performLongClick(): Boolean {
        previewTouchClickCancelled = true
        previewTouchReference = null
        updateInlineImagePreviewSelection()
        return super.performLongClick()
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        diagnostic?.invoke(
            "editTextMeasure role=$role durationUs=${(SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000} " +
                "measured=${measuredWidth}x$measuredHeight widthSpec=${View.MeasureSpec.getSize(widthMeasureSpec)} " +
                "heightSpec=${View.MeasureSpec.getSize(heightMeasureSpec)}",
        )
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val startedAt = SystemClock.elapsedRealtimeNanos()
        super.onLayout(changed, left, top, right, bottom)
        diagnostic?.invoke(
            "editTextLayout role=$role durationUs=${(SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000} " +
                "changed=$changed bounds=${right - left}x${bottom - top}",
        )
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) scheduleInlineImagePreviewRefresh()
    }

    private fun scheduleInlineImagePreviewRefresh() {
        removeCallbacks(imagePreviewRefreshRunnable)
        if (role != "content" || imagePreviewReleased) return
        imagePreviewToken.incrementAndGet()
        if (!imagePreviewEnabled || length() > INLINE_IMAGE_PREVIEW_MAX_CHARS) {
            clearInlineImagePreviewSpans()
            return
        }
        val editable = text
        val availableWidth = (width - totalPaddingLeft - totalPaddingRight).coerceAtLeast(1)
        val previewContextChanged =
            imagePreviewFolder != imagePreviewAppliedFolder ||
                imagePreviewLineSpacingMultiplier != imagePreviewAppliedLineSpacingMultiplier ||
                (width > 0 && availableWidth != imagePreviewAppliedWidth)
        if (
            previewContextChanged ||
            (editable != null && imagePreviewItems.any { !inlineImageSpanStillMatches(it, editable) })
        ) {
            clearInlineImagePreviewSpans()
        }
        postDelayed(imagePreviewRefreshRunnable, INLINE_IMAGE_PREVIEW_DEBOUNCE_MS)
    }

    private fun inlineImageSpanStillMatches(
        span: QuillpadInlineImageLineHeightSpan,
        editable: Editable,
    ): Boolean {
        val start = editable.getSpanStart(span)
        val end = editable.getSpanEnd(span)
        if (start < 0 || end <= start) return false
        val syntax = editable.subSequence(start, end).toString()
        val obsidianMatch = NoteFormatUtils.obsidianImageReferenceRegex.find(syntax)
        val markdownMatch = NoteFormatUtils.localMarkdownImageReferenceRegex.find(syntax)
        val match = obsidianMatch ?: markdownMatch ?: return false
        if (match.range.first != 0 || match.range.last != syntax.lastIndex) return false
        val reference =
            match.groupValues.getOrNull(1)?.trim()?.let { value ->
                if (obsidianMatch != null) value else value.trim('"', '\'')
            }
        return reference == span.reference
    }

    private fun refreshInlineImagePreviewsNow() {
        if (!imagePreviewEnabled || imagePreviewReleased || role != "content") return
        val source = text?.toString().orEmpty()
        if (source.length > INLINE_IMAGE_PREVIEW_MAX_CHARS || width <= 0) {
            clearInlineImagePreviewSpans()
            return
        }
        val token = imagePreviewToken.incrementAndGet()
        val documentKey = imagePreviewDocumentKey
        val matches = findInlineImageMatches(source)
        if (matches.isEmpty()) {
            clearInlineImagePreviewSpans()
            return
        }
        matches.forEach { match -> logImageScan(match, source, token, documentKey) }

        val availableWidth = (width - totalPaddingLeft - totalPaddingRight).coerceAtLeast(1)
        val folder = imagePreviewFolder
        if (canKeepInlineImagePreviewSpans(matches, folder, availableWidth)) return

        val resolver = imagePreviewResolver ?: return
        val executor = imagePreviewExecutor ?: return
        val maxPreviewHeight = dp(220)
        try {
            executor.execute {
                val previews =
                    matches.take(INLINE_IMAGE_PREVIEW_MAX_COUNT).mapNotNull { match ->
                        if (imagePreviewReleased || token != imagePreviewToken.get()) return@mapNotNull null
                        val startedAt = SystemClock.elapsedRealtime()
                        logImageLoad(
                            event = "loadStart",
                            match = match,
                            token = token,
                            documentKey = documentKey,
                            sourceWidth = -1,
                            sourceHeight = -1,
                            targetWidth = availableWidth,
                            targetHeight = maxPreviewHeight,
                            elapsedMs = 0,
                        )
                        val bitmap = resolver.resolveBitmap(folder, match.reference, availableWidth, maxPreviewHeight)
                        val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                        if (bitmap == null) {
                            logImageLoad(
                                event = "loadFailed reason=resolveBitmapNull",
                                match = match,
                                token = token,
                                documentKey = documentKey,
                                sourceWidth = -1,
                                sourceHeight = -1,
                                targetWidth = -1,
                                targetHeight = -1,
                                elapsedMs = elapsedMs,
                            )
                            return@mapNotNull null
                        }
                        val scale =
                            minOf(
                                availableWidth.toFloat() / bitmap.width.coerceAtLeast(1),
                                maxPreviewHeight.toFloat() / bitmap.height.coerceAtLeast(1),
                                1f,
                            )
                        val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                        val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                        logImageLoad(
                            event = "loadSuccess",
                            match = match,
                            token = token,
                            documentKey = documentKey,
                            sourceWidth = bitmap.width,
                            sourceHeight = bitmap.height,
                            targetWidth = targetWidth,
                            targetHeight = targetHeight,
                            elapsedMs = elapsedMs,
                        )
                        QuillpadInlineImageItem(
                            syntaxStart = match.syntaxStart,
                            syntaxEnd = match.syntaxEnd,
                            syntaxLineStart = match.syntaxLineStart,
                            syntaxLineEnd = match.syntaxLineEnd,
                            reference = match.reference,
                            bitmap = bitmap,
                            widthPx = targetWidth,
                            heightPx = targetHeight,
                        )
                    }
                val posted =
                    post {
                        if (
                            imagePreviewReleased ||
                            token != imagePreviewToken.get() ||
                            documentKey != imagePreviewDocumentKey ||
                            source != text?.toString().orEmpty()
                        ) {
                            previews.forEach { recycleBitmap(it.bitmap) }
                            return@post
                        }
                        applyInlineImagePreviewSpans(previews, folder, availableWidth)
                    }
                if (!posted) previews.forEach { recycleBitmap(it.bitmap) }
            }
        } catch (_: RejectedExecutionException) {
            // The view was disposed while a delayed refresh was being submitted.
        }
    }

    private fun canKeepInlineImagePreviewSpans(
        matches: List<QuillpadInlineImageMatch>,
        folder: String,
        availableWidth: Int,
    ): Boolean {
        val editable = text ?: return false
        if (
            folder != imagePreviewAppliedFolder ||
            availableWidth != imagePreviewAppliedWidth ||
            imagePreviewLineSpacingMultiplier != imagePreviewAppliedLineSpacingMultiplier
        ) {
            return false
        }
        if (matches.size != imagePreviewItems.size) return false
        return matches.zip(imagePreviewItems).all { (match, span) ->
            val spanStart = match.syntaxStart.coerceIn(0, editable.length)
            val spanEnd = match.syntaxEnd.coerceIn(spanStart, editable.length)
            match.reference == span.reference &&
                spanStart < spanEnd &&
                editable.getSpanStart(span) == spanStart &&
                editable.getSpanEnd(span) == spanEnd
        }
    }

    private fun applyInlineImagePreviewSpans(
        previews: List<QuillpadInlineImageItem>,
        folder: String,
        availableWidth: Int,
    ) {
        clearInlineImagePreviewSpans(requestRelayout = false)
        val editable = text
        if (editable == null) {
            previews.forEach { recycleBitmap(it.bitmap) }
            return
        }
        val spans =
            previews.mapNotNull { preview ->
                val spanStart = preview.syntaxStart.coerceIn(0, editable.length)
                val spanEnd = preview.syntaxEnd.coerceIn(spanStart, editable.length)
                if (spanStart >= spanEnd) {
                    recycleBitmap(preview.bitmap)
                    return@mapNotNull null
                }
                val previewGap = dp(8)
                QuillpadInlineImageLineHeightSpan(
                    reference = preview.reference,
                    bitmap = preview.bitmap,
                    widthPx = preview.widthPx,
                    heightPx = preview.heightPx,
                    reservedHeightPx = preview.heightPx + previewGap * 2,
                    previewGapPx = previewGap,
                    lineSpacingMultiplier = imagePreviewLineSpacingMultiplier,
                    syntaxStart = spanStart,
                    syntaxEnd = spanEnd,
                    syntaxLineStart = preview.syntaxLineStart,
                    syntaxLineEnd = preview.syntaxLineEnd,
                ).also { span ->
                    span.diagnosticBaseline = captureImageLayoutBaseline(span)
                    span.hiddenForSelection = span.shouldHideForSelection(editable, hasFocus() && previewTouchReference == null)
                    logImageLayout("layoutBefore", span, span.diagnosticBaseline)
                    editable.setSpan(span, spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    logImageLayout("spanApply", span, requestLayoutCalled = true)
                }
            }
        imagePreviewItems = spans
        imagePreviewAppliedFolder = folder
        imagePreviewAppliedWidth = availableWidth
        imagePreviewAppliedLineSpacingMultiplier = imagePreviewLineSpacingMultiplier
        requestLayout()
        invalidate()
        scheduleImagePreviewPreDraw("apply", spans)
    }

    private fun clearInlineImagePreviewSpans(requestRelayout: Boolean = true) {
        removeImagePreviewPreDrawListener()
        val editable = text
        val spans = editable?.getSpans(0, editable.length, QuillpadInlineImageLineHeightSpan::class.java).orEmpty()
        spans.forEach { span ->
            if (requestRelayout) logImageLayout("layoutBefore reason=remove", span, captureImageLayoutBaseline(span))
            logImageLayout("spanRemove", span, requestLayoutCalled = requestRelayout)
            editable?.removeSpan(span)
        }
        val oldItems = imagePreviewItems
        imagePreviewItems = emptyList()
        imagePreviewAppliedFolder = ""
        imagePreviewAppliedWidth = -1
        imagePreviewAppliedLineSpacingMultiplier = -1f
        oldItems.forEach { recycleBitmap(it.bitmap) }
        if (spans.isNotEmpty() || oldItems.isNotEmpty()) {
            if (requestRelayout) requestLayout()
            invalidate()
            if (requestRelayout) scheduleImagePreviewPreDraw("remove", oldItems)
        }
    }

    private fun findInlineImageMatches(source: String): List<QuillpadInlineImageMatch> {
        val result = mutableListOf<QuillpadInlineImageMatch>()
        var lineStart = 0
        while (lineStart <= source.length && result.size < INLINE_IMAGE_PREVIEW_MAX_COUNT) {
            val newline = source.indexOf('\n', lineStart)
            val lineEnd = if (newline >= 0) newline else source.length
            val line = source.substring(lineStart, lineEnd)
            val obsidianMatch = NoteFormatUtils.obsidianImageReferenceRegex.find(line)
            val markdownMatch = NoteFormatUtils.localMarkdownImageReferenceRegex.find(line)
            val match = obsidianMatch ?: markdownMatch
            val reference =
                match?.groupValues?.getOrNull(1)?.trim()?.let { value ->
                    if (obsidianMatch != null) value else value.trim('"', '\'')
                }
            if (!reference.isNullOrBlank() && lineEnd > lineStart) {
                result +=
                    QuillpadInlineImageMatch(
                        syntaxStart = lineStart + match!!.range.first,
                        syntaxEnd = lineStart + match.range.last + 1,
                        syntaxLineStart = lineStart,
                        syntaxLineEnd = lineEnd,
                        reference = reference,
                    )
            }
            if (newline < 0) break
            lineStart = newline + 1
        }
        return result
    }

    private fun imageLayoutDiagnosticsEnabled(): Boolean =
        (BuildConfig.KARDLEAF_DEV_VARIANT || BuildConfig.DEBUG) && KardLeafLog.isEnabled(BETA_IMAGE_LAYOUT_TAG)

    private inline fun logImageDebug(message: () -> String) {
        if (imageLayoutDiagnosticsEnabled()) KardLeafLog.d(BETA_IMAGE_LAYOUT_TAG, message())
    }

    private inline fun logImageWarning(message: () -> String) {
        if (imageLayoutDiagnosticsEnabled()) KardLeafLog.w(BETA_IMAGE_LAYOUT_TAG, message())
    }

    private fun logImageScan(
        match: QuillpadInlineImageMatch,
        source: String,
        token: Int,
        documentKey: String,
    ) {
        if (!imageLayoutDiagnosticsEnabled()) return
        val textLayout = layout
        val firstLayoutLine = textLayout?.getLineForOffset(match.syntaxStart.coerceIn(0, source.length)) ?: -1
        val lastLayoutLine =
            textLayout?.getLineForOffset((match.syntaxEnd - 1).coerceIn(0, source.lastIndex.coerceAtLeast(0))) ?: -1
        logImageDebug {
            "scan documentKeyHash=${documentKey.hashCode()} contentGeneration=$token contentLen=${source.length} " +
                "syntaxStart=${match.syntaxStart} syntaxEnd=${match.syntaxEnd} " +
                "syntaxLineStart=${match.syntaxLineStart} syntaxLineEnd=${match.syntaxLineEnd} " +
                "firstLayoutLine=$firstLayoutLine lastLayoutLine=$lastLayoutLine imageRefHash=${match.reference.hashCode()}"
        }
    }

    private fun logImageLoad(
        event: String,
        match: QuillpadInlineImageMatch,
        token: Int,
        documentKey: String,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        elapsedMs: Long,
    ) {
        logImageDebug {
            "$event documentKeyHash=${documentKey.hashCode()} contentGeneration=$token " +
                "imageRefHash=${match.reference.hashCode()} sourceWidth=$sourceWidth sourceHeight=$sourceHeight " +
                "targetWidth=$targetWidth targetHeight=$targetHeight decodeElapsedMs=$elapsedMs " +
                "generationMatched=${token == imagePreviewToken.get()} documentMatched=${documentKey == imagePreviewDocumentKey}"
        }
    }

    private fun captureImageLayoutBaseline(span: QuillpadInlineImageLineHeightSpan): QuillpadImageLayoutBaseline? {
        if (!imageLayoutDiagnosticsEnabled()) return null
        val source = text?.toString().orEmpty()
        val textLayout = layout ?: return null
        return QuillpadImageLayoutBaseline(
            contentLength = source.length,
            contentHash = source.hashCode(),
            measuredHeight = measuredHeight,
            layoutHeight = textLayout.height,
            metrics = captureImageTextMetrics(span, textLayout),
        )
    }

    private fun captureImageTextMetrics(
        span: QuillpadInlineImageLineHeightSpan,
        textLayout: android.text.Layout,
    ): List<QuillpadImageTextMetric> {
        val editable = text ?: return emptyList()
        val contentLength = editable.length
        if (contentLength <= 0) return emptyList()
        val syntaxStart = editable.getSpanStart(span).takeIf { it >= 0 } ?: span.syntaxStart
        val syntaxEnd = editable.getSpanEnd(span).takeIf { it >= 0 } ?: span.syntaxEnd
        val syntaxLineStart = if (syntaxStart <= 0) 0 else editable.lastIndexOf('\n', syntaxStart - 1) + 1
        val syntaxLineEnd = editable.indexOf('\n', syntaxEnd).let { if (it < 0) contentLength else it }
        return listOf(
            (syntaxLineStart - 1).coerceAtLeast(0),
            syntaxStart,
            syntaxEnd,
            (syntaxLineEnd + 1).coerceAtMost(contentLength),
        ).distinct().map { rawOffset ->
            val offset = rawOffset.coerceIn(0, contentLength)
            val line = textLayout.getLineForOffset(offset)
            QuillpadImageTextMetric(
                offset = offset,
                line = line,
                primaryHorizontal = textLayout.getPrimaryHorizontal(offset),
                lineTop = textLayout.getLineTop(line),
                lineBottom = textLayout.getLineBottom(line),
            )
        }
    }

    private fun imageGeometry(
        span: QuillpadInlineImageLineHeightSpan,
        textLayout: android.text.Layout,
    ): QuillpadImageGeometry? {
        val editable = text ?: return null
        if (editable.isEmpty()) return null
        val syntaxStart = editable.getSpanStart(span).takeIf { it >= 0 } ?: span.syntaxStart
        val syntaxEnd = editable.getSpanEnd(span).takeIf { it >= 0 } ?: span.syntaxEnd
        val firstOffset = syntaxStart.coerceIn(0, editable.length - 1)
        val lastOffset = (syntaxEnd - 1).coerceIn(firstOffset, editable.length - 1)
        val firstLine = textLayout.getLineForOffset(firstOffset)
        val lastLine = textLayout.getLineForOffset(lastOffset)
        val spanApplied = editable.getSpanStart(span) >= 0
        val reservedHeight = if (spanApplied && !span.hiddenForSelection) span.reservedHeightPx else 0
        val lineBottom = textLayout.getLineBottom(lastLine)
        val syntaxTextBottom = lineBottom - reservedHeight
        val imageTop = syntaxTextBottom + span.previewGapPx
        val imageBottom = imageTop + span.heightPx
        return QuillpadImageGeometry(
            firstLine = firstLine,
            lastLine = lastLine,
            lineCount = textLayout.lineCount,
            syntaxTextTop = textLayout.getLineTop(firstLine),
            syntaxTextBottom = syntaxTextBottom,
            imageTop = imageTop,
            imageBottom = imageBottom,
            nextLineTop = if (lastLine + 1 < textLayout.lineCount) textLayout.getLineTop(lastLine + 1) else -1,
            reservedAreaBottom = if (reservedHeight > 0) lineBottom else syntaxTextBottom,
            spanApplied = spanApplied,
        )
    }

    private fun logImageLayout(
        event: String,
        span: QuillpadInlineImageLineHeightSpan,
        baseline: QuillpadImageLayoutBaseline? = null,
        requestLayoutCalled: Boolean = false,
    ) {
        if (!imageLayoutDiagnosticsEnabled()) return
        val textLayout = layout ?: return
        val geometry = imageGeometry(span, textLayout) ?: return
        val measured = baseline?.measuredHeight ?: measuredHeight
        val layoutHeight = baseline?.layoutHeight ?: textLayout.height
        val editable = text
        val spanStart = editable?.getSpanStart(span)?.takeIf { it >= 0 } ?: span.syntaxStart
        val spanEnd = editable?.getSpanEnd(span)?.takeIf { it >= 0 } ?: span.syntaxEnd
        logImageDebug {
            "$event documentKeyHash=${imagePreviewDocumentKey.hashCode()} contentGeneration=${imagePreviewToken.get()} " +
                "imageRefHash=${span.reference.hashCode()} spanStart=$spanStart spanEnd=$spanEnd " +
                "firstLine=${geometry.firstLine} lastLine=${geometry.lastLine} lineCount=${geometry.lineCount} " +
                "measuredHeight=$measured layoutHeight=$layoutHeight reservedHeight=${span.reservedHeightPx} " +
                "imageWidth=${span.widthPx} imageHeight=${span.heightPx} syntaxTextTop=${geometry.syntaxTextTop} " +
                "syntaxTextBottom=${geometry.syntaxTextBottom} imageTop=${geometry.imageTop} imageBottom=${geometry.imageBottom} " +
                "nextLineTop=${geometry.nextLineTop} requestLayoutCalled=$requestLayoutCalled spanApplied=${geometry.spanApplied} " +
                "hiddenForSelection=${span.hiddenForSelection} contentLen=${editable?.length ?: 0} " +
                "contentHash=${editable?.toString()?.hashCode() ?: 0}"
        }
        if (event.startsWith("layoutBefore") || event.startsWith("layoutAfter")) {
            val metrics = baseline?.metrics ?: captureImageTextMetrics(span, textLayout)
            metrics.forEach { metric ->
                logImageDebug {
                    "textMetric phase=${event.substringBefore(' ')} imageRefHash=${span.reference.hashCode()} " +
                        "offset=${metric.offset} line=${metric.line} primaryHorizontal=${metric.primaryHorizontal} " +
                        "lineTop=${metric.lineTop} lineBottom=${metric.lineBottom}"
                }
            }
        }
    }

    private fun scheduleImagePreviewPreDraw(
        reason: String,
        items: List<QuillpadInlineImageLineHeightSpan>,
    ) {
        removeImagePreviewPreDrawListener()
        if (!imageLayoutDiagnosticsEnabled() || items.isEmpty()) return
        val observer = viewTreeObserver
        if (!observer.isAlive) return
        val listener =
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (observer.isAlive) observer.removeOnPreDrawListener(this)
                    if (imagePreviewPreDrawListener === this) imagePreviewPreDrawListener = null
                    items.forEach { span ->
                        logImageLayout("layoutAfter reason=$reason", span, requestLayoutCalled = true)
                        if (reason != "remove") logImageOverlapCheck(span)
                    }
                    logImageSelectionChecks()
                    return true
                }
            }
        imagePreviewPreDrawListener = listener
        observer.addOnPreDrawListener(listener)
    }

    private fun removeImagePreviewPreDrawListener() {
        imagePreviewPreDrawListener?.let { listener ->
            if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(listener)
        }
        imagePreviewPreDrawListener = null
    }

    private fun logImageOverlapCheck(span: QuillpadInlineImageLineHeightSpan) {
        if (!imageLayoutDiagnosticsEnabled()) return
        val textLayout = layout ?: return
        val geometry = imageGeometry(span, textLayout) ?: return
        val baseline = span.diagnosticBaseline ?: return
        if (!geometry.spanApplied || span.hiddenForSelection) {
            logImageDebug {
                "overlapCheck imageRefHash=${span.reference.hashCode()} imageBelowSyntax=true imageInsideReservedArea=true " +
                    "nextTextBelowImage=true layoutHeightIncreased=true horizontalMetricsStable=true contentUnchanged=true " +
                    "result=SKIPPED_HIDDEN"
            }
            return
        }
        val imageBelowSyntax = geometry.imageTop >= geometry.syntaxTextBottom + span.previewGapPx
        val imageInsideReservedArea =
            geometry.imageTop >= geometry.syntaxTextBottom && geometry.imageBottom <= geometry.reservedAreaBottom
        val nextTextBelowImage =
            if (geometry.nextLineTop >= 0) {
                geometry.nextLineTop >= geometry.imageBottom + span.previewGapPx
            } else {
                textLayout.height >= geometry.imageBottom + totalPaddingBottom
            }
        val layoutHeightIncreased = textLayout.height >= baseline.layoutHeight + span.reservedHeightPx - 2
        val currentMetrics = captureImageTextMetrics(span, textLayout).associateBy { it.offset }
        val horizontalMetricsStable =
            baseline.metrics.all { before ->
                currentMetrics[before.offset]?.let { after -> abs(before.primaryHorizontal - after.primaryHorizontal) <= 0.5f } == true
            }
        val source = text?.toString().orEmpty()
        val contentUnchanged = source.length == baseline.contentLength && source.hashCode() == baseline.contentHash
        val passed =
            imageBelowSyntax &&
                imageInsideReservedArea &&
                nextTextBelowImage &&
                layoutHeightIncreased &&
                horizontalMetricsStable &&
                contentUnchanged
        logImageDebug {
            "overlapCheck imageRefHash=${span.reference.hashCode()} imageBelowSyntax=$imageBelowSyntax " +
                "imageInsideReservedArea=$imageInsideReservedArea nextTextBelowImage=$nextTextBelowImage " +
                "layoutHeightIncreased=$layoutHeightIncreased horizontalMetricsStable=$horizontalMetricsStable " +
                "contentUnchanged=$contentUnchanged result=${if (passed) "PASS" else "FAIL"}"
        }
        if (!passed) {
            val reasons =
                buildList {
                    if (!imageBelowSyntax) add("imageAboveSyntaxBottom")
                    if (!imageInsideReservedArea) add("imageOutsideReservedArea")
                    if (!nextTextBelowImage) add("nextTextOverlapsImage")
                    if (!layoutHeightIncreased) add("layoutHeightNotIncreased")
                    if (!horizontalMetricsStable) add("horizontalMetricsChanged")
                    if (!contentUnchanged) add("contentChanged")
                }
            logImageWarning { "overlapDetected reason=${reasons.joinToString("+")} imageRefHash=${span.reference.hashCode()}" }
        }
    }

    private fun logImageSelectionChecks() {
        if (!imageLayoutDiagnosticsEnabled() || imagePreviewItems.isEmpty()) return
        val textLayout = layout ?: return
        val contentLength = text?.length ?: return
        val start = selectionStart
        val end = selectionEnd
        if (start < 0 || end < 0) return
        val cursorOffset = end.coerceIn(0, contentLength)
        val cursorLine = textLayout.getLineForOffset(cursorOffset)
        val cursorTop = textLayout.getLineTop(cursorLine)
        val cursorBottom = textLayout.getLineBottom(cursorLine)
        val cursorHorizontal = textLayout.getPrimaryHorizontal(cursorOffset)
        imagePreviewItems.forEach { span ->
            val bounds = if (span.hiddenForSelection) null else previewBounds(span, textLayout)
            val intersects = bounds != null && cursorBottom > bounds.top && cursorTop < bounds.bottom
            logImageDebug {
                "selectionCheck documentKeyHash=${imagePreviewDocumentKey.hashCode()} " +
                    "contentGeneration=${imagePreviewToken.get()} imageRefHash=${span.reference.hashCode()} " +
                    "selectionStart=$start selectionEnd=$end selectionLine=$cursorLine cursorTop=$cursorTop " +
                    "cursorBottom=$cursorBottom cursorHorizontal=$cursorHorizontal imageTop=${bounds?.top ?: -1f} " +
                    "imageBottom=${bounds?.bottom ?: -1f} cursorIntersectsImage=$intersects"
            }
        }
    }

    private fun previewBounds(
        item: QuillpadInlineImageLineHeightSpan,
        textLayout: android.text.Layout,
    ): RectF? {
        val editable = text ?: return null
        val spanEnd = editable.getSpanEnd(item)
        if (spanEnd <= 0 || editable.isEmpty()) return null
        val line = textLayout.getLineForOffset((spanEnd - 1).coerceIn(0, editable.length - 1))
        val reservedAreaBottom = textLayout.getLineBottom(line).toFloat()
        val top = reservedAreaBottom - item.reservedHeightPx + item.previewGapPx
        val bottom = top + item.heightPx
        val left = ((textLayout.width - item.widthPx) / 2f).coerceAtLeast(0f)
        return RectF(left, top, left + item.widthPx, bottom)
    }

    private fun findPreviewAt(
        x: Float,
        y: Float,
    ): QuillpadInlineImageLineHeightSpan? {
        val textLayout = layout ?: return null
        val localX = x - totalPaddingLeft + scrollX
        val localY = y - totalPaddingTop + scrollY
        return imagePreviewItems.firstOrNull { item -> previewBounds(item, textLayout)?.contains(localX, localY) == true }
    }

    private fun recycleBitmap(bitmap: Bitmap) {
        if (!bitmap.isRecycled) bitmap.recycle()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
}

internal fun quillpadNextLinePrefix(line: String): String? {
    val checklist = Regex("^((\\s*)[-+*] *\\[[ xX]] +).*").find(line)
    if (checklist != null) return "${checklist.groupValues[2]}- [ ] "
    val bullet = Regex("^((\\s*)([-+*] +)).*").find(line)
    if (bullet != null) return bullet.groupValues[2] + bullet.groupValues[3]
    val numbered = Regex("^((\\s*)([1-9][0-9]*)[.] +).*").find(line)
    if (numbered != null) return numbered.groupValues[2] + ((numbered.groupValues[3].toIntOrNull() ?: 0) + 1) + ". "
    return Regex("^(\\s+).+").find(line)?.groupValues?.get(1)
}

internal fun quillpadShouldEndList(line: String): Boolean =
    Regex("^\\s*(?:[-+*]\\s+(?:\\[[ xX]]\\s+)?|\\d+\\.\\s+)$").matches(line)

internal class KardLeafQuillpadEditorView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : FrameLayout(context, attrs), KardLeafEditorKernelView {
        private val scrollView = NestedScrollView(context)
        private val editorColumn = LinearLayout(context)
        private val titleEditText = KernelExtendedEditText(context, "title")
        private val contentEditText = KernelExtendedEditText(context, "content")
        private var programmaticChange = false
        private var continuingList = false
        private var titleChangedCallback: (() -> Unit)? = null
        private var contentChangedCallback: (() -> Unit)? = null
        private var selectionChangedCallback: ((Int, Int) -> Unit)? = null
        private var undoRedoChangedCallback: (() -> Unit)? = null
        private var userInteractionCallback: (() -> Unit)? = null
        private var scrollChangedCallback: (() -> Unit)? = null
        private var loadedTitle = ""
        private var loadedContent = ""
        private var openSession: EditorOpenSession? = null
        private var frameCommittedCallback: ((Long) -> Unit)? = null
        private var userPerfOpenStartRealtimeMs: Long? = null
        private var userPerfSizeTier = "unknown"
        private var textGenerationSequence = 0L
        private var currentTextGeneration: TextGeneration? = null
        private var measuredGenerationSequence = -1L
        private var laidOutGenerationSequence = -1L
        private var preDrawGenerationSequence = -1L
        private var committedGenerationSequence = -1L
        private var pendingPreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
        private var appliedConfig: QuillpadEditorConfig? = null
        private var cachedTypefaceFamily: String? = null
        private var cachedTypeface: Typeface? = null
        private var androidViewUpdateCount = 0
        private var configureCount = 0
        private var configureAppliedCount = 0
        private var bindCount = 0
        private var fullTextSnapshotCount = 0
        private var measureCount = 0
        private var layoutCount = 0
        private var revealScheduledCount = 0
        private var revealExecutedCount = 0
        private var imeVisibleOrAnimating = false
        private var imeBottom = 0
        private var imeFrameTracing = false
        private var lastImeFrameNanos = 0L
        private var lastRootSize = ""
        private var lastScrollSize = ""

        private val imeFrameCallback =
            object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (!imeFrameTracing) return
                    if (lastImeFrameNanos != 0L) {
                        val durationMs = (frameTimeNanos - lastImeFrameNanos) / 1_000_000f
                        logIme("frameDuration durationMs=$durationMs slowFrame=${durationMs > 32f}")
                    }
                    lastImeFrameNanos = frameTimeNanos
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }

        override var boundDocumentKey: String? = null
            private set

        private val titleWatcher =
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (!programmaticChange) titleChangedCallback?.invoke()
                }
            }

        private val contentWatcher =
            object : TextWatcher {
                private var changedText = ""

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    changedText = s?.subSequence(start, start + count)?.toString().orEmpty()
                }

                override fun afterTextChanged(s: Editable?) {
                    if (programmaticChange || continuingList) return
                    if (changedText.endsWith('\n')) continueMarkdownLine(s ?: return)
                    contentChangedCallback?.invoke()
                }
            }

        init {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            clipToPadding = false
            titleEditText.diagnostic = ::recordEditTextDiagnostic
            contentEditText.diagnostic = ::recordEditTextDiagnostic

            scrollView.apply {
                isFillViewport = true
                clipToPadding = false
                isVerticalScrollBarEnabled = false
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    if (scrollY != oldScrollY) logIme("scrollYChanged before=$oldScrollY after=$scrollY")
                    scrollChangedCallback?.invoke()
                }
            }
            editorColumn.apply {
                orientation = LinearLayout.VERTICAL
                clipToPadding = false
                setPadding(dp(16), dp(6), dp(16), dp(24))
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            }
            titleEditText.apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setPadding(0, 0, 0, dp(8))
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                addTextChangedListener(titleWatcher)
            }
            contentEditText.apply {
                gravity = Gravity.TOP or Gravity.START
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setPadding(0, 0, 0, 0)
                minHeight = 0
                minLines = 12
                setSingleLine(false)
                setHorizontallyScrolling(false)
                isVerticalScrollBarEnabled = false
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                enableUndoRedo()
                addTextChangedListener(contentWatcher)
                setOnCanUndoRedoListener { _, _ -> undoRedoChangedCallback?.invoke() }
                onSelectionChangedListener = { start, end ->
                    updateInlineImagePreviewSelection()
                    if (!programmaticChange) selectionChangedCallback?.invoke(start, end)
                }
            }
            val interactionListener =
                View.OnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                        userInteractionCallback?.invoke()
                    }
                    false
                }
            titleEditText.setOnTouchListener(interactionListener)
            contentEditText.setOnTouchListener(interactionListener)
            scrollView.setOnTouchListener(interactionListener)
            editorColumn.addView(titleEditText)
            editorColumn.addView(contentEditText)
            scrollView.addView(editorColumn)
            addView(scrollView)

            addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val size = "${right - left}x${bottom - top}"
                if (size != lastRootSize) {
                    lastRootSize = size
                    logIme("rootSizeChanged size=$size")
                }
            }
            scrollView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                val size = "${right - left}x${bottom - top}"
                if (size != lastScrollSize) {
                    lastScrollSize = size
                    logIme("scrollViewSizeChanged size=$size scrollY=${scrollView.scrollY}")
                }
            }
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val wasVisible = imeVisibleOrAnimating
                imeBottom = bottom
                imeVisibleOrAnimating = bottom > 0 || imeVisibleOrAnimating
                if (!wasVisible && bottom > 0) logIme("imeFirstVisible imeBottom=$bottom")
                logIme("imeInsetProgress source=apply imeBottom=$bottom")
                insets
            }
            ViewCompat.setWindowInsetsAnimationCallback(
                this,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                            imeVisibleOrAnimating = true
                            imeFrameTracing = true
                            lastImeFrameNanos = 0L
                            Choreographer.getInstance().postFrameCallback(imeFrameCallback)
                            logIme("imeAnimationStart imeBottom=$imeBottom")
                        }
                    }

                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                    ): WindowInsetsCompat {
                        if (runningAnimations.any { it.typeMask and WindowInsetsCompat.Type.ime() != 0 }) {
                            imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                            logIme("imeInsetProgress source=animation imeBottom=$imeBottom")
                        }
                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                            imeVisibleOrAnimating = imeBottom > 0
                            imeFrameTracing = false
                            Choreographer.getInstance().removeFrameCallback(imeFrameCallback)
                            logIme("imeStable imeBottom=$imeBottom visible=$imeVisibleOrAnimating")
                        }
                    }
                },
            )
        }

        fun configureUserPerf(
            session: EditorOpenSession?,
            openStartRealtimeMs: Long?,
            sizeTier: String,
            onFrameCommitted: (Long) -> Unit,
        ) {
            openSession = session
            frameCommittedCallback = onFrameCommitted
            userPerfOpenStartRealtimeMs = openStartRealtimeMs
            userPerfSizeTier = sizeTier
        }

        fun recordAndroidViewUpdate() {
            androidViewUpdateCount++
            logIme("androidViewUpdate")
        }

        fun updateComposeImeBottom(bottom: Int) {
            if (imeBottom == bottom) return
            imeBottom = bottom
            imeVisibleOrAnimating = bottom > 0
            if (bottom <= 0 || !contentEditText.hasFocus()) return
            requestCursorRectangle("RECTANGLE_REQUEST")
        }

        internal fun debugCounters(): QuillpadDebugCounters =
            QuillpadDebugCounters(
                androidViewUpdates = androidViewUpdateCount,
                configureCalls = configureCount,
                configureApplied = configureAppliedCount,
                bindCalls = bindCount,
                fullTextSnapshots = fullTextSnapshotCount,
                revealScheduled = revealScheduledCount,
                revealExecuted = revealExecutedCount,
            )

        private fun recordEditTextDiagnostic(event: String) {
            when {
                event.startsWith("editTextMeasure role=content") -> {
                    measureCount++
                    currentTextGeneration?.let { generation ->
                        if (contentLength() == generation.contentLength && measuredGenerationSequence != generation.sequence) {
                            measuredGenerationSequence = generation.sequence
                            logUserStage("newTextMeasureDone", generation)
                        }
                    }
                }
                event.startsWith("editTextLayout role=content") -> {
                    layoutCount++
                    currentTextGeneration?.let { generation ->
                        if (
                            contentLength() == generation.contentLength &&
                            measuredGenerationSequence == generation.sequence &&
                            laidOutGenerationSequence != generation.sequence
                        ) {
                            laidOutGenerationSequence = generation.sequence
                            logUserStage("newTextLayoutDone", generation)
                        }
                    }
                }
            }
            logIme(event)
        }

        private fun logIme(event: String) {
            val selection =
                if (contentEditText.selectionStart >= 0) {
                    "${contentEditText.selectionStart}:${contentEditText.selectionEnd}"
                } else {
                    "-1:-1"
                }
            KardLeafLog.d(
                QUILLPAD_IME_TAG,
                "$event contentLen=${contentLength()} selection=$selection measureCount=$measureCount layoutCount=$layoutCount " +
                    "updateCount=$androidViewUpdateCount configureCount=$configureCount bindCount=$bindCount " +
                    "scrollY=${scrollView.scrollY} imeBottom=$imeBottom " +
                    (openSession?.trace(contentLength()) ?: "sessionId=-1 documentKey=${boundDocumentKey?.hashCode()} thread=${Thread.currentThread().name}"),
            )
        }

        private fun logUserStage(
            stage: String,
            generation: TextGeneration,
        ) {
            val session = openSession
            val sessionTrace =
                session?.trace(generation.contentLength)
                    ?: "sessionId=${generation.sessionId} documentKey=${generation.documentKey.hashCode()} elapsed=-1ms"
            KardLeafLog.d(
                QUILLPAD_PERF_TAG,
                "editorOpen $stage ${generation.trace} $sessionTrace",
            )
        }

        fun configure(
            titleHint: String,
            contentHint: String,
            textColor: Int,
            hintColor: Int,
            titleTextSizeSp: Float,
            contentTextSizeSp: Float,
            contentLineHeightMultiplier: Float,
            contentLetterSpacingSp: Float,
            contentParagraphSpacingDp: Float,
            contentFontFamily: String,
            showTitle: Boolean,
            currentFolder: String = "",
            inlineImagePreviewEnabled: Boolean = true,
            readOnly: Boolean,
            onTitleChanged: () -> Unit,
            onContentChanged: () -> Unit,
            onSelectionChanged: (Int, Int) -> Unit,
            onUndoRedoChanged: () -> Unit,
            onUserInteraction: () -> Unit,
            onFastScrollSourceScrolled: () -> Unit,
            onInlineImageClicked: (String) -> Unit = {},
        ) {
            configureCount++
            titleChangedCallback = onTitleChanged
            contentChangedCallback = onContentChanged
            selectionChangedCallback = onSelectionChanged
            undoRedoChangedCallback = onUndoRedoChanged
            userInteractionCallback = onUserInteraction
            scrollChangedCallback = onFastScrollSourceScrolled
            contentEditText.configureInlineImagePreview(
                enabled = inlineImagePreviewEnabled && !readOnly,
                currentFolder = currentFolder,
                lineSpacingMultiplier = contentLineHeightMultiplier,
                onClick = onInlineImageClicked,
            )
            val config =
                QuillpadEditorConfig(
                    titleHint = titleHint,
                    contentHint = contentHint,
                    textColor = textColor,
                    hintColor = hintColor,
                    titleTextSizeSp = titleTextSizeSp,
                    contentTextSizeSp = contentTextSizeSp,
                    contentLineHeightMultiplier = contentLineHeightMultiplier,
                    contentLetterSpacingSp = contentLetterSpacingSp,
                    contentParagraphSpacingDp = contentParagraphSpacingDp,
                    contentFontFamily = contentFontFamily,
                    showTitle = showTitle,
                    currentFolder = currentFolder,
                    inlineImagePreviewEnabled = inlineImagePreviewEnabled,
                    readOnly = readOnly,
                )
            if (appliedConfig == config) {
                logIme("configure skipped=same")
                return
            }
            appliedConfig = config
            configureAppliedCount++
            logIme("configure applied=true")
            titleEditText.apply {
                hint = titleHint
                setTextColor(textColor)
                setHintTextColor(hintColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, titleTextSizeSp)
                visibility = if (showTitle) View.VISIBLE else View.GONE
                isFocusable = !readOnly
                isFocusableInTouchMode = !readOnly
                isCursorVisible = !readOnly
                showSoftInputOnFocus = !readOnly
            }
            contentEditText.apply {
                hint = contentHint
                setTextColor(textColor)
                setHintTextColor(hintColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, contentTextSizeSp)
                typeface = editorTypeface(contentFontFamily)
                setLineSpacing(0f, contentLineHeightMultiplier)
                letterSpacing = contentLetterSpacingSp / contentTextSizeSp.coerceAtLeast(1f)
                isFocusable = !readOnly
                isFocusableInTouchMode = !readOnly
                isCursorVisible = !readOnly
                showSoftInputOnFocus = !readOnly
            }
        }

        fun bindDocument(
            documentKey: String,
            initialTitle: String,
            initialContent: String,
            preferredSnapshot: KardLeafEditorSnapshot,
        ) {
            bindCount++
            logIme("bindDocument start incomingKey=${documentKey.hashCode()} incomingLen=${initialContent.length}")
            contentEditText.bindInlineImagePreviewDocument(documentKey)
            val differentDocument = boundDocumentKey != documentKey
            val incomingChanged = loadedTitle != initialTitle || loadedContent != initialContent
            if (!differentDocument && !incomingChanged) {
                logIme("bindDocument skipped=same")
                return
            }
            if (boundDocumentKey == null) {
                boundDocumentKey = documentKey
                setInitialSnapshot(preferredSnapshot.title, preferredSnapshot.content, preferredSnapshot.selection)
                loadedTitle = initialTitle
                loadedContent = initialContent
                return
            }
            if (differentDocument) {
                boundDocumentKey = documentKey
                setInitialSnapshot(preferredSnapshot.title, preferredSnapshot.content, preferredSnapshot.selection)
                loadedTitle = initialTitle
                loadedContent = initialContent
                return
            }
            fullTextSnapshotCount++
            logIme("fullTextSnapshot reason=incomingChanged")
            val currentTitle = getTitleString()
            val currentContent = getContentString()
            val safeToReload = currentTitle == loadedTitle && currentContent == loadedContent
            val missingInitialText =
                (currentTitle.isEmpty() && initialTitle.isNotEmpty()) ||
                    (currentContent.isEmpty() && initialContent.isNotEmpty())
            if (safeToReload || missingInitialText) {
                setInitialSnapshot(initialTitle, initialContent, getContentSelection())
            }
            boundDocumentKey = documentKey
            loadedTitle = initialTitle
            loadedContent = initialContent
        }

        private fun setInitialSnapshot(
            title: String,
            content: String,
            selection: TextRange,
        ) {
            val sessionId = openSession?.sessionId ?: 0L
            val generation = TextGeneration(++textGenerationSequence, sessionId, boundDocumentKey.orEmpty(), content.length)
            currentTextGeneration = generation
            measuredGenerationSequence = -1L
            laidOutGenerationSequence = -1L
            preDrawGenerationSequence = -1L
            committedGenerationSequence = -1L
            logUserStage("setTextStart", generation)
            val startedAt = SystemClock.elapsedRealtimeNanos()
            programmaticChange = true
            try {
                titleEditText.withoutTextWatchers { setText(title) }
                contentEditText.withoutTextWatchers { setText(content) }
                val start = selection.start.coerceIn(0, content.length)
                val end = selection.end.coerceIn(0, content.length)
                contentEditText.setSelection(start, end)
                contentEditText.clearHistory()
            } finally {
                programmaticChange = false
            }
            contentEditText.refreshInlineImagePreview()
            val durationUs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000
            KardLeafLog.d(
                QUILLPAD_PERF_TAG,
                "editorOpen setTextDone durationUs=$durationUs ${generation.trace} " +
                    (openSession?.trace(content.length) ?: "sessionId=$sessionId documentKey=${boundDocumentKey?.hashCode()} elapsed=-1ms"),
            )
            scheduleCurrentTextPreDraw(generation)
        }

        private fun scheduleCurrentTextPreDraw(generation: TextGeneration) {
            pendingPreDrawListener?.let { listener ->
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(listener)
            }
            val listener =
                object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        if (
                            currentTextGeneration != generation ||
                            measuredGenerationSequence != generation.sequence ||
                            laidOutGenerationSequence != generation.sequence ||
                            contentLength() != generation.contentLength
                        ) {
                            return true
                        }
                        if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(this)
                        pendingPreDrawListener = null
                        preDrawGenerationSequence = generation.sequence
                        logUserStage("newTextPreDraw", generation)
                        registerCurrentFrameCommit(generation)
                        return true
                    }
                }
            pendingPreDrawListener = listener
            viewTreeObserver.addOnPreDrawListener(listener)
            requestLayout()
            invalidate()
        }

        private fun registerCurrentFrameCommit(generation: TextGeneration) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                viewTreeObserver.registerFrameCommitCallback { markCurrentFrameCommitted(generation, "frameCommitCallback") }
            } else {
                Choreographer.getInstance().postFrameCallback {
                    postOnAnimation { markCurrentFrameCommitted(generation, "twoFrameFallback") }
                }
            }
        }

        private fun markCurrentFrameCommitted(
            generation: TextGeneration,
            source: String,
        ) {
            if (
                currentTextGeneration != generation ||
                preDrawGenerationSequence != generation.sequence ||
                committedGenerationSequence == generation.sequence ||
                contentLength() != generation.contentLength
            ) {
                return
            }
            committedGenerationSequence = generation.sequence
            logUserStage("frameCommitted source=$source", generation)
            val session = openSession
            KardLeafLog.d(
                QUILLPAD_PERF_TAG,
                "kernel bodyFirstVisible stage=frameCommitted source=$source ${generation.trace} " +
                    (session?.trace(generation.contentLength) ?: "sessionId=${generation.sessionId} elapsed=-1ms"),
            )
            KardLeafLog.d(
                USER_PERF_TAG,
                "editorOpen bodyRendered stage=frameCommitted engine=QUILLPAD mode=quillpadStyle ${generation.trace} " +
                    (session?.trace(generation.contentLength) ?: "sessionId=${generation.sessionId} elapsed=-1ms"),
            )
            if (session != null && generation.sessionId == session.sessionId) frameCommittedCallback?.invoke(session.sessionId)
        }

        private fun continueMarkdownLine(editable: Editable) {
            val cursor = contentEditText.selectionStart
            if (cursor <= 0) return
            val previousLineEnd = cursor - 1
            val previousLineStart = editable.lastIndexOf('\n', previousLineEnd - 1).let { if (it < 0) 0 else it + 1 }
            val previousLine = editable.substring(previousLineStart, previousLineEnd)
            val prefix = quillpadNextLinePrefix(previousLine) ?: return
            continuingList = true
            try {
                if (quillpadShouldEndList(previousLine)) {
                    editable.delete(previousLineStart, cursor)
                } else {
                    editable.insert(cursor, prefix)
                }
            } finally {
                continuingList = false
            }
        }

        private fun requestCursorRectangle(source: String) {
            if (!contentEditText.hasFocus()) {
                logIme("requestRectangleOnScreen skipped=focus source=$source")
                return
            }
            val layout = contentEditText.layout ?: return
            val offset = contentEditText.selectionEnd.coerceIn(0, contentLength())
            val line = layout.getLineForOffset(offset)
            revealScheduledCount++
            val rect =
                Rect(
                    0,
                    contentEditText.totalPaddingTop + layout.getLineTop(line),
                    contentEditText.width,
                    contentEditText.totalPaddingTop + layout.getLineBottom(line),
                )
            val before = scrollView.scrollY
            val requested = contentEditText.requestRectangleOnScreen(rect, true)
            if (requested) revealExecutedCount++
            logIme(
                "requestRectangleOnScreen source=$source requested=$requested scrollBefore=$before " +
                    "scrollAfter=${scrollView.scrollY} cursorTop=${rect.top} cursorBottom=${rect.bottom}",
            )
        }

        override fun getTitleString(): String = titleEditText.text?.toString().orEmpty()

        override fun getContentString(): String = contentEditText.text?.toString().orEmpty()

        override fun contentLength(): Int = contentEditText.length()

        override fun getContentSelection(): TextRange {
            val length = contentLength()
            return TextRange(
                contentEditText.selectionStart.coerceIn(0, length),
                contentEditText.selectionEnd.coerceIn(0, length),
            )
        }

        override fun getFastScrollMetrics(): EditorFastScrollMetrics {
            val max = maxScrollY()
            if (scrollView.height <= 0 || max <= 0) return EditorFastScrollMetrics()
            return EditorFastScrollMetrics(
                canScroll = true,
                ratio = (scrollView.scrollY.toFloat() / max).coerceIn(0f, 1f),
                thumbFraction = (scrollView.height.toFloat() / (scrollView.height + max)).coerceIn(0f, 1f),
            )
        }

        override fun fastScrollToRatio(ratio: Float) {
            val max = maxScrollY()
            scrollView.scrollTo(0, (ratio.coerceIn(0f, 1f) * max).roundToInt())
        }

        override fun shouldReserveContentTouchForEditing(
            windowX: Float,
            windowY: Float,
            radiusPx: Float,
        ): Boolean {
            val location = IntArray(2)
            contentEditText.getLocationInWindow(location)
            val localX = windowX - location[0]
            val localY = windowY - location[1]
            if (localX !in -radiusPx..(contentEditText.width + radiusPx) ||
                localY !in -radiusPx..(contentEditText.height + radiusPx)
            ) {
                return false
            }
            val selection = getContentSelection()
            if (selection.start != selection.end) return true
            val layout = contentEditText.layout ?: return true
            val line = layout.getLineForOffset(selection.end)
            val cursorX = location[0] + contentEditText.totalPaddingLeft + layout.getPrimaryHorizontal(selection.end)
            val cursorY =
                location[1] + contentEditText.totalPaddingTop +
                    (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f
            return abs(windowX - cursorX) <= radiusPx && abs(windowY - cursorY) <= radiusPx
        }

        override fun insertAtContentCursor(
            prefix: String,
            suffix: String,
        ) {
            val selection = getContentSelection()
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            val selected = contentEditText.text?.substring(start, end).orEmpty()
            contentEditText.text?.replace(start, end, prefix + selected + suffix)
            contentEditText.setSelection(start + prefix.length + selected.length)
        }

        override fun replaceContentSelection(insertion: String) {
            val selection = getContentSelection()
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            contentEditText.text?.replace(start, end, insertion)
            contentEditText.setSelection(start + insertion.length)
        }

        override fun replaceContent(
            newText: String,
            selection: TextRange?,
        ) {
            contentEditText.text?.replace(0, contentEditText.length(), newText)
            val target = selection ?: TextRange(newText.length)
            setContentSelection(target.start, target.end)
        }

        override fun setContentSelection(
            start: Int,
            end: Int,
        ) {
            val length = contentLength()
            contentEditText.setSelection(start.coerceIn(0, length), end.coerceIn(0, length))
        }

        override fun focusContent() {
            logIme("requestFocusStart role=content")
            if (!contentEditText.requestFocus()) {
                logIme("requestFocusDone role=content focused=false")
                return
            }
            logIme("requestFocusDone role=content focused=true")
            contentEditText.post {
                val shown =
                    context.getSystemService<InputMethodManager>()
                        ?.showSoftInput(contentEditText, InputMethodManager.SHOW_IMPLICIT) == true
                logIme("showSoftInput requested=true accepted=$shown")
                requestCursorRectangle("RECTANGLE_REQUEST_FOCUS")
            }
        }

        override fun scrollContentOffsetToVisible(offset: Int) {
            contentEditText.post {
                val layout = contentEditText.layout ?: return@post
                val line = layout.getLineForOffset(offset.coerceIn(0, contentLength()))
                val target = contentEditText.top + layout.getLineTop(line) - height / 4
                scrollView.smoothScrollTo(0, target.coerceAtLeast(0))
            }
        }

        override fun scrollToProgress(progress: Float) = fastScrollToRatio(progress)

        override fun highlightContentSearch(
            query: String,
            currentStart: Int,
            useRegex: Boolean,
            matchCase: Boolean,
        ): Int {
            clearContentSearchHighlights()
            if (query.isBlank()) return 0
            val editable = contentEditText.text ?: return 0
            val result = buildNoteSearchMatches(editable.toString(), query, useRegex, matchCase)
            if (result.errorMessage != null) return 0

            val density = resources.displayMetrics.density
            result.matches.forEach { match ->
                val isCurrent = match.start == currentStart
                var containsLineBreak = false
                for (index in match.start until match.end) {
                    if (editable[index] == '\n') {
                        containsLineBreak = true
                        break
                    }
                }
                val span = if (containsLineBreak) {
                    QuillpadMultilineSearchHighlightSpan(SEARCH_HIGHLIGHT_COLOR)
                } else {
                    QuillpadRoundedSearchHighlightSpan(
                        backgroundColor = SEARCH_HIGHLIGHT_COLOR,
                        outlineColor = if (isCurrent) SEARCH_CURRENT_OUTLINE_COLOR else null,
                        horizontalInsetPx = density,
                        verticalInsetPx = density,
                        cornerRadiusPx = density * 3f,
                        outlineWidthPx = density,
                    )
                }
                editable.setSpan(
                    span,
                    match.start,
                    match.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            contentEditText.invalidate()
            return result.matches.size
        }

        override fun clearContentSearchHighlights() {
            val editable = contentEditText.text ?: return
            editable.getSpans(0, editable.length, QuillpadRoundedSearchHighlightSpan::class.java)
                .forEach(editable::removeSpan)
            editable.getSpans(0, editable.length, QuillpadMultilineSearchHighlightSpan::class.java)
                .forEach(editable::removeSpan)
            contentEditText.invalidate()
        }

        override fun undoContent() = contentEditText.undo()

        override fun redoContent() = contentEditText.redo()

        override fun canUndoContent(): Boolean = contentEditText.canUndo()

        override fun canRedoContent(): Boolean = contentEditText.canRedo()

        override fun clearContentHistory() = contentEditText.clearHistory()

        override fun refreshContentInlineImagePreviews() = contentEditText.refreshInlineImagePreview()

        override fun executeCommand(
            command: String,
            args: List<Any>,
        ): Boolean {
            when (command) {
                "toggleHeading" -> contentEditText.setHeadingLevel((args.firstOrNull() as? Number)?.toInt() ?: 1)
                "toggleBold" -> contentEditText.insertMarkdown(MarkdownSpan.BOLD)
                "toggleItalic" -> contentEditText.insertMarkdown(MarkdownSpan.ITALICS)
                "toggleUnderline" -> contentEditText.insertUnderline()
                "toggleStrike" -> contentEditText.insertMarkdown(MarkdownSpan.STRIKETHROUGH)
                "toggleCode" -> contentEditText.insertMarkdown(MarkdownSpan.CODE)
                "toggleBlockquote" -> contentEditText.insertMarkdown(MarkdownSpan.QUOTE)
                "insertHorizontalRule" -> contentEditText.insertDivider()
                "insertCodeBlock" -> contentEditText.insertCodeBlock()
                "toggleUnorderedList" -> contentEditText.toggleBulletCurrentLine()
                "toggleOrderedList" -> contentEditText.toggleOrderedCurrentLine()
                "toggleCheckList" -> contentEditText.toggleChecklistCurrentLine()
                "indent" -> contentEditText.indentCurrentLine()
                "outdent" -> contentEditText.outdentCurrentLine()
                "selectRange" ->
                    setContentSelection(
                        (args.getOrNull(0) as? Number)?.toInt() ?: return false,
                        (args.getOrNull(1) as? Number)?.toInt() ?: return false,
                    )
                else -> return false
            }
            return true
        }

        override fun dispose(clearText: Boolean) {
            titleChangedCallback = null
            contentChangedCallback = null
            selectionChangedCallback = null
            undoRedoChangedCallback = null
            userInteractionCallback = null
            scrollChangedCallback = null
            frameCommittedCallback = null
            imeFrameTracing = false
            Choreographer.getInstance().removeFrameCallback(imeFrameCallback)
            pendingPreDrawListener?.let { listener ->
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(listener)
            }
            pendingPreDrawListener = null
            contentEditText.onSelectionChangedListener = null
            contentEditText.releaseInlineImagePreview()
            if (clearText) {
                programmaticChange = true
                titleEditText.text?.clear()
                contentEditText.text?.clear()
                programmaticChange = false
            }
        }

        private fun hasEditorFocus(): Boolean = titleEditText.hasFocus() || contentEditText.hasFocus()

        private fun maxScrollY(): Int = ((scrollView.getChildAt(0)?.height ?: 0) - scrollView.height).coerceAtLeast(0)

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

        private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

        private fun editorTypeface(fontFamily: String): Typeface {
            val normalized = fontFamily.trim().lowercase()
            if (cachedTypefaceFamily == normalized) return cachedTypeface ?: Typeface.DEFAULT
            val typeface =
                when (normalized) {
                    "system", "sans", "sans-serif" -> Typeface.DEFAULT
                    "serif" -> Typeface.SERIF
                    "monospace" -> Typeface.MONOSPACE
                    else -> Typeface.create(fontFamily.trim(), Typeface.NORMAL)
                }
            cachedTypefaceFamily = normalized
            cachedTypeface = typeface
            return typeface
        }

    }

@Composable
fun KardLeafQuillpadEditor(
    initialTitle: String,
    initialContent: String,
    documentKey: String,
    controller: KardLeafEditorController,
    onTitleChanged: () -> Unit,
    onContentChanged: () -> Unit,
    onUndoRedoChanged: () -> Unit,
    onUserInteraction: () -> Unit = {},
    onFastScrollSourceScrolled: () -> Unit = {},
    onInlineImageClicked: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    titleHint: String = "",
    contentHint: String = "",
    textColor: Color,
    hintColor: Color,
    titleTextSize: TextUnit = 22.sp,
    contentTextSize: TextUnit = 16.sp,
    contentLineHeightMultiplier: Float = 1.55f,
    contentLetterSpacingSp: Float = 0f,
    contentParagraphSpacingDp: Float = 8f,
    contentFontFamily: String = "system",
    requestFocusToken: Int = 0,
    initialSelection: TextRange? = null,
    showTitle: Boolean = true,
    currentFolder: String = "",
    inlineImagePreviewEnabled: Boolean = true,
    readOnly: Boolean = false,
    imeBottomPx: Int = 0,
    openSession: EditorOpenSession? = null,
    onFrameCommitted: (Long) -> Unit = {},
    userPerfOpenStartRealtimeMs: Long? = null,
    userPerfSizeTier: String = "unknown",
) {
    controller.acceptInitialSnapshot(documentKey, initialTitle, initialContent, initialSelection)
    val currentOnTitleChanged = rememberUpdatedState(onTitleChanged)
    val currentOnContentChanged = rememberUpdatedState(onContentChanged)
    val currentOnUndoRedoChanged = rememberUpdatedState(onUndoRedoChanged)
    val currentOnUserInteraction = rememberUpdatedState(onUserInteraction)
    val currentOnFastScrollSourceScrolled = rememberUpdatedState(onFastScrollSourceScrolled)
    val currentOnInlineImageClicked = rememberUpdatedState(onInlineImageClicked)
    val currentOnFrameCommitted = rememberUpdatedState(onFrameCommitted)
    val viewRef = remember { AtomicReference<KardLeafQuillpadEditorView?>(null) }
    val handledFocusToken = remember { AtomicInteger(-1) }

    DisposableEffect(controller) {
        onDispose { viewRef.getAndSet(null)?.let { controller.detach(it) } }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val start = SystemClock.elapsedRealtime()
            KardLeafQuillpadEditorView(context).also { view ->
                viewRef.set(view)
                KardLeafLog.d(
                    QUILLPAD_PERF_TAG,
                    "editorOpen androidViewCreated durationMs=${SystemClock.elapsedRealtime() - start} " +
                        (openSession?.trace(initialContent.length) ?: "sessionId=-1 documentKey=${documentKey.hashCode()}"),
                )
            }
        },
        update = { view ->
            viewRef.set(view)
            val titleSize = if (titleTextSize == TextUnit.Unspecified) 22f else titleTextSize.value
            val contentSize = if (contentTextSize == TextUnit.Unspecified) 16f else contentTextSize.value
            view.configureUserPerf(
                session = openSession,
                openStartRealtimeMs = userPerfOpenStartRealtimeMs,
                sizeTier = userPerfSizeTier,
                onFrameCommitted = { currentOnFrameCommitted.value(it) },
            )
            view.updateComposeImeBottom(imeBottomPx)
            view.recordAndroidViewUpdate()
            view.configure(
                titleHint = titleHint,
                contentHint = contentHint,
                textColor = textColor.toArgb(),
                hintColor = hintColor.toArgb(),
                titleTextSizeSp = titleSize,
                contentTextSizeSp = contentSize,
                contentLineHeightMultiplier = contentLineHeightMultiplier,
                contentLetterSpacingSp = contentLetterSpacingSp,
                contentParagraphSpacingDp = contentParagraphSpacingDp,
                contentFontFamily = contentFontFamily,
                showTitle = showTitle,
                currentFolder = currentFolder,
                inlineImagePreviewEnabled = inlineImagePreviewEnabled,
                readOnly = readOnly,
                onTitleChanged = { currentOnTitleChanged.value() },
                onContentChanged = { currentOnContentChanged.value() },
                onSelectionChanged = controller::updateCachedSelection,
                onUndoRedoChanged = { currentOnUndoRedoChanged.value() },
                onUserInteraction = { currentOnUserInteraction.value() },
                onFastScrollSourceScrolled = { currentOnFastScrollSourceScrolled.value() },
                onInlineImageClicked = { reference -> currentOnInlineImageClicked.value(reference) },
            )
            view.bindDocument(documentKey, initialTitle, initialContent, controller.getCachedSnapshot())
            if (controller.editorView !== view) controller.attach(view, documentKey, initialTitle, initialContent)
            if (handledFocusToken.getAndSet(requestFocusToken) != requestFocusToken && requestFocusToken > 0) {
                view.post { view.focusContent() }
            }
        },
    )
}
