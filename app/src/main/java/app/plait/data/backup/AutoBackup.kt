package app.plait.data.backup

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import app.plait.data.NoteStore
import app.plait.data.memo.MemoStore
import app.plait.data.sync.SecretStore
import app.plait.data.sync.SyncEngine
import app.plait.diag.Diag

/**
 * Backup upkeep: remembers when the last backup was made (by hand or scheduled), says when it is old, and, if switched on,
 * saves an encrypted backup into the sync folder about once a week. There is no background job: it is checked when Plait is
 * opened and left, which is when new notes appear anyway.
 */
object AutoBackup {
    private const val DAY = 24 * 60 * 60_000L
    private const val FILE = "plait-backup-auto.zip"

    private fun prefs(c: Context) = c.getSharedPreferences("plait", Context.MODE_PRIVATE)

    fun last(c: Context): Long = prefs(c).getLong("backup_last", 0L)
    fun markDone(c: Context) { prefs(c).edit().putLong("backup_last", System.currentTimeMillis()).apply() }
    fun enabled(c: Context) = prefs(c).getBoolean("backup_auto", false)
    fun setEnabled(c: Context, on: Boolean) { prefs(c).edit().putBoolean("backup_auto", on).apply() }
    fun canRun(c: Context) = SyncEngine.configured(c)

    private fun hasData(c: Context) = NoteStore.get(c).notes.isNotEmpty() || MemoStore.get(c).items.isNotEmpty()

    /** Whole days since the last backup, 0 if there never was one, or null when no reminder is due (nothing to lose, or one was made lately). */
    fun staleDays(c: Context, now: Long = System.currentTimeMillis()): Int? {
        if (!hasData(c)) return null
        val p = prefs(c)
        val last = p.getLong("backup_last", 0L)
        // Someone who never backed up is reminded 30 days after Plait first looked, not on day one.
        val since = if (last > 0) last else p.getLong("backup_base", 0L).takeIf { it > 0 } ?: run { p.edit().putLong("backup_base", now).apply(); now }
        if (now - since < 30 * DAY) return null
        return if (last > 0) ((now - last) / DAY).toInt() else 0
    }

    /** Show the reminder at most once a week. */
    fun shouldRemind(c: Context, now: Long = System.currentTimeMillis()): Boolean =
        staleDays(c, now) != null && now - prefs(c).getLong("backup_nag_at", 0L) > 7 * DAY

    fun reminded(c: Context) { prefs(c).edit().putLong("backup_nag_at", System.currentTimeMillis()).apply() }

    /** Writes the encrypted backup into the sync folder, replacing the previous one only once the new one is whole. Call off the main thread. */
    fun backUpNow(c: Context): Boolean = runCatching {
        val tree = SyncEngine.tree(c) ?: return false
        val pass = SecretStore.get(c, "sync_pass") ?: return false
        val folder = DocumentFile.fromTreeUri(c, tree) ?: return false
        val tmp = "plait-backup-auto-new.zip"
        folder.findFile(tmp)?.delete()
        val doc = folder.createFile("application/zip", tmp) ?: return false
        BackupManager.export(c, doc.uri, SyncEngine.includeMedia(c), pass)
        folder.findFile(FILE)?.delete()
        doc.renameTo(FILE)
        true
    }.onFailure { Diag.log("BACKUP", "scheduled backup failed: ${it.message}") }.getOrDefault(false)

    /** Called when the app is opened or left: makes the weekly backup if it is switched on and due. */
    fun runIfDue(c: Context) {
        if (enabled(c) && canRun(c) && System.currentTimeMillis() - last(c) > 7 * DAY) backUpNow(c)
    }
}
