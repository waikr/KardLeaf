package com.kangle.kardleaf.ui.viewmodel

import com.kangle.kardleaf.data.repository.RoomNoteRepository
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class SettingsViewModel(
    private val repository: RoomNoteRepository,
    private val scope: CoroutineScope,
) {
    fun exportUserDataBackup(onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                onSuccess(repository.exportUserDataBackup())
            } catch (e: Exception) {
                KardLeafLog.e("MainViewModel", "Failed to export user data backup", e)
                onError(e.message ?: "Export failed")
            }
        }
    }

    fun importUserDataBackup(json: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        scope.launch {
            try {
                repository.importUserDataBackup(json)
                repository.refreshNotes()
                onSuccess()
            } catch (e: Exception) {
                KardLeafLog.e("MainViewModel", "Failed to import user data backup", e)
                onError(e.message ?: "Import failed")
            }
        }
    }
}
