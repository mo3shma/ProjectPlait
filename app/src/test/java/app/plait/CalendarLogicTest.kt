package app.plait

import app.plait.data.Agenda
import app.plait.data.CalEvent
import app.plait.data.CallProvider
import app.plait.data.Source
import app.plait.data.google.CalEventJson
import app.plait.data.google.GoogleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val LONDON = ZoneId.of("Europe/London")

private val CALENDAR_LIST = """
{"kind":"calendar#calendarList","items":[
 {"id":"me@example.com","summary":"Work","selected":true,"accessRole":"owner"},
 {"id":"holidays@group.v.calendar.google.com","summary":"Holidays","selected":false},
 {"id":"team@example.com","summary":"Team","summaryOverride":"Design team","selected":true},
 {"id":"gone@example.com","summary":"Old","selected":true,"deleted":true}
]}
""".trimIndent()

private val EVENTS = """
{"kind":"calendar#events","items":[
 {"id":"abc1","status":"confirmed","summary":"Design review: Q4 onboarding",
  "start":{"dateTime":"2026-10-19T10:30:00+01:00"},"end":{"dateTime":"2026-10-19T11:15:00+01:00"},
  "hangoutLink":"https://meet.google.com/xkq-mfpa-dsh","description":"Weekly review"},
 {"id":"abc2","summary":"Company offsite","start":{"date":"2026-10-19"},"end":{"date":"2026-10-20"}},
 {"id":"abc3","status":"cancelled","summary":"Cancelled sync",
  "start":{"dateTime":"2026-10-19T12:00:00+01:00"},"end":{"dateTime":"2026-10-19T12:30:00+01:00"}},
 {"id":"abc4","summary":"Vendor call","location":"Northwind HQ, 4 Dock Road",
  "start":{"dateTime":"2026-10-19T12:30:00Z"},"end":{"dateTime":"2026-10-19T14:00:00Z"},
  "conferenceData":{"entryPoints":[
    {"entryPointType":"phone","uri":"tel:+441234567890"},
    {"entryPointType":"video","uri":"https://acme.zoom.us/j/81234567890?pwd=x"}]}},
 {"id":"abc5","start":{"dateTime":"2026-10-19T17:30:00+01:00"},"end":{"dateTime":"2026-10-19T18:30:00+01:00"}}
]}
""".trimIndent()

class GoogleParserTest {
    @Test fun calendarListKeepsOnlySelectedAndUsesOverrideName() {
        val cals = GoogleParser.parseCalendarList(CALENDAR_LIST)
        assertEquals(listOf("me@example.com" to "Work", "team@example.com" to "Design team"), cals)
    }

    @Test fun parsesTimedEventWithMeetLink() {
        val e = GoogleParser.parseEvents(EVENTS, LONDON).first { it.id == "g:abc1" }
        assertEquals("10:30 AM", e.time)
        assertEquals("11:15 AM", e.endLabel)
        assertEquals("45m", e.duration)
        assertEquals(Source.Google, e.source)
        assertEquals("Google Meet", e.place)
        assertEquals(CallProvider.Meet, e.callLink?.provider)
        assertFalse(e.allDay)
    }

    @Test fun skipsCancelledEvents() {
        assertTrue(GoogleParser.parseEvents(EVENTS, LONDON).none { it.id == "g:abc3" })
    }

    @Test fun allDayEventSpansLocalMidnightToMidnight() {
        val e = GoogleParser.parseEvents(EVENTS, LONDON).first { it.id == "g:abc2" }
        assertTrue(e.allDay)
        assertEquals("All day", e.time)
        assertEquals(24L * 3600 * 1000, e.endMs - e.startMs)
        assertEquals(Instant.parse("2026-10-18T23:00:00Z").toEpochMilli(), e.startMs) // BST midnight
    }

    @Test fun convertsUtcTimesToTheDeviceZoneAndPicksVideoEntryPoint() {
        val e = GoogleParser.parseEvents(EVENTS, LONDON).first { it.id == "g:abc4" }
        assertEquals("1:30 PM", e.time)
        assertEquals("1h 30m", e.duration)
        assertEquals(CallProvider.Zoom, e.callLink?.provider)
        assertEquals("https://acme.zoom.us/j/81234567890?pwd=x", e.conferenceUri)
        assertEquals("Zoom", e.place)
    }

    @Test fun untitledEventGetsAPlaceholderAndNoCallLink() {
        val e = GoogleParser.parseEvents(EVENTS, LONDON).first { it.id == "g:abc5" }
        assertEquals("(No title)", e.title)
        assertNull(e.callLink)
        assertEquals("Google Calendar", e.place)
    }

    @Test fun placeUsesFirstPartOfAPhysicalAddress() {
        val json = """{"items":[{"id":"x","summary":"Lunch","location":"Sourdough & Co., 14 Mill Lane",
            "start":{"dateTime":"2026-10-19T12:00:00+01:00"},"end":{"dateTime":"2026-10-19T13:00:00+01:00"}}]}"""
        assertEquals("Sourdough & Co.", GoogleParser.parseEvents(json, LONDON).single().place)
    }

    @Test fun durationFormatting() {
        assertEquals("", GoogleParser.formatDuration(0))
        assertEquals("30m", GoogleParser.formatDuration(30))
        assertEquals("2h", GoogleParser.formatDuration(120))
        assertEquals("1h 5m", GoogleParser.formatDuration(65))
    }

    @Test fun cacheRoundTripKeepsEveryField() {
        val events = GoogleParser.parseEvents(EVENTS, LONDON)
        assertEquals(events, CalEventJson.decode(CalEventJson.encode(events)))
    }
}

class AgendaTest {
    private val events = GoogleParser.parseEvents(EVENTS, LONDON)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test fun todayListsAllDayFirstThenByStart() {
        val today = Agenda.today(events, at("2026-10-19T08:00:00Z"), LONDON)
        assertEquals(listOf("g:abc2", "g:abc1", "g:abc4", "g:abc5"), today.map { it.id })
    }

    @Test fun todayExcludesOtherDays() {
        assertTrue(Agenda.today(events, at("2026-10-21T08:00:00Z"), LONDON).isEmpty())
    }

    @Test fun upNextIsFirstTimedEventNotYetFinished() {
        val now = at("2026-10-19T10:00:00Z") // 11:00 in London: the 10:30 review is under way
        val today = Agenda.today(events, now, LONDON)
        assertEquals("g:abc1", Agenda.upNext(today, now)?.id)
    }

    @Test fun upNextMovesOnOnceAnEventHasEnded() {
        val now = at("2026-10-19T10:20:00Z") // 11:20 in London
        val today = Agenda.today(events, now, LONDON)
        assertEquals("g:abc4", Agenda.upNext(today, now)?.id)
    }

    @Test fun upNextIsNullWhenTheDayIsOver() {
        val now = at("2026-10-19T18:00:00Z")
        assertNull(Agenda.upNext(Agenda.today(events, now, LONDON), now))
    }

    @Test fun laterDropsFinishedEventsButKeepsAllDay() {
        val now = at("2026-10-19T10:20:00Z")
        val today = Agenda.today(events, now, LONDON)
        val next = Agenda.upNext(today, now)
        assertEquals(listOf("g:abc2", "g:abc5"), Agenda.later(today, next, now).map { it.id })
    }

    @Test fun countdownLabels() {
        val start = at("2026-10-19T10:00:00Z")
        fun ev(offsetMs: Long) = CalEvent("t", "", "", "t", Source.Google, "", startMs = start + offsetMs, endMs = start + offsetMs + 3_600_000)
        assertEquals("4 min", Agenda.countdown(ev(4 * 60_000L), start))
        assertEquals("1 min", Agenda.countdown(ev(30_000L), start))
        assertEquals("Now", Agenda.countdown(ev(0), start))
        assertEquals("Now", Agenda.countdown(ev(-10 * 60_000L), start))
        assertEquals("2h 5m", Agenda.countdown(ev(125 * 60_000L), start))
        assertEquals("1h", Agenda.countdown(ev(60 * 60_000L), start))
    }

    @Test fun ringFillsTowardTheStartThenTracksTheEvent() {
        val start = at("2026-10-19T10:00:00Z")
        val e = CalEvent("t", "", "", "t", Source.Google, "", startMs = start, endMs = start + 3_600_000)
        assertEquals(0f, Agenda.ringProgress(e, start - 3 * 3_600_000L), 0.001f)
        assertEquals(0.75f, Agenda.ringProgress(e, start - 15 * 60_000L), 0.001f)
        assertEquals(0.5f, Agenda.ringProgress(e, start + 30 * 60_000L), 0.001f)
        assertNotNull(Agenda.countdown(e, start))
    }

    @Test fun sampleEventsKeepTheirFixedCountdown() {
        val sample = CalEvent("s", "10:30", "45m", "Sample", Source.Google, "", minutesAway = 4)
        assertEquals("4 min", Agenda.countdown(sample, 0))
    }
}

class CalendarGridTest {
    private val london = ZoneId.of("Europe/London")

    @Test fun earlierListsEndedTimedEventsOnly() {
        val events = GoogleParser.parseEvents(EVENTS, london)
        val now = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli() // 13:00 London
        val today = Agenda.today(events, now, london)
        assertEquals(listOf("g:abc1"), Agenda.earlier(today, now).map { it.id })
    }

    @Test fun eventsOnOneDayIncludeAllDayFirst() {
        val events = GoogleParser.parseEvents(EVENTS, london)
        val day = java.time.LocalDate.of(2026, 10, 19)
        assertEquals(listOf("g:abc2", "g:abc1", "g:abc4", "g:abc5"), Agenda.eventsOn(events, day, london).map { it.id })
        assertTrue(Agenda.eventsOn(events, day.plusDays(1), london).none { it.id != "g:abc2" && it.allDay.not() && false })
        assertTrue(Agenda.eventsOn(events, day.plusDays(1), london).isEmpty())
    }

    @Test fun weekStartsOnTheChosenDay() {
        val d = java.time.LocalDate.of(2026, 10, 21) // Wednesday
        assertEquals(java.time.LocalDate.of(2026, 10, 19), Agenda.weekOf(d).first())
        assertEquals(java.time.LocalDate.of(2026, 10, 25), Agenda.weekOf(d).last())
        assertEquals(java.time.LocalDate.of(2026, 10, 18), Agenda.weekOf(d, java.time.DayOfWeek.SUNDAY).first())
    }

    @Test fun monthGridCoversWholeWeeks() {
        val oct = Agenda.monthGrid(java.time.LocalDate.of(2026, 10, 15))
        assertEquals(35, oct.size) // 28 Sep to 1 Nov
        assertEquals(java.time.LocalDate.of(2026, 9, 28), oct.first())
        assertEquals(java.time.LocalDate.of(2026, 11, 1), oct.last())
        val aug = Agenda.monthGrid(java.time.LocalDate.of(2026, 8, 1))
        assertEquals(42, aug.size) // 27 Jul to 6 Sep
        assertTrue(aug.size % 7 == 0)
        assertEquals(java.time.DayOfWeek.MONDAY, aug.first().dayOfWeek)
    }
}

class PackTest {
    private fun ev(id: String, startMin: Int, endMin: Int) = CalEvent(id, "", "", id, Source.Google, "", startMs = startMin * 60_000L, endMs = endMin * 60_000L)

    @Test fun separateEventsEachGetTheFullWidth() {
        val placed = Agenda.pack(listOf(ev("a", 60, 120), ev("b", 120, 180)))
        assertTrue(placed.all { it.columns == 1 && it.column == 0 })
    }

    @Test fun overlappingEventsShareLanes() {
        val placed = Agenda.pack(listOf(ev("a", 60, 150), ev("b", 90, 120), ev("c", 100, 130))).associateBy { it.event.id }
        assertEquals(3, placed.getValue("a").columns)
        assertEquals(listOf(0, 1, 2), listOf("a", "b", "c").map { placed.getValue(it).column })
    }

    @Test fun aLaneIsReusedOnceFree() {
        val placed = Agenda.pack(listOf(ev("a", 60, 120), ev("b", 90, 150), ev("c", 120, 180))).associateBy { it.event.id }
        assertEquals(2, placed.getValue("a").columns)
        assertEquals(0, placed.getValue("c").column) // a ended at 120, so c takes its lane
    }

    @Test fun clustersAreIndependent() {
        val placed = Agenda.pack(listOf(ev("a", 60, 120), ev("b", 70, 100), ev("c", 300, 360))).associateBy { it.event.id }
        assertEquals(2, placed.getValue("a").columns)
        assertEquals(1, placed.getValue("c").columns)
    }

    @Test fun allDayEventsAreLeftOutOfTheGrid() {
        val allDay = ev("x", 0, 1440).copy(allDay = true)
        assertTrue(Agenda.pack(listOf(allDay)).isEmpty())
    }

    @Test fun pastMeansFinishedTimedEvents() {
        val e = ev("a", 60, 120)
        assertTrue(Agenda.isPast(e, 121 * 60_000L))
        assertFalse(Agenda.isPast(e, 100 * 60_000L))
        assertFalse(Agenda.isPast(e.copy(allDay = true), 500 * 60_000L))
        assertFalse(Agenda.isPast(CalEvent("s", "", "", "s", Source.Google, ""), 500 * 60_000L)) // samples without times
    }
}
