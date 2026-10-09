package app.margin.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SearchTest {
    private val now = Instant.parse("2025-03-01T12:00:00Z")
    private val engine = SearchEngine(ZoneOffset.UTC)

    private fun doc(
        id: String, title: String, body: String = "", tags: List<String> = emptyList(), folder: List<String> = emptyList(),
        updated: Instant = now, created: Instant = now, trashed: Boolean = false, archived: Boolean = false,
        pinned: Boolean = false, att: String = "", attCount: Int = 0, open: Int = 0,
    ) = SearchDocument(id, title, body, tags, folder, att, created, updated, pinned, false, archived, trashed, attCount, open, 0, 0)

    private fun ids(q: String, docs: List<SearchDocument>) = engine.search(QueryParser.parse(q), docs.asSequence(), now).map { it.id }

    private val docs = listOf(
        doc("1", "Quarterly budget", "Review the marketing spend and travel costs", tags = listOf("finance")),
        doc("2", "Trip to Zürich", "Book hotel near the lake. Café list.", tags = listOf("travel/europe"), folder = listOf("Personal")),
        doc("3", "Meeting notes", "Budget discussion with the team; next steps", tags = listOf("work")),
        doc("4", "Deleted thing", "budget secrets", trashed = true),
        doc("5", "Archived budget", "old budget", archived = true),
        doc("6", "Scan", "", att = "Invoice total 42 EUR receipt", attCount = 1),
    )

    @Test fun caseAndPrefixAndAccents() {
        assertEquals(setOf("1", "3"), ids("BUDG", docs).toSet())
        assertEquals(listOf("2"), ids("zurich", docs))
        assertEquals(listOf("2"), ids("cafe", docs))
    }

    @Test fun titleMatchesRankHigher() {
        assertEquals("1", ids("budget", docs).first())
    }

    @Test fun trashAndArchiveExcludedByDefault() {
        assertFalse("4" in ids("budget", docs))
        assertFalse("5" in ids("budget", docs))
        assertEquals(listOf("4"), ids("budget is:trashed", docs))
        assertEquals(listOf("5"), ids("budget is:archived", docs))
    }

    @Test fun phrasesExclusionsAndBoolean() {
        assertEquals(listOf("2"), ids("\"hotel near\"", docs))
        assertTrue(ids("\"near hotel\"", docs).isEmpty())
        assertEquals(listOf("1"), ids("budget -team", docs))
        assertEquals(setOf("2", "3"), ids("lake OR meeting", docs).toSet())
        assertEquals(setOf("1", "2"), ids("(lake OR marketing) -team", docs).toSet())
        assertEquals(setOf("1"), ids("budget AND NOT discussion", docs).toSet())
    }

    @Test fun fieldFilters() {
        assertEquals(listOf("3"), ids("title:meeting", docs))
        assertTrue(ids("title:discussion", docs).isEmpty())
        assertEquals(listOf("2"), ids("tag:travel", docs)) // hierarchical
        assertEquals(listOf("2"), ids("folder:personal", docs))
        assertEquals(listOf("6"), ids("has:attachment", docs))
        assertEquals(listOf("6"), ids("invoice", docs)) // OCR/attachment text searchable
    }

    @Test fun dateFilters() {
        val old = doc("o", "Old note", "x", updated = Instant.parse("2024-01-10T10:00:00Z"), created = Instant.parse("2023-12-01T00:00:00Z"))
        val all = docs + old
        assertEquals(listOf("o"), ids("before:2024-01-11", all))
        assertTrue("o" !in ids("after:2024-01-10 note", all))
        assertEquals(listOf("o"), ids("created:2023-12-01", all))
        assertEquals(listOf("o"), ids("created:..2023-12-31", all))
    }

    @Test fun parserWarningsAndEdgeCases() {
        val q = QueryParser.parse("after:2024-13-45 hello")
        assertTrue(q.warnings.isNotEmpty())
        assertTrue(QueryParser.parse("   ").isEmpty)
        assertTrue(QueryParser.parse("-").isEmpty)
        QueryParser.parse("((((a")
        QueryParser.parse("a ) b")
        QueryParser.parse("\"unterminated phrase")
        val url = QueryParser.parse("https://example.com/x")
        assertTrue(url.root is QueryNode.Phrase)
        val dr = QueryParser.parse("created:2024-01-01..").root as QueryNode.DateRange
        assertEquals(LocalDate.of(2024, 1, 1), dr.from)
        assertNull(dr.toInclusive)
    }

    @Test fun fuzzyMatchesAreFlagged() {
        val hits = engine.search(QueryParser.parse("~budgte"), docs.asSequence(), now)
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.all { it.approximate })
        assertTrue(ids("budgte", docs).isEmpty()) // fuzzy only on request
    }

    @Test fun fuzzyDistance() {
        assertEquals(1, Fuzzy.distance("budget", "budgte"))
        assertEquals(1, Fuzzy.distance("kitten", "sitten"))
        assertTrue(Fuzzy.close("meeting", "meting"))
        assertFalse(Fuzzy.close("cat", "cut")) // too short for typos
    }

    @Test fun snippetsHighlightMatches() {
        val body = "Intro. " + "filler ".repeat(40) + "The target word appears here. " + "tail ".repeat(40)
        val d = doc("s", "Snippet", body)
        val h = engine.snippet(QueryParser.parse("target"), d)
        assertTrue(h.text.startsWith("…"))
        assertTrue(h.text.endsWith("…"))
        assertEquals(1, h.ranges.size)
        assertEquals("target", h.text.substring(h.ranges[0].first, h.ranges[0].last + 1))
        val accent = engine.snippet(QueryParser.parse("cafe"), docs[1])
        assertEquals("Café", accent.text.substring(accent.ranges[0].first, accent.ranges[0].last + 1))
        val title = engine.highlightTitle(QueryParser.parse("budg"), "Quarterly budget")
        assertEquals(listOf(10..15), title.ranges)
    }

    @Test fun ftsCandidateExpression() {
        assertEquals("budget* OR \"hotel near\"", FtsQueryBuilder.candidates(QueryParser.parse("budget \"hotel near\" -team")))
        assertNull(FtsQueryBuilder.candidates(QueryParser.parse("is:pinned")))
        assertNull(FtsQueryBuilder.candidates(QueryParser.parse("-budget")))
        assertNull(FtsQueryBuilder.candidates(QueryParser.parse("budget OR is:pinned")))
        assertNull(FtsQueryBuilder.candidates(QueryParser.parse("tag:x OR word")))
        assertEquals("word*", FtsQueryBuilder.candidates(QueryParser.parse("tag:x word")))
        assertNull(FtsQueryBuilder.candidates(QueryParser.parse("~fuzzy")))
        assertEquals("zurich*", FtsQueryBuilder.candidates(QueryParser.parse("Zürich")))
    }

    @Test fun indexReflectsUpdatesBecauseDocumentsAreCanonical() {
        // Regression #9/#10: search runs over current documents, so an edit or a delete is reflected immediately.
        val before = listOf(doc("x", "Alpha", "first"))
        assertEquals(listOf("x"), ids("first", before))
        val after = listOf(doc("x", "Alpha", "second"))
        assertTrue(ids("first", after).isEmpty())
        assertEquals(listOf("x"), ids("second", after))
        val deleted = listOf(doc("x", "Alpha", "second", trashed = true))
        assertTrue(ids("second", deleted).isEmpty())
    }

    @Test fun largeLibraryPerformanceSmoke() {
        val words = listOf("alpha", "beta", "gamma", "delta", "epsilon", "project", "budget", "meeting", "travel", "recipe")
        val big = (0 until 10_000).map { i ->
            doc("n$i", "Note $i ${words[i % words.size]}", (0 until 60).joinToString(" ") { words[(i * 7 + it) % words.size] } + " unique$i")
        }
        val q = QueryParser.parse("budget meeting -travel")
        val start = System.nanoTime()
        engine.search(q, big.asSequence(), now)
        val ms = (System.nanoTime() - start) / 1_000_000
        println("search over 10k docs (JVM, full scan worst case): ${ms}ms")
        assertTrue("took $ms ms", ms < 10_000)
    }
}
