package app.margin.domain.io

import app.margin.domain.model.Attachment
import app.margin.domain.model.Folder
import app.margin.domain.model.Note
import app.margin.domain.model.NoteRevision
import app.margin.domain.model.NoteState
import app.margin.domain.model.SavedSearch
import app.margin.domain.model.Tag
import app.margin.domain.model.TaskItem
import app.margin.domain.template.BuiltInTemplates
import app.margin.domain.template.TemplateCatalog
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class IoTest {
    private val t0 = Instant.parse("2025-02-01T10:00:00Z")

    private val fixedTemplate = TemplateCatalog.duplicate(BuiltInTemplates.all.first())

    private fun snapshot(): LibrarySnapshot {
        val folders = listOf(Folder("f1", "Work", null, createdAt = t0), Folder("f2", "Clients: A/B", "f1", createdAt = t0))
        val notes = listOf(
            Note("n1", "Plan: Q1 \"draft\"", "# Plan\n\n- [ ] one\n- [x] two\n\n```\ncode <b>\n```\n", "f2", t0, t0.plusSeconds(60), pinned = true),
            Note("n2", "", "Untitled body line\nmore", null, t0, t0, state = NoteState.TRASHED, trashedAt = t0),
            Note("n3", "Plan: Q1 \"draft\"", "same title, different body", "f2", t0.plusSeconds(1), t0),
        )
        val tags = listOf(Tag("t1", "Work", "work"), Tag("t2", "émigré", "émigré"))
        return LibrarySnapshot(
            notes = notes, folders = folders, tags = tags, noteTags = listOf("n1" to "t1", "n1" to "t2"),
            attachments = listOf(Attachment("a1", "n1", "scan.png", "image/png", 4, "a1", t0, "OCR text", null)),
            revisions = listOf(NoteRevision("r1", "n1", "Old", "old body", t0)),
            tasks = listOf(TaskItem("k1", "Task", noteId = "n1", dueDate = LocalDate.of(2025, 3, 1), zoneId = "UTC", recurrence = "FREQ=WEEKLY", createdAt = t0, updatedAt = t0)),
            templates = listOf(fixedTemplate),
            savedSearches = listOf(SavedSearch("s1", "Open work", "tag:work has:open-task", t0)),
        )
    }

    @Test fun libraryJsonRoundTripIsLossless() {
        val s = snapshot()
        val back = LibraryJson.decode(LibraryJson.encode(s, t0, "1.0"))
        assertEquals(s, back)
        assertTrue(back.integrityProblems().isEmpty())
    }

    @Test fun libraryJsonRejectsBadInput() {
        listOf("", "[]", "{}", "{\"format\":\"margin.library\"}", "{\"format\":\"margin.library\",\"version\":99}",
            "{\"format\":\"margin.library\",\"version\":1,\"notes\":[{\"title\":\"x\"}]}",
            "{\"format\":\"margin.library\",\"version\":1,\"notes\":[{\"id\":\"x\",\"createdAt\":\"yesterday\"}]}",
        ).forEach { j ->
            try { LibraryJson.decode(j); fail("accepted: $j") } catch (e: LibraryJson.FormatException) { /* expected */ }
        }
    }

    @Test fun integrityChecks() {
        val s = snapshot()
        assertTrue(s.copy(noteTags = s.noteTags + ("zz" to "t1")).integrityProblems().isNotEmpty())
        assertTrue(s.copy(notes = s.notes + s.notes[0]).integrityProblems().any { it.contains("duplicate note") })
        assertTrue(s.copy(attachments = listOf(s.attachments[0].copy(storagePath = "../../etc/passwd"))).integrityProblems().any { it.contains("unsafe") })
    }

    @Test fun markdownExportAndReimport() {
        val n = snapshot().notes[0]
        val md = NoteExporter.markdown(n, NoteExportContext(listOf("Work", "a: b"), listOf("Work", "Clients")))
        assertTrue(md.startsWith("---\nid: n1\ntitle: \"Plan: Q1 \\\"draft\\\"\"\n"))
        val c = MarkdownImporter.parse("x.md", md)
        assertEquals("Plan: Q1 \"draft\"", c.title)
        assertEquals(n.body, c.body)
        assertEquals(listOf("Work", "a: b"), c.tags)
        assertEquals(listOf("Work", "Clients"), c.folderPath)
        assertEquals(n.createdAt, c.createdAt)
        assertTrue(c.pinned)
        assertEquals("n1", c.originalId)
    }

    @Test fun markdownWithoutFrontMatter() {
        val c = MarkdownImporter.parse("Groceries.md", "- milk\n- eggs")
        assertEquals("Groceries", c.title)
        assertEquals("- milk\n- eggs", c.body)
        val h = MarkdownImporter.parse("x.md", "# Heading title\nbody")
        assertEquals("Heading title", h.title)
        val listStyle = MarkdownImporter.parse("y.md", "---\ntitle: T\ntags:\n  - a\n  - b\ncreated: 2024-05-01\n---\nbody")
        assertEquals(listOf("a", "b"), listStyle.tags)
        assertEquals(Instant.parse("2024-05-01T00:00:00Z"), listStyle.createdAt)
        val broken = MarkdownImporter.parse("z.md", "---\nunterminated: yes\nbody")
        assertEquals("---\nunterminated: yes\nbody", broken.body)
    }

    @Test fun htmlExportIsSafeAndReimportable() {
        val n = snapshot().notes[0]
        val html = NoteExporter.html(n, NoteExportContext(listOf("Work"), listOf("Work")), ZoneOffset.UTC)
        assertTrue(html.contains("<title>Plan: Q1 &quot;draft&quot;</title>"))
        assertTrue(html.contains("code &lt;b&gt;"))
        val back = HtmlImporter.parse("p.html", html)
        assertEquals("Plan: Q1 \"draft\"", back.title)
        assertTrue(back.body.contains("- [ ] one"))
        assertTrue(back.body.contains("- [x] two"))
        assertTrue(back.body.contains("code <b>"))
    }

    @Test fun htmlImportDropsScriptsAndUnsafeLinks() {
        val md = HtmlImporter.toMarkdown("<h2>T</h2><p>Hi <b>there</b> <u>u</u> <a href=\"javascript:x()\">bad</a> <a href='https://ok.io'>ok</a></p><script>alert(1)</script><ul><li>a</li><li>b</li></ul>&amp;&#169;")
        assertEquals("## T\n\nHi **there** <u>u</u> bad [ok](https://ok.io)\n\n- a\n- b\n\n&©\n", md)
    }

    @Test fun textDecoding() {
        assertEquals("héllo\nx", TextDecoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "héllo\r\nx".toByteArray()).getOrThrow())
        assertEquals(FailReason.BINARY_CONTENT, (TextDecoding.decode(byteArrayOf(1, 0, 2)).exceptionOrNull() as ImportFailure).reason)
        assertEquals("café", TextDecoding.decode(byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte())).getOrThrow()) // Latin-1 fallback
    }

    @Test fun importPlannerDuplicates() {
        val a = ImportCandidate("a.md", "T", "body")
        val b = ImportCandidate("b.md", "T", "body")
        val e = ImportCandidate("e.md", "", "  ")
        val existing = ImportCandidate("c.md", "Old", "x", originalId = "n1")
        val p = ImportPlanner.plan(listOf(a, b, e, existing), setOf("n1"), emptySet(), DuplicatePolicy.SKIP)
        assertEquals(listOf("a.md"), p.toImport.map { it.sourceName })
        assertEquals(3, p.issues.size)
        val keep = ImportPlanner.plan(listOf(a, b), emptySet(), setOf(ImportPlanner.fingerprint("T", "body")), DuplicatePolicy.KEEP_BOTH)
        assertEquals(2, keep.toImport.size)
    }

    @Test fun safeFileNamesAndUniquePaths() {
        assertEquals("a_b_c_", NoteExporter.safeFileName("a/b:c?"))
        assertEquals("_CON", NoteExporter.safeFileName("CON"))
        assertEquals("Untitled", NoteExporter.safeFileName("..."))
        val s = snapshot()
        val paths = NoteExporter.assignPaths(s.notes, s.folders, "md")
        assertEquals("Work/Clients_ A_B/Plan_ Q1 _draft_.md", paths["n1"])
        assertEquals("Work/Clients_ A_B/Plan_ Q1 _draft_ (2).md", paths["n3"])
        assertEquals("Untitled body line.md", paths["n2"])
    }

    // ---- Backup archive -------------------------------------------------------------------

    private fun writeBackup(s: LibrarySnapshot = snapshot()): ByteArray {
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, s, { a -> if (a.id == "a1") ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) else null }, t0, "1.0", ZoneOffset.UTC)
        return out.toByteArray()
    }

    @Test fun backupRoundTripRestoresEverything() {
        // Regression #7: export a library → import into a clean database → verify content and attachments.
        val bytes = writeBackup()
        val staged = HashMap<String, ByteArray>()
        val r = BackupArchive.read(ByteArrayInputStream(bytes)) { id, data -> staged[id] = data.readBytes() }
        assertEquals(snapshot(), r.snapshot)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), staged["a1"])
        assertEquals(t0, r.createdAt)
        assertTrue(r.missingAttachments.isEmpty())
        // Human-readable Markdown copies are present.
        val names = ZipInputStream(ByteArrayInputStream(bytes)).let { z -> generateSequence { z.nextEntry?.name }.toList() }
        assertTrue(names.contains("markdown/Work/Clients_ A_B/Plan_ Q1 _draft_.md"))
    }

    @Test fun backupRecordsMissingAttachments() {
        val out = ByteArrayOutputStream()
        val w = BackupArchive.write(out, snapshot(), { null }, t0, "1.0")
        assertEquals(listOf("a1"), w.missingAttachments)
        val r = BackupArchive.read(ByteArrayInputStream(out.toByteArray())) { _, _ -> fail("nothing to stage") }
        assertEquals(listOf("a1"), r.missingAttachments)
    }

    private fun rewrite(bytes: ByteArray, transform: (String, ByteArray) -> Pair<String, ByteArray>?): ByteArray {
        val out = ByteArrayOutputStream()
        val zin = ZipInputStream(ByteArrayInputStream(bytes))
        ZipOutputStream(out).use { z ->
            while (true) {
                val e = zin.nextEntry ?: break
                val r = transform(e.name, zin.readBytes()) ?: continue
                z.putNextEntry(ZipEntry(r.first)); z.write(r.second); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun expectInvalid(bytes: ByteArray, reason: BackupArchive.InvalidBackup.Reason) {
        try {
            BackupArchive.read(ByteArrayInputStream(bytes)) { _, d -> d.readBytes() }
            fail("expected $reason")
        } catch (e: BackupArchive.InvalidBackup) {
            assertEquals(reason, e.reason)
        }
    }

    @Test fun tamperedOrDamagedBackupsAreRejected() {
        // Regression #11: malformed imports never reach the database because verification happens first.
        val good = writeBackup()
        expectInvalid(rewrite(good) { n, b -> if (n == "library.json") n to String(b).replace("one", "ONE").toByteArray() else n to b }, BackupArchive.InvalidBackup.Reason.CHECKSUM_MISMATCH)
        expectInvalid(rewrite(good) { n, b -> if (n == "attachments/a1") null else n to b }, BackupArchive.InvalidBackup.Reason.MISSING_ENTRY)
        expectInvalid(rewrite(good) { n, b -> if (n == "manifest.json") null else n to b }, BackupArchive.InvalidBackup.Reason.MISSING_MANIFEST)
        expectInvalid(rewrite(good) { n, b -> if (n == "attachments/a1") "../evil" to b else n to b }, BackupArchive.InvalidBackup.Reason.UNSAFE_PATH)
        expectInvalid(rewrite(good) { n, b -> if (n == "manifest.json") n to String(b).replace("\"version\":1", "\"version\":9").toByteArray() else n to b }, BackupArchive.InvalidBackup.Reason.NEWER_VERSION)
        expectInvalid(ByteArray(0), BackupArchive.InvalidBackup.Reason.NOT_A_BACKUP)
        expectInvalid("not a zip at all".toByteArray(), BackupArchive.InvalidBackup.Reason.NOT_A_BACKUP)
        // Inconsistent references inside an otherwise well-formed archive.
        val bad = snapshot().let { it.copy(noteTags = it.noteTags + ("ghost" to "t1")) }
        expectInvalid(writeBackup(bad), BackupArchive.InvalidBackup.Reason.INCONSISTENT)
    }

    // ---- Encryption -----------------------------------------------------------------------

    private fun enc(data: ByteArray, pass: String): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encrypt(ByteArrayInputStream(data), out, pass.toCharArray(), iterations = 10_000)
        return out.toByteArray()
    }

    private fun dec(data: ByteArray, pass: String): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(data), out, pass.toCharArray())
        return out.toByteArray()
    }

    @Test fun encryptionRoundTripAcrossSegments() {
        for (size in listOf(0, 1, (1 shl 20) - 1, 1 shl 20, (1 shl 20) + 1, 3 * (1 shl 20) + 17)) {
            val data = ByteArray(size) { (it * 31 + 7).toByte() }
            val e = enc(data, "correct horse")
            assertTrue(BackupCrypto.isEncrypted(e))
            assertFalse(e.copyOfRange(e.size - minOf(e.size, 64), e.size).contentEquals(data.copyOfRange(maxOf(0, size - 64), size)))
            assertArrayEquals("size $size", data, dec(e, "correct horse"))
        }
    }

    @Test fun wrongPassphraseTruncationAndTamperingFail() {
        val data = ByteArray(2 * (1 shl 20) + 5) { it.toByte() }
        val e = enc(data, "pw")
        fun expectFail(bytes: ByteArray, pass: String = "pw") {
            try { dec(bytes, pass); fail("should fail") } catch (x: BackupCrypto.WrongPassphraseOrCorrupt) { /* ok */ }
        }
        expectFail(e, "PW")
        expectFail(e.copyOf(e.size - 1))
        // Drop the final segment entirely (truncate at a segment boundary).
        val firstSegEnd = 35 + 4 + (1 shl 20) + 16
        val secondSegEnd = firstSegEnd + 4 + (1 shl 20) + 16
        expectFail(e.copyOf(secondSegEnd))
        expectFail(e.copyOf().also { it[100] = (it[100].toInt() xor 1).toByte() })
        expectFail(e.copyOf().also { it[10] = (it[10].toInt() xor 1).toByte() }) // header (salt) tampering
        expectFail(e + byteArrayOf(0, 0, 0, 20) + ByteArray(20))
        try { dec("plain".toByteArray(), "pw"); fail() } catch (x: BackupCrypto.NotEncrypted) { /* ok */ }
    }

    @Test fun encryptedBackupEndToEnd() {
        val zip = writeBackup()
        val e = enc(zip, "s3cret")
        val plain = dec(e, "s3cret")
        val r = BackupArchive.read(ByteArrayInputStream(plain)) { _, d -> d.readBytes() }
        assertEquals(snapshot(), r.snapshot)
    }
}
