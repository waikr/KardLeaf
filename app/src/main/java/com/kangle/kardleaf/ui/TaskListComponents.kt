package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.kangle.kardleaf.data.task.TaskHierarchy
import com.kangle.kardleaf.data.task.TaskMarkdownStore
import com.kangle.kardleaf.data.task.TaskReminderScheduler
import com.kangle.kardleaf.data.task.TaskRepeat
import com.kangle.kardleaf.data.task.TaskTimeRules
import com.kangle.kardleaf.data.task.toTaskEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import com.kangle.kardleaf.ui.theme.LocalKardLeafGlobalCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafTaskCornerRadiusDp
import com.kangle.kardleaf.ui.theme.LocalKardLeafThemeStyle
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

internal const val TASK_DAY_MILLIS = 24L * 60L * 60L * 1_000L

@Composable
internal fun TaskSection(
    title: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    tasks: List<TaskEntity>,
    editMode: Boolean,
    onReorder: (Int, Int) -> Unit,
    rowContent: @Composable (TaskEntity, Modifier) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val themeStyle = LocalKardLeafThemeStyle.current
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val isDracula = themeStyle == PrefsManager.AppThemeStyle.DRACULA
    val isCleanList = themeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST
    val isCleanListLight = isCleanList && MaterialTheme.colorScheme.background.luminance() >= 0.5f
    val taskCornerRadiusDp = LocalKardLeafTaskCornerRadiusDp.current.takeIf { it >= 0 }
        ?: LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
    val shape = RoundedCornerShape(
        (taskCornerRadiusDp ?: PrefsManager.DEFAULT_TASK_CORNER_RADIUS_DP).dp,
    )
    val containerColor = when {
        isDracula -> MaterialTheme.colorScheme.surfaceContainer
        isCleanListLight -> Color.White
        isCleanList -> MaterialTheme.colorScheme.surface
        isModern -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isDracula -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        isCleanListLight -> Color(0xFFE5E7EB)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isModern) 0.48f else 0.5f)
    }
    val borderWidth = if (isDracula) 1.5.dp else 1.dp
    val cardElevation = if (isModern) {
        when {
            isCleanListLight -> 0.dp
            isDracula -> 1.dp
            else -> 3.dp
        }
    } else {
        1.dp
    }
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = shape,
        color = containerColor,
        border = BorderStroke(borderWidth, borderColor),
        shadowElevation = cardElevation,
    ) {
        Column {
            val interactionSource = remember { MutableInteractionSource() }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onToggle,
                        )
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = if (collapsed) "展开$title" else "收起$title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).size(24.dp).scale(0.8f),
                )
            }
            if (!collapsed) {
                if (editMode) {
                    ReorderableColumn(
                        list = tasks,
                        onSettle = onReorder,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) { _, task, isDragging ->
                        key(task.id) {
                            ReorderableItem {
                                rowContent(
                                    task,
                                    Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (isDragging) {
                                                Modifier.border(
                                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                                                    RoundedCornerShape(12.dp),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .longPressDraggableHandle(
                                            onDragStarted = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onDragStopped = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                        ),
                                )
                            }
                        }
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        tasks.forEach { task ->
                            key(task.id) {
                                rowContent(task, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompletedTasksHeader(
    title: String = "已完成",
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggle,
                )
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp).scale(0.8f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
        )
        Text("$count 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun GroupEditorDialog(
    group: TaskGroupEntity?,
    parentPath: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(group?.id, parentPath) {
        mutableStateOf(group?.name?.substringAfterLast('/').orEmpty())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "新建分组" else "重命名分组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (parentPath.isNotBlank()) {
                    Text(
                        text = "上级分组：$parentPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分组名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun EmptyTaskState(
    filter: TaskFilter,
    searching: Boolean,
    filteringGroup: Boolean,
    trash: Boolean = false,
) {
    val taskCornerRadiusDp = LocalKardLeafTaskCornerRadiusDp.current.takeIf { it >= 0 }
        ?: LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
    Surface(
        shape = RoundedCornerShape((taskCornerRadiusDp ?: PrefsManager.DEFAULT_TASK_CORNER_RADIUS_DP).dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text =
                when {
                    searching -> "没有匹配的任务。"
                    trash -> "任务回收站为空。"
                    filter != TaskFilter.ALL -> "“${filter.label}”中暂无任务。"
                    filteringGroup -> "此清单中暂无任务。"
                    else -> "还没有任务。点击右下角的新建按钮即可开始。"
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun TaskRow(
    task: TaskEntity,
    groups: List<TaskGroupEntity>,
    isSelected: Boolean,
    selectionMode: Boolean,
    isTrash: Boolean,
    editMode: Boolean = false,
    indentLevel: Int = 0,
    dragModifier: Modifier = Modifier,
    hasChildren: Boolean = false,
    expanded: Boolean = false,
    onToggleChildren: () -> Unit = {},
    showGroupName: Boolean,
    onToggleDone: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onOpenNotePath: (String) -> Unit,
) {
    var confirmDelete by remember(task.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }
    val dismissState =
        rememberSwipeToDismissBoxState(
            positionalThreshold = { swipeThresholdPx },
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd,
                    SwipeToDismissBoxValue.EndToStart,
                    -> {
                        if (selectionMode) {
                            onSelect()
                        } else if (value == SwipeToDismissBoxValue.StartToEnd) {
                            onToggleDone(!task.done)
                        } else {
                            confirmDelete = true
                        }
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> true
                }
            },
        )
    val priorityColor =
        when (task.priority) {
            3 -> MaterialTheme.colorScheme.error
            2 -> MaterialTheme.colorScheme.tertiary
            1 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }
    val groupName =
        task.groupId
            ?.let { groupId -> groups.firstOrNull { it.id == groupId }?.name }
            ?.substringAfterLast('/')

    @Composable
    fun TaskContent(modifier: Modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier,
        ) {
            val clickModifier =
                if (editMode) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (selectionMode || isTrash) onSelect() else onEdit() },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress()
                        },
                    )
                }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(clickModifier)
                        .padding(start = (indentLevel * 20).dp)
                        .padding(horizontal = 10.dp)
                        .padding(vertical = 1.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (selectionMode) {
                    IconButton(
                        onClick = onSelect,
                        modifier = Modifier.width(48.dp).height(38.dp),
                    ) {
                        TaskSelectionCircle(selected = isSelected)
                    }
                } else {
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { checked ->
                            if (isTrash) onSelect() else onToggleDone(checked)
                        },
                        colors = CheckboxDefaults.colors(checkedColor = priorityColor, uncheckedColor = priorityColor),
                        modifier = Modifier.width(48.dp).height(38.dp).scale(0.8f),
                    )
                }
                Column(
                    modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = task.taskText,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp, lineHeight = 19.sp),
                            color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (task.notes.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = "有详情",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp).size(16.dp).scale(0.8f),
                            )
                        }
                        if (hasChildren) {
                            IconButton(
                                onClick = onToggleChildren,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "收起子任务" else "展开子任务",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp).scale(0.8f),
                                )
                            }
                        }
                    }
                    TaskMeta(
                        task = task,
                        groupName = if (showGroupName) groupName ?: "未分组" else null,
                        onOpenNotePath = onOpenNotePath,
                    )
                }
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(dragModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isTrash || editMode) {
            TaskContent(Modifier.fillMaxWidth())
        } else {
            SwipeToDismissBox(
                state = dismissState,
                modifier = Modifier.fillMaxWidth(),
                backgroundContent = {
                    val completing = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                    val actionReady =
                        when (dismissState.dismissDirection) {
                            SwipeToDismissBoxValue.StartToEnd -> dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd
                            SwipeToDismissBoxValue.EndToStart -> dismissState.targetValue == SwipeToDismissBoxValue.EndToStart
                            SwipeToDismissBoxValue.Settled -> false
                        }
                    val backgroundColor =
                        when {
                            !actionReady -> MaterialTheme.colorScheme.surfaceVariant
                            completing -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    backgroundColor,
                                )
                                .padding(horizontal = 20.dp),
                        contentAlignment = if (completing) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Icon(
                            imageVector = if (completing) Icons.Outlined.Check else Icons.Outlined.Delete,
                            contentDescription = null,
                            tint =
                                if (!actionReady) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else if (completing) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                        )
                    }
                },
            ) {
                TaskContent(Modifier.fillMaxWidth())
            }
        }
        Spacer(modifier = Modifier.height(1.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("移入任务回收站？") },
            text = { Text("“${task.taskText}”会移入任务回收站，可稍后恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TaskSelectionCircle(selected: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.size(18.dp),
        shape = CircleShape,
        color = if (selected) colorScheme.primary else colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(
                width = 2.dp,
                color = if (selected) colorScheme.primary else colorScheme.outline,
            ),
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = colorScheme.onPrimary,
                modifier = Modifier.padding(3.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailScreen(
    task: TaskEntity,
    groups: List<TaskGroupEntity>,
    subtasks: List<TaskEntity> = emptyList(),
    subtaskIndentLevels: Map<Long, Int> = emptyMap(),
    onBack: () -> Unit,
    saving: Boolean = false,
    onSave: (TaskEntity, List<TaskEntity>, List<String>, (Boolean) -> Unit) -> Unit,
    onToggleDone: (Boolean) -> Unit,
    onToggleSubtaskDone: (TaskEntity, Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val groupName =
        task.groupId
            ?.let { groupId -> groups.firstOrNull { it.id == groupId }?.name }
            ?.substringAfterLast('/')
            ?: "未分组"
    var editedTask by remember(task.id, task.updatedAt) { mutableStateOf(task) }
    var editedSubtasks by remember(task.id, subtasks) { mutableStateOf(subtasks) }
    var newSubtaskTexts by remember(task.id, task.updatedAt) { mutableStateOf(emptyList<String>()) }
    var subtasksVisible by rememberSaveable(task.id) { mutableStateOf(true) }
    var showMoreMenu by rememberSaveable(task.id) { mutableStateOf(false) }
    var showPriorityMenu by rememberSaveable(task.id) { mutableStateOf(false) }
    var showDatePicker by rememberSaveable(task.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(task.id) { mutableStateOf(false) }
    var pendingNewSubtaskFocusIndex by remember(task.id) { mutableStateOf<Int?>(null) }
    val taskCornerRadiusDp = LocalKardLeafTaskCornerRadiusDp.current.takeIf { it >= 0 }
        ?: LocalKardLeafGlobalCornerRadiusDp.current.takeIf { it >= 0 }
    val canSave = !saving && editedTask.taskText.isNotBlank()
    val newSubtaskFocusRequesters = remember(newSubtaskTexts.size) {
        List(newSubtaskTexts.size) { FocusRequester() }
    }

    fun saveDetails(source: String) {
        KardLeafLog.d(
            KardLeafLogTags.TASK_SAVE,
            "detail save request source=$source taskId=${task.id} " +
                "titleLen=${editedTask.taskText.length} notesLen=${editedTask.notes.length} " +
                "editedSubtasks=${editedSubtasks.size} newSubtasks=${newSubtaskTexts.count(String::isNotBlank)} " +
                "saving=$saving",
        )
        if (!canSave) {
            KardLeafLog.w(
                KardLeafLogTags.TASK_SAVE,
                "detail save skipped source=$source taskId=${task.id} reason=" +
                    if (saving) "saving" else "blank-title",
            )
            return
        }
        showMoreMenu = false
        KardLeafLog.d(KardLeafLogTags.TASK_SAVE, "detail save dispatch source=$source taskId=${task.id}")
        onSave(editedTask, editedSubtasks, newSubtaskTexts) { }
    }

    fun addNewSubtaskInput() {
        val lastIndex = newSubtaskTexts.lastIndex
        if (lastIndex >= 0 && newSubtaskTexts[lastIndex].isBlank()) {
            pendingNewSubtaskFocusIndex = lastIndex
            return
        }
        newSubtaskTexts = newSubtaskTexts + ""
        pendingNewSubtaskFocusIndex = newSubtaskTexts.lastIndex
    }

    fun addNextSubtask(index: Int) {
        if (newSubtaskTexts.getOrNull(index).isNullOrBlank()) {
            KardLeafLog.d(
                KardLeafLogTags.TASK_SAVE,
                "detail new subtask next skipped taskId=${task.id} index=$index reason=blank",
            )
            return
        }
        newSubtaskTexts = newSubtaskTexts.toMutableList().apply { add(index + 1, "") }
        pendingNewSubtaskFocusIndex = index + 1
        KardLeafLog.d(
            KardLeafLogTags.TASK_SAVE,
            "detail new subtask next taskId=${task.id} index=$index nextIndex=${index + 1} " +
                "newSubtasks=${newSubtaskTexts.size}",
        )
    }

    val hasUnsavedChanges =
        editedTask.taskText != task.taskText ||
            editedTask.notes != task.notes ||
            editedTask.priority != task.priority ||
            editedTask.reminderAt != task.reminderAt ||
            editedTask.dueAt != task.dueAt ||
            editedTask.repeatRule != task.repeatRule ||
            editedSubtasks.map { it.id to it.taskText } != subtasks.map { it.id to it.taskText } ||
            newSubtaskTexts.any(String::isNotBlank)

    fun saveAndBack(source: String = "system-back") {
        KardLeafLog.d(
            KardLeafLogTags.TASK_SAVE,
            "detail back request source=$source taskId=${task.id} hasChanges=$hasUnsavedChanges " +
                "canSave=$canSave saving=$saving",
        )
        if (saving) {
            KardLeafLog.w(KardLeafLogTags.TASK_SAVE, "detail back skipped source=$source taskId=${task.id} reason=saving")
            return
        }
        if (hasUnsavedChanges && canSave) {
            KardLeafLog.d(KardLeafLogTags.TASK_SAVE, "detail back save dispatch source=$source taskId=${task.id}")
            onSave(editedTask, editedSubtasks, newSubtaskTexts) { saved ->
                KardLeafLog.d(
                    KardLeafLogTags.TASK_SAVE,
                    "detail back save completed source=$source taskId=${task.id} success=$saved",
                )
            }
            KardLeafLog.d(KardLeafLogTags.TASK_SAVE, "detail back close source=$source taskId=${task.id} savePending=true")
        } else {
            KardLeafLog.d(
                KardLeafLogTags.TASK_SAVE,
                "detail back no-save source=$source taskId=${task.id} " +
                    "reason=" + if (!hasUnsavedChanges) "unchanged" else "cannot-save",
            )
        }
        onBack()
    }

    LaunchedEffect(pendingNewSubtaskFocusIndex, newSubtaskTexts.size) {
        val index = pendingNewSubtaskFocusIndex ?: return@LaunchedEffect
        if (index !in newSubtaskFocusRequesters.indices) return@LaunchedEffect
        withFrameNanos {
            newSubtaskFocusRequesters[index].requestFocus()
        }
        pendingNewSubtaskFocusIndex = null
    }

    BackHandler(enabled = true, onBack = { saveAndBack("system-back") })

    val now = System.currentTimeMillis()
    val taskTime =
        editedTask.dueAt?.let { taskDateLabel(it, now, defaultHour = 23, defaultMinute = 59) }
            ?: editedTask.reminderAt?.let { taskDateLabel(it, now, defaultHour = 9, defaultMinute = 0) }
    val repeat = TaskRepeat.from(editedTask.repeatRule).label.takeUnless { editedTask.repeatRule == TaskRepeat.NONE.value }
    val timeSummary = listOfNotNull(taskTime, repeat).joinToString(" · ")
    val priorityColor =
        when (editedTask.priority) {
            3 -> MaterialTheme.colorScheme.error
            2 -> MaterialTheme.colorScheme.tertiary
            1 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    val subtaskSurfaceColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { saveAndBack("top-back") }, enabled = !saving) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box {
                IconButton(onClick = { showMoreMenu = !showMoreMenu }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("保存") },
                        leadingIcon = { Icon(Icons.Outlined.Check, contentDescription = null) },
                        enabled = canSave,
                        onClick = { saveDetails("more-menu") },
                    )
                    DropdownMenuItem(
                        text = { Text("删除任务", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            showMoreMenu = false
                            confirmDelete = true
                        },
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = editedTask.done,
                    onCheckedChange = { done ->
                        editedTask = editedTask.copy(done = done)
                        onToggleDone(done)
                    },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(36.dp),
                )
                BasicTextField(
                    value = editedTask.taskText,
                    onValueChange = { editedTask = editedTask.copy(taskText = it) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(start = 4.dp, top = 3.dp)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                    saveDetails("title-enter")
                                    true
                                } else {
                                    false
                                }
                            },
                    textStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            color = if (editedTask.done) placeholderColor else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            lineHeight = 28.sp,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { saveDetails("title-ime-done") }),
                    decorationBox = { innerTextField ->
                        Box {
                            if (editedTask.taskText.isBlank()) {
                                Text(
                                    text = "任务标题",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                                    color = placeholderColor,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                ) {
                    IconButton(onClick = { showPriorityMenu = !showPriorityMenu }) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = "优先级",
                            tint = priorityColor,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showPriorityMenu,
                        onDismissRequest = { showPriorityMenu = false },
                    ) {
                        listOf(0 to "无优先级", 1 to "低优先级", 2 to "中优先级", 3 to "高优先级").forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Flag,
                                        contentDescription = null,
                                        tint = when (value) {
                                            3 -> MaterialTheme.colorScheme.error
                                            2 -> MaterialTheme.colorScheme.tertiary
                                            1 -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                                trailingIcon = {
                                    if (editedTask.priority == value) {
                                        Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                onClick = {
                                    editedTask = editedTask.copy(priority = value)
                                    showPriorityMenu = false
                                },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }.padding(horizontal = 52.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.task_date),
                    contentDescription = "时间和重复",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "时间&重复",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
                if (timeSummary.isNotBlank()) {
                    Text(
                        text = timeSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
            )

            val showSubtaskBlock = editedSubtasks.isNotEmpty() || newSubtaskTexts.isNotEmpty()
            if (showSubtaskBlock) {
                Surface(
                    shape = RoundedCornerShape((taskCornerRadiusDp ?: 16).dp),
                    color = subtaskSurfaceColor,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column {
                        if (subtasksVisible) {
                            editedSubtasks.forEachIndexed { index, subtask ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .padding(start = (16 + (subtaskIndentLevels[subtask.id] ?: 0) * 20).dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = subtask.done,
                                        onCheckedChange = { done ->
                                            editedSubtasks = editedSubtasks.map {
                                                if (it.id == subtask.id) it.copy(done = done) else it
                                            }
                                            onToggleSubtaskDone(subtask, done)
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.size(36.dp),
                                    )
                                    BasicTextField(
                                        value = subtask.taskText,
                                        onValueChange = { value ->
                                            editedSubtasks = editedSubtasks.map {
                                                if (it.id == subtask.id) it.copy(taskText = value) else it
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .padding(start = 4.dp)
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                                        saveDetails("existing-subtask-enter")
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                },
                                        textStyle =
                                            MaterialTheme.typography.bodyLarge.copy(
                                                color = if (subtask.done) placeholderColor else MaterialTheme.colorScheme.onSurface,
                                            ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = { saveDetails("existing-subtask-ime-done") }),
                                    )
                                }
                                if (index < editedSubtasks.lastIndex || newSubtaskTexts.isNotEmpty()) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    )
                                }
                            }
                            newSubtaskTexts.forEachIndexed { index, text ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = false,
                                        onCheckedChange = null,
                                        enabled = false,
                                        modifier = Modifier.size(36.dp),
                                    )
                                    BasicTextField(
                                        value = text,
                                        onValueChange = { value ->
                                            newSubtaskTexts = newSubtaskTexts.mapIndexed { itemIndex, item ->
                                                if (itemIndex == index) value else item
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .padding(start = 4.dp)
                                                .focusRequester(newSubtaskFocusRequesters[index])
                                                .onPreviewKeyEvent { event ->
                                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                                        addNextSubtask(index)
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                        keyboardActions = KeyboardActions(onNext = { addNextSubtask(index) }),
                                        decorationBox = { innerTextField ->
                                            Box {
                                                if (text.isBlank()) {
                                                    Text("子任务名称", color = placeholderColor, style = MaterialTheme.typography.bodyLarge)
                                                }
                                                innerTextField()
                                            }
                                        },
                                    )
                                }
                                if (index < newSubtaskTexts.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.task_subtask),
                                contentDescription = null,
                                tint = placeholderColor,
                                modifier = Modifier.size(24.dp),
                            )
                            Text(
                                text = "添加子任务",
                                style = MaterialTheme.typography.bodyLarge,
                                color = placeholderColor,
                                modifier = Modifier.weight(1f).clickable { addNewSubtaskInput() },
                            )
                            IconButton(
                                onClick = ::addNewSubtaskInput,
                                modifier = Modifier.size(36.dp).border(BorderStroke(1.dp, placeholderColor), CircleShape),
                            ) {
                                Icon(Icons.Outlined.Add, contentDescription = "添加子任务", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = { subtasksVisible = !subtasksVisible },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = if (subtasksVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (subtasksVisible) "收起子任务" else "展开子任务",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            BasicTextField(
                value = editedTask.notes,
                onValueChange = { editedTask = editedTask.copy(notes = it) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).padding(horizontal = 20.dp, vertical = 12.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                maxLines = 12,
                decorationBox = { innerTextField ->
                    Box {
                        if (editedTask.notes.isBlank()) {
                            Text(
                                text = "请输入描述，或选择模板",
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                                color = placeholderColor,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (!showSubtaskBlock) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.task_subtask),
                        contentDescription = null,
                        tint = placeholderColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "添加子任务",
                        style = MaterialTheme.typography.bodyLarge,
                        color = placeholderColor,
                        modifier = Modifier.padding(start = 8.dp).clickable { addNewSubtaskInput() },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        TaskReminderPickerDialog(
            initialReminderAt = editedTask.reminderAt,
            initialEndAt = editedTask.dueAt,
            initialRepeatRule = TaskRepeat.from(editedTask.repeatRule),
            onDismiss = { showDatePicker = false },
            onClear = {
                editedTask = editedTask.copy(
                    reminderAt = null,
                    dueAt = null,
                    repeatRule = TaskRepeat.NONE.value,
                )
                showDatePicker = false
            },
            onDateRangeSelected = { startAt, endAt, repeatRule ->
                editedTask = editedTask.copy(
                    reminderAt = startAt,
                    dueAt = endAt,
                    repeatRule = repeatRule.value,
                )
                showDatePicker = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("移入任务回收站？") },
            text = { Text("“${task.taskText}”会移入任务回收站，可稍后恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskMeta(
    task: TaskEntity,
    groupName: String?,
    onOpenNotePath: (String) -> Unit,
) {
    val now = System.currentTimeMillis()
    val taskTime =
        task.dueAt?.let { taskDateLabel(it, now, defaultHour = 23, defaultMinute = 59) }
            ?: task.reminderAt?.let { taskDateLabel(it, now, defaultHour = 9, defaultMinute = 0) }
    val overdueDays =
        TaskTimeRules.listTime(task)
            ?.takeIf { !task.done && it < now }
            ?.let { ((startOfTodayMillis(now) - startOfTodayMillis(it)) / TASK_DAY_MILLIS).coerceAtLeast(1L) }
    val repeat = TaskRepeat.from(task.repeatRule).label.takeUnless { task.repeatRule == TaskRepeat.NONE.value }
    val notePath = task.notePath?.takeIf { it.isNotBlank() }
    val trailing = listOfNotNull(repeat, notePath).joinToString(" · ")
    if (taskTime == null && trailing.isBlank() && overdueDays == null && groupName == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f)) {
            taskTime?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing.isNotBlank()) {
                Text(
                    text = if (taskTime == null) trailing else " · $trailing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (notePath == null) Modifier else Modifier.clickable { onOpenNotePath(notePath) },
                )
            }
        }
        overdueDays?.let {
            Text(
                text = "过期${it}天",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        groupName?.let {
            Row(
                modifier = Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 88.dp),
                )
            }
        }
    }
}

@Composable
internal fun MarkdownTaskRow(
    item: MarkdownTaskItem,
    hasChildren: Boolean = false,
    expanded: Boolean = false,
    onToggleChildren: () -> Unit = {},
    onToggleDone: (Boolean) -> Unit,
    onOpenNotePath: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onOpenNotePath(item.notePath) },
    ) {
        Row(
            modifier =
                Modifier
                    .padding(start = (item.indentLevel * 20).dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = item.done,
                onCheckedChange = onToggleDone,
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.width(48.dp).height(38.dp).scale(0.8f),
            )
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.taskText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color =
                            if (item.done) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (item.notes.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = "有详情",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp).size(16.dp).scale(0.8f),
                        )
                    }
                    if (hasChildren) {
                        IconButton(
                            onClick = onToggleChildren,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (expanded) "收起子任务" else "展开子任务",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp).scale(0.8f),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${item.noteTitle.ifBlank { item.notePath }} · 第 ${item.lineNumber} 行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

internal fun taskDateLabel(
    timeMillis: Long,
    now: Long,
    defaultHour: Int? = null,
    defaultMinute: Int? = null,
): String =
    when (daysFromToday(timeMillis, now)) {
        0L -> "今天"
        -1L -> "昨天"
        1L -> "明天"
        2L -> "后天"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(timeMillis))
    } +
        if (hasDisplayedTime(timeMillis, defaultHour, defaultMinute)) {
            " ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))}"
        } else {
            ""
        }

private fun daysFromToday(
    timeMillis: Long,
    now: Long,
): Long =
    (startOfTodayMillis(timeMillis) - startOfTodayMillis(now)) / TASK_DAY_MILLIS

internal fun hasDisplayedTime(
    timeMillis: Long,
    defaultHour: Int? = null,
    defaultMinute: Int? = null,
): Boolean {
    val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val minute = calendar.get(Calendar.MINUTE)
    return if (defaultHour != null && defaultMinute != null) {
        hour != defaultHour || minute != defaultMinute
    } else {
        hour != 0 || minute != 0
    }
}

internal fun startOfTodayMillis(now: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

internal fun endOfTodayMillis(now: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
