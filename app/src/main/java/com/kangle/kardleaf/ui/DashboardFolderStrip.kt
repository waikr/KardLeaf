package com.kangle.kardleaf.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.localizedText
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException

private const val CUSTOM_SORT_FLASH_TAG = "KardLeafCustomSortFlash"
private const val TAB_VISIBILITY_TAG = "KardLeafTabVisibility"
private val nextTabVisibilityRequestId = AtomicLong()

private inline fun logFolderStripTrace(message: () -> String) {
    if (KardLeafLog.isEnabled(CUSTOM_SORT_FLASH_TAG)) {
        KardLeafLog.d(CUSTOM_SORT_FLASH_TAG, message())
    }
}

private inline fun logTabVisibility(message: () -> String) {
    if (KardLeafLog.isEnabled(TAB_VISIBILITY_TAG)) {
        KardLeafLog.d(TAB_VISIBILITY_TAG, message())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderPathStrip(
    currentFilter: MainViewModel.NoteFilter,
    labels: List<String>,
    onOpenFolder: (String) -> Unit,
    onShowAllInFolder: (String) -> Unit = { path -> onOpenFolder(path) },
    rootChip: FolderChipData? = null,
    leadingChips: List<FolderChipData> = emptyList(),
    selectedLeadingPath: String? = null,
    previewPath: String = "",
    pagerCurrentPage: Int = -1,
    pagerSettledPage: Int = -1,
    pagerScrolling: Boolean = false,
    folderOrderVersion: Int = 0,
    savedOrderFor: (String) -> List<String> = { emptyList() },
    editMode: Boolean = false,
    onAddFolder: (String) -> Unit = {},
) {
    val filterLabel = currentFilter as? MainViewModel.NoteFilter.Label
    val filterPath = filterLabel?.name.orEmpty()
    val isRecursive = filterLabel?.recursive == true
    val currentPath = previewPath.ifBlank { filterPath }
    val rows = remember(labels, currentPath, folderOrderVersion) {
        buildFolderRows(labels, currentPath, savedOrderFor)
    }
    val visibleRows =
        if (rows.isEmpty() && (rootChip != null || leadingChips.isNotEmpty() || editMode)) {
            listOf(FolderRow(parentPath = "", children = emptyList(), selectedPath = null))
        } else {
            rows
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clipToBounds(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        visibleRows.forEach { row ->
            val rowChildren = remember(row.children, row.parentPath, rootChip, leadingChips) {
                if (row.parentPath.isBlank()) {
                    buildList {
                        rootChip?.let(::add)
                        addAll(leadingChips)
                        addAll(row.children)
                    }
                } else {
                    row.children
                }
            }
            val selectedPath = when {
                row.parentPath.isBlank() && selectedLeadingPath != null -> selectedLeadingPath
                row.parentPath.isBlank() && rootChip != null && currentFilter is MainViewModel.NoteFilter.All -> rootChip.path
                else -> row.selectedPath
            }
            val rowKey = rowChildren.joinToString("|") { it.path }
            key(row.parentPath) {
                val rowState = rememberScrollState()
                val requesters =
                    remember(rowChildren) {
                        rowChildren.associate { it.path to BringIntoViewRequester() }
                    }
                val selectedIndex = rowChildren.indexOfFirst { it.path == selectedPath }
                val pagerTrace =
                    rememberUpdatedState(
                        "pager=$pagerCurrentPage/$pagerSettledPage scrolling=$pagerScrolling",
                    )

                LaunchedEffect(selectedPath) {
                    val targetPath = selectedPath ?: return@LaunchedEffect
                    val requester = requesters[targetPath] ?: return@LaunchedEffect
                    val requestId = nextTabVisibilityRequestId.incrementAndGet()
                    val scrollBefore = rowState.value
                    logTabVisibility {
                        "requestStart rowHash=${rowKey.hashCode()} pathHash=${targetPath.hashCode()} " +
                            "selectedIndex=$selectedIndex requestId=$requestId ${pagerTrace.value} " +
                            "scrollBefore=$scrollBefore scrollMax=${rowState.maxValue} " +
                            "mechanism=bringIntoView selectedChangeSource=selectedPath"
                    }
                    try {
                        requester.bringIntoView()
                        val scrollAfter = rowState.value
                        logTabVisibility {
                            "requestEnd rowHash=${rowKey.hashCode()} pathHash=${targetPath.hashCode()} " +
                                "selectedIndex=$selectedIndex requestId=$requestId ${pagerTrace.value} " +
                                "scrollBefore=$scrollBefore scrollAfter=$scrollAfter scrollMax=${rowState.maxValue} " +
                                "mechanism=bringIntoView result=${if (scrollAfter == scrollBefore) "noMove" else "completed"}"
                        }
                    } catch (cancelled: CancellationException) {
                        logTabVisibility {
                            "requestEnd rowHash=${rowKey.hashCode()} pathHash=${targetPath.hashCode()} " +
                                "selectedIndex=$selectedIndex requestId=$requestId ${pagerTrace.value} " +
                                "scrollBefore=$scrollBefore scrollAfter=${rowState.value} scrollMax=${rowState.maxValue} " +
                                "mechanism=bringIntoView result=cancelled cancellationReason=supersededOrInterrupted"
                        }
                        throw cancelled
                    }
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rowState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowChildren.forEach { folder ->
                        key(folder.path) {
                            val isRootChip = rootChip != null && row.parentPath.isBlank() && folder.path == rootChip.path
                            val isLeadingChip = row.parentPath.isBlank() && leadingChips.any { it.path == folder.path }
                            val isHighlighted = folder.path == selectedPath
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
                                modifier = Modifier.bringIntoViewRequester(requesters.getValue(folder.path)),
                                onClick = {
                                    if (isRootChip || isLeadingChip) {
                                        onOpenFolder(folder.path)
                                    } else if (isHighlighted) {
                                        // 点击高亮（当前选中）的分类标签 → 显示该文件夹全部子笔记
                                        onShowAllInFolder(folder.path)
                                    } else {
                                        // 点击非高亮标签 → 切换到对应目录浏览
                                        onOpenFolder(folder.path)
                                    }
                                },
                            )
                        }
                    }
                    if (editMode) {
                        IconButton(onClick = { onAddFolder(row.parentPath) }) {
                            Icon(Icons.Outlined.Add, contentDescription = "在此分类添加分类项")
                        }
                    }
                }
            }
        }
    }
}

private data class FolderRow(
    val parentPath: String,
    val children: List<FolderChipData>,
    val selectedPath: String?,
)

data class FolderChipData(
    val name: String,
    val path: String,
)

internal fun buildFolderPagerPages(
    labels: List<String>,
    currentFilter: MainViewModel.NoteFilter,
    savedOrderFor: (String) -> List<String> = { emptyList() },
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
            val rootPage = FolderChipData(localizedText("全部笔记", "All notes"), "")
            val topLevel = directChildFolders(normalizedLabels, "", savedOrderFor)
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
            val siblings = directChildFolders(normalizedLabels, parent, savedOrderFor)
            val siblingPages =
                if (siblings.any { it.path == currentPath }) {
                    siblings
                } else {
                    val currentName = currentPath.substringAfterLast("/")
                    (siblings + FolderChipData(currentName, currentPath))
                        .distinctBy { it.path }
                        .sortedBy { it.name }
                }
            // 顶层目录和“全部笔记”共用同一组 Pager 页面，避免从全部笔记滑入目录后
            // pages 立刻从 [全部, 顶层目录...] 变成 [顶层目录...]，导致连续滑动被重建吞掉。
            if (parent.isBlank()) {
                listOf(FolderChipData(localizedText("全部笔记", "All notes"), "")) + siblingPages
            } else {
                siblingPages
            }
        }
    }
    logFolderStripTrace {
        "buildFolderPagerPages filter=$currentFilter labels=${normalizedLabels.size} result=${folderChipSummary(result)}"
    }
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
    savedOrderFor: (String) -> List<String>,
): List<FolderRow> {
    val normalizedLabels = labels.map { normalizeFolderPathForUi(it) }.filter { it.isNotBlank() }.distinct().sorted()
    val currentSegments = normalizeFolderPathForUi(currentPath).split("/").filter { it.isNotBlank() }
    val rows = mutableListOf<FolderRow>()

    var parent = ""
    var depth = 0
    while (true) {
        val children = directChildFolders(normalizedLabels, parent, savedOrderFor)
        if (children.isEmpty()) break
        val selectedPath =
            if (depth < currentSegments.size) {
                currentSegments.take(depth + 1).joinToString("/").takeIf { it.isNotBlank() }
            } else {
                null
            }
        rows += FolderRow(parentPath = parent, children = children, selectedPath = selectedPath)
        if (selectedPath.isNullOrBlank()) break
        parent = selectedPath.orEmpty()
        depth += 1
    }

    return rows
}

private fun directChildFolders(
    labels: List<String>,
    parent: String,
    savedOrderFor: (String) -> List<String>,
): List<FolderChipData> {
    val prefix = parent.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
    val orderIndex = savedOrderFor(parent).withIndex().associate { it.value to it.index }
    return labels
        .asSequence()
        .filter { it.startsWith(prefix) && it != parent }
        .map { path -> normalizeFolderPathForUi(path) }
        .filter { path ->
            val child = path.removePrefix(prefix)
            child.isNotBlank() && !child.contains("/")
        }
        .distinct()
        .sortedWith(
            compareBy<String> { path -> orderIndex[path] ?: Int.MAX_VALUE }
                .thenBy { path -> path.substringAfterLast('/').lowercase() },
        )
        .map { path ->
            FolderChipData(
                name = path.substringAfterLast('/'),
                path = path,
            )
        }
        .toList()
}

internal fun normalizeFolderPathForUi(path: String): String =
    path
        .replace("\\", "/")
        .split("/")
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "." }
        .joinToString("/")

@Composable
fun FolderChip(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val themeStyle = LocalKardLeafThemeStyle.current
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val isDracula = themeStyle == PrefsManager.AppThemeStyle.DRACULA
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(if (isDracula) 8.dp else if (isModern) 999.dp else 8.dp)
    val scale by animateFloatAsState(
        targetValue = if (isModern && isPressed) 0.96f else 1f,
        label = "FolderChipPressedScale",
    )
    Surface(
        shape = shape,
        color =
            if (isDracula && selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (isDracula) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (isModern) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        tonalElevation = if (isModern && selected && !isDracula) 3.dp else 0.dp,
        shadowElevation = if (isModern && selected && !isDracula) 2.dp else 0.dp,
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(
                    width = if (selected || isDracula) 1.dp else 0.dp,
                    color = if (selected || isDracula) MaterialTheme.colorScheme.primary.copy(alpha = if (selected) 0.9f else 0.38f) else Color.Transparent,
                    shape = shape,
                )
                .clickable(
                    interactionSource = interactionSource,
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
                } else if (isModern) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
