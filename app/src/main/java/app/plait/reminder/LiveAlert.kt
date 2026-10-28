package app.plait.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt

enum class TaskPhase { None, Soon, Due }

/** The decisions behind the alerts, with no Android in them so they can be tested. */
object LiveLogic {
    const val TASK_LEAD_MS = 15 * 60_000L

    fun taskPhase(now: Long, at: Long): TaskPhase = when {
        now < at - TASK_LEAD_MS -> TaskPhase.None
        now < at -> TaskPhase.Soon
        else -> TaskPhase.Due
    }

    /** When a snoozed alert comes back: [minutes] from now, or null when that would be after the meeting ends. */
    fun snoozeAt(now: Long, minutes: Int, endMs: Long): Long? {
        val at = now + minutes * 60_000L
        return if (endMs > 0 && at >= endMs) null else at
    }

    /** Whole minutes still to go, rounded up, so a slightly late alarm still reads "In 10 min". */
    fun minutesLeft(now: Long, start: Long): Int = if (start <= now) 0 else ((start - now + 59_999L) / 60_000L).toInt()

    /** "In 10 min: Design review", or "Starting now: ..." once [min] is 0. */
    fun meetingTitle(min: Int, title: String): String =
        if (min > 0) Txt.plural(R.plurals.alert_meeting_in, min, min, Bidi.isolate(title)) else Txt.s(R.string.alert_meeting_now, Bidi.isolate(title))

    /** The evening nudge: one meeting by name, or how many with the first one's name. */
    fun nudgeText(count: Int, firstTitle: String): String =
        if (count == 1) Txt.s(R.string.alert_nudge_one, Bidi.isolate(firstTitle)) else Txt.plural(R.plurals.alert_nudge_many, count, count, Bidi.isolate(firstTitle))

    /** A task alert's line: "Due 3:00 PM", plus the meeting it came from. */
    fun taskLine(clock: String, meeting: String): String =
        if (meeting.isNotBlank()) Txt.s(R.string.alert_task_due_from, Bidi.isolate(clock), Bidi.isolate(meeting)) else Txt.s(R.string.alert_task_due, Bidi.isolate(clock))
}

/** One shape for the ongoing alerts (recording, task due): an ordinary ongoing notification with static text. */
object LiveAlert {
    const val CHANNEL = "live"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            // Named in the app's language when first made; Android keeps that name afterwards.
            nm.createNotificationChannel(NotificationChannel(CHANNEL, Txt.s(R.string.alert_channel_live), NotificationManager.IMPORTANCE_LOW).apply {
                description = Txt.s(R.string.alert_channel_live_desc)
            })
        }
    }

    fun builder(context: Context, channel: String, title: String, tap: PendingIntent): NotificationCompat.Builder =
        NotificationCompat.Builder(AppLocale.wrap(context), channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(tap)
            .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true)
}
