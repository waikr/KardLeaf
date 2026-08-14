package com.kangle.kardleaf.data.task

import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.database.TaskGroupEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMarkdownStoreTest {
    @Test
    fun usesFixedManagedPath() {
        assertEquals(".KardLeaf/任务清单.md", TaskMarkdownStore.MANAGED_NOTE_PATH)
    }

    @Test
    fun rendersManagedTaskAsObsidianTasksEmojiFormat() {
        val task =
            TaskEntity(
                id = 42L,
                taskText = "write report #work",
                done = false,
                reminderAt = 1_800_000_000_000L,
                priority = 3,
                dueAt = 1_800_086_400_000L,
                repeatRule = "WEEKLY",
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L,
            )

        val line = TaskMarkdownStore.renderTaskLine(task)

        assertTrue(line.startsWith("- [ ] write report #work ⏫ 🔁 every week ➕ "))
        assertTrue(line.contains(" ⏳ "))
        assertTrue(line.contains(" 📅 "))
        assertTrue(line.endsWith("🆔 kardleaf-42"))
    }

    @Test
    fun preservesUnsupportedTasksMetadataWhenRewritingManagedTask() {
        val previous =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        TaskMarkdownStore.MANAGED_NOTE_PATH,
                        "任务清单",
                        "- [ ] task 🔺 🏁 keep ➕ 2026-08-01 🛫 2026-08-02 ❌ 2026-08-09 🆔 kardleaf-7 ⛔ dep-1",
                    ),
                ),
            ).single()
        val task =
            TaskEntity(
                id = 7L,
                taskText = "task",
                priority = 3,
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L,
            )

        val line = TaskMarkdownStore.renderTaskLine(task, previous)

        assertTrue(line.contains("🔺"))
        assertTrue(line.contains("🏁 keep ➕ 2026-08-01 🛫 2026-08-02"))
        assertTrue(line.contains("❌ 2026-08-09"))
        assertTrue(line.contains("🆔 kardleaf-7"))
        assertTrue(line.endsWith("⛔ dep-1"))
    }

    @Test
    fun assignsStableIdsToNewManagedTasksWithoutChangingOtherMetadata() {
        val content = "<!-- kardleaf-task-store:v1 -->\r\n## 未分组\r\n  + [ ] task 📅 2026-08-10 ⛔ dep-1"
        val tasks =
            parseMarkdownTasks(
                listOf(MarkdownTaskSource(TaskMarkdownStore.MANAGED_NOTE_PATH, "任务清单", content)),
            )

        val updated = TaskMarkdownStore.addManagedTaskIds(content, tasks, firstId = 9L)

        assertEquals(
            "<!-- kardleaf-task-store:v1 -->\r\n## 未分组\r\n  + [ ] task 📅 2026-08-10 🆔 kardleaf-9 ⛔ dep-1",
            updated,
        )
    }

    @Test
    fun keepsExternalTasksInTheirOriginalGroupWhenRendering() {
        val content =
            TaskMarkdownStore.renderTaskMarkdown(
                tasks = emptyList(),
                groups = listOf(TaskGroupEntity(3, "工作", 0, 1)),
                preservedExternalTasks = mapOf("工作" to listOf("- [ ] external 🆔 obsidian-id")),
            )

        assertTrue(content.contains("## 工作\n- [ ] external 🆔 obsidian-id"))
    }

    @Test
    fun doesNotWriteTrashedTasksBackToManagedMarkdown() {
        val task = TaskEntity(id = 8L, taskText = "hidden", createdAt = 1L, updatedAt = 1L, isTrashed = true)

        val content = TaskMarkdownStore.renderTaskMarkdown(listOf(task), emptyList())

        assertFalse(content.contains("hidden"))
    }

    @Test
    fun rendersNotesAsChildListAndPreservesChildTasks() {
        val previous =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        TaskMarkdownStore.MANAGED_NOTE_PATH,
                        "任务清单",
                        """
                        - [ ] Task 🆔 kardleaf-42
                          - old detail
                          - [ ] Child
                            - child detail
                        """.trimIndent(),
                    ),
                ),
            ).first()
        val task =
            TaskEntity(
                id = 42L,
                taskText = "Task",
                notes = "Android 15 偶尔空白\n\n需要测试大图片",
                createdAt = 1L,
                updatedAt = 1L,
            )

        val block = TaskMarkdownStore.renderTaskBlock(task, previous)

        assertTrue(block.contains("  - Android 15 偶尔空白"))
        assertTrue(block.contains("  - 需要测试大图片"))
        assertFalse(block.contains("  - old detail"))
        assertTrue(block.contains("  - [ ] Child"))
        assertTrue(block.contains("    - child detail"))
    }

    @Test
    fun doesNotDuplicateNestedManagedTasksWhenRendering() {
        val previousItems =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        TaskMarkdownStore.MANAGED_NOTE_PATH,
                        "任务清单",
                        """
                        - [ ] Parent 🆔 kardleaf-41
                          - [ ] Child 🆔 kardleaf-42
                            - child detail
                        """.trimIndent(),
                    ),
                ),
            )
        val tasks =
            listOf(
                TaskEntity(id = 41L, taskText = "Parent", createdAt = 1L, updatedAt = 2L),
                TaskEntity(id = 42L, taskText = "Child", notes = "child detail", createdAt = 1L, updatedAt = 1L, parentTaskId = 41L),
            )

        val content = TaskMarkdownStore.renderTaskMarkdown(tasks, emptyList(), previousItems)

        assertEquals(1, Regex("Child").findAll(content).count())
        assertEquals(1, Regex("child detail").findAll(content).count())
        assertTrue(content.contains("  - [ ] Child"))
        assertTrue(content.contains("    - child detail"))
    }

    @Test
    fun rendersNewTaskUnderRequestedParent() {
        val previousItems =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        TaskMarkdownStore.MANAGED_NOTE_PATH,
                        "浠诲姟娓呭崟",
                        "- [ ] Parent 🆔 kardleaf-41",
                    ),
                ),
            )
        val content =
            TaskMarkdownStore.renderTaskMarkdown(
                tasks = listOf(
                    TaskEntity(id = 41L, taskText = "Parent", createdAt = 1L, updatedAt = 2L),
                    TaskEntity(id = 42L, taskText = "Child", createdAt = 1L, updatedAt = 1L, parentTaskId = 41L),
                ),
                groups = emptyList(),
                previousItems = previousItems,
            )

        assertTrue(content.contains("- [ ] Parent"))
        assertTrue(content.contains("  - [ ] Child"))
    }

    @Test
    fun removesExistingParentWhenSelectionIsCleared() {
        val previousItems =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        TaskMarkdownStore.MANAGED_NOTE_PATH,
                        "任务清单",
                        """
                        - [ ] Parent 🆔 kardleaf-41
                          - [ ] Child 🆔 kardleaf-42
                        """.trimIndent(),
                    ),
                ),
            )
        val content =
            TaskMarkdownStore.renderTaskMarkdown(
                tasks = listOf(
                    TaskEntity(id = 41L, taskText = "Parent", createdAt = 1L, updatedAt = 2L),
                    TaskEntity(id = 42L, taskText = "Child", createdAt = 1L, updatedAt = 1L),
                ),
                groups = emptyList(),
                previousItems = previousItems,
            )

        assertTrue(content.contains("- [ ] Parent"))
        assertTrue(content.contains("- [ ] Child"))
        assertFalse(content.contains("  - [ ] Child"))
    }

    @Test
    fun preservesExternalTaskTreeAsOneBlock() {
        val items =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        TaskMarkdownStore.MANAGED_NOTE_PATH,
                        "任务清单",
                        """
                        - [ ] External 🆔 obsidian-1
                          - external detail
                          - [ ] Child
                            - child detail
                        """.trimIndent(),
                    ),
                ),
            )

        val blocks = TaskMarkdownStore.preservedExternalTaskBlocks(items, items.associate { it.lineNumber to null })

        assertEquals(1, blocks.getValue(null).size)
        assertTrue(blocks.getValue(null).single().contains("  - [ ] Child\n    - child detail"))
    }

    @Test
    fun rendersManagedMarkdownWithRequestedLineEnding() {
        val task = TaskEntity(id = 42L, taskText = "Task", notes = "detail", createdAt = 1L, updatedAt = 1L)

        val content = TaskMarkdownStore.renderTaskMarkdown(listOf(task), emptyList(), newline = "\r\n")

        assertTrue(content.contains("\r\n  - detail\r\n"))
        assertFalse(content.replace("\r\n", "").contains('\n'))
    }

    @Test
    fun preservesHighestAndLowestObsidianPriorityMarkers() {
        val previous =
            parseMarkdownTasks(
                listOf(MarkdownTaskSource(TaskMarkdownStore.MANAGED_NOTE_PATH, "任务清单", "- [ ] Task 🔺 🆔 kardleaf-42")),
            ).single()
        val task = TaskEntity(id = 42L, taskText = "Task", priority = 3, createdAt = 1L, updatedAt = 1L)

        assertTrue(TaskMarkdownStore.renderTaskLine(task, previous).contains("🔺"))
    }

    @Test
    fun rendersEveryTaskWhenCachedParentLinksContainCycle() {
        val content =
            TaskMarkdownStore.renderTaskMarkdown(
                tasks = listOf(
                    TaskEntity(id = 1L, taskText = "First", createdAt = 1L, updatedAt = 1L, parentTaskId = 2L),
                    TaskEntity(id = 2L, taskText = "Second", createdAt = 1L, updatedAt = 1L, parentTaskId = 1L),
                ),
                groups = emptyList(),
            )

        assertEquals(1, Regex("First").findAll(content).count())
        assertEquals(1, Regex("Second").findAll(content).count())
    }

    @Test
    fun rendersTaskWithMissingGroupAsUngrouped() {
        val content =
            TaskMarkdownStore.renderTaskMarkdown(
                tasks = listOf(TaskEntity(id = 7L, taskText = "Orphan", groupId = 99L, createdAt = 1L, updatedAt = 1L)),
                groups = emptyList(),
            )

        assertTrue(content.contains("## 未分组\n- [ ] Orphan"))
    }

    @Test
    fun renamesNestedGroupPathsWithoutChangingIdsOrUnrelatedGroups() {
        val groups =
            listOf(
                TaskGroupEntity(1L, "工作", 0, 1L),
                TaskGroupEntity(2L, "工作/KardLeaf", 1, 1L),
                TaskGroupEntity(3L, "工作/KardLeaf/发布", 2, 1L),
                TaskGroupEntity(4L, "工作台", 3, 1L),
            )

        val renamed = TaskMarkdownStore.renameGroupPaths(groups, "工作", "开发")

        assertEquals(
            listOf("开发", "开发/KardLeaf", "开发/KardLeaf/发布", "工作台"),
            renamed.map { it.name },
        )
        assertEquals(groups.map { it.id }, renamed.map { it.id })
    }

    @Test
    fun detectsRenameCollisionWithGroupOutsideRenamedSubtree() {
        val groups =
            listOf(
                TaskGroupEntity(1L, "工作", 0, 1L),
                TaskGroupEntity(2L, "工作/KardLeaf", 1, 1L),
                TaskGroupEntity(3L, "开发", 2, 1L),
                TaskGroupEntity(4L, "开发/KardLeaf", 3, 1L),
            )

        val renamed = TaskMarkdownStore.renameGroupPaths(groups, "工作", "开发")

        assertEquals("开发", TaskMarkdownStore.firstDuplicatedGroupName(renamed))
    }

    @Test
    fun detectsCollisionOnlyInDescendantPath() {
        val groups =
            listOf(
                TaskGroupEntity(1L, "工作", 0, 1L),
                TaskGroupEntity(2L, "工作/KardLeaf", 1, 1L),
                TaskGroupEntity(3L, "开发/KardLeaf", 2, 1L),
            )

        val renamed = TaskMarkdownStore.renameGroupPaths(groups, "工作", "开发")

        assertEquals("开发/KardLeaf", TaskMarkdownStore.firstDuplicatedGroupName(renamed))
    }

    @Test
    fun acceptsRenameWhenNoDuplicateNames() {
        val groups =
            listOf(
                TaskGroupEntity(1L, "工作", 0, 1L),
                TaskGroupEntity(2L, "工作/KardLeaf", 1, 1L),
                TaskGroupEntity(3L, "生活", 2, 1L),
            )

        val renamed = TaskMarkdownStore.renameGroupPaths(groups, "工作", "开发")

        assertEquals(null, TaskMarkdownStore.firstDuplicatedGroupName(renamed))
    }

    @Test
    fun detectsDuplicateWhenNewGroupNameAlreadyExists() {
        val groups =
            listOf(
                TaskGroupEntity(1L, "工作", 0, 1L),
                TaskGroupEntity(2L, "工作", 1, 1L),
            )

        assertEquals("工作", TaskMarkdownStore.firstDuplicatedGroupName(groups))
    }

    @Test
    fun treatsDifferentlyCasedGroupNamesAsDistinct() {
        val groups =
            listOf(
                TaskGroupEntity(1L, "工作", 0, 1L),
                TaskGroupEntity(2L, "WORK", 1, 1L),
            )

        assertEquals(null, TaskMarkdownStore.firstDuplicatedGroupName(groups))
    }
}
