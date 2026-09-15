package com.kangle.kardleaf.ui.editor.selection

import org.junit.Assert.*
import org.junit.Test

class TextSelectionToolbarTest {
    @Test fun settingsAndPagingMatchMarkText() {
        assertEquals(TextSelectionToolbarSettings(1, listOf("toggleBold", "toggleCode")),
            TextSelectionToolbarSettings.normalize(3, "toggleBold,unknown,toggleBold,toggleCode"))
        assertEquals(2, TextSelectionToolbarSettings.normalize("2", "").rows)
        assertFalse(TextSelectionToolbarSettings.normalize(false, 1, "").enabled)
        val commands = (0..11).toList()
        for (width in listOf(240f, 320f, 400f, 800f)) for (back in listOf(true, false)) {
            val cap = TextSelectionToolbarSettings.capacity(width)
            val pages = TextSelectionToolbarSettings.pages(commands, cap, back)
            assertEquals(commands, pages.flatten())
            pages.forEachIndexed { index, page ->
                assertTrue(page.size + (if (back || index > 0) 1 else 0) + (if (index < pages.lastIndex) 1 else 0) <= cap)
            }
        }
    }
    @Test fun rangeCommandsPreserveTextAndMatchCodeMirrorSemantics() {
        val quote = selectionBlockChange("alpha\nbeta", 0, 10, "toggleBlockquote")!!
        assertEquals("> alpha\n> beta", quote.text)
        assertEquals(14, quote.end)
        assertEquals("alpha\nbeta", selectionBlockChange(quote.text, quote.start, quote.end, "toggleBlockquote")!!.text)
        assertEquals("> alpha\n> beta", selectionBlockChange("> alpha\nbeta", 0, 12, "toggleBlockquote")!!.text)
        assertEquals("1. alpha\n2. beta", selectionBlockChange("alpha\nbeta", 0, 10, "toggleOrderedList")!!.text)
        assertEquals("alpha\nbeta", selectionBlockChange("1. alpha\n2. beta", 0, 16, "toggleOrderedList")!!.text)
        assertEquals("  - [ ] alpha\n  - [ ] beta", selectionBlockChange("  - alpha\n  2. beta", 0, 19, "toggleCheckList")!!.text)
        assertEquals("- ", selectionBlockChange("", 0, 0, "toggleUnorderedList")!!.text)
        assertNull(selectionBlockChange("alpha", 0, 5, "toggleBold"))
    }
}
