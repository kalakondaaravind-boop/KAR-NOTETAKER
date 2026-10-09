package app.margin.domain.io

import app.margin.domain.model.Folder
import app.margin.domain.model.Note
import app.margin.domain.organization.Folders
import app.margin.domain.text.HtmlRenderer
import app.margin.domain.text.MarkdownParser
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ExportFormat(val extension: String, val mime: String) {
    MARKDOWN("md", "text/markdown"),
    HTML("html", "text/html"),
    TEXT("txt", "text/plain"),
    JSON("json", "application/json"),
}

/** Context needed to export a note faithfully (tags and folder path are not part of the note row). */
data class NoteExportContext(val tags: List<String>, val folderPath: List<String>, val attachmentNames: List<String> = emptyList())

/**
 * What each format preserves (also in docs/DATA_FORMATS.md):
 *  - Markdown: body exactly, plus YAML front matter with id, title, dates, tags, folder, flags.
 *  - HTML: rendered formatting, title, dates, tags, folder; not re-importable losslessly.
 *  - Text: title and markup-free text only.
 *  - JSON (library): everything, including revisions, tasks and templates (lossless).
 */
object NoteExporter {

    fun markdown(n: Note, ctx: NoteExportContext): String = buildString {
        append("---\n")
        append("id: ").append(yaml(n.id)).append('\n')
        append("title: ").append(yaml(n.displayTitle())).append('\n')
        append("created: ").append(n.createdAt).append('\n')
        append("updated: ").append(n.updatedAt).append('\n')
        if (ctx.tags.isNotEmpty()) append("tags: [").append(ctx.tags.joinToString(", ") { yaml(it) }).append("]\n")
        if (ctx.folderPath.isNotEmpty()) append("folder: ").append(yaml(ctx.folderPath.joinToString("/"))).append('\n')
        if (n.pinned) append("pinned: true\n")
        if (n.favorite) append("favorite: true\n")
        if (n.state.name != "ACTIVE") append("state: ").append(n.state.name.lowercase()).append('\n')
        n.templateId?.let { append("template: ").append(yaml(it)).append('\n') }
        if (ctx.attachmentNames.isNotEmpty()) append("attachments: [").append(ctx.attachmentNames.joinToString(", ") { yaml(it) }).append("]\n")
        append("---\n\n")
        append(n.body)
        if (!n.body.endsWith("\n")) append('\n')
    }

    fun html(n: Note, ctx: NoteExportContext, zone: ZoneId): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone)
        val meta = buildString {
            append("<p class=\"meta\">Created ").append(fmt.format(n.createdAt)).append(" · Updated ").append(fmt.format(n.updatedAt))
            if (ctx.folderPath.isNotEmpty()) append(" · ").append(HtmlRenderer.escape(ctx.folderPath.joinToString(" / ")))
            if (ctx.tags.isNotEmpty()) append(" · ").append(ctx.tags.joinToString(" ") { "#" + HtmlRenderer.escape(it) })
            append("</p>")
        }
        return HtmlRenderer.document(n.displayTitle(), HtmlRenderer.renderBody(n.body), meta)
    }

    fun text(n: Note): String = n.displayTitle() + "\n\n" + MarkdownParser.toPlainText(n.body) + "\n"

    /** YAML scalar: quoted when needed so titles with ':' or '#' survive. */
    fun yaml(s: String): String {
        val needsQuote = s.isEmpty() || s.any { it in ":#[]{},&*!|>'\"%@`\n\r\t" } || s.first().isWhitespace() || s.last().isWhitespace() ||
            s.lowercase() in setOf("true", "false", "null", "yes", "no", "~") || s.toDoubleOrNull() != null
        if (!needsQuote) return s
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
    }

    private val unsafeFile = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    /** Filesystem-safe, length-limited file name; Windows reserved names avoided. */
    fun safeFileName(title: String, maxLength: Int = 80): String {
        var s = unsafeFile.replace(title, "_").trim().trim('.').replace(Regex("""\s+"""), " ")
        if (s.isEmpty()) s = "Untitled"
        if (s.uppercase().substringBefore('.') in setOf("CON", "PRN", "AUX", "NUL", "COM1", "COM2", "LPT1", "LPT2")) s = "_$s"
        return s.take(maxLength).trimEnd('.', ' ')
    }

    /** Assigns unique relative paths "Folder/Sub/Title.md", adding " (2)" on collisions. */
    fun assignPaths(notes: List<Note>, folders: List<Folder>, ext: String): Map<String, String> {
        val byId = folders.associateBy { it.id }
        val used = HashSet<String>()
        val out = LinkedHashMap<String, String>()
        for (n in notes.sortedBy { it.createdAt }) {
            val dir = Folders.path(n.folderId, byId).joinToString("/") { safeFileName(it.name) }
            val base = safeFileName(n.displayTitle())
            var candidate = (if (dir.isEmpty()) "" else "$dir/") + "$base.$ext"
            var i = 2
            while (!used.add(candidate.lowercase())) {
                candidate = (if (dir.isEmpty()) "" else "$dir/") + "$base ($i).$ext"; i++
            }
            out[n.id] = candidate
        }
        return out
    }
}
