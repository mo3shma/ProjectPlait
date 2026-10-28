package app.plait.data.device

import app.plait.R
import app.plait.i18n.Txt
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** A meeting typed into Plait, before it is saved to a calendar. */
data class MeetingDraft(
    val title: String,
    val date: LocalDate,
    val start: LocalTime,
    val durationMin: Int,
    val allDay: Boolean = false,
    val guests: List<String> = emptyList(),
    val place: String = "",
    val notes: String = "",
    /** Minutes before the start for a reminder, or null for none. */
    val reminderMin: Int? = 10,
) {
    fun startMs(zone: ZoneId): Long = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
    fun endMs(zone: ZoneId): Long = startMs(zone) + durationMin * 60_000L

    /** All-day events are stored as UTC midnight to the next UTC midnight, whatever the zone. */
    fun allDayStartMs(): Long = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    fun allDayEndMs(): Long = date.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    /** Null when the draft can be saved, otherwise what is wrong with it. */
    fun problem(): String? = when {
        title.isBlank() -> Txt.s(R.string.devcal_need_title)
        !allDay && durationMin <= 0 -> Txt.s(R.string.devcal_need_length)
        guests.any { !MeetingGuests.valid(it) } -> Txt.s(R.string.devcal_bad_guest)
        else -> null
    }
}

object MeetingGuests {
    private val email = Regex("""^[A-Za-z0-9._%+\-']+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$""")

    fun valid(s: String) = email.matches(s.trim())

    /** Splits what people type or paste: commas, semicolons, spaces and "Name <addr>" forms. */
    fun parse(input: String): List<String> =
        Regex("""<([^>]+)>|[^\s,;<>]+""").findAll(input).map { (it.groups[1]?.value ?: it.value).trim() }
            .filter { it.contains('@') }.map { it.lowercase() }.distinct().toList()
}

/**
 * Plait's own notes are written into the event description between two markers, so writing them
 * again replaces the old block instead of piling up copies.
 */
object NotesBlock {
    const val START = "— Plait notes —"
    const val END = "— end of Plait notes —"

    fun merge(description: String?, notes: String): String {
        val base = strip(description).trimEnd()
        val block = "$START\n${notes.trim()}\n$END"
        return if (base.isBlank()) block else "$base\n\n$block"
    }

    fun strip(description: String?): String {
        val d = description ?: return ""
        val a = d.indexOf(START)
        if (a < 0) return d
        val b = d.indexOf(END, a)
        val after = if (b >= 0) d.substring(b + END.length) else ""
        return (d.substring(0, a).trimEnd() + after).trim()
    }
}
