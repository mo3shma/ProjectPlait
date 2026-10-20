package app.plait.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Decides what "today", "up next" and "later" mean. Pure functions of the event list and the clock. */
object Agenda {
    fun dayBounds(nowMs: Long, zone: ZoneId): Pair<Long, Long> {
        val day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        return day.atStartOfDay(zone).toInstant().toEpochMilli() to day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Events that overlap today, all-day first, then by start. */
    fun today(events: List<CalEvent>, nowMs: Long, zone: ZoneId): List<CalEvent> {
        val (from, to) = dayBounds(nowMs, zone)
        return events.filter { it.startMs < to && it.endMs > from }
            .sortedWith(compareBy({ !it.allDay }, { it.startMs }))
    }

    /** First timed event that has not finished yet: the one in progress, or the next to start. */
    fun upNext(today: List<CalEvent>, nowMs: Long): CalEvent? =
        today.firstOrNull { !it.allDay && it.endMs > nowMs }

    /** Everything else still worth showing today. */
    fun later(today: List<CalEvent>, upNext: CalEvent?, nowMs: Long): List<CalEvent> =
        today.filter { it.id != upNext?.id && (it.allDay || it.endMs > nowMs) }

    fun minutesUntil(event: CalEvent, nowMs: Long): Long = Math.floorDiv(event.startMs - nowMs + 59_999, 60_000)

    /** Big number on the Up next card: "Now", "12 min", "1h 20m". */
    fun countdown(event: CalEvent, nowMs: Long): String {
        if (event.startMs <= 0) return "${event.minutesAway} min"
        val m = minutesUntil(event, nowMs)
        return when {
            m <= 0 -> "Now"
            m < 60 -> "$m min"
            m % 60 == 0L -> "${m / 60}h"
            else -> "${m / 60}h ${m % 60}m"
        }
    }

    /** 0..1 for the ring: fills over the hour before the start, then tracks the event itself. */
    fun ringProgress(event: CalEvent, nowMs: Long): Float {
        if (event.startMs <= 0) return 0.7f
        if (nowMs >= event.startMs) {
            val total = (event.endMs - event.startMs).coerceAtLeast(1)
            return ((nowMs - event.startMs).toFloat() / total).coerceIn(0f, 1f)
        }
        val m = minutesUntil(event, nowMs).coerceIn(0, 60)
        return 1f - m / 60f
    }

    /** Monday to Sunday of the current week with the number of events on each day. */
    fun weekLoad(events: List<CalEvent>, nowMs: Long, zone: ZoneId): List<DayLoad> {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        return (0L..6L).map { i ->
            val d = monday.plusDays(i)
            val from = d.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            DayLoad(
                day = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                date = d.dayOfMonth.toString(),
                count = events.count { it.startMs < to && it.endMs > from },
                today = d == today,
            )
        }
    }

    /** Events that finished before [nowMs] today, oldest first. */
    fun earlier(today: List<CalEvent>, nowMs: Long): List<CalEvent> =
        today.filter { !it.allDay && it.endMs <= nowMs }

    /** Events overlapping one calendar day, all-day first. */
    fun eventsOn(events: List<CalEvent>, date: LocalDate, zone: ZoneId): List<CalEvent> {
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return events.filter { it.startMs < to && it.endMs > from }
            .sortedWith(compareBy({ !it.allDay }, { it.startMs }))
    }

    /** Events grouped by the days they touch, for drawing a month or week without re-filtering per cell. */
    fun byDay(events: List<CalEvent>, days: List<LocalDate>, zone: ZoneId): Map<LocalDate, List<CalEvent>> {
        if (days.isEmpty()) return emptyMap()
        // Narrow to the visible range once, so each day only looks through that handful of events.
        val from = days.minOf { it }.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = days.maxOf { it }.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val near = events.filter { it.startMs < to && it.endMs > from }
        return days.associateWith { eventsOn(near, it, zone) }
    }

    /** The seven dates of the week containing [date]. */
    fun weekOf(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(firstDay))
        return List(7) { start.plusDays(it.toLong()) }
    }

    /** Whole weeks covering the month of [date]: 35 or 42 dates. */
    fun monthGrid(date: LocalDate, firstDay: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate> {
        val first = date.withDayOfMonth(1)
        val start = first.with(TemporalAdjusters.previousOrSame(firstDay))
        val last = date.withDayOfMonth(date.lengthOfMonth())
        val end = last.with(TemporalAdjusters.nextOrSame(firstDay.minus(1)))
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
        return List(days) { start.plusDays(it.toLong()) }
    }

    /** One event placed in a day column: which lane it sits in, out of how many side by side. */
    data class Placed(val event: CalEvent, val column: Int, val columns: Int)

    /**
     * Lays out timed events for a day grid. Events that overlap share the width: each cluster of
     * overlapping events is split into as many lanes as it needs, and every event in the cluster
     * gets the same lane count so their widths match.
     */
    fun pack(events: List<CalEvent>): List<Placed> {
        val sorted = events.filter { !it.allDay }.sortedWith(compareBy({ it.startMs }, { -it.endMs }))
        val out = ArrayList<Placed>()
        var cluster = ArrayList<CalEvent>()
        var clusterEnd = Long.MIN_VALUE

        fun flush() {
            if (cluster.isEmpty()) return
            val laneEnds = ArrayList<Long>()
            val lanes = ArrayList<Int>()
            for (e in cluster) {
                var lane = laneEnds.indexOfFirst { it <= e.startMs }
                if (lane < 0) { laneEnds.add(e.endMs); lane = laneEnds.lastIndex } else laneEnds[lane] = e.endMs
                lanes.add(lane)
            }
            cluster.forEachIndexed { i, e -> out.add(Placed(e, lanes[i], laneEnds.size)) }
            cluster = ArrayList()
        }

        for (e in sorted) {
            if (cluster.isNotEmpty() && e.startMs >= clusterEnd) { flush(); clusterEnd = Long.MIN_VALUE }
            cluster.add(e)
            clusterEnd = maxOf(clusterEnd, e.endMs)
        }
        flush()
        return out
    }

    fun isPast(event: CalEvent, nowMs: Long): Boolean = event.endMs > 0 && !event.allDay && event.endMs <= nowMs
}
