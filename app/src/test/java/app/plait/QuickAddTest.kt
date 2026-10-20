package app.plait

import app.plait.data.device.QuickAdd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickAddTest {
    private val monday = LocalDate.of(2026, 10, 19) // a Monday

    @Test fun fullSentence() {
        val r = QuickAdd.parse("lunch with sam@acme.com friday 1pm for 1h at Cafe Nero", monday)
        assertEquals("Lunch with Sam", r.title)
        assertEquals(LocalDate.of(2026, 10, 23), r.date)
        assertEquals(LocalTime.of(13, 0), r.time)
        assertEquals(60, r.durationMin)
        assertEquals(listOf("sam@acme.com"), r.guests)
        assertEquals("Cafe Nero", r.place)
    }

    @Test fun tomorrowAndMinutes() {
        val r = QuickAdd.parse("Design review tomorrow 3:30pm for 45 min", monday)
        assertEquals("Design review", r.title)
        assertEquals(monday.plusDays(1), r.date)
        assertEquals(LocalTime.of(15, 30), r.time)
        assertEquals(45, r.durationMin)
    }

    @Test fun timeRangeSetsTheLength() {
        val r = QuickAdd.parse("Planning on 24 oct 3-4:30pm", monday)
        assertEquals("Planning", r.title)
        assertEquals(LocalDate.of(2026, 10, 24), r.date)
        assertEquals(LocalTime.of(15, 0), r.time)
        assertEquals(90, r.durationMin)
    }

    @Test fun twentyFourHourClockAndNoon() {
        assertEquals(LocalTime.of(15, 30), QuickAdd.parse("Sync 15:30", monday).time)
        assertEquals(LocalTime.NOON, QuickAdd.parse("Lunch tomorrow noon", monday).time)
        assertEquals(LocalTime.of(12, 0), QuickAdd.parse("Standup at 12pm", monday).time)
        assertEquals(LocalTime.of(0, 30), QuickAdd.parse("Call 12:30am", monday).time)
    }

    @Test fun weekdaysLookForwardAndNextSkipsToday() {
        assertEquals(LocalDate.of(2026, 10, 21), QuickAdd.parse("Sync wednesday", monday).date)
        assertEquals(monday.plusWeeks(1), QuickAdd.parse("Sync next monday", monday).date)
        assertEquals(monday.plusDays(3), QuickAdd.parse("Sync in 3 days", monday).date)
        assertEquals(monday.plusWeeks(1), QuickAdd.parse("Review next week", monday).date)
    }

    @Test fun monthNamesRollToNextYearWhenPassed() {
        assertEquals(LocalDate.of(2027, 3, 5), QuickAdd.parse("Tax 5 March", monday).date)
        assertEquals(LocalDate.of(2026, 12, 25), QuickAdd.parse("Party dec 25", monday).date)
    }

    @Test fun tonightAndAllDay() {
        val r = QuickAdd.parse("Dinner tonight", monday)
        assertEquals(monday, r.date); assertEquals(LocalTime.of(19, 0), r.time)
        assertEquals(true, QuickAdd.parse("Conference all day friday", monday).allDay)
    }

    @Test fun plainTitleLeavesEverythingElseUnset() {
        val r = QuickAdd.parse("Call the bank", monday)
        assertEquals("Call the bank", r.title)
        assertNull(r.date); assertNull(r.time); assertNull(r.durationMin)
        assertEquals("", r.place)
    }

    @Test fun commandWordsAreDropped() {
        assertEquals("Budget review", QuickAdd.parse("schedule budget review tomorrow", monday).title)
    }
}

class QuickAddAiTest {
    @Test fun readsTheAiAnswer() {
        val r = QuickAdd.parseAi("Title: Dentist\nDate: 2026-11-03\nTime: 15:30\nMinutes: 45\nPlace: Main Street\nGuests: none")!!
        assertEquals("Dentist", r.title)
        assertEquals(LocalDate.of(2026, 11, 3), r.date)
        assertEquals(LocalTime.of(15, 30), r.time)
        assertEquals(45, r.durationMin)
        assertEquals("Main Street", r.place)
        assertEquals(emptyList<String>(), r.guests)
    }

    @Test fun badAnswersAreIgnored() {
        assertNull(QuickAdd.parseAi("I can't help"))
        val r = QuickAdd.parseAi("Title: Call\nDate: someday\nTime: none\nMinutes: 99999\nGuests: not-an-email, a@b.com")!!
        assertNull(r.date); assertNull(r.time); assertNull(r.durationMin)
        assertEquals(listOf("a@b.com"), r.guests)
    }

    @Test fun promptNamesTodayAndTheFormat() {
        val p = QuickAdd.aiPrompt("dentist the first tuesday of november", LocalDate.of(2026, 10, 19))
        assertEquals(true, p.contains("2026-10-19") && p.contains("Title:") && p.contains("first tuesday"))
    }
}
