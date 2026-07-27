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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.localizedText

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
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val panelHeightRatio = 2f / 3f
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
    var focusedParentPath by remember { mutableStateOf("") }
    var renameDialog by remember { mutableStateOf<FolderNavigationNameDialogState?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(focusedParentPath, normalizedLabels) {
        if (focusedParentPath.isNotBlank() && focusedParentPath !in normalizedLabels) {
            focusedParentPath = ""
        }
    }

    BackHandler(enabled = editMode || focusedParentPath.isNotBlank()) {
        if (editMode) {
            editMode = false
        } else {
            focusedParentPath = navigationParentFolderPath(focusedParentPath)
        }
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
            shape = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp),
            color = folderNavigationPalette().panel,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val haptic = LocalHapticFeedback.current
                val listState = rememberLazyListState()
                LaunchedEffect(focusedParentPath) {
                    listState.scrollToItem(0)
                }
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
                val dragItemBounds = remember { mutableMapOf<String, Rect>() }
                var draggingPath by remember { mutableStateOf<String?>(null) }
                var draggingParentPath by remember { mutableStateOf<String?>(null) }
                var dragTargetPath by remember { mutableStateOf<String?>(null) }
                var dragOffset by remember { mutableStateOf(Offset.Zero) }

                fun moveEditItemImmediately(fromPath: String, toPath: String) {
                    val fromIndex = orderedEditItems.indexOfFirst { it.path == fromPath }
                    val toIndex = orderedEditItems.indexOfFirst { it.path == toPath }
                    val fromItem = orderedEditItems.getOrNull(fromIndex) ?: return
                    val toItem = orderedEditItems.getOrNull(toIndex) ?: return
                    if (fromIndex == toIndex || fromItem.parentPath != toItem.parentPath) return
                    val fromOrigin = dragItemBounds[fromPath]?.topLeft
                    val targetOrigin = dragItemBounds[toPath]?.topLeft
                    moveFolderEditItemBlock(
                        items = orderedEditItems,
                        fromItem = fromItem,
                        toItem = toItem,
                        placeAfterTarget = fromIndex < toIndex,
                    )
                    if (fromOrigin != null && targetOrigin != null) {
                        dragOffset += fromOrigin - targetOrigin
                    }
                }

                fun saveDraggedOrder() {
                    val parentPath = draggingParentPath ?: return
                    onSaveFolderDisplayOrder(
                        parentPath,
                        orderedEditItems.filter { it.parentPath == parentPath }.map { it.path },
                    )
                }

                val displayedSections = if (editMode) {
                    buildFolderNavigationSectionsFromEditItems(
                        items = orderedEditItems,
                        parentPath = focusedParentPath,
                    )
                } else {
                    buildFolderNavigationSections(
                        labels = normalizedLabels,
                        folderNoteCounts = folderNoteCounts,
                        savedOrderFor = getFolderDisplayOrder,
                        parentPath = focusedParentPath,
                    )
                }
                val topRowTitle = if (focusedParentPath.isBlank()) {
                    localizedText("全部笔记", "All notes")
                } else {
                    focusedParentPath.substringAfterLast('/')
                }
                val topRowCount = if (focusedParentPath.isBlank()) {
                    allNotesCount
                } else {
                    folderNoteCounts[focusedParentPath] ?: 0
                }
                val topRowSelected = if (focusedParentPath.isBlank()) {
                    currentFilter is MainViewModel.NoteFilter.All
                } else {
                    currentPath == focusedParentPath
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item("navigation_top_${focusedParentPath}") {
                        FolderNavigationTopRow(
                            title = topRowTitle,
                            count = topRowCount,
                            selected = topRowSelected,
                            editMode = editMode,
                            showBack = focusedParentPath.isNotBlank(),
                            onClick = {
                                if (!editMode) {
                                    if (focusedParentPath.isBlank()) {
                                        onSelect(MainViewModel.NoteFilter.All)
                                    } else {
                                        onSelect(MainViewModel.NoteFilter.Label(focusedParentPath))
                                    }
                                }
                            },
                            onBack = {
                                focusedParentPath = navigationParentFolderPath(focusedParentPath)
                            },
                            onEditToggle = {
                                editMode = !editMode
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDismiss = onDismiss,
                        )
                    }
                    if (displayedSections.isEmpty()) {
                        item("empty_folders_${focusedParentPath}") {
                            Text(
                                text = "还没有下级分类",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                    lazyColumnItems(
                        items = displayedSections,
                        key = { it.path },
                    ) { section ->
                        FolderNavigationSectionView(
                            section = section,
                            selectedPath = currentPath,
                            editMode = editMode,
                            dragItemBounds = dragItemBounds,
                            draggingPath = draggingPath,
                            dragTargetPath = dragTargetPath,
                            dragOffset = dragOffset,
                            onDragStarted = { path ->
                                draggingPath = path
                                draggingParentPath = orderedEditItems.firstOrNull { it.path == path }?.parentPath
                                dragTargetPath = null
                                dragOffset = Offset.Zero
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancelled = {
                                draggingPath = null
                                draggingParentPath = null
                                dragTargetPath = null
                                dragOffset = Offset.Zero
                            },
                            onDragEnded = {
                                saveDraggedOrder()
                                draggingPath = null
                                draggingParentPath = null
                                dragTargetPath = null
                                dragOffset = Offset.Zero
                            },
                            onDragDelta = { path, delta ->
                                if (draggingPath == path) dragOffset += delta
                            },
                            onDragOver = { path, targetPath ->
                                dragTargetPath = targetPath
                                if (targetPath != null && targetPath != path) {
                                    moveEditItemImmediately(path, targetPath)
                                }
                            },
                            onSelectPath = { path -> onSelect(MainViewModel.NoteFilter.Label(path)) },
                            onRenamePath = ::openRenameDialog,
                            onExpandPath = { path ->
                                focusedParentPath = path
                            },
                        )
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

private data class FolderNavigationPalette(
    val panel: Color,
    val text: Color,
    val muted: Color,
    val allNotes: Color,
    val item: Color,
    val section: Color,
    val line: Color,
    val selectedLine: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val iconBackground: Color,
)

@Composable
private fun folderNavigationPalette(): FolderNavigationPalette {
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.background.luminance() > 0.5f
    return if (isLight) {
        FolderNavigationPalette(
            panel = Color(0xFFFFFFFF),
            text = Color(0xFF20242B),
            muted = Color(0xFF747B86),
            allNotes = Color(0xFFF7F9FB),
            item = Color(0xFFF3F5F8),
            section = Color(0xFFF8F9FB),
            line = Color(0xFFE7EAF0),
            selectedLine = Color(0xFFBDD6FF),
            accent = Color(0xFF357FF3),
            accentSoft = Color(0xFFE9F2FF),
            accentText = Color(0xFF1553A2),
            iconBackground = Color(0xFFFFFFFF),
        )
    } else {
        FolderNavigationPalette(
            panel = scheme.background,
            text = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            allNotes = scheme.surfaceVariant.copy(alpha = 0.34f),
            item = scheme.surfaceVariant.copy(alpha = 0.66f),
            section = scheme.surfaceVariant.copy(alpha = 0.34f),
            line = scheme.outlineVariant,
            selectedLine = scheme.primary.copy(alpha = 0.42f),
            accent = scheme.primary,
            accentSoft = scheme.primaryContainer,
            accentText = scheme.onPrimaryContainer,
            iconBackground = scheme.surface,
        )
    }
}

@Composable
private fun FolderNavigationTopRow(
    title: String,
    count: Int,
    selected: Boolean,
    editMode: Boolean,
    showBack: Boolean,
    onClick: () -> Unit,
    onBack: () -> Unit,
    onEditToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = folderNavigationPalette()
    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = if (selected) palette.accentSoft else palette.allNotes,
        contentColor = if (selected) palette.accentText else palette.text,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
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
                }
                .border(
                    width = 1.dp,
                    color = if (selected) palette.selectedLine else palette.line,
                    shape = shape,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = palette.iconBackground,
                    shadowElevation = 1.dp,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onBack,
                            ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "‹",
                            color = palette.accent,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                        .padding(start = if (showBack) 8.dp else 0.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(title)
                        append(" ")
                        withStyle(
                            SpanStyle(
                                color = if (selected) palette.accentText else palette.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) {
                            append(count.toString())
                        }
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onEditToggle,
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Text(
                    text = if (editMode) "完成" else "编辑",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = palette.accent,
                )
            }
        }
    }
}

@Composable
private fun FolderNavigationAddButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    val palette = folderNavigationPalette()
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = palette.item,
        modifier = Modifier.size(42.dp),
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
                color = palette.accent,
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
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(modifier = Modifier.width((item.depth * 18).dp))
        FolderNavigationChip(
            text = item.title,
            count = item.count,
            selected = selected,
            modifier = Modifier.weight(1f),
            onClick = onRename,
        )
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
    selectedPath: String,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    dragItemBounds: MutableMap<String, Rect>,
    draggingPath: String?,
    dragTargetPath: String?,
    dragOffset: Offset,
    onDragStarted: (String) -> Unit,
    onDragCancelled: () -> Unit,
    onDragEnded: () -> Unit,
    onDragDelta: (String, Offset) -> Unit,
    onDragOver: (String, String?) -> Unit,
    onSelectPath: (String) -> Unit,
    onRenamePath: (String) -> Unit,
    onExpandPath: (String) -> Unit,
) {
    val palette = folderNavigationPalette()
    val selected = section.path == selectedPath
    val shape = RoundedCornerShape(20.dp)
    Surface(
        shape = shape,
        color = palette.section,
        modifier =
            modifier
                .fillMaxWidth()
                .zIndex(
                    if (draggingPath == section.path) 1f else 0f,
                )
                .graphicsLayer {
                    when {
                        draggingPath == section.path -> {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 0.99f
                            scaleY = 0.99f
                        }
                        dragTargetPath == section.path -> {
                            scaleX = 1.01f
                            scaleY = 1.01f
                        }
                    }
                }
                .border(1.dp, palette.line, shape),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .folderNavigationDragTarget(
                                enabled = editMode,
                                path = section.path,
                                siblingPaths = section.siblingPaths,
                                itemBounds = dragItemBounds,
                                onDragStarted = onDragStarted,
                                onDragCancelled = onDragCancelled,
                                onDragEnded = onDragEnded,
                                onDragDelta = onDragDelta,
                                onDragOver = onDragOver,
                            )
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(5.dp)
                                .height(20.dp)
                                .background(
                                    color = if (selected) palette.accent else palette.line,
                                    shape = RoundedCornerShape(99.dp),
                                ),
                    )
                    Text(
                        text = buildAnnotatedString {
                            append(section.title)
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    color = palette.muted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            ) {
                                append(section.count.toString())
                            }
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = if (selected) palette.accentText else palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (section.chips.isNotEmpty()) {
                    TextButton(
                        onClick = { onExpandPath(section.path) },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    ) {
                        Text(
                            text = "展开 ›",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = palette.accent,
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(palette.line),
            )
            if (section.chips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FolderNavigationChipGrid(
                    items = section.chips,
                    selectedPath = selectedPath,
                    allSelected = false,
                    editMode = editMode,
                    dragItemBounds = dragItemBounds,
                    draggingPath = draggingPath,
                    dragTargetPath = dragTargetPath,
                    dragOffset = dragOffset,
                    onDragStarted = onDragStarted,
                    onDragCancelled = onDragCancelled,
                    onDragEnded = onDragEnded,
                    onDragDelta = onDragDelta,
                    onDragOver = onDragOver,
                    onSelectPath = onSelectPath,
                    onRenamePath = onRenamePath,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderNavigationChipGrid(
    items: List<FolderNavigationChipItem>,
    selectedPath: String?,
    allSelected: Boolean,
    editMode: Boolean,
    dragItemBounds: MutableMap<String, Rect>,
    draggingPath: String?,
    dragTargetPath: String?,
    dragOffset: Offset,
    onDragStarted: (String) -> Unit,
    onDragCancelled: () -> Unit,
    onDragEnded: () -> Unit,
    onDragDelta: (String, Offset) -> Unit,
    onDragOver: (String, String?) -> Unit,
    onSelectPath: (String) -> Unit,
    onRenamePath: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FolderNavigationChip(
                text = item.title,
                count = item.count,
                selected = allSelected || item.path == selectedPath,
                modifier =
                    Modifier
                        .zIndex(if (draggingPath == item.path) 1f else 0f)
                        .folderNavigationDragTarget(
                            enabled = editMode,
                            path = item.path,
                            siblingPaths = items.map { it.path },
                            itemBounds = dragItemBounds,
                            onDragStarted = onDragStarted,
                            onDragCancelled = onDragCancelled,
                            onDragEnded = onDragEnded,
                            onDragDelta = onDragDelta,
                            onDragOver = onDragOver,
                        )
                        .graphicsLayer {
                            when {
                                draggingPath == item.path -> {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 0.98f
                                    scaleY = 0.98f
                                }
                                dragTargetPath == item.path -> {
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            }
                        },
                onClick = {
                    if (editMode) {
                        onRenamePath(item.path)
                    } else {
                        onSelectPath(item.path)
                    }
                },
            )
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
    val palette = folderNavigationPalette()
    val shape = RoundedCornerShape(15.dp)
    Surface(
        shape = shape,
        color = if (selected) palette.accentSoft else palette.item,
        contentColor = if (selected) palette.accentText else palette.text,
        modifier =
            modifier
                .widthIn(max = 200.dp)
                .heightIn(min = 40.dp)
                .border(
                    width = 1.dp,
                    color = if (selected) palette.selectedLine else Color.Transparent,
                    shape = shape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        Text(
            text = buildAnnotatedString {
                append(text)
                append(" ")
                withStyle(
                    SpanStyle(
                        color = if (selected) palette.accentText else palette.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                ) {
                    append(count.toString())
                }
            },
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .widthIn(max = 180.dp)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

private fun Modifier.folderNavigationDragTarget(
    enabled: Boolean,
    path: String,
    siblingPaths: List<String>,
    itemBounds: MutableMap<String, Rect>,
    onDragStarted: (String) -> Unit,
    onDragCancelled: () -> Unit,
    onDragEnded: () -> Unit,
    onDragDelta: (String, Offset) -> Unit,
    onDragOver: (String, String?) -> Unit,
): Modifier {
    if (!enabled) return this
    return this
        .onGloballyPositioned { coordinates ->
            itemBounds[path] = coordinates.boundsInRoot()
        }
        .pointerInput(path, siblingPaths.sorted()) {
            var lastTargetPath: String? = null
            var pointerInRoot: Offset? = null
            detectDragGesturesAfterLongPress(
                onDragStart = { startPosition ->
                    lastTargetPath = null
                    pointerInRoot = itemBounds[path]?.topLeft?.plus(startPosition)
                    onDragStarted(path)
                },
                onDragCancel = {
                    lastTargetPath = null
                    pointerInRoot = null
                    onDragCancelled()
                },
                onDragEnd = {
                    lastTargetPath = null
                    pointerInRoot = null
                    onDragEnded()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDragDelta(path, dragAmount)
                    pointerInRoot = pointerInRoot?.plus(dragAmount)
                    val currentPointer = pointerInRoot
                    if (currentPointer != null) {
                        val targetPath = siblingPaths.firstOrNull { siblingPath ->
                            siblingPath != path && itemBounds[siblingPath]?.contains(currentPointer) == true
                        }
                        if (targetPath != lastTargetPath) {
                            lastTargetPath = targetPath
                            onDragOver(path, targetPath)
                        }
                    }
                },
            )
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
    val parentPath: String,
    val siblingPaths: List<String>,
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
    parentPath: String,
): List<FolderNavigationSection> {
    val siblings = panelDirectChildFolders(labels, parentPath, savedOrderFor)
    val siblingPaths = siblings.map { it.path }
    return siblings.map { folder ->
        val children = panelDirectChildFolders(labels, folder.path, savedOrderFor)
        FolderNavigationSection(
            title = folder.name,
            path = folder.path,
            parentPath = parentPath,
            siblingPaths = siblingPaths,
            count = folderNoteCounts[folder.path] ?: 0,
            chips = children.map { child ->
                FolderNavigationChipItem(
                    title = child.name,
                    path = child.path,
                    count = folderNoteCounts[child.path] ?: 0,
                )
            },
        )
    }
}

private fun buildFolderNavigationSectionsFromEditItems(
    items: List<FolderNavigationEditItem>,
    parentPath: String,
): List<FolderNavigationSection> {
    val childrenByParent = items.groupBy { it.parentPath }
    val siblings = childrenByParent[parentPath].orEmpty()
    val siblingPaths = siblings.map { it.path }
    return siblings.map { item ->
        val children = childrenByParent[item.path].orEmpty()
        FolderNavigationSection(
            title = item.title,
            path = item.path,
            parentPath = parentPath,
            siblingPaths = siblingPaths,
            count = item.count,
            chips = children.map { child ->
                FolderNavigationChipItem(
                    title = child.title,
                    path = child.path,
                    count = child.count,
                )
            },
        )
    }
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
