@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.feature.knowledge

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.data.repo.SearchRepositoryImpl
import app.margin.domain.ai.AiSettings
import app.margin.domain.knowledge.Duplicates
import app.margin.domain.knowledge.KnowledgeDoc
import app.margin.domain.knowledge.LinkGraph
import app.margin.domain.knowledge.RelatedNotes
import app.margin.domain.knowledge.RelationReason
import app.margin.domain.organization.Tags
import app.margin.domain.repository.NoteRepository
import app.margin.ui.components.EmptyState
import app.margin.ui.components.LoadingState
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class Connection(val aId: String, val aTitle: String, val bId: String, val bTitle: String, val detail: String, val explicit: Boolean)

data class KnowledgeUi(
    val loading: Boolean = true,
    /** Explicit [[links]] between notes — the user-created graph, as an accessible text list. */
    val links: List<Connection> = emptyList(),
    /** Suggested relationships (shared terms/tags). Clearly labelled as suggestions, never as links. */
    val suggestions: List<Connection> = emptyList(),
    val duplicates: List<Connection> = emptyList(),
    val orphans: List<Pair<String, String>> = emptyList(),
)

/**
 * Local knowledge discovery. Everything is computed on-device from note text; nothing is sent
 * anywhere. Computation is bounded (most recent notes) to keep it fast on large libraries.
 */
@HiltViewModel
class KnowledgeViewModel @Inject constructor(private val search: SearchRepositoryImpl, private val notes: NoteRepository) : ViewModel() {
    val ui = MutableStateFlow(KnowledgeUi())

    fun load() = viewModelScope.launch {
        ui.value = KnowledgeUi(loading = true)
        val docs = search.aiDocuments(AiSettings(includeArchived = false)).sortedByDescending { it.updatedAt }.take(MAX_NOTES)
        val result = withContext(Dispatchers.Default) {
            val titles = docs.associate { it.id to it.title.ifBlank { it.plainBody.lineSequence().firstOrNull().orEmpty().take(60) } }
            val kdocs = docs.map { KnowledgeDoc(it.id, it.title, it.plainBody, it.tagNames.map(Tags::key).toSet()) }
            val titleIndex = notes.titleIndex()
            val ids = docs.map { it.id }.toSet()
            val explicit = HashSet<Pair<String, String>>()
            // Explicit links come from note bodies via the same resolver used on save.
            docs.forEach { d -> LinkGraph.resolve(notes.getNote(d.id)?.body.orEmpty(), titleIndex, ids).resolved.forEach { to -> if (to != d.id) explicit += d.id to to } }
            val links = explicit.map { (a, b) -> Connection(a, titles[a].orEmpty(), b, titles[b].orEmpty(), "", true) }
            val suggestions = kdocs.take(MAX_SUGGESTION_SOURCES).flatMap { d ->
                RelatedNotes.find(d, kdocs, limit = 3, minScore = 0.2).filter { r -> (d.id to r.id) !in explicit && (r.id to d.id) !in explicit && d.id < r.id }
                    .map { r -> Connection(d.id, titles[d.id].orEmpty(), r.id, titles[r.id].orEmpty(),
                        (if (RelationReason.SHARED_TAGS in r.reasons) "#" else "") + r.sharedTerms.take(3).joinToString(", "), false) }
            }.distinctBy { it.aId to it.bId }.take(60)
            val dupes = kdocs.take(MAX_SUGGESTION_SOURCES).flatMap { d ->
                Duplicates.findFor(d, kdocs, 0.7).filter { d.id < it.id }.map { c -> Connection(d.id, titles[d.id].orEmpty(), c.id, titles[c.id].orEmpty(), "${(c.similarity * 100).toInt()}%", false) }
            }.take(40)
            val orphanIds = LinkGraph.orphans(ids, explicit)
            KnowledgeUi(false, links.sortedBy { it.aTitle }, suggestions, dupes, orphanIds.map { it to titles[it].orEmpty() }.sortedBy { it.second }.take(200))
        }
        ui.value = result
    }

    companion object {
        const val MAX_NOTES = 2_000
        const val MAX_SUGGESTION_SOURCES = 300
    }
}

@Composable
fun KnowledgeScreen(n: Navigator, vm: KnowledgeViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(Unit) { vm.load() }
    val untitled = stringResource(R.string.untitled)
    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text(stringResource(R.string.knowledge)) },
                navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
            TabRow(tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.links)) })
                Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.related)) })
                Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.duplicates)) })
                Tab(tab == 3, { tab = 3 }, text = { Text(stringResource(R.string.orphans)) })
            }
        }
    }) { p ->
        if (ui.loading) { LoadingState(Modifier.padding(p)); return@Scaffold }
        val list: List<Connection> = when (tab) { 0 -> ui.links; 1 -> ui.suggestions; 2 -> ui.duplicates; else -> emptyList() }
        val explainer = stringResource(when (tab) { 0 -> R.string.links_explainer; 1 -> R.string.related_explainer; 2 -> R.string.duplicates_explainer; else -> R.string.orphans_explainer })
        LazyColumn(Modifier.fillMaxSize().padding(p)) {
            item { Text(explainer, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (tab == 3) {
                if (ui.orphans.isEmpty()) item { EmptyState(Icons.Outlined.Hub, stringResource(R.string.no_orphans), "") }
                items(ui.orphans, key = { it.first }) { (id, title) ->
                    ListItem(headlineContent = { Text(title.ifBlank { untitled }) }, modifier = Modifier.clickable { n.open(Routes.editor(id)) })
                }
            } else {
                if (list.isEmpty()) item { EmptyState(Icons.Outlined.Hub, stringResource(R.string.nothing_found), "") }
                items(list, key = { it.aId + it.bId }) { c ->
                    ListItem(
                        headlineContent = { Text("${c.aTitle.ifBlank { untitled }}  ${if (c.explicit) "→" else "↔"}  ${c.bTitle.ifBlank { untitled }}") },
                        supportingContent = if (c.detail.isNotBlank()) { { Text(if (c.explicit) c.detail else stringResource(R.string.suggested_because, c.detail)) } } else null,
                        modifier = Modifier.clickable { n.open(Routes.editor(c.aId)) },
                    )
                }
            }
        }
    }
}
