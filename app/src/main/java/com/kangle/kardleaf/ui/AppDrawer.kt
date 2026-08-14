package com.kangle.kardleaf.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.kangle.kardleaf.R
import com.kangle.kardleaf.localizedText
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.VaultInfo
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeMode
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppDrawerContent(
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    labels: List<String>,
    allNotes: List<Note> = emptyList(),
    allLabels: List<String> = emptyList(),
    allNotesIncludingHidden: List<Note> = emptyList(),
    libraryCharacterCount: Long? = null,
    categoryOnly: Boolean = false,
    onScreenSelect: (MainViewModel.Screen) -> Unit,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onOpenFolder: (String) -> Unit = {},
    onNoteClick: (Note) -> Unit = {},
    onCreateNote: (String) -> Unit = {},
    onCreateDrawing: () -> Unit = {},
    onCreateLabel: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenFolderTree: () -> Unit,
    vaults: List<VaultInfo> = emptyList(),
    currentVault: VaultInfo? = null,
    onAddVault: () -> Unit = {},
    onSwitchVault: (VaultInfo) -> Unit = {},
    onDeleteVault: (VaultInfo) -> Unit = {},
    onRenameVault: (VaultInfo, String) -> Unit = { _, _ -> },
    onRenameNote: (Note, String) -> Unit = { _, _ -> },
    onMoveNote: (Note, String) -> Unit = { _, _ -> },
    onDeleteNote: (Note) -> Unit = {},
    onToggleFavorite: (Note) -> Unit = {},
    folderOrderVersion: Int = 0,
    getFolderDisplayOrder: (String) -> List<String> = { emptyList() },
    onSaveFolderDisplayOrder: (String, List<String>) -> Unit = { _, _ -> },
    onBackActionChanged: ((() -> Boolean)?) -> Unit = {},
    onShowOnboarding: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onPickDrawerAvatar: () -> Unit = {},
    onThemeModeChange: (PrefsManager.AppThemeMode) -> Unit = {},
) {
    val context = LocalContext.current
    val drawerPrefs = remember { PrefsManager(context) }
    val drawerOrder = drawerPrefs.getDrawerItemOrder()
    val hiddenItems = drawerPrefs.getHiddenDrawerItems()
    val drawerStyle = drawerPrefs.getDrawerStyle()
    val drawerGroupStartItems = drawerPrefs.getDrawerGroupStartItems()
    val isModern = LocalKardLeafThemeStyle.current != PrefsManager.AppThemeStyle.CLASSIC
    val drawerBackground = MaterialTheme.colorScheme.surfaceContainer

    ModalDrawerSheet(
        modifier = Modifier.width(if (isModern) 292.dp else 280.dp),
        drawerContainerColor = drawerBackground,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        val allFolderPaths = remember(labels, allNotes, allLabels, allNotesIncludingHidden) {
            collectDrawerFolderPaths(
                paths = labels + allLabels,
                notes = allNotes + allNotesIncludingHidden,
            )
        }
        var collapsedFolders by remember(allFolderPaths) {
            mutableStateOf(allFolderPaths)
        }
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
            KardLeafLog.d(
                FILE_TREE_TRACE_TAG,
                "selection restore oldHash=${selectedFolderPath?.hashCode()} newHash=${state.selectedFolderPath?.hashCode()}",
            )
            selectedFolderPath = state.selectedFolderPath
            collapsedFolders = state.collapsedFolders
        }

        LaunchedEffect(drawerUiBackStack, showFiles, selectedFolderPath, collapsedFolders) {
            onBackActionChanged {
                if (selectedFolderPath != null) {
                    KardLeafLog.d(
                        FILE_TREE_TRACE_TAG,
                        "selection clear source=back oldHash=${selectedFolderPath?.hashCode()}",
                    )
                    selectedFolderPath = null
                    true
                } else {
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
        }

        DisposableEffect(Unit) {
            onDispose { onBackActionChanged(null) }
        }

        val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
        DisposableEffect(lifecycleOwner, allFolderPaths) {
            if (lifecycleOwner == null) {
                onDispose { }
            } else {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) {
                        collapsedFolders = allFolderPaths
                        KardLeafLog.d(
                            FILE_TREE_TRACE_TAG,
                            "selection clear source=onStop oldHash=${selectedFolderPath?.hashCode()}",
                        )
                        selectedFolderPath = null
                        drawerUiBackStack = emptyList()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
        }

        if (categoryOnly) {
            CategoryDrawerContent(
                visibleLabels = visibleLabels,
                allNotes = allNotes,
                allLabels = allLabels,
                allNotesIncludingHidden = allNotesIncludingHidden,
                currentScreen = currentScreen,
                currentFilter = currentFilter,
                collapsedFolders = collapsedFolders,
                hasExpandedFolders = !collapsedFolders.containsAll(allFolderPaths),
                selectedFolderPath = selectedFolderPath,
                onToggleFolder = { path ->
                    collapsedFolders = if (path in collapsedFolders) {
                        collapsedFolders - path
                    } else {
                        collapsedFolders + path
                    }
                },
                onNoteClick = onNoteClick,
                onOpenFolder = onOpenFolder,
                onCreateNote = onCreateNote,
                onCreateLabel = onCreateLabel,
                onDeleteLabel = onDeleteLabel,
                onRenameLabel = onRenameLabel,
                onSelectFolder = { path ->
                    if (selectedFolderPath != path) {
                        KardLeafLog.d(
                            FILE_TREE_TRACE_TAG,
                            "selection oldHash=${selectedFolderPath?.hashCode()} newHash=${path?.hashCode()}",
                        )
                        selectedFolderPath = path
                    }
                },
                onExpandAll = { collapsedFolders = emptySet() },
                onCollapseAll = {
                    collapsedFolders = allFolderPaths
                    KardLeafLog.d(
                        FILE_TREE_TRACE_TAG,
                        "selection clear source=collapseAll oldHash=${selectedFolderPath?.hashCode()}",
                    )
                    selectedFolderPath = null
                },
                onDeleteFolder = onDeleteFolder,
                onRenameVault = onRenameVault,
                onRenameNote = onRenameNote,
                onMoveNote = onMoveNote,
                onDeleteNote = onDeleteNote,
                onToggleFavorite = onToggleFavorite,
                vaults = vaults,
                currentVault = currentVault,
                onAddVault = onAddVault,
                onSwitchVault = onSwitchVault,
                onDeleteVault = onDeleteVault,
                folderOrderVersion = folderOrderVersion,
                getFolderDisplayOrder = getFolderDisplayOrder,
                onSaveFolderDisplayOrder = onSaveFolderDisplayOrder,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(drawerBackground),
            ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) {
                    // 方案四是独立侧边栏布局，不依赖“非旧主题”。
                    // 否则用户在旧主题/经典主题下选择数据卡片式时，看不到热力图。
                    DataCardDrawerHeader(
                        avatarUri = drawerPrefs.getDrawerAvatarUri(),
                        onPickAvatar = onPickDrawerAvatar,
                        onOpenSettings = onOpenSettings,
                        onThemeModeChange = onThemeModeChange,
                    )
                    DataCardHeatmap(
                        allNotes = allNotes,
                        libraryCharacterCount = libraryCharacterCount,
                    )
                } else {
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
                }

                // 可编辑的侧边栏功能项（顺序、显隐、分组由设置“侧边栏调整”控制）
                val visibleDrawerItems = drawerOrder.filter { itemId ->
                    itemId !in hiddenItems &&
                        !(drawerStyle == PrefsManager.DrawerStyle.DATA_CARD && itemId == PrefsManager.DrawerItemId.SETTINGS)
                }
                if (drawerStyle.isGroupedDrawerStyle()) {
                    buildDrawerItemGroups(visibleDrawerItems, drawerGroupStartItems).forEach { groupItems ->
                        DrawerItemGroup(drawerStyle = drawerStyle) {
                            groupItems.forEach { itemId ->
                                AppDrawerFunctionalItem(
                                    itemId = itemId,
                                    drawerPrefs = drawerPrefs,
                                    currentScreen = currentScreen,
                                    currentFilter = currentFilter,
                                    onDashboardFilterSelect = onDashboardFilterSelect,
                                    onScreenSelect = onScreenSelect,
                                    onCreateDrawing = onCreateDrawing,
                                    onOpenFolderTree = onOpenFolderTree,
                                    onShowOnboarding = onShowOnboarding,
                                    onOpenSettings = onOpenSettings,
                                    onOpenPrivacy = onOpenPrivacy,
                                )
                            }
                        }
                    }
                } else {
                    visibleDrawerItems.forEach { itemId ->
                        AppDrawerFunctionalItem(
                            itemId = itemId,
                            drawerPrefs = drawerPrefs,
                            currentScreen = currentScreen,
                            currentFilter = currentFilter,
                            onDashboardFilterSelect = onDashboardFilterSelect,
                            onScreenSelect = onScreenSelect,
                            onCreateDrawing = onCreateDrawing,
                            onOpenFolderTree = onOpenFolderTree,
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
}


@Composable
private fun DataCardDrawerHeader(
    avatarUri: String?,
    onPickAvatar: () -> Unit,
    onOpenSettings: () -> Unit,
    onThemeModeChange: (PrefsManager.AppThemeMode) -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val themeStyle = LocalKardLeafThemeStyle.current
    val themeMode = LocalKardLeafThemeMode.current
    val isDarkNow = themeStyle == PrefsManager.AppThemeStyle.DRACULA ||
        themeStyle == PrefsManager.AppThemeStyle.GITHUB_DARK ||
        when (themeMode) {
            PrefsManager.AppThemeMode.SYSTEM -> systemDark
            PrefsManager.AppThemeMode.LIGHT -> false
            PrefsManager.AppThemeMode.DARK -> true
        }
    val avatarImage = rememberDrawerAvatarImage(avatarUri)
    var showAvatarDialog by remember { mutableStateOf(false) }

    if (showAvatarDialog) {
        Dialog(
            onDismissRequest = { showAvatarDialog = false },
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "更换头像",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "选择图片后将立即更新侧边栏头像",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(999.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (avatarImage != null) {
                            Image(
                                bitmap = avatarImage,
                                contentDescription = "头像预览",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = "头像预览",
                                modifier = Modifier.size(82.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = {
                            showAvatarDialog = false
                            onPickAvatar()
                        },
                        modifier = Modifier
                            .width(156.dp)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("上传头像")
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { showAvatarDialog = true },
            contentAlignment = Alignment.Center,
        ) {
            if (avatarImage != null) {
                Image(
                    bitmap = avatarImage,
                    contentDescription = "更换头像",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = "更换头像",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.app_name_cn),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = {
                onThemeModeChange(
                    if (isDarkNow) PrefsManager.AppThemeMode.LIGHT else PrefsManager.AppThemeMode.DARK,
                )
            },
        ) {
            Icon(
                imageVector = if (isDarkNow) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = "切换黑夜模式",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberDrawerAvatarImage(avatarUri: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(avatarUri) {
        avatarUri
            ?.takeIf { it.isNotBlank() }
            ?.let { uriText ->
                runCatching {
                    context.contentResolver.openInputStream(Uri.parse(uriText))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
    }
}

@Composable
private fun DataCardHeatmap(
    allNotes: List<Note>,
    libraryCharacterCount: Long?,
) {
    val today = remember { heatmapDayStart(Date()) }
    val monthStart = remember(today) {
        Calendar.getInstance().apply {
            time = today
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -2)
        }.time
    }
    val gridStart = remember(monthStart) {
        Calendar.getInstance().apply {
            time = monthStart
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
        }.time
    }
    val heatmapStats = remember(allNotes, today, monthStart, gridStart) {
        buildHeatmapStats(
            notes = allNotes,
            rangeStart = monthStart,
            rangeEnd = today,
            gridStart = gridStart,
        )
    }
    val monthFormatter = remember { SimpleDateFormat("M月", Locale.getDefault()) }
    val monthLabels = remember(monthStart) {
        List(3) { offset ->
            Calendar.getInstance().apply {
                time = monthStart
                add(Calendar.MONTH, offset)
            }.time
        }.map { monthFormatter.format(it) }
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
                shape = shape,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            monthLabels.forEach { month ->
                Text(
                    text = month,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                heatmapStats.columns.forEach { week ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        week.forEach { day ->
                            val color = when {
                                day.date.before(monthStart) || day.date.after(today) -> {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
                                }
                                day.noteCount <= 0 -> {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                                }
                                day.noteCount == 1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                day.noteCount == 2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.46f)
                                day.noteCount == 3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color),
                            )
                        }
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { dayLabel ->
                    Text(
                        text = dayLabel,
                        modifier = Modifier.height(11.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DataCardHeatmapStat(
                value = heatmapStats.activeDayCount.toString(),
                label = "使用天数",
                modifier = Modifier.weight(1f),
            )
            DataCardStatDivider()
            DataCardHeatmapStat(
                value = heatmapStats.noteCount.toString(),
                label = "笔记数量",
                modifier = Modifier.weight(1f),
            )
            DataCardStatDivider()
            DataCardHeatmapStat(
                value = libraryCharacterCount?.let(::formatHeatmapNumber) ?: "待统计",
                label = "文字数量",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DataCardHeatmapStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DataCardStatDivider() {
    Box(
        modifier = Modifier
            .height(30.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    )
}

private data class HeatmapStats(
    val columns: List<List<HeatmapDay>>,
    val activeDayCount: Int,
    val noteCount: Int,
)

private data class HeatmapDay(
    val date: Date,
    val noteCount: Int,
)

private fun buildHeatmapStats(
    notes: List<Note>,
    rangeStart: Date,
    rangeEnd: Date,
    gridStart: Date,
): HeatmapStats {
    val dayCounts = mutableMapOf<Long, Int>()
    var noteCount = 0

    notes.forEach { note ->
        if (note.isTrashed || note.isArchived) return@forEach
        val createdDay = heatmapDayStart(note.createdAt)
        if (createdDay.before(rangeStart) || createdDay.after(rangeEnd)) return@forEach
        val key = createdDay.time
        dayCounts[key] = (dayCounts[key] ?: 0) + 1
        noteCount++
    }

    val columns = mutableListOf<List<HeatmapDay>>()
    val cursor = Calendar.getInstance().apply { time = gridStart }
    while (!cursor.time.after(rangeEnd)) {
        val week = mutableListOf<HeatmapDay>()
        repeat(7) {
            val date = cursor.time
            week.add(HeatmapDay(date = date, noteCount = dayCounts[date.time] ?: 0))
            cursor.add(Calendar.DAY_OF_MONTH, 1)
        }
        columns.add(week)
    }

    return HeatmapStats(
        columns = columns,
        activeDayCount = dayCounts.size,
        noteCount = noteCount,
    )
}

private fun heatmapDayStart(date: Date): Date =
    Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.time

private fun formatHeatmapNumber(value: Long): String =
    when {
        value >= 100_000_000L -> String.format(Locale.getDefault(), "%.1f亿", value / 100_000_000f)
        value >= 10_000L -> String.format(Locale.getDefault(), "%.1f万", value / 10_000f)
        else -> value.toString()
    }


private fun PrefsManager.DrawerStyle.isGroupedDrawerStyle(): Boolean =
    this == PrefsManager.DrawerStyle.GROUPED_CARD || this == PrefsManager.DrawerStyle.DATA_CARD

@Composable
private fun FileTreeToolbarIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private class AboveFileTreePopupPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2).coerceIn(0, maxX)
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun FileTreeActionPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!expanded) return
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        AboveFileTreePopupPositionProvider(with(density) { 4.dp.roundToPx() })
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
        ) {
            Row {
                content()
            }
        }
    }
}

@Composable
private fun CategoryDrawerContent(
    visibleLabels: List<String>,
    allNotes: List<Note>,
    allLabels: List<String>,
    allNotesIncludingHidden: List<Note>,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    hasExpandedFolders: Boolean,
    selectedFolderPath: String?,
    onToggleFolder: (String) -> Unit,
    onNoteClick: (Note) -> Unit,
    onOpenFolder: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onCreateLabel: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onDeleteFolder: (String) -> Unit,
    vaults: List<VaultInfo>,
    currentVault: VaultInfo?,
    onAddVault: () -> Unit,
    onSwitchVault: (VaultInfo) -> Unit,
    onDeleteVault: (VaultInfo) -> Unit,
    onRenameVault: (VaultInfo, String) -> Unit,
    onRenameNote: (Note, String) -> Unit,
    onMoveNote: (Note, String) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    folderOrderVersion: Int,
    getFolderDisplayOrder: (String) -> List<String>,
    onSaveFolderDisplayOrder: (String, List<String>) -> Unit,
) {
    var moveTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var noteMoveTarget by remember { mutableStateOf<Note?>(null) }
    var noteDeleteTarget by remember { mutableStateOf<Note?>(null) }
    var inlineFolderEditor by remember { mutableStateOf<DrawerInlineFolderEditor?>(null) }
    var inlineFolderText by remember { mutableStateOf(TextFieldValue()) }
    var inlineNoteTarget by remember { mutableStateOf<Note?>(null) }
    var inlineNoteText by remember { mutableStateOf(TextFieldValue()) }
    var sortDialogParent by remember { mutableStateOf<String?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showVaultMenu by remember { mutableStateOf(false) }
    var vaultToDelete by remember { mutableStateOf<VaultInfo?>(null) }
    var vaultActionTarget by remember { mutableStateOf<VaultInfo?>(null) }
    var vaultToRename by remember { mutableStateOf<VaultInfo?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showHiddenFolders by remember { mutableStateOf(false) }
    var showAllFiles by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fileTreeScrollState = rememberScrollState()
    var drawerScanResult by remember(currentVault?.uri) { mutableStateOf(DrawerScanResult()) }
    // ponytail: scan only while a filter needs it; add an indexed file cache if this becomes slow.
    LaunchedEffect(currentVault?.uri, showHiddenFolders, showAllFiles) {
        drawerScanResult = if (showHiddenFolders || showAllFiles) {
            scanDrawerFiles(
                context = context,
                rootUri = currentVault?.uri,
                includeHiddenFolders = showHiddenFolders,
            )
        } else {
            DrawerScanResult()
        }
    }
    val treeLabels = remember(
        visibleLabels,
        allLabels,
        showHiddenFolders,
        drawerScanResult.folders,
        inlineFolderEditor?.path,
    ) {
        (if (showHiddenFolders) allLabels else visibleLabels) +
            drawerScanResult.folders +
            listOfNotNull(inlineFolderEditor?.path)
    }
    val treeNotes = if (showHiddenFolders && allNotesIncludingHidden.isNotEmpty()) {
        allNotesIncludingHidden
    } else {
        allNotes
    }
    val normalizedLabels = remember(treeLabels) { treeLabels.map(::normalizeDrawerPath) }
    val normalizedNotes = remember(treeNotes) {
        treeNotes.filter { !it.isTrashed && !it.isArchived }
    }
    LaunchedEffect(normalizedLabels, normalizedNotes) {
        KardLeafLog.d(
            FILE_TREE_TRACE_TAG,
            "tree data updated folders=${normalizedLabels.size} notes=${normalizedNotes.size}",
        )
    }
    val folderTree = remember(normalizedLabels, normalizedNotes, folderOrderVersion) {
        buildFolderTree(normalizedLabels, normalizedNotes, getFolderDisplayOrder)
    }
    val allFolderPaths = remember(normalizedLabels, normalizedNotes) {
        normalizedDrawerPaths(normalizedLabels, normalizedNotes)
    }

    fun clearInlineEditorState() {
        inlineFolderEditor = null
        inlineFolderText = TextFieldValue()
        inlineNoteTarget = null
        inlineNoteText = TextFieldValue()
    }

    fun cancelInlineEditor() {
        clearInlineEditorState()
        onSelectFolder(null)
    }

    fun finishInlineEditor(): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        inlineFolderEditor?.let { editor ->
            val name = inlineFolderText.text.trim().trim('/')
            if (name.isBlank() || name.contains('/')) {
                context.showToast("请输入有效的文件夹名称")
                return false
            }
            val newPath = joinDrawerFolderPath(editor.parentPath, name)
            if (newPath != editor.path && newPath in normalizedLabels) {
                context.showToast("已存在同名文件夹")
                return false
            }
            KardLeafLog.d(
                FILE_TREE_TRACE_TAG,
                "inline done event kind=folder isNew=${editor.isNew} pathHash=${editor.path.hashCode()} " +
                    "newPathHash=${newPath.hashCode()} textLength=${inlineFolderText.text.length}",
            )
            val callbackStartedAt = SystemClock.elapsedRealtime()
            if (editor.isNew) {
                onCreateLabel(newPath)
            } else if (newPath != editor.path) {
                onRenameLabel(editor.path, newPath)
            }
            KardLeafLog.d(
                FILE_TREE_TRACE_TAG,
                "inline callback returned kind=folder isNew=${editor.isNew} " +
                    "elapsed=${SystemClock.elapsedRealtime() - callbackStartedAt}ms",
            )
            clearInlineEditorState()
            onSelectFolder(null)
            KardLeafLog.d(
                FILE_TREE_TRACE_TAG,
                "inline ui cleared kind=folder elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
            )
            return true
        }
        inlineNoteTarget?.let { note ->
            val title = inlineNoteText.text.trim().trim('/')
            if (title.isBlank() || title.contains('/')) {
                context.showToast("请输入有效的文件名称")
                return false
            }
            KardLeafLog.d(
                FILE_TREE_TRACE_TAG,
                "inline done event kind=note pathHash=${note.file.path.hashCode()} " +
                    "changed=${title != note.title} textLength=${inlineNoteText.text.length}",
            )
            val callbackStartedAt = SystemClock.elapsedRealtime()
            if (title != note.title) onRenameNote(note, title)
            KardLeafLog.d(
                FILE_TREE_TRACE_TAG,
                "inline callback returned kind=note elapsed=${SystemClock.elapsedRealtime() - callbackStartedAt}ms",
            )
            clearInlineEditorState()
            onSelectFolder(null)
            KardLeafLog.d(
                FILE_TREE_TRACE_TAG,
                "inline ui cleared kind=note elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
            )
            return true
        }
        return true
    }

    fun selectFolder(path: String?): Boolean {
        KardLeafLog.d(
            FILE_TREE_TRACE_TAG,
            "select request newHash=${path?.hashCode()} inlineFolder=${inlineFolderEditor != null} " +
                "inlineNote=${inlineNoteTarget != null}",
        )
        if (inlineFolderEditor != null || inlineNoteTarget != null) {
            if (!finishInlineEditor()) return false
            if (path == null) return true
        }
        onSelectFolder(path)
        return true
    }

    fun startNewFolder(parent: String) {
        if (!selectFolder(null)) return
        val normalizedParent = normalizeDrawerPath(parent)
        if (normalizedParent in collapsedFolders) onToggleFolder(normalizedParent)
        val path = joinDrawerFolderPath(normalizedParent, INLINE_NEW_FOLDER_PATH)
        inlineFolderEditor = DrawerInlineFolderEditor(path, normalizedParent, isNew = true)
        inlineFolderText = TextFieldValue()
        onSelectFolder(path)
    }

    fun startRenameFolder(path: String) {
        if (!selectFolder(null)) return
        val name = path.substringAfterLast('/')
        inlineFolderEditor = DrawerInlineFolderEditor(path, parentDrawerFolderPath(path), isNew = false)
        inlineFolderText = TextFieldValue(name, TextRange(0, name.length))
        onSelectFolder(path)
    }

    fun startRenameNote(note: Note) {
        if (!selectFolder(null)) return
        val name = note.title.ifBlank { note.file.nameWithoutExtension }
        inlineNoteTarget = note
        inlineNoteText = TextFieldValue(name, TextRange(0, name.length))
    }

    val inlineFolderPath = inlineFolderEditor?.path
    val inlineNotePath = inlineNoteTarget?.file?.path

    BackHandler(enabled = inlineFolderEditor != null || inlineNoteTarget != null || selectedFolderPath != null || noteMoveTarget != null || noteDeleteTarget != null) {
        when {
            inlineFolderEditor != null || inlineNoteTarget != null -> cancelInlineEditor()
            noteMoveTarget != null -> noteMoveTarget = null
            noteDeleteTarget != null -> noteDeleteTarget = null
            else -> selectFolder(null)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 10.dp, top = 18.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Row(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        selectFolder(null)
                        showVaultMenu = true
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = currentVault?.displayName ?: "笔记库",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp),
                    )
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "切换仓库",
                        modifier = Modifier.padding(start = 2.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KardLeafDropdownMenu(
                    expanded = showVaultMenu,
                    onDismissRequest = { showVaultMenu = false },
                    modifier = Modifier.width(240.dp),
                ) {
                    vaults.forEach { vault ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = vault.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                showVaultMenu = false
                                if (vault.uri != currentVault?.uri) onSwitchVault(vault)
                            },
                            modifier = Modifier.background(
                                if (vault.uri == currentVault?.uri) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                } else {
                                    Color.Transparent
                                },
                            ),
                            trailingIcon = {
                                Box {
                                    IconButton(onClick = { vaultActionTarget = vault }) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = "仓库更多选项")
                                    }
                                    KardLeafDropdownMenu(
                                        expanded = vaultActionTarget == vault,
                                        onDismissRequest = { if (vaultActionTarget == vault) vaultActionTarget = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("重命名") },
                                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                                            onClick = {
                                                vaultActionTarget = null
                                                showVaultMenu = false
                                                vaultToRename = vault
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除") },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                                            onClick = {
                                                vaultActionTarget = null
                                                showVaultMenu = false
                                                vaultToDelete = vault
                                            },
                                        )
                                    }
                                }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("添加仓库") },
                        onClick = {
                            showVaultMenu = false
                            onAddVault()
                        },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileTreeToolbarIconButton(
                onClick = {
                    selectFolder(null)
                    if (hasExpandedFolders) onCollapseAll() else onExpandAll()
                },
            ) {
                Icon(
                    imageVector = if (hasExpandedFolders) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                    contentDescription = if (hasExpandedFolders) "全部收起" else "全部展开",
                )
            }
            FileTreeToolbarIconButton(onClick = {
                selectFolder(null)
                onCreateNote("")
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_file_tree_new_note),
                    contentDescription = "新增文件",
                    tint = Color.Unspecified,
                )
            }
            FileTreeToolbarIconButton(onClick = {
                startNewFolder("")
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_file_tree_new_folder),
                    contentDescription = "新增文件夹",
                    tint = Color.Unspecified,
                )
            }
            Box {
                FileTreeToolbarIconButton(onClick = {
                    selectFolder(null)
                    showFilterMenu = true
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_tree_filter),
                        contentDescription = "筛选",
                        tint = Color.Unspecified,
                    )
                }
                KardLeafDropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("显示隐藏文件夹") },
                        trailingIcon = {
                            if (showHiddenFolders) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = { showHiddenFolders = !showHiddenFolders },
                    )
                    DropdownMenuItem(
                        text = { Text("显示全部文件") },
                        trailingIcon = {
                            if (showAllFiles) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = { showAllFiles = !showAllFiles },
                    )
                }
            }
            Box {
                FileTreeToolbarIconButton(onClick = {
                    selectFolder(null)
                    showSortMenu = true
                }) {
                    Icon(
                        Icons.Filled.SwapVert,
                        contentDescription = "排序",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KardLeafDropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("标题") },
                        trailingIcon = {
                            if (getFolderDisplayOrder("").isEmpty()) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            onSaveFolderDisplayOrder("", emptyList())
                            showSortMenu = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("自定义") },
                        enabled = folderTree.size > 1,
                        trailingIcon = {
                            if (getFolderDisplayOrder("").isNotEmpty()) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            showSortMenu = false
                            sortDialogParent = ""
                        },
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .imePadding()
                .verticalScroll(fileTreeScrollState)
                .padding(vertical = 8.dp),
        ) {
            FileDrawerSection(
                visibleLabels = treeLabels,
                allNotes = treeNotes,
                extraFiles = drawerScanResult.files,
                showAllFiles = showAllFiles,
                currentScreen = currentScreen,
                currentFilter = currentFilter,
                collapsedFolders = collapsedFolders,
                selectedFolderPath = selectedFolderPath,
                showHiddenFolders = showHiddenFolders,
                inlineFolderPath = inlineFolderPath,
                inlineFolderText = inlineFolderText,
                inlineNotePath = inlineNotePath,
                inlineNoteText = inlineNoteText,
                onToggleFolder = onToggleFolder,
                onNoteClick = onNoteClick,
                onOpenFolder = onOpenFolder,
                onCreateNote = onCreateNote,
                onCreateFolder = ::startNewFolder,
                onDeleteLabel = onDeleteLabel,
                onRenameLabel = onRenameLabel,
                onSelectFolder = ::selectFolder,
                onRenameFolder = ::startRenameFolder,
                onMoveFolder = { moveTarget = it },
                onDeleteFolder = { deleteTarget = it },
                onRenameNote = ::startRenameNote,
                onMoveNote = { noteMoveTarget = it },
                onDeleteNote = { noteDeleteTarget = it },
                onToggleFavorite = onToggleFavorite,
                onInlineFolderTextChange = { inlineFolderText = it },
                onInlineFolderDone = { finishInlineEditor() },
                onInlineFolderCancel = ::cancelInlineEditor,
                onInlineNoteTextChange = { inlineNoteText = it },
                onInlineNoteDone = { finishInlineEditor() },
                onInlineNoteCancel = ::cancelInlineEditor,
                folderOrderVersion = folderOrderVersion,
                getFolderDisplayOrder = getFolderDisplayOrder,
            )
        }
    }

    moveTarget?.let { path ->
        DrawerMoveFolderDialog(
            folderPath = path,
            labels = normalizedLabels,
            onDismiss = { moveTarget = null },
            onMove = { targetParent ->
                val newPath = joinDrawerFolderPath(targetParent, path.substringAfterLast('/'))
                when {
                    newPath == path -> moveTarget = null
                    newPath in normalizedLabels -> context.showToast("目标位置已存在同名文件夹")
                    else -> {
                        onRenameLabel(path, newPath)
                        moveTarget = null
                    }
                }
            },
        )
    }

    deleteTarget?.let { path ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除文件夹") },
            text = { Text("将删除“${path.substringAfterLast('/')}”及其中的笔记和子文件夹。确定继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFolder(path)
                        deleteTarget = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }

    vaultToDelete?.let { vault ->
        AlertDialog(
            onDismissRequest = { vaultToDelete = null },
            title = { Text("删除仓库") },
            text = {
                Text("将从列表中删除“${vault.displayName}”及其 Room 缓存，不会删除目录中的 Markdown 文件。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteVault(vault)
                        vaultToDelete = null
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { vaultToDelete = null }) { Text("取消") }
            },
        )
    }

    vaultToRename?.let { vault ->
        DrawerFolderNameDialog(
            title = "重命名仓库",
            initialName = vault.displayName,
            fieldLabel = "仓库名称",
            onDismiss = { vaultToRename = null },
            onConfirm = { name ->
                if (name != vault.displayName) onRenameVault(vault, name)
                vaultToRename = null
            },
        )
    }

    noteMoveTarget?.let { note ->
        DrawerMoveFolderDialog(
            title = "移动文件",
            folderPath = note.file.path,
            initialParentPath = normalizeDrawerPath(note.folder),
            labels = normalizedLabels,
            onDismiss = { noteMoveTarget = null },
            onMove = { targetFolder ->
                onMoveNote(note, targetFolder)
                noteMoveTarget = null
            },
        )
    }

    noteDeleteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { noteDeleteTarget = null },
            title = { Text("删除文件") },
            text = { Text("确定删除“${note.title.ifBlank { note.file.nameWithoutExtension }}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteNote(note)
                        noteDeleteTarget = null
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteDeleteTarget = null }) { Text("取消") }
            },
        )
    }

    sortDialogParent?.let { parentPath ->
        DrawerFolderSortDialog(
            allFolderPaths = allFolderPaths,
            allNotes = normalizedNotes,
            initialParentPath = parentPath,
            folderOrderVersion = folderOrderVersion,
            getFolderDisplayOrder = getFolderDisplayOrder,
            onDismiss = { sortDialogParent = null },
            onSave = { path, order ->
                onSaveFolderDisplayOrder(path, order)
                sortDialogParent = null
            },
        )
    }
}

private fun parentDrawerFolderPath(path: String): String =
    path.substringBeforeLast('/', missingDelimiterValue = "")

private fun joinDrawerFolderPath(parent: String, name: String): String =
    listOf(parent, name.trim().trim('/'))
        .filter { it.isNotBlank() }
        .joinToString("/")

@Composable
private fun DrawerFolderNameDialog(
    title: String,
    initialName: String,
    fieldLabel: String = "文件夹名称",
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
                label = { Text(fieldLabel) },
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotBlank() && !trimmed.contains('/'),
                onClick = { onConfirm(trimmed) },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DrawerMoveFolderDialog(
    title: String = "移动文件夹",
    folderPath: String,
    initialParentPath: String? = null,
    labels: List<String>,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit,
) {
    var selectedParent by remember(folderPath) {
        mutableStateOf(initialParentPath ?: parentDrawerFolderPath(folderPath))
    }
    val availableParents = remember(labels, folderPath) {
        (listOf("") + labels)
            .filter { path ->
                path != folderPath && !path.startsWith("$folderPath/")
            }
            .distinct()
            .sorted()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = folderPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
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
                            color = if (selectedParent == parent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(selectedParent) }) { Text("移动") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun DrawerFolderSortDialog(
    allFolderPaths: List<String>,
    allNotes: List<Note>,
    initialParentPath: String,
    folderOrderVersion: Int,
    getFolderDisplayOrder: (String) -> List<String>,
    onDismiss: () -> Unit,
    onSave: (String, List<String>) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val normalizedPaths = remember(allFolderPaths, allNotes) {
        normalizedDrawerPaths(allFolderPaths, allNotes)
    }
    val parentChoices = remember(normalizedPaths) {
        (listOf("") + normalizedPaths)
            .distinct()
            .filter { parent ->
                buildFolderNodes(normalizedPaths, allNotes, parent, getFolderDisplayOrder).size > 1
            }
            .sortedWith(compareBy<String> { it.isNotBlank() }.thenBy { it })
    }
    var selectedParent by remember(initialParentPath) { mutableStateOf(initialParentPath) }
    var showParentMenu by remember { mutableStateOf(false) }
    val folders = remember(normalizedPaths, selectedParent, folderOrderVersion) {
        buildFolderNodes(normalizedPaths, allNotes, selectedParent, getFolderDisplayOrder)
    }
    val orderedFolders = remember { mutableStateListOf<FolderNode>() }
    val foldersKey = remember(folders) { folders.joinToString("|") { it.path } }

    LaunchedEffect(foldersKey) {
        orderedFolders.clear()
        orderedFolders.addAll(folders)
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index == to.index) return@rememberReorderableLazyListState
        orderedFolders.add(to.index, orderedFolders.removeAt(from.index))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整文件夹顺序") },
        text = {
            Column {
                Box {
                    TextButton(onClick = { showParentMenu = true }) {
                        Text(selectedParent.ifBlank { "根目录" })
                    }
                    KardLeafDropdownMenu(
                        expanded = showParentMenu,
                        onDismissRequest = { showParentMenu = false },
                    ) {
                        parentChoices.forEach { parent ->
                            DropdownMenuItem(
                                text = { Text(parent.ifBlank { "根目录" }) },
                                onClick = {
                                    selectedParent = parent
                                    showParentMenu = false
                                },
                            )
                        }
                    }
                }
                if (orderedFolders.isEmpty()) {
                    Text(
                        text = "当前目录没有可排序的文件夹",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(orderedFolders, key = { it.path }) { folder ->
                            ReorderableItem(
                                state = reorderableState,
                                key = folder.path,
                            ) { isDragging ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    tonalElevation = if (isDragging) 2.dp else 0.dp,
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
                                            imageVector = Icons.Filled.Menu,
                                            contentDescription = "长按拖动",
                                            modifier = Modifier
                                                .padding(end = 10.dp)
                                                .size(22.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = folder.name,
                                            modifier = Modifier.weight(1f),
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
            Button(onClick = { onSave(selectedParent, orderedFolders.map { it.path }) }) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun buildDrawerItemGroups(
    visibleItems: List<PrefsManager.DrawerItemId>,
    groupStartItems: Set<PrefsManager.DrawerItemId>,
): List<List<PrefsManager.DrawerItemId>> {
    if (visibleItems.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<PrefsManager.DrawerItemId>>()
    visibleItems.forEachIndexed { index, itemId ->
        if (index == 0 || itemId in groupStartItems) {
            groups.add(mutableListOf())
        }
        groups.last().add(itemId)
    }
    return groups.filter { it.isNotEmpty() }
}

@Composable
private fun DrawerItemGroup(
    drawerStyle: PrefsManager.DrawerStyle,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(if (drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) 24.dp else 22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) 0.72f else 0.88f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
                shape = shape,
            )
            .padding(vertical = if (drawerStyle == PrefsManager.DrawerStyle.DATA_CARD) 8.dp else 6.dp),
    ) {
        content()
    }
}

@Composable
private fun AppDrawerFunctionalItem(
    itemId: PrefsManager.DrawerItemId,
    drawerPrefs: PrefsManager,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    onDashboardFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onScreenSelect: (MainViewModel.Screen) -> Unit,
    onCreateDrawing: () -> Unit,
    onOpenFolderTree: () -> Unit,
    onShowOnboarding: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    if (itemId == PrefsManager.DrawerItemId.FILES) {
        ThemedDrawerItem(
            label = drawerPrefs.getDrawerItemLabel(
                itemId,
                if (drawerPrefs.getAppLanguage() == "en") englishDrawerItemLabel(itemId) else defaultDrawerItemLabel(itemId),
            ),
            icon = Icons.Outlined.Folder,
            selected = false,
            onClick = { onOpenFolderTree() },
        )
    } else {
        DrawerEntry(
            itemId = itemId,
            currentScreen = currentScreen,
            currentFilter = currentFilter,
            onDashboardFilterSelect = onDashboardFilterSelect,
            onScreenSelect = onScreenSelect,
            onCreateDrawing = onCreateDrawing,
            onShowOnboarding = onShowOnboarding,
            onOpenSettings = onOpenSettings,
            onOpenPrivacy = onOpenPrivacy,
        )
    }
}

private data class DrawerUiState(
    val showFiles: Boolean,
    val selectedFolderPath: String?,
    val collapsedFolders: Set<String>,
)

private const val INLINE_NEW_FOLDER_PATH = "__kardleaf_inline_new_folder__"
private const val FILE_TREE_TRACE_TAG = "KardLeafFileTree"

private data class DrawerInlineFolderEditor(
    val path: String,
    val parentPath: String,
    val isNew: Boolean,
)

private data class FolderNode(
    val name: String,
    val path: String,
    val children: List<FolderNode>,
    val notes: List<Note>,
    val files: List<DrawerFile> = emptyList(),
)

private data class DrawerFile(
    val name: String,
    val path: String,
)

private data class DrawerScanResult(
    val folders: Set<String> = emptySet(),
    val files: List<DrawerFile> = emptyList(),
)

private suspend fun scanDrawerFiles(
    context: android.content.Context,
    rootUri: String?,
    includeHiddenFolders: Boolean,
): DrawerScanResult = withContext(Dispatchers.IO) {
    val root = rootUri
        ?.let { runCatching { DocumentFile.fromTreeUri(context, Uri.parse(it)) }.getOrNull() }
        ?: return@withContext DrawerScanResult()
    val folders = linkedSetOf<String>()
    val files = mutableListOf<DrawerFile>()

    fun visit(directory: DocumentFile, parentPath: String) {
        directory.listFiles().forEach { child ->
            val name = child.name?.trim().orEmpty()
            if (name.isBlank()) return@forEach
            val path = joinDrawerFolderPath(parentPath, name)
            if (child.isDirectory) {
                if (!includeHiddenFolders && name.startsWith('.')) return@forEach
                folders += path
                visit(child, path)
            } else if (child.isFile) {
                if (!includeHiddenFolders && path.split('/').any { it.startsWith('.') }) return@forEach
                files += DrawerFile(name = name, path = path)
            }
        }
    }

    visit(root, "")
    DrawerScanResult(folders = folders, files = files)
}

@Composable
private fun FileDrawerSection(
    visibleLabels: List<String>,
    allNotes: List<Note>,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    selectedFolderPath: String?,
    showHiddenFolders: Boolean,
    inlineFolderPath: String?,
    inlineFolderText: TextFieldValue,
    inlineNotePath: String?,
    inlineNoteText: TextFieldValue,
    showAllFiles: Boolean,
    extraFiles: List<DrawerFile>,
    onToggleFolder: (String) -> Unit,
    onNoteClick: (Note) -> Unit,
    onOpenFolder: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onRenameFolder: (String) -> Unit,
    onMoveFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRenameNote: (Note) -> Unit,
    onMoveNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onInlineFolderTextChange: (TextFieldValue) -> Unit,
    onInlineFolderDone: () -> Unit,
    onInlineFolderCancel: () -> Unit,
    onInlineNoteTextChange: (TextFieldValue) -> Unit,
    onInlineNoteDone: () -> Unit,
    onInlineNoteCancel: () -> Unit,
    folderOrderVersion: Int = 0,
    getFolderDisplayOrder: (String) -> List<String> = { emptyList() },
) {
    val visibleNotes = remember(allNotes) { allNotes.filter { !it.isTrashed && !it.isArchived } }
    val rootNotes = remember(visibleNotes) {
        visibleNotes
            .filter { normalizeDrawerPath(it.folder).isBlank() }
            .sortedBy { it.title.lowercase(Locale.getDefault()) }
    }
    val notePaths = remember(visibleNotes) { visibleNotes.map { normalizeDrawerPath(it.file.path) }.toSet() }
    val drawerFiles = remember(extraFiles, notePaths, showAllFiles) {
        if (showAllFiles) extraFiles.filterNot { normalizeDrawerPath(it.path) in notePaths } else emptyList()
    }
    val folderTree = remember(visibleLabels, visibleNotes, drawerFiles, folderOrderVersion) {
        buildFolderTree(
            paths = visibleLabels,
            notes = visibleNotes,
            savedOrderFor = getFolderDisplayOrder,
            files = drawerFiles,
        )
    }
    if (folderTree.isNotEmpty()) {
        FolderTree(
            nodes = folderTree,
            currentScreen = currentScreen,
            currentFilter = currentFilter,
            collapsedFolders = collapsedFolders,
            selectedFolderPath = selectedFolderPath,
            inlineFolderPath = inlineFolderPath,
            inlineFolderText = inlineFolderText,
            inlineNotePath = inlineNotePath,
            inlineNoteText = inlineNoteText,
            onToggleFolder = onToggleFolder,
            onNoteClick = onNoteClick,
            onOpenFolder = onOpenFolder,
            onCreateNote = onCreateNote,
            onCreateFolder = onCreateFolder,
            onDeleteLabel = onDeleteLabel,
            onRenameLabel = onRenameLabel,
            onSelectFolder = onSelectFolder,
            onRenameFolder = onRenameFolder,
            onMoveFolder = onMoveFolder,
            onDeleteFolder = onDeleteFolder,
            onRenameNote = onRenameNote,
            onMoveNote = onMoveNote,
            onDeleteNote = onDeleteNote,
            onToggleFavorite = onToggleFavorite,
            onInlineFolderTextChange = onInlineFolderTextChange,
            onInlineFolderDone = onInlineFolderDone,
            onInlineFolderCancel = onInlineFolderCancel,
            onInlineNoteTextChange = onInlineNoteTextChange,
            onInlineNoteDone = onInlineNoteDone,
            onInlineNoteCancel = onInlineNoteCancel,
        )
    }
    rootNotes.forEach { note ->
        FolderNoteItem(
            note = note,
            depth = 0,
            onNoteClick = onNoteClick,
            onRenameNote = onRenameNote,
            onMoveNote = onMoveNote,
            onDeleteNote = onDeleteNote,
            onToggleFavorite = onToggleFavorite,
            onClearFolderSelection = onSelectFolder,
            inline = inlineNotePath == note.file.path,
            inlineText = inlineNoteText,
            onInlineTextChange = onInlineNoteTextChange,
            onInlineDone = onInlineNoteDone,
            onInlineCancel = onInlineNoteCancel,
        )
    }
    drawerFiles
        .filter { parentDrawerFolderPath(normalizeDrawerPath(it.path)).isBlank() }
        .forEach { file ->
            DrawerFileItem(
                file = file,
                depth = 0,
                onClick = { onSelectFolder(null) },
            )
        }
}

private fun normalizeDrawerPath(path: String): String =
    path.replace('\\', '/').trim('/')

private fun collectDrawerFolderPaths(paths: List<String>, notes: List<Note>): Set<String> =
    (paths.asSequence().map(::normalizeDrawerPath) +
        notes.asSequence()
            .filter { !it.isTrashed && !it.isArchived }
            .map { normalizeDrawerPath(it.folder) })
        .filter { it.isNotBlank() }
        .flatMap { path ->
            val parts = path.split("/").filter { it.isNotBlank() }
            parts.indices.asSequence().map { index -> parts.take(index + 1).joinToString("/") }
        }
        .toSet()

private fun normalizedDrawerPaths(
    paths: List<String>,
    notes: List<Note>,
    files: List<DrawerFile> = emptyList(),
): List<String> =
    (paths.map(::normalizeDrawerPath) +
        notes.map { normalizeDrawerPath(it.folder) } +
        files.map { parentDrawerFolderPath(normalizeDrawerPath(it.path)) })
        .filter { it.isNotBlank() }
        .distinct()

private fun buildFolderTree(
    paths: List<String>,
    notes: List<Note>,
    savedOrderFor: (String) -> List<String> = { emptyList() },
    files: List<DrawerFile> = emptyList(),
): List<FolderNode> = buildFolderNodes(
    normalizedPaths = normalizedDrawerPaths(paths, notes, files),
    notes = notes,
    prefix = "",
    savedOrderFor = savedOrderFor,
    files = files,
)

private fun buildFolderNodes(
    normalizedPaths: List<String>,
    notes: List<Note>,
    prefix: String,
    savedOrderFor: (String) -> List<String>,
    files: List<DrawerFile> = emptyList(),
): List<FolderNode> {
    val prefixWithSlash = prefix.takeIf { it.isNotBlank() }?.let { "$it/" }.orEmpty()
    val orderIndex = savedOrderFor(prefix).withIndex().associate { it.value to it.index }
    return normalizedPaths
        .asSequence()
        .filter { it.startsWith(prefixWithSlash) && it != prefix }
        .map { it.removePrefix(prefixWithSlash).substringBefore("/") }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(
            compareBy<String> { name ->
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                orderIndex[path] ?: Int.MAX_VALUE
            }.thenBy { it.lowercase(Locale.getDefault()) },
        )
        .map { name ->
            val path = if (prefix.isBlank()) name else "$prefix/$name"
            FolderNode(
                name = name,
                path = path,
                children = buildFolderNodes(normalizedPaths, notes, path, savedOrderFor, files),
                notes = notes
                    .filter { normalizeDrawerPath(it.folder) == path }
                    .sortedBy { it.title.lowercase(Locale.getDefault()) },
                files = files
                    .filter { parentDrawerFolderPath(normalizeDrawerPath(it.path)) == path }
                    .sortedBy { it.name.lowercase(Locale.getDefault()) },
            )
        }
        .toList()
}

@Composable
private fun FolderTree(
    nodes: List<FolderNode>,
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    collapsedFolders: Set<String>,
    selectedFolderPath: String?,
    inlineFolderPath: String?,
    inlineFolderText: TextFieldValue,
    inlineNotePath: String?,
    inlineNoteText: TextFieldValue,
    onToggleFolder: (String) -> Unit,
    onNoteClick: (Note) -> Unit,
    onOpenFolder: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onRenameFolder: (String) -> Unit,
    onMoveFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRenameNote: (Note) -> Unit,
    onMoveNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onInlineFolderTextChange: (TextFieldValue) -> Unit,
    onInlineFolderDone: () -> Unit,
    onInlineFolderCancel: () -> Unit,
    onInlineNoteTextChange: (TextFieldValue) -> Unit,
    onInlineNoteDone: () -> Unit,
    onInlineNoteCancel: () -> Unit,
    depth: Int = 0,
) {
    val guideIndent = if (LocalKardLeafThemeStyle.current == PrefsManager.AppThemeStyle.CLASSIC) 16 else 12
    nodes.forEach { node ->
        FolderTreeItem(
            node = node,
            depth = depth,
            currentScreen = currentScreen,
            currentFilter = currentFilter,
            collapsedFolders = collapsedFolders,
            selectedFolderPath = selectedFolderPath,
            onToggleFolder = onToggleFolder,
            onCreateNote = onCreateNote,
            onCreateFolder = onCreateFolder,
            onDeleteLabel = onDeleteLabel,
            onRenameLabel = onRenameLabel,
            onSelectFolder = onSelectFolder,
            onOpenFolder = onOpenFolder,
            onRenameFolder = onRenameFolder,
            onMoveFolder = onMoveFolder,
            onDeleteFolder = onDeleteFolder,
            onRenameNote = onRenameNote,
            onMoveNote = onMoveNote,
            onDeleteNote = onDeleteNote,
            onToggleFavorite = onToggleFavorite,
            inline = inlineFolderPath == node.path,
            inlineText = inlineFolderText,
            onInlineTextChange = onInlineFolderTextChange,
            onInlineDone = onInlineFolderDone,
            onInlineCancel = onInlineFolderCancel,
        )
        if (node.path !in collapsedFolders && (node.children.isNotEmpty() || node.notes.isNotEmpty() || node.files.isNotEmpty())) {
            val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            Column(
                modifier = Modifier.drawBehind {
                    val x = (24 + depth * guideIndent).dp.toPx()
                    drawLine(
                        color = guideColor,
                        start = androidx.compose.ui.geometry.Offset(x, 0f),
                        end = androidx.compose.ui.geometry.Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            ) {
                if (node.children.isNotEmpty()) {
                    FolderTree(
                        nodes = node.children,
                        currentScreen = currentScreen,
                        currentFilter = currentFilter,
                        collapsedFolders = collapsedFolders,
                        selectedFolderPath = selectedFolderPath,
                        inlineFolderPath = inlineFolderPath,
                        inlineFolderText = inlineFolderText,
                        inlineNotePath = inlineNotePath,
                        inlineNoteText = inlineNoteText,
                        onToggleFolder = onToggleFolder,
                        onNoteClick = onNoteClick,
                        onOpenFolder = onOpenFolder,
                        onCreateNote = onCreateNote,
                        onCreateFolder = onCreateFolder,
                        onDeleteLabel = onDeleteLabel,
                        onRenameLabel = onRenameLabel,
                        onSelectFolder = onSelectFolder,
                        onRenameFolder = onRenameFolder,
                        onMoveFolder = onMoveFolder,
                        onDeleteFolder = onDeleteFolder,
                        onRenameNote = onRenameNote,
                        onMoveNote = onMoveNote,
                        onDeleteNote = onDeleteNote,
                        onToggleFavorite = onToggleFavorite,
                        onInlineFolderTextChange = onInlineFolderTextChange,
                        onInlineFolderDone = onInlineFolderDone,
                        onInlineFolderCancel = onInlineFolderCancel,
                        onInlineNoteTextChange = onInlineNoteTextChange,
                        onInlineNoteDone = onInlineNoteDone,
                        onInlineNoteCancel = onInlineNoteCancel,
                        depth = depth + 1,
                    )
                }
                node.notes.forEach { note ->
                    FolderNoteItem(
                        note = note,
                        depth = depth + 1,
                        onNoteClick = onNoteClick,
                        onRenameNote = onRenameNote,
                        onMoveNote = onMoveNote,
                        onDeleteNote = onDeleteNote,
                        onToggleFavorite = onToggleFavorite,
                        onClearFolderSelection = onSelectFolder,
                        inline = inlineNotePath == note.file.path,
                        inlineText = inlineNoteText,
                        onInlineTextChange = onInlineNoteTextChange,
                        onInlineDone = onInlineNoteDone,
                        onInlineCancel = onInlineNoteCancel,
                    )
                }
                node.files.forEach { file ->
                    DrawerFileItem(
                        file = file,
                        depth = depth + 1,
                        onClick = { onSelectFolder(null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderNoteItem(
    note: Note,
    depth: Int,
    onNoteClick: (Note) -> Unit,
    onRenameNote: (Note) -> Unit,
    onMoveNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onToggleFavorite: (Note) -> Unit,
    onClearFolderSelection: (String?) -> Unit,
    inline: Boolean,
    inlineText: TextFieldValue,
    onInlineTextChange: (TextFieldValue) -> Unit,
    onInlineDone: () -> Unit,
    onInlineCancel: () -> Unit,
) {
    var showMenu by remember(note.file.path) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        ThemedDrawerItem(
            label = note.title.ifBlank { note.file.nameWithoutExtension },
            icon = null,
            selected = false,
            onClick = {
                if (!inline) {
                    onClearFolderSelection(null)
                    onNoteClick(note)
                }
            },
            onLongClick = if (inline) null else { offset ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                KardLeafLog.d(
                    FILE_TREE_TRACE_TAG,
                    "note long pathHash=${note.file.path.hashCode()} " +
                        "selectionCleared=true xPx=${offset.x}",
                )
                onClearFolderSelection(null)
                showMenu = true
            },
            content = if (inline) {
                {
                    DrawerInlineNameField(
                        value = inlineText,
                        placeholder = null,
                        onValueChange = onInlineTextChange,
                        onDone = onInlineDone,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                null
            },
            modifier = Modifier.padding(start = (24 + depth * 12).dp),
            compact = true,
        )
        DrawerNoteActionMenu(
            expanded = showMenu && !inline,
            isFavorite = note.isFavorite,
            onDismiss = {
                KardLeafLog.d(FILE_TREE_TRACE_TAG, "note menu dismiss pathHash=${note.file.path.hashCode()}")
                showMenu = false
            },
            onRename = {
                showMenu = false
                onRenameNote(note)
            },
            onMove = {
                showMenu = false
                onMoveNote(note)
            },
            onDelete = {
                KardLeafLog.d(FILE_TREE_TRACE_TAG, "note action=delete pathHash=${note.file.path.hashCode()}")
                showMenu = false
                onDeleteNote(note)
            },
            onToggleFavorite = {
                showMenu = false
                onToggleFavorite(note)
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DrawerInlineNameField(
    value: TextFieldValue,
    placeholder: String?,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        bringIntoViewRequester.bringIntoView()
        keyboardController?.show()
        snapshotFlow { imeInsets.getBottom(density) }
            .filter { it > 0 }
            .collectLatest {
                bringIntoViewRequester.bringIntoView()
            }
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(focusRequester),
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (value.text.isBlank() && placeholder != null) {
                    Text(
                        text = placeholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun DrawerFileItem(
    file: DrawerFile,
    depth: Int,
    onClick: () -> Unit,
) {
    ThemedDrawerItem(
        label = file.name,
        icon = Icons.Outlined.Description,
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(start = (24 + depth * 12).dp),
        compact = true,
    )
}

@Composable
private fun DrawerFolderActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpenFolder: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onCreateNote: () -> Unit,
    onCreateFolder: () -> Unit,
) {
    FileTreeActionPopup(expanded = expanded, onDismiss = onDismiss) {
        FileTreeToolbarIconButton(onClick = {
            onDismiss()
            onOpenFolder()
        }) {
            Icon(Icons.Outlined.OpenInNew, contentDescription = "首页打开")
        }
        FileTreeToolbarIconButton(onClick = {
            onDismiss()
            onRename()
        }) {
            Icon(Icons.Outlined.Edit, contentDescription = "重命名")
        }
        FileTreeToolbarIconButton(onClick = {
            onDismiss()
            onMove()
        }) {
            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "移动")
        }
        FileTreeToolbarIconButton(onClick = {
            onDismiss()
            onDelete()
        }) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除")
        }
        FileTreeToolbarIconButton(onClick = {
            onDismiss()
            onCreateNote()
        }) {
            Icon(
                painter = painterResource(R.drawable.ic_file_tree_new_note),
                contentDescription = "新增文件",
                tint = Color.Unspecified,
            )
        }
        FileTreeToolbarIconButton(onClick = {
            onDismiss()
            onCreateFolder()
        }) {
            Icon(
                painter = painterResource(R.drawable.ic_file_tree_new_folder),
                contentDescription = "新增文件夹",
                tint = Color.Unspecified,
            )
        }
    }
}

@Composable
private fun DrawerNoteActionMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    FileTreeActionPopup(expanded = expanded, onDismiss = onDismiss) {
        FileTreeToolbarIconButton(onClick = onRename) {
            Icon(Icons.Outlined.Edit, contentDescription = "重命名")
        }
        FileTreeToolbarIconButton(onClick = onMove) {
            Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = "移动")
        }
        FileTreeToolbarIconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除")
        }
        FileTreeToolbarIconButton(onClick = onToggleFavorite) {
            Icon(
                Icons.Outlined.StarBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
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
    inline: Boolean,
    inlineText: TextFieldValue,
    onInlineTextChange: (TextFieldValue) -> Unit,
    onInlineDone: () -> Unit,
    onInlineCancel: () -> Unit,
    onToggleFolder: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onRenameLabel: (String, String) -> Unit,
    onSelectFolder: (String?) -> Unit,
    onOpenFolder: (String) -> Unit,
    onRenameFolder: (String) -> Unit,
    onMoveFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRenameNote: (Note) -> Unit,
    onMoveNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onToggleFavorite: (Note) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isModern = LocalKardLeafThemeStyle.current != PrefsManager.AppThemeStyle.CLASSIC
    val isFilterSelected = currentScreen is MainViewModel.Screen.Dashboard && (currentFilter as? MainViewModel.NoteFilter.Label)?.name == node.path
    val isActionSelected = selectedFolderPath == node.path
    val isSelected = isFilterSelected || isActionSelected
    val canExpand = true
    val isCollapsed = node.path in collapsedFolders
    val canManage = node.path.split('/').none { it == ".KardLeaf" }

    val folderClick: () -> Unit = {
        KardLeafLog.d(
            FILE_TREE_TRACE_TAG,
            "folder tap pathHash=${node.path.hashCode()} selectedHash=${selectedFolderPath?.hashCode()} inline=$inline",
        )
        if (!inline && selectedFolderPath != null) onSelectFolder(null)
        if (!inline && canExpand) onToggleFolder(node.path)
    }
    val folderLongClick: (Offset) -> Unit = { offset ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        KardLeafLog.d(
            FILE_TREE_TRACE_TAG,
            "folder long pathHash=${node.path.hashCode()} " +
                "selectedBeforeHash=${selectedFolderPath?.hashCode()} xPx=${offset.x}",
        )
        onSelectFolder(node.path)
    }
    val dismissFolderMenu = {
        KardLeafLog.d(
            FILE_TREE_TRACE_TAG,
            "folder menu dismiss pathHash=${node.path.hashCode()} selectedHash=${selectedFolderPath?.hashCode()}",
        )
        onSelectFolder(null)
    }
    val folderMenu: @Composable () -> Unit = {
        DrawerFolderActionMenu(
            expanded = isActionSelected && canManage,
            onDismiss = dismissFolderMenu,
            onOpenFolder = { onOpenFolder(node.path) },
            onRename = { onRenameFolder(node.path) },
            onMove = { onMoveFolder(node.path) },
            onDelete = {
                KardLeafLog.d(FILE_TREE_TRACE_TAG, "folder action=delete pathHash=${node.path.hashCode()}")
                onDeleteFolder(node.path)
            },
            onCreateNote = { onCreateNote(node.path) },
            onCreateFolder = { onCreateFolder(node.path) },
        )
    }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 90f,
        label = "DrawerFolderChevron",
    )

    if (!isModern) {
        Box {
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
                        .then(
                            if (!inline) {
                                Modifier.pointerInput(node.path) {
                                    detectTapGestures(
                                        onTap = { folderClick() },
                                        onLongPress = folderLongClick,
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                        .padding(start = 16.dp + (depth * 16).dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (canExpand) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = if (isCollapsed) "展开文件夹" else "折叠文件夹",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                Spacer(modifier = Modifier.width(8.dp))
                if (inline) {
                    DrawerInlineNameField(
                        value = inlineText,
                        placeholder = "未命名".takeIf { node.name == INLINE_NEW_FOLDER_PATH },
                        onValueChange = onInlineTextChange,
                        onDone = onInlineDone,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (isActionSelected && !inline) folderMenu()
        }
        return
    }

    val folderShape = RoundedCornerShape(20.dp)
    val backgroundColor =
        if (isSelected) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
        } else {
            Color.Transparent
        }
    val borderColor = Color.Transparent
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp + (depth * 12).dp, end = 12.dp, top = 3.dp, bottom = 3.dp)
                .clip(folderShape)
                .background(backgroundColor)
                .border(1.dp, borderColor, folderShape)
                .height(42.dp)
                .then(
                    if (!inline) {
                        Modifier.pointerInput(node.path) {
                            detectTapGestures(
                                onTap = { folderClick() },
                                onLongPress = folderLongClick,
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (canExpand) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = if (isCollapsed) "展开文件夹" else "折叠文件夹",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Spacer(modifier = Modifier.width(8.dp))
            if (inline) {
                DrawerInlineNameField(
                    value = inlineText,
                    placeholder = "未命名".takeIf { node.name == INLINE_NEW_FOLDER_PATH },
                    onValueChange = onInlineTextChange,
                    onDone = onInlineDone,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (isActionSelected && !inline) folderMenu()
    }
}
