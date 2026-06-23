package com.kangle.kardleaf.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "privacy_notes")
data class PrivacyNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val updatedAtMs: Long,
)
