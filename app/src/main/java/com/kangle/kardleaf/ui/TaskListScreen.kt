package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountTree
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
import androidx.compose.material3.TextButton
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

private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"
private const val TASK_SCAN_LOG_TAG = "KardLeafTaskScan"
private const val GLOBAL_TASK_FILTER_PATH = "\u0000global"

internal enum class TaskFilter(val label: String) {
    ALL("全部"),
    TODAY("今天"),
    UPCOMING("即将到期"),
    OVERDUE("已逾期"),
    COMPLETED("已完成"),
}

internal enum class TaskSort(val label: String) {
    MANUAL("手动排序"),
    DUE("到期时间"),
    PRIORITY("优先级"),
    UPDATED("最近更新"),
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    noteRepository: RoomNoteRepository,
    onOpenDrawer: () -> Unit,
    onOpenNotePath: (String) -> Unit = {},
    onMissingManagedFile: (MissingTaskMarkdownFile) -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val appContext = context.applicationContext
    val taskDao = remember { AppDatabase.getDatabase(appContext).taskDao() }
    val prefsManager = remember { PrefsManager(appContext) }
    val managedTaskPath = TaskMarkdownStore.managedNotePath(prefsManager)
    val scheduler = remember { TaskReminderScheduler(appContext) }
    val taskStore = remember(noteRepository) { TaskMarkdownStore(appContext, noteRepository, taskDao) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionHintState = rememberPermissionHintState(context)
    val tasks by taskDao.observeTasks().collectAsState(initial = emptyList())
    val trashedTasks by taskDao.observeTrashedTasks().collectAsState(initial = emptyList())
    val groups by taskDao.observeGroups().collectAsState(initial = emptyList())
    val managedTaskSource by remember(taskDao, managedTaskPath) {
        taskDao.observeMarkdownTaskSource(managedTaskPath)
    }.collectAsState(initial = null)
    var editingTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showMarkdownTasks by remember {
        mutableStateOf(prefsManager.isShowMarkdownTasksInTaskListEnabled())
    }
    var showGlobalTasksOnly by remember { mutableStateOf(false) }
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
    var taskSort by remember { mutableStateOf(TaskSort.MANUAL) }
    var showTaskFilters by remember { mutableStateOf(false) }
    var showTaskOptions by remember { mutableStateOf(false) }
    var showTaskTrash by remember { mutableStateOf(false) }
    var taskEditMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showSelectionMenu by remember { mutableStateOf(false) }
    var showSelectionMoveDialog by remember { mutableStateOf(false) }
    var showDeleteSelectedDialog by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var taskSaveInProgress by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<TaskGroupEntity?>(null) }
    var newGroupParentPath by remember { mutableStateOf("") }
    var showGroupEditor by remember { mutableStateOf(false) }
    var deletingGroup by remember { mutableStateOf<TaskGroupEntity?>(null) }
    var openedTask by remember { mutableStateOf<TaskEntity?>(null) }
    var editorOpenStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var newTaskParentId by rememberSaveable { mutableStateOf<Long?>(null) }
    var expandedTaskIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var expandedMarkdownTaskKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var activeCollapsed by remember { mutableStateOf(false) }
    var completedCollapsed by remember { mutableStateOf(true) }
    var globalTasksCollapsed by remember { mutableStateOf(false) }
    var editableActiveTasks by remember { mutableStateOf(emptyList<TaskEntity>()) }
    var editableCompletedTasks by remember { mutableStateOf(emptyList<TaskEntity>()) }
    var duplicateTaskIdConflict by remember { mutableStateOf<DuplicateTaskIdConflict?>(null) }
    val editingTask =
        remember(tasks, trashedTasks, editingTaskId) {
            editingTaskId?.let { id -> (tasks + trashedTasks).firstOrNull { it.id == id } }
        }
    fun requestTaskEditor(
        source: String,
        taskId: Long? = null,
        parentTaskId: Long? = null,
    ) {
        editorOpenStartedAtMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(
            KardLeafLogTags.USER_PERF,
            "taskEditor openRequest source=$source taskId=${taskId ?: 0} parentTaskId=${parentTaskId ?: 0} tasks=${tasks.size} groups=${groups.size} markdownTasks=${markdownTasks.size}",
        )
        editingTaskId = taskId
        newTaskParentId = parentTaskId
        showEditor = true
    }
    LaunchedEffect(showEditor, editingTaskId, newTaskParentId) {
        if (showEditor) {
            KardLeafLog.d(
                KardLeafLogTags.USER_PERF,
                "taskEditor begin source=task_list taskId=${editingTaskId ?: 0} parentTaskId=${newTaskParentId ?: 0} elapsed=${editorOpenStartedAtMs?.let { SystemClock.elapsedRealtime() - it } ?: 0}ms",
            )
        }
    }
    LaunchedEffect(showEditor, tasks, groups, markdownTasks) {
        if (showEditor) {
            KardLeafLog.d(
                KardLeafLogTags.USER_PERF,
                "taskEditor dataSnapshot source=task_list tasks=${tasks.size} groups=${groups.size} markdownTasks=${markdownTasks.size} elapsed=${editorOpenStartedAtMs?.let { SystemClock.elapsedRealtime() - it } ?: 0}ms",
            )
        }
    }
    val groupFilteredTasks =
        remember(tasks, trashedTasks, groups, taskGroupFilter, showGlobalTasksOnly, showTaskTrash) {
            when {
                showTaskTrash -> trashedTasks
                showGlobalTasksOnly -> emptyList()
                else -> filterTasksByTaskGroup(tasks, groups, taskGroupFilter)
            }
        }
    val filteredTasks =
        remember(groupFilteredTasks, searchQuery, taskFilter, taskSort, showTaskTrash) {
            filterAndSortTasks(
                groupFilteredTasks,
                searchQuery,
                if (showTaskTrash) TaskFilter.ALL else taskFilter,
                taskSort,
            )
        }
    val managedTaskParentIds =
        remember(tasks, trashedTasks) {
            (tasks + trashedTasks).mapNotNull { task -> task.parentTaskId?.let { task.id to it } }.toMap()
        }
    val managedTaskRootIds =
        remember(tasks, trashedTasks, managedTaskParentIds) {
            TaskHierarchy.rootIds(tasks + trashedTasks, managedTaskParentIds)
        }
    val managedTaskById =
        remember(tasks, trashedTasks) {
            (tasks + trashedTasks).associateBy { it.id }
        }
    val filteredRootOrder =
        remember(filteredTasks, managedTaskRootIds) {
            filteredTasks.mapNotNull { managedTaskRootIds[it.id] }.distinct()
        }
    val filteredRootIds = filteredRootOrder.toSet()
    val activeRootIds =
        remember(filteredRootIds, managedTaskById, taskFilter, showTaskTrash) {
            if (showTaskTrash || taskFilter == TaskFilter.COMPLETED) {
                emptySet()
            } else {
                filteredRootIds.filter { managedTaskById[it]?.done != true }.toSet()
            }
        }
    val completedRootIds =
        remember(filteredRootIds, managedTaskById, taskFilter, showTaskTrash) {
            if (!showTaskTrash && (taskFilter == TaskFilter.ALL || taskFilter == TaskFilter.COMPLETED)) {
                filteredRootIds.filter { managedTaskById[it]?.done == true }.toSet()
            } else {
                emptySet()
            }
        }
    val activeTasks =
        remember(filteredRootOrder, activeRootIds, managedTaskById) {
            filteredRootOrder.mapNotNull { rootId -> managedTaskById[rootId]?.takeIf { rootId in activeRootIds } }
        }
    val completedTasks =
        remember(filteredRootOrder, completedRootIds, managedTaskById) {
            filteredRootOrder.mapNotNull { rootId -> managedTaskById[rootId]?.takeIf { rootId in completedRootIds } }
        }
    val selectedTasks =
        remember(tasks, trashedTasks, selectedTaskIds, showTaskTrash) {
            val source = if (showTaskTrash) trashedTasks else tasks
            source.filter { it.id in selectedTaskIds }
        }
    val selectedTaskGroup =
        remember(groups, taskGroupFilter) {
            val selectedPath =
                (taskGroupFilter as? MainViewModel.NoteFilter.Label)?.name
                    ?.let(::normalizeFolderPathForUi)
                    ?.takeIf { it.isNotBlank() }
            groups.firstOrNull { normalizeFolderPathForUi(it.name) == selectedPath }
        }
    val markdownTasksByKey =
        remember(markdownTasks) {
            markdownTasks.associateBy(::itemKey)
        }
    val visibleMarkdownTasks =
        remember(markdownTasks, markdownTasksByKey, searchQuery, managedTaskPath) {
            val query = searchQuery.trim()
            markdownTasks.filter { item ->
                markdownTasksByKey[markdownTaskRootKey(item, markdownTasksByKey)]?.done != true &&
                    !(
                        item.notePath == managedTaskPath &&
                            item.taskId?.startsWith(TaskMarkdownStore.TASK_ID_PREFIX) == true
                    ) &&
                    (query.isBlank() || item.taskText.contains(query, ignoreCase = true) ||
                        item.noteTitle.contains(query, ignoreCase = true) ||
                        item.notes.contains(query, ignoreCase = true))
            }
        }
    val managedTaskIndentLevels =
        remember(tasks, trashedTasks, managedTaskParentIds) {
            TaskHierarchy.depths(tasks + trashedTasks, managedTaskParentIds)
        }
    val showGlobalTaskSection =
        !showTaskTrash &&
            showMarkdownTasks &&
            taskFilter == TaskFilter.ALL &&
            taskGroupFilter is MainViewModel.NoteFilter.All

    LaunchedEffect(taskStore, managedTaskSource?.updatedAt, managedTaskSource?.content?.hashCode()) {
        val result = withContext(Dispatchers.IO) { taskStore.synchronize() }
        result.duplicateTaskIdConflict?.let { duplicateTaskIdConflict = it }
        result.missingManagedFile?.let(onMissingManagedFile)
    }

    LaunchedEffect(groups, taskGroupFilter) {
        val selectedPath = (taskGroupFilter as? MainViewModel.NoteFilter.Label)?.name ?: return@LaunchedEffect
        if (selectedPath.isNotBlank() && groups.none { normalizeFolderPathForUi(it.name) == normalizeFolderPathForUi(selectedPath) }) {
            taskGroupFilter = MainViewModel.NoteFilter.All
        }
    }

    LaunchedEffect(showTaskTrash, tasks, trashedTasks) {
        val visibleIds = (if (showTaskTrash) trashedTasks else tasks).mapTo(hashSetOf()) { it.id }
        selectedTaskIds = selectedTaskIds.intersect(visibleIds)
    }

    LaunchedEffect(taskEditMode, activeTasks, completedTasks) {
        if (!taskEditMode) {
            editableActiveTasks = emptyList()
            editableCompletedTasks = emptyList()
            return@LaunchedEffect
        }
        val activeById = activeTasks.associateBy { it.id }
        val completedById = completedTasks.associateBy { it.id }
        editableActiveTasks = editableActiveTasks.mapNotNull { activeById[it.id] } +
            activeTasks.filterNot { task -> editableActiveTasks.any { it.id == task.id } }
        editableCompletedTasks = editableCompletedTasks.mapNotNull { completedById[it.id] } +
            completedTasks.filterNot { task -> editableCompletedTasks.any { it.id == task.id } }
    }
    val displayActiveTasks =
        if (taskEditMode && editableActiveTasks.isNotEmpty()) {
            editableActiveTasks
        } else {
            activeTasks
        }
    val displayCompletedTasks =
        if (taskEditMode && editableCompletedTasks.isNotEmpty()) {
            editableCompletedTasks
        } else {
            completedTasks
        }
    val activeTaskTreeTasks =
        remember(groupFilteredTasks, managedTaskRootIds, activeRootIds) {
            groupFilteredTasks.filter { managedTaskRootIds[it.id] in activeRootIds }
        }
    val completedTaskTreeTasks =
        remember(groupFilteredTasks, managedTaskRootIds, completedRootIds) {
            groupFilteredTasks.filter { managedTaskRootIds[it.id] in completedRootIds }
        }
    val displayActiveTaskTree =
        remember(taskEditMode, displayActiveTasks, activeTaskTreeTasks, managedTaskParentIds, expandedTaskIds) {
            if (taskEditMode) {
                displayActiveTasks
            } else {
                TaskHierarchy.flatten(activeTaskTreeTasks, managedTaskParentIds, expandedTaskIds)
            }
        }
    val displayCompletedTaskTree =
        remember(taskEditMode, displayCompletedTasks, completedTaskTreeTasks, managedTaskParentIds, expandedTaskIds) {
            if (taskEditMode) {
                displayCompletedTasks
            } else {
                TaskHierarchy.flatten(completedTaskTreeTasks, managedTaskParentIds, expandedTaskIds)
            }
        }
    val activeTaskChildCounts =
        remember(activeTaskTreeTasks, managedTaskParentIds) {
            TaskHierarchy.childCounts(activeTaskTreeTasks, managedTaskParentIds)
        }
    val completedTaskChildCounts =
        remember(completedTaskTreeTasks, managedTaskParentIds) {
            TaskHierarchy.childCounts(completedTaskTreeTasks, managedTaskParentIds)
        }
    val visibleMarkdownTaskTree = flattenMarkdownTaskTree(visibleMarkdownTasks, expandedMarkdownTaskKeys)
    val markdownTaskChildCounts = markdownTaskChildCounts(visibleMarkdownTasks)

    fun saveTask(
        original: TaskEntity?,
        result: TaskEditorResult,
    ) {
        if (taskSaveInProgress) return
        taskSaveInProgress = true
        scope.launch {
            val now = System.currentTimeMillis()
            val savedTask =
                try {
                    withContext(Dispatchers.IO) {
                        taskStore.saveTaskBatch(
                            original = original,
                            candidate = result.toTaskEntity(original, now),
                            parentTaskId = result.parentTaskId,
                            parentTaskSelectionChanged = result.parentTaskSelectionChanged,
                            childTaskTexts = result.childTaskTexts,
                        )?.task
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    KardLeafLog.e(KardLeafLogTags.TASK_SAVE, "task screen save failed", error)
                    null
                }
            if (savedTask == null) {
                taskSaveInProgress = false
                context.showToast("任务保存失败，请检查笔记库权限")
                return@launch
            }
            taskSaveInProgress = false
            showEditor = false
            editingTaskId = null
            newTaskParentId = null
            runCatching { scheduler.schedule(savedTask) }.onFailure { error ->
                KardLeafLog.e(KardLeafLogTags.TASK_SAVE, "task reminder scheduling failed id=${savedTask.id}", error)
            }
            if (savedTask.reminderAt != null && !TaskReminderScheduler.areNotificationsEnabled(context)) {
                context.showToast("系统通知未开启，提醒可能无法弹出")
            }
        }
    }

    fun selectTask(task: TaskEntity) {
        selectedTaskIds =
            if (task.id in selectedTaskIds) {
                selectedTaskIds - task.id
            } else {
                selectedTaskIds + task.id
            }
    }

    fun reorderTasks(
        current: List<TaskEntity>,
        fromIndex: Int,
        toIndex: Int,
        completed: Boolean,
    ) {
        if (fromIndex == toIndex || fromIndex !in current.indices || toIndex !in current.indices) return
        taskSort = TaskSort.MANUAL
        val reordered =
            current.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        if (completed) {
            editableCompletedTasks = reordered
        } else {
            editableActiveTasks = reordered
        }
        scope.launch {
            val success =
                withContext(Dispatchers.IO) {
                    taskStore.reorderTasks(reordered.map { it.id })
                }
            if (!success) {
                if (completed) {
                    editableCompletedTasks = current
                } else {
                    editableActiveTasks = current
                }
                context.showToast("任务排序保存失败，请检查笔记库权限")
            }
        }
    }

    fun showUndoSnackbar(
        message: String,
        undo: suspend () -> Boolean,
    ) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result =
                snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = "撤回",
                    withDismissAction = false,
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                val success = withContext(Dispatchers.IO) { undo() }
                if (!success) context.showToast("撤回失败，请检查笔记库权限")
            }
        }
    }

    fun enterTaskSelection(task: TaskEntity) {
        selectedTaskIds = selectedTaskIds + task.id
    }

    fun moveSelectedToTrash() {
        val targets = selectedTasks
        selectedTaskIds = emptySet()
        showSelectionMenu = false
        scope.launch {
            val success = withContext(Dispatchers.IO) { taskStore.moveTasksToTrash(targets) }
            if (!success) {
                context.showToast("任务移入回收站失败，请检查笔记库权限")
            } else {
                showUndoSnackbar("已移入回收站") { taskStore.restoreTasks(targets) }
            }
        }
    }

    fun restoreSelectedTasks() {
        val targets = selectedTasks
        selectedTaskIds = emptySet()
        showSelectionMenu = false
        scope.launch {
            val success = withContext(Dispatchers.IO) { taskStore.restoreTasks(targets) }
            if (!success) context.showToast("任务恢复失败，请检查笔记库权限")
        }
    }

    fun permanentlyDeleteSelectedTasks() {
        val targets = selectedTasks
        selectedTaskIds = emptySet()
        showSelectionMenu = false
        scope.launch {
            val success = withContext(Dispatchers.IO) { taskStore.deleteTasksPermanently(targets) }
            if (!success) context.showToast("任务永久删除失败，请检查笔记库权限")
        }
    }

    fun moveSelectedToGroup(groupId: Long?) {
        val targets = selectedTasks
        selectedTaskIds = emptySet()
        showSelectionMoveDialog = false
        scope.launch {
            val success =
                withContext(Dispatchers.IO) {
                    taskStore.moveTasksToGroup(targets, groupId, System.currentTimeMillis())
                }
            if (!success) context.showToast("任务移动失败，请检查笔记库权限")
        }
    }

    fun editSelectedTask() {
        val task = selectedTasks.singleOrNull() ?: return
        selectedTaskIds = emptySet()
        showSelectionMenu = false
        requestTaskEditor("selection_edit", task.id, managedTaskParentIds[task.id])
    }

    fun emptyTaskTrash() {
        showEmptyTrashDialog = false
        selectedTaskIds = emptySet()
        scope.launch {
            val success = withContext(Dispatchers.IO) { taskStore.deleteTasksPermanently(trashedTasks) }
            if (!success) context.showToast("清空任务回收站失败，请检查笔记库权限")
        }
    }

    fun openTaskTrash() {
        showTaskOptions = false
        showTaskTrash = true
        taskEditMode = false
        selectedTaskIds = emptySet()
        showGlobalTasksOnly = false
    }

    fun closeTaskTrash() {
        showTaskOptions = false
        showTaskTrash = false
        selectedTaskIds = emptySet()
    }

    fun deleteTask(task: TaskEntity) {
        scope.launch {
            val success = withContext(Dispatchers.IO) { taskStore.moveTasksToTrash(listOf(task)) }
            if (!success) {
                context.showToast("任务移入回收站失败，请检查笔记库权限")
            } else {
                showUndoSnackbar("已移入回收站") { taskStore.restoreTasks(listOf(task)) }
            }
        }
    }

    fun toggleDone(
        task: TaskEntity,
        done: Boolean,
        onSuccess: (TaskEntity) -> Unit = {},
    ) {
        if (done) TaskCompletionFeedback.perform(context)
        scope.launch {
            val now = System.currentTimeMillis()
            val updated = task.copy(done = done, updatedAt = now)
            val result =
                withContext(Dispatchers.IO) {
                    taskStore.setTaskDone(task, updated)
                }
            if (result == null) {
                context.showToast("任务状态保存失败，请检查笔记库权限")
            } else {
                onSuccess(result.first)
                scheduler.schedule(result.first)
                result.second?.let(scheduler::schedule)
                if (done) {
                    showUndoSnackbar("已完成") {
                        taskStore.undoTaskCompletion(result.first, result.second)
                    }
                }
            }
        }
    }

    fun moveTask(
        task: TaskEntity,
        groupId: Long?,
    ) {
        scope.launch {
            val success =
                withContext(Dispatchers.IO) {
                    taskStore.moveTask(task, groupId, System.currentTimeMillis())
                }
            if (!success) context.showToast("任务移动失败，请检查笔记库权限")
        }
    }

    fun saveGroup(
        group: TaskGroupEntity?,
        name: String,
    ) {
        val parentPath =
            when {
                group != null -> group.name.substringBeforeLast('/', missingDelimiterValue = "")
                else -> newGroupParentPath
            }
        val resolvedPath = resolveTaskGroupPath(parentPath, name)
        if (resolvedPath.isBlank() || groups.any { it.id != group?.id && it.name.equals(resolvedPath, ignoreCase = true) }) {
            context.showToast(if (resolvedPath.isBlank()) "分组名称不能为空" else "已有同名分组")
            return
        }
        scope.launch {
            val saved = withContext(Dispatchers.IO) { taskStore.saveGroup(group, resolvedPath) }
            if (saved == null) context.showToast("分组保存失败，请检查笔记库权限")
        }
        showGroupEditor = false
    }

    fun moveGroup(
        group: TaskGroupEntity,
        offset: Int,
    ) {
        val index = groups.indexOfFirst { it.id == group.id }
        val other = groups.getOrNull(index + offset) ?: return
        scope.launch {
            val success = withContext(Dispatchers.IO) { taskStore.swapGroups(group, other) }
            if (!success) context.showToast("分组排序保存失败，请检查笔记库权限")
        }
    }

    fun testReminderTask(text: String): TaskEntity {
        val now = System.currentTimeMillis()
        return TaskEntity(
            id = (now % 1_000_000_000L) + 9_000_000_000L,
            taskText = text,
            done = false,
            reminderAt = now + 10_000L,
            createdAt = now,
            updatedAt = now,
        )
    }

    fun runReminderTest() {
        val task = testReminderTask("测试提醒")
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "test delayed start id=${task.id} delayMs=10000")
        scheduler.schedule(task)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = "10 秒后提醒",
                duration = SnackbarDuration.Long,
            )
        }
    }

    BackHandler(enabled = openedTask != null || taskEditMode || selectedTaskIds.isNotEmpty() || showSearch || showTaskTrash) {
        when {
            openedTask != null -> openedTask = null
            taskEditMode -> taskEditMode = false
            selectedTaskIds.isNotEmpty() -> {
                selectedTaskIds = emptySet()
                showSelectionMenu = false
            }
            showSearch -> {
                showSearch = false
                searchQuery = ""
                focusManager.clearFocus()
            }
            else -> closeTaskTrash()
        }
    }

    val detailTask = openedTask
    if (detailTask != null) {
        val detailSubtasks =
            remember(detailTask.id, tasks, managedTaskParentIds) {
                val descendantIds = TaskHierarchy.descendants(tasks, setOf(detailTask.id))
                val subtree = tasks.filter { it.id == detailTask.id || it.id in descendantIds }
                TaskHierarchy.flatten(subtree, managedTaskParentIds, descendantIds + detailTask.id)
                    .filterNot { it.id == detailTask.id }
            }
        val detailRootDepth = TaskHierarchy.depth(detailTask.id, managedTaskParentIds)
        val detailSubtaskIndentLevels =
            remember(detailTask.id, detailSubtasks, managedTaskParentIds) {
                detailSubtasks.associate { subtask ->
                    subtask.id to (TaskHierarchy.depth(subtask.id, managedTaskParentIds) - detailRootDepth - 1)
                        .coerceAtLeast(0)
                }
            }
        TaskDetailScreen(
            task = detailTask,
            groups = groups,
            subtasks = detailSubtasks,
            subtaskIndentLevels = detailSubtaskIndentLevels,
            onBack = { openedTask = null },
            onEdit = {
                openedTask = null
                requestTaskEditor("detail_edit", detailTask.id, managedTaskParentIds[detailTask.id])
            },
            onAddSubtask = {
                openedTask = null
                requestTaskEditor("detail_add_subtask", parentTaskId = detailTask.id)
            },
            onToggleDone = { done ->
                toggleDone(detailTask, done) { updatedTask -> openedTask = updatedTask }
            },
        )
        return
    }

    Scaffold(
        topBar = {
            if (selectedTaskIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${selectedTaskIds.size} 个已选中") },
                    navigationIcon = {
                        IconButton(onClick = {
                            selectedTaskIds = emptySet()
                            showSelectionMenu = false
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "取消选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val visibleIds = filteredTasks.mapTo(hashSetOf()) { it.id }
                            selectedTaskIds =
                                if (visibleIds.isNotEmpty() && selectedTaskIds.containsAll(visibleIds)) {
                                    emptySet()
                                } else {
                                    visibleIds
                                }
                        }) {
                            Icon(Icons.Outlined.SelectAll, contentDescription = "全选")
                        }
                        if (showTaskTrash) {
                            IconButton(onClick = { restoreSelectedTasks() }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = "恢复任务")
                            }
                        }
                        Box {
                            IconButton(onClick = { showSelectionMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "选中任务操作")
                            }
                            DropdownMenu(
                                expanded = showSelectionMenu,
                                onDismissRequest = { showSelectionMenu = false },
                            ) {
                                if (showTaskTrash) {
                                    DropdownMenuItem(
                                        text = { Text("永久删除") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            showSelectionMenu = false
                                            showDeleteSelectedDialog = true
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("编辑") },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                        enabled = selectedTasks.size == 1,
                                        onClick = { editSelectedTask() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("移动到分组") },
                                        leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null) },
                                        onClick = {
                                            showSelectionMenu = false
                                            showSelectionMoveDialog = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("移入回收站") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            showSelectionMenu = false
                                            showDeleteSelectedDialog = true
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Box(
                            modifier =
                                Modifier
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
                                    text = if (showTaskTrash) "任务回收站" else taskScreenTitle(taskFilter, taskGroupFilter),
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
                                    modifier =
                                        Modifier
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
                                        requestFocus = showSearch,
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
                        if (!showTaskTrash) {
                            Box {
                                val hasActiveFilters =
                                    taskFilter != TaskFilter.ALL ||
                                        taskGroupFilter !is MainViewModel.NoteFilter.All ||
                                        taskSort != TaskSort.MANUAL ||
                                        showGlobalTasksOnly
                                IconButton(onClick = { showTaskFilters = true }) {
                                    Icon(
                                        Icons.Outlined.FilterList,
                                        contentDescription = if (hasActiveFilters) "筛选任务，已启用" else "筛选任务",
                                        tint =
                                            if (hasActiveFilters) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                    )
                                }
                                TaskFilterMenu(
                                    expanded = showTaskFilters,
                                    taskFilter = taskFilter,
                                    taskGroupFilter = taskGroupFilter,
                                    taskSort = taskSort,
                                    showGlobalTasksOnly = showGlobalTasksOnly,
                                    groups = groups,
                                    onDismiss = { showTaskFilters = false },
                                    onTaskFilterChange = {
                                        taskFilter = it
                                        if (it != TaskFilter.ALL) showGlobalTasksOnly = false
                                        if (it == TaskFilter.COMPLETED) completedCollapsed = false
                                        showTaskFilters = false
                                    },
                                    onTaskGroupFilterChange = {
                                        taskGroupFilter = it
                                        showGlobalTasksOnly = false
                                        showTaskFilters = false
                                    },
                                    onTaskSortChange = {
                                        taskSort = it
                                        showTaskFilters = false
                                    },
                                    onReset = {
                                        taskFilter = TaskFilter.ALL
                                        taskGroupFilter = MainViewModel.NoteFilter.All
                                        taskSort = TaskSort.MANUAL
                                        showGlobalTasksOnly = false
                                        showTaskFilters = false
                                    },
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { showTaskOptions = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多选项")
                            }
                            DropdownMenu(
                                expanded = showTaskOptions,
                                onDismissRequest = { showTaskOptions = false },
                            ) {
                                if (showTaskTrash) {
                                    DropdownMenuItem(
                                        text = { Text("返回任务清单") },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                        onClick = { closeTaskTrash() },
                                    )
                                    if (trashedTasks.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("清空回收站") },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                            onClick = {
                                                showTaskOptions = false
                                                showEmptyTrashDialog = true
                                            },
                                        )
                                    }
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(if (taskEditMode) "完成编辑" else "编辑") },
                                        leadingIcon = {
                                            Icon(
                                                if (taskEditMode) Icons.Outlined.Check else Icons.Outlined.Edit,
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            taskEditMode = !taskEditMode
                                            selectedTaskIds = emptySet()
                                            showTaskOptions = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("新建分组") },
                                        onClick = {
                                            showTaskOptions = false
                                            editingGroup = null
                                            newGroupParentPath =
                                                (taskGroupFilter as? MainViewModel.NoteFilter.Label)
                                                    ?.name
                                                    .orEmpty()
                                            showGroupEditor = true
                                        },
                                    )
                                    selectedTaskGroup?.let { group ->
                                        DropdownMenuItem(
                                            text = { Text("重命名当前分组") },
                                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                            onClick = {
                                                showTaskOptions = false
                                                editingGroup = group
                                                showGroupEditor = true
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("上移当前分组") },
                                            leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                                            enabled = groups.indexOfFirst { it.id == group.id } > 0,
                                            onClick = {
                                                showTaskOptions = false
                                                moveGroup(group, -1)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("下移当前分组") },
                                            leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                                            enabled = groups.indexOfFirst { it.id == group.id } < groups.lastIndex,
                                            onClick = {
                                                showTaskOptions = false
                                                moveGroup(group, 1)
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("删除当前分组") },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                            onClick = {
                                                showTaskOptions = false
                                                deletingGroup = group
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("回收站") },
                                        onClick = { openTaskTrash() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("全局任务") },
                                        trailingIcon = {
                                            if (showMarkdownTasks) {
                                                Icon(Icons.Outlined.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            showMarkdownTasks = !showMarkdownTasks
                                            if (!showMarkdownTasks) showGlobalTasksOnly = false
                                            prefsManager.saveShowMarkdownTasksInTaskList(showMarkdownTasks)
                                            showTaskOptions = false
                                        },
                                    )
                                    if (BuildConfig.KARDLEAF_DEV_VARIANT) {
                                        DropdownMenuItem(
                                            text = { Text("测试提醒") },
                                            onClick = {
                                                showTaskOptions = false
                                                runReminderTest()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 80.dp),
            )
        },
        floatingActionButton = {
            if (!showTaskTrash) {
                FloatingActionButton(onClick = { requestTaskEditor("task_list_fab") }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新建任务")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (permissionHintState.visible) {
                item {
                    PermissionHint(permissionHintState, context)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            if (!showTaskTrash && taskFilter == TaskFilter.ALL &&
                !showSearch &&
                (groups.isNotEmpty() || showMarkdownTasks || taskEditMode)
            ) {
                item {
                    FolderPathStrip(
                        currentFilter = taskGroupFilter,
                        labels = groups.map { it.name },
                        rootChip = FolderChipData("全部任务", ""),
                        leadingChips =
                            if (showMarkdownTasks) {
                                listOf(FolderChipData("全局任务", GLOBAL_TASK_FILTER_PATH))
                            } else {
                                emptyList()
                            },
                        selectedLeadingPath = GLOBAL_TASK_FILTER_PATH.takeIf { showGlobalTasksOnly },
                        onOpenFolder = { path ->
                            if (path == GLOBAL_TASK_FILTER_PATH) {
                                showGlobalTasksOnly = true
                                taskGroupFilter = MainViewModel.NoteFilter.All
                            } else if (path.isBlank()) {
                                showGlobalTasksOnly = false
                                taskGroupFilter = MainViewModel.NoteFilter.All
                            } else {
                                showGlobalTasksOnly = false
                                taskGroupFilter = MainViewModel.NoteFilter.Label(path)
                            }
                        },
                        onShowAllInFolder = { path ->
                            showGlobalTasksOnly = false
                            val currentGroup = taskGroupFilter as? MainViewModel.NoteFilter.Label
                            taskGroupFilter =
                                MainViewModel.NoteFilter.Label(
                                    path,
                                    recursive = currentGroup?.recursive != true,
                                )
                        },
                        editMode = taskEditMode,
                        onAddFolder = { parentPath ->
                            editingGroup = null
                            newGroupParentPath = parentPath
                            showGroupEditor = true
                        },
                    )
                }
            }
            if (((showTaskTrash && filteredTasks.isEmpty()) ||
                    (!showTaskTrash && activeTasks.isEmpty() && completedTasks.isEmpty())) &&
                !showGlobalTaskSection
            ) {
                item {
                    EmptyTaskState(
                        filter = taskFilter,
                        searching = searchQuery.isNotBlank(),
                        filteringGroup = taskGroupFilter !is MainViewModel.NoteFilter.All,
                        trash = showTaskTrash,
                    )
                }
            } else {
                if (showTaskTrash) {
                    items(filteredTasks, key = { "trash-${it.id}" }) { task ->
                        TaskRow(
                            task = task,
                            groups = groups,
                            isSelected = task.id in selectedTaskIds,
                            selectionMode = selectedTaskIds.isNotEmpty(),
                            isTrash = true,
                            indentLevel = managedTaskIndentLevels[task.id] ?: 0,
                            hasChildren = false,
                            expanded = false,
                            onToggleChildren = {},
                            showGroupName = true,
                            onToggleDone = {},
                            onEdit = {},
                            onSelect = { selectTask(task) },
                            onLongPress = { enterTaskSelection(task) },
                            onDelete = {},
                            onOpenNotePath = onOpenNotePath,
                        )
                    }
                } else if (!showGlobalTasksOnly && taskFilter != TaskFilter.COMPLETED && activeTasks.isNotEmpty()) {
                    item(key = "active-section") {
                        TaskSection(
                            title = "未完成",
                            count = activeTasks.size,
                            collapsed = activeCollapsed,
                            onToggle = { activeCollapsed = !activeCollapsed },
                            tasks = displayActiveTaskTree,
                            editMode = taskEditMode,
                            onReorder = { from, to -> reorderTasks(displayActiveTasks, from, to, completed = false) },
                        ) { task, dragModifier ->
                            TaskRow(
                                task = task,
                                groups = groups,
                                isSelected = task.id in selectedTaskIds,
                                selectionMode = selectedTaskIds.isNotEmpty(),
                                isTrash = false,
                                indentLevel = managedTaskIndentLevels[task.id] ?: 0,
                                hasChildren = (activeTaskChildCounts[task.id] ?: 0) > 0,
                                expanded = task.id in expandedTaskIds,
                                onToggleChildren = {
                                    expandedTaskIds =
                                        if (task.id in expandedTaskIds) {
                                            expandedTaskIds - task.id
                                        } else {
                                            expandedTaskIds + task.id
                                        }
                                },
                                editMode = taskEditMode,
                                dragModifier = dragModifier,
                                showGroupName = taskGroupFilter is MainViewModel.NoteFilter.All,
                                onToggleDone = { toggleDone(task, it) },
                                onEdit = { openedTask = task },
                                onSelect = { selectTask(task) },
                                onLongPress = { enterTaskSelection(task) },
                                onDelete = { deleteTask(task) },
                                onOpenNotePath = onOpenNotePath,
                            )
                        }
                    }
                }
                if (showGlobalTaskSection) {
                    item(key = "global-tasks-header") {
                        CompletedTasksHeader(
                            title = "全局任务",
                            count = visibleMarkdownTasks.size,
                            collapsed = globalTasksCollapsed,
                            onToggle = { globalTasksCollapsed = !globalTasksCollapsed },
                        )
                    }
                    if (!globalTasksCollapsed) {
                        items(
                            visibleMarkdownTaskTree,
                            key = { item -> "global-${item.notePath}:${item.lineNumber}:${item.taskText}" },
                        ) { item ->
                            MarkdownTaskRow(
                                item = item,
                                hasChildren = (markdownTaskChildCounts[itemKey(item)] ?: 0) > 0,
                                expanded = itemKey(item) in expandedMarkdownTaskKeys,
                                onToggleChildren = {
                                    val key = itemKey(item)
                                    expandedMarkdownTaskKeys =
                                        if (key in expandedMarkdownTaskKeys) {
                                            expandedMarkdownTaskKeys - key
                                        } else {
                                            expandedMarkdownTaskKeys + key
                                        }
                                },
                                onToggleDone = { done ->
                                    if (done) TaskCompletionFeedback.perform(context)
                                    scope.launch {
                                        val success =
                                            withContext(Dispatchers.IO) {
                                                taskStore.setMarkdownTaskDone(item, done)
                                            }
                                        if (success && done) {
                                            showUndoSnackbar("已完成") {
                                                taskStore.setMarkdownTaskDone(item, false)
                                            }
                                        }
                                        if (!success) context.showToast("源任务已变化，未覆盖原笔记")
                                    }
                                },
                                onOpenNotePath = onOpenNotePath,
                            )
                        }
                    }
                }
                if (displayCompletedTasks.isNotEmpty()) {
                    item(key = "completed-header") {
                        TaskSection(
                            title = "已完成",
                            count = completedTasks.size,
                            collapsed = completedCollapsed,
                            onToggle = { completedCollapsed = !completedCollapsed },
                            tasks = displayCompletedTaskTree,
                            editMode = taskEditMode,
                            onReorder = { from, to -> reorderTasks(displayCompletedTasks, from, to, completed = true) },
                        ) { task, dragModifier ->
                            TaskRow(
                                task = task,
                                groups = groups,
                                isSelected = task.id in selectedTaskIds,
                                selectionMode = selectedTaskIds.isNotEmpty(),
                                isTrash = false,
                                indentLevel = managedTaskIndentLevels[task.id] ?: 0,
                                hasChildren = (completedTaskChildCounts[task.id] ?: 0) > 0,
                                expanded = task.id in expandedTaskIds,
                                onToggleChildren = {
                                    expandedTaskIds =
                                        if (task.id in expandedTaskIds) {
                                            expandedTaskIds - task.id
                                        } else {
                                            expandedTaskIds + task.id
                                        }
                                },
                                editMode = taskEditMode,
                                dragModifier = dragModifier,
                                showGroupName = taskGroupFilter is MainViewModel.NoteFilter.All,
                                onToggleDone = { toggleDone(task, it) },
                                onEdit = { openedTask = task },
                                onSelect = { selectTask(task) },
                                onLongPress = { enterTaskSelection(task) },
                                onDelete = { deleteTask(task) },
                                onOpenNotePath = onOpenNotePath,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
                TaskEditorDialog(
                    task = editingTask,
                    groups = groups,
                    parentTaskIds = managedTaskParentIds,
                    initialGroupId =
                        editingTask?.groupId
                            ?: newTaskParentId?.let { parentId -> tasks.firstOrNull { it.id == parentId }?.groupId }
                            ?: selectedTaskGroup?.id,
                    initialParentTaskId = newTaskParentId,
                    autoFocusTitle = true,
                    openStartedAtMs = editorOpenStartedAtMs,
                    onDismiss = {
                        if (!taskSaveInProgress) {
                            showEditor = false
                            editingTaskId = null
                            newTaskParentId = null
                        }
                    },
                    onSave = { result -> saveTask(editingTask, result) },
                )
    }

    if (showSelectionMoveDialog) {
        AlertDialog(
            onDismissRequest = { showSelectionMoveDialog = false },
            title = { Text("移动到分组") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (listOf<TaskGroupEntity?>(null) + groups).forEach { group ->
                        TextButton(
                            onClick = { moveSelectedToGroup(group?.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (group == null) "未分组" else group.name,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSelectionMoveDialog = false }) { Text("取消") } },
        )
    }

    if (showDeleteSelectedDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedDialog = false },
            title = { Text(if (showTaskTrash) "永久删除任务？" else "移入任务回收站？") },
            text = {
                Text(
                    if (showTaskTrash) {
                        "选中的 ${selectedTasks.size} 个任务将永久删除，无法恢复。"
                    } else {
                        "选中的 ${selectedTasks.size} 个任务会移入任务回收站。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSelectedDialog = false
                    if (showTaskTrash) permanentlyDeleteSelectedTasks() else moveSelectedToTrash()
                }) {
                    Text(if (showTaskTrash) "永久删除" else "移入回收站")
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteSelectedDialog = false }) { Text("取消") } },
        )
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text("清空任务回收站？") },
            text = { Text("回收站中的 ${trashedTasks.size} 个任务将永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = { emptyTaskTrash() }) { Text("清空回收站") }
            },
            dismissButton = { TextButton(onClick = { showEmptyTrashDialog = false }) { Text("取消") } },
        )
    }

    if (showGroupEditor) {
        GroupEditorDialog(
            group = editingGroup,
            parentPath =
                if (editingGroup == null) {
                    newGroupParentPath
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
                    scope.launch {
                        val success = withContext(Dispatchers.IO) { taskStore.deleteGroup(group) }
                        if (!success) context.showToast("分组删除失败，请检查笔记库权限")
                    }
                }) { Text("删除分组") }
            },
            dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text("取消") } },
        )
    }

    duplicateTaskIdConflict?.let { conflict ->
        DuplicateTaskIdDialog(
            conflict = conflict,
            onDismiss = { duplicateTaskIdConflict = null },
            onSave = { firstId, firstText, secondId, secondText ->
                scope.launch {
                    val success =
                        withContext(Dispatchers.IO) {
                            taskStore.resolveDuplicateTaskId(
                                conflict = conflict,
                                firstTaskId = firstId,
                                firstTaskText = firstText,
                                secondTaskId = secondId,
                                secondTaskText = secondText,
                            )
                        }
                    if (success) {
                        duplicateTaskIdConflict = null
                    } else {
                        context.showToast("重复 ID 保存失败，请重新检查任务内容")
                    }
                }
            },
        )
    }
}

@Composable
private fun DuplicateTaskIdDialog(
    conflict: DuplicateTaskIdConflict,
    onDismiss: () -> Unit,
    onSave: (firstId: String, firstText: String, secondId: String, secondText: String) -> Unit,
) {
    var firstId by remember(conflict) { mutableStateOf(conflict.first.taskId.orEmpty()) }
    var firstText by remember(conflict) { mutableStateOf(conflict.first.taskText) }
    var secondId by remember(conflict) { mutableStateOf(conflict.second.taskId.orEmpty()) }
    var secondText by remember(conflict) { mutableStateOf(conflict.second.taskText) }
    val canSave =
        firstId.isNotBlank() && secondId.isNotBlank() &&
            firstText.isNotBlank() && secondText.isNotBlank() && firstId.trim() != secondId.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现重复任务 ID") },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("两个任务使用了同一个 ID，请修改任务名称或 ID 后再保存。")
                Text("任务 1 · 第 ${conflict.first.lineNumber} 行")
                OutlinedTextField(
                    value = firstText,
                    onValueChange = { firstText = it },
                    label = { Text("任务名称") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = firstId,
                    onValueChange = { firstId = it },
                    label = { Text("任务 ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("任务 2 · 第 ${conflict.second.lineNumber} 行")
                OutlinedTextField(
                    value = secondText,
                    onValueChange = { secondText = it },
                    label = { Text("任务名称") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                OutlinedTextField(
                    value = secondId,
                    onValueChange = { secondId = it },
                    label = { Text("任务 ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(firstId, firstText, secondId, secondText) },
                enabled = canSave,
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
    val searched =
        tasks.filter { task ->
            query.isBlank() || task.taskText.contains(query, ignoreCase = true) ||
                task.notes.contains(query, ignoreCase = true)
        }
    val filtered =
        searched.filter { task ->
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
        TaskSort.MANUAL -> filtered.sortedWith(compareBy<TaskEntity> { it.manualOrder }.thenBy { it.id })
        TaskSort.DUE ->
            filtered.sortedWith(
                compareBy<TaskEntity> { (it.dueAt ?: it.reminderAt) == null }
                    .thenBy { it.dueAt ?: it.reminderAt }
                    .thenByDescending { it.priority }
                    .thenByDescending { it.updatedAt },
            )
        TaskSort.PRIORITY ->
            filtered.sortedWith(
                compareByDescending<TaskEntity> { it.priority }
                    .thenBy { (it.dueAt ?: it.reminderAt) == null }
                    .thenBy { it.dueAt ?: it.reminderAt }
                    .thenByDescending { it.updatedAt },
            )
        TaskSort.UPDATED -> filtered.sortedByDescending { it.updatedAt }
    }
}

internal fun filterTasksByTaskGroup(
    tasks: List<TaskEntity>,
    groups: List<TaskGroupEntity>,
    filter: MainViewModel.NoteFilter,
): List<TaskEntity> {
    val folderFilter = filter as? MainViewModel.NoteFilter.Label ?: return tasks
    val selectedPath = normalizeFolderPathForUi(folderFilter.name)
    val groupPaths = groups.associate { it.id to normalizeFolderPathForUi(it.name) }
    if (selectedPath.isBlank()) return tasks.filter { task -> task.groupId?.let(groupPaths::containsKey) != true }
    return tasks.filter { task ->
        val taskPath = task.groupId?.let(groupPaths::get).orEmpty()
        taskPath == selectedPath || (folderFilter.recursive && taskPath.startsWith("$selectedPath/"))
    }
}

private data class LegacyTaskGroupTreeRow(
    val group: TaskGroupEntity,
    val depth: Int,
)

private fun legacyTaskGroupTreeRows(groups: List<TaskGroupEntity>): List<LegacyTaskGroupTreeRow> {
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
                add(LegacyTaskGroupTreeRow(group, depth))
                append(normalizeFolderPathForUi(group.name), depth + 1)
            }
        }
        append("", 0)
    }
}

@Composable
private fun LegacyTaskGroupMenuRow(
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

private fun itemKey(item: MarkdownTaskItem): String = "${item.notePath}:${item.lineNumber}"

private fun markdownTaskParentKey(
    item: MarkdownTaskItem,
    itemsByKey: Map<String, MarkdownTaskItem>,
): String? =
    item.parentLineNumber
        ?.let { line -> itemsByKey["${item.notePath}:$line"] }
        ?.let(::itemKey)

private fun markdownTaskRootKey(
    item: MarkdownTaskItem,
    itemsByKey: Map<String, MarkdownTaskItem>,
): String {
    var currentKey = itemKey(item)
    val visited = hashSetOf<String>()
    while (visited.add(currentKey)) {
        val current = itemsByKey[currentKey] ?: return currentKey
        currentKey = markdownTaskParentKey(current, itemsByKey) ?: return currentKey
    }
    return currentKey
}

private fun flattenMarkdownTaskTree(
    items: List<MarkdownTaskItem>,
    expandedKeys: Set<String>,
): List<MarkdownTaskItem> {
    val itemsByKey = items.associateBy(::itemKey)
    val childrenByParent =
        items.mapNotNull { item ->
            markdownTaskParentKey(item, itemsByKey)?.let { parentKey -> parentKey to item }
        }.groupBy({ it.first }, { it.second })
    val result = ArrayList<MarkdownTaskItem>(items.size)
    val visited = hashSetOf<String>()

    fun append(item: MarkdownTaskItem) {
        val key = itemKey(item)
        if (!visited.add(key)) return
        result += item
        if (key in expandedKeys) childrenByParent[key].orEmpty().forEach(::append)
    }

    items.filter { markdownTaskParentKey(it, itemsByKey) == null }.forEach(::append)
    if (visited.isEmpty()) {
        items.forEach(::append)
    }
    return result
}

private fun markdownTaskChildCounts(items: List<MarkdownTaskItem>): Map<String, Int> {
    val itemsByKey = items.associateBy(::itemKey)
    return items
        .mapNotNull { item -> markdownTaskParentKey(item, itemsByKey) }
        .groupingBy { it }
        .eachCount()
}

private fun resolveTaskGroupPath(
    parentPath: String,
    name: String,
): String =
    normalizeFolderPathForUi(
        listOf(parentPath, name)
            .filter { it.isNotBlank() }
            .joinToString("/"),
    )

private fun taskScreenTitle(
    filter: TaskFilter,
    groupFilter: MainViewModel.NoteFilter,
): String =
    when {
        filter != TaskFilter.ALL -> filter.label
        groupFilter is MainViewModel.NoteFilter.Label && groupFilter.name.isBlank() -> "未分组"
        groupFilter is MainViewModel.NoteFilter.Label -> normalizeFolderPathForUi(groupFilter.name).substringAfterLast('/')
        else -> "任务"
    }


@Composable
private fun TaskSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    requestFocus: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus) {
        if (!requestFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        withFrameNanos { }
        keyboardController?.show()
    }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .padding(horizontal = 8.dp),
            singleLine = true,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
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
private fun TaskFilterMenu(
    expanded: Boolean,
    taskFilter: TaskFilter,
    taskGroupFilter: MainViewModel.NoteFilter,
    taskSort: TaskSort,
    showGlobalTasksOnly: Boolean,
    groups: List<TaskGroupEntity>,
    onDismiss: () -> Unit,
    onTaskFilterChange: (TaskFilter) -> Unit,
    onTaskGroupFilterChange: (MainViewModel.NoteFilter) -> Unit,
    onTaskSortChange: (TaskSort) -> Unit,
    onReset: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        TaskFilterMenuHeader("状态")
        TaskFilter.entries.forEach { filter ->
            TaskFilterMenuOption(
                label = filter.label,
                selected = taskFilter == filter,
                onClick = { onTaskFilterChange(filter) },
            )
        }
        HorizontalDivider()
        TaskFilterMenuHeader("清单")
        TaskFilterMenuOption(
            label = "全部任务",
            selected = taskGroupFilter is MainViewModel.NoteFilter.All,
            onClick = { onTaskGroupFilterChange(MainViewModel.NoteFilter.All) },
        )
        val selectedGroupPath =
            (taskGroupFilter as? MainViewModel.NoteFilter.Label)?.name
                ?.let(::normalizeFolderPathForUi)
        TaskFilterMenuOption(
            label = "未分组",
            selected = selectedGroupPath != null && selectedGroupPath.isBlank(),
            onClick = { onTaskGroupFilterChange(MainViewModel.NoteFilter.Label("")) },
        )
        groups.forEach { group ->
            val groupPath = normalizeFolderPathForUi(group.name)
            TaskFilterMenuOption(
                label = groupPath.replace("/", " › "),
                selected = selectedGroupPath == groupPath,
                onClick = { onTaskGroupFilterChange(MainViewModel.NoteFilter.Label(groupPath)) },
            )
        }
        HorizontalDivider()
        TaskFilterMenuHeader("排序")
        TaskSort.entries.forEach { sort ->
            TaskFilterMenuOption(
                label = sort.label,
                selected = taskSort == sort,
                onClick = { onTaskSortChange(sort) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("重置筛选") },
            enabled =
                taskFilter != TaskFilter.ALL ||
                    taskGroupFilter !is MainViewModel.NoteFilter.All ||
                    taskSort != TaskSort.MANUAL ||
                    showGlobalTasksOnly,
            onClick = onReset,
        )
    }
}

@Composable
private fun TaskFilterMenuHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TaskFilterMenuOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
        onClick = onClick,
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegacyTaskEditorDialog(
    task: TaskEntity?,
    groups: List<TaskGroupEntity> = emptyList(),
    parentTasks: List<TaskEntity> = emptyList(),
    parentTaskIds: Map<Long, Long> = emptyMap(),
    initialGroupId: Long? = null,
    initialParentTaskId: Long? = null,
    autoFocusTitle: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (TaskEditorResult) -> Unit,
) {
    val editorState = rememberTaskEditorState(task, initialGroupId, initialParentTaskId, parentTaskIds)
    with(editorState) {
    val titleFocusRequester = remember { FocusRequester() }
    val lightEditor = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val editorActionColor = if (lightEditor) Color(0xFFCBDDEE) else MaterialTheme.colorScheme.primaryContainer
    val editorFieldColor = if (lightEditor) Color(0xFFF8F8F8) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    val editorPlaceholderColor = if (lightEditor) Color(0xFFC8C1C1) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
    val editorHeaderHeight = 64.dp
    val editorToolbarHeight = 60.dp
    val editorIconSize = 26.dp
    val editorTextSize = 17.sp
    val editorNotesTextSize = 14.sp
    val editorPanelHeight =
        200.dp +
            (if (showInlineChildInput) 44.dp * childTaskTexts.size else 0.dp) +
            (if (reminderAt != null) 48.dp else 0.dp) +
            (if (error != null) 28.dp else 0.dp)
    val childFocusRequesters =
        remember(childTaskTexts.size) {
            List(childTaskTexts.size) { FocusRequester() }
        }

    LaunchedEffect(autoFocusTitle) {
        if (autoFocusTitle) {
            withFrameNanos { }
            titleFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(showInlineChildInput, focusedChildIndex, childTaskTexts.size) {
        val index = focusedChildIndex
        if (showInlineChildInput && index != null) {
            withFrameNanos { }
            childFocusRequesters.getOrNull(index)?.requestFocus()
        }
    }
    BackHandler(enabled = showGroupMenu || showPriorityMenu || showParentMenu) {
        showGroupMenu = false
        showPriorityMenu = false
        showParentMenu = false
    }

    val descendantTaskIds =
        remember(task?.id, parentTaskIds) {
            task?.id?.let { TaskHierarchy.descendants(it, parentTaskIds) }.orEmpty()
        }
    val selectableParentTasks =
        parentTasks.filter { parent ->
            !parent.isTrashed &&
                parent.id != task?.id &&
                parent.id !in descendantTaskIds &&
                parent.groupId == groupId
        }

    LaunchedEffect(groupId, selectableParentTasks) {
        if (parentTaskId != null && selectableParentTasks.none { it.id == parentTaskId }) {
            parentTaskId = null
            parentTaskSelectionChanged = true
        }
    }

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
        val lines = value.replace('\r', '\n').split('\n')
        val next = childTaskTexts.toMutableList()
        next[index] = lines.first()
        if (lines.size > 1) {
            next.addAll(index + 1, lines.drop(1))
            focusedChildIndex = index + 1
        }
        childTaskTexts = next
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
                                    start = 44.dp,
                                    top = 18.dp,
                                    end = 64.dp,
                                )
                                .focusRequester(titleFocusRequester),
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
                    Checkbox(
                        checked = done,
                        onCheckedChange = { done = it },
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 8.dp),
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(editorActionColor)
                                .clickable(onClick = ::submitTask),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "保存",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
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
                                        .focusRequester(childFocusRequesters[index]),
                                textStyle =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = editorNotesTextSize,
                                    ),
                                singleLine = false,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
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
                    LegacyTaskEditorToolbarButton(
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
                        LegacyTaskEditorToolbarButton(
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
                                    text = { Text(legacyPriorityLabel(value)) },
                                    onClick = {
                                        priority = value
                                        showPriorityMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        LegacyTaskEditorToolbarButton(
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
                    Box {
                        LegacyTaskEditorToolbarButton(
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccountTree,
                                    contentDescription = "父任务",
                                    tint =
                                        if (parentTaskId == null) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                    modifier = Modifier.size(editorIconSize),
                                )
                            },
                            onClick = { showParentMenu = true },
                        )
                        DropdownMenu(
                            expanded = showParentMenu,
                            onDismissRequest = { showParentMenu = false },
                            modifier = Modifier.widthIn(min = 180.dp, max = 260.dp),
                            properties =
                                PopupProperties(
                                    focusable = false,
                                    dismissOnBackPress = false,
                                    dismissOnClickOutside = true,
                                ),
                        ) {
                            Text(
                                text = "选择父任务",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            DropdownMenuItem(
                                text = { Text("无父任务") },
                                trailingIcon = {
                                    if (parentTaskId == null) {
                                        Icon(Icons.Outlined.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    parentTaskId = null
                                    parentTaskSelectionChanged = true
                                    showParentMenu = false
                                },
                            )
                            selectableParentTasks
                                .forEach { parent ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(parent.taskText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        trailingIcon = {
                                            if (parent.id == parentTaskId) {
                                                Icon(Icons.Outlined.Check, contentDescription = null)
                                            }
                                        },
                                        onClick = {
                                            parentTaskId = parent.id
                                            parentTaskSelectionChanged = true
                                            groupId = parent.groupId
                                            showParentMenu = false
                                        },
                                    )
                                }
                        }
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
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                decorFitsSystemWindows = false,
            ),
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.setDimAmount(0.12f)
            onDispose { }
        }
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 2.dp, vertical = 2.dp),
        ) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(editorPanelHeight.coerceAtMost(maxHeight))
                        .clip(panelShape),
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
                                .fillMaxHeight(),
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
                            LegacyTaskGroupMenuRow(
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
                            legacyTaskGroupTreeRows(groups).forEach { row ->
                                LegacyTaskGroupMenuRow(
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
private fun LegacyTaskEditorToolbarButton(
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

private fun legacyPriorityLabel(priority: Int): String =
    when (priority) {
        3 -> "高优先级"
        2 -> "中优先级"
        1 -> "低优先级"
        else -> "无优先级"
    }
