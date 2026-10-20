package app.plait.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.plait.R
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
        .putExtra("id", e.id).putExtra("event", e.eventId).putExtra("text", e.text).putExtra("title", e.title)

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
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pi)
    }

    /** After a restart: every reminder still to come is set again; ones missed while the phone was off fire now. */
    fun scheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        entries(context).forEach { set(context, if (it.at < now) it.copy(at = now + 60_000L) else it) }
    }

    internal fun fire(context: Context, i: Intent) {
        val e = Entry(i.getStringExtra("id") ?: return, i.getStringExtra("event").orEmpty(), i.getStringExtra("text").orEmpty(), i.getStringExtra("title").orEmpty(), 0)
        Reminders.ensureChannel(context)
        val n = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_plait)
            .setContentTitle(e.text)
            .setContentText(if (e.title.isNotBlank()) "Follow-up from ${e.title}" else "Follow-up")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(Reminders.open(context, e.eventId.ifBlank { null }, 2, code(e.id) + 3))
            .addAction(0, "Done", pending(context, ACTION_DONE, e, 1))
            .addAction(0, "Tomorrow", pending(context, ACTION_LATER, e, 2))
            .build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(code(e.id), n) }
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
        val day = when (d) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        }
        return "$day ${TimeFmt.clock(at, zone)}"
    }
}
