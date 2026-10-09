package app.margin.data.repo

import androidx.room.withTransaction
import app.margin.data.db.MarginDatabase
import app.margin.data.db.TemplateEntity
import app.margin.data.db.toDomain
import app.margin.data.db.toEntity
import app.margin.domain.io.DuplicatePolicy
import app.margin.domain.io.ImportCandidate
import app.margin.domain.io.ImportIssue
import app.margin.domain.io.ImportLimits
import app.margin.domain.io.ImportPlanner
import app.margin.domain.io.ImportReport
import app.margin.domain.io.LibrarySnapshot
import app.margin.domain.io.FailReason
import app.margin.domain.model.Attachment
import app.margin.domain.model.Clock
import app.margin.domain.model.Folder
import app.margin.domain.model.Ids
import app.margin.domain.model.Note
import app.margin.domain.model.Tag
import app.margin.domain.organization.Folders
import app.margin.domain.organization.Tags
import app.margin.domain.repository.AttachmentRepository
import app.margin.domain.repository.AttachmentSource
import app.margin.domain.repository.LibraryRepository
import app.margin.domain.repository.RestoreMode
import app.margin.domain.template.BuiltInTemplates
import app.margin.domain.template.TemplateJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class AttachmentRepositoryImpl(
    private val db: MarginDatabase,
    private val files: AttachmentFiles,
    private val clock: Clock,
) : AttachmentRepository {
    private val indexer = Indexer(db)

    override fun observeForNote(noteId: String): Flow<List<Attachment>> = db.attachments().observeForNote(noteId).map { l -> l.map { it.toDomain() } }
    override fun observeAll(): Flow<List<Attachment>> = db.attachments().observeAll().map { l -> l.map { it.toDomain() } }

    /** Copies the source into private storage first; the row is inserted only after the file is safely written. */
    override suspend fun add(noteId: String, source: AttachmentSource): Result<Attachment> = runCatching {
        val id = Ids.newId()
        val stored = files.store(id, source.open(), ImportLimits.MAX_ATTACHMENT_BYTES)
        val a = Attachment(id, noteId, sanitizeName(source.displayName), source.mimeType.ifBlank { "application/octet-stream" },
            stored.size, stored.path, clock.now(), null, stored.sha256)
        try {
            db.withTransaction {
                db.attachments().upsert(a.toEntity())
                indexer.reindex(noteId)
            }
        } catch (e: Exception) {
            files.delete(stored.path) // keep storage consistent with the database
            throw e
        }
        a
    }

    private fun sanitizeName(n: String) = n.replace(Regex("""[\\/\u0000-\u001F]"""), "_").trim().ifEmpty { "attachment" }.take(200)

    override suspend fun remove(id: String) {
        val a = db.attachments().get(id) ?: return
        db.withTransaction {
            db.attachments().delete(id)
            indexer.reindex(a.noteId)
        }
        files.delete(a.storagePath)
    }

    override suspend fun setExtractedText(id: String, text: String?) = db.withTransaction {
        db.attachments().setExtractedText(id, text?.take(1_000_000))
        db.attachments().get(id)?.let { indexer.reindex(it.noteId) }
        Unit
    }

    override suspend fun storageBytes(): Long = files.totalBytes()

    override suspend fun findBroken(): List<String> = db.attachments().all().filter { a ->
        !files.exists(a.storagePath) || (a.sha256 != null && files.sha256(a.storagePath) != a.sha256)
    }.map { it.id }
}

class LibraryRepositoryImpl(
    private val db: MarginDatabase,
    private val files: AttachmentFiles,
    private val clock: Clock,
) : LibraryRepository {
    private val indexer = Indexer(db)

    override suspend fun snapshot(includeRevisions: Boolean): LibrarySnapshot = db.withTransaction {
        // Read everything inside one transaction for a consistent point-in-time view.
        LibrarySnapshot(
            notes = db.notes().all().map { it.toDomain() },
            folders = db.folders().all().map { it.toDomain() },
            tags = db.tags().all().map { it.toDomain() },
            noteTags = db.tags().allLinks().map { it.noteId to it.tagId },
            attachments = db.attachments().all().map { it.toDomain() },
            revisions = if (includeRevisions) db.revisions().all().map { it.toDomain() } else emptyList(),
            tasks = db.tasks().all().map { it.toDomain() },
            templates = db.templates().all().mapNotNull { TemplateJson.decode(it.json).getOrNull() },
            savedSearches = db.savedSearches().all().map { it.toDomain() },
        )
    }

    /**
     * Applies an already-verified snapshot in one transaction. Attachment files are copied from the
     * staging area only after the transaction succeeds; on failure nothing changes.
     */
    override suspend fun restore(snapshot: LibrarySnapshot, mode: RestoreMode, stagedAttachments: Map<String, File>) {
        require(snapshot.integrityProblems().isEmpty()) { "snapshot is inconsistent" }
        val newAttachmentIds = db.withTransaction {
            val existingNotes = db.notes().allIds().toSet()
            val existingAttachments = db.attachments().all().map { it.id }.toSet()
            if (mode == RestoreMode.REPLACE) wipeInTransaction()
            val keep: (String) -> Boolean = { mode == RestoreMode.REPLACE || it !in existingNotes }
            // Folders first (parents before children), then tags, notes and dependents.
            val existingFolders = db.folders().all().map { it.id }.toSet()
            Folders.flatten(snapshot.folders, includeArchived = true).map { it.first }
                .filter { mode == RestoreMode.REPLACE || it.id !in existingFolders }
                .forEach { db.folders().upsert(it.toEntity()) }
            // Tags: merge by normalised key so MERGE never creates duplicates.
            val tagIdMap = HashMap<String, String>()
            for (t in snapshot.tags) {
                val existing = db.tags().byKey(t.key) ?: db.tags().get(t.id)
                if (existing != null) tagIdMap[t.id] = existing.id else { db.tags().insert(t.toEntity()); tagIdMap[t.id] = t.id }
            }
            val notes = snapshot.notes.filter { keep(it.id) }
            val noteIds = notes.map { it.id }.toSet()
            notes.forEach { db.notes().upsert(it.toEntity()) }
            db.tags().link(snapshot.noteTags.filter { it.first in noteIds }.map { app.margin.data.db.NoteTagEntity(it.first, tagIdMap[it.second] ?: it.second) })
            val attachments = snapshot.attachments.filter { it.noteId in noteIds && (mode == RestoreMode.REPLACE || it.id !in existingAttachments) && stagedAttachments.containsKey(it.id) }
            attachments.forEach { db.attachments().upsert(it.copy(storagePath = it.id).toEntity()) }
            snapshot.revisions.filter { it.noteId in noteIds }.forEach { db.revisions().upsert(it.toEntity()) }
            val allNoteIds = db.notes().allIds().toSet()
            // In MERGE mode nothing that already exists is overwritten.
            val existingTasks = if (mode == RestoreMode.MERGE) db.tasks().all().map { it.id }.toSet() else emptySet()
            val existingTemplates = if (mode == RestoreMode.MERGE) db.templates().all().map { it.id }.toSet() else emptySet()
            val existingSearches = if (mode == RestoreMode.MERGE) db.savedSearches().all().map { it.id }.toSet() else emptySet()
            val restoredTasks = snapshot.tasks.filter { it.id !in existingTasks }
            val restoredTaskIds = restoredTasks.map { it.id }.toSet() + existingTasks
            // Parents before subtasks so the self-referencing foreign key is satisfied.
            restoredTasks.sortedBy { if (it.parentTaskId == null) 0 else 1 }.forEach { t ->
                db.tasks().upsert(t.copy(noteId = t.noteId?.takeIf { it in allNoteIds }, parentTaskId = t.parentTaskId?.takeIf { it in restoredTaskIds }).toEntity())
            }
            snapshot.templates.filter { (BuiltInTemplates.byId(it.id) != null || it.id.startsWith("user.")) && it.id !in existingTemplates }.forEach {
                db.templates().upsert(TemplateEntity(it.id, TemplateJson.encode(it), clock.now().toEpochMilli()))
            }
            snapshot.savedSearches.filter { it.id !in existingSearches }.forEach { db.savedSearches().upsert(it.toEntity()) }
            indexer.rebuildAll()
            attachments.map { it.id }
        }
        for (id in newAttachmentIds) {
            stagedAttachments[id]?.inputStream()?.use { files.store(id, it, Long.MAX_VALUE) }
        }
        if (mode == RestoreMode.REPLACE) files.deleteOrphans(db.attachments().all().map { it.storagePath }.toSet())
    }

    private suspend fun wipeInTransaction() {
        db.fts().clear()
        db.tasks().deleteAll()
        db.notes().deleteAll() // cascades to note_tags, links, attachments, revisions
        db.folders().deleteAll()
        db.tags().deleteAll()
        db.templates().deleteAll()
        db.savedSearches().deleteAll()
    }

    override suspend fun wipeAll() {
        db.withTransaction { wipeInTransaction() }
        files.deleteOrphans(emptySet())
    }

    /** Staged import: everything is validated and planned first, then written in one transaction. */
    override suspend fun importNotes(candidates: List<ImportCandidate>, policy: DuplicatePolicy): ImportReport {
        if (candidates.size > ImportLimits.MAX_FILES) {
            return ImportReport(0, candidates.map { ImportIssue.Failed(it.sourceName, FailReason.TOO_LARGE, "too many files in one import") })
        }
        val plan = ImportPlanner.plan(candidates, db.notes().allIds().toSet(), db.notes().allFingerprints().toSet(), policy)
        val now = clock.now()
        db.withTransaction {
            val folders = db.folders().all().map { it.toDomain() }.toMutableList()
            for (c in plan.toImport) {
                val folderId = ensureFolderPath(c.folderPath, folders)
                val note = Note(
                    id = c.originalId?.takeIf { db.notes().get(it) == null } ?: Ids.newId(),
                    title = c.title.take(500), body = c.body, folderId = folderId,
                    createdAt = c.createdAt ?: now, updatedAt = c.updatedAt ?: c.createdAt ?: now,
                    pinned = c.pinned, favorite = c.favorite, importedAt = now,
                )
                val e = note.toEntity()
                db.notes().insert(e)
                if (c.tags.isNotEmpty()) {
                    val tags = Tags.parseList(c.tags.joinToString(",")).map { name ->
                        db.tags().byKey(Tags.key(name)) ?: Tag(Ids.newId(), Tags.clean(name), Tags.key(name)).toEntity().also { db.tags().insert(it) }
                    }
                    db.tags().link(tags.map { app.margin.data.db.NoteTagEntity(note.id, it.id) })
                }
                indexer.reindex(e)
            }
            plan.toImport.size
        }
        return ImportReport(plan.toImport.size, plan.issues)
    }

    /** Finds or creates nested folders by name; depth beyond the limit collapses into the deepest allowed level. */
    private suspend fun ensureFolderPath(path: List<String>, folders: MutableList<Folder>): String? {
        var parent: String? = null
        for (raw in path.take(Folders.MAX_DEPTH)) {
            val name = raw.trim().take(Folders.MAX_NAME)
            if (name.isEmpty() || Folders.validateName(name) != null) continue
            val existing = folders.firstOrNull { it.parentId == parent && it.name.equals(name, ignoreCase = true) }
            parent = existing?.id ?: Folder(Ids.newId(), name, parent, createdAt = clock.now()).also {
                db.folders().insert(it.toEntity()); folders += it
            }.id
        }
        return parent
    }
}
