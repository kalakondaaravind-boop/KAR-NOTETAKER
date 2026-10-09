package app.margin.data.repo

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * App-private attachment storage (filesDir/attachments). Files are named by attachment id; the
 * original display name lives in the database. Writes go to a temp file first and are renamed
 * into place, so a crash never leaves a half-written attachment under its final name.
 */
class AttachmentFiles(private val root: File) {
    init { root.mkdirs() }

    class TooLarge : IOException("Attachment exceeds the size limit")

    data class Stored(val path: String, val size: Long, val sha256: String)

    fun file(path: String): File {
        val f = File(root, path)
        require(f.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "path escapes attachment directory" }
        return f
    }

    fun store(id: String, input: InputStream, maxBytes: Long): Stored {
        require(Regex("""[A-Za-z0-9-]{1,64}""").matches(id)) { "bad id" }
        val tmp = File(root, "$id.tmp")
        val md = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            input.use { src ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        size += n
                        if (size > maxBytes) throw TooLarge()
                        md.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                    out.fd.sync()
                }
            }
            val dest = File(root, id)
            if (!tmp.renameTo(dest)) throw IOException("could not move attachment into place")
            return Stored(id, size, md.digest().joinToString("") { "%02x".format(it) })
        } finally {
            tmp.delete()
        }
    }

    fun open(path: String): InputStream? = file(path).takeIf { it.isFile }?.inputStream()
    fun exists(path: String) = file(path).isFile
    fun delete(path: String) { runCatching { file(path).delete() } }

    fun sha256(path: String): String? = open(path)?.use { s ->
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Removes files no longer referenced by any attachment row (e.g. after an interrupted delete). */
    fun deleteOrphans(referenced: Set<String>): Int =
        root.listFiles()?.filter { it.isFile && it.name !in referenced }?.count { it.delete() } ?: 0

    fun totalBytes(): Long = root.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0
}
