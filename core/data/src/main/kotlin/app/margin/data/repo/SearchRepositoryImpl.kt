package app.margin.data.repo

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.margin.data.db.MarginDatabase
import app.margin.data.db.SEARCH_COLUMNS
import app.margin.data.db.SEP
import app.margin.data.db.SUMMARY_COLUMNS
import app.margin.data.db.SearchRow
import app.margin.data.db.toDomain
import app.margin.data.db.toEntity
import app.margin.domain.ai.AiPolicy
import app.margin.domain.ai.AiSettings
import app.margin.domain.model.Clock
import app.margin.domain.model.Folder
import app.margin.domain.model.Ids
import app.margin.domain.model.SavedSearch
import app.margin.domain.organization.Folders
import app.margin.domain.repository.SearchRepository
import app.margin.domain.repository.SearchResults
import app.margin.domain.repository.SearchScope
import app.margin.domain.search.FtsQueryBuilder
import app.margin.domain.search.QueryParser
import app.margin.domain.search.SearchDocument
import app.margin.domain.search.SearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Two-stage search: the FTS index narrows candidates (when the query allows it), then the
 * domain SearchEngine applies exact semantics, ranking, snippets and highlighting over the
 * canonical rows. If the index is damaged, search falls back to scanning — slower but correct.
 */
class SearchRepositoryImpl(private val db: MarginDatabase, private val clock: Clock) : SearchRepository {
    private val engine = SearchEngine()
    private val indexer = Indexer(db)

    override suspend fun search(query: String, scope: SearchScope): SearchResults {
        val start = System.nanoTime()
        val parsed = QueryParser.parse(query)
        if (parsed.isEmpty) return SearchResults(emptyList(), emptyMap(), parsed.warnings, false, 0)
        val folders = db.folders().all().map { it.toDomain() }
        val byId = folders.associateBy { it.id }
        val scopeIds = scope.folderId?.let { setOf(it) + Folders.descendants(it, folders) }
        // Trashed notes are not in the FTS index, so queries that ask for them scan instead.
        val match = if (parsed.mentionsTrashOrArchive) null else FtsQueryBuilder.candidates(parsed)
        val candidates: List<String>? = match?.let {
            try { db.fts().match(it, 5_000) } catch (e: Exception) { null } // damaged index → correct but slower scan
        }
        val rows = loadRows(candidates, scopeIds)
        val docs = rows.asSequence().map { it.toDocument(byId) }
        val hits = engine.search(parsed, docs, clock.now(), scope.limit)
        val summaries = if (hits.isEmpty()) emptyMap() else {
            val ids = hits.map { it.id }
            val sql = "SELECT $SUMMARY_COLUMNS FROM notes n WHERE n.id IN (${ids.joinToString(",") { "?" }})"
            db.notes().searchSummaries(SimpleSQLiteQuery(sql, ids.toTypedArray())).associate { it.id to it.toDomain() }
        }
        return SearchResults(hits, summaries, parsed.warnings, hits.isNotEmpty() && hits.all { it.approximate }, (System.nanoTime() - start) / 1_000_000)
    }

    private suspend fun loadRows(candidateIds: List<String>?, scopeIds: Set<String>?): List<SearchRow> {
        val where = ArrayList<String>()
        val args = ArrayList<Any>()
        if (candidateIds != null) {
            if (candidateIds.isEmpty()) return emptyList()
            // SQLite has a bound-parameter limit (999 on older Android); chunk large candidate sets.
            return candidateIds.chunked(900).flatMap { chunk ->
                val w = mutableListOf("n.id IN (${chunk.joinToString(",") { "?" }})")
                val a = ArrayList<Any>(chunk)
                scopeIds?.let { s -> w += "n.folder_id IN (${s.joinToString(",") { "?" }})"; a += s }
                db.notes().searchRows(SimpleSQLiteQuery("SELECT $SEARCH_COLUMNS FROM notes n WHERE ${w.joinToString(" AND ")}", a.toTypedArray()))
            }
        }
        scopeIds?.let { s -> where += "n.folder_id IN (${s.joinToString(",") { "?" }})"; args += s }
        val sql = "SELECT $SEARCH_COLUMNS FROM notes n" + if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")
        return db.notes().searchRows(SimpleSQLiteQuery(sql, args.toTypedArray()))
    }

    /** AI-eligible documents for "ask my notes": trashed / excluded never; archived only if allowed. */
    suspend fun aiDocuments(settings: AiSettings): List<SearchDocument> {
        val byId = db.folders().all().map { it.toDomain() }.associateBy { it.id }
        return db.notes().searchRows(SimpleSQLiteQuery("SELECT $SEARCH_COLUMNS FROM notes n"))
            .filter { AiPolicy.eligible(AiPolicy.NoteVisibility(it.id, it.state == "TRASHED", it.state == "ARCHIVED", it.excludedFromAi), settings) }
            .map { it.toDocument(byId) }
    }

    override fun observeSavedSearches(): Flow<List<SavedSearch>> = db.savedSearches().observe().map { l -> l.map { it.toDomain() } }

    override suspend fun saveSearch(name: String, query: String) =
        db.savedSearches().upsert(SavedSearch(Ids.newId(), name.trim().ifEmpty { query.take(40) }, query, clock.now()).toEntity())

    override suspend fun deleteSavedSearch(id: String) = db.savedSearches().delete(id)

    override suspend fun rebuildIndex(): Int = db.withTransaction { indexer.rebuildAll() }
}

internal fun SearchRow.toDocument(folders: Map<String, Folder>) = SearchDocument(
    id = id, title = title, plainBody = plainText,
    tagNames = tagNames?.split(SEP)?.filter { it.isNotEmpty() } ?: emptyList(),
    folderPath = Folders.path(folderId, folders).map { it.name },
    attachmentText = attachmentText.orEmpty(),
    createdAt = Instant.ofEpochMilli(createdAt), updatedAt = Instant.ofEpochMilli(updatedAt),
    pinned = pinned, favorite = favorite, archived = state == "ARCHIVED", trashed = state == "TRASHED",
    attachmentCount = attachmentCount, openTaskCount = openTaskCount, doneTaskCount = doneTaskCount, linkCount = linkCount,
)
