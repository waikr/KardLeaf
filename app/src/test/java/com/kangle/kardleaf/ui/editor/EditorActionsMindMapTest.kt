package com.kangle.kardleaf.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun keepsHashCharactersUnlessClosingHashIsSeparated() {
        assertEquals("C#", documentOf("# C#\n").root.text)
        assertEquals("C##", documentOf("# C##\n").root.text)
        assertEquals("C", documentOf("# C #\n").root.text)
        assertEquals("正常标题", documentOf("# 正常标题 ###\n").root.text)
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
    fun movesSubtreeIntoInsertionGapAndAdjustsDepth() {
        val content = "# 根\n## A\n### A1\n## B\n## C\n"
        val betweenSiblings = moveMindMapSubtreeToPosition(documentOf(content), 1, 0, 1)
        assertEquals("# 根\n## B\n## A\n### A1\n## C\n", betweenSiblings!!.content)

        val acrossParents = moveMindMapSubtreeToPosition(
            documentOf("# 根\n## A\n### A1\n## B\n### B1\n"),
            movingIndex = 1,
            targetParentIndex = 3,
            targetChildIndex = 0,
        )
        assertEquals("# 根\n## B\n### A\n- A1\n### B1\n", acrossParents!!.content)

        val crossParentGap = moveMindMapSubtreeToPosition(
            documentOf("# 根\n## 目标\n### 节点1\n### 节点2\n## 来源\n### 节点10\n"),
            movingIndex = 5,
            targetParentIndex = 1,
            targetChildIndex = 1,
        )
        assertEquals(
            "# 根\n## 目标\n### 节点1\n### 节点10\n### 节点2\n## 来源\n",
            crossParentGap!!.content,
        )
    }

    @Test
    fun keepsExactSiblingInsertionSemantics() {
        val content = "# 根\n## A\n## B\n## C\n## D\n"

        assertEquals(
            "# 根\n## A\n## D\n## B\n## C\n",
            moveMindMapSubtreeToPosition(documentOf(content), 4, 0, 1)!!.content,
        )
        assertEquals(
            "# 根\n## A\n## C\n## B\n## D\n",
            moveMindMapSubtreeToPosition(documentOf(content), 2, 0, 2)!!.content,
        )
        assertEquals(
            "# 根\n## A\n## C\n## B\n## D\n",
            moveMindMapSubtreeToPosition(documentOf(content), 2, 0, 2)!!.content,
        )
        assertEquals(
            "# 根\n## A\n## C\n## D\n## B\n",
            moveMindMapSubtreeToPosition(documentOf(content), 2, 0, 3)!!.content,
        )
    }

    @Test
    fun usesParentAndFinalChildIndexAsTheMoveIdentity() {
        val content = "# 根\n## A\n### X\n## B\n### Y\n"
        val document = documentOf(content)

        assertEquals(
            "# 根\n## A\n## B\n### X\n### Y\n",
            moveMindMapSubtreeToPosition(document, 2, 3, 0)!!.content,
        )
        assertNull(moveMindMapSubtreeToPosition(document, 2, 1, 0))
        assertNull(moveMindMapSubtreeToPosition(document, 2, 3, -1))
        assertNull(moveMindMapSubtreeToPosition(document, 2, 3, 2))
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
    fun combinesPendingRenameWithNodeInsertionWithoutBreakingListIndentation() {
        val document = documentOf("# 根\n## A\n### A1\n- L3\n  - L4\n")

        val child = addMindMapChildWithPendingRename(document, 4, "新增子节点", 4, "重命名")
        val sibling = addMindMapSiblingWithPendingRename(document, 4, "新增同级", 4, "重命名")

        assertEquals("# 根\n## A\n### A1\n- L3\n  - 重命名\n    - 新增子节点\n", child!!.content)
        assertEquals("# 根\n## A\n### A1\n- L3\n  - 重命名\n  - 新增同级\n", sibling!!.content)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), documentOf(child.content).nodes.map { it.depth })
        assertEquals(listOf(0, 1, 2, 3, 4, 4), documentOf(sibling.content).nodes.map { it.depth })
    }

    @Test
    fun deletesNodeWithItsSubtreeAndBody() {
        val content = "# 根\n## A\nA 正文\n### A1\n- L3\n## B\n"
        val result = deleteMindMapSubtree(documentOf(content), 1)

        assertEquals("# 根\n## B\n", result!!.content)
        assertNull(deleteMindMapSubtree(documentOf(content), 0))
    }

    @Test
    fun movesAcrossHeadingAndListBoundary() {
        val toRoot = "# 根\n## A\n### A1\n- C\n## B\n"
        val movedToRoot = moveMindMapSubtreeToPosition(documentOf(toRoot), 3, 0, 2)
        assertEquals("# 根\n## A\n### A1\n## B\n## C\n", movedToRoot!!.content)

        val underHeading = "# 根\n## A\n### A1\n## C\n"
        val movedUnderHeading = moveMindMapSubtreeToPosition(documentOf(underHeading), 3, 1, 1)
        assertEquals("# 根\n## A\n### A1\n### C\n", movedUnderHeading!!.content)
    }

    @Test
    fun movesSubtreeIntoTargetNodeAsChild() {
        val content = "# 根\n## A\n### A1\n## B\n"

        val result = moveMindMapSubtreeToPosition(
            documentOf(content),
            movingIndex = 3,
            targetParentIndex = 1,
            targetChildIndex = 1,
        )

        assertEquals("# 根\n## A\n### A1\n### B\n", result!!.content)
        assertEquals(
            "# 根\n## A\n### A2\n### A3\n### A1\n",
            moveMindMapSubtreeToPosition(
                documentOf("# 根\n## A\n### A1\n### A2\n### A3\n"),
                movingIndex = 2,
                targetParentIndex = 1,
                targetChildIndex = 2,
            )!!.content,
        )
        assertNull(moveMindMapSubtreeToPosition(documentOf(content), 1, 2, 0))
    }

    @Test
    fun movesEverySiblingIntoTheSameTargetParent() {
        val content =
            "# 根\n## 节点1\n## 节点7\n## 节点9\n" +
                "### 节点10\n### 节点11\n### 节点12\n### 节点13\n### 节点14\n"

        listOf("节点10", "节点11", "节点12", "节点13", "节点14").forEach { title ->
            val document = documentOf(content)
            val movingIndex = document.nodes.single { it.text == title }.index
            val targetParentIndex = document.nodes.single { it.text == "节点7" }.index
            val result = requireNotNull(
                moveMindMapSubtreeToPosition(document, movingIndex, targetParentIndex, 0),
            )
            val updatedDocument = documentOf(result.content)
            val movedNode = updatedDocument.nodes.single { it.text == title }

            assertEquals("节点7", updatedDocument.nodes[requireNotNull(movedNode.parentIndex)].text)
        }
    }

    @Test
    fun movesWholeSubtreeAndRejectsCycles() {
        val content = "# 根\n## A\n### A1\n## B\n### B1\n"
        val document = documentOf(content)
        val result = moveMindMapSubtreeToPosition(document, 1, 3, 0)

        assertEquals("# 根\n## B\n### A\n- A1\n### B1\n", result!!.content)
        assertEquals("A", result.nodeTitle)
        assertEquals("B", result.contextTitle)
        assertNull(moveMindMapSubtreeToPosition(document, 1, 1, 0))
        assertNull(moveMindMapSubtreeToPosition(document, 1, 2, 0))
    }

    @Test
    fun keepsCrLfForAddMoveAndSlotMove() {
        fun assertCrLfOnly(content: String) {
            assertFalse(content.replace("\r\n", "").contains('\n'))
            assertFalse(content.replace("\r\n", "").contains('\r'))
        }

        val content = "# 根\r\n## A\r\n### A1\r\n## B\r\n"
        assertCrLfOnly(requireNotNull(addMindMapChild(documentOf(content), 1, "A2")).content)
        assertCrLfOnly(requireNotNull(moveMindMapSubtree(documentOf(content), 1, moveUp = false)).content)
        val moved = requireNotNull(moveMindMapSubtreeToPosition(documentOf(content), 1, 0, 1)).content
        assertCrLfOnly(moved)
        assertEquals("# 根\r\n## B\r\n## A\r\n### A1\r\n", moved)
    }

    @Test
    fun mutationPreflightRejectsNodeAndContentLimits() {
        val nodeLimited = buildString {
            append("# 根\n## A\n### A1\n")
            repeat(197) { append("- N$it\n") }
        }
        val nodeMutation = requireNotNull(addMindMapChild(documentOf(nodeLimited), 0, "超限"))
        val blockedNodes = prepareMarkdownMindMap(nodeMutation.content)
        assertNull(blockedNodes.document)
        assertEquals("节点过多", blockedNodes.unavailableTitle)

        val longDocument = documentOf("# 根\n" + "x".repeat(79_996))
        val contentMutation = requireNotNull(addMindMapChild(longDocument, 0, "超限"))
        val blockedContent = prepareMarkdownMindMap(contentMutation.content)
        assertNull(blockedContent.document)
        assertEquals("笔记过大", blockedContent.unavailableTitle)
        assertTrue(contentMutation.content.length > 80_000)
    }
}
