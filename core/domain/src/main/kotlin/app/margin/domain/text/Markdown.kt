package app.margin.domain.text

/**
 * Line-oriented block model for the canonical Markdown note body.
 * `firstLine`/`lastLine` are 0-based source line indexes, which lets the preview
 * toggle a checklist item or jump to a heading without re-serialising the whole note.
 */
sealed interface Block {
    val firstLine: Int
    val lastLine: Int

    data class Heading(val level: Int, val text: String, val inlines: List<Inline>, override val firstLine: Int) : Block {
        override val lastLine get() = firstLine
    }

    data class Paragraph(val text: String, val inlines: List<Inline>, override val firstLine: Int, override val lastLine: Int) : Block

    enum class ListKind { BULLET, ORDERED, TASK }

    data class ListItem(
        val kind: ListKind,
        /** Nesting level derived from leading indentation (2 spaces or 1 tab per level). */
        val indent: Int,
        val marker: String,
        val number: Int?,
        val checked: Boolean,
        val text: String,
        val inlines: List<Inline>,
        override val firstLine: Int,
    ) : Block {
        override val lastLine get() = firstLine
    }

    data class Quote(val text: String, val inlines: List<Inline>, override val firstLine: Int, override val lastLine: Int) : Block
    data class Code(val language: String, val code: String, override val firstLine: Int, override val lastLine: Int) : Block
    data class Divider(override val firstLine: Int) : Block {
        override val lastLine get() = firstLine
    }

    enum class Align { LEFT, CENTER, RIGHT, NONE }

    data class Table(
        val header: List<String>,
        val alignments: List<Align>,
        val rows: List<List<String>>,
        override val firstLine: Int,
        override val lastLine: Int,
    ) : Block
}

object MarkdownParser {

    private val headingRe = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val taskRe = Regex("""^(\s*)([-*+])\s+\[([ xX])]\s?(.*)$""")
    private val bulletRe = Regex("""^(\s*)([-*+])\s+(.*)$""")
    private val orderedRe = Regex("""^(\s*)(\d{1,9})([.)])\s+(.*)$""")
    private val dividerRe = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val fenceRe = Regex("""^\s{0,3}(```+|~~~+)\s*([\w+#.-]*)\s*$""")
    private val tableSepRe = Regex("""^\s*\|?\s*:?-{1,}:?\s*(\|\s*:?-{1,}:?\s*)*\|?\s*$""")

    fun parse(markdown: String): List<Block> {
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = ArrayList<Block>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }

            val fence = fenceRe.matchEntire(line)
            if (fence != null) {
                val marker = fence.groupValues[1]
                val start = i
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith(marker)) {
                    if (i > start + 1) code.append('\n')
                    code.append(lines[i]); i++
                }
                val end = if (i < lines.size) i else lines.size - 1 // an unterminated fence runs to the end
                blocks += Block.Code(fence.groupValues[2], code.toString(), start, end)
                i = end + 1
                continue
            }
            val heading = headingRe.matchEntire(line)
            if (heading != null) {
                val text = heading.groupValues[2]
                blocks += Block.Heading(heading.groupValues[1].length, text, InlineParser.parse(text), i)
                i++; continue
            }
            if (dividerRe.matches(line)) { blocks += Block.Divider(i); i++; continue }
            val task = taskRe.matchEntire(line)
            if (task != null) {
                val text = task.groupValues[4]
                blocks += Block.ListItem(Block.ListKind.TASK, indentOf(task.groupValues[1]), task.groupValues[2], null,
                    task.groupValues[3] != " ", text, InlineParser.parse(text), i)
                i++; continue
            }
            val bullet = bulletRe.matchEntire(line)
            if (bullet != null) {
                val text = bullet.groupValues[3]
                blocks += Block.ListItem(Block.ListKind.BULLET, indentOf(bullet.groupValues[1]), bullet.groupValues[2], null,
                    false, text, InlineParser.parse(text), i)
                i++; continue
            }
            val ordered = orderedRe.matchEntire(line)
            if (ordered != null) {
                val text = ordered.groupValues[4]
                blocks += Block.ListItem(Block.ListKind.ORDERED, indentOf(ordered.groupValues[1]), ordered.groupValues[3],
                    ordered.groupValues[2].toIntOrNull(), false, text, InlineParser.parse(text), i)
                i++; continue
            }
            if (line.trimStart().startsWith(">")) {
                val start = i
                val parts = ArrayList<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    parts += lines[i].trimStart().removePrefix(">").removePrefix(" "); i++
                }
                val text = parts.joinToString("\n")
                blocks += Block.Quote(text, InlineParser.parse(text), start, i - 1)
                continue
            }
            if (isTableStart(lines, i)) {
                val header = splitRow(line)
                val aligns = splitRow(lines[i + 1]).map { cell ->
                    val c = cell.trim()
                    when {
                        c.startsWith(":") && c.endsWith(":") -> Block.Align.CENTER
                        c.endsWith(":") -> Block.Align.RIGHT
                        c.startsWith(":") -> Block.Align.LEFT
                        else -> Block.Align.NONE
                    }
                }
                val start = i
                i += 2
                val rows = ArrayList<List<String>>()
                while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                    val r = splitRow(lines[i])
                    rows += List(header.size) { idx -> r.getOrElse(idx) { "" } }
                    i++
                }
                blocks += Block.Table(header, aligns, rows, start, i - 1)
                continue
            }
            // Paragraph: consume until a blank line or the start of another block.
            val start = i
            val parts = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank() && (i == start || !startsBlock(lines, i))) {
                parts += lines[i]; i++
            }
            val text = parts.joinToString("\n")
            blocks += Block.Paragraph(text, InlineParser.parse(text), start, i - 1)
        }
        return blocks
    }

    private fun startsBlock(lines: List<String>, i: Int): Boolean {
        val l = lines[i]
        return headingRe.matches(l) || fenceRe.matches(l) || dividerRe.matches(l) || taskRe.matches(l) ||
            bulletRe.matches(l) || orderedRe.matches(l) || l.trimStart().startsWith(">") ||
            isTableStart(lines, i)
    }

    private fun isTableStart(lines: List<String>, i: Int): Boolean =
        lines[i].contains('|') && i + 1 < lines.size && lines[i + 1].contains('-') && tableSepRe.matches(lines[i + 1])

    private fun indentOf(ws: String): Int {
        var cols = 0
        for (c in ws) cols += if (c == '\t') 2 else 1
        return cols / 2
    }

    fun splitRow(line: String): List<String> {
        var t = line.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
        val cells = ArrayList<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length && t[i + 1] == '|') { cur.append('|'); i += 2; continue }
            if (c == '|') { cells += cur.toString().trim(); cur.clear() } else cur.append(c)
            i++
        }
        cells += cur.toString().trim()
        return cells
    }

    /** Markup-free text for search indexes, snippets and AI context. */
    fun toPlainText(markdown: String): String = parse(markdown).joinToString("\n") { b ->
        when (b) {
            is Block.Heading -> InlineParser.plainText(b.inlines)
            is Block.Paragraph -> InlineParser.plainText(b.inlines)
            is Block.ListItem -> InlineParser.plainText(b.inlines)
            is Block.Quote -> InlineParser.plainText(b.inlines)
            is Block.Code -> b.code
            is Block.Divider -> ""
            is Block.Table -> (listOf(b.header) + b.rows).joinToString("\n") { it.joinToString(" ") }
        }
    }.lines().filter { it.isNotBlank() }.joinToString("\n")

    /** Toggle the checkbox on the given source line. Returns the input unchanged if that line is not a task. */
    fun toggleTask(markdown: String, line: Int): String {
        val lines = markdown.split('\n').toMutableList()
        if (line !in lines.indices) return markdown
        val m = taskRe.matchEntire(lines[line]) ?: return markdown
        val checked = m.groupValues[3] != " "
        val idx = lines[line].indexOf('[') + 1
        lines[line] = lines[line].substring(0, idx) + (if (checked) " " else "x") + lines[line].substring(idx + 1)
        return lines.joinToString("\n")
    }

    /** All targets referenced by [[wiki links]] and in-app links (margin://note/<id>) in a body. */
    fun linkTargets(markdown: String): Set<String> {
        val out = LinkedHashSet<String>()
        fun walk(nodes: List<Inline>) {
            nodes.forEach { n ->
                when (n) {
                    is Inline.WikiLink -> out += n.target
                    is Inline.Link -> { if (n.url.startsWith(NOTE_URI_PREFIX)) out += n.url; walk(n.children) }
                    is Inline.Bold -> walk(n.children)
                    is Inline.Italic -> walk(n.children)
                    is Inline.Strike -> walk(n.children)
                    is Inline.Underline -> walk(n.children)
                    else -> Unit
                }
            }
        }
        parse(markdown).forEach { b ->
            when (b) {
                is Block.Heading -> walk(b.inlines)
                is Block.Paragraph -> walk(b.inlines)
                is Block.ListItem -> walk(b.inlines)
                is Block.Quote -> walk(b.inlines)
                else -> Unit
            }
        }
        return out
    }

    fun webLinks(markdown: String): List<String> {
        val out = ArrayList<String>()
        fun walk(nodes: List<Inline>) {
            nodes.forEach { n ->
                when (n) {
                    is Inline.AutoLink -> out += n.url
                    is Inline.Link -> { if (n.url.startsWith("http")) out += n.url; walk(n.children) }
                    is Inline.Bold -> walk(n.children)
                    is Inline.Italic -> walk(n.children)
                    is Inline.Strike -> walk(n.children)
                    is Inline.Underline -> walk(n.children)
                    else -> Unit
                }
            }
        }
        parse(markdown).forEach { b ->
            when (b) {
                is Block.Heading -> walk(b.inlines)
                is Block.Paragraph -> walk(b.inlines)
                is Block.ListItem -> walk(b.inlines)
                is Block.Quote -> walk(b.inlines)
                else -> Unit
            }
        }
        return out
    }

    const val NOTE_URI_PREFIX = "margin://note/"
}
