package com.kangle.kardleaf.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing a Note.
 * This is the "cached" version of what's on disk.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(
            name = "index_notes_active_sort",
            value = ["isTrashed", "isArchived", "isPinned", "lastModifiedMs"],
        ),
        Index(
            name = "index_notes_all_with_archive_sort",
            value = ["isTrashed", "isPinned", "lastModifiedMs"],
        ),
        Index(
            name = "index_notes_trash_sort",
            value = ["isTrashed", "lastModifiedMs"],
        ),
        Index(
            name = "index_notes_archive_sort",
            value = ["isArchived", "isTrashed", "lastModifiedMs"],
        ),
        Index(
            name = "index_notes_folder_sort",
            value = ["folder", "isTrashed", "isPinned", "lastModifiedMs"],
        ),
        Index(
            name = "index_notes_favorite_sort",
            value = ["isFavorite", "isTrashed", "lastModifiedMs"],
        ),
        Index(
            name = "index_notes_labels",
            value = ["isTrashed", "isArchived", "folder"],
        ),
        Index(
            name = "index_notes_record_id",
            value = ["recordId"],
        ),
    ],
)
data class NoteEntity(
    // Unique ID: "Label/filename.md"
    @PrimaryKey
    val filePath: String,
    // Stable id from YAML frontmatter, used to keep remarks/history after rename/move
    val recordId: String = filePath,
    // "MyNote.md"
    val fileName: String,
    // "Work", "Life", etc.
    val folder: String,
    val title: String,
    // First ~200 chars for dashboard
    val contentPreview: String,
    // Full content
    val content: String,
    // Timestamp in millis
    val lastModifiedMs: Long,
    val createdAtMs: Long = lastModifiedMs,
    val color: Long,
    val reminder: Long? = null,
    val isPinned: Boolean,
    val isFavorite: Boolean = false,
    val isArchived: Boolean,
    val isTrashed: Boolean,
    val deletedAtMs: Long? = null,
    val firstImageReference: String? = null,
    val yamlTags: String = "",
)
