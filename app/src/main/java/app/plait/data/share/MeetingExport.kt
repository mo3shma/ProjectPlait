package app.plait.data.share

/** One voice note as it should appear in an export. */
data class VoiceBlock(val whenLabel: String, val summary: String?, val transcript: String)

/** One written note or photo, with the text read from it. */
data class TextBlock(val whenLabel: String, val kind: String, val text: String)

/** Everything about a meeting that is worth sharing. Built on the phone, only ever written to a file the user chooses. */
data class ExportData(
    val title: String,
    val whenLine: String,
    val place: String = "",
    val guests: List<String> = emptyList(),
    val about: String = "",
    val agenda: List<String> = emptyList(),
    val prep: String? = null,
    val voice: List<VoiceBlock> = emptyList(),
    val written: List<TextBlock> = emptyList(),
    val emails: List<String> = emptyList(),
    val followups: List<String> = emptyList(),
)

object MeetingExport {
    fun markdown(d: ExportData): String = buildString {
        appendLine("# ${d.title}")
        appendLine()
        appendLine("**${d.whenLine}**" + if (d.place.isNotBlank()) " · ${d.place}" else "")
        if (d.guests.isNotEmpty()) { appendLine(); appendLine("With: " + d.guests.joinToString(", ")) }
        if (d.about.isNotBlank()) { appendLine(); appendLine("## Why we are meeting"); appendLine(d.about) }
        if (d.agenda.isNotEmpty()) { appendLine(); appendLine("## Agenda"); d.agenda.forEach { appendLine("- $it") } }
        d.prep?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("## Prep"); appendLine(it) }
        for (v in d.voice) {
            appendLine(); appendLine("## Voice note · ${v.whenLabel}")
            v.summary?.takeIf { it.isNotBlank() }?.let { appendLine(it); appendLine() }
            if (v.transcript.isNotBlank()) { appendLine("Transcript:"); appendLine(v.transcript) }
        }
        for (w in d.written) { appendLine(); appendLine("## ${w.kind} · ${w.whenLabel}"); appendLine(w.text) }
        if (d.emails.isNotEmpty()) { appendLine(); appendLine("## Emails"); d.emails.forEach { appendLine("- $it") } }
        if (d.followups.isNotEmpty()) { appendLine(); appendLine("## Follow-ups"); d.followups.forEach { appendLine("- [ ] $it") } }
    }.trim() + "\n"

    /** Markdown with the markup removed, for sharing as a message. */
    fun plain(d: ExportData): String = markdown(d).lines().joinToString("\n") { l ->
        l.replace(Regex("^#+\\s*"), "").replace("**", "").replace(Regex("^- \\[ \\] "), "• ").replace(Regex("^- "), "• ")
    }.trim() + "\n"

    /** (heading, body) pairs for a PDF. */
    fun sections(d: ExportData): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        out.add("" to d.whenLine + (if (d.place.isNotBlank()) " · ${d.place}" else "") + (if (d.guests.isNotEmpty()) "\nWith: " + d.guests.joinToString(", ") else ""))
        if (d.about.isNotBlank()) out.add("Why we are meeting" to d.about)
        if (d.agenda.isNotEmpty()) out.add("Agenda" to d.agenda.joinToString("\n") { "• $it" })
        d.prep?.takeIf { it.isNotBlank() }?.let { out.add("Prep" to it) }
        d.voice.forEach { v -> out.add("Voice note · ${v.whenLabel}" to listOfNotNull(v.summary?.takeIf { it.isNotBlank() }, v.transcript.takeIf { it.isNotBlank() }?.let { "Transcript:\n$it" }).joinToString("\n\n")) }
        d.written.forEach { out.add("${it.kind} · ${it.whenLabel}" to it.text) }
        if (d.emails.isNotEmpty()) out.add("Emails" to d.emails.joinToString("\n") { "• $it" })
        if (d.followups.isNotEmpty()) out.add("Follow-ups" to d.followups.joinToString("\n") { "• $it" })
        return out
    }
}
