package com.kangle.kardleaf.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.kangle.kardleaf.BuildConfig
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.task.TaskReminderScheduler
import com.kangle.kardleaf.data.task.TaskReminderService
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"
private const val TASK_ALARM_LOG_TAG = "KardLeafAlarmTrace"

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TaskReminderScheduler.ACTION_TASK_REMINDER_DISMISS) {
            handleDismiss(context, intent)
            return
        }
        if (intent.action != TaskReminderScheduler.ACTION_TASK_REMINDER) {
            KardLeafLog.d(TASK_REMINDER_LOG_TAG, "receiver skip action=${intent.action}")
            return
        }
        val taskId = intent.getLongExtra(TaskReminderScheduler.EXTRA_TASK_ID, 0L)
        if (BuildConfig.DEBUG) {
            KardLeafLog.i(
                TASK_ALARM_LOG_TAG,
                "receiver action=${intent.action} id=$taskId receivedAt=${System.currentTimeMillis()} " +
                    "appState=${TaskReminderScheduler.appProcessState(context)}",
            )
        }
        if (taskId <= 0L) {
            KardLeafLog.w(TASK_REMINDER_LOG_TAG, "receiver skip invalidTaskId=$taskId")
            return
        }

        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "receiver fired id=$taskId")
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val task = AppDatabase.getDatabase(context).taskDao().getTask(taskId)
                if (
                    task != null &&
                    !task.done &&
                    task.reminderAt != null &&
                    task.reminderAt <= now
                ) {
                    KardLeafLog.i(TASK_REMINDER_LOG_TAG, "receiver due id=$taskId delayMs=${now - task.reminderAt}")
                    TaskReminderScheduler(context).deliverReminder(task)
                } else {
                    KardLeafLog.w(
                        TASK_REMINDER_LOG_TAG,
                        "receiver skip id=$taskId reason=${receiverSkipReason(task)} reminderAt=${task?.reminderAt} now=$now",
                    )
                }
            } catch (error: Throwable) {
                KardLeafLog.e(TASK_REMINDER_LOG_TAG, "receiver failed id=$taskId", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(TaskReminderScheduler.EXTRA_TASK_ID, 0L)
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "receiver dismiss id=$taskId")
        runCatching { context.stopService(Intent(context, TaskReminderService::class.java)) }
        if (taskId != 0L) {
            NotificationManagerCompat.from(context).cancel(taskId.hashCode())
        }
    }

    private fun receiverSkipReason(task: com.kangle.kardleaf.data.database.TaskEntity?): String = when {
        task == null -> "missingTask"
        task.done -> "done"
        task.reminderAt == null -> "noReminder"
        else -> "notDue"
    }
}
