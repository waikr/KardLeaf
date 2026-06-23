package com.kangle.kardleaf.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kangle.kardleaf.data.repository.PrefsManager
import com.kangle.kardleaf.ui.KardLeafCustomFeatures

private val DarkColorScheme =
    darkColorScheme(
        primary = PrimaryDark,
        onPrimary = OnPrimaryDark,
        primaryContainer = PrimaryContainerDark,
        onPrimaryContainer = OnPrimaryContainerDark,
        secondary = SecondaryDark,
        onSecondary = OnSecondaryDark,
        secondaryContainer = SecondaryContainerDark,
        onSecondaryContainer = OnSecondaryContainerDark,
        tertiary = TertiaryDark,
        onTertiary = OnTertiaryDark,
        tertiaryContainer = TertiaryContainerDark,
        onTertiaryContainer = OnTertiaryContainerDark,
        background = BackgroundDark,
        onBackground = OnBackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        onSurfaceVariant = OnSurfaceVariantDark,
        outline = OutlineDark,
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = PrimaryLight,
        onPrimary = OnPrimaryLight,
        primaryContainer = PrimaryContainerLight,
        onPrimaryContainer = OnPrimaryContainerLight,
        secondary = SecondaryLight,
        onSecondary = OnSecondaryLight,
        secondaryContainer = SecondaryContainerLight,
        onSecondaryContainer = OnSecondaryContainerLight,
        tertiary = TertiaryLight,
        onTertiary = OnTertiaryLight,
        tertiaryContainer = TertiaryContainerLight,
        onTertiaryContainer = OnTertiaryContainerLight,
        background = BackgroundLight,
        onBackground = OnBackgroundLight,
        surface = SurfaceLight,
        onSurface = OnSurfaceLight,
        surfaceVariant = SurfaceVariantLight,
        onSurfaceVariant = OnSurfaceVariantLight,
        outline = OutlineLight,
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
    )

@Composable
fun KardLeafTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = KardLeafCustomFeatures.UseDynamicColor,
    themeRevision: Int = 0,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefsManager = remember(context, themeRevision) { PrefsManager(context) }
    val themeColor = prefsManager.getThemeColor()
    val themeBackgroundColor = prefsManager.getThemeBackgroundColor()
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }.withThemeColor(themeColor, themeBackgroundColor, darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // OR surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

private fun androidx.compose.material3.ColorScheme.withThemeColor(
    themeColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
) = withAccentColor(themeColor, darkTheme)
    .withBackgroundColor(backgroundColor, darkTheme)

private data class ThemeAccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

private fun themeAccentPalette(
    themeColor: PrefsManager.ThemeColor,
    darkTheme: Boolean,
): ThemeAccentPalette = when (themeColor) {
    PrefsManager.ThemeColor.BLUE -> ThemeAccentPalette(
        primary = if (darkTheme) PrimaryDark else PrimaryLight,
        onPrimary = if (darkTheme) OnPrimaryDark else OnPrimaryLight,
        primaryContainer = if (darkTheme) PrimaryContainerDark else PrimaryContainerLight,
        onPrimaryContainer = if (darkTheme) OnPrimaryContainerDark else OnPrimaryContainerLight,
    )
    PrefsManager.ThemeColor.GREEN -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFF7DDDC3) else Color(0xFF00856F),
        onPrimary = if (darkTheme) Color(0xFF00382E) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF005244) else Color(0xFFC8F4E8),
        onPrimaryContainer = if (darkTheme) Color(0xFFC8F4E8) else Color(0xFF00382E),
    )
    PrefsManager.ThemeColor.PURPLE -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFD8B4FE) else Color(0xFF7C3AED),
        onPrimary = if (darkTheme) Color(0xFF3B0764) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF56308E) else Color(0xFFEDE9FE),
        onPrimaryContainer = if (darkTheme) Color(0xFFEDE9FE) else Color(0xFF3B0764),
    )
    PrefsManager.ThemeColor.PINK -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFFFB1C8) else Color(0xFFB83263),
        onPrimary = if (darkTheme) Color(0xFF4A0B25) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF7A1F42) else Color(0xFFFFD9E4),
        onPrimaryContainer = if (darkTheme) Color(0xFFFFD9E4) else Color(0xFF4A0B25),
    )
    PrefsManager.ThemeColor.AMBER -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFFFD27A) else Color(0xFF956300),
        onPrimary = if (darkTheme) Color(0xFF332100) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF624000) else Color(0xFFFFE2A8),
        onPrimaryContainer = if (darkTheme) Color(0xFFFFE2A8) else Color(0xFF332100),
    )
    PrefsManager.ThemeColor.RED -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFFFB4A8) else Color(0xFFDC2626),
        onPrimary = if (darkTheme) Color(0xFF450A0A) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF7F1D1D) else Color(0xFFFFE2E2),
        onPrimaryContainer = if (darkTheme) Color(0xFFFFE2E2) else Color(0xFF450A0A),
    )
}

private fun androidx.compose.material3.ColorScheme.withAccentColor(
    themeColor: PrefsManager.ThemeColor,
    darkTheme: Boolean,
): androidx.compose.material3.ColorScheme {
    val accent = themeAccentPalette(themeColor, darkTheme)
    return copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer,
        onPrimaryContainer = accent.onPrimaryContainer,
        inversePrimary = accent.primary,
        secondary = accent.primary,
        onSecondary = accent.onPrimary,
        secondaryContainer = accent.primaryContainer,
        onSecondaryContainer = accent.onPrimaryContainer,
        tertiary = accent.primary,
        onTertiary = accent.onPrimary,
        tertiaryContainer = accent.primaryContainer,
        onTertiaryContainer = accent.onPrimaryContainer,
        surfaceTint = accent.primary,
    )
}

private data class ThemeBackgroundPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

private fun themeBackgroundPalette(
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
): ThemeBackgroundPalette = when (backgroundColor) {
    PrefsManager.ThemeBackgroundColor.WHITE -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF111827) else Color.White,
        surface = if (darkTheme) Color(0xFF1F2937) else Color.White,
        surfaceVariant = if (darkTheme) Color(0xFF374151) else Color(0xFFF1F5F9),
        onSurfaceVariant = if (darkTheme) Color(0xFFD1D5DB) else Color(0xFF64748B),
        outline = if (darkTheme) Color(0xFF6B7280) else Color(0xFFCBD5E1),
        outlineVariant = if (darkTheme) Color(0xFF374151) else Color(0xFFE2E8F0),
    )
    PrefsManager.ThemeBackgroundColor.BLUE -> ThemeBackgroundPalette(
        background = if (darkTheme) BackgroundDark else BackgroundLight,
        surface = if (darkTheme) SurfaceDark else SurfaceLight,
        surfaceVariant = if (darkTheme) SurfaceVariantDark else SurfaceVariantLight,
        onSurfaceVariant = if (darkTheme) OnSurfaceVariantDark else OnSurfaceVariantLight,
        outline = if (darkTheme) OutlineDark else OutlineLight,
        outlineVariant = if (darkTheme) Color(0xFF2B3B4D) else Color(0xFFD8E6FB),
    )
    PrefsManager.ThemeBackgroundColor.GREEN -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF0F1F1A) else Color(0xFFF2FCF8),
        surface = if (darkTheme) Color(0xFF142820) else Color(0xFFFBFFFD),
        surfaceVariant = if (darkTheme) Color(0xFF27443B) else Color(0xFFE0F5EE),
        onSurfaceVariant = if (darkTheme) Color(0xFFC9DED6) else Color(0xFF4C635B),
        outline = if (darkTheme) Color(0xFF6D9284) else Color(0xFFA7CFC2),
        outlineVariant = if (darkTheme) Color(0xFF2E4B42) else Color(0xFFD1E9E1),
    )
    PrefsManager.ThemeBackgroundColor.PURPLE -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF1B1328) else Color(0xFFFAF7FF),
        surface = if (darkTheme) Color(0xFF251A35) else Color(0xFFFFFBFF),
        surfaceVariant = if (darkTheme) Color(0xFF43305D) else Color(0xFFF0E7FF),
        onSurfaceVariant = if (darkTheme) Color(0xFFD8C8E8) else Color(0xFF62556D),
        outline = if (darkTheme) Color(0xFF907EA4) else Color(0xFFCAB7E4),
        outlineVariant = if (darkTheme) Color(0xFF493762) else Color(0xFFE6D8FA),
    )
    PrefsManager.ThemeBackgroundColor.PINK -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF24131A) else Color(0xFFFFF7FA),
        surface = if (darkTheme) Color(0xFF2D1821) else Color(0xFFFFFBFD),
        surfaceVariant = if (darkTheme) Color(0xFF4B2B37) else Color(0xFFFFE8EF),
        onSurfaceVariant = if (darkTheme) Color(0xFFE8C8D3) else Color(0xFF71545E),
        outline = if (darkTheme) Color(0xFFA77B8B) else Color(0xFFE4B7C6),
        outlineVariant = if (darkTheme) Color(0xFF52313E) else Color(0xFFF7D6E1),
    )
    PrefsManager.ThemeBackgroundColor.AMBER -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF211A0D) else Color(0xFFFFFAEF),
        surface = if (darkTheme) Color(0xFF2A2111) else Color(0xFFFFFCF6),
        surfaceVariant = if (darkTheme) Color(0xFF46371B) else Color(0xFFFFEBC5),
        onSurfaceVariant = if (darkTheme) Color(0xFFE2D0A8) else Color(0xFF6D5B33),
        outline = if (darkTheme) Color(0xFFA48A50) else Color(0xFFE1C17A),
        outlineVariant = if (darkTheme) Color(0xFF4E3E20) else Color(0xFFF3DAA6),
    )
    PrefsManager.ThemeBackgroundColor.GRAY -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF15181D) else Color(0xFFF8FAFC),
        surface = if (darkTheme) Color(0xFF1E232B) else Color.White,
        surfaceVariant = if (darkTheme) Color(0xFF343B47) else Color(0xFFE2E8F0),
        onSurfaceVariant = if (darkTheme) Color(0xFFD1D5DB) else Color(0xFF64748B),
        outline = if (darkTheme) Color(0xFF6B7280) else Color(0xFFCBD5E1),
        outlineVariant = if (darkTheme) Color(0xFF374151) else Color(0xFFE2E8F0),
    )
}

private fun androidx.compose.material3.ColorScheme.withBackgroundColor(
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
): androidx.compose.material3.ColorScheme {
    val background = themeBackgroundPalette(backgroundColor, darkTheme)
    return copy(
        background = background.background,
        surface = background.surface,
        surfaceVariant = background.surfaceVariant,
        onSurfaceVariant = background.onSurfaceVariant,
        outline = background.outline,
        outlineVariant = background.outlineVariant,
        surfaceContainerLowest = background.surface,
        surfaceContainerLow = background.surface,
        surfaceContainer = background.surface,
        surfaceContainerHigh = background.surface,
        surfaceContainerHighest = background.surfaceVariant,
        surfaceBright = background.surface,
        surfaceDim = background.background,
        surfaceTint = background.surface,
        inverseSurface = if (darkTheme) Color(0xFFE5E7EB) else Color(0xFF1F2937),
        inverseOnSurface = if (darkTheme) Color(0xFF111827) else Color(0xFFF9FAFB),
    )
}
