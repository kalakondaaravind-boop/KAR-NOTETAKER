package app.margin.domain.io

/** CSV/TSV → a note with a Markdown table (first row = header). RFC 4180 quoting; delimiter auto-detected. */
object CsvImporter {
    fun rows(text: String, delimiter: Char? = null): List<List<String>> {
        val d = delimiter ?: text.lineSequence().firstOrNull().orEmpty().let { first ->
            listOf(',', ';', '\t').maxByOrNull { c -> first.count { it == c } } ?: ','
        }
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
                !quoted && c == d -> { row += cell.toString(); cell.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString(); cell.clear(); rows += row; row = ArrayList()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
        return rows.filter { r -> r.any { it.isNotBlank() } }.take(ImportLimits.MAX_TABLE_ROWS)
    }

    fun parse(fileName: String, text: String): ImportCandidate {
        val rows = rows(text)
        if (rows.isEmpty()) return ImportCandidate(fileName, fileName.substringBeforeLast('.'), "")
        val cols = rows.maxOf { it.size }
        fun esc(s: String) = s.replace("|", "\\|").replace("\n", " ").trim().ifEmpty { " " }
        fun line(r: List<String>) = "| " + (0 until cols).joinToString(" | ") { esc(r.getOrElse(it) { "" }) } + " |"
        val md = buildString {
            appendLine(line(rows[0]))
            appendLine("|" + " --- |".repeat(cols))
            rows.drop(1).forEach { appendLine(line(it)) }
        }
        return ImportCandidate(sourceName = fileName, title = fileName.substringBeforeLast('.'), body = md)
    }
}

/**
 * RTF → Markdown: text, paragraphs, bold / italic / underline / strike, \uN unicode and \'hh escapes.
 * Skips font/colour tables, pictures and other destinations.
 */
object RtfImporter {
    private val skipDest = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer", "footnote",
        "headerl", "headerr", "footerl", "footerr", "listtable", "listoverridetable", "generator", "themedata", "datastore", "latentstyles", "xmlnstbl", "rsidtbl")

    private data class State(var b: Boolean = false, var i: Boolean = false, var u: Boolean = false, var s: Boolean = false, var skip: Boolean = false, var uc: Int = 1)

    fun toMarkdown(rtf: String): String {
        if (!rtf.trimStart().startsWith("{\\rtf")) throw ImportFailure(FailReason.MALFORMED, "not an RTF document")
        val out = StringBuilder()
        val stack = ArrayDeque<State>()
        var st = State()
        var run = StringBuilder()
        var runFmt = State()
        fun flush() {
            if (run.isEmpty()) return
            var t = run.toString()
            val core = t.trim()
            if (core.isNotEmpty()) {
                val esc = core.replace(Regex("""([\\`*_\[\]~<>#|])"""), "\\\\$1")
                var w = esc
                if (runFmt.s) w = "~~$w~~"; if (runFmt.u) w = "<u>$w</u>"; if (runFmt.i) w = "*$w*"; if (runFmt.b) w = "**$w**"
                t = t.takeWhile { it == ' ' } + w + t.takeLastWhile { it == ' ' }
            }
            out.append(t); run = StringBuilder()
        }
        fun emit(text: String) {
            if (st.skip) return
            if (st.b != runFmt.b || st.i != runFmt.i || st.u != runFmt.u || st.s != runFmt.s) { flush(); runFmt = st.copy() }
            run.append(text)
        }
        var i = 0
        var skipChars = 0
        while (i < rtf.length) {
            val c = rtf[i]
            when (c) {
                '{' -> { stack.addLast(st.copy()); i++ }
                '}' -> { flush(); st = stack.removeLastOrNull() ?: State(); runFmt = st.copy(); i++ }
                '\\' -> {
                    if (i + 1 >= rtf.length) break
                    val n = rtf[i + 1]
                    when {
                        n == '\\' || n == '{' || n == '}' -> { if (skipChars > 0) skipChars-- else emit(n.toString()); i += 2 }
                        n == '\'' -> {
                            val hex = rtf.substring(i + 2, minOf(i + 4, rtf.length))
                            if (skipChars > 0) skipChars-- else hex.toIntOrNull(16)?.let { emit(String(byteArrayOf(it.toByte()), Charsets.ISO_8859_1).let { s -> if (it in 0x80..0x9F) cp1252(it) else s }) }
                            i += 4
                        }
                        n == '*' -> { st.skip = true; i += 2 }
                        n == '~' -> { emit("\u00A0"); i += 2 }
                        n == '-' || n == '_' -> { if (n == '_') emit("-"); i += 2 }
                        n.isLetter() -> {
                            var j = i + 1
                            while (j < rtf.length && rtf[j].isLetter()) j++
                            val word = rtf.substring(i + 1, j)
                            var k = j
                            if (k < rtf.length && (rtf[k] == '-' || rtf[k].isDigit())) { k++; while (k < rtf.length && rtf[k].isDigit()) k++ }
                            val param = rtf.substring(j, k).toIntOrNull()
                            if (k < rtf.length && rtf[k] == ' ') k++
                            i = k
                            when (word) {
                                "par", "sect" -> { flush(); if (!st.skip) out.append("\n\n") }
                                "line" -> emit(" ")
                                "tab" -> emit("    ")
                                "b" -> st.b = param != 0
                                "i" -> st.i = param != 0
                                "ul" -> st.u = param != 0
                                "ulnone" -> st.u = false
                                "strike" -> st.s = param != 0
                                "plain" -> { st.b = false; st.i = false; st.u = false; st.s = false }
                                "uc" -> st.uc = (param ?: 1).coerceIn(0, 4)
                                "u" -> { param?.let { emit((if (it < 0) it + 65536 else it).toChar().toString()) }; skipChars = st.uc }
                                "bullet" -> emit("•"); "endash" -> emit("–"); "emdash" -> emit("—")
                                "lquote" -> emit("‘"); "rquote" -> emit("’"); "ldblquote" -> emit("“"); "rdblquote" -> emit("”")
                                else -> if (word in skipDest) st.skip = true
                            }
                        }
                        else -> i += 2
                    }
                }
                '\r', '\n' -> i++
                else -> { if (skipChars > 0) skipChars-- else emit(c.toString()); i++ }
            }
        }
        flush()
        return out.toString().lines().joinToString("\n") { it.trimEnd() }.replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
    }

    private fun cp1252(b: Int): String = String(byteArrayOf(b.toByte()), charset("windows-1252"))

    fun parse(fileName: String, rtf: String) = ImportCandidate(sourceName = fileName, title = fileName.substringBeforeLast('.'), body = toMarkdown(rtf))
}
