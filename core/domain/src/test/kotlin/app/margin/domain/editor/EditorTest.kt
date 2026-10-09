package app.margin.domain.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class EditorTest {

    @Test fun boldWrapsSelectionAndUnwraps() {
        val v = TextValue("hello world", 6, 11)
        val b = EditorCommands.toggleInline(v, InlineStyle.BOLD)
        assertEquals("hello **world**", b.text)
        assertEquals("world", b.selectedText)
        val u = EditorCommands.toggleInline(b, InlineStyle.BOLD)
        assertEquals("hello world", u.text)
        assertEquals("world", u.selectedText)
    }

    @Test fun boldKeepsWhitespaceOutside() {
        val v = TextValue("say hi now", 3, 7) // " hi "
        assertEquals("say **hi** now", EditorCommands.toggleInline(v, InlineStyle.BOLD).text)
    }

    @Test fun inlineWithoutSelectionInsertsPair() {
        val v = TextValue("ab", 1)
        val r = EditorCommands.toggleInline(v, InlineStyle.CODE)
        assertEquals("a``b", r.text)
        assertEquals(2, r.selStart)
    }

    @Test fun underlineUsesHtmlTag() {
        assertEquals("<u>x</u>", EditorCommands.toggleInline(TextValue("x", 0, 1), InlineStyle.UNDERLINE).text)
    }

    @Test fun lineStylesApplyToSelectedLinesOnly() {
        val text = "keep\na\nb\nkeep"
        val v = TextValue(text, 5, 8)
        val r = EditorCommands.setLineStyle(v, LineStyle.NUMBERED)
        assertEquals("keep\n1. a\n2. b\nkeep", r.text)
        val back = EditorCommands.setLineStyle(r, LineStyle.NUMBERED)
        assertEquals(text, back.text)
    }

    @Test fun lineStyleSwitchesBetweenStyles() {
        val r = EditorCommands.setLineStyle(TextValue("- item", 3), LineStyle.CHECKLIST)
        assertEquals("- [ ] item", r.text)
        val h = EditorCommands.setLineStyle(TextValue("## Old", 2), LineStyle.HEADING1)
        assertEquals("# Old", h.text)
        val plain = EditorCommands.setLineStyle(TextValue("> q", 1), LineStyle.PLAIN)
        assertEquals("q", plain.text)
    }

    @Test fun indentAndOutdent() {
        val v = TextValue("- a\n- b", 0, 7)
        val i = EditorCommands.indent(v)
        assertEquals("  - a\n  - b", i.text)
        assertEquals("- a\n- b", EditorCommands.indent(i, outdent = true).text)
    }

    @Test fun listContinuation() {
        fun typeEnter(text: String): TextValue {
            val old = TextValue(text, text.length)
            val new = TextValue(text + "\n", text.length + 1)
            return EditorCommands.onTextChanged(old, new)
        }
        assertEquals("- a\n- ", typeEnter("- a").text)
        assertEquals("  * a\n  * ", typeEnter("  * a").text)
        assertEquals("9. a\n10. ", typeEnter("9. a").text)
        assertEquals("- [x] a\n- [ ] ", typeEnter("- [x] a").text)
        assertEquals("> q\n> ", typeEnter("> q").text)
        // Empty item terminates the list.
        val t = typeEnter("- a\n- ")
        assertEquals("- a\n", t.text)
        assertEquals(4, t.selStart)
        // Not a list → unchanged.
        assertEquals("plain\n", typeEnter("plain").text)
    }

    @Test fun listContinuationIgnoresPastes() {
        val old = TextValue("- a", 3)
        val new = TextValue("- a\nb", 5)
        assertEquals(new, EditorCommands.onTextChanged(old, new))
    }

    @Test fun listContinuationInMiddleOfText() {
        val old = TextValue("- a\nafter", 3)
        val new = TextValue("- a\n\nafter", 4)
        assertEquals("- a\n- \nafter", EditorCommands.onTextChanged(old, new).text)
    }

    @Test fun insertBlocks() {
        val v = TextValue("abc", 3)
        assertEquals("abc\n---\n", EditorCommands.insertDivider(v).text)
        val table = EditorCommands.insertTable(TextValue("", 0), 2, 1).text
        assertEquals("| Column 1 | Column 2 |\n| --- | --- |\n|   |   |", table)
        val code = EditorCommands.insertCodeBlock(TextValue("x", 0, 1))
        assertEquals("```\nx\n```", code.text)
        assertEquals(5, code.selStart)
        assertEquals("see [site](https://s.io)", EditorCommands.insertLink(TextValue("see site", 4, 8), "https://s.io").text)
        assertEquals("2024-03-01 09:05", EditorCommands.insertDateTime(TextValue(""), LocalDateTime.of(2024, 3, 1, 9, 5)).text)
    }

    @Test fun undoCoalescesTyping() {
        val u = UndoStack()
        var v = TextValue("")
        var t = 0L
        for (c in "hello") {
            val n = TextValue(v.text + c)
            u.record(v, n, t); v = n; t += 100
        }
        val back = u.undo(v)!!
        assertEquals("", back.text)
        assertFalse(u.canUndo)
        assertEquals("hello", u.redo(back)!!.text)
    }

    @Test fun undoSeparatesWordsAndPauses() {
        val u = UndoStack()
        var v = TextValue("")
        var t = 0L
        for (c in "ab cd") { val n = TextValue(v.text + c); u.record(v, n, t); v = n; t += 100 }
        t += 5_000
        val n = TextValue(v.text + "e"); u.record(v, n, t); v = n
        assertEquals("ab cd", u.undo(v)!!.text)
    }

    @Test fun undoLimit() {
        val u = UndoStack(limit = 3)
        var v = TextValue("")
        repeat(10) { i -> val n = TextValue("x".repeat(i + 3)); u.record(v, n, i * 10_000L); v = n }
        var count = 0
        while (u.canUndo) { v = u.undo(v)!!; count++ }
        assertEquals(3, count)
        assertNull(u.undo(v)?.takeIf { false })
        assertTrue(u.canRedo)
    }
}
