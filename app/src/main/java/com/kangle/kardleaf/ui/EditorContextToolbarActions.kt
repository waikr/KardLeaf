package com.kangle.kardleaf.ui

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.kangle.kardleaf.ui.editor.host.ToolbarIconButton

private data class InlineStyleOption(
    val label: String,
    val value: String,
    val swatch: Color? = null,
    val custom: Boolean = false,
)

private fun inlineStyleOptions(property: String): List<InlineStyleOption> = when (property) {
    "color" -> listOf(
        InlineStyleOption("黑色", "#212121", Color(AndroidColor.parseColor("#212121"))),
        InlineStyleOption("红色", "#e53935", Color(AndroidColor.parseColor("#e53935"))),
        InlineStyleOption("黄色", "#fdd835", Color(AndroidColor.parseColor("#fdd835"))),
        InlineStyleOption("蓝色", "#1e88e5", Color(AndroidColor.parseColor("#1e88e5"))),
        InlineStyleOption("自定义颜色", "", custom = true),
    )
    "backgroundColor" -> listOf(
        InlineStyleOption("白色", "#ffffff", Color(AndroidColor.parseColor("#ffffff"))),
        InlineStyleOption("红色", "#e53935", Color(AndroidColor.parseColor("#e53935"))),
        InlineStyleOption("黄色", "#fdd835", Color(AndroidColor.parseColor("#fdd835"))),
        InlineStyleOption("蓝色", "#1e88e5", Color(AndroidColor.parseColor("#1e88e5"))),
        InlineStyleOption("自定义颜色", "", custom = true),
    )
    "fontSize" -> listOf(
        InlineStyleOption("默认", ""),
        InlineStyleOption("小字", "0.8em"),
        InlineStyleOption("大字", "1.5em"),
    )
    else -> emptyList()
}

@Composable
private fun InlineStyleToolbarAction(
    property: String,
    icon: ImageVector,
    description: String,
    expanded: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCommand: (String, String) -> Unit,
) {
    Box {
        ToolbarIconButton(
            text = "",
            icon = icon,
            iconSize = 28.dp,
            contentDescription = description,
            enabled = enabled,
            onClick = { onExpandedChange(!expanded) },
        )
        KardLeafDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(190.dp),
            forceAboveAnchor = true,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
        ) {
            inlineStyleOptions(property).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        when {
                            option.swatch != null -> Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(option.swatch)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            )
                            option.custom -> Icon(Icons.Outlined.Add, contentDescription = null)
                        }
                    },
                    onClick = {
                        onExpandedChange(false)
                        if (option.custom) onCommand("openInlineColorPicker", property)
                        else onCommand("setInlineStyleAtCursor", "$property:${option.value}")
                    },
                )
            }
        }
    }
}

@Composable
internal fun EditorContextToolbarActions(
    kind: String,
    canDeleteRow: Boolean,
    canDeleteColumn: Boolean,
    enabled: Boolean,
    onCommand: (String, String) -> Unit,
) {
    var expandedStyleAction by remember(kind) { mutableStateOf<String?>(null) }
    AnimatedContent(
        targetState = kind,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(90)))
                .using(SizeTransform(clip = true) { _, _ -> tween(240) })
        },
        contentAlignment = Alignment.CenterStart,
        label = "editorContextActions",
    ) { displayedKind ->
        Row(
            modifier = Modifier.height(44.dp).widthIn(max = 276.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val table = displayedKind == "table"
            if (table) {
                listOf(
                    Triple(tableAddRowIcon, "增加行", "addRow"),
                    Triple(tableAddColumnIcon, "增加列", "addColumn"),
                    Triple(tableDeleteRowIcon, "删除行", "deleteRow"),
                    Triple(tableDeleteColumnIcon, "删除列", "deleteColumn"),
                    Triple(tableAlignmentIcon, "对齐方式", "alignment"),
                    Triple(tableSourcePreviewIcon, "源码预览切换", "sourcePreview"),
                ).forEach { (icon, description, action) ->
                    ToolbarIconButton(
                        text = "",
                        icon = icon,
                        iconSize = 28.dp,
                        contentDescription = description,
                        preserveIconColors = true,
                        enabled = enabled && displayedKind == kind && when (action) {
                            "deleteRow" -> canDeleteRow
                            "deleteColumn" -> canDeleteColumn
                            else -> true
                        },
                        onClick = { onCommand("tableToolbarAction", action) },
                    )
                }
            } else if (displayedKind == "style") {
                listOf(
                    Triple(inlineStyleColorToolbarIcon, "字色", "color"),
                    Triple(inlineStyleBackgroundToolbarIcon, "背景", "backgroundColor"),
                    Triple(inlineStyleFontSizeToolbarIcon, "字号", "fontSize"),
                ).forEach { (icon, description, property) ->
                    InlineStyleToolbarAction(
                        property = property,
                        icon = icon,
                        description = description,
                        expanded = expandedStyleAction == property,
                        enabled = enabled && displayedKind == kind,
                        onExpandedChange = { expandedStyleAction = if (it) property else null },
                        onCommand = onCommand,
                    )
                }
            }
        }
    }
}

private val tableAddRowIcon = ImageVector.Builder("TableAddRow", 24.dp, 24.dp, 1024f, 1024f).apply {
    addPath(
        pathData = PathParser().parsePathString("M262.0,170.0 H762.0 A92.0,92.0 0 0 1 854.0,262.0 V762.0 A92.0,92.0 0 0 1 762.0,854.0 H262.0 A92.0,92.0 0 0 1 170.0,762.0 V262.0 A92.0,92.0 0 0 1 262.0,170.0 Z").toNodes(),
        fill = SolidColor(Color(0xFF43464D)),
    )
    addPath(
        pathData = PathParser().parsePathString("M290.0,250.0 H450.0 A40.0,40.0 0 0 1 490.0,290.0 V450.0 A40.0,40.0 0 0 1 450.0,490.0 H290.0 A40.0,40.0 0 0 1 250.0,450.0 V290.0 A40.0,40.0 0 0 1 290.0,250.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    addPath(
        pathData = PathParser().parsePathString("M574.0,250.0 H734.0 A40.0,40.0 0 0 1 774.0,290.0 V450.0 A40.0,40.0 0 0 1 734.0,490.0 H574.0 A40.0,40.0 0 0 1 534.0,450.0 V290.0 A40.0,40.0 0 0 1 574.0,250.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    addPath(
        pathData = PathParser().parsePathString("M290.0,534.0 H450.0 A40.0,40.0 0 0 1 490.0,574.0 V734.0 A40.0,40.0 0 0 1 450.0,774.0 H290.0 A40.0,40.0 0 0 1 250.0,734.0 V574.0 A40.0,40.0 0 0 1 290.0,534.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFE0F4E3)),
    )
    addPath(
        pathData = PathParser().parsePathString("M574.0,534.0 H734.0 A40.0,40.0 0 0 1 774.0,574.0 V734.0 A40.0,40.0 0 0 1 734.0,774.0 H574.0 A40.0,40.0 0 0 1 534.0,734.0 V574.0 A40.0,40.0 0 0 1 574.0,534.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFE0F4E3)),
    )
    addPath(
        pathData = PathParser().parsePathString("M 330 286 H 286 V 330").toNodes(),
        stroke = SolidColor(Color(0xFF31C552)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 410 286 H 454 V 330").toNodes(),
        stroke = SolidColor(Color(0xFF31C552)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 330 454 H 286 V 410").toNodes(),
        stroke = SolidColor(Color(0xFF31C552)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 410 454 H 454 V 410").toNodes(),
        stroke = SolidColor(Color(0xFF31C552)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

private val tableAddColumnIcon = ImageVector.Builder("TableAddColumn", 24.dp, 24.dp, 1024f, 1024f).apply {
    addPath(
        pathData = PathParser().parsePathString("M262.0,170.0 H762.0 A92.0,92.0 0 0 1 854.0,262.0 V762.0 A92.0,92.0 0 0 1 762.0,854.0 H262.0 A92.0,92.0 0 0 1 170.0,762.0 V262.0 A92.0,92.0 0 0 1 262.0,170.0 Z").toNodes(),
        fill = SolidColor(Color(0xFF43464D)),
    )
    addPath(
        pathData = PathParser().parsePathString("M290.0,250.0 H450.0 A40.0,40.0 0 0 1 490.0,290.0 V450.0 A40.0,40.0 0 0 1 450.0,490.0 H290.0 A40.0,40.0 0 0 1 250.0,450.0 V290.0 A40.0,40.0 0 0 1 290.0,250.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    addPath(
        pathData = PathParser().parsePathString("M574.0,250.0 H734.0 A40.0,40.0 0 0 1 774.0,290.0 V450.0 A40.0,40.0 0 0 1 734.0,490.0 H574.0 A40.0,40.0 0 0 1 534.0,450.0 V290.0 A40.0,40.0 0 0 1 574.0,250.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFE0F4E3)),
    )
    addPath(
        pathData = PathParser().parsePathString("M290.0,534.0 H450.0 A40.0,40.0 0 0 1 490.0,574.0 V734.0 A40.0,40.0 0 0 1 450.0,774.0 H290.0 A40.0,40.0 0 0 1 250.0,734.0 V574.0 A40.0,40.0 0 0 1 290.0,534.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    addPath(
        pathData = PathParser().parsePathString("M574.0,534.0 H734.0 A40.0,40.0 0 0 1 774.0,574.0 V734.0 A40.0,40.0 0 0 1 734.0,774.0 H574.0 A40.0,40.0 0 0 1 534.0,734.0 V574.0 A40.0,40.0 0 0 1 574.0,534.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFE0F4E3)),
    )
    addPath(
        pathData = PathParser().parsePathString("M 330 286 H 286 V 330").toNodes(),
        stroke = SolidColor(Color(0xFF31C552)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 410 286 H 454 V 330").toNodes(),
        stroke = SolidColor(Color(0xFF31C552)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 330 454 H 286 V 410").toNodes(),
        stroke = SolidColor(Color(0xFF31C552)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 410 454 H 454 V 410").toNodes(),
        stroke = SolidColor(Color(0xFF31C552)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

private val tableDeleteRowIcon = ImageVector.Builder("TableDeleteRow", 24.dp, 24.dp, 1024f, 1024f).apply {
    addPath(
        pathData = PathParser().parsePathString("M262.0,170.0 H762.0 A92.0,92.0 0 0 1 854.0,262.0 V762.0 A92.0,92.0 0 0 1 762.0,854.0 H262.0 A92.0,92.0 0 0 1 170.0,762.0 V262.0 A92.0,92.0 0 0 1 262.0,170.0 Z").toNodes(),
        fill = SolidColor(Color(0xFF43464D)),
    )
    addPath(
        pathData = PathParser().parsePathString("M290.0,250.0 H450.0 A40.0,40.0 0 0 1 490.0,290.0 V450.0 A40.0,40.0 0 0 1 450.0,490.0 H290.0 A40.0,40.0 0 0 1 250.0,450.0 V290.0 A40.0,40.0 0 0 1 290.0,250.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFF8D9DE)),
    )
    addPath(
        pathData = PathParser().parsePathString("M574.0,250.0 H734.0 A40.0,40.0 0 0 1 774.0,290.0 V450.0 A40.0,40.0 0 0 1 734.0,490.0 H574.0 A40.0,40.0 0 0 1 534.0,450.0 V290.0 A40.0,40.0 0 0 1 574.0,250.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFF8D9DE)),
    )
    addPath(
        pathData = PathParser().parsePathString("M290.0,534.0 H450.0 A40.0,40.0 0 0 1 490.0,574.0 V734.0 A40.0,40.0 0 0 1 450.0,774.0 H290.0 A40.0,40.0 0 0 1 250.0,734.0 V574.0 A40.0,40.0 0 0 1 290.0,534.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    addPath(
        pathData = PathParser().parsePathString("M574.0,534.0 H734.0 A40.0,40.0 0 0 1 774.0,574.0 V734.0 A40.0,40.0 0 0 1 734.0,774.0 H574.0 A40.0,40.0 0 0 1 534.0,734.0 V574.0 A40.0,40.0 0 0 1 574.0,534.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    addPath(
        pathData = PathParser().parsePathString("M 330 286 H 286 V 330").toNodes(),
        stroke = SolidColor(Color(0xFFF35F69)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 410 286 H 454 V 330").toNodes(),
        stroke = SolidColor(Color(0xFFF35F69)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 330 454 H 286 V 410").toNodes(),
        stroke = SolidColor(Color(0xFFF35F69)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 410 454 H 454 V 410").toNodes(),
        stroke = SolidColor(Color(0xFFF35F69)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

private val tableDeleteColumnIcon = ImageVector.Builder("TableDeleteColumn", 24.dp, 24.dp, 1024f, 1024f).apply {
    addPath(
        pathData = PathParser().parsePathString("M262.0,170.0 H762.0 A92.0,92.0 0 0 1 854.0,262.0 V762.0 A92.0,92.0 0 0 1 762.0,854.0 H262.0 A92.0,92.0 0 0 1 170.0,762.0 V262.0 A92.0,92.0 0 0 1 262.0,170.0 Z").toNodes(),
        fill = SolidColor(Color(0xFF43464D)),
    )
    addPath(
        pathData = PathParser().parsePathString("M290.0,250.0 H450.0 A40.0,40.0 0 0 1 490.0,290.0 V450.0 A40.0,40.0 0 0 1 450.0,490.0 H290.0 A40.0,40.0 0 0 1 250.0,450.0 V290.0 A40.0,40.0 0 0 1 290.0,250.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFF8D9DE)),
    )
    addPath(
        pathData = PathParser().parsePathString("M574.0,250.0 H734.0 A40.0,40.0 0 0 1 774.0,290.0 V450.0 A40.0,40.0 0 0 1 734.0,490.0 H574.0 A40.0,40.0 0 0 1 534.0,450.0 V290.0 A40.0,40.0 0 0 1 574.0,250.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    addPath(
        pathData = PathParser().parsePathString("M290.0,534.0 H450.0 A40.0,40.0 0 0 1 490.0,574.0 V734.0 A40.0,40.0 0 0 1 450.0,774.0 H290.0 A40.0,40.0 0 0 1 250.0,734.0 V574.0 A40.0,40.0 0 0 1 290.0,534.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFF8D9DE)),
    )
    addPath(
        pathData = PathParser().parsePathString("M574.0,534.0 H734.0 A40.0,40.0 0 0 1 774.0,574.0 V734.0 A40.0,40.0 0 0 1 734.0,774.0 H574.0 A40.0,40.0 0 0 1 534.0,734.0 V574.0 A40.0,40.0 0 0 1 574.0,534.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
    )
    addPath(
        pathData = PathParser().parsePathString("M 330 286 H 286 V 330").toNodes(),
        stroke = SolidColor(Color(0xFFF35F69)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 410 286 H 454 V 330").toNodes(),
        stroke = SolidColor(Color(0xFFF35F69)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 330 454 H 286 V 410").toNodes(),
        stroke = SolidColor(Color(0xFFF35F69)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 410 454 H 454 V 410").toNodes(),
        stroke = SolidColor(Color(0xFFF35F69)),
        strokeLineWidth = 20f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

private val tableAlignmentIcon = ImageVector.Builder("TableAlignment", 24.dp, 24.dp, 1024f, 1024f).apply {
    addPath(
        pathData = PathParser().parsePathString("M320.0,232.0 H704.0 A88.0,88.0 0 0 1 792.0,320.0 V704.0 A88.0,88.0 0 0 1 704.0,792.0 H320.0 A88.0,88.0 0 0 1 232.0,704.0 V320.0 A88.0,88.0 0 0 1 320.0,232.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
        stroke = SolidColor(Color(0xFF43464D)),
        strokeLineWidth = 42f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M377.0,390.0 H647.0 A17.0,17.0 0 0 1 664.0,407.0 V407.0 A17.0,17.0 0 0 1 647.0,424.0 H377.0 A17.0,17.0 0 0 1 360.0,407.0 V407.0 A17.0,17.0 0 0 1 377.0,390.0 Z").toNodes(),
        fill = SolidColor(Color(0xFF43464D)),
    )
    addPath(
        pathData = PathParser().parsePathString("M427.0,500.0 H597.0 A17.0,17.0 0 0 1 614.0,517.0 V517.0 A17.0,17.0 0 0 1 597.0,534.0 H427.0 A17.0,17.0 0 0 1 410.0,517.0 V517.0 A17.0,17.0 0 0 1 427.0,500.0 Z").toNodes(),
        fill = SolidColor(Color(0xFF43464D)),
    )
    addPath(
        pathData = PathParser().parsePathString("M377.0,610.0 H647.0 A17.0,17.0 0 0 1 664.0,627.0 V627.0 A17.0,17.0 0 0 1 647.0,644.0 H377.0 A17.0,17.0 0 0 1 360.0,627.0 V627.0 A17.0,17.0 0 0 1 377.0,610.0 Z").toNodes(),
        fill = SolidColor(Color(0xFF43464D)),
    )
}.build()

private val tableSourcePreviewIcon = ImageVector.Builder("TableSourcePreview", 24.dp, 24.dp, 1024f, 1024f).apply {
    addPath(
        pathData = PathParser().parsePathString("M320.0,232.0 H704.0 A88.0,88.0 0 0 1 792.0,320.0 V704.0 A88.0,88.0 0 0 1 704.0,792.0 H320.0 A88.0,88.0 0 0 1 232.0,704.0 V320.0 A88.0,88.0 0 0 1 320.0,232.0 Z").toNodes(),
        fill = SolidColor(Color(0xFFFFFFFF)),
        stroke = SolidColor(Color(0xFF43464D)),
        strokeLineWidth = 42f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 626 402 H 386 M 386 402 L 450 338 M 386 402 L 450 466").toNodes(),
        stroke = SolidColor(Color(0xFF43464D)),
        strokeLineWidth = 40f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
    addPath(
        pathData = PathParser().parsePathString("M 398 622 H 638 M 638 622 L 574 558 M 638 622 L 574 686").toNodes(),
        stroke = SolidColor(Color(0xFF43464D)),
        strokeLineWidth = 40f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}.build()

// The same glyph paths as marktext/toolbar.ts, kept in the Compose toolbar for direct reuse.
internal val inlineStyleColorToolbarIcon: ImageVector = ImageVector.Builder(
    name = "KardLeafInlineStyleColorToolbarItem",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 256f,
    viewportHeight = 256f,
).apply {
    addGroup(
        scaleX = 1.255f,
        scaleY = 1.255f,
        translationX = -32.644f,
        translationY = -42.966f,
    )
    addPath(
        pathData = PathParser().parsePathString(
            "m152.58 152-8.1758-20.922h-32.602l-8.2266 20.922h-10.055l29.199-71.551h11.02l28.742 71.551zm-24.477-64.238-0.45703 1.4219q-1.2695 4.2148-3.7578 10.816l-9.1406 23.512h26.762l-9.1914-23.613q-1.4219-3.5039-2.8438-7.9219z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    )
    addPath(
        pathData = PathParser().parsePathString("m78 188h100").toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 8f,
        strokeLineCap = StrokeCap.Round,
    )
    clearGroup()
}.build()

internal val inlineStyleBackgroundToolbarIcon: ImageVector = ImageVector.Builder(
    name = "KardLeafInlineStyleBackgroundToolbarItem",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 256f,
    viewportHeight = 256f,
).apply {
    addGroup(
        scaleX = 1.0487f,
        scaleY = 1.0487f,
        translationX = -12.787f,
        translationY = -3.0861f,
    )
    listOf(
        "m104 70 54 54",
        "m120 86-46 46q-5 5 0 11l32 32q5 5 10 0l46-46q5 5 0-10l-42-42",
        "m185 142s-12 17-12 26a12 12 0 0 0 24 0c0-9-12-26-12-26z",
    ).forEach { path ->
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    clearGroup()
}.build()

internal val inlineStyleFontSizeToolbarIcon: ImageVector = ImageVector.Builder(
    name = "KardLeafInlineStyleFontSizeToolbarItem",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 256f,
    viewportHeight = 256f,
).apply {
    addGroup(
        scaleX = 1.1631f,
        scaleY = 1.1631f,
        translationX = -15.018f,
        translationY = -11.102f,
    )
    listOf(
        "m99.125 154-5.0312-12.875h-20.062l-5.0625 12.875h-6.1875l17.969-44.031h6.7812l17.688 44.031zm-15.062-39.531-0.28125 0.875q-0.78125 2.5938-2.3125 6.6562l-5.625 14.469h16.469l-5.6562-14.531q-0.875-2.1562-1.75-4.875z",
        "m173.63 154-7.8613-20.117h-31.348l-7.9102 20.117h-9.668l28.076-68.799h10.596l27.637 68.799zm-23.535-61.768-0.43946 1.3672q-1.2207 4.0527-3.6133 10.4l-8.7891 22.607h25.732l-8.8379-22.705q-1.3672-3.3692-2.7344-7.6172z",
    ).forEach { path ->
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }
    clearGroup()
}.build()

