package com.kangle.kardleaf.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager

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
    searchQuery: String,
    listState: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
    loadImageThumbnail: suspend (Note) -> android.graphics.Bitmap? = { null },
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

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = listState,
            contentPadding = PaddingValues(0.dp),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier.fillMaxSize(),
        ) {
            items(
                items = uiItems,
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
                        NoteCard(
                            note = item.note,
                            isSelected = selectedNotes.contains(item.note.file.path),
                            cardDensity = cardDensity,
                            showFolderTag = showFolderTags,
                            showYamlTags = showYamlTags,
                            searchQuery = searchQuery,
                            searchMatch = item.searchMatch,
                            showImagePreview = viewMode == PrefsManager.ViewMode.LIST && cardDensity != PrefsManager.CardDensity.COMPACT && searchQuery.isBlank(),
                            loadImageThumbnail = loadImageThumbnail,
                            onClick = { onNoteClick(item.note) },
                            onLongClick = { onNoteLongClick(item.note) },
                        )
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
): List<DashboardUiItem> {
    val filtered =
        notes.filter {
            !it.isTrashed && it.folder == folder
        }
    val sorted =
        when (sortOrder) {
            PrefsManager.SortOrder.DATE_MODIFIED -> filtered.sortedBy { it.lastModified }
            PrefsManager.SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        }.let {
            if (sortDirection == PrefsManager.SortDirection.DESCENDING) it.reversed() else it
        }.sortedByDescending { it.isPinned }

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
