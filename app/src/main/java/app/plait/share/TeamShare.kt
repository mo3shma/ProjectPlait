package app.plait.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.plait.data.CalEvent
import app.plait.data.NoteStore
import app.plait.data.Profile
import app.plait.data.backup.Backup
import app.plait.data.backup.BackupData
import app.plait.data.backup.BackupSettings
import app.plait.data.backup.BundleMeta
import app.plait.data.context.ContextStore
import app.plait.data.crypto.Crypt
import app.plait.data.memo.MemoStore
import java.io.File

/**
 * Sharing one meeting's notes with a teammate, on purpose, as a file. Nothing is shared unless you build and send it. Your
 * name goes on the notes so they know whose they are; emails and recordings only go if you tick them.
 */
object TeamShare {
    fun create(context: Context, ev: CalEvent, includeEmails: Boolean, includeMedia: Boolean, passphrase: String): File {
        Profile.load(context)
        val author = Profile.name.ifBlank { Profile.device }
        val notes = NoteStore.get(context).notes.filter { it.eventId == ev.id && it.author == null }.map { it.copy(author = author) }
        val memos = MemoStore.get(context).items.filter { it.eventId == ev.id && it.author == null }.map { it.copy(author = author) }
        val mail = if (includeEmails) ContextStore.get(context).forEvent(ev.id) else emptyList()
        val store = NoteStore.get(context); val memoStore = MemoStore.get(context)
        val media = if (includeMedia) notes.map { it.audioFile to store.audioFile(it) } + memos.mapNotNull { m -> m.imageFile?.let { it to File(memoStore.dir, it) } } else emptyList()
        val data = BackupData(System.currentTimeMillis(), Profile.device, BackupSettings(), notes, memos, mail, bundle = BundleMeta(ev.title, ev.startMs, author))
        val dir = File(context.cacheDir, "share").also { it.mkdirs() }
        val file = File(dir, ev.title.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).ifBlank { "notes" } + ".plait")
        file.outputStream().use { raw ->
            val sink = if (passphrase.isNotEmpty()) Crypt.encrypt(raw, passphrase.toCharArray()) else raw
            sink.use { Backup.write(it, data, media) }
        }
        return file
    }

    fun send(context: Context, ev: CalEvent, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val i = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Notes: ${ev.title}").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(i, "Send notes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
