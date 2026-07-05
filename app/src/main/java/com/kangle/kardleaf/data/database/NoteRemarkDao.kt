package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kangle.kardleaf.data.model.NoteRecordSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteRemarkDao {
    @Query("SELECT * FROM note_remarks WHERE noteId = :noteId ORDER BY updatedAtMs DESC, id DESC")
    fun getRemarks(noteId: String): Flow<List<NoteRemarkEntity>>

    @Query("SELECT * FROM note_remarks ORDER BY updatedAtMs DESC, id DESC")
    suspend fun getAllRemarks(): List<NoteRemarkEntity>

    @Query(
        """
        SELECT COALESCE(MAX(n.filePath), r.noteId) AS noteId,
            COALESCE(NULLIF(MAX(n.title), ''), '无标题') AS title,
            COALESCE(
                NULLIF(MAX(n.contentPreview), ''),
                (
                    SELECT substr(rr.content, 1, 200)
                    FROM note_remarks rr
                    WHERE rr.noteId = r.noteId
                    ORDER BY rr.updatedAtMs DESC, rr.id DESC
                    LIMIT 1
                ),
                ''
            ) AS contentPreview,
            COUNT(DISTINCT r.id) AS recordCount,
            MAX(r.updatedAtMs) AS updatedAtMs
        FROM note_remarks r
        LEFT JOIN notes n ON n.filePath = r.noteId OR n.recordId = r.noteId
        GROUP BY r.noteId
        ORDER BY MAX(r.updatedAtMs) DESC, r.noteId ASC
        """,
    )
    suspend fun getRemarkNoteSummaries(): List<NoteRecordSummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(remark: NoteRemarkEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remarks: List<NoteRemarkEntity>)

    @Query("UPDATE note_remarks SET content = :content, updatedAtMs = :updatedAtMs WHERE id = :id")
    suspend fun updateContent(
        id: Long,
        content: String,
        updatedAtMs: Long,
    )

    @Query("DELETE FROM note_remarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM note_remarks WHERE noteId = :noteId")
    suspend fun deleteByNoteId(noteId: String)

    @Query("UPDATE note_remarks SET noteId = :newNoteId WHERE noteId = :oldNoteId")
    suspend fun replaceNoteId(
        oldNoteId: String,
        newNoteId: String,
    )
}
