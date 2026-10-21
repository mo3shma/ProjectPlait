package app.plait.data.tasks

import android.content.Context
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.memo.Checklist
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoKind
import app.plait.data.memo.MemoStore
import app.plait.data.sync.SyncEngine
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** A task listed but not cleared: a suggestion waiting on the user, or an open line of a checklist. */
data class AttentionItem(
    val id: String,
    val kind: Kind,
    val text: String,
    val eventId: String,
    val eventTitle: String,
    val sinceMs: Long,
    val idea: TaskIdea? = null,
    val memoId: String? = null,
    val index: Int = -1,
) { enum class Kind { Suggestion, Open } }

object AttentionTasks {
    /** Everything that needs attention, most stale first. */
    fun collect(ideas: List<TaskIdea>, memos: List<Memo>, eventOf: (String) -> CalEvent?, nowMs: Long): List<AttentionItem> {
        fun event(id: String) = if (id.isBlank()) null else eventOf(id)
        fun since(e: CalEvent?, fallback: Long) = e?.startMs?.takeIf { it > 0 } ?: fallback
        val open = memos.filter { it.kind == MemoKind.Checklist }.flatMap { m ->
            val e = event(m.eventId)
            // Checklists of meetings still to come are agendas, not overdue tasks.
            if (e != null && e.startMs > nowMs) return@flatMap emptyList()
            Checklist.parse(m.text).mapIndexedNotNull { i, it ->
                if (it.done) null
                else AttentionItem("memo:${m.id}:$i", AttentionItem.Kind.Open, it.text, m.eventId, e?.title.orEmpty(), since(e, m.createdAt), memoId = m.id, index = i)
            }
        }
        val suggestions = ideas.filter { i -> open.none { it.eventId == i.eventId && it.text.equals(i.text, true) } }.map { i ->
            val e = event(i.eventId)
            AttentionItem("idea:${i.id}", AttentionItem.Kind.Suggestion, i.text, i.eventId, e?.title.orEmpty(), since(e, i.createdAt), idea = i)
        }
        return (suggestions + open).sortedWith(compareBy({ it.sinceMs }, { it.text.lowercase() }))
    }

    fun now(context: Context, nowMs: Long = System.currentTimeMillis()): List<AttentionItem> {
        TaskIdeas.load(context)
        return collect(TaskIdeas.items.toList(), MemoStore.get(context).items.toList(), Calendars::find, nowMs)
    }

    /** The checklist text with line [index] ticked, or null if that line is no longer the open [text] (the list changed). */
    internal fun ticked(checklist: String, index: Int, text: String): String? {
        val items = Checklist.parse(checklist)
        val it = items.getOrNull(index)?.takeIf { !it.done && it.text.equals(text, true) } ?: return null
        return Checklist.format(items.toMutableList().also { l -> l[index] = it.copy(done = true) })
    }

    fun tick(context: Context, item: AttentionItem) {
        val app = context.applicationContext
        val store = MemoStore.get(app)
        val memo = store.items.firstOrNull { it.id == item.memoId }
        val text = memo?.let { ticked(it.text, item.index, item.text) }
        if (text != null) store.update(memo.copy(text = text)) else Tasks.markDone(app, item.eventId, item.text)
        SyncEngine.requestAsync(app, force = true)
    }

    fun add(context: Context, item: AttentionItem) {
        Tasks.add(context, item.idea ?: return, item.eventTitle)
    }

    fun dismiss(context: Context, item: AttentionItem) {
        TaskIdeas.decide(context.applicationContext, item.idea ?: return)
    }

    /** "today", "yesterday", "3 days ago", "last week", "3 weeks ago", by calendar days in [zone]. */
    fun ago(sinceMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(sinceMs).atZone(zone).toLocalDate(), Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate())
        return when {
            days <= 0 -> "today"
            days == 1L -> "yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "last week"
            else -> "${days / 7} weeks ago"
        }
    }
}
