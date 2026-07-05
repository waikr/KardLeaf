package com.kangle.kardleaf.data.task

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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.receiver.TaskReminderReceiver
import com.kangle.kardleaf.data.utils.KardLeafLog

private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"

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

    fun showNotification(task: TaskEntity) {
        if (!areNotificationsEnabled(appContext)) {
            KardLeafLog.w(TASK_REMINDER_LOG_TAG, "notify skip id=${task.id} reason=notificationsDisabled")
            return
        }

        createNotificationChannel(appContext)
        logNotificationChannelState(appContext, "notify beforePost")
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: Intent()
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            appContext,
            task.id.hashCode(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val defaultSoundUri = reminderSoundUri()
        val fullScreenIntent = reminderAlertPendingIntent(task)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_todo)
            .setContentTitle("任务提醒")
            .setContentText(task.taskText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.taskText))
            .setContentIntent(contentIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setDefaults(Notification.DEFAULT_ALL)
            .setSound(defaultSoundUri)
            .setVibrate(longArrayOf(0L, 180L, 80L, 180L))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()

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
            KardLeafLog.i(TASK_REMINDER_LOG_TAG, "notify posted id=${task.id} active=$active channel=$CHANNEL_ID")
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "notify failed id=${task.id}", error)
        }
    }

    fun showReminderAlert(task: TaskEntity) {
        runCatching {
            appContext.startActivity(reminderAlertIntent(task))
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
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderAlertPendingIntent(task: TaskEntity): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            task.id.hashCode() xor ALERT_REQUEST_CODE_MASK,
            reminderAlertIntent(task),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun reminderAlertIntent(task: TaskEntity): Intent =
        Intent(appContext, TaskReminderAlertActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            .putExtra(EXTRA_TASK_ID, task.id)
            .putExtra(EXTRA_TASK_TEXT, task.taskText)

    companion object {
        const val ACTION_TASK_REMINDER = "com.kangle.kardleaf.action.TASK_REMINDER"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TEXT = "task_text"
        private const val CHANNEL_ID = "task_reminders_v3"
        private const val ALERT_REQUEST_CODE_MASK = 0x51F5
        private val REMINDER_VIBRATION_PATTERN = longArrayOf(0L, 180L, 80L, 180L)

        fun areNotificationsEnabled(context: Context): Boolean =
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        fun canScheduleExactAlarms(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java)
            val existingChannel = manager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel != null) {
                logNotificationChannelState(context, "channel existing")
                return
            }
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
        }
    }
}
