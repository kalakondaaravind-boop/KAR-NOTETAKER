package app.margin.domain.text

import app.margin.domain.editor.EditorCommands
import app.margin.domain.editor.TextValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StylesTest {
    @Test fun parsesColourHighlightAndFontSpans() {
        val t = """a <span style="color:#E53935;font-family:Caveat">red **hand**</span> and <mark>hi</mark>"""
        val nodes = InlineParser.parse(t)
        val styled = nodes.filterIsInstance<Inline.Styled>()
        assertEquals(2, styled.size)
        assertEquals(0xFFE53935, styled[0].attrs.color)
        assertEquals(NoteFont.CAVEAT, styled[0].attrs.font)
        assertTrue(styled[0].children.any { it is Inline.Bold })
        assertEquals(Palette.DEFAULT_MARK, styled[1].attrs.background)
        assertEquals("a red hand and hi", InlineParser.plainText(nodes))
    }

    @Test fun nestedSpansAndUnsafeValuesAreHandled() {
        val t = """<span style="color:#000"><span style="background-color:#FFF59D">x</span></span>"""
        val outer = InlineParser.parse(t).single() as Inline.Styled
        assertEquals(0xFF000000, outer.attrs.color)
        assertTrue(outer.children.single() is Inline.Styled)
        // Only hex colours and known fonts survive; anything else is dropped (no CSS injection into HTML export).
        val bad = InlineParser.parse("""<span style="color:red;background:url(x);font-family:evil">y</span>""").single() as Inline.Styled
        assertTrue(bad.attrs.isEmpty)
        assertEquals("y", HtmlRenderer.inline(listOf(bad)))
        val good = InlineParser.parse("""<span style="color:#1E88E5">z</span>""")
        assertEquals("""<span style="color:#1E88E5">z</span>""", HtmlRenderer.inline(good))
    }

    @Test fun highlighterHidesTagsAndMarkers() {
        val t = """**b** <span style="color:#1E88E5">blue</span> [site](https://x.org)"""
        val spans = MarkdownHighlighter.spans(t)
        val syntax = spans.filter { it.kind == MarkdownHighlighter.Kind.SYNTAX }.map { t.substring(it.start, it.end) }
        assertTrue(syntax.toString(), syntax.containsAll(listOf("**", "<span style=\"color:#1E88E5\">", "</span>", "[", "](https://x.org)")))
        val color = spans.single { it.kind == MarkdownHighlighter.Kind.COLOR }
        assertEquals("blue", t.substring(color.start, color.end))
        assertEquals(0xFF1E88E5, color.value)
    }

    @Test fun applyStyleWrapsUpdatesAndRemoves() {
        val v = TextValue("make this red", 5, 9)
        val red = EditorCommands.setColor(v, 0xFFE53935)
        assertEquals("""make <span style="color:#E53935">this</span> red""", red.text)
        assertEquals("this", red.selectedText)
        // Adding a font to the same selection updates the same span instead of nesting.
        val both = EditorCommands.setFont(red, NoteFont.LORA)
        assertEquals("""make <span style="color:#E53935;font-family:Lora">this</span> red""", both.text)
        assertEquals("this", both.selectedText)
        val cleared = EditorCommands.clearStyles(both)
        assertEquals("make this red", cleared.text)
        assertEquals("this", cleared.selectedText)
    }

    @Test fun emptySelectionInsertsStyledSpanForTyping() {
        val v = EditorCommands.setHighlight(TextValue("ab", 1), 0xFFC8E6C9)
        assertEquals("""a<span style="background-color:#C8E6C9"></span>b""", v.text)
        assertEquals(v.text.indexOf("</span>"), v.selStart)
    }

    @Test fun paletteAndFonts() {
        assertEquals(0xFFAABBCC, Palette.parseHex("#ABC"))
        assertNull(Palette.parseHex("javascript:x"))
        assertEquals("#FF0000", Palette.toHex(Palette.fromHsv(0f, 1f, 1f)))
        assertEquals("#0000FF", Palette.toHex(Palette.fromHsv(240f, 1f, 1f)))
        assertEquals(32, Palette.ink.size)
        assertEquals(NoteFont.PATRICK_HAND, NoteFont.from("'Patrick Hand', cursive"))
        assertEquals(NoteFont.LORA, NoteFont.from("lora"))
        assertEquals(NoteFont.SERIF, NoteFont.from("Times New Roman"))
    }
}
