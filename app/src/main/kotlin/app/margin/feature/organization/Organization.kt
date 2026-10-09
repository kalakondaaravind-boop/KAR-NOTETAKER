@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.feature.organization

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AutoAwesomeMotion
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.data.repo.FolderValidationException
import app.margin.domain.model.Folder
import app.margin.domain.organization.FolderDeletePolicy
import app.margin.domain.organization.Folders
import app.margin.domain.organization.SmartCollection
import app.margin.domain.organization.Tags
import app.margin.domain.repository.FolderRepository
import app.margin.domain.repository.TagRepository
import app.margin.domain.repository.TagWithCount
import app.margin.feature.notes.smartName
import app.margin.ui.components.ConfirmDialog
import app.margin.ui.components.EmptyState
import app.margin.ui.components.FolderPickerDialog
import app.margin.ui.components.SectionHeader
import app.margin.ui.components.TextInputDialog
import app.margin.ui.navigation.Collection
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoldersViewModel @Inject constructor(private val repo: FolderRepository) : ViewModel() {
    val folders: StateFlow<List<Folder>> = repo.observeFolders(includeArchived = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val counts: StateFlow<Map<String?, Int>> = repo.observeNoteCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun run(onError: (String) -> Unit, block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: FolderValidationException) { onError(e.reason) }
    }
    fun create(name: String, parent: String?, onError: (String) -> Unit) = run(onError) { repo.create(name, parent) }
    fun rename(id: String, name: String, onError: (String) -> Unit) = run(onError) { repo.rename(id, name) }
    fun move(id: String, parent: String?, onError: (String) -> Unit) = run(onError) { repo.move(id, parent) }
    fun pin(f: Folder) = viewModelScope.launch { repo.setPinned(f.id, !f.pinned) }
    fun archive(f: Folder) = viewModelScope.launch { repo.setArchived(f.id, !f.archived) }
    fun delete(id: String, policy: FolderDeletePolicy) = viewModelScope.launch { repo.delete(id, policy) }
}

@Composable
private fun folderError(code: String): String = when (code) {
    "TOO_DEEP" -> stringResource(R.string.folder_error_depth, Folders.MAX_DEPTH)
    "INTO_DESCENDANT", "INTO_ITSELF" -> stringResource(R.string.folder_error_cycle)
    "EMPTY" -> stringResource(R.string.name_required)
    "TOO_LONG" -> stringResource(R.string.name_too_long)
    "INVALID_CHARACTERS" -> stringResource(R.string.folder_error_chars)
    else -> stringResource(R.string.something_went_wrong)
}

@Composable
fun FoldersScreen(n: Navigator, vm: FoldersViewModel = hiltViewModel()) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    var create by rememberSaveable { mutableStateOf<String?>(null) } // "" = top level, else parent id
    var rename by rememberSaveable { mutableStateOf<String?>(null) }
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val onError: (String) -> Unit = { errorCode = it }
    val flat = Folders.flatten(folders, includeArchived = showArchived)

    errorCode?.let { code ->
        val msg = folderError(code)
        androidx.compose.runtime.LaunchedEffect(code) { snackbar.showSnackbar(msg); errorCode = null }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.folders)) },
                navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { TextButton(onClick = { showArchived = !showArchived }) { Text(stringResource(if (showArchived) R.string.hide_archived else R.string.show_archived)) } })
        },
        floatingActionButton = { FloatingActionButton(onClick = { create = "" }) { Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.new_notebook)) } },
    ) { p ->
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            item {
                ListItem(headlineContent = { Text(stringResource(R.string.inbox)) }, supportingContent = { Text(stringResource(R.string.inbox_explainer)) },
                    leadingContent = { Icon(Icons.Outlined.Inbox, null) }, trailingContent = { Text("${counts[null] ?: 0}") },
                    modifier = Modifier.clickable { n.open(Routes.notes(Collection.Inbox)) })
            }
            item { SectionHeader(stringResource(R.string.notebooks)) }
            if (flat.isEmpty()) item {
                EmptyState(Icons.Outlined.Folder, stringResource(R.string.folders_empty_title), stringResource(R.string.folders_empty_body),
                    stringResource(R.string.new_notebook), { create = "" }, Modifier.padding(vertical = 16.dp))
            }
            items(flat, key = { it.first.id }) { (f, depth) ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text(f.name + if (f.archived) " · " + stringResource(R.string.archived_label) else "") },
                    leadingContent = { Icon(if (depth == 0) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, tint = f.color?.let { androidx.compose.ui.graphics.Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (f.pinned) Icon(Icons.Outlined.PushPin, stringResource(R.string.pinned))
                            Text("${counts[f.id] ?: 0}", Modifier.padding(horizontal = 8.dp))
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.folder_actions, f.name)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (Folders.canCreateChild(f.id, folders)) DropdownMenuItem(text = { Text(stringResource(R.string.new_subfolder)) }, onClick = { menu = false; create = f.id })
                                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; rename = f.id })
                                DropdownMenuItem(text = { Text(stringResource(R.string.move)) }, onClick = { menu = false; moving = f.id })
                                DropdownMenuItem(text = { Text(stringResource(if (f.pinned) R.string.unpin else R.string.pin)) }, onClick = { menu = false; vm.pin(f) })
                                DropdownMenuItem(text = { Text(stringResource(if (f.archived) R.string.unarchive else R.string.archive_action)) }, onClick = { menu = false; vm.archive(f) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = f.id })
                            }
                        }
                    },
                    modifier = Modifier.padding(start = (depth * 20).dp).clickable { n.open(Routes.notes(Collection.InFolder(f.id))) },
                )
            }
            item { SectionHeader(stringResource(R.string.smart_collections)) }
            items(SmartCollection.entries.filter { it != SmartCollection.FAVORITES && it != SmartCollection.PINNED && it != SmartCollection.ARCHIVED }, key = { "smart" + it.name }) { s ->
                ListItem(headlineContent = { Text(smartName(s.name)) }, leadingContent = { Icon(Icons.Outlined.AutoAwesomeMotion, null) },
                    modifier = Modifier.clickable { n.open(Routes.notes(Collection.Smart(s.name))) })
            }
        }
    }

    val nameError = stringResource(R.string.folder_error_chars)
    create?.let { parent ->
        TextInputDialog(stringResource(if (parent.isEmpty()) R.string.new_notebook else R.string.new_subfolder), stringResource(R.string.name),
            validate = { if (Folders.validateName(it) != null && it.isNotBlank()) nameError else null },
            onConfirm = { vm.create(it, parent.ifEmpty { null }, onError) }, onDismiss = { create = null })
    }
    rename?.let { id ->
        TextInputDialog(stringResource(R.string.rename), stringResource(R.string.name), folders.firstOrNull { it.id == id }?.name.orEmpty(),
            validate = { if (Folders.validateName(it) != null && it.isNotBlank()) nameError else null },
            onConfirm = { vm.rename(id, it, onError) }, onDismiss = { rename = null })
    }
    moving?.let { id ->
        FolderPickerDialog(folders, stringResource(R.string.move_folder_to), onPick = { parent -> vm.move(id, parent, onError); moving = null }, onDismiss = { moving = null }, excludeId = id)
    }
    deleting?.let { id -> DeleteFolderDialog(folders.firstOrNull { it.id == id }?.name.orEmpty(), onDelete = { vm.delete(id, it) }, onDismiss = { deleting = null }) }
}

@Composable
private fun DeleteFolderDialog(name: String, onDelete: (FolderDeletePolicy) -> Unit, onDismiss: () -> Unit) {
    var policy by rememberSaveable { mutableStateOf(FolderDeletePolicy.MOVE_NOTES_TO_INBOX) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_folder_title, name)) },
        text = {
            Column {
                Text(stringResource(R.string.delete_folder_body))
                listOf(FolderDeletePolicy.MOVE_NOTES_TO_INBOX to R.string.delete_folder_keep_notes, FolderDeletePolicy.TRASH_NOTES to R.string.delete_folder_trash_notes).forEach { (p, label) ->
                    Row(Modifier.clickable { policy = p }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = policy == p, onClick = { policy = p })
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDelete(policy); onDismiss() }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@HiltViewModel
class TagsViewModel @Inject constructor(private val repo: TagRepository) : ViewModel() {
    val tags: StateFlow<List<TagWithCount>> = repo.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun rename(id: String, name: String, done: (TagRepository.RenameResult) -> Unit) = viewModelScope.launch { done(repo.rename(id, name)) }
    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun deleteUnused(done: (Int) -> Unit) = viewModelScope.launch { done(repo.deleteUnused()) }
}

@Composable
fun TagsScreen(n: Navigator, vm: TagsViewModel = hiltViewModel()) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    var rename by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val unused = tags.count { it.count == 0 }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tags)) },
                navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (unused > 0) IconButton(onClick = { vm.deleteUnused { c -> scope.launch { snackbar.showSnackbar(ctx.resources.getQuantityString(R.plurals.unused_tags_deleted, c, c)) } } }) {
                        Icon(Icons.Outlined.CleaningServices, stringResource(R.string.delete_unused_tags))
                    }
                })
        },
    ) { p ->
        if (tags.isEmpty()) EmptyState(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.tags_empty_title), stringResource(R.string.tags_empty_body), modifier = Modifier.padding(p))
        else LazyColumn(Modifier.fillMaxSize().padding(p)) {
            items(tags, key = { it.tag.id }) { t ->
                var menu by remember { mutableStateOf(false) }
                val depth = t.tag.key.count { it == '/' }
                ListItem(
                    headlineContent = { Text("#" + t.tag.name) },
                    supportingContent = { Text(ctx.resources.getQuantityString(R.plurals.note_count, t.count, t.count) + if (t.count == 0) " · " + stringResource(R.string.unused) else "") },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                    trailingContent = {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.tag_actions, t.tag.name)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename_or_merge)) }, onClick = { menu = false; rename = t.tag.id })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = t.tag.id })
                        }
                    },
                    modifier = Modifier.padding(start = (depth * 16).dp).clickable { n.open(Routes.notes(Collection.WithTag(t.tag.id, t.tag.key))) },
                )
            }
        }
    }
    val invalidMsg = stringResource(R.string.tag_invalid)
    val mergedMsg = stringResource(R.string.tag_merged)
    rename?.let { id ->
        val tag = tags.firstOrNull { it.tag.id == id }?.tag
        TextInputDialog(stringResource(R.string.rename_or_merge), stringResource(R.string.name), tag?.name.orEmpty(),
            validate = { if (Tags.validate(it) != null) invalidMsg else null },
            onConfirm = { name -> vm.rename(id, name) { r -> if (r == TagRepository.RenameResult.MERGED) scope.launch { snackbar.showSnackbar(mergedMsg) } } },
            onDismiss = { rename = null })
    }
    deleting?.let { id ->
        val t = tags.firstOrNull { it.tag.id == id }
        ConfirmDialog(stringResource(R.string.delete_tag_title, t?.tag?.name.orEmpty()), stringResource(R.string.delete_tag_body),
            stringResource(R.string.delete), { vm.delete(id) }, { deleting = null }, destructive = true)
    }
}
