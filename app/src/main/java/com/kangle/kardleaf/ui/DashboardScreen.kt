package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import java.io.File
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

private const val STARTUP_PERF_TRACE_TAG = "KardLeafStartupPerf"
private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val MENU_REOPEN_GUARD_MS = 250L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    isDrawerOpen: Boolean,
    onSelectFolder: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onFabClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    edgeDrawerWidthPx: Float = 0f,
    onBackFromTemporaryFilter: (MainViewModel.NoteFilter) -> Boolean = { false },
) {
    val notes by viewModel.notes.collectAsState()
    val uiItems by viewModel.uiItems.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState(initial = emptyList())
    val labels by viewModel.labels.collectAsState()
    val isPermissionNeeded by viewModel.isPermissionNeeded.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val folderSortSettings by viewModel.currentFolderSortSettings.collectAsState()
    val cardDensity by viewModel.cardDensity.collectAsState()
    val showYamlTagsOnLooseCards by viewModel.showYamlTagsOnLooseCards.collectAsState()
    val yamlTags by viewModel.yamlTags.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val scrollToTopEvents by viewModel.homeScrollToTopEvents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val effectiveSortOrder = folderSortSettings?.order ?: sortOrder
    val effectiveSortDirection = folderSortSettings?.direction ?: sortDirection
    val listStates = remember { mutableMapOf<MainViewModel.NoteFilter, LazyStaggeredGridState>() }
    val listState = remember(currentFilter) {
        listStates.getOrPut(currentFilter) { LazyStaggeredGridState() }
    }
    val dashboardStartMs = remember { SystemClock.elapsedRealtime() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showUndoSnackbar(message: String) {
        coroutineScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "撤回",
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastNoteAction()
            }
        }
    }

    LaunchedEffect(Unit) {
        Log.d(STARTUP_PERF_TRACE_TAG, "dashboard compose enter filter=$currentFilter")
    }

    LaunchedEffect(currentFilter, notes.size, uiItems.size, allNotes.size, labels.size, isLoading, viewMode, cardDensity) {
        Log.d(
            STARTUP_PERF_TRACE_TAG,
            "dashboard state elapsed=${SystemClock.elapsedRealtime() - dashboardStartMs}ms filter=$currentFilter " +
                "notes=${notes.size} uiItems=${uiItems.size} all=${allNotes.size} labels=${labels.size} " +
                "loading=$isLoading viewMode=$viewMode cardDensity=$cardDensity",
        )
    }

    // UI State
    var showCreateLabelDialog by remember { mutableStateOf(false) }
    var showCreateSubfolderDialog by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var labelToDelete by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showPullRefreshCircle by remember { mutableStateOf(false) }
    var showFolderNavigationPanel by remember { mutableStateOf(false) }
    var folderNavigationPanelProgress by remember { mutableStateOf(0f) }
    var previewDashboardTitlePath by remember { mutableStateOf<String?>(null) }
    var showQuickCreateActions by remember { mutableStateOf(false) }

    if (showCreateLabelDialog) {
        CreateLabelDialog(
            onDismiss = { showCreateLabelDialog = false },
            onConfirm = { name ->
                viewModel.createLabel(name)
                showCreateLabelDialog = false
            },
        )
    }

    if (showCreateSubfolderDialog) {
        CreateLabelDialog(
            onDismiss = { showCreateSubfolderDialog = false },
            onConfirm = { name ->
                val parent = (currentFilter as? MainViewModel.NoteFilter.Label)?.name.orEmpty()
                val childPath =
                    listOf(parent, name.trim())
                        .filter { it.isNotBlank() }
                        .joinToString("/")
                if (childPath.isNotBlank()) {
                    viewModel.createLabel(childPath)
                    viewModel.setFilter(MainViewModel.NoteFilter.Label(childPath))
                }
                showCreateSubfolderDialog = false
            },
        )
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text(stringResource(R.string.empty_trash_title)) },
            text = { Text(stringResource(R.string.empty_trash_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyTrashDialog = false
                }) {
                    Text(stringResource(R.string.empty_trash_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (labelToDelete != null) {
        AlertDialog(
            onDismissRequest = { labelToDelete = null },
            title = { Text(stringResource(R.string.delete_label_title)) },
            text = { Text(stringResource(R.string.delete_label_message, labelToDelete!!)) },
            confirmButton = {
                TextButton(onClick = {
                    val name = labelToDelete!!
                    viewModel.deleteLabel(
                        name = name,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.label_deleted_toast), Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            val localizedError =
                                if (error == "Label must be empty to delete it") {
                                    context.getString(R.string.error_delete_label_not_empty)
                                } else {
                                    error
                                }
                            Toast.makeText(context, localizedError, Toast.LENGTH_SHORT).show()
                        },
                    )
                    labelToDelete = null
                }) {
                    Text(stringResource(R.string.delete_label_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { labelToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Selection State
    val selectedNotes by viewModel.selectedNotes.collectAsState()
    val isInSelectionMode = selectedNotes.isNotEmpty()

    val selectedNotesList = notes.filter { selectedNotes.contains(it.file.path) }
    val allSelectedArchived = selectedNotesList.isNotEmpty() && selectedNotesList.all { it.isArchived }
    val allSelectedActive = selectedNotesList.isNotEmpty() && selectedNotesList.all { !it.isArchived && !it.isTrashed }
    val allSelectedFavorite = selectedNotesList.isNotEmpty() && selectedNotesList.all { it.isFavorite }
    var propertyNote by remember { mutableStateOf<Note?>(null) }

    propertyNote?.let { note ->
        NotePropertiesDialog(
            note = note,
            onDismiss = { propertyNote = null },
        )
    }

    LaunchedEffect(sortOrder, sortDirection) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(scrollToTopEvents) {
        if (scrollToTopEvents > 0) {
            delay(250L)
            repeat(3) {
                listState.scrollToItem(0, 0)
                delay(120L)
            }
        }
    }

    // Double back to exit
    var lastBackPressTime by remember { mutableStateOf(0L) }

    BackHandler(enabled = !isDrawerOpen) {
        Log.d(
            BACK_TRACE_TAG,
            "Dashboard root BackHandler hit drawerOpen=$isDrawerOpen showSearch=$showSearch " +
                "folderPanel=$showFolderNavigationPanel quickCreate=$showQuickCreateActions " +
                "selectionMode=$isInSelectionMode filter=$currentFilter propertyDialog=${propertyNote != null}",
        )
        when {
            showCreateLabelDialog -> showCreateLabelDialog = false
            showCreateSubfolderDialog -> showCreateSubfolderDialog = false
            showEmptyTrashDialog -> showEmptyTrashDialog = false
            labelToDelete != null -> labelToDelete = null
            propertyNote != null -> propertyNote = null
            showFolderNavigationPanel -> {
                folderNavigationPanelProgress = 0f
                showFolderNavigationPanel = false
            }
            showQuickCreateActions -> showQuickCreateActions = false
            showSearch -> {
                showSearch = false
                viewModel.onSearchQueryChanged("")
            }
            isInSelectionMode -> viewModel.clearSelection()
            viewModel.navigateUpFolder() -> Unit
            currentFilter != MainViewModel.NoteFilter.All -> {
                if (!onBackFromTemporaryFilter(currentFilter)) {
                    viewModel.setFilter(MainViewModel.NoteFilter.All)
                }
            }
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(context, context.getString(R.string.press_back_again_exit), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isInSelectionMode,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                SelectionTopAppBar(
                    selectionCount = selectedNotes.size,
                    currentFilter = currentFilter,
                    allSelectedArchived = allSelectedArchived,
                    allSelectedActive = allSelectedActive,
                    allSelectedFavorite = allSelectedFavorite,
                    onClearSelection = { viewModel.clearSelection() },
                    onDelete = {
                        viewModel.deleteSelectedNotes()
                        showUndoSnackbar("已移动到废弃")
                    },
                    onArchive = {
                        viewModel.archiveSelectedNotes()
                        showUndoSnackbar("已归档")
                    },
                    onRestore = { viewModel.restoreSelectedNotes() },
                    onMove = { targetLabel ->
                        viewModel.moveSelectedNotes(targetLabel, selectedNotesList)
                        showUndoSnackbar("已移动")
                    },
                    onPin = { viewModel.togglePinSelectedNotes() },
                    onFavorite = { viewModel.toggleFavoriteSelectedNotes() },
                    availableLabels = labels,
                    selectedNoteForProperties = selectedNotesList.singleOrNull(),
                    selectedNotesForTags = selectedNotesList,
                    availableYamlTags = yamlTags,
                    onApplyTags = { tags ->
                        viewModel.addTagsToSelectedNotes(tags) {
                            Toast.makeText(context, "已更新标签", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShowProperties = { propertyNote = it },
                    onShare = {
                        shareSelectedNotes(context, selectedNotesList)
                    },
                    onMoveToPrivacy = {
                        viewModel.moveSelectedNotesToPrivacy { count ->
                            Toast.makeText(
                                context,
                                if (count > 0) "已移动到隐私库" else "移动到隐私库失败",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = !isInSelectionMode,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TopAppBar(
                    title = {
                        if (showSearch) {
                            Surface(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp,
                            ) {
                                SearchBar(viewModel = viewModel)
                            }
                        } else {
                            Text(
                                text = previewDashboardTitlePath?.let(::dashboardTitleForPath) ?: dashboardTitle(currentFilter),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            showSearch = !showSearch
                            focusManager.clearFocus()
                        }) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "搜索",
                                tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        SortButton(viewModel = viewModel)
                        if (currentFilter is MainViewModel.NoteFilter.Trash) {
                            var showMoreMenu by remember { mutableStateOf(false) }
                            var lastMoreMenuDismissAt by remember { mutableStateOf(0L) }
                            LaunchedEffect(showMoreMenu) {
                                Log.d(BACK_TRACE_TAG, "Dashboard trash more state changed showMoreMenu=$showMoreMenu")
                            }
                            Box {
                                IconButton(onClick = {
                                    val now = SystemClock.uptimeMillis()
                                    val ignoreReopen = !showMoreMenu && now - lastMoreMenuDismissAt < MENU_REOPEN_GUARD_MS
                                    Log.d(BACK_TRACE_TAG, "Dashboard trash more click toggle menu filter=$currentFilter showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                                    if (!ignoreReopen) {
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
                                                    "Dashboard trash more popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoreMenu=$showMoreMenu",
                                                )
                                            }
                                            false
                                        },
                                    expanded = showMoreMenu,
                                    onDismissRequest = {
                                        Log.d(BACK_TRACE_TAG, "Dashboard trash more onDismissRequest showMoreMenu=$showMoreMenu")
                                        lastMoreMenuDismissAt = SystemClock.uptimeMillis()
                                        showMoreMenu = false
                                    },
                                    properties = PopupProperties(
                                        focusable = true,
                                        dismissOnBackPress = true,
                                        dismissOnClickOutside = true,
                                    ),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.empty_trash_desc)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            showEmptyTrashDialog = true
                                            showMoreMenu = false
                                        },
                                    )
                                }
                                BackHandler(enabled = showMoreMenu) {
                                    Log.d(BACK_TRACE_TAG, "Dashboard trash more BackHandler hit, closing menu")
                                    showMoreMenu = false
                                }
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (!isPermissionNeeded &&
                currentFilter !is MainViewModel.NoteFilter.Trash &&
                currentFilter !is MainViewModel.NoteFilter.Archive
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AnimatedVisibility(
                        visible = showQuickCreateActions,
                        enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(),
                        exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HomeFabIconButton(
                                icon = Icons.Outlined.Description,
                                contentDescription = "新建草稿",
                                onSwipeDown = { showQuickCreateActions = false },
                                onClick = {
                                    showQuickCreateActions = false
                                    viewModel.createTemporaryNote(source = "dashboard_quick_draft")
                                },
                            )
                            HomeFabIconButton(
                                icon = Icons.Outlined.CreateNewFolder,
                                contentDescription = "新建子文件夹",
                                onSwipeDown = { showQuickCreateActions = false },
                                onClick = {
                                    showQuickCreateActions = false
                                    showCreateSubfolderDialog = true
                                },
                            )
                        }
                    }
                    Surface(
                        modifier =
                            Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .pointerInput(showQuickCreateActions) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        when {
                                            dragAmount < 0f && !showQuickCreateActions -> {
                                                showQuickCreateActions = true
                                                change.consume()
                                            }
                                            dragAmount > 0f && showQuickCreateActions -> {
                                                showQuickCreateActions = false
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                                .clickable {
                                    showQuickCreateActions = false
                                    onFabClick()
                                },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(R.string.add_note),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .pointerInput(showSearch) {
                        if (showSearch) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                focusManager.clearFocus()
                            }
                        }
                    },
        ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 分类标签栏和横向 Pager 共享同一组页面；标签栏高光只跟随 currentFilter。
            val folderPagerPages = remember(labels, currentFilter) {
                buildFolderPagerPages(labels, currentFilter)
            }
            val folderPagerState = rememberPagerState(pageCount = { folderPagerPages.size })
            val useFolderPager =
                (currentFilter is MainViewModel.NoteFilter.All ||
                    currentFilter is MainViewModel.NoteFilter.Label) &&
                    searchQuery.isBlank() &&
                    !isInSelectionMode &&
                    folderPagerPages.isNotEmpty()
            var isFolderPagerVerticalGestureLocked by remember { mutableStateOf(false) }
            LaunchedEffect(useFolderPager) {
                if (!useFolderPager) {
                    isFolderPagerVerticalGestureLocked = false
                }
            }
            val currentFolderPath = (currentFilter as? MainViewModel.NoteFilter.Label)?.name
                ?.let(::normalizeFolderPathForUi)
                .orEmpty()
            val pullRefreshListState =
                if (useFolderPager && currentFolderPath.isNotEmpty()) {
                    listStates.getOrPut(MainViewModel.NoteFilter.Label(currentFolderPath)) { LazyStaggeredGridState() }
                } else {
                    listState
                }
            var isProgrammaticPagerSync by remember { mutableStateOf(false) }
            val previewFolderPath =
                if (folderPagerState.isScrollInProgress && !isProgrammaticPagerSync) {
                    folderPagerPages.getOrNull(folderPagerState.currentPage)?.path
                        ?: currentFolderPath
                } else {
                    currentFolderPath
                }
            LaunchedEffect(previewFolderPath, folderPagerState.isScrollInProgress, isProgrammaticPagerSync) {
                previewDashboardTitlePath = if (folderPagerState.isScrollInProgress && !isProgrammaticPagerSync) {
                    previewFolderPath
                } else {
                    null
                }
            }
            if (!isPermissionNeeded &&
                currentFilter !is MainViewModel.NoteFilter.Trash &&
                currentFilter !is MainViewModel.NoteFilter.Archive &&
                currentFilter !is MainViewModel.NoteFilter.Recent &&
                currentFilter !is MainViewModel.NoteFilter.Favorites &&
                currentFilter !is MainViewModel.NoteFilter.Drafts &&
                currentFilter !is MainViewModel.NoteFilter.YamlTag &&
                labels.isNotEmpty()
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(edgeDrawerWidthPx) {
                                // 左侧抽屉响应区域内，水平拖拽只交给侧边栏。
                                // 在 Initial 阶段消费 move，避免下面的 LazyRow / Pager 同时收到这次滑动。
                                if (edgeDrawerWidthPx <= 0f) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    if (down.position.x >= edgeDrawerWidthPx) return@awaitEachGesture

                                    var pointerPressed = true
                                    var isDrawerEdgeDrag = false
                                    while (pointerPressed) {
                                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        pointerPressed = change?.pressed == true
                                        if (change != null && pointerPressed) {
                                            val dx = kotlin.math.abs(change.position.x - down.position.x)
                                            val dy = kotlin.math.abs(change.position.y - down.position.y)
                                            if (!isDrawerEdgeDrag && dx > 1f && dx >= dy) {
                                                isDrawerEdgeDrag = true
                                            }
                                            if (isDrawerEdgeDrag) {
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(labels, currentFilter, edgeDrawerWidthPx) {
                                val startDistancePx = 8.dp.toPx()
                                val triggerDistancePx = 56.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    if (edgeDrawerWidthPx > 0f && down.position.x < edgeDrawerWidthPx) {
                                        return@awaitEachGesture
                                    }
                                    var pointerPressed = true
                                    var startedPanelDrag = false
                                    folderNavigationPanelProgress = 0f
                                    while (pointerPressed) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        pointerPressed = change?.pressed == true
                                        if (change != null && pointerPressed) {
                                            val dx = kotlin.math.abs(change.position.x - down.position.x)
                                            val dy = change.position.y - down.position.y
                                            if (dy > startDistancePx && dy > dx * 1.2f) {
                                                change.consume()
                                                startedPanelDrag = true
                                                showFolderNavigationPanel = true
                                                folderNavigationPanelProgress = (dy / triggerDistancePx).coerceIn(0f, 1f)
                                                if (dy > triggerDistancePx && dy > dx * 1.4f) {
                                                    folderNavigationPanelProgress = 1f
                                                    pointerPressed = false
                                                }
                                            }
                                        }
                                    }
                                    if (startedPanelDrag && folderNavigationPanelProgress < 1f) {
                                        folderNavigationPanelProgress = 0f
                                        showFolderNavigationPanel = false
                                    }
                                }
                            },
                ) {
                    FolderPathStrip(
                        currentFilter = currentFilter,
                        labels = labels,
                        previewPath = previewFolderPath,
                        onOpenFolder = { folder ->
                            val filter = MainViewModel.NoteFilter.Label(folder)
                            if (currentFilter != filter) {
                                viewModel.setFilter(filter)
                            }
                        },
                        onShowAllInFolder = { folder ->
                            viewModel.showAllInFolder(folder)
                        },
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
            ) {
                if (isPermissionNeeded) {
                    PermissionRequestState(
                        onSelectFolder = onSelectFolder,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    val pullRefreshState = rememberPullToRefreshState()
                    if (pullRefreshState.isRefreshing) {
                        LaunchedEffect(true) {
                            viewModel.refreshNotes()
                        }
                    }

                    LaunchedEffect(isLoading) {
                        if (!isLoading) {
                            pullRefreshState.endRefresh()
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .nestedScroll(pullRefreshState.nestedScrollConnection)
                                .pointerInput(isLoading, pullRefreshListState) {
                                    val pullIndicatorDistancePx = with(density) { 28.dp.toPx() }
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var pointerPressed = true
                                        showPullRefreshCircle = false
                                        var isVerticalPull = false
                                        while (pointerPressed) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            pointerPressed = change?.pressed == true
                                            if (change != null && pointerPressed) {
                                                val dx = kotlin.math.abs(change.position.x - down.position.x)
                                                val dy = change.position.y - down.position.y
                                                // 纯垂直下拉（dy>0 且 dy>dx）时不消费 move，
                                                // 让 Material3 PullToRefresh 的 nestedScroll 正常累积下拉距离、
                                                // 在合理距离（远小于半个屏幕）触发刷新。
                                                // 仅当垂直下拉伴随明显水平偏移（dx > dy*0.6）时才消费，
                                                // 阻止 HorizontalPager 误触切换页面（空列表场景）。
                                                if (!isVerticalPull && dy > 0f && dy > dx) {
                                                    isVerticalPull = true
                                                }
                                                if (isVerticalPull && dx > dy * 0.6f) {
                                                    change.consume()
                                                }
                                                val isAtPullRefreshTop =
                                                    pullRefreshListState.firstVisibleItemIndex == 0 &&
                                                        pullRefreshListState.firstVisibleItemScrollOffset == 0
                                                if (!isAtPullRefreshTop) {
                                                    showPullRefreshCircle = false
                                                }
                                                if (!isLoading &&
                                                    pointerPressed &&
                                                    isAtPullRefreshTop &&
                                                    dy > pullIndicatorDistancePx &&
                                                    dy > dx * 1.2f
                                                ) {
                                                    showPullRefreshCircle = true
                                                }
                                            }
                                        }
                                        showPullRefreshCircle = false
                                    }
                                }
                                .pointerInput(edgeDrawerWidthPx) {
                                    // 拦截边缘区域的水平拖拽，防止被 HorizontalPager 消费。
                                    // 必须在 Initial 阶段消费，否则 Pager 可能先收到 move 并开始切换分类。
                                    if (edgeDrawerWidthPx <= 0f) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        )
                                        if (down.position.x >= edgeDrawerWidthPx) return@awaitEachGesture
                                        // 不消费 down 事件，让点击仍可穿透到卡片
                                        var pointerPressed = true
                                        var isHorizontalDrag = false
                                        while (pointerPressed) {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            pointerPressed = change?.pressed == true
                                            if (change != null && pointerPressed) {
                                                val dx = kotlin.math.abs(change.position.x - down.position.x)
                                                val dy = kotlin.math.abs(change.position.y - down.position.y)
                                                // 边缘区域内，只要水平位移略大于垂直（dx > dy 且 dx > 1f）
                                                // 立即标记为水平拖拽并消费后续所有 move。
                                                if (!isHorizontalDrag && dx > dy && dx > 1f) {
                                                    isHorizontalDrag = true
                                                }
                                                if (isHorizontalDrag) {
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                                .pointerInput(useFolderPager, edgeDrawerWidthPx) {
                                    // 首页分类 Pager 的横纵方向锁定层：
                                    // - 纵向手势只交给列表滚动 / 下拉刷新，不让 Pager 参与；
                                    // - 横向手势继续交给 HorizontalPager 自己处理，保留原来的滑动体验；
                                    // - 左侧边缘区域跳过，避免影响侧边栏手势。
                                    if (!useFolderPager) {
                                        isFolderPagerVerticalGestureLocked = false
                                        return@pointerInput
                                    }
                                    awaitEachGesture {
                                        isFolderPagerVerticalGestureLocked = false
                                        val down = awaitFirstDown(
                                            requireUnconsumed = false,
                                            pass = PointerEventPass.Initial,
                                        )
                                        if (edgeDrawerWidthPx > 0f && down.position.x < edgeDrawerWidthPx) {
                                            return@awaitEachGesture
                                        }

                                        val touchSlop = viewConfiguration.touchSlop
                                        var pointerPressed = true
                                        var lockedVertical = false
                                        var lockedHorizontal = false
                                        while (pointerPressed) {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            pointerPressed = change?.pressed == true
                                            if (change != null && pointerPressed) {
                                                val dx = kotlin.math.abs(change.position.x - down.position.x)
                                                val dy = kotlin.math.abs(change.position.y - down.position.y)
                                                if (!lockedVertical && !lockedHorizontal &&
                                                    (dx > touchSlop || dy > touchSlop)
                                                ) {
                                                    when {
                                                        dy >= dx * 1.2f -> {
                                                            lockedVertical = true
                                                            isFolderPagerVerticalGestureLocked = true
                                                        }
                                                        dx > dy * 1.35f -> {
                                                            lockedHorizontal = true
                                                            isFolderPagerVerticalGestureLocked = false
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        isFolderPagerVerticalGestureLocked = false
                                    }
                                }
                                .clipToBounds(),
                    ) {
                        val currentPageIndex = folderPagerPages
                            .indexOfFirst { it.path == currentFolderPath }
                            .coerceAtLeast(0)
                        val folderPagerKey = folderPagerPages.joinToString("|") { it.path }
                        // 用 rememberUpdatedState 持有最新值：第二个 effect 不以 folderPagerKey/currentFolderPath
                        // 作为 key，pages 重建时 effect 不会重启，从而避免用旧 settledPage 在新 pages 里
                        // 取到错误 target 回写 setFilter（这正是点击全部笔记/返回/点标签失效的根因）。
                        val folderPagerPagesUpdated = rememberUpdatedState(folderPagerPages)
                        val currentFolderPathUpdated = rememberUpdatedState(currentFolderPath)

                        // 第一个 effect：外部筛选 → pager（瞬时同步）。
                        // 点击全部笔记/返回/点标签时 currentFilter 变化 → pages 重建 → folderPagerKey 变化
                        // → 此 effect 重启 → scrollToPage 瞬时跳到正确页，立即响应。
                        LaunchedEffect(currentPageIndex, folderPagerKey) {
                            if (folderPagerPages.isNotEmpty() && folderPagerState.currentPage != currentPageIndex) {
                                isProgrammaticPagerSync = true
                                try {
                                    folderPagerState.scrollToPage(currentPageIndex)
                                } finally {
                                    isProgrammaticPagerSync = false
                                }
                            }
                        }

                        // 第二个 effect：pager 手势滑动 → 外部筛选（仅在页面完全吸附后回写）。
                        // 用 settledPage 而非 currentPage：settledPage 只在滑动停止并吸附后更新，
                        // 滑动过程中不触发 setFilter，因此 pages 不会在中途重建，pager 不会卡在两页中间。
                        // key 只含 folderPagerState（稳定引用），不含 folderPagerKey/currentFolderPath，
                        // pages 重建不会重启本 effect，避免旧 settledPage 错误回写抵消外部切换。
                        LaunchedEffect(folderPagerState) {
                            snapshotFlow { folderPagerState.settledPage }
                                .distinctUntilChanged()
                                .collect { page ->
                                    val pages = folderPagerPagesUpdated.value
                                    val target = pages.getOrNull(page) ?: return@collect
                                    val currentPath = currentFolderPathUpdated.value
                                    if (target.path != currentPath) {
                                        if (target.path.isEmpty()) {
                                            viewModel.setFilter(MainViewModel.NoteFilter.All)
                                        } else {
                                            viewModel.setFilter(MainViewModel.NoteFilter.Label(target.path))
                                        }
                                    }
                                }
                        }

                        if (useFolderPager) {
                            HorizontalPager(
                                state = folderPagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = !isFolderPagerVerticalGestureLocked,
                                key = { page -> folderPagerPages[page].path },
                            ) { page ->
                                val pagePath = folderPagerPages[page].path
                                val isRootPage = pagePath.isEmpty()
                                if (isRootPage) {
                                    // 渲染 "全部笔记" 根页面（复用 uiItems）
                                    NoteGrid(
                                        uiItems = uiItems,
                                        selectedNotes = selectedNotes,
                                        isLoading = isLoading,
                                        notesCount = notes.size,
                                        viewMode = viewMode,
                                        cardDensity = cardDensity,
                                        showFolderTags = currentFilter is MainViewModel.NoteFilter.All || currentFilter is MainViewModel.NoteFilter.Favorites,
                                        showYamlTags = showYamlTagsOnLooseCards,
                                        searchQuery = searchQuery,
                                        listState = listState,
                                        loadImageThumbnail = { note -> viewModel.resolveNoteThumbnailBitmap(note) },
                                        onNoteClick = { note ->
                                            if (isInSelectionMode) {
                                                viewModel.toggleSelection(note)
                                            } else {
                                                onNoteClick(note)
                                            }
                                        },
                                        onNoteLongClick = { note ->
                                            viewModel.toggleSelection(note)
                                        },
                                    )
                                } else {
                                    val isCurrentPage = pagePath == currentFolderPath
                                    val isRecursive =
                                        (currentFilter as? MainViewModel.NoteFilter.Label)?.recursive == true
                                    val preciseItems =
                                        remember(
                                            allNotes,
                                            pagePath,
                                            effectiveSortOrder,
                                            effectiveSortDirection,
                                        ) {
                                            buildGesturePreviewItems(
                                                notes = allNotes,
                                                folder = pagePath,
                                                sortOrder = effectiveSortOrder,
                                                sortDirection = effectiveSortDirection,
                                            )
                                        }
                                    // recursive 模式下当前页显示该文件夹及全部子文件夹的笔记（复用 uiItems），
                                    // 其他页仍用精确匹配的预览项
                                    val pageItems =
                                        if (isCurrentPage && isRecursive) uiItems else preciseItems
                                    val pageFilter = remember(pagePath) { MainViewModel.NoteFilter.Label(pagePath) }
                                    val pageListState = remember(pageFilter) {
                                        listStates.getOrPut(pageFilter) { LazyStaggeredGridState() }
                                    }

                                    NoteGrid(
                                        uiItems = pageItems,
                                        selectedNotes = selectedNotes,
                                        isLoading = isLoading && isCurrentPage,
                                        notesCount =
                                            if (isCurrentPage && isRecursive) {
                                                notes.size
                                            } else {
                                                pageItems.count { it is DashboardUiItem.NoteItem }
                                            },
                                        viewMode = viewMode,
                                        cardDensity = cardDensity,
                                        showFolderTags = isCurrentPage && isRecursive,
                                        showYamlTags = showYamlTagsOnLooseCards,
                                        searchQuery = searchQuery,
                                        listState = pageListState,
                                        loadImageThumbnail = { note -> viewModel.resolveNoteThumbnailBitmap(note) },
                                        onNoteClick = { note ->
                                            if (isInSelectionMode) {
                                                viewModel.toggleSelection(note)
                                            } else {
                                                onNoteClick(note)
                                            }
                                        },
                                        onNoteLongClick = { note ->
                                            viewModel.toggleSelection(note)
                                        },
                                    )
                                }
                            }
                        } else {
                            NoteGrid(
                                uiItems = uiItems,
                                selectedNotes = selectedNotes,
                                isLoading = isLoading,
                                notesCount = notes.size,
                                viewMode = viewMode,
                                cardDensity = cardDensity,
                                showFolderTags = currentFilter is MainViewModel.NoteFilter.All || currentFilter is MainViewModel.NoteFilter.Favorites,
                                showYamlTags = showYamlTagsOnLooseCards,
                                searchQuery = searchQuery,
                                listState = listState,
                                loadImageThumbnail = { note -> viewModel.resolveNoteThumbnailBitmap(note) },
                                onNoteClick = { note ->
                                    if (isInSelectionMode) {
                                        viewModel.toggleSelection(note)
                                    } else {
                                        onNoteClick(note)
                                    }
                                },
                                onNoteLongClick = { note ->
                                    viewModel.toggleSelection(note)
                                },
                            )
                        }

                        if (showPullRefreshCircle &&
                            !pullRefreshState.isRefreshing &&
                            !isLoading
                        ) {
                            // 外层白色圆圈 + 内层黑色加载指示器，白色外圈加阴影增强可见性
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 8.dp)
                                        .size(36.dp)
                                        .shadow(4.dp, CircleShape, clip = false),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .background(Color.White, CircleShape),
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 3.dp,
                                    color = Color.Black,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showFolderNavigationPanel) {
            FolderNavigationPanel(
                labels = labels,
                currentFilter = currentFilter,
                dragProgress = folderNavigationPanelProgress,
                onDismiss = {
                    folderNavigationPanelProgress = 0f
                    showFolderNavigationPanel = false
                },
                onSelect = { filter ->
                    viewModel.setFilter(filter)
                    folderNavigationPanelProgress = 0f
                    showFolderNavigationPanel = false
                },
            )
        }
        if (showQuickCreateActions) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { showQuickCreateActions = false },
            )
        }
        }
    }
}


private fun dashboardTitle(filter: MainViewModel.NoteFilter): String =
    when (filter) {
        is MainViewModel.NoteFilter.All -> "全部笔记"
        is MainViewModel.NoteFilter.Recent -> "最近修改"
        is MainViewModel.NoteFilter.Favorites -> "收藏"
        is MainViewModel.NoteFilter.Drafts -> "草稿"
        is MainViewModel.NoteFilter.Label -> filter.name.substringAfterLast("/").ifBlank { "文件夹" }
        is MainViewModel.NoteFilter.YamlTag -> "#${filter.name}"
        is MainViewModel.NoteFilter.Archive -> "归档"
        is MainViewModel.NoteFilter.Trash -> "废弃"
    }

private fun dashboardTitleForPath(path: String): String =
    path.substringAfterLast("/").ifBlank { "全部笔记" }

@Composable
private fun HomeFabIconButton(
    icon: ImageVector,
    contentDescription: String,
    onSwipeDown: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        if (dragAmount > 0f) {
                            onSwipeDown()
                            change.consume()
                        }
                    }
                }
                .clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
