package app.plait.data.prep

import app.plait.data.TimeFmt
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Slot(val startMs: Long, val endMs: Long)

/** Finds gaps in your own calendars where a meeting of a given length fits. */
object FreeTime {
    /**
     * [busy] is every busy stretch as (start, end) in epoch milliseconds. Slots start on [stepMin] boundaries inside
     * the working window [dayStartMin]..[dayEndMin] (minutes after midnight), keep [bufferMin] clear on both sides,
     * and are spread out: at most [perDay] per day.
     */
    fun find(
        busy: List<Pair<Long, Long>>, fromMs: Long, days: Int, durationMin: Int,
        dayStartMin: Int = 9 * 60, dayEndMin: Int = 17 * 60, zone: ZoneId = ZoneId.systemDefault(),
        stepMin: Int = 30, bufferMin: Int = 0, weekdaysOnly: Boolean = true, perDay: Int = 3, limit: Int = 8,
    ): List<Slot> {
        val out = ArrayList<Slot>()
        val sorted = busy.sortedBy { it.first }
        val buffer = bufferMin * 60_000L
        val len = durationMin * 60_000L
        var day = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
        var scanned = 0
        while (scanned < days && out.size < limit) {
            val dow = day.dayOfWeek
            if (!(weekdaysOnly && (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY))) {
                var found = 0
                var m = dayStartMin
                val midnight = day.atStartOfDay(zone)
                while (m + durationMin <= dayEndMin && found < perDay && out.size < limit) {
                    val start = midnight.plusMinutes(m.toLong()).toInstant().toEpochMilli()
                    val end = start + len
                    if (start >= fromMs && sorted.none { it.first < end + buffer && it.second > start - buffer }) {
                        out.add(Slot(start, end)); found++
                        m += maxOf(stepMin, durationMin) // spread: the next one starts after this one ends
                    } else m += stepMin
                }
            }
            day = day.plusDays(1); scanned++
        }
        return out
    }

    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun label(s: Slot, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(s.startMs).atZone(zone).format(dayFmt) + ", " + TimeFmt.clock(s.startMs, zone) + " – " + TimeFmt.clock(s.endMs, zone)

    /** A message you can send from any app, listing the options. */
    fun shareText(title: String, slots: List<Slot>, zone: ZoneId = ZoneId.systemDefault()): String =
        "Would any of these times work for ${title.ifBlank { "our meeting" }}?\n" + slots.joinToString("\n") { "• " + label(it, zone) } +
            "\nTell me which suits you and I'll send the invite."
}

/** Long stretches of free time worth protecting, and a title for a to-do that needs time booked. */
object FocusTime {
    /**
     * The longest free stretch of at least [minMin] minutes inside the working window on each of the next [days] weekdays,
     * capped at [maxMin] and starting on the quarter hour. Nothing is booked: these are only suggestions.
     */
    fun suggest(
        busy: List<Pair<Long, Long>>, fromMs: Long, days: Int, minMin: Int = 90, maxMin: Int = 180,
        dayStartMin: Int = 9 * 60, dayEndMin: Int = 17 * 60, zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): List<Slot> {
        val out = ArrayList<Slot>()
        var day = java.time.Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
        var scanned = 0
        while (scanned < days) {
            if (day.dayOfWeek != java.time.DayOfWeek.SATURDAY && day.dayOfWeek != java.time.DayOfWeek.SUNDAY) {
                val midnight = day.atStartOfDay(zone)
                val winStart = maxOf(midnight.plusMinutes(dayStartMin.toLong()).toInstant().toEpochMilli(), fromMs)
                val winEnd = midnight.plusMinutes(dayEndMin.toLong()).toInstant().toEpochMilli()
                // walk through the busy stretches in order, noting each gap
                var cursor = winStart
                var best: Pair<Long, Long>? = null
                for ((s, e) in busy.sortedBy { it.first }.filter { it.second > winStart && it.first < winEnd }) {
                    if (s > cursor && (best == null || s - cursor > best.second - best.first)) best = cursor to s
                    cursor = maxOf(cursor, e)
                }
                if (winEnd > cursor && (best == null || winEnd - cursor > best.second - best.first)) best = cursor to winEnd
                best?.let { (a, b) ->
                    val quarter = 15 * 60_000L
                    val start = (a + quarter - 1) / quarter * quarter
                    val len = minOf(b - start, maxMin * 60_000L)
                    if (len >= minMin * 60_000L) out.add(Slot(start, start + len / quarter * quarter))
                }
            }
            day = day.plusDays(1); scanned++
        }
        return out
    }

    /** "To do: Send the SLA draft to Northwind", kept short enough for a calendar title. */
    fun todoTitle(text: String): String = "To do: " + text.trim().trimEnd('.').let { if (it.length > 60) it.take(60).substringBeforeLast(' ') + "…" else it }

    fun lengthLabel(s: Slot): String {
        val m = (s.endMs - s.startMs) / 60_000
        return if (m % 60 == 0L) "${m / 60} h" else if (m > 60) "${m / 60} h ${m % 60} min" else "$m min"
    }
}
