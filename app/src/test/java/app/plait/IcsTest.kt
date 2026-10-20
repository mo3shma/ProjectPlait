package app.plait

import app.plait.data.CallProvider
import app.plait.data.Source
import app.plait.data.ics.IcsParser
import app.plait.data.ics.Rrule
import app.plait.data.ics.RruleExpander
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val LON = ZoneId.of("Europe/London")
private fun utc(iso: String) = Instant.parse(iso).toEpochMilli()

private val OUTLOOK_FEED = """
BEGIN:VCALENDAR
PRODID:-//Microsoft Corporation//Outlook 16.0 MIMEDIR//EN
VERSION:2.0
X-WR-CALNAME:Work
BEGIN:VTIMEZONE
TZID:GMT Standard Time
BEGIN:STANDARD
DTSTART:16010101T020000
TZOFFSETFROM:+0100
TZOFFSETTO:+0000
END:STANDARD
END:VTIMEZONE
BEGIN:VEVENT
UID:weekly-sync
SUMMARY:Weekly sync
DESCRIPTION:Agenda\nhttps://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com&data=1
DTSTART;TZID=GMT Standard Time:20261012T100000
DTEND;TZID=GMT Standard Time:20261012T104500
RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO
EXDATE;TZID=GMT Standard Time:20261026T100000
X-MICROSOFT-SKYPETEAMSMEETINGURL:https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc/0
BEGIN:VALARM
TRIGGER:-PT15M
ACTION:DISPLAY
END:VALARM
END:VEVENT
BEGIN:VEVENT
UID:weekly-sync
RECURRENCE-ID;TZID=GMT Standard Time:20261102T100000
SUMMARY:Weekly sync (moved)
DTSTART;TZID=GMT Standard Time:20261103T140000
DTEND;TZID=GMT Standard Time:20261103T143000
END:VEVENT
BEGIN:VEVENT
UID:offsite
SUMMARY:Company offsite
DTSTART;VALUE=DATE:20261019
DTEND;VALUE=DATE:20261021
END:VEVENT
BEGIN:VEVENT
UID:gone
SUMMARY:Cancelled sync
STATUS:CANCELLED
DTSTART:20261015T090000Z
DTEND:20261015T093000Z
END:VEVENT
BEGIN:VEVENT
UID:monthly
SUMMARY:Monthly review
DTSTART:20261013T090000Z
DTEND:20261013T100000Z
RRULE:FREQ=MONTHLY;BYDAY=2TU;COUNT=3
LOCATION:Room 4\, Floor 2
END:VEVENT
BEGIN:VEVENT
UID:daily3
SUMMARY:Standup
DTSTART:20261014T120000Z
DURATION:PT15M
RRULE:FREQ=DAILY;COUNT=3
END:VEVENT
END:VCALENDAR
""".trimIndent()

class IcsParserTest {
    private val from = utc("2026-10-01T00:00:00Z")
    private val to = utc("2026-12-01T00:00:00Z")
    private val result = IcsParser.parse(OUTLOOK_FEED, Source.Outlook, LON, from, to)

    @Test fun readsTheCalendarName() = assertEquals("Work", result.name)

    @Test fun weeklyMeetingKeepsItsWallClockTimeAcrossTheClockChange() {
        val weekly = result.events.filter { it.title == "Weekly sync" }
        // Oct 12, 19, (26 excluded), (Nov 2 replaced), Nov 9, 16, 23, 30
        assertEquals(6, weekly.size)
        assertTrue(weekly.all { it.time == "10:00 AM" && it.endLabel == "10:45 AM" && it.duration == "45m" })
        assertEquals(utc("2026-10-12T09:00:00Z"), weekly.first().startMs) // BST
        assertEquals(utc("2026-11-09T10:00:00Z"), weekly.first { it.startMs > utc("2026-11-01T00:00:00Z") }.startMs) // GMT
    }

    @Test fun excludedDateIsSkipped() {
        assertTrue(result.events.none { it.title == "Weekly sync" && it.startMs == utc("2026-10-26T10:00:00Z") })
    }

    @Test fun movedOccurrenceReplacesTheOriginal() {
        assertTrue(result.events.none { it.title == "Weekly sync" && it.startMs == utc("2026-11-02T10:00:00Z") })
        val moved = result.events.single { it.title == "Weekly sync (moved)" }
        assertEquals("2:00 PM", moved.time)
        assertEquals("30m", moved.duration)
    }

    @Test fun teamsLinkComesFromOutlooksProperty() {
        val e = result.events.first { it.title == "Weekly sync" }
        assertEquals(CallProvider.Teams, e.callLink?.provider)
        assertEquals("Microsoft Teams", e.place)
        assertEquals(Source.Outlook, e.source)
    }

    @Test fun allDayEventSpansTwoLocalDays() {
        val e = result.events.single { it.title == "Company offsite" }
        assertTrue(e.allDay)
        assertEquals(utc("2026-10-18T23:00:00Z"), e.startMs)
        assertEquals(utc("2026-10-20T23:00:00Z"), e.endMs)
    }

    @Test fun cancelledEventsAreDropped() = assertTrue(result.events.none { it.title == "Cancelled sync" })

    @Test fun monthlySecondTuesdayHonoursTheWindow() {
        val monthly = result.events.filter { it.title == "Monthly review" }
        assertEquals(listOf(utc("2026-10-13T09:00:00Z"), utc("2026-11-10T09:00:00Z")), monthly.map { it.startMs })
        assertEquals("Room 4, Floor 2", monthly.first().location)
        val wider = IcsParser.parse(OUTLOOK_FEED, Source.Outlook, LON, from, utc("2027-02-01T00:00:00Z"))
        assertEquals(3, wider.events.count { it.title == "Monthly review" }) // COUNT=3 stops it
    }

    @Test fun durationPropertyAndCountedDailyRule() {
        val standups = result.events.filter { it.title == "Standup" }
        assertEquals(3, standups.size)
        assertEquals("15m", standups.first().duration)
    }

    @Test fun anEventBeforeTheWindowButStillRunningIsKept() {
        val ics = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:long\nSUMMARY:Conference\nDTSTART:20260929T090000Z\nDTEND:20261002T170000Z\nEND:VEVENT\nEND:VCALENDAR"
        assertEquals(1, IcsParser.parse(ics, Source.Other, LON, from, to).events.size)
    }

    @Test fun foldedLinesAndMissingUidAreHandled() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nSUMMARY:A very long\r\n  title that wraps\r\nDTSTART:20261020T100000Z\r\nDTEND:20261020T110000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val e = IcsParser.parse(ics, Source.Other, LON, from, to).events.single()
        assertEquals("A very long title that wraps", e.title)
    }

    @Test fun garbageInputGivesNoEventsAndNoCrash() {
        assertTrue(IcsParser.parse("<html>not a calendar</html>", Source.Other, LON, from, to).events.isEmpty())
    }
}

class RruleTest {
    private fun rule(s: String) = Rrule.parse(s) { null }!!
    private fun expand(start: String, rule: String, from: String, to: String, excluded: Set<LocalDateTime> = emptySet()) =
        RruleExpander.expand(LocalDateTime.parse(start), rule(rule), LocalDateTime.parse(from), LocalDateTime.parse(to), excluded)
            .map { it.toLocalDate().toString() }

    @Test fun weeklyOnTwoDaysWithInterval() {
        val d = expand("2026-10-05T09:00", "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE", "2026-10-01T00:00", "2026-11-01T00:00")
        assertEquals(listOf("2026-10-05", "2026-10-07", "2026-10-19", "2026-10-21"), d)
    }

    @Test fun lastFridayOfEachMonth() {
        val d = expand("2026-01-30T09:00", "FREQ=MONTHLY;BYDAY=-1FR", "2026-01-01T00:00", "2026-05-01T00:00")
        assertEquals(listOf("2026-01-30", "2026-02-27", "2026-03-27", "2026-04-24"), d)
    }

    @Test fun monthDay31SkipsShortMonths() {
        val d = expand("2026-01-31T09:00", "FREQ=MONTHLY;BYMONTHDAY=31", "2026-01-01T00:00", "2026-06-01T00:00")
        assertEquals(listOf("2026-01-31", "2026-03-31", "2026-05-31"), d)
    }

    @Test fun yearlyBirthday() {
        val d = expand("2024-03-14T00:00", "FREQ=YEARLY", "2025-01-01T00:00", "2028-01-01T00:00")
        assertEquals(listOf("2025-03-14", "2026-03-14", "2027-03-14"), d)
    }

    @Test fun untilEndsTheSeries() {
        val r = Rrule.parse("FREQ=DAILY;UNTIL=20261003T235959Z") { LocalDateTime.parse("2026-10-03T23:59:59") }!!
        val d = RruleExpander.expand(LocalDateTime.parse("2026-10-01T09:00"), r, LocalDateTime.parse("2026-09-01T00:00"), LocalDateTime.parse("2026-12-01T00:00"), emptySet())
        assertEquals(3, d.size)
    }

    @Test fun excludedDatesStillUseUpTheCount() {
        val d = expand("2026-10-01T09:00", "FREQ=DAILY;COUNT=4", "2026-09-01T00:00", "2026-12-01T00:00", setOf(LocalDateTime.parse("2026-10-02T09:00")))
        assertEquals(listOf("2026-10-01", "2026-10-03", "2026-10-04"), d)
    }

    @Test fun windowStartingLateStillCountsFromTheFirstInstance() {
        val d = expand("2026-10-01T09:00", "FREQ=DAILY;COUNT=5", "2026-10-04T00:00", "2026-12-01T00:00")
        assertEquals(listOf("2026-10-04", "2026-10-05"), d)
    }

    @Test fun unsupportedFrequencyIsRejected() {
        assertNull(Rrule.parse("FREQ=HOURLY") { null })
        assertEquals(LocalDate.of(2026, 10, 5).dayOfWeek, java.time.DayOfWeek.MONDAY)
    }
}

class FeedsTest {
    @Test fun normalisesWhatPeoplePaste() {
        assertEquals("https://outlook.office365.com/owa/calendar/abc/reachcalendar.ics", app.plait.data.ics.Feeds.normalize("  webcal://outlook.office365.com/owa/calendar/abc/reachcalendar.ics \n"))
        assertEquals("https://p01-caldav.icloud.com/published/2/x", app.plait.data.ics.Feeds.normalize("http://p01-caldav.icloud.com/published/2/x"))
        assertNull(app.plait.data.ics.Feeds.normalize("not a link"))
        assertNull(app.plait.data.ics.Feeds.normalize("https://localhost/cal.ics"))
        assertNull(app.plait.data.ics.Feeds.normalize("ftp://example.com/cal.ics"))
    }

    @Test fun recognisesTheProvider() {
        assertEquals(Source.Outlook, app.plait.data.ics.Feeds.sourceFor("https://outlook.office365.com/owa/calendar/a/reachcalendar.ics"))
        assertEquals(Source.Outlook, app.plait.data.ics.Feeds.sourceFor("https://outlook.live.com/owa/calendar/a/calendar.ics"))
        assertEquals(Source.ICloud, app.plait.data.ics.Feeds.sourceFor("https://p62-caldav.icloud.com/published/2/abc"))
        assertEquals(Source.Google, app.plait.data.ics.Feeds.sourceFor("https://calendar.google.com/calendar/ical/x/private-y/basic.ics"))
        assertEquals(Source.Other, app.plait.data.ics.Feeds.sourceFor("https://example.org/team.ics"))
    }

    @Test fun roundTripsThroughJson() {
        val feeds = listOf(app.plait.data.ics.Feed("https://a.example.com/x.ics", "Team", Source.Other), app.plait.data.ics.Feed("https://outlook.live.com/c.ics", "Mine", Source.Outlook))
        assertEquals(feeds, app.plait.data.ics.Feeds.decode(app.plait.data.ics.Feeds.encode(feeds)))
        assertTrue(app.plait.data.ics.Feeds.decode("garbage").isEmpty())
    }
}
