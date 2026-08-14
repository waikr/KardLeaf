package com.kangle.kardleaf.data.task

import com.kangle.kardleaf.data.database.TaskEntity

data class TaskEditorResult(
    val text: String,
    val done: Boolean,
    val groupId: Long?,
    val priority: Int,
    val dueAt: Long?,
    val reminderAt: Long?,
    val repeatRule: String,
    val notes: String,
    val reminderMode: String = TaskEntity.REMINDER_MODE_POPUP,
    val reminderRing: Boolean = true,
    val reminderVibrate: Boolean = true,
    val parentTaskId: Long? = null,
    val parentTaskSelectionChanged: Boolean = false,
    val childTaskTexts: List<String> = emptyList(),
)

fun TaskEditorResult.toTaskEntity(
    original: TaskEntity?,
    now: Long,
): TaskEntity =
    if (original == null) {
        TaskEntity(
            taskText = text,
            done = done,
            reminderAt = reminderAt,
            groupId = groupId,
            priority = priority,
            dueAt = dueAt,
            repeatRule = repeatRule,
            notes = notes,
            createdAt = now,
            updatedAt = now,
            reminderMode = reminderMode,
            reminderRing = reminderRing,
            reminderVibrate = reminderVibrate,
        )
    } else {
        original.copy(
            taskText = text,
            done = done,
            reminderAt = reminderAt,
            groupId = groupId,
            priority = priority,
            dueAt = dueAt,
            repeatRule = repeatRule,
            notes = notes,
            updatedAt = now,
            reminderMode = reminderMode,
            reminderRing = reminderRing,
            reminderVibrate = reminderVibrate,
        )
    }
