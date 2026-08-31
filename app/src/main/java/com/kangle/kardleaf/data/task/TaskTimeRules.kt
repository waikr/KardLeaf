package com.kangle.kardleaf.data.task

import com.kangle.kardleaf.data.database.TaskEntity
import java.util.Calendar

object TaskTimeRules {
    fun listTime(task: TaskEntity): Long? = task.dueAt ?: task.reminderAt

    fun isToday(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean {
        if (task.done) return false
        val start = startOfDay(now)
        val end = endOfDay(now)
        return listOfNotNull(task.reminderAt, task.dueAt).any { it in start..end }
    }

    fun isUpcoming(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        !task.done && listTime(task)?.let { it > endOfDay(now) } == true

    fun isOverdue(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        !task.done && listTime(task)?.let { it < now } == true

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
