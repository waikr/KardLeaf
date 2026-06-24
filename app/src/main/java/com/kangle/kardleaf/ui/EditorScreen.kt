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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
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
import androidx.compose.ui.text.TextRange
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
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EDITOR_TRACE_TAG = "KardLeafEditorTrace"
private const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
private const val EDITOR_GESTURE_TAG = "KardLeafGestureTrace"
private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val EDITOR_TOP_BAR_TRACE_TAG = "KardLeafEditorTopBar"
private const val MENU_REOPEN_GUARD_MS = 250L
private const val DIRECT_EDIT_MAX_CHARS = 600_000


private fun Iterable<PrefsManager.EditorTopToolbarItemId>.toEditorTopBarLogText(): String =
    joinToString(prefix = "[", postfix = "]") { it.name }

@Composable
private fun NoteSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focused: Boolean,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent),
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .padding(horizontal = 12.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun NoteSearchChip(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
        )
    }
}

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
    openDrawingPadOnStart: Boolean = false,
    onDrawingPadStartConsumed: () -> Unit = {},
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
    var editorTopToolbarOrder by remember { mutableStateOf(notePrefsManager.getEditorTopToolbarItemOrder()) }
    var editorTopToolbarMoreItems by remember { mutableStateOf(notePrefsManager.getEditorTopToolbarMoreItems()) }
    var noteSidePanelsEnabled by remember { mutableStateOf(notePrefsManager.isNoteSidePanelsEnabled()) }
    var noteSidePanelOpenMode by remember { mutableStateOf(notePrefsManager.getNoteSidePanelOpenMode()) }
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
    var showDrawingPad by remember { mutableStateOf(false) }
    var closeEditorWhenDashboardDrawingDismissed by remember { mutableStateOf(false) }
    var noteSearchQuery by remember { mutableStateOf("") }
    var noteReplaceText by remember(editorDocumentKey) { mutableStateOf("") }
    var noteSearchUseRegex by remember { mutableStateOf(false) }
    var noteSearchMatchCase by remember { mutableStateOf(false) }
    var noteSearchError by remember { mutableStateOf<String?>(null) }
    var noteSearchMatchCount by remember { mutableStateOf(0) }
    var noteSearchCurrentStart by remember { mutableStateOf(-1) }
    var noteSearchCurrentEnd by remember { mutableStateOf(-1) }
    var noteSearchCurrentOrdinal by remember { mutableStateOf(0) }
    var noteSearchFocused by remember { mutableStateOf(false) }
    var noteReplaceFocused by remember { mutableStateOf(false) }
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
    val replaceFocusRequester = remember { FocusRequester() }
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
        Log.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "screen state key=$editorDocumentKey open=$effectiveEditorOpen editing=$isEditing isOpening=$isOpeningNoteContent " +
                "largeBlocked=$blocksDirectEditForLargeNote notePath=${currentNote?.file?.path} " +
                "noteContentLen=${currentNote?.content?.length ?: -1} notePreviewLen=${currentNote?.contentPreview?.length ?: -1} " +
                "initialContentLen=${initialContent.length} renderedPreviewLen=${renderedPreview.length}",
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

    fun openNoteSidePanel(targetOffsetPx: Float) {
        if (abs(noteSidePanelTargetPx - targetOffsetPx) < 1f) {
            closeNoteSidePanel()
        } else {
            noteSidePanelTargetPx = targetOffsetPx.coerceIn(-noteSidePanelWidthPx, noteSidePanelWidthPx)
            noteSidePanelDragPx = noteSidePanelTargetPx
            noteSidePanelDragStartPx = noteSidePanelTargetPx
            isNoteSidePanelDragging = false
        }
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
    val noteSidePanelGestureEnabled = noteSidePanelsActive && noteSidePanelOpenMode == PrefsManager.NoteSidePanelOpenMode.GESTURE
    val noteSidePanelToolbarEnabled = noteSidePanelsEnabled && noteSidePanelOpenMode == PrefsManager.NoteSidePanelOpenMode.TOOLBAR && !isClosingEditor
    val noteSidePanelContentDragModifier = Modifier.noteSidePanelDrag(
        enabled = noteSidePanelGestureEnabled && !noteSidePanelHasOffset && (!isEditing || !isKeyboardVisible),
        protectEditorTouch = isEditing,
    )
    val noteSidePanelActiveDragModifier = Modifier.noteSidePanelDrag(noteSidePanelGestureEnabled && noteSidePanelHasOffset)
    val noteSidePanelEdgeDragModifier =
        Modifier.noteSidePanelDrag(noteSidePanelGestureEnabled && (!noteSidePanelHasOffset || isNoteSidePanelDragging))

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

    LaunchedEffect(effectiveEditorOpen, editorDocumentKey) {
        Log.d(
            EDITOR_TOP_BAR_TRACE_TAG,
            "reload prefs start key=$editorDocumentKey open=$effectiveEditorOpen " +
                "oldOrder=${editorTopToolbarOrder.toEditorTopBarLogText()} " +
                "oldMore=${editorTopToolbarMoreItems.toEditorTopBarLogText()} " +
                "oldPanelsEnabled=$noteSidePanelsEnabled oldMode=$noteSidePanelOpenMode",
        )
        editorTopToolbarOrder = notePrefsManager.getEditorTopToolbarItemOrder()
        editorTopToolbarMoreItems = notePrefsManager.getEditorTopToolbarMoreItems()
        noteSidePanelsEnabled = notePrefsManager.isNoteSidePanelsEnabled()
        noteSidePanelOpenMode = notePrefsManager.getNoteSidePanelOpenMode()
        Log.d(
            EDITOR_TOP_BAR_TRACE_TAG,
            "reload prefs done key=$editorDocumentKey open=$effectiveEditorOpen " +
                "newOrder=${editorTopToolbarOrder.toEditorTopBarLogText()} " +
                "newMore=${editorTopToolbarMoreItems.toEditorTopBarLogText()} " +
                "newPanelsEnabled=$noteSidePanelsEnabled newMode=$noteSidePanelOpenMode",
        )
    }

    LaunchedEffect(
        editorDocumentKey,
        effectiveEditorOpen,
        currentNote?.file?.path,
        isEditing,
        isPrivacyEditor,
        isClosingEditor,
        noteSidePanelsEnabled,
        noteSidePanelsReady,
        noteSidePanelsActive,
        noteSidePanelOpenMode,
        noteSidePanelToolbarEnabled,
        editorTopToolbarOrder,
        editorTopToolbarMoreItems,
    ) {
        val normalizedOrder = editorTopToolbarOrder.distinct().toMutableList().also { order ->
            PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.forEach { if (it !in order) order.add(it) }
        }
        val filteredOrder = normalizedOrder.filter { item ->
            noteSidePanelToolbarEnabled || (item != PrefsManager.EditorTopToolbarItemId.OUTLINE && item != PrefsManager.EditorTopToolbarItemId.REMARKS)
        }
        val safeMoreItems = editorTopToolbarMoreItems
            .filter { it in filteredOrder && it != PrefsManager.EditorTopToolbarItemId.MORE }
            .toSet()
        val moreDisplayItems = filteredOrder.filter { it in safeMoreItems }
        val topDisplayItems = filteredOrder.filter { it !in safeMoreItems }
        Log.d(
            EDITOR_TOP_BAR_TRACE_TAG,
            "state key=$editorDocumentKey path=${currentNote?.file?.path} open=$effectiveEditorOpen editing=$isEditing " +
                "privacy=$isPrivacyEditor closing=$isClosingEditor panelsEnabled=$noteSidePanelsEnabled " +
                "panelsReady=$noteSidePanelsReady panelsActive=$noteSidePanelsActive mode=$noteSidePanelOpenMode " +
                "toolbarEnabled=$noteSidePanelToolbarEnabled rawOrder=${editorTopToolbarOrder.toEditorTopBarLogText()} " +
                "rawMore=${editorTopToolbarMoreItems.toEditorTopBarLogText()} filteredOrder=${filteredOrder.toEditorTopBarLogText()} " +
                "top=${topDisplayItems.toEditorTopBarLogText()} more=${moreDisplayItems.toEditorTopBarLogText()} " +
                "showOutline=${PrefsManager.EditorTopToolbarItemId.OUTLINE in topDisplayItems || PrefsManager.EditorTopToolbarItemId.OUTLINE in moreDisplayItems} " +
                "showRemarks=${PrefsManager.EditorTopToolbarItemId.REMARKS in topDisplayItems || PrefsManager.EditorTopToolbarItemId.REMARKS in moreDisplayItems}",
        )
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

    fun openDrawingPad() {
        requestKeyboardOnEdit = false
        noteSearchFocused = false
        noteReplaceFocused = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        showDrawingPad = true
        showLabelMenu = false
        showMoreMenu = false
        showHeadingMenu = false
        showMathMenu = false
    }

    fun saveDrawingImage(bitmap: android.graphics.Bitmap) {
        coroutineScope.launch {
            val markdown = viewModel.importDrawingImage(bitmap, folder)
            if (markdown.isNotBlank()) {
                closeEditorWhenDashboardDrawingDismissed = false
                insertImageMarkdown(markdown)
                showDrawingPad = false
            } else {
                Toast.makeText(context, "画图保存失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(editorDocumentKey, openDrawingPadOnStart) {
        if (openDrawingPadOnStart) {
            closeEditorWhenDashboardDrawingDismissed = true
            openDrawingPad()
            onDrawingPadStartConsumed()
        }
    }

    LaunchedEffect(showDrawingPad) {
        if (showDrawingPad) {
            requestKeyboardOnEdit = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
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
        if (!showNoteSearch || (!noteSearchFocused && !noteReplaceFocused)) return
        noteSearchFocused = false
        noteReplaceFocused = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        Log.d(EDITOR_TRACE_TAG, "noteSearch cursor hidden reason=$reason")
    }

    fun closeNoteSearch() {
        showNoteSearch = false
        noteSearchQuery = ""
        noteReplaceText = ""
        noteSearchError = null
        noteSearchMatchCount = 0
        noteSearchCurrentStart = -1
        noteSearchCurrentEnd = -1
        noteSearchCurrentOrdinal = 0
        noteSearchFocused = false
        noteReplaceFocused = false
        editorController.clearSearchHighlights()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun updateSearchState(
        query: String,
        currentStart: Int,
        text: String = editorController.getText(),
    ): SearchMatchSummary {
        val summary = summarizeNoteSearchMatches(
            text = text,
            query = query,
            preferredStart = currentStart,
            useRegex = noteSearchUseRegex,
            matchCase = noteSearchMatchCase,
        )
        noteSearchError = summary.errorMessage
        noteSearchMatchCount = summary.count
        noteSearchCurrentStart = summary.currentStart
        noteSearchCurrentEnd = summary.currentEnd
        noteSearchCurrentOrdinal = summary.currentOrdinal
        val highlightCount = if (isEditing && summary.errorMessage == null) {
            editorController.highlightSearch(
                query = query,
                currentStart = summary.currentStart,
                useRegex = noteSearchUseRegex,
                matchCase = noteSearchMatchCase,
            )
        } else {
            editorController.clearSearchHighlights()
            0
        }
        Log.d(
            EDITOR_TRACE_TAG,
            "noteSearch queryLen=${query.length} regex=$noteSearchUseRegex matchCase=$noteSearchMatchCase " +
                "textLen=${text.length} current=${summary.currentStart}..${summary.currentEnd} " +
                "ordinal=${summary.currentOrdinal}/${summary.count} error=${summary.errorMessage} " +
                "highlights=$highlightCount attached=${editorController.editorView != null} editing=$isEditing",
        )
        return summary
    }

    fun selectSearchMatch(index: Int, query: String) {
        if (query.isBlank()) return
        val text = editorController.getText()
        if (index < 0 || !isEditing) {
            updateSearchState(query, -1, text)
            return
        }
        val summary = updateSearchState(query, index, text)
        if (summary.currentStart < 0 || summary.currentEnd <= summary.currentStart) return
        editorController.setSelection(summary.currentStart, summary.currentEnd)
        coroutineScope.launch {
            withFrameNanos { }
            delay(60)
            editorController.setSelection(summary.currentStart, summary.currentEnd)
            editorController.scrollToOffset(summary.currentStart)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    fun searchInNote(query: String) {
        if (query.isBlank()) {
            noteSearchError = null
            noteSearchMatchCount = 0
            noteSearchCurrentStart = -1
            noteSearchCurrentEnd = -1
            noteSearchCurrentOrdinal = 0
            editorController.clearSearchHighlights()
            return
        }
        val text = editorController.getText()
        val result = buildNoteSearchMatches(text, query, noteSearchUseRegex, noteSearchMatchCase)
        val index = result.matches.firstOrNull()?.start ?: -1
        selectSearchMatch(index, query)
    }

    fun moveSearchMatch(forward: Boolean) {
        val query = noteSearchQuery
        if (query.isBlank()) return
        val text = editorController.getText()
        val result = buildNoteSearchMatches(text, query, noteSearchUseRegex, noteSearchMatchCase)
        noteSearchError = result.errorMessage
        if (result.errorMessage != null || result.matches.isEmpty()) {
            updateSearchState(query, -1, text)
            return
        }
        val currentIndex = result.matches.indexOfFirst { it.start == noteSearchCurrentStart && it.end == noteSearchCurrentEnd }
        val nextIndex = if (forward) {
            if (currentIndex >= 0) (currentIndex + 1) % result.matches.size else 0
        } else {
            if (currentIndex > 0) currentIndex - 1 else result.matches.lastIndex
        }
        selectSearchMatch(result.matches[nextIndex].start, query)
    }

    fun replaceCurrentSearchMatch() {
        val query = noteSearchQuery
        if (query.isBlank()) {
            Toast.makeText(context, "请输入要查找的文本", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isEditing) {
            Toast.makeText(context, "请先切换到编辑状态再替换", Toast.LENGTH_SHORT).show()
            return
        }
        val snapshot = editorController.getSnapshot()
        val text = snapshot.content
        val result = buildNoteSearchMatches(text, query, noteSearchUseRegex, noteSearchMatchCase)
        if (result.errorMessage != null) {
            updateSearchState(query, -1, text)
            Toast.makeText(context, result.errorMessage, Toast.LENGTH_SHORT).show()
            return
        }
        val rawStart = minOf(snapshot.selection.start, snapshot.selection.end).coerceIn(0, text.length)
        val rawEnd = maxOf(snapshot.selection.start, snapshot.selection.end).coerceIn(0, text.length)
        val replaceMatch = result.matches.firstOrNull { it.start == rawStart && it.end == rawEnd }
            ?: result.matches.firstOrNull { it.start == noteSearchCurrentStart && it.end == noteSearchCurrentEnd }
            ?: result.matches.firstOrNull { it.start >= rawStart }
            ?: result.matches.firstOrNull()
        if (replaceMatch == null) {
            Toast.makeText(context, "没有找到要替换的文本", Toast.LENGTH_SHORT).show()
            noteSearchError = null
            noteSearchMatchCount = 0
            noteSearchCurrentStart = -1
            noteSearchCurrentEnd = -1
            noteSearchCurrentOrdinal = 0
            editorController.clearSearchHighlights()
            return
        }
        val replacement = buildCurrentReplacement(
            text = text,
            range = replaceMatch,
            query = query,
            replacement = noteReplaceText,
            useRegex = noteSearchUseRegex,
            matchCase = noteSearchMatchCase,
        )
        if (replacement.errorMessage != null) {
            Toast.makeText(context, replacement.errorMessage, Toast.LENGTH_SHORT).show()
            return
        }
        val replacementText = replacement.text ?: noteReplaceText
        editorController.setSelection(replaceMatch.start, replaceMatch.end)
        editorController.replaceSelection(replacementText)
        markEditorDirty()

        val newText = editorController.getText()
        val nextStart = (replaceMatch.start + replacementText.length).coerceIn(0, newText.length)
        val nextResult = buildNoteSearchMatches(newText, query, noteSearchUseRegex, noteSearchMatchCase)
        val nextMatch = nextResult.matches.firstOrNull { it.start >= nextStart } ?: nextResult.matches.firstOrNull()
        if (nextMatch != null) {
            selectSearchMatch(nextMatch.start, query)
        } else {
            updateSearchState(query, -1, newText)
            editorController.setSelection(nextStart)
        }
    }

    fun replaceAllSearchMatches() {
        val query = noteSearchQuery
        if (query.isBlank()) {
            Toast.makeText(context, "请输入要查找的文本", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isEditing) {
            Toast.makeText(context, "请先切换到编辑状态再替换", Toast.LENGTH_SHORT).show()
            return
        }
        val snapshot = editorController.getSnapshot()
        val replacement = replaceAllNoteSearchMatches(
            text = snapshot.content,
            query = query,
            replacement = noteReplaceText,
            useRegex = noteSearchUseRegex,
            matchCase = noteSearchMatchCase,
        )
        if (replacement.errorMessage != null) {
            updateSearchState(query, -1, snapshot.content)
            Toast.makeText(context, replacement.errorMessage, Toast.LENGTH_SHORT).show()
            return
        }
        val newText = replacement.text ?: snapshot.content
        if (replacement.count <= 0) {
            Toast.makeText(context, "没有找到要替换的文本", Toast.LENGTH_SHORT).show()
            noteSearchError = null
            noteSearchMatchCount = 0
            noteSearchCurrentStart = -1
            noteSearchCurrentEnd = -1
            noteSearchCurrentOrdinal = 0
            editorController.clearSearchHighlights()
            return
        }
        val cursor = snapshot.selection.start.coerceIn(0, newText.length)
        editorController.replaceAll(newText, TextRange(cursor, cursor))
        markEditorDirty()
        updateSearchState(query, -1, newText)
        Toast.makeText(context, "已替换 ${replacement.count} 处", Toast.LENGTH_SHORT).show()
    }

    fun jumpToHeading(heading: MarkdownHeading) {
        val text = editorController.getText()
        val target = heading.startOffset.coerceIn(0, text.length)
        showNoteInfoDialog = false
        closeNoteSearch()
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

    LaunchedEffect(noteSearchQuery, noteSearchUseRegex, noteSearchMatchCase) {
        if (showNoteSearch) {
            searchInNote(noteSearchQuery)
        }
    }

    LaunchedEffect(showNoteSearch) {
        if (!showNoteSearch) {
            noteSearchError = null
            noteSearchMatchCount = 0
            noteSearchCurrentStart = -1
            noteSearchCurrentEnd = -1
            noteSearchCurrentOrdinal = 0
            noteSearchFocused = false
            noteReplaceFocused = false
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
        editorController.releaseForClose()
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
        Log.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "screen preview render start token=$token key=$editorDocumentKey markdownLen=${markdown.length} " +
                "titleLen=${snapshot.title.length} contentLen=${snapshot.content.length}",
        )
        coroutineScope.launch {
            val preparedMarkdown = viewModel.preparePreviewMarkdown(markdown, folder)
            if (previewRenderToken == token) {
                renderedPreview = preparedMarkdown
                Log.d(
                    EDITOR_TRACE_TAG,
                    "preview render done token=$token key=$editorDocumentKey len=${preparedMarkdown.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
                Log.d(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "screen preview render done token=$token key=$editorDocumentKey len=${preparedMarkdown.length} " +
                        "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            } else {
                Log.d(EDITOR_TRACE_TAG, "preview render ignored stale token=$token latest=$previewRenderToken key=$editorDocumentKey")
                Log.w(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "screen preview render ignored stale token=$token latest=$previewRenderToken key=$editorDocumentKey " +
                        "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
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

    LaunchedEffect(effectiveEditorOpen, isEditing, currentNote, externalDraft, isLeavingEditor, showDrawingPad) {
        if (!isLeavingEditor &&
            !showDrawingPad &&
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

    LaunchedEffect(
        effectiveEditorOpen,
        isOpeningNoteContent,
        isEditing,
        blocksDirectEditForLargeNote,
        renderedPreview.length,
        initialContent.length,
    ) {
        if (effectiveEditorOpen) {
            val previewContentLen = if (isOpeningNoteContent) 0 else renderedPreview.length
            Log.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "screen visible mode=${if (isEditing && !blocksDirectEditForLargeNote) "editor" else "preview"} " +
                    "key=$editorDocumentKey isOpening=$isOpeningNoteContent editing=$isEditing largeBlocked=$blocksDirectEditForLargeNote " +
                    "initialContentLen=${initialContent.length} renderedPreviewLen=${renderedPreview.length} previewContentLen=$previewContentLen",
            )
            if (!isOpeningNoteContent && !isEditing && initialContent.isNotEmpty() && renderedPreview.isEmpty()) {
                Log.w(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "screen suspicious empty preview key=$editorDocumentKey initialContentLen=${initialContent.length} path=${currentNote?.file?.path}",
                )
            }
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
        closeNoteSearch()
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
                            NoteSearchTextField(
                                value = noteSearchQuery,
                                onValueChange = { noteSearchQuery = it },
                                placeholder = "搜索当前笔记",
                                focused = noteSearchFocused,
                                focusRequester = searchFocusRequester,
                                onFocusChanged = { noteSearchFocused = it },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    navigationIcon = {
                    if (!showNoteSearch && isEditing) {
                        IconButton(onClick = {
                            saveNote(saveHistory = true)
                            enterPreviewMode()
                        }) {
                            Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.done))
                        }
                    } else if (!showNoteSearch) {
                        IconButton(onClick = { leaveEditor() }) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (showNoteSearch) {
                        IconButton(
                            enabled = isEditing && noteSearchMatchCount > 0,
                            onClick = { moveSearchMatch(forward = false) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                        }
                        IconButton(
                            enabled = isEditing && noteSearchMatchCount > 0,
                            onClick = { moveSearchMatch(forward = true) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下一个")
                        }
                        Box(
                            modifier = Modifier.width(58.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = noteSearchError ?: if (noteSearchCurrentOrdinal > 0 && noteSearchMatchCount > 0) {
                                    "$noteSearchCurrentOrdinal/$noteSearchMatchCount"
                                } else {
                                    "${noteSearchMatchCount}处"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (noteSearchError == null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = { closeNoteSearch() },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                        }
                    } else {
                        val normalizedEditorTopToolbarOrder = editorTopToolbarOrder.distinct().toMutableList().also { order ->
                            PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.forEach { if (it !in order) order.add(it) }
                        }.filter { item ->
                            noteSidePanelToolbarEnabled || (item != PrefsManager.EditorTopToolbarItemId.OUTLINE && item != PrefsManager.EditorTopToolbarItemId.REMARKS)
                        }
                        val safeEditorTopToolbarMoreItems = editorTopToolbarMoreItems
                            .filter { it in normalizedEditorTopToolbarOrder && it != PrefsManager.EditorTopToolbarItemId.MORE }
                            .toSet()
                        val editorTopToolbarMoreDisplayItems = normalizedEditorTopToolbarOrder.filter { it in safeEditorTopToolbarMoreItems }
                        val editorTopToolbarTopItems = normalizedEditorTopToolbarOrder.filter { it !in safeEditorTopToolbarMoreItems }

                        Log.d(
                            EDITOR_TOP_BAR_TRACE_TAG,
                            "compose actions key=$editorDocumentKey toolbarEnabled=$noteSidePanelToolbarEnabled " +
                                "mode=$noteSidePanelOpenMode top=${editorTopToolbarTopItems.toEditorTopBarLogText()} " +
                                "more=${editorTopToolbarMoreDisplayItems.toEditorTopBarLogText()} showOutline=${PrefsManager.EditorTopToolbarItemId.OUTLINE in editorTopToolbarTopItems || PrefsManager.EditorTopToolbarItemId.OUTLINE in editorTopToolbarMoreDisplayItems} " +
                                "showRemarks=${PrefsManager.EditorTopToolbarItemId.REMARKS in editorTopToolbarTopItems || PrefsManager.EditorTopToolbarItemId.REMARKS in editorTopToolbarMoreDisplayItems}",
                        )

                        @Composable
                        fun LabelAction() {
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
                        }

                        @Composable
                        fun OutlineAction() {
                            IconButton(onClick = { openNoteSidePanel(noteSidePanelWidthPx) }) {
                                Icon(
                                    Icons.Outlined.FormatListBulleted,
                                    contentDescription = "大纲",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        @Composable
                        fun RemarksAction() {
                            IconButton(onClick = { openNoteSidePanel(-noteSidePanelWidthPx) }) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = "属性备注",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        @Composable
                        fun SearchAction() {
                            IconButton(onClick = {
                                showLabelMenu = false
                                showMoreMenu = false
                                showHeadingMenu = false
                                showMathMenu = false
                                showNoteSearch = true
                            }) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = "搜索当前笔记",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        @Composable
                        fun EditAction() {
                            if (!isEditing) {
                                IconButton(onClick = { enterEditMode() }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit))
                                }
                            }
                        }

                        val editorTopToolbarNoteOperationItems = setOf(
                            PrefsManager.EditorTopToolbarItemId.HISTORY,
                            PrefsManager.EditorTopToolbarItemId.PRIVACY,
                            PrefsManager.EditorTopToolbarItemId.ARCHIVE,
                            PrefsManager.EditorTopToolbarItemId.DELETE,
                        )

                        @Composable
                        fun HistoryAction() {
                            val note = currentNote
                            if (note != null && !note.isTrashed) {
                                IconButton(onClick = { showHistoryDialog = true }) {
                                    Icon(Icons.Outlined.History, contentDescription = "历史版本")
                                }
                            }
                        }

                        @Composable
                        fun PrivacyAction() {
                            val note = currentNote
                            if (note != null && !note.isTrashed) {
                                IconButton(onClick = { addCurrentNoteToPrivacy(note) { leaveEditor() } }) {
                                    Icon(Icons.Outlined.Lock, contentDescription = "保护")
                                }
                            }
                        }

                        @Composable
                        fun ArchiveAction() {
                            val note = currentNote
                            if (note != null && !note.isTrashed) {
                                IconButton(onClick = {
                                    if (note.isArchived) {
                                        viewModel.restoreNote(note)
                                    } else {
                                        viewModel.archiveNote(note)
                                    }
                                    leaveEditor()
                                }) {
                                    Icon(
                                        if (note.isArchived) Icons.Outlined.Refresh else Icons.Outlined.Archive,
                                        contentDescription = if (note.isArchived) stringResource(R.string.unarchive) else stringResource(R.string.archive),
                                    )
                                }
                            }
                        }

                        @Composable
                        fun DeleteAction() {
                            val note = currentNote
                            when {
                                isPrivacyEditor && onDeletePrivacyNote != null -> {
                                    IconButton(onClick = { onDeletePrivacyNote() }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                                    }
                                }
                                note != null && note.isTrashed -> {
                                    IconButton(onClick = {
                                        viewModel.restoreNote(note)
                                        leaveEditor()
                                    }) {
                                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.restore))
                                    }
                                }
                                note != null -> {
                                    IconButton(onClick = {
                                        viewModel.deleteNote(note)
                                        leaveEditor()
                                    }) {
                                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete))
                                    }
                                }
                            }
                        }

                        @Composable
                        fun EditorTopToolbarMoreItem(item: PrefsManager.EditorTopToolbarItemId) {
                            when (item) {
                                PrefsManager.EditorTopToolbarItemId.LABEL -> {
                                    if (!isPrivacyEditor) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.root_folder_no_label)) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                                            onClick = {
                                                folder = ""
                                                showMoreMenu = false
                                                if (currentNote != null) {
                                                    saveNote(saveHistory = false)
                                                }
                                            },
                                        )
                                        labels.forEach { label ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                                                onClick = {
                                                    folder = label
                                                    showMoreMenu = false
                                                    if (currentNote != null) {
                                                        saveNote(saveHistory = false)
                                                    }
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.create_new_label)) },
                                            leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                            onClick = {
                                                showMoreMenu = false
                                                showCreateLabelDialog = true
                                            },
                                        )
                                    }
                                }
                                PrefsManager.EditorTopToolbarItemId.OUTLINE -> if (noteSidePanelToolbarEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("大纲") },
                                        leadingIcon = { Icon(Icons.Outlined.FormatListBulleted, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            openNoteSidePanel(noteSidePanelWidthPx)
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.REMARKS -> if (noteSidePanelToolbarEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("属性备注") },
                                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            openNoteSidePanel(-noteSidePanelWidthPx)
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.SEARCH -> {
                                    DropdownMenuItem(
                                        text = { Text("搜索当前笔记") },
                                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            showLabelMenu = false
                                            showHeadingMenu = false
                                            showMathMenu = false
                                            showNoteSearch = true
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.EDIT -> {
                                    if (!isEditing) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.edit)) },
                                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                            onClick = {
                                                showMoreMenu = false
                                                enterEditMode()
                                            },
                                        )
                                    }
                                }
                                PrefsManager.EditorTopToolbarItemId.HISTORY -> {
                                    val note = currentNote
                                    if (note != null && !note.isTrashed) {
                                        DropdownMenuItem(
                                            text = { Text("历史版本") },
                                            leadingIcon = { Icon(Icons.Outlined.History, null) },
                                            onClick = {
                                                showHistoryDialog = true
                                                showMoreMenu = false
                                            },
                                        )
                                    }
                                }
                                PrefsManager.EditorTopToolbarItemId.PRIVACY -> {
                                    val note = currentNote
                                    if (note != null && !note.isTrashed) {
                                        DropdownMenuItem(
                                            text = { Text("保护") },
                                            leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                                            onClick = {
                                                addCurrentNoteToPrivacy(note) { leaveEditor() }
                                                showMoreMenu = false
                                            },
                                        )
                                    }
                                }
                                PrefsManager.EditorTopToolbarItemId.ARCHIVE -> {
                                    val note = currentNote
                                    if (note != null && !note.isTrashed) {
                                        if (note.isArchived) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.unarchive)) },
                                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                                onClick = {
                                                    viewModel.restoreNote(note)
                                                    showMoreMenu = false
                                                    leaveEditor()
                                                },
                                            )
                                        } else {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.archive)) },
                                                leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                                onClick = {
                                                    viewModel.archiveNote(note)
                                                    showMoreMenu = false
                                                    leaveEditor()
                                                },
                                            )
                                        }
                                    }
                                }
                                PrefsManager.EditorTopToolbarItemId.DELETE -> {
                                    val note = currentNote
                                    if (note != null && note.isTrashed) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.restore)) },
                                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                            onClick = {
                                                viewModel.restoreNote(note)
                                                showMoreMenu = false
                                                leaveEditor()
                                            },
                                        )
                                    } else if (note != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete)) },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                            onClick = {
                                                viewModel.deleteNote(note)
                                                showMoreMenu = false
                                                leaveEditor()
                                            },
                                        )
                                    }
                                }
                                PrefsManager.EditorTopToolbarItemId.MORE -> Unit
                            }
                        }

                        @Composable
                        fun MoreAction() {
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
                                        val renderedMoreToolbarItems = editorTopToolbarMoreDisplayItems
                                            .filter { it != PrefsManager.EditorTopToolbarItemId.LABEL }
                                            .filter { it !in editorTopToolbarNoteOperationItems }
                                            .filter { it != PrefsManager.EditorTopToolbarItemId.EDIT || !isEditing }
                                        renderedMoreToolbarItems.forEach { item ->
                                            EditorTopToolbarMoreItem(item)
                                        }
                                        if (renderedMoreToolbarItems.isNotEmpty()) {
                                            HorizontalDivider()
                                        }
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
                                        val renderedMoreToolbarItems = editorTopToolbarMoreDisplayItems
                                            .filter { it != PrefsManager.EditorTopToolbarItemId.EDIT || !isEditing }
                                        renderedMoreToolbarItems.forEach { item ->
                                            EditorTopToolbarMoreItem(item)
                                        }
                                    }
                                }
                            }
                        }

                        editorTopToolbarTopItems.forEach { item ->
                            when (item) {
                                PrefsManager.EditorTopToolbarItemId.LABEL -> LabelAction()
                                PrefsManager.EditorTopToolbarItemId.OUTLINE -> if (noteSidePanelToolbarEnabled) OutlineAction()
                                PrefsManager.EditorTopToolbarItemId.REMARKS -> if (noteSidePanelToolbarEnabled) RemarksAction()
                                PrefsManager.EditorTopToolbarItemId.SEARCH -> SearchAction()
                                PrefsManager.EditorTopToolbarItemId.EDIT -> EditAction()
                                PrefsManager.EditorTopToolbarItemId.HISTORY -> HistoryAction()
                                PrefsManager.EditorTopToolbarItemId.PRIVACY -> PrivacyAction()
                                PrefsManager.EditorTopToolbarItemId.ARCHIVE -> ArchiveAction()
                                PrefsManager.EditorTopToolbarItemId.DELETE -> DeleteAction()
                                PrefsManager.EditorTopToolbarItemId.MORE -> MoreAction()
                            }
                        }
                    } // end if (showNoteSearch) else
                },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
                )
                AnimatedVisibility(visible = showNoteSearch && isEditing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NoteSearchChip(
                            text = "Aa",
                            selected = noteSearchMatchCase,
                            onClick = { noteSearchMatchCase = !noteSearchMatchCase },
                        )
                        NoteSearchChip(
                            text = ".*",
                            selected = noteSearchUseRegex,
                            onClick = { noteSearchUseRegex = !noteSearchUseRegex },
                        )
                        NoteSearchTextField(
                            value = noteReplaceText,
                            onValueChange = { noteReplaceText = it },
                            placeholder = "替换为",
                            focused = noteReplaceFocused,
                            focusRequester = replaceFocusRequester,
                            onFocusChanged = { noteReplaceFocused = it },
                            modifier = Modifier.weight(1f),
                        )
                        NoteSearchChip(
                            text = "替换",
                            enabled = noteSearchQuery.isNotBlank(),
                            onClick = { replaceCurrentSearchMatch() },
                        )
                        NoteSearchChip(
                            text = "全部",
                            enabled = noteSearchQuery.isNotBlank(),
                            onClick = { replaceAllSearchMatches() },
                        )
                    }
                }
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
                                    KardLeafCustomFeatures.ToolbarItem.DRAWING -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.Palette,
                                        contentDescription = "绘图",
                                        onClick = { openDrawingPad() },
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
                                        KardLeafCustomFeatures.ToolbarItem.DRAWING -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.Palette,
                                            contentDescription = "绘图",
                                            onClick = { openDrawingPad() },
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
            if (effectiveEditorOpen && isEditing && !isClosingEditor && !blocksDirectEditForLargeNote) {
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
                val previewContent = if (isOpeningNoteContent) "" else renderedPreview
                PreviewWebView(
                    content = previewContent,
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
            if (noteSidePanelGestureEnabled && (!noteSidePanelHasOffset || isNoteSidePanelDragging)) {
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
                                noteSidePanelGestureEnabled && (!noteSidePanelHasOffset || isNoteSidePanelDragging)
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
        if (showDrawingPad) {
            DrawingPadScreen(
                modifier = Modifier.zIndex(10f),
                onDismiss = {
                    val shouldCloseEditor = closeEditorWhenDashboardDrawingDismissed
                    closeEditorWhenDashboardDrawingDismissed = false
                    showDrawingPad = false
                    if (shouldCloseEditor) {
                        leaveEditor()
                    }
                },
                onSave = { bitmap -> saveDrawingImage(bitmap) },
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


private data class NoteSearchMatchRange(
    val start: Int,
    val end: Int,
)

private data class NoteSearchMatchesResult(
    val matches: List<NoteSearchMatchRange> = emptyList(),
    val errorMessage: String? = null,
)

private data class NoteSearchReplacementResult(
    val text: String? = null,
    val count: Int = 0,
    val errorMessage: String? = null,
)

private data class SearchMatchSummary(
    val count: Int,
    val currentStart: Int,
    val currentEnd: Int,
    val currentOrdinal: Int,
    val errorMessage: String? = null,
)

private fun summarizeNoteSearchMatches(
    text: String,
    query: String,
    preferredStart: Int,
    useRegex: Boolean,
    matchCase: Boolean,
): SearchMatchSummary {
    val result = buildNoteSearchMatches(text, query, useRegex, matchCase)
    result.errorMessage?.let { return SearchMatchSummary(0, -1, -1, 0, it) }
    val matches = result.matches
    if (matches.isEmpty()) return SearchMatchSummary(0, -1, -1, 0)
    val preferredIndex = matches.indexOfFirst { it.start == preferredStart }
    val currentIndex = if (preferredIndex >= 0) preferredIndex else 0
    val current = matches[currentIndex]
    return SearchMatchSummary(
        count = matches.size,
        currentStart = current.start,
        currentEnd = current.end,
        currentOrdinal = currentIndex + 1,
    )
}

private fun buildNoteSearchMatches(
    text: String,
    query: String,
    useRegex: Boolean,
    matchCase: Boolean,
): NoteSearchMatchesResult {
    if (text.isEmpty() || query.isBlank()) return NoteSearchMatchesResult()
    return if (useRegex) {
        val pattern = createNoteSearchPattern(query, matchCase)
            ?: return NoteSearchMatchesResult(errorMessage = "正则表达式无效")
        val matcher = pattern.matcher(text)
        val matches = buildList {
            while (matcher.find()) {
                val start = matcher.start().coerceIn(0, text.length)
                val end = matcher.end().coerceIn(0, text.length)
                if (end > start) add(NoteSearchMatchRange(start, end))
            }
        }
        NoteSearchMatchesResult(matches = matches)
    } else {
        val matches = buildList {
            var searchFrom = 0
            while (searchFrom <= text.length - query.length) {
                val index = text.indexOf(query, startIndex = searchFrom, ignoreCase = !matchCase)
                if (index < 0) break
                val end = index + query.length
                add(NoteSearchMatchRange(index, end))
                searchFrom = end.coerceAtLeast(index + 1)
            }
        }
        NoteSearchMatchesResult(matches = matches)
    }
}

private fun buildCurrentReplacement(
    text: String,
    range: NoteSearchMatchRange,
    query: String,
    replacement: String,
    useRegex: Boolean,
    matchCase: Boolean,
): NoteSearchReplacementResult {
    if (!useRegex) return NoteSearchReplacementResult(text = replacement, count = 1)
    val pattern = createNoteSearchPattern(query, matchCase)
        ?: return NoteSearchReplacementResult(errorMessage = "正则表达式无效")
    val matcher = pattern.matcher(text)
    while (matcher.find()) {
        val start = matcher.start().coerceIn(0, text.length)
        val end = matcher.end().coerceIn(0, text.length)
        if (start == range.start && end == range.end) {
            val expanded = expandRegexReplacement(replacement, matcher)
                ?: return NoteSearchReplacementResult(errorMessage = "替换内容包含无效的正则引用")
            return NoteSearchReplacementResult(text = expanded, count = 1)
        }
    }
    return NoteSearchReplacementResult(errorMessage = "没有找到要替换的文本")
}

private fun replaceAllNoteSearchMatches(
    text: String,
    query: String,
    replacement: String,
    useRegex: Boolean,
    matchCase: Boolean,
): NoteSearchReplacementResult {
    if (text.isEmpty() || query.isBlank()) return NoteSearchReplacementResult(text = text)
    if (!useRegex) {
        val builder = StringBuilder(text.length)
        var count = 0
        var searchFrom = 0
        while (searchFrom <= text.length - query.length) {
            val index = text.indexOf(query, startIndex = searchFrom, ignoreCase = !matchCase)
            if (index < 0) break
            builder.append(text, searchFrom, index)
            builder.append(replacement)
            searchFrom = index + query.length
            count++
        }
        if (count == 0) return NoteSearchReplacementResult(text = text)
        builder.append(text, searchFrom, text.length)
        return NoteSearchReplacementResult(text = builder.toString(), count = count)
    }

    val pattern = createNoteSearchPattern(query, matchCase)
        ?: return NoteSearchReplacementResult(errorMessage = "正则表达式无效")
    val matcher = pattern.matcher(text)
    val builder = StringBuilder(text.length)
    var count = 0
    var lastEnd = 0
    while (matcher.find()) {
        val start = matcher.start().coerceIn(0, text.length)
        val end = matcher.end().coerceIn(0, text.length)
        if (end <= start) continue
        builder.append(text, lastEnd, start)
        val expanded = expandRegexReplacement(replacement, matcher)
            ?: return NoteSearchReplacementResult(errorMessage = "替换内容包含无效的正则引用")
        builder.append(expanded)
        lastEnd = end
        count++
    }
    if (count == 0) return NoteSearchReplacementResult(text = text)
    builder.append(text, lastEnd, text.length)
    return NoteSearchReplacementResult(text = builder.toString(), count = count)
}

private fun createNoteSearchPattern(
    query: String,
    matchCase: Boolean,
): Pattern? =
    try {
        val caseFlags = if (matchCase) {
            0
        } else {
            Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE
        }
        Pattern.compile(query, Pattern.MULTILINE or caseFlags)
    } catch (_: PatternSyntaxException) {
        null
    }

private fun expandRegexReplacement(
    replacement: String,
    matcher: java.util.regex.Matcher,
): String? {
    val builder = StringBuilder(replacement.length)
    var index = 0
    while (index < replacement.length) {
        val char = replacement[index]
        when {
            char == '\\' && index + 1 < replacement.length -> {
                builder.append(replacement[index + 1])
                index += 2
            }
            char == '$' -> {
                var cursor = index + 1
                if (cursor >= replacement.length || !replacement[cursor].isDigit()) return null
                while (cursor < replacement.length && replacement[cursor].isDigit()) cursor++
                val groupIndex = replacement.substring(index + 1, cursor).toIntOrNull() ?: return null
                val groupText = try {
                    matcher.group(groupIndex).orEmpty()
                } catch (_: RuntimeException) {
                    return null
                }
                builder.append(groupText)
                index = cursor
            }
            else -> {
                builder.append(char)
                index++
            }
        }
    }
    return builder.toString()
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

