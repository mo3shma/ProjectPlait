package app.plait.data

/**
 * Meetings that repeat share their notes forward: a note taken in one occurrence also shows in the later ones. Phone
 * calendar ids carry the series ("d:<event>:<start>"), Google ids carry it before "_"; otherwise a repeating meeting with
 * the same title and calendar counts as the same series.
 */
object Series {
    fun keyOfId(id: String): String? = when {
        id.startsWith("d:") && id.count { it == ':' } >= 2 -> id.substringBeforeLast(':')
        id.startsWith("g:") && id.contains('_') -> id.substringBefore('_')
        else -> null
    }

    fun key(ev: CalEvent): String? {
        if (!ev.recurring) return null
        return keyOfId(ev.id) ?: ("t:" + ev.source.name + ":" + ev.title.trim().lowercase())
    }

    /**
     * Whether a note made for [otherId] (at [madeAt]) belongs to an earlier meeting of [ev]'s series. Besides the calendar's
     * own series id, a meeting with the same name on the same calendar counts too, since calendars do not always mark
     * repeats (a moved occurrence, for one, often loses the mark).
     */
    fun carriesTo(ev: CalEvent, otherId: String, madeAt: Long, otherTitle: String? = null, find: (String) -> CalEvent?): Boolean {
        if (otherId.isEmpty() || otherId == ev.id) return false
        if (ev.startMs > 0 && madeAt >= ev.startMs) return false
        val other = find(otherId)
        val mine = key(ev)
        val theirs = keyOfId(otherId) ?: other?.let { if (it.recurring) key(it) else null }
        if (mine != null && mine == theirs) return true
        val t = (other?.title ?: otherTitle)?.trim()?.lowercase() ?: return false
        return t.length >= 4 && t == ev.title.trim().lowercase() && (other == null || other.source == ev.source)
    }
}
