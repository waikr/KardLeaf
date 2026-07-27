package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteLinkDao {
    @Query("DELETE FROM note_links WHERE sourcePath = :sourcePath OR sourceRecordId = :sourceRecordId")
    suspend fun deleteBySource(sourcePath: String, sourceRecordId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<NoteLinkEntity>)

    @Query("SELECT * FROM note_links WHERE sourcePath = :sourcePath OR sourceRecordId = :sourceRecordId ORDER BY startOffset ASC")
    fun getOutgoing(sourcePath: String, sourceRecordId: String): Flow<List<NoteLinkEntity>>

    @Query(
        "SELECT * FROM note_links WHERE (targetRecordId = :recordId OR targetPath = :path) " +
            "AND sourcePath != :path ORDER BY sourcePath ASC, startOffset ASC",
    )
    fun getBacklinks(recordId: String, path: String): Flow<List<NoteLinkEntity>>

    @Query(
        "SELECT * FROM note_links WHERE (targetRecordId = :recordId OR targetPath = :path) " +
            "AND sourcePath != :path ORDER BY sourcePath ASC, startOffset ASC",
    )
    suspend fun getBacklinksSync(recordId: String, path: String): List<NoteLinkEntity>

    @Query("SELECT * FROM note_links WHERE targetNormalized = :targetNormalized ORDER BY sourcePath ASC, startOffset ASC")
    suspend fun getLinksToNormalized(targetNormalized: String): List<NoteLinkEntity>

    @Query("SELECT * FROM note_links ORDER BY sourcePath ASC, startOffset ASC")
    suspend fun getAllSync(): List<NoteLinkEntity>

    @Update
    suspend fun update(link: NoteLinkEntity)

    @Query(
        "UPDATE note_links SET targetRecordId = NULL, targetPath = NULL, resolutionStatus = 'UNRESOLVED' " +
            "WHERE targetPath = :path OR targetRecordId = :recordId",
    )
    suspend fun markTargetUnresolved(path: String, recordId: String)

    @Query("DELETE FROM note_links WHERE sourcePath IN (:paths) OR targetPath IN (:paths)")
    suspend fun deleteForPaths(paths: List<String>)

    @Query("DELETE FROM note_links")
    suspend fun deleteAll()
}
