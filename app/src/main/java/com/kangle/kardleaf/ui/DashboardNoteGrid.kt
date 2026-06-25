package com.kangle.kardleaf.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

private const val DASHBOARD_CUSTOM_SORT_TRACE_TAG = "KardLeafCustomSort"
private const val DASHBOARD_CUSTOM_SORT_FLASH_TAG = "KardLeafCustomSortFlash"

@Composable
fun NoteGrid(
    uiItems: List<DashboardUiItem>,
    selectedNotes: Set<String>,
    isLoading: Boolean,
    notesCount: Int,
    viewMode: PrefsManager.ViewMode,
    cardDensity: PrefsManager.CardDensity,
    showFolderTags: Boolean,
    showYamlTags: Boolean,
    showModifiedDate: Boolean,
    showDeletedDate: Boolean = false,
    showNoteTitle: Boolean,
    showDateFilenameTitle: Boolean,
    customHiddenFilenamePatterns: List<String>,
    unnamedNoteDateFormat: String,
    searchQuery: String,
    listState: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
    loadImageThumbnail: suspend (Note) -> android.graphics.Bitmap? = { null },
    enableCustomSortDrag: Boolean = false,
    customSortDragHandleEnabled: Boolean = enableCustomSortDrag,
    showCustomSortDragHandleIcon: Boolean = false,
    onCustomSortOrderChanged: (List<String>) -> Unit = {},
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit,
) {
    if (isLoading && notesCount == 0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.loading_notes),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else if (notesCount == 0) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    stringResource(R.string.no_results_found),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        val columns = if (viewMode == PrefsManager.ViewMode.GRID) 2 else 1
        val haptic = LocalHapticFeedback.current
        val customSortItemsKey = remember(uiItems, enableCustomSortDrag) {
            if (enableCustomSortDrag) {
                uiItems.joinToString("|") { item ->
                    when (item) {
                        is DashboardUiItem.NoteItem -> "${item.key}:${item.note.lastModified.time}:${item.note.title}"
                        else -> item.key
                    }
                }
            } else {
                ""
            }
        }
        val uiItemsLogSummary = remember(uiItems) { dashboardUiItemsFlashSummary(uiItems) }
        val reorderableItems = remember(customSortItemsKey, enableCustomSortDrag) {
            Log.d(
                DASHBOARD_CUSTOM_SORT_FLASH_TAG,
                "NoteGrid create reorderableItems enable=$enableCustomSortDrag uiItems=$uiItemsLogSummary keyHash=${customSortItemsKey.hashCode()}",
            )
            mutableStateListOf<DashboardUiItem>().apply {
                if (enableCustomSortDrag) addAll(uiItems)
            }
        }
        val customSortDragMoved = remember(customSortItemsKey, enableCustomSortDrag) { mutableStateOf(false) }
        val displayedItems: List<DashboardUiItem> = if (enableCustomSortDrag) reorderableItems else uiItems
        val displayedItemsLogSummary = remember(displayedItems, enableCustomSortDrag) {
            dashboardUiItemsFlashSummary(displayedItems)
        }
        LaunchedEffect(enableCustomSortDrag, customSortDragHandleEnabled, customSortItemsKey, uiItemsLogSummary, displayedItemsLogSummary) {
            Log.d(
                DASHBOARD_CUSTOM_SORT_FLASH_TAG,
                "NoteGrid render enable=$enableCustomSortDrag handle=$customSortDragHandleEnabled notesCount=$notesCount isLoading=$isLoading selected=${selectedNotes.size} " +
                    "viewMode=$viewMode searchBlank=${searchQuery.isBlank()} uiItems=$uiItemsLogSummary displayed=$displayedItemsLogSummary keyHash=${customSortItemsKey.hashCode()}",
            )
        }
        fun currentCustomSortPaths(): List<String> =
            reorderableItems.mapNotNull { (it as? DashboardUiItem.NoteItem)?.note?.file?.path }

        val reorderableState = rememberReorderableLazyStaggeredGridState(listState) { from, to ->
            if (!enableCustomSortDrag || !customSortDragHandleEnabled || from.index == to.index) return@rememberReorderableLazyStaggeredGridState
            val fromItem = reorderableItems.getOrNull(from.index) as? DashboardUiItem.NoteItem
                ?: return@rememberReorderableLazyStaggeredGridState
            val toItem = reorderableItems.getOrNull(to.index) as? DashboardUiItem.NoteItem
                ?: return@rememberReorderableLazyStaggeredGridState
            if (fromItem.searchMatch != null || toItem.searchMatch != null) return@rememberReorderableLazyStaggeredGridState
            Log.d(
                DASHBOARD_CUSTOM_SORT_FLASH_TAG,
                "NoteGrid move from=${from.index}:${normalizeDashboardNotePath(fromItem.note.file.path)} " +
                    "to=${to.index}:${normalizeDashboardNotePath(toItem.note.file.path)} before=${dashboardUiItemsFlashSummary(reorderableItems)}",
            )
            reorderableItems.add(to.index, reorderableItems.removeAt(from.index))
            Log.d(
                DASHBOARD_CUSTOM_SORT_FLASH_TAG,
                "NoteGrid move after=${dashboardUiItemsFlashSummary(reorderableItems)}",
            )
            customSortDragMoved.value = true
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = listState,
            contentPadding = PaddingValues(0.dp),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            items(
                items = displayedItems,
                key = { it.key },
                span = { item ->
                    if (item is DashboardUiItem.HeaderItem || item is DashboardUiItem.SpacerItem) {
                        StaggeredGridItemSpan.FullLine
                    } else {
                        StaggeredGridItemSpan.SingleLane
                    }
                },
            ) { item ->
                when (item) {
                    is DashboardUiItem.HeaderItem -> {
                        val title =
                            when (item.type) {
                                DashboardUiItem.HeaderType.PINNED -> stringResource(R.string.pinned)
                                DashboardUiItem.HeaderType.OTHERS -> stringResource(R.string.others)
                                DashboardUiItem.HeaderType.ARCHIVED -> stringResource(R.string.archived_notes_header)
                                DashboardUiItem.HeaderType.SEARCH_RESULTS -> stringResource(R.string.search_results)
                                DashboardUiItem.HeaderType.SEARCH_EVERYWHERE -> stringResource(R.string.search_everywhere)
                            }
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, bottom = 4.dp, top = 8.dp),
                        ) {
                            if (item.type == DashboardUiItem.HeaderType.OTHERS ||
                                item.type == DashboardUiItem.HeaderType.ARCHIVED
                            ) {
                                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(text = title, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    is DashboardUiItem.NoteItem -> {
                        if (enableCustomSortDrag && item.searchMatch == null) {
                            ReorderableItem(
                                state = reorderableState,
                                key = item.key,
                            ) { _ ->
                                fun startCustomSortDrag() {
                                    Log.d(
                                        DASHBOARD_CUSTOM_SORT_FLASH_TAG,
                                        "NoteGrid dragStarted path=${normalizeDashboardNotePath(item.note.file.path)} order=${dashboardPathSummary(currentCustomSortPaths())}",
                                    )
                                    customSortDragMoved.value = false
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }

                                fun stopCustomSortDrag() {
                                    val currentPaths = currentCustomSortPaths()
                                    Log.d(
                                        DASHBOARD_CUSTOM_SORT_FLASH_TAG,
                                        "NoteGrid dragStopped moved=${customSortDragMoved.value} path=${normalizeDashboardNotePath(item.note.file.path)} order=${dashboardPathSummary(currentPaths)}",
                                    )
                                    if (customSortDragMoved.value) {
                                        onCustomSortOrderChanged(currentPaths)
                                    }
                                    customSortDragMoved.value = false
                                }

                                if (showCustomSortDragHandleIcon) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            NoteCard(
                                                note = item.note,
                                                isSelected = selectedNotes.contains(item.note.file.path),
                                                cardDensity = cardDensity,
                                                showFolderTag = showFolderTags,
                                                showYamlTags = showYamlTags,
                                                showModifiedDate = showModifiedDate,
                                                showDeletedDate = showDeletedDate,
                                                showNoteTitle = showNoteTitle,
                                                showDateFilenameTitle = showDateFilenameTitle,
                                                customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                                unnamedNoteDateFormat = unnamedNoteDateFormat,
                                                searchQuery = searchQuery,
                                                searchMatch = item.searchMatch,
                                                showImagePreview = viewMode == PrefsManager.ViewMode.LIST && cardDensity != PrefsManager.CardDensity.COMPACT && searchQuery.isBlank(),
                                                loadImageThumbnail = loadImageThumbnail,
                                                onClick = { onNoteClick(item.note) },
                                                onLongClick = { onNoteLongClick(item.note) },
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 6.dp)
                                                .width(34.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                                                .draggableHandle(
                                                    enabled = customSortDragHandleEnabled,
                                                    onDragStarted = { startCustomSortDrag() },
                                                    onDragStopped = { stopCustomSortDrag() },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                repeat(3) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(14.dp)
                                                            .height(2.dp)
                                                            .clip(RoundedCornerShape(999.dp))
                                                            .background(
                                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                    alpha = if (customSortDragHandleEnabled) 0.55f else 0.22f,
                                                                ),
                                                            ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box {
                                        NoteCard(
                                            note = item.note,
                                            isSelected = selectedNotes.contains(item.note.file.path),
                                            cardDensity = cardDensity,
                                            showFolderTag = showFolderTags,
                                            showYamlTags = showYamlTags,
                                            showModifiedDate = showModifiedDate,
                                            showDeletedDate = showDeletedDate,
                                            showNoteTitle = showNoteTitle,
                                            showDateFilenameTitle = showDateFilenameTitle,
                                            customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                            unnamedNoteDateFormat = unnamedNoteDateFormat,
                                            searchQuery = searchQuery,
                                            searchMatch = item.searchMatch,
                                            showImagePreview = viewMode == PrefsManager.ViewMode.LIST && cardDensity != PrefsManager.CardDensity.COMPACT && searchQuery.isBlank(),
                                            loadImageThumbnail = loadImageThumbnail,
                                            onClick = { onNoteClick(item.note) },
                                            onLongClick = { onNoteLongClick(item.note) },
                                        )
                                        Box(modifier = Modifier.matchParentSize()) {
                                            val borderDragHandleModifier =
                                                if (customSortDragHandleEnabled) {
                                                    Modifier.longPressDraggableHandle(
                                                        onDragStarted = { startCustomSortDrag() },
                                                        onDragStopped = { stopCustomSortDrag() },
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            // 只把卡片外边框附近作为拖动手柄，正文区域长按仍保留原来的多选逻辑。
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .fillMaxWidth()
                                                    .height(12.dp)
                                                    .then(borderDragHandleModifier),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .height(12.dp)
                                                    .then(borderDragHandleModifier),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .fillMaxHeight()
                                                    .width(12.dp)
                                                    .then(borderDragHandleModifier),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .fillMaxHeight()
                                                    .width(12.dp)
                                                    .then(borderDragHandleModifier),
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            NoteCard(
                                note = item.note,
                                isSelected = selectedNotes.contains(item.note.file.path),
                                cardDensity = cardDensity,
                                showFolderTag = showFolderTags,
                                showYamlTags = showYamlTags,
                                showModifiedDate = showModifiedDate,
                                showDeletedDate = showDeletedDate,
                                showNoteTitle = showNoteTitle,
                                showDateFilenameTitle = showDateFilenameTitle,
                                customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                unnamedNoteDateFormat = unnamedNoteDateFormat,
                                searchQuery = searchQuery,
                                searchMatch = item.searchMatch,
                                showImagePreview = viewMode == PrefsManager.ViewMode.LIST && cardDensity != PrefsManager.CardDensity.COMPACT && searchQuery.isBlank(),
                                loadImageThumbnail = loadImageThumbnail,
                                onClick = { onNoteClick(item.note) },
                                onLongClick = { onNoteLongClick(item.note) },
                            )
                        }
                    }
                    is DashboardUiItem.SpacerItem -> {
                        androidx.compose.foundation.layout.Spacer(
                            modifier =
                                Modifier
                                    .height(80.dp)
                                    .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

internal fun buildGesturePreviewItems(
    notes: List<Note>,
    folder: String,
    sortOrder: PrefsManager.SortOrder,
    sortDirection: PrefsManager.SortDirection,
    customOrder: List<String> = emptyList(),
): List<DashboardUiItem> {
    Log.d(
        DASHBOARD_CUSTOM_SORT_FLASH_TAG,
        "buildGesturePreviewItems enter folder=$folder sort=$sortOrder direction=$sortDirection notes=${dashboardNoteSummary(notes)} order=${dashboardPathSummary(customOrder)}",
    )
    val filtered =
        notes.filter {
            !it.isTrashed && it.folder == folder
        }
    val sorted =
        when (sortOrder) {
            PrefsManager.SortOrder.DATE_MODIFIED -> filtered.sortedBy { it.lastModified }
            PrefsManager.SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            PrefsManager.SortOrder.CUSTOM -> sortByCustomOrder(filtered, customOrder)
        }.let {
            if (sortOrder != PrefsManager.SortOrder.CUSTOM && sortDirection == PrefsManager.SortDirection.DESCENDING) {
                it.reversed()
            } else {
                it
            }
        }.sortedByDescending { it.isPinned }
    Log.d(
        DASHBOARD_CUSTOM_SORT_FLASH_TAG,
        "buildGesturePreviewItems sorted folder=$folder result=${dashboardNoteSummary(sorted)}",
    )

    val items = mutableListOf<DashboardUiItem>()
    val pinned = sorted.filter { it.isPinned && !it.isArchived }
    val others = sorted.filter { !it.isPinned && !it.isArchived }
    val archived = sorted.filter { it.isArchived }

    if (pinned.isNotEmpty()) {
        items += DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.PINNED)
        items += pinned.map { DashboardUiItem.NoteItem(it) }
    }
    if (pinned.isNotEmpty() && others.isNotEmpty()) {
        items += DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.OTHERS)
    }
    items += others.map { DashboardUiItem.NoteItem(it) }
    if (archived.isNotEmpty()) {
        items += DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.ARCHIVED)
        items += archived.map { DashboardUiItem.NoteItem(it) }
    }
    items += DashboardUiItem.SpacerItem
    return items
}

private fun sortByCustomOrder(
    notes: List<Note>,
    customOrder: List<String>,
): List<Note> {
    val normalizedOrder = customOrder
        .map(::normalizeDashboardNotePath)
        .filter { it.isNotBlank() }
        .distinct()
    val orderIndex = normalizedOrder
        .withIndex()
        .associate { it.value to it.index }
    Log.d(
        DASHBOARD_CUSTOM_SORT_TRACE_TAG,
        "sortByCustomOrder enter notes=${dashboardNoteSummary(notes)} order=${dashboardPathSummary(normalizedOrder)}",
    )
    if (orderIndex.isEmpty()) {
        val fallback = notes.sortedByDescending { it.lastModified.time }
        Log.d(
            DASHBOARD_CUSTOM_SORT_TRACE_TAG,
            "sortByCustomOrder fallback result=${dashboardNoteSummary(fallback)}",
        )
        return fallback
    }

    val sorted = notes.sortedWith(
        compareBy<Note> { orderIndex[normalizeDashboardNotePath(it.file.path)] ?: Int.MAX_VALUE }
            .thenByDescending { it.lastModified.time }
            .thenBy { it.title.lowercase() }
            .thenBy { normalizeDashboardNotePath(it.file.path) },
    )
    Log.d(
        DASHBOARD_CUSTOM_SORT_TRACE_TAG,
        "sortByCustomOrder result=${dashboardNoteSummary(sorted)}",
    )
    return sorted
}


private fun dashboardUiItemsFlashSummary(items: Collection<DashboardUiItem>, limit: Int = 5): String {
    val notePaths = items.mapNotNull { (it as? DashboardUiItem.NoteItem)?.note?.file?.path }
    val headerCount = items.count { it is DashboardUiItem.HeaderItem }
    val spacerCount = items.count { it is DashboardUiItem.SpacerItem }
    return "items=${items.size} notes=${dashboardPathSummary(notePaths, limit)} headers=$headerCount spacers=$spacerCount"
}

private fun dashboardPathSummary(paths: Collection<String>, limit: Int = 5): String {
    val normalized = paths.map(::normalizeDashboardNotePath)
    val suffix = if (normalized.size > limit) ", ..." else ""
    return "size=${normalized.size} head=${normalized.take(limit)}$suffix"
}

private fun dashboardNoteSummary(notes: Collection<Note>, limit: Int = 5): String =
    dashboardPathSummary(notes.map { it.file.path }, limit)

private fun normalizeDashboardNotePath(path: String): String =
    path.trim().replace("\\", "/").trim('/')
