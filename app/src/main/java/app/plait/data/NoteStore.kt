package app.plait.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One phrase of a transcript. [conf] is the speech engine's own confidence, 0..1; low values are shown as uncertain. */
data class NoteLine(val atMs: Long, val text: String, val conf: Float = 1f)

data class SavedNote(
    val id: String,
    val eventId: String,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val audioFile: String,
    val lines: List<NoteLine>,
    val bookmarksMs: List<Long>,
    /** What the phone's built-in AI wrote about the note, when it has been asked. */
    val ai: String? = null,
    /** Which engine wrote [ai] ("Gemini Nano", "Qwen 2.5 · 1.5B"). */
    val aiBy: String? = null,
    /** Who recorded it, when it came from a teammate. Null for your own. */
    val author: String? = null,
)

/** JSON encoding kept separate from file handling so it can be unit tested. */
object NoteJson {
    fun encode(notes: List<SavedNote>): String {
        val arr = JSONArray()
        for (n in notes) {
            arr.put(
                JSONObject()
                    .put("id", n.id)
                    .put("eventId", n.eventId)
                    .put("title", n.title)
                    .put("createdAt", n.createdAt)
                    .put("durationMs", n.durationMs)
                    .put("audioFile", n.audioFile)
                    .put("lines", JSONArray().also { a ->
                        n.lines.forEach { a.put(JSONObject().put("atMs", it.atMs).put("text", it.text).put("conf", it.conf.toDouble())) }
                    })
                    .put("bookmarksMs", JSONArray().also { a -> n.bookmarksMs.forEach { a.put(it) } })
                    .also { o -> n.ai?.let { o.put("ai", it) }; n.aiBy?.let { o.put("aiBy", it) }; n.author?.let { o.put("author", it) } }
            )
        }
        return JSONObject().put("version", 1).put("notes", arr).toString()
    }

    fun decode(json: String): List<SavedNote> {
        val root = JSONObject(json)
        val arr = root.optJSONArray("notes") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val lines = o.optJSONArray("lines")
            val marks = o.optJSONArray("bookmarksMs")
            SavedNote(
                id = o.getString("id"),
                eventId = o.getString("eventId"),
                title = o.optString("title"),
                createdAt = o.getLong("createdAt"),
                durationMs = o.getLong("durationMs"),
                audioFile = o.getString("audioFile"),
                lines = List(lines?.length() ?: 0) { k ->
                    lines!!.getJSONObject(k).let { NoteLine(it.getLong("atMs"), it.getString("text"), it.optDouble("conf", 1.0).toFloat()) }
                },
                bookmarksMs = List(marks?.length() ?: 0) { k -> marks!!.getLong(k) },
                ai = o.optString("ai").takeIf { it.isNotBlank() },
                aiBy = o.optString("aiBy").takeIf { it.isNotBlank() },
                author = o.optString("author").takeIf { it.isNotBlank() },
            )
        }
    }
}

/** Notes live in app-private storage: one JSON index plus one WAV per note. Nothing leaves the phone. */
class NoteStore private constructor(private val dir: File) {
    private val index = File(dir, "notes.json")

    /** Newest first. Backed by Compose state so screens update when a note is saved. */
    val notes = mutableStateListOf<SavedNote>()

    init {
        dir.mkdirs()
        if (index.exists()) {
            runCatching { notes.addAll(NoteJson.decode(index.readText()).sortedByDescending { it.createdAt }) }
        }
    }

    fun audioFile(note: SavedNote): File = File(dir, note.audioFile)

    fun newAudioFile(id: String): File = File(dir, "$id.wav")

    fun forEvent(eventId: String): List<SavedNote> = notes.filter { it.eventId == eventId }

    @Synchronized
    fun add(note: SavedNote) {
        notes.add(0, note)
        persist()
    }

    /** Adds many notes at once (an import), newest first. */
    @Synchronized
    fun addAll(list: List<SavedNote>) {
        if (list.isEmpty()) return
        notes.addAll(list)
        val sorted = notes.sortedByDescending { it.createdAt }
        notes.clear(); notes.addAll(sorted)
        persist()
    }

    /** Replaces a saved note with a changed copy (for example once the AI summary arrives). */
    @Synchronized
    fun update(note: SavedNote) {
        val i = notes.indexOfFirst { it.id == note.id }
        if (i >= 0) { notes[i] = note; persist() }
    }

    @Synchronized
    fun delete(note: SavedNote) {
        notes.remove(note)
        app.plait.data.sync.Tombstones.mark(note.id)
        File(dir, note.audioFile).delete()
        persist()
    }

    private fun persist() {
        val tmp = File(dir, "notes.json.tmp")
        tmp.writeText(NoteJson.encode(notes.toList()))
        if (!tmp.renameTo(index)) {
            index.writeText(tmp.readText())
            tmp.delete()
        }
    }

    companion object {
        @Volatile private var instance: NoteStore? = null

        /** The store if it has been opened already (it always has by the time a screen asks for a meeting). */
        fun peek(): NoteStore? = instance

        fun get(context: Context): NoteStore =
            instance ?: synchronized(this) {
                instance ?: NoteStore(File(context.applicationContext.filesDir, "notes")).also { instance = it }
            }
    }
}
