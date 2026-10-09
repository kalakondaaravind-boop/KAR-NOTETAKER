package app.margin.domain.editor

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class AutosaveTest {

    private fun draft(body: String, cursor: Int = 0) = Draft("n1", "T", body, cursor)

    @Test fun debouncesAndWritesLatestOnlyOnce() = runBlocking {
        val writes = Collections.synchronizedList(ArrayList<String>())
        val c = AutosaveController(this, persist = { writes += it.body }, debounceMs = 50)
        c.seed(draft(""))
        for (s in listOf("a", "ab", "abc")) { c.onEdit(draft(s)); delay(5) }
        assertEquals(SaveStatus.Pending, c.status.value)
        delay(200)
        assertEquals(listOf("abc"), writes)
        assertTrue(c.status.value is SaveStatus.Saved)
        assertFalse(c.hasUnsavedChanges)
    }

    @Test fun cursorOnlyChangesDoNotWrite() = runBlocking {
        val writes = ArrayList<String>()
        val c = AutosaveController(this, persist = { writes += it.body }, debounceMs = 20)
        c.seed(draft("x"))
        c.onEdit(draft("x", cursor = 1))
        delay(80)
        assertTrue(writes.isEmpty())
        assertEquals(SaveStatus.Idle, c.status.value)
    }

    @Test fun neverReportsSavedWhenPersistFails() = runBlocking {
        var calls = 0
        val c = AutosaveController(this, persist = { calls++; throw PersistenceException(SaveError.DATABASE_UNAVAILABLE) },
            debounceMs = 10, maxAttempts = 3, retryBaseMs = 10)
        c.seed(draft(""))
        c.onEdit(draft("data"))
        delay(300)
        val s = c.status.value
        assertTrue(s is SaveStatus.Failed)
        s as SaveStatus.Failed
        assertEquals(3, s.attempts)
        assertFalse(s.willRetry)
        assertEquals(3, calls)
        assertTrue(c.hasUnsavedChanges)
        assertEquals("data", c.currentDraft()!!.body) // content preserved in memory
    }

    @Test fun storageFullIsNotRetriedAutomatically() = runBlocking {
        var calls = 0
        val c = AutosaveController(this, persist = { calls++; throw PersistenceException(SaveError.STORAGE_FULL) }, debounceMs = 10)
        c.seed(draft(""))
        c.onEdit(draft("big"))
        delay(150)
        assertEquals(1, calls)
        assertEquals(SaveError.STORAGE_FULL, (c.status.value as SaveStatus.Failed).reason)
    }

    @Test fun recoversAfterTransientFailure() = runBlocking {
        var calls = 0
        val writes = ArrayList<String>()
        val c = AutosaveController(this, persist = {
            calls++
            if (calls == 1) throw RuntimeException("transient")
            writes += it.body
        }, debounceMs = 10, retryBaseMs = 10)
        c.seed(draft(""))
        c.onEdit(draft("ok"))
        delay(200)
        assertEquals(listOf("ok"), writes)
        assertTrue(c.status.value is SaveStatus.Saved)
    }

    @Test fun flushPersistsImmediately() = runBlocking {
        val writes = ArrayList<String>()
        val c = AutosaveController(this, persist = { writes += it.body }, debounceMs = 10_000)
        c.seed(draft(""))
        c.onEdit(draft("leave now"))
        assertTrue(c.flush())
        assertEquals(listOf("leave now"), writes)
        assertTrue(c.flush()) // nothing more to write
        assertEquals(1, writes.size)
    }

    @Test fun editDuringSaveIsWrittenAfterwards() = runBlocking {
        val writes = Collections.synchronizedList(ArrayList<String>())
        val c = AutosaveController(this, persist = { delay(60); writes += it.body }, debounceMs = 10)
        c.seed(draft(""))
        c.onEdit(draft("one"))
        delay(30) // first save in flight
        c.onEdit(draft("one two"))
        delay(300)
        assertEquals(listOf("one", "one two"), writes)
        assertTrue(c.status.value is SaveStatus.Saved)
    }
}
