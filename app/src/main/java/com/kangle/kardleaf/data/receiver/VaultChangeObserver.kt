package com.kangle.kardleaf.data.receiver

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Watches the selected SAF vault URI and reports possible external file changes.
 *
 * SAF providers are not as predictable as normal file paths: some providers notify the tree URI,
 * some notify the root document URI, and some notify the children URI. Registering all supported
 * URI forms makes the observer more reliable without changing the repository/UI architecture.
 */
class VaultChangeObserver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onChanged: (Uri?) -> Unit,
) {
    private var observer: ContentObserver? = null
    private var debounceJob: Job? = null
    private var activeRootUri: Uri? = null
    private var pendingChangedUri: Uri? = null

    fun start(rootUri: Uri) {
        if (observer != null && activeRootUri == rootUri) {
            return
        }

        stop()
        activeRootUri = rootUri

        val newObserver =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    scheduleChanged(null)
                }

                override fun onChange(
                    selfChange: Boolean,
                    uri: Uri?,
                ) {
                    scheduleChanged(uri)
                }
            }

        observer = newObserver
        buildObserverUris(rootUri).forEach { uri ->
            runCatching {
                context.contentResolver.registerContentObserver(
                    uri,
                    true,
                    newObserver,
                )
            }.onFailure { error ->
            }
        }
    }

    private fun buildObserverUris(rootUri: Uri): List<Uri> {
        val uris = linkedSetOf(rootUri)

        runCatching {
            val treeDocumentId = DocumentsContract.getTreeDocumentId(rootUri)
            uris += DocumentsContract.buildDocumentUriUsingTree(rootUri, treeDocumentId)
            uris += DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, treeDocumentId)
        }.onFailure { error ->
        }

        return uris.toList()
    }

    private fun scheduleChanged(uri: Uri?) {
        if (uri != null) {
            pendingChangedUri = uri
        }
        debounceJob?.cancel()
        debounceJob =
            scope.launch {
                delay(DEBOUNCE_MS)
                val changedUri = pendingChangedUri
                pendingChangedUri = null
                onChanged(changedUri)
            }
    }

    fun stop() {
        if (observer != null || debounceJob != null) {
        }
        debounceJob?.cancel()
        debounceJob = null
        pendingChangedUri = null
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        activeRootUri = null
    }

    companion object {
        private const val TAG = "VaultChangeObserver"
        private const val DEBOUNCE_MS = 800L
    }
}
