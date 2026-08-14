package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteSearchOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Date

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

    @Test
    fun appliesSearchScopeCaseRegexAndMetadataFilters() {
        val note =
            Note(
                file = File("Work/Entry.md"),
                title = "Release Plan",
                content = "Build CODE-2048 today",
                lastModified = Date(0),
                color = 0,
                tags = listOf("shipping"),
            )

        assertNull(
            findSearchMatch(
                note,
                "code",
                options = NoteSearchOptions(matchCase = true, matchTitle = false),
            ),
        )
        assertEquals(
            "正文",
            findSearchMatch(
                note,
                "CODE-\\d+",
                options = NoteSearchOptions(useRegex = true, matchTitle = false),
            )?.scope,
        )
        assertNull(findSearchMatch(note, "[", options = NoteSearchOptions(useRegex = true)))
        assertEquals(
            "标签",
            findSearchMatch(
                note,
                "",
                options = NoteSearchOptions(tag = "shipping", folder = "Work"),
            )?.scope,
        )
        assertEquals(
            "标题",
            findSearchMatch(
                note,
                "plan",
                options = NoteSearchOptions(matchContent = false),
            )?.scope,
        )
    }
}
