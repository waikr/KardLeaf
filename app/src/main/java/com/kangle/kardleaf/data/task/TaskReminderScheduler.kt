package com.kangle.kardleaf.data.task

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kangle.kardleaf.BuildConfig
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.receiver.TaskReminderReceiver
import com.kangle.kardleaf.data.utils.KardLeafLog

private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"
private const val TASK_ALARM_LOG_TAG = "KardLeafAlarmTrace"
private const val TASK_NOTIFICATION_LOG_TAG = "KardLeafNotificationTrace"

private fun reminderTrace(tag: String, message: String) {
    if (BuildConfig.DEBUG) KardLeafLog.i(tag, message)
}

class TaskReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun schedule(task: TaskEntity) {
        cancel(task.id)
        val triggerAt = task.reminderAt
        if (triggerAt == null) {
            KardLeafLog.d(TASK_REMINDER_LOG_TAG, "schedule skip id=${task.id} reason=noReminder")
            return
        }
        val now = System.currentTimeMillis()
        if (task.done || triggerAt <= now) {
            KardLeafLog.w(
                TASK_REMINDER_LOG_TAG,
                "schedule skip id=${task.id} reason=${if (task.done) "done" else "expired"} delayMs=${triggerAt - now}",
            )
            return
        }

        createNotificationChannel(appContext)
        val pendingIntent = reminderPendingIntent(task.id)
        val notificationsAllowed = areNotificationsEnabled(appContext)
        val exactAllowed = canScheduleExactAlarms(appContext)
        reminderTrace(
            TASK_ALARM_LOG_TAG,
            "schedule id=${task.id} now=$now triggerAt=$triggerAt alarmType=RTC_WAKEUP " +
                "requestCode=${task.id.hashCode()} flags=$REMINDER_PENDING_INTENT_FLAGS " +
                "notificationsAllowed=$notificationsAllowed exactAllowed=$exactAllowed",
        )
        KardLeafLog.i(
            TASK_REMINDER_LOG_TAG,
            "schedule request id=${task.id} triggerAt=$triggerAt delayMs=${triggerAt - now} notificationsAllowed=$notificationsAllowed exactAllowed=$exactAllowed",
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                KardLeafLog.w(TASK_REMINDER_LOG_TAG, "schedule mode=inexact id=${task.id}")
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                KardLeafLog.i(TASK_REMINDER_LOG_TAG, "schedule mode=exactIdle id=${task.id}")
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                KardLeafLog.i(TASK_REMINDER_LOG_TAG, "schedule mode=exact id=${task.id}")
            }
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "schedule failed id=${task.id}", error)
        }
    }

    fun cancel(taskId: Long) {
        alarmManager.cancel(reminderPendingIntent(taskId))
        NotificationManagerCompat.from(appContext).cancel(taskId.hashCode())
        KardLeafLog.d(TASK_REMINDER_LOG_TAG, "cancel id=$taskId")
    }

    suspend fun rescheduleAll() {
        val tasks = AppDatabase.getDatabase(appContext)
            .taskDao()
            .getPendingReminders(System.currentTimeMillis())
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "rescheduleAll count=${tasks.size}")
        tasks.forEach(::schedule)
    }

    /**
     * 提醒到期的统一入口：优先启动前台服务（后台/被杀后仍可响铃、震动、弹窗），
     * 启动失败时回退为直接发通知（由渠道发声，与旧行为一致）。
     */
    fun deliverReminder(task: TaskEntity) {
        createNotificationChannel(appContext)
        runCatching {
            ContextCompat.startForegroundService(appContext, TaskReminderService.startIntent(appContext, task))
        }.onSuccess {
            KardLeafLog.i(TASK_REMINDER_LOG_TAG, "deliver via service id=${task.id} mode=${task.reminderMode}")
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "deliver service failed id=${task.id}, fallback to notify", error)
            showNotification(task)
        }
    }

    fun showNotification(task: TaskEntity) {
        if (!areNotificationsEnabled(appContext)) {
            KardLeafLog.w(TASK_REMINDER_LOG_TAG, "notify skip id=${task.id} reason=notificationsDisabled")
            return
        }

        createNotificationChannel(appContext)
        logNotificationChannelState(appContext, "notify beforePost")
        val popup = task.reminderMode != TaskEntity.REMINDER_MODE_NOTIFICATION
        val channelId = if (task.reminderRing) CHANNEL_ID else SILENT_CHANNEL_ID
        val notification = buildReminderNotification(
            taskId = task.id,
            taskText = task.taskText,
            popup = popup,
            ring = task.reminderRing,
            vibrate = task.reminderVibrate,
            channelId = channelId,
        )

        runCatching {
            val managerCompat = NotificationManagerCompat.from(appContext)
            managerCompat.notify(task.id.hashCode(), notification)
            val active = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appContext.getSystemService(NotificationManager::class.java)
                    .activeNotifications
                    .any { it.id == task.id.hashCode() }
            } else {
                null
            }
            KardLeafLog.i(TASK_REMINDER_LOG_TAG, "notify posted id=${task.id} active=$active channel=$channelId")
            reminderTrace(
                TASK_NOTIFICATION_LOG_TAG,
                "posted id=${task.id} notificationId=${task.id.hashCode()} active=$active " +
                    "notificationsAllowed=${areNotificationsEnabled(appContext)}",
            )
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "notify failed id=${task.id}", error)
        }
    }

    fun buildReminderNotification(
        taskId: Long,
        taskText: String,
        popup: Boolean,
        ring: Boolean,
        vibrate: Boolean,
        channelId: String,
    ): Notification {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: Intent()
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            taskId.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissIntent = dismissPendingIntent(taskId)
        reminderTrace(
            TASK_NOTIFICATION_LOG_TAG,
            "build id=$taskId notificationId=${taskId.hashCode()} channel=$channelId " +
                "popup=$popup ring=$ring vibrate=$vibrate " +
                "fullScreenAllowed=${canUseFullScreenIntent(appContext)} " +
                "appState=${appProcessState(appContext)}",
        )
        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_shortcut_todo)
            .setContentTitle("任务提醒")
            .setContentText(taskText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskText))
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissIntent)
            .addAction(0, "知道了", dismissIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
        // O 以下声音/震动取自 builder；O 及以上由渠道或前台服务负责
        if (ring) builder.setSound(reminderSoundUri())
        if (vibrate) builder.setVibrate(REMINDER_VIBRATION_PATTERN)
        if (popup) {
            builder.setFullScreenIntent(reminderAlertPendingIntent(taskId, taskText), true)
        }
        return builder.build()
    }

    fun showReminderAlert(task: TaskEntity) {
        runCatching {
            appContext.startActivity(reminderAlertIntent(appContext, task.id, task.taskText))
            KardLeafLog.i(TASK_REMINDER_LOG_TAG, "alert startActivity requested id=${task.id}")
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "alert startActivity failed id=${task.id}", error)
        }
    }

    private fun reminderPendingIntent(taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            taskId.hashCode(),
            Intent(appContext, TaskReminderReceiver::class.java)
                .setAction(ACTION_TASK_REMINDER)
                .putExtra(EXTRA_TASK_ID, taskId),
            REMINDER_PENDING_INTENT_FLAGS,
        )

    private fun dismissPendingIntent(taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            taskId.hashCode() xor DISMISS_REQUEST_CODE_MASK,
            Intent(appContext, TaskReminderReceiver::class.java)
                .setAction(ACTION_TASK_REMINDER_DISMISS)
                .putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderAlertPendingIntent(taskId: Long, taskText: String): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            taskId.hashCode() xor ALERT_REQUEST_CODE_MASK,
            reminderAlertIntent(appContext, taskId, taskText),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        const val ACTION_TASK_REMINDER = "com.kangle.kardleaf.action.TASK_REMINDER"
        const val ACTION_TASK_REMINDER_DISMISS = "com.kangle.kardleaf.action.TASK_REMINDER_DISMISS"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TEXT = "task_text"
        const val CHANNEL_ID = "task_reminders_v3"
        const val SILENT_CHANNEL_ID = "task_reminders_silent_v1"
        private const val ALERT_REQUEST_CODE_MASK = 0x51F5
        private const val DISMISS_REQUEST_CODE_MASK = 0x2A9C
        private const val REMINDER_PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private val REMINDER_VIBRATION_PATTERN = longArrayOf(0L, 180L, 80L, 180L)

        fun reminderAlertIntent(context: Context, taskId: Long, taskText: String): Intent =
            Intent(context, TaskReminderAlertActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TASK_TEXT, taskText)

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

        fun areNotificationsEnabled(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        fun canScheduleExactAlarms(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        fun canUseFullScreenIntent(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

        fun hasAudibleReminderChannel(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID) ?: return true
            return channel.importance >= NotificationManager.IMPORTANCE_HIGH && channel.sound != null
        }

        fun appProcessState(context: Context): String {
            val manager = context.getSystemService(ActivityManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return if (manager.appTasks.any { it.taskInfo.isVisible }) "FOREGROUND" else "BACKGROUND"
            }
            val importance = manager.runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }
                ?.importance
                ?: return "UNKNOWN"
            return when {
                importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "FOREGROUND"
                importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "VISIBLE"
                else -> "BACKGROUND"
            }
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val defaultSoundUri = reminderSoundUri()
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "任务提醒",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "任务到期提醒"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                    vibrationPattern = REMINDER_VIBRATION_PATTERN
                    setSound(defaultSoundUri, audioAttributes)
                }
                manager.createNotificationChannel(channel)
                logNotificationChannelState(context, "channel created")
            } else {
                logNotificationChannelState(context, "channel existing")
            }
            if (manager.getNotificationChannel(SILENT_CHANNEL_ID) == null) {
                // 静音高优渠道：横幅与全屏意图由它承载，铃声/震动由前台服务按任务选项播放
                val silentChannel = NotificationChannel(
                    SILENT_CHANNEL_ID,
                    "任务提醒（应用内响铃）",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "任务到期横幅；铃声与震动由应用按任务设置播放"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(false)
                    setSound(null, null)
                }
                manager.createNotificationChannel(silentChannel)
            }
        }

        private fun reminderSoundUri() =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        private fun logNotificationChannelState(context: Context, reason: String) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)
            KardLeafLog.i(
                TASK_REMINDER_LOG_TAG,
                "$reason id=$CHANNEL_ID importance=${channel?.importance} sound=${channel?.sound != null} vibration=${channel?.shouldVibrate()}",
            )
            reminderTrace(
                TASK_NOTIFICATION_LOG_TAG,
                "$reason channel=$CHANNEL_ID importance=${channel?.importance} soundUri=${channel?.sound} " +
                    "vibration=${channel?.shouldVibrate()} fullScreenAllowed=${canUseFullScreenIntent(context)}",
            )
        }
    }
}
