@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.feature.attachments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.model.Attachment
import app.margin.domain.repository.AttachmentRepository
import app.margin.ui.components.EmptyState
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttachmentsViewModel @Inject constructor(private val repo: AttachmentRepository) : ViewModel() {
    val items: StateFlow<List<Attachment>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val usage = MutableStateFlow<Long?>(null)
    val broken = MutableStateFlow<Set<String>>(emptySet())
    fun refresh() = viewModelScope.launch {
        usage.value = repo.storageBytes()
        broken.value = repo.findBroken().toSet()
    }
}

@Composable
fun AttachmentsScreen(n: Navigator, vm: AttachmentsViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val broken by vm.broken.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.usage.value = null; vm.refresh(); checked = true }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.attachments)) },
            navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { TextButton(onClick = { vm.refresh() }) { Text(stringResource(R.string.check_files)) } })
    }) { p ->
        if (items.isEmpty()) EmptyState(Icons.Outlined.AttachFile, stringResource(R.string.attachments_empty_title), stringResource(R.string.attachments_empty_body), modifier = Modifier.padding(p))
        else LazyColumn(Modifier.fillMaxSize().padding(p)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    usage?.let { Text(stringResource(R.string.storage_used, android.text.format.Formatter.formatShortFileSize(context, it))) }
                    if (checked && broken.isNotEmpty()) Text(stringResource(R.string.broken_files, broken.size), color = MaterialTheme.colorScheme.error)
                }
            }
            items(items, key = { it.id }) { a ->
                val isBroken = a.id in broken
                ListItem(
                    headlineContent = { Text(a.fileName, maxLines = 1) },
                    supportingContent = { Text(android.text.format.Formatter.formatShortFileSize(context, a.sizeBytes) + " · " + a.mimeType + if (isBroken) " · " + stringResource(R.string.file_missing) else "") },
                    leadingContent = {
                        Icon(when {
                            isBroken -> Icons.Outlined.BrokenImage
                            a.mimeType.startsWith("image/") -> Icons.Outlined.Image
                            a.mimeType.startsWith("audio/") -> Icons.Outlined.Mic
                            else -> Icons.Outlined.Description
                        }, null, tint = if (isBroken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier.clickable { n.open(Routes.editor(a.noteId)) },
                )
            }
        }
    }
}
