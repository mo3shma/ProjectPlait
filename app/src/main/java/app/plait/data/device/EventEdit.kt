package app.plait.data.device

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.plait.data.CalEvent

/**
 * Changing a meeting you organise, in the phone's calendar it lives in. The account's calendar app syncs the change and
 * tells the guests (Google and Exchange send updates and cancellations). For a repeating meeting, a change to one
 * occurrence is written as an exception, so the rest of the series is left alone.
 */
object EventEdit {
    fun eventId(ev: CalEvent): Long? = if (ev.id.startsWith("d:")) ev.id.split(':').getOrNull(1)?.toLongOrNull() else null

    private fun canWrite(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** A meeting in the phone's calendars that you organise (or have alone), with permission to change it. */
    fun canManage(context: Context, ev: CalEvent): Boolean =
        eventId(ev) != null && canWrite(context) && isMine(ev)

    fun updateDetails(context: Context, ev: CalEvent, title: String, place: String, description: String): Boolean {
        val id = eventId(ev) ?: return false
        val v = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.EVENT_LOCATION, place)
            put(CalendarContract.Events.DESCRIPTION, description)
        }
        return runCatching { context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null) > 0 }.getOrDefault(false)
    }

    /** Moves this meeting (only this occurrence, for a repeating one). */
    fun reschedule(context: Context, ev: CalEvent, startMs: Long, endMs: Long): Boolean {
        val id = eventId(ev) ?: return false
        return runCatching {
            if (ev.recurring) {
                val v = ContentValues().apply {
                    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, ev.startMs)
                    put(CalendarContract.Events.DTSTART, startMs)
                    put(CalendarContract.Events.DTEND, endMs)
                }
                context.contentResolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, id), v) != null
            } else {
                val v = ContentValues().apply { put(CalendarContract.Events.DTSTART, startMs); put(CalendarContract.Events.DTEND, endMs) }
                context.contentResolver.update(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), v, null, null) > 0
            }
        }.getOrDefault(false)
    }

    /** Cancels just this occurrence of a repeating meeting. */
    fun cancelOccurrence(context: Context, ev: CalEvent): Boolean {
        val id = eventId(ev) ?: return false
        val v = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, ev.startMs)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }
        return runCatching { context.contentResolver.insert(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, id), v) != null }.getOrDefault(false)
    }

    /** Cancels and removes the meeting (the whole series, for a repeating one). Guests get a cancellation from the calendar. */
    fun delete(context: Context, ev: CalEvent): Boolean {
        val id = eventId(ev) ?: return false
        return runCatching { context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0 }.getOrDefault(false)
    }

    /** Your answer to an invitation in the phone's calendar; the account's calendar app tells the organizer. */
    fun respond(context: Context, ev: CalEvent, answer: app.plait.data.Rsvp): Boolean {
        val id = eventId(ev) ?: return false
        if (!canWrite(context)) return false
        val email = ev.guests.firstOrNull { it.self }?.email ?: return false
        val status = when (answer) {
            app.plait.data.Rsvp.Accepted -> CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED
            app.plait.data.Rsvp.Declined -> CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED
            app.plait.data.Rsvp.Tentative -> CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE
            app.plait.data.Rsvp.Pending -> CalendarContract.Attendees.ATTENDEE_STATUS_NONE
        }
        val v = ContentValues().apply { put(CalendarContract.Attendees.ATTENDEE_STATUS, status) }
        return runCatching {
            context.contentResolver.update(
                CalendarContract.Attendees.CONTENT_URI, v,
                "${CalendarContract.Attendees.EVENT_ID}=? AND ${CalendarContract.Attendees.ATTENDEE_EMAIL}=?", arrayOf(id.toString(), email),
            ) > 0
        }.getOrDefault(false)
    }

    /** You organise it (or it has no other organizer). */
    fun isMine(ev: CalEvent): Boolean = ev.guests.isEmpty() || ev.guests.any { it.self && it.organizer } || ev.guests.none { it.organizer }

    /** Opens the meeting in the phone's calendar app, for anything Plait does not edit itself. */
    fun openInCalendar(context: Context, ev: CalEvent): Boolean {
        val id = eventId(ev) ?: return false
        val i = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, ev.startMs).putExtra(CalendarContract.EXTRA_EVENT_END_TIME, ev.endMs)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(i); true }.getOrDefault(false)
    }
}
