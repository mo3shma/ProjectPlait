package app.plait.data.device

import app.plait.R
import app.plait.i18n.Txt
import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.ZoneId
import java.util.TimeZone

/**
 * Writes to the phone's calendar provider. Android's own sync adapters then carry the change to
 * whichever account owns the calendar (Google, Outlook/Exchange, iCloud through CalDAV), so a meeting
 * made in Plait lands in the same place as one made in the calendar app. No cloud project needed.
 */
object DeviceWrite {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    sealed interface Result {
        data class Saved(val eventId: Long, val calendar: String) : Result
        data class Failed(val reason: String) : Result
    }

    /** Saves [draft] into calendar [calId]. Guests are added as attendees so the account sends the invite. */
    fun insert(context: Context, calId: Long, draft: MeetingDraft, zone: ZoneId = ZoneId.systemDefault()): Result {
        if (!hasPermission(context)) return Result.Failed(Txt.s(R.string.devcal_need_permission))
        draft.problem()?.let { return Result.Failed(it) }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, draft.title.trim())
            if (draft.place.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, draft.place.trim())
            if (draft.notes.isNotBlank()) put(CalendarContract.Events.DESCRIPTION, draft.notes.trim())
            if (draft.allDay) {
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.DTSTART, draft.allDayStartMs())
                put(CalendarContract.Events.DTEND, draft.allDayEndMs())
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.DTSTART, draft.startMs(zone))
                put(CalendarContract.Events.DTEND, draft.endMs(zone))
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getTimeZone(zone).id)
            }
            if (draft.guests.isNotEmpty()) put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1)
        }
        val uri = try {
            resolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            return Result.Failed(e.message ?: Txt.s(R.string.devcal_refused))
        } ?: return Result.Failed(Txt.s(R.string.devcal_refused))
        val eventId = ContentUris.parseId(uri)

        runCatching {
            draft.guests.forEach { email ->
                resolver.insert(
                    CalendarContract.Attendees.CONTENT_URI,
                    ContentValues().apply {
                        put(CalendarContract.Attendees.EVENT_ID, eventId)
                        put(CalendarContract.Attendees.ATTENDEE_EMAIL, email)
                        put(CalendarContract.Attendees.ATTENDEE_TYPE, CalendarContract.Attendees.TYPE_REQUIRED)
                        put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, CalendarContract.Attendees.RELATIONSHIP_ATTENDEE)
                        put(CalendarContract.Attendees.ATTENDEE_STATUS, CalendarContract.Attendees.ATTENDEE_STATUS_INVITED)
                    },
                )
            }
            draft.reminderMin?.takeIf { !draft.allDay }?.let { minutes ->
                resolver.insert(
                    CalendarContract.Reminders.CONTENT_URI,
                    ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.MINUTES, minutes)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    },
                )
            }
        }
        val name = DeviceCalendar.calendars(context).firstOrNull { it.id == calId }?.let { "${it.name} (${it.account})" } ?: Txt.s(R.string.devcal_your_calendar)
        return Result.Saved(eventId, name)
    }

    /** Writes Plait's notes into an existing event's description. Returns false if the calendar said no. */
    fun writeNotes(context: Context, eventId: Long, notes: String): Boolean {
        if (!hasPermission(context) || notes.isBlank()) return false
        val resolver = context.contentResolver
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return runCatching {
            val current = resolver.query(uri, arrayOf(CalendarContract.Events.DESCRIPTION), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val merged = NotesBlock.merge(current, notes)
            resolver.update(uri, ContentValues().apply { put(CalendarContract.Events.DESCRIPTION, merged) }, null, null) > 0
        }.getOrDefault(false)
    }

    /**
     * A calendar that lives only on this phone, for people with no account on it. Created once, with
     * the sync-adapter flag the provider requires for local calendars.
     */
    fun ensureLocalCalendar(context: Context): Long? {
        if (!hasPermission(context)) return null
        val resolver = context.contentResolver
        DeviceCalendar.calendars(context).firstOrNull { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL && it.account == LOCAL_ACCOUNT }
            ?.let { return it.id }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "plait")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, Txt.s(R.string.devcal_local_name))
            put(CalendarContract.Calendars.CALENDAR_COLOR, Color.parseColor("#2F6FD6"))
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }
        return runCatching { resolver.insert(uri, values)?.let { ContentUris.parseId(it) } }.getOrNull()
    }

    /** The phone's own event id inside a Plait event id like "d:123:1700000000000", or null for other sources. */
    fun eventIdOf(plaitId: String): Long? =
        plaitId.takeIf { it.startsWith("d:") }?.split(':')?.getOrNull(1)?.toLongOrNull()

    const val LOCAL_ACCOUNT = "Plait"
}
