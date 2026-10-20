package app.plait.data

import app.plait.data.brief.Summarizer

/**
 * Turns the speech engine's raw words ("um so i think we should uh move it to friday") into text
 * you can read: no filler, no stutters, capital letters, and a full stop or question mark.
 */
object SpokenText {
    private val fillers = setOf("um", "uh", "umm", "uhh", "er", "erm", "ah", "hmm", "mm", "mhm", "huh")
    private val questionStarts = setOf(
        "what", "why", "how", "when", "where", "who", "whom", "whose", "which", "can", "could", "would", "should",
        "do", "does", "did", "is", "are", "was", "were", "will", "shall", "have", "has", "isn't", "aren't", "don't", "doesn't",
    )

    private val properNames = setOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "april", "june", "july", "august", "september", "october", "november", "december",
    )

    fun tidy(raw: String): String {
        val words = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.lowercase() !in fillers }
        val out = ArrayList<String>()
        for (w in words) {
            if (out.isNotEmpty() && out.last().equals(w, ignoreCase = true) && w.length <= 4) continue // "the the"
            out.add(w)
        }
        if (out.isEmpty()) return ""
        val fixed = out.mapIndexed { i, w ->
            when {
                w == "i" -> "I"
                w.startsWith("i'") -> "I" + w.substring(1)
                i == 0 -> w.replaceFirstChar { it.uppercase() }
                w.lowercase().trimEnd('.', ',', '?', '!') in properNames -> w.replaceFirstChar { it.uppercase() }
                else -> w
            }
        }
        val text = fixed.joinToString(" ")
        val question = fixed.size <= 14 && fixed.first().lowercase() in questionStarts
        return if (text.last() in ".!?") text else text + if (question) "?" else "."
    }
}

/** The one block of text that goes into the calendar event or gets shared: summary, then follow-ups. */
object NoteDigest {
    fun text(note: SavedNote): String {
        note.ai?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        val texts = note.lines.map { it.text }
        val bullets = Summarizer.bullets(texts, 3)
        val actions = Summarizer.actionItems(texts)
        return buildString {
            if (bullets.isNotEmpty()) {
                appendLine("Summary:")
                bullets.forEach { appendLine("- $it") }
            }
            if (actions.isNotEmpty()) {
                if (isNotEmpty()) appendLine()
                appendLine("Follow-ups:")
                actions.forEach { appendLine("- $it") }
            }
        }.trim()
    }
}
