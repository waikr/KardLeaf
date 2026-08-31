package com.kangle.kardleaf.ui.editor.native

import com.kangle.kardleaf.data.utils.EditorOpenSession
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.ImageReplacementHistorySource
import com.kangle.kardleaf.ui.selectImageReplacementHistorySource
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import com.kangle.kardleaf.data.utils.KardLeafPerfLog
import com.kangle.kardleaf.ui.KardLeafImageClickTarget
import com.kangle.kardleaf.ui.editor.api.EditorFastScrollMetrics
import com.kangle.kardleaf.ui.editor.api.KardLeafEditorKernelView
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.noties.markwon.editor.handler.EmphasisEditHandler
import io.noties.markwon.editor.handler.StrongEmphasisEditHandler
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EDITOR_TRACE_TAG = "KardLeafEditorTrace"
private const val EDITOR_UNDO_TAG = "KardLeafEditorUndo"
private val USER_PERF_TRACE_TAG = KardLeafLogTags.USER_PERF
private const val OPEN_PATH_PROBE_TAG = "KardLeafOpenPathProbe"
private const val NATIVE_IME_TAG = "KardLeafNativeIme"
private const val NATIVE_EDITOR_LAYOUT_TAG = "KardLeafEditorLayout"
private const val CURSOR_REVEAL_CHECK_DELAY_MS = 160L
private const val CURSOR_REVEAL_FINAL_DELAY_MS = 360L
private const val RECENT_MAIN_REVEAL_WINDOW_MS = 500L

private fun nativeEditorMemorySummary(): String {
    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    val totalMb = runtime.totalMemory() / 1024 / 1024
    val maxMb = runtime.maxMemory() / 1024 / 1024
    return "mem=${usedMb}MB/${totalMb}MB max=${maxMb}MB"
}

/** A lightweight snapshot read from the native editor only on demand. */
data class KardLeafEditorSnapshot(
    val title: String,
    val content: String,
    val selection: TextRange = TextRange(0, 0),
)

/**
 * Compose-facing controller for the native editor island.
 *
 * The full title/body are intentionally read only from explicit calls such as
 * save, preview, search and outline refresh. Typing remains inside EditText.
 */
class KardLeafEditorController {
    private data class PendingExternalRangeReplacement(
        val start: Int,
        val deleteCount: Int,
        val insertedText: String,
    )

    private data class DetachedContentEdit(
        val start: Int,
        val deletedText: String,
        val insertedText: String,
        val selectionBefore: TextRange,
        var selectionAfter: TextRange,
        val beforeLength: Int,
        val beforeHash: Int,
        val afterLength: Int,
        val afterHash: Int,
    )

    internal var editorView: KardLeafEditorKernelView? = null
        private set

    private var documentKey: String? = null
    private var lastLoadedTitle: String = ""
    private var lastLoadedContent: String = ""
    private var cachedTitle: String = ""
    private var cachedContent: String = ""
    private var cachedSelection: TextRange = TextRange(0, 0)
    private var externalContentUpdater: ((String, TextRange) -> Unit)? = null
    private var externalRangeReplacer: ((Int, Int, String, TextRange) -> Boolean)? = null
    private var externalSelectionUpdater: ((TextRange) -> Unit)? = null
    private var externalSnapshotRequester: (((KardLeafEditorSnapshot) -> Unit) -> Unit)? = null
    private var externalUndoAction: (() -> Unit)? = null
    private var externalRedoAction: (() -> Unit)? = null
    private var externalCommandExecutor: ((String, List<Any>) -> Boolean)? = null
    private var externalCanUndo: Boolean = false
    private var externalCanRedo: Boolean = false
    private val detachedUndo = ArrayDeque<DetachedContentEdit>()
    private val detachedRedo = ArrayDeque<DetachedContentEdit>()
    private var pendingExternalRangeReplacement: PendingExternalRangeReplacement? = null
    private var editorUndoOperationId = 0L

    fun acceptInitialSnapshot(
        documentKey: String,
        initialTitle: String,
        initialContent: String,
        initialSelection: TextRange? = null,
    ) {
        val isDifferentDocument = this.documentKey != documentKey
        if (editorView != null && !isDifferentDocument) return

        if (isDifferentDocument) {
            detachedUndo.clear()
            detachedRedo.clear()
        }

        val isStillAtLoadedText = cachedTitle == lastLoadedTitle && cachedContent == lastLoadedContent
        if (isDifferentDocument || isStillAtLoadedText) {
            this.documentKey = documentKey
            lastLoadedTitle = initialTitle
            lastLoadedContent = initialContent
            cachedTitle = initialTitle
            cachedContent = initialContent
            cachedSelection = when {
                isDifferentDocument -> initialSelection ?: TextRange(initialContent.length, initialContent.length)
                initialSelection != null -> initialSelection
                else -> TextRange(
                    cachedSelection.start.coerceIn(0, initialContent.length),
                    cachedSelection.end.coerceIn(0, initialContent.length),
                )
            }
            if (isDifferentDocument) {
                KardLeafLog.d(
                    EDITOR_TRACE_TAG,
                    "controller accept new document key=$documentKey titleLen=${initialTitle.length} contentLen=${initialContent.length}",
                )
            }
        }
    }

    internal fun attach(
        view: KardLeafEditorKernelView,
        documentKey: String,
        loadedTitle: String,
        loadedContent: String,
    ) {
        editorView = view
        this.documentKey = documentKey
        lastLoadedTitle = loadedTitle
        lastLoadedContent = loadedContent
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "controller attach key=$documentKey loadedTitleLen=${loadedTitle.length} loadedContentLen=${loadedContent.length} " +
                "viewTitleLen=${view.getTitleString().length} viewContentLen=${view.contentLength()}",
        )
    }

    internal fun detach(view: KardLeafEditorKernelView) {
        if (editorView === view) {
            if (isCurrentAttachedView(view)) {
                captureFromView(view)
                KardLeafLog.d(
                    EDITOR_TRACE_TAG,
                    "controller detach key=$documentKey cachedTitleLen=${cachedTitle.length} cachedContentLen=${cachedContent.length} selection=$cachedSelection",
                )
            } else {
                KardLeafLog.w(
                    EDITOR_TRACE_TAG,
                    "controller detach ignored stale view currentKey=$documentKey viewKey=${view.boundDocumentKey}",
                )
            }
            editorView = null
        }
        view.dispose()
    }

    fun releaseForClose(clearText: Boolean = false) {
        editorView?.let { view ->
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "controller releaseForClose key=$documentKey viewKey=${view.boundDocumentKey} titleLen=${view.getTitleString().length} contentLen=${view.contentLength()}",
            )
            editorView = null
            view.dispose(clearText = clearText)
        }
        documentKey = null
        lastLoadedTitle = ""
        lastLoadedContent = ""
        cachedTitle = ""
        cachedContent = ""
        cachedSelection = TextRange(0, 0)
        externalContentUpdater = null
        externalRangeReplacer = null
        externalSelectionUpdater = null
        externalSnapshotRequester = null
        externalUndoAction = null
        externalRedoAction = null
        externalCommandExecutor = null
        externalCanUndo = false
        externalCanRedo = false
        detachedUndo.clear()
        detachedRedo.clear()
        pendingExternalRangeReplacement = null
    }

    private fun captureFromView(view: KardLeafEditorKernelView) {
        cachedTitle = view.getTitleString()
        cachedContent = view.getContentString()
        cachedSelection = view.getContentSelection()
    }

    private fun isCurrentAttachedView(view: KardLeafEditorKernelView): Boolean =
        view.boundDocumentKey == documentKey

    private fun currentEditorView(): KardLeafEditorKernelView? =
        editorView?.takeIf { isCurrentAttachedView(it) }

    internal fun updateCachedSelection(
        start: Int,
        end: Int,
    ) {
        val len = currentEditorView()?.contentLength() ?: cachedContent.length
        cachedSelection = TextRange(start.coerceIn(0, len), end.coerceIn(0, len))
    }

    internal fun getCachedSnapshot(): KardLeafEditorSnapshot = KardLeafEditorSnapshot(
        title = cachedTitle,
        content = cachedContent,
        selection = cachedSelection,
    )

    internal fun setExternalContentUpdater(updater: ((String, TextRange) -> Unit)?) {
        externalContentUpdater = updater
    }

    internal fun setExternalRangeReplacer(replacer: ((Int, Int, String, TextRange) -> Boolean)?) {
        externalRangeReplacer = replacer
    }

    internal fun setExternalSelectionUpdater(updater: ((TextRange) -> Unit)?) {
        externalSelectionUpdater = updater
    }

    internal fun setExternalSnapshotRequester(requester: (((KardLeafEditorSnapshot) -> Unit) -> Unit)?) {
        externalSnapshotRequester = requester
    }

    internal fun setExternalUndoRedoActions(
        undoAction: (() -> Unit)?,
        redoAction: (() -> Unit)?,
    ) {
        externalUndoAction = undoAction
        externalRedoAction = redoAction
        if (undoAction == null && redoAction == null) {
            externalCanUndo = false
            externalCanRedo = false
        }
    }

    internal fun updateExternalUndoRedoState(
        canUndo: Boolean,
        canRedo: Boolean,
    ) {
        externalCanUndo = canUndo
        externalCanRedo = canRedo
    }

    internal fun setExternalCommandExecutor(executor: ((String, List<Any>) -> Boolean)?) {
        externalCommandExecutor = executor
    }

    fun executeCommand(command: String, vararg args: Any): Boolean =
        currentEditorView()?.executeCommand(command, args.toList())
            ?: (externalCommandExecutor?.invoke(command, args.toList()) == true)

    fun requestExternalSnapshot(onSnapshot: (KardLeafEditorSnapshot) -> Unit): Boolean {
        val requester = externalSnapshotRequester ?: return false
        requester(onSnapshot)
        return true
    }

    fun updateExternalContentSnapshot(
        content: String,
        selection: TextRange = cachedSelection,
    ) {
        cachedContent = content
        val len = cachedContent.length
        cachedSelection = TextRange(selection.start.coerceIn(0, len), selection.end.coerceIn(0, len))
    }

    fun updateExternalTitle(title: String) {
        cachedTitle = title
    }

    fun updateExternalSelection(start: Int, end: Int) {
        val len = cachedContent.length
        cachedSelection = TextRange(start.coerceIn(0, len), end.coerceIn(0, len))
    }

    fun applyExternalContentPatch(
        start: Int,
        deleteCount: Int,
        insertedText: String,
        selection: TextRange,
    ) {
        val pending = pendingExternalRangeReplacement
        if (pending != null && pending.start == start && pending.deleteCount == deleteCount && pending.insertedText == insertedText) {
            pendingExternalRangeReplacement = null
            cachedSelection = TextRange(selection.start.coerceIn(0, cachedContent.length), selection.end.coerceIn(0, cachedContent.length))
            logUndoState("replace", "CodeMirror", "external-range-patch", start, deleteCount, insertedText.length)
            return
        }
        val safeStart = start.coerceIn(0, cachedContent.length)
        val safeDeleteEnd = (safeStart + deleteCount).coerceIn(safeStart, cachedContent.length)
        cachedContent = cachedContent.substring(0, safeStart) + insertedText + cachedContent.substring(safeDeleteEnd)
        val len = cachedContent.length
        cachedSelection = TextRange(selection.start.coerceIn(0, len), selection.end.coerceIn(0, len))
    }

    private fun notifyExternalContentUpdater() {
        externalContentUpdater?.invoke(cachedContent, cachedSelection)
    }

    fun getSnapshot(): KardLeafEditorSnapshot {
        currentEditorView()?.let { captureFromView(it) }
        return KardLeafEditorSnapshot(
            title = cachedTitle,
            content = cachedContent,
            selection = cachedSelection,
        )
    }

    fun getTitle(): String = getSnapshot().title

    fun getText(): String = getSnapshot().content

    fun getContentLength(): Int = currentEditorView()?.contentLength() ?: cachedContent.length

    fun getSelection(): TextRange {
        currentEditorView()?.let {
            cachedSelection = it.getContentSelection()
        }
        return cachedSelection
    }

    fun shouldReserveContentTouchForEditing(
        windowX: Float,
        windowY: Float,
        radiusPx: Float,
    ): Boolean = currentEditorView()?.shouldReserveContentTouchForEditing(windowX, windowY, radiusPx) ?: false

    fun getFastScrollMetrics(): EditorFastScrollMetrics =
        currentEditorView()?.getFastScrollMetrics() ?: EditorFastScrollMetrics()

    fun fastScrollToRatio(ratio: Float) {
        currentEditorView()?.fastScrollToRatio(ratio)
    }

    fun insertAtCursor(
        prefix: String,
        suffix: String = "",
    ) {
        val attached = currentEditorView()
        if (attached != null) {
            attached.insertAtContentCursor(prefix, suffix)
            cachedSelection = attached.getContentSelection()
        } else {
            val start = cachedSelection.start.coerceIn(0, cachedContent.length)
            val end = cachedSelection.end.coerceIn(0, cachedContent.length)
            val selectedText = cachedContent.substring(start, end)
            val insertion = prefix + selectedText + suffix
            cachedContent = cachedContent.substring(0, start) + insertion + cachedContent.substring(end)
            val cursor = start + prefix.length + selectedText.length
            cachedSelection = TextRange(cursor, cursor)
            notifyExternalContentUpdater()
        }
    }

    fun replaceSelection(insertion: String) {
        val attached = currentEditorView()
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "controller replaceSelection before key=$documentKey attached=${attached != null} " +
                "cachedLen=${cachedContent.length} cachedSelection=${cachedSelection.start}..${cachedSelection.end} insertionLen=${insertion.length}",
        )
        if (attached != null) {
            attached.replaceContentSelection(insertion)
            cachedSelection = attached.getContentSelection()
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "controller replaceSelection after attached key=$documentKey viewLen=${attached.contentLength()} " +
                    "cachedSelection=${cachedSelection.start}..${cachedSelection.end}",
            )
        } else {
            val start = minOf(cachedSelection.start, cachedSelection.end).coerceIn(0, cachedContent.length)
            val end = maxOf(cachedSelection.start, cachedSelection.end).coerceIn(0, cachedContent.length)
            val beforeContent = cachedContent
            val beforeSelection = cachedSelection
            val deletedText = beforeContent.substring(start, end)
            val replacementSelection = TextRange(start + insertion.length, start + insertion.length)
            if (deletedText != insertion && externalRangeReplacer?.invoke(start, end, insertion, replacementSelection) == true) {
                cachedContent = beforeContent.substring(0, start) + insertion + beforeContent.substring(end)
                cachedSelection = replacementSelection
                pendingExternalRangeReplacement = PendingExternalRangeReplacement(start, end - start, insertion)
                logUndoState("replace", "CodeMirror", "external-range", start, end - start, insertion.length)
                return
            }
            cachedContent = cachedContent.substring(0, start) + insertion + cachedContent.substring(end)
            val cursor = start + insertion.length
            cachedSelection = TextRange(cursor, cursor)
            if (deletedText != insertion) {
                detachedUndo.addLast(
                    DetachedContentEdit(
                        start = start,
                        deletedText = deletedText,
                        insertedText = insertion,
                        selectionBefore = beforeSelection,
                        selectionAfter = cachedSelection,
                        beforeLength = beforeContent.length,
                        beforeHash = beforeContent.hashCode(),
                        afterLength = cachedContent.length,
                        afterHash = cachedContent.hashCode(),
                    ),
                )
                while (detachedUndo.size > MAX_DETACHED_HISTORY_SIZE) detachedUndo.removeFirst()
                detachedRedo.clear()
            }
            notifyExternalContentUpdater()
            logUndoState("replace", "Detached", "detached", start, end - start, insertion.length)
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "controller replaceSelection after cached key=$documentKey newCachedLen=${cachedContent.length} " +
                    "cachedSelection=${cachedSelection.start}..${cachedSelection.end} replace=$start..$end",
            )
        }
    }

    fun replaceAll(
        newText: String,
        selection: TextRange? = null,
    ) {
        val attached = currentEditorView()
        KardLeafLog.d(
            EDITOR_UNDO_TAG,
            "controller replaceAll path=${if (attached != null) "attached" else "external"} " +
                "newTextLen=${newText.length} selection=${selection?.start ?: -1}..${selection?.end ?: -1}",
        )
        if (attached != null) {
            attached.replaceContent(newText, selection)
            cachedSelection = attached.getContentSelection()
        } else {
            cachedContent = newText
            cachedSelection = selection ?: TextRange(newText.length, newText.length)
            KardLeafLog.d(
                EDITOR_UNDO_TAG,
                "controller replaceAll external fullDocumentUpdate controllerHistory=none",
            )
            notifyExternalContentUpdater()
        }
    }

    fun setSelection(
        start: Int,
        end: Int = start,
    ) {
        val attached = currentEditorView()
        if (attached != null) {
            attached.setContentSelection(start, end)
            cachedSelection = attached.getContentSelection()
        } else {
            val len = cachedContent.length
            cachedSelection = TextRange(start.coerceIn(0, len), end.coerceIn(0, len))
            val lastDetachedEdit = detachedUndo.peekLast()
            if (lastDetachedEdit != null && cachedContent.matches(lastDetachedEdit.afterLength, lastDetachedEdit.afterHash)) {
                lastDetachedEdit.selectionAfter = cachedSelection
            }
            externalSelectionUpdater?.invoke(cachedSelection) ?: notifyExternalContentUpdater()
        }
    }

    fun focus() {
        currentEditorView()?.focusContent()
    }

    fun scrollToOffset(offset: Int) {
        currentEditorView()?.scrollContentOffsetToVisible(offset)
    }

    fun scrollToProgress(progress: Float) {
        currentEditorView()?.scrollToProgress(progress)
    }

    fun highlightSearch(
        query: String,
        currentStart: Int = -1,
        useRegex: Boolean = false,
        matchCase: Boolean = false,
    ): Int = currentEditorView()?.highlightContentSearch(query, currentStart, useRegex, matchCase) ?: 0

    fun clearSearchHighlights() {
        currentEditorView()?.clearContentSearchHighlights()
    }

    fun undo() {
        val attached = currentEditorView()
        val selected = selectImageReplacementHistorySource(
            attachedAvailable = attached?.canUndoContent() == true,
            externalAvailable = externalUndoAction != null && externalCanUndo,
            detachedMatchesCurrent = detachedUndo.peekLast()?.let { currentContentMatches(it.afterLength, it.afterHash) } == true,
        )
        when (selected) {
            ImageReplacementHistorySource.Native -> attached?.undoContent()
            ImageReplacementHistorySource.Detached -> undoDetachedEdit()
            ImageReplacementHistorySource.External -> externalUndoAction?.invoke()
            ImageReplacementHistorySource.None -> Unit
        }
        logUndoState("undo", selected.name, selected.name.lowercase(), -1, 0, 0)
    }

    fun redo() {
        val attached = currentEditorView()
        val selected = selectImageReplacementHistorySource(
            attachedAvailable = attached?.canRedoContent() == true,
            externalAvailable = externalRedoAction != null && externalCanRedo,
            detachedMatchesCurrent = detachedRedo.peekLast()?.let { currentContentMatches(it.beforeLength, it.beforeHash) } == true,
        )
        when (selected) {
            ImageReplacementHistorySource.Native -> attached?.redoContent()
            ImageReplacementHistorySource.Detached -> redoDetachedEdit()
            ImageReplacementHistorySource.External -> externalRedoAction?.invoke()
            ImageReplacementHistorySource.None -> Unit
        }
        logUndoState("redo", selected.name, selected.name.lowercase(), -1, 0, 0)
    }

    fun canUndo(): Boolean =
        currentEditorView()?.canUndoContent() == true ||
            (externalUndoAction != null && externalCanUndo) ||
            detachedUndo.peekLast()?.let { currentContentMatches(it.afterLength, it.afterHash) } == true

    fun canRedo(): Boolean =
        currentEditorView()?.canRedoContent() == true ||
            (externalRedoAction != null && externalCanRedo) ||
            detachedRedo.peekLast()?.let { currentContentMatches(it.beforeLength, it.beforeHash) } == true

    fun clearHistory() {
        currentEditorView()?.clearContentHistory()
        detachedUndo.clear()
        detachedRedo.clear()
    }

    fun refreshInlineImagePreviews() {
        currentEditorView()?.refreshContentInlineImagePreviews()
    }

    private fun undoDetachedEdit() {
        val edit = detachedUndo.peekLast() ?: return
        if (!currentContentMatches(edit.afterLength, edit.afterHash)) return
        detachedUndo.removeLast()
        applyDetachedEdit(edit, undo = true)
        detachedRedo.addLast(edit)
        val replaceEnd = edit.start + edit.insertedText.length
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "controller detached undo key=$documentKey replace=${edit.start}..$replaceEnd",
        )
    }

    private fun redoDetachedEdit() {
        val edit = detachedRedo.peekLast() ?: return
        if (!currentContentMatches(edit.beforeLength, edit.beforeHash)) return
        detachedRedo.removeLast()
        applyDetachedEdit(edit, undo = false)
        detachedUndo.addLast(edit)
        val replaceEnd = edit.start + edit.deletedText.length
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "controller detached redo key=$documentKey replace=${edit.start}..$replaceEnd",
        )
    }

    private fun applyDetachedEdit(
        edit: DetachedContentEdit,
        undo: Boolean,
    ) {
        val current = currentEditorView()?.getContentString() ?: cachedContent
        val replacedLength = if (undo) edit.insertedText.length else edit.deletedText.length
        val replacement = if (undo) edit.deletedText else edit.insertedText
        val replaceEnd = (edit.start + replacedLength).coerceIn(edit.start, current.length)
        val nextContent = current.substring(0, edit.start) + replacement + current.substring(replaceEnd)
        val nextSelection =
            if (undo) {
                edit.selectionBefore
            } else {
                edit.selectionAfter
            }
        val attached = currentEditorView()
        if (attached != null) {
            attached.replaceContent(nextContent, nextSelection)
            attached.clearContentHistory()
            captureFromView(attached)
        } else {
            cachedContent = nextContent
            cachedSelection =
                TextRange(
                    nextSelection.start.coerceIn(0, nextContent.length),
                    nextSelection.end.coerceIn(0, nextContent.length),
                )
            externalCanUndo = false
            externalCanRedo = false
            notifyExternalContentUpdater()
        }
    }

    private fun logUndoState(
        action: String,
        kernel: String,
        selectedHistorySource: String,
        rangeStart: Int,
        rangeDeleteCount: Int,
        insertLength: Int,
    ) {
        editorUndoOperationId++
        KardLeafLog.d(
            EDITOR_UNDO_TAG,
            "operationId=$editorUndoOperationId action=$action kernel=$kernel selectedHistorySource=$selectedHistorySource " +
                "attachedCanUndo=${currentEditorView()?.canUndoContent() == true} externalCanUndo=$externalCanUndo " +
                "detachedUndoCount=${detachedUndo.size} detachedRedoCount=${detachedRedo.size} contentLen=${cachedContent.length} " +
                "contentHash=${cachedContent.hashCode()} range=$rangeStart deleteCount=$rangeDeleteCount insertLen=$insertLength " +
                "controllerCanUndo=${canUndo()} controllerCanRedo=${canRedo()}",
        )
    }

    private fun currentContentMatches(
        expectedLength: Int,
        expectedHash: Int,
    ): Boolean {
        val content = currentEditorView()?.getContentString() ?: cachedContent
        return content.matches(expectedLength, expectedHash)
    }

    private fun String.matches(
        expectedLength: Int,
        expectedHash: Int,
    ): Boolean =
        length == expectedLength && hashCode() == expectedHash

    private companion object {
        const val MAX_DETACHED_HISTORY_SIZE = 32
    }
}

class KardLeafNativeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs), KardLeafEditorKernelView {
    val scrollView: NestedScrollView
    val titleEditText: EditText
    val contentEditText: EditorEditText

    private val editorColumn: LinearLayout
    private val markdownExecutor = Executors.newSingleThreadExecutor()
    private val markdownWatcher: TextWatcher
    private val programmaticTitleChange = AtomicBoolean(false)
    private var isDisposed = false

    private var titleChangedCallback: (() -> Unit)? = null
    private var userInteractionCallback: (() -> Unit)? = null
    private var scrollChangedCallback: (() -> Unit)? = null

    override var boundDocumentKey: String? = null
        private set
    private var loadedTitle: String = ""
    private var loadedContent: String = ""
    private var userPerfOpenStartRealtimeMs: Long? = null
    private var userPerfSizeTier: String = "unknown"
    private var userPerfFirstNativeTextLaidOutLogged = false
    private var openSession: EditorOpenSession? = null
    private var frameCommittedCallback: ((Long) -> Unit)? = null
    private var nativeTextGeneration = 0L
    private var pendingNativePreDrawListener: ViewTreeObserver.OnPreDrawListener? = null
    private val userPerfScrollHandler = Handler(Looper.getMainLooper())
    private var userPerfScrollStartMs = 0L
    private var userPerfScrollLastMs = 0L
    private var userPerfScrollFrames = 0
    private var userPerfScrollSlowFrames = 0
    private var userPerfScrollMaxFrameMs = 0L
    private var userPerfScrollStartY = 0
    private val userPerfScrollSettleRunnable = Runnable { logUserPerfScrollSettled() }
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchMovedForScroll = false
    private var scrollingCursorHidden = false
    private var lastReadOnly = false
    private val editorColumnBaseBottomPaddingPx = dp(24)
    private val cursorSafeBottomMarginPx = dp(72)
    private val preImeCursorSafeBottomMarginPx = dp(280)
    private val cursorSafeTopMarginPx = dp(24)
    private val cursorDockToolbarHeightPx = dp(48)
    private val cursorDockMarginPx = dp(8)
    private var lastNativeImeInsetBottomPx = 0
    private var lastNavigationBarInsetBottomPx = 0
    private var lastVisibleFrameKeyboardHeightPx = 0
    private var lastEffectiveKeyboardHeightPx = 0
    private var lastKnownKeyboardHeightPx = 0
    private var ensureCursorVisibleGeneration = 0
    private var contentEditCursorCheckGeneration = 0
    private var lastMainRevealGeneration = 0
    private var lastMainRevealAtMs = 0L
    private var mainRevealScheduledGeneration = 0
    private var pendingCursorReveal = false
    private var pendingMainReveal = false
    private var revealViewHeightAtTouch = 0
    private var revealStartedAtMs = 0L
    private var revealStartedWithKeyboardOpen = false
    private var stableRevealTargetGeneration = 0
    private var stableRevealTargetCursorBottom = 0
    private var correctionRevealGeneration = 0
    private var postMainRevealProbeGeneration = 0
    private var cursorRevealExtraBottomPaddingPx = 0
    private var visibleFrameKeyboardListenerAttached = false
    private var nativeLayoutLogCount = 0
    private val visibleFrameKeyboardListener = ViewTreeObserver.OnGlobalLayoutListener {
        refreshVisibleFrameKeyboardHeight("visible-frame")
        tryScheduleMainReveal("layout-compressed-mainReveal")
    }

    private val titleWatcher = object : TextWatcher {
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
            if (!programmaticTitleChange.get()) {
                titleChangedCallback?.invoke()
            }
        }
    }

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        clipToPadding = false

        scrollView = NestedScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        editorColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipToPadding = false
            setPadding(dp(16), dp(6), dp(16), editorColumnBaseBottomPaddingPx)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        titleEditText = EditText(context).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setPadding(0, 0, 0, dp(8))
            minLines = 1
            maxLines = 1
            setSingleLine(true)
            includeFontPadding = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addTextChangedListener(titleWatcher)
        }

        contentEditText = EditorEditText(context).apply {
            gravity = Gravity.TOP or Gravity.START
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            minHeight = 0
            minLines = 12
            includeFontPadding = true
            isVerticalScrollBarEnabled = false
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(false)
            setHorizontallyScrolling(false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val markdownInitStartMs = SystemClock.elapsedRealtime()
        val markwon = Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .build()
        val markwonEditor = MarkwonEditor.builder(markwon)
            .useEditHandler(EmphasisEditHandler())
            .useEditHandler(StrongEmphasisEditHandler())
            .useEditHandler(KardLeafCodeHandler())
            .useEditHandler(KardLeafCodeBlockHandler())
            .useEditHandler(KardLeafBlockQuoteHandler())
            .useEditHandler(KardLeafStrikethroughHandler())
            .useEditHandler(KardLeafHeadingHandler())
            .build()
        markdownWatcher = MarkwonEditorTextWatcher.withPreRender(
            markwonEditor,
            markdownExecutor,
            contentEditText,
        )
        contentEditText.configureMarkdownWatcher(markdownWatcher)
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "native markdown watcher init done elapsed=${SystemClock.elapsedRealtime() - markdownInitStartMs}ms",
        )

        titleEditText.setOnTouchListener { _, event -> notifyUserInteractionOnTouch(event) }
        contentEditText.setOnTouchListener { _, event ->
            val isTapUp = event.actionMasked == MotionEvent.ACTION_UP && !touchMovedForScroll
            notifyUserInteractionOnTouch(event)
            if (isTapUp) {
                requestContentCursorRevealSequence("content-touch-up")
            }
            false
        }
        scrollView.setOnTouchListener { _, event -> notifyUserInteractionOnTouch(event) }
        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            recordUserPerfScrollFrame()
            scrollChangedCallback?.invoke()
        }

        editorColumn.addView(titleEditText)
        editorColumn.addView(contentEditText)
        scrollView.addView(editorColumn)
        addView(scrollView)

        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            lastNavigationBarInsetBottomPx = navBottom
            updateNativeImeInsetBottom(
                insetBottom = if (imeVisible) (imeBottom - navBottom).coerceAtLeast(0) else 0,
                reason = "window-insets",
            )
            insets
        }
        post { ViewCompat.requestApplyInsets(this) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachVisibleFrameKeyboardListener()
        ViewCompat.requestApplyInsets(this)
        refreshVisibleFrameKeyboardHeight("attached")
    }

    override fun onDetachedFromWindow() {
        detachVisibleFrameKeyboardListener()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        logNativeEditorLayout("native-sizeChanged", "old=${oldw}x$oldh new=${w}x$h")
        tryScheduleMainReveal("layout-compressed-mainReveal")
    }

    private fun notifyUserInteractionOnTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.rawX
                touchStartY = event.rawY
                touchMovedForScroll = false
                restoreCursorAfterUserScroll()
                userInteractionCallback?.invoke()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.rawX - touchStartX)
                val dy = abs(event.rawY - touchStartY)
                if (!touchMovedForScroll && dy > touchSlopPx && dy > dx) {
                    touchMovedForScroll = true
                    hideCursorDuringUserScroll()
                }
                userInteractionCallback?.invoke()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touchMovedForScroll = false
                restoreCursorAfterUserScroll()
            }
        }
        return false
    }

    private fun hideCursorDuringUserScroll() {
        if (scrollingCursorHidden) return
        scrollingCursorHidden = true
        titleEditText.isCursorVisible = false
        contentEditText.isCursorVisible = false
    }

    private fun restoreCursorAfterUserScroll() {
        if (!scrollingCursorHidden) return
        scrollingCursorHidden = false
        val cursorVisible = !lastReadOnly
        titleEditText.isCursorVisible = cursorVisible
        contentEditText.isCursorVisible = cursorVisible
    }


    private fun attachVisibleFrameKeyboardListener() {
        if (visibleFrameKeyboardListenerAttached || !viewTreeObserver.isAlive) return
        viewTreeObserver.addOnGlobalLayoutListener(visibleFrameKeyboardListener)
        visibleFrameKeyboardListenerAttached = true
    }

    private fun detachVisibleFrameKeyboardListener() {
        if (!visibleFrameKeyboardListenerAttached) return
        if (viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnGlobalLayoutListener(visibleFrameKeyboardListener)
        }
        visibleFrameKeyboardListenerAttached = false
    }

    private fun updateNativeImeInsetBottom(insetBottom: Int, reason: String) {
        val safeInset = insetBottom.coerceAtLeast(0)
        val changed = lastNativeImeInsetBottomPx != safeInset
        lastNativeImeInsetBottomPx = safeInset
        refreshVisibleFrameKeyboardHeight(reason, forceLog = changed)
    }

    private fun refreshVisibleFrameKeyboardHeight(
        reason: String,
        visibleFrameOut: Rect? = null,
        forceLog: Boolean = false,
    ): Int {
        val visibleFrameKeyboardHeight = visibleFrameKeyboardHeightPx(visibleFrameOut)
        val changed = lastVisibleFrameKeyboardHeightPx != visibleFrameKeyboardHeight
        lastVisibleFrameKeyboardHeightPx = visibleFrameKeyboardHeight
        updateEffectiveKeyboardHeight(reason, forceLog = forceLog || changed)
        return visibleFrameKeyboardHeight
    }

    private fun updateEffectiveKeyboardHeight(reason: String, forceLog: Boolean = false) {
        val effectiveKeyboardHeight = maxOf(lastNativeImeInsetBottomPx, lastVisibleFrameKeyboardHeightPx)
        val changed = lastEffectiveKeyboardHeightPx != effectiveKeyboardHeight
        lastEffectiveKeyboardHeightPx = effectiveKeyboardHeight
        if (effectiveKeyboardHeight > 0) {
            lastKnownKeyboardHeightPx = effectiveKeyboardHeight
        }
        if (changed) {
            applyEditorColumnBottomPadding(reason)
        }
        if (!forceLog && !changed) return
        KardLeafLog.d(
            NATIVE_IME_TAG,
            "InsetsController imeInset reason=$reason imeInset=$lastNativeImeInsetBottomPx " +
                "visibleFrameKeyboardHeight=$lastVisibleFrameKeyboardHeightPx effectiveKeyboardHeight=$effectiveKeyboardHeight " +
                "columnBottomPadding=${editorColumn.paddingBottom} scrollY=${scrollView.scrollY} " +
                "scrollHeight=${scrollView.height} maxScrollY=${maxScrollY()} " +
                "hasFocus=${contentEditText.hasFocus()} selection=${contentEditText.selectionStart}..${contentEditText.selectionEnd}",
        )
        logNativeEditorLayout("keyboard-height-$reason")
        if (effectiveKeyboardHeight > 0 && contentEditText.hasFocus()) {
            tryScheduleMainReveal("keyboard-height-mainReveal")
        }
    }

    private fun visibleFrameKeyboardHeightPx(visibleFrameOut: Rect? = null): Int {
        val root = rootView
        if (root.height <= 0) {
            visibleFrameOut?.setEmpty()
            return 0
        }
        val visibleFrame = Rect()
        root.getWindowVisibleDisplayFrame(visibleFrame)
        visibleFrameOut?.set(visibleFrame)
        val bottomOverlap = (root.height - visibleFrame.bottom - lastNavigationBarInsetBottomPx).coerceAtLeast(0)
        return if (bottomOverlap > dp(120)) bottomOverlap else 0
    }

    private fun applyEditorColumnBottomPadding(reason: String) {
        val keyboardScrollSpace = keyboardBottomScrollSpacePx()
        val targetBottomPadding = editorColumnBaseBottomPaddingPx +
            maxOf(keyboardScrollSpace, cursorRevealExtraBottomPaddingPx)
        if (editorColumn.paddingBottom == targetBottomPadding) return
        editorColumn.setPadding(
            editorColumn.paddingLeft,
            editorColumn.paddingTop,
            editorColumn.paddingRight,
            targetBottomPadding,
        )
        KardLeafLog.d(
            NATIVE_IME_TAG,
            "imeInset reason=$reason applyPadding columnBottomPadding=$targetBottomPadding " +
                "imeInset=$lastNativeImeInsetBottomPx visibleFrameKeyboardHeight=$lastVisibleFrameKeyboardHeightPx " +
                "effectiveKeyboardHeight=$lastEffectiveKeyboardHeightPx keyboardScrollSpace=$keyboardScrollSpace " +
                "revealExtra=$cursorRevealExtraBottomPaddingPx editTextPaddingBottom=${contentEditText.paddingBottom}",
        )
    }

    private fun keyboardBottomScrollSpacePx(): Int {
        if (lastEffectiveKeyboardHeightPx <= 0) return 0
        return lastEffectiveKeyboardHeightPx + cursorDockToolbarHeightPx + currentCursorLineHeightPx() + cursorDockMarginPx
    }

    private fun currentCursorLineHeightPx(): Int {
        val layout = contentEditText.layout ?: return dp(24)
        val selection = contentEditText.selectionEnd.coerceIn(0, contentEditText.length())
        val line = layout.getLineForOffset(selection)
        return (layout.getLineBottom(line) - layout.getLineTop(line)).coerceAtLeast(dp(20))
    }

    private fun setCursorRevealExtraBottomPadding(extraBottomPaddingPx: Int, reason: String) {
        val safeExtra = extraBottomPaddingPx.coerceAtLeast(0)
        if (cursorRevealExtraBottomPaddingPx == safeExtra) return
        cursorRevealExtraBottomPaddingPx = safeExtra
        applyEditorColumnBottomPadding(reason)
    }

    private fun requestContentCursorRevealSequence(reason: String) {
        if (lastReadOnly || isDisposed) return
        val generation = ++ensureCursorVisibleGeneration
        revealStartedAtMs = SystemClock.elapsedRealtime()
        revealViewHeightAtTouch = scrollView.height
        pendingCursorReveal = true
        pendingMainReveal = true
        mainRevealScheduledGeneration = 0
        stableRevealTargetGeneration = 0
        correctionRevealGeneration = 0
        postMainRevealProbeGeneration = 0
        refreshVisibleFrameKeyboardHeight("$reason-start")
        revealStartedWithKeyboardOpen = lastEffectiveKeyboardHeightPx > 0
        val revealPadding = if (lastEffectiveKeyboardHeightPx > 0) {
            cursorSafeBottomMarginPx
        } else {
            preImeCursorSafeBottomMarginPx
        }
        setCursorRevealExtraBottomPadding(revealPadding, "$reason-start")
        postCursorRevealCheck(
            generation = generation,
            reason = "$reason-check",
            delayMs = CURSOR_REVEAL_CHECK_DELAY_MS,
        )
        postCursorRevealCheck(
            generation = generation,
            reason = "$reason-final",
            delayMs = CURSOR_REVEAL_FINAL_DELAY_MS,
            finalCheck = true,
        )
        tryScheduleMainReveal("keyboard-height-mainReveal")
    }

    private fun hasMainRevealForCurrent(): Boolean =
        lastMainRevealGeneration == ensureCursorVisibleGeneration

    private fun isMainRevealLayoutReady(): Boolean {
        if (!pendingMainReveal || lastEffectiveKeyboardHeightPx <= 0 || scrollView.height <= 0) return false
        val elapsedMs = SystemClock.elapsedRealtime() - revealStartedAtMs
        return revealStartedWithKeyboardOpen || isMainRevealHeightCompressed() || elapsedMs >= 220L
    }

    private fun mainRevealHeightCompressionThreshold(): Int {
        if (revealViewHeightAtTouch <= 0) return dp(24)
        return minOf(dp(120), (revealViewHeightAtTouch * 0.15f).roundToInt()).coerceAtLeast(dp(24))
    }

    private fun isMainRevealHeightCompressed(): Boolean =
        revealViewHeightAtTouch <= 0 ||
            scrollView.height <= revealViewHeightAtTouch - mainRevealHeightCompressionThreshold()

    private fun tryScheduleMainReveal(reason: String): Boolean {
        if (!isMainRevealLayoutReady() || hasMainRevealForCurrent()) return false
        val generation = ensureCursorVisibleGeneration
        if (mainRevealScheduledGeneration == generation) return true
        mainRevealScheduledGeneration = generation
        contentEditText.post {
            mainRevealScheduledGeneration = 0
            if (generation == ensureCursorVisibleGeneration && pendingMainReveal && !hasMainRevealForCurrent()) {
                requestContentCursorVisible(
                    reason = reason,
                    mainReveal = true,
                )
            }
        }
        return true
    }

    private fun postCursorRevealCheck(
        generation: Int,
        reason: String,
        delayMs: Long,
        finalCheck: Boolean = false,
    ) {
        val action = Runnable {
            if (generation != ensureCursorVisibleGeneration) return@Runnable
            if (finalCheck && pendingMainReveal && !hasMainRevealForCurrent() && lastEffectiveKeyboardHeightPx > 0) {
                requestContentCursorVisible(
                    reason = "$reason-mainReveal",
                    finalCheck = true,
                    mainReveal = true,
                )
            } else if (pendingMainReveal && !hasMainRevealForCurrent() && tryScheduleMainReveal("$reason-mainReveal")) {
                return@Runnable
            } else {
                requestContentCursorVisible(
                    reason = reason,
                    finalCheck = finalCheck,
                )
            }
            if (finalCheck) {
                pendingCursorReveal = false
                refreshVisibleFrameKeyboardHeight("$reason-done")
                if (lastEffectiveKeyboardHeightPx > 0 || lastVisibleFrameKeyboardHeightPx == 0) {
                    setCursorRevealExtraBottomPadding(0, "$reason-done")
                }
            }
        }
        if (delayMs > 0L) {
            contentEditText.postDelayed(action, delayMs)
        } else {
            contentEditText.post(action)
        }
    }

    private fun scheduleContentEditCursorCheck() {
        if (lastReadOnly || isDisposed || !contentEditText.hasFocus()) return
        refreshVisibleFrameKeyboardHeight("content-edit-probe")
        if (!pendingCursorReveal && lastEffectiveKeyboardHeightPx == 0) return
        if (!pendingCursorReveal && !isContentCursorNearRevealBottom()) return
        val generation = ++contentEditCursorCheckGeneration
        contentEditText.postDelayed({
            if (generation == contentEditCursorCheckGeneration) {
                requestContentCursorVisible(
                    reason = "content-edit",
                )
            }
        }, 32L)
    }

    private fun isContentCursorNearRevealBottom(): Boolean {
        val layout = contentEditText.layout ?: return true
        if (scrollView.height <= 0) return true
        val selection = contentEditText.selectionEnd.coerceIn(0, contentEditText.length())
        val line = layout.getLineForOffset(selection)
        val cursorBottom = contentEditText.top + contentEditText.totalPaddingTop +
            layout.getLineBottom(line) - contentEditText.scrollY
        val revealBand = cursorSafeBottomMarginPx + cursorDockToolbarHeightPx +
            currentCursorLineHeightPx() + cursorDockMarginPx
        return cursorBottom >= scrollView.scrollY + scrollView.height - revealBand
    }

    private fun requestContentCursorVisible(
        reason: String,
        finalCheck: Boolean = false,
        mainReveal: Boolean = false,
    ) {
        if (lastReadOnly || isDisposed || !contentEditText.isAttachedToWindow || !contentEditText.hasFocus()) return
        val layout = contentEditText.layout ?: return
        val selection = contentEditText.selectionEnd.coerceIn(0, contentEditText.length())
        val line = layout.getLineForOffset(selection)
        val lineHeight = (layout.getLineBottom(line) - layout.getLineTop(line)).coerceAtLeast(dp(20))
        val visibleFrame = Rect()
        val visibleFrameKeyboardHeight = refreshVisibleFrameKeyboardHeight(reason, visibleFrame)
        val effectiveKeyboardHeight = maxOf(lastNativeImeInsetBottomPx, visibleFrameKeyboardHeight)
        val contentLocation = IntArray(2)
        contentEditText.getLocationOnScreen(contentLocation)
        val scrollLocation = IntArray(2)
        scrollView.getLocationOnScreen(scrollLocation)
        val cursorTop = contentLocation[1] + contentEditText.totalPaddingTop +
            layout.getLineTop(line) - contentEditText.scrollY
        val cursorBottom = contentLocation[1] + contentEditText.totalPaddingTop +
            layout.getLineBottom(line) - contentEditText.scrollY
        val scrollTop = scrollLocation[1]
        val scrollBottom = scrollLocation[1] + scrollView.height
        val cursorTarget = computeTargetCursorBottom(
            visibleFrame = visibleFrame,
            scrollTop = scrollTop,
            scrollBottom = scrollBottom,
            effectiveKeyboardHeight = effectiveKeyboardHeight,
            lineHeight = lineHeight,
        )
        val visibleTop = cursorTarget.visibleTop
        val rawVisibleBottom = cursorTarget.rawVisibleBottom
        val predictedVisibleBottom = cursorTarget.visibleBottom
        val safeTop = cursorTarget.safeTop
        val targetCursorBottom = cursorTarget.targetCursorBottom
        val cursorDeltaToTarget = cursorBottom - targetCursorBottom
        val alignTolerance = if (mainReveal) dp(2) else lineHeight
        val skippedBecauseAlreadyAligned = cursorDeltaToTarget <= alignTolerance
        val needsScroll = !skippedBecauseAlreadyAligned
        val heightCompressionThreshold = mainRevealHeightCompressionThreshold()
        val heightCompressed = isMainRevealHeightCompressed()
        val maxScrollY = maxScrollY()
        val beforeScrollY = scrollView.scrollY
        val canScrollMore = beforeScrollY < maxScrollY
        val bottomSpaceEnough = !needsScroll || beforeScrollY + cursorDeltaToTarget <= maxScrollY
        var finalFallback = false
        var directScroll = false
        var smoothScroll = false
        var skippedBecauseRecentMainReveal = false
        var skippedBecausePendingMainReveal = false
        var correctionAllowed = false
        var correctionUsed = false
        var correctionSkippedReason = "none"
        var finalFallbackReason = "none"
        val now = SystemClock.elapsedRealtime()
        if (needsScroll) {
            val recentMainReveal = !mainReveal &&
                lastMainRevealGeneration == ensureCursorVisibleGeneration &&
                now - lastMainRevealAtMs < RECENT_MAIN_REVEAL_WINDOW_MS
            correctionAllowed = recentMainReveal &&
                correctionRevealGeneration != ensureCursorVisibleGeneration &&
                cursorDeltaToTarget > alignTolerance &&
                canScrollMore
            if (recentMainReveal && !correctionAllowed) {
                correctionSkippedReason = when {
                    correctionRevealGeneration == ensureCursorVisibleGeneration -> "alreadyUsed"
                    cursorDeltaToTarget <= alignTolerance -> "withinTolerance"
                    !canScrollMore -> "cannotScrollMore"
                    else -> "recentMainReveal"
                }
            }
            skippedBecauseRecentMainReveal = recentMainReveal && !correctionAllowed && !finalCheck
            skippedBecausePendingMainReveal = !mainReveal && pendingMainReveal && !hasMainRevealForCurrent()
            if (!skippedBecauseRecentMainReveal && !skippedBecausePendingMainReveal) {
                val targetScrollY = (beforeScrollY + cursorDeltaToTarget).coerceIn(0, maxScrollY)
                if (targetScrollY != beforeScrollY) {
                    directScroll = true
                    correctionUsed = correctionAllowed
                    if (correctionUsed) correctionRevealGeneration = ensureCursorVisibleGeneration
                    finalFallback = finalCheck
                    if (finalFallback) finalFallbackReason = "belowTarget"
                    smoothScroll = (mainReveal || correctionAllowed) && !finalCheck
                    if (smoothScroll) {
                        scrollView.smoothScrollTo(0, targetScrollY)
                    } else {
                        scrollView.scrollTo(0, targetScrollY)
                    }
                    if (mainReveal) {
                        lastMainRevealGeneration = ensureCursorVisibleGeneration
                        lastMainRevealAtMs = now
                        pendingMainReveal = false
                    }
                } else if (correctionAllowed) {
                    correctionSkippedReason = "maxScrollY"
                }
            }
        } else {
            correctionSkippedReason = "alreadyAligned"
        }
        if (mainReveal && !directScroll) {
            lastMainRevealGeneration = ensureCursorVisibleGeneration
            lastMainRevealAtMs = now
            pendingMainReveal = false
        }
        if (mainReveal && hasMainRevealForCurrent()) {
            stableRevealTargetGeneration = ensureCursorVisibleGeneration
            stableRevealTargetCursorBottom = targetCursorBottom
            schedulePostMainRevealProbes(reason)
        }
        val scrollDelta = scrollView.scrollY - beforeScrollY
        val textLength = contentEditText.length()
        val lineCount = layout.lineCount
        val isAtTextEnd = selection >= textLength
        val nearTextEnd = line >= lineCount - 4 || selection >= textLength - 32
        val lastVisibleLine = layout.getLineForVertical(
            (scrollView.scrollY - contentEditText.top + scrollView.height).coerceAtLeast(0),
        )
        val contentHeight = scrollView.getChildAt(0)?.height ?: 0
        val requestReason = if (correctionUsed) "$reason-correction" else reason
        KardLeafLog.d(
            NATIVE_IME_TAG,
            "cursorRequest reason=$requestReason phase=$reason revealId=$ensureCursorVisibleGeneration selection=$selection line=$line " +
                "isAtTextEnd=$isAtTextEnd nearTextEnd=$nearTextEnd cursorOffset=$selection textLength=$textLength " +
                "lineCount=$lineCount cursorLine=$line lastVisibleLine=$lastVisibleLine " +
                "viewHeightAtTouch=$revealViewHeightAtTouch currentViewHeight=${scrollView.height} " +
                "heightCompressed=$heightCompressed heightCompressionThreshold=$heightCompressionThreshold " +
                "cursorBottom=$cursorBottom targetCursorBottom=$targetCursorBottom visibleBottom=$predictedVisibleBottom " +
                "rawVisibleBottom=$rawVisibleBottom toolbarHeight=$cursorDockToolbarHeightPx lineHeight=$lineHeight " +
                "margin=$cursorDockMarginPx targetStable=${cursorTarget.usedStableTarget} cursorDeltaToTarget=$cursorDeltaToTarget " +
                "cursorTop=$cursorTop visibleTop=$visibleTop safeTop=$safeTop needsScroll=$needsScroll " +
                "imeInset=$lastNativeImeInsetBottomPx visibleFrameKeyboardHeight=$visibleFrameKeyboardHeight " +
                "effectiveKeyboardHeight=$effectiveKeyboardHeight lastKnownKeyboardHeight=$lastKnownKeyboardHeightPx " +
                "contentHeight=$contentHeight scrollViewHeight=${scrollView.height} editTextHeight=${contentEditText.height} " +
                "editTextPaddingBottom=${contentEditText.paddingBottom} scrollViewPaddingBottom=${scrollView.paddingBottom} " +
                "columnBottomPadding=${editorColumn.paddingBottom} canScrollMore=$canScrollMore bottomSpaceEnough=$bottomSpaceEnough " +
                "scrollY=$beforeScrollY->${scrollView.scrollY} viewHeight=${scrollView.height} " +
                "maxScrollY=$maxScrollY delta=$scrollDelta requiredDelta=$cursorDeltaToTarget directScroll=$directScroll " +
                "smoothScroll=$smoothScroll finalFallback=$finalFallback " +
                "pendingMainReveal=$pendingMainReveal hasMainReveal=${hasMainRevealForCurrent()} " +
                "skippedBecauseAlreadyAligned=$skippedBecauseAlreadyAligned " +
                "skippedBecauseRecentMainReveal=$skippedBecauseRecentMainReveal " +
                "skippedBecausePendingMainReveal=$skippedBecausePendingMainReveal " +
                "correctionAllowed=$correctionAllowed correctionUsed=$correctionUsed " +
                "correctionSkippedReason=$correctionSkippedReason " +
                "finalFallbackReason=$finalFallbackReason",
        )
        logNativeEditorLayout("cursorRequest-$requestReason", "manualScroll=$directScroll finalFallback=$finalFallback")
    }

    private fun schedulePostMainRevealProbes(reason: String) {
        if (postMainRevealProbeGeneration == ensureCursorVisibleGeneration) return
        val generation = ensureCursorVisibleGeneration
        postMainRevealProbeGeneration = generation
        listOf(16L, 80L, 200L).forEach { delayMs ->
            contentEditText.postDelayed({
                if (generation == ensureCursorVisibleGeneration) {
                    requestContentCursorVisible(
                        reason = "$reason-postMainReveal-${delayMs}ms",
                    )
                }
            }, delayMs)
        }
    }

    private data class CursorTarget(
        val visibleTop: Int,
        val rawVisibleBottom: Int,
        val visibleBottom: Int,
        val safeTop: Int,
        val targetCursorBottom: Int,
        val usedStableTarget: Boolean,
    )

    private fun computeTargetCursorBottom(
        visibleFrame: Rect,
        scrollTop: Int,
        scrollBottom: Int,
        effectiveKeyboardHeight: Int,
        lineHeight: Int,
    ): CursorTarget {
        val visibleTop = if (visibleFrame.isEmpty) scrollTop else maxOf(scrollTop, visibleFrame.top)
        val rawVisibleBottom = if (visibleFrame.isEmpty) scrollBottom else minOf(scrollBottom, visibleFrame.bottom)
        val predictedKeyboardHeight = when {
            effectiveKeyboardHeight > 0 -> effectiveKeyboardHeight
            lastKnownKeyboardHeightPx > 0 -> lastKnownKeyboardHeightPx
            pendingCursorReveal -> fallbackKeyboardHeightPx()
            else -> 0
        }
        val visibleBottom = if (effectiveKeyboardHeight > 0 || predictedKeyboardHeight <= 0) {
            rawVisibleBottom
        } else {
            minOf(rawVisibleBottom, (scrollBottom - predictedKeyboardHeight).coerceAtLeast(scrollTop))
        }
        val safeTop = visibleTop + cursorSafeTopMarginPx
        val computedTarget = (visibleBottom - cursorDockToolbarHeightPx - lineHeight - cursorDockMarginPx)
            .coerceAtLeast(safeTop + lineHeight)
        val stableTarget = stableRevealTargetCursorBottom
        val canUseStableTarget = stableRevealTargetGeneration == ensureCursorVisibleGeneration &&
            stableTarget >= safeTop + lineHeight &&
            stableTarget <= visibleBottom - cursorDockMarginPx
        return CursorTarget(
            visibleTop = visibleTop,
            rawVisibleBottom = rawVisibleBottom,
            visibleBottom = visibleBottom,
            safeTop = safeTop,
            targetCursorBottom = if (canUseStableTarget) stableTarget else computedTarget,
            usedStableTarget = canUseStableTarget,
        )
    }

    private fun logNativeEditorLayout(
        reason: String,
        extra: String = "",
    ) {
        nativeLayoutLogCount++
        val visibleFrame = Rect()
        val visibleFrameKeyboardHeight = visibleFrameKeyboardHeightPx(visibleFrame)
        val layout = contentEditText.layout
        val selection = contentEditText.selectionEnd.coerceIn(0, contentEditText.length())
        val cursorBottom = if (layout != null) {
            val line = layout.getLineForOffset(selection)
            val contentLocation = IntArray(2)
            contentEditText.getLocationOnScreen(contentLocation)
            contentLocation[1] + contentEditText.totalPaddingTop +
                layout.getLineBottom(line) - contentEditText.scrollY
        } else {
            -1
        }
        val contentHeight = scrollView.getChildAt(0)?.height ?: 0
        val maxScrollY = maxScrollY()
        KardLeafLog.d(
            NATIVE_EDITOR_LAYOUT_TAG,
            "native layout reason=$reason count=$nativeLayoutLogCount nativeHeight=$height " +
                "scrollViewHeight=${scrollView.height} editTextHeight=${contentEditText.height} " +
                "contentHeight=$contentHeight editTextPaddingBottom=${contentEditText.paddingBottom} " +
                "scrollViewPaddingBottom=${scrollView.paddingBottom} columnBottomPadding=${editorColumn.paddingBottom} " +
                "editorColumnHeight=${editorColumn.height} rootHeight=${rootView.height} " +
                "visibleFrameBottom=${visibleFrame.bottom} visibleFrameKeyboardHeight=$visibleFrameKeyboardHeight " +
                "imeInset=$lastNativeImeInsetBottomPx effectiveKeyboardHeight=$lastEffectiveKeyboardHeightPx " +
                "lastKnownKeyboardHeight=$lastKnownKeyboardHeightPx " +
                "scrollY=${scrollView.scrollY} maxScrollY=$maxScrollY canScrollMore=${scrollView.scrollY < maxScrollY} " +
                "cursorBottom=$cursorBottom " +
                "pendingMainReveal=$pendingMainReveal hasMainReveal=${hasMainRevealForCurrent()} $extra",
        )
    }

    private fun fallbackKeyboardHeightPx(): Int {
        val rootHeight = rootView.height.takeIf { it > 0 } ?: height
        return if (rootHeight > 0) {
            maxOf(dp(280), (rootHeight * 0.38f).roundToInt())
        } else {
            dp(320)
        }
    }

    private fun recordUserPerfScrollFrame() {
        if (isDisposed) return
        val now = SystemClock.elapsedRealtime()
        if (userPerfScrollStartMs <= 0L) {
            userPerfScrollStartMs = now
            userPerfScrollLastMs = now
            userPerfScrollFrames = 0
            userPerfScrollSlowFrames = 0
            userPerfScrollMaxFrameMs = 0L
            userPerfScrollStartY = scrollView.scrollY
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorScroll humanStart mode=nativeEditor contentLen=${contentEditText.length()} " +
                    "sizeTier=$userPerfSizeTier scrollY=$userPerfScrollStartY maxScrollY=${maxScrollY()} key=$boundDocumentKey",
            )
        } else {
            val frameMs = now - userPerfScrollLastMs
            if (frameMs > 0L) {
                userPerfScrollFrames++
                userPerfScrollMaxFrameMs = maxOf(userPerfScrollMaxFrameMs, frameMs)
                if (frameMs > 32L) userPerfScrollSlowFrames++
            }
            userPerfScrollLastMs = now
        }
        userPerfScrollHandler.removeCallbacks(userPerfScrollSettleRunnable)
        userPerfScrollHandler.postDelayed(userPerfScrollSettleRunnable, 180L)
    }

    private fun logUserPerfScrollSettled() {
        val startMs = userPerfScrollStartMs
        if (startMs <= 0L) return
        val elapsed = (userPerfScrollLastMs - startMs).coerceAtLeast(0L)
        val endScrollY = scrollView.scrollY
        val deltaPx = abs(endScrollY - userPerfScrollStartY)
        val avgFrame = KardLeafPerfLog.avgFrame(elapsed, userPerfScrollFrames)
        val msPerPx = KardLeafPerfLog.msPerPx(elapsed, deltaPx)
        val smooth = userPerfScrollSlowFrames == 0 && userPerfScrollMaxFrameMs <= 32L
        KardLeafLog.d(
            USER_PERF_TRACE_TAG,
            "editorScroll humanSettled mode=nativeEditor elapsed=${elapsed}ms " +
                "frames=$userPerfScrollFrames slowFrames=$userPerfScrollSlowFrames " +
                "maxFrame=${userPerfScrollMaxFrameMs}ms avgFrame=${avgFrame}ms " +
                "smooth=$smooth contentLen=${contentEditText.length()} sizeTier=$userPerfSizeTier " +
                "fromY=$userPerfScrollStartY toY=$endScrollY deltaPx=$deltaPx msPerPx=$msPerPx " +
                "maxScrollY=${maxScrollY()} key=$boundDocumentKey",
        )
        userPerfScrollStartMs = 0L
        userPerfScrollLastMs = 0L
        userPerfScrollFrames = 0
        userPerfScrollSlowFrames = 0
        userPerfScrollMaxFrameMs = 0L
        userPerfScrollStartY = 0
    }

    fun configureUserPerf(
        openStartRealtimeMs: Long?,
        sizeTier: String,
        session: EditorOpenSession? = null,
        onFrameCommitted: (Long) -> Unit = {},
    ) {
        if (userPerfOpenStartRealtimeMs != openStartRealtimeMs) {
            userPerfFirstNativeTextLaidOutLogged = false
        }
        userPerfOpenStartRealtimeMs = openStartRealtimeMs
        userPerfSizeTier = sizeTier
        openSession = session
        frameCommittedCallback = onFrameCommitted
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
        currentFolder: String,
        inlineImagePreviewEnabled: Boolean,
        readOnly: Boolean,
        onTitleChanged: () -> Unit,
        onContentChanged: () -> Unit,
        onSelectionChanged: (Int, Int) -> Unit,
        onUndoRedoChanged: () -> Unit,
        onUserInteraction: () -> Unit,
        onFastScrollSourceScrolled: () -> Unit,
        onInlineImageClicked: (KardLeafImageClickTarget) -> Unit,
    ) {
        titleChangedCallback = onTitleChanged
        userInteractionCallback = onUserInteraction
        scrollChangedCallback = onFastScrollSourceScrolled
        titleEditText.hint = titleHint
        titleEditText.setTextColor(textColor)
        titleEditText.setHintTextColor(hintColor)
        titleEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleTextSizeSp)
        titleEditText.visibility = if (showTitle) View.VISIBLE else View.GONE
        lastReadOnly = readOnly
        titleEditText.isFocusable = !readOnly
        titleEditText.isFocusableInTouchMode = !readOnly
        titleEditText.isCursorVisible = !readOnly && !scrollingCursorHidden
        titleEditText.showSoftInputOnFocus = !readOnly

        contentEditText.hint = contentHint
        contentEditText.setTextColor(textColor)
        contentEditText.setHintTextColor(hintColor)
        contentEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, contentTextSizeSp)
        contentEditText.typeface = editorTypeface(contentFontFamily)
        contentEditText.setLineSpacing(dp(contentParagraphSpacingDp).toFloat(), contentLineHeightMultiplier)
        contentEditText.letterSpacing = contentLetterSpacingSp / contentTextSizeSp.coerceAtLeast(1f)
        contentEditText.isFocusable = !readOnly
        contentEditText.isFocusableInTouchMode = !readOnly
        contentEditText.isCursorVisible = !readOnly && !scrollingCursorHidden
        contentEditText.showSoftInputOnFocus = !readOnly
        contentEditText.configureInlineImagePreviewFolder(currentFolder)
        contentEditText.configureInlineImagePreviewEnabled(inlineImagePreviewEnabled && !readOnly)
        contentEditText.kardLeafContentCallback = {
            onContentChanged()
            scheduleContentEditCursorCheck()
        }
        contentEditText.kardLeafSelectionCallback = onSelectionChanged
        contentEditText.kardLeafUndoRedoCallback = onUndoRedoChanged
        contentEditText.kardLeafInlineImageClickCallback = onInlineImageClicked
    }

    fun bindDocument(
        documentKey: String,
        initialTitle: String,
        initialContent: String,
        preferredSnapshot: KardLeafEditorSnapshot,
    ) {
        val startMs = SystemClock.elapsedRealtime()
        if (boundDocumentKey == null) {
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "bindDocument first key=$documentKey initialTitleLen=${initialTitle.length} initialContentLen=${initialContent.length} " +
                    "preferredTitleLen=${preferredSnapshot.title.length} preferredContentLen=${preferredSnapshot.content.length}",
            )
            setInitialSnapshot(
                title = preferredSnapshot.title,
                content = preferredSnapshot.content,
                selection = preferredSnapshot.selection,
            )
            boundDocumentKey = documentKey
            loadedTitle = initialTitle
            loadedContent = initialContent
            KardLeafLog.d(EDITOR_TRACE_TAG, "bindDocument first done key=$documentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            return
        }

        val currentTitle = getTitleString()
        val currentContent = getContentString()
        val isDifferentDocument = boundDocumentKey != documentKey
        val incomingLoadedChanged = loadedTitle != initialTitle || loadedContent != initialContent
        if (!isDifferentDocument && !incomingLoadedChanged) return

        val isSameAsIncoming = currentTitle == initialTitle && currentContent == initialContent
        val isEditorStillAtLoadedText = currentTitle == loadedTitle && currentContent == loadedContent
        val isMissingInitialText =
            (currentTitle.isEmpty() && initialTitle.isNotEmpty()) ||
                (currentContent.isEmpty() && initialContent.isNotEmpty())
        val canSafelyReloadDifferentDocument = isDifferentDocument && !hasEditorFocus()
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "bindDocument change key=$documentKey oldKey=$boundDocumentKey currentTitleLen=${currentTitle.length} currentContentLen=${currentContent.length} " +
                "initialTitleLen=${initialTitle.length} initialContentLen=${initialContent.length} loadedTitleLen=${loadedTitle.length} loadedContentLen=${loadedContent.length} " +
                "different=$isDifferentDocument incomingChanged=$incomingLoadedChanged sameIncoming=$isSameAsIncoming stillLoaded=$isEditorStillAtLoadedText " +
                "missingInitial=$isMissingInitialText focus=${hasEditorFocus()}",
        )

        when {
            isSameAsIncoming -> {
                boundDocumentKey = documentKey
                loadedTitle = initialTitle
                loadedContent = initialContent
                KardLeafLog.d(EDITOR_TRACE_TAG, "bindDocument metadata only key=$documentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            }
            isEditorStillAtLoadedText || isMissingInitialText || canSafelyReloadDifferentDocument -> {
                val previousSelection = getContentSelection()
                setInitialSnapshot(initialTitle, initialContent, previousSelection)
                boundDocumentKey = documentKey
                loadedTitle = initialTitle
                loadedContent = initialContent
                KardLeafLog.d(EDITOR_TRACE_TAG, "bindDocument reloaded key=$documentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            }
            else -> {
                // Preserve newer local typing if repository emissions race with the editor.
                boundDocumentKey = documentKey
                loadedTitle = initialTitle
                loadedContent = initialContent
                KardLeafLog.w(EDITOR_TRACE_TAG, "bindDocument preserve local text key=$documentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            }
        }
    }

    override fun getTitleString(): String = titleEditText.text?.toString().orEmpty()

    override fun getContentString(): String = contentEditText.getTextString()

    override fun contentLength(): Int = contentEditText.length()

    override fun getContentSelection(): TextRange = contentEditText.getSelectionRange()

    override fun getFastScrollMetrics(): EditorFastScrollMetrics {
        val maxScrollY = maxScrollY()
        if (scrollView.height <= 0 || maxScrollY <= 0) return EditorFastScrollMetrics()
        val contentHeight = scrollView.height + maxScrollY
        return EditorFastScrollMetrics(
            canScroll = true,
            ratio = (scrollView.scrollY.toFloat() / maxScrollY).coerceIn(0f, 1f),
            thumbFraction = (scrollView.height.toFloat() / contentHeight).coerceIn(0f, 1f),
        )
    }

    override fun fastScrollToRatio(ratio: Float) {
        val maxScrollY = maxScrollY()
        if (maxScrollY <= 0) return
        val targetScrollY = (ratio.coerceIn(0f, 1f) * maxScrollY).roundToInt()
        scrollView.scrollTo(0, targetScrollY.coerceIn(0, maxScrollY))
    }

    private fun maxScrollY(): Int {
        val contentHeight = scrollView.getChildAt(0)?.height ?: 0
        return (contentHeight - scrollView.height).coerceAtLeast(0)
    }

    override fun shouldReserveContentTouchForEditing(
        windowX: Float,
        windowY: Float,
        radiusPx: Float,
    ): Boolean {
        val editorLocation = IntArray(2)
        contentEditText.getLocationInWindow(editorLocation)
        val localX = windowX - editorLocation[0]
        val localY = windowY - editorLocation[1]
        if (localX < -radiusPx || localX > contentEditText.width + radiusPx ||
            localY < -radiusPx || localY > contentEditText.height + radiusPx
        ) {
            return false
        }

        val selection = getContentSelection()
        if (selection.start != selection.end) {
            return true
        }

        val layout = contentEditText.layout ?: return true
        val cursor = selection.end.coerceIn(0, contentEditText.length())
        val line = layout.getLineForOffset(cursor)
        val cursorWindowX =
            editorLocation[0] + contentEditText.totalPaddingLeft +
                layout.getPrimaryHorizontal(cursor) - contentEditText.scrollX
        val cursorWindowY =
            editorLocation[1] + contentEditText.totalPaddingTop +
                ((layout.getLineTop(line) + layout.getLineBottom(line)) / 2f) - contentEditText.scrollY
        return abs(windowX - cursorWindowX) <= radiusPx &&
            abs(windowY - cursorWindowY) <= radiusPx
    }

    fun setInitialSnapshot(
        title: String,
        content: String,
        selection: TextRange? = null,
    ) {
        val startMs = SystemClock.elapsedRealtime()
        val openStartMs = userPerfOpenStartRealtimeMs
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "setInitialSnapshot key=$boundDocumentKey titleLen=${title.length} contentLen=${content.length} selection=$selection",
        )
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "native setInitialSnapshot start key=$boundDocumentKey titleLen=${title.length} contentLen=${content.length} " +
                "selection=$selection thread=${Thread.currentThread().name}",
        )
        if (openStartMs != null) {
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorOpen nativeSetInitialTextStart elapsed=${SystemClock.elapsedRealtime() - openStartMs}ms " +
                    "contentLen=${content.length} sizeTier=$userPerfSizeTier key=$boundDocumentKey",
            )
        }
        if (titleEditText.text?.toString().orEmpty() != title) {
            programmaticTitleChange.set(true)
            try {
                val titleStartMs = SystemClock.elapsedRealtime()
                titleEditText.setText(title)
                titleEditText.setSelection(title.length.coerceIn(0, titleEditText.length()))
                KardLeafLog.d(
                    EDITOR_TRACE_TAG,
                    "setInitialSnapshot title set done key=$boundDocumentKey titleLen=${title.length} elapsed=${SystemClock.elapsedRealtime() - titleStartMs}ms",
                )
            } finally {
                programmaticTitleChange.set(false)
            }
        } else {
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "setInitialSnapshot title unchanged key=$boundDocumentKey titleLen=${title.length}",
            )
        }
        val targetSelection = selection ?: TextRange(content.length, content.length)
        val contentStartMs = SystemClock.elapsedRealtime()
        contentEditText.setInitialText(content, targetSelection)
        scheduleNativeFrameCommit(content.length)
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "setInitialSnapshot content set done key=$boundDocumentKey contentLen=${content.length} " +
                "contentSetElapsed=${SystemClock.elapsedRealtime() - contentStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "native setInitialSnapshot contentDone key=$boundDocumentKey contentLen=${content.length} " +
                "contentSetElapsed=${SystemClock.elapsedRealtime() - contentStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        if (openStartMs != null) {
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorOpen nativeSetTextDone elapsed=${SystemClock.elapsedRealtime() - openStartMs}ms " +
                    "contentLen=${content.length} sizeTier=$userPerfSizeTier " +
                    "setTextElapsed=${SystemClock.elapsedRealtime() - contentStartMs}ms key=$boundDocumentKey",
            )
        }
        val layoutPostScheduledMs = SystemClock.elapsedRealtime()
        contentEditText.post {
            val postNowMs = SystemClock.elapsedRealtime()
            val editorChildHeight = scrollView.getChildAt(0)?.height ?: 0
            val contentLayout = contentEditText.layout
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorOpen nativeLayoutProbe postDelay=${postNowMs - layoutPostScheduledMs}ms " +
                    "elapsed=${postNowMs - (openStartMs ?: startMs)}ms contentLen=${contentEditText.length()} sizeTier=$userPerfSizeTier " +
                    "lineCount=${contentEditText.lineCount} layoutReady=${contentLayout != null} layoutHeight=${contentLayout?.height ?: -1} " +
                    "editHeight=${contentEditText.height} editMinLines=${contentEditText.minLines} editMinHeight=${contentEditText.minimumHeight} " +
                    "scrollViewHeight=${scrollView.height} editorColumnHeight=${editorColumn.height} childHeight=$editorChildHeight " +
                    "rootHeight=$height scrollY=${scrollView.scrollY} key=$boundDocumentKey",
            )
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "setInitialSnapshot content post key=$boundDocumentKey viewContentLen=${contentEditText.length()} " +
                    "lineCount=${contentEditText.lineCount} layoutReady=${contentEditText.layout != null} height=${contentEditText.height} " +
                    "elapsedFromStart=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            if (openStartMs != null && !userPerfFirstNativeTextLaidOutLogged) {
                userPerfFirstNativeTextLaidOutLogged = true
                KardLeafLog.d(
                    USER_PERF_TRACE_TAG,
                    "editorOpen nativeFirstTextLaidOut elapsed=${SystemClock.elapsedRealtime() - openStartMs}ms " +
                        "contentLen=${contentEditText.length()} sizeTier=$userPerfSizeTier " +
                        "layoutReady=${contentEditText.layout != null} lineCount=${contentEditText.lineCount} " +
                        "viewHeight=${contentEditText.height} scrollHeight=${scrollView.getChildAt(0)?.height ?: 0} " +
                        "key=$boundDocumentKey",
                )
                KardLeafLog.d(
                    USER_PERF_TRACE_TAG,
                    "editorOpen bodyRendered elapsed=${SystemClock.elapsedRealtime() - openStartMs}ms " +
                        "mode=nativeEditor renderStatus=${if (contentEditText.length() > 0) "visible" else "empty"} " +
                        "contentLen=${contentEditText.length()} sizeTier=$userPerfSizeTier " +
                        "layoutReady=${contentEditText.layout != null} lineCount=${contentEditText.lineCount} " +
                        "viewHeight=${contentEditText.height} scrollHeight=${scrollView.getChildAt(0)?.height ?: 0} " +
                        "key=$boundDocumentKey",
                )
            }
        }
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "setInitialSnapshot done key=$boundDocumentKey viewTitleLen=${titleEditText.length()} viewContentLen=${contentEditText.length()} " +
                "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
    }

    private fun scheduleNativeFrameCommit(contentLength: Int) {
        val generation = ++nativeTextGeneration
        pendingNativePreDrawListener?.let { listener ->
            if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(listener)
        }
        val listener =
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (generation != nativeTextGeneration || contentEditText.length() != contentLength || contentEditText.layout == null) {
                        return true
                    }
                    if (viewTreeObserver.isAlive) viewTreeObserver.removeOnPreDrawListener(this)
                    pendingNativePreDrawListener = null
                    val session = openSession ?: return true
                    KardLeafLog.d(USER_PERF_TRACE_TAG, "editorOpen nativeNewTextPreDraw ${session.trace(contentLength)}")
                    val committed = {
                        if (generation == nativeTextGeneration && contentEditText.length() == contentLength) {
                            KardLeafLog.d(USER_PERF_TRACE_TAG, "editorOpen nativeFrameCommitted ${session.trace(contentLength)}")
                            frameCommittedCallback?.invoke(session.sessionId)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        viewTreeObserver.registerFrameCommitCallback(committed)
                    } else {
                        Choreographer.getInstance().postFrameCallback { postOnAnimation { committed() } }
                    }
                    return true
                }
            }
        pendingNativePreDrawListener = listener
        viewTreeObserver.addOnPreDrawListener(listener)
        requestLayout()
        invalidate()
    }

    override fun insertAtContentCursor(
        prefix: String,
        suffix: String,
    ) {
        contentEditText.insertAtCursor(prefix, suffix)
    }

    override fun replaceContentSelection(insertion: String) {
        val beforeSelection = getContentSelection()
        val beforeLen = contentLength()
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "native replaceContentSelection before key=$boundDocumentKey len=$beforeLen " +
                "selection=${beforeSelection.start}..${beforeSelection.end} insertionLen=${insertion.length}",
        )
        contentEditText.replaceSelection(insertion)
        val afterSelection = getContentSelection()
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "native replaceContentSelection after key=$boundDocumentKey len=${contentLength()} " +
                "selection=${afterSelection.start}..${afterSelection.end}",
        )
    }

    override fun replaceContent(
        newText: String,
        selection: TextRange?,
    ) {
        contentEditText.replaceAll(newText, selection)
    }

    override fun setContentSelection(
        start: Int,
        end: Int,
    ) {
        contentEditText.setSelectionRange(start, end)
    }

    override fun focusContent() {
        val startMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "native focusContent start key=$boundDocumentKey attached=$isAttachedToWindow " +
                "contentFocusable=${contentEditText.isFocusable} touchMode=${contentEditText.isFocusableInTouchMode} " +
                "showSoftInput=${contentEditText.showSoftInputOnFocus} hasFocus=${contentEditText.hasFocus()} " +
                "contentLen=${contentEditText.length()} ${nativeEditorMemorySummary()}",
        )
        val result = contentEditText.requestFocus()
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "native focusContent requested key=$boundDocumentKey result=$result attached=$isAttachedToWindow " +
                "hasFocus=${contentEditText.hasFocus()} elapsed=${SystemClock.elapsedRealtime() - startMs}ms ${nativeEditorMemorySummary()}",
        )
        if (result && contentEditText.hasFocus() && contentEditText.showSoftInputOnFocus && !pendingCursorReveal) {
            requestContentCursorRevealSequence("focus-content")
        } else {
            KardLeafLog.d(
                NATIVE_IME_TAG,
                "cursorRequest reason=focus-content-skip revealId=$ensureCursorVisibleGeneration " +
                    "result=$result hasFocus=${contentEditText.hasFocus()} showSoftInput=${contentEditText.showSoftInputOnFocus} " +
                    "pendingCursorReveal=$pendingCursorReveal effectiveKeyboardHeight=$lastEffectiveKeyboardHeightPx " +
                    "skippedBecauseRecentMainReveal=false skippedBecauseAlreadyAligned=true finalFallback=false",
            )
        }
        contentEditText.postDelayed({
            val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val shown = if (contentEditText.isAttachedToWindow && contentEditText.hasFocus()) {
                inputMethodManager?.showSoftInput(contentEditText, InputMethodManager.SHOW_IMPLICIT) == true
            } else {
                false
            }
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "native focusContent showKeyboard key=$boundDocumentKey shown=$shown attached=${contentEditText.isAttachedToWindow} " +
                    "hasFocus=${contentEditText.hasFocus()} elapsed=${SystemClock.elapsedRealtime() - startMs}ms ${nativeEditorMemorySummary()}",
            )
        }, 80L)
    }

    override fun scrollContentOffsetToVisible(offset: Int) {
        contentEditText.post {
            val layout = contentEditText.layout ?: return@post
            val safeOffset = offset.coerceIn(0, contentEditText.length())
            val line = layout.getLineForOffset(safeOffset)
            val targetY = contentEditText.top + layout.getLineTop(line)
            val viewportBias = (height * 0.25f).toInt()
            scrollView.smoothScrollTo(0, (targetY - viewportBias).coerceAtLeast(0))
        }
    }

    override fun scrollToProgress(progress: Float) {
        scrollView.post {
            val contentHeight = scrollView.getChildAt(0)?.height ?: return@post
            val maxScrollY = (contentHeight - scrollView.height).coerceAtLeast(0)
            val targetY = (maxScrollY * progress.coerceIn(0f, 1f)).roundToInt()
            scrollView.scrollTo(0, targetY.coerceIn(0, maxScrollY))
        }
    }

    override fun highlightContentSearch(
        query: String,
        currentStart: Int,
        useRegex: Boolean,
        matchCase: Boolean,
    ): Int = contentEditText.highlightSearch(query, currentStart, useRegex, matchCase)

    override fun clearContentSearchHighlights() {
        contentEditText.clearSearchHighlights()
    }

    override fun undoContent() {
        contentEditText.undo()
    }

    override fun redoContent() {
        contentEditText.redo()
    }

    override fun canUndoContent(): Boolean = contentEditText.canUndo()

    override fun canRedoContent(): Boolean = contentEditText.canRedo()

    override fun clearContentHistory() {
        contentEditText.clearHistory()
    }

    override fun refreshContentInlineImagePreviews() {
        contentEditText.refreshInlineImagePreviews()
    }

    override fun executeCommand(
        command: String,
        args: List<Any>,
    ): Boolean = when (command) {
        "toggleHeading" -> contentEditText.toggleHeadingAtCursor((args.firstOrNull() as? Number)?.toInt() ?: 1)
        "toggleBold" -> contentEditText.toggleInlineAtCursor("**")
        "toggleItalic" -> contentEditText.toggleInlineAtCursor("_")
        "toggleUnderline" -> contentEditText.toggleInlineAtCursor("<u>", "</u>")
        "toggleStrike" -> contentEditText.toggleInlineAtCursor("~~")
        "toggleCode" -> contentEditText.toggleInlineAtCursor("`")
        "toggleBlockquote" -> contentEditText.toggleBlockquoteAtCursor()
        "toggleUnorderedList" -> contentEditText.toggleUnorderedListAtCursor()
        "toggleOrderedList" -> contentEditText.toggleOrderedListAtCursor()
        "toggleCheckList" -> contentEditText.toggleCheckListAtCursor()
        "indent" -> contentEditText.indentCurrentLine()
        "outdent" -> contentEditText.outdentCurrentLine()
        else -> false
    }

    fun hasEditorFocus(): Boolean = titleEditText.hasFocus() || contentEditText.hasFocus()

    override fun dispose(clearText: Boolean) {
        if (isDisposed) return
        detachVisibleFrameKeyboardListener()
        userPerfScrollHandler.removeCallbacks(userPerfScrollSettleRunnable)
        logUserPerfScrollSettled()
        isDisposed = true
        titleChangedCallback = null
        userInteractionCallback = null
        scrollChangedCallback = null
        titleEditText.removeTextChangedListener(titleWatcher)
        contentEditText.configureMarkdownWatcher(null)
        if (clearText) {
            clearTextForDispose()
        }
        contentEditText.releaseInlineImagePreviews()
        contentEditText.kardLeafContentCallback = null
        contentEditText.kardLeafSelectionCallback = null
        contentEditText.kardLeafUndoRedoCallback = null
        contentEditText.kardLeafInlineImageClickCallback = null
        markdownExecutor.shutdownNow()
    }

    private fun clearTextForDispose() {
        programmaticTitleChange.set(true)
        try {
            titleEditText.setText("")
        } finally {
            programmaticTitleChange.set(false)
        }
        contentEditText.clearTextForDispose()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun editorTypeface(fontFamily: String): Typeface =
        when (fontFamily.trim().lowercase(Locale.ROOT)) {
            "system" -> Typeface.DEFAULT
            "sans-serif" -> Typeface.SANS_SERIF
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.create(fontFamily.trim(), Typeface.NORMAL)
        }
}

@Composable
fun KardLeafNativeEditor(
    initialTitle: String,
    initialContent: String,
    documentKey: String,
    controller: KardLeafEditorController,
    onTitleChanged: () -> Unit,
    onContentChanged: () -> Unit,
    onUndoRedoChanged: () -> Unit,
    onUserInteraction: () -> Unit = {},
    onFastScrollSourceScrolled: () -> Unit = {},
    onInlineImageClicked: (KardLeafImageClickTarget) -> Unit = {},
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
    onFocusRequestHandled: (Int) -> Unit = {},
    initialSelection: TextRange? = null,
    showTitle: Boolean = true,
    currentFolder: String = "",
    inlineImagePreviewEnabled: Boolean = true,
    readOnly: Boolean = false,
    userPerfOpenStartRealtimeMs: Long? = null,
    userPerfSizeTier: String = "unknown",
    openSession: EditorOpenSession? = null,
    onFrameCommitted: (Long) -> Unit = {},
) {
    controller.acceptInitialSnapshot(documentKey, initialTitle, initialContent, initialSelection)

    val currentOnTitleChanged = rememberUpdatedState(onTitleChanged)
    val currentOnContentChanged = rememberUpdatedState(onContentChanged)
    val currentOnUndoRedoChanged = rememberUpdatedState(onUndoRedoChanged)
    val currentOnUserInteraction = rememberUpdatedState(onUserInteraction)
    val currentOnFastScrollSourceScrolled = rememberUpdatedState(onFastScrollSourceScrolled)
    val currentOnInlineImageClicked = rememberUpdatedState(onInlineImageClicked)
    val currentOnFrameCommitted = rememberUpdatedState(onFrameCommitted)
    val currentOnFocusRequestHandled = rememberUpdatedState(onFocusRequestHandled)
    val handledFocusToken = remember { AtomicInteger(-1) }
    val lastAppliedUpdateSignature = remember { AtomicReference("") }
    val skippedUpdateCount = remember { AtomicInteger(0) }
    val viewRef = remember { AtomicReference<KardLeafNativeEditorView?>(null) }

    DisposableEffect(controller) {
        onDispose {
            viewRef.getAndSet(null)?.let { controller.detach(it) }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            KardLeafNativeEditorView(context).also { view ->
                KardLeafLog.d(EDITOR_TRACE_TAG, "native AndroidView factory key=$documentKey")
                KardLeafLog.d(OPEN_PATH_PROBE_TAG, "native AndroidView factory key=$documentKey thread=${Thread.currentThread().name}")
                viewRef.set(view)
            }
        },
        update = { view ->
            viewRef.set(view)
            val titleTextSizeSp = if (titleTextSize == TextUnit.Unspecified) 22f else titleTextSize.value
            val contentTextSizeSp = if (contentTextSize == TextUnit.Unspecified) 16f else contentTextSize.value
            val updateSignature = listOf(
                System.identityHashCode(view),
                documentKey,
                initialTitle.length,
                initialTitle.hashCode(),
                initialContent.length,
                initialContent.hashCode(),
                titleHint,
                contentHint,
                textColor.toArgb(),
                hintColor.toArgb(),
                titleTextSizeSp,
                contentTextSizeSp,
                contentLineHeightMultiplier,
                contentLetterSpacingSp,
                contentParagraphSpacingDp,
                contentFontFamily,
                showTitle,
                currentFolder,
                inlineImagePreviewEnabled,
                readOnly,
            ).joinToString(separator = "|")

            if (lastAppliedUpdateSignature.get() != updateSignature) {
                lastAppliedUpdateSignature.set(updateSignature)
                skippedUpdateCount.set(0)
                KardLeafLog.d(
                    EDITOR_TRACE_TAG,
                    "native AndroidView apply update key=$documentKey bound=${view.boundDocumentKey} initialTitleLen=${initialTitle.length} " +
                        "initialContentLen=${initialContent.length} showTitle=$showTitle focusToken=$requestFocusToken",
                )
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "native AndroidView update key=$documentKey bound=${view.boundDocumentKey} initialTitleLen=${initialTitle.length} " +
                        "initialContentLen=${initialContent.length} folder=$currentFolder readOnly=$readOnly showTitle=$showTitle",
                )
                val configureStartMs = SystemClock.elapsedRealtime()
                view.configureUserPerf(
                    userPerfOpenStartRealtimeMs,
                    userPerfSizeTier,
                    openSession,
                ) { currentOnFrameCommitted.value(it) }
                view.configure(
                    titleHint = titleHint,
                    contentHint = contentHint,
                    textColor = textColor.toArgb(),
                    hintColor = hintColor.toArgb(),
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
                    onTitleChanged = { currentOnTitleChanged.value() },
                    onContentChanged = { currentOnContentChanged.value() },
                    onSelectionChanged = { start, end -> controller.updateCachedSelection(start, end) },
                    onUndoRedoChanged = { currentOnUndoRedoChanged.value() },
                    onUserInteraction = { currentOnUserInteraction.value() },
                    onFastScrollSourceScrolled = { currentOnFastScrollSourceScrolled.value() },
                    onInlineImageClicked = { target -> currentOnInlineImageClicked.value(target) },
                )
                KardLeafLog.d(
                    EDITOR_TRACE_TAG,
                    "native AndroidView configure done key=$documentKey elapsed=${SystemClock.elapsedRealtime() - configureStartMs}ms",
                )
                val bindStartMs = SystemClock.elapsedRealtime()
                view.bindDocument(
                    documentKey = documentKey,
                    initialTitle = initialTitle,
                    initialContent = initialContent,
                    preferredSnapshot = controller.getCachedSnapshot(),
                )
                KardLeafLog.d(
                    EDITOR_TRACE_TAG,
                    "native AndroidView bindDocument done key=$documentKey bindElapsed=${SystemClock.elapsedRealtime() - bindStartMs}ms",
                )
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "native AndroidView bindDocument done key=$documentKey bindElapsed=${SystemClock.elapsedRealtime() - bindStartMs}ms " +
                        "initialContentLen=${initialContent.length}",
                )
                controller.attach(view, documentKey, initialTitle, initialContent)
            } else {
                val skipCount = skippedUpdateCount.incrementAndGet()
                if (skipCount == 1 || skipCount % 20 == 0) {
                    KardLeafLog.d(
                        EDITOR_TRACE_TAG,
                        "native AndroidView skip update key=$documentKey skipCount=$skipCount bound=${view.boundDocumentKey} " +
                            "focusToken=$requestFocusToken attached=${view.isAttachedToWindow} hasEditorFocus=${view.hasEditorFocus()}",
                    )
                }
            }

            if (handledFocusToken.get() != requestFocusToken) {
                handledFocusToken.set(requestFocusToken)
                KardLeafLog.d(
                    EDITOR_TRACE_TAG,
                    "native AndroidView focus token changed token=$requestFocusToken key=$documentKey " +
                        "attached=${view.isAttachedToWindow} hasEditorFocus=${view.hasEditorFocus()} ${nativeEditorMemorySummary()}",
                )
                if (requestFocusToken > 0) {
                    KardLeafLog.d(
                        EDITOR_TRACE_TAG,
                        "native focus token post scheduled token=$requestFocusToken key=$documentKey " +
                            "attached=${view.isAttachedToWindow} bound=${view.boundDocumentKey}",
                    )
                    view.post {
                        val currentHandledToken = handledFocusToken.get()
                        KardLeafLog.d(
                            EDITOR_TRACE_TAG,
                            "native focus token post run token=$requestFocusToken handled=$currentHandledToken key=$documentKey " +
                                "attached=${view.isAttachedToWindow} bound=${view.boundDocumentKey} hasEditorFocus=${view.hasEditorFocus()} " +
                                "${nativeEditorMemorySummary()}",
                        )
                        if (view.isAttachedToWindow && currentHandledToken == requestFocusToken) {
                            KardLeafLog.d(
                                EDITOR_TRACE_TAG,
                                "native focus token handled token=$requestFocusToken key=$documentKey ${nativeEditorMemorySummary()}",
                            )
                            view.focusContent()
                            currentOnFocusRequestHandled.value(requestFocusToken)
                        } else {
                            KardLeafLog.w(
                                EDITOR_TRACE_TAG,
                                "native focus token skipped token=$requestFocusToken handled=$currentHandledToken key=$documentKey " +
                                    "attached=${view.isAttachedToWindow} bound=${view.boundDocumentKey}",
                            )
                        }
                    }
                }
            }
        },
    )
}
