package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kangle.kardleaf.data.model.NoteSearchMatch
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
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isTrashed = 1",
    )
    suspend fun getTrashedNoteShellsSync(): List<NoteEntity>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isArchived = 1 AND isTrashed = 0 ORDER BY lastModifiedMs DESC",
    )
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE folder = :folder AND isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getNotesByFolder(folder: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE (folder = :folder OR folder LIKE :folderPrefix) AND isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getNotesByFolderRecursive(
        folder: String,
        folderPrefix: String,
    ): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isFavorite = 1 AND isTrashed = 0 AND isArchived = 0 ORDER BY lastModifiedMs DESC",
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

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE filePath IN (:filePaths)",
    )
    suspend fun getNoteShellsByPaths(filePaths: List<String>): List<NoteEntity>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE folder = :folder OR substr(folder, 1, length(:folder) + 1) = :folder || '/'",
    )
    suspend fun getNoteShellsInFolderTree(folder: String): List<NoteEntity>

    @Query(
        """
        SELECT
            filePath AS noteId,
            CASE
                WHEN title LIKE '%' || :likeQuery || '%' ESCAPE '\' THEN '标题'
                ELSE '正文'
            END AS scope,
            CASE
                WHEN title LIKE '%' || :likeQuery || '%' ESCAPE '\' THEN title
                WHEN content LIKE '%' || :likeQuery || '%' ESCAPE '\' THEN
                    (CASE WHEN instr(lower(content), lower(:query)) > 61 THEN '...' ELSE '' END) ||
                    trim(replace(replace(substr(content, max(instr(lower(content), lower(:query)) - 60, 1), 60 + length(:query) + 90), char(13), ' '), char(10), ' ')) ||
                    (CASE WHEN instr(lower(content), lower(:query)) + length(:query) + 90 <= length(content) THEN '...' ELSE '' END)
                ELSE ''
            END AS snippet,
            CASE
                WHEN content LIKE '%' || :likeQuery || '%' ESCAPE '\' THEN instr(lower(content), lower(:query)) - 1
                ELSE -1
            END AS startOffset
        FROM notes
        WHERE isTrashed = 0
        AND (
            title LIKE '%' || :likeQuery || '%' ESCAPE '\'
            OR content LIKE '%' || :likeQuery || '%' ESCAPE '\'
        )
        ORDER BY lastModifiedMs DESC
        LIMIT :limit
        """,
    )
    fun searchNoteMatches(
        query: String,
        likeQuery: String,
        limit: Int,
    ): Flow<List<NoteSearchMatch>>

    @Query("SELECT * FROM notes ORDER BY lastModifiedMs DESC")
    fun getAllSearchableNotes(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes ORDER BY lastModifiedMs DESC",
    )
    fun getAllSearchableNoteShells(): Flow<List<NoteEntity>>

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

    @Query("SELECT DISTINCT folder FROM notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY folder ASC")
    suspend fun getActiveFoldersSync(): List<String>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, lastModifiedMs DESC LIMIT :limit",
    )
    suspend fun getWidgetRecentNoteShells(limit: Int): List<NoteEntity>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE (folder = :folder OR folder LIKE :folderPrefix) AND isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, lastModifiedMs DESC LIMIT :limit",
    )
    suspend fun getWidgetNoteShellsByFolder(
        folder: String,
        folderPrefix: String,
        limit: Int,
    ): List<NoteEntity>

    @Query("UPDATE notes SET recordId = :recordId WHERE filePath = :filePath")
    suspend fun updateRecordId(
        filePath: String,
        recordId: String,
    )

    @Query(
        """
        UPDATE notes
        SET recordId = CASE WHEN recordId = filePath THEN :newPath ELSE recordId END,
            filePath = :newPath,
            fileName = :newFileName,
            title = :newTitle,
            lastModifiedMs = :lastModifiedMs
        WHERE filePath = :oldPath
        """,
    )
    suspend fun renameNotePath(
        oldPath: String,
        newPath: String,
        newFileName: String,
        newTitle: String,
        lastModifiedMs: Long,
    ): Int

    @Query(
        """
        UPDATE notes
        SET recordId = CASE WHEN recordId = filePath THEN :newPath ELSE recordId END,
            filePath = :newPath,
            folder = :newFolder,
            lastModifiedMs = :lastModifiedMs
        WHERE filePath = :oldPath AND isTrashed = 0 AND isArchived = 0
        """,
    )
    suspend fun moveNotePath(
        oldPath: String,
        newPath: String,
        newFolder: String,
        lastModifiedMs: Long,
    ): Int

    @Query(
        """
        UPDATE notes
        SET recordId = CASE
                WHEN recordId = filePath THEN :newFolder || substr(filePath, length(:oldFolder) + 1)
                ELSE recordId
            END,
            filePath = :newFolder || substr(filePath, length(:oldFolder) + 1),
            folder = :newFolder || substr(folder, length(:oldFolder) + 1)
        WHERE folder = :oldFolder OR substr(folder, 1, length(:oldFolder) + 1) = :oldFolder || '/'
        """,
    )
    suspend fun renameFolderPaths(
        oldFolder: String,
        newFolder: String,
    ): Int

    @Query(
        """
        UPDATE notes
        SET recordId = CASE
                WHEN recordId = filePath THEN :newFolder || substr(filePath, length(:oldFolder) + 1)
                ELSE recordId
            END,
            filePath = :newFolder || substr(filePath, length(:oldFolder) + 1),
            folder = :newFolder || substr(folder, length(:oldFolder) + 1)
        WHERE isTrashed = 0
          AND (folder = :oldFolder OR substr(folder, 1, length(:oldFolder) + 1) = :oldFolder || '/')
        """,
    )
    suspend fun moveActiveFolderPaths(
        oldFolder: String,
        newFolder: String,
    ): Int

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

    @Query(
        """
        UPDATE notes
        SET filePath = :trashPath,
            fileName = :trashFileName,
            isArchived = 0,
            isTrashed = 1,
            deletedAtMs = :deletedAtMs
        WHERE filePath = :sourcePath
        """,
    )
    suspend fun moveNoteToTrashPath(
        sourcePath: String,
        trashPath: String,
        trashFileName: String,
        deletedAtMs: Long,
    ): Int

    @Query("UPDATE notes SET isArchived = 0, isTrashed = 0, deletedAtMs = NULL WHERE filePath = :filePath")
    suspend fun restoreNote(filePath: String)

    @Query(
        """
        UPDATE notes
        SET filePath = :restoredPath,
            fileName = :restoredFileName,
            isArchived = 0,
            isTrashed = 0,
            deletedAtMs = NULL
        WHERE filePath = :trashPath
        """,
    )
    suspend fun restoreNoteFromTrashPath(
        trashPath: String,
        restoredPath: String,
        restoredFileName: String,
    ): Int

    @Query("SELECT COUNT(*) FROM notes WHERE folder = :folder OR folder LIKE :folder || '/%'")
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

    @Query("SELECT yamlTags FROM notes WHERE yamlTags != '' AND isTrashed = 0 AND isArchived = 0")
    fun getAllYamlTagRows(): Flow<List<String>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, substr(contentPreview, 1, 200) AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE yamlTags LIKE '%' || :needle || '%' AND isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getNotesByYamlTag(needle: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, recordId, fileName, folder, title, substr(contentPreview, 1, 200) AS contentPreview, '' AS content, lastModifiedMs, createdAtMs, color, reminder, isPinned, isFavorite, isArchived, isTrashed, deletedAtMs, firstImageReference, yamlTags FROM notes WHERE yamlTags LIKE '%' || :needle || '%' AND isTrashed = 0 AND isArchived = 0",
    )
    suspend fun getNotesByYamlTagSync(needle: String): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countAllNotes(): Int

    @Query("DELETE FROM notes WHERE filePath IN (:filePaths)")
    suspend fun deleteNotesByPaths(filePaths: List<String>)
}
