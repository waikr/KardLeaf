package com.kangle.kardleaf.data.task

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTaskParserTest {
    @Test
    fun parsesMarkdownTasksWithoutChangingText() {
        val result = parseMarkdownTasks(
            listOf(
                MarkdownTaskSource(
                    notePath = "work/today.md",
                    title = "Today",
                    content = """
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
        val result = parseMarkdownTasks(
            listOf(
                MarkdownTaskSource(
                    notePath = "work/long.md",
                    title = "Long",
                    content = """
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
        val first = MarkdownTaskParserCache.parse(
            listOf(MarkdownTaskSource(path, "Cache", "- [ ] first task", updatedAt = 1L)),
        )
        val second = MarkdownTaskParserCache.parse(
            listOf(MarkdownTaskSource(path, "Cache", "- [ ] second task", updatedAt = 2L)),
        )

        assertEquals("first task", first.single().taskText)
        assertEquals("second task", second.single().taskText)
    }
}
