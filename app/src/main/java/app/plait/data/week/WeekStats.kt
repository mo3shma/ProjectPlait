package app.plait.data.week

import android.content.Context
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.brief.DayAnalytics
import app.plait.data.brief.MeetingKind
import app.plait.data.memo.Checklist
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoKind
import app.plait.data.memo.MemoStore
import app.plait.data.tasks.TaskIdea
import app.plait.data.tasks.TaskIdeas
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class WeekDay(val date: LocalDate, val meetings: Int, val minutes: Long)

data class WeekStats(
    val days: List<LocalDate>,
    val meetings: Int,
    val minutes: Long,
    val perDay: List<WeekDay>,
    val busiest: WeekDay?,
    val quietest: WeekDay?,
    val longest: CalEvent?,
    val externalMeetings: Int,
    val byKind: List<Pair<MeetingKind, Long>>,
    val finishedMeetings: Int,
    val notedMeetings: Int,
    val missingNotes: Int,
    val tasksDone: Int,
    val tasksOpen: Int,
)

/** Same meeting rule as [DayAnalytics]: timed, with a real length. */
private fun isMeeting(e: CalEvent) = !e.allDay && e.endMs > e.startMs

/** A meeting belongs to the day it starts on, so one running past midnight is not counted twice. */
private fun startDay(e: CalEvent, zone: ZoneId): LocalDate = Instant.ofEpochMilli(e.startMs).atZone(zone).toLocalDate()

private fun minutes(e: CalEvent) = (e.endMs - e.startMs) / 60_000

object WeekAnalytics {
    /**
     * [days] are the work days to measure; events starting on other days are ignored. Minutes only count meetings that
     * have started ([nowMs]): a running one adds the part so far, a future one adds nothing.
     */
    fun compute(
        days: List<LocalDate>, events: List<CalEvent>, kinds: Map<String, MeetingKind>, noted: Set<String>,
        tasksDone: Int, tasksOpen: Int, nowMs: Long, zone: ZoneId,
    ): WeekStats {
        val set = days.toSet()
        val timed = events.filter { isMeeting(it) && startDay(it, zone) in set }.distinctBy { it.id }
        fun soFar(e: CalEvent) = (minOf(e.endMs, nowMs) - e.startMs).coerceAtLeast(0) / 60_000
        val byDay = timed.groupBy { startDay(it, zone) }
        val perDay = days.map { d -> byDay[d].orEmpty().let { l -> WeekDay(d, l.size, l.sumOf(::soFar)) } }
        val perKind = HashMap<MeetingKind, Long>()
        for (e in timed) perKind.merge(kinds[e.id] ?: MeetingKind.General, soFar(e), Long::plus)
        val finished = timed.filter { it.endMs <= nowMs }
        val notedCount = finished.count { it.id in noted }
        // Same rule as the day recap: personal and focus time do not need notes.
        val missing = finished.count { kinds[it.id] != MeetingKind.Personal && kinds[it.id] != MeetingKind.Focus && it.id !in noted }
        return WeekStats(
            days = days,
            meetings = timed.size,
            minutes = perDay.sumOf { it.minutes },
            perDay = perDay,
            busiest = perDay.filter { it.meetings > 0 }.maxByOrNull { it.minutes },
            quietest = perDay.minByOrNull { it.minutes },
            longest = timed.filter { it.startMs <= nowMs }.maxByOrNull { it.endMs - it.startMs },
            externalMeetings = timed.count { kinds[it.id] == MeetingKind.External },
            byKind = perKind.entries.filter { it.value > 0 }.sortedByDescending { it.value }.map { it.key to it.value },
            finishedMeetings = finished.size,
            notedMeetings = notedCount,
            missingNotes = missing,
            tasksDone = tasksDone,
            tasksOpen = tasksOpen,
        )
    }

    /** Plain-language minutes ("1 h 30 min"), shared with the day recap. */
    fun span(min: Long): String = DayAnalytics.span(min)
}

data class DayPreview(val date: LocalDate, val meetings: Int, val minutes: Long, val first: CalEvent?, val titles: List<String>)

data class WeekPreview(
    val days: List<LocalDate>,
    val meetings: Int,
    val minutes: Long,
    val perDay: List<DayPreview>,
    val busiest: DayPreview?,
    val freeDays: List<LocalDate>,
    val firstMeeting: CalEvent?,
    val longest: CalEvent?,
    val externalMeetings: Int,
)

object WeekPreviews {
    /** What a week ahead looks like: every meeting counts in full, since none has happened yet. */
    fun compute(days: List<LocalDate>, events: List<CalEvent>, kinds: Map<String, MeetingKind>, zone: ZoneId): WeekPreview {
        val set = days.toSet()
        val timed = events.filter { isMeeting(it) && startDay(it, zone) in set }.distinctBy { it.id }.sortedBy { it.startMs }
        val byDay = timed.groupBy { startDay(it, zone) }
        val perDay = days.map { d ->
            val l = byDay[d].orEmpty()
            DayPreview(d, l.size, l.sumOf(::minutes), l.firstOrNull(), l.take(3).map { it.title })
        }
        return WeekPreview(
            days = days,
            meetings = timed.size,
            minutes = perDay.sumOf { it.minutes },
            perDay = perDay,
            busiest = perDay.filter { it.meetings > 0 }.maxByOrNull { it.minutes },
            freeDays = perDay.filter { it.meetings == 0 }.map { it.date },
            firstMeeting = timed.firstOrNull(),
            longest = timed.maxByOrNull { it.endMs - it.startMs },
            externalMeetings = timed.count { kinds[it.id] == MeetingKind.External },
        )
    }
}

object WeekEvents {
    /**
     * Events that start on [first]..[last] (inclusive, in [zone]) from the merged list the Home screen uses.
     * Calendars loads 90 days back and 180 days ahead of today, so a work week outside that comes back partial.
     */
    fun between(first: LocalDate, last: LocalDate, zone: ZoneId): List<CalEvent> = within(Calendars.events, first, last, zone)

    internal fun within(events: List<CalEvent>, first: LocalDate, last: LocalDate, zone: ZoneId): List<CalEvent> {
        val from = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return events.filter { it.startMs in from until to }
    }

    /** (done, open) over the checklists written on these meetings plus the task ideas still waiting on them. */
    fun checklistCounts(events: List<CalEvent>, memos: List<Memo>, ideas: List<TaskIdea>): Pair<Int, Int> {
        val ids = events.map { it.id }.toSet()
        val items = memos.filter { it.kind == MemoKind.Checklist && it.eventId in ids }.flatMap { Checklist.parse(it.text) }
        return items.count { it.done } to items.count { !it.done } + ideas.count { it.eventId in ids }
    }

    fun checklistCounts(context: Context, events: List<CalEvent>): Pair<Int, Int> {
        TaskIdeas.load(context)
        return checklistCounts(events, MemoStore.get(context).items.toList(), TaskIdeas.items.toList())
    }
}
