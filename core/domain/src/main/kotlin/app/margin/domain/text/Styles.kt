package app.margin.domain.text

/**
 * Character styling beyond Markdown: text colour, highlight (background) colour and font.
 *
 * Stored inline as portable HTML that any Markdown renderer understands:
 *   <span style="color:#E53935">red</span>
 *   <span style="background-color:#FFF59D">highlighted</span>
 *   <span style="font-family:Caveat">handwritten</span>
 *   <mark>highlighted with the default marker</mark>
 * Several properties can share one span: style="color:#1E88E5;font-family:Lora".
 */
enum class NoteFont(val id: String, val displayName: String, val css: String, val bundled: Boolean) {
    SANS("sans", "Sans (default)", "sans-serif", false),
    SERIF("serif", "Serif", "serif", false),
    MONO("mono", "Monospace", "monospace", false),
    CAVEAT("caveat", "Caveat · handwriting", "'Caveat', cursive", true),
    PATRICK_HAND("patrick-hand", "Patrick Hand · marker", "'Patrick Hand', cursive", true),
    DANCING_SCRIPT("dancing-script", "Dancing Script · script", "'Dancing Script', cursive", true),
    LORA("lora", "Lora · book serif", "'Lora', serif", true),
    MONTSERRAT("montserrat", "Montserrat · modern", "'Montserrat', sans-serif", true),
    PLAYFAIR("playfair", "Playfair Display · elegant", "'Playfair Display', serif", true);

    /** Name written into `font-family:` (the first CSS family, unquoted). */
    val familyName: String get() = css.substringBefore(',').trim().trim('\'')

    companion object {
        /** Accepts an id ("caveat"), a family name ("Caveat", "'Patrick Hand'") or a CSS list. */
        fun from(value: String?): NoteFont? {
            if (value.isNullOrBlank()) return null
            val first = value.substringBefore(',').trim().trim('\'', '"').lowercase()
            return entries.firstOrNull { it.id == first || it.familyName.lowercase() == first || it.css.lowercase() == value.trim().lowercase() }
                ?: when (first) { "sans-serif", "roboto", "arial", "helvetica" -> SANS; "monospace", "courier", "courier new" -> MONO; "times", "times new roman", "georgia" -> SERIF; else -> null }
        }
    }
}

/** ARGB colours (0xAARRGGBB). */
object Palette {
    /** Text / ink colours: 4 rows of 8, from strong to soft, readable on light paper. */
    val ink: List<Long> = listOf(
        0xFF000000, 0xFF424242, 0xFF757575, 0xFFBDBDBD, 0xFFFFFFFF, 0xFF5D4037, 0xFF8D6E63, 0xFFD7CCC8,
        0xFFD32F2F, 0xFFE53935, 0xFFF4511E, 0xFFFB8C00, 0xFFF9A825, 0xFFFDD835, 0xFFC0CA33, 0xFF7CB342,
        0xFF43A047, 0xFF00897B, 0xFF00ACC1, 0xFF039BE5, 0xFF2A76AD, 0xFF1E88E5, 0xFF3949AB, 0xFF5E35B1,
        0xFF8E24AA, 0xFFD81B60, 0xFFEC407A, 0xFFF48FB1, 0xFFCE93D8, 0xFF90CAF9, 0xFFA5D6A7, 0xFFFFCC80,
    )
    /** Highlighter colours (pastel, text stays readable on them). */
    val highlight: List<Long> = listOf(0xFFFFF59D, 0xFFFFE0B2, 0xFFFFCDD2, 0xFFF8BBD0, 0xFFE1BEE7, 0xFFC5CAE9, 0xFFBBDEFB, 0xFFB2EBF2, 0xFFC8E6C9, 0xFFDCEDC8)
    const val DEFAULT_MARK: Long = 0xFFFFF59D

    fun toHex(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

    /** "#RGB", "#RRGGBB" or "#AARRGGBB" → ARGB; null for anything else (names, rgb(), injections). */
    fun parseHex(value: String?): Long? {
        val v = value?.trim()?.removePrefix("#") ?: return null
        if (v.isEmpty() || v.any { it !in "0123456789abcdefABCDEF" }) return null
        return when (v.length) {
            3 -> 0xFF000000 or v.map { "$it$it" }.joinToString("").toLong(16)
            6 -> 0xFF000000 or v.toLong(16)
            8 -> v.toLong(16)
            else -> null
        }
    }

    /** HSV (h 0..360, s,v 0..1) → ARGB, for the custom colour picker. */
    fun fromHsv(h: Float, s: Float, v: Float): Long {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = v * s
        val x = c * (1 - kotlin.math.abs(hh % 2 - 1))
        val (r1, g1, b1) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = v - c
        fun ch(f: Float) = ((f + m) * 255f).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000 or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }
}

/** Parsed `style="..."` of a span. Only the three supported properties are kept; anything else is dropped. */
data class TextStyleAttrs(val color: Long? = null, val background: Long? = null, val font: NoteFont? = null) {
    val isEmpty: Boolean get() = color == null && background == null && font == null

    fun toCss(): String = listOfNotNull(
        color?.let { "color:${Palette.toHex(it)}" },
        background?.let { "background-color:${Palette.toHex(it)}" },
        font?.let { "font-family:${it.familyName}" },
    ).joinToString(";")

    fun openTag(): String = "<span style=\"${toCss()}\">"

    companion object {
        const val CLOSE = "</span>"

        fun parseCss(css: String): TextStyleAttrs {
            var color: Long? = null; var bg: Long? = null; var font: NoteFont? = null
            css.split(';').forEach { decl ->
                val k = decl.substringBefore(':', "").trim().lowercase()
                val v = decl.substringAfter(':', "").trim()
                when (k) {
                    "color" -> color = Palette.parseHex(v) ?: color
                    "background-color", "background" -> bg = Palette.parseHex(v) ?: bg
                    "font-family" -> font = NoteFont.from(v) ?: font
                }
            }
            return TextStyleAttrs(color, bg, font)
        }
    }
}
