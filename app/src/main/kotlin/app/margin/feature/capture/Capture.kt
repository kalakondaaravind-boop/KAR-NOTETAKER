@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.feature.capture

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.RemoteViews
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.MainActivity
import app.margin.MarginApp
import app.margin.R
import app.margin.domain.model.Folder
import app.margin.domain.model.NoteSummary
import app.margin.domain.organization.NoteFilter
import app.margin.domain.organization.SortField
import app.margin.domain.organization.SortOrder
import app.margin.domain.repository.AttachmentRepository
import app.margin.domain.repository.FolderRepository
import app.margin.domain.repository.NoteRepository
import app.margin.domain.repository.TagRepository
import app.margin.domain.settings.SettingsRepository
import app.margin.feature.lock.AppLockManager
import app.margin.feature.lock.LockScreen
import app.margin.platform.UriAttachmentSource
import app.margin.ui.components.FolderPickerDialog
import app.margin.ui.components.TagEditorDialog
import app.margin.ui.theme.MarginTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Parsed share intent. Malformed or empty shares produce [isEmpty] = true and are explained to the user. */
data class SharedContent(val subject: String, val text: String, val uris: List<Uri>) {
    val isEmpty get() = text.isBlank() && uris.isEmpty()

    companion object {
        fun from(intent: Intent?): SharedContent {
            if (intent == null) return SharedContent("", "", emptyList())
            val text = runCatching { intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() }.getOrNull().orEmpty().take(1_000_000)
            val subject = runCatching { intent.getStringExtra(Intent.EXTRA_SUBJECT) }.getOrNull().orEmpty().take(300)
            val uris = runCatching {
                when (intent.action) {
                    Intent.ACTION_SEND_MULTIPLE -> parcelableList(intent)
                    else -> listOfNotNull(parcelable(intent))
                }
            }.getOrDefault(emptyList()).filter { it.scheme == "content" }.take(50) // file:// shares are rejected (no permission model)
            return SharedContent(subject, text, uris)
        }

        @Suppress("DEPRECATION")
        private fun parcelable(i: Intent): Uri? =
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else i.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri

        @Suppress("DEPRECATION")
        private fun parcelableList(i: Intent): List<Uri> =
            if (Build.VERSION.SDK_INT >= 33) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else i.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM).orEmpty().mapNotNull { it as? Uri }
    }
}

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val notes: NoteRepository,
    private val attachments: AttachmentRepository,
    folders: FolderRepository,
    tags: TagRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    val folderList: StateFlow<List<Folder>> = folders.observeFolders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tagNames: StateFlow<List<String>> = tags.observeTags().map { l -> l.map { it.tag.name } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recent: StateFlow<List<NoteSummary>> = notes.observeNotes(NoteFilter(), SortOrder(SortField.UPDATED), 8).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class Result(val noteId: String, val failedAttachments: Int)

    /**
     * Saves locally only — shared content is never uploaded anywhere. New note by default; appending
     * to an existing note happens only when the user explicitly picks one.
     */
    fun save(
        context: Context, content: SharedContent, title: String, body: String, folderId: String?, tagList: List<String>,
        appendTo: String?, done: (Result) -> Unit,
    ) = viewModelScope.launch {
        val id = if (appendTo != null) {
            val n = notes.getNote(appendTo) ?: return@launch
            notes.saveContent(n.id, n.title, n.body.trimEnd() + "\n\n" + body.trim() + "\n", n.lastCursor)
            if (tagList.isNotEmpty()) notes.addTags(listOf(n.id), tagList)
            n.id
        } else {
            notes.create(title = title.trim(), body = body.trim(), folderId = folderId ?: settings.current().defaultFolderId, tags = tagList).id
        }
        var failed = 0
        val converted = StringBuilder()
        for (uri in content.uris) {
            val src = UriAttachmentSource(context.contentResolver, uri)
            val r = attachments.add(id, src)
            if (r.isFailure) { failed++; continue }
            // Word and PDF files are also turned into editable note text (the original stays attached).
            val name = src.displayName
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext == "docx" || ext == "pdf") runCatching {
                val bytes = app.margin.platform.Files.readBounded(context, uri, 50 * 1024 * 1024) ?: return@runCatching
                val md = if (ext == "docx") app.margin.domain.io.DocxImporter.parse(name, bytes).candidate.body
                    else app.margin.platform.PdfImport.parse(context, name, bytes).body
                if (md.isNotBlank()) converted.append("\n\n").append(md.trim())
            }
        }
        if (converted.isNotBlank()) notes.getNote(id)?.let { n -> notes.saveContent(n.id, n.title, n.body.trimEnd() + converted + "\n", n.lastCursor) }
        done(Result(id, failed))
    }
}

private fun isUrl(s: String) = Regex("""^https?://\S+$""").matches(s.trim())

/** Quick save / edit sheet for content shared from other apps. */
@AndroidEntryPoint
class ShareActivity : FragmentActivity() {
    private val vm: ShareViewModel by viewModels()
    @Inject lateinit var lock: AppLockManager
    @Inject lateinit var settingsRepo: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lock.register()
        val content = SharedContent.from(intent)
        setContent {
            val s by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = null)
            val settings = s ?: return@setContent
            MarginTheme(settings.theme, settings.dynamicColor, settings.glossyStyle) {
                val locked by lock.locked.collectAsStateWithLifecycle()
                var authError by rememberSaveable { mutableStateOf<String?>(null) }
                if (settings.lock.enabled && locked) {
                    LockScreen(this, onUnlocked = { lock.onAuthenticated() }, error = authError, onError = { authError = it })
                } else ShareSheet(content, vm, onDone = { finish() }, onOpen = { id ->
                    startActivity(Intent(this, MainActivity::class.java).setAction(MarginApp.ACTION_OPEN_NOTE).putExtra(MarginApp.EXTRA_NOTE_ID, id))
                    finish()
                })
            }
        }
    }
}

@Composable
private fun ShareSheet(content: SharedContent, vm: ShareViewModel, onDone: () -> Unit, onOpen: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val folders by vm.folderList.collectAsStateWithLifecycle()
    val allTags by vm.tagNames.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val initialBody = buildString {
        val t = content.text.trim()
        if (isUrl(t)) append("[").append(content.subject.ifBlank { t }).append("](").append(t).append(")") else append(t)
    }
    var title by rememberSaveable { mutableStateOf(content.subject.ifBlank { app.margin.domain.model.Note.derivedTitle(content.text)?.take(80).orEmpty() }) }
    var body by rememberSaveable { mutableStateOf(initialBody) }
    var folderId by rememberSaveable { mutableStateOf<String?>(null) }
    var tagList by rememberSaveable { mutableStateOf(listOf<String>()) }
    var appendTo by rememberSaveable { mutableStateOf<String?>(null) }
    var folderDialog by rememberSaveable { mutableStateOf(false) }
    var tagDialog by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.share_title), style = MaterialTheme.typography.titleLarge)
            if (content.isEmpty) {
                Text(stringResource(R.string.share_empty), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDone) { Text(stringResource(R.string.close)) }
                return@Column
            }
            Text(stringResource(R.string.share_local_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (appendTo == null) OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.note_title)) }, singleLine = true)
            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), label = { Text(stringResource(R.string.note_body)) })
            if (content.uris.isNotEmpty()) Text(context.resources.getQuantityString(R.plurals.share_attachments, content.uris.size, content.uris.size), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (appendTo == null) AssistChip(onClick = { folderDialog = true }, label = { Text(folders.firstOrNull { it.id == folderId }?.name ?: stringResource(R.string.inbox)) })
                AssistChip(onClick = { tagDialog = true }, label = { Text(if (tagList.isEmpty()) stringResource(R.string.add_tags) else tagList.joinToString(" ") { "#$it" }) })
            }
            Text(stringResource(R.string.share_append_to), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(appendTo == null, { appendTo = null }, label = { Text(stringResource(R.string.share_new_note)) })
                recent.take(6).forEach { r -> FilterChip(appendTo == r.id, { appendTo = r.id }, label = { Text(r.title.ifBlank { stringResource(R.string.untitled) }.take(24)) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !saving, onClick = {
                    saving = true
                    vm.save(context, content, title, body, folderId, tagList, appendTo) { onDone() }
                }) { Text(stringResource(R.string.save)) }
                OutlinedButton(enabled = !saving, onClick = {
                    saving = true
                    vm.save(context, content, title, body, folderId, tagList, appendTo) { r -> onOpen(r.noteId) }
                }) { Text(stringResource(R.string.save_and_open)) }
                TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
    if (folderDialog) FolderPickerDialog(folders, stringResource(R.string.move_to), onPick = { folderId = it; folderDialog = false }, onDismiss = { folderDialog = false })
    if (tagDialog) TagEditorDialog(tagList, allTags, onSave = { tagList = it; tagDialog = false }, onDismiss = { tagDialog = false })
}

/** Home-screen widget: new note, new checklist, search. Uses dynamic actions so it targets this build's package. */
class QuickCaptureWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        fun pi(action: String, code: Int) = PendingIntent.getActivity(context, code,
            Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val views = RemoteViews(context.packageName, R.layout.widget_quick_capture).apply {
            setOnClickPendingIntent(R.id.widget_new_note, pi(MarginApp.ACTION_NEW_NOTE, 1))
            setOnClickPendingIntent(R.id.widget_checklist, pi(MarginApp.ACTION_NEW_CHECKLIST, 2))
            setOnClickPendingIntent(R.id.widget_search, pi(MarginApp.ACTION_SEARCH, 3))
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
