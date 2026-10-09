package app.margin.data.repo

import androidx.room.withTransaction
import app.margin.data.db.MarginDatabase
import app.margin.data.db.toDomain
import app.margin.data.db.toEntity
import app.margin.domain.model.Clock
import app.margin.domain.model.Folder
import app.margin.domain.model.Ids
import app.margin.domain.model.NoteState
import app.margin.domain.organization.FolderDeletePolicy
import app.margin.domain.organization.Folders
import app.margin.domain.organization.Tags
import app.margin.domain.repository.FolderRepository
import app.margin.domain.repository.TagRepository
import app.margin.domain.repository.TagWithCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FolderValidationException(val reason: String) : Exception(reason)

class FolderRepositoryImpl(private val db: MarginDatabase, private val clock: Clock) : FolderRepository {
    private val indexer = Indexer(db)

    override fun observeFolders(includeArchived: Boolean): Flow<List<Folder>> =
        db.folders().observe(includeArchived).map { l -> l.map { it.toDomain() } }

    override suspend fun all(): List<Folder> = db.folders().all().map { it.toDomain() }

    override suspend fun create(name: String, parentId: String?): Folder = db.withTransaction {
        Folders.validateName(name)?.let { throw FolderValidationException(it.name) }
        val all = all()
        if (parentId != null && all.none { it.id == parentId }) throw FolderValidationException("PARENT_MISSING")
        if (!Folders.canCreateChild(parentId, all)) throw FolderValidationException("TOO_DEEP")
        val f = Folder(Ids.newId(), Folders.uniqueSiblingName(name, parentId, all), parentId,
            sortOrder = (all.filter { it.parentId == parentId }.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = clock.now())
        db.folders().insert(f.toEntity())
        f
    }

    override suspend fun rename(id: String, name: String) = db.withTransaction {
        Folders.validateName(name)?.let { throw FolderValidationException(it.name) }
        val all = all()
        val f = all.firstOrNull { it.id == id } ?: return@withTransaction
        db.folders().rename(id, Folders.uniqueSiblingName(name, f.parentId, all, excludeId = id))
        reindexFolderNotes(setOf(id) + Folders.descendants(id, all))
    }

    override suspend fun move(id: String, newParentId: String?) = db.withTransaction {
        val all = all()
        Folders.validateMove(id, newParentId, all)?.let { throw FolderValidationException(it.name) }
        val f = all.first { it.id == id }
        db.folders().setParent(id, newParentId)
        db.folders().rename(id, Folders.uniqueSiblingName(f.name, newParentId, all, excludeId = id))
        reindexFolderNotes(setOf(id) + Folders.descendants(id, all))
    }

    private suspend fun reindexFolderNotes(folderIds: Set<String>) {
        db.notes().idsInFolders(folderIds).forEach { indexer.reindex(it) }
    }

    override suspend fun setPinned(id: String, pinned: Boolean) = db.folders().setPinned(id, pinned)
    override suspend fun setArchived(id: String, archived: Boolean) = db.folders().setArchived(id, archived)
    override suspend fun setAppearance(id: String, color: Long?, icon: String?) = db.folders().setAppearance(id, color, icon)

    override suspend fun reorder(orderedIds: List<String>) = db.withTransaction {
        orderedIds.forEachIndexed { i, id -> db.folders().setOrder(id, i) }
    }

    /** Never deletes notes outright: they move to the Inbox or to Trash (restorable), per [policy]. */
    override suspend fun delete(id: String, policy: FolderDeletePolicy) = db.withTransaction {
        val plan = Folders.deletePlan(id, all(), policy)
        val noteIds = db.notes().idsInFolders(plan.folderIds)
        if (noteIds.isNotEmpty()) {
            db.notes().move(noteIds, null)
            if (policy == FolderDeletePolicy.TRASH_NOTES) db.notes().setState(noteIds, NoteState.TRASHED.name, clock.now().toEpochMilli())
        }
        db.folders().delete(plan.folderIds)
        noteIds.forEach { indexer.reindex(it) }
    }

    override fun observeNoteCounts(): Flow<Map<String?, Int>> = db.notes().observeFolderCounts().map { rows -> rows.associate { it.folderId to it.count } }
}

class TagRepositoryImpl(private val db: MarginDatabase) : TagRepository {
    private val indexer = Indexer(db)

    override fun observeTags(): Flow<List<TagWithCount>> = db.tags().observeWithCounts().map { rows ->
        rows.map { TagWithCount(app.margin.domain.model.Tag(it.id, it.name, it.key, it.color), it.count) }
    }

    /**
     * Renaming to a name whose key already belongs to another tag merges the two (notes keep a
     * single tag); otherwise the tag is renamed in place.
     */
    override suspend fun rename(id: String, newName: String): TagRepository.RenameResult = db.withTransaction {
        if (Tags.validate(newName) != null) return@withTransaction TagRepository.RenameResult.INVALID
        val key = Tags.key(newName)
        val existing = db.tags().byKey(key)
        val affected = db.tags().notesWithTag(id)
        val result = if (existing != null && existing.id != id) {
            db.tags().reassign(from = id, to = existing.id)
            db.tags().delete(id) // remaining duplicate links cascade away
            TagRepository.RenameResult.MERGED
        } else {
            db.tags().rename(id, Tags.clean(newName), key)
            TagRepository.RenameResult.OK
        }
        affected.forEach { indexer.reindex(it) }
        result
    }

    override suspend fun setColor(id: String, color: Long?) = db.tags().setColor(id, color)

    override suspend fun delete(id: String) = db.withTransaction {
        val affected = db.tags().notesWithTag(id)
        db.tags().delete(id)
        affected.forEach { indexer.reindex(it) }
    }

    override suspend fun deleteUnused(): Int = db.tags().deleteUnused()
}
