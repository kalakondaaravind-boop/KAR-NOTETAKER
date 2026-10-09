package app.margin.domain.organization

import app.margin.domain.model.NoteState
import app.margin.domain.text.Block
import app.margin.domain.text.MarkdownParser
import app.margin.domain.text.TextStats
import java.time.Duration
import java.time.Instant

/** Derived per-note metadata, recomputed on every save and stored next to the note. */
data class NoteMetrics(
    val wordCount: Int,
    val openTaskCount: Int,
    val doneTaskCount: Int,
    val linkCount: Int,
    val plainText: String,
) {
    val hasTasks get() = openTaskCount + doneTaskCount > 0

    companion object {
        fun compute(body: String): NoteMetrics {
            val blocks = MarkdownParser.parse(body)
            val tasks = blocks.filterIsInstance<Block.ListItem>().filter { it.kind == Block.ListKind.TASK }
            return NoteMetrics(
                wordCount = TextStats.of(body).words,
                openTaskCount = tasks.count { !it.checked },
                doneTaskCount = tasks.count { it.checked },
                linkCount = MarkdownParser.webLinks(body).size + MarkdownParser.linkTargets(body).size,
                plainText = MarkdownParser.toPlainText(body),
            )
        }
    }
}

/** Everything a list/collection filter can look at for a note (no body needed). */
data class NoteFacts(
    val id: String,
    val state: NoteState,
    val folderId: String?,
    val tagKeys: Set<String>,
    val pinned: Boolean,
    val favorite: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attachmentCount: Int,
    val wordCount: Int,
    val openTaskCount: Int,
    val doneTaskCount: Int,
    val linkCount: Int,
    val importedAt: Instant?,
    val reviewedAt: Instant?,
    val isEmpty: Boolean,
)

/**
 * Declarative filter. Smart collections are just named filters, so their membership is always
 * derived from current data (never a separately maintained list). The same filter is evaluated
 * in Kotlin ([matches]) and translated to SQL ([NoteFilterSql]); tests keep both in agreement.
 */
data class NoteFilter(
    val states: Set<NoteState> = setOf(NoteState.ACTIVE),
    val folderIds: Set<String>? = null,
    val inboxOnly: Boolean = false,
    /** All of these tags (hierarchical: a parent key matches child tags). */
    val tagKeys: Set<String> = emptySet(),
    val untagged: Boolean = false,
    val pinned: Boolean? = null,
    val favorite: Boolean? = null,
    val hasAttachments: Boolean? = null,
    val hasTasks: Boolean? = null,
    val hasOpenTasks: Boolean? = null,
    val hasLinks: Boolean? = null,
    val minWords: Int? = null,
    val createdAfter: Instant? = null,
    val createdBefore: Instant? = null,
    val updatedAfter: Instant? = null,
    val updatedBefore: Instant? = null,
    val importedOnly: Boolean = false,
    val notReviewedFor: Duration? = null,
    val emptyOnly: Boolean = false,
) {
    fun matches(f: NoteFacts, now: Instant): Boolean {
        if (f.state !in states) return false
        if (folderIds != null && f.folderId !in folderIds) return false
        if (inboxOnly && f.folderId != null) return false
        if (tagKeys.any { k -> f.tagKeys.none { Tags.matchesHierarchy(k, it) } }) return false
        if (untagged && f.tagKeys.isNotEmpty()) return false
        if (pinned != null && f.pinned != pinned) return false
        if (favorite != null && f.favorite != favorite) return false
        if (hasAttachments != null && (f.attachmentCount > 0) != hasAttachments) return false
        if (hasTasks != null && (f.openTaskCount + f.doneTaskCount > 0) != hasTasks) return false
        if (hasOpenTasks != null && (f.openTaskCount > 0) != hasOpenTasks) return false
        if (hasLinks != null && (f.linkCount > 0) != hasLinks) return false
        if (minWords != null && f.wordCount < minWords) return false
        if (createdAfter != null && f.createdAt < createdAfter) return false
        if (createdBefore != null && f.createdAt >= createdBefore) return false
        if (updatedAfter != null && f.updatedAt < updatedAfter) return false
        if (updatedBefore != null && f.updatedAt >= updatedBefore) return false
        if (importedOnly && f.importedAt == null) return false
        if (notReviewedFor != null) {
            val ref = f.reviewedAt ?: f.createdAt
            if (Duration.between(ref, now) < notReviewedFor) return false
        }
        if (emptyOnly && !f.isEmpty) return false
        return true
    }
}

enum class SortField { UPDATED, CREATED, TITLE, RELEVANCE }
data class SortOrder(val field: SortField = SortField.UPDATED, val descending: Boolean = true)

/** Built-in smart collections. */
enum class SmartCollection {
    RECENTLY_EDITED, RECENTLY_CREATED, FAVORITES, PINNED, UNTAGGED, ARCHIVED, DRAFTS,
    WITH_ATTACHMENTS, WITH_LINKS, WITH_TASKS, RECENTLY_IMPORTED, LONG_FORM, NOT_REVIEWED;

    fun filter(now: Instant): NoteFilter = when (this) {
        RECENTLY_EDITED -> NoteFilter(updatedAfter = now.minus(Duration.ofDays(7)))
        RECENTLY_CREATED -> NoteFilter(createdAfter = now.minus(Duration.ofDays(7)))
        FAVORITES -> NoteFilter(favorite = true)
        PINNED -> NoteFilter(pinned = true)
        UNTAGGED -> NoteFilter(untagged = true)
        ARCHIVED -> NoteFilter(states = setOf(NoteState.ARCHIVED))
        // "Drafts": short notes in the Inbox that were never organised.
        DRAFTS -> NoteFilter(inboxOnly = true, untagged = true, updatedAfter = now.minus(Duration.ofDays(30)))
        WITH_ATTACHMENTS -> NoteFilter(hasAttachments = true)
        WITH_LINKS -> NoteFilter(hasLinks = true)
        WITH_TASKS -> NoteFilter(hasOpenTasks = true)
        RECENTLY_IMPORTED -> NoteFilter(importedOnly = true, createdAfter = now.minus(Duration.ofDays(30)))
        LONG_FORM -> NoteFilter(minWords = 1_000)
        NOT_REVIEWED -> NoteFilter(notReviewedFor = Duration.ofDays(90))
    }

    val defaultSort: SortOrder get() = when (this) {
        RECENTLY_CREATED, RECENTLY_IMPORTED -> SortOrder(SortField.CREATED)
        NOT_REVIEWED -> SortOrder(SortField.UPDATED, descending = false)
        else -> SortOrder(SortField.UPDATED)
    }
}

/**
 * Translates a [NoteFilter] into a SQL WHERE clause against the `notes` table schema
 * (see core/data NoteEntity). Values are bound as parameters; nothing is concatenated.
 */
object NoteFilterSql {
    data class Clause(val where: String, val args: List<Any>)

    fun build(filter: NoteFilter, now: Instant): Clause {
        val parts = ArrayList<String>()
        val args = ArrayList<Any>()
        if (filter.states.isNotEmpty()) {
            parts += "n.state IN (${filter.states.joinToString(",") { "?" }})"
            args.addAll(filter.states.map { it.name })
        }
        filter.folderIds?.let { ids ->
            if (ids.isEmpty()) parts += "0" else { parts += "n.folder_id IN (${ids.joinToString(",") { "?" }})"; args.addAll(ids) }
        }
        if (filter.inboxOnly) parts += "n.folder_id IS NULL"
        filter.tagKeys.forEach { k ->
            parts += "EXISTS (SELECT 1 FROM note_tags nt JOIN tags t ON t.id = nt.tag_id WHERE nt.note_id = n.id AND (t.key = ? OR t.key LIKE ? ESCAPE '\\'))"
            args += k; args += likeEscape(k) + "/%"
        }
        if (filter.untagged) parts += "NOT EXISTS (SELECT 1 FROM note_tags nt WHERE nt.note_id = n.id)"
        filter.pinned?.let { parts += "n.pinned = ?"; args += if (it) 1 else 0 }
        filter.favorite?.let { parts += "n.favorite = ?"; args += if (it) 1 else 0 }
        filter.hasAttachments?.let {
            parts += (if (it) "" else "NOT ") + "EXISTS (SELECT 1 FROM attachments a WHERE a.note_id = n.id)"
        }
        filter.hasTasks?.let { parts += if (it) "(n.open_task_count + n.done_task_count) > 0" else "(n.open_task_count + n.done_task_count) = 0" }
        filter.hasOpenTasks?.let { parts += if (it) "n.open_task_count > 0" else "n.open_task_count = 0" }
        filter.hasLinks?.let { parts += if (it) "n.link_count > 0" else "n.link_count = 0" }
        filter.minWords?.let { parts += "n.word_count >= ?"; args += it }
        filter.createdAfter?.let { parts += "n.created_at >= ?"; args += it.toEpochMilli() }
        filter.createdBefore?.let { parts += "n.created_at < ?"; args += it.toEpochMilli() }
        filter.updatedAfter?.let { parts += "n.updated_at >= ?"; args += it.toEpochMilli() }
        filter.updatedBefore?.let { parts += "n.updated_at < ?"; args += it.toEpochMilli() }
        if (filter.importedOnly) parts += "n.imported_at IS NOT NULL"
        filter.notReviewedFor?.let { parts += "COALESCE(n.reviewed_at, n.created_at) <= ?"; args += now.minus(it).toEpochMilli() }
        if (filter.emptyOnly) parts += "(TRIM(n.title) = '' AND TRIM(n.body) = '')"
        return Clause(if (parts.isEmpty()) "1" else parts.joinToString(" AND "), args)
    }

    fun orderBy(sort: SortOrder): String {
        val dir = if (sort.descending) "DESC" else "ASC"
        return when (sort.field) {
            SortField.UPDATED, SortField.RELEVANCE -> "n.pinned DESC, n.updated_at $dir"
            SortField.CREATED -> "n.pinned DESC, n.created_at $dir"
            SortField.TITLE -> "n.pinned DESC, n.sort_title COLLATE NOCASE $dir, n.updated_at DESC"
        }
    }

    fun likeEscape(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
