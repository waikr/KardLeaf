package com.kangle.kardleaf.ui

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.repository.PrefsManager

private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val MENU_REOPEN_GUARD_MS = 250L

@Composable
fun SearchBar(viewModel: MainViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            singleLine = true,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SortButton(viewModel: MainViewModel) {
    val sortOrder by viewModel.sortOrder.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val customSortDragModeEnabled by viewModel.customSortDragModeEnabled.collectAsState()
    val folderSortSettings by viewModel.currentFolderSortSettings.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var showCustomSortDialog by remember { mutableStateOf(false) }
    var lastSortMenuDismissAt by remember { mutableStateOf(0L) }
    val folderFilter = currentFilter as? MainViewModel.NoteFilter.Label
    val isAllNotesView = currentFilter is MainViewModel.NoteFilter.All
    val isFolderView = folderFilter != null
    val canCustomSort = isAllNotesView || (folderFilter != null && !folderFilter.recursive)
    val savedEffectiveSortOrder = folderSortSettings?.order ?: sortOrder
    val effectiveSortOrder =
        if (!canCustomSort && savedEffectiveSortOrder == PrefsManager.SortOrder.CUSTOM) {
            sortOrder
        } else {
            savedEffectiveSortOrder
        }
    val effectiveSortDirection = folderSortSettings?.direction ?: sortDirection
    val customSortNotes =
        when {
            isAllNotesView -> notes.filter { !it.isTrashed }
            canCustomSort && folderFilter != null -> notes.filter { !it.isTrashed && it.folder == folderFilter.name }
            else -> emptyList()
        }

    LaunchedEffect(showSortMenu) {
        Log.d(BACK_TRACE_TAG, "SortButton state changed showSortMenu=$showSortMenu")
    }

    Box {
        IconButton(onClick = {
            val now = SystemClock.uptimeMillis()
            val ignoreReopen = !showSortMenu && now - lastSortMenuDismissAt < MENU_REOPEN_GUARD_MS
            Log.d(BACK_TRACE_TAG, "SortButton click toggle menu isFolderView=$isFolderView filter=$currentFilter showSortMenu=$showSortMenu ignoreReopen=$ignoreReopen")
            if (!ignoreReopen) {
                showSortMenu = !showSortMenu
            }
        }) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = stringResource(R.string.sort_notes),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        DropdownMenu(
            modifier =
                Modifier.onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                        Log.d(
                            BACK_TRACE_TAG,
                            "SortButton popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showSortMenu=$showSortMenu",
                        )
                    }
                    false
                },
            expanded = showSortMenu,
            onDismissRequest = {
                Log.d(BACK_TRACE_TAG, "SortButton onDismissRequest showSortMenu=$showSortMenu")
                lastSortMenuDismissAt = SystemClock.uptimeMillis()
                showSortMenu = false
            },
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = true,
            ),
        ) {
            if (isFolderView) {
                DropdownMenuItem(
                    text = { Text("仅此目录单独排序") },
                    leadingIcon = {
                        Checkbox(
                            checked = folderSortSettings != null,
                            onCheckedChange = null,
                        )
                    },
                    onClick = {
                        viewModel.setCurrentFolderSortOverrideEnabled(folderSortSettings == null)
                    },
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.date)) },
                trailingIcon = { if (effectiveSortOrder == PrefsManager.SortOrder.DATE_MODIFIED) Icon(Icons.Default.Check, null) },
                onClick = {
                    viewModel.setSortOrder(PrefsManager.SortOrder.DATE_MODIFIED)
                    showSortMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_title)) },
                trailingIcon = { if (effectiveSortOrder == PrefsManager.SortOrder.TITLE) Icon(Icons.Default.Check, null) },
                onClick = {
                    viewModel.setSortOrder(PrefsManager.SortOrder.TITLE)
                    showSortMenu = false
                },
            )
            if (canCustomSort) {
                DropdownMenuItem(
                    text = { Text("自定义") },
                    trailingIcon = { if (effectiveSortOrder == PrefsManager.SortOrder.CUSTOM) Icon(Icons.Default.Check, null) },
                    onClick = {
                        viewModel.enableCurrentFolderCustomSort(customSortNotes.map { it.file.path })
                    },
                )
                if (effectiveSortOrder == PrefsManager.SortOrder.CUSTOM) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("简洁自定义顺序") },
                        onClick = {
                            viewModel.setCustomSortDragModeEnabled(false)
                            showSortMenu = false
                            showCustomSortDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (customSortDragModeEnabled) "关闭拖动自定义顺序" else "拖动自定义顺序") },
                        trailingIcon = { if (customSortDragModeEnabled) Icon(Icons.Default.Check, null) },
                        onClick = {
                            viewModel.enableCurrentFolderCustomSort(customSortNotes.map { it.file.path })
                            viewModel.setCustomSortDragModeEnabled(!customSortDragModeEnabled)
                            showSortMenu = false
                        },
                    )
                }
            }
            if (effectiveSortOrder != PrefsManager.SortOrder.CUSTOM) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_ascending)) },
                    trailingIcon = { if (effectiveSortDirection == PrefsManager.SortDirection.ASCENDING) Icon(Icons.Default.Check, null) },
                    onClick = {
                        viewModel.setSortDirection(PrefsManager.SortDirection.ASCENDING)
                        showSortMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_descending)) },
                    trailingIcon = { if (effectiveSortDirection == PrefsManager.SortDirection.DESCENDING) Icon(Icons.Default.Check, null) },
                    onClick = {
                        viewModel.setSortDirection(PrefsManager.SortDirection.DESCENDING)
                        showSortMenu = false
                    },
                )
            }
        }

        if (showCustomSortDialog && canCustomSort) {
            CustomSortDialog(
                folderName = folderFilter?.name ?: "全部笔记",
                notes = customSortNotes,
                onDismiss = { showCustomSortDialog = false },
                onSave = { paths ->
                    viewModel.saveCurrentFolderCustomSortOrder(paths)
                    showCustomSortDialog = false
                },
            )
        }

        BackHandler(enabled = showSortMenu) {
            Log.d(BACK_TRACE_TAG, "SortButton BackHandler hit, closing sort menu")
            showSortMenu = false
        }
    }
}
