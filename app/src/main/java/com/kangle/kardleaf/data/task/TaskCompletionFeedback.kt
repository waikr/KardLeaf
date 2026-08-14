package com.kangle.kardleaf.data.task

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kangle.kardleaf.data.utils.KardLeafLog

private const val TASK_FEEDBACK_LOG_TAG = "KardLeafTaskFeedback"

internal object TaskCompletionFeedback {
    private const val VIBRATION_DURATION_MS = 35L

    fun perform(
        context: Context,
        usage: Int = VibrationAttributes.USAGE_TOUCH,
    ) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() != true) return
        val activeVibrator = vibrator ?: return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val attributes = VibrationAttributes.Builder()
                        .setUsage(usage)
                        .build()
                    activeVibrator.vibrate(effect, attributes)
                } else {
                    val attributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    activeVibrator.vibrate(effect, attributes)
                }
            } else {
                @Suppress("DEPRECATION")
                activeVibrator.vibrate(VIBRATION_DURATION_MS)
            }
        }.onFailure { error ->
            KardLeafLog.w(TASK_FEEDBACK_LOG_TAG, "completion feedback failed", error)
        }
    }
}
