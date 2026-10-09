package app.margin.domain.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeTest {
    private val docs = listOf(
        KnowledgeDoc("a", "Sourdough starter", "Feed the sourdough starter with flour and water daily. Starter hydration matters.", setOf("baking")),
        KnowledgeDoc("b", "Bread baking log", "Sourdough loaf: hydration 75%, flour blend, starter peak timing.", setOf("baking")),
        KnowledgeDoc("c", "Kubernetes upgrade", "Drain nodes, upgrade control plane, verify workloads.", setOf("work")),
    )

    @Test fun relatedNotesUseSharedTermsAndTags() {
        val r = RelatedNotes.find(docs[0], docs)
        assertEquals("b", r.first().id)
        assertTrue(RelationReason.SHARED_TAGS in r.first().reasons)
        assertTrue("sourdough" in r.first().sharedTerms)
        assertTrue(r.none { it.id == "c" })
        assertTrue(RelatedNotes.find(docs[0], listOf(docs[0])).isEmpty())
    }

    @Test fun duplicates() {
        val t = KnowledgeDoc("x", "Shopping", "milk eggs bread butter cheese apples", emptySet())
        val dup = KnowledgeDoc("y", "Shopping", "milk eggs bread butter cheese apples bananas", emptySet())
        val other = KnowledgeDoc("z", "Other", "completely unrelated words here today", emptySet())
        val found = Duplicates.findFor(t, listOf(t, dup, other))
        assertEquals(listOf("y"), found.map { it.id })
    }

    @Test fun linkResolutionAndOrphans() {
        val res = LinkGraph.resolve("See [[Alpha]] and [[missing]] and [x](margin://note/id2) [y](margin://note/gone)",
            mapOf("alpha" to "id1"), setOf("id1", "id2"))
        assertEquals(setOf("id1", "id2"), res.resolved)
        assertEquals(setOf("missing", "margin://note/gone"), res.missingTitles)
        assertEquals(setOf("c"), LinkGraph.orphans(listOf("a", "b", "c"), listOf("a" to "b")))
    }
}
