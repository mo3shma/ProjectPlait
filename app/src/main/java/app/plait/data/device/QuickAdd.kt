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
 * Turns "lunch with sam@acme.com friday 1pm for 1h at Café Nero" into a meeting. Plain rules, no AI needed: dates (today,
 * tomorrow, weekdays, "next monday", "in 3 days", "15 oct"), times (3pm, 15:30, noon), lengths (for 45 min, 1.5h, 3-4pm),
 * guests (email addresses) and places ("at …", "@ …").
 *
 * Arabic is read too, whatever the app's language, whenever the text has Arabic letters ([parseArabic]): "غداء مع
 * sam@acme.com الجمعة 1 م لمدة ساعة في مقهى نيرو". Arabic-Indic digits (٠-٩, ۰-۹) are read as 0-9 everywhere.
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

    /** Arabic-Indic and Persian digits typed on an Arabic keyboard, as 0-9. */
    internal fun westernDigits(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '\u0660'..'\u0669') '0' + (c - '\u0660') else if (c in '\u06F0'..'\u06F9') '0' + (c - '\u06F0') else c)
    }

    private val arabicLetter = Regex("[\\u0621-\\u064A]")

    fun parse(input: String, today: LocalDate): QuickResult {
        var s = " " + westernDigits(input).trim().replace(Regex("\\s+"), " ") + " "
        fun take(r: Regex): MatchResult? = r.find(s)?.also { s = s.replaceRange(it.range, " ") }

        val guests = email.findAll(s).map { it.value.lowercase() }.distinct().toList()
        // "lunch with sam@acme.com" keeps its meaning as "lunch with Sam"; an address anywhere else is just removed.
        val withName = StringBuilder()
        var last = 0
        for (m in email.findAll(s)) {
            withName.append(s, last, m.range.first)
            val after = withName.toString().trimEnd()
            if (after.endsWith("with", ignoreCase = true) || withAr.containsMatchIn(after)) {
                withName.append(m.value.substringBefore('@').split('.', '_', '-', '+').firstOrNull { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "")
            } else withName.append(' ')
            last = m.range.last + 1
        }
        withName.append(s, last, s.length)
        s = withName.toString()

        // Arabic first, so "الساعة 3:30" is read whole before the English rules see "3:30".
        val ar = if (arabicLetter.containsMatchIn(s)) parseArabic(s, today).also { s = it.rest } else null

        // length: "for 45 min", "for 1.5 hours", "90 minutes"
        var duration: Int? = ar?.durationMin
        take(rx("""\bfor\s+(\d+(?:\.\d+)?)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)\b"""))?.let { m ->
            val n = m.groupValues[1].toDouble()
            duration = if (m.groupValues[2].lowercase().startsWith("h")) (n * 60).toInt() else n.toInt()
        }
        if (duration == null) take(rx("""\b(\d+(?:\.\d+)?)\s*(hours?|hrs?|minutes?|mins?)\b"""))?.let { m ->
            val n = m.groupValues[1].toDouble()
            duration = if (m.groupValues[2].lowercase().startsWith("h")) (n * 60).toInt() else n.toInt()
        }

        // time range "3-4pm", "3pm-4pm", "3 to 4:30pm"
        var time: LocalTime? = ar?.time
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
        var date: LocalDate? = ar?.date
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
        if (place.isEmpty() && ar != null) take(Regex("""$B0(?:في|عند)\s+(\S[^,،]*?)(?=\s+(?:مع|لمدة)$B1|\s*[,،]|\s*$)"""))?.let { place = it.groupValues[1].trim() }

        // title is what is left
        var title = s.replace(Regex("\\s+"), " ").trim()
        title = title.replace(rx("""\b(with|on|at|for|in|to|and)\s*$"""), "").trim().trimEnd(',', '-', '–').trim()
        title = title.replace(rx("""^(add|schedule|set up|book|create|new meeting:?|meeting:?)\s+"""), "").trim()
        if (ar != null) {
            title = title.replace(Regex("""(?:\s+(?:مع|في|عند|يوم|لمدة|مدة|من|الساعة|الساعه|و))+\s*$"""), "").trim().trimEnd(',', '،', '-', '–').trim()
            title = title.replace(Regex("""^(?:أضف|اضف|إضافة|اضافة|احجز|جدولة|موعد:|اجتماع جديد:)\s*"""), "").trim()
        }
        if (title.isNotEmpty()) title = title.replaceFirstChar { it.uppercase() }
        return QuickResult(title, date, time, duration, guests, place, allDay)
    }

    // Arabic rules. Java's \b only knows ASCII letters, so word edges are spelled out; marks (tanween, shadda) count as letters.
    private const val B0 = """(?<![\p{L}\p{M}\p{N}_])"""
    private const val B1 = """(?![\p{L}\p{M}\p{N}_])"""
    /** The optional ending of an adverb: "صباحًا", "صباحاً", "صباحا". */
    private const val TAN = "(?:\u064Bا|ا\u064B|ا|\u064B)?"
    private const val AM = "(?:(?:ال)?صباح$TAN|(?:ال)?صبح|(?:ال)?فجر$TAN|ص)"
    private const val PM = "(?:(?:ال)?مساء$TAN|بعد الظهر|(?:ال)?ظهر$TAN|(?:ال)?عصر$TAN|(?:ال)?ليل$TAN|بالليل|م)"
    private const val HOUR = "(?:الساع[ةه]\\s*)"
    private val withAr = Regex("""(?:^|\s)مع$""")
    private val arDays = mapOf(
        "أحد" to DayOfWeek.SUNDAY, "احد" to DayOfWeek.SUNDAY, "اثنين" to DayOfWeek.MONDAY, "إثنين" to DayOfWeek.MONDAY, "أثنين" to DayOfWeek.MONDAY,
        "اتنين" to DayOfWeek.MONDAY, "ثلاثاء" to DayOfWeek.TUESDAY, "ثلاثا" to DayOfWeek.TUESDAY, "تلات" to DayOfWeek.TUESDAY, "أربعاء" to DayOfWeek.WEDNESDAY,
        "اربعاء" to DayOfWeek.WEDNESDAY, "أربعا" to DayOfWeek.WEDNESDAY, "اربعا" to DayOfWeek.WEDNESDAY, "خميس" to DayOfWeek.THURSDAY,
        "جمعة" to DayOfWeek.FRIDAY, "جمعه" to DayOfWeek.FRIDAY, "سبت" to DayOfWeek.SATURDAY,
    )
    private val arMonths = mapOf(
        "يناير" to Month.JANUARY, "فبراير" to Month.FEBRUARY, "مارس" to Month.MARCH, "أبريل" to Month.APRIL, "ابريل" to Month.APRIL, "إبريل" to Month.APRIL,
        "مايو" to Month.MAY, "يونيو" to Month.JUNE, "يونيه" to Month.JUNE, "يوليو" to Month.JULY, "يوليه" to Month.JULY, "أغسطس" to Month.AUGUST,
        "اغسطس" to Month.AUGUST, "سبتمبر" to Month.SEPTEMBER, "أكتوبر" to Month.OCTOBER, "اكتوبر" to Month.OCTOBER, "نوفمبر" to Month.NOVEMBER,
        "ديسمبر" to Month.DECEMBER, "كانون الثاني" to Month.JANUARY, "شباط" to Month.FEBRUARY, "آذار" to Month.MARCH, "نيسان" to Month.APRIL,
        "أيار" to Month.MAY, "حزيران" to Month.JUNE, "تموز" to Month.JULY, "آب" to Month.AUGUST, "أيلول" to Month.SEPTEMBER,
        "تشرين الأول" to Month.OCTOBER, "تشرين الثاني" to Month.NOVEMBER, "كانون الأول" to Month.DECEMBER,
    )

    /** What the Arabic rules took out of the text, and what is left of it ([rest]) for the English rules and the title. */
    internal class ArabicParts(val rest: String, val date: LocalDate?, val time: LocalTime?, val durationMin: Int?)

    /**
     * Arabic dates, times and lengths: "اليوم", "غدًا" / "بكرة", "بعد غد", "الجمعة", "يوم الأحد القادم", "الأسبوع القادم",
     * "بعد 3 أيام", "15 أكتوبر"; "الساعة 1 م", "1 مساءً", "10 صباحًا", "3:30 م", "من 3 إلى 4 م", "الساعة 3:30" (1 to 6 with
     * no morning or evening word is the afternoon, like English "at 3"), "الظهر", "الليلة"; "لمدة ساعة", "ساعتين",
     * "ساعة ونصف", "نصف ساعة", "30 دقيقة", "لمدة 2 ساعات". Places ("في …", "عند …") are read in [parse] after the English rules.
     */
    internal fun parseArabic(text: String, today: LocalDate): ArabicParts {
        var s = text
        fun take(p: String): MatchResult? = Regex(p).find(s)?.also { s = s.replaceRange(it.range, " ") }
        fun clock(h: Int, min: Int, am: String, pm: String): LocalTime? {
            val hh = when {
                am.isNotEmpty() -> hour24(h, "am")
                pm.contains("ليل") && h == 12 -> 0
                pm.isNotEmpty() -> hour24(h, "pm")
                else -> h
            }
            return if (hh in 0..23 && min in 0..59) LocalTime.of(hh, min) else null
        }

        var duration: Int? = null
        take("""$B0(?:(?:لمدة|مدة)\s*)?(\d+(?:\.\d+)?)\s*(ساعات|ساعة|ساعه|دقائق|دقيقة|دقيقه|دقايق)$B1""")?.let { m ->
            val n = m.groupValues[1].toDouble()
            duration = if (m.groupValues[2].startsWith("س")) (n * 60).toInt() else n.toInt()
        }
        val lengths = listOf("ساعتين" to 120, "ساعتان" to 120, "ساعة ونصف" to 90, "ساعة و نصف" to 90, "ساعة ونص" to 90, "ساعة وربع" to 75,
            "نصف ساعة" to 30, "نص ساعة" to 30, "ربع ساعة" to 15)
        if (duration == null) take("""$B0(?:(?:لمدة|مدة)\s+)?(${lengths.joinToString("|") { it.first }})$B1""")?.let { m ->
            duration = lengths.first { it.first == m.groupValues[1] }.second
        }
        // A bare "ساعة" is also a watch or a clock, so it is a length only after "لمدة".
        if (duration == null) take("""$B0(?:لمدة|مدة)\s+(?:ساعة|ساعه)$B1""")?.let { duration = 60 }

        var time: LocalTime? = null
        take("""$B0(?:من\s+)?$HOUR?(\d{1,2})(?::(\d{2}))?\s*(?:($AM)|($PM))?\s*(?:-|–|إلى|الى|حتى|لـ|ل)\s*$HOUR?(\d{1,2})(?::(\d{2}))?\s*(?:($AM)|($PM))$B1""")?.let { m ->
            val g = m.groupValues
            val endAm = g[7]; val endPm = g[8]
            val start = clock(g[1].toInt(), g[2].toIntOrNull() ?: 0, g[3].ifEmpty { if (g[4].isEmpty()) endAm else "" }, g[4].ifEmpty { if (g[3].isEmpty()) endPm else "" })
            val end = clock(g[5].toInt(), g[6].toIntOrNull() ?: 0, endAm, endPm)
            if (start != null && end != null) {
                time = start
                val len = (end.toSecondOfDay() - start.toSecondOfDay()) / 60
                if (len > 0 && duration == null) duration = len
            }
        }
        if (time == null) take("""$B0(?:في\s+)?$HOUR?(\d{1,2})(?::(\d{2}))?\s*(?:($AM)|($PM))$B1""")?.let { m ->
            time = clock(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0, m.groupValues[3], m.groupValues[4])
        }
        if (time == null) take("""$B0(?:في\s+)?$HOUR(\d{1,2})(?::(\d{2}))?$B1""")?.let { m ->
            val h = m.groupValues[1].toInt()
            // "الساعة 3" means the afternoon, "الساعة 9" the morning, as with English "at 3".
            time = clock(if (h in 1..6) h + 12 else h, m.groupValues[2].toIntOrNull() ?: 0, "", "")
        }
        if (time == null) take("""$B0(?:عند\s+|في\s+)?(?:منتصف الليل)$B1""")?.let { time = LocalTime.MIDNIGHT }
        if (time == null) take("""(?<!بعد\s)$B0(?:عند\s+|في\s+)?(?:الظهر|الظهيرة)$B1""")?.let { time = LocalTime.NOON }

        var date: LocalDate? = null
        if (take("""${B0}الليلة$B1""") != null) { date = today; if (time == null) time = LocalTime.of(19, 0) }
        if (date == null) take("""${B0}بعد\s+(?:غد$TAN|بكر[ةه])$B1""")?.let { date = today.plusDays(2) }
        if (date == null) take("""$B0(?:غد$TAN|بكر[ةه]|الغد)$B1""")?.let { date = today.plusDays(1) }
        if (date == null) take("""${B0}اليوم$B1""")?.let { date = today }
        if (date == null) take("""${B0}بعد\s+(?:(\d+)\s*(أيام|ايام|يوم|يومًا|يوما|أسابيع|اسابيع|أسبوع|اسبوع)|(يومين|أسبوعين|اسبوعين|أسبوع|اسبوع))$B1""")?.let { m ->
            val g = m.groupValues
            date = when {
                g[3].startsWith("يوم") -> today.plusDays(2)
                g[3].isNotEmpty() -> today.plusWeeks(if (g[3].endsWith("ين")) 2 else 1)
                g[2].contains("سب") -> today.plusWeeks(g[1].toLong())
                else -> today.plusDays(g[1].toLong())
            }
        }
        if (date == null) take("""$B0(?:ال)?[أا]سبوع\s+(?:القادم|المقبل|الجاي|الجاى)$B1""")?.let { date = today.plusWeeks(1) }
        if (date == null) take("""$B0(?:في\s+)?(?:يوم\s+(?:ال)?|ال)(${arDays.keys.joinToString("|")})$B1(\s+(?:القادمة|القادم|المقبلة|المقبل|الجاية|الجاي|الجاى)$B1)?""")?.let { m ->
            val dow = arDays.getValue(m.groupValues[1])
            var d = today.plusDays(((dow.value - today.dayOfWeek.value + 7) % 7).toLong())
            if (m.groupValues[2].isNotBlank() && d == today) d = d.plusWeeks(1)
            date = d
        }
        if (date == null) take("""$B0(?:في\s+)?(\d{1,2})\s+(?:من\s+)?(${arMonths.keys.joinToString("|")})$B1""")?.let { m ->
            date = resolve(today, arMonths.getValue(m.groupValues[2]), m.groupValues[1].toInt())
        }
        return ArabicParts(s, date, time, duration)
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
