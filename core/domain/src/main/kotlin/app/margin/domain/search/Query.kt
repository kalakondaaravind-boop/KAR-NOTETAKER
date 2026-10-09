package app.margin.domain.search

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Search syntax (shown in-app under "Search help"):
 *   word          prefix match on word starts, case- and accent-insensitive
 *   "exact words" phrase
 *   -word  -"p q" exclude
 *   a OR b        either (AND is implicit; "AND" is accepted)
 *   ( ... )       grouping
 *   title:x  body:x  tag:x  folder:x      field-restricted term (tag/folder match names, hierarchical)
 *   is:pinned is:favorite is:archived is:trashed
 *   has:attachment has:task has:link has:open-task
 *   after:2024-01-31  before:2024-02-01   (last modified, local dates)
 *   created:2024-01-01..2024-03-31        (creation date range; either end optional)
 *   ~word         fuzzy term (allows 1–2 typos)
 */
sealed interface QueryNode {
    data class And(val children: List<QueryNode>) : QueryNode
    data class Or(val children: List<QueryNode>) : QueryNode
    data class Not(val child: QueryNode) : QueryNode
    data class Term(val text: String, val field: Field = Field.ANY, val fuzzy: Boolean = false) : QueryNode
    data class Phrase(val words: List<String>, val field: Field = Field.ANY) : QueryNode
    data class Flag(val flag: SearchFlag) : QueryNode
    data class DateRange(val target: DateTarget, val from: LocalDate?, val toInclusive: LocalDate?) : QueryNode
}

enum class Field { ANY, TITLE, BODY, TAG, FOLDER }
enum class DateTarget { UPDATED, CREATED }
enum class SearchFlag { PINNED, FAVORITE, ARCHIVED, TRASHED, HAS_ATTACHMENT, HAS_TASK, HAS_OPEN_TASK, HAS_LINK }

data class ParsedQuery(val root: QueryNode?, val warnings: List<String>) {
    val isEmpty get() = root == null

    /** Positive (non-negated) text terms, used for FTS candidates, scoring and highlighting. */
    val positiveTerms: List<QueryNode> by lazy {
        val out = ArrayList<QueryNode>()
        fun walk(n: QueryNode, negated: Boolean) {
            when (n) {
                is QueryNode.And -> n.children.forEach { walk(it, negated) }
                is QueryNode.Or -> n.children.forEach { walk(it, negated) }
                is QueryNode.Not -> walk(n.child, !negated)
                is QueryNode.Term, is QueryNode.Phrase -> if (!negated) out += n
                else -> Unit
            }
        }
        root?.let { walk(it, false) }
        out
    }

    val mentionsTrashOrArchive: Boolean by lazy {
        var r = false
        fun walk(n: QueryNode) {
            when (n) {
                is QueryNode.And -> n.children.forEach(::walk)
                is QueryNode.Or -> n.children.forEach(::walk)
                is QueryNode.Not -> walk(n.child)
                is QueryNode.Flag -> if (n.flag == SearchFlag.ARCHIVED || n.flag == SearchFlag.TRASHED) r = true
                else -> Unit
            }
        }
        root?.let(::walk)
        r
    }
}

object QueryParser {

    private sealed interface Tok {
        data class Word(val text: String) : Tok
        data class Quoted(val text: String) : Tok
        data object LParen : Tok
        data object RParen : Tok
        data object Minus : Tok
    }

    fun parse(input: String): ParsedQuery {
        val warnings = ArrayList<String>()
        val toks = tokenize(input.take(MAX_QUERY_LENGTH))
        val p = Parser(toks, warnings)
        val root = p.parseOr()
        if (p.pos < toks.size) warnings += "Ignored unmatched ')'"
        return ParsedQuery(root, warnings)
    }

    private fun tokenize(s: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { out += Tok.LParen; i++ }
                c == ')' -> { out += Tok.RParen; i++ }
                c == '-' && (i + 1 < s.length && !s[i + 1].isWhitespace()) && (i == 0 || s[i - 1].isWhitespace() || s[i - 1] == '(') -> { out += Tok.Minus; i++ }
                c == '"' || c == '“' || c == '”' -> {
                    val end = s.indexOfAny(charArrayOf('"', '“', '”'), i + 1).let { if (it < 0) s.length else it }
                    out += Tok.Quoted(s.substring(i + 1, end)); i = end + 1
                }
                else -> {
                    var j = i
                    // field:"quoted value"
                    while (j < s.length && !s[j].isWhitespace() && s[j] != '(' && s[j] != ')') {
                        if (s[j] == '"' && j > i && s[j - 1] == ':') {
                            val end = s.indexOf('"', j + 1).let { if (it < 0) s.length else it }
                            j = end + 1
                            break
                        }
                        j++
                    }
                    out += Tok.Word(s.substring(i, j.coerceAtMost(s.length))); i = j
                }
            }
        }
        return out
    }

    private class Parser(val toks: List<Tok>, val warnings: MutableList<String>) {
        var pos = 0

        fun parseOr(): QueryNode? {
            val parts = ArrayList<QueryNode>()
            parseAnd()?.let { parts += it }
            while (pos < toks.size && (toks[pos] as? Tok.Word)?.text == "OR") {
                pos++
                parseAnd()?.let { parts += it }
            }
            return when (parts.size) { 0 -> null; 1 -> parts[0]; else -> QueryNode.Or(parts) }
        }

        fun parseAnd(): QueryNode? {
            val parts = ArrayList<QueryNode>()
            while (pos < toks.size) {
                val t = toks[pos]
                if (t is Tok.RParen) break
                if (t is Tok.Word && t.text == "OR") break
                if (t is Tok.Word && t.text == "AND") { pos++; continue }
                parseUnary()?.let { parts += it }
            }
            return when (parts.size) { 0 -> null; 1 -> parts[0]; else -> QueryNode.And(parts) }
        }

        fun parseUnary(): QueryNode? {
            val t = toks[pos]
            if (t is Tok.Minus || (t is Tok.Word && t.text == "NOT")) {
                pos++
                if (pos >= toks.size) return null
                return parseUnary()?.let { QueryNode.Not(it) }
            }
            return parsePrimary()
        }

        fun parsePrimary(): QueryNode? {
            val t = toks[pos++]
            return when (t) {
                is Tok.LParen -> {
                    val inner = parseOr()
                    if (pos < toks.size && toks[pos] is Tok.RParen) pos++ else warnings += "Missing ')'"
                    inner
                }
                is Tok.RParen -> null
                is Tok.Minus -> null
                is Tok.Quoted -> phrase(t.text, Field.ANY)
                is Tok.Word -> word(t.text)
            }
        }

        private fun phrase(text: String, field: Field): QueryNode? {
            val words = TextNormalizer.words(text)
            return when (words.size) {
                0 -> null
                1 -> QueryNode.Term(words[0], field)
                else -> QueryNode.Phrase(words, field)
            }
        }

        private fun word(raw: String): QueryNode? {
            val colon = raw.indexOf(':')
            if (colon > 0) {
                val key = raw.substring(0, colon).lowercase()
                val value = raw.substring(colon + 1).removeSurrounding("\"")
                when (key) {
                    "title" -> return phrase(value, Field.TITLE)
                    "body" -> return phrase(value, Field.BODY)
                    "tag" -> return value.trim().trimStart('#').takeIf { it.isNotEmpty() }?.let { QueryNode.Term(it, Field.TAG) }
                    "folder", "in", "notebook" -> return value.trim().takeIf { it.isNotEmpty() }?.let { QueryNode.Term(it, Field.FOLDER) }
                    "is" -> return when (value.lowercase()) {
                        "pinned" -> QueryNode.Flag(SearchFlag.PINNED)
                        "favorite", "favourite", "starred" -> QueryNode.Flag(SearchFlag.FAVORITE)
                        "archived" -> QueryNode.Flag(SearchFlag.ARCHIVED)
                        "trashed", "deleted" -> QueryNode.Flag(SearchFlag.TRASHED)
                        else -> { warnings += "Unknown filter is:$value"; null }
                    }
                    "has" -> return when (value.lowercase()) {
                        "attachment", "attachments", "file", "files" -> QueryNode.Flag(SearchFlag.HAS_ATTACHMENT)
                        "task", "tasks", "checklist" -> QueryNode.Flag(SearchFlag.HAS_TASK)
                        "open-task", "open-tasks", "todo" -> QueryNode.Flag(SearchFlag.HAS_OPEN_TASK)
                        "link", "links" -> QueryNode.Flag(SearchFlag.HAS_LINK)
                        else -> { warnings += "Unknown filter has:$value"; null }
                    }
                    "after" -> return date(value)?.let { QueryNode.DateRange(DateTarget.UPDATED, it.plusDays(1), null) }
                    "before" -> return date(value)?.let { QueryNode.DateRange(DateTarget.UPDATED, null, it.minusDays(1)) }
                    "on", "updated" -> return dateRange(value, DateTarget.UPDATED)
                    "created" -> return dateRange(value, DateTarget.CREATED)
                }
                // Unknown prefix (e.g. a URL "https://..."): treat as ordinary text.
            }
            val fuzzy = raw.startsWith("~") && raw.length > 1
            val words = TextNormalizer.words(if (fuzzy) raw.substring(1) else raw)
            return when (words.size) {
                0 -> null
                1 -> QueryNode.Term(words[0], Field.ANY, fuzzy)
                else -> QueryNode.Phrase(words, Field.ANY) // e.g. "e-mail" or "v2.1" → adjacent words
            }
        }

        private fun dateRange(v: String, target: DateTarget): QueryNode? {
            if (v.contains("..")) {
                val (a, b) = v.split("..", limit = 2)
                val from = if (a.isBlank()) null else date(a) ?: return null
                val to = if (b.isBlank()) null else date(b) ?: return null
                return QueryNode.DateRange(target, from, to)
            }
            val d = date(v) ?: return null
            return QueryNode.DateRange(target, d, d)
        }

        private fun date(v: String): LocalDate? = try {
            LocalDate.parse(v.trim())
        } catch (e: DateTimeParseException) {
            warnings += "Invalid date \"$v\" (use YYYY-MM-DD)"; null
        }
    }

    const val MAX_QUERY_LENGTH = 500
}
