package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Query

@Dao
interface PrivacyNoteDao {
    @Query("SELECT * FROM privacy_notes ORDER BY updatedAtMs DESC")
    suspend fun getAllOnce(): List<PrivacyNoteEntity>

    @Query("DELETE FROM privacy_notes")
    suspend fun deleteAll()
}
