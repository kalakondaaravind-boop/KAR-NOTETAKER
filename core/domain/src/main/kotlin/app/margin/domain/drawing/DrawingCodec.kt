package app.margin.domain.drawing

import app.margin.domain.model.PageStyle
import app.margin.domain.text.Palette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Drawing file format (stored as an attachment, MIME [MIME], extension [EXTENSION]).
 * Versioned JSON; decoding is defensive: unknown element types are skipped, numbers are clamped,
 * sizes are bounded, so a damaged or hostile file can never crash the app.
 */
object DrawingCodec {
    const val MIME = "application/vnd.kar.drawing+json"
    const val EXTENSION = "kardraw"
    const val VERSION = 1
    private val json = Json { prettyPrint = false }

    fun encode(d: Drawing): String = json.encodeToString(JsonElement.serializer(), buildJsonObject {
        put("format", "kar-drawing"); put("version", VERSION)
        put("width", d.width); put("height", d.height); put("page", d.page.id); put("background", Palette.toArgbHex(d.background))
        put("elements", buildJsonArray { d.elements.forEach { add(element(it)) } })
    })

    private fun element(e: Element): JsonObject = buildJsonObject {
        put("id", e.id)
        when (e) {
            is Stroke -> {
                put("type", "stroke"); put("tool", e.tool.id); put("color", Palette.toArgbHex(e.color)); put("width", e.width)
                // Flat [x, y, p, x, y, p, …] rounded to 0.1 to keep files small.
                put("pts", buildJsonArray { e.points.forEach { add(JsonPrimitive(r(it.x))); add(JsonPrimitive(r(it.y))); add(JsonPrimitive(r(it.pressure))) } })
            }
            is Shape -> {
                put("type", "shape"); put("kind", e.kind.id); put("color", Palette.toArgbHex(e.color)); put("width", e.width)
                e.fill?.let { put("fill", Palette.toArgbHex(it)) }
                put("x1", e.x1); put("y1", e.y1); put("x2", e.x2); put("y2", e.y2)
            }
            is TextBox -> {
                put("type", "text"); put("x", e.x); put("y", e.y); put("text", e.text); put("color", Palette.toArgbHex(e.color)); put("size", e.size)
                e.font?.let { put("font", it) }; if (e.bold) put("bold", true); if (e.italic) put("italic", true)
            }
            is Clipart -> {
                put("type", "clipart"); put("key", e.key); put("x", e.x); put("y", e.y); put("size", e.size); put("color", Palette.toArgbHex(e.color))
            }
        }
    }

    private fun r(f: Float) = Math.round(f * 10f) / 10f

    class InvalidDrawing(msg: String) : Exception(msg)

    fun decode(text: String): Drawing {
        val root = try { Json.parseToJsonElement(text) as? JsonObject } catch (e: Exception) { null } ?: throw InvalidDrawing("not a drawing file")
        if (root.str("format") != "kar-drawing") throw InvalidDrawing("not a drawing file")
        val v = root.num("version") ?: 0f
        if (v > VERSION) throw InvalidDrawing("drawing was made by a newer version of the app")
        val w = (root.num("width") ?: Drawing.DEFAULT_WIDTH).finiteOr(Drawing.DEFAULT_WIDTH).coerceIn(100f, Drawing.MAX_PAGE)
        val h = (root.num("height") ?: Drawing.DEFAULT_HEIGHT).finiteOr(Drawing.DEFAULT_HEIGHT).coerceIn(100f, Drawing.MAX_PAGE)
        val els = (root["elements"] as? JsonArray).orEmpty().take(Drawing.MAX_ELEMENTS).mapNotNull { (it as? JsonObject)?.let(::decodeElement) }
        val ids = HashSet<String>()
        return Drawing(w, h, PageStyle.from(root.str("page")), root.color("background") ?: 0xFFFFFFFF, els.filter { ids.add(it.id) })
    }

    private fun decodeElement(o: JsonObject): Element? {
        val id = o.str("id")?.takeIf { it.isNotBlank() && it.length <= 64 } ?: return null
        val color = o.color("color") ?: 0xFF000000
        fun f(k: String, d: Float = 0f) = (o.num(k) ?: d).finiteOr(d).coerceIn(-Drawing.MAX_PAGE, Drawing.MAX_PAGE)
        return when (o.str("type")) {
            "stroke" -> {
                val flat = (o["pts"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                val pts = flat.chunked(3).filter { it.size == 3 && it.all(Float::isFinite) }.take(Drawing.MAX_POINTS_PER_STROKE)
                    .map { Pt(it[0].coerceIn(-Drawing.MAX_PAGE, Drawing.MAX_PAGE), it[1].coerceIn(-Drawing.MAX_PAGE, Drawing.MAX_PAGE), it[2].coerceIn(0f, 1f)) }
                if (pts.isEmpty()) null else Stroke(id, pts, color, f("width", 4f).coerceIn(0.5f, 200f), InkTool.from(o.str("tool")))
            }
            "shape" -> Shape(id, ShapeKind.from(o.str("kind")), f("x1"), f("y1"), f("x2"), f("y2"), color, f("width", 4f).coerceIn(0.5f, 200f), o.color("fill"))
            "text" -> TextBox(id, f("x"), f("y"), (o.str("text") ?: "").take(10_000), color, f("size", 28f).coerceIn(6f, 400f),
                o.str("font")?.take(40), o.bool("bold"), o.bool("italic"))
            "clipart" -> o.str("key")?.takeIf { it.length in 1..64 }?.let { Clipart(id, it, f("x"), f("y"), f("size", 96f).coerceIn(12f, 2000f), color) }
            else -> null
        }
    }

    private fun Float.finiteOr(d: Float) = if (isFinite()) this else d
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.floatOrNull
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonObject.color(k: String): Long? = (this[k] as? JsonPrimitive)?.let { p -> p.contentOrNull?.let(Palette::parseHex) ?: p.longOrNull }
}

/**
 * SVG export (also used to embed drawings in HTML exports). Clipart shapes come from the app's icon library
 * through [clipartPath] (24×24 SVG path data); unknown keys are drawn as a labelled box.
 */
object DrawingSvg {
    fun render(d: Drawing, clipartPath: (String) -> String? = { null }, fontCss: (String?) -> String = { "sans-serif" }): String = buildString {
        append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${n(d.width)} ${n(d.height)}" width="${n(d.width)}" height="${n(d.height)}">""")
        append("""<rect width="100%" height="100%" fill="${hex(d.background)}"/>""")
        for (m in PageGeometry.marks(d.page, d.width, d.height)) when (m) {
            is PageGeometry.Line -> append("""<line x1="${n(m.x1)}" y1="${n(m.y1)}" x2="${n(m.x2)}" y2="${n(m.y2)}" stroke="${if (m.accent) "#E57373" else "#B3CDE3"}" stroke-width="1"/>""")
            is PageGeometry.Dot -> append("""<circle cx="${n(m.x)}" cy="${n(m.y)}" r="1.6" fill="#9DB6CC"/>""")
            is PageGeometry.Box -> append("""<rect x="${n(m.rect.left)}" y="${n(m.rect.top)}" width="${n(m.rect.width)}" height="${n(m.rect.height)}" fill="none" stroke="#9DB6CC" stroke-width="1.5"/>""")
            is PageGeometry.CheckBox -> append("""<rect x="${n(m.rect.left)}" y="${n(m.rect.top)}" width="${n(m.rect.width)}" height="${n(m.rect.height)}" rx="3" fill="none" stroke="#7FA3C4" stroke-width="1.5"/>""")
        }
        // Highlighter strokes go beneath ink, like the canvas.
        val (hl, rest) = d.elements.partition { it is Stroke && it.tool == InkTool.HIGHLIGHTER }
        (hl + rest).forEach { append(element(it, clipartPath, fontCss)) }
        append("</svg>")
    }

    private fun element(e: Element, clipartPath: (String) -> String?, fontCss: (String?) -> String): String = when (e) {
        is Stroke -> {
            val (s, quads) = Geometry.smoothQuads(e.points)
            val path = StringBuilder("M${n(s.x)} ${n(s.y)}")
            quads.forEach { q -> path.append(" Q${n(q[0])} ${n(q[1])} ${n(q[2])} ${n(q[3])}") }
            if (e.points.size == 1) path.append(" l0.01 0")
            """<path d="$path" fill="none" stroke="${hex(e.color)}" stroke-opacity="${n(e.tool.alpha * alpha(e.color))}" stroke-width="${n(e.width)}" stroke-linecap="${if (e.tool == InkTool.HIGHLIGHTER) "square" else "round"}" stroke-linejoin="round"/>"""
        }
        is Shape -> shape(e)
        is TextBox -> {
            val lines = e.text.split('\n')
            val style = (if (e.bold) "font-weight:bold;" else "") + (if (e.italic) "font-style:italic;" else "") + "font-family:${esc(fontCss(e.font))}"
            buildString {
                append("""<text x="${n(e.x)}" y="${n(e.y + e.size)}" font-size="${n(e.size)}" fill="${hex(e.color)}" style="$style">""")
                lines.forEachIndexed { i, l -> append("""<tspan x="${n(e.x)}" dy="${if (i == 0) "0" else n(e.size * 1.25f)}">${esc(l)}</tspan>""") }
                append("</text>")
            }
        }
        is Clipart -> clipartPath(e.key)?.let { p ->
            """<path transform="translate(${n(e.x)} ${n(e.y)}) scale(${n(e.size / 24f)})" d="${esc(p)}" fill="${hex(e.color)}"/>"""
        } ?: """<rect x="${n(e.x)}" y="${n(e.y)}" width="${n(e.size)}" height="${n(e.size)}" fill="none" stroke="${hex(e.color)}"/><text x="${n(e.x + 4)}" y="${n(e.y + 16)}" font-size="12">${esc(e.key)}</text>"""
    }

    private fun shape(e: Shape): String {
        val stroke = """stroke="${hex(e.color)}" stroke-width="${n(e.width)}" stroke-linecap="round" stroke-linejoin="round""""
        val fill = e.fill?.let { """fill="${hex(it)}" fill-opacity="${n(alpha(it))}"""" } ?: """fill="none""""
        val l = minOf(e.x1, e.x2); val t = minOf(e.y1, e.y2); val w = kotlin.math.abs(e.x2 - e.x1); val h = kotlin.math.abs(e.y2 - e.y1)
        return when (e.kind) {
            ShapeKind.LINE -> """<line x1="${n(e.x1)}" y1="${n(e.y1)}" x2="${n(e.x2)}" y2="${n(e.y2)}" $stroke/>"""
            ShapeKind.ARROW -> {
                val head = arrowHead(e)
                """<line x1="${n(e.x1)}" y1="${n(e.y1)}" x2="${n(e.x2)}" y2="${n(e.y2)}" $stroke/><polyline points="${head.joinToString(" ") { "${n(it.x)},${n(it.y)}" }}" fill="none" $stroke/>"""
            }
            ShapeKind.RECT -> """<rect x="${n(l)}" y="${n(t)}" width="${n(w)}" height="${n(h)}" $fill $stroke/>"""
            ShapeKind.ROUNDED_RECT -> """<rect x="${n(l)}" y="${n(t)}" width="${n(w)}" height="${n(h)}" rx="${n(minOf(w, h) * 0.18f)}" $fill $stroke/>"""
            ShapeKind.ELLIPSE -> """<ellipse cx="${n(l + w / 2)}" cy="${n(t + h / 2)}" rx="${n(w / 2)}" ry="${n(h / 2)}" $fill $stroke/>"""
            ShapeKind.TRIANGLE -> """<polygon points="${n(l + w / 2)},${n(t)} ${n(l + w)},${n(t + h)} ${n(l)},${n(t + h)}" $fill $stroke/>"""
            ShapeKind.STAR -> """<polygon points="${Geometry.starPoints(Rect(l, t, l + w, t + h)).joinToString(" ") { "${n(it.x)},${n(it.y)}" }}" $fill $stroke/>"""
        }
    }

    /** Two points of the arrow head plus the tip, as a polyline: wing1, tip, wing2. */
    fun arrowHead(e: Shape): List<Pt> {
        val ang = kotlin.math.atan2((e.y2 - e.y1).toDouble(), (e.x2 - e.x1).toDouble())
        val len = (e.width * 4f + 10f).toDouble()
        fun wing(off: Double) = Pt((e.x2 - len * kotlin.math.cos(ang + off)).toFloat(), (e.y2 - len * kotlin.math.sin(ang + off)).toFloat())
        return listOf(wing(0.5), Pt(e.x2, e.y2), wing(-0.5))
    }

    private fun n(f: Float): String = if (f == f.toLong().toFloat()) f.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, f).trimEnd('0').trimEnd('.')
    private fun hex(argb: Long) = Palette.toHex(argb)
    private fun alpha(argb: Long) = ((argb ushr 24) and 0xFF) / 255f
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
