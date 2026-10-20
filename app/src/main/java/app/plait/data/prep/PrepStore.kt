package app.plait.data.prep

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject
import java.io.File

/** The prep text kept for a meeting, who wrote it (an AI, the rules, or you), and when. */
data class PrepEntry(val text: String, val by: String, val at: Long)

class PrepStore private constructor(private val file: File) {
    val entries = mutableStateMapOf<String, PrepEntry>()

    init {
        if (file.exists()) runCatching {
            val o = JSONObject(file.readText())
            o.keys().forEach { id -> o.getJSONObject(id).let { entries[id] = PrepEntry(it.getString("text"), it.optString("by"), it.optLong("at")) } }
        }
    }

    @Synchronized fun put(eventId: String, entry: PrepEntry) { entries[eventId] = entry; persist() }
    @Synchronized fun clear(eventId: String) { entries.remove(eventId); persist() }

    private fun persist() {
        val o = JSONObject()
        entries.forEach { (id, e) -> o.put(id, JSONObject().put("text", e.text).put("by", e.by).put("at", e.at)) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) { file.writeText(tmp.readText()); tmp.delete() }
    }

    companion object {
        @Volatile private var instance: PrepStore? = null
        fun get(context: Context): PrepStore = instance ?: synchronized(this) {
            instance ?: PrepStore(File(context.applicationContext.filesDir, "prep.json")).also { instance = it }
        }
    }
}
