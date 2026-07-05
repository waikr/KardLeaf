package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import com.kangle.kardleaf.data.utils.KardLeafPerfLog
import android.os.SystemClock
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyStaggeredGridState

internal const val DASHBOARD_CUSTOM_SORT_TRACE_TAG = "KardLeafCustomSort"
private const val DASHBOARD_CUSTOM_SORT_FLASH_TAG = "KardLeafCustomSortFlash"
private val USER_PERF_TRACE_TAG = KardLeafLogTags.USER_PERF
private val DASHBOARD_SCROLL_TRACE_TAG = KardLeafLogTags.DASHBOARD_SCROLL
private inline fun logDashboardCustomSortTrace(message: () -> String) {
    if (KardLeafLog.isEnabled(DASHBOARD_CUSTOM_SORT_TRACE_TAG)) {
        KardLeafLog.d(DASHBOARD_CUSTOM_SORT_TRACE_TAG, message())
    }
}

private inline fun logDashboardCustomSortFlash(message: () -> String) {
    if (KardLeafLog.isEnabled(DASHBOARD_CUSTOM_SORT_FLASH_TAG)) {
        KardLeafLog.d(DASHBOARD_CUSTOM_SORT_FLASH_TAG, message())
    }
}

private fun estimateDashboardScrollDeltaPx(
    listState: LazyStaggeredGridState,
    startIndex: Int,
    startOffset: Int,
    endIndex: Int,
    endOffset: Int,
): Int {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    val averageItemHeight = visibleItems
        .map { it.size.height }
        .filter { it > 0 }
        .average()
        .takeIf { !it.isNaN() && it > 0.0 }
        ?: 1.0
    val columnCount = visibleItems
        .map { it.offset.x }
        .distinct()
        .size
        .coerceAtLeast(1)
    val estimatedRowDelta = abs(endIndex - startIndex).toFloat() / columnCount
    val offsetDelta = abs(endOffset - startOffset)
    return (estimatedRowDelta * averageItemHeight + offsetDelta).roundToInt().coerceAtLeast(0)
}

private fun dashboardVisibleRangeSummary(listState: LazyStaggeredGridState): String {
    val visibleItems = listState.layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return "visible=empty"
    val first = visibleItems.minByOrNull { it.index }
    val last = visibleItems.maxByOrNull { it.index }
    val viewportHeight = listState.layoutInfo.viewportSize.height
    return "visible=${first?.index}-${last?.index} count=${visibleItems.size} viewportH=$viewportHeight " +
        "canBack=${listState.canScrollBackward} canForward=${listState.canScrollForward}"
}

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
    modifiedDateFormat: String,
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
    scrollPerfPath: String = "",
    scrollPerfEnabled: Boolean = true,
    onSearchJump: (Note) -> Unit = {},
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
                uiItems.fold(uiItems.size) { hash, item ->
                    val itemHash = when (item) {
                        is DashboardUiItem.NoteItem ->
                            31 * item.key.hashCode() +
                                17 * item.note.lastModified.time.hashCode() +
                                item.note.title.hashCode()
                        else -> item.key.hashCode()
                    }
                    31 * hash + itemHash
                }
            } else {
                0
            }
        }
        val reorderableItems = remember(customSortItemsKey, enableCustomSortDrag) {
            logDashboardCustomSortFlash {
                "NoteGrid create reorderableItems enable=$enableCustomSortDrag uiItems=${dashboardUiItemsFlashSummary(uiItems)} keyHash=${customSortItemsKey.hashCode()}"
            }
            mutableStateListOf<DashboardUiItem>().apply {
                if (enableCustomSortDrag) addAll(uiItems)
            }
        }
        val customSortDragMoved = remember(customSortItemsKey, enableCustomSortDrag) { mutableStateOf(false) }
        val displayedItems: List<DashboardUiItem> = if (enableCustomSortDrag) reorderableItems else uiItems
        LaunchedEffect(enableCustomSortDrag, customSortDragHandleEnabled, customSortItemsKey) {
            logDashboardCustomSortFlash {
                "NoteGrid render enable=$enableCustomSortDrag handle=$customSortDragHandleEnabled notesCount=$notesCount isLoading=$isLoading selected=${selectedNotes.size} " +
                    "viewMode=$viewMode searchBlank=${searchQuery.isBlank()} uiItems=${dashboardUiItemsFlashSummary(uiItems)} displayed=${dashboardUiItemsFlashSummary(displayedItems)} keyHash=${customSortItemsKey.hashCode()}"
            }
        }
        LaunchedEffect(listState, scrollPerfPath, scrollPerfEnabled, notesCount, displayedItems.size, viewMode, enableCustomSortDrag) {
            var scrollStartMs: Long? = null
            var frameJob: Job? = null
            var frameCount = 0
            var slowFrameCount = 0
            var maxFrameMs = 0L
            var startIndex = listState.firstVisibleItemIndex
            var startOffset = listState.firstVisibleItemScrollOffset

            snapshotFlow { listState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { scrolling ->
                    if (scrollPerfEnabled && scrolling && scrollStartMs == null) {
                        scrollStartMs = SystemClock.elapsedRealtime()
                        startIndex = listState.firstVisibleItemIndex
                        startOffset = listState.firstVisibleItemScrollOffset
                        frameCount = 0
                        slowFrameCount = 0
                        maxFrameMs = 0L
                        frameJob?.cancel()
                        frameJob = launch {
                            var previousFrameNanos = withFrameNanos { it }
                            while (true) {
                                val frameNanos = withFrameNanos { it }
                                val frameMs = (frameNanos - previousFrameNanos) / 1_000_000L
                                frameCount += 1
                                if (frameMs > 24L) slowFrameCount += 1
                                if (frameMs > maxFrameMs) maxFrameMs = frameMs
                                previousFrameNanos = frameNanos
                            }
                        }
                        KardLeafLog.d(
                            USER_PERF_TRACE_TAG,
                            "dashboardVerticalScroll humanStart path=$scrollPerfPath " +
                                "viewMode=$viewMode notes=$notesCount items=${displayedItems.size} " +
                                "firstIndex=$startIndex firstOffset=$startOffset",
                        )
                    } else if (!scrolling && scrollStartMs != null) {
                        val start = scrollStartMs ?: return@collect
                        frameJob?.cancel()
                        frameJob = null
                        withFrameNanos { _ -> }
                        val elapsed = SystemClock.elapsedRealtime() - start
                        val endIndex = listState.firstVisibleItemIndex
                        val endOffset = listState.firstVisibleItemScrollOffset
                        val moved = startIndex != endIndex || startOffset != endOffset
                        val deltaPx = estimateDashboardScrollDeltaPx(listState, startIndex, startOffset, endIndex, endOffset)
                        val averageFrameMs = KardLeafPerfLog.avgFrame(elapsed, frameCount)
                        val msPerPx = KardLeafPerfLog.msPerPx(elapsed, deltaPx)
                        val smooth = moved && slowFrameCount == 0 && maxFrameMs <= 32L
                        val stuckLike = moved && elapsed >= 300L && deltaPx in 1..120
                        val scrollSummary =
                            "dashboardVerticalScroll humanSettled elapsed=${elapsed}ms path=$scrollPerfPath " +
                                "viewMode=$viewMode notes=$notesCount items=${displayedItems.size} moved=$moved stuckLike=$stuckLike " +
                                "fromIndex=$startIndex toIndex=$endIndex fromOffset=$startOffset toOffset=$endOffset " +
                                "deltaPx=$deltaPx msPerPx=$msPerPx frames=$frameCount slowFrames=$slowFrameCount " +
                                "maxFrame=${maxFrameMs}ms avgFrame=${averageFrameMs}ms smooth=$smooth " +
                                dashboardVisibleRangeSummary(listState)
                        KardLeafLog.d(USER_PERF_TRACE_TAG, scrollSummary)
                        if (!smooth || stuckLike) {
                            KardLeafLog.d(DASHBOARD_SCROLL_TRACE_TAG, scrollSummary)
                        }
                        scrollStartMs = null
                    }
                }
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
            logDashboardCustomSortFlash {
                "NoteGrid move from=${from.index}:${normalizeDashboardNotePath(fromItem.note.file.path)} " +
                    "to=${to.index}:${normalizeDashboardNotePath(toItem.note.file.path)} before=${dashboardUiItemsFlashSummary(reorderableItems)}"
            }
            reorderableItems.add(to.index, reorderableItems.removeAt(from.index))
            logDashboardCustomSortFlash {
                "NoteGrid move after=${dashboardUiItemsFlashSummary(reorderableItems)}"
            }
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
                                    logDashboardCustomSortFlash {
                                        "NoteGrid dragStarted path=${normalizeDashboardNotePath(item.note.file.path)} order=${dashboardPathSummary(currentCustomSortPaths())}"
                                    }
                                    customSortDragMoved.value = false
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }

                                fun stopCustomSortDrag() {
                                    val currentPaths = currentCustomSortPaths()
                                    logDashboardCustomSortFlash {
                                        "NoteGrid dragStopped moved=${customSortDragMoved.value} path=${normalizeDashboardNotePath(item.note.file.path)} order=${dashboardPathSummary(currentPaths)}"
                                    }
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
                                                modifiedDateFormat = modifiedDateFormat,
                                                showDeletedDate = showDeletedDate,
                                                showNoteTitle = showNoteTitle,
                                                showDateFilenameTitle = showDateFilenameTitle,
                                                customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                                unnamedNoteDateFormat = unnamedNoteDateFormat,
                                                searchQuery = searchQuery,
                                                searchMatch = item.searchMatch,
                                                showImagePreview = viewMode == PrefsManager.ViewMode.LIST && cardDensity != PrefsManager.CardDensity.COMPACT && searchQuery.isBlank(),
                                                loadImageThumbnail = loadImageThumbnail,
                                                onSearchJump = { onSearchJump(item.note) },
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
                                            modifiedDateFormat = modifiedDateFormat,
                                            showDeletedDate = showDeletedDate,
                                            showNoteTitle = showNoteTitle,
                                            showDateFilenameTitle = showDateFilenameTitle,
                                            customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                            unnamedNoteDateFormat = unnamedNoteDateFormat,
                                            searchQuery = searchQuery,
                                            searchMatch = item.searchMatch,
                                            showImagePreview = viewMode == PrefsManager.ViewMode.LIST && cardDensity != PrefsManager.CardDensity.COMPACT && searchQuery.isBlank(),
                                            loadImageThumbnail = loadImageThumbnail,
                                            onSearchJump = { onSearchJump(item.note) },
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
                                modifiedDateFormat = modifiedDateFormat,
                                showDeletedDate = showDeletedDate,
                                showNoteTitle = showNoteTitle,
                                showDateFilenameTitle = showDateFilenameTitle,
                                customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                unnamedNoteDateFormat = unnamedNoteDateFormat,
                                searchQuery = searchQuery,
                                searchMatch = item.searchMatch,
                                showImagePreview = viewMode == PrefsManager.ViewMode.LIST && cardDensity != PrefsManager.CardDensity.COMPACT && searchQuery.isBlank(),
                                loadImageThumbnail = loadImageThumbnail,
                                onSearchJump = { onSearchJump(item.note) },
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
    logDashboardCustomSortFlash {
        "buildGesturePreviewItems enter folder=$folder sort=$sortOrder direction=$sortDirection notes=${dashboardNoteSummary(notes)} order=${dashboardPathSummary(customOrder)}"
    }
    val filtered =
        notes.filter {
            !it.isTrashed && it.folder == folder
        }
    return buildGesturePreviewItemsForFolderNotes(
        notes = filtered,
        folder = folder,
        sortOrder = sortOrder,
        sortDirection = sortDirection,
        customOrder = customOrder,
    )
}

internal fun buildGesturePreviewItemsForFolderNotes(
    notes: List<Note>,
    folder: String,
    sortOrder: PrefsManager.SortOrder,
    sortDirection: PrefsManager.SortDirection,
    customOrder: List<String> = emptyList(),
): List<DashboardUiItem> {
    val sorted =
        when (sortOrder) {
            PrefsManager.SortOrder.DATE_MODIFIED -> notes.sortedBy { it.lastModified }
            PrefsManager.SortOrder.TITLE -> notes.sortedBy { it.title.lowercase() }
            PrefsManager.SortOrder.CUSTOM -> sortByCustomOrder(notes, customOrder)
        }.let {
            if (sortOrder != PrefsManager.SortOrder.CUSTOM && sortDirection == PrefsManager.SortDirection.DESCENDING) {
                it.reversed()
            } else {
                it
            }
        }.sortedByDescending { it.isPinned }
    logDashboardCustomSortFlash {
        "buildGesturePreviewItems sorted folder=$folder result=${dashboardNoteSummary(sorted)}"
    }

    val items = mutableListOf<DashboardUiItem>()
    val pinned = mutableListOf<Note>()
    val others = mutableListOf<Note>()
    val archived = mutableListOf<Note>()
    sorted.forEach { note ->
        when {
            note.isArchived -> archived += note
            note.isPinned -> pinned += note
            else -> others += note
        }
    }

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
    logDashboardCustomSortTrace {
        "sortByCustomOrder enter notes=${dashboardNoteSummary(notes)} order=${dashboardPathSummary(normalizedOrder)}"
    }
    if (orderIndex.isEmpty()) {
        val fallback = notes.sortedByDescending { it.lastModified.time }
        logDashboardCustomSortTrace {
            "sortByCustomOrder fallback result=${dashboardNoteSummary(fallback)}"
        }
        return fallback
    }

    val sorted = notes.sortedWith(
        compareBy<Note> { orderIndex[normalizeDashboardNotePath(it.file.path)] ?: Int.MAX_VALUE }
            .thenByDescending { it.lastModified.time }
            .thenBy { it.title.lowercase() }
            .thenBy { normalizeDashboardNotePath(it.file.path) },
    )
    logDashboardCustomSortTrace {
        "sortByCustomOrder result=${dashboardNoteSummary(sorted)}"
    }
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
