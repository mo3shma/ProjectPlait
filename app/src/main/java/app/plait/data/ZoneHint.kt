package app.plait.data

import java.time.Instant
import java.time.ZoneId

/** The "3:00 PM Dubai · 4:00 PM your time" line for events scheduled in another zone. */
object ZoneHint {
    /** "Asia/Dubai" -> "Dubai", "America/New_York" -> "New York". */
    fun city(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

    /** True when the two zones show different wall clocks at [atMs] (equal offsets, even in different zones, do not differ). */
    fun differs(a: ZoneId, b: ZoneId, atMs: Long): Boolean {
        val t = Instant.ofEpochMilli(atMs)
        return a.rules.getOffset(t) != b.rules.getOffset(t)
    }

    /** Null unless the event is timed, carries a known zone, and that zone shows a different time than [device]. */
    fun line(ev: CalEvent, device: ZoneId = ZoneId.systemDefault()): String? {
        if (ev.allDay || ev.startMs <= 0) return null
        val id = ev.zone?.takeIf { it.isNotBlank() } ?: return null
        val z = runCatching { ZoneId.of(id) }.getOrNull() ?: return null
        if (!differs(z, device, ev.startMs)) return null
        return "${TimeFmt.clock(ev.startMs, z)} ${city(id)} · ${TimeFmt.clock(ev.startMs, device)} your time"
    }
}
