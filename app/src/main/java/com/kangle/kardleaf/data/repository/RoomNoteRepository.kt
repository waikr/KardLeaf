package com.kangle.kardleaf.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.kangle.kardleaf.data.database.PrivacyNoteDao
import com.kangle.kardleaf.data.database.PrivacyNoteEntity
import com.kangle.kardleaf.data.model.AppConfig
import com.kangle.kardleaf.data.model.HistoryCleanupPreview
import com.kangle.kardleaf.data.model.Note
import com.kangle.kardleaf.data.model.NoteHistory
import com.kangle.kardleaf.data.model.NoteRecordSummary
import com.kangle.kardleaf.data.model.NoteRemark
import com.kangle.kardleaf.data.utils.NoteFormatUtils
import com.kangle.kardleaf.data.utils.NoteTextStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import android.os.SystemClock
import android.util.Base64
import android.util.LruCache
import java.util.Date
import java.util.Locale
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

    companion object {
        private const val MAX_TEXT_CACHE_ENTRIES = 200
        private const val NOTE_PREVIEW_CHAR_LIMIT = 200
        private const val LOCAL_WRITE_OBSERVER_COOLDOWN_MS = 1500L
        private const val STARTUP_PERF_TRACE_TAG = "KardLeafStartupPerf"
        private const val NOTE_THUMBNAIL_CACHE_MAX_BYTES = 12 * 1024 * 1024
        private const val YAML_TAG_TRACE_TAG = "KardLeafYamlTags"
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

    private val cacheMutex = Mutex()
    private val refreshMutex = Mutex()
    private val indexingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contentCache = LinkedHashMap<String, CachedText>(64, 0.75f, true)
    private val fileSignatures = mutableMapOf<String, FileSignature>()
    private val flowEmissionCounts = ConcurrentHashMap<String, Int>()
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
        android.util.Log.d(STARTUP_PERF_TRACE_TAG, message)
    }

    private fun logFlowEmission(name: String, size: Int, elapsedMs: Long) {
        val count = flowEmissionCounts.merge(name, 1) { old, one -> old + one } ?: 1
        if (count <= 20 || elapsedMs >= 16L) {
            logStartupPerf(
                "repository flow $name emit#$count size=$size mapElapsed=${elapsedMs}ms thread=${Thread.currentThread().name}",
            )
        }
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
                android.util.Log.w("RoomNoteRepository", "Could not take persistable permission: ${e.message}")
            }

            val docFile = DocumentFile.fromTreeUri(context, uri)
            val canRead = docFile?.canRead() == true
            if (docFile == null || !canRead) {
                android.util.Log.e("RoomNoteRepository", "Root folder is not readable or null: $uriString")
                return
            }

            rootDir = docFile
            rootTreeUri = uri
            val rootName = docFile.name

            appConfig = metadataManager.loadConfig(docFile)

            val dbCount = noteDao.countAllNotes()
            val willScan = scanImmediately || dbCount == 0
            if (willScan) {
                refreshNotes()
            }
        } catch (e: Exception) {
            android.util.Log.e("RoomNoteRepository", "Error setting root folder: $uriString", e)
        }
    }

    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllActiveNotes().map { entities ->
            val startMs = SystemClock.elapsedRealtime()
            val result = entities.map { it.toNote() }
            logFlowEmission("activeNotes", result.size, SystemClock.elapsedRealtime() - startMs)
            result
        }
    }

    override fun getAllNotesWithArchive(): Flow<List<Note>> {
        return noteDao.getAllNotesWithArchive().map { entities ->
            val startMs = SystemClock.elapsedRealtime()
            val result = entities.map { it.toNote() }
            logFlowEmission("allNotesWithArchive", result.size, SystemClock.elapsedRealtime() - startMs)
            result
        }
    }

    override fun getFavoriteNotes(): Flow<List<Note>> {
        return noteDao.getFavoriteNotes().map { entities ->
            entities.map { it.toNote() }
        }
    }

    fun getTrashedNotes(): Flow<List<Note>> {
        return noteDao.getTrashedNotes().map { entities ->
            entities.map { it.toNote() }
        }
    }

    fun getArchivedNotes(): Flow<List<Note>> {
        return noteDao.getArchivedNotes().map { entities ->
            entities.map { it.toNote() }
        }
    }

    fun getNotesByFolder(folder: String): Flow<List<Note>> {
        return noteDao.getNotesByFolder(folder).map { entities ->
            val startMs = SystemClock.elapsedRealtime()
            val result = entities.map { it.toNote() }
            logFlowEmission("notesByFolder:$folder", result.size, SystemClock.elapsedRealtime() - startMs)
            result
        }
    }

    fun getNotesByFolderRecursive(folder: String): Flow<List<Note>> {
        val normalized = folder.trimEnd('/')
        return noteDao.getNotesByFolderRecursive(normalized, "$normalized/%").map { entities ->
            val startMs = SystemClock.elapsedRealtime()
            val result = entities.map { it.toNote() }
            logFlowEmission("notesByFolderRecursive:$normalized", result.size, SystemClock.elapsedRealtime() - startMs)
            result
        }
    }

    override fun getLabels(): Flow<List<String>> = labelDao.getAllLabels()

    private fun logYamlTagTrace(message: String) {
        android.util.Log.d(YAML_TAG_TRACE_TAG, message)
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
            entities.map { it.toNote() }
        }
    }

    suspend fun updateNoteTags(
        notePath: String,
        tags: Collection<String>,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteByPath(notePath)
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
        val rawTags = NoteFormatUtils.extractTags(rawContent)
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
        logYamlTagTrace(
            "writeYamlTags prepared path=${entity.filePath} outputTags=${NoteFormatUtils.extractTags(fullContent)} outputLen=${fullContent.length}",
        )
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer -> writer.write(fullContent) }
        } ?: return false

        lastLocalWriteElapsedMs = SystemClock.elapsedRealtime()
        updateTextCache(file, fullContent)
        val writtenLastModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        noteDao.insertNote(
            entity.copy(
                contentPreview = frontMatter.cleanContent.take(200),
                content = frontMatter.cleanContent,
                lastModifiedMs = writtenLastModified,
                yamlTags = NoteFormatUtils.tagsToStorage(normalizedTags),
            ),
        )
        fileSignatures[entity.filePath] = FileSignature(writtenLastModified, file.length())
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
            return@withContext true
        }

    /**
     * Returns the Room copy only. This is used by the editor open path so the
     * screen can be shown immediately, without blocking navigation on SAF file IO.
     * A normal getNote() call can still run afterwards to verify the file version.
     */
    suspend fun getCachedNote(id: String): Note? {
        return withContext(Dispatchers.IO) {
            // Do not read the full `content` column here. Very large notes can exceed
            // Android CursorWindow's per-row limit when Room executes SELECT *.
            noteDao.getNoteShellByPath(id)?.toNote()
        }
    }

    suspend fun getNoteForEditor(id: String): Note? {
        return withContext(Dispatchers.IO) {
            // Editor opening should load metadata from Room and full text from the
            // markdown file, instead of selecting a huge cached content column.
            val entity = noteDao.getNoteShellByPath(id) ?: return@withContext null
            val file = findNoteDocument(entity) ?: findDocumentByPath(id) ?: run {
                return@withContext entity.toNote()
            }
            readNoteFromFileForEditor(entity, file).toNote()
        }
    }

    override suspend fun getNote(id: String): Note? {
        return withContext(Dispatchers.IO) {
            // Do not SELECT * here. This method is also used by editor side panels
            // and external-open paths, and large cached content can exceed CursorWindow.
            val entity = noteDao.getNoteShellByPath(id) ?: return@withContext null
            val file = findNoteDocument(entity) ?: findDocumentByPath(id) ?: run {
                return@withContext entity.toNote()
            }
            val fileModified = file.lastModified()
            val updated = readNoteFromFileForEditor(entity, file).copy(
                lastModifiedMs = fileModified.takeIf { it > 0L } ?: System.currentTimeMillis(),
            )
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

            var baseTitle = note.title.trim().ifEmpty { "Untitled" }
            var finalFileName = "$baseTitle.md"

            var conflict = false
            var targetFileDoc = targetDir.findFile(finalFileName)

            if (targetFileDoc != null) {
                if (oldFile != null && oldFile.name == finalFileName) {
                    conflict = false
                } else {
                    conflict = true
                }
            }

            var counter = 1
            var finalTitle = baseTitle
            while (conflict) {
                finalTitle = "$baseTitle ($counter)"
                finalFileName = "$finalTitle.md"
                targetFileDoc = targetDir.findFile(finalFileName)
                if (targetFileDoc == null) conflict = false else counter++
            }

            val filePath = joinPath(folderName, finalFileName)
            logYamlTagTrace(
                "saveNote start targetPath=$filePath oldFile=${oldFile?.path} noteTitle=${note.title} noteTags=${note.tags} noteContentLen=${note.content.length} saveHistory=$saveHistory",
            )
            var previousPath: String? = null
            var previousRawContent: String? = null
            var previousDbTags: List<String> = emptyList()

            if (oldFile != null) {
                val oldName = oldFile.name
                val oldParentName = normalizeFolderPath(oldFile.parent.orEmpty())
                previousPath = joinPath(oldParentName, oldName)
                val previousEntity = noteDao.getNoteByPath(previousPath)
                previousDbTags = NoteFormatUtils.tagsFromStorage(previousEntity?.yamlTags)
                logYamlTagTrace(
                    "saveNote oldEntity path=$previousPath exists=${previousEntity != null} oldDbTags=${previousEntity?.yamlTags?.let { NoteFormatUtils.tagsFromStorage(it) }.orEmpty()} oldTitle=${previousEntity?.title}",
                )

                if (saveHistory && previousEntity != null && hasTitleOrContentChanged(previousEntity, note)) {
                    saveHistorySnapshot(previousEntity)
                }

                val folderDoc = findFolder(root, oldParentName)
                val trashDoc = getTrashRoot(root, create = false)?.let { findFolder(it, oldParentName) }

                var oldFileDoc: DocumentFile? = folderDoc?.findFile(oldName)
                    ?: folderDoc?.findFile("Pinned")?.findFile(oldName)
                    ?: folderDoc?.findFile("Archived")?.findFile(oldName)
                    ?: trashDoc?.findFile(oldName)

                previousRawContent = oldFileDoc?.let { readText(it) }
                logYamlTagTrace(
                    "saveNote oldFileDoc path=$previousPath found=${oldFileDoc != null} oldRawLen=${previousRawContent?.length ?: -1} oldRawTags=${previousRawContent?.let { NoteFormatUtils.extractTags(it) }.orEmpty()} targetExistsBeforeCreate=${targetFileDoc != null}",
                )

                if (oldFileDoc != null && oldFileDoc.uri != targetFileDoc?.uri) {
                    if (oldName != finalFileName || oldFileDoc.parentFile?.name != targetDir.name) {
                        logYamlTagTrace("saveNote deleting old file oldPath=${joinPath(oldParentName, oldName)} newPath=$filePath oldName=$oldName finalFileName=$finalFileName")
                        oldFileDoc.delete()
                        noteDao.deleteNoteByPath(joinPath(oldParentName, oldName))
                    }
                }
            }

            if (targetFileDoc == null) {
                targetFileDoc = targetDir.createFile("text/markdown", finalFileName)
            }

            val targetRawContent = targetFileDoc?.let { readText(it) }
            val existingContent =
                if (previousPath != null && previousPath != filePath && !previousRawContent.isNullOrBlank()) {
                    previousRawContent
                } else {
                    targetRawContent ?: previousRawContent
                }
            val preservedTags =
                if (note.tags.isEmpty()) {
                    NoteFormatUtils.extractTags(existingContent.orEmpty()).ifEmpty { previousDbTags }
                } else {
                    note.tags
                }
            val noteForWrite = if (preservedTags == note.tags) note else note.copy(tags = preservedTags)
            logYamlTagTrace(
                "saveNote contentSource targetPath=$filePath previousPath=$previousPath targetLen=${targetRawContent?.length ?: -1} targetTags=${targetRawContent?.let { NoteFormatUtils.extractTags(it) }.orEmpty()} existingLen=${existingContent?.length ?: -1} existingTags=${existingContent?.let { NoteFormatUtils.extractTags(it) }.orEmpty()} previousLen=${previousRawContent?.length ?: -1} previousTags=${previousRawContent?.let { NoteFormatUtils.extractTags(it) }.orEmpty()} previousDbTags=$previousDbTags preservedTags=$preservedTags",
            )
            val fullContent = NoteFormatUtils.constructFileContent(noteForWrite, existingContent)
            val noteRecordId = NoteFormatUtils.extractKardLeafId(fullContent)?.takeIf { it.isNotBlank() } ?: filePath
            val outputTags = NoteFormatUtils.extractTags(fullContent)
            val writtenYamlTags = NoteFormatUtils.tagsToStorage(outputTags)
            logYamlTagTrace(
                "saveNote output targetPath=$filePath recordId=$noteRecordId outputTags=$outputTags writtenYamlTags=${NoteFormatUtils.tagsFromStorage(writtenYamlTags)} outputLen=${fullContent.length}",
            )

            val writtenLastModified =
                targetFileDoc?.let { doc ->
                    context.contentResolver.openOutputStream(doc.uri, "wt")?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(fullContent)
                        }
                    }
                    lastLocalWriteElapsedMs = SystemClock.elapsedRealtime()
                    updateTextCache(doc, fullContent)
                    doc.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                } ?: System.currentTimeMillis()

            val createdAtMs =
                previousPath?.let { noteDao.getNoteByPath(it)?.createdAtMs }
                    ?: noteDao.getNoteByPath(filePath)?.createdAtMs
                    ?: note.createdAt.time
            val entity =
                NoteEntity(
                    filePath = filePath,
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
            logYamlTagTrace(
                "saveNote dbInserted path=$filePath dbTags=${NoteFormatUtils.tagsFromStorage(entity.yamlTags)} createdAtMs=$createdAtMs lastModifiedMs=$writtenLastModified",
            )
            targetFileDoc?.let { doc ->
                fileSignatures[filePath] = FileSignature(writtenLastModified, doc.length())
            }
            syncNoteRecordsWithResolvedId(filePath, noteRecordId)
            previousPath?.takeIf { it != filePath }?.let {
                logYamlTagTrace("saveNote pathChanged oldPath=$it newPath=$filePath recordId=$noteRecordId outputTags=$outputTags")
                fileSignatures.remove(it)
                prefsManager.replacePinnedNotePath(it, filePath)
                prefsManager.replaceFavoriteNotePath(it, filePath)
                if (noteRecordId == filePath) {
                    noteHistoryDao.replaceNoteId(it, filePath)
                    noteRemarkDao.deleteByNoteId(filePath)
                    noteRemarkDao.replaceNoteId(it, filePath)
                    noteHistoryDao.pruneOldVersions(filePath, prefsManager.getHistoryVersionLimit())
                } else {
                    syncNoteRecordsWithResolvedId(it, noteRecordId)
                    noteHistoryDao.pruneOldVersions(noteRecordId, prefsManager.getHistoryVersionLimit())
                }
            }
            prefsManager.setNotePinned(filePath, note.isPinned && !note.isArchived && !note.isTrashed)
            prefsManager.setNoteFavorite(filePath, note.isFavorite && !note.isTrashed)

            return@withContext filePath
        }

    override fun getNoteHistory(noteId: String): Flow<List<NoteHistory>> = flow {
        val recordId = resolveNoteRecordId(noteId)
        emitAll(
            noteHistoryDao.getHistory(recordId).map { histories ->
                histories.map { it.toNoteHistory() }
            },
        )
    }

    override fun searchHistoryPreview(query: String): Flow<List<NoteHistory>> {
        return noteHistoryDao.searchHistoryPreview(query).map { histories ->
            histories.map { it.toNoteHistory() }
        }
    }

    override fun searchNoteIds(query: String): Flow<List<String>> = noteDao.searchNoteIds(query)

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
            val current = noteDao.getNoteByPath(noteId) ?: return@withContext ""
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
            val file = findNoteDocument(entity) ?: findDocumentByPath(noteId) ?: return@withContext emptyList()
            NoteFormatUtils.extractFrontMatterProperties(readText(file))
        }


    suspend fun getNoteTextStatsForProperties(noteId: String): NoteTextStats =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteShellByPath(noteId) ?: return@withContext NoteTextStats()
            val file = findNoteDocument(entity) ?: findDocumentByPath(noteId)
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
            android.util.Log.e("RoomNoteRepository", "Exception counting markdown text stats.", e)
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
            val entity = noteDao.getNoteByPath(noteId) ?: return@withContext false
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
            moveNoteToSystemFolder(id, isArchive = false)
            prefsManager.setNotePinned(id, false)
            prefsManager.setNoteFavorite(id, false)
            noteDao.trashNote(id, System.currentTimeMillis())
        }

    override suspend fun deleteNotes(noteIds: List<String>) =
        withContext(Dispatchers.IO) {
            val entities = noteDao.getNotesByPaths(noteIds)
            moveNoteEntitiesToSystemFolder(entities, isArchive = false)
            entities.forEach { prefsManager.setNotePinned(it.filePath, false) }
            entities.forEach { prefsManager.setNoteFavorite(it.filePath, false) }
            noteDao.trashNotes(entities.map { it.filePath }, System.currentTimeMillis())
        }

    override suspend fun archiveNote(id: String) =
        withContext(Dispatchers.IO) {
            moveNoteToSystemFolder(id, isArchive = true)
            prefsManager.setNotePinned(id, false)
            noteDao.archiveNote(id)
        }

    override suspend fun archiveNotes(noteIds: List<String>) =
        withContext(Dispatchers.IO) {
            val entities = noteDao.getNotesByPaths(noteIds)
            moveNoteEntitiesToSystemFolder(entities, isArchive = true)
            entities.forEach { prefsManager.setNotePinned(it.filePath, false) }
            noteDao.archiveNotes(entities.map { it.filePath })
        }

    override suspend fun restoreNote(id: String) =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteByPath(id) ?: return@withContext
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

            if (sourceFile != null) {
                val targetFolder = getOrCreateFolder(root, folder)
                val content = readText(sourceFile)
                val newFile = targetFolder?.createFile("text/markdown", fileName)
                newFile?.let { nf ->
                    context.contentResolver.openOutputStream(nf.uri)?.use { os ->
                        OutputStreamWriter(os).use { it.write(content) }
                    }
                    sourceFile.delete()
                }
            }

            noteDao.restoreNote(id)
        }

    override suspend fun togglePinStatus(
        noteIds: List<String>,
        isPinned: Boolean,
    ) = withContext(Dispatchers.IO) {
        val entities = noteDao.getNotesByPaths(noteIds).filter { it.isPinned != isPinned }
        entities.forEach { entity ->
            prefsManager.setNotePinned(entity.filePath, isPinned)
        }
        noteDao.updatePinStatuses(entities.map { it.filePath }, isPinned)
    }

    override suspend fun toggleFavoriteStatus(
        noteIds: List<String>,
        isFavorite: Boolean,
    ) = withContext(Dispatchers.IO) {
        val entities = noteDao.getNotesByPaths(noteIds).filter { it.isFavorite != isFavorite && !it.isTrashed }
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

            val effectiveRoot =
                when {
                    isTrashed -> getTrashRoot(root, create = false)
                    else -> root
                }

            val sourceFolderDoc = effectiveRoot?.let { rootDoc -> findFolder(rootDoc, sourceFolder) }
            var sourceFile = sourceFolderDoc?.findFile(fileName)
            if (sourceFile == null && !isTrashed && !isArchived) {
                sourceFile = sourceFolderDoc?.findFile("Pinned")?.findFile(fileName)
            } else if (isArchived && !isTrashed) {
                sourceFile = sourceFolderDoc?.findFile("Archived")?.findFile(fileName)
            }

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
                    if (sourceFolder == targetFolder && !isArchived) {
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
                    newFile?.let { nf ->
                        context.contentResolver.openOutputStream(nf.uri)?.use { os ->
                            OutputStreamWriter(os).use { it.write(content) }
                        }
                        sourceFile.delete()
                    }

                    val oldPath = joinPath(sourceFolder, fileName)
                    val newPath = joinPath(targetFolder, resolvedFileName)
                    val entity = noteDao.getNoteByPath(oldPath)
                    if (entity != null) {
                        noteDao.deleteNoteByPath(oldPath)
                        noteDao.insertNote(entity.copy(filePath = newPath, folder = targetFolder, fileName = resolvedFileName))
                        movedPaths += MovedNotePath(oldPath = oldPath, newPath = newPath)
                        if (isPinned) {
                            prefsManager.replacePinnedNotePath(oldPath, newPath)
                        }
                        if (entity.isFavorite) {
                            prefsManager.replaceFavoriteNotePath(oldPath, newPath)
                        }
                        noteHistoryDao.replaceNoteId(oldPath, newPath)
                        noteRemarkDao.deleteByNoteId(newPath)
                        noteRemarkDao.replaceNoteId(oldPath, newPath)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RoomNoteRepository", "Failed to move note: $fileName", e)
                }
            }
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

        val existing = noteDao.getNoteByPath(path)
        val file =
            preferredFile?.takeIf { it.isFile }
                ?: existing?.let { findNoteDocument(it) }
                ?: findDocumentByPath(path)

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
        val parsedYamlTags = NoteFormatUtils.extractTags(rawContent)
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

    private suspend fun refreshNotesInternal(forceReloadIfMetadataUnchanged: Boolean) =
        withContext(Dispatchers.IO) {
            val refreshStartMs = SystemClock.elapsedRealtime()
            val root = rootDir ?: run {
                logStartupPerf("refreshNotesInternal skip root=null force=$forceReloadIfMetadataUnchanged")
                return@withContext
            }
            if (!refreshMutex.tryLock()) {
                logStartupPerf("refreshNotesInternal skip busy force=$forceReloadIfMetadataUnchanged")
                return@withContext
            }

            var indexingContinuesInBackground = false
            try {
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
                    android.util.Log.e("RoomNoteRepository", "Error scanning root structure", e)
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
                    android.util.Log.e("RoomNoteRepository", "Error syncing labels", e)
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
                android.util.Log.e("RoomNoteRepository", "Critical error in refreshNotes", e)
            } finally {
                if (!indexingContinuesInBackground) {
                    _isIndexing.value = false
                }
                refreshMutex.unlock()
                logStartupPerf("refreshNotesInternal done total=${SystemClock.elapsedRealtime() - refreshStartMs}ms")
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
    ) {
        targets.chunked(25).forEachIndexed { batchIndex, batch ->
            val batchStartMs = SystemClock.elapsedRealtime()
            val notesToUpsert =
                batch.mapNotNull { (path, meta) ->
                    try {
                        val rawContent = readText(meta.file, bypassCache = bypassCache)
                        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
                        val parsedYamlTags = NoteFormatUtils.extractTags(rawContent)
                        val existingYamlTags = existing[path]?.yamlTags?.let { NoteFormatUtils.tagsFromStorage(it) }.orEmpty()
                        if (parsedYamlTags.isNotEmpty() || existingYamlTags.isNotEmpty()) {
                            logYamlTagTrace("indexNoteContent path=$path parsedTags=$parsedYamlTags existingDbTags=$existingYamlTags rawLen=${rawContent.length} bypassCache=$bypassCache")
                        }
                        NoteEntity(
                            filePath = path,
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
                        android.util.Log.e("RoomNoteRepository", "Error indexing file: $path", e)
                        null
                    }
                }
            if (notesToUpsert.isNotEmpty()) {
                noteDao.insertNotes(notesToUpsert)
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
        val rootDocumentId =
            runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
                .getOrNull()
                ?: return false

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
            result
        }.getOrElse { emptyList() }
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
        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        if (documentId == rootDocumentId) return null
        if (!documentId.startsWith("$rootDocumentId/")) return null
        val relativePath = normalizeFolderPath(documentId.removePrefix("$rootDocumentId/"))
        return relativePath.takeIf { isMarkdownTextFile(it.substringAfterLast("/")) }
    }

    private fun findDocumentByPath(path: String): DocumentFile? {
        val root = rootDir ?: return null
        val normalized = normalizeFolderPath(path)
        val folder = normalized.substringBeforeLast("/", missingDelimiterValue = "")
        val fileName = normalized.substringAfterLast("/")
        return findFolder(root, folder)?.findFile(fileName)?.takeIf { it.isFile }
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
                val listed = dir.listFiles()
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
                android.util.Log.e("RoomNoteRepository", "Error scanning folder: ${dir.uri}", e)
            }
        }

        processFiles(folder, isPinned = false, isArchiveTarget = isArchived)

        if (!isArchived && !isTrashed) {
            try {
                folder.findFile("Archived")?.let {
                    processFiles(it, isPinned = false, isArchiveTarget = true)
                }
            } catch (e: Exception) {
                android.util.Log.e("RoomNoteRepository", "Error scanning subfolders in $folderName", e)
            }
        }

        val children = folder.listFiles()
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
    ) {
        val entity = noteDao.getNoteByPath(id) ?: return
        moveNoteEntitiesToSystemFolder(listOf(entity), isArchive)
    }

    private suspend fun moveNoteEntitiesToSystemFolder(
        entities: List<NoteEntity>,
        isArchive: Boolean
    ) {
        val root = rootDir ?: return
        entities.forEach { entity ->
            val folder = entity.folder
            val fileName = entity.fileName

            var sourceFile =
                findFolder(root, folder)?.findFile(fileName)
                    ?: findFolder(root, folder)?.findFile("Pinned")?.findFile(fileName)
                    ?: findFolder(root, folder)?.findFile("Archived")?.findFile(fileName)
                    ?: getTrashRoot(root, create = false)?.let { findFolder(it, folder) }?.findFile(fileName)

            if (sourceFile != null) {
                val targetLabelFolder = if (isArchive) {
                    val base = getOrCreateFolder(root, folder)
                    base?.findFile("Archived") ?: base?.createDirectory("Archived")
                } else {
                    val sysRoot = getTrashRoot(root, create = true)
                    sysRoot?.let { getOrCreateFolder(it, folder) }
                }

                if (targetLabelFolder != null) {
                    val content = readText(sourceFile)
                    val newFile = targetLabelFolder.createFile("text/markdown", fileName)
                    newFile?.let { nf ->
                        context.contentResolver.openOutputStream(nf.uri)?.use { os ->
                            OutputStreamWriter(os).use { it.write(content) }
                        }
                        sourceFile.delete()
                    }
                }
            }
        }
    }

    private suspend fun readText(
        file: DocumentFile,
        bypassCache: Boolean = false,
    ): String =
        withContext(Dispatchers.IO) {
            val pathKey = file.uri.toString()
            val lastModified = file.lastModified()
            val length = file.length()

            if (!bypassCache) {
                cacheMutex.withLock {
                    val cached = contentCache[pathKey]
                    if (cached != null && cached.lastModified == lastModified && cached.length == length) {
                        return@withContext cached.text
                    }
                }
            }

            try {
                context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    val text = BufferedReader(inputStream.reader()).use { it.readText() }
                    cacheText(pathKey, lastModified, length, text)
                    text
                } ?: ""
            } catch (e: Exception) {
                android.util.Log.e("RoomNoteRepository", "Exception reading markdown.", e)
                ""
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
    ): NoteEntity {
        val rawContent = readText(file)
        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
        return entity.copy(
            title = file.name?.substringBeforeLast(".") ?: entity.title,
            contentPreview = frontMatter.cleanContent.take(200),
            content = frontMatter.cleanContent,
            lastModifiedMs = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
            color = 0xFFFFFFFF,
            reminder = frontMatter.reminder,
            firstImageReference = extractFirstImageReference(frontMatter.cleanContent).orEmpty(),
            yamlTags = NoteFormatUtils.tagsToStorage(NoteFormatUtils.extractTags(rawContent)),
        )
    }

    private suspend fun readLatestNoteFromFile(
        entity: NoteEntity,
        file: DocumentFile,
        fileModified: Long,
    ): NoteEntity {
        val updated = readNoteFromFileForEditor(entity, file).copy(
            lastModifiedMs = fileModified.takeIf { it > 0L } ?: System.currentTimeMillis(),
        )
        noteDao.insertNote(updated)
        return updated
    }

    suspend fun resolveMarkdownImages(
        markdown: String,
        currentFolder: String,
    ): String =
        withContext(Dispatchers.IO) {
            if (markdown.isBlank()) return@withContext markdown

            val obsidianImageRegex = Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]""")
            val standardImageRegex = Regex("""!\[([^]]*)]\((?!https?://|data:|file:)([^)]+)\)""", RegexOption.IGNORE_CASE)

            val withObsidianImages =
                obsidianImageRegex.replace(markdown) { match ->
                    val reference = match.groupValues[1].trim()
                    resolveImageDataUri(currentFolder, reference)?.let { dataUri ->
                        "![]($dataUri)"
                    } ?: match.value
                }

            standardImageRegex.replace(withObsidianImages) { match ->
                val alt = match.groupValues[1]
                val reference = match.groupValues[2].trim().trim('"', '\'')
                resolveImageDataUri(currentFolder, reference)?.let { dataUri ->
                    "![$alt]($dataUri)"
                } ?: match.value
            }
        }

    suspend fun importImage(
        sourceUri: Uri,
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
            val sourceName = queryDisplayName(sourceUri).ifBlank { "image" }
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
            while (targetFolder.findFile(targetName) != null) {
                targetName = "$baseName-$index.$extension"
                index++
            }

            val mimeType = imageMimeType(targetName) ?: "image/$extension"
            val targetFile = targetFolder.createFile(mimeType, targetName) ?: return@withContext ""
            val copied =
                runCatching {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                            input.copyTo(output)
                        }
                    }
                }.isSuccess
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
            "![[${reference}]]"
        }

    private fun extractFirstImageReference(markdown: String): String? {
        if (markdown.isBlank()) return null
        val obsidian = Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]""")
        val standard = Regex("""!\[[^]]*]\((?!https?://|data:|file:)([^)]+)\)""", RegexOption.IGNORE_CASE)
        return (obsidian.findAll(markdown).map { it.range.first to it.groupValues[1].trim() } +
            standard.findAll(markdown).map { it.range.first to it.groupValues[1].trim().trim('"', '\'') })
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
    private fun rewriteRelativeImageRefs(markdown: String, fromFolder: String, toFolder: String): String {
        if (fromFolder == toFolder) return markdown
        val obsidian = Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]""")
        val standard = Regex("""!\[([^]]*)]\((?!https?://|data:|file:)([^)]+)\)""", RegexOption.IGNORE_CASE)
        val r1 = obsidian.replace(markdown) { m ->
            val ref = m.groupValues[1].trim()
            val realPath = normalizePath(joinPath(fromFolder, ref))
            "![[${relativePath(toFolder, realPath)}]]"
        }
        return standard.replace(r1) { m ->
            val alt = m.groupValues[1]
            val ref = m.groupValues[2].trim().trim('"', '\'')
            val realPath = normalizePath(joinPath(fromFolder, ref))
            "![${alt}](${relativePath(toFolder, realPath)})"
        }
    }

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
            val reference = note.firstImageReference?.takeIf { it.isNotBlank() }
                ?: extractFirstImageReference(note.content)
                ?: return@withContext null
            val cacheKey = "${note.file.path}|${note.lastModified.time}|$reference"
            val cached = synchronized(noteThumbnailCache) { noteThumbnailCache.get(cacheKey) }
            if (cached != null) {
                if (!cached.isRecycled) {
                    return@withContext cached
                }
                synchronized(noteThumbnailCache) { noteThumbnailCache.remove(cacheKey) }
            }

            val imageFile = findImageFile(note.folder, reference) ?: return@withContext null
            val bitmap = decodeSampledBitmap(imageFile, maxWidthPx = 240, maxHeightPx = 200) ?: return@withContext null
            synchronized(noteThumbnailCache) {
                noteThumbnailCache.put(cacheKey, bitmap)
            }
            bitmap
        }

    suspend fun resolveNoteImages(
        markdown: String,
        currentFolder: String,
    ): List<NoteImage> =
        withContext(Dispatchers.IO) {
            if (markdown.isBlank()) return@withContext emptyList()

            val found = linkedSetOf<String>()
            Regex("""!\[\[([^|\]]+)(?:\|[^\]]*)?]]""")
                .findAll(markdown)
                .forEach { found.add(it.groupValues[1].trim()) }
            Regex("""!\[[^]]*]\((?!https?://|data:|file:)([^)]+)\)""", RegexOption.IGNORE_CASE)
                .findAll(markdown)
                .forEach { found.add(it.groupValues[1].trim().trim('"', '\'')) }

            found.mapNotNull { reference ->
                resolveImageDataUri(currentFolder, reference)?.let { dataUri ->
                    NoteImage(reference = reference, dataUri = dataUri)
                }
            }
        }

    private fun relativeFolderFromTreeUri(uri: Uri): String? {
        val treeUri = rootTreeUri ?: return null
        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
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
    ): String? {
        val imageFile = findImageFile(currentFolder, reference) ?: return null
        val mimeType = imageMimeType(imageFile.name.orEmpty()) ?: return null
        val bytes =
            runCatching {
                context.contentResolver.openInputStream(imageFile.uri)?.use { it.readBytes() }
            }.getOrNull() ?: return null

        return "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private fun findImageFile(
        currentFolder: String,
        reference: String,
    ): DocumentFile? {
        val root = rootDir ?: return null
        val cleanRef = normalizeFolderPath(Uri.decode(reference).substringBefore("#"))
        if (cleanRef.isBlank()) return null

        val current = normalizeFolderPath(currentFolder)
        val candidates =
            listOf(
                joinPath(current, cleanRef),
                cleanRef,
                joinPath(current, "attachments/$cleanRef"),
                joinPath(current, "附件/$cleanRef"),
                "attachments/$cleanRef",
                "附件/$cleanRef",
            )
                .map(::normalizePath)
                .distinct()

        return candidates.firstNotNullOfOrNull { path ->
            val parent = path.substringBeforeLast("/", missingDelimiterValue = "")
            val name = path.substringAfterLast("/")
            findFolder(root, parent)?.findFile(name)?.takeIf { it.isFile }
        }
    }

    private fun decodeSampledBitmap(
        imageFile: DocumentFile,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxWidthPx, maxHeightPx)
        }
        return context.contentResolver.openInputStream(imageFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
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
            val root = rootDir ?: return@withContext
            val deletedDir = getTrashRoot(root, create = false)

            deletedDir?.listFiles()?.forEach {
                it.delete()
            }

            noteDao.deleteAllTrashed()
        }

    override suspend fun cleanupExpiredTrash(olderThanDays: Int) =
        withContext(Dispatchers.IO) {
            if (olderThanDays <= 0) return@withContext
            val cutoffMs = System.currentTimeMillis() - olderThanDays * 24L * 60L * 60L * 1000L
            val expiredPaths = noteDao.getTrashedNotePathsBefore(cutoffMs)
            if (expiredPaths.isEmpty()) return@withContext
            val expiredEntities = noteDao.getNotesByPaths(expiredPaths)
            expiredEntities.forEach { entity ->
                findNoteDocument(entity)?.delete()
                prefsManager.setNotePinned(entity.filePath, false)
                prefsManager.setNoteFavorite(entity.filePath, false)
            }
            noteDao.deleteNotesByPaths(expiredPaths)
        }

    private suspend fun resolveNoteRecordId(notePath: String): String {
        if (notePath.isBlank()) return notePath
        val entity = noteDao.getNoteShellByPath(notePath) ?: return notePath
        val rawContent = (findNoteDocument(entity) ?: findDocumentByPath(notePath))?.let { readText(it) }
        val kardLeafId = rawContent?.let { NoteFormatUtils.extractKardLeafId(it) }?.takeIf { it.isNotBlank() }
        val recordId = kardLeafId ?: notePath
        syncNoteRecordsWithResolvedId(notePath, recordId)
        return recordId
    }

    private suspend fun resolveOrCreateNoteRecordIdForRemark(notePath: String): String {
        if (notePath.isBlank()) return notePath
        val entity = noteDao.getNoteShellByPath(notePath) ?: return notePath
        val file = findNoteDocument(entity) ?: findDocumentByPath(notePath) ?: return notePath
        val rawContent = readText(file)
        NoteFormatUtils.extractKardLeafId(rawContent)?.takeIf { it.isNotBlank() }?.let { recordId ->
            syncNoteRecordsWithResolvedId(notePath, recordId)
            return recordId
        }

        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)
        val noteForFrontMatter = entity.toNote().copy(content = frontMatter.cleanContent)
        val fullContent = NoteFormatUtils.constructFileContent(noteForFrontMatter, rawContent)
        val recordId = NoteFormatUtils.extractKardLeafId(fullContent)?.takeIf { it.isNotBlank() } ?: notePath
        if (recordId == notePath) return notePath

        context.contentResolver.openOutputStream(file.uri, "wt")?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(fullContent)
            }
        }
        lastLocalWriteElapsedMs = SystemClock.elapsedRealtime()
        updateTextCache(file, fullContent)

        val writtenLastModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        val updatedEntity = readNoteFromFileForEditor(entity, file).copy(lastModifiedMs = writtenLastModified)
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
        )
    }

    private suspend fun saveHistorySnapshot(entity: NoteEntity) {
        val recordId = resolveNoteRecordId(entity.filePath)
        noteHistoryDao.insert(
            NoteHistoryEntity(
                noteId = recordId,
                title = entity.title,
                content = entity.content,
                savedAtMs = System.currentTimeMillis(),
            ),
        )
        noteHistoryDao.pruneOldVersions(recordId, prefsManager.getHistoryVersionLimit())
    }

    private fun hasTitleOrContentChanged(
        entity: NoteEntity,
        note: Note,
    ): Boolean {
        return entity.title != note.title || entity.content != note.content
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

    private fun findNoteDocument(entity: NoteEntity): DocumentFile? {
        val root = rootDir ?: return null
        val baseFolder =
            if (entity.isTrashed) {
                getTrashRoot(root, create = false)?.let { findFolder(it, entity.folder) }
            } else {
                findFolder(root, entity.folder)
            } ?: return null

        return when {
            entity.isTrashed -> baseFolder.findFile(entity.fileName)
            entity.isArchived -> baseFolder.findFile("Archived")?.findFile(entity.fileName) ?: baseFolder.findFile(entity.fileName)
            entity.isPinned -> baseFolder.findFile("Pinned")?.findFile(entity.fileName) ?: baseFolder.findFile(entity.fileName)
            else -> baseFolder.findFile(entity.fileName)
        }
    }

    private fun findFolder(
        root: DocumentFile,
        path: String,
    ): DocumentFile? {
        val normalized = normalizeFolderPath(path)
        if (normalized.isBlank()) return root
        var current: DocumentFile = root
        normalized.split("/").forEach { segment ->
            current = current.findFile(segment)?.takeIf { it.isDirectory } ?: return null
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
            .filter { it.isNotBlank() && it != "Unknown" && it != "." }
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
