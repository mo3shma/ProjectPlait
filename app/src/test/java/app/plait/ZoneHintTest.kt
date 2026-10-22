package app.plait

import app.plait.data.CalEvent
import app.plait.data.Source
import app.plait.data.ZoneHint
import app.plait.data.google.CalEventJson
import app.plait.data.google.GoogleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ZoneHintTest {
    private val ny = ZoneId.of("America/New_York")
    private val dubai = ZoneId.of("Asia/Dubai")
    private val winter = Instant.parse("2026-01-15T11:00:00Z").toEpochMilli() // 3 PM Dubai, 6 AM New York
    private fun ev(zone: String?, allDay: Boolean = false, ms: Long = winter) =
        CalEvent("e", "", "", "t", Source.Google, "", startMs = ms, endMs = ms + 3_600_000, allDay = allDay, zone = zone)

    @Test fun cityNames() {
        assertEquals("Dubai", ZoneHint.city("Asia/Dubai"))
        assertEquals("New York", ZoneHint.city("America/New_York"))
        assertEquals("Buenos Aires", ZoneHint.city("America/Argentina/Buenos_Aires"))
        assertEquals("UTC", ZoneHint.city("UTC"))
    }

    @Test fun lineShownWhenOffsetsDiffer() {
        assertEquals("3:00 PM Dubai · 6:00 AM your time", ZoneHint.line(ev("Asia/Dubai"), ny))
    }

    @Test fun nothingWhenSameOffsetEvenIfDifferentZone() {
        // Jan: London = UTC+0 = Reykjavik; Jul: London is +1, Reykjavik +0
        val jan = ev("Atlantic/Reykjavik")
        assertNull(ZoneHint.line(jan, ZoneId.of("Europe/London")))
        val jul = ev("Atlantic/Reykjavik", ms = Instant.parse("2026-07-15T11:00:00Z").toEpochMilli())
        assertEquals("11:00 AM Reykjavik · 12:00 PM your time", ZoneHint.line(jul, ZoneId.of("Europe/London")))
        assertNull(ZoneHint.line(ev("Asia/Dubai"), dubai))
    }

    @Test fun nothingForAllDayFloatingOrUnknown() {
        assertNull(ZoneHint.line(ev("Asia/Dubai", allDay = true), ny))
        assertNull(ZoneHint.line(ev(null), ny))
        assertNull(ZoneHint.line(ev(""), ny))
        assertNull(ZoneHint.line(ev("Not/AZone"), ny))
        assertNull(ZoneHint.line(ev("Asia/Dubai", ms = 0), ny))
    }

    @Test fun cacheKeepsZoneAndOldCacheLoads() {
        val back = CalEventJson.decode(CalEventJson.encode(listOf(ev("Asia/Dubai"))))
        assertEquals("Asia/Dubai", back.single().zone)
        val old = """{"version":1,"events":[{"id":"x","time":"9:00 AM","title":"t","source":"Google","startMs":1,"endMs":2}]}"""
        assertNull(CalEventJson.decode(old).single().zone)
    }

    @Test fun googleReadsStartTimeZone() {
        val json = """{"items":[{"id":"a","summary":"S","start":{"dateTime":"2026-01-15T15:00:00+04:00","timeZone":"Asia/Dubai"},"end":{"dateTime":"2026-01-15T16:00:00+04:00"}},
            {"id":"b","summary":"T","start":{"dateTime":"2026-01-15T15:00:00+04:00"},"end":{"dateTime":"2026-01-15T16:00:00+04:00"}},
            {"id":"c","summary":"D","start":{"date":"2026-01-15"},"end":{"date":"2026-01-16"}}]}"""
        val z = GoogleParser.parseEvents(json, ny).map { it.zone }
        assertEquals(listOf("Asia/Dubai", null, null), z)
    }
}
