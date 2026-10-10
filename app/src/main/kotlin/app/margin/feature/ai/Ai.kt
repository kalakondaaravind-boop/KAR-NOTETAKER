@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.data.repo.SearchRepositoryImpl
import app.margin.domain.ai.AiAction
import app.margin.domain.ai.AiError
import app.margin.domain.ai.AiProvider
import app.margin.domain.ai.AiRequest
import app.margin.domain.ai.AiResult
import app.margin.domain.ai.AiService
import app.margin.domain.ai.KnowledgeRetrieval
import app.margin.domain.ai.LocalAiProvider
import app.margin.domain.ai.ProviderType
import app.margin.domain.ai.HttpProviderConfig
import app.margin.domain.ai.OpenAiCompatibleProvider
import app.margin.domain.ai.OpenAiDefaults
import app.margin.domain.ai.ResponseParser
import app.margin.domain.ai.SourceNote
import app.margin.domain.model.Clock
import app.margin.domain.repository.FolderRepository
import app.margin.domain.repository.NoteRepository
import app.margin.domain.settings.AppSettings
import app.margin.domain.settings.SecretStore
import app.margin.domain.settings.SettingsRepository
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import app.margin.ui.theme.LocalSemanticColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

const val SECRET_AI_KEY = "ai_api_key"

/** Builds the configured provider and runs requests through the domain AiService (policy → prompt → provider → validation). */
@Singleton
class AiCoordinator @Inject constructor(
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val search: SearchRepositoryImpl,
    private val notes: NoteRepository,
    private val clock: Clock,
) {
    /**
     * On-device assistant, or OpenAI with the key entered through the hidden setup (stored encrypted by
     * [SecretStore], read only at the moment a request is made). Anthropic is not offered in this build.
     */
    suspend fun provider(s: AppSettings): AiProvider? = when (s.ai.provider) {
        ProviderType.LOCAL -> LocalAiProvider()
        ProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(
            HttpProviderConfig(s.aiBaseUrl.ifBlank { OpenAiDefaults.BASE_URL }, secrets.get(SECRET_AI_KEY).orEmpty(), s.aiModel.ifBlank { OpenAiDefaults.MODEL }),
        )
        ProviderType.NONE, ProviderType.ANTHROPIC -> null
    }

    suspend fun run(request: AiRequest, primaryNoteId: String?): AiResult {
        val s = settings.current()
        val p = provider(s)
        val excluded = primaryNoteId?.let { notes.getNote(it)?.excludedFromAi } ?: false
        return AiService({ s.ai }, { p }).run(request, excluded)
    }

    /** "Ask my notes": retrieves authorised notes only (never trashed or excluded), then asks with citations. */
    suspend fun ask(question: String): AiResult {
        val s = settings.current()
        val docs = search.aiDocuments(s.ai)
        val sources = KnowledgeRetrieval.retrieve(question, docs.asSequence(), clock.now())
        if (sources.isEmpty()) return AiResult.Success("", "", emptyList(), emptyList(), insufficientEvidence = true, local = true)
        return run(AiRequest(AiAction.ASK_KNOWLEDGE_BASE, "", sources, question = question), null)
    }

    suspend fun isCloud(): Boolean = settings.current().ai.provider == ProviderType.OPENAI_COMPATIBLE
}

data class AiUi(val running: Boolean = false, val action: AiAction? = null, val result: AiResult? = null)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val ai: AiCoordinator,
    settingsRepo: SettingsRepository,
    private val notes: NoteRepository,
    private val folders: FolderRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val ui = MutableStateFlow(AiUi())
    private var job: Job? = null

    fun run(action: AiAction, input: String, noteId: String?, tone: String? = null, language: String? = null) {
        job?.cancel()
        job = viewModelScope.launch {
            ui.value = AiUi(running = true, action = action)
            val tags = notes.observeTagsForNote(noteId ?: "").first().map { it.name }
            val req = AiRequest(action, input, tone = tone, targetLanguage = language, existingTags = tags,
                existingFolders = folders.all().map { it.name })
            ui.value = AiUi(running = false, action = action, result = ai.run(req, noteId))
        }
    }

    fun ask(question: String) {
        job?.cancel()
        job = viewModelScope.launch {
            ui.value = AiUi(running = true, action = AiAction.ASK_KNOWLEDGE_BASE)
            ui.value = AiUi(running = false, action = AiAction.ASK_KNOWLEDGE_BASE, result = ai.ask(question))
        }
    }

    fun cancel() { job?.cancel(); ui.value = ui.value.copy(running = false, result = AiResult.Failure(AiError.CANCELLED)) }
    fun clear() { ui.value = AiUi() }
}

@Composable
fun aiErrorText(e: AiError): String = stringResource(when (e) {
    AiError.DISABLED -> R.string.ai_err_disabled
    AiError.NOT_CONFIGURED -> R.string.ai_err_not_configured
    AiError.CONSENT_REQUIRED -> R.string.ai_err_consent
    AiError.UNSUPPORTED -> R.string.ai_err_unsupported
    AiError.NETWORK -> R.string.ai_err_network
    AiError.TIMEOUT -> R.string.ai_err_timeout
    AiError.AUTH -> R.string.ai_err_auth
    AiError.RATE_LIMITED -> R.string.ai_err_rate
    AiError.SERVER -> R.string.ai_err_server
    AiError.BAD_RESPONSE -> R.string.ai_err_bad_response
    AiError.CANCELLED -> R.string.ai_err_cancelled
    AiError.NOTE_EXCLUDED -> R.string.ai_err_excluded
    AiError.INPUT_TOO_LARGE -> R.string.ai_err_too_large
})

private val sheetActions = listOf(
    AiAction.SUMMARIZE_SHORT to R.string.ai_summarize, AiAction.SUMMARIZE_DETAILED to R.string.ai_summarize_detailed,
    AiAction.EXTRACT_ACTIONS to R.string.ai_actions, AiAction.EXTRACT_DECISIONS to R.string.ai_decisions, AiAction.EXTRACT_DATES to R.string.ai_dates,
    AiAction.GENERATE_CHECKLIST to R.string.ai_checklist, AiAction.MAKE_OUTLINE to R.string.ai_outline, AiAction.SUGGEST_TITLE to R.string.ai_title,
    AiAction.SUGGEST_TAGS to R.string.ai_tags, AiAction.STUDY_QUESTIONS to R.string.ai_questions, AiAction.FLASHCARDS to R.string.ai_flashcards,
    AiAction.FIX_GRAMMAR to R.string.ai_grammar, AiAction.IMPROVE_CLARITY to R.string.ai_clarity, AiAction.REWRITE to R.string.ai_rewrite,
    AiAction.CHANGE_TONE to R.string.ai_tone, AiAction.TRANSLATE to R.string.ai_translate, AiAction.EXPLAIN to R.string.ai_explain,
    AiAction.EXPAND_OUTLINE to R.string.ai_expand, AiAction.MIND_MAP to R.string.ai_mindmap, AiAction.FIND_CONTRADICTIONS to R.string.ai_contradictions,
    AiAction.UNANSWERED_QUESTIONS to R.string.ai_open_questions, AiAction.REVIEW_PROMPTS to R.string.ai_review_prompts,
    AiAction.MEETING_AGENDA to R.string.ai_agenda, AiAction.SUMMARIZE_TRANSCRIPT to R.string.ai_transcript, AiAction.ACTION_PLAN to R.string.ai_action_plan,
)

/**
 * Explicit AI actions for the current note/selection. Results are previewed and labelled as
 * generated; the user chooses Insert / Replace selection / Copy / Discard. Nothing is applied silently.
 */
@Composable
fun AiActionSheet(
    noteId: String,
    input: String,
    hasSelection: Boolean,
    onInsert: (String) -> Unit,
    onReplaceSelection: (String) -> Unit,
    onSetTitle: (String) -> Unit,
    onAddTags: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    vm: AiViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var tone by rememberSaveable { mutableStateOf("friendly") }
    var language by rememberSaveable { mutableStateOf("English") }
    val cloud = settings.ai.provider == ProviderType.OPENAI_COMPATIBLE || settings.ai.provider == ProviderType.ANTHROPIC
    val gen = LocalSemanticColors.current.aiGenerated
    ModalBottomSheet(onDismissRequest = { vm.clear(); onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = gen)
                Text(stringResource(R.string.ai_assist), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                AssistChip(onClick = {}, label = { Text(stringResource(if (cloud) R.string.ai_cloud_badge else R.string.ai_local_badge)) },
                    leadingIcon = { Icon(if (cloud) Icons.Outlined.CloudQueue else Icons.Outlined.PhoneAndroid, null) })
            }
            Text(stringResource(if (hasSelection) R.string.ai_scope_selection else R.string.ai_scope_note), style = MaterialTheme.typography.bodySmall)
            if (cloud && settings.ai.cloudConsent) Text(stringResource(R.string.ai_cloud_disclosure, settings.aiModel.ifBlank { "?" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            val res = ui.result
            when {
                ui.running -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text(stringResource(R.string.ai_working), Modifier.weight(1f))
                    TextButton(onClick = vm::cancel) { Text(stringResource(R.string.cancel)) }
                }
                res is AiResult.Failure -> Column {
                    Text(aiErrorText(res.error), color = MaterialTheme.colorScheme.error)
                    Row {
                        TextButton(onClick = { ui.action?.let { vm.run(it, input, noteId, tone, language) } }) { Text(stringResource(R.string.retry)) }
                        TextButton(onClick = vm::clear) { Text(stringResource(R.string.back)) }
                    }
                }
                res is AiResult.Success -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.ai_generated_label, res.provider), style = MaterialTheme.typography.labelSmall, color = gen)
                        SelectionContainer { Text(res.text, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (ui.action) {
                                AiAction.SUGGEST_TITLE -> Button(onClick = { onSetTitle(ResponseParser.title(res.text)); vm.clear(); onDismiss() }) { Text(stringResource(R.string.ai_use_title)) }
                                AiAction.SUGGEST_TAGS -> Button(onClick = { onAddTags(ResponseParser.tags(res.text)); vm.clear(); onDismiss() }) { Text(stringResource(R.string.ai_add_tags)) }
                                else -> {
                                    Button(onClick = { onInsert(res.text); vm.clear(); onDismiss() }) { Text(stringResource(R.string.insert)) }
                                    if (hasSelection) OutlinedButton(onClick = { onReplaceSelection(res.text); vm.clear(); onDismiss() }) { Text(stringResource(R.string.ai_replace_selection)) }
                                }
                            }
                            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(res.text)) }) { Text(stringResource(R.string.copy)) }
                            TextButton(onClick = vm::clear) { Text(stringResource(R.string.discard)) }
                        }
                    }
                }
                else -> {
                    if (!settings.ai.enabled || settings.ai.provider == ProviderType.NONE) {
                        Text(stringResource(R.string.ai_err_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        sheetActions.forEach { (a, label) ->
                            AssistChip(onClick = { vm.run(a, input, noteId, tone, language) }, label = { Text(stringResource(label)) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(tone, { tone = it.take(40) }, Modifier.weight(1f), label = { Text(stringResource(R.string.ai_tone_label)) }, singleLine = true)
                        OutlinedTextField(language, { language = it.take(40) }, Modifier.weight(1f), label = { Text(stringResource(R.string.ai_language_label)) }, singleLine = true)
                    }
                }
            }
        }
    }
}

@Composable
fun AskNotesScreen(n: Navigator, vm: AiViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var question by rememberSaveable { mutableStateOf("") }
    val gen = LocalSemanticColors.current.aiGenerated
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.ask_notes)) },
            navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ask_explainer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!settings.ai.enabled || settings.ai.provider == ProviderType.NONE || !settings.ai.allowKnowledgeBase) {
                Card { Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.ask_requires_setup))
                    TextButton(onClick = { n.open(Routes.SETTINGS_AI) }) { Text(stringResource(R.string.open_ai_settings)) }
                } }
            }
            OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ask_question)) },
                trailingIcon = { IconButton(onClick = { vm.ask(question) }, enabled = question.isNotBlank() && !ui.running) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.ask)) } })
            val res = ui.result
            when {
                ui.running -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); Text(stringResource(R.string.ai_working), Modifier.padding(start = 12.dp)) }
                res is AiResult.Failure -> Text(aiErrorText(res.error), color = MaterialTheme.colorScheme.error)
                res is AiResult.Success -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (res.insufficientEvidence) {
                        Text(stringResource(R.string.ask_insufficient), style = MaterialTheme.typography.titleSmall)
                        if (res.text.isNotBlank()) Text(res.text, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.ai_generated_label, res.provider), style = MaterialTheme.typography.labelSmall, color = gen)
                        SelectionContainer { Text(res.text, style = MaterialTheme.typography.bodyLarge) }
                    }
                    if (res.sources.isNotEmpty()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.ask_sources), style = MaterialTheme.typography.titleSmall)
                        res.sources.forEachIndexed { i, s: SourceNote ->
                            val cited = s.id in res.citedSourceIds
                            Text("[${i + 1}] " + s.title.ifBlank { stringResource(R.string.untitled) } + if (cited) "  ✓ " + stringResource(R.string.cited) else "",
                                Modifier.fillMaxWidth().clickable { n.open(Routes.editor(s.id)) }.padding(vertical = 6.dp),
                                color = if (cited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
