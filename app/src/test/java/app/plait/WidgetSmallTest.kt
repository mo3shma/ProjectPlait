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

class WidgetSmallTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private val now = ms(21, 9)
    private val link = "https://meet.google.com/abc-defg-hij"
    private fun ev(id: String, title: String, start: Long, end: Long = start + 3_600_000L, place: String = "", join: String? = null) =
        CalEvent(id, "", "", title, Source.Other, place, conferenceUri = join, startMs = start, endMs = end, allDay = false)
    private fun model(vararg e: CalEvent) = WidgetModel.build(e.toList(), now, zone, Locale.ENGLISH)
    private fun small(vararg e: CalEvent) = WidgetModel.smallLines(model(*e))

    @Test fun withoutLinkBottomTextHoldsMore() {
        val m = model(ev("a", "Standup", ms(21, 10, 30)), ev("b", "B", ms(21, 12)), ev("c", "C", ms(21, 13)), ev("d", "D", ms(21, 14)))
        assertEquals("Wed 21", m.dateShort)
        val l = WidgetModel.smallLines(m)
        assertEquals("10:30 AM", l.timeLine)
        assertEquals("+3 more today", l.bottomText)
        assertNull(l.topEnd); assertFalse(l.showJoin); assertEquals(2, l.titleLines)
    }

    @Test fun lastOneToday() {
        assertEquals("Last one today", small(ev("a", "Solo", ms(21, 10))).bottomText)
    }

    @Test fun withLinkMoveMoreToTopAndShowPill() {
        val l = small(ev("a", "Call", ms(21, 10), join = link), ev("b", "B", ms(21, 12)), ev("c", "C", ms(21, 13)))
        assertTrue(l.showJoin); assertEquals("+2 more", l.topEnd); assertNull(l.bottomText); assertEquals(1, l.titleLines)
        val alone = small(ev("a", "Call", ms(21, 10), join = link))
        assertTrue(alone.showJoin); assertNull(alone.topEnd); assertNull(alone.bottomText)
    }

    @Test fun runningShowsNowUntilEnd() {
        val l = small(ev("a", "Workshop", ms(21, 8, 30), ms(21, 11, 30)))
        assertEquals("Now · until 11:30 AM", l.timeLine)
    }

    @Test fun clearDayNextShortOrTimeOnly() {
        val short = small(ev("a", "Sync", ms(26, 9)))
        assertEquals("A clear day", short.title); assertEquals("Next: Mon 9:00 AM", short.timeLine.take(17))
        assertEquals("Next: Mon 9:00 AM", small(ev("a", "Quarterly planning offsite", ms(26, 9))).timeLine)
        assertEquals(2, short.titleLines); assertFalse(short.showJoin); assertNull(short.bottomText)
    }

    @Test fun nothingAtAll() {
        val l = small()
        assertEquals("Nothing coming up", l.timeLine); assertEquals("A clear day", l.title)
    }

    @Test fun longAndArabicTitles() {
        assertEquals(100, small(ev("a", "x".repeat(300), ms(21, 10))).title.length)
        assertEquals("مراجعة التصميم", small(ev("a", " مراجعة التصميم ", ms(21, 10))).title)
    }

    @Test fun wideTitleLinesFollowPlace() {
        assertEquals(1, model(ev("a", "A", ms(21, 10), place = "Room 4")).titleLines)
        assertEquals(2, model(ev("a", "A", ms(21, 10))).titleLines)
    }
}
