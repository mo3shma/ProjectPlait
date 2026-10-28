package app.plait.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.plait.MainActivity
import app.plait.R
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.NextUp
import app.plait.data.tasks.AttentionTasks
import app.plait.data.tasks.AttentionItem
import app.plait.data.week.WorkWeek
import app.plait.i18n.Bidi
import app.plait.data.google.CalEventJson
import app.plait.i18n.AppLocale
import app.plait.i18n.Txt
import app.plait.reminder.Reminders
import app.plait.ui.WidgetLaunch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Home-screen widgets, one job each: 2x2 next meeting and Join ([NextMeetingWidgetSmall]), 4x2 today at a glance (this class), 4x3 quick
 * capture ([NextMeetingWidgetQuick]), tasks ([TasksWidget]) and month ([MonthWidget]). No countdowns (see [WidgetModel]). Look and options
 * (opacity, theme, what shows) come from [WidgetPrefs]. All five share one alarm and one update path: [updateAll] refreshes every widget of any class.
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
        val providers: List<Class<*>> = listOf(NextMeetingWidget::class.java, NextMeetingWidgetSmall::class.java, NextMeetingWidgetQuick::class.java, TasksWidget::class.java, MonthWidget::class.java)

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
            WidgetPrefs.load(context)
            val (wide, small, quick, tasks, month) = providers.map { ids(context, m, it) }
            context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit()
                .putLong(LAST_RUN, System.currentTimeMillis()).putString(LAST_IDS, idsKey(context, m)).apply()
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (listOf(wide, small, quick, tasks, month).all { it.isEmpty() }) { am.cancel(tick(context)); return }
            val now = System.currentTimeMillis()
            val model = runCatching { WidgetModel.build(load(context, now), now) }.getOrElse { WidgetModel.build(emptyList(), now) }
            if (wide.isNotEmpty()) { val views = build(context, model); wide.forEach { m.updateAppWidget(it, views) } }
            if (small.isNotEmpty()) { val views = buildSmall(context, model); small.forEach { m.updateAppWidget(it, views) } }
            if (quick.isNotEmpty()) { val views = buildQuick(context, model); quick.forEach { m.updateAppWidget(it, views) } }
            tasks.forEach { m.updateAppWidget(it, buildTasks(context, taskRows(m.getAppWidgetOptions(it)))) }
            if (month.isNotEmpty()) { val views = buildMonth(context, now); month.forEach { m.updateAppWidget(it, views) } }
            // Inexact on purpose: a few minutes of slack costs nothing here and saves exact-alarm wakeups.
            am.setAndAllowWhileIdle(AlarmManager.RTC, model.nextChangeMs + 1_000L, tick(context))
        }

        private fun tick(context: Context): PendingIntent =
            PendingIntent.getBroadcast(context, 9199, Intent(context, NextMeetingWidget::class.java).setAction(TICK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        /** The next week from the phone's calendars, Plait's own copies of Google and subscribed calendars, and the app's snapshot. */
        private fun load(context: Context, now: Long): List<CalEvent> {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            return load(context, start, start + 7 * 86_400_000L)
        }

        private fun load(context: Context, start: Long, end: Long, allDay: Boolean = false): List<CalEvent> {
            runCatching { Calendars.load(context) }
            val cached = runCatching { Calendars.events.filter { (allDay || !it.allDay) && it.startMs < end && it.endMs > start } }.getOrDefault(emptyList())
            val snapshot = runCatching {
                context.getSharedPreferences("plait", Context.MODE_PRIVATE).getString(SNAPSHOT, null)?.let { CalEventJson.decode(it) }.orEmpty()
            }.getOrDefault(emptyList()).filter { it.startMs < end && it.endMs > start }
            val device = runCatching { NextUp.events(context, start, end) }.getOrDefault(emptyList())
            return (device.filter { allDay || !it.allDay } + cached + snapshot).distinctBy { it.id }.sortedBy { it.startMs }
        }

        private val ALPHA = mapOf(WidgetOpacity.Clear to 0x8C, WidgetOpacity.Soft to 0xB3, WidgetOpacity.Solid to 0xF2)

        /**
         * One look for every widget: the panel from [WidgetPrefs] (Plait glass, or the phone's Material You neutral on Android 12 and up).
         * Day or night is not decided here: the backgrounds, capsules and text inks are night-qualified resources the launcher resolves
         * when it inflates, so they follow the phone's mode on their own. Only the Wallpaper tint is built in code, with both variants (API 31+).
         */
        private fun skin(context: Context, v: RemoteViews) {
            val alpha = ALPHA.getValue(WidgetPrefs.opacity)
            if (WidgetPrefs.theme == WidgetTheme.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                fun tint(res: Int) = ColorStateList.valueOf((context.getColor(res) and 0xFFFFFF) or (alpha shl 24))
                v.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_bg_surface)
                v.setColorStateList(R.id.widget_root, "setBackgroundTintList", tint(android.R.color.system_neutral1_50), tint(android.R.color.system_neutral1_900))
            } else {
                v.setInt(
                    R.id.widget_root, "setBackgroundResource",
                    when (WidgetPrefs.opacity) {
                        WidgetOpacity.Clear -> R.drawable.widget_bg_clear
                        WidgetOpacity.Soft -> R.drawable.widget_bg_soft
                        WidgetOpacity.Solid -> R.drawable.widget_bg_solid
                    },
                )
            }
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

        /**
         * The launcher inflates widgets in the phone's language and direction, which can differ from the app's: set the
         * direction and the fixed labels ([labels]: view id to string id) from the app's language.
         */
        private fun localize(v: RemoteViews, vararg labels: Pair<Int, Int>) {
            v.setInt(R.id.widget_root, "setLayoutDirection", if (AppLocale.isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
            labels.forEach { (view, text) -> v.setTextViewText(view, Txt.s(text)) }
        }

        /** The source-coloured dot: the model's accent as a colour filter on the white dot drawable. */
        private fun dot(v: RemoteViews, m: WidgetModel, on: Boolean = !m.clearDay) {
            show(v, R.id.widget_dot, on)
            v.setInt(R.id.widget_dot, "setColorFilter", m.accent)
        }

        private fun build(context: Context, m: WidgetModel): RemoteViews {
            val l = WidgetModel.wideLines(m)
            val v = RemoteViews(context.packageName, R.layout.widget_day_wide)
            localize(v, R.id.widget_now to R.string.common_now, R.id.widget_join_label to R.string.common_join)
            skin(context, v)
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
            show(v, R.id.widget_strip, !m.clearDay && WidgetPrefs.showLater)
            show(v, R.id.widget_then, l.then != null)
            l.then?.let { v.setTextViewText(R.id.widget_then, it) }
            show(v, R.id.widget_more, l.more != null)
            l.more?.let { v.setTextViewText(R.id.widget_more, it) }
            show(v, R.id.widget_last, l.lastText != null)
            l.lastText?.let { v.setTextViewText(R.id.widget_last, it) }
            m.eventId?.let { v.setOnClickPendingIntent(R.id.widget_next, Reminders.open(context, it, 0, 9102)) }
            bindJoin(context, v, m, if (l.showJoin && WidgetPrefs.showJoin) m.joinUrl else null)
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
            localize(v, R.id.widget_join_label to R.string.common_join)
            skin(context, v)
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
            bindJoin(context, v, m, if (l.showJoin && WidgetPrefs.showJoin) m.joinUrl else null)
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
            localize(
                v, R.id.widget_join_label to R.string.common_join, R.id.widget_tile_voice_label to R.string.common_voice_note,
                R.id.widget_tile_list_label to R.string.widget_tile_task_list, R.id.widget_tile_text_label to R.string.widget_tile_text_note,
            )
            skin(context, v)
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9100))
            dot(v, m)
            show(v, R.id.widget_time, l.top != null)
            l.top?.let { v.setTextViewText(R.id.widget_time, it) }
            v.setTextViewText(R.id.widget_title, l.title)
            m.eventId?.let { v.setOnClickPendingIntent(R.id.widget_next, Reminders.open(context, it, 0, 9102)) }
            bindJoin(context, v, m, if (l.showJoin && WidgetPrefs.showJoin) m.joinUrl else null)
            v.setOnClickPendingIntent(R.id.widget_tile_voice, tile(context, "voice", 9111))
            v.setOnClickPendingIntent(R.id.widget_tile_list, tile(context, "list", 9112))
            v.setOnClickPendingIntent(R.id.widget_tile_text, tile(context, "text", 9113))
            return v
        }

        private val TASK_ROWS = intArrayOf(R.id.widget_task_1, R.id.widget_task_2, R.id.widget_task_3, R.id.widget_task_4, R.id.widget_task_5)

        /** Rows that fit the widget's current height: 48dp of header and padding, 26dp a row. */
        private fun taskRows(options: android.os.Bundle): Int =
            ((options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110) - 48) / 26).coerceIn(1, TASK_ROWS.size)

        private fun buildTasks(context: Context, rows: Int): RemoteViews {
            val now = System.currentTimeMillis()
            val open = runCatching { AttentionTasks.now(context, now) }.getOrDefault(emptyList()).filter { it.kind == AttentionItem.Kind.Open }
            val v = RemoteViews(context.packageName, R.layout.widget_tasks)
            localize(v, R.id.widget_date to R.string.widget_tasks_title)
            skin(context, v)
            show(v, R.id.widget_count, open.isNotEmpty())
            v.setTextViewText(R.id.widget_count, open.size.toString())
            val hidden = open.size - rows
            TASK_ROWS.forEachIndexed { i, id ->
                v.setViewVisibility(id, if (i >= rows) View.GONE else if (i < open.size || i == 0) View.VISIBLE else View.INVISIBLE)
                val text = when {
                    open.isEmpty() -> if (i == 0) Txt.s(R.string.widget_tasks_empty) else ""
                    hidden > 0 && i == rows - 1 -> Txt.plural(R.plurals.widget_tasks_more, hidden + 1)
                    // Rows past the last task still get text (they are INVISIBLE), so index safely: open[i] threw when rows > open.size.
                    else -> open.getOrNull(i)?.let { t -> "○  " + Bidi.isolate(t.text) + (if (t.urgent && t.dueMs != null) " · " + Bidi.isolate(AttentionTasks.dueLabel(t.dueMs, now)) else "") }.orEmpty()
                }
                v.setTextViewText(id, text)
            }
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9120))
            v.setOnClickPendingIntent(R.id.widget_add, tile(context, "list", 9121))
            return v
        }

        /** Days from [from] up to (not including) [to] that have at least one event. */
        private fun busyDays(context: Context, from: LocalDate, to: LocalDate, zone: ZoneId): Set<LocalDate> {
            fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
            return runCatching { load(context, ms(from), ms(to), allDay = true) }.getOrDefault(emptyList()).flatMap { e ->
                generateSequence(Instant.ofEpochMilli(e.startMs).atZone(zone).toLocalDate()) { it.plusDays(1) }
                    .takeWhile { it < to && ms(it) < maxOf(e.endMs, e.startMs + 1) }.filter { it >= from }.toList()
            }.toSet()
        }

        /** The month of today in whole weeks from the week's first day, today ringed, a dot under days with events (if on). */
        private fun buildMonth(context: Context, now: Long): RemoteViews {
            val zone = ZoneId.systemDefault()
            WorkWeek.load(context)
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val first = today.withDayOfMonth(1)
            val start = WorkWeek.weekStart(first)
            val weeks = (WorkWeek.dayIndex(first) + first.lengthOfMonth() + 6) / 7
            val busy = if (WidgetPrefs.showDots) busyDays(context, start, start.plusWeeks(weeks.toLong()), zone) else emptySet()
            val v = RemoteViews(context.packageName, R.layout.widget_month)
            localize(v)
            skin(context, v)
            v.setOnClickPendingIntent(R.id.widget_root, Reminders.open(context, null, 0, 9130))
            val locale = AppLocale.locale
            v.setTextViewText(R.id.widget_date, java.text.SimpleDateFormat("LLLL yyyy", locale).format(java.util.Date(now)))
            val nf = java.text.NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
            fun cell(text: String, softInk: Boolean, dot: Boolean, ring: Boolean) = RemoteViews(context.packageName, if (softInk) R.layout.widget_month_cell_soft else R.layout.widget_month_cell).also { c ->
                c.setTextViewText(R.id.widget_cell_day, text)
                if (ring) c.setTextColor(R.id.widget_cell_day, 0xFFFFFFFF.toInt())
                if (ring) c.setInt(R.id.widget_cell_day, "setBackgroundResource", R.drawable.widget_today)
                c.setViewVisibility(R.id.widget_cell_dot, if (dot) View.VISIBLE else View.INVISIBLE)
            }
            v.removeAllViews(R.id.widget_grid)
            RemoteViews(context.packageName, R.layout.widget_month_week).also { row ->
                for (i in 0 until 7) row.addView(R.id.widget_week, cell(start.plusDays(i.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale), true, false, false))
                v.addView(R.id.widget_grid, row)
            }
            for (w in 0 until weeks) RemoteViews(context.packageName, R.layout.widget_month_week).also { row ->
                for (i in 0 until 7) {
                    val d = start.plusDays((w * 7 + i).toLong())
                    row.addView(R.id.widget_week, cell(nf.format(d.dayOfMonth), d.month != today.month, d in busy, d == today))
                }
                v.addView(R.id.widget_grid, row)
            }
            return v
        }
    }
}
