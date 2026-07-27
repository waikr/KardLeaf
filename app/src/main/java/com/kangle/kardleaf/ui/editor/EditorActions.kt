package com.kangle.kardleaf.ui.editor

import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.utils.KardLeafContentLimits
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.ui.MarkdownHeading
import com.kangle.kardleaf.ui.extractMarkdownHeadings
import androidx.compose.ui.text.TextRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

internal fun noteSearchSnippetForLog(text: String, start: Int, end: Int): String {
    if (start < 0 || end <= start || start >= text.length) return ""
    return text.substring(start.coerceAtLeast(0), end.coerceAtMost(text.length))
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .take(32)
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
                values = listOf(formatNoteSidePanelTime(currentNote.lastModified)),
            ),
        )
    }
}

private fun formatNoteSidePanelTime(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)


internal data class MindMapPrepareResult(
    val headings: List<MarkdownHeading>,
    val unavailableTitle: String? = null,
    val unavailableMessage: String? = null,
)

internal fun blockedLargeMindMapResult(contentLength: Int): MindMapPrepareResult =
    MindMapPrepareResult(
        headings = emptyList(),
        unavailableTitle = "笔记过大",
        unavailableMessage = "当前笔记约 ${contentLength.coerceAtLeast(0)} 字，已停止生成思维导图，避免误触后卡死。",
    )

internal fun prepareMarkdownMindMap(content: String): MindMapPrepareResult {
    if (content.length > KardLeafContentLimits.MIND_MAP_MAX_CONTENT_CHARS) {
        return blockedLargeMindMapResult(content.length)
    }
    val headings = extractMarkdownHeadings(content)
    if (headings.size > KardLeafContentLimits.MIND_MAP_MAX_HEADING_COUNT) {
        return MindMapPrepareResult(
            headings = emptyList(),
            unavailableTitle = "节点过多",
            unavailableMessage = "当前笔记检测到 ${headings.size} 个标题节点，已停止生成思维导图，避免 WebView 渲染过重。",
        )
    }
    val nonStandardReason = validateStandardMindMapHeadings(headings)
    if (nonStandardReason != null) {
        return MindMapPrepareResult(
            headings = emptyList(),
            unavailableTitle = "非标准思维导图格式",
            unavailableMessage = nonStandardReason,
        )
    }
    return MindMapPrepareResult(headings = headings)
}

private fun validateStandardMindMapHeadings(headings: List<MarkdownHeading>): String? {
    if (headings.isEmpty()) {
        return "当前笔记没有检测到标准 Markdown 标题。思维导图需要使用 # 一级节点、## 二级节点、### 三级节点这类结构。"
    }
    val first = headings.first()
    if (first.level != 1) {
        return "第 ${first.lineIndex + 1} 行不是一级标题。标准思维导图需要从 # 一级节点开始。"
    }
    headings.zipWithNext().forEach { (previous, current) ->
        if (current.level > previous.level + 1) {
            return "第 ${current.lineIndex + 1} 行标题层级跳级。请不要从 H${previous.level} 直接跳到 H${current.level}。"
        }
    }
    return null
}


internal data class MindMapReparentResult(
    val content: String,
    val selection: TextRange,
    val movedTitle: String,
    val parentTitle: String,
)

internal data class MindMapAddChildResult(
    val content: String,
    val selection: TextRange,
    val parentTitle: String,
)

internal data class MindMapDeleteResult(
    val content: String,
    val selection: TextRange,
    val deletedTitle: String,
)

internal data class MindMapRenameResult(
    val content: String,
    val selection: TextRange,
    val renamedTitle: String,
)

internal data class MindMapAddSiblingResult(
    val content: String,
    val selection: TextRange,
    val anchorTitle: String,
)

internal data class MindMapMoveResult(
    val content: String,
    val selection: TextRange,
    val movedTitle: String,
)

internal fun addMarkdownHeadingChild(
    content: String,
    headings: List<MarkdownHeading>,
    parentIndex: Int,
    title: String,
): MindMapAddChildResult? {
    val parent = if (parentIndex >= 0) headings.getOrNull(parentIndex) ?: return null else null
    val childLevel = ((parent?.level ?: 0) + 1).coerceIn(1, 6)
    if (parent != null && parent.level >= 6) return null
    val newTitle = title
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()
    if (newTitle.isEmpty()) return null

    val parentSubtreeEnd = if (parent == null) headings.size else findMarkdownHeadingSubtreeEnd(headings, parentIndex)
    val insertAt = if (parent == null) {
        content.length
    } else {
        headings.getOrNull(parentSubtreeEnd)
            ?.let { findLineStart(content, it.startOffset) }
            ?: content.length
    }.coerceIn(0, content.length)

    val marker = "#".repeat(childLevel) + " "
    val prefix = when {
        insertAt == 0 -> ""
        content.getOrNull(insertAt - 1) == '\n' || content.getOrNull(insertAt - 1) == '\r' -> ""
        else -> "\n"
    }
    val suffix = when {
        insertAt >= content.length -> "\n"
        content.getOrNull(insertAt) == '\n' || content.getOrNull(insertAt) == '\r' -> ""
        else -> "\n"
    }
    val insertion = prefix + marker + newTitle + suffix
    val updatedContent = content.substring(0, insertAt) + insertion + content.substring(insertAt)
    val titleStart = insertAt + prefix.length + marker.length
    val titleEnd = titleStart + newTitle.length

    return MindMapAddChildResult(
        content = updatedContent,
        selection = TextRange(titleStart, titleEnd),
        parentTitle = parent?.text?.ifBlank { "未命名节点" } ?: "根节点",
    )
}

internal fun addMarkdownHeadingSiblingAfter(
    content: String,
    headings: List<MarkdownHeading>,
    anchorIndex: Int,
    title: String,
): MindMapAddSiblingResult? {
    val anchor = headings.getOrNull(anchorIndex) ?: return null
    val newTitle = title
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()
    if (newTitle.isEmpty()) return null

    val level = anchor.level.coerceIn(1, 6)
    val anchorSubtreeEnd = findMarkdownHeadingSubtreeEnd(headings, anchorIndex)
    val insertAt = (
        headings.getOrNull(anchorSubtreeEnd)
            ?.let { findLineStart(content, it.startOffset) }
            ?: content.length
        ).coerceIn(0, content.length)

    val marker = "#".repeat(level) + " "
    val prefix = when {
        insertAt == 0 -> ""
        content.getOrNull(insertAt - 1) == '\n' || content.getOrNull(insertAt - 1) == '\r' -> ""
        else -> "\n"
    }
    val suffix = when {
        insertAt >= content.length -> "\n"
        content.getOrNull(insertAt) == '\n' || content.getOrNull(insertAt) == '\r' -> ""
        else -> "\n"
    }
    val insertion = prefix + marker + newTitle + suffix
    val updatedContent = content.substring(0, insertAt) + insertion + content.substring(insertAt)
    val titleStart = insertAt + prefix.length + marker.length

    return MindMapAddSiblingResult(
        content = updatedContent,
        selection = TextRange(titleStart, titleStart + newTitle.length),
        anchorTitle = anchor.text.ifBlank { "未命名节点" },
    )
}

internal fun moveMarkdownHeadingSubtree(
    content: String,
    headings: List<MarkdownHeading>,
    nodeIndex: Int,
    moveUp: Boolean,
): MindMapMoveResult? {
    val moving = headings.getOrNull(nodeIndex) ?: return null
    val movingSubtreeEnd = findMarkdownHeadingSubtreeEnd(headings, nodeIndex)
    val movingStart = findLineStart(content, moving.startOffset)
    val movingEnd = headings.getOrNull(movingSubtreeEnd)
        ?.let { findLineStart(content, it.startOffset) }
        ?: content.length

    val blockStart: Int
    val blockMid: Int
    val blockEnd: Int
    val movingBlockIsFirst: Boolean
    if (moveUp) {
        var previousSiblingIndex = -1
        for (cursor in nodeIndex - 1 downTo 0) {
            val level = headings[cursor].level
            if (level == moving.level) {
                previousSiblingIndex = cursor
                break
            }
            if (level < moving.level) break
        }
        if (previousSiblingIndex < 0) return null
        blockStart = findLineStart(content, headings[previousSiblingIndex].startOffset)
        blockMid = movingStart
        blockEnd = movingEnd
        movingBlockIsFirst = false
    } else {
        val nextSibling = headings.getOrNull(movingSubtreeEnd) ?: return null
        if (nextSibling.level != moving.level) return null
        val nextSubtreeEnd = findMarkdownHeadingSubtreeEnd(headings, movingSubtreeEnd)
        blockStart = movingStart
        blockMid = movingEnd
        blockEnd = headings.getOrNull(nextSubtreeEnd)
            ?.let { findLineStart(content, it.startOffset) }
            ?: content.length
        movingBlockIsFirst = true
    }
    if (blockStart !in 0..blockMid || blockMid > blockEnd || blockEnd > content.length) return null

    val firstBlock = content.substring(blockStart, blockMid)
    val secondBlock = content.substring(blockMid, blockEnd)
    // 交换后原第二块排在前面；若它原本止于文件末尾且缺少换行，需补一个换行避免两块拼在同一行。
    val needsNewline = !secondBlock.endsWith("\n") && !secondBlock.endsWith("\r")
    val swapped = if (needsNewline) secondBlock + "\n" + firstBlock else secondBlock + firstBlock
    val updatedContent = content.substring(0, blockStart) + swapped + content.substring(blockEnd)

    val newMovingStart = if (movingBlockIsFirst) {
        blockStart + secondBlock.length + (if (needsNewline) 1 else 0) + (moving.startOffset - blockStart)
    } else {
        blockStart + (moving.startOffset - blockMid)
    }
    val selection = newMovingStart.coerceIn(0, updatedContent.length)
    return MindMapMoveResult(
        content = updatedContent,
        selection = TextRange(selection, selection),
        movedTitle = moving.text.ifBlank { "未命名节点" },
    )
}

internal fun deleteMarkdownHeadingSubtree(
    content: String,
    headings: List<MarkdownHeading>,
    nodeIndex: Int,
): MindMapDeleteResult? {
    val heading = headings.getOrNull(nodeIndex) ?: return null
    val subtreeEnd = findMarkdownHeadingSubtreeEnd(headings, nodeIndex)
    val blockStart = findLineStart(content, heading.startOffset)
    val blockEnd = headings.getOrNull(subtreeEnd)
        ?.let { findLineStart(content, it.startOffset) }
        ?: content.length
    if (blockStart !in 0..blockEnd || blockEnd > content.length) return null

    val updatedContent = content.removeRange(blockStart, blockEnd)
    val selection = blockStart.coerceAtMost(updatedContent.length)
    return MindMapDeleteResult(
        content = updatedContent,
        selection = TextRange(selection, selection),
        deletedTitle = heading.text.ifBlank { "未命名节点" },
    )
}

internal fun renameMarkdownHeading(
    content: String,
    headings: List<MarkdownHeading>,
    nodeIndex: Int,
    title: String,
): MindMapRenameResult? {
    val heading = headings.getOrNull(nodeIndex) ?: return null
    val renamedTitle = title
        .replace('\r', ' ')
        .replace('\n', ' ')
        .trim()
    if (renamedTitle.isEmpty() || renamedTitle == heading.text) return null

    val lineStart = findLineStart(content, heading.startOffset)
    val lineEnd = content.indexOfAny(charArrayOf('\n', '\r'), startIndex = heading.startOffset)
        .let { if (it >= 0) it else content.length }
    if (lineStart !in 0..lineEnd || lineEnd > content.length) return null

    val indentation = content.substring(lineStart, heading.startOffset)
    val replacement = indentation + "#".repeat(heading.level.coerceIn(1, 6)) + " " + renamedTitle
    val updatedContent = content.replaceRange(lineStart, lineEnd, replacement)
    val titleStart = lineStart + indentation.length + heading.level + 1
    return MindMapRenameResult(
        content = updatedContent,
        selection = TextRange(titleStart, titleStart + renamedTitle.length),
        renamedTitle = renamedTitle,
    )
}

internal fun reparentMarkdownHeading(
    content: String,
    headings: List<MarkdownHeading>,
    movingIndex: Int,
    parentIndex: Int,
): MindMapReparentResult? {
    val moving = headings.getOrNull(movingIndex) ?: return null
    val parent = if (parentIndex >= 0) headings.getOrNull(parentIndex) else null
    if (parentIndex == movingIndex) return null

    val movingSubtreeEnd = findMarkdownHeadingSubtreeEnd(headings, movingIndex)
    if (parentIndex in movingIndex until movingSubtreeEnd) return null

    val blockStart = findLineStart(content, moving.startOffset)
    val blockEnd = headings.getOrNull(movingSubtreeEnd)
        ?.let { findLineStart(content, it.startOffset) }
        ?: content.length
    if (blockStart !in 0..blockEnd || blockEnd > content.length) return null

    val targetSubtreeEnd = if (parent == null) {
        headings.size
    } else {
        findMarkdownHeadingSubtreeEnd(headings, parentIndex)
    }
    val targetEnd = if (parent == null) {
        content.length
    } else {
        headings.getOrNull(targetSubtreeEnd)
            ?.let { findLineStart(content, it.startOffset) }
            ?: content.length
    }
    if (targetEnd in (blockStart + 1) until blockEnd) return null

    val targetLevel = ((parent?.level ?: 0) + 1).coerceIn(1, 6)
    val levelDelta = targetLevel - moving.level
    val originalBlock = content.substring(blockStart, blockEnd)
    val updatedBlock = if (levelDelta == 0) {
        originalBlock
    } else {
        adjustMarkdownHeadingLevelsInBlock(
            block = originalBlock,
            contentBlockStart = blockStart,
            headings = headings.subList(movingIndex, movingSubtreeEnd),
            levelDelta = levelDelta,
        )
    }

    val withoutBlock = content.removeRange(blockStart, blockEnd)
    val blockLength = blockEnd - blockStart
    val insertAt = (if (targetEnd > blockStart) targetEnd - blockLength else targetEnd)
        .coerceIn(0, withoutBlock.length)
    if (insertAt == blockStart && updatedBlock == originalBlock) return null

    val updatedContent = buildString(content.length - originalBlock.length + updatedBlock.length) {
        append(withoutBlock.substring(0, insertAt))
        append(updatedBlock)
        append(withoutBlock.substring(insertAt))
    }
    val movedHeadingOffsetInBlock = moving.startOffset - blockStart
    val newHeadingStart = (insertAt + movedHeadingOffsetInBlock).coerceIn(0, updatedContent.length)
    return MindMapReparentResult(
        content = updatedContent,
        selection = TextRange(newHeadingStart, newHeadingStart),
        movedTitle = moving.text.ifBlank { "未命名节点" },
        parentTitle = parent?.text?.ifBlank { "未命名节点" } ?: "根节点",
    )
}

private fun findMarkdownHeadingSubtreeEnd(
    headings: List<MarkdownHeading>,
    index: Int,
): Int {
    val heading = headings.getOrNull(index) ?: return headings.size
    for (cursor in index + 1 until headings.size) {
        if (headings[cursor].level <= heading.level) return cursor
    }
    return headings.size
}

private fun findLineStart(
    content: String,
    offset: Int,
): Int {
    var cursor = offset.coerceIn(0, content.length)
    while (cursor > 0 && content[cursor - 1] != '\n' && content[cursor - 1] != '\r') {
        cursor--
    }
    return cursor
}

private fun adjustMarkdownHeadingLevelsInBlock(
    block: String,
    contentBlockStart: Int,
    headings: List<MarkdownHeading>,
    levelDelta: Int,
): String {
    val builder = StringBuilder(block)
    headings.sortedByDescending { it.startOffset }.forEach { heading ->
        val markerStart = (heading.startOffset - contentBlockStart).coerceIn(0, builder.length)
        var markerEnd = markerStart
        while (markerEnd < builder.length && builder[markerEnd] == '#') {
            markerEnd++
        }
        if (markerEnd > markerStart) {
            val targetLevel = (heading.level + levelDelta).coerceIn(1, 6)
            builder.replace(markerStart, markerEnd, "#".repeat(targetLevel))
        }
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
