package app.margin.feature.backup

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.margin.data.repo.ReminderScheduler
import app.margin.domain.repository.LibraryRepository
import app.margin.domain.repository.TaskRepository
import app.margin.domain.security.DataErasure
import app.margin.domain.security.EraseReport
import app.margin.domain.security.EraseStep
import app.margin.domain.settings.AppSettings
import app.margin.domain.settings.SecretStore
import app.margin.domain.settings.SettingsRepository
import app.margin.feature.ai.SECRET_AI_KEY
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Erases everything the app stores on this tablet (Settings → Privacy / Backup & data → Erase all data).
 * Backup files the user saved elsewhere (Downloads, SD card…) are deliberately untouched: the app does not
 * own them, and they are the user's way back.
 */
@Singleton
class DataEraser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val tasks: TaskRepository,
    private val scheduler: ReminderScheduler,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
) {
    suspend fun eraseEverything(): EraseReport = withContext(Dispatchers.IO) {
        DataErasure.run(
            mapOf<EraseStep, suspend () -> Unit>(
                EraseStep.REMINDERS to suspend { tasks.tasksWithReminders().forEach { scheduler.cancel(it.id) } },
                EraseStep.LIBRARY to suspend { library.wipeAll() },
                EraseStep.SECRETS to suspend { secrets.remove(SECRET_AI_KEY) },
                // Defaults: onboarding not completed, app lock off, AI off, logs and histories empty.
                EraseStep.SETTINGS to suspend { settings.update { AppSettings() } },
                EraseStep.CACHE to suspend {
                    context.cacheDir.listFiles()?.forEach { f -> if (!f.deleteRecursively()) error("could not delete ${f.name}") }
                },
                EraseStep.NOTIFICATIONS to suspend { NotificationManagerCompat.from(context).cancelAll() },
            ),
        )
    }
}
