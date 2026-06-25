package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kangle.kardleaf.data.model.NoteRecordSummary
import kotlinx.coroutines.flow.Flow

data class NoteHistoryCount(
    val noteId: String,
    val versionCount: Int,
)

data class NoteHistoryPreviewEntity(
    val id: Long,
    val noteId: String,
    val title: String,
    val content: String,
    val savedAtMs: Long,
    val contentLength: Int,
)

@Dao
interface NoteHistoryDao {
    @Query("SELECT * FROM note_history WHERE noteId = :noteId ORDER BY savedAtMs DESC")
    fun getHistory(noteId: String): Flow<List<NoteHistoryEntity>>

    @Query(
        """
        SELECT id, noteId, title,
            CASE
                WHEN length(content) > :fullContentLimit THEN substr(content, 1, :previewLimit)
                ELSE content
            END AS content,
            savedAtMs,
            length(content) AS contentLength
        FROM note_history
        WHERE noteId = :noteId
        ORDER BY savedAtMs DESC
        """,
    )
    fun getHistoryPreview(
        noteId: String,
        previewLimit: Int,
        fullContentLimit: Int,
    ): Flow<List<NoteHistoryPreviewEntity>>

    @Query("SELECT * FROM note_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): NoteHistoryEntity?

    @Query("SELECT * FROM note_history ORDER BY savedAtMs DESC")
    suspend fun getAllHistory(): List<NoteHistoryEntity>

    @Query(
        """
        SELECT id, noteId, title, substr(content, 1, 200) AS content, savedAtMs
        FROM note_history
        WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'
        ORDER BY savedAtMs DESC
        """,
    )
    fun searchHistoryPreview(query: String): Flow<List<NoteHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: NoteHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(histories: List<NoteHistoryEntity>)

    @Query("DELETE FROM note_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM note_history WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query(
        """
        DELETE FROM note_history
        WHERE noteId = :noteId
        AND id NOT IN (
            SELECT id FROM note_history
            WHERE noteId = :noteId
            ORDER BY savedAtMs DESC, id DESC
            LIMIT :keep
        )
        """,
    )
    suspend fun pruneOldVersions(
        noteId: String,
        keep: Int,
    )

    @Query("SELECT DISTINCT noteId FROM note_history")
    suspend fun getAllHistoryNoteIds(): List<String>

    @Query(
        """
        SELECT COALESCE(MAX(n.filePath), h.noteId) AS noteId,
            COALESCE(
                NULLIF(MAX(n.title), ''),
                NULLIF((
                    SELECT hh.title
                    FROM note_history hh
                    WHERE hh.noteId = h.noteId
                    ORDER BY hh.savedAtMs DESC, hh.id DESC
                    LIMIT 1
                ), ''),
                '无标题'
            ) AS title,
            COALESCE(
                NULLIF(MAX(n.contentPreview), ''),
                (
                    SELECT substr(hh.content, 1, 200)
                    FROM note_history hh
                    WHERE hh.noteId = h.noteId
                    ORDER BY hh.savedAtMs DESC, hh.id DESC
                    LIMIT 1
                ),
                ''
            ) AS contentPreview,
            COUNT(DISTINCT h.id) AS recordCount,
            MAX(h.savedAtMs) AS updatedAtMs
        FROM note_history h
        LEFT JOIN notes n ON n.filePath = h.noteId OR n.recordId = h.noteId
        GROUP BY h.noteId
        ORDER BY MAX(h.savedAtMs) DESC, h.noteId ASC
        """,
    )
    suspend fun getHistoryNoteSummaries(): List<NoteRecordSummary>

    @Query(
        """
        SELECT noteId, COUNT(*) AS versionCount
        FROM note_history
        GROUP BY noteId
        HAVING COUNT(*) > :keep
        ORDER BY versionCount DESC, noteId ASC
        """,
    )
    suspend fun getHistoryCountsOverLimit(keep: Int): List<NoteHistoryCount>

    @Query("SELECT COUNT(*) FROM note_history")
    suspend fun getHistoryCount(): Int

    @Query("SELECT COUNT(*) FROM note_history WHERE noteId = :noteId")
    suspend fun getHistoryCountForNote(noteId: String): Int

    @Query("UPDATE note_history SET noteId = :newNoteId WHERE noteId = :oldNoteId")
    suspend fun replaceNoteId(
        oldNoteId: String,
        newNoteId: String,
    )
}
