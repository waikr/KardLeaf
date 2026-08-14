package com.kangle.kardleaf.data.utils

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.kangle.kardleaf.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class EditorOpenSession(
    val sessionId: Long,
    val documentKey: String,
    val humanStartRealtimeMs: Long,
    val contentGeneration: Long,
    val estimatedLength: Int,
    val kernel: String,
    val coldOrWarm: String,
) {
    fun elapsedMs(): Long = SystemClock.elapsedRealtime() - humanStartRealtimeMs

    fun trace(actualLength: Int? = null): String =
        "sessionId=$sessionId documentKey=${documentKey.hashCode()} contentGeneration=$contentGeneration " +
            "estimatedLength=$estimatedLength actualLength=${actualLength ?: -1} kernel=$kernel coldOrWarm=$coldOrWarm " +
            "thread=${Thread.currentThread().name} elapsed=${elapsedMs()}ms"

    companion object {
        private val nextId = AtomicLong()

        fun create(
            documentKey: String,
            estimatedLength: Int,
            kernel: String,
            coldOrWarm: String,
            humanStartRealtimeMs: Long = SystemClock.elapsedRealtime(),
        ): EditorOpenSession {
            val id = nextId.incrementAndGet()
            return EditorOpenSession(
                sessionId = id,
                documentKey = documentKey,
                humanStartRealtimeMs = humanStartRealtimeMs,
                contentGeneration = id,
                estimatedLength = estimatedLength,
                kernel = kernel,
                coldOrWarm = coldOrWarm,
            )
        }
    }
}

/**
 * KardLeaf unified log switch.
 *
 * During animation / performance / editor tests, turn category switches on here.
 * When logs become noisy, turn only the needed category on instead of editing scattered Log calls.
 */
object KardLeafLog {
    @Volatile
    private var userLoggingEnabled: Boolean = BuildConfig.KARDLEAF_DEV_VARIANT
    @Volatile
    private var logDir: File? = null

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
    private const val LOG_FILE_COUNT = 5
    private const val LOG_FILE_MAX_BYTES = 1_000_000L

    private val fileLock = Any()

    // ponytail: bounded queue drops oldest file logs if disk cannot keep up.
    private val fileLogExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(512),
        { task -> Thread(task, "KardLeafLogWriter").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )

    fun initialize(
        context: Context,
        enabled: Boolean,
    ) {
        logDir = File(context.applicationContext.filesDir, "diagnostic_logs").apply { mkdirs() }
        setUserLoggingEnabled(enabled)
    }

    fun setUserLoggingEnabled(enabled: Boolean) {
        userLoggingEnabled = enabled
    }

    fun isEnabled(tag: String): Boolean = userLoggingEnabled && isTagEnabled(tag)

    fun redactSensitiveText(text: String): String = text.redactSensitiveLogText()

    fun readFileLogs(): String {
        val dir = logDir ?: return ""
        runCatching {
            fileLogExecutor.submit {}.get(2, TimeUnit.SECONDS)
        }
        return synchronized(fileLock) {
            appLogFiles(dir)
                .filter(File::exists)
                .joinToString("\n") { file ->
                    "===== ${file.name} =====\n${file.readText(Charsets.UTF_8)}"
                }
        }
    }

    fun v(tag: String, message: String): Int = log(tag, Log.VERBOSE, message, null)
    fun v(tag: String, message: String, throwable: Throwable): Int = log(tag, Log.VERBOSE, message, throwable)

    fun d(tag: String, message: String): Int = log(tag, Log.DEBUG, message, null)
    fun d(tag: String, message: String, throwable: Throwable): Int = log(tag, Log.DEBUG, message, throwable)

    fun i(tag: String, message: String): Int = log(tag, Log.INFO, message, null)
    fun i(tag: String, message: String, throwable: Throwable): Int = log(tag, Log.INFO, message, throwable)

    fun w(tag: String, message: String): Int = log(tag, Log.WARN, message, null)
    fun w(tag: String, message: String, throwable: Throwable): Int = log(tag, Log.WARN, message, throwable)

    fun e(tag: String, message: String): Int = log(tag, Log.ERROR, message, null)
    fun e(tag: String, message: String, throwable: Throwable): Int = log(tag, Log.ERROR, message, throwable)

    private fun log(
        tag: String,
        priority: Int,
        message: String,
        throwable: Throwable?,
    ): Int {
        val redactedMessage = message.redactSensitiveLogText()
        writeFileLog(priority, tag, redactedMessage, throwable)
        if (!shouldWriteAndroidLog(priority, tag)) return 0
        return when (priority) {
            Log.VERBOSE -> if (throwable == null) Log.v(tag, redactedMessage) else Log.v(tag, redactedMessage, throwable)
            Log.DEBUG -> if (throwable == null) Log.d(tag, redactedMessage) else Log.d(tag, redactedMessage, throwable)
            Log.INFO -> if (throwable == null) Log.i(tag, redactedMessage) else Log.i(tag, redactedMessage, throwable)
            Log.WARN -> if (throwable == null) Log.w(tag, redactedMessage) else Log.w(tag, redactedMessage, throwable)
            else -> if (throwable == null) Log.e(tag, redactedMessage) else Log.e(tag, redactedMessage, throwable)
        }
    }

    private fun shouldWriteAndroidLog(priority: Int, tag: String): Boolean {
        if (priority >= Log.ERROR) {
            return ERROR_LOGS_ENABLED
        }
        return userLoggingEnabled && isTagEnabled(tag)
    }

    private fun writeFileLog(
        priority: Int,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        val dir = logDir ?: return
        if (priority < Log.WARN && (!userLoggingEnabled || !isTagEnabled(tag))) return
        val stack = throwable?.let { Log.getStackTraceString(it).redactSensitiveLogText() }.orEmpty()
        fileLogExecutor.execute {
            val line = buildString {
                append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
                append(' ')
                append(priorityLabel(priority))
                append('/')
                append(tag)
                append(": ")
                append(message)
                append('\n')
                if (stack.isNotBlank()) {
                    append(stack)
                    append('\n')
                }
            }.toByteArray(Charsets.UTF_8)
            synchronized(fileLock) {
                dir.mkdirs()
                val current = File(dir, "kardleaf.log")
                if (current.length() + line.size > LOG_FILE_MAX_BYTES) {
                    rotateLogs(dir)
                }
                current.appendBytes(line)
            }
        }
    }

    private fun rotateLogs(dir: File) {
        File(dir, "kardleaf.${LOG_FILE_COUNT - 1}.log").delete()
        for (index in (LOG_FILE_COUNT - 2) downTo 1) {
            val from = File(dir, "kardleaf.$index.log")
            if (from.exists()) from.renameTo(File(dir, "kardleaf.${index + 1}.log"))
        }
        val current = File(dir, "kardleaf.log")
        if (current.exists()) current.renameTo(File(dir, "kardleaf.1.log"))
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

    private fun priorityLabel(priority: Int): String = when (priority) {
        Log.VERBOSE -> "V"
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        else -> "E"
    }

    private fun appLogFiles(dir: File): List<File> =
        ((LOG_FILE_COUNT - 1) downTo 1).map { index -> File(dir, "kardleaf.$index.log") } +
            File(dir, "kardleaf.log")

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
