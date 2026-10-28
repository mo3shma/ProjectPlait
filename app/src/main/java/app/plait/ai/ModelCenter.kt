package app.plait.ai

import android.content.Context
import app.plait.R
import app.plait.audio.NoteProcessor
import app.plait.i18n.Txt
import app.plait.diag.Diag
import android.net.ConnectivityManager
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface ModelState {
    data object NotInstalled : ModelState
    data class Downloading(val done: Long, val total: Long) : ModelState
    data object Installed : ModelState
    data class Failed(val reason: String) : ModelState
}

/**
 * Downloads and keeps the optional models. A download resumes where it stopped (a `.part` file per file), writes a
 * marker only when every file is complete, and can be deleted to give the space back. Nothing is downloaded unless
 * the user taps Download. While anything downloads, [ModelDownloadService] keeps the app running in the background;
 * unfinished downloads are remembered and picked up again if Android stops the app, and a dropped connection is
 * retried a few times before the download is reported as stopped.
 */
object ModelCenter {
    val states = mutableStateMapOf<String, ModelState>()
    private val jobs = HashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val RETRIES = 5

    fun dir(context: Context, id: String) = File(context.filesDir, "models/$id")
    private fun marker(context: Context, id: String) = File(dir(context, id), ".complete")

    fun file(context: Context, id: String, name: String) = File(dir(context, id), name)

    /** True once every file of the model is on the phone, with or without the completion marker. */
    fun isInstalled(context: Context, id: String): Boolean =
        marker(context, id).exists() || ModelCatalog.byId(id)?.let { completeOnDisk(context, it) } == true

    /**
     * Reads what is on disk into [states]. A model whose files are all present at the right size counts as installed even
     * if the completion marker is missing (an interrupted finish, a file copied in by hand), so it is never downloaded twice.
     */
    fun refresh(context: Context) {
        for (m in ModelCatalog.all) {
            if (states[m.id] is ModelState.Downloading) continue
            if (!isInstalled(context, m.id) && completeOnDisk(context, m)) runCatching { marker(context, m.id).writeText("ok") }
            states[m.id] = if (isInstalled(context, m.id)) ModelState.Installed else ModelState.NotInstalled
        }
    }

    fun completeOnDisk(context: Context, spec: ModelSpec): Boolean =
        spec.files.all { f -> File(dir(context, spec.id), f.name).let { it.exists() && it.length() == f.bytes } }

    /** Marks a model installed if [adopt] has just made its files whole. */
    fun adopt(context: Context, spec: ModelSpec): Boolean {
        if (!completeOnDisk(context, spec)) return false
        runCatching { marker(context, spec.id).writeText("ok") }
        states[spec.id] = ModelState.Installed
        return true
    }

    fun state(id: String): ModelState = states[id] ?: ModelState.NotInstalled

    /** The models downloading right now, with their progress. */
    fun downloading(): List<Pair<ModelSpec, ModelState.Downloading>> =
        ModelCatalog.all.mapNotNull { m -> (states[m.id] as? ModelState.Downloading)?.let { m to it } }

    private fun prefs(context: Context) = context.getSharedPreferences("plait", Context.MODE_PRIVATE)
    private fun pending(context: Context): Set<String> = prefs(context).getStringSet("model_downloads", emptySet()).orEmpty()
    private fun setPending(context: Context, id: String, on: Boolean) {
        val now = pending(context).let { if (on) it + id else it - id }
        prefs(context).edit().putStringSet("model_downloads", now).apply()
    }

    /** Starts again any download that was still running when the app was stopped. */
    fun resumePending(context: Context) {
        val app = context.applicationContext
        for (id in pending(app)) {
            val spec = ModelCatalog.byId(id)
            if (spec == null || isInstalled(app, id)) { setPending(app, id, false); continue }
            download(app, spec)
        }
    }

    fun isMetered(context: Context): Boolean =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered ?: false

    /** "Download on Wi-Fi only": a download that starts on a metered network is held back, with a note, instead of using mobile data. */
    fun wifiOnly(context: Context): Boolean = prefs(context).getBoolean("models_wifi_only", false)
    fun setWifiOnly(context: Context, on: Boolean) { prefs(context).edit().putBoolean("models_wifi_only", on).apply() }

    fun download(context: Context, spec: ModelSpec) {
        if (jobs[spec.id]?.isActive == true) return
        val app = context.applicationContext
        // Never fetch what is already on the phone, whole or in part.
        if (isInstalled(app, spec.id) || adopt(app, spec)) return
        runCatching { ModelFinder.scanThisPhone(app) }
        if (isInstalled(app, spec.id)) return
        if (wifiOnly(app) && isMetered(app)) { states[spec.id] = ModelState.Failed(Txt.s(R.string.ai_dl_wifi_only)); return }
        val root = dir(app, spec.id).also { it.mkdirs() }
        val needed = spec.bytes - spec.files.sumOf { File(root, it.name + ".part").length() }
        if (root.usableSpace < needed + 50_000_000L) {
            states[spec.id] = ModelState.Failed(Txt.s(R.string.ai_dl_no_space, ModelCatalog.formatSize(needed)))
            return
        }
        states[spec.id] = ModelState.Downloading(0, spec.bytes)
        setPending(app, spec.id, true)
        ModelDownloadService.start(app)
        jobs[spec.id] = scope.launch {
            try {
                var attempt = 0
                while (true) {
                    try {
                        var done = spec.files.sumOf { f -> File(root, f.name).takeIf { it.exists() }?.length() ?: 0L }
                        for (f in spec.files) {
                            val target = File(root, f.name)
                            if (target.exists() && target.length() == f.bytes) continue
                            done = fetch(f, root, done, spec) { states[spec.id] = ModelState.Downloading(it, spec.bytes) }
                        }
                        break
                    } catch (e: IOException) {
                        // A dropped connection: wait and carry on from the .part file, a few times, before giving up.
                        if (++attempt > RETRIES) throw e
                        Diag.log("DOWNLOAD", "${spec.id}: ${e.message}, retry $attempt")
                        delay(attempt * 5_000L)
                    }
                }
                marker(app, spec.id).writeText("ok")
                states[spec.id] = ModelState.Installed
                setPending(app, spec.id, false)
                AiPrefs.makeDefault(app, spec)
                // A new speech model rewrites the rough transcripts already on the phone.
                if (spec.kind == ModelKind.Speech) NoteProcessor.improveAll(app)
            } catch (e: kotlinx.coroutines.CancellationException) {
                states[spec.id] = ModelState.NotInstalled
                throw e
            } catch (e: Exception) {
                Diag.log("DOWNLOAD", "${spec.id}: ${e.message}")
                setPending(app, spec.id, false)
                states[spec.id] = ModelState.Failed(
                    if (e is IOException) Txt.s(R.string.ai_dl_stopped, e.message ?: Txt.s(R.string.ai_dl_network)) else (e.message ?: Txt.s(R.string.ai_dl_failed)),
                )
            }
        }
    }


    /** Downloads one file, resuming from its .part. Returns the running total of bytes done. */
    private suspend fun fetch(f: ModelFile, root: File, doneBefore: Long, spec: ModelSpec, progress: (Long) -> Unit): Long {
        val part = File(root, f.name + ".part")
        var have = if (part.exists()) part.length() else 0L
        if (have > f.bytes) { part.delete(); have = 0 }
        val conn = (URL(f.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            if (code == 200 && have > 0) { part.delete(); have = 0 } // server ignored Range: start over
            else if (code != 200 && code != 206) throw IOException(Txt.s(R.string.ai_dl_server_answered, code))
            var done = doneBefore + have
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, have > 0 && code == 206).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val now = System.nanoTime()
                        if (now - lastReport > 250_000_000L) { lastReport = now; progress(done) }
                    }
                }
            }
            if (part.length() != f.bytes) throw IOException(Txt.s(R.string.ai_dl_incomplete, part.length(), f.bytes))
            if (!part.renameTo(File(root, f.name))) throw IOException(Txt.s(R.string.ai_dl_cannot_save))
            progress(done)
            return done
        } finally {
            conn.disconnect()
        }
    }

    fun cancel(context: Context, id: String) {
        jobs[id]?.cancel()
        states[id] = ModelState.NotInstalled
        setPending(context.applicationContext, id, false)
    }

    fun cancelAll(context: Context) {
        downloading().forEach { cancel(context, it.first.id) }
    }

    fun delete(context: Context, id: String) {
        jobs[id]?.cancel()
        setPending(context.applicationContext, id, false)
        dir(context, id).deleteRecursively()
        states[id] = ModelState.NotInstalled
        if (AiPrefs.textModelId == id) LocalLlm.release()
    }
}
