package com.kangle.kardleaf.data.model

import java.util.Date

data class NoteHistory(
    val id: Long,
    val noteId: String,
    val title: String,
    val content: String,
    val savedAt: Date,
    val contentLength: Int = content.length,
    val contentIsPreview: Boolean = false,
)
