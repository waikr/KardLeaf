package com.kangle.kardleaf.data.repository

import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteSearchMatch
import kotlinx.coroutines.flow.Flow

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

    fun getNoteHistory(noteId: String): Flow<List<NoteHistory>>

    fun searchHistoryPreview(query: String): Flow<List<NoteHistory>>

    fun searchNoteMatches(query: String): Flow<List<NoteSearchMatch>>

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

    suspend fun refreshNotes()
}
