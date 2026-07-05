package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.kangle.kardleaf.data.task.MarkdownTaskSource
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        """
        SELECT * FROM tasks
        ORDER BY done ASC,
            CASE WHEN reminderAt IS NULL THEN 1 ELSE 0 END ASC,
            reminderAt ASC,
            updatedAt DESC
        """,
    )
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE done = 0 AND reminderAt IS NOT NULL AND reminderAt > :now
        ORDER BY reminderAt ASC
        """,
    )
    suspend fun getPendingReminders(now: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Query(
        """
        SELECT * FROM tasks
        WHERE done = 0
        ORDER BY
            CASE WHEN reminderAt IS NULL THEN 1 ELSE 0 END ASC,
            reminderAt ASC,
            updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun getWidgetOpenTasks(limit: Int): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE done = 0")
    suspend fun countWidgetOpenTasks(): Int

    @Query(
        """
        SELECT filePath AS notePath, title, content, lastModifiedMs AS updatedAt
        FROM notes
        WHERE isTrashed = 0 AND isArchived = 0
            AND (content LIKE '%[ ]%' OR content LIKE '%[x]%' OR content LIKE '%[X]%')
        ORDER BY lastModifiedMs DESC
        """,
    )
    fun observeMarkdownTaskSources(): Flow<List<MarkdownTaskSource>>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}
