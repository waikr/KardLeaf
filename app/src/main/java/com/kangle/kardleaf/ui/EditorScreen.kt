package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.utils.EditorOpenSession
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.editor.*
import com.kangle.kardleaf.ui.editor.api.EditorFastScrollMetrics
import com.kangle.kardleaf.ui.editor.codemirror.CodeMirrorWebViewScrollController
import com.kangle.kardleaf.ui.editor.codemirror.KardLeafCodeMirrorEditor
import com.kangle.kardleaf.ui.editor.codemirror.KardLeafCodeMirrorImage
import com.kangle.kardleaf.ui.editor.host.EditorFastScrollEdgeView
import com.kangle.kardleaf.ui.editor.host.EditorFastScrollSignal
import com.kangle.kardleaf.ui.editor.host.NoteOutlineSidePanel
import com.kangle.kardleaf.ui.editor.host.NoteRemarkSidePanel
import com.kangle.kardleaf.ui.editor.host.PreviewWebView
import com.kangle.kardleaf.ui.editor.host.PreviewWebViewController
import com.kangle.kardleaf.ui.editor.host.ToolbarIconButton
import com.kangle.kardleaf.ui.editor.host.toggleTask
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorController
import com.kangle.kardleaf.ui.editor.native.KardLeafEditorSnapshot
import com.kangle.kardleaf.ui.editor.native.KardLeafNativeEditor
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadEditor
import com.kangle.kardleaf.ui.editor.history.NoteInfoDialog
import com.kangle.kardleaf.data.utils.KardLeafContentLimits
import com.kangle.kardleaf.data.repository.RoomNoteRepository
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.ui.text.TextRange
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
import com.kangle.kardleaf.localizedText
import com.kangle.kardleaf.data.ai.KardLeafAiAction
import com.kangle.kardleaf.data.ai.KardLeafAiClient
import com.kangle.kardleaf.data.ai.KardLeafAiPreferences
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.database.NoteLinkEntity
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeMode
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EDITOR_TRACE_TAG = "KardLeafEditorTrace"
private const val CODEMIRROR_DEBUG_TRACE_TAG = "KardLeafCM6Trace"
private const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
private const val OPEN_PATH_PROBE_TAG = "KardLeafOpenPathProbe"
private const val USER_PERF_TRACE_TAG = "KardLeafUserPerf"

private data class PendingCodeMirrorEditSwitch(
    val switchId: Int,
    val anchor: EditorViewportAnchor,
    val source: String,
    val startedAt: Long,
    val requestFocus: Boolean,
)

internal fun isPreviewRenderReadyForRequest(
    requestSignature: Triple<Int, Int, String>,
    lastRequestedSignature: Triple<Int, Int, String>?,
    visibleSignature: Pair<Int, Int>,
    lastRenderedSignature: Pair<Int, Int>?,
): Boolean =
    lastRequestedSignature == requestSignature &&
        lastRenderedSignature == visibleSignature

private const val EDITOR_GESTURE_TAG = "KardLeafGestureTrace"
private const val BACK_TRACE_TAG = "KardLeafBackTrace"
private const val EDITOR_TOP_BAR_TRACE_TAG = "KardLeafEditorTopBar"
private const val MIND_MAP_GESTURE_TRACE_TAG = "KardLeafMindMapGestureTrace"
private const val SEARCH_TRACE_TAG = "KardLeafSearchTrace"
private const val SAVE_PATH_TRACE_TAG = "KardLeafSavePath"
private const val TITLE_TRACE_TAG = "KardLeafTitleTrace"
private const val CODEMIRROR_IME_TRACE_TAG = "KardLeafCM6ImeTrace"
private const val NATIVE_EDITOR_LAYOUT_TAG = "KardLeafEditorLayout"
private const val EDIT_ENTER_TRACE_TAG = "KardLeafEditEnterTrace"
private const val EDITOR_FRAME_TAG = "KardLeafEditorFrame"
private const val MODE_SWITCH_TRACE_TAG = "KardLeafModeSwitch"
private const val PREVIEW_CHAIN_TRACE_TAG = "KardLeafPreviewChain"
private val CODEMIRROR_IME_OUTER_TRACE_ENABLED: Boolean
    get() = KardLeafLog.isEnabled(CODEMIRROR_IME_TRACE_TAG)
private const val MENU_REOPEN_GUARD_MS = 250L
private const val OPENING_PROGRESS_DELAY_MS = 220L
private const val DIRECT_EDIT_MAX_CHARS = 600_000
private const val WEBVIEW_PREVIEW_MAX_CHARS = 300_000
private const val USER_PERF_LARGE_NOTE_MIN_CHARS = 50_000
private const val LARGE_TEXT_PREVIEW_CHUNK_CHARS = 300

private fun KardLeafAiAction.localizedTitle(): String =
    when (this) {
        KardLeafAiAction.SUMMARIZE -> localizedText("AI 摘要", "AI summary")
        KardLeafAiAction.POLISH -> localizedText("AI 润色", "AI polish")
        KardLeafAiAction.EXPAND -> localizedText("AI 扩写", "AI expand")
        KardLeafAiAction.CONTINUE -> localizedText("AI 续写", "AI continue")
        KardLeafAiAction.SHORTEN -> localizedText("缩短内容", "Shorten")
        KardLeafAiAction.FIX_WRITING -> localizedText("纠错与语病修复", "Proofread")
        KardLeafAiAction.TRANSLATE -> localizedText("翻译", "Translate")
        KardLeafAiAction.EXPLAIN -> localizedText("解释内容", "Explain")
        KardLeafAiAction.KEY_POINTS -> localizedText("提取要点", "Extract key points")
        KardLeafAiAction.ACTION_ITEMS -> localizedText("提取待办", "Extract action items")
        KardLeafAiAction.GENERATE_TITLE -> localizedText("生成标题", "Generate title")
        KardLeafAiAction.CUSTOM -> localizedText("自定义指令", "Custom instruction")
    }

private enum class KardLeafAiTextScope {
    SELECTION,
    PARAGRAPH,
    SECTION,
    WHOLE_NOTE,
}

private data class KardLeafAiTextRange(val start: Int, val end: Int) {
    val length: Int get() = (end - start).coerceAtLeast(0)
}

private data class KardLeafAiDiffPreview(
    val removed: String,
    val added: String,
    val unchangedPrefixChars: Int,
    val unchangedSuffixChars: Int,
)

private fun KardLeafAiTextScope.localizedTitle(): String = when (this) {
    KardLeafAiTextScope.SELECTION -> localizedText("选中文字", "Selection")
    KardLeafAiTextScope.PARAGRAPH -> localizedText("当前段落", "Paragraph")
    KardLeafAiTextScope.SECTION -> localizedText("当前章节", "Section")
    KardLeafAiTextScope.WHOLE_NOTE -> localizedText("整篇笔记", "Whole note")
}

private fun KardLeafAiAction.showsDiffPreview(): Boolean = when (this) {
    KardLeafAiAction.POLISH,
    KardLeafAiAction.EXPAND,
    KardLeafAiAction.SHORTEN,
    KardLeafAiAction.FIX_WRITING,
    KardLeafAiAction.TRANSLATE,
    KardLeafAiAction.CUSTOM -> true
    else -> false
}

private fun findParagraphRange(content: String, cursor: Int): KardLeafAiTextRange {
    if (content.isEmpty()) return KardLeafAiTextRange(0, 0)
    val safeCursor = cursor.coerceIn(0, content.length)
    val start = content.lastIndexOf("\n\n", (safeCursor - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 2 }
    val end = content.indexOf("\n\n", safeCursor)
        .let { if (it < 0) content.length else it }
    return KardLeafAiTextRange(start.coerceAtMost(end), end)
}

private fun markdownHeadingLevel(line: String): Int? {
    val level = line.takeWhile { it == '#' }.length
    return level.takeIf { it in 1..6 && line.getOrNull(it)?.isWhitespace() == true }
}

private fun findMarkdownSectionRange(content: String, cursor: Int): KardLeafAiTextRange {
    if (content.isEmpty()) return KardLeafAiTextRange(0, 0)
    val safeCursor = cursor.coerceIn(0, content.length)
    var lineStart = if (safeCursor == 0) 0 else content.lastIndexOf('\n', (safeCursor - 1).coerceAtLeast(0)) + 1
    var headingStart = -1
    var headingLevel = -1
    while (true) {
        val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
        val level = markdownHeadingLevel(content.substring(lineStart, lineEnd))
        if (level != null) {
            headingStart = lineStart
            headingLevel = level
            break
        }
        if (lineStart == 0) break
        lineStart = content.lastIndexOf('\n', (lineStart - 2).coerceAtLeast(0)) + 1
    }
    if (headingStart < 0) return findParagraphRange(content, safeCursor)

    var nextLineStart = content.indexOf('\n', headingStart).let { if (it < 0) content.length else it + 1 }
    while (nextLineStart < content.length) {
        val lineEnd = content.indexOf('\n', nextLineStart).let { if (it < 0) content.length else it }
        val level = markdownHeadingLevel(content.substring(nextLineStart, lineEnd))
        if (level != null && level <= headingLevel) {
            return KardLeafAiTextRange(headingStart, nextLineStart)
        }
        nextLineStart = if (lineEnd >= content.length) content.length else lineEnd + 1
    }
    return KardLeafAiTextRange(headingStart, content.length)
}

private fun findAiTextRange(snapshot: KardLeafEditorSnapshot, scope: KardLeafAiTextScope): KardLeafAiTextRange {
    val content = snapshot.content
    val selectionStart = minOf(snapshot.selection.start, snapshot.selection.end).coerceIn(0, content.length)
    val selectionEnd = maxOf(snapshot.selection.start, snapshot.selection.end).coerceIn(selectionStart, content.length)
    val cursor = snapshot.selection.end.coerceIn(0, content.length)
    return when (scope) {
        KardLeafAiTextScope.SELECTION -> KardLeafAiTextRange(selectionStart, selectionEnd)
        KardLeafAiTextScope.PARAGRAPH -> findParagraphRange(content, cursor)
        KardLeafAiTextScope.SECTION -> findMarkdownSectionRange(content, cursor)
        KardLeafAiTextScope.WHOLE_NOTE -> KardLeafAiTextRange(0, content.length)
    }
}

private fun buildAiDiffPreview(original: String, revised: String): KardLeafAiDiffPreview {
    var prefix = 0
    val sharedLength = minOf(original.length, revised.length)
    while (prefix < sharedLength && original[prefix] == revised[prefix]) prefix++
    var suffix = 0
    while (
        suffix < original.length - prefix &&
        suffix < revised.length - prefix &&
        original[original.length - 1 - suffix] == revised[revised.length - 1 - suffix]
    ) {
        suffix++
    }
    return KardLeafAiDiffPreview(
        removed = original.substring(prefix, original.length - suffix),
        added = revised.substring(prefix, revised.length - suffix),
        unchangedPrefixChars = prefix,
        unchangedSuffixChars = suffix,
    )
}

private fun aiPreviewText(text: String, maxChars: Int = 2400): String =
    if (text.length <= maxChars) text else text.take(maxChars) + localizedText("\n……内容过长，已省略预览", "\n… Preview truncated")

@Composable
private fun EditorFileInfoText(
    date: Date,
    charCount: androidx.compose.runtime.State<Int>,
    folder: String,
) {
    val text = remember(date.time, charCount.value, folder) {
        formatEditorFileInfoText(
            date = date,
            charCount = charCount.value,
            folder = folder,
        )
    }
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onLeavingEditorStart: () -> Unit = {},
    editorOpenSession: EditorOpenSession? = null,
    onEditorFrameCommitted: (Long) -> Unit = {},
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
    val wikilinkPrompt by viewModel.wikilinkPrompt.collectAsState()
    val pendingEditorSearchJump by viewModel.pendingEditorSearchJump.collectAsState()
    val pendingEditorEditNoteId by viewModel.pendingEditorEditNoteId.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState(initial = emptyList())
    val noteLinkPath = currentNote?.file?.path.orEmpty()
    val outgoingWikilinks by remember(noteLinkPath) {
        if (noteLinkPath.isBlank()) flowOf(emptyList<NoteLinkEntity>()) else viewModel.outgoingWikilinks(noteLinkPath)
    }.collectAsState(initial = emptyList())
    val backlinks by remember(noteLinkPath) {
        if (noteLinkPath.isBlank()) flowOf(emptyList<NoteLinkEntity>()) else viewModel.backlinks(noteLinkPath)
    }.collectAsState(initial = emptyList())
    val externalDraft by viewModel.externalNoteDraft.collectAsState()
    val isEditorOpen by viewModel.isEditorOpen.collectAsState()
    val isOpeningNoteContent by viewModel.isOpeningNoteContent.collectAsState()
    val isShowingPartialLargeNote by viewModel.isShowingPartialLargeNote.collectAsState()
    val isPrivacyEditor = privacyDocumentKey != null
    val isNewRegularNote = !isPrivacyEditor && currentNote == null
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
    var editorKernel by remember { mutableStateOf(notePrefsManager.getEditorKernel()) }
    val autoCodeMirrorThresholdChars = notePrefsManager.getAutoCodeMirrorThresholdChars()
    val codeMirrorLivePreviewEnabled = notePrefsManager.isCodeMirrorLivePreviewEnabled()
    val editingImagePreviewEnabled = notePrefsManager.isEditingImagePreviewEnabled()
    val editorFontSizeSp = notePrefsManager.getEditorFontSizeSp()
    val editorLineHeightMultiplier = notePrefsManager.getEditorLineHeightMultiplier()
    val editorLetterSpacingSp = notePrefsManager.getEditorLetterSpacingSp()
    val editorParagraphSpacingDp = notePrefsManager.getEditorParagraphSpacingDp()
    val editorFontFamily = notePrefsManager.getEditorFontFamily()
    val previewThemeId = notePrefsManager.getPreviewTheme().name.lowercase()
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
    fun isQuickNoteFolder(folder: String?): Boolean =
        folder == PrefsManager.DEFAULT_QUICK_NOTE_FOLDER_NAME ||
            folder == PrefsManager.LEGACY_DRAFT_FOLDER_NAME
    val hideQuickNoteTitleInEditor = !isPrivacyEditor && (
        isQuickNoteFolder(currentNote?.folder) ||
            isQuickNoteFolder(externalDraft?.folder) ||
            (currentNote == null && externalDraft == null && isQuickNoteFolder(initialLabel))
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
    val initialTitle = if (hideQuickNoteTitleInEditor || hideInitialTitleInEditor) "" else rawInitialTitle
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
    val mindMapStateKey: Any = editorOpenSession?.sessionId ?: editorDocumentKey
    val isMindMapNoteFromFile = currentNote?.noteType.equals(NoteFormatUtils.NOTE_TYPE_MINDMAP, ignoreCase = true)
    var mindMapModeActivated by remember(editorDocumentKey) { mutableStateOf(false) }
    var mindMapDisplayTitle by remember(editorDocumentKey) { mutableStateOf("") }
    val isMindMapNote = isMindMapNoteFromFile || mindMapModeActivated
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
    var switchedEditorSnapshot by remember(editorDocumentKey) { mutableStateOf<KardLeafEditorSnapshot?>(null) }
    val editorSurfaceTitle = switchedEditorSnapshot?.title ?: displayInitialTitle
    val editorSurfaceContent = switchedEditorSnapshot?.content ?: initialContent
    val editorSurfaceSelection = switchedEditorSnapshot?.selection ?: defaultEditOpenSelection
    val editorContentLength = remember(editorDocumentKey, editorSurfaceContent.length) {
        mutableStateOf(editorSurfaceContent.length)
    }
    val hasMarkdownImages = remember(editorSurfaceContent) { containsMarkdownImageReferences(editorSurfaceContent) }
    val isManualCodeMirrorKernel = editorKernel == PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW
    val usesQuillpadStyleEditor = !isPrivacyEditor && editorKernel == PrefsManager.EditorKernel.QUILLPAD_STYLE
    val allowsCodeMirrorForThisNote = isManualCodeMirrorKernel || !hasMarkdownImages
    val usesCodeMirrorLikeEditor =
        !isPrivacyEditor &&
            allowsCodeMirrorForThisNote &&
            isManualCodeMirrorKernel
    val usesExternalEditorSnapshot = usesCodeMirrorLikeEditor
    LaunchedEffect(editorKernel, isPrivacyEditor, hasMarkdownImages, editorSurfaceContent.length) {
        KardLeafLog.d(
            "KardLeafCodeMirror",
            "screen editor kernel=$editorKernel useCodeMirror=$usesCodeMirrorLikeEditor " +
                "useQuillpadStyle=$usesQuillpadStyleEditor " +
                "autoSwitch=false hasImages=$hasMarkdownImages threshold=$autoCodeMirrorThresholdChars " +
                "contentLen=${editorSurfaceContent.length} privacy=$isPrivacyEditor",
        )
    }
    editorController.acceptInitialSnapshot(
        editorDocumentKey,
        switchedEditorSnapshot?.title ?: initialTitle,
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
    var renderedPreview by remember(editorDocumentKey) {
        mutableStateOf(
            when {
                initialContent.length > WEBVIEW_PREVIEW_MAX_CHARS -> ""
                initialTitle.isBlank() -> initialContent
                else -> "# $initialTitle\n\n$initialContent"
            },
        )
    }
    var largePlainPreviewSnapshot by remember(editorDocumentKey) { mutableStateOf<KardLeafEditorSnapshot?>(null) }
    var previewRenderToken by remember(editorDocumentKey) { mutableStateOf(0) }
    var previewScrollRatio by remember(editorDocumentKey) { mutableStateOf(0f) }
    var pendingPreviewScrollRatio by remember(editorDocumentKey) { mutableStateOf<Float?>(null) }
    var pendingPreviewSwitch by remember(editorDocumentKey) { mutableStateOf<Triple<Int, EditorViewportAnchor, Long>?>(null) }
    var pendingCodeMirrorEditSwitch by remember(editorDocumentKey) { mutableStateOf<PendingCodeMirrorEditSwitch?>(null) }
    var modeSwitchSequence by remember(editorDocumentKey) { mutableStateOf(0) }
    var activeModeSwitchId by remember(editorDocumentKey) { mutableStateOf(0) }
    var committedModeSwitchId by remember(editorDocumentKey) { mutableStateOf(0) }

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
    val keepsModeSurfacesAlive = usesQuillpadStyleEditor || usesCodeMirrorLikeEditor
    var editorSurfaceCreated by remember(editorDocumentKey) {
        mutableStateOf(defaultOpenNoteMode == KardLeafCustomFeatures.OpenNoteMode.EDIT)
    }
    var previewSurfaceCreated by remember(editorDocumentKey) {
        mutableStateOf(defaultOpenNoteMode != KardLeafCustomFeatures.OpenNoteMode.EDIT)
    }
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
    val fallbackUserPerfOpenStartMs = remember(editorDocumentKey) { SystemClock.elapsedRealtime() }
    val userPerfOpenStartMs = editorOpenSession?.humanStartRealtimeMs ?: fallbackUserPerfOpenStartMs
    var userPerfScreenComposedLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    var userPerfContentReadyLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    var userPerfAreaFirstFrameLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    var userPerfFirstContentLaidOutLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    var userPerfRenderedLogged by remember(editorDocumentKey) { mutableStateOf(false) }
    val quillpadRecomposeCount = remember(editorDocumentKey) { AtomicInteger() }
    if (effectiveEditorOpen && usesQuillpadStyleEditor) {
        SideEffect {
            KardLeafLog.d(
                "KardLeafQuillpadIme",
                "composeRecomposition count=${quillpadRecomposeCount.incrementAndGet()} contentLen=$userPerfContentLen " +
                    (editorOpenSession?.trace(userPerfContentLen) ?: "sessionId=-1 documentKey=${editorDocumentKey.hashCode()}"),
            )
        }
    }
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
    var editEnterTraceStartMs by remember(editorDocumentKey) { mutableStateOf(0L) }
    var editEnterTraceRun by remember(editorDocumentKey) { mutableStateOf(0) }
    var editEntrySelection by remember(editorDocumentKey) { mutableStateOf<TextRange?>(null) }
    LaunchedEffect(editEnterTraceRun) {
        if (editEnterTraceRun <= 0 || editEnterTraceStartMs <= 0L) return@LaunchedEffect
        val run = editEnterTraceRun
        val start = editEnterTraceStartMs
        val engine = if (usesCodeMirrorLikeEditor) "CODEMIRROR" else if (usesQuillpadStyleEditor) "QUILLPAD" else "NATIVE"
        KardLeafLog.d(
            EDIT_ENTER_TRACE_TAG,
            "stateObserved run=$run engine=$engine elapsed=${SystemClock.elapsedRealtime() - start}ms contentLen=$userPerfContentLen",
        )
        var previousFrame = withFrameNanos { it }
        val codeMirrorViewport =
            if (usesCodeMirrorLikeEditor) {
                " scrollTop=${codeMirrorScrollController.getScrollTop()} hasFocus=${codeMirrorScrollController.hasFocus()}"
            } else {
                ""
            }
        KardLeafLog.d(
            EDIT_ENTER_TRACE_TAG,
            "firstEditFrame run=$run engine=$engine elapsed=${SystemClock.elapsedRealtime() - start}ms$codeMirrorViewport",
        )
        var frameCount = 0
        var slowFrameCount = 0
        var maxFrameMs = 0L
        while (SystemClock.elapsedRealtime() - start < 2_500L) {
            val frame = withFrameNanos { it }
            val frameMs = ((frame - previousFrame) / 1_000_000L).coerceAtLeast(0L)
            previousFrame = frame
            frameCount += 1
            if (frameMs > 24L) slowFrameCount += 1
            maxFrameMs = maxOf(maxFrameMs, frameMs)
        }
        KardLeafLog.d(
            EDITOR_FRAME_TAG,
            "summary run=$run engine=$engine elapsed=${SystemClock.elapsedRealtime() - start}ms frames=$frameCount " +
                "slowFrames=$slowFrameCount maxFrame=${maxFrameMs}ms contentLen=$userPerfContentLen",
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
    var lastRequestedPreviewSignature by remember(editorDocumentKey) { mutableStateOf<Triple<Int, Int, String>?>(null) }
    val visiblePreviewContent =
        when {
            showsLargePlainTextPreview -> renderedPreview
            !isOpeningNoteContent && !isEditing && previewRenderToken == 0 ->
                if (initialTitle.isBlank()) initialContent else "# $initialTitle\n\n$initialContent"
            else -> renderedPreview
        }
    val visiblePreviewSignature = visiblePreviewContent.length to visiblePreviewContent.hashCode()
    val openingContentProgressPending =
        isOpeningNoteContent ||
            (!isEditing &&
                openingPreviewRenderPending &&
                visiblePreviewContent.isNotEmpty() &&
                lastRenderedPreviewSignature != visiblePreviewSignature)
    var showOpeningContentProgress by remember(editorDocumentKey) { mutableStateOf(false) }
    LaunchedEffect(editorDocumentKey, openingContentProgressPending) {
        showOpeningContentProgress = false
        if (openingContentProgressPending) {
            delay(OPENING_PROGRESS_DELAY_MS)
            showOpeningContentProgress = true
        }
    }

    fun userPerfModeName(): String = when {
        isEditing && usesCodeMirrorLikeEditor -> "codeMirror"
        isEditing && usesQuillpadStyleEditor -> "quillpadStyle"
        isEditing && !blocksDirectEditForLargeNote -> "nativeEditor"
        showsLargePlainTextPreview -> "largePlainPreview"
        else -> "markdownPreview"
    }

    fun userPerfEngineName(): String = when {
        usesCodeMirrorLikeEditor -> "CODEMIRROR"
        usesQuillpadStyleEditor -> "QUILLPAD_STYLE"
        else -> "NATIVE"
    }

    fun logUserPerfOpenStep(step: String, mode: String = userPerfModeName()) {
        if (!isUserPerfTrackedNote) return
        KardLeafLog.d(
            USER_PERF_TRACE_TAG,
            "editorOpen $step elapsed=${SystemClock.elapsedRealtime() - userPerfOpenStartMs}ms " +
                "engine=${userPerfEngineName()} mode=$mode " +
                "contentLen=$userPerfContentLen sizeTier=$userPerfSizeTier " +
                "isLarge=$isUserPerfLargeNote isOpening=$isOpeningNoteContent partialLarge=$isShowingPartialLargeNote " +
                "largeBlocked=$blocksDirectEditForLargeNote plainLargePreview=$usesLargePlainTextPreview " +
                "path=${currentNote?.file?.path} " +
                (editorOpenSession?.trace(userPerfContentLen) ?: "sessionId=-1"),
        )
    }

    fun userPerfAreaFirstFrameModifier(mode: String): Modifier =
        if (!isUserPerfTrackedNote) {
            Modifier
        } else {
            Modifier.onGloballyPositioned {
                if (!userPerfAreaFirstFrameLogged) {
                    userPerfAreaFirstFrameLogged = true
                    logUserPerfOpenStep("layoutPositioned", mode)
                }
            }
        }

    // UI state
    val aiPreferences = remember { KardLeafAiPreferences(context) }
    val aiClient = remember { KardLeafAiClient() }
    val aiSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAiPanel by remember { mutableStateOf(false) }
    var aiRequestSnapshot by remember(editorDocumentKey) { mutableStateOf<KardLeafEditorSnapshot?>(null) }
    var aiRequestRange by remember(editorDocumentKey) { mutableStateOf<KardLeafAiTextRange?>(null) }
    var aiTextScope by remember(editorDocumentKey) { mutableStateOf(KardLeafAiTextScope.PARAGRAPH) }
    var aiCustomInstruction by remember { mutableStateOf("") }
    var aiFollowUpInstruction by remember { mutableStateOf("") }
    var aiRunning by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<String?>(null) }
    var aiOriginalInput by remember { mutableStateOf("") }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiResultAction by remember { mutableStateOf<KardLeafAiAction?>(null) }
    var aiLastCustomInstruction by remember { mutableStateOf("") }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(editorDocumentKey) {
        onDispose {
            aiJob?.cancel()
            aiClient.cancelActiveRequest()
        }
    }
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
    var isDownloadingWebImages by remember(editorDocumentKey) { mutableStateOf(false) }
    var showDownloadWebImagesAction by remember(editorDocumentKey) { mutableStateOf(false) }
    var showWebClipImportDialog by remember(editorDocumentKey) { mutableStateOf(false) }
    var webImageProgress by remember(editorDocumentKey) { mutableStateOf<WebClipImageProgress?>(null) }
    var editingDrawingReference by remember(editorDocumentKey) { mutableStateOf<String?>(null) }
    var editingDrawingSource by remember(editorDocumentKey) { mutableStateOf<String?>(null) }
    var previewImageTargets by remember(editorDocumentKey) { mutableStateOf(emptyList<KardLeafImageClickTarget>()) }
    var viewingImageTarget by remember(editorDocumentKey) { mutableStateOf<KardLeafImageClickTarget?>(null) }
    var viewerResource by remember(editorDocumentKey) { mutableStateOf<RoomNoteRepository.ImageViewerResource?>(null) }
    var viewerLoading by remember(editorDocumentKey) { mutableStateOf(false) }
    var editingImageResource by remember(editorDocumentKey) { mutableStateOf<RoomNoteRepository.ImageEditorResource?>(null) }
    var editingImageTarget by remember(editorDocumentKey) { mutableStateOf<KardLeafImageClickTarget?>(null) }
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
    val editorFastScrollEdgeWidth = 16.dp
    val editorFastScrollBottomPadding = with(density) {
        (
            imeInsets.getBottom(density) -
                navigationBarsInsets.getBottom(density)
        ).coerceAtLeast(0).toDp()
    }
    val noteSidePanelEditorReserveRadiusPx = with(density) { 48.dp.toPx() }
    var noteSidePanelGestureRootX by remember { mutableStateOf(0f) }
    var noteSidePanelGestureRootY by remember { mutableStateOf(0f) }
    val latestNoteSidePanelOffsetPx by rememberUpdatedState(noteSidePanelOffsetPx)
    var isKeyboardVisible by remember { mutableStateOf(false) }
    var outlineHeadings by remember { mutableStateOf<List<MarkdownHeading>>(emptyList()) }
    var showMindMap by remember(mindMapStateKey) { mutableStateOf(false) }
    var mindMapAutoOpenHandled by remember(mindMapStateKey) { mutableStateOf(false) }
    var mindMapDocument by remember(mindMapStateKey) { mutableStateOf<MindMapDocument?>(null) }
    var mindMapUnavailableTitle by remember(mindMapStateKey) { mutableStateOf<String?>(null) }
    var mindMapUnavailableMessage by remember(mindMapStateKey) { mutableStateOf<String?>(null) }
    var mindMapInitialEditIndex by remember(mindMapStateKey) { mutableStateOf<Int?>(null) }
    var shouldShowBottomToolbar by remember { mutableStateOf(false) }
    var editorFocusRequestToken by remember(editorDocumentKey) { mutableStateOf(0) }
    var hasRequestedNewNoteKeyboard by remember(editorDocumentKey) { mutableStateOf(false) }

    fun handleEditorFocusRequest(token: Int) {
        if (editorFocusRequestToken == token) {
            editorFocusRequestToken = 0
            KardLeafLog.d(EDITOR_TRACE_TAG, "new note keyboard request consumed key=$editorDocumentKey token=$token")
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
        var quillpadImeFirstVisibleLogged by remember(editorDocumentKey) { mutableStateOf(false) }
        var quillpadImeStableBottom by remember(editorDocumentKey) { mutableStateOf(-1) }
        LaunchedEffect(imeVisible) {
            isKeyboardVisible = imeVisible
        }
        LaunchedEffect(
            imeBottomPx,
            effectiveEditorOpen,
            usesQuillpadStyleEditor,
        ) {
            if (!effectiveEditorOpen || !usesQuillpadStyleEditor) return@LaunchedEffect
            val trace =
                editorOpenSession?.trace(userPerfContentLen)
                    ?: "sessionId=-1 documentKey=${editorDocumentKey.hashCode()} actualLength=$userPerfContentLen"
            if (imeBottomPx > 0 && !quillpadImeFirstVisibleLogged) {
                quillpadImeFirstVisibleLogged = true
                KardLeafLog.d(
                    "KardLeafQuillpadIme",
                    "imeAnimationStart source=compose imeBottom=$imeBottomPx $trace",
                )
                KardLeafLog.d(
                    "KardLeafQuillpadIme",
                    "imeFirstVisible source=compose imeBottom=$imeBottomPx $trace",
                )
            }
            KardLeafLog.d(
                "KardLeafQuillpadIme",
                "imeInsetProgress source=compose imeBottom=$imeBottomPx visible=${imeBottomPx > 0} $trace",
            )
            delay(160L)
            if (quillpadImeStableBottom != imeBottomPx) {
                quillpadImeStableBottom = imeBottomPx
                KardLeafLog.d(
                    "KardLeafQuillpadIme",
                    "imeStable source=compose imeBottom=$imeBottomPx visible=${imeBottomPx > 0} $trace",
                )
            }
            if (imeBottomPx == 0) quillpadImeFirstVisibleLogged = false
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
            if (effectiveEditorOpen && !usesCodeMirrorLikeEditor) {
                KardLeafLog.d(
                    NATIVE_EDITOR_LAYOUT_TAG,
                    "compose insets reason=imeChanged imeBottom=$imeBottomPx imeVisible=$imeVisible " +
                        "editing=$isEditing showBars=$showBars bottomToolbar=$shouldShowBottomToolbar " +
                        "expanded=$isBottomToolbarExpanded toolbarDrag=$toolbarDragFraction key=$editorDocumentKey",
                )
            }
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
        isEditing,
        isOpeningNoteContent,
        isNewRegularNote,
        isNewPrivacyNote,
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
                "hideQuickNoteTitle=$hideQuickNoteTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
                "folder=$folder currentFolder=${currentNote?.folder} draftFolder=${externalDraft?.folder} initialLabel=$initialLabel " +
                "hiddenRules=${editorHiddenFilenamePatterns.size} dateFormat=$editorUnnamedNoteDateFormat",
        )
        KardLeafLog.d(
            EDITOR_TRACE_TAG,
            "screen state key=$editorDocumentKey open=$effectiveEditorOpen editing=$isEditing " +
                "notePath=${currentNote?.file?.path} noteTitleLen=${currentNote?.title?.length ?: -1} " +
                "noteContentLen=${currentNote?.content?.length ?: -1} notePreviewLen=${currentNote?.contentPreview?.length ?: -1} " +
                "draftContentLen=${externalDraft?.content?.length ?: -1} initialTitleLen=${initialTitle.length} " +
                "displayInitialTitleLen=${displayInitialTitle.length} initialContentLen=${initialContent.length} " +
                "newRegular=$isNewRegularNote newPrivacy=$isNewPrivacyNote keyboardRequested=$hasRequestedNewNoteKeyboard",
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
        if ((isNewRegularNote || isNewPrivacyNote) && effectiveEditorOpen) {
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "new note keyboard gate key=$editorDocumentKey requested=$hasRequestedNewNoteKeyboard " +
                    "regular=$isNewRegularNote privacy=$isNewPrivacyNote editing=$isEditing opening=$isOpeningNoteContent",
            )
        }
        if (
            !hasRequestedNewNoteKeyboard &&
            (isNewRegularNote || isNewPrivacyNote) &&
            effectiveEditorOpen &&
            isEditing &&
            !isOpeningNoteContent
        ) {
            editorFocusRequestToken = 1
            hasRequestedNewNoteKeyboard = true
            KardLeafLog.d(
                EDITOR_TRACE_TAG,
                "new note keyboard requested key=$editorDocumentKey token=$editorFocusRequestToken kernel=$editorKernel",
            )
        }
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
    fun buildCurrentNote(
        snapshot: KardLeafEditorSnapshot = editorController.getSnapshot(),
        noteTypeOverride: String? = null,
    ): Note {
        val snapshotTitleForSave = when {
            hideQuickNoteTitleInEditor && snapshot.title.isBlank() -> currentNote?.title.orEmpty()
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
                    "snapshotContentHash=${snapshot.content.hashCode()} " +
                    "hideQuickNoteTitle=$hideQuickNoteTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
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
            sourceType = currentNote?.sourceType ?: externalDraft?.sourceType,
            sourceUrl = currentNote?.sourceUrl ?: externalDraft?.sourceUrl,
            noteType = noteTypeOverride ?: if (isMindMapNoteFromFile || mindMapModeActivated) {
                NoteFormatUtils.NOTE_TYPE_MINDMAP
            } else {
                currentNote?.noteType
            },
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
            context.showToast(localizedText("当前笔记为空，无法添加到隐私库", "This note is empty and cannot be protected"))
            return
        }
        viewModel.moveNoteToPrivacy(note, privacyTitle, privacyContent) { moved ->
            context.showToast(if (moved) localizedText("已移动到隐私库", "Moved to protected notes") else localizedText("移动到隐私库失败", "Could not move to protected notes"))
            if (moved) {
                onMoved()
            }
        }
    }

    fun saveNote(
        saveHistory: Boolean = false,
        showToast: Boolean = false,
        noteTypeOverride: String? = null,
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
                    "contentLen=${snapshot.content.length} contentHash=${snapshot.content.hashCode()} " +
                    "selection=${snapshot.selection} attached=${editorController.editorView != null}",
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
                        if (showToast) context.showToast(localizedText("已保存", "Saved"))
                        KardLeafLog.d(
                            EDITOR_TRACE_TAG,
                            "savePrivacyNote dispatched key=$editorDocumentKey id=$effectivePrivacyNoteId elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                    } else {
                        if (showToast) context.showToast(localizedText("没有需要保存的修改", "No changes to save"))
                        KardLeafLog.d(EDITOR_TRACE_TAG, "savePrivacyNote skipped unchanged key=$editorDocumentKey elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                    }
                    finishSave()
                    return
                }

                val note = buildCurrentNote(snapshot, noteTypeOverride)
                val savedTitle = when {
                    hideQuickNoteTitleInEditor && snapshot.title.isBlank() -> currentNote?.title.orEmpty()
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
                            currentNote?.noteType != note.noteType ||
                            folder != (currentNote?.folder ?: "")
                    }
                KardLeafLog.d(
                    SAVE_PATH_TRACE_TAG,
                    "saveNote decision key=$editorDocumentKey source=$source saveHistory=$saveHistory " +
                        "currentPath=${currentNote?.file?.path} currentTitle=${currentNote?.title} " +
                        "notePath=${note.file.path} noteTitle=${note.title} savedTitle=$savedTitle " +
                        "snapshotTitleLen=${snapshot.title.length} snapshotContentLen=${snapshot.content.length} " +
                        "snapshotContentHash=${snapshot.content.hashCode()} " +
                        "currentContentLen=${currentNote?.content?.length ?: -1} folder=$folder " +
                        "currentContentHash=${currentNote?.content?.hashCode() ?: 0} currentFolder=${currentNote?.folder} " +
                        "isChanged=$isChanged editorDirty=${viewModel.editorDirty.value}",
                )
                if (isChanged) {
                    viewModel.saveNote(note, currentNote?.file, saveHistory = saveHistory)
                    if (showToast) context.showToast(localizedText("已保存", "Saved"))
                    KardLeafLog.d(
                        EDITOR_TRACE_TAG,
                        "saveNote dispatched key=$editorDocumentKey source=$source changed=true elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                    )
                } else {
                    if (showToast) context.showToast(localizedText("没有需要保存的修改", "No changes to save"))
                    KardLeafLog.d(EDITOR_TRACE_TAG, "saveNote skipped unchanged key=$editorDocumentKey source=$source elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                }
                finishSave()
            } else {
                if (showToast) context.showToast(localizedText("当前笔记为空，未保存", "The note is empty and was not saved"))
                KardLeafLog.w(EDITOR_TRACE_TAG, "saveNote skipped empty snapshot key=$editorDocumentKey source=$source elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                finishSave()
            }
        }

        if (usesExternalEditorSnapshot && editorController.requestExternalSnapshot { snapshot ->
                saveSnapshot(snapshot, if (usesCodeMirrorLikeEditor) "codemirror" else "quillpad-style")
            }
        ) {
            KardLeafLog.d(EDITOR_TRACE_TAG, "saveNote requested external editor snapshot key=$editorDocumentKey kernel=$editorKernel saveHistory=$saveHistory")
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

    fun openAiAssistant(snapshot: KardLeafEditorSnapshot) {
        if (isPrivacyEditor) {
            context.showToast(localizedText("隐私笔记不会发送到外部 AI", "Protected notes are not sent to external AI"))
            return
        }
        val config = aiPreferences.load()
        if (!config.isConfigured) {
            context.showToast(localizedText("请先在设置中配置 AI 助手", "Configure the AI assistant in Settings first"))
            return
        }
        if (snapshot.content.isBlank()) {
            context.showToast(localizedText("当前没有可处理的文本", "There is no text to process"))
            return
        }
        val hasSelection = snapshot.selection.start != snapshot.selection.end
        aiRequestSnapshot = snapshot
        aiTextScope = if (hasSelection) KardLeafAiTextScope.SELECTION else KardLeafAiTextScope.PARAGRAPH
        aiRequestRange = null
        aiCustomInstruction = ""
        aiFollowUpInstruction = ""
        aiError = null
        aiResult = null
        aiOriginalInput = ""
        aiResultAction = null
        showAiPanel = true
    }

    fun requestAiSnapshot() {
        if (usesExternalEditorSnapshot && editorController.requestExternalSnapshot { snapshot -> openAiAssistant(snapshot) }) {
            KardLeafLog.d(EDITOR_TRACE_TAG, "AI requested external snapshot key=$editorDocumentKey kernel=$editorKernel")
        } else {
            openAiAssistant(editorController.getSnapshot())
        }
    }

    fun startAiRequest(
        action: KardLeafAiAction,
        customInstruction: String,
        sourceText: String,
        sourceRange: KardLeafAiTextRange,
        revisionResult: String? = null,
        revisionInstruction: String = "",
    ) {
        aiJob?.cancel()
        aiClient.cancelActiveRequest()
        aiRequestRange = sourceRange
        aiRunning = true
        aiError = null
        aiResult = null
        aiResultAction = action
        if (revisionResult == null) {
            aiOriginalInput = sourceText
            aiLastCustomInstruction = customInstruction
        }
        aiJob = coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    if (revisionResult == null) {
                        aiClient.execute(
                            config = aiPreferences.load(),
                            action = action,
                            input = sourceText,
                            customInstruction = customInstruction,
                        )
                    } else {
                        aiClient.revise(
                            config = aiPreferences.load(),
                            originalInput = sourceText,
                            currentResult = revisionResult,
                            instruction = revisionInstruction,
                        )
                    }
                }
                if (result.isBlank()) {
                    aiError = localizedText("AI 返回了空内容", "AI returned empty content")
                } else {
                    aiResult = result
                }
            } catch (error: Throwable) {
                if (isActive) {
                    aiError = error.message ?: localizedText("AI 请求失败", "AI request failed")
                }
            } finally {
                aiRunning = false
            }
        }
    }

    fun runAiAction(action: KardLeafAiAction, customInstruction: String = "") {
        val snapshot = aiRequestSnapshot ?: return
        val range = findAiTextRange(snapshot, aiTextScope)
        if (range.length <= 0) {
            context.showToast(localizedText("当前范围没有可处理的文本", "The selected scope has no text"))
            return
        }
        val sourceText = snapshot.content.substring(range.start, range.end)
        if (sourceText.isBlank()) {
            context.showToast(localizedText("当前范围没有可处理的文本", "The selected scope has no text"))
            return
        }
        startAiRequest(action, customInstruction, sourceText, range)
    }

    fun rerunAiAction() {
        val snapshot = aiRequestSnapshot ?: return
        val range = aiRequestRange ?: return
        val action = aiResultAction ?: return
        if (range.end > snapshot.content.length) return
        startAiRequest(
            action = action,
            customInstruction = aiLastCustomInstruction,
            sourceText = snapshot.content.substring(range.start, range.end),
            sourceRange = range,
        )
    }

    fun continueEditingAiResult() {
        val currentResult = aiResult?.takeIf { it.isNotBlank() } ?: return
        val range = aiRequestRange ?: return
        val action = aiResultAction ?: return
        val instruction = aiFollowUpInstruction.trim()
        if (instruction.isBlank()) return
        aiFollowUpInstruction = ""
        startAiRequest(
            action = action,
            customInstruction = aiLastCustomInstruction,
            sourceText = aiOriginalInput,
            sourceRange = range,
            revisionResult = currentResult,
            revisionInstruction = instruction,
        )
    }

    fun stopAiRequest() {
        aiJob?.cancel()
        aiClient.cancelActiveRequest()
        aiRunning = false
    }

    fun copyAiResult() {
        val result = aiResult?.takeIf { it.isNotBlank() } ?: return
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("KardLeaf AI", result))
        context.showToast(localizedText("AI 结果已复制", "AI result copied"))
    }

    fun applyAiResult(replaceOriginal: Boolean) {
        val source = aiRequestSnapshot ?: return
        val sourceRange = aiRequestRange ?: return
        val result = aiResult?.takeIf { it.isNotBlank() } ?: return
        if (!isEditing) {
            context.showToast(localizedText("请先进入编辑模式", "Enter edit mode first"))
            return
        }
        if (editorController.getText() != source.content) {
            context.showToast(localizedText("AI 处理期间正文已变化，请复制结果后手动粘贴", "The note changed while AI was running. Copy and paste the result manually."))
            return
        }
        if (replaceOriginal) {
            editorController.setSelection(sourceRange.start, sourceRange.end)
            editorController.replaceSelection(result)
        } else {
            val insertAt = sourceRange.end.coerceIn(0, source.content.length)
            val prefix = if (insertAt > 0 && source.content.getOrNull(insertAt - 1) != '\n') "\n\n" else ""
            editorController.setSelection(insertAt)
            editorController.replaceSelection(prefix + result)
        }
        markEditorDirty()
        aiResult = null
        aiRequestSnapshot = null
        aiRequestRange = null
        showAiPanel = false
    }

    fun switchEditorKernel(targetKernel: PrefsManager.EditorKernel) {
        showMoreMenu = false
        showLabelMenu = false
        showHeadingMenu = false
        showMathMenu = false
        if (targetKernel == editorKernel) return
        fun applySnapshot(snapshot: KardLeafEditorSnapshot) {
            editorController.updateExternalTitle(snapshot.title)
            editorController.updateExternalContentSnapshot(snapshot.content, snapshot.selection)
            switchedEditorSnapshot = snapshot
            notePrefsManager.saveEditorKernel(targetKernel)
            editorKernel = targetKernel
            KardLeafLog.d(
                "KardLeafCodeMirror",
                "editor kernel switched and saved target=$targetKernel key=$editorDocumentKey " +
                    "titleLen=${snapshot.title.length} contentLen=${snapshot.content.length} selection=${snapshot.selection}",
            )
        }
        if (usesExternalEditorSnapshot && editorController.requestExternalSnapshot { snapshot ->
                applySnapshot(snapshot)
            }
        ) {
            KardLeafLog.d("KardLeafCodeMirror", "editor kernel switch requested external snapshot target=$targetKernel key=$editorDocumentKey kernel=$editorKernel")
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

    fun refreshMindMapFromEditor() {
        if (!showMindMap) return
        prepareMarkdownMindMap(editorController.getText()).also { result ->
            mindMapDocument = result.document
            mindMapUnavailableTitle = result.unavailableTitle
            mindMapUnavailableMessage = result.unavailableMessage
        }
    }

    fun prepareCurrentMindMap(): MindMapDocument? {
        val result = prepareMarkdownMindMap(editorController.getText())
        mindMapDocument = result.document
        mindMapUnavailableTitle = result.unavailableTitle
        mindMapUnavailableMessage = result.unavailableMessage
        return result.document
    }

    fun insertAtCursor(
        prefix: String,
        suffix: String = "",
    ) {
        editorController.insertAtCursor(prefix, suffix)
        markEditorDirty()
    }

    fun runEditorCommand(
        command: String,
        vararg args: Any,
    ): Boolean {
        val handled = editorController.executeCommand(command, *args)
        if (handled) markEditorDirty()
        return handled
    }

    fun insertAtCursorOrCommand(
        prefix: String,
        suffix: String = "",
        command: String? = null,
        vararg args: Any,
    ) {
        if (command != null && runEditorCommand(command, *args)) return
        insertAtCursor(prefix, suffix)
    }

    fun applyHeadingAtCursor(level: Int) {
        if (runEditorCommand("toggleHeading", level)) return
        insertAtCursor("#".repeat(level.coerceIn(1, 6)) + " ")
    }

    fun changeIndent(increase: Boolean) {
        val command = when {
            usesCodeMirrorLikeEditor -> if (increase) "indentMore" else "indentLess"
            else -> if (increase) "indent" else "outdent"
        }
        if (editorController.executeCommand(command)) markEditorDirty()
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
            context.showToast("当前页面暂不支持选择图片")
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
                    context.showToast(importBlockMessage, Toast.LENGTH_LONG)
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
        editingImageResource = null
        editingImageTarget = null
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
                    context.showToast("这张绘图没有可编辑数据")
                }
                return@launch
            }
            editingDrawingReference = reference
            editingDrawingSource = source
            editingImageResource = null
            editingImageTarget = null
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

    fun handleImageClicked(target: KardLeafImageClickTarget) {
        KardLeafLog.d(
            "KardLeafImageTrace",
            "image click source=${target.source} reference=${target.reference} " +
                "range=${target.markdownStart ?: -1}..${target.markdownEndExclusive ?: -1} occurrence=${target.occurrenceIndex}",
        )
        noteSearchFocused = false
        noteReplaceFocused = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        viewerResource = null
        viewerLoading = true
        viewingImageTarget = target
        showLabelMenu = false
        showMoreMenu = false
        showHeadingMenu = false
        showMathMenu = false
    }

    LaunchedEffect(viewingImageTarget, folder) {
        val target = viewingImageTarget ?: return@LaunchedEffect
        viewerLoading = true
        val loaded = viewModel.loadImageViewerResource(folder, target.reference)
        if (viewingImageTarget == target) {
            viewerResource = loaded ?: RoomNoteRepository.ImageViewerResource(
                reference = target.reference,
                bitmap = null,
                mimeType = null,
                sourceWidth = 0,
                sourceHeight = 0,
                exifOrientation = 0,
                documentType = "missing",
                drawingSource = null,
                editable = false,
                errorMessage = "找不到图片",
            )
            viewerLoading = false
        }
    }

    fun openImageEditor(
        target: KardLeafImageClickTarget,
        resource: RoomNoteRepository.ImageViewerResource,
    ) {
        coroutineScope.launch {
            val editorResource = viewModel.loadImageEditorResource(folder, resource)
            if (editorResource == null) {
                context.showToast("无法打开图片标注")
                return@launch
            }
            editingImageTarget = target
            editingImageResource = editorResource.takeUnless { it.mode == "drawing" }
            editingDrawingReference = editorResource.openedReference.takeIf { editorResource.mode == "drawing" }
            editingDrawingSource = editorResource.drawingSource
            viewingImageTarget = null
            viewerResource = null
            viewerLoading = false
            noteSearchFocused = false
            noteReplaceFocused = false
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            showDrawingPad = true
        }
    }

    fun renderPreviewSnapshot(snapshot: KardLeafEditorSnapshot) {
        val contentLength = maxOf(initialContent.length, snapshot.content.length)
        if (!isNewPrivacyNote && contentLength > WEBVIEW_PREVIEW_MAX_CHARS) {
            val token = previewRenderToken + 1
            previewRenderToken = token
            renderedPreview = ""
            largePlainPreviewSnapshot = snapshot
            previewImageTargets = emptyList()
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
        val requestSignature = Triple(markdown.length, markdown.hashCode(), folder)
        KardLeafLog.d(
            PREVIEW_CHAIN_TRACE_TAG,
            "renderRequest key=$editorDocumentKey rawLen=${requestSignature.first} rawHash=${requestSignature.second} " +
                "folderHash=${folder.hashCode()} requested=${lastRequestedPreviewSignature?.let { "${it.first}/${it.second}" } ?: "none"} " +
                "rendered=${lastRenderedPreviewSignature?.let { "${it.first}/${it.second}" } ?: "none"} " +
                "visible=${visiblePreviewSignature.first}/${visiblePreviewSignature.second} " +
                "pending=${pendingPreviewSwitch?.first ?: 0} editing=$isEditing",
        )
        if (lastRequestedPreviewSignature == requestSignature) {
            KardLeafLog.w(
                PREVIEW_CHAIN_TRACE_TAG,
                "renderSkippedDuplicate key=$editorDocumentKey rawLen=${markdown.length} rawHash=${markdown.hashCode()} " +
                    "rendered=${lastRenderedPreviewSignature?.let { "${it.first}/${it.second}" } ?: "none"} " +
                    "visible=${visiblePreviewSignature.first}/${visiblePreviewSignature.second} " +
                    "pending=${pendingPreviewSwitch?.first ?: 0} editing=$isEditing",
            )
            return
        }
        lastRequestedPreviewSignature = requestSignature
        previewImageTargets = extractPreviewImageClickTargets(snapshot.content)
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
                "contentLen=${snapshot.content.length} markdownLen=${markdown.length} images=${previewImageTargets.size}",
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

    fun applyMindMapEdit(
        snapshot: KardLeafEditorSnapshot,
        result: MindMapEditResult?,
        refreshPreview: Boolean = true,
    ): Boolean {
        if (result == null || result.content == snapshot.content) return false
        editorController.setSelection(0, snapshot.content.length)
        editorController.replaceSelection(result.content)
        editorController.setSelection(result.selection.start, result.selection.end)
        syncUndoRedoState()
        prepareMarkdownMindMap(result.content).also { prepared ->
            mindMapDocument = prepared.document
            mindMapUnavailableTitle = prepared.unavailableTitle
            mindMapUnavailableMessage = prepared.unavailableMessage
        }
        markEditorDirty()
        if (refreshPreview && !showMindMap && !isEditing) {
            renderPreviewSnapshot(snapshot.copy(content = result.content, selection = result.selection))
        }
        return true
    }

    fun downloadWebImages(note: Note) {
        if (isDownloadingWebImages) return
        showMoreMenu = false

        fun startDownload(snapshot: KardLeafEditorSnapshot) {
            KardLeafLog.i(
                WEB_CLIP_LOG_TAG,
                "late image backup start notePath=${note.file.path} contentLen=${snapshot.content.length}",
            )
            isDownloadingWebImages = true
            webImageProgress = null
            coroutineScope.launch {
                try {
                    val result =
                        localizeRemoteMarkdownImages(
                            context = context,
                            markdown = snapshot.content,
                            targetFolder = folder,
                            importImage = viewModel::importImage,
                            onImageProgress = { progress ->
                                withContext(Dispatchers.Main.immediate) {
                                    webImageProgress = progress
                                }
                            },
                        )
                    if (result.totalImages == 0) {
                        showDownloadWebImagesAction = false
                        context.showToast("当前笔记没有可下载的网络图片")
                        return@launch
                    }
                    if (result.savedImages == 0) {
                        context.showToast("网页图片下载失败，正文中的网络链接未修改")
                        return@launch
                    }

                    val updatedSelection =
                        TextRange(
                            snapshot.selection.start.coerceIn(0, result.markdown.length),
                            snapshot.selection.end.coerceIn(0, result.markdown.length),
                        )
                    val updatedSnapshot =
                        snapshot.copy(
                            content = result.markdown,
                            selection = updatedSelection,
                        )
                    editorController.replaceAll(updatedSnapshot.content, updatedSnapshot.selection)
                    showDownloadWebImagesAction = NoteFormatUtils.hasRemoteMarkdownImage(updatedSnapshot.content)
                    markEditorDirty()
                    if (!isEditing) {
                        renderPreviewSnapshot(updatedSnapshot)
                    }
                    viewModel.saveNote(
                        buildCurrentNote(updatedSnapshot),
                        note.file,
                        saveHistory = true,
                    )
                    val message =
                        if (result.failedImages > 0) {
                            "已下载 ${result.savedImages} 张，${result.failedImages} 张仍保留网络链接"
                        } else {
                            "已下载 ${result.savedImages} 张网页图片"
                        }
                    context.showToast(message)
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    KardLeafLog.e(
                        WEB_CLIP_LOG_TAG,
                        "late image backup failed notePath=${note.file.path}",
                        error,
                    )
                    context.showToast("网页图片下载失败")
                } finally {
                    isDownloadingWebImages = false
                    webImageProgress = null
                }
            }
        }

        if (usesExternalEditorSnapshot && editorController.requestExternalSnapshot { snapshot ->
                startDownload(snapshot)
            }
        ) {
            KardLeafLog.d(
                WEB_CLIP_LOG_TAG,
                "late image backup requested external snapshot key=$editorDocumentKey kernel=$editorKernel",
            )
        } else {
            startDownload(editorController.getSnapshot())
        }
    }

    fun saveDrawingImage(bitmap: android.graphics.Bitmap, drawingSource: String) {
        coroutineScope.launch {
            val imageResource = editingImageResource
            if (imageResource != null) {
                val result = viewModel.saveImageAnnotation(folder, imageResource, bitmap, drawingSource)
                if (result == null) {
                    context.showToast("图片标注保存失败")
                    return@launch
                }
                if (result.newlyCreated) {
                    val target = editingImageTarget
                    val snapshot = editorController.getSnapshot()
                    val replacement =
                        target?.let {
                            replaceClickedMarkdownImageReference(snapshot.content, it, result.reference)
                        }
                    if (replacement == null) {
                        KardLeafLog.w(
                            "KardLeafImageTrace",
                            "markdown reference replace failed occurrence=${target?.occurrenceIndex ?: -1} orphan=true",
                        )
                        context.showToast("标注已保存，但原图片位置已变化，未替换正文引用", Toast.LENGTH_LONG)
                    } else {
                        val previousSelection = snapshot.selection
                        editorController.setSelection(replacement.replaceStart, replacement.replaceEndExclusive)
                        editorController.replaceSelection(result.reference)
                        val delta =
                            result.reference.length - (replacement.replaceEndExclusive - replacement.replaceStart)

                        fun adjusted(position: Int): Int =
                            when {
                                position <= replacement.replaceStart -> position
                                position >= replacement.replaceEndExclusive -> position + delta
                                else -> replacement.replaceStart + result.reference.length
                            }
                        editorController.setSelection(
                            adjusted(previousSelection.start),
                            adjusted(previousSelection.end),
                        )
                        switchedEditorSnapshot = editorController.getSnapshot()
                        markEditorDirty()
                        syncUndoRedoState()
                        KardLeafLog.d(
                            "KardLeafImageTrace",
                            "markdown reference replace expected=${target.markdownStart ?: -1}..${target.markdownEndExclusive ?: -1} " +
                                "resolved=${replacement.replaceStart}..${replacement.replaceEndExclusive} " +
                                "occurrence=${target.occurrenceIndex} success=true",
                        )
                    }
                }
                editingImageResource = null
                editingImageTarget = null
                editingDrawingReference = null
                editingDrawingSource = null
                showDrawingPad = false
                editorController.refreshInlineImagePreviews()
                renderPreviewSnapshot(editorController.getSnapshot())
                return@launch
            }
            val editingReference = editingDrawingReference
            if (editingReference != null) {
                val saved = viewModel.updateDrawingImage(bitmap, drawingSource, folder, editingReference)
                if (saved) {
                    closeEditorWhenDashboardDrawingDismissed = false
                    editingDrawingReference = null
                    editingDrawingSource = null
                    editingImageTarget = null
                    showDrawingPad = false
                    editorController.refreshInlineImagePreviews()
                    renderPreviewSnapshot(editorController.getSnapshot())
                } else {
                    context.showToast("画图保存失败")
                }
                return@launch
            }

            val markdown = viewModel.importDrawingImage(bitmap, drawingSource, folder)
            if (markdown.isNotBlank()) {
                closeEditorWhenDashboardDrawingDismissed = false
                insertImageMarkdown(markdown)
                showDrawingPad = false
            } else {
                context.showToast("画图保存失败")
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
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    fun undoContent() {
        if (!canUndo && !editorController.canUndo()) return
        editorController.undo()
        refreshMindMapFromEditor()
        syncUndoRedoState()
        markEditorDirty()
    }

    fun redoContent() {
        if (!canRedo && !editorController.canRedo()) return
        editorController.redo()
        refreshMindMapFromEditor()
        syncUndoRedoState()
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
        val searchHadFocus = noteSearchFocused || noteReplaceFocused
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
        if (searchHadFocus) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
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
            editorController.executeCommand("selectRange", summary.currentStart, summary.currentEnd)
            coroutineScope.launch {
                withFrameNanos { }
                delay(60)
                editorController.executeCommand("selectRange", summary.currentStart, summary.currentEnd)
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
            context.showToast("请输入要查找的文本")
            return
        }
        if (!isEditing) {
            context.showToast("请先切换到编辑状态再替换")
            return
        }
        val snapshot = editorController.getSnapshot()
        val text = snapshot.content
        val result = buildNoteSearchMatches(text, query, noteSearchUseRegex, noteSearchMatchCase)
        if (result.errorMessage != null) {
            updateSearchState(query, -1, text)
            context.showToast(result.errorMessage)
            return
        }
        val rawStart = minOf(snapshot.selection.start, snapshot.selection.end).coerceIn(0, text.length)
        val rawEnd = maxOf(snapshot.selection.start, snapshot.selection.end).coerceIn(0, text.length)
        val replaceMatch = result.matches.firstOrNull { it.start == rawStart && it.end == rawEnd }
            ?: result.matches.firstOrNull { it.start == noteSearchCurrentStart && it.end == noteSearchCurrentEnd }
            ?: result.matches.firstOrNull { it.start >= rawStart }
            ?: result.matches.firstOrNull()
        if (replaceMatch == null) {
            context.showToast("没有找到要替换的文本")
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
            context.showToast(replacement.errorMessage)
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
            context.showToast("请输入要查找的文本")
            return
        }
        if (!isEditing) {
            context.showToast("请先切换到编辑状态再替换")
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
            context.showToast(replacement.errorMessage)
            return
        }
        val newText = replacement.text ?: snapshot.content
        if (replacement.count <= 0) {
            context.showToast("没有找到要替换的文本")
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
        context.showToast("已替换 ${replacement.count} 处")
    }

    fun jumpToHeading(heading: MarkdownHeading) {
        val text = editorController.getText()
        val target = heading.startOffset.coerceIn(0, text.length)
        showNoteInfoDialog = false
        closeNoteSearch()
        closeNoteSidePanel()
        if (isEditing) {
            editorController.setSelection(target)
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
        val jumpMatches = buildNoteSearchMatches(
            text = initialContent,
            query = jump.query,
            useRegex = false,
            matchCase = false,
        ).matches
        val preferredMatch = jumpMatches.firstOrNull { it.start == jump.preferredStart }
        if (jump.preferredStart >= 0 && preferredMatch == null && isOpeningNoteContent) {
            return@LaunchedEffect
        }
        val matchIndex = preferredMatch?.start
            ?: jumpMatches.firstOrNull()?.start
            ?: -1
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

    if (showWebClipImportDialog && isNewRegularNote) {
        WebClipImportDialog(
            onDismiss = { showWebClipImportDialog = false },
            onImported = { draft ->
                showWebClipImportDialog = false
                viewModel.createNote(draft, source = "editor_web_clip")
            },
            targetFolder = folder,
            importImage = viewModel::importImage,
        )
    }

    if (isDownloadingWebImages) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("下载网页图片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (webImageProgress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("正在检查正文中的网络图片……")
                    } else {
                        WebClipImageProgressContent(webImageProgress)
                    }
                }
            },
            confirmButton = {},
        )
    }

    if (showAiPanel) {
        val snapshot = aiRequestSnapshot
        val hasSelection = snapshot?.let { it.selection.start != it.selection.end } == true
        val selectedRange = snapshot?.let { findAiTextRange(it, aiTextScope) }
        val primaryActions = if (hasSelection && aiTextScope == KardLeafAiTextScope.SELECTION) {
            listOf(
                KardLeafAiAction.POLISH,
                KardLeafAiAction.FIX_WRITING,
                KardLeafAiAction.SHORTEN,
                KardLeafAiAction.TRANSLATE,
            )
        } else {
            listOf(
                KardLeafAiAction.SUMMARIZE,
                KardLeafAiAction.KEY_POINTS,
                KardLeafAiAction.GENERATE_TITLE,
                KardLeafAiAction.CONTINUE,
            )
        }
        val secondaryActions = KardLeafAiAction.values().filter {
            it != KardLeafAiAction.CUSTOM && it !in primaryActions
        }
        ModalBottomSheet(
            onDismissRequest = {
                if (aiRunning) stopAiRequest()
                showAiPanel = false
            },
            sheetState = aiSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = aiResultAction?.localizedTitle() ?: localizedText("AI 助手", "AI assistant"),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = localizedText(
                                "${aiTextScope.localizedTitle()} · ${selectedRange?.length ?: 0} 个字符",
                                "${aiTextScope.localizedTitle()} · ${selectedRange?.length ?: 0} characters",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (aiRunning) stopAiRequest()
                            showAiPanel = false
                        },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = localizedText("关闭", "Close"))
                    }
                }

                Text(
                    text = localizedText("处理范围", "Scope"),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KardLeafAiTextScope.values().forEach { scope ->
                        FilterChip(
                            selected = aiTextScope == scope,
                            enabled = !aiRunning && (scope != KardLeafAiTextScope.SELECTION || hasSelection),
                            onClick = {
                                aiTextScope = scope
                                aiResult = null
                                aiError = null
                                aiResultAction = null
                                aiRequestRange = null
                            },
                            label = { Text(scope.localizedTitle()) },
                        )
                    }
                }

                if (aiRunning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = localizedText("正在处理，可随时停止", "Processing. You can stop at any time."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { stopAiRequest() }) {
                        Text(localizedText("停止生成", "Stop generating"))
                    }
                } else {
                    aiError?.let { errorMessage ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = localizedText("AI 请求失败", "AI request failed"),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text(errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }

                    val resultText = aiResult?.takeIf { it.isNotBlank() }
                    if (resultText == null) {
                        Text(
                            text = localizedText("常用操作", "Quick actions"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            primaryActions.forEach { action ->
                                AssistChip(
                                    onClick = { runAiAction(action) },
                                    label = { Text(action.localizedTitle()) },
                                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null, modifier = Modifier.size(18.dp)) },
                                )
                            }
                        }
                        Text(
                            text = localizedText("更多操作", "More actions"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            secondaryActions.forEach { action ->
                                AssistChip(
                                    onClick = { runAiAction(action) },
                                    label = { Text(action.localizedTitle()) },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = aiCustomInstruction,
                            onValueChange = { aiCustomInstruction = it },
                            label = { Text(localizedText("自定义指令", "Custom instruction")) },
                            placeholder = { Text(localizedText("例如：改写成适合发布的 Markdown 教程", "Example: Rewrite as a publishable Markdown tutorial")) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            enabled = aiCustomInstruction.isNotBlank(),
                            onClick = { runAiAction(KardLeafAiAction.CUSTOM, aiCustomInstruction) },
                        ) {
                            Text(localizedText("执行自定义指令", "Run custom instruction"))
                        }
                    } else {
                        val action = aiResultAction
                        if (action?.showsDiffPreview() == true && aiOriginalInput != resultText) {
                            val diff = buildAiDiffPreview(aiOriginalInput, resultText)
                            Text(
                                text = localizedText("差异预览", "Diff preview"),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = localizedText(
                                    "相同前文 ${diff.unchangedPrefixChars} 字 · 相同后文 ${diff.unchangedSuffixChars} 字",
                                    "${diff.unchangedPrefixChars} unchanged prefix chars · ${diff.unchangedSuffixChars} unchanged suffix chars",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (diff.removed.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            localizedText("原文变化部分", "Original changed part"),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                        Text(aiPreviewText(diff.removed), color = MaterialTheme.colorScheme.onErrorContainer)
                                    }
                                }
                            }
                            if (diff.added.isNotEmpty()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            localizedText("AI 结果变化部分", "AI changed part"),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                        Text(aiPreviewText(diff.added), color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                            }
                        }

                        Text(
                            text = localizedText("完整结果", "Full result"),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 1.dp,
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Text(
                                text = resultText,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }

                        OutlinedTextField(
                            value = aiFollowUpInstruction,
                            onValueChange = { aiFollowUpInstruction = it },
                            label = { Text(localizedText("继续修改", "Refine result")) },
                            placeholder = { Text(localizedText("例如：再短一点，保留第二段", "Example: Make it shorter and keep the second paragraph")) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Button(
                                enabled = aiFollowUpInstruction.isNotBlank(),
                                onClick = { continueEditingAiResult() },
                            ) {
                                Text(localizedText("继续修改", "Refine"))
                            }
                            TextButton(onClick = { rerunAiAction() }) {
                                Text(localizedText("重新生成", "Regenerate"))
                            }
                            TextButton(onClick = { copyAiResult() }) {
                                Text(localizedText("复制", "Copy"))
                            }
                            if (isEditing && action != KardLeafAiAction.CONTINUE) {
                                TextButton(onClick = { applyAiResult(replaceOriginal = true) }) {
                                    Text(localizedText("替换当前范围", "Replace scope"))
                                }
                            }
                            if (isEditing) {
                                TextButton(onClick = { applyAiResult(replaceOriginal = false) }) {
                                    Text(localizedText("插入下方", "Insert below"))
                                }
                            }
                        }
                    }
                }
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
            outgoingLinks = outgoingWikilinks,
            backlinkLinks = backlinks,
            onDismiss = { showNoteInfoDialog = false },
            onHeadingClick = { heading -> jumpToHeading(heading) },
            onNoteClick = { path ->
                showNoteInfoDialog = false
                viewModel.openNoteByPath(path)
            },
            onWikilinkClick = { target ->
                showNoteInfoDialog = false
                viewModel.openWikilinkTarget(target, currentNote?.file?.path.orEmpty())
            },
        )
    }

    if (!isPrivacyEditor) externalConflict?.let { conflictNote ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissExternalConflict() },
            title = { Text(localizedText("文件冲突", "File conflict")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        localizedText("当前笔记有未保存修改，外部文件也发生了变化。请选择保留当前编辑内容，或使用外部版本。", "This note has unsaved changes and the external file also changed. Keep your edits or use the external version."),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = localizedText("外部版本预览：", "External version preview: ") + conflictNote.content.take(200) + if (conflictNote.content.length > 200) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExternalConflict() }) {
                    Text(localizedText("保留我的修改", "Keep my changes"))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { viewModel.applyExternalConflict() }) {
                        Text(localizedText("使用外部版本", "Use external version"))
                    }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            android.content.ClipData.newPlainText(localizedText("外部版本内容", "External version"), conflictNote.content),
                        )
                        context.showToast(localizedText("已复制外部版本内容", "External version copied"))
                        viewModel.dismissExternalConflict()
                    }) {
                        Text(localizedText("复制外部版本内容", "Copy external version"))
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
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        editorController.releaseForClose(clearText = false)
        onBack()
    }

    fun leaveEditorAfterSaveIfNeeded(source: String) {
        val shouldSave = shouldSaveEditorOnLeave()
        KardLeafLog.d(
            SAVE_PATH_TRACE_TAG,
            "leave request source=$source key=$editorDocumentKey isEditing=$isEditing " +
                "shouldSave=$shouldSave editorDirty=${viewModel.editorDirty.value} " +
                "privacyDirty=$privacyEditorDirty currentPath=${currentNote?.file?.path}",
        )
        if (!shouldSave) {
            KardLeafLog.d(SAVE_PATH_TRACE_TAG, "leave request bypass-save source=$source key=$editorDocumentKey")
            leaveEditor()
            return
        }
        saveNote(saveHistory = true) {
            KardLeafLog.d(
                SAVE_PATH_TRACE_TAG,
                "leave request save-dispatched source=$source key=$editorDocumentKey " +
                    "currentPath=${currentNote?.file?.path}",
            )
            leaveEditor()
        }
    }


    fun enterPreviewMode() {
        if (!usesCodeMirrorLikeEditor || showsLargePlainTextPreview) {
            fun applySnapshot(snapshot: KardLeafEditorSnapshot) {
                val editorMetrics =
                    if (usesCodeMirrorLikeEditor) codeMirrorScrollController.getFastScrollMetrics()
                    else editorController.getFastScrollMetrics()
                renderPreviewSnapshot(snapshot)
                pendingPreviewScrollRatio = editorMetrics.ratio.takeIf { editorMetrics.canScroll }
                pendingPreviewScrollRatio?.let(previewController::fastScrollToRatio)
                isLeavingEditor = true
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                isEditing = false
            }
            if (usesExternalEditorSnapshot && editorController.requestExternalSnapshot(::applySnapshot)) return
            applySnapshot(editorController.getSnapshot())
            return
        }

        val switchId = ++modeSwitchSequence
        activeModeSwitchId = switchId
        pendingPreviewSwitch = null
        val requestedAt = SystemClock.elapsedRealtime()
        val engine = if (usesCodeMirrorLikeEditor) "CODEMIRROR" else if (usesQuillpadStyleEditor) "QUILLPAD" else "NATIVE"
        KardLeafLog.d(
            MODE_SWITCH_TRACE_TAG,
            "request id=$switchId direction=edit_to_preview engine=$engine contentLen=$userPerfContentLen " +
                "largePlain=$showsLargePlainTextPreview",
        )
        previewSurfaceCreated = true

        fun activatePreview(anchor: EditorViewportAnchor, result: String?) {
            if (activeModeSwitchId != switchId) {
                KardLeafLog.d(MODE_SWITCH_TRACE_TAG, "drop id=$switchId direction=edit_to_preview reason=stale")
                return
            }
            isLeavingEditor = true
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            isEditing = false
            committedModeSwitchId = switchId
            KardLeafLog.d(
                MODE_SWITCH_TRACE_TAG,
                "stateChanged id=$switchId direction=edit_to_preview engine=$engine sourceOffset=${anchor.offset} " +
                    "edge=${anchor.edge} targetResult=$result elapsed=${SystemClock.elapsedRealtime() - requestedAt}ms",
            )
        }

        fun applySnapshot(snapshot: KardLeafEditorSnapshot, sourceAnchor: EditorViewportAnchor) {
            if (activeModeSwitchId != switchId) return
            val markdown = if (snapshot.title.isBlank()) snapshot.content else "# ${snapshot.title}\n\n${snapshot.content}"
            val requestSignature = Triple(markdown.length, markdown.hashCode(), folder)
            val alreadyRendered = isPreviewRenderReadyForRequest(
                requestSignature = requestSignature,
                lastRequestedSignature = lastRequestedPreviewSignature,
                visibleSignature = visiblePreviewSignature,
                lastRenderedSignature = lastRenderedPreviewSignature,
            )
            val previewAnchor = sourceAnchor.shifted(markdown.length - snapshot.content.length, markdown.length)
            KardLeafLog.d(
                PREVIEW_CHAIN_TRACE_TAG,
                "modeSwitchSourceReady id=$switchId rawLen=${markdown.length} rawHash=${markdown.hashCode()} " +
                    "requested=${lastRequestedPreviewSignature?.let { "${it.first}/${it.second}" } ?: "none"} " +
                    "rendered=${lastRenderedPreviewSignature?.let { "${it.first}/${it.second}" } ?: "none"} " +
                    "visible=${visiblePreviewSignature.first}/${visiblePreviewSignature.second} " +
                    "alreadyRendered=$alreadyRendered pending=${pendingPreviewSwitch?.first ?: 0}",
            )
            if (!alreadyRendered) renderPreviewSnapshot(snapshot)
            KardLeafLog.d(
                MODE_SWITCH_TRACE_TAG,
                "sourceReady id=$switchId direction=edit_to_preview engine=$engine sourceOffset=${sourceAnchor.offset} " +
                    "edge=${sourceAnchor.edge} targetOffset=${previewAnchor.offset} alreadyRendered=$alreadyRendered",
            )
            if (alreadyRendered) {
                previewController.scrollToAnchor(previewAnchor) { result ->
                    activatePreview(previewAnchor, result)
                }
            } else {
                pendingPreviewSwitch = Triple(switchId, previewAnchor, requestedAt)
                KardLeafLog.d(
                    MODE_SWITCH_TRACE_TAG,
                    "targetPending id=$switchId direction=edit_to_preview targetOffset=${previewAnchor.offset} edge=${previewAnchor.edge} reason=render",
                )
            }
        }

        fun captureSourceAnchor(snapshot: KardLeafEditorSnapshot) {
            codeMirrorScrollController.getViewportAnchor { anchor ->
                val fallback = EditorViewportAnchor(
                    offset = (snapshot.content.length * codeMirrorScrollController.getFastScrollMetrics().ratio).roundToInt(),
                    viewportFraction = 0.5f,
                    edge = EditorViewportEdge.CENTER,
                )
                applySnapshot(snapshot, anchor ?: fallback)
            }
        }
        if (usesExternalEditorSnapshot && editorController.requestExternalSnapshot { snapshot ->
                KardLeafLog.d(EDITOR_TRACE_TAG, "enterPreviewMode got external snapshot key=$editorDocumentKey kernel=$editorKernel contentLen=${snapshot.content.length}")
                captureSourceAnchor(snapshot)
            }
        ) {
            KardLeafLog.d(EDITOR_TRACE_TAG, "enterPreviewMode request external snapshot key=$editorDocumentKey kernel=$editorKernel")
            return
        }
        captureSourceAnchor(editorController.getSnapshot())
    }

    fun showLargeNoteEditBlockedToast() {
        context.showToast("当前笔记过大，暂时只能快速预览，避免编辑器卡死")
    }

    fun enterEditMode(
        preservePreviewPosition: Boolean = true,
        previewMarkdownOffset: Int? = null,
        requestFocus: Boolean = false,
    ) {
        val enterAt = SystemClock.elapsedRealtime()
        if (usesCodeMirrorLikeEditor && pendingCodeMirrorEditSwitch != null) {
            val pending = pendingCodeMirrorEditSwitch ?: return
            KardLeafLog.d(
                MODE_SWITCH_TRACE_TAG,
                "preview_to_edit ignored reason=surface_create_pending switchId=${pending.switchId} " +
                    "anchorOffset=${pending.anchor.offset} anchorEdge=${pending.anchor.edge} " +
                    "pendingAge=${SystemClock.elapsedRealtime() - pending.startedAt}ms",
            )
            return
        }
        if (!usesCodeMirrorLikeEditor || showsLargePlainTextPreview) {
            val snapshot = editorController.getSnapshot()
            val currentTextLength = maxOf(initialContent.length, snapshot.content.length)
            if (!usesCodeMirrorLikeEditor && isShowingPartialLargeNote) {
                viewModel.promotePartialLargeNoteForEditing()
                isEditing = false
                return
            }
            if (!usesCodeMirrorLikeEditor && !isNewPrivacyNote && currentTextLength > DIRECT_EDIT_MAX_CHARS) {
                showLargeNoteEditBlockedToast()
                isEditing = false
                return
            }
            previewController.clearFocus()
            if (preservePreviewPosition) {
                val targetOffset = previewMarkdownOffset
                    ?.minus(if (snapshot.title.isBlank()) 0 else "# ${snapshot.title}\n\n".length)
                    ?.coerceIn(0, snapshot.content.length)
                    ?: (snapshot.content.length * previewScrollRatio).toInt().coerceIn(0, snapshot.content.length)
                editorController.setSelection(targetOffset)
                editEntrySelection = TextRange(targetOffset)
                if (previewMarkdownOffset != null) {
                    if (usesCodeMirrorLikeEditor) codeMirrorScrollController.scrollToOffset(targetOffset)
                    else editorController.scrollToOffset(targetOffset)
                } else if (usesCodeMirrorLikeEditor) {
                    codeMirrorScrollController.fastScrollToRatio(previewScrollRatio)
                } else {
                    editorController.fastScrollToRatio(previewScrollRatio)
                }
            } else {
                editEntrySelection = snapshot.selection
            }
            if (requestFocus) editorFocusRequestToken += 1
            isLeavingEditor = false
            editEnterTraceStartMs = enterAt
            editEnterTraceRun += 1
            isEditing = true
            return
        }

        val switchId = ++modeSwitchSequence
        activeModeSwitchId = switchId
        val engine = if (usesCodeMirrorLikeEditor) "CODEMIRROR" else if (usesQuillpadStyleEditor) "QUILLPAD" else "NATIVE"
        KardLeafLog.d(
            EDIT_ENTER_TRACE_TAG,
            "enterStart engine=$engine contentLen=$userPerfContentLen preserve=$preservePreviewPosition offsetProvided=${previewMarkdownOffset != null}",
        )
        KardLeafLog.d(
            MODE_SWITCH_TRACE_TAG,
            "request id=$switchId direction=preview_to_edit engine=$engine contentLen=$userPerfContentLen " +
                "largePlain=$showsLargePlainTextPreview offsetProvided=${previewMarkdownOffset != null}",
        )
        val snapshot = editorController.getSnapshot()
        val editorWasCreated = editorSurfaceCreated
        KardLeafLog.d(
            EDIT_ENTER_TRACE_TAG,
            "snapshotReady engine=$engine elapsed=${SystemClock.elapsedRealtime() - enterAt}ms contentLen=${snapshot.content.length}",
        )
        val currentTextLength = maxOf(initialContent.length, snapshot.content.length)
        previewController.clearFocus()

        fun activateEditor(anchor: EditorViewportAnchor, source: String, targetResult: String?) {
            if (activeModeSwitchId != switchId) {
                KardLeafLog.d(MODE_SWITCH_TRACE_TAG, "drop id=$switchId direction=preview_to_edit reason=stale")
                return
            }
            editEntrySelection = if (preservePreviewPosition) TextRange(anchor.offset) else snapshot.selection
            if (previewMarkdownOffset != null) editorController.setSelection(anchor.offset)
            if (requestFocus) editorFocusRequestToken += 1
            isLeavingEditor = false
            editEnterTraceStartMs = enterAt
            editEnterTraceRun += 1
            isEditing = true
            committedModeSwitchId = switchId
            KardLeafLog.d(
                MODE_SWITCH_TRACE_TAG,
                "stateChanged id=$switchId direction=preview_to_edit engine=$engine source=$source " +
                    "sourceOffset=${anchor.offset} edge=${anchor.edge} targetResult=$targetResult elapsed=${SystemClock.elapsedRealtime() - enterAt}ms",
            )
            KardLeafLog.d(
                EDIT_ENTER_TRACE_TAG,
                "stateChanged run=$editEnterTraceRun engine=$engine elapsed=${SystemClock.elapsedRealtime() - enterAt}ms " +
                    "selection=${editEntrySelection?.start ?: snapshot.selection.start}:${editEntrySelection?.end ?: snapshot.selection.end}",
            )
        }

        fun applyTarget(anchor: EditorViewportAnchor, source: String) {
            val safeAnchor = anchor.shifted(0, snapshot.content.length)
            KardLeafLog.d(
                MODE_SWITCH_TRACE_TAG,
                "sourceReady id=$switchId direction=preview_to_edit source=$source sourceOffset=${safeAnchor.offset} edge=${safeAnchor.edge}",
            )
            if (!preservePreviewPosition) {
                activateEditor(safeAnchor, source, "not_preserved")
            } else if (!editorWasCreated) {
                editEntrySelection = TextRange(safeAnchor.offset)
                if (previewMarkdownOffset != null) editorController.setSelection(safeAnchor.offset)
                pendingCodeMirrorEditSwitch =
                    PendingCodeMirrorEditSwitch(switchId, safeAnchor, source, enterAt, requestFocus)
                editorSurfaceCreated = true
                KardLeafLog.d(
                    MODE_SWITCH_TRACE_TAG,
                    "pending id=$switchId direction=preview_to_edit engine=CODEMIRROR source=$source " +
                        "targetResult=surface_create pendingAnchorOffset=${safeAnchor.offset} " +
                        "pendingAnchorEdge=${safeAnchor.edge}",
                )
            } else {
                codeMirrorScrollController.scrollViewportToAnchor(safeAnchor) { result ->
                    activateEditor(safeAnchor, source, result)
                }
            }
        }

        if (!preservePreviewPosition) {
            applyTarget(EditorViewportAnchor(snapshot.selection.start, 0.5f, EditorViewportEdge.CENTER), "selection")
        } else if (previewMarkdownOffset != null) {
            applyTarget(
                EditorViewportAnchor(previewMarkdownOffset, 0.5f, EditorViewportEdge.CENTER)
                    .shifted(-(if (snapshot.title.isBlank()) 0 else "# ${snapshot.title}\n\n".length), snapshot.content.length),
                "double_tap",
            )
        } else {
            previewController.getViewportAnchor { previewAnchor ->
                val fallback = EditorViewportAnchor(
                    (snapshot.content.length * previewScrollRatio).roundToInt(),
                    0.5f,
                    EditorViewportEdge.CENTER,
                )
                applyTarget(
                    (previewAnchor ?: fallback).shifted(
                        -(if (snapshot.title.isBlank()) 0 else "# ${snapshot.title}\n\n".length),
                        snapshot.content.length,
                    ),
                    "markdown_anchor",
                )
            }
        }
    }
    LaunchedEffect(isEditing, committedModeSwitchId) {
        val switchId = committedModeSwitchId
        if (switchId <= 0) return@LaunchedEffect
        withFrameNanos { }
        KardLeafLog.d(
            MODE_SWITCH_TRACE_TAG,
            "visibleFrame id=$switchId mode=${if (isEditing) "edit" else "preview"} " +
                "engine=${if (usesCodeMirrorLikeEditor) "CODEMIRROR" else if (usesQuillpadStyleEditor) "QUILLPAD" else "NATIVE"} " +
                "largePlain=$showsLargePlainTextPreview",
        )
    }

    LaunchedEffect(pendingPreviewSwitch?.first) {
        val pending = pendingPreviewSwitch ?: return@LaunchedEffect
        delay(1_500)
        if (pendingPreviewSwitch?.first == pending.first && isEditing) {
            KardLeafLog.w(
                PREVIEW_CHAIN_TRACE_TAG,
                "modeSwitchPendingTimeout id=${pending.first} editing=$isEditing " +
                    "requested=${lastRequestedPreviewSignature?.let { "${it.first}/${it.second}" } ?: "none"} " +
                    "rendered=${lastRenderedPreviewSignature?.let { "${it.first}/${it.second}" } ?: "none"} " +
                    "visible=${visiblePreviewSignature.first}/${visiblePreviewSignature.second}",
            )
        }
    }

    wikilinkPrompt?.let { prompt ->
        WikilinkPromptDialog(
            prompt = prompt,
            onDismiss = viewModel::dismissWikilinkPrompt,
            onCreate = { target, sourcePath -> viewModel.createWikilinkNote(target, sourcePath) },
            onCandidate = { path -> viewModel.openWikilinkCandidate(path) },
        )
    }

    LaunchedEffect(effectiveEditorOpen, editorDocumentKey, isUserPerfTrackedNote) {
        if (effectiveEditorOpen && isUserPerfTrackedNote && !userPerfScreenComposedLogged) {
            userPerfScreenComposedLogged = true
            logUserPerfOpenStep(
                "editorComposeEntered",
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
            isEditing = false
        }
    }

    fun openMindMap(markAsMindMap: Boolean = true) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        syncUndoRedoState()
        var currentContent = editorController.getText()
        mindMapInitialEditIndex = null
        if (currentContent.isBlank()) {
            val snapshot = editorController.getSnapshot()
            if (applyMindMapEdit(snapshot, createMindMapRoot(), refreshPreview = false)) {
                currentContent = editorController.getText()
            }
        }
        val result = if (currentContent.length > KardLeafContentLimits.MIND_MAP_MAX_CONTENT_CHARS) {
            blockedLargeMindMapResult(currentContent.length)
        } else {
            prepareMarkdownMindMap(currentContent)
        }
        mindMapDocument = result.document
        mindMapUnavailableTitle = result.unavailableTitle
        mindMapUnavailableMessage = result.unavailableMessage
        mindMapDisplayTitle = editorController.getSnapshot().title
        if (markAsMindMap && result.document != null && !isMindMapNote) {
            mindMapModeActivated = true
            saveNote(
                saveHistory = false,
                noteTypeOverride = NoteFormatUtils.NOTE_TYPE_MINDMAP,
            )
        }
        mindMapAutoOpenHandled = true
        showMindMap = true
    }

    LaunchedEffect(editorDocumentKey, currentNote?.noteType, isOpeningNoteContent, isPrivacyEditor) {
        if (
            !isPrivacyEditor &&
            !isOpeningNoteContent &&
            isMindMapNoteFromFile &&
            !mindMapAutoOpenHandled
        ) {
            openMindMap(markAsMindMap = false)
        }
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

    LaunchedEffect(isEditing, isOpeningNoteContent) {
        if (isEditing) {
            editorSurfaceCreated = true
        } else if (!isOpeningNoteContent) {
            previewSurfaceCreated = true
        }
    }

    LaunchedEffect(
        pendingEditorEditNoteId,
        currentNote?.file?.path,
        effectiveEditorOpen,
        isOpeningNoteContent,
    ) {
        val noteId = currentNote?.id ?: return@LaunchedEffect
        if (pendingEditorEditNoteId == noteId && effectiveEditorOpen && !isOpeningNoteContent) {
            viewModel.consumeEditorEditRequest(noteId)
            enterEditMode()
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

    LaunchedEffect(showMindMap, isEditing, isOpeningNoteContent, folder, editorDocumentKey, initialTitle, initialContent, showsLargePlainTextPreview, usesCodeMirrorLikeEditor) {
        if (!showMindMap && !isEditing && !isOpeningNoteContent) {
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
        val backSnapshot = editorController.getSnapshot()
        val shouldSave = shouldSaveEditorOnLeave()
        KardLeafLog.d(
            SAVE_PATH_TRACE_TAG,
            "back leave decision key=$editorDocumentKey isEditing=$isEditing shouldSave=$shouldSave " +
                "currentPath=${currentNote?.file?.path} currentTitle=${currentNote?.title} " +
                "snapshotTitleLen=${backSnapshot.title.length} snapshotContentLen=${backSnapshot.content.length} " +
                "snapshotContentHash=${backSnapshot.content.hashCode()} " +
                "editorDirty=${viewModel.editorDirty.value} privacyDirty=$privacyEditorDirty " +
                "hideQuickNoteTitle=$hideQuickNoteTitleInEditor hideInitialTitle=$hideInitialTitleInEditor rawInitialTitle=$rawInitialTitle",
        )
        leaveEditorAfterSaveIfNeeded("system-back")
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
                                placeholder = localizedText("搜索当前笔记", "Search this note"),
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
                        IconButton(onClick = { leaveEditorAfterSaveIfNeeded("top-back") }) {
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
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = localizedText("上一个", "Previous"))
                        }
                        IconButton(
                            enabled = noteSearchMatchCount > 0,
                            onClick = { moveSearchMatch(forward = true) },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = localizedText("下一个", "Next"))
                        }
                        Box(
                            modifier = Modifier.width(58.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = noteSearchError ?: if (noteSearchCurrentOrdinal > 0 && noteSearchMatchCount > 0) {
                                    "$noteSearchCurrentOrdinal/$noteSearchMatchCount"
                                } else {
                                    localizedText("${noteSearchMatchCount}处", "$noteSearchMatchCount matches")
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
                            Icon(Icons.Default.Close, contentDescription = localizedText("关闭搜索", "Close search"))
                        }
                    } else {
                        val normalizedEditorTopToolbarOrder = editorTopToolbarOrder.distinct().toMutableList().also { order ->
                            PrefsManager.EditorTopToolbarItemId.DEFAULT_ORDER.forEach { if (it !in order) order.add(it) }
                        }.filter { item ->
                            item !in editorTopToolbarHiddenItems &&
                                (noteSidePanelToolbarEnabled || (item != PrefsManager.EditorTopToolbarItemId.OUTLINE && item != PrefsManager.EditorTopToolbarItemId.REMARKS))
                        }.let { order ->
                            if (isMindMapNote && PrefsManager.EditorTopToolbarItemId.MINDMAP !in order) {
                                order + PrefsManager.EditorTopToolbarItemId.MINDMAP
                            } else {
                                order
                            }
                        }
                        val safeEditorTopToolbarMoreItems = editorTopToolbarMoreItems
                            .filter { it in normalizedEditorTopToolbarOrder && it != PrefsManager.EditorTopToolbarItemId.MORE }
                            .filterNot { isMindMapNote && it == PrefsManager.EditorTopToolbarItemId.MINDMAP }
                            .toSet()
                        val editorTopToolbarMoreDisplayItems = normalizedEditorTopToolbarOrder.filter { it in safeEditorTopToolbarMoreItems }
                        val editorTopToolbarTopItems = normalizedEditorTopToolbarOrder
                            .filter { it !in safeEditorTopToolbarMoreItems }
                            .let { topItems ->
                                if (!isMindMapNote) {
                                    topItems
                                } else {
                                    val withoutMindMap = topItems.filter { it != PrefsManager.EditorTopToolbarItemId.MINDMAP }.toMutableList()
                                    val moreIndex = withoutMindMap.indexOf(PrefsManager.EditorTopToolbarItemId.MORE)
                                        .takeIf { it >= 0 }
                                        ?: withoutMindMap.size
                                    withoutMindMap.add(moreIndex, PrefsManager.EditorTopToolbarItemId.MINDMAP)
                                    withoutMindMap
                                }
                            }

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
                                    contentDescription = localizedText("大纲", "Outline"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        @Composable
                        fun RemarksAction() {
                            IconButton(onClick = { openNoteSidePanel(-noteSidePanelWidthPx) }) {
                                Icon(
                                    Icons.Outlined.StickyNote2,
                                    contentDescription = localizedText("属性备注", "Properties & remarks"),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        @Composable
                        fun WebClipAction() {
                            IconButton(onClick = {
                                showLabelMenu = false
                                showMoreMenu = false
                                showHeadingMenu = false
                                showMathMenu = false
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                showWebClipImportDialog = true
                            }) {
                                Icon(
                                    Icons.Outlined.Language,
                                    contentDescription = localizedText("保存网站", "Save website"),
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
                                    contentDescription = localizedText("搜索当前笔记", "Search this note"),
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
                                    contentDescription = localizedText("思维导图", "Mind map"),
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
                                    Icon(Icons.Outlined.History, contentDescription = localizedText("历史版本", "Version history"))
                                }
                            }
                        }

                        @Composable
                        fun PrivacyAction() {
                            val note = currentNote
                            if (note != null && !note.isTrashed) {
                                IconButton(onClick = { addCurrentNoteToPrivacy(note) { leaveEditor() } }) {
                                    Icon(Icons.Outlined.Shield, contentDescription = localizedText("保护", "Protect"))
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
                                        text = { Text(localizedText("思维导图", "Mind map")) },
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
                                            text = { Text(localizedText("移动笔记", "Move note")) },
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
                                        text = { Text(localizedText("大纲", "Outline")) },
                                        leadingIcon = { Icon(Icons.Outlined.Toc, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            openNoteSidePanel(noteSidePanelWidthPx)
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.REMARKS -> if (noteSidePanelToolbarEnabled) {
                                    DropdownMenuItem(
                                        text = { Text(localizedText("属性备注", "Properties & remarks")) },
                                        leadingIcon = { Icon(Icons.Outlined.StickyNote2, null) },
                                        onClick = {
                                            showMoreMenu = false
                                            openNoteSidePanel(-noteSidePanelWidthPx)
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.SEARCH -> {
                                    DropdownMenuItem(
                                        text = { Text(localizedText("搜索当前笔记", "Search this note")) },
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
                                    DropdownMenuItem(
                                        text = { Text(localizedText("历史版本", "Version history")) },
                                        leadingIcon = { Icon(Icons.Outlined.History, null) },
                                        enabled = note != null && !note.isTrashed,
                                        onClick = {
                                            if (note != null && !note.isTrashed) {
                                                showHistoryDialog = true
                                                showMoreMenu = false
                                            }
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.PRIVACY -> {
                                    val note = currentNote
                                    DropdownMenuItem(
                                        text = { Text(localizedText("保护", "Protect")) },
                                        leadingIcon = { Icon(Icons.Outlined.Shield, null) },
                                        enabled = note != null && !note.isTrashed,
                                        onClick = {
                                            if (note != null && !note.isTrashed) {
                                                addCurrentNoteToPrivacy(note) { leaveEditor() }
                                                showMoreMenu = false
                                            }
                                        },
                                    )
                                }
                                PrefsManager.EditorTopToolbarItemId.ARCHIVE -> {
                                    val note = currentNote
                                    val archivedNote = note?.takeIf { it.isArchived && !it.isTrashed }
                                    if (archivedNote != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.unarchive)) },
                                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                            onClick = {
                                                viewModel.restoreNote(archivedNote)
                                                showMoreMenu = false
                                                leaveEditor()
                                            },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.archive)) },
                                            leadingIcon = { Icon(Icons.Outlined.Inventory2, null) },
                                            enabled = note != null && !note.isTrashed,
                                            onClick = {
                                                note?.takeIf { !it.isTrashed }?.let { activeNote ->
                                                    viewModel.archiveNote(activeNote)
                                                    showMoreMenu = false
                                                    leaveEditor()
                                                }
                                            },
                                        )
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
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.delete)) },
                                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                            enabled = note != null,
                                            onClick = {
                                                if (note != null) {
                                                    viewModel.deleteNote(note)
                                                    showMoreMenu = false
                                                    leaveEditor()
                                                }
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
                            } else {
                                Box {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .combinedClickable(
                                                onClick = {
                                                    val now = SystemClock.uptimeMillis()
                                                    val ignoreReopen = !showMoreMenu && now - lastMoreMenuDismissAt < MENU_REOPEN_GUARD_MS
                                                    KardLeafLog.d(
                                                        BACK_TRACE_TAG,
                                                        "Editor note more click toggle menu noteId=${currentNoteObj?.id ?: "new"} showMoreMenu=$showMoreMenu ignoreReopen=$ignoreReopen",
                                                    )
                                                    if (!ignoreReopen) {
                                                        showLabelMenu = false
                                                        showHeadingMenu = false
                                                        showMathMenu = false
                                                        if (!showMoreMenu) {
                                                            showDownloadWebImagesAction = currentNoteObj?.let { note ->
                                                                val menuCheckContent =
                                                                    if (viewModel.editorDirty.value) {
                                                                        editorController.getSnapshot().content
                                                                    } else {
                                                                        note.content
                                                                    }
                                                                NoteFormatUtils.hasRemoteMarkdownImage(menuCheckContent)
                                                            } ?: false
                                                        }
                                                        showMoreMenu = !showMoreMenu
                                                    }
                                                },
                                                onLongClick = {
                                                    currentNoteObj?.let { note ->
                                                        addCurrentNoteToPrivacy(note) { leaveEditor() }
                                                    }
                                                },
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
                                        listOf(
                                            PrefsManager.EditorKernel.QUILLPAD_STYLE to localizedText("原生内核", "Native editor"),
                                            PrefsManager.EditorKernel.CODEMIRROR_LIVE_PREVIEW to "WebView",
                                        ).forEach { (targetEditorKernel, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                trailingIcon = {
                                                    RadioButton(
                                                        selected = editorKernel == targetEditorKernel,
                                                        onClick = null,
                                                    )
                                                },
                                                onClick = { switchEditorKernel(targetEditorKernel) },
                                            )
                                        }
                                        HorizontalDivider()
                                        val renderedMoreToolbarItems = editorTopToolbarMoreDisplayItems
                                            .filter { it != PrefsManager.EditorTopToolbarItemId.EDIT || !isEditing }
                                        var aiAssistantRendered = false
                                        renderedMoreToolbarItems.forEach { item ->
                                            EditorTopToolbarMoreItem(item)
                                            if (item == PrefsManager.EditorTopToolbarItemId.MINDMAP) {
                                                DropdownMenuItem(
                                                    text = { Text(localizedText("AI 助手", "AI assistant")) },
                                                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        requestAiSnapshot()
                                                    },
                                                )
                                                aiAssistantRendered = true
                                            }
                                        }
                                        if (!aiAssistantRendered) {
                                            DropdownMenuItem(
                                                text = { Text(localizedText("AI 助手", "AI assistant")) },
                                                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, null) },
                                                onClick = {
                                                    showMoreMenu = false
                                                    requestAiSnapshot()
                                                },
                                            )
                                        }
                                        currentNoteObj?.takeIf { showDownloadWebImagesAction }?.let { downloadableNote ->
                                            DropdownMenuItem(
                                                text = { Text("下载网页图片") },
                                                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                                                enabled = !isDownloadingWebImages,
                                                onClick = { downloadWebImages(downloadableNote) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isNewRegularNote) {
                            WebClipAction()
                            MindMapAction()
                        }
                        editorTopToolbarTopItems
                            .asSequence()
                            .filterNot { isNewRegularNote && it == PrefsManager.EditorTopToolbarItemId.MINDMAP }
                            .forEach { item ->
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
                Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(
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
                    EditorFileInfoText(
                        date = currentNote?.createdAt ?: fileInfoFallbackDate,
                        charCount = editorContentLength,
                        folder = folder,
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
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
                                    KardLeafCustomFeatures.ToolbarItem.RULE -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.HorizontalRule,
                                        contentDescription = "分割线",
                                        onClick = { insertAtCursorOrCommand("***\n", command = "insertHorizontalRule") },
                                    )
                                    KardLeafCustomFeatures.ToolbarItem.BOLD -> ToolbarIconButton(text = "B", bold = true, onClick = { insertAtCursorOrCommand("**", "**", command = "toggleBold") })
                                    KardLeafCustomFeatures.ToolbarItem.ITALIC -> ToolbarIconButton(text = "I", italic = true, onClick = { insertAtCursorOrCommand("_", "_", command = "toggleItalic") })
                                    KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> ToolbarIconButton(text = "U", underline = true, onClick = { insertAtCursorOrCommand("<u>", "</u>", command = "toggleUnderline") })
                                    KardLeafCustomFeatures.ToolbarItem.STRIKE -> ToolbarIconButton(text = "S", strikethrough = true, onClick = { insertAtCursorOrCommand("~~", "~~", command = "toggleStrike") })
                                    KardLeafCustomFeatures.ToolbarItem.LINK -> ToolbarIconButton(text = "Link", onClick = { insertAtCursor("[", "](url)") })
                                    KardLeafCustomFeatures.ToolbarItem.CODE -> ToolbarIconButton(text = "`", onClick = { insertAtCursorOrCommand("`", "`", command = "toggleCode") })
                                    KardLeafCustomFeatures.ToolbarItem.CODE_BLOCK -> ToolbarIconButton(text = "```", onClick = { insertAtCursorOrCommand("```\n", "\n```", command = "insertCodeBlock") })
                                    KardLeafCustomFeatures.ToolbarItem.QUOTE -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.FormatQuote,
                                        contentDescription = "引用",
                                        onClick = { insertAtCursorOrCommand("> ", command = "toggleBlockquote") },
                                    )
                                    KardLeafCustomFeatures.ToolbarItem.MATH -> ToolbarIconButton(text = "$", onClick = { insertAtCursor("$", "$") })
                                    KardLeafCustomFeatures.ToolbarItem.BULLET -> ToolbarIconButton(text = "-", onClick = { insertAtCursorOrCommand("- ", command = "toggleUnorderedList") })
                                    KardLeafCustomFeatures.ToolbarItem.NUMBERED -> ToolbarIconButton(text = "1.", onClick = { insertAtCursorOrCommand("1. ", command = "toggleOrderedList") })
                                    KardLeafCustomFeatures.ToolbarItem.INDENT -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.FormatIndentIncrease,
                                        contentDescription = "缩进",
                                        onClick = { changeIndent(true) },
                                    )
                                    KardLeafCustomFeatures.ToolbarItem.OUTDENT -> ToolbarIconButton(
                                        text = "",
                                        icon = Icons.Outlined.FormatIndentDecrease,
                                        contentDescription = "反缩进",
                                        onClick = { changeIndent(false) },
                                    )
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
                                                                    if (!runEditorCommand("toggleHeading", label.removePrefix("H").toIntOrNull() ?: 1)) {
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
                                        KardLeafCustomFeatures.ToolbarItem.RULE -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.HorizontalRule,
                                            contentDescription = "分割线",
                                            onClick = { insertAtCursorOrCommand("***\n", command = "insertHorizontalRule") },
                                        )
                                        KardLeafCustomFeatures.ToolbarItem.BOLD -> ToolbarIconButton(text = "B", bold = true, onClick = { insertAtCursorOrCommand("**", "**", command = "toggleBold") })
                                        KardLeafCustomFeatures.ToolbarItem.ITALIC -> ToolbarIconButton(text = "I", italic = true, onClick = { insertAtCursorOrCommand("_", "_", command = "toggleItalic") })
                                        KardLeafCustomFeatures.ToolbarItem.UNDERLINE -> ToolbarIconButton(text = "U", underline = true, onClick = { insertAtCursorOrCommand("<u>", "</u>", command = "toggleUnderline") })
                                        KardLeafCustomFeatures.ToolbarItem.STRIKE -> ToolbarIconButton(text = "S", strikethrough = true, onClick = { insertAtCursorOrCommand("~~", "~~", command = "toggleStrike") })
                                        KardLeafCustomFeatures.ToolbarItem.LINK -> ToolbarIconButton(text = "Link", onClick = { insertAtCursor("[", "](url)") })
                                        KardLeafCustomFeatures.ToolbarItem.CODE -> ToolbarIconButton(text = "`", onClick = { insertAtCursorOrCommand("`", "`", command = "toggleCode") })
                                        KardLeafCustomFeatures.ToolbarItem.CODE_BLOCK -> ToolbarIconButton(text = "```", onClick = { insertAtCursorOrCommand("```\n", "\n```", command = "insertCodeBlock") })
                                        KardLeafCustomFeatures.ToolbarItem.QUOTE -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.FormatQuote,
                                            contentDescription = "引用",
                                            onClick = { insertAtCursorOrCommand("> ", command = "toggleBlockquote") },
                                        )
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
                                        KardLeafCustomFeatures.ToolbarItem.INDENT -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.FormatIndentIncrease,
                                            contentDescription = "缩进",
                                            onClick = { changeIndent(true) },
                                        )
                                        KardLeafCustomFeatures.ToolbarItem.OUTDENT -> ToolbarIconButton(
                                            text = "",
                                            icon = Icons.Outlined.FormatIndentDecrease,
                                            contentDescription = "反缩进",
                                            onClick = { changeIndent(false) },
                                        )
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
            if (
                effectiveEditorOpen &&
                !blocksDirectEditForLargeNote &&
                (isEditing || (keepsModeSurfacesAlive && editorSurfaceCreated && !isOpeningNoteContent))
            ) {
                KardLeafLog.d(
                    LARGE_NOTE_OPEN_TRACE_TAG,
                    "screen compose editor surface key=$editorDocumentKey kernel=$editorKernel useCodeMirror=$usesCodeMirrorLikeEditor " +
                        "initialContentLen=${editorSurfaceContent.length} isOpening=$isOpeningNoteContent editing=$isEditing closing=$isClosingEditor",
                )
                if (usesCodeMirrorLikeEditor) {
                    KardLeafLog.d(
                        TITLE_TRACE_TAG,
                        "title render key=$editorDocumentKey engine=CODEMIRROR showTitle=${showBars && !hideQuickNoteTitleInEditor} " +
                            "showBars=$showBars hideQuickNoteTitle=$hideQuickNoteTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
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
                        active = isEditing,
                        onTitleChanged = { markEditorDirty() },
                        onContentChanged = {
                            editorContentLength.value = editorController.getContentLength()
                            markEditorDirty()
                            syncUndoRedoState()
                            refreshMindMapFromEditor()
                        },
                        onContentEdited = {
                            editorContentLength.value = editorController.getContentLength()
                            markEditorDirty()
                        },
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
                        showTitle = showBars && !hideQuickNoteTitleInEditor,
                        livePreviewEnabled = codeMirrorLivePreviewEnabled,
                        keyboardInsetPx = (
                            imeInsets.getBottom(density) - navigationBarsInsets.getBottom(density)
                        ).coerceAtLeast(0),
                        requestFocusToken = editorFocusRequestToken,
                        onFocusRequestHandled = ::handleEditorFocusRequest,
                        preferredFocusSelection = editEntrySelection,
                        initialViewportAnchor = pendingCodeMirrorEditSwitch?.anchor,
                        onInitialViewportAnchorApplied = { anchor, result ->
                            val pending = pendingCodeMirrorEditSwitch
                            if (
                                pending == null ||
                                pending.anchor != anchor ||
                                activeModeSwitchId != pending.switchId
                            ) {
                                KardLeafLog.d(
                                    MODE_SWITCH_TRACE_TAG,
                                    "drop direction=preview_to_edit reason=stale_initial_anchor offset=${anchor.offset}",
                                )
                            } else {
                                pendingCodeMirrorEditSwitch = null
                                if (pending.requestFocus) editorFocusRequestToken += 1
                                isLeavingEditor = false
                                editEnterTraceStartMs = pending.startedAt
                                editEnterTraceRun += 1
                                isEditing = true
                                committedModeSwitchId = pending.switchId
                                KardLeafLog.d(
                                    MODE_SWITCH_TRACE_TAG,
                                    "stateChanged id=${pending.switchId} direction=preview_to_edit engine=CODEMIRROR " +
                                        "source=${pending.source} sourceOffset=${anchor.offset} edge=${anchor.edge} " +
                                        "targetResult=$result elapsed=${SystemClock.elapsedRealtime() - pending.startedAt}ms " +
                                        "contentApplied=true surfaceVisible=true",
                                )
                            }
                        },
                        onDrawingImageClicked = { target -> handleImageClicked(target) },
                        wikilinkNotes = allNotes,
                        onInternalLinkOpen = { target ->
                            viewModel.openWikilinkTarget(target, currentNote?.file?.path.orEmpty())
                        },
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
                            .zIndex(if (isEditing) 1f else 0f)
                            .onGloballyPositioned { coordinates ->
                                logCodeMirrorOuterLayout("codeMirrorSlot", coordinates)
                            }
                            .then(userPerfAreaFirstFrameModifier("codeMirror")),
                    )
                } else if (usesQuillpadStyleEditor) {
                    KardLeafQuillpadEditor(
                        initialTitle = editorSurfaceTitle,
                        initialContent = editorSurfaceContent,
                        documentKey = editorDocumentKey,
                        controller = editorController,
                        active = isEditing,
                        onTitleChanged = { markEditorDirty() },
                        onContentChanged = {
                            editorContentLength.value = editorController.getContentLength()
                            markEditorDirty()
                            syncUndoRedoState()
                            refreshMindMapFromEditor()
                        },
                        onUndoRedoChanged = { syncUndoRedoState() },
                        onUserInteraction = { hideNoteSearchCursor("quillpad editor touch") },
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
                        onFocusRequestHandled = ::handleEditorFocusRequest,
                        initialSelection = editorSurfaceSelection,
                        initialViewportAnchor = editEntrySelection?.let { selection ->
                            EditorViewportAnchor(
                                offset = selection.start,
                                viewportFraction = 0.5f,
                                edge = when (selection.start) {
                                    0 -> EditorViewportEdge.START
                                    editorSurfaceContent.length -> EditorViewportEdge.END
                                    else -> EditorViewportEdge.CENTER
                                },
                            )
                        },
                        showTitle = showBars && !hideQuickNoteTitleInEditor,
                        currentFolder = folder,
                        inlineImagePreviewEnabled = editingImagePreviewEnabled,
                        readOnly = usesOpeningEditShell,
                        imeBottomPx = imeInsets.getBottom(density),
                        openSession = editorOpenSession,
                        onFrameCommitted = onEditorFrameCommitted,
                        userPerfOpenStartRealtimeMs = userPerfOpenStartMs,
                        userPerfSizeTier = userPerfSizeTier,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isEditing) 1f else 0f)
                            .then(userPerfAreaFirstFrameModifier("quillpadStyle")),
                    )
                } else {
                    KardLeafLog.d(
                        TITLE_TRACE_TAG,
                        "title render key=$editorDocumentKey engine=NATIVE showTitle=${showBars && !hideQuickNoteTitleInEditor} " +
                            "showBars=$showBars hideQuickNoteTitle=$hideQuickNoteTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
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
                            editorContentLength.value = editorController.getContentLength()
                            markEditorDirty()
                            syncUndoRedoState()
                            refreshMindMapFromEditor()
                        },
                        onUndoRedoChanged = { syncUndoRedoState() },
                        onUserInteraction = { hideNoteSearchCursor("editor content touch") },
                        onFastScrollSourceScrolled = { fastScrollSignal.notifyScrollChanged() },
                        onInlineImageClicked = { target -> handleImageClicked(target) },
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
                        onFocusRequestHandled = ::handleEditorFocusRequest,
                        initialSelection = editorSurfaceSelection,
                        showTitle = showBars && !hideQuickNoteTitleInEditor,
                        currentFolder = folder,
                        inlineImagePreviewEnabled = editingImagePreviewEnabled,
                        readOnly = usesOpeningEditShell,
                        userPerfOpenStartRealtimeMs = userPerfOpenStartMs,
                        userPerfSizeTier = userPerfSizeTier,
                        openSession = editorOpenSession,
                        onFrameCommitted = onEditorFrameCommitted,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(userPerfAreaFirstFrameModifier("nativeEditor")),
                    )
                }
            }
            if (effectiveEditorOpen && (!isEditing || (keepsModeSurfacesAlive && previewSurfaceCreated))) {
                if (usesCodeMirrorLikeEditor && isOpeningNoteContent) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColor)
                            .padding(start = 16.dp, end = 16.dp, top = 6.dp),
                    ) {
                        if (showBars && editorSurfaceTitle.isNotBlank()) {
                            Text(
                                text = editorSurfaceTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            )
                        }
                    }
                } else if (showsLargePlainTextPreview) {
                    val plainSnapshot = largePlainPreviewSnapshot
                    KardLeafLog.d(
                        TITLE_TRACE_TAG,
                        "title render key=$editorDocumentKey engine=LARGE_PLAIN_PREVIEW showTitle=true " +
                            "plainTitle=${plainSnapshot?.title} plainTitleLen=${plainSnapshot?.title?.length ?: -1} " +
                            "initialTitle=$initialTitle initialTitleLen=${initialTitle.length} displayInitialTitle=$displayInitialTitle " +
                            "displayInitialTitleLen=${displayInitialTitle.length} keepLastTitleForEmptyExternal=$keepLastTitleForEmptyExternal " +
                            "lastValidTitleLen=${lastValidEditorDisplayTitle.length} " +
                            "showDetailTitle=$showNoteDetailTitle hideQuickNoteTitle=$hideQuickNoteTitleInEditor hideInitialTitle=$hideInitialTitleInEditor " +
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
                        active = !isEditing,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isEditing) 0f else 1f)
                            .then(userPerfAreaFirstFrameModifier("markdownPreview")),
                        searchQuery = if (showNoteSearch) noteSearchQuery else "",
                        headingScrollText = previewHeadingScrollText,
                        headingScrollLevel = previewHeadingScrollLevel,
                        headingScrollToken = previewHeadingScrollToken,
                        onDoubleTap = { offset ->
                            enterEditMode(
                                preservePreviewPosition = true,
                                previewMarkdownOffset = offset,
                                requestFocus = true,
                            )
                        },
                        onUserInteraction = { hideNoteSearchCursor("preview touch") },
                        onScrollRatioChanged = { previewScrollRatio = it },
                        onFastScrollSourceScrolled = { fastScrollSignal.notifyScrollChanged() },
                        onImageClicked = { index ->
                            previewImageTargets.getOrNull(index)?.let { target ->
                                KardLeafLog.d(
                                    "KardLeafImageTrace",
                                    "preview image click index=$index reference=${target.reference} occurrence=${target.occurrenceIndex}",
                                )
                                handleImageClicked(target)
                            }
                        },
                        onInternalLinkOpen = { target ->
                            viewModel.openWikilinkTarget(target, currentNote?.file?.path.orEmpty())
                        },
                        onContentRendered = { length, contentHash ->
                            KardLeafLog.d(
                                PREVIEW_CHAIN_TRACE_TAG,
                                "renderCallback key=$editorDocumentKey callback=$length/$contentHash " +
                                    "expected=${visiblePreviewSignature.first}/${visiblePreviewSignature.second} " +
                                    "requested=${lastRequestedPreviewSignature?.let { "${it.first}/${it.second}" } ?: "none"} " +
                                    "pending=${pendingPreviewSwitch?.first ?: 0} editing=$isEditing",
                            )
                            if (length to contentHash != visiblePreviewSignature) {
                                KardLeafLog.d(
                                    "KardLeafPreviewTrace",
                                    "previewRender ignored stale len=$length expectedLen=${visiblePreviewSignature.first}",
                                )
                                return@PreviewWebView
                            }
                            lastRenderedPreviewSignature = length to contentHash
                            pendingPreviewScrollRatio?.let { ratio ->
                                previewController.fastScrollToRatio(ratio)
                                pendingPreviewScrollRatio = null
                            }
                            pendingPreviewSwitch?.let { (switchId, previewAnchor, startedAt) ->
                                if (activeModeSwitchId == switchId) {
                                    previewController.scrollToAnchor(previewAnchor) { result ->
                                        if (activeModeSwitchId == switchId) {
                                            pendingPreviewSwitch = null
                                            isLeavingEditor = true
                                            focusManager.clearFocus(force = true)
                                            keyboardController?.hide()
                                            isEditing = false
                                            committedModeSwitchId = switchId
                                            KardLeafLog.d(
                                                MODE_SWITCH_TRACE_TAG,
                                                "stateChanged id=$switchId direction=edit_to_preview targetOffset=${previewAnchor.offset} " +
                                                    "edge=${previewAnchor.edge} targetResult=$result " +
                                                    "elapsed=${SystemClock.elapsedRealtime() - startedAt}ms renderReady=true",
                                            )
                                        }
                                    }
                                } else {
                                    pendingPreviewSwitch = null
                                }
                            }
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
                        previewTheme = previewThemeId,
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
                            .width(editorFastScrollEdgeWidth)
                            .padding(bottom = editorFastScrollBottomPadding)
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
                displayTitle = mindMapDisplayTitle.ifBlank { snapshot.title.ifBlank { mindMapDocument?.root?.text.orEmpty() } },
                document = mindMapDocument,
                isDark = isDark,
                unavailableTitle = mindMapUnavailableTitle,
                unavailableMessage = mindMapUnavailableMessage,
                initialEditNodeIndex = mindMapInitialEditIndex,
                modifier = Modifier.zIndex(9f),
                onDismiss = { showMindMap = false },
                onInitialEditConsumed = { mindMapInitialEditIndex = null },
                onBackToHome = {
                    showMindMap = false
                    leaveEditorAfterSaveIfNeeded("mind-map-back")
                },
                onOpenSource = {
                    showMindMap = false
                    enterEditMode()
                },
                onMindMapNodeClick = { node ->
                    showMindMap = false
                    val previewTitlePrefixLength =
                        if (snapshot.title.isBlank()) 0 else "# ${snapshot.title}\n\n".length
                    enterEditMode(
                        previewMarkdownOffset = node.sourceOffset + previewTitlePrefixLength,
                    )
                },
                onUndo = { undoContent() },
                onRedo = { redoContent() },
                canUndo = canUndo,
                canRedo = canRedo,
                onNodeReparent = { movingIndex, parentIndex, gestureSequence ->
                    val editSnapshot = editorController.getSnapshot()
                    val document = prepareCurrentMindMap()
                    val movingNode = document?.nodes?.getOrNull(movingIndex)
                    val parentNode = document?.nodes?.getOrNull(parentIndex)
                    KardLeafLog.d(
                        MIND_MAP_GESTURE_TRACE_TAG,
                        "callback reparent start gesture=$gestureSequence movingIndex=$movingIndex parentIndex=$parentIndex " +
                            "nodes=${document?.nodes?.size ?: 0} contentLen=${editSnapshot.content.length} " +
                            "contentHash=${editSnapshot.content.hashCode()} editorDirty=${viewModel.editorDirty.value} " +
                            "isEditing=$isEditing movingTitle=${movingNode?.text} movingDepth=${movingNode?.depth ?: -1} " +
                            "parentTitle=${parentNode?.text} parentDepth=${parentNode?.depth ?: -1}",
                    )
                    val reparentResult = document?.let { reparentMindMapSubtree(it, movingIndex, parentIndex) }
                    if (reparentResult == null) {
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "callback reparent result=rejected gesture=$gestureSequence movingIndex=$movingIndex parentIndex=$parentIndex " +
                                "nodes=${document?.nodes?.size ?: 0} contentLen=${editSnapshot.content.length} " +
                                "contentHash=${editSnapshot.content.hashCode()}",
                        )
                    } else {
                        KardLeafLog.d(
                            MIND_MAP_GESTURE_TRACE_TAG,
                            "callback reparent result=accepted gesture=$gestureSequence movingIndex=$movingIndex parentIndex=$parentIndex " +
                                "oldContentLen=${editSnapshot.content.length} oldContentHash=${editSnapshot.content.hashCode()} " +
                                "newContentLen=${reparentResult.content.length} newContentHash=${reparentResult.content.hashCode()}",
                        )
                        if (applyMindMapEdit(editSnapshot, reparentResult)) {
                            KardLeafLog.d(
                                MIND_MAP_GESTURE_TRACE_TAG,
                                "callback reparent editor-updated gesture=$gestureSequence " +
                                    "controllerContentLen=${editorController.getText().length} " +
                                    "controllerContentHash=${editorController.getText().hashCode()}",
                            )
                        }
                    }
                },
                onNodeAddChild = { parentIndex, childTitle ->
                    val editSnapshot = editorController.getSnapshot()
                    val addResult = prepareCurrentMindMap()?.let { addMindMapChild(it, parentIndex, childTitle) }
                    if (applyMindMapEdit(editSnapshot, addResult)) {
                        mindMapInitialEditIndex = addResult?.nodeIndex
                        context.showToast("已在「${addResult?.contextTitle}」下添加子节点")
                    } else {
                        context.showToast("当前节点不能继续添加子节点")
                    }
                },
                onNodeAddSibling = { anchorIndex, siblingTitle ->
                    val editSnapshot = editorController.getSnapshot()
                    val addResult = prepareCurrentMindMap()?.let { addMindMapSibling(it, anchorIndex, siblingTitle) }
                    if (applyMindMapEdit(editSnapshot, addResult)) {
                        mindMapInitialEditIndex = addResult?.nodeIndex
                        context.showToast("已在「${addResult?.contextTitle}」后添加同级节点")
                    } else {
                        context.showToast("当前节点不能添加同级节点")
                    }
                },
                onNodeMove = { nodeIndex, moveUp ->
                    val editSnapshot = editorController.getSnapshot()
                    val moveResult = prepareCurrentMindMap()?.let { moveMindMapSubtree(it, nodeIndex, moveUp) }
                    applyMindMapEdit(editSnapshot, moveResult)
                },
                onNodeRename = { nodeIndex, renamedTitle ->
                    val editSnapshot = editorController.getSnapshot()
                    val renameResult = prepareCurrentMindMap()?.let { renameMindMapNode(it, nodeIndex, renamedTitle) }
                    if (applyMindMapEdit(editSnapshot, renameResult)) {
                        context.showToast("已重命名为「${renameResult?.nodeTitle}」")
                    } else {
                        context.showToast("节点名称没有变化")
                    }
                },
                onNodeDelete = { nodeIndex ->
                    val editSnapshot = editorController.getSnapshot()
                    val deleteResult = prepareCurrentMindMap()?.let { deleteMindMapSubtree(it, nodeIndex) }
                    applyMindMapEdit(editSnapshot, deleteResult)
                },
            )
        }
        if (viewingImageTarget != null) {
            ImageViewerScreen(
                resource = viewerResource,
                isLoading = viewerLoading,
                modifier = Modifier.zIndex(12f),
                onDismiss = {
                    viewingImageTarget = null
                    viewerResource = null
                    viewerLoading = false
                },
                onEdit = {
                    val target = viewingImageTarget
                    val resource = viewerResource
                    if (target != null && resource != null) {
                        openImageEditor(target, resource)
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
                    editingImageResource = null
                    editingImageTarget = null
                    showDrawingPad = false
                    if (shouldCloseEditor) {
                        leaveEditor()
                    }
                },
                initialDrawingSource = editingDrawingSource,
                initialBackgroundBitmap = editingImageResource?.backgroundBitmap,
                initialSourceWidth = editingImageResource?.sourceWidth ?: 0,
                initialSourceHeight = editingImageResource?.sourceHeight ?: 0,
                initialBackgroundMimeType = editingImageResource?.mimeType,
                initialExifOrientation = editingImageResource?.exifOrientation ?: 1,
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
                outgoingLinks = outgoingWikilinks,
                backlinkLinks = backlinks,
                onLinkClick = { path -> viewModel.openNoteByPath(path) },
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
                            context.showToast("备注已添加")
                        }
                    }
                },
                onUpdate = { remark, content ->
                    val updatedContent = content.trim()
                    if (updatedContent.isNotBlank()) {
                        viewModel.updateNoteRemark(remark.id, updatedContent) {
                            noteRemarkRefreshVersion++
                        }
                        context.showToast("备注已更新")
                    }
                },
                onDelete = { remark ->
                    viewModel.deleteNoteRemark(remark.id)
                    context.showToast("备注已删除")
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

