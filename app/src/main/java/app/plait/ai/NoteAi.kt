package app.plait.ai

import app.plait.data.SavedNote

/** What the phone's built-in model wrote about a voice note. */
data class NoteSections(val summary: String?, val decisions: List<String>, val followUps: List<String>)

object NoteAiPrompt {
    private const val MAX_CHARS = 3600

    fun build(note: SavedNote, maxChars: Int = MAX_CHARS): String {
        val transcript = note.lines.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
        val clipped = if (transcript.length <= maxChars) transcript else transcript.take(maxChars).substringBeforeLast(' ') + " …"
        return "You summarise a spoken voice note recorded around a meeting called \"${note.title}\". " +
            "The text is machine transcription, so it may contain wrong words. Use only what it says. Do not invent names, dates or numbers.\n\n" +
            "Voice note:\n$clipped\n\n" +
            "Answer in exactly this format and nothing else:\n" +
            "Summary: one or two plain sentences.\n" +
            "Decisions:\n- up to 3 short bullets, or write - none\n" +
            "Follow-ups:\n- up to 4 short bullets that say who does what, or write - none"
    }

    fun parse(text: String): NoteSections {
        var summary: String? = null
        val decisions = ArrayList<String>()
        val follow = ArrayList<String>()
        var section = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("Summary:", true) -> { summary = line.substringAfter(':').trim().ifBlank { null }; section = 0 }
                line.startsWith("Decisions", true) -> section = 1
                line.startsWith("Follow", true) -> section = 2
                line.startsWith("-") || line.startsWith("•") || line.startsWith("*") -> {
                    val b = line.trimStart('-', '•', '*', ' ').trim()
                    if (b.isNotEmpty() && !b.equals("none", true) && !b.equals("none.", true)) when (section) { 1 -> decisions.add(b); 2 -> follow.add(b) }
                }
                line.isNotEmpty() && section == 0 && summary == null -> summary = line
            }
        }
        return NoteSections(summary, decisions.take(3), follow.take(4))
    }

    /** Plain text saved with the note and written to the calendar: the model's own answer, tidied. */
    fun render(s: NoteSections): String = buildString {
        s.summary?.let { appendLine("Summary: $it") }
        if (s.decisions.isNotEmpty()) { appendLine("Decisions:"); s.decisions.forEach { appendLine("- $it") } }
        if (s.followUps.isNotEmpty()) { appendLine("Follow-ups:"); s.followUps.forEach { appendLine("- $it") } }
    }.trim()
}
