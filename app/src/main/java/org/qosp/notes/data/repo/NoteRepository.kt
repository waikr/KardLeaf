package org.qosp.notes.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.qosp.notes.data.model.Note

/**
 * In-memory state store for the retained Quillpad editor UI.
 *
 * The original is backed by Room. KardLeaf keeps a single in-memory note in
 * a [MutableStateFlow] so that [org.qosp.notes.ui.editor.EditorViewModel] can
 * load / update it through the same flow-based contract used by the original
 * editor; the host bridge remains the only persistent-storage owner.
 */
class NoteRepository {

    private val noteFlow = MutableStateFlow<Note?>(null)
    private var dirty = false
    private var revision = 0L

    fun getById(id: Long): Flow<Note?> = noteFlow

    suspend fun insertNote(note: Note): Long {
        // The host bridge owns persistent KardLeaf storage; this mirrors the
        // original repository contract only for Quillpad's editing session.
        val withId = note.copy(id = 1L)
        noteFlow.value = withId
        dirty = false
        revision = 0L
        return 1L
    }

    fun update(transform: (Note) -> Note) {
        noteFlow.value?.let { note ->
            dirty = true
            revision++
            noteFlow.value = transform(note)
        }
    }

    fun currentNote(): Note? = noteFlow.value

    fun isDirty(): Boolean = dirty

    fun revision(): Long = revision

    fun markSaved(savedRevision: Long = revision) {
        if (revision == savedRevision) dirty = false
    }

    fun replace(note: Note) {
        dirty = false
        noteFlow.value = note.copy(id = noteFlow.value?.id ?: note.id)
    }
}
