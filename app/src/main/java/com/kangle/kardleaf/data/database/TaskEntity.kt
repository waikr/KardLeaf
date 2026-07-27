package com.kangle.kardleaf.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["notePath"]),
        Index(value = ["groupId"]),
        Index(value = ["done", "reminderAt"]),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notePath: String? = null,
    val taskText: String,
    val done: Boolean = false,
    val reminderAt: Long? = null,
    val groupId: Long? = null,
    val priority: Int = 0,
    val dueAt: Long? = null,
    val repeatRule: String = "NONE",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val reminderMode: String = REMINDER_MODE_POPUP,
    val reminderRing: Boolean = true,
    val reminderVibrate: Boolean = true,
) {
    companion object {
        const val REMINDER_MODE_POPUP = "POPUP"
        const val REMINDER_MODE_NOTIFICATION = "NOTIFICATION"
    }
}

@Entity(
    tableName = "task_groups",
    indices = [Index(value = ["sortOrder"], unique = true)],
)
data class TaskGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
)
