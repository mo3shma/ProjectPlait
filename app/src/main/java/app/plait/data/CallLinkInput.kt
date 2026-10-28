package app.plait.data

import app.plait.R
import app.plait.i18n.Txt
/**
 * A call link typed or pasted into the meeting editor. Only https links are kept; the provider comes from the same
 * detection the Join button uses, so a link accepted here is found there.
 */
object CallLinkInput {
    sealed interface Parsed {
        /** [provider] is null for an https link no provider pattern knows (it is saved, but Join will not find it). */
        data class Ok(val url: String, val provider: CallProvider?) : Parsed
        data class Bad(val reason: String) : Parsed
    }

    fun parse(raw: String): Parsed {
        val t = raw.trim().removePrefix("<").removeSuffix(">").trim()
        if (t.isEmpty()) return Parsed.Bad(Txt.s(R.string.link_paste))
        if (t.any { it.isWhitespace() }) return Parsed.Bad(Txt.s(R.string.link_spaces))
        if (t.startsWith("http://", ignoreCase = true)) return Parsed.Bad(Txt.s(R.string.link_insecure))
        if (!t.startsWith("https://", ignoreCase = true)) return Parsed.Bad(Txt.s(R.string.link_https))
        val host = runCatching { java.net.URI(t).host }.getOrNull()
        if (host.isNullOrBlank() || !host.contains('.')) return Parsed.Bad(Txt.s(R.string.link_not_link))
        val found = CallLinks.find(t)
        return Parsed.Ok(found?.url ?: t, found?.provider)
    }

    /**
     * Where the link goes so [CallLinks.find] reads it back: the location when that is empty, otherwise the end of the
     * description. Nothing is added twice. Returns (place, notes).
     */
    fun place(url: String, place: String, notes: String): Pair<String, String> = when {
        place.contains(url) || notes.contains(url) -> place to notes
        place.isBlank() -> url to notes
        else -> Txt.s(R.string.link_join_line, url).let { place to (if (notes.isBlank()) it else "${notes.trimEnd()}\n\n$it") }
    }
}
