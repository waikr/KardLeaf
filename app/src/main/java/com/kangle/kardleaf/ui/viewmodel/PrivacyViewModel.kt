package com.kangle.kardleaf.ui.viewmodel

import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.repository.note.NotePrivacyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class PrivacyViewModel(
    private val repository: RoomNoteRepository,
    private val scope: CoroutineScope,
) {
    val notes = repository.getAllPrivacyNotes().stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun hasVault(onResult: (Boolean) -> Unit) {
        scope.launch { onResult(runCatching { repository.hasPrivacyVault() }.getOrDefault(true)) }
    }

    fun initialize(password: String, onResult: (Result<Unit>) -> Unit) {
        scope.launch { onResult(runCatching { repository.initializePrivacyVault(password) }) }
    }

    fun unlock(password: String, onResult: (Result<Unit>) -> Unit) {
        scope.launch { onResult(runCatching { repository.unlockPrivacyVault(password) }) }
    }

    fun prepareBiometricUnlock(onResult: (Result<NotePrivacyStore.BiometricUnlockRequest?>) -> Unit) {
        scope.launch { onResult(runCatching { repository.preparePrivacyBiometricUnlock() }) }
    }

    fun unlockWithBiometric(
        request: NotePrivacyStore.BiometricUnlockRequest,
        onResult: (Result<Unit>) -> Unit,
    ) {
        scope.launch { onResult(runCatching { repository.unlockPrivacyVaultWithBiometric(request) }) }
    }

    fun lock() {
        scope.launch { repository.lockPrivacyVault() }
    }

    fun save(id: Long, title: String, content: String, onDone: (Result<Long>) -> Unit) {
        scope.launch { onDone(runCatching { repository.savePrivacyNote(id, title, content) }) }
    }

    fun delete(id: Long, onDone: (Result<Unit>) -> Unit = {}) {
        scope.launch { onDone(runCatching { repository.deletePrivacyNote(id) }) }
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
