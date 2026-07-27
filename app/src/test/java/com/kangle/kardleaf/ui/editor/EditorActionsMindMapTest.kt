package com.kangle.kardleaf.ui.editor

import com.kangle.kardleaf.ui.extractMarkdownHeadings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EditorActionsMindMapTest {
    private fun headingsOf(content: String) = extractMarkdownHeadings(content)

    @Test
    fun addSiblingInsertsAfterAnchorSubtree() {
        val content = "# 根\n## A\n### A1\n## B\n"
        val headings = headingsOf(content)
        val anchorIndex = headings.indexOfFirst { it.text == "A" }
        val result = addMarkdownHeadingSiblingAfter(content, headings, anchorIndex, "新同级")
        assertNotNull(result)
        assertEquals("# 根\n## A\n### A1\n## 新同级\n## B\n", result!!.content)
        assertEquals("A", result.anchorTitle)
    }

    @Test
    fun addSiblingAtDocumentEndWithoutTrailingNewline() {
        val content = "# 根\n## A"
        val headings = headingsOf(content)
        val anchorIndex = headings.indexOfFirst { it.text == "A" }
        val result = addMarkdownHeadingSiblingAfter(content, headings, anchorIndex, "尾部同级")
        assertNotNull(result)
        assertEquals("# 根\n## A\n## 尾部同级\n", result!!.content)
    }

    @Test
    fun addSiblingRejectsBlankTitleOrBadIndex() {
        val content = "# 根\n## A\n"
        val headings = headingsOf(content)
        assertNull(addMarkdownHeadingSiblingAfter(content, headings, 1, "   "))
        assertNull(addMarkdownHeadingSiblingAfter(content, headings, 99, "X"))
    }

    @Test
    fun moveUpSwapsWithPreviousSiblingSubtree() {
        val content = "# 根\n## A\n### A1\n## B\n### B1\n"
        val headings = headingsOf(content)
        val movingIndex = headings.indexOfFirst { it.text == "B" }
        val result = moveMarkdownHeadingSubtree(content, headings, movingIndex, moveUp = true)
        assertNotNull(result)
        assertEquals("# 根\n## B\n### B1\n## A\n### A1\n", result!!.content)
        assertEquals("B", result.movedTitle)
    }

    @Test
    fun moveDownSwapsWithNextSiblingSubtree() {
        val content = "# 根\n## A\n### A1\n## B\n### B1\n"
        val headings = headingsOf(content)
        val movingIndex = headings.indexOfFirst { it.text == "A" }
        val result = moveMarkdownHeadingSubtree(content, headings, movingIndex, moveUp = false)
        assertNotNull(result)
        assertEquals("# 根\n## B\n### B1\n## A\n### A1\n", result!!.content)
    }

    @Test
    fun moveDownAtDocumentEndWithoutTrailingNewline() {
        val content = "# 根\n## A\n## B"
        val headings = headingsOf(content)
        val movingIndex = headings.indexOfFirst { it.text == "A" }
        val result = moveMarkdownHeadingSubtree(content, headings, movingIndex, moveUp = false)
        assertNotNull(result)
        assertEquals("# 根\n## B\n## A\n", result!!.content)
    }

    @Test
    fun moveUpAtFirstSiblingReturnsNull() {
        val content = "# 根\n## A\n## B\n"
        val headings = headingsOf(content)
        val movingIndex = headings.indexOfFirst { it.text == "A" }
        assertNull(moveMarkdownHeadingSubtree(content, headings, movingIndex, moveUp = true))
    }

    @Test
    fun moveDownAtLastSiblingReturnsNull() {
        val content = "# 根\n## A\n## B\n"
        val headings = headingsOf(content)
        val movingIndex = headings.indexOfFirst { it.text == "B" }
        assertNull(moveMarkdownHeadingSubtree(content, headings, movingIndex, moveUp = false))
    }

    @Test
    fun moveDoesNotCrossParentBoundary() {
        // B 的上一个标题是 A1（更深层级），A1 不是同级，不能与之交换。
        val content = "# 根\n## A\n### A1\n# 根2\n## B\n"
        val headings = headingsOf(content)
        val movingIndex = headings.indexOfFirst { it.text == "B" }
        assertNull(moveMarkdownHeadingSubtree(content, headings, movingIndex, moveUp = true))
    }

    @Test
    fun moveKeepsBodyTextWithHeadings() {
        val content = "# 根\n## A\nA 的正文\n## B\nB 的正文\n"
        val headings = headingsOf(content)
        val movingIndex = headings.indexOfFirst { it.text == "B" }
        val result = moveMarkdownHeadingSubtree(content, headings, movingIndex, moveUp = true)
        assertNotNull(result)
        assertEquals("# 根\n## B\nB 的正文\n## A\nA 的正文\n", result!!.content)
    }
}
