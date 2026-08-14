package com.kangle.kardleaf.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.NoteSearchOptions
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.localizedText
import android.view.KeyEvent as AndroidKeyEvent

private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val MENU_REOPEN_GUARD_MS = 250L

@Composable
fun SearchBar(
    viewModel: MainViewModel,
    requestFocus: Boolean = true,
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (!requestFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        withFrameNanos { }
        keyboardController?.show()
    }

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
                    .focusRequester(focusRequester)
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
fun SearchFilterToolbar(viewModel: MainViewModel) {
    val query by viewModel.searchQuery.collectAsState()
    val options by viewModel.searchOptions.collectAsState()
    val tags by viewModel.yamlTags.collectAsState()
    val folders by viewModel.labels.collectAsState()
    var showTagMenu by remember { mutableStateOf(false) }
    var showFolderMenu by remember { mutableStateOf(false) }
    val invalidRegex = options.useRegex && query.isNotBlank() && runCatching { Regex(query) }.isFailure

    Surface(
        modifier = Modifier.fillMaxWidth().height(52.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier =
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = options.matchCase,
                onClick = { viewModel.setSearchMatchCase(!options.matchCase) },
                label = { Text("Aa") },
                leadingIcon = { Icon(Icons.Outlined.TextFields, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = options.useRegex,
                onClick = { viewModel.setSearchUseRegex(!options.useRegex) },
                label = { Text(".*") },
                leadingIcon = { Icon(Icons.Outlined.Code, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = options.matchTitle,
                onClick = { viewModel.setSearchMatchTitle(!options.matchTitle) },
                label = { Text(localizedText("标题", "Title")) },
                leadingIcon = { Icon(Icons.Outlined.Title, null, Modifier.size(18.dp)) },
            )
            FilterChip(
                selected = options.matchContent,
                onClick = { viewModel.setSearchMatchContent(!options.matchContent) },
                label = { Text(localizedText("正文", "Body")) },
                leadingIcon = { Icon(Icons.Outlined.Article, null, Modifier.size(18.dp)) },
            )
            Box {
                FilterChip(
                    modifier = Modifier.widthIn(max = 160.dp),
                    selected = options.tag != null,
                    onClick = { showTagMenu = true },
                    label = {
                        Text(
                            options.tag?.let { "#$it" } ?: localizedText("标签", "Tag"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Label, null, Modifier.size(18.dp)) },
                )
                KardLeafDropdownMenu(
                    expanded = showTagMenu,
                    onDismissRequest = { showTagMenu = false },
                    properties = PopupProperties(focusable = false),
                ) {
                    DropdownMenuItem(
                        text = { Text(localizedText("全部标签", "All tags")) },
                        trailingIcon = { if (options.tag == null) Icon(Icons.Default.Check, null) },
                        onClick = {
                            viewModel.setSearchTag(null)
                            showTagMenu = false
                        },
                    )
                    tags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text("#$tag", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { if (options.tag == tag) Icon(Icons.Default.Check, null) },
                            onClick = {
                                viewModel.setSearchTag(tag)
                                showTagMenu = false
                            },
                        )
                    }
                }
            }
            Box {
                FilterChip(
                    modifier = Modifier.widthIn(max = 180.dp),
                    selected = options.folder != null,
                    onClick = { showFolderMenu = true },
                    label = {
                        Text(
                            options.folder?.ifBlank { localizedText("根目录", "Root") }
                                ?: localizedText("文件夹", "Folder"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) },
                )
                KardLeafDropdownMenu(
                    expanded = showFolderMenu,
                    onDismissRequest = { showFolderMenu = false },
                    properties = PopupProperties(focusable = false),
                ) {
                    DropdownMenuItem(
                        text = { Text(localizedText("全部文件夹", "All folders")) },
                        trailingIcon = { if (options.folder == null) Icon(Icons.Default.Check, null) },
                        onClick = {
                            viewModel.setSearchFolder(null)
                            showFolderMenu = false
                        },
                    )
                    folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { if (options.folder == folder) Icon(Icons.Default.Check, null) },
                            onClick = {
                                viewModel.setSearchFolder(folder)
                                showFolderMenu = false
                            },
                        )
                    }
                }
            }
            if (options != NoteSearchOptions()) {
                TextButton(onClick = viewModel::resetSearchFilters) {
                    Text(localizedText("重置", "Reset"))
                }
            }
            if (invalidRegex) {
                Text(
                    text = localizedText("正则无效", "Invalid regex"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
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
    var showApplyGlobalCustomDialog by remember { mutableStateOf(false) }
    var lastSortMenuDismissAt by remember { mutableStateOf(0L) }
    val folderFilter = currentFilter as? MainViewModel.NoteFilter.Label
    val isAllNotesView = currentFilter is MainViewModel.NoteFilter.All
    val isFolderView = folderFilter != null
    val canCustomSort = isAllNotesView || (folderFilter != null && !folderFilter.recursive)
    val savedEffectiveSortOrder = folderSortSettings?.order ?: sortOrder
    val effectiveSortOrder =
        if (!canCustomSort && savedEffectiveSortOrder == PrefsManager.SortOrder.CUSTOM) {
            PrefsManager.SortOrder.DATE_MODIFIED
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
        KardLeafLog.d(BACK_TRACE_TAG, "SortButton state changed showSortMenu=$showSortMenu")
    }

    Box {
        IconButton(onClick = {
            val now = SystemClock.uptimeMillis()
            val ignoreReopen = !showSortMenu && now - lastSortMenuDismissAt < MENU_REOPEN_GUARD_MS
            KardLeafLog.d(BACK_TRACE_TAG, "SortButton click toggle menu isFolderView=$isFolderView filter=$currentFilter showSortMenu=$showSortMenu ignoreReopen=$ignoreReopen")
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

        KardLeafDropdownMenu(
            modifier =
                Modifier
                    .width(176.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                            KardLeafLog.d(
                                BACK_TRACE_TAG,
                                "SortButton popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showSortMenu=$showSortMenu",
                            )
                        }
                        false
                    },
            expanded = showSortMenu,
            onDismissRequest = {
                KardLeafLog.d(BACK_TRACE_TAG, "SortButton onDismissRequest showSortMenu=$showSortMenu")
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
                    text = { Text("单独排序") },
                    trailingIcon = {
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
                        text = { Text("简洁调整") },
                        trailingIcon = { if (!customSortDragModeEnabled) Icon(Icons.Default.Check, null) },
                        onClick = {
                            viewModel.setCustomSortDragModeEnabled(false)
                            showSortMenu = false
                            showCustomSortDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("首页调整") },
                        trailingIcon = { if (customSortDragModeEnabled) Icon(Icons.Default.Check, null) },
                        onClick = {
                            viewModel.enableCurrentFolderCustomSort(customSortNotes.map { it.file.path })
                            viewModel.setCustomSortDragModeEnabled(!customSortDragModeEnabled)
                            showSortMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("全局应用") },
                        trailingIcon = { if (sortOrder == PrefsManager.SortOrder.CUSTOM) Icon(Icons.Default.Check, null) },
                        onClick = {
                            showSortMenu = false
                            showApplyGlobalCustomDialog = true
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
                folderName = folderFilter?.name ?: localizedText("全部笔记", "All notes"),
                notes = customSortNotes,
                onDismiss = { showCustomSortDialog = false },
                onSave = { paths ->
                    viewModel.saveCurrentFolderCustomSortOrder(paths)
                    showCustomSortDialog = false
                },
            )
        }

        if (showApplyGlobalCustomDialog) {
            AlertDialog(
                onDismissRequest = { showApplyGlobalCustomDialog = false },
                title = { Text("全局应用") },
                text = { Text("确定要在全局都使用自定义排序吗？已开启“单独排序”的目录会保持自己的排序设置。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.applyCustomSortGlobally()
                            showApplyGlobalCustomDialog = false
                        },
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showApplyGlobalCustomDialog = false }) {
                        Text("取消")
                    }
                },
            )
        }

        BackHandler(enabled = showSortMenu) {
            KardLeafLog.d(BACK_TRACE_TAG, "SortButton BackHandler hit, closing sort menu")
            showSortMenu = false
        }
    }
}
