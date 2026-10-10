package app.margin.domain.io

import app.margin.domain.text.Block
import app.margin.domain.text.Inline
import app.margin.domain.text.InlineParser
import app.margin.domain.text.MarkdownParser
import app.margin.domain.text.NoteFont
import app.margin.domain.text.Palette
import app.margin.domain.text.TextStyleAttrs
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** A file carried along with an imported note (e.g. images embedded in a Word document). */
class ImportMedia(val name: String, val mimeType: String, val bytes: ByteArray)

/**
 * Word (.docx) → Markdown note. Reads paragraphs, headings (Heading 1–6, Title, Subtitle), bold / italic /
 * underline / strikethrough, text colour, highlight, fonts we bundle, hyperlinks, bulleted / numbered lists,
 * quotes, tables and embedded images (kept as attachments). Parsing is hardened: no DTDs or external entities,
 * size limits on every part.
 */
object DocxImporter {
    const val MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val MAX_PART = 25L * 1024 * 1024
    private const val MAX_TOTAL = 80L * 1024 * 1024

    data class Result(val candidate: ImportCandidate, val media: List<ImportMedia>)

    fun parse(fileName: String, bytes: ByteArray): Result {
        val parts = unzip(bytes)
        val doc = parts["word/document.xml"] ?: throw ImportFailure(FailReason.MALFORMED, "not a Word document")
        val styles = parts["word/styles.xml"]?.let(::styleNames).orEmpty()
        val numbering = parts["word/numbering.xml"]?.let(::numberingFormats).orEmpty()
        val rels = parts["word/_rels/document.xml.rels"]?.let(::relationships).orEmpty()
        val core = parts["docProps/core.xml"]?.let(::coreProps).orEmpty()
        val media = rels.values.filter { it.type.endsWith("/image") }.mapNotNull { rel ->
            val path = "word/" + rel.target.removePrefix("/word/").removePrefix("./")
            parts[path]?.let { ImportMedia(rel.target.substringAfterLast('/'), mimeFor(rel.target), it) }
        }.distinctBy { it.name }

        val ctx = Ctx(styles, numbering, rels)
        val body = firstChild(parse(doc).documentElement, "body") ?: throw ImportFailure(FailReason.MALFORMED, "empty document")
        val out = StringBuilder()
        for (n in children(body)) when (n.localName) {
            "p" -> paragraph(n, ctx, out)
            "tbl" -> { ctx.endList(out); table(n, ctx, out) }
            "sdt" -> firstChild(n, "sdtContent")?.let { c -> children(c).filter { it.localName == "p" }.forEach { paragraph(it, ctx, out) } }
        }
        val md = out.toString().replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
        val title = core["title"]?.takeIf { it.isNotBlank() }
            ?: Regex("""^# (.+)$""", RegexOption.MULTILINE).find(md)?.groupValues?.get(1)?.trim()
            ?: fileName.substringBeforeLast('.')
        val bodyMd = if (md.startsWith("# $title\n")) md.removePrefix("# $title\n").trimStart('\n') else md
        val withMedia = if (media.isEmpty()) bodyMd else bodyMd.trimEnd() + "\n\n> Images from the document are attached to this note.\n"
        return Result(
            ImportCandidate(sourceName = fileName, title = title, body = withMedia,
                createdAt = core["created"]?.let(::instant), updatedAt = core["modified"]?.let(::instant)),
            media,
        )
    }

    // ---- parts --------------------------------------------------------------------------------------------

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/').trimStart('/')
                    if (name.contains("..")) continue
                    val want = name.endsWith(".xml") || name.endsWith(".rels") || name.startsWith("word/media/")
                    if (!want) continue
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val r = zip.read(chunk); if (r < 0) break
                        buf.write(chunk, 0, r); total += r
                        if (buf.size() > MAX_PART || total > MAX_TOTAL) throw ImportFailure(FailReason.TOO_LARGE, "document too large")
                    }
                    out[name] = buf.toByteArray()
                }
            }
        } catch (e: ImportFailure) { throw e } catch (e: Exception) { throw ImportFailure(FailReason.MALFORMED, "not a valid .docx file") }
        return out
    }

    fun parse(xml: ByteArray): org.w3c.dom.Document {
        val f = DocumentBuilderFactory.newInstance()
        f.isNamespaceAware = true
        f.isExpandEntityReferences = false
        f.isXIncludeAware = false
        runCatching { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        return try { f.newDocumentBuilder().parse(ByteArrayInputStream(xml)) } catch (e: Exception) { throw ImportFailure(FailReason.MALFORMED, "unreadable document part") }
    }

    private fun children(n: Node): List<Element> {
        val out = ArrayList<Element>()
        val l = n.childNodes
        for (i in 0 until l.length) (l.item(i) as? Element)?.let { out += it }
        return out
    }
    private fun firstChild(n: Node, local: String): Element? = children(n).firstOrNull { it.localName == local }
    private fun descendants(n: Element, local: String): List<Element> {
        val l = n.getElementsByTagNameNS("*", local)
        return (0 until l.length).map { l.item(it) as Element }
    }
    private fun wAttr(e: Element?, name: String): String? = e?.getAttributeNS(W, name)?.takeIf { it.isNotEmpty() } ?: e?.getAttribute("w:$name")?.takeIf { it.isNotEmpty() }
    private fun on(e: Element?): Boolean = e != null && wAttr(e, "val").let { it == null || it == "1" || it == "true" || it == "on" }

    private fun styleNames(xml: ByteArray): Map<String, String> = descendants(parse(xml).documentElement, "style").associate { s ->
        (wAttr(s, "styleId") ?: "") to (wAttr(firstChild(s, "name"), "val") ?: "").lowercase()
    }

    /** numId → (ilvl → numFmt). */
    private fun numberingFormats(xml: ByteArray): Map<String, Map<Int, String>> {
        val root = parse(xml).documentElement
        val abstract = descendants(root, "abstractNum").associate { a ->
            (wAttr(a, "abstractNumId") ?: "") to descendants(a, "lvl").associate { lvl -> (wAttr(lvl, "ilvl")?.toIntOrNull() ?: 0) to (wAttr(firstChild(lvl, "numFmt"), "val") ?: "bullet") }
        }
        return descendants(root, "num").associate { n -> (wAttr(n, "numId") ?: "") to (abstract[wAttr(firstChild(n, "abstractNumId"), "val")] ?: emptyMap()) }
    }

    data class Rel(val target: String, val type: String, val external: Boolean)
    private fun relationships(xml: ByteArray): Map<String, Rel> = descendants(parse(xml).documentElement, "Relationship").associate { r ->
        r.getAttribute("Id") to Rel(r.getAttribute("Target"), r.getAttribute("Type"), r.getAttribute("TargetMode") == "External")
    }

    private fun coreProps(xml: ByteArray): Map<String, String> {
        val root = parse(xml).documentElement
        return listOf("title", "created", "modified").mapNotNull { k -> descendants(root, k).firstOrNull()?.textContent?.trim()?.let { k to it } }.toMap()
    }

    private fun instant(s: String) = runCatching { Instant.parse(s) }.getOrNull()
    private fun mimeFor(name: String) = when (name.substringAfterLast('.').lowercase()) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "bmp" -> "image/bmp"; "webp" -> "image/webp"
        "svg" -> "image/svg+xml"; "emf" -> "image/emf"; "wmf" -> "image/wmf"; else -> "application/octet-stream"
    }

    // ---- content ------------------------------------------------------------------------------------------

    private class Ctx(val styles: Map<String, String>, val numbering: Map<String, Map<Int, String>>, val rels: Map<String, Rel>) {
        var inList = false
        val counters = HashMap<String, IntArray>()
        fun endList(out: StringBuilder) { if (inList) { out.append('\n'); inList = false } }
    }

    private data class Fmt(val b: Boolean = false, val i: Boolean = false, val u: Boolean = false, val s: Boolean = false,
                           val color: Long? = null, val bg: Long? = null, val font: NoteFont? = null, val code: Boolean = false)

    private fun runFmt(r: Element): Fmt {
        val p = firstChild(r, "rPr") ?: return Fmt()
        val color = wAttr(firstChild(p, "color"), "val")?.takeIf { it != "auto" && it != "000000" }?.let { Palette.parseHex(it) }
        val hl = wAttr(firstChild(p, "highlight"), "val")?.let(::highlightColor)
            ?: wAttr(firstChild(p, "shd"), "fill")?.takeIf { it != "auto" && it.uppercase() != "FFFFFF" }?.let { Palette.parseHex(it) }
        val fontName = firstChild(p, "rFonts")?.let { wAttr(it, "ascii") ?: wAttr(it, "hAnsi") }
        val mono = fontName?.lowercase()?.let { it.contains("consolas") || it.contains("courier") || it.contains("mono") } == true
        val font = if (mono) null else NoteFont.from(fontName)?.takeIf { it != NoteFont.SANS }
        val u = firstChild(p, "u")?.let { wAttr(it, "val") != "none" } == true
        return Fmt(on(firstChild(p, "b")), on(firstChild(p, "i")), u, on(firstChild(p, "strike")) || on(firstChild(p, "dstrike")), color, hl, font, mono)
    }

    private fun highlightColor(name: String): Long? = when (name.lowercase()) {
        "yellow" -> 0xFFFFF59D; "green" -> 0xFFC8E6C9; "cyan" -> 0xFFB2EBF2; "magenta" -> 0xFFF8BBD0; "blue" -> 0xFFBBDEFB
        "red" -> 0xFFFFCDD2; "darkyellow" -> 0xFFFFE0B2; "lightgray" -> 0xFFE0E0E0; "darkgray" -> 0xFFBDBDBD
        "none" -> null; else -> 0xFFFFF59D
    }

    private fun escape(t: String): String = buildString {
        for (c in t) { if (c in "\\`*_[]~<>#|") append('\\'); append(c) }
    }

    /** Runs → Markdown, merging adjacent runs with identical formatting. */
    private fun inlines(p: Element, ctx: Ctx): String {
        val segs = ArrayList<Pair<String, Fmt>>()
        fun runText(r: Element): String = buildString {
            for (c in children(r)) when (c.localName) {
                "t" -> append(c.textContent)
                "tab" -> append("    ")
                "br", "cr" -> append(" ")
                "noBreakHyphen" -> append("-")
                "drawing", "pict" -> append("")
            }
        }
        fun visit(e: Element, link: String?) {
            for (c in children(e)) when (c.localName) {
                "r" -> {
                    val t = runText(c)
                    if (t.isNotEmpty()) segs += (if (link != null) "\u0000L$link\u0001$t" else t) to runFmt(c)
                    else if (descendants(c, "blip").isNotEmpty()) segs += "\u0000IMG" to Fmt()
                }
                "hyperlink" -> {
                    val rid = c.getAttributeNS(R, "id").ifEmpty { c.getAttribute("r:id") }
                    val url = ctx.rels[rid]?.takeIf { it.external }?.target ?: wAttr(c, "anchor")?.let { "#$it" }
                    visit(c, url?.takeIf { it.startsWith("http://") || it.startsWith("https://") || it.startsWith("mailto:") })
                }
                "ins", "smartTag", "fldSimple", "customXml" -> visit(c, link)
                "sdt" -> firstChild(c, "sdtContent")?.let { visit(it, link) }
            }
        }
        visit(p, null)
        val merged = ArrayList<Pair<String, Fmt>>()
        for (s in segs) {
            val last = merged.lastOrNull()
            if (last != null && last.second == s.second && !s.first.startsWith("\u0000") && !last.first.startsWith("\u0000")) merged[merged.lastIndex] = last.first + s.first to s.second
            else merged += s
        }
        return merged.joinToString("") { (raw, f) ->
            when {
                raw == "\u0000IMG" -> "[image]"
                raw.startsWith("\u0000L") -> {
                    val url = raw.substring(2, raw.indexOf('\u0001')); val text = raw.substringAfter('\u0001')
                    "[${escape(text)}]($url)"
                }
                else -> wrap(raw, f)
            }
        }
    }

    private fun wrap(raw: String, f: Fmt): String {
        if (raw.isBlank()) return raw
        val lead = if (raw.first().isWhitespace()) " " else ""; val trail = if (raw.last().isWhitespace()) " " else ""
        var t = if (f.code) "`" + raw.trim().replace("`", "'") + "`" else escape(raw.trim())
        if (f.s) t = "~~$t~~"
        if (f.u) t = "<u>$t</u>"
        if (f.i) t = "*$t*"
        if (f.b) t = "**$t**"
        val attrs = TextStyleAttrs(f.color, f.bg, f.font)
        if (!attrs.isEmpty) t = attrs.openTag() + t + TextStyleAttrs.CLOSE
        return lead + t + trail
    }

    private fun paragraph(p: Element, ctx: Ctx, out: StringBuilder) {
        val ppr = firstChild(p, "pPr")
        val styleId = wAttr(firstChild(ppr ?: p, "pStyle"), "val")
        val style = ctx.styles[styleId] ?: styleId?.lowercase() ?: ""
        val text = inlines(p, ctx).trim()
        val numPr = ppr?.let { firstChild(it, "numPr") }
        if (numPr != null && text.isNotEmpty()) {
            val numId = wAttr(firstChild(numPr, "numId"), "val") ?: "0"
            val lvl = (wAttr(firstChild(numPr, "ilvl"), "val")?.toIntOrNull() ?: 0).coerceIn(0, 8)
            if (numId != "0") {
                val fmt = ctx.numbering[numId]?.get(lvl) ?: "bullet"
                val indent = "  ".repeat(lvl)
                val marker = if (fmt == "bullet" || fmt == "none") "-" else {
                    val c = ctx.counters.getOrPut(numId) { IntArray(9) }
                    c[lvl]++; for (k in lvl + 1 until 9) c[k] = 0
                    "${c[lvl]}."
                }
                val checkbox = when { text.startsWith("☐") -> "[ ] "; text.startsWith("☒") || text.startsWith("☑") -> "[x] "; else -> "" }
                out.append(indent).append(marker).append(' ').append(checkbox).append(if (checkbox.isEmpty()) text else text.drop(1).trimStart()).append('\n')
                ctx.inList = true
                return
            }
        }
        // Plain checkbox paragraphs (exported by many apps without numbering).
        if (text.startsWith("☐ ") || text.startsWith("☒ ") || text.startsWith("☑ ")) {
            out.append(if (text.startsWith("☐")) "- [ ] " else "- [x] ").append(text.drop(2)).append('\n'); ctx.inList = true; return
        }
        ctx.endList(out)
        if (text.isEmpty()) { out.append('\n'); return }
        val heading = Regex("""^heading\s*([1-6])$""").find(style)?.groupValues?.get(1)?.toInt()
            ?: when (style) { "title" -> 1; "subtitle" -> 2; else -> null }
        when {
            heading != null -> out.append("#".repeat(heading)).append(' ').append(text.replace(Regex("""^\*\*(.*)\*\*$"""), "$1")).append("\n\n")
            style.contains("quote") -> out.append("> ").append(text).append("\n\n")
            else -> out.append(text).append("\n\n")
        }
    }

    private fun table(t: Element, ctx: Ctx, out: StringBuilder) {
        val rows = children(t).filter { it.localName == "tr" }.map { tr ->
            children(tr).filter { it.localName == "tc" }.map { tc ->
                children(tc).filter { it.localName == "p" }.joinToString(" ") { inlines(it, ctx).trim() }.replace("|", "\\|").replace("\n", " ").ifBlank { " " }
            }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return
        val cols = rows.maxOf { it.size }
        fun line(cells: List<String>) = "| " + (0 until cols).joinToString(" | ") { cells.getOrElse(it) { " " } } + " |\n"
        // Header cells are bold in most documents; the Markdown header row is bold already.
        val header = rows[0].map { it.removeSurrounding("**") }
        out.append('\n').append(line(header)).append("|" + " --- |".repeat(cols) + "\n")
        rows.drop(1).forEach { out.append(line(it)) }
        out.append('\n')
    }
}

/**
 * Markdown note → Word (.docx). Headings, paragraphs, bold/italic/underline/strike, colour, highlight, fonts,
 * links, bulleted / numbered / task lists, quotes, code blocks, dividers, tables and images (e.g. drawings
 * rendered to PNG by the app). Opens in Word, Google Docs, LibreOffice and WPS.
 */
object DocxExporter {
    class Image(val name: String, val png: ByteArray, val widthPx: Int, val heightPx: Int, val caption: String? = null)

    private const val EMU_PER_PX = 9525L
    private const val MAX_WIDTH_EMU = 6_000_000L // ≈ 16.5 cm text width

    fun export(title: String, markdown: String, images: List<Image> = emptyList(), created: Instant? = null, modified: Instant? = null): ByteArray {
        val rels = ArrayList<Pair<String, String>>() // rId → xml
        var relN = 10
        fun hyperlink(url: String): String {
            val id = "rId${relN++}"
            rels += id to """<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink" Target="${x(url)}" TargetMode="External"/>"""
            return id
        }
        val numbering = Numbering()
        val body = StringBuilder()
        if (title.isNotBlank()) body.append(p("""<w:pPr><w:pStyle w:val="Title"/></w:pPr>""", run(x(title))))
        var orderedNum = -1
        var lastWasOrdered = false
        for (b in MarkdownParser.parse(markdown)) {
            if (!(b is Block.ListItem && b.kind == Block.ListKind.ORDERED)) lastWasOrdered = false
            when (b) {
                is Block.Heading -> body.append(p("""<w:pPr><w:pStyle w:val="Heading${b.level.coerceIn(1, 6)}"/></w:pPr>""", runs(b.inlines, ::hyperlink)))
                is Block.Paragraph -> body.append(p("", runs(b.inlines, ::hyperlink)))
                is Block.Quote -> body.append(p("""<w:pPr><w:pStyle w:val="Quote"/></w:pPr>""", runs(b.inlines, ::hyperlink)))
                is Block.Code -> body.append(p("""<w:pPr><w:pStyle w:val="CodeBlock"/></w:pPr>""",
                    b.code.split('\n').joinToString("""<w:r><w:br/></w:r>""") { run(x(it), """<w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/>""") }))
                is Block.Divider -> body.append(p("""<w:pPr><w:pBdr><w:bottom w:val="single" w:sz="6" w:space="1" w:color="9DB6CC"/></w:pBdr></w:pPr>""", ""))
                is Block.ListItem -> {
                    val lvl = b.indent.coerceIn(0, 8)
                    when (b.kind) {
                        Block.ListKind.BULLET -> body.append(p(numPr(Numbering.BULLET_NUM, lvl), runs(b.inlines, ::hyperlink)))
                        Block.ListKind.ORDERED -> {
                            if (!lastWasOrdered) orderedNum = numbering.newOrdered()
                            lastWasOrdered = true
                            body.append(p(numPr(orderedNum, lvl), runs(b.inlines, ::hyperlink)))
                        }
                        Block.ListKind.TASK -> body.append(p("""<w:pPr><w:ind w:left="${360 + lvl * 360}"/></w:pPr>""",
                            run(if (b.checked) "☒ " else "☐ ") + runs(b.inlines, ::hyperlink)))
                    }
                }
                is Block.Table -> body.append(table(b, ::hyperlink))
            }
        }
        images.forEachIndexed { i, img ->
            val id = "rId${relN++}"
            rels += id to """<Relationship Id="$id" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/image${i + 1}.png"/>"""
            body.append(p("""<w:pPr><w:jc w:val="center"/></w:pPr>""", drawingRun(id, i + 1, img)))
            img.caption?.let { body.append(p("""<w:pPr><w:pStyle w:val="Caption"/><w:jc w:val="center"/></w:pPr>""", run(x(it)))) }
        }

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr></w:body></w:document>"""

        val docRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/numbering" Target="numbering.xml"/>${rels.joinToString("") { it.second }}</Relationships>"""

        val now = Instant.now().toString()
        val coreXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><dc:title>${x(title)}</dc:title><dc:creator>KAR Notes</dc:creator><dcterms:created xsi:type="dcterms:W3CDTF">${created ?: now}</dcterms:created><dcterms:modified xsi:type="dcterms:W3CDTF">${modified ?: now}</dcterms:modified></cp:coreProperties>"""

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(name: String, data: ByteArray) { z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() }
            put("[Content_Types].xml", CONTENT_TYPES.toByteArray())
            put("_rels/.rels", ROOT_RELS.toByteArray())
            put("docProps/core.xml", coreXml.toByteArray())
            put("word/document.xml", documentXml.toByteArray())
            put("word/_rels/document.xml.rels", docRels.toByteArray())
            put("word/styles.xml", STYLES.toByteArray())
            put("word/numbering.xml", numbering.xml().toByteArray())
            images.forEachIndexed { i, img -> put("word/media/image${i + 1}.png", img.png) }
        }
        return bos.toByteArray()
    }

    private class Numbering {
        companion object { const val BULLET_NUM = 1 }
        private val ordered = ArrayList<Int>()
        fun newOrdered(): Int { val id = 2 + ordered.size; ordered += id; return id }
        fun xml(): String {
            val bullets = listOf("•", "◦", "▪")
            val bulletLvls = (0..8).joinToString("") { l -> """<w:lvl w:ilvl="$l"><w:start w:val="1"/><w:numFmt w:val="bullet"/><w:lvlText w:val="${bullets[l % 3]}"/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="${720 + l * 360}" w:hanging="360"/></w:pPr></w:lvl>""" }
            val fmts = listOf("decimal", "lowerLetter", "lowerRoman")
            val decLvls = (0..8).joinToString("") { l -> """<w:lvl w:ilvl="$l"><w:start w:val="1"/><w:numFmt w:val="${fmts[l % 3]}"/><w:lvlText w:val="%${l + 1}."/><w:lvlJc w:val="left"/><w:pPr><w:ind w:left="${720 + l * 360}" w:hanging="360"/></w:pPr></w:lvl>""" }
            val nums = """<w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>""" + ordered.joinToString("") {
                """<w:num w:numId="$it"><w:abstractNumId w:val="1"/>${(0..8).joinToString("") { l -> """<w:lvlOverride w:ilvl="$l"><w:startOverride w:val="1"/></w:lvlOverride>""" }}</w:num>"""
            }
            return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:numbering xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:abstractNum w:abstractNumId="0">$bulletLvls</w:abstractNum><w:abstractNum w:abstractNumId="1">$decLvls</w:abstractNum>$nums</w:numbering>"""
        }
    }

    private fun numPr(num: Int, lvl: Int) = """<w:pPr><w:pStyle w:val="ListParagraph"/><w:numPr><w:ilvl w:val="$lvl"/><w:numId w:val="$num"/></w:numPr></w:pPr>"""
    private fun p(pPr: String, content: String) = "<w:p>$pPr$content</w:p>"
    private fun run(text: String, rPr: String = "") = """<w:r>${if (rPr.isNotEmpty()) "<w:rPr>$rPr</w:rPr>" else ""}<w:t xml:space="preserve">$text</w:t></w:r>"""

    private data class RFmt(val b: Boolean = false, val i: Boolean = false, val u: Boolean = false, val s: Boolean = false, val code: Boolean = false, val attrs: TextStyleAttrs = TextStyleAttrs())

    private fun rPr(f: RFmt, link: Boolean = false): String = buildString {
        val font = when { f.code -> "Consolas"; f.attrs.font != null && f.attrs.font != NoteFont.SANS -> wordFont(f.attrs.font); else -> null }
        font?.let { append("""<w:rFonts w:ascii="$it" w:hAnsi="$it" w:cs="$it"/>""") }
        if (f.b) append("<w:b/>"); if (f.i) append("<w:i/>"); if (f.s) append("<w:strike/>")
        val color = f.attrs.color?.let { Palette.toHex(it).removePrefix("#") } ?: if (link) "1E66A8" else null
        color?.let { append("""<w:color w:val="$it"/>""") }
        if (f.u || link) append("""<w:u w:val="single"/>""")
        f.attrs.background?.let { append("""<w:shd w:val="clear" w:color="auto" w:fill="${Palette.toHex(it).removePrefix("#")}"/>""") }
    }

    private fun wordFont(f: NoteFont) = when (f) {
        NoteFont.SERIF -> "Georgia"; NoteFont.MONO -> "Consolas"; NoteFont.SANS -> "Calibri"
        else -> f.familyName // bundled fonts: Word substitutes if not installed
    }

    private fun runs(inlines: List<Inline>, hyperlink: (String) -> String, f: RFmt = RFmt()): String = buildString {
        for (n in inlines) when (n) {
            is Inline.Text -> append(run(x(n.text.replace('\n', ' ')), rPr(f)))
            is Inline.Bold -> append(runs(n.children, hyperlink, f.copy(b = true)))
            is Inline.Italic -> append(runs(n.children, hyperlink, f.copy(i = true)))
            is Inline.Underline -> append(runs(n.children, hyperlink, f.copy(u = true)))
            is Inline.Strike -> append(runs(n.children, hyperlink, f.copy(s = true)))
            is Inline.Code -> append(run(x(n.text), rPr(f.copy(code = true))))
            is Inline.Styled -> append(runs(n.children, hyperlink, f.copy(attrs = TextStyleAttrs(n.attrs.color ?: f.attrs.color, n.attrs.background ?: f.attrs.background, n.attrs.font ?: f.attrs.font))))
            is Inline.Link -> append("""<w:hyperlink r:id="${hyperlink(n.url)}">""").append(linkRuns(n.children, f)).append("</w:hyperlink>")
            is Inline.AutoLink -> append("""<w:hyperlink r:id="${hyperlink(n.url)}">""").append(run(x(n.url), rPr(f, link = true))).append("</w:hyperlink>")
            is Inline.WikiLink -> append(run(x(n.target), rPr(f.copy(u = true))))
            is Inline.Image -> append(run(x("[" + n.alt + "]"), rPr(f.copy(i = true))))
        }
    }

    private fun linkRuns(children: List<Inline>, f: RFmt): String =
        children.joinToString("") { c -> run(x(InlineParser.plainText(listOf(c))), rPr(f, link = true)) }

    private fun table(t: Block.Table, hyperlink: (String) -> String): String {
        val cols = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
        val width = 9000 / cols
        fun cell(text: String, header: Boolean) = """<w:tc><w:tcPr><w:tcW w:w="$width" w:type="dxa"/>${if (header) """<w:shd w:val="clear" w:color="auto" w:fill="DCEAF6"/>""" else ""}</w:tcPr>${p("", runs(InlineParser.parse(text), hyperlink, RFmt(b = header)))}</w:tc>"""
        fun row(cells: List<String>, header: Boolean) = "<w:tr>" + (0 until cols).joinToString("") { cell(cells.getOrElse(it) { "" }, header) } + "</w:tr>"
        return """<w:tbl><w:tblPr><w:tblStyle w:val="TableGrid"/><w:tblW w:w="0" w:type="auto"/><w:tblBorders>""" +
            listOf("top", "left", "bottom", "right", "insideH", "insideV").joinToString("") { """<w:$it w:val="single" w:sz="4" w:space="0" w:color="9DB6CC"/>""" } +
            """</w:tblBorders></w:tblPr><w:tblGrid>${(0 until cols).joinToString("") { """<w:gridCol w:w="$width"/>""" }}</w:tblGrid>""" +
            row(t.header, true) + t.rows.joinToString("") { row(it, false) } + "</w:tbl>" + p("", "")
    }

    private fun drawingRun(rid: String, n: Int, img: Image): String {
        var cx = img.widthPx * EMU_PER_PX; var cy = img.heightPx * EMU_PER_PX
        if (cx > MAX_WIDTH_EMU) { cy = cy * MAX_WIDTH_EMU / cx; cx = MAX_WIDTH_EMU }
        return """<w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="$cx" cy="$cy"/><wp:docPr id="$n" name="${x(img.name)}"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="$n" name="${x(img.name)}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r>"""
    }

    /** XML text/attribute escape; also drops characters XML 1.0 forbids. */
    fun x(s: String): String = buildString {
        for (c in s) when {
            c == '&' -> append("&amp;"); c == '<' -> append("&lt;"); c == '>' -> append("&gt;"); c == '"' -> append("&quot;")
            c == '\t' || c == '\n' || c == '\r' || c >= ' ' -> if (c != '\uFFFE' && c != '\uFFFF') append(c)
        }
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/><Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/><Override PartName="/word/numbering.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml"/><Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/></Types>"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/></Relationships>"""

    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:cs="Calibri"/><w:sz w:val="22"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>""" +
        """<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style>""" +
        """<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="240"/></w:pPr><w:rPr><w:b/><w:color w:val="1E4E79"/><w:sz w:val="48"/></w:rPr></w:style>""" +
        (1..6).joinToString("") { l -> """<w:style w:type="paragraph" w:styleId="Heading$l"><w:name w:val="heading $l"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:pPr><w:keepNext/><w:spacing w:before="${360 - l * 30}" w:after="120"/><w:outlineLvl w:val="${l - 1}"/></w:pPr><w:rPr><w:b/><w:color w:val="2A76AD"/><w:sz w:val="${listOf(36, 30, 26, 24, 22, 22)[l - 1]}"/></w:rPr></w:style>""" } +
        """<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:pPr><w:pBdr><w:left w:val="single" w:sz="18" w:space="8" w:color="2A76AD"/></w:pBdr><w:ind w:left="360"/></w:pPr><w:rPr><w:i/><w:color w:val="3F4D5A"/></w:rPr></w:style>""" +
        """<w:style w:type="paragraph" w:styleId="CodeBlock"><w:name w:val="Code Block"/><w:basedOn w:val="Normal"/><w:pPr><w:shd w:val="clear" w:color="auto" w:fill="EEF5FC"/><w:spacing w:after="0"/></w:pPr><w:rPr><w:rFonts w:ascii="Consolas" w:hAnsi="Consolas"/><w:sz w:val="20"/></w:rPr></w:style>""" +
        """<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:pPr><w:spacing w:after="40"/></w:pPr></w:style>""" +
        """<w:style w:type="paragraph" w:styleId="Caption"><w:name w:val="caption"/><w:basedOn w:val="Normal"/><w:rPr><w:i/><w:color w:val="6F7E8C"/><w:sz w:val="18"/></w:rPr></w:style>""" +
        """<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/></w:style></w:styles>"""
}
