package app.plait.widget

import app.plait.data.CalEvent
import app.plait.data.TimeFmt
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** Time-of-day tint of the glass panel. Boundaries 12:00 and 17:00 local. */
enum class Phase {
    Morning, Midday, Evening;

    companion object {
        fun at(ms: Long, zone: ZoneId): Phase = Instant.ofEpochMilli(ms).atZone(zone).hour.let { if (it < 12) Morning else if (it < 17) Midday else Evening }

        /** The next 12:00, 17:00 or midnight strictly after [ms]. */
        fun nextBoundary(ms: Long, zone: ZoneId): Long {
            val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            return listOf(d.atTime(12, 0), d.atTime(17, 0), d.plusDays(1).atStartOfDay()).map { it.atZone(zone).toInstant().toEpochMilli() }.first { it > ms }
        }
    }
}

/** The 2x2 widget's slots, decided by [WidgetModel.smallLines]. [timeLine] is the sub line on a clear day. */
data class SmallLines(
    val title: String, val timeLine: String, val topEnd: String?, val bottomText: String?, val showJoin: Boolean, val titleLines: Int,
    val timeSp: Int = 20,
)

/** The 4x2 widget's slots, decided by [WidgetModel.wideLines]. */
data class WideLines(
    val title: String, val titleSp: Int, val titleLines: Int,
    /** Time block: null on a clear day. While running it is "until 2:30 PM" and [timeUnder] is null. */
    val timeBig: String?, val timeSp: Int, val timeUnder: String?, val nowChip: Boolean,
    /** Place line, or the "Next: ..." line on a clear day. */
    val sub: String?, val showJoin: Boolean,
    val countLabel: String?, val then: String?, val more: String?, val lastText: String?,
)

/** What the widget shows, decided once from the events and the clock. Static clock times only: no countdowns. */
data class WidgetModel(
    val date: String,
    val title: String,
    /** Time range, or the "Next: ..." line on a clear day. */
    val whenLine: String,
    val place: String? = null,
    val joinUrl: String? = null,
    val eventId: String? = null,
    val running: Boolean = false,
    val clearDay: Boolean = false,
    val footer: String? = null,
    /** 4x2 title lines: one when a place line shares the cell, so the worst case fits ~110dp. */
    val titleLines: Int = 2,
    // Raw pieces for the 2x2 layout, see [smallLines].
    val dateShort: String = "",
    val startClock: String = "",
    val endClock: String = "",
    val more: Int = 0,
    /** Clear day only: "Mon 9:00 AM" and the title of the next meeting. */
    val nextWhen: String? = null,
    val nextTitle: String? = null,
    /** The next moment the text above can change (meeting starts or ends, or midnight). */
    val nextChangeMs: Long,
    val phase: Phase = Phase.Midday,
    /** Meetings left today including the shown one. */
    val count: Int = 0,
    val thenLine: String? = null,
    val moreLine: String? = null,
) {
    companion object {
        fun build(events: List<CalEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): WidgetModel {
            val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val short = java.text.SimpleDateFormat("EEE d", locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(java.util.Date(now))
            val date = java.text.SimpleDateFormat("EEE d MMM", locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(java.util.Date(now))
            val phase = Phase.at(now, zone)
            val boundary = Phase.nextBoundary(now, zone)
            val open = events.filter { !it.allDay && it.endMs > now }.sortedBy { it.startMs }
            val today = open.filter { it.startMs < midnight }
            val main = today.firstOrNull()
                ?: return open.firstOrNull().let { f ->
                    val nextWhen = f?.let {
                        val d = java.text.SimpleDateFormat("EEE", locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(java.util.Date(it.startMs))
                        "$d ${TimeFmt.clock(it.startMs, zone)}"
                    }
                    WidgetModel(
                        date, "A clear day", nextWhen?.let { "Next: $it ${clean(f!!.title)}" } ?: "Nothing coming up", clearDay = true, nextChangeMs = minOf(midnight, boundary),
                        titleLines = 1, dateShort = short, nextWhen = nextWhen, nextTitle = f?.let { clean(it.title) }, phase = phase,
                    )
                }
            val rest = today.drop(1)
            val link = main.callLink?.url
            val place = main.place.trim().takeIf { it.isNotEmpty() && !it.startsWith("http", true) } ?: if (link != null) "Online" else null
            val running = main.startMs <= now
            val footer = rest.firstOrNull()?.let {
                "Then ${clean(it.title)} ${TimeFmt.clock(it.startMs, zone)}" + if (rest.size > 1) " · +${rest.size - 1} more" else ""
            } ?: "Last one today"
            return WidgetModel(
                date, clean(main.title), (if (running) "Now · " else "") + TimeFmt.clock(main.startMs, zone) + " – " + TimeFmt.clock(main.endMs, zone),
                place, link, main.id, running, false, footer, if (place != null) 1 else 2, short,
                TimeFmt.clock(main.startMs, zone), TimeFmt.clock(main.endMs, zone), rest.size, null, null,
                minOf(boundary, if (running) main.endMs else main.startMs),
                phase, today.size, rest.firstOrNull()?.let { "Then ${clean(it.title)} ${TimeFmt.clock(it.startMs, zone)}" },
                if (rest.size > 1) "+${rest.size - 1} more" else null,
            )
        }

        /**
         * 2x2 layout, 7dp vertical padding so ~96dp inside a 110dp cell. Budget ([smallDp], 1.25 line height):
         * top 16 + time 25 + title 19 per line + Join 34 = 94 with the pill (1 title line, no place line),
         * or 16 + 25 + 37.5 + capsule 16 = 94.5 without it (2 lines). A running meeting swaps the time for "until ..." in 12sp.
         */
        fun smallLines(m: WidgetModel): SmallLines {
            if (m.clearDay) {
                val full = m.nextWhen?.let { "Next: $it ${m.nextTitle}" }
                val line = when {
                    m.nextWhen == null -> "Nothing coming up"
                    full!!.length <= 22 -> full
                    else -> "Next: ${m.nextWhen}"
                }
                return SmallLines(m.title, line, null, null, false, 2)
            }
            val join = m.joinUrl != null
            val more = if (m.more > 0) "+${m.more} more" else null
            return SmallLines(
                m.title, if (m.running) "until ${m.endClock}" else m.startClock,
                if (m.running) "Now" else if (join) more else null,
                if (join) null else if (more != null) "$more today" else "Last one today",
                join, if (join) 1 else 2, if (m.running) 12 else 20,
            )
        }

        /** Estimated height of the 2x2 content in dp; must stay within the ~96dp inside a 110dp cell. */
        fun smallDp(l: SmallLines, clearDay: Boolean): Double =
            16 + (if (clearDay) 20 * 1.25 * 2 + 12 * 1.25 * 2 else l.timeSp * 1.25 + l.titleLines * 15 * 1.25) +
                (if (l.showJoin) 34 else if (l.bottomText != null) 16 else 0)

        /**
         * 4x2 layout, 12dp vertical padding: 110 - 24 = 86 = top row 16 + main band 46 + strip 24. The main band holds the
         * tallest of the time block (24sp + 12sp = 45), the text column (2 title lines 45, or 1 line + place 39) and Join 36.
         */
        fun wideLines(m: WidgetModel): WideLines {
            if (m.clearDay) return WideLines(m.title, 22, 1, null, 0, null, false, m.whenLine, false, null, null, null, null)
            val rest = m.thenLine != null
            return WideLines(
                m.title, 18, m.titleLines,
                if (m.running) "until ${m.endClock}" else m.startClock, if (m.running) 15 else 24, if (m.running) null else "to ${m.endClock}", m.running,
                m.place, m.joinUrl != null,
                when (m.count) { 0 -> null; 1 -> "1 meeting"; else -> "${m.count} meetings" },
                m.thenLine, m.moreLine, if (rest) null else "Last one today",
            )
        }

        /** Estimated height of the 4x2 main band in dp; must stay within [WIDE_MAIN_DP]. */
        fun wideMainDp(l: WideLines): Double {
            val text = l.titleLines * l.titleSp * 1.25 + (if (l.sub != null) 13 * 1.25 else 0.0)
            val time = if (l.timeBig == null) 0.0 else (if (l.nowChip) 18 else 0) + l.timeSp * 1.25 + (if (l.timeUnder != null) 12 * 1.25 else 0.0)
            return maxOf(text, time, if (l.showJoin) 36.0 else 0.0)
        }

        const val WIDE_MAIN_DP = 46.0
        const val SMALL_INNER_DP = 96.0

        /** One line of at most 100 characters; the view ellipsizes the rest. */
        fun clean(title: String): String = title.replace(Regex("\\s+"), " ").trim().ifEmpty { "Untitled" }.let { if (it.length > 100) it.take(99).trimEnd() + "…" else it }
    }
}
