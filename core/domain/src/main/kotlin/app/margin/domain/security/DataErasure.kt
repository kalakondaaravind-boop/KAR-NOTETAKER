package app.margin.domain.security

import kotlinx.coroutines.CancellationException

/**
 * "Erase all data": every place the app keeps user data on the tablet, in a safe order.
 * Reminders are cancelled first (they need the task ids), the library last-but-one so a failure
 * there is reported before settings are reset.
 */
enum class EraseStep {
    /** Pending reminder alarms (AlarmManager) for every task. */
    REMINDERS,
    /** Notes, folders, tags, tasks, templates, history, saved searches, search index and attachment files. */
    LIBRARY,
    /** The OpenAI API key (Android Keystore-encrypted secret). */
    SECRETS,
    /** All settings, incl. app lock, AI log, backup history and search history; app returns to the welcome screen. */
    SETTINGS,
    /** Temporary files: camera captures, recordings, shared exports, restore staging. */
    CACHE,
    /** Posted notifications (reminders, quick capture). */
    NOTIFICATIONS,
}

data class EraseReport(val completed: List<EraseStep>, val failed: Map<EraseStep, String>) {
    val isComplete: Boolean get() = failed.isEmpty()
}

object DataErasure {
    /** Required, in this order. A missing step is a programming error, not a silent skip. */
    val ORDER: List<EraseStep> = listOf(EraseStep.REMINDERS, EraseStep.LIBRARY, EraseStep.SECRETS, EraseStep.SETTINGS, EraseStep.CACHE, EraseStep.NOTIFICATIONS)

    /**
     * Runs every step even if an earlier one fails (so as much as possible is erased), and reports
     * exactly which steps failed — the UI must never claim a complete erase when it wasn't.
     */
    suspend fun run(actions: Map<EraseStep, suspend () -> Unit>): EraseReport {
        require(actions.keys == ORDER.toSet()) { "erase actions must cover exactly ${ORDER}, got ${actions.keys}" }
        val done = ArrayList<EraseStep>()
        val failed = LinkedHashMap<EraseStep, String>()
        for (step in ORDER) {
            try {
                actions.getValue(step).invoke()
                done += step
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed[step] = e.javaClass.simpleName + (e.message?.let { ": " + it.take(120) } ?: "")
            }
        }
        return EraseReport(done, failed)
    }
}
