package com.kangle.kardleaf.data.task

import com.kangle.kardleaf.data.database.TaskEntity
import java.util.Calendar

internal enum class TaskRepeat(
    val value: String,
    val label: String,
) {
    NONE("NONE", "不重复"),
    DAILY("DAILY", "每天"),
    WEEKLY("WEEKLY", "每周"),
    MONTHLY("MONTHLY", "每月"),
    ;

    companion object {
        fun from(value: String): TaskRepeat = entries.firstOrNull { it.value == value } ?: NONE
    }
}

/**
 * Creates the next occurrence without changing the completed task.
 * Dates continue advancing until they are after the completion time, so an overdue repeat does not create a stale copy.
 */
internal fun nextTaskOccurrence(
    task: TaskEntity,
    now: Long,
): TaskEntity? {
    val repeat = TaskRepeat.from(task.repeatRule)
    if (repeat == TaskRepeat.NONE || (task.dueAt == null && task.reminderAt == null)) return null

    fun advance(time: Long): Long {
        var next = time
        do {
            next =
                Calendar.getInstance().apply {
                    timeInMillis = next
                    add(
                        when (repeat) {
                            TaskRepeat.DAILY -> Calendar.DAY_OF_MONTH
                            TaskRepeat.WEEKLY -> Calendar.WEEK_OF_YEAR
                            TaskRepeat.MONTHLY -> Calendar.MONTH
                            TaskRepeat.NONE -> return time
                        },
                        1,
                    )
                }.timeInMillis
        } while (next <= now)
        return next
    }

    return task.copy(
        id = 0,
        done = false,
        dueAt = task.dueAt?.let(::advance),
        reminderAt = task.reminderAt?.let(::advance),
        createdAt = now,
        updatedAt = now,
    )
}
