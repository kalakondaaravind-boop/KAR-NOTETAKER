package app.margin.domain.model

import app.margin.domain.text.MarkdownParser
import app.margin.domain.text.NoteFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMetaTest {
    @Test fun splitJoinRoundTrip() {
        val meta = NoteMeta(PageStyle.RULED, NoteFont.LORA)
        val body = NoteMeta.join(meta, "# Hello\n- [ ] task")
        assertTrue(body.startsWith("<!-- kar:page=ruled;font=lora -->\n"))
        val (m, content) = NoteMeta.split(body)
        assertEquals(meta, m); assertEquals("# Hello\n- [ ] task", content)
        assertEquals("plain", NoteMeta.join(NoteMeta(), "plain"))
        assertEquals(NoteMeta() to "no meta", NoteMeta.split("no meta"))
        // a directive later in the text is content, not meta
        assertEquals(NoteMeta(), NoteMeta.split("x\n<!-- kar:page=grid -->").first)
    }

    @Test fun directiveIsNeverShownAsContent() {
        val body = NoteMeta.join(NoteMeta(PageStyle.TODO), "Shopping\n- [ ] milk")
        assertEquals("Shopping", Note.derivedTitle(body))
        val plain = MarkdownParser.toPlainText(body)
        assertFalse(plain, plain.contains("kar:"))
        // line numbers are unchanged, so task toggling by line still works on the stored body
        val toggled = MarkdownParser.toggleTask(body, 2)
        assertTrue(toggled, toggled.contains("- [x] milk"))
    }
}
