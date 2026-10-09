package app.margin.domain.editor

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Immutable text + selection. `selStart <= selEnd` is not required (reverse selections are kept). */
data class TextValue(val text: String, val selStart: Int = text.length, val selEnd: Int = selStart) {
    val min get() = minOf(selStart, selEnd).coerceIn(0, text.length)
    val max get() = maxOf(selStart, selEnd).coerceIn(0, text.length)
    val hasSelection get() = selStart != selEnd
    val selectedText get() = text.substring(min, max)
}

enum class InlineStyle(val open: String, val close: String) {
    BOLD("**", "**"), ITALIC("*", "*"), STRIKE("~~", "~~"), UNDERLINE("<u>", "</u>"), CODE("`", "`")
}

enum class LineStyle { HEADING1, HEADING2, HEADING3, BULLET, NUMBERED, CHECKLIST, QUOTE, PLAIN }

/**
 * Pure formatting operations. Each takes the current value and returns a new value; none of
 * them touch text outside the affected selection or lines (protects unrelated content).
 */
object EditorCommands {

    private val linePrefixRe = Regex("""^(\s*)(#{1,6}\s+|[-*+]\s+\[[ xX]]\s+|[-*+]\s+|\d{1,9}[.)]\s+|>\s?)?""")

    fun toggleInline(v: TextValue, style: InlineStyle): TextValue {
        val s = v.min
        val e = v.max
        val t = v.text
        // Already wrapped immediately around the selection → unwrap.
        if (s >= style.open.length && e + style.close.length <= t.length &&
            t.regionMatches(s - style.open.length, style.open, 0, style.open.length) &&
            t.regionMatches(e, style.close, 0, style.close.length)
        ) {
            val nt = t.substring(0, s - style.open.length) + t.substring(s, e) + t.substring(e + style.close.length)
            return TextValue(nt, s - style.open.length, e - style.open.length)
        }
        // Selection itself includes the markers → unwrap.
        val sel = t.substring(s, e)
        if (sel.length >= style.open.length + style.close.length && sel.startsWith(style.open) && sel.endsWith(style.close)) {
            val inner = sel.substring(style.open.length, sel.length - style.close.length)
            return TextValue(t.substring(0, s) + inner + t.substring(e), s, s + inner.length)
        }
        if (!v.hasSelection) {
            // Insert an empty pair and place the cursor between the markers.
            val nt = t.substring(0, s) + style.open + style.close + t.substring(s)
            return TextValue(nt, s + style.open.length)
        }
        // Keep surrounding whitespace outside the markers ("**word** " not "**word **").
        val lead = sel.length - sel.trimStart().length
        val trail = sel.length - sel.trimEnd().length
        if (lead == sel.length) return v
        val core = sel.trim()
        val nt = t.substring(0, s + lead) + style.open + core + style.close + t.substring(e - trail)
        val ns = s + lead + style.open.length
        return TextValue(nt, ns, ns + core.length)
    }

    /** Applies a block style to every line touched by the selection; applying the same style again removes it. */
    fun setLineStyle(v: TextValue, style: LineStyle): TextValue {
        val t = v.text
        val firstLineStart = if (v.min == 0) 0 else t.lastIndexOf('\n', v.min - 1) + 1
        val lastLineEnd = t.indexOf('\n', v.max).let { if (it < 0) t.length else it }
        val lines = t.substring(firstLineStart, lastLineEnd).split('\n')
        val allHave = lines.all { currentStyle(it) == style }
        var number = 1
        val newLines = lines.map { line ->
            val m = linePrefixRe.find(line)!!
            val indent = m.groupValues[1]
            val content = line.substring(m.range.last + 1)
            val prefix = if (allHave || style == LineStyle.PLAIN) "" else when (style) {
                LineStyle.HEADING1 -> "# "
                LineStyle.HEADING2 -> "## "
                LineStyle.HEADING3 -> "### "
                LineStyle.BULLET -> "- "
                LineStyle.NUMBERED -> "${number++}. "
                LineStyle.CHECKLIST -> "- [ ] "
                LineStyle.QUOTE -> "> "
                LineStyle.PLAIN -> ""
            }
            val keepIndent = style in listOf(LineStyle.BULLET, LineStyle.NUMBERED, LineStyle.CHECKLIST)
            (if (keepIndent) indent else "") + prefix + content
        }
        val replaced = newLines.joinToString("\n")
        val nt = t.substring(0, firstLineStart) + replaced + t.substring(lastLineEnd)
        val delta = replaced.length - (lastLineEnd - firstLineStart)
        return if (!v.hasSelection) {
            // Keep the caret at the same position relative to the end of the line.
            val caret = (v.selStart + (if (lines.size == 1) delta else 0)).coerceIn(firstLineStart, firstLineStart + replaced.length)
            TextValue(nt, caret)
        } else TextValue(nt, firstLineStart, firstLineStart + replaced.length)
    }

    fun currentStyle(line: String): LineStyle {
        val l = line.trimStart()
        return when {
            l.startsWith("### ") -> LineStyle.HEADING3
            l.startsWith("## ") -> LineStyle.HEADING2
            l.startsWith("# ") -> LineStyle.HEADING1
            Regex("""^[-*+]\s+\[[ xX]]\s""").containsMatchIn(l) -> LineStyle.CHECKLIST
            Regex("""^[-*+]\s""").containsMatchIn(l) -> LineStyle.BULLET
            Regex("""^\d{1,9}[.)]\s""").containsMatchIn(l) -> LineStyle.NUMBERED
            l.startsWith(">") -> LineStyle.QUOTE
            else -> LineStyle.PLAIN
        }
    }

    /** Indent (or outdent) all selected list lines by two spaces. */
    fun indent(v: TextValue, outdent: Boolean = false): TextValue {
        val t = v.text
        val firstLineStart = if (v.min == 0) 0 else t.lastIndexOf('\n', v.min - 1) + 1
        val lastLineEnd = t.indexOf('\n', v.max).let { if (it < 0) t.length else it }
        val lines = t.substring(firstLineStart, lastLineEnd).split('\n')
        var firstDelta = 0
        val newLines = lines.mapIndexed { i, line ->
            val nl = if (outdent) {
                when {
                    line.startsWith("  ") -> line.substring(2)
                    line.startsWith("\t") || line.startsWith(" ") -> line.substring(1)
                    else -> line
                }
            } else "  $line"
            if (i == 0) firstDelta = nl.length - line.length
            nl
        }
        val replaced = newLines.joinToString("\n")
        val nt = t.substring(0, firstLineStart) + replaced + t.substring(lastLineEnd)
        val totalDelta = replaced.length - (lastLineEnd - firstLineStart)
        return TextValue(nt, (v.selStart + firstDelta).coerceAtLeast(firstLineStart), (v.selEnd + totalDelta).coerceAtLeast(firstLineStart))
    }

    fun insertBlock(v: TextValue, block: String): TextValue {
        val t = v.text
        val s = v.min
        val needsLeadingNl = s > 0 && t[s - 1] != '\n'
        val needsTrailingNl = v.max < t.length && t[v.max] != '\n'
        val insert = (if (needsLeadingNl) "\n" else "") + block + (if (needsTrailingNl) "\n" else "")
        val nt = t.substring(0, s) + insert + t.substring(v.max)
        return TextValue(nt, s + insert.length - (if (needsTrailingNl) 1 else 0))
    }

    fun insertText(v: TextValue, s: String): TextValue =
        TextValue(v.text.substring(0, v.min) + s + v.text.substring(v.max), v.min + s.length)

    fun insertDivider(v: TextValue) = insertBlock(v, "---\n")

    fun insertCodeBlock(v: TextValue): TextValue {
        val inner = v.selectedText
        val r = insertBlock(TextValue(v.text, v.min, v.max), "```\n$inner\n```")
        // Caret inside the block, after the selected content.
        val caret = r.text.indexOf("```\n$inner", (v.min - 1).coerceAtLeast(0)) + 4 + inner.length
        return r.copy(selStart = caret, selEnd = caret)
    }

    fun insertTable(v: TextValue, columns: Int = 3, rows: Int = 2): TextValue {
        val c = columns.coerceIn(1, 8)
        val header = (1..c).joinToString(" | ", "| ", " |") { "Column $it" }
        val sep = (1..c).joinToString(" | ", "| ", " |") { "---" }
        val row = (1..c).joinToString(" | ", "| ", " |") { " " }
        return insertBlock(v, (listOf(header, sep) + List(rows.coerceIn(1, 20)) { row }).joinToString("\n"))
    }

    fun insertLink(v: TextValue, url: String, label: String? = null): TextValue {
        val text = label?.takeIf { it.isNotBlank() } ?: v.selectedText.ifBlank { url }
        val md = "[$text]($url)"
        return TextValue(v.text.substring(0, v.min) + md + v.text.substring(v.max), v.min + md.length)
    }

    fun insertDateTime(v: TextValue, now: LocalDateTime, pattern: String = "yyyy-MM-dd HH:mm"): TextValue =
        insertText(v, now.format(DateTimeFormatter.ofPattern(pattern)))

    private val continuationRe = Regex("""^(\s*)([-*+]\s+\[[ xX]]\s+|[-*+]\s+|(\d{1,9})([.)])\s+|>\s?)(.*)$""")

    /**
     * List continuation: called with the value before and after a text change. If the change
     * was a single newline typed at the end of a list/quote line, the marker is continued; if
     * the list item was empty, the marker is removed instead (terminates the list).
     */
    fun onTextChanged(old: TextValue, new: TextValue): TextValue {
        if (new.text.length != old.text.length + 1 || new.hasSelection) return new
        val caret = new.selStart
        if (caret <= 0 || caret > new.text.length || new.text[caret - 1] != '\n') return new
        // The inserted character must be exactly that newline.
        if (new.text.substring(0, caret - 1) + new.text.substring(caret) != old.text) return new
        val prevLineStart = new.text.lastIndexOf('\n', caret - 2) + 1
        val prevLine = new.text.substring(prevLineStart, caret - 1)
        val m = continuationRe.matchEntire(prevLine) ?: return new
        val indent = m.groupValues[1]
        val marker = m.groupValues[2]
        val content = m.groupValues[5]
        if (content.isBlank()) {
            // Empty item → end the list: drop the marker and the newline just typed.
            val nt = new.text.substring(0, prevLineStart) + new.text.substring(caret)
            return TextValue(nt, prevLineStart)
        }
        val nextMarker = when {
            marker.trimStart().startsWith(">") -> "> "
            m.groupValues[3].isNotEmpty() -> "${(m.groupValues[3].toLongOrNull() ?: 0L) + 1}${m.groupValues[4]} "
            Regex("""\[[ xX]]""").containsMatchIn(marker) -> marker.substring(0, 1) + " [ ] "
            else -> marker
        }
        val insert = indent + nextMarker
        val nt = new.text.substring(0, caret) + insert + new.text.substring(caret)
        return TextValue(nt, caret + insert.length)
    }
}
