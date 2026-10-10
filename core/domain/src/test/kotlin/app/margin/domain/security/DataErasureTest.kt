package app.margin.domain.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DataErasureTest {
    private fun recording(log: MutableList<EraseStep>, failOn: Set<EraseStep> = emptySet()): Map<EraseStep, suspend () -> Unit> =
        EraseStep.entries.associateWith { step -> suspend { log += step; if (step in failOn) throw IllegalStateException("disk error") } }

    @Test fun runsEveryStepInTheSafeOrder() = runBlocking {
        val log = mutableListOf<EraseStep>()
        val r = DataErasure.run(recording(log))
        assertTrue(r.isComplete)
        assertEquals(DataErasure.ORDER, log)
        assertEquals(EraseStep.REMINDERS, log.first()) // needs task ids, so before the library is wiped
        assertTrue(log.indexOf(EraseStep.LIBRARY) < log.indexOf(EraseStep.SETTINGS))
    }

    @Test fun aFailureIsReportedAndTheRestStillRuns() = runBlocking {
        val log = mutableListOf<EraseStep>()
        val r = DataErasure.run(recording(log, failOn = setOf(EraseStep.SECRETS)))
        assertFalse(r.isComplete)
        assertEquals(setOf(EraseStep.SECRETS), r.failed.keys)
        assertTrue(r.failed.getValue(EraseStep.SECRETS).contains("disk error"))
        assertEquals(DataErasure.ORDER, log)
        assertEquals(DataErasure.ORDER - EraseStep.SECRETS, r.completed)
    }

    @Test fun everyStepMustBeProvided() = runBlocking {
        try {
            DataErasure.run(mapOf(EraseStep.LIBRARY to suspend { }))
            fail("missing steps must be rejected")
        } catch (e: IllegalArgumentException) { }
    }
}
