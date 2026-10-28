package app.plait.data.context

import app.plait.R
import app.plait.ai.ArabicText
import app.plait.data.CalEvent
import app.plait.data.brief.MeetingBrief
import app.plait.data.brief.Summarizer
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the phone's built-in model wrote, split into the sections we asked for. */
data class AiSections(val relation: String?, val keyPoints: List<String>, val toSettle: List<String>, val raw: String)

/**
 * Builds the prompt for the on-device model and reads its answer. The model has a small context
 * window, so each email is cleaned (no quoted history, signatures or disclaimers) and trimmed.
 * The prompt, and so the answer, is in the app's language; the emails are left as they were written.
 */
object AiPrompt {
    const val MAX_CHARS = 3200

    fun build(ev: CalEvent, brief: MeetingBrief, items: List<ContextItem>, zone: ZoneId = ZoneId.systemDefault(), maxChars: Int = MAX_CHARS): String {
        val pattern = if (AppLocale.isRtl) "EEEE d MMMM، h:mm a" else "EEEE d MMMM, h:mm a"
        val whenText = if (ev.startMs > 0) Instant.ofEpochMilli(ev.startMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern(pattern, AppLocale.locale)) else ev.time
        val others = ev.guests.filter { !it.self }.take(6).joinToString(", ") { it.label }
        val head = buildString {
            appendLine(Txt.s(R.string.ai_ctx_prompt_intro))
            appendLine()
            appendLine(Txt.s(R.string.ai_ctx_prompt_meeting, ev.title))
            appendLine(Txt.s(R.string.ai_ctx_prompt_when, whenText))
            if (others.isNotBlank()) appendLine(Txt.s(R.string.ai_ctx_prompt_with, others))
            appendLine(Txt.s(R.string.ai_ctx_prompt_why, if (brief.aboutIsGuess) Txt.s(R.string.ai_ctx_prompt_not_stated) else brief.about))
            appendLine()
            appendLine(Txt.s(R.string.ai_ctx_prompt_emails))
        }
        val tail = "\n\n" + Txt.s(R.string.ai_ctx_prompt_format)
        val budget = (maxChars - head.length - tail.length).coerceAtLeast(600)
        val per = (budget / items.size.coerceAtLeast(1)) - 80
        val body = items.mapIndexed { i, item ->
            val msgs = Threads.split(unwrapLinks(item.body), item.from)
            val text = msgs.joinToString("\n") { m -> (m.from?.let { "$it: " } ?: "") + m.text.replace(Regex("\\s+"), " ") }
            Txt.s(R.string.ai_ctx_prompt_email, i + 1, EmailParser.displayName(item.from), item.subject) + "\n" + Summarizer.clip(text, per.coerceAtLeast(200))
        }.joinToString("\n\n")
        return head + body + tail
    }

    // Section names in either language: an answer saved in one still reads after the app switches to the other.
    private val relationHead = Regex("^(relation|(ال)?علاقه)\\s*:")
    private val keyHead = Regex("^(key points|(ال)?نقاط (ال)?رئيسيه)")
    private val settleHead = Regex("^(to settle|ما يجب حسمه|للحسم)")

    fun parse(text: String): AiSections {
        var relation: String? = null
        val key = ArrayList<String>()
        val settle = ArrayList<String>()
        var section = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            val head = ArabicText.norm(line)
            when {
                relationHead.containsMatchIn(head) -> { relation = line.substringAfter(':').trim().ifBlank { null }; section = 0 }
                keyHead.containsMatchIn(head) -> section = 1
                settleHead.containsMatchIn(head) -> section = 2
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

/** A made-up email thread about a meeting, so the feature can be tried before real mail is shared. In the app's language. */
object ExampleEmail {
    fun forEvent(ev: CalEvent, zone: ZoneId = ZoneId.systemDefault()): ParsedEmail {
        val others = ev.guests.filter { !it.self }
        val first = others.getOrNull(0)?.label?.substringBefore(' ') ?: Txt.s(R.string.ai_example_first_name)
        val second = others.getOrNull(1)?.label?.substringBefore(' ') ?: Txt.s(R.string.ai_example_second_name)
        val day = if (ev.startMs > 0) Instant.ofEpochMilli(ev.startMs).atZone(zone)
            .format(DateTimeFormatter.ofPattern("EEEE", AppLocale.locale)) else Txt.s(R.string.ai_example_day)
        val body = Txt.s(R.string.ai_example_body, Bidi.isolate(ev.title), day, Bidi.isolate(second), first).trim()
        // An address needs Latin letters; an Arabic first name gets a plain one.
        val local = first.lowercase().takeIf { !ArabicText.has(it) } ?: "colleague"
        val from = others.firstOrNull()?.let { "${it.label} <${it.email ?: "$local@example.com"}>" } ?: "$first <$local@example.com>"
        return ParsedEmail(
            subject = Txt.s(R.string.ai_example_subject, ev.title), from = from, to = "${Txt.s(R.string.ai_example_you)} <you@example.com>", dateMs = null,
            body = body, attachments = listOf("prep-notes.pdf"),
        )
    }
}
