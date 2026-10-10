package app.margin.domain.repository

import app.margin.domain.io.DuplicatePolicy
import app.margin.domain.io.ImportCandidate
import app.margin.domain.io.ImportReport
import app.margin.domain.io.LibrarySnapshot
import app.margin.domain.model.Attachment
import app.margin.domain.model.Folder
import app.margin.domain.model.Note
import app.margin.domain.model.NoteRevision
import app.margin.domain.model.NoteSummary
import app.margin.domain.model.SavedSearch
import app.margin.domain.model.Tag
import app.margin.domain.model.TaskItem
import app.margin.domain.organization.FolderDeletePolicy
import app.margin.domain.organization.NoteFilter
import app.margin.domain.organization.SortOrder
import app.margin.domain.search.SearchHit
import app.margin.domain.template.TemplateDefinition
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts used by the presentation layer. Implementations (core:data) run every
 * multi-step mutation in a single database transaction and keep the search index in step.
 */
interface NoteRepository {
    fun observeNotes(filter: NoteFilter, sort: SortOrder, limit: Int = 500): Flow<List<NoteSummary>>
    fun observeNote(id: String): Flow<Note?>
    suspend fun getNote(id: String): Note?
    suspend fun create(title: String = "", body: String = "", folderId: String? = null, templateId: String? = null,
                       tags: List<String> = emptyList(), excludedFromAi: Boolean = false): Note
    /** Persists content; snapshots a revision per policy; updates metrics, links and search index. Throws PersistenceException. */
    suspend fun saveContent(id: String, title: String, body: String, cursor: Int)
    suspend fun setPinned(ids: Collection<String>, pinned: Boolean)
    suspend fun setFavorite(ids: Collection<String>, favorite: Boolean)
    suspend fun archive(ids: Collection<String>)
    suspend fun unarchive(ids: Collection<String>)
    suspend fun moveToTrash(ids: Collection<String>)
    /** Restores to the original folder if it still exists, otherwise to the Inbox. */
    suspend fun restoreFromTrash(ids: Collection<String>)
    suspend fun deletePermanently(ids: Collection<String>)
    suspend fun emptyTrash(): Int
    suspend fun purgeTrashOlderThanDays(days: Int): Int
    suspend fun move(ids: Collection<String>, folderId: String?)
    suspend fun duplicate(id: String): Note
    suspend fun markOpened(id: String)
    suspend fun markReviewed(id: String)
    suspend fun setExcludedFromAi(id: String, excluded: Boolean)
    fun observeTagsForNote(id: String): Flow<List<Tag>>
    suspend fun setTags(noteId: String, tagNames: List<String>)
    suspend fun addTags(noteIds: Collection<String>, tagNames: List<String>)
    suspend fun removeTag(noteIds: Collection<String>, tagId: String)
    fun observeRevisions(noteId: String): Flow<List<NoteRevision>>
    suspend fun restoreRevision(revisionId: String)
    suspend fun setRevisionPinned(revisionId: String, pinned: Boolean)
    fun observeBacklinks(noteId: String): Flow<List<NoteSummary>>
    fun observeOutgoingLinks(noteId: String): Flow<List<NoteSummary>>
    suspend fun titleIndex(): Map<String, String>
    fun observeCounts(): Flow<LibraryCounts>
}

data class LibraryCounts(val active: Int, val archived: Int, val trashed: Int, val pinned: Int, val favorites: Int)

interface FolderRepository {
    fun observeFolders(includeArchived: Boolean = false): Flow<List<Folder>>
    suspend fun all(): List<Folder>
    suspend fun create(name: String, parentId: String?): Folder
    suspend fun rename(id: String, name: String)
    suspend fun move(id: String, newParentId: String?)
    suspend fun setPinned(id: String, pinned: Boolean)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun setAppearance(id: String, color: Long?, icon: String?)
    suspend fun reorder(orderedIds: List<String>)
    suspend fun delete(id: String, policy: FolderDeletePolicy)
    fun observeNoteCounts(): Flow<Map<String?, Int>>
}

data class TagWithCount(val tag: Tag, val count: Int)

interface TagRepository {
    fun observeTags(): Flow<List<TagWithCount>>
    suspend fun rename(id: String, newName: String): RenameResult
    suspend fun setColor(id: String, color: Long?)
    suspend fun delete(id: String)
    suspend fun deleteUnused(): Int
    enum class RenameResult { OK, INVALID, MERGED }
}

data class SearchScope(val folderId: String? = null, val limit: Int = 200)

interface SearchRepository {
    suspend fun search(query: String, scope: SearchScope = SearchScope()): SearchResults
    fun observeSavedSearches(): Flow<List<SavedSearch>>
    suspend fun saveSearch(name: String, query: String)
    suspend fun deleteSavedSearch(id: String)
    suspend fun rebuildIndex(): Int
}

data class SearchResults(val hits: List<SearchHit>, val notes: Map<String, NoteSummary>, val warnings: List<String>, val approximate: Boolean, val tookMs: Long)

interface TaskRepository {
    fun observeOpenTasks(): Flow<List<TaskItem>>
    fun observeCompletedTasks(limit: Int = 200): Flow<List<TaskItem>>
    fun observeTasksForNote(noteId: String): Flow<List<TaskItem>>
    suspend fun get(id: String): TaskItem?
    suspend fun upsert(task: TaskItem)
    /** Marks complete; for recurring tasks also creates the next occurrence. Returns the new task, if any. */
    suspend fun complete(id: String): TaskItem?
    suspend fun reopen(id: String)
    suspend fun delete(id: String)
    suspend fun snooze(id: String, minutes: Long)
    suspend fun tasksWithReminders(): List<TaskItem>
}

interface TemplateRepository {
    fun observeTemplates(): Flow<List<TemplateDefinition>>
    suspend fun get(id: String): TemplateDefinition?
    suspend fun saveUserTemplate(t: TemplateDefinition)
    suspend fun deleteUserTemplate(id: String)
    /** Removes a user override so the built-in definition applies again. */
    suspend fun resetBuiltIn(id: String)
    fun observeRecentlyUsed(): Flow<List<String>>
    suspend fun markUsed(id: String)
}

interface AttachmentRepository {
    fun observeForNote(noteId: String): Flow<List<Attachment>>
    fun observeAll(): Flow<List<Attachment>>
    suspend fun add(noteId: String, source: AttachmentSource): Result<Attachment>
    /** Replaces an attachment's content (e.g. an edited drawing), keeping its id; the old file is removed only after the new one is saved. */
    suspend fun replace(id: String, source: AttachmentSource, extractedText: String? = null): Result<Attachment>
    suspend fun get(id: String): Attachment?
    suspend fun remove(id: String)
    suspend fun setExtractedText(id: String, text: String?)
    suspend fun storageBytes(): Long
    /** IDs of attachments whose file is missing or whose checksum no longer matches. */
    suspend fun findBroken(): List<String>
}

/** Where attachment bytes come from; the data layer streams and never loads whole files into memory. */
interface AttachmentSource {
    val displayName: String
    val mimeType: String
    val sizeHint: Long?
    fun open(): java.io.InputStream
}

interface LibraryRepository {
    suspend fun snapshot(includeRevisions: Boolean = true): LibrarySnapshot
    /** Applies a verified snapshot. REPLACE wipes the library first; MERGE keeps existing items with the same ids. */
    suspend fun restore(snapshot: LibrarySnapshot, mode: RestoreMode, stagedAttachments: Map<String, java.io.File>)
    suspend fun importNotes(candidates: List<ImportCandidate>, policy: DuplicatePolicy): ImportReport
    suspend fun wipeAll()
}

enum class RestoreMode { REPLACE, MERGE }
