package app.plait.data.prep

import app.plait.ai.NoteAiPrompt
import app.plait.data.CalEvent
import app.plait.data.SavedNote
import app.plait.data.brief.BriefBuilder
import app.plait.data.followups.Followup
import app.plait.data.followups.Followups
import app.plait.data.memo.Memo

/** What has happened in a recurring meeting's series since it last met. */
data class SinceLast(val lastMs: Long, val decisions: List<String>, val stillOpen: List<Followup>) {
    val isEmpty: Boolean get() = decisions.isEmpty() && stillOpen.isEmpty()

    companion object {
        /** Notes of earlier meetings with the same title (numbers ignored): their decisions, and follow-ups not yet done. */
        fun compute(ev: CalEvent, notes: List<SavedNote>, memos: List<Memo>, isOpen: (String) -> Boolean): SinceLast? {
            val key = BriefBuilder.seriesKey(ev.title)
            if (key.isBlank()) return null
            val before = if (ev.startMs > 0) ev.startMs else Long.MAX_VALUE
            val earlier = notes.filter { it.eventId != ev.id && it.createdAt < before && BriefBuilder.seriesKey(it.title) == key }
            if (earlier.isEmpty()) return null
            val ids = earlier.map { it.eventId }.toSet()
            val open = Followups.collect(earlier, memos.filter { it.eventId in ids }).filter { isOpen(it.id) }
            val latest = earlier.maxByOrNull { it.createdAt }!!
            val decisions = latest.ai?.let { NoteAiPrompt.parse(it).decisions }.orEmpty()
            return SinceLast(latest.createdAt, decisions, open)
        }
    }
}
