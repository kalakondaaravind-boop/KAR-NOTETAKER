package app.margin.data.repo

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.margin.data.db.MarginDatabase
import app.margin.data.db.NoteTagEntity
import app.margin.data.db.SUMMARY_COLUMNS
import app.margin.data.db.TagEntity
import app.margin.data.db.toDomain
import app.margin.data.db.toEntity
import app.margin.domain.editor.PersistenceException
import app.margin.domain.editor.SaveError
import app.margin.domain.io.ImportPlanner
import app.margin.domain.model.Clock
import app.margin.domain.model.Ids
import app.margin.domain.model.Note
import app.margin.domain.model.NoteRevision
import app.margin.domain.model.NoteState
import app.margin.domain.model.NoteSummary
import app.margin.domain.model.Tag
import app.margin.domain.organization.NoteFilter
import app.margin.domain.organization.NoteFilterSql
import app.margin.domain.organization.NoteMetrics
import app.margin.domain.organization.SortOrder
import app.margin.domain.organization.Tags
import app.margin.domain.repository.LibraryCounts
import app.margin.domain.repository.NoteRepository
import app.margin.domain.revision.RevisionPolicy
import app.margin.domain.revision.RevisionSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Duration

class NoteRepositoryImpl(
    private val db: MarginDatabase,
    private val clock: Clock,
    private val revisionSettings: suspend () -> RevisionSettings,
    private val attachmentFiles: AttachmentFiles,
) : NoteRepository {

    private val indexer = Indexer(db)

    override fun observeNotes(filter: NoteFilter, sort: SortOrder, limit: Int): Flow<List<NoteSummary>> {
        val clause = NoteFilterSql.build(filter, clock.now())
        val sql = "SELECT $SUMMARY_COLUMNS FROM notes n WHERE ${clause.where} ORDER BY ${NoteFilterSql.orderBy(sort)} LIMIT ?"
        val q = SimpleSQLiteQuery(sql, (clause.args + limit).toTypedArray())
        return db.notes().observeSummaries(q).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observeNote(id: String): Flow<Note?> = db.notes().observe(id).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun getNote(id: String): Note? = db.notes().get(id)?.toDomain()

    override suspend fun create(title: String, body: String, folderId: String?, templateId: String?, tags: List<String>, excludedFromAi: Boolean): Note = persisting {
        val now = clock.now()
        val note = Note(Ids.newId(), title, body, folderId, now, now, lastOpenedAt = now, templateId = templateId, excludedFromAi = excludedFromAi)
        db.withTransaction {
            // A folder deleted while the note was being created → Inbox rather than a failed insert.
            val safeFolder = folderId?.takeIf { db.folders().get(it) != null }
            val entity = note.copy(folderId = safeFolder).toEntity()
            db.notes().insert(entity)
            applyTags(note.id, tags)
            indexer.reindex(entity)
            if (body.isNotEmpty()) indexer.relink(entity)
        }
        note
    }

    override suspend fun saveContent(id: String, title: String, body: String, cursor: Int) = persisting {
        db.withTransaction {
            val existing = db.notes().get(id) ?: throw PersistenceException(SaveError.NOTE_MISSING)
            if (existing.title == title && existing.body == body) {
                if (existing.lastCursor != cursor) db.notes().updateCursor(id, cursor)
                return@withTransaction
            }
            val now = clock.now()
            val settings = revisionSettings()
            val lastRev = db.revisions().latestAt(id)?.let { java.time.Instant.ofEpochMilli(it) }
            if (RevisionPolicy.shouldSnapshot(existing.title, existing.body, title, body, lastRev, now, settings)) {
                db.revisions().upsert(NoteRevision(Ids.newId(), id, existing.title, existing.body, now).toEntity())
                val prune = RevisionPolicy.toPrune(db.revisions().forNote(id).map { it.toDomain() }, now, settings)
                if (prune.isNotEmpty()) db.revisions().delete(prune.map { it.id })
            }
            val m = NoteMetrics.compute(body)
            val sortTitle = title.trim().ifEmpty { Note.derivedTitle(body) ?: "" }
            val updated = db.notes().updateContent(
                id, title, body, m.plainText, sortTitle, now.toEpochMilli(), cursor,
                m.wordCount, m.openTaskCount, m.doneTaskCount, m.linkCount, ImportPlanner.fingerprint(title, body),
            )
            if (updated != 1) throw PersistenceException(SaveError.DATABASE_UNAVAILABLE)
            val fresh = db.notes().get(id)!!
            indexer.reindex(fresh)
            indexer.relink(fresh)
        }
    }

    override suspend fun setPinned(ids: Collection<String>, pinned: Boolean) = db.notes().setPinned(ids, pinned)
    override suspend fun setFavorite(ids: Collection<String>, favorite: Boolean) = db.notes().setFavorite(ids, favorite)

    private suspend fun setState(ids: Collection<String>, state: NoteState) = db.withTransaction {
        db.notes().setState(ids, state.name, if (state == NoteState.TRASHED) clock.now().toEpochMilli() else null)
        ids.forEach { indexer.reindex(it) }
    }

    override suspend fun archive(ids: Collection<String>) = setState(ids, NoteState.ARCHIVED)
    override suspend fun unarchive(ids: Collection<String>) = setState(ids, NoteState.ACTIVE)
    override suspend fun moveToTrash(ids: Collection<String>) = setState(ids, NoteState.TRASHED)

    override suspend fun restoreFromTrash(ids: Collection<String>) = db.withTransaction {
        // folder_id is SET NULL by the FK if the folder was deleted, so restored notes land in the Inbox.
        db.notes().setState(ids, NoteState.ACTIVE.name, null)
        ids.forEach { indexer.reindex(it) }
    }

    override suspend fun deletePermanently(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val files = db.withTransaction {
            val attachments = db.attachments().forNotes(ids)
            ids.forEach { db.fts().deleteForNote(it) }
            db.notes().delete(ids) // cascades: tags links, note links, attachments rows, revisions; tasks keep, note_id → NULL
            attachments.map { it.storagePath }
        }
        // Files are removed only after the transaction committed.
        files.forEach { attachmentFiles.delete(it) }
    }

    override suspend fun emptyTrash(): Int {
        val ids = db.notes().trashedIds()
        deletePermanently(ids)
        return ids.size
    }

    override suspend fun purgeTrashOlderThanDays(days: Int): Int {
        if (days <= 0) return 0
        val ids = db.notes().trashedBefore(clock.now().minus(Duration.ofDays(days.toLong())).toEpochMilli())
        deletePermanently(ids)
        return ids.size
    }

    override suspend fun move(ids: Collection<String>, folderId: String?) = db.withTransaction {
        if (folderId != null && db.folders().get(folderId) == null) return@withTransaction
        db.notes().move(ids, folderId)
        ids.forEach { indexer.reindex(it) }
    }

    override suspend fun duplicate(id: String): Note = db.withTransaction {
        val src = db.notes().get(id)?.toDomain() ?: error("note not found")
        val now = clock.now()
        val copy = src.copy(
            id = Ids.newId(), title = if (src.title.isBlank()) "" else "${src.title} (copy)", createdAt = now, updatedAt = now,
            lastOpenedAt = now, pinned = false, state = NoteState.ACTIVE, trashedAt = null, importedAt = null,
        )
        val e = copy.toEntity()
        db.notes().insert(e)
        val tagIds = db.tags().forNote(id).map { it.id }
        db.tags().link(tagIds.map { NoteTagEntity(copy.id, it) })
        indexer.reindex(e)
        indexer.relink(e)
        copy
    }

    override suspend fun markOpened(id: String) = db.notes().markOpened(id, clock.now().toEpochMilli())
    override suspend fun markReviewed(id: String) = db.notes().markReviewed(id, clock.now().toEpochMilli())
    override suspend fun setExcludedFromAi(id: String, excluded: Boolean) = db.notes().setExcludedFromAi(id, excluded)

    override fun observeTagsForNote(id: String): Flow<List<Tag>> = db.tags().observeForNote(id).map { l -> l.map { it.toDomain() } }

    /** Finds or creates tags by normalised key; first spelling wins. Must run inside a transaction. */
    private suspend fun resolveTags(names: List<String>): List<TagEntity> = Tags.parseList(names.joinToString(",")).map { name ->
        val key = Tags.key(name)
        db.tags().byKey(key) ?: TagEntity(Ids.newId(), Tags.clean(name), key, null).also { db.tags().insert(it) }
    }

    private suspend fun applyTags(noteId: String, names: List<String>) {
        if (names.isEmpty()) return
        db.tags().link(resolveTags(names).map { NoteTagEntity(noteId, it.id) })
    }

    override suspend fun setTags(noteId: String, tagNames: List<String>) = db.withTransaction {
        db.tags().clearNote(noteId)
        applyTags(noteId, tagNames)
        indexer.reindex(noteId)
    }

    override suspend fun addTags(noteIds: Collection<String>, tagNames: List<String>) = db.withTransaction {
        val tags = resolveTags(tagNames)
        noteIds.forEach { n -> db.tags().link(tags.map { NoteTagEntity(n, it.id) }); indexer.reindex(n) }
    }

    override suspend fun removeTag(noteIds: Collection<String>, tagId: String) = db.withTransaction {
        db.tags().unlink(noteIds, tagId)
        noteIds.forEach { indexer.reindex(it) }
    }

    override fun observeRevisions(noteId: String): Flow<List<NoteRevision>> = db.revisions().observe(noteId).map { l -> l.map { it.toDomain() } }

    /** Restoring is itself reversible: the current content is snapshotted first. */
    override suspend fun restoreRevision(revisionId: String) = db.withTransaction {
        val rev = db.revisions().get(revisionId) ?: return@withTransaction
        val cur = db.notes().get(rev.noteId) ?: return@withTransaction
        val now = clock.now()
        db.revisions().upsert(NoteRevision(Ids.newId(), cur.id, cur.title, cur.body, now, pinned = false).toEntity())
        val restored = cur.toDomain().copy(title = rev.title, body = rev.body, updatedAt = now).toEntity()
        db.notes().upsert(restored)
        indexer.reindex(restored)
        indexer.relink(restored)
    }

    override suspend fun setRevisionPinned(revisionId: String, pinned: Boolean) = db.revisions().setPinned(revisionId, pinned)

    override fun observeBacklinks(noteId: String): Flow<List<NoteSummary>> = db.notes().observeBacklinks(noteId).map { l -> l.map { it.toDomain() } }
    override fun observeOutgoingLinks(noteId: String): Flow<List<NoteSummary>> = db.notes().observeOutgoing(noteId).map { l -> l.map { it.toDomain() } }
    override suspend fun titleIndex(): Map<String, String> = indexer.titleIndex()

    override fun observeCounts(): Flow<LibraryCounts> = db.notes().observeCounts().map { c ->
        LibraryCounts(c?.active ?: 0, c?.archived ?: 0, c?.trashed ?: 0, c?.pinned ?: 0, c?.favorites ?: 0)
    }
}
