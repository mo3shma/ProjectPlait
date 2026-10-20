package app.plait.data.brief

import app.plait.data.CalEvent
import java.time.Instant
import java.time.ZoneId

/** The three stops of the day the home screen moves through. */
enum class Phase(val label: String, val greeting: String) {
    Morning("Morning", "Good morning"),
    Afternoon("Midday", "Good afternoon"),
    Evening("Evening", "Good evening");

    companion object {
        fun at(nowMs: Long, zone: ZoneId): Phase {
            val hour = Instant.ofEpochMilli(nowMs).atZone(zone).hour
            return when {
                hour < 12 -> Morning
                hour < 17 -> Afternoon
                else -> Evening
            }
        }
    }
}

/** Numbers about how today was spent, from the calendar and the notes taken. */
data class DayStats(
    val meetings: Int,
    val done: Int,
    val left: Int,
    /** Minutes in meetings so far (finished ones, plus the part of a running one). */
    val minutesSoFar: Long,
    /** Minutes in all of today's meetings, finished or not. */
    val minutesTotal: Long,
    val longest: CalEvent?,
    val externalMeetings: Int,
    val busiestHour: Int?,
    /** Minutes per kind of meeting, biggest first. */
    val byKind: List<Pair<MeetingKind, Long>>,
    /** Finished meetings that have no voice, written or photo note. */
    val missingNotes: List<CalEvent>,
    val notedCount: Int,
)

object DayAnalytics {
    /**
     * [today] is today's events; [kinds] gives each one's kind; [noted] holds the ids of meetings that
     * already have a note of any type. All-day events are not meetings and are left out.
     */
    fun compute(today: List<CalEvent>, kinds: Map<String, MeetingKind>, noted: Set<String>, nowMs: Long, zone: ZoneId): DayStats {
        val timed = today.filter { !it.allDay && it.endMs > it.startMs }
        val finished = timed.filter { it.endMs <= nowMs }
        var so = 0L
        val perKind = HashMap<MeetingKind, Long>()
        val perHour = HashMap<Int, Long>()
        for (e in timed) {
            val full = (e.endMs - e.startMs) / 60_000
            val part = (minOf(e.endMs, nowMs) - e.startMs).coerceAtLeast(0) / 60_000
            so += minOf(part, full)
            perKind.merge(kinds[e.id] ?: MeetingKind.General, full, Long::plus)
            // Spread the minutes over the hours the meeting touches.
            var t = e.startMs
            while (t < e.endMs) {
                val hourEnd = Instant.ofEpochMilli(t).atZone(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val slice = (minOf(hourEnd, e.endMs) - t) / 60_000
                perHour.merge(Instant.ofEpochMilli(t).atZone(zone).hour, slice, Long::plus)
                t = hourEnd
            }
        }
        val busiest = perHour.maxByOrNull { it.value }?.takeIf { it.value >= 30 }?.key
        return DayStats(
            meetings = timed.size,
            done = finished.size,
            left = timed.size - finished.size,
            minutesSoFar = so,
            minutesTotal = timed.sumOf { (it.endMs - it.startMs) / 60_000 },
            longest = timed.maxByOrNull { it.endMs - it.startMs },
            externalMeetings = timed.count { kinds[it.id] == MeetingKind.External },
            busiestHour = busiest,
            byKind = perKind.entries.sortedByDescending { it.value }.map { it.key to it.value },
            missingNotes = finished.filter { kinds[it.id] != MeetingKind.Personal && kinds[it.id] != MeetingKind.Focus && it.id !in noted },
            notedCount = finished.count { it.id in noted },
        )
    }

    fun span(min: Long): String = when {
        min <= 0 -> "0 min"
        min < 60 -> "$min min"
        min % 60 == 0L -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    /** A plain-words recap of the day for the evening card. */
    fun recap(s: DayStats): String {
        if (s.meetings == 0) return "No meetings today, so the whole day was yours."
        val parts = ArrayList<String>()
        parts.add("You spent ${span(s.minutesSoFar)} in ${s.done} meeting${if (s.done == 1) "" else "s"}.")
        if (s.longest != null && s.meetings > 1) parts.add("The longest was ${s.longest.title} at ${span((s.longest.endMs - s.longest.startMs) / 60_000)}.")
        if (s.externalMeetings > 0) parts.add("${s.externalMeetings} ${if (s.externalMeetings == 1) "was" else "were"} with people outside your organisation.")
        if (s.left > 0) parts.add("${s.left} still to come.")
        if (s.missingNotes.isNotEmpty()) parts.add("${s.missingNotes.size} ${if (s.missingNotes.size == 1) "has" else "have"} no notes yet.")
        else if (s.done > 0) parts.add("Every finished meeting has a note.")
        return parts.joinToString(" ")
    }

    private val words = listOf("No", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")
    fun word(n: Int) = words.getOrNull(n) ?: n.toString()

    /** One line that places the user in the day: what is running, or how long until the next thing. */
    fun sense(today: List<CalEvent>, nowMs: Long, zone: ZoneId): String {
        val timed = today.filter { !it.allDay && it.endMs > it.startMs }.sortedBy { it.startMs }
        if (timed.isEmpty()) return "Nothing is scheduled."
        val clock = app.plait.data.TimeFmt.clock
        fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(clock)
        timed.firstOrNull { it.startMs <= nowMs && it.endMs > nowMs }?.let { return "In ${it.title} until ${hm(it.endMs)}." }
        val next = timed.firstOrNull { it.startMs > nowMs }
            ?: return "Your last meeting ended at ${hm(timed.maxOf { it.endMs })}."
        val mins = (next.startMs - nowMs + 59_999) / 60_000
        val lead = if (timed.first() === next) "First up" else "Next"
        return "$lead: ${next.title} at ${hm(next.startMs)}, in ${span(mins)}."
    }

    /** Share of an eight-hour working day taken by meetings, 0..100. */
    fun workdayShare(minutes: Long): Int = (minutes * 100 / (8 * 60)).toInt().coerceIn(0, 100)
}
