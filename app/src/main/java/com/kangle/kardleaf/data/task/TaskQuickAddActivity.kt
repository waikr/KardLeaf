package com.kangle.kardleaf.data.task

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationAttributes
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.task.TaskEditorResult
import com.kangle.kardleaf.data.task.toTaskEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.KardLeafLogTags
import com.kangle.kardleaf.ui.TaskEditorDialog
import com.kangle.kardleaf.ui.showToast
import com.kangle.kardleaf.ui.theme.KardLeafTheme
import com.kangle.kardleaf.widget.TaskListWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskQuickAddActivity : ComponentActivity() {
    private var editingTask: TaskEntity? = null
    private var storeDeferred: Deferred<TaskMarkdownStore?>? = null
    private var saveInProgress = false
    private var editorOpenStartedAtMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val clickAction = intent.getStringExtra(EXTRA_WIDGET_CLICK_ACTION).orEmpty()
        if (clickAction == CLICK_ACTION_TOGGLE) {
            setTheme(android.R.style.Theme_NoDisplay)
        }
        super.onCreate(savedInstanceState)
        if (clickAction == CLICK_ACTION_TOGGLE) {
            if (taskId > 0L) {
                TaskCompletionFeedback.perform(applicationContext, VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
            }
            TaskListWidgetProvider.completeTaskFromWidgetAsync(applicationContext, taskId)
            finish()
            return
        }

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        editorOpenStartedAtMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(
            KardLeafLogTags.USER_PERF,
            "taskEditor openRequest source=widget taskId=$taskId action=${clickAction.ifBlank { "new" }}",
        )
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.12f }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        storeDeferred = lifecycleScope.async(Dispatchers.IO) { TaskMarkdownStore.create(applicationContext) }

        if (taskId <= 0L) {
            showEditor(null)
        } else {
            lifecycleScope.launch {
                val task =
                    withContext(Dispatchers.IO) {
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
        KardLeafLog.d(
            KardLeafLogTags.USER_PERF,
            "taskEditor showEditor source=widget taskId=${task?.id ?: 0} elapsed=${SystemClock.elapsedRealtime() - editorOpenStartedAtMs}ms",
        )
        setContent {
            val groups by taskDao.observeGroups().collectAsState(initial = emptyList())
            LaunchedEffect(groups.size) {
                KardLeafLog.d(
                    KardLeafLogTags.USER_PERF,
                    "taskEditor dataSnapshot source=widget groups=${groups.size} elapsed=${SystemClock.elapsedRealtime() - editorOpenStartedAtMs}ms",
                )
            }
            KardLeafTheme(styleSystemBars = false) {
                TaskEditorDialog(
                    task = task,
                    groups = groups,
                    useDialog = false,
                    autoFocusTitle = true,
                    openStartedAtMs = editorOpenStartedAtMs,
                    onDismiss = { if (!saveInProgress) finish() },
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
        if (saveInProgress) return
        saveInProgress = true
        val appContext = applicationContext
        lifecycleScope.launch {
            val saved =
                try {
                    withContext(Dispatchers.IO) {
                        val now = System.currentTimeMillis()
                        val store = storeDeferred?.await() ?: TaskMarkdownStore.create(appContext) ?: return@withContext null
                        val current = editingTask
                        store.saveTaskBatch(
                            original = current,
                            candidate = result.toTaskEntity(current, now),
                            parentTaskId = result.parentTaskId,
                            parentTaskSelectionChanged = result.parentTaskSelectionChanged,
                            childTaskTexts = result.childTaskTexts,
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    KardLeafLog.e(KardLeafLogTags.TASK_SAVE, "quick task save failed", error)
                    null
                }
            val savedBatch = saved
            if (savedBatch == null) {
                saveInProgress = false
                showToast("任务保存失败，请检查笔记库权限")
                return@launch
            }
            runCatching {
                val scheduler = TaskReminderScheduler(appContext)
                scheduler.schedule(savedBatch.task)
                savedBatch.children.forEach(scheduler::schedule)
                TaskListWidgetProvider.refreshAllWidgets(appContext)
            }.onFailure { error ->
                KardLeafLog.e(KardLeafLogTags.TASK_SAVE, "quick task refresh failed id=${savedBatch.task.id}", error)
            }
            showToast(if (editingTask == null) "已添加任务" else "已更新任务")
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
