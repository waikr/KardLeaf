package com.kangle.kardleaf.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.task.TaskQuickAddActivity
import com.kangle.kardleaf.data.utils.KardLeafLog
import java.util.Calendar
import kotlinx.coroutines.runBlocking

class TaskListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        return TaskFactory(applicationContext, appWidgetId)
    }

    private class TaskFactory(
        private val context: Context,
        private val appWidgetId: Int,
    ) : RemoteViewsFactory {
        private var rows: List<TaskWidgetRow> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            val tasks =
                runBlocking {
                    AppDatabase.getDatabase(context)
                        .taskDao()
                        .getWidgetOpenTasks(TaskListWidgetProvider.MAX_TASKS)
                }
            rows = buildRows(tasks)
            KardLeafLog.i(
                WIDGET_CLICK_LOG_TAG,
                "task adapter ready widgetId=$appWidgetId taskCount=${tasks.size} rowCount=${rows.size}",
            )
        }

        override fun onDestroy() {
            rows = emptyList()
        }

        override fun getCount(): Int = rows.size

        override fun getViewAt(position: Int): RemoteViews {
            return when (val row = rows.getOrNull(position)) {
                is TaskWidgetRow.Section -> RemoteViews(
                    context.packageName,
                    R.layout.widget_task_list_group_header,
                ).apply {
                    setTextViewText(R.id.task_widget_group_title, row.title)
                    setTextViewText(R.id.task_widget_group_count, row.count.toString())
                }

                is TaskWidgetRow.Task -> RemoteViews(
                    context.packageName,
                    R.layout.widget_task_list_item,
                ).apply {
                    setTextViewText(
                        R.id.task_widget_item_text,
                        TaskListWidgetProvider.compactTaskText(row.task.taskText),
                    )
                    val editIntent = Intent()
                        .putExtra(TaskQuickAddActivity.EXTRA_TASK_ID, row.task.id)
                        .putExtra(
                            TaskQuickAddActivity.EXTRA_WIDGET_CLICK_ACTION,
                            TaskQuickAddActivity.CLICK_ACTION_EDIT,
                        )
                    val toggleIntent = Intent()
                        .putExtra(TaskQuickAddActivity.EXTRA_TASK_ID, row.task.id)
                        .putExtra(
                            TaskQuickAddActivity.EXTRA_WIDGET_CLICK_ACTION,
                            TaskQuickAddActivity.CLICK_ACTION_TOGGLE,
                        )
                    setOnClickFillInIntent(R.id.task_widget_item_text, editIntent)
                    setOnClickFillInIntent(R.id.task_widget_item, editIntent)
                    setOnClickFillInIntent(R.id.task_widget_item_check, toggleIntent)
                }

                null -> RemoteViews(context.packageName, R.layout.widget_task_list_item)
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 2

        override fun getItemId(position: Int): Long = when (val row = rows.getOrNull(position)) {
            is TaskWidgetRow.Section -> Long.MIN_VALUE + row.order
            is TaskWidgetRow.Task -> row.task.id
            null -> position.toLong()
        }

        override fun hasStableIds(): Boolean = true

        private fun buildRows(tasks: List<TaskEntity>): List<TaskWidgetRow> {
            if (tasks.isEmpty()) return emptyList()

            val todayEnd = Calendar.getInstance().run {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
                timeInMillis
            }
            val groups = listOf(
                TaskGroup(
                    order = 0L,
                    title = "今天以前",
                    tasks = tasks.filter { task -> task.reminderAt?.let { it <= todayEnd } == true },
                ),
                TaskGroup(
                    order = 1L,
                    title = "稍后",
                    tasks = tasks.filter { task -> task.reminderAt?.let { it > todayEnd } == true },
                ),
                TaskGroup(
                    order = 2L,
                    title = "无提醒",
                    tasks = tasks.filter { task -> task.reminderAt == null },
                ),
            )
            val result = mutableListOf<TaskWidgetRow>()
            groups.forEach { group ->
                if (group.tasks.isNotEmpty()) {
                    result += TaskWidgetRow.Section(
                        order = group.order,
                        title = group.title,
                        count = group.tasks.size,
                    )
                    group.tasks.forEach { task -> result += TaskWidgetRow.Task(task) }
                }
            }
            return result
        }
    }

    private data class TaskGroup(
        val order: Long,
        val title: String,
        val tasks: List<TaskEntity>,
    )

    private sealed class TaskWidgetRow {
        data class Section(
            val order: Long,
            val title: String,
            val count: Int,
        ) : TaskWidgetRow()

        data class Task(val task: TaskEntity) : TaskWidgetRow()
    }

    companion object {
        private const val WIDGET_CLICK_LOG_TAG = "KardLeafWidgetClick"
    }
}
