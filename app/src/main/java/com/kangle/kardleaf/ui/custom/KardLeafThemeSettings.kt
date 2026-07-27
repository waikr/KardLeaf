package com.kangle.kardleaf.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.kangle.kardleaf.AppIconManager
import com.kangle.kardleaf.BuildConfig
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.data.utils.KardLeafLog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val SETTINGS_TRACE_TAG = "KardLeafSettingsTrace"
private const val DIAGNOSTIC_LOGCAT_MAX_CHARS = 2_000_000
private const val DIAGNOSTIC_LOGCAT_TIMEOUT_SECONDS = 5L

internal data class EditorFontOption(
    val label: String,
    val value: String,
    val subtitle: String,
)

internal fun settingsText(english: Boolean, zh: String, en: String): String =
    if (english) en else zh

internal fun createDiagnosticLogFile(
    context: Context,
    includeLogcat: Boolean,
): File {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    val now = Date()
    val fileTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(now)
    val displayTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)
    val appFileLogs = KardLeafLog.readFileLogs()
    val recentLogs = if (includeLogcat) readRecentLogcat(context) else ""
    val logText = buildString {
        appendLine("KardLeaf 诊断日志")
        appendLine("生成时间：$displayTime")
        appendLine("应用版本：${packageInfo.versionName} ($versionCode)")
        appendLine("包名：${context.packageName}")
        appendLine("构建：${if (BuildConfig.KARDLEAF_DEV_VARIANT) "dev" else "stable"}")
        appendLine("提交：${BuildConfig.KARDLEAF_GIT_COMMIT}")
        appendLine("Android：${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT}")
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        appendLine("应用内部日志：${if (appFileLogs.isBlank()) "无" else "已导出"}")
        appendLine("说明：默认保留警告和错误；开启日志后额外保留详细日志。")
        if (appFileLogs.isNotBlank()) {
            appendLine()
            appendLine("===== KardLeaf app logs =====")
            appendLine(appFileLogs)
        }
        appendLine()
        if (includeLogcat) {
            appendLine("系统日志：已开启，导出当前 logcat 缓冲区")
            appendLine("说明：日志会做基础敏感字段脱敏，并限制文件大小。")
            appendLine()
            appendLine("===== Android logcat =====")
            appendLine(recentLogs.ifBlank { "没有可导出的最近日志。" })
        } else {
            appendLine("系统日志：未开启")
            appendLine("开启日志后再次导出会包含最近 Logcat。")
        }
    }
    val shareDir = File(context.cacheDir, "shared_notes").apply { mkdirs() }
    return File(shareDir, "kardleaf_diagnostic_$fileTimestamp.txt").apply {
        writeText(logText, Charsets.UTF_8)
    }
}

internal fun readRecentLogcat(context: Context): String {
    val pidResult = runLogcat(
        context,
        listOf("logcat", "-d", "-v", "time", "--pid=${android.os.Process.myPid()}"),
    )
    val output = if (pidResult.exitCode == 0) {
        pidResult.output
    } else {
        runLogcat(context, listOf("logcat", "-d", "-v", "time")).output
    }
    return trimDiagnosticLogcat(KardLeafLog.redactSensitiveText(output))
}

internal data class LogcatResult(
    val exitCode: Int,
    val output: String,
)

internal fun runLogcat(
    context: Context,
    command: List<String>,
): LogcatResult {
    val outFile = File.createTempFile("kardleaf-logcat-", ".txt", context.cacheDir)
    return try {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(outFile)
            .start()
        val completed = process.waitFor(DIAGNOSTIC_LOGCAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            LogcatResult(-1, "logcat export timed out")
        } else {
            LogcatResult(process.exitValue(), outFile.readText(Charsets.UTF_8))
        }
    } finally {
        outFile.delete()
    }
}

internal fun trimDiagnosticLogcat(text: String): String {
    val trimmed = text.trim()
    if (trimmed.length <= DIAGNOSTIC_LOGCAT_MAX_CHARS) return trimmed
    return "[已截断，只保留最后 $DIAGNOSTIC_LOGCAT_MAX_CHARS 字符]\n" +
        trimmed.takeLast(DIAGNOSTIC_LOGCAT_MAX_CHARS)
}

internal val EditorBuiltinFontFamilies = listOf(
    EditorFontOption("系统默认", "system", "跟随 Android 默认字体"),
    EditorFontOption("无衬线", "sans-serif", "清爽通用正文"),
    EditorFontOption("衬线", "serif", "更接近书籍排版"),
    EditorFontOption("等宽", "monospace", "适合代码和纯文本"),
)

internal fun editorFontLabel(font: EditorFontOption, english: Boolean): String =
    if (!english) font.label else when (font.value) {
        "system" -> "System"
        "sans-serif" -> "Sans serif"
        "serif" -> "Serif"
        "monospace" -> "Monospace"
        else -> font.label
    }

internal fun editorFontSubtitle(font: EditorFontOption, english: Boolean): String =
    if (!english) font.subtitle else when (font.value) {
        "system" -> "Use Android's default font"
        "sans-serif" -> "Clean general text"
        "serif" -> "Book-like reading"
        "monospace" -> "For code and plain text"
        else -> font.subtitle
    }

internal data class LanguageOption(
    val label: String,
    val value: String,
    val subtitle: String,
)

internal val AppLanguageOptions = listOf(
    LanguageOption("中文", "zh", "默认语言"),
    LanguageOption("English", "en", "Use English resources"),
)

@Composable
internal fun SettingsValueSlider(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

internal val ThemeCustomAccentColorPalette = listOf(
    0xFF2563EB.toInt(),
    0xFF3B82F6.toInt(),
    0xFF0EA5E9.toInt(),
    0xFF14B8A6.toInt(),
    0xFF16A34A.toInt(),
    0xFF84CC16.toInt(),
    0xFFF59E0B.toInt(),
    0xFFF97316.toInt(),
    0xFFEF4444.toInt(),
    0xFFEC4899.toInt(),
    0xFF8B5CF6.toInt(),
    0xFF64748B.toInt(),
)

internal val ThemeCustomBackgroundColorPalette = listOf(
    0xFFFFFFFF.toInt(),
    0xFFF8FAFC.toInt(),
    0xFFF1F5F9.toInt(),
    0xFFEFF6FF.toInt(),
    0xFFE0F2FE.toInt(),
    0xFFF0FDFA.toInt(),
    0xFFF0FDF4.toInt(),
    0xFFFEFCE8.toInt(),
    0xFFFFF7ED.toInt(),
    0xFFFFF1F2.toInt(),
    0xFFFDF2F8.toInt(),
    0xFFF5F3FF.toInt(),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeColorPickerDialog(
    title: String,
    presets: List<Int>,
    selectedArgb: Int,
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var workingArgb by remember(selectedArgb) { mutableStateOf(selectedArgb or 0xFF000000.toInt()) }
    var hexText by remember(selectedArgb) { mutableStateOf(argbToThemeHex(selectedArgb)) }
    var hexError by remember { mutableStateOf(false) }

    fun setColor(argb: Int) {
        workingArgb = argb or 0xFF000000.toInt()
        hexText = argbToThemeHex(workingArgb)
        hexError = false
    }

    val red = (workingArgb shr 16) and 0xFF
    val green = (workingArgb shr 8) and 0xFF
    val blue = workingArgb and 0xFF
    val preview = Color(workingArgb)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ThemeColorWheel(
                    selectedArgb = workingArgb,
                    onColorChange = ::setColor,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(preview)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { value ->
                            hexText = value
                            val parsed = parseThemeHexColor(value)
                            hexError = parsed == null
                            if (parsed != null) workingArgb = parsed
                        },
                        label = { Text("HEX") },
                        singleLine = true,
                        isError = hexError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    presets.forEach { argb ->
                        val selected = (argb and 0x00FFFFFF) == (workingArgb and 0x00FFFFFF)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(argb))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { setColor(argb) },
                        )
                    }
                }
                ColorChannelSlider("R", red, Color(0xFFEF4444)) { setColor(themeRgb(it, green, blue)) }
                ColorChannelSlider("G", green, Color(0xFF22C55E)) { setColor(themeRgb(red, it, blue)) }
                ColorChannelSlider("B", blue, Color(0xFF3B82F6)) { setColor(themeRgb(red, green, it)) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(workingArgb) }, enabled = !hexError) {
                Text("应用")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
internal fun ThemeColorWheel(
    selectedArgb: Int,
    onColorChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = remember(selectedArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(selectedArgb, it) }
    }
    val hue = hsv[0]
    val saturation = hsv[1]
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .size(208.dp)
            .pointerInput(Unit) {
                fun updateColor(position: Offset) {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val radius = min(centerX, centerY)
                    val offsetX = position.x - centerX
                    val offsetY = position.y - centerY
                    val distance = sqrt(offsetX * offsetX + offsetY * offsetY)
                    val selectedSaturation = (distance / radius).coerceIn(0f, 1f)
                    val selectedHue = ((atan2(offsetY, offsetX) * 180f / PI.toFloat()) + 360f) % 360f
                    onColorChange(
                        android.graphics.Color.HSVToColor(
                            floatArrayOf(selectedHue, selectedSaturation, 1f),
                        ),
                    )
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateColor(down.position)
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        updateColor(change.position)
                        change.consume()
                        pressed = change.pressed
                    }
                }
            },
    ) {
        val radius = size.minDimension / 2f
        val center = this.center
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red,
                ),
                center = center,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = outlineColor,
            radius = radius - 0.5.dp.toPx(),
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        val angle = hue / 180f * PI.toFloat()
        val indicatorDistance = saturation * (radius - 10.dp.toPx()).coerceAtLeast(0f)
        val indicatorCenter = Offset(
            x = center.x + cos(angle) * indicatorDistance,
            y = center.y + sin(angle) * indicatorDistance,
        )
        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = indicatorCenter,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.68f),
            radius = 8.dp.toPx(),
            center = indicatorCenter,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
internal fun ColorChannelSlider(
    label: String,
    value: Int,
    color: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.width(18.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
    }
}

internal fun themeRgb(red: Int, green: Int, blue: Int): Int =
    android.graphics.Color.rgb(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

internal fun themeModeIcon(mode: PrefsManager.AppThemeMode): ImageVector =
    when (mode) {
        PrefsManager.AppThemeMode.SYSTEM -> Icons.Outlined.Devices
        PrefsManager.AppThemeMode.LIGHT -> Icons.Outlined.LightMode
        PrefsManager.AppThemeMode.DARK -> Icons.Outlined.DarkMode
    }

internal fun argbToThemeHex(argb: Int): String =
    "#%06X".format(argb and 0x00FFFFFF)

internal fun parseThemeHexColor(raw: String): Int? {
    val text = raw.trim().removePrefix("#")
    val normalized = when (text.length) {
        6 -> "FF$text"
        8 -> text
        else -> return null
    }
    return normalized.toLongOrNull(16)?.toInt()
}

internal val ThemeCornerRadiusOptions =
    listOf(PrefsManager.THEME_CORNER_RADIUS_FOLLOW, 0, 8, 16, 24, 32)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AppIconChoiceGrid(
    selectedIcon: AppIconManager.AppIcon,
    onIconClick: (AppIconManager.AppIcon) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppIconManager.AppIcon.values().forEach { icon ->
            AppIconChoiceBlock(
                icon = icon,
                selected = selectedIcon == icon,
                onClick = { onIconClick(icon) },
            )
        }
    }
}

@Composable
internal fun AppIconChoiceBlock(
    icon: AppIconManager.AppIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(id = icon.iconResId),
                contentDescription = icon.label,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,

                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = icon.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeModeChoiceGrid(
    selectedMode: PrefsManager.AppThemeMode,
    onModeClick: (PrefsManager.AppThemeMode) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrefsManager.AppThemeMode.values().forEach { mode ->
            ThemeChoiceBlock(
                icon = themeModeIcon(mode),
                title = themeModeLabel(mode),
                subtitle = themeModeSubtitle(mode),
                selected = selectedMode == mode,
                onClick = { onModeClick(mode) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeStyleChoiceGrid(
    styles: List<PrefsManager.AppThemeStyle>,
    selectedStyle: PrefsManager.AppThemeStyle,
    onStyleClick: (PrefsManager.AppThemeStyle) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        styles.forEach { style ->
            ThemeChoiceBlock(
                icon = Icons.Outlined.Palette,
                title = themeStyleLabel(style),
                subtitle = themeStyleSubtitle(style),
                selected = selectedStyle == style,
                onClick = { onStyleClick(style) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ModernThemeColorStyleChoiceGrid(
    selectedStyle: PrefsManager.ModernThemeColorStyle,
    onStyleClick: (PrefsManager.ModernThemeColorStyle) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrefsManager.ModernThemeColorStyle.values().forEach { style ->
            ThemeChoiceBlock(
                icon = Icons.Outlined.Palette,
                title = modernThemeColorStyleLabel(style),
                subtitle = modernThemeColorStyleSubtitle(style),
                selected = selectedStyle == style,
                onClick = { onStyleClick(style) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CleanListFeatureIconStyleChoiceGrid(
    selectedStyle: PrefsManager.CleanListFeatureIconStyle,
    onStyleClick: (PrefsManager.CleanListFeatureIconStyle) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrefsManager.CleanListFeatureIconStyle.values().forEach { style ->
            ThemeChoiceBlock(
                icon = Icons.Outlined.Apps,
                title = cleanListFeatureIconStyleLabel(style),
                subtitle = cleanListFeatureIconStyleSubtitle(style),
                selected = selectedStyle == style,
                onClick = { onStyleClick(style) },
            )
        }
    }
}

@Composable
internal fun ThemeChoiceBlock(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecommendedThemePaletteGrid(
    selectedAccentColor: PrefsManager.ThemeColor,
    selectedBackgroundColor: PrefsManager.ThemeBackgroundColor,
    onPaletteClick: (PrefsManager.ThemeColor, PrefsManager.ThemeBackgroundColor) -> Unit,
) {
    val palettes = listOf(
        ThemePalettePreset("蓝白", PrefsManager.ThemeColor.BLUE, PrefsManager.ThemeBackgroundColor.WHITE),
        ThemePalettePreset("叶绿", PrefsManager.ThemeColor.GREEN, PrefsManager.ThemeBackgroundColor.GREEN),
        ThemePalettePreset("清蓝", PrefsManager.ThemeColor.BLUE, PrefsManager.ThemeBackgroundColor.BLUE),
        ThemePalettePreset("柔紫", PrefsManager.ThemeColor.PURPLE, PrefsManager.ThemeBackgroundColor.PURPLE),
        ThemePalettePreset("暖读", PrefsManager.ThemeColor.AMBER, PrefsManager.ThemeBackgroundColor.AMBER),
        ThemePalettePreset("灰粉", PrefsManager.ThemeColor.PINK, PrefsManager.ThemeBackgroundColor.GRAY),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        palettes.forEach { preset ->
            ThemePaletteComboBox(
                title = preset.title,
                accentColor = themeAccentPreviewColor(preset.accent),
                backgroundColor = themeBackgroundPreviewColor(preset.background),
                selected = selectedAccentColor == preset.accent && selectedBackgroundColor == preset.background,
                onClick = { onPaletteClick(preset.accent, preset.background) },
            )
        }
    }
}

internal data class ThemePalettePreset(
    val title: String,
    val accent: PrefsManager.ThemeColor,
    val background: PrefsManager.ThemeBackgroundColor,
)

@Composable
internal fun ThemePaletteComboBox(
    title: String,
    accentColor: Color,
    backgroundColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(138.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 110.dp, height = 66.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(backgroundColor),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(9.dp)
                    .size(width = 58.dp, height = 22.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.82f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(9.dp)
                    .size(width = 42.dp, height = 8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accentColor.copy(alpha = 0.34f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(9.dp)
                    .size(width = 34.dp, height = 22.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(accentColor),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(20.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeColorPaletteGrid(
    colors: List<PrefsManager.ThemeColor>,
    selectedColor: PrefsManager.ThemeColor,
    customColor: Color,
    onColorClick: (PrefsManager.ThemeColor) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { color ->
            ThemePaletteBox(
                title = themeColorLabel(color),
                color = if (color == PrefsManager.ThemeColor.CUSTOM) customColor else themeAccentPreviewColor(color),
                selected = selectedColor == color,
                onClick = { onColorClick(color) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThemeBackgroundPaletteGrid(
    colors: List<PrefsManager.ThemeBackgroundColor>,
    selectedColor: PrefsManager.ThemeBackgroundColor,
    customColor: Color,
    onColorClick: (PrefsManager.ThemeBackgroundColor) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { color ->
            ThemePaletteBox(
                title = themeBackgroundColorLabel(color),
                color = if (color == PrefsManager.ThemeBackgroundColor.CUSTOM) customColor else themeBackgroundPreviewColor(color),
                selected = selectedColor == color,
                onClick = { onColorClick(color) },
            )
        }
    }
}

@Composable
internal fun ThemePaletteBox(
    title: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .width(92.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color),
            )
            Row(modifier = Modifier.align(Alignment.BottomCenter)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .background(color.copy(alpha = 0.62f)),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .background(color.copy(alpha = 0.28f)),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CornerRadiusPaletteGrid(

    title: String,
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onClick: (Int) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        values.forEach { value ->
            val selectedValue = selected == value
            val previewRadius = if (value == PrefsManager.THEME_CORNER_RADIUS_FOLLOW) 18.dp else value.dp
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .width(92.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        if (selectedValue) 2.dp else 1.dp,
                        if (selectedValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape,
                    )
                    .clickable { onClick(value) }
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 54.dp, height = 34.dp)
                        .clip(RoundedCornerShape(previewRadius))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selectedValue) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = label(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun ThemePreviewCard(
    themeStyle: PrefsManager.AppThemeStyle,
    themeMode: PrefsManager.AppThemeMode,
    accentColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    modernColorStyle: PrefsManager.ModernThemeColorStyle,
    customAccentColor: Color,
    customBackgroundColor: Color,
) {
    val isReference = themeStyle == PrefsManager.AppThemeStyle.NOW_IN_ANDROID ||
        (themeStyle == PrefsManager.AppThemeStyle.MODERN && modernColorStyle == PrefsManager.ModernThemeColorStyle.MODERN)
    val isCleanList = themeStyle == PrefsManager.AppThemeStyle.CLEAN_LIST
    val isGitHub = themeStyle == PrefsManager.AppThemeStyle.GITHUB_DARK
    val isDracula = themeStyle == PrefsManager.AppThemeStyle.DRACULA
    val accent = when {
        accentColor == PrefsManager.ThemeColor.CUSTOM -> customAccentColor
        isGitHub -> githubAccentPreviewColor(accentColor)
        isDracula -> draculaAccentPreviewColor(accentColor)
        else -> themeAccentPreviewColor(accentColor)
    }
    val background = when {
        isGitHub -> Color(0xFF0D1117)
        isDracula -> Color(0xFF282A36)
        isCleanList -> Color(0xFFF0F2F3)
        backgroundColor == PrefsManager.ThemeBackgroundColor.CUSTOM -> customBackgroundColor
        else -> themeBackgroundPreviewColor(backgroundColor)
    }
    val foreground = if (isGitHub) Color(0xFFC9D1D9) else if (isDracula) Color(0xFFF8F8F2) else Color(0xFF0F172A)
    val muted = if (isGitHub) Color(0xFF8B949E) else if (isDracula) Color(0xFFCFCBD8) else Color(0xFF64748B)
    val chipSurface = if (isGitHub) Color(0xFF161B22) else if (isDracula) Color(0xFF44475A) else Color.White
    val isModern = themeStyle != PrefsManager.AppThemeStyle.CLASSIC
    val shape = RoundedCornerShape(
        when {
            isGitHub -> 12.dp
            isDracula -> 14.dp
            isCleanList -> 28.dp
            isModern -> 30.dp
            else -> 22.dp
        },
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (isModern) 8.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(background)
            .border(1.dp, accent.copy(alpha = if (isModern) 0.22f else 0.28f), shape)
            .padding(if (isModern) 18.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "主题预览",
                    style = MaterialTheme.typography.titleMedium,
                    color = foreground,
                )
                Text(
                    text = if (isDracula) {
                        "${themeStyleLabel(themeStyle)} · 固定暗色 · ${themeColorLabel(accentColor)}霓虹"
                    } else if (isGitHub) {
                        "${themeStyleLabel(themeStyle)} · 固定暗色 · ${themeColorLabel(accentColor)}链接色"
                    } else {
                        if (themeStyle == PrefsManager.AppThemeStyle.MODERN) {
                            "${themeStyleLabel(themeStyle)} · ${modernThemeColorStyleLabel(modernColorStyle)}色彩 · ${themeModeLabel(themeMode)}"
                        } else {
                            "${themeStyleLabel(themeStyle)} · ${themeModeLabel(themeMode)} · ${themeColorLabel(accentColor)} · ${themeBackgroundColorLabel(backgroundColor)}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(accent),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreviewChip(text = if (isGitHub) "链接色" else if (isDracula) "霓虹按钮" else if (isCleanList) "强调按钮" else if (isModern) "柔和按钮" else "按钮", color = accent, selected = true)
            ThemePreviewChip(text = if (isGitHub) "仓库卡片" else if (isDracula) "硬边卡片" else if (isCleanList) "白色卡片" else if (isModern) "圆角卡片" else "标签", color = if (isGitHub) Color(0xFF21262D) else if (isDracula) Color(0xFF44475A) else accent.copy(alpha = 0.12f), selected = false, textColor = if (isGitHub || isDracula) foreground else Color(0xFF334155))
            ThemePreviewChip(text = if (isGitHub) "细边框" else if (isReference) "现代色彩" else if (isDracula) "暗色面板" else if (isCleanList) "分组列表" else if (isModern) "按压动效" else "卡片", color = chipSurface, selected = false, textColor = if (isGitHub || isDracula) foreground else Color(0xFF334155))
        }
    }
}

@Composable
internal fun ThemePreviewChip(
    text: String,
    color: Color,
    selected: Boolean,
    textColor: Color = Color(0xFF334155),
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color.White else textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .border(
                width = 1.dp,
                color = if (selected) color else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

fun hashPassword(raw: String): String {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

internal fun themeSummary(
    style: PrefsManager.AppThemeStyle,
    mode: PrefsManager.AppThemeMode,
    modernColorStyle: PrefsManager.ModernThemeColorStyle,
    accentColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
): String {
    if (Locale.getDefault().language == "en") {
        fun Enum<*>.displayName(): String = name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
        return when (style) {
            PrefsManager.AppThemeStyle.DRACULA ->
                "${style.displayName()} · Dark · ${accentColor.displayName()} neon accent"
            PrefsManager.AppThemeStyle.GITHUB_DARK ->
                "${style.displayName()} · Dark · ${accentColor.displayName()} links"
            PrefsManager.AppThemeStyle.MODERN ->
                "${mode.displayName()} · ${style.displayName()} · ${modernColorStyle.displayName()} · ${accentColor.displayName()} accent · ${backgroundColor.displayName()} background"
            else ->
                "${mode.displayName()} · ${style.displayName()} · ${accentColor.displayName()} accent · ${backgroundColor.displayName()} background"
        }
    }
    return when (style) {
        PrefsManager.AppThemeStyle.DRACULA ->
            "${themeStyleLabel(style)} · 固定暗色 · ${themeColorLabel(accentColor)}霓虹强调色"
        PrefsManager.AppThemeStyle.GITHUB_DARK ->
            "${themeStyleLabel(style)} · 固定暗色 · ${themeColorLabel(accentColor)}链接色"
        PrefsManager.AppThemeStyle.MODERN ->
            "${themeModeLabel(mode)} · ${themeStyleLabel(style)} · 色彩：${modernThemeColorStyleLabel(modernColorStyle)} · 强调色：${themeColorLabel(accentColor)} · 背景色：${themeBackgroundColorLabel(backgroundColor)}"
        else ->
            "${themeModeLabel(mode)} · ${themeStyleLabel(style)} · 强调色：${themeColorLabel(accentColor)} · 背景色：${themeBackgroundColorLabel(backgroundColor)}"
    }
}

internal fun themeStyleLabel(style: PrefsManager.AppThemeStyle): String =
    when (style) {
        PrefsManager.AppThemeStyle.CLASSIC -> "经典主题"
        PrefsManager.AppThemeStyle.MODERN -> "圆润主题"
        PrefsManager.AppThemeStyle.NOW_IN_ANDROID -> "圆润主题 · 现代色彩"
        PrefsManager.AppThemeStyle.CLEAN_LIST -> "清爽列表"
        PrefsManager.AppThemeStyle.GITHUB_DARK -> "极夜主题"
        PrefsManager.AppThemeStyle.DRACULA -> "霓彩主题"
    }

internal fun themeStyleSubtitle(style: PrefsManager.AppThemeStyle): String =
    when (style) {
        PrefsManager.AppThemeStyle.CLASSIC -> "保留 KardLeaf 原有的经典界面和视觉效果"
        PrefsManager.AppThemeStyle.MODERN -> "柔和圆角、卡片设置项和按压动画"
        PrefsManager.AppThemeStyle.NOW_IN_ANDROID -> "圆润主题下的现代色彩体系"
        PrefsManager.AppThemeStyle.CLEAN_LIST -> "浅灰背景、白色大圆角分组列表和彩色图标"
        PrefsManager.AppThemeStyle.GITHUB_DARK -> "接近代码仓库界面的极暗配色、蓝色链接和细边框"
        PrefsManager.AppThemeStyle.DRACULA -> "暗色霓虹紫粉风格，按钮和卡片更硬朗"
    }

internal fun modernThemeColorStyleLabel(style: PrefsManager.ModernThemeColorStyle): String =
    when (style) {
        PrefsManager.ModernThemeColorStyle.CLASSIC -> "经典"
        PrefsManager.ModernThemeColorStyle.MODERN -> "现代"
    }

internal fun modernThemeColorStyleSubtitle(style: PrefsManager.ModernThemeColorStyle): String =
    when (style) {
        PrefsManager.ModernThemeColorStyle.CLASSIC -> "使用当前圆润主题的强调色和背景色效果"
        PrefsManager.ModernThemeColorStyle.MODERN -> "使用现代 Material3 色彩效果"
    }

internal fun cleanListFeatureIconStyleLabel(style: PrefsManager.CleanListFeatureIconStyle): String =
    when (style) {
        PrefsManager.CleanListFeatureIconStyle.MODERN -> "现代"
        PrefsManager.CleanListFeatureIconStyle.SIMPLE -> "简约"
    }

internal fun cleanListFeatureIconStyleSubtitle(style: PrefsManager.CleanListFeatureIconStyle): String =
    when (style) {
        PrefsManager.CleanListFeatureIconStyle.MODERN -> "保留不同颜色的功能项图标"
        PrefsManager.CleanListFeatureIconStyle.SIMPLE -> "图标统一跟随当前强调色"
    }

internal fun globalCornerRadiusLabel(radiusDp: Int): String =
    if (radiusDp == PrefsManager.THEME_CORNER_RADIUS_FOLLOW) "跟随主题" else "${radiusDp}dp"

internal fun homeCornerRadiusLabel(radiusDp: Int): String =
    if (radiusDp == PrefsManager.THEME_CORNER_RADIUS_FOLLOW) "跟随全局" else "${radiusDp}dp"

internal fun themeModeLabel(mode: PrefsManager.AppThemeMode): String =
    when (mode) {
        PrefsManager.AppThemeMode.SYSTEM -> "跟随系统"
        PrefsManager.AppThemeMode.LIGHT -> "浅色模式"
        PrefsManager.AppThemeMode.DARK -> "黑夜模式"
    }

internal fun themeModeSubtitle(mode: PrefsManager.AppThemeMode): String =
    when (mode) {
        PrefsManager.AppThemeMode.SYSTEM -> "使用系统当前的浅色/深色设置"
        PrefsManager.AppThemeMode.LIGHT -> "始终使用浅色界面"
        PrefsManager.AppThemeMode.DARK -> "始终使用深色界面"
    }

internal fun themeColorLabel(color: PrefsManager.ThemeColor): String =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> "蓝色"
        PrefsManager.ThemeColor.GREEN -> "青绿色"
        PrefsManager.ThemeColor.PURPLE -> "紫色"
        PrefsManager.ThemeColor.PINK -> "粉色"
        PrefsManager.ThemeColor.AMBER -> "琥珀色"
        PrefsManager.ThemeColor.RED -> "红色"
        PrefsManager.ThemeColor.CUSTOM -> "自定义"
    }

internal fun themeColorSubtitle(color: PrefsManager.ThemeColor): String =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> "默认蓝色强调色"
        PrefsManager.ThemeColor.GREEN -> "自然叶子风格"
        PrefsManager.ThemeColor.PURPLE -> "柔和效率风格"
        PrefsManager.ThemeColor.PINK -> "轻柔生活风格"
        PrefsManager.ThemeColor.AMBER -> "温暖阅读风格"
        PrefsManager.ThemeColor.RED -> "醒目强调风格"
        PrefsManager.ThemeColor.CUSTOM -> "自定义强调色"
    }

internal fun themeBackgroundColorLabel(color: PrefsManager.ThemeBackgroundColor): String =
    when (color) {
        PrefsManager.ThemeBackgroundColor.WHITE -> "白色"
        PrefsManager.ThemeBackgroundColor.BLUE -> "淡蓝色"
        PrefsManager.ThemeBackgroundColor.GREEN -> "淡绿色"
        PrefsManager.ThemeBackgroundColor.PURPLE -> "淡紫色"
        PrefsManager.ThemeBackgroundColor.PINK -> "淡粉色"
        PrefsManager.ThemeBackgroundColor.AMBER -> "淡琥珀色"
        PrefsManager.ThemeBackgroundColor.GRAY -> "浅灰色"
        PrefsManager.ThemeBackgroundColor.CUSTOM -> "自定义"
    }

internal fun themeBackgroundColorSubtitle(color: PrefsManager.ThemeBackgroundColor): String =
    when (color) {
        PrefsManager.ThemeBackgroundColor.WHITE -> "默认白色背景"
        PrefsManager.ThemeBackgroundColor.BLUE -> "清爽蓝色背景"
        PrefsManager.ThemeBackgroundColor.GREEN -> "柔和自然背景"
        PrefsManager.ThemeBackgroundColor.PURPLE -> "淡紫氛围背景"
        PrefsManager.ThemeBackgroundColor.PINK -> "温柔浅粉背景"
        PrefsManager.ThemeBackgroundColor.AMBER -> "暖色阅读背景"
        PrefsManager.ThemeBackgroundColor.GRAY -> "中性浅灰背景"
        PrefsManager.ThemeBackgroundColor.CUSTOM -> "自定义背景色"
    }

internal fun themeAccentPreviewColor(color: PrefsManager.ThemeColor): Color =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> Color(0xFF3B82F6)
        PrefsManager.ThemeColor.GREEN -> Color(0xFF00856F)
        PrefsManager.ThemeColor.PURPLE -> Color(0xFF7C3AED)
        PrefsManager.ThemeColor.PINK -> Color(0xFFB83263)
        PrefsManager.ThemeColor.AMBER -> Color(0xFF956300)
        PrefsManager.ThemeColor.RED -> Color(0xFFDC2626)
        PrefsManager.ThemeColor.CUSTOM -> Color(0xFF3B82F6)
    }

internal fun draculaAccentPreviewColor(color: PrefsManager.ThemeColor): Color =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> Color(0xFF8BE9FD)
        PrefsManager.ThemeColor.GREEN -> Color(0xFF50FA7B)
        PrefsManager.ThemeColor.PURPLE -> Color(0xFFBD93F9)
        PrefsManager.ThemeColor.PINK -> Color(0xFFFF79C6)
        PrefsManager.ThemeColor.AMBER -> Color(0xFFFFB86C)
        PrefsManager.ThemeColor.RED -> Color(0xFFFF5555)
        PrefsManager.ThemeColor.CUSTOM -> Color(0xFFBD93F9)
    }

internal fun githubAccentPreviewColor(color: PrefsManager.ThemeColor): Color =
    when (color) {
        PrefsManager.ThemeColor.BLUE -> Color(0xFF58A6FF)
        PrefsManager.ThemeColor.GREEN -> Color(0xFF7EE787)
        PrefsManager.ThemeColor.PURPLE -> Color(0xFFD2A8FF)
        PrefsManager.ThemeColor.PINK -> Color(0xFFFFA6D1)
        PrefsManager.ThemeColor.AMBER -> Color(0xFFE3B341)
        PrefsManager.ThemeColor.RED -> Color(0xFFFF7B72)
        PrefsManager.ThemeColor.CUSTOM -> Color(0xFF58A6FF)
    }

internal fun themeBackgroundPreviewColor(color: PrefsManager.ThemeBackgroundColor): Color =
    when (color) {
        PrefsManager.ThemeBackgroundColor.WHITE -> Color.White
        PrefsManager.ThemeBackgroundColor.BLUE -> Color(0xFFF4FAFF)
        PrefsManager.ThemeBackgroundColor.GREEN -> Color(0xFFF2FCF8)
        PrefsManager.ThemeBackgroundColor.PURPLE -> Color(0xFFFAF7FF)
        PrefsManager.ThemeBackgroundColor.PINK -> Color(0xFFFFF7FA)
        PrefsManager.ThemeBackgroundColor.AMBER -> Color(0xFFFFFAEF)
        PrefsManager.ThemeBackgroundColor.GRAY -> Color(0xFFF8FAFC)
        PrefsManager.ThemeBackgroundColor.CUSTOM -> Color.White
    }
