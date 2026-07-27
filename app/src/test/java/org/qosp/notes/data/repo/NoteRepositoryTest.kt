package org.qosp.notes.data.repo

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Note

class NoteRepositoryTest {
    @Test
    fun updatesTheQuillpadSessionBeforeSave() =
        runBlocking {
            val repository = NoteRepository()
            repository.insertNote(Note(content = "before"))

            repository.update { it.copy(content = "after") }

            assertEquals("after", repository.currentNote()?.content)
            assertTrue(repository.isDirty())
            repository.markSaved()
            assertFalse(repository.isDirty())
        }

    @Test
    fun doesNotMarkEditsMadeDuringSaveAsSaved() =
        runBlocking {
            val repository = NoteRepository()
            repository.insertNote(Note(content = "before"))
            repository.update { it.copy(content = "saving") }
            val savingRevision = repository.revision()

            repository.update { it.copy(content = "typed during save") }
            repository.markSaved(savingRevision)

            assertEquals("typed during save", repository.currentNote()?.content)
            assertTrue(repository.isDirty())
        }

    @Test
    fun restoredContentReplacesTheSavedSessionSnapshot() =
        runBlocking {
            val repository = NoteRepository()
            repository.insertNote(Note(content = "old"))
            repository.update { it.copy(content = "saved before history") }
            repository.markSaved()

            repository.replace(Note(content = "restored history"))

            assertEquals("restored history", repository.currentNote()?.content)
            assertFalse(repository.isDirty())
        }

    @Test
    fun clearsDirtyBeforePublishingReplacedContent() =
        runBlocking {
            val repository = NoteRepository()
            repository.insertNote(Note(content = "old"))
            repository.update { it.copy(content = "dirty") }
            var dirtyWhenReplacementPublished: Boolean? = null
            val observer = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                repository.getById(1L).drop(1).first()
                dirtyWhenReplacementPublished = repository.isDirty()
            }

            repository.replace(Note(content = "restored"))
            observer.join()

            assertFalse(dirtyWhenReplacementPublished ?: true)
        }
}
