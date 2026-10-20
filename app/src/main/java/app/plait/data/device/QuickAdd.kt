package app.plait.data.device

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month

/** What was understood from a typed sentence. Anything not mentioned is null, so the form keeps its own default. */
data class QuickResult(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val durationMin: Int? = null,
    val guests: List<String> = emptyList(),
    val place: String = "",
    val allDay: Boolean = false,
)

/**
 * Turns "lunch with sam@acme.com friday 1pm for 1h at Café Nero" into a meeting. Plain rules, English only, no AI
 * needed: dates (today, tomorrow, weekdays, "next monday", "in 3 days", "15 oct"), times (3pm, 15:30, noon),
 * lengths (for 45 min, 1.5h, 3-4pm), guests (email addresses) and places ("at …", "@ …").
 */
object QuickAdd {
    private val days = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
    )
    private val months = mapOf(
        "january" to Month.JANUARY, "jan" to Month.JANUARY, "february" to Month.FEBRUARY, "feb" to Month.FEBRUARY, "march" to Month.MARCH, "mar" to Month.MARCH,
        "april" to Month.APRIL, "apr" to Month.APRIL, "may" to Month.MAY, "june" to Month.JUNE, "jun" to Month.JUNE, "july" to Month.JULY, "jul" to Month.JULY,
        "august" to Month.AUGUST, "aug" to Month.AUGUST, "september" to Month.SEPTEMBER, "sep" to Month.SEPTEMBER, "sept" to Month.SEPTEMBER,
        "october" to Month.OCTOBER, "oct" to Month.OCTOBER, "november" to Month.NOVEMBER, "nov" to Month.NOVEMBER, "december" to Month.DECEMBER, "dec" to Month.DECEMBER,
    )
    private val email = Regex("""[A-Za-z0-9._%+\-']+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    fun parse(input: String, today: LocalDate): QuickResult {
        var s = " " + input.trim().replace(Regex("\\s+"), " ") + " "
        fun take(r: Regex): MatchResult? = r.find(s)?.also { s = s.replaceRange(it.range, " ") }

        val guests = email.findAll(s).map { it.value.lowercase() }.distinct().toList()
        // "lunch with sam@acme.com" keeps its meaning as "lunch with Sam"; an address anywhere else is just removed.
        val withName = StringBuilder()
        var last = 0
        for (m in email.findAll(s)) {
            withName.append(s, last, m.range.first)
            val after = withName.toString().trimEnd()
            if (after.endsWith("with", ignoreCase = true)) {
                withName.append(m.value.substringBefore('@').split('.', '_', '-', '+').firstOrNull { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "")
            } else withName.append(' ')
            last = m.range.last + 1
        }
        withName.append(s, last, s.length)
        s = withName.toString()

        // length: "for 45 min", "for 1.5 hours", "90 minutes"
        var duration: Int? = null
        take(rx("""\bfor\s+(\d+(?:\.\d+)?)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)\b"""))?.let { m ->
            val n = m.groupValues[1].toDouble()
            duration = if (m.groupValues[2].lowercase().startsWith("h")) (n * 60).toInt() else n.toInt()
        }
        if (duration == null) take(rx("""\b(\d+(?:\.\d+)?)\s*(hours?|hrs?|minutes?|mins?)\b"""))?.let { m ->
            val n = m.groupValues[1].toDouble()
            duration = if (m.groupValues[2].lowercase().startsWith("h")) (n * 60).toInt() else n.toInt()
        }

        // time range "3-4pm", "3pm-4pm", "3 to 4:30pm"
        var time: LocalTime? = null
        take(rx("""\b(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*(?:-|to|until)\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b"""))?.let { m ->
            val endAp = m.groupValues[6].lowercase()
            val startAp = m.groupValues[3].lowercase().ifEmpty { endAp }
            val start = hour24(m.groupValues[1].toInt(), startAp) to (m.groupValues[2].toIntOrNull() ?: 0)
            val end = hour24(m.groupValues[4].toInt(), endAp) to (m.groupValues[5].toIntOrNull() ?: 0)
            if (start.first in 0..23 && end.first in 0..23) {
                time = LocalTime.of(start.first, start.second)
                val len = (end.first * 60 + end.second) - (start.first * 60 + start.second)
                if (len > 0 && duration == null) duration = len
            }
        }
        if (time == null) {
            take(rx("""\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)\b"""))?.let { m ->
                time = LocalTime.of(hour24(m.groupValues[1].toInt(), m.groupValues[3].lowercase()).coerceIn(0, 23), (m.groupValues[2].toIntOrNull() ?: 0).coerceIn(0, 59))
            }
        }
        if (time == null) take(rx("""\b(?:at\s+)?([01]?\d|2[0-3]):([0-5]\d)\b"""))?.let { m -> time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        if (time == null) take(rx("""\bnoon\b"""))?.let { time = LocalTime.NOON }
        if (time == null) take(rx("""\bmidnight\b"""))?.let { time = LocalTime.MIDNIGHT }
        if (time == null) take(rx("""\bat\s+(\d{1,2})\b(?!\s*(?:st|nd|rd|th|[a-z]))"""))?.let { m ->
            val h = m.groupValues[1].toInt()
            if (h in 1..12) time = LocalTime.of(if (h in 1..6) h + 12 else h, 0) // "at 3" means afternoon; "at 9" means morning
        }

        // date
        var date: LocalDate? = null
        var allDay = false
        if (take(rx("""\b(?:all[- ]day)\b""")) != null) allDay = true
        if (take(rx("""\btonight\b""")) != null) { date = today; if (time == null) time = LocalTime.of(19, 0) }
        if (date == null && take(rx("""\b(?:tomorrow|tmrw|tmr)\b""")) != null) date = today.plusDays(1)
        if (date == null && take(rx("""\btoday\b""")) != null) date = today
        if (date == null) take(rx("""\bin\s+(\d+)\s*(days?|weeks?)\b"""))?.let { m ->
            val n = m.groupValues[1].toLong()
            date = today.plusDays(if (m.groupValues[2].lowercase().startsWith("w")) n * 7 else n)
        }
        if (date == null) take(rx("""\bnext\s+week\b"""))?.let { date = today.plusWeeks(1) }
        if (date == null) take(rx("""\b(next\s+|this\s+|on\s+)?(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)\b"""))?.let { m ->
            val dow = days.getValue(m.groupValues[2].lowercase())
            var d = today.plusDays(((dow.value - today.dayOfWeek.value + 7) % 7).toLong())
            val prefix = m.groupValues[1].trim().lowercase()
            if (prefix == "next" && d <= today.plusDays(0) || (prefix == "next" && d.isBefore(today.plusDays(7)) && dow.value > today.dayOfWeek.value && false)) d = d.plusWeeks(1)
            if (prefix == "next" && d == today) d = d.plusWeeks(1)
            date = d
        }
        if (date == null) take(rx("""\b(?:on\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?(${months.keys.joinToString("|")})\b"""))?.let { m ->
            date = resolve(today, months.getValue(m.groupValues[2].lowercase()), m.groupValues[1].toInt())
        }
        if (date == null) take(rx("""\b(?:on\s+)?(${months.keys.joinToString("|")})\s+(\d{1,2})(?:st|nd|rd|th)?\b"""))?.let { m ->
            date = resolve(today, months.getValue(m.groupValues[1].lowercase()), m.groupValues[2].toInt())
        }

        // place
        var place = ""
        take(rx("""\s@\s*([^,]+?)(?=\s(?:for|on|tomorrow|today|at)\b|$)"""))?.let { place = it.groupValues[1].trim() }
        if (place.isEmpty()) take(rx("""\bat\s+([A-Z][^,]*?)(?=\s(?:for|on|tomorrow|today|next|this)\b|\s*$)"""))?.let { place = it.groupValues[1].trim() }
        if (place.isEmpty()) take(Regex("""\bat\s+([A-Za-zÀ-ÿ][^,]*?)\s*$"""))?.let { place = it.groupValues[1].trim() }

        // title is what is left
        var title = s.replace(Regex("\\s+"), " ").trim()
        title = title.replace(rx("""\b(with|on|at|for|in|to|and)\s*$"""), "").trim().trimEnd(',', '-', '–').trim()
        title = title.replace(rx("""^(add|schedule|set up|book|create|new meeting:?|meeting:?)\s+"""), "").trim()
        if (title.isNotEmpty()) title = title.replaceFirstChar { it.uppercase() }
        return QuickResult(title, date, time, duration, guests, place, allDay)
    }

    private fun hour24(h: Int, ampm: String): Int = when (ampm) {
        "am" -> if (h == 12) 0 else h
        "pm" -> if (h == 12) 12 else h + 12
        else -> h
    }

    /** The next time this day of the month comes round: this year if it hasn't passed, else next year. */
    private fun resolve(today: LocalDate, month: Month, day: Int): LocalDate? {
        val d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (d.isBefore(today)) d.plusYears(1) else d
    }

    /** The request to an AI for sentences the rules couldn't read. */
    fun aiPrompt(text: String, today: LocalDate): String =
        "Turn this into a calendar entry. Today is ${today.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }} $today.\n" +
            "Text: \"${text.replace('"', '\'')}\"\n" +
            "Answer in exactly this format and nothing else:\nTitle: short title\nDate: YYYY-MM-DD or none\nTime: HH:MM in 24-hour form, or none\nMinutes: a number or none\nPlace: place or none\nGuests: email addresses separated by commas, or none"

    /** Reads the AI's answer. Null if it has no title. */
    fun parseAi(answer: String): QuickResult? {
        fun field(name: String) = answer.lines().firstOrNull { it.trim().startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() && !it.equals("none", true) }
        val title = field("Title") ?: return null
        val date = field("Date")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val time = field("Time")?.let { t -> Regex("""(\d{1,2}):(\d{2})""").find(t)?.let { m -> runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull() } }
        val minutes = field("Minutes")?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }?.takeIf { it in 5..720 }
        val guests = field("Guests")?.split(',', ';', ' ')?.map { it.trim().lowercase() }?.filter { email.matches(it) }.orEmpty()
        return QuickResult(title, date, time, minutes, guests, field("Place").orEmpty())
    }
}
