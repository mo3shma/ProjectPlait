package app.plait.data

/** A stretch of a voice note read as one paragraph, starting at [atMs]. */
data class Paragraph(val atMs: Long, val text: String, val shaky: Boolean)

/**
 * Turns the phrases a recogniser hears (one per breath or pause) into paragraphs that read like a note: phrases join
 * up, and a new paragraph starts only after a real pause or when one has grown long. The phrases themselves are kept
 * as they are, because playback and bookmarks use their times.
 */
object Transcript {
    /** About how long a phrase takes to say, from its length: speech runs at roughly 15 characters a second. */
    private fun spokenMs(text: String) = text.length * 65L

    fun paragraphs(lines: List<NoteLine>, pauseMs: Long = 6_000, maxChars: Int = 600): List<Paragraph> {
        val out = ArrayList<Paragraph>()
        var start = 0L
        val text = StringBuilder()
        var shaky = 0; var count = 0
        var prev: NoteLine? = null
        fun flush() {
            if (text.isNotBlank()) out.add(Paragraph(start, text.toString().trim(), shaky * 2 > count))
            text.clear(); shaky = 0; count = 0
        }
        for (ln in lines) {
            val t = ln.text.trim()
            if (t.isEmpty()) continue
            val p = prev
            val silence = if (p == null) 0L else ln.atMs - p.atMs - spokenMs(p.text)
            if (p != null && (silence > pauseMs || text.length > maxChars)) flush()
            if (text.isEmpty()) { start = ln.atMs; text.append(t.replaceFirstChar { it.uppercase() }) }
            else text.append(' ').append(join(text, t))
            if (ln.conf < 0.6f) shaky++
            count++
            prev = ln
        }
        flush()
        return out
    }

    /** The next phrase as it continues the paragraph: after a full stop it starts a sentence, otherwise it carries on as written. */
    private fun join(sofar: CharSequence, next: String): String {
        val ended = sofar.trimEnd().lastOrNull()?.let { it in ".!?؟…" } ?: true
        return if (ended) next.replaceFirstChar { it.uppercase() } else next
    }

    /** The whole note as paragraphs separated by blank lines. */
    fun text(lines: List<NoteLine>): String = paragraphs(lines).joinToString("\n\n") { it.text }
}
