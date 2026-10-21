package app.plait.data.followups

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.plait.ai.NoteAiPrompt
import app.plait.data.SavedNote
import app.plait.data.brief.Summarizer
import app.plait.data.memo.Memo

/** Something you said you would do, found in a note. */
data class Followup(val id: String, val text: String, val eventId: String, val title: String, val whenMs: Long, val byAi: Boolean)

object Followups {
    /** Follow-ups from voice notes (the AI's list if it wrote one, else phrases like "we need to…") and from written or photo notes. */
    fun collect(notes: List<SavedNote>, memos: List<Memo>): List<Followup> {
        val out = ArrayList<Followup>()
        for (n in notes) {
            val ai = n.ai?.let { NoteAiPrompt.parse(it).followUps }.orEmpty()
            val items = ai.ifEmpty { Summarizer.actionItems(n.lines.map { it.text }) }
            items.forEach { out.add(Followup(id("v:${n.id}", it), it.trim(), n.eventId, n.title, n.createdAt, ai.isNotEmpty())) }
        }
        for (m in memos) {
            val lines = m.text.lines().map { app.plait.data.memo.Checklist.stripDue(it) }.filter { it.isNotEmpty() }
            val ticked = lines.mapNotNull { checkbox.matchEntire(it)?.groupValues?.get(1)?.trim() }
            val found = ticked.ifEmpty { Summarizer.actionItems(lines) }
            found.forEach { out.add(Followup(id("m:${m.id}", it), it, m.eventId, "", m.createdAt, false)) }
        }
        return out.distinctBy { it.id }.sortedByDescending { it.whenMs }
    }

    private val checkbox = Regex("""^(?:[-*•]\s*)?(?:\[ ?\]|todo:?|to do:?|action:?|follow[- ]?up:?)\s*(.+)$""", RegexOption.IGNORE_CASE)

    fun id(source: String, text: String): String =
        (source + "|" + text.lowercase().replace(Regex("\\W+"), " ").trim()).hashCode().toUInt().toString(16)
}

/** Which follow-ups are ticked off or put off until later. Kept on the phone. */
object FollowupState {
    var done by mutableStateOf<Set<String>>(emptySet()); private set
    var snoozed by mutableStateOf<Map<String, Long>>(emptyMap()); private set
    private var loaded = false

    private fun prefs(c: Context) = c.getSharedPreferences("plait_followups", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        done = p.getStringSet("done", emptySet()).orEmpty().toSet()
        snoozed = p.getStringSet("snoozed", emptySet()).orEmpty().mapNotNull { s -> s.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: 0L) } }.toMap()
    }

    fun isOpen(id: String, nowMs: Long): Boolean = id !in done && (snoozed[id] ?: 0L) <= nowMs

    fun markDone(context: Context, id: String) {
        done = done + id
        prefs(context).edit().putStringSet("done", done).apply()
    }

    /** Adds follow-ups that another phone ticked off. */
    fun mergeDone(context: Context, ids: Set<String>) {
        if (ids.isEmpty()) return
        done = done + ids
        prefs(context).edit().putStringSet("done", done).apply()
    }

    fun snooze(context: Context, id: String, untilMs: Long) {
        snoozed = snoozed + (id to untilMs)
        prefs(context).edit().putStringSet("snoozed", snoozed.map { "${it.key}=${it.value}" }.toSet()).apply()
    }
}
