package app.plait.ai

import app.plait.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.plait.MainActivity
import app.plait.data.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps model downloads going while Plait is in the background, the screen is off, or the app has been swiped away.
 * Android freezes apps that leave the screen unless they run a foreground service, which is why downloads used to stop.
 * The download itself lives in [ModelCenter]; this service holds the progress notification, keeps the CPU and Wi-Fi
 * awake, and stops itself when nothing is downloading.
 */
class ModelDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wake: PowerManager.WakeLock? = null
    private var wifi: WifiManager.WifiLock? = null
    private var ticking = false
    /** Models this service has seen downloading, so it can say which ones finished. */
    private val seen = HashSet<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        if (intent?.action == ACTION_CANCEL) {
            ModelCenter.cancelAll(this)
            Updater.cancel()
        }
        goForeground(progressNotification())
        holdLocks()
        // Restarted by Android after the process was killed: pick the unfinished downloads up again.
        if (intent == null) ModelCenter.resumePending(this)
        if (!ticking) {
            ticking = true
            scope.launch {
                // Give a just-started download a moment to register before deciding there is nothing to do.
                delay(1500)
                while (isActive) {
                    val active = ModelCenter.downloading()
                    active.forEach { seen += it.first.id }
                    finished().forEach { notifyReady(it) }
                    if (active.isEmpty() && Updater.downloading() == null) break
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, progressNotification())
                    delay(1000)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 limits data-sync services to six hours a day. The download keeps its .part files and resumes later.
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { wake?.takeIf { it.isHeld }?.release() }
        runCatching { wifi?.takeIf { it.isHeld }?.release() }
        super.onDestroy()
    }

    private fun goForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun holdLocks() {
        if (wake == null) wake = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "plait:model-download").apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
        @Suppress("DEPRECATION")
        if (wifi == null) wifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createWifiLock(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_HIGH_PERF else WifiManager.WIFI_MODE_FULL, "plait:model-download")
            ?.apply { setReferenceCounted(false); acquire() }
    }

    private fun finished(): List<ModelSpec> {
        val done = seen.mapNotNull { id -> ModelCatalog.byId(id)?.takeIf { ModelCenter.state(id) is ModelState.Installed } }
        seen.removeAll(done.map { it.id }.toSet())
        return done
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(): Notification {
        val active = ModelCenter.downloading()
        val update = Updater.downloading()
        val done = active.sumOf { it.second.done } + (update?.done ?: 0L)
        val total = (active.sumOf { it.second.total } + (update?.info?.bytes ?: 0L)).coerceAtLeast(1)
        val title = when {
            update != null && active.isEmpty() -> "Downloading Plait ${update.info.version}"
            update != null -> "Downloading Plait and ${active.size} model${if (active.size == 1) "" else "s"}"
            active.isEmpty() -> "Getting the download ready"
            active.size == 1 -> "Downloading ${active[0].first.title}"
            else -> "Downloading ${active.size} models"
        }
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (active.isEmpty() && update == null) "Plait keeps downloading in the background." else "${ModelCatalog.formatSize(done)} of ${ModelCatalog.formatSize(total)}")
            .setProgress(1000, (done * 1000 / total).toInt(), active.isEmpty() && update == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp())
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private fun notifyReady(spec: ModelSpec) {
        val n = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${spec.title} is ready")
            .setContentText("Downloaded and in use. Nothing leaves your phone.")
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(spec.id.hashCode(), n) }
    }

    companion object {
        private const val CHANNEL_PROGRESS = "model-download"
        private const val CHANNEL_DONE = "model-ready"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_CANCEL = "app.plait.CANCEL_DOWNLOADS"

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL_PROGRESS, "Model downloads", NotificationManager.IMPORTANCE_LOW))
            if (nm.getNotificationChannel(CHANNEL_DONE) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Model ready", NotificationManager.IMPORTANCE_DEFAULT))
        }

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java)) }
        }
    }
}
