package com.kangle.kardleaf.ui

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager

private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val MENU_REOPEN_GUARD_MS = 250L

private fun appendYamlTagInput(current: String, tag: String): String {
    val normalized = tag.trim().removePrefix("#").trim()
    if (normalized.isBlank()) return current
    val existingTags = current.split(',', '，')
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotBlank() }
    if (existingTags.any { it.equals(normalized, ignoreCase = true) }) return current
    return if (current.trim().isBlank()) {
        normalized
    } else {
        current.trimEnd().trimEnd(',', '，') + "，" + normalized
    }
}

private fun selectionToolbarActionLabel(item: PrefsManager.SelectionToolbarItemId): String =
    when (item) {
        PrefsManager.SelectionToolbarItemId.MOVE -> "移动"
        PrefsManager.SelectionToolbarItemId.COPY -> "复制"
        PrefsManager.SelectionToolbarItemId.PIN -> "置顶/取消置顶"
        PrefsManager.SelectionToolbarItemId.FAVORITE -> "收藏/取消收藏"
        PrefsManager.SelectionToolbarItemId.TAG -> "添加标签"
        PrefsManager.SelectionToolbarItemId.ARCHIVE -> "归档"
        PrefsManager.SelectionToolbarItemId.PROPERTIES -> "属性"
        PrefsManager.SelectionToolbarItemId.SHARE -> "分享"
        PrefsManager.SelectionToolbarItemId.PRIVACY -> "保护"
        PrefsManager.SelectionToolbarItemId.DELETE -> "删除"
    }

private fun selectionToolbarActionIcon(item: PrefsManager.SelectionToolbarItemId): ImageVector =
    when (item) {
        PrefsManager.SelectionToolbarItemId.MOVE -> Icons.AutoMirrored.Outlined.DriveFileMove
        PrefsManager.SelectionToolbarItemId.COPY -> Icons.Outlined.ContentCopy
        PrefsManager.SelectionToolbarItemId.PIN -> Icons.Outlined.PushPin
        PrefsManager.SelectionToolbarItemId.FAVORITE -> Icons.Outlined.FavoriteBorder
        PrefsManager.SelectionToolbarItemId.TAG -> Icons.Outlined.Label
        PrefsManager.SelectionToolbarItemId.ARCHIVE -> Icons.Outlined.Archive
        PrefsManager.SelectionToolbarItemId.PROPERTIES -> Icons.Outlined.Info
        PrefsManager.SelectionToolbarItemId.SHARE -> Icons.Outlined.Share
        PrefsManager.SelectionToolbarItemId.PRIVACY -> Icons.Outlined.Lock
        PrefsManager.SelectionToolbarItemId.DELETE -> Icons.Outlined.Delete
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SelectionTopAppBar(
    selectionCount: Int,
    currentFilter: MainViewModel.NoteFilter,
    allSelectedArchived: Boolean,
    allSelectedActive: Boolean,
    allSelectedFavorite: Boolean,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onMove: (String) -> Unit,
    onPin: () -> Unit,
    onFavorite: () -> Unit,
    availableLabels: List<String>,
    selectionToolbarItemOrder: List<PrefsManager.SelectionToolbarItemId> = PrefsManager.SelectionToolbarItemId.DEFAULT_ORDER,
    selectionToolbarMoreItems: Set<PrefsManager.SelectionToolbarItemId> = PrefsManager.SelectionToolbarItemId.DEFAULT_MORE_ITEMS,
    selectedNoteForProperties: Note? = null,
    selectedNotesForTags: List<Note> = emptyList(),
    availableYamlTags: List<String> = emptyList(),
    onApplyTags: (List<String>) -> Unit = {},
    onShowProperties: (Note) -> Unit = {},
    onDuplicate: () -> Unit = {},
    onShare: () -> Unit = {},
    onMoveToPrivacy: () -> Unit = {},
) {
    var showMoveMenu by remember { mutableStateOf(false) }
    var lastMoveMenuDismissAt by remember { mutableStateOf(0L) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var lastMoreMenuDismissAt by remember { mutableStateOf(0L) }
    var showCreateLabelDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var tagText by remember { mutableStateOf("") }

    LaunchedEffect(showMoveMenu, showMoreMenu) {
        Log.d(BACK_TRACE_TAG, "SelectionTopAppBar state changed showMoveMenu=$showMoveMenu showMoreMenu=$showMoreMenu")
    }

    BackHandler(enabled = showMoreMenu || showMoveMenu) {
        Log.d(BACK_TRACE_TAG, "SelectionTopAppBar BackHandler hit showMoreMenu=$showMoreMenu showMoveMenu=$showMoveMenu")
        showMoreMenu = false
        showMoveMenu = false
    }
    if (showCreateLabelDialog) {
        CreateLabelDialog(
            onDismiss = { showCreateLabelDialog = false },
            onConfirm = { name ->
                onMove(name)
                showCreateLabelDialog = false
            },
        )
    }

    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("添加标签") },
            text = {
                val currentTags = selectedNotesForTags.flatMap { it.tags }
                    .distinctBy { it.lowercase() }
                val suggestedTags = (currentTags + availableYamlTags)
                    .map { it.trim().removePrefix("#").trim() }
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .take(24)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = tagText,
                        onValueChange = { tagText = it },
                        label = { Text("标签，多个用逗号分隔") },
                        singleLine = false,
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (suggestedTags.isNotEmpty()) {
                        Text(
                            text = "已有标签，点击快速添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            suggestedTags.forEach { tag ->
                                AssistChip(
                                    onClick = { tagText = appendYamlTagInput(tagText, tag) },
                                    label = { Text("#$tag") },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val tags = tagText.split(',', '，')
                        .map { it.trim().removePrefix("#").trim() }
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                    onApplyTags(tags)
                    showTagDialog = false
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) {
                    Text("取消")
                }
            },
        )
    }

    val isTrash = currentFilter is MainViewModel.NoteFilter.Trash

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_selected_notes_title)) },
            text = { Text(stringResource(R.string.delete_selected_notes_message, selectionCount)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    TopAppBar(
        title = {
            Text(
                text = "$selectionCount",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_selection))
            }
        },
        actions = {
            fun isActionAvailable(item: PrefsManager.SelectionToolbarItemId): Boolean =
                when (item) {
                    PrefsManager.SelectionToolbarItemId.MOVE -> !isTrash
                    PrefsManager.SelectionToolbarItemId.COPY -> !isTrash && allSelectedActive
                    PrefsManager.SelectionToolbarItemId.PIN -> !isTrash && allSelectedActive
                    PrefsManager.SelectionToolbarItemId.FAVORITE -> !isTrash
                    PrefsManager.SelectionToolbarItemId.TAG -> !isTrash && selectedNotesForTags.isNotEmpty()
                    PrefsManager.SelectionToolbarItemId.ARCHIVE -> !isTrash && allSelectedActive
                    PrefsManager.SelectionToolbarItemId.PROPERTIES -> selectedNoteForProperties != null
                    PrefsManager.SelectionToolbarItemId.SHARE -> true
                    PrefsManager.SelectionToolbarItemId.PRIVACY -> !isTrash && allSelectedActive
                    PrefsManager.SelectionToolbarItemId.DELETE -> true
                }

            fun actionIcon(item: PrefsManager.SelectionToolbarItemId): ImageVector =
                when (item) {
                    PrefsManager.SelectionToolbarItemId.FAVORITE ->
                        if (allSelectedFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder
                    else -> selectionToolbarActionIcon(item)
                }

            fun performAction(item: PrefsManager.SelectionToolbarItemId) {
                when (item) {
                    PrefsManager.SelectionToolbarItemId.MOVE -> {
                        val now = SystemClock.uptimeMillis()
                        val ignoreReopen = !showMoveMenu && now - lastMoveMenuDismissAt < MENU_REOPEN_GUARD_MS
                        Log.d(BACK_TRACE_TAG, "SelectionTopAppBar move click toggle menu showMoveMenu=$showMoveMenu showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                        if (!ignoreReopen) {
                            showMoreMenu = false
                            showMoveMenu = !showMoveMenu
                        }
                    }
                    PrefsManager.SelectionToolbarItemId.COPY -> onDuplicate()
                    PrefsManager.SelectionToolbarItemId.PIN -> onPin()
                    PrefsManager.SelectionToolbarItemId.FAVORITE -> onFavorite()
                    PrefsManager.SelectionToolbarItemId.TAG -> {
                        tagText = ""
                        showTagDialog = true
                    }
                    PrefsManager.SelectionToolbarItemId.ARCHIVE -> onArchive()
                    PrefsManager.SelectionToolbarItemId.PROPERTIES -> selectedNoteForProperties?.let(onShowProperties)
                    PrefsManager.SelectionToolbarItemId.SHARE -> onShare()
                    PrefsManager.SelectionToolbarItemId.PRIVACY -> onMoveToPrivacy()
                    PrefsManager.SelectionToolbarItemId.DELETE -> showDeleteDialog = true
                }
            }

            @Composable
            fun MoveActionAnchor(showIcon: Boolean) {
                Box {
                    if (showIcon) {
                        IconButton(onClick = { performAction(PrefsManager.SelectionToolbarItemId.MOVE) }) {
                            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = stringResource(R.string.move))
                        }
                    }
                    DropdownMenu(
                        modifier =
                            Modifier.onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                    Log.d(
                                        BACK_TRACE_TAG,
                                        "SelectionTopAppBar move popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoveMenu=$showMoveMenu",
                                    )
                                }
                                false
                            },
                        expanded = showMoveMenu,
                        onDismissRequest = {
                            Log.d(BACK_TRACE_TAG, "SelectionTopAppBar move onDismissRequest showMoveMenu=$showMoveMenu")
                            lastMoveMenuDismissAt = SystemClock.uptimeMillis()
                            showMoveMenu = false
                        },
                        properties = PopupProperties(
                            focusable = false,
                            dismissOnBackPress = false,
                            dismissOnClickOutside = true,
                        ),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.root_folder)) },
                            onClick = {
                                onMove("")
                                showMoveMenu = false
                            },
                        )
                        availableLabels.forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onMove(label)
                                    showMoveMenu = false
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.create_new_label)) },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                showMoveMenu = false
                                showCreateLabelDialog = true
                            },
                        )
                    }
                }
            }

            @Composable
            fun ToolbarIconAction(item: PrefsManager.SelectionToolbarItemId) {
                if (item == PrefsManager.SelectionToolbarItemId.MOVE) {
                    MoveActionAnchor(showIcon = true)
                } else {
                    IconButton(onClick = { performAction(item) }) {
                        Icon(actionIcon(item), contentDescription = selectionToolbarActionLabel(item))
                    }
                }
            }

            val normalizedOrder = selectionToolbarItemOrder.distinct().toMutableList().also { order ->
                PrefsManager.SelectionToolbarItemId.DEFAULT_ORDER.forEach { if (it !in order) order.add(it) }
            }
            val topItems = normalizedOrder.filter { it !in selectionToolbarMoreItems && isActionAvailable(it) }
            val moreItems = normalizedOrder.filter { it in selectionToolbarMoreItems && isActionAvailable(it) }

            if (isTrash || allSelectedArchived) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.restore))
                }
            }

            topItems.forEach { item ->
                ToolbarIconAction(item)
            }
            if (PrefsManager.SelectionToolbarItemId.MOVE in moreItems) {
                MoveActionAnchor(showIcon = false)
            }

            Box {
                IconButton(onClick = {
                    val now = SystemClock.uptimeMillis()
                    val ignoreReopen = !showMoreMenu && now - lastMoreMenuDismissAt < MENU_REOPEN_GUARD_MS
                    Log.d(BACK_TRACE_TAG, "SelectionTopAppBar more click toggle menu showMoveMenu=$showMoveMenu showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                    if (!ignoreReopen) {
                        showMoveMenu = false
                        showMoreMenu = !showMoreMenu
                    }
                }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(
                    modifier =
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                Log.d(
                                    BACK_TRACE_TAG,
                                    "SelectionTopAppBar more popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoreMenu=$showMoreMenu",
                                )
                            }
                            false
                        },
                    expanded = showMoreMenu,
                    onDismissRequest = {
                        Log.d(BACK_TRACE_TAG, "SelectionTopAppBar more onDismissRequest showMoreMenu=$showMoreMenu")
                        lastMoreMenuDismissAt = SystemClock.uptimeMillis()
                        showMoreMenu = false
                    },
                    properties = PopupProperties(
                        focusable = false,
                        dismissOnBackPress = false,
                        dismissOnClickOutside = true,
                    ),
                ) {
                    moreItems.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(selectionToolbarActionLabel(item)) },
                            leadingIcon = { Icon(actionIcon(item), null) },
                            onClick = {
                                showMoreMenu = false
                                performAction(item)
                            },
                        )
                    }
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    )
}
