package app.plait.data.device

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.plait.data.CalEvent
import app.plait.data.TimeFmt

/**
 * Adding people to a meeting and sharing it. A meeting from the phone's own calendars gets the guest written straight
 * into that calendar, and the account's calendar app sends the invitation when it next syncs. Anything else (or without
 * permission to write) is shared as a ready-to-send message instead.
 */
object Invite {
    fun canAdd(context: Context, ev: CalEvent): Boolean =
        ev.id.startsWith("d:") && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** True when the guest was written into the phone's calendar. */
    fun add(context: Context, ev: CalEvent, email: String, name: String?): Boolean {
        if (!canAdd(context, ev)) return false
        val eventId = ev.id.split(':').getOrNull(1)?.toLongOrNull() ?: return false
        val values = ContentValues().apply {
            put(CalendarContract.Attendees.EVENT_ID, eventId)
            put(CalendarContract.Attendees.ATTENDEE_EMAIL, email)
            if (!name.isNullOrBlank()) put(CalendarContract.Attendees.ATTENDEE_NAME, name)
            put(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP, CalendarContract.Attendees.RELATIONSHIP_ATTENDEE)
            put(CalendarContract.Attendees.ATTENDEE_TYPE, CalendarContract.Attendees.TYPE_REQUIRED)
            put(CalendarContract.Attendees.ATTENDEE_STATUS, CalendarContract.Attendees.ATTENDEE_STATUS_INVITED)
        }
        return runCatching { context.contentResolver.insert(CalendarContract.Attendees.CONTENT_URI, values) != null }.getOrDefault(false)
    }

    /** The meeting as a short message: title, when, where and the call link. */
    fun text(ev: CalEvent): String = buildString {
        append(ev.title).append('\n')
        // TimeFmt gives Arabic its own comma: "الأربعاء 28 أكتوبر، 12:57 ص".
        if (ev.startMs > 0) append(java.time.Instant.ofEpochMilli(ev.startMs).atZone(java.time.ZoneId.systemDefault()).format(TimeFmt.fmt("EEE d MMM, h:mm a")))
            .append(" – ").append(TimeFmt.clock(ev.endMs)).append('\n')
        ev.callLink?.let { append(it.provider.label).append(": ").append(it.url).append('\n') }
            ?: ev.place.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
    }.trim()
}
