package app.plait.data

import app.plait.i18n.AppLocale
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Every time shown in Plait is 12-hour ("3:30 PM", Arabic "3:30 م"). One place, so no screen can drift back to 24-hour.
 * Everything is in the app's language ([AppLocale.locale]) with Western digits; English output is unchanged.
 */
object TimeFmt {
    private val cache = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

    /** A formatter for [pattern] ("EEE d MMM", "h:mm a") in the app's language. Cached; read it each time, never keep it. */
    fun fmt(pattern: String): DateTimeFormatter = AppLocale.locale.let { l -> cache.getOrPut(pattern to l) { DateTimeFormatter.ofPattern(local(pattern, l), l) } }

    /**
     * Arabic keeps "10:00 ص" on one line (a lone ص or م at the start of the next line reads as a stray letter) and uses the
     * Arabic comma: "الثلاثاء 27 أكتوبر، 9:20 م".
     */
    private fun local(pattern: String, l: Locale) = if (l.language == "ar") pattern.replace(" a", "\u00A0a").replace(",", "،") else pattern

    val clock: DateTimeFormatter get() = fmt("h:mm a")

    fun clock(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(ms).atZone(zone).format(clock)
    fun clock(t: LocalTime): String = t.format(clock)
    fun clock(minuteOfDay: Int): String = LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60).format(clock)

    /** "9 AM", for hour labels. */
    fun hour(h: Int): String = LocalTime.of(((h % 24) + 24) % 24, 0).format(fmt("h a"))

    /** Date and time for a note's timestamp: "Mon 19 Oct, 3:30 PM". */
    fun stamp(ms: Long): String = AppLocale.locale.let { l -> java.text.SimpleDateFormat(local("EEE d MMM, h:mm a", l), l).format(java.util.Date(ms)) }

    /** Turns an old "15:30" string (saved by an earlier version) into "3:30 PM". Anything else is returned as it is. */
    fun legacy(s: String): String {
        val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(s.trim()) ?: return s
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) clock(h * 60 + min) else s
    }
}
