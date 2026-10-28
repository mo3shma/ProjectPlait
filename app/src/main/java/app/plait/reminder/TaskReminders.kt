package app.plait.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.plait.R
import app.plait.i18n.Txt
import app.plait.data.TimeFmt
import app.plait.data.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reminders for tasks added from suggestions. Each fires once with the task, the meeting it came from, and Done and
 * Tomorrow buttons. They are kept on the phone and set again after a restart, like the meeting alerts.
 */
object TaskReminders {
    const val ACTION_FIRE = "app.plait.TASK_REMINDER"
    const val ACTION_DONE = "app.plait.TASK_DONE"
    const val ACTION_LATER = "app.plait.TASK_LATER"
    const val ACTION_LIVE = "app.plait.TASK_LIVE"

    private data class Entry(val id: String, val eventId: String, val text: String, val title: String, val at: Long)

    private fun prefs(c: Context) = c.getSharedPreferences("plait_tasks", Context.MODE_PRIVATE)

    @Synchronized private fun entries(c: Context): List<Entry> = runCatching {
        val arr = JSONArray(prefs(c).getString("reminders", "[]"))
        List(arr.length()) { arr.getJSONObject(it) }.map { Entry(it.getString("id"), it.getString("event"), it.getString("text"), it.optString("title"), it.getLong("at")) }
    }.getOrDefault(emptyList())

    @Synchronized private fun save(c: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("event", it.eventId).put("text", it.text).put("title", it.title).put("at", it.at)) }
        prefs(c).edit().putString("reminders", arr.toString()).apply()
    }

    private fun code(id: String) = (id.hashCode() and 0x3FFFFFFF) or 0x40000000

    private fun intent(c: Context, action: String, e: Entry) = Intent(c, ReminderReceiver::class.java).setAction(action)
        .putExtra("id", e.id).putExtra("event", e.eventId).putExtra("text", e.text).putExtra("title", e.title).putExtra("at", e.at)

    private fun entryOf(i: Intent) = Entry(i.getStringExtra("id") ?: "", i.getStringExtra("event").orEmpty(), i.getStringExtra("text").orEmpty(), i.getStringExtra("title").orEmpty(), i.getLongExtra("at", 0L))

    private fun pending(c: Context, action: String, e: Entry, offset: Int = 0) =
        PendingIntent.getBroadcast(c, code(e.id) + offset, intent(c, action, e), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context, id: String, eventId: String, text: String, title: String, at: Long) {
        val e = Entry(id, eventId, text, title, at)
        save(context, entries(context).filter { it.id != id } + e)
        set(context, e)
    }

    private fun set(c: Context, e: Entry) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(c, ACTION_FIRE, e)
        Reminders.setAlarm(am, e.at, pi)
        // The quiet heads-up starts 15 minutes before; if that is already past, it starts now.
        val now = System.currentTimeMillis()
        val pre = e.at - LiveLogic.TASK_LEAD_MS
        if (pre > now) Reminders.setAlarm(am, pre, pending(c, ACTION_LIVE, e, 4)) else if (e.at > now) postLive(c, e)
    }

    /** Drops the reminder for [id] (task ticked off, deleted, or its due date cleared or moved past). */
    fun cancel(context: Context, id: String) {
        val all = entries(context)
        all.firstOrNull { it.id == id }?.let { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).also { am -> am.cancel(pending(context, ACTION_FIRE, it)); am.cancel(pending(context, ACTION_LIVE, it, 4)) } }
        save(context, all.filter { it.id != id })
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(code(id))
    }

    /** After a restart: every reminder still to come is set again; ones missed while the phone was off fire now. */
    fun scheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        entries(context).forEach { set(context, if (it.at < now) it.copy(at = now + 60_000L) else it) }
    }

    internal fun fire(context: Context, i: Intent) {
        val e = entryOf(i)
        if (e.id.isNotEmpty()) postLive(context, e)
    }

    internal fun live(context: Context, i: Intent) = fire(context, i)

    /** The task's alert: a quiet heads-up before, then the due alert at the due time, until Done or Tomorrow. */
    private fun postLive(context: Context, e: Entry) {
        val due = LiveLogic.taskPhase(System.currentTimeMillis(), e.at) == TaskPhase.Due
        Reminders.ensureChannel(context)
        LiveAlert.ensureChannel(context)
        // Quiet beforehand; at the due time it uses the alerting channel so it can make a sound.
        val b = LiveAlert.builder(context, if (due) Reminders.CHANNEL else LiveAlert.CHANNEL, e.text, Reminders.open(context, e.eventId.ifBlank { null }, 2, code(e.id) + 3))
            .setOnlyAlertOnce(!due)
            .setContentText(LiveLogic.taskLine(TimeFmt.clock(e.at), e.title))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, Txt.s(R.string.common_done), pending(context, ACTION_DONE, e, 1))
            .addAction(0, Txt.s(R.string.common_tomorrow), pending(context, ACTION_LATER, e, 2))
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(code(e.id), b.build()) }
    }

    internal fun done(context: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        Tasks.markDone(context, i.getStringExtra("event").orEmpty(), i.getStringExtra("text").orEmpty())
        save(context, entries(context).filter { it.id != id })
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(code(id))
        app.plait.data.sync.SyncEngine.requestAsync(context, force = true)
    }

    internal fun later(context: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val at = app.plait.data.tasks.TaskSuggester.remindAt(app.plait.data.tasks.Due.Tomorrow, System.currentTimeMillis())
        schedule(context, id, i.getStringExtra("event").orEmpty(), i.getStringExtra("text").orEmpty(), i.getStringExtra("title").orEmpty(), at)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(code(id))
    }

    /** "Today 5:00 PM", "Tomorrow 9:00 AM", "Fri 9:00 AM". */
    fun label(at: Long, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val d = java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val clock = TimeFmt.clock(at, zone)
        return when (d) {
            today -> Txt.s(R.string.alert_task_today, clock)
            today.plusDays(1) -> Txt.s(R.string.alert_task_tomorrow, clock)
            else -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, app.plait.i18n.AppLocale.locale) + " " + clock
        }
    }
}
