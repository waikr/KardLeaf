package com.kangle.kardleaf.data.repository.note

import com.kangle.kardleaf.data.database.NoteHistoryEntity
import com.kangle.kardleaf.data.database.NoteRemarkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class NoteRecordExternalBackupTest {
    @Test
    fun committedFileIsOutsideTemporaryCleanupAndFailedLoadsInvalidateSignature() {
        // Source boundary check; real SAF rename/IO fault injection still requires Android.
        val relative = Path.of("src/main/java/com/kangle/kardleaf/data/repository/note/NoteRecordExternalBackup.kt")
        val source = listOf(relative, Path.of("app").resolve(relative)).first(Files::exists).toFile().readText()
        val publish = source.substringAfter("private fun writeSafely(").substringBefore("private fun deleteRecordFile(")
        val cleanup = publish.indexOf("temp.delete()")
        assertTrue(cleanup >= 0)
        assertTrue(publish.indexOf("throw error", cleanup) < publish.indexOf("onExternalWrite()"))
        assertTrue(publish.indexOf("throw error", cleanup) < publish.indexOf("current?.delete()"))
        assertTrue(publish.indexOf("throw error", cleanup) < publish.indexOf("KardLeafLog.d("))
        val load = source.substringAfter("private suspend fun loadFromExternalStoreLocked()")
            .substringBefore("private suspend fun replaceRoomCache(")
        assertTrue(load.substringAfter("catch (error: Exception)").contains("loadedSignature = null"))
    }

    @Test
    fun localPublicationDoesNotHideUnrelatedExternalChanges() {
        val loaded = mapOf("history/a.json" to 1, "remarks/b.json" to 2)
        val published = updatedRecordSignature(loaded, "history/a.json", 3)
        val external = mapOf("history/a.json" to 3, "remarks/b.json" to 4)

        assertEquals(mapOf("history/a.json" to 3, "remarks/b.json" to 2), published)
        assertNotEquals(external, published) // Refresh must still import the changed remark.
        assertEquals(1, loaded["history/a.json"]) // No mutation of the previous baseline.
    }

    @Test
    fun repeatedWritesMigrationAndDeletionTrackOnlyCommittedKeys() {
        val loaded = mapOf("history/old.json" to 1, "remarks/other.json" to 2)
        val migrated = updatedRecordSignature(
            updatedRecordSignature(loaded, "history/new.json", 3),
            "history/old.json",
            null,
        )
        val savedAgain = updatedRecordSignature(migrated, "history/new.json", 4)
        assertEquals(mapOf("history/new.json" to 4, "remarks/other.json" to 2), savedAgain)
        assertEquals(
            mapOf("remarks/other.json" to 2),
            updatedRecordSignature(savedAgain, "history/new.json", null),
        )
        assertNull(updatedRecordSignature<Int>(null, "history/new.json", 4))
        assertNull(updatedRecordSignature<Int>(null, "history/new.json", null))
    }

    @Test
    fun legacyPromotionKeepsNewestDuplicatesAndPreservesIdCollisions() {
        val histories =
            mergeHistoryRecords(
                external = listOf(NoteHistoryEntity(1, "note-a", "old", "a", 10)),
                room =
                    listOf(
                        NoteHistoryEntity(1, "note-a", "new", "b", 20),
                        NoteHistoryEntity(1, "note-b", "other", "c", 30),
                    ),
            )
        val remarks =
            mergeRemarkRecords(
                external = listOf(NoteRemarkEntity(1, "note-a", "old", 10, 10)),
                room =
                    listOf(
                        NoteRemarkEntity(1, "note-a", "new", 10, 20),
                        NoteRemarkEntity(1, "note-b", "other", 30, 30),
                    ),
            )

        assertEquals(listOf("new", "other"), histories.map { it.title })
        assertEquals(listOf("new", "other"), remarks.map { it.content })
        assertNotEquals(histories[0].id, histories[1].id)
        assertNotEquals(remarks[0].id, remarks[1].id)
    }
}
