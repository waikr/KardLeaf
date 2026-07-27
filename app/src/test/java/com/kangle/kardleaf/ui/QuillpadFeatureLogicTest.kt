package com.kangle.kardleaf.ui

import com.kangle.kardleaf.ui.editor.NoteSearchMatchRange
import com.kangle.kardleaf.ui.editor.buildCurrentReplacement
import com.kangle.kardleaf.ui.editor.buildNoteSearchMatches
import com.kangle.kardleaf.ui.editor.replaceAllNoteSearchMatches

import com.kangle.kardleaf.ui.editor.quillpad.parseQuillpadTags
import com.kangle.kardleaf.ui.editor.quillpad.quillpadNextLinePrefix
import com.kangle.kardleaf.ui.editor.quillpad.quillpadShouldEndList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.ui.editor.buildQuillpadLargePlainPreview
import org.qosp.notes.ui.editor.shouldSuppressQuillpadLiveMarkdown
import org.qosp.notes.ui.editor.shouldUseQuillpadLargePlainPreview
import org.qosp.notes.ui.editor.shouldReuseQuillpadToolbarOrder
import org.qosp.notes.ui.editor.visibleQuillpadToolbarItems

class QuillpadFeatureLogicTest {

    @Test
    fun continuesMarkdownLinesWithoutScanningTheRepository() {
        assertEquals("- ", quillpadNextLinePrefix("- item"))
        assertEquals("  - [ ] ", quillpadNextLinePrefix("  - [x] done"))
        assertEquals("10. ", quillpadNextLinePrefix("9. item"))
        assertEquals("    ", quillpadNextLinePrefix("    indented"))
        assertEquals(null, quillpadNextLinePrefix("plain text"))
        assertTrue(quillpadShouldEndList("- "))
        assertTrue(quillpadShouldEndList("  - [ ] "))
        assertTrue(quillpadShouldEndList("1. "))
        assertFalse(quillpadShouldEndList("- item"))
    }

    @Test
    fun searchFindsChineseEnglishAndSingleCharacters() {
        val text = "卡叶 note 卡"

        assertEquals(1, buildNoteSearchMatches(text, "卡叶", false, false).matches.size)
        assertEquals(1, buildNoteSearchMatches(text, "NOTE", false, false).matches.size)
        assertEquals(2, buildNoteSearchMatches(text, "卡", false, false).matches.size)
    }

    @Test
    fun replacesCurrentAndAllMatches() {
        val current = buildCurrentReplacement(
            text = "a1 a2",
            range = NoteSearchMatchRange(0, 2),
            query = "a(\\d)",
            replacement = "b\$1",
            useRegex = true,
            matchCase = true,
        )
        val all = replaceAllNoteSearchMatches("one ONE", "one", "two", false, false)

        assertEquals("b1", current.text)
        assertEquals("two two", all.text)
        assertEquals(2, all.count)
        assertNotNull(buildNoteSearchMatches("text", "[", true, false).errorMessage)
    }

    @Test
    fun suppressesLiveMarkdownOnlyForLargeNotes() {
        assertFalse(shouldSuppressQuillpadLiveMarkdown(5_000))
        assertTrue(shouldSuppressQuillpadLiveMarkdown(5_001))
    }

    @Test
    fun reusesToolbarWhenInflatedOrderAlreadyMatches() {
        assertTrue(shouldReuseQuillpadToolbarOrder(listOf(1, 2, 3), listOf(1, 2, 3)))
        assertFalse(shouldReuseQuillpadToolbarOrder(listOf(1, 3, 2), listOf(1, 2, 3)))
    }

    @Test
    fun usesTruncatedPlainPreviewForLargeNotes() {
        assertFalse(shouldUseQuillpadLargePlainPreview(50_000))
        assertTrue(shouldUseQuillpadLargePlainPreview(50_001))

        val preview = buildQuillpadLargePlainPreview("a".repeat(50_001))
        assertTrue(preview.startsWith("a".repeat(20_000)))
        assertTrue(preview.contains("大文本预览已截断"))
        assertTrue(preview.length < 50_001)
    }

    @Test
    fun filtersHiddenToolbarItemsAndNormalizesTags() {
        assertEquals(
            listOf("search", "history"),
            visibleQuillpadToolbarItems(
                configured = listOf("search"),
                defaults = listOf("search", "remarks", "history"),
                hidden = setOf("remarks"),
            ),
        )
        assertEquals(
            listOf("父/子", "中文 标签", "Tag"),
            parseQuillpadTags("#父/子，中文 标签,Tag,tag"),
        )
    }
}
