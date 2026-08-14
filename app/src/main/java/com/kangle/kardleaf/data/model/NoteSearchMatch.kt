package com.kangle.kardleaf.data.model

data class NoteSearchMatch(
    val noteId: String,
    val scope: String,
    val snippet: String,
    val startOffset: Int = -1,
)

data class NoteSearchOptions(
    val matchCase: Boolean = false,
    val useRegex: Boolean = false,
    val matchTitle: Boolean = true,
    val matchContent: Boolean = true,
    val tag: String? = null,
    val folder: String? = null,
) {
    val hasMetadataFilters: Boolean
        get() = tag != null || folder != null
}
