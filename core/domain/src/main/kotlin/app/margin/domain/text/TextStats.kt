package app.margin.domain.text

import kotlin.math.ceil

data class TextStats(val words: Int, val characters: Int, val charactersNoSpaces: Int, val readingMinutes: Int) {
    companion object {
        /** Words per minute used for the reading-time estimate (a common average for silent reading). */
        const val WORDS_PER_MINUTE = 230

        private val wordRe = Regex("""[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)*""")

        fun of(markdown: String): TextStats {
            val plain = MarkdownParser.toPlainText(markdown)
            val words = wordRe.findAll(plain).count()
            return TextStats(
                words = words,
                characters = plain.length,
                charactersNoSpaces = plain.count { !it.isWhitespace() },
                readingMinutes = if (words == 0) 0 else ceil(words / WORDS_PER_MINUTE.toDouble()).toInt(),
            )
        }
    }
}

/** Find / replace within a single note. Plain (non-regex) matching; case sensitivity optional. */
object FindReplace {
    fun findAll(text: String, query: String, ignoreCase: Boolean = true, wholeWord: Boolean = false): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (true) {
            val idx = text.indexOf(query, from, ignoreCase)
            if (idx < 0) break
            val end = idx + query.length
            val ok = !wholeWord || ((idx == 0 || !text[idx - 1].isLetterOrDigit()) && (end == text.length || !text[end].isLetterOrDigit()))
            if (ok) out += idx until end
            from = idx + maxOf(1, query.length)
        }
        return out
    }

    data class Result(val text: String, val replacements: Int)

    fun replaceAll(text: String, query: String, replacement: String, ignoreCase: Boolean = true, wholeWord: Boolean = false): Result {
        val matches = findAll(text, query, ignoreCase, wholeWord)
        if (matches.isEmpty()) return Result(text, 0)
        val sb = StringBuilder()
        var last = 0
        for (r in matches) { sb.append(text, last, r.first).append(replacement); last = r.last + 1 }
        sb.append(text, last, text.length)
        return Result(sb.toString(), matches.size)
    }

    fun replaceAt(text: String, range: IntRange, replacement: String): String =
        text.substring(0, range.first) + replacement + text.substring(range.last + 1)
}
