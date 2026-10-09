package app.margin.domain.template

import app.margin.domain.text.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Verifies every advertised built-in template: it parses, its schema is valid, it opens with
 * defaults, it accepts a fully filled form, and it renders a non-trivial, parseable note.
 * Also produces docs/TEMPLATE_INVENTORY.md when MARGIN_INVENTORY_OUT is set.
 */
class BuiltInTemplatesTest {
    private val now = LocalDateTime.of(2025, 6, 2, 8, 0)

    private fun sample(f: TemplateField): FieldValue = when (f.type) {
        FieldType.TEXT, FieldType.LONG_TEXT, FieldType.RICH_TEXT -> FieldValue.Text("Sample ${f.key}")
        FieldType.NUMBER, FieldType.CURRENCY -> FieldValue.Number(f.min ?: f.max ?: BigDecimal.ONE)
        FieldType.RATING -> FieldValue.Number(BigDecimal(3).min(f.max ?: BigDecimal(5)))
        FieldType.PROGRESS -> FieldValue.Number(BigDecimal(50))
        FieldType.DATE -> FieldValue.Date(LocalDate.of(2025, 6, 2))
        FieldType.TIME -> FieldValue.Time(LocalTime.of(9, 0))
        FieldType.DATETIME -> FieldValue.DateTime(now)
        FieldType.CHECKBOX -> FieldValue.Bool(true)
        FieldType.CHECKLIST -> FieldValue.Checklist(listOf("Step one" to true, "Step two" to false))
        FieldType.SINGLE_SELECT -> FieldValue.Choices(listOf(f.options.first()))
        FieldType.MULTI_SELECT -> FieldValue.Choices(f.options.take(2))
        FieldType.URL -> FieldValue.Text("https://example.org/${f.key}")
        FieldType.EMAIL -> FieldValue.Text("someone@example.org")
        FieldType.LIST -> FieldValue.Items(listOf("Item A", "Item B"))
        FieldType.TABLE -> FieldValue.Rows(listOf(f.options.map { "$it 1" }))
    }

    @Test fun everyBuiltInTemplateIsValidAndUsable() {
        val all = BuiltInTemplates.all
        assertTrue("library should not be empty", all.isNotEmpty())
        assertEquals("duplicate ids", all.size, all.map { it.id }.toSet().size)
        assertEquals("duplicate names", all.size, all.map { it.name.lowercase() }.toSet().size)
        val categories = BuiltInTemplates.catalogue.map { it.first }.toSet()
        val problems = ArrayList<String>()
        fun check(cond: Boolean, msg: () -> String) { if (!cond) problems += msg() }
        for (t in all) {
            val ctx = t.id
            try {
                check(t.category in categories) { "$ctx unknown category '${t.category}'" }
                val schema = TemplateValidator.validateSchema(t)
                check(schema.isEmpty()) { "$ctx schema: $schema" }
                if (schema.isNotEmpty()) continue
                check(t.description.length >= 10) { "$ctx needs a description" }
                check(t.allFields().size >= 2) { "$ctx should have at least 2 fields" }
                // Opens with defaults.
                val initial = TemplateEngine.initialValues(t, now)
                val r0 = TemplateEngine.render(t, initial, now)
                check(r0.title.isNotBlank()) { "$ctx empty title" }
                check(r0.body.length > 20) { "$ctx trivial body" }
                MarkdownParser.parse(r0.body)
                // Fully filled form validates and renders every value.
                val filled = FormValues(
                    values = t.allSections().filterNot { it.repeatable }.flatMap { it.fields }.associate { it.key to sample(it) },
                    repeats = t.allSections().filter { it.repeatable }.associate { s -> s.key to listOf(s.fields.associate { it.key to sample(it) }) },
                )
                val ve = TemplateValidator.validateValues(t, filled)
                check(ve.isEmpty()) { "$ctx values: $ve" }
                val r = TemplateEngine.render(t, filled, now)
                t.allFields().filter { it.type == FieldType.TEXT && it.visibleIf == null }.forEach { f ->
                    check(r.body.contains("Sample ${f.key}") || r.title.contains("Sample ${f.key}")) { "$ctx missing ${f.key}" }
                }
                // JSON export round trip keeps the definition.
                check(t.copy(builtIn = false, aliases = emptyList()) == TemplateJson.decode(TemplateJson.encode(t)).getOrThrow()) { "$ctx json round trip" }
            } catch (e: Exception) {
                problems += "$ctx threw $e"
            }
        }
        assertTrue("${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun catalogueInventory() {
        val rows = ArrayList<String>()
        var implemented = 0
        var deferred = 0
        for ((category, names) in BuiltInTemplates.catalogue) {
            rows += "\n## $category\n\n| Catalogue entry | Status | Implemented by |\n|---|---|---|"
            for (n in names) {
                val t = BuiltInTemplates.resolve(n, category)
                if (t != null) { implemented++; rows += "| $n | VERIFIED | `${t.id}` — ${t.name} |" }
                else { deferred++; rows += "| $n | DEFERRED | — |" }
            }
        }
        val total = implemented + deferred
        println("template catalogue: $implemented/$total entries covered by ${BuiltInTemplates.all.size} templates; $deferred deferred")
        System.getenv("MARGIN_INVENTORY_OUT")?.let { path ->
            File(path).writeText(buildString {
                append("# Template inventory\n\n")
                append("Generated by `BuiltInTemplatesTest.catalogueInventory`. Do not edit by hand.\n\n")
                append("- Built-in templates: **${BuiltInTemplates.all.size}**\n")
                append("- Catalogue entries (spec §12.2): **$total**, covered: **$implemented**, deferred: **$deferred**\n")
                append("- `VERIFIED` means the implementing template passed `everyBuiltInTemplateIsValidAndUsable` ")
                append("(schema valid, opens with defaults, accepts a full form, renders parseable Markdown, JSON round-trips). ")
                append("Several catalogue entries that describe the same structure resolve to one template via aliases.\n")
                rows.forEach { append(it).append('\n') }
            })
        }
        // Every catalogue category has at least one implemented template.
        BuiltInTemplates.catalogue.forEach { (c, names) -> assertTrue("no template for $c", names.any { BuiltInTemplates.resolve(it, c) != null }) }
    }
}
