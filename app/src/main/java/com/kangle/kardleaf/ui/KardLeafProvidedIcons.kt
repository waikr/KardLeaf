package com.kangle.kardleaf.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private const val ICON_VIEWPORT = 24f

private fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float): String {
    val right = x + width
    val bottom = y + height
    return "M${x + radius},$y H${right - radius} A$radius,$radius 0 0 1 $right,${y + radius} " +
        "V${bottom - radius} A$radius,$radius 0 0 1 ${right - radius},$bottom " +
        "H${x + radius} A$radius,$radius 0 0 1 $x,${bottom - radius} " +
        "V${y + radius} A$radius,$radius 0 0 1 ${x + radius},$y Z"
}

private fun circle(cx: Float, cy: Float, radius: Float): String =
    "M${cx - radius},$cy A$radius,$radius 0 1 0 ${cx + radius},$cy " +
        "A$radius,$radius 0 1 0 ${cx - radius},$cy Z"

private fun buildIcon(name: String, content: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = ICON_VIEWPORT,
        viewportHeight = ICON_VIEWPORT,
    ).apply(content).build()

private fun ImageVector.Builder.strokePath(pathData: String, width: Float = 2.05f) {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

private fun ImageVector.Builder.fillPath(pathData: String) {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
    )
}

internal val settingsSidebarIcon: ImageVector = buildIcon("KardLeafSettingsSidebar") {
    strokePath(roundedRect(3f, 4f, 18f, 16f, 2f), 2f)
    strokePath("M9,4V20", 2f)
}

internal val settingsSidePanelOpenModeIcon: ImageVector = buildIcon("KardLeafSettingsSidePanelOpenMode") {
    strokePath(roundedRect(3.25f, 4f, 17.5f, 16f, 2.25f), 1.75f)
    strokePath("M9,4V20", 1.75f)
    strokePath("M12,12H17", 1.75f)
    strokePath("M15,9.5L18,12L15,14.5", 1.75f)
}

internal val settingsSingleColumnIcon: ImageVector = buildIcon("KardLeafSettingsSingleColumn") {
    strokePath(roundedRect(3.5f, 4f, 17f, 6.1f, 2.2f))
    strokePath(roundedRect(3.5f, 13.9f, 17f, 6.1f, 2.2f))
}

internal val settingsGridIcon: ImageVector = buildIcon("KardLeafSettingsGrid") {
    listOf(
        roundedRect(3.5f, 3.5f, 7.3f, 7.3f, 2f),
        roundedRect(13.2f, 3.5f, 7.3f, 7.3f, 2f),
        roundedRect(3.5f, 13.2f, 7.3f, 7.3f, 2f),
        roundedRect(13.2f, 13.2f, 7.3f, 7.3f, 2f),
    ).forEach { strokePath(it) }
}

internal val settingsLooseDensityIcon: ImageVector = buildIcon("KardLeafSettingsLooseDensity") {
    listOf(4.5f, 12f, 19.5f).forEach { y ->
        fillPath(circle(5.5f, y, 1f))
        strokePath("M9,$y H19", 1.5f)
    }
}

internal val settingsCompactDensityIcon: ImageVector = buildIcon("KardLeafSettingsCompactDensity") {
    listOf(4.5f, 7.5f, 10.5f, 13.5f, 16.5f, 19.5f).forEach { y ->
        fillPath(circle(5f, y, .7f))
        strokePath("M8,$y H19", 1.5f)
    }
}

internal val settingsTopToolbarIcon: ImageVector = buildIcon("KardLeafSettingsTopToolbar") {
    strokePath(roundedRect(3f, 3.5f, 18f, 17f, 3f))
    strokePath("M3.8,8.2H20.2")
    fillPath(roundedRect(6f, 5.4f, 6f, 1.2f, .6f))
    fillPath(circle(17.5f, 6f, .85f))
}

internal val settingsBottomToolbarIcon: ImageVector = buildIcon("KardLeafSettingsBottomToolbar") {
    strokePath(roundedRect(3f, 3.5f, 18f, 17f, 3f))
    strokePath("M3.8,15.8H20.2")
    listOf(7.2f, 12f, 16.8f).forEach { x -> fillPath(circle(x, 18.1f, .85f)) }
}

internal val settingsEditorBottomToolbarIcon: ImageVector = buildIcon("KardLeafSettingsEditorBottomToolbar") {
    strokePath(roundedRect(3f, 3.5f, 18f, 17f, 3f))
    strokePath("M3.8,15.7H20.2")
    strokePath("M9.2,18.9L9.75,16.9L15.2,11.45L17.15,13.4L11.7,18.85L9.2,18.9Z")
    strokePath("M14.7,11.95L16.65,13.9")
}

internal val settingsLongPressToolbarIcon: ImageVector = buildIcon("KardLeafSettingsLongPressToolbar") {
    strokePath(roundedRect(5f, 3.2f, 14f, 4.2f, 2.1f))
    listOf(9f, 12f, 15f).forEach { x -> fillPath(circle(x, 5.3f, .7f)) }
    strokePath(
        "M10.2,18.8V11.6A1.55,1.55 0 0 1 13.3,11.6V14.7" +
            "M13.3,14.7V13.5A1.35,1.35 0 0 1 16,13.5V15.3" +
            "M16,15.3A1.25,1.25 0 0 1 18.5,15.4V17.8" +
            "C18.5,19.6 17.1,20.9 15.3,20.9H13.6" +
            "C11.7,20.9 10.1,20.1 9.2,18.5L7.8,16.1A1.2,1.2 0 0 1 9.8,14.8L10.2,15.4",
    )
}
