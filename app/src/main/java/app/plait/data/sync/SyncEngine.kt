package app.plait.data.sync

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import app.plait.data.NoteStore
import app.plait.data.backup.Backup
import app.plait.data.backup.BackupData
import app.plait.data.backup.BackupSettings
import app.plait.data.context.ContextStore
import app.plait.data.crypto.Crypt
import app.plait.data.followups.FollowupState
import app.plait.data.memo.MemoStore
import app.plait.data.prep.PrepStore
import app.plait.diag.Diag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.util.UUID

/**
 * Sync between your phones with no server of ours. Pick a folder that a cloud app you already use (Google Drive, Dropbox,
 * Nextcloud, Syncthing…) keeps in step across your phones. Each phone writes one sealed file there and reads the others'.
 * Files are encrypted on the phone with your passphrase (AES-256), so the cloud only ever holds data it cannot read.
 * Merging only adds and applies deletions, so a sync cannot lose a note.
 */
object SyncEngine {
    data class Result(val imported: Int, val removed: Int, val wrote: Boolean, val error: String?)

    var status by mutableStateOf("")
    var running by mutableStateOf(false)
    var folderName by mutableStateOf<String?>(null)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastRequest = 0L
    private const val PREFIX = "plait-sync-"
    private const val SUFFIX = ".plaitsync"

    private fun prefs(c: Context) = c.getSharedPreferences("plait", Context.MODE_PRIVATE)

    fun tree(c: Context): Uri? = prefs(c).getString("sync_tree", null)?.let(Uri::parse)
    fun includeMedia(c: Context) = prefs(c).getBoolean("sync_media", false)
    fun setIncludeMedia(c: Context, on: Boolean) { prefs(c).edit().putBoolean("sync_media", on).apply() }
    fun configured(c: Context) = tree(c) != null && SecretStore.get(c, "sync_pass") != null

    fun deviceId(c: Context): String =
        prefs(c).getString("device_id", null) ?: UUID.randomUUID().toString().take(8).also { prefs(c).edit().putString("device_id", it).apply() }

    /** Starts syncing with [folder] and [passphrase]. */
    fun connect(context: Context, folder: Uri, passphrase: String) {
        runCatching { context.contentResolver.takePersistableUriPermission(folder, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        prefs(context).edit().putString("sync_tree", folder.toString()).remove("sync_fp").remove("sync_seen").apply()
        SecretStore.put(context, "sync_pass", passphrase)
        folderName = DocumentFile.fromTreeUri(context, folder)?.name
    }

    fun disconnect(context: Context) {
        prefs(context).edit().remove("sync_tree").remove("sync_fp").remove("sync_seen").apply()
        SecretStore.remove(context, "sync_pass")
        folderName = null; status = ""
    }

    /** Syncs in the background, at most once every 90 seconds. */
    fun requestAsync(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        // Only the folder is checked here: the passphrase is a keystore decrypt (binder calls) and callers are often on the
        // main thread, so run() reads it on the IO thread and stops there if sync is not set up.
        if (tree(app) == null) return
        val now = System.currentTimeMillis()
        if (!force && now - lastRequest < 90_000L) return
        lastRequest = now
        scope.launch { run(app) }
    }

    private fun localState(c: Context): SyncState {
        FollowupState.load(c)
        return SyncState(
            NoteStore.get(c).notes.toList(), MemoStore.get(c).items.toList(), ContextStore.get(c).items.toList(),
            Tombstones.all(), FollowupState.done, PrepStore.get(c).entries.toMap(),
        )
    }

    suspend fun run(context: Context): Result = mutex.withLock {
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val treeUri = tree(app)
            val pass = SecretStore.get(app, "sync_pass")
            if (treeUri == null || pass == null) return@withContext Result(0, 0, false, "Sync isn't set up.")
            running = true; status = "Syncing…"
            try {
                val folder = DocumentFile.fromTreeUri(app, treeUri) ?: throw java.io.IOException("The sync folder isn't available.")
                folderName = folder.name
                val me = PREFIX + deviceId(app) + SUFFIX
                val seenPrefs = prefs(app).getStringSet("sync_seen", emptySet()).orEmpty().mapNotNull { s -> s.split('|').takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0L) } }.toMap().toMutableMap()
                var imported = 0; var removed = 0

                // 1. Read what the other phones wrote.
                for (doc in folder.listFiles()) {
                    val name = doc.name ?: continue
                    if (!doc.isFile || !name.startsWith(PREFIX) || !name.endsWith(SUFFIX) || name == me) continue
                    if ((seenPrefs[name] ?: -1L) >= doc.lastModified()) continue
                    val temp = File(app.cacheDir, "sync-" + System.nanoTime())
                    try {
                        val remote = readRemote(app, doc, pass.toCharArray(), temp)
                        val (a, r) = apply(app, remote, temp)
                        imported += a; removed += r
                        seenPrefs[name] = doc.lastModified()
                    } catch (e: Crypt.BadPassphrase) {
                        throw java.io.IOException("Another phone's file couldn't be opened. The passphrase must be the same on every phone.")
                    } finally { temp.deleteRecursively() }
                }
                prefs(app).edit().putStringSet("sync_seen", seenPrefs.map { "${it.key}|${it.value}" }.toSet()).apply()

                // 2. Write this phone's file if anything changed.
                val state = localState(app)
                val fp = SyncMerge.fingerprint(state) + if (includeMedia(app)) "m" else ""
                var wrote = false
                if (prefs(app).getString("sync_fp", null) != fp || folder.findFile(me) == null) {
                    writeOwn(app, folder, me, state, pass.toCharArray())
                    prefs(app).edit().putString("sync_fp", fp).apply()
                    wrote = true
                }
                status = "Synced " + java.text.SimpleDateFormat("h:mm a", java.util.Locale.ENGLISH).format(java.util.Date()) +
                    (if (imported + removed > 0) " · $imported new, $removed removed" else "")
                Result(imported, removed, wrote, null)
            } catch (e: Exception) {
                Diag.log("SYNC", e.message ?: e.javaClass.simpleName)
                status = e.message ?: "Sync failed."
                Result(0, 0, false, status)
            } finally { running = false }
        }
    }

    private fun readRemote(app: Context, doc: DocumentFile, pass: CharArray, temp: File): BackupData {
        val raw = BufferedInputStream(app.contentResolver.openInputStream(doc.uri) ?: throw java.io.IOException("Couldn't open ${doc.name}."))
        return raw.use { input ->
            input.mark(32)
            val head = ByteArray(Crypt.MAGIC.size)
            val n = input.read(head)
            if (n != head.size || !Crypt.isEncrypted(head)) throw java.io.IOException("${doc.name} isn't protected, so it was skipped.")
            Backup.read(Crypt.decrypt(input, pass), temp)
        }
    }

    /** Applies another phone's state to this one. Returns (added, removed). */
    private fun apply(app: Context, remote: BackupData, media: File): Pair<Int, Int> {
        val notes = NoteStore.get(app); val memos = MemoStore.get(app); val mail = ContextStore.get(app)
        val plan = SyncMerge.plan(localState(app), SyncState(remote.notes, remote.memos, remote.emails, remote.deleted, remote.done, remote.prep))
        for (n in plan.addNotes + plan.updateNotes) File(media, n.audioFile).takeIf { it.exists() }?.copyTo(notes.audioFile(n), overwrite = true)
        for (m in plan.addMemos) m.imageFile?.let { f -> File(media, f).takeIf { it.exists() }?.copyTo(File(memos.dir, f), overwrite = true) }
        Tombstones.addAll(plan.newDeleted)
        notes.addAll(plan.addNotes)
        plan.updateNotes.forEach { notes.update(it) }
        memos.addAll(plan.addMemos)
        mail.addAll(plan.addEmails)
        var removed = 0
        for (id in plan.removeIds) {
            notes.notes.firstOrNull { it.id == id }?.let { notes.delete(it); removed++ }
            memos.items.firstOrNull { it.id == id }?.let { memos.delete(it); removed++ }
            mail.items.firstOrNull { it.id == id }?.let { mail.remove(it); removed++ }
        }
        FollowupState.mergeDone(app, plan.newDone)
        plan.newPrep.forEach { (id, e) -> PrepStore.get(app).put(id, e) }
        return (plan.addNotes.size + plan.addMemos.size + plan.addEmails.size + plan.updateNotes.size) to removed
    }

    private fun writeOwn(app: Context, folder: DocumentFile, name: String, state: SyncState, pass: CharArray) {
        val notes = NoteStore.get(app); val memos = MemoStore.get(app)
        val data = BackupData(
            System.currentTimeMillis(), app.getSharedPreferences("plait", Context.MODE_PRIVATE).getString("device_label", null) ?: android.os.Build.MODEL.orEmpty(),
            BackupSettings(), state.notes, state.memos, state.emails, state.deleted, state.done, state.prep,
        )
        val media = if (includeMedia(app)) state.notes.map { it.audioFile to notes.audioFile(it) } + state.memos.mapNotNull { m -> m.imageFile?.let { it to File(memos.dir, it) } } else emptyList()
        val temp = File(app.cacheDir, "sync-out-" + System.nanoTime())
        try {
            temp.outputStream().use { raw -> Crypt.encrypt(raw, pass).use { Backup.write(it, data, media) } }
            val target = folder.findFile(name) ?: folder.createFile("application/octet-stream", name) ?: throw java.io.IOException("Couldn't create the sync file.")
            (app.contentResolver.openOutputStream(target.uri, "wt") ?: throw java.io.IOException("Couldn't write the sync file.")).use { out -> temp.inputStream().use { it.copyTo(out) } }
        } finally { temp.delete() }
    }
}
