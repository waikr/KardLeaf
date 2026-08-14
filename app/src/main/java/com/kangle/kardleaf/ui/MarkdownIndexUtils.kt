package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteSearchOptions
import java.util.Locale

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

enum class ObsidianLinkKind {
    WIKILINK,
    EMBED_NOTE,
    EMBED_IMAGE_OR_FILE,
    CODE_TEXT,
}

/** A note-level wikilink occurrence extracted from Markdown source. */
data class ObsidianLink(
    val rawTarget: String,
    val target: String,
    val alias: String?,
    val heading: String?,
    val blockId: String?,
    val startOffset: Int,
    val endOffset: Int,
    val contextSnippet: String,
    val kind: ObsidianLinkKind = ObsidianLinkKind.WIKILINK,
)

private val headingRegex = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
private val wikiLinkRegex = Regex("""(!)?\[\[([^\[\]\n]+)]]""")
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
    parseObsidianLinks(content)
        .map { it.target }
        .filter { it.isNotBlank() }
        .distinct()

/**
 * Parses note wikilinks while ignoring YAML front matter, fenced code and inline code.
 * Image/note embeds are intentionally excluded from the note-link index.
 */
fun parseObsidianLinks(content: String): List<ObsidianLink> {
    if (content.isBlank()) return emptyList()
    return parseObsidianLinkTokens(content)
        .filter { it.kind == ObsidianLinkKind.WIKILINK }
}

/**
 * Tokenizes all Obsidian-style `[[...]]` forms without turning embeds or code
 * text into ordinary note links. The indexer uses [parseObsidianLinks], while
 * renderers and diagnostics can use this richer classification.
 */
fun parseObsidianLinkTokens(content: String): List<ObsidianLink> {
    if (content.isBlank()) return emptyList()
    val ignoredRanges = markdownIgnoredRanges(content)
    return wikiLinkRegex.findAll(content).mapNotNull { match ->
        val start = match.range.first
        val rawInner = match.groupValues[2]
        val isIgnored = isEscaped(content, start) || ignoredRanges.any { start in it } || isInsideInlineCode(content, start)
        val kind = when {
            isIgnored -> ObsidianLinkKind.CODE_TEXT
            match.groupValues[1] == "!" && isLikelyImageOrFileTarget(rawInner) -> ObsidianLinkKind.EMBED_IMAGE_OR_FILE
            match.groupValues[1] == "!" -> ObsidianLinkKind.EMBED_NOTE
            else -> ObsidianLinkKind.WIKILINK
        }
        val aliasSplit = rawInner.indexOfUnescaped('|')
        val targetAndFragment = if (aliasSplit >= 0) rawInner.substring(0, aliasSplit) else rawInner
        val alias = aliasSplit.takeIf { it >= 0 }
            ?.let { rawInner.substring(it + 1).trim().takeIf(String::isNotBlank) }
        val fragmentIndex = targetAndFragment.indexOfUnescaped('#')
        val target = unescapeObsidianText(
            if (fragmentIndex >= 0) targetAndFragment.substring(0, fragmentIndex) else targetAndFragment,
        ).trim()
        if (target.isBlank()) return@mapNotNull null
        val fragment = fragmentIndex.takeIf { it >= 0 }
            ?.let { unescapeObsidianText(targetAndFragment.substring(it + 1)).trim() }
            ?.takeIf(String::isNotBlank)
        val blockId = fragment?.takeIf { it.startsWith('^') }?.removePrefix("^")?.takeIf(String::isNotBlank)
        val heading = fragment?.takeUnless { it.startsWith('^') }
        val lineStart = content.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = content.indexOf('\n', match.range.last + 1).let { if (it < 0) content.length else it }
        val snippet = content.substring(lineStart, lineEnd).trim().take(240)
        ObsidianLink(
            rawTarget = rawInner,
            target = target,
            alias = alias,
            heading = heading,
            blockId = blockId,
            startOffset = start,
            endOffset = match.range.last + 1,
            contextSnippet = snippet,
            kind = kind,
        )
    }.toList()
}

private fun isLikelyImageOrFileTarget(rawInner: String): Boolean {
    val target = rawInner.substringBefore('|').substringBefore('#').trim()
    return target.substringAfterLast('/').contains('.')
}

private fun isEscaped(content: String, position: Int): Boolean {
    var slashes = 0
    var index = position - 1
    while (index >= 0 && content[index] == '\\') {
        slashes++
        index--
    }
    return slashes % 2 == 1
}

private fun unescapeObsidianText(value: String): String =
    value.replace("\\\\", "\\").replace("\\|", "|").replace("\\#", "#")

private fun markdownIgnoredRanges(content: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var offset = 0
    var inFence = false
    var fenceStart = -1
    var frontMatterDone = !content.startsWith("---")
    while (offset <= content.length) {
        val newline = content.indexOf('\n', offset)
        val lineEnd = if (newline >= 0) newline else content.length
        val line = content.substring(offset, lineEnd).removeSuffix("\r")
        val trimmed = line.trimStart()
        val isFence = trimmed.startsWith("```") || trimmed.startsWith("~~~")
        if (!frontMatterDone) {
            if (offset > 0 && line.trim() == "---") {
                ranges += 0..lineEnd
                frontMatterDone = true
            } else {
                ranges += offset..lineEnd
            }
        } else if (isFence) {
            if (!inFence) {
                inFence = true
                fenceStart = offset
            } else {
                ranges += fenceStart..lineEnd
                inFence = false
                fenceStart = -1
            }
        } else if (inFence && newline < 0) {
            ranges += fenceStart..lineEnd
        }
        if (newline < 0) break
        offset = newline + 1
    }
    if (inFence && fenceStart >= 0) ranges += fenceStart..content.length
    return ranges
}

private fun isInsideInlineCode(content: String, position: Int): Boolean {
    val lineStart = content.lastIndexOf('\n', position - 1).let { if (it < 0) 0 else it + 1 }
    var index = lineStart
    var delimiterLength = 0
    while (index < position) {
        if (content[index] != '`') {
            index++
            continue
        }
        val runStart = index
        while (index < position && content[index] == '`') index++
        val runLength = index - runStart
        delimiterLength = if (delimiterLength == 0) runLength else if (runLength == delimiterLength) 0 else delimiterLength
    }
    return delimiterLength > 0
}

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
    options: NoteSearchOptions = NoteSearchOptions(),
    compiledRegex: Regex? = null,
): SearchMatch? {
    val q = query.trim()
    val folder = note.folder.replace("\\", "/")
    if (options.folder != null && folder != options.folder) return null
    if (options.tag != null && note.tags.none { it.equals(options.tag, ignoreCase = true) }) return null
    if (q.isBlank()) {
        return when {
            options.tag != null -> SearchMatch("标签", "#${options.tag}")
            options.folder != null -> SearchMatch("文件夹", folder)
            else -> null
        }
    }
    val regex =
        if (options.useRegex) {
            compiledRegex ?: runCatching {
                Regex(q, if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
            }.getOrNull() ?: return null
        } else {
            null
        }

    fun matchRange(text: String): IntRange? {
        regex?.find(text)?.let { return it.range }
        val start = text.indexOf(q, ignoreCase = !options.matchCase)
        return start.takeIf { it >= 0 }?.let { it until (it + q.length) }
    }

    val titleMatch = options.matchTitle.then { matchRange(note.title) }
    val contentMatch = options.matchContent.then { matchRange(note.content) }
    val matchedHistory by lazy {
        histories.firstOrNull {
            it.noteId == note.id &&
                (
                    options.matchTitle.then { matchRange(it.title) } != null ||
                        options.matchContent.then { matchRange(it.content) } != null
                )
        }
    }
    return when {
        titleMatch != null -> SearchMatch("标题", note.title)
        contentMatch != null ->
            SearchMatch(
                "正文",
                buildSearchSnippetAt(note.content, contentMatch.first, contentMatch.last - contentMatch.first + 1),
                if (options.useRegex) -1 else contentMatch.first,
            )
        matchedHistory != null -> {
            val history =
                matchedHistory!!
            SearchMatch("历史版本", buildSearchSnippet("${history.title}\n${history.content}", q))
        }
        else -> null
    }
}

private inline fun <T> Boolean.then(block: () -> T): T? = if (this) block() else null

private fun buildSearchSnippetAt(
    content: String,
    startOffset: Int,
    matchLength: Int,
): String {
    val start = (startOffset - 60).coerceAtLeast(0)
    val end = (startOffset + matchLength.coerceAtLeast(1) + 90).coerceAtMost(content.length)
    return buildString {
        if (start > 0) append("...")
        append(content.substring(start, end).replace('\r', ' ').replace('\n', ' ').trim())
        if (end < content.length) append("...")
    }
}

fun stripMarkdownForSnippet(content: String): String =
    content
        .replace(wikiLinkRegex) { match ->
            match.groupValues[2].substringAfter("|").substringBefore("#").trim()
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

fun normalizeObsidianTarget(value: String): String =
    value
        .replace("\\", "/")
        .removeSuffixIgnoreCase(".md")
        .trim('/')
        .trim()
        .lowercase(Locale.ROOT)

private fun normalizeObsidianName(value: String): String = normalizeObsidianTarget(value).substringAfterLast('/')

private fun String.removeSuffixIgnoreCase(suffix: String): String =
    if (endsWith(suffix, ignoreCase = true)) dropLast(suffix.length) else this

private fun String.indexOfUnescaped(char: Char): Int {
    var index = 0
    while (index < length) {
        if (this[index] == char) {
            var slashes = 0
            var before = index - 1
            while (before >= 0 && this[before] == '\\') {
                slashes++
                before--
            }
            if (slashes % 2 == 0) return index
        }
        index++
    }
    return -1
}
