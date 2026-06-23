package com.kangle.kardleaf.ui

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EDITOR_TRACE_TAG = "KardLeafEditorTrace"
private const val EDITOR_GESTURE_TAG = "KardLeafGestureTrace"
private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val MENU_REOPEN_GUARD_MS = 250L
private const val DIRECT_EDIT_MAX_CHARS = 600_000

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    initialLabel: String = "",
    privacyNoteId: Long? = null,
    privacyInitialTitle: String? = null,
    privacyInitialContent: String? = null,
    privacyDocumentKey: String? = null,
    onSavePrivacyNote: ((Long, String, String, (Long) -> Unit) -> Unit)? = null,
    onDeletePrivacyNote: (() -> Unit)? = null,
    onPickImage: (((Uri) -> Unit) -> Unit)? = null,
) {
    val currentNote by viewModel.currentNote.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState(initial = emptyList())
    val externalDraft by viewModel.externalNoteDraft.collectAsState()
    val isEditorOpen by viewModel.isEditorOpen.collectAsState()
    val isOpeningNoteContent by viewModel.isOpeningNoteContent.collectAsState()
    val isPrivacyEditor = privacyDocumentKey != null
    val effectiveEditorOpen = isPrivacyEditor || isEditorOpen
    val labels by viewModel.labels.collectAsState()
    val externalConflict by viewModel.externalConflict.collectAsState()
    var noteHistory by remember { mutableStateOf<List<NoteHistory>>(emptyList()) }
    var noteRemarks by remember { mutableStateOf<List<NoteRemark>>(emptyList()) }
    var noteRemarkDraft by remember { mutableStateOf("") }
    var noteRemarkRefreshVersion by remember { mutableStateOf(0) }
    var noteFrontMatterProperties by remember { mutableStateOf<List<NoteFormatUtils.FrontMatterProperty>>(emptyList()) }
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val defaultOpenNoteMode = remember {
        KardLeafCustomFeatures.getOpenNoteMode(context)
    }
    val toolbarOrder = remember {
        KardLeafCustomFeatures.getToolbarOrder(context)
    }
    val notePrefsManager = remember { PrefsManager(context) }
    val noteSidePanelsEnabled = remember {
        notePrefsManager.isNoteSidePanelsEnabled()
    }
    val previewDoubleTapIntervalMs = remember { notePrefsManager.getPreviewDoubleTapIntervalMs() }

    val initialTitle = if (isPrivacyEditor) privacyInitialTitle.orEmpty() else currentNote?.title ?: externalDraft?.title.orEmpty()
    val rawInitialContent = if (isPrivacyEditor) privacyInitialContent.orEmpty() else currentNote?.content ?: externalDraft?.content.orEmpty()
    val initialFrontMatter = remember(rawInitialContent) { NoteFormatUtils.parseFrontMatter(rawInitialContent) }
    val initialContent = initialFrontMatter.cleanContent
    val noteSidePanelProperties = remember(noteFrontMatterProperties, currentNote, initialTitle) {
        buildNoteSidePanelProperties(noteFrontMatterProperties, currentNote, initialTitle)
    }
    // The complete editor body lives inside KardLeafNativeEditorView. Compose keeps
    // only lightweight chrome state and reads a full snapshot on save/preview/search/outline.
    val editorController = remember { KardLeafEditorController() }
    val previewController = remember { PreviewWebViewController() }
    val fastScrollSignal = remember { EditorFastScrollSignal() }
    val editorDocumentKey = privacyDocumentKey ?: currentNote?.id ?: "external:${externalDraft.hashCode()}:$initialLabel"
    var noteTextStats by remember(editorDocumentKey) { mutableStateOf<NoteTextStats?>(null) }
    var effectivePrivacyNoteId by remember(privacyDocumentKey) { mutableStateOf(privacyNoteId ?: 0L) }
    var privacyEditorDirty by remember(privacyDocumentKey) { mutableStateOf(false) }
    editorController.acceptInitialSnapshot(editorDocumentKey, initialTitle, initialContent)
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    val isTemporaryDraft = currentNote == null && externalDraft?.isTemporary == true
    var folder by remember(currentNote, externalDraft, initialLabel, isPrivacyEditor) {
        mutableStateOf(
            if (isPrivacyEditor) {
                ""
            } else {
                currentNote?.folder?.takeIf { it != "Unknown" }
                    ?: externalDraft?.folder?.takeIf { it.isNotBlank() }
                    ?: initialLabel
            },
        )
    }
    var renderedPreview by remember(editorDocumentKey) { mutableStateOf("") }
    var previewRenderToken by remember(editorDocumentKey) { mutableStateOf(0) }
    var previewScrollRatio by remember(editorDocumentKey) { mutableStateOf(0f) }
    var pendingEditScrollRatio by remember(editorDocumentKey) { mutableStateOf<Float?>(null) }
    var pendingEditScrollOffset by remember(editorDocumentKey) { mutableStateOf<Int?>(null) }

    val isNewPrivacyNote = isPrivacyEditor && (privacyNoteId ?: 0L) <= 0L
    val blocksDirectEditForLargeNote = !isNewPrivacyNote && initialContent.length > DIRECT_EDIT_MAX_CHARS
    var isEditing by remember(editorDocumentKey, defaultOpenNoteMode, blocksDirectEditForLargeNote) {
        mutableStateOf(
            !blocksDirectEditForLargeNote &&
                (isNewPrivacyNote ||
                    (!isPrivacyEditor && currentNote == null) ||
                    defaultOpenNoteMode == KardLeafCustomFeatures.OpenNoteMode.EDIT),
        )
    }

    var editorFocusRequestToken by remember { mutableStateOf(0) }

    // UI state
    var showLabelMenu by remember { mutableStateOf(false) }
    var lastLabelMenuDismissAt by remember { mutableStateOf(0L) }
    var showCreateLabelDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var lastMoreMenuDismissAt by remember { mutableStateOf(0L) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showNoteInfoDialog by remember { mutableStateOf(false) }
    var showHeadingMenu by remember { mutableStateOf(false) }
    var lastHeadingMenuDismissAt by remember { mutableStateOf(0L) }
    var showMathMenu by remember { mutableStateOf(false) }
    var lastMathMenuDismissAt by remember { mutableStateOf(0L) }
    var showNoteSearch by remember { mutableStateOf(false) }
    var noteSearchQuery by remember { mutableStateOf("") }
    var noteSearchFocused by remember { mutableStateOf(false) }
    var previewHeadingScrollToken by remember { mutableStateOf(0) }
    var previewHeadingScrollText by remember { mutableStateOf("") }
    var previewHeadingScrollLevel by remember { mutableStateOf(0) }
    var requestKeyboardOnEdit by remember { mutableStateOf(false) }
    var isLeavingEditor by remember { mutableStateOf(false) }
    var isClosingEditor by remember { mutableStateOf(false) }
    var isBottomToolbarExpanded by remember { mutableStateOf(false) }
    var toolbarDragFraction by remember { mutableStateOf(if (isBottomToolbarExpanded) 1f else 0f) }
    val coroutineScope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val noteSidePanelWidth = 320.dp
    val noteSidePanelWidthPx = with(density) { noteSidePanelWidth.toPx() }
    val noteSidePanelOpenThresholdPx = with(density) { 36.dp.toPx() }
    var noteSidePanelTargetPx by remember { mutableStateOf(0f) }
    var noteSidePanelDragPx by remember { mutableStateOf(0f) }
    var noteSidePanelDragStartPx by remember { mutableStateOf(0f) }
    var isNoteSidePanelDragging by remember { mutableStateOf(false) }
    var noteSidePanelsReady by remember(editorDocumentKey) { mutableStateOf(false) }
    val noteSidePanelOffsetPx by animateFloatAsState(
        targetValue = if (isNoteSidePanelDragging) noteSidePanelDragPx else noteSidePanelTargetPx,
        animationSpec = if (isNoteSidePanelDragging) snap() else spring(),
        label = "noteSidePanel",
    )
    val noteSidePanelScrimInteractionSource = remember { MutableInteractionSource() }
    val noteSidePanelVisibleFraction =
        (abs(noteSidePanelOffsetPx) / noteSidePanelWidthPx).coerceIn(0f, 1f)
    val noteSidePanelsActive = noteSidePanelsEnabled && noteSidePanelsReady && !isClosingEditor
    val noteSidePanelEdgeWidth = 28.dp
    val noteSidePanelEditorReserveRadiusPx = with(density) { 48.dp.toPx() }
    var noteSidePanelGestureRootX by remember { mutableStateOf(0f) }
    var noteSidePanelGestureRootY by remember { mutableStateOf(0f) }
    val latestNoteSidePanelOffsetPx by rememberUpdatedState(noteSidePanelOffsetPx)
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    var outlineHeadings by remember { mutableStateOf<List<MarkdownHeading>>(emptyList()) }
    var shouldShowBottomToolbar by remember { mutableStateOf(false) }

    LaunchedEffect(
        editorDocumentKey,
        effectiveEditorOpen,
        currentNote?.file?.path,
        currentNote?.content?.length,
        currentNote?.contentPreview?.length,
        externalDraft?.content?.length,
    ) {
        Log.d(
            EDITOR_TRACE_TAG,
            "screen state key=$editorDocumentKey open=$effectiveEditorOpen editing=$isEditing " +
                "notePath=${currentNote?.file?.path} noteTitleLen=${currentNote?.title?.length ?: -1} " +
                "noteContentLen=${currentNote?.content?.length ?: -1} notePreviewLen=${currentNote?.contentPreview?.length ?: -1} " +
                "draftContentLen=${externalDraft?.content?.length ?: -1} initialTitleLen=${initialTitle.length} initialContentLen=${initialContent.length}",
        )
        if (effectiveEditorOpen && currentNote != null && currentNote!!.content.isEmpty() && currentNote!!.contentPreview.isNotEmpty()) {
            Log.w(
                EDITOR_TRACE_TAG,
                "screen suspicious blank note path=${currentNote!!.file.path} previewLen=${currentNote!!.contentPreview.length}",
            )
        }
    }

    LaunchedEffect(isEditing, isKeyboardVisible) {
        if (!isEditing) {
            shouldShowBottomToolbar = false
        } else if (isKeyboardVisible) {
            shouldShowBottomToolbar = true
        } else {
            delay(220L)
            shouldShowBottomToolbar = false
        }
    }

    fun closeNoteSidePanel() {
        noteSidePanelTargetPx = 0f
        noteSidePanelDragPx = 0f
        noteSidePanelDragStartPx = 0f
        isNoteSidePanelDragging = false
    }

    fun startNoteSidePanelDrag() {
        isNoteSidePanelDragging = true
        noteSidePanelDragStartPx = latestNoteSidePanelOffsetPx
        noteSidePanelDragPx = latestNoteSidePanelOffsetPx
    }

    fun dragNoteSidePanelBy(dragAmount: Float): Boolean {
        val previousOffset = noteSidePanelDragPx
        noteSidePanelDragPx =
            (noteSidePanelDragPx + dragAmount)
                .coerceIn(-noteSidePanelWidthPx, noteSidePanelWidthPx)
        return abs(noteSidePanelDragPx - previousOffset) > 0.5f
    }

    fun settleNoteSidePanelDrag() {
        val dragDelta = noteSidePanelDragPx - noteSidePanelDragStartPx
        noteSidePanelTargetPx =
            when {
                noteSidePanelDragStartPx < -noteSidePanelOpenThresholdPx &&
                    dragDelta > noteSidePanelOpenThresholdPx -> 0f
                noteSidePanelDragStartPx > noteSidePanelOpenThresholdPx &&
                    dragDelta < -noteSidePanelOpenThresholdPx -> 0f
                noteSidePanelDragPx > noteSidePanelOpenThresholdPx -> noteSidePanelWidthPx
                noteSidePanelDragPx < -noteSidePanelOpenThresholdPx -> -noteSidePanelWidthPx
                else -> 0f
            }
        noteSidePanelDragPx = noteSidePanelTargetPx
        noteSidePanelDragStartPx = noteSidePanelTargetPx
        isNoteSidePanelDragging = false
    }

    fun cancelNoteSidePanelDrag() {
        noteSidePanelDragPx = noteSidePanelTargetPx
        noteSidePanelDragStartPx = noteSidePanelTargetPx
        isNoteSidePanelDragging = false
    }

    fun Modifier.noteSidePanelDrag(
        enabled: Boolean,
        protectEditorTouch: Boolean = false,
    ): Modifier =
        if (enabled) {
            pointerInput(enabled, noteSidePanelWidthPx, protectEditorTouch, isEditing, isKeyboardVisible) {
                awaitPointerEventScope {
                    while (true) {
                        var down = awaitPointerEvent(PointerEventPass.Initial)
                            .changes
                            .firstOrNull { it.pressed }
                        while (down == null) {
                            down = awaitPointerEvent(PointerEventPass.Initial)
                                .changes
                                .firstOrNull { it.pressed }
                        }
                        if (protectEditorTouch) {
                            val windowX = noteSidePanelGestureRootX + down.position.x
                            val windowY = noteSidePanelGestureRootY + down.position.y
                            if (editorController.shouldReserveContentTouchForEditing(
                                    windowX = windowX,
                                    windowY = windowY,
                                    radiusPx = noteSidePanelEditorReserveRadiusPx,
                                )
                            ) {
                                Log.d(
                                    EDITOR_GESTURE_TAG,
                                    "note side panel reserved for editor x=$windowX y=$windowY editing=$isEditing keyboard=$isKeyboardVisible",
                                )
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                } while (event.changes.any { it.pressed })
                                continue
                            }
                        }

                        val pointerId = down.id
                        val touchSlop = viewConfiguration.touchSlop
                        var totalDx = 0f
                        var totalDy = 0f
                        var lockedHorizontal = false
                        var lockedVertical = false
                        var startedDrag = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                if (startedDrag) {
                                    settleNoteSidePanelDrag()
                                    Log.d(EDITOR_GESTURE_TAG, "note side panel drag end offset=$noteSidePanelTargetPx")
                                } else {
                                    cancelNoteSidePanelDrag()
                                }
                                break
                            }

                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y
                            val absDx = abs(totalDx)
                            val absDy = abs(totalDy)

                            if (!lockedHorizontal && !lockedVertical && (absDx > touchSlop || absDy > touchSlop)) {
                                when {
                                    absDy >= absDx * 1.2f -> {
                                        lockedVertical = true
                                        Log.d(
                                            EDITOR_GESTURE_TAG,
                                            "note side panel ignore vertical dx=$totalDx dy=$totalDy editing=$isEditing keyboard=$isKeyboardVisible",
                                        )
                                    }
                                    absDx > absDy * 1.5f -> {
                                        lockedHorizontal = true
                                        startedDrag = true
                                        startNoteSidePanelDrag()
                                        if (dragNoteSidePanelBy(totalDx)) {
                                            change.consume()
                                        }
                                        Log.d(
                                            EDITOR_GESTURE_TAG,
                                            "note side panel lock horizontal dx=$totalDx dy=$totalDy editing=$isEditing keyboard=$isKeyboardVisible",
                                        )
                                    }
                                }
                            } else if (lockedHorizontal) {
                                if (dragNoteSidePanelBy(delta.x)) {
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
        } else {
            this
        }

    val noteSidePanelHasOffset = noteSidePanelVisibleFraction > 0.01f
    val noteSidePanelContentDragModifier = Modifier.noteSidePanelDrag(
        enabled = noteSidePanelsActive && !noteSidePanelHasOffset && (!isEditing || !isKeyboardVisible),
        protectEditorTouch = isEditing,
    )
    val noteSidePanelActiveDragModifier = Modifier.noteSidePanelDrag(noteSidePanelsActive && noteSidePanelHasOffset)
    val noteSidePanelEdgeDragModifier =
        Modifier.noteSidePanelDrag(noteSidePanelsActive && (!noteSidePanelHasOffset || isNoteSidePanelDragging))

    // Keep the editor chrome stable while scrolling. Hiding/showing bars during
    // downward drags changes Scaffold padding and makes long notes feel jerky.
    var showBars by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {}
    }

    LaunchedEffect(editorDocumentKey) {
        noteSidePanelsReady = false
        delay(500L)
        noteSidePanelsReady = true
    }

    LaunchedEffect(noteSidePanelsActive) {
        if (!noteSidePanelsActive) {
            closeNoteSidePanel()
        }
    }

    LaunchedEffect(isEditing) {
        showBars = true
    }

    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            isBottomToolbarExpanded = false
            toolbarDragFraction = 0f
        }
    }

    // Helpers

    /** Builds a [Note] from the current native editor snapshot. */
    fun buildCurrentNote(): Note {
        val snapshot = editorController.getSnapshot()
        val parentPath = folder
        val fileName = currentNote?.file?.name?.takeIf { it.isNotEmpty() } ?: "new_note_placeholder"
        val autoTitle =
            if (snapshot.title.isNotEmpty()) {
                snapshot.title
            } else {
                KardLeafCustomFeatures.formatUnnamedNoteTitle(context)
            }
        return Note(
            file = File(parentPath, fileName),
            title = autoTitle,
            content = snapshot.content,
            lastModified = Date(),
            createdAt = currentNote?.createdAt ?: Date(),
            color = 0xFFFFFFFF,
            reminder = null,
            isPinned = currentNote?.isPinned ?: externalDraft?.isPinned ?: false,
            isFavorite = currentNote?.isFavorite ?: false,
            isArchived = currentNote?.isArchived ?: false,
            isTrashed = currentNote?.isTrashed ?: false,
        )
    }

    fun addCurrentNoteToPrivacy(
        note: Note,
        onMoved: () -> Unit = {},
    ) {
        val snapshot = editorController.getSnapshot()
        val privacyTitle = snapshot.title.ifBlank { note.title.ifBlank { note.file.nameWithoutExtension } }
        val privacyContent = snapshot.content.ifBlank { note.content }
        if (privacyTitle.isBlank() && privacyContent.isBlank()) {
            Toast.makeText(context, "当前笔记为空，无法添加到隐私库", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.moveNoteToPrivacy(note, privacyTitle, privacyContent) { moved ->
            Toast.makeText(
                context,
                if (moved) "已移动到隐私库" else "移动到隐私库失败",
                Toast.LENGTH_SHORT,
            ).show()
            if (moved) {
                onMoved()
            }
        }
    }

    fun saveNote(saveHistory: Boolean = false) {
        if (isTemporaryDraft) {
            Log.d(EDITOR_TRACE_TAG, "saveNote skipped temporary draft key=$editorDocumentKey")
            return
        }
        val startMs = SystemClock.elapsedRealtime()
        val snapshot = editorController.getSnapshot()
        Log.d(
            EDITOR_TRACE_TAG,
            "saveNote snapshot key=$editorDocumentKey saveHistory=$saveHistory titleLen=${snapshot.title.length} " +
                "contentLen=${snapshot.content.length} selection=${snapshot.selection} attached=${editorController.editorView != null}",
        )
        if (snapshot.title.isNotEmpty() || snapshot.content.isNotEmpty()) {
            if (isPrivacyEditor) {
                val privacyTitle = snapshot.title.ifBlank { "未命名" }
                val isChanged = privacyEditorDirty ||
                    privacyTitle != privacyInitialTitle.orEmpty().ifBlank { "未命名" } ||
                    snapshot.content != privacyInitialContent.orEmpty()
                if (isChanged) {
                    onSavePrivacyNote?.invoke(effectivePrivacyNoteId, privacyTitle, snapshot.content) { savedId ->
                        effectivePrivacyNoteId = savedId
                    }
                    privacyEditorDirty = false
                    Log.d(
                        EDITOR_TRACE_TAG,
                        "savePrivacyNote dispatched key=$editorDocumentKey id=$effectivePrivacyNoteId elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                    )
                } else {
                    Log.d(EDITOR_TRACE_TAG, "savePrivacyNote skipped unchanged key=$editorDocumentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                }
                return
            }

            val note = buildCurrentNote()
            val isChanged =
                if (currentNote == null) {
                    snapshot.title.isNotEmpty() || snapshot.content.isNotEmpty()
                } else {
                    snapshot.title != currentNote?.title ||
                        snapshot.content != currentNote?.content ||
                        currentNote?.isFavorite != note.isFavorite ||
                        folder != (currentNote?.folder?.takeIf { it != "Unknown" } ?: "")
                }
            if (isChanged) {
                viewModel.saveNote(note, currentNote?.file, saveHistory = saveHistory)
                Log.d(
                    EDITOR_TRACE_TAG,
                    "saveNote dispatched key=$editorDocumentKey changed=true elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            } else {
                Log.d(EDITOR_TRACE_TAG, "saveNote skipped unchanged key=$editorDocumentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            }
        } else {
            Log.w(EDITOR_TRACE_TAG, "saveNote skipped empty snapshot key=$editorDocumentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
        }
    }

    fun markEditorDirty() {
        if (isPrivacyEditor) {
            privacyEditorDirty = true
        } else if (!viewModel.editorDirty.value) {
            viewModel.setEditorDirty(true)
        }
    }

    fun syncUndoRedoState() {
        val nextCanUndo = editorController.canUndo()
        val nextCanRedo = editorController.canRedo()
        if (canUndo != nextCanUndo) canUndo = nextCanUndo
        if (canRedo != nextCanRedo) canRedo = nextCanRedo
    }

    fun insertAtCursor(
        prefix: String,
        suffix: String = "",
    ) {
        editorController.insertAtCursor(prefix, suffix)
        markEditorDirty()
    }

    fun insertImageMarkdown(markdown: String) {
        val snapshot = editorController.getSnapshot()
        val content = snapshot.content
        val start = minOf(snapshot.selection.start, snapshot.selection.end).coerceIn(0, content.length)
        val end = maxOf(snapshot.selection.start, snapshot.selection.end).coerceIn(0, content.length)
        val needsLeadingBreak = start > 0 && content.getOrNull(start - 1) != '\n'
        val insertion = buildString {
            if (needsLeadingBreak) append('\n')
            append(markdown.trim())
            append("\n\n")
        }
        Log.d(
            EDITOR_TRACE_TAG,
            "imageInsert compose before contentLen=${content.length} selection=${snapshot.selection.start}..${snapshot.selection.end} " +
                "replace=$start..$end markdownLen=${markdown.length} insertionLen=${insertion.length} " +
                "needsLeadingBreak=$needsLeadingBreak expectedCursor=${start + insertion.length} " +
                "insertion=${insertion.replace("\n", "\\n")}",
        )
        editorController.replaceSelection(insertion)
        val afterSelection = editorController.getSelection()
        Log.d(
            EDITOR_TRACE_TAG,
            "imageInsert compose after selection=${afterSelection.start}..${afterSelection.end} expectedCursor=${start + insertion.length}",
        )
        markEditorDirty()
    }

    fun launchImagePicker() {
        val picker = onPickImage
        if (picker == null) {
            Toast.makeText(context, "当前页面暂不支持选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        picker { uri ->
            coroutineScope.launch {
                val markdown = viewModel.importImage(uri, folder)
                if (markdown.isNotBlank()) {
                    insertImageMarkdown(markdown)
                }
            }
        }
    }

    fun undoContent() {
        if (!canUndo && !editorController.canUndo()) return
        editorController.undo()
        markEditorDirty()
    }

    fun redoContent() {
        if (!canRedo && !editorController.canRedo()) return
        editorController.redo()
        markEditorDirty()
    }

    fun hideNoteSearchCursor(reason: String) {
        if (!showNoteSearch || !noteSearchFocused) return
        noteSearchFocused = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        Log.d(EDITOR_TRACE_TAG, "noteSearch cursor hidden reason=$reason")
    }

    fun searchInNote(query: String) {
        if (query.isBlank()) {
            editorController.clearSearchHighlights()
            return
        }
        val text = editorController.getText()
        val index = text.indexOf(query, ignoreCase = true)
        val highlightCount = if (isEditing) editorController.highlightSearch(query) else 0
        Log.d(
            EDITOR_TRACE_TAG,
            "noteSearch queryLen=${query.length} textLen=${text.length} index=$index highlights=$highlightCount attached=${editorController.editorView != null} editing=$isEditing",
        )
        if (index >= 0 && isEditing) {
            val end = index + query.length
            editorController.setSelection(index, end)
            coroutineScope.launch {
                withFrameNanos { }
                delay(60)
                editorController.setSelection(index, end)
                editorController.scrollToOffset(index)
                runCatching { searchFocusRequester.requestFocus() }
            }
        }
    }

    fun jumpToHeading(heading: MarkdownHeading) {
        val text = editorController.getText()
        val target = heading.startOffset.coerceIn(0, text.length)
        showNoteInfoDialog = false
        showNoteSearch = false
        noteSearchQuery = ""
        closeNoteSidePanel()
        if (isEditing) {
            editorController.setSelection(target)
            editorController.focus()
            editorFocusRequestToken++
            coroutineScope.launch {
                withFrameNanos { }
                editorController.scrollToOffset(target)
            }
        } else {
            previewHeadingScrollText = heading.text
            previewHeadingScrollLevel = heading.level
            previewHeadingScrollToken++
            Log.d(
                EDITOR_TRACE_TAG,
                "outline jump preview heading=${heading.text.take(40)} level=${heading.level} token=$previewHeadingScrollToken",
            )
        }
    }

    LaunchedEffect(noteSearchQuery) {
        if (showNoteSearch) {
            searchInNote(noteSearchQuery)
        }
    }

    LaunchedEffect(showNoteSearch) {
        if (!showNoteSearch) {
            editorController.clearSearchHighlights()
        }
    }

    LaunchedEffect(showNoteSearch) {
        if (showNoteSearch) {
            withFrameNanos { }
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    // Lifecycle

    if (showCreateLabelDialog) {
        CreateLabelDialog(
            onDismiss = { showCreateLabelDialog = false },
            onConfirm = { name ->
                viewModel.createLabel(name)
                folder = name
                showCreateLabelDialog = false
            },
        )
    }

    LaunchedEffect(showHistoryDialog, currentNote?.file?.path) {
        if (showHistoryDialog && currentNote != null) {
            viewModel.getNoteHistory(currentNote!!.file.path).collect { noteHistory = it }
        } else {
            noteHistory = emptyList()
        }
    }

    LaunchedEffect(currentNote?.file?.path, isPrivacyEditor, noteRemarkRefreshVersion) {
        val noteId = currentNote?.file?.path
        if (!isPrivacyEditor && noteId != null) {
            noteRemarkDraft = ""
            viewModel.getNoteRemarks(noteId).collect { noteRemarks = it }
        } else {
            noteRemarks = emptyList()
            noteRemarkDraft = ""
        }
    }

    LaunchedEffect(currentNote?.file?.path, isPrivacyEditor, rawInitialContent, noteRemarkRefreshVersion) {
        val noteId = currentNote?.file?.path
        noteFrontMatterProperties =
            if (!isPrivacyEditor && noteId != null) {
                viewModel.getNoteFrontMatterProperties(noteId)
            } else {
                initialFrontMatter.properties
            }
    }

    if (showHistoryDialog && currentNote != null) {
        val currentBuiltForHistory = buildCurrentNote()
        NoteHistoryDialog(
            histories = noteHistory,
            currentContent = currentBuiltForHistory.content,
            onDismiss = { showHistoryDialog = false },
            onRestore = { history ->
                viewModel.restoreNoteHistory(currentNote!!.file.path, history.id, buildCurrentNote())
                showHistoryDialog = false
            },
            onDelete = { history ->
                viewModel.deleteNoteHistory(history.id)
            },
        )
    }

    if (showNoteInfoDialog) {
        val snapshot = editorController.getSnapshot()
        NoteInfoDialog(
            title = snapshot.title,
            content = snapshot.content,
            allNotes = allNotes,
            onDismiss = { showNoteInfoDialog = false },
            onHeadingClick = { heading -> jumpToHeading(heading) },
        )
    }

    if (!isPrivacyEditor) externalConflict?.let { conflictNote ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissExternalConflict() },
            title = { Text("文件冲突") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "当前笔记有未保存修改，外部文件也发生了变化。请选择保留当前编辑内容，或使用外部版本。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = "外部版本预览：${conflictNote.content.take(200)}${if (conflictNote.content.length > 200) "..." else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExternalConflict() }) {
                    Text("保留我的修改")
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { viewModel.applyExternalConflict() }) {
                        Text("使用外部版本")
                    }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText("外部版本内容", conflictNote.content),
                        )
                        android.widget.Toast
                            .makeText(context, "已复制外部版本内容", android.widget.Toast.LENGTH_SHORT)
                            .show()
                        viewModel.dismissExternalConflict()
                    }) {
                        Text("复制外部版本内容")
                    }
                }
            },
        )
    }

    LaunchedEffect(currentNote, externalDraft, isPrivacyEditor) {
        if (isPrivacyEditor) {
            folder = ""
        } else if (currentNote != null) {
            folder = currentNote!!.folder.takeIf { it != "Unknown" } ?: ""
        } else {
            folder = externalDraft?.folder?.takeIf { it.isNotBlank() } ?: initialLabel
        }
    }

    fun leaveEditor() {
        isLeavingEditor = true
        isClosingEditor = true
        closeNoteSidePanel()
        requestKeyboardOnEdit = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        onBack()
    }

    fun renderPreviewSnapshot(snapshot: KardLeafEditorSnapshot) {
        val startMs = SystemClock.elapsedRealtime()
        val markdown = "# ${snapshot.title}\n\n${snapshot.content}"
        val token = previewRenderToken + 1
        previewRenderToken = token
        renderedPreview = markdown
        Log.d(
            EDITOR_TRACE_TAG,
            "preview render start token=$token key=$editorDocumentKey titleLen=${snapshot.title.length} contentLen=${snapshot.content.length}",
        )
        coroutineScope.launch {
            val preparedMarkdown = viewModel.preparePreviewMarkdown(markdown, folder)
            if (previewRenderToken == token) {
                renderedPreview = preparedMarkdown
                Log.d(
                    EDITOR_TRACE_TAG,
                    "preview render done token=$token key=$editorDocumentKey len=${preparedMarkdown.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            } else {
                Log.d(EDITOR_TRACE_TAG, "preview render ignored stale token=$token latest=$previewRenderToken key=$editorDocumentKey")
            }
        }
    }

    fun enterPreviewMode() {
        val snapshot = editorController.getSnapshot()
        renderPreviewSnapshot(snapshot)
        isLeavingEditor = true
        requestKeyboardOnEdit = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        isEditing = false
    }

    fun showLargeNoteEditBlockedToast() {
        Toast.makeText(
            context,
            "当前笔记过大，暂时只能快速预览，避免编辑器卡死",
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun enterEditMode(
        preservePreviewPosition: Boolean = false,
        previewMarkdownOffset: Int? = null,
    ) {
        val snapshot = editorController.getSnapshot()
        val currentTextLength = maxOf(initialContent.length, snapshot.content.length)
        if (!isNewPrivacyNote && currentTextLength > DIRECT_EDIT_MAX_CHARS) {
            showLargeNoteEditBlockedToast()
            requestKeyboardOnEdit = false
            pendingEditScrollRatio = null
            pendingEditScrollOffset = null
            isEditing = false
            return
        }
        if (preservePreviewPosition) {
            val textLength = snapshot.content.length
            val titlePrefixLength = "# ${snapshot.title}\n\n".length
            val targetOffset = previewMarkdownOffset
                ?.minus(titlePrefixLength)
                ?.coerceIn(0, textLength)
                ?: (textLength * previewScrollRatio).toInt().coerceIn(0, textLength)
            editorController.setSelection(targetOffset)
            pendingEditScrollOffset = targetOffset
            pendingEditScrollRatio = if (previewMarkdownOffset == null) previewScrollRatio else null
        }
        isLeavingEditor = false
        requestKeyboardOnEdit = true
        isEditing = true
    }

    LaunchedEffect(blocksDirectEditForLargeNote, isOpeningNoteContent, isEditing) {
        if ((blocksDirectEditForLargeNote || isOpeningNoteContent) && isEditing) {
            requestKeyboardOnEdit = false
            pendingEditScrollRatio = null
            pendingEditScrollOffset = null
            isEditing = false
        }
    }

    LaunchedEffect(isOpeningNoteContent, currentNote?.file?.path, blocksDirectEditForLargeNote, defaultOpenNoteMode) {
        if (!isOpeningNoteContent &&
            !blocksDirectEditForLargeNote &&
            !isPrivacyEditor &&
            currentNote != null &&
            defaultOpenNoteMode == KardLeafCustomFeatures.OpenNoteMode.EDIT
        ) {
            isEditing = true
        }
    }

    LaunchedEffect(isEditing, pendingEditScrollRatio, pendingEditScrollOffset) {
        val targetOffset = pendingEditScrollOffset
        val targetRatio = pendingEditScrollRatio
        if (isEditing && targetOffset != null) {
            withFrameNanos { }
            editorController.setSelection(targetOffset)
            editorController.scrollToOffset(targetOffset)
            pendingEditScrollOffset = null
            return@LaunchedEffect
        }
        if (isEditing && targetRatio != null) {
            withFrameNanos { }
            val textLength = editorController.getText().length
            val ratioOffset = (textLength * targetRatio).toInt().coerceIn(0, textLength)
            editorController.setSelection(ratioOffset)
            editorController.scrollToProgress(targetRatio)
            pendingEditScrollRatio = null
        }
    }

    LaunchedEffect(effectiveEditorOpen, isEditing, currentNote, externalDraft, isLeavingEditor) {
        if (!isLeavingEditor &&
            !showNoteSearch &&
            effectiveEditorOpen &&
            isEditing &&
            (currentNote == null || requestKeyboardOnEdit || defaultOpenNoteMode == KardLeafCustomFeatures.OpenNoteMode.EDIT)
        ) {
            withFrameNanos { }
            editorFocusRequestToken++
            delay(120)
            keyboardController?.show()
            requestKeyboardOnEdit = false
        }
    }

    LaunchedEffect(isEditing, isOpeningNoteContent, folder, editorDocumentKey, initialTitle, initialContent) {
        if (!isEditing && !isOpeningNoteContent) {
            withFrameNanos { }
            renderPreviewSnapshot(editorController.getSnapshot())
        }
    }

    val shouldRefreshOutline = noteSidePanelsActive && noteSidePanelVisibleFraction > 0.01f
    LaunchedEffect(shouldRefreshOutline) {
        if (shouldRefreshOutline) {
            outlineHeadings = extractMarkdownHeadings(editorController.getText())
        }
    }

    val shouldRefreshNoteTextStats =
        noteSidePanelsActive &&
            !isNoteSidePanelDragging &&
            noteSidePanelTargetPx <= -noteSidePanelWidthPx + 1f &&
            noteSidePanelOffsetPx <= -noteSidePanelWidthPx * 0.96f
    LaunchedEffect(editorDocumentKey, shouldRefreshNoteTextStats) {
        if (shouldRefreshNoteTextStats) {
            noteTextStats = null
            val textSnapshot = editorController.getText()
            noteTextStats = withContext(Dispatchers.Default) {
                NoteTextStats.fromText(textSnapshot)
            }
        }
    }

    BackHandler {
        Log.d(
            BACK_TRACE_TAG,
            "Editor root BackHandler hit isEditing=$isEditing showNoteSearch=$showNoteSearch " +
                "sidePanelActive=$noteSidePanelsActive sidePanelOffset=$noteSidePanelOffsetPx " +
                "showLabelMenu=$showLabelMenu showMoreMenu=$showMoreMenu showHeadingMenu=$showHeadingMenu showMathMenu=$showMathMenu",
        )
        if (isEditing) {
            saveNote(saveHistory = true)
        }
        leaveEditor()
    }

    BackHandler(enabled = showNoteSearch) {
        Log.d(BACK_TRACE_TAG, "Editor note search BackHandler hit")
        showNoteSearch = false
        noteSearchQuery = ""
    }

    BackHandler(enabled = noteSidePanelsActive && abs(noteSidePanelOffsetPx) > 1f) {
        Log.d(BACK_TRACE_TAG, "Editor side panel BackHandler hit offset=$noteSidePanelOffsetPx")
        closeNoteSidePanel()
    }

    BackHandler(enabled = showLabelMenu || showMoreMenu || showHeadingMenu || showMathMenu) {
        Log.d(
            BACK_TRACE_TAG,
            "Editor menu BackHandler hit showLabelMenu=$showLabelMenu showMoreMenu=$showMoreMenu " +
                "showHeadingMenu=$showHeadingMenu showMathMenu=$showMathMenu",
        )
        showLabelMenu = false
        showMoreMenu = false
        showHeadingMenu = false
        showMathMenu = false
    }

    LaunchedEffect(showLabelMenu, showMoreMenu, showHeadingMenu, showMathMenu) {
        Log.d(
            BACK_TRACE_TAG,
            "Editor menu state changed showLabelMenu=$showLabelMenu showMoreMenu=$showMoreMenu " +
                "showHeadingMenu=$showHeadingMenu showMathMenu=$showMathMenu",
        )
    }

    val latestAutoSave by rememberUpdatedState(newValue = {
        if (isEditing) {
            saveNote(saveHistory = true)
        }
    })

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                    latestAutoSave()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
        }
    }

    // Theming

    val backgroundColor = MaterialTheme.colorScheme.background
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()

    // Scaffold

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor),
    ) {
        Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = showBars || isEditing,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it }),
            ) {
                Column {
                TopAppBar(
                    title = {
                        if (showNoteSearch) {
                            BasicTextField(
                                value = noteSearchQuery,
                                onValueChange = { noteSearchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(if (noteSearchFocused) MaterialTheme.colorScheme.primary else Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocusRequester)
                                    .onFocusChanged { noteSearchFocused = it.isFocused },
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (noteSearchQuery.isEmpty()) {
                                            Text(
                                                "搜索当前笔记",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                    },
                    navigationIcon = {
                    if (showNoteSearch) {
                        IconButton(onClick = {
                            showNoteSearch = false
                            noteSearchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭搜索")
                        }
                    } else if (isEditing) {
                        IconButton(onClick = {
                            saveNote(saveHistory = true)
                            enterPreviewMode()
                        }) {
                            Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.done))
                        }
                    } else {
                        IconButton(onClick = { leaveEditor() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (showNoteSearch) {
                        // Search mode: show only the close button
                        IconButton(onClick = {
                            showNoteSearch = false
                            noteSearchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭搜索")
                        }
                    } else {
                    // Label picker
                    if (!isPrivacyEditor) {
                        Box {
                            IconButton(onClick = {
                                val now = SystemClock.uptimeMillis()
                                val ignoreReopen = !showLabelMenu && now - lastLabelMenuDismissAt < MENU_REOPEN_GUARD_MS
                                Log.d(BACK_TRACE_TAG, "Editor label click toggle menu showLabelMenu=$showLabelMenu ignoreReopen=$ignoreReopen")
                                if (!ignoreReopen) {
                                    showMoreMenu = false
                                    showHeadingMenu = false
                                    showMathMenu = false
                                    showLabelMenu = !showLabelMenu
                                }
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.DriveFileMove,
                                    contentDescription = stringResource(R.string.label),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            DropdownMenu(
                                modifier =
                                    Modifier.onPreviewKeyEvent { event ->
                                        if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                            Log.d(
                                                BACK_TRACE_TAG,
                                                "Editor label popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showLabelMenu=$showLabelMenu",
                                            )
                                        }
                                        false
                                    },
                                expanded = showLabelMenu,
                                onDismissRequest = {
                                    Log.d(BACK_TRACE_TAG, "Editor label menu onDismissRequest showLabelMenu=$showLabelMenu")
                                    lastLabelMenuDismissAt = SystemClock.uptimeMillis()
                                    showLabelMenu = false
                                },
                                properties = PopupProperties(
                                    focusable = false,
                                    dismissOnBackPress = false,
                                    dismissOnClickOutside = true,
                                ),
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.root_folder_no_label)) },
                                    onClick = {
                                        folder = ""
                                        showLabelMenu = false
                                        if (currentNote != null) {
                                            saveNote(saveHistory = false)
                                        }
                                    },
                                )
                                labels.forEach { label ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            folder = label
                                            showLabelMenu = false
                                            if (currentNote != null) {
                                                saveNote(saveHistory = false)
                                            }
                                        },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.create_new_label)) },
                                    leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                    onClick = {
                                        showLabelMenu = false
                                        showCreateLabelDialog = true
                                    },
                                )
                            }
                        }
                    }

                    // Search
                    IconButton(onClick = {
                        showNoteSearch = true
                    }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "搜索当前笔记",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Edit toggle (view-mode only)
                    if (!isEditing) {
                        IconButton(onClick = { enterEditMode() }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit))
                        }
                    }

                    // More menu (existing notes only)
                    val currentNoteObj = currentNote
                    if (isPrivacyEditor && onDeletePrivacyNote != null) {
                        Box {
                            IconButton(onClick = {
                                val now = SystemClock.uptimeMillis()
                                val ignoreReopen = !showMoreMenu && now - lastMoreMenuDismissAt < MENU_REOPEN_GUARD_MS
                                Log.d(BACK_TRACE_TAG, "Editor privacy more click toggle menu showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                                if (!ignoreReopen) {
                                    showLabelMenu = false
                                    showHeadingMenu = false
                                    showMathMenu = false
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
                                                "Editor privacy more popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoreMenu=$showMoreMenu",
                                            )
                                        }
                                        false
                                    },
                                expanded = showMoreMenu,
                                onDismissRequest = {
                                    Log.d(BACK_TRACE_TAG, "Editor note more onDismissRequest showMoreMenu=$showMoreMenu")
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
                                    text = { Text(stringResource(R.string.delete)) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = {
                                        showMoreMenu = false
                                        onDeletePrivacyNote()
                                    },
                                )
                            }
                        }
                    } else if (currentNoteObj != null) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .combinedClickable(
                                        onClick = {
                                            val now = SystemClock.uptimeMillis()
                                            val ignoreReopen = !showMoreMenu && now - lastMoreMenuDismissAt < MENU_REOPEN_GUARD_MS
                                            Log.d(BACK_TRACE_TAG, "Editor note more click toggle menu noteId=${currentNoteObj.id} showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                                            if (!ignoreReopen) {
                                                showLabelMenu = false
                                                showHeadingMenu = false
                                                showMathMenu = false
                                                showMoreMenu = !showMoreMenu
                                            }
                                        },
                                        onLongClick = { addCurrentNoteToPrivacy(currentNoteObj) { leaveEditor() } },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                modifier =
                                    Modifier.onPreviewKeyEvent { event ->
                                        if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                            Log.d(
                                                BACK_TRACE_TAG,
                                                "Editor note more popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoreMenu=$showMoreMenu",
                                            )
                                        }
                                        false
                                    },
                                expanded = showMoreMenu,
                                onDismissRequest = {
                                    Log.d(BACK_TRACE_TAG, "Editor privacy more onDismissRequest showMoreMenu=$showMoreMenu")
                                    lastMoreMenuDismissAt = SystemClock.uptimeMillis()
                                    showMoreMenu = false
                                },
                                properties = PopupProperties(
                                    focusable = false,
                                    dismissOnBackPress = false,
                                    dismissOnClickOutside = true,
                                ),
                            ) {
                                if (!currentNoteObj.isTrashed) {
                                    DropdownMenuItem(
                                        text = { Text("历史版本") },
                                        leadingIcon = { Icon(Icons.Outlined.History, null) },
                                        onClick = {
                                            showHistoryDialog = true
                                            showMoreMenu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("保护") },
                                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                                        onClick = {
                                            addCurrentNoteToPrivacy(currentNoteObj) { leaveEditor() }
                                            showMoreMenu = false
                                        },
                                    )
                                    if (currentNoteObj.isArchived) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.unarchive)) },
                                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                            onClick = {
                                                viewModel.restoreNote(currentNoteObj)
                                                showMoreMenu = false
                                                onBack()
                                            },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.archive)) },
                                            leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                            onClick = {
                                                viewModel.archiveNote(currentNoteObj)
                                                showMoreMenu = false
                                                onBack()
                                            },
                                        )
                                    }
                                }

                                if (currentNoteObj.isTrashed) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.restore)) },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                        onClick = {
                                            viewModel.restoreNote(currentNoteObj)
                                            showMoreMenu = false
                                            onBack()
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            viewModel.deleteNote(currentNoteObj)
                                            showMoreMenu = false
                                            leaveEditor()
                                        },
                                    )
                                }
                            }
                        }
                    }
                    } // end if (showNoteSearch) else
                },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
                )
                HorizontalDivider(
                    thickness = 0.6.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                )
                }
            }
        },
        bottomBar = {
            if (isEditing && shouldShowBottomToolbar) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .navigationBarsPadding()
                            .imePadding()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        isBottomToolbarExpanded = toolbarDragFraction > 0.5f
                                        toolbarDragFraction = if (isBottomToolbarExpanded) 1f else 0f
                                    },
                                    onDragCancel = {
                                        isBottomToolbarExpanded = toolbarDragFraction > 0.5f
                                        toolbarDragFraction = if (isBottomToolbarExpanded) 1f else 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        val totalHeight = size.height.toFloat().coerceAtLeast(200f)
                                        toolbarDragFraction = (toolbarDragFraction - dragAmount / totalHeight)
                                            .coerceIn(0f, 1f)
                                        isBottomToolbarExpanded = toolbarDragFraction > 0.5f
                                    },
                                )
                            },
                ) {
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    AnimatedContent(
                        targetState = isBottomToolbarExpanded,
                        label = "toolbarExpand",
                    ) { expanded ->
                    if (expanded) {
                        FlowRow(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            toolbarOrder.forEach { toolbarItem ->
                                when (toolbarItem) {
                                    KardLeafCustomFeatures.ToolbarItem.PREVIEW -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.Visibility,
                                        contentDescription = "预览",
                                        onClick = {
                                            saveNote(saveHistory = true)
                                            enterPreviewMode()
                                        },
                                    )
                                    KardLeafCustomFeatures.ToolbarItem.UNDO -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.Undo,
                                        contentDescription = "撤销",
                                        onClick = { undoContent() },
                                    )
                                    KardLeafCustomFeatures.ToolbarItem.REDO -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.Redo,
                                        contentDescription = "恢复",
                                        onClick = { redoContent() },
                                    )
                                    KardLeafCustomFeatures.ToolbarItem.IMAGE -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.Image,
                                        contentDescription = "图片",
                                        onClick = { launchImagePicker() },
                                    )
                                    KardLeafCustomFeatures.ToolbarItem.HEADING -> ToolbarIconButton(text = "H1", bold = true, onClick = { insertAtCursor("# ") })
                                    KardLeafCustomFeatures.ToolbarItem.RULE -> ToolbarIconButton(text = "---", onClick = { insertAtCursor("---\n") })
                                    KardLeafCustomFeatures.ToolbarItem.BOLD -> ToolbarIconButton(text = "B", bold = true, onClick = { insertAtCursor("**", "**") })
                                    KardLeafCustomFeatures.ToolbarItem.ITALIC -> ToolbarIconButton(text = "I", italic = true, onClick = { insertAtCursor("_", "_") })
                                    KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> ToolbarIconButton(text = "U", underline = true, onClick = { insertAtCursor("<u>", "</u>") })
                                    KardLeafCustomFeatures.ToolbarItem.STRIKE -> ToolbarIconButton(text = "S", strikethrough = true, onClick = { insertAtCursor("~~", "~~") })
                                    KardLeafCustomFeatures.ToolbarItem.LINK -> ToolbarIconButton(text = "Link", onClick = { insertAtCursor("[", "](url)") })
                                    KardLeafCustomFeatures.ToolbarItem.CODE -> ToolbarIconButton(text = "<>", onClick = { insertAtCursor("`", "`") })
                                    KardLeafCustomFeatures.ToolbarItem.QUOTE -> ToolbarIconButton(text = "\"", onClick = { insertAtCursor("> ") })
                                    KardLeafCustomFeatures.ToolbarItem.MATH -> ToolbarIconButton(text = "$", onClick = { insertAtCursor("$", "$") })
                                    KardLeafCustomFeatures.ToolbarItem.BULLET -> ToolbarIconButton(text = "-", onClick = { insertAtCursor("- ") })
                                    KardLeafCustomFeatures.ToolbarItem.NUMBERED -> ToolbarIconButton(text = "1.", onClick = { insertAtCursor("1. ") })
                                    KardLeafCustomFeatures.ToolbarItem.CHECKBOX -> ToolbarIconButton(text = "[ ]", onClick = { insertAtCursor("- [ ] ") })
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            toolbarOrder.forEach { toolbarItem ->
                                item(toolbarItem.name) {
                                    when (toolbarItem) {
                                        KardLeafCustomFeatures.ToolbarItem.PREVIEW -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.Visibility,
                                            contentDescription = "预览",
                                            onClick = {
                                                saveNote(saveHistory = true)
                                                enterPreviewMode()
                                            },
                                        )
                                        KardLeafCustomFeatures.ToolbarItem.UNDO -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.Undo,
                                            contentDescription = "撤销",
                                            onClick = { undoContent() },
                                        )
                                        KardLeafCustomFeatures.ToolbarItem.REDO -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.Redo,
                                            contentDescription = "恢复",
                                            onClick = { redoContent() },
                                        )
                                        KardLeafCustomFeatures.ToolbarItem.IMAGE -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.Image,
                                            contentDescription = "图片",
                                            onClick = { launchImagePicker() },
                                        )
                                        KardLeafCustomFeatures.ToolbarItem.HEADING -> {
                                            Box {
                                                ToolbarIconButton(
                                                    text = "H1",
                                                    bold = true,
                                                    onClick = { insertAtCursor("# ") },
                                                    onLongClick = {
                                                        val now = SystemClock.uptimeMillis()
                                                        val ignoreReopen = !showHeadingMenu && now - lastHeadingMenuDismissAt < MENU_REOPEN_GUARD_MS
                                                        Log.d(BACK_TRACE_TAG, "Editor heading menu longClick toggle showHeadingMenu=$showHeadingMenu ignoreReopen=$ignoreReopen")
                                                        if (!ignoreReopen) {
                                                            showLabelMenu = false
                                                            showMoreMenu = false
                                                            showMathMenu = false
                                                            showHeadingMenu = !showHeadingMenu
                                                        }
                                                    },
                                                )
                                                DropdownMenu(
                                                    modifier =
                                                        Modifier.onPreviewKeyEvent { event ->
                                                            if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                                                Log.d(
                                                                    BACK_TRACE_TAG,
                                                                    "Editor heading popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showHeadingMenu=$showHeadingMenu",
                                                                )
                                                            }
                                                            false
                                                        },
                                                    expanded = showHeadingMenu,
                                                    onDismissRequest = {
                                                        Log.d(BACK_TRACE_TAG, "Editor heading menu onDismissRequest showHeadingMenu=$showHeadingMenu")
                                                        lastHeadingMenuDismissAt = SystemClock.uptimeMillis()
                                                        showHeadingMenu = false
                                                    },
                                                    properties = PopupProperties(
                                                        focusable = false,
                                                        dismissOnBackPress = false,
                                                        dismissOnClickOutside = true,
                                                    ),
                                                ) {
                                                    listOf("H1" to "# ", "H2" to "## ", "H3" to "### ", "H4" to "#### ")
                                                        .forEach { (label, md) ->
                                                            DropdownMenuItem(
                                                                text = { Text(label) },
                                                                onClick = {
                                                                    insertAtCursor(md)
                                                                    showHeadingMenu = false
                                                                },
                                                            )
                                                        }
                                                }
                                            }
                                        }
                                        KardLeafCustomFeatures.ToolbarItem.RULE -> ToolbarIconButton(text = "---", onClick = { insertAtCursor("---\n") })
                                        KardLeafCustomFeatures.ToolbarItem.BOLD -> ToolbarIconButton(text = "B", bold = true, onClick = { insertAtCursor("**", "**") })
                                        KardLeafCustomFeatures.ToolbarItem.ITALIC -> ToolbarIconButton(text = "I", italic = true, onClick = { insertAtCursor("_", "_") })
                                        KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> ToolbarIconButton(text = "U", underline = true, onClick = { insertAtCursor("<u>", "</u>") })
                                        KardLeafCustomFeatures.ToolbarItem.STRIKE -> ToolbarIconButton(text = "S", strikethrough = true, onClick = { insertAtCursor("~~", "~~") })
                                        KardLeafCustomFeatures.ToolbarItem.LINK -> ToolbarIconButton(text = "Link", onClick = { insertAtCursor("[", "](url)") })
                                        KardLeafCustomFeatures.ToolbarItem.CODE -> ToolbarIconButton(text = "<>", onClick = { insertAtCursor("`", "`") })
                                        KardLeafCustomFeatures.ToolbarItem.QUOTE -> ToolbarIconButton(text = "\"", onClick = { insertAtCursor("> ") })
                                        KardLeafCustomFeatures.ToolbarItem.MATH -> {
                                            Box {
                                                ToolbarIconButton(
                                                    text = "$",
                                                    onClick = { insertAtCursor("$", "$") },
                                                    onLongClick = {
                                                        val now = SystemClock.uptimeMillis()
                                                        val ignoreReopen = !showMathMenu && now - lastMathMenuDismissAt < MENU_REOPEN_GUARD_MS
                                                        Log.d(BACK_TRACE_TAG, "Editor math menu longClick toggle showMathMenu=$showMathMenu ignoreReopen=$ignoreReopen")
                                                        if (!ignoreReopen) {
                                                            showLabelMenu = false
                                                            showMoreMenu = false
                                                            showHeadingMenu = false
                                                            showMathMenu = !showMathMenu
                                                        }
                                                    },
                                                )
                                                DropdownMenu(
                                                    modifier =
                                                        Modifier.onPreviewKeyEvent { event ->
                                                            if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                                                Log.d(
                                                                    BACK_TRACE_TAG,
                                                                    "Editor math popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMathMenu=$showMathMenu",
                                                                )
                                                            }
                                                            false
                                                        },
                                                    expanded = showMathMenu,
                                                    onDismissRequest = {
                                                        Log.d(BACK_TRACE_TAG, "Editor math menu onDismissRequest showMathMenu=$showMathMenu")
                                                        lastMathMenuDismissAt = SystemClock.uptimeMillis()
                                                        showMathMenu = false
                                                    },
                                                    properties = PopupProperties(
                                                        focusable = false,
                                                        dismissOnBackPress = false,
                                                        dismissOnClickOutside = true,
                                                    ),
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.inline_math)) },
                                                        onClick = {
                                                            insertAtCursor("$", "$")
                                                            showMathMenu = false
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.block_math)) },
                                                        onClick = {
                                                            insertAtCursor("$$\n", "\n$$")
                                                            showMathMenu = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                        KardLeafCustomFeatures.ToolbarItem.BULLET -> ToolbarIconButton(text = "-", onClick = { insertAtCursor("- ") })
                                        KardLeafCustomFeatures.ToolbarItem.NUMBERED -> ToolbarIconButton(text = "1.", onClick = { insertAtCursor("1. ") })
                                        KardLeafCustomFeatures.ToolbarItem.CHECKBOX -> ToolbarIconButton(text = "[ ]", onClick = { insertAtCursor("- [ ] ") })
                                    }
                                }
                            }
                        }
                    }
                    } // AnimatedContent closing
                }
            }
        },
        containerColor = backgroundColor,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        val position = coordinates.positionInWindow()
                        noteSidePanelGestureRootX = position.x
                        noteSidePanelGestureRootY = position.y
                    }
                    .nestedScroll(nestedScrollConnection)
                    .padding(paddingValues)
                    .then(noteSidePanelContentDragModifier)
                    .then(noteSidePanelActiveDragModifier),
        ) {
            if (isEditing && !blocksDirectEditForLargeNote) {
                KardLeafNativeEditor(
                    initialTitle = initialTitle,
                    initialContent = initialContent,
                    documentKey = editorDocumentKey,
                    controller = editorController,
                    onTitleChanged = { markEditorDirty() },
                    onContentChanged = {
                        markEditorDirty()
                        syncUndoRedoState()
                    },
                    onUndoRedoChanged = { syncUndoRedoState() },
                    onUserInteraction = { hideNoteSearchCursor("editor content touch") },
                    onFastScrollSourceScrolled = { fastScrollSignal.notifyScrollChanged() },
                    titleHint = stringResource(R.string.title_hint),
                    contentHint = stringResource(R.string.start_typing_hint),
                    textColor = MaterialTheme.colorScheme.onBackground,
                    hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleTextSize = MaterialTheme.typography.titleLarge.fontSize,
                    contentTextSize = MaterialTheme.typography.bodyLarge.fontSize,
                    requestFocusToken = editorFocusRequestToken,
                    showTitle = showBars,
                    currentFolder = folder,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PreviewWebView(
                    content = if (isOpeningNoteContent) "" else renderedPreview,
                    isDark = isDark,
                    controller = previewController,
                    modifier = Modifier.fillMaxSize(),
                    searchQuery = if (showNoteSearch) noteSearchQuery else "",
                    headingScrollText = previewHeadingScrollText,
                    headingScrollLevel = previewHeadingScrollLevel,
                    headingScrollToken = previewHeadingScrollToken,
                    onDoubleTap = { offset -> enterEditMode(preservePreviewPosition = true, previewMarkdownOffset = offset) },
                    onUserInteraction = { hideNoteSearchCursor("preview touch") },
                    onScrollRatioChanged = { previewScrollRatio = it },
                    onFastScrollSourceScrolled = { fastScrollSignal.notifyScrollChanged() },
                    doubleTapIntervalMs = previewDoubleTapIntervalMs,
                    onCheckboxToggled = { index, checked ->
                        if (!isOpeningNoteContent) {
                            val snapshot = editorController.getSnapshot()
                            val newText = toggleTask(snapshot.content, index, checked)
                            val updatedSnapshot = snapshot.copy(content = newText)
                            editorController.replaceAll(newText)
                            renderPreviewSnapshot(updatedSnapshot)
                            if (isPrivacyEditor) {
                                val privacyTitle = snapshot.title.ifBlank { "未命名" }
                                onSavePrivacyNote?.invoke(effectivePrivacyNoteId, privacyTitle, newText) { savedId ->
                                    effectivePrivacyNoteId = savedId
                                }
                            } else {
                                viewModel.saveNote(
                                    buildCurrentNote().copy(content = newText),
                                    currentNote?.file,
                                )
                            }
                        }
                    },
                )
                if (isOpeningNoteContent) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Text(
                                text = "正在加载正文",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (noteSidePanelsActive && (!noteSidePanelHasOffset || isNoteSidePanelDragging)) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(noteSidePanelEdgeWidth)
                            .zIndex(if (isNoteSidePanelDragging) 3f else 0.5f)
                            .then(noteSidePanelEdgeDragModifier),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(noteSidePanelEdgeWidth)
                            .zIndex(if (isNoteSidePanelDragging) 3f else 0.5f)
                            .then(noteSidePanelEdgeDragModifier),
                )
            }
            if (!noteSidePanelHasOffset || isNoteSidePanelDragging) {
                AndroidView(
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(noteSidePanelEdgeWidth)
                            .zIndex(4f),
                    factory = { fastScrollContext -> EditorFastScrollEdgeView(fastScrollContext) },
                    update = { fastScrollView ->
                        fastScrollSignal.setListener { fastScrollView.showForScroll() }
                        fastScrollView.configure(
                            metricsProvider = {
                                if (isEditing) {
                                    editorController.getFastScrollMetrics()
                                } else {
                                    previewController.getFastScrollMetrics()
                                }
                            },
                            onScrollToRatio = { ratio ->
                                if (isEditing) {
                                    editorController.fastScrollToRatio(ratio)
                                } else {
                                    previewController.fastScrollToRatio(ratio)
                                }
                            },
                            onFastScrollInteraction = {
                                hideNoteSearchCursor(if (isEditing) "editor fast scroll" else "preview fast scroll")
                            },
                            sidePanelDragEnabled = {
                                noteSidePanelsActive && (!noteSidePanelHasOffset || isNoteSidePanelDragging)
                            },
                            onSidePanelDragStart = { startNoteSidePanelDrag() },
                            onSidePanelDragBy = { dragAmount -> dragNoteSidePanelBy(dragAmount) },
                            onSidePanelDragEnd = { settleNoteSidePanelDrag() },
                            onSidePanelDragCancel = { cancelNoteSidePanelDrag() },
                        )
                    },
                )
            }
        }
        }
        if (noteSidePanelsActive && noteSidePanelHasOffset) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .background(Color.Black.copy(alpha = 0.22f * noteSidePanelVisibleFraction))
                        .then(noteSidePanelActiveDragModifier)
                        .clickable(
                            interactionSource = noteSidePanelScrimInteractionSource,
                            indication = null,
                        ) {
                            closeNoteSidePanel()
                        },
            )
        }
        if (noteSidePanelsActive) {
            NoteOutlineSidePanel(
                headings = outlineHeadings,
                onHeadingClick = { heading -> jumpToHeading(heading) },
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(noteSidePanelWidth)
                        .offset {
                            IntOffset(
                                x = (-noteSidePanelWidthPx + noteSidePanelOffsetPx.coerceAtLeast(0f)).roundToInt(),
                                y = 0,
                            )
                        }
                        .zIndex(2f)
                        .then(noteSidePanelActiveDragModifier),
            )
            NoteRemarkSidePanel(
                frontMatterProperties = noteSidePanelProperties,
                textStats = noteTextStats,
                remarks = noteRemarks,
                draft = noteRemarkDraft,
                onDraftChange = { noteRemarkDraft = it },
                onAdd = {
                    val draft = noteRemarkDraft.trim()
                    currentNote?.file?.path?.let { noteId ->
                        if (draft.isNotBlank()) {
                            viewModel.addNoteRemark(noteId, draft) {
                                noteRemarkRefreshVersion++
                            }
                            noteRemarkDraft = ""
                            Toast.makeText(context, "备注已添加", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDelete = { remark ->
                    viewModel.deleteNoteRemark(remark.id)
                    Toast.makeText(context, "备注已删除", Toast.LENGTH_SHORT).show()
                },
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(noteSidePanelWidth)
                        .offset {
                            IntOffset(
                                x = (noteSidePanelWidthPx + noteSidePanelOffsetPx.coerceAtMost(0f)).roundToInt(),
                                y = 0,
                            )
                        }
                        .zIndex(2f)
                        .then(noteSidePanelActiveDragModifier),
            )
        }
    }
}

private fun buildNoteSidePanelProperties(
    frontMatterProperties: List<NoteFormatUtils.FrontMatterProperty>,
    note: Note?,
    title: String,
): List<NoteFormatUtils.FrontMatterProperty> {
    if (frontMatterProperties.isNotEmpty()) return frontMatterProperties
    val currentNote = note ?: return emptyList()
    return buildList {
        title.trim().takeIf { it.isNotBlank() }?.let { value ->
            add(NoteFormatUtils.FrontMatterProperty(key = "title", values = listOf(value)))
        }
        currentNote.file.path.trim().takeIf { it.isNotBlank() }?.let { value ->
            add(NoteFormatUtils.FrontMatterProperty(key = "path", values = listOf(value)))
        }
        add(
            NoteFormatUtils.FrontMatterProperty(
                key = "created",
                values = listOf(formatNoteSidePanelTime(currentNote.createdAt)),
            ),
        )
        add(
            NoteFormatUtils.FrontMatterProperty(
                key = "updated",
                values = listOf(formatNoteSidePanelTime(currentNote.lastModified)),
            ),
        )
    }
}

private fun formatNoteSidePanelTime(date: Date): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)

