package app.margin.domain.editor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import java.time.Instant

data class Draft(val noteId: String, val title: String, val body: String, val cursor: Int = 0) {
    /** Cursor is not canonical content: changing only the cursor never triggers a content write. */
    fun sameContent(o: Draft?) = o != null && o.noteId == noteId && o.title == title && o.body == body
}

sealed interface SaveStatus {
    data object Idle : SaveStatus
    /** Unsaved changes exist. They are not yet durable. */
    data object Pending : SaveStatus
    data object Saving : SaveStatus
    /** Reported only after the persistence call returned successfully. */
    data class Saved(val at: Instant) : SaveStatus
    data class Failed(val reason: SaveError, val attempts: Int, val willRetry: Boolean) : SaveStatus
}

enum class SaveError {
    STORAGE_FULL,
    DATABASE_UNAVAILABLE,
    /** The note was permanently deleted elsewhere; the editor keeps the text so the user can copy it. */
    NOTE_MISSING,
    UNKNOWN,
}

/** Thrown by persistence implementations to give the controller a typed reason. */
class PersistenceException(val error: SaveError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * Autosave pipeline (docs/ARCHITECTURE.md §Autosave):
 *  1. [onEdit] updates the in-memory draft immediately (never blocks typing).
 *  2. A debounced job persists the latest draft through [persist].
 *  3. Status moves Pending → Saving → Saved only after durable success.
 *  4. Recoverable failures retry with exponential backoff up to [maxAttempts]; content stays in memory.
 *  5. Identical content is never written twice.
 *  6. [flush] persists synchronously when leaving the editor / on lifecycle stop.
 */
class AutosaveController(
    private val scope: CoroutineScope,
    private val persist: suspend (Draft) -> Unit,
    private val now: () -> Instant = { Instant.now() },
    private val debounceMs: Long = 600,
    private val maxAttempts: Int = 4,
    private val retryBaseMs: Long = 500,
) {
    private val _status = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val status: StateFlow<SaveStatus> = _status.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var latest: Draft? = null
    @Volatile private var persisted: Draft? = null
    @Volatile private var version = 0L
    private var job: Job? = null

    /** Seed with content already in the database (so opening a note does not cause a write). */
    fun seed(draft: Draft) {
        latest = draft
        persisted = draft
        _status.value = SaveStatus.Idle
    }

    val hasUnsavedChanges: Boolean get() = !(latest?.sameContent(persisted) ?: true)

    fun currentDraft(): Draft? = latest

    fun onEdit(draft: Draft) {
        latest = draft
        if (draft.sameContent(persisted)) {
            // Cursor-only change or edit reverted to the saved text.
            if (_status.value is SaveStatus.Pending) _status.value = SaveStatus.Idle
            return
        }
        version++
        _status.value = SaveStatus.Pending
        job?.cancel()
        job = scope.launch {
            delay(debounceMs)
            // Once a write starts it must not be cancelled by the next keystroke; the mutex
            // serialises it with any later save, which then writes the newer draft.
            withContext(NonCancellable) { saveWithRetry() }
        }
    }

    /** Persist now. Returns true if everything is durable afterwards. */
    suspend fun flush(): Boolean {
        job?.cancel()
        return saveWithRetry(retry = false)
    }

    private suspend fun saveWithRetry(retry: Boolean = true): Boolean {
        var attempt = 0
        while (true) {
            attempt++
            val result = saveOnce()
            if (result == null) return true
            val willRetry = retry && attempt < maxAttempts && result != SaveError.STORAGE_FULL && result != SaveError.NOTE_MISSING
            _status.value = SaveStatus.Failed(result, attempt, willRetry)
            if (!willRetry) return false
            delay(retryBaseMs shl (attempt - 1))
        }
    }

    /** Returns null on success (or nothing to do), otherwise the error. */
    private suspend fun saveOnce(): SaveError? = mutex.withLock {
        val draft = latest ?: return null
        val v = version
        if (draft.sameContent(persisted)) {
            if (_status.value !is SaveStatus.Saved && _status.value !is SaveStatus.Idle) _status.value = SaveStatus.Saved(now())
            return null
        }
        _status.value = SaveStatus.Saving
        try {
            persist(draft)
        } catch (c: CancellationException) {
            _status.value = SaveStatus.Pending
            throw c
        } catch (p: PersistenceException) {
            return p.error
        } catch (t: Throwable) {
            return SaveError.UNKNOWN
        }
        persisted = draft
        _status.value = if (version == v && latest.let { it == null || it.sameContent(draft) }) SaveStatus.Saved(now()) else SaveStatus.Pending
        null
    }
}
