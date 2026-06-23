package com.kangle.kardleaf.data.model

data class NoteRemark(
    val id: Long,
    val noteId: String,
    val content: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
