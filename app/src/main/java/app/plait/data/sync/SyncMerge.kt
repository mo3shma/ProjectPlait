package app.plait.data.sync

import app.plait.data.SavedNote
import app.plait.data.context.ContextItem
import app.plait.data.memo.Memo
import app.plait.data.prep.PrepEntry

/** Everything that is kept in step between phones. */
data class SyncState(
    val notes: List<SavedNote> = emptyList(),
    val memos: List<Memo> = emptyList(),
    val emails: List<ContextItem> = emptyList(),
    /** Ids that were deleted, with when. A deletion travels so the item does not come back from another phone. */
    val deleted: Map<String, Long> = emptyMap(),
    /** Follow-ups ticked off. */
    val done: Set<String> = emptySet(),
    val prep: Map<String, PrepEntry> = emptyMap(),
)

/** What to change on this phone after reading another phone's state. */
data class SyncPlan(
    val addNotes: List<SavedNote>,
    val updateNotes: List<SavedNote>,
    val addMemos: List<Memo>,
    val addEmails: List<ContextItem>,
    val removeIds: Set<String>,
    val newDeleted: Map<String, Long>,
    val newDone: Set<String>,
    val newPrep: Map<String, PrepEntry>,
) {
    val isEmpty: Boolean
        get() = addNotes.isEmpty() && updateNotes.isEmpty() && addMemos.isEmpty() && addEmails.isEmpty() && removeIds.isEmpty() &&
            newDeleted.isEmpty() && newDone.isEmpty() && newPrep.isEmpty()
}

/**
 * Merging two phones' states. It only ever adds and applies deletions, so a sync can't lose a note: nothing is replaced
 * except by a richer version of the same note (one with a better transcript or an AI summary), and a deleted item stays
 * deleted everywhere.
 */
object SyncMerge {
    private fun richness(n: SavedNote) = (n.ai?.length ?: 0) + n.lines.sumOf { it.text.length }

    fun plan(local: SyncState, remote: SyncState): SyncPlan {
        val dead = local.deleted.keys + remote.deleted.keys
        val localNotes = local.notes.associateBy { it.id }
        val addNotes = remote.notes.filter { it.id !in localNotes && it.id !in dead }.distinctBy { it.id }
        val updateNotes = remote.notes.filter { r -> localNotes[r.id]?.let { richness(r) > richness(it) } == true && r.id !in dead }
        val addMemos = remote.memos.filter { r -> local.memos.none { it.id == r.id } && r.id !in dead }.distinctBy { it.id }
        val addEmails = remote.emails.filter { r -> local.emails.none { it.id == r.id } && r.id !in dead }.distinctBy { it.id }
        val localIds = (local.notes.map { it.id } + local.memos.map { it.id } + local.emails.map { it.id }).toSet()
        val remove = remote.deleted.keys.filter { it in localIds }.toSet()
        val newPrep = remote.prep.filter { (id, e) -> (local.prep[id]?.at ?: -1L) < e.at }
        return SyncPlan(
            addNotes, updateNotes, addMemos, addEmails, remove,
            remote.deleted.filterKeys { it !in local.deleted }, remote.done - local.done, newPrep,
        )
    }

    /** A short fingerprint of a state, to tell whether anything changed since the last time this phone wrote its file. */
    fun fingerprint(s: SyncState): String {
        val parts = s.notes.map { "n${it.id}:${richness(it)}" } + s.memos.map { "m${it.id}:${it.text.length}" } + s.emails.map { "e${it.id}:${it.aiSummary?.length ?: 0}" } +
            s.deleted.keys.map { "d$it" } + s.done.map { "f$it" } + s.prep.map { "p${it.key}:${it.value.at}" }
        return parts.sorted().joinToString("|").hashCode().toUInt().toString(16)
    }
}
