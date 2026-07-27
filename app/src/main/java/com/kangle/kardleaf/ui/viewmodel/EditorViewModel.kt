package com.kangle.kardleaf.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.utils.KardLeafLog
import com.kangle.kardleaf.data.utils.NoteTextStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class EditorViewModel(
    private val repository: RoomNoteRepository,
    private val scope: CoroutineScope,
) {
    fun getNoteHistory(noteId: String): Flow<List<NoteHistory>> = repository.getNoteHistory(noteId)
    fun getNoteRemarks(noteId: String): Flow<List<NoteRemark>> = repository.getNoteRemarks(noteId)
    suspend fun getNoteFrontMatterProperties(noteId: String) = repository.getNoteFrontMatterProperties(noteId)
    suspend fun getNoteForProperties(noteId: String): Note? = repository.getNote(noteId)
    suspend fun getFullNoteForShare(noteId: String): Note? = repository.getNoteForShare(noteId)

    suspend fun getFullNotesForShare(notes: List<Note>): List<Note>? {
        if (notes.isEmpty()) return emptyList()
        val fullNotes = mutableListOf<Note>()
        notes.forEach { fullNotes += getFullNoteForShare(it.id) ?: return null }
        return fullNotes
    }

    suspend fun getNoteTextStatsForProperties(noteId: String): NoteTextStats =
        repository.getNoteTextStatsForProperties(noteId)

    fun addNoteRemark(noteId: String, content: String, onComplete: () -> Unit) {
        scope.launch { repository.addNoteRemark(noteId, content); onComplete() }
    }

    fun updateNoteRemark(remarkId: Long, content: String, onComplete: () -> Unit) {
        scope.launch { repository.updateNoteRemark(remarkId, content); onComplete() }
    }

    fun deleteNoteRemark(remarkId: Long) {
        scope.launch { repository.deleteNoteRemark(remarkId) }
    }

    suspend fun getRemarkNoteSummaries(): List<NoteRecordSummary> = repository.getRemarkNoteSummaries()
    suspend fun getHistoryNoteSummaries(): List<NoteRecordSummary> = repository.getHistoryNoteSummaries()

    fun deleteNoteHistory(historyId: Long) {
        scope.launch { repository.deleteNoteHistory(historyId) }
    }

    suspend fun getHistoryCleanupPreview(keep: Int): List<HistoryCleanupPreview> =
        repository.getHistoryCleanupPreview(keep)

    fun cleanupOldHistoryVersions() {
        scope.launch {
            try {
                repository.cleanupOldHistoryVersions()
            } catch (e: Exception) {
                KardLeafLog.e("MainViewModel", "Failed to cleanup old history versions", e)
            }
        }
    }

    suspend fun preparePreviewMarkdown(markdown: String, currentFolder: String): String {
        val startMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(OPEN_PATH_PROBE_TAG, "previewPrepare start folder=$currentFolder markdownLen=${markdown.length} thread=${Thread.currentThread().name}")
        val result = repository.resolveMarkdownImages(markdown, currentFolder)
        KardLeafLog.d(OPEN_PATH_PROBE_TAG, "previewPrepare done folder=$currentFolder markdownLen=${markdown.length} resultLen=${result.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms thread=${Thread.currentThread().name}")
        return result
    }

    suspend fun importImage(uri: Uri, currentFolder: String): String = repository.importImage(uri, currentFolder)
    suspend fun getImageImportTooLargeMessage(uri: Uri): String? = repository.getImageImportTooLargeMessage(uri)
    suspend fun importDrawingImage(bitmap: Bitmap, source: String, folder: String): String = repository.importDrawingImage(bitmap, source, folder)
    suspend fun updateDrawingImage(bitmap: Bitmap, source: String, folder: String, reference: String): Boolean = repository.updateDrawingImage(bitmap, source, folder, reference)
    suspend fun loadDrawingSource(folder: String, reference: String): String? = repository.loadDrawingSource(folder, reference)
    suspend fun loadImageViewerResource(
        folder: String,
        reference: String,
    ): RoomNoteRepository.ImageViewerResource? =
        repository.loadImageViewerResource(folder, reference)

    suspend fun loadImageEditorResource(
        folder: String,
        resource: RoomNoteRepository.ImageViewerResource,
    ): RoomNoteRepository.ImageEditorResource? =
        repository.loadImageEditorResource(folder, resource)

    suspend fun saveImageAnnotation(
        folder: String,
        resource: RoomNoteRepository.ImageEditorResource,
        bitmap: Bitmap,
        drawingSource: String,
    ) = repository.saveImageAnnotation(folder, resource, bitmap, drawingSource)

    suspend fun resolveMarkdownImageDataUris(markdown: String, folder: String) = repository.resolveNoteImages(markdown, folder)
    suspend fun resolveNoteImages(note: Note) = repository.resolveNoteImages(note.content, note.folder)

    suspend fun resolveNoteThumbnailBitmap(note: Note): Bitmap? {
        val startMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(OPEN_PATH_PROBE_TAG, "thumbnailVm start path=${note.file.path} folder=${note.folder} firstImageRefLen=${note.firstImageReference?.length ?: 0}")
        val result = repository.resolveNoteThumbnailBitmap(note)
        KardLeafLog.d(OPEN_PATH_PROBE_TAG, "thumbnailVm done path=${note.file.path} folder=${note.folder} ok=${result != null} elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
        return result
    }

    suspend fun resolveImageThumbnailBitmap(note: Note, reference: String): Bitmap? =
        repository.resolveImageThumbnailBitmap(note, reference)

    fun peekNoteThumbnailBitmap(note: Note): Bitmap? = repository.peekNoteThumbnail(note)

    fun peekImageThumbnailBitmap(note: Note, reference: String): Bitmap? = repository.peekImageThumbnail(note, reference)

    private companion object {
        const val OPEN_PATH_PROBE_TAG = "KardLeafOpenPathProbe"
    }
}
