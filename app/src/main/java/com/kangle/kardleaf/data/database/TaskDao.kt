package com.kangle.kardleaf.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM task_groups ORDER BY sortOrder ASC")
    fun observeGroups(): Flow<List<TaskGroupEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM task_groups")
    suspend fun getMaxGroupOrder(): Int

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

    @Insert
    suspend fun insertGroup(group: TaskGroupEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Update
    suspend fun updateGroup(group: TaskGroupEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Delete
    suspend fun deleteGroup(group: TaskGroupEntity)

    @Query("UPDATE tasks SET groupId = :groupId, updatedAt = :updatedAt WHERE id = :taskId")
    suspend fun moveTaskToGroup(taskId: Long, groupId: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET groupId = NULL, updatedAt = :updatedAt WHERE groupId = :groupId")
    suspend fun moveGroupTasksToUngrouped(groupId: Long, updatedAt: Long)

    @Transaction
    suspend fun deleteGroupKeepingTasks(group: TaskGroupEntity, updatedAt: Long) {
        moveGroupTasksToUngrouped(group.id, updatedAt)
        deleteGroup(group)
    }

    @Transaction
    suspend fun swapGroupOrder(first: TaskGroupEntity, second: TaskGroupEntity) {
        updateGroup(first.copy(sortOrder = -1))
        updateGroup(second.copy(sortOrder = first.sortOrder))
        updateGroup(first.copy(sortOrder = second.sortOrder))
    }
}
