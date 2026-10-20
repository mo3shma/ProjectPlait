package app.plait.data.backup

import app.plait.data.NoteJson
import app.plait.data.SavedNote
import app.plait.data.context.ContextItem
import app.plait.data.context.ContextJson
import app.plait.data.ics.Feed
import app.plait.data.ics.Feeds
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoJson
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The settings worth carrying to another device. Calendar ids differ per device, so those are not included. */
data class BackupSettings(
    val profileName: String = "",
    val feeds: List<Feed> = emptyList(),
    /** Accounts chosen to show, or null if the user never chose. */
    val enabledAccounts: Set<String>? = null,
    val syncNotes: Boolean = false,
    val ocrLang: String = "Auto",
)

data class BackupData(
    val createdAt: Long,
    val deviceLabel: String,
    val settings: BackupSettings,
    val notes: List<SavedNote>,
    val memos: List<Memo>,
    val emails: List<ContextItem>,
    /** For syncing between phones: what was deleted, which follow-ups are done, and prep text. */
    val deleted: Map<String, Long> = emptyMap(),
    val done: Set<String> = emptySet(),
    val prep: Map<String, app.plait.data.prep.PrepEntry> = emptyMap(),
    /** Present when this file is one meeting's notes shared with a teammate. */
    val bundle: BundleMeta? = null,
)

/** Which meeting a shared bundle is about, so the receiver can attach it to their own copy of that meeting. */
data class BundleMeta(val title: String, val startMs: Long, val author: String)

/**
 * A Plait backup is one zip file: JSON for settings, notes, written/photo notes and attached emails, and
 * optionally the recordings and photos under media/. It is the low-cost way to move to a new phone or keep two
 * phones in step until cloud sync exists. Nothing is encrypted, so keep the file somewhere private.
 */
object Backup {
    const val VERSION = 1

    fun write(out: OutputStream, data: BackupData, media: List<Pair<String, File>>) {
        ZipOutputStream(out.buffered()).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            entry(
                "manifest.json",
                JSONObject().put("version", VERSION).put("createdAt", data.createdAt).put("device", data.deviceLabel)
                    .put("notes", data.notes.size).put("memos", data.memos.size).put("emails", data.emails.size).toString(),
            )
            entry("settings.json", settingsJson(data.settings))
            entry("notes.json", NoteJson.encode(data.notes))
            entry("memos.json", MemoJson.encode(data.memos))
            entry("emails.json", ContextJson.encode(data.emails))
            entry("extras.json", extrasJson(data))
            data.bundle?.let { entry("bundle.json", JSONObject().put("title", it.title).put("startMs", it.startMs).put("author", it.author).toString()) }
            for ((name, file) in media) {
                if (!file.exists()) continue
                zip.putNextEntry(ZipEntry("media/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    class Unreadable(message: String) : Exception(message)

    /**
     * Reads a backup. Media files are unpacked into [mediaDir] (created if needed); the caller moves what it
     * wants from there. Throws [Unreadable] for a file that is not a Plait backup.
     */
    fun read(input: InputStream, mediaDir: File): BackupData {
        mediaDir.mkdirs()
        val texts = HashMap<String, String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name
                if (name.startsWith("media/")) {
                    val safe = File(name.removePrefix("media/")).name // never write outside mediaDir
                    if (safe.isNotBlank() && !e.isDirectory) File(mediaDir, safe).outputStream().use { zip.copyTo(it) }
                } else if (!e.isDirectory) {
                    texts[name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
            }
        }
        val manifest = texts["manifest.json"] ?: throw Unreadable("This isn't a Plait backup.")
        val m = try { JSONObject(manifest) } catch (e: Exception) { throw Unreadable("This isn't a Plait backup.") }
        if (m.optInt("version", 0) !in 1..VERSION) throw Unreadable("This backup is from a newer version of Plait. Update the app and try again.")
        return try {
            BackupData(
                createdAt = m.optLong("createdAt"), deviceLabel = m.optString("device"),
                settings = texts["settings.json"]?.let { settingsFrom(it) } ?: BackupSettings(),
                notes = texts["notes.json"]?.let { NoteJson.decode(it) }.orEmpty(),
                memos = texts["memos.json"]?.let { MemoJson.decode(it) }.orEmpty(),
                emails = texts["emails.json"]?.let { ContextJson.decode(it) }.orEmpty(),
            ).let { d -> texts["extras.json"]?.let { extrasFrom(d, it) } ?: d }
                .let { d -> texts["bundle.json"]?.let { j -> JSONObject(j).let { o -> d.copy(bundle = BundleMeta(o.optString("title"), o.optLong("startMs"), o.optString("author"))) } } ?: d }
        } catch (e: Exception) {
            throw Unreadable("This backup file is damaged.")
        }
    }

    fun extrasJson(d: BackupData): String = JSONObject()
        .put("deleted", JSONObject(d.deleted as Map<*, *>))
        .put("done", JSONArray(d.done.toList()))
        .put("prep", JSONObject().also { o -> d.prep.forEach { (id, e) -> o.put(id, JSONObject().put("text", e.text).put("by", e.by).put("at", e.at)) } })
        .toString()

    fun extrasFrom(d: BackupData, json: String): BackupData {
        val o = JSONObject(json)
        val del = o.optJSONObject("deleted")
        val done = o.optJSONArray("done")
        val prep = o.optJSONObject("prep")
        return d.copy(
            deleted = del?.keys()?.asSequence()?.associateWith { del.getLong(it) } ?: emptyMap(),
            done = done?.let { a -> List(a.length()) { a.getString(it) }.toSet() } ?: emptySet(),
            prep = prep?.keys()?.asSequence()?.associateWith { k -> prep.getJSONObject(k).let { app.plait.data.prep.PrepEntry(it.getString("text"), it.optString("by"), it.optLong("at")) } } ?: emptyMap(),
        )
    }

    fun settingsJson(s: BackupSettings): String = JSONObject()
        .put("profileName", s.profileName)
        .put("feeds", JSONArray(Feeds.encode(s.feeds.filter { !it.url.startsWith("file://") })))
        .put("syncNotes", s.syncNotes).put("ocrLang", s.ocrLang)
        .also { o -> s.enabledAccounts?.let { o.put("enabledAccounts", JSONArray(it.toList())) } }
        .toString()

    fun settingsFrom(json: String): BackupSettings {
        val o = JSONObject(json)
        val accts = o.optJSONArray("enabledAccounts")
        return BackupSettings(
            profileName = o.optString("profileName"),
            feeds = Feeds.decode(o.optJSONArray("feeds")?.toString()),
            enabledAccounts = accts?.let { a -> List(a.length()) { a.getString(it) }.toSet() },
            syncNotes = o.optBoolean("syncNotes"),
            ocrLang = o.optString("ocrLang", "Auto"),
        )
    }

    /** Items in [incoming] whose id is not in [existing]: what an import should add. */
    fun <T> fresh(incoming: List<T>, existing: Set<String>, id: (T) -> String): List<T> =
        incoming.filter { id(it) !in existing }.distinctBy(id)
}
