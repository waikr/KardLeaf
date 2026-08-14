package com.kangle.kardleaf.widget

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kangle.kardleaf.MainActivity
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.repository.MetadataManager
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.KardLeafCustomFeatures
import com.kangle.kardleaf.ui.editor.host.ToolbarIconButton
import com.kangle.kardleaf.ui.showToast
import com.kangle.kardleaf.ui.theme.KardLeafTheme
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

private const val NOTE_WIDGET_QUICK_ADD_LOG_TAG = "KardLeafNoteWidgetAdd"
private const val MAX_TITLE_CHARS = 120
private const val MAX_CONTENT_CHARS = 50_000
private const val WIDGET_CLICK_LOG_TAG = "KardLeafWidgetClick"

class NoteWidgetQuickAddActivity : ComponentActivity() {
    private val targetFolder: String
        get() = intent.getStringExtra(EXTRA_TARGET_FOLDER).orEmpty()

    private val initialTitle: String
        get() = intent.getStringExtra(EXTRA_INITIAL_TITLE).orEmpty()

    private val initialContent: String
        get() = intent.getStringExtra(EXTRA_INITIAL_CONTENT).orEmpty()

    private val editNoteId: String
        get() = intent.getStringExtra(EXTRA_NOTE_ID).orEmpty()

    private var editingNote: Note? = null
    private lateinit var repositoryDeferred: Deferred<RoomNoteRepository?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KardLeafLog.i(
            WIDGET_CLICK_LOG_TAG,
            "note editor entered action=${intent.action} hasNoteId=${editNoteId.isNotBlank()} noteIdHash=${editNoteId.hashCode()}",
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.42f }
        repositoryDeferred = lifecycleScope.async(Dispatchers.IO) { prepareQuickRepository() }

        if (editNoteId.isBlank()) {
            showEditor(null)
        } else {
            lifecycleScope.launch {
                val note = withContext(Dispatchers.IO) { loadExistingNote(editNoteId) }
                if (note == null) {
                    KardLeafLog.w(WIDGET_CLICK_LOG_TAG, "note editor load failed noteIdHash=${editNoteId.hashCode()}")
                    showToast("无法打开这条笔记")
                    finish()
                    return@launch
                }
                editingNote = note
                KardLeafLog.i(WIDGET_CLICK_LOG_TAG, "note editor loaded noteIdHash=${editNoteId.hashCode()}")
                showEditor(note)
            }
        }
    }

    private fun showEditor(note: Note?) {
        setContent {
            KardLeafTheme(styleSystemBars = false) {
                NoteWidgetQuickAddScreen(
                    folder = note?.folder ?: targetFolder,
                    initialTitle = note?.title ?: initialTitle,
                    initialContent = note?.content ?: initialContent,
                    initialPinned = note?.isPinned ?: false,
                    isEditing = note != null,
                    onDismiss = { finish() },
                    onOpenEditor = ::openFullEditor,
                    onSave = ::saveQuickNote,
                )
            }
        }
    }

    private suspend fun loadExistingNote(noteId: String): Note? =
        repositoryDeferred.await()?.getNote(noteId)

    private suspend fun prepareQuickRepository(): RoomNoteRepository? {
        val appContext = applicationContext
        val prefsManager = PrefsManager(appContext)
        val rootUri = prefsManager.getRootUri() ?: return null
        return quickRepositoryMutex.withLock {
            if (cachedQuickRepositoryRootUri == rootUri) {
                cachedQuickRepository?.let { return@withLock it }
            }
            val repository = RoomNoteRepository(appContext, MetadataManager(appContext), prefsManager)
            if (!repository.setRootFolderForQuickSave(rootUri)) return@withLock null
            cachedQuickRepositoryRootUri = rootUri
            cachedQuickRepository = repository
            repository
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun saveQuickNote(
        title: String,
        content: String,
        isPinned: Boolean,
        onFinished: () -> Unit,
    ) {
        if (title.isBlank() && content.isBlank()) {
            showToast("请输入标题或正文")
            onFinished()
            return
        }

        val appContext = applicationContext
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val repository = repositoryDeferred.await()
                        ?: return@runCatching SaveResult(false, "请先在应用中选择笔记库")
                    val now = Date()
                    val finalTitle = title.trim().ifBlank {
                        KardLeafCustomFeatures.formatUnnamedNoteTitle(appContext, now)
                    }
                    val current = editingNote
                    val savedPath = if (current == null) {
                        repository.saveNoteFromQuickEditor(
                            Note(
                                file = targetFolder.takeIf { it.isNotBlank() }
                                    ?.let { folder -> File(folder, "new_note_placeholder") }
                                    ?: File("new_note_placeholder"),
                                title = finalTitle,
                                content = content,
                                lastModified = now,
                                createdAt = now,
                                color = 0xFFFFFFFF,
                                isPinned = isPinned,
                            ),
                        )
                    } else {
                        repository.saveNoteFromQuickEditor(
                            current.copy(
                                title = finalTitle,
                                content = content,
                                contentPreview = content,
                                lastModified = now,
                                isPinned = isPinned,
                            ),
                            oldFile = current.file,
                            saveHistory = true,
                        )
                    }
                    if (savedPath.isBlank()) {
                        SaveResult(false, "保存失败，请检查笔记库权限")
                    } else {
                        KardLeafLog.i(
                            NOTE_WIDGET_QUICK_ADD_LOG_TAG,
                            "saved mode=${if (current == null) "new" else "edit"} path=$savedPath folder=${current?.folder ?: targetFolder} titleLen=${finalTitle.length} contentLen=${content.length} pinned=$isPinned",
                        )
                        NoteListWidgetProvider.refreshAllWidgets(appContext)
                        DailyNoteWidgetProvider.refreshAllWidgets(appContext)
                        SaveResult(true, if (current == null) "已保存笔记" else "已更新笔记")
                    }
                }.getOrElse { error ->
                    KardLeafLog.e(NOTE_WIDGET_QUICK_ADD_LOG_TAG, "save failed folder=$targetFolder", error)
                    SaveResult(false, "保存失败：${error.message.orEmpty().ifBlank { "未知错误" }}")
                }
            }
            showToast(result.message)
            onFinished()
            if (result.success) finish()
        }
    }

    private fun openFullEditor(
        title: String,
        content: String,
        isPinned: Boolean,
    ) {
        editingNote?.let { note ->
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("note_id", note.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
            finish()
            return
        }

        val data = Uri.Builder()
            .scheme("kardleaf")
            .authority("new")
            .appendQueryParameter("title", title.take(MAX_TITLE_CHARS))
            .appendQueryParameter("content", content.take(MAX_CONTENT_CHARS))
            .appendQueryParameter("pinned", if (isPinned) "1" else "0")
            .apply {
                if (targetFolder.isBlank()) {
                    appendQueryParameter("root", "1")
                } else {
                    appendQueryParameter("folder", targetFolder)
                }
            }
            .build()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                this.data = data
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
        finish()
    }

    private data class SaveResult(
        val success: Boolean,
        val message: String,
    )

    companion object {
        private val quickRepositoryMutex = Mutex()

        @Volatile
        private var cachedQuickRepositoryRootUri: String? = null

        @Volatile
        private var cachedQuickRepository: RoomNoteRepository? = null

        internal const val EXTRA_TARGET_FOLDER = "kardleaf_widget_quick_add_folder"
        internal const val EXTRA_INITIAL_TITLE = "kardleaf_widget_quick_add_initial_title"
        internal const val EXTRA_INITIAL_CONTENT = "kardleaf_widget_quick_add_initial_content"
        internal const val EXTRA_NOTE_ID = "kardleaf_widget_note_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteWidgetQuickAddScreen(
    folder: String,
    initialTitle: String,
    initialContent: String,
    initialPinned: Boolean,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onOpenEditor: (String, String, Boolean) -> Unit,
    onSave: (String, String, Boolean, () -> Unit) -> Unit,
) {
    var title by rememberSaveable(initialTitle) { mutableStateOf(initialTitle) }
    var content by rememberSaveable(initialContent, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialContent, TextRange(initialContent.length)))
    }
    var isPinned by rememberSaveable(initialPinned) { mutableStateOf(initialPinned) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    val maxTitleChars = remember(initialTitle) { maxOf(MAX_TITLE_CHARS, initialTitle.length) }
    val maxContentChars = remember(initialContent) { maxOf(MAX_CONTENT_CHARS, initialContent.length) }
    val focusRequester = remember { FocusRequester() }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val panelHeight = (screenHeight - 340.dp).coerceIn(300.dp, 440.dp)
    val editorFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
        disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .height(panelHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
        ) {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            when {
                                isEditing -> "编辑笔记"
                                folder == PrefsManager.DEFAULT_QUICK_NOTE_FOLDER_NAME -> "新建速记"
                                else -> "快速笔记"
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isSaving = true
                                onSave(title, content.text, isPinned) { isSaving = false }
                            },
                            enabled = !isSaving,
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Outlined.Check, contentDescription = "保存")
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isPinned = !isPinned },
                            enabled = !isSaving,
                        ) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "取消置顶" else "置顶",
                                tint = if (isPinned) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        TextButton(
                            onClick = { onOpenEditor(title, content.text, isPinned) },
                            enabled = !isSaving,
                        ) {
                            Text("完整编辑")
                        }
                        IconButton(onClick = onDismiss, enabled = !isSaving) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )

                Text(
                    text = if (folder.isBlank()) "保存位置：根目录" else "保存位置：$folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                Spacer(Modifier.height(4.dp))

                TextField(
                    value = title,
                    onValueChange = { title = it.take(maxTitleChars) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    placeholder = { Text("标题") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = editorFieldColors,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                TextField(
                    value = content,
                    onValueChange = { next ->
                        if (next.text.length <= maxContentChars) content = next
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .focusRequester(focusRequester),
                    placeholder = { Text("开始输入...") },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions(),
                    colors = editorFieldColors,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    HorizontalDivider(
                        thickness = 0.6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToolbarIconButton(
                            text = "[ ]",
                            contentDescription = "待办",
                            onClick = { content = toggleTaskAtCursor(content) },
                        )
                        ToolbarIconButton(
                            text = "H1",
                            bold = true,
                            contentDescription = "标题",
                            onClick = { content = toggleLinePrefix(content, "# ") },
                        )
                        ToolbarIconButton(
                            text = "-",
                            contentDescription = "列表",
                            onClick = { content = toggleLinePrefix(content, "- ") },
                        )
                        ToolbarIconButton(
                            text = "1.",
                            contentDescription = "编号",
                            onClick = { content = toggleLinePrefix(content, "1. ") },
                        )
                        ToolbarIconButton(
                            text = "",
                            icon = Icons.Outlined.FormatQuote,
                            contentDescription = "引用",
                            onClick = { content = toggleLinePrefix(content, "> ") },
                        )
                        ToolbarIconButton(
                            text = "B",
                            bold = true,
                            contentDescription = "加粗",
                            onClick = { content = wrapSelection(content, "**", "**", "文字") },
                        )
                        ToolbarIconButton(
                            text = "`",
                            contentDescription = "代码",
                            onClick = { content = wrapSelection(content, "`", "`", "代码") },
                        )
                        ToolbarIconButton(
                            text = "#",
                            contentDescription = "标签",
                            onClick = { content = insertAtCursor(content, "#标签") },
                        )
                    }
                }
            }
        }
    }
}

private fun currentLineRange(value: TextFieldValue): IntRange {
    val cursor = value.selection.start.coerceIn(0, value.text.length)
    val start = value.text.lastIndexOf('\n', (cursor - 1).coerceAtLeast(0)).let { index ->
        if (cursor == 0 || index < 0) 0 else index + 1
    }
    val end = value.text.indexOf('\n', cursor).let { index ->
        if (index < 0) value.text.length else index
    }
    return start until end
}

private fun toggleTaskAtCursor(value: TextFieldValue): TextFieldValue {
    val range = currentLineRange(value)
    val line = value.text.substring(range.first, range.last + 1)
    val taskRegex = Regex("""^\s*-\s*\[([ xX])\]\s*""")
    val match = taskRegex.find(line)
    val replacement = when {
        match == null -> "- [ ] $line"
        match.groupValues[1].isBlank() -> "- [x] ${line.removePrefix(match.value)}"
        else -> line.removePrefix(match.value)
    }
    val newText = value.text.replaceRange(range.first, range.last + 1, replacement)
    val newCursor = (range.first + replacement.length).coerceAtMost(newText.length)
    return TextFieldValue(newText, TextRange(newCursor))
}

private fun toggleLinePrefix(
    value: TextFieldValue,
    prefix: String,
): TextFieldValue {
    val range = currentLineRange(value)
    val line = value.text.substring(range.first, range.last + 1)
    val replacement = if (line.startsWith(prefix)) line.removePrefix(prefix) else prefix + line
    val newText = value.text.replaceRange(range.first, range.last + 1, replacement)
    val delta = replacement.length - line.length
    val newCursor = (value.selection.start + delta).coerceIn(range.first, newText.length)
    return TextFieldValue(newText, TextRange(newCursor))
}

private fun wrapSelection(
    value: TextFieldValue,
    startMarker: String,
    endMarker: String,
    placeholder: String,
): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val selected = value.text.substring(start, end)
    val inner = selected.ifBlank { placeholder }
    val replacement = startMarker + inner + endMarker
    val newText = value.text.replaceRange(start, end, replacement)
    val selection = if (selected.isBlank()) {
        TextRange(start + startMarker.length, start + startMarker.length + inner.length)
    } else {
        TextRange(start + replacement.length)
    }
    return TextFieldValue(newText, selection)
}

private fun insertAtCursor(
    value: TextFieldValue,
    inserted: String,
): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val newText = value.text.replaceRange(start, end, inserted)
    return TextFieldValue(newText, TextRange(start + inserted.length))
}
