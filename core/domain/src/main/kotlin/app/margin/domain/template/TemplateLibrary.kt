package app.margin.domain.template

import app.margin.domain.model.Ids

/**
 * Built-in templates are versioned resources under /templates (one .tpl file per category,
 * listed in /templates/index.txt). User templates (custom, duplicated, or overrides of a
 * built-in) are stored in the database as JSON and merged here.
 */
object BuiltInTemplates {
    private const val INDEX = "/templates/index.txt"

    /** Loaded once; a malformed resource fails loudly in tests (BuiltInTemplatesTest), never at runtime for users. */
    val all: List<TemplateDefinition> by lazy { load() }

    fun byId(id: String): TemplateDefinition? = all.firstOrNull { it.id == id }

    private fun read(path: String): String =
        BuiltInTemplates::class.java.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("missing resource $path")

    private fun load(): List<TemplateDefinition> =
        read(INDEX).lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap { file -> TemplateDsl.parseLibrary(read("/templates/$file"), file) }

    /** The requested catalogue (docs/TEMPLATE_INVENTORY.md is generated from this). */
    val catalogue: List<Pair<String, List<String>>> by lazy {
        val out = ArrayList<Pair<String, MutableList<String>>>()
        read("/templates/catalogue.txt").lines().forEach { raw ->
            val l = raw.trim()
            when {
                l.isEmpty() || l.startsWith("#!") -> Unit
                l.startsWith("## ") -> out += l.removePrefix("## ").trim() to mutableListOf()
                else -> out.lastOrNull()?.second?.add(l)
            }
        }
        out
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim()

    /** Resolves a catalogue entry to the template that implements it (by name or alias), or null if deferred. */
    fun resolve(catalogueName: String, category: String? = null): TemplateDefinition? {
        val n = norm(catalogueName)
        val candidates = all.filter { t -> norm(t.name) == n || t.aliases.any { norm(it) == n } }
        return candidates.firstOrNull { category != null && it.category == category } ?: candidates.firstOrNull()
    }
}

class TemplateCatalog(
    private val builtIns: List<TemplateDefinition>,
    /** Custom templates and overrides from storage. An override has the built-in's id. */
    private val user: List<TemplateDefinition>,
) {
    /** Effective list: overrides replace built-ins; custom templates follow. */
    fun effective(): List<TemplateDefinition> {
        val overrides = user.associateBy { it.id }
        val merged = builtIns.map { overrides[it.id]?.copy(builtIn = true) ?: it }
        val custom = user.filter { u -> builtIns.none { it.id == u.id } }
        return merged + custom
    }

    fun isOverridden(id: String) = builtIns.any { it.id == id } && user.any { it.id == id }

    fun categories(): List<String> = effective().map { it.category }.distinct()

    fun search(query: String): List<TemplateDefinition> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return effective()
        return effective().filter { t ->
            t.name.lowercase().contains(q) || t.description.lowercase().contains(q) || t.category.lowercase().contains(q) ||
                t.aliases.any { it.lowercase().contains(q) } || (t.subcategory?.lowercase()?.contains(q) ?: false)
        }.sortedBy { if (it.name.lowercase().startsWith(q)) 0 else 1 }
    }

    companion object {
        /** A user-owned copy with a new id. */
        fun duplicate(t: TemplateDefinition, newName: String = "${t.name} (copy)"): TemplateDefinition =
            t.copy(id = "user." + Ids.newId(), name = newName, builtIn = false, version = 1)

        /** "Save note as template": the note body becomes the template's static body. */
        fun fromNote(title: String, body: String, category: String = "Custom"): TemplateDefinition = TemplateDefinition(
            id = "user." + Ids.newId(), version = 1, name = title.ifBlank { "My template" },
            description = "Created from a note", category = category, titlePattern = title.ifBlank { "{{name}}" },
            sections = emptyList(), staticBody = body,
        )

        /** When a built-in's version increases, an override based on an older version is flagged for review. */
        fun overrideIsStale(override: TemplateDefinition, builtIn: TemplateDefinition) = override.version < builtIn.version
    }
}
