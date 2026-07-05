package com.kangle.kardleaf.data.repository

import com.kangle.kardleaf.data.utils.KardLeafLog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.pm.ApplicationInfo
import android.provider.DocumentsContract
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.kangle.kardleaf.data.database.AppDatabase
import com.kangle.kardleaf.data.database.LabelDao
import com.kangle.kardleaf.data.database.LabelEntity
import com.kangle.kardleaf.data.database.NoteDao
import com.kangle.kardleaf.data.database.NoteEntity
import com.kangle.kardleaf.data.database.NoteMetadataEntity
import com.kangle.kardleaf.data.database.NoteRemarkDao
import com.kangle.kardleaf.data.database.NoteRemarkEntity
import com.kangle.kardleaf.data.database.NoteHistoryDao
import com.kangle.kardleaf.data.database.NoteHistoryEntity
import com.kangle.kardleaf.data.database.NoteHistoryPreviewEntity
import com.kangle.kardleaf.data.database.PrivacyNoteDao
import com.kangle.kardleaf.data.database.PrivacyNoteEntity
import com.kangle.kardleaf.data.model.AppConfig
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.model.NoteSearchMatch
import com.kangle.kardleaf.data.utils.KardLeafContentLimits
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import com.kangle.kardleaf.data.utils.SearchQueryUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import android.os.SystemClock
import android.util.Base64
import android.util.LruCache
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
    data class MovedNotePath(
        val oldPath: String,
        val newPath: String,
    )

    data class NoteImage(
        val reference: String,
        val dataUri: String,
    )

    private data class ReferencedDocument(
        val parent: DocumentFile,
        val file: DocumentFile,
    )

    private data class LimitedImageRead(
        val bytes: ByteArray? = null,
        val exceededLimit: Boolean = false,
    )

    companion object {
        private const val MAX_TEXT_CACHE_ENTRIES = 200
        private const val NOTE_PREVIEW_CHAR_LIMIT = 200
        private const val HISTORY_DIALOG_PREVIEW_CHAR_LIMIT = 200
        private const val HISTORY_DIALOG_FULL_CONTENT_CHAR_LIMIT = 80_000
        private const val SEARCH_RESULT_LIMIT = 100
        private const val LOCAL_WRITE_OBSERVER_COOLDOWN_MS = 1500L
        private const val STARTUP_PERF_TRACE_TAG = "KardLeafStartupPerf"
        private const val NOTE_THUMBNAIL_CACHE_MAX_BYTES = 12 * 1024 * 1024
        private const val YAML_TAG_TRACE_TAG = "KardLeafYamlTags"
        private const val LARGE_NOTE_OPEN_TRACE_TAG = "KardLeafLargeNoteOpen"
        private const val OPEN_PATH_PROBE_TAG = "KardLeafOpenPathProbe"
        private const val IMAGE_TRACE_TAG = "KardLeafImageTrace"
        private const val ENABLE_IMAGE_TRACE = false
        private const val ROOM_CONTENT_AUDIT_TAG = "KardLeafRoomContentAudit"
        private const val SEARCH_TRACE_TAG = "KardLeafSearchTrace"
    }

    private data class UserDataBackup(
        val version: Int = 1,
        val favoriteNotePaths: List<String>? = emptyList(),
        val pinnedNotePaths: List<String>? = emptyList(),
        val history: List<HistoryBackup>? = emptyList(),
        val remarks: List<RemarkBackup>? = emptyList(),
    )

    private data class HistoryBackup(
        val id: Long,
        val noteId: String,
        val title: String,
        val content: String,
        val savedAtMs: Long,
    )

    private data class RemarkBackup(
        val id: Long = 0,
        val noteId: String,
        val content: String,
        val createdAtMs: Long? = null,
        val updatedAtMs: Long,
    )


    private val noteDao: NoteDao = AppDatabase.getDatabase(context).noteDao()
    private val labelDao: LabelDao = AppDatabase.getDatabase(context).labelDao()
    private val noteHistoryDao: NoteHistoryDao = AppDatabase.getDatabase(context).noteHistoryDao()
    private val privacyNoteDao: PrivacyNoteDao = AppDatabase.getDatabase(context).privacyNoteDao()
    private val noteRemarkDao: NoteRemarkDao = AppDatabase.getDatabase(context).noteRemarkDao()
    private var rootDir: DocumentFile? = null
    private var rootTreeUri: Uri? = null
    private var rootDocumentId: String? = null
    private var appConfig = AppConfig()

    private data class CachedText(
        val lastModified: Long,
        val length: Long,
        val text: String,
    )

    private data class FileSignature(
        val lastModified: Long,
        val length: Long,
    )

    private data class HistorySnapshotContentSource(
        val rawContent: String? = null,
        val cleanContent: String? = null,
        val tags: List<String> = emptyList(),
        val fallbackReason: String? = null,
    )

    private val cacheMutex = Mutex()
    private val refreshMutex = Mutex()
    private val pendingRefresh = AtomicBoolean(false)
    private val pendingRefreshForceReload = AtomicBoolean(false)
    private val refreshGeneration = AtomicLong(0L)
    private val textReadLocks = mutableMapOf<String, Mutex>()
    private val indexingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contentCache = LinkedHashMap<String, CachedText>(64, 0.75f, true)
    private val fileSignatures = mutableMapOf<String, FileSignature>()
    private val flowEmissionCounts = ConcurrentHashMap<String, Int>()
    private val roomContentAuditKeys = ConcurrentHashMap<String, Boolean>()
    private val noteThumbnailCache =
        object : LruCache<String, Bitmap>(NOTE_THUMBNAIL_CACHE_MAX_BYTES) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                value.byteCount.coerceAtLeast(1)
        }
    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()
    private var lastLocalWriteElapsedMs = 0L
    private val backupGson = Gson()

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
        try {
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
                return
            }

            rootDir = docFile
            rootTreeUri = resolvedRoot.treeUri
            rootDocumentId = resolvedRoot.documentId
            val rootName = docFile.name

            appConfig = metadataManager.loadConfig(docFile)

            val dbCount = noteDao.countAllNotes()
            val willScan = scanImmediately || dbCount == 0
            if (willScan) {
                refreshNotes()
            }
        } catch (e: Exception) {
            KardLeafLog.e("RoomNoteRepository", "Error setting root folder: $uriString", e)
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
            val root = rootDir ?: return@withContext false
            if (name.isBlank()) return@withContext false

            val existing = findFolder(root, name)
            if (existing != null && existing.isDirectory) {
                labelDao.insert(LabelEntity(name))
                return@withContext true
            }

            val newDir = getOrCreateFolder(root, name)
            if (newDir != null) {
                labelDao.insert(LabelEntity(name))
                markWebDavRealtimeLocalDirty()
                return@withContext true
            }
            return@withContext false
        }

    override suspend fun deleteLabel(name: String): Boolean =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext false
            val count = noteDao.countNotesInFolder(name)
            if (count > 0) return@withContext false

            deleteFolder(root, name)
            getTrashRoot(root, create = false)?.let { deleteFolder(it, name) }
            if (prefsManager.getTrashFolderName() != "Trash") {
                root.findFile("Trash")?.let { deleteFolder(it, name) }
            }

            labelDao.delete(name)
            markWebDavRealtimeLocalDirty()
            return@withContext true
        }

    override suspend fun deleteLabelWithContents(name: String): Boolean =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext false
            val folder = normalizeFolderPath(name)
            if (folder.isBlank()) return@withContext false

            val folderPrefix = "$folder/%"
            val entities = noteDao.getNoteShellsInFolderTree(folder, folderPrefix)
                .filter { !it.isTrashed }
            if (entities.isNotEmpty()) {
                val movedEntities = moveNoteEntitiesToSystemFolder(entities, isArchive = false)
                if (movedEntities.isNotEmpty()) {
                    movedEntities.forEach { prefsManager.setNotePinned(it.filePath, false) }
                    movedEntities.forEach { prefsManager.setNoteFavorite(it.filePath, false) }
                    noteDao.trashNotes(movedEntities.map { it.filePath }, System.currentTimeMillis())
                }
                if (movedEntities.size != entities.size) return@withContext false
            }

            deleteFolder(root, folder)
            labelDao.deleteTree(folder, folderPrefix)
            markWebDavRealtimeLocalDirty()
            return@withContext true
        }

    override suspend fun renameLabel(
        oldName: String,
        newName: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext false
            val oldPath = normalizeFolderPath(oldName)
            val newPath = normalizeFolderPath(newName)
            if (oldPath.isBlank() || newPath.isBlank() || oldPath == newPath) return@withContext false

            val oldParent = oldPath.substringBeforeLast("/", missingDelimiterValue = "")
            val newParent = newPath.substringBeforeLast("/", missingDelimiterValue = "")
            val newSegment = newPath.substringAfterLast("/")
            if (oldParent != newParent || newSegment.isBlank()) return@withContext false

            val parentFolder = findFolder(root, oldParent) ?: return@withContext false
            if (parentFolder.findFile(newSegment) != null) return@withContext false
            val folder = parentFolder.findFile(oldPath.substringAfterLast("/"))?.takeIf { it.isDirectory } ?: return@withContext false
            if (!folder.renameTo(newSegment)) return@withContext false

            getTrashRoot(root, create = false)
                ?.let { findFolder(it, oldParent) }
                ?.findFile(oldPath.substringAfterLast("/"))
                ?.takeIf { it.isDirectory && it.name != newSegment }
                ?.renameTo(newSegment)

            refreshNotes()
            markWebDavRealtimeLocalDirty()
            return@withContext true
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

    suspend fun getNoteForEditor(id: String): Note? {
        return withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            KardLeafLog.d(LARGE_NOTE_OPEN_TRACE_TAG, "repo getNoteForEditor start path=$id")
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
            val result = readNoteFromFileForEditor(entity, file)?.toNote() ?: return@withContext null
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo getNoteForEditor done path=$id elapsed=${SystemClock.elapsedRealtime() - startMs}ms " +
                    "contentLen=${result.content.length} previewLen=${result.contentPreview.length}",
            )
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
            val updated = readNoteFromFileForEditor(entity, file)?.copy(
                lastModifiedMs = fileModified.takeIf { it > 0L } ?: System.currentTimeMillis(),
            ) ?: return@withContext null
            if (fileModified > 0L && fileModified != entity.lastModifiedMs) {
                noteDao.insertNote(updated)
            }
            updated.toNote()
        }
    }

    suspend fun saveNote(
        note: Note,
        oldFile: java.io.File? = null,
    ): String = saveNote(note, oldFile, saveHistory = false)

    override suspend fun saveNote(
        note: Note,
        oldFile: java.io.File?,
        saveHistory: Boolean,
    ): String =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext ""
            val folderName = normalizeFolderPath(note.folder)

            // Determine the actual root for searching/saving based on status
            val effectiveRoot =
                when {
                    note.isTrashed -> getTrashRoot(root, create = true)
                    else -> root
                } ?: root

            var targetDir = getOrCreateFolder(effectiveRoot, folderName) ?: return@withContext ""

            if (note.isArchived && !note.isTrashed) {
                targetDir = targetDir.findFile("Archived") ?: targetDir.createDirectory("Archived") ?: targetDir
            }

            val baseTitle = NoteFormatUtils.sanitizeMarkdownFileBaseName(note.title)
            var finalTitle = baseTitle
            var finalFileName = "$finalTitle.md"

            var previousPath: String? = null
            var previousRawContent: String? = null
            var previousRawTags: List<String> = emptyList()
            var previousDbTags: List<String> = emptyList()
            var oldFileDoc: DocumentFile? = null

            if (oldFile != null) {
                val oldName = oldFile.name
                val oldParentName = normalizeFolderPath(oldFile.parent.orEmpty())
                previousPath = joinPath(oldParentName, oldName)
                val previousEntity = getFullNoteEntityByPathForAudit(previousPath, "save-history-snapshot")
                previousDbTags = NoteFormatUtils.tagsFromStorage(previousEntity?.yamlTags)
                logYamlTagTrace(
                    "saveNote oldEntity path=$previousPath exists=${previousEntity != null} oldDbTags=${previousEntity?.yamlTags?.let { NoteFormatUtils.tagsFromStorage(it) }.orEmpty()} oldTitle=${previousEntity?.title}",
                )

                val folderDoc = findFolder(root, oldParentName)
                val trashDoc = getTrashRoot(root, create = false)?.let { findFolder(it, oldParentName) }

                oldFileDoc = folderDoc?.findFile(oldName)
                    ?: folderDoc?.findFile("Pinned")?.findFile(oldName)
                    ?: folderDoc?.findFile("Archived")?.findFile(oldName)
                    ?: trashDoc?.findFile(oldName)

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

            var targetFileDoc = targetDir.findFile(finalFileName)
            var filePath = joinPath(folderName, finalFileName)
            var counter = 1
            while (true) {
                val sameFile = targetFileDoc != null && oldFileDoc != null && targetFileDoc.uri == oldFileDoc.uri
                val fileConflict = targetFileDoc != null && !sameFile
                val dbConflict = filePath != previousPath && noteDao.getNoteShellByPath(filePath) != null
                if (!fileConflict && !dbConflict) break

                finalTitle = "$baseTitle ($counter)"
                finalFileName = "$finalTitle.md"
                filePath = joinPath(folderName, finalFileName)
                targetFileDoc = targetDir.findFile(finalFileName)
                counter++
            }

            logYamlTagTrace(
                "saveNote start targetPath=$filePath oldFile=${oldFile?.path} noteTitle=${note.title} noteTags=${note.tags} noteContentLen=${note.content.length} saveHistory=$saveHistory",
            )

            val createdNewFile = targetFileDoc == null
            if (createdNewFile) {
                targetFileDoc = targetDir.createFile("text/markdown", finalFileName)
            }
            val writableTarget = targetFileDoc ?: run {
                KardLeafLog.e("RoomNoteRepository", "Failed to create note file: $filePath")
                return@withContext ""
            }

            val targetRawContent = if (createdNewFile) null else readText(writableTarget)
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
            val fullContent = NoteFormatUtils.constructFileContent(noteForWrite, existingContent, replaceTags = true)
            val outputFrontMatter = NoteFormatUtils.parseFrontMatter(fullContent)
            val noteRecordId = NoteFormatUtils.extractKardLeafId(outputFrontMatter) ?: filePath
            val outputTags = NoteFormatUtils.extractTags(outputFrontMatter)
            val writtenYamlTags = NoteFormatUtils.tagsToStorage(outputTags)
            logYamlTagTrace(
                "saveNote output targetPath=$filePath recordId=$noteRecordId outputTags=$outputTags writtenYamlTags=${NoteFormatUtils.tagsFromStorage(writtenYamlTags)} outputLen=${fullContent.length}",
            )

            val writtenLastModified =
                try {
                    context.contentResolver.openOutputStream(writableTarget.uri, "wt")?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(fullContent)
                        }
                    } ?: throw IOException("openOutputStream returned null")
                    lastLocalWriteElapsedMs = SystemClock.elapsedRealtime()
                    updateTextCache(writableTarget, fullContent)
                    writableTarget.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    KardLeafLog.e("RoomNoteRepository", "Failed to write note file: $filePath", e)
                    if (createdNewFile && previousPath != filePath) {
                        writableTarget.delete()
                    }
                    return@withContext ""
                }

            val oldPathToRemove =
                previousPath?.takeIf { it != filePath && oldFileDoc != null && oldFileDoc.uri != writableTarget.uri }
            if (oldPathToRemove != null && oldFileDoc?.delete() != true) {
                KardLeafLog.e("RoomNoteRepository", "Failed to delete old note file after save: $oldPathToRemove")
                return@withContext ""
            }

            val createdAtMs =
                previousPath?.let { noteDao.getNoteShellByPath(it)?.createdAtMs }
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
            noteDao.insertNote(entity)
            oldPathToRemove?.let { noteDao.deleteNoteByPath(it) }
            logYamlTagTrace(
                "saveNote dbInserted path=$filePath dbTags=${NoteFormatUtils.tagsFromStorage(entity.yamlTags)} createdAtMs=$createdAtMs lastModifiedMs=$writtenLastModified",
            )
            fileSignatures[filePath] = FileSignature(writtenLastModified, writableTarget.length())
            syncNoteRecordsWithResolvedId(filePath, noteRecordId)
            previousPath?.takeIf { it != filePath }?.let {
                logYamlTagTrace("saveNote pathChanged oldPath=$it newPath=$filePath recordId=$noteRecordId outputTags=$outputTags")
                fileSignatures.remove(it)
                prefsManager.replacePinnedNotePath(it, filePath)
                prefsManager.replaceFavoriteNotePath(it, filePath)
                if (noteRecordId == filePath) {
                    noteHistoryDao.replaceNoteId(it, filePath)
                    noteRemarkDao.replaceNoteId(it, filePath)
                    val historyLimit = prefsManager.getHistoryVersionLimit()
                    if (historyLimit > 0) {
                        noteHistoryDao.pruneOldVersions(filePath, historyLimit)
                    }
                } else {
                    syncNoteRecordsWithResolvedId(it, noteRecordId)
                    val historyLimit = prefsManager.getHistoryVersionLimit()
                    if (historyLimit > 0) {
                        noteHistoryDao.pruneOldVersions(noteRecordId, historyLimit)
                    }
                }
            }
            prefsManager.setNotePinned(filePath, note.isPinned && !note.isArchived && !note.isTrashed)
            prefsManager.setNoteFavorite(filePath, note.isFavorite && !note.isTrashed)
            markWebDavRealtimeLocalDirty()

            return@withContext filePath
        }

    override fun getNoteHistory(noteId: String): Flow<List<NoteHistory>> = flow {
        val recordId = resolveNoteRecordId(noteId)
        emitAll(
            noteHistoryDao
                .getHistoryPreview(
                    noteId = recordId,
                    previewLimit = HISTORY_DIALOG_PREVIEW_CHAR_LIMIT,
                    fullContentLimit = HISTORY_DIALOG_FULL_CONTENT_CHAR_LIMIT,
                )
                .map { histories ->
                    histories.map { it.toNoteHistory(HISTORY_DIALOG_FULL_CONTENT_CHAR_LIMIT) }
                },
        )
    }

    override fun searchHistoryPreview(query: String): Flow<List<NoteHistory>> {
        val safeQuery = query.trim()
        val meaningful = SearchQueryUtils.isMeaningfulSearchQuery(safeQuery)
        KardLeafLog.d(SEARCH_TRACE_TAG, "history request ${SearchQueryUtils.describeForLog(query)}")
        if (!meaningful) {
            KardLeafLog.d(SEARCH_TRACE_TAG, "history skip reason=notMeaningful ${SearchQueryUtils.describeForLog(query)}")
            return flowOf(emptyList())
        }
        return noteHistoryDao.searchHistoryPreview(safeQuery, SEARCH_RESULT_LIMIT).map { histories ->
            KardLeafLog.d(SEARCH_TRACE_TAG, "history result queryLen=${safeQuery.length} count=${histories.size}")
            histories.map { it.toNoteHistory() }
        }
    }

    override fun searchNoteMatches(query: String): Flow<List<NoteSearchMatch>> {
        val safeQuery = query.trim()
        val meaningful = SearchQueryUtils.isMeaningfulSearchQuery(safeQuery)
        KardLeafLog.d(SEARCH_TRACE_TAG, "notes request ${SearchQueryUtils.describeForLog(query)}")
        if (!meaningful) {
            KardLeafLog.d(SEARCH_TRACE_TAG, "notes skip reason=notMeaningful ${SearchQueryUtils.describeForLog(query)}")
            return flowOf(emptyList())
        }
        return noteDao.searchNoteMatches(safeQuery, SEARCH_RESULT_LIMIT).map { matches ->
            KardLeafLog.d(
                SEARCH_TRACE_TAG,
                "notes result queryLen=${safeQuery.length} count=${matches.size} " +
                    "first=${matches.firstOrNull()?.let { it.scope + ":" + it.startOffset }}",
            )
            matches
        }
    }

    suspend fun exportUserDataBackup(): String =
        withContext(Dispatchers.IO) {
            val backup =
                UserDataBackup(
                    favoriteNotePaths = prefsManager.getFavoriteNotePaths().toList(),
                    pinnedNotePaths = prefsManager.getPinnedNotePaths().toList(),
                    history =
                        noteHistoryDao.getAllHistory().map { history ->
                            HistoryBackup(
                                id = history.id,
                                noteId = history.noteId,
                                title = history.title,
                                content = history.content,
                                savedAtMs = history.savedAtMs,
                            )
                        },
                    remarks =
                        noteRemarkDao.getAllRemarks().map { remark ->
                            RemarkBackup(
                                id = remark.id,
                                noteId = remark.noteId,
                                content = remark.content,
                                createdAtMs = remark.createdAtMs,
                                updatedAtMs = remark.updatedAtMs,
                            )
                        },
                )
            backupGson.toJson(backup)
        }

    suspend fun importUserDataBackup(json: String) =
        withContext(Dispatchers.IO) {
            val backup = backupGson.fromJson(json, UserDataBackup::class.java) ?: return@withContext
            prefsManager.replaceFavoriteNotePaths(backup.favoriteNotePaths.orEmpty())
            prefsManager.replacePinnedNotePaths(backup.pinnedNotePaths.orEmpty())
            val historyBackup = backup.history.orEmpty()
            if (historyBackup.isNotEmpty()) {
                noteHistoryDao.insertAll(
                    historyBackup.map { history ->
                        NoteHistoryEntity(
                            id = history.id,
                            noteId = history.noteId,
                            title = history.title,
                            content = history.content,
                            savedAtMs = history.savedAtMs,
                        )
                    },
                )
            }
            val remarkBackup = backup.remarks.orEmpty()
            if (remarkBackup.isNotEmpty()) {
                noteRemarkDao.insertAll(
                    remarkBackup.map { remark ->
                        NoteRemarkEntity(
                            id = remark.id,
                            noteId = remark.noteId,
                            content = remark.content,
                            createdAtMs = remark.createdAtMs ?: remark.updatedAtMs,
                            updatedAtMs = remark.updatedAtMs,
                        )
                    },
                )
            }
        }

    override suspend fun deleteNoteHistory(historyId: Long) =
        withContext(Dispatchers.IO) {
            noteHistoryDao.deleteById(historyId)
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

    override suspend fun getHistoryCleanupPreview(keep: Int): List<HistoryCleanupPreview> =
        withContext(Dispatchers.IO) {
            val safeKeep = keep.coerceIn(
                PrefsManager.MIN_HISTORY_VERSION_LIMIT,
                PrefsManager.MAX_HISTORY_VERSION_LIMIT,
            )
            noteHistoryDao.getHistoryCountsOverLimit(safeKeep).map { item ->
                HistoryCleanupPreview(
                    noteId = item.noteId,
                    versionCount = item.versionCount,
                    deleteCount = item.versionCount - safeKeep,
                )
            }
        }

    override suspend fun cleanupOldHistoryVersions() =
        withContext(Dispatchers.IO) {
            val keep = prefsManager.getHistoryVersionLimit()
            val noteIds = noteHistoryDao.getAllHistoryNoteIds()
            noteIds.forEach { noteId ->
                noteHistoryDao.pruneOldVersions(
                    noteId = noteId,
                    keep = keep,
                )
            }
        }

    // region 隐私空间笔记（仅存 Room，不写外部文件，可导入导出）
    fun getAllPrivacyNotes(): Flow<List<PrivacyNoteEntity>> = privacyNoteDao.getAll()

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
    }

    suspend fun deleteNoteRemark(remarkId: Long) =
        withContext(Dispatchers.IO) {
            noteRemarkDao.deleteById(remarkId)
        }

    suspend fun deleteNoteRemarks(noteId: String) =
        withContext(Dispatchers.IO) {
            if (noteId.isBlank()) return@withContext
            val recordId = resolveNoteRecordId(noteId)
            noteRemarkDao.deleteByNoteId(noteId)
            if (recordId != noteId) {
                noteRemarkDao.deleteByNoteId(recordId)
            }
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


    suspend fun savePrivacyNote(id: Long, title: String, content: String): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (id > 0) {
                val existing = privacyNoteDao.getById(id)
                privacyNoteDao.upsert(
                    (existing ?: PrivacyNoteEntity(id = id, title = title, content = content, updatedAtMs = now))
                        .copy(title = title, content = content, updatedAtMs = now),
                )
                id
            } else {
                privacyNoteDao.upsert(PrivacyNoteEntity(title = title, content = content, updatedAtMs = now))
            }
        }

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

            val privacyId = privacyNoteDao.upsert(
                PrivacyNoteEntity(
                    title = title,
                    content = content,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
            val sourceFile = findNoteDocument(entity)
            if (sourceFile != null && !sourceFile.delete()) {
                privacyNoteDao.deleteById(privacyId)
                return@withContext false
            }
            noteDao.deleteNoteByPath(noteId)
            deleteNoteRecordsForPath(noteId)
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

    suspend fun deletePrivacyNote(id: Long) =
        withContext(Dispatchers.IO) {
            privacyNoteDao.deleteById(id)
        }

    suspend fun exportPrivacyNotes(): String =
        withContext(Dispatchers.IO) {
            Gson().toJson(
                privacyNoteDao.getAllOnce().map {
                    PrivacyNoteBackup(title = it.title, content = it.content, updatedAtMs = it.updatedAtMs)
                },
            )
        }

    suspend fun importPrivacyNotes(json: String): Int =
        withContext(Dispatchers.IO) {
            val type = object : com.google.gson.reflect.TypeToken<List<PrivacyNoteBackup>>() {}.type
            val list: List<PrivacyNoteBackup> = try {
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            list.forEach { item ->
                privacyNoteDao.upsert(
                    PrivacyNoteEntity(title = item.title, content = item.content, updatedAtMs = item.updatedAtMs),
                )
            }
            list.size
        }

    private data class PrivacyNoteBackup(
        val title: String = "",
        val content: String = "",
        val updatedAtMs: Long = 0L,
    )
    // endregion

    override suspend fun deleteNote(id: String) =
        withContext(Dispatchers.IO) {
            if (!moveNoteToSystemFolder(id, isArchive = false)) return@withContext
            prefsManager.setNotePinned(id, false)
            prefsManager.setNoteFavorite(id, false)
            noteDao.trashNote(id, System.currentTimeMillis())
            markWebDavRealtimeLocalDirty()
        }

    override suspend fun deleteNotes(noteIds: List<String>) =
        withContext(Dispatchers.IO) {
            val entities = noteDao.getNoteShellsByPaths(noteIds)
            if (entities.isEmpty()) return@withContext
            val movedEntities = moveNoteEntitiesToSystemFolder(entities, isArchive = false)
            if (movedEntities.isEmpty()) return@withContext
            movedEntities.forEach { prefsManager.setNotePinned(it.filePath, false) }
            movedEntities.forEach { prefsManager.setNoteFavorite(it.filePath, false) }
            noteDao.trashNotes(movedEntities.map { it.filePath }, System.currentTimeMillis())
            markWebDavRealtimeLocalDirty()
        }

    override suspend fun archiveNote(id: String) =
        withContext(Dispatchers.IO) {
            if (!moveNoteToSystemFolder(id, isArchive = true)) return@withContext
            prefsManager.setNotePinned(id, false)
            noteDao.archiveNote(id)
            markWebDavRealtimeLocalDirty()
        }

    override suspend fun archiveNotes(noteIds: List<String>) =
        withContext(Dispatchers.IO) {
            val entities = noteDao.getNoteShellsByPaths(noteIds)
            if (entities.isEmpty()) return@withContext
            val movedEntities = moveNoteEntitiesToSystemFolder(entities, isArchive = true)
            if (movedEntities.isEmpty()) return@withContext
            movedEntities.forEach { prefsManager.setNotePinned(it.filePath, false) }
            noteDao.archiveNotes(movedEntities.map { it.filePath })
            markWebDavRealtimeLocalDirty()
        }

    override suspend fun restoreNote(id: String) =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteShellByPath(id) ?: return@withContext
            val root = rootDir ?: return@withContext

            val folder = entity.folder
            val fileName = entity.fileName

            val trashRoot = getTrashRoot(root, create = false)
            val deletedSource =
                if (folder.isBlank()) {
                    trashRoot?.findFile(fileName)
                } else {
                    trashRoot?.let { findFolder(it, folder) }?.findFile(fileName)
                }
            val archiveSource = findFolder(root, folder)?.findFile("Archived")?.findFile(fileName)
            val sourceFile = deletedSource ?: archiveSource

            val targetFolder = getOrCreateFolder(root, folder) ?: return@withContext
            if (sourceFile == null || !moveMarkdownDocument(sourceFile, targetFolder, fileName, "restoreNote")) {
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

            var targetFolderDoc = targetRoot?.let { getOrCreateFolder(it, targetFolder) }

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
                        if (prefsManager.getImagePathMode() == PrefsManager.ImagePathMode.RELATIVE) {
                            rewriteRelativeImageRefs(rawContent, sourceFolder, targetFolder)
                        } else {
                            rawContent
                        }

                    // 文件名冲突处理：如果目标位置已有同名文件（且不是源文件本身），加 (1) (2) ... 后缀
                    var resolvedFileName = fileName
                    var counter = 1
                    val maxRetries = 100
                    while (targetFolderDoc.findFile(resolvedFileName) != null && counter <= maxRetries) {
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
                        noteDao.insertNote(entity.copy(filePath = newPath, recordId = movedRecordId, folder = targetFolder, fileName = resolvedFileName))
                        movedPaths += MovedNotePath(oldPath = oldPath, newPath = newPath)
                        if (isPinned) {
                            prefsManager.replacePinnedNotePath(oldPath, newPath)
                        }
                        if (entity.isFavorite) {
                            prefsManager.replaceFavoriteNotePath(oldPath, newPath)
                        }
                        if (movedRecordId == newPath) {
                            noteHistoryDao.replaceNoteId(oldPath, newPath)
                            noteRemarkDao.replaceNoteId(oldPath, newPath)
                        } else {
                            noteHistoryDao.replaceNoteId(oldPath, movedRecordId)
                            noteRemarkDao.replaceNoteId(oldPath, movedRecordId)
                            syncNoteRecordsWithResolvedId(newPath, movedRecordId)
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

    override suspend fun refreshNotes() = refreshNotesInternal(forceReloadIfMetadataUnchanged = false)

    suspend fun refreshNotesFromExternalChange() =
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
                noteDao.deleteNoteByPath(path)
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
                createdAtMs = existing?.createdAtMs ?: lastModified,
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
        fileSignatures[path] = FileSignature(lastModified, length)
        if (!entity.isTrashed) {
            labelDao.insertAll(folderPathWithParents(entity.folder).map { LabelEntity(it) })
        }
        return entity
    }

    private suspend fun refreshNotesInternal(forceReloadIfMetadataUnchanged: Boolean): Unit =
        withContext(Dispatchers.IO) {
            val refreshStartMs = SystemClock.elapsedRealtime()
            val root = rootDir ?: run {
                logStartupPerf("refreshNotesInternal skip root=null force=$forceReloadIfMetadataUnchanged")
                return@withContext
            }
            if (!refreshMutex.tryLock()) {
                pendingRefresh.set(true)
                if (forceReloadIfMetadataUnchanged) {
                    pendingRefreshForceReload.set(true)
                }
                logStartupPerf("refreshNotesInternal skip busy force=$forceReloadIfMetadataUnchanged")
                return@withContext
            }

            var indexingContinuesInBackground = false
            try {
                val generation = refreshGeneration.incrementAndGet()
                _isIndexing.value = true
                logStartupPerf("refreshNotesInternal start force=$forceReloadIfMetadataUnchanged thread=${Thread.currentThread().name}")
                // 1. Get current DB state
                val dbLoadStartMs = SystemClock.elapsedRealtime()
                val dbNotes = noteDao.getAllNoteMetadataSync().associateBy { it.filePath }
                val dbPaths = dbNotes.keys
                logStartupPerf(
                    "refreshNotesInternal db loaded count=${dbNotes.size} elapsed=${SystemClock.elapsedRealtime() - dbLoadStartMs}ms total=${SystemClock.elapsedRealtime() - refreshStartMs}ms",
                )

                // 2. Scan file system for file metadata and real folders.
                val fsFiles = mutableMapOf<String, FileMeta>()
                val fsFolders = mutableSetOf<String>()

                val scanStartMs = SystemClock.elapsedRealtime()
                try {
                    val usedFastScan = scanVaultMetaFast(root, fsFiles, fsFolders)
                    if (!usedFastScan) {
                        // Scan root notes and user folders recursively.
                        scanFolderMeta(root, "", isArchived = false, isTrashed = false, fsFiles, fsFolders)

                        // Scan Trash
                        getTrashRoot(root, create = false)?.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
                            folder.name?.let { name ->
                                scanFolderMeta(folder, name, isArchived = false, isTrashed = true, fsFiles, fsFolders)
                            }
                        }
                    }
                    logStartupPerf(
                        "refreshNotesInternal scan done files=${fsFiles.size} elapsed=${SystemClock.elapsedRealtime() - scanStartMs}ms total=${SystemClock.elapsedRealtime() - refreshStartMs}ms",
                    )
                } catch (e: Exception) {
                    KardLeafLog.e("RoomNoteRepository", "Error scanning root structure", e)
                    logStartupPerf("refreshNotesInternal scan failed elapsed=${SystemClock.elapsedRealtime() - scanStartMs}ms")
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

                val toProcess =
                    if (shouldForceReloadAllContent) {
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
                    noteDao.deleteNotesByPaths(toDelete)
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
                refreshMutex.unlock()
                logStartupPerf("refreshNotesInternal done total=${SystemClock.elapsedRealtime() - refreshStartMs}ms")
                if (pendingRefresh.getAndSet(false)) {
                    val pendingForce = pendingRefreshForceReload.getAndSet(false)
                    logStartupPerf("refreshNotesInternal run pending force=$pendingForce")
                    refreshNotesInternal(forceReloadIfMetadataUnchanged = pendingForce)
                }
            }
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
                            createdAtMs = existing[path]?.createdAtMs ?: meta.lastModified,
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

        val trashName = prefsManager.getTrashFolderName()
        val trashDocumentId =
            findSafChildDirectoryId(treeUri, rootDocumentId, trashName)
                ?: if (trashName != "Trash") findSafChildDirectoryId(treeUri, rootDocumentId, "Trash") else null
        if (trashDocumentId != null) {
            querySafChildren(treeUri, trashDocumentId)
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
        val children = querySafChildren(treeUri, documentId)

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
                    querySafChildren(treeUri, archivedFolder.documentId)
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
                } ?: emptyList()
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
            emptyList()
        }
    }

    private fun findSafChildDirectoryId(
        treeUri: Uri,
        parentDocumentId: String,
        name: String,
    ): String? =
        querySafChildren(treeUri, parentDocumentId)
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

    private fun isUserLabelFolderName(name: String): Boolean =
        name.isNotBlank() &&
            !name.startsWith(".") &&
            name != "Archived" &&
            name != prefsManager.getTrashFolderName() &&
            name != "Trash"

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
                val base = getOrCreateFolder(root, folder)
                base?.findFile("Archived") ?: base?.createDirectory("Archived")
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
    ): Boolean {
        if (targetFolder.findFile(fileName) != null) {
            KardLeafLog.e("RoomNoteRepository", "$reason target conflict: $fileName")
            return false
        }
        val content =
            try {
                readText(sourceFile)
            } catch (e: Exception) {
                KardLeafLog.e("RoomNoteRepository", "$reason failed to read source: $fileName", e)
                return false
            }
        val newFile = targetFolder.createFile("text/markdown", fileName) ?: run {
            KardLeafLog.e("RoomNoteRepository", "$reason failed to create target: $fileName")
            return false
        }
        try {
            context.contentResolver.openOutputStream(newFile.uri)?.use { os ->
                OutputStreamWriter(os).use { it.write(content) }
            } ?: throw IOException("openOutputStream returned null")
        } catch (e: Exception) {
            KardLeafLog.e("RoomNoteRepository", "$reason failed to write target: $fileName", e)
            newFile.delete()
            return false
        }
        if (!sourceFile.delete()) {
            KardLeafLog.e("RoomNoteRepository", "$reason failed to delete source: $fileName")
            newFile.delete()
            return false
        }
        return true
    }

    private suspend fun readText(
        file: DocumentFile,
        bypassCache: Boolean = false,
    ): String = readTextOrNull(file, bypassCache).orEmpty()

    private suspend fun readTextOrNull(
        file: DocumentFile,
        bypassCache: Boolean = false,
    ): String? =
        withContext(Dispatchers.IO) {
            val startMs = SystemClock.elapsedRealtime()
            val pathKey = file.uri.toString()
            val lastModified = file.lastModified()
            val length = file.length()
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo readText start name=${file.name} length=$length lastModified=$lastModified bypassCache=$bypassCache uri=$pathKey",
            )
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external readText start name=${file.name} length=$length lastModified=$lastModified bypassCache=$bypassCache uri=$pathKey",
            )

            if (!bypassCache) {
                cacheMutex.withLock {
                    val cached = contentCache[pathKey]
                    if (cached != null && cached.lastModified == lastModified && cached.length == length) {
                        KardLeafLog.d(
                            LARGE_NOTE_OPEN_TRACE_TAG,
                            "repo readText cache hit name=${file.name} length=$length textLen=${cached.text.length} " +
                                "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                        KardLeafLog.d(
                            OPEN_PATH_PROBE_TAG,
                            "external readText cacheHit name=${file.name} length=$length textLen=${cached.text.length} " +
                                "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                        return@withContext cached.text
                    }
                }
            }

            val readLock = cacheMutex.withLock {
                textReadLocks.getOrPut(pathKey) { Mutex() }
            }
            return@withContext readLock.withLock readTextLock@ {
                val cachedAfterWait = if (!bypassCache) {
                    cacheMutex.withLock {
                        val cached = contentCache[pathKey]
                        if (cached != null && cached.lastModified == lastModified && cached.length == length) {
                            KardLeafLog.d(
                                LARGE_NOTE_OPEN_TRACE_TAG,
                                "repo readText cache hit after wait name=${file.name} length=$length textLen=${cached.text.length} " +
                                    "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                            )
                            cached.text
                        } else {
                            null
                        }
                    }
                } else {
                    null
                }
                if (cachedAfterWait != null) {
                    return@readTextLock cachedAfterWait
                }

                try {
                    var text = readTextFromUri(file.uri, file.name.orEmpty())
                    if (text != null && text.isEmpty() && length > 0L) {
                        KardLeafLog.w(
                            LARGE_NOTE_OPEN_TRACE_TAG,
                            "repo readText empty result for non-empty file, retry name=${file.name} length=$length " +
                                "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                        delay(80L)
                        val retryText = readTextFromUri(file.uri, file.name.orEmpty())
                        if (!retryText.isNullOrEmpty()) {
                            text = retryText
                        }
                    }

                    if (text != null) {
                        if (text.isNotEmpty() || length == 0L) {
                            cacheText(pathKey, lastModified, length, text)
                        } else {
                            KardLeafLog.w(
                                LARGE_NOTE_OPEN_TRACE_TAG,
                                "repo readText skipped caching suspicious empty text name=${file.name} length=$length",
                            )
                        }
                        KardLeafLog.d(
                            LARGE_NOTE_OPEN_TRACE_TAG,
                            "repo readText done name=${file.name} fileLength=$length textLen=${text.length} " +
                                "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                        KardLeafLog.d(
                            OPEN_PATH_PROBE_TAG,
                            "external readText done name=${file.name} fileLength=$length textLen=${text.length} " +
                                "elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                        text
                    } else {
                        KardLeafLog.w(
                            LARGE_NOTE_OPEN_TRACE_TAG,
                            "repo readText empty stream name=${file.name} length=$length elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                        )
                        null
                    }
                } catch (e: Exception) {
                    KardLeafLog.e(LARGE_NOTE_OPEN_TRACE_TAG, "repo readText failed name=${file.name} length=$length", e)
                    KardLeafLog.e("RoomNoteRepository", "Exception reading markdown.", e)
                    null
                }
            }
        }

    private fun readTextFromUri(
        uri: Uri,
        traceName: String = "",
    ): String? {
        val startMs = SystemClock.elapsedRealtime()
        val inputStream = context.contentResolver.openInputStream(uri) ?: run {
            KardLeafLog.w(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo readText openInputStream null name=$traceName elapsed=${SystemClock.elapsedRealtime() - startMs}ms uri=$uri",
            )
            return null
        }
        KardLeafLog.d(
            LARGE_NOTE_OPEN_TRACE_TAG,
            "repo readText openInputStream done name=$traceName elapsed=${SystemClock.elapsedRealtime() - startMs}ms uri=$uri",
        )
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external openInputStream done name=$traceName elapsed=${SystemClock.elapsedRealtime() - startMs}ms uri=$uri",
        )
        val readStartMs = SystemClock.elapsedRealtime()
        return inputStream.use { stream ->
            BufferedReader(stream.reader()).use { reader ->
                reader.readText()
            }
        }.also { text ->
            KardLeafLog.d(
                LARGE_NOTE_OPEN_TRACE_TAG,
                "repo readText stream read done name=$traceName textLen=${text.length} readElapsed=${SystemClock.elapsedRealtime() - readStartMs}ms " +
                    "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external streamRead done name=$traceName textLen=${text.length} readElapsed=${SystemClock.elapsedRealtime() - readStartMs}ms " +
                    "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
        }
    }

    private suspend fun updateTextCache(
        file: DocumentFile,
        text: String,
    ) {
        cacheText(
            pathKey = file.uri.toString(),
            lastModified = file.lastModified(),
            length = file.length(),
            text = text,
        )
    }

    private suspend fun cacheText(
        pathKey: String,
        lastModified: Long,
        length: Long,
        text: String,
    ) {
        cacheMutex.withLock {
            if (contentCache.size >= MAX_TEXT_CACHE_ENTRIES) {
                contentCache.remove(contentCache.keys.first())
            }
            contentCache[pathKey] = CachedText(lastModified, length, text)
        }
    }

    private suspend fun clearTextCache() {
        cacheMutex.withLock {
            contentCache.clear()
        }
    }

    private suspend fun readNoteFromFileForEditor(
        entity: NoteEntity,
        file: DocumentFile,
    ): NoteEntity? {
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
        return entity.copy(
            recordId = parsedRecordId,
            title = file.name?.substringBeforeLast(".") ?: entity.title,
            contentPreview = frontMatter.cleanContent.take(200),
            content = frontMatter.cleanContent,
            lastModifiedMs = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
            color = 0xFFFFFFFF,
            reminder = frontMatter.reminder,
            firstImageReference = firstImageReference,
            yamlTags = NoteFormatUtils.tagsToStorage(parsedTags),
        )
    }

    private suspend fun readLatestNoteFromFile(
        entity: NoteEntity,
        file: DocumentFile,
        fileModified: Long,
    ): NoteEntity? {
        val updated = readNoteFromFileForEditor(entity, file)?.copy(
            lastModifiedMs = fileModified.takeIf { it > 0L } ?: System.currentTimeMillis(),
        ) ?: return null
        noteDao.insertNote(updated)
        return updated
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

            val withObsidianImages =
                NoteFormatUtils.obsidianImageReferenceRegex.replace(markdown) { match ->
                    val reference = match.groupValues[1].trim()
                    resolveImageDataUri(currentFolder, reference, mode = "preview")?.let { dataUri ->
                        "![]($dataUri)"
                    } ?: match.value
                }

            val resolvedMarkdown = NoteFormatUtils.localMarkdownImageReferenceWithAltRegex.replace(withObsidianImages) { match ->
                val alt = match.groupValues[1]
                val reference = match.groupValues[2].trim().trim('\"', '\'')
                resolveImageDataUri(currentFolder, reference, mode = "preview")?.let { dataUri ->
                    "![$alt]($dataUri)"
                } ?: match.value
            }
            if (obsidianCount > 0 || markdownCount > 0) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external resolveMarkdownImages done folder=$currentFolder markdownLen=${markdown.length} " +
                        "resultLen=${resolvedMarkdown.length} refs=${obsidianCount + markdownCount} elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
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
            writeTextDocument(sourceFile, drawingSource)
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
        val cleanRef = normalizeFolderPath(Uri.decode(reference).substringBefore("#"))
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
                .takeIf { it in setOf("png", "jpg", "jpeg", "gif", "webp", "svg") }
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
            val bitmap = resolveImageThumbnailBitmapInternal(note, reference, maxWidthPx = 240, maxHeightPx = 200)
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
            resolveImageThumbnailBitmapInternal(note, reference, maxWidthPx = 360, maxHeightPx = 360)
        }

    private fun resolveImageThumbnailBitmapInternal(
        note: Note,
        reference: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? {
        val startMs = SystemClock.elapsedRealtime()
        val cleanReference = reference.takeIf { it.isNotBlank() } ?: return null
        val cacheKey = "${note.file.path}|${note.lastModified.time}|$maxWidthPx|$maxHeightPx|$cleanReference"
        val cached = synchronized(noteThumbnailCache) { noteThumbnailCache.get(cacheKey) }
        if (cached != null) {
            if (!cached.isRecycled) {
                KardLeafLog.d(
                    OPEN_PATH_PROBE_TAG,
                    "external thumbnail cacheHit path=${note.file.path} folder=${note.folder} ref=$cleanReference elapsed=${SystemClock.elapsedRealtime() - startMs}ms",
                )
                return cached
            }
            synchronized(noteThumbnailCache) { noteThumbnailCache.remove(cacheKey) }
        }

        val locateStartMs = SystemClock.elapsedRealtime()
        val imageFile = findImageFile(note.folder, cleanReference) ?: run {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external thumbnail imageMissing path=${note.file.path} folder=${note.folder} ref=$cleanReference " +
                    "locateElapsed=${SystemClock.elapsedRealtime() - locateStartMs}ms totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return null
        }
        val locateElapsedMs = SystemClock.elapsedRealtime() - locateStartMs
        val decodeStartMs = SystemClock.elapsedRealtime()
        val bitmap = decodeSampledBitmap(imageFile, maxWidthPx = maxWidthPx, maxHeightPx = maxHeightPx) ?: run {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external thumbnail decodeNull path=${note.file.path} folder=${note.folder} ref=$cleanReference name=${imageFile.name} " +
                    "locateElapsed=${locateElapsedMs}ms decodeElapsed=${SystemClock.elapsedRealtime() - decodeStartMs}ms " +
                    "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return null
        }
        synchronized(noteThumbnailCache) {
            noteThumbnailCache.put(cacheKey, bitmap)
        }
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external thumbnail decoded path=${note.file.path} folder=${note.folder} ref=$cleanReference name=${imageFile.name} " +
                "locateElapsed=${locateElapsedMs}ms decodeElapsed=${SystemClock.elapsedRealtime() - decodeStartMs}ms " +
                "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
        )
        return bitmap
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
            val result = found.mapNotNull { reference ->
                resolveImageDataUri(currentFolder, reference, mode = "livePreview")?.let { dataUri ->
                    NoteImage(reference = reference, dataUri = dataUri)
                }
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
            else -> null
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
            return thumbnailImageDataUri(imageFile)
        }
        val readStartMs = SystemClock.elapsedRealtime()
        val readResult = readImageBytesWithinLimit(imageFile.uri, KardLeafContentLimits.IMAGE_DATA_URI_MAX_BYTES)
        if (readResult.exceededLimit) {
            KardLeafLog.d(
                OPEN_PATH_PROBE_TAG,
                "external resolveImageDataUri exceededLimit folder=$currentFolder ref=$reference readElapsed=${SystemClock.elapsedRealtime() - readStartMs}ms " +
                    "totalElapsed=${SystemClock.elapsedRealtime() - startMs}ms",
            )
            return thumbnailImageDataUri(imageFile)
        }
        val bytes = readResult.bytes ?: return null

        val dataUri = "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
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
        val cleanRef = normalizeFolderPath(Uri.decode(reference).substringBefore("#"))
        if (cleanRef.isBlank()) {
            imageTrace { "imageResolve directUri missing" }
            return null
        }
        imageReferenceCandidates(currentFolder, cleanRef).forEach { path ->
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, "$rootDocumentId/$path")
            val document = DocumentFile.fromSingleUri(context, documentUri)
            if (runCatching { document?.isFile == true }.getOrDefault(false) && document != null) {
                return document
            }
        }
        findImageFileInConfiguredImageFolder(currentFolder, cleanRef)?.let { return it }
        imageTrace { "imageResolve directUri missing" }
        return null
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
        val cleanRef = normalizeFolderPath(Uri.decode(reference).substringBefore("#"))
        if (cleanRef.isBlank()) return null

        val current = normalizeFolderPath(currentFolder)
        val candidates = imageReferenceCandidates(current, cleanRef)
        KardLeafLog.d(
            OPEN_PATH_PROBE_TAG,
            "external findReferencedDocument start folder=$currentFolder ref=$reference cleanRef=$cleanRef candidates=${candidates.size}",
        )

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
            .distinct()

    private fun decodeSampledBitmap(
        imageFile: DocumentFile,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? {
        val startMs = SystemClock.elapsedRealtime()
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
            else -> null
        }

    override suspend fun emptyTrash() =
        withContext(Dispatchers.IO) {
            val deletedPaths = noteDao.getTrashedNoteShellsSync()
                .filter { entity ->
                    val deleted = findNoteDocument(entity)?.delete() == true
                    if (!deleted) KardLeafLog.e("RoomNoteRepository", "Failed to delete trashed note file: ${entity.filePath}")
                    deleted
                }
                .map { it.filePath }

            if (deletedPaths.isNotEmpty()) {
                noteDao.deleteNotesByPaths(deletedPaths)
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
            val deletedPaths = expiredEntities
                .filter { entity ->
                    val deleted = findNoteDocument(entity)?.delete() == true
                    if (!deleted) KardLeafLog.e("RoomNoteRepository", "Failed to delete expired trashed note file: ${entity.filePath}")
                    deleted
                }
                .map { entity ->
                    prefsManager.setNotePinned(entity.filePath, false)
                    prefsManager.setNoteFavorite(entity.filePath, false)
                    entity.filePath
                }

            if (deletedPaths.isNotEmpty()) {
                noteDao.deleteNotesByPaths(deletedPaths)
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
        val updatedEntity = readNoteFromFileForEditor(entity, file)?.copy(lastModifiedMs = writtenLastModified)
            ?: return notePath
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
        noteHistoryDao.replaceNoteId(notePath, recordId)
        noteRemarkDao.replaceNoteId(notePath, recordId)
    }

    private suspend fun deleteNoteRecordsForPath(notePath: String) {
        if (notePath.isBlank()) return
        val recordId = resolveNoteRecordId(notePath)
        noteHistoryDao.deleteByNoteId(notePath)
        noteRemarkDao.deleteByNoteId(notePath)
        if (recordId != notePath) {
            noteHistoryDao.deleteByNoteId(recordId)
            noteRemarkDao.deleteByNoteId(recordId)
        }
    }

    private fun NoteEntity.toNote(): Note {
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
            color = color,
            reminder = reminder,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isArchived = isArchived,
            isTrashed = isTrashed,
            deletedAt = deletedAtMs?.let { Date(it) },
            firstImageReference = firstImageReference?.takeIf { it.isNotBlank() },
            tags = noteTags,
        )
    }

    private fun NoteHistoryEntity.toNoteHistory(): NoteHistory {
        return NoteHistory(
            id = id,
            noteId = noteId,
            title = title,
            content = content,
            savedAt = Date(savedAtMs),
            contentLength = content.length,
            contentIsPreview = false,
        )
    }

    private fun NoteHistoryPreviewEntity.toNoteHistory(fullContentLimit: Int): NoteHistory {
        return NoteHistory(
            id = id,
            noteId = noteId,
            title = title,
            content = content,
            savedAt = Date(savedAtMs),
            contentLength = contentLength,
            contentIsPreview = contentLength > fullContentLimit,
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
    }

    private fun hasTitleOrContentChanged(
        entity: NoteEntity,
        note: Note,
        externalContent: String? = null,
    ): Boolean {
        return entity.title != note.title || (externalContent ?: entity.content) != note.content
    }

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
                if (entity.isArchived) add(joinPath(entity.folder, "Archived/${entity.fileName}"))
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
            entity.isArchived -> {
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
private fun NoteRemarkEntity.toNoteRemark(): NoteRemark =
    NoteRemark(
        id = id,
        noteId = noteId,
        content = content,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )
