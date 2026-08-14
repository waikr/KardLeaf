package com.kangle.kardleaf.data.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RoomSearchQueryPolicyTest {
    @Test
    fun noteSearchUsesFullContentLike() {
        val source = readSource("src/main/java/com/kangle/kardleaf/data/database/NoteDao.kt")

        assertTrue(source.contains("OR content LIKE '%' || :query || '%'"))
    }

    @Test
    fun historySearchMatchesFullContentAndReturnsPreview() {
        val source = readSource("src/main/java/com/kangle/kardleaf/data/database/NoteHistoryDao.kt")

        assertTrue(source.contains("OR content LIKE '%' || :query || '%'"))
        assertTrue(source.contains("substr(content, 1, 200) AS content"))
    }

    @Test
    fun historySummaryDoesNotUseOrJoin() {
        val source = readSource("src/main/java/com/kangle/kardleaf/data/database/NoteHistoryDao.kt")

        assertFalse(source.contains(" OR n.recordId = h.noteId"))
        assertFalse(source.contains(" OR note_by_record.recordId = h.noteId"))
        assertTrue(source.contains("LEFT JOIN notes note_by_path ON note_by_path.filePath = h.noteId"))
        assertTrue(source.contains("LEFT JOIN notes note_by_record ON note_by_record.recordId = h.noteId"))
    }

    private fun readSource(path: String): String {
        val candidates = listOf(Path.of(path), Path.of("app").resolve(path))
        return candidates.first(Files::exists).toFile().readText()
    }
}
