package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.database.TaskEntity
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListLogicTest {
    @Test
    fun filtersAndSortsWithoutDroppingTaskMetadata() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.JULY, 17, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = task(1, "today", dueAt = now + 60_000, priority = 1)
        val urgent = task(2, "urgent", dueAt = now + 120_000, priority = 3, notes = "find me")
        val tomorrow = task(3, "tomorrow", dueAt = now + 24 * 60 * 60_000L)

        assertEquals(
            listOf(urgent),
            filterAndSortTasks(listOf(today, urgent, tomorrow), "find", TaskFilter.ALL, TaskSort.DUE, now),
        )
        assertEquals(
            listOf(urgent, today),
            filterAndSortTasks(listOf(today, urgent, tomorrow), "", TaskFilter.TODAY, TaskSort.PRIORITY, now),
        )
    }

    @Test
    fun completingRepeatingTaskCreatesOneFutureOccurrence() {
        val now = 1_700_000_000_000L
        val original = task(
            id = 9,
            text = "weekly",
            dueAt = now - 15 * 24 * 60 * 60_000L,
            reminderAt = now - 15 * 24 * 60 * 60_000L,
            repeatRule = TaskRepeat.WEEKLY.value,
            groupId = 4,
        )

        val next = requireNotNull(nextTaskOccurrence(original, now))

        assertEquals(0, next.id)
        assertFalse(next.done)
        assertEquals(4L, next.groupId)
        assertTrue(requireNotNull(next.dueAt) > now)
        assertTrue(requireNotNull(next.reminderAt) > now)
    }

    private fun task(
        id: Long,
        text: String,
        dueAt: Long? = null,
        reminderAt: Long? = null,
        priority: Int = 0,
        notes: String = "",
        repeatRule: String = TaskRepeat.NONE.value,
        groupId: Long? = null,
    ) = TaskEntity(
        id = id,
        taskText = text,
        dueAt = dueAt,
        reminderAt = reminderAt,
        priority = priority,
        notes = notes,
        repeatRule = repeatRule,
        groupId = groupId,
        createdAt = 1,
        updatedAt = 1,
    )
}
