package com.kangle.kardleaf.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.util.Log
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
    onSave: (Bitmap) -> Unit,
    modifier: Modifier = Modifier,
) {
    var drawingView by remember { mutableStateOf<KardLeafDrawingPadView?>(null) }
    var tool by remember { mutableStateOf(DrawingTool.Pen) }
    var grid by remember { mutableStateOf(DrawingGrid.Square) }
    var canvasColor by remember { mutableStateOf(Color.White) }
    var penColor by remember { mutableStateOf(Color.Black) }
    var highlighterColor by remember { mutableStateOf(Color(0xFFFFEB3B)) }
    var penStrokeWidth by remember { mutableStateOf(7f) }
    var highlighterStrokeWidth by remember { mutableStateOf(18f) }
    var eraserStrokeWidth by remember { mutableStateOf(28f) }

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

    Log.d(
        DrawingPadLogTag,
        "compose drawing screen tool=$tool grid=$grid canvasColor=${canvasColor.toArgb()} penColor=${penColor.toArgb()} activeWidth=$activeWidth drawingViewReady=${drawingView != null}",
    )

    fun saveDrawing(source: String) {
        val view = drawingView
        Log.d(DrawingPadLogTag, "$source save clicked drawingViewReady=${view != null}")
        val bitmap = view?.exportBitmap()
        Log.d(DrawingPadLogTag, "$source export bitmap result=${bitmap?.width}x${bitmap?.height}")
        bitmap?.let(onSave)
    }

    BackHandler {
        Log.d(DrawingPadLogTag, "back pressed close drawing screen")
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
                        Log.d(
                            DrawingPadLogTag,
                            "top bar positioned size=${coordinates.size.width}x${coordinates.size.height} window=${coordinates.localToWindow(androidx.compose.ui.geometry.Offset.Zero)}",
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = {
                    Log.d(DrawingPadLogTag, "top back button clicked")
                    onDismiss()
                }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "关闭画图")
                }
                Text(
                    text = "绘图",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .onGloballyPositioned { coordinates ->
                            Log.d(
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
                        Log.d(DrawingPadLogTag, "AndroidView factory create drawing view")
                        KardLeafDrawingPadView(context).also { view ->
                            drawingView = view
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
                        Log.d(DrawingPadLogTag, "AndroidView update drawing view width=${view.width} height=${view.height}")
                        drawingView = view
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
                            Log.d(
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
                valueRange = 2f..54f,
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
            onSave = {},
        )
    }
}

private data class DrawingStroke(
    val path: Path,
    val points: List<android.graphics.PointF>,
    val tool: DrawingTool,
    val color: Int,
    val width: Float,
)

private class KardLeafDrawingPadView(context: Context) : View(context) {
    private val strokes = mutableListOf<DrawingStroke>()
    private val redoStrokes = mutableListOf<DrawingStroke>()
    private var currentPath: Path? = null
    private val currentPoints = mutableListOf<android.graphics.PointF>()
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
        Log.d(DrawingPadLogTag, "drawing view attached width=$width height=$height")
    }

    override fun onDetachedFromWindow() {
        Log.d(DrawingPadLogTag, "drawing view detached width=$width height=$height strokes=${strokes.size}")
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d(DrawingPadLogTag, "drawing view size changed ${oldw}x$oldh -> ${w}x$h")
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
        this.penStrokeWidth = penStrokeWidth.coerceIn(2f, 54f)
        this.highlighterStrokeWidth = highlighterStrokeWidth.coerceIn(2f, 54f)
        this.eraserStrokeWidth = eraserStrokeWidth.coerceIn(2f, 54f)
        invalidate()
    }

    fun undo() {
        Log.d(DrawingPadLogTag, "undo clicked strokes=${strokes.size} redo=${redoStrokes.size}")
        if (strokes.isNotEmpty()) {
            redoStrokes.add(strokes.removeAt(strokes.lastIndex))
            invalidate()
        }
    }

    fun redo() {
        Log.d(DrawingPadLogTag, "redo clicked strokes=${strokes.size} redo=${redoStrokes.size}")
        if (redoStrokes.isNotEmpty()) {
            strokes.add(redoStrokes.removeAt(redoStrokes.lastIndex))
            invalidate()
        }
    }

    fun clear() {
        Log.d(DrawingPadLogTag, "clear clicked strokes=${strokes.size} redo=${redoStrokes.size}")
        strokes.clear()
        redoStrokes.clear()
        currentPath = null
        currentPoints.clear()
        invalidate()
    }

    fun exportBitmap(): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        Log.d(DrawingPadLogTag, "exportBitmap requested view=${width}x$height safe=${safeWidth}x$safeHeight strokes=${strokes.size} redo=${redoStrokes.size}")
        return Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawDrawingContent(Canvas(bitmap))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(DrawingPadLogTag, "touch down tool=$tool x=$x y=$y")
                parent.requestDisallowInterceptTouchEvent(true)
                if (tool == DrawingTool.StrokeEraser) {
                    removeStrokeNear(x, y)
                    return true
                }
                currentPath = Path().apply { moveTo(x, y) }
                currentPoints.clear()
                currentPoints.add(android.graphics.PointF(x, y))
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (tool == DrawingTool.StrokeEraser) {
                    removeStrokeNear(x, y)
                    return true
                }
                currentPath?.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
                currentPoints.add(android.graphics.PointF(x, y))
                lastX = x
                lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                Log.d(DrawingPadLogTag, "touch end action=${event.actionMasked} tool=$tool x=$x y=$y")
                if (tool != DrawingTool.StrokeEraser) {
                    currentPath?.let { path ->
                        path.lineTo(x, y)
                        currentPoints.add(android.graphics.PointF(x, y))
                        strokes.add(
                            DrawingStroke(
                                path = Path(path),
                                points = currentPoints.map { android.graphics.PointF(it.x, it.y) },
                                tool = tool,
                                color = currentStrokeColor(),
                                width = currentStrokeWidth(),
                            ),
                        )
                        redoStrokes.clear()
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
            redoStrokes.clear()
            invalidate()
        }
    }

    private fun DrawingStroke.isNear(x: Float, y: Float, threshold: Float): Boolean {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val expanded = threshold + width
        if (!bounds.insetAndContains(x, y, expanded)) return false
        return points.any { point -> hypot((point.x - x).toDouble(), (point.y - y).toDouble()) <= expanded }
    }

    private fun RectF.insetAndContains(x: Float, y: Float, inset: Float): Boolean =
        x >= left - inset && x <= right + inset && y >= top - inset && y <= bottom + inset
}
