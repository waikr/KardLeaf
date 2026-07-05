package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory

data class MarkdownHeading(
    val level: Int,
    val text: String,
    val startOffset: Int,
    val lineIndex: Int,
)

data class SearchMatch(
    val scope: String,
    val snippet: String,
    val startOffset: Int = -1,
)

private val headingRegex = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
private val wikiLinkRegex = Regex("""!?\[\[([^\]]+)]]""")
private val markdownLinkRegex = Regex("""(?<!!)\[[^]]+]\(([^)]+)\)""")
private val tagRegex = Regex("""(?<![\w/])#([A-Za-z0-9_\-/\u4e00-\u9fa5]+)""")
private val snippetHeadingPrefixRegex = Regex("""^#{1,6}\s+""")
private val snippetTaskPrefixRegex = Regex("""^\s*[-*+]\s+\[[ xX]]\s+""")
private val snippetBulletPrefixRegex = Regex("""^\s*[-*+]\s+""")
private val snippetOrderedListPrefixRegex = Regex("""^\s*\d+\.\s+""")
private val snippetMarkdownTokenRegex = Regex("""[*_`~>]""")

fun extractMarkdownHeadings(content: String): List<MarkdownHeading> {
    val headings = mutableListOf<MarkdownHeading>()
    var offset = 0
    var lineIndex = 0
    while (offset <= content.length) {
        val newlineIndex = content.indexOfAny(charArrayOf('\n', '\r'), startIndex = offset)
        val lineEnd = if (newlineIndex >= 0) newlineIndex else content.length
        val line = content.substring(offset, lineEnd)
        val leadingWhitespace = line.length - line.trimStart().length
        val match = headingRegex.find(line.trim())
        if (match != null) {
            headings += MarkdownHeading(
                level = match.groupValues[1].length,
                text = match.groupValues[2].trim(),
                startOffset = offset + leadingWhitespace,
                lineIndex = lineIndex,
            )
        }
        if (newlineIndex < 0) break
        offset = if (content[newlineIndex] == '\r' && content.getOrNull(newlineIndex + 1) == '\n') {
            newlineIndex + 2
        } else {
            newlineIndex + 1
        }
        lineIndex++
    }
    return headings
}

fun extractObsidianLinks(content: String): List<String> =
    wikiLinkRegex.findAll(content)
        .map { match ->
            match.groupValues[1]
                .substringBefore("|")
                .substringBefore("#")
                .substringBefore("^")
                .trim()
        }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

fun extractObsidianTags(content: String): List<String> =
    tagRegex.findAll(content)
        .map { it.groupValues[1].trim('/') }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()

fun noteMatchesObsidianTarget(
    note: Note,
    target: String,
): Boolean {
    val normalizedTarget = normalizeObsidianName(target)
    if (normalizedTarget.isBlank()) return false
    return normalizeObsidianName(note.title) == normalizedTarget ||
        normalizeObsidianName(note.file.nameWithoutExtension) == normalizedTarget ||
        normalizeObsidianName(note.file.path.replace("\\", "/").removeSuffix(".md")).endsWith("/$normalizedTarget")
}

fun findSearchMatch(
    note: Note,
    query: String,
    histories: List<NoteHistory> = emptyList(),
): SearchMatch? {
    val q = query.trim()
    if (q.isBlank()) return null
    val tags by lazy { extractObsidianTags(note.content) }
    val matchedHistory by lazy {
        histories.firstOrNull { it.noteId == note.id && (it.title.contains(q, true) || it.content.contains(q, true)) }
    }
    return when {
        note.title.contains(q, ignoreCase = true) -> SearchMatch("标题", note.title)
        note.folder.replace("\\", "/").contains(q, ignoreCase = true) -> SearchMatch("文件夹", note.folder.replace("\\", "/"))
        tags.any { it.contains(q.removePrefix("#"), ignoreCase = true) } ->
            SearchMatch("标签", tags.joinToString(" ") { "#$it" })
        note.content.contains(q, ignoreCase = true) -> SearchMatch("正文", buildSearchSnippet(note.content, q), note.content.indexOf(q, ignoreCase = true))
        matchedHistory != null -> {
            val history = matchedHistory!!
            SearchMatch("历史版本", buildSearchSnippet("${history.title}\n${history.content}", q))
        }
        else -> null
    }
}

fun stripMarkdownForSnippet(content: String): String =
    content
        .replace(wikiLinkRegex) { match ->
            match.groupValues[1].substringAfter("|").substringBefore("#").trim()
        }
        .replace(markdownLinkRegex) { match -> match.groupValues[1] }
        .lineSequence()
        .map { line ->
            line
                .replace(snippetHeadingPrefixRegex, "")
                .replace(snippetTaskPrefixRegex, "")
                .replace(snippetBulletPrefixRegex, "")
                .replace(snippetOrderedListPrefixRegex, "")
                .replace(snippetMarkdownTokenRegex, "")
                .trim()
        }
        .filter { it.isNotBlank() }
        .joinToString("\n")

fun buildSearchSnippet(
    content: String,
    query: String,
): String {
    val plain = stripMarkdownForSnippet(content)
    val index = plain.indexOf(query, ignoreCase = true)
    if (index < 0) return plain.take(180)
    val start = (index - 60).coerceAtLeast(0)
    val end = (index + query.length + 90).coerceAtMost(plain.length)
    val prefix = if (start > 0) "..." else ""
    val suffix = if (end < plain.length) "..." else ""
    return prefix + plain.substring(start, end).trim() + suffix
}

private fun normalizeObsidianName(value: String): String =
    value
        .replace("\\", "/")
        .substringAfterLast("/")
        .removeSuffix(".md")
        .trim()
        .lowercase()
