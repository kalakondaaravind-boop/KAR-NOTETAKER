package app.margin.domain.template

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class FieldType(val code: String) {
    TEXT("text"),
    LONG_TEXT("long"),
    RICH_TEXT("rich"),
    NUMBER("number"),
    CURRENCY("currency"),
    DATE("date"),
    TIME("time"),
    DATETIME("datetime"),
    CHECKBOX("checkbox"),
    CHECKLIST("checklist"),
    SINGLE_SELECT("select"),
    MULTI_SELECT("multiselect"),
    URL("url"),
    EMAIL("email"),
    RATING("rating"),
    PROGRESS("progress"),
    /** A bulleted list of free-text items. */
    LIST("list"),
    /** A Markdown table with fixed columns (options = column headers). */
    TABLE("table");

    companion object {
        fun fromCode(c: String): FieldType? = entries.firstOrNull { it.code == c.lowercase() }
    }
}

/** Show a field/section only when another field has a given value (or, with value=null, is non-empty). */
data class Condition(val fieldKey: String, val equals: String?)

data class TemplateField(
    val key: String,
    val label: String,
    val type: FieldType,
    val required: Boolean = false,
    /** Default as text; "today"/"now" are resolved at fill time. Lists use '|' separators. */
    val default: String? = null,
    val placeholder: String? = null,
    val help: String? = null,
    val options: List<String> = emptyList(),
    val min: BigDecimal? = null,
    val max: BigDecimal? = null,
    val maxLength: Int? = null,
    val currency: String? = null,
    val visibleIf: Condition? = null,
)

data class TemplateSection(
    val key: String,
    val title: String,
    val description: String? = null,
    val fields: List<TemplateField> = emptyList(),
    val sections: List<TemplateSection> = emptyList(),
    val repeatable: Boolean = false,
    val minRepeat: Int = 1,
    val maxRepeat: Int = 20,
    val visibleIf: Condition? = null,
)

data class TemplateDefinition(
    /** Stable id, e.g. "builtin.daily-journal" or "user.<uuid>". */
    val id: String,
    val version: Int,
    val name: String,
    val description: String,
    val category: String,
    val subcategory: String? = null,
    val icon: String = "note",
    val color: Long? = null,
    val intendedUse: String? = null,
    /** e.g. "Journal — {{date}}" or "{{field:project}} status". */
    val titlePattern: String = "{{name}}",
    val defaultTags: List<String> = emptyList(),
    val sections: List<TemplateSection>,
    /**
     * Sensitive templates (health, identity documents, finances) create notes that are excluded
     * from AI context by default. The user can change this per note.
     */
    val sensitive: Boolean = false,
    /** Included in exports/backups as a definition. */
    val exportable: Boolean = true,
    val builtIn: Boolean = false,
    /** Shown before use, e.g. "Not legal advice." */
    val disclaimer: String? = null,
    /** Aliases: catalogue names this template also satisfies. */
    val aliases: List<String> = emptyList(),
    /**
     * Fixed Markdown appended after the form sections. Used by "save note as template"; supports
     * the same {{date}}/{{time}}/{{week}}/{{month}}/{{year}} placeholders as [titlePattern].
     */
    val staticBody: String? = null,
) {
    fun allFields(): List<TemplateField> {
        val out = ArrayList<TemplateField>()
        fun walk(s: TemplateSection) { out += s.fields; s.sections.forEach(::walk) }
        sections.forEach(::walk)
        return out
    }

    fun allSections(): List<TemplateSection> {
        val out = ArrayList<TemplateSection>()
        fun walk(s: TemplateSection) { out += s; s.sections.forEach(::walk) }
        sections.forEach(::walk)
        return out
    }
}

sealed interface FieldValue {
    data class Text(val value: String) : FieldValue
    data class Number(val value: BigDecimal) : FieldValue
    data class Bool(val value: Boolean) : FieldValue
    data class Date(val value: LocalDate) : FieldValue
    data class Time(val value: LocalTime) : FieldValue
    data class DateTime(val value: LocalDateTime) : FieldValue
    data class Choices(val values: List<String>) : FieldValue
    data class Checklist(val items: List<Pair<String, Boolean>>) : FieldValue
    data class Items(val values: List<String>) : FieldValue
    data class Rows(val rows: List<List<String>>) : FieldValue
}

/**
 * Values for a template form. Non-repeatable fields live in [values] keyed by field key;
 * each repeatable section stores one map per instance in [repeats].
 */
data class FormValues(
    val values: Map<String, FieldValue> = emptyMap(),
    val repeats: Map<String, List<Map<String, FieldValue>>> = emptyMap(),
)
