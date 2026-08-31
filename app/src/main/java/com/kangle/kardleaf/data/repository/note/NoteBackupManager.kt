package com.kangle.kardleaf.data.repository.note

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
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
        @field:SerializedName(value = "version", alternate = ["a"])
        val version: Int = 1,
        @field:SerializedName(value = "favoriteNotePaths", alternate = ["b"])
        val favoriteNotePaths: List<String>? = emptyList(),
        @field:SerializedName(value = "pinnedNotePaths", alternate = ["c"])
        val pinnedNotePaths: List<String>? = emptyList(),
        @field:SerializedName(value = "history", alternate = ["d"])
        val history: List<HistoryBackup>? = emptyList(),
        @field:SerializedName(value = "remarks", alternate = ["e"])
        val remarks: List<RemarkBackup>? = emptyList(),
    )

    private data class HistoryBackup(
        @field:SerializedName(value = "id", alternate = ["a"])
        val id: Long,
        @field:SerializedName(value = "noteId", alternate = ["b"])
        val noteId: String,
        @field:SerializedName(value = "title", alternate = ["c"])
        val title: String,
        @field:SerializedName(value = "content", alternate = ["d"])
        val content: String,
        @field:SerializedName(value = "savedAtMs", alternate = ["e"])
        val savedAtMs: Long,
    )

    private data class RemarkBackup(
        @field:SerializedName(value = "id", alternate = ["a"])
        val id: Long = 0,
        @field:SerializedName(value = "noteId", alternate = ["b"])
        val noteId: String,
        @field:SerializedName(value = "content", alternate = ["c"])
        val content: String,
        @field:SerializedName(value = "createdAtMs", alternate = ["d"])
        val createdAtMs: Long? = null,
        @field:SerializedName(value = "updatedAtMs", alternate = ["e"])
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
