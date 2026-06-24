package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getAllActiveNotes(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getAllNotesWithArchive(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isTrashed = 1 ORDER BY lastModifiedMs DESC",
    )
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isArchived = 1 AND isTrashed = 0 ORDER BY lastModifiedMs DESC",
    )
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE folder = :folder AND isTrashed = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getNotesByFolder(folder: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE (folder = :folder OR folder LIKE :folderPrefix) AND isTrashed = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getNotesByFolderRecursive(
        folder: String,
        folderPrefix: String,
    ): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isFavorite = 1 AND isTrashed = 0 ORDER BY lastModifiedMs DESC",
    )
    fun getFavoriteNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE filePath = :filePath")
    suspend fun getNoteByPath(filePath: String): NoteEntity?

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE filePath = :filePath",
    )
    suspend fun getNoteShellByPath(filePath: String): NoteEntity?


    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE filePath = :recordKey OR recordId = :recordKey LIMIT 1",
    )
    suspend fun getNoteShellByRecordKey(recordKey: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE filePath IN (:filePaths)")
    suspend fun getNotesByPaths(filePaths: List<String>): List<NoteEntity>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE filePath IN (:filePaths)",
    )
    suspend fun getNoteShellsByPaths(filePaths: List<String>): List<NoteEntity>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE folder = :folder OR folder LIKE :folderPrefix",
    )
    suspend fun getNoteShellsInFolderTree(
        folder: String,
        folderPrefix: String,
    ): List<NoteEntity>

    @Query(
        """
        SELECT filePath FROM notes
        WHERE isTrashed = 0
        AND (
            title LIKE '%' || :query || '%'
            OR folder LIKE '%' || :query || '%'
            OR content LIKE '%' || :query || '%'
            OR yamlTags LIKE '%' || :query || '%'
        )
        """,
    )
    fun searchNoteIds(query: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE filePath = :filePath")
    suspend fun deleteNoteByPath(filePath: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT folder FROM notes WHERE isTrashed = 0 AND isArchived = 0")
    fun getAllLabels(): Flow<List<String>>

    @Query("UPDATE notes SET recordId = :recordId WHERE filePath = :filePath")
    suspend fun updateRecordId(
        filePath: String,
        recordId: String,
    )

    @Query("UPDATE notes SET isPinned = :isPinned WHERE filePath = :filePath")
    suspend fun updatePinStatus(
        filePath: String,
        isPinned: Boolean,
    )

    @Query("UPDATE notes SET isPinned = :isPinned WHERE filePath IN (:filePaths)")
    suspend fun updatePinStatuses(
        filePaths: List<String>,
        isPinned: Boolean,
    )

    @Query("UPDATE notes SET isFavorite = :isFavorite WHERE filePath IN (:filePaths)")
    suspend fun updateFavoriteStatuses(
        filePaths: List<String>,
        isFavorite: Boolean,
    )

    @Query("UPDATE notes SET isArchived = 1, isTrashed = 0, deletedAtMs = NULL WHERE filePath = :filePath")
    suspend fun archiveNote(filePath: String)

    @Query("UPDATE notes SET isArchived = 1, isTrashed = 0, deletedAtMs = NULL WHERE filePath IN (:filePaths)")
    suspend fun archiveNotes(filePaths: List<String>)

    @Query("UPDATE notes SET isTrashed = 1, deletedAtMs = :deletedAtMs WHERE filePath = :filePath")
    suspend fun trashNote(
        filePath: String,
        deletedAtMs: Long,
    )

    @Query("UPDATE notes SET isTrashed = 1, deletedAtMs = :deletedAtMs WHERE filePath IN (:filePaths)")
    suspend fun trashNotes(
        filePaths: List<String>,
        deletedAtMs: Long,
    )

    @Query("UPDATE notes SET isArchived = 0, isTrashed = 0, deletedAtMs = NULL WHERE filePath = :filePath")
    suspend fun restoreNote(filePath: String)

    @Query("SELECT COUNT(*) FROM notes WHERE folder = :folder")
    suspend fun countNotesInFolder(folder: String): Int

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun deleteAllTrashed()

    @Query("SELECT filePath FROM notes WHERE isTrashed = 1 AND deletedAtMs IS NOT NULL AND deletedAtMs < :cutoffMs")
    suspend fun getTrashedNotePathsBefore(cutoffMs: Long): List<String>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes",
    )
    suspend fun getAllNoteMetadataSync(): List<NoteMetadataEntity>

    @Query(
        """
        UPDATE notes
        SET fileName = :fileName,
            folder = :folder,
            title = :title,
            lastModifiedMs = :lastModifiedMs,
            createdAtMs = :createdAtMs,
            color = :color,
            reminder = :reminder,
            isPinned = :isPinned,
            isFavorite = :isFavorite,
            isArchived = :isArchived,
            isTrashed = :isTrashed,
            deletedAtMs = :deletedAtMs,
            firstImageReference = :firstImageReference,
            yamlTags = :yamlTags
        WHERE filePath = :filePath
        """,
    )
    suspend fun updateNoteMetadata(
        filePath: String,
        fileName: String,
        folder: String,
        title: String,
        lastModifiedMs: Long,
        createdAtMs: Long,
        color: Long,
        reminder: Long?,
        isPinned: Boolean,
        isFavorite: Boolean,
        isArchived: Boolean,
        isTrashed: Boolean,
        deletedAtMs: Long?,
        firstImageReference: String?,
        yamlTags: String,
    )

    @Query("SELECT yamlTags FROM notes WHERE yamlTags != '' AND isTrashed = 0")
    fun getAllYamlTagRows(): Flow<List<String>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE yamlTags LIKE '%' || :needle || '%' AND isTrashed = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getNotesByYamlTag(needle: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE yamlTags LIKE '%' || :needle || '%' AND isTrashed = 0")
    suspend fun getNotesByYamlTagSync(needle: String): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countAllNotes(): Int

    @Query("DELETE FROM notes WHERE filePath IN (:filePaths)")
    suspend fun deleteNotesByPaths(filePaths: List<String>)
}
