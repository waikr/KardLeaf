package com.kangle.kardleaf.data.task

import com.kangle.kardleaf.data.database.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskHierarchyTest {
    @Test
    fun calculatesDescendantsDepthAndChildCountsForDeepTree() {
        val parents = mapOf(2L to 1L, 3L to 1L, 4L to 2L, 5L to 4L)
        val tasks = (1L..5L).map { id -> task(id, parents[id]) }

        assertEquals(setOf(2L, 3L, 4L, 5L), TaskHierarchy.descendants(1L, parents))
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), TaskHierarchy.descendants(tasks, setOf(1L), includeRoots = true))
        assertEquals(3, TaskHierarchy.depth(5L, parents))
        assertEquals(mapOf(1L to 2, 2L to 1, 4L to 1), TaskHierarchy.childCounts(tasks, parents))
    }

    @Test
    fun completionSectionFollowsRootWhileChildrenKeepTheirOwnState() {
        val parent = task(1)
        val completedChild = task(2, 1L).copy(done = true)
        val openChild = task(3, 1L)
        val completedParent = task(4).copy(done = true)
        val openChildOfCompletedParent = task(5, 4L)
        val tasks = listOf(parent, completedChild, openChild, completedParent, openChildOfCompletedParent)
        val parents = tasks.mapNotNull { it.parentTaskId?.let { parentId -> it.id to parentId } }.toMap()
        val roots = TaskHierarchy.rootIds(tasks, parents)

        assertEquals(1L, roots.getValue(completedChild.id))
        assertEquals(4L, roots.getValue(openChildOfCompletedParent.id))
        assertEquals(
            setOf(1L),
            tasks.filter { roots[it.id] == it.id && !it.done }.mapTo(hashSetOf()) { it.id },
        )
        assertEquals(
            setOf(4L),
            tasks.filter { roots[it.id] == it.id && it.done }.mapTo(hashSetOf()) { it.id },
        )
        assertEquals(
            setOf(1L, 2L, 3L),
            TaskHierarchy.descendants(tasks, setOf(1L), includeRoots = true),
        )
        assertEquals(
            setOf(4L, 5L),
            TaskHierarchy.descendants(tasks, setOf(4L), includeRoots = true),
        )
    }

    @Test
    fun detectsCyclesAndKeepsEveryTaskInFlattenedOutput() {
        val tasks = listOf(task(1), task(2), task(3))
        val parents = mapOf(2L to 3L, 3L to 2L)

        assertTrue(TaskHierarchy.createsCycle(parents, taskId = 2L, proposedParentId = 3L))
        assertFalse(TaskHierarchy.createsCycle(parents, taskId = 1L, proposedParentId = 2L))
        assertEquals(tasks, TaskHierarchy.flatten(tasks, parents, emptySet()))
    }

    @Test
    fun keepsDanglingParentsVisibleAndExcludesThemFromChildCounts() {
        val tasks = listOf(task(1, 99L), task(2), task(3, 1L))
        val parents = mapOf(1L to 99L, 3L to 1L)

        assertEquals(listOf(1L, 2L), TaskHierarchy.flatten(tasks, parents, emptySet()).map { it.id })
        assertEquals(
            listOf(1L, 3L, 2L),
            TaskHierarchy.flatten(tasks, parents, setOf(1L)).map { it.id },
        )
        assertEquals(mapOf(1L to 1), TaskHierarchy.childCounts(tasks, parents))
    }

    @Test
    fun collectsDescendantsFromMultipleRootsWithoutChangingTreeOrder() {
        val parents = mapOf(2L to 1L, 4L to 3L, 5L to 4L)
        val tasks = (1L..5L).map { id -> task(id, parents[id]) }

        assertEquals(setOf(2L, 4L, 5L), TaskHierarchy.descendants(tasks, setOf(1L, 3L)))
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            TaskHierarchy.flatten(tasks, parents, setOf(1L, 3L)).map { it.id },
        )
    }

    private fun task(id: Long, parentTaskId: Long? = null) =
        TaskEntity(id = id, taskText = "task$id", parentTaskId = parentTaskId, createdAt = 1L, updatedAt = 1L)
}
