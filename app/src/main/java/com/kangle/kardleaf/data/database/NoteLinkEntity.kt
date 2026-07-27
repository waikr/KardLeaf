package com.kangle.kardleaf.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Derived wikilink occurrence. Markdown remains the source of truth. */
@Entity(
    tableName = "note_links",
    indices = [
        Index(name = "index_note_links_source", value = ["sourceRecordId", "sourcePath"]),
        Index(name = "index_note_links_target", value = ["targetRecordId", "targetPath"]),
        Index(name = "index_note_links_target_normalized", value = ["targetNormalized"]),
    ],
)
data class NoteLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceRecordId: String,
    val sourcePath: String,
    val targetRaw: String,
    val targetNormalized: String,
    val targetRecordId: String? = null,
    val targetPath: String? = null,
    val alias: String? = null,
    val heading: String? = null,
    val blockId: String? = null,
    val startOffset: Int,
    val endOffset: Int,
    val contextSnippet: String,
    val resolutionStatus: String,
)

object NoteLinkResolutionStatus {
    const val RESOLVED = "RESOLVED"
    const val UNRESOLVED = "UNRESOLVED"
    const val AMBIGUOUS = "AMBIGUOUS"
}
