package com.kangle.kardleaf.data.repository.note

import com.kangle.kardleaf.data.database.NoteHistoryEntity
import com.kangle.kardleaf.data.database.NoteRemarkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NoteRecordExternalBackupTest {
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
