package app.plait.data.device

import app.plait.R
import app.plait.i18n.Txt
import app.plait.i18n.Bidi
import android.content.Context
import app.plait.data.Calendars
import app.plait.data.prep.Slot
import java.time.Instant
import java.time.ZoneId

/** Puts something on the calendar the user chose for new meetings, only ever as the result of a tap. */
object Booking {
    /** Returns a message to show: where it was saved, or what went wrong. */
    fun book(context: Context, title: String, slot: Slot, zone: ZoneId = ZoneId.systemDefault()): String {
        if (!DeviceWrite.hasPermission(context)) return Txt.s(R.string.devcal_allow_first)
        val start = Instant.ofEpochMilli(slot.startMs).atZone(zone)
        val draft = MeetingDraft(title, start.toLocalDate(), start.toLocalTime(), ((slot.endMs - slot.startMs) / 60_000).toInt(), reminderMin = 10)
        val calId = Calendars.destination()?.id ?: DeviceWrite.ensureLocalCalendar(context) ?: return Txt.s(R.string.devcal_no_calendar)
        return when (val r = DeviceWrite.insert(context, calId, draft, zone)) {
            is DeviceWrite.Result.Saved -> { Calendars.refreshDevice(context); Txt.s(R.string.devcal_saved_to, Bidi.isolate(r.calendar)) }
            is DeviceWrite.Result.Failed -> r.reason
        }
    }
}
