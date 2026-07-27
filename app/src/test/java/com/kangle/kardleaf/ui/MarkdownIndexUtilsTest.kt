package com.kangle.kardleaf.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownIndexUtilsTest {
    @Test
    fun parsesAliasesPathsAndFragmentsWithOffsets() {
        val content = "[[目标]] [[工作/会议|工作会议]] [[目标#标题]] [[目标#^block]]"
        val links = parseObsidianLinks(content)

        assertEquals(4, links.size)
        assertEquals("目标", links[0].target)
        assertEquals("工作/会议", links[1].target)
        assertEquals("工作会议", links[1].alias)
        assertEquals("标题", links[2].heading)
        assertEquals("block", links[3].blockId)
        assertEquals("目标", content.substring(links[0].startOffset + 2, links[0].endOffset - 2))
    }

    @Test
    fun ignoresFrontMatterFencedCodeInlineCodeAndEmbeds() {
        val content = """
            ---
            link: [[front-matter]]
            ---
            `[[inline]]`
            ```markdown
            [[fenced]]
            ```
            ![[image.png]]
            [[real]]
        """.trimIndent()

        assertEquals(listOf("real"), extractObsidianLinks(content))
        assertTrue(parseObsidianLinks(content).single().startOffset > 0)
    }

    @Test
    fun normalizesSlashAndMarkdownExtension() {
        assertEquals("work/meeting", normalizeObsidianTarget("\\work\\Meeting.md"))
        assertEquals("work/meeting", normalizeObsidianTarget("WORK\\Meeting.MD"))
    }

    @Test
    fun classifiesEmbedsAndEscapedLinksWithoutIndexingThem() {
        val tokens = parseObsidianLinkTokens("![[image.png]] ![[Other note]] \\[[escaped]] [[real]]")

        assertEquals(
            listOf(
                ObsidianLinkKind.EMBED_IMAGE_OR_FILE,
                ObsidianLinkKind.EMBED_NOTE,
                ObsidianLinkKind.WIKILINK,
            ),
            tokens.filter { it.kind != ObsidianLinkKind.CODE_TEXT }.map { it.kind },
        )
        assertEquals(listOf("real"), extractObsidianLinks("![[image.png]] ![[Other note]] \\[[escaped]] [[real]]"))
    }
}
