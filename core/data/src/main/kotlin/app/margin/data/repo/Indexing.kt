package app.margin.data.repo

import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import app.margin.data.db.MarginDatabase
import app.margin.data.db.NoteEntity
import app.margin.data.db.NoteFtsEntity
import app.margin.data.db.NoteLinkEntity
import app.margin.domain.editor.PersistenceException
import app.margin.domain.editor.SaveError
import app.margin.domain.knowledge.LinkGraph
import app.margin.domain.organization.Folders

/**
 * Keeps derived structures (FTS index, explicit link table) in step with canonical note data.
 * Always called inside the same transaction as the write that made them stale.
 */
internal class Indexer(private val db: MarginDatabase) {

    suspend fun reindex(note: NoteEntity) {
        db.fts().deleteForNote(note.id)
        if (note.state == "TRASHED") return // trashed notes are not searchable unless explicitly requested (handled by scan)
        val tags = db.tags().forNote(note.id).joinToString(" ") { it.name }
        val folders = db.folders().all().associate { it.id to it }
        val path = Folders.path(note.folderId, folders.mapValues { (_, f) ->
            app.margin.domain.model.Folder(f.id, f.name, f.parentId, createdAt = java.time.Instant.EPOCH)
        }).joinToString(" ") { it.name }
        val attachments = db.attachments().forNotes(listOf(note.id)).joinToString("\n") { it.fileName + " " + (it.extractedText ?: "") }
        db.fts().insert(NoteFtsEntity(noteId = note.id, title = note.title, body = note.plainText, tags = tags, folder = path, attachments = attachments))
    }

    suspend fun reindex(noteId: String) {
        db.notes().get(noteId)?.let { reindex(it) } ?: db.fts().deleteForNote(noteId)
    }

    /** Re-resolves [[links]] for a note. Unresolved titles simply produce no link row. */
    suspend fun relink(note: NoteEntity) {
        db.links().clearFrom(note.id)
        val index = titleIndex()
        val ids = db.notes().allIds().toSet()
        val res = LinkGraph.resolve(note.body, index, ids)
        db.links().insert(res.resolved.filter { it != note.id }.map { NoteLinkEntity(note.id, it) })
    }

    /** Lower-cased display title → id, most recently updated first wins. */
    suspend fun titleIndex(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (row in db.notes().titlesForLinking()) {
            val t = row.title.trim().ifEmpty { app.margin.domain.model.Note.derivedTitle(row.plainText) ?: "" }.lowercase()
            if (t.isNotEmpty()) out.putIfAbsent(t, row.id)
        }
        return out
    }

    suspend fun rebuildAll(): Int {
        db.fts().clear()
        val notes = db.notes().all()
        notes.forEach { reindex(it); relink(it) }
        return notes.size
    }
}

/** Maps low-level storage failures to typed errors the autosave pipeline understands. */
internal inline fun <T> persisting(block: () -> T): T = try {
    block()
} catch (e: SQLiteFullException) {
    throw PersistenceException(SaveError.STORAGE_FULL, e)
} catch (e: SQLiteDiskIOException) {
    throw PersistenceException(SaveError.DATABASE_UNAVAILABLE, e)
} catch (e: SQLiteDatabaseLockedException) {
    throw PersistenceException(SaveError.DATABASE_UNAVAILABLE, e)
} catch (e: SQLiteException) {
    throw PersistenceException(SaveError.DATABASE_UNAVAILABLE, e)
}
