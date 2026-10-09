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
 * Design tokens. A calm, paper-like palette: warm neutral surfaces, a muted green-grey primary,
 * and an ochre accent used sparingly (pins, highlights). Contrast of on-colours ≥ 4.5:1.
 */
private val Ink = Color(0xFF1F2A26)
private val Paper = Color(0xFFFBF8F3)
private val Moss = Color(0xFF3F5F55)
private val Ochre = Color(0xFF8A5A12)

private val LightColors = lightColorScheme(
    primary = Moss, onPrimary = Color.White,
    primaryContainer = Color(0xFFC6E3D8), onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4F6259), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E6DF), onSecondaryContainer = Color(0xFF0C1F18),
    tertiary = Ochre, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB3), onTertiaryContainer = Color(0xFF2B1700),
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFE6E2DA), onSurfaceVariant = Color(0xFF4A4740),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F2EC),
    surfaceContainer = Color(0xFFF1EDE6), surfaceContainerHigh = Color(0xFFEBE7E0), surfaceContainerHighest = Color(0xFFE5E1DA),
    outline = Color(0xFF7B776F), outlineVariant = Color(0xFFCBC6BC),
    error = Color(0xFFB3261E), onError = Color.White, errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9CCC0), onPrimary = Color(0xFF12352C),
    primaryContainer = Color(0xFF284B41), onPrimaryContainer = Color(0xFFC6E3D8),
    secondary = Color(0xFFB6CBC1), onSecondary = Color(0xFF21342D),
    secondaryContainer = Color(0xFF384B43), onSecondaryContainer = Color(0xFFD9E6DF),
    tertiary = Color(0xFFF2BD72), onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF653F00), onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF141714), onBackground = Color(0xFFE4E9E5),
    surface = Color(0xFF141714), onSurface = Color(0xFFE4E9E5),
    surfaceVariant = Color(0xFF3F4943), onSurfaceVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFF0F120F), surfaceContainerLow = Color(0xFF1C201D),
    surfaceContainer = Color(0xFF202421), surfaceContainerHigh = Color(0xFF2A2E2B), surfaceContainerHighest = Color(0xFF353936),
    outline = Color(0xFF89938C), outlineVariant = Color(0xFF3F4943),
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
    SemanticColors(Color(0xFFFFE8A3), Ink, Color(0xFF2E6B3F), Color(0xFF8A5A12), Color(0xFF5B4A9E), Color(0xFFEFEAE2), Moss)
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
fun MarginTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val semantic = if (dark) {
        SemanticColors(Color(0xFF5C4A12), Color(0xFFFFF1C9), Color(0xFF8FD3A0), Color(0xFFF2BD72), Color(0xFFC8B8FF), Color(0xFF262B28), colors.primary)
    } else {
        SemanticColors(Color(0xFFFFE8A3), Ink, Color(0xFF2E6B3F), Ochre, Color(0xFF5B4A9E), Color(0xFFEFEAE2), colors.primary)
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSemanticColors provides semantic, LocalSpacing provides Spacing()) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
