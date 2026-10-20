package app.plait.data.sync

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Remembers what was deleted here, so that deleting a note on one phone removes it on the others instead of it coming back. */
object Tombstones {
    private var file: File? = null
    private var map = HashMap<String, Long>()

    @Synchronized fun init(context: Context) {
        if (file != null) return
        file = File(context.applicationContext.filesDir, "deleted.json")
        runCatching { file!!.takeIf { it.exists() }?.let { f -> JSONObject(f.readText()).let { o -> o.keys().forEach { k -> map[k] = o.getLong(k) } } } }
    }

    @Synchronized fun mark(id: String) { map[id] = System.currentTimeMillis(); save() }

    @Synchronized fun addAll(entries: Map<String, Long>) { if (entries.isNotEmpty()) { map.putAll(entries); save() } }

    @Synchronized fun all(): Map<String, Long> = HashMap(map)

    private fun save() {
        val f = file ?: return
        runCatching { f.writeText(JSONObject(map as Map<*, *>).toString()) }
    }
}
