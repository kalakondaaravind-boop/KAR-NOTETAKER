package app.margin.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.margin.domain.ai.AiSettings
import app.margin.domain.ai.ProviderType
import app.margin.domain.organization.SortField
import app.margin.domain.organization.SortOrder
import app.margin.domain.revision.RevisionSettings
import app.margin.domain.security.LockSettings
import app.margin.domain.settings.AppSettings
import app.margin.domain.settings.HomeSection
import app.margin.domain.settings.SettingsRepository
import app.margin.domain.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration

/**
 * Preferences in Jetpack DataStore. Every value is read defensively (unknown enum names or
 * out-of-range numbers fall back to defaults) and the whole object goes through [AppSettings.sanitize].
 */
class SettingsRepositoryImpl(private val store: DataStore<Preferences>) : SettingsRepository {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val glossy = booleanPreferencesKey("glossy_style")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val textScale = floatPreferencesKey("editor_text_scale")
        val mono = booleanPreferencesKey("editor_monospace")
        val marks = booleanPreferencesKey("editor_show_marks")
        val openInPreview = booleanPreferencesKey("open_in_preview")
        val sortField = stringPreferencesKey("sort_field")
        val sortDesc = booleanPreferencesKey("sort_desc")
        val defaultFolder = stringPreferencesKey("default_folder")
        val defaultTemplate = stringPreferencesKey("default_template")
        val trashDays = intPreferencesKey("trash_retention_days")
        val revEnabled = booleanPreferencesKey("revisions_enabled")
        val revMax = intPreferencesKey("revisions_max")
        val revDays = intPreferencesKey("revisions_days")
        val revInterval = intPreferencesKey("revisions_interval_min")
        val homeOrder = stringPreferencesKey("home_sections")
        val homeHidden = stringPreferencesKey("home_hidden")
        val searchHistory = booleanPreferencesKey("search_history")
        val recentSearches = stringPreferencesKey("recent_searches")
        val notifPreview = booleanPreferencesKey("notification_previews")
        val reminderOffset = intPreferencesKey("default_reminder_offset")
        val quickCapture = booleanPreferencesKey("quick_capture_notification")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lockTimeout = longPreferencesKey("lock_timeout_seconds")
        val secureWindow = booleanPreferencesKey("secure_window")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiProvider = stringPreferencesKey("ai_provider")
        val aiConsent = booleanPreferencesKey("ai_cloud_consent")
        val aiKb = booleanPreferencesKey("ai_knowledge_base")
        val aiArchived = booleanPreferencesKey("ai_include_archived")
        val aiMaxChars = intPreferencesKey("ai_max_context")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
        val aiModel = stringPreferencesKey("ai_model")
        val aiKeyStored = booleanPreferencesKey("ai_key_stored")
        val clipboard = booleanPreferencesKey("clipboard_suggestion")
        val lastBackup = longPreferencesKey("last_backup_ms")
        val backupDays = intPreferencesKey("backup_reminder_days")
        val backupSnooze = longPreferencesKey("backup_snooze_until_ms")
        val aiCloudCount = intPreferencesKey("ai_cloud_requests")
        val aiCloudLast = longPreferencesKey("ai_cloud_last_ms")
    }

    private inline fun <reified E : Enum<E>> enumOr(v: String?, d: E): E = v?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: d
    private val sep = '\u001f'

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        val order = p[K.homeOrder]?.split(',')?.mapNotNull { n -> HomeSection.entries.firstOrNull { it.name == n } } ?: d.homeSections
        val hidden = p[K.homeHidden]?.let { h -> h.split(',').mapNotNull { n -> HomeSection.entries.firstOrNull { it.name == n } }.toSet() } ?: d.hiddenHomeSections
        return AppSettings.sanitize(
            AppSettings(
                onboardingCompleted = p[K.onboarding] ?: false,
                theme = enumOr(p[K.theme], ThemeMode.SYSTEM),
                dynamicColor = p[K.dynamicColor] ?: d.dynamicColor,
                glossyStyle = p[K.glossy] ?: d.glossyStyle,
                reducedMotion = p[K.reducedMotion] ?: false,
                editorTextScale = p[K.textScale] ?: 1f,
                editorMonospace = p[K.mono] ?: false,
                showFormattingMarks = p[K.marks] ?: false,
                openInPreview = p[K.openInPreview] ?: false,
                defaultSort = SortOrder(enumOr(p[K.sortField], SortField.UPDATED), p[K.sortDesc] ?: true),
                defaultFolderId = p[K.defaultFolder],
                defaultTemplateId = p[K.defaultTemplate],
                trashRetentionDays = p[K.trashDays] ?: d.trashRetentionDays,
                revisions = RevisionSettings(
                    snapshotInterval = Duration.ofMinutes((p[K.revInterval] ?: 10).toLong().coerceIn(1, 24 * 60)),
                    maxRevisionsPerNote = p[K.revMax] ?: 50,
                    maxAge = Duration.ofDays((p[K.revDays] ?: 90).toLong().coerceIn(1, 3650)),
                    enabled = p[K.revEnabled] ?: true,
                ),
                homeSections = order,
                hiddenHomeSections = hidden,
                searchHistoryEnabled = p[K.searchHistory] ?: true,
                recentSearches = p[K.recentSearches]?.split(sep)?.filter { it.isNotBlank() } ?: emptyList(),
                notificationPreviews = p[K.notifPreview] ?: false,
                defaultReminderOffsetMinutes = p[K.reminderOffset] ?: 0,
                quickCaptureNotification = p[K.quickCapture] ?: false,
                lock = LockSettings(
                    enabled = p[K.lockEnabled] ?: false,
                    timeout = Duration.ofSeconds((p[K.lockTimeout] ?: 60L).coerceIn(0, 24 * 3600)),
                    secureWindow = p[K.secureWindow] ?: true,
                ),
                ai = AiSettings(
                    enabled = p[K.aiEnabled] ?: false,
                    // On-device or the user's own OpenAI key (hidden setup). Any other stored provider reads back as NONE.
                    provider = enumOr(p[K.aiProvider], ProviderType.NONE).let { if (it == ProviderType.LOCAL || it == ProviderType.OPENAI_COMPATIBLE) it else ProviderType.NONE },
                    cloudConsent = p[K.aiConsent] ?: false,
                    allowKnowledgeBase = p[K.aiKb] ?: false,
                    includeArchived = p[K.aiArchived] ?: false,
                    maxContextChars = p[K.aiMaxChars] ?: 12_000,
                ),
                aiBaseUrl = p[K.aiBaseUrl] ?: "",
                aiModel = p[K.aiModel] ?: "",
                aiKeyStored = p[K.aiKeyStored] ?: false,
                clipboardCaptureSuggestion = p[K.clipboard] ?: false,
                lastBackupAt = p[K.lastBackup]?.let(java.time.Instant::ofEpochMilli),
                backupReminderDays = p[K.backupDays] ?: d.backupReminderDays,
                backupReminderSnoozedUntil = p[K.backupSnooze]?.let(java.time.Instant::ofEpochMilli),
                aiCloudRequests = p[K.aiCloudCount] ?: 0,
                aiCloudLastAt = p[K.aiCloudLast]?.let(java.time.Instant::ofEpochMilli),
            ),
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: AppSettings) {
        p[K.onboarding] = s.onboardingCompleted
        p[K.theme] = s.theme.name
        p[K.dynamicColor] = s.dynamicColor
        p[K.glossy] = s.glossyStyle
        p[K.reducedMotion] = s.reducedMotion
        p[K.textScale] = s.editorTextScale
        p[K.mono] = s.editorMonospace
        p[K.marks] = s.showFormattingMarks
        p[K.openInPreview] = s.openInPreview
        p[K.sortField] = s.defaultSort.field.name
        p[K.sortDesc] = s.defaultSort.descending
        val folder = s.defaultFolderId
        if (folder != null) p[K.defaultFolder] = folder else p.remove(K.defaultFolder)
        val template = s.defaultTemplateId
        if (template != null) p[K.defaultTemplate] = template else p.remove(K.defaultTemplate)
        p[K.trashDays] = s.trashRetentionDays
        p[K.revEnabled] = s.revisions.enabled
        p[K.revMax] = s.revisions.maxRevisionsPerNote
        p[K.revDays] = s.revisions.maxAge.toDays().toInt()
        p[K.revInterval] = s.revisions.snapshotInterval.toMinutes().toInt()
        p[K.homeOrder] = s.homeSections.joinToString(",") { it.name }
        p[K.homeHidden] = s.hiddenHomeSections.joinToString(",") { it.name }
        p[K.searchHistory] = s.searchHistoryEnabled
        p[K.recentSearches] = if (s.searchHistoryEnabled) s.recentSearches.joinToString(sep.toString()) else ""
        p[K.notifPreview] = s.notificationPreviews
        p[K.reminderOffset] = s.defaultReminderOffsetMinutes
        p[K.quickCapture] = s.quickCaptureNotification
        p[K.lockEnabled] = s.lock.enabled
        p[K.lockTimeout] = s.lock.timeout.seconds
        p[K.secureWindow] = s.lock.secureWindow
        p[K.aiEnabled] = s.ai.enabled
        p[K.aiProvider] = s.ai.provider.name
        p[K.aiConsent] = s.ai.cloudConsent
        p[K.aiKb] = s.ai.allowKnowledgeBase
        p[K.aiArchived] = s.ai.includeArchived
        p[K.aiMaxChars] = s.ai.maxContextChars
        p[K.aiBaseUrl] = s.aiBaseUrl
        p[K.aiModel] = s.aiModel
        p[K.aiKeyStored] = s.aiKeyStored
        p[K.clipboard] = s.clipboardCaptureSuggestion
        s.lastBackupAt?.let { p[K.lastBackup] = it.toEpochMilli() } ?: p.remove(K.lastBackup)
        p[K.backupDays] = s.backupReminderDays
        s.backupReminderSnoozedUntil?.let { p[K.backupSnooze] = it.toEpochMilli() } ?: p.remove(K.backupSnooze)
        p[K.aiCloudCount] = s.aiCloudRequests
        s.aiCloudLastAt?.let { p[K.aiCloudLast] = it.toEpochMilli() } ?: p.remove(K.aiCloudLast)
    }

    override val settings: Flow<AppSettings> = store.data.map { read(it) }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p -> write(p, AppSettings.sanitize(transform(read(p)))) }
    }
}
