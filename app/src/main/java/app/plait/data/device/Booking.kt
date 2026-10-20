package app.plait.data.device

import android.content.Context
import app.plait.data.Calendars
import app.plait.data.prep.Slot
import java.time.Instant
import java.time.ZoneId

/** Puts something on the calendar the user chose for new meetings, only ever as the result of a tap. */
object Booking {
    /** Returns a message to show: where it was saved, or what went wrong. */
    fun book(context: Context, title: String, slot: Slot, zone: ZoneId = ZoneId.systemDefault()): String {
        if (!DeviceWrite.hasPermission(context)) return "Allow saving to your calendar first (hub, top right)."
        val start = Instant.ofEpochMilli(slot.startMs).atZone(zone)
        val draft = MeetingDraft(title, start.toLocalDate(), start.toLocalTime(), ((slot.endMs - slot.startMs) / 60_000).toInt(), reminderMin = 10)
        val calId = Calendars.destination()?.id ?: DeviceWrite.ensureLocalCalendar(context) ?: return "No calendar to save to. Open the hub to add an account."
        return when (val r = DeviceWrite.insert(context, calId, draft, zone)) {
            is DeviceWrite.Result.Saved -> { Calendars.refreshDevice(context); "Saved to ${r.calendar}" }
            is DeviceWrite.Result.Failed -> r.reason
        }
    }
}
