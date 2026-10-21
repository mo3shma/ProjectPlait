package app.plait

import app.plait.data.week.WorkWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate

class WorkWeekTest {
    private val sun = LocalDate.of(2026, 10, 18)
    private fun d(day: DayOfWeek) = sun.plusDays(((day.value - SUNDAY.value + 7) % 7).toLong())
    private fun days(from: LocalDate) = List(5) { from.plusDays(it.toLong()) }

    @Test fun anchorIsSunday() = assertEquals(SUNDAY, sun.dayOfWeek)

    @Test fun workAndLastDays() {
        val cases = mapOf(
            MONDAY to (setOf(SATURDAY, SUNDAY) to FRIDAY),
            SUNDAY to (setOf(FRIDAY, SATURDAY) to THURSDAY),
            SATURDAY to (setOf(THURSDAY, FRIDAY) to WEDNESDAY),
        )
        for ((start, expect) in cases) for (day in DayOfWeek.entries) {
            assertEquals("$start/$day work", day !in expect.first, WorkWeek.isWorkDay(d(day), start))
            assertEquals("$start/$day last", day == expect.second, WorkWeek.isLastWorkDay(d(day), start))
        }
    }

    @Test fun startMonday() {
        val mon = d(MONDAY)
        assertEquals(days(mon), WorkWeek.thisWorkWeek(d(WEDNESDAY), MONDAY))
        assertEquals(days(mon.plusWeeks(1)), WorkWeek.nextWorkWeek(d(WEDNESDAY), MONDAY))
        assertEquals(days(mon), WorkWeek.thisWorkWeek(d(FRIDAY), MONDAY))
        assertEquals(days(mon.plusWeeks(1)), WorkWeek.nextWorkWeek(d(FRIDAY), MONDAY))
        assertEquals(days(mon), WorkWeek.thisWorkWeek(d(SATURDAY), MONDAY))
        assertEquals(days(mon.plusWeeks(1)), WorkWeek.nextWorkWeek(d(SATURDAY), MONDAY))
        // Sunday 18th belongs to the week that started Monday 12th; next week starts Monday 19th.
        assertEquals(days(mon.minusWeeks(1)), WorkWeek.thisWorkWeek(sun, MONDAY))
        assertEquals(days(mon), WorkWeek.nextWorkWeek(sun, MONDAY))
        assertEquals(d(FRIDAY), WorkWeek.lastWorkDay(d(MONDAY), MONDAY))
        assertEquals("Starts Monday, works Mon to Fri", WorkWeek.label(MONDAY))
    }

    @Test fun startSunday() {
        assertEquals(days(sun), WorkWeek.thisWorkWeek(d(MONDAY), SUNDAY))
        assertEquals(days(sun.plusWeeks(1)), WorkWeek.nextWorkWeek(d(MONDAY), SUNDAY))
        assertEquals(days(sun), WorkWeek.thisWorkWeek(d(THURSDAY), SUNDAY))
        assertEquals(days(sun.plusWeeks(1)), WorkWeek.nextWorkWeek(d(THURSDAY), SUNDAY))
        assertEquals(days(sun), WorkWeek.thisWorkWeek(d(FRIDAY), SUNDAY))
        assertEquals(days(sun.plusWeeks(1)), WorkWeek.nextWorkWeek(d(FRIDAY), SUNDAY))
        assertEquals(days(sun.plusWeeks(1)), WorkWeek.nextWorkWeek(d(SATURDAY), SUNDAY))
        assertEquals(days(sun.plusWeeks(1)), WorkWeek.nextWorkWeek(sun, SUNDAY))
        assertEquals(sun, WorkWeek.weekStart(d(SATURDAY), SUNDAY))
        assertEquals("Starts Sunday, works Sun to Thu", WorkWeek.label(SUNDAY))
    }

    @Test fun startSaturday() {
        val sat = sun.minusDays(1)
        assertEquals(days(sat), WorkWeek.thisWorkWeek(d(MONDAY), SATURDAY))
        assertEquals(days(sat), WorkWeek.thisWorkWeek(d(WEDNESDAY), SATURDAY))
        assertEquals(days(sat.plusWeeks(1)), WorkWeek.nextWorkWeek(d(WEDNESDAY), SATURDAY))
        assertEquals(days(sat.plusWeeks(1)), WorkWeek.nextWorkWeek(d(THURSDAY), SATURDAY))
        assertEquals(days(sat.plusWeeks(1)), WorkWeek.nextWorkWeek(d(FRIDAY), SATURDAY))
        assertEquals(days(sat.plusWeeks(1)), WorkWeek.nextWorkWeek(sat, SATURDAY))
        assertEquals("Starts Saturday, works Sat to Wed", WorkWeek.label(SATURDAY))
    }

    @Test fun nextWeekAlwaysStrictlyAfter() {
        for (start in DayOfWeek.entries) for (i in 0 until 21) {
            val date = sun.plusDays(i.toLong())
            val next = WorkWeek.nextWorkWeek(date, start)
            assertEquals(start, next.first().dayOfWeek)
            assertTrue("$start $date", next.first() > date && next.first() <= date.plusDays(7))
            assertEquals(WorkWeek.thisWorkWeek(date, start), WorkWeek.thisWorkWeek(date, start).filter { WorkWeek.isWorkDay(it, start) })
        }
    }
}
