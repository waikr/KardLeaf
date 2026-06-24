package com.kangle.kardleaf.data.model

data class NoteRecordSummary(
    val noteId: String,
    val title: String,
    val contentPreview: String = "",
    val recordCount: Int,
    val updatedAtMs: Long,
)
