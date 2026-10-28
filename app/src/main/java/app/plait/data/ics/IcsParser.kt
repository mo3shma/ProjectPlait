package app.plait.data.ics

import app.plait.data.CalEvent
import app.plait.data.TimeFmt
import app.plait.data.CallLinks
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.Source
import app.plait.data.google.GoogleParser
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class IcsResult(val name: String?, val events: List<CalEvent>)

/**
 * Reads a published calendar (.ics) feed, such as the link Outlook on the web gives for "publish a
 * calendar", or an iCloud public calendar. Recurring meetings are expanded into the window asked for.
 */
object IcsParser {
    private val clock get() = TimeFmt.clock

    private class Prop(val name: String, val params: Map<String, String>, val value: String)

    /** A start or end: either a calendar date (all-day) or an instant with the zone it was written in. */
    private class Moment(val date: LocalDate?, val wall: LocalDateTime?, val zone: ZoneId, val tz: String? = null) {
        val allDay get() = date != null
        fun instantMs(): Long = if (date != null) date.atStartOfDay(zone).toInstant().toEpochMilli()
        else ZonedDateTime.of(wall, zone).toInstant().toEpochMilli()
    }

    private class Raw(
        val uid: String,
        val summary: String?,
        val description: String?,
        val location: String?,
        val start: Moment,
        val end: Moment?,
        val duration: Duration?,
        val rrule: Rrule?,
        val excluded: List<Moment>,
        val recurrenceId: Moment?,
        val cancelled: Boolean,
        val conference: String?,
        val guests: List<Guest>,
    )

    fun parse(text: String, source: Source, deviceZone: ZoneId, fromMs: Long, toMs: Long, fallbackName: String? = null): IcsResult {
        val lines = text.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "").split('\n')
        var calName: String? = null
        val raws = ArrayList<Raw>()

        var current: MutableList<Prop>? = null
        var nested = 0
        for (line in lines) {
            if (line.isBlank()) continue
            val prop = parseProp(line) ?: continue
            when {
                prop.name == "BEGIN" && prop.value == "VEVENT" -> { current = ArrayList(); nested = 0 }
                prop.name == "END" && prop.value == "VEVENT" -> {
                    current?.let { toRaw(it, deviceZone)?.let(raws::add) }
                    current = null
                }
                current != null && prop.name == "BEGIN" -> nested++
                current != null && prop.name == "END" -> nested--
                current != null && nested == 0 -> current.add(prop)
                current == null && prop.name == "X-WR-CALNAME" -> calName = unescape(prop.value)
            }
        }

        // Overrides replace one occurrence of a recurring series.
        val overridden = HashSet<String>()
        for (r in raws) r.recurrenceId?.let { overridden.add(r.uid + "|" + it.instantMs()) }

        val displayZone = deviceZone
        val events = ArrayList<CalEvent>()
        for (r in raws) {
            if (r.cancelled) continue
            val length: Duration = when {
                r.end != null -> Duration.between(
                    if (r.start.allDay) r.start.date!!.atStartOfDay() else r.start.wall,
                    if (r.end.allDay) r.end.date!!.atStartOfDay() else r.end.wall,
                ).let { d -> if (r.start.allDay) d else Duration.ofMillis(r.end.instantMs() - r.start.instantMs()) }
                r.duration != null -> r.duration
                r.start.allDay -> Duration.ofDays(1)
                else -> Duration.ZERO
            }

            val rule = r.rrule
            if (rule == null || r.recurrenceId != null) {
                addOccurrence(events, r, r.start.instantMs(), length, source, calName ?: fallbackName, displayZone, fromMs, toMs)
            } else {
                val zone = r.start.zone
                val startWall = r.start.wall ?: r.start.date!!.atStartOfDay()
                val windowFrom = LocalDateTime.ofInstant(Instant.ofEpochMilli(fromMs - length.toMillis() - 86_400_000L), zone)
                val windowTo = LocalDateTime.ofInstant(Instant.ofEpochMilli(toMs), zone)
                val excluded = r.excluded.map { it.wall ?: it.date!!.atStartOfDay() }.toSet()
                for (wall in RruleExpander.expand(startWall, rule, windowFrom, windowTo, excluded)) {
                    val startMs = if (r.start.allDay) wall.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                    else ZonedDateTime.of(wall, zone).toInstant().toEpochMilli()
                    if ((r.uid + "|" + startMs) in overridden) continue
                    addOccurrence(events, r, startMs, length, source, calName ?: fallbackName, displayZone, fromMs, toMs)
                }
            }
        }
        return IcsResult(calName, events.distinctBy { it.id }.sortedWith(compareBy({ !it.allDay }, { it.startMs })))
    }

    private fun addOccurrence(
        out: MutableList<CalEvent>, r: Raw, startMs: Long, length: Duration, source: Source,
        calName: String?, zone: ZoneId, fromMs: Long, toMs: Long,
    ) {
        val allDay = r.start.allDay
        val endMs = if (allDay) {
            val days = length.toDays().coerceAtLeast(1)
            LocalDate.ofInstant(Instant.ofEpochMilli(startMs), r.start.zone).plusDays(days).atStartOfDay(r.start.zone).toInstant().toEpochMilli()
        } else startMs + length.toMillis()
        if (startMs >= toMs || endMs <= fromMs) return

        val location = r.location?.takeIf { it.isNotBlank() }
        val event = CalEvent(
            id = "i:${r.uid}:$startMs",
            time = if (allDay) "All day" else Instant.ofEpochMilli(startMs).atZone(zone).format(clock),
            duration = if (allDay) "" else GoogleParser.formatDuration((endMs - startMs) / 60_000),
            title = r.summary?.takeIf { it.isNotBlank() } ?: "(No title)",
            source = source,
            place = "",
            location = location,
            description = r.description?.takeIf { it.isNotBlank() },
            conferenceUri = r.conference,
            endLabel = if (allDay) "" else Instant.ofEpochMilli(endMs).atZone(zone).format(clock),
            startMs = startMs, endMs = endMs, allDay = allDay,
            guests = r.guests, recurring = r.rrule != null || r.recurrenceId != null,
            zone = if (allDay) null else r.start.tz,
        )
        val provider = event.callLink?.provider?.label
        out.add(event.copy(place = provider ?: location?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: (calName ?: source.label)))
    }

    private fun toRaw(props: List<Prop>, deviceZone: ZoneId): Raw? {
        fun first(name: String) = props.firstOrNull { it.name == name }
        val startProp = first("DTSTART") ?: return null
        val start = moment(startProp, deviceZone) ?: return null
        val end = first("DTEND")?.let { moment(it, deviceZone) }
        val rule = first("RRULE")?.let { p ->
            Rrule.parse(p.value) { text -> untilToWall(text, start.zone) }
        }
        val conference = listOf("X-MICROSOFT-SKYPETEAMSMEETINGURL", "X-MICROSOFT-ONLINEMEETINGCONFLINK", "X-GOOGLE-CONFERENCE")
            .firstNotNullOfOrNull { n -> first(n)?.value?.let(::unescape)?.takeIf { it.startsWith("http") } }
        return Raw(
            uid = first("UID")?.value ?: (first("SUMMARY")?.value.orEmpty() + startProp.value),
            summary = first("SUMMARY")?.value?.let(::unescape),
            description = first("DESCRIPTION")?.value?.let(::unescape),
            location = first("LOCATION")?.value?.let(::unescape),
            start = start, end = end,
            duration = first("DURATION")?.value?.let { runCatching { Duration.parse(it) }.getOrNull() },
            rrule = rule,
            excluded = props.filter { it.name == "EXDATE" }.flatMap { p ->
                p.value.split(',').mapNotNull { v -> moment(Prop("EXDATE", p.params, v.trim()), deviceZone) }
            },
            recurrenceId = first("RECURRENCE-ID")?.let { moment(it, deviceZone) },
            cancelled = first("STATUS")?.value.equals("CANCELLED", ignoreCase = true),
            conference = conference,
            guests = guestsOf(props),
        )
    }

    private fun mailOf(value: String): String? =
        value.trim().removePrefix("mailto:").removePrefix("MAILTO:").trim().lowercase().takeIf { it.contains('@') }

    /** ORGANIZER and ATTENDEE lines. Rooms and other resources are skipped. */
    private fun guestsOf(props: List<Prop>): List<Guest> {
        val out = ArrayList<Guest>()
        for (p in props.filter { it.name == "ATTENDEE" }) {
            val cutype = p.params["CUTYPE"]?.uppercase()
            if (cutype == "RESOURCE" || cutype == "ROOM") continue
            val email = mailOf(p.value)
            out.add(
                Guest(
                    name = p.params["CN"]?.let(::unescape).orEmpty(),
                    email = email,
                    rsvp = when (p.params["PARTSTAT"]?.uppercase()) {
                        "ACCEPTED" -> Rsvp.Accepted
                        "DECLINED" -> Rsvp.Declined
                        "TENTATIVE" -> Rsvp.Tentative
                        else -> Rsvp.Pending
                    },
                    optional = p.params["ROLE"].equals("OPT-PARTICIPANT", ignoreCase = true),
                )
            )
        }
        props.firstOrNull { it.name == "ORGANIZER" }?.let { org ->
            val email = mailOf(org.value)
            val at = out.indexOfFirst { it.email != null && it.email == email }
            if (at >= 0) out[at] = out[at].copy(organizer = true)
            else if (out.isNotEmpty() || email != null) {
                out.add(0, Guest(org.params["CN"]?.let(::unescape).orEmpty(), email, Rsvp.Accepted, organizer = true))
            }
        }
        return out
    }

    private fun untilToWall(text: String, zone: ZoneId): LocalDateTime? = try {
        when {
            text.length == 8 -> LocalDate.parse(text, DateTimeFormatter.BASIC_ISO_DATE).atTime(23, 59, 59)
            text.endsWith("Z") -> LocalDateTime.parse(text.dropLast(1), DT).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
            else -> LocalDateTime.parse(text, DT)
        }
    } catch (e: Exception) {
        null
    }

    private val DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    private fun moment(p: Prop, deviceZone: ZoneId): Moment? = try {
        val v = p.value.trim()
        val tzid = p.params["TZID"]
        when {
            p.params["VALUE"].equals("DATE", ignoreCase = true) || v.length == 8 ->
                Moment(LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE), null, deviceZone)
            v.endsWith("Z") -> Moment(null, LocalDateTime.parse(v.dropLast(1), DT), ZoneOffset.UTC)
            else -> {
                val z = tzid?.let { zoneOf(it) } // null: floating or an unknown name, treated as the phone's zone
                Moment(null, LocalDateTime.parse(v, DT), z ?: deviceZone, z?.id)
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun zoneOf(tzid: String): ZoneId? {
        val clean = tzid.trim('"')
        return runCatching { ZoneId.of(clean) }.getOrNull() ?: WINDOWS_ZONES[clean]?.let { runCatching { ZoneId.of(it) }.getOrNull() }
    }

    private fun parseProp(line: String): Prop? {
        // The first colon outside quotes separates the name and parameters from the value.
        var inQuotes = false
        var colon = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') inQuotes = !inQuotes
            else if (c == ':' && !inQuotes) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val segments = splitOutsideQuotes(head, ';')
        val params = segments.drop(1).mapNotNull { s ->
            val eq = s.indexOf('=')
            if (eq < 0) null else s.substring(0, eq).uppercase() to s.substring(eq + 1).trim('"')
        }.toMap()
        return Prop(segments[0].uppercase(), params, value)
    }

    private fun splitOutsideQuotes(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (c in s) {
            if (c == '"') inQuotes = !inQuotes
            if (c == sep && !inQuotes) { out.add(sb.toString()); sb.clear() } else sb.append(c)
        }
        out.add(sb.toString())
        return out
    }

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    /** Windows zone names, which Outlook still writes into feeds, mapped to IANA ids. */
    private val WINDOWS_ZONES = mapOf(
        "UTC" to "UTC", "GMT Standard Time" to "Europe/London", "Greenwich Standard Time" to "Atlantic/Reykjavik",
        "W. Europe Standard Time" to "Europe/Berlin", "Central Europe Standard Time" to "Europe/Budapest",
        "Central European Standard Time" to "Europe/Warsaw", "Romance Standard Time" to "Europe/Paris",
        "E. Europe Standard Time" to "Europe/Chisinau", "FLE Standard Time" to "Europe/Kiev", "GTB Standard Time" to "Europe/Bucharest",
        "Russian Standard Time" to "Europe/Moscow", "Turkey Standard Time" to "Europe/Istanbul", "Israel Standard Time" to "Asia/Jerusalem",
        "Egypt Standard Time" to "Africa/Cairo", "South Africa Standard Time" to "Africa/Johannesburg",
        "W. Central Africa Standard Time" to "Africa/Lagos", "E. Africa Standard Time" to "Africa/Nairobi",
        "Arab Standard Time" to "Asia/Riyadh", "Arabian Standard Time" to "Asia/Dubai", "Iran Standard Time" to "Asia/Tehran",
        "Pakistan Standard Time" to "Asia/Karachi", "India Standard Time" to "Asia/Kolkata", "SE Asia Standard Time" to "Asia/Bangkok",
        "China Standard Time" to "Asia/Shanghai", "Singapore Standard Time" to "Asia/Singapore", "Tokyo Standard Time" to "Asia/Tokyo",
        "Korea Standard Time" to "Asia/Seoul", "AUS Eastern Standard Time" to "Australia/Sydney", "New Zealand Standard Time" to "Pacific/Auckland",
        "Eastern Standard Time" to "America/New_York", "US Eastern Standard Time" to "America/Indiana/Indianapolis",
        "Central Standard Time" to "America/Chicago", "Canada Central Standard Time" to "America/Regina",
        "Mountain Standard Time" to "America/Denver", "Pacific Standard Time" to "America/Los_Angeles",
        "Alaskan Standard Time" to "America/Anchorage", "Hawaiian Standard Time" to "Pacific/Honolulu",
        "Atlantic Standard Time" to "America/Halifax", "E. South America Standard Time" to "America/Sao_Paulo",
        "Argentina Standard Time" to "America/Argentina/Buenos_Aires", "Central America Standard Time" to "America/Guatemala",
    )
}
