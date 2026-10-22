package app.plait.data.google

import app.plait.data.CalEvent
import app.plait.data.TimeFmt
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.Source
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Turns Google Calendar API responses into [CalEvent]s. Pure, so it is tested with fixtures. */
object GoogleParser {
    private val clock = TimeFmt.clock

    /** Calendars the user has switched on, as (id, name) pairs. */
    fun parseCalendarList(json: String): List<Pair<String, String>> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return List(items.length()) { items.getJSONObject(it) }
            .filter { it.optBoolean("selected", false) && !it.optBoolean("deleted", false) }
            .map { it.getString("id") to it.optString("summaryOverride").ifBlank { it.optString("summary", it.getString("id")) } }
    }

    fun parseEvents(json: String, zone: ZoneId, calendarId: String = ""): List<CalEvent> {
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        val out = ArrayList<CalEvent>(items.length())
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            if (o.optString("status") == "cancelled") continue
            val start = o.optJSONObject("start") ?: continue
            val end = o.optJSONObject("end") ?: continue

            val allDay = start.has("date") && !start.has("dateTime")
            val startMs: Long
            val endMs: Long
            val time: String
            val endLabel: String
            if (allDay) {
                startMs = LocalDate.parse(start.getString("date")).atStartOfDay(zone).toInstant().toEpochMilli()
                endMs = LocalDate.parse(end.getString("date")).atStartOfDay(zone).toInstant().toEpochMilli()
                time = "All day"
                endLabel = ""
            } else {
                val s = OffsetDateTime.parse(start.getString("dateTime"))
                val e = OffsetDateTime.parse(end.getString("dateTime"))
                startMs = s.toInstant().toEpochMilli()
                endMs = e.toInstant().toEpochMilli()
                time = s.atZoneSameInstant(zone).format(clock)
                endLabel = e.atZoneSameInstant(zone).format(clock)
            }

            val conference = conferenceUri(o)
            val location = o.optString("location").ifBlank { null }
            val guests = parseGuests(o)
            out.add(
                CalEvent(
                    id = "g:" + o.getString("id"),
                    time = time,
                    duration = if (allDay) "" else formatDuration((endMs - startMs) / 60_000),
                    title = o.optString("summary").ifBlank { "(No title)" },
                    source = Source.Google,
                    place = placeLabel(location, conference),
                    location = location,
                    description = o.optString("description").ifBlank { null },
                    conferenceUri = conference,
                    endLabel = endLabel,
                    startMs = startMs,
                    endMs = endMs,
                    allDay = allDay,
                    guests = guests,
                    recurring = o.has("recurringEventId"),
                    // The Google calendar it lives in, needed to change it.
                    account = calendarId,
                    // start.timeZone is only sent for some events (recurring, or when set in the editor).
                    zone = if (allDay) null else start.optString("timeZone").ifBlank { null },
                )
            )
        }
        return out
    }

    /** Attendees plus the organizer (who is not always in the attendee list). Rooms are left out. */
    fun parseGuests(o: JSONObject): List<Guest> {
        val out = ArrayList<Guest>()
        val list = o.optJSONArray("attendees")
        if (list != null) {
            for (i in 0 until list.length()) {
                val a = list.getJSONObject(i)
                if (a.optBoolean("resource", false)) continue
                out.add(
                    Guest(
                        name = a.optString("displayName"),
                        email = a.optString("email").ifBlank { null }?.lowercase(),
                        rsvp = when (a.optString("responseStatus")) {
                            "accepted" -> Rsvp.Accepted
                            "declined" -> Rsvp.Declined
                            "tentative" -> Rsvp.Tentative
                            else -> Rsvp.Pending
                        },
                        organizer = a.optBoolean("organizer", false),
                        self = a.optBoolean("self", false),
                        optional = a.optBoolean("optional", false),
                    )
                )
            }
        }
        o.optJSONObject("organizer")?.let { org ->
            val email = org.optString("email").ifBlank { null }?.lowercase()
            val existing = out.indexOfFirst { it.email != null && it.email == email }
            if (existing >= 0) out[existing] = out[existing].copy(organizer = true)
            else if (email != null && (out.isNotEmpty() || !org.optBoolean("self", false))) {
                out.add(0, Guest(org.optString("displayName"), email, Rsvp.Accepted, organizer = true, self = org.optBoolean("self", false)))
            }
        }
        return out
    }

    internal fun conferenceUri(o: JSONObject): String? {
        o.optString("hangoutLink").takeIf { it.isNotBlank() }?.let { return it }
        val points = o.optJSONObject("conferenceData")?.optJSONArray("entryPoints") ?: return null
        for (i in 0 until points.length()) {
            val p = points.getJSONObject(i)
            if (p.optString("entryPointType") == "video") p.optString("uri").takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun placeLabel(location: String?, conference: String?): String = when {
        conference != null -> when {
            "meet.google.com" in conference -> "Google Meet"
            "zoom" in conference -> "Zoom"
            "teams" in conference -> "Microsoft Teams"
            else -> "Video call"
        }
        location != null -> location.substringBefore(',').trim().ifBlank { location }
        else -> "Google Calendar"
    }

    fun formatDuration(minutes: Long): String = when {
        minutes <= 0 -> ""
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0L -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

/** Cache encoding, so the agenda is there offline and before the first sync finishes. */
object CalEventJson {
    fun encode(events: List<CalEvent>): String {
        val arr = JSONArray()
        for (e in events) {
            arr.put(
                JSONObject()
                    .put("id", e.id).put("time", e.time).put("duration", e.duration).put("title", e.title)
                    .put("source", e.source.name).put("place", e.place)
                    .put("location", e.location ?: JSONObject.NULL)
                    .put("description", e.description ?: JSONObject.NULL)
                    .put("conferenceUri", e.conferenceUri ?: JSONObject.NULL)
                    .put("endLabel", e.endLabel).put("startMs", e.startMs).put("endMs", e.endMs).put("allDay", e.allDay)
                    .put("recurring", e.recurring).put("account", e.account).put("zone", e.zone ?: JSONObject.NULL)
                    .put("guests", JSONArray().also { g ->
                        e.guests.forEach {
                            g.put(
                                JSONObject().put("name", it.name).put("email", it.email ?: JSONObject.NULL)
                                    .put("rsvp", it.rsvp.name).put("organizer", it.organizer)
                                    .put("self", it.self).put("optional", it.optional)
                            )
                        }
                    })
            )
        }
        return JSONObject().put("version", 1).put("events", arr).toString()
    }

    fun decode(json: String): List<CalEvent> {
        val arr = JSONObject(json).optJSONArray("events") ?: return emptyList()
        fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k).ifBlank { null }
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            CalEvent(
                id = o.getString("id"), time = TimeFmt.legacy(o.getString("time")), duration = o.optString("duration"),
                title = o.getString("title"), source = Source.valueOf(o.getString("source")), place = o.optString("place"),
                location = o.str("location"), description = o.str("description"), conferenceUri = o.str("conferenceUri"),
                endLabel = TimeFmt.legacy(o.optString("endLabel")), startMs = o.getLong("startMs"), endMs = o.getLong("endMs"),
                allDay = o.optBoolean("allDay"),
                recurring = o.optBoolean("recurring"),
                account = o.optString("account"),
                zone = o.str("zone"),
                guests = o.optJSONArray("guests")?.let { g ->
                    List(g.length()) { k ->
                        val x = g.getJSONObject(k)
                        Guest(
                            name = x.optString("name"), email = x.str("email"),
                            rsvp = runCatching { Rsvp.valueOf(x.optString("rsvp")) }.getOrDefault(Rsvp.Pending),
                            organizer = x.optBoolean("organizer"), self = x.optBoolean("self"), optional = x.optBoolean("optional"),
                        )
                    }
                } ?: emptyList(),
            )
        }
    }
}
