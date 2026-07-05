package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.utils.KardLeafLog
import android.os.SystemClock
import android.widget.Toast
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle

private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val MENU_REOPEN_GUARD_MS = 250L
private const val MOVE_DESTINATION_FAVORITES_PREFS = "kardleaf_move_destination_favorites"
private const val KEY_MOVE_DESTINATION_FAVORITE_PATHS = "move_destination_favorite_paths"
private const val ROOT_DESTINATION_PATH = ""

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
        PrefsManager.SelectionToolbarItemId.FAVORITE -> Icons.Outlined.StarBorder
        PrefsManager.SelectionToolbarItemId.TAG -> Icons.Outlined.Sell
        PrefsManager.SelectionToolbarItemId.ARCHIVE -> Icons.Outlined.Inventory2
        PrefsManager.SelectionToolbarItemId.PROPERTIES -> Icons.Outlined.Info
        PrefsManager.SelectionToolbarItemId.SHARE -> Icons.Outlined.Share
        PrefsManager.SelectionToolbarItemId.PRIVACY -> Icons.Outlined.Shield
        PrefsManager.SelectionToolbarItemId.DELETE -> Icons.Outlined.DeleteOutline
    }


private data class MoveDestinationTreeItem(
    val path: String,
    val title: String,
    val depth: Int,
)

private fun buildMoveDestinationTreeItems(labels: List<String>): List<MoveDestinationTreeItem> {
    val paths = linkedSetOf<String>()
    labels.asSequence()
        .map { it.trim().trim('/') }
        .filter { it.isNotBlank() }
        .forEach { label ->
            var current = ""
            label.split('/')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .forEach { part ->
                    current = if (current.isBlank()) part else "$current/$part"
                    paths += current
                }
        }
    return paths.map { path ->
        MoveDestinationTreeItem(
            path = path,
            title = path.substringAfterLast('/'),
            depth = path.count { it == '/' },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoveNotesBottomSheet(
    availableLabels: List<String>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val rootFolderTitle = stringResource(R.string.root_folder)
    val favoritePrefs = remember(context) {
        context.getSharedPreferences(MOVE_DESTINATION_FAVORITES_PREFS, android.content.Context.MODE_PRIVATE)
    }
    val destinationItems = remember(availableLabels) { buildMoveDestinationTreeItems(availableLabels) }
    val destinationPathSet = remember(destinationItems) {
        destinationItems.mapTo(mutableSetOf()) { it.path }.apply { add(ROOT_DESTINATION_PATH) }
    }
    val destinationItemByPath = remember(destinationItems) { destinationItems.associateBy { it.path } }
    val defaultFavoritePaths = remember(destinationItems) {
        (listOf(ROOT_DESTINATION_PATH) + destinationItems.take(6).map { it.path }).distinct()
    }
    var favoritePaths by remember(availableLabels) {
        val stored = favoritePrefs.getString(KEY_MOVE_DESTINATION_FAVORITE_PATHS, null)
        val loaded = when {
            stored == null -> null
            stored.isBlank() -> emptyList()
            else -> stored.split('\n')
                .map { it.trim().trim('/') }
                .filter { it == ROOT_DESTINATION_PATH || it.isNotBlank() }
                .distinct()
        }
        val initial = (loaded ?: defaultFavoritePaths)
            .filter { it in destinationPathSet }
            .take(12)
        mutableStateOf(initial)
    }

    fun saveFavoritePaths(paths: List<String>) {
        val cleaned = paths
            .map { it.trim().trim('/') }
            .filter { it in destinationPathSet }
            .distinct()
            .take(12)
        favoritePaths = cleaned
        favoritePrefs.edit()
            .putString(KEY_MOVE_DESTINATION_FAVORITE_PATHS, cleaned.joinToString("\n"))
            .apply()
    }

    fun destinationDisplayName(path: String): String =
        if (path == ROOT_DESTINATION_PATH) {
            rootFolderTitle
        } else {
            destinationItemByPath[path]?.title ?: path.substringAfterLast('/').ifBlank { path }
        }

    fun addFavoritePath(path: String) {
        if (path !in favoritePaths) {
            saveFavoritePaths(favoritePaths + path)
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            Toast.makeText(context, "已收藏：${destinationDisplayName(path)}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "已在收藏栏：${destinationDisplayName(path)}", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeFavoritePath(path: String) {
        saveFavoritePaths(favoritePaths.filterNot { it == path })
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.67f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Text(
                text = "收藏栏",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (favoritePaths.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(favoritePaths, key = { if (it == ROOT_DESTINATION_PATH) "favorite-root" else "favorite-$it" }) { path ->
                        MoveFavoriteChip(
                            title = destinationDisplayName(path),
                            icon = if (path == ROOT_DESTINATION_PATH) Icons.Outlined.Home else Icons.Outlined.BookmarkBorder,
                            onClick = { onMove(path) },
                            onLongClick = { removeFavoritePath(path) },
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

            Text(
                text = "选择目录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "root") {
                    MoveDestinationRow(
                        title = rootFolderTitle,
                        subtitle = "笔记库根目录",
                        icon = Icons.Outlined.Home,
                        depth = 0,
                        isFavorite = ROOT_DESTINATION_PATH in favoritePaths,
                        onClick = { onMove(ROOT_DESTINATION_PATH) },
                        onLongClick = { addFavoritePath(ROOT_DESTINATION_PATH) },
                    )
                }
                if (destinationItems.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "还没有目录",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                } else {
                    items(destinationItems, key = { it.path }) { item ->
                        MoveDestinationRow(
                            title = item.title,
                            subtitle = item.path,
                            icon = Icons.Outlined.Folder,
                            depth = item.depth,
                            isFavorite = item.path in favoritePaths,
                            onClick = { onMove(item.path) },
                            onLongClick = { addFavoritePath(item.path) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoveFavoriteChip(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoveDestinationRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    depth: Int = 0,
    isFavorite: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (depth > 0) {
                Spacer(modifier = Modifier.width((depth * 18).dp))
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isFavorite) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = "已收藏",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
    selectionToolbarItemOrder: List<PrefsManager.SelectionToolbarItemId> = PrefsManager.SelectionToolbarItemId.DEFAULT_ORDER,
    selectionToolbarMoreItems: Set<PrefsManager.SelectionToolbarItemId> = PrefsManager.SelectionToolbarItemId.DEFAULT_MORE_ITEMS,
    selectionToolbarHiddenItems: Set<PrefsManager.SelectionToolbarItemId> = PrefsManager.SelectionToolbarItemId.DEFAULT_HIDDEN_ITEMS,
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
    var showTagDialog by remember { mutableStateOf(false) }
    var tagText by remember { mutableStateOf("") }

    LaunchedEffect(showMoveMenu, showMoreMenu) {
        KardLeafLog.d(BACK_TRACE_TAG, "SelectionTopAppBar state changed showMoveMenu=$showMoveMenu showMoreMenu=$showMoreMenu")
    }

    BackHandler(enabled = showMoreMenu || showMoveMenu) {
        KardLeafLog.d(BACK_TRACE_TAG, "SelectionTopAppBar BackHandler hit showMoreMenu=$showMoreMenu showMoveMenu=$showMoveMenu")
        showMoreMenu = false
        showMoveMenu = false
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
                        val isModern = LocalKardLeafThemeStyle.current != PrefsManager.AppThemeStyle.CLASSIC
                        val chipShape = RoundedCornerShape(if (isModern) 999.dp else 8.dp)
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
                                    shape = chipShape,
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (isModern) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        labelColor = if (isModern) {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isModern) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
                                        },
                                    ),
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

    if (showMoveMenu) {
        MoveNotesBottomSheet(
            availableLabels = availableLabels,
            onDismiss = {
                KardLeafLog.d(BACK_TRACE_TAG, "SelectionTopAppBar move sheet onDismiss showMoveMenu=$showMoveMenu")
                lastMoveMenuDismissAt = SystemClock.uptimeMillis()
                showMoveMenu = false
            },
            onMove = { targetLabel ->
                onMove(targetLabel)
                lastMoveMenuDismissAt = SystemClock.uptimeMillis()
                showMoveMenu = false
            },
        )
    }

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
                        if (allSelectedFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
                    else -> selectionToolbarActionIcon(item)
                }

            fun performAction(item: PrefsManager.SelectionToolbarItemId) {
                when (item) {
                    PrefsManager.SelectionToolbarItemId.MOVE -> {
                        val now = SystemClock.uptimeMillis()
                        val ignoreReopen = !showMoveMenu && now - lastMoveMenuDismissAt < MENU_REOPEN_GUARD_MS
                        KardLeafLog.d(BACK_TRACE_TAG, "SelectionTopAppBar move click toggle menu showMoveMenu=$showMoveMenu showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
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
                if (showIcon) {
                    IconButton(onClick = { performAction(PrefsManager.SelectionToolbarItemId.MOVE) }) {
                        Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = stringResource(R.string.move))
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
            val visibleOrder = normalizedOrder.filter { it !in selectionToolbarHiddenItems }
            val topItems = visibleOrder.filter { it !in selectionToolbarMoreItems && isActionAvailable(it) }
            val moreItems = visibleOrder.filter { it in selectionToolbarMoreItems && isActionAvailable(it) }

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

            if (moreItems.isNotEmpty()) {
                Box {
                    IconButton(onClick = {
                        val now = SystemClock.uptimeMillis()
                        val ignoreReopen = !showMoreMenu && now - lastMoreMenuDismissAt < MENU_REOPEN_GUARD_MS
                        KardLeafLog.d(BACK_TRACE_TAG, "SelectionTopAppBar more click toggle menu showMoveMenu=$showMoveMenu showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                        if (!ignoreReopen) {
                            showMoveMenu = false
                            showMoreMenu = !showMoreMenu
                        }
                    }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    KardLeafDropdownMenu(
                        modifier =
                            Modifier.onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                    KardLeafLog.d(
                                        BACK_TRACE_TAG,
                                        "SelectionTopAppBar more popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoreMenu=$showMoreMenu",
                                    )
                                }
                                false
                            },
                        expanded = showMoreMenu,
                        onDismissRequest = {
                            KardLeafLog.d(BACK_TRACE_TAG, "SelectionTopAppBar more onDismissRequest showMoreMenu=$showMoreMenu")
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
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
    )
}
