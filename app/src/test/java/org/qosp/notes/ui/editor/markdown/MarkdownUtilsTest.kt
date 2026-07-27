package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownUtilsTest {
    @Test
    fun formatsInlineAndBlockMath() {
        assertEquals("\$x\$", mathMarkdown("x"))
        assertEquals("\$\$\nx + y\nz\n\$\$", mathMarkdown("x + y\nz"))
    }
}
