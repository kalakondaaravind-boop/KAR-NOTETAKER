package app.margin.domain.model

import app.margin.domain.text.NoteFont

/** Paper backgrounds for notes and drawings. Geometry (spacing in dp) is shared by the editor and the canvas. */
enum class PageStyle(val id: String, val label: String, val spacingDp: Float) {
    PLAIN("plain", "Plain", 0f),
    RULED("ruled", "Ruled (wide)", 32f),
    RULED_NARROW("ruled-narrow", "Ruled (narrow)", 26f),
    GRID("grid", "Squared", 24f),
    GRID_SMALL("grid-small", "Graph paper", 12f),
    DOTTED("dotted", "Dot grid", 24f),
    CORNELL("cornell", "Cornell notes", 32f),
    TODO("todo", "To-do list", 40f),
    MUSIC("music", "Music staff", 10f),
    STORYBOARD("storyboard", "Storyboard", 0f);

    /** Lines are drawn in the page body; Cornell adds a cue column (fraction of width) and a summary band. */
    val hasMargin: Boolean get() = this == RULED || this == RULED_NARROW || this == CORNELL || this == TODO

    companion object {
        fun from(id: String?): PageStyle = entries.firstOrNull { it.id == id?.trim()?.lowercase() } ?: PLAIN
        const val CORNELL_CUE_FRACTION = 0.28f
        const val CORNELL_SUMMARY_FRACTION = 0.18f
    }
}

/**
 * Per-note presentation stored as a hidden first-line comment in the Markdown body:
 *   <!-- kar:page=ruled;font=lora -->
 * Keeps the database schema, backups and exports unchanged, and other Markdown apps simply ignore it.
 */
data class NoteMeta(val page: PageStyle = PageStyle.PLAIN, val font: NoteFont? = null) {
    val isDefault: Boolean get() = page == PageStyle.PLAIN && font == null

    fun directive(): String = "<!-- kar:" + listOfNotNull(
        "page=${page.id}".takeIf { page != PageStyle.PLAIN },
        font?.let { "font=${it.id}" },
    ).joinToString(";") + " -->"

    companion object {
        private val re = Regex("""^<!--\s*kar:([^\n]*?)\s*-->[ \t]*(\n|$)""")

        /** Splits a stored body into its meta and the content the user edits. */
        fun split(body: String): Pair<NoteMeta, String> {
            val m = re.find(body) ?: return NoteMeta() to body
            if (m.range.first != 0) return NoteMeta() to body
            var page = PageStyle.PLAIN
            var font: NoteFont? = null
            m.groupValues[1].split(';').forEach { kv ->
                val k = kv.substringBefore('=').trim()
                val v = kv.substringAfter('=', "").trim()
                when (k) { "page" -> page = PageStyle.from(v); "font" -> font = NoteFont.from(v) }
            }
            return NoteMeta(page, font) to body.substring(m.range.last + 1)
        }

        fun isDirectiveLine(line: String): Boolean = re.find(line)?.range?.first == 0

        /** Inverse of [split]; default meta adds nothing, so ordinary notes stay plain Markdown. */
        fun join(meta: NoteMeta, content: String): String = if (meta.isDefault) content else meta.directive() + "\n" + content
    }
}

/** "New page" templates: a paper style plus optional starter content. */
data class PageTemplate(val id: String, val name: String, val description: String, val page: PageStyle, val starter: String, val drawing: Boolean = false)

object PageTemplates {
    val all: List<PageTemplate> = listOf(
        PageTemplate("blank", "Blank page", "Plain paper for anything.", PageStyle.PLAIN, ""),
        PageTemplate("ruled", "Ruled page", "Wide-ruled lines for handwriting-style notes.", PageStyle.RULED, ""),
        PageTemplate("ruled-narrow", "Narrow ruled", "College-ruled lines, more text per page.", PageStyle.RULED_NARROW, ""),
        PageTemplate("squared", "Squared page", "5 mm squares for maths, layouts and tables.", PageStyle.GRID, ""),
        PageTemplate("dotted", "Dot grid", "Bullet-journal dots.", PageStyle.DOTTED, ""),
        PageTemplate("todo", "To-do list", "A checklist ready to fill in.", PageStyle.TODO,
            "# To do\n\n- [ ] \n- [ ] \n- [ ] \n- [ ] \n- [ ] \n"),
        PageTemplate("cornell", "Cornell notes", "Cues, notes and a summary, the classic study layout.", PageStyle.CORNELL,
            "# Topic\n\n## Cues / questions\n- \n\n## Notes\n- \n\n## Summary\n"),
        PageTemplate("daily-planner", "Daily planner", "Priorities, schedule and notes for one day.", PageStyle.RULED,
            "# Today\n\n## Top 3\n- [ ] \n- [ ] \n- [ ] \n\n## Schedule\n" +
                (8..18).joinToString("") { "- %02d:00 \n".format(it) } + "\n## Notes\n"),
        PageTemplate("weekly-planner", "Weekly planner", "One section per day.", PageStyle.RULED,
            "# Week of \n\n" + listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").joinToString("") { "## $it\n- [ ] \n\n" }),
        PageTemplate("meeting-page", "Meeting page", "Attendees, agenda, notes and actions.", PageStyle.RULED_NARROW,
            "# Meeting\n\n**Date:** \n**Attendees:** \n\n## Agenda\n1. \n\n## Notes\n- \n\n## Actions\n- [ ] \n"),
        PageTemplate("sketch", "Sketch page", "A blank drawing canvas.", PageStyle.PLAIN, "", drawing = true),
        PageTemplate("graph-sketch", "Graph paper sketch", "Drawing canvas on graph paper.", PageStyle.GRID_SMALL, "", drawing = true),
        PageTemplate("music", "Music staff", "Staff lines for notation by hand.", PageStyle.MUSIC, "", drawing = true),
        PageTemplate("storyboard", "Storyboard", "Six frames for sketching a sequence.", PageStyle.STORYBOARD, "", drawing = true),
    )

    fun byId(id: String): PageTemplate? = all.firstOrNull { it.id == id }
}
