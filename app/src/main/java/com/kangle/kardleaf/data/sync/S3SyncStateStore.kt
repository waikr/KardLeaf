package com.kangle.kardleaf.data.sync

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File

internal data class S3StoredState(
    @field:SerializedName(value = "version", alternate = ["a"])
    val version: Int = 1,
    @field:SerializedName(value = "initialized", alternate = ["b"])
    val initialized: Boolean = false,
    @field:SerializedName(value = "pendingRefresh", alternate = ["c"])
    val pendingRefresh: Boolean = false,
    @field:SerializedName(value = "files", alternate = ["d"])
    val files: Map<String, S3Baseline> = emptyMap(),
)

internal class S3SyncStateStore(context: Context, identity: String) {
    private val file = File(context.noBackupFilesDir, "s3-sync/$identity.json")
    private val gson = Gson()

    fun read(): S3StoredState {
        val atomic = AtomicFile(file)
        if (!file.exists() && !File(file.path + ".bak").exists()) return S3StoredState()
        return try {
            gson.fromJson(atomic.openRead().bufferedReader().use { it.readText() }, S3StoredState::class.java).also {
                require(it.version == 1 && it.files.keys.all { path -> S3Paths.validate(path).isNotEmpty() })
            }
        } catch (_: Exception) {
            error("S3 同步基线损坏，已停止同步以保护文件")
        }
    }

    fun write(state: S3StoredState) = writeS3Atomic(file, gson.toJson(state))
}

internal fun writeS3Atomic(file: File, text: String) {
    check(file.parentFile?.let { it.isDirectory || it.mkdirs() } == true) { "无法创建同步状态目录" }
    val atomic = AtomicFile(file)
    val output = atomic.startWrite()
    try {
        output.write(text.toByteArray(Charsets.UTF_8))
        atomic.finishWrite(output)
    } catch (error: Exception) {
        atomic.failWrite(output)
        throw error
    }
}
