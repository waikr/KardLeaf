package com.kangle.kardleaf.ui.editor.history

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.kangle.kardleaf.data.model.NoteHistory
import java.text.SimpleDateFormat

private const val HISTORY_DIALOG_LIGHTWEIGHT_CHAR_LIMIT = 80_000
private const val HISTORY_DIALOG_PREVIEW_CHAR_LIMIT = 200
private const val HISTORY_DIALOG_DIFF_LINE_LIMIT = 3_000

internal fun buildHistoryVersionItems(
    histories: List<NoteHistory>,
    currentContent: String,
    dateFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
): List<HistoryVersionItem> {
    val currentContentIsPreview = currentContent.length > HISTORY_DIALOG_LIGHTWEIGHT_CHAR_LIMIT
    val current = HistoryVersionItem(
        key = HistoryVersionItem.CURRENT_KEY,
        title = "当前版本",
        meta = "正在使用 · 约 ${currentContent.length} 字",
        sourceMeta = "正在使用 · 约 ${currentContent.length} 字",
        badge = "当前",
        content = if (currentContentIsPreview) historyPreviewText(currentContent, currentContent.length) else currentContent,
        contentLength = currentContent.length,
        contentIsPreview = currentContentIsPreview,
        current = true,
        history = null,
    )
    val historyItems = histories.mapIndexed { index, history ->
        val versionNumber = histories.size - index
        HistoryVersionItem(
            key = "history-${history.id}",
            title = "版本 $versionNumber",
            meta = "${dateFormat.format(history.savedAt)} · 历史保存 · 约 ${history.contentLength} 字",
            sourceMeta = "${timeFormat.format(history.savedAt)} 保存 · 约 ${history.contentLength} 字",
            badge = if (history.contentIsPreview) "预览" else "可对比",
            content = if (history.contentIsPreview) historyPreviewText(history.content, history.contentLength) else history.content,
            contentLength = history.contentLength,
            contentIsPreview = history.contentIsPreview,
            current = false,
            history = history,
        )
    }
    return listOf(current) + historyItems
}

private fun historyPreviewText(
    content: String,
    originalLength: Int,
): String {
    val preview = content.take(HISTORY_DIALOG_PREVIEW_CHAR_LIMIT)
    return if (originalLength > preview.length) {
        "$preview\n\n……仅显示前 ${HISTORY_DIALOG_PREVIEW_CHAR_LIMIT} 字预览，恢复历史版本时仍会使用完整正文"
    } else {
        preview
    }
}

internal fun canBuildHistoryDiff(
    oldContent: String,
    newContent: String,
): Boolean {
    if (oldContent.length > HISTORY_DIALOG_LIGHTWEIGHT_CHAR_LIMIT ||
        newContent.length > HISTORY_DIALOG_LIGHTWEIGHT_CHAR_LIMIT
    ) {
        return false
    }
    return oldContent.lineSequence().take(HISTORY_DIALOG_DIFF_LINE_LIMIT + 1).count() <= HISTORY_DIALOG_DIFF_LINE_LIMIT &&
        newContent.lineSequence().take(HISTORY_DIALOG_DIFF_LINE_LIMIT + 1).count() <= HISTORY_DIALOG_DIFF_LINE_LIMIT
}

internal fun buildHistoryDiffModel(
    oldContent: String,
    newContent: String,
): HistoryDiffModel {
    val ops = buildLineDiffOps(oldContent.lines(), newContent.lines())
    val displayRows = compactLineDiffOps(ops)
    return HistoryDiffModel(
        groups = buildHistoryDiffGroups(displayRows),
        displayRows = displayRows,
        addCount = displayRows.count { it.type == HistoryDiffType.ADD },
        removeCount = displayRows.count { it.type == HistoryDiffType.REMOVE },
        changeCount = displayRows.count { it.type == HistoryDiffType.CHANGE },
    )
}

private fun compactLineDiffOps(ops: List<LineDiffOp>): List<HistoryDiffDisplayRow> {
    val rows = mutableListOf<HistoryDiffDisplayRow>()
    var index = 0
    while (index < ops.size) {
        val op = ops[index]
        if (op.type == LineDiffType.SAME) {
            rows += HistoryDiffDisplayRow(
                type = HistoryDiffType.SAME,
                oldText = op.line,
                newText = op.line,
                oldLineNumber = op.oldLineNumber,
                newLineNumber = op.newLineNumber,
            )
            index++
        } else {
            val chunk = mutableListOf<LineDiffOp>()
            while (index < ops.size && ops[index].type != LineDiffType.SAME) {
                chunk += ops[index]
                index++
            }
            val deleted = chunk.filter { it.type == LineDiffType.DELETED }
            val added = chunk.filter { it.type == LineDiffType.ADDED }
            val pairCount = minOf(deleted.size, added.size)
            repeat(pairCount) { pairIndex ->
                val old = deleted[pairIndex]
                val new = added[pairIndex]
                rows += HistoryDiffDisplayRow(
                    type = HistoryDiffType.CHANGE,
                    oldText = old.line,
                    newText = new.line,
                    oldLineNumber = old.oldLineNumber,
                    newLineNumber = new.newLineNumber,
                )
            }
            deleted.drop(pairCount).forEach { deletedOp ->
                rows += HistoryDiffDisplayRow(
                    type = HistoryDiffType.REMOVE,
                    oldText = deletedOp.line,
                    newText = null,
                    oldLineNumber = deletedOp.oldLineNumber,
                    newLineNumber = null,
                )
            }
            added.drop(pairCount).forEach { addedOp ->
                rows += HistoryDiffDisplayRow(
                    type = HistoryDiffType.ADD,
                    oldText = null,
                    newText = addedOp.line,
                    oldLineNumber = null,
                    newLineNumber = addedOp.newLineNumber,
                )
            }
        }
    }
    return rows
}

private fun buildHistoryDiffGroups(rows: List<HistoryDiffDisplayRow>): List<HistoryDiffGroup> {
    val groups = mutableListOf<HistoryDiffGroup>()
    var index = 0
    while (index < rows.size) {
        val row = rows[index]
        if (row.type == HistoryDiffType.SAME) {
            index++
            continue
        }
        val sameTypeRows = mutableListOf<HistoryDiffDisplayRow>()
        val type = row.type
        while (index < rows.size && rows[index].type == type) {
            sameTypeRows += rows[index]
            index++
        }
        val lineNumber = sameTypeRows.firstOrNull()?.oldLineNumber ?: sameTypeRows.firstOrNull()?.newLineNumber ?: 1
        val title = when (type) {
            HistoryDiffType.CHANGE -> "第 $lineNumber 行：内容改写"
            HistoryDiffType.ADD -> "新增内容"
            HistoryDiffType.REMOVE -> "删除内容"
            HistoryDiffType.SAME -> "未变化内容"
        }
        val subtitle = when (type) {
            HistoryDiffType.CHANGE -> "上面是左侧版本，下面是右侧版本"
            HistoryDiffType.ADD -> "右侧版本新增了 ${sameTypeRows.size} 行"
            HistoryDiffType.REMOVE -> "左侧版本有，右侧版本已删除"
            HistoryDiffType.SAME -> "未变化内容"
        }
        groups += HistoryDiffGroup(
            type = type,
            title = title,
            subtitle = subtitle,
            rows = sameTypeRows,
        )
    }
    return groups
}

@Composable
internal fun diffColors(type: HistoryDiffType): DiffBlockColors =
    when (type) {
        HistoryDiffType.ADD -> DiffBlockColors(
            background = HistoryUiColors.GreenBackground,
            border = HistoryUiColors.GreenBorder,
            content = HistoryUiColors.GreenText,
        )
        HistoryDiffType.REMOVE -> DiffBlockColors(
            background = HistoryUiColors.RedBackground,
            border = HistoryUiColors.RedBorder,
            content = HistoryUiColors.RedText,
        )
        HistoryDiffType.CHANGE -> DiffBlockColors(
            background = HistoryUiColors.YellowBackground,
            border = HistoryUiColors.YellowBorder,
            content = HistoryUiColors.YellowText,
        )
        HistoryDiffType.SAME -> DiffBlockColors(
            background = HistoryUiColors.CardBackground,
            border = HistoryUiColors.Border,
            content = HistoryUiColors.TextTertiary,
        )
    }

internal object HistoryUiColors {
    val PageBackground: Color
        @Composable get() = MaterialTheme.colorScheme.background
    val TopBarBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val CardBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surface
    val TextPrimary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f)
    val TextSecondary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val TextTertiary: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
    val TextMuted: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    val Border: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
    val SoftBorder: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val PanelBackground: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.10f)
    val SubPanelBackground: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.06f)
    val SelectedPanelBackground: Color
        @Composable get() = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    val IconButtonBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    val DarkButton: Color
        @Composable get() = MaterialTheme.colorScheme.primary
    val NeutralPill: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    val DisabledButton: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val GreenBackground = Color(0xFFECFDF5)
    val GreenBorder = Color(0xFFBBF7D0)
    val GreenText = Color(0xFF047857)
    val RedBackground = Color(0xFFFFF1F2)
    val RedBorder = Color(0xFFFECDD3)
    val RedText = Color(0xFFBE123C)
    val YellowBackground = Color(0xFFFFFBEB)
    val YellowBorder = Color(0xFFFDE68A)
    val YellowText = Color(0xFF92400E)
}

internal data class HistoryVersionItem(
    val key: String,
    val title: String,
    val meta: String,
    val sourceMeta: String,
    val badge: String,
    val content: String,
    val contentLength: Int,
    val contentIsPreview: Boolean,
    val current: Boolean,
    val history: NoteHistory?,
) {
    companion object {
        const val CURRENT_KEY = "current"
    }
}

internal enum class HistoryCompareSide {
    LEFT,
    RIGHT,
}

internal enum class HistoryCompareMode(val label: String) {
    CHANGES("只看改动"),
    FULL("完整正文"),
    SPLIT("并排对比"),
}

internal fun HistoryCompareMode.shift(offset: Int): HistoryCompareMode {
    val modes = HistoryCompareMode.entries
    val targetIndex = (modes.indexOf(this) + offset).coerceIn(0, modes.lastIndex)
    return modes[targetIndex]
}

internal enum class HistoryDiffType {
    ADD,
    REMOVE,
    CHANGE,
    SAME,
}

internal data class HistoryDiffModel(
    val groups: List<HistoryDiffGroup>,
    val displayRows: List<HistoryDiffDisplayRow>,
    val addCount: Int,
    val removeCount: Int,
    val changeCount: Int,
) {
    companion object {
        fun empty(): HistoryDiffModel =
            HistoryDiffModel(
                groups = emptyList(),
                displayRows = emptyList(),
                addCount = 0,
                removeCount = 0,
                changeCount = 0,
            )
    }
}

internal data class HistoryDiffGroup(
    val type: HistoryDiffType,
    val title: String,
    val subtitle: String,
    val rows: List<HistoryDiffDisplayRow>,
)

internal data class HistoryDiffDisplayRow(
    val type: HistoryDiffType,
    val oldText: String?,
    val newText: String?,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
)

internal data class DiffBlockColors(
    val background: Color,
    val border: Color,
    val content: Color,
)

private enum class LineDiffType {
    SAME,
    DELETED,
    ADDED,
}

private data class LineDiffOp(
    val type: LineDiffType,
    val line: String,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
)

private fun buildLineDiffOps(
    oldLines: List<String>,
    newLines: List<String>,
): List<LineDiffOp> {
    val rows = oldLines.size
    val cols = newLines.size
    val dp = Array(rows + 1) { IntArray(cols + 1) }
    for (i in rows - 1 downTo 0) {
        for (j in cols - 1 downTo 0) {
            dp[i][j] =
                if (oldLines[i] == newLines[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
        }
    }

    val ops = mutableListOf<LineDiffOp>()
    var i = 0
    var j = 0
    while (i < rows && j < cols) {
        when {
            oldLines[i] == newLines[j] -> {
                ops += LineDiffOp(LineDiffType.SAME, oldLines[i], i + 1, j + 1)
                i++
                j++
            }
            dp[i + 1][j] >= dp[i][j + 1] -> {
                ops += LineDiffOp(LineDiffType.DELETED, oldLines[i], i + 1, null)
                i++
            }
            else -> {
                ops += LineDiffOp(LineDiffType.ADDED, newLines[j], null, j + 1)
                j++
            }
        }
    }
    while (i < rows) {
        ops += LineDiffOp(LineDiffType.DELETED, oldLines[i], i + 1, null)
        i++
    }
    while (j < cols) {
        ops += LineDiffOp(LineDiffType.ADDED, newLines[j], null, j + 1)
        j++
    }
    return ops
}
