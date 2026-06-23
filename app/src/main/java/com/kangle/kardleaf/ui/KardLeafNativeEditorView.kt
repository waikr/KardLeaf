package com.kangle.kardleaf.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.SystemClock
import android.util.Log
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import androidx.core.widget.NestedScrollView
import io.noties.markwon.Markwon
import io.noties.markwon.editor.MarkwonEditor
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import io.noties.markwon.editor.handler.EmphasisEditHandler
import io.noties.markwon.editor.handler.StrongEmphasisEditHandler
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EDITOR_TRACE_TAG = "KardLeafEditorTrace"

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
    internal var editorView: KardLeafNativeEditorView? = null
        private set

    private var documentKey: String? = null
    private var lastLoadedTitle: String = ""
    private var lastLoadedContent: String = ""
    private var cachedTitle: String = ""
    private var cachedContent: String = ""
    private var cachedSelection: TextRange = TextRange(0, 0)

    fun acceptInitialSnapshot(
        documentKey: String,
        initialTitle: String,
        initialContent: String,
    ) {
        val isDifferentDocument = this.documentKey != documentKey
        if (editorView != null && !isDifferentDocument) return

        val isStillAtLoadedText = cachedTitle == lastLoadedTitle && cachedContent == lastLoadedContent
        if (isDifferentDocument || isStillAtLoadedText) {
            this.documentKey = documentKey
            lastLoadedTitle = initialTitle
            lastLoadedContent = initialContent
            cachedTitle = initialTitle
            cachedContent = initialContent
            cachedSelection = TextRange(initialContent.length, initialContent.length)
            if (isDifferentDocument) {
                Log.d(
                    EDITOR_TRACE_TAG,
                    "controller accept new document key=$documentKey titleLen=${initialTitle.length} contentLen=${initialContent.length}",
                )
            }
        }
    }

    internal fun attach(
        view: KardLeafNativeEditorView,
        documentKey: String,
        loadedTitle: String,
        loadedContent: String,
    ) {
        editorView = view
        this.documentKey = documentKey
        lastLoadedTitle = loadedTitle
        lastLoadedContent = loadedContent
        Log.d(
            EDITOR_TRACE_TAG,
            "controller attach key=$documentKey loadedTitleLen=${loadedTitle.length} loadedContentLen=${loadedContent.length} " +
                "viewTitleLen=${view.titleEditText.length()} viewContentLen=${view.contentLength()}",
        )
    }

    internal fun detach(view: KardLeafNativeEditorView) {
        if (editorView === view) {
            if (isCurrentAttachedView(view)) {
                captureFromView(view)
                Log.d(
                    EDITOR_TRACE_TAG,
                    "controller detach key=$documentKey cachedTitleLen=${cachedTitle.length} cachedContentLen=${cachedContent.length} selection=$cachedSelection",
                )
            } else {
                Log.w(
                    EDITOR_TRACE_TAG,
                    "controller detach ignored stale view currentKey=$documentKey viewKey=${view.boundDocumentKey}",
                )
            }
            editorView = null
            view.dispose()
        }
    }

    private fun captureFromView(view: KardLeafNativeEditorView) {
        cachedTitle = view.getTitleString()
        cachedContent = view.getContentString()
        cachedSelection = view.getContentSelection()
    }

    private fun isCurrentAttachedView(view: KardLeafNativeEditorView): Boolean =
        view.boundDocumentKey == documentKey

    private fun currentEditorView(): KardLeafNativeEditorView? =
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
        }
    }

    fun replaceSelection(insertion: String) {
        val attached = currentEditorView()
        Log.d(
            EDITOR_TRACE_TAG,
            "controller replaceSelection before key=$documentKey attached=${attached != null} " +
                "cachedLen=${cachedContent.length} cachedSelection=${cachedSelection.start}..${cachedSelection.end} insertionLen=${insertion.length}",
        )
        if (attached != null) {
            attached.replaceContentSelection(insertion)
            cachedSelection = attached.getContentSelection()
            Log.d(
                EDITOR_TRACE_TAG,
                "controller replaceSelection after attached key=$documentKey viewLen=${attached.contentLength()} " +
                    "cachedSelection=${cachedSelection.start}..${cachedSelection.end}",
            )
        } else {
            val start = minOf(cachedSelection.start, cachedSelection.end).coerceIn(0, cachedContent.length)
            val end = maxOf(cachedSelection.start, cachedSelection.end).coerceIn(0, cachedContent.length)
            cachedContent = cachedContent.substring(0, start) + insertion + cachedContent.substring(end)
            val cursor = start + insertion.length
            cachedSelection = TextRange(cursor, cursor)
            Log.d(
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
        if (attached != null) {
            attached.replaceContent(newText, selection)
            cachedSelection = attached.getContentSelection()
        } else {
            cachedContent = newText
            cachedSelection = selection ?: TextRange(newText.length, newText.length)
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

    fun highlightSearch(query: String): Int = currentEditorView()?.highlightContentSearch(query) ?: 0

    fun clearSearchHighlights() {
        currentEditorView()?.clearContentSearchHighlights()
    }

    fun undo() {
        currentEditorView()?.undoContent()
    }

    fun redo() {
        currentEditorView()?.redoContent()
    }

    fun canUndo(): Boolean = currentEditorView()?.canUndoContent() ?: false

    fun canRedo(): Boolean = currentEditorView()?.canRedoContent() ?: false

    fun clearHistory() {
        currentEditorView()?.clearContentHistory()
    }
}

class KardLeafNativeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
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

    var boundDocumentKey: String? = null
        private set
    private var loadedTitle: String = ""
    private var loadedContent: String = ""

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
            setPadding(dp(12), dp(6), dp(12), dp(24))
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

        titleEditText.setOnTouchListener { _, event -> notifyUserInteractionOnTouch(event) }
        contentEditText.setOnTouchListener { _, event -> notifyUserInteractionOnTouch(event) }
        scrollView.setOnTouchListener { _, event -> notifyUserInteractionOnTouch(event) }
        scrollView.setOnScrollChangeListener { _, _, _, _, _ ->
            scrollChangedCallback?.invoke()
        }

        editorColumn.addView(titleEditText)
        editorColumn.addView(contentEditText)
        scrollView.addView(editorColumn)
        addView(scrollView)
    }

    private fun notifyUserInteractionOnTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
            userInteractionCallback?.invoke()
        }
        return false
    }

    fun configure(
        titleHint: String,
        contentHint: String,
        textColor: Int,
        hintColor: Int,
        titleTextSizeSp: Float,
        contentTextSizeSp: Float,
        showTitle: Boolean,
        currentFolder: String,
        onTitleChanged: () -> Unit,
        onContentChanged: () -> Unit,
        onSelectionChanged: (Int, Int) -> Unit,
        onUndoRedoChanged: () -> Unit,
        onUserInteraction: () -> Unit,
        onFastScrollSourceScrolled: () -> Unit,
    ) {
        titleChangedCallback = onTitleChanged
        userInteractionCallback = onUserInteraction
        scrollChangedCallback = onFastScrollSourceScrolled
        titleEditText.hint = titleHint
        titleEditText.setTextColor(textColor)
        titleEditText.setHintTextColor(hintColor)
        titleEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleTextSizeSp)
        titleEditText.visibility = if (showTitle) View.VISIBLE else View.GONE

        contentEditText.hint = contentHint
        contentEditText.setTextColor(textColor)
        contentEditText.setHintTextColor(hintColor)
        contentEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, contentTextSizeSp)
        contentEditText.configureInlineImagePreviewFolder(currentFolder)
        contentEditText.kardLeafContentCallback = onContentChanged
        contentEditText.kardLeafSelectionCallback = onSelectionChanged
        contentEditText.kardLeafUndoRedoCallback = onUndoRedoChanged
    }

    fun bindDocument(
        documentKey: String,
        initialTitle: String,
        initialContent: String,
        preferredSnapshot: KardLeafEditorSnapshot,
    ) {
        val startMs = SystemClock.elapsedRealtime()
        if (boundDocumentKey == null) {
            Log.d(
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
            Log.d(EDITOR_TRACE_TAG, "bindDocument first done key=$documentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
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
        Log.d(
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
                Log.d(EDITOR_TRACE_TAG, "bindDocument metadata only key=$documentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            }
            isEditorStillAtLoadedText || isMissingInitialText || canSafelyReloadDifferentDocument -> {
                val previousSelection = getContentSelection()
                setInitialSnapshot(initialTitle, initialContent, previousSelection)
                boundDocumentKey = documentKey
                loadedTitle = initialTitle
                loadedContent = initialContent
                Log.d(EDITOR_TRACE_TAG, "bindDocument reloaded key=$documentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            }
            else -> {
                // Preserve newer local typing if repository emissions race with the editor.
                boundDocumentKey = documentKey
                loadedTitle = initialTitle
                loadedContent = initialContent
                Log.w(EDITOR_TRACE_TAG, "bindDocument preserve local text key=$documentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            }
        }
    }

    fun getTitleString(): String = titleEditText.text?.toString().orEmpty()

    fun getContentString(): String = contentEditText.getTextString()

    fun contentLength(): Int = contentEditText.length()

    fun getContentSelection(): TextRange = contentEditText.getSelectionRange()

    fun getFastScrollMetrics(): EditorFastScrollMetrics {
        val maxScrollY = maxScrollY()
        if (scrollView.height <= 0 || maxScrollY <= 0) return EditorFastScrollMetrics()
        val contentHeight = scrollView.height + maxScrollY
        return EditorFastScrollMetrics(
            canScroll = true,
            ratio = (scrollView.scrollY.toFloat() / maxScrollY).coerceIn(0f, 1f),
            thumbFraction = (scrollView.height.toFloat() / contentHeight).coerceIn(0f, 1f),
        )
    }

    fun fastScrollToRatio(ratio: Float) {
        val maxScrollY = maxScrollY()
        if (maxScrollY <= 0) return
        val targetScrollY = (ratio.coerceIn(0f, 1f) * maxScrollY).roundToInt()
        scrollView.scrollTo(0, targetScrollY.coerceIn(0, maxScrollY))
    }

    private fun maxScrollY(): Int {
        val contentHeight = scrollView.getChildAt(0)?.height ?: 0
        return (contentHeight - scrollView.height).coerceAtLeast(0)
    }

    fun shouldReserveContentTouchForEditing(
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
        Log.d(
            EDITOR_TRACE_TAG,
            "setInitialSnapshot key=$boundDocumentKey titleLen=${title.length} contentLen=${content.length} selection=$selection",
        )
        programmaticTitleChange.set(true)
        try {
            titleEditText.setText(title)
            titleEditText.setSelection(title.length.coerceIn(0, titleEditText.length()))
        } finally {
            programmaticTitleChange.set(false)
        }
        val targetSelection = selection ?: TextRange(content.length, content.length)
        contentEditText.setInitialText(content, targetSelection)
        Log.d(
            EDITOR_TRACE_TAG,
            "setInitialSnapshot done key=$boundDocumentKey viewTitleLen=${titleEditText.length()} viewContentLen=${contentEditText.length()} " +
                "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
    }

    fun insertAtContentCursor(
        prefix: String,
        suffix: String = "",
    ) {
        contentEditText.insertAtCursor(prefix, suffix)
    }

    fun replaceContentSelection(insertion: String) {
        val beforeSelection = getContentSelection()
        val beforeLen = contentLength()
        Log.d(
            EDITOR_TRACE_TAG,
            "native replaceContentSelection before key=$boundDocumentKey len=$beforeLen " +
                "selection=${beforeSelection.start}..${beforeSelection.end} insertionLen=${insertion.length}",
        )
        contentEditText.replaceSelection(insertion)
        val afterSelection = getContentSelection()
        Log.d(
            EDITOR_TRACE_TAG,
            "native replaceContentSelection after key=$boundDocumentKey len=${contentLength()} " +
                "selection=${afterSelection.start}..${afterSelection.end}",
        )
    }

    fun replaceContent(
        newText: String,
        selection: TextRange? = null,
    ) {
        contentEditText.replaceAll(newText, selection)
    }

    fun setContentSelection(
        start: Int,
        end: Int = start,
    ) {
        contentEditText.setSelectionRange(start, end)
    }

    fun focusContent() {
        val startMs = SystemClock.elapsedRealtime()
        val result = contentEditText.requestFocus()
        Log.d(
            EDITOR_TRACE_TAG,
            "native focusContent requested result=$result attached=$isAttachedToWindow hasFocus=${contentEditText.hasFocus()} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
    }

    fun scrollContentOffsetToVisible(offset: Int) {
        contentEditText.post {
            val layout = contentEditText.layout ?: return@post
            val safeOffset = offset.coerceIn(0, contentEditText.length())
            val line = layout.getLineForOffset(safeOffset)
            val targetY = contentEditText.top + layout.getLineTop(line)
            val viewportBias = (height * 0.25f).toInt()
            scrollView.smoothScrollTo(0, (targetY - viewportBias).coerceAtLeast(0))
        }
    }

    fun scrollToProgress(progress: Float) {
        scrollView.post {
            val contentHeight = scrollView.getChildAt(0)?.height ?: return@post
            val maxScrollY = (contentHeight - scrollView.height).coerceAtLeast(0)
            val targetY = (maxScrollY * progress.coerceIn(0f, 1f)).roundToInt()
            scrollView.scrollTo(0, targetY.coerceIn(0, maxScrollY))
        }
    }

    fun highlightContentSearch(query: String): Int = contentEditText.highlightSearch(query)

    fun clearContentSearchHighlights() {
        contentEditText.clearSearchHighlights()
    }

    fun undoContent() {
        contentEditText.undo()
    }

    fun redoContent() {
        contentEditText.redo()
    }

    fun canUndoContent(): Boolean = contentEditText.canUndo()

    fun canRedoContent(): Boolean = contentEditText.canRedo()

    fun clearContentHistory() {
        contentEditText.clearHistory()
    }

    fun hasEditorFocus(): Boolean = titleEditText.hasFocus() || contentEditText.hasFocus()

    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        titleChangedCallback = null
        userInteractionCallback = null
        scrollChangedCallback = null
        titleEditText.removeTextChangedListener(titleWatcher)
        contentEditText.configureMarkdownWatcher(null)
        contentEditText.releaseInlineImagePreviews()
        contentEditText.kardLeafContentCallback = null
        contentEditText.kardLeafSelectionCallback = null
        contentEditText.kardLeafUndoRedoCallback = null
        markdownExecutor.shutdownNow()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
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
    modifier: Modifier = Modifier,
    titleHint: String = "",
    contentHint: String = "",
    textColor: Color,
    hintColor: Color,
    titleTextSize: TextUnit = 22.sp,
    contentTextSize: TextUnit = 16.sp,
    requestFocusToken: Int = 0,
    showTitle: Boolean = true,
    currentFolder: String = "",
) {
    controller.acceptInitialSnapshot(documentKey, initialTitle, initialContent)

    val currentOnTitleChanged = rememberUpdatedState(onTitleChanged)
    val currentOnContentChanged = rememberUpdatedState(onContentChanged)
    val currentOnUndoRedoChanged = rememberUpdatedState(onUndoRedoChanged)
    val currentOnUserInteraction = rememberUpdatedState(onUserInteraction)
    val currentOnFastScrollSourceScrolled = rememberUpdatedState(onFastScrollSourceScrolled)
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
                Log.d(EDITOR_TRACE_TAG, "native AndroidView factory key=$documentKey")
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
                showTitle,
                currentFolder,
            ).joinToString(separator = "|")

            if (lastAppliedUpdateSignature.get() != updateSignature) {
                lastAppliedUpdateSignature.set(updateSignature)
                skippedUpdateCount.set(0)
                Log.d(
                    EDITOR_TRACE_TAG,
                    "native AndroidView apply update key=$documentKey bound=${view.boundDocumentKey} initialTitleLen=${initialTitle.length} " +
                        "initialContentLen=${initialContent.length} showTitle=$showTitle focusToken=$requestFocusToken",
                )
                view.configure(
                    titleHint = titleHint,
                    contentHint = contentHint,
                    textColor = textColor.toArgb(),
                    hintColor = hintColor.toArgb(),
                    titleTextSizeSp = titleTextSizeSp,
                    contentTextSizeSp = contentTextSizeSp,
                    showTitle = showTitle,
                    currentFolder = currentFolder,
                    onTitleChanged = { currentOnTitleChanged.value() },
                    onContentChanged = { currentOnContentChanged.value() },
                    onSelectionChanged = { start, end -> controller.updateCachedSelection(start, end) },
                    onUndoRedoChanged = { currentOnUndoRedoChanged.value() },
                    onUserInteraction = { currentOnUserInteraction.value() },
                    onFastScrollSourceScrolled = { currentOnFastScrollSourceScrolled.value() },
                )
                view.bindDocument(
                    documentKey = documentKey,
                    initialTitle = initialTitle,
                    initialContent = initialContent,
                    preferredSnapshot = controller.getCachedSnapshot(),
                )
                controller.attach(view, documentKey, initialTitle, initialContent)
            } else {
                val skipCount = skippedUpdateCount.incrementAndGet()
                if (skipCount == 1 || skipCount % 20 == 0) {
                    Log.d(
                        EDITOR_TRACE_TAG,
                        "native AndroidView skip update key=$documentKey skipCount=$skipCount bound=${view.boundDocumentKey} " +
                            "focusToken=$requestFocusToken attached=${view.isAttachedToWindow} hasEditorFocus=${view.hasEditorFocus()}",
                    )
                }
            }

            if (handledFocusToken.get() != requestFocusToken) {
                handledFocusToken.set(requestFocusToken)
                Log.d(
                    EDITOR_TRACE_TAG,
                    "native AndroidView focus token changed token=$requestFocusToken key=$documentKey " +
                        "attached=${view.isAttachedToWindow} hasEditorFocus=${view.hasEditorFocus()}",
                )
                if (requestFocusToken > 0) {
                    view.post {
                        if (view.isAttachedToWindow && handledFocusToken.get() == requestFocusToken) {
                            Log.d(EDITOR_TRACE_TAG, "native focus token handled token=$requestFocusToken key=$documentKey")
                            view.focusContent()
                        }
                    }
                }
            }
        },
    )
}
