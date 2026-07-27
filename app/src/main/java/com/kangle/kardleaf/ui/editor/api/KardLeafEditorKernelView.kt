package com.kangle.kardleaf.ui.editor.api

import androidx.compose.ui.text.TextRange

/** Lightweight metrics shared by the editor and preview fast-scroll overlay. */
data class EditorFastScrollMetrics(
    val canScroll: Boolean = false,
    val ratio: Float = 0f,
    val thumbFraction: Float = 1f,
)

/** The View-only contract used by KardLeaf's editor chrome and toolbars. */
internal interface KardLeafEditorKernelView {
    val boundDocumentKey: String?

    fun getTitleString(): String

    fun getContentString(): String

    fun contentLength(): Int

    fun getContentSelection(): TextRange

    fun getFastScrollMetrics(): EditorFastScrollMetrics

    fun fastScrollToRatio(ratio: Float)

    fun shouldReserveContentTouchForEditing(
        windowX: Float,
        windowY: Float,
        radiusPx: Float,
    ): Boolean

    fun insertAtContentCursor(
        prefix: String,
        suffix: String = "",
    )

    fun replaceContentSelection(insertion: String)

    fun replaceContent(
        newText: String,
        selection: TextRange? = null,
    )

    fun setContentSelection(
        start: Int,
        end: Int = start,
    )

    fun focusContent()

    fun scrollContentOffsetToVisible(offset: Int)

    fun scrollToProgress(progress: Float)

    fun highlightContentSearch(
        query: String,
        currentStart: Int,
        useRegex: Boolean,
        matchCase: Boolean,
    ): Int

    fun clearContentSearchHighlights()

    fun undoContent()

    fun redoContent()

    fun canUndoContent(): Boolean

    fun canRedoContent(): Boolean

    fun clearContentHistory()

    fun refreshContentInlineImagePreviews()

    fun executeCommand(
        command: String,
        args: List<Any>,
    ): Boolean = false

    fun dispose(clearText: Boolean = false)
}
