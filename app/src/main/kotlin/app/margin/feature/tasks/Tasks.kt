@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.margin.feature.tasks

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.margin.R
import app.margin.domain.model.Clock
import app.margin.domain.model.Ids
import app.margin.domain.model.NoteSummary
import app.margin.domain.model.TaskItem
import app.margin.domain.model.TaskPriority
import app.margin.domain.organization.NoteFilter
import app.margin.domain.organization.SortField
import app.margin.domain.organization.SortOrder
import app.margin.domain.repository.NoteRepository
import app.margin.domain.repository.TaskRepository
import app.margin.domain.settings.SettingsRepository
import app.margin.domain.task.EmbeddedTask
import app.margin.domain.task.EmbeddedTasks
import app.margin.domain.task.RecurrenceRule
import app.margin.domain.task.ReminderPlanner
import app.margin.domain.task.TaskBucket
import app.margin.domain.task.TaskRules
import app.margin.domain.text.MarkdownParser
import app.margin.ui.components.EmptyState
import app.margin.ui.components.SectionHeader
import app.margin.ui.navigation.Navigator
import app.margin.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

data class EmbeddedGroup(val note: NoteSummary, val items: List<EmbeddedTask>)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
    val scheduler: AlarmReminderScheduler,
    private val clock: Clock,
) : ViewModel() {
    val open: StateFlow<List<TaskItem>> = tasks.observeOpenTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completed: StateFlow<List<TaskItem>> = tasks.observeCompletedTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Unchecked checklist items inside notes (derived from note bodies; never duplicated into the task table). */
    val embedded: StateFlow<List<EmbeddedGroup>> = notes.observeNotes(NoteFilter(hasOpenTasks = true), SortOrder(SortField.UPDATED), 50).map { list ->
        list.mapNotNull { s ->
            val body = notes.getNote(s.id)?.body ?: return@mapNotNull null
            val open = EmbeddedTasks.extract(s.id, body).filter { !it.checked }
            if (open.isEmpty()) null else EmbeddedGroup(s, open)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun now(): Instant = clock.now()

    /** Saves the task; returns whether the reminder (if any) was actually scheduled. */
    fun save(t: TaskItem, done: (scheduled: Boolean?) -> Unit) = viewModelScope.launch {
        tasks.upsert(t)
        val wantsReminder = ReminderPlanner.shouldSchedule(t, clock.now())
        done(if (wantsReminder) scheduler.schedule(t) else null)
    }

    fun complete(id: String) = viewModelScope.launch { tasks.complete(id) }
    fun reopen(id: String) = viewModelScope.launch { tasks.reopen(id) }
    fun delete(id: String) = viewModelScope.launch { tasks.delete(id) }

    /** Toggling an embedded checklist item edits only that line of the note. */
    fun toggleEmbedded(e: EmbeddedTask) = viewModelScope.launch {
        val note = notes.getNote(e.noteId) ?: return@launch
        notes.saveContent(note.id, note.title, MarkdownParser.toggleTask(note.body, e.line), note.lastCursor)
    }

    fun convertEmbedded(e: EmbeddedTask) = viewModelScope.launch { tasks.upsert(EmbeddedTasks.toTask(e, clock.now(), ZoneId.systemDefault())) }

    suspend fun defaultOffset() = settings.current().defaultReminderOffsetMinutes
}

@Composable
fun TaskRow(t: TaskItem, onToggle: () -> Unit, onClick: () -> Unit, now: Instant = Instant.now()) {
    val zone = ZoneId.systemDefault()
    val bucket = TaskRules.bucket(t, now, zone)
    val due = t.dueDate?.let { d ->
        d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + (t.dueTime?.let { " " + it.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) } ?: "")
    }
    ListItem(
        headlineContent = { Text(t.title, textDecoration = if (t.isCompleted) TextDecoration.LineThrough else null) },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                due?.let { Text(it, color = if (bucket == TaskBucket.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                if (t.recurrence != null) Icon(Icons.Outlined.Repeat, stringResource(R.string.repeats), Modifier.padding(0.dp))
                if (t.reminderOffsetMinutes != null) Icon(Icons.Outlined.Alarm, stringResource(R.string.has_reminder))
                if (t.priority == TaskPriority.HIGH) Icon(Icons.Outlined.Flag, stringResource(R.string.priority_high), tint = MaterialTheme.colorScheme.error)
                if (t.noteId != null) Icon(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.linked_note))
            }
        },
        leadingContent = { Checkbox(checked = t.isCompleted, onCheckedChange = { onToggle() }) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
fun TasksScreen(n: Navigator, vm: TasksViewModel = hiltViewModel()) {
    val open by vm.open.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val embedded by vm.embedded.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val now = vm.now()
    val zone = ZoneId.systemDefault()
    val buckets = open.groupBy { TaskRules.bucket(it, now, zone) }
    val notScheduled = stringResource(R.string.reminder_not_scheduled)
    val inexact = stringResource(R.string.reminder_inexact)

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.nav_tasks)) },
                    navigationIcon = { IconButton(onClick = n::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } })
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.tasks_open)) })
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.tasks_in_notes)) })
                    Tab(tab == 2, { tab = 2 }, text = { Text(stringResource(R.string.tasks_completed)) })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) FloatingActionButton(onClick = {
                editing = TaskItem(id = Ids.newId(), title = "", zoneId = zone.id, createdAt = now, updatedAt = now)
            }) { Icon(Icons.Filled.Add, stringResource(R.string.new_task)) }
        },
    ) { p ->
        when (tab) {
            0 -> if (open.isEmpty()) EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.tasks_empty_title), stringResource(R.string.tasks_empty_body), modifier = Modifier.padding(p))
            else LazyColumn(Modifier.fillMaxSize().padding(p)) {
                if (!vm.scheduler.notificationsAllowed() && open.any { it.reminderOffsetMinutes != null }) item {
                    InfoCard(stringResource(R.string.notifications_off), stringResource(R.string.allow)) {
                        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }
                if (!vm.scheduler.canScheduleExact() && open.any { it.reminderOffsetMinutes != null }) item {
                    InfoCard(stringResource(R.string.exact_alarms_off), stringResource(R.string.open_settings)) {
                        if (Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + context.packageName)))
                    }
                }
                listOf(TaskBucket.OVERDUE to R.string.overdue, TaskBucket.TODAY to R.string.today, TaskBucket.UPCOMING to R.string.upcoming, TaskBucket.SOMEDAY to R.string.someday).forEach { (b, label) ->
                    val list = buckets[b].orEmpty()
                    if (list.isNotEmpty()) {
                        item(key = "h$b") { SectionHeader(stringResource(label) + " · ${list.size}") }
                        items(list, key = { it.id }) { t -> TaskRow(t, onToggle = { vm.complete(t.id) }, onClick = { editing = t }, now = now) }
                    }
                }
            }
            1 -> if (embedded.isEmpty()) EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.embedded_empty_title), stringResource(R.string.embedded_empty_body), modifier = Modifier.padding(p))
            else LazyColumn(Modifier.fillMaxSize().padding(p)) {
                embedded.forEach { g ->
                    item(key = "n" + g.note.id) {
                        SectionHeader(g.note.title.ifBlank { stringResource(R.string.untitled) }) { TextButton(onClick = { n.open(Routes.editor(g.note.id)) }) { Text(stringResource(R.string.open)) } }
                    }
                    items(g.items, key = { "e" + g.note.id + ":" + it.line }) { e ->
                        ListItem(
                            headlineContent = { Text(e.text) },
                            supportingContent = e.dueDate?.let { d -> { Text(d.toString()) } },
                            leadingContent = { Checkbox(checked = e.checked, onCheckedChange = { vm.toggleEmbedded(e) }) },
                            trailingContent = { TextButton(onClick = { vm.convertEmbedded(e) }) { Text(stringResource(R.string.make_task)) } },
                        )
                    }
                }
            }
            else -> if (completed.isEmpty()) EmptyState(Icons.Outlined.TaskAlt, stringResource(R.string.completed_empty_title), stringResource(R.string.completed_empty_body), modifier = Modifier.padding(p))
            else LazyColumn(Modifier.fillMaxSize().padding(p)) {
                items(completed, key = { it.id }) { t ->
                    TaskRow(t, onToggle = { vm.reopen(t.id) }, onClick = { editing = t }, now = now)
                }
            }
        }
    }

    editing?.let { t ->
        TaskEditorSheet(t, onDismiss = { editing = null }, onDelete = { vm.delete(t.id); editing = null }, onOpenNote = { id -> editing = null; n.open(Routes.editor(id)) }, onSave = { updated ->
            editing = null
            if (updated.reminderOffsetMinutes != null && Build.VERSION.SDK_INT >= 33 && !vm.scheduler.notificationsAllowed()) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            vm.save(updated) { scheduled ->
                when (scheduled) {
                    false -> scope.launch { snackbar.showSnackbar(notScheduled) }
                    true -> if (!vm.scheduler.canScheduleExact()) scope.launch { snackbar.showSnackbar(inexact) }
                    null -> Unit
                }
            }
        })
    }
}

@Composable
private fun InfoCard(text: String, action: String, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

private enum class RepeatPreset(val label: Int) { NONE(R.string.repeat_none), DAILY(R.string.repeat_daily), WEEKDAYS(R.string.repeat_weekdays), WEEKLY(R.string.repeat_weekly), MONTHLY(R.string.repeat_monthly), YEARLY(R.string.repeat_yearly), CUSTOM(R.string.repeat_custom) }

private fun presetOf(rule: RecurrenceRule?): RepeatPreset = when {
    rule == null -> RepeatPreset.NONE
    rule.interval != 1 || rule.count != null || rule.until != null || rule.nthWeekday != null -> RepeatPreset.CUSTOM
    rule.freq == RecurrenceRule.Freq.DAILY -> RepeatPreset.DAILY
    rule.freq == RecurrenceRule.Freq.WEEKLY && rule.byDays == setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY) -> RepeatPreset.WEEKDAYS
    rule.freq == RecurrenceRule.Freq.WEEKLY -> RepeatPreset.WEEKLY
    rule.freq == RecurrenceRule.Freq.MONTHLY -> RepeatPreset.MONTHLY
    else -> RepeatPreset.YEARLY
}

@Composable
private fun TaskEditorSheet(task: TaskItem, onDismiss: () -> Unit, onDelete: () -> Unit, onOpenNote: (String) -> Unit, onSave: (TaskItem) -> Unit) {
    var title by rememberSaveable { mutableStateOf(task.title) }
    var notes by rememberSaveable { mutableStateOf(task.notes) }
    var date by rememberSaveable { mutableStateOf(task.dueDate?.toString()) }
    var time by rememberSaveable { mutableStateOf(task.dueTime?.toString()) }
    var priority by rememberSaveable { mutableStateOf(task.priority) }
    var offset by rememberSaveable { mutableStateOf(task.reminderOffsetMinutes) }
    val initialRule = remember { RecurrenceRule.parse(task.recurrence) }
    var preset by rememberSaveable { mutableStateOf(presetOf(initialRule)) }
    var customInterval by rememberSaveable { mutableStateOf((initialRule?.interval ?: 2).toString()) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun rule(): String? {
        val d = date?.let { LocalDate.parse(it) }
        return when (preset) {
            RepeatPreset.NONE -> null
            RepeatPreset.DAILY -> RecurrenceRule(RecurrenceRule.Freq.DAILY).toRRule()
            RepeatPreset.WEEKDAYS -> RecurrenceRule(RecurrenceRule.Freq.WEEKLY, byDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)).toRRule()
            RepeatPreset.WEEKLY -> RecurrenceRule(RecurrenceRule.Freq.WEEKLY, byDays = setOfNotNull(d?.dayOfWeek)).toRRule()
            RepeatPreset.MONTHLY -> RecurrenceRule(RecurrenceRule.Freq.MONTHLY, byMonthDay = d?.dayOfMonth).toRRule()
            RepeatPreset.YEARLY -> RecurrenceRule(RecurrenceRule.Freq.YEARLY).toRRule()
            RepeatPreset.CUSTOM -> initialRule?.takeIf { presetOf(it) == RepeatPreset.CUSTOM }?.copy(interval = customInterval.toIntOrNull()?.coerceIn(1, 999) ?: 1)?.toRRule()
                ?: RecurrenceRule(RecurrenceRule.Freq.DAILY, interval = customInterval.toIntOrNull()?.coerceIn(1, 999) ?: 2).toRRule()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.task_title)) }, isError = title.isBlank())
            OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.task_notes)) }, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(date ?: stringResource(R.string.set_due_date)) }
                if (date != null) OutlinedButton(onClick = { pickTime = true }) { Text(time ?: stringResource(R.string.set_time)) }
                if (date != null) TextButton(onClick = { date = null; time = null; offset = null; preset = RepeatPreset.NONE }) { Text(stringResource(R.string.clear)) }
            }
            Text(stringResource(R.string.priority), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(TaskPriority.NONE to R.string.priority_none, TaskPriority.LOW to R.string.priority_low, TaskPriority.MEDIUM to R.string.priority_medium, TaskPriority.HIGH to R.string.priority_high).forEach { (pr, l) ->
                    FilterChip(priority == pr, { priority = pr }, label = { Text(stringResource(l)) })
                }
            }
            if (date != null) {
                Text(stringResource(R.string.reminder), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to R.string.reminder_none, 0 to R.string.reminder_at_time, 15 to R.string.reminder_15, 60 to R.string.reminder_60, 1440 to R.string.reminder_day).forEach { (o, l) ->
                        FilterChip(offset == o, { offset = o }, label = { Text(stringResource(l)) })
                    }
                }
                if (time == null && offset != null) Text(stringResource(R.string.reminder_default_time, ReminderPlanner.DEFAULT_TIME.toString()), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.repeat), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RepeatPreset.entries.forEach { r -> FilterChip(preset == r, { preset = r }, label = { Text(stringResource(r.label)) }) }
                }
                if (preset == RepeatPreset.CUSTOM) OutlinedTextField(customInterval, { customInterval = it.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.repeat_every_n_days)) }, singleLine = true)
                rule()?.let { r -> RecurrenceRule.parse(r)?.let { Text(it.describe(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            task.noteId?.let { id -> TextButton(onClick = { onOpenNote(id) }) { Icon(Icons.AutoMirrored.Outlined.Notes, null); Text(stringResource(R.string.open_linked_note)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (task.title.isNotEmpty()) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                androidx.compose.material3.Button(enabled = title.isNotBlank(), onClick = {
                    onSave(task.copy(
                        title = title.trim(), notes = notes, dueDate = date?.let { LocalDate.parse(it) }, dueTime = time?.let { LocalTime.parse(it) },
                        priority = priority, reminderOffsetMinutes = if (date == null) null else offset, recurrence = if (date == null) null else rule(),
                        snoozedUntil = null,
                    ))
                }) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() })
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; pickDate = false }) { Text(stringResource(R.string.ok)) }
        }, dismissButton = { TextButton(onClick = { pickDate = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time?.let { LocalTime.parse(it).hour } ?: 9, initialMinute = time?.let { LocalTime.parse(it).minute } ?: 0)
        AlertDialog(onDismissRequest = { pickTime = false }, text = { TimePicker(state) }, confirmButton = {
            TextButton(onClick = { time = LocalTime.of(state.hour, state.minute).toString(); pickTime = false }) { Text(stringResource(R.string.ok)) }
        }, dismissButton = {
            Row {
                TextButton(onClick = { time = null; pickTime = false }) { Text(stringResource(R.string.clear)) }
                TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) }
            }
        })
    }
}
