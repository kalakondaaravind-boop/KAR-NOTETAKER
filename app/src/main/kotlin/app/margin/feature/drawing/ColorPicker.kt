package app.margin.feature.drawing

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.margin.R
import app.margin.domain.text.Palette

/** Recent colours and the pen preset slots, kept on this tablet only. */
class ColorPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kar_colors", Context.MODE_PRIVATE)

    fun recent(): List<Long> = parse(prefs.getString("recent", null)).take(MAX_RECENT)

    fun addRecent(c: Long) {
        val next = (listOf(c) + recent().filterNot { it == c }).take(MAX_RECENT)
        prefs.edit().putString("recent", next.joinToString(",") { Palette.toArgbHex(it) }).apply()
    }

    fun slots(): List<Long> = parse(prefs.getString("slots", null)).let { s -> if (s.size == DEFAULT_SLOTS.size) s else DEFAULT_SLOTS }

    fun setSlot(i: Int, c: Long) {
        val s = slots().toMutableList(); if (i !in s.indices) return
        s[i] = c
        prefs.edit().putString("slots", s.joinToString(",") { Palette.toArgbHex(it) }).apply()
    }

    private fun parse(v: String?): List<Long> = v?.split(',')?.mapNotNull { Palette.parseHex(it) } ?: emptyList()

    companion object {
        const val MAX_RECENT = 12
        /** Black, blue, red, green, orange, purple: a typical pen case. */
        val DEFAULT_SLOTS = listOf(0xFF1F2937, 0xFF1E88E5, 0xFFE53935, 0xFF43A047, 0xFFFB8C00, 0xFF8E24AA)
    }
}

/** A round colour swatch; checkerboard shows through transparent colours. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Swatch(color: Long, selected: Boolean, size: Dp = 32.dp, label: String? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = Color(color)
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFE0E0E0), Color.White)))
            .background(c)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = label ?: Palette.toHex(color); this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, Modifier.size(size * 0.5f), tint = if (luma(color) > 0.6f) Color.Black else Color.White)
    }
}

fun luma(c: Long): Float {
    val r = (c shr 16 and 0xFF) / 255f; val g = (c shr 8 and 0xFF) / 255f; val b = (c and 0xFF) / 255f
    return 0.299f * r + 0.587f * g + 0.114f * b
}

private fun toHsv(c: Long): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV((c and 0xFFFFFFFFL).toInt(), it) }

/**
 * Full colour picker: the palette, highlighter colours, recent colours and a custom colour
 * (hue / saturation / brightness / opacity sliders or a hex code).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initial: Long,
    title: String,
    allowAlpha: Boolean = true,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { ColorPrefs(context) }
    val hsv0 = remember { toHsv(initial) }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    var alpha by remember { mutableFloatStateOf(((initial shr 24) and 0xFF) / 255f) }
    var hex by remember { mutableStateOf(Palette.toArgbHex(initial)) }
    fun current(): Long = (Palette.fromHsv(hue, sat, value) and 0x00FFFFFF) or ((alpha * 255f).toLong().coerceIn(0, 255) shl 24)
    fun set(c: Long) {
        val h = toHsv(c); hue = h[0]; sat = h[1]; value = h[2]
        alpha = if (allowAlpha) ((c shr 24) and 0xFF) / 255f else 1f
        hex = Palette.toArgbHex(current())
    }
    val chosen = current()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 72.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(Color(initial)))
                    Text("→", Modifier.padding(horizontal = 8.dp))
                    Box(Modifier.size(width = 72.dp, height = 40.dp).clip(RoundedCornerShape(10.dp)).background(Color(chosen))
                        .border(1.dp, Color(0x33000000), RoundedCornerShape(10.dp)))
                }
                val recent = remember { prefs.recent() }
                if (recent.isNotEmpty()) {
                    Text(stringResource(R.string.color_recent), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { c -> Swatch(c, c == chosen) { set(c) } }
                    }
                }
                Text(stringResource(R.string.color_palette), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.ink.forEach { c -> Swatch(c, c == chosen) { set(c) } }
                }
                Text(stringResource(R.string.color_highlighters), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.highlight.forEach { c -> Swatch(c, c == chosen) { set(c) } }
                }
                Text(stringResource(R.string.color_custom), style = MaterialTheme.typography.labelLarge)
                GradientSlider(stringResource(R.string.color_hue), hue / 360f, (0..6).map { Color(Palette.fromHsv(it * 60f, 1f, 1f)) }) { hue = it * 360f; hex = Palette.toArgbHex(current()) }
                GradientSlider(stringResource(R.string.color_saturation), sat, listOf(Color(Palette.fromHsv(hue, 0f, value)), Color(Palette.fromHsv(hue, 1f, value)))) { sat = it; hex = Palette.toArgbHex(current()) }
                GradientSlider(stringResource(R.string.color_brightness), value, listOf(Color.Black, Color(Palette.fromHsv(hue, sat, 1f)))) { value = it; hex = Palette.toArgbHex(current()) }
                if (allowAlpha) GradientSlider(stringResource(R.string.color_opacity), alpha, listOf(Color(chosen).copy(alpha = 0f), Color(chosen).copy(alpha = 1f))) { alpha = it; hex = Palette.toArgbHex(current()) }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v -> hex = v.take(9); Palette.parseHex(v)?.let { c -> val h = toHsv(c); hue = h[0]; sat = h[1]; value = h[2]; if (allowAlpha) alpha = ((c shr 24) and 0xFF) / 255f } },
                    label = { Text(stringResource(R.string.color_hex)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { prefs.addRecent(chosen); onPick(chosen) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun GradientSlider(label: String, value: Float, colors: List<Color>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 10.dp).clip(RoundedCornerShape(5.dp))) {
                // checkerboard behind translucent gradients
                val sq = size.height
                var x = 0f; var i = 0
                while (x < size.width) { if (i % 2 == 0) drawRect(Color(0xFFDDDDDD), Offset(x, 0f), androidx.compose.ui.geometry.Size(sq, sq)); x += sq; i++ }
                drawRect(Brush.horizontalGradient(colors))
            }
            Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A row of quick pen colours (preset slots). Tap to use, long-press to change a slot. */
@Composable
fun PenSlots(slots: List<Long>, current: Long, onPick: (Long) -> Unit, onEdit: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        slots.forEachIndexed { i, c ->
            Swatch(c, (c or 0xFF000000) == (current or 0xFF000000), 34.dp, stringResource(R.string.color_slot, i + 1), onLongClick = { onEdit(i) }) { onPick(c) }
        }
        Spacer(Modifier.width(2.dp))
    }
}
