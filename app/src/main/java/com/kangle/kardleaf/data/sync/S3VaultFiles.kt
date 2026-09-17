package com.kangle.kardleaf.data.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.security.MessageDigest
import java.util.UUID

internal class S3VaultFiles(private val context: Context, private val rootUri: String, private val underscore: Boolean) {
    private val root = DocumentFile.fromTreeUri(context, Uri.parse(rootUri)) ?: error("无法访问当前笔记库")
    private val resolver = context.contentResolver
    private val journal = File(context.noBackupFilesDir, "s3-local/${s3Hash(rootUri)}.json")
    private val recovery = File(context.noBackupFilesDir, "s3-recovery/${s3Hash(rootUri)}")
    private val gson = Gson()
    private data class Entry(val file: DocumentFile, val name: String, val size: Long, val time: Long, val directory: Boolean)
    private data class Publication(
        @field:SerializedName(value = "path", alternate = ["a"])
        val path: String,
        @field:SerializedName(value = "temporary", alternate = ["b"])
        val temporary: String,
        @field:SerializedName(value = "backup", alternate = ["c"])
        val backup: String,
    )

    private fun create(dir: DocumentFile, name: String, mime: String): DocumentFile {
        val uri = DocumentsContract.createDocument(resolver, dir.uri, mime, name) ?: error("无法创建同步文件或目录")
        val file = DocumentFile.fromSingleUri(context, uri) ?: error("新建文件无法访问")
        if (file.name != name) { file.delete(); error("存储提供方更改了文件名，已停止同步") }
        return file
    }

    private fun rename(file: DocumentFile, name: String): DocumentFile {
        // SingleDocumentFile.renameTo/createDirectory are unsupported; use SAF directly, retaining the returned URI.
        val uri = DocumentsContract.renameDocument(resolver, file.uri, name) ?: error("存储提供方不支持安全重命名")
        return (DocumentFile.fromSingleUri(context, uri) ?: error("重命名后的文件无法访问")).also {
            check(it.name == name) { "存储提供方更改了目标文件名，已停止同步" }
        }
    }

    private fun children(dir: DocumentFile): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(rootUri), DocumentsContract.getDocumentId(dir.uri))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        return (resolver.query(uri, projection, null, null, null) ?: error("无法完整扫描笔记库，请检查目录权限")).use { cursor ->
            val items = mutableListOf<Entry>()
            while (cursor.moveToNext()) {
                val childUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(rootUri), cursor.getString(0))
                val file = DocumentFile.fromSingleUri(context, childUri) ?: error("目录项无法访问")
                items += Entry(file, cursor.getString(1) ?: error("目录项缺少名称"), if (cursor.isNull(3)) -1 else cursor.getLong(3),
                    if (cursor.isNull(4)) 0 else cursor.getLong(4), cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
            require(items.map { it.name }.distinct().size == items.size) { "笔记库有重复文件名" }
            items
        }
    }

    private fun find(path: String): DocumentFile? {
        S3Paths.validate(path)
        var current = root
        for (segment in path.removeSuffix("/").split('/')) {
            current = children(current).firstOrNull { it.name == segment }?.file ?: return null
        }
        return current
    }

    private fun parent(path: String, create: Boolean): DocumentFile? {
        var current = root
        for (segment in path.removeSuffix("/").split('/').dropLast(1)) {
            val entry = children(current).firstOrNull { it.name == segment }
            current = if (entry == null) {
                if (!create) return null
                create(current, segment, DocumentsContract.Document.MIME_TYPE_DIR)
            } else {
                require(entry.directory) { "同步路径与已有文件冲突" }
                entry.file
            }
        }
        return current
    }

    suspend fun scan(): Map<String, S3FileState> {
        // ponytail: full streaming hashes favor correctness; add a validated change journal only if large-vault I/O warrants it.
        recover()
        val result = linkedMapOf<String, S3FileState>()
        suspend fun walk(dir: DocumentFile, prefix: String) {
            currentCoroutineContext().ensureActive()
            for (entry in children(dir)) {
                val path = prefix + entry.name + if (entry.directory) "/" else ""
                if (!S3Paths.included(path, underscore)) continue
                result[path] = if (entry.directory) S3FileState(0, entry.time, directory = true) else fingerprint(entry.file, entry.time).also {
                    require(entry.size < 0 || entry.size == it.size) { "本地文件大小正在变化或读取不完整" }
                }
                if (entry.directory) walk(entry.file, path)
            }
        }
        walk(root, "")
        return result
    }

    suspend fun state(path: String): S3FileState? {
        val dir = parent(path, false) ?: return null
        val entry = children(dir).firstOrNull { it.name == path.removeSuffix("/").substringAfterLast('/') } ?: return null
        require(entry.directory == path.endsWith('/')) { "文件与目录类型发生变化" }
        return if (entry.directory) S3FileState(0, entry.time, directory = true) else fingerprint(entry.file, entry.time)
    }

    private suspend fun fingerprint(file: DocumentFile, modifiedMs: Long, target: File? = null): S3FileState {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        val output = target?.outputStream()
        try {
            (resolver.openInputStream(file.uri) ?: error("无法读取同步文件")).use { input ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(bytes)
                    if (count < 0) break
                    digest.update(bytes, 0, count)
                    output?.write(bytes, 0, count)
                    size += count
                }
            }
        } finally { output?.close() }
        require(file.lastModified() == modifiedMs) { "本地文件正在保存，请稍后重试" }
        return S3FileState(size, modifiedMs, digest.digest().s3Hex())
    }

    suspend fun snapshot(path: String, target: File, expected: S3FileState) {
        if (expected.directory) { target.writeBytes(byteArrayOf()); return }
        val file = find(path) ?: error("本地文件已删除，请重新预览")
        val actual = fingerprint(file, file.lastModified(), target)
        require(actual.localVersion() == expected.localVersion()) { "本地文件已变化，请重新预览" }
    }

    suspend fun apply(path: String, source: File?, expected: S3FileState?, expectedHash: String = "") {
        require(S3Paths.included(path, underscore)) { "该路径不参与同步" }
        require(state(path)?.localVersion() == expected?.localVersion()) { "本地文件已变化，请重新预览" }
        if (path.endsWith('/')) {
            val file = find(path)
            if (source == null) {
                if (file != null) {
                    require(children(file).isEmpty()) { "目录中仍有文件，已保留目录" }
                    check(file.delete()) { "无法删除空目录" }
                }
            } else if (file == null) {
                create(parent(path, true) ?: error("无法创建父目录"), path.removeSuffix("/").substringAfterLast('/'), DocumentsContract.Document.MIME_TYPE_DIR)
            }
            return
        }
        val folder = parent(path, source != null) ?: return
        val name = path.substringAfterLast('/')
        val old = children(folder).firstOrNull { it.name == name }?.file
        if (old != null && expected != null) {
            check(recovery.isDirectory || recovery.mkdirs()) { "无法保留本地恢复副本" }
            val backup = File(recovery, "${s3Hash(path)}-local")
            snapshot(path, backup, expected)
            writeS3Atomic(File(recovery, "${s3Hash(path)}-local.json"), gson.toJson(mapOf("path" to path, "time" to System.currentTimeMillis())))
        }
        if (source == null) { if (old != null) check(old.delete()) { "本地删除失败" }; return }
        val temporaryName = ".${UUID.randomUUID()}.kardleaf-s3-tmp"
        val backupName = ".${UUID.randomUUID()}.kardleaf-s3-tmp"
        val temporary = create(folder, temporaryName, "application/octet-stream")
        var published = false
        try {
            (resolver.openOutputStream(temporary.uri, "wt") ?: error("无法写入同步临时文件")).use { output -> source.inputStream().use { it.copyTo(output) } }
            val verified = fingerprint(temporary, temporary.lastModified())
            require(verified.size == source.length() && verified.hash == expectedHash) { "本地写入校验失败" }
            require(state(path)?.localVersion() == expected?.localVersion()) { "本地文件在下载时发生变化" }
            writeS3Atomic(journal, gson.toJson(Publication(path, temporaryName, backupName)))
            val oldBackup = old?.let { rename(it, backupName) }
            rename(temporary, name)
            published = true
            oldBackup?.let { check(it.delete()) { "无法清理原文件备份，将在下次同步重试" } }
            check(journal.delete()) { "同步恢复记录清理失败" }
        } finally {
            if (!published) {
                if (journal.exists()) recover() else temporary.delete()
            }
        }
    }

    fun recover() {
        if (!journal.exists() && !File(journal.path + ".bak").exists()) return
        val record = android.util.AtomicFile(journal).openRead().bufferedReader().use { gson.fromJson(it, Publication::class.java) }
        S3Paths.validate(record.path)
        require(listOf(record.temporary, record.backup).all { '/' !in it && '\\' !in it && it.endsWith(".kardleaf-s3-tmp") }) { "同步恢复记录无效" }
        val folder = parent(record.path, false) ?: error("同步恢复目录丢失")
        val entries = children(folder).associateBy { it.name }
        val name = record.path.substringAfterLast('/')
        val backup = entries[record.backup]?.file
        if (entries[name] == null && backup != null) rename(backup, name)
        else if (entries[name] != null) backup?.let { check(it.delete()) { "无法清理同步恢复文件" } }
        entries[record.temporary]?.file?.let { check(it.delete()) { "无法清理同步临时文件" } }
        check(journal.delete()) { "无法清理同步恢复记录" }
    }
}
