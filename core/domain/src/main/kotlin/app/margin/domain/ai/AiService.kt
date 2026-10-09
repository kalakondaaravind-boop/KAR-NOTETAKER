package app.margin.domain.ai

import app.margin.domain.search.QueryNode
import app.margin.domain.search.ParsedQuery
import app.margin.domain.search.SearchDocument
import app.margin.domain.search.SearchEngine
import app.margin.domain.search.TextNormalizer
import app.margin.domain.template.FieldType
import app.margin.domain.template.FieldValue
import app.margin.domain.template.FormValues
import app.margin.domain.template.TemplateDefinition
import app.margin.domain.template.TemplateJson
import app.margin.domain.template.TemplateValidator
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class AiService(
    private val settings: () -> AiSettings,
    private val provider: () -> AiProvider?,
) {
    /** Runs one explicit user action. Never modifies notes; the caller previews the result. */
    suspend fun run(request: AiRequest, primaryNoteExcluded: Boolean = false): AiResult {
        val s = settings()
        val p = provider()
        when (val d = AiPolicy.check(request.action, s, p, primaryNoteExcluded)) {
            is AiPolicy.Decision.Denied -> return AiResult.Failure(d.error)
            AiPolicy.Decision.Allowed -> Unit
        }
        val prompt = PromptBuilder.build(request, s)
        val result = try { p!!.run(request, prompt) } catch (c: CancellationException) { throw c } catch (e: Exception) { AiResult.Failure(AiError.SERVER) }
        if (result !is AiResult.Success) return result
        val bounded = PromptBuilder.bound(request.sources, s.maxContextChars)
        return when (request.action) {
            AiAction.ASK_KNOWLEDGE_BASE, AiAction.ASK_SELECTED, AiAction.DRAFT_FROM_NOTES -> {
                if (result.local) return result.copy(sources = bounded)
                val g = ResponseParser.grounded(result.text, bounded.size)
                result.copy(
                    text = g.text, sources = bounded,
                    citedSourceIds = g.citedIndexes.map { bounded[it - 1].id },
                    insufficientEvidence = g.insufficient,
                )
            }
            else -> result
        }
    }
}

/** Keyword retrieval for "ask my notes": the question's content words OR-ed, ranked by the search engine. */
object KnowledgeRetrieval {
    private val stop = setOf("what", "when", "where", "which", "who", "whom", "why", "how", "does", "did", "the", "and", "for", "are",
        "was", "were", "is", "of", "to", "in", "on", "my", "me", "i", "a", "an", "do", "about", "with", "that", "this", "have", "has", "can", "you")

    fun query(question: String): ParsedQuery {
        val words = TextNormalizer.words(question).filter { it !in stop && it.length > 1 }.distinct().take(12)
        val root = when (words.size) {
            0 -> null
            1 -> QueryNode.Term(words[0])
            else -> QueryNode.Or(words.map { QueryNode.Term(it) })
        }
        return ParsedQuery(root, emptyList())
    }

    /** Top-[k] authorised notes for the question. Caller must pass only AI-eligible documents. */
    fun retrieve(question: String, eligibleDocs: Sequence<SearchDocument>, now: Instant, k: Int = 6, engine: SearchEngine = SearchEngine()): List<SourceNote> {
        val q = query(question)
        if (q.isEmpty) return emptyList()
        val byId = HashMap<String, SearchDocument>()
        val hits = engine.search(q, eligibleDocs.onEach { byId[it.id] = it }, now, limit = k)
        return hits.mapNotNull { h -> byId[h.id]?.let { SourceNote(it.id, it.title, it.plainBody) } }
    }
}

/** Validates AI output that must be structured. Deterministic validation stays the source of truth. */
object AiStructured {
    sealed interface TemplateDraft {
        data class Valid(val template: TemplateDefinition) : TemplateDraft
        data class Invalid(val problems: List<String>) : TemplateDraft
    }

    fun templateFromReply(raw: String, newId: String): TemplateDraft {
        val decoded = TemplateJson.decode(ResponseParser.json(raw))
        val t = decoded.getOrElse { return TemplateDraft.Invalid(listOf(it.message ?: "Could not read template")) }
            .copy(id = newId, builtIn = false, version = 1)
        val errors = TemplateValidator.validateSchema(t)
        return if (errors.isEmpty()) TemplateDraft.Valid(t) else TemplateDraft.Invalid(errors.map { "${it.path}: ${it.message}" })
    }

    data class Fill(val values: FormValues, val rejected: List<String>)

    /** Maps a JSON reply onto the template's non-repeating fields; anything invalid is rejected, not coerced. */
    fun fillFromReply(raw: String, t: TemplateDefinition): Fill {
        val obj = try { Json.parseToJsonElement(ResponseParser.json(raw)) as? JsonObject } catch (e: Exception) { null }
            ?: return Fill(FormValues(), listOf("Reply was not a JSON object"))
        val fields = t.allSections().filterNot { it.repeatable }.flatMap { it.fields }.associateBy { it.key }
        val values = HashMap<String, FieldValue>()
        val rejected = ArrayList<String>()
        for ((k, v) in obj) {
            val f = fields[k] ?: continue
            val str = (v as? JsonPrimitive)?.contentOrNull
            val arr = (v as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val parsed: FieldValue? = try {
                when (f.type) {
                    FieldType.TEXT, FieldType.LONG_TEXT, FieldType.RICH_TEXT, FieldType.URL, FieldType.EMAIL -> str?.let { FieldValue.Text(it) }
                    FieldType.NUMBER, FieldType.CURRENCY, FieldType.RATING, FieldType.PROGRESS -> str?.let { FieldValue.Number(BigDecimal(it.replace(",", "").trim())) }
                    FieldType.DATE -> str?.let { FieldValue.Date(LocalDate.parse(it.take(10))) }
                    FieldType.TIME -> str?.let { FieldValue.Time(LocalTime.parse(it)) }
                    FieldType.DATETIME -> str?.let { FieldValue.DateTime(LocalDateTime.parse(it)) }
                    FieldType.CHECKBOX -> (v as? JsonPrimitive)?.booleanOrNull?.let { FieldValue.Bool(it) }
                    FieldType.CHECKLIST -> (arr ?: str?.let { listOf(it) })?.let { l -> FieldValue.Checklist(l.map { it to false }) }
                    FieldType.LIST -> (arr ?: str?.let { listOf(it) })?.let { FieldValue.Items(it) }
                    FieldType.SINGLE_SELECT -> str?.let { s -> f.options.firstOrNull { it.equals(s.trim(), true) }?.let { FieldValue.Choices(listOf(it)) } }
                    FieldType.MULTI_SELECT -> (arr ?: str?.split(','))?.mapNotNull { s -> f.options.firstOrNull { it.equals(s.trim(), true) } }
                        ?.takeIf { it.isNotEmpty() }?.let { FieldValue.Choices(it.distinct()) }
                    FieldType.TABLE -> (v as? JsonArray)?.map { row -> ((row as? JsonArray)?.map { (it as? JsonPrimitive)?.contentOrNull.orEmpty() }) ?: listOf((row as? JsonPrimitive)?.contentOrNull.orEmpty()) }
                        ?.let { FieldValue.Rows(it) }
                }
            } catch (e: Exception) { null }
            if (parsed == null) rejected += k else values[k] = parsed
        }
        val form = FormValues(values)
        // Drop values that fail validation (e.g. out-of-range numbers) instead of passing them on.
        val bad = TemplateValidator.validateValues(t, form).filter { it.kind != app.margin.domain.template.ValueError.Kind.REQUIRED }.map { it.fieldKey }.toSet()
        return Fill(FormValues(values.filterKeys { it !in bad }), rejected + bad)
    }
}
