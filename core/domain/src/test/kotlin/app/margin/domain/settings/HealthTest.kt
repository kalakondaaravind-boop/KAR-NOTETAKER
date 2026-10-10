package app.margin.domain.settings

import app.margin.domain.ai.AiError
import app.margin.domain.ai.AiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class HealthTest {
    private val now = Instant.parse("2026-10-10T09:00:00Z")
    private val base = AppSettings()

    @Test fun quietForNewOrEmptyLibraries() {
        assertEquals(BackupHealth.Status.OK, BackupHealth.status(base, 0, now))
        assertEquals(BackupHealth.Status.OK, BackupHealth.status(base, BackupHealth.MIN_NOTES_FOR_REMINDER - 1, now))
    }

    @Test fun neverBackedUpOnceThereIsSomethingToLose() =
        assertEquals(BackupHealth.Status.NEVER_BACKED_UP, BackupHealth.status(base, 5, now))

    @Test fun overdueAfterTheInterval() {
        val recent = BackupHealth.recordBackup(base, now.minus(Duration.ofDays(13)))
        assertEquals(BackupHealth.Status.OK, BackupHealth.status(recent, 50, now))
        val old = BackupHealth.recordBackup(base, now.minus(Duration.ofDays(15)))
        assertEquals(BackupHealth.Status.OVERDUE, BackupHealth.status(old, 50, now))
    }

    @Test fun snoozeAndOff() {
        val snoozed = BackupHealth.snooze(base, now)
        assertEquals(BackupHealth.Status.OK, BackupHealth.status(snoozed, 50, now.plus(Duration.ofDays(2))))
        assertEquals(BackupHealth.Status.NEVER_BACKED_UP, BackupHealth.status(snoozed, 50, now.plus(Duration.ofDays(4))))
        assertEquals(BackupHealth.Status.OK, BackupHealth.status(base.copy(backupReminderDays = 0), 50, now))
        assertNull(BackupHealth.recordBackup(snoozed, now).backupReminderSnoozedUntil)
    }

    @Test fun valuesAreSanitised() {
        assertEquals(365, AppSettings.sanitize(base.copy(backupReminderDays = 9999)).backupReminderDays)
        assertEquals(0, AppSettings.sanitize(base.copy(aiCloudRequests = -4)).aiCloudRequests)
    }

    @Test fun onlyTransmittedRequestsAreCounted() {
        val sent = AiResult.Success("ok", "openai", local = false)
        assertTrue(AiAudit.reachedCloud(sent, cloudProvider = true))
        assertTrue(AiAudit.reachedCloud(AiResult.Failure(AiError.AUTH), true))
        assertTrue(AiAudit.reachedCloud(AiResult.Failure(AiError.SERVER, "HTTP 500"), true))
        assertFalse(AiAudit.reachedCloud(AiResult.Failure(AiError.CONSENT_REQUIRED), true))
        assertFalse(AiAudit.reachedCloud(AiResult.Failure(AiError.NOTE_EXCLUDED), true))
        assertFalse(AiAudit.reachedCloud(sent, cloudProvider = false))
        assertFalse(AiAudit.reachedCloud(AiResult.Success("x", "local", local = true), true))
        val logged = AiAudit.record(AiAudit.record(base, now), now)
        assertEquals(2, logged.aiCloudRequests)
        assertEquals(now, logged.aiCloudLastAt)
    }
}
