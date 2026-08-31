package com.kangle.kardleaf.data.repository.note

import com.kangle.kardleaf.data.database.NoteHistoryDao
import com.kangle.kardleaf.data.database.NoteHistoryEntity
import com.kangle.kardleaf.data.database.NoteHistoryPreviewEntity
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.SearchQueryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Date

internal class NoteHistoryStore(
    private val dao: NoteHistoryDao,
    private val prefs: PrefsManager,
) {
    fun getHistory(noteId: String): Flow<List<NoteHistory>> =
        dao.getHistoryPreview(noteId, PREVIEW_CHAR_LIMIT, FULL_CONTENT_CHAR_LIMIT).map { histories ->
            histories.map { it.toNoteHistory(FULL_CONTENT_CHAR_LIMIT) }
        }

    fun searchPreview(query: String): Flow<List<NoteHistory>> {
        val safeQuery = query.trim()
        KardLeafLog.d(SEARCH_TRACE_TAG, "history request ${SearchQueryUtils.describeForLog(query)}")
        if (safeQuery.isBlank()) {
            KardLeafLog.d(SEARCH_TRACE_TAG, "history skip reason=blankQuery ${SearchQueryUtils.describeForLog(query)}")
            return flowOf(emptyList())
        }
        return dao.searchHistoryPreview(SearchQueryUtils.escapeLikePattern(safeQuery), SEARCH_RESULT_LIMIT).map { histories ->
            val previewLimited = histories.count { it.content.length >= PREVIEW_CHAR_LIMIT }
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "history result queryLen=${safeQuery.length} count=${histories.size} limit=$SEARCH_RESULT_LIMIT " +
                    "previewLimit=$PREVIEW_CHAR_LIMIT previewLimited=$previewLimited " +
                    "maxReturnedContentLen=${histories.maxOfOrNull { it.content.length } ?: 0}",
            )
            histories.map { it.toNoteHistory() }
        }
    }

    suspend fun delete(historyId: Long) = withContext(Dispatchers.IO) { dao.deleteById(historyId) }

    suspend fun getCleanupPreview(keep: Int): List<HistoryCleanupPreview> = withContext(Dispatchers.IO) {
        val safeKeep = keep.coerceIn(PrefsManager.MIN_HISTORY_VERSION_LIMIT, PrefsManager.MAX_HISTORY_VERSION_LIMIT)
        dao.getHistoryCountsOverLimit(safeKeep).map {
            HistoryCleanupPreview(it.noteId, it.versionCount, it.versionCount - safeKeep)
        }
    }

    suspend fun cleanupOldVersions() = withContext(Dispatchers.IO) {
        val keep = prefs.getHistoryVersionLimit()
        dao.getAllHistoryNoteIds().forEach { dao.pruneOldVersions(it, keep) }
    }

    private fun NoteHistoryEntity.toNoteHistory() = NoteHistory(
        id = id,
        noteId = noteId,
        title = title,
        content = content,
        savedAt = Date(savedAtMs),
        contentLength = content.length,
        contentIsPreview = false,
    )

    private fun NoteHistoryPreviewEntity.toNoteHistory(fullContentLimit: Int) = NoteHistory(
        id = id,
        noteId = noteId,
        title = title,
        content = content,
        savedAt = Date(savedAtMs),
        contentLength = contentLength,
        contentIsPreview = contentLength > fullContentLimit,
    )

    private companion object {
        const val PREVIEW_CHAR_LIMIT = 200
        const val FULL_CONTENT_CHAR_LIMIT = 80_000
        const val SEARCH_RESULT_LIMIT = 100
        const val SEARCH_TRACE_TAG = "KardLeafSearchTrace"
    }
}
