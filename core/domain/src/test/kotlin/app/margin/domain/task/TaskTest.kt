package app.margin.domain.task

import app.margin.domain.model.TaskItem
import app.margin.domain.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskTest {
    private fun d(s: String): LocalDate = LocalDate.parse(s)
    private val ny = ZoneId.of("America/New_York")

    @Test fun dailyAndInterval() {
        val r = RecurrenceRule(RecurrenceRule.Freq.DAILY, interval = 3)
        assertEquals(d("2025-01-04"), r.next(d("2025-01-01"), d("2025-01-01")))
        assertEquals(d("2025-01-07"), r.next(d("2025-01-01"), d("2025-01-05")))
    }

    @Test fun weeklyByDays() {
        val r = RecurrenceRule(RecurrenceRule.Freq.WEEKLY, byDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        // 2025-01-06 is a Monday
        assertEquals(d("2025-01-09"), r.next(d("2025-01-06"), d("2025-01-06")))
        assertEquals(d("2025-01-13"), r.next(d("2025-01-06"), d("2025-01-09")))
        val biweekly = r.copy(interval = 2)
        assertEquals(d("2025-01-20"), biweekly.next(d("2025-01-06"), d("2025-01-09")))
    }

    @Test fun monthlyClampsToMonthEnd() {
        val r = RecurrenceRule(RecurrenceRule.Freq.MONTHLY, byMonthDay = 31)
        assertEquals(d("2025-02-28"), r.next(d("2025-01-31"), d("2025-01-31")))
        assertEquals(d("2025-03-31"), r.next(d("2025-01-31"), d("2025-02-28")))
        assertEquals(d("2024-02-29"), r.next(d("2024-01-31"), d("2024-01-31")))
    }

    @Test fun monthlyNthWeekday() {
        val lastFriday = RecurrenceRule(RecurrenceRule.Freq.MONTHLY, nthWeekday = -1 to DayOfWeek.FRIDAY)
        assertEquals(d("2025-02-28"), lastFriday.next(d("2025-01-31"), d("2025-01-31")))
        val secondTue = RecurrenceRule(RecurrenceRule.Freq.MONTHLY, nthWeekday = 2 to DayOfWeek.TUESDAY)
        assertEquals(d("2025-02-11"), secondTue.next(d("2025-01-14"), d("2025-01-14")))
    }

    @Test fun yearlyLeapDay() {
        val r = RecurrenceRule(RecurrenceRule.Freq.YEARLY)
        assertEquals(d("2025-02-28"), r.next(d("2024-02-29"), d("2024-02-29")))
    }

    @Test fun countAndUntil() {
        val r = RecurrenceRule(RecurrenceRule.Freq.DAILY, count = 3)
        assertEquals(d("2025-01-03"), r.next(d("2025-01-01"), d("2025-01-02")))
        assertNull(r.next(d("2025-01-01"), d("2025-01-03")))
        val u = RecurrenceRule(RecurrenceRule.Freq.DAILY, until = d("2025-01-02"))
        assertNull(u.next(d("2025-01-01"), d("2025-01-02")))
    }

    @Test fun rruleRoundTripAndBadInput() {
        val r = RecurrenceRule(RecurrenceRule.Freq.WEEKLY, 2, setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY), count = 5)
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,FR;COUNT=5", r.toRRule())
        assertEquals(r, RecurrenceRule.parse(r.toRRule()))
        val m = RecurrenceRule(RecurrenceRule.Freq.MONTHLY, nthWeekday = -1 to DayOfWeek.FRIDAY, until = d("2026-01-01"))
        assertEquals(m, RecurrenceRule.parse(m.toRRule()))
        listOf("", "FREQ=HOURLY", "FREQ=DAILY;INTERVAL=0", "FREQ=WEEKLY;BYDAY=XX", "garbage", "FREQ=DAILY;UNTIL=2025").forEach {
            assertNull(it, RecurrenceRule.parse(it))
        }
        assertEquals("Every 2 weeks on Mon, Fri, 5 times", r.describe())
    }

    private fun task(due: LocalDate?, time: LocalTime? = null, offset: Int? = 0, rec: String? = null, zone: ZoneId = ny) = TaskItem(
        id = "t", title = "Pay rent", dueDate = due, dueTime = time, zoneId = zone.id, reminderOffsetMinutes = offset, recurrence = rec,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test fun reminderTimeIsLocalAcrossDst() {
        // US DST starts 2025-03-09. A 09:00 reminder stays 09:00 local before and after.
        val before = ReminderPlanner.reminderAt(task(d("2025-03-08"), LocalTime.of(9, 0)))!!
        val after = ReminderPlanner.reminderAt(task(d("2025-03-10"), LocalTime.of(9, 0)))!!
        assertEquals(LocalTime.of(9, 0), before.atZone(ny).toLocalTime())
        assertEquals(LocalTime.of(9, 0), after.atZone(ny).toLocalTime())
        // 02:30 doesn't exist on DST day → moves forward to 03:30.
        val gap = ReminderPlanner.reminderAt(task(d("2025-03-09"), LocalTime.of(2, 30)))!!
        assertEquals(LocalTime.of(3, 30), gap.atZone(ny).toLocalTime())
        // Default time and offsets.
        assertEquals(LocalTime.of(8, 30), ReminderPlanner.reminderAt(task(d("2025-05-01"), offset = 30))!!.atZone(ny).toLocalTime())
        assertNull(ReminderPlanner.reminderAt(task(d("2025-05-01"), offset = null)))
        assertNull(ReminderPlanner.reminderAt(task(null)))
    }

    @Test fun pastRemindersAreNotScheduled() {
        val t = task(d("2025-01-01"), LocalTime.NOON)
        assertFalse(ReminderPlanner.shouldSchedule(t, Instant.parse("2025-06-01T00:00:00Z")))
        assertTrue(ReminderPlanner.shouldSchedule(t, Instant.parse("2024-12-31T00:00:00Z")))
        assertFalse(ReminderPlanner.shouldSchedule(t.copy(completedAt = Instant.EPOCH), Instant.parse("2024-12-31T00:00:00Z")))
    }

    @Test fun buckets() {
        val now = ZonedDateTime.of(2025, 5, 10, 12, 0, 0, 0, ny).toInstant()
        assertEquals(TaskBucket.OVERDUE, TaskRules.bucket(task(d("2025-05-09")), now, ny))
        assertEquals(TaskBucket.TODAY, TaskRules.bucket(task(d("2025-05-10")), now, ny))
        assertEquals(TaskBucket.OVERDUE, TaskRules.bucket(task(d("2025-05-10"), LocalTime.of(9, 0)), now, ny))
        assertEquals(TaskBucket.UPCOMING, TaskRules.bucket(task(d("2025-05-11")), now, ny))
        assertEquals(TaskBucket.SOMEDAY, TaskRules.bucket(task(null), now, ny))
        assertEquals(TaskBucket.COMPLETED, TaskRules.bucket(task(null).copy(completedAt = now), now, ny))
    }

    @Test fun completingRecurringTaskCreatesNextOccurrence() {
        val now = ZonedDateTime.of(2025, 1, 31, 10, 0, 0, 0, ny).toInstant()
        val t = task(d("2025-01-31"), rec = "FREQ=MONTHLY")
        val c = TaskRules.complete(t, now) { "next" }
        assertEquals(now, c.completed.completedAt)
        val n = c.nextOccurrence!!
        assertEquals("next", n.id)
        assertEquals(d("2025-02-28"), n.dueDate)
        assertNull(n.completedAt)
        // Pinned month day prevents drift: the occurrence after Feb 28 is Mar 31.
        val c2 = TaskRules.complete(n, ZonedDateTime.of(2025, 2, 28, 10, 0, 0, 0, ny).toInstant()) { "n2" }
        assertEquals(d("2025-03-31"), c2.nextOccurrence!!.dueDate)
    }

    @Test fun overdueRecurringSkipsMissedOccurrences() {
        val now = ZonedDateTime.of(2025, 1, 20, 10, 0, 0, 0, ny).toInstant()
        val c = TaskRules.complete(task(d("2025-01-01"), rec = "FREQ=DAILY"), now) { "x" }
        assertEquals(d("2025-01-20"), c.nextOccurrence!!.dueDate)
    }

    @Test fun countLimitedSeriesEnds() {
        val now = ZonedDateTime.of(2025, 1, 1, 10, 0, 0, 0, ny).toInstant()
        val c = TaskRules.complete(task(d("2025-01-01"), rec = "FREQ=DAILY;COUNT=2"), now) { "x" }
        assertEquals("FREQ=DAILY;COUNT=1", c.nextOccurrence!!.recurrence)
        assertNull(TaskRules.complete(c.nextOccurrence!!, now.plusSeconds(86_400)) { "y" }.nextOccurrence)
        assertNull(TaskRules.complete(task(d("2025-01-01")), now).nextOccurrence)
    }

    @Test fun embeddedTasks() {
        val body = "# List\n- [ ] Call bank due:2025-04-01 !!!\n- [x] Done thing\n- [ ] \n- normal bullet"
        val e = EmbeddedTasks.extract("n1", body)
        assertEquals(2, e.size)
        assertEquals(1, e[0].line)
        assertEquals(d("2025-04-01"), e[0].dueDate)
        assertEquals(TaskPriority.HIGH, e[0].priority)
        assertTrue(e[1].checked)
        val t = EmbeddedTasks.toTask(e[0], Instant.EPOCH, ny)
        assertEquals("Call bank", t.title)
        assertEquals("n1", t.noteId)
        assertEquals(d("2025-04-01"), t.dueDate)
    }

    @Test fun ordering() {
        val a = task(d("2025-01-02")).copy(id = "a")
        val b = task(d("2025-01-01")).copy(id = "b", priority = TaskPriority.LOW)
        val c = task(d("2025-01-01")).copy(id = "c", priority = TaskPriority.HIGH)
        val n = task(null).copy(id = "n")
        assertEquals(listOf("c", "b", "a", "n"), listOf(a, b, c, n).sortedWith(TaskRules.ordering).map { it.id })
    }
}
