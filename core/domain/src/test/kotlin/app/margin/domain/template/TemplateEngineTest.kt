package app.margin.domain.template

import app.margin.domain.text.Block
import app.margin.domain.text.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class TemplateEngineTest {
    private val now = LocalDateTime.of(2025, 3, 14, 9, 30)

    private val dsl = """
        === sample v3
        name: Sample
        category: Testing
        description: For tests.
        title: {{field:project}} — {{date}}
        tags: test, sample
        ## Basics
        > Fill this in.
        text project "Project" * ? "Name"
        select status "Status" [Green|Amber|Red] = Green
        long risk_note "Why not green?" if=status:Red
        number budget "Budget" min=0 max=1000
        rating confidence "Confidence"
        email owner_email "Owner email"
        url link "Link"
        checklist steps "Steps" = "One|Two"
        table people "People" [Name|Role]
        ### Nested
        progress done "Done" = 40
        ## Items +repeat
        text item "Item" *
        date due "Due" = today
    """.trimIndent()

    private val t = TemplateDsl.parseLibrary(dsl).single()

    @Test fun dslParsesStructure() {
        assertEquals("builtin.sample", t.id)
        assertEquals(3, t.version)
        assertEquals(listOf("test", "sample"), t.defaultTags)
        assertEquals(2, t.sections.size)
        assertEquals("Fill this in.", t.sections[0].description)
        assertEquals("nested", t.sections[0].sections.single().key)
        assertTrue(t.sections[1].repeatable)
        val status = t.allFields().first { it.key == "status" }
        assertEquals(listOf("Green", "Amber", "Red"), status.options)
        assertEquals("Green", status.default)
        assertEquals(Condition("status", "Red"), t.allFields().first { it.key == "risk_note" }.visibleIf)
        assertEquals(BigDecimal(5), t.allFields().first { it.key == "confidence" }.max)
        assertTrue(t.allFields().first { it.key == "project" }.required)
        assertTrue(TemplateValidator.validateSchema(t).isEmpty())
    }

    @Test fun dslReportsErrorsWithLineNumbers() {
        val bad = "=== x\nname: X\ncategory: C\ndescription: D\n## S\nwidget k \"Label\""
        try { TemplateDsl.parseLibrary(bad); throw AssertionError("expected failure") } catch (e: TemplateDsl.ParseException) {
            assertEquals(6, e.line)
        }
        try { TemplateDsl.parseLibrary("=== y\nname: Y\ndescription: D\n## S\ntext a \"A\""); throw AssertionError() } catch (e: TemplateDsl.ParseException) {
            assertTrue(e.message!!.contains("category"))
        }
        try { TemplateDsl.parseLibrary("=== z\nname: Z\ncategory: C\ndescription: D\n## S\ntext a \"A\" bogus"); throw AssertionError() } catch (e: TemplateDsl.ParseException) {
            assertTrue(e.message!!.contains("unrecognised"))
        }
    }

    @Test fun schemaValidationCatchesProblems() {
        val broken = t.copy(sections = t.sections + TemplateSection("basics", "Dup", fields = listOf(
            TemplateField("project", "Again", FieldType.TEXT),
            TemplateField("Bad Key", "x", FieldType.TEXT),
            TemplateField("sel", "Sel", FieldType.SINGLE_SELECT),
            TemplateField("n", "N", FieldType.NUMBER, min = BigDecimal.TEN, max = BigDecimal.ONE, default = "abc"),
            TemplateField("c", "C", FieldType.TEXT, visibleIf = Condition("nope", null)),
        )))
        val msgs = TemplateValidator.validateSchema(broken).map { it.path + ": " + it.message }
        assertTrue(msgs.any { it.contains("duplicate key") })
        assertTrue(msgs.any { it.contains("invalid key") })
        assertTrue(msgs.any { it.contains("select needs options") })
        assertTrue(msgs.any { it.contains("min > max") })
        assertTrue(msgs.any { it.contains("default 'abc'") })
        assertTrue(msgs.any { it.contains("unknown field 'nope'") })
        assertTrue(msgs.any { it.contains("duplicate section key") })
    }

    @Test fun initialValuesResolveDefaults() {
        val v = TemplateEngine.initialValues(t, now)
        assertEquals(FieldValue.Choices(listOf("Green")), v.values["status"])
        assertEquals(FieldValue.Number(BigDecimal(40)), v.values["done"])
        assertEquals(FieldValue.Checklist(listOf("One" to false, "Two" to false)), v.values["steps"])
        assertEquals(1, v.repeats["items"]!!.size)
        assertEquals(FieldValue.Date(LocalDate.of(2025, 3, 14)), v.repeats["items"]!![0]["due"])
    }

    @Test fun valueValidation() {
        val v0 = TemplateEngine.initialValues(t, now)
        val errs = TemplateValidator.validateValues(t, v0)
        assertTrue(errs.any { it.fieldKey == "project" && it.kind == ValueError.Kind.REQUIRED })
        assertTrue(errs.any { it.fieldKey == "item" && it.instance == 0 })
        val v = v0.copy(
            values = v0.values + mapOf(
                "project" to FieldValue.Text("Apollo"),
                "budget" to FieldValue.Number(BigDecimal(5000)),
                "owner_email" to FieldValue.Text("not-an-email"),
                "link" to FieldValue.Text("ftp://x"),
                "confidence" to FieldValue.Number(BigDecimal(9)),
            ),
            repeats = mapOf("items" to listOf(mapOf("item" to FieldValue.Text("a")))),
        )
        val kinds = TemplateValidator.validateValues(t, v).map { it.fieldKey to it.kind }.toSet()
        assertEquals(setOf(
            "budget" to ValueError.Kind.ABOVE_MAX,
            "owner_email" to ValueError.Kind.INVALID_EMAIL,
            "link" to ValueError.Kind.INVALID_URL,
            "confidence" to ValueError.Kind.ABOVE_MAX,
        ), kinds)
    }

    @Test fun hiddenConditionalFieldsAreNotRenderedOrRequired() {
        val required = t.copy(sections = t.sections.map { s -> s.copy(fields = s.fields.map { if (it.key == "risk_note") it.copy(required = true) else it }) })
        val v = TemplateEngine.initialValues(required, now).let { it.copy(values = it.values + ("project" to FieldValue.Text("P")), repeats = mapOf("items" to listOf(mapOf("item" to FieldValue.Text("i"))))) }
        assertTrue(TemplateValidator.validateValues(required, v).isEmpty())
        assertFalse(TemplateEngine.render(required, v, now).body.contains("Why not green"))
        val red = v.copy(values = v.values + ("status" to FieldValue.Choices(listOf("Red"))))
        assertTrue(TemplateValidator.validateValues(required, red).any { it.fieldKey == "risk_note" })
        assertTrue(TemplateEngine.render(required, red, now).body.contains("**Why not green?**"))
    }

    @Test fun renderProducesUsableMarkdown() {
        val v0 = TemplateEngine.initialValues(t, now)
        val v = v0.copy(
            values = v0.values + mapOf(
                "project" to FieldValue.Text("Apollo"),
                "confidence" to FieldValue.Number(BigDecimal(4)),
                "people" to FieldValue.Rows(listOf(listOf("Ana", "Lead"), listOf("Bo|b", ""))),
            ),
            repeats = mapOf("items" to listOf(mapOf("item" to FieldValue.Text("First")), mapOf("item" to FieldValue.Text("Second")))),
        )
        val r = TemplateEngine.render(t, v, now)
        assertEquals("Apollo — 2025-03-14", r.title)
        assertEquals(listOf("test", "sample"), r.tags)
        val body = r.body
        assertTrue(body.contains("## Basics"))
        assertTrue(body.contains("### Nested"))
        assertTrue(body.contains("**Project:** Apollo"))
        assertTrue(body.contains("**Confidence:** ★★★★☆ (4/5)"))
        assertTrue(body.contains("**Done:** 40%"))
        assertTrue(body.contains("- [ ] One\n- [ ] Two"))
        assertTrue(body.contains("## Items 1") && body.contains("## Items 2"))
        assertTrue(body.contains("| Bo\\|b |   |"))
        val blocks = MarkdownParser.parse(body)
        assertTrue(blocks.any { it is Block.Table })
        assertEquals(2, blocks.count { it is Block.ListItem && it.kind == Block.ListKind.TASK })
    }

    @Test fun titleFallsBackToName() {
        val r = TemplateEngine.renderTitle(t, FormValues(), now)
        assertEquals("2025-03-14", r)
        val t2 = t.copy(titlePattern = "{{field:project}}")
        assertEquals("Sample", TemplateEngine.renderTitle(t2, FormValues(), now))
    }

    @Test fun jsonRoundTrip() {
        val json = TemplateJson.encode(t)
        val back = TemplateJson.decode(json).getOrThrow()
        assertEquals(t.copy(builtIn = false, aliases = emptyList()), back)
    }

    @Test fun malformedJsonNeverThrows() {
        listOf("", "nope", "[]", "{}", "{\"id\":\"x\"}", "{\"format\":\"other\"}",
            "{\"id\":\"x\",\"name\":\"n\",\"sections\":[{\"key\":\"s\",\"fields\":[{\"key\":\"a\",\"label\":\"A\",\"type\":\"hologram\"}]}]}",
            "{\"id\":\"x\",\"name\":\"n\",\"formatVersion\":99,\"sections\":[]}",
            "{\"id\":\"x\",\"name\":\"n\",\"sections\":[42]}",
        ).forEach { assertTrue("should fail: $it", TemplateJson.decode(it).isFailure) }
        val ok = TemplateJson.decode("{\"id\":\"x\",\"name\":\"n\",\"sections\":[]}").getOrThrow()
        assertTrue(TemplateValidator.validateSchema(ok).isNotEmpty()) // decodes, but schema says it is unusable
    }

    @Test fun saveNoteAsTemplateAndDuplicate() {
        val tpl = TemplateCatalog.fromNote("Standup", "## Yesterday\n\n## Today {{date}}\n")
        assertTrue(TemplateValidator.validateSchema(tpl).isEmpty())
        val r = TemplateEngine.render(tpl, FormValues(), now)
        assertEquals("Standup", r.title)
        assertEquals("## Yesterday\n\n## Today 2025-03-14\n", r.body)
        val d = TemplateCatalog.duplicate(t)
        assertTrue(d.id.startsWith("user."))
        assertFalse(d.builtIn)
        assertEquals("Sample (copy)", d.name)
    }

    @Test fun overridesReplaceBuiltInsAndCanBeReset() {
        val override = t.copy(name = "My sample")
        val custom = TemplateCatalog.duplicate(t)
        val cat = TemplateCatalog(listOf(t), listOf(override, custom))
        val eff = cat.effective()
        assertEquals(listOf("My sample", "Sample (copy)"), eff.map { it.name })
        assertTrue(cat.isOverridden(t.id))
        val reset = TemplateCatalog(listOf(t), listOf(custom))
        assertEquals("Sample", reset.effective().first().name)
        assertTrue(TemplateCatalog.overrideIsStale(t.copy(version = 2), t))
    }

    @Test fun generatedNoteIsIndependentOfTemplate() {
        // Regression #6: editing the generated note must not change the template.
        val before = TemplateJson.encode(t)
        val r = TemplateEngine.render(t, TemplateEngine.initialValues(t, now), now)
        @Suppress("UNUSED_VARIABLE") val edited = r.body.replace("Basics", "Changed")
        assertEquals(before, TemplateJson.encode(t))
    }
}
