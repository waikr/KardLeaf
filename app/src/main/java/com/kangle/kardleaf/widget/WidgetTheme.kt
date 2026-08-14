package com.kangle.kardleaf.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import com.kangle.kardleaf.data.utils.KardLeafLog

internal const val WIDGET_THEME_LOG_TAG = "KardLeafWidgetTheme"

object WidgetTheme {
    enum class Kind(val key: String) {
        NOTE("note"),
        TASK("task"),
        DAILY("daily"),
    }

    enum class Preset(val label: String) {
        SYSTEM("默认主题"),
        DARK("深夜墨黑"),
        PAPER("纸张暖白"),
        FOREST("森林绿"),
        OCEAN("海盐蓝"),
        CUSTOM("自定义"),
    }

    data class Settings(
        val preset: Preset = Preset.SYSTEM,
        val customAccent: Int = 0xFF2563EB.toInt(),
        val customBackground: Int = 0xFFF8FAFC.toInt(),
    )

    data class Palette(
        val background: Int,
        val surface: Int,
        val accent: Int,
        val onSurface: Int,
        val muted: Int,
    )

    private const val PREFS_NAME = "kardleaf_widget_theme"
    private const val DEFAULT_ACCENT = 0xFF2563EB.toInt()
    private const val DEFAULT_BACKGROUND = 0xFFF8FAFC.toInt()
    private const val DEFAULT_ON_SURFACE = 0xFF0F172A.toInt()
    private const val DEFAULT_MUTED = 0xFF64748B.toInt()

    fun load(context: Context, kind: Kind, appWidgetId: Int): Settings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val prefix = keyPrefix(kind, appWidgetId)
        val preset = runCatching {
            Preset.valueOf(prefs.getString("${prefix}preset", Preset.SYSTEM.name) ?: Preset.SYSTEM.name)
        }.getOrDefault(Preset.SYSTEM)
        return Settings(
            preset = preset,
            customAccent = prefs.getInt("${prefix}accent", DEFAULT_ACCENT),
            customBackground = prefs.getInt("${prefix}background", DEFAULT_BACKGROUND),
        )
    }

    fun save(context: Context, kind: Kind, appWidgetId: Int, settings: Settings) {
        val prefix = keyPrefix(kind, appWidgetId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("${prefix}preset", settings.preset.name)
            .putInt("${prefix}accent", settings.customAccent)
            .putInt("${prefix}background", settings.customBackground)
            .apply()
        KardLeafLog.i(
            WIDGET_THEME_LOG_TAG,
            "theme saved kind=${kind.name} widgetId=$appWidgetId preset=${settings.preset.name} " +
                "accent=${Integer.toHexString(settings.customAccent)} background=${Integer.toHexString(settings.customBackground)}",
        )
    }

    fun clear(context: Context, kind: Kind, appWidgetId: Int) {
        val prefix = keyPrefix(kind, appWidgetId)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("${prefix}preset")
            .remove("${prefix}accent")
            .remove("${prefix}background")
            .apply()
    }

    fun palette(context: Context, kind: Kind, appWidgetId: Int): Palette =
        palette(context, load(context, kind, appWidgetId))

    fun configuredPalette(context: Context, kind: Kind, appWidgetId: Int): Palette? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains("${keyPrefix(kind, appWidgetId)}preset")) {
            palette(context, kind, appWidgetId)
        } else {
            null
        }
    }

    fun palette(context: Context, settings: Settings): Palette = when (settings.preset) {
        Preset.SYSTEM -> defaultPalette()
        Preset.DARK -> Palette(
            background = 0xFF111827.toInt(),
            surface = 0xFF1F2937.toInt(),
            accent = 0xFF60A5FA.toInt(),
            onSurface = 0xFFF9FAFB.toInt(),
            muted = 0xFFCBD5E1.toInt(),
        )
        Preset.PAPER -> Palette(
            background = 0xFFFFF8E7.toInt(),
            surface = 0xFFFFFDF5.toInt(),
            accent = 0xFFA66A2C.toInt(),
            onSurface = 0xFF3B2B20.toInt(),
            muted = 0xFF806A5A.toInt(),
        )
        Preset.FOREST -> Palette(
            background = 0xFFEFF8F1.toInt(),
            surface = 0xFFFAFFFB.toInt(),
            accent = 0xFF2F7D52.toInt(),
            onSurface = 0xFF1E3427.toInt(),
            muted = 0xFF5C7765.toInt(),
        )
        Preset.OCEAN -> Palette(
            background = 0xFFEAF7FB.toInt(),
            surface = 0xFFFAFEFF.toInt(),
            accent = 0xFF237B94.toInt(),
            onSurface = 0xFF15333B.toInt(),
            muted = 0xFF55747D.toInt(),
        )
        Preset.CUSTOM -> customPalette(settings.customAccent, settings.customBackground)
    }

    fun applyBackground(views: RemoteViews, viewId: Int, color: Int?) {
        if (color == null) return
        // ponytail: API 30 and below cannot tint RemoteViews drawables; add per-preset drawables if legacy theme parity matters.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        views.setColorStateList(viewId, "setBackgroundTintList", ColorStateList.valueOf(color))
    }

    fun applyText(views: RemoteViews, viewId: Int, color: Int?) {
        if (color == null) return
        views.setInt(viewId, "setTextColor", color)
    }

    fun applyIcon(views: RemoteViews, viewId: Int, color: Int?) {
        if (color == null) return
        views.setInt(viewId, "setColorFilter", color)
    }

    private fun keyPrefix(kind: Kind, appWidgetId: Int): String = "${kind.key}_${appWidgetId}_"

    private fun defaultPalette(): Palette = Palette(
        background = DEFAULT_BACKGROUND,
        surface = Color.WHITE,
        accent = DEFAULT_ACCENT,
        onSurface = DEFAULT_ON_SURFACE,
        muted = DEFAULT_MUTED,
    )

    private fun customPalette(accent: Int, background: Int): Palette {
        val light = isLight(background)
        return Palette(
            background = background,
            surface = mix(background, if (light) Color.WHITE else Color.BLACK, if (light) 0.45f else 0.25f),
            accent = accent,
            onSurface = if (light) 0xFF18212B.toInt() else 0xFFF8FAFC.toInt(),
            muted = if (light) 0xFF52616F.toInt() else 0xFFCBD5E1.toInt(),
        )
    }

    private fun isLight(color: Int): Boolean =
        (Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f) / 255f > 0.55f

    private fun mix(first: Int, second: Int, amount: Float): Int {
        fun channel(value: (Int) -> Int): Int =
            (value(first) + (value(second) - value(first)) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(channel(Color::red), channel(Color::green), channel(Color::blue))
    }
}
