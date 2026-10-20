package app.plait.data.ics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** A weekday, optionally the nth one of its month (1MO = first Monday, -1FR = last Friday). */
data class ByDay(val ordinal: Int?, val day: DayOfWeek)

/** The subset of RFC 5545 recurrence rules calendars actually produce. */
data class Rrule(
    val freq: String,
    val interval: Int = 1,
    val count: Int? = null,
    val until: LocalDateTime? = null,
    val byDay: List<ByDay> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    companion object {
        private val days = mapOf(
            "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
        )

        /** [untilToWall] turns the UNTIL text into a wall-clock time in the event's zone. */
        fun parse(value: String, untilToWall: (String) -> LocalDateTime?): Rrule? {
            val parts = value.split(';').mapNotNull { p ->
                val i = p.indexOf('=')
                if (i < 0) null else p.substring(0, i).uppercase() to p.substring(i + 1)
            }.toMap()
            val freq = parts["FREQ"]?.uppercase() ?: return null
            if (freq !in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")) return null
            return Rrule(
                freq = freq,
                interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                count = parts["COUNT"]?.toIntOrNull(),
                until = parts["UNTIL"]?.let(untilToWall),
                byDay = parts["BYDAY"]?.split(',')?.mapNotNull { token ->
                    val t = token.trim().uppercase()
                    val day = days[t.takeLast(2)] ?: return@mapNotNull null
                    val ord = t.dropLast(2).takeIf { it.isNotEmpty() }?.toIntOrNull()
                    ByDay(ord, day)
                } ?: emptyList(),
                byMonthDay = parts["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
                byMonth = parts["BYMONTH"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
                weekStart = parts["WKST"]?.let { days[it.uppercase()] } ?: DayOfWeek.MONDAY,
            )
        }
    }
}

object RruleExpander {
    private const val MAX_STEPS = 20_000

    /**
     * Wall-clock start times of a recurring event that begin within [from, to).
     * COUNT is honoured from the first instance, and excluded dates still use up the count, as the standard says.
     */
    fun expand(
        start: LocalDateTime,
        rule: Rrule,
        from: LocalDateTime,
        to: LocalDateTime,
        excluded: Set<LocalDateTime>,
    ): List<LocalDateTime> {
        val out = ArrayList<LocalDateTime>()
        val time: LocalTime = start.toLocalTime()
        val firstDate = start.toLocalDate()
        var emitted = 0
        var steps = 0
        var period = 0L

        loop@ while (steps++ < MAX_STEPS) {
            // Periods only move forward, so once one starts after the window we are done.
            if (periodStartOf(firstDate, rule, period).isAfter(to.toLocalDate())) break
            val dates = datesInPeriod(firstDate, rule, period).filter { !it.isBefore(firstDate) }
            for (date in dates) {
                val at = LocalDateTime.of(date, time)
                if (rule.until != null && at.isAfter(rule.until)) break@loop
                if (!at.isBefore(to)) break@loop
                emitted++
                if (rule.count != null && emitted > rule.count) break@loop
                if (!at.isBefore(from) && at !in excluded) out.add(at)
            }
            period++
        }
        return out
    }

    private fun periodStartOf(first: LocalDate, rule: Rrule, k: Long): LocalDate = when (rule.freq) {
        "DAILY" -> first.plusDays(k * rule.interval)
        "WEEKLY" -> first.with(TemporalAdjusters.previousOrSame(rule.weekStart)).plusWeeks(k * rule.interval)
        "MONTHLY" -> first.withDayOfMonth(1).plusMonths(k * rule.interval)
        else -> first.withDayOfYear(1).plusYears(k * rule.interval)
    }

    private fun datesInPeriod(first: LocalDate, rule: Rrule, k: Long): List<LocalDate> {
        val start = periodStartOf(first, rule, k)
        return when (rule.freq) {
            "DAILY" ->
                if (rule.byDay.isEmpty() || rule.byDay.any { it.day == start.dayOfWeek }) listOf(start) else emptyList()

            "WEEKLY" -> {
                val wanted = if (rule.byDay.isEmpty()) listOf(first.dayOfWeek) else rule.byDay.map { it.day }
                (0L..6L).map { start.plusDays(it) }.filter { it.dayOfWeek in wanted }
            }

            "MONTHLY" -> {
                val ym = YearMonth.from(start)
                when {
                    rule.byMonthDay.isNotEmpty() -> rule.byMonthDay.mapNotNull { monthDay(ym, it) }
                    rule.byDay.isNotEmpty() -> rule.byDay.flatMap { byDayIn(ym, it) }
                    else -> listOfNotNull(monthDay(ym, first.dayOfMonth))
                }.sorted()
            }

            else -> { // YEARLY
                val months = if (rule.byMonth.isNotEmpty()) rule.byMonth else listOf(first.monthValue)
                months.flatMap { m ->
                    val ym = YearMonth.of(start.year, m)
                    when {
                        rule.byMonthDay.isNotEmpty() -> rule.byMonthDay.mapNotNull { monthDay(ym, it) }
                        rule.byDay.isNotEmpty() -> rule.byDay.flatMap { byDayIn(ym, it) }
                        else -> listOfNotNull(monthDay(ym, first.dayOfMonth))
                    }
                }.sorted()
            }
        }
    }

    private fun monthDay(ym: YearMonth, day: Int): LocalDate? {
        val d = if (day > 0) day else ym.lengthOfMonth() + day + 1
        return if (d in 1..ym.lengthOfMonth()) ym.atDay(d) else null
    }

    private fun byDayIn(ym: YearMonth, by: ByDay): List<LocalDate> {
        val all = (1..ym.lengthOfMonth()).map { ym.atDay(it) }.filter { it.dayOfWeek == by.day }
        return when {
            by.ordinal == null -> all
            by.ordinal > 0 -> listOfNotNull(all.getOrNull(by.ordinal - 1))
            else -> listOfNotNull(all.getOrNull(all.size + by.ordinal))
        }
    }
}
