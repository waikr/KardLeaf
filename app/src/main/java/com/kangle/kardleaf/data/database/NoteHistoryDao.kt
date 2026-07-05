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
        LIMIT :limit
        """,
    )
    fun searchHistoryPreview(
        query: String,
        limit: Int,
    ): Flow<List<NoteHistoryEntity>>

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
        WITH mapped_history AS (
            SELECT h.id,
                COALESCE(note_by_path.filePath, note_by_record.filePath, h.noteId) AS noteId,
                h.title AS historyTitle,
                h.content AS historyContent,
                h.savedAtMs,
                COALESCE(note_by_path.title, note_by_record.title) AS noteTitle,
                COALESCE(note_by_path.contentPreview, note_by_record.contentPreview) AS notePreview
            FROM note_history h
            LEFT JOIN notes note_by_path ON note_by_path.filePath = h.noteId
            LEFT JOIN notes note_by_record ON note_by_record.recordId = h.noteId
                AND note_by_path.filePath IS NULL
        )
        SELECT mh_outer.noteId AS noteId,
            COALESCE(
                NULLIF(MAX(mh_outer.noteTitle), ''),
                NULLIF((
                    SELECT mh.historyTitle
                    FROM mapped_history mh
                    WHERE mh.noteId = mh_outer.noteId
                    ORDER BY mh.savedAtMs DESC, mh.id DESC
                    LIMIT 1
                ), ''),
                mh_outer.noteId
            ) AS title,
            COALESCE(
                NULLIF(MAX(mh_outer.notePreview), ''),
                (
                    SELECT substr(mh.historyContent, 1, 200)
                    FROM mapped_history mh
                    WHERE mh.noteId = mh_outer.noteId
                    ORDER BY mh.savedAtMs DESC, mh.id DESC
                    LIMIT 1
                ),
                ''
            ) AS contentPreview,
            COUNT(DISTINCT mh_outer.id) AS recordCount,
            MAX(mh_outer.savedAtMs) AS updatedAtMs
        FROM mapped_history mh_outer
        GROUP BY mh_outer.noteId
        ORDER BY MAX(mh_outer.savedAtMs) DESC, mh_outer.noteId ASC
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
