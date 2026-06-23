package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrivacyNoteDao {
    @Query("SELECT * FROM privacy_notes ORDER BY updatedAtMs DESC")
    fun getAll(): Flow<List<PrivacyNoteEntity>>

    @Query("SELECT * FROM privacy_notes WHERE id = :id")
    suspend fun getById(id: Long): PrivacyNoteEntity?

    @Query("SELECT * FROM privacy_notes ORDER BY updatedAtMs DESC")
    suspend fun getAllOnce(): List<PrivacyNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: PrivacyNoteEntity): Long

    @Query("DELETE FROM privacy_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM privacy_notes")
    suspend fun count(): Int
}
