package com.kangle.kardleaf.data.utils

import com.kangle.kardleaf.data.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Date

class NoteFormatUtilsTest {
    @Test
    fun readsAndWritesMindMapNoteType() {
        val raw = "---\nnote_type: mindmap\n---\n\n# Root\n"
        val frontMatter = NoteFormatUtils.parseFrontMatter(raw)

        assertEquals(NoteFormatUtils.NOTE_TYPE_MINDMAP, NoteFormatUtils.extractNoteType(frontMatter))

        val note = Note(
            file = File("Root.md"),
            title = "Root",
            content = frontMatter.cleanContent,
            lastModified = Date(0),
            color = 0xFFFFFFFF,
            noteType = NoteFormatUtils.NOTE_TYPE_MINDMAP,
        )
        val written = NoteFormatUtils.constructFileContent(note, existingRawContent = raw)

        assertTrue(written.contains("note_type: \"mindmap\""))
        assertEquals(NoteFormatUtils.NOTE_TYPE_MINDMAP, NoteFormatUtils.extractNoteType(NoteFormatUtils.parseFrontMatter(written)))
    }

    @Test
    fun sanitizesMarkdownFileBaseName() {
        assertEquals("abcdefghi", NoteFormatUtils.sanitizeMarkdownFileBaseName("  a/b\\c:d*e?f\"g<h>i|  "))
        assertEquals("Untitled", NoteFormatUtils.sanitizeMarkdownFileBaseName(" /\\:*?\"<>| "))
        assertEquals("Unknown", NoteFormatUtils.sanitizeMarkdownFileBaseName("Unknown"))
    }

    @Test
    fun rewriteRelativeImageRefsForMoveSkipsExternalAndAbsoluteLinks() {
        val markdown = """
            ![http](http://example.com/a.png)
            ![https](https://example.com/a.png)
            ![data](data:image/png;base64,abc)
            ![file](file:///sdcard/a.png)
            ![content](content://media/external/a.png)
            ![absolute](/storage/emulated/0/a.png)
            ![windows](C:\Images\a.png)
            ![local](images/a.png)
        """.trimIndent()

        val rewritten = NoteFormatUtils.rewriteRelativeImageRefsForMove(
            markdown = markdown,
            fromFolder = "notes/source",
            toFolder = "notes/target",
        )

        assertEquals(
            """
                ![http](http://example.com/a.png)
                ![https](https://example.com/a.png)
                ![data](data:image/png;base64,abc)
                ![file](file:///sdcard/a.png)
                ![content](content://media/external/a.png)
                ![absolute](/storage/emulated/0/a.png)
                ![windows](C:\Images\a.png)
                ![local](../source/images/a.png)
            """.trimIndent(),
            rewritten,
        )
    }
}
