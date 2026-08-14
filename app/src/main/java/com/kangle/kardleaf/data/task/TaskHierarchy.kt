package com.kangle.kardleaf.data.task

import com.kangle.kardleaf.data.database.TaskEntity

internal object TaskHierarchy {
    fun descendants(
        tasks: List<TaskEntity>,
        roots: Set<Long>,
        includeRoots: Boolean = false,
    ): Set<Long> {
        if (roots.isEmpty()) return emptySet()
        val childrenByParent = tasks.groupBy { it.parentTaskId }
        val result = if (includeRoots) roots.toMutableSet() else hashSetOf()
        val pending = ArrayDeque<Long>().apply { addAll(roots) }
        while (pending.isNotEmpty()) {
            childrenByParent[pending.removeFirst()].orEmpty().forEach { child ->
                if (result.add(child.id)) pending.addLast(child.id)
            }
        }
        return result
    }

    fun descendants(
        taskId: Long,
        parentIds: Map<Long, Long?>,
    ): Set<Long> =
        parentIds
            .keys
            .filter { it != taskId && reachesParent(parentIds, it, taskId) }
            .toSet()

    fun createsCycle(
        parentIds: Map<Long, Long?>,
        taskId: Long,
        proposedParentId: Long,
    ): Boolean =
        taskId == proposedParentId || reachesParent(parentIds, proposedParentId, taskId)

    fun depth(
        taskId: Long,
        parentIds: Map<Long, Long?>,
    ): Int {
        var depth = 0
        var parentId = parentIds[taskId]
        val visited = hashSetOf(taskId)
        while (parentId != null && visited.add(parentId)) {
            depth++
            parentId = parentIds[parentId]
        }
        return depth
    }

    fun depths(
        tasks: List<TaskEntity>,
        parentIds: Map<Long, Long?>,
    ): Map<Long, Int> = tasks.associate { it.id to depth(it.id, parentIds) }

    fun rootIds(
        tasks: List<TaskEntity>,
        parentIds: Map<Long, Long?>,
    ): Map<Long, Long> {
        val taskIds = tasks.mapTo(hashSetOf()) { it.id }
        return tasks.associate { task ->
            var current = task.id
            val visited = hashSetOf<Long>()
            while (visited.add(current)) {
                val parentId = parentIds[current]
                if (parentId == null || parentId !in taskIds) break
                current = parentId
            }
            task.id to current
        }
    }

    fun childCounts(
        tasks: List<TaskEntity>,
        parentIds: Map<Long, Long?>,
    ): Map<Long, Int> {
        val taskIds = tasks.mapTo(hashSetOf()) { it.id }
        return tasks
            .mapNotNull { task -> parentIds[task.id]?.takeIf { it in taskIds } }
            .groupingBy { it }
            .eachCount()
    }

    fun flatten(
        tasks: List<TaskEntity>,
        parentIds: Map<Long, Long?>,
        expandedTaskIds: Set<Long>,
    ): List<TaskEntity> {
        val taskIds = tasks.mapTo(hashSetOf()) { it.id }
        val childrenByParent =
            tasks
                .filter { parentIds[it.id] in taskIds }
                .groupBy { parentIds.getValue(it.id) }
        val result = ArrayList<TaskEntity>(tasks.size)
        val visited = hashSetOf<Long>()

        fun append(task: TaskEntity) {
            if (!visited.add(task.id)) return
            result += task
            if (task.id in expandedTaskIds) childrenByParent[task.id].orEmpty().forEach(::append)
        }

        tasks.filter { parentIds[it.id] !in taskIds }.forEach(::append)
        tasks.filter { task ->
            if (task.id in visited) return@filter false
            var currentId: Long? = task.id
            val chain = hashSetOf<Long>()
            while (currentId != null && currentId in taskIds && chain.add(currentId)) {
                currentId = parentIds[currentId]
            }
            currentId != null && currentId in chain
        }.forEach(::append)
        return result
    }

    private fun reachesParent(
        parentIds: Map<Long, Long?>,
        start: Long,
        target: Long,
    ): Boolean {
        var current: Long? = start
        val visited = hashSetOf<Long>()
        while (current != null && visited.add(current)) {
            if (current == target) return true
            current = parentIds[current]
        }
        return false
    }
}
