package com.kangle.kardleaf.ui

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first

private const val CUSTOM_SORT_FLASH_TAG = "KardLeafCustomSortFlash"

@Composable
fun FolderPathStrip(
    currentFilter: MainViewModel.NoteFilter,
    labels: List<String>,
    onOpenFolder: (String) -> Unit,
    onShowAllInFolder: (String) -> Unit = { path -> onOpenFolder(path) },
    previewPath: String = "",
) {
    val filterLabel = currentFilter as? MainViewModel.NoteFilter.Label
    val filterPath = filterLabel?.name.orEmpty()
    val isRecursive = filterLabel?.recursive == true
    val currentPath = previewPath.ifBlank { filterPath }
    val rows = remember(labels, currentPath) { buildFolderRows(labels, currentPath) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            val rowKey = row.children.joinToString("|") { it.path }
            val rowState = rememberLazyListState()
            val selectedIndex = row.children.indexOfFirst { it.path == row.selectedPath }
            val itemSpacingPx = with(LocalDensity.current) { 8.dp.toPx() }

            // 选中项变化时滚动保证其可见，不被遮挡。
            // 用 snapshotFlow 监听布局信息变化，选中项不可见或被边缘遮挡时滚动。
            LaunchedEffect(rowKey, row.selectedPath) {
                if (selectedIndex < 0) return@LaunchedEffect
                snapshotFlow { rowState.layoutInfo }
                    .first { it.totalItemsCount > 0 }
                if (selectedIndex == 0) {
                    rowState.animateScrollToItem(0)
                    return@LaunchedEffect
                }
                val layoutInfo = rowState.layoutInfo
                val selectedItem = layoutInfo.visibleItemsInfo.find { it.index == selectedIndex }
                val visibleItems = layoutInfo.visibleItemsInfo
                val viewportEnd = layoutInfo.viewportEndOffset
                if (selectedItem == null && visibleItems.isNotEmpty()) {
                    val firstVisible = visibleItems.first()
                    val lastVisible = visibleItems.last()
                    val oneItemScroll =
                        visibleItems
                            .zipWithNext()
                            .firstOrNull()
                            ?.let { (first, second) -> second.offset - first.offset }
                            ?.takeIf { it > 0 }
                            ?.toFloat()
                            ?: (lastVisible.size + itemSpacingPx)
                    when {
                        selectedIndex > lastVisible.index -> rowState.animateScrollBy(oneItemScroll)
                        selectedIndex < firstVisible.index -> rowState.animateScrollBy(-oneItemScroll)
                    }
                    return@LaunchedEffect
                }
                if (selectedItem == null) return@LaunchedEffect
                val selectedEnd = selectedItem.offset + selectedItem.size
                if (selectedEnd > viewportEnd) {
                    rowState.animateScrollBy((selectedEnd - viewportEnd).toFloat())
                } else if (selectedItem.offset < layoutInfo.viewportStartOffset) {
                    rowState.animateScrollBy((selectedItem.offset - layoutInfo.viewportStartOffset).toFloat())
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                state = rowState,
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lazyRowItems(
                    items = row.children,
                    key = { it.path },
                ) { folder ->
                    val isHighlighted = folder.path == row.selectedPath
                    // recursive 模式下，被选中并显示全部子笔记的高亮分类项追加" · 全部"后缀
                    val displayText =
                        if (isHighlighted && isRecursive && folder.path == filterPath) {
                            "${folder.name} · 全部"
                        } else {
                            folder.name
                        }
                    FolderChip(
                        text = displayText,
                        selected = isHighlighted,
                        onClick = {
                            // 点击高亮（当前选中）的分类标签 → 显示该文件夹全部子笔记；
                            // 点击非高亮标签 → 切换到对应目录浏览
                            if (isHighlighted) {
                                onShowAllInFolder(folder.path)
                            } else {
                                onOpenFolder(folder.path)
                            }
                        },
                    )
                }
            }
        }
    }
}

private data class FolderRow(
    val children: List<FolderChipData>,
    val selectedPath: String?,
)

internal data class FolderChipData(
    val name: String,
    val path: String,
)

internal fun buildFolderPagerPages(
    labels: List<String>,
    currentFilter: MainViewModel.NoteFilter,
): List<FolderChipData> {
    val normalizedLabels = labels
        .map(::normalizeFolderPathForUi)
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

    val result = if (currentFilter is MainViewModel.NoteFilter.All) {
        if (normalizedLabels.isEmpty()) {
            emptyList()
        } else {
            val rootPage = FolderChipData("全部笔记", "")
            val topLevel = directChildFolders(normalizedLabels, "")
            listOf(rootPage) + topLevel
        }
    } else {
        val currentPath = (currentFilter as? MainViewModel.NoteFilter.Label)?.name
            ?.let(::normalizeFolderPathForUi)
            .orEmpty()
        if (currentPath.isBlank()) {
            emptyList()
        } else {
            val parent = currentPath.substringBeforeLast("/", missingDelimiterValue = "")
            val siblings = directChildFolders(normalizedLabels, parent)
            if (siblings.any { it.path == currentPath }) {
                siblings
            } else {
                val currentName = currentPath.substringAfterLast("/")
                (siblings + FolderChipData(currentName, currentPath))
                    .distinctBy { it.path }
                    .sortedBy { it.name }
            }
        }
    }
    Log.d(
        CUSTOM_SORT_FLASH_TAG,
        "buildFolderPagerPages filter=$currentFilter labels=${normalizedLabels.size} result=${folderChipSummary(result)}",
    )
    return result
}


private fun folderChipSummary(pages: Collection<FolderChipData>, limit: Int = 8): String {
    val paths = pages.map { it.path.ifBlank { "<ALL>" } }
    val suffix = if (paths.size > limit) ", ..." else ""
    return "size=${paths.size} head=${paths.take(limit)}$suffix"
}

private fun buildFolderRows(
    labels: List<String>,
    currentPath: String,
): List<FolderRow> {
    val normalizedLabels = labels.map { normalizeFolderPathForUi(it) }.filter { it.isNotBlank() }.distinct().sorted()
    val currentSegments = normalizeFolderPathForUi(currentPath).split("/").filter { it.isNotBlank() }
    val rows = mutableListOf<FolderRow>()

    var parent = ""
    var depth = 0
    while (true) {
        val children = directChildFolders(normalizedLabels, parent)
        if (children.isEmpty()) break
        val selectedPath =
            if (depth < currentSegments.size) {
                currentSegments.take(depth + 1).joinToString("/").takeIf { it.isNotBlank() }
            } else {
                null
            }
        rows += FolderRow(children = children, selectedPath = selectedPath)
        if (selectedPath.isNullOrBlank()) break
        parent = selectedPath.orEmpty()
        depth += 1
    }

    return rows
}

private fun directChildFolders(
    labels: List<String>,
    parent: String,
): List<FolderChipData> {
    val prefix = parent.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
    return labels
        .asSequence()
        .filter { it.startsWith(prefix) && it != parent }
        .map { it.removePrefix(prefix) }
        .filter { it.isNotBlank() && !it.contains("/") }
        .distinct()
        .sorted()
        .map { child ->
            FolderChipData(
                name = child,
                path = if (parent.isBlank()) child else "$parent/$child",
            )
        }
        .toList()
}

internal fun normalizeFolderPathForUi(path: String): String =
    path
        .replace("\\", "/")
        .split("/")
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "Unknown" && it != "." }
        .joinToString("/")

@Composable
fun FolderChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        modifier =
            Modifier
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color =
                if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
