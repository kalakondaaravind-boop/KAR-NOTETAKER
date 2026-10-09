package app.margin.domain.task

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Recurrence rules, stored as a compact RFC 5545 RRULE subset so they also export cleanly
 * to calendar formats: FREQ=DAILY|WEEKLY|MONTHLY|YEARLY; INTERVAL; BYDAY (weekly days, or
 * "2TU"/"-1FR" for monthly nth weekday); BYMONTHDAY; COUNT; UNTIL (yyyyMMdd).
 *
 * Monthly on day 29–31 falls on the last day of shorter months (documented behaviour).
 */
data class RecurrenceRule(
    val freq: Freq,
    val interval: Int = 1,
    val byDays: Set<DayOfWeek> = emptySet(),
    /** Monthly "nth weekday": e.g. (2, TUESDAY) = second Tuesday; (-1, FRIDAY) = last Friday. */
    val nthWeekday: Pair<Int, DayOfWeek>? = null,
    val byMonthDay: Int? = null,
    val count: Int? = null,
    val until: LocalDate? = null,
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    init {
        require(interval in 1..999) { "interval out of range" }
        require(count == null || count in 1..10_000) { "count out of range" }
        require(byMonthDay == null || byMonthDay in 1..31) { "monthday out of range" }
        require(nthWeekday == null || nthWeekday.first in -5..5 && nthWeekday.first != 0) { "nth out of range" }
    }

    fun toRRule(): String = buildList {
        add("FREQ=${freq.name}")
        if (interval != 1) add("INTERVAL=$interval")
        if (byDays.isNotEmpty()) add("BYDAY=" + byDays.sortedBy { it.value }.joinToString(",") { code(it) })
        nthWeekday?.let { (n, d) -> add("BYDAY=$n${code(d)}") }
        byMonthDay?.let { add("BYMONTHDAY=$it") }
        count?.let { add("COUNT=$it") }
        until?.let { add("UNTIL=" + it.toString().replace("-", "")) }
    }.joinToString(";")

    /**
     * The first occurrence strictly after [after], given the series started on [start].
     * Returns null when the series has ended ([until] or [count] exhausted).
     */
    fun next(start: LocalDate, after: LocalDate): LocalDate? {
        var occurrence = 0
        var d: LocalDate? = start
        // Iterate occurrences from the start; bounded so a malformed rule can't loop forever.
        var guard = 0
        while (d != null && guard++ < 100_000) {
            occurrence++
            if (count != null && occurrence > count) return null
            if (until != null && d > until) return null
            if (d > after) return d
            d = following(start, d)
        }
        return null
    }

    /** The occurrence after [current] in the series anchored at [start], ignoring count/until. */
    private fun following(start: LocalDate, current: LocalDate): LocalDate = when (freq) {
        Freq.DAILY -> current.plusDays(interval.toLong())
        Freq.WEEKLY -> {
            if (byDays.isEmpty()) current.plusWeeks(interval.toLong())
            else {
                val days = byDays.sortedBy { it.value }
                val laterThisWeek = days.firstOrNull { it.value > current.dayOfWeek.value }
                if (laterThisWeek != null) current.with(TemporalAdjusters.nextOrSame(laterThisWeek))
                else {
                    val weekStart = current.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(interval.toLong())
                    weekStart.with(TemporalAdjusters.nextOrSame(days.first()))
                }
            }
        }
        Freq.MONTHLY -> {
            val monthsSinceStart = ChronoUnit.MONTHS.between(start.withDayOfMonth(1), current.withDayOfMonth(1))
            val target = start.withDayOfMonth(1).plusMonths(monthsSinceStart + interval)
            when {
                nthWeekday != null -> nth(target, nthWeekday.first, nthWeekday.second)
                else -> {
                    val day = byMonthDay ?: start.dayOfMonth
                    target.withDayOfMonth(minOf(day, target.lengthOfMonth()))
                }
            }
        }
        Freq.YEARLY -> {
            val years = ChronoUnit.YEARS.between(LocalDate.of(start.year, 1, 1), LocalDate.of(current.year, 1, 1))
            val y = start.year + years.toInt() + interval
            val target = LocalDate.of(y, start.month, 1)
            target.withDayOfMonth(minOf(start.dayOfMonth, target.lengthOfMonth())) // Feb 29 → Feb 28
        }
    }

    private fun nth(monthStart: LocalDate, n: Int, day: DayOfWeek): LocalDate =
        if (n > 0) {
            val d = monthStart.with(TemporalAdjusters.dayOfWeekInMonth(n, day))
            // "5th Tuesday" in a month without one → last Tuesday.
            if (d.month != monthStart.month) monthStart.with(TemporalAdjusters.lastInMonth(day)) else d
        } else {
            val d = monthStart.with(TemporalAdjusters.lastInMonth(day)).plusWeeks((n + 1).toLong())
            if (d.month != monthStart.month) monthStart.with(TemporalAdjusters.firstInMonth(day)) else d
        }

    /** Human-readable summary (English; the UI layer localises from the structured fields). */
    fun describe(): String {
        val every = if (interval == 1) "Every" else "Every $interval"
        val unit = when (freq) { Freq.DAILY -> "day"; Freq.WEEKLY -> "week"; Freq.MONTHLY -> "month"; Freq.YEARLY -> "year" }
        val base = "$every ${unit}${if (interval == 1) "" else "s"}"
        val detail = when {
            byDays.isNotEmpty() -> " on " + byDays.sortedBy { it.value }.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase).take(3) }
            nthWeekday != null -> " on the " + ordinal(nthWeekday.first) + " " + nthWeekday.second.name.lowercase().replaceFirstChar(Char::uppercase)
            byMonthDay != null -> " on day $byMonthDay"
            else -> ""
        }
        val end = when {
            count != null -> ", $count times"
            until != null -> ", until $until"
            else -> ""
        }
        return base + detail + end
    }

    private fun ordinal(n: Int) = when (n) { -1 -> "last"; 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th" }

    companion object {
        private val codes = mapOf("MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY)

        fun code(d: DayOfWeek) = codes.entries.first { it.value == d }.key

        /** Parses the supported RRULE subset; returns null (never throws) for anything unsupported. */
        fun parse(rrule: String?): RecurrenceRule? {
            if (rrule.isNullOrBlank()) return null
            return try {
                val parts = rrule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate {
                    val (k, v) = it.split('=', limit = 2); k.trim().uppercase() to v.trim().uppercase()
                }
                val freq = Freq.valueOf(parts["FREQ"] ?: return null)
                var byDays = emptySet<DayOfWeek>()
                var nth: Pair<Int, DayOfWeek>? = null
                parts["BYDAY"]?.let { v ->
                    val m = Regex("""^(-?\d)([A-Z]{2})$""").matchEntire(v)
                    if (m != null) nth = m.groupValues[1].toInt() to (codes[m.groupValues[2]] ?: return null)
                    else byDays = v.split(',').map { codes[it] ?: return null }.toSet()
                }
                RecurrenceRule(
                    freq = freq,
                    interval = parts["INTERVAL"]?.toInt() ?: 1,
                    byDays = byDays,
                    nthWeekday = nth,
                    byMonthDay = parts["BYMONTHDAY"]?.toInt(),
                    count = parts["COUNT"]?.toInt(),
                    until = parts["UNTIL"]?.take(8)?.let { LocalDate.parse("${it.take(4)}-${it.substring(4, 6)}-${it.substring(6, 8)}") },
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
