package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.database.TaskGroupEntity
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.task.MarkdownTaskItem
import com.kangle.kardleaf.data.task.MarkdownTaskParserCache
import com.kangle.kardleaf.data.task.TaskReminderScheduler
import com.kangle.kardleaf.data.utils.KardLeafLog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TASK_REMINDER_PATTERN = "yyyy-MM-dd HH:mm"
private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"
private const val TASK_SCAN_LOG_TAG = "KardLeafTaskScan"

internal enum class TaskFilter(val label: String) {
    ALL("全部"),
    TODAY("今天"),
    UPCOMING("即将到期"),
    OVERDUE("已逾期"),
    COMPLETED("已完成"),
}

internal enum class TaskSort(val label: String) {
    DUE("到期时间"),
    PRIORITY("优先级"),
    UPDATED("最近更新"),
}

internal enum class TaskRepeat(val value: String, val label: String) {
    NONE("NONE", "不重复"),
    DAILY("DAILY", "每天"),
    WEEKLY("WEEKLY", "每周"),
    MONTHLY("MONTHLY", "每月"),
    ;

    companion object {
        fun from(value: String): TaskRepeat = entries.firstOrNull { it.value == value } ?: NONE
    }
}

internal data class TaskEditorResult(
    val text: String,
    val done: Boolean,
    val groupId: Long?,
    val priority: Int,
    val dueAt: Long?,
    val reminderAt: Long?,
    val repeatRule: String,
    val notes: String,
    val reminderMode: String = TaskEntity.REMINDER_MODE_POPUP,
    val reminderRing: Boolean = true,
    val reminderVibrate: Boolean = true,
)

private data class TaskGroupSection(
    val group: TaskGroupEntity?,
    val tasks: List<TaskEntity>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onOpenDrawer: () -> Unit,
    onOpenNotePath: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val appContext = context.applicationContext
    val taskDao = remember { AppDatabase.getDatabase(appContext).taskDao() }
    val prefsManager = remember { PrefsManager(appContext) }
    val scheduler = remember { TaskReminderScheduler(appContext) }
    val scope = rememberCoroutineScope()
    val tasks by taskDao.observeTasks().collectAsState(initial = emptyList())
    val groups by taskDao.observeGroups().collectAsState(initial = emptyList())
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showMarkdownTasks by remember {
        mutableStateOf(prefsManager.isShowMarkdownTasksInTaskListEnabled())
    }
    var taskGroupFilter by remember {
        mutableStateOf<MainViewModel.NoteFilter>(MainViewModel.NoteFilter.All)
    }
    val markdownTasks by remember(taskDao, showMarkdownTasks) {
        if (!showMarkdownTasks) {
            flowOf(emptyList<MarkdownTaskItem>())
        } else {
            taskDao.observeMarkdownTaskSources().map { sources ->
                withContext(Dispatchers.Default) {
                    val startMs = SystemClock.elapsedRealtime()
                    val parsed = MarkdownTaskParserCache.parse(sources)
                    val largeCount = sources.count { it.content.length > 100_000 }
                    if (largeCount > 0 || sources.size > 50) {
                        KardLeafLog.d(
                            TASK_SCAN_LOG_TAG,
                            "markdown task scan sources=${sources.size} large=$largeCount tasks=${parsed.size} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                    }
                    parsed
                }
            }
        }
    }.collectAsState(initial = emptyList())
    var taskFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var taskSort by remember { mutableStateOf(TaskSort.DUE) }
    var showTaskOptions by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<TaskGroupEntity?>(null) }
    var showGroupEditor by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf<TaskGroupEntity?>(null) }
    val collapsedGroups = remember { mutableStateMapOf<Long?, Boolean>() }
    var completedCollapsed by remember { mutableStateOf(true) }
    val taskGroupPaths = remember(groups) { buildTaskGroupNavigationPaths(groups) }
    val groupFilteredTasks = remember(tasks, groups, taskGroupFilter) {
        filterTasksByTaskGroup(tasks, groups, taskGroupFilter)
    }
    val visibleGroups = remember(groups, taskGroupFilter) {
        filterTaskGroups(groups, taskGroupFilter)
    }
    val filteredTasks = remember(groupFilteredTasks, searchQuery, taskFilter, taskSort) {
        filterAndSortTasks(groupFilteredTasks, searchQuery, taskFilter, taskSort)
    }
    val activeTasks = remember(filteredTasks, taskFilter) {
        if (taskFilter == TaskFilter.COMPLETED) emptyList() else filteredTasks.filterNot { it.done }
    }
    val completedTasks = remember(filteredTasks, taskFilter) {
        if (taskFilter == TaskFilter.ALL || taskFilter == TaskFilter.COMPLETED) {
            filteredTasks.filter { it.done }
        } else {
            emptyList()
        }
    }
    val taskSections = remember(activeTasks, visibleGroups, taskGroupFilter) {
        buildTaskGroupSections(activeTasks, visibleGroups).filter { section ->
            taskGroupFilter is MainViewModel.NoteFilter.All || section.group != null
        }
    }
    val visibleMarkdownTasks = remember(markdownTasks, searchQuery) {
        val query = searchQuery.trim()
        markdownTasks.filterNot { it.done }.filter {
            query.isBlank() || it.taskText.contains(query, ignoreCase = true) ||
                it.noteTitle.contains(query, ignoreCase = true)
        }
    }

    LaunchedEffect(taskGroupPaths, taskGroupFilter) {
        val selectedPath = (taskGroupFilter as? MainViewModel.NoteFilter.Label)?.name ?: return@LaunchedEffect
        if (selectedPath !in taskGroupPaths) {
            taskGroupFilter = MainViewModel.NoteFilter.All
        }
    }

    fun saveTask(
        original: TaskEntity?,
        result: TaskEditorResult,
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val savedTask = withContext(Dispatchers.IO) {
                val task = if (original == null) {
                    val draft = TaskEntity(
                        taskText = result.text,
                        done = result.done,
                        reminderAt = result.reminderAt,
                        groupId = result.groupId,
                        priority = result.priority,
                        dueAt = result.dueAt,
                        repeatRule = result.repeatRule,
                        notes = result.notes,
                        createdAt = now,
                        updatedAt = now,
                        reminderMode = result.reminderMode,
                        reminderRing = result.reminderRing,
                        reminderVibrate = result.reminderVibrate,
                    )
                    draft.copy(id = taskDao.insert(draft))
                } else {
                    original.copy(
                        taskText = result.text,
                        done = result.done,
                        reminderAt = result.reminderAt,
                        groupId = result.groupId,
                        priority = result.priority,
                        dueAt = result.dueAt,
                        repeatRule = result.repeatRule,
                        notes = result.notes,
                        updatedAt = now,
                        reminderMode = result.reminderMode,
                        reminderRing = result.reminderRing,
                        reminderVibrate = result.reminderVibrate,
                    ).also { taskDao.update(it) }
                }
                KardLeafLog.i(
                    TASK_REMINDER_LOG_TAG,
                    "save id=${task.id} done=${task.done} reminderAt=${task.reminderAt} delayMs=${task.reminderAt?.let { it - System.currentTimeMillis() }}",
                )
                scheduler.schedule(task)
                task
            }
            if (savedTask.reminderAt != null && !TaskReminderScheduler.areNotificationsEnabled(context)) {
                context.showToast("系统通知未开启，提醒可能无法弹出")
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        scope.launch(Dispatchers.IO) {
            taskDao.delete(task)
            scheduler.cancel(task.id)
        }
    }

    fun toggleDone(task: TaskEntity, done: Boolean) {
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val updated = task.copy(done = done, updatedAt = now)
            taskDao.update(updated)
            scheduler.schedule(updated)
            if (done) {
                nextTaskOccurrence(task, now)?.let { next ->
                    val saved = next.copy(id = taskDao.insert(next))
                    scheduler.schedule(saved)
                }
            }
        }
    }

    fun moveTask(task: TaskEntity, groupId: Long?) {
        scope.launch(Dispatchers.IO) {
            taskDao.moveTaskToGroup(task.id, groupId, System.currentTimeMillis())
        }
    }

    fun saveGroup(group: TaskGroupEntity?, name: String) {
        val parentPath = when {
            group != null -> group.name.substringBeforeLast('/', missingDelimiterValue = "")
            else -> (taskGroupFilter as? MainViewModel.NoteFilter.Label)?.name.orEmpty()
        }
        val resolvedPath = resolveTaskGroupPath(parentPath, name)
        if (resolvedPath.isBlank() || groups.any { it.id != group?.id && it.name.equals(resolvedPath, ignoreCase = true) }) {
            context.showToast(if (resolvedPath.isBlank()) "分组名称不能为空" else "已有同名分组")
            return
        }
        scope.launch(Dispatchers.IO) {
            if (group == null) {
                taskDao.insertGroup(
                    TaskGroupEntity(
                        name = resolvedPath,
                        sortOrder = taskDao.getMaxGroupOrder() + 1,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            } else {
                taskDao.updateGroup(group.copy(name = resolvedPath))
            }
        }
        showGroupEditor = false
    }

    fun moveGroup(group: TaskGroupEntity, offset: Int) {
        val index = groups.indexOfFirst { it.id == group.id }
        val other = groups.getOrNull(index + offset) ?: return
        scope.launch(Dispatchers.IO) { taskDao.swapGroupOrder(group, other) }
    }

    fun testReminderTask(text: String): TaskEntity {
        val now = System.currentTimeMillis()
        return TaskEntity(
            id = (now % 1_000_000_000L) + 9_000_000_000L,
            taskText = text,
            done = false,
            reminderAt = now,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun runImmediateReminderTest() {
        val task = testReminderTask("立即提醒测试")
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "test immediate start id=${task.id}")
        scheduler.deliverReminder(task)
        context.showToast("已触发立即提醒测试")
    }

    fun runPopupTest() {
        val task = testReminderTask("测试弹窗")
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "test popup start id=${task.id}")
        scheduler.showReminderAlert(task)
        context.showToast("已触发测试弹窗")
    }

    fun runSoundTest() {
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "test sound start")
        runCatching {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, soundUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.volume = 1f
            }
            ringtone?.play()
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { ringtone?.stop() }
            }, 3_000L)
            context.showToast("已播放测试铃声")
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "test sound failed", error)
            context.showToast("测试铃声失败")
        }
    }

    BackHandler(enabled = showSearch) {
        showSearch = false
        searchQuery = ""
        focusManager.clearFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        AnimatedVisibility(
                            visible = !showSearch,
                            enter = fadeIn(),
                            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                        ) {
                            Text(
                                text = taskGroupTitle(taskGroupFilter),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        AnimatedVisibility(
                            visible = showSearch,
                            enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                            exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                        ) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                tonalElevation = 2.dp,
                            ) {
                                TaskSearchBar(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onClear = { searchQuery = "" },
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "打开侧边栏")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        focusManager.clearFocus()
                    }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "搜索任务",
                            tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { showTaskOptions = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多选项")
                        }
                        DropdownMenu(
                            expanded = showTaskOptions,
                            onDismissRequest = { showTaskOptions = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("新建分组") },
                                onClick = {
                                    showTaskOptions = false
                                    editingGroup = null
                                    showGroupEditor = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("显示笔记中的 Markdown 任务") },
                                trailingIcon = {
                                    Checkbox(checked = showMarkdownTasks, onCheckedChange = null)
                                },
                                onClick = {
                                    showMarkdownTasks = !showMarkdownTasks
                                    prefsManager.saveShowMarkdownTasksInTaskList(showMarkdownTasks)
                                    showTaskOptions = false
                                },
                            )
                            TaskSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text("${if (taskSort == sort) "✓ " else ""}按${sort.label}排序") },
                                    onClick = {
                                        taskSort = sort
                                        showTaskOptions = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("立即提醒") },
                                onClick = {
                                    showTaskOptions = false
                                    runImmediateReminderTest()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("测试弹窗") },
                                onClick = {
                                    showTaskOptions = false
                                    runPopupTest()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("测试铃声") },
                                onClick = {
                                    showTaskOptions = false
                                    runSoundTest()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingTask = null
                showEditor = true
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建任务")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PermissionHint()
            }
            if (taskGroupPaths.isNotEmpty()) {
                item {
                    FolderPathStrip(
                        currentFilter = taskGroupFilter,
                        labels = taskGroupPaths,
                        rootChip = FolderChipData("全部任务", ""),
                        onOpenFolder = { path ->
                            taskGroupFilter = if (path.isBlank()) {
                                MainViewModel.NoteFilter.All
                            } else {
                                MainViewModel.NoteFilter.Label(path)
                            }
                        },
                        onShowAllInFolder = { path ->
                            taskGroupFilter = MainViewModel.NoteFilter.Label(path, recursive = true)
                        },
                    )
                }
            }
            item {
                TaskFiltersRow(
                    selectedFilter = taskFilter,
                    onFilterChange = {
                        taskFilter = it
                        if (it == TaskFilter.COMPLETED) completedCollapsed = false
                    },
                )
            }
            item {
                SectionHeader(
                    "任务清单",
                    "${activeTasks.size} 个待办 · ${completedTasks.size} 个已完成 · 按${taskSort.label}排序",
                )
            }
            if (activeTasks.isEmpty() && completedTasks.isEmpty()) {
                item {
                    EmptyTaskState(filter = taskFilter, searching = searchQuery.isNotBlank())
                }
            } else {
                if (taskFilter != TaskFilter.COMPLETED) taskSections.forEach { section ->
                    val groupId = section.group?.id
                    item(key = "group-${groupId ?: "ungrouped"}") {
                        TaskGroupHeader(
                            group = section.group,
                            count = tasks.count { task ->
                                !task.done && if (section.group == null) {
                                    groups.none { it.id == task.groupId }
                                } else {
                                    task.groupId == section.group.id
                                }
                            },
                            collapsed = collapsedGroups[groupId] == true,
                            canMoveUp = section.group != null && groups.indexOfFirst { it.id == groupId } > 0,
                            canMoveDown = section.group != null && groups.indexOfFirst { it.id == groupId } < groups.lastIndex,
                            onToggle = { collapsedGroups[groupId] = collapsedGroups[groupId] != true },
                            onRename = {
                                editingGroup = section.group
                                showGroupEditor = true
                            },
                            onDelete = { deletingGroup = section.group },
                            onMoveUp = { section.group?.let { moveGroup(it, -1) } },
                            onMoveDown = { section.group?.let { moveGroup(it, 1) } },
                        )
                    }
                    if (collapsedGroups[groupId] != true) {
                        items(section.tasks, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                groups = groups,
                                onToggleDone = { toggleDone(task, it) },
                                onEdit = {
                                    editingTask = task
                                    showEditor = true
                                },
                                onMove = { moveTask(task, it) },
                                onDelete = { deleteTask(task) },
                                onOpenNotePath = onOpenNotePath,
                            )
                        }
                    }
                }
                if (completedTasks.isNotEmpty()) {
                    item(key = "completed-header") {
                        CompletedTasksHeader(
                            count = completedTasks.size,
                            collapsed = completedCollapsed,
                            onToggle = { completedCollapsed = !completedCollapsed },
                        )
                    }
                    if (!completedCollapsed) {
                        items(completedTasks, key = { "completed-${it.id}" }) { task ->
                            TaskRow(
                                task = task,
                                groups = groups,
                                onToggleDone = { toggleDone(task, it) },
                                onEdit = {
                                    editingTask = task
                                    showEditor = true
                                },
                                onMove = { moveTask(task, it) },
                                onDelete = { deleteTask(task) },
                                onOpenNotePath = onOpenNotePath,
                            )
                        }
                    }
                }
            }
            if (showMarkdownTasks && taskFilter == TaskFilter.ALL && taskGroupFilter is MainViewModel.NoteFilter.All) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider()
                    SectionHeader("笔记中的 Markdown 任务", "${visibleMarkdownTasks.size} 条未完成")
                }
                if (visibleMarkdownTasks.isEmpty()) {
                    item {
                        Text(
                            text = "未识别到匹配的 - [ ] 任务。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(visibleMarkdownTasks, key = { "${it.notePath}:${it.lineNumber}:${it.taskText}" }) { item ->
                        MarkdownTaskRow(item = item, onOpenNotePath = onOpenNotePath)
                    }
                }
            }
        }
    }

    if (showEditor) {
        TaskEditorDialog(
            task = editingTask,
            groups = groups,
            onDismiss = { showEditor = false },
            onSave = { result ->
                saveTask(editingTask, result)
                showEditor = false
            },
        )
    }

    if (showGroupEditor) {
        GroupEditorDialog(
            group = editingGroup,
            parentPath = if (editingGroup == null) {
                (taskGroupFilter as? MainViewModel.NoteFilter.Label)?.name.orEmpty()
            } else {
                editingGroup?.name?.substringBeforeLast('/', missingDelimiterValue = "").orEmpty()
            },
            onDismiss = { showGroupEditor = false },
            onSave = { saveGroup(editingGroup, it) },
        )
    }

    deletingGroup?.let { group ->
        AlertDialog(
            onDismissRequest = { deletingGroup = null },
            title = { Text("删除分组？") },
            text = { Text("“${group.name}”中的任务会移到“未分组”，不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    deletingGroup = null
                    scope.launch(Dispatchers.IO) {
                        taskDao.deleteGroupKeepingTasks(group, System.currentTimeMillis())
                    }
                }) { Text("删除分组") }
            },
            dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text("取消") } },
        )
    }
}

internal fun filterAndSortTasks(
    tasks: List<TaskEntity>,
    query: String,
    filter: TaskFilter,
    sort: TaskSort,
    now: Long = System.currentTimeMillis(),
): List<TaskEntity> {
    val startToday = startOfTodayMillis(now)
    val endToday = endOfTodayMillis(now)
    val searched = tasks.filter { task ->
        query.isBlank() || task.taskText.contains(query, ignoreCase = true) ||
            task.notes.contains(query, ignoreCase = true)
    }
    val filtered = searched.filter { task ->
        val due = task.dueAt ?: task.reminderAt
        when (filter) {
            TaskFilter.ALL -> true
            TaskFilter.TODAY -> !task.done && due != null && due in startToday..endToday
            TaskFilter.UPCOMING -> !task.done && due != null && due > endToday
            TaskFilter.OVERDUE -> !task.done && due != null && due < now
            TaskFilter.COMPLETED -> task.done
        }
    }
    return when (sort) {
        TaskSort.DUE -> filtered.sortedWith(
            compareBy<TaskEntity> { (it.dueAt ?: it.reminderAt) == null }
                .thenBy { it.dueAt ?: it.reminderAt }
                .thenByDescending { it.priority }
                .thenByDescending { it.updatedAt },
        )
        TaskSort.PRIORITY -> filtered.sortedWith(
            compareByDescending<TaskEntity> { it.priority }
                .thenBy { (it.dueAt ?: it.reminderAt) == null }
                .thenBy { it.dueAt ?: it.reminderAt }
                .thenByDescending { it.updatedAt },
        )
        TaskSort.UPDATED -> filtered.sortedByDescending { it.updatedAt }
    }
}

private fun buildTaskGroupSections(
    tasks: List<TaskEntity>,
    groups: List<TaskGroupEntity>,
): List<TaskGroupSection> =
    groups.map { group -> TaskGroupSection(group, tasks.filter { it.groupId == group.id }) } +
        TaskGroupSection(null, tasks.filter { task -> groups.none { it.id == task.groupId } })

private fun buildTaskGroupNavigationPaths(groups: List<TaskGroupEntity>): List<String> =
    groups
        .flatMap { group ->
            val segments = normalizeFolderPathForUi(group.name).split('/').filter { it.isNotBlank() }
            segments.indices.map { index -> segments.take(index + 1).joinToString("/") }
        }
        .distinct()
        .sorted()

private fun filterTasksByTaskGroup(
    tasks: List<TaskEntity>,
    groups: List<TaskGroupEntity>,
    filter: MainViewModel.NoteFilter,
): List<TaskEntity> {
    val folderFilter = filter as? MainViewModel.NoteFilter.Label ?: return tasks
    val selectedPath = normalizeFolderPathForUi(folderFilter.name)
    val groupPaths = groups.associate { it.id to normalizeFolderPathForUi(it.name) }
    return tasks.filter { task ->
        val taskPath = task.groupId?.let(groupPaths::get).orEmpty()
        taskPath == selectedPath || (folderFilter.recursive && taskPath.startsWith("$selectedPath/"))
    }
}

private fun filterTaskGroups(
    groups: List<TaskGroupEntity>,
    filter: MainViewModel.NoteFilter,
): List<TaskGroupEntity> {
    val folderFilter = filter as? MainViewModel.NoteFilter.Label ?: return groups
    val selectedPath = normalizeFolderPathForUi(folderFilter.name)
    return groups.filter { group ->
        val groupPath = normalizeFolderPathForUi(group.name)
        groupPath == selectedPath || (folderFilter.recursive && groupPath.startsWith("$selectedPath/"))
    }
}

private fun resolveTaskGroupPath(parentPath: String, name: String): String =
    normalizeFolderPathForUi(
        listOf(parentPath, name)
            .filter { it.isNotBlank() }
            .joinToString("/"),
    )

private fun taskGroupTitle(filter: MainViewModel.NoteFilter): String {
    val folderFilter = filter as? MainViewModel.NoteFilter.Label ?: return "任务"
    val name = normalizeFolderPathForUi(folderFilter.name).substringAfterLast('/')
    return if (folderFilter.recursive) "$name · 全部" else name
}

internal fun nextTaskOccurrence(task: TaskEntity, now: Long): TaskEntity? {
    val repeat = TaskRepeat.from(task.repeatRule)
    if (repeat == TaskRepeat.NONE || (task.dueAt == null && task.reminderAt == null)) return null

    fun advance(time: Long): Long {
        var next = time
        do {
            next = Calendar.getInstance().apply {
                timeInMillis = next
                add(
                    when (repeat) {
                        TaskRepeat.DAILY -> Calendar.DAY_OF_MONTH
                        TaskRepeat.WEEKLY -> Calendar.WEEK_OF_YEAR
                        TaskRepeat.MONTHLY -> Calendar.MONTH
                        TaskRepeat.NONE -> return time
                    },
                    1,
                )
            }.timeInMillis
        } while (next <= now)
        return next
    }

    return task.copy(
        id = 0,
        done = false,
        dueAt = task.dueAt?.let(::advance),
        reminderAt = task.reminderAt?.let(::advance),
        createdAt = now,
        updatedAt = now,
    )
}

@Composable
private fun PermissionHint() {
    val context = LocalContext.current
    val notificationsEnabled = TaskReminderScheduler.areNotificationsEnabled(context)
    val exactAlarmsEnabled = TaskReminderScheduler.canScheduleExactAlarms(context)
    val fullScreenEnabled = TaskReminderScheduler.canUseFullScreenIntent(context)
    val overlayEnabled = TaskReminderScheduler.canDrawOverlays(context)
    val channelAudible = TaskReminderScheduler.hasAudibleReminderChannel(context)
    val statusText = when {
        !notificationsEnabled -> "通知未允许 · 通知栏、横幅和声音都无法显示"
        !exactAlarmsEnabled -> "通知已允许 · 未开启精确提醒，系统可能延后触发"
        !fullScreenEnabled -> "通知和精确提醒已允许 · 全屏提醒未允许"
        !overlayEnabled -> "未授予悬浮窗权限 · 亮屏后台时弹窗提醒只能以横幅显示"
        !channelAudible -> "提醒渠道已静音或重要级别过低"
        else -> "通知、精确提醒、弹窗和声音均可用"
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            if (!notificationsEnabled) {
                TextButton(onClick = { openAppNotificationSettings(context) }) {
                    Text("通知")
                }
            }
            if (!exactAlarmsEnabled) {
                TextButton(onClick = { openExactAlarmSettings(context) }) {
                    Text("精确")
                }
            }
            if (!fullScreenEnabled) {
                TextButton(onClick = { openFullScreenIntentSettings(context) }) {
                    Text("全屏")
                }
            }
            if (!overlayEnabled) {
                TextButton(onClick = { openOverlayPermissionSettings(context) }) {
                    Text("弹窗")
                }
            }
            if (!channelAudible) {
                TextButton(onClick = { openReminderChannelSettings(context) }) {
                    Text("声音")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索任务",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "清除搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TaskFiltersRow(
    selectedFilter: TaskFilter,
    onFilterChange: (TaskFilter) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun TaskGroupHeader(
    group: TaskGroupEntity?,
    count: Int,
    collapsed: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var showMenu by remember(group?.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
            contentDescription = if (collapsed) "展开分组" else "折叠分组",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group?.name ?: "未分组",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
            Text(
                text = "${count} 个未完成",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (group != null) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "分组操作")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { showMenu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("上移") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                        enabled = canMoveUp,
                        onClick = { showMenu = false; onMoveUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("下移") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                        enabled = canMoveDown,
                        onClick = { showMenu = false; onMoveDown() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除分组") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedTasksHeader(count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "已完成",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        Text("$count 个", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupEditorDialog(
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
private fun EmptyTaskState(filter: TaskFilter, searching: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = when {
                searching -> "没有匹配的任务。"
                filter != TaskFilter.ALL -> "“${filter.label}”中暂无任务。"
                else -> "还没有任务。点击右下角的新建按钮即可开始。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    groups: List<TaskGroupEntity>,
    onToggleDone: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onMove: (Long?) -> Unit,
    onDelete: () -> Unit,
    onOpenNotePath: (String) -> Unit,
) {
    var showMenu by remember(task.id) { mutableStateOf(false) }
    var showMoveDialog by remember(task.id) { mutableStateOf(false) }
    var confirmDelete by remember(task.id) { mutableStateOf(false) }
    val due = task.dueAt ?: task.reminderAt
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (due?.let { it < System.currentTimeMillis() } == true && !task.done) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (task.done) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.done, onCheckedChange = onToggleDone)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = task.taskText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                TaskMeta(task = task, onOpenNotePath = onOpenNotePath)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "任务操作")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { showMenu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("移动到分组") },
                        leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                        onClick = { showMenu = false; showMoveDialog = true },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { showMenu = false; confirmDelete = true },
                    )
                }
            }
        }
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("移动到分组") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (listOf<TaskGroupEntity?>(null) + groups).forEach { group ->
                        TextButton(
                            onClick = { showMoveDialog = false; onMove(group?.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (group == null) "未分组" else group.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMoveDialog = false }) { Text("取消") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除任务？") },
            text = { Text("“${task.taskText}”删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TaskMeta(
    task: TaskEntity,
    onOpenNotePath: (String) -> Unit,
) {
    val reminder = task.reminderAt?.let { reminderStatusText(it) }
    val due = task.dueAt?.let { dueStatusText(it) }
    val priority = priorityLabel(task.priority).takeUnless { task.priority == 0 }
    val repeat = TaskRepeat.from(task.repeatRule).label.takeUnless { task.repeatRule == TaskRepeat.NONE.value }
    val notes = "有备注".takeIf { task.notes.isNotBlank() }
    val notePath = task.notePath?.takeIf { it.isNotBlank() }
    val text = listOfNotNull(priority, due, reminder, repeat, notes, notePath).joinToString(" · ").ifBlank { "无日期" }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = if (notePath == null) Modifier else Modifier.clickable { onOpenNotePath(notePath) },
    )
}

@Composable
private fun MarkdownTaskRow(
    item: MarkdownTaskItem,
    onOpenNotePath: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenNotePath(item.notePath) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckBox,
                contentDescription = null,
                tint = if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.taskText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                )
                Text(
                    text = "${item.noteTitle.ifBlank { item.notePath }} · 第 ${item.lineNumber} 行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun TaskEditorDialog(
    task: TaskEntity?,
    groups: List<TaskGroupEntity> = emptyList(),
    autoFocusTitle: Boolean = false,
    embeddedOverlay: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (TaskEditorResult) -> Unit,
) {
    var text by remember(task?.id) { mutableStateOf(task?.taskText.orEmpty()) }
    var done by remember(task?.id) { mutableStateOf(task?.done ?: false) }
    var groupId by remember(task?.id) { mutableStateOf(task?.groupId) }
    var priority by remember(task?.id) { mutableStateOf(task?.priority ?: 0) }
    var dueAt by remember(task?.id) { mutableStateOf(task?.dueAt) }
    var reminderAt by remember(task?.id) { mutableStateOf(task?.reminderAt) }
    var reminderPopup by remember(task?.id) {
        mutableStateOf((task?.reminderMode ?: TaskEntity.REMINDER_MODE_POPUP) != TaskEntity.REMINDER_MODE_NOTIFICATION)
    }
    var reminderRing by remember(task?.id) { mutableStateOf(task?.reminderRing ?: true) }
    var reminderVibrate by remember(task?.id) { mutableStateOf(task?.reminderVibrate ?: true) }
    var repeatRule by remember(task?.id) { mutableStateOf(TaskRepeat.from(task?.repeatRule ?: TaskRepeat.NONE.value)) }
    var notes by remember(task?.id) { mutableStateOf(task?.notes.orEmpty()) }
    var error by remember(task?.id) { mutableStateOf<String?>(null) }
    var showGroupMenu by remember(task?.id) { mutableStateOf(false) }
    var showPriorityMenu by remember(task?.id) { mutableStateOf(false) }
    var showRepeatMenu by remember(task?.id) { mutableStateOf(false) }
    var showDuePicker by remember(task?.id) { mutableStateOf(false) }
    var showReminderPicker by remember(task?.id) { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }
    val editorScrollState = rememberScrollState()

    LaunchedEffect(autoFocusTitle) {
        if (autoFocusTitle) {
            withFrameNanos { }
            titleFocusRequester.requestFocus()
        }
    }

    fun submitTask() {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) {
            error = "任务标题不能为空"
            return
        }
        val selectedReminderAt = reminderAt
        if (!done && selectedReminderAt != null && selectedReminderAt <= System.currentTimeMillis() + 1000L) {
            error = "提醒时间需要晚于当前时间"
            return
        }
        if (repeatRule != TaskRepeat.NONE && dueAt == null && reminderAt == null) {
            error = "重复任务需要到期时间或提醒时间"
            return
        }
        onSave(
            TaskEditorResult(
                text = trimmedText,
                done = done,
                groupId = groupId,
                priority = priority,
                dueAt = dueAt,
                reminderAt = selectedReminderAt,
                repeatRule = repeatRule.value,
                notes = notes.trim(),
                reminderMode = if (reminderPopup) TaskEntity.REMINDER_MODE_POPUP else TaskEntity.REMINDER_MODE_NOTIFICATION,
                reminderRing = reminderRing,
                reminderVibrate = reminderVibrate,
            ),
        )
    }

    val editorFields: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier.verticalScroll(editorScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = { Text("任务标题") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester),
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("备注") },
                placeholder = { Text("补充描述（可选）") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    TaskEditorCompactOption(
                        icon = { Icon(Icons.Outlined.Folder, null) },
                        label = "分组",
                        value = groups.firstOrNull { it.id == groupId }?.name ?: "未分组",
                        onClick = { showGroupMenu = true },
                    )
                    DropdownMenu(expanded = showGroupMenu, onDismissRequest = { showGroupMenu = false }) {
                        (listOf<TaskGroupEntity?>(null) + groups).forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group?.name ?: "未分组") },
                                onClick = { groupId = group?.id; showGroupMenu = false },
                            )
                        }
                    }
                }
                Box {
                    TaskEditorCompactOption(
                        icon = { Icon(Icons.Outlined.Flag, null) },
                        label = "优先级",
                        value = priorityLabel(priority),
                        onClick = { showPriorityMenu = true },
                    )
                    DropdownMenu(expanded = showPriorityMenu, onDismissRequest = { showPriorityMenu = false }) {
                        (0..3).forEach { value ->
                            DropdownMenuItem(
                                text = { Text(priorityLabel(value)) },
                                onClick = { priority = value; showPriorityMenu = false },
                            )
                        }
                    }
                }
                TaskEditorCompactOption(
                    icon = { Icon(Icons.Outlined.CalendarMonth, null) },
                    label = "到期",
                    value = dueAt?.let(::formatTaskOptionTime) ?: "无",
                    onClick = { showDuePicker = true },
                )
                TaskEditorCompactOption(
                    icon = { Icon(Icons.Outlined.Notifications, null) },
                    label = "提醒",
                    value = reminderAt?.let(::formatTaskOptionTime) ?: "无",
                    onClick = { showReminderPicker = true },
                )
                Box {
                    TaskEditorCompactOption(
                        icon = { Icon(Icons.Outlined.Repeat, null) },
                        label = "重复",
                        value = repeatRule.label,
                        onClick = { showRepeatMenu = true },
                    )
                    DropdownMenu(expanded = showRepeatMenu, onDismissRequest = { showRepeatMenu = false }) {
                        TaskRepeat.entries.forEach { repeat ->
                            DropdownMenuItem(
                                text = { Text(repeat.label) },
                                onClick = { repeatRule = repeat; showRepeatMenu = false },
                            )
                        }
                    }
                }
            }
            if (reminderAt != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "提醒方式",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
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
            }
            if (task != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = done, onCheckedChange = { done = it })
                    Text("已完成")
                }
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (embeddedOverlay) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val panelHeight = (screenHeight - 340.dp).coerceIn(300.dp, 440.dp)
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
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (task == null) "添加任务" else "编辑任务",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        TextButton(onClick = ::submitTask) {
                            Text("保存")
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(10.dp))
                    editorFields(Modifier.weight(1f))
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (task == null) "添加任务" else "编辑任务") },
            text = { editorFields(Modifier) },
            confirmButton = {
                TextButton(onClick = ::submitTask) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
        )
    }

    if (showDuePicker) {
        TaskReminderPickerDialog(
            title = "到期时间",
            initialReminderAt = dueAt,
            onDismiss = { showDuePicker = false },
            onClear = { dueAt = null; showDuePicker = false },
            onReminderSelected = { dueAt = it; showDuePicker = false },
        )
    }

    if (showReminderPicker) {
        TaskReminderPickerDialog(
            title = "提醒时间",
            initialReminderAt = reminderAt,
            onDismiss = { showReminderPicker = false },
            onClear = {
                reminderAt = null
                error = null
                showReminderPicker = false
            },
            onReminderSelected = { selected ->
                reminderAt = selected
                error = if (selected <= System.currentTimeMillis() + 1000L) {
                    "提醒时间需要晚于当前时间"
                } else {
                    null
                }
                showReminderPicker = false
            },
        )
    }
}

@Composable
private fun TaskEditorCompactOption(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .width(92.dp)
            .height(72.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatTaskOptionTime(timeMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timeMillis))

private fun formatReminderTime(timeMillis: Long): String =
    SimpleDateFormat(TASK_REMINDER_PATTERN, Locale.getDefault()).format(Date(timeMillis))

private fun reminderStatusText(timeMillis: Long): String {
    val now = System.currentTimeMillis()
    val prefix = when {
        timeMillis < now -> "已过提醒"
        timeMillis <= endOfTodayMillis(now) -> "今天提醒"
        timeMillis <= endOfTomorrowMillis(now) -> "明天提醒"
        else -> "提醒"
    }
    return "$prefix ${formatReminderTime(timeMillis)}"
}

private fun dueStatusText(timeMillis: Long): String {
    val now = System.currentTimeMillis()
    val prefix = when {
        timeMillis < now -> "已逾期"
        timeMillis <= endOfTodayMillis(now) -> "今天到期"
        timeMillis <= endOfTomorrowMillis(now) -> "明天到期"
        else -> "到期"
    }
    return "$prefix ${formatReminderTime(timeMillis)}"
}

private fun priorityLabel(priority: Int): String = when (priority) {
    3 -> "高优先级"
    2 -> "中优先级"
    1 -> "低优先级"
    else -> "无优先级"
}

@Composable
private fun TaskReminderPickerDialog(
    title: String,
    initialReminderAt: Long?,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onReminderSelected: (Long) -> Unit,
) {
    val initialTime = initialReminderAt ?: System.currentTimeMillis() + 60 * 60 * 1000L
    var selectedDateUtcMillis by remember(initialReminderAt) { mutableStateOf(utcDateMillis(initialTime)) }
    var displayedMonthUtcMillis by remember(initialReminderAt) {
        mutableStateOf(monthStartUtcMillis(selectedDateUtcMillis))
    }
    var selectedHour by remember(initialReminderAt) { mutableStateOf(hourOfDay(initialTime)) }
    var selectedMinute by remember(initialReminderAt) { mutableStateOf(minuteOfHour(initialTime)) }
    var timeSelected by remember(initialReminderAt) { mutableStateOf(true) }
    var quickReminderAt by remember(initialReminderAt) { mutableStateOf<Long?>(null) }
    var quickReminderLabel by remember(initialReminderAt) { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showQuickReminderPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TaskReminderTabs(title = title, onClear = onClear)
                TaskReminderMonthHeader(
                    monthUtcMillis = displayedMonthUtcMillis,
                    onPreviousMonth = {
                        displayedMonthUtcMillis = addMonthsUtc(displayedMonthUtcMillis, -1)
                    },
                    onNextMonth = {
                        displayedMonthUtcMillis = addMonthsUtc(displayedMonthUtcMillis, 1)
                    },
                )
                TaskReminderCalendar(
                    monthUtcMillis = displayedMonthUtcMillis,
                    selectedDateUtcMillis = selectedDateUtcMillis,
                    onDateSelected = {
                        selectedDateUtcMillis = it
                        quickReminderAt = null
                        quickReminderLabel = null
                    },
                )
                TaskReminderOptions(
                    timeText = if (timeSelected) formatClockTime(selectedHour, selectedMinute) else "无",
                    reminderText = quickReminderLabel ?: "无",
                    onTimeClick = { showTimePicker = true },
                    onReminderClick = { showQuickReminderPicker = true },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            val quickAt = quickReminderAt
                            if (quickAt != null) {
                                onReminderSelected(quickAt)
                            } else if (timeSelected) {
                                onReminderSelected(
                                    combineDateAndTime(
                                        selectedDateUtcMillis,
                                        selectedHour,
                                        selectedMinute,
                                    ),
                                )
                            } else {
                                onClear()
                            }
                        },
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }

    if (showTimePicker) {
        TaskReminderTimePickerDialog(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                timeSelected = true
                quickReminderAt = null
                quickReminderLabel = null
                showTimePicker = false
            },
        )
    }

    if (showQuickReminderPicker) {
        TaskQuickReminderDialog(
            onDismiss = { showQuickReminderPicker = false },
            onSelected = { delayMillis, label ->
                val targetAt = System.currentTimeMillis() + delayMillis
                quickReminderAt = targetAt
                quickReminderLabel = label
                selectedDateUtcMillis = utcDateMillis(targetAt)
                displayedMonthUtcMillis = monthStartUtcMillis(selectedDateUtcMillis)
                selectedHour = hourOfDay(targetAt)
                selectedMinute = minuteOfHour(targetAt)
                timeSelected = true
                showQuickReminderPicker = false
            },
        )
    }
}

@Composable
private fun TaskReminderTabs(title: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClear) {
            Text("清除", color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(end = 48.dp),
        )
    }
}

@Composable
private fun TaskReminderMonthHeader(
    monthUtcMillis: Long,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPreviousMonth) {
            Text("‹", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = monthTitle(monthUtcMillis),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        TextButton(onClick = onNextMonth) {
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun TaskReminderCalendar(
    monthUtcMillis: Long,
    selectedDateUtcMillis: Long,
    onDateSelected: (Long) -> Unit,
) {
    val weeks = remember(monthUtcMillis) { calendarDayCells(monthUtcMillis).chunked(7) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .size(46.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            TaskReminderDateCell(
                                day = day,
                                selected = sameUtcDate(day.dateUtcMillis, selectedDateUtcMillis),
                                onClick = { onDateSelected(day.dateUtcMillis) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskReminderDateCell(
    day: ReminderCalendarDay,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (selected) colorScheme.primary else colorScheme.surface)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = day.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TaskReminderOptions(
    timeText: String,
    reminderText: String,
    onTimeClick: () -> Unit,
    onReminderClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            TaskReminderOptionRow(
                icon = { Icon(Icons.Outlined.AccessTime, contentDescription = null) },
                title = "时间",
                value = timeText,
                onClick = onTimeClick,
            )
            TaskReminderOptionRow(
                icon = { Icon(Icons.Outlined.Alarm, contentDescription = null) },
                title = "快捷",
                value = reminderText,
                onClick = onReminderClick,
            )
        }
    }
}

@Composable
private fun TaskReminderOptionRow(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private data class QuickReminderOption(
    val label: String,
    val targetAt: () -> Long,
)

private val quickReminderOptions = listOf(
    QuickReminderOption("10秒后") { System.currentTimeMillis() + 10_000L },
    QuickReminderOption("10分钟后") { System.currentTimeMillis() + 10L * 60_000L },
    QuickReminderOption("1小时后") { System.currentTimeMillis() + 60L * 60_000L },
    QuickReminderOption("下个 20:00") { nextTimeMillis(20, 0) },
    QuickReminderOption("明早 09:00") { nextMorningMillis() },
    QuickReminderOption("明晚 20:00") { nextTimeMillis(20, 0, dayOffset = 1) },
)

@Composable
private fun TaskQuickReminderDialog(
    onDismiss: () -> Unit,
    onSelected: (Long, String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快捷提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickReminderOptions.forEach { option ->
                    TaskQuickReminderRow(
                        option = option,
                        onClick = {
                            val targetAt = option.targetAt()
                            onSelected(targetAt - System.currentTimeMillis(), option.label)
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun TaskQuickReminderRow(
    option: QuickReminderOption,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Alarm,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskReminderTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private data class ReminderCalendarDay(
    val dateUtcMillis: Long,
    val dayOfMonth: Int,
    val subtitle: String,
)

private fun calendarDayCells(monthUtcMillis: Long): List<ReminderCalendarDay?> {
    val month = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        timeInMillis = monthUtcMillis
    }
    val firstWeekdayOffset = month.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val maxDay = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val cells = mutableListOf<ReminderCalendarDay?>()
    repeat(firstWeekdayOffset) { cells += null }
    for (day in 1..maxDay) {
        cells += ReminderCalendarDay(
            dateUtcMillis = month.apply { set(Calendar.DAY_OF_MONTH, day) }.timeInMillis,
            dayOfMonth = day,
            subtitle = dateSubtitle(month.timeInMillis),
        )
    }
    while (cells.size % 7 != 0) cells += null
    return cells
}

private fun dateSubtitle(dateUtcMillis: Long): String =
    if (sameUtcDate(dateUtcMillis, utcDateMillis(System.currentTimeMillis()))) "今天" else ""

private fun monthTitle(monthUtcMillis: Long): String =
    SimpleDateFormat("yyyy年M月", Locale.getDefault()).format(Date(monthUtcMillis))

private fun monthStartUtcMillis(dateUtcMillis: Long): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        timeInMillis = dateUtcMillis
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

private fun addMonthsUtc(monthUtcMillis: Long, offset: Int): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        timeInMillis = monthUtcMillis
        add(Calendar.MONTH, offset)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

private fun sameUtcDate(firstUtcMillis: Long, secondUtcMillis: Long): Boolean {
    val first = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = firstUtcMillis }
    val second = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = secondUtcMillis }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

private fun formatClockTime(hour: Int, minute: Int): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

private fun utcDateMillis(timeMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun hourOfDay(timeMillis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timeMillis }.get(Calendar.HOUR_OF_DAY)

private fun minuteOfHour(timeMillis: Long): Int =
    Calendar.getInstance().apply { timeInMillis = timeMillis }.get(Calendar.MINUTE)

private fun combineDateAndTime(
    dateUtcMillis: Long,
    hour: Int,
    minute: Int,
): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(
            utc.get(Calendar.YEAR),
            utc.get(Calendar.MONTH),
            utc.get(Calendar.DAY_OF_MONTH),
            hour,
            minute,
            0,
        )
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun startOfTodayMillis(now: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun endOfTodayMillis(now: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

private fun endOfTomorrowMillis(now: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

private fun nextMorningMillis(): Long =
    nextTimeMillis(hour = 9, minute = 0, dayOffset = 1)

private fun nextTimeMillis(
    hour: Int,
    minute: Int,
    dayOffset: Int = 0,
): Long {
    val now = System.currentTimeMillis()
    return Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_MONTH, dayOffset)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) {
            add(Calendar.DAY_OF_MONTH, 1)
        }
    }.timeInMillis
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    }
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openReminderChannelSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, TaskReminderScheduler.CHANNEL_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun openOverlayPermissionSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
