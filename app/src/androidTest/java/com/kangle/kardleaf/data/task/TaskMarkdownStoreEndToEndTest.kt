package com.kangle.kardleaf.data.task

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.TaskDao
import com.kangle.kardleaf.data.database.TaskEntity
import com.kangle.kardleaf.data.database.TaskGroupEntity
import com.kangle.kardleaf.data.repository.MetadataManager
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.repository.RoomNoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskMarkdownStoreEndToEndTest {
    private lateinit var context: Context
    private lateinit var prefs: PrefsManager
    private lateinit var database: AppDatabase
    private lateinit var repository: RoomNoteRepository
    private lateinit var store: TaskMarkdownStore
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = TestContext(instrumentation.targetContext, instrumentation.context)
        resetDocuments()
        prefs = PrefsManager(context)
        AppDatabase.closeDatabase()
        prefs.saveRootUri(TEST_ROOT_URI)
        databaseName = prefs.getActiveVaultDatabaseName()
        context.deleteDatabase(databaseName)
        database = AppDatabase.getDatabase(context)
        database.clearAllTables()
        repository = RoomNoteRepository(context, MetadataManager(context), prefs)
        assertTrue(runBlocking { repository.setRootFolderForQuickSave(TEST_ROOT_URI) })
        store = TaskMarkdownStore(context, repository, database.taskDao(), prefs)
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
        context.deleteDatabase(databaseName)
        prefs.removeVault(TEST_ROOT_URI)
        resetDocuments()
    }

    private fun resetDocuments() {
        context.contentResolver.call(
            Uri.parse("content://${TaskTestDocumentsProvider.AUTHORITY}"),
            "testReset",
            null,
            null,
        )
    }

    @Test
    fun saveGroupRenameKeepsIdsForAllNestedPathsAndTasksAfterSynchronize() = runBlocking {
        val fixture = createFixture()
        val saved = requireNotNull(store.saveGroup(fixture.root, "Build"))

        assertEquals(fixture.root.id, saved.id)
        assertGroupNames("Build", "Build/Android", "Build/Android/Compose")
        assertTaskGroups(fixture.tasks)
        assertMarkdownContains("## Build", "## Build/Android", "## Build/Android/Compose")
        assertMarkdownDoesNotContain("## Work", "## Work/Android", "## Work/Android/Compose")

        assertTrue(store.synchronize().success)
        assertTrue(store.synchronize().success)
        assertGroupNames("Build", "Build/Android", "Build/Android/Compose")
        assertTaskGroups(fixture.tasks)
        assertEquals(3, database.taskDao().getAllGroupsSnapshot().size)
    }

    @Test
    fun saveGroupRenameRecoveryReusesIdsAndRejectsConflicts() = runBlocking {
        val fixture = createFixture()
        val failingDao = FailOnceRenameTaskDao(database.taskDao())
        val failingStore = TaskMarkdownStore(context, repository, failingDao, prefs)

        assertNull(failingStore.saveGroup(fixture.root, "Build"))
        assertEquals(2, failingDao.renameCalls)
        assertGroupNames("Build", "Build/Android", "Build/Android/Compose")
        assertTaskGroups(fixture.tasks)
        assertMarkdownContains("## Build", "## Build/Android", "## Build/Android/Compose")
        assertMarkdownDoesNotContain("## Work", "## Work/Android", "## Work/Android/Compose")

        val groupsBeforeConflict = database.taskDao().getAllGroupsSnapshot()
        val tasksBeforeConflict = database.taskDao().getAllTasksSnapshot()
        val markdownBeforeConflict = managedMarkdown()
        val renamedRoot = groupsBeforeConflict.first { it.id == fixture.root.id }
        assertNull(failingStore.saveGroup(renamedRoot, "Build/Android"))
        assertEquals(groupsBeforeConflict, database.taskDao().getAllGroupsSnapshot())
        assertEquals(tasksBeforeConflict, database.taskDao().getAllTasksSnapshot())
        assertEquals(markdownBeforeConflict, managedMarkdown())

        assertTrue(failingStore.synchronize().success)
        assertTrue(failingStore.synchronize().success)
        assertGroupNames("Build", "Build/Android", "Build/Android/Compose")
        assertTaskGroups(fixture.tasks)
        assertEquals(3, database.taskDao().getAllGroupsSnapshot().size)
    }

    @Test
    fun refreshImportsExternalMarkdownAndKeepsCacheWhenScanFails() = runBlocking {
        val root = requireNotNull(DocumentFile.fromTreeUri(context, Uri.parse(TEST_ROOT_URI)))
        val external = requireNotNull(root.createFile("text/markdown", "syncthing.md"))
        context.contentResolver.openOutputStream(external.uri, "wt")!!.bufferedWriter().use {
            it.write("# Syncthing\n\nexternal content")
        }

        val imported = repository.refreshNotes()
        repository.isIndexing.first { indexing -> !indexing }
        assertTrue(imported.success)
        assertEquals(setOf("syncthing.md"), imported.addedPaths)
        assertNotNull(repository.getNote("syncthing.md"))

        context.contentResolver.call(
            Uri.parse("content://${TaskTestDocumentsProvider.AUTHORITY}"),
            "testFailNextChildQuery",
            null,
            null,
        )
        val failed = repository.refreshNotes()
        assertFalse(failed.success)
        assertNotNull(repository.getNote("syncthing.md"))
    }

    private suspend fun createFixture(): Fixture {
        val root = requireNotNull(store.saveGroup(null, "Work"))
        val child = requireNotNull(store.saveGroup(null, "Work/Android"))
        val grandchild = requireNotNull(store.saveGroup(null, "Work/Android/Compose"))
        val tasks =
            listOf(
                "root" to root.id,
                "child" to child.id,
                "grandchild" to grandchild.id,
            ).map { (text, groupId) ->
                requireNotNull(
                    store.saveTask(
                        original = null,
                        candidate = TaskEntity(taskText = text, groupId = groupId, createdAt = 1L, updatedAt = 1L),
                    ),
                )
            }
        return Fixture(root, tasks)
    }

    private suspend fun assertGroupNames(vararg names: String) {
        assertEquals(names.toList(), database.taskDao().getAllGroupsSnapshot().map(TaskGroupEntity::name))
    }

    private suspend fun assertTaskGroups(tasks: List<TaskEntity>) {
        val current = database.taskDao().getAllTasksSnapshot().associateBy(TaskEntity::id)
        tasks.forEach { task -> assertEquals(task.groupId, current.getValue(task.id).groupId) }
    }

    private suspend fun managedMarkdown(): String =
        requireNotNull(repository.getNote(TaskMarkdownStore.MANAGED_NOTE_PATH)).content

    private suspend fun assertMarkdownContains(vararg values: String) {
        val content = managedMarkdown()
        values.forEach { assertTrue("Missing Markdown heading: $it", content.contains(it)) }
    }

    private suspend fun assertMarkdownDoesNotContain(vararg values: String) {
        val content = managedMarkdown()
        values.forEach { assertFalse("Unexpected Markdown heading: $it", content.contains(it)) }
    }

    private data class Fixture(
        val root: TaskGroupEntity,
        val tasks: List<TaskEntity>,
    )

    private class FailOnceRenameTaskDao(
        private val delegate: TaskDao,
    ) : TaskDao by delegate {
        var renameCalls = 0
            private set

        override suspend fun renameGroupsKeepingIds(groups: List<TaskGroupEntity>) {
            renameCalls++
            if (renameCalls == 1) error("test Room rename failure")
            delegate.renameGroupsKeepingIds(groups)
        }
    }

    private class TestContext(
        base: Context,
        private val providerContext: Context,
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this

        override fun getContentResolver() = providerContext.contentResolver

        override fun checkCallingOrSelfUriPermission(uri: Uri, modeFlags: Int): Int =
            if (uri.authority == TaskTestDocumentsProvider.AUTHORITY) {
                PackageManager.PERMISSION_GRANTED
            } else {
                super.checkCallingOrSelfUriPermission(uri, modeFlags)
            }
    }

    companion object {
        private val TEST_ROOT_URI: String =
            DocumentsContract.buildTreeDocumentUri(
                TaskTestDocumentsProvider.AUTHORITY,
                TaskTestDocumentsProvider.ROOT_ID,
            ).toString()
    }
}
