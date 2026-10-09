package app.margin.domain.template

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.math.BigDecimal

/**
 * JSON form of a template (custom templates, export/import, AI-generated drafts).
 * Documented in docs/DATA_FORMATS.md. Decoding never throws on bad input: it returns
 * [Result.failure] with a readable message, and the caller then runs [TemplateValidator].
 */
object TemplateJson {
    const val FORMAT = "margin.template"
    const val FORMAT_VERSION = 1
    private const val MAX_BYTES = 512 * 1024

    private val json = Json { prettyPrint = true }

    fun encode(t: TemplateDefinition): String = json.encodeToString(JsonElement.serializer(), toJson(t))

    fun toJson(t: TemplateDefinition): JsonObject = buildJsonObject {
        put("format", FORMAT)
        put("formatVersion", FORMAT_VERSION)
        put("id", t.id)
        put("version", t.version)
        put("name", t.name)
        put("description", t.description)
        put("category", t.category)
        t.subcategory?.let { put("subcategory", it) }
        put("icon", t.icon)
        t.color?.let { put("color", it) }
        t.intendedUse?.let { put("intendedUse", it) }
        put("titlePattern", t.titlePattern)
        put("defaultTags", buildJsonArray { t.defaultTags.forEach { add(JsonPrimitive(it)) } })
        put("sensitive", t.sensitive)
        put("exportable", t.exportable)
        t.disclaimer?.let { put("disclaimer", it) }
        t.staticBody?.let { put("staticBody", it) }
        put("sections", buildJsonArray { t.sections.forEach { add(section(it)) } })
    }

    private fun section(s: TemplateSection): JsonObject = buildJsonObject {
        put("key", s.key)
        put("title", s.title)
        s.description?.let { put("description", it) }
        if (s.repeatable) { put("repeatable", true); put("minRepeat", s.minRepeat); put("maxRepeat", s.maxRepeat) }
        s.visibleIf?.let { put("visibleIf", cond(it)) }
        put("fields", buildJsonArray { s.fields.forEach { add(field(it)) } })
        if (s.sections.isNotEmpty()) put("sections", buildJsonArray { s.sections.forEach { add(section(it)) } })
    }

    private fun cond(c: Condition) = buildJsonObject { put("field", c.fieldKey); c.equals?.let { put("equals", it) } }

    private fun field(f: TemplateField): JsonObject = buildJsonObject {
        put("key", f.key)
        put("label", f.label)
        put("type", f.type.code)
        if (f.required) put("required", true)
        f.default?.let { put("default", it) }
        f.placeholder?.let { put("placeholder", it) }
        f.help?.let { put("help", it) }
        if (f.options.isNotEmpty()) put("options", buildJsonArray { f.options.forEach { add(JsonPrimitive(it)) } })
        f.min?.let { put("min", it.toPlainString()) }
        f.max?.let { put("max", it.toPlainString()) }
        f.maxLength?.let { put("maxLength", it) }
        f.currency?.let { put("currency", it) }
        f.visibleIf?.let { put("visibleIf", cond(it)) }
    }

    class DecodeException(message: String) : Exception(message)

    fun decode(text: String): Result<TemplateDefinition> = runCatching {
        if (text.length > MAX_BYTES) throw DecodeException("Template file is too large")
        val root = try { Json.parseToJsonElement(text) } catch (e: Exception) { throw DecodeException("Not valid JSON") }
        fromJson(root as? JsonObject ?: throw DecodeException("Expected a JSON object"))
    }

    fun fromJson(o: JsonObject): TemplateDefinition {
        val format = o.str("format")
        if (format != null && format != FORMAT) throw DecodeException("Not a Margin template (format=$format)")
        val fv = o["formatVersion"]?.jsonPrimitive?.intOrNull ?: 1
        if (fv > FORMAT_VERSION) throw DecodeException("Template was created by a newer version of the app")
        return TemplateDefinition(
            id = o.str("id") ?: throw DecodeException("Missing id"),
            version = o["version"]?.jsonPrimitive?.intOrNull ?: 1,
            name = o.str("name") ?: throw DecodeException("Missing name"),
            description = o.str("description").orEmpty(),
            category = o.str("category") ?: "Custom",
            subcategory = o.str("subcategory"),
            icon = o.str("icon") ?: "note",
            color = o["color"]?.jsonPrimitive?.longOrNull,
            intendedUse = o.str("intendedUse"),
            titlePattern = o.str("titlePattern") ?: "{{name}}",
            defaultTags = o.arr("defaultTags").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            sections = o.arr("sections").map { sectionFrom(it.asObj("section")) },
            sensitive = o["sensitive"]?.jsonPrimitive?.booleanOrNull ?: false,
            exportable = o["exportable"]?.jsonPrimitive?.booleanOrNull ?: true,
            builtIn = false,
            disclaimer = o.str("disclaimer"),
            staticBody = o.str("staticBody"),
        )
    }

    private fun sectionFrom(o: JsonObject): TemplateSection = TemplateSection(
        key = o.str("key") ?: throw DecodeException("Section missing key"),
        title = o.str("title").orEmpty(),
        description = o.str("description"),
        fields = o.arr("fields").map { fieldFrom(it.asObj("field")) },
        sections = o.arr("sections").map { sectionFrom(it.asObj("section")) },
        repeatable = o["repeatable"]?.jsonPrimitive?.booleanOrNull ?: false,
        minRepeat = o["minRepeat"]?.jsonPrimitive?.intOrNull ?: 1,
        maxRepeat = o["maxRepeat"]?.jsonPrimitive?.intOrNull ?: 20,
        visibleIf = (o["visibleIf"] as? JsonObject)?.let(::condFrom),
    )

    private fun condFrom(o: JsonObject) = Condition(o.str("field") ?: throw DecodeException("Condition missing field"), o.str("equals"))

    private fun fieldFrom(o: JsonObject): TemplateField {
        val typeCode = o.str("type") ?: throw DecodeException("Field missing type")
        return TemplateField(
            key = o.str("key") ?: throw DecodeException("Field missing key"),
            label = o.str("label") ?: throw DecodeException("Field missing label"),
            type = FieldType.fromCode(typeCode) ?: throw DecodeException("Unsupported field type '$typeCode'"),
            required = o["required"]?.jsonPrimitive?.booleanOrNull ?: false,
            default = o.str("default"),
            placeholder = o.str("placeholder"),
            help = o.str("help"),
            options = o.arr("options").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            min = o.str("min")?.toBigDecimalOrNull(),
            max = o.str("max")?.toBigDecimalOrNull() ?: if (typeCode == FieldType.RATING.code) BigDecimal(5) else null,
            maxLength = o["maxLength"]?.jsonPrimitive?.intOrNull,
            currency = o.str("currency"),
            visibleIf = (o["visibleIf"] as? JsonObject)?.let(::condFrom),
        )
    }

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.arr(k: String): JsonArray = (this[k] as? JsonArray) ?: JsonArray(emptyList())
    private fun JsonElement.asObj(what: String): JsonObject = this as? JsonObject ?: throw DecodeException("Expected $what object")
}
