@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, FlowPreview::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.margin.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.model.SavedSearch
import app.margin.domain.repository.SearchRepository
import app.margin.domain.repository.SearchResults
import app.margin.domain.repository.SearchScope
import app.margin.domain.settings.AppSettings
import app.margin.domain.settings.SettingsRepository
import app.margin.ui.components.EmptyState
import app.margin.ui.components.NoteRow
import app.margin.ui.components.TextInputDialog
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUi(val query: String = "", val results: SearchResults? = null, val searching: Boolean = false)

@HiltViewModel
class SearchViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val search: SearchRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val folder: String? = saved.get<String>("folder")?.takeIf { it.isNotBlank() }
    private val _ui = MutableStateFlow(SearchUi(query = saved.get<String>("q").orEmpty()))
    val ui: StateFlow<SearchUi> = _ui.asStateFlow()
    val saved: StateFlow<List<SavedSearch>> = search.observeSavedSearches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val appSettings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    private val queries = MutableStateFlow(_ui.value.query)

    init {
        viewModelScope.launch {
            // Debounced; collectLatest cancels a stale search when the query changes.
            queries.debounce(180).distinctUntilChanged().collectLatest { q ->
                if (q.isBlank()) { _ui.value = _ui.value.copy(results = null, searching = false); return@collectLatest }
                _ui.value = _ui.value.copy(searching = true)
                val r = search.search(q, SearchScope(folderId = folder))
                _ui.value = _ui.value.copy(results = r, searching = false)
            }
        }
    }

    fun setQuery(q: String) { _ui.value = _ui.value.copy(query = q); queries.value = q }

    fun commit() = viewModelScope.launch {
        val q = _ui.value.query
        if (appSettings.value.searchHistoryEnabled && q.isNotBlank()) settings.update { it.copy(recentSearches = AppSettings.addRecentSearch(it.recentSearches, q)) }
    }

    fun clearHistory() = viewModelScope.launch { settings.update { it.copy(recentSearches = emptyList()) } }
    fun saveSearch(name: String) = viewModelScope.launch { search.saveSearch(name, _ui.value.query) }
    fun deleteSaved(id: String) = viewModelScope.launch { search.deleteSavedSearch(id) }
    val scopedToFolder get() = folder != null
}

private val filterChips = listOf(
    "is:pinned" to R.string.chip_pinned, "is:favorite" to R.string.favorite, "has:task" to R.string.chip_tasks,
    "has:open-task" to R.string.chip_open_tasks, "has:attachment" to R.string.attachments, "has:link" to R.string.chip_links,
    "is:archived" to R.string.archive, "is:trashed" to R.string.trash,
)

@Composable
fun SearchScreen(n: Navigator, vm: SearchViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val savedSearches by vm.saved.collectAsStateWithLifecycle()
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    var help by rememberSaveable { mutableStateOf(false) }
    var saveDialog by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Scaffold(topBar = {
        TopAppBar(
            navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            title = {
                TextField(
                    value = ui.query, onValueChange = vm::setQuery, singleLine = true,
                    placeholder = { Text(stringResource(if (vm.scopedToFolder) R.string.search_in_folder_hint else R.string.search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.commit() }),
                    trailingIcon = { if (ui.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Clear, stringResource(R.string.clear)) } },
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            },
            actions = {
                if (ui.query.isNotBlank()) IconButton(onClick = { saveDialog = true }) { Icon(Icons.Outlined.BookmarkAdd, stringResource(R.string.save_search)) }
                IconButton(onClick = { help = true }) { Icon(Icons.Outlined.HelpOutline, stringResource(R.string.search_help)) }
            },
        )
    }) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                filterChips.forEach { (token, label) ->
                    val on = ui.query.split(' ').contains(token)
                    FilterChip(selected = on, onClick = {
                        val parts = ui.query.split(' ').filter { it.isNotBlank() }
                        vm.setQuery((if (on) parts - token else parts + token).joinToString(" "))
                    }, label = { Text(stringResource(label)) })
                }
            }
            val r = ui.results
            when {
                ui.query.isBlank() -> LazyColumn {
                    if (savedSearches.isNotEmpty()) {
                        item { Text(stringResource(R.string.saved_searches), Modifier.padding(16.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                        items(savedSearches, key = { "s" + it.id }) { s ->
                            ListItem(headlineContent = { Text(s.name) }, supportingContent = { Text(s.query) }, leadingContent = { Icon(Icons.Outlined.Bookmarks, null) },
                                trailingContent = { IconButton(onClick = { vm.deleteSaved(s.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) } },
                                modifier = Modifier.clickable { vm.setQuery(s.query) })
                        }
                    }
                    if (settings.searchHistoryEnabled && settings.recentSearches.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp)) {
                                Text(stringResource(R.string.recent_searches), Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = vm::clearHistory) { Text(stringResource(R.string.clear_history)) }
                            }
                        }
                        items(settings.recentSearches, key = { "h$it" }) { q ->
                            ListItem(headlineContent = { Text(q) }, leadingContent = { Icon(Icons.Outlined.History, null) }, modifier = Modifier.clickable { vm.setQuery(q) })
                        }
                    }
                    item { Text(stringResource(R.string.search_tip), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                r == null -> Unit
                r.hits.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, stringResource(R.string.no_results_title),
                    stringResource(R.string.no_results_body), stringResource(R.string.try_fuzzy), { vm.setQuery(ui.query.split(' ').joinToString(" ") { w -> if (w.contains(':') || w.startsWith("-") || w.startsWith("~") || w.length < 4) w else "~$w" }) })
                else -> LazyColumn {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.results_count, r.hits.size, r.hits.size, r.tookMs), style = MaterialTheme.typography.labelMedium)
                            if (r.approximate) Text(stringResource(R.string.approximate_matches), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                            r.warnings.forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    items(r.hits, key = { it.id }) { h ->
                        val note = r.notes[h.id] ?: return@items
                        NoteRow(note, false, false, onClick = { vm.commit(); n.open(Routes.editor(h.id)) }, onLongClick = {},
                            titleHighlight = h.title.takeIf { it.text.isNotBlank() }, snippet = h.snippet.takeIf { it.text.isNotBlank() })
                    }
                }
            }
        }
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, title = { Text(stringResource(R.string.search_help)) },
        text = { Text(stringResource(R.string.search_help_body)) }, confirmButton = { TextButton(onClick = { help = false }) { Text(stringResource(R.string.close)) } })
    if (saveDialog) TextInputDialog(stringResource(R.string.save_search), stringResource(R.string.name), ui.query.take(40), onConfirm = vm::saveSearch, onDismiss = { saveDialog = false })
}
