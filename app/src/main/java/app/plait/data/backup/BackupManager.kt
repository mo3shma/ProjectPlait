package app.plait.data.backup

import android.content.Context
import android.net.Uri
import app.plait.data.Calendars
import app.plait.data.NoteStore
import app.plait.data.Profile
import app.plait.data.context.ContextStore
import app.plait.data.memo.MemoStore
import java.io.File

/** Export and import of everything Plait keeps on the phone. */
object BackupManager {
    data class Report(val notes: Int, val memos: Int, val emails: Int, val feeds: Int, val media: Int, val settingsApplied: Boolean)

    /** The file is protected and no passphrase was given. */
    class NeedsPassphrase : Exception("This backup is protected. Enter its passphrase and try again.")

    fun export(context: Context, uri: Uri, includeMedia: Boolean, passphrase: String = ""): Report {
        val notes = NoteStore.get(context)
        val memos = MemoStore.get(context)
        val mail = ContextStore.get(context)
        val settings = currentSettings()
        val data = BackupData(System.currentTimeMillis(), Profile.device, settings, notes.notes.toList(), memos.items.toList(), mail.items.toList())
        val media = if (includeMedia) {
            notes.notes.map { it.audioFile to notes.audioFile(it) } + memos.items.mapNotNull { m -> m.imageFile?.let { it to File(memos.dir, it) } }
        } else emptyList()
        val out = context.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Couldn't write to that location.")
        val sink = if (passphrase.isNotEmpty()) app.plait.data.crypto.Crypt.encrypt(out, passphrase.toCharArray()) else out
        sink.use { Backup.write(it, data, media) }
        return Report(data.notes.size, data.memos.size, data.emails.size, settings.feeds.size, media.count { it.second.exists() }, true)
    }

    /** Adds what this phone does not have. Nothing on the phone is overwritten or deleted. */
    fun import(context: Context, uri: Uri, passphrase: String = ""): Report {
        val temp = File(context.cacheDir, "import-" + System.currentTimeMillis())
        try {
            val raw = java.io.BufferedInputStream(context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("Couldn't open that file."))
            val data0 = raw.use { input ->
                input.mark(32)
                val head = ByteArray(app.plait.data.crypto.Crypt.MAGIC.size)
                val n = input.read(head)
                val encrypted = n == head.size && app.plait.data.crypto.Crypt.isEncrypted(head)
                if (!encrypted) input.reset()
                if (encrypted && passphrase.isEmpty()) throw NeedsPassphrase()
                val stream = if (encrypted) app.plait.data.crypto.Crypt.decrypt(input, passphrase.toCharArray()) else input
                try { Backup.read(stream, temp) } catch (e: app.plait.data.crypto.Crypt.BadPassphrase) { throw e }
            }
            val notes = NoteStore.get(context)
            val memos = MemoStore.get(context)
            val mail = ContextStore.get(context)
            // A teammate's notes for one meeting: attach them to this phone's copy of that meeting, if there is one.
            val data = data0.bundle?.let { b ->
                val target = Calendars.events.firstOrNull { ev ->
                    app.plait.data.brief.BriefBuilder.seriesKey(ev.title) == app.plait.data.brief.BriefBuilder.seriesKey(b.title) && kotlin.math.abs(ev.startMs - b.startMs) <= 10 * 60_000L
                }
                val oldIds = (data0.notes.map { it.eventId } + data0.memos.map { it.eventId } + data0.emails.map { it.eventId }).toSet()
                if (target == null) data0 else data0.copy(
                    notes = data0.notes.map { it.copy(eventId = target.id, author = it.author ?: b.author) },
                    memos = data0.memos.map { it.copy(eventId = target.id, author = it.author ?: b.author) },
                    emails = data0.emails.map { it.copy(eventId = target.id) },
                ).also { oldIds.size }
            } ?: data0

            val newNotes = Backup.fresh(data.notes, notes.notes.map { it.id }.toSet()) { it.id }
            val newMemos = Backup.fresh(data.memos, memos.items.map { it.id }.toSet()) { it.id }
            val newMail = Backup.fresh(data.emails, mail.items.map { it.id }.toSet()) { it.id }
            var media = 0
            for (n in newNotes) File(temp, n.audioFile).takeIf { it.exists() }?.let { it.copyTo(notes.audioFile(n), overwrite = true); media++ }
            for (m in newMemos) m.imageFile?.let { f -> File(temp, f).takeIf { it.exists() }?.let { it.copyTo(File(memos.dir, f), overwrite = true); media++ } }
            notes.addAll(newNotes)
            memos.addAll(newMemos)
            mail.addAll(newMail)

            val s = data.settings
            if (Profile.name.isBlank() && s.profileName.isNotBlank()) Profile.setName(context, s.profileName)
            if (Calendars.enabledAccounts == null && s.enabledAccounts != null) Calendars.setAccounts(context, s.enabledAccounts)
            if (s.syncNotes && !Calendars.syncNotes) Calendars.setSyncNotes(context, true)
            val addedFeeds = Calendars.adoptFeeds(context, s.feeds)
            return Report(newNotes.size, newMemos.size, newMail.size, addedFeeds, media, true)
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun currentSettings() = BackupSettings(
        profileName = Profile.name, feeds = Calendars.feeds, enabledAccounts = Calendars.enabledAccounts,
        syncNotes = Calendars.syncNotes, ocrLang = app.plait.ui.HomeState.ocrLang.name,
    )

    fun summary(r: Report, verb: String): String {
        val parts = buildList {
            if (r.notes > 0) add("${r.notes} voice note${if (r.notes == 1) "" else "s"}")
            if (r.memos > 0) add("${r.memos} written or photo note${if (r.memos == 1) "" else "s"}")
            if (r.emails > 0) add("${r.emails} email${if (r.emails == 1) "" else "s"}")
            if (r.feeds > 0) add("${r.feeds} calendar link${if (r.feeds == 1) "" else "s"}")
        }
        return if (parts.isEmpty()) "$verb. Nothing new to add." else "$verb ${parts.joinToString(", ")}."
    }
}
