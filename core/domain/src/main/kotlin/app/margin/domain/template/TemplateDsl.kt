package app.margin.domain.template

import java.math.BigDecimal

/**
 * Compact text format for the built-in, version-controlled template library
 * (core/domain/src/main/resources/templates/{@literal *}.tpl). Grammar:
 *
 * ```
 * === slug [vN]
 * name: Daily journal
 * category: Journaling and reflection
 * subcategory: Daily
 * icon: book
 * description: One line.
 * use: When to use it.
 * title: Journal — {{date}}
 * tags: journal, daily
 * aliases: Morning pages, Journal entry
 * disclaimer: Not medical advice.
 * private: true                      (sensitive: generated notes are excluded from AI context by default)
 * ## Section title [+repeat] [if=field:value]
 * > Section guidance text
 * <type> <key> "Label" [*] [= default] [? "placeholder"] [[a|b|c]] [min=N] [max=N] [cur=EUR] [if=field:value] [! "help"]
 * ### Nested section …
 * ```
 * Lines starting with '#!' are comments. Errors carry the line number.
 */
object TemplateDsl {

    class ParseException(val line: Int, message: String) : Exception("line $line: $message")

    fun parseLibrary(text: String, sourceName: String = "templates"): List<TemplateDefinition> {
        val lines = text.replace("\r\n", "\n").split('\n')
        val chunks = ArrayList<Pair<Int, List<String>>>()
        var current: MutableList<String>? = null
        var start = 0
        lines.forEachIndexed { i, l ->
            if (l.startsWith("=== ")) {
                current?.let { chunks += start to it }
                current = mutableListOf(l); start = i + 1
            } else current?.add(l)
        }
        current?.let { chunks += start to it }
        return chunks.map { (lineNo, chunk) ->
            try { parseOne(chunk, lineNo) } catch (e: ParseException) { throw ParseException(e.line, "$sourceName: ${e.message}") }
        }
    }

    private class SectionBuilder(val key: String, val title: String, val level: Int, val repeatable: Boolean, val visibleIf: Condition?) {
        var description: String? = null
        val fields = ArrayList<TemplateField>()
        val children = ArrayList<SectionBuilder>()
        fun build(): TemplateSection = TemplateSection(key, title, description, fields.toList(), children.map { it.build() }, repeatable, visibleIf = visibleIf)
    }

    private fun parseOne(chunk: List<String>, firstLine: Int): TemplateDefinition {
        val header = chunk.first().removePrefix("=== ").trim().split(Regex("""\s+"""))
        val slug = header[0]
        if (!Regex("""[a-z0-9][a-z0-9-]*""").matches(slug)) throw ParseException(firstLine, "bad slug '$slug'")
        val version = header.getOrNull(1)?.removePrefix("v")?.toIntOrNull() ?: 1
        val meta = HashMap<String, String>()
        val roots = ArrayList<SectionBuilder>()
        val stack = ArrayList<SectionBuilder>()
        val usedKeys = HashSet<String>()

        fun ensureSection(lineNo: Int): SectionBuilder {
            if (stack.isEmpty()) {
                val s = SectionBuilder("main", "", 2, false, null)
                roots += s; stack += s
            }
            return stack.last()
        }

        chunk.drop(1).forEachIndexed { idx, raw ->
            val lineNo = firstLine + idx + 1
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("#!")) return@forEachIndexed
            val hm = Regex("""^(#{2,4})\s+(.+)$""").matchEntire(line)
            if (hm != null) {
                val level = hm.groupValues[1].length
                var rest = hm.groupValues[2]
                val repeat = rest.contains("+repeat")
                rest = rest.replace("+repeat", "")
                val cond = Regex("""\bif=(\w+):([^\s]*)""").find(rest)?.let { m ->
                    rest = rest.replace(m.value, ""); Condition(m.groupValues[1], m.groupValues[2].ifEmpty { null })
                }
                val title = rest.trim()
                var key = slugify(title).ifEmpty { "section" }
                while (!usedKeys.add("s:$key")) key += "_"
                val sb = SectionBuilder(key, title, level, repeat, cond)
                while (stack.isNotEmpty() && stack.last().level >= level) stack.removeAt(stack.lastIndex)
                if (stack.isEmpty()) {
                    if (level != 2) throw ParseException(lineNo, "nested section without parent")
                    roots += sb
                } else stack.last().children += sb
                stack += sb
                return@forEachIndexed
            }
            if (line.startsWith("> ")) {
                val s = ensureSection(lineNo)
                s.description = listOfNotNull(s.description, line.removePrefix("> ").trim()).joinToString(" ")
                return@forEachIndexed
            }
            val mm = Regex("""^([a-z]+):\s*(.*)$""").matchEntire(line)
            if (mm != null && FieldType.fromCode(mm.groupValues[1]) == null && stack.isEmpty()) {
                meta[mm.groupValues[1]] = mm.groupValues[2].trim()
                return@forEachIndexed
            }
            val field = parseField(line, lineNo)
            if (!usedKeys.add("f:${field.key}")) throw ParseException(lineNo, "duplicate field key '${field.key}'")
            ensureSection(lineNo).fields += field
        }

        fun req(k: String) = meta[k] ?: throw ParseException(firstLine, "template '$slug' missing '$k:'")
        return TemplateDefinition(
            id = "builtin.$slug",
            version = version,
            name = req("name"),
            description = req("description"),
            category = req("category"),
            subcategory = meta["subcategory"],
            icon = meta["icon"] ?: "note",
            intendedUse = meta["use"],
            titlePattern = meta["title"] ?: "{{name}}",
            defaultTags = meta["tags"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            sections = roots.map { it.build() },
            sensitive = meta["private"]?.toBooleanStrictOrNull() ?: false,
            builtIn = true,
            disclaimer = meta["disclaimer"],
            aliases = meta["aliases"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
        )
    }

    private val fieldRe = Regex("""^([a-z]+)\s+([a-z][a-z0-9_]*)\s+"([^"]*)"(.*)$""")

    private fun parseField(line: String, lineNo: Int): TemplateField {
        val m = fieldRe.matchEntire(line.trim()) ?: throw ParseException(lineNo, "cannot parse field: $line")
        val type = FieldType.fromCode(m.groupValues[1]) ?: throw ParseException(lineNo, "unknown field type '${m.groupValues[1]}'")
        var rest = m.groupValues[4]
        fun take(re: Regex): MatchResult? = re.find(rest)?.also { rest = rest.removeRange(it.range) }
        val help = take(Regex("""!\s*"([^"]*)""""))?.groupValues?.get(1)
        val placeholder = take(Regex("""\?\s*"([^"]*)""""))?.groupValues?.get(1)
        val default = take(Regex("""=\s*"([^"]*)""""))?.groupValues?.get(1)
            ?: take(Regex("""(?<![a-z])=\s*([^\s\[]+)"""))?.groupValues?.get(1)
        val options = take(Regex("""\[([^\]]*)]"""))?.groupValues?.get(1)?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val min = take(Regex("""\bmin=(-?[\d.]+)"""))?.groupValues?.get(1)?.let { BigDecimal(it) }
        val max = take(Regex("""\bmax=(-?[\d.]+)"""))?.groupValues?.get(1)?.let { BigDecimal(it) }
        val len = take(Regex("""\blen=(\d+)"""))?.groupValues?.get(1)?.toInt()
        val cur = take(Regex("""\bcur=([A-Z]{3})"""))?.groupValues?.get(1)
        val cond = take(Regex("""\bif=(\w+):([^\s]*)"""))?.let { Condition(it.groupValues[1], it.groupValues[2].ifEmpty { null }) }
        val required = take(Regex("""(^|\s)\*(\s|$)""")) != null
        if (rest.isNotBlank()) throw ParseException(lineNo, "unrecognised field options '${rest.trim()}'")
        return TemplateField(
            key = m.groupValues[2], label = m.groupValues[3], type = type, required = required, default = default,
            placeholder = placeholder, help = help, options = options, min = min,
            max = max ?: if (type == FieldType.RATING) BigDecimal(5) else null,
            maxLength = len, currency = cur, visibleIf = cond,
        )
    }

    fun slugify(s: String): String = s.lowercase().replace(Regex("""[^a-z0-9]+"""), "_").trim('_').take(40)
}
