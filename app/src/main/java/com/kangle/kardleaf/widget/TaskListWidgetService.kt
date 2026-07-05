package com.kangle.kardleaf.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.kangle.kardleaf.R
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import kotlinx.coroutines.runBlocking

class TaskListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = TaskFactory(applicationContext)

    private class TaskFactory(
        private val context: Context,
    ) : RemoteViewsFactory {
        private var tasks: List<TaskEntity> = emptyList()

        override fun onCreate() = Unit

        override fun onDataSetChanged() {
            tasks =
                runBlocking {
                    AppDatabase.getDatabase(context)
                        .taskDao()
                        .getWidgetOpenTasks(TaskListWidgetProvider.MAX_TASKS)
                }
        }

        override fun onDestroy() {
            tasks = emptyList()
        }

        override fun getCount(): Int = tasks.size

        override fun getViewAt(position: Int): RemoteViews {
            val task = tasks.getOrNull(position)
            return RemoteViews(context.packageName, R.layout.widget_task_list_item).apply {
                if (task != null) {
                    setTextViewText(R.id.task_widget_item_text, TaskListWidgetProvider.compactTaskText(task.taskText))
                    val fillInIntent = Intent().putExtra(TaskListWidgetProvider.EXTRA_TASK_ID, task.id)
                    setOnClickFillInIntent(R.id.task_widget_item, fillInIntent)
                    setOnClickFillInIntent(R.id.task_widget_item_check, fillInIntent)
                }
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = tasks.getOrNull(position)?.id ?: position.toLong()

        override fun hasStableIds(): Boolean = true
    }
}
