package app.plait.data.prep

import android.content.Context
import app.plait.ai.Ai
import app.plait.data.CalEvent
import app.plait.data.NoteStore
import app.plait.data.brief.BriefBuilder
import app.plait.data.brief.MeetingBrief
import app.plait.data.context.ContextAnalyzer
import app.plait.data.context.ContextStore
import app.plait.data.followups.FollowupState
import app.plait.data.memo.MemoStore
import java.time.ZoneId

/** Gathers what is known about a meeting from this phone, and turns it into prep with the chosen AI or with rules. */
object PrepBuilder {
    fun input(context: Context, ev: CalEvent, brief: MeetingBrief, zone: ZoneId = ZoneId.systemDefault()): PrepInput {
        FollowupState.load(context)
        val notes = NoteStore.get(context).notes.toList()
        val memos = MemoStore.get(context).items.toList()
        val items = ContextStore.get(context).forEvent(ev.id)
        val emailLines = if (items.isEmpty()) emptyList() else ContextAnalyzer.analyze(ev, brief, items, zone).let { s ->
            (s.toSettle + s.highlights).distinctBy { it.text }.take(3).map { "${it.kind.label}: ${it.text}" }
        }
        val since = SinceLast.compute(ev, notes, memos) { FollowupState.isOpen(it, System.currentTimeMillis()) }
        return PrepInput(ev, brief, brief.lastTime?.summary, since?.stillOpen?.map { it.text }.orEmpty(), emailLines)
    }

    /** Writes prep with the chosen AI and keeps it. Returns the entry, or null if no AI is on or it gave nothing. */
    suspend fun generate(context: Context, ev: CalEvent, zone: ZoneId = ZoneId.systemDefault()): PrepEntry? {
        val status = Ai.status(context)
        if (!status.ready) return null
        val brief = BriefBuilder.build(ev, NoteStore.get(context).notes.toList(), System.currentTimeMillis())
        val facts = input(context, ev, brief, zone)
        if (!facts.hasBasis) return null // nothing real to go on, so nothing is made up
        val result = Ai.generate(context, Prep.prompt(facts, zone, status.promptBudget), background = true) ?: return null
        val parsed = Prep.parse(result.text)
        if (parsed.isEmpty) return null
        val entry = PrepEntry(Prep.render(parsed), result.label, System.currentTimeMillis())
        PrepStore.get(context).put(ev.id, entry)
        return entry
    }
}
