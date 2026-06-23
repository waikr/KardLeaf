package com.kangle.kardleaf.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_remarks",
    indices = [
        Index(
            name = "index_note_remarks_note_updated",
            value = ["noteId", "updatedAtMs"],
        ),
        Index(
            name = "index_note_remarks_updated",
            value = ["updatedAtMs"],
        ),
    ],
)
data class NoteRemarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: String,
    val content: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)
