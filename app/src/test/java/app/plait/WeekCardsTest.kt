package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.week.DayPreview
import app.plait.data.week.WeekDay
import app.plait.data.week.WeekStats
import app.plait.ui.screens.barHeights
import app.plait.ui.screens.previewDayLine
import app.plait.ui.screens.weekKey
import app.plait.ui.screens.weekTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeekCardsTest {
    private val d = LocalDate.of(2026, 10, 19)
    private fun ev(title: String, start: String, mins: Long = 60): CalEvent {
        val s = Instant.parse(start).toEpochMilli()
        return CalEvent(title, "", "", title, Source.Other, "", startMs = s, endMs = s + mins * 60_000)
    }
    private fun stats(meetings: Int = 0, busiest: WeekDay? = null, longest: CalEvent? = null, finished: Int = 0, noted: Int = 0, done: Int = 0, open: Int = 0) =
        WeekStats(listOf(d), meetings, 200, emptyList(), busiest, null, longest, 0, emptyList(), finished, noted, 0, done, open)

    @Test fun tilesFull() {
        val s = stats(7, WeekDay(d.plusDays(1), 3, 200), ev("Design review", "2026-10-19T09:00:00Z", 90), 6, 4, 5, 2)
        assertEquals(
            listOf("7" to "meetings", "3 h 20 min" to "in meetings", "Tuesday" to "busiest day · 3 h 20 min", "4 of 6" to "meetings noted",
                "5 done" to "tasks · 2 open", "1 h 30 min" to "longest · Design review"),
            weekTiles(s),
        )
    }

    @Test fun tilesHideMissing() {
        assertEquals(listOf("0" to "meetings", "3 h 20 min" to "in meetings"), weekTiles(stats()))
    }

    @Test fun bars() {
        val days = listOf(WeekDay(d, 2, 120), WeekDay(d, 0, 0), WeekDay(d, 1, 3), WeekDay(d, 1, 60))
        assertEquals(listOf(52f, 0f, 6f, 26f), barHeights(days, 52f))
        assertEquals(listOf(0f), barHeights(listOf(WeekDay(d, 0, 0)), 52f))
    }

    @Test fun dayLine() {
        val utc = ZoneId.of("UTC")
        assertEquals("Clear", previewDayLine(DayPreview(d, 0, 0, null, emptyList()), utc))
        val e = ev("Design review", "2026-10-26T09:00:00Z")
        assertEquals("3 meetings · first 9:00 AM Design review", previewDayLine(DayPreview(d, 3, 180, e, emptyList()), utc))
        assertTrue(previewDayLine(DayPreview(d, 1, 60, e, emptyList()), utc).startsWith("1 meeting ·"))
    }

    @Test fun key() {
        assertEquals("2026-10-19", weekKey(listOf(d, d.plusDays(1))))
        assertEquals("", weekKey(emptyList()))
    }
}
