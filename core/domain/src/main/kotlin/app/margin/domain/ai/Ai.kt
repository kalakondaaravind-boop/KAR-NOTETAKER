package app.margin.domain.ai

/**
 * Provider-neutral AI layer (docs/AI_GUIDE.md). Separation of concerns:
 *  - [AiAction]        what the user explicitly asked for
 *  - [AiPolicy]        privacy/consent gate (runs before anything leaves the device)
 *  - [PromptBuilder]   system policy + untrusted note content kept in separate, delimited parts
 *  - [AiProvider]      transport (local heuristics, OpenAI-compatible, Anthropic)
 *  - [ResponseParser]  validation of what comes back (citations, JSON)
 * Core note features never depend on any of this.
 */
enum class AiAction(val needsKnowledgeBase: Boolean = false, val output: Output = Output.TEXT) {
    SUMMARIZE_SHORT, SUMMARIZE_DETAILED,
    EXTRACT_ACTIONS(output = Output.CHECKLIST), EXTRACT_DECISIONS(output = Output.LIST), EXTRACT_DATES(output = Output.LIST),
    EXTRACT_ENTITIES(output = Output.LIST),
    GENERATE_CHECKLIST(output = Output.CHECKLIST),
    REWRITE, IMPROVE_CLARITY, CHANGE_TONE, FIX_GRAMMAR, TRANSLATE,
    EXPAND_OUTLINE, MAKE_OUTLINE, EXPLAIN,
    STUDY_QUESTIONS(output = Output.LIST), FLASHCARDS(output = Output.LIST), MIND_MAP(output = Output.LIST),
    SUGGEST_TITLE(output = Output.TITLE), SUGGEST_TAGS(output = Output.TAGS), SUGGEST_FOLDER(output = Output.FOLDER),
    FIND_RELATED(needsKnowledgeBase = true, output = Output.NOTES), FIND_DUPLICATES(needsKnowledgeBase = true, output = Output.NOTES),
    ASK_SELECTED, ASK_KNOWLEDGE_BASE(needsKnowledgeBase = true),
    DRAFT_FROM_NOTES, TEMPLATE_FROM_DESCRIPTION(output = Output.TEMPLATE_JSON), FILL_TEMPLATE(output = Output.FORM_JSON),
    FIND_CONTRADICTIONS(output = Output.LIST), UNANSWERED_QUESTIONS(output = Output.LIST), REVIEW_PROMPTS(output = Output.LIST),
    MEETING_AGENDA, SUMMARIZE_TRANSCRIPT, ACTION_PLAN;

    enum class Output { TEXT, LIST, CHECKLIST, TITLE, TAGS, FOLDER, NOTES, TEMPLATE_JSON, FORM_JSON }
}

data class SourceNote(val id: String, val title: String, val text: String)

data class AiRequest(
    val action: AiAction,
    /** The selected text or note body the action applies to. */
    val input: String,
    /** Notes supplying context (selected notes or retrieved sources). Order defines citation numbers. */
    val sources: List<SourceNote> = emptyList(),
    val question: String? = null,
    val tone: String? = null,
    val targetLanguage: String? = null,
    val existingTags: List<String> = emptyList(),
    val existingFolders: List<String> = emptyList(),
)

data class Prompt(val system: String, val user: String, val maxOutputTokens: Int = 1024)

sealed interface AiResult {
    /** Text produced by a model or local heuristic. Always presented as generated, never auto-applied. */
    data class Success(
        val text: String,
        val provider: String,
        val sources: List<SourceNote> = emptyList(),
        val citedSourceIds: List<String> = emptyList(),
        val insufficientEvidence: Boolean = false,
        val local: Boolean,
    ) : AiResult

    data class Failure(val error: AiError, val detail: String? = null) : AiResult
}

enum class AiError { DISABLED, NOT_CONFIGURED, CONSENT_REQUIRED, UNSUPPORTED, NETWORK, TIMEOUT, AUTH, RATE_LIMITED, SERVER, BAD_RESPONSE, CANCELLED, NOTE_EXCLUDED, INPUT_TOO_LARGE }

interface AiProvider {
    val id: String
    val displayName: String
    /** True if content leaves the device. Drives consent and the disclosure shown before running. */
    val sendsDataOffDevice: Boolean
    fun supports(action: AiAction): Boolean
    suspend fun run(request: AiRequest, prompt: Prompt): AiResult
}

enum class ProviderType { NONE, LOCAL, OPENAI_COMPATIBLE, ANTHROPIC }

data class AiSettings(
    val enabled: Boolean = false,
    val provider: ProviderType = ProviderType.NONE,
    /** Explicit, revocable consent to send selected content to the configured cloud provider. */
    val cloudConsent: Boolean = false,
    /** Allow questions across the whole (authorised) library, not just selected notes. */
    val allowKnowledgeBase: Boolean = false,
    val includeArchived: Boolean = false,
    val maxContextChars: Int = 12_000,
)

object AiPolicy {
    sealed interface Decision {
        data object Allowed : Decision
        data class Denied(val error: AiError) : Decision
    }

    /**
     * Every AI call passes through here. Excluded notes (per-note opt-out, sensitive templates)
     * are filtered out by the caller using [eligible]; this function refuses if the primary note
     * itself is excluded.
     */
    fun check(action: AiAction, settings: AiSettings, provider: AiProvider?, primaryNoteExcluded: Boolean): Decision = when {
        !settings.enabled || settings.provider == ProviderType.NONE -> Decision.Denied(AiError.DISABLED)
        provider == null -> Decision.Denied(AiError.NOT_CONFIGURED)
        primaryNoteExcluded -> Decision.Denied(AiError.NOTE_EXCLUDED)
        provider.sendsDataOffDevice && !settings.cloudConsent -> Decision.Denied(AiError.CONSENT_REQUIRED)
        action.needsKnowledgeBase && !settings.allowKnowledgeBase -> Decision.Denied(AiError.CONSENT_REQUIRED)
        !provider.supports(action) -> Decision.Denied(AiError.UNSUPPORTED)
        else -> Decision.Allowed
    }

    data class NoteVisibility(val id: String, val trashed: Boolean, val archived: Boolean, val excludedFromAi: Boolean)

    /** Search rules for AI context: never trashed or excluded notes; archived only if allowed. */
    fun eligible(n: NoteVisibility, settings: AiSettings): Boolean =
        !n.trashed && !n.excludedFromAi && (settings.includeArchived || !n.archived)
}
