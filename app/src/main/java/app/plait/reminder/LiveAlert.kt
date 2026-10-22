package app.plait.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.plait.R

enum class MeetingPhase { None, Countdown, InProgress }
enum class TaskPhase { None, Soon, Due }
enum class LiveKind { Recording, Meeting, Task }

/** The decisions behind the live alerts, with no Android in them so they can be tested. */
object LiveLogic {
    const val MEETING_LEAD_MS = 10 * 60_000L
    const val TASK_LEAD_MS = 15 * 60_000L

    /** Countdown from [lead] before the start, in progress until the end, nothing outside that. */
    fun meetingPhase(now: Long, start: Long, end: Long, lead: Long = MEETING_LEAD_MS): MeetingPhase = when {
        end <= start || now >= end -> MeetingPhase.None
        now >= start -> MeetingPhase.InProgress
        now >= start - lead -> MeetingPhase.Countdown
        else -> MeetingPhase.None
    }

    /** The next moment the meeting alert changes state (countdown begins, meeting starts, meeting ends). */
    fun nextBoundary(now: Long, start: Long, end: Long, lead: Long = MEETING_LEAD_MS): Long? =
        listOf(start - lead, start, end).firstOrNull { it > now }

    fun taskPhase(now: Long, at: Long): TaskPhase = when {
        now < at - TASK_LEAD_MS -> TaskPhase.None
        now < at -> TaskPhase.Soon
        else -> TaskPhase.Due
    }

    /** Recording wins over the meeting alert; a task alert can sit beside either, so at most two show. */
    fun choose(recording: Boolean, meeting: MeetingPhase, taskLive: Boolean): List<LiveKind> = buildList {
        if (recording) add(LiveKind.Recording) else if (meeting != MeetingPhase.None) add(LiveKind.Meeting)
        if (taskLive) add(LiveKind.Task)
    }

    /** When a snoozed alert comes back: [minutes] from now, or null when that would be after the meeting ends. */
    fun snoozeAt(now: Long, minutes: Int, endMs: Long): Long? {
        val at = now + minutes * 60_000L
        return if (endMs > 0 && at >= endMs) null else at
    }

    /** Whole minutes still to go, rounded up, so a slightly late alarm still reads "In 10 min". */
    fun minutesLeft(now: Long, start: Long): Int = if (start <= now) 0 else ((start - now + 59_999L) / 60_000L).toInt()
}

/**
 * One notification shape for the three live alerts. It is an ordinary ongoing notification (title, no custom views, never
 * a minimum-importance channel), so it works on Android 8 to 16; on Android 16 it also asks to be promoted to a Live Update.
 */
object LiveAlert {
    const val CHANNEL = "live"
    private const val EXTRA_PROMOTED = "android.requestPromotedOngoing"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Live alerts", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Timers for the meeting about to start, and for tasks that are due."
            })
        }
    }

    fun builder(context: Context, channel: String, title: String, tap: PendingIntent): NotificationCompat.Builder {
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(tap)
            .setOngoing(true).setAutoCancel(false).setOnlyAlertOnce(true)
        // shortcut: the key is written out because core-ktx 1.16 has no setRequestPromotedOngoing; use it once core is 1.17+.
        if (Build.VERSION.SDK_INT >= 36) b.addExtras(Bundle().apply { putBoolean(EXTRA_PROMOTED, true) })
        return b
    }

    /** A timer the system draws and ticks: counting down to [whenMs], or up from it. */
    fun NotificationCompat.Builder.timer(whenMs: Long, countDown: Boolean): NotificationCompat.Builder =
        setShowWhen(true).setWhen(whenMs).setUsesChronometer(true).setChronometerCountDown(countDown)

    fun canPromote(context: Context): Boolean = Build.VERSION.SDK_INT >= 36 &&
        runCatching { context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications() }.getOrDefault(false)

    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            context.startActivity(
                Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
