package app.margin.domain.settings

import app.margin.domain.ai.AiError
import app.margin.domain.ai.AiResult
import java.time.Duration
import java.time.Instant

/**
 * Backup health. Everything lives only on the tablet, so a lost or broken device means lost notes
 * unless the user keeps backup files. This decides when Home should nudge (and stays quiet otherwise).
 */
object BackupHealth {
    enum class Status { OK, NEVER_BACKED_UP, OVERDUE }

    /** Below this many notes there is too little at stake to nag a brand-new user. */
    const val MIN_NOTES_FOR_REMINDER = 3
    val SNOOZE: Duration = Duration.ofDays(3)

    fun status(s: AppSettings, noteCount: Int, now: Instant): Status {
        if (s.backupReminderDays == 0 || noteCount < MIN_NOTES_FOR_REMINDER) return Status.OK
        if (s.backupReminderSnoozedUntil?.isAfter(now) == true) return Status.OK
        val last = s.lastBackupAt ?: return Status.NEVER_BACKED_UP
        return if (Duration.between(last, now) > Duration.ofDays(s.backupReminderDays.toLong())) Status.OVERDUE else Status.OK
    }

    fun recordBackup(s: AppSettings, now: Instant) = s.copy(lastBackupAt = now, backupReminderSnoozedUntil = null)
    fun snooze(s: AppSettings, now: Instant) = s.copy(backupReminderSnoozedUntil = now.plus(SNOOZE))
}

/** AI transparency: which outcomes mean a request was actually transmitted to a cloud provider. */
object AiAudit {
    /** Errors raised on the device before anything is sent. */
    private val NEVER_SENT = setOf(AiError.DISABLED, AiError.NOT_CONFIGURED, AiError.CONSENT_REQUIRED, AiError.UNSUPPORTED, AiError.NOTE_EXCLUDED, AiError.CANCELLED)

    fun reachedCloud(result: AiResult, cloudProvider: Boolean): Boolean = cloudProvider && when (result) {
        is AiResult.Success -> !result.local
        is AiResult.Failure -> result.error !in NEVER_SENT
    }

    fun record(s: AppSettings, now: Instant) = s.copy(aiCloudRequests = s.aiCloudRequests + 1, aiCloudLastAt = now)
}
