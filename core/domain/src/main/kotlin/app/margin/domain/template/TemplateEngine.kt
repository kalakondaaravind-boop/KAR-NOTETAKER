package app.margin.domain.template

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Schema-level problems in a template definition (deterministic; AI-generated templates go through this too). */
data class SchemaError(val path: String, val message: String)

/** Value-level problems when filling in a template. */
data class ValueError(val fieldKey: String, val instance: Int?, val kind: Kind) {
    enum class Kind { REQUIRED, NOT_A_NUMBER, BELOW_MIN, ABOVE_MAX, TOO_LONG, INVALID_EMAIL, INVALID_URL, INVALID_OPTION, INVALID_DATE }
}

object TemplateValidator {
    const val MAX_FIELDS = 200
    const val MAX_DEPTH = 3
    const val MAX_LABEL = 200
    private val keyRe = Regex("""[a-z][a-z0-9_]{0,63}""")

    fun validateSchema(t: TemplateDefinition): List<SchemaError> {
        val errors = ArrayList<SchemaError>()
        if (t.id.isBlank() || t.id.length > 120) errors += SchemaError("id", "id must be 1–120 characters")
        if (t.name.isBlank()) errors += SchemaError("name", "name is required")
        if (t.name.length > MAX_LABEL) errors += SchemaError("name", "name too long")
        if (t.category.isBlank()) errors += SchemaError("category", "category is required")
        if (t.version < 1) errors += SchemaError("version", "version must be ≥ 1")
        if (t.sections.isEmpty() && t.staticBody.isNullOrBlank()) errors += SchemaError("sections", "at least one section or a body is required")
        if ((t.staticBody?.length ?: 0) > 200_000) errors += SchemaError("staticBody", "body too long")
        val fields = t.allFields()
        if (fields.size > MAX_FIELDS) errors += SchemaError("fields", "too many fields (max $MAX_FIELDS)")
        val keys = HashMap<String, TemplateField>()
        fields.forEach { f ->
            val p = "field:${f.key}"
            if (!keyRe.matches(f.key)) errors += SchemaError(p, "invalid key")
            if (keys.put(f.key, f) != null) errors += SchemaError(p, "duplicate key")
            if (f.label.isBlank() || f.label.length > MAX_LABEL) errors += SchemaError(p, "label must be 1–$MAX_LABEL characters")
            if (f.type in setOf(FieldType.SINGLE_SELECT, FieldType.MULTI_SELECT) && f.options.isEmpty()) errors += SchemaError(p, "select needs options")
            if (f.type == FieldType.TABLE && f.options.isEmpty()) errors += SchemaError(p, "table needs column headers")
            if (f.options.size != f.options.distinct().size) errors += SchemaError(p, "duplicate options")
            if (f.min != null && f.max != null && f.min > f.max) errors += SchemaError(p, "min > max")
            if (f.type == FieldType.RATING && (f.max == null || f.max < BigDecimal.ONE || f.max > BigDecimal.TEN)) errors += SchemaError(p, "rating max must be 1–10")
            f.maxLength?.let { if (it < 1) errors += SchemaError(p, "maxLength must be positive") }
            f.default?.let { d ->
                val v = TemplateEngine.parseDefault(f, d, LocalDateTime.of(2000, 1, 1, 0, 0))
                if (v == null) errors += SchemaError(p, "default '$d' is not valid for ${f.type.code}")
                else if (f.type in setOf(FieldType.SINGLE_SELECT, FieldType.MULTI_SELECT)) {
                    val chosen = (v as? FieldValue.Choices)?.values.orEmpty()
                    if (chosen.any { it !in f.options }) errors += SchemaError(p, "default not among options")
                }
            }
        }
        val sectionKeys = HashSet<String>()
        fun walk(s: TemplateSection, depth: Int, inRepeat: Boolean) {
            val p = "section:${s.key}"
            if (depth > MAX_DEPTH) errors += SchemaError(p, "sections nested deeper than $MAX_DEPTH")
            if (!sectionKeys.add(s.key)) errors += SchemaError(p, "duplicate section key")
            if (s.repeatable && inRepeat) errors += SchemaError(p, "repeatable sections cannot be nested")
            if (s.repeatable && (s.minRepeat < 0 || s.maxRepeat < maxOf(1, s.minRepeat) || s.maxRepeat > 100)) errors += SchemaError(p, "invalid repeat bounds")
            if (s.repeatable && s.sections.isNotEmpty()) errors += SchemaError(p, "repeatable sections cannot contain sections")
            s.sections.forEach { walk(it, depth + 1, inRepeat || s.repeatable) }
        }
        t.sections.forEach { walk(it, 1, false) }
        // Conditions must reference existing, non-repeating fields declared anywhere in the template.
        val repeatFieldKeys = t.allSections().filter { it.repeatable }.flatMap { s -> s.fields.map { it.key } }.toSet()
        val conds = fields.mapNotNull { f -> f.visibleIf?.let { "field:${f.key}" to it } } +
            t.allSections().mapNotNull { s -> s.visibleIf?.let { "section:${s.key}" to it } }
        conds.forEach { (p, c) ->
            val target = keys[c.fieldKey]
            when {
                target == null -> errors += SchemaError(p, "condition references unknown field '${c.fieldKey}'")
                c.fieldKey in repeatFieldKeys -> errors += SchemaError(p, "condition cannot reference a repeating field")
                p == "field:${c.fieldKey}" -> errors += SchemaError(p, "field cannot depend on itself")
            }
        }
        return errors
    }

    private val emailRe = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    private val urlRe = Regex("""^(https?://)[^\s/$.?#].[^\s]*$""", RegexOption.IGNORE_CASE)

    /** Validates values. Hidden (conditional) fields are never required. */
    fun validateValues(t: TemplateDefinition, v: FormValues): List<ValueError> {
        val errors = ArrayList<ValueError>()
        fun check(f: TemplateField, value: FieldValue?, instance: Int?) {
            val empty = TemplateEngine.isEmpty(value)
            if (empty) { if (f.required) errors += ValueError(f.key, instance, ValueError.Kind.REQUIRED); return }
            when (f.type) {
                FieldType.NUMBER, FieldType.CURRENCY, FieldType.RATING, FieldType.PROGRESS -> {
                    val n = (value as? FieldValue.Number)?.value
                    if (n == null) errors += ValueError(f.key, instance, ValueError.Kind.NOT_A_NUMBER)
                    else {
                        val max = f.max ?: if (f.type == FieldType.PROGRESS) BigDecimal(100) else null
                        val min = f.min ?: if (f.type == FieldType.PROGRESS || f.type == FieldType.RATING) BigDecimal.ZERO else null
                        if (min != null && n < min) errors += ValueError(f.key, instance, ValueError.Kind.BELOW_MIN)
                        if (max != null && n > max) errors += ValueError(f.key, instance, ValueError.Kind.ABOVE_MAX)
                    }
                }
                FieldType.EMAIL -> if (!emailRe.matches((value as? FieldValue.Text)?.value?.trim().orEmpty())) errors += ValueError(f.key, instance, ValueError.Kind.INVALID_EMAIL)
                FieldType.URL -> if (!urlRe.matches((value as? FieldValue.Text)?.value?.trim().orEmpty())) errors += ValueError(f.key, instance, ValueError.Kind.INVALID_URL)
                FieldType.SINGLE_SELECT, FieldType.MULTI_SELECT -> {
                    val c = (value as? FieldValue.Choices)?.values.orEmpty()
                    if (c.any { it !in f.options } || (f.type == FieldType.SINGLE_SELECT && c.size > 1)) errors += ValueError(f.key, instance, ValueError.Kind.INVALID_OPTION)
                }
                else -> Unit
            }
            val len = when (value) { is FieldValue.Text -> value.value.length; else -> 0 }
            if (f.maxLength != null && len > f.maxLength) errors += ValueError(f.key, instance, ValueError.Kind.TOO_LONG)
        }
        fun walk(s: TemplateSection) {
            if (!TemplateEngine.visible(s.visibleIf, v)) return
            if (s.repeatable) {
                v.repeats[s.key].orEmpty().forEachIndexed { i, inst ->
                    s.fields.forEach { f -> if (TemplateEngine.visible(f.visibleIf, v)) check(f, inst[f.key], i) }
                }
            } else {
                s.fields.forEach { f -> if (TemplateEngine.visible(f.visibleIf, v)) check(f, v.values[f.key], null) }
                s.sections.forEach(::walk)
            }
        }
        t.sections.forEach(::walk)
        return errors
    }
}

data class RenderedNote(val title: String, val body: String, val tags: List<String>)

object TemplateEngine {

    fun isEmpty(v: FieldValue?): Boolean = when (v) {
        null -> true
        is FieldValue.Text -> v.value.isBlank()
        is FieldValue.Choices -> v.values.isEmpty()
        is FieldValue.Checklist -> v.items.none { it.first.isNotBlank() }
        is FieldValue.Items -> v.values.none { it.isNotBlank() }
        is FieldValue.Rows -> v.rows.none { r -> r.any { it.isNotBlank() } }
        is FieldValue.Bool -> false
        else -> false
    }

    fun visible(c: Condition?, v: FormValues): Boolean {
        if (c == null) return true
        val value = v.values[c.fieldKey]
        return if (c.equals == null) !isEmpty(value) else when (value) {
            is FieldValue.Text -> value.value.equals(c.equals, ignoreCase = true)
            is FieldValue.Choices -> value.values.any { it.equals(c.equals, ignoreCase = true) }
            is FieldValue.Bool -> value.value.toString() == c.equals.lowercase()
            is FieldValue.Number -> value.value.compareTo(c.equals.toBigDecimalOrNull() ?: return false) == 0
            else -> false
        }
    }

    /** Parses a textual default; returns null if it is invalid for the field type. */
    fun parseDefault(f: TemplateField, raw: String, now: LocalDateTime): FieldValue? = try {
        when (f.type) {
            FieldType.TEXT, FieldType.LONG_TEXT, FieldType.RICH_TEXT, FieldType.URL, FieldType.EMAIL -> FieldValue.Text(raw)
            FieldType.NUMBER, FieldType.CURRENCY, FieldType.RATING, FieldType.PROGRESS -> FieldValue.Number(BigDecimal(raw))
            FieldType.DATE -> FieldValue.Date(when (raw) { "today" -> now.toLocalDate(); "tomorrow" -> now.toLocalDate().plusDays(1); else -> LocalDate.parse(raw) })
            FieldType.TIME -> FieldValue.Time(if (raw == "now") now.toLocalTime().withSecond(0).withNano(0) else LocalTime.parse(raw))
            FieldType.DATETIME -> FieldValue.DateTime(if (raw == "now") now.withSecond(0).withNano(0) else LocalDateTime.parse(raw))
            FieldType.CHECKBOX -> FieldValue.Bool(raw.toBooleanStrict())
            FieldType.CHECKLIST -> FieldValue.Checklist(raw.split('|').map { it.trim() to false }.filter { it.first.isNotEmpty() })
            FieldType.SINGLE_SELECT -> FieldValue.Choices(listOf(raw))
            FieldType.MULTI_SELECT -> FieldValue.Choices(raw.split('|').map { it.trim() }.filter { it.isNotEmpty() })
            FieldType.LIST -> FieldValue.Items(raw.split('|').map { it.trim() }.filter { it.isNotEmpty() })
            FieldType.TABLE -> FieldValue.Rows(raw.split('|').map { r -> r.split(';').map { it.trim() } })
        }
    } catch (e: NumberFormatException) { null } catch (e: DateTimeParseException) { null } catch (e: IllegalArgumentException) { null }

    /** Initial form state with defaults resolved and one instance per repeatable section. */
    fun initialValues(t: TemplateDefinition, now: LocalDateTime): FormValues {
        val values = HashMap<String, FieldValue>()
        val repeats = HashMap<String, List<Map<String, FieldValue>>>()
        fun defaults(fields: List<TemplateField>) = fields.mapNotNull { f -> f.default?.let { d -> parseDefault(f, d, now)?.let { f.key to it } } }.toMap()
        t.allSections().forEach { s ->
            if (s.repeatable) repeats[s.key] = List(maxOf(1, s.minRepeat)) { defaults(s.fields) }
            else values += defaults(s.fields)
        }
        return FormValues(values, repeats)
    }

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    private val dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun formatValue(f: TemplateField, v: FieldValue?): String = when (v) {
        null -> ""
        is FieldValue.Text -> v.value.trim()
        is FieldValue.Number -> when (f.type) {
            FieldType.CURRENCY -> (f.currency?.let { "$it " } ?: "") + v.value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            FieldType.PROGRESS -> "${v.value.stripTrailingZeros().toPlainString()}%"
            FieldType.RATING -> {
                val max = (f.max ?: BigDecimal(5)).toInt()
                val n = v.value.toInt().coerceIn(0, max)
                "★".repeat(n) + "☆".repeat(max - n) + " ($n/$max)"
            }
            else -> v.value.stripTrailingZeros().toPlainString()
        }
        is FieldValue.Bool -> if (v.value) "Yes" else "No"
        is FieldValue.Date -> v.value.format(dateFmt)
        is FieldValue.Time -> v.value.format(timeFmt)
        is FieldValue.DateTime -> v.value.format(dtFmt)
        is FieldValue.Choices -> v.values.joinToString(", ")
        is FieldValue.Checklist -> v.items.joinToString(", ") { it.first }
        is FieldValue.Items -> v.values.joinToString(", ")
        is FieldValue.Rows -> v.rows.joinToString("; ") { it.joinToString(" / ") }
    }

    /**
     * Generates the note. Empty optional fields still render their label so the structure stays
     * useful for filling in later in the editor; hidden conditional fields are omitted.
     */
    fun render(t: TemplateDefinition, v: FormValues, now: LocalDateTime): RenderedNote {
        val sb = StringBuilder()
        fun field(f: TemplateField, value: FieldValue?) {
            if (!visible(f.visibleIf, v)) return
            when (f.type) {
                FieldType.LONG_TEXT, FieldType.RICH_TEXT -> {
                    sb.append("**").append(f.label).append("**\n")
                    val text = (value as? FieldValue.Text)?.value?.trim().orEmpty()
                    sb.append(if (text.isEmpty()) "" else text).append("\n\n")
                }
                FieldType.CHECKLIST -> {
                    sb.append("**").append(f.label).append("**\n")
                    val items = (value as? FieldValue.Checklist)?.items?.filter { it.first.isNotBlank() }.orEmpty()
                    if (items.isEmpty()) sb.append("- [ ] \n") else items.forEach { (text, done) -> sb.append(if (done) "- [x] " else "- [ ] ").append(text.trim()).append('\n') }
                    sb.append('\n')
                }
                FieldType.LIST -> {
                    sb.append("**").append(f.label).append("**\n")
                    val items = (value as? FieldValue.Items)?.values?.filter { it.isNotBlank() }.orEmpty()
                    if (items.isEmpty()) sb.append("- \n") else items.forEach { sb.append("- ").append(it.trim()).append('\n') }
                    sb.append('\n')
                }
                FieldType.TABLE -> {
                    sb.append("**").append(f.label).append("**\n\n")
                    val cols = f.options
                    sb.append(cols.joinToString(" | ", "| ", " |")).append('\n')
                    sb.append(cols.joinToString(" | ", "| ", " |") { "---" }).append('\n')
                    val rows = (value as? FieldValue.Rows)?.rows?.filter { r -> r.any { it.isNotBlank() } }.orEmpty()
                    val out = if (rows.isEmpty()) listOf(List(cols.size) { "" }) else rows
                    out.forEach { r -> sb.append(List(cols.size) { i -> r.getOrElse(i) { "" }.replace("|", "\\|").ifBlank { " " } }.joinToString(" | ", "| ", " |")).append('\n') }
                    sb.append('\n')
                }
                else -> sb.append("**").append(f.label).append(":** ").append(formatValue(f, value)).append("\n\n")
            }
        }
        fun section(s: TemplateSection, level: Int) {
            if (!visible(s.visibleIf, v)) return
            val hashes = "#".repeat(level.coerceAtMost(6))
            if (s.repeatable) {
                val instances = v.repeats[s.key].orEmpty().ifEmpty { listOf(emptyMap()) }
                instances.forEachIndexed { i, inst ->
                    if (s.title.isNotEmpty()) sb.append(hashes).append(' ').append(s.title).append(if (instances.size > 1) " ${i + 1}" else "").append("\n\n")
                    if (i == 0) s.description?.let { sb.append("*").append(it).append("*\n\n") }
                    s.fields.forEach { f -> field(f, inst[f.key]) }
                }
            } else {
                if (s.title.isNotEmpty()) sb.append(hashes).append(' ').append(s.title).append("\n\n")
                s.description?.let { sb.append("*").append(it).append("*\n\n") }
                s.fields.forEach { f -> field(f, v.values[f.key]) }
                s.sections.forEach { section(it, level + 1) }
            }
        }
        t.sections.forEach { section(it, 2) }
        t.staticBody?.let { sb.append(expandPlaceholders(it, now)) }
        return RenderedNote(renderTitle(t, v, now), sb.toString().trimEnd() + "\n", t.defaultTags)
    }

    /** Date placeholders only (used for static bodies, where field values do not apply). */
    fun expandPlaceholders(text: String, now: LocalDateTime): String =
        Regex("""\{\{\s*(date|time|week|month|year)\s*}}""").replace(text) { m -> placeholder(m.groupValues[1], now) ?: m.value }

    private fun placeholder(name: String, now: LocalDateTime): String? = when (name) {
        "date" -> now.toLocalDate().format(dateFmt)
        "time" -> now.toLocalTime().format(timeFmt)
        "week" -> "W" + now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString().padStart(2, '0')
        "month" -> now.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        "year" -> now.year.toString()
        else -> null
    }

    fun renderTitle(t: TemplateDefinition, v: FormValues, now: LocalDateTime): String {
        val fieldsByKey = t.allFields().associateBy { it.key }
        return Regex("""\{\{\s*([a-z]+)(?::([a-z0-9_]+))?\s*}}""").replace(t.titlePattern) { m ->
            when (m.groupValues[1]) {
                "name" -> t.name
                "date", "time", "week", "month", "year" -> placeholder(m.groupValues[1], now).orEmpty()
                "field" -> fieldsByKey[m.groupValues[2]]?.let { formatValue(it, v.values[it.key]) }.orEmpty()
                else -> m.value
            }
        }.replace(Regex("""\s{2,}"""), " ").trim().trim('—', '-', ':').trim().ifEmpty { t.name }
    }
}
