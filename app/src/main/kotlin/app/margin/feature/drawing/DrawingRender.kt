package app.margin.feature.drawing

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.margin.domain.drawing.Clipart
import app.margin.domain.drawing.Drawing
import app.margin.domain.drawing.DrawingSvg
import app.margin.domain.drawing.Element
import app.margin.domain.drawing.Geometry
import app.margin.domain.drawing.InkTool
import app.margin.domain.drawing.PageGeometry
import app.margin.domain.drawing.Pt
import app.margin.domain.drawing.Rect
import app.margin.domain.drawing.Shape
import app.margin.domain.drawing.ShapeKind
import app.margin.domain.drawing.Stroke
import app.margin.domain.drawing.TextBox
import app.margin.domain.model.PageStyle
import app.margin.ui.theme.NoteFonts
import java.io.ByteArrayOutputStream

/** Paper line colours (soft blue lines, red margin), the same in the editor, on the canvas and in exports. */
val PaperLine = Color(0xFFB3CDE3)
val PaperAccent = Color(0xFFE57373)
val PaperDot = Color(0xFF9DB6CC)

/** Draws paper lines for [style] into the current area. [scale] converts dp spacing to pixels. */
fun DrawScope.drawPaper(style: PageStyle, width: Float, height: Float, scale: Float = 1f, lineWidth: Float = 1f) {
    for (m in PageGeometry.marks(style, width, height, scale)) when (m) {
        is PageGeometry.Line -> drawLine(if (m.accent) PaperAccent else PaperLine, Offset(m.x1, m.y1), Offset(m.x2, m.y2), lineWidth)
        is PageGeometry.Dot -> drawCircle(PaperDot, 1.6f * lineWidth, Offset(m.x, m.y))
        is PageGeometry.Box -> drawRect(PaperDot, Offset(m.rect.left, m.rect.top), Size(m.rect.width, m.rect.height), style = DrawStroke(1.5f * lineWidth))
        is PageGeometry.CheckBox -> drawRoundRect(Color(0xFF7FA3C4), Offset(m.rect.left, m.rect.top), Size(m.rect.width, m.rect.height), CornerRadius(3f), style = DrawStroke(1.5f * lineWidth))
    }
}

private fun argb(c: Long, alphaMul: Float = 1f): Color = Color(c).let { it.copy(alpha = it.alpha * alphaMul) }

fun strokePath(points: List<Pt>): Path {
    val path = Path()
    val (s, quads) = Geometry.smoothQuads(points)
    path.moveTo(s.x, s.y)
    @Suppress("DEPRECATION")
    quads.forEach { q -> path.quadraticBezierTo(q[0], q[1], q[2], q[3]) }
    return path
}

/** Draws a drawing in page units (the caller applies zoom/pan). */
fun DrawScope.drawDrawingContent(
    d: Drawing,
    textMeasurer: TextMeasurer,
    extra: List<Element> = emptyList(),
    hidden: Set<String> = emptySet(),
) {
    drawRect(Color(d.background), Offset.Zero, Size(d.width, d.height))
    drawPaper(d.page, d.width, d.height)
    val all = d.elements.filter { it.id !in hidden } + extra
    // Highlighter ink sits beneath everything else, like a real highlighter under pen.
    all.filter { it is Stroke && it.tool == InkTool.HIGHLIGHTER }.forEach { drawElement(it, textMeasurer) }
    all.filterNot { it is Stroke && it.tool == InkTool.HIGHLIGHTER }.forEach { drawElement(it, textMeasurer) }
}

fun DrawScope.drawElement(e: Element, tm: TextMeasurer) {
    when (e) {
        is Stroke -> {
            val color = argb(e.color, e.tool.alpha)
            if (e.points.size == 1) drawCircle(color, e.width / 2f, Offset(e.points[0].x, e.points[0].y))
            else drawPath(strokePath(e.points), color, style = DrawStroke(
                width = e.width,
                cap = if (e.tool == InkTool.HIGHLIGHTER || e.tool == InkTool.MARKER) StrokeCap.Square else StrokeCap.Round,
                join = StrokeJoin.Round,
            ))
        }
        is Shape -> drawShape(e)
        is TextBox -> {
            val style = TextStyle(
                color = argb(e.color),
                fontSize = e.size.toSp(),
                lineHeight = (e.size * 1.25f).toSp(),
                fontFamily = NoteFonts.family(e.font),
                fontWeight = if (e.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (e.italic) FontStyle.Italic else FontStyle.Normal,
            )
            drawText(tm, e.text.ifEmpty { " " }, Offset(e.x, e.y), style)
        }
        is Clipart -> {
            val p = ClipartLibrary.path(e.key)
            if (p == null) drawRect(argb(e.color), Offset(e.x, e.y), Size(e.size, e.size), style = DrawStroke(2f))
            else translate(e.x, e.y) { scale(e.size / 24f, e.size / 24f, pivot = Offset.Zero) { drawPath(p, argb(e.color)) } }
        }
    }
}

private fun DrawScope.drawShape(e: Shape) {
    val color = argb(e.color)
    val stroke = DrawStroke(e.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val l = minOf(e.x1, e.x2); val t = minOf(e.y1, e.y2); val w = kotlin.math.abs(e.x2 - e.x1); val h = kotlin.math.abs(e.y2 - e.y1)
    val fill = e.fill?.let { argb(it) }
    fun both(draw: (Color, androidx.compose.ui.graphics.drawscope.DrawStyle) -> Unit) { fill?.let { draw(it, Fill) }; draw(color, stroke) }
    when (e.kind) {
        ShapeKind.LINE -> drawLine(color, Offset(e.x1, e.y1), Offset(e.x2, e.y2), e.width, StrokeCap.Round)
        ShapeKind.ARROW -> {
            drawLine(color, Offset(e.x1, e.y1), Offset(e.x2, e.y2), e.width, StrokeCap.Round)
            val head = DrawingSvg.arrowHead(e)
            val p = Path().apply { moveTo(head[0].x, head[0].y); lineTo(head[1].x, head[1].y); lineTo(head[2].x, head[2].y) }
            drawPath(p, color, style = stroke)
        }
        ShapeKind.RECT -> both { c, s -> drawRect(c, Offset(l, t), Size(w, h), style = s) }
        ShapeKind.ROUNDED_RECT -> both { c, s -> drawRoundRect(c, Offset(l, t), Size(w, h), CornerRadius(minOf(w, h) * 0.18f), style = s) }
        ShapeKind.ELLIPSE -> both { c, s -> drawOval(c, Offset(l, t), Size(w, h), style = s) }
        ShapeKind.TRIANGLE -> {
            val p = Path().apply { moveTo(l + w / 2, t); lineTo(l + w, t + h); lineTo(l, t + h); close() }
            both { c, s -> drawPath(p, c, style = s) }
        }
        ShapeKind.STAR -> {
            val pts = Geometry.starPoints(Rect(l, t, l + w, t + h))
            val p = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close() }
            both { c, s -> drawPath(p, c, style = s) }
        }
    }
}

/** Dashed selection box with a resize handle at the bottom-right. */
fun DrawScope.drawSelection(b: Rect, color: Color, zoom: Float) {
    val pad = 8f / zoom
    drawRect(color, Offset(b.left - pad, b.top - pad), Size(b.width + 2 * pad, b.height + 2 * pad),
        style = DrawStroke(2f / zoom, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / zoom, 6f / zoom))))
    drawCircle(color, HANDLE_RADIUS / zoom, Offset(b.right + pad, b.bottom + pad))
    drawCircle(Color.White, (HANDLE_RADIUS - 4f) / zoom, Offset(b.right + pad, b.bottom + pad))
}

const val HANDLE_RADIUS = 18f

/** Off-screen rendering for previews and PNG/PDF/Word export. */
object DrawingImages {
    fun textMeasurer(context: Context, density: Density = Density(context)) =
        TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)

    /** Renders the whole page so its longer side is at most [maxSide] pixels. */
    fun bitmap(context: Context, d: Drawing, maxSide: Int = 2000): Bitmap {
        val s = minOf(1f, maxSide / maxOf(d.width, d.height))
        val w = (d.width * s).toInt().coerceAtLeast(1); val h = (d.height * s).toInt().coerceAtLeast(1)
        val img = ImageBitmap(w, h, ImageBitmapConfig.Argb8888)
        val density = Density(1f)
        val tm = textMeasurer(context, density)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(w.toFloat(), h.toFloat())) {
            scale(s, s, pivot = Offset.Zero) { drawDrawingContent(d, tm) }
        }
        return img.asAndroidBitmap()
    }

    fun png(context: Context, d: Drawing, maxSide: Int = 2000): ByteArray {
        val bmp = bitmap(context, d, maxSide)
        return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
    }

    /** Draws onto an Android canvas (e.g. a PDF page), fitted into [targetW]×[targetH]. */
    fun drawOn(context: Context, canvas: android.graphics.Canvas, d: Drawing, targetW: Float, targetH: Float) {
        val s = minOf(targetW / d.width, targetH / d.height)
        val density = Density(1f)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(targetW, targetH)) {
            translate((targetW - d.width * s) / 2f, 0f) { scale(s, s, pivot = Offset.Zero) { drawDrawingContent(d, textMeasurer(context, density)) } }
        }
    }

    fun svg(d: Drawing): String = DrawingSvg.render(d, clipartPath = ClipartLibrary::svgPath) { id -> app.margin.domain.text.NoteFont.from(id)?.css ?: "sans-serif" }
}
