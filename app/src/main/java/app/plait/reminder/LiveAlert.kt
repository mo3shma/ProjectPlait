package app.plait.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.plait.R

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
}

/** One shape for the ongoing alerts (recording, task due): an ordinary ongoing notification with static text. */
object LiveAlert {
    const val CHANNEL = "live"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live alerts", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A quiet heads-up before a task is due."
            })
        }
    }

    fun builder(context: Context, channel: String, title: String, tap: PendingIntent): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(tap)
            .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true)
}
