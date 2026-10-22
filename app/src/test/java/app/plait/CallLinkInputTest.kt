package app.plait

import app.plait.data.CallLinkInput
import app.plait.data.CallLinks
import app.plait.data.CallProvider
import app.plait.data.google.GoogleEventBody
import app.plait.data.google.GoogleApiException
import app.plait.data.device.MeetingDraft
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CallLinkInputTest {
    private fun ok(raw: String) = CallLinkInput.parse(raw) as CallLinkInput.Parsed.Ok

    @Test fun recognisesProviders() {
        assertEquals(CallProvider.Zoom, ok("https://us02web.zoom.us/j/123456789?pwd=abc").provider)
        assertEquals(CallProvider.Teams, ok("https://teams.microsoft.com/l/meetup-join/19%3ameeting").provider)
        assertEquals(CallProvider.Webex, ok("https://acme.webex.com/meet/jane").provider)
        assertEquals(CallProvider.Meet, ok("https://meet.google.com/abc-defg-hij").provider)
    }

    @Test fun anyHttpsLinkIsKeptButUnrecognised() {
        val r = ok("  https://example.com/room/7  ")
        assertNull(r.provider)
        assertEquals("https://example.com/room/7", r.url)
    }

    @Test fun rejectsBadLinks() {
        listOf("", "   ", "http://zoom.us/j/1", "zoom.us/j/123", "https://", "https://localhost", "https://a b.com", "ftp://x.com/y").forEach {
            assertTrue(it, CallLinkInput.parse(it) is CallLinkInput.Parsed.Bad)
        }
    }

    @Test fun placementIsReadBackByJoin() {
        val url = "https://us02web.zoom.us/j/123456789"
        val (p1, n1) = CallLinkInput.place(url, "", "Agenda")
        assertEquals(url, p1); assertEquals("Agenda", n1)
        assertEquals(CallProvider.Zoom, CallLinks.find(null, p1, n1)?.provider)
        val (p2, n2) = CallLinkInput.place(url, "Room 4", "Agenda")
        assertEquals("Room 4", p2)
        assertEquals(CallProvider.Zoom, CallLinks.find(null, p2, n2)?.provider)
        val (p3, n3) = CallLinkInput.place(url, "Room 4", "")
        assertEquals("Join: $url", n3); assertEquals("Room 4", p3)
        assertEquals("Room 4" to "see $url", CallLinkInput.place(url, "Room 4", "see $url"))
    }

    @Test fun conferenceRequestBody() {
        val o = GoogleEventBody.meet("req-1").getJSONObject("conferenceData").getJSONObject("createRequest")
        assertEquals("req-1", o.getString("requestId"))
        assertEquals("hangoutsMeet", o.getJSONObject("conferenceSolutionKey").getString("type"))
    }

    @Test fun insertBody() {
        val d = MeetingDraft("Sync", LocalDate.of(2026, 11, 3), LocalTime.of(14, 30), 45, guests = listOf("a@b.co"), place = "HQ", notes = "n")
        val zone = ZoneId.of("Europe/Berlin")
        val o = GoogleEventBody.insert(d, zone, "r")
        assertEquals("Sync", o.getString("summary"))
        assertEquals("2026-11-03T14:30:00+01:00", o.getJSONObject("start").getString("dateTime"))
        assertEquals("2026-11-03T15:15:00+01:00", o.getJSONObject("end").getString("dateTime"))
        assertEquals("a@b.co", o.getJSONArray("attendees").getJSONObject(0).getString("email"))
        assertEquals("r", o.getJSONObject("conferenceData").getJSONObject("createRequest").getString("requestId"))
        assertFalse(GoogleEventBody.insert(d, zone, null).has("conferenceData"))
        val allDay = GoogleEventBody.insert(d.copy(allDay = true), zone, null)
        assertEquals("2026-11-04", allDay.getJSONObject("end").getString("date"))
        assertFalse(allDay.has("reminders"))
    }

    @Test fun meetRefusalExplained() {
        assertTrue(GoogleEventBody.whyNoMeet(GoogleApiException(403, "x")).contains("refused"))
        assertTrue(GoogleEventBody.whyNoMeet(java.io.IOException()).contains("reach Google"))
    }
}
