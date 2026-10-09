package app.margin.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Stable identifiers. Titles are never used as identity. */
object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}

/** Abstraction over time so domain rules are deterministic in tests. */
fun interface Clock {
    fun now(): Instant

    companion object {
        val System: Clock = Clock { Instant.now() }
    }
}

/** Where a note lives. Exactly one primary location: a folder, or the Inbox (folderId == null). */
enum class NoteState { ACTIVE, ARCHIVED, TRASHED }

data class Note(
    val id: String,
    val title: String,
    /** Canonical body: Markdown (see docs/ARCHITECTURE.md, "Content format"). */
    val body: String,
    val folderId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastOpenedAt: Instant? = null,
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val state: NoteState = NoteState.ACTIVE,
    /** When the note was moved to trash; used for retention purging. */
    val trashedAt: Instant? = null,
    val templateId: String? = null,
    val contentFormatVersion: Int = CONTENT_FORMAT_VERSION,
    /** Excluded from AI context and (optionally) from search snippets. */
    val excludedFromAi: Boolean = false,
    val lastCursor: Int = 0,
    val reviewedAt: Instant? = null,
    val importedAt: Instant? = null,
) {
    /** Title shown in lists: explicit title, else first non-blank line, else "Untitled". */
    fun displayTitle(untitled: String = "Untitled"): String =
        title.trim().ifEmpty { derivedTitle(body) ?: untitled }

    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()

    companion object {
        const val CONTENT_FORMAT_VERSION = 1

        private val markdownPrefix = Regex("""^(\s*(#{1,6}|>|[-*+]|\d+[.)])\s+)*(\[[ xX]]\s+)?""")

        fun derivedTitle(body: String): String? = body.lineSequence()
            .map { it.trim().replaceFirst(markdownPrefix, "").trim() }
            .filterNot { it.startsWith("```") || it == "---" }
            .firstOrNull { it.isNotEmpty() }
            ?.take(80)
    }
}

data class Folder(
    val id: String,
    val name: String,
    /** null = top-level notebook. */
    val parentId: String?,
    val color: Long? = null,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Instant,
)

data class Tag(
    val id: String,
    /** Display name as the user typed it first. */
    val name: String,
    /** Normalised key used for uniqueness (case/whitespace-insensitive). */
    val key: String,
    val color: Long? = null,
)

data class Attachment(
    val id: String,
    val noteId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Path relative to the app's private attachment directory. */
    val storagePath: String,
    val createdAt: Instant,
    /** Text extracted by OCR / import, searchable. The original file is always preserved. */
    val extractedText: String? = null,
    val sha256: String? = null,
)

data class NoteRevision(
    val id: String,
    val noteId: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
    /** Pinned revisions are protected from automatic retention cleanup. */
    val pinned: Boolean = false,
)

enum class TaskPriority { NONE, LOW, MEDIUM, HIGH }

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String = "",
    val noteId: String? = null,
    val parentTaskId: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    /** IANA zone the due date/time was expressed in (handles travel + DST). */
    val zoneId: String = java.time.ZoneId.systemDefault().id,
    val priority: TaskPriority = TaskPriority.NONE,
    val completedAt: Instant? = null,
    val recurrence: String? = null,
    val reminderOffsetMinutes: Int? = null,
    val snoozedUntil: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isCompleted: Boolean get() = completedAt != null
}

data class SavedSearch(
    val id: String,
    val name: String,
    val query: String,
    val createdAt: Instant,
)

data class NoteLink(val fromNoteId: String, val toNoteId: String)

/** Lightweight projection used by lists. */
data class NoteSummary(
    val id: String,
    val title: String,
    val preview: String,
    val folderId: String?,
    val updatedAt: Instant,
    val createdAt: Instant,
    val pinned: Boolean,
    val favorite: Boolean,
    val state: NoteState,
    val tagNames: List<String> = emptyList(),
    val hasAttachments: Boolean = false,
)
