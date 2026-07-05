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
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TASK_REMINDER_PATTERN = "yyyy-MM-dd HH:mm"
private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"
private const val TASK_SCAN_LOG_TAG = "KardLeafTaskScan"


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onOpenDrawer: () -> Unit,
    onOpenNotePath: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val taskDao = remember { AppDatabase.getDatabase(appContext).taskDao() }
    val scheduler = remember { TaskReminderScheduler(appContext) }
    val scope = rememberCoroutineScope()
    val tasks by taskDao.observeTasks().collectAsState(initial = emptyList())
    val markdownTasks by remember(taskDao) {
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
    }.collectAsState(initial = emptyList())
    var editingTask by remember { mutableStateOf<TaskEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var quickTaskText by remember { mutableStateOf("") }
    var showCompletedTasks by remember { mutableStateOf(false) }
    var showTaskOptions by remember { mutableStateOf(false) }
    val visibleTasks = remember(tasks, showCompletedTasks) {
        if (showCompletedTasks) tasks else tasks.filterNot { it.done }
    }
    val taskSections = remember(visibleTasks, showCompletedTasks) {
        buildTaskSections(visibleTasks, showCompletedTasks)
    }
    val visibleMarkdownTasks = remember(markdownTasks, showCompletedTasks) {
        if (showCompletedTasks) markdownTasks else markdownTasks.filterNot { it.done }
    }

    fun saveTask(
        original: TaskEntity?,
        text: String,
        notePath: String?,
        done: Boolean,
        reminderAt: Long?,
    ) {
        scope.launch {
            val now = System.currentTimeMillis()
            val savedTask = withContext(Dispatchers.IO) {
                val task = if (original == null) {
                    val draft = TaskEntity(
                        taskText = text,
                        notePath = notePath,
                        done = done,
                        reminderAt = reminderAt,
                        createdAt = now,
                        updatedAt = now,
                    )
                    draft.copy(id = taskDao.insert(draft))
                } else {
                    original.copy(
                        taskText = text,
                        notePath = notePath,
                        done = done,
                        reminderAt = reminderAt,
                        updatedAt = now,
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
                Toast.makeText(context, "系统通知未开启，提醒可能无法弹出", Toast.LENGTH_SHORT).show()
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
            val updated = task.copy(done = done, updatedAt = System.currentTimeMillis())
            taskDao.update(updated)
            scheduler.schedule(updated)
        }
    }

    fun addQuickTask(reminderAt: Long? = null) {
        val title = quickTaskText.trim()
        if (title.isBlank()) return
        quickTaskText = ""
        saveTask(null, title, null, false, reminderAt)
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
        scheduler.showNotification(task)
        scheduler.showReminderAlert(task)
        Toast.makeText(context, "已触发立即提醒测试", Toast.LENGTH_SHORT).show()
    }

    fun runPopupTest() {
        val task = testReminderTask("测试弹窗")
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "test popup start id=${task.id}")
        scheduler.showReminderAlert(task)
        Toast.makeText(context, "已触发测试弹窗", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "已播放测试铃声", Toast.LENGTH_SHORT).show()
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "test sound failed", error)
            Toast.makeText(context, "测试铃声失败", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务") },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = "打开侧边栏")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showTaskOptions = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多选项")
                        }
                        DropdownMenu(
                            expanded = showTaskOptions,
                            onDismissRequest = { showTaskOptions = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (showCompletedTasks) "隐藏已完成任务" else "显示已完成任务") },
                                onClick = {
                                    showCompletedTasks = !showCompletedTasks
                                    showTaskOptions = false
                                },
                            )
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
                    IconButton(onClick = {
                        editingTask = null
                        showEditor = true
                    }) {
                        Icon(Icons.Outlined.Add, contentDescription = "新建任务")
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
            item {
                QuickAddTaskRow(
                    text = quickTaskText,
                    onTextChange = { quickTaskText = it },
                    onAdd = ::addQuickTask,
                )
            }
            item {
                SectionHeader(
                    "任务清单",
                    taskListSubtitle(tasks.size, visibleTasks.size, taskSections, showCompletedTasks),
                )
            }
            if (visibleTasks.isEmpty()) {
                item {
                    EmptyTaskState(showCompletedTasks = showCompletedTasks)
                }
            } else {
                taskSections.forEach { section ->
                    item(key = "section-${section.title}") {
                        TaskGroupHeader(section = section)
                    }
                    items(section.tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onToggleDone = { toggleDone(task, it) },
                            onEdit = {
                                editingTask = task
                                showEditor = true
                            },
                            onDelete = { deleteTask(task) },
                            onOpenNotePath = onOpenNotePath,
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()
                SectionHeader("笔记中的 Markdown 任务", markdownTaskSubtitle(markdownTasks.size, visibleMarkdownTasks.size, showCompletedTasks))
            }
            if (visibleMarkdownTasks.isEmpty()) {
                item {
                    Text(
                        text = "未识别到 - [ ] 或 - [x] 格式的任务清单。",
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

    if (showEditor) {
        TaskEditorDialog(
            task = editingTask,
            onDismiss = { showEditor = false },
            onSave = { text, notePath, done, reminderAt ->
                saveTask(editingTask, text, notePath, done, reminderAt)
                showEditor = false
            },
        )
    }
}

private fun taskListSubtitle(
    totalCount: Int,
    visibleCount: Int,
    sections: List<TaskSection>,
    showCompletedTasks: Boolean,
): String = when {
    totalCount == 0 -> "暂无任务，用上方输入框快速添加"
    showCompletedTasks -> "${totalCount} 个任务"
    sections.any { it.kind == TaskSectionKind.OVERDUE } -> "有 ${sections.first { it.kind == TaskSectionKind.OVERDUE }.tasks.size} 个任务已过提醒时间"
    visibleCount == totalCount -> "${visibleCount} 个未完成任务"
    visibleCount == 0 -> "已完成任务已隐藏"
    else -> "${visibleCount} 个未完成任务，已隐藏 ${totalCount - visibleCount} 个已完成"
}

private enum class TaskSectionKind {
    OVERDUE,
    TODAY,
    FUTURE,
    NO_REMINDER,
    COMPLETED,
}

private data class TaskSection(
    val kind: TaskSectionKind,
    val title: String,
    val subtitle: String,
    val tasks: List<TaskEntity>,
)

private fun buildTaskSections(
    tasks: List<TaskEntity>,
    showCompletedTasks: Boolean,
    now: Long = System.currentTimeMillis(),
): List<TaskSection> {
    val activeTasks = tasks.filterNot { it.done }
    val todayEnd = endOfTodayMillis(now)
    val sections = mutableListOf<TaskSection>()

    fun addSection(
        kind: TaskSectionKind,
        title: String,
        subtitle: String,
        sectionTasks: List<TaskEntity>,
    ) {
        if (sectionTasks.isNotEmpty()) {
            sections += TaskSection(kind, title, subtitle, sectionTasks)
        }
    }

    addSection(
        TaskSectionKind.OVERDUE,
        "已过提醒",
        "先处理这些，或重新设置时间",
        activeTasks
            .filter { reminder -> reminder.reminderAt?.let { it < now } == true }
            .sortedBy { it.reminderAt },
    )
    addSection(
        TaskSectionKind.TODAY,
        "今天",
        "今天到期的提醒",
        activeTasks
            .filter { task -> task.reminderAt?.let { it in now..todayEnd } == true }
            .sortedBy { it.reminderAt },
    )
    addSection(
        TaskSectionKind.FUTURE,
        "稍后",
        "未来几天或更远的提醒",
        activeTasks
            .filter { task -> task.reminderAt?.let { it > todayEnd } == true }
            .sortedBy { it.reminderAt },
    )
    addSection(
        TaskSectionKind.NO_REMINDER,
        "无提醒",
        "尚未设置提醒时间",
        activeTasks
            .filter { it.reminderAt == null }
            .sortedByDescending { it.updatedAt },
    )
    if (showCompletedTasks) {
        addSection(
            TaskSectionKind.COMPLETED,
            "已完成",
            "保留最近更新的完成项",
            tasks.filter { it.done }.sortedByDescending { it.updatedAt },
        )
    }

    return sections
}

private fun markdownTaskSubtitle(
    totalCount: Int,
    visibleCount: Int,
    showCompletedTasks: Boolean,
): String = if (showCompletedTasks || visibleCount == totalCount) {
    "${totalCount} 条"
} else {
    "${visibleCount} 条，已隐藏 ${totalCount - visibleCount} 条已完成"
}

@Composable
private fun PermissionHint() {
    val context = LocalContext.current
    val notificationsEnabled = TaskReminderScheduler.areNotificationsEnabled(context)
    val exactAlarmsEnabled = TaskReminderScheduler.canScheduleExactAlarms(context)
    val statusText = when {
        notificationsEnabled && exactAlarmsEnabled -> "通知已允许 · 精确提醒可用 · 到点会弹窗并播放铃声"
        notificationsEnabled -> "通知已允许 · 未开启精确提醒，系统可能延后触发"
        exactAlarmsEnabled -> "通知未允许 · 到点可能只有弹窗，通知栏不会显示"
        else -> "通知未允许 · 未开启精确提醒，建议先打开"
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
private fun TaskGroupHeader(section: TaskSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (section.kind == TaskSectionKind.OVERDUE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = "${section.tasks.size} 个 · ${section.subtitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyTaskState(showCompletedTasks: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (showCompletedTasks) {
                "还没有任务。输入一句待办，再选择一个快捷提醒就可以开始。"
            } else {
                "当前没有未完成任务。已完成的任务可以从右上角菜单重新显示。"
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
    onToggleDone: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenNotePath: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (task.reminderAt?.let { it < System.currentTimeMillis() } == true && !task.done) {
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
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑任务")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除任务")
            }
        }
    }
}

@Composable
private fun TaskMeta(
    task: TaskEntity,
    onOpenNotePath: (String) -> Unit,
) {
    val reminder = task.reminderAt?.let { reminderStatusText(it) }
    val notePath = task.notePath?.takeIf { it.isNotBlank() }
    val text = listOfNotNull(notePath, reminder).joinToString(" · ").ifBlank { "无提醒" }
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
private fun QuickAddTaskRow(
    text: String,
    onTextChange: (String) -> Unit,
    onAdd: (Long?) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("快速添加任务") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    enabled = text.isNotBlank(),
                    onClick = { onAdd(null) },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "添加任务")
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickReminderButton(
                    label = "10秒后",
                    enabled = text.isNotBlank(),
                    onClick = { onAdd(System.currentTimeMillis() + 10_000L) },
                )
                QuickReminderButton(
                    label = "1小时后",
                    enabled = text.isNotBlank(),
                    onClick = { onAdd(System.currentTimeMillis() + 60L * 60_000L) },
                )
                QuickReminderButton(
                    label = "20:00",
                    enabled = text.isNotBlank(),
                    onClick = { onAdd(nextTimeMillis(20, 0)) },
                )
                QuickReminderButton(
                    label = "明早",
                    enabled = text.isNotBlank(),
                    onClick = { onAdd(nextMorningMillis()) },
                )
                QuickReminderButton(
                    label = "明晚",
                    enabled = text.isNotBlank(),
                    onClick = { onAdd(nextTimeMillis(20, 0, dayOffset = 1)) },
                )
            }
        }
    }
}

@Composable
private fun QuickReminderButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.74f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Alarm,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
private fun TaskEditorDialog(
    task: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Boolean, Long?) -> Unit,
) {
    var text by remember(task?.id) { mutableStateOf(task?.taskText.orEmpty()) }
    var done by remember(task?.id) { mutableStateOf(task?.done ?: false) }
    var reminderAt by remember(task?.id) { mutableStateOf(task?.reminderAt) }
    var error by remember(task?.id) { mutableStateOf<String?>(null) }
    var showReminderPicker by remember(task?.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "添加任务" else "编辑任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    label = { Text("任务标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showReminderPicker = true },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Notifications, contentDescription = null)
                        Text(
                            text = reminderAt?.let(::formatReminderTime) ?: "无提醒",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        )
                        if (reminderAt != null) {
                            TextButton(onClick = {
                                reminderAt = null
                                error = null
                            }) {
                                Text("清除")
                            }
                        }
                        TextButton(onClick = { showReminderPicker = true }) { Text("选择") }
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
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedText = text.trim()
                    if (trimmedText.isBlank()) {
                        error = "任务标题不能为空"
                        return@TextButton
                    }
                    val selectedReminderAt = reminderAt
                    if (selectedReminderAt != null && selectedReminderAt <= System.currentTimeMillis() + 1000L) {
                        error = "提醒时间需要晚于当前时间"
                        return@TextButton
                    }
                    onSave(
                        trimmedText,
                        task?.notePath,
                        done,
                        selectedReminderAt,
                    )
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )

    if (showReminderPicker) {
        TaskReminderPickerDialog(
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

@Composable
private fun TaskReminderPickerDialog(
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
                TaskReminderTabs(onClear = onClear)
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
private fun TaskReminderTabs(onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClear) {
            Text("清除", color = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = "开始",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "结束",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
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
                title = "提醒",
                value = reminderText,
                onClick = onReminderClick,
            )
            TaskReminderOptionRow(
                icon = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
                title = "重复",
                value = "无",
                onClick = {},
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
