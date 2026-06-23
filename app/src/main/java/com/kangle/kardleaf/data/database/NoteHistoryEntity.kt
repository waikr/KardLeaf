package com.kangle.kardleaf.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_history",
    indices = [
        Index(
            name = "index_note_history_note_saved",
            value = ["noteId", "savedAtMs"],
        ),
        Index(
            name = "index_note_history_saved",
            value = ["savedAtMs"],
        ),
    ],
)
data class NoteHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: String,
    val title: String,
    val content: String,
    val savedAtMs: Long,
)
