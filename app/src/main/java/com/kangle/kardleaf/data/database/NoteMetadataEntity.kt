package com.kangle.kardleaf.data.database

data class NoteMetadataEntity(
    val filePath: String,
    val recordId: String,
    val fileName: String,
    val folder: String,
    val title: String,
    val contentPreview: String,
    val lastModifiedMs: Long,
    val createdAtMs: Long,
    val color: Long,
    val reminder: Long?,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val isArchived: Boolean,
    val isTrashed: Boolean,
    val deletedAtMs: Long?,
    val firstImageReference: String?,
    val yamlTags: String,
)
