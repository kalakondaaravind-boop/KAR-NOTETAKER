package app.margin.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

/** List projection: no body, only what list rows need. */
data class NoteSummaryRow(
    val id: String,
    val title: String,
    val preview: String,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val pinned: Boolean,
    val favorite: Boolean,
    val state: String,
    @ColumnInfo(name = "tag_names") val tagNames: String?,
    @ColumnInfo(name = "attachment_count") val attachmentCount: Int,
)

/** Everything the search engine needs for one note, assembled in a single query. */
data class SearchRow(
    val id: String,
    val title: String,
    @ColumnInfo(name = "plain_text") val plainText: String,
    @ColumnInfo(name = "folder_id") val folderId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val pinned: Boolean,
    val favorite: Boolean,
    val state: String,
    @ColumnInfo(name = "excluded_from_ai") val excludedFromAi: Boolean,
    @ColumnInfo(name = "open_task_count") val openTaskCount: Int,
    @ColumnInfo(name = "done_task_count") val doneTaskCount: Int,
    @ColumnInfo(name = "link_count") val linkCount: Int,
    @ColumnInfo(name = "tag_names") val tagNames: String?,
    @ColumnInfo(name = "attachment_text") val attachmentText: String?,
    @ColumnInfo(name = "attachment_count") val attachmentCount: Int,
)

data class CountsRow(val active: Int, val archived: Int, val trashed: Int, val pinned: Int, val favorites: Int)
data class FolderCountRow(@ColumnInfo(name = "folder_id") val folderId: String?, val count: Int)
data class TagCountRow(val id: String, val name: String, val key: String, val color: Long?, val count: Int)

const val SUMMARY_COLUMNS = """n.id, n.title, substr(n.plain_text, 1, 240) AS preview, n.folder_id, n.updated_at, n.created_at,
    n.pinned, n.favorite, n.state,
    (SELECT group_concat(t.name, char(31)) FROM note_tags nt JOIN tags t ON t.id = nt.tag_id WHERE nt.note_id = n.id) AS tag_names,
    (SELECT count(*) FROM attachments a WHERE a.note_id = n.id) AS attachment_count"""

const val SEARCH_COLUMNS = """n.id, n.title, n.plain_text, n.folder_id, n.created_at, n.updated_at, n.pinned, n.favorite, n.state,
    n.excluded_from_ai, n.open_task_count, n.done_task_count, n.link_count,
    (SELECT group_concat(t.name, char(31)) FROM note_tags nt JOIN tags t ON t.id = nt.tag_id WHERE nt.note_id = n.id) AS tag_names,
    (SELECT group_concat(a.file_name || ' ' || coalesce(a.extracted_text, ''), char(10)) FROM attachments a WHERE a.note_id = n.id) AS attachment_text,
    (SELECT count(*) FROM attachments a WHERE a.note_id = n.id) AS attachment_count"""

@Dao
interface NoteDao {
    @RawQuery(observedEntities = [NoteEntity::class, NoteTagEntity::class, TagEntity::class, AttachmentEntity::class])
    fun observeSummaries(query: SupportSQLiteQuery): Flow<List<NoteSummaryRow>>

    @RawQuery
    suspend fun searchRows(query: SupportSQLiteQuery): List<SearchRow>

    @RawQuery
    suspend fun searchSummaries(query: SupportSQLiteQuery): List<NoteSummaryRow>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun getMany(ids: Collection<String>): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Query("SELECT id FROM notes")
    suspend fun allIds(): List<String>

    @Query("SELECT fingerprint FROM notes")
    suspend fun allFingerprints(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    @Upsert
    suspend fun upsert(note: NoteEntity)

    @Query("""UPDATE notes SET title = :title, body = :body, plain_text = :plainText, sort_title = :sortTitle, updated_at = :updatedAt,
        last_cursor = :cursor, word_count = :wordCount, open_task_count = :openTasks, done_task_count = :doneTasks,
        link_count = :links, fingerprint = :fingerprint WHERE id = :id""")
    suspend fun updateContent(
        id: String, title: String, body: String, plainText: String, sortTitle: String, updatedAt: Long, cursor: Int,
        wordCount: Int, openTasks: Int, doneTasks: Int, links: Int, fingerprint: String,
    ): Int

    @Query("UPDATE notes SET last_cursor = :cursor WHERE id = :id")
    suspend fun updateCursor(id: String, cursor: Int)

    @Query("UPDATE notes SET pinned = :pinned WHERE id IN (:ids)")
    suspend fun setPinned(ids: Collection<String>, pinned: Boolean)

    @Query("UPDATE notes SET favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: Collection<String>, favorite: Boolean)

    @Query("UPDATE notes SET state = :state, trashed_at = :trashedAt WHERE id IN (:ids)")
    suspend fun setState(ids: Collection<String>, state: String, trashedAt: Long?)

    @Query("UPDATE notes SET folder_id = :folderId WHERE id IN (:ids)")
    suspend fun move(ids: Collection<String>, folderId: String?)

    @Query("UPDATE notes SET last_opened_at = :at WHERE id = :id")
    suspend fun markOpened(id: String, at: Long)

    @Query("UPDATE notes SET reviewed_at = :at WHERE id = :id")
    suspend fun markReviewed(id: String, at: Long)

    @Query("UPDATE notes SET excluded_from_ai = :excluded WHERE id = :id")
    suspend fun setExcludedFromAi(id: String, excluded: Boolean)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<String>): Int

    @Query("SELECT id FROM notes WHERE state = 'TRASHED'")
    suspend fun trashedIds(): List<String>

    @Query("SELECT id FROM notes WHERE state = 'TRASHED' AND trashed_at IS NOT NULL AND trashed_at < :before")
    suspend fun trashedBefore(before: Long): List<String>

    @Query("SELECT id FROM notes WHERE folder_id IN (:folderIds)")
    suspend fun idsInFolders(folderIds: Collection<String>): List<String>

    @Query("SELECT id, title, plain_text FROM notes WHERE state != 'TRASHED' ORDER BY updated_at DESC")
    suspend fun titlesForLinking(): List<TitleRow>

    @Query("""SELECT
        coalesce(SUM(state = 'ACTIVE'), 0) AS active, coalesce(SUM(state = 'ARCHIVED'), 0) AS archived,
        coalesce(SUM(state = 'TRASHED'), 0) AS trashed, coalesce(SUM(state = 'ACTIVE' AND pinned), 0) AS pinned,
        coalesce(SUM(state = 'ACTIVE' AND favorite), 0) AS favorites FROM notes""")
    fun observeCounts(): Flow<CountsRow?>

    @Query("SELECT folder_id, count(*) AS count FROM notes WHERE state = 'ACTIVE' GROUP BY folder_id")
    fun observeFolderCounts(): Flow<List<FolderCountRow>>

    @Query("SELECT $SUMMARY_COLUMNS FROM notes n JOIN note_links l ON l.from_id = n.id WHERE l.to_id = :noteId AND n.state != 'TRASHED' ORDER BY n.updated_at DESC")
    fun observeBacklinks(noteId: String): Flow<List<NoteSummaryRow>>

    @Query("SELECT $SUMMARY_COLUMNS FROM notes n JOIN note_links l ON l.to_id = n.id WHERE l.from_id = :noteId AND n.state != 'TRASHED' ORDER BY n.updated_at DESC")
    fun observeOutgoing(noteId: String): Flow<List<NoteSummaryRow>>

    @Query("SELECT count(*) FROM notes")
    suspend fun count(): Int

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

data class TitleRow(val id: String, val title: String, @ColumnInfo(name = "plain_text") val plainText: String)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE archived = 0 OR :includeArchived ORDER BY pinned DESC, sort_order, name COLLATE NOCASE")
    fun observe(includeArchived: Boolean): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun all(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(f: FolderEntity)

    @Upsert
    suspend fun upsert(f: FolderEntity)

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE folders SET parent_id = :parentId WHERE id = :id")
    suspend fun setParent(id: String, parentId: String?)

    @Query("UPDATE folders SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE folders SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("UPDATE folders SET color = :color, icon = :icon WHERE id = :id")
    suspend fun setAppearance(id: String, color: Long?, icon: String?)

    @Query("UPDATE folders SET sort_order = :order WHERE id = :id")
    suspend fun setOrder(id: String, order: Int)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<String>)

    @Query("DELETE FROM folders")
    suspend fun deleteAll()
}

@Dao
interface TagDao {
    @Query("""SELECT t.id, t.name, t.key, t.color,
        (SELECT count(*) FROM note_tags nt JOIN notes n ON n.id = nt.note_id WHERE nt.tag_id = t.id AND n.state != 'TRASHED') AS count
        FROM tags t ORDER BY t.name COLLATE NOCASE""")
    fun observeWithCounts(): Flow<List<TagCountRow>>

    @Query("SELECT * FROM tags")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE `key` = :key")
    suspend fun byKey(key: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun get(id: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(t: TagEntity)

    @Upsert
    suspend fun upsert(t: TagEntity)

    @Query("UPDATE tags SET name = :name, `key` = :key WHERE id = :id")
    suspend fun rename(id: String, name: String, key: String)

    @Query("UPDATE tags SET color = :color WHERE id = :id")
    suspend fun setColor(id: String, color: Long?)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT DISTINCT tag_id FROM note_tags)")
    suspend fun deleteUnused(): Int

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tag_id = t.id WHERE nt.note_id = :noteId ORDER BY t.name COLLATE NOCASE")
    fun observeForNote(noteId: String): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tag_id = t.id WHERE nt.note_id = :noteId")
    suspend fun forNote(noteId: String): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(refs: List<NoteTagEntity>)

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    suspend fun clearNote(noteId: String)

    @Query("DELETE FROM note_tags WHERE tag_id = :tagId AND note_id IN (:noteIds)")
    suspend fun unlink(noteIds: Collection<String>, tagId: String)

    @Query("UPDATE OR IGNORE note_tags SET tag_id = :to WHERE tag_id = :from")
    suspend fun reassign(from: String, to: String)

    @Query("SELECT * FROM note_tags")
    suspend fun allLinks(): List<NoteTagEntity>

    @Query("SELECT DISTINCT note_id FROM note_tags WHERE tag_id = :tagId")
    suspend fun notesWithTag(tagId: String): List<String>

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}

@Dao
interface LinkDao {
    @Query("DELETE FROM note_links WHERE from_id = :fromId")
    suspend fun clearFrom(fromId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(links: List<NoteLinkEntity>)

    @Query("SELECT * FROM note_links")
    suspend fun all(): List<NoteLinkEntity>
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE note_id = :noteId ORDER BY created_at")
    fun observeForNote(noteId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT a.* FROM attachments a JOIN notes n ON n.id = a.note_id WHERE n.state != 'TRASHED' ORDER BY a.created_at DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments")
    suspend fun all(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE note_id IN (:noteIds)")
    suspend fun forNotes(noteIds: Collection<String>): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Upsert
    suspend fun upsert(a: AttachmentEntity)

    @Query("UPDATE attachments SET extracted_text = :text WHERE id = :id")
    suspend fun setExtractedText(id: String, text: String?)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT coalesce(sum(size_bytes), 0) FROM attachments")
    suspend fun totalBytes(): Long
}

@Dao
interface RevisionDao {
    @Query("SELECT * FROM revisions WHERE note_id = :noteId ORDER BY created_at DESC")
    fun observe(noteId: String): Flow<List<RevisionEntity>>

    @Query("SELECT * FROM revisions WHERE note_id = :noteId")
    suspend fun forNote(noteId: String): List<RevisionEntity>

    @Query("SELECT max(created_at) FROM revisions WHERE note_id = :noteId")
    suspend fun latestAt(noteId: String): Long?

    @Query("SELECT * FROM revisions WHERE id = :id")
    suspend fun get(id: String): RevisionEntity?

    @Query("SELECT * FROM revisions")
    suspend fun all(): List<RevisionEntity>

    @Upsert
    suspend fun upsert(r: RevisionEntity)

    @Query("UPDATE revisions SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("DELETE FROM revisions WHERE id IN (:ids)")
    suspend fun delete(ids: Collection<String>)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE completed_at IS NULL ORDER BY due_date IS NULL, due_date, due_time IS NULL, due_time, created_at")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE completed_at IS NOT NULL ORDER BY completed_at DESC LIMIT :limit")
    fun observeCompleted(limit: Int): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE note_id = :noteId ORDER BY completed_at IS NOT NULL, due_date")
    fun observeForNote(noteId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE completed_at IS NULL AND (reminder_offset_minutes IS NOT NULL OR snoozed_until IS NOT NULL)")
    suspend fun withReminders(): List<TaskEntity>

    @Upsert
    suspend fun upsert(t: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates")
    fun observeAll(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates")
    suspend fun all(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun get(id: String): TemplateEntity?

    @Upsert
    suspend fun upsert(t: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT template_id FROM template_usage ORDER BY used_at DESC LIMIT 12")
    fun observeRecent(): Flow<List<String>>

    @Upsert
    suspend fun markUsed(u: TemplateUsageEntity)

    @Query("DELETE FROM templates")
    suspend fun deleteAll()
}

@Dao
interface SavedSearchDao {
    @Query("SELECT * FROM saved_searches ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<SavedSearchEntity>>

    @Query("SELECT * FROM saved_searches")
    suspend fun all(): List<SavedSearchEntity>

    @Upsert
    suspend fun upsert(s: SavedSearchEntity)

    @Query("DELETE FROM saved_searches WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM saved_searches")
    suspend fun deleteAll()
}

@Dao
interface FtsDao {
    @Insert
    suspend fun insert(row: NoteFtsEntity)

    @Query("DELETE FROM note_fts WHERE note_id = :noteId")
    suspend fun deleteForNote(noteId: String)

    @Query("DELETE FROM note_fts")
    suspend fun clear()

    /** Candidate ids for a MATCH expression produced by FtsQueryBuilder (standard query syntax). */
    @Query("SELECT note_id FROM note_fts WHERE note_fts MATCH :match LIMIT :limit")
    suspend fun match(match: String, limit: Int): List<String>
}
