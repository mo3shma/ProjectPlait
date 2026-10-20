package app.plait

import app.plait.data.CallProvider
import app.plait.data.Source
import app.plait.data.device.DeviceCalendar
import app.plait.data.device.DeviceRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DeviceCalendarTest {
    private val london = ZoneId.of("Europe/London")
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun row(
        begin: String = "2026-10-19T12:30:00Z", end: String = "2026-10-19T14:00:00Z", allDay: Boolean = false,
        title: String? = "Vendor call", location: String? = null, description: String? = null,
        type: String? = "com.google", account: String? = "me@gmail.com", selfStatus: Int = 0, status: Int = -1,
    ) = DeviceRow(
        eventId = 42, beginMs = at(begin), endMs = at(end), allDay = allDay, title = title, location = location,
        description = description, accountType = type, accountName = account, calendarName = "Work",
        selfStatus = selfStatus, status = status,
    )

    @Test fun recognisesAccountTypes() {
        assertEquals(Source.Google, DeviceCalendar.sourceFor("com.google", "me@gmail.com"))
        assertEquals(Source.Outlook, DeviceCalendar.sourceFor("com.microsoft.exchange", "me@corp.com"))
        assertEquals(Source.Outlook, DeviceCalendar.sourceFor("com.google.android.gm.exchange", "me@corp.com"))
        assertEquals(Source.Outlook, DeviceCalendar.sourceFor("com.microsoft.office.outlook.USER_ACCOUNT", "me@corp.com"))
        assertEquals(Source.ICloud, DeviceCalendar.sourceFor("bitfire.at.davdroid", "me@icloud.com"))
        assertEquals(Source.Outlook, DeviceCalendar.sourceFor("bitfire.at.davdroid", "me@outlook.com"))
        assertEquals(Source.Other, DeviceCalendar.sourceFor("bitfire.at.davdroid", "me@fastmail.com"))
        assertEquals(Source.Other, DeviceCalendar.sourceFor("com.android.localphone", "Phone"))
        assertEquals(Source.Other, DeviceCalendar.sourceFor(null, null))
    }

    @Test fun allDayDatesStayOnTheSameCalendarDayInAnyZone() {
        val begin = at("2026-10-19T00:00:00Z")
        val end = at("2026-10-20T00:00:00Z")
        assertEquals(at("2026-10-18T23:00:00Z") to at("2026-10-19T23:00:00Z"), DeviceCalendar.allDayBoundsLocal(begin, end, london))
        assertEquals(
            at("2026-10-19T04:00:00Z") to at("2026-10-20T04:00:00Z"),
            DeviceCalendar.allDayBoundsLocal(begin, end, ZoneId.of("America/New_York")),
        )
        assertEquals(
            at("2026-10-18T13:00:00Z") to at("2026-10-19T13:00:00Z"),
            DeviceCalendar.allDayBoundsLocal(begin, end, ZoneId.of("Australia/Sydney")),
        )
    }

    @Test fun timedEventUsesLocalClockAndFindsTheCallLink() {
        val e = DeviceCalendar.toEvent(row(description = "Join https://acme.zoom.us/j/81234567890"), london)!!
        assertEquals("1:30 PM", e.time)
        assertEquals("3:00 PM", e.endLabel)
        assertEquals("1h 30m", e.duration)
        assertEquals("Zoom", e.place)
        assertEquals(CallProvider.Zoom, e.callLink?.provider)
        assertEquals(Source.Google, e.source)
        assertTrue(e.id.startsWith("d:42:"))
    }

    @Test fun placeFallsBackToTheStreetThenTheCalendarName() {
        assertEquals("Sourdough & Co.", DeviceCalendar.toEvent(row(location = "Sourdough & Co., 14 Mill Lane"), london)!!.place)
        assertEquals("Work", DeviceCalendar.toEvent(row(), london)!!.place)
    }

    @Test fun allDayEvent() {
        val e = DeviceCalendar.toEvent(row(begin = "2026-10-19T00:00:00Z", end = "2026-10-20T00:00:00Z", allDay = true, title = "Offsite"), london)!!
        assertTrue(e.allDay)
        assertEquals("All day", e.time)
        assertEquals("", e.duration)
        assertEquals(at("2026-10-18T23:00:00Z"), e.startMs)
    }

    @Test fun hidesDeclinedAndCancelledEvents() {
        assertNull(DeviceCalendar.toEvent(row(selfStatus = 2), london)) // declined
        assertNull(DeviceCalendar.toEvent(row(status = 2), london)) // cancelled
        assertNotNull(DeviceCalendar.toEvent(row(selfStatus = 1), london)) // accepted
        assertNotNull(DeviceCalendar.toEvent(row(selfStatus = 4), london)) // tentative stays
    }

    @Test fun untitledEventGetsAPlaceholder() {
        assertEquals("(No title)", DeviceCalendar.toEvent(row(title = " "), london)!!.title)
        assertEquals("(No title)", DeviceCalendar.toEvent(row(title = null), london)!!.title)
    }

    @Test fun recurringInstancesGetDistinctIds() {
        val a = DeviceCalendar.toEvent(row(begin = "2026-10-19T09:00:00Z", end = "2026-10-19T10:00:00Z"), london)!!
        val b = DeviceCalendar.toEvent(row(begin = "2026-10-26T09:00:00Z", end = "2026-10-26T10:00:00Z"), london)!!
        assertTrue(a.id != b.id)
    }
}
