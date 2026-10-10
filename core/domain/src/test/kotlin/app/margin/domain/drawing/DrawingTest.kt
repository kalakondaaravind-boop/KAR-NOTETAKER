package app.margin.domain.drawing

import app.margin.domain.model.PageStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DrawingTest {
    private var n = 0
    private val ids = { "e${++n}" }
    private fun line(id: String, x0: Float, y: Float, x1: Float, color: Long = 0xFF000000) =
        Stroke(id, (0..20).map { Pt(x0 + (x1 - x0) * it / 20f, y) }, color, 4f)

    private val base = Drawing(elements = listOf(
        line("a", 100f, 100f, 300f),
        line("b", 100f, 400f, 300f, 0xFFE53935),
        Shape("s", ShapeKind.RECT, 500f, 500f, 600f, 600f, 0xFF1E88E5, 3f),
        Clipart("c", "star", 800f, 800f, 96f, 0xFFFDD835),
        TextBox("t", 100f, 900f, "Hello\nworld", 0xFF000000, 28f, "caveat", italic = true),
    ))

    @Test fun lassoSelectsWhatIsInsideTheLoop() {
        val loop = listOf(Pt(50f, 50f), Pt(350f, 50f), Pt(350f, 150f), Pt(50f, 150f))
        assertEquals(setOf("a"), DrawingOps.lasso(base, loop))
        val big = listOf(Pt(0f, 0f), Pt(700f, 0f), Pt(700f, 700f), Pt(0f, 700f))
        assertEquals(setOf("a", "b", "s"), DrawingOps.lasso(base, big))
    }

    @Test fun cutCopyPasteDuplicate() {
        val sel = setOf("a", "s")
        val clip = DrawingOps.copy(base, sel)
        assertEquals(listOf("a", "s"), clip.map { it.id })
        // cut = copy + delete
        val cut = DrawingOps.delete(base, sel)
        assertEquals(3, cut.elements.size)
        assertNull(cut.byId("a"))
        // paste gets fresh ids, offset, and does not touch the originals
        val (pasted, newIds) = DrawingOps.paste(cut, clip, ids)
        assertEquals(5, pasted.elements.size)
        assertTrue(newIds.none { it in sel })
        val b0 = DrawingOps.selectionBounds(base, sel)!!
        val b1 = DrawingOps.selectionBounds(pasted, newIds)!!
        assertEquals(b0.left + DrawingOps.PASTE_OFFSET, b1.left, 0.01f)
        // paste at a point puts the group's top-left there
        val (at, atIds) = DrawingOps.paste(cut, clip, ids, Pt(10f, 20f))
        val b2 = DrawingOps.selectionBounds(at, atIds)!!
        assertEquals(10f, b2.left, 0.01f); assertEquals(20f, b2.top, 0.01f)
        // duplicate
        val (dup, dupIds) = DrawingOps.duplicate(base, setOf("c"), ids)
        assertEquals(6, dup.elements.size)
        assertEquals(800f + DrawingOps.PASTE_OFFSET, (dup.byId(dupIds.single()) as Clipart).x, 0.01f)
    }

    @Test fun moveScaleRecolorAndLayering() {
        val moved = DrawingOps.move(base, setOf("s"), 10f, -5f)
        assertEquals(510f, (moved.byId("s") as Shape).x1, 0.01f)
        val scaled = DrawingOps.scale(base, setOf("c"), 2f, 2f)
        assertEquals(192f, (scaled.byId("c") as Clipart).size, 0.01f)
        val red = DrawingOps.recolor(base, setOf("a", "t"), 0xFFFF0000)
        assertEquals(0xFFFF0000, (red.byId("a") as Stroke).color); assertEquals(0xFFFF0000, (red.byId("t") as TextBox).color)
        assertEquals("a", DrawingOps.bringToFront(base, setOf("a")).elements.last().id)
        assertEquals("t", DrawingOps.sendToBack(base, setOf("t")).elements.first().id)
    }

    @Test fun partialEraserSplitsStrokesWholeEraserRemoves() {
        val eraserPath = listOf(Pt(200f, 80f), Pt(200f, 120f))
        val split = DrawingOps.erase(base, eraserPath, 10f, partial = true, newId = ids)
        val pieces = split.elements.filterIsInstance<Stroke>().filter { it.points.all { p -> p.y == 100f } }
        assertEquals(2, pieces.size)
        assertTrue(pieces.all { s -> s.points.none { kotlin.math.abs(it.x - 200f) < 10f } })
        val whole = DrawingOps.erase(base, eraserPath, 10f, partial = false, newId = ids)
        assertNull(whole.byId("a"))
        assertEquals(base.elements.size - 1, whole.elements.size)
        // something far away is untouched
        assertTrue(DrawingOps.erase(base, listOf(Pt(5000f, 5000f)), 10f, true, ids) === base || DrawingOps.erase(base, listOf(Pt(5000f, 5000f)), 10f, true, ids) == base)
    }

    @Test fun undoRedo() {
        val h = DrawingHistory(base)
        assertFalse(h.canUndo)
        val d1 = DrawingOps.delete(base, setOf("a")); h.push(d1)
        val d2 = DrawingOps.delete(d1, setOf("b")); h.push(d2)
        h.push(d2) // identical state is not recorded
        assertEquals(d1, h.undo()); assertEquals(base, h.undo()); assertEquals(base, h.undo())
        assertEquals(d1, h.redo())
        h.push(DrawingOps.clear(d1))
        assertFalse(h.canRedo)
    }

    @Test fun hitTestFindsTopmost() {
        assertEquals("a", DrawingOps.hitTest(base, 200f, 102f)?.id)
        assertEquals("s", DrawingOps.hitTest(base, 550f, 550f)?.id)
        assertNull(DrawingOps.hitTest(base, 3000f, 3000f))
    }

    @Test fun strokesAreSimplifiedButKeepTheirShape() {
        val raw = (0..400).map { Pt(it.toFloat(), 50f + if (it == 200) 30f else 0f) }
        val done = DrawingOps.finishStroke(raw)
        assertTrue(done.size < 20)
        assertEquals(raw.first(), done.first()); assertEquals(raw.last(), done.last())
        assertTrue(done.any { it.y == 80f }) // the peak survives
    }

    @Test fun codecRoundTripsAndRejectsGarbage() {
        val d = base.copy(page = PageStyle.GRID, background = 0xFFFFF8E1)
        val back = DrawingCodec.decode(DrawingCodec.encode(d))
        assertEquals(d.page, back.page); assertEquals(d.background, back.background)
        assertEquals(d.elements.map { it.id }, back.elements.map { it.id })
        assertEquals(d.elements[2], back.elements[2]); assertEquals(d.elements[3], back.elements[3]); assertEquals(d.elements[4], back.elements[4])
        assertEquals((d.elements[0] as Stroke).points.size, (back.elements[0] as Stroke).points.size)
        for (bad in listOf("", "[]", "{}", """{"format":"other"}""", """{"format":"kar-drawing","version":99}""")) {
            try { DrawingCodec.decode(bad); fail("accepted $bad") } catch (e: DrawingCodec.InvalidDrawing) { }
        }
        // hostile values are clamped / dropped, never crash
        val hostile = """{"format":"kar-drawing","version":1,"width":1e30,"elements":[
            {"id":"x","type":"stroke","pts":[1,2,1, "NaN",5,1],"width":99999},
            {"id":"x","type":"clipart","key":"star"},{"type":"stroke"},{"id":"y","type":"alien"}]}"""
        val h = DrawingCodec.decode(hostile)
        assertEquals(Drawing.MAX_PAGE, h.width, 0f)
        assertEquals(1, h.elements.size) // duplicate id, missing id and unknown type dropped
        assertEquals(200f, (h.elements[0] as Stroke).width, 0f)
    }

    @Test fun svgExportContainsEveryElementAndEscapesText() {
        val d = base.copy(elements = base.elements + TextBox("x", 0f, 0f, "<script>&", 0xFF000000), page = PageStyle.RULED)
        val svg = DrawingSvg.render(d, clipartPath = { if (it == "star") "M12 2L15 9H22L16 14L18 21L12 17L6 21L8 14L2 9H9Z" else null })
        assertTrue(svg.startsWith("<svg"))
        assertTrue(svg.contains("<path d=\"M100 100"))
        assertTrue(svg.contains("<rect x=\"500\""))
        assertTrue(svg.contains("scale(4)"))           // 96 / 24
        assertTrue(svg.contains("font-style:italic"))
        assertTrue(svg.contains("&lt;script&gt;&amp;"))
        assertFalse(svg.contains("<script>"))
        assertTrue(svg.contains("<line"))              // ruled paper
    }

    @Test fun pageGeometryForEveryStyle() {
        for (s in PageStyle.entries) {
            val marks = PageGeometry.marks(s, 1200f, 1700f)
            if (s == PageStyle.PLAIN) assertTrue(marks.isEmpty()) else assertTrue("$s has no marks", marks.isNotEmpty())
        }
        val todo = PageGeometry.marks(PageStyle.TODO, 1200f, 600f)
        assertTrue(todo.count { it is PageGeometry.CheckBox } > 0)
        assertEquals(todo.count { it is PageGeometry.Line }, todo.count { it is PageGeometry.CheckBox })
        assertEquals(6, PageGeometry.marks(PageStyle.STORYBOARD, 1200f, 1700f).count { it is PageGeometry.Box })
    }
}
