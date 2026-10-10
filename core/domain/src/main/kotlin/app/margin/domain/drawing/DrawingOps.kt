package app.margin.domain.drawing

import app.margin.domain.model.PageStyle

/** Editing operations. All pure: they return a new [Drawing] (and selection where relevant). */
object DrawingOps {
    /** Offset applied to duplicated / pasted elements so the copy is visibly separate. */
    const val PASTE_OFFSET = 24f

    fun add(d: Drawing, e: Element): Drawing =
        if (d.elements.size >= Drawing.MAX_ELEMENTS) d else d.copy(elements = d.elements + e)

    fun delete(d: Drawing, ids: Set<String>): Drawing = d.copy(elements = d.elements.filterNot { it.id in ids })

    fun move(d: Drawing, ids: Set<String>, dx: Float, dy: Float): Drawing =
        d.copy(elements = d.elements.map { if (it.id in ids) it.translate(dx, dy) else it })

    /** Scales the selection about its bounds' top-left (resize handle). */
    fun scale(d: Drawing, ids: Set<String>, sx: Float, sy: Float): Drawing {
        val b = selectionBounds(d, ids) ?: return d
        return d.copy(elements = d.elements.map { if (it.id in ids) it.scale(sx, sy, b.left, b.top) else it })
    }

    fun recolor(d: Drawing, ids: Set<String>, argb: Long): Drawing =
        d.copy(elements = d.elements.map { if (it.id in ids) it.recolor(argb) else it })

    fun bringToFront(d: Drawing, ids: Set<String>): Drawing =
        d.copy(elements = d.elements.filterNot { it.id in ids } + d.elements.filter { it.id in ids })

    fun sendToBack(d: Drawing, ids: Set<String>): Drawing =
        d.copy(elements = d.elements.filter { it.id in ids } + d.elements.filterNot { it.id in ids })

    fun selectionBounds(d: Drawing, ids: Set<String>): Rect? =
        d.elements.filter { it.id in ids }.map { it.bounds() }.reduceOrNull { a, b -> a.union(b) }

    /** Topmost element under the point. */
    fun hitTest(d: Drawing, x: Float, y: Float, tolerance: Float = 12f): Element? =
        d.elements.lastOrNull { Geometry.hits(it, x, y, tolerance) }

    /**
     * Lasso selection: a stroke is selected when most of its points are inside the loop; other elements when
     * their centre is inside.
     */
    fun lasso(d: Drawing, loop: List<Pt>): Set<String> {
        if (loop.size < 3) return emptySet()
        return d.elements.filter { e ->
            when (e) {
                is Stroke -> e.points.count { Geometry.pointInPolygon(it.x, it.y, loop) } * 2 > e.points.size
                else -> e.bounds().let { Geometry.pointInPolygon(it.centerX, it.centerY, loop) }
            }
        }.map { it.id }.toSet()
    }

    /** Copies with fresh ids, offset by [PASTE_OFFSET]. Returns the new drawing and the copies' ids. */
    fun duplicate(d: Drawing, ids: Set<String>, newId: () -> String): Pair<Drawing, Set<String>> {
        val copies = d.elements.filter { it.id in ids }.map { it.withId(newId()).translate(PASTE_OFFSET, PASTE_OFFSET) }
        return d.copy(elements = (d.elements + copies).take(Drawing.MAX_ELEMENTS)) to copies.map { it.id }.toSet()
    }

    /**
     * Pastes clipboard elements with fresh ids. With [at] the group's top-left is placed there, otherwise it is
     * offset from where it was copied.
     */
    fun paste(d: Drawing, clip: List<Element>, newId: () -> String, at: Pt? = null): Pair<Drawing, Set<String>> {
        if (clip.isEmpty()) return d to emptySet()
        val b = clip.map { it.bounds() }.reduce { a, c -> a.union(c) }
        val dx = at?.let { it.x - b.left } ?: PASTE_OFFSET
        val dy = at?.let { it.y - b.top } ?: PASTE_OFFSET
        val pasted = clip.map { it.withId(newId()).translate(dx, dy) }
        return d.copy(elements = (d.elements + pasted).take(Drawing.MAX_ELEMENTS)) to pasted.map { it.id }.toSet()
    }

    /** Clipboard content for copy/cut (in drawing order). */
    fun copy(d: Drawing, ids: Set<String>): List<Element> = d.elements.filter { it.id in ids }

    /**
     * Stroke eraser along a path. [partial] splits strokes where the eraser passed (like a real eraser);
     * otherwise any touched element is removed whole. Non-stroke elements are always removed whole.
     */
    fun erase(d: Drawing, path: List<Pt>, radius: Float, partial: Boolean, newId: () -> String): Drawing {
        if (path.isEmpty()) return d
        val area = Rect.of(path).inflate(radius)
        fun touched(x: Float, y: Float) = Geometry.distToPolyline(x, y, path) <= radius
        val out = ArrayList<Element>(d.elements.size)
        for (e in d.elements) {
            if (!e.bounds().intersects(area)) { out += e; continue }
            if (e is Stroke && partial) {
                val pieces = ArrayList<List<Pt>>()
                var cur = ArrayList<Pt>()
                for (p in e.points) {
                    if (Geometry.distToPolyline(p.x, p.y, path) <= radius + e.width / 2f) {
                        if (cur.size >= 2) pieces += cur
                        cur = ArrayList()
                    } else cur += p
                }
                if (cur.size >= 2) pieces += cur
                if (pieces.size == 1 && pieces[0].size == e.points.size) out += e
                else pieces.forEachIndexed { i, pts -> out += e.copy(id = if (i == 0) e.id else newId(), points = pts) }
            } else {
                val hit = path.any { Geometry.hits(e, it.x, it.y, radius) } || (e is Stroke && e.points.any { touched(it.x, it.y) })
                if (!hit) out += e
            }
        }
        return d.copy(elements = out)
    }

    /** Finishes a freehand stroke: drops near-duplicate points and simplifies, keeping the shape. */
    fun finishStroke(points: List<Pt>): List<Pt> {
        val dedup = ArrayList<Pt>(points.size)
        for (p in points) {
            val last = dedup.lastOrNull()
            if (last == null || kotlin.math.hypot(p.x - last.x, p.y - last.y) >= 0.75f) dedup += p
        }
        val simplified = if (dedup.size > 8) Geometry.simplify(dedup, 0.6f) else dedup
        return simplified.take(Drawing.MAX_POINTS_PER_STROKE)
    }

    fun setPage(d: Drawing, page: PageStyle) = d.copy(page = page)
    fun clear(d: Drawing) = d.copy(elements = emptyList())
}

/** Undo/redo over immutable drawings. Consecutive identical states are not recorded. */
class DrawingHistory(initial: Drawing, private val limit: Int = 100) {
    private val undo = ArrayDeque<Drawing>()
    private val redo = ArrayDeque<Drawing>()
    var current: Drawing = initial
        private set

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun push(next: Drawing) {
        if (next == current) return
        undo.addLast(current)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
        current = next
    }

    fun undo(): Drawing { if (undo.isNotEmpty()) { redo.addLast(current); current = undo.removeLast() }; return current }
    fun redo(): Drawing { if (redo.isNotEmpty()) { undo.addLast(current); current = redo.removeLast() }; return current }
}

/** Paper lines for [PageStyle]s, shared by the canvas, the text editor background and SVG/PDF export. */
object PageGeometry {
    sealed interface Mark
    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val accent: Boolean = false) : Mark
    data class Dot(val x: Float, val y: Float) : Mark
    data class Box(val rect: Rect) : Mark
    data class CheckBox(val rect: Rect) : Mark

    const val TOP_MARGIN = 96f
    const val LEFT_MARGIN = 72f

    /** [scale] converts the style's dp spacing into page units (1 for drawings, density for the editor). */
    fun marks(style: PageStyle, width: Float, height: Float, scale: Float = 1f): List<Mark> {
        val s = style.spacingDp * scale
        val out = ArrayList<Mark>()
        fun hLines(from: Float, x1: Float, x2: Float) { var y = from; while (y < height - s / 2) { out += Line(x1, y, x2, y); y += s } }
        when (style) {
            PageStyle.PLAIN -> Unit
            PageStyle.RULED, PageStyle.RULED_NARROW -> {
                hLines(TOP_MARGIN * scale, 0f, width)
                out += Line(LEFT_MARGIN * scale, 0f, LEFT_MARGIN * scale, height, accent = true)
            }
            PageStyle.GRID, PageStyle.GRID_SMALL -> {
                var x = 0f; while (x <= width) { out += Line(x, 0f, x, height); x += s }
                var y = 0f; while (y <= height) { out += Line(0f, y, width, y); y += s }
            }
            PageStyle.DOTTED -> {
                var y = s; while (y < height) { var x = s; while (x < width) { out += Dot(x, y); x += s }; y += s }
            }
            PageStyle.CORNELL -> {
                val cue = width * PageStyle.CORNELL_CUE_FRACTION
                val summaryTop = height * (1 - PageStyle.CORNELL_SUMMARY_FRACTION)
                var y = TOP_MARGIN * scale; while (y < summaryTop) { out += Line(0f, y, width, y); y += s }
                out += Line(cue, 0f, cue, summaryTop, accent = true)
                out += Line(0f, summaryTop, width, summaryTop, accent = true)
            }
            PageStyle.TODO -> {
                var y = TOP_MARGIN * scale
                val box = s * 0.45f
                while (y < height - s / 2) {
                    out += Line(0f, y, width, y)
                    out += CheckBox(Rect(LEFT_MARGIN * scale - box - 8 * scale, y - s / 2 - box / 2, LEFT_MARGIN * scale - 8 * scale, y - s / 2 + box / 2))
                    y += s
                }
            }
            PageStyle.MUSIC -> {
                var top = TOP_MARGIN * scale
                while (top + 4 * s < height - s) {
                    for (i in 0..4) out += Line(LEFT_MARGIN * scale / 2, top + i * s, width - LEFT_MARGIN * scale / 2, top + i * s)
                    top += 10 * s
                }
            }
            PageStyle.STORYBOARD -> {
                val m = 40f * scale; val gap = 32f * scale
                val cols = 2; val rows = 3
                val w = (width - 2 * m - (cols - 1) * gap) / cols
                val h = (height - 2 * m - (rows - 1) * gap) / rows
                for (r in 0 until rows) for (c in 0 until cols) {
                    val l = m + c * (w + gap); val t = m + r * (h + gap)
                    out += Box(Rect(l, t, l + w, t + h * 0.72f))
                    out += Line(l, t + h * 0.84f, l + w, t + h * 0.84f)
                    out += Line(l, t + h, l + w, t + h)
                }
            }
        }
        return out
    }
}
