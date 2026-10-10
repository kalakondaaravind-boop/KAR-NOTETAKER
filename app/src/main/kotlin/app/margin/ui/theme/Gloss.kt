package app.margin.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * "3D glossy" layer of the design system.
 *
 * A glossy surface = soft coloured drop shadow (depth) + top-to-bottom gradient (curvature)
 * + a specular sheen over the upper ~45% + a light-to-dark hairline edge (bevel).
 * The sheen is drawn *under* the content, so text and icons stay crisp.
 *
 * Every gradient stop below was checked for WCAG AA: text sitting anywhere from 28% to 72% of the
 * surface height keeps ≥ 4.5:1 (white on primary ≥ 4.9:1, ink on pastel ≥ 10.9:1, ink on cards ≥ 7.7:1;
 * see docs/DESIGN.md). When the user turns the style off (Settings → Appearance) every helper here
 * falls back to the flat Material 3 look.
 */

@Immutable
data class Gloss(val enabled: Boolean = true, val dark: Boolean = false)

val LocalGloss = staticCompositionLocalOf { Gloss() }

enum class GlossTone { PRIMARY, PASTEL, CARD }

@Immutable
data class GlossStyle(
    val top: Color,
    val bottom: Color,
    val sheenAlpha: Float,
    val edgeLight: Color,
    val edgeDark: Color,
    val shadow: Color,
    val content: Color,
)

private object GlossPalette {
    val primaryLight = GlossStyle(Color(0xFF2C74A9), Color(0xFF195685), 0.18f, Color(0x99FFFFFF), Color(0x66103B5C), Color(0xFF1B4F7A), Color.White)
    val pastelLight = GlossStyle(Color(0xFFDDF0FD), Color(0xFFB9DCF5), 0.45f, Color(0xCCFFFFFF), Color(0x552A76AD), Color(0xFF2A76AD), Color(0xFF0A2A42))
    val cardLight = GlossStyle(Color(0xFFFFFFFF), Color(0xFFEAF3FB), 0.60f, Color(0xFFFFFFFF), Color(0x40719CC0), Color(0xFF2A76AD), Color(0xFF13212E))
    val primaryDark = GlossStyle(Color(0xFFAEDAFA), Color(0xFF85C2EF), 0.18f, Color(0x80FFFFFF), Color(0x66002A40), Color(0xFF000000), Color(0xFF00344F))
    val pastelDark = GlossStyle(Color(0xFF285D84), Color(0xFF174264), 0.12f, Color(0x4DCDE7FA), Color(0x66000000), Color(0xFF000000), Color(0xFFCDE7FA))
    val cardDark = GlossStyle(Color(0xFF22303D), Color(0xFF17232E), 0.06f, Color(0x33CDE7FA), Color(0x66000000), Color(0xFF000000), Color(0xFFDCE6F0))
}

/** The glossy style for [tone], or null when the user prefers the flat style. */
@Composable
@ReadOnlyComposable
fun glossStyle(tone: GlossTone): GlossStyle? {
    val g = LocalGloss.current
    if (!g.enabled) return null
    return when (tone) {
        GlossTone.PRIMARY -> if (g.dark) GlossPalette.primaryDark else GlossPalette.primaryLight
        GlossTone.PASTEL -> if (g.dark) GlossPalette.pastelDark else GlossPalette.pastelLight
        GlossTone.CARD -> if (g.dark) GlossPalette.cardDark else GlossPalette.cardLight
    }
}

/**
 * Paints a glossy surface behind the content. With [style] == null it paints [flat] (if given) and clips,
 * so call sites need no branching.
 */
fun Modifier.glossy(style: GlossStyle?, shape: Shape, elevation: Dp = 6.dp, flat: Color = Color.Unspecified): Modifier {
    if (style == null) return (if (flat.isSpecified) this.background(flat, shape) else this).clip(shape)
    return this
        .shadow(elevation, shape, clip = false, ambientColor = style.shadow, spotColor = style.shadow)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(style.top, style.bottom)))
        .drawWithContent {
            val h = size.height * 0.45f
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = style.sheenAlpha), Color.Transparent), startY = 0f, endY = h),
                size = Size(size.width, h),
            )
            drawContent()
        }
        .border(1.dp, Brush.verticalGradient(listOf(style.edgeLight, style.edgeDark)), shape)
}

/** Primary action button: glossy sky-blue pill (flat Material button when the style is off). */
@Composable
fun GlossyButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val style = glossStyle(GlossTone.PRIMARY)
    if (style == null) {
        Button(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }
    Button(
        onClick = onClick,
        modifier = modifier.glossy(if (enabled) style else null, CircleShape, elevation = 4.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = style.content),
        elevation = null,
        content = content,
    )
}

/** Extended FAB in glossy pastel blue. */
@Composable
fun GlossyExtendedFab(onClick: () -> Unit, icon: @Composable () -> Unit, text: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val style = glossStyle(GlossTone.PASTEL)
    if (style == null) {
        ExtendedFloatingActionButton(onClick = onClick, icon = icon, text = text, modifier = modifier)
        return
    }
    val shape = MaterialTheme.shapes.large
    ExtendedFloatingActionButton(
        onClick = onClick, icon = icon, text = text,
        modifier = modifier.glossy(style, shape, elevation = 10.dp),
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = style.content,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
    )
}

/** A raised glossy panel that groups related rows (home sections). Flat: a tonal container. */
@Composable
fun GlossyPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.glossy(glossStyle(GlossTone.CARD), MaterialTheme.shapes.large, elevation = 3.dp, flat = MaterialTheme.colorScheme.surfaceContainerLow),
        content = content,
    )
}

/** Vertical gradient for app bars: surface fading into a hint of pastel sky (flat: plain surface). */
@Composable
fun glossyBarBrush(): Brush {
    val c = MaterialTheme.colorScheme
    return if (LocalGloss.current.enabled) Brush.verticalGradient(listOf(c.surface, c.primaryContainer.copy(alpha = 0.45f)))
    else Brush.verticalGradient(listOf(c.surface, c.surface))
}
