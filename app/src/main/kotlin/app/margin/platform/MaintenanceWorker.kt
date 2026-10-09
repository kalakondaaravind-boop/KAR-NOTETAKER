package app.margin.platform

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.margin.domain.repository.NoteRepository
import app.margin.domain.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Daily housekeeping: permanently removes notes that have been in the Trash longer than the
 * user's retention setting (0 = keep forever). Runs only when the device is idle-ish and has
 * enough storage; never touches active or archived notes.
 */
@HiltWorker
class MaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val days = settings.current().trashRetentionDays
        if (days > 0) notes.purgeTrashOlderThanDays(days)
        Result.success()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val NAME = "maintenance"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
