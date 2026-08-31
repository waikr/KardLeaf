package com.kangle.kardleaf.ui.editor.quillpad

import android.graphics.Bitmap
import android.net.Uri
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import kotlinx.coroutines.flow.Flow
import org.qosp.notes.data.model.Note as QuillpadNote

internal data class KardLeafQuillpadFeatureSnapshot(
    val path: String,
    val folder: String,
    val content: String,
    val tags: List<String>,
)

/** Routes supported Quillpad actions through KardLeaf's existing repository. */
class KardLeafQuillpadActionBridge(
    private val editorBridge: KardLeafQuillpadEditorBridge,
) {
    private val repository = editorBridge.repository

    suspend fun togglePin(): QuillpadNote? = editorBridge.withStorageLock {
        val source = editorBridge.currentSource() ?: return@withStorageLock null
        val target = !source.isPinned
        repository.togglePinStatus(listOf(source.file.path), target)
        editorBridge.reloadSource()?.takeIf { it.isPinned == target }
    }

    suspend fun toggleArchive(): QuillpadNote? = editorBridge.withStorageLock {
        val source = editorBridge.currentSource() ?: return@withStorageLock null
        val target = !source.isArchived
        if (target) repository.archiveNote(source.file.path) else repository.restoreNote(source.file.path)
        editorBridge.reloadSource()?.takeIf { it.isArchived == target }
    }

    suspend fun moveToTrash(): QuillpadNote? = editorBridge.withStorageLock {
        val source = editorBridge.currentSource() ?: return@withStorageLock null
        repository.deleteNote(source.file.path)
        editorBridge.reloadSource()?.takeIf { it.isDeleted }
    }

    suspend fun restore(): QuillpadNote? = editorBridge.withStorageLock {
        val source = editorBridge.currentSource() ?: return@withStorageLock null
        repository.restoreNote(source.file.path)
        editorBridge.reloadSource()?.takeIf { !it.isDeleted && !it.isArchived }
    }

    suspend fun deletePermanently(): Boolean = editorBridge.withStorageLock {
        val source = editorBridge.currentSource() ?: return@withStorageLock false
        val deleted = repository
            .deleteTrashedNotesPermanentlyWithResult(listOf(source.file.path))
            .successIds
            .contains(source.file.path)
        if (deleted) editorBridge.clearSource()
        deleted
    }

    suspend fun importImage(uri: Uri): String {
        val source = editorBridge.currentSource() ?: return ""
        return repository.importImage(uri, source.folder)
    }

    internal fun featureSnapshot(): KardLeafQuillpadFeatureSnapshot? = editorBridge.currentSource()?.let { source ->
        KardLeafQuillpadFeatureSnapshot(
            path = source.file.path,
            folder = source.folder,
            content = source.content,
            tags = source.tags,
        )
    }

    internal fun histories(snapshot: KardLeafQuillpadFeatureSnapshot): Flow<List<NoteHistory>> =
        repository.getNoteHistory(snapshot.path)

    internal suspend fun deleteHistory(historyId: Long) = repository.deleteNoteHistory(historyId)

    internal suspend fun restoreHistory(snapshot: KardLeafQuillpadFeatureSnapshot, historyId: Long): Boolean =
        editorBridge.withStorageLock {
            repository.restoreNoteHistory(snapshot.path, historyId).isNotBlank()
        }

    internal fun remarks(snapshot: KardLeafQuillpadFeatureSnapshot): Flow<List<NoteRemark>> =
        repository.getNoteRemarks(snapshot.path)

    internal suspend fun frontMatterProperties(snapshot: KardLeafQuillpadFeatureSnapshot): List<NoteFormatUtils.FrontMatterProperty> =
        repository.getNoteFrontMatterProperties(snapshot.path)

    internal suspend fun textStats(snapshot: KardLeafQuillpadFeatureSnapshot): NoteTextStats =
        repository.getNoteTextStatsForProperties(snapshot.path)

    internal suspend fun addRemark(snapshot: KardLeafQuillpadFeatureSnapshot, content: String): Boolean =
        repository.addNoteRemark(snapshot.path, content) != null

    internal suspend fun updateRemark(remarkId: Long, content: String) = repository.updateNoteRemark(remarkId, content)

    internal suspend fun deleteRemark(remarkId: Long) = repository.deleteNoteRemark(remarkId)

    internal suspend fun updateNoteTimestamps(
        snapshot: KardLeafQuillpadFeatureSnapshot,
        createdAtMs: Long,
        updatedAtMs: Long,
    ): Boolean = repository.updateNoteTimestamps(snapshot.path, createdAtMs, updatedAtMs) != null

    internal fun availableTags(): Flow<List<String>> = repository.getYamlTags()

    internal suspend fun updateTags(snapshot: KardLeafQuillpadFeatureSnapshot, tags: List<String>): Boolean =
        editorBridge.withStorageLock { repository.updateNoteTags(snapshot.path, tags) }

    internal suspend fun loadDrawingSource(snapshot: KardLeafQuillpadFeatureSnapshot, reference: String): String? =
        repository.loadDrawingSource(snapshot.folder, reference)

    internal suspend fun importDrawing(
        snapshot: KardLeafQuillpadFeatureSnapshot,
        bitmap: Bitmap,
        drawingSource: String,
    ): String = editorBridge.withStorageLock {
        repository.importDrawingImage(bitmap, drawingSource, snapshot.folder)
    }

    internal suspend fun updateDrawing(
        snapshot: KardLeafQuillpadFeatureSnapshot,
        reference: String,
        bitmap: Bitmap,
        drawingSource: String,
    ): Boolean = editorBridge.withStorageLock {
        repository.updateDrawingImage(bitmap, drawingSource, snapshot.folder, reference)
    }

    internal suspend fun reloadCurrent(): QuillpadNote? = editorBridge.withStorageLock { editorBridge.reloadSource() }
}
