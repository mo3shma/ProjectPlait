package app.plait.data.google

import app.plait.data.CalEvent
import app.plait.data.Rsvp
import app.plait.data.device.MeetingDraft
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
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

    class Created(val eventId: String, val meetUrl: String?)

    /** Makes a new event on [calendarId]; with a [requestId] Google also creates a Meet link for it. */
    fun create(calendarId: String, draft: MeetingDraft, zone: ZoneId, requestId: String?): Created {
        val resp = JSONObject(request("POST", "$base/calendars/${enc(calendarId)}/events?sendUpdates=all&conferenceDataVersion=1", GoogleEventBody.insert(draft, zone, requestId), null))
        val id = resp.optString("id")
        return Created(id, if (requestId == null) null else GoogleParser.conferenceUri(resp) ?: awaitMeet("$base/calendars/${enc(calendarId)}/events/${enc(id)}"))
    }

    /** Adds a Meet link to an existing event. Returns the link, or null while Google is still making it. */
    fun addMeet(ev: CalEvent, requestId: String): String? {
        val url = "$base/calendars/${enc(calendar(ev))}/events/${enc(eventId(ev))}"
        val resp = JSONObject(request("POST", "$url?sendUpdates=all&conferenceDataVersion=1", GoogleEventBody.meet(requestId), "PATCH"))
        return GoogleParser.conferenceUri(resp) ?: awaitMeet(url)
    }

    /** Google makes the link asynchronously; look a few times before giving up. */
    private fun awaitMeet(eventUrl: String): String? {
        repeat(4) {
            Thread.sleep(800)
            // The event already exists, so a failed look must not look like a failed save.
            runCatching { GoogleParser.conferenceUri(JSONObject(request("GET", "$eventUrl?conferenceDataVersion=1", null, null))) }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun patch(url: String, body: JSONObject) = send("POST", url, body, override = "PATCH")

    private fun send(method: String, url: String, body: JSONObject?, override: String? = null) {
        request(method, url, body, override)
    }

    private fun request(method: String, url: String, body: JSONObject?, override: String?): String {
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
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** The JSON Google receives; kept apart from the network code so it can be tested. */
object GoogleEventBody {
    /** Asks Google to make a Meet link. The request id must be new for every request. */
    fun conference(requestId: String): JSONObject = JSONObject().put(
        "createRequest",
        JSONObject().put("requestId", requestId).put("conferenceSolutionKey", JSONObject().put("type", "hangoutsMeet")),
    )

    /** Why a Meet link could not be made, in words for the person. */
    fun whyNoMeet(e: Throwable): String = when ((e as? GoogleApiException)?.code) {
        401, 403 -> "Google refused to make a Meet link. The account may not allow it, or Calendar editing was not approved."
        400 -> "Google can't add a Meet link to this calendar."
        null -> "Couldn't reach Google to make a Meet link."
        else -> "Google couldn't make a Meet link (error ${(e as GoogleApiException).code})."
    }

    fun meet(requestId: String): JSONObject = JSONObject().put("conferenceData", conference(requestId))

    fun insert(d: MeetingDraft, zone: ZoneId, requestId: String?): JSONObject {
        val o = JSONObject().put("summary", d.title.trim())
        if (d.place.isNotBlank()) o.put("location", d.place.trim())
        if (d.notes.isNotBlank()) o.put("description", d.notes.trim())
        if (d.allDay) {
            o.put("start", JSONObject().put("date", d.date.toString())).put("end", JSONObject().put("date", d.date.plusDays(1).toString()))
        } else {
            val f = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            val start = d.date.atTime(d.start).atZone(zone)
            o.put("start", JSONObject().put("dateTime", f.format(start)).put("timeZone", zone.id))
            o.put("end", JSONObject().put("dateTime", f.format(start.plusMinutes(d.durationMin.toLong()))).put("timeZone", zone.id))
        }
        if (d.guests.isNotEmpty()) o.put("attendees", JSONArray().also { a -> d.guests.forEach { a.put(JSONObject().put("email", it)) } })
        d.reminderMin?.takeIf { !d.allDay }?.let {
            o.put("reminders", JSONObject().put("useDefault", false).put("overrides", JSONArray().put(JSONObject().put("method", "popup").put("minutes", it))))
        }
        if (requestId != null) o.put("conferenceData", conference(requestId))
        return o
    }
}
