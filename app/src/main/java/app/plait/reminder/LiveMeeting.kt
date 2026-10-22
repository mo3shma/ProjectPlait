package app.plait.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import app.plait.audio.RecordingService
import app.plait.data.CalEvent
import app.plait.data.NextUp
import app.plait.data.TimeFmt
import app.plait.reminder.LiveAlert.timer

/**
 * The live alert for the next meeting: a countdown from 10 minutes before, "In progress" with the time elapsed once it
 * starts, gone when it ends. One alarm is always set for the next change, and [refresh] works out the rest from the
 * clock, so it is safe to call at any time (alarm, calendar sync, restart, recording started or stopped).
 */
object LiveMeeting {
    const val ACTION = "app.plait.LIVE_MEETING"
    private const val CODE = 7002

    private fun alarm(c: Context) = PendingIntent.getBroadcast(
        c, CODE, Intent(c, ReminderReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        Thread { runCatching { refresh(app) } }.start()
    }

    fun refresh(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        am.cancel(alarm(context))
        val now = System.currentTimeMillis()
        val ev = if (ReminderPrefs.before(context)) NextUp.next(context, now) else null
        if (ev == null) { nm.cancel(CODE); return }
        val phase = LiveLogic.meetingPhase(now, ev.startMs, ev.endMs)
        if (LiveKind.Meeting in LiveLogic.choose(RecordingService.live, phase, false)) post(context, nm, ev, phase) else nm.cancel(CODE)
        LiveLogic.nextBoundary(now, ev.startMs, ev.endMs)?.let { Reminders.setAlarm(am, it, alarm(context)) }
    }

    private fun post(c: Context, nm: NotificationManager, ev: CalEvent, phase: MeetingPhase) {
        LiveAlert.ensureChannel(c)
        val countdown = phase == MeetingPhase.Countdown
        val b = LiveAlert.builder(c, LiveAlert.CHANNEL, if (countdown) ev.title else "In progress: ${ev.title}", Reminders.open(c, ev.id, 0, ev.id.hashCode() + 5))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentText(if (countdown) "Starts ${TimeFmt.clock(ev.startMs)}" else "Started ${TimeFmt.clock(ev.startMs)}")
            .timer(ev.startMs, countdown)
        ev.callLink?.url?.let { url ->
            val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            b.addAction(0, "Join", PendingIntent.getActivity(c, ev.id.hashCode() + 6, view, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        if (!countdown) b.addAction(0, "Notes", Reminders.open(c, ev.id, 2, ev.id.hashCode() + 7))
        runCatching { nm.notify(CODE, b.build()) }
    }
}
