package app.plait.data.google

import app.plait.data.CalEvent
import app.plait.data.Rsvp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Changes to meetings read straight from Google (ids "g:…"). Google sends the updates and cancellations to guests
 * itself ("sendUpdates=all"). Blocking: call from a background dispatcher.
 */
class GoogleWrite(private val token: String) {
    private val base = "https://www.googleapis.com/calendar/v3"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun calendar(ev: CalEvent) = ev.account.ifBlank { "primary" }
    private fun eventId(ev: CalEvent) = ev.id.removePrefix("g:")
    private fun seriesId(ev: CalEvent) = eventId(ev).substringBefore('_')
    private fun url(ev: CalEvent, id: String, updates: Boolean) =
        "$base/calendars/${enc(calendar(ev))}/events/${enc(id)}" + if (updates) "?sendUpdates=all" else ""

    fun updateDetails(ev: CalEvent, title: String, place: String, description: String) =
        patch(url(ev, eventId(ev), true), JSONObject().put("summary", title).put("location", place).put("description", description))

    /** Moves this occurrence (an instance id moves just that one). */
    fun reschedule(ev: CalEvent, startMs: Long, endMs: Long) {
        val f = DateTimeFormatter.ISO_INSTANT
        patch(
            url(ev, eventId(ev), true),
            JSONObject().put("start", JSONObject().put("dateTime", f.format(Instant.ofEpochMilli(startMs))))
                .put("end", JSONObject().put("dateTime", f.format(Instant.ofEpochMilli(endMs)))),
        )
    }

    fun cancelOccurrence(ev: CalEvent) = send("DELETE", url(ev, eventId(ev), true), null)

    fun delete(ev: CalEvent, wholeSeries: Boolean) = send("DELETE", url(ev, if (wholeSeries) seriesId(ev) else eventId(ev), true), null)

    /** Your answer to someone else's invitation. The organizer is told. */
    fun respond(ev: CalEvent, answer: Rsvp) {
        val people = JSONArray()
        ev.guests.filter { !it.email.isNullOrBlank() }.forEach { g ->
            val status = if (g.self) answer else g.rsvp
            people.put(
                JSONObject().put("email", g.email).put("optional", g.optional)
                    .put("responseStatus", when (status) { Rsvp.Accepted -> "accepted"; Rsvp.Declined -> "declined"; Rsvp.Tentative -> "tentative"; Rsvp.Pending -> "needsAction" }),
            )
        }
        patch(url(ev, eventId(ev), true), JSONObject().put("attendees", people))
    }

    private fun patch(url: String, body: JSONObject) = send("POST", url, body, override = "PATCH")

    private fun send(method: String, url: String, body: JSONObject?, override: String? = null) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (override != null) conn.setRequestProperty("X-HTTP-Method-Override", override)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GoogleApiException(code, "Google Calendar returned $code. ${err.take(200)}")
            }
        } finally {
            conn.disconnect()
        }
    }
}
