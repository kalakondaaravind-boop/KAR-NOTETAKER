package app.margin.domain.revision

import app.margin.domain.model.NoteRevision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class RevisionPolicyTest {
    private val now = Instant.parse("2025-01-10T10:00:00Z")
    private val s = RevisionSettings()

    @Test fun snapshotRules() {
        assertTrue(RevisionPolicy.shouldSnapshot("t", "old", "t", "old!", null, now, s))
        assertFalse(RevisionPolicy.shouldSnapshot("t", "same", "t", "same", null, now, s))
        assertFalse(RevisionPolicy.shouldSnapshot("", "", "t", "new", null, now, s))
        val recent = now.minusSeconds(60)
        assertFalse(RevisionPolicy.shouldSnapshot("t", "a long body of text", "t", "a long body of text!", recent, now, s))
        assertTrue(RevisionPolicy.shouldSnapshot("t", "a long body of text", "t", "completely different", recent, now, s))
        assertTrue(RevisionPolicy.shouldSnapshot("t", "a", "t", "ab", now.minus(Duration.ofMinutes(11)), now, s))
        assertFalse(RevisionPolicy.shouldSnapshot("t", "a", "t", "ab", null, now, s.copy(enabled = false)))
    }

    @Test fun changeSize() {
        assertEquals(0, RevisionPolicy.changeSize("abc", "abc"))
        assertEquals(1, RevisionPolicy.changeSize("abc", "abxc"))
        assertEquals(3, RevisionPolicy.changeSize("abc", "xyz"))
    }

    @Test fun pruneKeepsPinnedAndNewest() {
        val revs = (0 until 60).map { i ->
            NoteRevision("r$i", "n", "t", "b", now.minus(Duration.ofDays(i.toLong() * 2)), pinned = i == 59)
        }
        val pruned = RevisionPolicy.toPrune(revs, now, s).map { it.id }.toSet()
        assertFalse("r0" in pruned)
        assertFalse("r59" in pruned) // pinned
        assertTrue("r50" in pruned) // over count
        assertTrue("r46" in pruned) // 92 days old
        assertFalse("r44" in pruned) // 88 days
    }

    @Test fun neverPrunesTheOnlyRevision() {
        val old = NoteRevision("r", "n", "t", "b", now.minus(Duration.ofDays(400)))
        assertTrue(RevisionPolicy.toPrune(listOf(old), now, s).isEmpty())
    }
}
