package com.kangle.kardleaf.data.database

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppDatabaseMigrationTest {
    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java.canonicalName,
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migration6To18ValidatesCompleteSchema() {
        migrationHelper.createDatabase(FULL_CHAIN_DATABASE, 6).close()

        migrationHelper
            .runMigrationsAndValidate(
                FULL_CHAIN_DATABASE,
                CURRENT_DATABASE_VERSION,
                true,
                *ALL_MIGRATIONS,
            ).close()
    }

    @Test
    fun migration15To18PreservesVersion160NoteAndTaskData() {
        val note =
            NoteFixture(
                filePath = "compat/version-160.md",
                recordId = "compat-v160-record",
                title = "Version 1.6.0 compatibility",
                contentPreview = "Version 1.6.0 preview",
                content = "Version 1.6.0 body that must survive migration.",
            )
        migrationHelper.createDatabase(VERSION_160_DATABASE, VERSION_160_DATABASE_VERSION).apply {
            insertNote(note)
            execSQL(
                """
                INSERT INTO `tasks`
                    (`id`, `notePath`, `taskText`, `done`, `reminderAt`, `createdAt`, `updatedAt`)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(71L, note.filePath, "Keep this task", 0, 1_900_000_000_000L, 101L, 202L),
            )
            close()
        }

        migrationHelper
            .runMigrationsAndValidate(
                VERSION_160_DATABASE,
                CURRENT_DATABASE_VERSION,
                true,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
            ).use { database ->
                database.assertNotePreserved(note)
                database.query(
                    """
                    SELECT `notePath`, `taskText`, `done`, `reminderAt`, `createdAt`, `updatedAt`,
                           `groupId`, `priority`, `dueAt`, `repeatRule`, `notes`,
                           `reminderMode`, `reminderRing`, `reminderVibrate`
                    FROM `tasks`
                    WHERE `id` = 71
                    """.trimIndent(),
                ).use { cursor ->
                    assertTrue("The version 1.6.0 task was lost", cursor.moveToFirst())
                    assertEquals(note.filePath, cursor.getString(0))
                    assertEquals("Keep this task", cursor.getString(1))
                    assertEquals(0, cursor.getInt(2))
                    assertEquals(1_900_000_000_000L, cursor.getLong(3))
                    assertEquals(101L, cursor.getLong(4))
                    assertEquals(202L, cursor.getLong(5))
                    assertTrue("Migrated task groupId must remain null", cursor.isNull(6))
                    assertEquals(0, cursor.getInt(7))
                    assertTrue("Migrated task dueAt must remain null", cursor.isNull(8))
                    assertEquals("NONE", cursor.getString(9))
                    assertEquals("", cursor.getString(10))
                    assertEquals("POPUP", cursor.getString(11))
                    assertEquals(1, cursor.getInt(12))
                    assertEquals(1, cursor.getInt(13))
                }
            }
    }

    @Test
    fun migration16To18PreservesNoteAndCreatesNoteLinksSchema() {
        val note =
            NoteFixture(
                filePath = "compat/version-16.md",
                recordId = "compat-v16-record",
                title = "Version 16 compatibility",
                contentPreview = "Version 16 preview",
                content = "Version 16 body that must survive migration.",
            )
        migrationHelper.createDatabase(VERSION_16_DATABASE, 16).apply {
            insertNote(note)
            close()
        }

        migrationHelper
            .runMigrationsAndValidate(
                VERSION_16_DATABASE,
                CURRENT_DATABASE_VERSION,
                true,
                MIGRATION_16_17,
                MIGRATION_17_18,
            ).use { database ->
                database.assertNotePreserved(note)
                database.assertNoteLinksSchema()
            }
    }

    private fun SupportSQLiteDatabase.insertNote(note: NoteFixture) {
        execSQL(
            """
            INSERT INTO `notes` (
                `filePath`, `recordId`, `fileName`, `folder`, `title`, `contentPreview`, `content`,
                `lastModifiedMs`, `createdAtMs`, `color`, `reminder`, `isPinned`, `isFavorite`,
                `isArchived`, `isTrashed`, `deletedAtMs`, `firstImageReference`, `yamlTags`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                note.filePath,
                note.recordId,
                note.filePath.substringAfterLast('/'),
                "compat",
                note.title,
                note.contentPreview,
                note.content,
                1_700_000_000_123L,
                1_600_000_000_123L,
                0xFF336699L,
                null,
                1,
                1,
                0,
                0,
                null,
                "images/compat.png",
                "migration,compatibility",
            ),
        )
    }

    private fun SupportSQLiteDatabase.assertNotePreserved(note: NoteFixture) {
        query(
            """
            SELECT `recordId`, `title`, `contentPreview`, `content`, `folder`
            FROM `notes`
            WHERE `filePath` = ?
            """.trimIndent(),
            arrayOf(note.filePath),
        ).use { cursor ->
            assertTrue("The note at ${note.filePath} was lost", cursor.moveToFirst())
            assertEquals(note.recordId, cursor.getString(0))
            assertEquals(note.title, cursor.getString(1))
            assertEquals(note.contentPreview, cursor.getString(2))
            assertEquals(note.content, cursor.getString(3))
            assertEquals("compat", cursor.getString(4))
            assertFalse("Duplicate note rows were created", cursor.moveToNext())
        }
    }

    private fun SupportSQLiteDatabase.assertNoteLinksSchema() {
        query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'note_links'").use { cursor ->
            assertTrue("Table note_links is missing", cursor.moveToFirst())
        }

        val actualColumns = mutableListOf<String>()
        query("PRAGMA table_info(`note_links`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                actualColumns += cursor.getString(nameColumn)
            }
        }
        assertEquals("note_links columns differ from schema 17", NOTE_LINK_COLUMNS, actualColumns)

        val actualIndexes = mutableMapOf<String, Boolean>()
        query("PRAGMA index_list(`note_links`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            val uniqueColumn = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                actualIndexes[cursor.getString(nameColumn)] = cursor.getInt(uniqueColumn) != 0
            }
        }
        assertEquals("note_links index set differs from schema 17", NOTE_LINK_INDEXES.keys, actualIndexes.keys)
        NOTE_LINK_INDEXES.forEach { (name, columns) ->
            assertEquals("Index $name must not be unique", false, actualIndexes[name])
            val actualIndexColumns = mutableListOf<String>()
            query("PRAGMA index_info(`$name`)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) {
                    actualIndexColumns += cursor.getString(nameColumn)
                }
            }
            assertNotNull("Index $name is missing", actualIndexes[name])
            assertEquals("Index $name has the wrong columns", columns, actualIndexColumns)
        }
    }

    private data class NoteFixture(
        val filePath: String,
        val recordId: String,
        val title: String,
        val contentPreview: String,
        val content: String,
    )

    companion object {
        private const val CURRENT_DATABASE_VERSION = 18
        private const val VERSION_160_DATABASE_VERSION = 15
        private const val FULL_CHAIN_DATABASE = "migration-6-to-18"
        private const val VERSION_160_DATABASE = "migration-15-to-18"
        private const val VERSION_16_DATABASE = "migration-16-to-18"

        private val ALL_MIGRATIONS: Array<Migration> =
            arrayOf(
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18,
            )

        private val NOTE_LINK_COLUMNS =
            listOf(
                "id",
                "sourceRecordId",
                "sourcePath",
                "targetRaw",
                "targetNormalized",
                "targetRecordId",
                "targetPath",
                "alias",
                "heading",
                "blockId",
                "startOffset",
                "endOffset",
                "contextSnippet",
                "resolutionStatus",
            )

        private val NOTE_LINK_INDEXES =
            linkedMapOf(
                "index_note_links_source" to listOf("sourceRecordId", "sourcePath"),
                "index_note_links_target" to listOf("targetRecordId", "targetPath"),
                "index_note_links_target_normalized" to listOf("targetNormalized"),
            )
    }
}
