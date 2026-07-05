package com.kangle.kardleaf.data.task

import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    private val stopHandler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        val taskId = intent.getLongExtra(TaskReminderScheduler.EXTRA_TASK_ID, 0L)
        val taskText = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_TEXT).orEmpty()
        KardLeafLog.i(TASK_REMINDER_LOG_TAG, "alert activity shown id=$taskId")
        startAlertFeedback(taskId)

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
        stopAlertFeedback()
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

    private fun startAlertFeedback(taskId: Long) {
        runCatching {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(this, soundUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                    volume = 1f
                }
                play()
            }
            KardLeafLog.i(
                TASK_REMINDER_LOG_TAG,
                "alert sound requested id=$taskId hasRingtone=${ringtone != null}",
            )
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "alert sound failed id=$taskId", error)
        }

        runCatching {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0L, 260L, 120L, 260L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, -1)
            }
            KardLeafLog.i(
                TASK_REMINDER_LOG_TAG,
                "alert vibration requested id=$taskId hasVibrator=${vibrator != null}",
            )
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "alert vibration failed id=$taskId", error)
        }

        stopHandler.postDelayed({ stopAlertFeedback() }, ALERT_FEEDBACK_TIMEOUT_MS)
    }

    private fun stopAlertFeedback() {
        stopHandler.removeCallbacksAndMessages(null)
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
        ringtone = null
        vibrator = null
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

    companion object {
        private const val ALERT_FEEDBACK_TIMEOUT_MS = 15_000L
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
