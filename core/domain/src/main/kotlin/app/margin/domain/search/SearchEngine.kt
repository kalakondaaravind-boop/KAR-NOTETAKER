package app.margin.domain.search

import app.margin.domain.organization.Tags
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.ln

object TextNormalizer {
    private val marks = Regex("""\p{Mn}+""")
    private val wordRe = Regex("""[\p{L}\p{N}]+""")

    /** Lower-case, accent-insensitive form. Length may differ from the input. */
    fun fold(s: String): String = marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)

    fun words(s: String): List<String> = wordRe.findAll(fold(s)).map { it.value }.toList()

    /**
     * Words with their character ranges in the *original* string (for snippet highlighting).
     * Each original word is folded individually so ranges stay accurate.
     */
    fun wordsWithRanges(s: String): List<Pair<String, IntRange>> =
        wordRe.findAll(s).map { m -> fold(m.value) to m.range }.toList()
}

/** Everything the matcher needs about a note. Built by the data layer from canonical tables. */
data class SearchDocument(
    val id: String,
    val title: String,
    val plainBody: String,
    val tagNames: List<String>,
    val folderPath: List<String>,
    val attachmentText: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val pinned: Boolean,
    val favorite: Boolean,
    val archived: Boolean,
    val trashed: Boolean,
    val attachmentCount: Int,
    val openTaskCount: Int,
    val doneTaskCount: Int,
    val linkCount: Int,
)

data class Highlight(val text: String, val ranges: List<IntRange>)

data class SearchHit(
    val id: String,
    val score: Double,
    val title: Highlight,
    val snippet: Highlight,
    /** True when the hit only matched through typo-tolerant (fuzzy) matching. */
    val approximate: Boolean,
)

/**
 * Deterministic matcher + ranker. The FTS index narrows candidates; this class decides the
 * final, exact semantics so behaviour is identical regardless of the SQLite build.
 */
class SearchEngine(private val zone: ZoneId = ZoneId.systemDefault()) {

    private class Prepared(val doc: SearchDocument) {
        val title = TextNormalizer.words(doc.title)
        val body = TextNormalizer.words(doc.plainBody)
        val tags = doc.tagNames.map { Tags.key(it) }
        val folders = doc.folderPath.map { TextNormalizer.fold(it.trim()) }
        val attachments = TextNormalizer.words(doc.attachmentText)
        val all = title + body + attachments + tags.flatMap { TextNormalizer.words(it) } + folders.flatMap { TextNormalizer.words(it) }
    }

    private enum class MatchKind { NONE, EXACT, FUZZY }

    fun matches(query: ParsedQuery, doc: SearchDocument): Boolean = evaluate(query, Prepared(doc)) != MatchKind.NONE

    /**
     * Returns ranked hits. By default trashed/archived notes are excluded unless the query asks
     * for them explicitly (is:archived / is:trashed) — a deleted note never shows up in ordinary search.
     */
    fun search(query: ParsedQuery, docs: Sequence<SearchDocument>, now: Instant, limit: Int = 200): List<SearchHit> {
        if (query.isEmpty) return emptyList()
        val includeHidden = query.mentionsTrashOrArchive
        val hits = ArrayList<SearchHit>()
        for (d in docs) {
            if (!includeHidden && (d.trashed || d.archived)) continue
            val p = Prepared(d)
            val kind = evaluate(query, p)
            if (kind == MatchKind.NONE) continue
            hits += SearchHit(d.id, score(query, p, now), highlightTitle(query, d.title), snippet(query, d), kind == MatchKind.FUZZY)
        }
        return hits.sortedWith(compareByDescending<SearchHit> { it.score }.thenBy { it.id }).take(limit)
    }

    private fun evaluate(q: ParsedQuery, p: Prepared): MatchKind = q.root?.let { eval(it, p) } ?: MatchKind.NONE

    private fun eval(n: QueryNode, p: Prepared): MatchKind = when (n) {
        is QueryNode.And -> {
            var fuzzy = false
            var ok = true
            for (c in n.children) {
                when (eval(c, p)) { MatchKind.NONE -> { ok = false; break }; MatchKind.FUZZY -> fuzzy = true; else -> Unit }
            }
            if (!ok) MatchKind.NONE else if (fuzzy) MatchKind.FUZZY else MatchKind.EXACT
        }
        is QueryNode.Or -> n.children.map { eval(it, p) }.let { r ->
            when { MatchKind.EXACT in r -> MatchKind.EXACT; MatchKind.FUZZY in r -> MatchKind.FUZZY; else -> MatchKind.NONE }
        }
        is QueryNode.Not -> if (eval(n.child, p) == MatchKind.NONE) MatchKind.EXACT else MatchKind.NONE
        is QueryNode.Term -> term(n, p)
        is QueryNode.Phrase -> if (phrase(n, p)) MatchKind.EXACT else MatchKind.NONE
        is QueryNode.Flag -> if (flag(n.flag, p.doc)) MatchKind.EXACT else MatchKind.NONE
        is QueryNode.DateRange -> if (dateIn(n, p.doc)) MatchKind.EXACT else MatchKind.NONE
    }

    private fun term(t: QueryNode.Term, p: Prepared): MatchKind {
        val q = TextNormalizer.fold(t.text)
        return when (t.field) {
            Field.TAG -> if (p.tags.any { Tags.matchesHierarchy(Tags.key(t.text), it) }) MatchKind.EXACT else MatchKind.NONE
            Field.FOLDER -> if (p.folders.any { it == q || it.startsWith(q) }) MatchKind.EXACT else MatchKind.NONE
            else -> {
                val words = when (t.field) { Field.TITLE -> p.title; Field.BODY -> p.body; else -> p.all }
                when {
                    words.any { it.startsWith(q) } -> MatchKind.EXACT
                    t.fuzzy && words.any { Fuzzy.close(q, it) } -> MatchKind.FUZZY
                    else -> MatchKind.NONE
                }
            }
        }
    }

    private fun phrase(ph: QueryNode.Phrase, p: Prepared): Boolean {
        val sources = when (ph.field) {
            Field.TITLE -> listOf(p.title)
            Field.BODY -> listOf(p.body)
            else -> listOf(p.title, p.body, p.attachments)
        }
        return sources.any { containsSequence(it, ph.words) }
    }

    private fun containsSequence(words: List<String>, seq: List<String>): Boolean {
        if (seq.isEmpty() || words.size < seq.size) return false
        outer@ for (i in 0..words.size - seq.size) {
            for (j in seq.indices) {
                val w = words[i + j]
                // last word of a phrase is a prefix match (typing-friendly), others exact
                val ok = if (j == seq.lastIndex) w.startsWith(seq[j]) else w == seq[j]
                if (!ok) continue@outer
            }
            return true
        }
        return false
    }

    private fun flag(f: SearchFlag, d: SearchDocument) = when (f) {
        SearchFlag.PINNED -> d.pinned
        SearchFlag.FAVORITE -> d.favorite
        SearchFlag.ARCHIVED -> d.archived
        SearchFlag.TRASHED -> d.trashed
        SearchFlag.HAS_ATTACHMENT -> d.attachmentCount > 0
        SearchFlag.HAS_TASK -> d.openTaskCount + d.doneTaskCount > 0
        SearchFlag.HAS_OPEN_TASK -> d.openTaskCount > 0
        SearchFlag.HAS_LINK -> d.linkCount > 0
    }

    private fun dateIn(r: QueryNode.DateRange, d: SearchDocument): Boolean {
        val instant = if (r.target == DateTarget.CREATED) d.createdAt else d.updatedAt
        val date: LocalDate = instant.atZone(zone).toLocalDate()
        if (r.from != null && date < r.from) return false
        if (r.toInclusive != null && date > r.toInclusive) return false
        return true
    }

    /** BM25-flavoured field-weighted score with a small recency and pin boost. */
    private fun score(q: ParsedQuery, p: Prepared, now: Instant): Double {
        var s = 0.0
        for (t in q.positiveTerms) {
            val words = when (t) { is QueryNode.Term -> listOf(TextNormalizer.fold(t.text)); is QueryNode.Phrase -> t.words; else -> emptyList() }
            for (w in words) {
                val titleTf = p.title.count { it.startsWith(w) }
                val bodyTf = p.body.count { it.startsWith(w) }
                val tagHit = p.tags.any { it.startsWith(w) || it.split('/', ' ').any { x -> x.startsWith(w) } }
                val exactTitle = p.title.any { it == w }
                s += 3.0 * sat(titleTf) + (if (exactTitle) 1.0 else 0.0) + sat(bodyTf) / (1 + ln(1.0 + p.body.size / 500.0)) +
                    (if (tagHit) 2.5 else 0.0) + 0.5 * sat(p.attachments.count { it.startsWith(w) })
            }
            if (t is QueryNode.Phrase) s += 2.0
        }
        if (q.positiveTerms.isEmpty()) s = 1.0
        val ageDays = (now.toEpochMilli() - p.doc.updatedAt.toEpochMilli()).coerceAtLeast(0) / 86_400_000.0
        s += 0.5 / (1.0 + ageDays / 30.0)
        if (p.doc.pinned) s += 0.3
        return s
    }

    private fun sat(tf: Int) = if (tf == 0) 0.0 else tf * 2.2 / (tf + 1.2)

    private fun termPredicates(q: ParsedQuery): List<List<(String) -> Boolean>> = q.positiveTerms.mapNotNull { t ->
        when (t) {
            is QueryNode.Term -> if (t.field == Field.TAG || t.field == Field.FOLDER) null else {
                val f = TextNormalizer.fold(t.text)
                listOf<(String) -> Boolean>({ w -> w.startsWith(f) || (t.fuzzy && Fuzzy.close(f, w)) })
            }
            is QueryNode.Phrase -> t.words.map { pw -> { w: String -> w.startsWith(pw) } }
            else -> null
        }
    }

    fun highlightTitle(q: ParsedQuery, title: String): Highlight {
        val preds = termPredicates(q).flatten()
        val ranges = TextNormalizer.wordsWithRanges(title).filter { (w, _) -> preds.any { it(w) } }.map { it.second }
        return Highlight(title, ranges)
    }

    /** A window of about [width] characters around the first body match, with match ranges. */
    fun snippet(q: ParsedQuery, d: SearchDocument, width: Int = 160): Highlight {
        val preds = termPredicates(q).flatten()
        val body = d.plainBody.ifBlank { d.attachmentText }
        val words = TextNormalizer.wordsWithRanges(body)
        val matched = words.filter { (w, _) -> preds.any { it(w) } }.map { it.second }
        if (body.isEmpty()) return Highlight("", emptyList())
        val anchor = matched.firstOrNull()?.first ?: 0
        var start = (anchor - width / 3).coerceAtLeast(0)
        // Start at a word boundary.
        if (start > 0) { val sp = body.lastIndexOf(' ', start); start = if (sp >= 0 && anchor - sp < width) sp + 1 else start }
        val end = (start + width).coerceAtMost(body.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < body.length) "…" else ""
        val text = prefix + body.substring(start, end).replace('\n', ' ') + suffix
        val shift = prefix.length - start
        val ranges = matched.filter { it.first >= start && it.last < end }.map { (it.first + shift)..(it.last + shift) }
        return Highlight(text, ranges)
    }
}

object Fuzzy {
    /** Allowed edits scale with word length: <4 none, 4–7 one, 8+ two. Compared against word prefixes too. */
    fun close(query: String, word: String): Boolean {
        val maxEd = when { query.length < 4 -> 0; query.length < 8 -> 1; else -> 2 }
        if (maxEd == 0) return false
        if (kotlin.math.abs(word.length - query.length) > maxEd && word.length < query.length) return false
        val candidate = if (word.length > query.length + maxEd) word.substring(0, query.length) else word
        return distance(query, candidate, maxEd) <= maxEd
    }

    /** Optimal-string-alignment (Damerau) distance with early exit. */
    fun distance(a: String, b: String, cap: Int = Int.MAX_VALUE): Int {
        if (a == b) return 0
        val n = a.length
        val m = b.length
        if (kotlin.math.abs(n - m) > cap) return cap + 1
        var prev2 = IntArray(m + 1)
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > cap) return cap + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }
}

/**
 * Builds the SQLite FTS4 MATCH expression used only to pre-select candidates. It uses the
 * standard query syntax subset (prefix terms, quoted phrases, OR) that every Android SQLite
 * build supports; exclusions and filters are applied afterwards by [SearchEngine].
 * Returns null when the query has no positive text terms (filters only / pure exclusion),
 * in which case the caller scans by structured filter instead.
 */
object FtsQueryBuilder {
    fun candidates(q: ParsedQuery): String? {
        val root = q.root ?: return null
        // Only safe when every possible match must contain at least one indexable positive term.
        if (!requiresIndexable(root)) return null
        val parts = q.positiveTerms.mapNotNull { t ->
            when {
                t is QueryNode.Term && indexable(t) -> TextNormalizer.words(t.text).joinToString(" ") { "$it*" }.ifEmpty { null }
                t is QueryNode.Phrase -> "\"" + t.words.joinToString(" ") + "\""
                else -> null
            }
        }
        return parts.distinct().joinToString(" OR ").ifEmpty { null }
    }

    private fun indexable(t: QueryNode.Term) = !t.fuzzy && t.field != Field.TAG && t.field != Field.FOLDER

    private fun requiresIndexable(n: QueryNode): Boolean = when (n) {
        is QueryNode.And -> n.children.any { requiresIndexable(it) }
        is QueryNode.Or -> n.children.all { requiresIndexable(it) }
        is QueryNode.Not -> false
        is QueryNode.Term -> indexable(n)
        is QueryNode.Phrase -> true
        else -> false
    }
}
