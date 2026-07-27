package com.kangle.kardleaf.ui

import com.google.gson.JsonParser
import kotlin.math.min

internal data class DrawingDocumentInfo(
    val version: Int,
    val documentType: String,
    val coordinateSpace: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val strokeCount: Int,
    val sourceReference: String?,
)

internal sealed interface DrawingSidecarClassification {
    data object None : DrawingSidecarClassification
    data class Valid(val document: DrawingDocumentInfo) : DrawingSidecarClassification
    data class Invalid(val error: DrawingSidecarError) : DrawingSidecarClassification
}

internal enum class DrawingSidecarError {
    Malformed,
    UnsupportedVersion,
    Incomplete,
}

internal enum class ImageReplacementHistorySource { Native, External, Detached, None }

internal fun selectImageReplacementHistorySource(
    attachedAvailable: Boolean,
    externalAvailable: Boolean,
    detachedMatchesCurrent: Boolean,
): ImageReplacementHistorySource =
    when {
        attachedAvailable -> ImageReplacementHistorySource.Native
        detachedMatchesCurrent -> ImageReplacementHistorySource.Detached
        externalAvailable -> ImageReplacementHistorySource.External
        else -> ImageReplacementHistorySource.None
    }

/** Only the dedicated KardLeaf sidecar may use v2; legacy files must be explicit drawing payloads. */
internal fun classifyDrawingSidecar(source: String?, isKardLeafSidecar: Boolean): DrawingSidecarClassification {
    if (source.isNullOrBlank()) return DrawingSidecarClassification.None
    val version =
        runCatching { JsonParser.parseString(source).asJsonObject.get("version")?.asInt ?: 1 }
            .getOrElse { return DrawingSidecarClassification.Invalid(DrawingSidecarError.Malformed) }
    if (version !in 1..2) return DrawingSidecarClassification.Invalid(DrawingSidecarError.UnsupportedVersion)
    val document = inspectDrawingDocument(source) ?: return DrawingSidecarClassification.Invalid(DrawingSidecarError.Malformed)
    val isLegacyDrawing = document.version == 1 && source.contains("\"strokes\"")
    val isCompleteV2 =
        document.documentType in setOf("drawing", "imageAnnotation") &&
            document.coordinateSpace.isNotBlank() &&
            document.canvasWidth > 0 && document.canvasHeight > 0 &&
            source.contains("\"strokes\"") &&
            (document.documentType != "imageAnnotation" || !document.sourceReference.isNullOrBlank())
    return when {
        !isKardLeafSidecar && !isLegacyDrawing -> DrawingSidecarClassification.None
        document.version !in 1..2 -> DrawingSidecarClassification.Invalid(DrawingSidecarError.UnsupportedVersion)
        document.version == 1 && isLegacyDrawing -> DrawingSidecarClassification.Valid(document)
        isCompleteV2 -> DrawingSidecarClassification.Valid(document)
        else -> DrawingSidecarClassification.Invalid(DrawingSidecarError.Incomplete)
    }
}

internal fun inspectDrawingDocument(source: String?): DrawingDocumentInfo? =
    runCatching {
        if (source.isNullOrBlank()) return@runCatching null
        val json = JsonParser.parseString(source).asJsonObject
        val version = json.get("version")?.asInt ?: 1
        if (version !in 1..2) return@runCatching null
        val canvas = json.getAsJsonObject("canvas")
        val background = json.getAsJsonObject("background")
        DrawingDocumentInfo(
            version = version,
            documentType = if (version == 1) "drawing" else json.get("documentType")?.asString ?: "drawing",
            coordinateSpace = if (version == 1) "canvas" else json.get("coordinateSpace")?.asString ?: "canvas",
            canvasWidth = canvas?.get("width")?.asInt ?: json.get("canvasWidth")?.asInt ?: 0,
            canvasHeight = canvas?.get("height")?.asInt ?: json.get("canvasHeight")?.asInt ?: 0,
            strokeCount = json.getAsJsonArray("strokes")?.size() ?: 0,
            sourceReference = background?.get("sourceReference")?.asString?.takeIf { it.isNotBlank() },
        )
    }.getOrNull()

internal data class FitCenterTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun sourceToScreen(
        x: Float,
        y: Float,
    ): Pair<Float, Float> =
        (offsetX + x * scale) to (offsetY + y * scale)

    fun screenToSource(
        x: Float,
        y: Float,
    ): Pair<Float, Float> =
        ((x - offsetX) / scale) to ((y - offsetY) / scale)
}

internal fun calculateFitCenterTransform(
    sourceWidth: Int,
    sourceHeight: Int,
    viewWidth: Int,
    viewHeight: Int,
    zoom: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
): FitCenterTransform {
    val safeSourceWidth = sourceWidth.coerceAtLeast(1)
    val safeSourceHeight = sourceHeight.coerceAtLeast(1)
    val safeViewWidth = viewWidth.coerceAtLeast(1)
    val safeViewHeight = viewHeight.coerceAtLeast(1)
    val fitScale =
        min(
            safeViewWidth.toFloat() / safeSourceWidth,
            safeViewHeight.toFloat() / safeSourceHeight,
        )
    val scale = fitScale * zoom.coerceAtLeast(1f)
    val displayWidth = safeSourceWidth * scale
    val displayHeight = safeSourceHeight * scale
    return FitCenterTransform(
        scale = scale,
        offsetX = (safeViewWidth - displayWidth) / 2f + panX,
        offsetY = (safeViewHeight - displayHeight) / 2f + panY,
    )
}

internal class DrawingSnapshotHistory<T> {
    private val past = mutableListOf<List<T>>()
    private val future = mutableListOf<List<T>>()
    private var restoredItems: List<T>? = null
    private var restoredUndoCursor = 0

    val undoCount: Int get() = past.size + restoredUndoCursor
    val redoCount: Int get() = future.size

    fun clear() {
        past.clear()
        future.clear()
        restoredItems = null
        restoredUndoCursor = 0
    }

    fun initializeRestored(items: List<T>) {
        clear()
        // Keep a single immutable baseline instead of copying every restored prefix.
        restoredItems = items.toList()
        restoredUndoCursor = items.size
    }

    fun recordBefore(snapshot: List<T>) {
        restoredItems = null
        restoredUndoCursor = 0
        past += snapshot.toList()
        future.clear()
    }

    fun undo(current: List<T>): List<T>? {
        if (past.isEmpty()) {
            val restored = restoredItems ?: return null
            if (restoredUndoCursor == 0) return null
            future += current.toList()
            restoredUndoCursor--
            return restored.take(restoredUndoCursor)
        }
        future += current.toList()
        return past.removeAt(past.lastIndex)
    }

    fun redo(current: List<T>): List<T>? {
        if (future.isEmpty()) return null
        past += current.toList()
        return future.removeAt(future.lastIndex)
    }
}

internal fun uniqueAnnotationBaseName(
    seed: String,
    isTaken: (String) -> Boolean,
): String {
    var candidate = seed
    var suffix = 1
    while (isTaken(candidate)) {
        candidate = "$seed-$suffix"
        suffix++
    }
    return candidate
}
