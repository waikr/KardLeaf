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

    @Query(
        "UPDATE note_links SET sourceRecordId = CASE WHEN sourceRecordId = :oldPath THEN :newPath ELSE sourceRecordId END, " +
            "sourcePath = :newPath WHERE sourcePath = :oldPath",
    )
    suspend fun renameSourcePath(oldPath: String, newPath: String): Int

    @Query(
        "UPDATE note_links SET sourceRecordId = CASE WHEN sourceRecordId = sourcePath " +
            "THEN :newFolder || substr(sourcePath, length(:oldFolder) + 1) ELSE sourceRecordId END, " +
            "sourcePath = :newFolder || substr(sourcePath, length(:oldFolder) + 1) " +
            "WHERE sourcePath = :oldFolder OR substr(sourcePath, 1, length(:oldFolder) + 1) = :oldFolder || '/'",
    )
    suspend fun renameFolderSourcePaths(
        oldFolder: String,
        newFolder: String,
    ): Int

    @Query(
        "UPDATE note_links SET sourceRecordId = CASE WHEN sourceRecordId = sourcePath " +
            "THEN :newFolder || substr(sourcePath, length(:oldFolder) + 1) ELSE sourceRecordId END, " +
            "sourcePath = :newFolder || substr(sourcePath, length(:oldFolder) + 1) " +
            "WHERE (sourcePath = :oldFolder OR substr(sourcePath, 1, length(:oldFolder) + 1) = :oldFolder || '/') " +
            "AND sourcePath IN (SELECT filePath FROM notes WHERE isTrashed = 0)",
    )
    suspend fun moveActiveFolderSourcePaths(
        oldFolder: String,
        newFolder: String,
    ): Int

    @Query(
        "UPDATE note_links SET targetRecordId = NULL, targetPath = NULL, resolutionStatus = 'UNRESOLVED' " +
            "WHERE targetPath = :oldFolder OR substr(targetPath, 1, length(:oldFolder) + 1) = :oldFolder || '/'",
    )
    suspend fun markFolderTargetsUnresolved(oldFolder: String): Int

    @Query("DELETE FROM note_links WHERE sourcePath IN (:paths) OR targetPath IN (:paths)")
    suspend fun deleteForPaths(paths: List<String>)

    @Query("DELETE FROM note_links")
    suspend fun deleteAll()
}
