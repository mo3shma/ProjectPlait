package app.plait.ui.screens

import android.content.Context

internal enum class AboutSource { Invite, Title }

/** One event's "About" text. [text] is null for a "tried" entry: the AI ran and gave nothing usable. */
internal data class AboutEntry(val text: String?, val by: String, val kind: AboutSource, val inputHash: Int, val atMs: Long) {
    fun encode(): String = listOf(by, kind.name, inputHash, atMs, text.orEmpty()).joinToString("\u0000")

    companion object {
        const val RETRY_MS = 24 * 60 * 60 * 1000L

        // Text goes last so a stray separator inside it cannot shift the other fields.
        fun decode(s: String?): AboutEntry? {
            val p = s?.split("\u0000", limit = 5)?.takeIf { it.size == 5 } ?: return null
            val kind = AboutSource.entries.firstOrNull { it.name == p[1] } ?: return null
            return AboutEntry(p[4].ifEmpty { null }, p[0], kind, p[2].toIntOrNull() ?: return null, p[3].toLongOrNull() ?: return null)
        }

        fun key(eventId: String) = "v12:$eventId"
    }
}

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
