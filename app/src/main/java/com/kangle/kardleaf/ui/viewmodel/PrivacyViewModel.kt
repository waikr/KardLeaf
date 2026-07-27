package com.kangle.kardleaf.ui.viewmodel

import com.kangle.kardleaf.data.repository.RoomNoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class PrivacyViewModel(
    private val repository: RoomNoteRepository,
    private val scope: CoroutineScope,
) {
    val notes = repository.getAllPrivacyNotes().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(id: Long, title: String, content: String, onDone: () -> Unit) {
        scope.launch {
            repository.savePrivacyNote(id, title, content)
            onDone()
        }
    }

    fun saveAndReturnId(id: Long, title: String, content: String, onSaved: (Long) -> Unit) {
        scope.launch { onSaved(repository.savePrivacyNote(id, title, content)) }
    }

    fun delete(id: Long) {
        scope.launch { repository.deletePrivacyNote(id) }
    }

    fun export(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                onSuccess(repository.exportPrivacyNotes())
            } catch (e: Exception) {
                onError(e.message ?: "导出失败")
            }
        }
    }

    fun import(json: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                onSuccess(repository.importPrivacyNotes(json))
            } catch (e: Exception) {
                onError(e.message ?: "导入失败")
            }
        }
    }
}
