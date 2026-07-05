package com.kangle.kardleaf.data.model

data class NoteSearchMatch(
    val noteId: String,
    val scope: String,
    val snippet: String,
    val startOffset: Int = -1,
)
