package app.plait.data

/** Editing a voice note's transcript as plain text, one line per phrase, keeping the times where the phrases still line up. */
object NoteEdit {
    fun transcriptText(note: SavedNote): String = note.lines.joinToString("\n") { it.text }

    fun applyTranscript(note: SavedNote, text: String): SavedNote {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val old = note.lines
        val rebuilt = lines.mapIndexed { i, t ->
            val at = when {
                old.size == lines.size -> old[i].atMs
                old.isEmpty() -> if (lines.size > 1) note.durationMs * i / lines.size else 0L
                else -> note.durationMs * i / lines.size
            }
            NoteLine(at, t, if (old.size == lines.size && old[i].text == t) old[i].conf else 1f)
        }
        // A rewritten transcript no longer matches an old AI summary of it, so the summary is dropped.
        val changed = rebuilt.map { it.text } != old.map { it.text }
        return note.copy(lines = rebuilt, ai = if (changed) null else note.ai, aiBy = if (changed) null else note.aiBy)
    }
}
