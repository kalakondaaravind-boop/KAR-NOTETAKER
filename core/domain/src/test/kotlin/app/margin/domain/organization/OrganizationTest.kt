package app.margin.domain.organization

import app.margin.domain.model.Folder
import app.margin.domain.model.NoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class OrganizationTest {
    private val t0 = Instant.parse("2025-01-01T00:00:00Z")
    private fun f(id: String, parent: String?, name: String = id) = Folder(id, name, parent, createdAt = t0)

    @Test fun tagKeysIgnoreCaseAndWhitespace() {
        assertEquals("work", Tags.key("  Work "))
        assertEquals("work", Tags.key("#WORK"))
        assertEquals("deep work", Tags.key("Deep   Work"))
        assertEquals("work/clients", Tags.key("Work / Clients"))
        assertEquals("Deep Work", Tags.clean("  Deep   Work "))
        assertEquals(NameError.EMPTY, Tags.validate("  # "))
        assertEquals(NameError.INVALID_CHARACTERS, Tags.validate("a,b"))
        assertEquals(NameError.TOO_LONG, Tags.validate("x".repeat(65)))
        assertNull(Tags.validate("ok"))
    }

    @Test fun parseTagListDeduplicates() {
        assertEquals(listOf("Work", "home"), Tags.parseList("Work, work ,#WORK, home,,"))
    }

    @Test fun hierarchicalTags() {
        assertTrue(Tags.matchesHierarchy("work", "work/clients"))
        assertTrue(Tags.matchesHierarchy("work", "work"))
        assertFalse(Tags.matchesHierarchy("work", "workshop"))
    }

    @Test fun tagSuggestions() {
        val existing = listOf("work", "Workshop", "home", "network", "work/clients")
        assertEquals(listOf("work", "Workshop", "work/clients", "network"), Tags.suggest("wor", existing, emptyList()))
        assertEquals(listOf("Workshop", "work/clients", "network"), Tags.suggest("wor", existing, listOf("WORK")))
    }

    @Test fun folderMoveValidation() {
        val all = listOf(f("a", null), f("b", "a"), f("c", "b"), f("d", null), f("e", "d"))
        assertEquals(Folders.MoveError.INTO_ITSELF, Folders.validateMove("a", "a", all))
        assertEquals(Folders.MoveError.INTO_DESCENDANT, Folders.validateMove("a", "c", all))
        assertNull(Folders.validateMove("b", "d", all)) // d(1) + subtree b-c (2) = 3
        assertNull(Folders.validateMove("b", null, all))
        // e is depth 2 → adding subtree a (height 3) gives 5 > 4
        assertEquals(Folders.MoveError.TOO_DEEP, Folders.validateMove("a", "e", all))
        assertEquals(Folders.MoveError.NOT_FOUND, Folders.validateMove("zz", null, all))
    }

    @Test fun depthLimitForNewChildren() {
        val all = listOf(f("1", null), f("2", "1"), f("3", "2"), f("4", "3"))
        assertTrue(Folders.canCreateChild("3", all))
        assertFalse(Folders.canCreateChild("4", all))
        assertTrue(Folders.canCreateChild(null, all))
    }

    @Test fun cyclicDataDoesNotHang() {
        val all = listOf(f("x", "y"), f("y", "x"))
        assertEquals(Int.MAX_VALUE, Folders.depthOf("x", all.associateBy { it.id }))
        assertEquals(2, Folders.path("x", all.associateBy { it.id }).size)
    }

    @Test fun uniqueSiblingNames() {
        val all = listOf(f("a", null, "Ideas"), f("b", null, "Ideas (2)"), f("c", "a", "Ideas"))
        assertEquals("ideas (3)", Folders.uniqueSiblingName("ideas", null, all))
        assertEquals("Ideas (2)", Folders.uniqueSiblingName("Ideas", "a", all.filter { it.id != "c" } + f("c", "a", "Ideas")))
        assertEquals("Other", Folders.uniqueSiblingName(" Other ", null, all))
        assertEquals("Ideas", Folders.uniqueSiblingName("Ideas", null, all, excludeId = "a"))
    }

    @Test fun flattenAndDeletePlan() {
        val all = listOf(f("b", null, "Beta"), f("a", null, "Alpha"), f("a1", "a", "One"), f("a1x", "a1", "Deep"))
        assertEquals(listOf("a" to 0, "a1" to 1, "a1x" to 2, "b" to 0), Folders.flatten(all).map { it.first.id to it.second })
        assertEquals(setOf("a", "a1", "a1x"), Folders.deletePlan("a", all, FolderDeletePolicy.MOVE_NOTES_TO_INBOX).folderIds)
        assertEquals(listOf("Alpha", "One", "Deep"), Folders.path("a1x", all.associateBy { it.id }).map { it.name })
    }

    private fun facts(
        id: String = "n", state: NoteState = NoteState.ACTIVE, folder: String? = null, tags: Set<String> = emptySet(),
        words: Int = 10, open: Int = 0, links: Int = 0, att: Int = 0, created: Instant = t0, updated: Instant = t0,
        imported: Instant? = null, reviewed: Instant? = null, pinned: Boolean = false, favorite: Boolean = false,
    ) = NoteFacts(id, state, folder, tags, pinned, favorite, created, updated, att, words, open, 0, links, imported, reviewed, false)

    @Test fun smartCollectionsDeriveMembership() {
        val now = t0.plus(Duration.ofDays(100))
        assertTrue(SmartCollection.UNTAGGED.filter(now).matches(facts(), now))
        assertFalse(SmartCollection.UNTAGGED.filter(now).matches(facts(tags = setOf("x")), now))
        assertTrue(SmartCollection.WITH_TASKS.filter(now).matches(facts(open = 1), now))
        assertTrue(SmartCollection.LONG_FORM.filter(now).matches(facts(words = 1500), now))
        assertTrue(SmartCollection.NOT_REVIEWED.filter(now).matches(facts(), now))
        assertFalse(SmartCollection.NOT_REVIEWED.filter(now).matches(facts(reviewed = now.minus(Duration.ofDays(5))), now))
        assertTrue(SmartCollection.ARCHIVED.filter(now).matches(facts(state = NoteState.ARCHIVED), now))
        assertFalse(SmartCollection.FAVORITES.filter(now).matches(facts(state = NoteState.TRASHED, favorite = true), now))
        assertTrue(SmartCollection.RECENTLY_EDITED.filter(now).matches(facts(updated = now.minusSeconds(60)), now))
        assertFalse(SmartCollection.RECENTLY_EDITED.filter(now).matches(facts(), now))
    }

    @Test fun tagFilterIsHierarchical() {
        val f = NoteFilter(tagKeys = setOf("work"))
        assertTrue(f.matches(facts(tags = setOf("work/clients")), t0))
        assertFalse(f.matches(facts(tags = setOf("workshop")), t0))
    }

    @Test fun sqlBuilderUsesParametersOnly() {
        val c = NoteFilterSql.build(NoteFilter(tagKeys = setOf("o'brien%"), minWords = 5), t0)
        assertFalse(c.where.contains("o'brien"))
        assertTrue(c.args.contains("o'brien%"))
        assertTrue(c.args.contains("o'brien\\%/%"))
    }
}
