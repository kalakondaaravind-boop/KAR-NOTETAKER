package app.margin.domain.text

/**
 * Inline Markdown model. Supported syntax (documented in docs/ARCHITECTURE.md):
 *  **bold**  __bold__  *italic*  _italic_  ~~strike~~  <u>underline</u>  `code`
 *  [text](url)  [[Wiki link]]  bare https:// URLs  ![alt](url) (image reference)
 * Every node records the source range it came from so the editor can style raw text
 * without changing character offsets.
 */
sealed interface Inline {
    val start: Int
    val end: Int

    data class Text(val text: String, override val start: Int, override val end: Int) : Inline
    data class Bold(val children: List<Inline>, override val start: Int, override val end: Int) : Inline
    data class Italic(val children: List<Inline>, override val start: Int, override val end: Int) : Inline
    data class Strike(val children: List<Inline>, override val start: Int, override val end: Int) : Inline
    data class Underline(val children: List<Inline>, override val start: Int, override val end: Int) : Inline
    data class Code(val text: String, override val start: Int, override val end: Int) : Inline
    data class Link(val children: List<Inline>, val url: String, override val start: Int, override val end: Int) : Inline
    data class WikiLink(val target: String, override val start: Int, override val end: Int) : Inline
    data class AutoLink(val url: String, override val start: Int, override val end: Int) : Inline
    data class Image(val alt: String, val url: String, override val start: Int, override val end: Int) : Inline
}

object InlineParser {

    private val urlRegex = Regex("""https?://[^\s<>()\[\]]+[^\s<>()\[\].,;:!?'"]""")

    fun parse(text: String): List<Inline> = parseRange(text, 0, text.length, depth = 0)

    private fun parseRange(s: String, from: Int, to: Int, depth: Int): List<Inline> {
        val out = ArrayList<Inline>()
        val buf = StringBuilder()
        var bufStart = from
        var i = from

        fun flush(at: Int) {
            if (buf.isNotEmpty()) out += Inline.Text(buf.toString(), bufStart, at)
            buf.clear()
        }

        while (i < to) {
            val c = s[i]
            // Escapes: \* \_ etc.
            if (c == '\\' && i + 1 < to && s[i + 1] in ESCAPABLE) {
                if (buf.isEmpty()) bufStart = i
                buf.append(s[i + 1]); i += 2; continue
            }
            val node: Pair<Inline, Int>? = if (depth > 8) null else when {
                c == '`' -> code(s, i, to)
                c == '!' && i + 1 < to && s[i + 1] == '[' -> image(s, i, to)
                c == '[' && i + 1 < to && s[i + 1] == '[' -> wiki(s, i, to)
                c == '[' -> link(s, i, to, depth)
                s.startsWith("**", i) -> delimited(s, i, to, "**", depth) { ch, a, b -> Inline.Bold(ch, a, b) }
                s.startsWith("__", i) && boundaryBefore(s, i) -> delimited(s, i, to, "__", depth) { ch, a, b -> Inline.Bold(ch, a, b) }
                s.startsWith("~~", i) -> delimited(s, i, to, "~~", depth) { ch, a, b -> Inline.Strike(ch, a, b) }
                s.startsWith("<u>", i) -> delimited(s, i, to, "<u>", depth, close = "</u>") { ch, a, b -> Inline.Underline(ch, a, b) }
                c == '*' -> delimited(s, i, to, "*", depth) { ch, a, b -> Inline.Italic(ch, a, b) }
                c == '_' && boundaryBefore(s, i) -> delimited(s, i, to, "_", depth, requireBoundaryAfter = true) { ch, a, b -> Inline.Italic(ch, a, b) }
                (c == 'h') && (s.startsWith("http://", i) || s.startsWith("https://", i)) && boundaryBefore(s, i) -> autolink(s, i, to)
                else -> null
            }
            if (node != null) {
                flush(i)
                out += node.first
                i = node.second
                bufStart = i
            } else {
                if (buf.isEmpty()) bufStart = i
                buf.append(c); i++
            }
        }
        flush(to)
        return out
    }

    private fun boundaryBefore(s: String, i: Int) = i == 0 || !s[i - 1].isLetterOrDigit()

    private fun code(s: String, i: Int, to: Int): Pair<Inline, Int>? {
        val close = s.indexOf('`', i + 1)
        if (close < 0 || close >= to || close == i + 1) return null
        return Inline.Code(s.substring(i + 1, close), i, close + 1) to close + 1
    }

    private fun delimited(
        s: String, i: Int, to: Int, open: String, depth: Int,
        close: String = open, requireBoundaryAfter: Boolean = false,
        make: (List<Inline>, Int, Int) -> Inline,
    ): Pair<Inline, Int>? {
        val contentStart = i + open.length
        if (contentStart >= to || s[contentStart].isWhitespace()) return null
        var j = contentStart
        while (j < to) {
            var k = s.indexOf(close, j)
            if (k < 0 || k + close.length > to) return null
            // "***" closing a "**" run that contains an inner "*": the outer delimiter is the last two.
            if (close.length == 2 && close[0] == close[1]) {
                while (k + close.length < to && s[k + close.length] == close[0]) k++
            }
            // For single '*' do not match the first half of '**'.
            val doubled = close.length == 1 && k + 1 < to && s[k + 1] == close[0]
            val validEnd = k > contentStart && !s[k - 1].isWhitespace() &&
                (!requireBoundaryAfter || k + close.length >= s.length || !s[k + close.length].isLetterOrDigit())
            if (doubled) { j = k + 2; continue }
            if (validEnd) {
                val children = parseRange(s, contentStart, k, depth + 1)
                return make(children, i, k + close.length) to k + close.length
            }
            j = k + close.length
        }
        return null
    }

    private fun link(s: String, i: Int, to: Int, depth: Int): Pair<Inline, Int>? {
        val closeBracket = findClosing(s, i, to, '[', ']') ?: return null
        if (closeBracket + 1 >= to || s[closeBracket + 1] != '(') return null
        val closeParen = s.indexOf(')', closeBracket + 2)
        if (closeParen < 0 || closeParen >= to) return null
        val url = s.substring(closeBracket + 2, closeParen).trim()
        if (url.isEmpty() || url.any { it.isWhitespace() }) return null
        return Inline.Link(parseRange(s, i + 1, closeBracket, depth + 1), url, i, closeParen + 1) to closeParen + 1
    }

    private fun image(s: String, i: Int, to: Int): Pair<Inline, Int>? {
        val closeBracket = findClosing(s, i + 1, to, '[', ']') ?: return null
        if (closeBracket + 1 >= to || s[closeBracket + 1] != '(') return null
        val closeParen = s.indexOf(')', closeBracket + 2)
        if (closeParen < 0 || closeParen >= to) return null
        val url = s.substring(closeBracket + 2, closeParen).trim()
        if (url.isEmpty()) return null
        return Inline.Image(s.substring(i + 2, closeBracket), url, i, closeParen + 1) to closeParen + 1
    }

    private fun wiki(s: String, i: Int, to: Int): Pair<Inline, Int>? {
        val close = s.indexOf("]]", i + 2)
        if (close < 0 || close + 2 > to) return null
        val target = s.substring(i + 2, close)
        if (target.isBlank() || '\n' in target || '[' in target) return null
        return Inline.WikiLink(target.trim(), i, close + 2) to close + 2
    }

    private fun autolink(s: String, i: Int, to: Int): Pair<Inline, Int>? {
        val m = urlRegex.find(s, i) ?: return null
        if (m.range.first != i || m.range.last + 1 > to) return null
        return Inline.AutoLink(m.value, i, m.range.last + 1) to m.range.last + 1
    }

    private fun findClosing(s: String, openIdx: Int, to: Int, open: Char, close: Char): Int? {
        var depth = 0
        var i = openIdx
        while (i < to) {
            when (s[i]) {
                '\\' -> i++
                open -> depth++
                close -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return null
    }

    /** Plain text with markup removed (used for search indexing, previews, AI context). */
    fun plainText(nodes: List<Inline>): String = buildString { nodes.forEach { appendPlain(it) } }

    private fun StringBuilder.appendPlain(n: Inline) {
        when (n) {
            is Inline.Text -> append(n.text)
            is Inline.Code -> append(n.text)
            is Inline.Bold -> n.children.forEach { appendPlain(it) }
            is Inline.Italic -> n.children.forEach { appendPlain(it) }
            is Inline.Strike -> n.children.forEach { appendPlain(it) }
            is Inline.Underline -> n.children.forEach { appendPlain(it) }
            is Inline.Link -> n.children.forEach { appendPlain(it) }
            is Inline.WikiLink -> append(n.target)
            is Inline.AutoLink -> append(n.url)
            is Inline.Image -> append(n.alt)
        }
    }

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!~<>|"
}
