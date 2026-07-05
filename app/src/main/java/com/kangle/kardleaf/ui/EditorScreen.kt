package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafContentLimits
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeMode
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
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
private const val CODEMIRROR_DEBUG_TRACE_TAG = "KardLeafCM6Trace"
private const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
private const val OPEN_PATH_PROBE_TAG = "KardLeafOpenPathProbe"
private const val USER_PERF_TRACE_TAG = "KardLeafUserPerf"
private const val EDITOR_GESTURE_TAG = "KardLeafGestureTrace"
private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val EDITOR_TOP_BAR_TRACE_TAG = "KardLeafEditorTopBar"
private const val SEARCH_TRACE_TAG = "KardLeafSearchTrace"
private const val SAVE_PATH_TRACE_TAG = "KardLeafSavePath"
private const val TITLE_TRACE_TAG = "KardLeafTitleTrace"
private const val CODEMIRROR_IME_TRACE_TAG = "KardLeafCM6ImeTrace"
private val CODEMIRROR_IME_OUTER_TRACE_ENABLED: Boolean
    get() = KardLeafLog.isEnabled(CODEMIRROR_IME_TRACE_TAG)
private const val MENU_REOPEN_GUARD_MS = 250L
private const val DIRECT_EDIT_MAX_CHARS = 600_000
private const val WEBVIEW_PREVIEW_MAX_CHARS = 50_000
private const val USER_PERF_LARGE_NOTE_MIN_CHARS = 50_000
private const val LARGE_TEXT_PREVIEW_CHUNK_CHARS = 300

private fun editorMemorySummary(): String {
    val runtime = Runtime.getRuntime()
    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
    val totalMb = runtime.totalMemory() / 1024 / 1024
    val maxMb = runtime.maxMemory() / 1024 / 1024
    return "mem=${usedMb}MB/${totalMb}MB max=${maxMb}MB"
}

private fun userPerfNoteSizeTier(length: Int): String = when {
    length < 10_000 -> "lt_1w"
    length < 50_000 -> "1w_5w"
    length < 100_000 -> "5w_10w"
    length < 1_000_000 -> "10w_100w"
    else -> "gte_100w"
}

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


@Composable
private fun LargePlainTextPreview(
    title: String,
    content: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    searchCurrentStart: Int = -1,
    searchCurrentEnd: Int = -1,
    onUserInteraction: () -> Unit = {},
    onFastScrollSourceScrolled: () -> Unit = {},
    onFirstContentLaidOut: () -> Unit = {},
    contentTextSizeSp: Float = 16f,
    contentLineHeightMultiplier: Float = 1.55f,
    contentLetterSpacingSp: Float = 0f,
    contentParagraphSpacingDp: Float = 8f,
    contentFontFamily: String = "system",
) {
    val chunkCount = largePlainTextPreviewChunkCount(content.length)
    val searchHighlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    var scrollPerfInitialized by remember(content.length) { mutableStateOf(false) }
    var scrollPerfStartMs by remember(content.length) { mutableStateOf(0L) }
    var scrollPerfLastMs by remember(content.length) { mutableStateOf(0L) }
    var scrollPerfFrames by remember(content.length) { mutableStateOf(0) }
    var scrollPerfSlowFrames by remember(content.length) { mutableStateOf(0) }
    var scrollPerfMaxFrameMs by remember(content.length) { mutableStateOf(0L) }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (!scrollPerfInitialized) {
            scrollPerfInitialized = true
            return@LaunchedEffect
        }
        val now = SystemClock.elapsedRealtime()
        if (scrollPerfStartMs <= 0L) {
            scrollPerfStartMs = now
            scrollPerfLastMs = now
            scrollPerfFrames = 0
            scrollPerfSlowFrames = 0
            scrollPerfMaxFrameMs = 0L
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorScroll humanStart mode=largePlainPreview contentLen=${content.length} " +
                    "sizeTier=${userPerfNoteSizeTier(content.length)} firstIndex=${listState.firstVisibleItemIndex} " +
                    "offset=${listState.firstVisibleItemScrollOffset}",
            )
        } else {
            val frameMs = now - scrollPerfLastMs
            if (frameMs > 0L) {
                scrollPerfFrames++
                scrollPerfMaxFrameMs = maxOf(scrollPerfMaxFrameMs, frameMs)
                if (frameMs > 32L) scrollPerfSlowFrames++
            }
            scrollPerfLastMs = now
        }
        onFastScrollSourceScrolled()
        delay(180L)
        if (scrollPerfLastMs == now && scrollPerfStartMs > 0L) {
            val elapsed = (scrollPerfLastMs - scrollPerfStartMs).coerceAtLeast(0L)
            val avgFrame = if (scrollPerfFrames > 0) elapsed.toFloat() / scrollPerfFrames else 0f
            val smooth = scrollPerfSlowFrames == 0 && scrollPerfMaxFrameMs <= 32L
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorScroll humanSettled mode=largePlainPreview elapsed=${elapsed}ms " +
                    "frames=$scrollPerfFrames slowFrames=$scrollPerfSlowFrames " +
                    "maxFrame=${scrollPerfMaxFrameMs}ms avgFrame=${String.format("%.1f", avgFrame)}ms " +
                    "smooth=$smooth contentLen=${content.length} sizeTier=${userPerfNoteSizeTier(content.length)} " +
                    "firstIndex=${listState.firstVisibleItemIndex} offset=${listState.firstVisibleItemScrollOffset}",
            )
            scrollPerfStartMs = 0L
            scrollPerfLastMs = 0L
            scrollPerfFrames = 0
            scrollPerfSlowFrames = 0
            scrollPerfMaxFrameMs = 0L
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(content.length) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        onUserInteraction()
                    }
                }
            },
    ) {
        item(key = "large_plain_text_preview_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "当前笔记过大，已切换为纯文本快速预览，正文会按需分块显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(
            count = chunkCount,
            key = { index -> "large_plain_text_chunk_$index" },
        ) { index ->
            val start = index * LARGE_TEXT_PREVIEW_CHUNK_CHARS
            val end = minOf(start + LARGE_TEXT_PREVIEW_CHUNK_CHARS, content.length)
            val chunkText = content.substring(start, end)
            val highlightStart = maxOf(searchCurrentStart, start)
            val highlightEnd = minOf(searchCurrentEnd, end)
            val chunkDisplayText: AnnotatedString = if (
                searchCurrentStart >= 0 &&
                searchCurrentEnd > searchCurrentStart &&
                highlightStart < highlightEnd
            ) {
                buildAnnotatedString {
                    append(chunkText)
                    addStyle(
                        style = SpanStyle(background = searchHighlightColor),
                        start = highlightStart - start,
                        end = highlightEnd - start,
                    )
                }
            } else {
                AnnotatedString(chunkText)
            }
            Text(
                text = chunkDisplayText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = contentTextSizeSp.sp,
                    lineHeight = (contentTextSizeSp * contentLineHeightMultiplier).sp,
                    letterSpacing = contentLetterSpacingSp.sp,
                    fontFamily = editorComposeFontFamily(contentFontFamily),
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (index == 0) {
                            Modifier.onGloballyPositioned { onFirstContentLaidOut() }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 18.dp, vertical = (contentParagraphSpacingDp / 4f).dp),
            )
        }
    }
}

private fun editorComposeFontFamily(fontFamily: String): FontFamily? =
    when (fontFamily.trim().lowercase(Locale.ROOT)) {
        "", "system" -> null
        "sans-serif" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> null
    }

private fun largePlainTextPreviewChunkCount(textLength: Int): Int =
    if (textLength <= 0) 0 else ((textLength - 1) / LARGE_TEXT_PREVIEW_CHUNK_CHARS) + 1

private fun largePlainTextPreviewFastScrollMetrics(
    listState: LazyListState,
    chunkCount: Int,
): EditorFastScrollMetrics {
    val totalItems = chunkCount + 1
    if (totalItems <= 1) return EditorFastScrollMetrics()
    val visibleItems = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val maxFirstIndex = (totalItems - visibleItems).coerceAtLeast(1)
    return EditorFastScrollMetrics(
        canScroll = true,
        ratio = (listState.firstVisibleItemIndex.toFloat() / maxFirstIndex).coerceIn(0f, 1f),
        thumbFraction = (visibleItems.toFloat() / totalItems).coerceIn(0f, 1f),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLeavingEditorStart: () -> Unit = {},
    editorOpenStartRealtimeMs: Long? = null,
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
    val pendingEditorSearchJump by viewModel.pendingEditorSearchJump.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState(initial = emptyList())
    val externalDraft by viewModel.externalNoteDraft.collectAsState()
    val isEditorOpen by viewModel.isEditorOpen.collectAsState()
    val isOpeningNoteContent by viewModel.isOpeningNoteContent.collectAsState()
    val isShowingPartialLargeNote by viewModel.isShowingPartialLargeNote.collectAsState()
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
    val imeInsets = WindowInsets.ime
    val navigationBarsInsets = WindowInsets.navigationBars
    val defaultOpenNoteMode = remember {
        KardLeafCustomFeatures.getOpenNoteMode(context)
    }
    val toolbarOrder = remember {
        KardLeafCustomFeatures.getToolbarOrder(context)
    }
    val notePrefsManager = remember { PrefsManager(context) }
    val savedEditorKernel = notePrefsManager.getEditorKernel()
    val autoCodeMirrorThresholdChars = notePrefsManager.getAutoCodeMirrorThresholdChars()
    val codeMirrorLivePreviewEnabled = notePrefsManager.isCodeMirrorLivePreviewEnabled()
    val editorFontSizeSp = notePrefsManager.getEditorFontSizeSp()
    val editorLineHeightMultiplier = notePrefsManager.getEditorLineHeightMultiplier()
    val editorLetterSpacingSp = notePrefsManager.getEditorLetterSpacingSp()
    val editorParagraphSpacingDp = notePrefsManager.getEditorParagraphSpacingDp()
    val editorFontFamily = notePrefsManager.getEditorFontFamily()
    val editorBottomToolbarAlwaysVisible = notePrefsManager.isEditorBottomToolbarAlwaysVisible()
    var editorTopToolbarOrder by remember { mutableStateOf(notePrefsManager.getEditorTopToolbarItemOrder()) }
    var editorTopToolbarMoreItems by remember { mutableStateOf(notePrefsManager.getEditorTopToolbarMoreItems()) }
    var editorTopToolbarHiddenItems by remember { mutableStateOf(notePrefsManager.getEditorTopToolbarHiddenItems()) }
    var noteSidePanelsEnabled by remember { mutableStateOf(notePrefsManager.isNoteSidePanelsEnabled()) }
    var noteSidePanelOpenMode by remember { mutableStateOf(notePrefsManager.getNoteSidePanelOpenMode()) }
    val previewDoubleTapIntervalMs = remember { notePrefsManager.getPreviewDoubleTapIntervalMs() }
    val showNoteDetailTitle = remember { notePrefsManager.isNoteDetailTitleVisible() }
    val showNoteDetailFileInfo = remember { notePrefsManager.isNoteDetailFileInfoVisible() }
    val editorHiddenFilenamePatterns = remember { notePrefsManager.getCustomHiddenFilenamePatterns() }
    val editorUnnamedNoteDateFormat = remember { KardLeafCustomFeatures.getUnnamedNoteDateFormat(context) }
    val hideDraftTitleInEditor = !isPrivacyEditor && (
        currentNote?.folder == PrefsManager.DEFAULT_DRAFT_FOLDER_NAME ||
            externalDraft?.folder == PrefsManager.DEFAULT_DRAFT_FOLDER_NAME ||
            (currentNote == null && externalDraft == null && initialLabel == PrefsManager.DEFAULT_DRAFT_FOLDER_NAME)
    )

    val rawInitialTitle = if (isPrivacyEditor) privacyInitialTitle.orEmpty() else currentNote?.title ?: externalDraft?.title.orEmpty()
    val hideInitialTitleInEditor = remember(
        rawInitialTitle,
        isPrivacyEditor,
        showNoteDetailTitle,
        editorUnnamedNoteDateFormat,
        editorHiddenFilenamePatterns,
    ) {
        !isPrivacyEditor &&
            !showNoteDetailTitle &&
            shouldHideDateFilenameTitle(
                title = rawInitialTitle,
                dateFormat = editorUnnamedNoteDateFormat,
                hiddenFilenamePatterns = editorHiddenFilenamePatterns,
            )
    }
    val initialTitle = if (hideDraftTitleInEditor || hideInitialTitleInEditor) "" else rawInitialTitle
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
    val codeMirrorScrollController = remember { CodeMirrorWebViewScrollController() }
    val fastScrollSignal = remember { EditorFastScrollSignal() }
    val externalDraftIdentityKey = remember(externalDraft) { System.identityHashCode(externalDraft) }
    val externalDraftFolderKey = externalDraft?.folder?.takeIf { it.isNotBlank() } ?: initialLabel
    val editorDocumentKey = privacyDocumentKey ?: currentNote?.id ?: "external:$externalDraftIdentityKey:$externalDraftFolderKey"
    var lastValidEditorDisplayTitle by remember { mutableStateOf("") }
    val isEmptyExternalTitleState =
        editorDocumentKey.startsWith("external:0") &&
            currentNote?.file?.path == null &&
            currentNote?.title.isNullOrBlank() &&
            externalDraft?.title.isNullOrBlank() &&
            rawInitialTitle.isBlank() &&
            initialTitle.isBlank()
    val keepLastTitleForEmptyExternal = isEmptyExternalTitleState && lastValidEditorDisplayTitle.isNotBlank()
    val displayInitialTitle = if (keepLastTitleForEmptyExternal) lastValidEditorDisplayTitle else initialTitle
    LaunchedEffect(editorDocumentKey, initialTitle, isEmptyExternalTitleState) {
        if (!isEmptyExternalTitleState && initialTitle.isNotBlank()) {
            lastValidEditorDisplayTitle = initialTitle
        }
    }
    var noteTextStats by remember(editorDocumentKey) { mutableStateOf<NoteTextStats?>(null) }
    val fileInfoFallbackDate = remember(editorDocumentKey) { Date() }
    var effectivePrivacyNoteId by remember(privacyDocumentKey) { mutableStateOf(privacyNoteId ?: 0L) }
    var privacyEditorDirty by remember(privacyDocumentKey) { mutableStateOf(false) }
    val defaultEditOpenSelection = if (
        !isPrivacyEditor &&
        currentNote != null &&
        defaultOpenNoteMode == KardLeafCustomFeatures.OpenNoteMode.EDIT
    ) {
        TextRange(0, 0)
    } else {
        null
    }
    var temporaryEditorKernel by remember(editorDocumentKey) { mutableStateOf<PrefsManager.EditorKernel?>(null) }
    var temporaryEditorSnapshot by remember(editorDocumentKey) { mutableStateOf<KardLeafEditorSnapshot?>(null) }
    val editorKernel = temporaryEditorKernel ?: savedEditorKernel
    val editorSurfaceTitle = temporaryEditorSnapshot?.title ?: displayInitialTitle
    val editorSurfaceContent = temporaryEditorSnapshot?.content ?: initialContent
    val editorSurfaceSelection = temporaryEditorSnapshot?.selection ?: defaultEditOpenSelection
    val hasMarkdownImages = remember(editorSurfaceContent) { containsMarkdownImageReferences(editorSurfaceContent) }
    val isManualCodeMirrorKernel = editorKernel == PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW
    val allowsCodeMirrorForThisNote = isManualCodeMirrorKernel || !hasMarkdownImages
    val usesCodeMirrorLikeEditor =
        !isPrivacyEditor &&
            allowsCodeMirrorForThisNote &&
            isManualCodeMirrorKernel
    LaunchedEffect(editorKernel, isPrivacyEditor, hasMarkdownImages, editorSurfaceContent.length, temporaryEditorKernel) {
        KardLeafLog.d(
            "KardLeafCodeMirror",
            "screen editor kernel=$editorKernel temporary=$temporaryEditorKernel useCodeMirror=$usesCodeMirrorLikeEditor " +
                "autoSwitch=false hasImages=$hasMarkdownImages threshold=$autoCodeMirrorThresholdChars " +
                "contentLen=${editorSurfaceContent.length} privacy=$isPrivacyEditor",
        )
    }
    editorController.acceptInitialSnapshot(
        editorDocumentKey,
        temporaryEditorSnapshot?.title ?: initialTitle,
        editorSurfaceContent,
        editorSurfaceSelection,
    )
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    val isTemporaryDraft = currentNote == null && externalDraft?.isTemporary == true
    var folder by remember(currentNote, externalDraft, initialLabel, isPrivacyEditor) {
        mutableStateOf(
            if (isPrivacyEditor) {
                ""
            } else {
                currentNote?.folder
                    ?: externalDraft?.folder?.takeIf { it.isNotBlank() }
                    ?: if (externalDraft?.forceRootFolder == true) "" else null
                    ?: initialLabel
            },
        )
    }
    val noteFileInfoText = remember(currentNote?.createdAt?.time, initialContent.length, folder, fileInfoFallbackDate.time) {
        formatEditorFileInfoText(
            date = currentNote?.createdAt ?: fileInfoFallbackDate,
            charCount = initialContent.length,
            folder = folder,
        )
    }
    var renderedPreview by remember(editorDocumentKey) { mutableStateOf("") }
    var largePlainPreviewSnapshot by remember(editorDocumentKey) { mutableStateOf<KardLeafEditorSnapshot?>(null) }
    var previewRenderToken by remember(editorDocumentKey) { mutableStateOf(0) }
    var previewScrollRatio by remember(editorDocumentKey) { mutableStateOf(0f) }
    var pendingEditScrollRatio by remember(editorDocumentKey) { mutableStateOf<Float?>(null) }
    var pendingEditScrollOffset by remember(editorDocumentKey) { mutableStateOf<Int?>(null) }

    val isNewPrivacyNote = isPrivacyEditor && (privacyNoteId ?: 0L) <= 0L
    val codeMirrorEditAvailableForLargeNote =
        !isPrivacyEditor &&
            allowsCodeMirrorForThisNote &&
            (usesCodeMirrorLikeEditor || isManualCodeMirrorKernel)
    val blocksDirectEditForLargeNote = !codeMirrorEditAvailableForLargeNote && !isNewPrivacyNote && editorSurfaceContent.length > DIRECT_EDIT_MAX_CHARS
    val defersAutoEditForLargeNote =
        !codeMirrorEditAvailableForLargeNote &&
            editorKernel == PrefsManager.EditorKernel.AUTO &&
            !isNewPrivacyNote &&
            editorSurfaceContent.length > autoCodeMirrorThresholdChars
    val usesLargePlainTextPreview =
        !isNewPrivacyNote &&
            editorSurfaceContent.length > WEBVIEW_PREVIEW_MAX_CHARS
    val showsLargePlainTextPreview = usesLargePlainTextPreview || largePlainPreviewSnapshot != null
    val usesOpeningEditShell =
        (isOpeningNoteContent || isShowingPartialLargeNote) &&
            !usesCodeMirrorLikeEditor &&
            !isPrivacyEditor &&
            editorSurfaceContent.isNotEmpty() &&
            defaultOpenNoteMode == KardLeafCustomFeatures.OpenNoteMode.EDIT
    val userPerfContentLen = editorSurfaceContent.length
    val userPerfSizeTier = userPerfNoteSizeTier(userPerfContentLen)
    val isUserPerfLargeNote = !isNewPrivacyNote && userPerfContentLen >= USER_PERF_LARGE_NOTE_MIN_CHARS
    val isUserPerfTrackedNote = !isNewPrivacyNote && userPerfContentLen > 0
    val userPerfOpenStartMs = editorOpenStartRealtimeMs ?: SystemClock.elapsedRealtime()
    var userPerfScreenComposedLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    var userPerfContentReadyLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    var userPerfAreaFirstFrameLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    var userPerfFirstContentLaidOutLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    var userPerfRenderedLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    val largePlainTextPreviewListState = rememberLazyListState()
    var isEditing by remember(
        editorDocumentKey,
        defaultOpenNoteMode,
        blocksDirectEditForLargeNote,
        defersAutoEditForLargeNote,
        usesCodeMirrorLikeEditor,
        isOpeningNoteContent,
        isShowingPartialLargeNote,
        usesOpeningEditShell,
    ) {
        mutableStateOf(
            usesOpeningEditShell ||
                (!isOpeningNoteContent &&
                    !blocksDirectEditForLargeNote &&
                    !defersAutoEditForLargeNote &&
                    (isNewPrivacyNote ||
                        (!isPrivacyEditor && currentNote == null) ||
                        defaultOpenNoteMode == KardLeafCustomFeatures.OpenNoteMode.EDIT)),
        )
    }
    LaunchedEffect(
        effectiveEditorOpen,
        editorDocumentKey,
        isOpeningNoteContent,
        isEditing,
        usesOpeningEditShell,
        defaultOpenNoteMode,
        initialContent.length,
    ) {
        if (effectiveEditorOpen && isUserPerfTrackedNote) {
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorOpen composeDecision key=$editorDocumentKey contentLen=${initialContent.length} sizeTier=$userPerfSizeTier " +
                    "isOpening=$isOpeningNoteContent isEditing=$isEditing usesOpeningShell=$usesOpeningEditShell " +
                    "defaultOpenMode=$defaultOpenNoteMode blocksLarge=$blocksDirectEditForLargeNote defersLarge=$defersAutoEditForLargeNote " +
                    "codeMirror=$usesCodeMirrorLikeEditor currentNoteNull=${currentNote == null} externalDraftNull=${externalDraft == null}",
            )
        }
    }

    var openingPreviewRenderPending by remember(editorDocumentKey) { mutableStateOf(false) }
    var lastRenderedPreviewSignature by remember(editorDocumentKey) { mutableStateOf<Pair<Int, Int>?>(null) }
    val visiblePreviewContent = renderedPreview
    val visiblePreviewSignature = visiblePreviewContent.length to visiblePreviewContent.hashCode()
    val showOpeningContentProgress =
        isOpeningNoteContent ||
            (!isEditing &&
                openingPreviewRenderPending &&
                visiblePreviewContent.isNotEmpty() &&
                lastRenderedPreviewSignature != visiblePreviewSignature)

    fun userPerfModeName(): String = when {
        isEditing && usesCodeMirrorLikeEditor -> "codeMirror"
        isEditing && !blocksDirectEditForLargeNote -> "nativeEditor"
        showsLargePlainTextPreview -> "largePlainPreview"
        else -> "markdownPreview"
    }

    fun logUserPerfOpenStep(step: String, mode: String = userPerfModeName()) {
        if (!isUserPerfTrackedNote) return
        KardLeafLog.d(
            USER_PERF_TRACE_TAG,
            "editorOpen $step elapsed=${SystemClock.elapsedRealtime() - userPerfOpenStartMs}ms " +
                "engine=${if (usesCodeMirrorLikeEditor) "CODEMIRROR" else "NATIVE"} mode=$mode " +
                "contentLen=$userPerfContentLen sizeTier=$userPerfSizeTier " +
                "isLarge=$isUserPerfLargeNote isOpening=$isOpeningNoteContent partialLarge=$isShowingPartialLargeNote " +
                "largeBlocked=$blocksDirectEditForLargeNote plainLargePreview=$usesLargePlainTextPreview " +
                "path=${currentNote?.file?.path}",
        )
    }

    fun userPerfAreaFirstFrameModifier(mode: String): Modifier =
        if (!isUserPerfTrackedNote) {
            Modifier
        } else {
            Modifier.onGloballyPositioned {
                if (!userPerfAreaFirstFrameLogged) {
                    userPerfAreaFirstFrameLogged = true
                    logUserPerfOpenStep("visualAreaFirstFrame", mode)
                }
            }
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
    var editingDrawingReference by remember(editorDocumentKey) { mutableStateOf<String?>(null) }
    var editingDrawingSource by remember(editorDocumentKey) { mutableStateOf<String?>(null) }
    var previewImageReferences by remember(editorDocumentKey) { mutableStateOf(emptyList<String?>()) }
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
    var noteSearchRequestToken by remember { mutableStateOf(0) }
    var noteSearchFocused by remember { mutableStateOf(false) }
    var noteReplaceFocused by remember { mutableStateOf(false) }
    var suppressNextSearchKeyboardRequest by remember { mutableStateOf(false) }
    var largePlainSearchJumpDebugToken by remember { mutableStateOf(0) }
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
    val noteSidePanelDragStartThresholdPx = with(density) { 40.dp.toPx() }
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
    var isKeyboardVisible by remember { mutableStateOf(false) }
    var outlineHeadings by remember { mutableStateOf<List<MarkdownHeading>>(emptyList()) }
    var showMindMap by remember(editorDocumentKey) { mutableStateOf(false) }
    var mindMapHeadings by remember(editorDocumentKey) { mutableStateOf<List<MarkdownHeading>>(emptyList()) }
    var mindMapUnavailableTitle by remember(editorDocumentKey) { mutableStateOf<String?>(null) }
    var mindMapUnavailableMessage by remember(editorDocumentKey) { mutableStateOf<String?>(null) }
    var shouldShowBottomToolbar by remember { mutableStateOf(false) }

    LaunchedEffect(effectiveEditorOpen, editorDocumentKey, currentNote?.file?.path, defaultOpenNoteMode) {
        if (
            effectiveEditorOpen &&
            !isPrivacyEditor &&
            currentNote != null &&
            defaultOpenNoteMode == KardLeafCustomFeatures.OpenNoteMode.EDIT
        ) {
            requestKeyboardOnEdit = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    // Keep the editor chrome stable while scrolling. Hiding/showing bars during
    // downward drags changes Scaffold padding and makes long notes feel jerky.
    var showBars by remember { mutableStateOf(true) }
    val codeMirrorOuterTraceCounts = remember(editorDocumentKey) { mutableMapOf<String, Int>() }

    fun nextCodeMirrorOuterTraceCount(layer: String): Int {
        val next = (codeMirrorOuterTraceCounts[layer] ?: 0) + 1
        codeMirrorOuterTraceCounts[layer] = next
        return next
    }

    fun logCodeMirrorOuterLayout(
        layer: String,
        coordinates: LayoutCoordinates,
        extra: String = "",
    ) {
        if (!CODEMIRROR_IME_OUTER_TRACE_ENABLED || !effectiveEditorOpen || !usesCodeMirrorLikeEditor) return
        val imeBottomPx = imeInsets.getBottom(density)
        val isImeVisible = imeBottomPx > 0
        val size = coordinates.size
        val position = coordinates.positionInWindow()
        val count = nextCodeMirrorOuterTraceCount(layer)
        KardLeafLog.d(
            CODEMIRROR_IME_TRACE_TAG,
            "outer layout layer=$layer count=$count size=${size.width}x${size.height} " +
                "pos=${position.x.roundToInt()},${position.y.roundToInt()} imeBottom=$imeBottomPx " +
                "imeVisible=$isImeVisible editing=$isEditing showBars=$showBars " +
                "bottomToolbar=$shouldShowBottomToolbar expanded=$isBottomToolbarExpanded " +
                "toolbarDrag=$toolbarDragFraction contentLen=${initialContent.length} " +
                "sizeTier=$userPerfSizeTier key=$editorDocumentKey $extra",
        )
    }

    @Composable
    fun KeyboardInsetsTracker() {
        val trackerDensity = LocalDensity.current
        val imeBottomPx = imeInsets.getBottom(trackerDensity)
        val imeVisible = imeBottomPx > 0
        LaunchedEffect(imeVisible) {
            isKeyboardVisible = imeVisible
        }
        SideEffect {
            if (CODEMIRROR_IME_OUTER_TRACE_ENABLED && effectiveEditorOpen && usesCodeMirrorLikeEditor) {
                val count = nextCodeMirrorOuterTraceCount("composeSideEffect")
                if (imeVisible || count <= 5 || count % 10 == 0) {
                    KardLeafLog.d(
                        CODEMIRROR_IME_TRACE_TAG,
                        "outer composeSideEffect count=$count imeBottom=$imeBottomPx imeVisible=$imeVisible " +
                            "editing=$isEditing showBars=$showBars bottomToolbar=$shouldShowBottomToolbar " +
                            "expanded=$isBottomToolbarExpanded toolbarDrag=$toolbarDragFraction " +
                            "contentLen=${initialContent.length} sizeTier=$userPerfSizeTier key=$editorDocumentKey",
                    )
                }
            }
        }
        LaunchedEffect(
            imeBottomPx,
            imeVisible,
            isEditing,
            showBars,
            shouldShowBottomToolbar,
            isBottomToolbarExpanded,
            toolbarDragFraction,
            usesCodeMirrorLikeEditor,
            effectiveEditorOpen,
        ) {
            if (CODEMIRROR_IME_OUTER_TRACE_ENABLED && effectiveEditorOpen && usesCodeMirrorLikeEditor) {
                KardLeafLog.d(
                    CODEMIRROR_IME_TRACE_TAG,
                    "outer state imeChanged imeBottom=$imeBottomPx imeVisible=$imeVisible " +
                        "editing=$isEditing showBars=$showBars bottomToolbar=$shouldShowBottomToolbar " +
                        "expanded=$isBottomToolbarExpanded toolbarDrag=$toolbarDragFraction " +
                        "contentLen=${initialContent.length} sizeTier=$userPerfSizeTier key=$editorDocumentKey",
                )
            }
        }
    }

    KeyboardInsetsTracker()

    LaunchedEffect(
        editorDocumentKey,
        effectiveEditorOpen,
        currentNote?.file?.path,
        currentNote?.content?.length,
        currentNote?.contentPreview?.length,
        externalDraft?.content?.length,
    ) {
        KardLeafLog.d(
            TITLE_TRACE_TAG,
            "title source key=$editorDocumentKey open=$effectiveEditorOpen editing=$isEditing " +
                "showDetailTitle=$showNoteDetailTitle isPrivacy=$isPrivacyEditor " +
                "currentPath=${currentNote?.file?.path} currentTitle=${currentNote?.title} currentTitleLen=${currentNote?.title?.length ?: -1} " +
                "draftTitle=${externalDraft?.title} draftTitleLen=${externalDraft?.title?.length ?: -1} " +
                "rawInitialTitle=$rawInitialTitle rawInitialTitleLen=${rawInitialTitle.length} initialTitle=$initialTitle initialTitleLen=${initialTitle.length} " +
                "displayInitialTitle=$displayInitialTitle displayInitialTitleLen=${displayInitialTitle.length} " +
                "keepLastTitleForEmptyExternal=$keepLastTitleForEmptyExternal lastValidTitleLen=${lastValidEditorDisplayTitle.length} " +
                "hideDraftTitle=$hideDraftTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
                "folder=$folder currentFolder=${currentNote?.folder} draftFolder=${externalDraft?.folder} initialLabel=$initialLabel " +
                "hiddenRules=${editorHiddenFilenamePatterns.size} dateFormat=$editorUnnamedNoteDateFormat",
        )
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "screen state key=$editorDocumentKey open=$effectiveEditorOpen editing=$isEditing " +
                "notePath=${currentNote?.file?.path} noteTitleLen=${currentNote?.title?.length ?: -1} " +
                "noteContentLen=${currentNote?.content?.length ?: -1} notePreviewLen=${currentNote?.contentPreview?.length ?: -1} " +
                "draftContentLen=${externalDraft?.content?.length ?: -1} initialTitleLen=${initialTitle.length} " +
                "displayInitialTitleLen=${displayInitialTitle.length} initialContentLen=${initialContent.length}",
        )
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "screen state key=$editorDocumentKey open=$effectiveEditorOpen editing=$isEditing isOpening=$isOpeningNoteContent " +
                "largeBlocked=$blocksDirectEditForLargeNote notePath=${currentNote?.file?.path} " +
                "noteContentLen=${currentNote?.content?.length ?: -1} notePreviewLen=${currentNote?.contentPreview?.length ?: -1} " +
                "initialContentLen=${initialContent.length} renderedPreviewLen=${renderedPreview.length}",
        )
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "editorScreen state key=$editorDocumentKey open=$effectiveEditorOpen editing=$isEditing isOpening=$isOpeningNoteContent " +
                "path=${currentNote?.file?.path} folder=$folder contentLen=${currentNote?.content?.length ?: -1} " +
                "initialContentLen=${initialContent.length} renderedPreviewLen=${renderedPreview.length} " +
                "codeMirror=$usesCodeMirrorLikeEditor largeBlocked=$blocksDirectEditForLargeNote",
        )
        if (effectiveEditorOpen && currentNote != null && currentNote!!.content.isEmpty() && currentNote!!.contentPreview.isNotEmpty()) {
            KardLeafLog.w(
                EDITOR_TRACE_TAG,
                "screen suspicious blank note path=${currentNote!!.file.path} previewLen=${currentNote!!.contentPreview.length}",
            )
        }
    }

    LaunchedEffect(isEditing, isKeyboardVisible, editorBottomToolbarAlwaysVisible) {
        if (!isEditing) {
            shouldShowBottomToolbar = false
        } else if (editorBottomToolbarAlwaysVisible || isKeyboardVisible) {
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
            pointerInput(enabled, noteSidePanelWidthPx, noteSidePanelDragStartThresholdPx, protectEditorTouch, isEditing, isKeyboardVisible) {
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
                                KardLeafLog.d(
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
                        val horizontalDragStartThreshold = maxOf(touchSlop * 2.5f, noteSidePanelDragStartThresholdPx)
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
                                    KardLeafLog.d(EDITOR_GESTURE_TAG, "note side panel drag end offset=$noteSidePanelTargetPx")
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

                            if (!lockedHorizontal && !lockedVertical && (absDx > horizontalDragStartThreshold || absDy > touchSlop)) {
                                when {
                                    absDy >= absDx * 1.2f -> {
                                        lockedVertical = true
                                        KardLeafLog.d(
                                            EDITOR_GESTURE_TAG,
                                            "note side panel ignore vertical dx=$totalDx dy=$totalDy editing=$isEditing keyboard=$isKeyboardVisible",
                                        )
                                    }
                                    absDx > horizontalDragStartThreshold && absDx > absDy * 1.5f -> {
                                        lockedHorizontal = true
                                        startedDrag = true
                                        startNoteSidePanelDrag()
                                        if (dragNoteSidePanelBy(totalDx)) {
                                            change.consume()
                                        }
                                        KardLeafLog.d(
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
    val noteSidePanelContentDragModifier = Modifier
    val noteSidePanelActiveDragModifier = Modifier.noteSidePanelDrag(noteSidePanelGestureEnabled && noteSidePanelHasOffset)
    val noteSidePanelEdgeDragModifier =
        Modifier.noteSidePanelDrag(noteSidePanelGestureEnabled && (!noteSidePanelHasOffset || isNoteSidePanelDragging))

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {}
    }

    LaunchedEffect(editorDocumentKey) {
        noteSidePanelsReady = false
        delay(500L)
        noteSidePanelsReady = true
    }

    LaunchedEffect(effectiveEditorOpen, editorDocumentKey) {
        KardLeafLog.d(
            EDITOR_TOP_BAR_TRACE_TAG,
            "reload prefs start key=$editorDocumentKey open=$effectiveEditorOpen " +
                "oldOrder=${editorTopToolbarOrder.toEditorTopBarLogText()} " +
                "oldMore=${editorTopToolbarMoreItems.toEditorTopBarLogText()} " +
                "oldHidden=${editorTopToolbarHiddenItems.toEditorTopBarLogText()} " +
                "oldPanelsEnabled=$noteSidePanelsEnabled oldMode=$noteSidePanelOpenMode",
        )
        editorTopToolbarOrder = notePrefsManager.getEditorTopToolbarItemOrder()
        editorTopToolbarMoreItems = notePrefsManager.getEditorTopToolbarMoreItems()
        editorTopToolbarHiddenItems = notePrefsManager.getEditorTopToolbarHiddenItems()
        noteSidePanelsEnabled = notePrefsManager.isNoteSidePanelsEnabled()
        noteSidePanelOpenMode = notePrefsManager.getNoteSidePanelOpenMode()
        KardLeafLog.d(
            EDITOR_TOP_BAR_TRACE_TAG,
            "reload prefs done key=$editorDocumentKey open=$effectiveEditorOpen " +
                "newOrder=${editorTopToolbarOrder.toEditorTopBarLogText()} " +
                "newMore=${editorTopToolbarMoreItems.toEditorTopBarLogText()} " +
                "newHidden=${editorTopToolbarHiddenItems.toEditorTopBarLogText()} " +
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
        editorTopToolbarHiddenItems,
    ) {
        val normalizedOrder = editorTopToolbarOrder.distinct().toMutableList().also { order ->
            PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.forEach { if (it !in order) order.add(it) }
        }
        val filteredOrder = normalizedOrder.filter { item ->
            item !in editorTopToolbarHiddenItems &&
                (noteSidePanelToolbarEnabled || (item != PrefsManager.EditorTopToolbarItemId.OUTLINE && item != PrefsManager.EditorTopToolbarItemId.REMARKS))
        }
        val safeMoreItems = editorTopToolbarMoreItems
            .filter { it in filteredOrder && it != PrefsManager.EditorTopToolbarItemId.MORE }
            .toSet()
        val moreDisplayItems = filteredOrder.filter { it in safeMoreItems }
        val topDisplayItems = filteredOrder.filter { it !in safeMoreItems }
        KardLeafLog.d(
            EDITOR_TOP_BAR_TRACE_TAG,
            "state key=$editorDocumentKey path=${currentNote?.file?.path} open=$effectiveEditorOpen editing=$isEditing " +
                "privacy=$isPrivacyEditor closing=$isClosingEditor panelsEnabled=$noteSidePanelsEnabled " +
                "panelsReady=$noteSidePanelsReady panelsActive=$noteSidePanelsActive mode=$noteSidePanelOpenMode " +
                "toolbarEnabled=$noteSidePanelToolbarEnabled rawOrder=${editorTopToolbarOrder.toEditorTopBarLogText()} " +
                "rawMore=${editorTopToolbarMoreItems.toEditorTopBarLogText()} rawHidden=${editorTopToolbarHiddenItems.toEditorTopBarLogText()} " +
                "filteredOrder=${filteredOrder.toEditorTopBarLogText()} " +
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

    /** Builds a [Note] from the current editor snapshot. */
    fun buildCurrentNote(snapshot: KardLeafEditorSnapshot = editorController.getSnapshot()): Note {
        val snapshotTitleForSave = when {
            hideDraftTitleInEditor && snapshot.title.isBlank() -> currentNote?.title.orEmpty()
            hideInitialTitleInEditor && snapshot.title.isBlank() -> rawInitialTitle
            else -> snapshot.title
        }
        val parentPath = folder
        val fileName = currentNote?.file?.name?.takeIf { it.isNotEmpty() } ?: "new_note_placeholder"
        val existingAutoTitles = allNotes
            .asSequence()
            .filter { note -> note.folder == parentPath && note.file.path != currentNote?.file?.path }
            .map { note -> note.title.ifBlank { note.file.nameWithoutExtension } }
            .filter { title -> title.isNotBlank() }
            .toSet()
        val autoTitle =
            if (snapshotTitleForSave.isNotEmpty()) {
                snapshotTitleForSave
            } else {
                KardLeafCustomFeatures.formatUnnamedNoteTitle(
                    context = context,
                    existingTitles = existingAutoTitles,
                )
            }
        KardLeafLog.d(
            SAVE_PATH_TRACE_TAG,
            "buildCurrentNote key=$editorDocumentKey currentPath=${currentNote?.file?.path} " +
                "fileName=$fileName folder=$parentPath rawInitialTitleLen=${rawInitialTitle.length} " +
                "snapshotTitleLen=${snapshot.title.length} snapshotContentLen=${snapshot.content.length} " +
                "hideDraftTitle=$hideDraftTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
                "snapshotTitleForSaveLen=${snapshotTitleForSave.length} autoTitle=$autoTitle generated=${snapshotTitleForSave.isEmpty()}",
        )
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

    fun saveNote(
        saveHistory: Boolean = false,
        showToast: Boolean = false,
        onComplete: (() -> Unit)? = null,
    ) {
        fun finishSave() {
            onComplete?.invoke()
        }
        if ((isOpeningNoteContent || isShowingPartialLargeNote) && !isPrivacyEditor && !usesCodeMirrorLikeEditor) {
            KardLeafLog.d(EDITOR_TRACE_TAG, "saveNote skipped while note is in lightweight open state key=$editorDocumentKey")
            finishSave()
            return
        }
        if (isTemporaryDraft) {
            KardLeafLog.d(EDITOR_TRACE_TAG, "saveNote skipped temporary draft key=$editorDocumentKey")
            finishSave()
            return
        }
        val startMs = SystemClock.elapsedRealtime()

        fun saveSnapshot(snapshot: KardLeafEditorSnapshot, source: String) {
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "saveNote snapshot key=$editorDocumentKey source=$source saveHistory=$saveHistory titleLen=${snapshot.title.length} " +
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
                        if (showToast) Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        KardLeafLog.d(
                            EDITOR_TRACE_TAG,
                            "savePrivacyNote dispatched key=$editorDocumentKey id=$effectivePrivacyNoteId elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                    } else {
                        if (showToast) Toast.makeText(context, "没有需要保存的修改", Toast.LENGTH_SHORT).show()
                        KardLeafLog.d(EDITOR_TRACE_TAG, "savePrivacyNote skipped unchanged key=$editorDocumentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                    }
                    finishSave()
                    return
                }

                val note = buildCurrentNote(snapshot)
                val savedTitle = when {
                    hideDraftTitleInEditor && snapshot.title.isBlank() -> currentNote?.title.orEmpty()
                    hideInitialTitleInEditor && snapshot.title.isBlank() -> rawInitialTitle
                    else -> snapshot.title
                }
                val isChanged =
                    if (currentNote == null) {
                        savedTitle.isNotEmpty() || snapshot.content.isNotEmpty()
                    } else {
                        savedTitle != currentNote?.title ||
                            snapshot.content != currentNote?.content ||
                            currentNote?.isFavorite != note.isFavorite ||
                            folder != (currentNote?.folder ?: "")
                    }
                KardLeafLog.d(
                    SAVE_PATH_TRACE_TAG,
                    "saveNote decision key=$editorDocumentKey source=$source saveHistory=$saveHistory " +
                        "currentPath=${currentNote?.file?.path} currentTitle=${currentNote?.title} " +
                        "notePath=${note.file.path} noteTitle=${note.title} savedTitle=$savedTitle " +
                        "snapshotTitleLen=${snapshot.title.length} snapshotContentLen=${snapshot.content.length} " +
                        "currentContentLen=${currentNote?.content?.length ?: -1} folder=$folder " +
                        "currentFolder=${currentNote?.folder} isChanged=$isChanged editorDirty=${viewModel.editorDirty.value}",
                )
                if (isChanged) {
                    viewModel.saveNote(note, currentNote?.file, saveHistory = saveHistory)
                    if (showToast) Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                    KardLeafLog.d(
                        EDITOR_TRACE_TAG,
                        "saveNote dispatched key=$editorDocumentKey source=$source changed=true elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                    )
                } else {
                    if (showToast) Toast.makeText(context, "没有需要保存的修改", Toast.LENGTH_SHORT).show()
                    KardLeafLog.d(EDITOR_TRACE_TAG, "saveNote skipped unchanged key=$editorDocumentKey source=$source elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                }
                finishSave()
            } else {
                if (showToast) Toast.makeText(context, "当前笔记为空，未保存", Toast.LENGTH_SHORT).show()
                KardLeafLog.w(EDITOR_TRACE_TAG, "saveNote skipped empty snapshot key=$editorDocumentKey source=$source elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                finishSave()
            }
        }

        if (usesCodeMirrorLikeEditor && editorController.requestExternalSnapshot { snapshot ->
                saveSnapshot(snapshot, "codemirror")
            }
        ) {
            KardLeafLog.d(EDITOR_TRACE_TAG, "saveNote requested CodeMirror snapshot key=$editorDocumentKey saveHistory=$saveHistory")
            return
        }

        saveSnapshot(editorController.getSnapshot(), "cached")
    }

    fun markEditorDirty() {
        if (isPrivacyEditor) {
            privacyEditorDirty = true
        } else if (!viewModel.editorDirty.value) {
            viewModel.setEditorDirty(true)
        }
    }

    fun switchEditorKernelTemporarily(targetKernel: PrefsManager.EditorKernel) {
        showMoreMenu = false
        showLabelMenu = false
        showHeadingMenu = false
        showMathMenu = false
        fun applySnapshot(snapshot: KardLeafEditorSnapshot) {
            editorController.updateExternalTitle(snapshot.title)
            editorController.updateExternalContentSnapshot(snapshot.content, snapshot.selection)
            temporaryEditorSnapshot = snapshot
            temporaryEditorKernel = targetKernel
            KardLeafLog.d(
                "KardLeafCodeMirror",
                "temporary editor kernel switched target=$targetKernel key=$editorDocumentKey " +
                    "titleLen=${snapshot.title.length} contentLen=${snapshot.content.length} selection=${snapshot.selection}",
            )
        }
        if (usesCodeMirrorLikeEditor && editorController.requestExternalSnapshot { snapshot ->
                applySnapshot(snapshot)
            }
        ) {
            KardLeafLog.d("KardLeafCodeMirror", "temporary editor kernel switch requested CodeMirror snapshot target=$targetKernel key=$editorDocumentKey")
        } else {
            applySnapshot(editorController.getSnapshot())
        }
    }

    fun shouldSaveEditorOnLeave(): Boolean =
        if (isPrivacyEditor) {
            privacyEditorDirty || isNewPrivacyNote
        } else {
            currentNote == null || viewModel.editorDirty.value
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

    fun runCodeMirrorCommand(
        command: String,
        vararg args: Any,
    ): Boolean {
        if (!usesCodeMirrorLikeEditor) return false
        val handled = editorController.executeExternalCommand(command, *args)
        if (handled) markEditorDirty()
        return handled
    }

    fun insertAtCursorOrCommand(
        prefix: String,
        suffix: String = "",
        command: String? = null,
        vararg args: Any,
    ) {
        if (command != null && runCodeMirrorCommand(command, *args)) return
        insertAtCursor(prefix, suffix)
    }

    fun applyHeadingAtCursor(level: Int) {
        if (runCodeMirrorCommand("toggleHeading", level)) return
        insertAtCursor("#".repeat(level.coerceIn(1, 6)) + " ")
    }

    fun insertImageMarkdown(
        markdown: String,
        lockedSelection: TextRange? = null,
    ) {
        val snapshot = editorController.getSnapshot()
        val content = snapshot.content
        val selection = lockedSelection ?: snapshot.selection
        val start = minOf(selection.start, selection.end).coerceIn(0, content.length)
        val end = maxOf(selection.start, selection.end).coerceIn(0, content.length)
        val fallbackToContentLength = selection.start > content.length || selection.end > content.length
        val needsLeadingBreak = start > 0 && content.getOrNull(start - 1) != '\n'
        val insertion = buildString {
            if (needsLeadingBreak) append('\n')
            append(markdown.trim())
            append("\n\n")
        }
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[insert-image] compose before contentLen=${content.length} currentSelection=${snapshot.selection.start}..${snapshot.selection.end} " +
                "lockedSelection=${lockedSelection?.start ?: -1}..${lockedSelection?.end ?: -1} " +
                "usedSelection=$start..$end fallbackToContentLength=$fallbackToContentLength " +
                "insertPos=$start replace=$start..$end markdownLen=${markdown.length} insertionLen=${insertion.length} " +
                "needsLeadingBreak=$needsLeadingBreak expectedCursor=${start + insertion.length} " +
                "insertion=${insertion.replace("\n", "\\n")}",
        )
        if (lockedSelection != null) {
            editorController.setSelection(start, end)
        }
        editorController.replaceSelection(insertion)
        val afterSelection = editorController.getSelection()
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[insert-image] compose after cursor=${afterSelection.start}..${afterSelection.end} expectedCursor=${start + insertion.length}",
        )
        markEditorDirty()
    }

    fun launchImagePicker() {
        val picker = onPickImage
        if (picker == null) {
            Toast.makeText(context, "当前页面暂不支持选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        val lockedSelection = editorController.getSelection()
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[insert-image] click selection=${lockedSelection.start}..${lockedSelection.end}",
        )
        KardLeafLog.d(
            CODEMIRROR_DEBUG_TRACE_TAG,
            "[insert-image] before picker selection=${lockedSelection.start}..${lockedSelection.end}",
        )
        picker { uri ->
            val pickedSelection = editorController.getSelection()
            KardLeafLog.d(
                CODEMIRROR_DEBUG_TRACE_TAG,
                "[insert-image] picker callback received lockedSelection=${lockedSelection.start}..${lockedSelection.end} " +
                    "currentSelection=${pickedSelection.start}..${pickedSelection.end} scheme=${uri.scheme.orEmpty()}",
            )
            coroutineScope.launch {
                val importStartMs = SystemClock.elapsedRealtime()
                KardLeafLog.d(
                    CODEMIRROR_DEBUG_TRACE_TAG,
                    "[insert-image] import start currentFolder=$folder",
                )
                val importBlockMessage = viewModel.getImageImportTooLargeMessage(uri)
                if (importBlockMessage != null) {
                    Toast.makeText(context, importBlockMessage, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val markdown = viewModel.importImage(uri, folder)
                val importElapsedMs = SystemClock.elapsedRealtime() - importStartMs
                val currentSelection = editorController.getSelection()
                KardLeafLog.d(
                    CODEMIRROR_DEBUG_TRACE_TAG,
                    "[insert-image] import done elapsed=${importElapsedMs}ms lockedSelection=${lockedSelection.start}..${lockedSelection.end} " +
                        "currentSelection=${currentSelection.start}..${currentSelection.end} markdownLen=${markdown.length}",
                )
                if (markdown.isNotBlank()) {
                    insertImageMarkdown(markdown, lockedSelection)
                }
            }
        }
    }

    fun openDrawingPad() {
        editingDrawingReference = null
        editingDrawingSource = null
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

    fun openDrawingPadForReference(reference: String) {
        coroutineScope.launch {
            val source = viewModel.loadDrawingSource(folder, reference)
            if (source.isNullOrBlank()) {
                if (reference.substringAfterLast("/").startsWith("drawing_")) {
                    Toast.makeText(context, "这张绘图没有可编辑数据", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            editingDrawingReference = reference
            editingDrawingSource = source
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
    }

    fun renderPreviewSnapshot(snapshot: KardLeafEditorSnapshot) {
        val contentLength = maxOf(initialContent.length, snapshot.content.length)
        if (!isNewPrivacyNote && contentLength > WEBVIEW_PREVIEW_MAX_CHARS) {
            val token = previewRenderToken + 1
            previewRenderToken = token
            renderedPreview = ""
            largePlainPreviewSnapshot = snapshot
            previewImageReferences = emptyList()
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "screen preview use large plain text key=$editorDocumentKey titleLen=${snapshot.title.length} " +
                    "contentLen=${snapshot.content.length} initialContentLen=${initialContent.length} " +
                    "codeMirror=$usesCodeMirrorLikeEditor skipMarkdownRender=true skipImageScan=true threshold=$WEBVIEW_PREVIEW_MAX_CHARS",
            )
            return
        }
        largePlainPreviewSnapshot = null
        val startMs = SystemClock.elapsedRealtime()
        val markdown = if (snapshot.title.isBlank()) snapshot.content else "# ${snapshot.title}\n\n${snapshot.content}"
        previewImageReferences = extractMarkdownImageReferences(markdown)
        val token = previewRenderToken + 1
        previewRenderToken = token
        renderedPreview = markdown
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "preview render start token=$token key=$editorDocumentKey titleLen=${snapshot.title.length} contentLen=${snapshot.content.length}",
        )
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "previewRender start token=$token key=$editorDocumentKey folder=$folder titleLen=${snapshot.title.length} " +
                "contentLen=${snapshot.content.length} markdownLen=${markdown.length} images=${previewImageReferences.size}",
        )
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "screen preview render start token=$token key=$editorDocumentKey markdownLen=${markdown.length} " +
                "titleLen=${snapshot.title.length} contentLen=${snapshot.content.length}",
        )
        coroutineScope.launch {
            val preparedMarkdown = viewModel.preparePreviewMarkdown(markdown, folder)
            if (previewRenderToken == token) {
                renderedPreview = preparedMarkdown
                KardLeafLog.d(
                    EDITOR_TRACE_TAG,
                    "preview render done token=$token key=$editorDocumentKey len=${preparedMarkdown.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
                KardLeafLog.d(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "screen preview render done token=$token key=$editorDocumentKey len=${preparedMarkdown.length} " +
                        "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "previewRender done token=$token key=$editorDocumentKey folder=$folder len=${preparedMarkdown.length} " +
                        "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            } else {
                KardLeafLog.d(EDITOR_TRACE_TAG, "preview render ignored stale token=$token latest=$previewRenderToken key=$editorDocumentKey")
                KardLeafLog.w(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "screen preview render ignored stale token=$token latest=$previewRenderToken key=$editorDocumentKey " +
                        "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            }
        }
    }

    fun saveDrawingImage(bitmap: android.graphics.Bitmap, drawingSource: String) {
        coroutineScope.launch {
            val editingReference = editingDrawingReference
            if (editingReference != null) {
                val saved = viewModel.updateDrawingImage(bitmap, drawingSource, folder, editingReference)
                if (saved) {
                    closeEditorWhenDashboardDrawingDismissed = false
                    editingDrawingReference = null
                    editingDrawingSource = null
                    showDrawingPad = false
                    editorController.refreshInlineImagePreviews()
                    renderPreviewSnapshot(editorController.getSnapshot())
                } else {
                    Toast.makeText(context, "画图保存失败", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val markdown = viewModel.importDrawingImage(bitmap, drawingSource, folder)
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
        KardLeafLog.d(EDITOR_TRACE_TAG, "noteSearch cursor hidden reason=$reason")
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
        noteSearchRequestToken++
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
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "noteSearch queryLen=${query.length} regex=$noteSearchUseRegex matchCase=$noteSearchMatchCase " +
                "textLen=${text.length} current=${summary.currentStart}..${summary.currentEnd} " +
                "currentText=${noteSearchSnippetForLog(text, summary.currentStart, summary.currentEnd)} " +
                "ordinal=${summary.currentOrdinal}/${summary.count} error=${summary.errorMessage} " +
                "highlights=$highlightCount attached=${editorController.editorView != null} editing=$isEditing",
        )
        return summary
    }

    fun scrollLargePlainPreviewToSearchOffset(offset: Int, textLength: Int) {
        if (!showsLargePlainTextPreview || offset < 0 || textLength <= 0) {
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "largePreviewJump skip show=$showsLargePlainTextPreview offset=$offset textLen=$textLength " +
                    "first=${largePlainTextPreviewListState.firstVisibleItemIndex} scrollOffset=${largePlainTextPreviewListState.firstVisibleItemScrollOffset}",
            )
            return
        }
        val chunkCount = largePlainTextPreviewChunkCount(textLength)
        if (chunkCount <= 0) {
            KardLeafLog.d(SEARCH_TRACE_TAG, "largePreviewJump skip emptyChunks offset=$offset textLen=$textLength")
            return
        }
        val chunkIndex = ((offset / LARGE_TEXT_PREVIEW_CHUNK_CHARS) + 1).coerceIn(1, chunkCount)
        largePlainSearchJumpDebugToken += 1
        val token = largePlainSearchJumpDebugToken
        KardLeafLog.d(
            SEARCH_TRACE_TAG,
            "largePreviewJump start token=$token offset=$offset chunkIndex=$chunkIndex chunkCount=$chunkCount " +
                "textLen=$textLength firstBefore=${largePlainTextPreviewListState.firstVisibleItemIndex} " +
                "offsetBefore=${largePlainTextPreviewListState.firstVisibleItemScrollOffset} method=scrollToItem",
        )
        coroutineScope.launch {
            val jumpStartMs = SystemClock.elapsedRealtime()
            try {
                largePlainTextPreviewListState.scrollToItem(chunkIndex)
                KardLeafLog.d(
                    SEARCH_TRACE_TAG,
                    "largePreviewJump complete token=$token elapsed=${SystemClock.elapsedRealtime() - jumpStartMs}ms " +
                        "target=$chunkIndex firstAfter=${largePlainTextPreviewListState.firstVisibleItemIndex} " +
                        "offsetAfter=${largePlainTextPreviewListState.firstVisibleItemScrollOffset}",
                )
            } catch (e: Exception) {
                KardLeafLog.w(
                    SEARCH_TRACE_TAG,
                    "largePreviewJump failed token=$token elapsed=${SystemClock.elapsedRealtime() - jumpStartMs}ms " +
                        "target=$chunkIndex firstNow=${largePlainTextPreviewListState.firstVisibleItemIndex}",
                    e,
                )
            }
        }
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "noteSearch largePlainPreview instantJump offset=$offset chunkIndex=$chunkIndex chunkCount=$chunkCount textLen=$textLength",
        )
    }

    fun selectSearchMatch(
        index: Int,
        query: String,
        searchText: String = editorController.getText(),
        source: String = "cached",
    ) {
        if (query.isBlank()) return
        val text = searchText
        KardLeafLog.d(
            SEARCH_TRACE_TAG,
            "selectSearchMatch enter index=$index queryLen=${query.length} textLen=${text.length} source=$source " +
                "editing=$isEditing largePlain=$showsLargePlainTextPreview current=${noteSearchCurrentStart}..${noteSearchCurrentEnd} " +
                "count=$noteSearchMatchCount first=${largePlainTextPreviewListState.firstVisibleItemIndex}",
        )
        if (index < 0) {
            updateSearchState(query, -1, text)
            return
        }
        val summary = updateSearchState(query, index, text)
        KardLeafLog.d(
            SEARCH_TRACE_TAG,
            "selectSearchMatch summary start=${summary.currentStart} end=${summary.currentEnd} " +
                "selectedText=${noteSearchSnippetForLog(text, summary.currentStart, summary.currentEnd)} " +
                "ordinal=${summary.currentOrdinal}/${summary.count} error=${summary.errorMessage} " +
                "editing=$isEditing largePlain=$showsLargePlainTextPreview source=$source",
        )
        if (summary.currentStart < 0 || summary.currentEnd <= summary.currentStart) return
        if (!isEditing && showsLargePlainTextPreview) {
            scrollLargePlainPreviewToSearchOffset(summary.currentStart, text.length)
            return
        }
        if (!isEditing) {
            previewController.scrollToSearchOrdinal(summary.currentOrdinal)
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "selectSearchMatch previewJump ordinal=${summary.currentOrdinal}/${summary.count} start=${summary.currentStart}",
            )
            return
        }
        if (usesCodeMirrorLikeEditor) {
            editorController.updateExternalSelection(summary.currentStart, summary.currentEnd)
            editorController.executeExternalCommand("selectRange", summary.currentStart, summary.currentEnd)
            coroutineScope.launch {
                withFrameNanos { }
                delay(60)
                editorController.executeExternalCommand("selectRange", summary.currentStart, summary.currentEnd)
                runCatching { searchFocusRequester.requestFocus() }
            }
            return
        }
        editorController.setSelection(summary.currentStart, summary.currentEnd)
        coroutineScope.launch {
            withFrameNanos { }
            delay(60)
            editorController.setSelection(summary.currentStart, summary.currentEnd)
            editorController.scrollToOffset(summary.currentStart)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    fun runWithSearchText(
        reason: String,
        query: String,
        block: (String, String) -> Unit,
    ) {
        if (isEditing && usesCodeMirrorLikeEditor) {
            noteSearchRequestToken += 1
            val token = noteSearchRequestToken
            val cachedLen = editorController.getText().length
            val requested = editorController.requestExternalSnapshot { snapshot ->
                if (token != noteSearchRequestToken || query != noteSearchQuery) {
                    KardLeafLog.d(
                        SEARCH_TRACE_TAG,
                        "searchSnapshot skip stale reason=$reason token=$token currentToken=$noteSearchRequestToken " +
                            "queryLen=${query.length} currentQueryLen=${noteSearchQuery.length} snapshotLen=${snapshot.content.length}",
                    )
                    return@requestExternalSnapshot
                }
                KardLeafLog.d(
                    SEARCH_TRACE_TAG,
                    "searchSnapshot ready reason=$reason token=$token cachedLen=$cachedLen snapshotLen=${snapshot.content.length} " +
                        "selection=${snapshot.selection.start}..${snapshot.selection.end} queryLen=${query.length}",
                )
                block(snapshot.content, "codemirror-snapshot")
            }
            if (requested) {
                KardLeafLog.d(
                    SEARCH_TRACE_TAG,
                    "searchSnapshot request reason=$reason token=$token cachedLen=$cachedLen queryLen=${query.length}",
                )
                return
            }
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "searchSnapshot fallback reason=$reason token=$token cachedLen=$cachedLen queryLen=${query.length}",
            )
        }
        block(editorController.getText(), "cached")
    }

    fun searchInNote(query: String) {
        KardLeafLog.d(
            SEARCH_TRACE_TAG,
            "searchInNote enter queryLen=${query.length} editing=$isEditing largePlain=$showsLargePlainTextPreview " +
                "currentCount=$noteSearchMatchCount current=${noteSearchCurrentStart}..${noteSearchCurrentEnd}",
        )
        if (query.isBlank()) {
            noteSearchRequestToken++
            noteSearchError = null
            noteSearchMatchCount = 0
            noteSearchCurrentStart = -1
            noteSearchCurrentEnd = -1
            noteSearchCurrentOrdinal = 0
            editorController.clearSearchHighlights()
            return
        }
        runWithSearchText("search", query) { text, source ->
            val result = buildNoteSearchMatches(text, query, noteSearchUseRegex, noteSearchMatchCase)
            val index = result.matches.firstOrNull()?.start ?: -1
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "searchInNote result queryLen=${query.length} textLen=${text.length} source=$source count=${result.matches.size} " +
                    "firstIndex=$index firstText=${noteSearchSnippetForLog(text, index, index + query.length)} " +
                    "error=${result.errorMessage}",
            )
            selectSearchMatch(index, query, text, source)
        }
    }

    fun moveSearchMatch(forward: Boolean) {
        val query = noteSearchQuery
        if (query.isBlank()) return
        runWithSearchText(if (forward) "next" else "previous", query) { text, source ->
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "moveSearchMatch enter forward=$forward queryLen=${query.length} textLen=${text.length} source=$source " +
                    "current=${noteSearchCurrentStart}..${noteSearchCurrentEnd} ordinal=$noteSearchCurrentOrdinal/$noteSearchMatchCount " +
                    "editing=$isEditing largePlain=$showsLargePlainTextPreview",
            )
            val result = buildNoteSearchMatches(text, query, noteSearchUseRegex, noteSearchMatchCase)
            noteSearchError = result.errorMessage
            if (result.errorMessage != null || result.matches.isEmpty()) {
                updateSearchState(query, -1, text)
                return@runWithSearchText
            }
            val currentIndex = result.matches.indexOfFirst { it.start == noteSearchCurrentStart && it.end == noteSearchCurrentEnd }
            val nextIndex = if (forward) {
                if (currentIndex >= 0) (currentIndex + 1) % result.matches.size else 0
            } else {
                if (currentIndex > 0) currentIndex - 1 else result.matches.lastIndex
            }
            val nextMatch = result.matches[nextIndex]
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "moveSearchMatch result forward=$forward count=${result.matches.size} currentIndex=$currentIndex " +
                    "nextIndex=$nextIndex nextStart=${nextMatch.start} " +
                    "nextText=${noteSearchSnippetForLog(text, nextMatch.start, nextMatch.end)} source=$source",
            )
            selectSearchMatch(nextMatch.start, query, text, source)
        }
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
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "outline jump preview heading=${heading.text.take(40)} level=${heading.level} token=$previewHeadingScrollToken",
            )
        }
    }

    LaunchedEffect(pendingEditorSearchJump, editorDocumentKey, initialContent, isOpeningNoteContent) {
        val jump = pendingEditorSearchJump ?: return@LaunchedEffect
        if (isPrivacyEditor || currentNote?.id != jump.noteId || jump.query.isBlank() || initialContent.isBlank()) {
            return@LaunchedEffect
        }
        val matchIndex = initialContent.indexOf(jump.query, ignoreCase = true)
        if (matchIndex < 0) {
            if (!isOpeningNoteContent) {
                viewModel.consumeEditorSearchJump(jump.requestId)
            }
            return@LaunchedEffect
        }
        suppressNextSearchKeyboardRequest = !showNoteSearch
        showNoteSearch = true
        noteSearchQuery = jump.query
        withFrameNanos { }
        selectSearchMatch(matchIndex, jump.query, initialContent, "dashboard-jump")
        viewModel.consumeEditorSearchJump(jump.requestId)
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
            if (suppressNextSearchKeyboardRequest) {
                suppressNextSearchKeyboardRequest = false
            } else {
                keyboardController?.show()
            }
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

    if (showLabelMenu && !isPrivacyEditor) {
        MoveNotesBottomSheet(
            availableLabels = labels,
            onDismiss = {
                lastLabelMenuDismissAt = SystemClock.uptimeMillis()
                showLabelMenu = false
            },
            onMove = { targetLabel ->
                folder = targetLabel
                lastLabelMenuDismissAt = SystemClock.uptimeMillis()
                showLabelMenu = false
                if (currentNote != null) {
                    saveNote(saveHistory = false)
                }
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
        // Opening the editor already loaded the markdown text. Reuse that parsed
        // front matter instead of reading the same file again during the first frame.
        noteFrontMatterProperties = initialFrontMatter.properties
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
            folder = currentNote!!.folder
        } else {
            folder = externalDraft?.folder?.takeIf { it.isNotBlank() } ?: initialLabel
        }
    }

    fun leaveEditor() {
        KardLeafLog.d(
            SAVE_PATH_TRACE_TAG,
            "leaveEditor start key=$editorDocumentKey currentPath=${currentNote?.file?.path} " +
                "isEditing=$isEditing isLeaving=$isLeavingEditor isClosing=$isClosingEditor " +
                "editorDirty=${viewModel.editorDirty.value} showSearch=$showNoteSearch",
        )
        onLeavingEditorStart()
        isLeavingEditor = true
        isClosingEditor = true
        closeNoteSidePanel()
        requestKeyboardOnEdit = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        editorController.releaseForClose(clearText = false)
        onBack()
    }



    fun enterPreviewMode() {
        fun applySnapshot(snapshot: KardLeafEditorSnapshot) {
            renderPreviewSnapshot(snapshot)
            isLeavingEditor = true
            requestKeyboardOnEdit = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            isEditing = false
        }
        if (usesCodeMirrorLikeEditor && editorController.requestExternalSnapshot { snapshot ->
                KardLeafLog.d(EDITOR_TRACE_TAG, "enterPreviewMode got CodeMirror snapshot key=$editorDocumentKey contentLen=${snapshot.content.length}")
                applySnapshot(snapshot)
            }
        ) {
            KardLeafLog.d(EDITOR_TRACE_TAG, "enterPreviewMode request CodeMirror snapshot key=$editorDocumentKey")
            return
        }
        applySnapshot(editorController.getSnapshot())
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
        if (!usesCodeMirrorLikeEditor && isShowingPartialLargeNote) {
            viewModel.promotePartialLargeNoteForEditing()
            requestKeyboardOnEdit = false
            pendingEditScrollRatio = null
            pendingEditScrollOffset = null
            isEditing = false
            return
        }
        if (!usesCodeMirrorLikeEditor && !isNewPrivacyNote && currentTextLength > DIRECT_EDIT_MAX_CHARS) {
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

    LaunchedEffect(effectiveEditorOpen, editorDocumentKey, isUserPerfTrackedNote) {
        if (effectiveEditorOpen && isUserPerfTrackedNote && !userPerfScreenComposedLogged) {
            userPerfScreenComposedLogged = true
            logUserPerfOpenStep(
                "screenComposed",
                userPerfModeName(),
            )
        }
    }

    LaunchedEffect(effectiveEditorOpen, isOpeningNoteContent, isUserPerfTrackedNote, initialContent.length) {
        if (effectiveEditorOpen && isUserPerfTrackedNote) {
            KardLeafLog.d(
                USER_PERF_TRACE_TAG,
                "editorOpen contentReadyCheck elapsed=${SystemClock.elapsedRealtime() - userPerfOpenStartMs}ms " +
                    "key=$editorDocumentKey contentLen=${initialContent.length} isOpening=$isOpeningNoteContent " +
                    "alreadyLogged=$userPerfContentReadyLogged willLog=${!isOpeningNoteContent && !userPerfContentReadyLogged}",
            )
        }
        if (effectiveEditorOpen && isUserPerfTrackedNote && !isOpeningNoteContent && !userPerfContentReadyLogged) {
            userPerfContentReadyLogged = true
            logUserPerfOpenStep(
                "contentReady",
                userPerfModeName(),
            )
        }
    }

    LaunchedEffect(blocksDirectEditForLargeNote, isEditing) {
        if (blocksDirectEditForLargeNote && isEditing) {
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "screen leave editor mode because large note blocks direct edit key=$editorDocumentKey initialContentLen=${initialContent.length}",
            )
            requestKeyboardOnEdit = false
            pendingEditScrollRatio = null
            pendingEditScrollOffset = null
            isEditing = false
        }
    }

    fun openMindMap() {
        val result = if (initialContent.length > KardLeafContentLimits.MIND_MAP_MAX_CONTENT_CHARS) {
            blockedLargeMindMapResult(initialContent.length)
        } else {
            prepareMarkdownMindMap(editorController.getText())
        }
        mindMapHeadings = result.headings
        mindMapUnavailableTitle = result.unavailableTitle
        mindMapUnavailableMessage = result.unavailableMessage
        showMindMap = true
    }


    LaunchedEffect(
        effectiveEditorOpen,
        isOpeningNoteContent,
        isEditing,
        visiblePreviewContent.length,
        visiblePreviewSignature,
        lastRenderedPreviewSignature,
    ) {
        if (isOpeningNoteContent && effectiveEditorOpen) {
            openingPreviewRenderPending = true
            return@LaunchedEffect
        }
        if (!effectiveEditorOpen || isEditing || showsLargePlainTextPreview || visiblePreviewContent.isEmpty()) {
            openingPreviewRenderPending = false
            return@LaunchedEffect
        }
        if (openingPreviewRenderPending && lastRenderedPreviewSignature == visiblePreviewSignature) {
            openingPreviewRenderPending = false
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

    LaunchedEffect(effectiveEditorOpen, isEditing, isOpeningNoteContent, currentNote, externalDraft, isLeavingEditor, showDrawingPad) {
        val shouldRequestKeyboard = requestKeyboardOnEdit || currentNote == null
        val shouldTraceKeyboardEffect =
            effectiveEditorOpen &&
                (currentNote == null || requestKeyboardOnEdit || externalDraft != null)
        if (shouldTraceKeyboardEffect) {
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "keyboardEffect enter key=$editorDocumentKey should=$shouldRequestKeyboard requestOnEdit=$requestKeyboardOnEdit " +
                    "currentNoteNull=${currentNote == null} draftFolder=${externalDraft?.folder} draftTemporary=${externalDraft?.isTemporary} " +
                    "editing=$isEditing opening=$isOpeningNoteContent leaving=$isLeavingEditor drawing=$showDrawingPad search=$showNoteSearch " +
                    "focusToken=$editorFocusRequestToken codeMirror=$usesCodeMirrorLikeEditor contentLen=${initialContent.length} ${editorMemorySummary()}",
            )
        }
        if (!isLeavingEditor &&
            !showDrawingPad &&
            !showNoteSearch &&
            effectiveEditorOpen &&
            isEditing &&
            !isOpeningNoteContent &&
            shouldRequestKeyboard
        ) {
            withFrameNanos { }
            val nextToken = editorFocusRequestToken + 1
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "keyboardEffect requestFocus key=$editorDocumentKey nextToken=$nextToken " +
                    "draftFolder=${externalDraft?.folder} contentLen=${initialContent.length} ${editorMemorySummary()}",
            )
            editorFocusRequestToken = nextToken
            delay(120)
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "keyboardEffect skipComposeShowKeyboard key=$editorDocumentKey token=$editorFocusRequestToken " +
                    "controllerNull=${keyboardController == null} ${editorMemorySummary()}",
            )
            requestKeyboardOnEdit = false
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "keyboardEffect done key=$editorDocumentKey token=$editorFocusRequestToken requestOnEdit=$requestKeyboardOnEdit ${editorMemorySummary()}",
            )
        } else if (shouldTraceKeyboardEffect) {
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "keyboardEffect skip key=$editorDocumentKey should=$shouldRequestKeyboard editing=$isEditing opening=$isOpeningNoteContent " +
                    "leaving=$isLeavingEditor drawing=$showDrawingPad search=$showNoteSearch ${editorMemorySummary()}",
            )
        }
    }

    LaunchedEffect(isEditing, isOpeningNoteContent, folder, editorDocumentKey, initialTitle, initialContent, showsLargePlainTextPreview, usesCodeMirrorLikeEditor) {
        if (!isEditing && !isOpeningNoteContent) {
            withFrameNanos { }
            if (showsLargePlainTextPreview) {
                renderPreviewSnapshot(largePlainPreviewSnapshot ?: KardLeafEditorSnapshot(initialTitle, initialContent))
            } else {
                renderPreviewSnapshot(editorController.getSnapshot())
            }
        }
    }

    LaunchedEffect(
        effectiveEditorOpen,
        isOpeningNoteContent,
        isEditing,
        blocksDirectEditForLargeNote,
        renderedPreview.length,
        initialContent.length,
        showsLargePlainTextPreview,
        usesCodeMirrorLikeEditor,
    ) {
        if (effectiveEditorOpen) {
            val previewContentLen = if (isOpeningNoteContent) 0 else renderedPreview.length
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "screen visible mode=${if (isEditing && !blocksDirectEditForLargeNote) "editor" else "preview"} " +
                    "key=$editorDocumentKey isOpening=$isOpeningNoteContent editing=$isEditing largeBlocked=$blocksDirectEditForLargeNote " +
                    "partialLarge=$isShowingPartialLargeNote plainLargePreview=$showsLargePlainTextPreview codeMirror=$usesCodeMirrorLikeEditor " +
                    "initialContentLen=${initialContent.length} " +
                    "renderedPreviewLen=${renderedPreview.length} previewContentLen=$previewContentLen",
            )
            if (!showsLargePlainTextPreview && !usesCodeMirrorLikeEditor && !isOpeningNoteContent && !isEditing && initialContent.isNotEmpty() && renderedPreview.isEmpty()) {
                KardLeafLog.w(
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
        KardLeafLog.d(
            BACK_TRACE_TAG,
            "Editor root BackHandler hit isEditing=$isEditing showNoteSearch=$showNoteSearch " +
                "sidePanelActive=$noteSidePanelsActive sidePanelOffset=$noteSidePanelOffsetPx " +
                "showLabelMenu=$showLabelMenu showMoreMenu=$showMoreMenu showHeadingMenu=$showHeadingMenu showMathMenu=$showMathMenu",
        )
        if (isEditing) {
            val backSnapshot = editorController.getSnapshot()
            KardLeafLog.d(
                SAVE_PATH_TRACE_TAG,
                "back leave decision key=$editorDocumentKey shouldSave=${shouldSaveEditorOnLeave()} " +
                    "currentPath=${currentNote?.file?.path} currentTitle=${currentNote?.title} " +
                    "snapshotTitleLen=${backSnapshot.title.length} snapshotContentLen=${backSnapshot.content.length} " +
                    "editorDirty=${viewModel.editorDirty.value} privacyDirty=$privacyEditorDirty " +
                    "hideDraftTitle=$hideDraftTitleInEditor hideInitialTitle=$hideInitialTitleInEditor rawInitialTitle=$rawInitialTitle",
            )
        }
        if (isEditing && shouldSaveEditorOnLeave()) {
            KardLeafLog.d(
                SAVE_PATH_TRACE_TAG,
                "back leave deferred until save snapshot key=$editorDocumentKey currentPath=${currentNote?.file?.path}",
            )
            saveNote(saveHistory = true) {
                KardLeafLog.d(
                    SAVE_PATH_TRACE_TAG,
                    "back save snapshot finished, leaving key=$editorDocumentKey currentPath=${currentNote?.file?.path}",
                )
                leaveEditor()
            }
        } else {
            leaveEditor()
        }
    }

    BackHandler(enabled = showNoteSearch) {
        KardLeafLog.d(BACK_TRACE_TAG, "Editor note search BackHandler hit")
        closeNoteSearch()
    }

    BackHandler(enabled = noteSidePanelsActive && abs(noteSidePanelOffsetPx) > 1f) {
        KardLeafLog.d(BACK_TRACE_TAG, "Editor side panel BackHandler hit offset=$noteSidePanelOffsetPx")
        closeNoteSidePanel()
    }

    BackHandler(enabled = showLabelMenu || showMoreMenu || showHeadingMenu || showMathMenu) {
        KardLeafLog.d(
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
        KardLeafLog.d(
            BACK_TRACE_TAG,
            "Editor menu state changed showLabelMenu=$showLabelMenu showMoreMenu=$showMoreMenu " +
                "showHeadingMenu=$showHeadingMenu showMathMenu=$showMathMenu",
        )
    }

    val latestAutoSave by rememberUpdatedState(newValue = {
        if (isEditing && shouldSaveEditorOnLeave()) {
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
    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val appThemeMode = LocalKardLeafThemeMode.current
    val appThemeStyle = LocalKardLeafThemeStyle.current
    val isDark = appThemeStyle == PrefsManager.AppThemeStyle.DRACULA ||
        appThemeStyle == PrefsManager.AppThemeStyle.GITHUB_DARK ||
        when (appThemeMode) {
            PrefsManager.AppThemeMode.SYSTEM -> systemDarkTheme
            PrefsManager.AppThemeMode.LIGHT -> false
            PrefsManager.AppThemeMode.DARK -> true
        }

    // Scaffold

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .onGloballyPositioned { coordinates ->
                    logCodeMirrorOuterLayout("screenRootBox", coordinates)
                },
    ) {
        Scaffold(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            logCodeMirrorOuterLayout("scaffold", coordinates)
        },
        topBar = {
            AnimatedVisibility(
                visible = showBars || isEditing,
                enter = kardLeafSharedAxisYIn(
                    initialOffsetY = { height -> -height / 3 },
                    durationMillis = KardLeafMotion.ContainerDurationMillis,
                ),
                exit = kardLeafSharedAxisYOut(
                    targetOffsetY = { height -> -height / 3 },
                    durationMillis = KardLeafMotion.ContainerDurationMillis,
                ),
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
                            enabled = noteSearchMatchCount > 0,
                            onClick = { moveSearchMatch(forward = false) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上一个")
                        }
                        IconButton(
                            enabled = noteSearchMatchCount > 0,
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
                            item !in editorTopToolbarHiddenItems &&
                                (noteSidePanelToolbarEnabled || (item != PrefsManager.EditorTopToolbarItemId.OUTLINE && item != PrefsManager.EditorTopToolbarItemId.REMARKS))
                        }
                        val safeEditorTopToolbarMoreItems = editorTopToolbarMoreItems
                            .filter { it in normalizedEditorTopToolbarOrder && it != PrefsManager.EditorTopToolbarItemId.MORE }
                            .toSet()
                        val editorTopToolbarMoreDisplayItems = normalizedEditorTopToolbarOrder.filter { it in safeEditorTopToolbarMoreItems }
                        val editorTopToolbarTopItems = normalizedEditorTopToolbarOrder.filter { it !in safeEditorTopToolbarMoreItems }

                        KardLeafLog.d(
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
                                        KardLeafLog.d(BACK_TRACE_TAG, "Editor label click toggle menu showLabelMenu=$showLabelMenu ignoreReopen=$ignoreReopen")
                                        if (!ignoreReopen) {
                                            showMoreMenu = false
                                            showHeadingMenu = false
                                            showMathMenu = false
                                            showLabelMenu = !showLabelMenu
                                        }
                                    }) {
                                        Icon(
                                            Icons.Outlined.FolderOpen,
                                            contentDescription = stringResource(R.string.label),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }

                        @Composable
                        fun OutlineAction() {
                            IconButton(onClick = { openNoteSidePanel(noteSidePanelWidthPx) }) {
                                Icon(
                                    Icons.Outlined.Toc,
                                    contentDescription = "大纲",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        @Composable
                        fun RemarksAction() {
                            IconButton(onClick = { openNoteSidePanel(-noteSidePanelWidthPx) }) {
                                Icon(
                                    Icons.Outlined.StickyNote2,
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
                        fun MindMapAction() {
                            IconButton(onClick = {
                                showLabelMenu = false
                                showMoreMenu = false
                                showHeadingMenu = false
                                showMathMenu = false
                                closeNoteSearch()
                                openMindMap()
                            }) {
                                Icon(
                                    Icons.Outlined.AccountTree,
                                    contentDescription = "思维导图",
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
                                    Icon(Icons.Outlined.Shield, contentDescription = "保护")
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
                                        if (note.isArchived) Icons.Outlined.Refresh else Icons.Outlined.Inventory2,
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
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete))
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
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = stringResource(R.string.delete))
                                    }
                                }
                            }
                        }

                        @Composable
                        fun EditorTopToolbarMoreItem(item: PrefsManager.EditorTopToolbarItemId) {
                            when (item) {
                                PrefsManager.EditorTopToolbarItemId.MINDMAP -> {
                                    DropdownMenuItem(
                                        text = { Text("思维导图") },
                                        leadingIcon = { Icon(Icons.Outlined.AccountTree, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            closeNoteSearch()
                                            openMindMap()
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.LABEL -> {
                                    if (!isPrivacyEditor) {
                                        DropdownMenuItem(
                                            text = { Text("移动笔记") },
                                            leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) },
                                            onClick = {
                                                showMoreMenu = false
                                                showLabelMenu = true
                                            },
                                        )
                                    }
                                }
                                PrefsManager.EditorTopToolbarItemId.OUTLINE -> if (noteSidePanelToolbarEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("大纲") },
                                        leadingIcon = { Icon(Icons.Outlined.Toc, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            openNoteSidePanel(noteSidePanelWidthPx)
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.REMARKS -> if (noteSidePanelToolbarEnabled) {
                                    DropdownMenuItem(
                                        text = { Text("属性备注") },
                                        leadingIcon = { Icon(Icons.Outlined.StickyNote2, null) },
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
                                            leadingIcon = { Icon(Icons.Outlined.Shield, null) },
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
                                                leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
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
                                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
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
                                        KardLeafLog.d(BACK_TRACE_TAG, "Editor privacy more click toggle menu showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
                                        if (!ignoreReopen) {
                                            showLabelMenu = false
                                            showHeadingMenu = false
                                            showMathMenu = false
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
                                                        "Editor privacy more popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoreMenu=$showMoreMenu",
                                                    )
                                                }
                                                false
                                            },
                                        expanded = showMoreMenu,
                                        onDismissRequest = {
                                            KardLeafLog.d(BACK_TRACE_TAG, "Editor note more onDismissRequest showMoreMenu=$showMoreMenu")
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
                                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
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
                                                    KardLeafLog.d(BACK_TRACE_TAG, "Editor note more click toggle menu noteId=${currentNoteObj.id} showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen")
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
                                    KardLeafDropdownMenu(
                                        modifier =
                                            Modifier.onPreviewKeyEvent { event ->
                                                if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                                    KardLeafLog.d(
                                                        BACK_TRACE_TAG,
                                                        "Editor note more popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMoreMenu=$showMoreMenu",
                                                    )
                                                }
                                                false
                                            },
                                        expanded = showMoreMenu,
                                        onDismissRequest = {
                                            KardLeafLog.d(BACK_TRACE_TAG, "Editor privacy more onDismissRequest showMoreMenu=$showMoreMenu")
                                            lastMoreMenuDismissAt = SystemClock.uptimeMillis()
                                            showMoreMenu = false
                                        },
                                        properties = PopupProperties(
                                            focusable = false,
                                            dismissOnBackPress = false,
                                            dismissOnClickOutside = true,
                                        ),
                                    ) {
                                        val targetEditorKernel = if (usesCodeMirrorLikeEditor) {
                                            PrefsManager.EditorKernel.NATIVE
                                        } else {
                                            PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (targetEditorKernel == PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW) {
                                                        "切换内核：CodeMirror"
                                                    } else {
                                                        "切换内核：原生编辑器"
                                                    },
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (targetEditorKernel == PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW) Icons.Outlined.Code else Icons.Outlined.Edit,
                                                    null,
                                                )
                                            },
                                            onClick = { switchEditorKernelTemporarily(targetEditorKernel) },
                                        )
                                        HorizontalDivider()
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
                                PrefsManager.EditorTopToolbarItemId.MINDMAP -> MindMapAction()
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
                AnimatedVisibility(
                    visible = !showNoteSearch && showNoteDetailFileInfo,
                    enter = kardLeafSharedAxisYIn(
                        initialOffsetY = { height -> -height / 4 },
                        durationMillis = KardLeafMotion.ContainerDurationMillis,
                    ),
                    exit = kardLeafSharedAxisYOut(
                        targetOffsetY = { height -> -height / 4 },
                        durationMillis = KardLeafMotion.MicroDurationMillis,
                    ),
                ) {
                    Text(
                        text = noteFileInfoText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AnimatedVisibility(
                    visible = showNoteSearch && isEditing,
                    enter = kardLeafSharedAxisYIn(
                        initialOffsetY = { height -> -height / 4 },
                        durationMillis = KardLeafMotion.ContainerDurationMillis,
                    ),
                    exit = kardLeafSharedAxisYOut(
                        targetOffsetY = { height -> -height / 4 },
                        durationMillis = KardLeafMotion.MicroDurationMillis,
                    ),
                ) {
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
            AnimatedVisibility(
                modifier = Modifier
                    .offset {
                        val imeBottom = imeInsets.getBottom(density)
                        val navigationBottom = navigationBarsInsets.getBottom(density)
                        IntOffset(0, -(imeBottom - navigationBottom).coerceAtLeast(0))
                    }
                    .onGloballyPositioned { coordinates ->
                        logCodeMirrorOuterLayout("bottomBarAnimatedVisibility", coordinates)
                    },
                visible = isEditing && shouldShowBottomToolbar,
                enter = kardLeafSharedAxisYIn(
                    initialOffsetY = { height -> height / 3 },
                    durationMillis = KardLeafMotion.ContainerDurationMillis,
                ),
                exit = kardLeafSharedAxisYOut(
                    targetOffsetY = { height -> height / 3 },
                    durationMillis = KardLeafMotion.ContainerDurationMillis,
                ),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .onGloballyPositioned { coordinates ->
                                logCodeMirrorOuterLayout("bottomToolbarBeforeInsets", coordinates)
                            }
                            .navigationBarsPadding()
                            .onGloballyPositioned { coordinates ->
                                logCodeMirrorOuterLayout("bottomToolbarAfterInsets", coordinates)
                            }
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
                        transitionSpec = {
                            kardLeafFadeThroughContentTransform(
                                durationMillis = KardLeafMotion.ContainerDurationMillis,
                            )
                        },
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
                                        enabled = canUndo,
                                        contentDescription = "撤销",
                                        onClick = { undoContent() },
                                    )
                                    KardLeafCustomFeatures.ToolbarItem.REDO -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.Redo,
                                        enabled = canRedo,
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
                                    KardLeafCustomFeatures.ToolbarItem.HEADING -> ToolbarIconButton(text = "H1", bold = true, onClick = { applyHeadingAtCursor(1) })
                                    KardLeafCustomFeatures.ToolbarItem.HEADING2 -> ToolbarIconButton(text = "H2", bold = true, onClick = { applyHeadingAtCursor(2) })
                                    KardLeafCustomFeatures.ToolbarItem.HEADING3 -> ToolbarIconButton(text = "H3", bold = true, onClick = { applyHeadingAtCursor(3) })
                                    KardLeafCustomFeatures.ToolbarItem.RULE -> ToolbarIconButton(text = "---", onClick = { insertAtCursorOrCommand("---\n", command = "insertHorizontalRule") })
                                    KardLeafCustomFeatures.ToolbarItem.BOLD -> ToolbarIconButton(text = "B", bold = true, onClick = { insertAtCursorOrCommand("**", "**", command = "toggleBold") })
                                    KardLeafCustomFeatures.ToolbarItem.ITALIC -> ToolbarIconButton(text = "I", italic = true, onClick = { insertAtCursorOrCommand("_", "_", command = "toggleItalic") })
                                    KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> ToolbarIconButton(text = "U", underline = true, onClick = { insertAtCursor("<u>", "</u>") })
                                    KardLeafCustomFeatures.ToolbarItem.STRIKE -> ToolbarIconButton(text = "S", strikethrough = true, onClick = { insertAtCursorOrCommand("~~", "~~", command = "toggleStrike") })
                                    KardLeafCustomFeatures.ToolbarItem.LINK -> ToolbarIconButton(text = "Link", onClick = { insertAtCursor("[", "](url)") })
                                    KardLeafCustomFeatures.ToolbarItem.CODE -> ToolbarIconButton(text = "`", onClick = { insertAtCursorOrCommand("`", "`", command = "toggleCode") })
                                    KardLeafCustomFeatures.ToolbarItem.CODE_BLOCK -> ToolbarIconButton(text = "```", onClick = { insertAtCursorOrCommand("```\n", "\n```", command = "insertCodeBlock") })
                                    KardLeafCustomFeatures.ToolbarItem.QUOTE -> ToolbarIconButton(text = "\"", onClick = { insertAtCursorOrCommand("> ", command = "toggleBlockquote") })
                                    KardLeafCustomFeatures.ToolbarItem.MATH -> ToolbarIconButton(text = "$", onClick = { insertAtCursor("$", "$") })
                                    KardLeafCustomFeatures.ToolbarItem.BULLET -> ToolbarIconButton(text = "-", onClick = { insertAtCursorOrCommand("- ", command = "toggleUnorderedList") })
                                    KardLeafCustomFeatures.ToolbarItem.NUMBERED -> ToolbarIconButton(text = "1.", onClick = { insertAtCursorOrCommand("1. ", command = "toggleOrderedList") })
                                    KardLeafCustomFeatures.ToolbarItem.CHECKBOX -> ToolbarIconButton(text = "[ ]", onClick = { insertAtCursorOrCommand("- [ ] ", command = "toggleCheckList") })
                                    KardLeafCustomFeatures.ToolbarItem.CHECKBOX_DONE -> ToolbarIconButton(text = "[x]", onClick = { insertAtCursor("- [x] ") })
                                    KardLeafCustomFeatures.ToolbarItem.TABLE -> ToolbarIconButton(text = "表格", onClick = { insertAtCursorOrCommand("| 列1 | 列2 |\n| --- | --- |\n| 内容 | 内容 |\n", command = "insertTable") })
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
                                            enabled = canUndo,
                                            contentDescription = "撤销",
                                            onClick = { undoContent() },
                                        )
                                        KardLeafCustomFeatures.ToolbarItem.REDO -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.Redo,
                                            enabled = canRedo,
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
                                                    onClick = { applyHeadingAtCursor(1) },
                                                    onLongClick = {
                                                        val now = SystemClock.uptimeMillis()
                                                        val ignoreReopen = !showHeadingMenu && now - lastHeadingMenuDismissAt < MENU_REOPEN_GUARD_MS
                                                        KardLeafLog.d(BACK_TRACE_TAG, "Editor heading menu longClick toggle showHeadingMenu=$showHeadingMenu ignoreReopen=$ignoreReopen")
                                                        if (!ignoreReopen) {
                                                            showLabelMenu = false
                                                            showMoreMenu = false
                                                            showMathMenu = false
                                                            showHeadingMenu = !showHeadingMenu
                                                        }
                                                    },
                                                )
                                                KardLeafDropdownMenu(
                                                    modifier =
                                                        Modifier.onPreviewKeyEvent { event ->
                                                            if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                                                KardLeafLog.d(
                                                                    BACK_TRACE_TAG,
                                                                    "Editor heading popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showHeadingMenu=$showHeadingMenu",
                                                                )
                                                            }
                                                            false
                                                        },
                                                    expanded = showHeadingMenu,
                                                    onDismissRequest = {
                                                        KardLeafLog.d(BACK_TRACE_TAG, "Editor heading menu onDismissRequest showHeadingMenu=$showHeadingMenu")
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
                                                                    if (!runCodeMirrorCommand("toggleHeading", label.removePrefix("H").toIntOrNull() ?: 1)) {
                                                                        insertAtCursor(md)
                                                                    }
                                                                    showHeadingMenu = false
                                                                },
                                                            )
                                                        }
                                                }
                                            }
                                        }
                                        KardLeafCustomFeatures.ToolbarItem.HEADING2 -> ToolbarIconButton(text = "H2", bold = true, onClick = { applyHeadingAtCursor(2) })
                                        KardLeafCustomFeatures.ToolbarItem.HEADING3 -> ToolbarIconButton(text = "H3", bold = true, onClick = { applyHeadingAtCursor(3) })
                                        KardLeafCustomFeatures.ToolbarItem.RULE -> ToolbarIconButton(text = "---", onClick = { insertAtCursorOrCommand("---\n", command = "insertHorizontalRule") })
                                        KardLeafCustomFeatures.ToolbarItem.BOLD -> ToolbarIconButton(text = "B", bold = true, onClick = { insertAtCursorOrCommand("**", "**", command = "toggleBold") })
                                        KardLeafCustomFeatures.ToolbarItem.ITALIC -> ToolbarIconButton(text = "I", italic = true, onClick = { insertAtCursorOrCommand("_", "_", command = "toggleItalic") })
                                        KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> ToolbarIconButton(text = "U", underline = true, onClick = { insertAtCursor("<u>", "</u>") })
                                        KardLeafCustomFeatures.ToolbarItem.STRIKE -> ToolbarIconButton(text = "S", strikethrough = true, onClick = { insertAtCursorOrCommand("~~", "~~", command = "toggleStrike") })
                                        KardLeafCustomFeatures.ToolbarItem.LINK -> ToolbarIconButton(text = "Link", onClick = { insertAtCursor("[", "](url)") })
                                        KardLeafCustomFeatures.ToolbarItem.CODE -> ToolbarIconButton(text = "`", onClick = { insertAtCursorOrCommand("`", "`", command = "toggleCode") })
                                        KardLeafCustomFeatures.ToolbarItem.CODE_BLOCK -> ToolbarIconButton(text = "```", onClick = { insertAtCursorOrCommand("```\n", "\n```", command = "insertCodeBlock") })
                                        KardLeafCustomFeatures.ToolbarItem.QUOTE -> ToolbarIconButton(text = "\"", onClick = { insertAtCursorOrCommand("> ", command = "toggleBlockquote") })
                                        KardLeafCustomFeatures.ToolbarItem.MATH -> {
                                            Box {
                                                ToolbarIconButton(
                                                    text = "$",
                                                    onClick = { insertAtCursor("$", "$") },
                                                    onLongClick = {
                                                        val now = SystemClock.uptimeMillis()
                                                        val ignoreReopen = !showMathMenu && now - lastMathMenuDismissAt < MENU_REOPEN_GUARD_MS
                                                        KardLeafLog.d(BACK_TRACE_TAG, "Editor math menu longClick toggle showMathMenu=$showMathMenu ignoreReopen=$ignoreReopen")
                                                        if (!ignoreReopen) {
                                                            showLabelMenu = false
                                                            showMoreMenu = false
                                                            showHeadingMenu = false
                                                            showMathMenu = !showMathMenu
                                                        }
                                                    },
                                                )
                                                KardLeafDropdownMenu(
                                                    modifier =
                                                        Modifier.onPreviewKeyEvent { event ->
                                                            if (event.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_BACK) {
                                                                KardLeafLog.d(
                                                                    BACK_TRACE_TAG,
                                                                    "Editor math popup onPreviewKeyEvent back action=${event.nativeKeyEvent.action} showMathMenu=$showMathMenu",
                                                                )
                                                            }
                                                            false
                                                        },
                                                    expanded = showMathMenu,
                                                    onDismissRequest = {
                                                        KardLeafLog.d(BACK_TRACE_TAG, "Editor math menu onDismissRequest showMathMenu=$showMathMenu")
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
                                        KardLeafCustomFeatures.ToolbarItem.BULLET -> ToolbarIconButton(text = "-", onClick = { insertAtCursorOrCommand("- ", command = "toggleUnorderedList") })
                                        KardLeafCustomFeatures.ToolbarItem.NUMBERED -> ToolbarIconButton(text = "1.", onClick = { insertAtCursorOrCommand("1. ", command = "toggleOrderedList") })
                                        KardLeafCustomFeatures.ToolbarItem.CHECKBOX -> ToolbarIconButton(text = "[ ]", onClick = { insertAtCursorOrCommand("- [ ] ", command = "toggleCheckList") })
                                        KardLeafCustomFeatures.ToolbarItem.CHECKBOX_DONE -> ToolbarIconButton(text = "[x]", onClick = { insertAtCursor("- [x] ") })
                                        KardLeafCustomFeatures.ToolbarItem.TABLE -> ToolbarIconButton(text = "表格", onClick = { insertAtCursorOrCommand("| 列1 | 列2 |\n| --- | --- |\n| 内容 | 内容 |\n", command = "insertTable") })
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
                        logCodeMirrorOuterLayout("contentHostBeforeScaffoldPadding", coordinates)
                    }
                    .nestedScroll(nestedScrollConnection)
                    .padding(paddingValues)
                    .onGloballyPositioned { coordinates ->
                        logCodeMirrorOuterLayout(
                            "contentHostAfterScaffoldPadding",
                            coordinates,
                            "paddingTop=${paddingValues.calculateTopPadding()} paddingBottom=${paddingValues.calculateBottomPadding()}",
                        )
                    }
                    .then(noteSidePanelContentDragModifier)
                    .then(noteSidePanelActiveDragModifier),
        ) {
            if (effectiveEditorOpen && isEditing && !isClosingEditor && !blocksDirectEditForLargeNote) {
                KardLeafLog.d(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "screen compose editor surface key=$editorDocumentKey kernel=$editorKernel useCodeMirror=$usesCodeMirrorLikeEditor " +
                        "initialContentLen=${editorSurfaceContent.length} isOpening=$isOpeningNoteContent editing=$isEditing closing=$isClosingEditor",
                )
                if (usesCodeMirrorLikeEditor) {
                    KardLeafLog.d(
                        TITLE_TRACE_TAG,
                        "title render key=$editorDocumentKey engine=CODEMIRROR showTitle=$showBars " +
                            "showBars=$showBars hideDraftTitle=$hideDraftTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
                            "showDetailTitle=$showNoteDetailTitle rawInitialTitle=$rawInitialTitle initialTitle=$initialTitle " +
                            "displayInitialTitle=$displayInitialTitle keepLastTitleForEmptyExternal=$keepLastTitleForEmptyExternal " +
                            "lastValidTitleLen=${lastValidEditorDisplayTitle.length} currentPath=${currentNote?.file?.path} currentTitle=${currentNote?.title}",
                    )
                    KardLeafCodeMirrorEditor(
                        initialTitle = editorSurfaceTitle,
                        initialContent = editorSurfaceContent,
                        documentKey = editorDocumentKey,
                        controller = editorController,
                        scrollController = codeMirrorScrollController,
                        onTitleChanged = { markEditorDirty() },
                        onContentChanged = {
                            markEditorDirty()
                            syncUndoRedoState()
                        },
                        onContentEdited = { markEditorDirty() },
                        onUndoRedoStateChanged = { syncUndoRedoState() },
                        onUserInteraction = { hideNoteSearchCursor("codemirror editor touch") },
                        onFastScrollSourceScrolled = { fastScrollSignal.notifyScrollChanged() },
                        titleHint = stringResource(R.string.title_hint),
                        textColor = MaterialTheme.colorScheme.onBackground,
                        hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        titleTextSize = MaterialTheme.typography.titleLarge.fontSize,
                        contentTextSize = editorFontSizeSp.sp,
                        contentLineHeightMultiplier = editorLineHeightMultiplier,
                        contentLetterSpacingSp = editorLetterSpacingSp,
                        contentParagraphSpacingDp = editorParagraphSpacingDp,
                        contentFontFamily = editorFontFamily,
                        isDark = isDark,
                        showTitle = showBars,
                        livePreviewEnabled = codeMirrorLivePreviewEnabled,
                        resolveImages = { markdown ->
                            viewModel.resolveMarkdownImageDataUris(markdown, folder).map { image ->
                                KardLeafCodeMirrorImage(
                                    reference = image.reference,
                                    dataUri = image.dataUri,
                                )
                            }
                        },
                        userPerfOpenStartRealtimeMs = userPerfOpenStartMs,
                        userPerfSizeTier = userPerfSizeTier,
                        onUserPerfBodyRendered = { renderedLen, status ->
                            if (isUserPerfTrackedNote && !userPerfRenderedLogged) {
                                userPerfRenderedLogged = true
                                KardLeafLog.d(
                                    USER_PERF_TRACE_TAG,
                                    "editorOpen bodyRendered elapsed=${SystemClock.elapsedRealtime() - userPerfOpenStartMs}ms " +
                                        "engine=CODEMIRROR mode=codeMirror renderStatus=$status renderedLen=$renderedLen " +
                                        "contentLen=$userPerfContentLen sizeTier=$userPerfSizeTier isLarge=$isUserPerfLargeNote " +
                                        "isOpening=$isOpeningNoteContent partialLarge=$isShowingPartialLargeNote path=${currentNote?.file?.path}",
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { coordinates ->
                                logCodeMirrorOuterLayout("codeMirrorSlot", coordinates)
                            }
                            .then(userPerfAreaFirstFrameModifier("codeMirror")),
                    )
                } else {
                    KardLeafLog.d(
                        TITLE_TRACE_TAG,
                        "title render key=$editorDocumentKey engine=NATIVE showTitle=${showBars && !hideDraftTitleInEditor} " +
                            "showBars=$showBars hideDraftTitle=$hideDraftTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
                            "showDetailTitle=$showNoteDetailTitle rawInitialTitle=$rawInitialTitle initialTitle=$initialTitle " +
                            "displayInitialTitle=$displayInitialTitle keepLastTitleForEmptyExternal=$keepLastTitleForEmptyExternal " +
                            "lastValidTitleLen=${lastValidEditorDisplayTitle.length} currentPath=${currentNote?.file?.path} currentTitle=${currentNote?.title}",
                    )
                    KardLeafNativeEditor(
                        initialTitle = editorSurfaceTitle,
                        initialContent = editorSurfaceContent,
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
                        onInlineImageClicked = { reference -> openDrawingPadForReference(reference) },
                        titleHint = stringResource(R.string.title_hint),
                        contentHint = stringResource(R.string.start_typing_hint),
                        textColor = MaterialTheme.colorScheme.onBackground,
                        hintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        titleTextSize = MaterialTheme.typography.titleLarge.fontSize,
                        contentTextSize = editorFontSizeSp.sp,
                        contentLineHeightMultiplier = editorLineHeightMultiplier,
                        contentLetterSpacingSp = editorLetterSpacingSp,
                        contentParagraphSpacingDp = editorParagraphSpacingDp,
                        contentFontFamily = editorFontFamily,
                        requestFocusToken = editorFocusRequestToken,
                        initialSelection = editorSurfaceSelection,
                        showTitle = showBars && !hideDraftTitleInEditor,
                        currentFolder = folder,
                        readOnly = usesOpeningEditShell,
                        userPerfOpenStartRealtimeMs = userPerfOpenStartMs,
                        userPerfSizeTier = userPerfSizeTier,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(userPerfAreaFirstFrameModifier("nativeEditor")),
                    )
                }
            } else if (effectiveEditorOpen) {
                if (showsLargePlainTextPreview) {
                    val plainSnapshot = largePlainPreviewSnapshot
                    KardLeafLog.d(
                        TITLE_TRACE_TAG,
                        "title render key=$editorDocumentKey engine=LARGE_PLAIN_PREVIEW showTitle=true " +
                            "plainTitle=${plainSnapshot?.title} plainTitleLen=${plainSnapshot?.title?.length ?: -1} " +
                            "initialTitle=$initialTitle initialTitleLen=${initialTitle.length} displayInitialTitle=$displayInitialTitle " +
                            "displayInitialTitleLen=${displayInitialTitle.length} keepLastTitleForEmptyExternal=$keepLastTitleForEmptyExternal " +
                            "lastValidTitleLen=${lastValidEditorDisplayTitle.length} " +
                            "showDetailTitle=$showNoteDetailTitle hideDraftTitle=$hideDraftTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
                            "currentPath=${currentNote?.file?.path} currentTitle=${currentNote?.title}",
                    )
                    LargePlainTextPreview(
                        title = plainSnapshot?.title?.takeIf { it.isNotBlank() } ?: displayInitialTitle,
                        content = plainSnapshot?.content ?: initialContent,
                        listState = largePlainTextPreviewListState,
                        searchCurrentStart = if (showNoteSearch) noteSearchCurrentStart else -1,
                        searchCurrentEnd = if (showNoteSearch) noteSearchCurrentEnd else -1,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(userPerfAreaFirstFrameModifier("largePlainPreview")),
                        onUserInteraction = { hideNoteSearchCursor("large plain preview touch") },
                        onFastScrollSourceScrolled = { fastScrollSignal.notifyScrollChanged() },
                        contentTextSizeSp = editorFontSizeSp,
                        contentLineHeightMultiplier = editorLineHeightMultiplier,
                        contentLetterSpacingSp = editorLetterSpacingSp,
                        contentParagraphSpacingDp = editorParagraphSpacingDp,
                        contentFontFamily = editorFontFamily,
                        onFirstContentLaidOut = {
                            if (isUserPerfTrackedNote && !userPerfFirstContentLaidOutLogged) {
                                userPerfFirstContentLaidOutLogged = true
                                logUserPerfOpenStep("firstTextLaidOut", "largePlainPreview")
                                KardLeafLog.d(
                                    USER_PERF_TRACE_TAG,
                                    "editorOpen bodyRendered elapsed=${SystemClock.elapsedRealtime() - userPerfOpenStartMs}ms " +
                                        "mode=largePlainPreview renderStatus=${if ((plainSnapshot?.content ?: initialContent).isNotEmpty()) "visible" else "empty"} " +
                                        "contentLen=$userPerfContentLen sizeTier=$userPerfSizeTier " +
                                        "isLarge=$isUserPerfLargeNote isOpening=$isOpeningNoteContent path=${currentNote?.file?.path}",
                                )
                            }
                        },
                    )
                } else {
                    PreviewWebView(
                        content = visiblePreviewContent,
                        sessionKey = editorDocumentKey,
                        isDark = isDark,
                        controller = previewController,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(userPerfAreaFirstFrameModifier("markdownPreview")),
                        searchQuery = if (showNoteSearch) noteSearchQuery else "",
                        headingScrollText = previewHeadingScrollText,
                        headingScrollLevel = previewHeadingScrollLevel,
                        headingScrollToken = previewHeadingScrollToken,
                        onDoubleTap = { offset -> enterEditMode(preservePreviewPosition = true, previewMarkdownOffset = offset) },
                        onUserInteraction = { hideNoteSearchCursor("preview touch") },
                        onScrollRatioChanged = { previewScrollRatio = it },
                        onFastScrollSourceScrolled = { fastScrollSignal.notifyScrollChanged() },
                        onImageClicked = { index ->
                            previewImageReferences.getOrNull(index)?.let { reference ->
                                openDrawingPadForReference(reference)
                            }
                        },
                        onContentRendered = { length, contentHash ->
                            lastRenderedPreviewSignature = length to contentHash
                            if (isUserPerfTrackedNote && !userPerfRenderedLogged) {
                                userPerfRenderedLogged = true
                                val renderStatus = if (length > 0 && contentHash != 0) "visible" else "empty"
                                KardLeafLog.d(
                                    USER_PERF_TRACE_TAG,
                                    "editorOpen previewRendered elapsed=${SystemClock.elapsedRealtime() - userPerfOpenStartMs}ms " +
                                        "mode=markdownPreview renderedLen=$length contentLen=$userPerfContentLen " +
                                        "sizeTier=$userPerfSizeTier isLarge=$isUserPerfLargeNote " +
                                        "isOpening=$isOpeningNoteContent renderStatus=$renderStatus " +
                                        "hash=$contentHash path=${currentNote?.file?.path}",
                                )
                                KardLeafLog.d(
                                    USER_PERF_TRACE_TAG,
                                    "editorOpen bodyRendered elapsed=${SystemClock.elapsedRealtime() - userPerfOpenStartMs}ms " +
                                        "mode=markdownPreview renderStatus=$renderStatus renderedLen=$length " +
                                        "contentLen=$userPerfContentLen sizeTier=$userPerfSizeTier " +
                                        "isLarge=$isUserPerfLargeNote isOpening=$isOpeningNoteContent path=${currentNote?.file?.path}",
                                )
                            }
                        },
                        doubleTapIntervalMs = previewDoubleTapIntervalMs,
                        contentFontSizeSp = editorFontSizeSp,
                        contentLineHeightMultiplier = editorLineHeightMultiplier,
                        contentLetterSpacingSp = editorLetterSpacingSp,
                        contentParagraphSpacingDp = editorParagraphSpacingDp,
                        contentFontFamily = editorFontFamily,
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
                                        buildCurrentNote(updatedSnapshot),
                                        currentNote?.file,
                                    )
                                }
                            }
                        },
                    )
                }
                if (showOpeningContentProgress) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
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
            val showEditorFastScrollEdge = !noteSidePanelHasOffset || isNoteSidePanelDragging
            if (showEditorFastScrollEdge) {
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
                                if (isEditing && usesCodeMirrorLikeEditor) {
                                    codeMirrorScrollController.getFastScrollMetrics()
                                } else if (isEditing) {
                                    editorController.getFastScrollMetrics()
                                } else if (showsLargePlainTextPreview) {
                                    largePlainTextPreviewFastScrollMetrics(
                                        largePlainTextPreviewListState,
                                        largePlainTextPreviewChunkCount(largePlainPreviewSnapshot?.content?.length ?: initialContent.length),
                                    )
                                } else {
                                    previewController.getFastScrollMetrics()
                                }
                            },
                            onScrollToRatio = { ratio ->
                                if (isEditing && usesCodeMirrorLikeEditor) {
                                    codeMirrorScrollController.fastScrollToRatio(ratio)
                                } else if (isEditing) {
                                    editorController.fastScrollToRatio(ratio)
                                } else if (showsLargePlainTextPreview) {
                                    coroutineScope.launch {
                                        val totalItems = largePlainTextPreviewChunkCount(largePlainPreviewSnapshot?.content?.length ?: initialContent.length) + 1
                                        val targetIndex = (ratio.coerceIn(0f, 1f) * (totalItems - 1).coerceAtLeast(0)).roundToInt()
                                        KardLeafLog.d(
                                            SEARCH_TRACE_TAG,
                                            "largePreviewFastScroll ratio=$ratio totalItems=$totalItems targetIndex=$targetIndex " +
                                                "firstBefore=${largePlainTextPreviewListState.firstVisibleItemIndex} offsetBefore=${largePlainTextPreviewListState.firstVisibleItemScrollOffset}",
                                        )
                                        largePlainTextPreviewListState.scrollToItem(targetIndex)
                                        KardLeafLog.d(
                                            SEARCH_TRACE_TAG,
                                            "largePreviewFastScroll done targetIndex=$targetIndex firstAfter=${largePlainTextPreviewListState.firstVisibleItemIndex} " +
                                                "offsetAfter=${largePlainTextPreviewListState.firstVisibleItemScrollOffset}",
                                        )
                                    }
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
            } else {
                fastScrollSignal.setListener(null)
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
        if (showMindMap) {
            val snapshot = editorController.getSnapshot()
            MarkdownMindMapScreen(
                title = snapshot.title,
                headings = mindMapHeadings,
                isDark = isDark,
                unavailableTitle = mindMapUnavailableTitle,
                unavailableMessage = mindMapUnavailableMessage,
                modifier = Modifier.zIndex(9f),
                onDismiss = { showMindMap = false },
                onHeadingClick = { heading ->
                    showMindMap = false
                    jumpToHeading(heading)
                },
                onNodeReparent = { movingIndex, parentIndex ->
                    val editSnapshot = editorController.getSnapshot()
                    val currentHeadings = extractMarkdownHeadings(editSnapshot.content)
                    val reparentResult = reparentMarkdownHeading(
                        content = editSnapshot.content,
                        headings = currentHeadings,
                        movingIndex = movingIndex,
                        parentIndex = parentIndex,
                    )
                    if (reparentResult == null) {
                        Toast.makeText(context, "没有可调整的标题层级", Toast.LENGTH_SHORT).show()
                    } else {
                        val updatedSnapshot = editSnapshot.copy(content = reparentResult.content)
                        editorController.replaceAll(reparentResult.content, reparentResult.selection)
                        prepareMarkdownMindMap(reparentResult.content).also { result ->
                            mindMapHeadings = result.headings
                            mindMapUnavailableTitle = result.unavailableTitle
                            mindMapUnavailableMessage = result.unavailableMessage
                        }
                        markEditorDirty()
                        if (!isEditing) {
                            renderPreviewSnapshot(updatedSnapshot)
                        }
                        Toast.makeText(
                            context,
                            "已将「${reparentResult.movedTitle}」移动到「${reparentResult.parentTitle}」下",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onNodeAddChild = { parentIndex ->
                    val editSnapshot = editorController.getSnapshot()
                    val prepareResult = prepareMarkdownMindMap(editSnapshot.content)
                    if (prepareResult.unavailableTitle != null) {
                        mindMapHeadings = prepareResult.headings
                        mindMapUnavailableTitle = prepareResult.unavailableTitle
                        mindMapUnavailableMessage = prepareResult.unavailableMessage
                    } else {
                        val addResult = addMarkdownHeadingChild(
                            content = editSnapshot.content,
                            headings = prepareResult.headings,
                            parentIndex = parentIndex,
                        )
                        if (addResult == null) {
                            Toast.makeText(context, "当前节点不能继续添加子节点", Toast.LENGTH_SHORT).show()
                        } else {
                            val updatedSnapshot = editSnapshot.copy(content = addResult.content)
                            editorController.replaceAll(addResult.content, addResult.selection)
                            prepareMarkdownMindMap(addResult.content).also { result ->
                                mindMapHeadings = result.headings
                                mindMapUnavailableTitle = result.unavailableTitle
                                mindMapUnavailableMessage = result.unavailableMessage
                            }
                            markEditorDirty()
                            if (!isEditing) {
                                renderPreviewSnapshot(updatedSnapshot)
                            }
                            Toast.makeText(
                                context,
                                "已在「${addResult.parentTitle}」下添加子节点",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                },
            )
        }
        if (showDrawingPad) {
            DrawingPadScreen(
                modifier = Modifier.zIndex(10f),
                onDismiss = {
                    val shouldCloseEditor = closeEditorWhenDashboardDrawingDismissed
                    closeEditorWhenDashboardDrawingDismissed = false
                    editingDrawingReference = null
                    editingDrawingSource = null
                    showDrawingPad = false
                    if (shouldCloseEditor) {
                        leaveEditor()
                    }
                },
                initialDrawingSource = editingDrawingSource,
                onSave = { bitmap, drawingSource -> saveDrawingImage(bitmap, drawingSource) },
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
                onUpdate = { remark, content ->
                    val updatedContent = content.trim()
                    if (updatedContent.isNotBlank()) {
                        viewModel.updateNoteRemark(remark.id, updatedContent) {
                            noteRemarkRefreshVersion++
                        }
                        Toast.makeText(context, "备注已更新", Toast.LENGTH_SHORT).show()
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


private fun noteSearchSnippetForLog(text: String, start: Int, end: Int): String {
    if (start < 0 || end <= start || start >= text.length) return ""
    return text.substring(start.coerceAtLeast(0), end.coerceAtMost(text.length))
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .take(32)
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


private data class MindMapPrepareResult(
    val headings: List<MarkdownHeading>,
    val unavailableTitle: String? = null,
    val unavailableMessage: String? = null,
)

private fun blockedLargeMindMapResult(contentLength: Int): MindMapPrepareResult =
    MindMapPrepareResult(
        headings = emptyList(),
        unavailableTitle = "笔记过大",
        unavailableMessage = "当前笔记约 ${contentLength.coerceAtLeast(0)} 字，已停止生成思维导图，避免误触后卡死。",
    )

private fun prepareMarkdownMindMap(content: String): MindMapPrepareResult {
    if (content.length > KardLeafContentLimits.MIND_MAP_MAX_CONTENT_CHARS) {
        return blockedLargeMindMapResult(content.length)
    }
    val headings = extractMarkdownHeadings(content)
    if (headings.size > KardLeafContentLimits.MIND_MAP_MAX_HEADING_COUNT) {
        return MindMapPrepareResult(
            headings = emptyList(),
            unavailableTitle = "节点过多",
            unavailableMessage = "当前笔记检测到 ${headings.size} 个标题节点，已停止生成思维导图，避免 WebView 渲染过重。",
        )
    }
    val nonStandardReason = validateStandardMindMapHeadings(headings)
    if (nonStandardReason != null) {
        return MindMapPrepareResult(
            headings = emptyList(),
            unavailableTitle = "非标准思维导图格式",
            unavailableMessage = nonStandardReason,
        )
    }
    return MindMapPrepareResult(headings = headings)
}

private fun validateStandardMindMapHeadings(headings: List<MarkdownHeading>): String? {
    if (headings.isEmpty()) {
        return "当前笔记没有检测到标准 Markdown 标题。思维导图需要使用 # 一级节点、## 二级节点、### 三级节点这类结构。"
    }
    val first = headings.first()
    if (first.level != 1) {
        return "第 ${first.lineIndex + 1} 行不是一级标题。标准思维导图需要从 # 一级节点开始。"
    }
    headings.zipWithNext().forEach { (previous, current) ->
        if (current.level > previous.level + 1) {
            return "第 ${current.lineIndex + 1} 行标题层级跳级。请不要从 H${previous.level} 直接跳到 H${current.level}。"
        }
    }
    return null
}


private data class MindMapReparentResult(
    val content: String,
    val selection: TextRange,
    val movedTitle: String,
    val parentTitle: String,
)

private data class MindMapAddChildResult(
    val content: String,
    val selection: TextRange,
    val parentTitle: String,
)

private fun addMarkdownHeadingChild(
    content: String,
    headings: List<MarkdownHeading>,
    parentIndex: Int,
): MindMapAddChildResult? {
    val parent = if (parentIndex >= 0) headings.getOrNull(parentIndex) ?: return null else null
    val childLevel = ((parent?.level ?: 0) + 1).coerceIn(1, 6)
    if (parent != null && parent.level >= 6) return null

    val parentSubtreeEnd = if (parent == null) headings.size else findMarkdownHeadingSubtreeEnd(headings, parentIndex)
    val insertAt = if (parent == null) {
        content.length
    } else {
        headings.getOrNull(parentSubtreeEnd)
            ?.let { findLineStart(content, it.startOffset) }
            ?: content.length
    }.coerceIn(0, content.length)

    val newTitle = "新节点"
    val marker = "#".repeat(childLevel) + " "
    val prefix = when {
        insertAt == 0 -> ""
        content.getOrNull(insertAt - 1) == '\n' || content.getOrNull(insertAt - 1) == '\r' -> ""
        else -> "\n"
    }
    val suffix = when {
        insertAt >= content.length -> "\n"
        content.getOrNull(insertAt) == '\n' || content.getOrNull(insertAt) == '\r' -> ""
        else -> "\n"
    }
    val insertion = prefix + marker + newTitle + suffix
    val updatedContent = content.substring(0, insertAt) + insertion + content.substring(insertAt)
    val titleStart = insertAt + prefix.length + marker.length
    val titleEnd = titleStart + newTitle.length

    return MindMapAddChildResult(
        content = updatedContent,
        selection = TextRange(titleStart, titleEnd),
        parentTitle = parent?.text?.ifBlank { "未命名节点" } ?: "根节点",
    )
}

private fun reparentMarkdownHeading(
    content: String,
    headings: List<MarkdownHeading>,
    movingIndex: Int,
    parentIndex: Int,
): MindMapReparentResult? {
    val moving = headings.getOrNull(movingIndex) ?: return null
    val parent = if (parentIndex >= 0) headings.getOrNull(parentIndex) else null
    if (parentIndex == movingIndex) return null

    val movingSubtreeEnd = findMarkdownHeadingSubtreeEnd(headings, movingIndex)
    if (parentIndex in movingIndex until movingSubtreeEnd) return null

    val blockStart = findLineStart(content, moving.startOffset)
    val blockEnd = headings.getOrNull(movingSubtreeEnd)
        ?.let { findLineStart(content, it.startOffset) }
        ?: content.length
    if (blockStart !in 0..blockEnd || blockEnd > content.length) return null

    val targetSubtreeEnd = if (parent == null) {
        headings.size
    } else {
        findMarkdownHeadingSubtreeEnd(headings, parentIndex)
    }
    val targetEnd = if (parent == null) {
        content.length
    } else {
        headings.getOrNull(targetSubtreeEnd)
            ?.let { findLineStart(content, it.startOffset) }
            ?: content.length
    }
    if (targetEnd in (blockStart + 1) until blockEnd) return null

    val targetLevel = ((parent?.level ?: 0) + 1).coerceIn(1, 6)
    val levelDelta = targetLevel - moving.level
    val originalBlock = content.substring(blockStart, blockEnd)
    val updatedBlock = if (levelDelta == 0) {
        originalBlock
    } else {
        adjustMarkdownHeadingLevelsInBlock(
            block = originalBlock,
            contentBlockStart = blockStart,
            headings = headings.subList(movingIndex, movingSubtreeEnd),
            levelDelta = levelDelta,
        )
    }

    val withoutBlock = content.removeRange(blockStart, blockEnd)
    val blockLength = blockEnd - blockStart
    val insertAt = (if (targetEnd > blockStart) targetEnd - blockLength else targetEnd)
        .coerceIn(0, withoutBlock.length)
    if (insertAt == blockStart && updatedBlock == originalBlock) return null

    val updatedContent = buildString(content.length - originalBlock.length + updatedBlock.length) {
        append(withoutBlock.substring(0, insertAt))
        append(updatedBlock)
        append(withoutBlock.substring(insertAt))
    }
    val movedHeadingOffsetInBlock = moving.startOffset - blockStart
    val newHeadingStart = (insertAt + movedHeadingOffsetInBlock).coerceIn(0, updatedContent.length)
    return MindMapReparentResult(
        content = updatedContent,
        selection = TextRange(newHeadingStart, newHeadingStart),
        movedTitle = moving.text.ifBlank { "未命名节点" },
        parentTitle = parent?.text?.ifBlank { "未命名节点" } ?: "根节点",
    )
}

private fun findMarkdownHeadingSubtreeEnd(
    headings: List<MarkdownHeading>,
    index: Int,
): Int {
    val heading = headings.getOrNull(index) ?: return headings.size
    for (cursor in index + 1 until headings.size) {
        if (headings[cursor].level <= heading.level) return cursor
    }
    return headings.size
}

private fun findLineStart(
    content: String,
    offset: Int,
): Int {
    var cursor = offset.coerceIn(0, content.length)
    while (cursor > 0 && content[cursor - 1] != '\n' && content[cursor - 1] != '\r') {
        cursor--
    }
    return cursor
}

private fun adjustMarkdownHeadingLevelsInBlock(
    block: String,
    contentBlockStart: Int,
    headings: List<MarkdownHeading>,
    levelDelta: Int,
): String {
    val builder = StringBuilder(block)
    headings.sortedByDescending { it.startOffset }.forEach { heading ->
        val markerStart = (heading.startOffset - contentBlockStart).coerceIn(0, builder.length)
        var markerEnd = markerStart
        while (markerEnd < builder.length && builder[markerEnd] == '#') {
            markerEnd++
        }
        if (markerEnd > markerStart) {
            val targetLevel = (heading.level + levelDelta).coerceIn(1, 6)
            builder.replace(markerStart, markerEnd, "#".repeat(targetLevel))
        }
    }
    return builder.toString()
}

private fun formatEditorFileInfoText(
    date: Date,
    charCount: Int,
    folder: String,
): String {
    val folderText = folder
        .replace("\\", "/")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: "未分类"
    val timeText = SimpleDateFormat("yyyy/M/d HH:mm", Locale.getDefault()).format(date)
    return "$timeText | ${charCount.coerceAtLeast(0)} 字 | $folderText"
}


