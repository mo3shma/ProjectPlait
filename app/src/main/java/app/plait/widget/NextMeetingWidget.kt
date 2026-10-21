package app.plait.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.plait.R
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.NextUp
import app.plait.data.google.CalEventJson
import app.plait.reminder.Reminders
import java.time.Instant
import java.time.ZoneId

/**
 * Home-screen widget, 4x2 (this class) and 2x2 ([NextMeetingWidgetSmall]): the next meeting, no countdown (see [WidgetModel]).
 * Both sizes share one alarm and one update path: [updateAll] refreshes every widget of either class.
 * Refresh triggers, and nothing else wakes it:
 *  - the app saves a snapshot or reschedules reminders (calendar change, app left, boot via BootReceiver -> Reminders.scheduleAll)
 *  - one alarm at the next change (meeting start or end, or midnight), re-armed on every update, cancelled with the last widget
 *  - the system's updatePeriodMillis, 30 minutes, as a safety net
 */
class NextMeetingWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TICK) {
            val pending = goAsync()
            Thread { try { updateAll(context) } finally { pending.finish() } }.start()
        } else super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread { try { updateAll(context) } finally { pending.finish() } }.start()
    }

    // onDisabled fires when the last widget of THIS class goes; the other size may remain, so re-check both.
    override fun onDisabled(context: Context) = cancelIfNone(context)

    companion object {
        private const val SNAPSHOT = "widget_snapshot"
        private const val TICK = "app.plait.WIDGET_TICK"

        /** The app writes the days it is showing, so the widget has the same meetings even for calendars it cannot read itself. */
        fun saveSnapshot(context: Context, events: List<CalEvent>) {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val keep = events.filter { !it.allDay && it.startMs < start + 2 * 86_400_000L && it.endMs > start }
            context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit().putString(SNAPSHOT, CalEventJson.encode(keep)).apply()
        }

        /** The alarm outlives a removed widget only while a widget of the other size remains. */
        internal fun cancelIfNone(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            if (m.getAppWidgetIds(ComponentName(context, NextMeetingWidget::class.java)).isEmpty() &&
                m.getAppWidgetIds(ComponentName(context, NextMeetingWidgetSmall::class.java)).isEmpty()
            ) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tick(context))
        }

        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, NextMeetingWidget::class.java))
            val smallIds = m.getAppWidgetIds(ComponentName(context, NextMeetingWidgetSmall::class.java))
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (ids.isEmpty() && smallIds.isEmpty()) { am.cancel(tick(context)); return }
            val now = System.currentTimeMillis()
            val model = runCatching { WidgetModel.build(load(context, now), now) }.getOrElse { WidgetModel.build(emptyList(), now) }
            if (ids.isNotEmpty()) { val views = build(context, model); ids.forEach { m.updateAppWidget(it, views) } }
            if (smallIds.isNotEmpty()) { val views = buildSmall(context, model); smallIds.forEach { m.updateAppWidget(it, views) } }
            // Inexact on purpose: a few minutes of slack costs nothing here and saves exact-alarm wakeups.
            am.setAndAllowWhileIdle(AlarmManager.RTC, model.nextChangeMs + 1_000L, tick(context))
        }

        private fun tick(context: Context): PendingIntent =
            PendingIntent.getBroadcast(context, 9199, Intent(context, NextMeetingWidget::class.java).setAction(TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        /** The next week from the phone's calendars, Plait's own copies of Google and subscribed calendars, and the app's snapshot. */
        private fun load(context: Context, now: Long): List<CalEvent> {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val end = start + 7 * 86_400_000L
            runCatching { Calendars.load(context) }
            val cached = runCatching { Calendars.events.filter { !it.allDay && it.startMs < end && it.endMs > start } }.getOrDefault(emptyList())
            val snapshot = runCatching {
                context.getSharedPreferences("plait", Context.MODE_PRIVATE).getString(SNAPSHOT, null)?.let { CalEventJson.decode(it) }.orEmpty()
            }.getOrDefault(emptyList()).filter { it.startMs < end && it.endMs > start }
            val device = runCatching { NextUp.events(context, start, end) }.getOrDefault(emptyList())
            return (device + cached + snapshot).distinctBy { it.id }.sortedBy { it.startMs }
        }

        private fun build(context: Context, m: WidgetModel): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_day_wide)
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9100))
            v.setTextViewText(R.id.widget_date, m.date)
            v.setTextViewText(R.id.widget_title, m.title)
            v.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, if (m.clearDay) 24f else 18f)
            v.setInt(R.id.widget_title, "setMaxLines", m.titleLines)
            v.setTextViewText(R.id.widget_when, m.whenLine)
            v.setViewVisibility(R.id.widget_place, if (m.place == null) View.GONE else View.VISIBLE)
            m.place?.let { v.setTextViewText(R.id.widget_place, it) }
            v.setViewVisibility(R.id.widget_footer, if (m.footer == null) View.GONE else View.VISIBLE)
            m.footer?.let { v.setTextViewText(R.id.widget_footer, it) }
            m.eventId?.let { v.setOnClickPendingIntent(R.id.widget_next, Reminders.open(context, it, 0, 9102)) }
            bindJoin(context, v, m.joinUrl)
            return v
        }

        private fun bindJoin(context: Context, v: RemoteViews, url: String?) {
            if (url == null) { v.setViewVisibility(R.id.widget_join, View.GONE); return }
            v.setViewVisibility(R.id.widget_join, View.VISIBLE)
            v.setOnClickPendingIntent(
                R.id.widget_join,
                PendingIntent.getActivity(
                    context, 9101, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }

        private fun buildSmall(context: Context, m: WidgetModel): RemoteViews {
            val l = WidgetModel.smallLines(m)
            val v = RemoteViews(context.packageName, R.layout.widget_day_small)
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9100))
            v.setTextViewText(R.id.widget_date, m.dateShort)
            v.setViewVisibility(R.id.widget_top_end, if (l.topEnd == null) View.GONE else View.VISIBLE)
            l.topEnd?.let { v.setTextViewText(R.id.widget_top_end, it) }
            v.setTextViewText(R.id.widget_title, l.title)
            v.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, if (m.clearDay) 18f else 16f)
            v.setInt(R.id.widget_title, "setMaxLines", l.titleLines)
            v.setTextViewText(R.id.widget_when, l.timeLine)
            v.setTextViewTextSize(R.id.widget_when, TypedValue.COMPLEX_UNIT_SP, if (m.clearDay) 11f else 14f)
            v.setInt(R.id.widget_when, "setMaxLines", if (m.clearDay) 2 else 1)
            v.setViewVisibility(R.id.widget_footer, if (l.bottomText == null) View.GONE else View.VISIBLE)
            l.bottomText?.let { v.setTextViewText(R.id.widget_footer, it) }
            m.eventId?.let { v.setOnClickPendingIntent(R.id.widget_next, Reminders.open(context, it, 0, 9102)) }
            bindJoin(context, v, if (l.showJoin) m.joinUrl else null)
            return v
        }
    }
}
