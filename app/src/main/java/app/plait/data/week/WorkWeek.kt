package app.plait.data.week

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Five work days in a row from [startDay]; the two after are the weekend. */
object WorkWeek {
    const val WORK_DAYS = 5
    var startDay: DayOfWeek by mutableStateOf(WeekFields.of(Locale.getDefault()).firstDayOfWeek); private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        context.getSharedPreferences("plait", Context.MODE_PRIVATE).getString("week_start", null)
            ?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }?.let { startDay = it }
    }

    fun set(context: Context, day: DayOfWeek) {
        startDay = day
        context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit().putString("week_start", day.name).apply()
    }

    fun dayIndex(date: LocalDate, start: DayOfWeek = startDay): Int = Math.floorMod(date.dayOfWeek.value - start.value, 7)
    fun isWorkDay(date: LocalDate, start: DayOfWeek = startDay) = dayIndex(date, start) < WORK_DAYS
    fun isLastWorkDay(date: LocalDate, start: DayOfWeek = startDay) = dayIndex(date, start) == WORK_DAYS - 1
    fun weekStart(date: LocalDate, start: DayOfWeek = startDay): LocalDate = date.minusDays(dayIndex(date, start).toLong())
    fun lastWorkDay(date: LocalDate, start: DayOfWeek = startDay): LocalDate = weekStart(date, start).plusDays(WORK_DAYS - 1L)
    fun thisWorkWeek(date: LocalDate, start: DayOfWeek = startDay): List<LocalDate> = weekStart(date, start).let { s -> List(WORK_DAYS) { s.plusDays(it.toLong()) } }
    fun nextWorkWeek(date: LocalDate, start: DayOfWeek = startDay): List<LocalDate> = thisWorkWeek(weekStart(date, start).plusWeeks(1), start)

    fun label(start: DayOfWeek = startDay): String {
        fun name(d: DayOfWeek, style: TextStyle) = d.getDisplayName(style, Locale.ENGLISH)
        return "Starts ${name(start, TextStyle.FULL)}, works ${name(start, TextStyle.SHORT)} to ${name(start.plus(WORK_DAYS - 1L), TextStyle.SHORT)}"
    }
}
