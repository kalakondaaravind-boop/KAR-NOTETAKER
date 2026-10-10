package app.margin.domain.io

import app.margin.domain.text.LiveMarkdown
import app.margin.domain.text.MarkdownHighlighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class OfficeImportExportTest {
    private val note = """
        # Plan

        Some **bold**, *italic*, <u>underlined</u> and ~~struck~~ text with a [link](https://example.com).
        <span style="color:#E53935">Red words</span> and <span style="background-color:#FFF59D">highlighted</span> and <span style="font-family:Caveat">handwriting</span>.

        - first
        - second
          - nested
        1. one
        2. two
        - [ ] open task
        - [x] done task

        > A quote

        | Name | Qty |
        | --- | --- |
        | Apples | 3 |
    """.trimIndent()

    private fun entries(docx: ByteArray): Map<String, String> {
        val out = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(docx)).use { z -> while (true) { val e = z.nextEntry ?: break; out[e.name] = z.readBytes().toString(Charsets.UTF_8) } }
        return out
    }

    @Test fun docxExportIsAWellFormedPackage() {
        val bytes = DocxExporter.export("My <plan> & notes", note, listOf(DocxExporter.Image("sketch.png", byteArrayOf(1, 2, 3), 800, 600, "Sketch")))
        val parts = entries(bytes)
        for (p in listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml", "word/styles.xml", "word/numbering.xml", "word/_rels/document.xml.rels", "docProps/core.xml", "word/media/image1.png"))
            assertTrue("missing $p", p in parts.keys)
        // every XML part parses
        parts.filterKeys { it.endsWith(".xml") || it.endsWith(".rels") }.forEach { (k, v) -> DocxImporter.parse(v.toByteArray()); assertTrue(k, v.isNotEmpty()) }
        val doc = parts.getValue("word/document.xml")
        assertTrue(doc.contains("My &lt;plan&gt; &amp; notes"))
        assertTrue(doc.contains("<w:b/>")); assertTrue(doc.contains("<w:i/>")); assertTrue(doc.contains("<w:strike/>"))
        assertTrue(doc.contains("""w:color w:val="E53935"""")); assertTrue(doc.contains("""w:fill="FFF59D""""))
        assertTrue(doc.contains("<w:hyperlink")); assertTrue(doc.contains("<w:tbl>")); assertTrue(doc.contains("☐ ")); assertTrue(doc.contains("☒ "))
        assertTrue(doc.contains("r:embed=\"rId"))
        assertTrue(parts.getValue("word/_rels/document.xml.rels").contains("https://example.com"))
    }

    @Test fun docxRoundTripKeepsStructureAndFormatting() {
        val r = DocxImporter.parse("plan.docx", DocxExporter.export("Plan", note))
        val md = r.candidate.body
        assertEquals("Plan", r.candidate.title)
        for (needle in listOf("**bold**", "*italic*", "<u>underlined</u>", "~~struck~~", "[link](https://example.com)",
                "color:#E53935", "background-color:#FFF59D", "font-family:Caveat",
                "- first", "  - nested", "1. one", "2. two", "- [ ] open task", "- [x] done task", "> A quote", "| Name | Qty |", "| Apples | 3 |"))
            assertTrue("missing $needle in:\n$md", md.contains(needle))
    }

    @Test fun docxImportRejectsDoctypeAndGarbage() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>&e;</w:t></w:r></w:p></w:body></w:document>"""
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> z.putNextEntry(ZipEntry("word/document.xml")); z.write(evil.toByteArray()); z.closeEntry() }
        try { DocxImporter.parse("evil.docx", bos.toByteArray()); fail("DOCTYPE accepted") } catch (e: ImportFailure) { assertEquals(FailReason.MALFORMED, e.reason) }
        try { DocxImporter.parse("x.docx", "not a zip".toByteArray()); fail() } catch (e: ImportFailure) { }
    }

    @Test fun csvBecomesATable() {
        val c = CsvImporter.parse("stock.csv", "Item,Notes\r\nApples,\"red, crisp\"\nPears,\"said \"\"ripe\"\"\"\n")
        assertEquals("stock", c.title)
        assertTrue(c.body.contains("| Item | Notes |"))
        assertTrue(c.body.contains("| Apples | red, crisp |"))
        assertTrue(c.body.contains("| Pears | said \"ripe\" |"))
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), CsvImporter.rows("a;b\n1;2"))
    }

    @Test fun rtfToMarkdown() {
        val bs = "\\"
        val rtf = "{${bs}rtf1${bs}ansi{${bs}fonttbl{${bs}f0 Arial;}}{${bs}colortbl;${bs}red255${bs}green0${bs}blue0;}Hello {${bs}b bold} and {${bs}i italic}${bs}par Caf${bs}'e9 ${bs}u8364? done}"
        val md = RtfImporter.toMarkdown(rtf)
        assertTrue(md, md.contains("Hello **bold** and *italic*"))
        assertTrue(md, md.contains("Café €"))
        assertFalse(md.contains("Arial"))
        try { RtfImporter.toMarkdown("plain text"); fail() } catch (e: ImportFailure) { }
    }

    @Test fun liveMarkdownHidesMarksAndMapsCursor() {
        val src = "# Title\nSome **bold** and *it* text\n- [ ] task\n- item"
        val v = LiveMarkdown.build(src)
        assertEquals("Title\nSome bold and it text\n☐ task\n• item", v.text)
        // styles land on the visible words
        val bold = v.styles.first { it.kind == MarkdownHighlighter.Kind.BOLD }
        assertEquals("bold", v.text.substring(bold.start, bold.end))
        val italic = v.styles.first { it.kind == MarkdownHighlighter.Kind.ITALIC }
        assertEquals("it", v.text.substring(italic.start, italic.end))
        // cursor at the start of the heading line sits after "# " so typing keeps the heading
        assertEquals(2, v.toOrig(0))
        // cursor right after "bold" (view) maps before the closing ** (typing continues bold)
        val afterBold = v.text.indexOf("bold") + 4
        assertEquals(src.indexOf("bold") + 4, v.toOrig(afterBold))
        // every original offset maps into the view and back without crashing
        for (o in 0..src.length) { val t = v.toView(o); assertTrue(t in 0..v.text.length); assertTrue(v.toOrig(t) in 0..src.length) }
        for (t in 0..v.text.length) assertTrue(v.toOrig(t) in 0..src.length)
        // the original text is unchanged; showing marks gives an identity view
        val raw = LiveMarkdown.build(src, showMarks = true)
        assertEquals(src, raw.text); assertEquals(5, raw.toOrig(5))
    }

    @Test fun liveMarkdownHidesStyleTagsAndLinkTargets() {
        val src = """Pay <span style="color:#E53935">now</span> via [portal](https://x.y)"""
        val v = LiveMarkdown.build(src)
        assertEquals("Pay now via portal", v.text)
        val col = v.styles.first { it.kind == MarkdownHighlighter.Kind.COLOR }
        assertEquals("now", v.text.substring(col.start, col.end))
        assertEquals(0xFFE53935, col.value)
    }
}
