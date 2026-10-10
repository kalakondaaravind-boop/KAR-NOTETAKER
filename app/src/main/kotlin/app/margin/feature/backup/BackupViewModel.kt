package app.margin.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.margin.BuildConfig
import app.margin.data.repo.AttachmentFiles
import app.margin.domain.io.BackupArchive
import app.margin.domain.io.BackupCrypto
import app.margin.domain.io.DuplicatePolicy
import app.margin.domain.io.FailReason
import app.margin.domain.io.HtmlImporter
import app.margin.domain.io.ImportCandidate
import app.margin.domain.io.ImportFailure
import app.margin.domain.io.ImportIssue
import app.margin.domain.io.ImportReport
import app.margin.domain.io.LibraryJson
import app.margin.domain.io.LibrarySnapshot
import app.margin.domain.io.MarkdownImporter
import app.margin.domain.io.NoteExportContext
import app.margin.domain.io.NoteExporter
import app.margin.domain.io.PlainTextImporter
import app.margin.domain.io.TextDecoding
import app.margin.domain.model.Clock
import app.margin.domain.organization.Folders
import app.margin.domain.repository.LibraryRepository
import app.margin.domain.repository.RestoreMode
import app.margin.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

sealed interface DataOp {
    data object Idle : DataOp
    data class Working(val label: Int) : DataOp
    data class Done(val message: Int, val detail: String? = null) : DataOp
    data class Failed(val message: Int, val detail: String? = null) : DataOp
    /** A verified backup is ready to apply; the user chooses Replace or Merge. */
    data class ReadyToRestore(val counts: String, val createdAt: String?, val missingAttachments: Int) : DataOp
    data object NeedsPassphrase : DataOp
    data class Imported(val report: ImportReport) : DataOp
}

enum class ExportKind { MARKDOWN_ZIP, HTML_ZIP, TEXT_ZIP, JSON }

/**
 * Backup, restore, import and export. All heavy work runs on Dispatchers.IO; restore verifies
 * the whole archive (checksums, structure, references) into a staging area before anything in
 * the library changes.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: LibraryRepository,
    private val search: SearchRepository,
    private val files: AttachmentFiles,
    private val clock: Clock,
    private val settings: app.margin.domain.settings.SettingsRepository,
) : ViewModel() {
    private val _op = MutableStateFlow<DataOp>(DataOp.Idle)
    val op: StateFlow<DataOp> = _op.asStateFlow()

    private var pendingRestore: Pair<LibrarySnapshot, Map<String, File>>? = null
    private var pendingUri: Uri? = null
    private val staging get() = File(context.cacheDir, "restore-staging")

    fun dismiss() { _op.value = DataOp.Idle }

    fun backup(target: Uri, passphrase: CharArray?) = viewModelScope.launch {
        _op.value = DataOp.Working(app.margin.R.string.backup_working)
        _op.value = try {
            withContext(Dispatchers.IO) {
                val snapshot = library.snapshot()
                val tmp = File(context.cacheDir, "backup-${System.nanoTime()}.zip")
                try {
                    FileOutputStream(tmp).use { out ->
                        BackupArchive.write(out, snapshot, { a -> files.open(a.storagePath) }, clock.now(), BuildConfig.VERSION_NAME, ZoneId.systemDefault())
                    }
                    // Verify the archive we just wrote can be read back before reporting success.
                    tmp.inputStream().use { BackupArchive.read(BufferedInputStream(it)) { _, _ -> } }
                    context.contentResolver.openOutputStream(target, "wt")!!.use { out ->
                        tmp.inputStream().use { input ->
                            if (passphrase != null && passphrase.isNotEmpty()) BackupCrypto.encrypt(input, out, passphrase) else input.copyTo(out)
                        }
                    }
                } finally {
                    tmp.delete()
                    passphrase?.fill('\u0000')
                }
                // Only a verified, fully written backup counts (drives the Home reminder).
                settings.update { app.margin.domain.settings.BackupHealth.recordBackup(it, clock.now()) }
                DataOp.Done(app.margin.R.string.backup_done, "${snapshot.notes.size} notes, ${snapshot.attachments.size} attachments")
            }
        } catch (e: Exception) {
            DataOp.Failed(app.margin.R.string.backup_failed, e.message)
        }
    }

    /** Step 1 of restore: read and verify. Encrypted backups ask for a passphrase first. */
    fun inspect(uri: Uri, passphrase: CharArray? = null) = viewModelScope.launch {
        pendingUri = uri
        _op.value = DataOp.Working(app.margin.R.string.restore_verifying)
        _op.value = try {
            withContext(Dispatchers.IO) {
                staging.deleteRecursively(); staging.mkdirs()
                val head = context.contentResolver.openInputStream(uri)!!.use { s -> ByteArray(8).also { b -> s.read(b) } }
                val plain = File(staging, "plain.zip")
                if (BackupCrypto.isEncrypted(head)) {
                    if (passphrase == null) return@withContext DataOp.NeedsPassphrase
                    try {
                        context.contentResolver.openInputStream(uri)!!.use { input -> FileOutputStream(plain).use { BackupCrypto.decrypt(BufferedInputStream(input), it, passphrase) } }
                    } finally { passphrase.fill('\u0000') }
                } else {
                    context.contentResolver.openInputStream(uri)!!.use { input -> FileOutputStream(plain).use { input.copyTo(it) } }
                }
                val staged = HashMap<String, File>()
                val result = plain.inputStream().use { input ->
                    BackupArchive.read(BufferedInputStream(input)) { id, data ->
                        val f = File(staging, "att-$id")
                        FileOutputStream(f).use { data.copyTo(it) }
                        staged[id] = f
                    }
                }
                plain.delete()
                pendingRestore = result.snapshot to staged
                val s = result.snapshot
                DataOp.ReadyToRestore("${s.notes.size} notes · ${s.folders.size} folders · ${s.tags.size} tags · ${s.tasks.size} tasks · ${s.attachments.size} attachments",
                    result.createdAt?.toString(), result.missingAttachments.size)
            }
        } catch (e: BackupCrypto.WrongPassphraseOrCorrupt) {
            DataOp.Failed(app.margin.R.string.restore_wrong_passphrase)
        } catch (e: BackupArchive.InvalidBackup) {
            DataOp.Failed(app.margin.R.string.restore_invalid, e.reason.name)
        } catch (e: Exception) {
            DataOp.Failed(app.margin.R.string.restore_invalid, e.message)
        }
    }

    fun retryWithPassphrase(p: CharArray) { pendingUri?.let { inspect(it, p) } }

    /** Step 2 of restore: apply in one transaction. */
    fun applyRestore(mode: RestoreMode) = viewModelScope.launch {
        val (snapshot, staged) = pendingRestore ?: return@launch
        _op.value = DataOp.Working(app.margin.R.string.restore_applying)
        _op.value = try {
            withContext(Dispatchers.IO) { library.restore(snapshot, mode, staged) }
            DataOp.Done(app.margin.R.string.restore_done)
        } catch (e: Exception) {
            DataOp.Failed(app.margin.R.string.restore_failed, e.message)
        } finally {
            pendingRestore = null
            withContext(Dispatchers.IO) { staging.deleteRecursively() }
        }
    }

    fun cancelRestore() = viewModelScope.launch {
        pendingRestore = null
        withContext(Dispatchers.IO) { staging.deleteRecursively() }
        _op.value = DataOp.Idle
    }

    fun export(target: Uri, kind: ExportKind) = viewModelScope.launch {
        _op.value = DataOp.Working(app.margin.R.string.export_working)
        _op.value = try {
            withContext(Dispatchers.IO) {
                val s = library.snapshot(includeRevisions = kind == ExportKind.JSON)
                context.contentResolver.openOutputStream(target, "wt")!!.use { out ->
                    when (kind) {
                        ExportKind.JSON -> out.write(LibraryJson.encode(s, clock.now(), BuildConfig.VERSION_NAME).toByteArray())
                        else -> ZipOutputStream(out).use { zip ->
                            val ext = when (kind) { ExportKind.HTML_ZIP -> "html"; ExportKind.TEXT_ZIP -> "txt"; else -> "md" }
                            val paths = NoteExporter.assignPaths(s.notes.filter { it.state.name != "TRASHED" }, s.folders, ext)
                            val tags = s.tags.associateBy { it.id }
                            val folders = s.folders.associateBy { it.id }
                            for (n in s.notes) {
                                val path = paths[n.id] ?: continue
                                val ctx = NoteExportContext(s.noteTags.filter { it.first == n.id }.mapNotNull { tags[it.second]?.name },
                                    Folders.path(n.folderId, folders).map { it.name }, s.attachments.filter { it.noteId == n.id }.map { it.fileName })
                                val content = when (kind) {
                                    ExportKind.HTML_ZIP -> NoteExporter.html(n, ctx, ZoneId.systemDefault())
                                    ExportKind.TEXT_ZIP -> NoteExporter.text(n)
                                    else -> NoteExporter.markdown(n, ctx)
                                }
                                zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry()
                            }
                            if (kind == ExportKind.MARKDOWN_ZIP) {
                                // Attachments travel with the Markdown export under _attachments/<note>/<file>.
                                for (a in s.attachments) {
                                    val notePath = paths[a.noteId] ?: continue
                                    val input = files.open(a.storagePath) ?: continue
                                    zip.putNextEntry(ZipEntry("_attachments/" + notePath.substringBeforeLast('.') + "/" + NoteExporter.safeFileName(a.fileName, 120)))
                                    input.use { it.copyTo(zip) }
                                    zip.closeEntry()
                                }
                            }
                        }
                    }
                }
                DataOp.Done(app.margin.R.string.export_done, "${s.notes.size} notes")
            }
        } catch (e: Exception) {
            DataOp.Failed(app.margin.R.string.export_failed, e.message)
        }
    }

    /** Imports Markdown, text and HTML files (and Margin JSON exports, merged without overwriting). */
    fun import(uris: List<Uri>, policy: DuplicatePolicy) = viewModelScope.launch {
        _op.value = DataOp.Working(app.margin.R.string.import_working)
        _op.value = try {
            withContext(Dispatchers.IO) {
                val candidates = ArrayList<ImportCandidate>()
                val issues = ArrayList<ImportIssue>()
                var jsonRestored = 0
                for (uri in uris.take(app.margin.domain.io.ImportLimits.MAX_FILES)) {
                    val src = app.margin.platform.UriAttachmentSource(context.contentResolver, uri)
                    val name = src.displayName
                    val ext = name.substringAfterLast('.', "").lowercase()
                    val bytes = app.margin.platform.Files.readBounded(context, uri, app.margin.domain.io.ImportLimits.MAX_TEXT_BYTES.toInt())
                    if (bytes == null) { issues += ImportIssue.Failed(name, FailReason.TOO_LARGE); continue }
                    val text = TextDecoding.decode(bytes).getOrElse { e ->
                        issues += ImportIssue.Failed(name, (e as? ImportFailure)?.reason ?: FailReason.ENCODING); null
                    } ?: continue
                    when {
                        ext == "json" -> try {
                            val snapshot = LibraryJson.decode(text)
                            library.restore(snapshot, RestoreMode.MERGE, emptyMap())
                            jsonRestored += snapshot.notes.size
                        } catch (e: Exception) { issues += ImportIssue.Failed(name, FailReason.MALFORMED, e.message) }
                        ext == "md" || ext == "markdown" -> candidates += MarkdownImporter.parse(name, text)
                        ext == "txt" || ext == "text" || src.mimeType == "text/plain" -> candidates += PlainTextImporter.parse(name, text)
                        ext == "html" || ext == "htm" || src.mimeType == "text/html" -> candidates += HtmlImporter.parse(name, text)
                        else -> issues += ImportIssue.Failed(name, FailReason.UNSUPPORTED_TYPE)
                    }
                }
                val report = library.importNotes(candidates, policy)
                DataOp.Imported(ImportReport(report.imported + jsonRestored, issues + report.issues))
            }
        } catch (e: Exception) {
            DataOp.Failed(app.margin.R.string.import_failed, e.message)
        }
    }

    fun rebuildIndex() = viewModelScope.launch {
        _op.value = DataOp.Working(app.margin.R.string.index_rebuilding)
        _op.value = try { val n = search.rebuildIndex(); DataOp.Done(app.margin.R.string.index_rebuilt, "$n notes") }
        catch (e: Exception) { DataOp.Failed(app.margin.R.string.something_went_wrong, e.message) }
    }

    fun wipe() = viewModelScope.launch {
        _op.value = DataOp.Working(app.margin.R.string.wiping)
        _op.value = try { library.wipeAll(); DataOp.Done(app.margin.R.string.wiped) } catch (e: Exception) { DataOp.Failed(app.margin.R.string.something_went_wrong, e.message) }
    }
}
