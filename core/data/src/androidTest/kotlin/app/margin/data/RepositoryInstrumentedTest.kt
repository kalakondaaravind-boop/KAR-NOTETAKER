package app.margin.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.margin.data.db.MarginDatabase
import app.margin.data.repo.AttachmentFiles
import app.margin.data.repo.FolderRepositoryImpl
import app.margin.data.repo.LibraryRepositoryImpl
import app.margin.data.repo.NoteRepositoryImpl
import app.margin.data.repo.ReminderScheduler
import app.margin.data.repo.SearchRepositoryImpl
import app.margin.data.repo.TagRepositoryImpl
import app.margin.data.repo.TaskRepositoryImpl
import app.margin.domain.io.BackupArchive
import app.margin.domain.io.DuplicatePolicy
import app.margin.domain.io.ImportCandidate
import app.margin.domain.model.Clock
import app.margin.domain.model.NoteState
import app.margin.domain.model.TaskItem
import app.margin.domain.organization.FolderDeletePolicy
import app.margin.domain.organization.NoteFilter
import app.margin.domain.organization.SortOrder
import app.margin.domain.repository.RestoreMode
import app.margin.domain.repository.TagRepository
import app.margin.domain.revision.RevisionSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Repository-level versions of the critical regression workflows (spec §22.4), on a real SQLite
 * database. Run with: ./gradlew :core:data:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var now = Instant.parse("2025-03-01T10:00:00Z")
    private val clock = Clock { now }
    private lateinit var db: MarginDatabase
    private lateinit var files: AttachmentFiles
    private lateinit var notes: NoteRepositoryImpl
    private lateinit var search: SearchRepositoryImpl
    private val dbName = "test-${System.nanoTime()}.db"

    private val scheduled = ArrayList<String>()
    private val scheduler = object : ReminderScheduler {
        override suspend fun schedule(task: TaskItem): Boolean { scheduled += task.id; return true }
        override suspend fun cancel(taskId: String) { scheduled -= taskId }
    }

    private fun open() {
        db = Room.databaseBuilder(context, MarginDatabase::class.java, dbName).build()
        files = AttachmentFiles(File(context.cacheDir, "att-$dbName"))
        notes = NoteRepositoryImpl(db, clock, { RevisionSettings() }, files)
        search = SearchRepositoryImpl(db, clock)
    }

    @Before fun setUp() = open()

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test fun createEditLeaveReopen() = runBlocking {
        // #1 create → type → leave → reopen
        val n = notes.create()
        notes.saveContent(n.id, "Groceries", "- [ ] milk\n- [ ] eggs", 5)
        val back = notes.getNote(n.id)!!
        assertEquals("Groceries", back.title)
        assertEquals("- [ ] milk\n- [ ] eggs", back.body)
        assertEquals(5, back.lastCursor)
    }

    @Test fun survivesRestart() = runBlocking {
        // #2 create (offline by construction) → restart (close + reopen the database file) → verify
        val n = notes.create(title = "Offline", body = "written without network")
        db.close()
        open()
        assertEquals("written without network", notes.getNote(n.id)!!.body)
    }

    @Test fun trashRestoreKeepsRelationships() = runBlocking {
        // #4 delete → restore → content and relationships intact
        val folders = FolderRepositoryImpl(db, clock)
        val f = folders.create("Work", null)
        val n = notes.create(title = "Plan", body = "body", folderId = f.id, tags = listOf("Q1"))
        notes.moveToTrash(listOf(n.id))
        assertEquals(NoteState.TRASHED, notes.getNote(n.id)!!.state)
        notes.restoreFromTrash(listOf(n.id))
        val r = notes.getNote(n.id)!!
        assertEquals(NoteState.ACTIVE, r.state)
        assertEquals(f.id, r.folderId)
        assertEquals(listOf("Q1"), notes.observeTagsForNote(n.id).first().map { it.name })
    }

    @Test fun restoreAfterFolderDeletedLandsInInbox() = runBlocking {
        val folders = FolderRepositoryImpl(db, clock)
        val f = folders.create("Temp", null)
        val n = notes.create(title = "x", body = "y", folderId = f.id)
        folders.delete(f.id, FolderDeletePolicy.TRASH_NOTES)
        assertEquals(NoteState.TRASHED, notes.getNote(n.id)!!.state)
        notes.restoreFromTrash(listOf(n.id))
        assertNull(notes.getNote(n.id)!!.folderId)
    }

    @Test fun tagsAndSearchByTag() = runBlocking {
        // #5 create tags → apply → search by tag
        val a = notes.create(title = "Client call", body = "notes", tags = listOf("work/clients"))
        notes.create(title = "Shopping", body = "list", tags = listOf("home"))
        notes.setTags(a.id, listOf("Work/Clients", "urgent")) // same key as existing → no duplicate tag
        val hits = search.search("tag:work").hits.map { it.id }
        assertEquals(listOf(a.id), hits)
        assertEquals(3, TagRepositoryImpl(db).observeTags().first().size)
    }

    @Test fun searchReflectsUpdatesAndDeletes() = runBlocking {
        // #9 / #10
        val n = notes.create(title = "Alpha", body = "first draft")
        assertEquals(listOf(n.id), search.search("first").hits.map { it.id })
        notes.saveContent(n.id, "Alpha", "second draft", 0)
        assertTrue(search.search("first").hits.isEmpty())
        assertEquals(listOf(n.id), search.search("second").hits.map { it.id })
        notes.moveToTrash(listOf(n.id))
        assertTrue(search.search("second").hits.isEmpty())
        assertEquals(listOf(n.id), search.search("second is:trashed").hits.map { it.id })
        notes.deletePermanently(listOf(n.id))
        assertTrue(search.search("second is:trashed").hits.isEmpty())
    }

    @Test fun indexRebuildRecoversFromDamage() = runBlocking {
        val n = notes.create(title = "Recoverable", body = "content")
        db.fts().clear() // simulate a lost index
        assertTrue(search.search("recoverable").hits.isEmpty())
        search.rebuildIndex()
        assertEquals(listOf(n.id), search.search("recoverable").hits.map { it.id })
    }

    @Test fun revisionsAndRestore() = runBlocking {
        val n = notes.create(title = "Doc", body = "v1")
        notes.saveContent(n.id, "Doc", "v2", 0)
        val revs = notes.observeRevisions(n.id).first()
        assertEquals("v1", revs.single().body)
        notes.restoreRevision(revs.single().id)
        assertEquals("v1", notes.getNote(n.id)!!.body)
        // Restoring kept the replaced text as a revision too.
        assertTrue(notes.observeRevisions(n.id).first().any { it.body == "v2" })
    }

    @Test fun backlinks() = runBlocking {
        val target = notes.create(title = "Target", body = "")
        val src = notes.create(title = "Source", body = "see [[Target]]")
        assertEquals(listOf(src.id), notes.observeBacklinks(target.id).first().map { it.id })
    }

    @Test fun taskPersistsAndRecurs() = runBlocking {
        // #8 create task → schedule → persisted state
        val tasks = TaskRepositoryImpl(db, clock, scheduler)
        val t = TaskItem("t1", "Pay rent", dueDate = LocalDate.of(2025, 3, 5), zoneId = "UTC", reminderOffsetMinutes = 60,
            recurrence = "FREQ=MONTHLY", createdAt = now, updatedAt = now)
        tasks.upsert(t)
        assertTrue("t1" in scheduled)
        assertEquals("Pay rent", tasks.get("t1")!!.title)
        val next = tasks.complete("t1")!!
        assertNotNull(tasks.get("t1")!!.completedAt)
        assertEquals(LocalDate.of(2025, 4, 5), next.dueDate)
        assertTrue(next.id in scheduled)
    }

    @Test fun backupToCleanDatabaseRoundTrip() = runBlocking {
        // #7 export library → import into clean database → verify content and attachments
        val library = LibraryRepositoryImpl(db, files, clock)
        val n = notes.create(title = "Keep me", body = "important", tags = listOf("x"))
        val att = app.margin.data.repo.AttachmentRepositoryImpl(db, files, clock).add(n.id, object : app.margin.domain.repository.AttachmentSource {
            override val displayName = "a.txt"; override val mimeType = "text/plain"; override val sizeHint = 3L
            override fun open() = ByteArrayInputStream("abc".toByteArray())
        }).getOrThrow()
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, library.snapshot(), { files.open(it.storagePath) }, now, "test")
        val staged = HashMap<String, File>()
        val read = BackupArchive.read(ByteArrayInputStream(out.toByteArray())) { id, data ->
            val f = File(context.cacheDir, "stage-$id"); f.outputStream().use { data.copyTo(it) }; staged[id] = f
        }
        // Clean database
        db.close(); context.deleteDatabase(dbName); open()
        val clean = LibraryRepositoryImpl(db, files, clock)
        clean.restore(read.snapshot, RestoreMode.REPLACE, staged)
        val restored = notes.getNote(n.id)!!
        assertEquals("important", restored.body)
        assertEquals(listOf("x"), notes.observeTagsForNote(n.id).first().map { it.name })
        assertEquals("abc", files.open(att.storagePath)!!.readBytes().toString(Charsets.UTF_8))
        assertEquals(listOf(n.id), search.search("important").hits.map { it.id })
    }

    @Test fun malformedImportLeavesDataIntact() = runBlocking {
        // #11
        val n = notes.create(title = "Existing", body = "safe")
        try { BackupArchive.read(ByteArrayInputStream("garbage".toByteArray())) { _, _ -> } } catch (e: BackupArchive.InvalidBackup) { /* expected */ }
        assertEquals("safe", notes.getNote(n.id)!!.body)
        assertEquals(1, notes.observeNotes(NoteFilter(), SortOrder()).first().size)
    }

    @Test fun importSkipsDuplicates() = runBlocking {
        val library = LibraryRepositoryImpl(db, files, clock)
        val c = ImportCandidate("a.md", "Same", "body", folderPath = listOf("Imported", "Sub"))
        assertEquals(1, library.importNotes(listOf(c), DuplicatePolicy.SKIP).imported)
        val again = library.importNotes(listOf(c), DuplicatePolicy.SKIP)
        assertEquals(0, again.imported)
        assertEquals(1, again.skipped.size)
        assertEquals(2, db.folders().all().size)
    }

    @Test fun trashRetentionPurges() = runBlocking {
        val n = notes.create(title = "old", body = "x")
        notes.moveToTrash(listOf(n.id))
        now = now.plus(Duration.ofDays(31))
        assertEquals(1, notes.purgeTrashOlderThanDays(30))
        assertNull(notes.getNote(n.id))
    }

    @Test fun tagRenameMerges() = runBlocking {
        val tags = TagRepositoryImpl(db)
        val a = notes.create(title = "a", body = "", tags = listOf("alpha"))
        notes.create(title = "b", body = "", tags = listOf("beta"))
        val betaId = tags.observeTags().first().first { it.tag.key == "beta" }.tag.id
        assertEquals(TagRepository.RenameResult.MERGED, tags.rename(betaId, "Alpha"))
        assertEquals(listOf("alpha"), tags.observeTags().first().map { it.tag.key })
        assertEquals(2, tags.observeTags().first().single().count)
        assertFalse(notes.observeTagsForNote(a.id).first().isEmpty())
    }
}
