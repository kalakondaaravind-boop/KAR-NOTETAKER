@file:OptIn(ExperimentalMaterial3Api::class)

package app.margin.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.model.Ids
import app.margin.domain.repository.TemplateRepository
import app.margin.domain.template.BuiltInTemplates
import app.margin.domain.template.FieldType
import app.margin.domain.template.TemplateDefinition
import app.margin.domain.template.TemplateDsl
import app.margin.domain.template.TemplateField
import app.margin.domain.template.TemplateSection
import app.margin.domain.template.TemplateValidator
import app.margin.ui.navigation.Navigator
import app.margin.ui.theme.LocalSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/** Editable draft. Each field keeps its original definition so attributes the builder doesn't expose survive edits. */
data class FieldDraft(val label: String, val type: FieldType, val required: Boolean, val options: String, val original: TemplateField? = null)
data class SectionDraft(val title: String, val repeatable: Boolean, val fields: List<FieldDraft>, val original: TemplateSection? = null)
data class BuilderUi(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val category: String = "Custom",
    val titlePattern: String = "{{name}} — {{date}}",
    val sensitive: Boolean = false,
    val sections: List<SectionDraft> = listOf(SectionDraft("", false, listOf(FieldDraft("", FieldType.TEXT, false, "")))),
    val staticBody: String? = null,
    val base: TemplateDefinition? = null,
    val errors: List<String> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class TemplateBuilderViewModel @Inject constructor(saved: SavedStateHandle, private val repo: TemplateRepository) : ViewModel() {
    private val _ui = MutableStateFlow(BuilderUi())
    val ui: StateFlow<BuilderUi> = _ui.asStateFlow()

    init {
        val id = saved.get<String>("id")?.takeIf { it.isNotBlank() }
        viewModelScope.launch {
            val t = id?.let { repo.get(it) }
            _ui.value = if (t == null) BuilderUi(id = "user." + Ids.newId(), loaded = true) else BuilderUi(
                id = t.id, name = t.name, description = t.description, category = t.category, titlePattern = t.titlePattern, sensitive = t.sensitive,
                sections = flatten(t.sections), staticBody = t.staticBody, base = t, loaded = true,
            )
        }
    }

    /** Nested sections are flattened for editing (the builder edits one level; deeper nesting is kept by title order). */
    private fun flatten(sections: List<TemplateSection>): List<SectionDraft> = sections.flatMap { s ->
        listOf(SectionDraft(s.title, s.repeatable, s.fields.map { f -> FieldDraft(f.label, f.type, f.required, f.options.joinToString(", "), f) }, s)) + flatten(s.sections)
    }

    fun update(t: (BuilderUi) -> BuilderUi) { _ui.value = t(_ui.value).copy(errors = emptyList()) }

    fun build(): TemplateDefinition {
        val u = _ui.value
        val usedKeys = HashSet<String>()
        val usedSections = HashSet<String>()
        fun key(label: String, prefix: String): String {
            var k = TemplateDsl.slugify(label).ifEmpty { prefix }.let { if (it.first().isLetter()) it else "f_$it" }.take(60)
            var i = 2
            val base = k
            while (!usedKeys.add(k)) k = "${base}_${i++}"
            return k
        }
        val sections = u.sections.filter { s -> s.fields.any { it.label.isNotBlank() } || s.title.isNotBlank() }.mapIndexed { si, s ->
            var sk = TemplateDsl.slugify(s.title).ifEmpty { "section_${si + 1}" }
            while (!usedSections.add(sk)) sk += "_"
            TemplateSection(
                key = sk, title = s.title.trim(), description = s.original?.description, repeatable = s.repeatable,
                visibleIf = s.original?.visibleIf,
                fields = s.fields.filter { it.label.isNotBlank() }.map { d ->
                    val opts = d.options.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    val base = d.original
                    TemplateField(
                        key = base?.key?.takeIf { usedKeys.add(it) } ?: key(d.label, "field"),
                        label = d.label.trim(), type = d.type, required = d.required,
                        default = base?.default?.takeIf { base.type == d.type }, placeholder = base?.placeholder, help = base?.help,
                        options = if (d.type in setOf(FieldType.SINGLE_SELECT, FieldType.MULTI_SELECT, FieldType.TABLE)) opts else emptyList(),
                        min = base?.min, max = base?.max ?: if (d.type == FieldType.RATING) BigDecimal(5) else null,
                        maxLength = base?.maxLength, currency = base?.currency, visibleIf = base?.visibleIf,
                    )
                },
            )
        }
        val builtIn = BuiltInTemplates.byId(u.id)
        return TemplateDefinition(
            id = u.id, version = builtIn?.version ?: ((u.base?.version ?: 0) + 1), name = u.name.trim(), description = u.description.trim(),
            category = u.category.trim().ifEmpty { "Custom" }, titlePattern = u.titlePattern.ifBlank { "{{name}}" }, sections = sections,
            sensitive = u.sensitive, builtIn = false, staticBody = u.staticBody, defaultTags = u.base?.defaultTags ?: emptyList(),
            disclaimer = u.base?.disclaimer, icon = u.base?.icon ?: "note",
        )
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val t = build()
        val errors = TemplateValidator.validateSchema(t).map { "${it.path}: ${it.message}" }
        if (errors.isNotEmpty()) { _ui.value = _ui.value.copy(errors = errors); return@launch }
        repo.saveUserTemplate(t)
        onSaved()
    }
}

@Composable
fun TemplateBuilderScreen(n: Navigator, vm: TemplateBuilderViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val sp = LocalSpacing.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(if (ui.base == null) R.string.new_template else R.string.edit_template)) },
            navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { TextButton(onClick = { vm.save { n.back() } }, enabled = ui.loaded) { Text(stringResource(R.string.save)) } },
        )
    }) { p ->
        Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = sp.readingWidth).verticalScroll(rememberScrollState()).padding(sp.l), verticalArrangement = Arrangement.spacedBy(sp.m)) {
                if (BuiltInTemplates.byId(ui.id) != null) Text(stringResource(R.string.customizing_builtin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                ui.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedTextField(ui.name, { v -> vm.update { it.copy(name = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.template_name)) }, singleLine = true)
                OutlinedTextField(ui.description, { v -> vm.update { it.copy(description = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.description)) })
                OutlinedTextField(ui.category, { v -> vm.update { it.copy(category = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.category)) }, singleLine = true)
                OutlinedTextField(ui.titlePattern, { v -> vm.update { it.copy(titlePattern = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.title_pattern)) },
                    supportingText = { Text(stringResource(R.string.title_pattern_help)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sensitive_template))
                        Text(stringResource(R.string.sensitive_template_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(ui.sensitive, { v -> vm.update { it.copy(sensitive = v) } })
                }
                ui.staticBody?.let { body ->
                    OutlinedTextField(body, { v -> vm.update { it.copy(staticBody = v) } }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.fixed_body)) }, minLines = 4)
                }
                ui.sections.forEachIndexed { si, s ->
                    SectionEditor(s, si, ui.sections.size, onChange = { ns -> vm.update { u -> u.copy(sections = u.sections.toMutableList().also { it[si] = ns }) } },
                        onRemove = { vm.update { u -> u.copy(sections = u.sections.toMutableList().also { it.removeAt(si) }) } })
                }
                OutlinedButton(onClick = { vm.update { it.copy(sections = it.sections + SectionDraft("", false, listOf(FieldDraft("", FieldType.TEXT, false, "")))) } }) {
                    Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_section))
                }
            }
        }
    }
}

@Composable
private fun SectionEditor(s: SectionDraft, index: Int, count: Int, onChange: (SectionDraft) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(s.title, { onChange(s.copy(title = it)) }, Modifier.weight(1f), label = { Text(stringResource(R.string.section_title_n, index + 1)) }, singleLine = true)
                if (count > 1) IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_section)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(s.repeatable, { onChange(s.copy(repeatable = it)) })
                Text(stringResource(R.string.repeatable_section))
            }
            s.fields.forEachIndexed { fi, f ->
                FieldEditor(f, fi, s.fields.size,
                    onChange = { nf -> onChange(s.copy(fields = s.fields.toMutableList().also { it[fi] = nf })) },
                    onMove = { d -> val to = fi + d; if (to in s.fields.indices) onChange(s.copy(fields = s.fields.toMutableList().also { java.util.Collections.swap(it, fi, to) })) },
                    onRemove = { onChange(s.copy(fields = s.fields.toMutableList().also { it.removeAt(fi) })) })
            }
            TextButton(onClick = { onChange(s.copy(fields = s.fields + FieldDraft("", FieldType.TEXT, false, ""))) }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_field)) }
        }
    }
}

@Composable
private fun FieldEditor(f: FieldDraft, index: Int, count: Int, onChange: (FieldDraft) -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(f.label, { onChange(f.copy(label = it)) }, Modifier.weight(1f), label = { Text(stringResource(R.string.field_label_n, index + 1)) }, singleLine = true)
            IconButton(onClick = { onMove(-1) }, enabled = index > 0) { Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.move_up)) }
            IconButton(onClick = { onMove(1) }, enabled = index < count - 1) { Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.move_down)) }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_field)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ExposedDropdownMenuBox(expanded, { expanded = it }, Modifier.weight(1f)) {
                OutlinedTextField(fieldTypeLabel(f.type), {}, readOnly = true, label = { Text(stringResource(R.string.field_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth())
                androidx.compose.material3.ExposedDropdownMenu(expanded, { expanded = false }) {
                    FieldType.entries.forEach { t -> DropdownMenuItem(text = { Text(fieldTypeLabel(t)) }, onClick = { onChange(f.copy(type = t)); expanded = false }) }
                }
            }
            Checkbox(f.required, { onChange(f.copy(required = it)) })
            Text(stringResource(R.string.required))
        }
        if (f.type in setOf(FieldType.SINGLE_SELECT, FieldType.MULTI_SELECT, FieldType.TABLE)) {
            OutlinedTextField(f.options, { onChange(f.copy(options = it)) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(if (f.type == FieldType.TABLE) R.string.table_columns else R.string.options_csv)) }, singleLine = true)
        }
    }
}

@Composable
fun fieldTypeLabel(t: FieldType): String = stringResource(when (t) {
    FieldType.TEXT -> R.string.ft_text; FieldType.LONG_TEXT -> R.string.ft_long; FieldType.RICH_TEXT -> R.string.ft_rich
    FieldType.NUMBER -> R.string.ft_number; FieldType.CURRENCY -> R.string.ft_currency; FieldType.DATE -> R.string.ft_date
    FieldType.TIME -> R.string.ft_time; FieldType.DATETIME -> R.string.ft_datetime; FieldType.CHECKBOX -> R.string.ft_checkbox
    FieldType.CHECKLIST -> R.string.ft_checklist; FieldType.SINGLE_SELECT -> R.string.ft_select; FieldType.MULTI_SELECT -> R.string.ft_multiselect
    FieldType.URL -> R.string.ft_url; FieldType.EMAIL -> R.string.ft_email; FieldType.RATING -> R.string.ft_rating
    FieldType.PROGRESS -> R.string.ft_progress; FieldType.LIST -> R.string.ft_list; FieldType.TABLE -> R.string.ft_table
})
