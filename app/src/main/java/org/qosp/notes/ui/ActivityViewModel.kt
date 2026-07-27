package org.qosp.notes.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadActionBridge
import com.kangle.kardleaf.ui.editor.quillpad.KardLeafQuillpadEditorBridge
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository

/** Activity-scoped adapter for the KardLeaf operations exposed by Quillpad UI. */
class ActivityViewModel(
    private val noteRepository: NoteRepository,
    private val editorBridge: KardLeafQuillpadEditorBridge,
    private val actionBridge: KardLeafQuillpadActionBridge,
) : ViewModel() {

    var tempPhotoUri: Uri? = null
    var notesToBackup: Set<Note> = emptySet()

    suspend fun togglePin(note: Note): Boolean = persistLatest(note) && replace(actionBridge.togglePin())

    suspend fun toggleArchive(note: Note): Boolean = persistLatest(note) && replace(actionBridge.toggleArchive())

    suspend fun delete(note: Note): Boolean = persistLatest(note) && replace(actionBridge.moveToTrash())

    suspend fun restore(note: Note): Boolean = persistLatest(note) && replace(actionBridge.restore())

    suspend fun deletePermanently(note: Note): Boolean = persistLatest(note) && actionBridge.deletePermanently()

    suspend fun importImage(uri: Uri): String = actionBridge.importImage(uri)

    private suspend fun persistLatest(fallback: Note): Boolean {
        var current = fallback
        while (editorBridge.needsSave(noteRepository.isDirty())) {
            val revision = noteRepository.revision()
            current = noteRepository.currentNote() ?: current
            if (!editorBridge.save(current)) return false
            noteRepository.markSaved(revision)
        }
        return true
    }

    private fun replace(note: Note?): Boolean {
        note ?: return false
        noteRepository.replace(note)
        return true
    }
}
