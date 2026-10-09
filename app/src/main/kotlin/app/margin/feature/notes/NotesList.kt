@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package app.margin.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.model.Clock
import app.margin.domain.model.Folder
import app.margin.domain.model.NoteState
import app.margin.domain.model.NoteSummary
import app.margin.domain.organization.NoteFilter
import app.margin.domain.organization.SmartCollection
import app.margin.domain.organization.SortField
import app.margin.domain.organization.SortOrder
import app.margin.domain.repository.FolderRepository
import app.margin.domain.repository.NoteRepository
import app.margin.domain.repository.TagRepository
import app.margin.domain.settings.SettingsRepository
import app.margin.ui.components.ConfirmDialog
import app.margin.ui.components.EmptyState
import app.margin.ui.components.FolderPickerDialog
import app.margin.ui.components.LoadingState
import app.margin.ui.components.NoteRow
import app.margin.ui.components.TagEditorDialog
import app.margin.ui.navigation.Collection
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesListState(
    val loading: Boolean = true,
    val notes: List<NoteSummary> = emptyList(),
    val folders: Map<String, Folder> = emptyMap(),
    val subfolders: List<Folder> = emptyList(),
    val sort: SortOrder = SortOrder(),
    val selected: Set<String> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesListViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val notes: NoteRepository,
    private val foldersRepo: FolderRepository,
    tagsRepo: TagRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val collection: Collection = Collection.decode(saved.get<String>("collection"))
    private val sort = MutableStateFlow<SortOrder?>(null)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    val allTags = tagsRepo.observeTags().map { l -> l.map { it.tag.name } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun filter(): NoteFilter = when (val c = collection) {
        Collection.All -> NoteFilter()
        Collection.Inbox -> NoteFilter(inboxOnly = true)
        Collection.Pinned -> NoteFilter(pinned = true)
        Collection.Favorites -> NoteFilter(favorite = true)
        Collection.Archive -> NoteFilter(states = setOf(NoteState.ARCHIVED))
        Collection.Trash -> NoteFilter(states = setOf(NoteState.TRASHED))
        is Collection.InFolder -> NoteFilter(folderIds = setOf(c.folderId))
        is Collection.WithTag -> NoteFilter(tagKeys = setOf(c.tagKey))
        is Collection.Smart -> SmartCollection.entries.firstOrNull { it.name == c.name }?.filter(clock.now()) ?: NoteFilter()
    }

    private val effectiveSort = combine(sort, settings.settings) { s, st ->
        s ?: (collection as? Collection.Smart)?.let { c -> SmartCollection.entries.firstOrNull { it.name == c.name }?.defaultSort } ?: st.defaultSort
    }

    val state: StateFlow<NotesListState> = combine(
        effectiveSort.flatMapLatest { s -> notes.observeNotes(filter(), s, 2_000).map { it to s } },
        foldersRepo.observeFolders(includeArchived = true),
        selected,
    ) { (list, s), folders, sel ->
        NotesListState(
            loading = false, notes = list, folders = folders.associateBy { it.id },
            subfolders = (collection as? Collection.InFolder)?.let { c -> folders.filter { it.parentId == c.folderId } } ?: emptyList(),
            sort = s, selected = sel.intersect(list.map { it.id }.toSet()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesListState())

    fun setSort(s: SortOrder) { sort.value = s }
    fun toggle(id: String) { selected.value = selected.value.let { if (id in it) it - id else it + id } }
    fun selectAll() { selected.value = state.value.notes.map { it.id }.toSet() }
    fun clearSelection() { selected.value = emptySet() }

    private fun bulk(block: suspend (Set<String>) -> Unit) = viewModelScope.launch {
        val ids = selected.value
        if (ids.isEmpty()) return@launch
        block(ids)
        selected.value = emptySet()
    }

    fun pin() = bulk { ids -> notes.setPinned(ids, !state.value.notes.filter { it.id in ids }.all { it.pinned }) }
    fun favorite() = bulk { ids -> notes.setFavorite(ids, !state.value.notes.filter { it.id in ids }.all { it.favorite }) }
    fun archive() = bulk { notes.archive(it) }
    fun unarchive() = bulk { notes.unarchive(it) }
    fun trash(onDone: (Set<String>) -> Unit) = bulk { notes.moveToTrash(it); onDone(it) }
    fun restore() = bulk { notes.restoreFromTrash(it) }
    fun undoTrash(ids: Set<String>) = viewModelScope.launch { notes.restoreFromTrash(ids) }
    fun deleteForever() = bulk { notes.deletePermanently(it) }
    fun move(folderId: String?) = bulk { notes.move(it, folderId) }
    fun addTags(tags: List<String>) = bulk { notes.addTags(it, tags) }
    fun emptyTrash() = viewModelScope.launch { notes.emptyTrash() }

    fun newNote(onCreated: (String) -> Unit) = viewModelScope.launch {
        val folder = (collection as? Collection.InFolder)?.folderId
        // The tag key resolves to the existing tag (keys are unique), so no duplicate tag is created.
        val tag = (collection as? Collection.WithTag)?.tagKey
        val n = notes.create(folderId = folder, tags = listOfNotNull(tag))
        if (collection == Collection.Pinned) notes.setPinned(listOf(n.id), true)
        if (collection == Collection.Favorites) notes.setFavorite(listOf(n.id), true)
        onCreated(n.id)
    }
}

@Composable
private fun collectionTitle(c: Collection, folders: Map<String, Folder>): String = when (c) {
    Collection.All -> stringResource(R.string.all_notes)
    Collection.Inbox -> stringResource(R.string.inbox)
    Collection.Pinned -> stringResource(R.string.pinned)
    Collection.Favorites -> stringResource(R.string.favorites)
    Collection.Archive -> stringResource(R.string.archive)
    Collection.Trash -> stringResource(R.string.trash)
    is Collection.InFolder -> folders[c.folderId]?.name ?: stringResource(R.string.folder)
    is Collection.WithTag -> "#" + c.tagKey
    is Collection.Smart -> smartName(c.name)
}

@Composable
fun smartName(name: String): String = when (name) {
    "RECENTLY_EDITED" -> stringResource(R.string.smart_recently_edited)
    "RECENTLY_CREATED" -> stringResource(R.string.smart_recently_created)
    "FAVORITES" -> stringResource(R.string.favorites)
    "PINNED" -> stringResource(R.string.pinned)
    "UNTAGGED" -> stringResource(R.string.smart_untagged)
    "ARCHIVED" -> stringResource(R.string.archive)
    "DRAFTS" -> stringResource(R.string.smart_drafts)
    "WITH_ATTACHMENTS" -> stringResource(R.string.smart_with_attachments)
    "WITH_LINKS" -> stringResource(R.string.smart_with_links)
    "WITH_TASKS" -> stringResource(R.string.smart_with_tasks)
    "RECENTLY_IMPORTED" -> stringResource(R.string.smart_recently_imported)
    "LONG_FORM" -> stringResource(R.string.smart_long_form)
    "NOT_REVIEWED" -> stringResource(R.string.smart_not_reviewed)
    else -> name
}

@Composable
fun NotesListScreen(n: Navigator, collection: Collection, vm: NotesListViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val selectionMode = state.selected.isNotEmpty()
    val isTrash = collection == Collection.Trash
    val isArchive = collection == Collection.Archive
    var sortMenu by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var moveDialog by rememberSaveable { mutableStateOf(false) }
    var tagDialog by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmEmptyTrash by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val movedToTrash = stringResource(R.string.moved_to_trash)
    val undo = stringResource(R.string.undo)

    BackHandler(enabled = selectionMode) { vm.clearSelection() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.Outlined.Close, stringResource(R.string.clear_selection)) } },
                    title = { Text(pluralStringResource(R.plurals.selected_count, state.selected.size, state.selected.size)) },
                    actions = {
                        IconButton(onClick = vm::selectAll) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all)) }
                        when {
                            isTrash -> {
                                IconButton(onClick = vm::restore) { Icon(Icons.Outlined.RestoreFromTrash, stringResource(R.string.restore)) }
                                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.DeleteForever, stringResource(R.string.delete_forever)) }
                            }
                            else -> {
                                IconButton(onClick = vm::pin) { Icon(Icons.Outlined.PushPin, stringResource(R.string.pin)) }
                                IconButton(onClick = { moveDialog = true }) { Icon(Icons.Outlined.DriveFileMove, stringResource(R.string.move)) }
                                IconButton(onClick = {
                                    vm.trash { ids ->
                                        scope.launch {
                                            if (snackbar.showSnackbar(movedToTrash, undo, withDismissAction = true) == SnackbarResult.ActionPerformed) vm.undoTrash(ids)
                                        }
                                    }
                                }) { Icon(Icons.Outlined.Delete, stringResource(R.string.move_to_trash)) }
                                IconButton(onClick = { overflow = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.favorite)) }, leadingIcon = { Icon(Icons.Outlined.StarOutline, null) }, onClick = { overflow = false; vm.favorite() })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.add_tags)) }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) }, onClick = { overflow = false; tagDialog = true })
                                    if (isArchive) DropdownMenuItem(text = { Text(stringResource(R.string.unarchive)) }, leadingIcon = { Icon(Icons.Outlined.Unarchive, null) }, onClick = { overflow = false; vm.unarchive() })
                                    else DropdownMenuItem(text = { Text(stringResource(R.string.archive_action)) }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { overflow = false; vm.archive() })
                                }
                            }
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(collectionTitle(collection, state.folders)) },
                    navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                    actions = {
                        IconButton(onClick = { n.open(Routes.search(folder = (collection as? Collection.InFolder)?.folderId)) }) { Icon(Icons.Outlined.Search, stringResource(R.string.nav_search)) }
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, stringResource(R.string.sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf(
                                SortOrder(SortField.UPDATED, true) to R.string.sort_updated,
                                SortOrder(SortField.CREATED, true) to R.string.sort_created_newest,
                                SortOrder(SortField.CREATED, false) to R.string.sort_created_oldest,
                                SortOrder(SortField.TITLE, false) to R.string.sort_title,
                            ).forEach { (o, label) ->
                                DropdownMenuItem(text = { Text(stringResource(label) + if (o == state.sort) " ✓" else "") }, onClick = { sortMenu = false; vm.setSort(o) })
                            }
                        }
                        if (isTrash && state.notes.isNotEmpty()) {
                            IconButton(onClick = { confirmEmptyTrash = true }) { Icon(Icons.Outlined.DeleteForever, stringResource(R.string.empty_trash)) }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode && !isTrash && !isArchive) {
                FloatingActionButton(onClick = { vm.newNote { n.open(Routes.editor(it)) } }) { Icon(Icons.Filled.Add, stringResource(R.string.new_note)) }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.notes.isEmpty() && state.subfolders.isEmpty() -> EmptyState(
                icon = if (isTrash) Icons.Outlined.Delete else if (isArchive) Icons.Outlined.Archive else Icons.Outlined.Inbox,
                title = stringResource(if (isTrash) R.string.trash_empty_title else if (isArchive) R.string.archive_empty_title else R.string.list_empty_title),
                body = stringResource(if (isTrash) R.string.trash_empty_body else if (isArchive) R.string.archive_empty_body else R.string.list_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                if (isTrash) item { Text(stringResource(R.string.trash_explainer), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
                items(state.subfolders, key = { "sf" + it.id }) { f ->
                    ListItem(headlineContent = { Text(f.name) }, leadingContent = { Icon(Icons.Outlined.Folder, null) },
                        modifier = Modifier.clickable { n.open(Routes.notes(Collection.InFolder(f.id))) })
                }
                if (state.subfolders.isNotEmpty()) item { HorizontalDivider() }
                items(state.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note, selected = note.id in state.selected, selectionMode = selectionMode,
                        onClick = { if (selectionMode) vm.toggle(note.id) else n.open(Routes.editor(note.id, preview = isTrash)) },
                        onLongClick = { vm.toggle(note.id) },
                        folderName = if (collection !is Collection.InFolder) note.folderId?.let { state.folders[it]?.name } else null,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (moveDialog) FolderPickerDialog(state.folders.values.toList(), stringResource(R.string.move_to), onPick = { vm.move(it); moveDialog = false }, onDismiss = { moveDialog = false })
    if (tagDialog) TagEditorDialog(emptyList(), allTags, onSave = { vm.addTags(it); tagDialog = false }, onDismiss = { tagDialog = false }, title = stringResource(R.string.add_tags))
    if (confirmDelete) ConfirmDialog(
        stringResource(R.string.delete_forever_title), pluralStringResource(R.plurals.delete_forever_message, state.selected.size, state.selected.size),
        stringResource(R.string.delete_forever), vm::deleteForever, { confirmDelete = false }, destructive = true,
    )
    if (confirmEmptyTrash) ConfirmDialog(
        stringResource(R.string.empty_trash), pluralStringResource(R.plurals.empty_trash_message, state.notes.size, state.notes.size),
        stringResource(R.string.empty_trash), { vm.emptyTrash() }, { confirmEmptyTrash = false }, destructive = true,
    )
}
