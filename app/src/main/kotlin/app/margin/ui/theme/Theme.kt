package app.margin.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.margin.domain.settings.ThemeMode

/*
 * Design tokens — "Sky & Pastel Blue". A clear sky-blue primary on very pale blue surfaces, with
 * pastel-blue containers and a soft periwinkle accent used sparingly. Every text/background pair
 * meets WCAG AA (≥ 4.5:1); ratios are recorded in docs/DESIGN.md.
 */
private val Ink = Color(0xFF13212E)
private val SkyMist = Color(0xFFF4F9FE)
val SkyBlue = Color(0xFF2A76AD)
val PastelBlue = Color(0xFFCDE7FA)
private val Periwinkle = Color(0xFF5C6BB0)

private val LightColors = lightColorScheme(
    primary = SkyBlue, onPrimary = Color.White,
    primaryContainer = PastelBlue, onPrimaryContainer = Color(0xFF0A2A42),
    secondary = Color(0xFF4E6E8C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEAF6), onSecondaryContainer = Color(0xFF13293C),
    tertiary = Periwinkle, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDE1FA), onTertiaryContainer = Color(0xFF18204A),
    background = SkyMist, onBackground = Ink,
    surface = SkyMist, onSurface = Ink,
    surfaceVariant = Color(0xFFDEE8F2), onSurfaceVariant = Color(0xFF3F4D5A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEEF5FC),
    surfaceContainer = Color(0xFFE8F1FA), surfaceContainerHigh = Color(0xFFE1ECF7), surfaceContainerHighest = Color(0xFFDAE7F4),
    outline = Color(0xFF6F7E8C), outlineVariant = Color(0xFFC2D0DD),
    error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD0F7), onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF1D4E72), onPrimaryContainer = PastelBlue,
    secondary = Color(0xFFB5CCE3), onSecondary = Color(0xFF1F3448),
    secondaryContainer = Color(0xFF36506A), onSecondaryContainer = Color(0xFFDCEAF6),
    tertiary = Color(0xFFBFC6F5), onTertiary = Color(0xFF283272),
    tertiaryContainer = Color(0xFF3F4A8A), onTertiaryContainer = Color(0xFFDDE1FA),
    background = Color(0xFF0E1822), onBackground = Color(0xFFDCE6F0),
    surface = Color(0xFF0E1822), onSurface = Color(0xFFDCE6F0),
    surfaceVariant = Color(0xFF3C4855), onSurfaceVariant = Color(0xFFBCC8D4),
    surfaceContainerLowest = Color(0xFF09121A), surfaceContainerLow = Color(0xFF15212C),
    surfaceContainer = Color(0xFF192531), surfaceContainerHigh = Color(0xFF23303C), surfaceContainerHighest = Color(0xFF2E3B47),
    outline = Color(0xFF86939F), outlineVariant = Color(0xFF3C4855),
)

/** Spacing scale (4dp grid). Use these instead of ad-hoc values. */
@Immutable
data class Spacing(
    val xxs: androidx.compose.ui.unit.Dp = 2.dp,
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val s: androidx.compose.ui.unit.Dp = 8.dp,
    val m: androidx.compose.ui.unit.Dp = 12.dp,
    val l: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxl: androidx.compose.ui.unit.Dp = 32.dp,
    /** Max readable line length for long-form text on large screens. */
    val readingWidth: androidx.compose.ui.unit.Dp = 720.dp,
)

/** Colours for semantic states that Material does not name. */
@Immutable
data class SemanticColors(
    val highlight: Color,
    val onHighlight: Color,
    val success: Color,
    val warning: Color,
    val aiGenerated: Color,
    val code: Color,
    val link: Color,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(Color(0xFFBFE0FF), Ink, Color(0xFF2E6B3F), Color(0xFF8A5A12), Periwinkle, Color(0xFFE6F0F9), SkyBlue)
}

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

/** Editor body text style; scale and monospace come from user settings. */
fun editorTextStyle(base: TextStyle, scale: Float, monospace: Boolean): TextStyle = base.copy(
    fontSize = if (base.fontSize.isSpecified) base.fontSize * scale else base.fontSize,
    lineHeight = if (base.lineHeight.isSpecified) base.lineHeight * scale else base.lineHeight,
    fontFamily = if (monospace) FontFamily.Monospace else base.fontFamily,
)

@Composable
fun MarginTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val semantic = if (dark) {
        SemanticColors(Color(0xFF1F4A6E), Color(0xFFE3F1FF), Color(0xFF8FD3A0), Color(0xFFF2BD72), Color(0xFFBFC6F5), Color(0xFF1A2733), colors.primary)
    } else {
        SemanticColors(Color(0xFFBFE0FF), Ink, Color(0xFF2E6B3F), Color(0xFF8A5A12), Periwinkle, Color(0xFFE6F0F9), colors.primary)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSemanticColors provides semantic, LocalSpacing provides Spacing()) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
