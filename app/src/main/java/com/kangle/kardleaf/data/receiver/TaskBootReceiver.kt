package com.kangle.kardleaf.data.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kangle.kardleaf.data.task.TaskReminderScheduler
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"

class TaskBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val exactAlarmPermissionChanged =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && !exactAlarmPermissionChanged) {
            KardLeafLog.d(TASK_REMINDER_LOG_TAG, "boot receiver skip action=${intent.action}")
            return
        }

        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "boot receiver reschedule action=${intent.action}")
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                TaskReminderScheduler(context).rescheduleAll()
            } catch (error: Throwable) {
                KardLeafLog.e(TASK_REMINDER_LOG_TAG, "boot receiver reschedule failed", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
