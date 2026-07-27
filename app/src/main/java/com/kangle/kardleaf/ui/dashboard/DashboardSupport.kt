package com.kangle.kardleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.localizedText
import com.kangle.kardleaf.ui.theme.LocalKardLeafGlobalCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafHomeCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle

@Composable
internal fun DashboardFolderLocationRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}



internal fun folderPagerPathSummary(pages: Collection<FolderChipData>, limit: Int = 8): String {
    val paths = pages.map { it.path.ifBlank { "<ALL>" } }
    val suffix = if (paths.size > limit) ", ..." else ""
    return "size=${paths.size} currentHead=${paths.take(limit)}$suffix"
}

internal data class FolderPagerPreviewItems(
    val items: List<DashboardUiItem>,
    val sortOrder: PrefsManager.SortOrder,
    val sortDirection: PrefsManager.SortDirection,
)

internal fun buildFolderPagerPreviewItems(
    notes: List<Note>,
    path: String,
    defaultSortOrder: PrefsManager.SortOrder,
    defaultSortDirection: PrefsManager.SortDirection,
    getFolderSortSettings: (String) -> PrefsManager.FolderSortSettings?,
    getFolderCustomSortOrder: (String) -> List<String>,
): FolderPagerPreviewItems? {
    if (path.isBlank()) return null
    val settings = getFolderSortSettings(path)
    val order = settings?.order ?: defaultSortOrder
    val direction = settings?.direction ?: defaultSortDirection
    val customOrder =
        if (order == PrefsManager.SortOrder.CUSTOM) {
            getFolderCustomSortOrder(path)
        } else {
            emptyList()
        }
    return FolderPagerPreviewItems(
        items = buildGesturePreviewItemsForFolderNotes(
            notes = notes.filter { !it.isTrashed && it.folder == path },
            folder = path,
            sortOrder = order,
            sortDirection = direction,
            customOrder = customOrder,
        ),
        sortOrder = order,
        sortDirection = direction,
    )
}

internal fun dashboardFilterNoteCountSummary(
    filter: MainViewModel.NoteFilter,
    allNotes: Collection<Note>,
): String {
    val folderFilter = filter as? MainViewModel.NoteFilter.Label
    if (folderFilter == null) {
        val activeAll = allNotes.count { !it.isTrashed && !it.isArchived }
        return "activeAll=$activeAll folderDirect=-1 folderRecursive=-1"
    }
    val normalizedFolder = folderFilter.name.normalizeDashboardFolderPath()
    val recursivePrefix = if (normalizedFolder.isBlank()) "" else "$normalizedFolder/"
    var directCount = 0
    var recursiveCount = 0
    var activeAll = 0
    allNotes.forEach { note ->
        if (note.isTrashed || note.isArchived) return@forEach
        activeAll += 1
        val folder = note.folder.normalizeDashboardFolderPath()
        if (folder == normalizedFolder) {
            directCount += 1
            recursiveCount += 1
        } else if (recursivePrefix.isNotEmpty() && folder.startsWith(recursivePrefix)) {
            recursiveCount += 1
        }
    }
    return "activeAll=$activeAll folderDirect=$directCount folderRecursive=$recursiveCount recursive=${folderFilter.recursive}"
}

internal fun pathListFlashSummary(paths: Collection<String>, limit: Int = 6): String {
    val normalized = paths.map { it.normalizeDashboardFolderPath() }
    val suffix = if (normalized.size > limit) ", ..." else ""
    return "size=${normalized.size} head=${normalized.take(limit)}$suffix"
}

internal fun dashboardScreenUiItemsFlashSummary(items: Collection<DashboardUiItem>, limit: Int = 6): String {
    val notePaths = items.mapNotNull { (it as? DashboardUiItem.NoteItem)?.note?.file?.path }
    val normalized = notePaths.map { it.normalizeDashboardFolderPath() }
    val suffix = if (normalized.size > limit) ", ..." else ""
    val headerCount = items.count { it is DashboardUiItem.HeaderItem }
    val spacerCount = items.count { it is DashboardUiItem.SpacerItem }
    return "items=${items.size} notes=size=${normalized.size} head=${normalized.take(limit)}$suffix headers=$headerCount spacers=$spacerCount"
}

internal fun dashboardPathDebugHash(path: String): String =
    if (path.isBlank()) "ALL" else path.normalizeDashboardFolderPath().hashCode().toString(16)

internal fun folderPagerPathHashSummary(pages: Collection<FolderChipData>, limit: Int = 8): String {
    val hashes = pages.map { dashboardPathDebugHash(it.path) }
    val suffix = if (hashes.size > limit) ", ..." else ""
    return "size=${hashes.size} hashes=${hashes.take(limit)}$suffix"
}

internal fun dashboardThumbnailProbeSummary(items: Collection<DashboardUiItem>, limit: Int = 6): String {
    val imageNotes = items.mapNotNull { (it as? DashboardUiItem.NoteItem)?.note }
        .filter { !it.firstImageReference.isNullOrBlank() }
    val suffix = if (imageNotes.size > limit) ", ..." else ""
    val head = imageNotes.take(limit).map { note ->
        val reference = note.firstImageReference.orEmpty()
        "${note.file.path.normalizeDashboardFolderPath().hashCode().toString(16)}:${reference.length}:${reference.take(36)}"
    }
    return "thumbRefs=${imageNotes.size} thumbHead=$head$suffix"
}

internal fun String.normalizeDashboardFolderPath(): String =
    replace("\\", "/")
        .trim('/')
        .trim()


internal fun dashboardTitle(filter: MainViewModel.NoteFilter): String =
    when (filter) {
        is MainViewModel.NoteFilter.All -> localizedText("全部笔记", "All notes")
        is MainViewModel.NoteFilter.Recent -> localizedText("最近修改", "Recent")
        is MainViewModel.NoteFilter.Favorites -> localizedText("收藏", "Favorites")
        is MainViewModel.NoteFilter.QuickNotes -> localizedText("速记", "Quick notes")
        is MainViewModel.NoteFilter.Random -> localizedText("随机", "Random")
        is MainViewModel.NoteFilter.Label -> filter.name.substringAfterLast("/").ifBlank { localizedText("文件夹", "Folder") }
        is MainViewModel.NoteFilter.YamlTag -> "#${filter.name}"
        is MainViewModel.NoteFilter.Archive -> localizedText("归档", "Archive")
        is MainViewModel.NoteFilter.Trash -> localizedText("废弃", "Trash")
    }

internal fun dashboardTitleForPath(path: String): String =
    path.substringAfterLast("/").ifBlank { localizedText("全部笔记", "All notes") }

@Composable
internal fun HomeBottomToolbar(
    items: List<PrefsManager.HomeBottomToolbarItemId>,
    buttonSizeDp: Int,
    onItemClick: (PrefsManager.HomeBottomToolbarItemId) -> Unit,
) {
    val isDracula = LocalKardLeafThemeStyle.current == PrefsManager.AppThemeStyle.DRACULA
    val homeCornerRadiusDp = LocalKardLeafHomeCornerRadiusDp.current.takeIf { it >= 0 }
        ?: LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
    val shape = RoundedCornerShape((homeCornerRadiusDp ?: 34).dp)
    val containerColor = if (isDracula) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }
    val preferredItemSize = buttonSizeDp
        .coerceIn(
            PrefsManager.MIN_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP,
            PrefsManager.MAX_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP,
        )
        .dp
    val visibleCount = items.size
    val horizontalScrollState = rememberScrollState()

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val enableHorizontalScroll = visibleCount >= 8
        val outerHorizontalPadding = when {
            visibleCount <= 5 -> 18.dp
            visibleCount <= 7 -> 6.dp
            else -> 8.dp
        }
        val contentHorizontalPadding = when {
            visibleCount <= 5 -> 14.dp
            visibleCount <= 7 -> 8.dp
            else -> 10.dp
        }
        val itemSpacing = when {
            visibleCount <= 5 -> 10.dp
            visibleCount <= 7 -> 6.dp
            else -> 8.dp
        }
        val maxToolbarWidth = maxWidth - outerHorizontalPadding * 2f
        val fitItemSize = if (!enableHorizontalScroll && visibleCount > 0) {
            calculateHomeBottomToolbarFitItemSize(
                preferredItemSize = preferredItemSize,
                minItemSize = PrefsManager.MIN_HOME_BOTTOM_TOOLBAR_BUTTON_SIZE_DP.dp,
                maxWidth = maxToolbarWidth,
                contentHorizontalPadding = contentHorizontalPadding,
                itemSpacing = itemSpacing,
                itemCount = visibleCount,
            )
        } else {
            preferredItemSize
        }
        val toolbarHeight = (fitItemSize + 24.dp).coerceAtLeast(62.dp)
        val iconSize = (fitItemSize * 0.48f).coerceAtMost(26.dp)

        Surface(
            modifier = Modifier
                .padding(horizontal = outerHorizontalPadding, vertical = 10.dp)
                .widthIn(max = maxToolbarWidth)
                .shadow(14.dp, shape, clip = false)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDracula) 0.28f else 0.42f),
                    shape = shape,
                ),
            shape = shape,
            color = containerColor,
            tonalElevation = if (isDracula) 0.dp else 8.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .height(toolbarHeight)
                    .then(if (enableHorizontalScroll) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                    .padding(horizontal = contentHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { itemId ->
                    val itemColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = if (isDracula) 0.26f else 0.72f,
                    )
                    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
                    val itemShape = homeCornerRadiusDp?.let { RoundedCornerShape(it.dp) } ?: CircleShape

                    Box(
                        modifier = Modifier
                            .size(fitItemSize)
                            .clip(itemShape)
                            .background(itemColor)
                            .clickable { onItemClick(itemId) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = homeBottomToolbarItemIcon(itemId),
                            contentDescription = homeBottomToolbarItemLabel(itemId),
                            modifier = Modifier.size(iconSize),
                            tint = iconTint,
                        )
                    }
                }
            }
        }
    }
}

internal fun calculateHomeBottomToolbarFitItemSize(
    preferredItemSize: Dp,
    minItemSize: Dp,
    maxWidth: Dp,
    contentHorizontalPadding: Dp,
    itemSpacing: Dp,
    itemCount: Int,
): Dp {
    if (itemCount <= 0) return preferredItemSize
    val availableWidth = maxWidth - contentHorizontalPadding * 2f - itemSpacing * (itemCount - 1).toFloat()
    return (availableWidth / itemCount.toFloat()).coerceIn(minItemSize, preferredItemSize)
}


internal fun homeBottomToolbarItemAvailable(
    itemId: PrefsManager.HomeBottomToolbarItemId,
    currentFilter: MainViewModel.NoteFilter,
): Boolean {
    val isReadonlyList = currentFilter is MainViewModel.NoteFilter.Archive || currentFilter is MainViewModel.NoteFilter.Trash
    return when (itemId) {
        PrefsManager.HomeBottomToolbarItemId.NEW_NOTE,
        PrefsManager.HomeBottomToolbarItemId.NEW_DRAFT,
        PrefsManager.HomeBottomToolbarItemId.NEW_DRAWING,
        PrefsManager.HomeBottomToolbarItemId.NEW_FOLDER -> !isReadonlyList
        else -> true
    }
}

@Composable
internal fun HomeFabIconButton(
    icon: ImageVector,
    contentDescription: String,
    onSwipeDown: () -> Unit,
    onClick: () -> Unit,
) {
    val isDracula = LocalKardLeafThemeStyle.current == PrefsManager.AppThemeStyle.DRACULA
    val homeCornerRadiusDp = LocalKardLeafHomeCornerRadiusDp.current.takeIf { it >= 0 }
        ?: LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
    val shape = homeCornerRadiusDp?.let { RoundedCornerShape(it.dp) }
        ?: if (isDracula) RoundedCornerShape(14.dp) else CircleShape
    Surface(
        modifier =
            Modifier
                .size(56.dp)
                .clip(shape)
                .then(
                    if (isDracula) {
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), shape)
                    } else {
                        Modifier
                    },
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount > 0f) {
                            onSwipeDown()
                            change.consume()
                        }
                    }
                }
                .clickable(onClick = onClick),
        shape = shape,
        color = if (isDracula) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = if (isDracula) 0.dp else 4.dp,
        shadowElevation = if (isDracula) 0.dp else 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (isDracula) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
