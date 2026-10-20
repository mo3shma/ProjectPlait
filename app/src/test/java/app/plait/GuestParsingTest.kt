package app.plait

import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.Source
import app.plait.data.device.DeviceCalendar
import app.plait.data.google.CalEventJson
import app.plait.data.google.GoogleParser
import app.plait.data.ics.IcsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

private val LONDON2 = ZoneId.of("Europe/London")

private val GOOGLE_WITH_GUESTS = """
{"items":[{"id":"m1","summary":"Design review","recurringEventId":"series1",
 "start":{"dateTime":"2026-10-19T10:30:00+01:00"},"end":{"dateTime":"2026-10-19T11:15:00+01:00"},
 "organizer":{"email":"Priya@Acme.com","displayName":"Priya Raman"},
 "attendees":[
  {"email":"priya@acme.com","displayName":"Priya Raman","responseStatus":"accepted","organizer":true},
  {"email":"me@acme.com","responseStatus":"accepted","self":true},
  {"email":"sam@acme.com","displayName":"Sam Aoki","responseStatus":"tentative","optional":true},
  {"email":"dev@acme.com","responseStatus":"needsAction"},
  {"email":"room-4@resource.calendar.google.com","displayName":"Room 4","resource":true,"responseStatus":"accepted"},
  {"email":"lena@acme.com","displayName":"Lena","responseStatus":"declined"}
 ]},
 {"id":"m2","summary":"Organizer only","start":{"dateTime":"2026-10-19T12:00:00+01:00"},"end":{"dateTime":"2026-10-19T12:30:00+01:00"},
  "organizer":{"email":"boss@acme.com","displayName":"The Boss"}},
 {"id":"m3","summary":"Organizer not listed","start":{"dateTime":"2026-10-19T13:00:00+01:00"},"end":{"dateTime":"2026-10-19T13:30:00+01:00"},
  "organizer":{"email":"host@other.com","displayName":"Host"},
  "attendees":[{"email":"me@acme.com","self":true,"responseStatus":"accepted"}]}
]}
""".trimIndent()

class GoogleGuestsTest {
    private val events = GoogleParser.parseEvents(GOOGLE_WITH_GUESTS, LONDON2)

    @Test fun readsAttendeesWithTheirReplies() {
        val e = events.first { it.id == "g:m1" }
        assertEquals(5, e.guests.size) // the room is left out
        assertTrue(e.recurring)
        val byEmail = e.guests.associateBy { it.email }
        assertEquals(Rsvp.Accepted, byEmail.getValue("priya@acme.com").rsvp)
        assertTrue(byEmail.getValue("priya@acme.com").organizer)
        assertTrue(byEmail.getValue("me@acme.com").self)
        assertEquals(Rsvp.Tentative, byEmail.getValue("sam@acme.com").rsvp)
        assertTrue(byEmail.getValue("sam@acme.com").optional)
        assertEquals(Rsvp.Pending, byEmail.getValue("dev@acme.com").rsvp)
        assertEquals(Rsvp.Declined, byEmail.getValue("lena@acme.com").rsvp)
    }

    @Test fun anOrganizerAlreadyInTheListIsNotDuplicated() {
        assertEquals(1, events.first { it.id == "g:m1" }.guests.count { it.email == "priya@acme.com" })
    }

    @Test fun anEventWithOnlyAnOrganizerStillShowsThem() {
        val g = events.first { it.id == "g:m2" }.guests
        assertEquals(1, g.size)
        assertTrue(g.single().organizer)
    }

    @Test fun anOrganizerMissingFromTheAttendeesIsAddedFirst() {
        val g = events.first { it.id == "g:m3" }.guests
        assertEquals(listOf("host@other.com", "me@acme.com"), g.map { it.email })
        assertTrue(g.first().organizer)
    }

    @Test fun guestsSurviveTheCache() {
        assertEquals(events, CalEventJson.decode(CalEventJson.encode(events)))
    }
}

class IcsGuestsTest {
    private val feed = """
BEGIN:VCALENDAR
BEGIN:VEVENT
UID:g1
SUMMARY:Northwind renewal
DTSTART:20261020T130000Z
DTEND:20261020T133000Z
ORGANIZER;CN="Park, Jo":mailto:Jo.Park@northwind.com
ATTENDEE;CN="Park, Jo";PARTSTAT=ACCEPTED;ROLE=REQ-PARTICIPANT:mailto:jo.park@northwind.com
ATTENDEE;CN=Sam Aoki;PARTSTAT=TENTATIVE;ROLE=OPT-PARTICIPANT:mailto:sam@acme.com
ATTENDEE;CN=Lee;PARTSTAT=NEEDS-ACTION:mailto:lee@northwind.com
ATTENDEE;CN=Boardroom;CUTYPE=RESOURCE;PARTSTAT=ACCEPTED:mailto:boardroom@acme.com
ATTENDEE;PARTSTAT=DECLINED:mailto:nobody@acme.com
END:VEVENT
BEGIN:VEVENT
UID:g2
SUMMARY:Weekly
DTSTART:20261021T090000Z
DTEND:20261021T093000Z
RRULE:FREQ=WEEKLY
ORGANIZER;CN=Host:mailto:host@acme.com
END:VEVENT
END:VCALENDAR
""".trimIndent()

    private val result = IcsParser.parse(
        feed, Source.Outlook, LONDON2,
        Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(),
    )

    @Test fun readsAttendeesRolesAndReplies() {
        val e = result.events.first { it.title == "Northwind renewal" }
        assertEquals(listOf("jo.park@northwind.com", "sam@acme.com", "lee@northwind.com", "nobody@acme.com"), e.guests.map { it.email })
        assertEquals("Park, Jo", e.guests[0].name)
        assertTrue(e.guests[0].organizer)
        assertEquals(Rsvp.Tentative, e.guests[1].rsvp)
        assertTrue(e.guests[1].optional)
        assertEquals(Rsvp.Pending, e.guests[2].rsvp)
        assertEquals(Rsvp.Declined, e.guests[3].rsvp)
        assertFalse(e.recurring)
    }

    @Test fun repeatingEventsAreMarkedAndOrganizerAloneStillCounts() {
        val weekly = result.events.first { it.title == "Weekly" }
        assertTrue(weekly.recurring)
        assertEquals(listOf("host@acme.com"), weekly.guests.map { it.email })
    }
}

class DeviceGuestsTest {
    @Test fun mapsAndroidAttendeeStatuses() {
        assertEquals(Rsvp.Accepted, DeviceCalendar.rsvpOf(1))
        assertEquals(Rsvp.Declined, DeviceCalendar.rsvpOf(2))
        assertEquals(Rsvp.Pending, DeviceCalendar.rsvpOf(3))
        assertEquals(Rsvp.Tentative, DeviceCalendar.rsvpOf(4))
        assertEquals(Rsvp.Pending, DeviceCalendar.rsvpOf(0))
    }

    @Test fun organizerFirstYouLastNoDuplicates() {
        val ordered = DeviceCalendar.orderGuests(
            listOf(
                Guest("Me", "me@a.com", self = true), Guest("Sam", "sam@a.com"),
                Guest("Priya", "p@a.com", organizer = true), Guest("Sam again", "sam@a.com"),
            )
        )
        assertEquals(listOf("p@a.com", "sam@a.com", "me@a.com"), ordered.map { it.email })
    }
}
