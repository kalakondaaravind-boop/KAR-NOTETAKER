package app.margin.domain.io

import app.margin.domain.model.Attachment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Full backup archive (.marginbackup, a ZIP; docs/BACKUP_AND_RECOVERY.md):
 *   library.json            lossless library (LibraryJson)
 *   attachments/<id>        original attachment bytes
 *   markdown/<folders>/<title>.md  human-readable copy of every note (convenience; ignored on restore)
 *   manifest.json           format, version, counts, SHA-256 and size of every other entry
 * Restore verifies every checksum before anything is applied, so a damaged archive never
 * partially overwrites the library.
 */
object BackupArchive {
    const val FORMAT = "margin.backup"
    const val VERSION = 1
    const val MAX_ENTRIES = 100_000
    const val MAX_TOTAL_BYTES = 8L * 1024 * 1024 * 1024
    private const val MAX_JSON_BYTES = 512L * 1024 * 1024

    data class WriteResult(val entries: Int, val bytes: Long, val missingAttachments: List<String>)

    fun write(
        out: OutputStream,
        snapshot: LibrarySnapshot,
        openAttachment: (Attachment) -> InputStream?,
        exportedAt: Instant,
        appVersion: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WriteResult {
        val zip = ZipOutputStream(out)
        val checksums = LinkedHashMap<String, Pair<String, Long>>()
        var total = 0L
        val missing = ArrayList<String>()

        fun put(name: String, source: InputStream) {
            zip.putNextEntry(ZipEntry(name))
            val md = MessageDigest.getInstance("SHA-256")
            var size = 0L
            val buf = ByteArray(64 * 1024)
            source.use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n); zip.write(buf, 0, n); size += n
                }
            }
            zip.closeEntry()
            checksums[name] = hex(md.digest()) to size
            total += size
        }

        put("library.json", LibraryJson.encode(snapshot, exportedAt, appVersion).toByteArray(Charsets.UTF_8).inputStream())
        for (a in snapshot.attachments) {
            val s = openAttachment(a)
            if (s == null) missing += a.id else put("attachments/${a.id}", s)
        }
        val paths = NoteExporter.assignPaths(snapshot.notes, snapshot.folders, "md")
        val tagsById = snapshot.tags.associateBy { it.id }
        val folderById = snapshot.folders.associateBy { it.id }
        for (n in snapshot.notes) {
            val ctx = NoteExportContext(
                tags = snapshot.noteTags.filter { it.first == n.id }.mapNotNull { tagsById[it.second]?.name },
                folderPath = app.margin.domain.organization.Folders.path(n.folderId, folderById).map { it.name },
            )
            put("markdown/" + paths.getValue(n.id), NoteExporter.markdown(n, ctx).toByteArray(Charsets.UTF_8).inputStream())
        }
        val manifest = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("createdAt", exportedAt.toString())
            put("appVersion", appVersion)
            put("timeZone", zone.id)
            put("counts", buildJsonObject {
                put("notes", snapshot.notes.size); put("folders", snapshot.folders.size); put("tags", snapshot.tags.size)
                put("attachments", snapshot.attachments.size - missing.size); put("tasks", snapshot.tasks.size)
                put("templates", snapshot.templates.size); put("revisions", snapshot.revisions.size)
            })
            put("missingAttachments", buildJsonArray { missing.forEach { add(JsonPrimitive(it)) } })
            put("entries", buildJsonArray {
                checksums.forEach { (name, v) -> add(buildJsonObject { put("name", name); put("sha256", v.first); put("size", v.second) }) }
            })
        }
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(Json.encodeToString(JsonElement.serializer(), manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        zip.flush()
        return WriteResult(checksums.size + 1, total, missing)
    }

    class InvalidBackup(val reason: Reason, detail: String? = null) : Exception(detail ?: reason.name) {
        enum class Reason { NOT_A_BACKUP, NEWER_VERSION, MISSING_MANIFEST, CHECKSUM_MISMATCH, MISSING_ENTRY, TOO_LARGE, UNSAFE_PATH, MALFORMED, INCONSISTENT }
    }

    data class ReadResult(val snapshot: LibrarySnapshot, val createdAt: Instant?, val missingAttachments: List<String>)

    /**
     * Reads and fully verifies an archive. Attachment bytes are streamed to [stageAttachment]
     * (e.g. into a staging directory); the caller applies the snapshot and moves staged files only
     * after this returns successfully.
     */
    fun read(input: InputStream, stageAttachment: (id: String, data: InputStream) -> Unit): ReadResult {
        val zip = ZipInputStream(input)
        val computed = HashMap<String, Pair<String, Long>>()
        var manifestBytes: ByteArray? = null
        var libraryBytes: ByteArray? = null
        var entries = 0
        var total = 0L
        val staged = HashSet<String>()
        while (true) {
            val e = zip.nextEntry ?: break
            if (++entries > MAX_ENTRIES) throw InvalidBackup(InvalidBackup.Reason.TOO_LARGE, "too many entries")
            val name = e.name
            if (name.contains("..") || name.startsWith("/") || name.contains('\\') || name.contains('\u0000')) throw InvalidBackup(InvalidBackup.Reason.UNSAFE_PATH, name)
            if (e.isDirectory) continue
            val md = MessageDigest.getInstance("SHA-256")
            val counting = BoundedInput(DigestInputStream(NonClosing(zip), md), MAX_TOTAL_BYTES - total)
            when {
                name == "manifest.json" -> manifestBytes = counting.readCapped(MAX_JSON_BYTES)
                name == "library.json" -> libraryBytes = counting.readCapped(MAX_JSON_BYTES)
                name.startsWith("attachments/") -> {
                    val id = name.removePrefix("attachments/")
                    if (id.isEmpty() || id.contains('/')) throw InvalidBackup(InvalidBackup.Reason.UNSAFE_PATH, name)
                    stageAttachment(id, counting)
                    counting.drain()
                    staged += id
                }
                else -> counting.drain() // markdown copies and unknown entries are not needed for restore
            }
            total += counting.count
            if (name != "manifest.json") computed[name] = hex(md.digest()) to counting.count
        }
        val manifest = manifestBytes ?: throw InvalidBackup(if (entries == 0) InvalidBackup.Reason.NOT_A_BACKUP else InvalidBackup.Reason.MISSING_MANIFEST)
        val m = try { Json.parseToJsonElement(manifest.toString(Charsets.UTF_8)) as JsonObject } catch (e: Exception) { throw InvalidBackup(InvalidBackup.Reason.MALFORMED, "manifest") }
        if ((m["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) throw InvalidBackup(InvalidBackup.Reason.NOT_A_BACKUP)
        val v = (m["version"] as? JsonPrimitive)?.intOrNull ?: throw InvalidBackup(InvalidBackup.Reason.MALFORMED, "version")
        if (v > VERSION) throw InvalidBackup(InvalidBackup.Reason.NEWER_VERSION)
        val listed = (m["entries"] as? JsonArray)?.map { it as JsonObject } ?: throw InvalidBackup(InvalidBackup.Reason.MALFORMED, "entries")
        for (entry in listed) {
            val name = (entry["name"] as? JsonPrimitive)?.contentOrNull ?: throw InvalidBackup(InvalidBackup.Reason.MALFORMED)
            val sha = (entry["sha256"] as? JsonPrimitive)?.contentOrNull
            val size = (entry["size"] as? JsonPrimitive)?.longOrNull
            val actual = computed[name] ?: throw InvalidBackup(InvalidBackup.Reason.MISSING_ENTRY, name)
            if (actual.first != sha || actual.second != size) throw InvalidBackup(InvalidBackup.Reason.CHECKSUM_MISMATCH, name)
        }
        if (computed.keys.any { it.startsWith("attachments/") || it == "library.json" } &&
            computed.keys.filter { it.startsWith("attachments/") || it == "library.json" }.any { k -> listed.none { (it["name"] as? JsonPrimitive)?.contentOrNull == k } }
        ) throw InvalidBackup(InvalidBackup.Reason.INCONSISTENT, "unlisted entry")
        val lib = libraryBytes ?: throw InvalidBackup(InvalidBackup.Reason.MISSING_ENTRY, "library.json")
        val snapshot = try { LibraryJson.decode(lib.toString(Charsets.UTF_8)) } catch (e: LibraryJson.FormatException) { throw InvalidBackup(InvalidBackup.Reason.MALFORMED, e.message) }
        val problems = snapshot.integrityProblems()
        if (problems.isNotEmpty()) throw InvalidBackup(InvalidBackup.Reason.INCONSISTENT, problems.take(5).joinToString("; "))
        val missing = (m["missingAttachments"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        val notStaged = snapshot.attachments.map { it.id }.filter { it !in staged && it !in missing }
        if (notStaged.isNotEmpty()) throw InvalidBackup(InvalidBackup.Reason.MISSING_ENTRY, "attachments/${notStaged.first()}")
        return ReadResult(snapshot, (m["createdAt"] as? JsonPrimitive)?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() }, missing)
    }

    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private class NonClosing(s: InputStream) : FilterInputStream(s) { override fun close() {} }

    /** Counts bytes and refuses to read beyond [limit] (zip-bomb guard). */
    private class BoundedInput(s: InputStream, private val limit: Long) : FilterInputStream(s) {
        var count = 0L
        override fun read(): Int {
            val b = super.read()
            if (b >= 0) bump(1)
            return b
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) bump(n.toLong())
            return n
        }
        private fun bump(n: Long) {
            count += n
            if (count > limit) throw InvalidBackup(InvalidBackup.Reason.TOO_LARGE)
        }
        fun readCapped(max: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > max) throw InvalidBackup(InvalidBackup.Reason.TOO_LARGE)
            }
            return out.toByteArray()
        }
        fun drain() { val buf = ByteArray(64 * 1024); while (read(buf) >= 0) Unit }
        override fun close() {}
    }
}
