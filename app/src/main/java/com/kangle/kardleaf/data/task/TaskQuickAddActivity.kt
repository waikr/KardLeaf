package com.kangle.kardleaf.data.task

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.TaskEditorDialog
import com.kangle.kardleaf.ui.TaskEditorResult
import com.kangle.kardleaf.ui.showToast
import com.kangle.kardleaf.ui.theme.KardLeafTheme
import com.kangle.kardleaf.widget.TaskListWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TASK_QUICK_ADD_LOG_TAG = "KardLeafTaskQuickAdd"
private const val WIDGET_CLICK_LOG_TAG = "KardLeafWidgetClick"

class TaskQuickAddActivity : ComponentActivity() {
    private var editingTask: TaskEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val clickAction = intent.getStringExtra(EXTRA_WIDGET_CLICK_ACTION).orEmpty()
        KardLeafLog.i(
            WIDGET_CLICK_LOG_TAG,
            "task action entered clickAction=$clickAction taskId=$taskId action=${intent.action}",
        )
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        if (clickAction == CLICK_ACTION_TOGGLE) {
            lifecycleScope.launch {
                val completed = withContext(Dispatchers.IO) {
                    TaskListWidgetProvider.completeTaskFromWidget(applicationContext, taskId)
                }
                KardLeafLog.i(
                    WIDGET_CLICK_LOG_TAG,
                    "task toggle finished taskId=$taskId completed=$completed",
                )
                finish()
            }
            return
        }

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE,
        )

        if (taskId <= 0L) {
            showEditor(null)
        } else {
            lifecycleScope.launch {
                val task = withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext).taskDao().getTask(taskId)
                }
                if (task == null) {
                    showToast("无法打开这条任务")
                    finish()
                    return@launch
                }
                editingTask = task
                showEditor(task)
            }
        }
    }

    private fun showEditor(task: TaskEntity?) {
        val taskDao = AppDatabase.getDatabase(applicationContext).taskDao()
        setContent {
            val groups by taskDao.observeGroups().collectAsState(initial = emptyList())
            KardLeafTheme(styleSystemBars = false) {
                TaskEditorDialog(
                    task = task,
                    groups = groups,
                    autoFocusTitle = true,
                    embeddedOverlay = true,
                    onDismiss = { finish() },
                    onSave = ::saveTask,
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun saveTask(result: TaskEditorResult) {
        val appContext = applicationContext
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val dao = AppDatabase.getDatabase(appContext).taskDao()
                val current = editingTask
                val task = if (current == null) {
                    val draft = TaskEntity(
                        taskText = result.text,
                        notePath = null,
                        done = result.done,
                        reminderAt = result.reminderAt,
                        groupId = result.groupId,
                        priority = result.priority,
                        dueAt = result.dueAt,
                        repeatRule = result.repeatRule,
                        notes = result.notes,
                        createdAt = now,
                        updatedAt = now,
                    )
                    draft.copy(id = dao.insert(draft))
                } else {
                    current.copy(
                        taskText = result.text,
                        done = result.done,
                        reminderAt = result.reminderAt,
                        groupId = result.groupId,
                        priority = result.priority,
                        dueAt = result.dueAt,
                        repeatRule = result.repeatRule,
                        notes = result.notes,
                        updatedAt = now,
                    ).also { dao.update(it) }
                }
                KardLeafLog.i(
                    TASK_QUICK_ADD_LOG_TAG,
                    "widget editor saved mode=${if (current == null) "new" else "edit"} id=${task.id} textLen=${task.taskText.length} reminderAt=${task.reminderAt}",
                )
                TaskReminderScheduler(appContext).schedule(task)
                TaskListWidgetProvider.refreshAllWidgets(appContext)
                task
            }
            showToast(if (editingTask == null) "已添加任务" else "已更新任务")
            KardLeafLog.i(TASK_QUICK_ADD_LOG_TAG, "widget editor finish id=${saved.id}")
            finish()
        }
    }

    companion object {
        internal const val EXTRA_TASK_ID = "kardleaf_widget_task_id"
        internal const val EXTRA_WIDGET_CLICK_ACTION = "kardleaf_widget_task_click_action"
        internal const val CLICK_ACTION_EDIT = "edit"
        internal const val CLICK_ACTION_TOGGLE = "toggle"
    }
}
