package com.kangle.kardleaf.ui

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.system.Os
import com.kangle.kardleaf.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val DEV_STORAGE_REPORT_LIMIT = 500

private data class DevStoragePathStat(
    val path: String,
    val logicalBytes: Long,
    val allocatedBytes: Long,
    val fileCount: Int,
    val isCache: Boolean = false,
)

private data class DevStorageSourceStat(
    val label: String,
    val path: String,
    val logicalBytes: Long,
    val allocatedBytes: Long,
    val fileCount: Int,
)

private data class DevStorageNodeSummary(
    val logicalBytes: Long,
    val allocatedBytes: Long,
    val fileCount: Int,
)

private data class DevSystemStorageStat(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
)

private class DevStorageExportStats {
    val sources = mutableListOf<DevStorageSourceStat>()
    val directories = mutableListOf<DevStoragePathStat>()
    val files = mutableListOf<DevStoragePathStat>()
    val skipped = mutableListOf<String>()
    var archivedBytes: Long = 0L
    var archivedFileCount: Int = 0
    var systemStorage: DevSystemStorageStat? = null
    var systemStorageError: String? = null
}

internal fun createDevStorageExportFile(context: Context): File {
    check(BuildConfig.KARDLEAF_DEV_VARIANT) { "仅 Dev 版本允许导出应用私有数据" }

    val shareDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
    shareDir.listFiles()
        ?.filter { it.isFile && it.name.startsWith("kardleaf_dev_storage_") && it.extension == "zip" }
        ?.forEach { it.delete() }

    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val outputFile = File(shareDir, "kardleaf_dev_storage_$timestamp.zip")
    val deviceProtectedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.createDeviceProtectedStorageContext()
    } else {
        null
    }
    val sources = buildList {
        add("internal-data" to File(context.applicationInfo.dataDir))
        deviceProtectedContext?.let { add("device-protected-data" to it.dataDir) }
        context.getExternalFilesDirs(null).forEachIndexed { index, file ->
            if (file != null) add("external-files-$index" to file)
        }
        context.getExternalCacheDirs().forEachIndexed { index, file ->
            if (file != null) add("external-cache-$index" to file)
        }
        context.externalMediaDirs.forEachIndexed { index, file ->
            add("external-media-$index" to file)
        }
    }.distinctBy { (_, file) -> runCatching { file.canonicalPath }.getOrDefault(file.absolutePath) }
    val cacheRootCanonicalPaths = buildList {
        add(context.cacheDir)
        add(context.codeCacheDir)
        deviceProtectedContext?.let {
            add(it.cacheDir)
            add(it.codeCacheDir)
        }
        context.getExternalCacheDirs().forEach { file ->
            if (file != null) add(file)
        }
    }.mapNotNull { file -> runCatching { file.canonicalPath }.getOrNull() }
        .distinct()

    val stats = DevStorageExportStats()
    val systemStorageResult = readDevSystemStorageStat(context)
    stats.systemStorage = systemStorageResult.getOrNull()
    stats.systemStorageError = systemStorageResult.exceptionOrNull()?.message
    try {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            sources.forEach { (label, root) ->
                if (!root.exists()) {
                    stats.skipped += "$label：目录不存在（${root.absolutePath}）"
                    return@forEach
                }
                val rootCanonicalPath = runCatching { root.canonicalPath }.getOrElse { error ->
                    stats.skipped += "$label：无法解析根目录（${error.message.orEmpty()}）"
                    return@forEach
                }
                val summary = addDevStorageNodeToZip(
                    file = root,
                    entryPath = label,
                    sourceRootCanonicalPath = rootCanonicalPath,
                    cacheRootCanonicalPaths = cacheRootCanonicalPaths,
                    visitedDirectories = mutableSetOf(),
                    outputFile = outputFile,
                    zip = zip,
                    stats = stats,
                )
                stats.sources += DevStorageSourceStat(
                    label = label,
                    path = root.absolutePath,
                    logicalBytes = summary.logicalBytes,
                    allocatedBytes = summary.allocatedBytes,
                    fileCount = summary.fileCount,
                )
            }

            val report = buildDevStorageReport(context, stats)
            zip.putNextEntry(ZipEntry("storage_report.txt"))
            zip.write(report.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return outputFile
    } catch (error: Throwable) {
        outputFile.delete()
        throw error
    }
}

private fun addDevStorageNodeToZip(
    file: File,
    entryPath: String,
    sourceRootCanonicalPath: String,
    cacheRootCanonicalPaths: List<String>,
    visitedDirectories: MutableSet<String>,
    outputFile: File,
    zip: ZipOutputStream,
    stats: DevStorageExportStats,
): DevStorageNodeSummary {
    val fileCanonical = runCatching { file.canonicalPath }.getOrElse {
        stats.skipped += "$entryPath：无法解析路径（${it.message.orEmpty()}）"
        return DevStorageNodeSummary(0L, 0L, 0)
    }
    val outputCanonical = runCatching { outputFile.canonicalPath }.getOrDefault(outputFile.absolutePath)
    if (fileCanonical == outputCanonical) {
        stats.skipped += "$entryPath：跳过当前正在生成的导出文件"
        return DevStorageNodeSummary(0L, 0L, 0)
    }
    val sourceRootPrefix = "$sourceRootCanonicalPath${File.separator}"
    if (fileCanonical != sourceRootCanonicalPath && !fileCanonical.startsWith(sourceRootPrefix)) {
        stats.skipped += "$entryPath：跳过指向数据目录外的符号链接 -> $fileCanonical"
        return DevStorageNodeSummary(0L, 0L, 0)
    }

    if (file.isDirectory) {
        if (!visitedDirectories.add(fileCanonical)) {
            stats.skipped += "$entryPath：跳过重复或循环目录 -> $fileCanonical"
            return DevStorageNodeSummary(0L, 0L, 0)
        }
        val children = file.listFiles()
        if (children == null) {
            stats.skipped += "$entryPath：无法读取目录"
            return DevStorageNodeSummary(0L, 0L, 0)
        }
        if (children.isEmpty()) {
            zip.putNextEntry(ZipEntry("$entryPath/"))
            zip.closeEntry()
        }
        var totalLogicalBytes = 0L
        var totalAllocatedBytes = readDevStorageAllocatedBytes(file)
        var totalFiles = 0
        children.sortedBy { it.name.lowercase(Locale.ROOT) }.forEach { child ->
            val childSummary = addDevStorageNodeToZip(
                file = child,
                entryPath = "$entryPath/${child.name}",
                sourceRootCanonicalPath = sourceRootCanonicalPath,
                cacheRootCanonicalPaths = cacheRootCanonicalPaths,
                visitedDirectories = visitedDirectories,
                outputFile = outputFile,
                zip = zip,
                stats = stats,
            )
            totalLogicalBytes += childSummary.logicalBytes
            totalAllocatedBytes += childSummary.allocatedBytes
            totalFiles += childSummary.fileCount
        }
        stats.directories += DevStoragePathStat(
            path = entryPath,
            logicalBytes = totalLogicalBytes,
            allocatedBytes = totalAllocatedBytes,
            fileCount = totalFiles,
        )
        return DevStorageNodeSummary(totalLogicalBytes, totalAllocatedBytes, totalFiles)
    }

    if (!file.isFile) {
        stats.skipped += "$entryPath：不是普通文件"
        return DevStorageNodeSummary(0L, 0L, 0)
    }

    val logicalSize = file.length().coerceAtLeast(0L)
    val allocatedSize = readDevStorageAllocatedBytes(file)
    val isCache = cacheRootCanonicalPaths.any { cacheRoot ->
        fileCanonical == cacheRoot || fileCanonical.startsWith("$cacheRoot${File.separator}")
    }
    stats.files += DevStoragePathStat(
        path = entryPath,
        logicalBytes = logicalSize,
        allocatedBytes = allocatedSize,
        fileCount = 1,
        isCache = isCache,
    )
    val input = runCatching { BufferedInputStream(FileInputStream(file)) }.getOrElse { error ->
        stats.skipped += "$entryPath：读取失败（${error.message.orEmpty()}）"
        return DevStorageNodeSummary(logicalSize, allocatedSize, 1)
    }
    return input.use {
        val entry = ZipEntry(entryPath).apply { time = file.lastModified() }
        zip.putNextEntry(entry)
        try {
            input.copyTo(zip, bufferSize = 64 * 1024)
        } finally {
            zip.closeEntry()
        }
        stats.archivedBytes += logicalSize
        stats.archivedFileCount += 1
        DevStorageNodeSummary(logicalSize, allocatedSize, 1)
    }
}

private fun readDevStorageAllocatedBytes(file: File): Long {
    return runCatching {
        Math.multiplyExact(Os.lstat(file.absolutePath).st_blocks, 512L)
    }.getOrElse {
        file.length()
    }.coerceAtLeast(0L)
}

private fun readDevSystemStorageStat(context: Context): Result<DevSystemStorageStat> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return Result.failure(IllegalStateException("Android 8.0 以下不支持系统存储统计接口"))
    }
    return runCatching {
        val manager = context.getSystemService(StorageStatsManager::class.java)
            ?: error("无法获取 StorageStatsManager")
        val storageUuid = context.applicationInfo.storageUuid ?: StorageManager.UUID_DEFAULT
        val storageStats = manager.queryStatsForPackage(
            storageUuid,
            context.packageName,
            Process.myUserHandle(),
        )
        DevSystemStorageStat(
            appBytes = storageStats.appBytes,
            dataBytes = storageStats.dataBytes,
            cacheBytes = storageStats.cacheBytes,
        )
    }
}

private fun buildDevStorageReport(
    context: Context,
    stats: DevStorageExportStats,
): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    val scannedLogicalBytes = stats.sources.sumOf { it.logicalBytes }
    val scannedAllocatedBytes = stats.sources.sumOf { it.allocatedBytes }
    val scannedFiles = stats.sources.sumOf { it.fileCount }
    val scannedCacheLogicalBytes = stats.files.filter { it.isCache }.sumOf { it.logicalBytes }
    val scannedCacheAllocatedBytes = stats.files.filter { it.isCache }.sumOf { it.allocatedBytes }
    val scannedDataLogicalBytes = (scannedLogicalBytes - scannedCacheLogicalBytes).coerceAtLeast(0L)
    val scannedDataAllocatedBytes = (scannedAllocatedBytes - scannedCacheAllocatedBytes).coerceAtLeast(0L)
    return buildString {
        appendLine("KardLeaf Dev 用户数据与缓存空间报告")
        appendLine("生成时间：$generatedAt")
        appendLine("应用版本：${packageInfo.versionName} ($versionCode)")
        appendLine("包名：${context.packageName}")
        appendLine("Git 节点：${BuildConfig.KARDLEAF_GIT_COMMIT}")
        appendLine()
        appendLine("用途：定位应用私有数据、数据库、WebView 数据、内部缓存及外部应用目录中的大文件。")
        appendLine("注意：ZIP 与解压目录显示的是文件逻辑长度，不等于 Android 文件系统实际占用；请对照逻辑大小、实际占用块和系统统计。")
        appendLine("注意：ZIP 含用户数据和缓存，可能包含笔记索引、历史记录、偏好设置及登录状态，请勿公开分享。")
        appendLine("注意：这是运行中应用的排查快照，不保证数据库文件可用于恢复。")
        appendLine()
        appendLine("===== Android 系统存储统计（用于对齐设置页） =====")
        val systemStorage = stats.systemStorage
        if (systemStorage == null) {
            appendLine("获取失败：${stats.systemStorageError.orEmpty().ifBlank { "未知错误" }}")
        } else {
            val systemUserDataBytes = (systemStorage.dataBytes - systemStorage.cacheBytes).coerceAtLeast(0L)
            appendLine("应用本体：${formatDevStorageBytes(systemStorage.appBytes)}")
            appendLine("用户数据（不含缓存，按系统数据总量减缓存计算）：${formatDevStorageBytes(systemUserDataBytes)}")
            appendLine("缓存：${formatDevStorageBytes(systemStorage.cacheBytes)}")
            appendLine("系统数据总量（含缓存）：${formatDevStorageBytes(systemStorage.dataBytes)}")
            appendLine("应用总占用（应用本体 + 系统数据总量）：${formatDevStorageBytes(systemStorage.appBytes + systemStorage.dataBytes)}")
        }
        appendLine()
        appendLine("===== 文件扫描统计（用于定位具体文件） =====")
        appendLine("用户数据逻辑大小（不含缓存）：${formatDevStorageBytes(scannedDataLogicalBytes)}")
        appendLine("用户数据实际占用块（不含缓存）：${formatDevStorageBytes(scannedDataAllocatedBytes)}")
        appendLine("缓存逻辑大小：${formatDevStorageBytes(scannedCacheLogicalBytes)}")
        appendLine("缓存实际占用块：${formatDevStorageBytes(scannedCacheAllocatedBytes)}")
        appendLine("扫描逻辑总量：${formatDevStorageBytes(scannedLogicalBytes)} | 文件 $scannedFiles 个")
        appendLine("扫描实际占用块总量：${formatDevStorageBytes(scannedAllocatedBytes)}")
        appendLine("成功写入 ZIP（逻辑内容）：${formatDevStorageBytes(stats.archivedBytes)} | 文件 ${stats.archivedFileCount} 个")
        if (systemStorage != null) {
            val systemUserDataBytes = (systemStorage.dataBytes - systemStorage.cacheBytes).coerceAtLeast(0L)
            appendLine("用户数据差值（系统 - 实际占用块）：${formatDevStorageSignedBytes(systemUserDataBytes - scannedDataAllocatedBytes)}")
            appendLine("缓存差值（系统 - 实际占用块）：${formatDevStorageSignedBytes(systemStorage.cacheBytes - scannedCacheAllocatedBytes)}")
            appendLine("说明：仍为正数时，通常属于系统配额统计、目录元数据、统计时点变化，或无法逐文件归属的系统管理数据。")
        }
        appendLine()
        appendLine("===== 根目录汇总 =====")
        stats.sources.sortedByDescending { it.allocatedBytes }.forEachIndexed { index, source ->
            appendLine("${index + 1}. ${source.label} | 逻辑 ${formatDevStorageBytes(source.logicalBytes)} | 实际占用 ${formatDevStorageBytes(source.allocatedBytes)} | ${source.fileCount} 个文件")
            appendLine("   ${source.path}")
        }
        appendLine()
        appendLine("===== 最大目录（前 $DEV_STORAGE_REPORT_LIMIT 项） =====")
        stats.directories
            .sortedByDescending { it.allocatedBytes }
            .take(DEV_STORAGE_REPORT_LIMIT)
            .forEachIndexed { index, item ->
                appendLine("${index + 1}. 逻辑 ${formatDevStorageBytes(item.logicalBytes)} | 实际占用 ${formatDevStorageBytes(item.allocatedBytes)} | ${item.fileCount} 个文件 | ${item.path}")
            }
        appendLine()
        appendLine("===== 最大用户数据文件（前 $DEV_STORAGE_REPORT_LIMIT 项） =====")
        stats.files
            .filterNot { it.isCache }
            .sortedByDescending { it.allocatedBytes }
            .take(DEV_STORAGE_REPORT_LIMIT)
            .forEachIndexed { index, item ->
                appendLine("${index + 1}. 逻辑 ${formatDevStorageBytes(item.logicalBytes)} | 实际占用 ${formatDevStorageBytes(item.allocatedBytes)} | ${item.path}")
            }
        appendLine()
        appendLine("===== 最大缓存文件（前 $DEV_STORAGE_REPORT_LIMIT 项） =====")
        stats.files
            .filter { it.isCache }
            .sortedByDescending { it.allocatedBytes }
            .take(DEV_STORAGE_REPORT_LIMIT)
            .forEachIndexed { index, item ->
                appendLine("${index + 1}. 逻辑 ${formatDevStorageBytes(item.logicalBytes)} | 实际占用 ${formatDevStorageBytes(item.allocatedBytes)} | ${item.path}")
            }
        appendLine()
        appendLine("===== 跳过或读取失败 =====")
        if (stats.skipped.isEmpty()) {
            appendLine("无")
        } else {
            stats.skipped.forEach { appendLine(it) }
        }
    }
}

private fun formatDevStorageSignedBytes(bytes: Long): String {
    val prefix = if (bytes >= 0L) "+" else "-"
    return prefix + formatDevStorageBytes(kotlin.math.abs(bytes))
}

private fun formatDevStorageBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.2f %s", value, units[unitIndex])
}
