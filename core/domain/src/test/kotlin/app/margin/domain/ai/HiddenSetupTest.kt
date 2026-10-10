package app.margin.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenSetupTest {
    private fun SecretTapCounter.tapEvery(ms: Long, times: Int, start: Long = 1_000): List<Boolean> = (0 until times).map { tap(start + it * ms) }

    @Test fun tenQuickTapsUnlockOnTheTenthOnly() {
        val r = SecretTapCounter().tapEvery(200, 10)
        assertEquals(List(9) { false } + true, r)
    }

    @Test fun nineTapsDoNothing() = assertTrue(SecretTapCounter().tapEvery(200, 9).none { it })

    @Test fun aPauseRestartsTheCount() {
        val c = SecretTapCounter()
        assertTrue(c.tapEvery(200, 9).none { it })
        assertFalse(c.tap(1_000 + 8 * 200 + SECRET_TAP_WINDOW_MS + 1)) // pause too long: this is tap 1 again
        assertTrue(c.tapEvery(200, 9, start = 50_000).none { it })    // a new run of 9 more: still 10 needed from the restart
    }

    @Test fun countStartsOverAfterUnlocking() {
        val c = SecretTapCounter()
        assertTrue(c.tapEvery(100, 10).last())
        assertFalse(c.tap(2_100))
        assertTrue(c.tapEvery(100, 9, start = 2_200).last())
    }

    @Test fun keyPlausibility() {
        // Built at runtime so the repository's secret scanner never sees a key-shaped literal.
        val prefix = "sk" + "-"
        assertTrue(looksLikeOpenAiKey(prefix + "proj-" + "a".repeat(30)))
        assertTrue(looksLikeOpenAiKey("  " + prefix + "b".repeat(20) + "  "))
        assertFalse(looksLikeOpenAiKey("c".repeat(30)))
        assertFalse(looksLikeOpenAiKey(prefix + "short"))
        assertFalse(looksLikeOpenAiKey(prefix + "abc " + "d".repeat(20)))
    }
}
