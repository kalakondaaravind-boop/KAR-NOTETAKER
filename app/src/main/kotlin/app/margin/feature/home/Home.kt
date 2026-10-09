@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import app.margin.domain.repository.NoteRepository
import app.margin.domain.repository.TaskRepository
import app.margin.domain.repository.TemplateRepository
import app.margin.domain.settings.HomeSection
import app.margin.domain.settings.SettingsRepository
import app.margin.domain.task.TaskBucket
import app.margin.domain.task.TaskRules
import app.margin.domain.template.TemplateDefinition
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val notes: NoteRepository,
    folders: FolderRepository,
    tasks: TaskRepository,
    templates: TemplateRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
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
        noteLists, tasks.observeOpenTasks(), folders.observeFolders(), recentTemplates, settings.settings,
    ) { (pinned, recent, favs), openTasks, folderList, tpl, s ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** Quick capture: saves immediately as a note in the default folder. Returns via callback once durable. */
    fun quickCapture(text: String, onSaved: (String) -> Unit) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        val s = settings.current()
        val n = notes.create(body = text.trim(), folderId = s.defaultFolderId)
        onSaved(n.id)
    }

    fun newNote(onCreated: (String) -> Unit) = viewModelScope.launch {
        val s = settings.current()
        onCreated(notes.create(folderId = s.defaultFolderId).id)
    }
}

@Composable
fun HomeScreen(n: Navigator, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sp = LocalSpacing.current
    var capture by rememberSaveable { mutableStateOf("") }
    var savedMessage by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = { IconButton(onClick = n::openDrawer) { Icon(Icons.Outlined.Menu, stringResource(R.string.open_library)) } },
                actions = {
                    IconButton(onClick = { n.open(Routes.search()) }) { Icon(Icons.Outlined.Search, stringResource(R.string.nav_search)) }
                    IconButton(onClick = { n.open(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.newNote { n.open(Routes.editor(it)) } },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.new_note)) },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (section in state.sections) {
                when (section) {
                    HomeSection.QUICK_CAPTURE -> item(key = "capture") {
                        Row(Modifier.widthIn(max = sp.readingWidth).fillMaxWidth().padding(horizontal = sp.l, vertical = sp.s), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = capture, onValueChange = { capture = it; savedMessage = false },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(stringResource(R.string.quick_capture_hint)) },
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { vm.quickCapture(capture) { capture = ""; savedMessage = true } }),
                                supportingText = if (savedMessage) { { Text(stringResource(R.string.quick_capture_saved)) } } else null,
                            )
                            IconButton(onClick = { vm.quickCapture(capture) { capture = ""; savedMessage = true } }, enabled = capture.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.quick_capture_save))
                            }
                        }
                    }
                    HomeSection.PINNED -> if (state.pinned.isNotEmpty()) {
                        item(key = "pinned-h") { SectionHeader(stringResource(R.string.pinned)) { TextButton(onClick = { n.open(Routes.notes(Collection.Pinned)) }) { Text(stringResource(R.string.see_all)) } } }
                        items(state.pinned, key = { "p" + it.id }) { note -> NoteRow(note, false, false, { n.open(Routes.editor(note.id)) }, {}) }
                    }
                    HomeSection.RECENT -> {
                        item(key = "recent-h") { SectionHeader(stringResource(R.string.recent)) { TextButton(onClick = { n.open(Routes.notes(Collection.All)) }) { Text(stringResource(R.string.see_all)) } } }
                        if (!state.loading && state.isLibraryEmpty) {
                            item(key = "empty") {
                                EmptyState(Icons.Outlined.EditNote, stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_body),
                                    stringResource(R.string.browse_templates), { n.open(Routes.templates()) }, Modifier.padding(vertical = sp.l))
                            }
                        }
                        items(state.recent, key = { "r" + it.id }) { note -> NoteRow(note, false, false, { n.open(Routes.editor(note.id)) }, {}) }
                    }
                    HomeSection.FAVORITES -> if (state.favorites.isNotEmpty()) {
                        item(key = "fav-h") { SectionHeader(stringResource(R.string.favorites)) { TextButton(onClick = { n.open(Routes.notes(Collection.Favorites)) }) { Text(stringResource(R.string.see_all)) } } }
                        items(state.favorites, key = { "f" + it.id }) { note -> NoteRow(note, false, false, { n.open(Routes.editor(note.id)) }, {}) }
                    }
                    HomeSection.TASKS -> if (state.tasks.isNotEmpty()) {
                        item(key = "tasks-h") { SectionHeader(stringResource(R.string.upcoming_tasks)) { TextButton(onClick = { n.open(Routes.TASKS) }) { Text(stringResource(R.string.see_all)) } } }
                        items(state.tasks, key = { "t" + it.id }) { t ->
                            app.margin.feature.tasks.TaskRow(t, onToggle = { n.open(Routes.TASKS) }, onClick = { n.open(Routes.TASKS) })
                        }
                    }
                    HomeSection.FOLDERS -> if (state.folders.isNotEmpty()) {
                        item(key = "folders-h") { SectionHeader(stringResource(R.string.folders)) { TextButton(onClick = { n.open(Routes.FOLDERS) }) { Text(stringResource(R.string.see_all)) } } }
                        item(key = "folders") {
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = sp.l), horizontalArrangement = Arrangement.spacedBy(sp.s)) {
                                state.folders.forEach { f ->
                                    AssistChip(onClick = { n.open(Routes.notes(Collection.InFolder(f.id))) }, label = { Text(f.name) },
                                        leadingIcon = { Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp)) })
                                }
                            }
                        }
                    }
                    HomeSection.TEMPLATES -> if (state.templates.isNotEmpty()) {
                        item(key = "tpl-h") { SectionHeader(stringResource(R.string.recent_templates)) { TextButton(onClick = { n.open(Routes.templates()) }) { Text(stringResource(R.string.see_all)) } } }
                        item(key = "tpl") {
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = sp.l), horizontalArrangement = Arrangement.spacedBy(sp.s)) {
                                state.templates.forEach { t ->
                                    AssistChip(onClick = { n.open(Routes.templateForm(t.id)) }, label = { Text(t.name) },
                                        leadingIcon = { Icon(Icons.Outlined.Dashboard, null, Modifier.size(18.dp)) })
                                }
                            }
                        }
                    }
                    HomeSection.ATTACHMENTS -> item(key = "att") {
                        SectionHeader(stringResource(R.string.attachments)) { TextButton(onClick = { n.open(Routes.ATTACHMENTS) }) { Text(stringResource(R.string.see_all)) } }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.size(sp.xl)) }
        }
    }
}
