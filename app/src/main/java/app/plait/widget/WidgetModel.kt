package app.plait.widget

import app.plait.data.CalEvent
import app.plait.data.CallProvider
import app.plait.data.Source
import app.plait.R
import app.plait.data.TimeFmt
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
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

/** The colour of the meeting's service, as ServicePalette leads with it (call provider first, then source). Drawables: widget_pill_<name>. */
enum class JoinLook(val argb: Int) {
    Teams(0xFF5B5FC7.toInt()), Meet(0xFF1A73E8.toInt()), Zoom(0xFF2D8CFF.toInt()), Webex(0xFF00A0D1.toInt()),
    Google(0xFF4285F4.toInt()), Outlook(0xFF0078D4.toInt()), ICloud(0xFFFF9F0A.toInt()), Other(0xFF7B8CDE.toInt());

    companion object {
        fun of(provider: CallProvider?, source: Source): JoinLook = when (provider) {
            CallProvider.Teams -> Teams
            CallProvider.Meet -> Meet
            CallProvider.Zoom -> Zoom
            CallProvider.Webex -> Webex
            null -> when (source) { Source.Google -> Google; Source.Outlook -> Outlook; Source.ICloud -> ICloud; Source.Other -> Other }
        }
    }
}

/** The 2x2 widget's slots, decided by [WidgetModel.smallLines]. [timeLine] is the sub line on a clear day. */
data class SmallLines(
    val title: String, val timeLine: String, val topEnd: String?, val showJoin: Boolean, val titleLines: Int,
    val timeSp: Int = 16, val titleSp: Int = 16,
)

/** The 4x2 widget's slots, decided by [WidgetModel.wideLines]. */
data class WideLines(
    val title: String, val titleSp: Int, val titleLines: Int,
    /** "1:30 – 2:30 PM", or "until 2:30 PM" while running; null on a clear day. */
    val timeLine: String?, val nowChip: Boolean,
    /** Place line (only when the title is short), or the "Next: ..." line on a clear day. */
    val sub: String?, val showJoin: Boolean,
    val countLabel: String?, val then: String?, val more: String?, val lastText: String?,
)

/** The 4x3 strip: [top] is the time or the "Next: ..." line, [title] the bold line. */
data class QuickLines(val top: String?, val title: String, val showJoin: Boolean)

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
    /** 4x2 title lines: two only for a long title, which then gets no place line, so the worst case fits ~110dp. */
    val titleLines: Int = 1,
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
    val look: JoinLook = JoinLook.Other,
) {
    /** ARGB of the meeting's service: the accent dot. */
    val accent: Int get() = look.argb

    /** ARGB the Join pill is tinted with; the pill itself is the matching widget_pill_* drawable. */
    val joinTint: Int get() = look.argb

    companion object {
        fun build(events: List<CalEvent>, now: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = AppLocale.locale): WidgetModel {
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
                        date, Txt.s(R.string.wdg_clear_day), nextWhen?.let { nextFull(it, clean(f!!.title)) } ?: Txt.s(R.string.wdg_nothing_coming), clearDay = true, nextChangeMs = minOf(midnight, boundary),
                        titleLines = 1, dateShort = short, nextWhen = nextWhen, nextTitle = f?.let { clean(it.title) }, phase = phase,
                    )
                }
            val rest = today.drop(1)
            val link = main.callLink?.url
            val place = main.place.trim().takeIf { it.isNotEmpty() && !it.startsWith("http", true) } ?: if (link != null) Txt.s(R.string.wdg_online) else null
            val running = main.startMs <= now
            val thenLine = rest.firstOrNull()?.let { Txt.s(R.string.wdg_then, Bidi.isolate(clean(it.title)), Bidi.isolate(TimeFmt.clock(it.startMs, zone))) }
            val moreLine = if (rest.size > 1) Txt.s(R.string.wdg_more, rest.size - 1) else null
            val footer = thenLine?.let { it + if (moreLine != null) " · $moreLine" else "" } ?: Txt.s(R.string.wdg_last_today)
            val title = clean(main.title)
            return WidgetModel(
                date, title, (if (running) Txt.s(R.string.common_now) + " · " else "") + TimeFmt.clock(main.startMs, zone) + " – " + TimeFmt.clock(main.endMs, zone),
                place, link, main.id, running, false, footer, if (title.length > ONE_LINE_MAX) 2 else 1, short,
                TimeFmt.clock(main.startMs, zone), TimeFmt.clock(main.endMs, zone), rest.size, null, null,
                minOf(boundary, if (running) main.endMs else main.startMs),
                phase, today.size, thenLine, moreLine,
                JoinLook.of(main.callLink?.provider, main.source),
            )
        }

        /** "1:30 – 2:30 PM" (Arabic "1:30 – 2:30 م"); the first AM/PM mark is dropped when both are the same. */
        fun range(start: String, end: String): String {
            val sep = if ('\u00A0' in end) '\u00A0' else ' ' // Arabic times keep their mark with a no-break space (TimeFmt)
            val mark = end.substringAfterLast(sep, "")
            return if (mark.isNotEmpty() && start.endsWith("$sep$mark")) start.dropLast(mark.length + 1) + " – " + end else "$start – $end"
        }

        /** "Next: Mon 9:00 AM Design review". */
        private fun nextFull(whenText: String, title: String) = Txt.s(R.string.wdg_next_full, Bidi.isolate(whenText), Bidi.isolate(title))

        private fun next(whenText: String) = Txt.s(R.string.wdg_next, Bidi.isolate(whenText))

        private fun until(clock: String) = Txt.s(R.string.wdg_until, Bidi.isolate(clock))

        /** 20sp up to 16 characters, 18sp up to [ONE_LINE_MAX], then two lines at 16sp. */
        fun wideTitleSp(title: String): Int = if (title.length <= 16) 20 else if (title.length <= ONE_LINE_MAX) 18 else 16

        /**
         * 2x2 layout, 6dp vertical padding so 98dp inside a 110dp cell. Budget ([smallDp], 1.25 line height):
         * with Join: top 16 + time 13sp 16.25 + title 2 x 14sp 35 + pill 28 = 95.25; without: 16 + time 16sp 20 + title 3 x 16sp 60 = 96.
         * A running meeting swaps the time for "until ..." in 12sp.
         */
        fun smallLines(m: WidgetModel): SmallLines {
            if (m.clearDay) {
                val full = m.nextWhen?.let { nextFull(it, m.nextTitle.toString()) }
                val line = when {
                    m.nextWhen == null -> Txt.s(R.string.wdg_nothing_coming)
                    full!!.replace(Regex("[\u2066-\u2069]"), "").length <= 22 -> full
                    else -> next(m.nextWhen)
                }
                return SmallLines(m.title, line, null, false, 2, titleSp = 20)
            }
            val join = m.joinUrl != null
            return SmallLines(
                m.title, if (m.running) until(m.endClock) else m.startClock,
                if (m.running) Txt.s(R.string.common_now) else if (m.more > 0) Txt.s(R.string.wdg_more, m.more) else null,
                join, if (join) 2 else 3, if (m.running) 12 else if (join) 13 else 16, if (join) 14 else 16,
            )
        }

        /** Estimated height of the 2x2 content in dp; must stay within [SMALL_INNER_DP]. */
        fun smallDp(l: SmallLines, clearDay: Boolean): Double =
            16 + (if (clearDay) 20 * 1.25 * 2 + 12 * 1.25 * 2 else l.timeSp * 1.25 + l.titleLines * l.titleSp * 1.25) + (if (l.showJoin) SMALL_JOIN_DP else 0.0)

        /**
         * 4x2 layout, 8dp vertical padding: 110 - 16 = 94 = top row 16 + main band 58 + strip 20. The text column is the time
         * line 13sp (16.25) + title + place 12sp (15): one title line (18-20sp, 22.5-25) plus place = 53.75-56.25, or a long
         * title on 2 lines at 16sp (40) with no place = 56.25. Join is 36.
         */
        fun wideLines(m: WidgetModel): WideLines {
            if (m.clearDay) return WideLines(m.title, 22, 1, null, false, m.whenLine, false, null, null, null, null)
            val rest = m.thenLine != null
            return WideLines(
                m.title, wideTitleSp(m.title), m.titleLines,
                if (m.running) until(m.endClock) else range(m.startClock, m.endClock), m.running,
                if (m.title.length <= ONE_LINE_MAX) m.place else null, m.joinUrl != null,
                if (m.count == 0) null else Txt.plural(R.plurals.common_meetings, m.count),
                m.thenLine, m.moreLine, if (rest) null else Txt.s(R.string.wdg_last_today),
            )
        }

        /** Estimated height of the 4x2 main band in dp; must stay within [WIDE_MAIN_DP]. */
        fun wideMainDp(l: WideLines): Double {
            val text = (if (l.timeLine != null) 16.25 else 0.0) + l.titleLines * l.titleSp * 1.25 + (if (l.sub != null) 12 * 1.25 else 0.0)
            return maxOf(text, if (l.showJoin) 36.0 else 0.0)
        }

        /** What the 4x3 strip says: the next meeting, a running one, or why there is none. Join only with a call link. */
        fun quickLines(m: WidgetModel): QuickLines {
            if (m.clearDay) return QuickLines(if (m.nextWhen != null) next(m.nextWhen) else Txt.s(R.string.wdg_nothing_coming), m.title, false)
            val time = if (m.running) Txt.s(R.string.common_now) + " · " + until(m.endClock) else m.startClock
            // Without a call link the pill's room goes to what comes after.
            return QuickLines(if (m.joinUrl != null) time else time + " · " + (m.thenLine ?: Txt.s(R.string.wdg_last_today)), m.title, m.joinUrl != null)
        }

        const val ONE_LINE_MAX = 24
        const val WIDE_MAIN_DP = 58.0
        const val SMALL_INNER_DP = 98.0
        const val SMALL_JOIN_DP = 28.0

        // 4x3: strip + gap + tiles inside a 180dp cell less 12dp padding top and bottom.
        const val QUICK_STRIP_DP = 52.0
        const val QUICK_GAP_DP = 8.0
        const val QUICK_TILE_DP = 88.0
        const val QUICK_INNER_DP = 156.0

        /** One line of at most 100 characters; the view ellipsizes the rest. */
        fun clean(title: String): String = title.replace(Regex("\\s+"), " ").trim().ifEmpty { Txt.s(R.string.common_untitled) }.let { if (it.length > 100) it.take(99).trimEnd() + "…" else it }
    }
}
