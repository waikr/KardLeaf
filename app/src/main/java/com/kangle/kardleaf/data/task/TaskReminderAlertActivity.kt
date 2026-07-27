package com.kangle.kardleaf.data.task

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.ui.theme.KardLeafTheme

private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"

class TaskReminderAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val taskId = intent.getLongExtra(TaskReminderScheduler.EXTRA_TASK_ID, 0L)
        val taskText = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_TEXT).orEmpty()
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "alert activity shown id=$taskId")

        setContent {
            KardLeafTheme {
                TaskReminderAlertDialog(
                    taskText = taskText,
                    onDismiss = { finish() },
                    onOpenApp = {
                        openApp()
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        // 铃声/震动由 TaskReminderService 播放；弹窗被关闭即认为用户已知晓，停止提醒反馈
        if (isFinishing) {
            stopAlertService()
        }
        super.onDestroy()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
    }

    private fun stopAlertService() {
        runCatching { startService(TaskReminderService.stopIntent(this)) }
            .onFailure { error ->
                KardLeafLog.e(TASK_REMINDER_LOG_TAG, "alert stop service failed", error)
                runCatching { stopService(Intent(this, TaskReminderService::class.java)) }
            }
    }

    private fun openApp() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        runCatching { startActivity(launchIntent) }
    }
}

@Composable
private fun TaskReminderAlertDialog(
    taskText: String,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "任务提醒",
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = taskText.ifBlank { "待办任务到期" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenApp) {
                Text("打开")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    )
}
