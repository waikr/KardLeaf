package com.kangle.kardleaf.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tasks",
    indices = [
        Index(value = ["notePath"]),
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
    val createdAt: Long,
    val updatedAt: Long,
)
