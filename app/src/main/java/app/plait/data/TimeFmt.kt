package app.plait.data

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Every time shown in Plait is 12-hour ("3:30 PM"). One place, so no screen can drift back to 24-hour. */
object TimeFmt {
    val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val hourOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("h a", Locale.ENGLISH)

    fun clock(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(ms).atZone(zone).format(clock)
    fun clock(t: LocalTime): String = t.format(clock)
    fun clock(minuteOfDay: Int): String = LocalTime.of((minuteOfDay / 60) % 24, minuteOfDay % 60).format(clock)

    /** "9 AM", for hour labels. */
    fun hour(h: Int): String = LocalTime.of(((h % 24) + 24) % 24, 0).format(hourOnly)

    /** Date and time for a note's timestamp: "Mon 19 Oct, 3:30 PM". */
    fun stamp(ms: Long): String = java.text.SimpleDateFormat("EEE d MMM, h:mm a", Locale.ENGLISH).format(java.util.Date(ms))

    /** Turns an old "15:30" string (saved by an earlier version) into "3:30 PM". Anything else is returned as it is. */
    fun legacy(s: String): String {
        val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(s.trim()) ?: return s
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h in 0..23 && min in 0..59) clock(h * 60 + min) else s
    }
}
