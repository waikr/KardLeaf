package com.kangle.kardleaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.repository.PrefsManager
import kotlinx.coroutines.delay

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppDrawerContent(
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    labels: List<String>,
    onScreenSelect: (MainViewModel.Screen) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onCreateLabel: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolderManagement: () -> Unit,
    onBackActionChanged: ((() -> Boolean)?) -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    val context = LocalContext.current
    val drawerPrefs = remember { PrefsManager(context) }
    val drawerOrder = drawerPrefs.getDrawerItemOrder()
    val hiddenItems = drawerPrefs.getHiddenDrawerItems()

    ModalDrawerSheet(
        modifier = Modifier.width(280.dp),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        var collapsedFolders by remember(labels) { mutableStateOf<Set<String>>(emptySet()) }
        var showFiles by remember { mutableStateOf(false) }
        var selectedFolderPath by remember(labels) { mutableStateOf<String?>(null) }
        var drawerUiBackStack by remember(labels) { mutableStateOf<List<DrawerUiState>>(emptyList()) }
        val visibleLabels = labels

        fun currentDrawerUiState(): DrawerUiState =
            DrawerUiState(
                showFiles = showFiles,
                selectedFolderPath = selectedFolderPath,
                collapsedFolders = collapsedFolders,
            )

        fun pushDrawerUiState() {
            val state = currentDrawerUiState()
            if (drawerUiBackStack.lastOrNull() != state) {
                drawerUiBackStack = drawerUiBackStack + state
            }
        }

        fun restoreDrawerUiState(state: DrawerUiState) {
            showFiles = state.showFiles
            selectedFolderPath = state.selectedFolderPath
            collapsedFolders = state.collapsedFolders
        }

        LaunchedEffect(drawerUiBackStack, showFiles, selectedFolderPath, collapsedFolders) {
            onBackActionChanged {
                val previous = drawerUiBackStack.lastOrNull()
                if (previous == null) {
                    false
                } else {
                    drawerUiBackStack = drawerUiBackStack.dropLast(1)
                    restoreDrawerUiState(previous)
                    true
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose { onBackActionChanged(null) }
        }
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "${stringResource(R.string.app_name_cn)} · ${stringResource(R.string.app_author)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()

                // 可编辑的侧边栏功能项（顺序与显隐由设置“侧边栏编辑”控制）
                drawerOrder.forEach { itemId ->
                    if (itemId in hiddenItems) return@forEach
                    if (itemId == PrefsManager.DrawerItemId.FILES) {
                        NavigationDrawerItem(
                            label = { Text(drawerPrefs.getDrawerItemLabel(itemId, defaultDrawerItemLabel(itemId))) },
                            icon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                            selected = currentScreen is MainViewModel.Screen.Folders,
                            onClick = { onOpenFolderManagement() },
                            modifier = Modifier.padding(horizontal = 12.dp),
                            colors = drawerItemColors(),
                        )
                    } else {
                        DrawerEntry(
                            itemId = itemId,
                            currentScreen = currentScreen,
                            currentFilter = currentFilter,
                            onDashboardFilterSelect = onDashboardFilterSelect,
                            onScreenSelect = onScreenSelect,
                            onShowOnboarding = onShowOnboarding,
                            onOpenSettings = onOpenSettings,
                            onOpenPrivacy = onOpenPrivacy,
                        )
                    }
                }

            }
        }
    }
}

@Composable
private fun drawerItemColors() =
    NavigationDrawerItemDefaults.colors(
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
        unselectedContainerColor = Color.Transparent,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        selectedBadgeColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedBadgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

@Composable
private fun DrawerEntry(
    itemId: PrefsManager.DrawerItemId,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onScreenSelect: (MainViewModel.Screen) -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember { PrefsManager(context) }
    val label = prefsManager.getDrawerItemLabel(itemId, defaultDrawerItemLabel(itemId))
    val modifier = Modifier.padding(horizontal = 12.dp)
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.All,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.All) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.RECENT -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.History, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Recent,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Recent) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.FAVORITES -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Favorites,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Favorites) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.DRAFTS -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.Drafts, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Drafts,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Drafts) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.TAGS -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.Label, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Tags,
            onClick = { onScreenSelect(MainViewModel.Screen.Tags) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.FILES -> Unit
        PrefsManager.DrawerItemId.DATES -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Dates,
            onClick = { onScreenSelect(MainViewModel.Screen.Dates) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.IMAGES -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.Image, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Images,
            onClick = { onScreenSelect(MainViewModel.Screen.Images) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.ARCHIVE -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Archive,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Archive) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.TRASH -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Trash,
            onClick = { onDashboardFilterSelect(MainViewModel.NoteFilter.Trash) },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.PRIVACY -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            selected = false,
            onClick = { onOpenPrivacy() },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.ONBOARDING -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
            selected = false,
            onClick = { onShowOnboarding() },
            modifier = modifier,
            colors = drawerItemColors(),
        )
        PrefsManager.DrawerItemId.SETTINGS -> NavigationDrawerItem(
            label = { Text(label) },
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            selected = false,
            onClick = { onOpenSettings() },
            modifier = modifier,
            colors = drawerItemColors(),
        )
    }
}

private fun defaultDrawerItemLabel(itemId: PrefsManager.DrawerItemId): String =
    when (itemId) {
        PrefsManager.DrawerItemId.ALL_NOTES -> "全部笔记"
        PrefsManager.DrawerItemId.RECENT -> "最近修改"
        PrefsManager.DrawerItemId.FAVORITES -> "收藏"
        PrefsManager.DrawerItemId.DRAFTS -> "草稿"
        PrefsManager.DrawerItemId.TAGS -> "标签"
        PrefsManager.DrawerItemId.FILES -> "文件"
        PrefsManager.DrawerItemId.DATES -> "日期"
        PrefsManager.DrawerItemId.IMAGES -> "图片"
        PrefsManager.DrawerItemId.ARCHIVE -> "归档"
        PrefsManager.DrawerItemId.TRASH -> "废弃"
        PrefsManager.DrawerItemId.PRIVACY -> "隐私"
        PrefsManager.DrawerItemId.ONBOARDING -> "介绍"
        PrefsManager.DrawerItemId.SETTINGS -> "设置"
    }

private data class DrawerUiState(
    val showFiles: Boolean,
    val selectedFolderPath: String?,
    val collapsedFolders: Set<String>,
)

private data class FolderNode(
    val name: String,
    val path: String,
    val children: List<FolderNode>,
)

@Composable
private fun FileDrawerSection(
    visibleLabels: List<String>,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    selectedFolderPath: String?,
    onToggleFolder: (String) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
) {
    if (visibleLabels.isNotEmpty()) {
        FolderTree(
            nodes = buildFolderTree(visibleLabels),
            currentScreen = currentScreen,
            currentFilter = currentFilter,
            collapsedFolders = collapsedFolders,
            selectedFolderPath = selectedFolderPath,
            onToggleFolder = onToggleFolder,
            onDashboardFilterSelect = onDashboardFilterSelect,
            onDeleteLabel = onDeleteLabel,
            onRenameLabel = onRenameLabel,
            onSelectFolder = onSelectFolder,
        )
    }
}

private fun buildFolderTree(paths: List<String>): List<FolderNode> {
    fun build(prefix: String): List<FolderNode> {
        val prefixWithSlash = prefix.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
        return paths
            .asSequence()
            .filter { it.startsWith(prefixWithSlash) && it != prefix }
            .map { it.removePrefix(prefixWithSlash).substringBefore("/") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
            .map { name ->
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                FolderNode(
                    name = name,
                    path = path,
                    children = build(path),
                )
            }
            .toList()
    }
    return build("")
}

@Composable
private fun FolderTree(
    nodes: List<FolderNode>,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    selectedFolderPath: String?,
    onToggleFolder: (String) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    depth: Int = 0,
) {
    nodes.forEach { node ->
        FolderTreeItem(
            node = node,
            depth = depth,
            currentScreen = currentScreen,
            currentFilter = currentFilter,
            collapsedFolders = collapsedFolders,
            selectedFolderPath = selectedFolderPath,
            onToggleFolder = onToggleFolder,
            onDashboardFilterSelect = onDashboardFilterSelect,
            onDeleteLabel = onDeleteLabel,
            onRenameLabel = onRenameLabel,
            onSelectFolder = onSelectFolder,
        )
        if (node.children.isNotEmpty() && node.path !in collapsedFolders) {
            FolderTree(
                nodes = node.children,
                currentScreen = currentScreen,
                currentFilter = currentFilter,
                collapsedFolders = collapsedFolders,
                selectedFolderPath = selectedFolderPath,
                onToggleFolder = onToggleFolder,
                onDashboardFilterSelect = onDashboardFilterSelect,
                onDeleteLabel = onDeleteLabel,
                onRenameLabel = onRenameLabel,
                onSelectFolder = onSelectFolder,
                depth = depth + 1,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FolderTreeItem(
    node: FolderNode,
    depth: Int,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    selectedFolderPath: String?,
    onToggleFolder: (String) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isFilterSelected = currentScreen is MainViewModel.Screen.Dashboard && (currentFilter as? MainViewModel.NoteFilter.Label)?.name == node.path
    val isActionSelected = selectedFolderPath == node.path
    val isSelected = isFilterSelected || isActionSelected
    val hasChildren = node.children.isNotEmpty()
    val isCollapsed = node.path in collapsedFolders
    var isEditing by remember(node.path) { mutableStateOf(false) }
    var editedName by remember(node.path) { mutableStateOf(node.name) }
    val editFocusRequester = remember { FocusRequester() }
    var hasEditFocused by remember(node.path) { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            hasEditFocused = false
            delay(100)
            editFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(selectedFolderPath) {
        if (selectedFolderPath != node.path && isEditing) {
            isEditing = false
            editedName = node.name
            hasEditFocused = false
        }
    }

    if (isEditing) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 4.dp + (depth * 16).dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    isEditing = false
                    onDeleteLabel(node.path)
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除文件夹",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            TextField(
                value = editedName,
                onValueChange = { editedName = it },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(editFocusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            hasEditFocused = true
                        } else if (hasEditFocused && !focusState.isFocused) {
                            isEditing = false
                            hasEditFocused = false
                        }
                    },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
            )
            IconButton(
                onClick = {
                    val trimmed = editedName.trim()
                    if (trimmed.isNotBlank() && trimmed != node.name) {
                        val parent = node.path.substringBeforeLast("/", missingDelimiterValue = "")
                        val newPath = if (parent.isBlank()) trimmed else "$parent/$trimmed"
                        onRenameLabel(node.path, newPath)
                    }
                    isEditing = false
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Outlined.Check, contentDescription = "保存文件夹名称")
            }
        }
        return
    }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        Color.Transparent
                    },
                )
                .padding(start = 24.dp + (depth * 16).dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .combinedClickable(
                        onClick = {
                            if (isActionSelected) {
                                editedName = node.name
                                isEditing = true
                            } else {
                                onSelectFolder(null)
                                onDashboardFilterSelect(MainViewModel.NoteFilter.Label(node.path))
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            editedName = node.name
                            onSelectFolder(node.path)
                        },
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.width(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = node.name,
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(
            modifier =
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable(
                        enabled = selectedFolderPath != null,
                        onClick = { onSelectFolder(null) },
                    ),
        )
        if (hasChildren) {
            IconButton(
                onClick = { onToggleFolder(node.path) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = if (isCollapsed) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (isCollapsed) "展开文件夹" else "折叠文件夹",
                    tint =
                        if (isSelected) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}
