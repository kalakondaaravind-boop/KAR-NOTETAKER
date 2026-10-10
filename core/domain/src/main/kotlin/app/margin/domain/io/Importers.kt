package app.margin.domain.io

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** A note parsed from an external file, before it is written to the database. */
data class ImportCandidate(
    val sourceName: String,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val folderPath: List<String> = emptyList(),
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    /** Original id when re-importing a Margin export (used for duplicate detection). */
    val originalId: String? = null,
    /** Files carried with the note (e.g. images from a Word document); stored as attachments. */
    val media: List<ImportMedia> = emptyList(),
)

sealed interface ImportIssue {
    val sourceName: String
    data class Skipped(override val sourceName: String, val reason: SkipReason) : ImportIssue
    data class Failed(override val sourceName: String, val reason: FailReason, val detail: String? = null) : ImportIssue
}

enum class SkipReason { DUPLICATE, EMPTY }
enum class FailReason { TOO_LARGE, BINARY_CONTENT, UNSUPPORTED_TYPE, MALFORMED, ENCODING }

data class ImportReport(val imported: Int, val issues: List<ImportIssue>) {
    val skipped get() = issues.filterIsInstance<ImportIssue.Skipped>()
    val failed get() = issues.filterIsInstance<ImportIssue.Failed>()
}

enum class DuplicatePolicy { SKIP, KEEP_BOTH }

object ImportLimits {
    const val MAX_TEXT_BYTES = 10L * 1024 * 1024
    const val MAX_FILES = 5_000
    const val MAX_ATTACHMENT_BYTES = 200L * 1024 * 1024
    const val MAX_TABLE_ROWS = 20_000
}

object TextDecoding {
    /**
     * Decodes UTF-8 (BOM stripped). Rejects content that looks binary (NUL bytes) so a mislabelled
     * image or archive is never imported as a garbled note.
     */
    fun decode(bytes: ByteArray): Result<String> {
        if (bytes.size > ImportLimits.MAX_TEXT_BYTES) return Result.failure(ImportFailure(FailReason.TOO_LARGE))
        val sample = bytes.take(8192)
        if (sample.any { it == 0.toByte() }) return Result.failure(ImportFailure(FailReason.BINARY_CONTENT))
        var start = 0
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) start = 3
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            Result.success(decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString().replace("\r\n", "\n"))
        } catch (e: CharacterCodingException) {
            // Fallback: ISO-8859-1 never fails; used for legacy text files.
            Result.success(String(bytes, start, bytes.size - start, Charsets.ISO_8859_1).replace("\r\n", "\n"))
        }
    }
}

class ImportFailure(val reason: FailReason, detail: String? = null) : Exception(detail ?: reason.name)

object MarkdownImporter {
    data class FrontMatter(val values: Map<String, String>, val lists: Map<String, List<String>>, val body: String)

    /** Parses a YAML front-matter block (flat key: value and [a, b] / "- item" lists). Unknown keys are ignored. */
    fun frontMatter(text: String): FrontMatter {
        if (!text.startsWith("---\n")) return FrontMatter(emptyMap(), emptyMap(), text)
        val end = text.indexOf("\n---", 4)
        if (end < 0) return FrontMatter(emptyMap(), emptyMap(), text)
        val afterEnd = text.indexOf('\n', end + 4).let { if (it < 0) text.length else it + 1 }
        val values = LinkedHashMap<String, String>()
        val lists = LinkedHashMap<String, List<String>>()
        var currentListKey: String? = null
        for (line in text.substring(4, end).split('\n')) {
            val item = Regex("""^\s+-\s+(.*)$""").matchEntire(line)
            if (item != null && currentListKey != null) {
                lists[currentListKey] = lists[currentListKey].orEmpty() + unquote(item.groupValues[1]); continue
            }
            val m = Regex("""^([A-Za-z_][\w-]*):\s*(.*)$""").matchEntire(line) ?: continue
            val key = m.groupValues[1].lowercase()
            val v = m.groupValues[2].trim()
            currentListKey = null
            when {
                v.isEmpty() -> currentListKey = key
                v.startsWith("[") && v.endsWith("]") -> lists[key] = splitInline(v.substring(1, v.length - 1))
                else -> values[key] = unquote(v)
            }
        }
        return FrontMatter(values, lists, text.substring(afterEnd).trimStart('\n'))
    }

    private fun splitInline(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                quote != null && c == '\\' && i + 1 < s.length -> { cur.append(c).append(s[i + 1]); i++ }
                quote != null && c == quote -> { quote = null; cur.append(c) }
                quote == null && (c == '"' || c == '\'') -> { quote = c; cur.append(c) }
                quote == null && c == ',' -> { out += unquote(cur.toString().trim()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        if (cur.isNotBlank()) out += unquote(cur.toString().trim())
        return out.filter { it.isNotEmpty() }
    }

    fun unquote(v: String): String {
        val t = v.trim()
        if (t.length >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length - 1).replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\")
        }
        if (t.length >= 2 && t.startsWith("'") && t.endsWith("'")) return t.substring(1, t.length - 1).replace("''", "'")
        return t
    }

    private fun instant(v: String?): Instant? {
        if (v.isNullOrBlank()) return null
        return try { Instant.parse(v) } catch (e: DateTimeParseException) {
            try { LocalDate.parse(v.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC) } catch (e2: DateTimeParseException) { null }
        }
    }

    fun parse(fileName: String, text: String, relativeDir: List<String> = emptyList()): ImportCandidate {
        val fm = frontMatter(text)
        val title = fm.values["title"] ?: Regex("""^#\s+(.+)$""", RegexOption.MULTILINE).find(fm.body.take(2000))?.groupValues?.get(1)?.trim()
            ?: fileName.substringBeforeLast('.')
        val folder = fm.values["folder"]?.split('/')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: relativeDir
        return ImportCandidate(
            sourceName = fileName, title = title, body = fm.body,
            tags = (fm.lists["tags"] ?: fm.values["tags"]?.split(',')?.map { it.trim() } ?: emptyList()).filter { it.isNotBlank() },
            folderPath = folder,
            createdAt = instant(fm.values["created"] ?: fm.values["date"]), updatedAt = instant(fm.values["updated"] ?: fm.values["modified"]),
            pinned = fm.values["pinned"] == "true", favorite = fm.values["favorite"] == "true",
            originalId = fm.values["id"],
        )
    }
}

object PlainTextImporter {
    fun parse(fileName: String, text: String, relativeDir: List<String> = emptyList()) =
        ImportCandidate(sourceName = fileName, title = fileName.substringBeforeLast('.'), body = text, folderPath = relativeDir)
}

/**
 * Minimal HTML → Markdown conversion for imported pages and HTML exports. Handles headings,
 * paragraphs, line breaks, lists, emphasis, code, links and quotes; scripts/styles are dropped.
 * Anything else becomes plain text. It never executes or fetches content.
 */
object HtmlImporter {
    private val entities = mapOf("&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&nbsp;" to " ")

    fun decodeEntities(s: String): String {
        var out = Regex("""&#(\d{1,7});""").replace(s) { m -> m.groupValues[1].toIntOrNull()?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: "" }
        out = Regex("""&#x([0-9a-fA-F]{1,6});""").replace(out) { m -> m.groupValues[1].toIntOrNull(16)?.takeIf { it in 1..0x10FFFF }?.let { String(Character.toChars(it)) } ?: "" }
        entities.forEach { (k, v) -> out = out.replace(k, v) }
        return out
    }

    fun toMarkdown(html: String): String {
        var s = html.replace("\r\n", "\n")
        s = s.replace(Regex("""(?is)<(script|style|head|noscript|iframe|object)[^>]*>.*?</\1>"""), "")
        s = s.replace(Regex("""(?s)<!--.*?-->"""), "")
        s = s.replace(Regex("""(?is)<p[^>]*class=["']meta["'][^>]*>.*?</p>"""), "") // Margin export metadata line
        // Code is protected from the generic tag stripping below and restored verbatim at the end.
        val protectedCode = ArrayList<String>()
        fun protect(text: String): String { protectedCode += text; return "\uE002${protectedCode.size - 1}\uE003" }
        s = Regex("""(?is)<pre[^>]*>(?:\s*<code[^>]*>)?(.*?)(?:</code>\s*)?</pre>""").replace(s) { m ->
            "\n" + protect("```\n" + decodeEntities(m.groupValues[1].replace(Regex("<[^>]+>"), "")).trim('\n') + "\n```") + "\n"
        }
        s = Regex("""(?is)<code[^>]*>(.*?)</code>""").replace(s) { m -> protect("`" + decodeEntities(m.groupValues[1].replace(Regex("<[^>]+>"), "")) + "`") }
        for (level in 1..6) s = Regex("""(?is)<h$level[^>]*>(.*?)</h$level>""").replace(s) { "\n\n" + "#".repeat(level) + " " + it.groupValues[1].trim() + "\n\n" }
        s = Regex("""(?is)<a\s[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""").replace(s) { m ->
            val url = m.groupValues[1]
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("mailto:")) "[${m.groupValues[2]}]($url)" else m.groupValues[2]
        }
        s = s.replace(Regex("""(?is)<(strong|b)(\s[^>]*)?>(.*?)</\1>"""), "**$3**")
        s = s.replace(Regex("""(?is)<(em|i)(\s[^>]*)?>(.*?)</\1>"""), "*$3*")
        s = s.replace(Regex("""(?is)<(del|s|strike)(\s[^>]*)?>(.*?)</\1>"""), "~~$3~~")
        // Private-use markers survive the generic tag stripping below, then become <u>…</u> again.
        s = s.replace(Regex("""(?is)<u(\s[^>]*)?>(.*?)</u>"""), "\uE000$2\uE001")
        s = s.replace(Regex("""(?is)</li>\s*"""), "")
        s = s.replace(Regex("""(?is)<li[^>]*>\s*<input[^>]*checked[^>]*>\s*"""), "\n- [x] ")
        s = s.replace(Regex("""(?is)<li[^>]*>\s*<input[^>]*type=["']?checkbox["']?[^>]*>\s*"""), "\n- [ ] ")
        s = s.replace(Regex("""(?is)<li[^>]*>\s*"""), "\n- ")
        s = s.replace(Regex("""(?is)<blockquote[^>]*>(.*?)</blockquote>""")) { m -> "\n" + m.groupValues[1].trim().lines().joinToString("\n") { "> $it" } + "\n" }
        s = s.replace(Regex("""(?i)<br\s*/?>"""), "\n")
        s = s.replace(Regex("""(?i)<hr\s*/?>"""), "\n---\n")
        s = s.replace(Regex("""(?i)</(p|div|ul|ol|table|tr|section|article)>"""), "\n\n")
        s = s.replace(Regex("""<(?:"[^"]*"|'[^']*'|[^'">])*>"""), "") // all remaining tags
        s = decodeEntities(s).replace("\uE000", "<u>").replace("\uE001", "</u>")
        s = Regex("""\uE002(\d+)\uE003""").replace(s) { m -> protectedCode[m.groupValues[1].toInt()] }
        return s.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("""\n{3,}"""), "\n\n").trim() + "\n"
    }

    fun parse(fileName: String, html: String): ImportCandidate {
        val title = Regex("""(?is)<title[^>]*>(.*?)</title>""").find(html)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }
            ?.takeIf { it.isNotEmpty() } ?: fileName.substringBeforeLast('.')
        var body = toMarkdown(html)
        // HTML exports from Margin repeat the title as <h1>; avoid duplicating it.
        if (body.startsWith("# $title\n")) body = body.removePrefix("# $title\n").trimStart('\n')
        return ImportCandidate(sourceName = fileName, title = title, body = body)
    }
}

object ImportPlanner {
    fun fingerprint(title: String, body: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((title.trim() + "\u0000" + body.trim()).toByteArray(Charsets.UTF_8))
        return d.joinToString("") { "%02x".format(it) }
    }

    data class Plan(val toImport: List<ImportCandidate>, val issues: List<ImportIssue>)

    /**
     * Decides what to write. Duplicates are detected by original id (Margin exports) or by exact
     * content fingerprint, against both the existing library and earlier files in the same batch.
     */
    fun plan(
        candidates: List<ImportCandidate>,
        existingIds: Set<String>,
        existingFingerprints: Set<String>,
        policy: DuplicatePolicy,
    ): Plan {
        val seen = HashSet(existingFingerprints)
        val ids = HashSet(existingIds)
        val out = ArrayList<ImportCandidate>()
        val issues = ArrayList<ImportIssue>()
        for (c in candidates) {
            if (c.title.isBlank() && c.body.isBlank()) { issues += ImportIssue.Skipped(c.sourceName, SkipReason.EMPTY); continue }
            val fp = fingerprint(c.title, c.body)
            val dup = (c.originalId != null && c.originalId in ids) || fp in seen
            if (dup && policy == DuplicatePolicy.SKIP) { issues += ImportIssue.Skipped(c.sourceName, SkipReason.DUPLICATE); continue }
            seen += fp
            c.originalId?.let { ids += it }
            out += c
        }
        return Plan(out, issues)
    }
}
