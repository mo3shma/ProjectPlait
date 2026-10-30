package app.plait.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.plait.ai.ModelDownloadService
import app.plait.diag.Diag
import app.plait.R
import app.plait.i18n.Txt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A newer Plait on GitHub, with the APK that suits this phone. */
data class UpdateInfo(val version: String, val notes: String, val url: String, val bytes: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val done: Long) : UpdateState
    /** Android needs the user to let Plait install apps (once). */
    data class NeedsPermission(val info: UpdateInfo, val file: File) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val reason: String, val info: UpdateInfo? = null) : UpdateState
}

/**
 * Keeps Plait up to date without a trip to GitHub. It asks GitHub's public API for the latest release (no account, no
 * data about you: one request each time Plait opens, and it can be switched off), downloads the APK for this phone's
 * processor with a resumable download that carries on in the background, and hands it to Android's installer. Android
 * checks the update is signed with the same key, so only a genuine Plait can replace this one.
 */
object Updater {
    const val REPO = "mo3shma/ProjectPlait"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"

    var state by mutableStateOf<UpdateState>(UpdateState.Idle); private set
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("plait", Context.MODE_PRIVATE)
    fun autoCheck(c: Context) = prefs(c).getBoolean("update_auto", true)
    fun setAutoCheck(c: Context, on: Boolean) { prefs(c).edit().putBoolean("update_auto", on).apply() }

    fun currentVersion(c: Context): String =
        runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull() ?: "0"

    /** Checks on every launch if the user has not switched it off. It only looks: the download starts when the user taps Update. */
    fun checkInBackground(context: Context) {
        if (autoCheck(context)) check(context)
    }

    fun check(context: Context) {
        if (app.plait.BuildConfig.PLAY || job?.isActive == true) return
        val app = context.applicationContext
        state = UpdateState.Checking
        job = scope.launch {
            state = try {
                val info = fetchLatest(app)
                if (info != null && isNewer(info.version, currentVersion(app))) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                Diag.log("UPDATE", "check: ${e.message}")
                UpdateState.Failed(Txt.s(R.string.settings_update_unreachable))
            }
        }
    }

    private fun fetchLatest(context: Context): UpdateInfo? {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Plait/${currentVersion(context)}")
        }
        try {
            if (conn.responseCode == 404) return null // no release yet
            if (conn.responseCode != 200) throw IOException("GitHub answered ${conn.responseCode}")
            return parseRelease(conn.inputStream.bufferedReader().readText(), Build.SUPPORTED_ABIS.toList())
        } finally { conn.disconnect() }
    }

    /** The release's version, notes and the APK for this phone: plait.apk on 64-bit phones, plait-armv7.apk on 32-bit ones. */
    internal fun parseRelease(json: String, abis: List<String>): UpdateInfo? {
        val o = JSONObject(json)
        val version = o.optString("tag_name").trim().removePrefix("v").ifBlank { return null }
        val assets = o.optJSONArray("assets") ?: return null
        val list = List(assets.length()) { assets.getJSONObject(it) }
        val want = if (abis.firstOrNull() == "arm64-v8a" || "arm64-v8a" in abis) "plait.apk" else "plait-armv7.apk"
        val asset = list.firstOrNull { it.optString("name") == want } ?: list.firstOrNull { it.optString("name") == "plait.apk" } ?: return null
        return UpdateInfo(version, o.optString("body").trim(), asset.getString("browser_download_url"), asset.optLong("size"))
    }

    /** "0.20.0" is newer than "0.19.3"; "0.19.10" is newer than "0.19.9". */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate); val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Downloads the update (resuming a part already on the phone) and installs it. */
    fun download(context: Context, info: UpdateInfo) {
        if (app.plait.BuildConfig.PLAY || (job?.isActive == true && state is UpdateState.Downloading)) return
        val app = context.applicationContext
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.filter { !it.name.startsWith("plait-${info.version}.") }?.forEach { it.delete() } // older updates
        val file = File(dir, "plait-${info.version}.apk")
        state = UpdateState.Downloading(info, 0)
        ModelDownloadService.start(app)
        job = scope.launch {
            try {
                if (!(file.exists() && (info.bytes <= 0 || file.length() == info.bytes))) fetch(info, file)
                install(app, info, file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                state = UpdateState.Available(info); throw e
            } catch (e: Exception) {
                Diag.log("UPDATE", "download: ${e.message}")
                state = UpdateState.Failed(Txt.s(R.string.settings_update_stopped), info)
            }
        }
    }

    fun cancel() { job?.cancel() }

    /** The update download, for the background-download notification. */
    fun downloading(): UpdateState.Downloading? = state as? UpdateState.Downloading

    internal suspend fun fetch(info: UpdateInfo, file: File) {
        val part = File(file.path + ".part")
        var attempt = 0
        while (true) {
            try {
                var have = if (part.exists()) part.length() else 0L
                val conn = (URL(info.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
                try {
                    val code = conn.responseCode
                    if (code == 200 && have > 0) { part.delete(); have = 0 }
                    else if (code != 200 && code != 206) throw IOException("server answered $code")
                    var done = have
                    conn.inputStream.use { input ->
                        java.io.FileOutputStream(part, have > 0).use { out ->
                            val buf = ByteArray(64 * 1024)
                            var last = 0L
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n); done += n
                                val now = System.nanoTime()
                                if (now - last > 250_000_000L) { last = now; state = UpdateState.Downloading(info, done) }
                            }
                        }
                    }
                } finally { conn.disconnect() }
                if (info.bytes > 0 && part.length() != info.bytes) throw IOException("incomplete (${part.length()} of ${info.bytes})")
                if (!part.renameTo(file)) throw IOException("couldn't save the update")
                return
            } catch (e: IOException) {
                if (++attempt > 5) throw e
                kotlinx.coroutines.delay(attempt * 5_000L)
            }
        }
    }

    /** Hands the APK to Android's installer. The first time, Android asks the user to let Plait install apps. */
    suspend fun install(context: Context, info: UpdateInfo, file: File) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedsPermission(info, file)
            return
        }
        state = UpdateState.Installing(info)
        withContext(Dispatchers.IO) {
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(app.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("plait.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(app, UpdateReceiver::class.java).setAction(ACTION_STATUS).putExtra("version", info.version)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
            }
        }
    }

    /** After the user allowed Plait to install apps. */
    fun resumeInstall(context: Context) {
        val s = state as? UpdateState.NeedsPermission ?: return
        scope.launch { runCatching { install(context, s.info, s.file) }.onFailure { state = UpdateState.Failed(Txt.s(R.string.settings_update_start_failed), s.info) } }
    }

    internal fun onStatus(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val info = (state as? UpdateState.Installing)?.info
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
            PackageInstaller.STATUS_SUCCESS -> state = UpdateState.Idle // the app restarts as the new version
            PackageInstaller.STATUS_FAILURE_ABORTED -> state = info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Diag.log("UPDATE", "install: $status $msg")
                state = UpdateState.Failed(
                    if (status == PackageInstaller.STATUS_FAILURE_CONFLICT || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
                        Txt.s(R.string.settings_update_conflict)
                    else msg?.let { Txt.s(R.string.settings_update_not_installed_why, it) } ?: Txt.s(R.string.settings_update_not_installed),
                    info,
                )
            }
        }
    }

    const val ACTION_STATUS = "app.plait.UPDATE_STATUS"
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Updater.ACTION_STATUS) Updater.onStatus(context, intent)
    }
}
