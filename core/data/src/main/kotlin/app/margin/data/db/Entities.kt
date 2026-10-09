package app.margin.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Schema notes (docs/ARCHITECTURE.md §Data):
 *  - All ids are stable UUID strings. Times are epoch milliseconds (UTC).
 *  - notes.folder_id → folders: SET NULL (a vanished folder never deletes notes).
 *  - Child rows (tags, links, attachments, revisions) cascade only on permanent note deletion,
 *    which is always an explicit, confirmed user action.
 *  - Derived columns (plain_text, word_count, *_task_count, link_count, fingerprint) are recomputed
 *    on every save from the canonical body and can be rebuilt at any time.
 */

@Entity(
    tableName = "folders",
    indices = [Index("parent_id")],
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "parent_id") val parentId: String?,
    val color: Long?,
    val icon: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val pinned: Boolean,
    val archived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(entity = FolderEntity::class, parentColumns = ["id"], childColumns = ["folder_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("folder_id"), Index("updated_at"), Index("state"), Index("pinned"), Index("fingerprint")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "plain_text") val plainText: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    val pinned: Boolean,
    val favorite: Boolean,
    /** ACTIVE | ARCHIVED | TRASHED */
    val state: String,
    @ColumnInfo(name = "trashed_at") val trashedAt: Long?,
    @ColumnInfo(name = "template_id") val templateId: String?,
    @ColumnInfo(name = "content_format_version") val contentFormatVersion: Int,
    @ColumnInfo(name = "excluded_from_ai") val excludedFromAi: Boolean,
    @ColumnInfo(name = "last_cursor") val lastCursor: Int,
    @ColumnInfo(name = "reviewed_at") val reviewedAt: Long?,
    @ColumnInfo(name = "imported_at") val importedAt: Long?,
    @ColumnInfo(name = "word_count") val wordCount: Int,
    @ColumnInfo(name = "open_task_count") val openTaskCount: Int,
    @ColumnInfo(name = "done_task_count") val doneTaskCount: Int,
    @ColumnInfo(name = "link_count") val linkCount: Int,
    val fingerprint: String,
)

@Entity(tableName = "tags", indices = [Index(value = ["key"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val key: String,
    val color: Long?,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tag_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tag_id")],
)
data class NoteTagEntity(
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

@Entity(
    tableName = "note_links",
    primaryKeys = ["from_id", "to_id"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["from_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["to_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("to_id")],
)
data class NoteLinkEntity(
    @ColumnInfo(name = "from_id") val fromId: String,
    @ColumnInfo(name = "to_id") val toId: String,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("note_id")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "note_id") val noteId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "storage_path") val storagePath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "extracted_text") val extractedText: String?,
    val sha256: String?,
)

@Entity(
    tableName = "revisions",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["note_id", "created_at"])],
)
data class RevisionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "note_id") val noteId: String,
    val title: String,
    val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val pinned: Boolean,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["parent_task_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("note_id"), Index("parent_task_id"), Index("due_date"), Index("completed_at")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    @ColumnInfo(name = "note_id") val noteId: String?,
    @ColumnInfo(name = "parent_task_id") val parentTaskId: String?,
    /** ISO-8601 local date (yyyy-MM-dd) so it sorts correctly as text. */
    @ColumnInfo(name = "due_date") val dueDate: String?,
    /** ISO-8601 local time (HH:mm[:ss]). */
    @ColumnInfo(name = "due_time") val dueTime: String?,
    @ColumnInfo(name = "zone_id") val zoneId: String,
    val priority: String,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
    val recurrence: String?,
    @ColumnInfo(name = "reminder_offset_minutes") val reminderOffsetMinutes: Int?,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** User templates and overrides of built-ins, stored as validated margin.template JSON. */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val json: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "template_usage")
data class TemplateUsageEntity(
    @PrimaryKey @ColumnInfo(name = "template_id") val templateId: String,
    @ColumnInfo(name = "used_at") val usedAt: Long,
)

@Entity(tableName = "saved_searches")
data class SavedSearchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val query: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * Full-text index. Not authoritative: it is maintained in the same transaction as note writes and
 * can be rebuilt from the canonical tables at any time (Settings → Data → Rebuild search index).
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["note_id"])
@Entity(tableName = "note_fts")
data class NoteFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    @ColumnInfo(name = "note_id") val noteId: String,
    val title: String,
    val body: String,
    val tags: String,
    val folder: String,
    val attachments: String,
)
