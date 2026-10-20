package app.plait.data.memo

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class MemoKind { Text, Photo, Checklist }

/**
 * A written or photographed note on a meeting. For a photo, [text] is what the phone read out of the
 * picture (a whiteboard, a printed agenda, a page of handwriting), and [imageFile] is the picture itself.
 */
data class Memo(
    val id: String,
    val eventId: String,
    val kind: MemoKind,
    val createdAt: Long,
    val text: String,
    val imageFile: String? = null,
    val author: String? = null,
)

object MemoJson {
    fun encode(items: List<Memo>): String {
        val arr = JSONArray()
        for (m in items) {
            arr.put(
                JSONObject().put("id", m.id).put("eventId", m.eventId).put("kind", m.kind.name)
                    .put("createdAt", m.createdAt).put("text", m.text).also { o -> m.imageFile?.let { o.put("imageFile", it) }; m.author?.let { o.put("author", it) } }
            )
        }
        return JSONObject().put("version", 1).put("memos", arr).toString()
    }

    fun decode(json: String): List<Memo> {
        val arr = JSONObject(json).optJSONArray("memos") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Memo(
                o.getString("id"), o.getString("eventId"),
                runCatching { MemoKind.valueOf(o.optString("kind")) }.getOrDefault(MemoKind.Text),
                o.optLong("createdAt"), o.optString("text"), o.optString("imageFile").takeIf { it.isNotBlank() }, o.optString("author").takeIf { it.isNotBlank() },
            )
        }
    }
}

/** Written notes and photos live in app-private storage, like voice notes. Nothing leaves the phone. */
class MemoStore private constructor(val dir: File) {
    private val index = File(dir, "memos.json")
    val items = mutableStateListOf<Memo>()

    init {
        dir.mkdirs()
        if (index.exists()) runCatching { items.addAll(MemoJson.decode(index.readText()).sortedByDescending { it.createdAt }) }
    }

    fun forEvent(eventId: String): List<Memo> = items.filter { it.eventId == eventId }
    fun image(memo: Memo): File? = memo.imageFile?.let { File(dir, it) }
    fun newImageFile(id: String): File = File(dir, "$id.jpg")

    @Synchronized fun add(memo: Memo) { items.add(0, memo); persist() }

    @Synchronized fun addAll(list: List<Memo>) {
        if (list.isEmpty()) return
        items.addAll(list)
        val sorted = items.sortedByDescending { it.createdAt }
        items.clear(); items.addAll(sorted)
        persist()
    }

    @Synchronized fun update(memo: Memo) {
        val i = items.indexOfFirst { it.id == memo.id }
        if (i >= 0) { items[i] = memo; persist() }
    }

    @Synchronized fun delete(memo: Memo) {
        items.remove(memo)
        app.plait.data.sync.Tombstones.mark(memo.id)
        image(memo)?.delete()
        persist()
    }

    private fun persist() {
        val tmp = File(dir, "memos.json.tmp")
        tmp.writeText(MemoJson.encode(items.toList()))
        if (!tmp.renameTo(index)) { index.writeText(tmp.readText()); tmp.delete() }
    }

    companion object {
        @Volatile private var instance: MemoStore? = null
        fun get(context: Context): MemoStore =
            instance ?: synchronized(this) {
                instance ?: MemoStore(File(context.applicationContext.filesDir, "memos")).also { instance = it }
            }
    }
}
