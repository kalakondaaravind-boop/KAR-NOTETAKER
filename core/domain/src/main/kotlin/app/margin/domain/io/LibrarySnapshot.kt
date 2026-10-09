package app.margin.domain.io

import app.margin.domain.model.Attachment
import app.margin.domain.model.Folder
import app.margin.domain.model.Note
import app.margin.domain.model.NoteRevision
import app.margin.domain.model.NoteState
import app.margin.domain.model.SavedSearch
import app.margin.domain.model.Tag
import app.margin.domain.model.TaskItem
import app.margin.domain.model.TaskPriority
import app.margin.domain.template.TemplateDefinition
import app.margin.domain.template.TemplateJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Everything that makes up a user's library. Indexes, caches and embeddings are excluded (rebuilt). */
data class LibrarySnapshot(
    val notes: List<Note> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val tags: List<Tag> = emptyList(),
    /** noteId to tagId */
    val noteTags: List<Pair<String, String>> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val revisions: List<NoteRevision> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val templates: List<TemplateDefinition> = emptyList(),
    val savedSearches: List<SavedSearch> = emptyList(),
) {
    val isEmpty get() = notes.isEmpty() && folders.isEmpty() && tags.isEmpty() && tasks.isEmpty() && templates.isEmpty()

    /** Referential problems that would make a restore inconsistent. Empty = consistent. */
    fun integrityProblems(): List<String> {
        val p = ArrayList<String>()
        val noteIds = notes.map { it.id }.toSet()
        val folderIds = folders.map { it.id }.toSet()
        val tagIds = tags.map { it.id }.toSet()
        fun dupes(name: String, ids: List<String>) { if (ids.size != ids.toSet().size) p += "duplicate $name ids" }
        dupes("note", notes.map { it.id }); dupes("folder", folders.map { it.id }); dupes("tag", tags.map { it.id })
        dupes("attachment", attachments.map { it.id }); dupes("task", tasks.map { it.id }); dupes("revision", revisions.map { it.id })
        notes.filter { it.folderId != null && it.folderId !in folderIds }.forEach { p += "note ${it.id} references missing folder" }
        folders.filter { it.parentId != null && it.parentId !in folderIds }.forEach { p += "folder ${it.id} references missing parent" }
        noteTags.filter { it.first !in noteIds || it.second !in tagIds }.forEach { p += "note-tag ${it.first}/${it.second} dangling" }
        attachments.filter { it.noteId !in noteIds }.forEach { p += "attachment ${it.id} references missing note" }
        revisions.filter { it.noteId !in noteIds }.forEach { p += "revision ${it.id} references missing note" }
        tasks.filter { it.noteId != null && it.noteId !in noteIds }.forEach { p += "task ${it.id} references missing note" }
        if (tags.map { it.key }.toSet().size != tags.size) p += "duplicate tag keys"
        attachments.filter { it.storagePath.contains("..") || it.storagePath.startsWith("/") }.forEach { p += "attachment ${it.id} has unsafe path" }
        return p
    }
}

/**
 * JSON codec for [LibrarySnapshot] ("margin.library" v1, see docs/DATA_FORMATS.md).
 * Hand-written over the JsonElement tree so unknown fields are ignored (forward compatible)
 * and malformed input becomes a typed error instead of a crash.
 */
object LibraryJson {
    const val FORMAT = "margin.library"
    const val VERSION = 1
    private val pretty = Json { prettyPrint = true }

    class FormatException(message: String) : Exception(message)

    fun encode(s: LibrarySnapshot, exportedAt: Instant, appVersion: String): String =
        pretty.encodeToString(JsonElement.serializer(), buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", exportedAt.toString())
            put("appVersion", appVersion)
            put("notes", buildJsonArray { s.notes.forEach { add(note(it)) } })
            put("folders", buildJsonArray { s.folders.forEach { add(folder(it)) } })
            put("tags", buildJsonArray { s.tags.forEach { t -> add(buildJsonObject { put("id", t.id); put("name", t.name); put("key", t.key); t.color?.let { put("color", it) } }) } })
            put("noteTags", buildJsonArray { s.noteTags.forEach { (n, t) -> add(buildJsonObject { put("noteId", n); put("tagId", t) }) } })
            put("attachments", buildJsonArray { s.attachments.forEach { add(attachment(it)) } })
            put("revisions", buildJsonArray { s.revisions.forEach { r -> add(buildJsonObject {
                put("id", r.id); put("noteId", r.noteId); put("title", r.title); put("body", r.body); put("createdAt", r.createdAt.toString()); put("pinned", r.pinned)
            }) } })
            put("tasks", buildJsonArray { s.tasks.forEach { add(task(it)) } })
            put("templates", buildJsonArray { s.templates.forEach { add(TemplateJson.toJson(it)) } })
            put("savedSearches", buildJsonArray { s.savedSearches.forEach { q -> add(buildJsonObject { put("id", q.id); put("name", q.name); put("query", q.query); put("createdAt", q.createdAt.toString()) }) } })
        })

    private fun note(n: Note) = buildJsonObject {
        put("id", n.id); put("title", n.title); put("body", n.body)
        n.folderId?.let { put("folderId", it) }
        put("createdAt", n.createdAt.toString()); put("updatedAt", n.updatedAt.toString())
        n.lastOpenedAt?.let { put("lastOpenedAt", it.toString()) }
        put("pinned", n.pinned); put("favorite", n.favorite); put("state", n.state.name)
        n.trashedAt?.let { put("trashedAt", it.toString()) }
        n.templateId?.let { put("templateId", it) }
        put("contentFormatVersion", n.contentFormatVersion)
        put("excludedFromAi", n.excludedFromAi)
        n.reviewedAt?.let { put("reviewedAt", it.toString()) }
        n.importedAt?.let { put("importedAt", it.toString()) }
    }

    private fun folder(f: Folder) = buildJsonObject {
        put("id", f.id); put("name", f.name); f.parentId?.let { put("parentId", it) }
        f.color?.let { put("color", it) }; f.icon?.let { put("icon", it) }
        put("sortOrder", f.sortOrder); put("pinned", f.pinned); put("archived", f.archived); put("createdAt", f.createdAt.toString())
    }

    private fun attachment(a: Attachment) = buildJsonObject {
        put("id", a.id); put("noteId", a.noteId); put("fileName", a.fileName); put("mimeType", a.mimeType)
        put("sizeBytes", a.sizeBytes); put("storagePath", a.storagePath); put("createdAt", a.createdAt.toString())
        a.extractedText?.let { put("extractedText", it) }; a.sha256?.let { put("sha256", it) }
    }

    private fun task(t: TaskItem) = buildJsonObject {
        put("id", t.id); put("title", t.title); put("notes", t.notes)
        t.noteId?.let { put("noteId", it) }; t.parentTaskId?.let { put("parentTaskId", it) }
        t.dueDate?.let { put("dueDate", it.toString()) }; t.dueTime?.let { put("dueTime", it.toString()) }
        put("zoneId", t.zoneId); put("priority", t.priority.name)
        t.completedAt?.let { put("completedAt", it.toString()) }
        t.recurrence?.let { put("recurrence", it) }
        t.reminderOffsetMinutes?.let { put("reminderOffsetMinutes", it) }
        t.snoozedUntil?.let { put("snoozedUntil", it.toString()) }
        put("createdAt", t.createdAt.toString()); put("updatedAt", t.updatedAt.toString())
    }

    fun decode(text: String): LibrarySnapshot {
        val root = try { Json.parseToJsonElement(text) } catch (e: Exception) { throw FormatException("Not valid JSON") }
        val o = root as? JsonObject ?: throw FormatException("Expected a JSON object")
        if (o.s("format") != FORMAT) throw FormatException("Not a Margin library export")
        val v = (o["version"] as? JsonPrimitive)?.intOrNull ?: throw FormatException("Missing version")
        if (v > VERSION) throw FormatException("Created by a newer app version ($v)")
        try {
            return LibrarySnapshot(
                notes = o.arr("notes").map { it.obj().let { n ->
                    Note(
                        id = n.req("id"), title = n.s("title").orEmpty(), body = n.s("body").orEmpty(), folderId = n.s("folderId"),
                        createdAt = n.instant("createdAt")!!, updatedAt = n.instant("updatedAt")!!, lastOpenedAt = n.instant("lastOpenedAt"),
                        pinned = n.b("pinned"), favorite = n.b("favorite"),
                        state = n.s("state")?.let { st -> NoteState.entries.firstOrNull { it.name == st } } ?: NoteState.ACTIVE,
                        trashedAt = n.instant("trashedAt"), templateId = n.s("templateId"),
                        contentFormatVersion = (n["contentFormatVersion"] as? JsonPrimitive)?.intOrNull ?: 1,
                        excludedFromAi = n.b("excludedFromAi"), reviewedAt = n.instant("reviewedAt"), importedAt = n.instant("importedAt"),
                    )
                } },
                folders = o.arr("folders").map { it.obj().let { f ->
                    Folder(f.req("id"), f.req("name"), f.s("parentId"), (f["color"] as? JsonPrimitive)?.longOrNull, f.s("icon"),
                        (f["sortOrder"] as? JsonPrimitive)?.intOrNull ?: 0, f.b("pinned"), f.b("archived"), f.instant("createdAt")!!)
                } },
                tags = o.arr("tags").map { it.obj().let { t -> Tag(t.req("id"), t.req("name"), t.req("key"), (t["color"] as? JsonPrimitive)?.longOrNull) } },
                noteTags = o.arr("noteTags").map { it.obj().let { x -> x.req("noteId") to x.req("tagId") } },
                attachments = o.arr("attachments").map { it.obj().let { a ->
                    Attachment(a.req("id"), a.req("noteId"), a.req("fileName"), a.s("mimeType") ?: "application/octet-stream",
                        (a["sizeBytes"] as? JsonPrimitive)?.longOrNull ?: 0, a.req("storagePath"), a.instant("createdAt")!!, a.s("extractedText"), a.s("sha256"))
                } },
                revisions = o.arr("revisions").map { it.obj().let { r ->
                    NoteRevision(r.req("id"), r.req("noteId"), r.s("title").orEmpty(), r.s("body").orEmpty(), r.instant("createdAt")!!, r.b("pinned"))
                } },
                tasks = o.arr("tasks").map { it.obj().let { t ->
                    TaskItem(
                        id = t.req("id"), title = t.req("title"), notes = t.s("notes").orEmpty(), noteId = t.s("noteId"), parentTaskId = t.s("parentTaskId"),
                        dueDate = t.s("dueDate")?.let(LocalDate::parse), dueTime = t.s("dueTime")?.let(LocalTime::parse),
                        zoneId = t.s("zoneId") ?: "UTC",
                        priority = t.s("priority")?.let { p -> TaskPriority.entries.firstOrNull { it.name == p } } ?: TaskPriority.NONE,
                        completedAt = t.instant("completedAt"), recurrence = t.s("recurrence"),
                        reminderOffsetMinutes = (t["reminderOffsetMinutes"] as? JsonPrimitive)?.intOrNull,
                        snoozedUntil = t.instant("snoozedUntil"), createdAt = t.instant("createdAt")!!, updatedAt = t.instant("updatedAt")!!,
                    )
                } },
                templates = o.arr("templates").map { TemplateJson.fromJson(it.obj()) },
                savedSearches = o.arr("savedSearches").map { it.obj().let { q -> SavedSearch(q.req("id"), q.req("name"), q.req("query"), q.instant("createdAt")!!) } },
            )
        } catch (e: FormatException) {
            throw e
        } catch (e: Exception) {
            throw FormatException("Malformed library data: ${e.javaClass.simpleName}")
        }
    }

    private fun JsonElement.obj(): JsonObject = this as? JsonObject ?: throw FormatException("Expected object")
    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.req(k: String): String = s(k) ?: throw FormatException("Missing '$k'")
    private fun JsonObject.b(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
    private fun JsonObject.arr(k: String): JsonArray = this[k] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.instant(k: String): Instant? = s(k)?.let { Instant.parse(it) }
}
