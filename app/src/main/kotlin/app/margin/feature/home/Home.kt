@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import app.margin.platform.rememberDictation
import app.margin.ui.theme.GlossTone
import app.margin.ui.theme.GlossyExtendedFab
import app.margin.ui.theme.GlossyPanel
import app.margin.ui.theme.glossStyle
import app.margin.ui.theme.glossy
import app.margin.ui.theme.glossyBarBrush
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.model.Clock
import app.margin.domain.model.Folder
import app.margin.domain.model.NoteSummary
import app.margin.domain.model.TaskItem
import app.margin.domain.organization.NoteFilter
import app.margin.domain.organization.SmartCollection
import app.margin.domain.organization.SortField
import app.margin.domain.organization.SortOrder
import app.margin.domain.repository.FolderRepository
import app.margin.domain.repository.LibraryRepository
import app.margin.domain.repository.NoteRepository
import app.margin.domain.repository.TaskRepository
import app.margin.domain.repository.TemplateRepository
import app.margin.domain.settings.BackupHealth
import app.margin.domain.settings.HomeSection
import app.margin.domain.settings.SettingsRepository
import app.margin.domain.task.TaskBucket
import app.margin.domain.task.TaskRules
import app.margin.domain.template.TemplateDefinition
import app.margin.feature.ai.BrandLockupWithAiUnlock
import app.margin.ui.components.EmptyState
import app.margin.ui.components.NoteRow
import app.margin.ui.components.SectionHeader
import app.margin.ui.navigation.Collection
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import app.margin.ui.theme.LocalSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class HomeState(
    val loading: Boolean = true,
    val sections: List<HomeSection> = emptyList(),
    val pinned: List<NoteSummary> = emptyList(),
    val recent: List<NoteSummary> = emptyList(),
    val favorites: List<NoteSummary> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val templates: List<TemplateDefinition> = emptyList(),
    val isLibraryEmpty: Boolean = false,
    /** Settings → Capture: show a paste button that reads the clipboard only when tapped. */
    val clipboardButton: Boolean = false,
    /** Everything is stored only on this tablet, so Home nudges when there is no recent backup. */
    val backup: BackupHealth.Status = BackupHealth.Status.OK,
    val lastBackupAt: java.time.Instant? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notes: NoteRepository,
    folders: FolderRepository,
    private val tasks: TaskRepository,
    templates: TemplateRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
    library: LibraryRepository,
) : ViewModel() {
    private val now = clock.now()

    private val noteLists = combine(
        notes.observeNotes(NoteFilter(pinned = true), SortOrder(SortField.UPDATED), 10),
        notes.observeNotes(SmartCollection.RECENTLY_EDITED.filter(now).copy(updatedAfter = null), SortOrder(SortField.UPDATED), 12),
        notes.observeNotes(NoteFilter(favorite = true), SortOrder(SortField.UPDATED), 10),
    ) { p, r, f -> Triple(p, r, f) }

    private val recentTemplates = combine(templates.observeTemplates(), templates.observeRecentlyUsed()) { all, recent ->
        val byId = all.associateBy { it.id }
        recent.mapNotNull { byId[it] }.take(6)
    }

    val state: StateFlow<HomeState> = combine(
        noteLists, tasks.observeOpenTasks(), folders.observeFolders(), recentTemplates,
        combine(settings.settings, library.observeCounts()) { s, c -> s to c },
    ) { (pinned, recent, favs), openTasks, folderList, tpl, (s, counts) ->
        val zone = ZoneId.systemDefault()
        val n = clock.now()
        HomeState(
            loading = false,
            sections = s.visibleHomeSections,
            pinned = pinned,
            // Recent excludes pinned notes so the same note isn't listed twice.
            recent = recent.filterNot { it.pinned }.take(8),
            favorites = favs.filterNot { it.pinned }.take(6),
            tasks = openTasks.filter { TaskRules.bucket(it, n, zone) in setOf(TaskBucket.OVERDUE, TaskBucket.TODAY, TaskBucket.UPCOMING) }.take(6),
            folders = folderList.filter { it.parentId == null }.take(8),
            templates = tpl,
            isLibraryEmpty = pinned.isEmpty() && recent.isEmpty(),
            clipboardButton = s.clipboardCaptureSuggestion,
            backup = BackupHealth.status(s, counts.active + counts.archived, n),
            lastBackupAt = s.lastBackupAt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Quick capture: saves immediately as a note in the default folder. Returns via callback once durable. */
    fun quickCapture(text: String, onSaved: (String) -> Unit) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        val s = settings.current()
        val n = notes.create(body = text.trim(), folderId = s.defaultFolderId)
        onSaved(n.id)
    }

    fun newNote(body: String = "", onCreated: (String) -> Unit) = viewModelScope.launch {
        val s = settings.current()
        onCreated(notes.create(body = body, folderId = s.defaultFolderId).id)
    }

    /** Completing from Home is undoable (snackbar), so a mis-tap never loses a task. */
    fun completeTask(id: String) = viewModelScope.launch { tasks.complete(id) }
    fun reopenTask(id: String) = viewModelScope.launch { tasks.reopen(id) }
    fun snoozeBackup() = viewModelScope.launch { settings.update { BackupHealth.snooze(it, clock.now()) } }
}

@Composable
fun HomeScreen(n: Navigator, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sp = LocalSpacing.current
    var capture by rememberSaveable { mutableStateOf("") }
    var savedMessage by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val taskDone = stringResource(R.string.task_completed)
    val undo = stringResource(R.string.undo)
    val panel = Modifier.widthIn(max = sp.readingWidth).fillMaxWidth().padding(horizontal = sp.l, vertical = sp.s)
    val noSpeech = stringResource(R.string.speech_unavailable)
    val dictationPrompt = stringResource(R.string.dictation_prompt)
    // Mic in quick capture: transcript goes into the field so it can be checked before saving.
    val dictateIntoCapture = rememberDictation(dictationPrompt,
        onText = { capture = if (capture.isBlank()) it else capture.trimEnd() + " " + it; savedMessage = false },
        onUnavailable = { scope.launch { snackbar.showSnackbar(noSpeech) } })
    // "Voice note" tile: transcript becomes a new note, opened in the editor to review and continue.
    val dictateNewNote = rememberDictation(dictationPrompt,
        onText = { text -> vm.newNote(text) { n.open(Routes.editor(it)) } },
        onUnavailable = { scope.launch { snackbar.showSnackbar(noSpeech) } })

    @Composable
    fun NotesPanel(title: String, notes: List<NoteSummary>, seeAll: String) = GlossyPanel(panel) {
        SectionHeader(title) { TextButton(onClick = { n.open(seeAll) }) { Text(stringResource(R.string.see_all)) } }
        notes.forEachIndexed { i, note ->
            if (i > 0) PanelDivider()
            NoteRow(note, false, false, { n.open(Routes.editor(note.id)) }, {}, containerColor = Color.Transparent)
        }
        Spacer(Modifier.size(sp.xs))
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                expandedHeight = 80.dp,
                modifier = Modifier.background(glossyBarBrush()),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { BrandLockupWithAiUnlock(markSize = 30.dp) },
                navigationIcon = { IconButton(onClick = n::openDrawer) { Icon(Icons.Outlined.Menu, stringResource(R.string.open_library)) } },
                actions = {
                    IconButton(onClick = { n.open(Routes.search()) }) { Icon(Icons.Outlined.Search, stringResource(R.string.nav_search)) }
                    IconButton(onClick = { n.open(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            GlossyExtendedFab(
                onClick = { vm.newNote { n.open(Routes.editor(it)) } },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_note)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = sp.s, bottom = 112.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.backup != BackupHealth.Status.OK) item(key = "backup") {
                BackupBanner(state.backup, state.lastBackupAt, panel, onBackup = { n.open(Routes.SETTINGS_DATA) }, onLater = vm::snoozeBackup)
            }
            for (section in state.sections) {
                when (section) {
                    HomeSection.QUICK_CAPTURE -> item(key = "capture") {
                        GlossyPanel(panel) {
                            Row(Modifier.fillMaxWidth().padding(start = sp.l, end = sp.s, top = sp.l, bottom = sp.s), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = capture, onValueChange = { capture = it; savedMessage = false },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text(stringResource(R.string.quick_capture_hint)) },
                                    maxLines = 4,
                                    shape = MaterialTheme.shapes.medium,
                                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { vm.quickCapture(capture) { capture = ""; savedMessage = true } }),
                                    supportingText = if (savedMessage) { { Text(stringResource(R.string.quick_capture_saved)) } } else null,
                                )
                                if (state.clipboardButton) {
                                    // Reads the clipboard only on this explicit tap (no background access).
                                    IconButton(onClick = { clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { capture = if (capture.isBlank()) it else capture + "\n" + it; savedMessage = false } }) {
                                        Icon(Icons.Outlined.ContentPaste, stringResource(R.string.paste_from_clipboard))
                                    }
                                }
                                IconButton(onClick = dictateIntoCapture) { Icon(Icons.Outlined.Mic, stringResource(R.string.dictate)) }
                                IconButton(onClick = { vm.quickCapture(capture) { capture = ""; savedMessage = true } }, enabled = capture.isNotBlank()) {
                                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.quick_capture_save))
                                }
                            }
                            QuickActions(
                                onNewNote = { vm.newNote { n.open(Routes.editor(it)) } },
                                onVoiceNote = dictateNewNote,
                                onChecklist = { vm.newNote("- [ ] ") { n.open(Routes.editor(it)) } },
                                onOpen = n::open,
                            )
                        }
                    }
                    HomeSection.PINNED -> if (state.pinned.isNotEmpty()) item(key = "pinned") {
                        NotesPanel(stringResource(R.string.pinned), state.pinned, Routes.notes(Collection.Pinned))
                    }
                    HomeSection.RECENT -> item(key = "recent") {
                        if (!state.loading && state.isLibraryEmpty) {
                            GlossyPanel(panel) {
                                SectionHeader(stringResource(R.string.recent))
                                EmptyState(Icons.Outlined.EditNote, stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_body),
                                    stringResource(R.string.browse_templates), { n.open(Routes.templates()) }, Modifier.padding(vertical = sp.s))
                            }
                        } else if (state.recent.isNotEmpty()) {
                            NotesPanel(stringResource(R.string.recent), state.recent, Routes.notes(Collection.All))
                        }
                    }
                    HomeSection.FAVORITES -> if (state.favorites.isNotEmpty()) item(key = "favorites") {
                        NotesPanel(stringResource(R.string.favorites), state.favorites, Routes.notes(Collection.Favorites))
                    }
                    HomeSection.TASKS -> if (state.tasks.isNotEmpty()) item(key = "tasks") {
                        GlossyPanel(panel) {
                            SectionHeader(stringResource(R.string.upcoming_tasks)) { TextButton(onClick = { n.open(Routes.TASKS) }) { Text(stringResource(R.string.see_all)) } }
                            state.tasks.forEachIndexed { i, t ->
                                if (i > 0) PanelDivider()
                                app.margin.feature.tasks.TaskRow(t, containerColor = Color.Transparent, onToggle = {
                                    vm.completeTask(t.id)
                                    scope.launch {
                                        if (snackbar.showSnackbar(taskDone, actionLabel = undo, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.reopenTask(t.id)
                                    }
                                }, onClick = { n.open(Routes.TASKS) })
                            }
                            Spacer(Modifier.size(sp.xs))
                        }
                    }
                    HomeSection.FOLDERS -> if (state.folders.isNotEmpty()) item(key = "folders") {
                        GlossyPanel(panel) {
                            SectionHeader(stringResource(R.string.folders)) { TextButton(onClick = { n.open(Routes.FOLDERS) }) { Text(stringResource(R.string.see_all)) } }
                            FlowRow(Modifier.fillMaxWidth().padding(start = sp.l, end = sp.l, bottom = sp.m), horizontalArrangement = Arrangement.spacedBy(sp.s)) {
                                state.folders.forEach { f ->
                                    AssistChip(onClick = { n.open(Routes.notes(Collection.InFolder(f.id))) }, label = { Text(f.name) },
                                        leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) })
                                }
                            }
                        }
                    }
                    HomeSection.TEMPLATES -> if (state.templates.isNotEmpty()) item(key = "templates") {
                        GlossyPanel(panel) {
                            SectionHeader(stringResource(R.string.recent_templates)) { TextButton(onClick = { n.open(Routes.templates()) }) { Text(stringResource(R.string.see_all)) } }
                            FlowRow(Modifier.fillMaxWidth().padding(start = sp.l, end = sp.l, bottom = sp.m), horizontalArrangement = Arrangement.spacedBy(sp.s)) {
                                state.templates.forEach { t ->
                                    AssistChip(onClick = { n.open(Routes.templateForm(t.id)) }, label = { Text(t.name) },
                                        leadingIcon = { Icon(Icons.Outlined.Dashboard, null, Modifier.size(18.dp)) })
                                }
                            }
                        }
                    }
                    HomeSection.ATTACHMENTS -> item(key = "attachments") {
                        GlossyPanel(panel) {
                            SectionHeader(stringResource(R.string.attachments)) { TextButton(onClick = { n.open(Routes.ATTACHMENTS) }) { Text(stringResource(R.string.see_all)) } }
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.size(sp.xl)) }
        }
    }
}

/** Data-protection nudge: shown only when there is something to lose and no recent backup. */
@Composable
private fun BackupBanner(status: BackupHealth.Status, last: java.time.Instant?, modifier: Modifier, onBackup: () -> Unit, onLater: () -> Unit) {
    val style = glossStyle(GlossTone.PASTEL)
    val sp = LocalSpacing.current
    Row(
        modifier.glossy(style, MaterialTheme.shapes.large, elevation = 4.dp, flat = MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = sp.l, vertical = sp.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.m),
    ) {
        Icon(Icons.Outlined.Backup, null, tint = style?.content ?: MaterialTheme.colorScheme.onPrimaryContainer)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.backup_banner_title), style = MaterialTheme.typography.titleSmall, color = style?.content ?: MaterialTheme.colorScheme.onPrimaryContainer)
            val whenText = last?.let { java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it) }
            Text(
                if (status == BackupHealth.Status.NEVER_BACKED_UP || whenText == null) stringResource(R.string.backup_banner_never) else stringResource(R.string.backup_banner_overdue, whenText),
                style = MaterialTheme.typography.bodySmall, color = style?.content ?: MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        TextButton(onClick = onLater) { Text(stringResource(R.string.later)) }
        app.margin.ui.theme.GlossyButton(onClick = onBackup) { Text(stringResource(R.string.backup_now)) }
    }
}

@Composable
private fun PanelDivider() = HorizontalDivider(
    Modifier.padding(horizontal = LocalSpacing.current.l),
    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
)

/**
 * One-tap starts for the most frequent jobs (consultant recommendation: cut time-to-first-note).
 * Template tiles open the built-in templates by their stable ids.
 */
@Composable
private fun QuickActions(onNewNote: () -> Unit, onVoiceNote: () -> Unit, onChecklist: () -> Unit, onOpen: (String) -> Unit) {
    val sp = LocalSpacing.current
    FlowRow(
        Modifier.fillMaxWidth().padding(start = sp.l, end = sp.l, top = sp.xs, bottom = sp.l),
        horizontalArrangement = Arrangement.spacedBy(sp.s),
        verticalArrangement = Arrangement.spacedBy(sp.s),
    ) {
        QuickTile(Icons.Outlined.EditNote, R.string.new_note, onNewNote)
        QuickTile(Icons.Outlined.Mic, R.string.voice_note, onVoiceNote)
        QuickTile(Icons.Outlined.Checklist, R.string.shortcut_new_checklist, onChecklist)
        QuickTile(Icons.Outlined.Groups, R.string.qa_meeting) { onOpen(Routes.templateForm(QUICK_MEETING_TEMPLATE)) }
        QuickTile(Icons.Outlined.Today, R.string.qa_journal) { onOpen(Routes.templateForm(QUICK_JOURNAL_TEMPLATE)) }
        QuickTile(Icons.Outlined.AutoAwesome, R.string.ask_notes) { onOpen(Routes.ASK) }
        QuickTile(Icons.Outlined.Dashboard, R.string.templates) { onOpen(Routes.templates()) }
    }
}

const val QUICK_MEETING_TEMPLATE = "builtin.meeting-notes"
const val QUICK_JOURNAL_TEMPLATE = "builtin.daily-journal"

@Composable
private fun QuickTile(icon: ImageVector, label: Int, onClick: () -> Unit) {
    val tile = glossStyle(GlossTone.PASTEL)
    val badge = glossStyle(GlossTone.PRIMARY)
    Column(
        Modifier
            .width(86.dp)
            .glossy(tile, MaterialTheme.shapes.medium, elevation = 4.dp, flat = MaterialTheme.colorScheme.secondaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(40.dp).glossy(badge, CircleShape, elevation = 3.dp, flat = MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = badge?.content ?: MaterialTheme.colorScheme.onPrimary)
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
            color = tile?.content ?: MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
