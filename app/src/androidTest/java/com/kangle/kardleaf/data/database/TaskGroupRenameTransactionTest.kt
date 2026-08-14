package com.kangle.kardleaf.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskGroupRenameTransactionTest {
    @Test
    fun renameGroupsKeepingIdsUpdatesNamesByPrimaryKey() = runBlocking {
        val database = newDatabase()
        try {
            val dao = database.taskDao()
            val root = TaskGroupEntity(id = 11L, name = "工作", sortOrder = 0, createdAt = 1L)
            val child = TaskGroupEntity(id = 12L, name = "工作/KardLeaf", sortOrder = 1, createdAt = 2L)
            dao.insertGroup(root)
            dao.insertGroup(child)

            dao.renameGroupsKeepingIds(
                listOf(root.copy(name = "开发"), child.copy(name = "开发/KardLeaf")),
            )

            assertEquals(
                listOf(root.copy(name = "开发"), child.copy(name = "开发/KardLeaf")),
                dao.getAllGroupsSnapshot(),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun renameGroupsKeepingIdsRollsBackWhenOneUpdateFails() = runBlocking {
        val database = newDatabase()
        try {
            val dao = database.taskDao()
            val root = TaskGroupEntity(id = 21L, name = "工作", sortOrder = 0, createdAt = 1L)
            val child = TaskGroupEntity(id = 22L, name = "工作/KardLeaf", sortOrder = 1, createdAt = 2L)
            dao.insertGroup(root)
            dao.insertGroup(child)

            var failed = false
            try {
                dao.renameGroupsKeepingIds(
                    listOf(root.copy(name = "开发"), child.copy(name = "开发/KardLeaf", sortOrder = root.sortOrder)),
                )
            } catch (_: Exception) {
                failed = true
            }

            assertTrue(failed)
            assertEquals(listOf(root, child), dao.getAllGroupsSnapshot())
        } finally {
            database.close()
        }
    }

    private fun newDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
}
