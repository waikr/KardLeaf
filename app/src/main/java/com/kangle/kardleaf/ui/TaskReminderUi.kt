package com.kangle.kardleaf.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.*
import com.kangle.kardleaf.data.task.TaskReminderScheduler
import com.kangle.kardleaf.data.task.TaskRepeat
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.TimeZone

private const val PERMISSION_HINT_SUCCESS_DURATION_MS = 3_000L

internal enum class TaskDateTarget {
    START,
    END,
}

enum class NoteTimeField {
    CREATED,
    UPDATED,
}

internal data class PermissionHintState(
    val notificationsEnabled: Boolean,
    val exactAlarmsEnabled: Boolean,
    val fullScreenEnabled: Boolean,
    val overlayEnabled: Boolean,
    val channelAudible: Boolean,
    val allPermissionsEnabled: Boolean,
    val showSuccessHint: Boolean,
) {
    val visible: Boolean get() = !allPermissionsEnabled || showSuccessHint
}

@Composable
internal fun rememberPermissionHintState(context: Context): PermissionHintState {
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    var permissionRefreshVersion by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        permissionRefreshVersion++
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val notificationsEnabled =
        remember(permissionRefreshVersion) {
            TaskReminderScheduler.areNotificationsEnabled(context)
        }
    val exactAlarmsEnabled =
        remember(permissionRefreshVersion) {
            TaskReminderScheduler.canScheduleExactAlarms(context)
        }
    val fullScreenEnabled =
        remember(permissionRefreshVersion) {
            TaskReminderScheduler.canUseFullScreenIntent(context)
        }
    val overlayEnabled =
        remember(permissionRefreshVersion) {
            TaskReminderScheduler.canDrawOverlays(context)
        }
    val channelAudible =
        remember(permissionRefreshVersion) {
            TaskReminderScheduler.hasAudibleReminderChannel(context)
        }
    val allPermissionsEnabled =
        notificationsEnabled && exactAlarmsEnabled &&
            fullScreenEnabled && overlayEnabled && channelAudible
    var wasAllPermissionsEnabled by remember { mutableStateOf(allPermissionsEnabled) }
    var showSuccessHint by remember { mutableStateOf(false) }

    LaunchedEffect(allPermissionsEnabled) {
        if (allPermissionsEnabled && !wasAllPermissionsEnabled) {
            wasAllPermissionsEnabled = true
            showSuccessHint = true
            delay(PERMISSION_HINT_SUCCESS_DURATION_MS)
            showSuccessHint = false
        } else if (!allPermissionsEnabled) {
            wasAllPermissionsEnabled = false
            showSuccessHint = false
        }
    }

    return PermissionHintState(
        notificationsEnabled = notificationsEnabled,
        exactAlarmsEnabled = exactAlarmsEnabled,
        fullScreenEnabled = fullScreenEnabled,
        overlayEnabled = overlayEnabled,
        channelAudible = channelAudible,
        allPermissionsEnabled = allPermissionsEnabled,
        showSuccessHint = showSuccessHint,
    )
}

@Composable
internal fun PermissionHint(
    state: PermissionHintState,
    context: Context,
) {
    val notificationsEnabled = state.notificationsEnabled
    val exactAlarmsEnabled = state.exactAlarmsEnabled
    val fullScreenEnabled = state.fullScreenEnabled
    val overlayEnabled = state.overlayEnabled
    val channelAudible = state.channelAudible

    val statusText =
        when {
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
                modifier =
                    Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskReminderPickerDialog(
    initialReminderAt: Long?,
    initialEndAt: Long?,
    initialRepeatRule: TaskRepeat,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onDateRangeSelected: (Long?, Long?, TaskRepeat) -> Unit,
) {
    val initialTime = initialReminderAt ?: initialEndAt ?: System.currentTimeMillis() + 60 * 60 * 1000L
    var startAt by remember(initialReminderAt, initialEndAt) { mutableStateOf(initialReminderAt) }
    var endAt by remember(initialReminderAt, initialEndAt) { mutableStateOf(initialEndAt) }
    val initialTarget =
        if (initialReminderAt != null || initialEndAt == null) {
            TaskDateTarget.START
        } else {
            TaskDateTarget.END
        }
    var activeTarget by remember(initialReminderAt, initialEndAt) { mutableStateOf(initialTarget) }
    var selectedDateUtcMillis by remember(initialReminderAt, initialEndAt) {
        mutableStateOf(utcDateMillis(initialTime))
    }
    var selectedHour by remember(initialReminderAt, initialEndAt) { mutableStateOf(hourOfDay(initialTime)) }
    var selectedMinute by remember(initialReminderAt, initialEndAt) { mutableStateOf(minuteOfHour(initialTime)) }
    var timeSelected by remember(initialReminderAt, initialEndAt) {
        mutableStateOf(
            when (initialTarget) {
                TaskDateTarget.START -> initialReminderAt?.let { hasDisplayedTime(it, 9, 0) } ?: false
                TaskDateTarget.END -> initialEndAt?.let { hasDisplayedTime(it, 23, 59) } ?: false
            },
        )
    }
    var quickReminderAt by remember(initialReminderAt, initialEndAt) { mutableStateOf<Long?>(null) }
    var quickReminderLabel by remember(initialReminderAt, initialEndAt) { mutableStateOf<String?>(null) }
    var repeatRule by remember(initialReminderAt, initialEndAt) { mutableStateOf(initialRepeatRule) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showQuickReminderPicker by remember { mutableStateOf(false) }

    fun loadTarget(target: TaskDateTarget) {
        activeTarget = target
        val targetAt =
            when (target) {
                TaskDateTarget.START -> startAt
                TaskDateTarget.END -> endAt
            } ?: (startAt ?: endAt ?: System.currentTimeMillis() + 60 * 60 * 1000L)
        selectedDateUtcMillis = utcDateMillis(targetAt)
        selectedHour = hourOfDay(targetAt)
        selectedMinute = minuteOfHour(targetAt)
        timeSelected =
            when (target) {
                TaskDateTarget.START -> startAt?.let { hasDisplayedTime(it, 9, 0) } ?: false
                TaskDateTarget.END -> endAt?.let { hasDisplayedTime(it, 23, 59) } ?: false
            }
        quickReminderAt = null
        quickReminderLabel = null
    }

    fun setActiveTargetAt(targetAt: Long) {
        when (activeTarget) {
            TaskDateTarget.START -> startAt = targetAt
            TaskDateTarget.END -> endAt = targetAt
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TaskReminderTabs(
                    activeTarget = activeTarget,
                    onTargetChange = ::loadTarget,
                    onClear = onClear,
                )
                key(activeTarget) {
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateUtcMillis)
                    DatePicker(
                        state = datePickerState,
                        title = null,
                        headline = null,
                        showModeToggle = false,
                    )
                    LaunchedEffect(selectedDateUtcMillis) {
                        if (datePickerState.selectedDateMillis != selectedDateUtcMillis) {
                            datePickerState.selectedDateMillis = selectedDateUtcMillis
                        }
                    }
                    LaunchedEffect(datePickerState.selectedDateMillis) {
                        datePickerState.selectedDateMillis?.let {
                            if (it != selectedDateUtcMillis) {
                                selectedDateUtcMillis = it
                                quickReminderAt = null
                                quickReminderLabel = null
                            }
                        }
                    }
                }
                TaskReminderOptions(
                    timeText = if (timeSelected) formatClockTime(selectedHour, selectedMinute) else "无",
                    reminderText = quickReminderLabel ?: "无",
                    repeatText = repeatRule.label,
                    onTimeClick = { showTimePicker = true },
                    onClearTime = {
                        timeSelected = false
                        quickReminderAt = null
                        quickReminderLabel = null
                    },
                    onReminderClick = { showQuickReminderPicker = true },
                    onRepeatSelected = { repeatRule = it },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            val selectedAt =
                                quickReminderAt ?: combineDateAndTime(
                                    selectedDateUtcMillis,
                                    if (timeSelected) {
                                        selectedHour
                                    } else if (activeTarget == TaskDateTarget.START) {
                                        9
                                    } else {
                                        23
                                    },
                                    if (timeSelected) {
                                        selectedMinute
                                    } else if (activeTarget == TaskDateTarget.START) {
                                        0
                                    } else {
                                        59
                                    },
                                )
                            selectedAt.let(::setActiveTargetAt)
                            onDateRangeSelected(startAt, endAt, repeatRule)
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
                selectedHour = hourOfDay(targetAt)
                selectedMinute = minuteOfHour(targetAt)
                timeSelected = true
                showQuickReminderPicker = false
            },
        )
    }
}

@Composable
private fun TaskReminderTabs(
    activeTarget: TaskDateTarget,
    onTargetChange: (TaskDateTarget) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClear) {
            Text("清除", color = MaterialTheme.colorScheme.primary)
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
        ) {
            TextButton(onClick = { onTargetChange(TaskDateTarget.START) }) {
                Text(
                    text = "开始",
                    color =
                        if (activeTarget == TaskDateTarget.START) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            TextButton(onClick = { onTargetChange(TaskDateTarget.END) }) {
                Text(
                    text = "结束",
                    color =
                        if (activeTarget == TaskDateTarget.END) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun TaskReminderOptions(
    timeText: String,
    reminderText: String,
    repeatText: String,
    onTimeClick: () -> Unit,
    onClearTime: () -> Unit,
    onReminderClick: () -> Unit,
    onRepeatSelected: (TaskRepeat) -> Unit,
) {
    var showRepeatMenu by remember { mutableStateOf(false) }
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
            if (timeText != "无") {
                TextButton(onClick = onClearTime) {
                    Text("不设置时间")
                }
            }
            TaskReminderOptionRow(
                icon = { Icon(Icons.Outlined.Alarm, contentDescription = null) },
                title = "提醒",
                value = reminderText,
                onClick = onReminderClick,
            )
            Box {
                TaskReminderOptionRow(
                    icon = { Icon(Icons.Outlined.Repeat, contentDescription = null) },
                    title = "重复",
                    value = repeatText,
                    onClick = { showRepeatMenu = true },
                )
                DropdownMenu(
                    expanded = showRepeatMenu,
                    onDismissRequest = { showRepeatMenu = false },
                ) {
                    TaskRepeat.entries.forEach { repeat ->
                        DropdownMenuItem(
                            text = { Text(repeat.label) },
                            onClick = {
                                onRepeatSelected(repeat)
                                showRepeatMenu = false
                            },
                        )
                    }
                }
            }
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
        modifier =
            Modifier
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
            modifier =
                Modifier
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

private val quickReminderOptions =
    listOf(
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
        modifier =
            Modifier
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
    val state =
        rememberTimePickerState(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteTimestampPickerDialog(
    initialTimestamp: Long,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var selectedDateUtcMillis by remember(initialTimestamp) { mutableStateOf(utcDateMillis(initialTimestamp)) }
    var selectedHour by remember(initialTimestamp) { mutableStateOf(hourOfDay(initialTimestamp)) }
    var selectedMinute by remember(initialTimestamp) { mutableStateOf(minuteOfHour(initialTimestamp)) }
    var showTimePicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                key(selectedDateUtcMillis) {
                    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateUtcMillis)
                    DatePicker(
                        state = datePickerState,
                        title = null,
                        headline = null,
                        showModeToggle = false,
                    )
                    LaunchedEffect(selectedDateUtcMillis) {
                        if (datePickerState.selectedDateMillis != selectedDateUtcMillis) {
                            datePickerState.selectedDateMillis = selectedDateUtcMillis
                        }
                    }
                    LaunchedEffect(datePickerState.selectedDateMillis) {
                        datePickerState.selectedDateMillis?.let { selectedDateUtcMillis = it }
                    }
                }
                TaskReminderOptionRow(
                    icon = { Icon(Icons.Outlined.AccessTime, contentDescription = null) },
                    title = "时间",
                    value = formatClockTime(selectedHour, selectedMinute),
                    onClick = { showTimePicker = true },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = {
                            onConfirm(combineDateAndTime(selectedDateUtcMillis, selectedHour, selectedMinute))
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
                showTimePicker = false
            },
        )
    }
}

private fun formatClockTime(
    hour: Int,
    minute: Int,
): String =
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
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    val intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
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
