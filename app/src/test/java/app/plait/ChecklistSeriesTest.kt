package app.plait

import app.plait.data.CalEvent
import app.plait.data.Series
import app.plait.data.Source
import app.plait.data.memo.Checklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistSeriesTest {
    @Test fun checklistRoundTripsAndToggles() {
        val t = Checklist.fromPlain("Send deck\nBook room")
        assertEquals("[ ] Send deck\n[ ] Book room", t)
        val done = Checklist.toggle(t, 1)
        assertEquals("1 of 2 done", Checklist.progress(done))
        assertEquals("[ ] Send deck\n[x] Book room\n[ ] Call Sam", Checklist.add(done, "Call Sam"))
    }

    @Test fun editingKeepsTicks() {
        val t = "[x] Send deck\n[ ] Book room"
        assertEquals("[x] Send deck\n[ ] Book a bigger room", Checklist.edit(t, "Send deck\nBook a bigger room"))
    }

    private fun ev(id: String, start: Long, recurring: Boolean = true) = CalEvent(id, "", "", "Weekly sync", Source.Other, "", startMs = start, recurring = recurring)

    @Test fun notesCarryForwardInASeriesOnly() {
        val next = ev("d:42:2000", 2000)
        assertTrue(Series.carriesTo(next, "d:42:1000", 1500) { null })
        assertFalse(Series.carriesTo(next, "d:43:1000", 1500) { null })
        assertFalse(Series.carriesTo(next, "d:42:3000", 3500) { null })
        assertFalse(Series.carriesTo(ev("d:42:2000", 2000, recurring = false), "d:42:1000", 1500) { null })
    }
}

class SeriesTitleTest {
    @Test fun sameNameOnSameCalendarCarriesEvenWithoutARepeatMark() {
        val next = CalEvent("d:9:5000", "", "", "Weekly sync", Source.Other, "", startMs = 5000)
        val earlier = CalEvent("d:7:1000", "", "", "Weekly sync", Source.Other, "", startMs = 1000)
        assertTrue(Series.carriesTo(next, earlier.id, 1500) { if (it == earlier.id) earlier else null })
        assertFalse(Series.carriesTo(next, earlier.id, 6000) { if (it == earlier.id) earlier else null })
        assertTrue(Series.carriesTo(next, "d:7:1000", 1500, "weekly sync") { null })
        assertFalse(Series.carriesTo(next, "d:7:1000", 1500, "Budget") { null })
    }
}
