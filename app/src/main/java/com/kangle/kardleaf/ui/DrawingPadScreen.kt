package com.kangle.kardleaf.ui

import com.kangle.kardleaf.data.utils.KardLeafLog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.Preview
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

private enum class DrawingTool(val label: String) {
    Pen("画笔"),
    Highlighter("荧光笔"),
    AreaEraser("区域橡皮"),
    StrokeEraser("整笔擦除"),
}

private enum class DrawingGrid(val label: String) {
    None("空白"),
    Square("方格"),
    Rule("横线"),
    Dot("点阵"),
}

private const val DrawingPadLogTag = "KardLeafDrawingPad"

@Composable
internal fun DrawingPadScreen(
    onDismiss: () -> Unit,
    onSave: (Bitmap, String) -> Unit,
    modifier: Modifier = Modifier,
    initialDrawingSource: String? = null,
    initialBackgroundBitmap: Bitmap? = null,
    initialSourceWidth: Int = 0,
    initialSourceHeight: Int = 0,
    initialBackgroundMimeType: String? = null,
    initialExifOrientation: Int = 1,
) {
    val restoredDrawingState = remember(initialDrawingSource) { parseDrawingPadSource(initialDrawingSource) }
    var drawingView by remember { mutableStateOf<KardLeafDrawingPadView?>(null) }
    var tool by remember { mutableStateOf(DrawingTool.Pen) }
    var grid by remember { mutableStateOf(restoredDrawingState?.grid ?: DrawingGrid.Square) }
    var canvasColor by remember { mutableStateOf(Color(restoredDrawingState?.canvasColor ?: AndroidColor.WHITE)) }
    var penColor by remember { mutableStateOf(Color(restoredDrawingState?.penColor ?: AndroidColor.BLACK)) }
    var highlighterColor by remember { mutableStateOf(Color(restoredDrawingState?.highlighterColor ?: AndroidColor.YELLOW)) }
    val sourceWidthScale = (initialSourceWidth.takeIf { it > 0 }?.toFloat() ?: 1080f) / 1080f
    var penStrokeWidth by remember { mutableStateOf(restoredDrawingState?.penStrokeWidth ?: (7f * sourceWidthScale)) }
    var highlighterStrokeWidth by remember { mutableStateOf(restoredDrawingState?.highlighterStrokeWidth ?: (18f * sourceWidthScale)) }
    var eraserStrokeWidth by remember { mutableStateOf(restoredDrawingState?.eraserStrokeWidth ?: (28f * sourceWidthScale)) }

    val saveButtonAccent = MaterialTheme.colorScheme.primary

    val activeWidth = when (tool) {
        DrawingTool.Pen -> penStrokeWidth
        DrawingTool.Highlighter -> highlighterStrokeWidth
        DrawingTool.AreaEraser,
        DrawingTool.StrokeEraser -> eraserStrokeWidth
    }

    fun updateActiveWidth(value: Float) {
        when (tool) {
            DrawingTool.Pen -> penStrokeWidth = value
            DrawingTool.Highlighter -> highlighterStrokeWidth = value
            DrawingTool.AreaEraser,
            DrawingTool.StrokeEraser -> eraserStrokeWidth = value
        }
    }

    KardLeafLog.d(
        DrawingPadLogTag,
        "compose drawing screen tool=$tool grid=$grid canvasColor=${canvasColor.toArgb()} penColor=${penColor.toArgb()} activeWidth=$activeWidth drawingViewReady=${drawingView != null}",
    )

    fun saveDrawing(source: String) {
        val view = drawingView
        KardLeafLog.d(DrawingPadLogTag, "$source save clicked drawingViewReady=${view != null}")
        val bitmap = view?.exportBitmap()
        val drawingSource = view?.exportDrawingSource().orEmpty()
        KardLeafLog.d(DrawingPadLogTag, "$source export bitmap result=${bitmap?.width}x${bitmap?.height} sourceLen=${drawingSource.length}")
        if (bitmap != null && drawingSource.isNotBlank()) {
            onSave(bitmap, drawingSource)
        }
    }

    BackHandler {
        KardLeafLog.d(DrawingPadLogTag, "back pressed close drawing screen")
        onDismiss()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
                    .onGloballyPositioned { coordinates ->
                        KardLeafLog.d(
                            DrawingPadLogTag,
                            "top bar positioned size=${coordinates.size.width}x${coordinates.size.height} window=${coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero)}",
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = {
                    KardLeafLog.d(DrawingPadLogTag, "top back button clicked")
                    onDismiss()
                }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭画图")
                }
                Text(
                    text = if (initialBackgroundBitmap != null || restoredDrawingState?.documentType == "imageAnnotation") "图片标注" else "绘图",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .onGloballyPositioned { coordinates ->
                            KardLeafLog.d(
                                DrawingPadLogTag,
                                "save button positioned size=${coordinates.size.width}x${coordinates.size.height} window=${coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero)}",
                            )
                        }
                        .clickable { saveDrawing("top") },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = saveButtonAccent,
                    border = BorderStroke(1.5.dp, saveButtonAccent),
                ) {
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = "保存画图",
                            tint = saveButtonAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "保存",
                            color = saveButtonAccent,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            HorizontalDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        KardLeafLog.d(DrawingPadLogTag, "AndroidView factory create drawing view")
                        KardLeafDrawingPadView(context).also { view ->
                            drawingView = view
                            view.configureImageBackground(
                                bitmap = initialBackgroundBitmap,
                                sourceWidth = initialSourceWidth,
                                sourceHeight = initialSourceHeight,
                                mimeType = initialBackgroundMimeType,
                                exifOrientation = initialExifOrientation,
                            )
                            view.restoreDrawingSourceState(restoredDrawingState)
                            view.applyState(
                                tool = tool,
                                grid = grid,
                                canvasColor = canvasColor.toArgb(),
                                penColor = penColor.toArgb(),
                                highlighterColor = highlighterColor.toArgb(),
                                penStrokeWidth = penStrokeWidth,
                                highlighterStrokeWidth = highlighterStrokeWidth,
                                eraserStrokeWidth = eraserStrokeWidth,
                            )
                        }
                    },
                    update = { view ->
                        KardLeafLog.d(DrawingPadLogTag, "AndroidView update drawing view width=${view.width} height=${view.height}")
                        drawingView = view
                        view.configureImageBackground(
                            bitmap = initialBackgroundBitmap,
                            sourceWidth = initialSourceWidth,
                            sourceHeight = initialSourceHeight,
                            mimeType = initialBackgroundMimeType,
                            exifOrientation = initialExifOrientation,
                        )
                        view.applyState(
                            tool = tool,
                            grid = grid,
                            canvasColor = canvasColor.toArgb(),
                            penColor = penColor.toArgb(),
                            highlighterColor = highlighterColor.toArgb(),
                            penStrokeWidth = penStrokeWidth,
                            highlighterStrokeWidth = highlighterStrokeWidth,
                            eraserStrokeWidth = eraserStrokeWidth,
                        )
                    },
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .onGloballyPositioned { coordinates ->
                            KardLeafLog.d(
                                DrawingPadLogTag,
                                "floating save positioned size=${coordinates.size.width}x${coordinates.size.height} window=${coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero)}",
                            )
                        }
                        .clickable { saveDrawing("floating") },
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = saveButtonAccent,
                    border = BorderStroke(2.dp, saveButtonAccent),
                ) {
                    Row(
                        modifier = Modifier
                            .height(42.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Save,
                            contentDescription = "保存画图",
                            tint = saveButtonAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "保存",
                            color = saveButtonAccent,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            HorizontalDivider()
            DrawingPadControls(
                tool = tool,
                onToolChange = { tool = it },
                grid = grid,
                onGridChange = { grid = it },
                canvasColor = canvasColor,
                onCanvasColorChange = { canvasColor = it },
                penColor = penColor,
                onPenColorChange = {
                    penColor = it
                    tool = DrawingTool.Pen
                },
                highlighterColor = highlighterColor,
                onHighlighterColorChange = {
                    highlighterColor = it
                    tool = DrawingTool.Highlighter
                },
                activeWidth = activeWidth,
                onActiveWidthChange = ::updateActiveWidth,
                onUndo = { drawingView?.undo() },
                onRedo = { drawingView?.redo() },
                onClear = { drawingView?.clear() },
            )
        }
    }
}

@Composable
private fun DrawingPadControls(
    tool: DrawingTool,
    onToolChange: (DrawingTool) -> Unit,
    grid: DrawingGrid,
    onGridChange: (DrawingGrid) -> Unit,
    canvasColor: Color,
    onCanvasColorChange: (Color) -> Unit,
    penColor: Color,
    onPenColorChange: (Color) -> Unit,
    highlighterColor: Color,
    onHighlighterColorChange: (Color) -> Unit,
    activeWidth: Float,
    onActiveWidthChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onUndo) {
                Icon(Icons.Outlined.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = onRedo) {
                Icon(Icons.Outlined.Redo, contentDescription = "恢复")
            }
            TextButton(onClick = onClear) {
                Text("清空")
            }
            Spacer(Modifier.weight(1f))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawingOptionChip("画笔", tool == DrawingTool.Pen) { onToolChange(DrawingTool.Pen) }
            DrawingOptionChip("荧光笔", tool == DrawingTool.Highlighter) { onToolChange(DrawingTool.Highlighter) }
            DrawingOptionChip("区域橡皮", tool == DrawingTool.AreaEraser) { onToolChange(DrawingTool.AreaEraser) }
            DrawingOptionChip("整笔擦除", tool == DrawingTool.StrokeEraser) { onToolChange(DrawingTool.StrokeEraser) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("网格", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DrawingGrid.values().forEach { item ->
                DrawingOptionChip(item.label, grid == item) { onGridChange(item) }
            }
            Spacer(Modifier.width(4.dp))
            Text("背景", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(Color.White, Color(0xFFFFFBF0), Color(0xFFF7F7F7), Color(0xFF242424)).forEach { color ->
                DrawingColorDot(
                    color = color,
                    selected = canvasColor == color,
                    borderColor = MaterialTheme.colorScheme.outline,
                    onClick = { onCanvasColorChange(color) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val colors = listOf(
                Color.Black,
                Color(0xFFE53935),
                Color(0xFF1E88E5),
                Color(0xFF43A047),
                Color(0xFFFDD835),
                Color(0xFF8E24AA),
                Color(0xFFFF8F00),
            )
            Icon(
                imageVector = if (tool == DrawingTool.Highlighter) Icons.Outlined.Edit else Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            colors.forEach { color ->
                DrawingColorDot(
                    color = color,
                    selected = if (tool == DrawingTool.Highlighter) highlighterColor == color else penColor == color,
                    borderColor = MaterialTheme.colorScheme.outline,
                    onClick = {
                        if (tool == DrawingTool.Highlighter) {
                            onHighlighterColorChange(color)
                        } else {
                            onPenColorChange(color)
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "粗细 ${activeWidth.toInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = activeWidth,
                onValueChange = onActiveWidthChange,
                valueRange = 2f..180f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DrawingOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DrawingColorDot(
    color: Color,
    selected: Boolean,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (selected) 30.dp else 24.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else borderColor,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

@Preview(
    name = "绘图界面",
    widthDp = 393,
    heightDp = 852,
    showBackground = true,
)
@Composable
private fun DrawingPadScreenPreview() {
    MaterialTheme {
        DrawingPadScreen(
            onDismiss = {},
            onSave = { _, _ -> },
        )
    }
}

private data class DrawingPadSourceState(
    val version: Int,
    val documentType: String,
    val coordinateSpace: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val grid: DrawingGrid,
    val canvasColor: Int,
    val penColor: Int,
    val highlighterColor: Int,
    val penStrokeWidth: Float,
    val highlighterStrokeWidth: Float,
    val eraserStrokeWidth: Float,
    val backgroundSourceReference: String?,
    val backgroundMimeType: String?,
    val exifOrientation: Int,
    val strokes: List<DrawingPadSourceStroke>,
)

private data class DrawingPadSourceStroke(
    val tool: DrawingTool,
    val color: Int,
    val width: Float,
    val points: List<PointF>,
)

private fun parseDrawingPadSource(source: String?): DrawingPadSourceState? =
    runCatching {
        if (source.isNullOrBlank()) return@runCatching null
        val json = JSONObject(source)
        val version = json.optInt("version", 1)
        if (version !in 1..2) return@runCatching null
        val canvas = json.optJSONObject("canvas")
        val background = json.optJSONObject("background")
        val toolState = json.optJSONObject("toolState")
        val strokesJson = json.optJSONArray("strokes") ?: JSONArray()
        val strokes = buildList {
            for (index in 0 until strokesJson.length()) {
                val strokeJson = strokesJson.optJSONObject(index) ?: continue
                val pointsJson = strokeJson.optJSONArray("points") ?: JSONArray()
                val points = buildList {
                    for (pointIndex in 0 until pointsJson.length()) {
                        val pointJson = pointsJson.optJSONObject(pointIndex) ?: continue
                        add(PointF(
                            pointJson.optDouble("x").toFloat(),
                            pointJson.optDouble("y").toFloat(),
                        ))
                    }
                }
                if (points.isNotEmpty()) {
                    add(
                        DrawingPadSourceStroke(
                            tool = parseDrawingTool(strokeJson.optString("tool"), DrawingTool.Pen),
                            color = strokeJson.optInt("color", AndroidColor.BLACK),
                            width = strokeJson.optDouble("width", 7.0).toFloat().coerceIn(2f, 180f),
                            points = points,
                        ),
                    )
                }
            }
        }
        DrawingPadSourceState(
            version = version,
            documentType = if (version == 1) "drawing" else json.optString("documentType", "drawing"),
            coordinateSpace = if (version == 1) "canvas" else json.optString("coordinateSpace", "canvas"),
            canvasWidth = canvas?.optInt("width", 0) ?: json.optInt("canvasWidth", 0),
            canvasHeight = canvas?.optInt("height", 0) ?: json.optInt("canvasHeight", 0),
            grid = parseDrawingGrid(background?.optString("grid") ?: json.optString("grid"), DrawingGrid.Square),
            canvasColor = background?.optInt("color", AndroidColor.WHITE) ?: json.optInt("canvasColor", AndroidColor.WHITE),
            penColor = toolState?.optInt("penColor", AndroidColor.BLACK) ?: json.optInt("penColor", AndroidColor.BLACK),
            highlighterColor =
                toolState?.optInt("highlighterColor", AndroidColor.YELLOW)
                    ?: json.optInt("highlighterColor", AndroidColor.YELLOW),
            penStrokeWidth =
                (
                    toolState?.optDouble("penStrokeWidth", 7.0)
                        ?: json.optDouble("penStrokeWidth", 7.0)
                ).toFloat().coerceIn(2f, 180f),
            highlighterStrokeWidth =
                (
                    toolState?.optDouble("highlighterStrokeWidth", 18.0)
                        ?: json.optDouble("highlighterStrokeWidth", 18.0)
                ).toFloat().coerceIn(2f, 180f),
            eraserStrokeWidth =
                (
                    toolState?.optDouble("eraserStrokeWidth", 28.0)
                        ?: json.optDouble("eraserStrokeWidth", 28.0)
                ).toFloat().coerceIn(2f, 180f),
            backgroundSourceReference = background?.optString("sourceReference")?.takeIf { it.isNotBlank() },
            backgroundMimeType = background?.optString("mimeType")?.takeIf { it.isNotBlank() },
            exifOrientation = background?.optInt("exifOrientation", 1) ?: 1,
            strokes = strokes,
        )
    }.getOrNull()

private fun parseDrawingTool(value: String?, fallback: DrawingTool): DrawingTool =
    runCatching { DrawingTool.valueOf(value.orEmpty()) }.getOrDefault(fallback)

private fun parseDrawingGrid(value: String?, fallback: DrawingGrid): DrawingGrid =
    runCatching { DrawingGrid.valueOf(value.orEmpty()) }.getOrDefault(fallback)

private fun buildDrawingPath(points: List<PointF>): Path =
    Path().apply {
        val first = points.firstOrNull() ?: return@apply
        moveTo(first.x, first.y)
        var lastX = first.x
        var lastY = first.y
        points.drop(1).forEach { point ->
            quadTo(lastX, lastY, (lastX + point.x) / 2f, (lastY + point.y) / 2f)
            lastX = point.x
            lastY = point.y
        }
        lineTo(lastX, lastY)
    }

private data class DrawingStroke(
    val path: Path,
    val points: List<PointF>,
    val tool: DrawingTool,
    val color: Int,
    val width: Float,
)

private class KardLeafDrawingPadView(context: Context) : View(context) {
    private val strokes = mutableListOf<DrawingStroke>()
    private val history = DrawingSnapshotHistory<DrawingStroke>()
    private var currentPath: Path? = null
    private val currentPoints = mutableListOf<PointF>()
    private var tool: DrawingTool = DrawingTool.Pen
    private var grid: DrawingGrid = DrawingGrid.Square
    private var canvasColor: Int = AndroidColor.WHITE
    private var penColor: Int = AndroidColor.BLACK
    private var highlighterColor: Int = AndroidColor.YELLOW
    private var penStrokeWidth: Float = 7f
    private var highlighterStrokeWidth: Float = 18f
    private var eraserStrokeWidth: Float = 28f
    private var lastX = 0f
    private var lastY = 0f
    private var pendingRestoreState: DrawingPadSourceState? = null
    private var documentType = "drawing"
    private var coordinateSpace = "canvas"
    private var backgroundBitmap: Bitmap? = null
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var backgroundMimeType: String? = null
    private var backgroundSourceReference: String? = null
    private var exifOrientation = 1
    private var imageScale = 1f
    private var imageOffsetX = 0f
    private var imageOffsetY = 0f
    private var lastScaleFocusX = 0f
    private var lastScaleFocusY = 0f
    private var eraserSessionBefore: List<DrawingStroke>? = null
    private var eraserSessionChanged = false

    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    cancelCurrentStroke()
                    lastScaleFocusX = detector.focusX
                    lastScaleFocusY = detector.focusY
                    return isImageAnnotation()
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (!isImageAnnotation()) return false
                    val sourceAtFocus = screenToSource(detector.focusX, detector.focusY)
                    val nextScale = (imageScale * detector.scaleFactor).coerceIn(1f, 5f)
                    imageScale = nextScale
                    val baseScale = annotationBaseScale()
                    val scaledWidth = sourceWidth * baseScale * imageScale
                    val scaledHeight = sourceHeight * baseScale * imageScale
                    imageOffsetX = detector.focusX - ((width - scaledWidth) / 2f + sourceAtFocus.x * baseScale * imageScale)
                    imageOffsetY = detector.focusY - ((height - scaledHeight) / 2f + sourceAtFocus.y * baseScale * imageScale)
                    imageOffsetX += detector.focusX - lastScaleFocusX
                    imageOffsetY += detector.focusY - lastScaleFocusY
                    lastScaleFocusX = detector.focusX
                    lastScaleFocusY = detector.focusY
                    clampImageOffset()
                    invalidate()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    KardLeafLog.d(
                        DrawingPadLogTag,
                        "annotation transform scale=$imageScale offset=$imageOffsetX,$imageOffsetY",
                    )
                }
            },
        )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(42, 140, 140, 140)
        strokeWidth = 1f
    }
    private val areaEraseMode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        KardLeafLog.d(DrawingPadLogTag, "drawing view attached width=$width height=$height")
    }

    override fun onDetachedFromWindow() {
        KardLeafLog.d(DrawingPadLogTag, "drawing view detached width=$width height=$height strokes=${strokes.size}")
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        KardLeafLog.d(DrawingPadLogTag, "drawing view size changed ${oldw}x$oldh -> ${w}x$h")
        applyPendingRestoreStateIfNeeded()
        clampImageOffset()
    }

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun configureImageBackground(
        bitmap: Bitmap?,
        sourceWidth: Int,
        sourceHeight: Int,
        mimeType: String?,
        exifOrientation: Int,
    ) {
        if (bitmap === backgroundBitmap && this.sourceWidth == sourceWidth && this.sourceHeight == sourceHeight) return
        backgroundBitmap = bitmap
        if (bitmap != null && sourceWidth > 0 && sourceHeight > 0) {
            documentType = "imageAnnotation"
            coordinateSpace = "sourceImagePixels"
            this.sourceWidth = sourceWidth
            this.sourceHeight = sourceHeight
            backgroundMimeType = mimeType
            this.exifOrientation = exifOrientation
        }
        invalidate()
    }

    fun restoreDrawingSourceState(state: DrawingPadSourceState?) {
        strokes.clear()
        history.clear()
        currentPath = null
        currentPoints.clear()
        pendingRestoreState = state
        applyPendingRestoreStateIfNeeded()
        invalidate()
    }

    private fun applyPendingRestoreStateIfNeeded() {
        val state = pendingRestoreState ?: return
        if (width <= 0 || height <= 0) return
        documentType = state.documentType
        coordinateSpace = state.coordinateSpace
        if (state.documentType == "imageAnnotation") {
            sourceWidth = state.canvasWidth.coerceAtLeast(sourceWidth)
            sourceHeight = state.canvasHeight.coerceAtLeast(sourceHeight)
            backgroundMimeType = state.backgroundMimeType ?: backgroundMimeType
            backgroundSourceReference = state.backgroundSourceReference
            exifOrientation = state.exifOrientation
        }
        val scaleX =
            if (state.documentType != "imageAnnotation" && state.canvasWidth > 0) {
                width.toFloat() / state.canvasWidth.toFloat()
            } else {
                1f
            }
        val scaleY =
            if (state.documentType != "imageAnnotation" && state.canvasHeight > 0) {
                height.toFloat() / state.canvasHeight.toFloat()
            } else {
                1f
            }
        strokes.clear()
        strokes.addAll(
            state.strokes.map { stroke ->
                val scaledPoints = stroke.points.map { point -> PointF(point.x * scaleX, point.y * scaleY) }
                DrawingStroke(
                    path = buildDrawingPath(scaledPoints),
                    points = scaledPoints,
                    tool = stroke.tool,
                    color = stroke.color,
                    width = stroke.width * ((scaleX + scaleY) / 2f),
                )
            },
        )
        history.initializeRestored(strokes)
        pendingRestoreState = null
        KardLeafLog.d(
            DrawingPadLogTag,
            "restore version=${state.version} type=${state.documentType} strokes=${strokes.size} undo=${history.undoCount}",
        )
        invalidate()
    }

    fun applyState(
        tool: DrawingTool,
        grid: DrawingGrid,
        canvasColor: Int,
        penColor: Int,
        highlighterColor: Int,
        penStrokeWidth: Float,
        highlighterStrokeWidth: Float,
        eraserStrokeWidth: Float,
    ) {
        this.tool = tool
        this.grid = grid
        this.canvasColor = canvasColor
        this.penColor = penColor
        this.highlighterColor = highlighterColor
        this.penStrokeWidth = penStrokeWidth.coerceIn(2f, 180f)
        this.highlighterStrokeWidth = highlighterStrokeWidth.coerceIn(2f, 180f)
        this.eraserStrokeWidth = eraserStrokeWidth.coerceIn(2f, 180f)
        invalidate()
    }

    fun undo() {
        logHistory("undo-before")
        restoreSnapshot(history.undo(snapshotStrokes()) ?: return)
        logHistory("undo-after")
    }

    fun redo() {
        logHistory("redo-before")
        restoreSnapshot(history.redo(snapshotStrokes()) ?: return)
        logHistory("redo-after")
    }

    fun clear() {
        logHistory("clear-before")
        if (strokes.isEmpty()) return
        recordBeforeMutation()
        strokes.clear()
        currentPath = null
        currentPoints.clear()
        invalidate()
        logHistory("clear-after")
    }

    fun exportBitmap(): Bitmap {
        if (isImageAnnotation()) {
            // Repository re-decodes immutable source and renders the final export on Dispatchers.IO.
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val (safeWidth, safeHeight) =
            width.coerceAtLeast(1) to height.coerceAtLeast(1)
        KardLeafLog.d(
            DrawingPadLogTag,
            "exportBitmap requested view=${width}x$height export=${safeWidth}x$safeHeight strokes=${strokes.size}",
        )
        return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            if (isImageAnnotation()) drawAnnotationExport(Canvas(bitmap), safeWidth, safeHeight) else drawDrawingContent(Canvas(bitmap))
        }
    }

    fun exportDrawingSource(): String {
        val canvasWidth = if (isImageAnnotation()) sourceWidth.coerceAtLeast(1) else width.coerceAtLeast(1)
        val canvasHeight = if (isImageAnnotation()) sourceHeight.coerceAtLeast(1) else height.coerceAtLeast(1)
        val root = JSONObject()
            .put("version", 2)
            .put("documentType", if (isImageAnnotation()) "imageAnnotation" else "drawing")
            .put("coordinateSpace", if (isImageAnnotation()) "sourceImagePixels" else "canvas")
            .put("canvas", JSONObject().put("width", canvasWidth).put("height", canvasHeight))
            .put(
                "background",
                if (isImageAnnotation()) {
                    JSONObject()
                        .put("type", "image")
                        .put("sourceReference", backgroundSourceReference.orEmpty())
                        .put("mimeType", backgroundMimeType.orEmpty())
                        .put("sourceWidth", sourceWidth)
                        .put("sourceHeight", sourceHeight)
                        .put("exifOrientation", exifOrientation)
                } else {
                    JSONObject()
                        .put("type", "grid")
                        .put("color", canvasColor)
                        .put("grid", grid.name)
                },
            )
            .put(
                "toolState",
                JSONObject()
                    .put("penColor", penColor)
                    .put("highlighterColor", highlighterColor)
                    .put("penStrokeWidth", penStrokeWidth)
                    .put("highlighterStrokeWidth", highlighterStrokeWidth)
                    .put("eraserStrokeWidth", eraserStrokeWidth),
            )
        val strokesJson = JSONArray()
        strokes.forEach { stroke ->
            val pointsJson = JSONArray()
            stroke.points.forEach { point ->
                pointsJson.put(JSONObject().put("x", point.x).put("y", point.y))
            }
            strokesJson.put(
                JSONObject()
                    .put("tool", stroke.tool.name)
                    .put("color", stroke.color)
                    .put("width", stroke.width)
                    .put("points", pointsJson),
            )
        }
        root.put("strokes", strokesJson)
        return root.toString(2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isImageAnnotation()) {
            scaleDetector.onTouchEvent(event)
            if (event.pointerCount > 1 || scaleDetector.isInProgress || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                cancelCurrentStroke()
                parent.requestDisallowInterceptTouchEvent(true)
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }
        }
        val point = if (isImageAnnotation()) screenToSource(event.x, event.y) else PointF(event.x, event.y)
        val x = point.x
        val y = point.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isImageAnnotation() && !isSourcePointInside(x, y)) return true
                KardLeafLog.d(
                    DrawingPadLogTag,
                    "touch down tool=$tool screen=${event.x},${event.y} source=$x,$y scale=$imageScale offset=$imageOffsetX,$imageOffsetY",
                )
                parent.requestDisallowInterceptTouchEvent(true)
                if (tool == DrawingTool.StrokeEraser) {
                    eraserSessionBefore = snapshotStrokes()
                    eraserSessionChanged = false
                    removeStrokeNear(x, y)
                    return true
                }
                currentPath = Path().apply { moveTo(x, y) }
                currentPoints.clear()
                currentPoints.add(PointF(x, y))
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isImageAnnotation() && !isSourcePointInside(x, y)) return true
                if (tool == DrawingTool.StrokeEraser) {
                    removeStrokeNear(x, y)
                    return true
                }
                currentPath?.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
                currentPoints.add(PointF(x, y))
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                KardLeafLog.d(
                    DrawingPadLogTag,
                    "touch end action=${event.actionMasked} tool=$tool screen=${event.x},${event.y} source=$x,$y " +
                        "scale=$imageScale offset=$imageOffsetX,$imageOffsetY",
                )
                if (tool == DrawingTool.StrokeEraser) {
                    if (eraserSessionChanged) {
                        eraserSessionBefore?.let { history.recordBefore(it) }
                        logHistory("stroke-erase")
                    }
                    eraserSessionBefore = null
                    eraserSessionChanged = false
                    parent.requestDisallowInterceptTouchEvent(false)
                    invalidate()
                    return true
                }
                if (tool != DrawingTool.StrokeEraser) {
                    currentPath?.let { path ->
                        recordBeforeMutation()
                        path.lineTo(x, y)
                        currentPoints.add(PointF(x, y))
                        strokes.add(
                            DrawingStroke(
                                path = Path(path),
                                points = currentPoints.map { PointF(it.x, it.y) },
                                tool = tool,
                                color = currentStrokeColor(),
                                width = currentStrokeWidth(),
                            ),
                        )
                    }
                }
                currentPath = null
                currentPoints.clear()
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawDrawingContent(canvas)
    }

    private fun drawDrawingContent(canvas: Canvas) {
        if (isImageAnnotation()) {
            drawAnnotationContent(canvas)
            return
        }
        drawCanvasBackground(canvas)

        // Draw strokes on a transparent layer so the area eraser only clears ink.
        // The grid/background stays below this layer and is never erased.
        val layer = canvas.saveLayer(
            0f,
            0f,
            width.coerceAtLeast(1).toFloat(),
            height.coerceAtLeast(1).toFloat(),
            null,
        )
        strokes.forEach { stroke -> drawStroke(canvas, stroke) }
        currentPath?.let { path ->
            drawStroke(
                canvas = canvas,
                stroke = DrawingStroke(
                    path = path,
                    points = currentPoints,
                    tool = tool,
                    color = currentStrokeColor(),
                    width = currentStrokeWidth(),
                ),
            )
        }
        canvas.restoreToCount(layer)
    }

    private fun drawAnnotationContent(canvas: Canvas) {
        canvas.drawColor(AndroidColor.BLACK)
        backgroundBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, annotationDestinationRect(), bitmapPaint)
        }
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        strokes.forEach { stroke -> drawAnnotationStroke(canvas, stroke, annotationDisplayScale(), annotationDestinationRect().left, annotationDestinationRect().top) }
        currentPath?.let {
            drawAnnotationStroke(
                canvas,
                DrawingStroke(Path(it), currentPoints.map { point -> PointF(point.x, point.y) }, tool, currentStrokeColor(), currentStrokeWidth()),
                annotationDisplayScale(),
                annotationDestinationRect().left,
                annotationDestinationRect().top,
            )
        }
        canvas.restoreToCount(layer)
    }

    private fun drawAnnotationExport(
        canvas: Canvas,
        exportWidth: Int,
        exportHeight: Int,
    ) {
        canvas.drawColor(AndroidColor.BLACK)
        backgroundBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, exportWidth.toFloat(), exportHeight.toFloat()), bitmapPaint)
        }
        val layer = canvas.saveLayer(0f, 0f, exportWidth.toFloat(), exportHeight.toFloat(), null)
        val exportScale = exportWidth.toFloat() / sourceWidth.coerceAtLeast(1)
        strokes.forEach { stroke -> drawAnnotationStroke(canvas, stroke, exportScale, 0f, 0f) }
        canvas.restoreToCount(layer)
    }

    private fun drawAnnotationStroke(
        canvas: Canvas,
        stroke: DrawingStroke,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        val mappedPoints = stroke.points.map { point -> PointF(offsetX + point.x * scale, offsetY + point.y * scale) }
        drawStroke(
            canvas,
            stroke.copy(
                path = buildDrawingPath(mappedPoints),
                points = mappedPoints,
                width = stroke.width * scale,
            ),
        )
    }

    private fun drawCanvasBackground(canvas: Canvas) {
        canvas.drawColor(canvasColor)
        val spacing = 48f
        when (grid) {
            DrawingGrid.None -> Unit
            DrawingGrid.Square -> {
                var x = 0f
                while (x <= width) {
                    canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                    x += spacing
                }
                var y = 0f
                while (y <= height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                    y += spacing
                }
            }
            DrawingGrid.Rule -> {
                var y = 0f
                while (y <= height) {
                    canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                    y += spacing
                }
            }
            DrawingGrid.Dot -> {
                var x = 0f
                while (x <= width) {
                    var y = 0f
                    while (y <= height) {
                        canvas.drawCircle(x, y, 2f, gridPaint)
                        y += spacing
                    }
                    x += spacing
                }
            }
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        stroke: DrawingStroke,
    ) {
        paint.xfermode = if (stroke.tool == DrawingTool.AreaEraser) areaEraseMode else null
        paint.color = stroke.color
        paint.strokeWidth = stroke.width
        paint.alpha = if (stroke.tool == DrawingTool.Highlighter) 110 else 255
        canvas.drawPath(stroke.path, paint)
        paint.xfermode = null
        paint.alpha = 255
    }

    private fun currentStrokeColor(): Int = when (tool) {
        DrawingTool.Pen -> penColor
        DrawingTool.Highlighter -> highlighterColor
        DrawingTool.AreaEraser,
        DrawingTool.StrokeEraser -> AndroidColor.TRANSPARENT
    }

    private fun currentStrokeWidth(): Float = when (tool) {
        DrawingTool.Pen -> penStrokeWidth
        DrawingTool.Highlighter -> highlighterStrokeWidth
        DrawingTool.AreaEraser,
        DrawingTool.StrokeEraser -> eraserStrokeWidth
    }

    private fun removeStrokeNear(x: Float, y: Float) {
        val index = strokes.indexOfLast { stroke -> stroke.isNear(x, y, eraserStrokeWidth) }
        if (index >= 0) {
            strokes.removeAt(index)
            eraserSessionChanged = true
            invalidate()
        }
    }

    private fun isImageAnnotation(): Boolean =
        documentType == "imageAnnotation" && backgroundBitmap != null && sourceWidth > 0 && sourceHeight > 0

    private fun annotationBaseScale(): Float =
        calculateFitCenterTransform(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            viewWidth = width,
            viewHeight = height,
        ).scale

    private fun annotationDisplayScale(): Float = annotationBaseScale() * imageScale

    private fun annotationDestinationRect(): RectF {
        val transform =
            calculateFitCenterTransform(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                viewWidth = width,
                viewHeight = height,
                zoom = imageScale,
                panX = imageOffsetX,
                panY = imageOffsetY,
            )
        val displayScale = transform.scale
        val displayWidth = sourceWidth * displayScale
        val displayHeight = sourceHeight * displayScale
        val left = transform.offsetX
        val top = transform.offsetY
        return RectF(left, top, left + displayWidth, top + displayHeight)
    }

    private fun screenToSource(
        screenX: Float,
        screenY: Float,
    ): PointF {
        val transform =
            calculateFitCenterTransform(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                viewWidth = width,
                viewHeight = height,
                zoom = imageScale,
                panX = imageOffsetX,
                panY = imageOffsetY,
            )
        val (x, y) = transform.screenToSource(screenX, screenY)
        return PointF(x, y)
    }

    private fun isSourcePointInside(
        x: Float,
        y: Float,
    ): Boolean =
        x in 0f..sourceWidth.toFloat() && y in 0f..sourceHeight.toFloat()

    private fun clampImageOffset() {
        if (!isImageAnnotation() || width <= 0 || height <= 0) return
        val displayScale = annotationDisplayScale()
        val maxX = ((sourceWidth * displayScale - width) / 2f).coerceAtLeast(0f)
        val maxY = ((sourceHeight * displayScale - height) / 2f).coerceAtLeast(0f)
        imageOffsetX = imageOffsetX.coerceIn(-maxX, maxX)
        imageOffsetY = imageOffsetY.coerceIn(-maxY, maxY)
    }

    private fun cancelCurrentStroke() {
        currentPath = null
        currentPoints.clear()
        invalidate()
    }

    private fun snapshotStrokes(): List<DrawingStroke> = strokes.toList()

    private fun restoreSnapshot(snapshot: List<DrawingStroke>) {
        strokes.clear()
        strokes.addAll(snapshot)
        cancelCurrentStroke()
        invalidate()
    }

    private fun recordBeforeMutation() {
        history.recordBefore(snapshotStrokes())
    }

    private fun logHistory(operation: String) {
        KardLeafLog.d(
            DrawingPadLogTag,
            "history operation=$operation strokes=${strokes.size} undo=${history.undoCount} redo=${history.redoCount}",
        )
    }

    private fun DrawingStroke.isNear(
        x: Float,
        y: Float,
        threshold: Float,
    ): Boolean {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val expanded = threshold + width
        if (!bounds.insetAndContains(x, y, expanded)) return false
        return points.any { point -> hypot((point.x - x).toDouble(), (point.y - y).toDouble()) <= expanded }
    }

    private fun RectF.insetAndContains(x: Float, y: Float, inset: Float): Boolean =
        x >= left - inset && x <= right + inset && y >= top - inset && y <= bottom + inset
}
