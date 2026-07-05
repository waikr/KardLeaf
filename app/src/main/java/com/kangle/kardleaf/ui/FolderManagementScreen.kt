package com.kangle.kardleaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.data.model.Note
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderManagementScreen(
    viewModel: MainViewModel,
    isDrawerOpen: Boolean,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
) {
    val labels by viewModel.labels.collectAsState()
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    val orderVersion by viewModel.folderManagerOrderVersion.collectAsState()
    var currentPath by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FolderManageItem?>(null) }
    var moveTarget by remember { mutableStateOf<FolderManageItem?>(null) }
    var deleteTarget by remember { mutableStateOf<FolderManageItem?>(null) }
    var sortTargets by remember { mutableStateOf<List<FolderManageItem>?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var expandedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    val snackbarHostState = remember { SnackbarHostState() }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    fun showToast(message: String) {
        toastMessage = message
    }

    LaunchedEffect(toastMessage) {
        val message = toastMessage ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = message,
            duration = SnackbarDuration.Short,
        )
        toastMessage = null
    }

    LaunchedEffect(labels, currentPath) {
        if (currentPath.isNotBlank() && currentPath !in labels) {
            currentPath = parentFolderPath(currentPath)
        }
        expandedFolderPaths = expandedFolderPaths.filterTo(mutableSetOf()) { it in labels }
    }

    val currentFolderSavedOrder = remember(currentPath, orderVersion) {
        viewModel.getFolderDisplayOrder(currentPath)
    }
    val childFolders = remember(labels, notes, currentPath, currentFolderSavedOrder, orderVersion) {
        buildManageItems(
            labels = labels,
            notes = notes,
            parentPath = currentPath,
            savedOrder = currentFolderSavedOrder,
        )
    }
    val visibleFolders = remember(labels, notes, currentPath, expandedFolderPaths, orderVersion) {
        buildManageTreeItems(
            labels = labels,
            notes = notes,
            parentPath = currentPath,
            expandedPaths = expandedFolderPaths,
            savedOrderFor = viewModel::getFolderDisplayOrder,
        )
    }
    val isCustomFolderOrder = currentFolderSavedOrder.isNotEmpty()

    BackHandler(enabled = !isDrawerOpen) {
        if (currentPath.isNotBlank()) {
            currentPath = parentFolderPath(currentPath)
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("分类管理")
                        Text(
                            text = currentPath.ifBlank { "根目录" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentPath.isNotBlank()) {
                                currentPath = parentFolderPath(currentPath)
                            } else {
                                onOpenDrawer()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (currentPath.isBlank()) Icons.Default.Menu else Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = if (currentPath.isBlank()) "打开侧边栏" else "返回上级",
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = "排序")
                        }
                        KardLeafDropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("标题") },
                                trailingIcon = {
                                    if (!isCustomFolderOrder) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    viewModel.saveFolderDisplayOrder(currentPath, emptyList())
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("自定义") },
                                enabled = childFolders.size > 1,
                                trailingIcon = {
                                    if (isCustomFolderOrder) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    showSortMenu = false
                                    sortTargets = childFolders
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "新建文件夹")
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            if (childFolders.isEmpty()) {
                EmptyFolderManagerContent(
                    modifier = Modifier.weight(1f),
                    currentPath = currentPath,
                    onCreateFolder = { showCreateDialog = true },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleFolders, key = { it.path }) { folder ->
                        FolderManageRow(
                            folder = folder,
                            isExpanded = folder.path in expandedFolderPaths,
                            onOpen = { currentPath = folder.path },
                            onToggleExpandAll = {
                                val descendants = descendantFolderPaths(labels, folder.path).toSet()
                                expandedFolderPaths = if (folder.path in expandedFolderPaths) {
                                    expandedFolderPaths - descendants - folder.path
                                } else {
                                    expandedFolderPaths + descendants + folder.path
                                }
                            },
                            onRename = { renameTarget = folder },
                            onMove = { moveTarget = folder },
                            onDelete = { deleteTarget = folder },
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        FolderNameDialog(
            title = "新建文件夹",
            confirmText = "创建",
            initialName = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                val targetPath = joinFolderPath(currentPath, name)
                if (targetPath in labels) {
                    showToast("已存在同名文件夹")
                } else {
                    viewModel.createLabel(targetPath)
                }
                showCreateDialog = false
            },
        )
    }

    renameTarget?.let { folder ->
        FolderNameDialog(
            title = "重命名文件夹",
            confirmText = "保存",
            initialName = folder.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                val newPath = joinFolderPath(parentFolderPath(folder.path), newName)
                when {
                    newPath == folder.path -> Unit
                    newPath in labels -> showToast("已存在同名文件夹")
                    else -> viewModel.renameLabel(
                        oldPath = folder.path,
                        newPath = newPath,
                        onError = { showToast("重命名失败") },
                    )
                }
                renameTarget = null
            },
        )
    }

    moveTarget?.let { folder ->
        MoveFolderDialog(
            folder = folder,
            labels = labels,
            onDismiss = { moveTarget = null },
            onMove = { targetParent ->
                val newPath = joinFolderPath(targetParent, folder.name)
                when {
                    newPath == folder.path -> Unit
                    newPath in labels -> showToast("目标位置已存在同名文件夹")
                    else -> viewModel.renameLabel(
                        oldPath = folder.path,
                        newPath = newPath,
                        onError = { showToast("移动失败") },
                    )
                }
                moveTarget = null
            },
        )
    }

    deleteTarget?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件夹") },
            text = { Text("将删除“${folder.name}”及其中的笔记和子文件夹。确定继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLabelWithContents(
                            name = folder.path,
                            onSuccess = { showToast("已删除文件夹") },
                            onError = { showToast("删除文件夹失败") },
                        )
                        deleteTarget = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            },
        )
    }

    sortTargets?.let { folders ->
        FolderSortDialog(
            parentPath = currentPath,
            folders = folders,
            onDismiss = { sortTargets = null },
            onSave = { orderedPaths ->
                viewModel.saveFolderDisplayOrder(currentPath, orderedPaths)
                sortTargets = null
            },
        )
    }
}

@Composable
private fun EmptyFolderManagerContent(
    modifier: Modifier,
    currentPath: String,
    onCreateFolder: () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (currentPath.isBlank()) "根目录还没有文件夹" else "当前文件夹下没有子文件夹",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "点击新建文件夹开始整理目录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onCreateFolder) {
                Text("新建文件夹")
            }
        }
    }
}

@Composable
private fun FolderManageRow(
    folder: FolderManageItem,
    isExpanded: Boolean,
    onOpen: () -> Unit,
    onToggleExpandAll: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = (14 + folder.depth * 20).dp, top = 12.dp, end = 2.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${folder.noteCount} 条笔记 · ${folder.childCount} 个子文件夹",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onToggleExpandAll,
                enabled = folder.childCount > 0,
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.KeyboardArrowDown else Icons.Outlined.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "收起子文件夹" else "展开子文件夹",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "文件夹操作")
                }
                KardLeafDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("移动") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onMove()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除整个文件夹") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    confirmText: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = name.trim().trim('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("文件夹名称") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotBlank() && !trimmed.contains('/'),
                onClick = { onConfirm(trimmed) },
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun MoveFolderDialog(
    folder: FolderManageItem,
    labels: List<String>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    var selectedParent by remember(folder.path) { mutableStateOf(parentFolderPath(folder.path)) }
    val availableParents = remember(labels, folder.path) {
        listOf("") + labels.filter { path ->
            path != folder.path && !path.startsWith("${folder.path}/")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动文件夹") },
        text = {
            Column {
                Text(
                    text = folder.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    availableParents.forEach { parent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParent = parent }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Folder,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(22.dp),
                                tint = if (selectedParent == parent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = parent.ifBlank { "根目录" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selectedParent == parent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(selectedParent) }) {
                Text("移动")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun FolderSortDialog(
    parentPath: String,
    folders: List<FolderManageItem>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val orderedFolders = remember { mutableStateListOf<FolderManageItem>() }
    val foldersKey = remember(folders) { folders.joinToString("|") { it.path } }

    LaunchedEffect(foldersKey) {
        orderedFolders.clear()
        orderedFolders.addAll(folders)
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        if (from.index == to.index) return@rememberReorderableLazyListState
        orderedFolders.add(to.index, orderedFolders.removeAt(from.index))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整文件夹顺序") },
        text = {
            Column {
                Text(
                    text = parentPath.ifBlank { "根目录" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = orderedFolders,
                        key = { it.path },
                    ) { folder ->
                        ReorderableItem(
                            state = reorderableState,
                            key = folder.path,
                        ) { isDragging ->
                            val elevation = animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 1.dp,
                                label = "folderSortItemElevation",
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = if (isDragging) 2.dp else 0.dp,
                                shadowElevation = elevation.value,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "长按拖动",
                                        modifier = Modifier
                                            .padding(end = 10.dp)
                                            .size(22.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = folder.path,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(orderedFolders.map { it.path }) }) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

private data class FolderManageItem(
    val name: String,
    val path: String,
    val noteCount: Int,
    val childCount: Int,
    val depth: Int = 0,
)

private fun buildManageItems(
    labels: List<String>,
    notes: List<Note>,
    parentPath: String,
    savedOrder: List<String>,
    depth: Int = 0,
): List<FolderManageItem> {
    val children = directChildFolderPaths(labels, parentPath)
    val orderIndex = savedOrder.withIndex().associate { it.value to it.index }
    return children
        .sortedWith(
            compareBy<String> { orderIndex[it] ?: Int.MAX_VALUE }
                .thenBy { it.substringAfterLast('/').lowercase() },
        )
        .map { path ->
            FolderManageItem(
                name = path.substringAfterLast('/'),
                path = path,
                noteCount = notes.count { it.folder == path },
                childCount = directChildFolderPaths(labels, path).size,
                depth = depth,
            )
        }
}

private fun buildManageTreeItems(
    labels: List<String>,
    notes: List<Note>,
    parentPath: String,
    expandedPaths: Set<String>,
    savedOrderFor: (String) -> List<String>,
): List<FolderManageItem> {
    val result = mutableListOf<FolderManageItem>()

    fun appendChildren(parent: String, depth: Int) {
        buildManageItems(
            labels = labels,
            notes = notes,
            parentPath = parent,
            savedOrder = savedOrderFor(parent),
            depth = depth,
        ).forEach { folder ->
            result += folder
            if (folder.path in expandedPaths) {
                appendChildren(folder.path, depth + 1)
            }
        }
    }

    appendChildren(parentPath, 0)
    return result
}

private fun descendantFolderPaths(
    labels: List<String>,
    parentPath: String,
): List<String> {
    val prefix = "$parentPath/"
    return labels.filter { it.startsWith(prefix) }
}

private fun directChildFolderPaths(
    labels: List<String>,
    parentPath: String,
): List<String> {
    val prefix = parentPath.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
    return labels
        .asSequence()
        .filter { it.startsWith(prefix) && it != parentPath }
        .map { it.removePrefix(prefix) }
        .filter { it.isNotBlank() && !it.contains('/') }
        .map { name -> joinFolderPath(parentPath, name) }
        .distinct()
        .toList()
}

private fun parentFolderPath(path: String): String =
    path.substringBeforeLast('/', missingDelimiterValue = "")

private fun joinFolderPath(
    parent: String,
    name: String,
): String =
    listOf(parent.trim().trim('/'), name.trim().trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")
