package com.kangle.kardleaf.data.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTaskParserTest {
    @Test
    fun parsesMarkdownTasksWithoutChangingText() {
        val result =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        notePath = "work/today.md",
                        title = "Today",
                        content =
                            """
                            - [ ] write release notes
                            normal bullet
                            - [x] ship debug build
                            """.trimIndent(),
                    ),
                ),
            )

        assertEquals(2, result.size)
        assertEquals("write release notes", result[0].taskText)
        assertEquals(false, result[0].done)
        assertEquals(1, result[0].lineNumber)
        assertEquals("ship debug build", result[1].taskText)
        assertEquals(true, result[1].done)
        assertEquals(3, result[1].lineNumber)
    }

    @Test
    fun parsesSupportedMarkdownTaskMarkersPastTwentyThousandChars() {
        val prefix = "a".repeat(20_500)
        val result =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        notePath = "work/long.md",
                        title = "Long",
                        content =
                            """
                            $prefix
                            * [ ] star task
                            + [X] plus task
                            1. [ ] ordered task
                            2) [ ] ignored task
                            """.trimIndent(),
                    ),
                ),
            )

        assertEquals(3, result.size)
        assertEquals("star task", result[0].taskText)
        assertEquals(false, result[0].done)
        assertEquals(2, result[0].lineNumber)
        assertEquals("plus task", result[1].taskText)
        assertEquals(true, result[1].done)
        assertEquals("ordered task", result[2].taskText)
        assertEquals(false, result[2].done)
    }

    @Test
    fun cachedParserRefreshesWhenSourceTimestampChanges() {
        val path = "work/cache-${System.nanoTime()}.md"
        val first =
            MarkdownTaskParserCache.parse(
                listOf(MarkdownTaskSource(path, "Cache", "- [ ] first task", updatedAt = 1L)),
            )
        val second =
            MarkdownTaskParserCache.parse(
                listOf(MarkdownTaskSource(path, "Cache", "- [ ] second task", updatedAt = 2L)),
            )

        assertEquals("first task", first.single().taskText)
        assertEquals("second task", second.single().taskText)
    }

    @Test
    fun parsesObsidianTasksMetadataAndSkipsFencedCode() {
        val result =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        notePath = "work/tasks.md",
                        title = "Tasks",
                        content =
                            """
                            ```md
                            - [ ] ignored 📅 2026-08-10
                            ```
                              + [ ] write report #work ⏫ 🔁 every week #testing ➕ 2026-08-01 🛫 2026-08-02 ⏳ 2026-08-03 📅 2026-08-10 🆔 abc123 ⛔ dep-1
                            """.trimIndent(),
                    ),
                ),
            )

        val task = result.single()
        assertEquals("write report #work #testing", task.taskText)
        assertEquals("  ", task.indent)
        assertEquals("+", task.listMarker)
        assertEquals("⏫", task.priorityMarker)
        assertEquals("every week", task.recurrenceRule)
        assertEquals("2026-08-01", task.createdDate)
        assertEquals("2026-08-02", task.startDate)
        assertEquals("2026-08-03", task.scheduledDate)
        assertEquals("2026-08-10", task.dueDate)
        assertEquals("abc123", task.taskId)
    }

    @Test
    fun cachedParserRefreshesEqualLengthContentAtSameTimestamp() {
        val path = "work/equal-cache-${System.nanoTime()}.md"
        val first =
            MarkdownTaskParserCache.parse(
                listOf(MarkdownTaskSource(path, "Cache", "- [ ] first", updatedAt = 1L)),
            )
        val second =
            MarkdownTaskParserCache.parse(
                listOf(MarkdownTaskSource(path, "Cache", "- [ ] other", updatedAt = 1L)),
            )

        assertEquals("first", first.single().taskText)
        assertEquals("other", second.single().taskText)
    }

    @Test
    fun patchesMovedTaskWithoutChangingMetadataOrLineEnding() {
        val original = "  + [ ] task #work ⏫ 🔁 every week ➕ 2026-08-01 📅 2026-08-10 🆔 abc123\r\nnext"
        val item = parseMarkdownTasks(listOf(MarkdownTaskSource("tasks.md", "Tasks", original))).single()
        val moved = "heading\r\n$original"

        val patched = patchMarkdownTaskDone(moved, item, done = true) as MarkdownTaskPatchResult.Success

        assertEquals(2, patched.lineNumber)
        assertTrue(patched.content.contains("  + [x] task #work ⏫ 🔁 every week ➕ 2026-08-01 📅 2026-08-10 🆔 abc123"))
        assertTrue(patched.content.contains("\r\n"))
    }

    @Test
    fun refusesAmbiguousMovedTask() {
        val line = "- [ ] duplicate 📅 2026-08-10"
        val item = parseMarkdownTasks(listOf(MarkdownTaskSource("tasks.md", "Tasks", line))).single()

        val patched = patchMarkdownTaskDone("heading\n$line\n$line", item, done = true)

        assertEquals(MarkdownTaskPatchResult.Conflict, patched)
    }

    @Test
    fun changesOnlyDueDateAndPreservesOtherMetadata() {
        val line = "- [ ] task ⏫ 🔁 every week ➕ 2026-08-01 📅 2026-08-10 🆔 abc123 ⛔ dep-1"
        val item = parseMarkdownTasks(listOf(MarkdownTaskSource("tasks.md", "Tasks", line))).single()

        val patched = patchMarkdownTaskDueDate(line, item, "2026-08-15") as MarkdownTaskPatchResult.Success

        assertEquals(
            "- [ ] task ⏫ 🔁 every week ➕ 2026-08-01 📅 2026-08-15 🆔 abc123 ⛔ dep-1",
            patched.content,
        )
        assertFalse(patched.content.contains("2026-08-10"))

        val withoutDue = "- [ ] task 🏁 keep 🆔 abc123"
        val withoutDueItem =
            parseMarkdownTasks(
                listOf(MarkdownTaskSource("tasks.md", "Tasks", withoutDue)),
            ).single()
        val added = patchMarkdownTaskDueDate(withoutDue, withoutDueItem, "2026-08-16") as MarkdownTaskPatchResult.Success
        assertEquals("- [ ] task 🏁 keep 📅 2026-08-16 🆔 abc123", added.content)
    }

    @Test
    fun parsesDirectChildListItemsAsNotesWithoutTakingChildTasks() {
        val items =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        "tasks.md",
                        "Tasks",
                        """
                        - [ ] Parent
                          - Android 15 **大图** [[测试]]
                          - [ ] Child
                            - Child detail
                          - Parent second detail
                        """.trimIndent(),
                    ),
                ),
            )

        assertEquals(2, items.size)
        assertEquals("Android 15 **大图** [[测试]]\nParent second detail", items[0].notes)
        assertEquals("Child detail", items[1].notes)
    }

    @Test
    fun identifiesNestedCheckboxAsSubtask() {
        val items =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        "tasks.md",
                        "Tasks",
                        """
                        - [ ] Parent
                          - Parent detail
                          - [ ] Child
                            - Child detail
                        """.trimIndent(),
                    ),
                ),
            )

        assertEquals(2, items.size)
        assertEquals(0, items[0].indentLevel)
        assertEquals(1, items[1].indentLevel)
        assertEquals(1, items[1].parentLineNumber)
        assertEquals("Parent detail", items[0].notes)
        assertEquals("Child detail", items[1].notes)
    }

    @Test
    fun calculatesEveryNestedTaskDepth() {
        val items =
            parseMarkdownTasks(
                listOf(
                    MarkdownTaskSource(
                        "tasks.md",
                        "Tasks",
                        """
                        - [ ] Root
                          - [ ] Child
                            - [ ] Grandchild
                              - [ ] Great-grandchild
                        """.trimIndent(),
                    ),
                ),
            )

        assertEquals(listOf(0, 1, 2, 3), items.map { it.indentLevel })
        assertEquals(listOf(null, 1, 2, 3), items.map { it.parentLineNumber })
    }

    @Test
    fun patchesDuplicateTaskFieldsWithoutChangingIndentation() {
        val content = "  - [ ] old task 🆔 kardleaf-42"
        val item = parseMarkdownTasks(listOf(MarkdownTaskSource("tasks.md", "Tasks", content))).single()

        val patched =
            patchMarkdownTaskFields(content, item, "renamed task", "kardleaf-43") as MarkdownTaskPatchResult.Success

        assertEquals("  - [ ] renamed task 🆔 kardleaf-43", patched.content)
    }

    @Test
    fun patchesCheckboxWithoutChangingNotesOrCrLf() {
        val content = "- [ ] Task 📅 2026-08-15\r\n  - Android 15 偶尔空白\r\n  - 需要测试大图片"
        val item = parseMarkdownTasks(listOf(MarkdownTaskSource("tasks.md", "Tasks", content))).single()

        val patched = patchMarkdownTaskDone(content, item, done = true) as MarkdownTaskPatchResult.Success

        assertEquals(
            "- [x] Task 📅 2026-08-15\r\n  - Android 15 偶尔空白\r\n  - 需要测试大图片",
            patched.content,
        )
    }
}
