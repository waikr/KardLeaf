package com.kangle.kardleaf.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ReplacementSpan
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.widget.EditText
import androidx.compose.ui.text.TextRange
import androidx.documentfile.provider.DocumentFile
import com.kangle.kardleaf.data.repository.PrefsManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private const val EDITOR_TRACE_TAG = "KardLeafEditorTrace"
private const val LIVE_MARKDOWN_MAX_CHARS = 5000
private const val MERGE_EDIT_WINDOW_MS = 400L
private const val MAX_HISTORY_SIZE = 200
private const val IMAGE_PREVIEW_MAX_CHARS = 30000
private const val IMAGE_PREVIEW_MAX_COUNT = 12
private const val IMAGE_PREVIEW_DEBOUNCE_MS = 350L
private const val INLINE_IMAGE_PREVIEW_ENABLED = true

private const val SEARCH_HIGHLIGHT_COLOR = 0x66FFD54F

private class KardLeafSearchHighlightSpan(color: Int) : BackgroundColorSpan(color)

private class KardLeafInlineImageSpan(
    private val bitmap: Bitmap,
    private val widthPx: Int,
    private val heightPx: Int,
    private val verticalPaddingPx: Int,
    private val horizontalShadowPaddingPx: Int,
) : ReplacementSpan() {
    private val dst = Rect()
    private val shadowRect = RectF()
    private val ambientShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(2.5f, 0f, 0f, 0x16000000)
    }
    private val keyShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(4f, 0f, 1.5f, 0x22000000)
    }

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        fm?.let { metrics ->
            val targetHeight = heightPx + verticalPaddingPx * 2
            val currentHeight = metrics.descent - metrics.ascent
            if (targetHeight > currentHeight) {
                val center = (metrics.ascent + metrics.descent) / 2
                metrics.ascent = center - targetHeight / 2
                metrics.descent = metrics.ascent + targetHeight
                metrics.top = metrics.ascent
                metrics.bottom = metrics.descent
            }
        }
        return (widthPx + horizontalShadowPaddingPx * 2).coerceAtLeast(1)
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val imageTop = top + verticalPaddingPx
        val imageLeft = x.toInt() + horizontalShadowPaddingPx
        dst.set(
            imageLeft,
            imageTop,
            imageLeft + widthPx,
            imageTop + heightPx,
        )
        shadowRect.set(dst)
        canvas.drawRoundRect(shadowRect, 8f, 8f, ambientShadowPaint)
        canvas.drawRoundRect(shadowRect, 8f, 8f, keyShadowPaint)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }
}

private data class KardLeafImagePreviewMatch(
    val lineStart: Int,
    val lineEnd: Int,
    val reference: String,
)

private data class KardLeafImagePreviewItem(
    val lineStart: Int,
    val lineEnd: Int,
    val bitmap: Bitmap,
    val widthPx: Int,
    val heightPx: Int,
)

private val checkboxContinuationRegex = Regex("""^(\s*[-*+]\s+\[[ xX]\]\s*)(.*)$""")
private val bulletContinuationRegex = Regex("""^(\s*(?:[-*+•]|\d+\.))\s+(.*)$""")
private val quoteContinuationRegex = Regex("""^(\s*>\s+)(.*)$""")

private data class EditOperation(
    val start: Int,
    val deletedText: String,
    val insertedText: String,
    val selectionBefore: TextRange,
    val selectionAfter: TextRange,
)

class EditorEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : EditText(context, attrs) {

    var kardLeafSelectionCallback: ((Int, Int) -> Unit)? = null
    var kardLeafContentCallback: (() -> Unit)? = null
    var kardLeafUndoRedoCallback: (() -> Unit)? = null

    var smartContinuationEnabled: Boolean = true

    private val imagePreviewExecutor = Executors.newSingleThreadExecutor()
    private val imagePreviewToken = AtomicInteger(0)
    private val imagePreviewResolver = KardLeafInlineImagePreviewResolver(context)
    private val imagePreviewRefreshRunnable = Runnable { refreshInlineImagePreviewsNow() }
    private var imagePreviewFolder: String = ""
    private var imagePreviewItems: List<KardLeafImagePreviewItem> = emptyList()

    private val undoStack = ArrayDeque<EditOperation>()
    private val redoStack = ArrayDeque<EditOperation>()

    private var programmaticChange = false
    private var isApplyingHistory = false
    private var isHandlingContinuation = false

    private var pendingStart = 0
    private var pendingDeletedText = ""
    private var pendingSelectionBefore = TextRange(0, 0)

    private var lastOp: EditOperation? = null
    private var lastOpTimeMs = 0L
    private var lastInsertedText = ""
    private var lastInsertStart = -1

    private var debugImageInsertUntilMs = 0L
    private var debugImageExpectedCursor = -1

    private var markdownWatcher: TextWatcher? = null
    private var markdownWatcherAttached = false

    private val internalWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            if (isApplyingHistory || programmaticChange) return
            pendingStart = start
            pendingDeletedText = if (count > 0) {
                s?.subSequence(start, start + count)?.toString().orEmpty()
            } else {
                ""
            }
            pendingSelectionBefore = TextRange(
                selectionStart.coerceAtLeast(0),
                selectionEnd.coerceAtLeast(0),
            )
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (isApplyingHistory || programmaticChange) return
            val insertedText = if (count > 0) {
                s?.subSequence(start, start + count)?.toString().orEmpty()
            } else {
                ""
            }
            val deletedText = pendingDeletedText
            lastInsertedText = insertedText
            lastInsertStart = start

            if (deletedText.isEmpty() && insertedText.isEmpty()) return

            if (insertedText.length > 200 || deletedText.length > 200) {
                Log.d(
                    EDITOR_TRACE_TAG,
                    "editor large change start=$start deletedLen=${deletedText.length} insertedLen=${insertedText.length} " +
                        "textLen=${s?.length ?: -1} selection=$selectionStart..$selectionEnd " +
                        "viewHeight=$height lineCount=$lineCount scrollY=$scrollY hasFocus=${hasFocus()}",
                )
            }

            val now = SystemClock.elapsedRealtime()
            val op = EditOperation(
                start = pendingStart,
                deletedText = deletedText,
                insertedText = insertedText,
                selectionBefore = pendingSelectionBefore,
                selectionAfter = TextRange(
                    selectionStart.coerceAtLeast(0),
                    selectionEnd.coerceAtLeast(0),
                ),
            )

            if (shouldMergeWithLast(op, now)) {
                lastOp = mergeOps(lastOp!!, op)
            } else {
                flushPendingOp()
                lastOp = op
            }
            lastOpTimeMs = now
            redoStack.clear()
            notifyUndoRedoChanged()
        }

        override fun afterTextChanged(s: Editable?) {
            if (programmaticChange) return
            if (smartContinuationEnabled &&
                !isHandlingContinuation &&
                !isApplyingHistory &&
                lastInsertedText == "\n"
            ) {
                isHandlingContinuation = true
                try {
                    handleEnterKey(lastInsertStart)
                } finally {
                    isHandlingContinuation = false
                }
                lastInsertedText = ""
            }
            kardLeafContentCallback?.invoke()
            post { refreshMarkdownWatcher() }
            scheduleInlineImagePreviewRefresh()
        }
    }

    init {
        addTextChangedListener(internalWatcher)
    }

    fun configureInlineImagePreviewFolder(currentFolder: String) {
        val normalized = currentFolder.trim().trim('/')
        if (imagePreviewFolder == normalized) return
        imagePreviewFolder = normalized
        scheduleInlineImagePreviewRefresh()
    }

    fun releaseInlineImagePreviews() {
        removeCallbacks(imagePreviewRefreshRunnable)
        imagePreviewToken.incrementAndGet()
        clearInlineImagePreviewSpans()
        imagePreviewExecutor.shutdownNow()
    }

    private fun scheduleInlineImagePreviewRefresh() {
        removeCallbacks(imagePreviewRefreshRunnable)
        if (!INLINE_IMAGE_PREVIEW_ENABLED) {
            clearInlineImagePreviewSpans()
            return
        }
        postDelayed(imagePreviewRefreshRunnable, IMAGE_PREVIEW_DEBOUNCE_MS)
    }

    private fun refreshInlineImagePreviewsNow() {
        if (!INLINE_IMAGE_PREVIEW_ENABLED) {
            clearInlineImagePreviewSpans()
            return
        }
        val editable = text ?: return
        val source = editable.toString()
        if (source.length > IMAGE_PREVIEW_MAX_CHARS || width <= 0) {
            clearInlineImagePreviewSpans()
            return
        }
        val matches = findInlineImagePreviewMatches(source)
        if (matches.isEmpty()) {
            clearInlineImagePreviewSpans()
            return
        }

        val token = imagePreviewToken.incrementAndGet()
        val folder = imagePreviewFolder
        val horizontalShadowPadding = dp(4)
        val availableWidth =
            (width - totalPaddingLeft - totalPaddingRight - horizontalShadowPadding * 2).coerceAtLeast(dp(160))
        val maxPreviewHeight = dp(220)
        imagePreviewExecutor.execute {
            val previews = matches.take(IMAGE_PREVIEW_MAX_COUNT).mapNotNull { match ->
                val bitmap = imagePreviewResolver.resolveBitmap(
                    currentFolder = folder,
                    reference = match.reference,
                    maxWidthPx = availableWidth,
                    maxHeightPx = maxPreviewHeight,
                ) ?: return@mapNotNull null
                val scale = minOf(
                    availableWidth.toFloat() / bitmap.width.coerceAtLeast(1),
                    maxPreviewHeight.toFloat() / bitmap.height.coerceAtLeast(1),
                    1f,
                )
                val previewWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val previewHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                KardLeafImagePreviewItem(
                    lineStart = match.lineStart,
                    lineEnd = match.lineEnd,
                    bitmap = bitmap,
                    widthPx = previewWidth,
                    heightPx = previewHeight,
                )
            }
            post {
                if (token != imagePreviewToken.get()) return@post
                if (source != text?.toString().orEmpty()) return@post
                applyInlineImagePreviewSpans(previews)
            }
        }
    }

    private fun applyInlineImagePreviewSpans(previews: List<KardLeafImagePreviewItem>) {
        clearInlineImagePreviewSpans(requestRelayout = false)
        if (previews.isEmpty()) {
            requestLayout()
            invalidate()
            return
        }
        val editable = text ?: return
        val extraPadding = dp(12)
        previews.forEach { preview ->
            val start = preview.lineStart.coerceIn(0, editable.length)
            val end = preview.lineEnd.coerceIn(start, editable.length)
            if (start < end) {
                editable.setSpan(
                    KardLeafInlineImageSpan(
                        bitmap = preview.bitmap,
                        widthPx = preview.widthPx,
                        heightPx = preview.heightPx,
                        verticalPaddingPx = extraPadding / 2,
                        horizontalShadowPaddingPx = dp(4),
                    ),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        imagePreviewItems = previews
        requestLayout()
        invalidate()
    }

    private fun clearInlineImagePreviewSpans(requestRelayout: Boolean = true) {
        val editable = text ?: return
        val spans = editable.getSpans(0, editable.length, KardLeafInlineImageSpan::class.java)
        spans.forEach { editable.removeSpan(it) }
        if (imagePreviewItems.isNotEmpty() || spans.isNotEmpty()) {
            imagePreviewItems = emptyList()
            if (requestRelayout) requestLayout()
            invalidate()
        }
    }


    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) {
            scheduleInlineImagePreviewRefresh()
        }
    }

    private fun findInlineImagePreviewMatches(source: String): List<KardLeafImagePreviewMatch> {
        val result = mutableListOf<KardLeafImagePreviewMatch>()
        val obsidian = Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]""")
        val standard = Regex("""!\[[^]]*]\((?!https?://|data:|file:)([^)]+)\)""", RegexOption.IGNORE_CASE)
        var lineStart = 0
        while (lineStart <= source.length && result.size < IMAGE_PREVIEW_MAX_COUNT) {
            val newline = source.indexOf('\n', lineStart)
            val lineEnd = if (newline >= 0) newline else source.length
            val line = source.substring(lineStart, lineEnd)
            val reference = obsidian.find(line)?.groupValues?.getOrNull(1)?.trim()
                ?: standard.find(line)?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')
            if (!reference.isNullOrBlank()) {
                result.add(
                    KardLeafImagePreviewMatch(
                        lineStart = lineStart,
                        lineEnd = lineEnd,
                        reference = reference,
                    ),
                )
            }
            if (newline < 0) break
            lineStart = newline + 1
        }
        return result
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun configureMarkdownWatcher(watcher: TextWatcher?) {
        if (markdownWatcher === watcher) {
            refreshMarkdownWatcher()
            return
        }
        if (markdownWatcherAttached && markdownWatcher != null) {
            removeTextChangedListener(markdownWatcher)
        }
        markdownWatcher = watcher
        markdownWatcherAttached = false
        refreshMarkdownWatcher()
    }

    private fun refreshMarkdownWatcher() {
        val watcher = markdownWatcher ?: return
        val shouldAttach = length() <= LIVE_MARKDOWN_MAX_CHARS
        when {
            shouldAttach && !markdownWatcherAttached -> {
                addTextChangedListener(watcher)
                markdownWatcherAttached = true
                Log.d(EDITOR_TRACE_TAG, "live markdown watcher attached textLen=${length()}")
            }
            !shouldAttach && markdownWatcherAttached -> {
                removeTextChangedListener(watcher)
                markdownWatcherAttached = false
                Log.d(EDITOR_TRACE_TAG, "live markdown watcher detached textLen=${length()}")
            }
        }
    }

    private fun shouldMergeWithLast(op: EditOperation, now: Long): Boolean {
        val last = lastOp ?: return false
        if (now - lastOpTimeMs > MERGE_EDIT_WINDOW_MS) return false
        if (last.deletedText.isEmpty() && op.deletedText.isEmpty() &&
            last.start + last.insertedText.length == op.start
        ) {
            return true
        }
        if (last.insertedText.isEmpty() && op.insertedText.isEmpty() &&
            op.start + op.deletedText.length == last.start
        ) {
            return true
        }
        return false
    }

    private fun mergeOps(a: EditOperation, b: EditOperation): EditOperation =
        if (a.deletedText.isEmpty() && b.deletedText.isEmpty()) {
            a.copy(insertedText = a.insertedText + b.insertedText, selectionAfter = b.selectionAfter)
        } else {
            a.copy(
                start = b.start,
                deletedText = b.deletedText + a.deletedText,
                selectionAfter = b.selectionAfter,
            )
        }

    private fun flushPendingOp() {
        lastOp?.let {
            undoStack.add(it)
            while (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeFirst()
        }
        lastOp = null
    }

    private fun notifyUndoRedoChanged() {
        kardLeafUndoRedoCallback?.invoke()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty() || lastOp != null

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        flushPendingOp()
        if (undoStack.isEmpty()) return
        val op = undoStack.removeLast()
        isApplyingHistory = true
        try {
            val editable = text ?: return
            val start = op.start
            val end = start + op.insertedText.length
            if (start in 0..end && end <= editable.length) {
                editable.replace(start, end, op.deletedText)
            }
            val len = editable.length
            setSelection(
                op.selectionBefore.start.coerceIn(0, len),
                op.selectionBefore.end.coerceIn(0, len),
            )
            redoStack.add(op)
        } finally {
            isApplyingHistory = false
        }
        kardLeafContentCallback?.invoke()
        notifyUndoRedoChanged()
        scheduleInlineImagePreviewRefresh()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val op = redoStack.removeLast()
        isApplyingHistory = true
        try {
            val editable = text ?: return
            val start = op.start
            val end = start + op.deletedText.length
            if (start in 0..end && end <= editable.length) {
                editable.replace(start, end, op.insertedText)
            }
            val len = editable.length
            setSelection(
                op.selectionAfter.start.coerceIn(0, len),
                op.selectionAfter.end.coerceIn(0, len),
            )
            undoStack.add(op)
        } finally {
            isApplyingHistory = false
        }
        kardLeafContentCallback?.invoke()
        notifyUndoRedoChanged()
        scheduleInlineImagePreviewRefresh()
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        lastOp = null
        notifyUndoRedoChanged()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (SystemClock.elapsedRealtime() <= debugImageInsertUntilMs) {
            Log.d(
                EDITOR_TRACE_TAG,
                "imageInsert selectionChanged selection=$selStart..$selEnd expectedCursor=$debugImageExpectedCursor " +
                    "programmatic=$programmaticChange textLen=${length()} hasFocus=${hasFocus()} layoutReady=${layout != null}",
            )
        }
        if (!programmaticChange) {
            kardLeafSelectionCallback?.invoke(selStart, selEnd)
        }
    }

    fun setInitialText(text: String, selection: TextRange? = null) {
        programmaticChange = true
        try {
            setText(text)
            val len = text.length
            val target = selection ?: TextRange(len, len)
            setSelection(
                target.start.coerceIn(0, len),
                target.end.coerceIn(0, len),
            )
        } finally {
            programmaticChange = false
        }
        clearHistory()
        post { refreshMarkdownWatcher() }
        scheduleInlineImagePreviewRefresh()
    }

    fun replaceAll(newText: String, selection: TextRange? = null) {
        flushPendingOp()
        val oldText = text?.toString().orEmpty()
        val beforeSelection = TextRange(
            selectionStart.coerceAtLeast(0),
            selectionEnd.coerceAtLeast(0),
        )
        programmaticChange = true
        try {
            setText(newText)
            val sel = selection ?: TextRange(newText.length, newText.length)
            val len = newText.length
            setSelection(
                sel.start.coerceIn(0, len),
                sel.end.coerceIn(0, len),
            )
        } finally {
            programmaticChange = false
        }
        if (oldText != newText) {
            undoStack.add(
                EditOperation(
                    start = 0,
                    deletedText = oldText,
                    insertedText = newText,
                    selectionBefore = beforeSelection,
                    selectionAfter = selection ?: TextRange(newText.length, newText.length),
                ),
            )
            while (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeFirst()
            redoStack.clear()
        }
        kardLeafSelectionCallback?.invoke(selectionStart, selectionEnd)
        kardLeafContentCallback?.invoke()
        notifyUndoRedoChanged()
        post { refreshMarkdownWatcher() }
        scheduleInlineImagePreviewRefresh()
    }

    fun insertAtCursor(prefix: String, suffix: String = "") {
        flushPendingOp()
        val editable = text ?: return
        val start = selectionStart.coerceIn(0, editable.length)
        val end = selectionEnd.coerceIn(0, editable.length)
        val selectedText = editable.substring(start, end)
        val insertion = prefix + selectedText + suffix
        val newCursor = start + prefix.length + selectedText.length
        val isImageInsertion = insertion.contains("attachments/") || insertion.contains("![[") || insertion.contains("![")
        programmaticChange = true
        try {
            editable.replace(start, end, insertion)
            setSelection(newCursor, newCursor)
        } finally {
            programmaticChange = false
        }
        if (isImageInsertion) {
            val afterSelection = getSelectionRange()
            Log.d(
                EDITOR_TRACE_TAG,
                "imageInsert editText replace after len=${editable.length} selection=${afterSelection.start}..${afterSelection.end} " +
                    "expectedCursor=$newCursor lineCount=$lineCount layoutReady=${layout != null} hasFocus=${hasFocus()}",
            )
            post {
                val cursor = selectionStart.coerceIn(0, length())
                val layoutInfo = layout?.let { currentLayout ->
                    val line = currentLayout.getLineForOffset(cursor)
                    "line=$line lineTop=${currentLayout.getLineTop(line)} lineBottom=${currentLayout.getLineBottom(line)}"
                } ?: "layout=null"
                Log.d(
                    EDITOR_TRACE_TAG,
                    "imageInsert editText post len=${length()} selection=$selectionStart..$selectionEnd " +
                        "expectedCursor=$newCursor $layoutInfo lineCount=$lineCount viewHeight=$height scrollY=$scrollY hasFocus=${hasFocus()}",
                )
            }
        }
        undoStack.add(
            EditOperation(
                start = start,
                deletedText = selectedText,
                insertedText = insertion,
                selectionBefore = TextRange(start, end),
                selectionAfter = TextRange(newCursor, newCursor),
            ),
        )
        while (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeFirst()
        redoStack.clear()
        kardLeafSelectionCallback?.invoke(newCursor, newCursor)
        kardLeafContentCallback?.invoke()
        notifyUndoRedoChanged()
        post { refreshMarkdownWatcher() }
        scheduleInlineImagePreviewRefresh()
    }

    fun replaceSelection(insertion: String) {
        flushPendingOp()
        val editable = text ?: return
        val rawStart = selectionStart.coerceIn(0, editable.length)
        val rawEnd = selectionEnd.coerceIn(0, editable.length)
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        val selectedText = editable.substring(start, end)
        val newCursor = start + insertion.length
        val isImageInsertion = insertion.contains("attachments/") || insertion.contains("![[") || insertion.contains("![")
        if (insertion.contains("attachments/") || insertion.contains("![[") || insertion.contains("![")) {
            debugImageExpectedCursor = newCursor
            debugImageInsertUntilMs = SystemClock.elapsedRealtime() + 2500L
            Log.d(
                EDITOR_TRACE_TAG,
                "imageInsert editText replace before len=${editable.length} selection=$rawStart..$rawEnd " +
                    "replace=$start..$end insertionLen=${insertion.length} newCursor=$newCursor hasFocus=${hasFocus()} " +
                    "insertion=${insertion.replace("\n", "\\n")}",
            )
        }
        programmaticChange = true
        try {
            editable.replace(start, end, insertion)
            setSelection(newCursor, newCursor)
        } finally {
            programmaticChange = false
        }
        if (insertion.contains("attachments/") || insertion.contains("![[") || insertion.contains("![")) {
            val afterSelection = getSelectionRange()
            Log.d(
                EDITOR_TRACE_TAG,
                "imageInsert editText replace after len=${editable.length} selection=${afterSelection.start}..${afterSelection.end} " +
                    "expectedCursor=$newCursor lineCount=$lineCount layoutReady=${layout != null} hasFocus=${hasFocus()}",
            )
            post {
                val cursor = selectionStart.coerceIn(0, length())
                val layoutInfo = layout?.let { currentLayout ->
                    val line = currentLayout.getLineForOffset(cursor)
                    "line=$line lineTop=${currentLayout.getLineTop(line)} lineBottom=${currentLayout.getLineBottom(line)}"
                } ?: "layout=null"
                Log.d(
                    EDITOR_TRACE_TAG,
                    "imageInsert editText post len=${length()} selection=$selectionStart..$selectionEnd " +
                        "expectedCursor=$newCursor $layoutInfo lineCount=$lineCount viewHeight=$height scrollY=$scrollY hasFocus=${hasFocus()}",
                )
            }
        }
        undoStack.add(
            EditOperation(
                start = start,
                deletedText = selectedText,
                insertedText = insertion,
                selectionBefore = TextRange(rawStart, rawEnd),
                selectionAfter = TextRange(newCursor, newCursor),
            ),
        )
        while (undoStack.size > MAX_HISTORY_SIZE) undoStack.removeFirst()
        redoStack.clear()
        kardLeafSelectionCallback?.invoke(newCursor, newCursor)
        kardLeafContentCallback?.invoke()
        notifyUndoRedoChanged()
        post { refreshMarkdownWatcher() }
        scheduleInlineImagePreviewRefresh()
    }

    fun highlightSearch(query: String): Int {
        clearSearchHighlights()
        if (query.isBlank()) return 0
        val editable = text ?: return 0
        val source = editable.toString()
        var count = 0
        var searchFrom = 0
        while (searchFrom <= source.length - query.length) {
            val index = source.indexOf(query, startIndex = searchFrom, ignoreCase = true)
            if (index < 0) break
            val end = index + query.length
            editable.setSpan(
                KardLeafSearchHighlightSpan(SEARCH_HIGHLIGHT_COLOR),
                index,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            count++
            searchFrom = end.coerceAtLeast(index + 1)
        }
        Log.d(
            EDITOR_TRACE_TAG,
            "search highlight queryLen=${query.length} count=$count textLen=${source.length} selection=$selectionStart..$selectionEnd",
        )
        return count
    }

    fun clearSearchHighlights() {
        val editable = text ?: return
        val spans = editable.getSpans(0, editable.length, KardLeafSearchHighlightSpan::class.java)
        spans.forEach { editable.removeSpan(it) }
        if (spans.isNotEmpty()) {
            Log.d(EDITOR_TRACE_TAG, "search highlight cleared count=${spans.size} textLen=${editable.length}")
        }
    }

    fun getTextString(): String = text?.toString().orEmpty()

    fun getSelectionRange(): TextRange {
        val len = text?.length ?: 0
        return TextRange(
            selectionStart.coerceIn(0, len),
            selectionEnd.coerceIn(0, len),
        )
    }

    fun setSelectionRange(start: Int, end: Int = start) {
        val len = text?.length ?: 0
        runCatching { setSelection(start.coerceIn(0, len), end.coerceIn(0, len)) }
    }

    private fun handleEnterKey(newlinePos: Int) {
        val editable = text ?: return
        val fullText = editable.toString()
        val cursor = selectionStart
        if (newlinePos < 0 || cursor != newlinePos + 1) return

        val beforeNewline = fullText.substring(0, newlinePos)
        val afterInsertedNewline = fullText.substring(newlinePos + 1)

        val syntaxSuffixes = listOf("**", "_", "~~", "</u>", "`", "$")
        val jumpSuffix = syntaxSuffixes.firstOrNull { afterInsertedNewline.startsWith(it) }
        if (jumpSuffix != null) {
            val replaceEnd = newlinePos + 1 + jumpSuffix.length
            if (replaceEnd <= fullText.length) {
                editable.replace(newlinePos, replaceEnd, jumpSuffix + "\n")
                val newSel = newlinePos + jumpSuffix.length + 1
                setSelection(newSel, newSel)
                return
            }
        }

        val lastLineStart = beforeNewline.lastIndexOf('\n') + 1
        val lastLine = beforeNewline.substring(lastLineStart)
        val trimmedLastLine = lastLine.trimEnd()

        val checkboxMatch = checkboxContinuationRegex.find(trimmedLastLine)
        val bulletMatch = bulletContinuationRegex.find(trimmedLastLine)
        val quoteMatch = quoteContinuationRegex.find(trimmedLastLine)

        when {
            checkboxMatch != null -> {
                val prefix = checkboxMatch.groups[1]!!.value
                val lineContent = checkboxMatch.groups[2]!!.value
                if (lineContent.trim().isEmpty()) {
                    editable.delete(lastLineStart, cursor)
                } else {
                    editable.insert(cursor, prefix)
                    setSelection(cursor + prefix.length, cursor + prefix.length)
                }
            }
            bulletMatch != null -> {
                val prefixPart = bulletMatch.groups[1]!!.value
                val lineContent = bulletMatch.groups[2]!!.value
                if (lineContent.trim().isEmpty()) {
                    editable.delete(lastLineStart, cursor)
                } else {
                    val nextPrefix = if (prefixPart.trim().firstOrNull()?.isDigit() == true) {
                        val num = prefixPart.filter { it.isDigit() }.toIntOrNull() ?: 0
                        "${num + 1}. "
                    } else {
                        if (prefixPart.endsWith(" ")) prefixPart else "$prefixPart "
                    }
                    editable.insert(cursor, nextPrefix)
                    setSelection(cursor + nextPrefix.length, cursor + nextPrefix.length)
                }
            }
            quoteMatch != null -> {
                val prefix = quoteMatch.groups[1]!!.value
                val lineContent = quoteMatch.groups[2]!!.value
                if (lineContent.trim().isEmpty()) {
                    editable.delete(lastLineStart, cursor)
                } else {
                    editable.insert(cursor, prefix)
                    setSelection(cursor + prefix.length, cursor + prefix.length)
                }
            }
        }
    }
}

private class KardLeafInlineImagePreviewResolver(
    private val context: Context,
) {
    private val prefsManager = PrefsManager(context.applicationContext)

    fun resolveBitmap(
        currentFolder: String,
        reference: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? = runCatching {
        val rootUri = prefsManager.getRootUri()?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val root = DocumentFile.fromTreeUri(context, Uri.parse(rootUri)) ?: return@runCatching null
        val cleanRef = normalizePath(Uri.decode(reference).substringBefore("#"))
        if (cleanRef.isBlank()) return@runCatching null

        val current = normalizePath(currentFolder)
        val candidates = listOf(
            joinPath(current, cleanRef),
            cleanRef,
            joinPath(current, "attachments/$cleanRef"),
            joinPath(current, "附件/$cleanRef"),
            "attachments/$cleanRef",
            "附件/$cleanRef",
        )
            .map(::normalizePath)
            .distinct()

        val imageFile = candidates.firstNotNullOfOrNull { path ->
            val parent = path.substringBeforeLast("/", missingDelimiterValue = "")
            val name = path.substringAfterLast("/")
            findFolder(root, parent)?.findFile(name)?.takeIf { it.isFile }
        } ?: return@runCatching null

        decodeSampledBitmap(imageFile, maxWidthPx, maxHeightPx)
    }.getOrNull()

    private fun decodeSampledBitmap(
        imageFile: DocumentFile,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxWidthPx, maxHeightPx)
        }
        return context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var inSampleSize = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / inSampleSize >= reqWidth || halfHeight / inSampleSize >= reqHeight) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun findFolder(
        root: DocumentFile,
        folderPath: String,
    ): DocumentFile? {
        var current = root
        normalizePath(folderPath)
            .split("/")
            .filter { it.isNotBlank() }
            .forEach { part ->
                current = current.findFile(part)?.takeIf { it.isDirectory } ?: return null
            }
        return current
    }

    private fun joinPath(
        parent: String,
        child: String,
    ): String = when {
        parent.isBlank() -> child
        child.isBlank() -> parent
        else -> "${parent.trimEnd('/')}/${child.trimStart('/')}"
    }

    private fun normalizePath(path: String): String {
        val parts = path.replace('\\', '/').split('/').filter { it.isNotBlank() }
        val stack = mutableListOf<String>()
        parts.forEach { part ->
            when (part) {
                "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
