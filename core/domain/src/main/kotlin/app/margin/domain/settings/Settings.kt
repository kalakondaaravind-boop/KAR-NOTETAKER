package app.margin.domain.settings

import app.margin.domain.ai.AiSettings
import app.margin.domain.ai.ProviderType
import app.margin.domain.organization.SortField
import app.margin.domain.organization.SortOrder
import app.margin.domain.revision.RevisionSettings
import app.margin.domain.security.LockSettings
import kotlinx.coroutines.flow.Flow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class HomeSection { QUICK_CAPTURE, PINNED, RECENT, FAVORITES, TASKS, FOLDERS, TEMPLATES, ATTACHMENTS }

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** KAR 3D glossy surfaces (depth, gradient, sheen). Off = flat Material 3. */
    val glossyStyle: Boolean = true,
    val reducedMotion: Boolean = false,
    /** Editor text scale (1.0 = system default). Clamped to 0.8–2.0. */
    val editorTextScale: Float = 1.0f,
    val editorMonospace: Boolean = false,
    /** Open notes in rendered preview instead of the editor. */
    val openInPreview: Boolean = false,
    val defaultSort: SortOrder = SortOrder(SortField.UPDATED, true),
    val defaultFolderId: String? = null,
    val defaultTemplateId: String? = null,
    val trashRetentionDays: Int = 30,
    val revisions: RevisionSettings = RevisionSettings(),
    val homeSections: List<HomeSection> = HomeSection.entries.toList(),
    val hiddenHomeSections: Set<HomeSection> = setOf(HomeSection.ATTACHMENTS),
    val searchHistoryEnabled: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    val notificationPreviews: Boolean = false,
    val defaultReminderOffsetMinutes: Int = 0,
    val quickCaptureNotification: Boolean = false,
    val lock: LockSettings = LockSettings(),
    val ai: AiSettings = AiSettings(),
    val aiBaseUrl: String = "",
    val aiModel: String = "",
    /** Whether an API key is stored (the key itself is kept encrypted, never in this object). */
    val aiKeyStored: Boolean = false,
    val clipboardCaptureSuggestion: Boolean = false,
    /** When the last backup file was written successfully (drives the Home backup reminder). */
    val lastBackupAt: java.time.Instant? = null,
    /** Remind on Home when the last backup is older than this many days; 0 turns the reminder off. */
    val backupReminderDays: Int = 14,
    val backupReminderSnoozedUntil: java.time.Instant? = null,
    /** Transparency log: AI requests that actually reached OpenAI, and when the last one did. */
    val aiCloudRequests: Int = 0,
    val aiCloudLastAt: java.time.Instant? = null,
) {
    val visibleHomeSections get() = homeSections.filter { it !in hiddenHomeSections }

    companion object {
        const val MAX_RECENT_SEARCHES = 15

        /** Normalises values loaded from storage (older or tampered preferences never crash the app). */
        fun sanitize(s: AppSettings) = s.copy(
            editorTextScale = s.editorTextScale.coerceIn(0.8f, 2.0f),
            trashRetentionDays = s.trashRetentionDays.coerceIn(0, 3650),
            revisions = s.revisions.copy(maxRevisionsPerNote = s.revisions.maxRevisionsPerNote.coerceIn(1, 1000)),
            homeSections = (s.homeSections.distinct() + HomeSection.entries.filter { it !in s.homeSections }),
            recentSearches = s.recentSearches.distinct().take(MAX_RECENT_SEARCHES),
            defaultReminderOffsetMinutes = s.defaultReminderOffsetMinutes.coerceIn(0, 60 * 24 * 7),
            backupReminderDays = s.backupReminderDays.coerceIn(0, 365),
            aiCloudRequests = s.aiCloudRequests.coerceAtLeast(0),
            ai = s.ai.copy(maxContextChars = s.ai.maxContextChars.coerceIn(2_000, 100_000),
                cloudConsent = s.ai.cloudConsent && s.ai.provider != ProviderType.NONE),
        )

        fun addRecentSearch(list: List<String>, query: String): List<String> {
            val q = query.trim()
            if (q.isEmpty()) return list
            return (listOf(q) + list.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
        }
    }
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Encrypted storage for user-supplied secrets (AI API keys). */
interface SecretStore {
    suspend fun put(name: String, value: String)
    suspend fun get(name: String): String?
    suspend fun remove(name: String)
}
