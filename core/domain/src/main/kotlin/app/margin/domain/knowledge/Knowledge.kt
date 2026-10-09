package app.margin.domain.knowledge

import app.margin.domain.search.TextNormalizer
import app.margin.domain.text.MarkdownParser
import kotlin.math.ln
import kotlin.math.sqrt

/** Input for local relationship discovery (all computed on-device). */
data class KnowledgeDoc(val id: String, val title: String, val plainText: String, val tagKeys: Set<String>)

/** Why a note is suggested as related. Displayed to the user; never presented as an explicit link. */
enum class RelationReason { SHARED_TERMS, SHARED_TAGS }

data class Related(val id: String, val score: Double, val reasons: Set<RelationReason>, val sharedTerms: List<String>)

object RelatedNotes {
    private val stop = setOf(
        "the", "and", "for", "that", "this", "with", "are", "was", "but", "not", "you", "your", "from", "have", "has", "had",
        "they", "them", "their", "will", "would", "can", "could", "should", "what", "when", "where", "which", "who", "how",
        "about", "into", "than", "then", "there", "these", "those", "were", "been", "being", "also", "just", "more", "some",
        "any", "all", "our", "out", "its", "it's", "a", "an", "of", "to", "in", "on", "is", "it", "as", "at", "by", "or", "be",
        "we", "i", "my", "me", "do", "so", "if", "no", "yes", "up", "one", "two",
    )

    private fun terms(d: KnowledgeDoc): Map<String, Int> =
        (TextNormalizer.words(d.title) + TextNormalizer.words(d.title) + TextNormalizer.words(d.plainText))
            .filter { it.length > 2 && it !in stop && !it.all(Char::isDigit) }
            .groupingBy { it }.eachCount()

    /** TF-IDF cosine similarity plus a tag-overlap bonus. Returns at most [limit] notes above [minScore]. */
    fun find(target: KnowledgeDoc, corpus: List<KnowledgeDoc>, limit: Int = 8, minScore: Double = 0.08): List<Related> {
        val others = corpus.filter { it.id != target.id }
        if (others.isEmpty()) return emptyList()
        val tfs = others.associate { it.id to terms(it) }
        val targetTf = terms(target)
        val df = HashMap<String, Int>()
        (tfs.values + listOf(targetTf)).forEach { m -> m.keys.forEach { df[it] = (df[it] ?: 0) + 1 } }
        val n = others.size + 1.0
        fun vec(tf: Map<String, Int>) = tf.mapValues { (t, c) -> (1 + ln(c.toDouble())) * ln(1 + n / (df[t] ?: 1)) }
        val tv = vec(targetTf)
        val tNorm = sqrt(tv.values.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
        return others.mapNotNull { o ->
            val ov = vec(tfs.getValue(o.id))
            val oNorm = sqrt(ov.values.sumOf { it * it }).takeIf { it > 0 } ?: 1.0
            val shared = tv.keys.intersect(ov.keys)
            val cos = shared.sumOf { tv.getValue(it) * ov.getValue(it) } / (tNorm * oNorm)
            val tagOverlap = if (target.tagKeys.isEmpty() || o.tagKeys.isEmpty()) 0.0
                else target.tagKeys.intersect(o.tagKeys).size.toDouble() / target.tagKeys.union(o.tagKeys).size
            val score = cos + 0.25 * tagOverlap
            if (score < minScore) return@mapNotNull null
            val reasons = buildSet {
                if (cos >= minScore / 2) add(RelationReason.SHARED_TERMS)
                if (tagOverlap > 0) add(RelationReason.SHARED_TAGS)
            }
            val top = shared.sortedByDescending { tv.getValue(it) * ov.getValue(it) }.take(5)
            Related(o.id, score, reasons, top)
        }.sortedByDescending { it.score }.take(limit)
    }
}

object Duplicates {
    private fun shingles(text: String, k: Int = 3): Set<Int> {
        val w = TextNormalizer.words(text)
        if (w.size < k) return if (w.isEmpty()) emptySet() else setOf(w.joinToString(" ").hashCode())
        return (0..w.size - k).map { i -> w.subList(i, i + k).joinToString(" ").hashCode() }.toSet()
    }

    fun similarity(a: String, b: String): Double {
        val sa = shingles(a)
        val sb = shingles(b)
        if (sa.isEmpty() && sb.isEmpty()) return 0.0
        return sa.intersect(sb).size.toDouble() / sa.union(sb).size
    }

    data class Candidate(val id: String, val similarity: Double)

    /** Notes whose content overlaps at least [threshold] (Jaccard over 3-word shingles). */
    fun findFor(target: KnowledgeDoc, corpus: List<KnowledgeDoc>, threshold: Double = 0.6): List<Candidate> {
        val t = target.title + "\n" + target.plainText
        if (TextNormalizer.words(t).size < 3) return emptyList()
        return corpus.asSequence().filter { it.id != target.id }
            .map { Candidate(it.id, similarity(t, it.title + "\n" + it.plainText)) }
            .filter { it.similarity >= threshold }
            .sortedByDescending { it.similarity }
            .toList()
    }
}

/** Explicit, user-created links ([[Title]] or margin://note/<id>), resolved to note IDs. */
object LinkGraph {
    data class Resolution(val resolved: Set<String>, val missingTitles: Set<String>)

    /**
     * Resolves link targets in [body]. Titles match case-insensitively; when several notes share a
     * title the most recently updated wins (caller passes [titleIndex] already ordered that way).
     */
    fun resolve(body: String, titleIndex: Map<String, String>, existingIds: Set<String>): Resolution {
        val resolved = LinkedHashSet<String>()
        val missing = LinkedHashSet<String>()
        for (t in MarkdownParser.linkTargets(body)) {
            if (t.startsWith(MarkdownParser.NOTE_URI_PREFIX)) {
                val id = t.removePrefix(MarkdownParser.NOTE_URI_PREFIX)
                if (id in existingIds) resolved += id else missing += t
            } else {
                val id = titleIndex[t.trim().lowercase()]
                if (id != null) resolved += id else missing += t
            }
        }
        return Resolution(resolved, missing)
    }

    /** Notes with no incoming and no outgoing explicit links. */
    fun orphans(allIds: Collection<String>, links: Collection<Pair<String, String>>): Set<String> {
        val connected = HashSet<String>()
        links.forEach { (a, b) -> connected += a; connected += b }
        return allIds.filterNot { it in connected }.toSet()
    }
}
