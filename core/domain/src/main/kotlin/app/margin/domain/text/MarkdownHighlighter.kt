package app.margin.domain.text

/**
 * Computes style spans over the raw Markdown text for the "live" editor. Offsets map
 * 1:1 to the text, so the UI can apply them via an identity offset mapping and the user
 * keeps editing plain characters (no formatting mode to select first).
 */
object MarkdownHighlighter {

    /**
     * SYNTAX marks Markdown/HTML punctuation (`**`, `# `, `<span …>`, `](url)` …). The editor hides it so text
     * reads as formatted ("what you see"), while the characters stay in the canonical Markdown.
     */
    enum class Kind { HEADING1, HEADING2, HEADING3, BOLD, ITALIC, STRIKE, UNDERLINE, CODE, CODE_BLOCK, LINK, WIKILINK, QUOTE, LIST_MARKER, CHECKED_ITEM, SYNTAX, COLOR, HIGHLIGHT, FONT }

    /** [value]: ARGB colour (as Long) for COLOR/HIGHLIGHT, font id for FONT. */
    data class Span(val start: Int, val end: Int, val kind: Kind, val value: Any? = null)

    fun spans(text: String): List<Span> {
        val out = ArrayList<Span>()
        val lineStarts = IntArray(text.count { it == '\n' } + 1)
        var li = 1
        text.forEachIndexed { idx, c -> if (c == '\n') lineStarts[li++] = idx + 1 }
        val lines = text.split('\n')
        fun lineRange(line: Int) = lineStarts[line] to lineStarts[line] + lines[line].length

        for (b in MarkdownParser.parse(text)) {
            when (b) {
                is Block.Heading -> {
                    val (s, e) = lineRange(b.firstLine)
                    val kind = when (b.level) { 1 -> Kind.HEADING1; 2 -> Kind.HEADING2; else -> Kind.HEADING3 }
                    out += Span(s, e, kind)
                    out += Span(s, s + lines[b.firstLine].indexOf(' ').coerceAtLeast(0) + 1, Kind.SYNTAX)
                    val textOffset = s + lines[b.firstLine].indexOf(b.text).coerceAtLeast(0)
                    inlineSpans(b.inlines, textOffset, out)
                }
                is Block.Paragraph -> inlineSpans(b.inlines, lineStarts[b.firstLine], out)
                is Block.ListItem -> {
                    val (s, e) = lineRange(b.firstLine)
                    val line = lines[b.firstLine]
                    val textIdx = line.length - b.text.length
                    out += Span(s, s + textIdx, Kind.LIST_MARKER)
                    if (b.checked) out += Span(s + textIdx, e, Kind.CHECKED_ITEM)
                    inlineSpans(b.inlines, s + textIdx, out)
                }
                is Block.Quote -> {
                    val (s, _) = lineRange(b.firstLine)
                    val (_, e) = lineRange(b.lastLine)
                    out += Span(s, e, Kind.QUOTE)
                }
                is Block.Code -> {
                    val (s, _) = lineRange(b.firstLine)
                    val (_, e) = lineRange(b.lastLine)
                    out += Span(s, e, Kind.CODE_BLOCK)
                }
                is Block.Divider -> { val (s, e) = lineRange(b.firstLine); out += Span(s, e, Kind.SYNTAX) }
                is Block.Table -> {
                    for (l in b.firstLine..b.lastLine) {
                        val (s, _) = lineRange(l)
                        lines[l].forEachIndexed { i, c -> if (c == '|') out += Span(s + i, s + i + 1, Kind.SYNTAX) }
                    }
                    val (s, e) = lineRange(b.firstLine + 1)
                    out += Span(s, e, Kind.SYNTAX)
                }
            }
        }
        return out.filter { it.end > it.start && it.start >= 0 && it.end <= text.length }
    }

    private fun inlineSpans(nodes: List<Inline>, base: Int, out: MutableList<Span>) {
        for (n in nodes) {
            val s = base + n.start
            val e = base + n.end
            when (n) {
                is Inline.Text -> Unit
                is Inline.Bold -> { out += Span(s, e, Kind.BOLD); markers(s, e, 2, out); inlineSpans(n.children, base, out) }
                is Inline.Italic -> { out += Span(s, e, Kind.ITALIC); markers(s, e, 1, out); inlineSpans(n.children, base, out) }
                is Inline.Strike -> { out += Span(s, e, Kind.STRIKE); markers(s, e, 2, out); inlineSpans(n.children, base, out) }
                is Inline.Underline -> {
                    out += Span(s, e, Kind.UNDERLINE)
                    out += Span(s, s + 3, Kind.SYNTAX); out += Span(e - 4, e, Kind.SYNTAX)
                    inlineSpans(n.children, base, out)
                }
                is Inline.Code -> { out += Span(s, e, Kind.CODE); out += Span(s, s + 1, Kind.SYNTAX); out += Span(e - 1, e, Kind.SYNTAX) }
                is Inline.Link -> {
                    out += Span(s, e, Kind.LINK)
                    val labelEnd = n.children.lastOrNull()?.let { base + it.end } ?: (s + 1)
                    out += Span(s, s + 1, Kind.SYNTAX)            // [
                    out += Span(labelEnd, e, Kind.SYNTAX)         // ](url)
                    inlineSpans(n.children, base, out)
                }
                is Inline.WikiLink -> { out += Span(s, e, Kind.WIKILINK); out += Span(s, s + 2, Kind.SYNTAX); out += Span(e - 2, e, Kind.SYNTAX) }
                is Inline.Styled -> {
                    val inner = base + n.openEnd
                    val innerEnd = base + n.closeStart
                    n.attrs.color?.let { out += Span(inner, innerEnd, Kind.COLOR, it) }
                    n.attrs.background?.let { out += Span(inner, innerEnd, Kind.HIGHLIGHT, it) }
                    n.attrs.font?.let { out += Span(inner, innerEnd, Kind.FONT, it.id) }
                    out += Span(s, inner, Kind.SYNTAX)
                    out += Span(innerEnd, e, Kind.SYNTAX)
                    inlineSpans(n.children, base, out)
                }
                is Inline.AutoLink -> out += Span(s, e, Kind.LINK)
                is Inline.Image -> out += Span(s, e, Kind.LINK)
            }
        }
    }

    private fun markers(s: Int, e: Int, len: Int, out: MutableList<Span>) {
        out += Span(s, s + len, Kind.SYNTAX)
        out += Span(e - len, e, Kind.SYNTAX)
    }
}
