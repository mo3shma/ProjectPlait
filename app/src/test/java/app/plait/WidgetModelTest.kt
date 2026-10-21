package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.widget.WidgetModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class WidgetModelTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private val now = ms(21, 9)
    private fun ev(id: String, title: String, start: Long, end: Long = start + 3_600_000L, place: String = "", link: String? = null, allDay: Boolean = false) =
        CalEvent(id, "", "", title, Source.Other, place, conferenceUri = link, startMs = start, endMs = end, allDay = allDay)
    private fun build(vararg e: CalEvent, at: Long = now) = WidgetModel.build(e.toList(), at, zone, Locale.ENGLISH)

    @Test fun nextThenAndMore() {
        val m = build(ev("a", "Standup", ms(21, 10, 30)), ev("b", "Design review", ms(21, 12)), ev("c", "1:1", ms(21, 14)), ev("d", "Retro", ms(21, 16)))
        assertEquals("Wed 21 Oct", m.date)
        assertEquals("Standup", m.title)
        assertEquals("10:30 AM – 11:30 AM", m.whenLine)
        assertFalse(m.running)
        assertEquals("Then Design review 12:00 PM · +2 more", m.footer)
        assertEquals(ms(21, 10, 30), m.nextChangeMs)
    }

    @Test fun lastOneAndSingleThen() {
        assertEquals("Last one today", build(ev("a", "Standup", ms(21, 10))).footer)
        assertEquals("Then B 11:00 AM", build(ev("a", "A", ms(21, 10)), ev("b", "B", ms(21, 11))).footer)
    }

    @Test fun allDayIgnoredAndPastDropped() {
        val m = build(ev("x", "Holiday", ms(21, 0), ms(22, 0), allDay = true), ev("p", "Past", ms(21, 7), ms(21, 8)), ev("a", "Real", ms(21, 11)))
        assertEquals("Real", m.title)
        assertEquals("Last one today", m.footer)
    }

    @Test fun runningMeetingShowsNowAndChangesAtItsEnd() {
        val m = build(ev("a", "Workshop", ms(21, 8, 30), ms(21, 9, 45)), ev("b", "Next", ms(21, 10)))
        assertTrue(m.running)
        assertEquals("Now · 8:30 AM – 9:45 AM", m.whenLine)
        assertEquals(ms(21, 9, 45), m.nextChangeMs)
    }

    @Test fun placeLinkAndOnline() {
        assertEquals("Room 4", build(ev("a", "A", ms(21, 10), place = "Room 4")).place)
        val online = build(ev("a", "A", ms(21, 10), link = "https://meet.google.com/abc-defg-hij"))
        assertEquals("Online", online.place)
        assertEquals("https://meet.google.com/abc-defg-hij", online.joinUrl)
        val none = build(ev("a", "A", ms(21, 10)))
        assertNull(none.place); assertNull(none.joinUrl)
    }

    @Test fun emptyDayWithFutureMeeting() {
        val m = build(ev("a", "Team stand-up", ms(26, 9)), ev("h", "Holiday", ms(22, 0), ms(23, 0), allDay = true))
        assertTrue(m.clearDay)
        assertEquals("A clear day", m.title)
        assertEquals("Next: Mon 9:00 AM Team stand-up", m.whenLine)
        assertNull(m.footer)
        assertEquals(ms(22, 0), m.nextChangeMs)
    }

    @Test fun nothingAtAll() {
        val m = build()
        assertTrue(m.clearDay)
        assertEquals("Nothing coming up", m.whenLine)
    }

    @Test fun titleTrimming() {
        assertEquals("Untitled", WidgetModel.clean("   \n "))
        assertEquals("a b c", WidgetModel.clean("  a\n b\t c "))
        val long = WidgetModel.clean("x".repeat(300))
        assertEquals(100, long.length)
        assertTrue(long.endsWith("…"))
        assertEquals("مراجعة التصميم", build(ev("a", " مراجعة التصميم ", ms(21, 10))).title)
    }
}
