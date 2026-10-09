package app.margin.domain.text

import app.margin.domain.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test fun parsesAllBlockTypes() {
        val md = """
            # Title
            Intro **bold** and *it*.

            - one
              - nested
            1. first
            2) second
            - [ ] todo
            - [x] done
            > quoted
            > more
            ---
            ```kotlin
            val x = 1

            println(x)
            ```
            | A | B |
            |:--|--:|
            | 1 | 2 |
            | 3 |
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        val kinds = blocks.map { it::class.simpleName }
        assertEquals(listOf("Heading", "Paragraph", "ListItem", "ListItem", "ListItem", "ListItem", "ListItem", "ListItem", "Quote", "Divider", "Code", "Table"), kinds)
        val nested = blocks[3] as Block.ListItem
        assertEquals(1, nested.indent)
        val second = blocks[5] as Block.ListItem
        assertEquals(2, second.number)
        assertTrue((blocks[7] as Block.ListItem).checked)
        assertFalse((blocks[6] as Block.ListItem).checked)
        assertEquals("quoted\nmore", (blocks[8] as Block.Quote).text)
        val code = blocks[10] as Block.Code
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1\n\nprintln(x)", code.code)
        val table = blocks[11] as Block.Table
        assertEquals(listOf("A", "B"), table.header)
        assertEquals(listOf(Block.Align.LEFT, Block.Align.RIGHT), table.alignments)
        assertEquals(listOf(listOf("1", "2"), listOf("3", "")), table.rows)
    }

    @Test fun unterminatedFenceDoesNotLoseContent() {
        val blocks = MarkdownParser.parse("```\ncode line\nmore")
        assertEquals("code line\nmore", (blocks.single() as Block.Code).code)
    }

    @Test fun inlineParsing() {
        val nodes = InlineParser.parse("a **b *c*** ~~d~~ <u>u</u> `x*y` [l](https://e.com) [[Note A]] https://x.org/p.")
        val plain = InlineParser.plainText(nodes)
        assertEquals("a b c d u x*y l Note A https://x.org/p.", plain)
        assertTrue(nodes.any { it is Inline.WikiLink && it.target == "Note A" })
        assertTrue(nodes.any { it is Inline.AutoLink && it.url == "https://x.org/p" })
        assertTrue(nodes.any { it is Inline.Code && it.text == "x*y" })
    }

    @Test fun snakeCaseIsNotItalic() {
        val nodes = InlineParser.parse("my_var_name stays")
        assertEquals(1, nodes.size)
        assertTrue(nodes[0] is Inline.Text)
    }

    @Test fun unmatchedMarkersStayLiteral() {
        assertEquals("2 * 3 = 6 and **open", InlineParser.plainText(InlineParser.parse("2 * 3 = 6 and **open")))
    }

    @Test fun escapes() {
        assertEquals("*not italic*", InlineParser.plainText(InlineParser.parse("\\*not italic\\*")))
    }

    @Test fun inlineOffsetsMatchSource() {
        val s = "x **bold** y"
        val b = InlineParser.parse(s).first { it is Inline.Bold }
        assertEquals("**bold**", s.substring(b.start, b.end))
    }

    @Test fun plainTextStripsMarkup() {
        assertEquals("Title\nHello world\ntask", MarkdownParser.toPlainText("# Title\n\nHello **world**\n\n- [ ] task"))
    }

    @Test fun toggleTaskOnlyChangesThatLine() {
        val md = "a\n- [ ] one\n- [x] two"
        assertEquals("a\n- [x] one\n- [x] two", MarkdownParser.toggleTask(md, 1))
        assertEquals("a\n- [ ] one\n- [ ] two", MarkdownParser.toggleTask(md, 2))
        assertEquals(md, MarkdownParser.toggleTask(md, 0))
        assertEquals(md, MarkdownParser.toggleTask(md, 99))
    }

    @Test fun linkTargets() {
        val md = "See [[Alpha]] and **[[Beta]]** and [x](margin://note/123) and https://web.dev"
        assertEquals(setOf("Alpha", "Beta", "margin://note/123"), MarkdownParser.linkTargets(md))
        assertEquals(listOf("https://web.dev"), MarkdownParser.webLinks(md))
    }

    @Test fun htmlRendererEscapesAndBlocksUnsafeLinks() {
        val html = HtmlRenderer.renderBody("<script>alert(1)</script>\n\n[x](javascript:alert(1)) [ok](https://a.b)")
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("javascript:"))
        assertTrue(html.contains("<a href=\"https://a.b\">ok</a>"))
    }

    @Test fun htmlRendererLists() {
        val html = HtmlRenderer.renderBody("- a\n- [x] b\n1. c")
        assertTrue(html.contains("<ul>\n<li>a</li>\n<li><input type=\"checkbox\" disabled checked> b</li>\n</ul>\n<ol>\n<li>c</li>\n</ol>"))
    }

    @Test fun highlighterSpansAreInBoundsAndCoverSyntax() {
        val text = "# Head\nsome **bold** text\n- [x] done item\n> q\n```\ncode\n```"
        val spans = MarkdownHighlighter.spans(text)
        spans.forEach { assertTrue(it.start >= 0 && it.end <= text.length && it.start < it.end) }
        val bold = spans.first { it.kind == MarkdownHighlighter.Kind.BOLD }
        assertEquals("**bold**", text.substring(bold.start, bold.end))
        assertTrue(spans.any { it.kind == MarkdownHighlighter.Kind.HEADING1 && text.substring(it.start, it.end) == "# Head" })
        assertTrue(spans.any { it.kind == MarkdownHighlighter.Kind.CHECKED_ITEM && text.substring(it.start, it.end) == "done item" })
        assertTrue(spans.any { it.kind == MarkdownHighlighter.Kind.CODE_BLOCK })
    }

    @Test fun highlighterHandlesEmptyAndTrailingNewlines() {
        assertTrue(MarkdownHighlighter.spans("").isEmpty())
        MarkdownHighlighter.spans("**a**\n\n\n")
    }

    @Test fun derivedTitle() {
        assertEquals("Shopping", Note.derivedTitle("\n\n# Shopping\n- milk"))
        assertEquals("xylophone lessons", Note.derivedTitle("- [ ] xylophone lessons"))
        assertEquals(null, Note.derivedTitle("   \n"))
    }

    @Test fun textStats() {
        val s = TextStats.of("# Hello\n\nIt's a **well-known** fact.")
        assertEquals(5, s.words)
        assertEquals(1, s.readingMinutes)
        assertEquals(0, TextStats.of("").readingMinutes)
    }

    @Test fun findReplace() {
        val t = "Cat cat category CAT"
        assertEquals(4, FindReplace.findAll(t, "cat").size)
        assertEquals(3, FindReplace.findAll(t, "cat", wholeWord = true).size)
        assertEquals(2, FindReplace.findAll(t, "cat", ignoreCase = false).size)
        val r = FindReplace.replaceAll(t, "cat", "dog", wholeWord = true)
        assertEquals("dog dog category dog", r.text)
        assertEquals(3, r.replacements)
        assertEquals(0, FindReplace.findAll(t, "").size)
    }
}
