package app.plait.data.tasks

import android.content.Context
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
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
    /** Due time of an open checklist line, epoch millis; null for suggestions and undated lines. */
    val dueMs: Long? = null,
    /** Open and due within 24 hours of the moment it was collected, or already overdue. */
    val urgent: Boolean = false,
) { enum class Kind { Suggestion, Open } }

object AttentionTasks {
    private const val HOUR = 3_600_000L
    private const val URGENT_WINDOW = 24 * HOUR

    /**
     * Everything that needs attention: urgent items first (most overdue, then soonest due), then suggestions, then the
     * other open items, each of those groups most stale first.
     */
    fun collect(ideas: List<TaskIdea>, memos: List<Memo>, eventOf: (String) -> CalEvent?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<AttentionItem> {
        fun event(id: String) = if (id.isBlank()) null else eventOf(id)
        fun since(e: CalEvent?, fallback: Long) = e?.startMs?.takeIf { it > 0 } ?: fallback
        val open = memos.filter { it.kind == MemoKind.Checklist }.flatMap { m ->
            val e = event(m.eventId)
            // Checklists of meetings still to come are agendas, not overdue tasks, unless the line itself is due soon.
            val agenda = e != null && e.startMs > nowMs
            Checklist.parse(m.text, zone).mapIndexedNotNull { i, it ->
                val urgent = it.dueMs != null && it.dueMs <= nowMs + URGENT_WINDOW
                if (it.done || (agenda && !urgent)) null
                else AttentionItem("memo:${m.id}:$i", AttentionItem.Kind.Open, it.text, m.eventId, e?.title.orEmpty(), since(e, m.createdAt), memoId = m.id, index = i, dueMs = it.dueMs, urgent = urgent)
            }
        }
        val suggestions = ideas.filter { i -> open.none { it.eventId == i.eventId && it.text.equals(i.text, true) } }.map { i ->
            val e = event(i.eventId)
            AttentionItem("idea:${i.id}", AttentionItem.Kind.Suggestion, i.text, i.eventId, e?.title.orEmpty(), since(e, i.createdAt), idea = i)
        }
        val stalest = compareBy<AttentionItem>({ it.sinceMs }, { it.text.lowercase() })
        val (urgent, rest) = open.partition { it.urgent }
        return urgent.sortedWith(compareBy<AttentionItem> { it.dueMs }.then(stalest)) + suggestions.sortedWith(stalest) + rest.sortedWith(stalest)
    }

    fun urgentCount(items: List<AttentionItem>): Int = items.count { it.urgent }

    /**
     * "Overdue by 2 days", "Overdue by 3 h", "Due in 5 h" (under 6 hours away), "Due today 5:00 PM", "Due tomorrow 9:00 AM",
     * "Due Mon 5:00 PM" (within a week), "Due Mon 2 Nov, 5:00 PM".
     */
    fun dueLabel(dueMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val diff = dueMs - nowMs
        fun n(v: Long) = v.toInt()
        if (diff < 0) {
            val late = -diff
            return when {
                late >= 24 * HOUR -> Txt.plural(R.plurals.gen_overdue_days, n(late / (24 * HOUR)))
                late >= HOUR -> Txt.plural(R.plurals.gen_overdue_hours, n(late / HOUR))
                else -> Txt.plural(R.plurals.gen_overdue_min, n(maxOf(1L, late / 60_000L)))
            }
        }
        if (diff < 6 * HOUR) return if (diff >= HOUR) Txt.plural(R.plurals.gen_due_in_hours, n(diff / HOUR)) else Txt.plural(R.plurals.gen_due_in_min, n(maxOf(1L, diff / 60_000L)))
        val date = Instant.ofEpochMilli(dueMs).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate(), date)
        val clock = Bidi.isolate(app.plait.data.TimeFmt.clock(dueMs, zone))
        val dow = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, AppLocale.locale)
        return when {
            days == 0L -> Txt.s(R.string.gen_due_today_at, clock)
            days == 1L -> Txt.s(R.string.gen_due_tomorrow_at, clock)
            days < 7 -> Txt.s(R.string.gen_due_weekday_at, dow, clock)
            else -> Txt.s(R.string.gen_due_date_at, dow, date.dayOfMonth, date.month.getDisplayName(java.time.format.TextStyle.SHORT, AppLocale.locale), clock)
        }
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
            days <= 0 -> Txt.s(R.string.gen_ago_today)
            days == 1L -> Txt.s(R.string.gen_ago_yesterday)
            days < 7 -> Txt.plural(R.plurals.gen_ago_days, days.toInt())
            days < 14 -> Txt.s(R.string.gen_ago_last_week)
            else -> Txt.plural(R.plurals.gen_ago_weeks, (days / 7).toInt())
        }
    }
}
