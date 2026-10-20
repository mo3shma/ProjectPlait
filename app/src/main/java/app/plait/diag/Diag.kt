package app.plait.diag

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small log kept only on the phone, so a problem on a real device can be looked at (and shared by you, if you choose)
 * without any crash-reporting service. It keeps the last 200 lines and records crashes.
 */
object Diag {
    private var file: File? = null
    private val lock = Any()
    private const val MAX_LINES = 200

    fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, "diag.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            log("CRASH", "${t.name}: ${e.javaClass.simpleName}: ${e.message}\n" + e.stackTrace.take(12).joinToString("\n") { "  at $it" })
            previous?.uncaughtException(t, e)
        }
    }

    fun log(tag: String, message: String) {
        val f = file ?: return
        synchronized(lock) {
            runCatching {
                val line = SimpleDateFormat("MM-dd HH:mm:ss", Locale.ENGLISH).format(Date()) + " [$tag] " + message.replace("\r", "")
                val lines = (if (f.exists()) f.readLines() else emptyList()) + line.lines()
                f.writeText(lines.takeLast(MAX_LINES).joinToString("\n"))
            }
        }
    }

    fun read(): String = synchronized(lock) { file?.takeIf { it.exists() }?.readText().orEmpty() }

    fun clear() { synchronized(lock) { file?.delete() } }
}
