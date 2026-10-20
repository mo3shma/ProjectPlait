package app.plait.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import app.plait.data.context.EmailParser
import app.plait.data.context.ParsedEmail

/** An email or note another app shared with Plait, waiting for the user to pick its meeting. */
class SharedMail(val mail: ParsedEmail, val source: String)

object ShareInbox {
    var pending by mutableStateOf<SharedMail?>(null)

    private const val MAX_BYTES = 3_000_000

    /** Reads a share or "open with" intent. Returns true if it carried something we could use. */
    fun handle(context: Context, intent: Intent?): Boolean {
        intent ?: return false
        val shared = runCatching { read(context, intent) }.getOrNull() ?: return false
        if (shared.mail.body.isBlank()) return false
        pending = shared
        return true
    }

    private fun read(context: Context, intent: Intent): SharedMail? {
        val type = intent.type.orEmpty()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                if (stream != null && (type.startsWith("message/") || stream.toString().endsWith(".eml", true))) {
                    readEml(context, stream)?.let { return SharedMail(it, "file") }
                }
                if (!text.isNullOrBlank()) return SharedMail(EmailParser.fromText(text, subject), "shared")
                if (stream != null && type.startsWith("text/")) {
                    readBytes(context, stream)?.let { return SharedMail(EmailParser.fromText(String(it, Charsets.UTF_8), subject), "shared") }
                }
            }
            Intent.ACTION_VIEW -> intent.data?.let { uri -> readEml(context, uri)?.let { return SharedMail(it, "file") } }
        }
        return null
    }

    private fun readBytes(context: Context, uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_BYTES) }

    /** .eml bytes are read as ISO-8859-1 so every byte survives until the MIME parser decodes it. */
    private fun readEml(context: Context, uri: Uri): ParsedEmail? =
        readBytes(context, uri)?.let { EmailParser.parseEml(String(it, Charsets.ISO_8859_1)) }

    fun clipboardText(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return null
        val clip: ClipData = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
    }
}
