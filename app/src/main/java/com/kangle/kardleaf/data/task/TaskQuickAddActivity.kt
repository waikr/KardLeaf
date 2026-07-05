package com.kangle.kardleaf.data.task

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.theme.KardLeafTheme
import com.kangle.kardleaf.widget.TaskListWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TASK_QUICK_ADD_LOG_TAG = "KardLeafTaskQuickAdd"

class TaskQuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        setContent {
            KardLeafTheme {
                TaskQuickAddDialog(
                    onDismiss = { finish() },
                    onSave = { text -> saveTask(text) },
                )
            }
        }
    }

    private fun saveTask(text: String) {
        val appContext = applicationContext
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val draft = TaskEntity(
                    taskText = text,
                    notePath = null,
                    done = false,
                    reminderAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
                val dao = AppDatabase.getDatabase(appContext).taskDao()
                val task = draft.copy(id = dao.insert(draft))
                KardLeafLog.i(TASK_QUICK_ADD_LOG_TAG, "widget quick add saved id=${task.id} textLen=${task.taskText.length}")
                TaskReminderScheduler(appContext).schedule(task)
                TaskListWidgetProvider.refreshAllWidgets(appContext)
                task
            }
            Toast.makeText(this@TaskQuickAddActivity, "已添加任务", Toast.LENGTH_SHORT).show()
            KardLeafLog.i(TASK_QUICK_ADD_LOG_TAG, "widget quick add finish id=${saved.id}")
            finish()
        }
    }
}

@Composable
private fun TaskQuickAddDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建任务") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                label = { Text("任务标题") },
                singleLine = true,
                isError = error != null,
                supportingText = {
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isBlank()) {
                        error = "任务标题不能为空"
                    } else {
                        onSave(trimmed)
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    )
}
