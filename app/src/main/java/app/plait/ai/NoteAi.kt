package app.plait.ai

import app.plait.R
import app.plait.data.SavedNote
import app.plait.i18n.Txt

/** What the phone's built-in model wrote about a voice note. */
data class NoteSections(val summary: String?, val decisions: List<String>, val followUps: List<String>)

object NoteAiPrompt {
    private const val MAX_CHARS = 3600

    fun build(note: SavedNote, maxChars: Int = MAX_CHARS): String {
        val transcript = note.lines.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim()
        val clipped = if (transcript.length <= maxChars) transcript else transcript.take(maxChars).substringBeforeLast(' ') + " …"
        return Txt.s(R.string.ai_note_prompt, note.title, clipped)
    }

    // Section names in either language, so a note saved in one still reads after the app switches to the other.
    private val summaryHead = Regex("^(summary|(ال)?ملخص)\\s*:")
    private val decisionsHead = Regex("^(decisions|(ال)?قرارات)")
    private val followHead = Regex("^(follow|(مهام )?(ال)?متابع)")
    private val none = setOf("none", "none.", "لا يوجد", "لا توجد", "لا شيء", "لا شي")

    fun parse(text: String): NoteSections {
        var summary: String? = null
        val decisions = ArrayList<String>()
        val follow = ArrayList<String>()
        var section = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            val head = ArabicText.norm(line)
            when {
                summaryHead.containsMatchIn(head) -> { summary = line.substringAfter(':').trim().ifBlank { null }; section = 0 }
                decisionsHead.containsMatchIn(head) -> section = 1
                followHead.containsMatchIn(head) -> section = 2
                line.startsWith("-") || line.startsWith("•") || line.startsWith("*") -> {
                    val b = line.trimStart('-', '•', '*', ' ').trim()
                    if (b.isNotEmpty() && ArabicText.norm(b).trimEnd('.') !in none && b.lowercase() !in none) when (section) { 1 -> decisions.add(b); 2 -> follow.add(b) }
                }
                line.isNotEmpty() && section == 0 && summary == null -> summary = line
            }
        }
        return NoteSections(summary, decisions.take(3), follow.take(4))
    }

    /** Plain text saved with the note and written to the calendar, in the app's language: the model's own answer, tidied. */
    fun render(s: NoteSections): String = buildString {
        s.summary?.let { appendLine(Txt.s(R.string.ai_note_line_summary, it)) }
        if (s.decisions.isNotEmpty()) { appendLine(Txt.s(R.string.ai_note_line_decisions)); s.decisions.forEach { appendLine("- $it") } }
        if (s.followUps.isNotEmpty()) { appendLine(Txt.s(R.string.ai_note_line_followups)); s.followUps.forEach { appendLine("- $it") } }
    }.trim()
}

/**
 * A voice note's summary, written from the whole transcript through the harness. Small models only take a page at a
 * time, so a long transcript is read in parts: the model notes the key points of each part, then writes the summary,
 * decisions and follow-ups from all of them. Every line it writes is checked against the transcript; nothing that
 * fails is kept. The rules and the answer are in the app's language; the transcript stays in the language it was spoken.
 */
object NoteSummary {
    private const val PART_CHARS = 1800

    /** Splits a transcript into parts of about [size] characters, at sentence ends where possible. */
    fun parts(text: String, size: Int = PART_CHARS): List<String> {
        val sentences = text.split(Regex("(?<=[.!?؟])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (s in sentences) {
            if (cur.isNotEmpty() && cur.length + s.length > size) { out.add(cur.toString()); cur.clear() }
            if (s.length > size) s.chunked(size).forEach { out.add(it) } else cur.append(if (cur.isEmpty()) "" else " ").append(s)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    private fun list(o: org.json.JSONObject, key: String, source: String, words: Int, max: Int): List<String> =
        o.optJSONArray(key)?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
            .mapNotNull { AiHarness.checked(it, source, words)?.trimEnd('.') }.distinct().take(max)

    /** The rendered summary (as [NoteAiPrompt.render] writes it), or null when the AI gave nothing usable. */
    suspend fun write(context: android.content.Context, note: app.plait.data.SavedNote): String? {
        val transcript = app.plait.data.Transcript.text(note.lines)
        if (transcript.count { it.isLetter() } < 20) return null
        val parts = parts(transcript)
        val material = if (parts.size == 1) transcript else {
            // Read every part: the summary is built from the whole note, not just its beginning.
            val points = ArrayList<String>()
            for (part in parts) {
                val o = AiHarness.json(context, Txt.s(R.string.ai_note_points_rules), Txt.s(R.string.ai_note_points_user, note.title, part), background = false) ?: continue
                points += list(o, "points", part, 16, 5)
            }
            if (points.isEmpty()) return null
            points.joinToString("\n") { "- $it" }
        }
        val o = AiHarness.json(
            context, Txt.s(R.string.ai_note_summary_rules),
            Txt.s(
                R.string.ai_note_summary_user, note.title,
                Txt.s(if (parts.size == 1) R.string.ai_note_material_transcript else R.string.ai_note_material_points), material,
            ),
            background = false,
        ) ?: return null
        val sections = NoteSections(
            summary = AiHarness.paragraph(o.optString("summary"), transcript, 40),
            decisions = list(o, "decisions", transcript, 14, 3),
            followUps = list(o, "follow_ups", transcript, 14, 4),
        )
        if (sections.summary == null && sections.decisions.isEmpty() && sections.followUps.isEmpty()) return null
        return NoteAiPrompt.render(sections)
    }

    /** A written or photo note's summary through the harness, checked against the note; null when the AI gave nothing usable. */
    suspend fun writeText(context: android.content.Context, title: String, text: String): String? {
        val o = AiHarness.json(context, Txt.s(R.string.ai_memo_rules), Txt.s(R.string.ai_memo_user, title, text.take(3000)), background = false) ?: return null
        return AiHarness.paragraph(o.optString("summary"), text, 45, maxSentences = 3)
    }
}
