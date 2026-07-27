package com.kangle.kardleaf.data.task

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.utils.KardLeafLog

private const val TASK_REMINDER_LOG_TAG = "KardLeafTaskReminder"

internal fun shouldLaunchReminderPopupDirectly(appState: String, canDrawOverlays: Boolean): Boolean =
    appState == "FOREGROUND" || appState == "VISIBLE" || canDrawOverlays

/**
 * 提醒到期后的前台服务：铃声与震动由服务直接播放，不依赖通知渠道设置，
 * 因此应用在后台或被杀掉后仍可响铃；弹窗模式下同时负责直接拉起提醒弹窗。
 */
class TaskReminderService : Service() {
    private val stopHandler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var foregroundNotificationId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALERT -> startAlert(intent)
            else -> stopAlert()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopFeedback()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int) {
        KardLeafLog.w(TASK_REMINDER_LOG_TAG, "service shortService timeout startId=$startId")
        stopAlert()
    }

    private fun startAlert(intent: Intent) {
        val taskId = intent.getLongExtra(TaskReminderScheduler.EXTRA_TASK_ID, 0L)
        val taskText = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_TEXT).orEmpty()
        val ring = intent.getBooleanExtra(EXTRA_REMINDER_RING, true)
        val vibrate = intent.getBooleanExtra(EXTRA_REMINDER_VIBRATE, true)
        val popup = intent.getBooleanExtra(EXTRA_REMINDER_POPUP, true)
        val notificationId = taskId.hashCode()
        val scheduler = TaskReminderScheduler(this)
        TaskReminderScheduler.createNotificationChannel(this)

        // 服务自己播放铃声/震动，挂载的通知走静音渠道，避免与渠道声音叠加。
        val notification = scheduler.buildReminderNotification(
            taskId = taskId,
            taskText = taskText,
            popup = popup,
            ring = false,
            vibrate = false,
            channelId = TaskReminderScheduler.SILENT_CHANNEL_ID,
        )
        foregroundNotificationId?.takeIf { it != notificationId }?.let {
            // 上一条提醒尚在前台展示：先分离其通知再切换，保证旧通知不被吞掉
            stopForegroundKeepNotification()
        }
        val foregrounded = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE,
                )
            } else {
                startForeground(notificationId, notification)
            }
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "service startForeground failed id=$taskId", error)
        }.isSuccess

        if (!foregrounded) {
            // 无法进入前台：退回渠道发声的普通通知（与旧行为一致），并尽快停止自身
            val fallback = scheduler.buildReminderNotification(
                taskId = taskId,
                taskText = taskText,
                popup = popup,
                ring = ring,
                vibrate = vibrate,
                channelId = if (ring) TaskReminderScheduler.CHANNEL_ID else TaskReminderScheduler.SILENT_CHANNEL_ID,
            )
            runCatching { NotificationManagerCompat.from(this).notify(notificationId, fallback) }
            stopSelf()
            return
        }

        foregroundNotificationId = notificationId
        KardLeafLog.i(
            TASK_REMINDER_LOG_TAG,
            "service alert id=$taskId ring=$ring vibrate=$vibrate popup=$popup " +
                "appState=${TaskReminderScheduler.appProcessState(this)}",
        )
        startFeedback(taskId, ring, vibrate)
        if (popup) {
            maybeLaunchPopup(taskId, taskText)
        }
        stopHandler.removeCallbacksAndMessages(null)
        stopHandler.postDelayed({ stopAlert() }, ALERT_TIMEOUT_MS)
    }

    private fun maybeLaunchPopup(taskId: Long, taskText: String) {
        val appState = TaskReminderScheduler.appProcessState(this)
        val overlays = TaskReminderScheduler.canDrawOverlays(this)
        if (!shouldLaunchReminderPopupDirectly(appState, overlays)) {
            // 锁屏/熄屏场景交给通知上的 fullScreenIntent；亮屏后台且无悬浮窗权限时只能横幅
            KardLeafLog.i(
                TASK_REMINDER_LOG_TAG,
                "popup deferred to fullScreenIntent id=$taskId appState=$appState overlays=$overlays",
            )
            return
        }
        runCatching {
            startActivity(TaskReminderScheduler.reminderAlertIntent(this, taskId, taskText))
            KardLeafLog.i(TASK_REMINDER_LOG_TAG, "popup direct launch id=$taskId appState=$appState overlays=$overlays")
        }.onFailure { error ->
            KardLeafLog.e(TASK_REMINDER_LOG_TAG, "popup direct launch failed id=$taskId", error)
        }
    }

    private fun startFeedback(taskId: Long, ring: Boolean, vibrate: Boolean) {
        stopFeedback()
        if (ring) {
            runCatching {
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ringtone = RingtoneManager.getRingtone(this, soundUri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                        volume = 1f
                    }
                    play()
                }
                KardLeafLog.i(TASK_REMINDER_LOG_TAG, "service sound requested id=$taskId hasRingtone=${ringtone != null}")
            }.onFailure { error ->
                KardLeafLog.e(TASK_REMINDER_LOG_TAG, "service sound failed id=$taskId", error)
            }
        }
        if (vibrate) {
            runCatching {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getSystemService(VibratorManager::class.java).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(ALERT_VIBRATION_PATTERN, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(ALERT_VIBRATION_PATTERN, 0)
                }
                KardLeafLog.i(TASK_REMINDER_LOG_TAG, "service vibration requested id=$taskId hasVibrator=${vibrator != null}")
            }.onFailure { error ->
                KardLeafLog.e(TASK_REMINDER_LOG_TAG, "service vibration failed id=$taskId", error)
            }
        }
    }

    private fun stopAlert() {
        stopFeedback()
        stopForegroundKeepNotification()
        stopSelf()
    }

    private fun stopForegroundKeepNotification() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH) }
        foregroundNotificationId = null
    }

    private fun stopFeedback() {
        stopHandler.removeCallbacksAndMessages(null)
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
        ringtone = null
        vibrator = null
    }

    companion object {
        const val ACTION_START_ALERT = "com.kangle.kardleaf.action.TASK_REMINDER_ALERT_START"
        const val ACTION_STOP_ALERT = "com.kangle.kardleaf.action.TASK_REMINDER_ALERT_STOP"
        const val EXTRA_REMINDER_RING = "reminder_ring"
        const val EXTRA_REMINDER_VIBRATE = "reminder_vibrate"
        const val EXTRA_REMINDER_POPUP = "reminder_popup"
        private const val ALERT_TIMEOUT_MS = 15_000L
        private val ALERT_VIBRATION_PATTERN = longArrayOf(0L, 260L, 120L, 260L, 700L)

        fun startIntent(context: Context, task: TaskEntity): Intent =
            Intent(context, TaskReminderService::class.java)
                .setAction(ACTION_START_ALERT)
                .putExtra(TaskReminderScheduler.EXTRA_TASK_ID, task.id)
                .putExtra(TaskReminderScheduler.EXTRA_TASK_TEXT, task.taskText)
                .putExtra(EXTRA_REMINDER_RING, task.reminderRing)
                .putExtra(EXTRA_REMINDER_VIBRATE, task.reminderVibrate)
                .putExtra(EXTRA_REMINDER_POPUP, task.reminderMode != TaskEntity.REMINDER_MODE_NOTIFICATION)

        fun stopIntent(context: Context): Intent =
            Intent(context, TaskReminderService::class.java).setAction(ACTION_STOP_ALERT)
    }
}
