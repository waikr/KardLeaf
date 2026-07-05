package com.kangle.kardleaf.data.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteFormatUtilsTest {
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
