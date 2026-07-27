package org.qosp.notes.ui.utils.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.text.Editable
import android.text.Layout
import android.text.Spannable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.widget.EditText
import androidx.core.content.getSystemService
import java.util.ArrayDeque

open class ExtendedEditText : EditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        // Keep long editable text on TextView's cheapest line-breaking path.
        breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
        hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
    }

    val textWatchers: MutableList<TextWatcher> = mutableListOf()

    private var largeTextMeasureLogCount = 0
    private var largeTextLayoutLogCount = 0
    private val history = EditHistory()
    private val undoRedoTextWatcher = UndoRedoTextWatcher()
    private var undoRedoEnabled = false
    private var operationHint: OperationType? = null

    private val textBeforeSelection get() = text?.substring(0 until selectionStart).orEmpty()
    val currentLineStartPos get() = textBeforeSelection.lastIndexOf("\n") + 1
    val currentLineIndex get() = textBeforeSelection.filter { it == '\n' }.length

    val selectedText get() = text?.substring(selectionStart, selectionEnd)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentLength = text?.length ?: 0
        val startedAt = SystemClock.uptimeMillis()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val elapsed = SystemClock.uptimeMillis() - startedAt
        if (contentLength >= LARGE_TEXT_LOG_MIN_CHARS &&
            (largeTextMeasureLogCount < LARGE_TEXT_LOG_LIMIT || elapsed >= SLOW_LAYOUT_LOG_MS)
        ) {
            largeTextMeasureLogCount++
            Log.d(
                PERF_TAG,
                "editText onMeasure count=$largeTextMeasureLogCount id=$id contentLen=$contentLength " +
                    "elapsed=${elapsed}ms widthSpec=${View.MeasureSpec.getMode(widthMeasureSpec)}/" +
                    "${View.MeasureSpec.getSize(widthMeasureSpec)} heightSpec=" +
                    "${View.MeasureSpec.getMode(heightMeasureSpec)}/${View.MeasureSpec.getSize(heightMeasureSpec)} " +
                    "measured=${measuredWidth}x${measuredHeight} layoutHeight=${layout?.height ?: -1} " +
                    "lineCount=${layout?.lineCount ?: -1}",
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val contentLength = text?.length ?: 0
        val startedAt = SystemClock.uptimeMillis()
        super.onLayout(changed, left, top, right, bottom)
        val elapsed = SystemClock.uptimeMillis() - startedAt
        if (contentLength >= LARGE_TEXT_LOG_MIN_CHARS &&
            (largeTextLayoutLogCount < LARGE_TEXT_LOG_LIMIT || elapsed >= SLOW_LAYOUT_LOG_MS)
        ) {
            largeTextLayoutLogCount++
            Log.d(
                PERF_TAG,
                "editText onLayout count=$largeTextLayoutLogCount id=$id contentLen=$contentLength " +
                    "elapsed=${elapsed}ms changed=$changed bounds=${right - left}x${bottom - top} " +
                    "layoutHeight=${layout?.height ?: -1} lineCount=${layout?.lineCount ?: -1}",
            )
        }
    }

    var isMarkdownEnabled: Boolean = false
    var onUndoRedoListener: OnCanUndoRedoListener? = null
    var onSelectionChangedListener: ((Int, Int) -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onSelectionChangedListener?.invoke(selStart, selEnd)
    }

    // With a regular EditText, users can paste rich text inside which may look out of place.
    // This function prevents that from happening by changing the clip board
    override fun onTextContextMenuItem(id: Int): Boolean {
        var targetId = id
        if (id == android.R.id.paste) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                targetId = android.R.id.pasteAsPlainText
            } else {
                // If device doesn't support paste as plain text
                val manager = context.getSystemService<ClipboardManager>() ?: return super.onTextContextMenuItem(id)
                val clipData = manager.primaryClip ?: return super.onTextContextMenuItem(id)

                for (i in 0 until clipData.itemCount) {
                    val text = clipData.getItemAt(i).coerceToText(context).toString()
                    manager.setPrimaryClip(ClipData.newPlainText("", text))
                }
            }
        }
        val type = when (id) {
            android.R.id.paste, android.R.id.pasteAsPlainText -> OperationType.PASTE
            android.R.id.cut -> OperationType.REPLACE
            else -> return super.onTextContextMenuItem(targetId)
        }
        var handled = false
        editHistory(type) { handled = super.onTextContextMenuItem(targetId) }
        return handled
    }

    /**
     * Executes the given block without notifying any TextWatchers.
     */
    inline fun withoutTextWatchers(block: ExtendedEditText.() -> Unit) {
        val watchers = textWatchers.toList()

        watchers.forEach { removeTextChangedListener(it) }
        block(this)
        watchers.forEach { addTextChangedListener(it) }
    }

    /**
     * Executes the given block with only notifying TextWatchers which are instances of T.
     */
    inline fun <reified T> withOnlyTextWatcher(block: ExtendedEditText.() -> Unit) {
        val watchers = textWatchers
            .filterNot { it is T }
            .toList()

        watchers.forEach { removeTextChangedListener(it) }
        block(this)
        watchers.forEach { addTextChangedListener(it) }
    }

    /**
     * Executes the given block without notifying TextWatchers which are instances of T.
     */
    inline fun <reified T> withoutTextWatcher(block: ExtendedEditText.() -> Unit) {
        val watchers = textWatchers
            .filter { it is T }
            .toList()

        watchers.forEach { removeTextChangedListener(it) }
        block(this)
        watchers.forEach { addTextChangedListener(it) }
    }

    override fun addTextChangedListener(watcher: TextWatcher?) {
        // Crashes if textWatcher is null during instantiation
        if (textWatchers != null) {
            if (watcher != null) textWatchers.add(watcher)
        }
        super.addTextChangedListener(watcher)
    }

    override fun removeTextChangedListener(watcher: TextWatcher?) {
        // Crashes if textWatcher is null during instantiation
        if (textWatchers != null) {
            if (watcher != null) textWatchers.remove(watcher)
        }
        super.removeTextChangedListener(watcher)
    }

    fun requestFocusAndMoveCaret(): Boolean = requestFocus().also { tookFocus ->
        if (tookFocus && text != null) setSelection(length())
    }

    fun enableUndoRedo() {
        if (undoRedoEnabled) return
        undoRedoEnabled = true
        addTextChangedListener(undoRedoTextWatcher)
    }

    fun setOnCanUndoRedoListener(listener: OnCanUndoRedoListener) {
        onUndoRedoListener = listener
    }

    fun canUndo(): Boolean = history.canUndo()

    fun canRedo(): Boolean = history.canRedo()

    fun clearHistory() {
        history.clear()
        notifyHistoryChanged()
    }

    fun editHistory(
        operationType: OperationType = OperationType.TOOLBAR,
        includePrevious: Boolean = false,
        block: ExtendedEditText.() -> Unit,
    ) {
        val previousHint = operationHint
        operationHint = operationType
        history.beginBatch(currentSelection(), includePrevious)
        try {
            block()
        } finally {
            history.endBatch(currentSelection())
            operationHint = previousHint
            notifyHistoryChanged()
        }
    }

    fun undo() {
        if (!canUndo()) return

        val entry = history.undo() ?: return
        if (!applyHistory(entry, undo = true)) history.clear()
        notifyHistoryChanged()
    }

    fun redo() {
        if (!canRedo()) return

        val entry = history.redo() ?: return
        if (!applyHistory(entry, undo = false)) history.clear()
        notifyHistoryChanged()
    }

    private fun applyHistory(entry: HistoryEntry, undo: Boolean): Boolean {
        val editable = text ?: return false
        var valid = true
        withoutTextWatcher<UndoRedoTextWatcher> {
            val changes = if (undo) entry.changes.asReversed() else entry.changes
            for (change in changes) {
                val replacedLength = if (undo) change.insertedText.length else change.deletedText.length
                val replacement = if (undo) change.deletedText else change.insertedText
                val end = change.start + replacedLength
                if (change.start < 0 || end < change.start || end > editable.length) {
                    valid = false
                    break
                }
                editable.replace(change.start, end, replacement)
            }
        }
        if (!valid) return false
        val selection = if (undo) entry.selectionBefore else entry.selectionAfter
        val length = editable.length
        requestFocus()
        setSelection(selection.start.coerceIn(0, length), selection.end.coerceIn(0, length))
        return true
    }

    inner class UndoRedoTextWatcher : TextWatcher {
        private var pendingStart = 0
        private var deletedText = ""
        private var selectionBefore = TextSelection(0, 0)
        private var pendingChange: TextChange? = null

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            pendingStart = start
            deletedText = s.subSequence(start, start + count).toString()
            selectionBefore = currentSelection()
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            val insertedText = s.subSequence(start, start + count).toString()
            if (deletedText.isEmpty() && insertedText.isEmpty()) return
            pendingChange = TextChange(
                start = pendingStart,
                deletedText = deletedText,
                insertedText = insertedText,
                selectionBefore = selectionBefore,
                selectionAfter = currentSelection(),
                timestamp = SystemClock.elapsedRealtime(),
                operationType = operationHint ?: classifyChange(s, pendingStart, deletedText, insertedText, selectionBefore),
            )
        }

        override fun afterTextChanged(s: Editable) {
            val change = pendingChange ?: return
            pendingChange = null
            history.record(change.copy(selectionAfter = currentSelection()))
            notifyHistoryChanged()
        }
    }

    private fun classifyChange(
        text: CharSequence,
        start: Int,
        deleted: String,
        inserted: String,
        selection: TextSelection,
    ): OperationType = when {
        text is Spannable && BaseInputConnection.getComposingSpanStart(text) >= 0 -> OperationType.COMPOSING
        inserted.contains('\n') -> OperationType.NEW_LINE
        selection.start != selection.end || (deleted.isNotEmpty() && inserted.isNotEmpty()) -> OperationType.REPLACE
        deleted.isEmpty() -> if (inserted.length == 1) OperationType.INSERT else OperationType.INSERT_BULK
        inserted.isNotEmpty() -> OperationType.REPLACE
        start < selection.start -> OperationType.BACKSPACE
        else -> OperationType.FORWARD_DELETE
    }

    private fun currentSelection() = TextSelection(
        selectionStart.coerceAtLeast(0),
        selectionEnd.coerceAtLeast(0),
    )

    private fun notifyHistoryChanged() {
        onUndoRedoListener?.listen(canUndo(), canRedo())
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) history.breakMerge()
    }

    fun interface OnCanUndoRedoListener {
        fun listen(canUndo: Boolean, canRedo: Boolean)
    }

    companion object {
        private const val PERF_TAG = "KardLeafQuillpadPerf"
        private const val LARGE_TEXT_LOG_MIN_CHARS = 5_000
        private const val LARGE_TEXT_LOG_LIMIT = 12
        private const val SLOW_LAYOUT_LOG_MS = 16L

    }
}

enum class OperationType {
    INSERT,
    INSERT_BULK,
    BACKSPACE,
    FORWARD_DELETE,
    REPLACE,
    PASTE,
    NEW_LINE,
    TOOLBAR,
    COMPOSING,
}

data class TextSelection(val start: Int, val end: Int)

data class TextChange(
    val start: Int,
    val deletedText: String,
    val insertedText: String,
    val selectionBefore: TextSelection,
    val selectionAfter: TextSelection,
    val timestamp: Long,
    val operationType: OperationType,
)

internal data class HistoryEntry(val changes: List<TextChange>) {
    val selectionBefore get() = changes.first().selectionBefore
    val selectionAfter get() = changes.last().selectionAfter
    val chars get() = changes.sumOf { it.deletedText.length + it.insertedText.length }
}

internal class EditHistory(
    private val maxEntries: Int = 200,
    private val maxOperationChars: Int = 200_000,
    private val maxTotalChars: Int = 4_000_000,
    private val mergeWindowMs: Long = 400L,
) {
    private val undo = ArrayDeque<HistoryEntry>()
    private val redo = ArrayDeque<HistoryEntry>()
    private var undoChars = 0
    private var redoChars = 0
    private var mergeBlocked = false
    private var batchDepth = 0
    private var batchSelectionBefore = TextSelection(0, 0)
    private val batchChanges = mutableListOf<TextChange>()

    val undoSize get() = undo.size
    val redoSize get() = redo.size
    val historyChars get() = undoChars + redoChars

    fun canUndo() = undo.isNotEmpty() || batchChanges.isNotEmpty()
    fun canRedo() = redo.isNotEmpty()

    fun clear() {
        undo.clear()
        redo.clear()
        undoChars = 0
        redoChars = 0
        batchDepth = 0
        batchChanges.clear()
        mergeBlocked = true
    }

    fun breakMerge() {
        mergeBlocked = true
    }

    fun beginBatch(selectionBefore: TextSelection, includePrevious: Boolean) {
        if (batchDepth++ > 0) return
        batchSelectionBefore = selectionBefore
        batchChanges.clear()
        if (includePrevious && undo.isNotEmpty()) {
            val previous = undo.removeLast()
            undoChars -= previous.chars
            batchSelectionBefore = previous.selectionBefore
            batchChanges.addAll(previous.changes)
        }
    }

    fun endBatch(selectionAfter: TextSelection) {
        if (batchDepth == 0 || --batchDepth > 0) return
        if (batchChanges.isEmpty()) return
        val changes = batchChanges.toMutableList()
        changes[0] = changes.first().copy(selectionBefore = batchSelectionBefore)
        changes[changes.lastIndex] = changes.last().copy(selectionAfter = selectionAfter)
        batchChanges.clear()
        push(HistoryEntry(changes), merge = false)
    }

    fun record(change: TextChange) {
        if (batchDepth > 0) {
            batchChanges.add(change)
            return
        }
        push(HistoryEntry(listOf(change)), merge = true)
    }

    fun undo(): HistoryEntry? {
        if (batchDepth > 0) return null
        val entry = undo.pollLast() ?: return null
        undoChars -= entry.chars
        redo.addLast(entry)
        redoChars += entry.chars
        mergeBlocked = true
        return entry
    }

    fun redo(): HistoryEntry? {
        if (batchDepth > 0) return null
        val entry = redo.pollLast() ?: return null
        redoChars -= entry.chars
        undo.addLast(entry)
        undoChars += entry.chars
        mergeBlocked = true
        return entry
    }

    private fun push(entry: HistoryEntry, merge: Boolean) {
        redo.clear()
        redoChars = 0
        if (entry.chars > maxOperationChars) {
            clear()
            return
        }
        val previous = undo.peekLast()
        val merged = if (merge && !mergeBlocked && previous != null) merge(previous, entry) else null
        if (merged != null) {
            undo.removeLast()
            undoChars -= previous.chars
            undo.addLast(merged)
            undoChars += merged.chars
        } else {
            undo.addLast(entry)
            undoChars += entry.chars
        }
        mergeBlocked = false
        while (undo.size > maxEntries || undoChars > maxTotalChars) {
            undoChars -= undo.removeFirst().chars
        }
    }

    private fun merge(a: HistoryEntry, b: HistoryEntry): HistoryEntry? {
        if (a.changes.size != 1 || b.changes.size != 1) return null
        val first = a.changes.single()
        val second = b.changes.single()
        if (second.timestamp - first.timestamp !in 0..mergeWindowMs) return null
        if (first.selectionAfter != second.selectionBefore) return null

        val merged = when {
            first.operationType == OperationType.INSERT && second.operationType == OperationType.INSERT &&
                first.deletedText.isEmpty() && second.deletedText.isEmpty() &&
                second.insertedText.length == 1 &&
                first.start + first.insertedText.length == second.start ->
                first.copy(
                    insertedText = first.insertedText + second.insertedText,
                    selectionAfter = second.selectionAfter,
                    timestamp = second.timestamp,
                )
            first.operationType == OperationType.BACKSPACE && second.operationType == OperationType.BACKSPACE &&
                first.insertedText.isEmpty() && second.insertedText.isEmpty() &&
                second.start + second.deletedText.length == first.start ->
                first.copy(
                    start = second.start,
                    deletedText = second.deletedText + first.deletedText,
                    selectionAfter = second.selectionAfter,
                    timestamp = second.timestamp,
                )
            first.operationType == OperationType.FORWARD_DELETE && second.operationType == OperationType.FORWARD_DELETE &&
                first.insertedText.isEmpty() && second.insertedText.isEmpty() && first.start == second.start ->
                first.copy(
                    deletedText = first.deletedText + second.deletedText,
                    selectionAfter = second.selectionAfter,
                    timestamp = second.timestamp,
                )
            first.operationType == OperationType.COMPOSING &&
                first.start == second.start && second.deletedText == first.insertedText ->
                first.copy(
                    insertedText = second.insertedText,
                    selectionAfter = second.selectionAfter,
                    timestamp = second.timestamp,
                )
            first.operationType == OperationType.COMPOSING && second.operationType == OperationType.COMPOSING &&
                second.deletedText.isEmpty() && first.start + first.insertedText.length == second.start ->
                first.copy(
                    insertedText = first.insertedText + second.insertedText,
                    selectionAfter = second.selectionAfter,
                    timestamp = second.timestamp,
                )
            else -> null
        }
        return merged?.let { HistoryEntry(listOf(it)) }
    }
}
