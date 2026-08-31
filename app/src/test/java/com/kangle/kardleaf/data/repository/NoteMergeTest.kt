package com.kangle.kardleaf.data.repository

import com.kangle.kardleaf.data.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Date

class NoteMergeTest {
    @Test
    fun joinsBodiesAndOptionalTitles() {
        val notes = listOf(
            testNote("First", "one\n"),
            testNote("Second", "two"),
        )

        assertEquals("one\n\ntwo", mergeMarkdownBlocks(notes, includeTitles = false, separator = "\n\n"))
        assertEquals("# First\n\none\n\n# Second\n\ntwo", mergeMarkdownBlocks(notes, includeTitles = true, separator = "\n\n"))
    }

    private fun testNote(title: String, content: String) = Note(
        file = File("$title.md"),
        title = title,
        content = content,
        lastModified = Date(0),
        color = 0,
    )
}
