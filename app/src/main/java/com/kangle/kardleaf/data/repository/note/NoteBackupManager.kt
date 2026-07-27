package com.kangle.kardleaf.data.repository.note

import com.google.gson.Gson
import com.kangle.kardleaf.data.database.NoteHistoryDao
import com.kangle.kardleaf.data.database.NoteHistoryEntity
import com.kangle.kardleaf.data.database.NoteRemarkDao
import com.kangle.kardleaf.data.database.NoteRemarkEntity
import com.kangle.kardleaf.data.repository.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class NoteBackupManager(
    private val historyDao: NoteHistoryDao,
    private val remarkDao: NoteRemarkDao,
    private val prefs: PrefsManager,
) {
    private val gson = Gson()

    private data class UserDataBackup(
        val version: Int = 1,
        val favoriteNotePaths: List<String>? = emptyList(),
        val pinnedNotePaths: List<String>? = emptyList(),
        val history: List<HistoryBackup>? = emptyList(),
        val remarks: List<RemarkBackup>? = emptyList(),
    )

    private data class HistoryBackup(
        val id: Long,
        val noteId: String,
        val title: String,
        val content: String,
        val savedAtMs: Long,
    )

    private data class RemarkBackup(
        val id: Long = 0,
        val noteId: String,
        val content: String,
        val createdAtMs: Long? = null,
        val updatedAtMs: Long,
    )

    suspend fun export(): String = withContext(Dispatchers.IO) {
        gson.toJson(
            UserDataBackup(
                favoriteNotePaths = prefs.getFavoriteNotePaths().toList(),
                pinnedNotePaths = prefs.getPinnedNotePaths().toList(),
                history = historyDao.getAllHistory().map {
                    HistoryBackup(it.id, it.noteId, it.title, it.content, it.savedAtMs)
                },
                remarks = remarkDao.getAllRemarks().map {
                    RemarkBackup(it.id, it.noteId, it.content, it.createdAtMs, it.updatedAtMs)
                },
            ),
        )
    }

    suspend fun import(json: String) = withContext(Dispatchers.IO) {
        val backup = gson.fromJson(json, UserDataBackup::class.java) ?: return@withContext
        prefs.replaceFavoriteNotePaths(backup.favoriteNotePaths.orEmpty())
        prefs.replacePinnedNotePaths(backup.pinnedNotePaths.orEmpty())
        backup.history.orEmpty().takeIf { it.isNotEmpty() }?.let { history ->
            historyDao.insertAll(history.map {
                NoteHistoryEntity(it.id, it.noteId, it.title, it.content, it.savedAtMs)
            })
        }
        backup.remarks.orEmpty().takeIf { it.isNotEmpty() }?.let { remarks ->
            remarkDao.insertAll(remarks.map {
                NoteRemarkEntity(it.id, it.noteId, it.content, it.createdAtMs ?: it.updatedAtMs, it.updatedAtMs)
            })
        }
    }
}
