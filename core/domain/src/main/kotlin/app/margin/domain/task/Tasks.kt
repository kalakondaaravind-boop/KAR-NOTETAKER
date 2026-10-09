package app.margin.domain.task

import app.margin.domain.model.Ids
import app.margin.domain.model.TaskItem
import app.margin.domain.model.TaskPriority
import app.margin.domain.text.Block
import app.margin.domain.text.MarkdownParser
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderPlanner {
    /** Used when a task has a due date but no time. */
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    /**
     * When the reminder should fire, or null if the task has no due date / no reminder / is done.
     * Due date+time are interpreted in the task's own zone, so a 09:00 reminder stays at 09:00 local
     * across DST changes. A time inside a DST gap (e.g. 02:30 on spring-forward day) moves forward
     * by the gap length, per java.time rules.
     */
    fun reminderAt(task: TaskItem): Instant? {
        if (task.isCompleted) return null
        task.snoozedUntil?.let { return it }
        val date = task.dueDate ?: return null
        val offset = task.reminderOffsetMinutes ?: return null
        val zone = runCatching { ZoneId.of(task.zoneId) }.getOrDefault(ZoneId.systemDefault())
        val due = ZonedDateTime.of(date, task.dueTime ?: DEFAULT_TIME, zone)
        return due.minusMinutes(offset.toLong()).toInstant()
    }

    fun dueInstant(task: TaskItem): Instant? {
        val date = task.dueDate ?: return null
        val zone = runCatching { ZoneId.of(task.zoneId) }.getOrDefault(ZoneId.systemDefault())
        return ZonedDateTime.of(date, task.dueTime ?: LocalTime.MAX, zone).toInstant()
    }

    /** Reminders in the past are not scheduled (they would fire immediately on every boot). */
    fun shouldSchedule(task: TaskItem, now: Instant): Boolean = reminderAt(task)?.isAfter(now) == true
}

enum class TaskBucket { OVERDUE, TODAY, UPCOMING, SOMEDAY, COMPLETED }

object TaskRules {
    fun bucket(task: TaskItem, now: Instant, viewerZone: ZoneId): TaskBucket {
        if (task.isCompleted) return TaskBucket.COMPLETED
        val due = task.dueDate ?: return TaskBucket.SOMEDAY
        val dueAt = ReminderPlanner.dueInstant(task)!!
        val today = now.atZone(viewerZone).toLocalDate()
        return when {
            dueAt.isBefore(now) && (task.dueTime != null || due.isBefore(today)) -> TaskBucket.OVERDUE
            due == today -> TaskBucket.TODAY
            due.isBefore(today) -> TaskBucket.OVERDUE
            else -> TaskBucket.UPCOMING
        }
    }

    val ordering: Comparator<TaskItem> = compareBy<TaskItem> { it.dueDate ?: LocalDate.MAX }
        .thenBy { it.dueTime ?: LocalTime.MAX }
        .thenByDescending { it.priority.ordinal }
        .thenBy { it.createdAt }

    data class Completion(val completed: TaskItem, val nextOccurrence: TaskItem?)

    /**
     * Completing a task. For a recurring task the completed instance stays in history and a new
     * task is created for the next occurrence (same title, notes, reminder offset, and priority).
     */
    fun complete(task: TaskItem, now: Instant, newId: () -> String = Ids::newId): Completion {
        val done = task.copy(completedAt = now, updatedAt = now, snoozedUntil = null)
        val parsed = RecurrenceRule.parse(task.recurrence) ?: return Completion(done, null)
        val due = task.dueDate ?: return Completion(done, null)
        // COUNT includes the occurrence just completed.
        val remaining = parsed.count?.let { it - 1 }
        if (remaining != null && remaining <= 0) return Completion(done, null)
        // Pin the month day so "monthly on the 31st" doesn't drift to the 28th after February.
        val rule = if (parsed.freq == RecurrenceRule.Freq.MONTHLY && parsed.byMonthDay == null && parsed.nthWeekday == null)
            parsed.copy(byMonthDay = due.dayOfMonth) else parsed
        val zone = runCatching { ZoneId.of(task.zoneId) }.getOrDefault(ZoneId.systemDefault())
        val today = now.atZone(zone).toLocalDate()
        // Skip occurrences missed while the task was overdue: the next one is on/after today.
        val after = maxOf(due, today.minusDays(1))
        val nextDate = rule.copy(count = null).next(due, after) ?: return Completion(done, null)
        val next = task.copy(
            id = newId(), dueDate = nextDate, completedAt = null, snoozedUntil = null,
            recurrence = rule.copy(count = remaining).toRRule(), createdAt = now, updatedAt = now,
        )
        return Completion(done, next)
    }

    fun snooze(task: TaskItem, until: Instant, now: Instant) = task.copy(snoozedUntil = until, updatedAt = now)

    fun validateTitle(title: String): Boolean = title.isNotBlank() && title.length <= 500
}

/** A checklist item inside a note, surfaced in the Tasks view without duplicating it. */
data class EmbeddedTask(
    val noteId: String,
    val line: Int,
    val text: String,
    val checked: Boolean,
    val dueDate: LocalDate?,
    val priority: TaskPriority,
)

object EmbeddedTasks {
    private val dueRe = Regex("""(?:📅\s*|\bdue:\s*|@due\()(\d{4}-\d{2}-\d{2})\)?""")
    private val prioRe = Regex("""(?:^|\s)(!{1,3})(?:\s|$)""")

    fun extract(noteId: String, body: String): List<EmbeddedTask> =
        MarkdownParser.parse(body).filterIsInstance<Block.ListItem>()
            .filter { it.kind == Block.ListKind.TASK && it.text.isNotBlank() }
            .map { item ->
                val due = dueRe.find(item.text)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val prio = when (prioRe.find(item.text)?.groupValues?.get(1)?.length) { 1 -> TaskPriority.LOW; 2 -> TaskPriority.MEDIUM; 3 -> TaskPriority.HIGH; else -> TaskPriority.NONE }
                EmbeddedTask(noteId, item.firstLine, item.text.trim(), item.checked, due, prio)
            }

    /** Creates a standalone task from a checklist item; the note itself is left untouched. */
    fun toTask(e: EmbeddedTask, now: Instant, zone: ZoneId): TaskItem = TaskItem(
        id = Ids.newId(), title = e.text.replace(dueRe, "").replace(prioRe, " ").trim().ifEmpty { e.text },
        noteId = e.noteId, dueDate = e.dueDate, zoneId = zone.id, priority = e.priority,
        createdAt = now, updatedAt = now,
    )
}
