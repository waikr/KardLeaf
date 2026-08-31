package com.kangle.kardleaf.ui.editor.quillpad

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.kangle.kardleaf.data.repository.PrefsManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Native Beta inline-image resolver.
 *
 * Keeps the maintained editor independent from the legacy Alpha implementation and avoids
 * repeatedly walking the SAF tree or decoding the same image after every text change.
 */
internal class QuillpadInlineImageResolver(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefsManager = PrefsManager(appContext)

    fun resolveBitmap(
        currentFolder: String,
        reference: String,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? = runCatching {
        val rawReference = Uri.decode(reference.trim().trim('"', '\'')).substringBefore("#")
        val parsedReference = runCatching { Uri.parse(rawReference) }.getOrNull()
        val directContentFile =
            parsedReference
                ?.takeIf { it.scheme.equals("content", ignoreCase = true) }
                ?.let { DocumentFile.fromSingleUri(appContext, it) }
                ?.takeIf { safeIsFile(it) }
        val cleanReference = normalizePath(rawReference)
        if (directContentFile == null && cleanReference.isBlank()) return@runCatching null

        val imageFile =
            directContentFile ?: run {
                val rootUriString = prefsManager.getRootUri()?.takeIf { it.isNotBlank() } ?: return@runCatching null
                val locationKey = "$rootUriString|${normalizePath(currentFolder)}|$cleanReference"
                resolveImageFile(rootUriString, currentFolder, cleanReference, locationKey)
                    ?: return@runCatching null
            }
        val cacheKey = buildString {
            append(imageFile.uri)
            append('|').append(runCatching { imageFile.lastModified() }.getOrDefault(0L))
            append('|').append(runCatching { imageFile.length() }.getOrDefault(0L))
            append('|').append(maxWidthPx)
            append('x').append(maxHeightPx)
        }
        synchronized(bitmapCache) {
            bitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }
        }?.let { return@runCatching it }

        val proposedLock = Any()
        val decodeLock = bitmapDecodeLocks.putIfAbsent(cacheKey, proposedLock) ?: proposedLock
        try {
            synchronized(decodeLock) {
                synchronized(bitmapCache) {
                    bitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }
                } ?: decodeSampledBitmap(imageFile, maxWidthPx, maxHeightPx)?.also { decoded ->
                    synchronized(bitmapCache) { bitmapCache.put(cacheKey, decoded) }
                }
            }
        } finally {
            bitmapDecodeLocks.remove(cacheKey, decodeLock)
        }
    }.getOrNull()

    private fun resolveImageFile(
        rootUriString: String,
        currentFolder: String,
        cleanReference: String,
        locationKey: String,
    ): DocumentFile? {
        synchronized(resolvedUriCache) { resolvedUriCache.get(locationKey) }
            ?.let { cachedUri ->
                DocumentFile.fromSingleUri(appContext, cachedUri)?.takeIf { safeIsFile(it) }?.let { return it }
                synchronized(resolvedUriCache) { resolvedUriCache.remove(locationKey) }
            }

        val rootUri = runCatching { Uri.parse(rootUriString) }.getOrNull() ?: return null
        val candidates = imageReferenceCandidates(currentFolder, cleanReference)
        val rootDocumentId = runCatching { DocumentsContract.getTreeDocumentId(rootUri) }.getOrNull()
        if (!rootDocumentId.isNullOrBlank()) {
            candidates.forEach { path ->
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, "$rootDocumentId/$path")
                val document = DocumentFile.fromSingleUri(appContext, documentUri)
                if (document != null && safeIsFile(document)) {
                    synchronized(resolvedUriCache) { resolvedUriCache.put(locationKey, documentUri) }
                    return document
                }
            }
        }

        resolveFromConfiguredImageFolder(currentFolder, cleanReference)?.let { configured ->
            synchronized(resolvedUriCache) { resolvedUriCache.put(locationKey, configured.uri) }
            return configured
        }

        val root = DocumentFile.fromTreeUri(appContext, rootUri) ?: return null
        candidates.forEach { path ->
            val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
            val name = path.substringAfterLast('/')
            val file = findFolder(root, parentPath)?.findFile(name)?.takeIf { safeIsFile(it) }
            if (file != null) {
                synchronized(resolvedUriCache) { resolvedUriCache.put(locationKey, file.uri) }
                return file
            }
        }
        return null
    }

    private fun resolveFromConfiguredImageFolder(
        currentFolder: String,
        cleanReference: String,
    ): DocumentFile? {
        val imageFolderUri = prefsManager.getImageFolderUri()
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return null
        val imageFolderDocumentId = runCatching { DocumentsContract.getTreeDocumentId(imageFolderUri) }.getOrNull()
            ?: return null
        val configuredFolder = normalizePath(prefsManager.getImageFolder())
        if (configuredFolder.isBlank()) return null

        imageReferenceCandidates(currentFolder, cleanReference)
            .mapNotNull { path ->
                normalizePath(path)
                    .takeIf { it.startsWith("$configuredFolder/") }
                    ?.removePrefix("$configuredFolder/")
                    ?.takeIf { it.isNotBlank() }
            }
            .distinct()
            .forEach { relativePath ->
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                    imageFolderUri,
                    "$imageFolderDocumentId/$relativePath",
                )
                val document = DocumentFile.fromSingleUri(appContext, documentUri)
                if (document != null && safeIsFile(document)) return document
            }
        return null
    }

    private fun decodeSampledBitmap(
        imageFile: DocumentFile,
        maxWidthPx: Int,
        maxHeightPx: Int,
    ): Bitmap? {
        val requestedWidth = maxWidthPx.coerceAtLeast(1)
        val requestedHeight = maxHeightPx.coerceAtLeast(1)

        val descriptorBitmap =
            runCatching {
                appContext.contentResolver.openFileDescriptor(imageFile.uri, "r")?.use { descriptor ->
                    val fileDescriptor = descriptor.fileDescriptor
                    Os.lseek(fileDescriptor, 0L, OsConstants.SEEK_SET)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFileDescriptor(fileDescriptor, null, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
                    Os.lseek(fileDescriptor, 0L, OsConstants.SEEK_SET)
                    val orientation =
                        normalizeExifOrientation(
                            ExifInterface(fileDescriptor).getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL,
                            ),
                        )
                    val swapsDimensions = orientationSwapsDimensions(orientation)
                    val displayWidth = if (swapsDimensions) bounds.outHeight else bounds.outWidth
                    val displayHeight = if (swapsDimensions) bounds.outWidth else bounds.outHeight
                    val options =
                        BitmapFactory.Options().apply {
                            inSampleSize =
                                calculateInSampleSize(
                                    displayWidth,
                                    displayHeight,
                                    requestedWidth,
                                    requestedHeight,
                                )
                        }
                    Os.lseek(fileDescriptor, 0L, OsConstants.SEEK_SET)
                    BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options)
                        ?.let { applyExifOrientation(it, orientation) }
                }
            }.getOrNull()
        if (descriptorBitmap != null) return descriptorBitmap

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(imageFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val orientation =
            runCatching {
                appContext.contentResolver.openInputStream(imageFile.uri)?.use { input ->
                    normalizeExifOrientation(
                        ExifInterface(input).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL,
                        ),
                    )
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val swapsDimensions = orientationSwapsDimensions(orientation)
        val displayWidth = if (swapsDimensions) bounds.outHeight else bounds.outWidth
        val displayHeight = if (swapsDimensions) bounds.outWidth else bounds.outHeight
        val options =
            BitmapFactory.Options().apply {
                inSampleSize =
                    calculateInSampleSize(
                        displayWidth,
                        displayHeight,
                        requestedWidth,
                        requestedHeight,
                    )
            }
        return appContext.contentResolver.openInputStream(imageFile.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)?.let { applyExifOrientation(it, orientation) }
        }
    }

    private fun normalizeExifOrientation(orientation: Int): Int =
        orientation.takeIf { it in ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270 }
            ?: ExifInterface.ORIENTATION_NORMAL

    private fun orientationSwapsDimensions(orientation: Int): Boolean =
        orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE

    private fun applyExifOrientation(
        bitmap: Bitmap,
        orientation: Int,
    ): Bitmap {
        val matrix =
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
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { oriented ->
            if (oriented !== bitmap) bitmap.recycle()
        }
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        requestedWidth: Int,
        requestedHeight: Int,
    ): Int {
        var sample = 1
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sample >= requestedWidth || halfHeight / sample >= requestedHeight) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun imageReferenceCandidates(
        currentFolder: String,
        cleanReference: String,
    ): List<String> {
        val current = normalizePath(currentFolder)
        return listOf(
            joinPath(current, cleanReference),
            cleanReference,
            joinPath(current, "attachments/$cleanReference"),
            joinPath(current, "附件/$cleanReference"),
            "attachments/$cleanReference",
            "附件/$cleanReference",
        ).map(::normalizePath).distinct()
    }

    private fun findFolder(
        root: DocumentFile,
        folderPath: String,
    ): DocumentFile? {
        var current = root
        normalizePath(folderPath)
            .split('/')
            .filter { it.isNotBlank() }
            .forEach { part ->
                current = current.findFile(part)?.takeIf { it.isDirectory } ?: return null
            }
        return current
    }

    private fun safeIsFile(file: DocumentFile): Boolean = runCatching { file.isFile }.getOrDefault(false)

    private fun joinPath(parent: String, child: String): String = when {
        parent.isBlank() -> child
        child.isBlank() -> parent
        else -> "${parent.trimEnd('/')}/${child.trimStart('/')}"
    }

    private fun normalizePath(path: String): String {
        val stack = mutableListOf<String>()
        path.replace('\\', '/').split('/').filter { it.isNotBlank() }.forEach { part ->
            when (part) {
                "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack += part
            }
        }
        return stack.joinToString("/")
    }

    private companion object {
        private const val BITMAP_CACHE_BYTES = 16 * 1024 * 1024
        private const val RESOLVED_URI_CACHE_SIZE = 256

        private val bitmapCache =
            object : LruCache<String, Bitmap>(BITMAP_CACHE_BYTES) {
                override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount.coerceAtLeast(1)
            }
        private val resolvedUriCache = LruCache<String, Uri>(RESOLVED_URI_CACHE_SIZE)
        private val bitmapDecodeLocks = ConcurrentHashMap<String, Any>()
    }
}
