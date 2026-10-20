package app.plait.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.plait.data.CalEvent
import app.plait.data.TimeFmt
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.Source
import app.plait.data.google.GoogleParser
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** One row of the system calendar's instance table, before it becomes a [CalEvent]. */
data class DeviceRow(
    val eventId: Long,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val title: String?,
    val location: String?,
    val description: String?,
    val accountType: String?,
    val accountName: String?,
    val calendarName: String?,
    val calendarId: Long = 0L,
    /** CalendarContract.Attendees.ATTENDEE_STATUS_* for the user, or 0 when unknown. */
    val selfStatus: Int,
    /** CalendarContract.Events.STATUS_* or -1 when unset. */
    val status: Int,
)

/**
 * Reads whatever calendars the phone already syncs (Google, Outlook/Exchange, iCloud through a
 * CalDAV app, Samsung and so on) from Android's calendar provider. Needs only the READ_CALENDAR
 * permission: no cloud project, no sign-in inside Plait.
 */
object DeviceCalendar {
    private val clock = TimeFmt.clock

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun sourceFor(accountType: String?, accountName: String?): Source {
        val type = accountType.orEmpty().lowercase()
        val name = accountName.orEmpty().lowercase()
        return when {
            type == "com.google" -> Source.Google
            "exchange" in type || "outlook" in type || "microsoft" in type -> Source.Outlook
            "icloud" in type || "apple" in type || "icloud" in name ||
                name.endsWith("@me.com") || name.endsWith("@mac.com") -> Source.ICloud
            name.endsWith("@outlook.com") || name.endsWith("@hotmail.com") || name.endsWith("@live.com") -> Source.Outlook
            else -> Source.Other
        }
    }

    /**
     * All-day events are stored as UTC midnight to UTC midnight whatever the zone; map them onto
     * the same calendar days in the device's zone so they land on the right local day.
     */
    fun allDayBoundsLocal(beginUtcMs: Long, endUtcMs: Long, zone: ZoneId): Pair<Long, Long> {
        val b = Instant.ofEpochMilli(beginUtcMs).atZone(ZoneOffset.UTC).toLocalDate()
        val e = Instant.ofEpochMilli(endUtcMs).atZone(ZoneOffset.UTC).toLocalDate()
        return b.atStartOfDay(zone).toInstant().toEpochMilli() to e.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Null when the event should be hidden (cancelled, or declined by the user). */
    fun toEvent(row: DeviceRow, zone: ZoneId, guests: List<Guest> = emptyList(), recurring: Boolean = false): CalEvent? {
        if (row.status == CalendarContract.Events.STATUS_CANCELED) return null
        if (row.selfStatus == DECLINED) return null

        val (start, end) = if (row.allDay) allDayBoundsLocal(row.beginMs, row.endMs, zone) else row.beginMs to row.endMs
        val source = sourceFor(row.accountType, row.accountName)
        val location = row.location?.takeIf { it.isNotBlank() }
        val event = CalEvent(
            id = "d:${row.eventId}:${row.beginMs}",
            time = if (row.allDay) "All day" else Instant.ofEpochMilli(start).atZone(zone).format(clock),
            duration = if (row.allDay) "" else GoogleParser.formatDuration((end - start) / 60_000),
            title = row.title?.takeIf { it.isNotBlank() } ?: "(No title)",
            source = source,
            place = "",
            location = location,
            description = row.description?.takeIf { it.isNotBlank() },
            endLabel = if (row.allDay) "" else Instant.ofEpochMilli(end).atZone(zone).format(clock),
            startMs = start,
            endMs = end,
            allDay = row.allDay,
            guests = guests,
            recurring = recurring,
            calId = row.calendarId,
            account = row.accountName.orEmpty(),
        )
        val provider = event.callLink?.provider?.label
        return event.copy(place = provider ?: location?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() } ?: (row.calendarName ?: source.label))
    }

    /** A calendar on the phone, with what the hub needs to show it and to write to it. */
    data class DeviceCal(
        val id: Long, val name: String, val account: String, val accountType: String,
        val color: Int, val writable: Boolean, val primary: Boolean,
    ) {
        val source: Source get() = sourceFor(accountType, account)
    }

    /** Every calendar the phone knows, grouped by account in the hub. */
    fun calendars(context: Context): List<DeviceCal> {
        if (!hasPermission(context)) return emptyList()
        val out = ArrayList<DeviceCal>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.IS_PRIMARY,
                ),
                null, null, "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    out.add(
                        DeviceCal(
                            id = c.getLong(0), name = c.getString(1).orEmpty().ifBlank { "Calendar" },
                            account = c.getString(2).orEmpty(), accountType = c.getString(3).orEmpty(),
                            color = c.getInt(4), writable = c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            primary = !c.isNull(6) && c.getInt(6) == 1,
                        )
                    )
                }
            }
        }
        return out
    }

    /** Events from [fromMs] to [toMs] across every calendar the system shows. */
    fun query(context: Context, fromMs: Long, toMs: Long, zone: ZoneId): List<CalEvent> {
        if (!hasPermission(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            android.content.ContentUris.appendId(it, fromMs - DAY_MS) // widened: all-day rows are in UTC
            android.content.ContentUris.appendId(it, toMs + DAY_MS)
        }.build()
        // Account details come from the calendars table itself, which every device exposes.
        data class Cal(val type: String?, val account: String?, val name: String?)
        val calendars = HashMap<Long, Cal>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) calendars[c.getLong(0)] = Cal(c.getString(1), c.getString(2), c.getString(3))
        }

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
        )
        val rows = ArrayList<DeviceRow>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val cal = calendars[c.getLong(7)]
                rows.add(
                    DeviceRow(
                        eventId = c.getLong(0), beginMs = c.getLong(1), endMs = c.getLong(2), allDay = c.getInt(3) == 1,
                        title = c.getString(4), location = c.getString(5), description = c.getString(6),
                        accountType = cal?.type, accountName = cal?.account, calendarName = cal?.name, calendarId = c.getLong(7),
                        selfStatus = if (c.isNull(8)) 0 else c.getInt(8), status = if (c.isNull(9)) -1 else c.getInt(9),
                    )
                )
            }
        }
        val ids = rows.map { it.eventId }.distinct()
        val meta = eventMeta(context, ids)
        // Some account types do not set the "has attendees" flag, so every event is asked for its guests.
        val guestsByEvent = attendees(context, ids, calendars.values.mapNotNull { it.account }.map { it.lowercase() }.toSet(), meta)

        return rows.mapNotNull { row ->
            val m = meta[row.eventId]
            toEvent(row, zone, guestsByEvent[row.eventId].orEmpty(), recurring = m?.recurring == true)
        }
            .filter { it.startMs < toMs && it.endMs > fromMs }
            .distinctBy { it.id }
            .sortedWith(compareBy({ !it.allDay }, { it.startMs }))
    }

    private class Meta(val organizer: String?, val recurring: Boolean, val hasAttendees: Boolean)

    /** Organizer, repeat rule and whether guests exist, for the events that appear in the window. */
    private fun eventMeta(context: Context, ids: List<Long>): Map<Long, Meta> {
        val out = HashMap<Long, Meta>()
        for (chunk in ids.chunked(400)) {
            runCatching {
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Events._ID, CalendarContract.Events.ORGANIZER,
                        CalendarContract.Events.RRULE, CalendarContract.Events.HAS_ATTENDEE_DATA,
                    ),
                    "${CalendarContract.Events._ID} IN (${chunk.joinToString(",")})", null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        out[c.getLong(0)] = Meta(
                            organizer = c.getString(1)?.lowercase(),
                            recurring = !c.getString(2).isNullOrBlank(),
                            hasAttendees = c.getInt(3) == 1,
                        )
                    }
                }
            }
        }
        return out
    }

    private fun attendees(context: Context, ids: List<Long>, selfEmails: Set<String>, meta: Map<Long, Meta>): Map<Long, List<Guest>> {
        val out = HashMap<Long, MutableList<Guest>>()
        for (chunk in ids.chunked(400)) {
            runCatching {
                context.contentResolver.query(
                    CalendarContract.Attendees.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Attendees.EVENT_ID, CalendarContract.Attendees.ATTENDEE_NAME,
                        CalendarContract.Attendees.ATTENDEE_EMAIL, CalendarContract.Attendees.ATTENDEE_STATUS,
                        CalendarContract.Attendees.ATTENDEE_TYPE, CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                    ),
                    "${CalendarContract.Attendees.EVENT_ID} IN (${chunk.joinToString(",")})", null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getInt(4) == CalendarContract.Attendees.TYPE_RESOURCE) continue // meeting rooms
                        val email = c.getString(2)?.lowercase()?.takeIf { it.isNotBlank() }
                        val isOrganizer = c.getInt(5) == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER ||
                            (email != null && email == meta[c.getLong(0)]?.organizer)
                        val status = c.getInt(3)
                        out.getOrPut(c.getLong(0)) { ArrayList() }.add(
                            Guest(
                                name = c.getString(1).orEmpty().let { if (it == email) "" else it },
                                email = email,
                                // The organizer of an invite has said yes by sending it, even when the calendar leaves their answer blank.
                                rsvp = if (isOrganizer && status == CalendarContract.Attendees.ATTENDEE_STATUS_NONE) Rsvp.Accepted else rsvpOf(status),
                                organizer = isOrganizer,
                                self = email != null && email in selfEmails,
                                optional = c.getInt(4) == CalendarContract.Attendees.TYPE_OPTIONAL,
                            )
                        )
                    }
                }
            }
        }
        // The organizer is not always among the attendee rows (and some invites have no rows at all): add them.
        for (id in ids) {
            val org = meta[id]?.organizer?.takeIf { it.isNotBlank() } ?: continue
            val list = out.getOrPut(id) { ArrayList() }
            if (list.none { it.email == org }) {
                val me = org in selfEmails
                if (!me || list.isNotEmpty()) list.add(Guest("", org, Rsvp.Accepted, organizer = true, self = me))
            }
            if (list.isEmpty()) out.remove(id)
        }
        return out.mapValues { (_, list) -> orderGuests(list) }
    }

    fun rsvpOf(status: Int): Rsvp = when (status) {
        CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> Rsvp.Accepted
        CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> Rsvp.Declined
        CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> Rsvp.Tentative
        else -> Rsvp.Pending
    }

    /** Organizer first, you last, everyone else in the order the calendar gave. */
    fun orderGuests(list: List<Guest>): List<Guest> =
        list.distinctBy { it.email ?: it.name }
            .sortedWith(compareBy({ !it.organizer }, { it.self }))

    private const val DAY_MS = 24L * 3600 * 1000
    private const val DECLINED = 2 // CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
}
