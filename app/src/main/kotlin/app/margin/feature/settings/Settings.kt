@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.BuildConfig
import app.margin.R
import app.margin.domain.ai.Endpoints
import app.margin.domain.ai.ProviderType
import app.margin.domain.io.DuplicatePolicy
import app.margin.domain.organization.SortField
import app.margin.domain.organization.SortOrder
import app.margin.domain.repository.RestoreMode
import app.margin.domain.settings.AppSettings
import app.margin.domain.settings.HomeSection
import app.margin.domain.settings.SecretStore
import app.margin.domain.settings.SettingsRepository
import app.margin.domain.settings.ThemeMode
import app.margin.feature.ai.SECRET_AI_KEY
import app.margin.feature.backup.BackupViewModel
import app.margin.feature.backup.DataOp
import app.margin.feature.backup.ExportKind
import app.margin.feature.lock.AppLockManager
import app.margin.feature.lock.showAuthPrompt
import app.margin.ui.components.ConfirmDialog
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val secrets: SecretStore,
    val lock: AppLockManager,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    fun update(t: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(t) }

    fun saveApiKey(key: String) = viewModelScope.launch {
        if (key.isBlank()) { secrets.remove(SECRET_AI_KEY); repo.update { it.copy(aiKeyStored = false) } }
        else { secrets.put(SECRET_AI_KEY, key.trim()); repo.update { it.copy(aiKeyStored = true) } }
    }

    /** Revoking cloud consent also forgets the key, so nothing can be sent until set up again. */
    fun revokeCloud() = viewModelScope.launch {
        secrets.remove(SECRET_AI_KEY)
        repo.update { it.copy(ai = it.ai.copy(cloudConsent = false, allowKnowledgeBase = false), aiKeyStored = false) }
    }
}

@Composable
private fun SettingsScaffold(title: String, n: Navigator, content: @Composable () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
    }) { p -> Column(Modifier.fillMaxSize().padding(p).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) { content() } }
}

@Composable
private fun Header(text: String) = Text(text, Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = icon?.let { { Icon(it, null) } }, trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
        modifier = Modifier.clickable(onClick = onClick))
}

@Composable
fun SettingsScreen(n: Navigator, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.settings), n) {
        LinkRow(stringResource(R.string.privacy_security), stringResource(R.string.privacy_security_sub), Icons.Outlined.Lock) { n.open(Routes.SETTINGS_PRIVACY) }
        LinkRow(stringResource(R.string.ai_settings), stringResource(if (s.ai.enabled) R.string.ai_on else R.string.ai_off), Icons.Outlined.AutoAwesome) { n.open(Routes.SETTINGS_AI) }
        LinkRow(stringResource(R.string.data_settings), stringResource(R.string.data_settings_sub), Icons.Outlined.Storage) { n.open(Routes.SETTINGS_DATA) }

        Header(stringResource(R.string.appearance))
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark).forEach { (m, l) ->
                FilterChip(s.theme == m, { vm.update { it.copy(theme = m) } }, label = { Text(stringResource(l)) })
            }
        }
        SwitchRow(stringResource(R.string.dynamic_color), s.dynamicColor, stringResource(R.string.dynamic_color_sub)) { v -> vm.update { it.copy(dynamicColor = v) } }
        ListItem(headlineContent = { Text(stringResource(R.string.editor_text_size, (s.editorTextScale * 100).toInt())) }, supportingContent = {
            Slider(s.editorTextScale, { v -> vm.update { it.copy(editorTextScale = (Math.round(v * 20) / 20f)) } }, valueRange = 0.8f..2.0f, steps = 23)
        })
        SwitchRow(stringResource(R.string.monospace_editor), s.editorMonospace) { v -> vm.update { it.copy(editorMonospace = v) } }
        SwitchRow(stringResource(R.string.open_in_preview), s.openInPreview, stringResource(R.string.open_in_preview_sub)) { v -> vm.update { it.copy(openInPreview = v) } }
        SwitchRow(stringResource(R.string.reduced_motion), s.reducedMotion) { v -> vm.update { it.copy(reducedMotion = v) } }

        Header(stringResource(R.string.home_screen))
        s.homeSections.forEachIndexed { i, sec ->
            ListItem(
                headlineContent = { Text(stringResource(homeSectionLabel(sec))) },
                leadingContent = { Switch(sec !in s.hiddenHomeSections, { on -> vm.update { it.copy(hiddenHomeSections = if (on) it.hiddenHomeSections - sec else it.hiddenHomeSections + sec) } }) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { vm.update { it.copy(homeSections = it.homeSections.toMutableList().also { l -> java.util.Collections.swap(l, i, i - 1) }) } }, enabled = i > 0) { Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.move_up)) }
                        IconButton(onClick = { vm.update { it.copy(homeSections = it.homeSections.toMutableList().also { l -> java.util.Collections.swap(l, i, i + 1) }) } }, enabled = i < s.homeSections.lastIndex) { Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.move_down)) }
                    }
                },
            )
        }

        Header(stringResource(R.string.notes_and_organization))
        Text(stringResource(R.string.default_sort), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(SortOrder(SortField.UPDATED, true) to R.string.sort_updated, SortOrder(SortField.CREATED, true) to R.string.sort_created_newest, SortOrder(SortField.TITLE, false) to R.string.sort_title).forEach { (o, l) ->
                FilterChip(s.defaultSort == o, { vm.update { it.copy(defaultSort = o) } }, label = { Text(stringResource(l)) })
            }
        }
        Text(stringResource(R.string.trash_retention), Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 30, 90, 0).forEach { d -> FilterChip(s.trashRetentionDays == d, { vm.update { it.copy(trashRetentionDays = d) } }, label = { Text(if (d == 0) stringResource(R.string.keep_forever) else stringResource(R.string.n_days, d)) }) }
        }
        SwitchRow(stringResource(R.string.revision_history), s.revisions.enabled, stringResource(R.string.revision_history_sub)) { v -> vm.update { it.copy(revisions = it.revisions.copy(enabled = v)) } }
        if (s.revisions.enabled) {
            Text(stringResource(R.string.keep_revisions), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(20, 50, 200).forEach { c -> FilterChip(s.revisions.maxRevisionsPerNote == c, { vm.update { it.copy(revisions = it.revisions.copy(maxRevisionsPerNote = c)) } }, label = { Text(stringResource(R.string.n_per_note, c)) }) }
                listOf(30L, 90L, 365L).forEach { d -> FilterChip(s.revisions.maxAge.toDays() == d, { vm.update { it.copy(revisions = it.revisions.copy(maxAge = Duration.ofDays(d))) } }, label = { Text(stringResource(R.string.n_days, d.toInt())) }) }
            }
        }

        Header(stringResource(R.string.search))
        SwitchRow(stringResource(R.string.search_history), s.searchHistoryEnabled, stringResource(R.string.search_history_sub)) { v -> vm.update { it.copy(searchHistoryEnabled = v, recentSearches = if (v) it.recentSearches else emptyList()) } }

        Header(stringResource(R.string.nav_tasks))
        Text(stringResource(R.string.default_reminder), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to R.string.reminder_at_time, 15 to R.string.reminder_15, 60 to R.string.reminder_60).forEach { (o, l) ->
                FilterChip(s.defaultReminderOffsetMinutes == o, { vm.update { it.copy(defaultReminderOffsetMinutes = o) } }, label = { Text(stringResource(l)) })
            }
        }

        Header(stringResource(R.string.about))
        ListItem(headlineContent = { Text(stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME) }, supportingContent = { Text(stringResource(R.string.about_body)) })
    }
}

private fun homeSectionLabel(s: HomeSection) = when (s) {
    HomeSection.QUICK_CAPTURE -> R.string.quick_capture
    HomeSection.PINNED -> R.string.pinned
    HomeSection.RECENT -> R.string.recent
    HomeSection.FAVORITES -> R.string.favorites
    HomeSection.TASKS -> R.string.upcoming_tasks
    HomeSection.FOLDERS -> R.string.folders
    HomeSection.TEMPLATES -> R.string.recent_templates
    HomeSection.ATTACHMENTS -> R.string.attachments
}

@Composable
fun PrivacySettingsScreen(n: Navigator, vm: SettingsViewModel = hiltViewModel(), data: BackupViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var wipeDialog by rememberSaveable { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    SettingsScaffold(stringResource(R.string.privacy_security), n) {
        Card(Modifier.padding(16.dp)) { Text(stringResource(R.string.privacy_statement), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
        Header(stringResource(R.string.app_lock))
        val canAuth = activity?.let { AppLockManager.canAuthenticate(it) } ?: false
        SwitchRow(stringResource(R.string.app_lock), s.lock.enabled, stringResource(if (canAuth) R.string.app_lock_sub else R.string.app_lock_unavailable), enabled = canAuth || s.lock.enabled) { on ->
            val act = activity ?: return@SwitchRow
            // Turning the lock on or off requires authenticating first.
            showAuthPrompt(act, onSuccess = {
                vm.lock.markUnlockedNow()
                vm.update { it.copy(lock = it.lock.copy(enabled = on)) }
            }, onError = { authError = it })
        }
        authError?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
        if (s.lock.enabled) {
            Text(stringResource(R.string.lock_after), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0L to R.string.lock_immediately, 60L to R.string.lock_1m, 300L to R.string.lock_5m, 1800L to R.string.lock_30m).forEach { (sec, l) ->
                    FilterChip(s.lock.timeout.seconds == sec, { vm.update { it.copy(lock = it.lock.copy(timeout = Duration.ofSeconds(sec))) } }, label = { Text(stringResource(l)) })
                }
            }
            SwitchRow(stringResource(R.string.secure_window), s.lock.secureWindow, stringResource(R.string.secure_window_sub)) { v -> vm.update { it.copy(lock = it.lock.copy(secureWindow = v)) } }
        }
        Header(stringResource(R.string.notifications))
        SwitchRow(stringResource(R.string.notification_previews), s.notificationPreviews, stringResource(R.string.notification_previews_sub)) { v -> vm.update { it.copy(notificationPreviews = v) } }
        Header(stringResource(R.string.ai_settings))
        ListItem(headlineContent = { Text(stringResource(if (s.ai.cloudConsent) R.string.ai_cloud_consent_given else R.string.ai_cloud_consent_not_given)) },
            trailingContent = { if (s.ai.cloudConsent) TextButton(onClick = vm::revokeCloud) { Text(stringResource(R.string.revoke)) } })
        Header(stringResource(R.string.data_collection))
        Text(stringResource(R.string.no_telemetry), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        Header(stringResource(R.string.danger_zone))
        OutlinedButton(onClick = { wipeDialog = true }, Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.delete_all_data), color = MaterialTheme.colorScheme.error) }
    }
    if (wipeDialog) {
        var typed by rememberSaveable { mutableStateOf("") }
        val word = stringResource(R.string.delete_confirm_word)
        AlertDialog(onDismissRequest = { wipeDialog = false }, title = { Text(stringResource(R.string.delete_all_data)) },
            text = { Column { Text(stringResource(R.string.delete_all_data_body, word)); OutlinedTextField(typed, { typed = it }, singleLine = true) } },
            confirmButton = { TextButton(enabled = typed.trim().equals(word, ignoreCase = true), onClick = { data.wipe(); wipeDialog = false }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { wipeDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    OperationDialog(data)
}

@Composable
fun AiSettingsScreen(n: Navigator, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var baseUrl by rememberSaveable(s.aiBaseUrl) { mutableStateOf(s.aiBaseUrl) }
    var model by rememberSaveable(s.aiModel) { mutableStateOf(s.aiModel) }
    var key by rememberSaveable { mutableStateOf("") }
    var consentDialog by rememberSaveable { mutableStateOf(false) }
    val cloud = s.ai.provider == ProviderType.OPENAI_COMPATIBLE || s.ai.provider == ProviderType.ANTHROPIC
    SettingsScaffold(stringResource(R.string.ai_settings), n) {
        Card(Modifier.padding(16.dp)) { Text(stringResource(R.string.ai_intro), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
        SwitchRow(stringResource(R.string.ai_enable), s.ai.enabled, stringResource(R.string.ai_enable_sub)) { v -> vm.update { it.copy(ai = it.ai.copy(enabled = v)) } }
        Header(stringResource(R.string.ai_provider))
        listOf(
            ProviderType.NONE to (R.string.ai_provider_none to R.string.ai_provider_none_sub),
            ProviderType.LOCAL to (R.string.ai_provider_local to R.string.ai_provider_local_sub),
            ProviderType.OPENAI_COMPATIBLE to (R.string.ai_provider_openai to R.string.ai_provider_openai_sub),
            ProviderType.ANTHROPIC to (R.string.ai_provider_anthropic to R.string.ai_provider_anthropic_sub),
        ).forEach { (p, labels) ->
            ListItem(headlineContent = { Text(stringResource(labels.first)) }, supportingContent = { Text(stringResource(labels.second)) },
                leadingContent = { RadioButton(s.ai.provider == p, { vm.update { it.copy(ai = it.ai.copy(provider = p, cloudConsent = if (p == it.ai.provider) it.ai.cloudConsent else false)) } }) },
                modifier = Modifier.clickable { vm.update { it.copy(ai = it.ai.copy(provider = p, cloudConsent = if (p == it.ai.provider) it.ai.cloudConsent else false)) } })
        }
        if (cloud) {
            Header(stringResource(R.string.ai_connection))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val urlOk = baseUrl.isBlank() && s.ai.provider == ProviderType.ANTHROPIC || Endpoints.validate(baseUrl)
                OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_base_url)) }, singleLine = true, isError = !urlOk,
                    supportingText = { Text(stringResource(if (urlOk) R.string.ai_base_url_help else R.string.ai_base_url_invalid)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_model)) }, singleLine = true,
                    supportingText = { Text(stringResource(R.string.ai_model_help)) })
                OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(if (s.aiKeyStored) R.string.ai_key_replace else R.string.ai_key)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), supportingText = { Text(stringResource(R.string.ai_key_help)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = urlOk && model.isNotBlank(), onClick = {
                        vm.update { it.copy(aiBaseUrl = baseUrl.trim(), aiModel = model.trim()) }
                        if (key.isNotBlank()) { vm.saveApiKey(key); key = "" }
                    }) { Text(stringResource(R.string.save)) }
                    if (s.aiKeyStored) OutlinedButton(onClick = { vm.saveApiKey("") }) { Text(stringResource(R.string.ai_forget_key)) }
                }
            }
            Header(stringResource(R.string.ai_consent))
            SwitchRow(stringResource(R.string.ai_cloud_consent), s.ai.cloudConsent, stringResource(R.string.ai_cloud_consent_sub)) { on ->
                if (on) consentDialog = true else vm.revokeCloud()
            }
        }
        if (s.ai.provider != ProviderType.NONE) {
            Header(stringResource(R.string.ai_context))
            SwitchRow(stringResource(R.string.ai_allow_kb), s.ai.allowKnowledgeBase, stringResource(R.string.ai_allow_kb_sub)) { v -> vm.update { it.copy(ai = it.ai.copy(allowKnowledgeBase = v)) } }
            SwitchRow(stringResource(R.string.ai_include_archived), s.ai.includeArchived) { v -> vm.update { it.copy(ai = it.ai.copy(includeArchived = v)) } }
            Text(stringResource(R.string.ai_context_size, s.ai.maxContextChars), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
            Slider(s.ai.maxContextChars.toFloat(), { v -> vm.update { it.copy(ai = it.ai.copy(maxContextChars = (v / 1000).toInt() * 1000)) } }, Modifier.padding(horizontal = 16.dp), valueRange = 2_000f..48_000f)
        }
    }
    if (consentDialog) ConfirmDialog(stringResource(R.string.ai_consent_title), stringResource(R.string.ai_consent_body, baseUrl.ifBlank { "api.anthropic.com" }),
        stringResource(R.string.ai_consent_agree), { vm.update { it.copy(ai = it.ai.copy(cloudConsent = true)) } }, { consentDialog = false })
}

@Composable
fun DataSettingsScreen(n: Navigator, vm: BackupViewModel = hiltViewModel()) {
    var passDialog by rememberSaveable { mutableStateOf(false) }
    var backupPass by remember { mutableStateOf("") }
    var policy by rememberSaveable { mutableStateOf(DuplicatePolicy.SKIP) }
    val today = LocalDate.now().toString()
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { vm.backup(it, backupPass.toCharArray().takeIf { p -> p.isNotEmpty() }); backupPass = "" }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.inspect(it) } }
    var exportKind by rememberSaveable { mutableStateOf(ExportKind.MARKDOWN_ZIP) }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { vm.export(it, exportKind) } }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.export(it, ExportKind.JSON) } }
    val importFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.import(uris, policy) }

    SettingsScaffold(stringResource(R.string.data_settings), n) {
        Card(Modifier.padding(16.dp)) { Text(stringResource(R.string.backup_explainer), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
        Header(stringResource(R.string.backup))
        LinkRow(stringResource(R.string.create_backup), stringResource(R.string.create_backup_sub)) { passDialog = true }
        LinkRow(stringResource(R.string.restore_backup), stringResource(R.string.restore_backup_sub)) { openBackup.launch(arrayOf("*/*")) }
        Header(stringResource(R.string.export))
        LinkRow(stringResource(R.string.export_markdown), stringResource(R.string.export_markdown_sub)) { exportKind = ExportKind.MARKDOWN_ZIP; exportZip.launch("margin-markdown-$today.zip") }
        LinkRow(stringResource(R.string.export_html), stringResource(R.string.export_html_sub)) { exportKind = ExportKind.HTML_ZIP; exportZip.launch("margin-html-$today.zip") }
        LinkRow(stringResource(R.string.export_text), stringResource(R.string.export_text_sub)) { exportKind = ExportKind.TEXT_ZIP; exportZip.launch("margin-text-$today.zip") }
        LinkRow(stringResource(R.string.export_json), stringResource(R.string.export_json_sub)) { exportJson.launch("margin-library-$today.json") }
        Header(stringResource(R.string.import_header))
        Text(stringResource(R.string.duplicates_policy), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(policy == DuplicatePolicy.SKIP, { policy = DuplicatePolicy.SKIP }, label = { Text(stringResource(R.string.skip_duplicates)) })
            FilterChip(policy == DuplicatePolicy.KEEP_BOTH, { policy = DuplicatePolicy.KEEP_BOTH }, label = { Text(stringResource(R.string.keep_both)) })
        }
        LinkRow(stringResource(R.string.import_files), stringResource(R.string.import_files_sub)) { importFiles.launch(arrayOf("text/*", "application/json", "*/*")) }
        Header(stringResource(R.string.maintenance))
        LinkRow(stringResource(R.string.rebuild_index), stringResource(R.string.rebuild_index_sub)) { vm.rebuildIndex() }
        LinkRow(stringResource(R.string.attachments), stringResource(R.string.storage_and_files)) { n.open(Routes.ATTACHMENTS) }
    }
    if (passDialog) {
        var show by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { passDialog = false }, title = { Text(stringResource(R.string.create_backup)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_passphrase_explainer))
                    OutlinedTextField(show, { show = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.passphrase_optional)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    if (show.isNotEmpty() && show.length < 8) Text(stringResource(R.string.passphrase_short), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = show.isEmpty() || show.length >= 8, onClick = {
                    backupPass = show; passDialog = false
                    createBackup.launch("margin-backup-$today" + if (show.isEmpty()) ".marginbackup" else ".marginbackup.enc")
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = { TextButton(onClick = { passDialog = false }) { Text(stringResource(R.string.cancel)) } })
    }
    OperationDialog(vm)
}

/** Progress / result / restore-confirmation dialogs shared by data screens. */
@Composable
fun OperationDialog(vm: BackupViewModel) {
    val op by vm.op.collectAsStateWithLifecycle()
    when (val o = op) {
        DataOp.Idle -> Unit
        is DataOp.Working -> AlertDialog(onDismissRequest = {}, title = { Text(stringResource(o.label)) },
            text = { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text(stringResource(R.string.please_wait), Modifier.padding(start = 16.dp)) } },
            confirmButton = {})
        is DataOp.Done -> AlertDialog(onDismissRequest = vm::dismiss, title = { Text(stringResource(o.message)) }, text = { o.detail?.let { Text(it) } },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) } })
        is DataOp.Failed -> AlertDialog(onDismissRequest = vm::dismiss, title = { Text(stringResource(o.message)) },
            text = { Text(stringResource(R.string.nothing_changed) + (o.detail?.let { "\n\n$it" } ?: "")) },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) } })
        DataOp.NeedsPassphrase -> {
            var pass by remember { mutableStateOf("") }
            AlertDialog(onDismissRequest = vm::dismiss, title = { Text(stringResource(R.string.encrypted_backup)) },
                text = { OutlinedTextField(pass, { pass = it }, label = { Text(stringResource(R.string.passphrase)) }, singleLine = true, visualTransformation = PasswordVisualTransformation()) },
                confirmButton = { TextButton(enabled = pass.isNotEmpty(), onClick = { vm.retryWithPassphrase(pass.toCharArray()); pass = "" }) { Text(stringResource(R.string.unlock)) } },
                dismissButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.cancel)) } })
        }
        is DataOp.ReadyToRestore -> {
            var mode by rememberSaveable { mutableStateOf(RestoreMode.MERGE) }
            AlertDialog(onDismissRequest = { vm.cancelRestore() }, title = { Text(stringResource(R.string.backup_verified)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(o.counts)
                        o.createdAt?.let { Text(stringResource(R.string.backup_created_at, it)) }
                        if (o.missingAttachments > 0) Text(stringResource(R.string.backup_missing_attachments, o.missingAttachments), color = MaterialTheme.colorScheme.tertiary)
                        HorizontalDivider()
                        Row(Modifier.clickable { mode = RestoreMode.MERGE }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(mode == RestoreMode.MERGE, { mode = RestoreMode.MERGE }); Text(stringResource(R.string.restore_merge))
                        }
                        Row(Modifier.clickable { mode = RestoreMode.REPLACE }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(mode == RestoreMode.REPLACE, { mode = RestoreMode.REPLACE }); Text(stringResource(R.string.restore_replace))
                        }
                        if (mode == RestoreMode.REPLACE) Text(stringResource(R.string.restore_replace_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.applyRestore(mode) }) { Text(stringResource(R.string.restore)) } },
                dismissButton = { TextButton(onClick = { vm.cancelRestore() }) { Text(stringResource(R.string.cancel)) } })
        }
        is DataOp.Imported -> AlertDialog(onDismissRequest = vm::dismiss, title = { Text(stringResource(R.string.import_done)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.import_summary, o.report.imported, o.report.skipped.size, o.report.failed.size))
                    o.report.issues.take(30).forEach { i ->
                        Text("• " + i.sourceName + ": " + when (i) { is app.margin.domain.io.ImportIssue.Skipped -> i.reason.name.lowercase(); is app.margin.domain.io.ImportIssue.Failed -> i.reason.name.lowercase() },
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.ok)) } })
    }
}
