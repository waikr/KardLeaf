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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note

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
    selectedNoteForProperties: Note? = null,
    selectedNotesForTags: List<Note> = emptyList(),
    availableYamlTags: List<String> = emptyList(),
    onApplyTags: (List<String>) -> Unit = {},
    onShowProperties: (Note) -> Unit = {},
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
            if (isTrash || allSelectedArchived) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.restore))
                }
            }

            if (!isTrash) {
                IconButton(onClick = {
                    val now = SystemClock.uptimeMillis()
                    val ignoreReopen = !showMoveMenu && now - lastMoveMenuDismissAt < MENU_REOPEN_GUARD_MS
                    Log.d(BACK_TRACE_TAG, "SelectionTopAppBar move click toggle menu showMoveMenu=$showMoveMenu showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                    if (!ignoreReopen) {
                        showMoreMenu = false
                        showMoveMenu = !showMoveMenu
                    }
                }) {
                    Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = stringResource(R.string.move))
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
                        focusable = true,
                        dismissOnBackPress = true,
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

            if (!isTrash && allSelectedActive) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Outlined.PushPin, contentDescription = stringResource(R.string.pin_unpin))
                }
            }

            if (!isTrash) {
                IconButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (allSelectedFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "收藏/取消收藏",
                    )
                }
            }

            if (!isTrash && selectedNotesForTags.isNotEmpty()) {
                IconButton(onClick = {
                    tagText = ""
                    showTagDialog = true
                }) {
                    Icon(Icons.Outlined.Label, contentDescription = "添加标签")
                }
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
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                    ),
                ) {
                    if (!isTrash && allSelectedActive) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.archive)) },
                            leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                            onClick = {
                                onArchive()
                                showMoreMenu = false
                            },
                        )
                    }
                    if (selectedNoteForProperties != null) {
                        DropdownMenuItem(
                            text = { Text("属性") },
                            leadingIcon = { Icon(Icons.Outlined.Info, null) },
                            onClick = {
                                onShowProperties(selectedNoteForProperties)
                                showMoreMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("分享") },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = {
                            onShare()
                            showMoreMenu = false
                        },
                    )
                    if (!isTrash && allSelectedActive) {
                        DropdownMenuItem(
                            text = { Text("添加到隐私库") },
                            leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                            onClick = {
                                onMoveToPrivacy()
                                showMoreMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = {
                            showDeleteDialog = true
                            showMoreMenu = false
                        },
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    )
}
