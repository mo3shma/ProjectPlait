package app.plait.data.context

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** An email or note the user attached to a meeting. Stays on the phone. */
data class ContextItem(
    val id: String,
    val eventId: String,
    val subject: String,
    val from: String,
    val to: String,
    val dateMs: Long?,
    val body: String,
    val addedAt: Long,
    /** "shared", "pasted" or "file". */
    val source: String,
    val attachments: List<String> = emptyList(),
    /** Text written by the phone's built-in AI when the user asked for it. */
    val aiSummary: String? = null,
)

object ContextJson {
    fun encode(items: List<ContextItem>): String {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("eventId", it.eventId).put("subject", it.subject).put("from", it.from)
                    .put("to", it.to).put("dateMs", it.dateMs ?: JSONObject.NULL).put("body", it.body)
                    .put("addedAt", it.addedAt).put("source", it.source)
                    .put("attachments", JSONArray(it.attachments)).put("aiSummary", it.aiSummary ?: JSONObject.NULL)
            )
        }
        return JSONObject().put("version", 1).put("items", arr).toString()
    }

    fun decode(json: String): List<ContextItem> {
        val arr = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val att = o.optJSONArray("attachments")
            ContextItem(
                id = o.getString("id"), eventId = o.getString("eventId"), subject = o.optString("subject"),
                from = o.optString("from"), to = o.optString("to"),
                dateMs = if (o.isNull("dateMs")) null else o.getLong("dateMs"),
                body = o.optString("body"), addedAt = o.getLong("addedAt"), source = o.optString("source", "pasted"),
                attachments = List(att?.length() ?: 0) { k -> att!!.getString(k) },
                aiSummary = if (o.isNull("aiSummary")) null else o.optString("aiSummary").ifBlank { null },
            )
        }
    }
}

/** App-private store for attached emails: one small JSON file. */
class ContextStore private constructor(private val file: File) {
    val items = mutableStateListOf<ContextItem>()

    init {
        if (file.exists()) runCatching { items.addAll(ContextJson.decode(file.readText()).sortedByDescending { it.addedAt }) }
    }

    fun forEvent(eventId: String): List<ContextItem> = items.filter { it.eventId == eventId }

    fun countFor(eventId: String): Int = items.count { it.eventId == eventId }

    @Synchronized fun add(item: ContextItem) { items.add(0, item); persist() }

    @Synchronized fun addAll(list: List<ContextItem>) {
        if (list.isEmpty()) return
        items.addAll(list)
        val sorted = items.sortedByDescending { it.addedAt }
        items.clear(); items.addAll(sorted)
        persist()
    }

    @Synchronized fun remove(item: ContextItem) { items.removeAll { it.id == item.id }; app.plait.data.sync.Tombstones.mark(item.id); persist() }

    /** One AI answer covers the whole meeting, so it is kept on the newest email and cleared from the rest. */
    @Synchronized fun setEventAi(eventId: String, text: String?) {
        var first = true
        for (i in items.indices) {
            if (items[i].eventId != eventId) continue
            items[i] = items[i].copy(aiSummary = if (first) text else null)
            first = false
        }
        persist()
    }

    private fun persist() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(ContextJson.encode(items.toList()))
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    companion object {
        @Volatile private var instance: ContextStore? = null
        fun get(context: Context): ContextStore = instance ?: synchronized(this) {
            instance ?: ContextStore(File(context.applicationContext.filesDir, "context.json")).also { instance = it }
        }
    }
}
