package app.margin.domain.text

/**
 * "What you see" view of a Markdown note for the editor. Formatting characters (`**`, `*`, `# `, `<span …>`,
 * link targets…) are hidden and list markers are shown as • / ☐ / ☑, while the stored text stays Markdown.
 * The editor applies [View.styles] and uses the offset maps so the cursor, selection and typing land in the
 * right place of the underlying Markdown.
 *
 * Cursor rule at a hidden marker: at the start of a line the cursor sits after it (typing never breaks a
 * heading or list), elsewhere before it (typing continues the formatting of the previous character, as in
 * word processors).
 */
object LiveMarkdown {
    data class View(
        val text: String,
        /** Style spans in view coordinates (SYNTAX and LIST_MARKER spans are consumed here). */
        val styles: List<MarkdownHighlighter.Span>,
        private val origToView: IntArray,
        private val viewToOrig: IntArray,
    ) {
        fun toView(orig: Int): Int = origToView[orig.coerceIn(0, origToView.lastIndex)]
        fun toOrig(view: Int): Int = viewToOrig[view.coerceIn(0, viewToOrig.lastIndex)]
    }

    private class Edit(val start: Int, val end: Int, val replacement: String, val afterBias: Boolean)

    private val tableish = Regex("""^[\s|:\-]+$""")
    private val quotePrefix = Regex("""^(\s{0,3}>\s?)""")

    fun build(source: String, showMarks: Boolean = false): View {
        val spans = MarkdownHighlighter.spans(source)
        if (showMarks) {
            val id = IntArray(source.length + 1) { it }
            return View(source, spans.filter { it.kind != MarkdownHighlighter.Kind.SYNTAX }, id, id)
        }
        fun lineStart(i: Int) = i == 0 || source[i - 1] == '\n'
        val edits = ArrayList<Edit>()
        for (s in spans) when (s.kind) {
            MarkdownHighlighter.Kind.SYNTAX -> {
                val raw = source.substring(s.start, s.end)
                if (raw.matches(tableish) && raw.contains('|') || raw == "|") continue          // keep tables readable
                if (lineStart(s.start) && raw.trim().let { it.length >= 3 && it.all { c -> c in "-*_ " } }) {
                    edits += Edit(s.start, s.end, "─".repeat(24), false)                       // divider
                } else edits += Edit(s.start, s.end, "", lineStart(s.start))
            }
            MarkdownHighlighter.Kind.LIST_MARKER -> {
                val raw = source.substring(s.start, s.end)
                val indent = raw.takeWhile { it == ' ' || it == '\t' }
                val marker = raw.drop(indent.length)
                val shown = when {
                    marker.contains("[ ]") -> "☐ "
                    marker.contains("[x]") || marker.contains("[X]") -> "☑ "
                    marker.firstOrNull() in listOf('-', '*', '+') -> "• "
                    else -> null // ordered numbers stay as typed
                }
                if (shown != null) edits += Edit(s.start, s.end, indent.replace("\t", "    ") + shown, true)
            }
            MarkdownHighlighter.Kind.QUOTE -> {
                var i = s.start
                while (i < s.end) {
                    val lineEnd = source.indexOf('\n', i).let { if (it < 0 || it > s.end) s.end else it }
                    quotePrefix.find(source.substring(i, lineEnd))?.let { m -> edits += Edit(i, i + m.value.length, "┃ ", true) }
                    i = lineEnd + 1
                }
            }
            else -> Unit
        }
        edits.sortWith(compareBy({ it.start }, { -it.end }))
        val kept = ArrayList<Edit>()
        var lastEnd = -1
        for (e in edits) if (e.start >= lastEnd && e.end > e.start) { kept += e; lastEnd = e.end }

        val view = StringBuilder(source.length)
        val o2v = IntArray(source.length + 1)
        val v2o = ArrayList<Int>(source.length + 1)
        var o = 0
        for (e in kept) {
            while (o < e.start) { o2v[o] = view.length; v2o += o; view.append(source[o]); o++ }
            // Hidden / replaced range.
            val vStart = view.length
            for (k in e.start until e.end) o2v[k] = if (e.afterBias) vStart + e.replacement.length else vStart
            for (k in e.replacement.indices) { v2o += if (e.afterBias) e.end else e.start; view.append(e.replacement[k]) }
            o = e.end
        }
        while (o < source.length) { o2v[o] = view.length; v2o += o; view.append(source[o]); o++ }
        o2v[source.length] = view.length
        v2o += source.length
        // A view position at a hidden range boundary: choose after (line-start markers) or before.
        for (e in kept) {
            val vAt = o2v[e.start].let { if (e.afterBias) it - e.replacement.length else it }
            if (e.replacement.isEmpty() && vAt in v2o.indices) v2o[vAt] = if (e.afterBias) e.end else e.start
            if (e.replacement.isNotEmpty() && e.afterBias) {
                val vEnd = vAt + e.replacement.length
                if (vEnd in v2o.indices) v2o[vEnd] = e.end
            }
        }
        val styles = spans.filter { it.kind != MarkdownHighlighter.Kind.SYNTAX && it.kind != MarkdownHighlighter.Kind.LIST_MARKER }
            .map { it.copy(start = o2v[it.start], end = o2v[it.end]) }
            .filter { it.end > it.start }
        return View(view.toString(), styles, o2v, v2o.toIntArray())
    }
}
