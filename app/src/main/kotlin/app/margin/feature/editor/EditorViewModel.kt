package app.margin.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.margin.di.AppScope
import app.margin.domain.editor.AutosaveController
import app.margin.domain.editor.Draft
import app.margin.domain.editor.EditorCommands
import app.margin.domain.editor.InlineStyle
import app.margin.domain.editor.LineStyle
import app.margin.domain.editor.SaveStatus
import app.margin.domain.editor.TextValue
import app.margin.domain.editor.UndoStack
import app.margin.domain.model.Attachment
import app.margin.domain.model.Clock
import app.margin.domain.model.Folder
import app.margin.domain.model.Note
import app.margin.domain.model.NoteMeta
import app.margin.domain.model.PageStyle
import app.margin.domain.text.NoteFont
import app.margin.domain.model.NoteState
import app.margin.domain.model.NoteSummary
import app.margin.domain.model.Tag
import app.margin.domain.repository.AttachmentRepository
import app.margin.domain.repository.AttachmentSource
import app.margin.domain.repository.FolderRepository
import app.margin.domain.repository.NoteRepository
import app.margin.domain.repository.TagRepository
import app.margin.domain.repository.TemplateRepository
import app.margin.domain.settings.AppSettings
import app.margin.domain.settings.SettingsRepository
import app.margin.domain.template.TemplateCatalog
import app.margin.domain.text.FindReplace
import app.margin.domain.text.MarkdownParser
import app.margin.domain.text.TextStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class EditorMeta(
    val note: Note? = null,
    val missing: Boolean = false,
    val loading: Boolean = true,
)

data class FindState(val query: String = "", val replacement: String = "", val matches: List<IntRange> = emptyList(), val current: Int = 0, val showReplace: Boolean = false)

/**
 * Editor state holder. Typing updates [body]/[title] immediately; persistence is delegated to the
 * domain [AutosaveController] (debounced, retried, never reports "saved" early). The current draft
 * is also mirrored into [SavedStateHandle] (bounded size) so it survives process death even if the
 * last debounce window had not yet elapsed.
 */
@HiltViewModel
class EditorViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val notes: NoteRepository,
    private val folders: FolderRepository,
    tagsRepo: TagRepository,
    private val attachments: AttachmentRepository,
    private val templates: TemplateRepository,
    private val settingsRepo: SettingsRepository,
    private val clock: Clock,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    val noteId: String = checkNotNull(saved.get<String>("noteId"))

    private val _meta = MutableStateFlow(EditorMeta())
    val meta: StateFlow<EditorMeta> = _meta.asStateFlow()

    private val _title = MutableStateFlow(saved.get<String>(KEY_TITLE) ?: "")
    val title: StateFlow<String> = _title.asStateFlow()

    private val restored = NoteMeta.split(saved.get<String>(KEY_BODY) ?: "")
    private val _body = MutableStateFlow(TextFieldValue(restored.second))
    val body: StateFlow<TextFieldValue> = _body.asStateFlow()

    /** Paper style and note font, stored as a hidden first line of the body (see [NoteMeta]). */
    private val _page = MutableStateFlow(restored.first)
    val page: StateFlow<NoteMeta> = _page.asStateFlow()
    private fun prefixLen() = NoteMeta.join(_page.value, "").length

    private val _preview = MutableStateFlow(saved.get<Boolean>("preview") ?: false)
    val preview: StateFlow<Boolean> = _preview.asStateFlow()

    private val _find = MutableStateFlow<FindState?>(null)
    val find: StateFlow<FindState?> = _find.asStateFlow()

    private val undo = UndoStack()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val autosave = AutosaveController(
        scope = viewModelScope,
        persist = { d -> notes.saveContent(d.noteId, d.title, d.body, d.cursor) },
        now = { clock.now() },
    )
    val saveStatus: StateFlow<SaveStatus> = autosave.status

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val tags: StateFlow<List<Tag>> = notes.observeTagsForNote(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allTagNames: StateFlow<List<String>> = tagsRepo.observeTags().map { l -> l.map { it.tag.name } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val folderList: StateFlow<List<Folder>> = folders.observeFolders(includeArchived = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val attachmentList: StateFlow<List<Attachment>> = attachments.observeForNote(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val backlinks: StateFlow<List<NoteSummary>> = notes.observeBacklinks(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live note row (pin/favorite/state changes made elsewhere). */
    val live: StateFlow<Note?> = notes.observeNote(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val note = notes.getNote(noteId)
            if (note == null) {
                _meta.value = EditorMeta(missing = true, loading = false)
                return@launch
            }
            val restoredTitle = saved.get<String>(KEY_TITLE)
            val restoredBody = saved.get<String>(KEY_BODY)
            // Seed with what is durable; if a newer draft survived process death, re-apply it as an edit.
            autosave.seed(Draft(note.id, note.title, note.body, note.lastCursor))
            if (restoredTitle == null && restoredBody == null) {
                _title.value = note.title
                val (m, content) = NoteMeta.split(note.body)
                _page.value = m
                val cursor = (note.lastCursor - prefixLen()).coerceIn(0, content.length)
                _body.value = TextFieldValue(content, TextRange(cursor))
            } else {
                autosave.onEdit(currentDraft())
            }
            if (saved.get<Boolean>("preview") == null) _preview.value = settings.value.openInPreview && _body.value.text.isNotEmpty()
            _meta.value = EditorMeta(note = note, loading = false)
            notes.markOpened(noteId)
            watchExternalChanges()
        }
    }

    /**
     * Content can change outside this editor (revision restore, AI insert from another screen,
     * import merge). If there are no unsaved local edits, adopt the stored version; if there are,
     * local edits win and will be saved (the overwritten version stays in revision history).
     */
    private fun watchExternalChanges() = viewModelScope.launch {
        notes.observeNote(noteId).collect { stored ->
            if (stored == null || autosave.hasUnsavedChanges) return@collect
            val local = autosave.currentDraft() ?: return@collect
            if (stored.title != local.title || stored.body != local.body) {
                autosave.seed(Draft(stored.id, stored.title, stored.body, stored.lastCursor))
                _title.value = stored.title
                val (m, content) = NoteMeta.split(stored.body)
                _page.value = m
                _body.value = TextFieldValue(content, TextRange(_body.value.selection.start.coerceIn(0, content.length)))
                undo.clear()
                _canUndo.value = false; _canRedo.value = false
                mirror()
            }
        }
    }

    private fun currentDraft() = Draft(noteId, _title.value, NoteMeta.join(_page.value, _body.value.text), _body.value.selection.start + prefixLen())

    fun setPageStyle(p: PageStyle) { if (_page.value.page != p) { _page.value = _page.value.copy(page = p); afterEdit() } }
    fun setNoteFont(f: NoteFont?) { if (_page.value.font != f) { _page.value = _page.value.copy(font = f); afterEdit() } }

    // Character styles: colour, highlight and font on the selection (or the word at the cursor).
    fun setColor(argb: Long?) = apply { EditorCommands.setColor(it, argb) }
    fun setHighlight(argb: Long?) = apply { EditorCommands.setHighlight(it, argb) }
    fun setSelectionFont(f: NoteFont?) = apply { EditorCommands.setFont(it, f) }
    fun clearStyles() = apply { EditorCommands.clearStyles(it) }

    private fun mirror() {
        // SavedStateHandle lives in a Bundle; keep it well under the Binder transaction limit.
        val b = NoteMeta.join(_page.value, _body.value.text)
        if (b.length <= MAX_SAVED_STATE_CHARS) { saved[KEY_BODY] = b; saved[KEY_TITLE] = _title.value }
        else { saved.remove<String>(KEY_BODY); saved.remove<String>(KEY_TITLE) }
    }

    private fun afterEdit() {
        mirror()
        autosave.onEdit(currentDraft())
        _canUndo.value = undo.canUndo
        _canRedo.value = undo.canRedo
        _find.value?.let { f -> _find.value = f.copy(matches = FindReplace.findAll(_body.value.text, f.query)) }
    }

    fun onTitleChange(t: String) {
        if (t == _title.value) return
        _title.value = t.replace('\n', ' ')
        afterEdit()
    }

    /** Text field callback: applies list continuation, records undo, schedules autosave. */
    fun onBodyChange(new: TextFieldValue) {
        val old = _body.value
        if (new.text == old.text) {
            _body.value = new // selection / composition change only
            if (new.selection != old.selection) autosave.onEdit(currentDraft())
            return
        }
        val continued = EditorCommands.onTextChanged(old.toDomain(), new.toDomain())
        val result = if (continued.text != new.text) TextFieldValue(continued.text, TextRange(continued.selStart, continued.selEnd)) else new
        undo.record(old.toDomain(), result.toDomain(), System.currentTimeMillis())
        _body.value = result
        afterEdit()
    }

    private fun apply(transform: (TextValue) -> TextValue) {
        val old = _body.value
        val r = transform(old.toDomain())
        if (r.text == old.text && r.selStart == old.selection.start && r.selEnd == old.selection.end) return
        undo.checkpoint() // formatting is always its own undo step
        undo.record(old.toDomain(), r, System.currentTimeMillis())
        undo.checkpoint()
        _body.value = TextFieldValue(r.text, TextRange(r.selStart, r.selEnd))
        afterEdit()
    }

    fun toggleInline(s: InlineStyle) = apply { EditorCommands.toggleInline(it, s) }
    fun setLineStyle(s: LineStyle) = apply { EditorCommands.setLineStyle(it, s) }
    fun indent(outdent: Boolean) = apply { EditorCommands.indent(it, outdent) }
    fun insertDivider() = apply { EditorCommands.insertDivider(it) }
    fun insertCodeBlock() = apply { EditorCommands.insertCodeBlock(it) }
    fun insertTable() = apply { EditorCommands.insertTable(it) }
    fun insertLink(url: String, label: String?) = apply { EditorCommands.insertLink(it, url.trim(), label) }
    fun insertDateTime() = apply { EditorCommands.insertDateTime(it, LocalDateTime.now()) }
    fun insertText(text: String) = apply { EditorCommands.insertText(it, text) }
    fun insertBlock(text: String) = apply { EditorCommands.insertBlock(it, text) }
    fun insertWikiLink(title: String) = apply { EditorCommands.insertText(it, "[[${title.replace("]]", "")}]]") }

    fun undo() {
        val prev = undo.undo(_body.value.toDomain()) ?: return
        _body.value = TextFieldValue(prev.text, TextRange(prev.selStart, prev.selEnd))
        afterEdit()
    }

    fun redo() {
        val next = undo.redo(_body.value.toDomain()) ?: return
        _body.value = TextFieldValue(next.text, TextRange(next.selStart, next.selEnd))
        afterEdit()
    }

    /** Preview checkbox toggle edits only that line. */
    fun toggleTask(line: Int) = apply { v -> TextValue(MarkdownParser.toggleTask(v.text, line), v.selStart.coerceAtMost(v.text.length), v.selEnd.coerceAtMost(v.text.length)) }

    fun setPreview(on: Boolean) { _preview.value = on; saved["preview"] = on }

    // ---- Find / replace ---------------------------------------------------------------------
    fun openFind(replace: Boolean = false) { _find.value = (_find.value ?: FindState()).copy(showReplace = replace) }
    fun closeFind() { _find.value = null }
    fun setFindQuery(q: String) { _find.value = (_find.value ?: FindState()).copy(query = q, matches = FindReplace.findAll(_body.value.text, q), current = 0); selectCurrentMatch() }
    fun setReplacement(r: String) { _find.update { it?.copy(replacement = r) } }
    fun nextMatch(delta: Int) {
        val f = _find.value ?: return
        if (f.matches.isEmpty()) return
        _find.value = f.copy(current = Math.floorMod(f.current + delta, f.matches.size))
        selectCurrentMatch()
    }
    private fun selectCurrentMatch() {
        val f = _find.value ?: return
        val r = f.matches.getOrNull(f.current) ?: return
        _body.value = _body.value.copy(selection = TextRange(r.first, r.last + 1))
    }
    fun replaceCurrent() {
        val f = _find.value ?: return
        val r = f.matches.getOrNull(f.current) ?: return
        apply { v -> val t = FindReplace.replaceAt(v.text, r, f.replacement); TextValue(t, r.first + f.replacement.length) }
    }
    fun replaceAll() {
        val f = _find.value ?: return
        apply { v -> val res = FindReplace.replaceAll(v.text, f.query, f.replacement); TextValue(res.text, v.selStart.coerceAtMost(res.text.length)) }
    }

    fun stats(): TextStats = TextStats.of(_body.value.text)

    // ---- Note actions -----------------------------------------------------------------------
    fun flush(onDone: (Boolean) -> Unit = {}) = viewModelScope.launch { onDone(autosave.flush()) }
    fun togglePinned() = viewModelScope.launch { live.value?.let { notes.setPinned(listOf(noteId), !it.pinned) } }
    fun toggleFavorite() = viewModelScope.launch { live.value?.let { notes.setFavorite(listOf(noteId), !it.favorite) } }
    fun setTags(t: List<String>) = viewModelScope.launch { notes.setTags(noteId, t) }
    fun move(folderId: String?) = viewModelScope.launch { notes.move(listOf(noteId), folderId) }
    fun archive() = viewModelScope.launch { autosave.flush(); notes.archive(listOf(noteId)) }
    fun unarchive() = viewModelScope.launch { notes.unarchive(listOf(noteId)) }
    fun trash(onDone: () -> Unit) = viewModelScope.launch { autosave.flush(); notes.moveToTrash(listOf(noteId)); onDone() }
    fun restore() = viewModelScope.launch { notes.restoreFromTrash(listOf(noteId)) }
    fun markReviewed() = viewModelScope.launch { notes.markReviewed(noteId) }
    fun setExcludedFromAi(excluded: Boolean) = viewModelScope.launch { notes.setExcludedFromAi(noteId, excluded) }
    fun duplicate(onDone: (String) -> Unit) = viewModelScope.launch { autosave.flush(); onDone(notes.duplicate(noteId).id) }

    fun saveAsTemplate(name: String, onDone: (String) -> Unit) = viewModelScope.launch {
        val t = TemplateCatalog.fromNote(name.ifBlank { _title.value }, _body.value.text)
        templates.saveUserTemplate(t)
        onDone(t.id)
    }

    fun addAttachment(source: AttachmentSource, onResult: (Result<Attachment>) -> Unit) = viewModelScope.launch {
        onResult(attachments.add(noteId, source))
    }
    /** Clipart goes in as a small drawing, so it can later be moved, resized and recoloured on the canvas. */
    fun addClipartDrawing(key: String, color: Long, onResult: (Result<Attachment>) -> Unit) = viewModelScope.launch {
        val d = app.margin.domain.drawing.Drawing(width = 600f, height = 600f,
            elements = listOf(app.margin.domain.drawing.Clipart("c" + java.util.UUID.randomUUID().toString().take(8), key, 100f, 100f, 400f, color)))
        val bytes = app.margin.domain.drawing.DrawingCodec.encode(d).toByteArray()
        onResult(attachments.add(noteId, app.margin.platform.BytesSource(bytes, app.margin.domain.drawing.DrawingCodec.MIME, "Clipart." + app.margin.domain.drawing.DrawingCodec.EXTENSION)))
    }

    fun removeAttachment(id: String) = viewModelScope.launch { attachments.remove(id) }
    fun setExtractedText(id: String, text: String) = viewModelScope.launch { attachments.setExtractedText(id, text) }

    suspend fun resolveTitle(title: String): String? = notes.titleIndex()[title.trim().lowercase()]

    fun createLinkedNote(title: String, onCreated: (String) -> Unit) = viewModelScope.launch {
        val n = notes.create(title = title.trim(), folderId = live.value?.folderId)
        onCreated(n.id)
    }

    fun isTrashed(): Boolean = live.value?.state == NoteState.TRASHED

    /** Leaving the editor: an empty new note is discarded instead of cluttering the library. */
    fun onLeave() {
        val note = _meta.value.note ?: return
        val emptyNow = _title.value.isBlank() && _body.value.text.isBlank()
        appScope.launch {
            if (emptyNow && note.title.isBlank() && note.body.isBlank() && attachmentList.value.isEmpty()) {
                notes.deletePermanently(listOf(noteId))
            } else {
                autosave.flush()
            }
        }
    }

    override fun onCleared() {
        // viewModelScope is cancelled; finish any pending write on the app scope.
        if (autosave.hasUnsavedChanges) appScope.launch { autosave.flush() }
        super.onCleared()
    }

    fun observeOutgoing(): Flow<List<NoteSummary>> = if (_meta.value.missing) emptyFlow() else notes.observeOutgoingLinks(noteId)

    companion object {
        private const val KEY_BODY = "draft_body"
        private const val KEY_TITLE = "draft_title"
        private const val MAX_SAVED_STATE_CHARS = 100_000
    }
}

internal fun TextFieldValue.toDomain() = TextValue(text, selection.start, selection.end)
