package app.margin.domain.organization

import app.margin.domain.model.NoteState
import app.margin.domain.search.FtsQueryBuilder
import app.margin.domain.search.QueryParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Emits SQL produced by [NoteFilterSql] / [FtsQueryBuilder] together with sample rows and the
 * memberships expected from the Kotlin semantics ([NoteFilter.matches]). tools/verify-sql.py runs
 * them against a real SQLite database with the app's schema and checks both agree.
 * Writes only when MARGIN_SQL_OUT is set.
 */
class SqlFixturesTest {
    private val now = Instant.parse("2025-06-01T00:00:00Z")

    private fun facts(): List<NoteFacts> {
        val out = ArrayList<NoteFacts>()
        val tagSets = listOf(emptySet(), setOf("work"), setOf("work/clients"), setOf("home", "work"), setOf("workshop"))
        var i = 0
        for (state in NoteState.entries) for (tags in tagSets) for (pinned in listOf(false, true)) {
            i++
            out += NoteFacts(
                id = "n$i", state = state, folderId = if (i % 3 == 0) null else "f${i % 2}", tagKeys = tags,
                pinned = pinned, favorite = i % 4 == 0,
                createdAt = now.minus(Duration.ofDays((i * 7L) % 120)), updatedAt = now.minus(Duration.ofDays((i * 3L) % 40)),
                attachmentCount = i % 5 / 4, wordCount = (i * 97) % 2000, openTaskCount = i % 3, doneTaskCount = i % 2,
                linkCount = i % 4 / 3, importedAt = if (i % 6 == 0) now.minus(Duration.ofDays(3)) else null,
                reviewedAt = if (i % 5 == 0) now.minus(Duration.ofDays(10)) else null, isEmpty = i == 7,
            )
        }
        return out
    }

    @Test fun emitFixtures() {
        val path = System.getenv("MARGIN_SQL_OUT") ?: return
        val data = facts()
        val filters = SmartCollection.entries.associate { it.name to it.filter(now) } + mapOf(
            "tag_work" to NoteFilter(tagKeys = setOf("work")),
            "tag_work_home" to NoteFilter(tagKeys = setOf("work", "home")),
            "folder_f1_open" to NoteFilter(folderIds = setOf("f1"), hasOpenTasks = true),
            "inbox_all_states" to NoteFilter(states = NoteState.entries.toSet(), inboxOnly = true),
            "no_folders" to NoteFilter(folderIds = emptySet()),
            "no_attachments_short" to NoteFilter(hasAttachments = false, hasTasks = false, hasLinks = false),
            "empty" to NoteFilter(emptyOnly = true, states = NoteState.entries.toSet()),
            "date_window" to NoteFilter(createdAfter = now.minus(Duration.ofDays(60)), createdBefore = now.minus(Duration.ofDays(10)), updatedBefore = now.minus(Duration.ofDays(5))),
        )
        fun arg(a: Any): JsonElement = when (a) { is Number -> JsonPrimitive(a); is Boolean -> JsonPrimitive(a); else -> JsonPrimitive(a.toString()) }
        val json = buildJsonObject {
            put("now", now.toEpochMilli())
            put("notes", buildJsonArray {
                data.forEach { f -> add(buildJsonObject {
                    put("id", f.id); put("state", f.state.name); f.folderId?.let { put("folder_id", it) }
                    put("tags", JsonArray(f.tagKeys.map { JsonPrimitive(it) })); put("pinned", f.pinned); put("favorite", f.favorite)
                    put("created_at", f.createdAt.toEpochMilli()); put("updated_at", f.updatedAt.toEpochMilli())
                    put("attachments", f.attachmentCount); put("word_count", f.wordCount); put("open_task_count", f.openTaskCount)
                    put("done_task_count", f.doneTaskCount); put("link_count", f.linkCount)
                    f.importedAt?.let { put("imported_at", it.toEpochMilli()) }; f.reviewedAt?.let { put("reviewed_at", it.toEpochMilli()) }
                    put("is_empty", f.isEmpty)
                }) }
            })
            put("filters", buildJsonArray {
                filters.forEach { (name, flt) ->
                    val c = NoteFilterSql.build(flt, now)
                    add(buildJsonObject {
                        put("name", name); put("where", c.where); put("args", JsonArray(c.args.map(::arg)))
                        put("expected", JsonArray(data.filter { flt.matches(it, now) }.map { JsonPrimitive(it.id) }))
                    })
                }
            })
            put("orders", JsonArray(SortField.entries.flatMap { f -> listOf(true, false).map { JsonPrimitive(NoteFilterSql.orderBy(SortOrder(f, it))) } }))
            put("fts", JsonArray(listOf("budget", "budg \"team meeting\"", "Zürich café", "a OR b -c", "title:plan notes", "\"unterminated", "it's", "x*y").mapNotNull { q ->
                FtsQueryBuilder.candidates(QueryParser.parse(q))?.let { JsonPrimitive(it) }
            }))
        }
        File(path).writeText(json.toString())
    }
}
