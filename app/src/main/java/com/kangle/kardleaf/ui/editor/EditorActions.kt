package com.kangle.kardleaf.ui.editor

import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.utils.KardLeafContentLimits
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import androidx.compose.ui.text.TextRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

internal fun noteSearchSnippetForLog(text: String, start: Int, end: Int): String {
    if (start < 0 || end <= start || start >= text.length) return ""
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    return "range=$safeStart..$safeEnd len=${safeEnd - safeStart}"
}

internal data class NoteSearchMatchRange(
    val start: Int,
    val end: Int,
)

internal data class NoteSearchMatchesResult(
    val matches: List<NoteSearchMatchRange> = emptyList(),
    val errorMessage: String? = null,
)

internal data class NoteSearchReplacementResult(
    val text: String? = null,
    val count: Int = 0,
    val errorMessage: String? = null,
)

internal data class SearchMatchSummary(
    val count: Int,
    val currentStart: Int,
    val currentEnd: Int,
    val currentOrdinal: Int,
    val errorMessage: String? = null,
)

internal fun summarizeNoteSearchMatches(
    text: String,
    query: String,
    preferredStart: Int,
    useRegex: Boolean,
    matchCase: Boolean,
): SearchMatchSummary {
    val result = buildNoteSearchMatches(text, query, useRegex, matchCase)
    result.errorMessage?.let { return SearchMatchSummary(0, -1, -1, 0, it) }
    val matches = result.matches
    if (matches.isEmpty()) return SearchMatchSummary(0, -1, -1, 0)
    val preferredIndex = matches.indexOfFirst { it.start == preferredStart }
    val currentIndex = if (preferredIndex >= 0) preferredIndex else 0
    val current = matches[currentIndex]
    return SearchMatchSummary(
        count = matches.size,
        currentStart = current.start,
        currentEnd = current.end,
        currentOrdinal = currentIndex + 1,
    )
}

internal fun buildNoteSearchMatches(
    text: String,
    query: String,
    useRegex: Boolean,
    matchCase: Boolean,
): NoteSearchMatchesResult {
    if (text.isEmpty() || query.isBlank()) return NoteSearchMatchesResult()
    return if (useRegex) {
        val pattern = createNoteSearchPattern(query, matchCase)
            ?: return NoteSearchMatchesResult(errorMessage = "正则表达式无效")
        val matcher = pattern.matcher(text)
        val matches = buildList {
            while (matcher.find()) {
                val start = matcher.start().coerceIn(0, text.length)
                val end = matcher.end().coerceIn(0, text.length)
                if (end > start) add(NoteSearchMatchRange(start, end))
            }
        }
        NoteSearchMatchesResult(matches = matches)
    } else {
        val matches = buildList {
            var searchFrom = 0
            while (searchFrom <= text.length - query.length) {
                val index = text.indexOf(query, startIndex = searchFrom, ignoreCase = !matchCase)
                if (index < 0) break
                val end = index + query.length
                add(NoteSearchMatchRange(index, end))
                searchFrom = end.coerceAtLeast(index + 1)
            }
        }
        NoteSearchMatchesResult(matches = matches)
    }
}

internal fun buildCurrentReplacement(
    text: String,
    range: NoteSearchMatchRange,
    query: String,
    replacement: String,
    useRegex: Boolean,
    matchCase: Boolean,
): NoteSearchReplacementResult {
    if (!useRegex) return NoteSearchReplacementResult(text = replacement, count = 1)
    val pattern = createNoteSearchPattern(query, matchCase)
        ?: return NoteSearchReplacementResult(errorMessage = "正则表达式无效")
    val matcher = pattern.matcher(text)
    while (matcher.find()) {
        val start = matcher.start().coerceIn(0, text.length)
        val end = matcher.end().coerceIn(0, text.length)
        if (start == range.start && end == range.end) {
            val expanded = expandRegexReplacement(replacement, matcher)
                ?: return NoteSearchReplacementResult(errorMessage = "替换内容包含无效的正则引用")
            return NoteSearchReplacementResult(text = expanded, count = 1)
        }
    }
    return NoteSearchReplacementResult(errorMessage = "没有找到要替换的文本")
}

internal fun replaceAllNoteSearchMatches(
    text: String,
    query: String,
    replacement: String,
    useRegex: Boolean,
    matchCase: Boolean,
): NoteSearchReplacementResult {
    if (text.isEmpty() || query.isBlank()) return NoteSearchReplacementResult(text = text)
    if (!useRegex) {
        val builder = StringBuilder(text.length)
        var count = 0
        var searchFrom = 0
        while (searchFrom <= text.length - query.length) {
            val index = text.indexOf(query, startIndex = searchFrom, ignoreCase = !matchCase)
            if (index < 0) break
            builder.append(text, searchFrom, index)
            builder.append(replacement)
            searchFrom = index + query.length
            count++
        }
        if (count == 0) return NoteSearchReplacementResult(text = text)
        builder.append(text, searchFrom, text.length)
        return NoteSearchReplacementResult(text = builder.toString(), count = count)
    }

    val pattern = createNoteSearchPattern(query, matchCase)
        ?: return NoteSearchReplacementResult(errorMessage = "正则表达式无效")
    val matcher = pattern.matcher(text)
    val builder = StringBuilder(text.length)
    var count = 0
    var lastEnd = 0
    while (matcher.find()) {
        val start = matcher.start().coerceIn(0, text.length)
        val end = matcher.end().coerceIn(0, text.length)
        if (end <= start) continue
        builder.append(text, lastEnd, start)
        val expanded = expandRegexReplacement(replacement, matcher)
            ?: return NoteSearchReplacementResult(errorMessage = "替换内容包含无效的正则引用")
        builder.append(expanded)
        lastEnd = end
        count++
    }
    if (count == 0) return NoteSearchReplacementResult(text = text)
    builder.append(text, lastEnd, text.length)
    return NoteSearchReplacementResult(text = builder.toString(), count = count)
}

private fun createNoteSearchPattern(
    query: String,
    matchCase: Boolean,
): Pattern? =
    try {
        val caseFlags = if (matchCase) {
            0
        } else {
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        }
        Pattern.compile(query, Pattern.MULTILINE or caseFlags)
    } catch (_: PatternSyntaxException) {
        null
    }

private fun expandRegexReplacement(
    replacement: String,
    matcher: java.util.regex.Matcher,
): String? {
    val builder = StringBuilder(replacement.length)
    var index = 0
    while (index < replacement.length) {
        val char = replacement[index]
        when {
            char == '\\' && index + 1 < replacement.length -> {
                builder.append(replacement[index + 1])
                index += 2
            }
            char == '$' -> {
                var cursor = index + 1
                if (cursor >= replacement.length || !replacement[cursor].isDigit()) return null
                while (cursor < replacement.length && replacement[cursor].isDigit()) cursor++
                val groupIndex = replacement.substring(index + 1, cursor).toIntOrNull() ?: return null
                val groupText = try {
                    matcher.group(groupIndex).orEmpty()
                } catch (_: RuntimeException) {
                    return null
                }
                builder.append(groupText)
                index = cursor
            }
            else -> {
                builder.append(char)
                index++
            }
        }
    }
    return builder.toString()
}

internal fun buildNoteSidePanelProperties(
    frontMatterProperties: List<NoteFormatUtils.FrontMatterProperty>,
    note: Note?,
    title: String,
): List<NoteFormatUtils.FrontMatterProperty> {
    if (frontMatterProperties.isNotEmpty()) return frontMatterProperties
    val currentNote = note ?: return emptyList()
    return buildList {
        title.trim().takeIf { it.isNotBlank() }?.let { value ->
            add(NoteFormatUtils.FrontMatterProperty(key = "title", values = listOf(value)))
        }
        currentNote.file.path.trim().takeIf { it.isNotBlank() }?.let { value ->
            add(NoteFormatUtils.FrontMatterProperty(key = "path", values = listOf(value)))
        }
        add(
            NoteFormatUtils.FrontMatterProperty(
                key = "created",
                values = listOf(formatNoteSidePanelTime(currentNote.createdAt)),
            ),
        )
        add(
            NoteFormatUtils.FrontMatterProperty(
                key = "updated",
                values = listOf(formatNoteSidePanelTime(currentNote.updatedAt)),
            ),
        )
    }
}

private fun formatNoteSidePanelTime(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)


internal enum class MindMapSourceKind {
    H1,
    H2,
    H3,
    LIST,
}

internal data class MindMapNode(
    val index: Int,
    val depth: Int,
    val text: String,
    val sourceOffset: Int,
    val lineIndex: Int,
    val parentIndex: Int?,
    val sourceKind: MindMapSourceKind,
)

internal data class MindMapDocument(
    val content: String,
    val nodes: List<MindMapNode>,
) {
    val root: MindMapNode
        get() = nodes.first()
}

internal data class MindMapPrepareResult(
    val document: MindMapDocument? = null,
    val unavailableTitle: String? = null,
    val unavailableMessage: String? = null,
)

internal data class MindMapEditResult(
    val content: String,
    val selection: TextRange,
    val nodeTitle: String,
    val contextTitle: String? = null,
    val nodeIndex: Int? = null,
)

private data class MindMapParseResult(
    val document: MindMapDocument? = null,
    val error: String? = null,
)

private val mindMapHeadingRegex = Regex("""^(#{1,6})\s+(.+?)(?:\s+#+)?\s*$""")
private val mindMapListRegex = Regex("""^([ \t]*)([-*+])\s+(.+?)\s*$""")

internal fun blockedLargeMindMapResult(contentLength: Int): MindMapPrepareResult =
    MindMapPrepareResult(
        unavailableTitle = "笔记过大",
        unavailableMessage = "当前笔记约 ${contentLength.coerceAtLeast(0)} 字，已停止生成思维导图，避免误触后卡死。",
    )

internal fun prepareMarkdownMindMap(content: String): MindMapPrepareResult {
    if (content.length > KardLeafContentLimits.MIND_MAP_MAX_CONTENT_CHARS) {
        return blockedLargeMindMapResult(content.length)
    }
    val parsed = parseMindMapDocument(content)
    val document = parsed.document
    if (document != null && document.nodes.size > KardLeafContentLimits.MIND_MAP_MAX_NODE_COUNT) {
        return MindMapPrepareResult(
            unavailableTitle = "节点过多",
            unavailableMessage = "当前笔记检测到 ${document.nodes.size} 个思维导图节点，已停止生成，避免 WebView 渲染过重。",
        )
    }
    return if (document != null) {
        MindMapPrepareResult(document = document)
    } else {
        MindMapPrepareResult(
            unavailableTitle = "非标准思维导图格式",
            unavailableMessage = parsed.error,
        )
    }
}

private fun parseMindMapDocument(content: String): MindMapParseResult {
    val nodes = mutableListOf<MindMapNode>()
    val ancestors = mutableListOf<Int>()
    var offset = 0
    var lineIndex = 0
    var fenceCharacter: Char? = null
    var fenceLength = 0

    while (offset <= content.length) {
        val newlineIndex = content.indexOfAny(charArrayOf('\n', '\r'), startIndex = offset)
        val lineEnd = if (newlineIndex >= 0) newlineIndex else content.length
        val line = content.substring(offset, lineEnd)
        val trimmedStart = line.trimStart()
        val fenceRun = trimmedStart.takeWhile { it == '`' || it == '~' }
        if (fenceCharacter != null) {
            if (fenceRun.length >= fenceLength && fenceRun.all { it == fenceCharacter }) {
                fenceCharacter = null
                fenceLength = 0
            }
        } else if (fenceRun.length >= 3 && fenceRun.all { it == fenceRun.first() }) {
            fenceCharacter = fenceRun.first()
            fenceLength = fenceRun.length
        } else {
            val headingMatch = mindMapHeadingRegex.matchEntire(trimmedStart)
            val listMatch = mindMapListRegex.matchEntire(line)
            val parsedNode = when {
                headingMatch != null -> {
                    val indentation = line.length - trimmedStart.length
                    if (indentation != 0) {
                        return MindMapParseResult(error = "第 ${lineIndex + 1} 行标题带有缩进。H1-H3 必须从行首开始。")
                    }
                    val level = headingMatch.groupValues[1].length
                    if (level > 3) {
                        return MindMapParseResult(error = "第 ${lineIndex + 1} 行使用了 H$level。思维导图仅允许 H1-H3，深层节点请使用缩进列表。")
                    }
                    Triple(level - 1, headingMatch.groupValues[2].trim(), MindMapSourceKind.entries[level - 1])
                }
                listMatch != null -> {
                    val indentation = listMatch.groupValues[1]
                    if ('\t' in indentation) {
                        return MindMapParseResult(error = "第 ${lineIndex + 1} 行列表使用了 Tab。思维导图列表必须使用空格缩进。")
                    }
                    if (listMatch.groupValues[2] != "-") {
                        return MindMapParseResult(error = "第 ${lineIndex + 1} 行列表标记不是 -。思维导图深层节点统一使用 -。")
                    }
                    if (indentation.length % 2 != 0) {
                        return MindMapParseResult(error = "第 ${lineIndex + 1} 行列表缩进不是 2 的倍数。")
                    }
                    Triple(3 + indentation.length / 2, listMatch.groupValues[3].trim(), MindMapSourceKind.LIST)
                }
                else -> null
            }

            if (parsedNode != null) {
                val (depth, text, sourceKind) = parsedNode
                if (nodes.isEmpty() && depth != 0) {
                    return MindMapParseResult(error = "第 ${lineIndex + 1} 行是第一条结构节点，但不是 H1 根节点。")
                }
                if (depth == 0 && nodes.isNotEmpty()) {
                    return MindMapParseResult(error = "第 ${lineIndex + 1} 行出现了第二个 H1。思维导图只能有一个根节点。")
                }
                if (nodes.isNotEmpty() && depth > nodes.last().depth + 1) {
                    return MindMapParseResult(error = "第 ${lineIndex + 1} 行层级从 ${nodes.last().depth} 跳到了 $depth。节点层级不能跳级。")
                }
                val parentIndex = if (depth == 0) {
                    null
                } else {
                    ancestors.getOrNull(depth - 1)?.takeIf { it >= 0 }
                        ?: return MindMapParseResult(error = "第 ${lineIndex + 1} 行没有合法的上级节点。")
                }
                val nodeIndex = nodes.size
                nodes += MindMapNode(
                    index = nodeIndex,
                    depth = depth,
                    text = text,
                    sourceOffset = offset,
                    lineIndex = lineIndex,
                    parentIndex = parentIndex,
                    sourceKind = sourceKind,
                )
                while (ancestors.size <= depth) ancestors += -1
                while (ancestors.size > depth + 1) ancestors.removeAt(ancestors.lastIndex)
                ancestors[depth] = nodeIndex
            }
        }

        if (newlineIndex < 0) break
        offset = if (content[newlineIndex] == '\r' && content.getOrNull(newlineIndex + 1) == '\n') {
            newlineIndex + 2
        } else {
            newlineIndex + 1
        }
        lineIndex++
    }

    if (nodes.isEmpty()) {
        return MindMapParseResult(error = "当前笔记没有检测到思维导图结构。第一条结构节点必须是 # 根节点。")
    }
    return MindMapParseResult(document = MindMapDocument(content, nodes))
}

private fun sanitizeMindMapTitle(title: String): String =
    title.replace('\r', ' ').replace('\n', ' ').trim()

private fun mindMapNodePrefix(depth: Int): String {
    require(depth >= 0)
    return if (depth <= 2) "#".repeat(depth + 1) + " " else "  ".repeat(depth - 3) + "- "
}

internal fun formatMindMapNodeLine(depth: Int, title: String): String = mindMapNodePrefix(depth) + title

internal fun createMindMapRoot(title: String = "中心主题"): MindMapEditResult? {
    val rootTitle = sanitizeMindMapTitle(title)
    if (rootTitle.isEmpty()) return null
    val content = formatMindMapNodeLine(0, rootTitle)
    return MindMapEditResult(content, TextRange(2, content.length), rootTitle)
}

private fun mindMapEol(content: String): String = when {
    "\r\n" in content -> "\r\n"
    '\r' in content -> "\r"
    else -> "\n"
}

private fun insertMindMapNode(
    document: MindMapDocument,
    insertAt: Int,
    depth: Int,
    title: String,
    contextTitle: String,
): MindMapEditResult {
    val content = document.content
    val eol = mindMapEol(content)
    val prefix = if (insertAt > 0 && content[insertAt - 1] != '\n' && content[insertAt - 1] != '\r') eol else ""
    val suffix = if (insertAt >= content.length || (content[insertAt] != '\n' && content[insertAt] != '\r')) eol else ""
    val line = formatMindMapNodeLine(depth, title)
    val insertion = prefix + line + suffix
    val updatedContent = content.substring(0, insertAt) + insertion + content.substring(insertAt)
    val titleStart = insertAt + prefix.length + mindMapNodePrefix(depth).length
    return MindMapEditResult(
        content = updatedContent,
        selection = TextRange(titleStart, titleStart + title.length),
        nodeTitle = title,
        contextTitle = contextTitle,
        nodeIndex = document.nodes.count { it.sourceOffset < insertAt },
    )
}

internal fun addMindMapChild(
    document: MindMapDocument,
    parentIndex: Int,
    title: String,
): MindMapEditResult? {
    val parent = document.nodes.getOrNull(parentIndex) ?: return null
    val newTitle = sanitizeMindMapTitle(title)
    if (newTitle.isEmpty()) return null
    val subtreeEnd = findMindMapSubtreeEnd(document.nodes, parentIndex)
    val insertAt = document.nodes.getOrNull(subtreeEnd)?.sourceOffset ?: document.content.length
    return insertMindMapNode(document, insertAt, parent.depth + 1, newTitle, parent.text)
}

internal fun addMindMapSibling(
    document: MindMapDocument,
    anchorIndex: Int,
    title: String,
): MindMapEditResult? {
    val anchor = document.nodes.getOrNull(anchorIndex)?.takeIf { it.depth > 0 } ?: return null
    val newTitle = sanitizeMindMapTitle(title)
    if (newTitle.isEmpty()) return null
    val subtreeEnd = findMindMapSubtreeEnd(document.nodes, anchorIndex)
    val insertAt = document.nodes.getOrNull(subtreeEnd)?.sourceOffset ?: document.content.length
    return insertMindMapNode(document, insertAt, anchor.depth, newTitle, anchor.text)
}

private fun applyPendingMindMapRename(
    document: MindMapDocument,
    renameIndex: Int,
    renameTitle: String,
): MindMapDocument {
    if (renameIndex < 0) return document
    val renameResult = renameMindMapNode(document, renameIndex, renameTitle) ?: return document
    return prepareMarkdownMindMap(renameResult.content).document ?: document
}

internal fun addMindMapChildWithPendingRename(
    document: MindMapDocument,
    parentIndex: Int,
    title: String,
    renameIndex: Int,
    renameTitle: String,
): MindMapEditResult? = addMindMapChild(
    applyPendingMindMapRename(document, renameIndex, renameTitle),
    parentIndex,
    title,
)

internal fun addMindMapSiblingWithPendingRename(
    document: MindMapDocument,
    anchorIndex: Int,
    title: String,
    renameIndex: Int,
    renameTitle: String,
): MindMapEditResult? = addMindMapSibling(
    applyPendingMindMapRename(document, renameIndex, renameTitle),
    anchorIndex,
    title,
)

internal fun moveMindMapSubtree(
    document: MindMapDocument,
    nodeIndex: Int,
    moveUp: Boolean,
): MindMapEditResult? {
    val nodes = document.nodes
    val moving = nodes.getOrNull(nodeIndex)?.takeIf { it.depth > 0 } ?: return null
    val siblings = nodes.filter { it.parentIndex == moving.parentIndex }
    val siblingPosition = siblings.indexOfFirst { it.index == nodeIndex }
    val other = siblings.getOrNull(siblingPosition + if (moveUp) -1 else 1) ?: return null
    val movingEnd = nodes.getOrNull(findMindMapSubtreeEnd(nodes, nodeIndex))?.sourceOffset ?: document.content.length
    val otherEnd = nodes.getOrNull(findMindMapSubtreeEnd(nodes, other.index))?.sourceOffset ?: document.content.length
    val blockStart: Int
    val blockMid: Int
    val blockEnd: Int
    val movingBlockIsFirst: Boolean
    if (moveUp) {
        blockStart = other.sourceOffset
        blockMid = moving.sourceOffset
        blockEnd = movingEnd
        movingBlockIsFirst = false
    } else {
        blockStart = moving.sourceOffset
        blockMid = movingEnd
        blockEnd = otherEnd
        movingBlockIsFirst = true
    }
    val firstBlock = document.content.substring(blockStart, blockMid)
    val secondBlock = document.content.substring(blockMid, blockEnd)
    val eol = mindMapEol(document.content)
    val needsNewline = !secondBlock.endsWith("\n") && !secondBlock.endsWith("\r")
    val swapped = if (needsNewline) secondBlock + eol + firstBlock else secondBlock + firstBlock
    val updatedContent = document.content.substring(0, blockStart) + swapped + document.content.substring(blockEnd)
    val newMovingStart = if (movingBlockIsFirst) {
        blockStart + secondBlock.length + (if (needsNewline) eol.length else 0)
    } else {
        blockStart
    }
    return MindMapEditResult(updatedContent, TextRange(newMovingStart), moving.text)
}

internal fun deleteMindMapSubtree(
    document: MindMapDocument,
    nodeIndex: Int,
): MindMapEditResult? {
    val node = document.nodes.getOrNull(nodeIndex)?.takeIf { it.depth > 0 } ?: return null
    val subtreeEnd = findMindMapSubtreeEnd(document.nodes, nodeIndex)
    val blockEnd = document.nodes.getOrNull(subtreeEnd)?.sourceOffset ?: document.content.length
    val updatedContent = document.content.removeRange(node.sourceOffset, blockEnd)
    val selection = node.sourceOffset.coerceAtMost(updatedContent.length)
    return MindMapEditResult(updatedContent, TextRange(selection), node.text)
}

internal fun renameMindMapNode(
    document: MindMapDocument,
    nodeIndex: Int,
    title: String,
): MindMapEditResult? {
    val node = document.nodes.getOrNull(nodeIndex) ?: return null
    val renamedTitle = sanitizeMindMapTitle(title)
    if (renamedTitle.isEmpty() || renamedTitle == node.text) return null
    val lineEnd = document.content.indexOfAny(charArrayOf('\n', '\r'), startIndex = node.sourceOffset)
        .let { if (it >= 0) it else document.content.length }
    val replacement = formatMindMapNodeLine(node.depth, renamedTitle)
    val updatedContent = document.content.replaceRange(node.sourceOffset, lineEnd, replacement)
    val titleStart = node.sourceOffset + mindMapNodePrefix(node.depth).length
    return MindMapEditResult(
        content = updatedContent,
        selection = TextRange(titleStart, titleStart + renamedTitle.length),
        nodeTitle = renamedTitle,
    )
}

internal fun moveMindMapSubtreeToPosition(
    document: MindMapDocument,
    movingIndex: Int,
    targetParentIndex: Int,
    targetChildIndex: Int,
): MindMapEditResult? {
    val nodes = document.nodes
    val moving = nodes.getOrNull(movingIndex)?.takeIf { it.depth > 0 } ?: return null
    val targetParent = nodes.getOrNull(targetParentIndex) ?: return null
    val movingSubtreeEnd = findMindMapSubtreeEnd(nodes, movingIndex)
    if (targetParentIndex in movingIndex until movingSubtreeEnd) return null
    val oldSiblings = nodes.filter { it.parentIndex == moving.parentIndex }
    val oldChildIndex = oldSiblings.indexOfFirst { it.index == movingIndex }
    val targetChildren = nodes.filter {
        it.parentIndex == targetParentIndex && it.index != movingIndex
    }
    if (targetChildIndex !in 0..targetChildren.size) return null
    if (moving.parentIndex == targetParentIndex && oldChildIndex == targetChildIndex) return null
    val targetOffset = targetChildren.getOrNull(targetChildIndex)?.sourceOffset
        ?: nodes.getOrNull(findMindMapSubtreeEnd(nodes, targetParentIndex))?.sourceOffset
        ?: document.content.length
    return moveMindMapSubtreeBlock(document, movingIndex, targetParent.index, targetOffset)
}

private fun moveMindMapSubtreeBlock(
    document: MindMapDocument,
    movingIndex: Int,
    parentIndex: Int,
    targetOffset: Int,
): MindMapEditResult? {
    val nodes = document.nodes
    val moving = nodes.getOrNull(movingIndex)?.takeIf { it.depth > 0 } ?: return null
    val parent = nodes.getOrNull(parentIndex) ?: return null
    val movingSubtreeEnd = findMindMapSubtreeEnd(nodes, movingIndex)
    if (parentIndex in movingIndex until movingSubtreeEnd) return null
    val blockStart = moving.sourceOffset
    val blockEnd = nodes.getOrNull(movingSubtreeEnd)?.sourceOffset ?: document.content.length
    if (moving.parentIndex == parentIndex && (targetOffset == blockStart || targetOffset == blockEnd)) return null
    val originalBlock = document.content.substring(blockStart, blockEnd)
    val depthDelta = parent.depth + 1 - moving.depth
    val adjustedBlock = adjustMindMapDepthsInBlock(
        block = originalBlock,
        contentBlockStart = blockStart,
        nodes = nodes.subList(movingIndex, movingSubtreeEnd),
        depthDelta = depthDelta,
    )
    val withoutBlock = document.content.removeRange(blockStart, blockEnd)
    val insertAt = (if (targetOffset > blockStart) targetOffset - originalBlock.length else targetOffset)
        .coerceIn(0, withoutBlock.length)
    val eol = mindMapEol(document.content)
    val prefix = if (insertAt > 0 && withoutBlock[insertAt - 1] != '\n' && withoutBlock[insertAt - 1] != '\r') eol else ""
    val suffix = if (insertAt < withoutBlock.length && !adjustedBlock.endsWith("\n") && !adjustedBlock.endsWith("\r")) eol else ""
    val insertedBlock = prefix + adjustedBlock + suffix
    val updatedContent = withoutBlock.substring(0, insertAt) + insertedBlock + withoutBlock.substring(insertAt)
    val newNodeStart = insertAt + prefix.length
    return MindMapEditResult(
        content = updatedContent,
        selection = TextRange(newNodeStart),
        nodeTitle = moving.text,
        contextTitle = parent.text,
    )
}

private fun findMindMapSubtreeEnd(nodes: List<MindMapNode>, index: Int): Int {
    val node = nodes.getOrNull(index) ?: return nodes.size
    for (cursor in index + 1 until nodes.size) {
        if (nodes[cursor].depth <= node.depth) return cursor
    }
    return nodes.size
}

private fun adjustMindMapDepthsInBlock(
    block: String,
    contentBlockStart: Int,
    nodes: List<MindMapNode>,
    depthDelta: Int,
): String {
    if (depthDelta == 0) return block
    val builder = StringBuilder(block)
    nodes.asReversed().forEach { node ->
        val lineStart = node.sourceOffset - contentBlockStart
        val lineEnd = builder.indexOfAny(charArrayOf('\n', '\r'), startIndex = lineStart)
            .let { if (it >= 0) it else builder.length }
        builder.replace(lineStart, lineEnd, formatMindMapNodeLine(node.depth + depthDelta, node.text))
    }
    return builder.toString()
}

internal fun formatEditorFileInfoText(
    date: Date,
    charCount: Int,
    folder: String,
): String {
    val folderText = folder
        .replace("\\", "/")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: "未分类"
    val timeText = SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(date)
    return "$timeText | ${charCount.coerceAtLeast(0)} 字 | $folderText"
}
