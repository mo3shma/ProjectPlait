package app.plait.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import app.plait.MainActivity
import app.plait.R
import app.plait.data.CallLinks
import app.plait.data.NextUp
import app.plait.data.NoteStore
import app.plait.data.TimeFmt
import app.plait.data.brief.BriefBuilder
import app.plait.data.context.ContextAnalyzer
import app.plait.data.context.ContextStore
import app.plait.data.memo.MemoStore
import app.plait.widget.NextMeetingWidget
import java.time.LocalDate
import java.time.ZoneId

/** The user's choices about alerts. */
object ReminderPrefs {
    private fun p(c: Context) = c.getSharedPreferences("plait", Context.MODE_PRIVATE)
    fun before(c: Context) = p(c).getBoolean("remind_before", true)
    fun leadMin(c: Context) = p(c).getInt("remind_lead", 10)
    fun nudge(c: Context) = p(c).getBoolean("remind_nudge", true)
    fun setBefore(c: Context, v: Boolean) { p(c).edit().putBoolean("remind_before", v).apply(); Reminders.scheduleAsync(c) }
    fun setLead(c: Context, v: Int) { p(c).edit().putInt("remind_lead", v).apply(); Reminders.scheduleAsync(c) }
    fun setNudge(c: Context, v: Boolean) { p(c).edit().putBoolean("remind_nudge", v).apply(); Reminders.scheduleAsync(c) }
}

/**
 * Alerts before meetings (with a one-line reminder of what it is about) and an evening nudge to write up meetings that
 * have no notes. Scheduled with the system alarm clock, so they fire with Plait closed. Everything is worked out on
 * the phone.
 */
object Reminders {
    const val CHANNEL = "meetings"
    const val EXTRA_EVENT = "plait_event_id"
    private const val ACTION_MEETING = "app.plait.MEETING_REMINDER"
    private const val ACTION_NUDGE = "app.plait.NOTE_NUDGE"
    private const val NUDGE_CODE = 7001

    private var lastScheduled = 0L

    /** Re-plans alerts and refreshes the widget. Calendar syncs call this often, so unless [force] it runs at most every 10 minutes. */
    fun scheduleAsync(context: Context, force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && now - lastScheduled < 600_000L) return
        lastScheduled = now
        val app = context.applicationContext
        Thread { runCatching { scheduleAll(app) } }.start()
    }

    fun scheduleAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p = context.getSharedPreferences("plait", Context.MODE_PRIVATE)
        val old = p.getStringSet("reminder_codes", emptySet()).orEmpty()
        old.mapNotNull { it.toIntOrNull() }.forEach { code -> am.cancel(pending(context, code, Intent(context, ReminderReceiver::class.java).setAction(ACTION_MEETING))) }
        val codes = HashSet<String>()
        val now = System.currentTimeMillis()

        if (ReminderPrefs.before(context)) {
            val lead = ReminderPrefs.leadMin(context) * 60_000L
            val notes = NoteStore.get(context).notes.toList()
            val mail = ContextStore.get(context)
            val zone = ZoneId.systemDefault()
            NextUp.events(context, now, now + 36 * 3_600_000L).filter { it.startMs - lead > now }.take(40).forEach { ev ->
                val brief = BriefBuilder.build(ev, notes, now)
                val items = mail.forEvent(ev.id)
                val prepGoal = app.plait.data.prep.PrepStore.get(context).entries[ev.id]?.let { app.plait.data.prep.Prep.parse(it.text).goal }
                val line = prepGoal ?: items.takeIf { it.isNotEmpty() }?.let {
                    val s = ContextAnalyzer.analyze(ev, brief, it, zone)
                    (s.toSettle.firstOrNull() ?: s.highlights.firstOrNull())?.let { h -> "${h.kind.label}: ${h.text}" }
                } ?: (if (!brief.aboutIsGuess) brief.about else listOf(brief.withLine, ev.place).filter { it.isNotBlank() }.joinToString(" · "))
                val code = ev.id.hashCode() and 0x3FFFFFFF
                codes.add(code.toString())
                val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_MEETING)
                    .putExtra(EXTRA_EVENT, ev.id).putExtra("title", ev.title).putExtra("min", (lead / 60_000).toInt())
                    .putExtra("when", TimeFmt.clock(ev.startMs)).putExtra("line", line.take(160)).putExtra("join", ev.callLink?.url)
                setAlarm(am, ev.startMs - lead, pending(context, code, intent))
            }
        }
        p.edit().putStringSet("reminder_codes", codes).apply()

        val nudge = pending(context, NUDGE_CODE, Intent(context, ReminderReceiver::class.java).setAction(ACTION_NUDGE))
        am.cancel(nudge)
        if (ReminderPrefs.nudge(context)) {
            var at = LocalDate.now().atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (at <= now) at += 86_400_000L
            setAlarm(am, at, nudge)
        }
        NextMeetingWidget.updateAll(context)
    }

    private fun pending(context: Context, code: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Meetings and notes", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Heads-up before a meeting, and a reminder to write up meetings without notes."
            })
        }
    }

    internal fun open(context: Context, eventId: String?, tab: Int, code: Int): PendingIntent {
        val i = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (eventId != null) i.putExtra(EXTRA_EVENT, eventId).putExtra("plait_tab", tab)
        return PendingIntent.getActivity(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    internal fun notifyMeeting(context: Context, i: Intent) {
        ensureChannel(context)
        val id = i.getStringExtra(EXTRA_EVENT) ?: return
        val min = i.getIntExtra("min", 10)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_plait)
            .setContentTitle("In $min min: ${i.getStringExtra("title")}")
            .setContentText(listOf(i.getStringExtra("when"), i.getStringExtra("line")).filter { !it.isNullOrBlank() }.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(i.getStringExtra("when"), i.getStringExtra("line")).filter { !it.isNullOrBlank() }.joinToString("\n")))
            .setCategory(NotificationCompat.CATEGORY_EVENT).setAutoCancel(true)
            .setContentIntent(open(context, id, 0, id.hashCode()))
            .also { b ->
                i.getStringExtra("join")?.let { url ->
                    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    val join = NotificationCompat.Action.Builder(
                        0, "Join", PendingIntent.getActivity(context, id.hashCode() + 1, view, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                    ).build()
                    b.addAction(join)
                    // On a paired watch the same alert shows up with a Join button.
                    b.extend(NotificationCompat.WearableExtender().addAction(join))
                }
            }
            .build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id.hashCode(), n) }
    }

    /** At the end of the day: how many finished meetings still have no note. */
    internal fun notifyNudge(context: Context) {
        val now = System.currentTimeMillis()
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val done = NextUp.events(context, dayStart, now).filter { it.endMs <= now }
        val noted = (NoteStore.get(context).notes.map { it.eventId } + MemoStore.get(context).items.map { it.eventId }).toSet()
        val kinds = done.associate { it.id to BriefBuilder.classify(it) }
        val missing = done.filter { it.id !in noted && kinds[it.id] != app.plait.data.brief.MeetingKind.Personal && kinds[it.id] != app.plait.data.brief.MeetingKind.Focus }
        if (missing.isEmpty()) return
        ensureChannel(context)
        val text = if (missing.size == 1) "${missing.first().title} has no notes yet." else "${missing.size} meetings have no notes yet, like ${missing.first().title}."
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_plait).setContentTitle("Add notes while it's fresh").setContentText(text)
            .setAutoCancel(true).setContentIntent(open(context, missing.first().id, 2, NUDGE_CODE + 1)).build()
        runCatching { (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NUDGE_CODE, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    "app.plait.MEETING_REMINDER" -> Reminders.notifyMeeting(context, intent)
                    "app.plait.NOTE_NUDGE" -> { Reminders.notifyNudge(context); Reminders.scheduleAll(context) }
                    TaskReminders.ACTION_FIRE -> TaskReminders.fire(context, intent)
                    TaskReminders.ACTION_DONE -> TaskReminders.done(context, intent)
                    TaskReminders.ACTION_LATER -> TaskReminders.later(context, intent)
                }
            } finally { pending.finish() }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread { try { runCatching { Reminders.scheduleAll(context) }; runCatching { TaskReminders.scheduleAll(context) } } finally { pending.finish() } }.start()
    }
}
