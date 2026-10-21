package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.brief.MeetingKind
import app.plait.data.week.BriefText
import app.plait.data.week.WeekAnalytics
import app.plait.data.week.WeekBriefs
import app.plait.data.week.WeekPreviews
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WeekBriefsTest {
    private val utc = ZoneId.of("UTC")
    private val work = (19..23).map { LocalDate.of(2026, 10, it) }
    private val next = (26..30).map { LocalDate.of(2026, 10, it) }
    private val now = Instant.parse("2026-10-23T18:00:00Z").toEpochMilli()
    private fun ev(id: String, title: String, start: String, mins: Long): CalEvent {
        val s = Instant.parse(start).toEpochMilli()
        return CalEvent(id, "", "", title, Source.Other, "", startMs = s, endMs = s + mins * 60_000)
    }

    private val events = listOf(
        ev("a", "Design review", "2026-10-19T09:00:00Z", 60),
        ev("b", "Budget talk", "2026-10-20T10:00:00Z", 90),
        ev("c", "Roadmap sync", "2026-10-20T14:00:00Z", 60),
        ev("d", "Hiring loop", "2026-10-22T09:00:00Z", 30),
    )

    private fun stats(evs: List<CalEvent> = events, open: Int = 3, noted: Set<String> = setOf("a", "b")) =
        WeekAnalytics.compute(work, evs, emptyMap(), noted, 2, open, now, utc)

    private fun preview(evs: List<CalEvent>) = WeekPreviews.compute(next, evs, emptyMap<String, MeetingKind>(), utc)

    // --- recap rules (4 meetings, 4 h 0 min? no: 60+90+60+30 = 240 min = "4 h") ---
    @Test fun recapNormal() {
        assertEquals(
            "That's your work week done, Sam. You had 4 meetings, about 4 h in total, and Tuesday was your busiest day. 3 tasks are still open and 2 meetings have no notes. Worth a look before Monday.",
            WeekBriefs.recapRules(stats(), "Sam"),
        )
    }

    @Test fun recapNoNameNoTasks() {
        assertEquals(
            "That's your work week done. You had 4 meetings, about 4 h in total, and Tuesday was your busiest day. 2 meetings have no notes.",
            WeekBriefs.recapRules(stats(open = 0), null),
        )
    }

    @Test fun recapSingularAndEmpty() {
        val one = WeekBriefs.recapRules(stats(listOf(events[0]), open = 1, noted = emptySet()), "Sam")
        assertEquals("That's your work week done, Sam. You had 1 meeting, 1 h long, on Monday. 1 task is still open and 1 meeting has no notes. Worth a look before Monday.", one)
        assertEquals("That's your work week done. It was a clear week with no meetings.", WeekBriefs.recapRules(stats(emptyList(), open = 0), ""))
    }

    // --- preview rules ---
    private val nextEvents = listOf(
        ev("p1", "Design review", "2026-10-26T09:00:00Z", 60),
        ev("p2", "Standup", "2026-10-28T10:30:00Z", 30),
        ev("p3", "Planning", "2026-10-28T14:00:00Z", 60),
        ev("p4", "One to one", "2026-10-29T15:00:00Z", 30),
        ev("p5", "Offsite", "2026-10-29T16:00:00Z", 60),
        ev("p6", "Wrap up", "2026-10-29T17:00:00Z", 60),
    )

    @Test fun previewNormal() {
        assertEquals(
            "Next week looks moderate, Sam: 6 meetings over 3 days. Thursday is the busiest with 3 meetings. The first one is Design review on Monday at 9:00 AM, and Tuesday and Friday are clear.",
            WeekBriefs.previewRules(preview(nextEvents), "Sam", utc),
        )
    }

    @Test fun previewSingleAndEmpty() {
        assertEquals("Next week is completely clear, Sam.", WeekBriefs.previewRules(preview(emptyList()), "Sam", utc))
        assertEquals("Next week is completely clear.", WeekBriefs.previewRules(preview(emptyList()), null, utc))
        assertEquals(
            "Next week looks light: just 1 meeting, Standup on Wednesday at 10:30 AM. The rest of the week is clear.",
            WeekBriefs.previewRules(preview(listOf(nextEvents[1])), null, utc),
        )
    }

    @Test fun clockIs12Hour() {
        assertEquals("12:05 AM", WeekBriefs.clock(Instant.parse("2026-10-26T00:05:00Z").toEpochMilli(), utc))
        assertEquals("12:30 PM", WeekBriefs.clock(Instant.parse("2026-10-26T12:30:00Z").toEpochMilli(), utc))
        assertEquals("3:00 PM", WeekBriefs.clock(Instant.parse("2026-10-26T15:00:00Z").toEpochMilli(), utc))
    }

    @Test fun loadThresholds() {
        assertEquals("light", WeekBriefs.load(5, 100))
        assertEquals("moderate", WeekBriefs.load(6, 100))
        assertEquals("moderate", WeekBriefs.load(15, 24 * 60))
        assertEquals("heavy", WeekBriefs.load(16, 100))
        assertEquals("heavy", WeekBriefs.load(8, 24 * 60 + 1))
    }

    // --- facts ---
    @Test fun recapFactsHoldOnlyWhatIsTrue() {
        val f = WeekBriefs.recapFacts(stats(), "Sam")
        assertTrue(f.contains("NAME: Sam"))
        assertTrue(f.contains("MEETINGS: 4"))
        assertTrue(f.contains("BUSIEST DAY: Tuesday (2 meetings, 2 h 30 min)"))
        assertTrue(f.contains("OPEN TASKS: 3"))
        assertTrue(f.contains("MEETINGS WITHOUT NOTES: 2"))
        val none = WeekBriefs.recapFacts(stats(open = 0, noted = setOf("a", "b", "c", "d")), null)
        assertTrue(!none.contains("OPEN TASKS") && !none.contains("WITHOUT NOTES") && !none.contains("NAME"))
    }

    @Test fun previewFactsHaveDayTimeTitle() {
        val f = WeekBriefs.previewFacts(preview(nextEvents), null, utc)
        assertTrue(f.contains("FIRST MEETING: Monday at 9:00 AM, Design review"))
        assertTrue(f.contains("FREE DAYS: Tuesday, Friday"))
        assertTrue(f.contains("LOAD: moderate"))
    }

    // --- verification ---
    private val facts = WeekBriefs.recapFacts(stats(), "Sam")
    private fun v(a: String?, meetings: Int = 4, must: List<String> = listOf("Tuesday")) = WeekBriefs.verify(a, facts, meetings, 70, 4, must)

    @Test fun verifyAcceptsGoodAnswer() {
        assertNotNull(v("Nice work this week, Sam. You had 4 meetings, about 4 h in total, and Tuesday was the busiest. 3 tasks are still open, so Monday may be a good time to tidy up."))
        assertNotNull(v("That's the week done, Sam. Tuesday was the busiest of your 4 meetings across a 5-day week."))
    }

    @Test fun verifyRejectsInventedNumber() {
        assertNull(v("You had 14 meetings and Tuesday was the busiest."))
        assertNull(v("You had 4 meetings, about 6.5 h, and Tuesday was the busiest."))
    }

    @Test fun verifyRejectsInventedTitleOrName() {
        assertNull(v("Tuesday was the busiest, and your \"Quarterly Planning\" ran long."))
        assertNull(v("Tuesday was the busiest, with the Board Meeting taking most of it."))
        assertNull(v("Nice work, Tuesday was the busiest. Say hi to Priya."))
    }

    @Test fun verifyRejectsContradictions() {
        assertNull(v("You had no meetings this week, though Tuesday was the busiest."))
        assertNull(v("The week was completely clear. Tuesday too."))
        assertNull(WeekBriefs.verify("A busy week with 4 meetings.", "MEETINGS: 0", 0, 70, 4))
        assertNotNull(WeekBriefs.verify("No meetings this week, so the time was yours.", "MEETINGS: 0", 0, 70, 4))
    }

    @Test fun verifyRequiresBusiestDayAndRealText() {
        assertNull(v("You had 4 meetings this week."))
        assertNull(v(null))
        assertNull(v("   "))
        assertNull(v("See https://example.com for Tuesday."))
    }

    @Test fun verifyCutsToWholeSentences() {
        val out = WeekBriefs.verify("Tuesday was busy. Four meetings. Quiet otherwise. Fine. Enough.", facts, 4, 70, 3, listOf("Tuesday"))
        assertEquals("Tuesday was busy. Four meetings. Quiet otherwise.", out)
    }

    // --- cache ---
    @Test fun positiveCacheIgnoresHashAndReadsOldFormat() {
        val raw = WeekBriefs.encode("h1", BriefText("Done, Sam.", "Gemma 4 · E2B"))
        assertEquals(BriefText("Done, Sam.", "Gemma 4 · E2B"), WeekBriefs.cached(raw))
        assertEquals(BriefText("Old.", null), WeekBriefs.cached("{\"h\":\"x\",\"t\":\"Old.\"}"))
        assertNull(WeekBriefs.cached(null))
        assertNull(WeekBriefs.cached("not json"))
        assertNull(WeekBriefs.cached(WeekBriefs.encode("h1", BriefText("", null))))
        assertNull(WeekBriefs.cached(WeekBriefs.encodeTried("h1", 0)))
    }

    @Test fun triedEntryRules() {
        val h = 3_600_000L
        val tried = WeekBriefs.encodeTried("h1", 1_000)
        assertTrue(WeekBriefs.mayTry(null, "h1", 0))
        assertTrue(WeekBriefs.mayTry("junk", "h1", 0))
        assertTrue(!WeekBriefs.mayTry(tried, "h1", 1_000 + 100 * h)) // same facts: never again
        assertTrue(!WeekBriefs.mayTry(tried, "h2", 1_000 + 5 * h))   // changed, but under 6 h
        assertTrue(WeekBriefs.mayTry(tried, "h2", 1_000 + 6 * h))    // changed and 6 h passed
    }
}
