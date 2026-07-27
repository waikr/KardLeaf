package com.kangle.kardleaf.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews
import com.kangle.kardleaf.MainActivity
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.task.TaskQuickAddActivity
import com.kangle.kardleaf.data.task.TaskReminderScheduler
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TaskListWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                val appContext = context.applicationContext
                appWidgetIds.forEach { appWidgetId -> updateWidget(appContext, appWidgetManager, appWidgetId) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val total = AppDatabase.getDatabase(context).taskDao().countWidgetOpenTasks()
        val views = createRemoteViews(context, appWidgetId, total)
        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.task_widget_list)
    }

    private fun createRemoteViews(
        context: Context,
        appWidgetId: Int,
        total: Int,
    ): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_task_list).apply {
            setRemoteAdapter(
                R.id.task_widget_list,
                Intent(context, TaskListWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = Uri.parse("kardleaf://task-widget/$appWidgetId")
                },
            )
            setEmptyView(R.id.task_widget_list, R.id.task_widget_empty)
            setViewVisibility(R.id.task_widget_empty, if (total == 0) View.VISIBLE else View.GONE)

            val openTasksIntent = openTasksPendingIntent(context, appWidgetId)
            setOnClickPendingIntent(R.id.task_widget_title_control, openTasksIntent)
            setOnClickPendingIntent(R.id.task_widget_open, openTasksIntent)
            setOnClickPendingIntent(R.id.task_widget_add, newTaskPendingIntent(context, appWidgetId))
            setPendingIntentTemplate(
                R.id.task_widget_list,
                PendingIntent.getActivity(
                    context,
                    REQUEST_TASK_ACTION + appWidgetId,
                    Intent(context, TaskQuickAddActivity::class.java).apply {
                        data = Uri.parse("kardleaf://task-widget/action/$appWidgetId")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
            )
        }

    private fun openTasksPendingIntent(
        context: Context,
        appWidgetId: Int,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("kardleaf://tasks")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_TASKS + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun newTaskPendingIntent(
        context: Context,
        appWidgetId: Int,
    ): PendingIntent {
        val intent =
            Intent(context, TaskQuickAddActivity::class.java).apply {
                action = ACTION_QUICK_ADD_TASK
                data = Uri.parse("kardleaf://task-widget/new/$appWidgetId")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
        return PendingIntent.getActivity(
            context,
            REQUEST_NEW_TASK + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        internal const val MAX_TASKS = 100
        private const val ACTION_QUICK_ADD_TASK = "com.kangle.kardleaf.action.QUICK_ADD_TASK_WIDGET"
        private const val REQUEST_NEW_TASK = 28_000
        private const val REQUEST_TASK_ACTION = 29_000
        private const val REQUEST_OPEN_TASKS = 30_000
        private const val TASK_WIDGET_LOG_TAG = "KardLeafTaskWidget"
        private val COMPLETE_VIBRATION_PATTERN = longArrayOf(0L, 55L, 35L, 55L)
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal suspend fun completeTaskFromWidget(context: Context, taskId: Long): Boolean {
            if (taskId <= 0L) {
                KardLeafLog.w(TASK_WIDGET_LOG_TAG, "complete ignored invalid taskId=$taskId")
                return false
            }
            val appContext = context.applicationContext
            val taskDao = AppDatabase.getDatabase(appContext).taskDao()
            val task = taskDao.getTask(taskId)
            if (task == null) {
                KardLeafLog.w(TASK_WIDGET_LOG_TAG, "complete failed missing taskId=$taskId")
                return false
            }
            val updated = task.copy(done = true, updatedAt = System.currentTimeMillis())
            taskDao.update(updated)
            TaskReminderScheduler(appContext).schedule(updated)
            performCompleteFeedback(appContext)
            refreshWidgets(appContext)
            KardLeafLog.i(TASK_WIDGET_LOG_TAG, "complete success taskId=$taskId")
            return true
        }

        internal fun compactTaskText(text: String): String =
            text
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "未命名任务" }

        private fun performCompleteFeedback(context: Context) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) {
                KardLeafLog.w(TASK_WIDGET_LOG_TAG, "complete feedback unavailable")
                return
            }

            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(COMPLETE_VIBRATION_PATTERN, -1)
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    vibrator.vibrate(effect, attributes)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(COMPLETE_VIBRATION_PATTERN, -1)
                }
            }.onSuccess {
                KardLeafLog.i(TASK_WIDGET_LOG_TAG, "complete feedback requested")
            }.onFailure { error ->
                KardLeafLog.w(TASK_WIDGET_LOG_TAG, "complete feedback failed", error)
            }
        }

        private suspend fun refreshWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(ComponentName(context, TaskListWidgetProvider::class.java))
            widgetIds.forEach { widgetId ->
                TaskListWidgetProvider().updateWidget(context, manager, widgetId)
            }
        }

        fun refreshAllWidgets(context: Context) {
            val appContext = context.applicationContext
            widgetScope.launch {
                refreshWidgets(appContext)
            }
        }
    }
}
