package app.margin.domain.drawing

import app.margin.domain.model.PageStyle
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * Drawing model. Coordinates are page units (1 unit ≈ 1 dp at 100% zoom); the page is portrait A4-like
 * by default. Everything is immutable: tools produce a new [Drawing], which makes undo/redo trivial.
 */

data class Pt(val x: Float, val y: Float, val pressure: Float = 1f)

data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom
    fun intersects(o: Rect) = left <= o.right && o.left <= right && top <= o.bottom && o.top <= bottom
    fun union(o: Rect) = Rect(min(left, o.left), min(top, o.top), max(right, o.right), max(bottom, o.bottom))
    fun inflate(d: Float) = Rect(left - d, top - d, right + d, bottom + d)

    companion object {
        fun of(points: List<Pt>): Rect {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (p in points) { l = min(l, p.x); t = min(t, p.y); r = max(r, p.x); b = max(b, p.y) }
            return if (points.isEmpty()) Rect(0f, 0f, 0f, 0f) else Rect(l, t, r, b)
        }
    }
}

/** Pen types. Highlighter is translucent and drawn beneath ink; pencil is slightly textured/translucent. */
enum class InkTool(val id: String, val alpha: Float) {
    PEN("pen", 1f), PENCIL("pencil", 0.85f), MARKER("marker", 1f), HIGHLIGHTER("highlighter", 0.35f);
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: PEN }
}

enum class ShapeKind(val id: String) {
    LINE("line"), ARROW("arrow"), RECT("rect"), ROUNDED_RECT("rounded-rect"), ELLIPSE("ellipse"), TRIANGLE("triangle"), STAR("star");
    companion object { fun from(id: String?) = entries.firstOrNull { it.id == id } ?: LINE }
}

sealed interface Element {
    val id: String
    fun bounds(): Rect
    fun translate(dx: Float, dy: Float): Element
    /** Scales about the anchor point (ax, ay). */
    fun scale(sx: Float, sy: Float, ax: Float, ay: Float): Element
    fun withId(newId: String): Element
    fun recolor(argb: Long): Element
}

private fun sc(v: Float, a: Float, s: Float) = a + (v - a) * s

data class Stroke(
    override val id: String,
    val points: List<Pt>,
    val color: Long,
    val width: Float,
    val tool: InkTool = InkTool.PEN,
) : Element {
    override fun bounds() = Rect.of(points).inflate(width / 2f)
    override fun translate(dx: Float, dy: Float) = copy(points = points.map { it.copy(x = it.x + dx, y = it.y + dy) })
    override fun scale(sx: Float, sy: Float, ax: Float, ay: Float) =
        copy(points = points.map { it.copy(x = sc(it.x, ax, sx), y = sc(it.y, ay, sy)) }, width = (width * (abs(sx) + abs(sy)) / 2f).coerceIn(0.5f, 200f))
    override fun withId(newId: String) = copy(id = newId)
    override fun recolor(argb: Long) = copy(color = argb)
}

data class Shape(
    override val id: String,
    val kind: ShapeKind,
    val x1: Float, val y1: Float, val x2: Float, val y2: Float,
    val color: Long,
    val width: Float,
    /** Fill colour, or null for outline only. */
    val fill: Long? = null,
) : Element {
    override fun bounds() = Rect(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2)).inflate(width / 2f + if (kind == ShapeKind.ARROW) width * 3 else 0f)
    override fun translate(dx: Float, dy: Float) = copy(x1 = x1 + dx, y1 = y1 + dy, x2 = x2 + dx, y2 = y2 + dy)
    override fun scale(sx: Float, sy: Float, ax: Float, ay: Float) = copy(x1 = sc(x1, ax, sx), y1 = sc(y1, ay, sy), x2 = sc(x2, ax, sx), y2 = sc(y2, ay, sy))
    override fun withId(newId: String) = copy(id = newId)
    override fun recolor(argb: Long) = copy(color = argb, fill = fill?.let { argb and 0x00FFFFFF or (it and 0xFF000000) })
}

data class TextBox(
    override val id: String,
    val x: Float, val y: Float,
    val text: String,
    val color: Long,
    /** Font size in page units. */
    val size: Float = 28f,
    /** [app.margin.domain.text.NoteFont] id, or null for the default font. */
    val font: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
) : Element {
    /** Approximate box (the app measures precisely; this is used for selection/export only). */
    override fun bounds(): Rect {
        val lines = text.split('\n')
        val w = (lines.maxOfOrNull { it.length } ?: 0) * size * 0.55f
        return Rect(x, y, x + max(w, size), y + lines.size * size * 1.25f)
    }
    override fun translate(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
    override fun scale(sx: Float, sy: Float, ax: Float, ay: Float) = copy(x = sc(x, ax, sx), y = sc(y, ay, sy), size = (size * (abs(sx) + abs(sy)) / 2f).coerceIn(6f, 400f))
    override fun withId(newId: String) = copy(id = newId)
    override fun recolor(argb: Long) = copy(color = argb)
}

data class Clipart(
    override val id: String,
    /** Key into the app's clipart library (e.g. "star", "lightbulb"). Unknown keys render as a placeholder. */
    val key: String,
    val x: Float, val y: Float,
    val size: Float = 96f,
    val color: Long,
) : Element {
    override fun bounds() = Rect(x, y, x + size, y + size)
    override fun translate(dx: Float, dy: Float) = copy(x = x + dx, y = y + dy)
    override fun scale(sx: Float, sy: Float, ax: Float, ay: Float) = copy(x = sc(x, ax, sx), y = sc(y, ay, sy), size = (size * (abs(sx) + abs(sy)) / 2f).coerceIn(12f, 2000f))
    override fun withId(newId: String) = copy(id = newId)
    override fun recolor(argb: Long) = copy(color = argb)
}

data class Drawing(
    val width: Float = DEFAULT_WIDTH,
    val height: Float = DEFAULT_HEIGHT,
    val page: PageStyle = PageStyle.PLAIN,
    /** Paper colour. */
    val background: Long = 0xFFFFFFFF,
    val elements: List<Element> = emptyList(),
) {
    fun byId(id: String) = elements.firstOrNull { it.id == id }
    fun contentBounds(): Rect? = elements.map { it.bounds() }.reduceOrNull { a, b -> a.union(b) }

    companion object {
        const val DEFAULT_WIDTH = 1200f
        const val DEFAULT_HEIGHT = 1700f
        const val MAX_ELEMENTS = 20_000
        const val MAX_POINTS_PER_STROKE = 5_000
        const val MAX_PAGE = 20_000f
    }
}

/** Point/segment geometry used by hit-testing, erasing and lasso selection. */
object Geometry {
    fun distToSegment(px: Float, py: Float, a: Pt, b: Pt): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 == 0f) return hypot(px - a.x, py - a.y)
        val t = (((px - a.x) * dx + (py - a.y) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (a.x + t * dx), py - (a.y + t * dy))
    }

    fun distToPolyline(px: Float, py: Float, pts: List<Pt>): Float = when (pts.size) {
        0 -> Float.MAX_VALUE
        1 -> hypot(px - pts[0].x, py - pts[0].y)
        else -> (0 until pts.size - 1).minOf { distToSegment(px, py, pts[it], pts[it + 1]) }
    }

    /** Even-odd ray casting. */
    fun pointInPolygon(x: Float, y: Float, poly: List<Pt>): Boolean {
        if (poly.size < 3) return false
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > y) != (b.y > y) && x < (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    /** True when the point (with [tolerance]) touches the element. */
    fun hits(e: Element, x: Float, y: Float, tolerance: Float): Boolean = when (e) {
        is Stroke -> distToPolyline(x, y, e.points) <= tolerance + e.width / 2f
        is Shape -> when (e.kind) {
            ShapeKind.LINE, ShapeKind.ARROW -> distToSegment(x, y, Pt(e.x1, e.y1), Pt(e.x2, e.y2)) <= tolerance + e.width / 2f
            else -> e.bounds().inflate(tolerance).contains(x, y)
        }
        else -> e.bounds().inflate(tolerance).contains(x, y)
    }

    /** Ramer–Douglas–Peucker simplification; keeps first/last points. */
    fun simplify(points: List<Pt>, epsilon: Float): List<Pt> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size).also { it[0] = true; it[points.lastIndex] = true }
        val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to points.lastIndex) }
        while (stack.isNotEmpty()) {
            val (s, e) = stack.removeLast()
            var maxD = 0f; var idx = -1
            for (i in s + 1 until e) {
                val d = distToSegment(points[i].x, points[i].y, points[s], points[e])
                if (d > maxD) { maxD = d; idx = i }
            }
            if (idx >= 0 && maxD > epsilon) { keep[idx] = true; stack.add(s to idx); stack.add(idx to e) }
        }
        return points.filterIndexed { i, _ -> keep[i] }
    }

    /**
     * Smooth curve through the points as quadratic segments (control = point, end = midpoint to next).
     * Shared by on-screen rendering and SVG/PDF export so they look identical.
     * Returns the start point and a list of (cx, cy, ex, ey).
     */
    fun smoothQuads(points: List<Pt>): Pair<Pt, List<FloatArray>> {
        if (points.isEmpty()) return Pt(0f, 0f) to emptyList()
        if (points.size < 3) return points[0] to points.drop(1).map { floatArrayOf(it.x, it.y, it.x, it.y) }
        val out = ArrayList<FloatArray>(points.size)
        for (i in 1 until points.lastIndex) {
            val p = points[i]; val n = points[i + 1]
            out += floatArrayOf(p.x, p.y, (p.x + n.x) / 2f, (p.y + n.y) / 2f)
        }
        val last = points.last()
        out += floatArrayOf(last.x, last.y, last.x, last.y)
        return points[0] to out
    }

    /** Star polygon inside the rect (5 points). */
    fun starPoints(r: Rect, points: Int = 5): List<Pt> {
        val cx = r.centerX; val cy = r.centerY
        val outer = min(r.width, r.height) / 2f; val inner = outer * 0.45f
        return (0 until points * 2).map { i ->
            val rad = if (i % 2 == 0) outer else inner
            val a = Math.PI * i / points - Math.PI / 2
            Pt(cx + (rad * kotlin.math.cos(a)).toFloat(), cy + (rad * kotlin.math.sin(a)).toFloat())
        }
    }
}
