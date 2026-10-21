package app.plait.widget

import app.plait.data.CalEvent
import app.plait.data.TimeFmt
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The 2x2 widget's text slots, decided by [WidgetModel.smallLines]. */
data class SmallLines(val title: String, val timeLine: String, val topEnd: String?, val bottomText: String?, val showJoin: Boolean, val titleLines: Int)

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
) {
    companion object {
        fun build(events: List<CalEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): WidgetModel {
            val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val midnight = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val short = java.text.SimpleDateFormat("EEE d", locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(java.util.Date(now))
            val date = java.text.SimpleDateFormat("EEE d MMM", locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(java.util.Date(now))
            val open = events.filter { !it.allDay && it.endMs > now }.sortedBy { it.startMs }
            val today = open.filter { it.startMs < midnight }
            val main = today.firstOrNull()
                ?: return open.firstOrNull().let { f ->
                    val nextWhen = f?.let {
                        val d = java.text.SimpleDateFormat("EEE", locale).apply { timeZone = java.util.TimeZone.getTimeZone(zone) }.format(java.util.Date(it.startMs))
                        "$d ${TimeFmt.clock(it.startMs, zone)}"
                    }
                    WidgetModel(
                        date, "A clear day", nextWhen?.let { "Next: $it ${clean(f!!.title)}" } ?: "Nothing coming up", clearDay = true, nextChangeMs = midnight,
                        titleLines = 1, dateShort = short, nextWhen = nextWhen, nextTitle = f?.let { clean(it.title) },
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
                minOf(midnight, if (running) main.endMs else main.startMs),
            )
        }

        /**
         * 2x2 layout: no place line, the Join pill replaces the bottom text. Title lines are budgeted for the ~86dp of a
         * 110dp cell (top 14 + title 19/line + time 17 + bottom 32 pill or 14 text): 1 line with the pill, 2 without.
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
                m.title, if (m.running) "Now · until ${m.endClock}" else m.startClock,
                if (join) more else null, if (join) null else if (more != null) "$more today" else "Last one today",
                join, if (join) 1 else 2,
            )
        }

        /** One line of at most 100 characters; the view ellipsizes the rest. */
        fun clean(title: String): String = title.replace(Regex("\\s+"), " ").trim().ifEmpty { "Untitled" }.let { if (it.length > 100) it.take(99).trimEnd() + "…" else it }
    }
}
