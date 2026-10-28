package app.plait.data.brief

import app.plait.R
import app.plait.data.CalEvent
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import java.time.Instant
import java.time.ZoneId

/** One slot on the day strip: where a meeting sits between the start and end of the day. */
data class LoadBlock(val eventId: String, val from: Float, val to: Float, val kind: MeetingKind)

/**
 * The words at the top of the home page: a bold headline and a short summary of the day, worked
 * out on the phone from the calendar, the invites and the emails attached to meetings.
 */
data class DayBrief(
    val headline: String,
    val summary: String,
    /** Short flags per meeting id: "No agenda", "2 emails · risk". */
    val flags: Map<String, String>,
    val load: List<LoadBlock>,
    val loadStartHour: Int,
    val loadEndHour: Int,
)

object DayBriefBuilder {
    private fun hm(ms: Long, zone: ZoneId) = Bidi.isolate(Instant.ofEpochMilli(ms).atZone(zone).format(app.plait.data.TimeFmt.clock))
    private fun title(t: String) = Bidi.isolate(tidyTitle(t))

    /**
     * [today] is today's events in time order, all-day events first. [briefs] holds each meeting's
     * brief; [emailFlags] holds a note per meeting with attached emails ("2 emails · risk").
     */
    fun build(
        today: List<CalEvent>,
        briefs: Map<String, MeetingBrief>,
        emailFlags: Map<String, String>,
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DayBrief {
        val timed = today.filter { !it.allDay && it.endMs > it.startMs }
        val flags = LinkedHashMap<String, String>()
        for (ev in today) {
            val b = briefs[ev.id] ?: continue
            val email = emailFlags[ev.id]
            val flag = when {
                email != null -> email
                b.kind == MeetingKind.External && b.aboutIsGuess && b.agenda.isEmpty() -> Txt.s(R.string.gen_flag_no_agenda)
                b.kind == MeetingKind.Interview -> Txt.s(R.string.gen_flag_check_guests)
                else -> null
            }
            if (flag != null) flags[ev.id] = flag
        }

        val startHour = ((timed.minOfOrNull { hourOf(it.startMs, zone) } ?: 8).coerceAtMost(8)).coerceAtLeast(0)
        val endHour = ((timed.maxOfOrNull { hourOfCeil(it.endMs, zone) } ?: 18).coerceAtLeast(18)).coerceAtMost(24)
        val width = (endHour - startHour).coerceAtLeast(1) * 60f
        val dayStart = Instant.ofEpochMilli(timed.firstOrNull()?.startMs ?: nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val load = timed.map { ev ->
            val from = ((ev.startMs - dayStart) / 60_000f - startHour * 60f) / width
            val to = ((ev.endMs - dayStart) / 60_000f - startHour * 60f) / width
            LoadBlock(ev.id, from.coerceIn(0f, 1f), to.coerceIn(0f, 1f), briefs[ev.id]?.kind ?: MeetingKind.General)
        }

        if (today.isEmpty()) {
            return DayBrief(Txt.s(R.string.gen_brief_clear_headline), Txt.s(R.string.gen_brief_clear_summary), flags, load, startHour, endHour)
        }
        val left = timed.filter { it.endMs > nowMs }
        val running = timed.firstOrNull { it.startMs <= nowMs && it.endMs > nowMs }
        val next = left.firstOrNull { it.startMs > nowMs }
        val external = left.count { briefs[it.id]?.kind == MeetingKind.External }

        val headline = when {
            timed.isNotEmpty() && left.isEmpty() -> Txt.s(R.string.gen_brief_wrap)
            running != null -> Txt.s(R.string.gen_brief_in_now, Bidi.isolate(tidyTitle(running.title).take(40)))
            next != null && (next.startMs - nowMs) / 60_000 < 15 ->
                ((next.startMs - nowMs) / 60_000).coerceAtLeast(1).toInt().let { m ->
                    Txt.plural(R.plurals.gen_brief_starts_in, m, Bidi.isolate(tidyTitle(next.title).take(40)), m)
                }
            left.size == 1 -> Txt.s(R.string.gen_brief_one_left)
            left.isNotEmpty() -> Txt.plural(R.plurals.gen_brief_meetings_ahead, left.size, DayAnalytics.word(left.size))
            else -> Txt.s(R.string.gen_brief_all_day_only)
        }

        val parts = ArrayList<String>()
        if (timed.isNotEmpty() && left.isEmpty()) {
            parts.add(Txt.plural(R.plurals.gen_brief_done, timed.size))
        } else if (left.isNotEmpty()) {
            if (running == null && next != null) parts.add(Txt.s(R.string.gen_brief_next_at, title(next.title), hm(next.startMs, zone)))
            if (external > 0) parts.add(Txt.plural(R.plurals.gen_brief_external, external))
            val b2b = backToBack(left)
            if (b2b >= 3) parts.add(Txt.plural(R.plurals.gen_brief_back_to_back, b2b, b2b, hm(left.first().startMs, zone)))
            val free = left.last().endMs
            parts.add(Txt.s(R.string.gen_brief_free_from, hm(free, zone)))
            val gap = biggestGap(left, nowMs)
            if (gap != null && gap.third >= 45) parts.add(Txt.s(R.string.gen_brief_longest_break, hm(gap.first, zone), hm(gap.second, zone), DayAnalytics.span(gap.third)))
        }
        val prep = flags.entries.firstOrNull { briefs[it.key]?.kind != null && (left.any { e -> e.id == it.key }) }
        if (prep != null) {
            val flag = prep.value.lowercase().replaceFirstChar { it.uppercase() }
            parts.add(Txt.s(R.string.gen_brief_prep, title(today.first { it.id == prep.key }.title), flag))
        }
        return DayBrief(headline, parts.joinToString(" "), flags, load, startHour, endHour)
    }

    private fun hourOf(ms: Long, zone: ZoneId) = Instant.ofEpochMilli(ms).atZone(zone).hour
    private fun hourOfCeil(ms: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return if (t.minute > 0) t.hour + 1 else t.hour
    }

    /** Length of the run of meetings that start within ten minutes of the previous one ending. */
    internal fun backToBack(events: List<CalEvent>): Int {
        var best = 0
        var run = 0
        var prevEnd = Long.MIN_VALUE
        for (e in events) {
            run = if (prevEnd != Long.MIN_VALUE && e.startMs - prevEnd <= 10 * 60_000) run + 1 else 1
            best = maxOf(best, run)
            prevEnd = maxOf(prevEnd, e.endMs)
        }
        return best
    }

    /** The longest gap between consecutive meetings, as (from, to, minutes). */
    internal fun biggestGap(events: List<CalEvent>, nowMs: Long): Triple<Long, Long, Long>? {
        var best: Triple<Long, Long, Long>? = null
        var prevEnd = maxOf(nowMs, events.firstOrNull()?.let { if (it.startMs < nowMs) it.endMs else nowMs } ?: nowMs)
        for (e in events) {
            if (e.endMs <= prevEnd) continue
            val gap = (e.startMs - prevEnd) / 60_000
            if (e.startMs > prevEnd && gap > (best?.third ?: 0L) && prevEnd > nowMs) best = Triple(prevEnd, e.startMs, gap)
            prevEnd = maxOf(prevEnd, e.endMs)
        }
        return best
    }
}

object DayBriefPrompt {
    /** The facts for the phone's built-in model, which turns them into a few friendly sentences. */
    fun build(today: List<CalEvent>, briefs: Map<String, MeetingBrief>, day: DayBrief, zone: ZoneId = ZoneId.systemDefault()): String {
        val clock = app.plait.data.TimeFmt.clock
        val lines = today.take(10).joinToString("\n") { ev ->
            val b = briefs[ev.id]
            val at = if (ev.allDay) "all day" else Instant.ofEpochMilli(ev.startMs).atZone(zone).format(clock)
            val extra = listOfNotNull(b?.kind?.label, b?.withLine?.takeIf { it.isNotBlank() }, day.flags[ev.id]).joinToString("; ")
            "- $at ${tidyTitle(ev.title)}" + if (extra.isNotBlank()) " ($extra)" else ""
        }
        // In Arabic the harness's language rule follows the instructions, so the answer is written in Arabic.
        return app.plait.ai.AiHarness.withLanguage("You write a short morning briefing for a busy person. Use only the facts below. Do not invent anything.\n" +
            "Write 2 or 3 plain sentences, no lists, no greeting. Say what the day looks like and what needs attention first.") + "\n\n" +
            "Meetings today:\n$lines\n\nSummary so far: ${day.summary}"
    }
}
