package com.kangle.kardleaf.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.task.TaskQuickAddActivity
import com.kangle.kardleaf.data.task.TaskReminderScheduler
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
        appWidgetIds.forEach { appWidgetId -> updateWidgetAsync(context, appWidgetManager, appWidgetId) }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TOGGLE_TASK) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId <= 0L) return
        widgetScope.launch {
            val appContext = context.applicationContext
            val taskDao = AppDatabase.getDatabase(appContext).taskDao()
            val task = taskDao.getTask(taskId) ?: return@launch
            val updated = task.copy(done = true, updatedAt = System.currentTimeMillis())
            taskDao.update(updated)
            TaskReminderScheduler(appContext).schedule(updated)
            performCompleteFeedback(appContext)
            refreshAllWidgets(appContext)
        }
    }

    private fun updateWidgetAsync(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        widgetScope.launch {
            val appContext = context.applicationContext
            val total = AppDatabase.getDatabase(appContext).taskDao().countWidgetOpenTasks()
            val views = createRemoteViews(appContext, appWidgetId, total)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.task_widget_list)
        }
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
            setTextViewText(R.id.task_widget_count, if (total > 0) "${total}个未完成" else "全部完成")

            setOnClickPendingIntent(R.id.task_widget_add, newTaskPendingIntent(context, appWidgetId))
            setPendingIntentTemplate(
                R.id.task_widget_list,
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_TOGGLE_TASK + appWidgetId,
                    Intent(context, TaskListWidgetProvider::class.java).apply { action = ACTION_TOGGLE_TASK },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ),
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
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        return PendingIntent.getActivity(
            context,
            REQUEST_NEW_TASK + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        internal const val ACTION_TOGGLE_TASK = "com.kangle.kardleaf.action.TOGGLE_TASK_WIDGET"
        internal const val EXTRA_TASK_ID = "task_id"
        internal const val MAX_TASKS = 10
        private const val ACTION_QUICK_ADD_TASK = "com.kangle.kardleaf.action.QUICK_ADD_TASK_WIDGET"
        private const val REQUEST_NEW_TASK = 28_000
        private const val REQUEST_TOGGLE_TASK = 29_000
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        internal fun compactTaskText(text: String): String =
            text
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "未命名任务" }

        private fun performCompleteFeedback(context: Context) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(35L)
            }
        }

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val widgetIds = manager.getAppWidgetIds(ComponentName(context, TaskListWidgetProvider::class.java))
            widgetIds.forEach { widgetId ->
                TaskListWidgetProvider().updateWidgetAsync(context, manager, widgetId)
            }
        }
    }
}
