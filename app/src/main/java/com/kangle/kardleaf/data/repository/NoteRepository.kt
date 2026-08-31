package com.kangle.kardleaf.data.repository

import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteSearchMatch
import com.kangle.kardleaf.data.model.NoteSearchOptions
import kotlinx.coroutines.flow.Flow

data class RefreshResult(
    val generation: Long,
    val addedPaths: Set<String> = emptySet(),
    val modifiedPaths: Set<String> = emptySet(),
    val deletedPaths: Set<String> = emptySet(),
    val success: Boolean = true,
) {
    val changed: Boolean
        get() = addedPaths.isNotEmpty() || modifiedPaths.isNotEmpty() || deletedPaths.isNotEmpty()
}

data class MergeNotesOptions(
    val includeTitles: Boolean = false,
    val separator: String = DEFAULT_SEPARATOR,
    val moveSourcesToTrash: Boolean = false,
    val mergeMetadata: Boolean = false,
) {
    companion object {
        const val DEFAULT_SEPARATOR = "\n\n"
    }
}

data class MergeNotesResult(
    val targetPath: String? = null,
    val sourceCount: Int = 0,
    val handledSourceCount: Int = 0,
    val failedSourceCount: Int = 0,
)

internal fun mergeMarkdownBlocks(
    notes: List<Note>,
    includeTitles: Boolean,
    separator: String,
): String = notes.joinToString(separator) { note ->
    buildString {
        if (includeTitles) {
            append("# ")
            append(note.title)
            append("\n\n")
        }
        append(note.content.trimEnd('\r', '\n'))
    }
}

interface NoteRepository {
    fun getAllNotes(): Flow<List<Note>>

    fun getAllNotesWithArchive(): Flow<List<Note>>

    fun getFavoriteNotes(): Flow<List<Note>>

    suspend fun getNote(id: String): Note?

    suspend fun saveNote(
        note: Note,
        oldFile: java.io.File? = null,
        saveHistory: Boolean = false,
    ): String

    suspend fun mergeNotes(
        noteIds: List<String>,
        options: MergeNotesOptions = MergeNotesOptions(),
    ): MergeNotesResult

    fun getNoteHistory(noteId: String): Flow<List<NoteHistory>>

    fun searchHistoryPreview(query: String): Flow<List<NoteHistory>>

    fun searchNoteMatches(
        query: String,
        options: NoteSearchOptions = NoteSearchOptions(),
    ): Flow<List<NoteSearchMatch>>

    suspend fun deleteNoteHistory(historyId: Long)

    suspend fun restoreNoteHistory(
        noteId: String,
        historyId: Long,
    ): String

    suspend fun getHistoryCleanupPreview(keep: Int): List<HistoryCleanupPreview>

    suspend fun cleanupOldHistoryVersions()

    suspend fun deleteNote(id: String)

    suspend fun deleteNotes(noteIds: List<String>)

    suspend fun archiveNote(id: String)

    suspend fun archiveNotes(noteIds: List<String>)

    suspend fun togglePinStatus(
        noteIds: List<String>,
        isPinned: Boolean,
    )

    suspend fun toggleFavoriteStatus(
        noteIds: List<String>,
        isFavorite: Boolean,
    )

    suspend fun restoreNote(id: String)

    suspend fun moveNotes(
        notes: List<Note>,
        targetFolder: String,
    )

    suspend fun setRootFolder(
        uriString: String,
        scanImmediately: Boolean = true,
    )

    fun getLabels(): Flow<List<String>>

    suspend fun createLabel(name: String): Boolean

    suspend fun renameLabel(
        oldName: String,
        newName: String,
    ): Boolean

    suspend fun deleteLabel(name: String): Boolean

    suspend fun deleteLabelWithContents(name: String): Boolean

    suspend fun emptyTrash()

    suspend fun cleanupExpiredTrash(olderThanDays: Int)

    suspend fun refreshNotes(): RefreshResult
}
