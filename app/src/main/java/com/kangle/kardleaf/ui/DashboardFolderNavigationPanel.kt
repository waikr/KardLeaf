package com.kangle.kardleaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.Note
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun FolderNavigationPanel(
    labels: List<String>,
    notes: List<Note>,
    currentFilter: MainViewModel.NoteFilter,
    dragProgress: Float = 1f,
    folderOrderVersion: Int,
    getFolderDisplayOrder: (String) -> List<String>,
    onSaveFolderDisplayOrder: (String, List<String>) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String, (String) -> Unit) -> Unit,
    onDeleteFolder: (String, () -> Unit, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (MainViewModel.NoteFilter) -> Unit,
) {
    val normalizedLabels = remember(labels) {
        labels
            .map(::normalizeFolderPathForUi)
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val currentPath = (currentFilter as? MainViewModel.NoteFilter.Label)?.name
        ?.let(::normalizeFolderPathForUi)
        .orEmpty()
    val activeNotes = remember(notes) { notes.filterNot { it.isArchived || it.isTrashed } }
    val allNotesCount = activeNotes.size
    val folderNoteCounts = remember(normalizedLabels, activeNotes) {
        buildFolderRecursiveNoteCounts(
            labels = normalizedLabels,
            activeNotes = activeNotes,
        )
    }
    val folderSections = remember(normalizedLabels, folderNoteCounts, folderOrderVersion) {
        buildFolderNavigationSections(
            labels = normalizedLabels,
            folderNoteCounts = folderNoteCounts,
            savedOrderFor = getFolderDisplayOrder,
        )
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val panelHeightRatio = 0.58f
    val fallbackPanelHeightPx = remember(configuration, density) {
        with(density) { (configuration.screenHeightDp.dp * panelHeightRatio).toPx() }
    }
    val targetProgress = dragProgress.coerceIn(0f, 1f)
    val panelProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(
            durationMillis = if (targetProgress == 0f || targetProgress >= 1f) KardLeafMotion.ContainerDurationMillis else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "FolderNavigationPanelProgress",
    )
    var panelHeightPx by remember { mutableStateOf(0) }
    var editMode by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf<FolderNavigationNameDialogState?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val chipColumns = if (configuration.screenWidthDp >= 600) 4 else 3

    fun openRenameDialog(path: String) {
        renameDialog = FolderNavigationNameDialogState(
            title = "重命名目录",
            confirmText = "保存",
            initialName = path.substringAfterLast('/'),
            onConfirm = { name ->
                val newPath = navigationJoinFolderPath(
                    navigationParentFolderPath(path),
                    name,
                )
                when {
                    newPath == path -> Unit
                    newPath in normalizedLabels -> errorMessage = "已存在同名目录"
                    else -> onRenameFolder(
                        path,
                        newPath,
                        { message ->
                            errorMessage = message.ifBlank { "重命名失败" }
                        },
                    )
                }
            },
        )
    }

    fun openCreateDialog(parentPath: String) {
        renameDialog = FolderNavigationNameDialogState(
            title = if (parentPath.isBlank()) "新建二级目录" else "新建三级目录",
            confirmText = "创建",
            initialName = "",
            onConfirm = { name ->
                val newPath = navigationJoinFolderPath(parentPath, name)
                when {
                    newPath.isBlank() -> Unit
                    newPath in normalizedLabels -> errorMessage = "已存在同名目录"
                    else -> onCreateFolder(newPath)
                }
            },
        )
    }

    BackHandler(enabled = editMode) {
        editMode = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f * panelProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
        )
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(panelHeightRatio)
                    .align(Alignment.TopCenter)
                    .onSizeChanged { panelHeightPx = it.height }
                    .graphicsLayer {
                        val measuredHeight = if (panelHeightPx > 0) panelHeightPx.toFloat() else fallbackPanelHeightPx
                        translationY = -measuredHeight * (1f - panelProgress)
                    },
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val haptic = LocalHapticFeedback.current
                FolderNavigationHeader(
                    editMode = editMode,
                    onDismiss = onDismiss,
                    onEditToggle = {
                        editMode = !editMode
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                )

                val listState = rememberLazyListState()
                val editItems = remember(normalizedLabels, folderNoteCounts, folderOrderVersion) {
                    buildFolderNavigationEditItems(
                        labels = normalizedLabels,
                        folderNoteCounts = folderNoteCounts,
                        savedOrderFor = getFolderDisplayOrder,
                    )
                }
                val orderedEditItems = remember { mutableStateListOf<FolderNavigationEditItem>() }
                val editItemsKey = remember(editItems) { editItems.joinToString("|") { it.path } }
                LaunchedEffect(editItemsKey) {
                    orderedEditItems.clear()
                    orderedEditItems.addAll(editItems)
                }
                val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                    val fromIndex = from.index - 1
                    val toIndex = to.index - 1
                    val fromItem = orderedEditItems.getOrNull(fromIndex)
                        ?: return@rememberReorderableLazyListState
                    val toItem = orderedEditItems.getOrNull(toIndex)
                        ?: return@rememberReorderableLazyListState
                    if (fromIndex == toIndex || fromItem.parentPath != toItem.parentPath) {
                        return@rememberReorderableLazyListState
                    }
                    moveFolderEditItemBlock(
                        items = orderedEditItems,
                        fromItem = fromItem,
                        toItem = toItem,
                        placeAfterTarget = fromIndex < toIndex,
                    )
                    onSaveFolderDisplayOrder(
                        fromItem.parentPath,
                        orderedEditItems.filter { it.parentPath == fromItem.parentPath }.map { it.path },
                    )
                }
                val displayedSections = folderSections

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("all_notes") {
                        FolderNavigationAllNotesRow(
                            count = allNotesCount,
                            selected = currentFilter is MainViewModel.NoteFilter.All,
                            columns = chipColumns,
                            editMode = editMode,
                            onClick = {
                                if (!editMode) {
                                    onSelect(MainViewModel.NoteFilter.All)
                                }
                            },
                            onCreateChild = { openCreateDialog("") },
                        )
                    }
                    if (displayedSections.isEmpty()) {
                        item("empty_folders") {
                            Text(
                                text = "还没有文件夹",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                    if (editMode) {
                        lazyColumnItems(
                            items = orderedEditItems,
                            key = { it.path },
                        ) { item ->
                            ReorderableItem(
                                state = reorderableState,
                                key = item.path,
                            ) { _ ->
                                FolderNavigationEditRow(
                                    item = item,
                                    selected = item.path == currentPath,
                                    showAddChild = item.depth == 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                        ),
                                    onRename = { openRenameDialog(item.path) },
                                    onCreateChild = { openCreateDialog(item.path) },
                                )
                            }
                        }
                    } else {
                        lazyColumnItems(
                            items = displayedSections,
                            key = { it.path },
                        ) { section ->
                            FolderNavigationSectionView(
                                section = section,
                                columns = chipColumns,
                                selectedPath = currentPath,
                                onSelectPath = { path -> onSelect(MainViewModel.NoteFilter.Label(path)) },
                                onRenamePath = {},
                            )
                        }
                    }
                }
            }
        }
    }

    renameDialog?.let { dialogState ->
        FolderNavigationNameDialog(
            state = dialogState,
            onDismiss = { renameDialog = null },
            onConfirm = { name ->
                dialogState.onConfirm(name)
                renameDialog = null
            },
        )
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("操作失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("知道了")
                }
            },
        )
    }
}

@Composable
private fun FolderNavigationHeader(
    editMode: Boolean,
    onDismiss: () -> Unit,
    onEditToggle: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(onDismiss) {
                    val triggerDistancePx = 24.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var pointerPressed = true
                        while (pointerPressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            pointerPressed = change?.pressed == true
                            if (change != null && pointerPressed) {
                                val dx = kotlin.math.abs(change.position.x - down.position.x)
                                val dy = change.position.y - down.position.y
                                if (dy < -triggerDistancePx && kotlin.math.abs(dy) > dx * 1.2f) {
                                    change.consume()
                                    onDismiss()
                                    pointerPressed = false
                                }
                            }
                        }
                    }
                },
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (editMode) 0.92f else 0.62f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp),
        ) {
            Text(
                text = if (editMode) "编辑中：点击目录重命名，长按拖动排序" else "分类导航",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        TextButton(
            onClick = onEditToggle,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp),
        ) {
            Text(if (editMode) "完成" else "编辑")
        }
    }
}

@Composable
private fun FolderNavigationAllNotesRow(
    count: Int,
    selected: Boolean,
    columns: Int,
    editMode: Boolean,
    onClick: () -> Unit,
    onCreateChild: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = 8.dp
        val chipWidth = (maxWidth - spacing * (columns - 1).toFloat()) / columns.toFloat()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolderNavigationChip(
                text = "全部笔记",
                count = count,
                selected = selected,
                modifier = Modifier.width(chipWidth),
                onClick = onClick,
            )
            if (editMode) {
                FolderNavigationAddButton(
                    contentDescription = "新建二级目录",
                    onClick = onCreateChild,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FolderNavigationAddButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
        modifier = Modifier.size(34.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { this.contentDescription = contentDescription }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FolderNavigationEditRow(
    item: FolderNavigationEditItem,
    selected: Boolean,
    showAddChild: Boolean,
    modifier: Modifier = Modifier,
    onRename: () -> Unit,
    onCreateChild: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(modifier = Modifier.width((item.depth * 18).dp))
        Surface(
            shape = shape,
            color = if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f) else MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 36.dp)
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
                    shape = shape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onRename,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "(${item.count})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
        if (showAddChild) {
            FolderNavigationAddButton(
                contentDescription = "新建三级目录",
                onClick = onCreateChild,
            )
        }
    }
}

@Composable
private fun FolderNavigationSectionView(
    section: FolderNavigationSection,
    columns: Int,
    selectedPath: String,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onSelectPath: (String) -> Unit,
    onRenamePath: (String) -> Unit,
) {
    val selected = section.path == selectedPath
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (editMode) {
                            onRenamePath(section.path)
                        } else {
                            onSelectPath(section.path)
                        }
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "(${section.count})",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        FolderNavigationChipGrid(
            items = section.chips,
            columns = columns,
            selectedPath = selectedPath,
            allSelected = false,
            editMode = editMode,
            onSelectPath = onSelectPath,
            onRenamePath = onRenamePath,
        )
    }
}

@Composable
private fun FolderNavigationChipGrid(
    items: List<FolderNavigationChipItem>,
    columns: Int,
    selectedPath: String?,
    allSelected: Boolean,
    editMode: Boolean,
    onSelectPath: (String) -> Unit,
    onRenamePath: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    FolderNavigationChip(
                        text = item.title,
                        count = item.count,
                        selected = allSelected || item.path == selectedPath,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (editMode) {
                                onRenamePath(item.path)
                            } else {
                                onSelectPath(item.path)
                            }
                        },
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FolderNavigationChip(
    text: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color =
            if (selected) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        modifier =
            modifier
                .heightIn(min = 34.dp)
                .border(
                    width = if (selected) 1.dp else 0.dp,
                    color = if (selected) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
                    shape = shape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "($count)",
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FolderNavigationNameDialog(
    state: FolderNavigationNameDialogState,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(state.initialName) { mutableStateOf(state.initialName) }
    val trimmed = name.trim().trim('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(state.title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("目录名称") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotBlank() && !trimmed.contains('/'),
                onClick = { onConfirm(trimmed) },
            ) {
                Text(state.confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private data class FolderNavigationSection(
    val title: String,
    val path: String,
    val count: Int,
    val chips: List<FolderNavigationChipItem>,
)

private data class FolderNavigationEditItem(
    val title: String,
    val path: String,
    val parentPath: String,
    val depth: Int,
    val count: Int,
)

private data class FolderNavigationChipItem(
    val title: String,
    val path: String,
    val count: Int,
)

private data class FolderNavigationNameDialogState(
    val title: String,
    val confirmText: String,
    val initialName: String,
    val onConfirm: (String) -> Unit,
)

private fun buildFolderNavigationEditItems(
    labels: List<String>,
    folderNoteCounts: Map<String, Int>,
    savedOrderFor: (String) -> List<String>,
): List<FolderNavigationEditItem> {
    val items = mutableListOf<FolderNavigationEditItem>()

    fun addChildren(parent: String, depth: Int) {
        panelDirectChildFolders(labels, parent, savedOrderFor).forEach { folder ->
            items += FolderNavigationEditItem(
                title = folder.name,
                path = folder.path,
                parentPath = parent,
                depth = depth,
                count = folderNoteCounts[folder.path] ?: 0,
            )
            addChildren(folder.path, depth + 1)
        }
    }

    addChildren(parent = "", depth = 0)
    return items
}

private fun moveFolderEditItemBlock(
    items: MutableList<FolderNavigationEditItem>,
    fromItem: FolderNavigationEditItem,
    toItem: FolderNavigationEditItem,
    placeAfterTarget: Boolean,
) {
    val movingPrefix = "${fromItem.path}/"
    val movingBlock = items.filter { it.path == fromItem.path || it.path.startsWith(movingPrefix) }
    if (movingBlock.isEmpty()) return

    val remaining = items.filterNot { it.path == fromItem.path || it.path.startsWith(movingPrefix) }
    val targetIndex = remaining.indexOfFirst { it.path == toItem.path }
    if (targetIndex < 0) return

    val insertIndex = if (placeAfterTarget) {
        val targetPrefix = "${toItem.path}/"
        remaining.indexOfLast { it.path == toItem.path || it.path.startsWith(targetPrefix) } + 1
    } else {
        targetIndex
    }
    val reordered = remaining.take(insertIndex) + movingBlock + remaining.drop(insertIndex)
    items.clear()
    items.addAll(reordered)
}

private fun buildFolderNavigationSections(
    labels: List<String>,
    folderNoteCounts: Map<String, Int>,
    savedOrderFor: (String) -> List<String>,
): List<FolderNavigationSection> {
    val sections = mutableListOf<FolderNavigationSection>()

    fun addSection(folder: FolderChipData) {
        val children = panelDirectChildFolders(labels, folder.path, savedOrderFor)
        sections += FolderNavigationSection(
            title = folder.name,
            path = folder.path,
            count = folderNoteCounts[folder.path] ?: 0,
            chips = children.map { child ->
                FolderNavigationChipItem(
                    title = child.name,
                    path = child.path,
                    count = folderNoteCounts[child.path] ?: 0,
                )
            },
        )
        children.forEach { child ->
            if (panelDirectChildFolders(labels, child.path, savedOrderFor).isNotEmpty()) {
                addSection(child)
            }
        }
    }

    panelDirectChildFolders(labels, parent = "", savedOrderFor).forEach(::addSection)
    return sections
}

private fun buildFolderRecursiveNoteCounts(
    labels: List<String>,
    activeNotes: List<Note>,
): Map<String, Int> {
    val labelSet = labels.toSet()
    val counts = mutableMapOf<String, Int>()

    activeNotes.forEach { note ->
        var folder = normalizeFolderPathForUi(note.folder)
        while (folder.isNotBlank()) {
            if (folder in labelSet) {
                counts[folder] = (counts[folder] ?: 0) + 1
            }
            folder = navigationParentFolderPath(folder)
        }
    }

    return counts
}

private fun panelDirectChildFolders(
    labels: List<String>,
    parent: String,
    savedOrderFor: (String) -> List<String>,
): List<FolderChipData> {
    val orderIndex = savedOrderFor(parent).withIndex().associate { it.value to it.index }
    return navigationDirectChildFolderPaths(labels, parent)
        .sortedWith(
            compareBy<String> { orderIndex[it] ?: Int.MAX_VALUE }
                .thenBy { it.substringAfterLast('/').lowercase() },
        )
        .map { path ->
            FolderChipData(
                name = path.substringAfterLast('/'),
                path = path,
            )
        }
}

private fun navigationDirectChildFolderPaths(
    labels: List<String>,
    parent: String,
): List<String> {
    val prefix = parent.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
    return labels
        .asSequence()
        .filter { it.startsWith(prefix) && it != parent }
        .map { it.removePrefix(prefix) }
        .filter { it.isNotBlank() && !it.contains('/') }
        .map { child -> navigationJoinFolderPath(parent, child) }
        .distinct()
        .toList()
}

private fun navigationParentFolderPath(path: String): String =
    path.substringBeforeLast('/', missingDelimiterValue = "")

private fun navigationJoinFolderPath(
    parent: String,
    name: String,
): String =
    listOf(parent.trim().trim('/'), name.trim().trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")
