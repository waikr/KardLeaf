package com.kangle.kardleaf.ui

import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.database.TaskGroupEntity
import com.kangle.kardleaf.data.task.TaskEditorResult
import com.kangle.kardleaf.data.task.TaskRepeat
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import com.kangle.kardleaf.ui.theme.LocalKardLeafGlobalCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafTaskCornerRadiusDp

internal const val UNGROUPED_TASK_GROUP_PICKER_ID = "__kardleaf_ungrouped__"
private const val MAX_VISIBLE_CHILD_TASKS = 3
private const val TASK_EDITOR_ENTER_DURATION_MS = 260

internal fun taskGroupPickerSelectionId(
    groups: List<TaskGroupEntity>,
    groupId: Long?,
): String =
    groupId
        ?.let { id -> groups.firstOrNull { it.id == id }?.name }
        ?.let(::normalizeFolderPathForUi)
        ?.takeIf(String::isNotBlank)
        ?: UNGROUPED_TASK_GROUP_PICKER_ID

internal fun taskGroupPickerNodes(groups: List<TaskGroupEntity>): List<FileTreePickerNode<Long?>> {
    val groupNodes = buildFileTreePickerNodes<Long?>(
        groups.sortedBy { it.sortOrder }.map { group ->
            normalizeFolderPathForUi(group.name) to (group.id as Long?)
        },
    )
    return buildList {
        add(
            FileTreePickerNode<Long?>(
                id = UNGROUPED_TASK_GROUP_PICKER_ID,
                label = "未分组",
                value = null,
            ),
        )
        addAll(groupNodes)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TaskEditorOverlay(
    visible: Boolean,
    task: TaskEntity?,
    groups: List<TaskGroupEntity> = emptyList(),
    initialGroupId: Long? = null,
    initialParentTaskId: Long? = null,
    autoFocusTitle: Boolean = false,
    openStartedAtMs: Long? = null,
    saveState: TaskEditorSaveState = TaskEditorSaveState.Idle,
    saveError: String? = null,
    drawScrim: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (TaskEditorResult) -> Unit,
) {
    val editorState = rememberTaskEditorState(task, initialGroupId, initialParentTaskId)
    val titleFocusRequester = remember(task?.id) { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
    var restoreFocusAfterDate by remember { mutableStateOf(false) }
    var panelReady by remember(task?.id) { mutableStateOf(false) }
    val traceStart = remember(openStartedAtMs) { openStartedAtMs ?: SystemClock.elapsedRealtime() }
    val taskCornerRadiusDp = LocalKardLeafTaskCornerRadiusDp.current.takeIf { it >= 0 }
        ?: LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
    val panelShape = RoundedCornerShape((taskCornerRadiusDp ?: 16).dp)

    LaunchedEffect(visible, autoFocusTitle, task?.id) {
        if (!visible) {
            panelReady = false
            focusManager.clearFocus(force = true)
            return@LaunchedEffect
        }
        KardLeafLog.d(
            KardLeafLogTags.USER_PERF,
            "taskEditor panelReady taskId=${task?.id ?: 0} elapsed=${SystemClock.elapsedRealtime() - traceStart}ms",
        )
        if (autoFocusTitle) {
            withFrameNanos { }
            titleFocusRequester.requestFocus()
        }
        panelReady = true
    }

    LaunchedEffect(editorState.showDatePicker, visible) {
        if (visible && !editorState.showDatePicker && restoreFocusAfterDate) {
            restoreFocusAfterDate = false
            withFrameNanos { }
            titleFocusRequester.requestFocus()
        }
    }

    fun openDatePicker() {
        if (saveState == TaskEditorSaveState.Saving) return
        restoreFocusAfterDate = true
        focusManager.clearFocus()
        editorState.showDatePicker = true
    }

    fun dismissEditor() {
        if (saveState == TaskEditorSaveState.Saving) return
        onDismiss()
    }

    fun submitTask() {
        if (saveState == TaskEditorSaveState.Saving) return
        val text = editorState.text.trim()
        if (text.isBlank()) {
            editorState.error = "任务描述不能为空"
            return
        }
        if (editorState.reminderAt != null && editorState.dueAt != null && editorState.reminderAt!! > editorState.dueAt!!) {
            editorState.error = "开始时间不能晚于结束时间"
            return
        }
        if (TaskRepeat.from(editorState.repeatRule) != TaskRepeat.NONE &&
            editorState.reminderAt == null &&
            editorState.dueAt == null
        ) {
            editorState.error = "重复任务需要开始或结束时间"
            return
        }
        editorState.error = null
        onSave(
            TaskEditorResult(
                text = text,
                done = editorState.done,
                groupId = editorState.groupId,
                priority = editorState.priority,
                dueAt = editorState.dueAt,
                reminderAt = editorState.reminderAt,
                repeatRule = editorState.repeatRule,
                notes = editorState.notes.trim(),
                reminderMode =
                    if (editorState.reminderPopup) {
                        TaskEntity.REMINDER_MODE_POPUP
                    } else {
                        TaskEntity.REMINDER_MODE_NOTIFICATION
                    },
                reminderRing = editorState.reminderRing,
                reminderVibrate = editorState.reminderVibrate,
                parentTaskId = editorState.parentTaskId,
                childTaskTexts = editorState.childTaskTexts,
            ),
        )
    }

    val handleBack = rememberUpdatedState {
        when {
            saveState == TaskEditorSaveState.Saving -> Unit
            editorState.showGroupMenu -> editorState.showGroupMenu = false
            editorState.showPriorityMenu -> editorState.showPriorityMenu = false
            else -> dismissEditor()
        }
    }

    BackHandler(enabled = visible && (saveState == TaskEditorSaveState.Saving || !editorState.showDatePicker)) {
        handleBack.value()
    }

    val hostWindow = (LocalView.current.context as? Activity)?.window
    DisposableEffect(hostWindow, visible, editorState.showDatePicker) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hostWindow != null && visible && !editorState.showDatePicker) {
            val callback = OnBackInvokedCallback { handleBack.value() }
            hostWindow.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
            onDispose { hostWindow.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback) }
        } else {
            onDispose { }
        }
    }

    val groupPickerNodes = remember(groups) { taskGroupPickerNodes(groups) }
    val panelProgress by animateFloatAsState(
        targetValue = if (visible && panelReady) 1f else 0f,
        animationSpec =
            if (visible && panelReady) {
                tween(TASK_EDITOR_ENTER_DURATION_MS, easing = LinearEasing)
            } else {
                snap()
            },
        label = "taskEditorPanel",
    )
    val panelEnterOffsetPx = with(density) { 24.dp.toPx() }

    if (visible) {
        Box(Modifier.fillMaxSize().zIndex(10f)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (drawScrim) Modifier.background(Color.Black.copy(alpha = 0.12f)) else Modifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (saveState != TaskEditorSaveState.Saving) dismissEditor() },
                    ),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .offset { IntOffset(0, -imeTargetBottomPx) }
                        .graphicsLayer {
                            alpha = panelProgress
                            translationY = (1f - panelProgress) * panelEnterOffsetPx
                        }
                        .heightIn(max = maxHeight)
                        .clip(panelShape)
                        .clickable(
                            enabled = panelReady,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    shape = panelShape,
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp,
                ) {
                    TaskEditorPanel(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        state = editorState,
                        task = task,
                        groups = groups,
                        groupPickerNodes = groupPickerNodes,
                        titleFocusRequester = titleFocusRequester,
                        saveState = saveState,
                        saveError = saveError,
                        onOpenDatePicker = ::openDatePicker,
                        onSelectGroup = { selectTaskEditorGroup(editorState, it) },
                        onSave = ::submitTask,
                    )
                }
            }
        }
    }

    if (editorState.showDatePicker) {
        TaskReminderPickerDialog(
            initialReminderAt = editorState.reminderAt,
            initialEndAt = editorState.dueAt,
            initialRepeatRule = TaskRepeat.from(editorState.repeatRule),
            onDismiss = { editorState.showDatePicker = false },
            onClear = {
                editorState.reminderAt = null
                editorState.dueAt = null
                editorState.repeatRule = TaskRepeat.NONE.value
                editorState.error = null
                editorState.showDatePicker = false
            },
            onDateRangeSelected = { startAt, endAt, repeat ->
                editorState.reminderAt = startAt
                editorState.dueAt = endAt
                editorState.repeatRule = repeat.value
                editorState.error = null
                editorState.showDatePicker = false
            },
        )
    }
}

private fun selectTaskEditorGroup(state: TaskEditorState, groupId: Long?) {
    if (state.groupId != groupId && state.parentTaskId != null) {
        state.parentTaskId = null
        state.notice = "移动到新分组后，该任务将成为顶级任务"
    }
    state.groupId = groupId
    state.showGroupMenu = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorPanel(
    modifier: Modifier,
    state: TaskEditorState,
    task: TaskEntity?,
    groups: List<TaskGroupEntity>,
    groupPickerNodes: List<FileTreePickerNode<Long?>>,
    titleFocusRequester: FocusRequester,
    saveState: TaskEditorSaveState,
    saveError: String?,
    onOpenDatePicker: () -> Unit,
    onSelectGroup: (Long?) -> Unit,
    onSave: () -> Unit,
) {
    val lightEditor = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val editorFieldColor =
        if (lightEditor) Color(0xFFF8F8F8) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    val editorPlaceholderColor =
        if (lightEditor) Color(0xFFC8C1C1) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    val editorHeaderHeight = 64.dp
    val editorToolbarHeight = 60.dp
    val editorIconSize = 26.dp
    val editorTextSize = 17.sp
    val editorNotesTextSize = 14.sp
    val editingEnabled = saveState != TaskEditorSaveState.Saving
    val taskCornerRadiusDp = LocalKardLeafTaskCornerRadiusDp.current.takeIf { it >= 0 }
        ?: LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
    val notesShape = RoundedCornerShape((taskCornerRadiusDp ?: 8).dp)
    val groupShape = RoundedCornerShape((taskCornerRadiusDp ?: 10).dp)
    val childFocusRequesters = remember(state.childTaskTexts.size) {
        List(state.childTaskTexts.size) { FocusRequester() }
    }

    LaunchedEffect(state.showInlineChildInput, state.focusedChildIndex, state.childTaskTexts.size) {
        val index = state.focusedChildIndex
        if (state.showInlineChildInput && index != null) {
            withFrameNanos { }
            childFocusRequesters.getOrNull(index)?.requestFocus()
        }
    }

    val childScrollState = rememberScrollState()
    LaunchedEffect(state.showInlineChildInput, state.childTaskTexts.size) {
        if (state.showInlineChildInput) {
            withFrameNanos { }
            childScrollState.scrollTo(childScrollState.maxValue)
        }
    }

    var ignoreNextGroupMenuToggle by remember { mutableStateOf(false) }
    LaunchedEffect(ignoreNextGroupMenuToggle) {
        if (ignoreNextGroupMenuToggle) {
            withFrameNanos { }
            ignoreNextGroupMenuToggle = false
        }
    }

    fun toggleInlineChildInput() {
        if (state.showInlineChildInput) {
            titleFocusRequester.requestFocus()
            state.showInlineChildInput = false
            state.childTaskTexts = emptyList()
            state.focusedChildIndex = null
        } else {
            state.showInlineChildInput = true
            state.childTaskTexts = listOf("")
            state.focusedChildIndex = 0
        }
    }

    fun updateChildTaskText(index: Int, value: String) {
        if (index !in state.childTaskTexts.indices) return
        val lines = value.replace('\r', '\n').split('\n')
        val next = state.childTaskTexts.toMutableList()
        next[index] = lines.first()
        if (lines.size > 1) {
            next.addAll(index + 1, lines.drop(1))
            state.focusedChildIndex = index + 1
        }
        state.childTaskTexts = next
    }

    fun insertChildLine(index: Int) {
        val (nextDrafts, nextIndex) = appendChildDraftLine(state.childTaskTexts, index)
        state.childTaskTexts = nextDrafts
        nextIndex?.let { state.focusedChildIndex = it }
    }

    fun removeEmptyChildLine(index: Int): Boolean {
        val (nextDrafts, nextIndex) = removeEmptyChildDraftLine(state.childTaskTexts, index) ?: return false
        state.childTaskTexts = nextDrafts
        state.focusedChildIndex = nextIndex
        return true
    }

    val editorPanelHeight =
        200.dp +
            (if (state.showInlineChildInput) {
                44.dp * state.childTaskTexts.size.coerceAtMost(MAX_VISIBLE_CHILD_TASKS)
            } else {
                0.dp
            }) +
            (if (state.reminderAt != null) 48.dp else 0.dp) +
            (if (state.error != null || saveError != null) 28.dp else 0.dp) +
            (if (state.notice != null) 28.dp else 0.dp)

    Box(modifier.fillMaxWidth().height(editorPanelHeight)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(editorHeaderHeight),
            ) {
                BasicTextField(
                    value = state.text,
                    onValueChange = {
                        if (editingEnabled) {
                            state.text = it.replace('\r', ' ').replace('\n', ' ')
                            state.error = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 18.dp, end = 112.dp)
                        .focusRequester(titleFocusRequester),
                    textStyle = MaterialTheme.typography.headlineLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Light,
                        fontSize = editorTextSize,
                    ),
                    singleLine = false,
                    maxLines = 1,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (state.text.isBlank()) {
                                Text(
                                    text = if (task == null) "添加一条新的任务" else "编辑任务",
                                    style = MaterialTheme.typography.headlineLarge.copy(
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

                val canSave = state.text.isNotBlank() && saveState != TaskEditorSaveState.Saving
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(enabled = canSave, onClick = onSave),
                    shape = CircleShape,
                    color = if (canSave) Color(0xFF1677FF) else Color(0xFFE5E7EB),
                    tonalElevation = 0.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (saveState == TaskEditorSaveState.Saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "保存",
                                tint = if (canSave) Color.White else Color(0xFF9CA3AF),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().height(76.dp),
                shape = notesShape,
                color = editorFieldColor,
            ) {
                BasicTextField(
                    value = state.notes,
                    onValueChange = { if (editingEnabled) state.notes = it },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = editorNotesTextSize,
                    ),
                    maxLines = 8,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (state.notes.isBlank()) {
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
            if (state.showInlineChildInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp * state.childTaskTexts.size.coerceAtMost(MAX_VISIBLE_CHILD_TASKS))
                        .verticalScroll(childScrollState),
                ) {
                    state.childTaskTexts.forEachIndexed { index, childText ->
                        Row(
                            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
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
                                onValueChange = { if (editingEnabled) updateChildTaskText(index, it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(childFocusRequesters[index])
                                    .onPreviewKeyEvent { event ->
                                        when {
                                            event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                                if (editingEnabled) insertChildLine(index)
                                                true
                                            }
                                            event.type == KeyEventType.KeyDown && event.key == Key.Backspace ->
                                                editingEnabled && removeEmptyChildLine(index)
                                            else -> false
                                        }
                                    },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = editorNotesTextSize,
                                ),
                                singleLine = false,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { if (editingEnabled) insertChildLine(index) }),
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
            }
            Row(
                modifier = Modifier.fillMaxWidth().height(editorToolbarHeight),
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
                        onClick = { if (editingEnabled) onOpenDatePicker() },
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
                        onClick = { if (editingEnabled) state.showPriorityMenu = true },
                    )
                    DropdownMenu(
                        expanded = state.showPriorityMenu,
                        onDismissRequest = { state.showPriorityMenu = false },
                        properties = PopupProperties(focusable = false, dismissOnBackPress = false),
                    ) {
                        (0..3).forEach { value ->
                            DropdownMenuItem(
                                text = { Text(priorityLabel(value)) },
                                onClick = {
                                    if (editingEnabled) {
                                        state.priority = value
                                        state.showPriorityMenu = false
                                    }
                                },
                            )
                        }
                    }
                }
                TaskEditorToolbarButton(
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.task_subtask),
                            contentDescription = "子任务",
                            tint = if (!state.showInlineChildInput && state.parentTaskId == null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(editorIconSize),
                        )
                    },
                    onClick = { if (editingEnabled) toggleInlineChildInput() },
                )
                Spacer(Modifier.weight(1f))
                Box {
                    Row(
                        modifier = Modifier
                            .clip(groupShape)
                            .clickable(enabled = editingEnabled) {
                                if (ignoreNextGroupMenuToggle) {
                                    ignoreNextGroupMenuToggle = false
                                } else {
                                    state.showGroupMenu = !state.showGroupMenu
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = groups.firstOrNull { it.id == state.groupId }?.name?.substringAfterLast('/') ?: "未分组",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择分组")
                    }
                    if (state.showGroupMenu) {
                        FileTreePickerDialog(
                            title = "选择分组",
                            nodes = groupPickerNodes,
                            selectedId = taskGroupPickerSelectionId(groups, state.groupId),
                            selectionMode = FileTreeSelectionMode.FOLDER,
                            forceAboveAnchor = true,
                            onSelect = { node -> if (editingEnabled) onSelectGroup(node.value) },
                            onDismiss = {
                                state.showGroupMenu = false
                                ignoreNextGroupMenuToggle = true
                            },
                        )
                    }
                }
            }
            if (state.reminderAt != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = state.reminderPopup, onClick = { if (editingEnabled) state.reminderPopup = true }, label = { Text("弹窗+通知") })
                    FilterChip(selected = !state.reminderPopup, onClick = { if (editingEnabled) state.reminderPopup = false }, label = { Text("仅通知") })
                    FilterChip(selected = state.reminderRing, onClick = { if (editingEnabled) state.reminderRing = !state.reminderRing }, label = { Text("响铃") })
                    FilterChip(selected = state.reminderVibrate, onClick = { if (editingEnabled) state.reminderVibrate = !state.reminderVibrate }, label = { Text("震动") })
                }
            }
            state.notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            state.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            saveError?.let {
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

@Composable
private fun TaskEditorToolbarButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
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
