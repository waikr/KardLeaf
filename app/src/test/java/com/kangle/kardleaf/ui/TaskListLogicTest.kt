package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.database.TaskGroupEntity
import com.kangle.kardleaf.data.task.TaskRepeat
import com.kangle.kardleaf.data.task.TaskEditorResult
import com.kangle.kardleaf.data.task.TaskHierarchy
import com.kangle.kardleaf.data.task.nextTaskOccurrence
import com.kangle.kardleaf.data.task.toTaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TaskListLogicTest {
    @Test
    fun filtersAndSortsWithoutDroppingTaskMetadata() {
        val now =
            Calendar.getInstance().apply {
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
        val original =
            task(
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

    @Test
    fun allTasksStayFlatAndUngroupedIncludesOrphans() {
        val groups =
            listOf(
                TaskGroupEntity(id = 10, name = "工作", sortOrder = 0, createdAt = 1),
                TaskGroupEntity(id = 11, name = "生活", sortOrder = 1, createdAt = 1),
            )
        val work = task(1, "work", groupId = 10)
        val life = task(2, "life", groupId = 11)
        val ungrouped = task(3, "ungrouped")
        val orphan = task(4, "orphan", groupId = 99)
        val tasks = listOf(work, life, ungrouped, orphan)

        assertEquals(tasks, filterTasksByTaskGroup(tasks, groups, MainViewModel.NoteFilter.All))
        assertEquals(
            listOf(work),
            filterTasksByTaskGroup(tasks, groups, MainViewModel.NoteFilter.Label("工作")),
        )
        assertEquals(
            listOf(ungrouped, orphan),
            filterTasksByTaskGroup(tasks, groups, MainViewModel.NoteFilter.Label("")),
        )
    }

    @Test
    fun dateLabelUsesRelativeNamesAndOnlyShowsExplicitTime() {
        val now = calendar(2026, Calendar.AUGUST, 12, 12, 0).timeInMillis
        val today = calendar(2026, Calendar.AUGUST, 12, 23, 59).timeInMillis
        val tomorrow = calendar(2026, Calendar.AUGUST, 13, 15, 30).timeInMillis
        val dayAfterTomorrow = calendar(2026, Calendar.AUGUST, 14, 9, 0).timeInMillis
        val later = calendar(2026, Calendar.AUGUST, 18, 23, 59).timeInMillis

        assertEquals("今天", taskDateLabel(today, now, defaultHour = 23, defaultMinute = 59))
        assertEquals("明天 15:30", taskDateLabel(tomorrow, now, defaultHour = 23, defaultMinute = 59))
        assertEquals("后天", taskDateLabel(dayAfterTomorrow, now, defaultHour = 9, defaultMinute = 0))
        assertEquals("8月18日", taskDateLabel(later, now, defaultHour = 23, defaultMinute = 59))
    }

    @Test
    fun dateLabelFormatsYesterday() {
        val now = calendar(2026, Calendar.AUGUST, 12, 12, 0).timeInMillis
        val yesterday = calendar(2026, Calendar.AUGUST, 11, 10, 15).timeInMillis

        assertEquals("昨天 10:15", taskDateLabel(yesterday, now, defaultHour = 23, defaultMinute = 59))
    }

    @Test
    fun editorResultMapsEveryReminderFieldForBothEntrypoints() {
        val original = task(8, "old")
        val result =
            TaskEditorResult(
                text = "new",
                done = true,
                groupId = 4,
                priority = 3,
                dueAt = 20,
                reminderAt = 10,
                repeatRule = TaskRepeat.DAILY.value,
                notes = "detail",
                reminderMode = TaskEntity.REMINDER_MODE_NOTIFICATION,
                reminderRing = false,
                reminderVibrate = false,
            )

        val updated = result.toTaskEntity(original, now = 2)

        assertEquals(8, updated.id)
        assertEquals("new", updated.taskText)
        assertTrue(updated.done)
        assertEquals(4L, updated.groupId)
        assertEquals(TaskEntity.REMINDER_MODE_NOTIFICATION, updated.reminderMode)
        assertFalse(updated.reminderRing)
        assertFalse(updated.reminderVibrate)
        assertEquals(2L, updated.updatedAt)
    }

    @Test
    fun taskTreeKeepsRootsTogetherAndHidesChildrenUntilExpanded() {
        val firstParent = task(1, "parent 1")
        val firstChild = task(2, "child 1")
        val secondParent = task(3, "parent 2")
        val secondChild = task(4, "child 2")
        val tasks = listOf(firstParent, firstChild, secondParent, secondChild)
        val parents = mapOf(2L to 1L, 4L to 3L)

        assertEquals(
            listOf(firstParent, secondParent),
            TaskHierarchy.flatten(tasks, parents, emptySet()),
        )
        assertEquals(
            listOf(firstParent, firstChild, secondParent, secondChild),
            TaskHierarchy.flatten(tasks, parents, setOf(1L, 3L)),
        )
    }

    @Test
    fun taskTreeKeepsCyclicTasksVisibleBesideNormalRoots() {
        val root = task(1, "root")
        val first = task(2, "first")
        val second = task(3, "second")

        assertEquals(
            listOf(root, first, second),
            TaskHierarchy.flatten(listOf(root, first, second), mapOf(2L to 3L, 3L to 2L), emptySet()),
        )
    }

    @Test
    fun childDraftEnterAddsOnlyAfterNonBlankTextAndBackspaceRemovesEmptyLine() {
        assertEquals(
            listOf("first", ""),
            appendChildDraftLine(listOf("first"), 0).first,
        )
        assertEquals(
            listOf("first", ""),
            appendChildDraftLine(listOf("first", ""), 1).first,
        )
        assertEquals(
            listOf("first"),
            removeEmptyChildDraftLine(listOf("first", ""), 1)?.first,
        )
        assertEquals(null, appendChildDraftLine(listOf(""), 0).second)
    }

    private fun calendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Calendar =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
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
