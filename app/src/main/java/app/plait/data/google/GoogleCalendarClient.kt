package app.plait.data.google

import app.plait.data.CalEvent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GoogleApiException(val code: Int, message: String) : Exception(message)

/** Minimal Calendar v3 reader. Blocking: call from a background dispatcher. */
class GoogleCalendarClient(private val accessToken: String) {
    private val base = "https://www.googleapis.com/calendar/v3"

    fun fetch(fromMs: Long, toMs: Long, zone: ZoneId, maxCalendars: Int = 12): List<CalEvent> {
        val calendars = GoogleParser.parseCalendarList(get("$base/users/me/calendarList?minAccessRole=reader&maxResults=50"))
            .take(maxCalendars)
        val rfc = DateTimeFormatter.ISO_INSTANT
        val min = enc(rfc.format(Instant.ofEpochMilli(fromMs)))
        val max = enc(rfc.format(Instant.ofEpochMilli(toMs)))
        val all = ArrayList<CalEvent>()
        for ((id, _) in calendars) {
            val url = "$base/calendars/${enc(id)}/events?singleEvents=true&orderBy=startTime&maxResults=250" +
                "&timeMin=$min&timeMax=$max&conferenceDataVersion=1"
            all += GoogleParser.parseEvents(get(url), zone, id)
        }
        // The same invitation can sit on several calendars the user owns.
        return all.distinctBy { it.id }.sortedWith(compareBy({ !it.allDay }, { it.startMs }))
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) {
                val body = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GoogleApiException(code, "Google Calendar returned $code. ${body.take(200)}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
