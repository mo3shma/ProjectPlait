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
import app.plait.MainActivity
import app.plait.R
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.NextUp
import app.plait.data.google.CalEventJson
import app.plait.reminder.Reminders
import app.plait.ui.WidgetLaunch
import java.time.Instant
import java.time.ZoneId

/**
 * Home-screen widget, 4x2 (this class), 2x2 ([NextMeetingWidgetSmall]) and the 4x3 quick add ([NextMeetingWidgetQuick]): the next
 * meeting, no countdown (see [WidgetModel]). All three share one alarm and one update path: [updateAll] refreshes every widget of any class.
 * Refresh triggers, and nothing else wakes it:
 *  - the app saves a snapshot or reschedules reminders (calendar change, app left, boot via BootReceiver -> Reminders.scheduleAll)
 *  - one alarm at the next change (meeting start or end, 12:00 or 17:00 for the tint, or midnight), re-armed on every update, cancelled with the last widget
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
        if (!providerUpdateDue(context)) return
        val pending = goAsync()
        Thread { try { updateAll(context) } finally { pending.finish() } }.start()
    }

    // onDisabled fires when the last widget of THIS class goes; another size may remain, so re-check all.
    override fun onDisabled(context: Context) = cancelIfNone(context)

    companion object {
        private const val SNAPSHOT = "widget_snapshot"
        private const val TICK = "app.plait.WIDGET_TICK"

        /** Every provider class that shares the data and the alarm. */
        val providers: List<Class<*>> = listOf(NextMeetingWidget::class.java, NextMeetingWidgetSmall::class.java, NextMeetingWidgetQuick::class.java)

        /** The app writes the days it is showing, so the widget has the same meetings even for calendars it cannot read itself. */
        fun saveSnapshot(context: Context, events: List<CalEvent>) {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val keep = events.filter { !it.allDay && it.startMs < start + 2 * 86_400_000L && it.endMs > start }
            context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit().putString(SNAPSHOT, CalEventJson.encode(keep)).apply()
        }

        private fun ids(context: Context, m: AppWidgetManager, cls: Class<*>) = m.getAppWidgetIds(ComponentName(context, cls))

        /** The alarm outlives a removed widget only while a widget of another size remains. */
        internal fun cancelIfNone(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            if (providers.all { ids(context, m, it).isEmpty() }) (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(tick(context))
        }

        private const val LAST_RUN = "widget_last_run"
        private const val LAST_IDS = "widget_last_ids"
        private const val MIN_GAP_MS = 120_000L

        /** True when a provider callback may skip: the last full update was under two minutes ago and no widget was added or removed since. */
        internal fun shouldSkip(lastRunMs: Long, nowMs: Long, sameIds: Boolean): Boolean = sameIds && nowMs - lastRunMs in 0 until MIN_GAP_MS

        private fun idsKey(context: Context, m: AppWidgetManager) = providers.flatMap { ids(context, m, it).toList() }.sorted().joinToString(",")

        /** The system's onUpdate: all three receivers fire together and each would rebuild all three widgets, so repeats within two minutes are dropped. */
        internal fun providerUpdateDue(context: Context): Boolean {
            val prefs = context.getSharedPreferences("plait", Context.MODE_PRIVATE)
            val key = idsKey(context, AppWidgetManager.getInstance(context))
            return !shouldSkip(prefs.getLong(LAST_RUN, 0L), System.currentTimeMillis(), prefs.getString(LAST_IDS, null) == key)
        }

        fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val (wide, small, quick) = providers.map { ids(context, m, it) }
            context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit()
                .putLong(LAST_RUN, System.currentTimeMillis()).putString(LAST_IDS, idsKey(context, m)).apply()
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (wide.isEmpty() && small.isEmpty() && quick.isEmpty()) { am.cancel(tick(context)); return }
            val now = System.currentTimeMillis()
            val model = runCatching { WidgetModel.build(load(context, now), now) }.getOrElse { WidgetModel.build(emptyList(), now) }
            if (wide.isNotEmpty()) { val views = build(context, model); wide.forEach { m.updateAppWidget(it, views) } }
            if (small.isNotEmpty()) { val views = buildSmall(context, model); small.forEach { m.updateAppWidget(it, views) } }
            if (quick.isNotEmpty()) { val views = buildQuick(context, model); quick.forEach { m.updateAppWidget(it, views) } }
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

        private fun bg(p: Phase) = when (p) {
            Phase.Morning -> R.drawable.widget_bg_morning
            Phase.Midday -> R.drawable.widget_bg_midday
            Phase.Evening -> R.drawable.widget_bg_evening
        }

        private fun pill(l: JoinLook) = when (l) {
            JoinLook.Teams -> R.drawable.widget_pill_teams
            JoinLook.Meet -> R.drawable.widget_pill_meet
            JoinLook.Zoom -> R.drawable.widget_pill_zoom
            JoinLook.Webex -> R.drawable.widget_pill_webex
            JoinLook.Google -> R.drawable.widget_pill_google
            JoinLook.Outlook -> R.drawable.widget_pill_outlook
            JoinLook.ICloud -> R.drawable.widget_pill_icloud
            JoinLook.Other -> R.drawable.widget_pill_other
        }

        private fun show(v: RemoteViews, id: Int, on: Boolean) = v.setViewVisibility(id, if (on) View.VISIBLE else View.GONE)

        /** The source-coloured dot: the model's accent as a colour filter on the white dot drawable. */
        private fun dot(v: RemoteViews, m: WidgetModel, on: Boolean = !m.clearDay) {
            show(v, R.id.widget_dot, on)
            v.setInt(R.id.widget_dot, "setColorFilter", m.accent)
        }

        private fun build(context: Context, m: WidgetModel): RemoteViews {
            val l = WidgetModel.wideLines(m)
            val v = RemoteViews(context.packageName, R.layout.widget_day_wide)
            v.setInt(R.id.widget_root, "setBackgroundResource", bg(m.phase))
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9100))
            v.setTextViewText(R.id.widget_date, m.date)
            dot(v, m)
            show(v, R.id.widget_count, l.countLabel != null)
            l.countLabel?.let { v.setTextViewText(R.id.widget_count, it) }
            show(v, R.id.widget_time_row, l.timeLine != null)
            show(v, R.id.widget_now, l.nowChip)
            l.timeLine?.let { v.setTextViewText(R.id.widget_time, it) }
            v.setTextViewText(R.id.widget_title, l.title)
            v.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, l.titleSp.toFloat())
            v.setInt(R.id.widget_title, "setMaxLines", l.titleLines)
            show(v, R.id.widget_place, l.sub != null)
            l.sub?.let { v.setTextViewText(R.id.widget_place, it) }
            // Icon only on a real place line: video when the meeting has a call, a pin otherwise.
            v.setTextViewCompoundDrawablesRelative(
                R.id.widget_place, if (m.clearDay) 0 else if (m.joinUrl != null) R.drawable.ic_widget_video_small else R.drawable.ic_widget_place, 0, 0, 0,
            )
            show(v, R.id.widget_strip, !m.clearDay)
            show(v, R.id.widget_then, l.then != null)
            l.then?.let { v.setTextViewText(R.id.widget_then, it) }
            show(v, R.id.widget_more, l.more != null)
            l.more?.let { v.setTextViewText(R.id.widget_more, it) }
            show(v, R.id.widget_last, l.lastText != null)
            l.lastText?.let { v.setTextViewText(R.id.widget_last, it) }
            m.eventId?.let { v.setOnClickPendingIntent(R.id.widget_next, Reminders.open(context, it, 0, 9102)) }
            bindJoin(context, v, m, if (l.showJoin) m.joinUrl else null)
            return v
        }

        private fun bindJoin(context: Context, v: RemoteViews, m: WidgetModel, url: String?) {
            if (url == null) { v.setViewVisibility(R.id.widget_join, View.GONE); return }
            v.setViewVisibility(R.id.widget_join, View.VISIBLE)
            v.setInt(R.id.widget_join, "setBackgroundResource", pill(m.look))
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
            v.setInt(R.id.widget_root, "setBackgroundResource", bg(m.phase))
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9100))
            v.setTextViewText(R.id.widget_date, m.dateShort)
            show(v, R.id.widget_top_end, l.topEnd != null)
            show(v, R.id.widget_mark, l.topEnd == null)
            l.topEnd?.let { v.setTextViewText(R.id.widget_top_end, it) }
            // The dot would crowd "until 11:30 AM"; a running meeting already has its "Now" capsule.
            dot(v, m, !m.clearDay && !m.running)
            show(v, R.id.widget_time_row, !m.clearDay)
            v.setTextViewText(R.id.widget_time, l.timeLine)
            v.setTextViewTextSize(R.id.widget_time, TypedValue.COMPLEX_UNIT_SP, l.timeSp.toFloat())
            v.setTextViewText(R.id.widget_title, l.title)
            v.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, l.titleSp.toFloat())
            v.setInt(R.id.widget_title, "setMaxLines", l.titleLines)
            show(v, R.id.widget_sub, m.clearDay)
            if (m.clearDay) v.setTextViewText(R.id.widget_sub, l.timeLine)
            m.eventId?.let { v.setOnClickPendingIntent(R.id.widget_next, Reminders.open(context, it, 0, 9102)) }
            bindJoin(context, v, m, if (l.showJoin) m.joinUrl else null)
            return v
        }

        private fun tile(context: Context, kind: String, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context, code,
                Intent(context, MainActivity::class.java).setAction(WidgetLaunch.ACTION).putExtra(WidgetLaunch.EXTRA_KIND, kind)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun buildQuick(context: Context, m: WidgetModel): RemoteViews {
            val l = WidgetModel.quickLines(m)
            val v = RemoteViews(context.packageName, R.layout.widget_quick)
            v.setInt(R.id.widget_root, "setBackgroundResource", bg(m.phase))
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9100))
            dot(v, m)
            show(v, R.id.widget_time, l.top != null)
            l.top?.let { v.setTextViewText(R.id.widget_time, it) }
            v.setTextViewText(R.id.widget_title, l.title)
            m.eventId?.let { v.setOnClickPendingIntent(R.id.widget_next, Reminders.open(context, it, 0, 9102)) }
            bindJoin(context, v, m, if (l.showJoin) m.joinUrl else null)
            v.setOnClickPendingIntent(R.id.widget_tile_voice, tile(context, "voice", 9111))
            v.setOnClickPendingIntent(R.id.widget_tile_list, tile(context, "list", 9112))
            v.setOnClickPendingIntent(R.id.widget_tile_text, tile(context, "text", 9113))
            return v
        }
    }
}
