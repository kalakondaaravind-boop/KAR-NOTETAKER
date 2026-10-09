@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.feature.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.model.Note
import app.margin.domain.model.NoteRevision
import app.margin.domain.repository.NoteRepository
import app.margin.ui.components.EmptyState
import app.margin.ui.navigation.Navigator
import app.margin.ui.theme.LocalSemanticColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class RevisionsViewModel @Inject constructor(saved: SavedStateHandle, private val notes: NoteRepository) : ViewModel() {
    private val noteId: String = checkNotNull(saved.get<String>("noteId"))
    val revisions: StateFlow<List<NoteRevision>> = notes.observeRevisions(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val current: StateFlow<Note?> = notes.observeNote(noteId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun restore(id: String, done: () -> Unit) = viewModelScope.launch { notes.restoreRevision(id); done() }
    fun pin(id: String, pinned: Boolean) = viewModelScope.launch { notes.setRevisionPinned(id, pinned) }
}

/** Simple line diff (LCS) for comparing a revision with the current note. */
object LineDiff {
    sealed interface Line { val text: String; data class Same(override val text: String) : Line; data class Added(override val text: String) : Line; data class Removed(override val text: String) : Line }

    fun diff(old: String, new: String, maxLines: Int = 2_000): List<Line> {
        val a = old.lines().take(maxLines)
        val b = new.lines().take(maxLines)
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        val out = ArrayList<Line>()
        var i = 0; var j = 0
        while (i < a.size && j < b.size) {
            when {
                a[i] == b[j] -> { out += Line.Same(a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { out += Line.Removed(a[i]); i++ }
                else -> { out += Line.Added(b[j]); j++ }
            }
        }
        while (i < a.size) out += Line.Removed(a[i++])
        while (j < b.size) out += Line.Added(b[j++])
        return out
    }
}

@Composable
fun RevisionsScreen(n: Navigator, vm: RevisionsViewModel = hiltViewModel()) {
    val revisions by vm.revisions.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.history)) },
            navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
    }) { p ->
        if (revisions.isEmpty()) {
            EmptyState(Icons.Outlined.History, stringResource(R.string.history_empty_title), stringResource(R.string.history_empty_body), modifier = Modifier.padding(p))
        } else LazyColumn(Modifier.fillMaxSize().padding(p)) {
            item { Text(stringResource(R.string.history_explainer), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
            items(revisions, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text(fmt.format(r.createdAt)) },
                    supportingContent = { Text(r.title.ifBlank { r.body.lineSequence().firstOrNull().orEmpty() }.take(80), maxLines = 1) },
                    trailingContent = {
                        IconButton(onClick = { vm.pin(r.id, !r.pinned) }) {
                            Icon(if (r.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, stringResource(if (r.pinned) R.string.unpin_revision else R.string.pin_revision))
                        }
                    },
                    modifier = Modifier.clickable { open = r.id },
                )
            }
        }
    }
    val rev = revisions.firstOrNull { it.id == open }
    if (rev != null) {
        val c = LocalSemanticColors.current
        val diff = LineDiff.diff(rev.body, current?.body.orEmpty())
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(fmt.format(rev.createdAt)) },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.diff_legend), style = MaterialTheme.typography.labelSmall)
                    Text(buildAnnotatedString {
                        diff.forEach { l ->
                            when (l) {
                                is LineDiff.Line.Same -> append("  " + l.text + "\n")
                                is LineDiff.Line.Removed -> withStyle(SpanStyle(background = c.highlight, color = c.onHighlight)) { append("− " + l.text + "\n") }
                                is LineDiff.Line.Added -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = MaterialTheme.colorScheme.primary)) { append("+ " + l.text + "\n") }
                            }
                        }
                    }, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { vm.restore(rev.id) { open = null; n.back() } }) { Text(stringResource(R.string.restore_this_version)) } },
            dismissButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.close)) } },
        )
    }
}
