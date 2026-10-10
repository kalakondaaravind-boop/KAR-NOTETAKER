@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.repository.NoteRepository
import app.margin.domain.repository.TemplateRepository
import app.margin.domain.settings.SettingsRepository
import app.margin.domain.template.FieldType
import app.margin.domain.template.FieldValue
import app.margin.domain.template.FormValues
import app.margin.domain.template.TemplateDefinition
import app.margin.domain.template.TemplateEngine
import app.margin.domain.template.TemplateField
import app.margin.domain.template.TemplateSection
import app.margin.domain.template.TemplateValidator
import app.margin.domain.template.ValueError
import app.margin.ui.components.LoadingState
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import app.margin.ui.theme.LocalSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import javax.inject.Inject

/** Identifies a field value: a plain field ("key") or a field in a repeatable section instance. */
data class FieldRef(val key: String, val section: String? = null, val instance: Int? = null)

data class FormUi(
    val template: TemplateDefinition? = null,
    val values: FormValues = FormValues(),
    /** Raw text for number/date/time inputs so partially typed values aren't lost. */
    val raw: Map<FieldRef, String> = emptyMap(),
    val errors: List<ValueError> = emptyList(),
    val parseErrors: Set<FieldRef> = emptySet(),
    val missing: Boolean = false,
    val creating: Boolean = false,
)

@HiltViewModel
class TemplateFormViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val templates: TemplateRepository,
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val id: String = checkNotNull(saved.get<String>("templateId"))
    private val _ui = MutableStateFlow(FormUi())
    val ui: StateFlow<FormUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val t = templates.get(id)
            _ui.value = if (t == null) FormUi(missing = true) else FormUi(template = t, values = TemplateEngine.initialValues(t, LocalDateTime.now()))
        }
    }

    fun value(ref: FieldRef): FieldValue? {
        val v = _ui.value.values
        return if (ref.section == null) v.values[ref.key] else v.repeats[ref.section]?.getOrNull(ref.instance ?: 0)?.get(ref.key)
    }

    fun set(ref: FieldRef, value: FieldValue?) {
        val cur = _ui.value
        val v = cur.values
        val newValues = if (ref.section == null) {
            v.copy(values = if (value == null) v.values - ref.key else v.values + (ref.key to value))
        } else {
            val list = v.repeats[ref.section].orEmpty().toMutableList()
            val idx = ref.instance ?: 0
            if (idx in list.indices) list[idx] = if (value == null) list[idx] - ref.key else list[idx] + (ref.key to value)
            v.copy(repeats = v.repeats + (ref.section to list))
        }
        _ui.value = cur.copy(values = newValues, parseErrors = cur.parseErrors - ref,
            errors = cur.errors.filterNot { it.fieldKey == ref.key && it.instance == ref.instance })
    }

    /** Text input for typed fields: keeps raw text, parses when valid. */
    fun setRaw(ref: FieldRef, field: TemplateField, text: String) {
        val parsed: FieldValue? = if (text.isBlank()) null else try {
            when (field.type) {
                FieldType.NUMBER, FieldType.CURRENCY -> FieldValue.Number(BigDecimal(text.replace(",", ".").trim()))
                FieldType.DATE -> FieldValue.Date(LocalDate.parse(text.trim()))
                FieldType.TIME -> FieldValue.Time(LocalTime.parse(text.trim()))
                FieldType.DATETIME -> FieldValue.DateTime(LocalDateTime.parse(text.trim().replace(' ', 'T')))
                else -> FieldValue.Text(text)
            }
        } catch (e: NumberFormatException) { null } catch (e: DateTimeParseException) { null }
        set(ref, parsed)
        val cur = _ui.value
        _ui.value = cur.copy(raw = cur.raw + (ref to text), parseErrors = if (text.isNotBlank() && parsed == null) cur.parseErrors + ref else cur.parseErrors - ref)
    }

    fun addInstance(section: TemplateSection) {
        val cur = _ui.value
        val list = cur.values.repeats[section.key].orEmpty()
        if (list.size >= section.maxRepeat) return
        val defaults = section.fields.mapNotNull { f -> f.default?.let { d -> TemplateEngine.parseDefault(f, d, LocalDateTime.now())?.let { f.key to it } } }.toMap()
        _ui.value = cur.copy(values = cur.values.copy(repeats = cur.values.repeats + (section.key to list + defaults)))
    }

    fun removeInstance(section: TemplateSection, index: Int) {
        val cur = _ui.value
        val list = cur.values.repeats[section.key].orEmpty().toMutableList()
        if (list.size <= maxOf(1, section.minRepeat) || index !in list.indices) return
        list.removeAt(index)
        // Raw inputs are keyed by instance index; drop those for this section so they can't shift onto other rows.
        _ui.value = cur.copy(values = cur.values.copy(repeats = cur.values.repeats + (section.key to list)), raw = cur.raw.filterKeys { it.section != section.key })
    }

    fun preview(): Pair<String, String>? = _ui.value.template?.let { t -> TemplateEngine.render(t, _ui.value.values, LocalDateTime.now()).let { it.title to it.body } }

    fun create(onCreated: (String) -> Unit) = viewModelScope.launch {
        val cur = _ui.value
        val t = cur.template ?: return@launch
        val errors = TemplateValidator.validateValues(t, cur.values)
        if (errors.isNotEmpty() || cur.parseErrors.isNotEmpty()) { _ui.value = cur.copy(errors = errors); return@launch }
        _ui.value = cur.copy(creating = true)
        val r = TemplateEngine.render(t, cur.values, LocalDateTime.now())
        val s = settings.current()
        val note = notes.create(title = r.title, body = r.body, folderId = s.defaultFolderId, templateId = t.id, tags = r.tags, excludedFromAi = t.sensitive)
        templates.markUsed(t.id)
        onCreated(note.id)
    }
}

@Composable
fun TemplateFormScreen(n: Navigator, vm: TemplateFormViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var preview by rememberSaveable { mutableStateOf(false) }
    val sp = LocalSpacing.current
    val t = ui.template
    Scaffold(topBar = {
        TopAppBar(title = { Text(t?.name ?: "") },
            navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { if (t != null) IconButton(onClick = { preview = true }) { Icon(Icons.Outlined.Visibility, stringResource(R.string.preview)) } })
    }, bottomBar = {
        if (t != null) Box(Modifier.fillMaxWidth().padding(sp.l)) {
            Button(onClick = { vm.create { n.replace(Routes.editor(it)) } }, enabled = !ui.creating, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.create_note)) }
        }
    }) { p ->
        when {
            ui.missing -> app.margin.ui.components.EmptyState(Icons.Outlined.Close, stringResource(R.string.template_missing), stringResource(R.string.template_missing_body), modifier = Modifier.padding(p))
            t == null -> LoadingState(Modifier.padding(p))
            else -> Box(Modifier.fillMaxSize().padding(p), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = sp.readingWidth).verticalScroll(rememberScrollState()).padding(horizontal = sp.l), verticalArrangement = Arrangement.spacedBy(sp.m)) {
                    Text(t.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    t.disclaimer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                    if (t.sensitive) Text(stringResource(R.string.template_sensitive_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ui.errors.isNotEmpty() || ui.parseErrors.isNotEmpty()) Text(stringResource(R.string.form_has_errors), color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
                    t.sections.forEach { s -> SectionView(s, 0, ui, vm) }
                    if (t.staticBody != null && t.sections.isEmpty()) Text(stringResource(R.string.template_static_info))
                    androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 24.dp))
                }
            }
        }
    }
    if (preview) {
        val (title, body) = vm.preview() ?: ("" to "")
        AlertDialog(onDismissRequest = { preview = false }, title = { Text(title) },
            text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) { Text(body, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)) } },
            confirmButton = { TextButton(onClick = { preview = false }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun SectionView(s: TemplateSection, depth: Int, ui: FormUi, vm: TemplateFormViewModel) {
    if (!TemplateEngine.visible(s.visibleIf, ui.values)) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.title.isNotEmpty()) {
            HorizontalDivider()
            Text(s.title, style = if (depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() })
        }
        s.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (s.repeatable) {
            val instances = ui.values.repeats[s.key].orEmpty()
            instances.forEachIndexed { i, _ ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.title} ${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (instances.size > maxOf(1, s.minRepeat)) IconButton(onClick = { vm.removeInstance(s, i) }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_entry, i + 1)) }
                }
                s.fields.forEach { f -> FieldView(f, FieldRef(f.key, s.key, i), ui, vm) }
            }
            if (instances.size < s.maxRepeat) OutlinedButton(onClick = { vm.addInstance(s) }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_another, s.title)) }
        } else {
            s.fields.forEach { f -> FieldView(f, FieldRef(f.key), ui, vm) }
            s.sections.forEach { SectionView(it, depth + 1, ui, vm) }
        }
    }
}

@Composable
private fun errorText(ui: FormUi, ref: FieldRef, f: TemplateField): String? {
    if (ref in ui.parseErrors) return stringResource(when (f.type) { FieldType.DATE -> R.string.err_date_format; FieldType.TIME -> R.string.err_time_format; FieldType.DATETIME -> R.string.err_datetime_format; else -> R.string.err_number })
    val e = ui.errors.firstOrNull { it.fieldKey == ref.key && it.instance == ref.instance } ?: return null
    return when (e.kind) {
        ValueError.Kind.REQUIRED -> stringResource(R.string.err_required)
        ValueError.Kind.NOT_A_NUMBER -> stringResource(R.string.err_number)
        ValueError.Kind.BELOW_MIN -> stringResource(R.string.err_min, f.min?.toPlainString() ?: "0")
        ValueError.Kind.ABOVE_MAX -> stringResource(R.string.err_max, f.max?.toPlainString() ?: "100")
        ValueError.Kind.TOO_LONG -> stringResource(R.string.err_too_long, f.maxLength ?: 0)
        ValueError.Kind.INVALID_EMAIL -> stringResource(R.string.err_email)
        ValueError.Kind.INVALID_URL -> stringResource(R.string.err_url)
        ValueError.Kind.INVALID_OPTION -> stringResource(R.string.err_option)
        ValueError.Kind.INVALID_DATE -> stringResource(R.string.err_date_format)
    }
}

@Composable
private fun FieldView(f: TemplateField, ref: FieldRef, ui: FormUi, vm: TemplateFormViewModel) {
    if (!TemplateEngine.visible(f.visibleIf, ui.values)) return
    val value = vm.value(ref)
    val err = errorText(ui, ref, f)
    val label = f.label + if (f.required) " *" else ""
    val support: @Composable (() -> Unit)? = (err ?: f.help)?.let { msg -> { Text(msg) } }
    when (f.type) {
        FieldType.TEXT, FieldType.URL, FieldType.EMAIL -> OutlinedTextField(
            (value as? FieldValue.Text)?.value.orEmpty(), { vm.set(ref, if (it.isEmpty()) null else FieldValue.Text(it)) },
            Modifier.fillMaxWidth(), label = { Text(label) }, placeholder = f.placeholder?.let { { Text(it) } }, singleLine = true, isError = err != null, supportingText = support,
            keyboardOptions = KeyboardOptions(keyboardType = when (f.type) { FieldType.URL -> KeyboardType.Uri; FieldType.EMAIL -> KeyboardType.Email; else -> KeyboardType.Text }),
        )
        FieldType.LONG_TEXT, FieldType.RICH_TEXT -> OutlinedTextField(
            (value as? FieldValue.Text)?.value.orEmpty(), { vm.set(ref, if (it.isEmpty()) null else FieldValue.Text(it)) },
            Modifier.fillMaxWidth().heightIn(min = 96.dp), label = { Text(label) }, placeholder = f.placeholder?.let { { Text(it) } }, minLines = 3, isError = err != null, supportingText = support,
        )
        FieldType.NUMBER, FieldType.CURRENCY -> OutlinedTextField(
            ui.raw[ref] ?: (value as? FieldValue.Number)?.value?.toPlainString().orEmpty(), { vm.setRaw(ref, f, it) },
            Modifier.fillMaxWidth(), label = { Text(label + (f.currency?.let { " ($it)" } ?: "")) }, singleLine = true, isError = err != null, supportingText = support,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        FieldType.DATE, FieldType.TIME, FieldType.DATETIME -> DateTimeField(f, ref, value, ui, vm, label, err)
        FieldType.CHECKBOX -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Switch(checked = (value as? FieldValue.Bool)?.value ?: false, onCheckedChange = { vm.set(ref, FieldValue.Bool(it)) })
        }
        FieldType.SINGLE_SELECT, FieldType.MULTI_SELECT -> Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            val chosen = (value as? FieldValue.Choices)?.values.orEmpty()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                f.options.forEach { o ->
                    FilterChip(selected = o in chosen, onClick = {
                        val next = if (f.type == FieldType.SINGLE_SELECT) (if (o in chosen) emptyList() else listOf(o)) else if (o in chosen) chosen - o else chosen + o
                        vm.set(ref, if (next.isEmpty()) null else FieldValue.Choices(next))
                    }, label = { Text(o) })
                }
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        FieldType.RATING -> Column {
            Text(label, style = MaterialTheme.typography.labelLarge)
            val max = (f.max ?: BigDecimal(5)).toInt()
            val cur = (value as? FieldValue.Number)?.value?.toInt() ?: 0
            Row {
                (1..max).forEach { i ->
                    IconToggleButton(checked = i <= cur, onCheckedChange = { vm.set(ref, if (i == cur) null else FieldValue.Number(BigDecimal(i))) },
                        modifier = Modifier.semantics { stateDescription = "$cur / $max" }) {
                        Icon(if (i <= cur) Icons.Filled.Star else Icons.Outlined.StarOutline, stringResource(R.string.rating_n, i, max), tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
        FieldType.PROGRESS -> Column {
            val v = (value as? FieldValue.Number)?.value?.toFloat() ?: 0f
            Text("$label: ${v.toInt()}%", style = MaterialTheme.typography.labelLarge)
            Slider(value = v, onValueChange = { vm.set(ref, FieldValue.Number(BigDecimal(Math.round(it / 5f) * 5))) }, valueRange = 0f..100f, steps = 19)
        }
        FieldType.CHECKLIST -> ItemListField(label, err, ((value as? FieldValue.Checklist)?.items ?: emptyList()), checkable = true) { items ->
            vm.set(ref, if (items.isEmpty()) null else FieldValue.Checklist(items))
        }
        FieldType.LIST -> ItemListField(label, err, ((value as? FieldValue.Items)?.values ?: emptyList()).map { it to false }, checkable = false) { items ->
            vm.set(ref, if (items.isEmpty()) null else FieldValue.Items(items.map { it.first }))
        }
        FieldType.TABLE -> TableField(label, f.options, (value as? FieldValue.Rows)?.rows ?: emptyList()) { rows -> vm.set(ref, if (rows.isEmpty()) null else FieldValue.Rows(rows)) }
    }
}

@Composable
private fun ItemListField(label: String, err: String?, items: List<Pair<String, Boolean>>, checkable: Boolean, onChange: (List<Pair<String, Boolean>>) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        items.forEachIndexed { i, (text, checked) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (checkable) Checkbox(checked, { c -> onChange(items.toMutableList().also { it[i] = text to c }) })
                OutlinedTextField(text, { t -> onChange(items.toMutableList().also { it[i] = t to checked }) }, Modifier.weight(1f), singleLine = true)
                IconButton(onClick = { onChange(items.toMutableList().also { it.removeAt(i) }) }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_item, i + 1)) }
            }
        }
        TextButton(onClick = { onChange(items + ("" to false)) }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_item)) }
        err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun TableField(label: String, columns: List<String>, rows: List<List<String>>, onChange: (List<List<String>>) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        rows.forEachIndexed { ri, row ->
            Column(Modifier.padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.row_n, ri + 1), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange(rows.toMutableList().also { it.removeAt(ri) }) }) { Icon(Icons.Outlined.Close, stringResource(R.string.remove_item, ri + 1)) }
                }
                columns.forEachIndexed { ci, col ->
                    OutlinedTextField(row.getOrElse(ci) { "" }, { v ->
                        onChange(rows.toMutableList().also { list -> list[ri] = List(columns.size) { idx -> if (idx == ci) v else row.getOrElse(idx) { "" } } })
                    }, Modifier.fillMaxWidth(), label = { Text(col) }, singleLine = true)
                }
            }
        }
        TextButton(onClick = { onChange(rows + listOf(List(columns.size) { "" })) }) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.add_row)) }
    }
}

@Composable
private fun DateTimeField(f: TemplateField, ref: FieldRef, value: FieldValue?, ui: FormUi, vm: TemplateFormViewModel, label: String, err: String?) {
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val text = ui.raw[ref] ?: when (value) {
        is FieldValue.Date -> value.value.toString()
        is FieldValue.Time -> value.value.toString()
        is FieldValue.DateTime -> value.value.toString().replace('T', ' ').take(16)
        else -> ""
    }
    val hint = when (f.type) { FieldType.DATE -> "YYYY-MM-DD"; FieldType.TIME -> "HH:MM"; else -> "YYYY-MM-DD HH:MM" }
    OutlinedTextField(text, { vm.setRaw(ref, f, it) }, Modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(hint) }, singleLine = true,
        isError = err != null, supportingText = err?.let { { Text(it) } },
        trailingIcon = {
            Row {
                if (f.type != FieldType.TIME) IconButton(onClick = { pickDate = true }) { Icon(Icons.Outlined.CalendarMonth, stringResource(R.string.pick_date)) }
                if (f.type != FieldType.DATE) IconButton(onClick = { pickTime = true }) { Icon(Icons.Outlined.Schedule, stringResource(R.string.pick_time)) }
            }
        })
    if (pickDate) {
        val state = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    val newText = if (f.type == FieldType.DATETIME) "$d ${text.substringAfter(' ', "09:00").ifBlank { "09:00" }}" else d.toString()
                    vm.setRaw(ref, f, newText)
                }
                pickDate = false
            }) { Text(stringResource(R.string.ok)) }
        }, dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState()
        AlertDialog(onDismissRequest = { pickTime = false }, text = { TimePicker(state) }, confirmButton = {
            TextButton(onClick = {
                val t = "%02d:%02d".format(state.hour, state.minute)
                vm.setRaw(ref, f, if (f.type == FieldType.DATETIME) "${text.substringBefore(' ').ifBlank { LocalDate.now().toString() }} $t" else t)
                pickTime = false
            }) { Text(stringResource(R.string.ok)) }
        }, dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
