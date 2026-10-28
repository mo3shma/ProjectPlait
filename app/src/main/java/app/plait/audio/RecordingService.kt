package app.plait.audio

import app.plait.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.plait.MainActivity
import app.plait.reminder.LiveAlert
import app.plait.i18n.Bidi
import app.plait.i18n.Txt

/**
 * Keeps the process alive and the microphone open while a note is being recorded, so capture
 * continues with the screen off or another app in front. The recording itself lives in
 * [RecordingHub]; this service only holds the notification Android requires.
 */
class RecordingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's buttons drive the same session the Record screen shows, so they can never disagree.
        when (intent?.action) {
            ACTION_TOGGLE -> {
                val c = RecordingHub.active
                if (c == null) stopSelf() else if (c.phase == RecPhase.Paused) c.resume() else c.pause()
                return START_NOT_STICKY
            }
            ACTION_STOP -> {
                if (RecordingHub.active == null) stopSelf() else RecordingHub.requestStop(applicationContext)
                return START_NOT_STICKY
            }
        }
        title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        ensureChannel(this)
        val notification = build(this)
        live = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        live = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 41
        private const val EXTRA_TITLE = "title"
        private const val ACTION_TOGGLE = "app.plait.REC_TOGGLE"
        private const val ACTION_STOP = "app.plait.REC_STOP"

        /** True while the service is up, so [refresh] only re-posts a live notification. */
        @Volatile var live = false; private set
        @Volatile private var title = ""

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, Txt.s(R.string.note_notif_channel), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        private fun action(context: Context, code: Int, action: String) = PendingIntent.getService(
            context, code, Intent(context, RecordingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Static text (no timer), with Pause/Resume and Stop. */
        private fun build(context: Context): Notification {
            val paused = RecordingHub.active?.phase == RecPhase.Paused
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val who = Bidi.isolate(title.ifBlank { Txt.s(R.string.note_notif_tap_return) })
            val b = LiveAlert.builder(context, CHANNEL, Txt.s(R.string.note_notif_title), open)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(0, Txt.s(if (paused) R.string.note_notif_resume else R.string.note_notif_pause), action(context, 1, ACTION_TOGGLE))
                .addAction(0, Txt.s(R.string.note_notif_stop), action(context, 2, ACTION_STOP))
                .setContentText(Txt.s(if (paused) R.string.note_notif_paused else R.string.note_notif_recording, who)).setShowWhen(false)
            return b.build()
        }

        /** Re-posts the notification after the state changed (Pause or Resume from the app or the notification). */
        fun refresh(context: Context) {
            if (!live) return
            runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(context)) }
        }

        fun start(context: Context, title: String) {
            ContextCompat.startForegroundService(
                context, Intent(context, RecordingService::class.java).putExtra(EXTRA_TITLE, title),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
