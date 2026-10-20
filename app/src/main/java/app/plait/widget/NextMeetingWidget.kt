package app.plait.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import app.plait.R
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.NextUp
import app.plait.data.TimeFmt
import app.plait.data.google.CalEventJson
import app.plait.reminder.Reminders
import java.time.Instant
import java.time.ZoneId

/**
 * Home-screen widget: the day brief, like the morning card. Four sizes show the same day in different depth:
 * 2×1 the count and the next meeting, 2×2 a big count and the next meeting, 4×2 the headline and the next meeting with a
 * countdown and Join, 4×4 all of that plus the rest of the day. The launcher picks the one that fits as it is resized.
 */
class NextMeetingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread { try { ids.forEach { update(context, manager, it) } } finally { pending.finish() } }.start()
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val pending = goAsync()
        Thread { try { update(context, manager, id) } finally { pending.finish() } }.start()
    }

    private enum class Size(val layout: Int) {
        Tiny(R.layout.widget_day_tiny), Square(R.layout.widget_day_square), Wide(R.layout.widget_day_wide), Large(R.layout.widget_day_large)
    }

    private class Day(val today: List<CalEvent>, val next: CalEvent?, val tomorrow: List<CalEvent>, val now: Long)

    companion object {
        private const val SNAPSHOT = "widget_snapshot"

        /** The app writes the days it is showing, so the widget has the same meetings even for calendars it cannot read itself. */
        fun saveSnapshot(context: Context, events: List<CalEvent>) {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val keep = events.filter { !it.allDay && it.startMs < start + 2 * 86_400_000L && it.endMs > start }
            context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit().putString(SNAPSHOT, CalEventJson.encode(keep)).apply()
        }

        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            m.getAppWidgetIds(ComponentName(context, NextMeetingWidget::class.java)).forEach { update(context, m, it) }
        }

        private fun update(context: Context, m: AppWidgetManager, id: Int) {
            val day = runCatching { load(context) }.getOrElse { Day(emptyList(), null, emptyList(), System.currentTimeMillis()) }
            val views = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    RemoteViews(
                        mapOf(
                            SizeF(100f, 40f) to build(context, Size.Tiny, day),
                            SizeF(100f, 100f) to build(context, Size.Square, day),
                            SizeF(220f, 100f) to build(context, Size.Wide, day),
                            SizeF(220f, 230f) to build(context, Size.Large, day),
                        ),
                    )
                } else {
                    val o = m.getAppWidgetOptions(id)
                    val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                    val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 120)
                    build(context, when { w < 200 && h < 100 -> Size.Tiny; w < 200 -> Size.Square; h < 220 -> Size.Wide; else -> Size.Large }, day)
                }
            }.getOrElse { build(context, Size.Wide, day) }
            m.updateAppWidget(id, views)
        }

        /** Today and tomorrow from the phone's calendars, Plait's own copies of Google and subscribed calendars, and the app's snapshot. */
        private fun load(context: Context): Day {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val start = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val end = start + 2 * 86_400_000L
            runCatching { Calendars.load(context) }
            val cached = runCatching { Calendars.events.filter { !it.allDay && it.startMs < end && it.endMs > start } }.getOrDefault(emptyList())
            val snapshot = runCatching {
                context.getSharedPreferences("plait", Context.MODE_PRIVATE).getString(SNAPSHOT, null)?.let { CalEventJson.decode(it) }.orEmpty()
            }.getOrDefault(emptyList()).filter { it.startMs < end && it.endMs > start }
            val device = runCatching { NextUp.events(context, start, end) }.getOrDefault(emptyList())
            val all = (device + cached + snapshot).distinctBy { it.id }.sortedBy { it.startMs }
            val today = all.filter { it.startMs < start + 86_400_000L }
            return Day(today, all.filter { it.endMs > now }.minByOrNull { it.startMs }, all.filter { it.startMs >= start + 86_400_000L }, now)
        }

        private fun build(context: Context, size: Size, d: Day): RemoteViews {
            val v = RemoteViews(context.packageName, size.layout)
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9100))
            val n = d.today.size
            val left = d.today.filter { it.endMs > d.now }
            val ev = d.next
            val name = runCatching { app.plait.data.Profile.firstName() }.getOrDefault("").let { if (it.isBlank()) "" else ", $it" }
            val date = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date(d.now))
            val whenText = ev?.let { (if (it.startMs <= d.now) "Now · " else if (it.startMs >= d.now + 86_400_000L - 1) "Tomorrow " else "") + TimeFmt.clock(it.startMs) + " – " + TimeFmt.clock(it.endMs) }
                ?: "Nothing else coming up"

            when (size) {
                Size.Tiny -> {
                    v.setTextViewText(R.id.widget_count, n.toString())
                    v.setTextViewText(R.id.widget_title, ev?.title ?: if (n == 0) "A clear day" else "That's the day")
                    v.setTextViewText(R.id.widget_when, if (ev != null) whenText else "$n meeting${if (n == 1) "" else "s"} today")
                }
                Size.Square -> {
                    v.setTextViewText(R.id.widget_greeting, date.uppercase())
                    v.setTextViewText(R.id.widget_count, n.toString())
                    v.setTextViewText(R.id.widget_count_label, if (n == 1) "meeting today" else "meetings today")
                    v.setTextViewText(R.id.widget_title, ev?.title ?: "Nothing else today")
                    v.setTextViewText(R.id.widget_when, if (ev != null) whenText else "")
                }
                else -> {
                    v.setTextViewText(R.id.widget_greeting, "Your day$name · $date")
                    v.setTextViewText(R.id.widget_headline, if (n == 0) "A clear day." else "$n meeting${if (n == 1) "" else "s"} today.")
                    v.setTextViewText(
                        R.id.widget_summary,
                        when {
                            n == 0 && d.tomorrow.isNotEmpty() -> "Tomorrow starts at ${TimeFmt.clock(d.tomorrow.first().startMs)}."
                            n == 0 -> "Nothing on the calendar."
                            left.isEmpty() -> "All done. The last one ended at ${TimeFmt.clock(d.today.last().endMs)}."
                            else -> "${n - left.size} done, ${left.size} to go. Free from ${TimeFmt.clock(left.last().endMs)}."
                        },
                    )
                    v.setTextViewText(R.id.widget_next_label, if (ev != null && ev.startMs <= d.now) "HAPPENING NOW" else "NEXT")
                    v.setTextViewText(R.id.widget_title, ev?.title ?: "Nothing coming up")
                    v.setTextViewText(R.id.widget_when, whenText)
                    if (ev == null) {
                        v.setViewVisibility(R.id.widget_countdown, View.GONE)
                        v.setViewVisibility(R.id.widget_join, View.GONE)
                    } else {
                        val live = ev.startMs <= d.now
                        v.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
                        v.setChronometerCountDown(R.id.widget_countdown, true)
                        v.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + ((if (live) ev.endMs else ev.startMs) - d.now), if (live) "ends in %s" else "starts in %s", true)
                        v.setOnClickPendingIntent(R.id.widget_next, Reminders.open(context, ev.id, 0, 9102))
                        val link = ev.callLink
                        if (link != null) {
                            v.setViewVisibility(R.id.widget_join, View.VISIBLE)
                            v.setOnClickPendingIntent(
                                R.id.widget_join,
                                PendingIntent.getActivity(
                                    context, 9101, Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                                ),
                            )
                        } else v.setViewVisibility(R.id.widget_join, View.GONE)
                    }
                    if (size == Size.Large) {
                        val rows = listOf(
                            Triple(R.id.widget_row1, R.id.widget_row1_time, R.id.widget_row1_title),
                            Triple(R.id.widget_row2, R.id.widget_row2_time, R.id.widget_row2_title),
                            Triple(R.id.widget_row3, R.id.widget_row3_time, R.id.widget_row3_title),
                            Triple(R.id.widget_row4, R.id.widget_row4_time, R.id.widget_row4_title),
                            Triple(R.id.widget_row5, R.id.widget_row5_time, R.id.widget_row5_title),
                        )
                        // The whole day in order (done ones marked), apart from the one shown above.
                        val rest = d.today.filter { it.id != ev?.id }.ifEmpty { d.tomorrow.filter { it.id != ev?.id } }
                        rows.forEachIndexed { i, (row, time, title) ->
                            val e = rest.getOrNull(i)
                            if (e == null) v.setViewVisibility(row, View.GONE) else {
                                v.setViewVisibility(row, View.VISIBLE)
                                v.setTextViewText(time, TimeFmt.clock(e.startMs))
                                v.setTextViewText(title, (if (e.endMs <= d.now) "✓ " else "") + e.title)
                                v.setOnClickPendingIntent(row, Reminders.open(context, e.id, 0, 9110 + i))
                            }
                        }
                    }
                }
            }
            return v
        }
    }
}
