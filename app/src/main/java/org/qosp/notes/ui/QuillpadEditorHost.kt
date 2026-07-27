package org.qosp.notes.ui

import androidx.fragment.app.Fragment

/** Host actions used by the retained Quillpad editor in either activity or embedded mode. */
interface QuillpadEditorHost {
    suspend fun resolvePreviewMarkdown(markdown: String): String

    fun onEditorContentReady(source: String)

    fun openTags()

    fun openHistory()

    fun openRemarks()

    fun openDrawing(selection: Pair<Int, Int>, reference: String?)
}

internal fun Fragment.findQuillpadEditorHost(): QuillpadEditorHost? {
    var current = parentFragment
    while (current != null) {
        if (current is QuillpadEditorHost) return current
        current = current.parentFragment
    }
    return activity as? QuillpadEditorHost
}
