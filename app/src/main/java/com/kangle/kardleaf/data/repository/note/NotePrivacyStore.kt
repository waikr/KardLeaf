package com.kangle.kardleaf.data.repository.note

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kangle.kardleaf.data.database.PrivacyNoteDao
import com.kangle.kardleaf.data.database.PrivacyNoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class NotePrivacyStore(private val dao: PrivacyNoteDao) {
    private data class Backup(
        val title: String = "",
        val content: String = "",
        val updatedAtMs: Long = 0L,
    )

    fun getAll(): Flow<List<PrivacyNoteEntity>> = dao.getAll()

    suspend fun save(id: Long, title: String, content: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (id > 0) {
            val existing = dao.getById(id)
            dao.upsert(
                (existing ?: PrivacyNoteEntity(id = id, title = title, content = content, updatedAtMs = now))
                    .copy(title = title, content = content, updatedAtMs = now),
            )
            id
        } else {
            dao.upsert(PrivacyNoteEntity(title = title, content = content, updatedAtMs = now))
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.deleteById(id) }

    suspend fun export(): String = withContext(Dispatchers.IO) {
        Gson().toJson(dao.getAllOnce().map { Backup(it.title, it.content, it.updatedAtMs) })
    }

    suspend fun import(json: String): Int = withContext(Dispatchers.IO) {
        val type = object : TypeToken<List<Backup>>() {}.type
        val list: List<Backup> = try {
            Gson().fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        list.forEach { dao.upsert(PrivacyNoteEntity(title = it.title, content = it.content, updatedAtMs = it.updatedAtMs)) }
        list.size
    }
}
