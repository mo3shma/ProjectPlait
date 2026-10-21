package app.plait.ui.screens

import android.content.Context

internal enum class AboutSource { Invite, Title }

/**
 * One event's "About" text. [text] is null for a "tried" entry: the AI ran and gave nothing usable. [engine] is a stable id of
 * the engine that wrote it ([UNKNOWN_ENGINE] for old entries), [linkedHash] a hash of the notes and tasks linked at that time.
 */
internal data class AboutEntry(
    val text: String?, val by: String, val kind: AboutSource, val inputHash: Int, val atMs: Long,
    val engine: String = UNKNOWN_ENGINE, val engineLabel: String = "", val linkedHash: Int = 0,
) {
    // A leading "2" marks the new layout; old entries start with the engine label ("ai").
    fun encode(): String = listOf("2", by, kind.name, inputHash, atMs, engine, engineLabel, linkedHash, text.orEmpty()).joinToString("\u0000")

    companion object {
        const val RETRY_MS = 24 * 60 * 60 * 1000L
        const val UNKNOWN_ENGINE = "unknown"

        // Text goes last so a stray separator inside it cannot shift the other fields.
        fun decode(s: String?): AboutEntry? {
            if (s == null) return null
            val n = s.split("\u0000", limit = 9).takeIf { it.size == 9 && it[0] == "2" }
            if (n != null) {
                val kind = AboutSource.entries.firstOrNull { it.name == n[2] } ?: return null
                return AboutEntry(n[8].ifEmpty { null }, n[1], kind, n[3].toIntOrNull() ?: return null, n[4].toLongOrNull() ?: return null, n[5], n[6], n[7].toIntOrNull() ?: return null)
            }
            val p = s.split("\u0000", limit = 5).takeIf { it.size == 5 } ?: return null
            val kind = AboutSource.entries.firstOrNull { it.name == p[1] } ?: return null
            return AboutEntry(p[4].ifEmpty { null }, p[0], kind, p[2].toIntOrNull() ?: return null, p[3].toLongOrNull() ?: return null)
        }

        fun key(eventId: String) = "v12:$eventId"
    }
}

/** What is linked to a meeting: note texts (voice summaries or transcripts, written, photo and checklist notes) with their time, and open task lines. */
internal data class AboutLinked(val notes: List<Pair<Long, String>>, val tasks: List<String>) {
    private val ns get() = notes.filter { it.second.isNotBlank() }
    private val ts get() = tasks.filter { it.isNotBlank() }
    val isEmpty get() = ns.isEmpty() && ts.isEmpty()

    /** 0 when nothing is linked; otherwise changes with any added, edited or removed note or task (a ticked checklist line is an edit). */
    val hash: Int
        get() = if (isEmpty) 0
        else listOf(ns.sortedWith(compareBy({ it.first }, { it.second })).map { it.second.trim() }, ts.map { it.trim() }.sorted()).hashCode()

    /** At most the 3 newest notes (200 characters each) and 5 open tasks; empty when nothing is linked. */
    fun digest(): String = buildList {
        val n = ns.sortedByDescending { it.first }.take(3)
        if (n.isNotEmpty()) add("NOTES:\n" + n.joinToString("\n") { "- " + it.second.split(Regex("[ \t\r\n]+")).joinToString(" ").trim().take(200) })
        val t = ts.take(5)
        if (t.isNotEmpty()) add("OPEN TASKS:\n" + t.joinToString("\n") { "- " + it.split(Regex("[ \t\r\n]+")).joinToString(" ").trim() })
    }.joinToString("\n")
}

/**
 * Whether a refresh by the user should rewrite the text: only with no entry, another engine, or changed notes or tasks.
 * An old entry (engine unknown) counts as the same engine, so it is rewritten only when something is linked.
 */
internal fun shouldRefresh(entry: AboutEntry?, currentEngine: String, currentLinkedHash: Int): Boolean =
    entry == null || (entry.engine != AboutEntry.UNKNOWN_ENGINE && entry.engine != currentEngine) || entry.linkedHash != currentLinkedHash

/** The cached text worth showing: a title guess is not shown once the invite has real content. */
internal fun cachedAbout(entry: AboutEntry?, from: AboutSource): String? =
    entry?.text?.takeIf { !(entry.kind == AboutSource.Title && from == AboutSource.Invite) }

/**
 * Whether the AI should write now. A written text stays until the user refreshes, except one upgrade from a title guess to
 * an invite-based text. A failed try is retried only when the input changed and a day has passed.
 */
internal fun shouldWrite(entry: AboutEntry?, from: AboutSource, inputHash: Int, aiReady: Boolean, nowMs: Long): Boolean = when {
    !aiReady -> false
    entry == null -> true
    entry.kind == AboutSource.Title && from == AboutSource.Invite -> true
    entry.text != null -> false
    else -> entry.inputHash != inputHash && nowMs - entry.atMs >= AboutEntry.RETRY_MS
}
