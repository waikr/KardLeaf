package com.kangle.kardleaf.data.sync

import kotlinx.coroutines.sync.Mutex

internal object S3SyncGate {
    val cloudMutex = Mutex()
    val fileMutex = Mutex()
    @Volatile var activeRoot: String? = null
}
