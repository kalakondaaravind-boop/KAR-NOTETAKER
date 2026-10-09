package app.margin.domain.ai

import app.margin.domain.search.TextNormalizer
import app.margin.domain.text.Block
import app.margin.domain.text.InlineParser
import app.margin.domain.text.MarkdownParser

/**
 * On-device, deterministic "AI". It never sends data anywhere and never invents content: every
 * output is extracted from the input text. It supports a subset of actions; others report
 * UNSUPPORTED so the UI can explain that a configured model is required.
 */
class LocalAiProvider : AiProvider {
    override val id = "local"
    override val displayName = "On-device (basic)"
    override val sendsDataOffDevice = false

    private val supported = setOf(
        AiAction.SUMMARIZE_SHORT, AiAction.SUMMARIZE_DETAILED, AiAction.EXTRACT_ACTIONS, AiAction.EXTRACT_DATES,
        AiAction.EXTRACT_DECISIONS, AiAction.GENERATE_CHECKLIST, AiAction.MAKE_OUTLINE, AiAction.SUGGEST_TITLE,
        AiAction.SUGGEST_TAGS, AiAction.STUDY_QUESTIONS, AiAction.FLASHCARDS, AiAction.ASK_SELECTED, AiAction.ASK_KNOWLEDGE_BASE,
        AiAction.UNANSWERED_QUESTIONS,
    )

    override fun supports(action: AiAction) = action in supported

    override suspend fun run(request: AiRequest, prompt: Prompt): AiResult {
        val text = when (request.action) {
            AiAction.SUMMARIZE_SHORT -> summarize(request.input, 3)
            AiAction.SUMMARIZE_DETAILED -> summarize(request.input, 7).lines().joinToString("\n") { "- $it" }
            AiAction.EXTRACT_ACTIONS -> actions(request.input).ifEmpty { listOf("No action items found.") }.joinToString("\n") { if (it.startsWith("No action")) it else "- [ ] $it" }
            AiAction.EXTRACT_DATES -> dates(request.input).ifEmpty { listOf("No dates found.") }.joinToString("\n") { "- $it" }
            AiAction.EXTRACT_DECISIONS -> decisions(request.input).ifEmpty { listOf("No decisions found.") }.joinToString("\n") { "- $it" }
            AiAction.GENERATE_CHECKLIST -> sentences(request.input).take(20).joinToString("\n") { "- [ ] ${it.trimEnd('.')}" }
            AiAction.MAKE_OUTLINE -> outline(request.input)
            AiAction.SUGGEST_TITLE -> title(request.input)
            AiAction.SUGGEST_TAGS -> suggestTags(request.input, request.existingTags).joinToString(", ")
            AiAction.STUDY_QUESTIONS -> studyQuestions(request.input).joinToString("\n") { "- $it" }
            AiAction.FLASHCARDS -> flashcards(request.input).ifEmpty { listOf("No definitions (\"X is Y\") found to turn into cards.") }.joinToString("\n") { "- $it" }
            AiAction.UNANSWERED_QUESTIONS -> sentences(request.input).filter { it.trim().endsWith("?") }.ifEmpty { listOf("No open questions found.") }.joinToString("\n") { "- $it" }
            AiAction.ASK_SELECTED, AiAction.ASK_KNOWLEDGE_BASE -> return answer(request)
            else -> return AiResult.Failure(AiError.UNSUPPORTED)
        }
        return AiResult.Success(text, displayName, local = true)
    }

    private val stop = setOf("the", "and", "for", "that", "this", "with", "are", "was", "but", "not", "you", "your", "from", "have", "has",
        "will", "would", "can", "could", "should", "what", "when", "where", "which", "who", "how", "about", "into", "than", "then", "there",
        "they", "them", "their", "were", "been", "also", "just", "more", "some", "any", "all", "our", "its", "a", "an", "of", "to", "in",
        "on", "is", "it", "as", "at", "by", "or", "be", "we", "i", "my", "me", "do", "so", "if", "no", "up")

    fun sentences(md: String): List<String> {
        val out = ArrayList<String>()
        for (b in MarkdownParser.parse(md)) {
            when (b) {
                is Block.Paragraph -> out += InlineParser.plainText(b.inlines).split(Regex("""(?<=[.!?])\s+""")).map { it.trim() }.filter { it.length > 2 }
                is Block.ListItem -> out += InlineParser.plainText(b.inlines).trim()
                is Block.Quote -> out += InlineParser.plainText(b.inlines).trim()
                else -> Unit
            }
        }
        return out.filter { it.isNotBlank() }
    }

    /** Extractive summary: highest-scoring sentences by keyword frequency, in original order. */
    fun summarize(md: String, n: Int): String {
        val sents = sentences(md)
        if (sents.size <= n) return sents.joinToString(" ")
        val freq = sents.flatMap { TextNormalizer.words(it) }.filter { it !in stop && it.length > 2 }.groupingBy { it }.eachCount()
        val scored = sents.mapIndexed { i, s ->
            val w = TextNormalizer.words(s).filter { it !in stop && it.length > 2 }
            Triple(i, s, if (w.isEmpty()) 0.0 else w.sumOf { freq[it] ?: 0 }.toDouble() / (w.size + 3) + (if (i == 0) 1.0 else 0.0))
        }
        return scored.sortedByDescending { it.third }.take(n).sortedBy { it.first }.joinToString(" ") { it.second }
    }

    private val actionRe = Regex("""(?i)\b(todo|to do|action|need to|needs to|must|should|follow up|follow-up|remember to|don't forget|by (monday|tuesday|wednesday|thursday|friday|tomorrow|next week))\b""")

    fun actions(md: String): List<String> {
        val out = LinkedHashSet<String>()
        for (b in MarkdownParser.parse(md)) {
            if (b is Block.ListItem && b.kind == Block.ListKind.TASK && !b.checked) out += InlineParser.plainText(b.inlines).trim()
        }
        sentences(md).filter { actionRe.containsMatchIn(it) }.forEach { out += it.trimEnd('.') }
        return out.filter { it.isNotBlank() }.take(30)
    }

    private val decisionRe = Regex("""(?i)\b(decided|decision|agreed|we will|we'll|chose|approved|go with|resolved)\b""")
    fun decisions(md: String) = sentences(md).filter { decisionRe.containsMatchIn(it) }.take(30)

    private val dateRes = listOf(
        Regex("""\b\d{4}-\d{2}-\d{2}\b"""),
        Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\b"""),
        Regex("""(?i)\b(\d{1,2}\s+)?(jan(uary)?|feb(ruary)?|mar(ch)?|apr(il)?|may|jun(e)?|jul(y)?|aug(ust)?|sep(t(ember)?)?|oct(ober)?|nov(ember)?|dec(ember)?)(\s+\d{1,2}(st|nd|rd|th)?)?(,?\s+\d{4})?\b"""),
        Regex("""(?i)\b(today|tomorrow|tonight|next (week|month|monday|tuesday|wednesday|thursday|friday)|this (week|friday|monday))\b"""),
    )

    fun dates(md: String): List<String> = sentences(md).mapNotNull { s ->
        val hits = dateRes.flatMap { r -> r.findAll(s).map { it.value.trim() }.filter { v -> v.length > 3 || v.any(Char::isDigit) } }
        if (hits.isEmpty()) null else hits.distinct().joinToString(", ") + " — " + s.take(140)
    }.distinct().take(30)

    fun outline(md: String): String {
        val headings = MarkdownParser.parse(md).filterIsInstance<Block.Heading>()
        if (headings.isNotEmpty()) return headings.joinToString("\n") { "  ".repeat(it.level - 1) + "- " + InlineParser.plainText(it.inlines) }
        return sentences(md).take(15).joinToString("\n") { "- " + it.take(100) }
    }

    fun title(md: String): String {
        MarkdownParser.parse(md).firstOrNull { it is Block.Heading }?.let { return InlineParser.plainText((it as Block.Heading).inlines).take(80) }
        val kw = keywords(md, 4)
        return if (kw.isEmpty()) "Untitled" else kw.joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    fun keywords(md: String, n: Int): List<String> =
        TextNormalizer.words(MarkdownParser.toPlainText(md)).filter { it !in stop && it.length > 3 && !it.all(Char::isDigit) }
            .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(n).map { it.key }

    fun suggestTags(md: String, existing: List<String>): List<String> {
        val words = TextNormalizer.words(MarkdownParser.toPlainText(md)).toSet()
        val fromExisting = existing.filter { t -> TextNormalizer.words(t).let { tw -> tw.isNotEmpty() && tw.all { it in words } } }
        return (fromExisting + keywords(md, 5)).distinctBy { it.lowercase() }.take(5)
    }

    private val defRe = Regex("""^(.{2,60}?)\s+(is|are|means|refers to)\s+(.{5,})$""", RegexOption.IGNORE_CASE)

    fun flashcards(md: String): List<String> = sentences(md).mapNotNull { s ->
        defRe.matchEntire(s.trimEnd('.'))?.let { m -> "Q: What ${m.groupValues[2].lowercase()} ${m.groupValues[1].trim()}? | A: ${m.groupValues[3].trim()}" }
    }.take(30)

    fun studyQuestions(md: String): List<String> {
        val cards = flashcards(md).map { it.substringBefore(" | ").removePrefix("Q: ") }
        val heads = MarkdownParser.parse(md).filterIsInstance<Block.Heading>().map { "Explain: ${InlineParser.plainText(it.inlines)}" }
        return (cards + heads).ifEmpty { keywords(md, 5).map { "What do your notes say about \"$it\"?" } }.take(10)
    }

    /**
     * Extractive question answering: returns the source sentences that best match the question,
     * each cited. If nothing matches, reports insufficient evidence instead of guessing.
     */
    private fun answer(r: AiRequest): AiResult {
        val q = TextNormalizer.words(r.question.orEmpty()).filter { it !in stop && it.length > 2 }.toSet()
        if (q.isEmpty() || r.sources.isEmpty()) return AiResult.Success("Not enough information in the selected notes to answer.", displayName, r.sources, emptyList(), true, local = true)
        data class Hit(val src: Int, val sentence: String, val score: Int)
        val hits = r.sources.flatMapIndexed { i, s ->
            sentences(s.text).map { sent -> Hit(i, sent, TextNormalizer.words(sent).toSet().count { w -> q.any { w.startsWith(it) } }) }
        }.filter { it.score > 0 }.sortedByDescending { it.score }
        val needed = maxOf(1, (q.size + 1) / 2)
        val good = hits.filter { it.score >= needed }.take(4)
        if (good.isEmpty()) return AiResult.Success("Not enough information in the selected notes to answer.", displayName, r.sources, emptyList(), true, local = true)
        val text = "Relevant passages from your notes:\n" + good.joinToString("\n") { "- \"${it.sentence}\" [${it.src + 1}]" }
        return AiResult.Success(text, displayName, r.sources, good.map { r.sources[it.src].id }.distinct(), false, local = true)
    }
}
