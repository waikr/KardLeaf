package com.kangle.kardleaf.data.repository

import com.kangle.kardleaf.data.utils.EditorOpenSession
import com.kangle.kardleaf.data.utils.KardLeafLog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.DocumentsContract
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.LabelDao
import com.kangle.kardleaf.data.database.LabelEntity
import com.kangle.kardleaf.data.database.NoteDao
import com.kangle.kardleaf.data.database.NoteEntity
import com.kangle.kardleaf.data.database.NoteMetadataEntity
import com.kangle.kardleaf.data.database.NoteLinkDao
import com.kangle.kardleaf.data.database.NoteLinkEntity
import com.kangle.kardleaf.data.database.NoteLinkResolutionStatus
import com.kangle.kardleaf.data.database.NoteRemarkDao
import com.kangle.kardleaf.data.database.NoteRemarkEntity
import com.kangle.kardleaf.data.database.NoteHistoryDao
import com.kangle.kardleaf.data.database.NoteHistoryEntity
import com.kangle.kardleaf.data.database.PrivacyNoteDao
import com.kangle.kardleaf.data.database.PrivacyNoteEntity
import com.kangle.kardleaf.data.model.AppConfig
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.model.NoteSearchMatch
import com.kangle.kardleaf.data.model.NoteSearchOptions
import com.kangle.kardleaf.data.utils.KardLeafContentLimits
import com.kangle.kardleaf.data.utils.LocalPreviewImageResource
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import com.kangle.kardleaf.data.utils.SearchQueryUtils
import com.kangle.kardleaf.ui.normalizeObsidianTarget
import com.kangle.kardleaf.ui.parseObsidianLinks
import com.kangle.kardleaf.ui.uniqueAnnotationBaseName
import com.kangle.kardleaf.ui.classifyDrawingSidecar
import com.kangle.kardleaf.ui.DrawingSidecarClassification
import com.kangle.kardleaf.ui.DrawingSidecarError
import com.kangle.kardleaf.ui.findSearchMatch
import com.kangle.kardleaf.data.repository.note.NoteContentCache
import com.kangle.kardleaf.data.repository.note.NoteBackupManager
import com.kangle.kardleaf.data.repository.note.NotePrivacyStore
import com.kangle.kardleaf.data.repository.note.PrivacyVaultCrypto
import com.kangle.kardleaf.data.repository.note.NoteHistoryStore
import com.kangle.kardleaf.data.repository.note.NoteRecordExternalBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.system.Os
import android.system.OsConstants
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.OutputStreamWriter
import android.os.SystemClock
import android.os.Process
import android.util.Base64
import android.util.LruCache
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ConcurrentHashMap

class RoomNoteRepository(
    private val context: Context,
    private val metadataManager: MetadataManager,
    private val prefsManager: PrefsManager,
) : NoteRepository {
    data class DeleteNotesResult(
        val successIds: List<String>,
        val failedIds: List<String>,
        val restorableIds: List<String> = successIds,
    ) {
        val successCount: Int get() = successIds.size
        val failedCount: Int get() = failedIds.size
    }

    data class MovedNotePath(
        val oldPath: String,
        val newPath: String,
    )

    private data class TrashMoveResult(
        val sourcePath: String,
        val trashPath: String,
    )

    private data class RecordKeyMove(
        val oldKey: String,
        val newKey: String,
        val movedHistory: Int,
        val movedRemarks: Int,
    )

    private data class MergeSource(
        val entity: NoteEntity,
        val file: DocumentFile,
        val rawContent: String,
        val note: Note,
    )

    data class NoteImage(
        val reference: String,
        val dataUri: String,
    )

    data class ImageViewerResource(
        val reference: String,
        val bitmap: Bitmap?,
        val mimeType: String?,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val exifOrientation: Int,
        val documentType: String,
        val drawingSource: String?,
        val editable: Boolean,
        val errorMessage: String? = null,
    )

    data class ImageEditorResource(
        val mode: String,
        val openedReference: String,
        val backgroundBitmap: Bitmap?,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val mimeType: String?,
        val exifOrientation: Int,
        val drawingSource: String?,
    )

    data class ImageAnnotationSaveResult(
        val reference: String,
        val newlyCreated: Boolean,
    )

    data class WikilinkCandidate(
        val id: String,
        val recordId: String,
        val title: String,
        val path: String,
        val folder: String,
    )

    data class WikilinkResolution(
        val status: String,
        val targetPath: String? = null,
        val targetRecordId: String? = null,
        val candidates: List<String> = emptyList(),
        val candidateDetails: List<WikilinkCandidate> = emptyList(),
    )

    private data class ReferencedDocument(
        val parent: DocumentFile,
        val file: DocumentFile,
        // provider 的 documentId 已验证为「父路径/文件名」结构，可用拼接直达子文件而无需目录枚举
        val viaDirectUri: Boolean = false,
    )

    private data class ResolvedImageReference(
        val fileUri: Uri,
        val parentUri: Uri?,
        val viaDirectUri: Boolean,
    )

    private data class LimitedImageRead(
        val bytes: ByteArray? = null,
        val exceededLimit: Boolean = false,
    )

    companion object {
        private const val NOTE_PREVIEW_CHAR_LIMIT = 200
        private const val SEARCH_RESULT_LIMIT = 100
        private const val LOCAL_WRITE_OBSERVER_COOLDOWN_MS = 1500L
        private const val STARTUP_PERF_TRACE_TAG = "KardLeafStartupPerf"
        private const val NOTE_THUMBNAIL_CACHE_MAX_BYTES = 24 * 1024 * 1024
        private const val THUMBNAIL_RESOLVE_TIMEOUT_MS = 15_000L
        private const val IMAGE_DATA_URI_CACHE_MAX_BYTES = 24 * 1024 * 1024
        private const val IMAGE_RESOLVE_PARALLELISM = 4
        private const val THUMBNAIL_RESOLVE_LOCK_STRIPES = 64
        private const val RESOLVED_IMAGE_REFERENCE_CACHE_SIZE = 256
        private const val YAML_TAG_TRACE_TAG = "KardLeafYamlTags"
        private const val SAVE_PATH_TRACE_TAG = "KardLeafSavePath"
        private const val FILE_TREE_TRACE_TAG = "KardLeafFileTree"
        private const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
        private const val OPEN_PATH_PROBE_TAG = "KardLeafOpenPathProbe"
        private const val USER_PERF_TRACE_TAG = "KardLeafUserPerf"
        private const val IMAGE_TRACE_TAG = "KardLeafImageTrace"
        private const val ENABLE_IMAGE_TRACE = false
        private const val ROOM_CONTENT_AUDIT_TAG = "KardLeafRoomContentAudit"
        private const val SEARCH_TRACE_TAG = "KardLeafSearchTrace"
        private const val HEATMAP_STATS_TAG = "KardLeafHeatmapStats"
        private const val HEATMAP_STATS_PREFS = "kardleaf_heatmap_stats"
        private const val HEATMAP_STATS_DAY_KEY = "character_count_day"
        private const val HEATMAP_STATS_ROOT_KEY = "character_count_root"
        private const val HEATMAP_STATS_VALUE_KEY = "character_count_value"
        private const val WIKILINK_PREFS = "kardleaf_wikilinks"
        private const val WIKILINK_REBUILD_V17_KEY = "rebuild_v17_done"
        private const val TRASH_FILE_MARKER = ".__kardleaf_trash__"
        private const val ARCHIVE_ROOT_PATH = ".KardLeaf/Archive"
        private const val TASK_STORE_FOLDER = ".KardLeaf"
        private const val TASK_STORE_FILE = "任务清单.md"
        private val MERGE_MANAGED_METADATA_KEYS = setOf(
            NoteFormatUtils.KARDLEAF_ID_KEY,
            "created",
            "updated",
            NoteFormatUtils.TAGS_KEY,
            NoteFormatUtils.SOURCE_TYPE_KEY,
            NoteFormatUtils.SOURCE_URL_KEY,
            NoteFormatUtils.NOTE_TYPE_KEY,
            "color",
            "reminder",
        )
    }

    private val database = AppDatabase.getDatabase(context)
    private val noteDao: NoteDao = database.noteDao()
    private val labelDao: LabelDao = database.labelDao()
    private val noteHistoryDao: NoteHistoryDao = database.noteHistoryDao()
    private val privacyNoteDao: PrivacyNoteDao = database.privacyNoteDao()
    private val noteRemarkDao: NoteRemarkDao = database.noteRemarkDao()
    private val noteLinkDao: NoteLinkDao = database.noteLinkDao()
    private val backupManager = NoteBackupManager(noteHistoryDao, noteRemarkDao, prefsManager)
    private val privacyStore = NotePrivacyStore(context, privacyNoteDao)
    private val historyStore = NoteHistoryStore(noteHistoryDao, prefsManager)
    private val recordExternalBackup = NoteRecordExternalBackup(
        context = context,
        database = database,
        historyDao = noteHistoryDao,
        remarkDao = noteRemarkDao,
        onExternalWrite = { lastLocalWriteElapsedMs = SystemClock.elapsedRealtime() },
    )
    private var rootDir: DocumentFile? = null
    private var rootTreeUri: Uri? = null
    private var rootDocumentId: String? = null
    private var appConfig = AppConfig()

    private data class FileSignature(
        val lastModified: Long,
        val length: Long,
    )

    private data class ThumbnailSourceSignature(
        val uri: String,
        val lastModified: Long,
        val length: Long,
        val strongMetadata: Boolean,
    )

    private data class HistorySnapshotContentSource(
        val rawContent: String? = null,
        val cleanContent: String? = null,
        val tags: List<String> = emptyList(),
        val fallbackReason: String? = null,
    )

    private val refreshMutex = Mutex()
    // ponytail: one vault-wide queue keeps dependent SAF mutations ordered; use keyed locks only if measured throughput requires it.
    private val fileTreeMutationMutex = Mutex()
    private val heatmapStatsMutex = Mutex()
    private val heatmapStatsPrefs = context.getSharedPreferences(HEATMAP_STATS_PREFS, Context.MODE_PRIVATE)
    private val wikilinkPrefs = context.getSharedPreferences(WIKILINK_PREFS, Context.MODE_PRIVATE)
    private val _libraryCharacterCount = MutableStateFlow(readCachedLibraryCharacterCount())
    val libraryCharacterCount: StateFlow<Long?> = _libraryCharacterCount.asStateFlow()
    private val pendingRefresh = AtomicBoolean(false)
    private val pendingRefreshForceReload = AtomicBoolean(false)
    private val refreshGeneration = AtomicLong(0L)
    private val completedRefreshResult = MutableStateFlow(RefreshResult(generation = 0L, success = false))
    private val indexingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val noteLinkSourceLocks = ConcurrentHashMap<String, Mutex>()
    private val noteLinkSourceVersions = ConcurrentHashMap<String, AtomicLong>()
    private val noteLinkResolutionMutex = Mutex()
    private val textCache = NoteContentCache(context)
    private val fileSignatures = mutableMapOf<String, FileSignature>()
    private val flowEmissionCounts = ConcurrentHashMap<String, Int>()
    private val roomContentAuditKeys = ConcurrentHashMap<String, Boolean>()
    private val thumbnailProbeSeq = AtomicLong(0L)
    private val thumbnailResolveLocks = Array(THUMBNAIL_RESOLVE_LOCK_STRIPES) { Mutex() }
    private val thumbnailSourceSignatures = ConcurrentHashMap<String, ThumbnailSourceSignature>()
    private val noteThumbnailCache =
        object : LruCache<String, Bitmap>(NOTE_THUMBNAIL_CACHE_MAX_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                value.byteCount.coerceAtLeast(1)
        }
    private val resolvedImageReferenceCache =
        LruCache<String, ResolvedImageReference>(RESOLVED_IMAGE_REFERENCE_CACHE_SIZE)
    private val imageDataUriCache =
        object : LruCache<String, String>(IMAGE_DATA_URI_CACHE_MAX_BYTES) {
            override fun sizeOf(key: String, value: String): Int =
                (value.length.toLong() * 2L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1)
        }
    private val imageResolveSemaphore = Semaphore(IMAGE_RESOLVE_PARALLELISM)
    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()
    private var lastLocalWriteElapsedMs = 0L

    private fun logStartupPerf(message: String) {
        KardLeafLog.d(STARTUP_PERF_TRACE_TAG, message)
    }

    private fun markWebDavRealtimeLocalDirty() {
        prefsManager.markWebDavRealtimeLocalDirty()
    }

    private fun logFlowEmission(name: String, size: Int, elapsedMs: Long) {
        val count = flowEmissionCounts.merge(name, 1) { old, one -> old + one } ?: 1
        if (count <= 20 || elapsedMs >= 16L) {
            logStartupPerf(
                "repository flow $name emit#$count size=$size mapElapsed=${elapsedMs}ms thread=${Thread.currentThread().name}",
            )
        }
    }

    private fun logRoomContentAuditOnce(
        key: String,
        message: String,
    ) {
        if (!isDebuggableBuild()) return
        if (roomContentAuditKeys.putIfAbsent(key, true) == null) {
            KardLeafLog.d(ROOM_CONTENT_AUDIT_TAG, message)
        }
    }

    private fun logRoomContentAudit(message: String) {
        if (isDebuggableBuild()) {
            KardLeafLog.d(ROOM_CONTENT_AUDIT_TAG, message)
        }
    }

    private fun isDebuggableBuild(): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun logLightweightListQueryOnce(
        name: String,
        size: Int,
    ) {
        logRoomContentAuditOnce(
            key = "light-list-$name",
            message = "list query uses lightweight Room projection source=$name rows=$size contentColumn=preview",
        )
    }

    private suspend fun getFullNoteEntityByPathForAudit(
        filePath: String,
        reason: String,
    ): NoteEntity? {
        logRoomContentAuditOnce(
            key = "select-star-notes-$reason",
            message = "remaining SELECT * notes path reason=$reason",
        )
        return noteDao.getNoteByPath(filePath)
    }

    override suspend fun setRootFolder(
        uriString: String,
        scanImmediately: Boolean,
    ) {
        setRootFolder(uriString, scanImmediately, scanWhenDatabaseEmpty = true)
    }

    suspend fun setRootFolder(
        uriString: String,
        scanImmediately: Boolean,
        scanWhenDatabaseEmpty: Boolean,
    ): Boolean =
        setRootFolderInternal(
            uriString = uriString,
            scanImmediately = scanImmediately,
            scanWhenDatabaseEmpty = scanWhenDatabaseEmpty,
        )

    suspend fun setRootFolderForQuickSave(uriString: String): Boolean =
        setRootFolderInternal(
            uriString = uriString,
            scanImmediately = false,
            scanWhenDatabaseEmpty = false,
        )

    private suspend fun setRootFolderInternal(
        uriString: String,
        scanImmediately: Boolean,
        scanWhenDatabaseEmpty: Boolean,
    ): Boolean {
        return try {
            val uri = Uri.parse(uriString)

            // Validate permission first
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                // Permission might already be granted or not persistable, proceed with caution
                KardLeafLog.w("RoomNoteRepository", "Could not take persistable permission: ${e.message}")
            }

            val resolvedRoot = resolveRootDocument(uri)
            val docFile = resolvedRoot?.documentFile
            val canRead = docFile?.canRead() == true
            if (resolvedRoot == null || docFile == null || !canRead) {
                KardLeafLog.e("RoomNoteRepository", "Root folder is not readable or null: $uriString")
                return false
            }

            rootDir = docFile
            rootTreeUri = resolvedRoot.treeUri
            rootDocumentId = resolvedRoot.documentId
            privacyStore.onRootChanged(docFile)
            resolvedImageReferenceCache.evictAll()
            invalidateThumbnailCaches()
            synchronized(imageDataUriCache) { imageDataUriCache.evictAll() }
            recordExternalBackup.onRootChanged(docFile)
            _libraryCharacterCount.value = readCachedLibraryCharacterCount()
            val rootName = docFile.name

            appConfig = metadataManager.loadConfig(docFile)

            if (!recordExternalBackup.loadFromExternalStore()) {
                KardLeafLog.w("RoomNoteRepository", "History/remarks external store load failed; keeping Room cache")
            }

            val dbCount = noteDao.countAllNotes()
            val willScan = scanImmediately || (scanWhenDatabaseEmpty && dbCount == 0)
            if (willScan) {
                refreshNotes()
            }
            true
        } catch (e: Exception) {
            KardLeafLog.e("RoomNoteRepository", "Error setting root folder: $uriString", e)
            false
        }
    }

    private data class ResolvedRootDocument(
        val documentFile: DocumentFile,
        val treeUri: Uri,
        val documentId: String,
    )

    private fun resolveRootDocument(uri: Uri): ResolvedRootDocument? {
        val requestedDocumentId = resolveRootDocumentId(uri) ?: return null
        val directResolved = resolveRootDocumentFromTreeUri(uri, requestedDocumentId)
        if (directResolved?.documentFile?.canRead() == true) return directResolved

        return context.contentResolver.persistedUriPermissions
            .asSequence()
            .filter { it.isReadPermission }
            .mapNotNull { permission -> resolveRootDocumentFromTreeUri(permission.uri, requestedDocumentId) }
            .firstOrNull()
    }

    private fun resolveRootDocumentFromTreeUri(
        treeUri: Uri,
        requestedDocumentId: String,
    ): ResolvedRootDocument? {
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        if (requestedDocumentId != treeDocumentId && !requestedDocumentId.startsWith("$treeDocumentId/")) {
            return null
        }
        val treeRoot = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val documentFile = if (requestedDocumentId == treeDocumentId) {
            treeRoot
        } else {
            val relativePath = requestedDocumentId.removePrefix("$treeDocumentId/")
            relativePath
                .split('/')
                .filter { it.isNotBlank() }
                .fold(treeRoot as DocumentFile?) { current, segment -> current?.findFile(segment) }
        } ?: return null
        return ResolvedRootDocument(
            documentFile = documentFile,
            treeUri = treeUri,
            documentId = requestedDocumentId,
        )
    }

    private fun currentRootDocumentId(): String? = rootDocumentId

    private fun findDocumentByDirectUri(
        relativePath: String,
        expectDirectory: Boolean,
    ): DocumentFile? {
        val treeUri = rootTreeUri ?: return null
        val rootId = currentRootDocumentId() ?: return null
        val normalized = relativePath.replace('\\', '/').trim('/')
        if (normalized.isBlank()) return rootDir
        val documentUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, "$rootId/$normalized")
        }.getOrNull() ?: return null
        val document = DocumentFile.fromSingleUri(context, documentUri) ?: return null
        val matchesType = runCatching {
            if (expectDirectory) document.isDirectory else document.isFile
        }.getOrDefault(false)
        return document.takeIf { matchesType }
    }

    private fun resolveRootDocumentId(uri: Uri): String? =
        runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()

    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllActiveNotes().map { entities ->
            logLightweightListQueryOnce("activeNotes", entities.size)
            val startMs = SystemClock.elapsedRealtime()
            val result = entities.map { it.toNote() }
            logFlowEmission("activeNotes", result.size, SystemClock.elapsedRealtime() - startMs)
            result
        }
    }

    suspend fun refreshLibraryCharacterCountIfDue(): Long? = withContext(Dispatchers.IO) {
        heatmapStatsMutex.withLock {
            val currentRoot = prefsManager.getRootUri().orEmpty()
            val cachedRoot = heatmapStatsPrefs.getString(HEATMAP_STATS_ROOT_KEY, null)
            val cachedValue = heatmapStatsPrefs.getLong(HEATMAP_STATS_VALUE_KEY, -1L).takeIf { it >= 0L }
            val todayKey = currentHeatmapStatsDayKey()
            val lastDayKey = heatmapStatsPrefs.getLong(HEATMAP_STATS_DAY_KEY, Long.MIN_VALUE)

            if (cachedRoot != currentRoot) {
                _libraryCharacterCount.value = null
            }
            if (currentRoot.isBlank() || rootDir == null || _isIndexing.value) {
                return@withLock if (cachedRoot == currentRoot) cachedValue else null
            }
            if (cachedRoot == currentRoot && lastDayKey == todayKey) {
                _libraryCharacterCount.value = cachedValue
                return@withLock cachedValue
            }

            val startedAt = SystemClock.elapsedRealtime()
            KardLeafLog.d(HEATMAP_STATS_TAG, "library character count start")
            val notes = noteDao.getAllNoteMetadataSync().filter { !it.isTrashed }
            var characterCount = 0L
            var countedFiles = 0
            var missingFiles = 0
            var failedFiles = 0
            val threadId = Process.myTid()
            val previousPriority = runCatching { Process.getThreadPriority(threadId) }.getOrNull()
            runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
            try {
                for (metadata in notes) {
                    currentCoroutineContext().ensureActive()
                    val entity = metadata.toLookupNoteEntity()
                    val file = findNoteDocumentByDirectUri(entity) ?: findNoteDocument(entity)
                    if (file == null) {
                        missingFiles++
                        continue
                    }
                    val count = countHeatmapCharacters(file)
                    if (count == null) {
                        failedFiles++
                    } else {
                        characterCount += count
                        countedFiles++
                    }
                }
            } finally {
                previousPriority?.let { priority ->
                    runCatching { Process.setThreadPriority(priority) }
                }
            }

            if (failedFiles > 0) {
                heatmapStatsPrefs.edit()
                    .putLong(HEATMAP_STATS_DAY_KEY, todayKey)
                    .putString(HEATMAP_STATS_ROOT_KEY, currentRoot)
                    .apply {
                        if (cachedRoot != currentRoot) {
                            remove(HEATMAP_STATS_VALUE_KEY)
                        }
                    }
                    .apply()
                KardLeafLog.w(
                    HEATMAP_STATS_TAG,
                    "library character count failed active=${notes.size} counted=$countedFiles missing=$missingFiles failed=$failedFiles " +
                        "elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
                )
                return@withLock if (cachedRoot == currentRoot) cachedValue else null
            }

            heatmapStatsPrefs.edit()
                .putLong(HEATMAP_STATS_DAY_KEY, todayKey)
                .putString(HEATMAP_STATS_ROOT_KEY, currentRoot)
                .putLong(HEATMAP_STATS_VALUE_KEY, characterCount)
                .apply()
            _libraryCharacterCount.value = characterCount
            KardLeafLog.d(
                HEATMAP_STATS_TAG,
                "library character count done active=${notes.size} counted=$countedFiles missing=$missingFiles " +
                    "characters=$characterCount elapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
            )
            characterCount
        }
    }

    override fun getAllNotesWithArchive(): Flow<List<Note>> {
        return noteDao.getAllNotesWithArchive().map { entities ->
            logLightweightListQueryOnce("allNotesWithArchive", entities.size)
            val startMs = SystemClock.elapsedRealtime()
            val result = entities.map { it.toNote() }
            logFlowEmission("allNotesWithArchive", result.size, SystemClock.elapsedRealtime() - startMs)
            result
        }
    }

    override fun getFavoriteNotes(): Flow<List<Note>> {
        return noteDao.getFavoriteNotes().map { entities ->
            logLightweightListQueryOnce("favoriteNotes", entities.size)
            entities.map { it.toNote() }
        }
    }

    fun getTrashedNotes(): Flow<List<Note>> {
        return noteDao.getTrashedNotes().map { entities ->
            logLightweightListQueryOnce("trashedNotes", entities.size)
            entities.map { it.toNote() }
        }
    }

    fun getArchivedNotes(): Flow<List<Note>> {
        return noteDao.getArchivedNotes().map { entities ->
            logLightweightListQueryOnce("archivedNotes", entities.size)
            entities.map { it.toNote() }
        }
    }

    fun getNotesByFolder(folder: String): Flow<List<Note>> {
        return noteDao.getNotesByFolder(folder).map { entities ->
            logLightweightListQueryOnce("notesByFolder", entities.size)
            val startMs = SystemClock.elapsedRealtime()
            val result = entities.map { it.toNote() }
            logFlowEmission("notesByFolder:$folder", result.size, SystemClock.elapsedRealtime() - startMs)
            result
        }
    }

    fun getNotesByFolderRecursive(folder: String): Flow<List<Note>> {
        val normalized = folder.trimEnd('/')
        return noteDao.getNotesByFolderRecursive(normalized, "$normalized/%").map { entities ->
            logLightweightListQueryOnce("notesByFolderRecursive", entities.size)
            val startMs = SystemClock.elapsedRealtime()
            val result = entities.map { it.toNote() }
            logFlowEmission("notesByFolderRecursive:$normalized", result.size, SystemClock.elapsedRealtime() - startMs)
            result
        }
    }

    override fun getLabels(): Flow<List<String>> = labelDao.getAllLabels()

    private fun logYamlTagTrace(message: String) {
        KardLeafLog.d(YAML_TAG_TRACE_TAG, message)
    }

    fun getYamlTags(): Flow<List<String>> =
        noteDao.getAllYamlTagRows().map { rows ->
            val result = rows.flatMap { NoteFormatUtils.tagsFromStorage(it) }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedWith(compareBy(java.lang.String.CASE_INSENSITIVE_ORDER) { it })
            logYamlTagTrace("getYamlTags rows=${rows.size} result=$result")
            result
        }

    fun getNotesByYamlTag(tag: String): Flow<List<Note>> {
        val needle = NoteFormatUtils.tagsToStorage(listOf(tag))
        return noteDao.getNotesByYamlTag(needle).map { entities ->
            logLightweightListQueryOnce("notesByYamlTag", entities.size)
            entities.map { it.toNote() }
        }
    }

    suspend fun updateNoteTags(
        notePath: String,
        tags: Collection<String>,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteShellByPath(notePath)
            if (entity == null) {
                logYamlTagTrace("updateNoteTags missing entity path=$notePath inputTags=${NoteFormatUtils.normalizeTags(tags)}")
                return@withContext false
            }
            val normalizedTags = NoteFormatUtils.normalizeTags(tags)
            logYamlTagTrace(
                "updateNoteTags path=$notePath oldDbTags=${NoteFormatUtils.tagsFromStorage(entity.yamlTags)} inputTags=$normalizedTags",
            )
            writeYamlTags(entity, normalizedTags)
        }

    suspend fun updateNoteTimestamps(
        notePath: String,
        createdAtMs: Long,
        updatedAtMs: Long,
    ): Note? =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteShellByPath(notePath) ?: return@withContext null
            val file = findNoteDocumentDirectFirst(entity, traceReason = "updateNoteTimestamps")
                ?: findDocumentByPath(notePath, traceReason = "updateNoteTimestamps.fallbackPath")
                ?: return@withContext null
            val rawContent = readText(file)
            val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
            val note = entity.toNote(
                updatedAtMs = NoteFormatUtils.extractUpdatedAt(frontMatter),
            ).copy(
                content = frontMatter.cleanContent,
                contentPreview = frontMatter.cleanContent.take(200),
                createdAt = Date(createdAtMs),
                updatedAt = Date(updatedAtMs),
            )
            val savedPath = saveNoteInternal(
                note = note,
                oldFile = note.file,
                saveHistory = false,
                preferDirectLookup = false,
                createdAtOverride = Date(createdAtMs),
                updatedAtOverride = Date(updatedAtMs),
            )
            if (savedPath.isBlank()) null else getNoteForEditor(savedPath)
        }

    suspend fun renameYamlTag(
        oldTag: String,
        newTag: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val oldNormalized = NoteFormatUtils.normalizeTags(listOf(oldTag)).firstOrNull() ?: return@withContext false
            val newNormalized = NoteFormatUtils.normalizeTags(listOf(newTag)).firstOrNull() ?: return@withContext false
            if (oldNormalized.equals(newNormalized, ignoreCase = true)) return@withContext false
            val entities = noteDao.getNotesByYamlTagSync(NoteFormatUtils.tagsToStorage(listOf(oldNormalized)))
            var changed = false
            entities.forEach { entity ->
                val updatedTags = NoteFormatUtils.tagsFromStorage(entity.yamlTags)
                    .map { tag -> if (tag.equals(oldNormalized, ignoreCase = true)) newNormalized else tag }
                changed = writeYamlTags(entity, updatedTags) || changed
            }
            changed
        }

    suspend fun deleteYamlTag(tag: String): Boolean =
        withContext(Dispatchers.IO) {
            val normalized = NoteFormatUtils.normalizeTags(listOf(tag)).firstOrNull() ?: return@withContext false
            val entities = noteDao.getNotesByYamlTagSync(NoteFormatUtils.tagsToStorage(listOf(normalized)))
            var changed = false
            entities.forEach { entity ->
                val updatedTags = NoteFormatUtils.tagsFromStorage(entity.yamlTags)
                    .filterNot { it.equals(normalized, ignoreCase = true) }
                changed = writeYamlTags(entity, updatedTags) || changed
            }
            changed
        }

    private suspend fun writeYamlTags(
        entity: NoteEntity,
        tags: Collection<String>,
    ): Boolean {
        val file = findNoteDocument(entity)
        if (file == null) {
            logYamlTagTrace("writeYamlTags fileNotFound path=${entity.filePath} title=${entity.title} inputTags=${NoteFormatUtils.normalizeTags(tags)} dbTags=${NoteFormatUtils.tagsFromStorage(entity.yamlTags)}")
            return false
        }
        val rawContent = readText(file)
        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
        val normalizedTags = NoteFormatUtils.normalizeTags(tags)
        val rawTags = NoteFormatUtils.extractTags(frontMatter)
        logYamlTagTrace(
            "writeYamlTags start path=${entity.filePath} title=${entity.title} rawTags=$rawTags dbTags=${NoteFormatUtils.tagsFromStorage(entity.yamlTags)} inputTags=$normalizedTags rawLen=${rawContent.length}",
        )
        val noteForFile = entity.toNote().copy(
            content = frontMatter.cleanContent,
            contentPreview = frontMatter.cleanContent.take(200),
            tags = normalizedTags,
        )
        val fullContent = NoteFormatUtils.constructFileContent(
            note = noteForFile,
            existingRawContent = rawContent,
            replaceTags = true,
        )
        val outputFrontMatter = NoteFormatUtils.parseFrontMatter(fullContent)
        val outputTags = NoteFormatUtils.extractTags(outputFrontMatter)
        logYamlTagTrace(
            "writeYamlTags prepared path=${entity.filePath} outputTags=$outputTags outputLen=${fullContent.length}",
        )
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer -> writer.write(fullContent) }
        } ?: return false

        lastLocalWriteElapsedMs = SystemClock.elapsedRealtime()
        updateTextCache(file, fullContent)
        val writtenLastModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        noteDao.insertNote(
            entity.copy(
                recordId = NoteFormatUtils.extractKardLeafId(outputFrontMatter) ?: entity.filePath,
                contentPreview = frontMatter.cleanContent.take(200),
                content = frontMatter.cleanContent,
                lastModifiedMs = writtenLastModified,
                createdAtMs = NoteFormatUtils.extractCreatedAt(outputFrontMatter) ?: entity.createdAtMs,
                yamlTags = NoteFormatUtils.tagsToStorage(normalizedTags),
            ),
        )
        fileSignatures[entity.filePath] = FileSignature(writtenLastModified, file.length())
        markWebDavRealtimeLocalDirty()
        logYamlTagTrace(
            "writeYamlTags done path=${entity.filePath} savedDbTags=${NoteFormatUtils.tagsFromStorage(NoteFormatUtils.tagsToStorage(normalizedTags))} lastModified=$writtenLastModified length=${file.length()}",
        )
        return true
    }

    override suspend fun createLabel(name: String): Boolean =
        withContext(Dispatchers.IO) {
            fileTreeMutationMutex.withLock {
                val root = rootDir ?: return@withLock false
                val path = normalizeFolderPath(name)
                if (path.isBlank()) return@withLock false
                val parentPath = path.substringBeforeLast("/", missingDelimiterValue = "")
                val folderName = path.substringAfterLast("/")
                val parent = findFolder(root, parentPath) ?: return@withLock false
                val siblings = parent.listFiles()
                if (siblings.any { it.name == folderName && !it.isDirectory }) return@withLock false
                val existing = siblings.firstOrNull { it.name == folderName && it.isDirectory }
                val directory = existing ?: parent.createDirectory(folderName) ?: return@withLock false
                val roomSucceeded = runCatching { labelDao.insert(LabelEntity(path)) }.isSuccess
                if (!roomSucceeded) {
                    if (existing == null) runCatching { directory.delete() }
                    return@withLock false
                }
                if (existing == null) markWebDavRealtimeLocalDirty()
                true
            }
        }

    override suspend fun deleteLabel(name: String): Boolean =
        withContext(Dispatchers.IO) {
            fileTreeMutationMutex.withLock {
                val root = rootDir ?: return@withLock false
                val count = noteDao.countNotesInFolder(name)
                if (count > 0) return@withLock false

                deleteFolder(root, name)
                getTrashRoot(root, create = false)?.let { deleteFolder(it, name) }
                if (prefsManager.getTrashFolderName() != "Trash") {
                    root.findFile("Trash")?.let { deleteFolder(it, name) }
                }

                labelDao.delete(name)
                markWebDavRealtimeLocalDirty()
                true
            }
        }

    override suspend fun deleteLabelWithContents(name: String): Boolean =
        withContext(Dispatchers.IO) {
            fileTreeMutationMutex.withLock {
                val root = rootDir ?: return@withLock false
                val folder = normalizeFolderPath(name)
                if (folder.isBlank()) return@withLock false

                val folderPrefix = "$folder/%"
                val entities = noteDao.getNoteShellsInFolderTree(folder)
                    .filter { !it.isTrashed }
                if (entities.isNotEmpty()) {
                    val movedNotes = moveNoteEntitiesToTrash(entities)
                    if (movedNotes.isNotEmpty()) {
                        movedNotes.forEach { prefsManager.setNotePinned(it.sourcePath, false) }
                        movedNotes.forEach { prefsManager.setNoteFavorite(it.sourcePath, false) }
                    }
                    if (movedNotes.size != entities.size) return@withLock false
                }

                deleteFolder(root, folder)
                labelDao.deleteTree(folder, folderPrefix)
                markWebDavRealtimeLocalDirty()
                true
            }
        }

    override suspend fun renameLabel(
        oldName: String,
        newName: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            fileTreeMutationMutex.withLock {
                renameLabelLocked(oldName, newName)
            }
        }

    private suspend fun renameLabelLocked(oldName: String, newName: String): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        val root = rootDir ?: return false
        val oldPath = normalizeFolderPath(oldName)
        val newPath = normalizeFolderPath(newName)
        if (!isValidFolderRelocation(oldPath, newPath)) return false

        val oldParent = oldPath.substringBeforeLast("/", missingDelimiterValue = "")
        val newParent = newPath.substringBeforeLast("/", missingDelimiterValue = "")
        val oldSegment = oldPath.substringAfterLast("/")
        val newSegment = newPath.substringAfterLast("/")
        val sameParent = oldParent == newParent
        val sourceParent = findFolder(root, oldParent) ?: return false
        val targetParent = findFolder(root, newParent) ?: return false
        val folder = sourceParent.findFile(oldSegment)?.takeIf { it.isDirectory } ?: return false
        if (targetParent.findFile(newSegment)?.uri?.let { it != folder.uri } == true) return false
        val affectedNotes = noteDao.getNoteShellsInFolderTree(oldPath).filter { sameParent || !it.isTrashed }

        val safStartedAt = SystemClock.elapsedRealtime()
        val relocatedFolder = runCatching {
            relocateDocument(folder, sourceParent, targetParent, oldSegment, newSegment)
        }.onFailure {
            KardLeafLog.e(FILE_TREE_TRACE_TAG, "relocate folder SAF failed oldHash=${oldPath.hashCode()}", it)
        }.getOrNull() ?: return false

        val trashParent = if (sameParent) getTrashRoot(root, create = false)?.let { findFolder(it, oldParent) } else null
        val trashFolder = trashParent?.findFile(oldSegment)?.takeIf { it.isDirectory && it.name != newSegment }
        val trashRenamed = if (trashParent != null && trashFolder != null) {
            runCatching { renameFolderDocument(trashParent, trashFolder, oldSegment, newSegment) }.getOrDefault(false)
        } else {
            false
        }
        if (trashFolder != null && !trashRenamed) {
            val rolledBack = runCatching {
                relocateDocument(relocatedFolder, targetParent, sourceParent, newSegment, oldSegment)
            }.getOrNull() != null
            KardLeafLog.e(
                FILE_TREE_TRACE_TAG,
                "relocate trash mirror failed oldHash=${oldPath.hashCode()} activeRolledBack=$rolledBack",
            )
            return false
        }

        val recordMoves = mutableListOf<RecordKeyMove>()
        val roomSucceeded = runCatching {
            database.withTransaction {
                if (sameParent) {
                    noteDao.renameFolderPaths(oldPath, newPath)
                    noteLinkDao.renameFolderSourcePaths(oldPath, newPath)
                } else {
                    noteLinkDao.moveActiveFolderSourcePaths(oldPath, newPath)
                    noteDao.moveActiveFolderPaths(oldPath, newPath)
                }
                labelDao.renameTree(oldPath, newPath)
                noteLinkDao.markFolderTargetsUnresolved(oldPath)
                affectedNotes.filter { it.recordId == it.filePath }.forEach { note ->
                    val newNotePath = remapTreePath(note.filePath, oldPath, newPath)
                    recordMoves += RecordKeyMove(
                        oldKey = note.filePath,
                        newKey = newNotePath,
                        movedHistory = noteHistoryDao.replaceNoteId(note.filePath, newNotePath),
                        movedRemarks = noteRemarkDao.replaceNoteId(note.filePath, newNotePath),
                    )
                }
            }
        }.onFailure { error ->
            KardLeafLog.e(FILE_TREE_TRACE_TAG, "rename folder Room update failed oldHash=${oldPath.hashCode()}", error)
        }.isSuccess

        if (!roomSucceeded) {
            runCatching { relocateDocument(relocatedFolder, targetParent, sourceParent, newSegment, oldSegment) }
            if (trashRenamed && trashParent != null) {
                trashParent.findFile(newSegment)?.let { runCatching { renameFolderDocument(trashParent, it, newSegment, oldSegment) } }
            }
            return false
        }

        recordMoves.forEach { move ->
            syncRecordStoreAfterKeyChange(
                move.movedHistory,
                move.movedRemarks,
                move.oldKey,
                move.newKey,
            )
        }
        remapFileTreeCaches(oldPath, newPath)
        val affectedTargets = affectedNotes.flatMap { note ->
            listOf(note.filePath, remapTreePath(note.filePath, oldPath, newPath))
        }
        indexingScope.launch { runCatching { reconcileWikilinkTargets(affectedTargets) } }
        markWebDavRealtimeLocalDirty()
        KardLeafLog.d(
            FILE_TREE_TRACE_TAG,
            "rename folder repo done oldHash=${oldPath.hashCode()} notes=${affectedNotes.size} " +
                "safElapsed=${SystemClock.elapsedRealtime() - safStartedAt}ms totalElapsed=${SystemClock.elapsedRealtime() - startedAt}ms",
        )
        return true
    }

    /** Renames only the Markdown document and its derived cache keys; file contents are never rewritten. */
    suspend fun renameNoteFile(notePath: String, requestedTitle: String): String =
        withContext(Dispatchers.IO) {
            fileTreeMutationMutex.withLock {
                renameNoteFileLocked(notePath, requestedTitle)
            }
        }

    private suspend fun renameNoteFileLocked(notePath: String, requestedTitle: String): String {
        val root = rootDir ?: return ""
        val oldPath = normalizeFolderPath(notePath)
        val oldEntity = noteDao.getNoteShellByPath(oldPath) ?: return ""
        val folderPath = oldPath.substringBeforeLast("/", missingDelimiterValue = "")
        val oldFileName = oldPath.substringAfterLast("/")
        val parent = findFolder(root, folderPath) ?: return ""
        val siblings = parent.listFiles()
        val source = findNoteDocumentByDirectUri(oldEntity, "file-tree-rename")
            ?: siblings.firstOrNull { it.name == oldFileName && it.isFile }
            ?: return ""

        val baseTitle = NoteFormatUtils.sanitizeMarkdownFileBaseName(requestedTitle)
        var finalTitle = baseTitle
        var finalFileName = "$finalTitle.md"
        var target = siblings.firstOrNull { it.name == finalFileName }
        var counter = 1
        while (target != null && target.uri != source.uri) {
            finalTitle = "$baseTitle($counter)"
            finalFileName = "$finalTitle.md"
            target = siblings.firstOrNull { it.name == finalFileName }
            counter++
        }
        val newPath = joinPath(folderPath, finalFileName)
        if (newPath == oldPath) return oldPath

        val renamed = runCatching { renameFolderDocument(parent, source, oldFileName, finalFileName) }
            .onFailure { KardLeafLog.e(FILE_TREE_TRACE_TAG, "rename note SAF failed pathHash=${oldPath.hashCode()}", it) }
            .getOrDefault(false)
        if (!renamed) return ""

        var movedHistory = 0
        var movedRemarks = 0
        val lastModified = source.lastModified().takeIf { it > 0L } ?: oldEntity.lastModifiedMs
        val roomSucceeded = runCatching {
            database.withTransaction {
                check(noteDao.renameNotePath(oldPath, newPath, finalFileName, finalTitle, lastModified) == 1) {
                    "Missing Room note for $oldPath"
                }
                noteLinkDao.renameSourcePath(oldPath, newPath)
                noteLinkDao.markTargetUnresolved(oldPath, oldEntity.recordId)
                if (oldEntity.recordId == oldPath) {
                    movedHistory = noteHistoryDao.replaceNoteId(oldPath, newPath)
                    movedRemarks = noteRemarkDao.replaceNoteId(oldPath, newPath)
                }
            }
        }.onFailure { error ->
            KardLeafLog.e(FILE_TREE_TRACE_TAG, "rename note Room update failed pathHash=${oldPath.hashCode()}", error)
        }.isSuccess

        if (!roomSucceeded) {
            val renamedFile = parent.findFile(finalFileName) ?: source
            runCatching { renameFolderDocument(parent, renamedFile, finalFileName, oldFileName) }
            return ""
        }

        prefsManager.replacePinnedNotePath(oldPath, newPath)
        prefsManager.replaceFavoriteNotePath(oldPath, newPath)
        fileSignatures.remove(oldPath)?.let { fileSignatures[newPath] = it }
        syncRecordStoreAfterKeyChange(movedHistory, movedRemarks, oldPath, newPath)
        val affectedTargets = listOf(
            oldEntity.title,
            oldEntity.fileName,
            oldPath,
            finalTitle,
            finalFileName,
            newPath,
        )
        indexingScope.launch { runCatching { reconcileWikilinkTargets(affectedTargets) } }
        markWebDavRealtimeLocalDirty()
        return newPath
    }

    /** Moves one file-tree Markdown document without rewriting its contents. */
    suspend fun moveNoteFile(notePath: String, targetFolder: String): String =
        withContext(Dispatchers.IO) {
            fileTreeMutationMutex.withLock {
                moveNoteFileLocked(notePath, targetFolder)
            }
        }

    private suspend fun moveNoteFileLocked(notePath: String, targetFolder: String): String {
        val root = rootDir ?: return ""
        val oldPath = normalizeFolderPath(notePath)
        val oldEntity = noteDao.getNoteShellByPath(oldPath)?.takeIf { !it.isTrashed && !it.isArchived } ?: return ""
        val oldFolder = oldPath.substringBeforeLast("/", missingDelimiterValue = "")
        val fileName = oldPath.substringAfterLast("/")
        val normalizedTarget = normalizeFolderPath(targetFolder)
        val newPath = resolveFileTreeMovePath(oldPath, normalizedTarget) ?: return ""
        val logicalSourceParent = findFolder(root, oldFolder) ?: return ""
        val source = findNoteDocumentByDirectUri(oldEntity, "file-tree-move")
            ?: logicalSourceParent.findFile(fileName)?.takeIf { it.isFile }
            ?: return ""
        val sourceParent = listOfNotNull(
            logicalSourceParent.findFile("Pinned")?.takeIf { it.isDirectory },
            logicalSourceParent,
        ).firstOrNull { parent -> parent.findFile(fileName)?.uri == source.uri } ?: return ""
        val targetParent = findFolder(root, normalizedTarget) ?: return ""
        val targetConflict = targetParent.findFile(fileName) != null ||
            targetParent.findFile("Pinned")?.findFile(fileName) != null
        if (targetConflict) return ""

        val movedFile = runCatching {
            relocateDocument(source, sourceParent, targetParent, fileName, fileName)
        }.onFailure {
            KardLeafLog.e(FILE_TREE_TRACE_TAG, "move note SAF failed pathHash=${oldPath.hashCode()}", it)
        }.getOrNull() ?: return ""

        var movedHistory = 0
        var movedRemarks = 0
        val lastModified = movedFile.lastModified().takeIf { it > 0L } ?: oldEntity.lastModifiedMs
        val roomSucceeded = runCatching {
            database.withTransaction {
                check(noteDao.moveNotePath(oldPath, newPath, normalizedTarget, lastModified) == 1) {
                    "Missing Room note for $oldPath"
                }
                noteLinkDao.renameSourcePath(oldPath, newPath)
                noteLinkDao.markTargetUnresolved(oldPath, oldEntity.recordId)
                if (oldEntity.recordId == oldPath) {
                    movedHistory = noteHistoryDao.replaceNoteId(oldPath, newPath)
                    movedRemarks = noteRemarkDao.replaceNoteId(oldPath, newPath)
                }
            }
        }.onFailure { error ->
            KardLeafLog.e(FILE_TREE_TRACE_TAG, "move note Room update failed pathHash=${oldPath.hashCode()}", error)
        }.isSuccess

        if (!roomSucceeded) {
            val rolledBack = runCatching {
                relocateDocument(movedFile, targetParent, sourceParent, fileName, fileName)
            }.getOrNull() != null
            KardLeafLog.e(FILE_TREE_TRACE_TAG, "move note rolled back pathHash=${oldPath.hashCode()} success=$rolledBack")
            return ""
        }

        prefsManager.replacePinnedNotePath(oldPath, newPath)
        prefsManager.replaceFavoriteNotePath(oldPath, newPath)
        fileSignatures.remove(oldPath)?.let { fileSignatures[newPath] = it }
        syncRecordStoreAfterKeyChange(movedHistory, movedRemarks, oldPath, newPath)
        indexingScope.launch { runCatching { reconcileWikilinkTargets(listOf(oldPath, newPath)) } }
        markWebDavRealtimeLocalDirty()
        return newPath
    }

    private fun remapFileTreeCaches(oldFolder: String, newFolder: String) {
        val movedSignatures = fileSignatures
            .filterKeys { it == oldFolder || it.startsWith("$oldFolder/") }
            .mapKeys { (path, _) -> remapTreePath(path, oldFolder, newFolder) }
        fileSignatures.keys.removeAll { it == oldFolder || it.startsWith("$oldFolder/") }
        fileSignatures.putAll(movedSignatures)
        prefsManager.replacePinnedNotePaths(
            prefsManager.getPinnedNotePaths().map { remapTreePath(it, oldFolder, newFolder) },
        )
        prefsManager.replaceFavoriteNotePaths(
            prefsManager.getFavoriteNotePaths().map { remapTreePath(it, oldFolder, newFolder) },
        )
    }

    /**
     * Returns the Room copy only. This is used by the editor open path so the
     * screen can be shown immediately, without blocking navigation on SAF file IO.
     * A normal getNote() call can still run afterwards to verify the file version.
     */
    suspend fun getCachedNote(id: String): Note? {
        return withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "repo getCachedNote start path=$id")
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "internal cachedNote start path=$id thread=${Thread.currentThread().name}",
            )
            // Do not read the full `content` column here. Very large notes can exceed
            // Android CursorWindow's per-row limit when Room executes SELECT *.
            val result = noteDao.getNoteShellByPath(id)?.toNote()
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo getCachedNote done path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "contentLen=${result?.content?.length ?: -1} previewLen=${result?.contentPreview?.length ?: -1}",
            )
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "internal cachedNote done path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "ok=${result != null} folder=${result?.folder} contentLen=${result?.content?.length ?: -1} " +
                    "previewLen=${result?.contentPreview?.length ?: -1} thread=${Thread.currentThread().name}",
            )
            result
        }
    }

    suspend fun getNoteForEditor(
        id: String,
        openSession: EditorOpenSession? = null,
    ): Note? {
        return withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo getNoteForEditor start path=$id ${openSession?.trace() ?: "sessionId=-1"}",
            )
            openSession?.let { KardLeafLog.d(USER_PERF_TRACE_TAG, "editorOpen repositoryLoadStart ${it.trace()}") }
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "editorLoad start path=$id parent=${normalizeFolderPath(id.substringBeforeLast("/", missingDelimiterValue = ""))} " +
                    "thread=${Thread.currentThread().name}",
            )
            // Editor opening should load metadata from Room and full text from the
            // markdown file, instead of selecting a huge cached content column.
            val entityQueryStartMs = SystemClock.elapsedRealtime()
            val entity = noteDao.getNoteShellByPath(id) ?: run {
                KardLeafLog.w(LARGE_NOTE_OPEN_TRACE_TAG, "repo getNoteForEditor no entity path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms entityQueryElapsed=${SystemClock.elapsedRealtime() - entityQueryStartMs}ms")
                KardLeafLog.w(
                    OPEN_PATH_PROBE_TAG,
                    "internal entity missing path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                        "entityQueryElapsed=${SystemClock.elapsedRealtime() - entityQueryStartMs}ms",
                )
                return@withContext null
            }
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo getNoteForEditor entity query done path=$id entityQueryElapsed=${SystemClock.elapsedRealtime() - entityQueryStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo getNoteForEditor entity path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "entityContentLen=${entity.content.length} entityPreviewLen=${entity.contentPreview.length}",
            )
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "internal entity done path=$id folder=${entity.folder} fileName=${entity.fileName} archived=${entity.isArchived} " +
                    "trashed=${entity.isTrashed} pinned=${entity.isPinned} entityQueryElapsed=${SystemClock.elapsedRealtime() - entityQueryStartMs}ms " +
                    "contentLen=${entity.content.length} previewLen=${entity.contentPreview.length}",
            )
            val findFileStartMs = SystemClock.elapsedRealtime()
            KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external locate start path=$id folder=${entity.folder} fileName=${entity.fileName}")
            val file = findNoteDocumentDirectFirst(entity, traceReason = "getNoteForEditor")
                ?: findDocumentByPath(id, traceReason = "getNoteForEditor.fallbackPath")
                ?: run {
                    KardLeafLog.w(
                        LARGE_NOTE_OPEN_TRACE_TAG,
                        "repo getNoteForEditor no file path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                            "findFileElapsed=${SystemClock.elapsedRealtime() - findFileStartMs}ms",
                    )
                    KardLeafLog.w(
                        OPEN_PATH_PROBE_TAG,
                        "external locate missing path=$id folder=${entity.folder} fileName=${entity.fileName} " +
                            "findFileElapsed=${SystemClock.elapsedRealtime() - findFileStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                    )
                    return@withContext null
                }
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo getNoteForEditor find file done path=$id findFileElapsed=${SystemClock.elapsedRealtime() - findFileStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo getNoteForEditor file path=$id name=${file.name} length=${file.length()} lastModified=${file.lastModified()} " +
                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external locate done path=$id fileName=${file.name} uri=${file.uri} " +
                    "length=${file.length()} lastModified=${file.lastModified()} " +
                    "findFileElapsed=${SystemClock.elapsedRealtime() - findFileStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            val readResult = readNoteFromFileForEditor(entity, file) ?: return@withContext null
            val result =
                readResult.entity.toNote(
                    noteType = readResult.noteType,
                    sourceType = readResult.sourceType,
                    sourceUrl = readResult.sourceUrl,
                    updatedAtMs = readResult.updatedAtMs,
                )
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo getNoteForEditor done path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "contentLen=${result.content.length} previewLen=${result.contentPreview.length} " +
                    (openSession?.trace(result.content.length) ?: "sessionId=-1"),
            )
            openSession?.let { KardLeafLog.d(USER_PERF_TRACE_TAG, "editorOpen repositoryLoadDone ${it.trace(result.content.length)}") }
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "editorLoad done path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "contentLen=${result.content.length} previewLen=${result.contentPreview.length} folder=${result.folder}",
            )
            result
        }
    }

    suspend fun getNoteForShare(id: String): Note? {
        val note = getNoteForEditor(id)
        logRoomContentAudit(
            "share external markdown full read success=${note != null} fullLen=${note?.content?.length ?: -1}",
        )
        return note
    }

    override suspend fun getNote(id: String): Note? {
        return withContext(Dispatchers.IO) {
            // Do not SELECT * here. This method is also used by editor side panels
            // and external-open paths, and large cached content can exceed CursorWindow.
            val entity = noteDao.getNoteShellByPath(id) ?: return@withContext null
            val file = findNoteDocumentDirectFirst(entity, traceReason = "getNote")
                ?: findDocumentByPath(id, traceReason = "getNote.fallbackPath")
                ?: run {
                    return@withContext entity.toNote()
                }
            val fileModified = file.lastModified()
            val readResult = readNoteFromFileForEditor(entity, file) ?: return@withContext null
            val updated = readResult.entity.copy(
                lastModifiedMs = fileModified.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
            if ((fileModified > 0L && fileModified != entity.lastModifiedMs) || updated.createdAtMs != entity.createdAtMs) {
                noteDao.insertNote(updated)
            }
            updated.toNote(
                noteType = readResult.noteType,
                sourceType = readResult.sourceType,
                sourceUrl = readResult.sourceUrl,
                updatedAtMs = readResult.updatedAtMs,
            )
        }
    }

    suspend fun notePathExists(path: String): Boolean =
        withContext(Dispatchers.IO) {
            val normalized = normalizeFolderPath(path)
            if (normalized.isBlank()) return@withContext false
            if (noteDao.getNoteShellByPath(normalized) != null) return@withContext true
            findDocumentByDirectUri(normalized, expectDirectory = false)?.let { return@withContext true }
            findDocumentByPath(normalized, traceReason = "notePathExists") != null
        }

    suspend fun folderPathExists(path: String): Boolean =
        withContext(Dispatchers.IO) {
            val normalized = normalizeFolderPath(path)
            if (normalized.isBlank()) return@withContext true
            findFolder(rootDir ?: return@withContext false, normalized) != null
        }

    fun relativeFolderPathFromTreeUri(uri: Uri): String? = relativeFolderFromTreeUri(uri)

    suspend fun saveNote(
        note: Note,
        oldFile: java.io.File? = null,
    ): String = fileTreeMutationMutex.withLock {
        saveNoteInternal(note, oldFile, saveHistory = false, preferDirectLookup = false)
    }

    override suspend fun saveNote(
        note: Note,
        oldFile: java.io.File?,
        saveHistory: Boolean,
    ): String = fileTreeMutationMutex.withLock {
        saveNoteInternal(note, oldFile, saveHistory, preferDirectLookup = false)
    }

    suspend fun saveNoteFromQuickEditor(
        note: Note,
        oldFile: java.io.File? = null,
        saveHistory: Boolean = false,
    ): String = fileTreeMutationMutex.withLock {
        saveNoteInternal(note, oldFile, saveHistory, preferDirectLookup = true)
    }

    override suspend fun mergeNotes(
        noteIds: List<String>,
        options: MergeNotesOptions,
    ): MergeNotesResult = withContext(Dispatchers.IO) {
        val orderedPaths = noteIds.map(::normalizeFolderPath).filter { it.isNotBlank() }.distinct()
        val sourceCount = (orderedPaths.size - 1).coerceAtLeast(0)
        if (orderedPaths.size < 2) {
            return@withContext MergeNotesResult(sourceCount = sourceCount, failedSourceCount = sourceCount)
        }

        val entitiesByPath = noteDao.getNoteShellsByPaths(orderedPaths).associateBy { it.filePath }
        if (entitiesByPath.size != orderedPaths.size) {
            return@withContext MergeNotesResult(sourceCount = sourceCount, failedSourceCount = sourceCount)
        }
        if (entitiesByPath.values.any { it.isArchived || it.isTrashed }) {
            return@withContext MergeNotesResult(sourceCount = sourceCount, failedSourceCount = sourceCount)
        }

        val sources = mutableListOf<MergeSource>()
        for (path in orderedPaths) {
            val entity = entitiesByPath[path] ?: return@withContext MergeNotesResult(
                sourceCount = sourceCount,
                failedSourceCount = sourceCount,
            )
            val file = findNoteDocumentDirectFirst(entity, traceReason = "mergeNotes")
                ?: findDocumentByPath(path, traceReason = "mergeNotes.fallbackPath")
                ?: return@withContext MergeNotesResult(sourceCount = sourceCount, failedSourceCount = sourceCount)
            val rawContent = readTextOrNull(file, bypassCache = true)
                ?: return@withContext MergeNotesResult(sourceCount = sourceCount, failedSourceCount = sourceCount)
            val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
            val note = entity.toNote(
                noteType = NoteFormatUtils.extractNoteType(frontMatter),
                sourceType = NoteFormatUtils.extractSourceType(frontMatter),
                sourceUrl = NoteFormatUtils.extractSourceUrl(frontMatter),
                updatedAtMs = NoteFormatUtils.extractUpdatedAt(frontMatter),
            ).copy(
                content = frontMatter.cleanContent,
                contentPreview = frontMatter.cleanContent.take(NOTE_PREVIEW_CHAR_LIMIT),
                createdAt = Date(NoteFormatUtils.extractCreatedAt(frontMatter) ?: entity.createdAtMs),
                tags = NoteFormatUtils.extractTags(frontMatter),
            )
            sources += MergeSource(entity = entity, file = file, rawContent = rawContent, note = note)
        }

        val target = sources.first()
        val targetFolder = target.note.folder
        val notesForMerge = sources.map { source ->
            val content = if (
                prefsManager.getImagePathMode() == PrefsManager.ImagePathMode.RELATIVE &&
                source.note.folder != targetFolder
            ) {
                rewriteRelativeImageRefs(source.note.content, source.note.folder, targetFolder)
            } else {
                source.note.content
            }
            source.note.copy(content = content)
        }
        val separator = options.separator.takeIf { it == "\n\n" || it == "\n" || it.isEmpty() }
            ?: MergeNotesOptions.DEFAULT_SEPARATOR
        val mergedContent = mergeMarkdownBlocks(
            notes = notesForMerge,
            includeTitles = options.includeTitles,
            separator = separator,
        )
        val mergedTags = if (options.mergeMetadata) {
            NoteFormatUtils.normalizeTags(notesForMerge.flatMap { it.tags })
        } else {
            target.note.tags
        }
        val mergedFrontMatter = if (options.mergeMetadata) {
            mergeFrontMatterForNotes(
                targetRawContent = target.rawContent,
                sourceRawContents = sources.drop(1).map { it.rawContent },
            )
        } else {
            null
        }
        val savedPath = saveNoteInternal(
            note = target.note.copy(
                content = mergedContent,
                contentPreview = mergedContent.take(NOTE_PREVIEW_CHAR_LIMIT),
                tags = mergedTags,
            ),
            oldFile = target.note.file,
            saveHistory = true,
            preferDirectLookup = false,
            existingRawContentOverride = mergedFrontMatter,
        )
        if (savedPath.isBlank()) {
            return@withContext MergeNotesResult(sourceCount = sourceCount, failedSourceCount = sourceCount)
        }

        val sourceSources = sources.drop(1)
        val handledPaths = if (options.moveSourcesToTrash) {
            moveNoteEntitiesToTrash(sourceSources.map { it.entity }).also { moved ->
                val movedPaths = moved.map { it.sourcePath }.toSet()
                moved.forEach {
                    prefsManager.setNotePinned(it.sourcePath, false)
                    prefsManager.setNoteFavorite(it.sourcePath, false)
                }
                sourceSources
                    .filter { it.entity.filePath in movedPaths }
                    .forEach { source ->
                        noteLinkDao.deleteBySource(source.entity.filePath, source.entity.recordId)
                        noteLinkDao.markTargetUnresolved(source.entity.filePath, source.entity.recordId)
                    }
            }.map { it.sourcePath }
        } else {
            deleteMergedSourceNotes(sourceSources)
        }
        if (handledPaths.isNotEmpty()) reconcileAllWikilinkResolutions()

        MergeNotesResult(
            targetPath = savedPath,
            sourceCount = sourceCount,
            handledSourceCount = handledPaths.size,
            failedSourceCount = sourceCount - handledPaths.size,
        )
    }

    private fun mergeFrontMatterForNotes(
        targetRawContent: String,
        sourceRawContents: List<String>,
    ): String? {
        val valuesByKey = linkedMapOf<String, MutableList<String>>()
        val displayKeyByKey = linkedMapOf<String, String>()

        fun addProperty(property: NoteFormatUtils.FrontMatterProperty) {
            val normalizedKey = property.key.lowercase(Locale.ROOT)
            val values = valuesByKey.getOrPut(normalizedKey) { mutableListOf() }
            displayKeyByKey.putIfAbsent(normalizedKey, property.key)
            property.values.forEach { value ->
                if (value !in values) values += value
            }
        }

        NoteFormatUtils.parseFrontMatter(targetRawContent).properties.forEach(::addProperty)
        sourceRawContents
            .flatMap { NoteFormatUtils.parseFrontMatter(it).properties }
            .filterNot { it.key.lowercase(Locale.ROOT) in MERGE_MANAGED_METADATA_KEYS }
            .forEach(::addProperty)

        if (valuesByKey.isEmpty()) return null
        return buildString {
            append("---\n")
            valuesByKey.forEach { (normalizedKey, values) ->
                val key = displayKeyByKey.getValue(normalizedKey)
                if (values.size == 1) {
                    append(key)
                    append(": ")
                    append(quoteMergedYamlValue(values.first()))
                    append('\n')
                } else {
                    append(key)
                    append(":\n")
                    values.forEach { value ->
                        append("  - ")
                        append(quoteMergedYamlValue(value))
                        append('\n')
                    }
                }
            }
            append("---\n\n")
        }
    }

    private fun quoteMergedYamlValue(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ')}\""

    private suspend fun deleteMergedSourceNotes(sources: List<MergeSource>): List<String> {
        val deletedSources = sources.filter { source ->
            val deleted = source.file.delete()
            if (!deleted) {
                KardLeafLog.e("RoomNoteRepository", "Failed to delete merged source note: ${source.entity.filePath}")
            }
            deleted
        }
        if (deletedSources.isEmpty()) return emptyList()

        val deletedPaths = deletedSources.map { it.entity.filePath }
        deletedSources.forEach { source ->
            noteLinkDao.deleteBySource(source.entity.filePath, source.entity.recordId)
            noteLinkDao.markTargetUnresolved(source.entity.filePath, source.entity.recordId)
            deleteNoteRecordsForPath(source.entity.filePath, source.entity.recordId)
            prefsManager.setNotePinned(source.entity.filePath, false)
            prefsManager.setNoteFavorite(source.entity.filePath, false)
            fileSignatures.remove(source.entity.filePath)
        }
        noteDao.deleteNotesByPaths(deletedPaths)
        markWebDavRealtimeLocalDirty()
        return deletedPaths
    }

    private suspend fun saveNoteInternal(
        note: Note,
        oldFile: java.io.File?,
        saveHistory: Boolean,
        preferDirectLookup: Boolean,
        createdAtOverride: Date? = null,
        updatedAtOverride: Date? = null,
        existingRawContentOverride: String? = null,
    ): String =
        withContext(Dispatchers.IO) {
            val operationStartedAt = SystemClock.elapsedRealtime()
            val root = rootDir ?: return@withContext ""
            val folderName = normalizeFolderPath(note.folder)

            // Determine the actual root for searching/saving based on status
            val effectiveRoot =
                when {
                    note.isTrashed -> getTrashRoot(root, create = true)
                    else -> root
                } ?: root

            // A direct URI resolves to SingleDocumentFile, which cannot enumerate or create children.
            // Resolve the parent through the tree so findFile/createFile remain supported.
            var targetDir = getOrCreateFolder(effectiveRoot, folderName) ?: return@withContext ""

            if (note.isArchived && !note.isTrashed) {
                targetDir = getArchiveFolder(root, create = true) ?: return@withContext ""
            }

            val baseTitle = NoteFormatUtils.sanitizeMarkdownFileBaseName(note.title)
            var finalTitle = baseTitle
            var finalFileName = "$finalTitle.md"

            var previousPath: String? = null
            var previousRawContent: String? = null
            var previousRawTags: List<String> = emptyList()
            var previousDbTags: List<String> = emptyList()
            var previousRecordId: String? = null
            var oldFileDoc: DocumentFile? = null

            if (oldFile != null) {
                val oldName = oldFile.name
                val oldParentName = normalizeFolderPath(oldFile.parent.orEmpty())
                previousPath = joinPath(oldParentName, oldName)
                val previousEntity = getFullNoteEntityByPathForAudit(previousPath, "save-history-snapshot")
                previousRecordId = previousEntity?.recordId
                previousDbTags = NoteFormatUtils.tagsFromStorage(previousEntity?.yamlTags)
                logYamlTagTrace(
                    "saveNote oldEntity path=$previousPath exists=${previousEntity != null} oldDbTags=${previousEntity?.yamlTags?.let { NoteFormatUtils.tagsFromStorage(it) }.orEmpty()} oldTitle=${previousEntity?.title}",
                )

                oldFileDoc =
                    if (preferDirectLookup) {
                        previousEntity?.let { findNoteDocumentByDirectUri(it, "quick-save") }
                    } else {
                        null
                    }
                if (oldFileDoc == null) {
                    val folderDoc = findFolder(root, oldParentName)
                    val trashDoc = getTrashRoot(root, create = false)?.let { findFolder(it, oldParentName) }
                    oldFileDoc = if (previousEntity?.isArchived == true) {
                        findArchiveNoteDocument(root, oldName)
                            ?: folderDoc?.findFile("Archived")?.findFile(oldName)
                    } else {
                        folderDoc?.findFile(oldName)
                            ?: folderDoc?.findFile("Pinned")?.findFile(oldName)
                            ?: folderDoc?.findFile("Archived")?.findFile(oldName)
                            ?: trashDoc?.findFile(oldName)
                    }
                }

                val historySnapshotContentSource = readHistorySnapshotContentSource(oldFileDoc)
                previousRawContent = historySnapshotContentSource.rawContent
                previousRawTags = historySnapshotContentSource.tags
                logYamlTagTrace(
                    "saveNote oldFileDoc path=$previousPath found=${oldFileDoc != null} oldRawLen=${previousRawContent?.length ?: -1} oldRawTags=$previousRawTags",
                )

                if (saveHistory &&
                    previousEntity != null &&
                    hasTitleOrContentChanged(previousEntity, note, historySnapshotContentSource.cleanContent)
                ) {
                    saveHistorySnapshot(
                        entity = previousEntity,
                        externalContent = historySnapshotContentSource.cleanContent,
                        externalFallbackReason = historySnapshotContentSource.fallbackReason,
                    )
                }
            }

            var filePath = joinPath(folderName, finalFileName)
            var targetFileDoc =
                oldFileDoc?.takeIf { preferDirectLookup && previousPath == filePath }
                    ?: (if (preferDirectLookup) findDocumentByDirectUri(filePath, expectDirectory = false) else null)
                    ?: targetDir.findFile(finalFileName)
            var counter = 1
            while (true) {
                val sameFile = targetFileDoc != null && oldFileDoc != null && targetFileDoc.uri == oldFileDoc.uri
                val fileConflict = targetFileDoc != null && !sameFile
                // Markdown 文件是主数据；Room 缓存中的孤立路径不能占用文件名。
                if (!fileConflict) break

                finalTitle = "$baseTitle($counter)"
                finalFileName = "$finalTitle.md"
                filePath = joinPath(folderName, finalFileName)
                targetFileDoc =
                    (if (preferDirectLookup) findDocumentByDirectUri(filePath, expectDirectory = false) else null)
                        ?: targetDir.findFile(finalFileName)
                counter++
            }

            logYamlTagTrace(
                "saveNote start targetPath=$filePath oldFile=${oldFile?.path} noteTitle=${note.title} noteTags=${note.tags} noteContentLen=${note.content.length} saveHistory=$saveHistory",
            )
            KardLeafLog.d(
                SAVE_PATH_TRACE_TAG,
                "repo saveNote start targetPath=$filePath oldFile=${oldFile?.path} noteTitle=${note.title} " +
                    "contentLen=${note.content.length} contentHash=${note.content.hashCode()} saveHistory=$saveHistory " +
                    "setupElapsed=${SystemClock.elapsedRealtime() - operationStartedAt}ms",
            )

            val createdNewFile = targetFileDoc == null
            if (createdNewFile) {
                targetFileDoc = targetDir.createFile("text/markdown", finalFileName)
            }
            val writableTarget = targetFileDoc ?: run {
                KardLeafLog.e("RoomNoteRepository", "Failed to create note file: $filePath")
                return@withContext ""
            }

            val targetMatchesOldFile = oldFileDoc?.uri == writableTarget.uri
            val targetRawContent =
                when {
                    createdNewFile -> null
                    existingRawContentOverride != null -> existingRawContentOverride
                    preferDirectLookup && targetMatchesOldFile && previousRawContent != null -> previousRawContent
                    else -> readText(writableTarget)
                }
            val targetFrontMatter = targetRawContent?.let(NoteFormatUtils::parseFrontMatter)
            val targetTags = targetFrontMatter?.let { NoteFormatUtils.extractTags(it) }.orEmpty()
            val existingContent =
                if (previousPath != null && previousPath != filePath && !previousRawContent.isNullOrBlank()) {
                    previousRawContent
                } else {
                    targetRawContent ?: previousRawContent
                }
            val existingTags =
                when {
                    existingContent == null -> emptyList()
                    previousRawContent != null && existingContent == previousRawContent -> previousRawTags
                    targetRawContent != null && existingContent == targetRawContent -> targetTags
                    else -> NoteFormatUtils.extractTags(existingContent)
                }
            val preservedTags =
                if (note.tags.isEmpty()) {
                    existingTags.ifEmpty { previousDbTags }
                } else {
                    note.tags
                }
            val noteForWrite = if (preservedTags == note.tags) note else note.copy(tags = preservedTags)
            logYamlTagTrace(
                "saveNote contentSource targetPath=$filePath previousPath=$previousPath targetLen=${targetRawContent?.length ?: -1} targetTags=$targetTags existingLen=${existingContent?.length ?: -1} existingTags=$existingTags previousLen=${previousRawContent?.length ?: -1} previousTags=$previousRawTags previousDbTags=$previousDbTags preservedTags=$preservedTags",
            )
            val fullContent = NoteFormatUtils.constructFileContent(
                note = noteForWrite,
                existingRawContent = existingContent,
                replaceTags = true,
                createdAtOverride = createdAtOverride,
                updatedAtOverride = updatedAtOverride,
            )
            val outputFrontMatter = NoteFormatUtils.parseFrontMatter(fullContent)
            val noteRecordId = NoteFormatUtils.extractKardLeafId(outputFrontMatter) ?: filePath
            val outputTags = NoteFormatUtils.extractTags(outputFrontMatter)
            val writtenYamlTags = NoteFormatUtils.tagsToStorage(outputTags)
            logYamlTagTrace(
                "saveNote output targetPath=$filePath recordId=$noteRecordId outputTags=$outputTags writtenYamlTags=${NoteFormatUtils.tagsFromStorage(writtenYamlTags)} outputLen=${fullContent.length}",
            )

            val fileWriteStartedAt = SystemClock.elapsedRealtime()
            val writtenLastModified =
                try {
                    context.contentResolver.openOutputStream(writableTarget.uri, "wt")?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(fullContent)
                        }
                    } ?: throw IOException("openOutputStream returned null")
                    lastLocalWriteElapsedMs = SystemClock.elapsedRealtime()
                    updateTextCache(writableTarget, fullContent)
                    val modified = writableTarget.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                    KardLeafLog.d(
                        SAVE_PATH_TRACE_TAG,
                        "repo file-written path=$filePath rawLen=${fullContent.length} rawHash=${fullContent.hashCode()} " +
                            "contentLen=${note.content.length} contentHash=${note.content.hashCode()} modified=$modified " +
                            "fileElapsed=${SystemClock.elapsedRealtime() - fileWriteStartedAt}ms " +
                            "totalElapsed=${SystemClock.elapsedRealtime() - operationStartedAt}ms",
                    )
                    modified
                } catch (e: Exception) {
                    KardLeafLog.e("RoomNoteRepository", "Failed to write note file: $filePath", e)
                    if (createdNewFile && previousPath != filePath) {
                        writableTarget.delete()
                    } else if (existingContent != null) {
                        runCatching {
                            context.contentResolver.openOutputStream(writableTarget.uri, "wt")?.use { outputStream ->
                                OutputStreamWriter(outputStream).use { writer -> writer.write(existingContent) }
                            } ?: throw IOException("rollback openOutputStream returned null")
                            updateTextCache(writableTarget, existingContent)
                            KardLeafLog.w(
                                "RoomNoteRepository",
                                "write failed; restored previous Markdown path=$filePath length=${existingContent.length}",
                            )
                        }.onFailure { restoreError ->
                            KardLeafLog.e(
                                "RoomNoteRepository",
                                "write failed and previous Markdown restore failed path=$filePath",
                                restoreError,
                            )
                        }
                    }
                    return@withContext ""
                }

            val oldPathToRemove =
                previousPath?.takeIf { it != filePath && oldFileDoc != null && oldFileDoc.uri != writableTarget.uri }
            if (oldPathToRemove != null && oldFileDoc?.delete() != true) {
                KardLeafLog.e("RoomNoteRepository", "Failed to delete old note file after save: $oldPathToRemove")
                if (!targetMatchesOldFile) {
                    val cleanedUp = writableTarget.delete()
                    KardLeafLog.w(
                        "RoomNoteRepository",
                        "cleaned up new duplicate target after old-file delete failure path=$filePath success=$cleanedUp",
                    )
                }
                return@withContext ""
            }

            val createdAtMs =
                NoteFormatUtils.extractCreatedAt(outputFrontMatter)
                    ?: createdAtOverride?.time
                    ?: previousPath?.let { noteDao.getNoteShellByPath(it)?.createdAtMs }
                    ?: noteDao.getNoteShellByPath(filePath)?.createdAtMs
                    ?: note.createdAt.time
            val entity =
                NoteEntity(
                    filePath = filePath,
                    recordId = noteRecordId,
                    fileName = finalFileName,
                    folder = folderName,
                    title = finalTitle,
                    contentPreview = note.content.take(200),
                    content = note.content,
                    lastModifiedMs = writtenLastModified,
                    createdAtMs = createdAtMs,
                    color = note.color,
                    reminder = note.reminder,
                    isPinned = note.isPinned,
                    isFavorite = note.isFavorite,
                    isArchived = note.isArchived,
                    isTrashed = note.isTrashed,
                    deletedAtMs = note.deletedAt?.time,
                    firstImageReference = extractFirstImageReference(note.content).orEmpty(),
                    yamlTags = writtenYamlTags,
                )
            val roomWriteStartedAt = SystemClock.elapsedRealtime()
            noteDao.insertNote(entity)
            oldPathToRemove?.let { noteDao.deleteNoteByPath(it) }
            oldPathToRemove?.let { oldPath ->
                val oldRecordId = previousRecordId ?: oldPath
                noteLinkDao.deleteBySource(oldPath, oldRecordId)
                noteLinkDao.markTargetUnresolved(oldPath, oldRecordId)
            }
            scheduleNoteLinkIndex(entity)
            logYamlTagTrace(
                "saveNote dbInserted path=$filePath dbTags=${NoteFormatUtils.tagsFromStorage(entity.yamlTags)} createdAtMs=$createdAtMs lastModifiedMs=$writtenLastModified",
            )
            KardLeafLog.d(
                SAVE_PATH_TRACE_TAG,
                "repo db-inserted path=$filePath contentLen=${entity.content.length} contentHash=${entity.content.hashCode()} " +
                    "previewLen=${entity.contentPreview.length} previewHash=${entity.contentPreview.hashCode()} " +
                    "roomElapsed=${SystemClock.elapsedRealtime() - roomWriteStartedAt}ms " +
                    "totalElapsed=${SystemClock.elapsedRealtime() - operationStartedAt}ms",
            )
            val writtenLength =
                if (preferDirectLookup) {
                    fullContent.toByteArray(Charsets.UTF_8).size.toLong()
                } else {
                    writableTarget.length()
                }
            fileSignatures[filePath] = FileSignature(writtenLastModified, writtenLength)
            if (!preferDirectLookup || previousPath != null) {
                syncNoteRecordsWithResolvedId(filePath, noteRecordId)
            }
            previousPath?.takeIf { it != filePath }?.let {
                logYamlTagTrace("saveNote pathChanged oldPath=$it newPath=$filePath recordId=$noteRecordId outputTags=$outputTags")
                fileSignatures.remove(it)
                prefsManager.replacePinnedNotePath(it, filePath)
                prefsManager.replaceFavoriteNotePath(it, filePath)
                if (noteRecordId == filePath) {
                    val movedHistory = noteHistoryDao.replaceNoteId(it, filePath)
                    val movedRemarks = noteRemarkDao.replaceNoteId(it, filePath)
                    val historyLimit = prefsManager.getHistoryVersionLimit()
                    if (historyLimit > 0) {
                        noteHistoryDao.pruneOldVersions(filePath, historyLimit)
                    }
                    syncRecordStoreAfterKeyChange(movedHistory, movedRemarks, it, filePath)
                } else {
                    syncNoteRecordsWithResolvedId(it, noteRecordId)
                    val historyLimit = prefsManager.getHistoryVersionLimit()
                    if (historyLimit > 0) {
                        noteHistoryDao.pruneOldVersions(noteRecordId, historyLimit)
                        recordExternalBackup.syncHistory(noteRecordId)
                    }
                }
            }
            prefsManager.setNotePinned(filePath, note.isPinned && !note.isArchived && !note.isTrashed)
            prefsManager.setNoteFavorite(filePath, note.isFavorite && !note.isTrashed)
            markWebDavRealtimeLocalDirty()
            KardLeafLog.d(
                SAVE_PATH_TRACE_TAG,
                "repo saveNote done path=$filePath pathChanged=${previousPath != null && previousPath != filePath} " +
                    "totalElapsed=${SystemClock.elapsedRealtime() - operationStartedAt}ms",
            )

            return@withContext filePath
        }

    override fun getNoteHistory(noteId: String): Flow<List<NoteHistory>> = flow {
        val recordId = resolveNoteRecordId(noteId)
        emitAll(historyStore.getHistory(recordId))
    }

    override fun searchHistoryPreview(query: String): Flow<List<NoteHistory>> = historyStore.searchPreview(query)

    override fun searchNoteMatches(
        query: String,
        options: NoteSearchOptions,
    ): Flow<List<NoteSearchMatch>> {
        val safeQuery = query.trim()
        val searchStartMs = SystemClock.elapsedRealtime()
        val searchMode = if (options == NoteSearchOptions()) "default-like" else "advanced-scan"
        KardLeafLog.d(
            SEARCH_TRACE_TAG,
            "notes request mode=$searchMode options=$options ${SearchQueryUtils.describeForLog(query)}",
        )
        if (safeQuery.isBlank() && !options.hasMetadataFilters) {
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "notes skip mode=$searchMode reason=blankQuery ${SearchQueryUtils.describeForLog(query)}",
            )
            return flowOf(emptyList())
        }
        if (options == NoteSearchOptions()) {
            val likeQuery = SearchQueryUtils.escapeLikePattern(safeQuery)
            return noteDao.searchNoteMatches(safeQuery, likeQuery, SEARCH_RESULT_LIMIT).map { matches ->
                val unpositioned = matches.count { it.startOffset < 0 }
                KardLeafLog.d(
                    SEARCH_TRACE_TAG,
                    "notes result mode=default-like queryLen=${safeQuery.length} count=${matches.size} " +
                        "limit=$SEARCH_RESULT_LIMIT unpositioned=$unpositioned " +
                        "title=${matches.count { it.scope == "标题" }} content=${matches.count { it.scope == "正文" }} " +
                        "first=${matches.firstOrNull()?.let { it.scope + ":" + it.startOffset }} " +
                        "elapsed=${SystemClock.elapsedRealtime() - searchStartMs}ms",
                )
                matches
            }
        }
        val regex =
            if (options.useRegex && safeQuery.isNotBlank()) {
                runCatching {
                    Regex(safeQuery, if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE))
                }.getOrNull() ?: run {
                    KardLeafLog.d(
                        SEARCH_TRACE_TAG,
                        "notes skip mode=advanced-scan reason=invalidRegex ${SearchQueryUtils.describeForLog(query)}",
                    )
                    return flowOf(emptyList())
                }
            } else {
                null
            }
        // ponytail: advanced search scans cached note text; move to FTS only when large-vault profiling justifies it.
        val searchableNotes =
            if (options.matchTitle && !options.matchContent) {
                noteDao.getAllSearchableNoteShells()
            } else {
                noteDao.getAllSearchableNotes()
            }
        return searchableNotes.map { notes ->
            val matches =
                notes.asSequence()
                    .mapNotNull { entity ->
                        findSearchMatch(
                            note = entity.toNote(),
                            query = safeQuery,
                            options = options,
                            compiledRegex = regex,
                        )?.let { match ->
                            NoteSearchMatch(
                                noteId = entity.filePath,
                                scope = match.scope,
                                snippet = match.snippet,
                                startOffset = match.startOffset,
                            )
                        }
                    }
                    .take(SEARCH_RESULT_LIMIT)
                    .toList()
            val unpositioned = matches.count { it.startOffset < 0 }
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "notes result mode=advanced-scan queryLen=${safeQuery.length} candidates=${notes.size} count=${matches.size} " +
                    "limit=$SEARCH_RESULT_LIMIT unpositioned=$unpositioned " +
                    "title=${matches.count { it.scope == "标题" }} content=${matches.count { it.scope == "正文" }} " +
                    "first=${matches.firstOrNull()?.let { it.scope + ":" + it.startOffset }} " +
                    "elapsed=${SystemClock.elapsedRealtime() - searchStartMs}ms",
            )
            matches
        }.flowOn(Dispatchers.Default)
    }

    suspend fun exportUserDataBackup(): String = backupManager.export()

    suspend fun importUserDataBackup(json: String) {
        backupManager.import(json)
        recordExternalBackup.syncFullFromRoom()
    }

    fun getOutgoingWikilinks(notePath: String): Flow<List<NoteLinkEntity>> = flow {
        val entity = noteDao.getNoteShellByPath(notePath)
        if (entity == null) {
            emit(emptyList())
        } else {
            val queryStartedAt = SystemClock.elapsedRealtime()
            emitAll(
                noteLinkDao.getOutgoing(entity.filePath, entity.recordId).onEach { links ->
                    KardLeafLog.d(
                        "KardLeafWikiLinkTrace",
                        "outgoing sourcePath=${entity.filePath} occurrenceCount=${links.size} " +
                            "queryElapsed=${SystemClock.elapsedRealtime() - queryStartedAt}ms",
                    )
                },
            )
        }
    }

    fun getBacklinks(notePath: String): Flow<List<NoteLinkEntity>> = flow {
        val entity = noteDao.getNoteShellByPath(notePath)
        if (entity == null) {
            emit(emptyList())
        } else {
            val queryStartedAt = SystemClock.elapsedRealtime()
            emitAll(
                noteLinkDao.getBacklinks(entity.recordId, entity.filePath).onEach { links ->
                    KardLeafLog.d(
                        "KardLeafBacklinkTrace",
                        "targetPath=${entity.filePath} sourceCount=${links.map { it.sourcePath }.distinct().size} " +
                            "occurrenceCount=${links.size} queryElapsed=${SystemClock.elapsedRealtime() - queryStartedAt}ms",
                    )
                },
            )
        }
    }

    suspend fun getAllNoteLinks(): List<NoteLinkEntity> =
        withContext(Dispatchers.IO) { noteLinkDao.getAllSync() }

    suspend fun getWikilinkCandidates(query: String, limit: Int = 50): List<WikilinkCandidate> =
        withContext(Dispatchers.IO) {
            val needle = query.trim().lowercase(Locale.ROOT)
            noteDao.getAllNoteMetadataSync()
                .asSequence()
                .filter { !it.isTrashed }
                .map { entity ->
                    WikilinkCandidate(
                        id = entity.filePath,
                        recordId = entity.recordId,
                        title = entity.title,
                        path = joinPath(entity.folder, entity.fileName),
                        folder = entity.folder,
                    )
                }
                .filter { needle.isBlank() || listOf(it.title, it.path, it.folder).any { value -> value.lowercase(Locale.ROOT).contains(needle) } }
                .sortedWith(compareBy<WikilinkCandidate> { !it.title.equals(query.trim(), ignoreCase = true) }.thenBy { it.path.lowercase(Locale.ROOT) })
                .take(limit.coerceIn(1, 100))
                .toList()
        }

    suspend fun resolveWikilinkTarget(target: String, sourcePath: String = ""): WikilinkResolution =
        withContext(Dispatchers.IO) {
            val lookupTarget = target.substringBefore('|').substringBefore('#').trim()
            val metadata = noteDao.getAllNoteMetadataSync()
            val candidates = resolveWikilinkCandidates(lookupTarget, sourcePath, metadata)
            val result = when (candidates.size) {
                0 -> WikilinkResolution(NoteLinkResolutionStatus.UNRESOLVED)
                1 -> WikilinkResolution(
                    status = NoteLinkResolutionStatus.RESOLVED,
                    targetPath = candidates.single().filePath,
                    targetRecordId = candidates.single().recordId,
                    candidates = listOf(candidates.single().filePath),
                    candidateDetails = candidates.map { it.toWikilinkCandidate() },
                )
                else -> WikilinkResolution(
                    status = NoteLinkResolutionStatus.AMBIGUOUS,
                    candidates = candidates.map { it.filePath },
                    candidateDetails = candidates.map { it.toWikilinkCandidate() },
                )
            }
            KardLeafLog.d(
                "KardLeafWikiLinkTrace",
                "resolve queryLen=${target.trim().length} candidateCount=${candidates.size} status=${result.status} " +
                    "sourcePath=$sourcePath targetNormalized=${normalizeObsidianTarget(lookupTarget)}",
            )
            result
        }

    private suspend fun indexNoteLinksForEntity(
        entity: NoteEntity,
        metadataOverride: List<NoteMetadataEntity>? = null,
        version: Long? = null,
    ) = withContext(Dispatchers.IO) {
        val sourceKey = entity.filePath
        val lock = noteLinkSourceLocks.getOrPut(sourceKey) { Mutex() }
        lock.withLock {
            val startMs = SystemClock.elapsedRealtime()
            val latestVersion = noteLinkSourceVersions[sourceKey]?.get()
            if (version != null && latestVersion != version) {
                KardLeafLog.d("KardLeafWikiLinkTrace", "index skipped stale sourcePath=$sourceKey version=$version latest=$latestVersion")
                return@withLock
            }
            val current = noteDao.getNoteShellByPath(entity.filePath)
            if (current == null || current.lastModifiedMs != entity.lastModifiedMs) {
                KardLeafLog.d(
                    "KardLeafWikiLinkTrace",
                    "index skipped stale db sourcePath=$sourceKey entityModified=${entity.lastModifiedMs} dbModified=${current?.lastModifiedMs}",
                )
                return@withLock
            }
            val sourceRecordId = entity.recordId.ifBlank { entity.filePath }
            val parsed = parseObsidianLinks(entity.content)
            val metadata = metadataOverride ?: noteDao.getAllNoteMetadataSync()
            val links = parsed.map { parsedLink ->
                val candidates = resolveWikilinkCandidates(parsedLink.target, entity.filePath, metadata)
                val status = when (candidates.size) {
                    0 -> NoteLinkResolutionStatus.UNRESOLVED
                    1 -> NoteLinkResolutionStatus.RESOLVED
                    else -> NoteLinkResolutionStatus.AMBIGUOUS
                }
                val resolved = candidates.singleOrNull()
                NoteLinkEntity(
                    sourceRecordId = sourceRecordId,
                    sourcePath = entity.filePath,
                    targetRaw = parsedLink.rawTarget,
                    targetNormalized = normalizeObsidianTarget(parsedLink.target),
                    targetRecordId = resolved?.recordId,
                    targetPath = resolved?.filePath,
                    alias = parsedLink.alias,
                    heading = parsedLink.heading,
                    blockId = parsedLink.blockId,
                    startOffset = parsedLink.startOffset,
                    endOffset = parsedLink.endOffset,
                    contextSnippet = parsedLink.contextSnippet,
                    resolutionStatus = status,
                )
            }
            if (version != null && noteLinkSourceVersions[sourceKey]?.get() != version) {
                KardLeafLog.d("KardLeafWikiLinkTrace", "index dropped after parse sourcePath=$sourceKey version=$version")
                return@withLock
            }
            noteLinkDao.deleteBySource(entity.filePath, sourceRecordId)
            if (links.isNotEmpty()) noteLinkDao.insertAll(links)
            KardLeafLog.d(
                "KardLeafWikiLinkTrace",
                "index sourceRecordId=$sourceRecordId sourcePath=${entity.filePath} contentLength=${entity.content.length} " +
                    "linkCount=${links.size} resolvedCount=${links.count { it.resolutionStatus == NoteLinkResolutionStatus.RESOLVED }} " +
                    "unresolvedCount=${links.count { it.resolutionStatus == NoteLinkResolutionStatus.UNRESOLVED }} " +
                    "ambiguousCount=${links.count { it.resolutionStatus == NoteLinkResolutionStatus.AMBIGUOUS }} " +
                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
        }
    }

    private fun resolveWikilinkCandidates(
        target: String,
        sourcePath: String,
        metadata: List<NoteMetadataEntity>,
    ): List<NoteMetadataEntity> {
        val normalized = normalizeObsidianTarget(target)
        if (normalized.isBlank()) return emptyList()
        val normalizedSourcePath = normalizeObsidianTarget(sourcePath)
        val sourceFolder = normalizedSourcePath.substringBeforeLast('/', "")
        val targetHasPath = normalized.contains('/')
        val exactPath = metadata.filter { entity ->
            val path = normalizeObsidianTarget(entity.filePath)
            val displayPath = normalizeObsidianTarget(joinPath(entity.folder, entity.fileName))
            !entity.isTrashed && (path == normalized || displayPath == normalized)
        }
        if (targetHasPath) return exactPath.distinctBy { it.filePath }
        val sameFolder = metadata.filter { entity ->
            !entity.isTrashed && normalizeObsidianTarget(entity.folder) == sourceFolder &&
                (normalizeObsidianTarget(entity.title) == normalized || normalizeObsidianTarget(entity.fileName) == normalized)
        }
        if (sameFolder.isNotEmpty()) return sameFolder.distinctBy { it.filePath }
        return metadata.filter { entity ->
            !entity.isTrashed &&
                (normalizeObsidianTarget(entity.title) == normalized || normalizeObsidianTarget(entity.fileName) == normalized)
        }.distinctBy { it.filePath }
    }

    private fun NoteMetadataEntity.toWikilinkCandidate(): WikilinkCandidate =
        WikilinkCandidate(
            id = filePath,
            recordId = recordId,
            title = title,
            path = joinPath(folder, fileName),
            folder = folder,
        )

    private suspend fun resolvePendingLinksForTarget(entity: NoteEntity) = withContext(Dispatchers.IO) {
        reconcileWikilinkTargets(
            listOf(entity.title, entity.filePath, joinPath(entity.folder, entity.fileName)),
        )
    }

    private suspend fun reconcileWikilinkTargets(targets: Collection<String>) = withContext(Dispatchers.IO) {
        val normalizedTargets = targets.map(::normalizeObsidianTarget).filter { it.isNotBlank() }.toSet()
        if (normalizedTargets.isEmpty()) return@withContext
        val metadata = noteDao.getAllNoteMetadataSync()
        normalizedTargets.forEach { normalized ->
            noteLinkDao.getLinksToNormalized(normalized).forEach { link ->
                val candidates = resolveWikilinkCandidates(link.targetNormalized, link.sourcePath, metadata)
                val resolved = candidates.singleOrNull()
                val status = when (candidates.size) {
                    0 -> NoteLinkResolutionStatus.UNRESOLVED
                    1 -> NoteLinkResolutionStatus.RESOLVED
                    else -> NoteLinkResolutionStatus.AMBIGUOUS
                }
                noteLinkDao.update(link.copy(
                    targetRecordId = resolved?.recordId,
                    targetPath = resolved?.filePath,
                    resolutionStatus = status,
                ))
            }
        }
    }

    private suspend fun reconcileAllWikilinkResolutions() = withContext(Dispatchers.IO) {
        noteLinkResolutionMutex.withLock {
            val metadata = noteDao.getAllNoteMetadataSync()
            val links = noteLinkDao.getAllSync()
            links.forEach { link ->
                val candidates = resolveWikilinkCandidates(link.targetNormalized, link.sourcePath, metadata)
                val resolved = candidates.singleOrNull()
                val status = when (candidates.size) {
                    0 -> NoteLinkResolutionStatus.UNRESOLVED
                    1 -> NoteLinkResolutionStatus.RESOLVED
                    else -> NoteLinkResolutionStatus.AMBIGUOUS
                }
                if (link.resolutionStatus != status || link.targetPath != resolved?.filePath || link.targetRecordId != resolved?.recordId) {
                    noteLinkDao.update(
                        link.copy(
                            targetRecordId = resolved?.recordId,
                            targetPath = resolved?.filePath,
                            resolutionStatus = status,
                        ),
                    )
                }
            }
            KardLeafLog.d("KardLeafWikiLinkTrace", "reconcile links=${links.size} metadata=${metadata.size}")
        }
    }

    private suspend fun scheduleNoteLinkIndex(entity: NoteEntity) {
        val sourceKey = entity.filePath
        val version = noteLinkSourceVersions.getOrPut(sourceKey) { AtomicLong() }.incrementAndGet()
        indexingScope.launch {
            runCatching {
                indexNoteLinksForEntity(entity, version = version)
                if (noteLinkSourceVersions[sourceKey]?.get() == version) {
                    reconcileAllWikilinkResolutions()
                }
            }.onFailure { error ->
                KardLeafLog.e("KardLeafWikiLinkTrace", "index failed sourcePath=${entity.filePath}", error)
            }
        }
    }

    override suspend fun deleteNoteHistory(historyId: Long) {
        val noteId = noteHistoryDao.getHistoryById(historyId)?.noteId
        historyStore.delete(historyId)
        recordExternalBackup.syncHistory(noteId)
    }

    override suspend fun restoreNoteHistory(
        noteId: String,
        historyId: Long,
    ): String =
        withContext(Dispatchers.IO) {
            val current = noteDao.getNoteShellByPath(noteId) ?: return@withContext ""
            val recordId = resolveNoteRecordId(noteId)
            val history = noteHistoryDao.getHistoryById(historyId) ?: return@withContext ""
            if (history.noteId != recordId && history.noteId != noteId) return@withContext ""

            val restored =
                current.toNote().copy(
                    title = history.title,
                    content = history.content,
                    lastModified = Date(),
                )
            saveNote(restored, current.toNote().file, saveHistory = true)
        }

    override suspend fun getHistoryCleanupPreview(keep: Int): List<HistoryCleanupPreview> = historyStore.getCleanupPreview(keep)

    override suspend fun cleanupOldHistoryVersions() {
        historyStore.cleanupOldVersions()
        recordExternalBackup.syncFullFromRoom()
    }

    // region 隐私空间笔记（外部 Vault 是唯一数据源；Room 仅作旧明文迁移源）
    fun getAllPrivacyNotes(): Flow<List<PrivacyNoteEntity>> = privacyStore.getAll()

    suspend fun hasPrivacyVault(): Boolean = privacyStore.hasVault()

    suspend fun initializePrivacyVault(password: String) {
        privacyStore.initialize(password)
        prefsManager.savePrivacyPasswordHash(PrivacyVaultCrypto.legacyPasswordHash(password))
    }

    suspend fun unlockPrivacyVault(password: String) {
        val passwordHash = PrivacyVaultCrypto.legacyPasswordHash(password)
        privacyStore.unlock(password, legacyPasswordVerified = passwordHash == prefsManager.getPrivacyPasswordHash())
        prefsManager.savePrivacyPasswordHash(passwordHash)
    }

    suspend fun changePrivacyVaultPassword(
        currentPassword: String,
        newPassword: String,
    ) {
        privacyStore.changePassword(
            currentPassword = currentPassword,
            newPassword = newPassword,
            legacyPasswordVerified = PrivacyVaultCrypto.legacyPasswordHash(currentPassword) == prefsManager.getPrivacyPasswordHash(),
        )
        prefsManager.savePrivacyPasswordHash(PrivacyVaultCrypto.legacyPasswordHash(newPassword))
    }

    suspend fun removePrivacyVaultPassword(currentPassword: String) {
        privacyStore.removePassword(
            currentPassword,
            legacyPasswordVerified = PrivacyVaultCrypto.legacyPasswordHash(currentPassword) == prefsManager.getPrivacyPasswordHash(),
        )
        prefsManager.savePrivacyPasswordHash(null)
    }

    internal suspend fun preparePrivacyBiometricUnlock(): NotePrivacyStore.BiometricUnlockRequest? =
        privacyStore.prepareBiometricUnlock()

    internal suspend fun unlockPrivacyVaultWithBiometric(request: NotePrivacyStore.BiometricUnlockRequest) =
        privacyStore.unlockWithBiometric(request)

    suspend fun lockPrivacyVault() = privacyStore.lock()

    fun getNoteRemarks(noteId: String): Flow<List<NoteRemark>> = flow {
        val recordId = resolveNoteRecordId(noteId)
        emitAll(
            noteRemarkDao.getRemarks(recordId).map { remarks ->
                remarks.map { it.toNoteRemark() }
            },
        )
    }

    suspend fun getNoteFrontMatterProperties(noteId: String): List<NoteFormatUtils.FrontMatterProperty> =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteShellByPath(noteId) ?: return@withContext emptyList()
            val file = findNoteDocumentDirectFirst(entity, traceReason = "getNoteFrontMatterProperties")
                ?: findDocumentByPath(noteId, traceReason = "getNoteFrontMatterProperties.fallbackPath")
                ?: return@withContext emptyList()
            NoteFormatUtils.extractFrontMatterProperties(readText(file))
        }


    suspend fun getNoteTextStatsForProperties(noteId: String): NoteTextStats =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteShellByPath(noteId) ?: return@withContext NoteTextStats()
            val file = findNoteDocumentDirectFirst(entity, traceReason = "getNoteTextStatsForProperties")
                ?: findDocumentByPath(noteId, traceReason = "getNoteTextStatsForProperties.fallbackPath")
            if (file != null) {
                countTextStatsFromDocument(file)
            } else {
                NoteTextStats.fromText(entity.contentPreview)
            }
        }


    private fun countTextStatsFromDocument(file: DocumentFile): NoteTextStats {
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                BufferedReader(inputStream.reader()).use { reader ->
                    NoteTextStats.fromLines(reader.lineSequence())
                }
            } ?: NoteTextStats()
        } catch (e: Exception) {
            KardLeafLog.e("RoomNoteRepository", "Exception counting markdown text stats.", e)
            NoteTextStats()
        }
    }

    suspend fun addNoteRemark(noteId: String, content: String): String? =
        withContext(Dispatchers.IO) {
            if (noteId.isBlank() || content.isBlank()) return@withContext null
            val recordId = resolveOrCreateNoteRecordIdForRemark(noteId)
            val now = System.currentTimeMillis()
            noteRemarkDao.upsert(
                NoteRemarkEntity(
                    noteId = recordId,
                    content = content,
                    createdAtMs = now,
                    updatedAtMs = now,
                ),
            )
            recordExternalBackup.syncRemarks(recordId)
            recordId
        }

    suspend fun updateNoteRemark(
        remarkId: Long,
        content: String,
    ) = withContext(Dispatchers.IO) {
        if (remarkId <= 0L || content.isBlank()) return@withContext
        noteRemarkDao.updateContent(
            id = remarkId,
            content = content,
            updatedAtMs = System.currentTimeMillis(),
        )
        recordExternalBackup.syncRemarks(noteRemarkDao.getRemarkById(remarkId)?.noteId)
    }

    suspend fun deleteNoteRemark(remarkId: Long) =
        withContext(Dispatchers.IO) {
            val noteId = noteRemarkDao.getRemarkById(remarkId)?.noteId
            noteRemarkDao.deleteById(remarkId)
            recordExternalBackup.syncRemarks(noteId)
        }

    suspend fun deleteNoteRemarks(noteId: String) =
        withContext(Dispatchers.IO) {
            if (noteId.isBlank()) return@withContext
            val recordId = resolveNoteRecordId(noteId)
            noteRemarkDao.deleteByNoteId(noteId)
            if (recordId != noteId) {
                noteRemarkDao.deleteByNoteId(recordId)
            }
            recordExternalBackup.syncRemarks(noteId, recordId)
        }

    suspend fun getRemarkNoteSummaries(): List<NoteRecordSummary> =
        withContext(Dispatchers.IO) {
            noteRemarkDao.getRemarkNoteSummaries()
        }

    suspend fun getHistoryNoteSummaries(): List<NoteRecordSummary> =
        withContext(Dispatchers.IO) {
            noteHistoryDao.getHistoryNoteSummaries()
        }

    suspend fun resolveRecordNotePath(recordKey: String): String? =
        withContext(Dispatchers.IO) {
            val key = recordKey.trim()
            if (key.isBlank()) return@withContext null

            noteDao.getNoteShellByRecordKey(key)?.let { return@withContext it.filePath }

            // 只在用户点击记录但缓存索引还没建立时做一次兜底扫描，避免打开设置页时卡住。
            noteDao.getAllNoteMetadataSync().forEach { metadata ->
                val file = findDocumentByPath(metadata.filePath) ?: return@forEach
                val recordId = readKardLeafRecordId(file) ?: return@forEach
                if (recordId == key) {
                    noteDao.updateRecordId(metadata.filePath, recordId)
                    return@withContext metadata.filePath
                }
            }
            null
        }

    private fun readKardLeafRecordId(file: DocumentFile): String? {
        return runCatching {
            context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                BufferedReader(inputStream.reader()).use { reader ->
                    val firstLine = reader.readLine() ?: return@use null
                    if (firstLine.trim() != "---") return@use null

                    val frontMatter = StringBuilder().append(firstLine).append('\n')
                    var lineCount = 1
                    while (lineCount < 80) {
                        val line = reader.readLine() ?: break
                        frontMatter.append(line).append('\n')
                        lineCount++
                        if (line.trim() == "---") break
                    }
                    NoteFormatUtils.extractKardLeafId(frontMatter.toString())
                }
            }
        }.getOrNull()
    }


    suspend fun savePrivacyNote(id: Long, title: String, content: String): Long = privacyStore.save(id, title, content)

    suspend fun moveNoteToPrivacy(
        noteId: String,
        titleOverride: String? = null,
        contentOverride: String? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val entity = getFullNoteEntityByPathForAudit(noteId, "move-note-to-privacy-fallback") ?: return@withContext false
            val latestNote = getNote(noteId)
            val title = titleOverride?.trim()?.takeIf { it.isNotBlank() }
                ?: latestNote?.title?.takeIf { it.isNotBlank() }
                ?: entity.title.ifBlank { entity.fileName.substringBeforeLast(".") }
            val content = contentOverride ?: latestNote?.content ?: entity.content
            if (title.isBlank() && content.isBlank()) return@withContext false

            val privacyId = runCatching {
                privacyStore.save(0L, title, content)
            }.getOrElse { return@withContext false }
            val sourceFile = findNoteDocument(entity)
            if (sourceFile != null && !sourceFile.delete()) {
                runCatching { privacyStore.delete(privacyId) }
                return@withContext false
            }
            noteDao.deleteNoteByPath(noteId)
            deleteNoteRecordsForPath(noteId, entity.recordId)
            prefsManager.setNotePinned(noteId, false)
            prefsManager.setNoteFavorite(noteId, false)
            fileSignatures.remove(noteId)
            markWebDavRealtimeLocalDirty()
            true
        }

    suspend fun moveNotesToPrivacy(noteIds: List<String>): Int {
        var movedCount = 0
        noteIds.distinct().forEach { noteId ->
            if (moveNoteToPrivacy(noteId)) {
                movedCount++
            }
        }
        return movedCount
    }

    suspend fun deletePrivacyNote(id: Long) = privacyStore.delete(id)

    suspend fun exportPrivacyNotes(): String = privacyStore.export()

    suspend fun importPrivacyNotes(json: String): Int = privacyStore.import(json)
    // endregion

    override suspend fun deleteNote(id: String) {
        deleteNoteWithResult(id)
    }

    suspend fun deleteNoteWithResult(id: String): Boolean =
        deleteNotesWithResult(listOf(id)).successIds.contains(id)

    override suspend fun deleteNotes(noteIds: List<String>) {
        deleteNotesWithResult(noteIds)
    }

    suspend fun deleteNotesWithResult(noteIds: List<String>): DeleteNotesResult =
        withContext(Dispatchers.IO) {
            fileTreeMutationMutex.withLock { deleteNotesWithResultLocked(noteIds) }
        }

    private suspend fun deleteNotesWithResultLocked(noteIds: List<String>): DeleteNotesResult {
        val requestedIds = noteIds.distinct()
        if (requestedIds.isEmpty()) {
            return DeleteNotesResult(successIds = emptyList(), failedIds = emptyList())
        }
        val entities = noteDao.getNoteShellsByPaths(requestedIds)
        if (entities.isEmpty()) {
            return DeleteNotesResult(successIds = emptyList(), failedIds = requestedIds)
        }

        val movedNotes = moveNoteEntitiesToTrash(entities)
        val successIds = movedNotes.map { it.sourcePath }
        if (successIds.isNotEmpty()) {
            successIds.forEach { prefsManager.setNotePinned(it, false) }
            successIds.forEach { prefsManager.setNoteFavorite(it, false) }
            markWebDavRealtimeLocalDirty()
        }
        val successSet = successIds.toSet()

        return DeleteNotesResult(
            successIds = successIds,
            failedIds = requestedIds.filter { it !in successSet },
            restorableIds = movedNotes.map { it.trashPath },
        )
    }

    suspend fun deleteTrashedNotesPermanentlyWithResult(noteIds: List<String>): DeleteNotesResult =
        withContext(Dispatchers.IO) {
            val requestedIds = noteIds.distinct()
            if (requestedIds.isEmpty()) {
                return@withContext DeleteNotesResult(successIds = emptyList(), failedIds = emptyList())
            }
            val entitiesByPath = noteDao.getNoteShellsByPaths(requestedIds)
                .filter { it.isTrashed }
                .associateBy { it.filePath }
            val successIds = mutableListOf<String>()

            requestedIds.forEach { id ->
                val entity = entitiesByPath[id] ?: return@forEach
                val file = findNoteDocument(entity)
                val deleted = file?.delete() ?: true
                if (deleted) {
                    successIds += id
                } else {
                    KardLeafLog.e("RoomNoteRepository", "Failed to permanently delete trashed note file: $id")
                }
            }

            if (successIds.isNotEmpty()) {
                successIds.forEach {
                    val recordId = entitiesByPath[it]?.recordId ?: it
                    noteLinkDao.deleteBySource(it, recordId)
                    noteLinkDao.markTargetUnresolved(it, recordId)
                    prefsManager.setNotePinned(it, false)
                    prefsManager.setNoteFavorite(it, false)
                    deleteNoteRecordsForPath(it, recordId)
                }
                noteDao.deleteNotesByPaths(successIds)
                markWebDavRealtimeLocalDirty()
            }
            val successSet = successIds.toSet()

            DeleteNotesResult(
                successIds = successIds,
                failedIds = requestedIds.filter { it !in successSet },
            )
        }

    override suspend fun archiveNote(id: String) =
        withContext(Dispatchers.IO) {
            if (!moveNoteToSystemFolder(id, isArchive = true)) return@withContext
            noteDao.archiveNote(id)
            markWebDavRealtimeLocalDirty()
        }

    override suspend fun archiveNotes(noteIds: List<String>) =
        withContext(Dispatchers.IO) {
            val entities = noteDao.getNoteShellsByPaths(noteIds)
            if (entities.isEmpty()) return@withContext
            val movedEntities = moveNoteEntitiesToSystemFolder(entities, isArchive = true)
            if (movedEntities.isEmpty()) return@withContext
            noteDao.archiveNotes(movedEntities.map { it.filePath })
            markWebDavRealtimeLocalDirty()
        }

    override suspend fun restoreNote(id: String) =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteShellByPath(id) ?: return@withContext
            val root = rootDir ?: return@withContext

            val folder = entity.folder
            val fileName = entity.fileName

            if (entity.isTrashed) {
                val trashRoot = getTrashRoot(root, create = false) ?: return@withContext
                val sourceFolder = findFolder(trashRoot, folder) ?: return@withContext
                val sourceFile = sourceFolder.findFile(fileName)?.takeIf { it.isFile } ?: return@withContext
                val targetFolder = getOrCreateFolder(root, folder) ?: return@withContext
                val restoredFileName = findAvailableFileName(
                    targetFolder = targetFolder,
                    preferredFileName = originalFileNameFromTrash(fileName),
                ) ?: return@withContext
                val restoredPath = joinPath(folder, restoredFileName)
                val restoredFile = moveMarkdownDocumentReturningTarget(
                    sourceFile = sourceFile,
                    targetFolder = targetFolder,
                    fileName = restoredFileName,
                    reason = "restoreNote",
                ) ?: return@withContext

                val roomUpdated = runCatching {
                    noteDao.restoreNoteFromTrashPath(
                        trashPath = id,
                        restoredPath = restoredPath,
                        restoredFileName = restoredFileName,
                    ) == 1
                }.onFailure { error ->
                    KardLeafLog.e("RoomNoteRepository", "restoreNote Room update failed: $id", error)
                }.getOrDefault(false)

                if (!roomUpdated) {
                    val rolledBack = moveMarkdownDocument(
                        sourceFile = restoredFile,
                        targetFolder = sourceFolder,
                        fileName = fileName,
                        reason = "restoreNote rollback",
                    )
                    KardLeafLog.e(
                        "RoomNoteRepository",
                        "restoreNote Room update rejected trash=$id restored=$restoredPath rolledBack=$rolledBack",
                    )
                    return@withContext
                }

                fileSignatures.remove(id)
                fileSignatures[restoredPath] = FileSignature(restoredFile.lastModified(), restoredFile.length())
                noteDao.getNoteByPath(restoredPath)?.let { scheduleNoteLinkIndex(it) }
                markWebDavRealtimeLocalDirty()
                return@withContext
            }

            val archiveSource = findArchiveNoteDocument(root, fileName)
                ?: findFolder(root, folder)?.findFile("Archived")?.findFile(fileName)
            val targetFolder = getOrCreateFolder(root, folder) ?: return@withContext
            if (archiveSource == null || !moveMarkdownDocument(archiveSource, targetFolder, fileName, "restoreNote")) {
                return@withContext
            }

            noteDao.restoreNote(id)
            markWebDavRealtimeLocalDirty()
        }

    override suspend fun togglePinStatus(
        noteIds: List<String>,
        isPinned: Boolean,
    ) = withContext(Dispatchers.IO) {
        val entities = noteDao.getNoteShellsByPaths(noteIds).filter { it.isPinned != isPinned }
        entities.forEach { entity ->
            prefsManager.setNotePinned(entity.filePath, isPinned)
        }
        noteDao.updatePinStatuses(entities.map { it.filePath }, isPinned)
    }

    override suspend fun toggleFavoriteStatus(
        noteIds: List<String>,
        isFavorite: Boolean,
    ) = withContext(Dispatchers.IO) {
        val entities = noteDao.getNoteShellsByPaths(noteIds).filter { it.isFavorite != isFavorite && !it.isTrashed }
        entities.forEach { entity ->
            prefsManager.setNoteFavorite(entity.filePath, isFavorite)
        }
        noteDao.updateFavoriteStatuses(entities.map { it.filePath }, isFavorite)
    }

    override suspend fun moveNotes(
        notes: List<Note>,
        targetFolder: String,
    ) {
        moveNotesWithResult(notes, targetFolder)
    }

    suspend fun moveNotesWithResult(
        notes: List<Note>,
        targetFolder: String,
        allowNameConflict: Boolean = true,
        createTargetFolder: Boolean = true,
        rewriteRelativeImages: Boolean = true,
    ): List<MovedNotePath> = withContext(Dispatchers.IO) {
        val root = rootDir ?: return@withContext emptyList()
        val movedPaths = mutableListOf<MovedNotePath>()

        notes.forEach {
            val fileName = it.file.name
            val sourceFolder = it.folder
            val isArchived = it.isArchived
            val isTrashed = it.isTrashed
            val isPinned = it.isPinned

            val sourceFile = findMoveSourceDocument(
                root = root,
                folder = sourceFolder,
                fileName = fileName,
                isArchived = isArchived,
                isTrashed = isTrashed,
                isPinned = isPinned,
            )

            var targetRoot =
                when {
                    isTrashed -> getTrashRoot(root, create = false)
                    else -> root
                }

            var targetFolderDoc = targetRoot?.let { rootFolder ->
                if (createTargetFolder) getOrCreateFolder(rootFolder, targetFolder)
                else findFolder(rootFolder, targetFolder)
            }

            if (isArchived && !isTrashed) {
                targetFolderDoc = targetFolderDoc?.findFile("Archived") ?: targetFolderDoc?.createDirectory("Archived")
            }

            if (sourceFile != null && targetFolderDoc != null) {
                try {
                    // 如果源和目标在同一文件夹，跳过
                    if (sourceFolder == targetFolder) {
                        return@forEach
                    }

                    val rawContent = readText(sourceFile)
                    // RELATIVE 图片路径模式下，移动笔记时同步更新笔记内图片相对引用
                    val content =
                        if (rewriteRelativeImages && prefsManager.getImagePathMode() == PrefsManager.ImagePathMode.RELATIVE) {
                            rewriteRelativeImageRefs(rawContent, sourceFolder, targetFolder)
                        } else {
                            rawContent
                        }

                    // 文件名冲突处理：如果目标位置已有同名文件（且不是源文件本身），加 (1) (2) ... 后缀
                    var resolvedFileName = fileName
                    var counter = 1
                    val maxRetries = 100
                    if (!allowNameConflict && targetFolderDoc.findFile(fileName) != null) return@forEach
                    while (allowNameConflict && targetFolderDoc.findFile(resolvedFileName) != null && counter <= maxRetries) {
                        val dotIndex = fileName.lastIndexOf('.')
                        val baseName = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
                        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
                        resolvedFileName = "$baseName ($counter)$ext"
                        counter++
                    }

                    val newFile = targetFolderDoc.createFile("text/markdown", resolvedFileName)
                    if (newFile == null) {
                        KardLeafLog.e("RoomNoteRepository", "Failed to create moved note: $resolvedFileName")
                        return@forEach
                    }
                    try {
                        context.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                            OutputStreamWriter(os).use { it.write(content) }
                        } ?: throw IOException("openOutputStream returned null")
                    } catch (e: Exception) {
                        KardLeafLog.e("RoomNoteRepository", "Failed to write moved note: $resolvedFileName", e)
                        newFile.delete()
                        return@forEach
                    }
                    if (!sourceFile.delete()) {
                        KardLeafLog.e("RoomNoteRepository", "Failed to delete moved source note: $fileName")
                        newFile.delete()
                        return@forEach
                    }

                    val oldPath = joinPath(sourceFolder, fileName)
                    val newPath = joinPath(targetFolder, resolvedFileName)
                    val entity = getFullNoteEntityByPathForAudit(oldPath, "move-note-copy-cached-content")
                    if (entity != null) {
                        val movedRecordId =
                            NoteFormatUtils.extractKardLeafId(NoteFormatUtils.parseFrontMatter(content))
                                ?: entity.recordId.takeIf { recordId -> recordId.isNotBlank() && recordId != oldPath }
                                ?: newPath
                        noteDao.deleteNoteByPath(oldPath)
                        val movedEntity = entity.copy(filePath = newPath, recordId = movedRecordId, folder = targetFolder, fileName = resolvedFileName)
                        noteLinkDao.deleteBySource(oldPath, entity.recordId)
                        noteLinkDao.markTargetUnresolved(oldPath, entity.recordId)
                        noteDao.insertNote(movedEntity)
                        scheduleNoteLinkIndex(movedEntity)
                        movedPaths += MovedNotePath(oldPath = oldPath, newPath = newPath)
                        if (isPinned) {
                            prefsManager.replacePinnedNotePath(oldPath, newPath)
                        }
                        if (entity.isFavorite) {
                            prefsManager.replaceFavoriteNotePath(oldPath, newPath)
                        }
                        if (movedRecordId == newPath) {
                            val movedHistory = noteHistoryDao.replaceNoteId(oldPath, newPath)
                            val movedRemarks = noteRemarkDao.replaceNoteId(oldPath, newPath)
                            syncRecordStoreAfterKeyChange(movedHistory, movedRemarks, oldPath, newPath)
                        } else {
                            val movedHistory = noteHistoryDao.replaceNoteId(oldPath, movedRecordId)
                            val movedRemarks = noteRemarkDao.replaceNoteId(oldPath, movedRecordId)
                            syncNoteRecordsWithResolvedId(newPath, movedRecordId)
                            syncRecordStoreAfterKeyChange(movedHistory, movedRemarks, oldPath, movedRecordId)
                        }
                    }
                } catch (e: Exception) {
                    KardLeafLog.e("RoomNoteRepository", "Failed to move note: $fileName", e)
                }
            }
        }
        if (movedPaths.isNotEmpty()) {
            markWebDavRealtimeLocalDirty()
        }
        movedPaths
    }

    override suspend fun refreshNotes(): RefreshResult =
        refreshNotesInternal(forceReloadIfMetadataUnchanged = false)

    suspend fun refreshNotesFromExternalChange(): RefreshResult =
        refreshNotesInternal(forceReloadIfMetadataUnchanged = true)

    suspend fun refreshSingleNoteByUri(
        uri: Uri,
        bypassCache: Boolean = true,
    ): Note? =
        withContext(Dispatchers.IO) {
            val path = relativeNotePathFromDocumentUri(uri) ?: run {
                return@withContext null
            }
            val file = DocumentFile.fromSingleUri(context, uri)?.takeIf { it.isFile }
            refreshSingleNoteByPathInternal(
                path = path,
                preferredFile = file,
                bypassCache = bypassCache,
            )
        }

    suspend fun refreshSingleNoteByPath(
        path: String,
        bypassCache: Boolean = true,
    ): Note? =
        withContext(Dispatchers.IO) {
            refreshSingleNoteByPathInternal(
                path = normalizeFolderPath(path),
                preferredFile = null,
                bypassCache = bypassCache,
            )
        }

    private suspend fun refreshSingleNoteByPathInternal(
        path: String,
        preferredFile: DocumentFile?,
        bypassCache: Boolean,
    ): Note? {
        if (path.isBlank() || !isMarkdownTextFile(path.substringAfterLast("/"))) {
            return null
        }

        val existing = noteDao.getNoteShellByPath(path)
        val file =
            preferredFile?.takeIf { it.isFile }
                ?: existing?.let { findNoteDocumentDirectFirst(it, traceReason = "refreshSingleNoteByPath") }
                ?: findDocumentByPath(path, traceReason = "refreshSingleNoteByPath.fallbackPath")

        if (file == null || !file.isFile) {
            if (existing != null) {
                noteLinkDao.deleteBySource(path, existing.recordId)
                noteLinkDao.markTargetUnresolved(path, existing.recordId)
                deleteNoteRecordsForPath(path, existing.recordId)
                noteDao.deleteNoteByPath(path)
                reconcileAllWikilinkResolutions()
                fileSignatures.remove(path)
                prefsManager.setNotePinned(path, false)
                prefsManager.setNoteFavorite(path, false)
            }
            return null
        }

        val entity = upsertNoteFromDocument(
            path = path,
            file = file,
            existing = existing,
            bypassCache = bypassCache,
        )
        return entity.toNote()
    }

    private suspend fun upsertNoteFromDocument(
        path: String,
        file: DocumentFile,
        existing: NoteEntity?,
        bypassCache: Boolean,
    ): NoteEntity {
        val rawContent = readText(file, bypassCache = bypassCache)
        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
        val parsedYamlTags = NoteFormatUtils.extractTags(frontMatter)
        val parsedRecordId = NoteFormatUtils.extractKardLeafId(frontMatter) ?: path
        val existingYamlTags = existing?.yamlTags?.let { NoteFormatUtils.tagsFromStorage(it) }.orEmpty()
        if (parsedYamlTags.isNotEmpty() || existingYamlTags.isNotEmpty()) {
            logYamlTagTrace("upsertNoteFromDocument path=$path parsedTags=$parsedYamlTags existingDbTags=$existingYamlTags rawLen=${rawContent.length} bypassCache=$bypassCache")
        }
        val fileName = file.name ?: path.substringAfterLast("/")
        val folderName = existing?.folder ?: path.substringBeforeLast("/", missingDelimiterValue = "")
        val lastModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        val length = file.length()
        val entity =
            NoteEntity(
                filePath = path,
                recordId = parsedRecordId,
                fileName = fileName,
                folder = folderName,
                title = fileName.substringBeforeLast("."),
                contentPreview = frontMatter.cleanContent.take(200),
                content = frontMatter.cleanContent,
                lastModifiedMs = lastModified,
                createdAtMs = NoteFormatUtils.extractCreatedAt(frontMatter) ?: existing?.createdAtMs ?: lastModified,
                color = 0xFFFFFFFF,
                reminder = frontMatter.reminder,
                isPinned = prefsManager.isNotePinned(path),
                isFavorite = prefsManager.isNoteFavorite(path),
                isArchived = existing?.isArchived ?: false,
                isTrashed = existing?.isTrashed ?: false,
                deletedAtMs = existing?.deletedAtMs,
                firstImageReference = extractFirstImageReference(frontMatter.cleanContent).orEmpty(),
                yamlTags = NoteFormatUtils.tagsToStorage(parsedYamlTags),
            )
        noteDao.insertNote(entity)
        scheduleNoteLinkIndex(entity)
        fileSignatures[path] = FileSignature(lastModified, length)
        if (!entity.isTrashed) {
            labelDao.insertAll(folderPathWithParents(entity.folder).map { LabelEntity(it) })
        }
        return entity
    }

    private suspend fun refreshNotesInternal(forceReloadIfMetadataUnchanged: Boolean): RefreshResult =
        withContext(Dispatchers.IO) {
            val refreshStartMs = SystemClock.elapsedRealtime()
            val root = rootDir ?: run {
                logStartupPerf("refreshNotesInternal skip root=null force=$forceReloadIfMetadataUnchanged")
                return@withContext RefreshResult(generation = refreshGeneration.get(), success = false)
            }
            if (!refreshMutex.tryLock()) {
                val activeGeneration = refreshGeneration.get()
                pendingRefresh.set(true)
                if (forceReloadIfMetadataUnchanged) {
                    pendingRefreshForceReload.set(true)
                }
                logStartupPerf("refreshNotesInternal skip busy force=$forceReloadIfMetadataUnchanged")
                return@withContext completedRefreshResult.first { result -> result.generation > activeGeneration }
            }

            var indexingContinuesInBackground = false
            var refreshResult = RefreshResult(generation = refreshGeneration.get(), success = false)
            try {
                val generation = refreshGeneration.incrementAndGet()
                refreshResult = RefreshResult(generation = generation, success = false)
                _isIndexing.value = true
                logStartupPerf("refreshNotesInternal start force=$forceReloadIfMetadataUnchanged thread=${Thread.currentThread().name}")
                if (!recordExternalBackup.refreshFromExternalIfChanged()) {
                    KardLeafLog.w("RoomNoteRepository", "History/remarks refresh failed; keeping Room cache")
                }
                // 1. Get current DB state
                val dbLoadStartMs = SystemClock.elapsedRealtime()
                val dbNotes = noteDao.getAllNoteMetadataSync().associateBy { it.filePath }
                val dbPaths = dbNotes.keys
                val archivedNotesByFileName = dbNotes.values
                    .filter { it.isArchived && !it.isTrashed }
                    .associateBy { it.fileName }
                logStartupPerf(
                    "refreshNotesInternal db loaded count=${dbNotes.size} elapsed=${SystemClock.elapsedRealtime() - dbLoadStartMs}ms total=${SystemClock.elapsedRealtime() - refreshStartMs}ms",
                )

                // 2. Scan file system for file metadata and real folders.
                val fsFiles = mutableMapOf<String, FileMeta>()
                val fsFolders = mutableSetOf<String>()

                val scanStartMs = SystemClock.elapsedRealtime()
                try {
                    val usedFastScan = scanVaultMetaFast(root, fsFiles, fsFolders, archivedNotesByFileName)
                    if (!usedFastScan) {
                        // Scan root notes and user folders recursively.
                        scanFolderMeta(root, "", isArchived = false, isTrashed = false, fsFiles, fsFolders)
                        scanArchiveMeta(root, archivedNotesByFileName, fsFiles)

                        // Scan Trash root files and its label folders.
                        getTrashRoot(root, create = false)?.listFiles()?.let { trashChildren ->
                            trashChildren
                                .filter { it.isFile && isMarkdownTextFile(it.name.orEmpty()) }
                                .forEach { file ->
                                    val fileName = file.name ?: return@forEach
                                    val filePath = fileName
                                    if (fsFiles[filePath]?.isTrashed == false) {
                                        KardLeafLog.w("RoomNoteRepository", "trash scan collision kept active path=$filePath")
                                        return@forEach
                                    }
                                    fsFiles[filePath] =
                                        FileMeta(
                                            file = file,
                                            fileName = fileName,
                                            folderName = "",
                                            lastModified = file.lastModified(),
                                            length = file.length(),
                                            isPinned = false,
                                            isArchived = false,
                                            isTrashed = true,
                                        )
                                }
                            trashChildren.filter { it.isDirectory }.forEach { folder ->
                                folder.name?.let { name ->
                                    scanFolderMeta(folder, name, isArchived = false, isTrashed = true, fsFiles, fsFolders)
                                }
                            }
                        }
                    }
                    logStartupPerf(
                        "refreshNotesInternal scan done files=${fsFiles.size} elapsed=${SystemClock.elapsedRealtime() - scanStartMs}ms total=${SystemClock.elapsedRealtime() - refreshStartMs}ms",
                    )
                } catch (e: Exception) {
                    KardLeafLog.e("RoomNoteRepository", "Error scanning root structure", e)
                    logStartupPerf("refreshNotesInternal scan failed elapsed=${SystemClock.elapsedRealtime() - scanStartMs}ms")
                    return@withContext refreshResult
                }

                val fsPaths = fsFiles.keys

                // 3. Determine changes
                val toDelete = dbPaths.filter { !fsPaths.contains(it) }
                val metadataChangedPaths =
                    fsPaths.filter { path ->
                        val meta = fsFiles[path]!!
                        val dbNote = dbNotes[path]
                        val currentSignature = meta.signature()
                        val previousSignature = fileSignatures[path]

                        dbNote == null ||
                            meta.lastModified != dbNote.lastModifiedMs ||
                            meta.isArchived != dbNote.isArchived ||
                            meta.isTrashed != dbNote.isTrashed ||
                            (dbNote.contentPreview.isEmpty() && meta.length > 0L) ||
                            dbNote.firstImageReference == null ||
                            (previousSignature != null && previousSignature != currentSignature)
                    }

                val isLocalWriteCooldown =
                    SystemClock.elapsedRealtime() - lastLocalWriteElapsedMs < LOCAL_WRITE_OBSERVER_COOLDOWN_MS
                val shouldForceReloadAllContent =
                    forceReloadIfMetadataUnchanged &&
                        !isLocalWriteCooldown &&
                        toDelete.isEmpty() &&
                        metadataChangedPaths.isEmpty() &&
                        fsPaths.isNotEmpty()

                val wikilinksNeedRebuild = !wikilinkPrefs.getBoolean(WIKILINK_REBUILD_V17_KEY, false)

                val toProcess =
                    if (shouldForceReloadAllContent || wikilinksNeedRebuild) {
                        fsPaths.toList()
                    } else {
                        metadataChangedPaths
                    }

                logStartupPerf(
                    "refreshNotesInternal diff db=${dbPaths.size} fs=${fsPaths.size} delete=${toDelete.size} changed=${metadataChangedPaths.size} " +
                        "toProcess=${toProcess.size} forceAll=$shouldForceReloadAllContent localCooldown=$isLocalWriteCooldown total=${SystemClock.elapsedRealtime() - refreshStartMs}ms",
                )

                if (shouldForceReloadAllContent) {
                    clearTextCache()
                }

                // 4. First phase: write metadata-only entities immediately.
                val newMetadataEntities = mutableListOf<NoteEntity>()
                val existingMetadataEntities = mutableListOf<NoteEntity>()
                toProcess.forEach { path ->
                    val meta = fsFiles[path] ?: return@forEach
                    val existing = dbNotes[path]
                    val entity = buildMetadataOnlyEntity(meta, existing)
                    if (existing == null) {
                        newMetadataEntities += entity
                    } else {
                        existingMetadataEntities += entity
                    }
                }

                // 5. Update DB
                val dbWriteStartMs = SystemClock.elapsedRealtime()
                if (toDelete.isNotEmpty()) {
                    toDelete.forEach { path ->
                        val recordId = dbNotes[path]?.recordId ?: path
                        noteLinkDao.deleteBySource(path, recordId)
                        noteLinkDao.markTargetUnresolved(path, recordId)
                        deleteNoteRecordsForPath(path, recordId)
                    }
                    noteDao.deleteNotesByPaths(toDelete)
                    reconcileAllWikilinkResolutions()
                }
                if (newMetadataEntities.isNotEmpty()) {
                    noteDao.insertNotes(newMetadataEntities)
                }
                existingMetadataEntities.forEach { entity ->
                    noteDao.updateNoteMetadata(
                        filePath = entity.filePath,
                        fileName = entity.fileName,
                        folder = entity.folder,
                        title = entity.title,
                        lastModifiedMs = entity.lastModifiedMs,
                        createdAtMs = entity.createdAtMs,
                        color = entity.color,
                        reminder = entity.reminder,
                        isPinned = entity.isPinned,
                        isFavorite = entity.isFavorite,
                        isArchived = entity.isArchived,
                        isTrashed = entity.isTrashed,
                        deletedAtMs = entity.deletedAtMs,
                        firstImageReference = entity.firstImageReference,
                        yamlTags = entity.yamlTags,
                    )
                }

                logStartupPerf(
                    "refreshNotesInternal db write done new=${newMetadataEntities.size} existing=${existingMetadataEntities.size} delete=${toDelete.size} " +
                        "elapsed=${SystemClock.elapsedRealtime() - dbWriteStartMs}ms total=${SystemClock.elapsedRealtime() - refreshStartMs}ms",
                )

                // 6. Sync Labels (Simple approach: rebuild from current valid notes)
                // Ideally we'd do this incrementally too, but labels are lightweight.
                val currentLabels = mutableSetOf<String>()
                try {
                    currentLabels.addAll(fsFolders)
                    fsFiles.values
                        .filter { !it.isTrashed }
                        .forEach { meta ->
                            currentLabels.addAll(folderPathWithParents(meta.folderName))
                        }

                    // Update labels in DB
                    // We can just nuke and rebuild labels as they are just folder names
                    labelDao.deleteAll()
                    labelDao.insertAll(currentLabels.map { LabelEntity(it) })
                } catch (e: Exception) {
                    KardLeafLog.e("RoomNoteRepository", "Error syncing labels", e)
                }

                fileSignatures.clear()
                fileSignatures.putAll(fsFiles.mapValues { it.value.signature() })

                refreshResult =
                    RefreshResult(
                        generation = generation,
                        addedPaths = metadataChangedPaths.filterTo(linkedSetOf()) { path -> dbNotes[path] == null },
                        modifiedPaths = metadataChangedPaths.filterTo(linkedSetOf()) { path -> dbNotes[path] != null },
                        deletedPaths = toDelete.toSet(),
                    )

                if (toProcess.isNotEmpty()) {
                    val contentTargets = toProcess.mapNotNull { path -> fsFiles[path]?.let { path to it } }
                    indexingContinuesInBackground = true
                    _isIndexing.value = true
                    logStartupPerf(
                        "refreshNotesInternal indexing scheduled targets=${contentTargets.size} bypassCache=$shouldForceReloadAllContent total=${SystemClock.elapsedRealtime() - refreshStartMs}ms",
                    )
                    indexingScope.launch {
                        val indexingStartMs = SystemClock.elapsedRealtime()
                        try {
                            indexNoteContentInBackground(
                                targets = contentTargets,
                                existing = dbNotes,
                                bypassCache = shouldForceReloadAllContent,
                                generation = generation,
                            )
                            if (wikilinksNeedRebuild && generation == refreshGeneration.get()) {
                                wikilinkPrefs.edit().putBoolean(WIKILINK_REBUILD_V17_KEY, true).apply()
                            }
                        } finally {
                            _isIndexing.value = false
                            logStartupPerf(
                                "indexing done targets=${contentTargets.size} elapsed=${SystemClock.elapsedRealtime() - indexingStartMs}ms",
                            )
                        }
                    }
                } else {
                    logStartupPerf("refreshNotesInternal indexing not needed total=${SystemClock.elapsedRealtime() - refreshStartMs}ms")
                }
            } catch (e: Exception) {
                KardLeafLog.e("RoomNoteRepository", "Critical error in refreshNotes", e)
            } finally {
                if (!indexingContinuesInBackground) {
                    _isIndexing.value = false
                }
                completedRefreshResult.value = refreshResult
                refreshMutex.unlock()
                logStartupPerf(
                    "refreshNotesInternal done generation=${refreshResult.generation} success=${refreshResult.success} " +
                        "added=${refreshResult.addedPaths.size} modified=${refreshResult.modifiedPaths.size} " +
                        "deleted=${refreshResult.deletedPaths.size} total=${SystemClock.elapsedRealtime() - refreshStartMs}ms",
                )
                if (pendingRefresh.getAndSet(false)) {
                    val pendingForce = pendingRefreshForceReload.getAndSet(false)
                    logStartupPerf("refreshNotesInternal run pending force=$pendingForce")
                    refreshNotesInternal(forceReloadIfMetadataUnchanged = pendingForce)
                }
            }
            refreshResult
        }

    private fun buildMetadataOnlyEntity(
        meta: FileMeta,
        existing: NoteMetadataEntity?,
    ): NoteEntity {
        val path = joinPath(meta.folderName, meta.fileName)
        val existingTags = existing?.yamlTags?.let { NoteFormatUtils.tagsFromStorage(it) }.orEmpty()
        if (existingTags.isNotEmpty()) {
            logYamlTagTrace("buildMetadataOnlyEntity path=$path keepExistingTags=$existingTags lastModified=${meta.lastModified}")
        }
        return NoteEntity(
            filePath = path,
            recordId = existing?.recordId?.takeIf { it.isNotBlank() } ?: path,
            fileName = meta.fileName,
            folder = meta.folderName,
            title = meta.fileName.substringBeforeLast("."),
            contentPreview = existing?.contentPreview.orEmpty().take(200),
            content = "",
            lastModifiedMs = meta.lastModified,
            createdAtMs = existing?.createdAtMs ?: meta.lastModified,
            color = 0xFFFFFFFF,
            reminder = existing?.reminder,
            isPinned = prefsManager.isNotePinned(path),
            isFavorite = prefsManager.isNoteFavorite(path),
            isArchived = meta.isArchived,
            isTrashed = meta.isTrashed,
            deletedAtMs = if (meta.isTrashed) existing?.deletedAtMs ?: meta.lastModified else null,
            firstImageReference = existing?.firstImageReference,
            yamlTags = existing?.yamlTags.orEmpty(),
        )
    }

    private suspend fun indexNoteContentInBackground(
        targets: List<Pair<String, FileMeta>>,
        existing: Map<String, NoteMetadataEntity>,
        bypassCache: Boolean,
        generation: Long,
    ) {
        targets.chunked(25).forEachIndexed { batchIndex, batch ->
            if (generation != refreshGeneration.get()) return
            val batchStartMs = SystemClock.elapsedRealtime()
            val notesToUpsert =
                batch.mapNotNull { (path, meta) ->
                    try {
                        val rawContent = readText(meta.file, bypassCache = bypassCache)
                        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
                        val parsedYamlTags = NoteFormatUtils.extractTags(frontMatter)
                        val parsedRecordId = NoteFormatUtils.extractKardLeafId(frontMatter) ?: path
                        val existingYamlTags = existing[path]?.yamlTags?.let { NoteFormatUtils.tagsFromStorage(it) }.orEmpty()
                        if (parsedYamlTags.isNotEmpty() || existingYamlTags.isNotEmpty()) {
                            logYamlTagTrace("indexNoteContent path=$path parsedTags=$parsedYamlTags existingDbTags=$existingYamlTags rawLen=${rawContent.length} bypassCache=$bypassCache")
                        }
                        NoteEntity(
                            filePath = path,
                            recordId = parsedRecordId,
                            fileName = meta.fileName,
                            folder = meta.folderName,
                            title = meta.fileName.substringBeforeLast("."),
                            contentPreview = frontMatter.cleanContent.take(200),
                            content = frontMatter.cleanContent,
                            lastModifiedMs = meta.lastModified,
                            createdAtMs = NoteFormatUtils.extractCreatedAt(frontMatter) ?: existing[path]?.createdAtMs ?: meta.lastModified,
                            color = 0xFFFFFFFF,
                            reminder = frontMatter.reminder,
                            isPinned = prefsManager.isNotePinned(path),
                            isFavorite = prefsManager.isNoteFavorite(path),
                            isArchived = meta.isArchived,
                            isTrashed = meta.isTrashed,
                            deletedAtMs = if (meta.isTrashed) existing[path]?.deletedAtMs ?: meta.lastModified else null,
                            firstImageReference = extractFirstImageReference(frontMatter.cleanContent).orEmpty(),
                            yamlTags = NoteFormatUtils.tagsToStorage(parsedYamlTags),
                        )
                    } catch (e: Exception) {
                        KardLeafLog.e("RoomNoteRepository", "Error indexing file: $path", e)
                        null
                    }
            }
            if (notesToUpsert.isNotEmpty()) {
                if (generation != refreshGeneration.get()) return
                val currentByPath = noteDao.getNoteShellsByPaths(notesToUpsert.map { it.filePath }).associateBy { it.filePath }
                val freshNotes = notesToUpsert.filter { note ->
                    currentByPath[note.filePath]?.let { current ->
                        current.lastModifiedMs == note.lastModifiedMs &&
                            current.fileName == note.fileName &&
                            current.folder == note.folder &&
                            current.isArchived == note.isArchived &&
                            current.isTrashed == note.isTrashed
                    } == true
                }
                if (freshNotes.isNotEmpty()) {
                    noteDao.insertNotes(freshNotes)
                    val metadata = noteDao.getAllNoteMetadataSync()
                    freshNotes.forEach { entity ->
                        val sourceKey = entity.filePath
                        val version = noteLinkSourceVersions.getOrPut(sourceKey) { AtomicLong() }.incrementAndGet()
                        runCatching { indexNoteLinksForEntity(entity, metadata, version) }
                            .onFailure { error -> KardLeafLog.e("KardLeafWikiLinkTrace", "index batch failed sourcePath=${entity.filePath}", error) }
                    }
                    runCatching { reconcileAllWikilinkResolutions() }
                        .onFailure { error -> KardLeafLog.e("KardLeafWikiLinkTrace", "reconcile batch failed", error) }
                }
            }
            val batchElapsedMs = SystemClock.elapsedRealtime() - batchStartMs
            if (batchIndex < 8 || batchElapsedMs >= 40L) {
                logStartupPerf(
                    "indexing batch#${batchIndex + 1} size=${batch.size} upsert=${notesToUpsert.size} elapsed=${batchElapsedMs}ms",
                )
            }
        }
    }

    private data class FileMeta(
        val file: DocumentFile,
        val fileName: String,
        val folderName: String,
        val lastModified: Long,
        val length: Long,
        val isPinned: Boolean,
        val isArchived: Boolean,
        val isTrashed: Boolean,
    ) {
        fun signature() = FileSignature(lastModified = lastModified, length = length)
    }

    private data class SafChild(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val lastModified: Long,
        val size: Long,
    ) {
        val isDirectory: Boolean
            get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
    }

    private fun scanVaultMetaFast(
        root: DocumentFile,
        output: MutableMap<String, FileMeta>,
        folderOutput: MutableSet<String>,
        archivedNotesByFileName: Map<String, NoteMetadataEntity>,
    ): Boolean {
        val treeUri = rootTreeUri ?: return false
        val rootDocumentId = currentRootDocumentId() ?: return false

        scanSafFolderMeta(
            treeUri = treeUri,
            documentId = rootDocumentId,
            folderName = "",
            isArchived = false,
            isTrashed = false,
            output = output,
            folderOutput = folderOutput,
        )
        scanSafArchiveMeta(treeUri, rootDocumentId, archivedNotesByFileName, output)

        val trashName = prefsManager.getTrashFolderName()
        val trashDocumentId =
            findSafChildDirectoryId(treeUri, rootDocumentId, trashName, failOnError = true)
                ?: if (trashName != "Trash") {
                    findSafChildDirectoryId(treeUri, rootDocumentId, "Trash", failOnError = true)
                } else {
                    null
                }
        if (trashDocumentId != null) {
            val trashChildren = querySafChildren(treeUri, trashDocumentId, failOnError = true)
            trashChildren
                .filter { !it.isDirectory && isMarkdownTextFile(it.name) }
                .forEach { child ->
                    addSafFileMeta(
                        treeUri = treeUri,
                        child = child,
                        folderName = "",
                        isPinned = false,
                        isArchived = false,
                        isTrashed = true,
                        output = output,
                    )
                }
            trashChildren
                .filter { it.isDirectory }
                .filter { it.name.isNotBlank() && !it.name.startsWith(".") }
                .forEach { folder ->
                    scanSafFolderMeta(
                        treeUri = treeUri,
                        documentId = folder.documentId,
                        folderName = folder.name,
                        isArchived = false,
                        isTrashed = true,
                        output = output,
                        folderOutput = folderOutput,
                    )
                }
        }

        return true
    }

    private fun scanSafArchiveMeta(
        treeUri: Uri,
        rootDocumentId: String,
        archivedNotesByFileName: Map<String, NoteMetadataEntity>,
        output: MutableMap<String, FileMeta>,
    ) {
        val systemDocumentId = findSafChildDirectoryId(treeUri, rootDocumentId, ".KardLeaf", failOnError = true) ?: return
        querySafChildren(treeUri, systemDocumentId, failOnError = true)
            .firstOrNull { !it.isDirectory && it.name == TASK_STORE_FILE }
            ?.let { taskFile ->
                addSafFileMeta(
                    treeUri = treeUri,
                    child = taskFile,
                    folderName = TASK_STORE_FOLDER,
                    isPinned = false,
                    isArchived = false,
                    isTrashed = false,
                    output = output,
                )
            }
        val archiveDocumentId = findSafChildDirectoryId(treeUri, systemDocumentId, "Archive", failOnError = true) ?: return
        querySafChildren(treeUri, archiveDocumentId, failOnError = true)
            .filter { !it.isDirectory && isMarkdownTextFile(it.name) }
            .forEach { child ->
                val folderName = archivedNotesByFileName[child.name]?.folder.orEmpty()
                val path = joinPath(folderName, child.name)
                if (output[path]?.isArchived == false) {
                    KardLeafLog.w("RoomNoteRepository", "archive scan collision kept active path=$path")
                    return@forEach
                }
                addSafFileMeta(
                    treeUri = treeUri,
                    child = child,
                    folderName = folderName,
                    isPinned = false,
                    isArchived = true,
                    isTrashed = false,
                    output = output,
                )
            }
    }

    private fun scanSafFolderMeta(
        treeUri: Uri,
        documentId: String,
        folderName: String,
        isArchived: Boolean,
        isTrashed: Boolean,
        output: MutableMap<String, FileMeta>,
        folderOutput: MutableSet<String>,
    ) {
        if (!isTrashed) {
            folderOutput.addAll(folderPathWithParents(folderName))
        }
        val children = querySafChildren(treeUri, documentId, failOnError = true)

        children
            .asSequence()
            .filter { !it.isDirectory && isMarkdownTextFile(it.name) }
            .forEach { child ->
                addSafFileMeta(
                    treeUri = treeUri,
                    child = child,
                    folderName = folderName,
                    isPinned = false,
                    isArchived = isArchived,
                    isTrashed = isTrashed,
                    output = output,
                )
            }

        if (!isArchived && !isTrashed) {
            children
                .firstOrNull { it.isDirectory && it.name == "Archived" }
                ?.let { archivedFolder ->
                    querySafChildren(treeUri, archivedFolder.documentId, failOnError = true)
                        .asSequence()
                        .filter { !it.isDirectory && isMarkdownTextFile(it.name) }
                        .forEach { child ->
                            addSafFileMeta(
                                treeUri = treeUri,
                                child = child,
                                folderName = folderName,
                                isPinned = false,
                                isArchived = true,
                                isTrashed = false,
                                output = output,
                            )
                        }
                }
        }

        children
            .asSequence()
            .filter { it.isDirectory }
            .filter { child -> isUserLabelFolderName(child.name) }
            .forEach { child ->
                scanSafFolderMeta(
                    treeUri = treeUri,
                    documentId = child.documentId,
                    folderName = joinPath(folderName, child.name),
                    isArchived = isArchived,
                    isTrashed = isTrashed,
                    output = output,
                    folderOutput = folderOutput,
                )
            }
    }

    private fun addSafFileMeta(
        treeUri: Uri,
        child: SafChild,
        folderName: String,
        isPinned: Boolean,
        isArchived: Boolean,
        isTrashed: Boolean,
        output: MutableMap<String, FileMeta>,
    ) {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.documentId)
        val file = DocumentFile.fromSingleUri(context, docUri)
        if (file == null) {
            return
        }
        val filePath = joinPath(folderName, child.name)
        if (isTrashed && output[filePath]?.isTrashed == false) {
            KardLeafLog.w("RoomNoteRepository", "trash scan collision kept active path=$filePath")
            return
        }
        output[filePath] =
            FileMeta(
                file = file,
                fileName = child.name,
                folderName = folderName,
                lastModified = child.lastModified,
                length = child.size,
                isPinned = isPinned,
                isArchived = isArchived,
                isTrashed = isTrashed,
            )
    }

    private fun querySafChildren(
        treeUri: Uri,
        documentId: String,
        failOnError: Boolean = false,
    ): List<SafChild> {
        val startMs = SystemClock.elapsedRealtime()
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            )
        return runCatching {
            val queryStartMs = SystemClock.elapsedRealtime()
            val result =
                context.contentResolver.query(childUri, projection, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                    val result = mutableListOf<SafChild>()
                    while (cursor.moveToNext()) {
                        val childDocumentId = cursor.getStringOrNull(idIndex) ?: continue
                        val name = cursor.getStringOrNull(nameIndex) ?: continue
                        val mimeType = cursor.getStringOrNull(mimeIndex).orEmpty()
                        result +=
                            SafChild(
                                documentId = childDocumentId,
                                name = name,
                                mimeType = mimeType,
                                lastModified = cursor.getLongOrZero(modifiedIndex),
                                size = cursor.getLongOrZero(sizeIndex),
                            )
                    }
                    result
                } ?: if (failOnError) {
                    error("SAF query returned no cursor for $documentId")
                } else {
                    emptyList()
                }
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            if (elapsedMs >= 48L || result.size >= 100) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external querySafChildren documentId=$documentId count=${result.size} " +
                        "queryElapsed=${SystemClock.elapsedRealtime() - queryStartMs}ms totalElapsed=${elapsedMs}ms",
                )
            }
            result
        }.getOrElse { error ->
            KardLeafLog.w(
                OPEN_PATH_PROBE_TAG,
                "external querySafChildren failed documentId=$documentId elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                error,
            )
            if (failOnError) throw IllegalStateException("SAF scan failed for $documentId", error)
            emptyList()
        }
    }

    private fun findSafChildDirectoryId(
        treeUri: Uri,
        parentDocumentId: String,
        name: String,
        failOnError: Boolean = false,
    ): String? =
        querySafChildren(treeUri, parentDocumentId, failOnError = failOnError)
            .firstOrNull { it.isDirectory && it.name == name }
            ?.documentId

    private fun android.database.Cursor.getStringOrNull(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun android.database.Cursor.getLongOrZero(index: Int): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else 0L

    private fun relativeNotePathFromDocumentUri(uri: Uri): String? {
        val treeUri = rootTreeUri ?: return null
        val rootDocumentId = currentRootDocumentId() ?: return null
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        if (documentId == rootDocumentId) return null
        if (!documentId.startsWith("$rootDocumentId/")) return null
        val relativePath = normalizeFolderPath(documentId.removePrefix("$rootDocumentId/"))
        return relativePath.takeIf { isMarkdownTextFile(it.substringAfterLast("/")) }
    }

    private fun findDocumentByPath(
        path: String,
        traceReason: String? = null,
    ): DocumentFile? {
        val startMs = SystemClock.elapsedRealtime()
        val root = rootDir ?: run {
            if (traceReason != null) {
                KardLeafLog.w(OPEN_PATH_PROBE_TAG, "external findDocumentByPath noRoot reason=$traceReason path=$path")
            }
            return null
        }
        val normalized = normalizeFolderPath(path)
        val folder = normalized.substringBeforeLast("/", missingDelimiterValue = "")
        val fileName = normalized.substringAfterLast("/")
        if (traceReason != null) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external findDocumentByPath start reason=$traceReason path=$path folder=$folder fileName=$fileName",
            )
        }
        val folderStartMs = SystemClock.elapsedRealtime()
        val parentFolder = findFolder(root, folder, traceReason = traceReason?.let { "$it.parent" })
        val folderElapsedMs = SystemClock.elapsedRealtime() - folderStartMs
        val findFileStartMs = SystemClock.elapsedRealtime()
        val result = parentFolder?.findFile(fileName)?.takeIf { it.isFile }
        val findFileElapsedMs = SystemClock.elapsedRealtime() - findFileStartMs
        if (traceReason != null || folderElapsedMs >= 16L || findFileElapsedMs >= 16L) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external findDocumentByPath done reason=${traceReason ?: "slow"} path=$path folder=$folder fileName=$fileName " +
                    "parentFound=${parentFolder != null} fileFound=${result != null} folderElapsed=${folderElapsedMs}ms " +
                    "findFileElapsed=${findFileElapsedMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
        }
        return result
    }

    private fun isMarkdownTextFile(fileName: String): Boolean =
        fileName.endsWith(".md", ignoreCase = true) || fileName.endsWith(".txt", ignoreCase = true)

    private fun addFileNameMarker(
        fileName: String,
        marker: String,
    ): String {
        val extensionIndex = fileName.lastIndexOf('.').takeIf { it > 0 } ?: fileName.length
        return fileName.substring(0, extensionIndex) + marker + fileName.substring(extensionIndex)
    }

    private fun findAvailableTrashFileName(
        targetFolder: DocumentFile,
        preferredFileName: String,
    ): String? {
        if (targetFolder.findFile(preferredFileName) == null) return preferredFileName

        val timestamp = System.currentTimeMillis()
        for (index in 0 until 100) {
            val suffix = if (index == 0) "$TRASH_FILE_MARKER$timestamp" else "$TRASH_FILE_MARKER$timestamp-$index"
            val candidate = addFileNameMarker(preferredFileName, suffix)
            if (targetFolder.findFile(candidate) == null) return candidate
        }
        KardLeafLog.e("RoomNoteRepository", "moveNoteToTrash failed to allocate target name: $preferredFileName")
        return null
    }

    private fun originalFileNameFromTrash(fileName: String): String {
        val markerIndex = fileName.lastIndexOf(TRASH_FILE_MARKER)
        if (markerIndex <= 0) return fileName
        val extensionIndex = fileName.lastIndexOf('.')
        if (extensionIndex <= markerIndex + TRASH_FILE_MARKER.length) return fileName
        return fileName.substring(0, markerIndex) + fileName.substring(extensionIndex)
    }

    private fun findAvailableFileName(
        targetFolder: DocumentFile,
        preferredFileName: String,
    ): String? {
        if (targetFolder.findFile(preferredFileName) == null) return preferredFileName

        val extensionIndex = preferredFileName.lastIndexOf('.').takeIf { it > 0 } ?: preferredFileName.length
        val baseName = preferredFileName.substring(0, extensionIndex)
        val extension = preferredFileName.substring(extensionIndex)
        for (index in 1..100) {
            val candidate = "$baseName ($index)$extension"
            if (targetFolder.findFile(candidate) == null) return candidate
        }
        KardLeafLog.e("RoomNoteRepository", "restoreNote failed to allocate target name: $preferredFileName")
        return null
    }

    private fun isUserLabelFolderName(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith(".") &&
            name != "Archived" &&
            name != prefsManager.getTrashFolderName() &&
            name != "Trash"

    private fun scanArchiveMeta(
        root: DocumentFile,
        archivedNotesByFileName: Map<String, NoteMetadataEntity>,
        output: MutableMap<String, FileMeta>,
    ) {
        findFolder(root, TASK_STORE_FOLDER)
            ?.findFile(TASK_STORE_FILE)
            ?.takeIf { it.isFile }
            ?.let { taskFile ->
                output[joinPath(TASK_STORE_FOLDER, TASK_STORE_FILE)] = FileMeta(
                    file = taskFile,
                    fileName = TASK_STORE_FILE,
                    folderName = TASK_STORE_FOLDER,
                    lastModified = taskFile.lastModified(),
                    length = taskFile.length(),
                    isPinned = false,
                    isArchived = false,
                    isTrashed = false,
                )
            }
        val archiveFolder = getArchiveFolder(root, create = false) ?: return
        archiveFolder.listFiles()
            .filter { it.isFile && isMarkdownTextFile(it.name.orEmpty()) }
            .forEach { file ->
                val fileName = file.name ?: return@forEach
                val folderName = archivedNotesByFileName[fileName]?.folder.orEmpty()
                val path = joinPath(folderName, fileName)
                if (output[path]?.isArchived == false) {
                    KardLeafLog.w("RoomNoteRepository", "archive scan collision kept active path=$path")
                    return@forEach
                }
                output[path] = FileMeta(
                    file = file,
                    fileName = fileName,
                    folderName = folderName,
                    lastModified = file.lastModified(),
                    length = file.length(),
                    isPinned = false,
                    isArchived = true,
                    isTrashed = false,
                )
            }
    }

    private fun scanFolderMeta(
        folder: DocumentFile,
        folderName: String,
        isArchived: Boolean,
        isTrashed: Boolean,
        output: MutableMap<String, FileMeta>,
        folderOutput: MutableSet<String>,
    ) {
        if (!isTrashed) {
            folderOutput.addAll(folderPathWithParents(folderName))
        }

        fun processFiles(
            dir: DocumentFile,
            isPinned: Boolean,
            isArchiveTarget: Boolean,
        ) {
            try {
                val listStartMs = SystemClock.elapsedRealtime()
                val listed = dir.listFiles()
                val listElapsedMs = SystemClock.elapsedRealtime() - listStartMs
                if (listElapsedMs >= 48L || listed.size >= 100) {
                    KardLeafLog.d(
                        OPEN_PATH_PROBE_TAG,
                        "external scanFolder listFiles folder=$folderName dir=${dir.name} count=${listed.size} " +
                            "elapsed=${listElapsedMs}ms pinned=$isPinned archived=$isArchiveTarget trashed=$isTrashed",
                    )
                }
                listed.filter { it.isFile && (it.name?.endsWith(".md") == true || it.name?.endsWith(".txt") == true) }.forEach {
                        file ->
                    val fileName = file.name ?: return@forEach
                    val filePath = joinPath(folderName, fileName)
                    if (isTrashed && output[filePath]?.isTrashed == false) {
                        KardLeafLog.w("RoomNoteRepository", "trash scan collision kept active path=$filePath")
                        return@forEach
                    }
                    output[filePath] =
                        FileMeta(
                            file = file,
                            fileName = fileName,
                            folderName = folderName,
                            lastModified = file.lastModified(),
                            length = file.length(),
                            isPinned = isPinned,
                            isArchived = isArchiveTarget,
                            isTrashed = isTrashed,
                        )
                }
            } catch (e: Exception) {
                KardLeafLog.e("RoomNoteRepository", "Error scanning folder: ${dir.uri}", e)
                throw e
            }
        }

        processFiles(folder, isPinned = false, isArchiveTarget = isArchived)

        if (!isArchived && !isTrashed) {
            try {
                folder.findFile("Archived")?.let {
                    processFiles(it, isPinned = false, isArchiveTarget = true)
                }
            } catch (e: Exception) {
                KardLeafLog.e("RoomNoteRepository", "Error scanning subfolders in $folderName", e)
                throw e
            }
        }

        val childListStartMs = SystemClock.elapsedRealtime()
        val children = folder.listFiles()
        val childListElapsedMs = SystemClock.elapsedRealtime() - childListStartMs
        if (childListElapsedMs >= 48L || children.size >= 100) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external scanFolder children folder=$folderName dir=${folder.name} count=${children.size} " +
                    "elapsed=${childListElapsedMs}ms archived=$isArchived trashed=$isTrashed",
            )
        }
        children
            .filter { it.isDirectory }
            .filter { child ->
                val name = child.name.orEmpty()
                name.isNotBlank() &&
                    !name.startsWith(".") &&
                    name != "Archived" &&
                    name != prefsManager.getTrashFolderName() &&
                    name != "Trash"
            }
            .forEach { child ->
                val childName = child.name ?: return@forEach
                scanFolderMeta(
                    folder = child,
                    folderName = joinPath(folderName, childName),
                    isArchived = isArchived,
                    isTrashed = isTrashed,
                    output = output,
                    folderOutput = folderOutput,
                )
            }
    }

    private suspend fun moveNoteToSystemFolder(
        id: String,
        isArchive: Boolean
    ): Boolean {
        val entity = noteDao.getNoteShellByPath(id) ?: return false
        return moveNoteEntitiesToSystemFolder(listOf(entity), isArchive).isNotEmpty()
    }

    private suspend fun moveNoteEntitiesToTrash(
        entities: List<NoteEntity>,
    ): List<TrashMoveResult> {
        val root = rootDir ?: return emptyList()
        val trashRoot = getTrashRoot(root, create = true) ?: return emptyList()
        val deletedAtMs = System.currentTimeMillis()
        val movedNotes = mutableListOf<TrashMoveResult>()

        entities.forEach { entity ->
            val sourceFile = findMoveSourceDocument(
                root = root,
                folder = entity.folder,
                fileName = entity.fileName,
                isArchived = entity.isArchived,
                isTrashed = false,
                isPinned = entity.isPinned,
            )
            if (sourceFile == null) {
                KardLeafLog.e("RoomNoteRepository", "Missing source note file for trash move: ${entity.filePath}")
                return@forEach
            }

            val targetFolder = getOrCreateFolder(trashRoot, entity.folder) ?: run {
                KardLeafLog.e("RoomNoteRepository", "Failed to create trash folder for: ${entity.filePath}")
                return@forEach
            }
            val trashFileName = findAvailableTrashFileName(targetFolder, entity.fileName) ?: return@forEach
            val trashPath = joinPath(entity.folder, trashFileName)
            val targetFile = moveMarkdownDocumentReturningTarget(
                sourceFile = sourceFile,
                targetFolder = targetFolder,
                fileName = trashFileName,
                reason = "moveNoteToTrash",
            ) ?: return@forEach

            val roomUpdated = runCatching {
                noteDao.moveNoteToTrashPath(
                    sourcePath = entity.filePath,
                    trashPath = trashPath,
                    trashFileName = trashFileName,
                    deletedAtMs = deletedAtMs,
                ) == 1
            }.onFailure { error ->
                KardLeafLog.e("RoomNoteRepository", "moveNoteToTrash Room update failed: ${entity.filePath}", error)
            }.getOrDefault(false)

            if (!roomUpdated) {
                val sourceFolder = getOrCreateFolder(root, entity.folder)
                val rolledBack = sourceFolder != null && moveMarkdownDocument(
                    sourceFile = targetFile,
                    targetFolder = sourceFolder,
                    fileName = entity.fileName,
                    reason = "moveNoteToTrash rollback",
                )
                KardLeafLog.e(
                    "RoomNoteRepository",
                    "moveNoteToTrash Room update rejected source=${entity.filePath} trash=$trashPath rolledBack=$rolledBack",
                )
                return@forEach
            }

            fileSignatures.remove(entity.filePath)
            fileSignatures[trashPath] = FileSignature(targetFile.lastModified(), targetFile.length())
            KardLeafLog.d(
                "RoomNoteRepository",
                "moveNoteToTrash done source=${entity.filePath} trash=$trashPath renamed=${trashFileName != entity.fileName}",
            )
            movedNotes += TrashMoveResult(sourcePath = entity.filePath, trashPath = trashPath)
        }
        return movedNotes
    }

    private suspend fun moveNoteEntitiesToSystemFolder(
        entities: List<NoteEntity>,
        isArchive: Boolean
    ): List<NoteEntity> {
        val root = rootDir ?: return emptyList()
        val movedEntities = mutableListOf<NoteEntity>()
        entities.forEach { entity ->
            val folder = entity.folder
            val fileName = entity.fileName

            val sourceFile = findMoveSourceDocument(
                root = root,
                folder = folder,
                fileName = fileName,
                isArchived = entity.isArchived,
                isTrashed = entity.isTrashed,
                isPinned = entity.isPinned,
            )

            if (sourceFile == null) {
                KardLeafLog.e("RoomNoteRepository", "Missing source note file for system move: ${entity.filePath}")
                return@forEach
            }

            val targetLabelFolder = if (isArchive) {
                getArchiveFolder(root, create = true)
            } else {
                val sysRoot = getTrashRoot(root, create = true)
                sysRoot?.let { getOrCreateFolder(it, folder) }
            }

            if (targetLabelFolder != null &&
                moveMarkdownDocument(sourceFile, targetLabelFolder, fileName, "moveNoteToSystemFolder")
            ) {
                movedEntities += entity
            }
        }
        return movedEntities
    }

    private fun findMoveSourceDocument(
        root: DocumentFile,
        folder: String,
        fileName: String,
        isArchived: Boolean,
        isTrashed: Boolean,
        isPinned: Boolean,
    ): DocumentFile? {
        val effectiveRoot = if (isTrashed) getTrashRoot(root, create = false) else root
        if (isArchived && !isTrashed) {
            findArchiveNoteDocument(effectiveRoot ?: root, fileName)?.let { return it }
        }
        val sourceFolderDoc = effectiveRoot?.let { findFolder(it, folder) } ?: return null
        return when {
            isTrashed -> sourceFolderDoc.findFile(fileName)
            isArchived -> sourceFolderDoc.findFile("Archived")?.findFile(fileName)
            isPinned -> sourceFolderDoc.findFile("Pinned")?.findFile(fileName)
                ?: sourceFolderDoc.findFile(fileName)
            else -> sourceFolderDoc.findFile(fileName)
        }?.takeIf { it.isFile }
    }

    private suspend fun moveMarkdownDocument(
        sourceFile: DocumentFile,
        targetFolder: DocumentFile,
        fileName: String,
        reason: String,
    ): Boolean =
        moveMarkdownDocumentReturningTarget(sourceFile, targetFolder, fileName, reason) != null

    private suspend fun moveMarkdownDocumentReturningTarget(
        sourceFile: DocumentFile,
        targetFolder: DocumentFile,
        fileName: String,
        reason: String,
    ): DocumentFile? {
        if (targetFolder.findFile(fileName) != null) {
            KardLeafLog.e("RoomNoteRepository", "$reason target conflict: $fileName")
            return null
        }
        val newFile = targetFolder.createFile("text/markdown", fileName) ?: run {
            KardLeafLog.e("RoomNoteRepository", "$reason failed to create target: $fileName")
            return null
        }
        val sourceLength = sourceFile.length()
        var copiedBytes = -1L
        try {
            context.contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                    copiedBytes = input.copyTo(output)
                    output.flush()
                } ?: throw IOException("openOutputStream returned null")
            } ?: throw IOException("openInputStream returned null")
        } catch (e: Exception) {
            KardLeafLog.e("RoomNoteRepository", "$reason failed to copy target: $fileName", e)
            newFile.delete()
            return null
        }
        val targetLength = newFile.length()
        if ((sourceLength > 0L && copiedBytes != sourceLength) ||
            (targetLength > 0L && targetLength != copiedBytes)
        ) {
            KardLeafLog.e(
                "RoomNoteRepository",
                "$reason copy length mismatch: $fileName source=$sourceLength copied=$copiedBytes target=$targetLength",
            )
            newFile.delete()
            return null
        }
        if (!sourceFile.delete()) {
            KardLeafLog.e("RoomNoteRepository", "$reason failed to delete source: $fileName")
            newFile.delete()
            return null
        }
        return newFile
    }

    private fun readCachedLibraryCharacterCount(): Long? {
        val currentRoot = prefsManager.getRootUri().orEmpty()
        val cachedRoot = heatmapStatsPrefs.getString(HEATMAP_STATS_ROOT_KEY, null)
        return heatmapStatsPrefs.getLong(HEATMAP_STATS_VALUE_KEY, -1L)
            .takeIf { cachedRoot == currentRoot && it >= 0L }
    }

    private fun currentHeatmapStatsDayKey(): Long =
        Calendar.getInstance().let { calendar ->
            calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
        }

    private fun NoteMetadataEntity.toLookupNoteEntity(): NoteEntity =
        NoteEntity(
            filePath = filePath,
            recordId = recordId,
            fileName = fileName,
            folder = folder,
            title = title,
            contentPreview = contentPreview,
            content = "",
            lastModifiedMs = lastModifiedMs,
            createdAtMs = createdAtMs,
            color = color,
            reminder = reminder,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isArchived = isArchived,
            isTrashed = isTrashed,
            deletedAtMs = deletedAtMs,
            firstImageReference = firstImageReference,
            yamlTags = yamlTags,
        )

    private suspend fun countHeatmapCharacters(file: DocumentFile): Long? {
        return try {
            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { reader ->
                var characterCount = 0L
                var lineIndex = 0
                var inFrontMatter = false
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    val skipLine = when {
                        lineIndex == 0 && trimmed == "---" -> {
                            inFrontMatter = true
                            true
                        }
                        inFrontMatter && trimmed == "---" -> {
                            inFrontMatter = false
                            true
                        }
                        inFrontMatter -> true
                        else -> false
                    }
                    if (!skipLine) {
                        characterCount += line.count { !it.isWhitespace() }.toLong()
                    }
                    lineIndex++
                }
                characterCount
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            KardLeafLog.w(HEATMAP_STATS_TAG, "library character count read failed uri=${file.uri}", error)
            null
        }
    }

    private suspend fun readText(
        file: DocumentFile,
        bypassCache: Boolean = false,
    ): String = textCache.read(file, bypassCache).orEmpty()

    private suspend fun readTextOrNull(
        file: DocumentFile,
        bypassCache: Boolean = false,
    ): String? = textCache.read(file, bypassCache)

    private suspend fun updateTextCache(
        file: DocumentFile,
        text: String,
    ) = textCache.update(file, text)

    private suspend fun clearTextCache() = textCache.clear()

    private data class NoteFileReadResult(
        val entity: NoteEntity,
        val noteType: String?,
        val sourceType: String?,
        val sourceUrl: String?,
        val updatedAtMs: Long?,
    )

    private suspend fun readNoteFromFileForEditor(
        entity: NoteEntity,
        file: DocumentFile,
    ): NoteFileReadResult? {
        val startMs = SystemClock.elapsedRealtime()
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "repo readNoteForEditor start path=${entity.filePath} fileName=${file.name} length=${file.length()}",
        )
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external readNoteForEditor start path=${entity.filePath} folder=${entity.folder} fileName=${file.name} length=${file.length()}",
        )
        val rawContent = readTextOrNull(file) ?: run {
            KardLeafLog.w(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo readNoteForEditor readText missing path=${entity.filePath} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return null
        }
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "repo readNoteForEditor readText done path=${entity.filePath} rawLen=${rawContent.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        val parseStartMs = SystemClock.elapsedRealtime()
        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "repo readNoteForEditor parseFrontMatter done path=${entity.filePath} cleanLen=${frontMatter.cleanContent.length} " +
                "parseElapsed=${SystemClock.elapsedRealtime() - parseStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        val tagsStartMs = SystemClock.elapsedRealtime()
        val parsedTags = NoteFormatUtils.extractTags(frontMatter)
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "repo readNoteForEditor extractTags done path=${entity.filePath} tags=${parsedTags.size} " +
                "tagsElapsed=${SystemClock.elapsedRealtime() - tagsStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        val recordIdStartMs = SystemClock.elapsedRealtime()
        val parsedRecordId = NoteFormatUtils.extractKardLeafId(frontMatter) ?: entity.filePath
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "repo readNoteForEditor extractRecordId done path=${entity.filePath} recordIdElapsed=${SystemClock.elapsedRealtime() - recordIdStartMs}ms " +
                "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        val imageRefStartMs = SystemClock.elapsedRealtime()
        val firstImageReference = extractFirstImageReference(frontMatter.cleanContent).orEmpty()
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "repo readNoteForEditor extractFirstImage done path=${entity.filePath} imageRefLen=${firstImageReference.length} " +
                "imageElapsed=${SystemClock.elapsedRealtime() - imageRefStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "repo readNoteForEditor parsed path=${entity.filePath} rawLen=${rawContent.length} cleanLen=${frontMatter.cleanContent.length} " +
                "tags=${parsedTags.size} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external readNoteForEditor parsed path=${entity.filePath} rawLen=${rawContent.length} cleanLen=${frontMatter.cleanContent.length} " +
                "tags=${parsedTags.size} firstImageRefLen=${firstImageReference.length} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        return NoteFileReadResult(
            entity = entity.copy(
                recordId = parsedRecordId,
                title = file.name?.substringBeforeLast(".") ?: entity.title,
                contentPreview = frontMatter.cleanContent.take(200),
                content = frontMatter.cleanContent,
                lastModifiedMs = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                createdAtMs = NoteFormatUtils.extractCreatedAt(frontMatter) ?: entity.createdAtMs,
                color = 0xFFFFFFFF,
                reminder = frontMatter.reminder,
                firstImageReference = firstImageReference,
                yamlTags = NoteFormatUtils.tagsToStorage(parsedTags),
            ),
            noteType = NoteFormatUtils.extractNoteType(frontMatter),
            sourceType = NoteFormatUtils.extractSourceType(frontMatter),
            sourceUrl = NoteFormatUtils.extractSourceUrl(frontMatter),
            updatedAtMs = NoteFormatUtils.extractUpdatedAt(frontMatter),
        )
    }

    private suspend fun readLatestNoteFromFile(
        entity: NoteEntity,
        file: DocumentFile,
        fileModified: Long,
    ): NoteEntity? {
        val readResult = readNoteFromFileForEditor(entity, file) ?: return null
        val updated = readResult.entity.copy(
            lastModifiedMs = fileModified.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        noteDao.insertNote(updated)
        return updated
    }

    suspend fun resolveMarkdownImagesForWebPreview(
        markdown: String,
        currentFolder: String,
    ): String =
        withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            if (markdown.isBlank()) return@withContext markdown
            val references = linkedSetOf<String>()
            NoteFormatUtils.obsidianImageReferenceRegex
                .findAll(markdown)
                .mapTo(references) { it.groupValues[1].trim() }
            NoteFormatUtils.localMarkdownImageReferenceWithAltRegex
                .findAll(markdown)
                .mapTo(references) { it.groupValues[2].trim().trim('"', '\'') }
            if (references.isEmpty()) return@withContext markdown

            val resolvedUrls =
                coroutineScope {
                    references.map { reference ->
                        async {
                            reference to
                                imageResolveSemaphore.withPermit {
                                    resolveImageWebUrl(currentFolder, reference)
                                }
                        }
                    }.awaitAll().toMap(linkedMapOf())
                }
            val referenceLabels = linkedMapOf<String, String>()

            fun resolveReference(
                reference: String,
                alt: String,
                original: String,
            ): String {
                if (resolvedUrls[reference] == null) {
                    val normalized = reference.trim().lowercase(Locale.ROOT)
                    return if (
                        normalized.startsWith("http://") ||
                        normalized.startsWith("https://") ||
                        normalized.startsWith("data:")
                    ) {
                        original
                    } else {
                        // Only resolved local SAF images are allowed into the preview page.
                        "![$alt](about:blank)"
                    }
                }
                val label =
                    referenceLabels.getOrPut(reference) {
                        "__kardleaf_preview_image_${referenceLabels.size}__"
                    }
                return "![$alt][$label]"
            }

            val withObsidianImages =
                NoteFormatUtils.obsidianImageReferenceRegex.replace(markdown) { match ->
                    val reference = match.groupValues[1].trim()
                    resolveReference(reference, alt = "", original = match.value)
                }
            val resolvedBody =
                NoteFormatUtils.localMarkdownImageReferenceWithAltRegex.replace(withObsidianImages) { match ->
                    val alt = match.groupValues[1]
                    val reference = match.groupValues[2].trim().trim('"', '\'')
                    resolveReference(reference, alt, match.value)
                }
            val resolvedMarkdown =
                if (referenceLabels.isEmpty()) {
                    resolvedBody
                } else {
                    buildString {
                        append(resolvedBody)
                        append("\n\n")
                        referenceLabels.forEach { (reference, label) ->
                            append('[').append(label).append("]: ")
                            append(resolvedUrls.getValue(reference)).append('\n')
                        }
                    }
                }
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external resolveMarkdownImagesForWebPreview folder=$currentFolder markdownLen=${markdown.length} " +
                    "references=${references.size} resolved=${referenceLabels.size} resultLen=${resolvedMarkdown.length} " +
                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            resolvedMarkdown
        }

    suspend fun resolveMarkdownImages(
        markdown: String,
        currentFolder: String,
    ): String =
        withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            if (markdown.isBlank()) return@withContext markdown
            val obsidianCount = NoteFormatUtils.obsidianImageReferenceRegex.findAll(markdown).count()
            val markdownCount = NoteFormatUtils.localMarkdownImageReferenceWithAltRegex.findAll(markdown).count()
            if (obsidianCount > 0 || markdownCount > 0) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external resolveMarkdownImages start folder=$currentFolder markdownLen=${markdown.length} " +
                        "obsidianRefs=$obsidianCount markdownRefs=$markdownCount",
                )
            }

            val references = linkedSetOf<String>()
            NoteFormatUtils.obsidianImageReferenceRegex
                .findAll(markdown)
                .mapTo(references) { it.groupValues[1].trim() }
            NoteFormatUtils.localMarkdownImageReferenceWithAltRegex
                .findAll(markdown)
                .mapTo(references) { it.groupValues[2].trim().trim('"', '\'') }
            val resolvedDataUris =
                coroutineScope {
                    references.map { reference ->
                        async {
                            reference to
                                imageResolveSemaphore.withPermit {
                                    resolveImageDataUri(currentFolder, reference, mode = "preview")
                                }
                        }
                    }.awaitAll().toMap(linkedMapOf())
                }
            val referenceLabels = linkedMapOf<String, String>()

            fun resolveReference(
                reference: String,
                alt: String,
                original: String,
            ): String {
                val dataUri = resolvedDataUris[reference]
                if (dataUri == null) return original
                val label =
                    referenceLabels.getOrPut(reference) {
                        "__kardleaf_resolved_image_${referenceLabels.size}__"
                    }
                return "![$alt][$label]"
            }

            val withObsidianImages =
                NoteFormatUtils.obsidianImageReferenceRegex.replace(markdown) { match ->
                    val reference = match.groupValues[1].trim()
                    resolveReference(reference, alt = "", original = match.value)
                }
            val resolvedBody =
                NoteFormatUtils.localMarkdownImageReferenceWithAltRegex.replace(withObsidianImages) { match ->
                    val alt = match.groupValues[1]
                    val reference = match.groupValues[2].trim().trim('\"', '\'')
                    resolveReference(reference, alt, match.value)
                }
            val resolvedMarkdown =
                if (referenceLabels.isEmpty()) {
                    resolvedBody
                } else {
                    buildString {
                        append(resolvedBody)
                        append("\n\n")
                        referenceLabels.forEach { (reference, label) ->
                            append('[').append(label).append("]: ")
                            append(resolvedDataUris.getValue(reference)).append('\n')
                        }
                    }
                }
            if (obsidianCount > 0 || markdownCount > 0) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external resolveMarkdownImages done folder=$currentFolder markdownLen=${markdown.length} " +
                        "resultLen=${resolvedMarkdown.length} refs=${obsidianCount + markdownCount} " +
                        "uniqueResolved=${referenceLabels.size} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            }
            resolvedMarkdown
        }

    suspend fun importDrawingImage(
        bitmap: Bitmap,
        drawingSource: String,
        currentFolder: String,
    ): String =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext ""
            val configuredImageFolder = prefsManager.getImageFolder()
            val imageFolderUri = prefsManager.getImageFolderUri()?.let { Uri.parse(it) }
            val imagePathMode = prefsManager.getImagePathMode()
            val relativeImageLocation = prefsManager.getRelativeImageLocation()
            val useCurrentNoteFolder =
                imagePathMode == PrefsManager.ImagePathMode.RELATIVE &&
                    relativeImageLocation == PrefsManager.RelativeImageLocation.CURRENT_NOTE_FOLDER
            val targetFolder =
                if (useCurrentNoteFolder) {
                    findFolder(root, currentFolder) ?: getOrCreateFolder(root, currentFolder)
                } else {
                    imageFolderUri
                        ?.let { DocumentFile.fromTreeUri(context, it)?.takeIf { folder -> folder.isDirectory && folder.canWrite() } }
                        ?: getOrCreateFolder(root, configuredImageFolder)
                } ?: return@withContext ""

            val baseName = "drawing_${System.currentTimeMillis()}"
            var targetName = "$baseName.png"
            var index = 1
            while (targetFolder.findFile(targetName) != null || targetFolder.findFile(drawingSourceNameForImageName(targetName)) != null) {
                targetName = "$baseName-$index.png"
                index++
            }

            val targetFile = targetFolder.createFile("image/png", targetName) ?: return@withContext ""
            val copied = writeDrawingBitmap(targetFile, bitmap)
            if (!copied) {
                targetFile.delete()
                return@withContext ""
            }

            val sourceName = drawingSourceNameForImageName(targetName)
            val sourceFile = targetFolder.createFile("application/json", sourceName)
            val sourceSaved = sourceFile?.let { writeTextDocument(it, drawingSource) } == true
            if (!sourceSaved) {
                targetFile.delete()
                sourceFile?.delete()
                return@withContext ""
            }

            val referenceFolder =
                when {
                    imagePathMode == PrefsManager.ImagePathMode.ROOT ->
                        imageFolderUri?.let(::relativeFolderFromTreeUri) ?: configuredImageFolder
                    useCurrentNoteFolder -> ""
                    else -> {
                        val fixedFolder = imageFolderUri?.let(::relativeFolderFromTreeUri) ?: configuredImageFolder
                        relativePath(currentFolder, joinPath(fixedFolder, targetName)).substringBeforeLast("/", missingDelimiterValue = "")
                    }
                }
            val reference =
                if (referenceFolder.isBlank()) {
                    targetName
                } else {
                    joinPath(referenceFolder, targetName)
                }
            "![[${reference}]]"
        }

    suspend fun updateDrawingImage(
        bitmap: Bitmap,
        drawingSource: String,
        currentFolder: String,
        reference: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val target = findReferencedDocument(currentFolder, reference) ?: return@withContext false
            if (!writeDrawingBitmap(target.file, bitmap)) return@withContext false

            val sourceName = drawingSourceNameForImageName(target.file.name.orEmpty())
            val sourceFile = target.parent.findFile(sourceName)
                ?: target.parent.createFile("application/json", sourceName)
                ?: return@withContext false
            val saved = writeTextDocument(sourceFile, drawingSource)
            if (saved) invalidateThumbnailCaches()
            saved
        }

    suspend fun loadDrawingSource(
        currentFolder: String,
        reference: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val sourceReference = drawingSourceReferenceForImageReference(reference) ?: return@withContext null
            val sourceFile = findImageFile(currentFolder, sourceReference) ?: return@withContext null
            readText(sourceFile, bypassCache = true).takeIf { it.isNotBlank() }
        }

    suspend fun loadImageViewerResource(
        currentFolder: String,
        reference: String,
        maxWidthPx: Int = 2048,
        maxHeightPx: Int = 2048,
    ): ImageViewerResource? =
        withContext(Dispatchers.IO) {
            val target = findReferencedDocument(currentFolder, reference) ?: return@withContext null
            val fileName = target.file.name.orEmpty()
            val mimeType = imageMimeType(fileName)
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val sidecarCandidates =
                listOf(
                    annotationSidecarNameForImageName(fileName),
                    drawingSourceNameForImageName(fileName),
                ).distinct()
            var sidecarSource: String? = null
            var sidecarClassification: DrawingSidecarClassification = DrawingSidecarClassification.None
            for (name in sidecarCandidates) {
                val source = findChildFile(target, name)?.let { file ->
                    readText(file, bypassCache = true).takeIf { it.isNotBlank() }
                } ?: continue
                val classification = classifyDrawingSidecar(source, name.endsWith(".kardleaf.json"))
                if (classification is DrawingSidecarClassification.Valid) {
                    sidecarSource = source
                    sidecarClassification = classification
                    break
                }
                if (classification is DrawingSidecarClassification.Invalid && name.endsWith(".kardleaf.json")) {
                    sidecarClassification = classification
                    break
                }
            }
            val documentType = (sidecarClassification as? DrawingSidecarClassification.Valid)?.document?.documentType ?: "image"
            val missingSource =
                (sidecarClassification as? DrawingSidecarClassification.Valid)
                    ?.document
                    ?.takeIf { it.documentType == "imageAnnotation" }
                    ?.sourceReference
                    ?.let { findChildFile(target, it) == null } == true
            val decoded = decodeOrientedSampledBitmap(target.file, maxWidthPx, maxHeightPx)
            val editableFormat =
                extension in setOf("png", "jpg", "jpeg") ||
                    (extension == "webp" && !isAnimatedWebP(target.file))
            val error =
                when {
                    mimeType == null -> "暂不支持这种图片格式"
                    decoded == null -> "图片加载失败或文件已损坏"
                    sidecarClassification is DrawingSidecarClassification.Invalid ->
                        when ((sidecarClassification as DrawingSidecarClassification.Invalid).error) {
                            DrawingSidecarError.UnsupportedVersion -> "图片标注文档版本不受支持"
                            DrawingSidecarError.Incomplete -> "图片标注文档不完整"
                            DrawingSidecarError.Malformed -> "图片标注文档已损坏"
                        }
                    missingSource -> "标注源图片已丢失"
                    else -> null
                }
            val resource =
                ImageViewerResource(
                    reference = reference,
                    bitmap = decoded?.bitmap,
                    mimeType = mimeType,
                    sourceWidth = decoded?.sourceWidth ?: 0,
                    sourceHeight = decoded?.sourceHeight ?: 0,
                    exifOrientation = decoded?.exifOrientation ?: ExifInterface.ORIENTATION_UNDEFINED,
                    documentType = documentType,
                    drawingSource = sidecarSource,
                    editable = error == null && editableFormat,
                    errorMessage = error,
                )
            KardLeafLog.d(
                "KardLeafImageViewer",
                "viewer sidecar=${sidecarClassification::class.simpleName} type=${resource.documentType} mime=${resource.mimeType.orEmpty()} " +
                    "source=${resource.sourceWidth}x${resource.sourceHeight} " +
                    "decoded=${resource.bitmap?.width ?: 0}x${resource.bitmap?.height ?: 0} " +
                    "orientation=${resource.exifOrientation} editable=${resource.editable}",
            )
            resource
        }

    suspend fun loadImageEditorResource(
        currentFolder: String,
        resource: ImageViewerResource,
    ): ImageEditorResource? =
        withContext(Dispatchers.IO) {
            when (resource.documentType) {
                "drawing" ->
                    ImageEditorResource(
                        mode = "drawing",
                        openedReference = resource.reference,
                        backgroundBitmap = null,
                        sourceWidth = resource.sourceWidth,
                        sourceHeight = resource.sourceHeight,
                        mimeType = resource.mimeType,
                        exifOrientation = resource.exifOrientation,
                        drawingSource = resource.drawingSource,
                    )
                "imageAnnotation" -> {
                    val source = resource.drawingSource ?: return@withContext null
                    val json = runCatching { org.json.JSONObject(source) }.getOrNull() ?: return@withContext null
                    if (json.optInt("version") != 2) return@withContext null
                    val background = json.optJSONObject("background") ?: return@withContext null
                    val sourceReference =
                        background.optString("sourceReference").takeIf { it.isNotBlank() }
                            ?: return@withContext null
                    val previewParent =
                        normalizeFolderPath(Uri.decode(resource.reference).substringBefore('#'))
                            .substringBeforeLast("/", missingDelimiterValue = "")
                    val resolvedSourceReference =
                        if (previewParent.isBlank()) {
                            sourceReference
                        } else {
                            joinPath(previewParent, sourceReference)
                        }
                    val sourceTarget = findReferencedDocument(currentFolder, resolvedSourceReference) ?: return@withContext null
                    val decoded = decodeOrientedSampledBitmap(sourceTarget.file, 3072, 3072) ?: return@withContext null
                    KardLeafLog.d(
                        "KardLeafImageAnnotation",
                        "annotation restore version=2 source=${decoded.sourceWidth}x${decoded.sourceHeight} " +
                            "strokes=${json.optJSONArray("strokes")?.length() ?: 0}",
                    )
                    ImageEditorResource(
                        mode = "imageAnnotation",
                        openedReference = resource.reference,
                        backgroundBitmap = decoded.bitmap,
                        sourceWidth = decoded.sourceWidth,
                        sourceHeight = decoded.sourceHeight,
                        mimeType = background.optString("mimeType").ifBlank { imageMimeType(sourceTarget.file.name.orEmpty()) },
                        exifOrientation = background.optInt("exifOrientation", decoded.exifOrientation),
                        drawingSource = source,
                    )
                }
                "image" -> {
                    if (!resource.editable) return@withContext null
                    val target = findReferencedDocument(currentFolder, resource.reference) ?: return@withContext null
                    val decoded = decodeOrientedSampledBitmap(target.file, 3072, 3072) ?: return@withContext null
                    ImageEditorResource(
                        mode = "newAnnotation",
                        openedReference = resource.reference,
                        backgroundBitmap = decoded.bitmap,
                        sourceWidth = decoded.sourceWidth,
                        sourceHeight = decoded.sourceHeight,
                        mimeType = resource.mimeType,
                        exifOrientation = decoded.exifOrientation,
                        drawingSource = null,
                    )
                }
                else -> null
            }
        }

    suspend fun saveImageAnnotation(
        currentFolder: String,
        editorResource: ImageEditorResource,
        previewBitmap: Bitmap,
        drawingSource: String,
    ): ImageAnnotationSaveResult? =
        withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            val json = runCatching { org.json.JSONObject(drawingSource) }.getOrNull() ?: return@withContext null
            if (json.optInt("version") != 2 || json.optString("documentType") != "imageAnnotation") {
                return@withContext null
            }
            if (editorResource.mode == "imageAnnotation") {
                val previewTarget = findReferencedDocument(currentFolder, editorResource.openedReference) ?: return@withContext null
                val sidecarName = annotationSidecarNameForImageName(previewTarget.file.name.orEmpty())
                val sidecar = previewTarget.parent.findFile(sidecarName) ?: return@withContext null
                val sourceReference = json.optJSONObject("background")?.optString("sourceReference").orEmpty()
                val sourceFile = previewTarget.parent.findFile(sourceReference) ?: return@withContext null
                val exportBitmap = renderImageAnnotationExport(sourceFile, json) ?: return@withContext null
                val exportSize = "${exportBitmap.width}x${exportBitmap.height}"
                val previewSaved = writeDrawingBitmap(previewTarget.file, exportBitmap)
                exportBitmap.recycle()
                val jsonSaved = previewSaved && writeTextDocument(sidecar, json.toString(2))
                KardLeafLog.d(
                    "KardLeafImageAnnotation",
                    "annotation save existing source=true json=$jsonSaved preview=$previewSaved " +
                        "export=$exportSize elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
                return@withContext if (jsonSaved) {
                    invalidateThumbnailCaches()
                    ImageAnnotationSaveResult(editorResource.openedReference, newlyCreated = false)
                } else {
                    null
                }
            }

            if (editorResource.mode != "newAnnotation") return@withContext null
            val originalTarget = findReferencedDocument(currentFolder, editorResource.openedReference) ?: return@withContext null
            val originalName = originalTarget.file.name.orEmpty()
            val originalExtension = originalName.substringAfterLast('.', "").lowercase(Locale.ROOT).ifBlank { "img" }
            val originalMime = editorResource.mimeType ?: imageMimeType(originalName) ?: return@withContext null
            val baseSeed = "annotation_${System.currentTimeMillis()}"
            val baseName =
                uniqueAnnotationBaseName(baseSeed) { candidate ->
                    originalTarget.parent.findFile("$candidate.png") != null ||
                        originalTarget.parent.findFile("$candidate.kardleaf.json") != null ||
                        originalTarget.parent.findFile("$candidate.source.$originalExtension") != null
                }
            val sourceName = "$baseName.source.$originalExtension"
            val jsonName = "$baseName.kardleaf.json"
            val previewName = "$baseName.png"
            val created = mutableListOf<DocumentFile>()

            fun fail(): ImageAnnotationSaveResult? {
                created.forEach { it.delete() }
                return null
            }
            val sourceFile = originalTarget.parent.createFile(originalMime, sourceName) ?: return@withContext null
            created += sourceFile
            val sourceCopied =
                runCatching {
                    val input = context.contentResolver.openInputStream(originalTarget.file.uri) ?: return@runCatching false
                    val output = context.contentResolver.openOutputStream(sourceFile.uri, "wt") ?: return@runCatching false
                    input.use { source -> output.use { destination -> source.copyTo(destination) } }
                    true
                }.getOrDefault(false)
            if (!sourceCopied) return@withContext fail()
            val background = json.optJSONObject("background") ?: org.json.JSONObject().also { json.put("background", it) }
            background
                .put("type", "image")
                .put("sourceReference", sourceFile.name ?: sourceName)
                .put("mimeType", originalMime)
                .put("sourceWidth", editorResource.sourceWidth)
                .put("sourceHeight", editorResource.sourceHeight)
                .put("exifOrientation", editorResource.exifOrientation)
            val jsonFile = originalTarget.parent.createFile("application/json", jsonName) ?: return@withContext fail()
            created += jsonFile
            if (!writeTextDocument(jsonFile, json.toString(2))) return@withContext fail()
            val previewFile = originalTarget.parent.createFile("image/png", previewName) ?: return@withContext fail()
            created += previewFile
            val exportBitmap = renderImageAnnotationExport(sourceFile, json) ?: return@withContext fail()
            val exportSize = "${exportBitmap.width}x${exportBitmap.height}"
            val previewSaved = writeDrawingBitmap(previewFile, exportBitmap)
            exportBitmap.recycle()
            if (!previewSaved) return@withContext fail()
            val cleanOpenedReference = normalizeFolderPath(Uri.decode(editorResource.openedReference).substringBefore('#'))
            val referenceParent = cleanOpenedReference.substringBeforeLast("/", missingDelimiterValue = "")
            val newReference =
                if (referenceParent.isBlank()) {
                    previewFile.name ?: previewName
                } else {
                    joinPath(referenceParent, previewFile.name ?: previewName)
                }
            KardLeafLog.d(
                "KardLeafImageAnnotation",
                "annotation save new source=$sourceCopied json=true preview=true export=$exportSize " +
                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            ImageAnnotationSaveResult(newReference, newlyCreated = true)
        }

    private data class DecodedOrientedBitmap(
        val bitmap: Bitmap,
        val sourceWidth: Int,
        val sourceHeight: Int,
        val exifOrientation: Int,
    )

    private fun renderImageAnnotationExport(
        sourceFile: DocumentFile,
        drawing: org.json.JSONObject,
    ): Bitmap? {
        val canvasSize = drawing.optJSONObject("canvas") ?: return null
        val sourceWidth = canvasSize.optInt("width").coerceAtLeast(1)
        val sourceHeight = canvasSize.optInt("height").coerceAtLeast(1)
        val scale = kotlin.math.min(1f, kotlin.math.sqrt(16_000_000.0 / (sourceWidth.toDouble() * sourceHeight)).toFloat())
        val exportWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val exportHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
        val decoded = decodeOrientedSampledBitmap(sourceFile, exportWidth, exportHeight) ?: return null
        return try {
            Bitmap.createBitmap(exportWidth, exportHeight, Bitmap.Config.ARGB_8888).also { output ->
                val canvas = Canvas(output)
                canvas.drawBitmap(decoded.bitmap, null, RectF(0f, 0f, exportWidth.toFloat(), exportHeight.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val strokes = drawing.optJSONArray("strokes") ?: return@also
                for (index in 0 until strokes.length()) {
                    val stroke = strokes.optJSONObject(index) ?: continue
                    val points = stroke.optJSONArray("points") ?: continue
                    if (points.length() == 0) continue
                    val path = Path()
                    val first = points.optJSONObject(0) ?: continue
                    var lastX = first.optDouble("x").toFloat() * exportWidth / sourceWidth
                    var lastY = first.optDouble("y").toFloat() * exportHeight / sourceHeight
                    path.moveTo(lastX, lastY)
                    for (pointIndex in 1 until points.length()) {
                        val point = points.optJSONObject(pointIndex) ?: continue
                        val x = point.optDouble("x").toFloat() * exportWidth / sourceWidth
                        val y = point.optDouble("y").toFloat() * exportHeight / sourceHeight
                        path.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
                        lastX = x
                        lastY = y
                    }
                    path.lineTo(lastX, lastY)
                    val tool = stroke.optString("tool")
                    paint.color = stroke.optInt("color")
                    paint.strokeWidth = stroke.optDouble("width").toFloat() * exportWidth / sourceWidth
                    paint.alpha = if (tool == "Highlighter") 110 else 255
                    paint.xfermode = if (tool == "AreaEraser") PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
                    canvas.drawPath(path, paint)
                }
                paint.xfermode = null
            }
        } finally {
            decoded.bitmap.recycle()
        }
    }

    private fun decodeOrientedSampledBitmap(
        imageFile: DocumentFile,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): DecodedOrientedBitmap? {
        // 快路径：单次 openFileDescriptor + lseek 复位，避免 3 次跨进程打开文件流
        val startMs = SystemClock.elapsedRealtime()
        val fdDecoded =
            runCatching {
                context.contentResolver.openFileDescriptor(imageFile.uri, "r")?.use { pfd ->
                    decodeOrientedSampledBitmapFromFd(pfd.fileDescriptor, maxWidthPx, maxHeightPx)
                }
            }.getOrNull()
        if (fdDecoded != null) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external decodeOriented fd name=${imageFile.name} source=${fdDecoded.sourceWidth}x${fdDecoded.sourceHeight} " +
                    "decoded=${fdDecoded.bitmap.width}x${fdDecoded.bitmap.height} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return fdDecoded
        }
        return decodeOrientedSampledBitmapFromStreams(imageFile, maxWidthPx, maxHeightPx)
    }

    private fun decodeOrientedSampledBitmapFromFd(
        fd: FileDescriptor,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): DecodedOrientedBitmap? {
        // 非可 seek 的 fd（如管道型 provider）在此抛 ErrnoException，由调用方回退到流式解码
        Os.lseek(fd, 0L, OsConstants.SEEK_SET)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(fd, null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val rawOrientation =
            runCatching {
                Os.lseek(fd, 0L, OsConstants.SEEK_SET)
                ExifInterface(fd).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val orientation =
            rawOrientation.takeIf { it in ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270 }
                ?: ExifInterface.ORIENTATION_NORMAL
        val swapsDimensions =
            orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val sourceWidth = if (swapsDimensions) bounds.outHeight else bounds.outWidth
        val sourceHeight = if (swapsDimensions) bounds.outWidth else bounds.outHeight
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = calculateMaxBoundInSampleSize(sourceWidth, sourceHeight, maxWidthPx, maxHeightPx)
            }
        Os.lseek(fd, 0L, OsConstants.SEEK_SET)
        val decoded = BitmapFactory.decodeFileDescriptor(fd, null, options) ?: return null
        val matrix = exifOrientationMatrix(orientation)
        val oriented =
            if (matrix.isIdentity) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { corrected ->
                    if (corrected !== decoded) decoded.recycle()
                }
            }
        return DecodedOrientedBitmap(oriented, sourceWidth, sourceHeight, orientation)
    }

    private fun decodeOrientedSampledBitmapFromStreams(
        imageFile: DocumentFile,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): DecodedOrientedBitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val rawOrientation =
                runCatching {
                    context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
                        ExifInterface(input).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        )
                    }
                }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val orientation =
                rawOrientation.takeIf { it in ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270 }
                    ?: ExifInterface.ORIENTATION_NORMAL
            val swapsDimensions =
                orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                    orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                    orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                    orientation == ExifInterface.ORIENTATION_TRANSVERSE
            val sourceWidth = if (swapsDimensions) bounds.outHeight else bounds.outWidth
            val sourceHeight = if (swapsDimensions) bounds.outWidth else bounds.outHeight
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = calculateMaxBoundInSampleSize(sourceWidth, sourceHeight, maxWidthPx, maxHeightPx)
                }
            val decoded =
                context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
                    BitmapFactory.decodeStream(input, null, options)
                } ?: return@runCatching null
            val matrix = exifOrientationMatrix(orientation)
            val oriented =
                if (matrix.isIdentity) {
                    decoded
                } else {
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { corrected ->
                        if (corrected !== decoded) decoded.recycle()
                    }
                }
            DecodedOrientedBitmap(oriented, sourceWidth, sourceHeight, orientation)
        }.getOrNull()

    private fun calculateMaxBoundInSampleSize(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Int {
        var sampleSize = 1
        while (width / sampleSize > maxWidth || height / sampleSize > maxHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun exifOrientationMatrix(orientation: Int): Matrix =
        Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }

    private fun annotationSidecarNameForImageName(imageName: String): String {
        val baseName = imageName.substringBeforeLast(".", missingDelimiterValue = imageName).ifBlank { "annotation" }
        return "$baseName.kardleaf.json"
    }

    private fun isAnimatedWebP(file: DocumentFile): Boolean =
        runCatching {
            val bytes =
                context.contentResolver.openInputStream(file.uri)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    val count = input.read(buffer)
                    if (count <= 0) ByteArray(0) else buffer.copyOf(count)
                } ?: return@runCatching false
            val marker = bytes.toString(Charsets.ISO_8859_1)
            marker.contains("ANIM") || marker.contains("ANMF")
        }.getOrDefault(false)

    private fun writeDrawingBitmap(
        targetFile: DocumentFile,
        bitmap: Bitmap,
    ): Boolean =
        runCatching {
            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            } == true
        }.getOrDefault(false)

    private fun writeTextDocument(
        targetFile: DocumentFile,
        text: String,
    ): Boolean =
        runCatching {
            val output = context.contentResolver.openOutputStream(targetFile.uri, "wt") ?: return@runCatching false
            output.use { stream ->
                OutputStreamWriter(stream).use { writer -> writer.write(text) }
            }
            true
        }.getOrDefault(false)

    private fun drawingSourceNameForImageName(imageName: String): String {
        val baseName = imageName.substringBeforeLast(".", missingDelimiterValue = imageName).ifBlank { "drawing" }
        return "$baseName.json"
    }

    private fun drawingSourceReferenceForImageReference(reference: String): String? {
        val cleanRef = normalizeFolderPath(Uri.decode(reference))
        if (cleanRef.isBlank()) return null
        val parent = cleanRef.substringBeforeLast("/", missingDelimiterValue = "")
        val name = cleanRef.substringAfterLast("/")
        val sourceName = drawingSourceNameForImageName(name)
        return if (parent.isBlank()) sourceName else joinPath(parent, sourceName)
    }

    suspend fun getImageImportTooLargeMessage(sourceUri: Uri): String? =
        withContext(Dispatchers.IO) {
            val sourceSize = queryOpenableSize(sourceUri) ?: return@withContext null
            if (sourceSize > KardLeafContentLimits.IMAGE_IMPORT_MAX_BYTES) {
                "图片过大（${formatFileSize(sourceSize)}），已取消导入。建议压缩到 ${formatFileSize(KardLeafContentLimits.IMAGE_IMPORT_MAX_BYTES)} 以内。"
            } else {
                null
            }
        }

    suspend fun importImage(
        sourceUri: Uri,
        currentFolder: String,
    ): String =
        withContext(Dispatchers.IO) {
            val importStartMs = SystemClock.elapsedRealtime()
            KardLeafLog.d(
                "KardLeafCM6Trace",
                "[insert-image] repository import start currentFolder=$currentFolder scheme=${sourceUri.scheme.orEmpty()} mime=${context.contentResolver.getType(sourceUri).orEmpty()}",
            )
            val sourceSize = queryOpenableSize(sourceUri)
            if (sourceSize != null && sourceSize > KardLeafContentLimits.IMAGE_IMPORT_MAX_BYTES) {
                KardLeafLog.w(
                    "KardLeafCM6Trace",
                    "[insert-image] repository import blocked size=$sourceSize limit=${KardLeafContentLimits.IMAGE_IMPORT_MAX_BYTES}",
                )
                return@withContext ""
            }
            val root = rootDir ?: return@withContext ""
            val configuredImageFolder = prefsManager.getImageFolder()
            val imageFolderUri = prefsManager.getImageFolderUri()?.let { Uri.parse(it) }
            val imagePathMode = prefsManager.getImagePathMode()
            val relativeImageLocation = prefsManager.getRelativeImageLocation()
            val useCurrentNoteFolder =
                imagePathMode == PrefsManager.ImagePathMode.RELATIVE &&
                    relativeImageLocation == PrefsManager.RelativeImageLocation.CURRENT_NOTE_FOLDER
            val targetFolder =
                if (useCurrentNoteFolder) {
                    findFolder(root, currentFolder) ?: getOrCreateFolder(root, currentFolder)
                } else {
                    imageFolderUri
                        ?.let { DocumentFile.fromTreeUri(context, it)?.takeIf { folder -> folder.isDirectory && folder.canWrite() } }
                        ?: getOrCreateFolder(root, configuredImageFolder)
                } ?: return@withContext ""
            val nameStartMs = SystemClock.elapsedRealtime()
            val sourceName = queryDisplayName(sourceUri).ifBlank { "image" }
            KardLeafLog.d(
                "KardLeafCM6Trace",
                "[insert-image] repository query name elapsed=${SystemClock.elapsedRealtime() - nameStartMs}ms sourceName=$sourceName",
            )
            val extension = sourceName.substringAfterLast(".", "")
                .lowercase()
                .takeIf { it in setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "avif") }
                ?: extensionFromMime(context.contentResolver.getType(sourceUri))
                ?: "png"
            val baseName = sourceName.substringBeforeLast(".").ifBlank { "image" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(80)
                .ifBlank { "image" }

            var targetName = "$baseName.$extension"
            var index = 1
            val nameConflictStartMs = SystemClock.elapsedRealtime()
            while (targetFolder.findFile(targetName) != null) {
                targetName = "$baseName-$index.$extension"
                index++
            }
            KardLeafLog.d(
                "KardLeafCM6Trace",
                "[insert-image] repository target name elapsed=${SystemClock.elapsedRealtime() - nameConflictStartMs}ms conflictChecks=$index targetName=$targetName",
            )

            val mimeType = imageMimeType(targetName) ?: "image/$extension"
            val createStartMs = SystemClock.elapsedRealtime()
            val targetFile = targetFolder.createFile(mimeType, targetName) ?: return@withContext ""
            KardLeafLog.d(
                "KardLeafCM6Trace",
                "[insert-image] repository create file elapsed=${SystemClock.elapsedRealtime() - createStartMs}ms mime=$mimeType targetName=$targetName",
            )
            var copiedBytes = -1L
            val copyStartMs = SystemClock.elapsedRealtime()
            val copied =
                runCatching {
                    copiedBytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                            input.copyTo(output)
                        } ?: -1L
                    } ?: -1L
                    copiedBytes >= 0L
                }.getOrElse { error ->
                    KardLeafLog.w("KardLeafCM6Trace", "[insert-image] repository copy failed targetName=$targetName", error)
                    false
                }
            KardLeafLog.d(
                "KardLeafCM6Trace",
                "[insert-image] repository copy done elapsed=${SystemClock.elapsedRealtime() - copyStartMs}ms bytes=$copiedBytes success=$copied targetName=$targetName",
            )
            if (!copied) {
                targetFile.delete()
                return@withContext ""
            }

            val referenceFolder =
                when {
                    imagePathMode == PrefsManager.ImagePathMode.ROOT ->
                        imageFolderUri?.let(::relativeFolderFromTreeUri) ?: configuredImageFolder
                    useCurrentNoteFolder -> ""
                    else -> {
                        val fixedFolder = imageFolderUri?.let(::relativeFolderFromTreeUri) ?: configuredImageFolder
                        relativePath(currentFolder, joinPath(fixedFolder, targetName)).substringBeforeLast("/", missingDelimiterValue = "")
                    }
                }
            val reference =
                if (referenceFolder.isBlank()) {
                    targetName
                } else {
                    joinPath(referenceFolder, targetName)
                }
            KardLeafLog.d(
                "KardLeafCM6Trace",
                "[insert-image] repository import done elapsed=${SystemClock.elapsedRealtime() - importStartMs}ms reference=$reference",
            )
            "![[${reference}]]"
        }

    private fun extractFirstImageReference(markdown: String): String? {
        if (markdown.isBlank()) return null
        return (
            NoteFormatUtils.obsidianImageReferenceRegex
                .findAll(markdown)
                .map { it.range.first to it.groupValues[1].trim() } +
                NoteFormatUtils.localMarkdownImageReferenceRegex
                    .findAll(markdown)
                    .map { it.range.first to it.groupValues[1].trim().trim('"', '\'') }
            )
            .filter { (_, reference) -> reference.isNotBlank() && !isExternalImageReference(reference) }
            .minByOrNull { it.first }
            ?.second
    }

    private fun isExternalImageReference(reference: String): Boolean {
        val normalized = reference.trim().lowercase()
        return normalized.startsWith("http://") ||
            normalized.startsWith("https://") ||
            normalized.startsWith("data:") ||
            normalized.startsWith("file:")
    }

    /**
     * RELATIVE 图片路径模式下，移动笔记时把笔记内的图片引用从“相对源目录”改写为“相对目标目录”。
     * 仅处理本地图片引用（Obsidian `![[ref]]` 与标准 `![alt](ref)`），跳过 http/data/file 等绝对 URL。
     */
    private fun rewriteRelativeImageRefs(markdown: String, fromFolder: String, toFolder: String): String =
        NoteFormatUtils.rewriteRelativeImageRefsForMove(markdown, fromFolder, toFolder)

    private fun normalizePath(path: String): String {
        val parts = path.split("/").filter { it.isNotBlank() }
        val stack = mutableListOf<String>()
        for (p in parts) {
            when {
                p == "." -> {}
                p == ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else stack.add("..")
                else -> stack.add(p)
            }
        }
        return stack.joinToString("/")
    }

    private fun relativePath(fromFolder: String, toPath: String): String {
        val fromParts = normalizePath(fromFolder).split("/").filter { it.isNotBlank() }
        val toParts = normalizePath(toPath).split("/").filter { it.isNotBlank() }
        var common = 0
        while (common < fromParts.size && common < toParts.size && fromParts[common] == toParts[common]) common++
        val up = fromParts.size - common
        val down = toParts.drop(common)
        val parts = mutableListOf<String>()
        repeat(up) { parts.add("..") }
        parts.addAll(down)
        return if (parts.isEmpty()) "" else parts.joinToString("/")
    }

    suspend fun resolveNoteThumbnailBitmap(note: Note): Bitmap? =
        withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            val reference = note.firstImageReference?.takeIf { it.isNotBlank() }
                ?: extractFirstImageReference(note.content)
                ?: run {
                    KardLeafLog.d(
                        OPEN_PATH_PROBE_TAG,
                        "external thumbnail noReference path=${note.file.path} folder=${note.folder} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                    )
                    return@withContext null
                }
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external thumbnail start path=${note.file.path} folder=${note.folder} ref=$reference contentLen=${note.content.length}",
            )
            val requestKey = thumbnailStableCacheKey(note, reference, maxWidthPx = 240, maxHeightPx = 200)
            val bitmap = withTimeoutOrNull(THUMBNAIL_RESOLVE_TIMEOUT_MS) {
                thumbnailResolveLock(requestKey).withLock {
                    resolveImageThumbnailBitmapInternal(note, reference, maxWidthPx = 240, maxHeightPx = 200)
                }
            }
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external thumbnail done path=${note.file.path} folder=${note.folder} ref=$reference ok=${bitmap != null} " +
                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            bitmap
        }

    suspend fun resolveImageThumbnailBitmap(
        note: Note,
        reference: String,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val requestKey = thumbnailStableCacheKey(note, reference, maxWidthPx = 360, maxHeightPx = 360)
            withTimeoutOrNull(THUMBNAIL_RESOLVE_TIMEOUT_MS) {
                thumbnailResolveLock(requestKey).withLock {
                    resolveImageThumbnailBitmapInternal(note, reference, maxWidthPx = 360, maxHeightPx = 360)
                }
            }
        }

    private fun resolveImageThumbnailBitmapInternal(
        note: Note,
        reference: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? {
        val startMs = SystemClock.elapsedRealtime()
        val traceId = thumbnailProbeSeq.incrementAndGet()
        val cleanReference = reference.takeIf { it.isNotBlank() } ?: return null
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external thumbnail internalStart trace=$traceId path=${note.file.path} folder=${note.folder} " +
                "rawLen=${reference.length} cleanLen=${cleanReference.length} hasSpace=${cleanReference.any { it.isWhitespace() }} " +
                "hasQuote=${cleanReference.contains('\"')} max=${maxWidthPx}x$maxHeightPx ref=$cleanReference",
        )
        val stableCacheKey = thumbnailStableCacheKey(note, cleanReference, maxWidthPx, maxHeightPx)
        val cacheKey = thumbnailVersionedCacheKey(note, cleanReference, maxWidthPx, maxHeightPx)
        val locateStartMs = SystemClock.elapsedRealtime()
        val imageFile = findImageFile(note.folder, cleanReference) ?: run {
            synchronized(noteThumbnailCache) {
                noteThumbnailCache.remove(cacheKey)
                noteThumbnailCache.remove(stableCacheKey)
            }
            thumbnailSourceSignatures.remove(stableCacheKey)
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external thumbnail imageMissing trace=$traceId path=${note.file.path} folder=${note.folder} ref=$cleanReference " +
                    "locateElapsed=${SystemClock.elapsedRealtime() - locateStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return null
        }
        val locateElapsedMs = SystemClock.elapsedRealtime() - locateStartMs
        val sourceSignature = thumbnailSourceSignature(imageFile)
        val cached = synchronized(noteThumbnailCache) {
            noteThumbnailCache.get(cacheKey) ?: noteThumbnailCache.get(stableCacheKey)
        }
        if (cached != null && !cached.isRecycled && sourceSignature.strongMetadata && thumbnailSourceSignatures[stableCacheKey] == sourceSignature) {
            synchronized(noteThumbnailCache) { noteThumbnailCache.put(cacheKey, cached) }
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external thumbnail cacheHit trace=$traceId path=${note.file.path} folder=${note.folder} ref=$cleanReference " +
                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return cached
        }
        synchronized(noteThumbnailCache) {
            noteThumbnailCache.remove(cacheKey)
            noteThumbnailCache.remove(stableCacheKey)
        }
        thumbnailSourceSignatures.remove(stableCacheKey)
        val decodeStartMs = SystemClock.elapsedRealtime()
        val bitmap = decodeOrientedSampledBitmap(imageFile, maxWidthPx = maxWidthPx, maxHeightPx = maxHeightPx)?.bitmap ?: run {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external thumbnail decodeNull trace=$traceId path=${note.file.path} folder=${note.folder} ref=$cleanReference name=${imageFile.name} " +
                    "locateElapsed=${locateElapsedMs}ms decodeElapsed=${SystemClock.elapsedRealtime() - decodeStartMs}ms " +
                    "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return null
        }
        if (sourceSignature.strongMetadata) {
            synchronized(noteThumbnailCache) {
                noteThumbnailCache.put(cacheKey, bitmap)
                noteThumbnailCache.put(stableCacheKey, bitmap)
            }
            thumbnailSourceSignatures[stableCacheKey] = sourceSignature
        }
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external thumbnail decoded trace=$traceId path=${note.file.path} folder=${note.folder} ref=$cleanReference name=${imageFile.name} " +
                "locateElapsed=${locateElapsedMs}ms decodeElapsed=${SystemClock.elapsedRealtime() - decodeStartMs}ms " +
                "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        return bitmap
    }

    private fun thumbnailStableCacheKey(
        note: Note,
        reference: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): String =
        "stable|${rootTreeUri ?: prefsManager.getRootUri().orEmpty()}|${note.file.path}|" +
            "$maxWidthPx|$maxHeightPx|${reference.trim()}"

    private fun thumbnailVersionedCacheKey(
        note: Note,
        reference: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): String =
        "versioned|${rootTreeUri ?: prefsManager.getRootUri().orEmpty()}|${note.file.path}|${note.lastModified.time}|" +
            "$maxWidthPx|$maxHeightPx|${reference.trim()}"

    private fun thumbnailResolveLock(cacheKey: String): Mutex =
        thumbnailResolveLocks[(cacheKey.hashCode() and Int.MAX_VALUE) % thumbnailResolveLocks.size]

    private fun invalidateThumbnailCaches() {
        synchronized(noteThumbnailCache) { noteThumbnailCache.evictAll() }
        thumbnailSourceSignatures.clear()
    }

    private fun thumbnailSourceSignature(
        imageFile: DocumentFile,
    ): ThumbnailSourceSignature {
        val sourceLastModified = runCatching { imageFile.lastModified() }.getOrDefault(0L)
        return ThumbnailSourceSignature(
            uri = imageFile.uri.toString(),
            lastModified = sourceLastModified,
            length = runCatching { imageFile.length() }.getOrDefault(0L),
            strongMetadata = sourceLastModified > 0L,
        )
    }

    // 同步读内存缓存：供 Compose 首帧直接出图，避免灰色占位闪烁
    fun peekNoteThumbnail(note: Note): Bitmap? {
        val reference = (note.firstImageReference?.takeIf { it.isNotBlank() } ?: extractFirstImageReference(note.content))
            ?.takeIf { it.isNotBlank() } ?: return null
        return peekThumbnailBitmap(note, reference, maxWidthPx = 240, maxHeightPx = 200)
    }

    fun peekImageThumbnail(
        note: Note,
        reference: String,
    ): Bitmap? = peekThumbnailBitmap(note, reference, maxWidthPx = 360, maxHeightPx = 360)

    private fun peekThumbnailBitmap(
        note: Note,
        reference: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? {
        val cleanReference = reference.takeIf { it.isNotBlank() } ?: return null
        val versionedKey = thumbnailVersionedCacheKey(note, cleanReference, maxWidthPx, maxHeightPx)
        val stableKey = thumbnailStableCacheKey(note, cleanReference, maxWidthPx, maxHeightPx)
        val cached = synchronized(noteThumbnailCache) {
            noteThumbnailCache.get(versionedKey) ?: noteThumbnailCache.get(stableKey)
        }
        return cached?.takeIf { !it.isRecycled }
    }

    suspend fun resolveNoteImages(
        markdown: String,
        currentFolder: String,
    ): List<NoteImage> =
        withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            if (markdown.isBlank()) return@withContext emptyList()

            val found = linkedSetOf<String>()
            NoteFormatUtils.obsidianImageReferenceRegex
                .findAll(markdown)
                .forEach { found.add(it.groupValues[1].trim()) }
            NoteFormatUtils.localMarkdownImageReferenceRegex
                .findAll(markdown)
                .forEach { found.add(it.groupValues[1].trim().trim('"', '\'')) }

            if (found.isNotEmpty()) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external resolveNoteImages start folder=$currentFolder markdownLen=${markdown.length} refs=${found.size}",
                )
            }
            val result =
                coroutineScope {
                    found.map { reference ->
                        async {
                            imageResolveSemaphore.withPermit {
                                resolveImageDataUri(currentFolder, reference, mode = "livePreview")?.let { dataUri ->
                                    NoteImage(reference = reference, dataUri = dataUri)
                                }
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            if (found.isNotEmpty()) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external resolveNoteImages done folder=$currentFolder refs=${found.size} resolved=${result.size} " +
                        "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            }
            result
        }

    private fun relativeFolderFromTreeUri(uri: Uri): String? {
        val treeUri = rootTreeUri ?: return null
        if (uri.authority != treeUri.authority) return null
        val rootDocumentId = currentRootDocumentId() ?: return null
        val imageDocumentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        return when {
            imageDocumentId == rootDocumentId -> ""
            imageDocumentId.startsWith("$rootDocumentId/") -> normalizeFolderPath(imageDocumentId.removePrefix("$rootDocumentId/"))
            else -> null
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                }
        }.getOrNull().orEmpty()
    }

    private fun queryOpenableSize(uri: Uri): Long? {
        val queriedSize = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else -1L
                }
        }.getOrNull()?.takeIf { it > 0L }
        if (queriedSize != null) return queriedSize

        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.length.takeIf { it > 0L }
            }
        }.getOrNull()
    }

    private fun extensionFromMime(mimeType: String?): String? =
        when (mimeType?.lowercase()) {
            "image/png" -> "png"
            "image/jpeg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/svg+xml" -> "svg"
            "image/avif" -> "avif"
            else -> null
        }

    private fun resolveImageWebUrl(
        currentFolder: String,
        reference: String,
    ): String? {
        val imageFile =
            findImageFileByDirectUri(currentFolder, reference)
                ?: findImageFile(currentFolder, reference)
                ?: return null
        val mimeType =
            imageMimeType(imageFile.name.orEmpty())
                ?: context.contentResolver.getType(imageFile.uri)?.takeIf { it.startsWith("image/", ignoreCase = true) }
                ?: return null
        val sourceLastModified = runCatching { imageFile.lastModified() }.getOrDefault(0L)
        val sourceLength = runCatching { imageFile.length() }.getOrDefault(0L)
        return LocalPreviewImageResource.buildUrl(
            sourceUri = imageFile.uri,
            mimeType = mimeType,
            lastModified = sourceLastModified,
            length = sourceLength,
        )
    }

    private fun resolveImageDataUri(
        currentFolder: String,
        reference: String,
        mode: String,
    ): String? {
        val startMs = SystemClock.elapsedRealtime()
        val traceRef = imageTraceRef(reference)
        imageTrace { "imageResolve start ref=$traceRef mode=$mode" }
        val directStartMs = SystemClock.elapsedRealtime()
        findImageFileByDirectUri(currentFolder, reference)?.let { directFile ->
            readImageDataUri(directFile, currentFolder, reference, startMs)?.let { dataUri ->
                imageTrace { "imageResolve directUri hit cost=${SystemClock.elapsedRealtime() - directStartMs}ms" }
                imageTrace { "imageResolve done source=directUri total=${SystemClock.elapsedRealtime() - startMs}ms" }
                return dataUri
            }
            imageTrace { "imageResolve directUri failed reason=unreadable_or_unknown_type cost=${SystemClock.elapsedRealtime() - directStartMs}ms" }
        }
        imageTrace { "imageResolve fallback findFile start" }
        val fallbackStartMs = SystemClock.elapsedRealtime()
        val imageFile = findImageFile(currentFolder, reference) ?: run {
            val fallbackElapsedMs = SystemClock.elapsedRealtime() - fallbackStartMs
            imageTrace { "imageResolve fallback miss cost=${fallbackElapsedMs}ms" }
            imageTrace { "imageResolve done source=miss total=${SystemClock.elapsedRealtime() - startMs}ms" }
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external resolveImageDataUri missing folder=$currentFolder ref=$reference elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return null
        }
        imageTrace { "imageResolve fallback hit cost=${SystemClock.elapsedRealtime() - fallbackStartMs}ms" }
        return readImageDataUri(imageFile, currentFolder, reference, startMs).also { dataUri ->
            imageTrace { "imageResolve done source=${if (dataUri != null) "fallback" else "miss"} total=${SystemClock.elapsedRealtime() - startMs}ms" }
        }
    }

    private fun readImageDataUri(
        imageFile: DocumentFile,
        currentFolder: String,
        reference: String,
        startMs: Long,
    ): String? {
        val mimeType = imageMimeType(imageFile.name.orEmpty())
            ?: context.contentResolver.getType(imageFile.uri)?.takeIf { it.startsWith("image/", ignoreCase = true) }
            ?: return null
        val imageSize = imageFile.length()
        val sourceLastModified = runCatching { imageFile.lastModified() }.getOrDefault(0L)
        val cacheKey =
            "${imageFile.uri}|$sourceLastModified|$imageSize|$mimeType"
        if (sourceLastModified > 0L) {
            synchronized(imageDataUriCache) {
                imageDataUriCache.get(cacheKey)
            }?.let { cached ->
                imageTrace { "imageResolve dataUri cacheHit total=${SystemClock.elapsedRealtime() - startMs}ms" }
                return cached
            }
        }
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external resolveImageDataUri file folder=$currentFolder ref=$reference name=${imageFile.name} size=$imageSize " +
                "locateElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        if (imageSize > KardLeafContentLimits.IMAGE_DATA_URI_MAX_BYTES) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external resolveImageDataUri oversized folder=$currentFolder ref=$reference size=$imageSize elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return thumbnailImageDataUri(imageFile)?.also { dataUri ->
                if (sourceLastModified > 0L) synchronized(imageDataUriCache) { imageDataUriCache.put(cacheKey, dataUri) }
            }
        }
        val readStartMs = SystemClock.elapsedRealtime()
        val readResult = readImageBytesWithinLimit(imageFile.uri, KardLeafContentLimits.IMAGE_DATA_URI_MAX_BYTES)
        if (readResult.exceededLimit) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external resolveImageDataUri exceededLimit folder=$currentFolder ref=$reference readElapsed=${SystemClock.elapsedRealtime() - readStartMs}ms " +
                    "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return thumbnailImageDataUri(imageFile)?.also { dataUri ->
                if (sourceLastModified > 0L) synchronized(imageDataUriCache) { imageDataUriCache.put(cacheKey, dataUri) }
            }
        }
        val bytes = readResult.bytes ?: return null

        val dataUri = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        if (sourceLastModified > 0L) synchronized(imageDataUriCache) { imageDataUriCache.put(cacheKey, dataUri) }
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external resolveImageDataUri done folder=$currentFolder ref=$reference bytes=${bytes.size} dataUriLen=${dataUri.length} " +
                "readElapsed=${SystemClock.elapsedRealtime() - readStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        return dataUri
    }

    private inline fun imageTrace(message: () -> String) {
        if (ENABLE_IMAGE_TRACE) KardLeafLog.d(IMAGE_TRACE_TAG, message())
    }

    private fun imageTraceRef(reference: String): String {
        val clean = Uri.decode(reference).substringBefore("#").replace("\\", "/").trim()
        val name = clean.substringAfterLast("/").take(80).ifBlank { "<blank>" }
        return "name=$name hash=${Integer.toHexString(reference.hashCode())}"
    }

    private fun readImageBytesWithinLimit(
        uri: Uri,
        maxBytes: Long,
    ): LimitedImageRead =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) return@runCatching LimitedImageRead(exceededLimit = true)
                    output.write(buffer, 0, read)
                }
                LimitedImageRead(bytes = output.toByteArray())
            } ?: LimitedImageRead()
        }.getOrDefault(LimitedImageRead())

    private fun thumbnailImageDataUri(imageFile: DocumentFile): String? {
        val bitmap = decodeSampledBitmap(imageFile, maxWidthPx = 360, maxHeightPx = 360) ?: return null
        val output = ByteArrayOutputStream()
        return if (bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
            "data:image/jpeg;base64,${Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)}"
        } else {
            null
        }
    }

    private fun formatFileSize(bytes: Long): String =
        when {
            bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
            bytes >= 1024L -> "${bytes / 1024L} KB"
            else -> "$bytes B"
        }

    private fun findImageFile(
        currentFolder: String,
        reference: String,
    ): DocumentFile? = findReferencedDocument(currentFolder, reference)?.file

    private fun findImageFileByDirectUri(
        currentFolder: String,
        reference: String,
    ): DocumentFile? {
        val rawRef = reference.trim().trim('"', '\'')
        val parsedUri = runCatching { Uri.parse(rawRef) }.getOrNull()
        if (parsedUri != null && parsedUri.scheme.equals("content", ignoreCase = true)) {
            val configuredImageFolderUri = prefsManager.getImageFolderUri()
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            if (!listOfNotNull(rootTreeUri, configuredImageFolderUri).any { isUriWithinTree(parsedUri, it) }) {
                imageTrace { "imageResolve directUri blocked outside granted roots" }
                return null
            }
            val direct = DocumentFile.fromSingleUri(context, parsedUri)?.takeIf { it.isFile }
            if (direct == null) imageTrace { "imageResolve directUri missing" }
            return direct
        }

        val treeUri = rootTreeUri ?: run {
            imageTrace { "imageResolve directUri missing" }
            return null
        }
        val rootDocumentId = currentRootDocumentId() ?: run {
            imageTrace { "imageResolve directUri missing" }
            return null
        }
        val cleanRef = normalizeFolderPath(Uri.decode(reference))
        if (cleanRef.isBlank()) {
            imageTrace { "imageResolve directUri missing" }
            return null
        }
        imageReferenceVariants(cleanRef).flatMap { imageReferenceCandidates(currentFolder, it) }.distinct().forEach { path ->
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "$rootDocumentId/$path")
            val document = DocumentFile.fromSingleUri(context, documentUri)
            if (runCatching { document?.isFile == true }.getOrDefault(false) && document != null) {
                return document
            }
        }
        imageReferenceVariants(cleanRef).forEach { variant ->
            findImageFileInConfiguredImageFolder(currentFolder, variant)?.let { return it }
        }
        imageTrace { "imageResolve directUri missing" }
        return null
    }

    private fun isUriWithinTree(uri: Uri, treeUri: Uri): Boolean {
        if (uri.authority != treeUri.authority) return false
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return false
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        return documentId == rootId || documentId.startsWith("$rootId/")
    }

    private fun findImageFileInConfiguredImageFolder(
        currentFolder: String,
        cleanRef: String,
    ): DocumentFile? {
        val imageFolderUri = prefsManager.getImageFolderUri()?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
        val imageFolderDocumentId = runCatching { DocumentsContract.getTreeDocumentId(imageFolderUri) }.getOrNull() ?: return null
        val configuredFolder = normalizeFolderPath(prefsManager.getImageFolder())
        if (configuredFolder.isBlank()) return null

        imageReferenceCandidates(currentFolder, cleanRef)
            .mapNotNull { path ->
                normalizePath(path)
                    .takeIf { it.startsWith("$configuredFolder/") }
                    ?.removePrefix("$configuredFolder/")
                    ?.takeIf { it.isNotBlank() }
            }
            .distinct()
            .forEach { relativePath ->
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(imageFolderUri, "$imageFolderDocumentId/$relativePath")
                val document = DocumentFile.fromSingleUri(context, documentUri)
                if (runCatching { document?.isFile == true }.getOrDefault(false) && document != null) {
                    return document
                }
            }
        return null
    }

    private fun findReferencedDocument(
        currentFolder: String,
        reference: String,
    ): ReferencedDocument? {
        val startMs = SystemClock.elapsedRealtime()
        val root = rootDir ?: return null
        val cleanRef = normalizeFolderPath(Uri.decode(reference))
        if (cleanRef.isBlank()) return null

        val current = normalizeFolderPath(currentFolder)
        val cacheKey = "$current|$cleanRef"
        resolvedImageReferenceCache.get(cacheKey)?.let { cached ->
            val file = DocumentFile.fromSingleUri(context, cached.fileUri)
            if (file != null && runCatching { file.isFile }.getOrDefault(false)) {
                val parent = cached.parentUri?.let { DocumentFile.fromTreeUri(context, it) } ?: root
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external findReferencedDocument cacheHit folder=$currentFolder ref=$reference " +
                        "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
                return ReferencedDocument(parent = parent, file = file, viaDirectUri = cached.viaDirectUri)
            }
            resolvedImageReferenceCache.remove(cacheKey)
        }

        val candidates = imageReferenceVariants(cleanRef)
            .flatMap { imageReferenceCandidates(current, it) }
            .distinct()
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external findReferencedDocument start folder=$currentFolder ref=$reference cleanRef=$cleanRef candidates=${candidates.size}",
        )

        // 快路径：按 documentId 拼路径直接探测，单候选一次 provider 查询，免逐目录枚举
        val treeUri = rootTreeUri
        val rootDocId = currentRootDocumentId()
        if (treeUri != null && rootDocId != null) {
            candidates.forEachIndexed { index, path ->
                val probeStartMs = SystemClock.elapsedRealtime()
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "$rootDocId/$path")
                val file = DocumentFile.fromSingleUri(context, fileUri)
                val isFile = file != null && runCatching { file.isFile }.getOrDefault(false)
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external findReferencedDocument directProbe index=$index path=$path found=$isFile " +
                        "elapsed=${SystemClock.elapsedRealtime() - probeStartMs}ms",
                )
                if (isFile && file != null) {
                    val parentPath = path.substringBeforeLast("/", missingDelimiterValue = "")
                    val parentUri =
                        if (parentPath.isBlank()) {
                            null
                        } else {
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, "$rootDocId/$parentPath")
                        }
                    val parent = parentUri?.let { DocumentFile.fromTreeUri(context, it) } ?: root
                    resolvedImageReferenceCache.put(
                        cacheKey,
                        ResolvedImageReference(fileUri = fileUri, parentUri = parentUri, viaDirectUri = true),
                    )
                    KardLeafLog.d(
                        OPEN_PATH_PROBE_TAG,
                        "external findReferencedDocument done folder=$currentFolder ref=$reference found=true direct=true " +
                            "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                    )
                    return ReferencedDocument(parent = parent, file = file, viaDirectUri = true)
                }
            }
        }

        // 慢路径：逐目录枚举，兼容 documentId 非路径结构的 provider
        var result: ReferencedDocument? = null
        candidates.forEachIndexed { index, path ->
            if (result != null) return@forEachIndexed
            val candidateStartMs = SystemClock.elapsedRealtime()
            val parentPath = path.substringBeforeLast("/", missingDelimiterValue = "")
            val name = path.substringAfterLast("/")
            val parent = findFolder(root, parentPath, traceReason = "imageRef[$index]")
            val parentElapsedMs = SystemClock.elapsedRealtime() - candidateStartMs
            val findFileStartMs = SystemClock.elapsedRealtime()
            val file = parent?.findFile(name)?.takeIf { it.isFile }
            val findFileElapsedMs = SystemClock.elapsedRealtime() - findFileStartMs
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external findReferencedDocument candidate folder=$currentFolder ref=$reference index=$index path=$path " +
                    "parentFound=${parent != null} fileFound=${file != null} parentElapsed=${parentElapsedMs}ms " +
                    "findFileElapsed=${findFileElapsedMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            if (parent != null && file != null) {
                resolvedImageReferenceCache.put(
                    cacheKey,
                    ResolvedImageReference(fileUri = file.uri, parentUri = parent.uri, viaDirectUri = false),
                )
                result = ReferencedDocument(parent = parent, file = file)
            }
        }
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external findReferencedDocument done folder=$currentFolder ref=$reference found=${result != null} " +
                "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        return result
    }

    // 通过 documentId 拼接直取同目录下的兄弟文件；仅在 viaDirectUri 已验证时信任「不存在」结论
    private fun findChildFile(
        target: ReferencedDocument,
        name: String,
    ): DocumentFile? {
        if (name.isBlank()) return null
        if (target.viaDirectUri && !name.contains('/')) {
            val childUri =
                runCatching {
                    val parentDocId = DocumentsContract.getDocumentId(target.parent.uri)
                    DocumentsContract.buildDocumentUriUsingTree(target.parent.uri, "$parentDocId/$name")
                }.getOrNull()
            if (childUri != null) {
                return DocumentFile.fromSingleUri(context, childUri)
                    ?.takeIf { runCatching { it.isFile }.getOrDefault(false) }
            }
        }
        return target.parent.findFile(name)?.takeIf { it.isFile }
    }

    private fun imageReferenceCandidates(
        currentFolder: String,
        cleanRef: String,
    ): List<String> =
        listOf(
            joinPath(currentFolder, cleanRef),
            cleanRef,
            joinPath(currentFolder, "attachments/$cleanRef"),
            joinPath(currentFolder, "附件/$cleanRef"),
            "attachments/$cleanRef",
            "附件/$cleanRef",
        )
            .map(::normalizePath)
            .filter { path ->
                path.isNotBlank() && path != ".." && !path.startsWith("../")
            }
            .distinct()

    private fun imageReferenceVariants(cleanRef: String): List<String> =
        listOf(cleanRef, cleanRef.substringBefore('#')).filter { it.isNotBlank() }.distinct()

    private fun decodeSampledBitmap(
        imageFile: DocumentFile,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? {
        val startMs = SystemClock.elapsedRealtime()
        // 快路径：单次 openFileDescriptor + lseek 复位，避免两次跨进程打开文件流
        val fdBitmap =
            runCatching {
                context.contentResolver.openFileDescriptor(imageFile.uri, "r")?.use { pfd ->
                    val fd = pfd.fileDescriptor
                    Os.lseek(fd, 0L, OsConstants.SEEK_SET)
                    val fdBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(fd, null, fdBounds)
                    if (fdBounds.outWidth <= 0 || fdBounds.outHeight <= 0) return@use null
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(fdBounds.outWidth, fdBounds.outHeight, maxWidthPx, maxHeightPx)
                    }
                    Os.lseek(fd, 0L, OsConstants.SEEK_SET)
                    BitmapFactory.decodeFileDescriptor(fd, null, options)
                }
            }.getOrNull()
        if (fdBitmap != null) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external decodeSampledBitmap fd name=${imageFile.name} decoded=${fdBitmap.width}x${fdBitmap.height} " +
                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return fdBitmap
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStartMs = SystemClock.elapsedRealtime()
        context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        val boundsElapsedMs = SystemClock.elapsedRealtime() - boundsStartMs
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external decodeSampledBitmap invalidBounds name=${imageFile.name} boundsElapsed=${boundsElapsedMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxWidthPx, maxHeightPx)
        }
        val decodeStartMs = SystemClock.elapsedRealtime()
        val bitmap = context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external decodeSampledBitmap done name=${imageFile.name} bounds=${bounds.outWidth}x${bounds.outHeight} " +
                "target=${maxWidthPx}x$maxHeightPx sample=${options.inSampleSize} ok=${bitmap != null} " +
                "boundsElapsed=${boundsElapsedMs}ms decodeElapsed=${SystemClock.elapsedRealtime() - decodeStartMs}ms " +
                "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        return bitmap
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        var inSampleSize = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / inSampleSize >= reqWidth || halfHeight / inSampleSize >= reqHeight) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun imageMimeType(fileName: String): String? =
        when (fileName.substringAfterLast(".", "").lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "avif" -> "image/avif"
            else -> null
        }

    override suspend fun emptyTrash() =
        withContext(Dispatchers.IO) {
            val deletedEntities = noteDao.getTrashedNoteShellsSync()
                .filter { entity ->
                    val deleted = findNoteDocument(entity)?.delete() == true
                    if (!deleted) KardLeafLog.e("RoomNoteRepository", "Failed to delete trashed note file: ${entity.filePath}")
                    deleted
                }

            if (deletedEntities.isNotEmpty()) {
                deletedEntities.forEach { entity ->
                    deleteNoteRecordsForPath(entity.filePath, entity.recordId)
                }
                noteDao.deleteNotesByPaths(deletedEntities.map { it.filePath })
                markWebDavRealtimeLocalDirty()
            }
        }

    override suspend fun cleanupExpiredTrash(olderThanDays: Int) =
        withContext(Dispatchers.IO) {
            if (olderThanDays <= 0) return@withContext
            val cutoffMs = System.currentTimeMillis() - olderThanDays * 24L * 60L * 60L * 1000L
            val expiredPaths = noteDao.getTrashedNotePathsBefore(cutoffMs)
            if (expiredPaths.isEmpty()) return@withContext
            val expiredEntities = noteDao.getNoteShellsByPaths(expiredPaths)
            val deletedEntities = expiredEntities
                .filter { entity ->
                    val deleted = findNoteDocument(entity)?.delete() == true
                    if (!deleted) KardLeafLog.e("RoomNoteRepository", "Failed to delete expired trashed note file: ${entity.filePath}")
                    deleted
                }
            if (deletedEntities.isNotEmpty()) {
                deletedEntities.forEach { entity ->
                    prefsManager.setNotePinned(entity.filePath, false)
                    prefsManager.setNoteFavorite(entity.filePath, false)
                }
                deletedEntities.forEach { entity ->
                    deleteNoteRecordsForPath(entity.filePath, entity.recordId)
                }
                noteDao.deleteNotesByPaths(deletedEntities.map { it.filePath })
                markWebDavRealtimeLocalDirty()
            }
        }

    private suspend fun resolveNoteRecordId(notePath: String): String {
        if (notePath.isBlank()) return notePath
        val entity = noteDao.getNoteShellByPath(notePath) ?: return notePath
        val rawContent =
            (findNoteDocumentDirectFirst(entity, traceReason = "resolveNoteRecordId")
                ?: findDocumentByPath(notePath, traceReason = "resolveNoteRecordId.fallbackPath"))
                ?.let { readText(it) }
        val kardLeafId = rawContent?.let { NoteFormatUtils.extractKardLeafId(it) }?.takeIf { it.isNotBlank() }
        val recordId = kardLeafId ?: notePath
        syncNoteRecordsWithResolvedId(notePath, recordId)
        return recordId
    }

    private suspend fun resolveOrCreateNoteRecordIdForRemark(notePath: String): String {
        if (notePath.isBlank()) return notePath
        val entity = noteDao.getNoteShellByPath(notePath) ?: return notePath
        val file = findNoteDocumentDirectFirst(entity, traceReason = "resolveOrCreateNoteRecordIdForRemark")
            ?: findDocumentByPath(notePath, traceReason = "resolveOrCreateNoteRecordIdForRemark.fallbackPath")
            ?: return notePath
        val rawContent = readText(file)
        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
        NoteFormatUtils.extractKardLeafId(frontMatter)?.let { recordId ->
            syncNoteRecordsWithResolvedId(notePath, recordId)
            return recordId
        }

        val noteForFrontMatter = entity.toNote().copy(content = frontMatter.cleanContent)
        val fullContent = NoteFormatUtils.constructFileContent(noteForFrontMatter, rawContent)
        val recordId = NoteFormatUtils.extractKardLeafId(fullContent) ?: notePath
        if (recordId == notePath) return notePath

        context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(fullContent)
            }
        }
        lastLocalWriteElapsedMs = SystemClock.elapsedRealtime()
        updateTextCache(file, fullContent)

        val writtenLastModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        val readResult = readNoteFromFileForEditor(entity, file) ?: return notePath
        val updatedEntity = readResult.entity.copy(lastModifiedMs = writtenLastModified)
        noteDao.insertNote(updatedEntity)
        fileSignatures[notePath] = FileSignature(writtenLastModified, file.length())
        syncNoteRecordsWithResolvedId(notePath, recordId)
        return recordId
    }

    private suspend fun syncNoteRecordsWithResolvedId(
        notePath: String,
        recordId: String,
    ) {
        if (notePath.isBlank() || recordId.isBlank() || notePath == recordId) return
        noteDao.updateRecordId(notePath, recordId)
        val movedHistory = noteHistoryDao.replaceNoteId(notePath, recordId)
        val movedRemarks = noteRemarkDao.replaceNoteId(notePath, recordId)
        syncRecordStoreAfterKeyChange(movedHistory, movedRemarks, notePath, recordId)
    }

    /** noteId 键变化后仅在确有记录被迁移时同步外部主数据，避免热路径上的无谓文件 IO。 */
    private suspend fun syncRecordStoreAfterKeyChange(
        movedHistory: Int,
        movedRemarks: Int,
        oldKey: String,
        newKey: String,
    ) {
        if (movedHistory > 0) recordExternalBackup.syncHistory(oldKey, newKey)
        if (movedRemarks > 0) recordExternalBackup.syncRemarks(oldKey, newKey)
    }

    private suspend fun deleteNoteRecordsForPath(notePath: String, knownRecordId: String? = null) {
        if (notePath.isBlank()) return
        val recordId = knownRecordId?.takeIf { it.isNotBlank() } ?: resolveNoteRecordId(notePath)
        noteHistoryDao.deleteByNoteId(notePath)
        noteRemarkDao.deleteByNoteId(notePath)
        if (recordId != notePath) {
            noteHistoryDao.deleteByNoteId(recordId)
            noteRemarkDao.deleteByNoteId(recordId)
        }
        recordExternalBackup.syncRemarks(notePath, recordId)
        recordExternalBackup.syncHistory(notePath, recordId)
    }

    private fun NoteEntity.toNote(
        noteType: String? = null,
        sourceType: String? = null,
        sourceUrl: String? = null,
        updatedAtMs: Long? = null,
    ): Note {
        val noteTags = NoteFormatUtils.tagsFromStorage(yamlTags)
        if (noteTags.isNotEmpty()) {
            logYamlTagTrace("toNote path=$filePath title=$title tags=$noteTags yamlTagsRawLen=${yamlTags.length}")
        }
        return Note(
            file = java.io.File(folder, fileName),
            title = title,
            content = content,
            contentPreview = contentPreview.ifBlank { content.take(200) },
            lastModified = Date(lastModifiedMs),
            createdAt = Date(createdAtMs),
            updatedAt = Date(updatedAtMs ?: lastModifiedMs),
            color = color,
            reminder = reminder,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isArchived = isArchived,
            isTrashed = isTrashed,
            deletedAt = deletedAtMs?.let { Date(it) },
            firstImageReference = firstImageReference?.takeIf { it.isNotBlank() },
            tags = noteTags,
            sourceType = sourceType,
            sourceUrl = sourceUrl,
            noteType = noteType,
        )
    }

    private suspend fun readHistorySnapshotContentSource(oldFileDoc: DocumentFile?): HistorySnapshotContentSource {
        if (oldFileDoc == null) {
            return HistorySnapshotContentSource(fallbackReason = "old-file-missing")
        }
        return try {
            val rawContent = readText(oldFileDoc)
            if (rawContent.isEmpty() && oldFileDoc.length() > 0L) {
                return HistorySnapshotContentSource(fallbackReason = "old-file-read-empty")
            }
            val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
            HistorySnapshotContentSource(
                rawContent = rawContent,
                cleanContent = frontMatter.cleanContent,
                tags = NoteFormatUtils.extractTags(frontMatter),
            )
        } catch (e: Exception) {
            HistorySnapshotContentSource(fallbackReason = "old-file-read-failed")
        }
    }

    private suspend fun saveHistorySnapshot(
        entity: NoteEntity,
        externalContent: String?,
        externalFallbackReason: String?,
    ) {
        val historyLimit = prefsManager.getHistoryVersionLimit()
        if (historyLimit <= 0) return
        val recordId = resolveNoteRecordId(entity.filePath)
        val snapshotContent = externalContent ?: entity.content.also {
            logRoomContentAudit(
                "history snapshot fallback to Room content reason=${externalFallbackReason ?: "external-content-null"} path=${entity.filePath} roomLen=${entity.content.length}",
            )
        }
        noteHistoryDao.insert(
            NoteHistoryEntity(
                noteId = recordId,
                title = entity.title,
                content = snapshotContent,
                savedAtMs = System.currentTimeMillis(),
            ),
        )
        noteHistoryDao.pruneOldVersions(recordId, historyLimit)
        recordExternalBackup.syncHistory(recordId)
    }

    private fun hasTitleOrContentChanged(
        entity: NoteEntity,
        note: Note,
        externalContent: String? = null,
    ): Boolean {
        return entity.title != note.title || (externalContent ?: entity.content) != note.content
    }

    private fun getArchiveFolder(
        root: DocumentFile,
        create: Boolean,
    ): DocumentFile? =
        if (create) getOrCreateFolder(root, ARCHIVE_ROOT_PATH) else findFolder(root, ARCHIVE_ROOT_PATH)

    private fun findArchiveNoteDocument(
        root: DocumentFile,
        fileName: String,
    ): DocumentFile? =
        getArchiveFolder(root, create = false)?.findFile(fileName)?.takeIf { it.isFile }

    private fun getTrashRoot(
        root: DocumentFile,
        create: Boolean,
    ): DocumentFile? {
        val configuredName = prefsManager.getTrashFolderName()
        val configured = root.findFile(configuredName)
        if (configured != null) return configured
        if (!create && configuredName != "Trash") {
            root.findFile("Trash")?.let { return it }
        }
        return if (create) root.createDirectory(configuredName) else null
    }

    private fun findNoteDocumentByDirectUri(
        entity: NoteEntity,
        traceReason: String? = null,
    ): DocumentFile? {
        val startMs = SystemClock.elapsedRealtime()
        val treeUri = rootTreeUri ?: run {
            if (traceReason != null) {
                KardLeafLog.w(OPEN_PATH_PROBE_TAG, "external directUri noTreeUri reason=$traceReason path=${entity.filePath}")
            }
            return null
        }
        val rootDocumentId =
            currentRootDocumentId()
                ?: run {
                    if (traceReason != null) {
                        KardLeafLog.w(OPEN_PATH_PROBE_TAG, "external directUri noRootDocumentId reason=$traceReason path=${entity.filePath}")
                    }
                    return null
                }

        val basePath = joinPath(entity.folder, entity.fileName)
        val candidates = mutableListOf<String>().apply {
            if (!entity.isTrashed) {
                if (entity.isArchived) {
                    add(joinPath(ARCHIVE_ROOT_PATH, entity.fileName))
                    add(joinPath(entity.folder, "Archived/${entity.fileName}"))
                }
                if (entity.isPinned) add(joinPath(entity.folder, "Pinned/${entity.fileName}"))
                add(basePath)
            }
        }.distinct()

        if (candidates.isEmpty()) {
            if (traceReason != null) {
                KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external directUri skip reason=$traceReason path=${entity.filePath} trashed=${entity.isTrashed}")
            }
            return null
        }

        for (candidate in candidates) {
            val documentId = "$rootDocumentId/$candidate"
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
            val checkStartMs = SystemClock.elapsedRealtime()
            val document = DocumentFile.fromSingleUri(context, documentUri)
            val isFile = runCatching { document?.isFile == true }.getOrDefault(false)
            val checkElapsedMs = SystemClock.elapsedRealtime() - checkStartMs
            if (traceReason != null || checkElapsedMs >= 16L) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external directUri check reason=${traceReason ?: "slow"} path=${entity.filePath} " +
                        "candidate=$candidate found=$isFile elapsed=${checkElapsedMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            }
            if (isFile && document != null) {
                if (traceReason != null) {
                    KardLeafLog.d(
                        OPEN_PATH_PROBE_TAG,
                        "external directUri hit reason=$traceReason path=${entity.filePath} candidate=$candidate " +
                            "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                    )
                }
                return document
            }
        }

        if (traceReason != null) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external directUri miss reason=$traceReason path=${entity.filePath} candidates=${candidates.size} " +
                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
        }
        return null
    }

    private fun findNoteDocumentDirectFirst(
        entity: NoteEntity,
        traceReason: String,
    ): DocumentFile? {
        val direct = findNoteDocumentByDirectUri(entity, traceReason = "$traceReason.directUri")
        if (direct != null) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external reuseDirectUri directUriReuseHit reason=$traceReason path=${entity.filePath} " +
                    "uri=${direct.uri} skipFindFileAfterDirectHit",
            )
            return direct
        }
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external fallbackFindFileAfterDirectMiss reason=$traceReason path=${entity.filePath}",
        )
        return findNoteDocument(entity, traceReason = "$traceReason.fallbackFindFile")
    }

    private fun findNoteDocument(
        entity: NoteEntity,
        traceReason: String? = null,
    ): DocumentFile? {
        val startMs = SystemClock.elapsedRealtime()
        val root = rootDir ?: run {
            if (traceReason != null) {
                KardLeafLog.w(OPEN_PATH_PROBE_TAG, "external findNoteDocument noRoot reason=$traceReason path=${entity.filePath}")
            }
            return null
        }
        if (traceReason != null) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external findNoteDocument start reason=$traceReason path=${entity.filePath} folder=${entity.folder} " +
                    "fileName=${entity.fileName} archived=${entity.isArchived} trashed=${entity.isTrashed} pinned=${entity.isPinned}",
            )
        }
        val baseFolderStartMs = SystemClock.elapsedRealtime()
        val baseFolder =
            if (entity.isTrashed) {
                getTrashRoot(root, create = false)?.let {
                    findFolder(it, entity.folder, traceReason = traceReason?.let { reason -> "$reason.trashFolder" })
                }
            } else {
                findFolder(root, entity.folder, traceReason = traceReason?.let { "$it.baseFolder" })
            } ?: run {
                if (traceReason != null) {
                    KardLeafLog.w(
                        OPEN_PATH_PROBE_TAG,
                        "external findNoteDocument noBaseFolder reason=$traceReason path=${entity.filePath} folder=${entity.folder} " +
                            "baseFolderElapsed=${SystemClock.elapsedRealtime() - baseFolderStartMs}ms",
                    )
                }
                return null
            }
        val baseFolderElapsedMs = SystemClock.elapsedRealtime() - baseFolderStartMs

        fun findDirect(label: String, folder: DocumentFile, fileName: String): DocumentFile? {
            val findStartMs = SystemClock.elapsedRealtime()
            val found = folder.findFile(fileName)?.takeIf { it.isFile }
            val findElapsedMs = SystemClock.elapsedRealtime() - findStartMs
            if (traceReason != null || findElapsedMs >= 16L) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external findNoteDocument findFile reason=${traceReason ?: "slow"} label=$label path=${entity.filePath} " +
                        "fileName=$fileName found=${found != null} elapsed=${findElapsedMs}ms",
                )
            }
            return found
        }

        val result = when {
            entity.isTrashed -> findDirect("trashBase", baseFolder, entity.fileName)
            entity.isArchived -> findArchiveNoteDocument(root, entity.fileName) ?: run {
                val archivedFolderStartMs = SystemClock.elapsedRealtime()
                val archivedFolder = baseFolder.findFile("Archived")
                val archivedFolderElapsedMs = SystemClock.elapsedRealtime() - archivedFolderStartMs
                if (traceReason != null || archivedFolderElapsedMs >= 16L) {
                    KardLeafLog.d(
                        OPEN_PATH_PROBE_TAG,
                        "external findNoteDocument archivedFolder reason=${traceReason ?: "slow"} path=${entity.filePath} " +
                            "found=${archivedFolder != null} elapsed=${archivedFolderElapsedMs}ms",
                    )
                }
                archivedFolder?.let { findDirect("archived", it, entity.fileName) } ?: findDirect("baseFallback", baseFolder, entity.fileName)
            }
            entity.isPinned -> {
                val pinnedFolderStartMs = SystemClock.elapsedRealtime()
                val pinnedFolder = baseFolder.findFile("Pinned")
                val pinnedFolderElapsedMs = SystemClock.elapsedRealtime() - pinnedFolderStartMs
                if (traceReason != null || pinnedFolderElapsedMs >= 16L) {
                    KardLeafLog.d(
                        OPEN_PATH_PROBE_TAG,
                        "external findNoteDocument pinnedFolder reason=${traceReason ?: "slow"} path=${entity.filePath} " +
                            "found=${pinnedFolder != null} elapsed=${pinnedFolderElapsedMs}ms",
                    )
                }
                pinnedFolder?.let { findDirect("pinned", it, entity.fileName) } ?: findDirect("baseFallback", baseFolder, entity.fileName)
            }
            else -> findDirect("base", baseFolder, entity.fileName)
        }
        if (traceReason != null || baseFolderElapsedMs >= 16L) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external findNoteDocument done reason=${traceReason ?: "slow"} path=${entity.filePath} " +
                    "folder=${entity.folder} fileName=${entity.fileName} found=${result != null} " +
                    "baseFolderElapsed=${baseFolderElapsedMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
        }
        return result
    }

    private fun findFolder(
        root: DocumentFile,
        path: String,
        traceReason: String? = null,
    ): DocumentFile? {
        val startMs = SystemClock.elapsedRealtime()
        val normalized = normalizeFolderPath(path)
        if (normalized.isBlank()) {
            if (traceReason != null) {
                KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external findFolder root reason=$traceReason path=$path elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
            }
            return root
        }
        var current: DocumentFile = root
        normalized.split("/").forEachIndexed { index, segment ->
            val segmentStartMs = SystemClock.elapsedRealtime()
            val next = current.findFile(segment)?.takeIf { it.isDirectory }
            val segmentElapsedMs = SystemClock.elapsedRealtime() - segmentStartMs
            if (traceReason != null || segmentElapsedMs >= 16L) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external findFolder segment reason=${traceReason ?: "slow"} path=$normalized index=$index segment=$segment " +
                        "found=${next != null} elapsed=${segmentElapsedMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
            }
            current = next ?: return null
        }
        if (traceReason != null) {
            KardLeafLog.d(OPEN_PATH_PROBE_TAG, "external findFolder done reason=$traceReason path=$normalized elapsed=${SystemClock.elapsedRealtime() - startMs}ms")
        }
        return current
    }

    private fun getOrCreateFolder(
        root: DocumentFile,
        path: String,
    ): DocumentFile? {
        val normalized = normalizeFolderPath(path)
        if (normalized.isBlank()) return root
        var current: DocumentFile = root
        normalized.split("/").forEach { segment ->
            current =
                current.findFile(segment)?.takeIf { it.isDirectory }
                    ?: current.createDirectory(segment)
                    ?: return null
        }
        return current
    }

    private fun relocateDocument(
        document: DocumentFile,
        sourceParent: DocumentFile,
        targetParent: DocumentFile,
        oldName: String,
        newName: String,
    ): DocumentFile? {
        if (sourceParent.uri == targetParent.uri) {
            return if (renameFolderDocument(sourceParent, document, oldName, newName)) {
                sourceParent.findFile(newName) ?: document
            } else {
                null
            }
        }
        if (oldName != newName || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val sourceParentUri = moveDocumentUri(sourceParent) ?: return null
        val targetParentUri = moveDocumentUri(targetParent) ?: return null
        val movedUri = DocumentsContract.moveDocument(
            context.contentResolver,
            document.uri,
            sourceParentUri,
            targetParentUri,
        ) ?: return null
        return DocumentFile.fromSingleUri(context, movedUri)
    }

    private fun moveDocumentUri(document: DocumentFile): Uri? {
        val uri = document.uri
        if (!DocumentsContract.isTreeUri(uri)) return uri
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }
            .recoverCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull() ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
    }

    private fun renameFolderDocument(
        parent: DocumentFile,
        folder: DocumentFile,
        oldName: String,
        newName: String,
    ): Boolean {
        if (!isCaseOnlyFolderRename(oldName, newName)) return folder.renameTo(newName)
        val temporaryName = ".kardleaf-rename-${System.nanoTime()}"
        if (!folder.renameTo(temporaryName)) return false
        val temporaryFolder = parent.findFile(temporaryName)
        val renamed = temporaryFolder?.renameTo(newName) == true
        if (!renamed) temporaryFolder?.renameTo(oldName)
        return renamed
    }

    private fun deleteFolder(
        root: DocumentFile,
        path: String,
    ) {
        val normalized = normalizeFolderPath(path)
        if (normalized.isBlank()) return
        val parent = normalized.substringBeforeLast("/", missingDelimiterValue = "")
        val name = normalized.substringAfterLast("/")
        val parentFolder = findFolder(root, parent) ?: return
        parentFolder.findFile(name)?.takeIf { it.isDirectory }?.delete()
    }

    private fun normalizeFolderPath(path: String): String {
        return path
            .replace("\\", "/")
            .split("/")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "." }
            .joinToString("/")
    }

    private fun joinPath(
        folder: String,
        fileName: String,
    ): String = normalizeFolderPath(folder).takeIf { it.isNotBlank() }?.let { "$it/$fileName" } ?: fileName

    private fun folderPathWithParents(path: String): List<String> {
        val parts = normalizeFolderPath(path).split("/").filter { it.isNotBlank() }
        return parts.indices.map { index -> parts.take(index + 1).joinToString("/") }
    }
}

internal fun isCaseOnlyFolderRename(currentName: String, newName: String): Boolean =
    currentName != newName && currentName.equals(newName, ignoreCase = true)

internal fun isValidFolderRelocation(oldPath: String, newPath: String): Boolean {
    if (oldPath.isBlank() || newPath.isBlank() || oldPath == newPath || newPath.startsWith("$oldPath/")) return false
    val oldParent = oldPath.substringBeforeLast("/", missingDelimiterValue = "")
    val newParent = newPath.substringBeforeLast("/", missingDelimiterValue = "")
    return oldParent == newParent || oldPath.substringAfterLast("/") == newPath.substringAfterLast("/")
}

internal fun resolveFileTreeMovePath(sourcePath: String, targetParent: String): String? {
    val source = sourcePath.replace('\\', '/').trim('/')
    val parent = targetParent.replace('\\', '/').trim('/')
    val fileName = source.substringAfterLast('/').takeIf { source.isNotBlank() && it.isNotBlank() } ?: return null
    val target = if (parent.isBlank()) fileName else "$parent/$fileName"
    return target.takeUnless { it == source }
}

internal fun remapTreePath(path: String, oldPath: String, newPath: String): String =
    when {
        path == oldPath -> newPath
        path.startsWith("$oldPath/") -> newPath + path.removePrefix(oldPath)
        else -> path
    }

private fun NoteRemarkEntity.toNoteRemark(): NoteRemark =
    NoteRemark(
        id = id,
        noteId = noteId,
        content = content,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )
