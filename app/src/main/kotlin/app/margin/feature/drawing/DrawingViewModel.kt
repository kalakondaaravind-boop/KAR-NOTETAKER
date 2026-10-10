package app.margin.feature.drawing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.margin.data.repo.AttachmentFiles
import app.margin.di.AppScope
import app.margin.domain.drawing.Clipart
import app.margin.domain.drawing.Drawing
import app.margin.domain.drawing.DrawingCodec
import app.margin.domain.drawing.DrawingHistory
import app.margin.domain.drawing.DrawingOps
import app.margin.domain.drawing.Element
import app.margin.domain.drawing.Pt
import app.margin.domain.drawing.TextBox
import app.margin.domain.model.PageStyle
import app.margin.domain.repository.AttachmentRepository
import app.margin.platform.BytesSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** Copied drawing elements, shared between drawings for this app session (in memory only). */
object DrawingClipboard {
    val items = MutableStateFlow<List<Element>>(emptyList())
}

@HiltViewModel
class DrawingViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val attachments: AttachmentRepository,
    private val files: AttachmentFiles,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {
    val noteId: String = saved.get<String>("noteId").orEmpty()
    private var attachmentId: String? = saved.get<String>("attachment")?.takeIf { it.isNotBlank() }

    private var history = DrawingHistory(Drawing(page = PageStyle.from(saved.get<String>("page"))))
    private val _drawing = MutableStateFlow(history.current)
    /** What is on screen: the committed drawing, or a live preview while a gesture is in progress. */
    val drawing: StateFlow<Drawing> = _drawing.asStateFlow()

    val selection = MutableStateFlow<Set<String>>(emptySet())
    data class UndoState(val canUndo: Boolean = false, val canRedo: Boolean = false)
    val undoState = MutableStateFlow(UndoState())
    val loaded = MutableStateFlow(false)
    /** Set when the drawing can't be read or saved; the screen shows it. */
    val problem = MutableStateFlow<Problem?>(null)
    enum class Problem { LOAD, SAVE }
    val saving = MutableStateFlow(false)

    private val saveLock = Mutex()
    private var saveJob: Job? = null
    @Volatile private var dirty = false

    init {
        viewModelScope.launch {
            attachmentId?.let { id ->
                val d = withContext(Dispatchers.IO) {
                    runCatching {
                        val a = attachments.get(id) ?: return@runCatching null
                        files.open(a.storagePath)?.use { s -> DrawingCodec.decode(s.readBytes().toString(Charsets.UTF_8)) }
                    }.getOrNull()
                }
                if (d == null) problem.value = Problem.LOAD else { history = DrawingHistory(d); _drawing.value = d }
            }
            sync()
            loaded.value = true
        }
    }

    fun newId(): String = UUID.randomUUID().toString().substring(0, 12)

    private fun sync() { undoState.value = UndoState(history.canUndo, history.canRedo) }

    /** Shows [d] without recording it (during a drag). Call [commit] when the gesture ends. */
    fun preview(d: Drawing) { _drawing.value = d }
    fun cancelPreview() { _drawing.value = history.current }

    fun commit(d: Drawing = _drawing.value) {
        history.push(d)
        _drawing.value = history.current
        val ids = history.current.elements.map { it.id }.toSet()
        selection.value = selection.value.filter { it in ids }.toSet()
        sync(); scheduleSave()
    }

    private fun base() = history.current

    fun add(e: Element, select: Boolean = false) {
        commit(DrawingOps.add(base(), e))
        if (select) selection.value = setOf(e.id)
    }

    fun addStroke(points: List<Pt>, color: Long, width: Float, tool: app.margin.domain.drawing.InkTool) {
        val pts = DrawingOps.finishStroke(points)
        if (pts.isEmpty()) return
        add(app.margin.domain.drawing.Stroke(newId(), pts, color, width, tool))
    }

    fun addClipart(key: String, center: Pt, color: Long, size: Float = 160f) =
        add(Clipart(newId(), key, center.x - size / 2, center.y - size / 2, size, color), select = true)

    fun upsertText(t: TextBox) {
        val d = base()
        if (d.byId(t.id) != null) commit(d.copy(elements = d.elements.map { if (it.id == t.id) t else it }))
        else add(t, select = true)
    }

    fun select(ids: Set<String>) { selection.value = ids }
    fun clearSelection() { selection.value = emptySet() }
    fun selectAll() { selection.value = base().elements.map { it.id }.toSet() }

    fun undo() { _drawing.value = history.undo(); prune(); sync(); scheduleSave() }
    fun redo() { _drawing.value = history.redo(); prune(); sync(); scheduleSave() }
    private fun prune() { val ids = history.current.elements.map { it.id }.toSet(); selection.value = selection.value.filter { it in ids }.toSet() }

    fun copy() { val c = DrawingOps.copy(base(), selection.value); if (c.isNotEmpty()) DrawingClipboard.items.value = c }
    fun cut() { copy(); delete() }
    fun delete() { val s = selection.value; if (s.isEmpty()) return; commit(DrawingOps.delete(base(), s)); selection.value = emptySet() }
    fun paste(at: Pt? = null) {
        val (d, ids) = DrawingOps.paste(base(), DrawingClipboard.items.value, ::newId, at)
        if (ids.isEmpty()) return
        commit(d); selection.value = ids
    }
    fun duplicate() { val (d, ids) = DrawingOps.duplicate(base(), selection.value, ::newId); if (ids.isNotEmpty()) { commit(d); selection.value = ids } }
    fun recolor(c: Long) = commit(DrawingOps.recolor(base(), selection.value, c))
    fun toFront() = commit(DrawingOps.bringToFront(base(), selection.value))
    fun toBack() = commit(DrawingOps.sendToBack(base(), selection.value))
    fun setPage(p: PageStyle) = commit(DrawingOps.setPage(base(), p))
    fun setBackground(c: Long) = commit(base().copy(background = c or 0xFF000000))
    fun clearPage() { commit(DrawingOps.clear(base())); selection.value = emptySet() }

    private fun scheduleSave() {
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(700); save() }
    }

    /** Writes the drawing as an attachment of the note. A brand-new, empty drawing is not saved. */
    suspend fun save() = withContext(NonCancellable) {
        saveLock.withLock {
            if (!dirty) return@withLock
            val d = history.current
            if (attachmentId == null && d.elements.isEmpty()) { dirty = false; return@withLock }
            dirty = false
            saving.value = true
            val bytes = withContext(Dispatchers.Default) { DrawingCodec.encode(d).toByteArray() }
            val text = d.elements.filterIsInstance<TextBox>().joinToString("\n") { it.text }.ifBlank { null }
            val src = BytesSource(bytes, DrawingCodec.MIME, "Drawing." + DrawingCodec.EXTENSION)
            val id = attachmentId
            val r = if (id == null) attachments.add(noteId, src).onSuccess { a ->
                attachmentId = a.id
                if (text != null) attachments.setExtractedText(a.id, text)
            } else attachments.replace(id, src, text)
            r.onFailure { dirty = true; problem.value = Problem.SAVE }
            r.onSuccess { if (problem.value == Problem.SAVE) problem.value = null }
            saving.value = false
        }
    }

    /** Saves now, even if the screen is closing. */
    fun flush() { saveJob?.cancel(); appScope.launch { save() } }

    override fun onCleared() { flush(); super.onCleared() }
}
