package com.kangle.kardleaf.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EditorActionsMindMapTest {
    private fun documentOf(content: String): MindMapDocument =
        requireNotNull(prepareMarkdownMindMap(content).document)

    @Test
    fun parsesStandardHeadingsAndUnlimitedLists() {
        val content = "正文前言\n# 根\n## A\n### A1\n- L3\n  - L4\n    - L5\n## B\n"
        val document = documentOf(content)

        assertEquals(listOf(0, 1, 2, 3, 4, 5, 1), document.nodes.map { it.depth })
        assertEquals(listOf(null, 0, 1, 2, 3, 4, 0), document.nodes.map { it.parentIndex })
        assertEquals(
            listOf(
                MindMapSourceKind.H1,
                MindMapSourceKind.H2,
                MindMapSourceKind.H3,
                MindMapSourceKind.LIST,
                MindMapSourceKind.LIST,
                MindMapSourceKind.LIST,
                MindMapSourceKind.H2,
            ),
            document.nodes.map { it.sourceKind },
        )
        assertEquals(content.indexOf("    - L5"), document.nodes[5].sourceOffset)
    }

    @Test
    fun ignoresStructureInsideFencedCode() {
        val content = "# 根\n```md\n## 不是节点\n- 也不是节点\n```\n## A\n"
        assertEquals(listOf("根", "A"), documentOf(content).nodes.map { it.text })
    }

    @Test
    fun rejectsNonStandardStructures() {
        val invalidDocuments = listOf(
            "## A\n" to "不是 H1",
            "# 根\n# 第二个根\n" to "第二个 H1",
            "# 根\n### 跳级\n" to "不能跳级",
            "# 根\n## A\n- 缺少 H3\n" to "不能跳级",
            "# 根\n## A\n### A1\n - 奇数缩进\n" to "不是 2 的倍数",
            "# 根\n## A\n### A1\n* 非标准标记\n" to "统一使用 -",
            "# 根\n#### H4\n" to "仅允许 H1-H3",
        )

        invalidDocuments.forEach { (content, expectedMessage) ->
            val result = prepareMarkdownMindMap(content)
            assertNull(result.document)
            assertEquals("非标准思维导图格式", result.unavailableTitle)
            require(result.unavailableMessage.orEmpty().contains(expectedMessage)) {
                "Expected '$expectedMessage' in '${result.unavailableMessage}'"
            }
        }
    }

    @Test
    fun formatsEveryDepthWithOneRule() {
        assertEquals("# 根", formatMindMapNodeLine(0, "根"))
        assertEquals("## A", formatMindMapNodeLine(1, "A"))
        assertEquals("### B", formatMindMapNodeLine(2, "B"))
        assertEquals("- C", formatMindMapNodeLine(3, "C"))
        assertEquals("  - D", formatMindMapNodeLine(4, "D"))
        assertEquals("    - E", formatMindMapNodeLine(5, "E"))
    }

    @Test
    fun createsParseableRootForEmptyMindMap() {
        val result = requireNotNull(createMindMapRoot())

        assertEquals("# 中心主题", result.content)
        assertEquals("中心主题", documentOf(result.content).root.text)
        assertEquals(2, result.selection.start)
        assertEquals(result.content.length, result.selection.end)
    }

    @Test
    fun countsAllNodesForRenderLimit() {
        fun contentWithNodeCount(count: Int) = buildString {
            append("# 根\n## A\n### A1\n")
            repeat(count - 3) { append("- N$it\n") }
        }

        assertNotNull(prepareMarkdownMindMap(contentWithNodeCount(200)).document)
        val blocked = prepareMarkdownMindMap(contentWithNodeCount(201))
        assertNull(blocked.document)
        assertEquals("节点过多", blocked.unavailableTitle)
    }

    @Test
    fun addsChildrenAndSiblingUsingDepthFormat() {
        val content = "# 根\n## A\n### A1\n- L3\n## B\n"
        val document = documentOf(content)
        val headingChild = addMindMapChild(document, 2, "列表孩子")
        val listChild = addMindMapChild(document, 3, "深层孩子")
        val sibling = addMindMapSibling(document, 1, "A 的同级")

        assertEquals("# 根\n## A\n### A1\n- L3\n- 列表孩子\n## B\n", headingChild!!.content)
        assertEquals("# 根\n## A\n### A1\n- L3\n  - 深层孩子\n## B\n", listChild!!.content)
        assertEquals("# 根\n## A\n### A1\n- L3\n## A 的同级\n## B\n", sibling!!.content)
        assertEquals(4, headingChild.nodeIndex)
        assertEquals(4, listChild.nodeIndex)
        assertEquals(4, sibling.nodeIndex)
        assertNull(addMindMapSibling(document, 0, "第二个根"))
    }

    @Test
    fun movesSiblingSubtreeWithBodyText() {
        val content = "# 根\n## A\n### A1\nA 正文\n## B\nB 正文"
        val result = moveMindMapSubtree(documentOf(content), 3, moveUp = true)

        assertEquals("# 根\n## B\nB 正文\n## A\n### A1\nA 正文\n", result!!.content)
        assertNull(moveMindMapSubtree(documentOf(content), 1, moveUp = true))
    }

    @Test
    fun renamesRootAndDeepListWithoutChangingFormat() {
        val content = "# 根\n## A\n### A1\n- L3\n  - L4\n"
        val root = renameMindMapNode(documentOf(content), 0, "新根")
        val deep = renameMindMapNode(documentOf(content), 4, "新 L4")

        assertEquals("# 新根\n## A\n### A1\n- L3\n  - L4\n", root!!.content)
        assertEquals("# 根\n## A\n### A1\n- L3\n  - 新 L4\n", deep!!.content)
    }

    @Test
    fun deletesNodeWithItsSubtreeAndBody() {
        val content = "# 根\n## A\nA 正文\n### A1\n- L3\n## B\n"
        val result = deleteMindMapSubtree(documentOf(content), 1)

        assertEquals("# 根\n## B\n", result!!.content)
        assertNull(deleteMindMapSubtree(documentOf(content), 0))
    }

    @Test
    fun reparentsAcrossHeadingAndListBoundary() {
        val toRoot = "# 根\n## A\n### A1\n- C\n## B\n"
        val movedToRoot = reparentMindMapSubtree(documentOf(toRoot), 3, 0)
        assertEquals("# 根\n## A\n### A1\n## B\n## C\n", movedToRoot!!.content)

        val underHeading = "# 根\n## A\n### A1\n## C\n"
        val movedUnderHeading = reparentMindMapSubtree(documentOf(underHeading), 3, 2)
        assertEquals("# 根\n## A\n### A1\n- C\n", movedUnderHeading!!.content)
    }

    @Test
    fun reparentsWholeSubtreeAndRejectsCycles() {
        val content = "# 根\n## A\n### A1\n## B\n### B1\n"
        val document = documentOf(content)
        val result = reparentMindMapSubtree(document, 1, 3)

        assertEquals("# 根\n## B\n### B1\n### A\n- A1\n", result!!.content)
        assertEquals("A", result.nodeTitle)
        assertEquals("B", result.contextTitle)
        assertNull(reparentMindMapSubtree(document, 1, 2))
    }
}
