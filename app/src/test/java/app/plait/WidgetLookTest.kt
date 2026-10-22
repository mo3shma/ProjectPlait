package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.widget.Phase
import app.plait.widget.WidgetModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class WidgetLookTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private val link = "https://meet.google.com/abc-defg-hij"
    private fun ev(id: String, title: String, start: Long, end: Long = start + 3_600_000L, place: String = "", join: String? = null) =
        CalEvent(id, "", "", title, Source.Other, place, conferenceUri = join, startMs = start, endMs = end, allDay = false)
    private fun model(vararg e: CalEvent, at: Long = ms(21, 9)) = WidgetModel.build(e.toList(), at, zone, Locale.ENGLISH)
    private fun wide(vararg e: CalEvent, at: Long = ms(21, 9)) = WidgetModel.wideLines(model(*e, at = at))
    private fun small(vararg e: CalEvent) = WidgetModel.smallLines(model(*e))

    @Test fun phaseBoundaries() {
        assertEquals(Phase.Morning, Phase.at(ms(21, 11, 59), zone))
        assertEquals(Phase.Midday, Phase.at(ms(21, 12), zone))
        assertEquals(Phase.Midday, Phase.at(ms(21, 16, 59), zone))
        assertEquals(Phase.Evening, Phase.at(ms(21, 17), zone))
        assertEquals(Phase.Evening, Phase.at(ms(21, 23, 59), zone))
        assertEquals(Phase.Morning, Phase.at(ms(22, 0), zone))
    }

    @Test fun nextBoundaryIsStrictlyAfter() {
        assertEquals(ms(21, 12), Phase.nextBoundary(ms(21, 11, 59), zone))
        assertEquals(ms(21, 17), Phase.nextBoundary(ms(21, 12), zone))
        assertEquals(ms(21, 17), Phase.nextBoundary(ms(21, 16, 59), zone))
        assertEquals(ms(22, 0), Phase.nextBoundary(ms(21, 17), zone))
        assertEquals(ms(22, 12), Phase.nextBoundary(ms(22, 0), zone))
    }

    @Test fun nextChangeIsEarliestOfMeetingAndBoundary() {
        assertEquals(ms(21, 12), model(ev("a", "Late", ms(21, 15)), at = ms(21, 9)).nextChangeMs)
        assertEquals(ms(21, 10), model(ev("a", "Soon", ms(21, 10)), at = ms(21, 9)).nextChangeMs)
        assertEquals(ms(21, 17), model(ev("a", "Late", ms(21, 20)), at = ms(21, 13)).nextChangeMs)
        assertEquals(ms(21, 23), model(ev("a", "Late", ms(21, 23)), at = ms(21, 18)).nextChangeMs)
        assertEquals(ms(22, 0), model(ev("a", "Tomorrow", ms(22, 9)), at = ms(21, 18)).nextChangeMs)
        assertEquals(ms(21, 12), model(at = ms(21, 11, 59)).nextChangeMs)
        assertEquals(Phase.Evening, model(at = ms(21, 18)).phase)
    }

    @Test fun countCapsule() {
        assertNull(wide().countLabel)
        assertEquals("1 meeting", wide(ev("a", "A", ms(21, 10))).countLabel)
        assertEquals("3 meetings", wide(ev("a", "A", ms(21, 10)), ev("b", "B", ms(21, 11)), ev("c", "C", ms(21, 12))).countLabel)
    }

    @Test fun thenAndMoreLines() {
        val m = model(ev("a", "A", ms(21, 10)), ev("b", "Design review", ms(21, 12)), ev("c", "C", ms(21, 14)), ev("d", "D", ms(21, 15)))
        assertEquals("Then Design review 12:00 PM", m.thenLine); assertEquals("+2 more", m.moreLine)
        val l = WidgetModel.wideLines(m)
        assertEquals("Then Design review 12:00 PM", l.then); assertEquals("+2 more", l.more); assertNull(l.lastText)
        val two = model(ev("a", "A", ms(21, 10)), ev("b", "B", ms(21, 11)))
        assertNull(two.moreLine); assertEquals("Then B 11:00 AM", two.thenLine)
        val last = wide(ev("a", "A", ms(21, 10)))
        assertNull(last.then); assertNull(last.more); assertEquals("Last one today", last.lastText)
    }

    @Test fun timeLineUpcomingAndRunning() {
        val up = wide(ev("a", "A", ms(21, 13, 30), ms(21, 14, 30)))
        assertEquals("1:30 – 2:30 PM", up.timeLine); assertFalse(up.nowChip)
        assertEquals("11:30 AM – 12:30 PM", wide(ev("a", "A", ms(21, 11, 30), ms(21, 12, 30))).timeLine)
        val run = wide(ev("a", "A", ms(21, 8, 30), ms(21, 10, 30)))
        assertEquals("until 10:30 AM", run.timeLine); assertTrue(run.nowChip)
    }

    @Test fun clearDayWide() {
        val l = wide(ev("a", "Team stand-up", ms(26, 9)))
        assertNull(l.timeLine); assertFalse(l.showJoin); assertNull(l.countLabel)
        assertEquals("A clear day", l.title); assertEquals(22, l.titleSp)
        assertEquals("Next: Mon 9:00 AM Team stand-up", l.sub)
        assertEquals("Nothing coming up", wide().sub)
    }

    @Test fun joinAndPlaceWide() {
        val call = wide(ev("a", "A", ms(21, 10), join = link))
        assertTrue(call.showJoin); assertEquals("Online", call.sub); assertEquals(1, call.titleLines); assertEquals(20, call.titleSp)
        val none = wide(ev("a", "A", ms(21, 10)))
        assertFalse(none.showJoin); assertNull(none.sub); assertEquals(1, none.titleLines)
    }

    @Test fun smallRunningAndJoin() {
        val run = small(ev("a", "A", ms(21, 8), ms(21, 11), join = link), ev("b", "B", ms(21, 12)))
        assertEquals("Now", run.topEnd); assertEquals("until 11:00 AM", run.timeLine); assertTrue(run.showJoin)
        assertEquals("1:30 PM", small(ev("a", "A", ms(21, 13, 30))).timeLine)
    }

    @Test fun longAndArabicTitlesKeepBudget() {
        val titles = listOf("x".repeat(300), "مراجعة التصميم الأسبوعية للفريق", "MOBIHITS & Mobizone / Weekly")
        for (t in titles) for (j in listOf(null, link)) for (p in listOf("", "Room 4")) {
            val m = model(ev("a", t, ms(21, 10), place = p, join = j), ev("b", "B", ms(21, 12)))
            val w = WidgetModel.wideLines(m)
            assertTrue("wide $t $j $p", WidgetModel.wideMainDp(w) <= WidgetModel.WIDE_MAIN_DP)
            assertTrue("small $t $j $p", WidgetModel.smallDp(WidgetModel.smallLines(m), false) <= WidgetModel.SMALL_INNER_DP)
        }
    }

    @Test fun worstCaseBudgetsIncludingRunningAndClear() {
        val run = model(ev("a", "A", ms(21, 8), ms(21, 11), join = link, place = "Room"), ev("b", "B", ms(21, 12)))
        assertTrue(WidgetModel.wideMainDp(WidgetModel.wideLines(run)) <= WidgetModel.WIDE_MAIN_DP)
        for (m in listOf(model(ev("a", "Sync", ms(26, 9))), model())) {
            assertTrue(WidgetModel.wideMainDp(WidgetModel.wideLines(m)) <= WidgetModel.WIDE_MAIN_DP)
            assertTrue(WidgetModel.smallDp(WidgetModel.smallLines(m), true) <= WidgetModel.SMALL_INNER_DP)
        }
    }
}
