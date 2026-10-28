package app.plait.data.share

import app.plait.R
import app.plait.i18n.Txt

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

/** Headings and labels are in the app's language; the meeting's own words are left exactly as they are. */
object MeetingExport {
    private fun withLine(d: ExportData) = Txt.s(R.string.share_with, d.guests.joinToString(Txt.s(R.string.share_list_sep)))
    private fun voice(v: VoiceBlock) = Txt.s(R.string.share_voice_note, v.whenLabel)

    fun markdown(d: ExportData): String = buildString {
        appendLine("# ${d.title}")
        appendLine()
        appendLine("**${d.whenLine}**" + if (d.place.isNotBlank()) " · ${d.place}" else "")
        if (d.guests.isNotEmpty()) { appendLine(); appendLine(withLine(d)) }
        if (d.about.isNotBlank()) { appendLine(); appendLine("## " + Txt.s(R.string.share_why)); appendLine(d.about) }
        if (d.agenda.isNotEmpty()) { appendLine(); appendLine("## " + Txt.s(R.string.common_agenda)); d.agenda.forEach { appendLine("- $it") } }
        d.prep?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("## " + Txt.s(R.string.share_prep)); appendLine(it) }
        for (v in d.voice) {
            appendLine(); appendLine("## " + voice(v))
            v.summary?.takeIf { it.isNotBlank() }?.let { appendLine(it); appendLine() }
            if (v.transcript.isNotBlank()) { appendLine(Txt.s(R.string.share_transcript)); appendLine(v.transcript) }
        }
        for (w in d.written) { appendLine(); appendLine("## ${w.kind} · ${w.whenLabel}"); appendLine(w.text) }
        if (d.emails.isNotEmpty()) { appendLine(); appendLine("## " + Txt.s(R.string.share_emails)); d.emails.forEach { appendLine("- $it") } }
        if (d.followups.isNotEmpty()) { appendLine(); appendLine("## " + Txt.s(R.string.share_followups)); d.followups.forEach { appendLine("- [ ] $it") } }
    }.trim() + "\n"

    /** Markdown with the markup removed, for sharing as a message. */
    fun plain(d: ExportData): String = markdown(d).lines().joinToString("\n") { l ->
        l.replace(Regex("^#+\\s*"), "").replace("**", "").replace(Regex("^- \\[ \\] "), "• ").replace(Regex("^- "), "• ")
    }.trim() + "\n"

    /** (heading, body) pairs for a PDF. */
    fun sections(d: ExportData): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        out.add("" to d.whenLine + (if (d.place.isNotBlank()) " · ${d.place}" else "") + (if (d.guests.isNotEmpty()) "\n" + withLine(d) else ""))
        if (d.about.isNotBlank()) out.add(Txt.s(R.string.share_why) to d.about)
        if (d.agenda.isNotEmpty()) out.add(Txt.s(R.string.common_agenda) to d.agenda.joinToString("\n") { "• $it" })
        d.prep?.takeIf { it.isNotBlank() }?.let { out.add(Txt.s(R.string.share_prep) to it) }
        d.voice.forEach { v -> out.add(voice(v) to listOfNotNull(v.summary?.takeIf { it.isNotBlank() }, v.transcript.takeIf { it.isNotBlank() }?.let { Txt.s(R.string.share_transcript) + "\n" + it }).joinToString("\n\n")) }
        d.written.forEach { out.add("${it.kind} · ${it.whenLabel}" to it.text) }
        if (d.emails.isNotEmpty()) out.add(Txt.s(R.string.share_emails) to d.emails.joinToString("\n") { "• $it" })
        if (d.followups.isNotEmpty()) out.add(Txt.s(R.string.share_followups) to d.followups.joinToString("\n") { "• $it" })
        return out
    }
}
