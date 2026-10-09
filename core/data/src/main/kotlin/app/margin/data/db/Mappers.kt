package app.margin.data.db

import app.margin.domain.model.Attachment
import app.margin.domain.model.Folder
import app.margin.domain.model.Note
import app.margin.domain.model.NoteRevision
import app.margin.domain.model.NoteState
import app.margin.domain.model.NoteSummary
import app.margin.domain.model.SavedSearch
import app.margin.domain.model.Tag
import app.margin.domain.model.TaskItem
import app.margin.domain.model.TaskPriority
import app.margin.domain.io.ImportPlanner
import app.margin.domain.organization.NoteMetrics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun Long.instant(): Instant = Instant.ofEpochMilli(this)
internal fun Long?.instantOrNull(): Instant? = this?.let(Instant::ofEpochMilli)
internal const val SEP = '\u001f'

internal fun NoteEntity.toDomain() = Note(
    id = id, title = title, body = body, folderId = folderId, createdAt = createdAt.instant(), updatedAt = updatedAt.instant(),
    lastOpenedAt = lastOpenedAt.instantOrNull(), pinned = pinned, favorite = favorite,
    state = NoteState.entries.firstOrNull { it.name == state } ?: NoteState.ACTIVE,
    trashedAt = trashedAt.instantOrNull(), templateId = templateId, contentFormatVersion = contentFormatVersion,
    excludedFromAi = excludedFromAi, lastCursor = lastCursor, reviewedAt = reviewedAt.instantOrNull(), importedAt = importedAt.instantOrNull(),
)

/** Builds the row including every derived column, so derived data can never drift from the body. */
internal fun Note.toEntity(): NoteEntity {
    val m = NoteMetrics.compute(body)
    return NoteEntity(
        id = id, title = title, body = body, plainText = m.plainText, sortTitle = displayTitle(""), folderId = folderId,
        createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(), lastOpenedAt = lastOpenedAt?.toEpochMilli(),
        pinned = pinned, favorite = favorite, state = state.name, trashedAt = trashedAt?.toEpochMilli(), templateId = templateId,
        contentFormatVersion = contentFormatVersion, excludedFromAi = excludedFromAi, lastCursor = lastCursor,
        reviewedAt = reviewedAt?.toEpochMilli(), importedAt = importedAt?.toEpochMilli(), wordCount = m.wordCount,
        openTaskCount = m.openTaskCount, doneTaskCount = m.doneTaskCount, linkCount = m.linkCount,
        fingerprint = ImportPlanner.fingerprint(title, body),
    )
}

internal fun NoteSummaryRow.toDomain() = NoteSummary(
    id = id,
    title = title.trim().ifEmpty { Note.derivedTitle(preview) ?: "" },
    preview = preview.let { p -> if (title.isBlank()) p.substringAfter('\n', "") else p }.replace('\n', ' ').take(200),
    folderId = folderId, updatedAt = updatedAt.instant(), createdAt = createdAt.instant(), pinned = pinned, favorite = favorite,
    state = NoteState.entries.firstOrNull { it.name == state } ?: NoteState.ACTIVE,
    tagNames = tagNames?.split(SEP)?.filter { it.isNotEmpty() }?.sorted() ?: emptyList(),
    hasAttachments = attachmentCount > 0,
)

internal fun FolderEntity.toDomain() = Folder(id, name, parentId, color, icon, sortOrder, pinned, archived, createdAt.instant())
internal fun Folder.toEntity() = FolderEntity(id, name, parentId, color, icon, sortOrder, pinned, archived, createdAt.toEpochMilli())

internal fun TagEntity.toDomain() = Tag(id, name, key, color)
internal fun Tag.toEntity() = TagEntity(id, name, key, color)

internal fun AttachmentEntity.toDomain() = Attachment(id, noteId, fileName, mimeType, sizeBytes, storagePath, createdAt.instant(), extractedText, sha256)
internal fun Attachment.toEntity() = AttachmentEntity(id, noteId, fileName, mimeType, sizeBytes, storagePath, createdAt.toEpochMilli(), extractedText, sha256)

internal fun RevisionEntity.toDomain() = NoteRevision(id, noteId, title, body, createdAt.instant(), pinned)
internal fun NoteRevision.toEntity() = RevisionEntity(id, noteId, title, body, createdAt.toEpochMilli(), pinned)

internal fun TaskEntity.toDomain() = TaskItem(
    id = id, title = title, notes = notes, noteId = noteId, parentTaskId = parentTaskId,
    dueDate = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    dueTime = dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
    zoneId = zoneId, priority = TaskPriority.entries.firstOrNull { it.name == priority } ?: TaskPriority.NONE,
    completedAt = completedAt.instantOrNull(), recurrence = recurrence, reminderOffsetMinutes = reminderOffsetMinutes,
    snoozedUntil = snoozedUntil.instantOrNull(), createdAt = createdAt.instant(), updatedAt = updatedAt.instant(),
)

internal fun TaskItem.toEntity() = TaskEntity(
    id = id, title = title, notes = notes, noteId = noteId, parentTaskId = parentTaskId, dueDate = dueDate?.toString(),
    dueTime = dueTime?.toString(), zoneId = zoneId, priority = priority.name, completedAt = completedAt?.toEpochMilli(),
    recurrence = recurrence, reminderOffsetMinutes = reminderOffsetMinutes, snoozedUntil = snoozedUntil?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
)

internal fun SavedSearchEntity.toDomain() = SavedSearch(id, name, query, createdAt.instant())
internal fun SavedSearch.toEntity() = SavedSearchEntity(id, name, query, createdAt.toEpochMilli())
