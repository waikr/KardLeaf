package com.kangle.kardleaf.data.repository.note

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.NoteHistoryDao
import com.kangle.kardleaf.data.database.NoteHistoryEntity
import com.kangle.kardleaf.data.database.NoteRemarkDao
import com.kangle.kardleaf.data.database.NoteRemarkEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * `.KardLeaf/{remarks,history}` 是备注与历史版本的主数据，Room 仅为查询缓存。
 *
 * 旧版本已经写出的 v1 镜像文件保持兼容。首次打开未带 store 元数据的仓库时，
 * 先合并旧镜像与该仓库 Room，安全发布完整文件后再写 store 元数据；之后始终以
 * 外部文件为准。所有文件 IO 与 Room 缓存替换都由同一 Mutex 串行。
 */
internal class NoteRecordExternalBackup(
    private val context: Context,
    private val database: AppDatabase,
    private val historyDao: NoteHistoryDao,
    private val remarkDao: NoteRemarkDao,
    private val onExternalWrite: () -> Unit,
) {
    private val gson = Gson()
    private val mutex = Mutex()

    @Volatile
    private var rootDir: DocumentFile? = null

    private var loadedSignature: Map<String, FileSignature>? = null

    private data class FileSignature(
        val modifiedAtMs: Long,
        val length: Long,
    )

    private data class StoreMeta(
        @field:SerializedName(value = "version", alternate = ["a"])
        val version: Int = STORE_VERSION,
    )

    private data class RemarkPayload(
        @field:SerializedName(value = "id", alternate = ["a"])
        val id: Long,
        @field:SerializedName(value = "content", alternate = ["b"])
        val content: String,
        @field:SerializedName(value = "createdAtMs", alternate = ["c"])
        val createdAtMs: Long,
        @field:SerializedName(value = "updatedAtMs", alternate = ["d"])
        val updatedAtMs: Long,
    )

    private data class RemarksFilePayload(
        @field:SerializedName(value = "version", alternate = ["a"])
        val version: Int = FILE_VERSION,
        @field:SerializedName(value = "noteId", alternate = ["b"])
        val noteId: String,
        @field:SerializedName(value = "remarks", alternate = ["c"])
        val remarks: List<RemarkPayload>,
    )

    private data class HistoryVersionPayload(
        @field:SerializedName(value = "id", alternate = ["a"])
        val id: Long,
        @field:SerializedName(value = "title", alternate = ["b"])
        val title: String,
        @field:SerializedName(value = "content", alternate = ["c"])
        val content: String,
        @field:SerializedName(value = "savedAtMs", alternate = ["d"])
        val savedAtMs: Long,
    )

    private data class HistoryFilePayload(
        @field:SerializedName(value = "version", alternate = ["a"])
        val version: Int = FILE_VERSION,
        @field:SerializedName(value = "noteId", alternate = ["b"])
        val noteId: String,
        @field:SerializedName(value = "versions", alternate = ["c"])
        val versions: List<HistoryVersionPayload>,
    )

    fun onRootChanged(root: DocumentFile?) {
        rootDir = root
        loadedSignature = null
    }

    suspend fun loadFromExternalStore(): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock { loadFromExternalStoreLocked() }
        }

    suspend fun refreshFromExternalIfChanged(): Boolean =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val signature = snapshotSignature()
                if (signature == loadedSignature) return@withLock true
                loadFromExternalStoreLocked()
            }
        }

    suspend fun syncRemarks(vararg noteIds: String?) =
        withContext(Dispatchers.IO) {
            syncFromRoom(noteIds) { syncRemarksFile(it) }
        }

    suspend fun syncHistory(vararg noteIds: String?) =
        withContext(Dispatchers.IO) {
            syncFromRoom(noteIds) { syncHistoryFile(it) }
        }

    /** 仅供明确的旧数据提升、手工备份导入和全局清理使用，仓库绑定绝不调用。 */
    suspend fun syncFullFromRoom() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    writeFullSnapshot(historyDao.getAllHistory(), remarkDao.getAllRemarks())
                    writeStoreMeta()
                    loadedSignature = snapshotSignature()
                } catch (error: Exception) {
                    runCatching { loadFromExternalStoreLocked() }
                    throw error
                }
            }
        }

    private suspend fun syncFromRoom(
        noteIds: Array<out String?>,
        sync: suspend (String) -> Unit,
    ) {
        val ids = noteIds.filterNotNull().filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        mutex.withLock {
            try {
                ids.forEach { sync(it) }
                loadedSignature = snapshotSignature()
            } catch (error: Exception) {
                runCatching { loadFromExternalStoreLocked() }
                throw error
            }
        }
    }

    private suspend fun loadFromExternalStoreLocked(): Boolean {
        val root = rootDir ?: return false
        return try {
            recoverInterruptedWrites(resolveDir(REMARKS_DIR_NAME, create = false))
            recoverInterruptedWrites(resolveDir(HISTORY_DIR_NAME, create = false))
            val meta = readStoreMeta(root)
            val externalHistory = readAllHistory()
            val externalRemarks = readAllRemarks()
            val authoritative = meta != null
            val histories =
                if (authoritative) {
                    validateUniqueIds(externalHistory.map { it.id }, "history")
                    externalHistory
                } else {
                    mergeHistoryRecords(externalHistory, historyDao.getAllHistory())
                }
            val remarks =
                if (authoritative) {
                    validateUniqueIds(externalRemarks.map { it.id }, "remarks")
                    externalRemarks
                } else {
                    mergeRemarkRecords(externalRemarks, remarkDao.getAllRemarks())
                }

            if (!authoritative) {
                writeFullSnapshot(histories, remarks)
                writeStoreMeta()
            }
            replaceRoomCache(histories, remarks)
            loadedSignature = snapshotSignature()
            true
        } catch (error: Exception) {
            KardLeafLog.e(TAG, "External record store load failed root=${root.uri}", error)
            false
        }
    }

    private suspend fun replaceRoomCache(
        histories: List<NoteHistoryEntity>,
        remarks: List<NoteRemarkEntity>,
    ) {
        database.withTransaction {
            historyDao.deleteAll()
            remarkDao.deleteAll()
            if (histories.isNotEmpty()) historyDao.insertAll(histories)
            if (remarks.isNotEmpty()) remarkDao.insertAll(remarks)
        }
    }

    private suspend fun syncRemarksFile(noteId: String) {
        val remarks = remarkDao.getRemarksList(noteId)
        if (remarks.isEmpty()) {
            deleteRecordFile(REMARKS_DIR_NAME, noteId)
            return
        }
        writeRemarksFile(noteId, remarks)
    }

    private suspend fun syncHistoryFile(noteId: String) {
        val history = historyDao.getHistoryList(noteId)
        if (history.isEmpty()) {
            deleteRecordFile(HISTORY_DIR_NAME, noteId)
            return
        }
        writeHistoryFile(noteId, history)
    }

    private fun writeFullSnapshot(
        histories: List<NoteHistoryEntity>,
        remarks: List<NoteRemarkEntity>,
    ) {
        val remarksByNote = remarks.groupBy { it.noteId }
        val historyByNote = histories.groupBy { it.noteId }
        remarksByNote.forEach { (noteId, records) -> writeRemarksFile(noteId, records) }
        historyByNote.forEach { (noteId, records) -> writeHistoryFile(noteId, records) }
        pruneStaleFiles(resolveDir(REMARKS_DIR_NAME, create = false), remarksByNote.keys)
        pruneStaleFiles(resolveDir(HISTORY_DIR_NAME, create = false), historyByNote.keys)
    }

    private fun writeRemarksFile(
        noteId: String,
        remarks: List<NoteRemarkEntity>,
    ) {
        val payload =
            RemarksFilePayload(
                noteId = noteId,
                remarks = remarks.map { RemarkPayload(it.id, it.content, it.createdAtMs, it.updatedAtMs) },
            )
        writeJsonFile(REMARKS_DIR_NAME, noteId, payload)
    }

    private fun writeHistoryFile(
        noteId: String,
        history: List<NoteHistoryEntity>,
    ) {
        val payload =
            HistoryFilePayload(
                noteId = noteId,
                versions = history.map { HistoryVersionPayload(it.id, it.title, it.content, it.savedAtMs) },
            )
        writeJsonFile(HISTORY_DIR_NAME, noteId, payload)
    }

    private fun writeJsonFile(
        subDirName: String,
        noteId: String,
        payload: Any,
    ) {
        val dir = resolveDir(subDirName, create = true) ?: throw IOException("Cannot create $subDirName")
        writeSafely(dir, backupFileName(noteId), gson.toJson(payload).toByteArray(Charsets.UTF_8))
    }

    private fun writeStoreMeta() {
        val dir = resolveBackupRoot(create = true) ?: throw IOException("Cannot create .KardLeaf")
        writeSafely(dir, STORE_META_FILE, gson.toJson(StoreMeta()).toByteArray(Charsets.UTF_8))
    }

    private fun writeSafely(
        dir: DocumentFile,
        fileName: String,
        bytes: ByteArray,
    ) {
        require(bytes.size <= MAX_JSON_FILE_BYTES) { "$fileName is too large" }
        recoverFile(dir, fileName)
        val temp =
            dir.createFile(BINARY_MIME, ".$fileName.${UUID.randomUUID()}$TEMP_FILE_SUFFIX")
                ?: throw IOException("Cannot create temporary file for $fileName")
        val backupName = backupName(fileName)
        try {
            context.contentResolver.openOutputStream(temp.uri, "wt")?.use { it.write(bytes) }
                ?: throw IOException("Cannot open temporary file for $fileName")
            if (!readBytes(temp).contentEquals(bytes)) throw IOException("Temporary file verification failed for $fileName")

            val staleBackup = dir.findFile(backupName)?.takeIf { it.isFile }
            if (staleBackup != null && !staleBackup.delete()) throw IOException("Cannot remove stale backup for $fileName")
            val current = dir.findFile(fileName)?.takeIf { it.isFile }
            if (current != null && !current.renameTo(backupName)) throw IOException("Cannot back up $fileName")
            if (!temp.renameTo(fileName)) {
                dir.findFile(backupName)?.renameTo(fileName)
                throw IOException("Cannot publish $fileName")
            }
            dir.findFile(backupName)?.delete()
            onExternalWrite()
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }

    private fun deleteRecordFile(
        subDirName: String,
        noteId: String,
    ) {
        val dir = resolveDir(subDirName, create = false) ?: return
        val fileName = backupFileName(noteId)
        recoverFile(dir, fileName)
        val file = dir.findFile(fileName)?.takeIf { it.isFile } ?: return
        if (!file.delete()) throw IOException("Cannot delete $subDirName/$fileName")
        onExternalWrite()
    }

    private fun pruneStaleFiles(
        dir: DocumentFile?,
        liveNoteIds: Set<String>,
    ) {
        if (dir == null) return
        recoverInterruptedWrites(dir)
        val expected = liveNoteIds.mapTo(mutableSetOf()) { backupFileName(it) }
        dir.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            if (child.isFile && name.endsWith(JSON_SUFFIX) && name !in expected) {
                if (!child.delete()) throw IOException("Cannot prune ${dir.name}/$name")
                onExternalWrite()
            }
        }
    }

    private fun readStoreMeta(root: DocumentFile): StoreMeta? {
        val appDir = root.findFile(BACKUP_DIR_NAME)?.takeIf { it.isDirectory } ?: return null
        recoverFile(appDir, STORE_META_FILE)
        val file = appDir.findFile(STORE_META_FILE)?.takeIf { it.isFile } ?: return null
        val meta =
            gson.fromJson(readBytes(file).toString(Charsets.UTF_8), StoreMeta::class.java)
                ?: throw IOException("Invalid record store metadata")
        require(meta.version == STORE_VERSION) { "Unsupported record store version ${meta.version}" }
        return meta
    }

    private fun readAllRemarks(): List<NoteRemarkEntity> {
        val dir = resolveDir(REMARKS_DIR_NAME, create = false) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && it.name.orEmpty().endsWith(JSON_SUFFIX) }
            .sortedBy { it.name }
            .flatMap { file ->
                val payload =
                    gson.fromJson(readBytes(file).toString(Charsets.UTF_8), RemarksFilePayload::class.java)
                        ?: throw IOException("Invalid remarks file ${file.name}")
                require(payload.version == FILE_VERSION) { "Unsupported remarks version ${payload.version}" }
                require(payload.noteId.isNotBlank()) { "Blank noteId in ${file.name}" }
                payload.remarks.map {
                    require(it.id > 0L) { "Invalid remark id in ${file.name}" }
                    NoteRemarkEntity(it.id, payload.noteId, it.content, it.createdAtMs, it.updatedAtMs)
                }
            }
    }

    private fun readAllHistory(): List<NoteHistoryEntity> {
        val dir = resolveDir(HISTORY_DIR_NAME, create = false) ?: return emptyList()
        return dir.listFiles()
            .filter { it.isFile && it.name.orEmpty().endsWith(JSON_SUFFIX) }
            .sortedBy { it.name }
            .flatMap { file ->
                val payload =
                    gson.fromJson(readBytes(file).toString(Charsets.UTF_8), HistoryFilePayload::class.java)
                        ?: throw IOException("Invalid history file ${file.name}")
                require(payload.version == FILE_VERSION) { "Unsupported history version ${payload.version}" }
                require(payload.noteId.isNotBlank()) { "Blank noteId in ${file.name}" }
                payload.versions.map {
                    require(it.id > 0L) { "Invalid history id in ${file.name}" }
                    NoteHistoryEntity(it.id, payload.noteId, it.title, it.content, it.savedAtMs)
                }
            }
    }

    private fun validateUniqueIds(
        ids: List<Long>,
        label: String,
    ) {
        require(ids.size == ids.toSet().size) { "Duplicate $label ids in external store" }
    }

    private fun readBytes(file: DocumentFile): ByteArray {
        val declaredLength = file.length()
        require(declaredLength in 0..MAX_JSON_FILE_BYTES.toLong()) { "${file.name} is too large" }
        val input = context.contentResolver.openInputStream(file.uri) ?: throw IOException("Cannot read ${file.name}")
        return input.use { stream ->
            val output = ByteArrayOutputStream(declaredLength.coerceAtMost(64 * 1024L).toInt())
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_JSON_FILE_BYTES) { "${file.name} is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun recoverInterruptedWrites(dir: DocumentFile?) {
        if (dir == null) return
        val children = dir.listFiles()
        children.filter { it.isFile && it.name.orEmpty().endsWith(TEMP_FILE_SUFFIX) }.forEach { it.delete() }
        children.filter { it.isFile && it.name.orEmpty().endsWith(BACKUP_FILE_SUFFIX) }.forEach { backup ->
            val name = backup.name.orEmpty()
            val finalName = name.removePrefix(".").removeSuffix(BACKUP_FILE_SUFFIX)
            if (dir.findFile(finalName)?.isFile == true) {
                backup.delete()
            } else {
                backup.renameTo(finalName)
            }
        }
    }

    private fun recoverFile(
        dir: DocumentFile,
        fileName: String,
    ) {
        val backup = dir.findFile(backupName(fileName))?.takeIf { it.isFile } ?: return
        if (dir.findFile(fileName)?.isFile == true) {
            backup.delete()
        } else {
            backup.renameTo(fileName)
        }
    }

    private fun snapshotSignature(): Map<String, FileSignature> {
        val result = linkedMapOf<String, FileSignature>()
        val appDir = resolveBackupRoot(create = false) ?: return result
        appDir.findFile(STORE_META_FILE)?.takeIf { it.isFile }?.let {
            result[STORE_META_FILE] = FileSignature(it.lastModified(), it.length())
        }
        listOf(REMARKS_DIR_NAME, HISTORY_DIR_NAME).forEach { dirName ->
            appDir.findFile(dirName)?.takeIf { it.isDirectory }?.listFiles()
                ?.filter { it.isFile && it.name.orEmpty().endsWith(JSON_SUFFIX) }
                ?.forEach { file ->
                    result["$dirName/${file.name}"] = FileSignature(file.lastModified(), file.length())
                }
        }
        return result
    }

    private fun resolveBackupRoot(create: Boolean): DocumentFile? {
        val root = rootDir ?: return null
        return root.findFile(BACKUP_DIR_NAME)?.takeIf { it.isDirectory }
            ?: if (create) root.createDirectory(BACKUP_DIR_NAME) else null
    }

    private fun resolveDir(
        name: String,
        create: Boolean,
    ): DocumentFile? {
        val backupRoot = resolveBackupRoot(create) ?: return null
        return backupRoot.findFile(name)?.takeIf { it.isDirectory }
            ?: if (create) backupRoot.createDirectory(name) else null
    }

    private fun backupFileName(noteId: String): String {
        val sanitized =
            buildString {
                noteId.forEach { ch -> append(if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_') }
            }.trim('_').take(40)
        val hash = md5Hex(noteId).take(8)
        return if (sanitized.isEmpty()) "$hash.json" else "$sanitized-$hash.json"
    }

    private fun backupName(fileName: String) = ".$fileName$BACKUP_FILE_SUFFIX"

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "NoteRecordExternalBackup"
        const val STORE_VERSION = 1
        const val FILE_VERSION = 1
        const val BACKUP_DIR_NAME = ".KardLeaf"
        const val STORE_META_FILE = "records.json"
        const val REMARKS_DIR_NAME = "remarks"
        const val HISTORY_DIR_NAME = "history"
        const val JSON_SUFFIX = ".json"
        const val TEMP_FILE_SUFFIX = ".kardleaf-sync-tmp"
        const val BACKUP_FILE_SUFFIX = ".kardleaf-record-bak"
        const val BINARY_MIME = "application/octet-stream"
        const val MAX_JSON_FILE_BYTES = 64 * 1024 * 1024
    }
}

internal fun mergeHistoryRecords(
    external: List<NoteHistoryEntity>,
    room: List<NoteHistoryEntity>,
): List<NoteHistoryEntity> {
    val merged = linkedMapOf<Long, NoteHistoryEntity>()
    (external + room).forEach { candidate ->
        val existing = merged[candidate.id]
        when {
            existing == null -> merged[candidate.id] = candidate
            existing.noteId == candidate.noteId -> {
                if (candidate.savedAtMs >= existing.savedAtMs) merged[candidate.id] = candidate
            }
            else -> {
                val newId = freshRecordId(merged.keys)
                merged[newId] = candidate.copy(id = newId)
            }
        }
    }
    return merged.values.toList()
}

internal fun mergeRemarkRecords(
    external: List<NoteRemarkEntity>,
    room: List<NoteRemarkEntity>,
): List<NoteRemarkEntity> {
    val merged = linkedMapOf<Long, NoteRemarkEntity>()
    (external + room).forEach { candidate ->
        val existing = merged[candidate.id]
        when {
            existing == null -> merged[candidate.id] = candidate
            existing.noteId == candidate.noteId -> {
                if (candidate.updatedAtMs >= existing.updatedAtMs) merged[candidate.id] = candidate
            }
            else -> {
                val newId = freshRecordId(merged.keys)
                merged[newId] = candidate.copy(id = newId)
            }
        }
    }
    return merged.values.toList()
}

private fun freshRecordId(used: Set<Long>): Long {
    var id: Long
    do {
        id = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    } while (id == 0L || id in used)
    return id
}
