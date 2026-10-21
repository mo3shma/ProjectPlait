package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.brief.MeetingKind
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoKind
import app.plait.data.tasks.Due
import app.plait.data.tasks.TaskIdea
import app.plait.data.week.WeekAnalytics
import app.plait.data.week.WeekEvents
import app.plait.data.week.WeekPreviews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeekStatsTest {
    private val utc = ZoneId.of("UTC")
    private val dubai = ZoneId.of("Asia/Dubai")
    private val days = (19..23).map { LocalDate.of(2026, 10, it) } // Mon..Fri
    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun ev(id: String, start: String, mins: Long, allDay: Boolean = false): CalEvent {
        val s = ms(start)
        return CalEvent(id, "", "", id, Source.Other, "", startMs = s, endMs = s + mins * 60_000, allDay = allDay)
    }

    private val week = listOf(
        ev("A", "2026-10-19T09:00:00Z", 60), ev("B", "2026-10-19T14:00:00Z", 90),
        ev("C", "2026-10-20T10:00:00Z", 30), ev("HOL", "2026-10-20T00:00:00Z", 1440, allDay = true),
        ev("D", "2026-10-21T09:00:00Z", 180),
        ev("R", "2026-10-22T11:00:00Z", 60), // running at now
        ev("F", "2026-10-23T10:00:00Z", 60), // future
        ev("OUT", "2026-10-26T10:00:00Z", 60), // next week, ignored
    )
    private val kinds = mapOf("B" to MeetingKind.External, "D" to MeetingKind.Planning)
    private val now = ms("2026-10-22T11:30:00Z")

    @Test fun realisticWeek() {
        val s = WeekAnalytics.compute(days, week, kinds, setOf("A", "D", "R"), 3, 2, now, utc)
        assertEquals(6, s.meetings)
        assertEquals(60L + 90 + 30 + 180 + 30, s.minutes) // running R counts 30 so far, future F none
        assertEquals(listOf(2, 1, 1, 1, 1), s.perDay.map { it.meetings })
        assertEquals(listOf(150L, 30L, 180L, 30L, 0L), s.perDay.map { it.minutes })
        assertEquals(days[2], s.busiest!!.date)
        assertEquals(days[4], s.quietest!!.date)
        assertEquals("D", s.longest!!.id)
        assertEquals(1, s.externalMeetings)
        assertEquals(listOf(MeetingKind.Planning to 180L, MeetingKind.General to 120L, MeetingKind.External to 90L), s.byKind)
        assertEquals(4, s.finishedMeetings)
        assertEquals(2, s.notedMeetings) // R is noted but not finished
        assertEquals(2, s.missingNotes)
        assertEquals(3, s.tasksDone); assertEquals(2, s.tasksOpen)
    }

    @Test fun emptyWeek() {
        val s = WeekAnalytics.compute(days, emptyList(), emptyMap(), emptySet(), 0, 0, now, utc)
        assertEquals(0, s.meetings); assertEquals(0L, s.minutes)
        assertNull(s.busiest); assertNull(s.longest)
        assertEquals(days[0], s.quietest!!.date) // tie: earliest
        assertTrue(s.byKind.isEmpty())
        val none = WeekAnalytics.compute(emptyList(), week, kinds, emptySet(), 0, 0, now, utc)
        assertNull(none.quietest); assertEquals(0, none.meetings)
    }

    @Test fun dayOffInTheMiddle() {
        val s = WeekAnalytics.compute(days, week.filter { it.id != "D" }, kinds, emptySet(), 0, 0, now, utc)
        assertEquals(0, s.perDay[2].meetings)
        assertEquals(days[0], s.busiest!!.date)
        assertEquals(days[2], s.quietest!!.date) // 0 min, earlier than Friday's 0
    }

    @Test fun midnightBoundaryUtc() {
        val e = listOf(ev("LATE", "2026-10-19T23:30:00Z", 60), ev("EARLY", "2026-10-20T00:00:00Z", 30), ev("SUN", "2026-10-18T23:59:00Z", 30))
        val s = WeekAnalytics.compute(days, e, emptyMap(), emptySet(), 0, 0, ms("2026-10-25T00:00:00Z"), utc)
        assertEquals(listOf(1, 1, 0, 0, 0), s.perDay.map { it.meetings }) // LATE stays on Monday, SUN is outside
        assertEquals(60L, s.perDay[0].minutes)
    }

    @Test fun zoneDecidesTheDay() {
        // 21:00Z Monday is 01:00 Tuesday in Dubai.
        val e = listOf(ev("X", "2026-10-19T21:00:00Z", 60))
        val later = ms("2026-10-25T00:00:00Z")
        assertEquals(1, WeekAnalytics.compute(days, e, emptyMap(), emptySet(), 0, 0, later, utc).perDay[0].meetings)
        val d = WeekAnalytics.compute(days, e, emptyMap(), emptySet(), 0, 0, later, dubai)
        assertEquals(listOf(0, 1, 0, 0, 0), d.perDay.map { it.meetings })
        // 20:00Z Friday is midnight Saturday in Dubai: out of the week there, in it for UTC.
        val fri = listOf(ev("Y", "2026-10-23T20:00:00Z", 60))
        assertEquals(1, WeekAnalytics.compute(days, fri, emptyMap(), emptySet(), 0, 0, later, utc).meetings)
        assertEquals(0, WeekAnalytics.compute(days, fri, emptyMap(), emptySet(), 0, 0, later, dubai).meetings)
    }

    @Test fun previewFreeDays() {
        val e = listOf(
            ev("M1", "2026-10-19T09:00:00Z", 60), ev("M2", "2026-10-19T11:00:00Z", 60), // Mon 120
            ev("W1", "2026-10-21T15:00:00Z", 120), // Wed 120
            ev("AD", "2026-10-22T00:00:00Z", 1440, allDay = true), // ignored
        )
        val p = WeekPreviews.compute(days, e, emptyMap(), utc)
        assertEquals(3, p.meetings)
        assertEquals(240L, p.minutes)
        assertEquals(listOf(days[1], days[3], days[4]), p.freeDays)
        assertEquals(days[0], p.busiest!!.date) // 120 vs 120: earliest wins
        assertNull(WeekPreviews.compute(days, emptyList(), emptyMap(), utc).busiest)
        assertEquals(days, WeekPreviews.compute(days, emptyList(), emptyMap(), utc).freeDays)
    }

    @Test fun previewDetails() {
        val e = listOf(
            ev("M1", "2026-10-19T09:00:00Z", 60), ev("W1", "2026-10-21T15:00:00Z", 120),
            ev("T3", "2026-10-20T08:00:00Z", 60), ev("T1", "2026-10-20T06:00:00Z", 60), ev("T2", "2026-10-20T07:00:00Z", 60), ev("T4", "2026-10-20T09:00:00Z", 60),
        )
        val p = WeekPreviews.compute(days, e, mapOf("W1" to MeetingKind.External), utc)
        assertEquals(listOf("T1", "T2", "T3"), p.perDay[1].titles) // up to 3, time order
        assertEquals("T1", p.perDay[1].first!!.id)
        assertEquals("M1", p.firstMeeting!!.id)
        assertEquals("W1", p.longest!!.id)
        assertEquals(1, p.externalMeetings)
        assertEquals(days[1], p.busiest!!.date) // Tue 240 min
    }

    @Test fun eventsWithinFiltersByStartDay() {
        val got = WeekEvents.within(week, days.first(), days.last(), utc).map { it.id }
        assertTrue("OUT" !in got && "F" in got && "A" in got)
        // Dubai: Monday starts at 20:00Z Sunday
        val dub = listOf(ev("S", "2026-10-18T20:30:00Z", 30), ev("T", "2026-10-18T19:30:00Z", 30))
        assertEquals(listOf("S"), WeekEvents.within(dub, days.first(), days.last(), dubai).map { it.id })
    }

    @Test fun checklistCounts() {
        val evs = listOf(ev("A", "2026-10-19T09:00:00Z", 60), ev("B", "2026-10-19T10:00:00Z", 60))
        fun memo(id: String, ev: String, kind: MemoKind, text: String) = Memo(id, ev, kind, 0, text)
        val memos = listOf(
            memo("1", "A", MemoKind.Checklist, "[x] one\n[ ] two\n[X] three"),
            memo("2", "B", MemoKind.Checklist, "[ ] four"),
            memo("3", "A", MemoKind.Text, "[x] not a checklist"),
            memo("4", "Z", MemoKind.Checklist, "[x] other meeting"),
        )
        val ideas = listOf(TaskIdea("i1", "s", "A", "t", Due.None, 0), TaskIdea("i2", "s", "Z", "t", Due.None, 0))
        assertEquals(2 to 3, WeekEvents.checklistCounts(evs, memos, ideas))
        assertEquals(0 to 0, WeekEvents.checklistCounts(emptyList(), memos, ideas))
    }
}
