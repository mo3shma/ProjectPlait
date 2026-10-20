package app.plait.data.context

import app.plait.data.CalEvent
import app.plait.data.brief.MeetingBrief
import app.plait.data.brief.Summarizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the phone's built-in model wrote, split into the sections we asked for. */
data class AiSections(val relation: String?, val keyPoints: List<String>, val toSettle: List<String>, val raw: String)

/**
 * Builds the prompt for the on-device model and reads its answer. The model has a small context
 * window, so each email is cleaned (no quoted history, signatures or disclaimers) and trimmed.
 */
object AiPrompt {
    const val MAX_CHARS = 3200

    fun build(ev: CalEvent, brief: MeetingBrief, items: List<ContextItem>, zone: ZoneId = ZoneId.systemDefault(), maxChars: Int = MAX_CHARS): String {
        val whenText = if (ev.startMs > 0) Instant.ofEpochMilli(ev.startMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEEE d MMMM, h:mm a", Locale.ENGLISH)) else ev.time
        val others = ev.guests.filter { !it.self }.take(6).joinToString(", ") { it.label }
        val head = buildString {
            appendLine("You are helping someone prepare for a meeting. Use only the emails below. Do not invent facts, names or numbers.")
            appendLine()
            appendLine("Meeting: ${ev.title}")
            appendLine("When: $whenText")
            if (others.isNotBlank()) appendLine("With: $others")
            appendLine("Why (from the invite): ${if (brief.aboutIsGuess) "not stated" else brief.about}")
            appendLine()
            appendLine("Emails the person attached:")
        }
        val tail = """

Answer in exactly this format and nothing else:
Relation: one sentence on how these emails relate to the meeting, or say they do not relate.
Key points:
- up to 3 short bullets
To settle in the meeting:
- up to 3 short bullets"""
        val budget = (maxChars - head.length - tail.length).coerceAtLeast(600)
        val per = (budget / items.size.coerceAtLeast(1)) - 80
        val body = items.mapIndexed { i, item ->
            val msgs = Threads.split(unwrapLinks(item.body), item.from)
            val text = msgs.joinToString("\n") { m -> (m.from?.let { "$it: " } ?: "") + m.text.replace(Regex("\\s+"), " ") }
            "[Email ${i + 1}] From: ${EmailParser.displayName(item.from)} | Subject: ${item.subject}\n${Summarizer.clip(text, per.coerceAtLeast(200))}"
        }.joinToString("\n\n")
        return head + body + tail
    }

    fun parse(text: String): AiSections {
        var relation: String? = null
        val key = ArrayList<String>()
        val settle = ArrayList<String>()
        var section = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("Relation:", true) -> { relation = line.substringAfter(':').trim().ifBlank { null }; section = 0 }
                line.startsWith("Key points", true) -> section = 1
                line.startsWith("To settle", true) -> section = 2
                line.startsWith("-") || line.startsWith("•") || line.startsWith("*") -> {
                    val b = line.trimStart('-', '•', '*', ' ').trim()
                    if (b.isNotEmpty()) when (section) { 1 -> key.add(b); 2 -> settle.add(b) }
                }
                line.isNotEmpty() && section == 0 && relation == null -> relation = line
            }
        }
        return AiSections(relation, key.take(3), settle.take(3), text.trim())
    }
}

/** A made-up email thread about a meeting, so the feature can be tried before real mail is shared. */
object ExampleEmail {
    fun forEvent(ev: CalEvent, zone: ZoneId = ZoneId.systemDefault()): ParsedEmail {
        val others = ev.guests.filter { !it.self }
        val first = others.getOrNull(0)?.label?.substringBefore(' ') ?: "Jordan"
        val second = others.getOrNull(1)?.label?.substringBefore(' ') ?: "Alex"
        val day = if (ev.startMs > 0) Instant.ofEpochMilli(ev.startMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)) else "Tuesday"
        val body = """
Hi all,

Quick note ahead of ${ev.title} on $day. We agreed last week to keep the scope small, and $second will send the updated numbers by Thursday.

Can you confirm who owns the follow-ups before we meet? One risk: the timeline is tight and I'm concerned about the 20% budget cut. Notes are attached in prep-notes.pdf.

Thanks,
$first
""".trim()
        val from = others.firstOrNull()?.let { "${it.label} <${it.email ?: "${first.lowercase()}@example.com"}>" } ?: "$first <${first.lowercase()}@example.com>"
        return ParsedEmail(
            subject = "Before ${ev.title}", from = from, to = "You <you@example.com>", dateMs = null,
            body = body, attachments = listOf("prep-notes.pdf"),
        )
    }
}
