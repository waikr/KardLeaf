package com.kangle.kardleaf.ui.editor.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.database.NoteLinkEntity
import com.kangle.kardleaf.data.database.NoteLinkResolutionStatus
import com.kangle.kardleaf.ui.MarkdownHeading
import com.kangle.kardleaf.ui.extractObsidianLinks

@Composable
internal fun NoteInfoDialog(
    title: String,
    content: String,
    allNotes: List<Note>,
    outgoingLinks: List<NoteLinkEntity> = emptyList(),
    backlinkLinks: List<NoteLinkEntity> = emptyList(),
    onDismiss: () -> Unit,
    onHeadingClick: (MarkdownHeading) -> Unit = {},
    onNoteClick: (String) -> Unit = {},
    onWikilinkClick: (String) -> Unit = {},
) {
    val headings = remember(content) { extractHistoryMarkdownHeadings(content) }
    val links = remember(content) { extractObsidianLinks(content) }
    val tags = remember(content) { extractHistoryObsidianTags(content) }
    val displayedOutgoing = remember(links, outgoingLinks) {
        if (outgoingLinks.isNotEmpty()) outgoingLinks else links.map { target ->
            NoteLinkEntity(
                sourceRecordId = "",
                sourcePath = "",
                targetRaw = target,
                targetNormalized = target.lowercase(),
                startOffset = -1,
                endOffset = -1,
                contextSnippet = "",
                resolutionStatus = NoteLinkResolutionStatus.UNRESOLVED,
            )
        }
    }
    val displayedBacklinks = backlinkLinks

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("笔记信息") },
        text = {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    NoteInfoSection("大纲") {
                        if (headings.isEmpty()) {
                            Text("暂无标题", style = MaterialTheme.typography.bodySmall)
                        } else {
                            headings.forEach { heading ->
                                Text(
                                    text = "${"  ".repeat((heading.level - 1).coerceAtLeast(0))}${heading.text}",
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onHeadingClick(heading) }
                                            .padding(vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                item {
                    NoteInfoSection("标签") {
                        if (tags.isEmpty()) {
                            Text("暂无标签", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    NoteInfoSection("出链") {
                        if (displayedOutgoing.isEmpty()) {
                            Text("暂无双链", style = MaterialTheme.typography.bodySmall)
                        } else {
                            displayedOutgoing.forEach { link ->
                                val statusLabel = when (link.resolutionStatus) {
                                    NoteLinkResolutionStatus.RESOLVED -> ""
                                    NoteLinkResolutionStatus.AMBIGUOUS -> " · 歧义"
                                    else -> " · 未解析"
                                }
                                Text(
                                    text = "[[${link.targetRaw}]]$statusLabel",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            link.targetPath?.let(onNoteClick) ?: onWikilinkClick(link.targetRaw)
                                        }
                                        .padding(vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
                item {
                    NoteInfoSection("反向链接") {
                        if (displayedBacklinks.isEmpty()) {
                            Text("暂无反向链接", style = MaterialTheme.typography.bodySmall)
                        } else {
                            displayedBacklinks.groupBy { it.sourcePath }.forEach { (sourcePath, sourceLinks) ->
                                val sourceNote = allNotes.firstOrNull { it.file.path == sourcePath }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onNoteClick(sourcePath) }
                                        .padding(vertical = 4.dp),
                                ) {
                                    Text(sourceNote?.title ?: sourcePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    sourceLinks.take(3).forEach { link ->
                                        if (link.contextSnippet.isNotBlank()) {
                                            Text(link.contextSnippet, style = MaterialTheme.typography.labelSmall, maxLines = 2)
                                        }
                                    }
                                    if (sourceLinks.size > 1) {
                                        Text("引用 ${sourceLinks.size} 次", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

@Composable
private fun NoteInfoSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

private val historyHeadingRegex = Regex("""^(#{1,6})\s+(.+?)\s*#*\s*$""")
private fun extractHistoryMarkdownHeadings(content: String): List<MarkdownHeading> {
    val headings = mutableListOf<MarkdownHeading>()
    var offset = 0
    var lineIndex = 0
    while (offset <= content.length) {
        val newlineIndex = content.indexOfAny(charArrayOf('\n', '\r'), startIndex = offset)
        val lineEnd = if (newlineIndex >= 0) newlineIndex else content.length
        val line = content.substring(offset, lineEnd)
        val leadingWhitespace = line.length - line.trimStart().length
        val match = historyHeadingRegex.find(line.trim())
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

private fun extractHistoryObsidianTags(content: String): List<String> =
    Regex("""(?<![\w/])#([A-Za-z0-9_\-/\u4e00-\u9fa5]+)""").findAll(content)
        .map { it.groupValues[1].trim('/') }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
