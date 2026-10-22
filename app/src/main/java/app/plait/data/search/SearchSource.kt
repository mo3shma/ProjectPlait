package app.plait.data.search

import android.content.Context
import app.plait.data.Calendars
import app.plait.data.NoteStore
import app.plait.data.context.ContextStore
import app.plait.data.memo.MemoKind
import app.plait.data.memo.MemoStore

/** Gathers everything on the phone that search should cover. */
object SearchSource {
    fun collect(context: Context, nowMs: Long = System.currentTimeMillis()): List<Doc> {
        val notes = NoteStore.get(context).notes
        val memos = MemoStore.get(context).items
        val mail = ContextStore.get(context).items
        val events = Calendars.events
        val titleOf = events.associate { it.id to it.title } + notes.associate { it.eventId to it.title }
        fun title(id: String) = titleOf[id] ?: "Meeting"
        val out = ArrayList<Doc>()
        for (n in notes) {
            out.add(Doc("v:${n.id}", DocKind.Voice, n.eventId, title(n.eventId), n.lines.joinToString(" ") { it.text } + (n.ai?.let { "\n$it" } ?: ""), n.createdAt))
        }
        for (m in memos) {
            if (m.text.isNotBlank()) out.add(Doc("m:${m.id}", if (m.kind == MemoKind.Photo) DocKind.Photo else DocKind.Memo, m.eventId, title(m.eventId), if (m.kind == MemoKind.Checklist) listOfNotNull(m.title, app.plait.data.memo.Checklist.plain(m.text)).joinToString("\n") else m.text, m.createdAt))
        }
        for (e in mail) {
            out.add(Doc("e:${e.id}", DocKind.Email, e.eventId, e.subject.ifBlank { title(e.eventId) }, e.body.take(6000), e.addedAt))
        }
        val from = nowMs - 90L * 86_400_000L
        val to = nowMs + 60L * 86_400_000L
        for (ev in events) {
            if (ev.startMs in from..to) {
                val text = listOfNotNull(ev.description, ev.location, ev.guests.filter { !it.self }.joinToString(", ") { it.label }.ifBlank { null }).joinToString(". ")
                out.add(Doc("c:${ev.id}", DocKind.Event, ev.id, ev.title, text, ev.startMs))
            }
        }
        return out
    }
}
