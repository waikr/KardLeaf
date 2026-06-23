package com.kangle.kardleaf.data.model

import java.io.File
import java.util.Date

data class Note(
    val file: File,
    val title: String,
    val content: String,
    val lastModified: Date,
    val createdAt: Date = lastModified,
    val color: Long,
    val reminder: Long? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val deletedAt: Date? = null,
    val contentPreview: String = content,
    val firstImageReference: String? = null,
    val tags: List<String> = emptyList(),
) {
    val folder: String
        get() = file.parent ?: ""

    val id: String
        get() = file.path
}
