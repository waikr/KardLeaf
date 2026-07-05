package com.kangle.kardleaf.data.utils

import android.util.Log
import com.kangle.kardleaf.BuildConfig

/**
 * KardLeaf unified log switch.
 *
 * During animation / performance / editor tests, turn category switches on here.
 * When logs become noisy, turn only the needed category on instead of editing scattered Log calls.
 */
object KardLeafLog {
    // Non-error diagnostics are only enabled in the dev variant.
    // Stable/release builds keep only error logs to avoid noisy production logging.
    private val NON_ERROR_LOGS_ENABLED: Boolean
        get() = BuildConfig.KARDLEAF_DEV_VARIANT

    // Category switches.
    private const val ERROR_LOGS_ENABLED = true
    private const val PERFORMANCE_LOGS_ENABLED = true
    private const val EDITOR_LOGS_ENABLED = true
    private const val CODEMIRROR_LOGS_ENABLED = true
    private const val CODEMIRROR_IME_LOGS_ENABLED = false
    private const val CODEMIRROR_TABLE_LOGS_ENABLED = false
    private const val NAVIGATION_LOGS_ENABLED = true
    private const val DASHBOARD_LOGS_ENABLED = false
    private const val CUSTOM_SORT_LOGS_ENABLED = false
    private const val DRAWING_PAD_LOGS_ENABLED = false
    private const val SYNC_LOGS_ENABLED = true
    private const val SETTINGS_LOGS_ENABLED = true
    private const val MISC_LOGS_ENABLED = true

    fun isEnabled(tag: String): Boolean = NON_ERROR_LOGS_ENABLED && isTagEnabled(tag)

    fun v(tag: String, message: String): Int = logIfEnabled(tag, Log.VERBOSE) { Log.v(tag, message.redactSensitiveLogText()) }
    fun v(tag: String, message: String, throwable: Throwable): Int = logIfEnabled(tag, Log.VERBOSE) { Log.v(tag, message.redactSensitiveLogText(), throwable) }

    fun d(tag: String, message: String): Int = logIfEnabled(tag, Log.DEBUG) { Log.d(tag, message.redactSensitiveLogText()) }
    fun d(tag: String, message: String, throwable: Throwable): Int = logIfEnabled(tag, Log.DEBUG) { Log.d(tag, message.redactSensitiveLogText(), throwable) }

    fun i(tag: String, message: String): Int = logIfEnabled(tag, Log.INFO) { Log.i(tag, message.redactSensitiveLogText()) }
    fun i(tag: String, message: String, throwable: Throwable): Int = logIfEnabled(tag, Log.INFO) { Log.i(tag, message.redactSensitiveLogText(), throwable) }

    fun w(tag: String, message: String): Int = logIfEnabled(tag, Log.WARN) { Log.w(tag, message.redactSensitiveLogText()) }
    fun w(tag: String, message: String, throwable: Throwable): Int = logIfEnabled(tag, Log.WARN) { Log.w(tag, message.redactSensitiveLogText(), throwable) }

    fun e(tag: String, message: String): Int = logIfEnabled(tag, Log.ERROR) { Log.e(tag, message.redactSensitiveLogText()) }
    fun e(tag: String, message: String, throwable: Throwable): Int = logIfEnabled(tag, Log.ERROR) { Log.e(tag, message.redactSensitiveLogText(), throwable) }

    private inline fun logIfEnabled(tag: String, priority: Int, block: () -> Int): Int {
        if (priority >= Log.ERROR) {
            if (!ERROR_LOGS_ENABLED) return 0
            return block()
        }
        if (!NON_ERROR_LOGS_ENABLED) return 0
        if (!isTagEnabled(tag)) return 0
        return block()
    }

    private fun isTagEnabled(tag: String): Boolean = when {
        BuildConfig.KARDLEAF_DEV_VARIANT -> true
        tag.contains("UserPerf") || tag.contains("StartupPerf") || tag.contains("LargeNoteOpen") -> PERFORMANCE_LOGS_ENABLED
        tag.contains("CM6ImeTrace") -> CODEMIRROR_IME_LOGS_ENABLED
        tag.contains("CM6TableTrace") || tag.contains("PreviewTableTrace") -> CODEMIRROR_TABLE_LOGS_ENABLED
        tag.contains("CM6") || tag.contains("CodeMirror") -> CODEMIRROR_LOGS_ENABLED
        tag.contains("Editor") || tag.contains("Preview") || tag.contains("SearchTrace") || tag.contains("SavePath") || tag.contains("TitleTrace") -> EDITOR_LOGS_ENABLED
        tag.contains("BackTrace") || tag.contains("GestureTrace") || tag.contains("Animation") -> NAVIGATION_LOGS_ENABLED
        tag.contains("CustomSort") -> CUSTOM_SORT_LOGS_ENABLED
        tag.contains("Dashboard") || tag.contains("MainViewModel") -> DASHBOARD_LOGS_ENABLED
        tag.contains("DrawingPad") -> DRAWING_PAD_LOGS_ENABLED
        tag.contains("WebDav") || tag.contains("Sync") -> SYNC_LOGS_ENABLED
        tag.contains("SettingsTrace") -> SETTINGS_LOGS_ENABLED
        else -> MISC_LOGS_ENABLED
    }

    private val sensitiveFieldRegex =
        Regex("""(?i)\b(path|currentPath|previousPath|filePath|folder|title|oldTitle|name|sourceName|targetName|uri|url|serverUrl|username|password|token|authorization)=([^\s,)]{1,512})""")
    private val uriRegex = Regex("""(?i)\b(content|file)://[^\s,)]{1,512}""")

    private fun String.redactSensitiveLogText(): String =
        sensitiveFieldRegex.replace(this) { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2]
            "$key=<redacted:${value.length}>"
        }.let { text ->
            uriRegex.replace(text) { match ->
                "${match.groupValues[1]}://<redacted>"
            }
        }
}
