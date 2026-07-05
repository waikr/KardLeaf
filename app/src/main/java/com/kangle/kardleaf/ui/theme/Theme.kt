package com.kangle.kardleaf.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
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

private val ReferenceLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF006D36),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD3E8D3),
        onPrimaryContainer = Color(0xFF0D1F12),
        secondary = Color(0xFF4F6352),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3E8D3),
        onSecondaryContainer = Color(0xFF0D1F12),
        tertiary = Color(0xFF3A656F),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFBEEAF6),
        onTertiaryContainer = Color(0xFF001F26),
        background = Color(0xFFFBFDF7),
        onBackground = Color(0xFF1A1C1A),
        surface = Color(0xFFFBFDF7),
        onSurface = Color(0xFF1A1C1A),
        surfaceVariant = Color(0xFFDDE5DB),
        onSurfaceVariant = Color(0xFF414941),
        outline = Color(0xFF727971),
        error = ErrorLight,
        onError = OnErrorLight,
        errorContainer = ErrorContainerLight,
        onErrorContainer = OnErrorContainerLight,
    ).copy(
        outlineVariant = Color(0xFFC1C9BF),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF0F1EC),
        surfaceContainer = Color(0xFFF0F1EC),
        surfaceContainerHigh = Color(0xFFE2E3DE),
        surfaceContainerHighest = Color(0xFFDDE5DB),
        surfaceTint = Color(0xFF006D36),
    )

private val ReferenceDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF0EE37C),
        onPrimary = Color(0xFF003919),
        primaryContainer = Color(0xFF005227),
        onPrimaryContainer = Color(0xFF5AFF9D),
        secondary = Color(0xFFB7CCB8),
        onSecondary = Color(0xFF223526),
        secondaryContainer = Color(0xFF394B3C),
        onSecondaryContainer = Color(0xFFD3E8D3),
        tertiary = Color(0xFFA2CED9),
        onTertiary = Color(0xFF02363F),
        tertiaryContainer = Color(0xFF214D56),
        onTertiaryContainer = Color(0xFFBEEAF6),
        background = Color(0xFF1A1C1A),
        onBackground = Color(0xFFE2E3DE),
        surface = Color(0xFF1A1C1A),
        onSurface = Color(0xFFE2E3DE),
        surfaceVariant = Color(0xFF414941),
        onSurfaceVariant = Color(0xFFC1C9BF),
        outline = Color(0xFF8B938A),
        error = ErrorDark,
        onError = OnErrorDark,
        errorContainer = ErrorContainerDark,
        onErrorContainer = OnErrorContainerDark,
    ).copy(
        outlineVariant = Color(0xFF2F312E),
        surfaceContainerLowest = Color(0xFF0D0F0D),
        surfaceContainerLow = Color(0xFF1A1C1A),
        surfaceContainer = Color(0xFF1F211E),
        surfaceContainerHigh = Color(0xFF2F312E),
        surfaceContainerHighest = Color(0xFF414941),
        surfaceTint = Color(0xFF0EE37C),
    )

private val DraculaColorScheme =
    darkColorScheme(
        primary = Color(0xFFBD93F9),
        onPrimary = Color(0xFF282A36),
        primaryContainer = Color(0xFF44475A),
        onPrimaryContainer = Color(0xFFF8F8F2),
        secondary = Color(0xFFFF79C6),
        onSecondary = Color(0xFF282A36),
        secondaryContainer = Color(0xFF5A3555),
        onSecondaryContainer = Color(0xFFFFD6EF),
        tertiary = Color(0xFF8BE9FD),
        onTertiary = Color(0xFF10232A),
        tertiaryContainer = Color(0xFF2D5A67),
        onTertiaryContainer = Color(0xFFD9FBFF),
        background = Color(0xFF282A36),
        onBackground = Color(0xFFF8F8F2),
        surface = Color(0xFF282A36),
        onSurface = Color(0xFFF8F8F2),
        surfaceVariant = Color(0xFF44475A),
        onSurfaceVariant = Color(0xFFCFCBD8),
        outline = Color(0xFF6272A4),
        error = Color(0xFFFF5555),
        onError = Color(0xFF2A0E12),
        errorContainer = Color(0xFF6E2A33),
        onErrorContainer = Color(0xFFFFD8DD),
    ).copy(
        outlineVariant = Color(0xFF3A3D4F),
        surfaceContainerLowest = Color(0xFF1E1F29),
        surfaceContainerLow = Color(0xFF282A36),
        surfaceContainer = Color(0xFF303241),
        surfaceContainerHigh = Color(0xFF383A4B),
        surfaceContainerHighest = Color(0xFF44475A),
        surfaceBright = Color(0xFF3A3D4F),
        surfaceDim = Color(0xFF1E1F29),
        surfaceTint = Color(0xFFBD93F9),
        inverseSurface = Color(0xFFF8F8F2),
        inverseOnSurface = Color(0xFF282A36),
    )

private val GitHubDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF58A6FF),
        onPrimary = Color(0xFF0D1117),
        primaryContainer = Color(0xFF1F6FEB),
        onPrimaryContainer = Color(0xFFF0F6FC),
        secondary = Color(0xFF8B949E),
        onSecondary = Color(0xFF0D1117),
        secondaryContainer = Color(0xFF21262D),
        onSecondaryContainer = Color(0xFFC9D1D9),
        tertiary = Color(0xFF7EE787),
        onTertiary = Color(0xFF0D1117),
        tertiaryContainer = Color(0xFF238636),
        onTertiaryContainer = Color(0xFFF0F6FC),
        background = Color(0xFF0D1117),
        onBackground = Color(0xFFC9D1D9),
        surface = Color(0xFF161B22),
        onSurface = Color(0xFFC9D1D9),
        surfaceVariant = Color(0xFF21262D),
        onSurfaceVariant = Color(0xFF8B949E),
        outline = Color(0xFF30363D),
        error = Color(0xFFFF7B72),
        onError = Color(0xFF0D1117),
        errorContainer = Color(0xFFDA3633),
        onErrorContainer = Color(0xFFF0F6FC),
    ).copy(
        outlineVariant = Color(0xFF30363D),
        surfaceContainerLowest = Color(0xFF010409),
        surfaceContainerLow = Color(0xFF0D1117),
        surfaceContainer = Color(0xFF161B22),
        surfaceContainerHigh = Color(0xFF21262D),
        surfaceContainerHighest = Color(0xFF30363D),
        surfaceBright = Color(0xFF21262D),
        surfaceDim = Color(0xFF010409),
        surfaceTint = Color(0xFF58A6FF),
        inverseSurface = Color(0xFFC9D1D9),
        inverseOnSurface = Color(0xFF0D1117),
    )

val LocalKardLeafThemeStyle =
    staticCompositionLocalOf { PrefsManager.AppThemeStyle.CLASSIC }

val LocalKardLeafThemeMode =
    staticCompositionLocalOf { PrefsManager.AppThemeMode.SYSTEM }

val LocalKardLeafGlobalCornerRadiusDp =
    staticCompositionLocalOf { PrefsManager.THEME_CORNER_RADIUS_FOLLOW }

val LocalKardLeafHomeCornerRadiusDp =
    staticCompositionLocalOf { PrefsManager.THEME_CORNER_RADIUS_FOLLOW }

private val ModernShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

private val DraculaShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(12.dp),
        extraLarge = RoundedCornerShape(16.dp),
    )

private val CleanListShapes =
    Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(34.dp),
    )

private val GitHubShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(8.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(12.dp),
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
    val themeStyle = prefsManager.getAppThemeStyle()
    val themeMode = prefsManager.getAppThemeMode()
    val themeColor = prefsManager.getThemeColor()
    val customThemeColor = Color(prefsManager.getCustomThemeColorArgb())
    val themeBackgroundColor = prefsManager.getThemeBackgroundColor()
    val modernThemeColorStyle = prefsManager.getModernThemeColorStyle()
    val customThemeBackgroundColor = Color(prefsManager.getCustomThemeBackgroundColorArgb())
    val globalCornerRadiusDp = prefsManager.getGlobalCornerRadiusDp()
    val homeCornerRadiusDp = prefsManager.getHomeCornerRadiusDp()
    val effectiveDarkTheme = themeStyle == PrefsManager.AppThemeStyle.DRACULA ||
        themeStyle == PrefsManager.AppThemeStyle.GITHUB_DARK ||
        when (themeMode) {
            PrefsManager.AppThemeMode.SYSTEM -> darkTheme
            PrefsManager.AppThemeMode.LIGHT -> false
            PrefsManager.AppThemeMode.DARK -> true
        }
    val baseColorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (effectiveDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            effectiveDarkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
    val colorScheme =
        when (themeStyle) {
            PrefsManager.AppThemeStyle.CLASSIC ->
                baseColorScheme.withThemeColor(
                    themeColor,
                    themeBackgroundColor,
                    effectiveDarkTheme,
                    customThemeColor,
                    customThemeBackgroundColor,
                )
            PrefsManager.AppThemeStyle.MODERN ->
                if (modernThemeColorStyle == PrefsManager.ModernThemeColorStyle.MODERN) {
                    (if (effectiveDarkTheme) ReferenceDarkColorScheme else ReferenceLightColorScheme)
                        .withAccentColor(themeColor, effectiveDarkTheme, customThemeColor)
                        .withBackgroundColor(themeBackgroundColor, effectiveDarkTheme, customThemeBackgroundColor)
                } else {
                    baseColorScheme.withModernThemeColor(
                        themeColor,
                        themeBackgroundColor,
                        effectiveDarkTheme,
                        customThemeColor,
                        customThemeBackgroundColor,
                    )
                }
            PrefsManager.AppThemeStyle.NOW_IN_ANDROID ->
                (if (effectiveDarkTheme) ReferenceDarkColorScheme else ReferenceLightColorScheme)
                    .withAccentColor(themeColor, effectiveDarkTheme, customThemeColor)
                    .withBackgroundColor(themeBackgroundColor, effectiveDarkTheme, customThemeBackgroundColor)
            PrefsManager.AppThemeStyle.CLEAN_LIST ->
                baseColorScheme.withCleanListThemeColor(
                    themeColor,
                    themeBackgroundColor,
                    effectiveDarkTheme,
                    customThemeColor,
                    customThemeBackgroundColor,
                )
            PrefsManager.AppThemeStyle.GITHUB_DARK ->
                GitHubDarkColorScheme.withGitHubThemeColor(themeColor, customThemeColor)
            PrefsManager.AppThemeStyle.DRACULA ->
                DraculaColorScheme.withDraculaThemeColor(themeColor, customThemeColor)
        }
    val themeShapes =
        when (themeStyle) {
            PrefsManager.AppThemeStyle.CLASSIC -> Shapes()
            PrefsManager.AppThemeStyle.DRACULA -> DraculaShapes
            PrefsManager.AppThemeStyle.CLEAN_LIST -> CleanListShapes
            PrefsManager.AppThemeStyle.GITHUB_DARK -> GitHubShapes
            else -> ModernShapes
        }
    val shapes = if (globalCornerRadiusDp >= 0) {
        cornerRadiusShapes(globalCornerRadiusDp)
    } else {
        themeShapes
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // OR surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !effectiveDarkTheme
        }
    }

    CompositionLocalProvider(
        LocalKardLeafThemeStyle provides themeStyle,
        LocalKardLeafThemeMode provides themeMode,
        LocalKardLeafGlobalCornerRadiusDp provides globalCornerRadiusDp,
        LocalKardLeafHomeCornerRadiusDp provides homeCornerRadiusDp,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = shapes,
            content = content,
        )
    }
}

private fun cornerRadiusShapes(radiusDp: Int): Shapes {
    val radius = RoundedCornerShape(radiusDp.dp)
    return Shapes(
        extraSmall = radius,
        small = radius,
        medium = radius,
        large = radius,
        extraLarge = radius,
    )
}

private fun androidx.compose.material3.ColorScheme.withThemeColor(
    themeColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
    customAccentColor: Color,
    customBackgroundColor: Color,
) = withAccentColor(themeColor, darkTheme, customAccentColor)
    .withBackgroundColor(backgroundColor, darkTheme, customBackgroundColor)

private data class ThemeAccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

private fun contentColorFor(color: Color): Color =
    if (color.luminance() > 0.55f) Color(0xFF111827) else Color.White

private fun customAccentPalette(color: Color): ThemeAccentPalette {
    val content = contentColorFor(color)
    return ThemeAccentPalette(
        primary = color,
        onPrimary = content,
        primaryContainer = color.copy(alpha = 0.20f),
        onPrimaryContainer = contentColorFor(color.copy(alpha = 0.20f)),
    )
}

private fun themeAccentPalette(
    themeColor: PrefsManager.ThemeColor,
    darkTheme: Boolean,
    customAccentColor: Color,
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
    PrefsManager.ThemeColor.CUSTOM -> customAccentPalette(customAccentColor)
}

private fun androidx.compose.material3.ColorScheme.withAccentColor(
    themeColor: PrefsManager.ThemeColor,
    darkTheme: Boolean,
    customAccentColor: Color,
): androidx.compose.material3.ColorScheme {
    val accent = themeAccentPalette(themeColor, darkTheme, customAccentColor)
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

private fun draculaThemeAccentPalette(
    themeColor: PrefsManager.ThemeColor,
    customAccentColor: Color,
): ThemeAccentPalette =
    when (themeColor) {
        PrefsManager.ThemeColor.BLUE -> ThemeAccentPalette(
            primary = Color(0xFF8BE9FD),
            onPrimary = Color(0xFF10232A),
            primaryContainer = Color(0xFF2D5A67),
            onPrimaryContainer = Color(0xFFD9FBFF),
        )
        PrefsManager.ThemeColor.GREEN -> ThemeAccentPalette(
            primary = Color(0xFF50FA7B),
            onPrimary = Color(0xFF0D2B17),
            primaryContainer = Color(0xFF2A6138),
            onPrimaryContainer = Color(0xFFD8FFE0),
        )
        PrefsManager.ThemeColor.PURPLE -> ThemeAccentPalette(
            primary = Color(0xFFBD93F9),
            onPrimary = Color(0xFF282A36),
            primaryContainer = Color(0xFF44475A),
            onPrimaryContainer = Color(0xFFF8F8F2),
        )
        PrefsManager.ThemeColor.PINK -> ThemeAccentPalette(
            primary = Color(0xFFFF79C6),
            onPrimary = Color(0xFF2A0D22),
            primaryContainer = Color(0xFF5A3555),
            onPrimaryContainer = Color(0xFFFFD6EF),
        )
        PrefsManager.ThemeColor.AMBER -> ThemeAccentPalette(
            primary = Color(0xFFFFB86C),
            onPrimary = Color(0xFF2E1800),
            primaryContainer = Color(0xFF66421F),
            onPrimaryContainer = Color(0xFFFFE1C2),
        )
        PrefsManager.ThemeColor.RED -> ThemeAccentPalette(
            primary = Color(0xFFFF5555),
            onPrimary = Color(0xFF2A0E12),
            primaryContainer = Color(0xFF6E2A33),
            onPrimaryContainer = Color(0xFFFFD8DD),
        )
        PrefsManager.ThemeColor.CUSTOM -> customAccentPalette(customAccentColor)
    }

private fun androidx.compose.material3.ColorScheme.withDraculaThemeColor(
    themeColor: PrefsManager.ThemeColor,
    customAccentColor: Color,
): androidx.compose.material3.ColorScheme {
    val accent = draculaThemeAccentPalette(themeColor, customAccentColor)
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

private fun androidx.compose.material3.ColorScheme.withGitHubThemeColor(
    themeColor: PrefsManager.ThemeColor,
    customAccentColor: Color,
): androidx.compose.material3.ColorScheme {
    val accent = when (themeColor) {
        PrefsManager.ThemeColor.BLUE -> ThemeAccentPalette(
            primary = Color(0xFF58A6FF),
            onPrimary = Color(0xFF0D1117),
            primaryContainer = Color(0xFF1F6FEB),
            onPrimaryContainer = Color(0xFFF0F6FC),
        )
        PrefsManager.ThemeColor.GREEN -> ThemeAccentPalette(
            primary = Color(0xFF7EE787),
            onPrimary = Color(0xFF0D1117),
            primaryContainer = Color(0xFF238636),
            onPrimaryContainer = Color(0xFFF0F6FC),
        )
        PrefsManager.ThemeColor.PURPLE -> ThemeAccentPalette(
            primary = Color(0xFFD2A8FF),
            onPrimary = Color(0xFF0D1117),
            primaryContainer = Color(0xFF8957E5),
            onPrimaryContainer = Color(0xFFF0F6FC),
        )
        PrefsManager.ThemeColor.PINK -> ThemeAccentPalette(
            primary = Color(0xFFFFA6D1),
            onPrimary = Color(0xFF0D1117),
            primaryContainer = Color(0xFFDB61A2),
            onPrimaryContainer = Color(0xFFF0F6FC),
        )
        PrefsManager.ThemeColor.AMBER -> ThemeAccentPalette(
            primary = Color(0xFFE3B341),
            onPrimary = Color(0xFF0D1117),
            primaryContainer = Color(0xFF9E6A03),
            onPrimaryContainer = Color(0xFFFFF8C5),
        )
        PrefsManager.ThemeColor.RED -> ThemeAccentPalette(
            primary = Color(0xFFFF7B72),
            onPrimary = Color(0xFF0D1117),
            primaryContainer = Color(0xFFDA3633),
            onPrimaryContainer = Color(0xFFF0F6FC),
        )
        PrefsManager.ThemeColor.CUSTOM -> customAccentPalette(customAccentColor)
    }
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

private fun modernThemeAccentPalette(
    themeColor: PrefsManager.ThemeColor,
    darkTheme: Boolean,
    customAccentColor: Color,
): ThemeAccentPalette = when (themeColor) {
    PrefsManager.ThemeColor.BLUE -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFA8C7CA) else Color(0xFF6F8F94),
        onPrimary = if (darkTheme) Color(0xFF143236) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF2F4F54) else Color(0xFFD9E9EA),
        onPrimaryContainer = if (darkTheme) Color(0xFFD9E9EA) else Color(0xFF1F3D42),
    )
    PrefsManager.ThemeColor.GREEN -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFBCD5B8) else Color(0xFF728F68),
        onPrimary = if (darkTheme) Color(0xFF23351F) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF40573B) else Color(0xFFDDEBD7),
        onPrimaryContainer = if (darkTheme) Color(0xFFDDEBD7) else Color(0xFF2D4127),
    )
    PrefsManager.ThemeColor.PURPLE -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFD3C4EA) else Color(0xFF8C7AA8),
        onPrimary = if (darkTheme) Color(0xFF322545) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF55466D) else Color(0xFFE8DFF4),
        onPrimaryContainer = if (darkTheme) Color(0xFFE8DFF4) else Color(0xFF3E3154),
    )
    PrefsManager.ThemeColor.PINK -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFE9B9C8) else Color(0xFFA96F83),
        onPrimary = if (darkTheme) Color(0xFF472432) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF6B3E4E) else Color(0xFFF4DCE4),
        onPrimaryContainer = if (darkTheme) Color(0xFFF4DCE4) else Color(0xFF57303E),
    )
    PrefsManager.ThemeColor.AMBER -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFE7CF8A) else Color(0xFF9D8440),
        onPrimary = if (darkTheme) Color(0xFF3E3210) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF665523) else Color(0xFFEEE3BE),
        onPrimaryContainer = if (darkTheme) Color(0xFFEEE3BE) else Color(0xFF4A3D16),
    )
    PrefsManager.ThemeColor.RED -> ThemeAccentPalette(
        primary = if (darkTheme) Color(0xFFE5B0AA) else Color(0xFFA96A63),
        onPrimary = if (darkTheme) Color(0xFF47221F) else Color.White,
        primaryContainer = if (darkTheme) Color(0xFF6D3B36) else Color(0xFFF1D9D5),
        onPrimaryContainer = if (darkTheme) Color(0xFFF1D9D5) else Color(0xFF56302B),
    )
    PrefsManager.ThemeColor.CUSTOM -> customAccentPalette(customAccentColor)
}

private data class ModernThemeBackgroundPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

private fun customModernBackgroundPalette(color: Color): ModernThemeBackgroundPalette {
    val onSurface = contentColorFor(color)
    return ModernThemeBackgroundPalette(
        background = color,
        surface = color,
        surfaceVariant = color.copy(alpha = 0.72f),
        onSurface = onSurface,
        onSurfaceVariant = onSurface.copy(alpha = 0.78f),
        outline = onSurface.copy(alpha = 0.36f),
        outlineVariant = onSurface.copy(alpha = 0.16f),
    )
}

private fun modernThemeBackgroundPalette(
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
    customBackgroundColor: Color,
): ModernThemeBackgroundPalette = when (backgroundColor) {
    PrefsManager.ThemeBackgroundColor.WHITE -> ModernThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF111817) else Color(0xFFF7F8F5),
        surface = if (darkTheme) Color(0xFF18211F) else Color(0xFFFFFFFC),
        surfaceVariant = if (darkTheme) Color(0xFF26322F) else Color(0xFFEDF1EC),
        onSurface = if (darkTheme) Color(0xFFE8EFEB) else Color(0xFF202A27),
        onSurfaceVariant = if (darkTheme) Color(0xFFC7D1CC) else Color(0xFF5E6B64),
        outline = if (darkTheme) Color(0xFF74827B) else Color(0xFFC8D3CC),
        outlineVariant = if (darkTheme) Color(0xFF33413C) else Color(0xFFE0E7E2),
    )
    PrefsManager.ThemeBackgroundColor.BLUE -> ModernThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF10191B) else Color(0xFFF3F8FA),
        surface = if (darkTheme) Color(0xFF172224) else Color(0xFFFDFFFF),
        surfaceVariant = if (darkTheme) Color(0xFF253438) else Color(0xFFE5F0F3),
        onSurface = if (darkTheme) Color(0xFFE7EFF1) else Color(0xFF1E2A2D),
        onSurfaceVariant = if (darkTheme) Color(0xFFC4D1D5) else Color(0xFF596B70),
        outline = if (darkTheme) Color(0xFF71858B) else Color(0xFFC4D6DB),
        outlineVariant = if (darkTheme) Color(0xFF314348) else Color(0xFFDDE9EC),
    )
    PrefsManager.ThemeBackgroundColor.GREEN -> ModernThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF101A15) else Color(0xFFF4FAF3),
        surface = if (darkTheme) Color(0xFF172219) else Color(0xFFFDFFFC),
        surfaceVariant = if (darkTheme) Color(0xFF263527) else Color(0xFFE7F1E4),
        onSurface = if (darkTheme) Color(0xFFE8F0E7) else Color(0xFF202B20),
        onSurfaceVariant = if (darkTheme) Color(0xFFC6D2C4) else Color(0xFF5B6C58),
        outline = if (darkTheme) Color(0xFF768573) else Color(0xFFC8D8C4),
        outlineVariant = if (darkTheme) Color(0xFF334532) else Color(0xFFDDEADC),
    )
    PrefsManager.ThemeBackgroundColor.PURPLE -> ModernThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF17141C) else Color(0xFFF8F5FB),
        surface = if (darkTheme) Color(0xFF201B27) else Color(0xFFFFFCFF),
        surfaceVariant = if (darkTheme) Color(0xFF312B3B) else Color(0xFFEEE8F4),
        onSurface = if (darkTheme) Color(0xFFEDE7F1) else Color(0xFF29242F),
        onSurfaceVariant = if (darkTheme) Color(0xFFD2C8DA) else Color(0xFF685F70),
        outline = if (darkTheme) Color(0xFF84778E) else Color(0xFFD5C8DF),
        outlineVariant = if (darkTheme) Color(0xFF40364A) else Color(0xFFE7DFEC),
    )
    PrefsManager.ThemeBackgroundColor.PINK -> ModernThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF1B1417) else Color(0xFFFBF5F7),
        surface = if (darkTheme) Color(0xFF241B1F) else Color(0xFFFFFCFD),
        surfaceVariant = if (darkTheme) Color(0xFF382A30) else Color(0xFFF4E7EB),
        onSurface = if (darkTheme) Color(0xFFF0E7EA) else Color(0xFF302427),
        onSurfaceVariant = if (darkTheme) Color(0xFFD8C6CC) else Color(0xFF705D63),
        outline = if (darkTheme) Color(0xFF8E767F) else Color(0xFFDFC6CF),
        outlineVariant = if (darkTheme) Color(0xFF493640) else Color(0xFFECDDE3),
    )
    PrefsManager.ThemeBackgroundColor.AMBER -> ModernThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF1A170F) else Color(0xFFFAF7EF),
        surface = if (darkTheme) Color(0xFF242014) else Color(0xFFFFFDF7),
        surfaceVariant = if (darkTheme) Color(0xFF362F1E) else Color(0xFFF0EAD8),
        onSurface = if (darkTheme) Color(0xFFF0EBDD) else Color(0xFF2E291D),
        onSurfaceVariant = if (darkTheme) Color(0xFFD4CBB6) else Color(0xFF6D634C),
        outline = if (darkTheme) Color(0xFF8B7E62) else Color(0xFFD8CCAA),
        outlineVariant = if (darkTheme) Color(0xFF453D29) else Color(0xFFE8DFC8),
    )
    PrefsManager.ThemeBackgroundColor.GRAY -> ModernThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF141718) else Color(0xFFF6F7F7),
        surface = if (darkTheme) Color(0xFF1C2021) else Color(0xFFFFFFFF),
        surfaceVariant = if (darkTheme) Color(0xFF2D3334) else Color(0xFFECEFED),
        onSurface = if (darkTheme) Color(0xFFE9EEEE) else Color(0xFF252A2B),
        onSurfaceVariant = if (darkTheme) Color(0xFFC9D0D0) else Color(0xFF606869),
        outline = if (darkTheme) Color(0xFF788384) else Color(0xFFCCD3D1),
        outlineVariant = if (darkTheme) Color(0xFF3A4243) else Color(0xFFE1E6E4),
    )
    PrefsManager.ThemeBackgroundColor.CUSTOM -> customModernBackgroundPalette(customBackgroundColor)
}

private fun androidx.compose.material3.ColorScheme.withModernThemeColor(
    themeColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
    customAccentColor: Color,
    customBackgroundColor: Color,
): androidx.compose.material3.ColorScheme {
    val accent = modernThemeAccentPalette(themeColor, darkTheme, customAccentColor)
    val background = modernThemeBackgroundPalette(backgroundColor, darkTheme, customBackgroundColor)
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
        background = background.background,
        onBackground = background.onSurface,
        surface = background.surface,
        onSurface = background.onSurface,
        surfaceVariant = background.surfaceVariant,
        onSurfaceVariant = background.onSurfaceVariant,
        outline = background.outline,
        outlineVariant = background.outlineVariant,
        surfaceContainerLowest = background.surface,
        surfaceContainerLow = background.surface,
        surfaceContainer = background.surface,
        surfaceContainerHigh = background.surfaceVariant,
        surfaceContainerHighest = background.surfaceVariant,
        surfaceBright = background.surface,
        surfaceDim = background.background,
        surfaceTint = accent.primary,
        inverseSurface = if (darkTheme) Color(0xFFE8EFEB) else Color(0xFF24302D),
        inverseOnSurface = if (darkTheme) Color(0xFF202A27) else Color(0xFFF7F8F5),
    )
}

private fun androidx.compose.material3.ColorScheme.withCleanListThemeColor(
    themeColor: PrefsManager.ThemeColor,
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
    customAccentColor: Color,
    customBackgroundColor: Color,
): androidx.compose.material3.ColorScheme {
    val accent = themeAccentPalette(themeColor, darkTheme, customAccentColor)
    val backgroundPalette = themeBackgroundPalette(backgroundColor, darkTheme, customBackgroundColor)
    val background = if (darkTheme) Color(0xFF151718) else backgroundPalette.background
    val surface = if (darkTheme) Color(0xFF1D2021) else Color.White
    val surfaceVariant = if (darkTheme) Color(0xFF303637) else backgroundPalette.surfaceVariant
    val onBackground = if (darkTheme) Color(0xFFECEFF0) else backgroundPalette.onSurface
    val onSurface = if (darkTheme) Color(0xFFECEFF0) else Color(0xFF202226)
    val onSurfaceVariant = if (darkTheme) Color(0xFFC4C9CB) else Color(0xFF747982)
    val outline = if (darkTheme) Color(0xFF737C7E) else backgroundPalette.outline
    val outlineVariant = if (darkTheme) Color(0xFF343B3D) else backgroundPalette.outlineVariant
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
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
        surfaceBright = surface,
        surfaceDim = background,
        surfaceTint = accent.primary,
        inverseSurface = if (darkTheme) Color(0xFFECEFF0) else Color(0xFF202226),
        inverseOnSurface = if (darkTheme) Color(0xFF202226) else Color.White,
    )
}

private data class ThemeBackgroundPalette(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

private fun customBackgroundPalette(color: Color): ThemeBackgroundPalette {
    val onSurface = contentColorFor(color)
    return ThemeBackgroundPalette(
        background = color,
        surface = color,
        surfaceVariant = color.copy(alpha = 0.72f),
        onSurface = onSurface,
        onSurfaceVariant = onSurface.copy(alpha = 0.78f),
        outline = onSurface.copy(alpha = 0.36f),
        outlineVariant = onSurface.copy(alpha = 0.16f),
    )
}

private fun themeBackgroundPalette(
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
    customBackgroundColor: Color,
): ThemeBackgroundPalette = when (backgroundColor) {
    PrefsManager.ThemeBackgroundColor.WHITE -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF111827) else Color.White,
        surface = if (darkTheme) Color(0xFF1F2937) else Color.White,
        surfaceVariant = if (darkTheme) Color(0xFF374151) else Color(0xFFF1F5F9),
        onSurface = if (darkTheme) Color(0xFFE5E7EB) else OnSurfaceLight,
        onSurfaceVariant = if (darkTheme) Color(0xFFD1D5DB) else Color(0xFF64748B),
        outline = if (darkTheme) Color(0xFF6B7280) else Color(0xFFCBD5E1),
        outlineVariant = if (darkTheme) Color(0xFF374151) else Color(0xFFE2E8F0),
    )
    PrefsManager.ThemeBackgroundColor.BLUE -> ThemeBackgroundPalette(
        background = if (darkTheme) BackgroundDark else BackgroundLight,
        surface = if (darkTheme) SurfaceDark else SurfaceLight,
        surfaceVariant = if (darkTheme) SurfaceVariantDark else SurfaceVariantLight,
        onSurface = if (darkTheme) OnSurfaceDark else OnSurfaceLight,
        onSurfaceVariant = if (darkTheme) OnSurfaceVariantDark else OnSurfaceVariantLight,
        outline = if (darkTheme) OutlineDark else OutlineLight,
        outlineVariant = if (darkTheme) Color(0xFF2B3B4D) else Color(0xFFD8E6FB),
    )
    PrefsManager.ThemeBackgroundColor.GREEN -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF0F1F1A) else Color(0xFFF2FCF8),
        surface = if (darkTheme) Color(0xFF142820) else Color(0xFFFBFFFD),
        surfaceVariant = if (darkTheme) Color(0xFF27443B) else Color(0xFFE0F5EE),
        onSurface = if (darkTheme) Color(0xFFE8F0E7) else Color(0xFF202B20),
        onSurfaceVariant = if (darkTheme) Color(0xFFC9DED6) else Color(0xFF4C635B),
        outline = if (darkTheme) Color(0xFF6D9284) else Color(0xFFA7CFC2),
        outlineVariant = if (darkTheme) Color(0xFF2E4B42) else Color(0xFFD1E9E1),
    )
    PrefsManager.ThemeBackgroundColor.PURPLE -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF1B1328) else Color(0xFFFAF7FF),
        surface = if (darkTheme) Color(0xFF251A35) else Color(0xFFFFFBFF),
        surfaceVariant = if (darkTheme) Color(0xFF43305D) else Color(0xFFF0E7FF),
        onSurface = if (darkTheme) Color(0xFFEDE7F1) else Color(0xFF29242F),
        onSurfaceVariant = if (darkTheme) Color(0xFFD8C8E8) else Color(0xFF62556D),
        outline = if (darkTheme) Color(0xFF907EA4) else Color(0xFFCAB7E4),
        outlineVariant = if (darkTheme) Color(0xFF493762) else Color(0xFFE6D8FA),
    )
    PrefsManager.ThemeBackgroundColor.PINK -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF24131A) else Color(0xFFFFF7FA),
        surface = if (darkTheme) Color(0xFF2D1821) else Color(0xFFFFFBFD),
        surfaceVariant = if (darkTheme) Color(0xFF4B2B37) else Color(0xFFFFE8EF),
        onSurface = if (darkTheme) Color(0xFFF0E7EA) else Color(0xFF302427),
        onSurfaceVariant = if (darkTheme) Color(0xFFE8C8D3) else Color(0xFF71545E),
        outline = if (darkTheme) Color(0xFFA77B8B) else Color(0xFFE4B7C6),
        outlineVariant = if (darkTheme) Color(0xFF52313E) else Color(0xFFF7D6E1),
    )
    PrefsManager.ThemeBackgroundColor.AMBER -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF211A0D) else Color(0xFFFFFAEF),
        surface = if (darkTheme) Color(0xFF2A2111) else Color(0xFFFFFCF6),
        surfaceVariant = if (darkTheme) Color(0xFF46371B) else Color(0xFFFFEBC5),
        onSurface = if (darkTheme) Color(0xFFF0EBDD) else Color(0xFF2E291D),
        onSurfaceVariant = if (darkTheme) Color(0xFFE2D0A8) else Color(0xFF6D5B33),
        outline = if (darkTheme) Color(0xFFA48A50) else Color(0xFFE1C17A),
        outlineVariant = if (darkTheme) Color(0xFF4E3E20) else Color(0xFFF3DAA6),
    )
    PrefsManager.ThemeBackgroundColor.GRAY -> ThemeBackgroundPalette(
        background = if (darkTheme) Color(0xFF15181D) else Color(0xFFF8FAFC),
        surface = if (darkTheme) Color(0xFF1E232B) else Color.White,
        surfaceVariant = if (darkTheme) Color(0xFF343B47) else Color(0xFFE2E8F0),
        onSurface = if (darkTheme) Color(0xFFE5E7EB) else Color(0xFF252A2B),
        onSurfaceVariant = if (darkTheme) Color(0xFFD1D5DB) else Color(0xFF64748B),
        outline = if (darkTheme) Color(0xFF6B7280) else Color(0xFFCBD5E1),
        outlineVariant = if (darkTheme) Color(0xFF374151) else Color(0xFFE2E8F0),
    )
    PrefsManager.ThemeBackgroundColor.CUSTOM -> customBackgroundPalette(customBackgroundColor)
}

private fun androidx.compose.material3.ColorScheme.withBackgroundColor(
    backgroundColor: PrefsManager.ThemeBackgroundColor,
    darkTheme: Boolean,
    customBackgroundColor: Color,
): androidx.compose.material3.ColorScheme {
    val background = themeBackgroundPalette(backgroundColor, darkTheme, customBackgroundColor)
    return copy(
        background = background.background,
        onBackground = background.onSurface,
        surface = background.surface,
        onSurface = background.onSurface,
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
