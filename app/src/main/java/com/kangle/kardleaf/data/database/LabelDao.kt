package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {
    @Query("SELECT name FROM labels ORDER BY name ASC")
    fun getAllLabels(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(label: LabelEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(labels: List<LabelEntity>)

    @Query("DELETE FROM labels WHERE name = :name")
    suspend fun delete(name: String)

    @Query("DELETE FROM labels WHERE name = :name OR name LIKE :namePrefix")
    suspend fun deleteTree(
        name: String,
        namePrefix: String,
    )

    @Query("DELETE FROM labels")
    suspend fun deleteAll()
}
