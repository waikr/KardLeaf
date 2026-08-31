package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.editor.host.PreviewWebView
import com.kangle.kardleaf.ui.editor.host.PreviewWebViewController
import com.kangle.kardleaf.data.utils.NoteTextStats
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged

private val STARTUP_PERF_TRACE_TAG = KardLeafLogTags.STARTUP_PERF
private val USER_PERF_TRACE_TAG = KardLeafLogTags.USER_PERF
private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private val DASHBOARD_SCROLL_TRACE_TAG = KardLeafLogTags.DASHBOARD_SCROLL
private const val CUSTOM_SORT_FLASH_TAG = "KardLeafCustomSortFlash"
private const val PAGER_PROBE_TAG = "KardLeafPagerProbe"
private const val MENU_REOPEN_GUARD_MS = 250L
private const val HOME_BOTTOM_TOOLBAR_REVEAL_DELAY_MS = 235L
private const val HOME_BOTTOM_TOOLBAR_ENTER_DURATION_MS = 240
private const val HOME_BOTTOM_TOOLBAR_EXIT_DURATION_MS = 180
private inline fun logDashboardCustomSortFlash(message: () -> String) {
    if (KardLeafLog.isEnabled(DASHBOARD_CUSTOM_SORT_TRACE_TAG)) {
        KardLeafLog.d(CUSTOM_SORT_FLASH_TAG, message())
    }
}
@Composable
private fun KardLeafUndoSnackbar(snackbarData: SnackbarData) {
    val hasAction = snackbarData.visuals.actionLabel != null
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = 16.dp,
                top = 8.dp,
                end = if (hasAction) 8.dp else 16.dp,
                bottom = 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snackbarData.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (hasAction) {
                IconButton(
                    onClick = { snackbarData.performAction() },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Undo,
                        contentDescription = "撤回",
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    isDrawerOpen: Boolean,
    onSelectFolder: () -> Unit,
    onCreateSampleVault: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onSearchNoteClick: (Note, String) -> Unit = { note, query -> viewModel.openNoteAtSearchMatch(note, query) },
    onFabClick: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenCategoryDrawer: () -> Unit = {},
    onCreateQuickNoteClick: () -> Unit = {},
    onWebClipImported: (KardLeafCustomFeatures.ExternalNoteDraft) -> Unit = {},
    onCreateDrawingClick: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    edgeDrawerWidthPx: Float = 0f,
    rootFolderName: String = "",
    pauseBackgroundWork: Boolean = false,
    sampleCleanupPromptRequestId: Long = 0L,
    onSampleCleanupPromptConsumed: () -> Unit = {},
    onClearSampleVaultSamples: suspend () -> Boolean = { false },
    onRestoreSampleVaultSamples: suspend () -> Boolean = { false },
    onBackFromTemporaryFilter: (MainViewModel.NoteFilter) -> Boolean = { false },
    appStartupStartRealtimeMs: Long = 0L,
) {
    val notes by viewModel.notes.collectAsState()
    val uiItems by viewModel.uiItems.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState(initial = emptyList())
    val labels by viewModel.labels.collectAsState()
    val isPermissionNeeded by viewModel.isPermissionNeeded.collectAsState()
    val currentFilterState = viewModel.currentFilter.collectAsState()
    val currentFilter = currentFilterState.value
    val viewMode by viewModel.viewMode.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val currentFolderSortSettings by viewModel.currentFolderSortSettings.collectAsState()
    val folderSortVersion by viewModel.folderSortVersion.collectAsState()
    val folderManagerOrderVersion by viewModel.folderManagerOrderVersion.collectAsState()
    val customSortDragModeEnabled by viewModel.customSortDragModeEnabled.collectAsState()
    val cardDensity by viewModel.cardDensity.collectAsState()
    val showYamlTagsOnLooseCards by viewModel.showYamlTagsOnLooseCards.collectAsState()
    val showModifiedDateOnCards by viewModel.showModifiedDateOnCards.collectAsState()
    val cardModifiedDateFormat by viewModel.cardModifiedDateFormat.collectAsState()
    val showNoteTitleOnCards by viewModel.showNoteTitleOnCards.collectAsState()
    val showCurrentNoteTitleOnCards = showNoteTitleOnCards && currentFilter !is MainViewModel.NoteFilter.QuickNotes
    val showDateFilenameTitleOnCards by viewModel.showDateFilenameTitleOnCards.collectAsState()
    val customHiddenFilenamePatterns by viewModel.customHiddenFilenamePatterns.collectAsState()
    val yamlTags by viewModel.yamlTags.collectAsState()
    val noteCountByYamlTag = remember(allNotes) {
        allNotes
            .filterNot { it.isArchived || it.isTrashed }
            .flatMap { note -> note.tags.distinct().map { tag -> tag to note.file.path } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, paths) -> paths.distinct().size }
    }
    val selectionToolbarItemOrder by viewModel.selectionToolbarItemOrder.collectAsState()
    val selectionToolbarMoreItems by viewModel.selectionToolbarMoreItems.collectAsState()
    val selectionToolbarHiddenItems by viewModel.selectionToolbarHiddenItems.collectAsState()
    val homeActionStyle by viewModel.homeActionStyle.collectAsState()
    val homeBottomToolbarItemOrder by viewModel.homeBottomToolbarItemOrder.collectAsState()
    val homeBottomToolbarHiddenItems by viewModel.homeBottomToolbarHiddenItems.collectAsState()
    val homeBottomToolbarButtonSizeDp by viewModel.homeBottomToolbarButtonSizeDp.collectAsState()
    val selectedNotes by viewModel.selectedNotes.collectAsState()
    val pendingDeleteIds by viewModel.pendingDeleteIds.collectAsState()
    val isInSelectionMode = selectedNotes.isNotEmpty()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchOptions by viewModel.searchOptions.collectAsState()
    val isSearchActive = searchQuery.isNotBlank() || searchOptions.hasMetadataFilters
    val openSearchRequest by viewModel.openSearchRequest.collectAsState()
    val shouldShowHomeBottomToolbar =
        !isPermissionNeeded &&
            !isInSelectionMode &&
            currentFilter !is MainViewModel.NoteFilter.Random &&
            !isSearchActive &&
            homeActionStyle == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR
    val homeBottomToolbarItems = homeBottomToolbarItemOrder
        .filter { it !in homeBottomToolbarHiddenItems }
        .filter { homeBottomToolbarItemAvailable(it, currentFilter) }
    val isLoading by viewModel.isLoading.collectAsState()
    val isImportingLibrary by viewModel.isImportingLibrary.collectAsState()
    val isVaultSwitchRefreshing by viewModel.isVaultSwitchRefreshing.collectAsState()
    val isEditorOpen by viewModel.isEditorOpen.collectAsState()
    val dashboardScrollIntent by viewModel.dashboardScrollIntent.collectAsState()
    val dashboardUserScrollVersion by viewModel.dashboardUserScrollVersion.collectAsState()
    val shouldShowInitialNoteLoading = isLoading && allNotes.isEmpty() && !isImportingLibrary
    val context = LocalContext.current
    val unnamedNoteDateFormat = KardLeafCustomFeatures.getUnnamedNoteDateFormat(context)
    val showHomeWebClipAction = remember(context) { PrefsManager(context).isHomeWebClipActionVisible() }
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val listStates = remember { mutableMapOf<MainViewModel.NoteFilter, LazyStaggeredGridState>() }
    val searchListStates = remember { mutableMapOf<MainViewModel.NoteFilter, LazyStaggeredGridState>() }
    val listState = remember(currentFilter, isSearchActive) {
        val states = if (isSearchActive) searchListStates else listStates
        states.getOrPut(currentFilter) { LazyStaggeredGridState() }
    }
    val dashboardStartMs = remember { SystemClock.elapsedRealtime() }
    var dashboardFilterSwitchStartMs by remember { mutableStateOf(dashboardStartMs) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val activeThumbnailLoader: suspend (Note) -> Bitmap? = remember(viewModel) {
        viewModel::resolveNoteThumbnailBitmap
    }
    val notePeekThumbnail: (Note) -> Bitmap? = remember(viewModel) {
        viewModel::peekNoteThumbnailBitmap
    }
    fun showThemedSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    fun showUndoSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "撤回",
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastNoteAction()
            }
        }
    }


    LaunchedEffect(Unit) {
        KardLeafLog.d(STARTUP_PERF_TRACE_TAG, "dashboard compose enter filter=$currentFilter")
    }

    var dashboardFirstReadyLogged by remember { mutableStateOf(false) }
    LaunchedEffect(isPermissionNeeded, isLoading, notes.size, uiItems.size, allNotes.size) {
        val hasDashboardContent = notes.isNotEmpty() || uiItems.isNotEmpty() || allNotes.isNotEmpty()
        if (!dashboardFirstReadyLogged && !isPermissionNeeded && !isLoading && hasDashboardContent) {
            withFrameNanos { }
            dashboardFirstReadyLogged = true
            val appElapsed = if (appStartupStartRealtimeMs > 0L) {
                SystemClock.elapsedRealtime() - appStartupStartRealtimeMs
            } else {
                -1L
            }
            KardLeafLog.d(
                STARTUP_PERF_TRACE_TAG,
                "dashboard firstReady appElapsed=${appElapsed}ms dashboardElapsed=${SystemClock.elapsedRealtime() - dashboardStartMs}ms " +
                    "filter=$currentFilter notes=${notes.size} uiItems=${uiItems.size} all=${allNotes.size}",
            )
        }
    }

    LaunchedEffect(currentFilter) {
        dashboardFilterSwitchStartMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(
            USER_PERF_TRACE_TAG,
            "dashboardCategorySwitch start filter=$currentFilter " +
                "notes=${notes.size} uiItems=${uiItems.size} all=${allNotes.size} labels=${labels.size} " +
                dashboardFilterNoteCountSummary(currentFilter, allNotes),
        )
        withFrameNanos { }
        KardLeafLog.d(
            USER_PERF_TRACE_TAG,
            "dashboardCategorySwitch firstFrame elapsed=${SystemClock.elapsedRealtime() - dashboardFilterSwitchStartMs}ms filter=$currentFilter " +
                "notes=${notes.size} uiItems=${uiItems.size} all=${allNotes.size} " +
                dashboardFilterNoteCountSummary(currentFilter, allNotes),
        )
    }

    LaunchedEffect(currentFilter, notes.size, uiItems.size, allNotes.size, labels.size, isLoading, viewMode, cardDensity) {
        val elapsedSinceSwitch = SystemClock.elapsedRealtime() - dashboardFilterSwitchStartMs
        KardLeafLog.d(
            STARTUP_PERF_TRACE_TAG,
            "dashboard state elapsed=${SystemClock.elapsedRealtime() - dashboardStartMs}ms filter=$currentFilter " +
                "notes=${notes.size} uiItems=${uiItems.size} all=${allNotes.size} labels=${labels.size} " +
                "loading=$isLoading viewMode=$viewMode cardDensity=$cardDensity",
        )
        KardLeafLog.d(
            USER_PERF_TRACE_TAG,
            "dashboardCategoryState elapsedSinceSwitch=${elapsedSinceSwitch}ms filter=$currentFilter " +
                "notes=${notes.size} uiItems=${uiItems.size} all=${allNotes.size} labels=${labels.size} " +
                "loading=$isLoading viewMode=$viewMode cardDensity=$cardDensity " +
                dashboardFilterNoteCountSummary(currentFilter, allNotes),
        )
    }

    // UI State
    var showCreateLabelDialog by remember { mutableStateOf(false) }
    var showCreateSubfolderDialog by remember { mutableStateOf(false) }
    var showCreateFolderLocationDialog by remember { mutableStateOf(false) }
    var createFolderParentPath by remember { mutableStateOf<String?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var labelToDelete by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showSearchCategoryStrip by remember { mutableStateOf(false) }
    var showPullRefreshCircle by remember { mutableStateOf(false) }
    var showManualRefreshProgress by remember { mutableStateOf(false) }
    var manualRefreshLoadingSeen by remember { mutableStateOf(false) }
    var showFolderNavigationPanel by remember { mutableStateOf(false) }
    var folderNavigationShowTags by remember { mutableStateOf(false) }
    var folderNavigationEditMode by remember { mutableStateOf(false) }
    var folderNavigationFocusedParentPath by remember { mutableStateOf("") }
    val folderNavigationChevronRotation by animateFloatAsState(
        targetValue = if (showFolderNavigationPanel) 180f else 0f,
        label = "DashboardFolderChevron",
    )
    var folderNavigationPanelProgress by remember { mutableStateOf(0f) }
    var folderNavigationPanelCloseJob by remember { mutableStateOf<Job?>(null) }
    var showSampleCleanupPrompt by remember { mutableStateOf(false) }
    var showSampleCleanupConfirmDialog by remember { mutableStateOf(false) }
    var handledSampleCleanupPromptRequestId by remember { mutableStateOf(0L) }
    var previewDashboardTitlePath by remember { mutableStateOf<String?>(null) }
    var showQuickCreateActions by remember { mutableStateOf(false) }
    var showWebClipImportDialog by remember { mutableStateOf(false) }
    var shareNotesPending by remember { mutableStateOf<List<Note>>(emptyList()) }
    var imageShareWarningPending by remember { mutableStateOf<List<Note>>(emptyList()) }
    var shareBlockedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openSearchRequest) {
        if (openSearchRequest > 0L) {
            showSearch = true
            showSearchCategoryStrip = false
            showFolderNavigationPanel = false
        }
    }

    fun openFolderNavigationPanel() {
        folderNavigationPanelCloseJob?.cancel()
        folderNavigationShowTags = currentFilter is MainViewModel.NoteFilter.YamlTag
        folderNavigationEditMode = false
        folderNavigationFocusedParentPath = ""
        showFolderNavigationPanel = true
        folderNavigationPanelProgress = 0f
        coroutineScope.launch {
            withFrameNanos { }
            folderNavigationPanelProgress = 1f
        }
    }

    fun closeFolderNavigationPanel() {
        folderNavigationPanelProgress = 0f
        folderNavigationPanelCloseJob?.cancel()
        folderNavigationPanelCloseJob = coroutineScope.launch {
            delay(KardLeafMotion.ContainerDurationMillis.toLong())
            showFolderNavigationPanel = false
        }
    }

    fun closeSearch() {
        showSearch = false
        showSearchCategoryStrip = false
        focusManager.clearFocus()
        viewModel.clearSearch()
    }

    fun showSampleCleanupUndoSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "撤回",
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && onRestoreSampleVaultSamples()) {
                viewModel.refreshNotes()
            }
        }
    }

    LaunchedEffect(sampleCleanupPromptRequestId) {
        if (sampleCleanupPromptRequestId > 0L && sampleCleanupPromptRequestId != handledSampleCleanupPromptRequestId) {
            handledSampleCleanupPromptRequestId = sampleCleanupPromptRequestId
            showSampleCleanupPrompt = true
        }
    }


    shareBlockedMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { shareBlockedMessage = null },
            title = { Text("无法导出") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { shareBlockedMessage = null }) {
                    Text("知道了")
                }
            },
        )
    }

    if (imageShareWarningPending.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { imageShareWarningPending = emptyList() },
            title = { Text("导出为图片") },
            text = { Text("如果生成的图片太大，系统可能不支持导出；内容过长时会自动截断。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        shareSelectedNotes(context, imageShareWarningPending, ShareSelectedNotesMode.TEXT_IMAGE)
                        imageShareWarningPending = emptyList()
                    },
                ) {
                    Text("继续导出")
                }
            },
            dismissButton = {
                TextButton(onClick = { imageShareWarningPending = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (shareNotesPending.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { shareNotesPending = emptyList() },
            title = { Text("选择分享格式") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardFolderLocationRow(
                        icon = Icons.Outlined.Description,
                        title = "文本文件",
                        subtitle = "导出为 .txt 文件",
                        onClick = {
                            val notesToShare = shareNotesPending
                            shareNotesPending = emptyList()
                            coroutineScope.launch {
                                val fullNotes = viewModel.getFullNotesForShare(notesToShare)
                                if (fullNotes == null) {
                                    shareBlockedMessage = "无法读取完整正文，已取消导出"
                                    return@launch
                                }
                                shareSelectedNotes(context, fullNotes, ShareSelectedNotesMode.TEXT_FILE)
                            }
                        },
                    )
                    DashboardFolderLocationRow(
                        icon = Icons.Outlined.Image,
                        title = "文本图片",
                        subtitle = "生成 PNG 图片分享",
                        onClick = {
                            val notesToShare = shareNotesPending
                            shareNotesPending = emptyList()
                            coroutineScope.launch {
                                val fullNotes = viewModel.getFullNotesForShare(notesToShare)
                                if (fullNotes == null) {
                                    shareBlockedMessage = "无法读取完整正文，已取消导出"
                                    return@launch
                                }
                                val blockMessage = imageExportBlockMessage(fullNotes)
                                if (blockMessage != null) {
                                    shareBlockedMessage = blockMessage
                                } else {
                                    imageShareWarningPending = fullNotes
                                }
                            }
                        },
                    )
                    DashboardFolderLocationRow(
                        icon = Icons.Outlined.Description,
                        title = "Word",
                        subtitle = "导出为 .docx 文档",
                        onClick = {
                            val notesToShare = shareNotesPending
                            shareNotesPending = emptyList()
                            coroutineScope.launch {
                                val fullNotes = viewModel.getFullNotesForShare(notesToShare)
                                if (fullNotes == null) {
                                    shareBlockedMessage = "无法读取完整正文，已取消导出"
                                    return@launch
                                }
                                val blockMessage = wordExportBlockMessage(fullNotes)
                                if (blockMessage != null) {
                                    shareBlockedMessage = blockMessage
                                    return@launch
                                }
                                shareSelectedNotes(context, fullNotes, ShareSelectedNotesMode.WORD)
                            }
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { shareNotesPending = emptyList() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showCreateLabelDialog) {
        CreateLabelDialog(
            onDismiss = { showCreateLabelDialog = false },
            onConfirm = { name ->
                viewModel.createLabel(name)
                showCreateLabelDialog = false
            },
        )
    }

    if (showCreateFolderLocationDialog) {
        val currentFolder = (currentFilter as? MainViewModel.NoteFilter.Label)?.name.orEmpty().normalizeDashboardFolderPath()
        val parentFolder = currentFolder.substringBeforeLast("/", "").normalizeDashboardFolderPath()
        AlertDialog(
            onDismissRequest = { showCreateFolderLocationDialog = false },
            title = { Text("新建文件夹位置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardFolderLocationRow(
                        icon = Icons.Outlined.CreateNewFolder,
                        title = "放在当前文件夹里",
                        subtitle = currentFolder.ifBlank { "根目录" },
                        onClick = {
                            createFolderParentPath = currentFolder
                            showCreateFolderLocationDialog = false
                            showCreateSubfolderDialog = true
                        },
                    )
                    DashboardFolderLocationRow(
                        icon = Icons.Outlined.CreateNewFolder,
                        title = "放在上一级文件夹里",
                        subtitle = parentFolder.ifBlank { "根目录" },
                        onClick = {
                            createFolderParentPath = parentFolder
                            showCreateFolderLocationDialog = false
                            showCreateSubfolderDialog = true
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCreateFolderLocationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showCreateSubfolderDialog) {
        CreateLabelDialog(
            onDismiss = {
                showCreateSubfolderDialog = false
                createFolderParentPath = null
            },
            onConfirm = { name ->
                val parent = createFolderParentPath
                    ?: (currentFilter as? MainViewModel.NoteFilter.Label)?.name.orEmpty().normalizeDashboardFolderPath()
                val childPath =
                    listOf(parent, name.trim())
                        .filter { it.isNotBlank() }
                        .joinToString("/")
                if (childPath.isNotBlank()) {
                    viewModel.createLabel(childPath)
                    viewModel.setFilter(MainViewModel.NoteFilter.Label(childPath))
                }
                showCreateSubfolderDialog = false
                createFolderParentPath = null
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
                            showThemedSnackbar(context.getString(R.string.label_deleted_toast))
                        },
                        onError = { error ->
                            val localizedError =
                                if (error == "Label must be empty to delete it") {
                                    context.getString(R.string.error_delete_label_not_empty)
                                } else {
                                    error
                                }
                            showThemedSnackbar(localizedError)
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
    val selectedNotesList = remember(notes, selectedNotes) {
        notes.filter { selectedNotes.contains(it.file.path) }
    }
    val allSelectedArchived = selectedNotesList.isNotEmpty() && selectedNotesList.all { it.isArchived }
    val allSelectedActive = selectedNotesList.isNotEmpty() && selectedNotesList.all { !it.isArchived && !it.isTrashed }
    val allSelectedFavorite = selectedNotesList.isNotEmpty() && selectedNotesList.all { it.isFavorite }
    var propertyNote by remember { mutableStateOf<Note?>(null) }
    var propertyTextStats by remember { mutableStateOf<NoteTextStats?>(null) }

    fun showProperties(note: Note) {
        val noteId = note.id
        propertyNote = note
        propertyTextStats = null
        coroutineScope.launch {
            val fullNote = viewModel.getNoteForProperties(noteId)
            val stats = viewModel.getNoteTextStatsForProperties(noteId)
            if (propertyNote?.id == noteId) {
                fullNote?.let { propertyNote = it }
                propertyTextStats = stats
            }
        }
    }

    propertyNote?.let { note ->
        NotePropertiesDialog(
            note = note,
            textStats = propertyTextStats,
            noteCountByTag = noteCountByYamlTag,
            onTimeChange = { field, timestamp ->
                val current = propertyNote
                if (current != null) {
                    val createdAtMs = if (field == NoteTimeField.CREATED) timestamp else current.createdAt.time
                    val updatedAtMs = if (field == NoteTimeField.UPDATED) timestamp else current.updatedAt.time
                    viewModel.updateNoteTimestamps(note.id, createdAtMs, updatedAtMs) { updated ->
                        if (updated != null && propertyNote?.id == note.id) {
                            propertyNote = updated
                        } else if (updated == null) {
                            showThemedSnackbar("时间修改失败")
                        }
                    }
                }
            },
            onDismiss = {
                propertyNote = null
                propertyTextStats = null
            },
        )
    }

    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { (!listState.canScrollBackward) to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (atTop, isScrolling) ->
                viewModel.updateDashboardViewport(
                    atTop = atTop,
                    userScrollStarted = isScrolling && !wasScrolling,
                )
                wasScrolling = isScrolling
            }
    }

    val dashboardTargetIndex =
        dashboardScrollIntent?.targetPath?.let { targetPath ->
            uiItems.indexOfFirst { item ->
                (item as? DashboardUiItem.NoteItem)?.note?.file?.path == targetPath
            }.takeIf { it >= 0 }
        }
    LaunchedEffect(
        dashboardScrollIntent,
        dashboardTargetIndex,
        currentFilter,
        isSearchActive,
        isEditorOpen,
        dashboardUserScrollVersion,
        listState,
        listState.isScrollInProgress,
        uiItems,
    ) {
        val intent = dashboardScrollIntent ?: return@LaunchedEffect
        when (
            decideDashboardScrollIntent(
                intent = intent,
                currentFilter = currentFilter,
                searchActive = isSearchActive,
                editorOpen = isEditorOpen,
                userScrollVersion = dashboardUserScrollVersion,
                targetIndex = dashboardTargetIndex,
                scrollInProgress = listState.isScrollInProgress,
            )
        ) {
            DashboardScrollDecision.WAIT -> Unit
            DashboardScrollDecision.DROP -> viewModel.consumeDashboardScrollIntent(intent.id)
            DashboardScrollDecision.APPLY -> {
                withFrameNanos { }
                when (intent.action) {
                    DashboardScrollAction.TOP -> listState.requestDashboardScrollToItem(0)
                    DashboardScrollAction.REVEAL_PATH -> {
                        val index = dashboardTargetIndex ?: return@LaunchedEffect
                        val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
                        if (!alreadyVisible) listState.requestDashboardScrollToItem(index)
                    }
                }
                viewModel.consumeDashboardScrollIntent(intent.id)
            }
        }
    }

    var homeBottomToolbarVisible by remember { mutableStateOf(true) }
    LaunchedEffect(homeActionStyle, currentFilter, isPermissionNeeded, isInSelectionMode, listState) {
        homeBottomToolbarVisible = true
        if (homeActionStyle != PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR ||
            isPermissionNeeded ||
            isInSelectionMode
        ) {
            return@LaunchedEffect
        }

        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling) {
                    homeBottomToolbarVisible = false
                } else {
                    delay(HOME_BOTTOM_TOOLBAR_REVEAL_DELAY_MS)
                    if (!listState.isScrollInProgress) {
                        homeBottomToolbarVisible = true
                    }
                }
            }
    }

    // Double back to exit
    var lastBackPressTime by remember { mutableStateOf(0L) }

    BackHandler(enabled = !isDrawerOpen) {
        KardLeafLog.d(
            BACK_TRACE_TAG,
            "Dashboard root BackHandler hit drawerOpen=$isDrawerOpen showSearch=$showSearch " +
                "folderPanel=$showFolderNavigationPanel quickCreate=$showQuickCreateActions " +
                "selectionMode=$isInSelectionMode dragMode=$customSortDragModeEnabled filter=$currentFilter propertyDialog=${propertyNote != null}",
        )
        when {
            showCreateLabelDialog -> showCreateLabelDialog = false
            showCreateSubfolderDialog -> {
                showCreateSubfolderDialog = false
                createFolderParentPath = null
            }
            showCreateFolderLocationDialog -> showCreateFolderLocationDialog = false
            showEmptyTrashDialog -> showEmptyTrashDialog = false
            labelToDelete != null -> labelToDelete = null
            propertyNote != null -> propertyNote = null
            customSortDragModeEnabled -> viewModel.setCustomSortDragModeEnabled(false)
            showFolderNavigationPanel -> {
                closeFolderNavigationPanel()
            }
            showQuickCreateActions -> showQuickCreateActions = false
            shareBlockedMessage != null -> shareBlockedMessage = null
            imageShareWarningPending.isNotEmpty() -> imageShareWarningPending = emptyList()
            shareNotesPending.isNotEmpty() -> shareNotesPending = emptyList()
            showSearch -> {
                closeSearch()
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
                    showThemedSnackbar(context.getString(R.string.press_back_again_exit))
                }
            }
        }
    }

    fun openCreateFolderDialog() {
        val currentFolder = (currentFilter as? MainViewModel.NoteFilter.Label)?.name.orEmpty().normalizeDashboardFolderPath()
        if (currentFolder.isBlank()) {
            createFolderParentPath = ""
            showCreateSubfolderDialog = true
        } else {
            showCreateFolderLocationDialog = true
        }
    }

    fun openHomeBottomToolbarItem(itemId: PrefsManager.HomeBottomToolbarItemId) {
        when (itemId) {
            PrefsManager.HomeBottomToolbarItemId.NEW_NOTE -> onFabClick()
            PrefsManager.HomeBottomToolbarItemId.NEW_DRAFT -> onCreateQuickNoteClick()
            PrefsManager.HomeBottomToolbarItemId.NEW_DRAWING -> onCreateDrawingClick()
            PrefsManager.HomeBottomToolbarItemId.NEW_FOLDER -> openCreateFolderDialog()
            PrefsManager.HomeBottomToolbarItemId.TASKS -> viewModel.navigateTo(MainViewModel.Screen.Tasks)
            PrefsManager.HomeBottomToolbarItemId.ALL_NOTES -> {
                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                viewModel.setFilter(MainViewModel.NoteFilter.All)
            }
            PrefsManager.HomeBottomToolbarItemId.RECENT -> {
                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                viewModel.setFilter(MainViewModel.NoteFilter.Recent)
            }
            PrefsManager.HomeBottomToolbarItemId.FAVORITES -> {
                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                viewModel.setFilter(MainViewModel.NoteFilter.Favorites)
            }
            PrefsManager.HomeBottomToolbarItemId.DRAFTS -> {
                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                viewModel.setFilter(MainViewModel.NoteFilter.QuickNotes)
            }
            PrefsManager.HomeBottomToolbarItemId.TAGS -> viewModel.navigateTo(MainViewModel.Screen.Tags)
            PrefsManager.HomeBottomToolbarItemId.FILES -> onOpenCategoryDrawer()
            PrefsManager.HomeBottomToolbarItemId.DATES -> viewModel.navigateTo(MainViewModel.Screen.Dates)
            PrefsManager.HomeBottomToolbarItemId.IMAGES -> viewModel.navigateTo(MainViewModel.Screen.Images)
            PrefsManager.HomeBottomToolbarItemId.ARCHIVE -> {
                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                viewModel.setFilter(MainViewModel.NoteFilter.Archive)
            }
            PrefsManager.HomeBottomToolbarItemId.TRASH -> {
                viewModel.navigateTo(MainViewModel.Screen.Dashboard)
                viewModel.setFilter(MainViewModel.NoteFilter.Trash)
            }
            PrefsManager.HomeBottomToolbarItemId.PRIVACY -> onOpenPrivacy()
            PrefsManager.HomeBottomToolbarItemId.SETTINGS -> viewModel.navigateTo(MainViewModel.Screen.Settings)
        }
    }

    Scaffold(
        topBar = {
            if (currentFilter is MainViewModel.NoteFilter.Random) {
                Unit
            } else if (isInSelectionMode) {
                SelectionTopAppBar(
                    selectionCount = selectedNotes.size,
                    currentFilter = currentFilter,
                    allSelectedArchived = allSelectedArchived,
                    allSelectedActive = allSelectedActive,
                    allSelectedFavorite = allSelectedFavorite,
                    onClearSelection = { viewModel.clearSelection() },
                    onDelete = {
                        val deleteForever = currentFilter is MainViewModel.NoteFilter.Trash
                        viewModel.deleteSelectedNotes { result ->
                            when {
                                result.failedCount > 0 && result.successCount > 0 -> {
                                    showThemedSnackbar("已删除${result.successCount}个，${result.failedCount}个失败")
                                }
                                result.failedCount > 0 -> {
                                    showThemedSnackbar("删除失败")
                                }
                                deleteForever -> {
                                    showThemedSnackbar("已永久删除")
                                }
                                else -> {
                                    showUndoSnackbar("已删除")
                                }
                            }
                        }
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
                    selectionToolbarItemOrder = selectionToolbarItemOrder,
                    selectionToolbarMoreItems = selectionToolbarMoreItems,
                    selectionToolbarHiddenItems = selectionToolbarHiddenItems,
                    selectedNoteForProperties = selectedNotesList.singleOrNull(),
                    selectedNotesForTags = selectedNotesList,
                    availableYamlTags = yamlTags,
                    onApplyTags = { tags ->
                        viewModel.addTagsToSelectedNotes(tags) { successCount, failedCount ->
                            when {
                                successCount > 0 && failedCount > 0 -> {
                                    showThemedSnackbar("已更新${successCount}个，${failedCount}个失败")
                                }
                                successCount > 0 -> showThemedSnackbar("已更新标签")
                                else -> showThemedSnackbar("标签更新失败")
                            }
                        }
                    },
                    onShowProperties = ::showProperties,
                    onDuplicate = {
                        val targetFolder = when (val filter = currentFilter) {
                            is MainViewModel.NoteFilter.Label -> filter.name
                            is MainViewModel.NoteFilter.QuickNotes -> PrefsManager.DEFAULT_QUICK_NOTE_FOLDER_NAME
                            else -> ""
                        }
                        viewModel.duplicateSelectedNotes(targetFolder) { count ->
                            showThemedSnackbar(if (count > 0) "已复制 $count 篇笔记" else "复制失败")
                        }
                    },
                    onShare = {
                        shareNotesPending = selectedNotesList
                    },
                    onMoveToPrivacy = {
                        viewModel.moveSelectedNotesToPrivacy { count ->
                            showThemedSnackbar(if (count > 0) "已移动到隐私库" else "移动到隐私库失败")
                        }
                    },
                    onMerge = { options ->
                        viewModel.mergeSelectedNotes(selectedNotesList, options) { result ->
                            when {
                                result.targetPath == null -> showThemedSnackbar("合并失败")
                                result.failedSourceCount > 0 -> {
                                    showThemedSnackbar("已合并，但有 ${result.failedSourceCount} 个源文件处理失败")
                                }
                                else -> showThemedSnackbar("已合并 ${result.sourceCount + 1} 篇笔记")
                            }
                        }
                    },
                )
            } else {
                Column {
                    TopAppBar(
                        title = {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !showSearch,
                                    enter = fadeIn(),
                                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) {
                                                if (showFolderNavigationPanel) {
                                                    closeFolderNavigationPanel()
                                                } else {
                                                    openFolderNavigationPanel()
                                                }
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = previewDashboardTitlePath?.let(::dashboardTitleForPath) ?: dashboardTitle(currentFilter),
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Icon(
                                            imageVector = Icons.Outlined.KeyboardArrowDown,
                                            contentDescription = if (showFolderNavigationPanel) "收起分类导航" else "展开分类导航",
                                            modifier =
                                                Modifier
                                                    .padding(start = 2.dp)
                                                    .size(22.dp)
                                                    .graphicsLayer { rotationZ = folderNavigationChevronRotation },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showSearch,
                                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                                ) {
                                    Surface(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height(50.dp),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 2.dp,
                                    ) {
                                        SearchBar(
                                            viewModel = viewModel,
                                            requestFocus = showSearch,
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.menu))
                            }
                        },
                        actions = {
                            if (showHomeWebClipAction) {
                                IconButton(
                                    onClick = {
                                        focusManager.clearFocus()
                                        showWebClipImportDialog = true
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.Language,
                                        contentDescription = "网页转 Markdown",
                                    )
                                }
                            }
                            if (showFolderNavigationPanel) {
                                if (!folderNavigationShowTags && folderNavigationFocusedParentPath.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            folderNavigationFocusedParentPath =
                                                navigationParentFolderPath(folderNavigationFocusedParentPath)
                                        },
                                    ) {
                                        Icon(
                                            Icons.Outlined.ArrowBack,
                                            contentDescription = "返回上一级分类",
                                        )
                                    }
                                }
                                FolderNavigationToolbarActions(
                                    editMode = folderNavigationEditMode,
                                    showTags = folderNavigationShowTags,
                                    onEditToggle = {
                                        val enteringEditMode = !folderNavigationEditMode
                                        folderNavigationEditMode = enteringEditMode
                                        if (enteringEditMode) {
                                            showThemedSnackbar("已进入编辑模式")
                                        }
                                    },
                                    onSwitch = {
                                        folderNavigationShowTags = !folderNavigationShowTags
                                        folderNavigationEditMode = false
                                        folderNavigationFocusedParentPath = ""
                                    },
                                )
                            } else {
                                IconButton(onClick = {
                                    if (showSearch) {
                                        closeSearch()
                                    } else {
                                        showSearch = true
                                        showSearchCategoryStrip = false
                                    }
                                }) {
                                    Icon(
                                        Icons.Outlined.Search,
                                        contentDescription = "搜索",
                                        tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                SortButton(viewModel = viewModel)
                            }
                            if (currentFilter is MainViewModel.NoteFilter.Trash) {
                                var showMoreMenu by remember { mutableStateOf(false) }
                                var lastMoreMenuDismissAt by remember { mutableStateOf(0L) }
                                LaunchedEffect(showMoreMenu) {
                                    KardLeafLog.d(BACK_TRACE_TAG, "Dashboard trash more state changed showMoreMenu=$showMoreMenu")
                                }
                                Box {
                                    IconButton(onClick = {
                                        val now = SystemClock.uptimeMillis()
                                        val ignoreReopen = !showMoreMenu && now - lastMoreMenuDismissAt < MENU_REOPEN_GUARD_MS
                                        KardLeafLog.d(BACK_TRACE_TAG, "Dashboard trash more click toggle menu filter=$currentFilter showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                                        if (!ignoreReopen) {
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
                                                        "Dashboard trash more popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoreMenu=$showMoreMenu",
                                                    )
                                                }
                                                false
                                            },
                                        expanded = showMoreMenu,
                                        onDismissRequest = {
                                            KardLeafLog.d(BACK_TRACE_TAG, "Dashboard trash more onDismissRequest showMoreMenu=$showMoreMenu")
                                            lastMoreMenuDismissAt = SystemClock.uptimeMillis()
                                            showMoreMenu = false
                                        },
                                        properties = PopupProperties(
                                            focusable = false,
                                            dismissOnBackPress = false,
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
                                        KardLeafLog.d(BACK_TRACE_TAG, "Dashboard trash more BackHandler hit, closing menu")
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
                    AnimatedVisibility(visible = showSearch) {
                        SearchFilterToolbar(
                            viewModel = viewModel,
                            showCategoryStrip = showSearchCategoryStrip,
                            onCategoryStripToggle = {
                                if (showSearchCategoryStrip || searchOptions.folder != null) {
                                    if (searchOptions.folder != null) {
                                        viewModel.setSearchFolder(null)
                                    }
                                    showSearchCategoryStrip = false
                                } else {
                                    showSearchCategoryStrip = true
                                }
                            },
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp),
            ) { snackbarData ->
                KardLeafUndoSnackbar(snackbarData = snackbarData)
            }
        },
        bottomBar = {},
        floatingActionButton = {
            if (!isPermissionNeeded &&
                homeActionStyle == PrefsManager.HomeActionStyle.SIMPLE_NEW_BUTTON &&
                currentFilter !is MainViewModel.NoteFilter.Trash &&
                currentFilter !is MainViewModel.NoteFilter.Archive &&
                currentFilter !is MainViewModel.NoteFilter.Random
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
                                icon = Icons.Outlined.Language,
                                contentDescription = "网页转 Markdown",
                                onSwipeDown = { showQuickCreateActions = false },
                                onClick = {
                                    showQuickCreateActions = false
                                    showWebClipImportDialog = true
                                },
                            )
                            HomeFabIconButton(
                                icon = Icons.Outlined.Description,
                                contentDescription = "新建速记",
                                onSwipeDown = { showQuickCreateActions = false },
                                onClick = {
                                    showQuickCreateActions = false
                                    viewModel.createQuickNote(source = "dashboard_quick_memo")
                                },
                            )
                            HomeFabIconButton(
                                icon = Icons.Outlined.Palette,
                                contentDescription = "新建绘图",
                                onSwipeDown = { showQuickCreateActions = false },
                                onClick = {
                                    showQuickCreateActions = false
                                    onCreateDrawingClick()
                                },
                            )
                            HomeFabIconButton(
                                icon = Icons.Outlined.CreateNewFolder,
                                contentDescription = "新建文件夹",
                                onSwipeDown = { showQuickCreateActions = false },
                                onClick = {
                                    showQuickCreateActions = false
                                    openCreateFolderDialog()
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
            if (currentFilter is MainViewModel.NoteFilter.Random) {
                RandomNoteReviewView(
                    notes = notes,
                    sessionSeed = currentFilter.seed,
                    viewModel = viewModel,
                    onEdit = viewModel::openNoteForEditing,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (showManualRefreshProgress || isImportingLibrary || isVaultSwitchRefreshing) {
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // 分类标签栏和横向 Pager 共享同一组页面；标签栏高光只跟随 currentFilter。
            val currentFolderPath = (currentFilter as? MainViewModel.NoteFilter.Label)?.name
                ?.let(::normalizeFolderPathForUi)
                .orEmpty()
            val folderPagerPages = remember(labels, currentFilter, folderManagerOrderVersion) {
                buildFolderPagerPages(
                    labels = labels,
                    currentFilter = currentFilter,
                    savedOrderFor = viewModel::getFolderDisplayOrder,
                )
            }
            val currentPageIndex = folderPagerPages
                .indexOfFirst { it.path == currentFolderPath }
                .coerceAtLeast(0)
            val folderPagerKey = remember(folderPagerPages) {
                folderPagerPages.joinToString("|") { it.path }
            }
            val folderPagerState = androidx.compose.runtime.key(folderPagerKey) {
                rememberPagerState(
                    initialPage = currentPageIndex,
                    pageCount = { folderPagerPages.size },
                )
            }
            val useFolderPager =
                (currentFilter is MainViewModel.NoteFilter.All ||
                    currentFilter is MainViewModel.NoteFilter.Label) &&
                    !showSearch &&
                    !isSearchActive &&
                    folderPagerPages.isNotEmpty()
            val customSortDragRefreshBlocked =
                customSortDragModeEnabled &&
                    !isSearchActive &&
                    (currentFolderSortSettings?.order ?: sortOrder) == PrefsManager.SortOrder.CUSTOM
            LaunchedEffect(
                currentFilter,
                labels,
                folderPagerKey,
                useFolderPager,
                isSearchActive,
                isInSelectionMode,
                folderSortVersion,
            ) {
                logDashboardCustomSortFlash {
                    "Dashboard pagerInputs filter=$currentFilter labels=${labels.size} pages=${folderPagerPathSummary(folderPagerPages)} " +
                        "usePager=$useFolderPager searchActive=$isSearchActive selection=$isInSelectionMode sortVersion=$folderSortVersion uiItems=${dashboardScreenUiItemsFlashSummary(uiItems)} notes=${notes.size}"
                }
            }
            var isFolderPagerVerticalGestureLocked by remember { mutableStateOf(false) }
            LaunchedEffect(useFolderPager, folderPagerState.isScrollInProgress) {
                if (!useFolderPager || !folderPagerState.isScrollInProgress) {
                    if (isFolderPagerVerticalGestureLocked) {
                        KardLeafLog.d(
                            DASHBOARD_SCROLL_TRACE_TAG,
                            "dashboardPager verticalLock release reason=${if (useFolderPager) "scrollIdle" else "pagerDisabled"}",
                        )
                    }
                    isFolderPagerVerticalGestureLocked = false
                }
            }
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
            LaunchedEffect(
                currentFolderPath,
                previewFolderPath,
                folderPagerState.currentPage,
                folderPagerState.settledPage,
                folderPagerState.isScrollInProgress,
                isProgrammaticPagerSync,
            ) {
                logDashboardCustomSortFlash {
                    "Dashboard pagerState currentPath=$currentFolderPath preview=$previewFolderPath currentPage=${folderPagerState.currentPage} " +
                        "settled=${folderPagerState.settledPage} scrolling=${folderPagerState.isScrollInProgress} programmatic=$isProgrammaticPagerSync"
                }
            }
            LaunchedEffect(previewFolderPath, folderPagerState.isScrollInProgress, isProgrammaticPagerSync) {
                previewDashboardTitlePath = if (folderPagerState.isScrollInProgress && !isProgrammaticPagerSync) {
                    previewFolderPath
                } else {
                    null
                }
            }
            val shouldShowCategoryStrip =
                !isPermissionNeeded &&
                    labels.isNotEmpty() &&
                    if (showSearch) {
                        showSearchCategoryStrip
                    } else {
                        currentFilter !is MainViewModel.NoteFilter.Trash &&
                            currentFilter !is MainViewModel.NoteFilter.Archive &&
                            currentFilter !is MainViewModel.NoteFilter.Recent &&
                            currentFilter !is MainViewModel.NoteFilter.Favorites &&
                            currentFilter !is MainViewModel.NoteFilter.QuickNotes &&
                            currentFilter !is MainViewModel.NoteFilter.Random &&
                            currentFilter !is MainViewModel.NoteFilter.YamlTag
                    }
            if (shouldShowCategoryStrip) {
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
                            .pointerInput(labels, currentFilter, edgeDrawerWidthPx, showSearch) {
                                if (showSearch) return@pointerInput
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
                                                folderNavigationPanelCloseJob?.cancel()
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
                                        closeFolderNavigationPanel()
                                    }
                                }
                            },
                ) {
                    val categoryStripFilter = if (showSearch) {
                        searchOptions.folder?.let { MainViewModel.NoteFilter.Label(it) }
                            ?: MainViewModel.NoteFilter.All
                    } else {
                        currentFilter
                    }
                    FolderPathStrip(
                        currentFilter = categoryStripFilter,
                        labels = labels,
                        previewPath = if (showSearch) "" else previewFolderPath,
                        pagerCurrentPage = if (showSearch) -1 else folderPagerState.currentPage,
                        pagerSettledPage = if (showSearch) -1 else folderPagerState.settledPage,
                        pagerScrolling = !showSearch && folderPagerState.isScrollInProgress,
                        folderOrderVersion = folderManagerOrderVersion,
                        savedOrderFor = viewModel::getFolderDisplayOrder,
                        onOpenFolder = { folder ->
                            if (showSearch) {
                                viewModel.setSearchFolder(folder)
                            } else {
                                val filter = MainViewModel.NoteFilter.Label(folder)
                                if (currentFilter != filter) {
                                    viewModel.setFilter(filter)
                                }
                            }
                        },
                        onShowAllInFolder = { folder ->
                            if (showSearch) {
                                viewModel.setSearchFolder(folder)
                            } else {
                                viewModel.showAllInFolder(folder)
                            }
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
                        onCreateSampleVault = onCreateSampleVault,
                        onSelectFolder = onSelectFolder,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    val pullRefreshState = rememberPullToRefreshState()
                    LaunchedEffect(customSortDragRefreshBlocked) {
                        if (customSortDragRefreshBlocked) {
                            showPullRefreshCircle = false
                            showManualRefreshProgress = false
                            manualRefreshLoadingSeen = false
                            pullRefreshState.endRefresh()
                        }
                    }
                    if (pullRefreshState.isRefreshing) {
                        LaunchedEffect(customSortDragRefreshBlocked) {
                            if (customSortDragRefreshBlocked) {
                                pullRefreshState.endRefresh()
                            } else {
                                showManualRefreshProgress = true
                                manualRefreshLoadingSeen = false
                                viewModel.refreshNotes(
                                    reason = NoteRefreshReason.USER_PULL_REFRESH,
                                    forceTop = true,
                                )
                            }
                        }
                    }

                    val pullRefreshNestedScrollModifier =
                        if (customSortDragRefreshBlocked) {
                            Modifier
                        } else {
                            Modifier.nestedScroll(pullRefreshState.nestedScrollConnection)
                        }

                    LaunchedEffect(isLoading, showManualRefreshProgress, manualRefreshLoadingSeen) {
                        if (showManualRefreshProgress && isLoading) {
                            manualRefreshLoadingSeen = true
                        }
                        if (showManualRefreshProgress && manualRefreshLoadingSeen && !isLoading) {
                            showManualRefreshProgress = false
                            manualRefreshLoadingSeen = false
                            pullRefreshState.endRefresh()
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .then(pullRefreshNestedScrollModifier)
                                .pointerInput(isLoading, pullRefreshListState, customSortDragRefreshBlocked) {
                                    if (customSortDragRefreshBlocked) {
                                        showPullRefreshCircle = false
                                        return@pointerInput
                                    }
                                    val pullIndicatorDistancePx = with(density) { 28.dp.toPx() }
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val downIndex = pullRefreshListState.firstVisibleItemIndex
                                        val downOffset = pullRefreshListState.firstVisibleItemScrollOffset
                                        var pointerPressed = true
                                        showPullRefreshCircle = false
                                        var isVerticalPull = false
                                        var consumedForHorizontalGuard = false
                                        var maxDx = 0f
                                        var maxDy = 0f
                                        var moveEvents = 0
                                        var scrollMoved = false
                                        while (pointerPressed) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            pointerPressed = change?.pressed == true
                                            if (change != null && pointerPressed) {
                                                val dx = kotlin.math.abs(change.position.x - down.position.x)
                                                val dy = change.position.y - down.position.y
                                                maxDx = maxOf(maxDx, dx)
                                                maxDy = maxOf(maxDy, kotlin.math.abs(dy))
                                                moveEvents++
                                                scrollMoved = scrollMoved ||
                                                    pullRefreshListState.firstVisibleItemIndex != downIndex ||
                                                    pullRefreshListState.firstVisibleItemScrollOffset != downOffset
                                                // 纯垂直下拉（dy>0 且 dy>dx）时不消费 move，
                                                // 让 Material3 PullToRefresh 的 nestedScroll 正常累积下拉距离、
                                                // 在合理距离（远小于半个屏幕）触发刷新。
                                                // 仅当垂直下拉伴随明显水平偏移（dx > dy*0.6）时才消费，
                                                // 阻止 HorizontalPager 误触切换页面（空列表场景）。
                                                if (!isVerticalPull && dy > 0f && dy > dx) {
                                                    isVerticalPull = true
                                                }
                                                if (isVerticalPull && dx > dy * 0.6f) {
                                                    consumedForHorizontalGuard = true
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
                                        val endIndex = pullRefreshListState.firstVisibleItemIndex
                                        val endOffset = pullRefreshListState.firstVisibleItemScrollOffset
                                        if (moveEvents > 0 && (consumedForHorizontalGuard || !scrollMoved || maxDy > 24f)) {
                                            KardLeafLog.d(
                                                DASHBOARD_SCROLL_TRACE_TAG,
                                                "dashboardPointer end moves=$moveEvents consumedHorizontalGuard=$consumedForHorizontalGuard " +
                                                    "scrollMoved=$scrollMoved maxDx=${maxDx.toInt()} maxDy=${maxDy.toInt()} " +
                                                    "fromIndex=$downIndex toIndex=$endIndex fromOffset=$downOffset toOffset=$endOffset " +
                                                    "pullRefreshing=${pullRefreshState.isRefreshing} loading=$isLoading customSortBlocked=$customSortDragRefreshBlocked",
                                            )
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
                                        try {
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
                                                                KardLeafLog.d(
                                                                    DASHBOARD_SCROLL_TRACE_TAG,
                                                                    "dashboardPager verticalLock acquire dx=${dx.toInt()} dy=${dy.toInt()}",
                                                                )
                                                            }
                                                            dx > dy * 1.35f -> {
                                                                lockedHorizontal = true
                                                                isFolderPagerVerticalGestureLocked = false
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        } finally {
                                            if (isFolderPagerVerticalGestureLocked) {
                                                KardLeafLog.d(DASHBOARD_SCROLL_TRACE_TAG, "dashboardPager verticalLock release reason=gestureEnd")
                                            }
                                            isFolderPagerVerticalGestureLocked = false
                                        }
                                    }
                                }
                                .clipToBounds(),
                    ) {
                        // 用 rememberUpdatedState 持有最新值：第二个 effect 不以 folderPagerKey/currentFolderPath
                        // 作为 key，pages 重建时 effect 不会重启，从而避免用旧 settledPage 在新 pages 里
                        // 取到错误 target 回写 setFilter（这正是点击全部笔记/返回/点标签失效的根因）。
                        val folderPagerPagesUpdated = rememberUpdatedState(folderPagerPages)
                        val currentFolderPathUpdated = rememberUpdatedState(currentFolderPath)

                        // 第一个 effect：外部筛选 → pager（瞬时同步）。
                        // 点击全部笔记/返回/点标签时 currentFilter 变化 → pages 重建 → folderPagerKey 变化
                        // → 此 effect 重启 → scrollToPage 瞬时跳到正确页，立即响应。
                        LaunchedEffect(currentPageIndex, folderPagerKey) {
                            logDashboardCustomSortFlash {
                                "Dashboard syncEffect enter currentPage=${folderPagerState.currentPage} targetIndex=$currentPageIndex currentPath=$currentFolderPath pages=${folderPagerPathSummary(folderPagerPages)} keyHash=${folderPagerKey.hashCode()}"
                            }
                            if (folderPagerPages.isNotEmpty() && folderPagerState.currentPage != currentPageIndex) {
                                isProgrammaticPagerSync = true
                                logDashboardCustomSortFlash {
                                    "Dashboard syncEffect scrollToPage start from=${folderPagerState.currentPage} to=$currentPageIndex currentPath=$currentFolderPath"
                                }
                                try {
                                    folderPagerState.scrollToPage(currentPageIndex)
                                } finally {
                                    logDashboardCustomSortFlash {
                                        "Dashboard syncEffect scrollToPage end currentPage=${folderPagerState.currentPage} settled=${folderPagerState.settledPage} currentPath=$currentFolderPath"
                                    }
                                    isProgrammaticPagerSync = false
                                }
                            }
                        }

                        LaunchedEffect(folderPagerState, useFolderPager) {
                            var swipeStartMs: Long? = null
                            var swipeStartPage = runCatching { folderPagerState.currentPage }.getOrDefault(0)
                            var frameJob: Job? = null
                            var frameCount = 0
                            var slowFrameCount = 0
                            var maxFrameMs = 0L

                            snapshotFlow {
                                runCatching {
                                    val maxPage = (folderPagerPagesUpdated.value.size - 1).coerceAtLeast(0)
                                    Triple(
                                        folderPagerState.isScrollInProgress,
                                        folderPagerState.currentPage.coerceIn(0, maxPage),
                                        isProgrammaticPagerSync,
                                    )
                                }.getOrDefault(Triple(false, 0, isProgrammaticPagerSync))
                            }
                                .distinctUntilChanged()
                                .collect { (scrolling, page, programmatic) ->
                                    if (useFolderPager && scrolling && !programmatic && swipeStartMs == null) {
                                        swipeStartMs = SystemClock.elapsedRealtime()
                                        swipeStartPage = page
                                        frameCount = 0
                                        slowFrameCount = 0
                                        maxFrameMs = 0L
                                        frameJob?.cancel()
                                        frameJob = launch {
                                            var previousFrameNanos = withFrameNanos { it }
                                            while (true) {
                                                val frameNanos = withFrameNanos { it }
                                                val frameMs = (frameNanos - previousFrameNanos) / 1_000_000L
                                                frameCount += 1
                                                if (frameMs > 24L) slowFrameCount += 1
                                                if (frameMs > maxFrameMs) maxFrameMs = frameMs
                                                previousFrameNanos = frameNanos
                                            }
                                        }
                                        val startPath = folderPagerPagesUpdated.value.getOrNull(swipeStartPage)?.path.orEmpty()
                                        KardLeafLog.d(
                                            USER_PERF_TRACE_TAG,
                                            "dashboardSwipe humanStart page=$swipeStartPage path=$startPath pathHash=${dashboardPathDebugHash(startPath)} " +
                                                "current=${folderPagerState.currentPage} settled=${folderPagerState.settledPage} pages=${folderPagerPagesUpdated.value.size}",
                                        )
                                        KardLeafLog.d(
                                            PAGER_PROBE_TAG,
                                            "swipeStart page=$swipeStartPage path=$startPath pathHash=${dashboardPathDebugHash(startPath)} " +
                                                "current=${folderPagerState.currentPage} settled=${folderPagerState.settledPage} pages=${folderPagerPathHashSummary(folderPagerPagesUpdated.value)}",
                                        )
                                    } else if (!scrolling && swipeStartMs != null) {
                                        val start = swipeStartMs ?: return@collect
                                        frameJob?.cancel()
                                        frameJob = null
                                        withFrameNanos { _ -> }
                                        val settledPage = folderPagerState.settledPage
                                        val switchedPage = swipeStartPage != settledPage
                                        val averageFrameMs = if (frameCount > 0) {
                                            (SystemClock.elapsedRealtime() - start).toFloat() / frameCount
                                        } else {
                                            0f
                                        }
                                        val fromPath = folderPagerPagesUpdated.value.getOrNull(swipeStartPage)?.path.orEmpty()
                                        val toPath = folderPagerPagesUpdated.value.getOrNull(settledPage)?.path.orEmpty()
                                        KardLeafLog.d(
                                            USER_PERF_TRACE_TAG,
                                            "dashboardSwipe humanSettled elapsed=${SystemClock.elapsedRealtime() - start}ms " +
                                                "switched=$switchedPage fromPage=$swipeStartPage toPage=$settledPage " +
                                                "fromPath=$fromPath fromHash=${dashboardPathDebugHash(fromPath)} " +
                                                "toPath=$toPath toHash=${dashboardPathDebugHash(toPath)} " +
                                                "frames=$frameCount slowFrames=$slowFrameCount maxFrame=${maxFrameMs}ms " +
                                                "avgFrame=${String.format(java.util.Locale.US, "%.1f", averageFrameMs)}ms",
                                        )
                                        KardLeafLog.d(
                                            PAGER_PROBE_TAG,
                                            "swipeSettled elapsed=${SystemClock.elapsedRealtime() - start}ms switched=$switchedPage " +
                                                "fromPage=$swipeStartPage fromHash=${dashboardPathDebugHash(fromPath)} " +
                                                "toPage=$settledPage toHash=${dashboardPathDebugHash(toPath)} " +
                                                "frames=$frameCount slowFrames=$slowFrameCount maxFrame=${maxFrameMs}ms",
                                        )
                                        swipeStartMs = null
                                    }
                                }
                        }

                        // 第二个 effect：pager 手势滑动 → 外部筛选。
                        // pages 在同级分类间保持稳定，跟随 currentPage 回写可让列表和标签在页面切换时立即更新，
                        // 不必继续等待手势结束并完全吸附。
                        // key 只含 folderPagerState（稳定引用），不含 folderPagerKey/currentFolderPath，
                        // pages 重建不会重启本 effect，避免旧页面值错误回写抵消外部切换。
                        LaunchedEffect(folderPagerState) {
                            snapshotFlow { folderPagerState.currentPage }
                                .distinctUntilChanged()
                                .collect { page ->
                                    val pages = folderPagerPagesUpdated.value
                                    val target = pages.getOrNull(page) ?: return@collect
                                    val currentPath = currentFolderPathUpdated.value
                                    logDashboardCustomSortFlash {
                                        "Dashboard currentPage collect page=$page target=${target.path} currentPath=$currentPath pages=${folderPagerPathSummary(pages)} scrolling=${folderPagerState.isScrollInProgress} programmatic=$isProgrammaticPagerSync"
                                    }
                                    if (isProgrammaticPagerSync) {
                                        logDashboardCustomSortFlash {
                                            "Dashboard currentPage skip programmatic target=${target.path} currentPath=$currentPath"
                                        }
                                        return@collect
                                    }
                                    if (target.path != currentPath) {
                                        logDashboardCustomSortFlash {
                                            "Dashboard currentPage setFilter target=${target.path} currentPath=$currentPath"
                                        }
                                        if (target.path.isEmpty()) {
                                            viewModel.setFilter(MainViewModel.NoteFilter.All)
                                        } else {
                                            viewModel.setFilter(MainViewModel.NoteFilter.Label(target.path))
                                        }
                                    }
                                }
                        }

                        if (useFolderPager) {
                            // Pager 页集合变短时，Compose 可能还会用旧的 currentPage/nearestRange
                            // 去访问新的单页列表，导致 IndexOutOfBoundsException。
                            // 这里不改筛选逻辑，只在页面集合变化时一起重建 Pager 内部 itemProvider。
                            androidx.compose.runtime.key(folderPagerKey) {
                                HorizontalPager(
                                    state = folderPagerState,
                                    modifier = Modifier.fillMaxSize(),
                                    userScrollEnabled =
                                        !isInSelectionMode &&
                                            !isFolderPagerVerticalGestureLocked &&
                                            !customSortDragModeEnabled,
                                    key = { page -> folderPagerPages.getOrNull(page)?.path ?: "__stale_folder_page_$page" },
                                ) { page ->
                                    val pagePath = folderPagerPages.getOrNull(page)?.path
                                    if (pagePath == null) {
                                        Box(modifier = Modifier.fillMaxSize())
                                        return@HorizontalPager
                                    }
                                val isRootPage = pagePath.isEmpty()
                                if (isRootPage) {
                                    val shouldSkipRootOffscreen =
                                        currentFilter !is MainViewModel.NoteFilter.All &&
                                            page != folderPagerState.currentPage
                                    if (shouldSkipRootOffscreen) {
                                        Box(modifier = Modifier.fillMaxSize())
                                        return@HorizontalPager
                                    }
                                    // 渲染 "全部笔记" 根页面（复用 uiItems）
                                    val rootCustomSortDragAvailable =
                                        currentFilter is MainViewModel.NoteFilter.All &&
                                            currentFolderSortSettings?.order == PrefsManager.SortOrder.CUSTOM &&
                                            !isSearchActive &&
                                            !isInSelectionMode
                                    val rootCustomSortDragHandleEnabled =
                                        rootCustomSortDragAvailable &&
                                            page == folderPagerState.currentPage &&
                                            page == folderPagerState.settledPage &&
                                            !folderPagerState.isScrollInProgress
                                    val activeRootNotesCount = remember(allNotes) {
                                        allNotes.count { !it.isTrashed && !it.isArchived }
                                    }
                                    val pendingActiveDeleteCount = remember(allNotes, pendingDeleteIds) {
                                        allNotes.count {
                                            !it.isTrashed &&
                                                !it.isArchived &&
                                                it.id in pendingDeleteIds
                                        }
                                    }
                                    val waitingForRootItems =
                                        currentFilter is MainViewModel.NoteFilter.All &&
                                            activeRootNotesCount > 0 &&
                                            notes.size + pendingActiveDeleteCount != activeRootNotesCount
                                    if (waitingForRootItems) {
                                        // 返回全部笔记时，notes/uiItems 会比 currentFilter 晚一帧更新。
                                        // 这里先挡掉旧分类的小列表，避免主页固定闪一下。
                                        Box(modifier = Modifier.fillMaxSize())
                                    } else {
                                        NoteGrid(
                                            uiItems = uiItems,
                                            selectedNotes = selectedNotes,
                                            isLoading = shouldShowInitialNoteLoading,
                                            notesCount = notes.size,
                                        viewMode = viewMode,
                                        cardDensity = cardDensity,
                                        showFolderTags = currentFilter is MainViewModel.NoteFilter.All || currentFilter is MainViewModel.NoteFilter.Favorites,
                                        rootFolderName = rootFolderName,
                                        showYamlTags = showYamlTagsOnLooseCards,
                                        showModifiedDate = showModifiedDateOnCards,
                                        modifiedDateFormat = cardModifiedDateFormat,
                                        showDeletedDate = currentFilter is MainViewModel.NoteFilter.Trash,
                                        showNoteTitle = showCurrentNoteTitleOnCards,
                                        showDateFilenameTitle = showDateFilenameTitleOnCards,
                                        customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                        unnamedNoteDateFormat = unnamedNoteDateFormat,
                                        searchQuery = searchQuery,
                                        listState = listState,
                                        loadImageThumbnail = activeThumbnailLoader,
                                        peekImageThumbnail = notePeekThumbnail,
                                        thumbnailTraceSource = "root page=$page hash=${dashboardPathDebugHash("")}",
                                        enableCustomSortDrag = rootCustomSortDragAvailable,
                                        customSortDragHandleEnabled = rootCustomSortDragHandleEnabled,
                                        showCustomSortDragHandleIcon = customSortDragModeEnabled && rootCustomSortDragHandleEnabled,
                                        onCustomSortOrderChanged = { paths ->
                                            viewModel.saveCurrentFolderCustomSortOrder(paths)
                                        },
                                        scrollPerfPath = "",
                                        scrollPerfEnabled = page == folderPagerState.currentPage &&
                                            page == folderPagerState.settledPage &&
                                            !folderPagerState.isScrollInProgress,
                                        onSearchJump = { note ->
                                            if (!isInSelectionMode) {
                                                onSearchNoteClick(note, searchQuery)
                                            }
                                        },
                                        onNoteClick = { note ->
                                            if (isInSelectionMode) {
                                                viewModel.toggleSelection(note)
                                            } else {
                                                KardLeafLog.d(
                                                    USER_PERF_TRACE_TAG,
                                                    "dashboardNoteClick source=rootPager filter=$currentFilter notes=${notes.size} uiItems=${uiItems.size} all=${allNotes.size} " +
                                                        "pagerScrolling=${folderPagerState.isScrollInProgress} listScrolling=${listState.isScrollInProgress} " +
                                                        "pauseBackground=$pauseBackgroundWork noteContentLen=${note.content.length} notePreviewLen=${note.contentPreview.length}",
                                                )
                                                onNoteClick(note)
                                            }
                                        },
                                            onNoteLongClick = { note ->
                                                viewModel.toggleSelection(note)
                                            },
                                        )
                                    }
                                } else {
                                    val isCurrentPage = pagePath == currentFolderPath
                                    val isRecursive =
                                        (currentFilter as? MainViewModel.NoteFilter.Label)?.recursive == true
                                    val pagePreview =
                                        remember(allNotes, pagePath, sortOrder, sortDirection, folderSortVersion) {
                                            buildFolderPagerPreviewItems(
                                                notes = allNotes,
                                                path = pagePath,
                                                defaultSortOrder = sortOrder,
                                                defaultSortDirection = sortDirection,
                                                getFolderSortSettings = viewModel::getFolderSortSettings,
                                                getFolderCustomSortOrder = viewModel::getFolderCustomSortOrder,
                                            )
                                        }
                                    val pageSortOrder = pagePreview?.sortOrder ?: sortOrder
                                    val pageSortDirection = pagePreview?.sortDirection ?: sortDirection
                                    val preciseItems = pagePreview?.items.orEmpty()
                                    // recursive 模式下当前页显示该文件夹及全部子文件夹的笔记（复用 uiItems），
                                    // 其他页仍用精确匹配的预览项
                                    val pageItems =
                                        if (isCurrentPage && isRecursive) uiItems else preciseItems
                                    val pageFilter = remember(pagePath) { MainViewModel.NoteFilter.Label(pagePath) }
                                    val pageListState = remember(pageFilter) {
                                        listStates.getOrPut(pageFilter) { LazyStaggeredGridState() }
                                    }
                                    val pageCustomSortDragAvailable =
                                        !isRecursive &&
                                            pageSortOrder == PrefsManager.SortOrder.CUSTOM &&
                                            !isSearchActive &&
                                            !isInSelectionMode
                                    val pageCustomSortDragHandleEnabled =
                                        pageCustomSortDragAvailable &&
                                            isCurrentPage &&
                                            !folderPagerState.isScrollInProgress &&
                                            page == folderPagerState.currentPage &&
                                            page == folderPagerState.settledPage
                                    LaunchedEffect(
                                        pagePath,
                                        page,
                                        isCurrentPage,
                                        isRecursive,
                                        pageSortOrder,
                                        pageSortDirection,
                                        pageCustomSortDragAvailable,
                                        pageCustomSortDragHandleEnabled,
                                        folderPagerState.currentPage,
                                        folderPagerState.settledPage,
                                        folderPagerState.isScrollInProgress,
                                    ) {
                                        logDashboardCustomSortFlash {
                                            "Dashboard pageRender page=$page path=$pagePath isCurrent=$isCurrentPage recursive=$isRecursive sort=$pageSortOrder/$pageSortDirection " +
                                                "dragAvailable=$pageCustomSortDragAvailable dragHandle=$pageCustomSortDragHandleEnabled currentPage=${folderPagerState.currentPage} settled=${folderPagerState.settledPage} scrolling=${folderPagerState.isScrollInProgress} " +
                                                "thumbnailLoading=continuous pathHash=${dashboardPathDebugHash(pagePath)} items=${dashboardScreenUiItemsFlashSummary(pageItems)} ${dashboardThumbnailProbeSummary(pageItems)}"
                                        }
                                    }
                                    NoteGrid(
                                        uiItems = pageItems,
                                        selectedNotes = selectedNotes,
                                        isLoading = shouldShowInitialNoteLoading && isCurrentPage,
                                        notesCount =
                                            if (isCurrentPage && isRecursive) {
                                                notes.size
                                            } else {
                                                pageItems.count { it is DashboardUiItem.NoteItem }
                                            },
                                        viewMode = viewMode,
                                        cardDensity = cardDensity,
                                        showFolderTags = isCurrentPage && isRecursive,
                                        rootFolderName = rootFolderName,
                                        showYamlTags = showYamlTagsOnLooseCards,
                                        showModifiedDate = showModifiedDateOnCards,
                                        modifiedDateFormat = cardModifiedDateFormat,
                                        showDeletedDate = currentFilter is MainViewModel.NoteFilter.Trash,
                                        showNoteTitle = showCurrentNoteTitleOnCards,
                                        showDateFilenameTitle = showDateFilenameTitleOnCards,
                                        customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                        unnamedNoteDateFormat = unnamedNoteDateFormat,
                                        searchQuery = searchQuery,
                                        listState = pageListState,
                                        loadImageThumbnail = activeThumbnailLoader,
                                        peekImageThumbnail = notePeekThumbnail,
                                        thumbnailTraceSource = "folder page=$page hash=${dashboardPathDebugHash(pagePath)}",
                                        enableCustomSortDrag = pageCustomSortDragAvailable,
                                        customSortDragHandleEnabled = pageCustomSortDragHandleEnabled,
                                        showCustomSortDragHandleIcon = customSortDragModeEnabled && pageCustomSortDragHandleEnabled,
                                        onCustomSortOrderChanged = { paths ->
                                            logDashboardCustomSortFlash {
                                                "Dashboard page onCustomSortOrderChanged page=$page path=$pagePath paths=${pathListFlashSummary(paths)}"
                                            }
                                            viewModel.saveCurrentFolderCustomSortOrder(paths)
                                        },
                                        scrollPerfPath = pagePath,
                                        scrollPerfEnabled = isCurrentPage &&
                                            page == folderPagerState.currentPage &&
                                            page == folderPagerState.settledPage &&
                                            !folderPagerState.isScrollInProgress,
                                        onSearchJump = { note ->
                                            if (!isInSelectionMode) {
                                                onSearchNoteClick(note, searchQuery)
                                            }
                                        },
                                        onNoteClick = { note ->
                                            if (isInSelectionMode) {
                                                viewModel.toggleSelection(note)
                                            } else {
                                                KardLeafLog.d(
                                                    USER_PERF_TRACE_TAG,
                                                    "dashboardNoteClick source=folderPager page=$page current=$isCurrentPage recursive=$isRecursive filter=$currentFilter " +
                                                        "notes=${notes.size} pageItems=${pageItems.size} all=${allNotes.size} " +
                                                        "pagerScrolling=${folderPagerState.isScrollInProgress} listScrolling=${pageListState.isScrollInProgress} " +
                                                        "pauseBackground=$pauseBackgroundWork noteContentLen=${note.content.length} notePreviewLen=${note.contentPreview.length}",
                                                )
                                                onNoteClick(note)
                                            }
                                        },
                                        onNoteLongClick = { note ->
                                            viewModel.toggleSelection(note)
                                        },
                                    )
                                }
                            }
                            }
                        } else {
                            val currentFolderCustomSortDragEnabled = remember(
                                currentFilter,
                                currentFolderPath,
                                currentFolderSortSettings,
                                folderSortVersion,
                                sortOrder,
                                searchQuery,
                                isInSelectionMode,
                            ) {
                                val folderFilter = currentFilter as? MainViewModel.NoteFilter.Label
                                val canCustomSortCurrentFilter =
                                    currentFilter is MainViewModel.NoteFilter.All ||
                                        (folderFilter != null && !folderFilter.recursive && currentFolderPath.isNotBlank())
                                canCustomSortCurrentFilter &&
                                    !isSearchActive &&
                                    !isInSelectionMode &&
                                    (currentFolderSortSettings?.order ?: sortOrder) == PrefsManager.SortOrder.CUSTOM
                            }
                            LaunchedEffect(
                                currentFilter,
                                currentFolderPath,
                                folderSortVersion,
                                sortOrder,
                                searchQuery,
                                isInSelectionMode,
                                currentFolderCustomSortDragEnabled,
                            ) {
                                logDashboardCustomSortFlash {
                                    "Dashboard singlePageRender filter=$currentFilter path=$currentFolderPath sort=$sortOrder " +
                                        "drag=$currentFolderCustomSortDragEnabled searchActive=$isSearchActive selection=$isInSelectionMode items=${dashboardScreenUiItemsFlashSummary(uiItems)} notes=${notes.size}"
                                }
                            }

                            NoteGrid(
                                uiItems = uiItems,
                                selectedNotes = selectedNotes,
                                isLoading = shouldShowInitialNoteLoading,
                                notesCount = notes.size,
                                viewMode = viewMode,
                                cardDensity = cardDensity,
                                showFolderTags = currentFilter is MainViewModel.NoteFilter.All || currentFilter is MainViewModel.NoteFilter.Favorites,
                                rootFolderName = rootFolderName,
                                showYamlTags = showYamlTagsOnLooseCards,
                                showModifiedDate = showModifiedDateOnCards,
                                modifiedDateFormat = cardModifiedDateFormat,
                                showDeletedDate = currentFilter is MainViewModel.NoteFilter.Trash,
                                showNoteTitle = showCurrentNoteTitleOnCards,
                                showDateFilenameTitle = showDateFilenameTitleOnCards,
                                customHiddenFilenamePatterns = customHiddenFilenamePatterns,
                                unnamedNoteDateFormat = unnamedNoteDateFormat,
                                searchQuery = searchQuery,
                                listState = listState,
                                loadImageThumbnail = activeThumbnailLoader,
                                peekImageThumbnail = notePeekThumbnail,
                                thumbnailTraceSource = "single hash=${dashboardPathDebugHash(currentFolderPath)}",
                                enableCustomSortDrag = currentFolderCustomSortDragEnabled,
                                customSortDragHandleEnabled = currentFolderCustomSortDragEnabled,
                                showCustomSortDragHandleIcon = customSortDragModeEnabled && currentFolderCustomSortDragEnabled,
                                onCustomSortOrderChanged = { paths ->
                                    logDashboardCustomSortFlash {
                                        "Dashboard singlePage onCustomSortOrderChanged path=$currentFolderPath paths=${pathListFlashSummary(paths)}"
                                    }
                                    viewModel.saveCurrentFolderCustomSortOrder(paths)
                                },
                                scrollPerfPath = currentFolderPath,
                                scrollPerfEnabled = true,
                                onSearchJump = { note ->
                                    if (!isInSelectionMode) {
                                        onSearchNoteClick(note, searchQuery)
                                    }
                                },
                                onNoteClick = { note ->
                                    if (isInSelectionMode) {
                                        viewModel.toggleSelection(note)
                                    } else {
                                        KardLeafLog.d(
                                            USER_PERF_TRACE_TAG,
                                            "dashboardNoteClick source=singlePage filter=$currentFilter notes=${notes.size} uiItems=${uiItems.size} all=${allNotes.size} " +
                                                "listScrolling=${listState.isScrollInProgress} pauseBackground=$pauseBackgroundWork " +
                                                "noteContentLen=${note.content.length} notePreviewLen=${note.contentPreview.length}",
                                        )
                                        onNoteClick(note)
                                    }
                                },
                                onNoteLongClick = { note ->
                                    viewModel.toggleSelection(note)
                                },
                            )
                        }

                        if (showPullRefreshCircle &&
                            !customSortDragRefreshBlocked &&
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
                                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (homeBottomToolbarItems.isNotEmpty() && homeActionStyle == PrefsManager.HomeActionStyle.BOTTOM_TOOLBAR) {
            AnimatedVisibility(
                visible = shouldShowHomeBottomToolbar && homeBottomToolbarVisible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(animationSpec = tween(HOME_BOTTOM_TOOLBAR_ENTER_DURATION_MS)) +
                    slideInVertically(animationSpec = tween(HOME_BOTTOM_TOOLBAR_ENTER_DURATION_MS)) { it },
                exit = fadeOut(animationSpec = tween(HOME_BOTTOM_TOOLBAR_EXIT_DURATION_MS)) +
                    slideOutVertically(animationSpec = tween(HOME_BOTTOM_TOOLBAR_EXIT_DURATION_MS)) { it },
            ) {
                HomeBottomToolbar(
                    items = homeBottomToolbarItems,
                    buttonSizeDp = homeBottomToolbarButtonSizeDp,
                    onItemClick = ::openHomeBottomToolbarItem,
                )
            }
        }
        if (showFolderNavigationPanel) {
            FolderNavigationPanel(
                labels = labels,
                notes = allNotes,
                currentFilter = currentFilter,
                showTags = folderNavigationShowTags,
                editMode = folderNavigationEditMode,
                onEditModeChange = { folderNavigationEditMode = it },
                focusedParentPath = folderNavigationFocusedParentPath,
                onFocusedParentPathChange = { folderNavigationFocusedParentPath = it },
                yamlTags = yamlTags,
                noteCountByYamlTag = noteCountByYamlTag,
                dragProgress = folderNavigationPanelProgress,
                folderOrderVersion = folderManagerOrderVersion,
                getFolderDisplayOrder = viewModel::getFolderDisplayOrder,
                onSaveFolderDisplayOrder = viewModel::saveFolderDisplayOrder,
                onCreateFolder = viewModel::createLabel,
                onRenameFolder = { oldPath, newPath, onError ->
                    viewModel.renameLabel(
                        oldPath = oldPath,
                        newPath = newPath,
                        onError = onError,
                    )
                },
                onDeleteFolder = { path, onSuccess, onError ->
                    viewModel.deleteLabelWithContents(
                        name = path,
                        onSuccess = onSuccess,
                        onError = onError,
                    )
                },
                onDismiss = {
                    closeFolderNavigationPanel()
                },
                onSelect = { filter ->
                    viewModel.setFilter(filter)
                    closeFolderNavigationPanel()
                },
            )
        }
        if (showWebClipImportDialog) {
            WebClipImportDialog(
                onDismiss = { showWebClipImportDialog = false },
                onImported = { draft ->
                    showWebClipImportDialog = false
                    onWebClipImported(draft)
                },
                targetFolder = (currentFilter as? MainViewModel.NoteFilter.Label)?.name.orEmpty(),
                importImage = viewModel::importImage,
            )
        }
        if (showSampleCleanupConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showSampleCleanupConfirmDialog = false },
                title = { Text("清空示例内容") },
                text = { Text("确认删除示例文件夹里的示例笔记吗？删除后可以立即撤回。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSampleCleanupConfirmDialog = false
                            showSampleCleanupPrompt = false
                            onSampleCleanupPromptConsumed()
                            coroutineScope.launch {
                                if (onClearSampleVaultSamples()) {
                                    viewModel.refreshNotes()
                                    showSampleCleanupUndoSnackbar("已清空示例内容")
                                } else {
                                    showThemedSnackbar("清空示例内容失败")
                                }
                            }
                        },
                    ) {
                        Text("确认删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSampleCleanupConfirmDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
        if (showSampleCleanupPrompt) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    modifier =
                        Modifier
                            .padding(horizontal = 16.dp)
                            .padding(top = 20.dp, bottom = 80.dp)
                            .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "是否清空示例文件夹内容？",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                showSampleCleanupPrompt = false
                                onSampleCleanupPromptConsumed()
                            },
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        TextButton(onClick = { showSampleCleanupConfirmDialog = true }) {
                            Text("删除")
                        }
                    }
                }
            }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RandomNoteReviewView(
    notes: List<Note>,
    sessionSeed: Long,
    viewModel: MainViewModel,
    onEdit: (Note) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reviewNotes = remember(notes) { notes.filter { it.content.isNotBlank() } }
    val noteIds = remember(reviewNotes) { reviewNotes.map { it.id } }
    val notesById = remember(reviewNotes) { reviewNotes.associateBy { it.id } }
    val pageNoteIds = remember(sessionSeed) { mutableStateListOf<String>() }
    val scrollRatios = remember(sessionSeed) { mutableStateMapOf<String, Float>() }
    var committedPage by remember(sessionSeed) { mutableIntStateOf(0) }
    var pagerGeneration by remember(sessionSeed) { mutableIntStateOf(0) }

    fun pickRandomNoteId(excludingId: String?): String? {
        if (noteIds.isEmpty()) return null
        val candidates = if (noteIds.size > 1) noteIds.filterNot { it == excludingId } else noteIds
        return candidates.random()
    }

    fun prepareNextRandom(afterPage: Int) {
        val currentId = pageNoteIds.getOrNull(afterPage) ?: return
        val nextId = pickRandomNoteId(excludingId = currentId) ?: return
        val nextPage = afterPage + 1
        if (nextPage <= pageNoteIds.lastIndex) {
            pageNoteIds[nextPage] = nextId
        } else {
            pageNoteIds.add(nextId)
        }
    }

    fun resetRandomPages() {
        pageNoteIds.clear()
        pickRandomNoteId(excludingId = null)?.let(pageNoteIds::add)
        committedPage = 0
        if (pageNoteIds.isNotEmpty()) {
            prepareNextRandom(afterPage = 0)
        }
        pagerGeneration += 1
    }

    LaunchedEffect(sessionSeed, noteIds) {
        if (reviewNotes.isEmpty()) {
            pageNoteIds.clear()
            scrollRatios.clear()
            committedPage = 0
            pagerGeneration += 1
            return@LaunchedEffect
        }
        val validIds = noteIds.toSet()
        scrollRatios.keys.toList().forEach { noteId ->
            if (noteId !in validIds) scrollRatios.remove(noteId)
        }
        val currentId = pageNoteIds.getOrNull(committedPage)
        if (currentId == null || currentId !in validIds || pageNoteIds.any { it !in validIds }) {
            resetRandomPages()
        } else {
            prepareNextRandom(afterPage = committedPage)
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        if (pageNoteIds.isEmpty()) {
            Text(
                text = "没有可预习的笔记",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            androidx.compose.runtime.key(pagerGeneration) {
                val pagerState = rememberPagerState(
                    initialPage = committedPage.coerceIn(0, pageNoteIds.lastIndex),
                    pageCount = { pageNoteIds.size },
                )
                val pagerScope = rememberCoroutineScope()
                var pagerSettleJob by remember(pagerState) { mutableStateOf<Job?>(null) }
                var pagerDragJob by remember(pagerState) { mutableStateOf<Job?>(null) }
                var pagerDragChannel by remember(pagerState) { mutableStateOf<Channel<Float>?>(null) }
                var dragStartPage by remember(pagerState) { mutableIntStateOf(pagerState.currentPage) }
                var pagerWidthPx by remember(pagerState) { mutableStateOf(1f) }
                val minimumFlingVelocityPx = with(LocalDensity.current) { 400.dp.toPx() }

                fun settlePager(totalDeltaX: Float, velocityX: Float) {
                    val pageWidth = pagerWidthPx.coerceAtLeast(1f)
                    val velocityPageDelta = when {
                        velocityX <= -minimumFlingVelocityPx -> 1
                        velocityX >= minimumFlingVelocityPx -> -1
                        else -> 0
                    }
                    val distancePageDelta = when {
                        totalDeltaX <= -pageWidth * 0.5f -> 1
                        totalDeltaX >= pageWidth * 0.5f -> -1
                        else -> 0
                    }
                    val requestedPageDelta = if (velocityPageDelta != 0) velocityPageDelta else distancePageDelta
                    val targetPage = (dragStartPage + requestedPageDelta).coerceIn(0, pageNoteIds.lastIndex)
                    val activeDragJob = pagerDragJob
                    pagerDragChannel?.close()
                    pagerDragChannel = null
                    pagerSettleJob?.cancel()
                    pagerSettleJob = pagerScope.launch {
                        activeDragJob?.join()
                        pagerState.animateScrollToPage(targetPage)
                    }
                    KardLeafLog.d(
                        "KardLeafRandomReview",
                        "dragRelease startPage=$dragStartPage targetPage=$targetPage " +
                            "totalDx=${totalDeltaX.toInt()} velocityX=${velocityX.toInt()} " +
                            "pageFraction=${String.format(java.util.Locale.US, "%.2f", totalDeltaX / pageWidth)}",
                    )
                }

                LaunchedEffect(pagerState, noteIds) {
                    snapshotFlow { pagerState.settledPage }
                        .distinctUntilChanged()
                        .collect { settledPage ->
                            if (settledPage == committedPage) return@collect
                            val fromPage = committedPage
                            committedPage = settledPage
                            prepareNextRandom(afterPage = settledPage)
                            KardLeafLog.d(
                                "KardLeafRandomReview",
                                "pagerSettled fromPage=$fromPage toPage=$settledPage " +
                                    "direction=${if (settledPage > fromPage) "random" else "previous"} " +
                                    "pageCount=${pageNoteIds.size}",
                            )
                        }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { pagerWidthPx = it.width.toFloat().coerceAtLeast(1f) },
                    userScrollEnabled = false,
                    key = { page ->
                        val noteId = pageNoteIds.getOrNull(page).orEmpty()
                        "$page:$noteId"
                    },
                ) { page ->
                    val noteId = pageNoteIds.getOrNull(page)
                    val note = noteId?.let(notesById::get)
                    if (note == null) {
                        Box(modifier = Modifier.fillMaxSize())
                        return@HorizontalPager
                    }

                    var previewContent by remember(note.id, note.content) {
                        mutableStateOf(note.content)
                    }
                    val previewController = remember(page, note.id) { PreviewWebViewController() }
                    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val previewThemeContext = LocalContext.current
                    val previewThemeId = remember { PrefsManager(previewThemeContext).getPreviewTheme().name.lowercase() }

                    LaunchedEffect(note.id, note.content) {
                        previewContent = note.content
                        previewContent = try {
                            viewModel.preparePreviewMarkdown(note.content, note.folder)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            note.content
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        PreviewWebView(
                            content = previewContent,
                            sessionKey = "random-review:$page:${note.id}:${note.lastModified.time}",
                            isDark = isDark,
                            controller = previewController,
                            modifier = Modifier.fillMaxSize(),
                            previewTheme = previewThemeId,
                            onScrollRatioChanged = { ratio -> scrollRatios[note.id] = ratio },
                            onContentRendered = { _, _ ->
                                previewController.fastScrollToRatio(scrollRatios[note.id] ?: 0f)
                            },
                            onCheckboxToggled = { _, _ -> },
                            onHorizontalPagerDragStart = {
                                pagerSettleJob?.cancel()
                                pagerDragJob?.cancel()
                                pagerDragChannel?.close()
                                dragStartPage = pagerState.currentPage
                                val dragChannel = Channel<Float>(Channel.UNLIMITED)
                                pagerDragChannel = dragChannel
                                pagerDragJob = pagerScope.launch {
                                    pagerState.scroll(MutatePriority.UserInput) {
                                        for (deltaX in dragChannel) {
                                            scrollBy(-deltaX)
                                        }
                                    }
                                }
                                KardLeafLog.d(
                                    "KardLeafRandomReview",
                                    "dragStart page=$dragStartPage offset=${pagerState.currentPageOffsetFraction}",
                                )
                            },
                            onHorizontalPagerDrag = { deltaX ->
                                pagerDragChannel?.trySend(deltaX)
                            },
                            onHorizontalPagerDragEnd = { totalDeltaX, velocityX ->
                                settlePager(totalDeltaX, velocityX)
                            },
                            onHorizontalPagerDragCancel = {
                                settlePager(totalDeltaX = 0f, velocityX = 0f)
                            },
                        )

                        FilledIconButton(
                            onClick = { onEdit(note) },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(20.dp)
                                .size(56.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "编辑笔记",
                            )
                        }
                    }
                }
            }
        }
    }
}
