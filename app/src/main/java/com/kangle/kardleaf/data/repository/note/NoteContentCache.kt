package com.kangle.kardleaf.data.repository.note

import android.content.Context
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import com.kangle.kardleaf.data.utils.KardLeafLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader

internal class NoteContentCache(private val context: Context) {
    private data class CachedText(
        val lastModified: Long,
        val length: Long,
        val text: String,
    )

    private data class CacheObservation(
        val event: String,
        val entryCount: Int,
        val totalChars: Long,
        val maxEntryChars: Int,
        val peakEntryChars: Int,
        val peakEntryCount: Int,
        val peakTotalChars: Long,
        val hitCount: Long,
        val missCount: Long,
        val putCount: Long,
        val replaceCount: Long,
        val evictionCount: Long,
        val readGateCount: Int,
        val peakReadGateCount: Int,
        val readGateCreatedCount: Long,
        val readGateRemovedCount: Long,
        val itemChars: Int,
        val pathHash: String,
    )

    private val mutex = Mutex()
    private val readLocks = mutableMapOf<String, Mutex>()
    private val entries = LinkedHashMap<String, CachedText>(64, 0.75f, true)
    private var totalChars = 0L
    private var maxEntryChars = 0
    private var peakEntryChars = 0
    private var peakEntryCount = 0
    private var peakTotalChars = 0L
    private var hitCount = 0L
    private var missCount = 0L
    private var putCount = 0L
    private var replaceCount = 0L
    private var evictionCount = 0L
    private var peakReadGateCount = 0
    private var readGateCreatedCount = 0L
    private var readGateRemovedCount = 0L
    private var eventsSinceSnapshot = 0
    private var lastLoggedPeakEntryChars = 0

    suspend fun read(file: DocumentFile, bypassCache: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            val pathKey = file.uri.toString()
            val lastModified = file.lastModified()
            val length = file.length()
            KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText start name=${file.name} length=$length lastModified=$lastModified bypassCache=$bypassCache uri=$pathKey")
            KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external readText start name=${file.name} length=$length lastModified=$lastModified bypassCache=$bypassCache uri=$pathKey")

            if (!bypassCache) {
                var observation: CacheObservation? = null
                val cached = mutex.withLock {
                    entries[pathKey]
                        ?.takeIf { it.lastModified == lastModified && it.length == length }
                        ?.also {
                            hitCount++
                            observation = snapshotIfDueLocked(it.text.length, pathHash(pathKey))
                        }
                }
                logObservation(observation)
                if (cached != null) {
                    KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText cache hit name=${file.name} length=$length textLen=${cached.text.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                    KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external readText cacheHit name=${file.name} length=$length textLen=${cached.text.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                    return@withContext cached.text
                }
            }

            var readGateObservation: CacheObservation? = null
            var readGateCreated = false
            val readLock = mutex.withLock {
                readLocks.getOrPut(pathKey) {
                    readGateCreated = true
                    Mutex()
                }.also {
                    if (readGateCreated) {
                        readGateCreatedCount++
                        if (readLocks.size > peakReadGateCount) {
                            peakReadGateCount = readLocks.size
                            if (peakReadGateCount.isPowerOfTwo()) {
                                readGateObservation = observationLocked("read_gate_peak", 0, pathHash(pathKey))
                            }
                        }
                    }
                }
            }
            logObservation(readGateObservation)
            readLock.withLock readTextLock@ {
                if (!bypassCache) {
                    var observation: CacheObservation? = null
                    val cached = mutex.withLock {
                        entries[pathKey]
                            ?.takeIf { it.lastModified == lastModified && it.length == length }
                            ?.also {
                                hitCount++
                                observation = snapshotIfDueLocked(it.text.length, pathHash(pathKey))
                            }
                    }
                    logObservation(observation)
                    if (cached != null) {
                        KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText cache hit after wait name=${file.name} length=$length textLen=${cached.text.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                        return@readTextLock cached.text
                    }
                }

                try {
                    if (!bypassCache) {
                        val observation = mutex.withLock {
                            missCount++
                            snapshotIfDueLocked(0, pathHash(pathKey))
                        }
                        logObservation(observation)
                    }
                    var text = readFromUri(file)
                    if (text != null && text.isEmpty() && length > 0L) {
                        KardLeafLog.w(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText empty result for non-empty file, retry name=${file.name} length=$length elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                        delay(80L)
                        val retryText = readFromUri(file)
                        if (!retryText.isNullOrEmpty()) text = retryText
                    }
                    if (text != null) {
                        if (text.isNotEmpty() || length == 0L) {
                            put(pathKey, lastModified, length, text)
                        } else {
                            KardLeafLog.w(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText skipped caching suspicious empty text name=${file.name} length=$length")
                        }
                        KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText done name=${file.name} fileLength=$length textLen=${text.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                        KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external readText done name=${file.name} fileLength=$length textLen=${text.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                        text
                    } else {
                        KardLeafLog.w(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText empty stream name=${file.name} length=$length elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
                        null
                    }
                } catch (e: Exception) {
                    KardLeafLog.e(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText failed name=${file.name} length=$length", e)
                    KardLeafLog.e("RoomNoteRepository", "Exception reading markdown.", e)
                    null
                }
            }
        }

    private fun readFromUri(file: DocumentFile): String? {
        val startMs = SystemClock.elapsedRealtime()
        val inputStream = context.contentResolver.openInputStream(file.uri) ?: run {
            KardLeafLog.w(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText openInputStream null name=${file.name.orEmpty()} elapsed=${SystemClock.elapsedRealtime() - startMs}ms uri=${file.uri}")
            return null
        }
        KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText openInputStream done name=${file.name.orEmpty()} elapsed=${SystemClock.elapsedRealtime() - startMs}ms uri=${file.uri}")
        KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external openInputStream done name=${file.name.orEmpty()} elapsed=${SystemClock.elapsedRealtime() - startMs}ms uri=${file.uri}")
        val readStartMs = SystemClock.elapsedRealtime()
        return inputStream.use { stream -> BufferedReader(stream.reader()).use { it.readText() } }.also { text ->
            KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText stream read done name=${file.name.orEmpty()} textLen=${text.length} readElapsed=${SystemClock.elapsedRealtime() - readStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external streamRead done name=${file.name.orEmpty()} textLen=${text.length} readElapsed=${SystemClock.elapsedRealtime() - readStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms")
        }
    }

    suspend fun update(file: DocumentFile, text: String) =
        put(file.uri.toString(), file.lastModified(), file.length(), text)

    private suspend fun put(pathKey: String, lastModified: Long, length: Long, text: String) {
        val observation = mutex.withLock {
            putCount++
            var evicted = false
            var removedCurrentMax = false
            if (entries.size >= MAX_ENTRIES) {
                entries.remove(entries.keys.first())?.let { removed ->
                    totalChars -= removed.text.length.toLong()
                    evictionCount++
                    evicted = true
                    removedCurrentMax = removed.text.length == maxEntryChars
                }
            }
            entries.put(pathKey, CachedText(lastModified, length, text))?.let { replaced ->
                totalChars -= replaced.text.length.toLong()
                replaceCount++
                removedCurrentMax = removedCurrentMax || replaced.text.length == maxEntryChars
            }
            totalChars += text.length.toLong()
            maxEntryChars =
                if (removedCurrentMax) entries.values.maxOfOrNull { it.text.length } ?: 0 else maxOf(maxEntryChars, text.length)
            val previousPeak = peakEntryChars
            peakEntryChars = maxOf(peakEntryChars, text.length)
            peakEntryCount = maxOf(peakEntryCount, entries.size)
            peakTotalChars = maxOf(peakTotalChars, totalChars)
            val snapshot = snapshotIfDueLocked(text.length, pathHash(pathKey))
            val logNewPeak =
                peakEntryChars > previousPeak &&
                    (lastLoggedPeakEntryChars == 0 || peakEntryChars.toLong() >= lastLoggedPeakEntryChars.toLong() * 2L)
            when {
                evicted -> observationLocked("eviction", text.length, pathHash(pathKey))
                logNewPeak -> {
                    lastLoggedPeakEntryChars = peakEntryChars
                    observationLocked("item_peak", text.length, pathHash(pathKey))
                }
                else -> snapshot
            }
        }
        logObservation(observation)
    }

    suspend fun clear() {
        val observation = mutex.withLock {
            entries.clear()
            totalChars = 0L
            maxEntryChars = 0
            observationLocked("clear", 0, "0")
        }
        logObservation(observation)
    }

    private fun snapshotIfDueLocked(itemChars: Int, pathHash: String): CacheObservation? {
        eventsSinceSnapshot++
        if (eventsSinceSnapshot < SNAPSHOT_INTERVAL) return null
        eventsSinceSnapshot = 0
        return observationLocked("snapshot", itemChars, pathHash)
    }

    private fun observationLocked(event: String, itemChars: Int, pathHash: String) =
        CacheObservation(
            event = event,
            entryCount = entries.size,
            totalChars = totalChars,
            maxEntryChars = maxEntryChars,
            peakEntryChars = peakEntryChars,
            peakEntryCount = peakEntryCount,
            peakTotalChars = peakTotalChars,
            hitCount = hitCount,
            missCount = missCount,
            putCount = putCount,
            replaceCount = replaceCount,
            evictionCount = evictionCount,
            readGateCount = readLocks.size,
            peakReadGateCount = peakReadGateCount,
            readGateCreatedCount = readGateCreatedCount,
            readGateRemovedCount = readGateRemovedCount,
            itemChars = itemChars,
            pathHash = pathHash,
        )

    private fun logObservation(observation: CacheObservation?) {
        observation ?: return
        KardLeafLog.d(
            CONTENT_CACHE_TRACE_TAG,
            "event=${observation.event} pathHash=${observation.pathHash} itemChars=${observation.itemChars} " +
                "entries=${observation.entryCount} totalChars=${observation.totalChars} " +
                "maxEntryChars=${observation.maxEntryChars} peakEntryChars=${observation.peakEntryChars} " +
                "peakEntries=${observation.peakEntryCount} peakTotalChars=${observation.peakTotalChars} " +
                "hits=${observation.hitCount} misses=${observation.missCount} puts=${observation.putCount} " +
                "replacements=${observation.replaceCount} evictions=${observation.evictionCount} " +
                "readGates=${observation.readGateCount} peakReadGates=${observation.peakReadGateCount} " +
                "readGateCreates=${observation.readGateCreatedCount} readGateRemoves=${observation.readGateRemovedCount}",
        )
    }

    private companion object {
        const val MAX_ENTRIES = 200
        const val SNAPSHOT_INTERVAL = 32
        const val CONTENT_CACHE_TRACE_TAG = "KardLeafContentCache"
        const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
        const val OPEN_PATH_PROBE_TAG = "KardLeafOpenPathProbe"

        fun pathHash(pathKey: String): String = pathKey.hashCode().toUInt().toString(16)

        fun Int.isPowerOfTwo(): Boolean = this > 0 && (this and (this - 1)) == 0
    }
}
