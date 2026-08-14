package com.kangle.kardleaf.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.kangle.kardleaf.BuildConfig
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.database.TaskGroupEntity
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.task.DuplicateTaskIdConflict
import com.kangle.kardleaf.data.task.MarkdownTaskItem
import com.kangle.kardleaf.data.task.MarkdownTaskParserCache
import com.kangle.kardleaf.data.task.MissingTaskMarkdownFile
import com.kangle.kardleaf.data.task.TaskCompletionFeedback
import com.kangle.kardleaf.data.task.TaskEditorResult
import com.kangle.kardleaf.data.task.TaskMarkdownStore
import com.kangle.kardleaf.data.task.TaskReminderScheduler
import com.kangle.kardleaf.data.task.TaskRepeat
import com.kangle.kardleaf.data.task.toTaskEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.ReorderableColumn
import sh.calvin.reorderable.ReorderableItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private data class TaskGroupTreeRow(
    val group: TaskGroupEntity,
    val depth: Int,
)

private fun taskGroupTreeRows(groups: List<TaskGroupEntity>): List<TaskGroupTreeRow> {
    val ordered = groups.sortedBy { it.sortOrder }
    val paths = ordered.associateBy { normalizeFolderPathForUi(it.name) }
    val children =
        ordered.groupBy { group ->
            normalizeFolderPathForUi(group.name)
                .substringBeforeLast('/', missingDelimiterValue = "")
                .takeIf(paths::containsKey)
                .orEmpty()
        }
    return buildList {
        fun append(
            parentPath: String,
            depth: Int,
        ) {
            children[parentPath].orEmpty().forEach { group ->
                add(TaskGroupTreeRow(group, depth))
                append(normalizeFolderPathForUi(group.name), depth + 1)
            }
        }
        append("", 0)
    }
}

@Composable
private fun TaskGroupMenuRow(
    label: String,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp + 20.dp * depth, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Icon(Icons.Outlined.Check, contentDescription = null)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskEditorDialog(
    task: TaskEntity?,
    groups: List<TaskGroupEntity> = emptyList(),
    parentTaskIds: Map<Long, Long> = emptyMap(),
    initialGroupId: Long? = null,
    initialParentTaskId: Long? = null,
    autoFocusTitle: Boolean = false,
    useDialog: Boolean = true,
    openStartedAtMs: Long? = null,
    onDismiss: () -> Unit,
    onSave: (TaskEditorResult) -> Unit,
) {
    val editorState = rememberTaskEditorState(task, initialGroupId, initialParentTaskId, parentTaskIds)
    with(editorState) {
    val titleFocusRequester = remember { FocusRequester() }
    val lightEditor = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val editorFieldColor = if (lightEditor) Color(0xFFF8F8F8) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    val editorPlaceholderColor = if (lightEditor) Color(0xFFC8C1C1) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    val editorHeaderHeight = 64.dp
    val editorToolbarHeight = 60.dp
    val editorIconSize = 26.dp
    val editorTextSize = 17.sp
    val editorNotesTextSize = 14.sp
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val editorTraceStartMs = remember(openStartedAtMs) { openStartedAtMs ?: SystemClock.elapsedRealtime() }
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    var editorFirstLayoutLogged by remember { mutableStateOf(false) }
    var titleFocusRequested by remember { mutableStateOf(false) }
    val editorPanelHeight =
        200.dp +
            (if (showInlineChildInput) 44.dp * childTaskTexts.size else 0.dp) +
            (if (reminderAt != null) 48.dp else 0.dp) +
            (if (error != null) 28.dp else 0.dp)
    val childFocusRequesters =
        remember(childTaskTexts.size) {
            List(childTaskTexts.size) { FocusRequester() }
        }

    LaunchedEffect(Unit) {
        KardLeafLog.d(
            KardLeafLogTags.USER_PERF,
            "taskEditor firstCompose taskId=${task?.id ?: 0} groups=${groups.size} parentIds=${parentTaskIds.size} elapsed=${SystemClock.elapsedRealtime() - editorTraceStartMs}ms",
        )
        snapshotFlow { imeInsets.getBottom(density) }.collect { bottomPx: Int ->
            KardLeafLog.d(
                KardLeafLogTags.USER_PERF,
                "taskEditor imeInsets bottom=$bottomPx visible=${bottomPx > 0} taskId=${task?.id ?: 0} elapsed=${SystemClock.elapsedRealtime() - editorTraceStartMs}ms",
            )
        }
    }
    LaunchedEffect(showInlineChildInput, focusedChildIndex, childTaskTexts.size) {
        val index = focusedChildIndex
        if (showInlineChildInput && index != null) {
            withFrameNanos { }
            childFocusRequesters.getOrNull(index)?.requestFocus()
        }
    }
    fun dismissEditor() {
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        onDismiss()
    }

    BackHandler(enabled = !showDatePicker) {
        if (showGroupMenu || showPriorityMenu) {
            showGroupMenu = false
            showPriorityMenu = false
        } else {
            dismissEditor()
        }
    }

    val groupMenuRows = remember(groups) { taskGroupTreeRows(groups) }

    fun submitTask() {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            error = "任务描述不能为空"
            return
        }
        val selectedStartAt = reminderAt
        val selectedEndAt = dueAt
        if (selectedStartAt != null && selectedEndAt != null && selectedStartAt > selectedEndAt) {
            error = "开始时间不能晚于结束时间"
            return
        }
        if (TaskRepeat.from(repeatRule) != TaskRepeat.NONE && selectedEndAt == null && selectedStartAt == null) {
            error = "重复任务需要开始或结束时间"
            return
        }
        onSave(
            TaskEditorResult(
                text = trimmedText,
                done = done,
                groupId = groupId,
                priority = priority,
                dueAt = dueAt,
                reminderAt = reminderAt,
                repeatRule = repeatRule,
                notes = notes.trim(),
                reminderMode = if (reminderPopup) TaskEntity.REMINDER_MODE_POPUP else TaskEntity.REMINDER_MODE_NOTIFICATION,
                reminderRing = reminderRing,
                reminderVibrate = reminderVibrate,
                parentTaskId = parentTaskId,
                parentTaskSelectionChanged = parentTaskSelectionChanged,
                childTaskTexts = childTaskTexts,
            ),
        )
    }

    fun toggleInlineChildInput() {
        if (showInlineChildInput) {
            titleFocusRequester.requestFocus()
            showInlineChildInput = false
            childTaskTexts = emptyList()
            focusedChildIndex = null
        } else {
            showInlineChildInput = true
            childTaskTexts = listOf("")
            focusedChildIndex = 0
        }
    }

    fun updateChildTaskText(
        index: Int,
        value: String,
    ) {
        if (index !in childTaskTexts.indices) return
        val lines = value.replace('\r', '\n').split('\n')
        val next = childTaskTexts.toMutableList()
        next[index] = lines.first()
        if (lines.size > 1) {
            next.addAll(index + 1, lines.drop(1))
            focusedChildIndex = index + 1
        }
        childTaskTexts = next
    }

    fun insertChildLine(index: Int) {
        val (nextDrafts, nextIndex) = appendChildDraftLine(childTaskTexts, index)
        childTaskTexts = nextDrafts
        nextIndex?.let { focusedChildIndex = it }
    }

    fun removeEmptyChildLine(index: Int): Boolean {
        val (nextDrafts, nextIndex) = removeEmptyChildDraftLine(childTaskTexts, index) ?: return false
        childTaskTexts = nextDrafts
        focusedChildIndex = nextIndex
        return true
    }

    val editorPanel: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(editorPanelHeight),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(editorHeaderHeight),
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it.replace('\r', ' ').replace('\n', ' ')
                            error = null
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 8.dp,
                                    top = 18.dp,
                                    end = 112.dp,
                                )
                                .focusRequester(titleFocusRequester)
                                .onGloballyPositioned {
                                    if (autoFocusTitle && !titleFocusRequested) {
                                        titleFocusRequested = true
                                        KardLeafLog.d(
                                            KardLeafLogTags.USER_PERF,
                                            "taskEditor titleLayout taskId=${task?.id ?: 0} elapsed=${SystemClock.elapsedRealtime() - editorTraceStartMs}ms",
                                        )
                                        val focusRequest = runCatching { titleFocusRequester.requestFocus() }
                                        focusRequest.fold(
                                            onSuccess = { _ ->
                                                KardLeafLog.d(
                                                    KardLeafLogTags.USER_PERF,
                                                    "taskEditor focusResult taskId=${task?.id ?: 0} requested=true elapsed=${SystemClock.elapsedRealtime() - editorTraceStartMs}ms",
                                                )
                                            },
                                            onFailure = { error ->
                                                KardLeafLog.w(
                                                    KardLeafLogTags.USER_PERF,
                                                    "taskEditor focusResult taskId=${task?.id ?: 0} requested=false error=${error.javaClass.simpleName}",
                                                )
                                            },
                                        )
                                    }
                                },
                        textStyle =
                            MaterialTheme.typography.headlineLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Light,
                                fontSize = editorTextSize,
                            ),
                        singleLine = false,
                        maxLines = 1,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (text.isBlank()) {
                                    Text(
                                        text = if (task == null) "添加一条新的任务" else "编辑任务",
                                        style =
                                            MaterialTheme.typography.headlineLarge.copy(
                                                color = editorPlaceholderColor,
                                                fontWeight = FontWeight.Light,
                                                fontSize = editorTextSize,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )

                    val canSave = text.isNotBlank()
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .clickable(enabled = canSave, onClick = ::submitTask),
                        shape = CircleShape,
                        color = if (canSave) Color(0xFF1677FF) else Color(0xFFE5E7EB),
                        tonalElevation = 0.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "保存",
                                tint = if (canSave) Color.White else Color(0xFF9CA3AF),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(76.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = editorFieldColor,
                ) {
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp,
                                ),
                        textStyle =
                            MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = editorNotesTextSize,
                            ),
                        maxLines = 8,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (notes.isBlank()) {
                                    Text(
                                        text = "请输入",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = editorNotesTextSize),
                                        color = editorPlaceholderColor,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
                if (showInlineChildInput) {
                    childTaskTexts.forEachIndexed { index, childText ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "·",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 20.sp,
                                modifier = Modifier.width(20.dp),
                            )
                            BasicTextField(
                                value = childText,
                                onValueChange = { updateChildTaskText(index, it) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(childFocusRequesters[index])
                                        .onPreviewKeyEvent { event ->
                                            when {
                                                event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                                    insertChildLine(index)
                                                    true
                                                }
                                                event.type == KeyEventType.KeyDown && event.key == Key.Backspace -> {
                                                    removeEmptyChildLine(index)
                                                }
                                                else -> false
                                            }
                                        },
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = editorNotesTextSize,
                                    ),
                                singleLine = false,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { insertChildLine(index) }),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (childText.isBlank()) {
                                            Text(
                                                text = "添加子任务",
                                                color = editorPlaceholderColor,
                                                fontSize = editorNotesTextSize,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(editorToolbarHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TaskEditorToolbarButton(
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.task_date),
                                contentDescription = "日期",
                                modifier = Modifier.size(editorIconSize),
                            )
                        },
                        onClick = { showDatePicker = true },
                    )
                    Box {
                        TaskEditorToolbarButton(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.task_priority),
                                    contentDescription = "优先级",
                                    modifier = Modifier.size(editorIconSize),
                                )
                            },
                            onClick = { showPriorityMenu = true },
                        )
                        DropdownMenu(
                            expanded = showPriorityMenu,
                            onDismissRequest = { showPriorityMenu = false },
                            properties = PopupProperties(focusable = false),
                        ) {
                            (0..3).forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(priorityLabel(value)) },
                                    onClick = {
                                        priority = value
                                        showPriorityMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        TaskEditorToolbarButton(
                            icon = {
                                Icon(
                                    painter = painterResource(R.drawable.task_subtask),
                                    contentDescription = "子任务",
                                    tint =
                                        if (!showInlineChildInput && parentTaskId == null) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    modifier = Modifier.size(editorIconSize),
                                )
                            },
                            onClick = ::toggleInlineChildInput,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Box {
                        Row(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showGroupMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = groups.firstOrNull { it.id == groupId }?.name?.substringAfterLast('/') ?: "未分组",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择分组")
                        }
                    }
                }
                if (reminderAt != null) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = reminderPopup,
                            onClick = { reminderPopup = true },
                            label = { Text("弹窗+通知") },
                        )
                        FilterChip(
                            selected = !reminderPopup,
                            onClick = { reminderPopup = false },
                            label = { Text("仅通知") },
                        )
                        FilterChip(
                            selected = reminderRing,
                            onClick = { reminderRing = !reminderRing },
                            label = { Text("响铃") },
                        )
                        FilterChip(
                            selected = reminderVibrate,
                            onClick = { reminderVibrate = !reminderVibrate },
                            label = { Text("震动") },
                        )
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
    }

    val panelShape = RoundedCornerShape(16.dp)
    val editorWindowContent: @Composable () -> Unit = {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        val hostWindow = dialogWindow ?: (LocalView.current.context as? Activity)?.window
        DisposableEffect(hostWindow) {
            hostWindow?.setDimAmount(0.12f)
            KardLeafLog.d(
                KardLeafLogTags.USER_PERF,
                "taskEditor windowReady taskId=${task?.id ?: 0} dialogWindow=${dialogWindow != null} elapsed=${SystemClock.elapsedRealtime() - editorTraceStartMs}ms",
            )
            onDispose { }
        }
        DisposableEffect(dialogWindow, showDatePicker, showGroupMenu, showPriorityMenu) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hostWindow != null && !showDatePicker) {
                val callback = OnBackInvokedCallback {
                    if (showGroupMenu || showPriorityMenu) {
                        showGroupMenu = false
                        showPriorityMenu = false
                    } else {
                        dismissEditor()
                    }
                }
                hostWindow.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                    callback,
                )
                onDispose {
                    hostWindow.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
                }
            } else {
                onDispose { }
            }
        }
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .onGloballyPositioned { coordinates ->
                        if (!editorFirstLayoutLogged) {
                            editorFirstLayoutLogged = true
                            KardLeafLog.d(
                                KardLeafLogTags.USER_PERF,
                                "taskEditor firstLayout taskId=${task?.id ?: 0} size=${coordinates.size.width}x${coordinates.size.height} elapsed=${SystemClock.elapsedRealtime() - editorTraceStartMs}ms",
                            )
                        }
                    }
                    .clickable(onClick = ::dismissEditor),
        ) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(editorPanelHeight.coerceAtMost(maxHeight))
                        .clip(panelShape)
                        .clickable { },
                shape = panelShape,
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                editorPanel(Modifier.padding(horizontal = 16.dp))
            }
            if (showGroupMenu) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .zIndex(2f),
                ) {
                    Spacer(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { showGroupMenu = false },
                    )
                    Surface(
                        modifier =
                            Modifier
                                .widthIn(min = 260.dp, max = 320.dp)
                                .fillMaxHeight()
                                .clickable { },
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 3.dp,
                    ) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text(
                                text = "选择分组",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            TaskGroupMenuRow(
                                label = "未分组",
                                depth = 0,
                                selected = groupId == null,
                                onClick = {
                                    if (groupId != null && parentTaskId != null) {
                                        parentTaskId = null
                                        parentTaskSelectionChanged = true
                                    }
                                    groupId = null
                                    showGroupMenu = false
                                },
                            )
                            groupMenuRows.forEach { row ->
                                TaskGroupMenuRow(
                                    label = row.group.name.substringAfterLast('/'),
                                    depth = row.depth,
                                    selected = row.group.id == groupId,
                                    onClick = {
                                        if (groupId != row.group.id && parentTaskId != null) {
                                            parentTaskId = null
                                            parentTaskSelectionChanged = true
                                        }
                                        groupId = row.group.id
                                        showGroupMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (useDialog) {
        Dialog(
            onDismissRequest = ::dismissEditor,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    decorFitsSystemWindows = false,
                ),
        ) {
            editorWindowContent()
        }
    } else {
        editorWindowContent()
    }

    if (showDatePicker) {
        TaskReminderPickerDialog(
            initialReminderAt = reminderAt,
            initialEndAt = dueAt,
            initialRepeatRule = TaskRepeat.from(repeatRule),
            onDismiss = { showDatePicker = false },
            onClear = {
                reminderAt = null
                dueAt = null
                repeatRule = TaskRepeat.NONE.value
                error = null
                showDatePicker = false
            },
            onDateRangeSelected = { startAt, endAt, repeat ->
                reminderAt = startAt
                dueAt = endAt
                repeatRule = repeat.value
                error = null
                showDatePicker = false
            },
        )
    }
    }
}

@Composable
private fun TaskEditorToolbarButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

private fun priorityLabel(priority: Int): String =
    when (priority) {
        3 -> "高优先级"
        2 -> "中优先级"
        1 -> "低优先级"
        else -> "无优先级"
    }
