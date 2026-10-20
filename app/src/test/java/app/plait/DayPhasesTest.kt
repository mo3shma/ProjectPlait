package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.brief.DayAnalytics
import app.plait.data.brief.MeetingKind
import app.plait.data.brief.Phase
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoJson
import app.plait.data.memo.MemoKind
import app.plait.data.memo.OcrText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val Z = ZoneId.of("UTC")
private fun t(iso: String) = Instant.parse(iso).toEpochMilli()
private fun m(id: String, from: String, to: String) = CalEvent(id, "", "", id, Source.Google, "", startMs = t(from), endMs = t(to))

class DayPhasesTest {
    @Test fun phaseFollowsTheClock() {
        assertEquals(Phase.Morning, Phase.at(t("2026-10-19T06:00:00Z"), Z))
        assertEquals(Phase.Morning, Phase.at(t("2026-10-19T11:59:00Z"), Z))
        assertEquals(Phase.Afternoon, Phase.at(t("2026-10-19T12:00:00Z"), Z))
        assertEquals(Phase.Afternoon, Phase.at(t("2026-10-19T16:59:00Z"), Z))
        assertEquals(Phase.Evening, Phase.at(t("2026-10-19T17:00:00Z"), Z))
        assertEquals(Phase.Evening, Phase.at(t("2026-10-19T23:30:00Z"), Z))
        assertEquals("Good evening", Phase.Evening.greeting)
    }

    private val day = listOf(
        m("a", "2026-10-19T09:00:00Z", "2026-10-19T09:30:00Z"),
        m("b", "2026-10-19T10:00:00Z", "2026-10-19T11:30:00Z"),
        m("c", "2026-10-19T14:00:00Z", "2026-10-19T15:00:00Z"),
    )
    private val kinds = mapOf("a" to MeetingKind.Standup, "b" to MeetingKind.External, "c" to MeetingKind.Review)

    @Test fun statsAtMidday() {
        val s = DayAnalytics.compute(day, kinds, setOf("a"), t("2026-10-19T12:00:00Z"), Z)
        assertEquals(3, s.meetings)
        assertEquals(2, s.done)
        assertEquals(1, s.left)
        assertEquals(120L, s.minutesSoFar)
        assertEquals(180L, s.minutesTotal)
        assertEquals("b", s.longest?.id)
        assertEquals(1, s.externalMeetings)
        assertEquals(listOf("b"), s.missingNotes.map { it.id })
        assertEquals(1, s.notedCount)
        assertEquals(10, s.busiestHour)
        assertEquals(MeetingKind.External, s.byKind.first().first)
    }

    @Test fun aRunningMeetingCountsOnlyItsElapsedPart() {
        val s = DayAnalytics.compute(day, kinds, emptySet(), t("2026-10-19T14:20:00Z"), Z)
        assertEquals(90L + 30L + 20L, s.minutesSoFar)
        assertEquals(2, s.done)
    }

    @Test fun recapIsPlainAndHonest() {
        val s = DayAnalytics.compute(day, kinds, setOf("a", "b", "c"), t("2026-10-19T18:00:00Z"), Z)
        val r = DayAnalytics.recap(s)
        assertTrue(r, r.contains("3 h"))
        assertTrue(r, r.contains("Every finished meeting has a note."))
        val none = DayAnalytics.compute(emptyList(), emptyMap(), emptySet(), t("2026-10-19T18:00:00Z"), Z)
        assertTrue(DayAnalytics.recap(none).startsWith("No meetings today"))
    }

    @Test fun personalAndFocusBlocksDoNotNeedNotes() {
        val e = listOf(m("gym", "2026-10-19T07:00:00Z", "2026-10-19T08:00:00Z"))
        val s = DayAnalytics.compute(e, mapOf("gym" to MeetingKind.Personal), emptySet(), t("2026-10-19T12:00:00Z"), Z)
        assertTrue(s.missingNotes.isEmpty())
    }

    @Test fun spansAndShare() {
        assertEquals("45 min", DayAnalytics.span(45))
        assertEquals("2 h", DayAnalytics.span(120))
        assertEquals("1 h 15 min", DayAnalytics.span(75))
        assertEquals(50, DayAnalytics.workdayShare(240))
        assertEquals(100, DayAnalytics.workdayShare(900))
    }
}

class MemoTest {
    @Test fun jsonRoundTrip() {
        val items = listOf(
            Memo("1", "e", MemoKind.Text, 5L, "Ask about budget"),
            Memo("2", "e", MemoKind.Photo, 6L, "Agenda\n1. Intro", "2.jpg"),
        )
        val back = MemoJson.decode(MemoJson.encode(items))
        assertEquals(items, back)
        assertNull(back[0].imageFile)
    }

    @Test fun ocrTextJoinsWrappedLinesAndKeepsLists() {
        val raw = "Q4 planning meeting agenda for\nthe whole team\n\n1. Budget review\n2. Hiring plan\n- Vendor shortlist\nNext steps: send the deck by Fri-\nday afternoon."
        val out = OcrText.tidy(raw)
        assertTrue(out, out.contains("agenda for the whole team"))
        assertTrue(out, out.contains("\n\n1. Budget review\n2. Hiring plan\n- Vendor shortlist"))
        assertTrue(out, out.contains("Friday afternoon."))
        assertEquals("", OcrText.tidy("  \n \n"))
    }

    @Test fun gistIsTheFirstRealLine() {
        assertEquals("Budget review", OcrText.gist("\n\nBudget review\nmore"))
        assertTrue(OcrText.gist("word ".repeat(40)).endsWith("…"))
    }
}

class SenseTest {
    private val day = listOf(m("a", "2026-10-19T09:00:00Z", "2026-10-19T09:30:00Z"), m("b", "2026-10-19T13:00:00Z", "2026-10-19T14:00:00Z"))
    @Test fun beforeTheFirstMeeting() = assertEquals("First up: a at 9:00 AM, in 1 h.", DayAnalytics.sense(day, t("2026-10-19T08:00:00Z"), Z))
    @Test fun insideAMeeting() = assertEquals("In a until 9:30 AM.", DayAnalytics.sense(day, t("2026-10-19T09:10:00Z"), Z))
    @Test fun betweenMeetings() = assertEquals("Next: b at 1:00 PM, in 3 h 30 min.", DayAnalytics.sense(day, t("2026-10-19T09:30:00Z"), Z))
    @Test fun afterTheLast() = assertEquals("Your last meeting ended at 2:00 PM.", DayAnalytics.sense(day, t("2026-10-19T18:00:00Z"), Z))
    @Test fun emptyDay() = assertEquals("Nothing is scheduled.", DayAnalytics.sense(emptyList(), t("2026-10-19T08:00:00Z"), Z))
}

class OcrChoiceTest {
    private val arabic = "مرحبا بكم في اجتماع المشروع غدا"

    @Test fun arabicShareCountsLetters() {
        assertEquals(1f, app.plait.data.memo.OcrChoice.arabicShare(arabic), 0.01f)
        assertEquals(0f, app.plait.data.memo.OcrChoice.arabicShare("Hello 123"), 0f)
        assertTrue(app.plait.data.memo.OcrChoice.arabicShare("Budget الميزانية review") in 0.2f..0.6f)
        assertEquals(0f, app.plait.data.memo.OcrChoice.arabicShare("123 !!"), 0f)
    }

    @Test fun autoKeepsArabicWhenTheArabicReaderSawArabic() {
        val pick = app.plait.data.memo.OcrChoice.pick(app.plait.data.memo.OcrLang.Auto, "garbage", arabic, 70)
        assertEquals(arabic, pick)
    }

    @Test fun autoKeepsLatinForEnglishPages() {
        val pick = app.plait.data.memo.OcrChoice.pick(app.plait.data.memo.OcrLang.Auto, "Agenda for Monday", "Agenda for Monday noisy", 80)
        assertEquals("Agenda for Monday", pick)
    }

    @Test fun lowConfidenceArabicIsNotTrusted() {
        val pick = app.plait.data.memo.OcrChoice.pick(app.plait.data.memo.OcrLang.Auto, "Latin text", arabic, 10)
        assertEquals("Latin text", pick)
    }

    @Test fun explicitChoicesAreRespected() {
        assertEquals("L", app.plait.data.memo.OcrChoice.pick(app.plait.data.memo.OcrLang.Latin, "L", arabic, 90))
        assertEquals(arabic, app.plait.data.memo.OcrChoice.pick(app.plait.data.memo.OcrLang.Arabic, "L", arabic, 5))
        assertEquals("L", app.plait.data.memo.OcrChoice.pick(app.plait.data.memo.OcrLang.Arabic, "L", null, 0))
    }
}

class TextUtilTest {
    @Test fun accountsKeepTheirDomain() {
        val s = app.plait.ui.TextUtil.shortenAccount("a.very.long.name.indeed@northwind-traders.com", 26)
        assertTrue(s, s.endsWith("@northwind-traders.com") || s.contains("…"))
        assertTrue(s, s.length <= 28)
        assertEquals("me@acme.com", app.plait.ui.TextUtil.shortenAccount("me@acme.com"))
        assertEquals("Phone calendar with a long…", app.plait.ui.TextUtil.shortenAccount("Phone calendar with a long name here", 27))
    }
}
