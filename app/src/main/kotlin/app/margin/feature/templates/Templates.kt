@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.model.Ids
import app.margin.domain.repository.TemplateRepository
import app.margin.domain.template.BuiltInTemplates
import app.margin.domain.template.TemplateCatalog
import app.margin.domain.template.TemplateDefinition
import app.margin.domain.template.TemplateEngine
import app.margin.domain.template.TemplateJson
import app.margin.domain.template.TemplateValidator
import app.margin.platform.Files
import app.margin.ui.components.ConfirmDialog
import app.margin.ui.components.EmptyState
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val repo: TemplateRepository) : ViewModel() {
    val templates: StateFlow<List<TemplateDefinition>> = repo.observeTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent: StateFlow<List<String>> = repo.observeRecentlyUsed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun duplicate(t: TemplateDefinition, done: (String) -> Unit) = viewModelScope.launch {
        val copy = TemplateCatalog.duplicate(t)
        repo.saveUserTemplate(copy); done(copy.id)
    }
    fun delete(id: String) = viewModelScope.launch { repo.deleteUserTemplate(id) }
    fun reset(id: String) = viewModelScope.launch { repo.resetBuiltIn(id) }

    /** Imports a margin.template JSON file. Invalid definitions are rejected with reasons, never stored. */
    fun import(text: String, done: (List<String>) -> Unit) = viewModelScope.launch {
        val decoded = TemplateJson.decode(text)
        val t = decoded.getOrElse { done(listOf(it.message ?: "Invalid file")); return@launch }
            .let { if (BuiltInTemplates.byId(it.id) != null || !it.id.startsWith("user.")) it.copy(id = "user." + Ids.newId()) else it }
        val errors = TemplateValidator.validateSchema(t)
        if (errors.isNotEmpty()) { done(errors.map { "${it.path}: ${it.message}" }); return@launch }
        repo.saveUserTemplate(t)
        done(emptyList())
    }
}

@Composable
fun TemplatesScreen(n: Navigator, vm: TemplatesViewModel = hiltViewModel()) {
    val all by vm.templates.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var previewing by rememberSaveable { mutableStateOf<String?>(null) }
    var importErrors by remember { mutableStateOf<List<String>?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val importedMsg = stringResource(R.string.template_imported)

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) { Files.readBounded(context, uri, 512 * 1024)?.toString(Charsets.UTF_8) }
            if (text == null) importErrors = listOf(context.getString(R.string.template_file_unreadable))
            else vm.import(text) { errs -> if (errs.isEmpty()) scope.launch { snackbar.showSnackbar(importedMsg) } else importErrors = errs }
        }
    }

    val catalog = remember(all) { TemplateCatalog(all, emptyList()) }
    val filtered = remember(all, query, category) {
        catalog.search(query).filter { category == null || it.category == category || (category == CUSTOM && !it.builtIn) }
    }
    val categories = remember(all) { listOf(CUSTOM) + all.map { it.category }.distinct() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.templates)) },
                navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = { IconButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Icon(Icons.Outlined.FileUpload, stringResource(R.string.import_template)) } })
        },
        floatingActionButton = { FloatingActionButton(onClick = { n.open(Routes.templateBuilder()) }) { Icon(Icons.Filled.Add, stringResource(R.string.new_template)) } },
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text(stringResource(R.string.search_templates, all.size)) })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(category == null, { category = null }, label = { Text(stringResource(R.string.all)) })
                categories.forEach { c -> FilterChip(category == c, { category = if (category == c) null else c }, label = { Text(if (c == CUSTOM) stringResource(R.string.my_templates) else c) }) }
            }
            if (filtered.isEmpty()) EmptyState(Icons.Outlined.Dashboard, stringResource(R.string.no_templates_title), stringResource(R.string.no_templates_body))
            else LazyColumn {
                if (query.isBlank() && category == null && recent.isNotEmpty()) {
                    item { Text(stringResource(R.string.recently_used), Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                    items(recent.mapNotNull { id -> all.firstOrNull { it.id == id } }, key = { "r" + it.id }) { t -> TemplateRow(t, vm, n, { previewing = it }, { confirmDelete = it }) }
                    item { Text(stringResource(R.string.all_templates), Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                }
                items(filtered, key = { it.id }) { t -> TemplateRow(t, vm, n, { previewing = it }, { confirmDelete = it }) }
            }
        }
    }
    previewing?.let { id -> all.firstOrNull { it.id == id }?.let { t -> TemplatePreviewDialog(t, onUse = { previewing = null; n.open(Routes.templateForm(t.id)) }, onDismiss = { previewing = null }) } }
    importErrors?.let { errs ->
        AlertDialog(onDismissRequest = { importErrors = null }, title = { Text(stringResource(R.string.template_import_failed)) },
            text = { Text(errs.take(10).joinToString("\n")) }, confirmButton = { TextButton(onClick = { importErrors = null }) { Text(stringResource(R.string.close)) } })
    }
    confirmDelete?.let { id -> ConfirmDialog(stringResource(R.string.delete_template), stringResource(R.string.delete_template_body), stringResource(R.string.delete), { vm.delete(id) }, { confirmDelete = null }, destructive = true) }
}

private const val CUSTOM = "\u0000custom"

@Composable
private fun TemplateRow(t: TemplateDefinition, vm: TemplatesViewModel, n: Navigator, onPreview: (String) -> Unit, onDelete: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    ListItem(
        headlineContent = { Text(t.name) },
        supportingContent = { Text(t.description, maxLines = 2) },
        overlineContent = { Text(if (t.builtIn) t.category else stringResource(R.string.my_templates)) },
        leadingContent = { Icon(Icons.Outlined.Dashboard, null) },
        trailingContent = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.template_actions, t.name)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.preview)) }, onClick = { menu = false; onPreview(t.id) })
                DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = false; vm.duplicate(t) { n.open(Routes.templateBuilder(it)) } })
                DropdownMenuItem(text = { Text(stringResource(R.string.customize)) }, onClick = { menu = false; n.open(Routes.templateBuilder(t.id)) })
                if (t.builtIn && BuiltInTemplates.byId(t.id) != t) DropdownMenuItem(text = { Text(stringResource(R.string.reset_to_default)) }, onClick = { menu = false; vm.reset(t.id) })
                if (t.exportable) DropdownMenuItem(text = { Text(stringResource(R.string.export_json)) }, onClick = {
                    menu = false
                    val uri = Files.shareableText(context, app.margin.domain.io.NoteExporter.safeFileName(t.name) + ".margin-template.json", TemplateJson.encode(t))
                    Files.share(context, uri, "application/json", t.name)
                })
                if (!t.builtIn) DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete(t.id) })
            }
        },
        modifier = Modifier.clickable { n.open(Routes.templateForm(t.id)) },
    )
}

@Composable
private fun TemplatePreviewDialog(t: TemplateDefinition, onUse: () -> Unit, onDismiss: () -> Unit) {
    val rendered = remember(t) { TemplateEngine.render(t, TemplateEngine.initialValues(t, LocalDateTime.now()), LocalDateTime.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t.name) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(t.description, style = MaterialTheme.typography.bodyMedium)
                t.intendedUse?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                t.disclaimer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 4.dp)) }
                Text(rendered.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text(rendered.body, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            }
        },
        confirmButton = { TextButton(onClick = onUse) { Text(stringResource(R.string.use_template)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
