package app.margin.platform

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import app.margin.domain.repository.AttachmentSource
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Attachment bytes from a content:// URI (file picker, share sheet, camera output). Streams; never buffers the whole file. */
class UriAttachmentSource(private val resolver: ContentResolver, private val uri: Uri, fallbackName: String = "attachment") : AttachmentSource {
    override val displayName: String
    override val sizeHint: Long?
    override val mimeType: String = resolver.getType(uri) ?: guessMime(uri.lastPathSegment.orEmpty())

    init {
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: fallbackName
        sizeHint = size
    }

    override fun open(): InputStream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    companion object {
        fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "gif" -> "image/gif"
            "pdf" -> "application/pdf"; "txt" -> "text/plain"; "md", "markdown" -> "text/markdown"
            "m4a" -> "audio/mp4"; "mp3" -> "audio/mpeg"; "ogg" -> "audio/ogg"; "html", "htm" -> "text/html"; "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }
}

class FileAttachmentSource(private val file: File, override val mimeType: String, override val displayName: String = file.name) : AttachmentSource {
    override val sizeHint: Long get() = file.length()
    override fun open(): InputStream = file.inputStream()
}

object Files {
    fun authority(context: Context) = context.packageName + ".files"

    /** A fresh, private file for the system camera to write into. */
    fun newCameraFile(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        return f to FileProvider.getUriForFile(context, authority(context), f)
    }

    /** Writes [content] to a cache file and returns a shareable content URI (deleted on next cache trim). */
    fun shareableText(context: Context, fileName: String, content: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
        val f = File(dir, fileName)
        f.writeText(content)
        return FileProvider.getUriForFile(context, authority(context), f)
    }

    fun attachmentUri(context: Context, storagePath: String): Uri =
        FileProvider.getUriForFile(context, authority(context), File(File(context.filesDir, "attachments"), storagePath))

    /** Sharing is always an explicit export of a copy; the note itself never becomes shared/collaborative. */
    fun share(context: Context, uri: Uri, mime: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareText(context: Context, text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, title)
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAttachment(context: Context, storagePath: String, mime: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(attachmentUri(context, storagePath), mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) { false }

    /** Reads at most [max] bytes; returns null if the file is larger or unreadable (never loads unbounded data). */
    fun readBounded(context: Context, uri: Uri, max: Int): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > max) return null
            }
            out.toByteArray()
        }
    } catch (e: Exception) { null }

    fun openUrl(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: ActivityNotFoundException) { false }
}

/**
 * On-device OCR with ML Kit's bundled Latin text recogniser. Images never leave the device.
 * The original image is kept as an attachment; recognised text is stored separately for search.
 */
object Ocr {
    suspend fun recognize(context: Context, uri: Uri): String = withContext(Dispatchers.Default) {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it.text) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            recognizer.close()
        }
    }
}

/** Simple voice-note recorder (AAC in MP4). The caller requests RECORD_AUDIO first. */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var file: File? = null
        private set

    fun start(): Boolean {
        val dir = File(context.cacheDir, "audio").apply { mkdirs() }
        val f = File(dir, "voice-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
            recorder = r; file = f
            true
        } catch (e: Exception) {
            r.release(); f.delete(); false
        }
    }

    /** Returns the recording, or null if it failed / was too short. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        return try { r.stop(); file } catch (e: RuntimeException) { file?.delete(); null } finally { r.release() }
    }

    fun cancel() { stop()?.delete() }
}
