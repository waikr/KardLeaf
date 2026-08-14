package com.kangle.kardleaf.data.repository.note

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.kangle.kardleaf.data.database.NoteHistoryDao
import com.kangle.kardleaf.data.database.NoteHistoryEntity
import com.kangle.kardleaf.data.database.NoteRemarkDao
import com.kangle.kardleaf.data.database.NoteRemarkEntity
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.OutputStreamWriter
import java.security.MessageDigest

/**
 * 备注与版本记录的外部存储镜像备份（过渡方案）。
 *
 * Room 仍是唯一数据源，备注/版本记录的打开与保存链路完全不变；本类只在每次
 * 变更落库之后，把对应笔记的全部备注 / 版本记录整体重写到用户选择的根目录：
 *
 * ```
 * .KardLeaf/
 *     remarks/<笔记key>.json   // 该笔记的全部备注
 *     history/<笔记key>.json   // 该笔记的全部版本记录
 * ```
 *
 * 文件内容以 Room 当前状态为准（写入即覆盖，记录为空则删除文件），因此镜像是
 * 幂等的：任何一次同步都会收敛到最新状态。所有写入均为 fire-and-forget
 * （独立 IO scope + Mutex 串行），失败只记日志，绝不影响主链路。
 * 这是后续把全部数据迁移到外部存储的准备步骤。
 */
internal class NoteRecordExternalBackup(
    private val context: Context,
    private val historyDao: NoteHistoryDao,
    private val remarkDao: NoteRemarkDao,
    private val onExternalWrite: () -> Unit,
) {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    @Volatile
    private var rootDir: DocumentFile? = null

    @Volatile
    private var fullSyncDoneForRoot: String? = null

    private data class RemarkPayload(
        val id: Long,
        val content: String,
        val createdAtMs: Long,
        val updatedAtMs: Long,
    )

    private data class RemarksFilePayload(
        val version: Int = 1,
        val noteId: String,
        val remarks: List<RemarkPayload>,
    )

    private data class HistoryVersionPayload(
        val id: Long,
        val title: String,
        val content: String,
        val savedAtMs: Long,
    )

    private data class HistoryFilePayload(
        val version: Int = 1,
        val noteId: String,
        val versions: List<HistoryVersionPayload>,
    )

    fun onRootChanged(root: DocumentFile?) {
        rootDir = root
        fullSyncDoneForRoot = null
    }

    /** 根目录选定后做一次全量镜像；同一根目录本进程内只跑一次，force 可强制重跑。 */
    fun scheduleFullSync(force: Boolean = false) {
        scope.launch {
            writeMutex.withLock {
                val rootKey = rootDir?.uri?.toString() ?: return@withLock
                if (!force && fullSyncDoneForRoot == rootKey) return@withLock
                try {
                    runFullSync()
                    fullSyncDoneForRoot = rootKey
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    KardLeafLog.e(TAG, "Full external backup failed", e)
                }
            }
        }
    }

    fun scheduleRemarksSync(vararg noteIds: String?) {
        scheduleSync(noteIds) { syncRemarksFile(it) }
    }

    fun scheduleHistorySync(vararg noteIds: String?) {
        scheduleSync(noteIds) { syncHistoryFile(it) }
    }

    private fun scheduleSync(
        noteIds: Array<out String?>,
        sync: suspend (String) -> Unit,
    ) {
        val ids = noteIds.filterNotNull().filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return
        scope.launch {
            writeMutex.withLock {
                ids.forEach { noteId ->
                    try {
                        sync(noteId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        KardLeafLog.e(TAG, "External backup failed noteId=$noteId", e)
                    }
                }
            }
        }
    }

    private suspend fun runFullSync() {
        if (rootDir == null) return
        val remarksByNote = remarkDao.getAllRemarks().groupBy { it.noteId }
        remarksByNote.forEach { (noteId, remarks) -> writeRemarksFile(noteId, remarks) }
        val historyNoteIds = historyDao.getAllHistoryNoteIds()
        historyNoteIds.forEach { noteId -> writeHistoryFile(noteId, historyDao.getHistoryList(noteId)) }
        pruneStaleFiles(resolveDir(REMARKS_DIR_NAME, create = false), remarksByNote.keys)
        pruneStaleFiles(resolveDir(HISTORY_DIR_NAME, create = false), historyNoteIds.toSet())
    }

    private suspend fun syncRemarksFile(noteId: String) {
        val remarks = remarkDao.getRemarksList(noteId)
        if (remarks.isEmpty()) {
            deleteBackupFile(REMARKS_DIR_NAME, noteId)
        } else {
            writeRemarksFile(noteId, remarks)
        }
    }

    private suspend fun syncHistoryFile(noteId: String) {
        val history = historyDao.getHistoryList(noteId)
        if (history.isEmpty()) {
            deleteBackupFile(HISTORY_DIR_NAME, noteId)
        } else {
            writeHistoryFile(noteId, history)
        }
    }

    private fun writeRemarksFile(
        noteId: String,
        remarks: List<NoteRemarkEntity>,
    ) {
        val payload = RemarksFilePayload(
            noteId = noteId,
            remarks = remarks.map { RemarkPayload(it.id, it.content, it.createdAtMs, it.updatedAtMs) },
        )
        writeJsonFile(REMARKS_DIR_NAME, noteId, payload)
    }

    private fun writeHistoryFile(
        noteId: String,
        history: List<NoteHistoryEntity>,
    ) {
        if (history.isEmpty()) return
        val payload = HistoryFilePayload(
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
        val dir = resolveDir(subDirName, create = true) ?: return
        val fileName = backupFileName(noteId)
        var file = dir.findFile(fileName)?.takeIf { it.isFile }
        if (file == null) {
            file = dir.createFile("application/json", fileName)
        }
        val target = file ?: return
        context.contentResolver.openOutputStream(target.uri, "wt")?.use { outputStream ->
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                gson.toJson(payload, writer)
                writer.flush()
            }
        }
        onExternalWrite()
    }

    private fun deleteBackupFile(
        subDirName: String,
        noteId: String,
    ) {
        val dir = resolveDir(subDirName, create = false) ?: return
        val file = dir.findFile(backupFileName(noteId)) ?: return
        if (file.isFile) {
            file.delete()
            onExternalWrite()
        }
    }

    /** 全量同步时清掉镜像目录里已无对应记录的 json 文件，保持镜像与 Room 一致。 */
    private fun pruneStaleFiles(
        dir: DocumentFile?,
        liveNoteIds: Set<String>,
    ) {
        if (dir == null) return
        val expected = liveNoteIds.mapTo(mutableSetOf()) { backupFileName(it) }
        var deleted = false
        dir.listFiles().forEach { child ->
            val name = child.name ?: return@forEach
            if (child.isFile && name.endsWith(".json") && name !in expected) {
                deleted = child.delete() || deleted
            }
        }
        if (deleted) onExternalWrite()
    }

    private fun resolveDir(
        name: String,
        create: Boolean,
    ): DocumentFile? {
        val root = rootDir ?: return null
        val backupRoot = root.findFile(BACKUP_DIR_NAME)?.takeIf { it.isDirectory }
            ?: (if (create) root.createDirectory(BACKUP_DIR_NAME) else null)
            ?: return null
        return backupRoot.findFile(name)?.takeIf { it.isDirectory }
            ?: (if (create) backupRoot.createDirectory(name) else null)
    }

    /**
     * noteId 可能是 recordId，也可能是含 `/` 的笔记路径，不能直接作文件名。
     * 保留可读前缀 + noteId 哈希后缀保证唯一，真实 noteId 存在 json 内容里。
     */
    private fun backupFileName(noteId: String): String {
        val sanitized = buildString {
            noteId.forEach { ch ->
                append(if (ch.isLetterOrDigit() || ch == '-' || ch == '_') ch else '_')
            }
        }.trim('_').take(40)
        val hash = md5Hex(noteId).take(8)
        return if (sanitized.isEmpty()) "$hash.json" else "$sanitized-$hash.json"
    }

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "NoteRecordExternalBackup"
        const val BACKUP_DIR_NAME = ".KardLeaf"
        const val REMARKS_DIR_NAME = "remarks"
        const val HISTORY_DIR_NAME = "history"
    }
}
