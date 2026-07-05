package com.kangle.kardleaf.data.sync

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.kangle.kardleaf.data.repository.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

class WebDavCloudSyncManager(
    private val context: Context,
    private val prefsManager: PrefsManager,
) {
    private val appContext = context.applicationContext
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun previewSync(): SyncPreview = withContext(Dispatchers.IO) {
        val settings = prefsManager.getWebDavSettings()
        validateSettings(settings)
        val service = WebDavSyncService(settings, client)
        service.ensureRemoteWorkspace()
        buildPreview(settings, service)
    }

    suspend fun sync(
        preview: SyncPreview? = null,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: (suspend (SyncProgress) -> Unit)? = null,
    ): SyncResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = prefsManager.getWebDavSettings()
            validateSettings(settings)
            val service = WebDavSyncService(settings, client)
            service.ensureRemoteWorkspace()
            val actualPreview = preview ?: buildPreview(settings, service)
            executePreview(
                service = service,
                settings = settings,
                preview = actualPreview,
                resolutions = resolutions,
                includeUploads = true,
                includeDownloads = true,
                onProgress = onProgress,
            )
        }
    }

    suspend fun upload(): String = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = prefsManager.getWebDavSettings()
            validateSettings(settings)
            val service = WebDavSyncService(settings, client)
            service.ensureRemoteWorkspace()
            val result = executePreview(
                service = service,
                settings = settings,
                preview = buildPreview(settings, service),
                resolutions = emptyMap(),
                includeUploads = true,
                includeDownloads = false,
                onProgress = null,
            )
            if (result.skippedConflictCount > 0) {
                error("WebDAV 自动上传发现冲突，请到设置页处理")
            }
            result.message
        }
    }

    suspend fun download(): String = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = prefsManager.getWebDavSettings()
            validateSettings(settings)
            val service = WebDavSyncService(settings, client)
            service.ensureRemoteWorkspace()
            val result = executePreview(
                service = service,
                settings = settings,
                preview = buildPreview(settings, service),
                resolutions = emptyMap(),
                includeUploads = false,
                includeDownloads = true,
                onProgress = null,
            )
            result.message
        }
    }

    suspend fun readRemoteState(): RemoteFileState? = withContext(Dispatchers.IO) {
        val settings = prefsManager.getWebDavSettings()
        validateSettings(settings)
        val service = WebDavSyncService(settings, client)
        service.ensureRemoteWorkspace()
        RemoteFileState(service.remoteStateMarker())
    }

    suspend fun readRealtimeRemoteState(): RemoteFileState? = readRemoteState()

    suspend fun downloadRealtimeChangesOnly(): RealtimeDownloadResult = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val settings = prefsManager.getWebDavSettings()
            validateSettings(settings)
            val service = WebDavSyncService(settings, client)
            service.ensureRemoteWorkspace()
            val result = executePreview(
                service = service,
                settings = settings,
                preview = buildPreview(settings, service),
                resolutions = emptyMap(),
                includeUploads = false,
                includeDownloads = true,
                onProgress = null,
            )
            RealtimeDownloadResult(
                message = result.message,
                changedPaths = result.changedPaths,
                skippedConflictCount = result.skippedConflictCount,
            )
        }
    }

    suspend fun describeRealtimeRemoteState(): String = withContext(Dispatchers.IO) {
        val settings = prefsManager.getWebDavSettings()
        validateSettings(settings)
        val service = WebDavSyncService(settings, client)
        service.ensureRemoteWorkspace()
        val remoteCount = service.listRemoteFiles().size
        val localCount = scanLocalFiles().size
        "WebDAV 检查成功：本地 $localCount 个文件，远端 $remoteCount 个文件"
    }

    private fun buildPreview(settings: PrefsManager.WebDavSettings, service: WebDavSyncService): SyncPreview {
        val localFiles = scanLocalFiles()
        val remoteFiles = service.listRemoteFiles()
        val localMap = localFiles.associateBy { it.relativePath }
        val remoteMap = remoteFiles.associateBy { it.relativePath }
        val snapshots = parseSnapshots(prefsManager.getWebDavFileSyncSnapshot())
        val uploads = mutableListOf<String>()
        val downloads = mutableListOf<String>()
        val localNewer = mutableListOf<String>()
        val remoteNewer = mutableListOf<String>()
        val conflicts = mutableListOf<SyncConflict>()
        val knownSameSnapshots = mutableListOf<FileSyncSnapshot>()

        (localMap.keys + remoteMap.keys).toSortedSet().forEach { path ->
            val local = localMap[path]
            val remote = remoteMap[path]
            when {
                local != null && remote == null -> {
                    if (snapshots[path]?.remoteMarker.isNullOrBlank()) {
                        uploads += path
                    } else {
                        conflicts += SyncConflict(
                            relativePath = path,
                            reason = ConflictReason.REMOTE_MISSING,
                            localModifiedMs = local.lastModifiedMs,
                            remoteModifiedMs = 0L,
                        )
                    }
                }
                local == null && remote != null -> {
                    if (snapshots[path]?.localMarker.isNullOrBlank()) {
                        downloads += path
                    } else {
                        conflicts += SyncConflict(
                            relativePath = path,
                            reason = ConflictReason.LOCAL_MISSING,
                            localModifiedMs = 0L,
                            remoteModifiedMs = remote.lastModifiedMs,
                        )
                    }
                }
                local != null && remote != null -> {
                    val snapshot = snapshots[path]
                    val localMarker = local.markerValue()
                    val remoteMarker = remote.markerValue()
                    val localChanged = snapshot?.localMarker != localMarker
                    val remoteChanged = snapshot?.remoteMarker != remoteMarker

                    when {
                        snapshot != null && !localChanged && !remoteChanged -> Unit
                        snapshot == null && isProbablySame(local, remote) -> {
                            knownSameSnapshots += FileSyncSnapshot(
                                relativePath = path,
                                localMarker = localMarker,
                                remoteMarker = remoteMarker,
                            )
                        }
                        snapshot == null -> {
                            conflicts += SyncConflict(
                                relativePath = path,
                                reason = ConflictReason.NEEDS_CONFIRM,
                                localModifiedMs = local.lastModifiedMs,
                                remoteModifiedMs = remote.lastModifiedMs,
                            )
                        }
                        localChanged && remoteChanged -> {
                            conflicts += SyncConflict(
                                relativePath = path,
                                reason = ConflictReason.BOTH_CHANGED,
                                localModifiedMs = local.lastModifiedMs,
                                remoteModifiedMs = remote.lastModifiedMs,
                            )
                        }
                        localChanged -> localNewer += path
                        remoteChanged -> remoteNewer += path
                        else -> Unit
                    }
                }
            }
        }
        if (knownSameSnapshots.isNotEmpty()) {
            saveKnownSnapshots(knownSameSnapshots)
        }

        return SyncPreview(
            toUpload = uploads,
            toDownload = downloads,
            localNewer = localNewer,
            remoteNewer = remoteNewer,
            conflicts = conflicts,
            localMarker = localStateMarker(localFiles),
            remoteMarker = remoteStateMarker(remoteFiles),
            configSummary = configSummary(settings),
            generatedAtMs = System.currentTimeMillis(),
        ).also { updatePendingConflicts(it.conflicts) }
    }

    private suspend fun executePreview(
        service: WebDavSyncService,
        settings: PrefsManager.WebDavSettings,
        preview: SyncPreview,
        resolutions: Map<String, ConflictResolution>,
        includeUploads: Boolean,
        includeDownloads: Boolean,
        onProgress: (suspend (SyncProgress) -> Unit)?,
    ): SyncResult {
        val localFilesBefore = scanLocalFiles()
        val remoteFilesBefore = service.listRemoteFiles()
        if (
            preview.localMarker != localStateMarker(localFilesBefore) ||
            preview.remoteMarker != remoteStateMarker(remoteFilesBefore) ||
            preview.configSummary != configSummary(settings)
        ) {
            error("同步预览已过期，请重新生成同步预览")
        }
        val localFiles = localFilesBefore.associateBy { it.relativePath }
        val uploadPaths = if (includeUploads) preview.toUpload + preview.localNewer else emptyList()
        val downloadPaths = if (includeDownloads) preview.toDownload + preview.remoteNewer else emptyList()
        val conflictUploads = preview.conflicts
            .filter {
                resolutions[it.relativePath] == ConflictResolution.KEEP_LOCAL &&
                    includeUploads &&
                    it.reason != ConflictReason.LOCAL_MISSING
            }
            .map { it.relativePath }
        val conflictDownloads = preview.conflicts
            .filter {
                resolutions[it.relativePath] == ConflictResolution.KEEP_REMOTE &&
                    includeDownloads &&
                    it.reason != ConflictReason.REMOTE_MISSING
            }
            .map { it.relativePath }
        val skippedConflicts = preview.conflicts.size - conflictUploads.size - conflictDownloads.size
        val unappliedByMode = (if (includeUploads) 0 else preview.toUpload.size + preview.localNewer.size) +
            (if (includeDownloads) 0 else preview.toDownload.size + preview.remoteNewer.size)
        val allUploads = uploadPaths + conflictUploads
        val allDownloads = downloadPaths + conflictDownloads
        val total = allUploads.size + allDownloads.size
        var processed = 0
        var uploaded = 0
        var downloaded = 0
        val changedPaths = mutableListOf<String>()

        allUploads.distinct().forEach { path ->
            val local = localFiles[path] ?: return@forEach
            processed++
            emitProgress(onProgress, SyncProgress(total, processed, path, SyncOperation.UPLOAD))
            service.uploadFile(local.document, path)
            uploaded++
            changedPaths += path
        }
        allDownloads.distinct().forEach { path ->
            processed++
            emitProgress(onProgress, SyncProgress(total, processed, path, SyncOperation.DOWNLOAD))
            service.downloadFile(path) { input -> writeLocalFile(path, input) }
            downloaded++
            changedPaths += path
        }

        val appliedPaths = changedPaths.distinct()
        if (appliedPaths.isNotEmpty()) {
            saveAppliedSnapshots(appliedPaths, service)
            prefsManager.saveWebDavIncrementalLastUploadMs(System.currentTimeMillis())
        }
        if (skippedConflicts <= 0 && unappliedByMode <= 0) {
            val marker = remoteStateMarker(service.listRemoteFiles())
            prefsManager.saveWebDavRealtimeKnownRemoteMarker(marker)
            if (uploaded > 0) {
                prefsManager.saveWebDavRealtimeLastUploadRemoteMarker(marker)
            }
        }

        val result = SyncResult(
            uploadedCount = uploaded,
            downloadedCount = downloaded,
            skippedConflictCount = skippedConflicts.coerceAtLeast(0),
            changedPaths = changedPaths,
        )
        if (result.skippedConflictCount <= 0) updatePendingConflicts(emptyList())
        return result
    }

    private suspend fun emitProgress(
        onProgress: (suspend (SyncProgress) -> Unit)?,
        progress: SyncProgress,
    ) {
        if (onProgress == null) return
        withContext(Dispatchers.Main) {
            onProgress(progress)
        }
    }

    private fun scanLocalFiles(): List<LocalFileInfo> {
        val rootUri = prefsManager.getRootUri()?.let(Uri::parse)
            ?: error("未选择笔记库，无法同步 WebDAV")
        val root = DocumentFile.fromTreeUri(appContext, rootUri)
            ?: error("无法访问当前笔记库")
        return scanLocalFolder(root, "")
    }

    private fun scanLocalFolder(folder: DocumentFile, relativeFolder: String): List<LocalFileInfo> {
        val files = mutableListOf<LocalFileInfo>()
        folder.listFiles().forEach { child ->
            val name = child.name?.takeIf { it.isNotBlank() } ?: return@forEach
            val segment = safePathSegment(name) ?: return@forEach
            val path = listOf(relativeFolder, segment)
                .filter { it.isNotBlank() }
                .joinToString("/")
            when {
                child.isDirectory -> files += scanLocalFolder(child, path)
                child.isFile && !name.endsWith(TEMP_FILE_SUFFIX) -> {
                    files += LocalFileInfo(
                        relativePath = path,
                        document = child,
                        lastModifiedMs = child.lastModified(),
                        length = child.length(),
                        hash = hashSmallMarkdown(child, name),
                    )
                }
            }
        }
        return files
    }

    private fun writeLocalFile(relativePath: String, input: InputStream) {
        val safePath = normalizeRelativePath(relativePath)
            ?: error("远端文件路径不安全：$relativePath")
        val rootUri = prefsManager.getRootUri()?.let(Uri::parse)
            ?: error("未选择笔记库，无法写入远端文件")
        val root = DocumentFile.fromTreeUri(appContext, rootUri)
            ?: error("无法访问当前笔记库")
        val parts = safePath.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) error("远端文件路径为空")
        val fileName = parts.last()
        val folder = parts.dropLast(1).fold(root) { current, segment ->
            val existing = current.findFile(segment)
            when {
                existing == null -> current.createDirectory(segment)
                    ?: error("无法创建目录：$segment")
                existing.isDirectory -> existing
                else -> error("无法创建目录：$segment 已是文件")
            }
        }
        val tempFile = File.createTempFile("kardleaf-webdav-download-", ".tmp", appContext.cacheDir)
        try {
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            val oldTemp = folder.findFile("$fileName$TEMP_FILE_SUFFIX")
            oldTemp?.delete()
            val tempDoc = folder.createFile(guessMimeType(fileName), "$fileName$TEMP_FILE_SUFFIX")
                ?: error("无法创建临时文件：$fileName")
            appContext.contentResolver.openOutputStream(tempDoc.uri)?.use { output ->
                FileInputStream(tempFile).use { it.copyTo(output) }
            } ?: error("无法写入临时文件：$fileName")
            val existing = folder.findFile(fileName)
            if (existing != null && !existing.delete()) {
                tempDoc.delete()
                error("无法替换本地文件：$fileName")
            }
            if (!tempDoc.renameTo(fileName)) {
                val target = folder.createFile(guessMimeType(fileName), fileName)
                    ?: error("无法创建本地文件：$fileName")
                appContext.contentResolver.openOutputStream(target.uri)?.use { output ->
                    FileInputStream(tempFile).use { it.copyTo(output) }
                } ?: error("无法写入本地文件：$fileName")
                tempDoc.delete()
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun isProbablySame(local: LocalFileInfo, remote: RemoteFileInfo): Boolean {
        if (local.length >= 0L && remote.contentLength >= 0L && local.length != remote.contentLength) {
            return false
        }
        val remoteTime = remote.lastModifiedMs
        val localTime = local.lastModifiedMs
        if (remoteTime <= 0L || localTime <= 0L) return false
        return abs(localTime - remoteTime) <= TIME_TOLERANCE_MS
    }

    private fun hashSmallMarkdown(file: DocumentFile, name: String): String {
        val length = file.length()
        if (length < 0L || length > MAX_HASH_BYTES) return ""
        val lowerName = name.lowercase()
        if (!lowerName.endsWith(".md") && !lowerName.endsWith(".markdown")) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        appContext.contentResolver.openInputStream(file.uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return ""
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun localStateMarker(files: List<LocalFileInfo>): String =
        LOCAL_STATE_PREFIX + files.sortedBy { it.relativePath }
            .joinToString("\n") { "${it.relativePath}|${it.markerValue()}" }

    private fun remoteStateMarker(files: List<RemoteFileInfo>): String =
        REMOTE_STATE_PREFIX + files.sortedBy { it.relativePath }
            .joinToString("\n") { "${it.relativePath}|${it.markerValue()}" }

    private fun saveAppliedSnapshots(appliedPaths: List<String>, service: WebDavSyncService) {
        val snapshots = parseSnapshots(prefsManager.getWebDavFileSyncSnapshot()).toMutableMap()
        val localFiles = scanLocalFiles().associateBy { it.relativePath }
        val remoteFiles = service.listRemoteFiles().associateBy { it.relativePath }
        appliedPaths.forEach { path ->
            val local = localFiles[path] ?: return@forEach
            val remote = remoteFiles[path] ?: return@forEach
            snapshots[path] = FileSyncSnapshot(
                relativePath = path,
                localMarker = local.markerValue(),
                remoteMarker = remote.markerValue(),
            )
        }
        prefsManager.saveWebDavFileSyncSnapshot(serializeSnapshots(snapshots.values))
    }

    private fun saveKnownSnapshots(knownSnapshots: List<FileSyncSnapshot>) {
        val snapshots = parseSnapshots(prefsManager.getWebDavFileSyncSnapshot()).toMutableMap()
        knownSnapshots.forEach { snapshots[it.relativePath] = it }
        prefsManager.saveWebDavFileSyncSnapshot(serializeSnapshots(snapshots.values))
    }

    private fun updatePendingConflicts(conflicts: List<SyncConflict>) {
        if (conflicts.isEmpty()) {
            prefsManager.clearWebDavPendingConflicts()
            return
        }
        prefsManager.saveWebDavPendingConflicts(
            conflicts.take(40).joinToString("\n") { conflict ->
                "${conflict.relativePath}\t${conflict.reason.label}\t${conflict.localModifiedMs}\t${conflict.remoteModifiedMs}"
            },
        )
    }

    private fun parseSnapshots(value: String): Map<String, FileSyncSnapshot> =
        value.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@mapNotNull null
                val path = Uri.decode(parts[0])
                if (path.isBlank()) return@mapNotNull null
                path to FileSyncSnapshot(
                    relativePath = path,
                    localMarker = Uri.decode(parts[1]),
                    remoteMarker = Uri.decode(parts[2]),
                )
            }
            .toMap()

    private fun serializeSnapshots(snapshots: Collection<FileSyncSnapshot>): String =
        snapshots.sortedBy { it.relativePath }.joinToString("\n") { snapshot ->
            listOf(snapshot.relativePath, snapshot.localMarker, snapshot.remoteMarker)
                .joinToString("\t") { Uri.encode(it) }
        }

    private fun configSummary(settings: PrefsManager.WebDavSettings): String =
        listOf(
            settings.serverUrl.trimEnd('/'),
            normalizeRelativePath(settings.remoteFolder).orEmpty(),
            settings.username.trim(),
        ).joinToString("|")

    private fun validateSettings(settings: PrefsManager.WebDavSettings) {
        val url = settings.serverUrl.trim()
        val lowerUrl = url.lowercase(Locale.ROOT)
        if (url.isBlank()) error("请先填写 WebDAV 地址")
        if (lowerUrl.startsWith("http://")) {
            error("WebDAV 地址使用 HTTP 明文传输，请改用 HTTPS")
        }
        if (!lowerUrl.startsWith("https://")) {
            error("WebDAV 地址必须以 https:// 开头")
        }
        if (settings.username.isBlank()) error("请先填写 WebDAV 用户名")
        if (settings.password.isBlank()) error("请先填写 WebDAV 密码或应用密码")
        if (settings.remoteFolder.isBlank()) error("请先填写 WebDAV 远程目录")
        normalizeRelativePath(settings.remoteFolder)
            ?: error("WebDAV 远程目录不能包含 .. 或非法路径")
    }

    data class RemoteFileState(
        val marker: String,
    )

    data class RealtimeDownloadResult(
        val message: String,
        val changedPaths: List<String>,
        val skippedConflictCount: Int,
    )

    data class SyncResult(
        val uploadedCount: Int,
        val downloadedCount: Int,
        val skippedConflictCount: Int,
        val changedPaths: List<String>,
    ) {
        val message: String =
            if (skippedConflictCount > 0) {
                "WebDAV 文件级同步未完全完成：上传 $uploadedCount 个，下载 $downloadedCount 个，冲突待处理 $skippedConflictCount 个"
            } else {
                "WebDAV 文件级同步完成：上传 $uploadedCount 个，下载 $downloadedCount 个，跳过冲突 0 个"
            }
    }

    data class SyncPreview(
        val toUpload: List<String>,
        val toDownload: List<String>,
        val localNewer: List<String>,
        val remoteNewer: List<String>,
        val conflicts: List<SyncConflict>,
        val localMarker: String,
        val remoteMarker: String,
        val configSummary: String,
        val generatedAtMs: Long,
    ) {
        val uploadCount: Int get() = toUpload.size + localNewer.size
        val downloadCount: Int get() = toDownload.size + remoteNewer.size
        val isEmpty: Boolean get() = uploadCount == 0 && downloadCount == 0 && conflicts.isEmpty()
        fun summary(): String = "上传 $uploadCount 个，下载 $downloadCount 个，冲突 ${conflicts.size} 个"
    }

    data class SyncConflict(
        val relativePath: String,
        val reason: ConflictReason,
        val localModifiedMs: Long,
        val remoteModifiedMs: Long,
    )

    data class SyncProgress(
        val totalFiles: Int,
        val processedFiles: Int,
        val currentFile: String,
        val operation: SyncOperation,
    )

    enum class ConflictResolution {
        KEEP_LOCAL,
        KEEP_REMOTE,
        SKIP,
    }

    enum class ConflictReason(val label: String) {
        BOTH_CHANGED("本地和远端都修改过"),
        LOCAL_MISSING("本地缺失，疑似本地删除或重命名"),
        REMOTE_MISSING("远端缺失，疑似远端删除或重命名"),
        NEEDS_CONFIRM("缺少同步快照，需要确认"),
    }

    enum class SyncOperation(val label: String) {
        UPLOAD("上传"),
        DOWNLOAD("下载"),
    }

    private data class LocalFileInfo(
        val relativePath: String,
        val document: DocumentFile,
        val lastModifiedMs: Long,
        val length: Long,
        val hash: String,
    ) {
        fun markerValue(): String = listOf(length.toString(), lastModifiedMs.toString(), hash)
            .joinToString("|")
    }

    private data class FileSyncSnapshot(
        val relativePath: String,
        val localMarker: String,
        val remoteMarker: String,
    )

    private data class RemoteFileInfo(
        val relativePath: String,
        val isCollection: Boolean,
        val lastModifiedMs: Long,
        val etag: String,
        val contentLength: Long,
    ) {
        fun markerValue(): String = listOf(etag, lastModifiedMs.toString(), contentLength.toString())
            .joinToString("|")
    }

    private inner class WebDavSyncService(
        private val settings: PrefsManager.WebDavSettings,
        private val client: OkHttpClient,
    ) {
        fun ensureRemoteWorkspace() {
            ensureRemoteFolder()
            ensureRemoteCollection(remoteRootUrl())
        }

        fun listRemoteFiles(): List<RemoteFileInfo> =
            listRemoteFiles(remoteRootUrl(), "")

        fun remoteStateMarker(): String {
            val marker = listRemoteFiles()
                .sortedBy { it.relativePath }
                .joinToString("\n") { file -> "${file.relativePath}|${file.markerValue()}" }
            return REMOTE_STATE_PREFIX + marker
        }

        fun uploadFile(file: DocumentFile, relativePath: String) {
            val safePath = normalizeRelativePath(relativePath)
                ?: error("本地文件路径不安全：$relativePath")
            val tempFile = File.createTempFile("kardleaf-webdav-upload-", ".tmp", appContext.cacheDir)
            try {
                appContext.contentResolver.openInputStream(file.uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                } ?: error("无法读取本地文件：$safePath")
                ensureRemoteParentCollections(safePath)
                val request = Request.Builder()
                    .url(remoteEntryUrl(safePath))
                    .put(tempFile.asRequestBody(guessMimeType(safePath).toMediaType()))
                    .applyAuth()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) httpError("WebDAV 上传失败", response.code)
                }
            } finally {
                tempFile.delete()
            }
        }

        fun downloadFile(relativePath: String, sink: (InputStream) -> Unit) {
            val safePath = normalizeRelativePath(relativePath)
                ?: error("远端文件路径不安全：$relativePath")
            val request = Request.Builder()
                .url(remoteEntryUrl(safePath))
                .get()
                .applyAuth()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) httpError("WebDAV 下载失败", response.code)
                response.body?.byteStream()?.use(sink) ?: error("WebDAV 下载失败：远端返回空文件")
            }
        }

        fun deleteRemoteFile(relativePath: String) {
            error("WebDAV 删除远端文件默认禁用：$relativePath")
        }

        private fun listRemoteFiles(folderUrl: String, currentRelativePath: String): List<RemoteFileInfo> {
            val files = mutableListOf<RemoteFileInfo>()
            propfindChildren(folderUrl).forEach { item ->
                if (item.relativePath.isBlank() || item.relativePath == currentRelativePath) {
                    return@forEach
                }
                if (item.isCollection) {
                    files += listRemoteFiles(remoteEntryUrl(item.relativePath), item.relativePath)
                } else {
                    files += item
                }
            }
            return files
        }

        private fun propfindChildren(folderUrl: String): List<RemoteFileInfo> {
            val request = Request.Builder()
                .url(folderUrl)
                .method("PROPFIND", WEBDAV_PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
                .header("Depth", "1")
                .applyAuth()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return emptyList()
                if (!response.isSuccessful) httpError("WebDAV 扫描远端失败", response.code)
                val xml = response.body?.string().orEmpty()
                if (xml.isBlank()) return emptyList()
                val document = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                }.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
                val responseNodes = document.getElementsByTagNameNS("*", "response")
                val result = mutableListOf<RemoteFileInfo>()
                for (index in 0 until responseNodes.length) {
                    val element = responseNodes.item(index) as? Element ?: continue
                    val relativePath = remoteHrefToRelativePath(firstWebDavText(element, "href"))
                    val safePath = normalizeRelativePath(relativePath) ?: continue
                    result += RemoteFileInfo(
                        relativePath = safePath,
                        isCollection = element.getElementsByTagNameNS("*", "collection").length > 0,
                        lastModifiedMs = parseWebDavTime(firstWebDavText(element, "getlastmodified")),
                        etag = firstWebDavText(element, "getetag"),
                        contentLength = firstWebDavText(element, "getcontentlength").toLongOrNull() ?: -1L,
                    )
                }
                return result
            }
        }

        private fun ensureRemoteParentCollections(relativePath: String) {
            val parts = relativePath.split('/').dropLast(1)
            var currentUrl = remoteRootUrl()
            parts.forEach { segment ->
                currentUrl = "${currentUrl.trimEnd('/')}/${encodePathSegment(segment)}"
                ensureRemoteCollection(currentUrl)
            }
        }

        private fun ensureRemoteFolder() {
            val baseUrl = settings.serverUrl.trimEnd('/')
            val remoteFolder = normalizeRelativePath(settings.remoteFolder)
                ?: error("WebDAV 远程目录不能包含 .. 或非法路径")
            var currentUrl = baseUrl
            remoteFolder.split('/').filter { it.isNotBlank() }.forEach { segment ->
                currentUrl += "/${encodePathSegment(segment)}"
                ensureRemoteCollection(currentUrl)
            }
        }

        private fun ensureRemoteCollection(url: String) {
            val request = Request.Builder()
                .url(url)
                .method("MKCOL", null)
                .applyAuth()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code !in listOf(201, 405)) {
                    httpError("WebDAV 创建目录失败", response.code)
                }
            }
        }

        private fun remoteRootUrl(): String =
            "${remoteFolderUrl().trimEnd('/')}/$REMOTE_VAULT_FOLDER"

        private fun remoteEntryUrl(relativePath: String): String {
            val safePath = normalizeRelativePath(relativePath).orEmpty()
            val encodedPath = safePath
                .split('/')
                .filter { it.isNotBlank() }
                .joinToString("/") { encodePathSegment(it) }
            return if (encodedPath.isBlank()) {
                remoteRootUrl()
            } else {
                "${remoteRootUrl().trimEnd('/')}/$encodedPath"
            }
        }

        private fun remoteFolderUrl(): String {
            val folderPart = normalizeRelativePath(settings.remoteFolder).orEmpty()
                .split('/')
                .filter { it.isNotBlank() }
                .joinToString("/") { encodePathSegment(it) }
            return "${settings.serverUrl.trimEnd('/')}/$folderPart"
        }

        private fun remoteHrefToRelativePath(href: String): String {
            if (href.isBlank()) return ""
            val hrefPath = Uri.parse(href).encodedPath ?: href.substringBefore('?')
            val rootPath = Uri.parse(remoteRootUrl()).encodedPath.orEmpty()
            val decodedHrefPath = Uri.decode(hrefPath).trimEnd('/')
            val decodedRootPath = Uri.decode(rootPath).trimEnd('/')
            return when {
                decodedHrefPath == decodedRootPath -> ""
                decodedHrefPath.startsWith("$decodedRootPath/") ->
                    decodedHrefPath.removePrefix("$decodedRootPath/").trim('/')
                else -> ""
            }
        }

        private fun Request.Builder.applyAuth(): Request.Builder {
            header("Authorization", Credentials.basic(settings.username.trim(), settings.password))
            return this
        }
    }

    companion object {
        private val syncMutex = Mutex()

        fun readableError(error: Throwable, fallback: String): String {
            val root = error.cause ?: error
            val detail = when (root) {
                is UnknownHostException -> "网络不可达或 WebDAV 域名无法解析"
                is SocketTimeoutException -> "网络超时，请检查 WebDAV 服务状态后重试"
                is ConnectException -> "无法连接 WebDAV 服务器，请检查地址、网络和端口"
                is IOException -> root.message?.takeIf { it.isNotBlank() } ?: "网络连接失败"
                else -> root.message?.takeIf { it.isNotBlank() }
                    ?: error.message?.takeIf { it.isNotBlank() }
                    ?: fallback
            }
            return if (detail.startsWith(fallback)) detail else "$fallback：$detail"
        }

        private fun normalizeRelativePath(path: String): String? {
            val normalized = path.replace('\\', '/').trim('/')
            if (normalized.isBlank()) return ""
            val parts = normalized.split('/').filter { it.isNotBlank() }
            if (parts.any { safePathSegment(it) == null }) return null
            return parts.joinToString("/")
        }

        private fun safePathSegment(segment: String): String? {
            val value = segment.trim()
            if (value.isBlank() || value == "." || value == "..") return null
            if (value.contains('/') || value.contains('\\')) return null
            return value
        }

        private fun firstWebDavText(element: Element, localName: String): String =
            element.getElementsByTagNameNS("*", localName)
                .item(0)
                ?.textContent
                ?.trim()
                .orEmpty()

        private fun parseWebDavTime(value: String): Long {
            if (value.isBlank()) return 0L
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            return runCatching { format.parse(value)?.time ?: 0L }.getOrDefault(0L)
        }

        private fun httpError(action: String, code: Int): Nothing {
            val detail = when (code) {
                401 -> "认证失败，请检查用户名和密码"
                403 -> "没有权限访问该 WebDAV 目录"
                404 -> "远端路径不存在"
                409 -> "远端父目录不存在或目录冲突"
                in 500..599 -> "服务器错误，请稍后重试"
                else -> "HTTP $code"
            }
            error("$action：$detail")
        }

        private fun encodePathSegment(segment: String): String =
            Uri.encode(segment).replace("+", "%20")

        private fun guessMimeType(fileName: String): String {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }

        private const val REMOTE_VAULT_FOLDER = "vault"
        private const val REMOTE_STATE_PREFIX = "vault-files:"
        private const val LOCAL_STATE_PREFIX = "local-files:"
        private const val TIME_TOLERANCE_MS = 5_000L
        private const val MAX_HASH_BYTES = 512 * 1024L
        private const val TEMP_FILE_SUFFIX = ".kardleaf-sync-tmp"
        private val WEBDAV_PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
                <d:prop>
                    <d:resourcetype />
                    <d:getetag />
                    <d:getlastmodified />
                    <d:getcontentlength />
                </d:prop>
            </d:propfind>
        """.trimIndent()
    }
}
