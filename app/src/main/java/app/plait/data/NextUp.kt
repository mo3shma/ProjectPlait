package app.plait.data

import android.content.Context
import app.plait.data.device.DeviceCalendar
import java.time.ZoneId

/**
 * Reads the calendar without the app open (for the widget, reminders and the evening nudge), applying the same account
 * and calendar choices the user made in the hub.
 */
object NextUp {
    fun events(context: Context, fromMs: Long, toMs: Long): List<CalEvent> {
        if (!DeviceCalendar.hasPermission(context)) return emptyList()
        val p = context.getSharedPreferences("plait", Context.MODE_PRIVATE)
        val enabled = if (p.contains("enabled_accounts")) p.getStringSet("enabled_accounts", emptySet()).orEmpty().toSet() else null
        val hidden = p.getStringSet("hidden_cals", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        val raw = runCatching { DeviceCalendar.query(context, fromMs, toMs, ZoneId.systemDefault()) }.getOrDefault(emptyList())
        return AccountFilter.visible(raw, enabled, hidden).filter { !it.allDay }
    }

    /** The meeting happening now, or else the next one starting within two days. */
    fun next(context: Context, nowMs: Long = System.currentTimeMillis()): CalEvent? =
        events(context, nowMs - 3_600_000L, nowMs + 2 * 86_400_000L).filter { it.endMs > nowMs }.minByOrNull { it.startMs }
}
