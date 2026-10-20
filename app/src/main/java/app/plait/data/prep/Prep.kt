package app.plait.data.prep

import app.plait.data.CalEvent
import app.plait.data.TimeFmt
import app.plait.data.brief.MeetingBrief
import java.time.ZoneId

/** A short plan for walking into a meeting. */
data class PrepSections(val goal: String?, val remember: List<String>, val ask: List<String>, val raw: String = "") {
    val isEmpty: Boolean get() = goal.isNullOrBlank() && remember.isEmpty() && ask.isEmpty()
}

/** What the prep is built from. Everything here comes from this phone. */
data class PrepInput(
    val event: CalEvent,
    val brief: MeetingBrief,
    /** What was decided or said last time this meeting happened, if there is a note. */
    val lastSummary: String? = null,
    /** Things still open from earlier meetings in the series. */
    val openFollowups: List<String> = emptyList(),
    /** The most useful lines from emails attached to this meeting. */
    val emailLines: List<String> = emptyList(),
) {
    /** True when there is something real to base prep on: an invite that says what it is for, an agenda, last time, open follow-ups, or emails. */
    val hasBasis: Boolean
        get() = !brief.aboutIsGuess || brief.agenda.isNotEmpty() || !lastSummary.isNullOrBlank() || openFollowups.isNotEmpty() || emailLines.isNotEmpty()
}

object Prep {
    /** The request to the AI. The format is fixed so the answer can be read back reliably. */
    fun prompt(i: PrepInput, zone: ZoneId = ZoneId.systemDefault(), maxChars: Int = 3000): String {
        val e = i.event
        val others = e.guests.filter { !it.self }.take(6).joinToString(", ") { it.label }
        val facts = buildString {
            appendLine("Meeting: ${e.title}")
            appendLine("When: ${TimeFmt.clock(e.startMs, zone)}")
            if (others.isNotBlank()) appendLine("With: $others")
            appendLine("Why (from the invite): ${if (i.brief.aboutIsGuess) "not stated" else i.brief.about}")
            if (i.brief.agenda.isNotEmpty()) appendLine("Agenda: " + i.brief.agenda.joinToString("; "))
            i.lastSummary?.let { appendLine("Last time: $it") }
            if (i.openFollowups.isNotEmpty()) appendLine("Still open from before: " + i.openFollowups.joinToString("; "))
            if (i.emailLines.isNotEmpty()) appendLine("From attached emails: " + i.emailLines.joinToString("; "))
        }
        val head = "You help someone prepare for a meeting in 30 seconds. Use only the facts below. Do not invent names, dates or numbers.\n\n"
        val tail = "\nAnswer in exactly this format and nothing else:\nGoal: one sentence on what a good outcome is.\nRemember:\n- up to 3 short bullets\nAsk:\n- up to 3 short questions to raise"
        val room = (maxChars - head.length - tail.length).coerceAtLeast(500)
        return head + facts.take(room) + tail
    }

    fun parse(text: String): PrepSections {
        var goal: String? = null
        val remember = ArrayList<String>()
        val ask = ArrayList<String>()
        var section = 0
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("Goal:", true) -> { goal = line.substringAfter(':').trim().ifBlank { null }; section = 0 }
                line.startsWith("Remember", true) -> section = 1
                line.startsWith("Ask", true) -> section = 2
                line.startsWith("-") || line.startsWith("•") || line.startsWith("*") -> {
                    val b = line.trimStart('-', '•', '*', ' ').trim()
                    if (b.isNotEmpty() && !b.equals("none", true)) when (section) { 1 -> remember.add(b); 2 -> ask.add(b) }
                }
                line.isNotEmpty() && section == 0 && goal == null -> goal = line
            }
        }
        return PrepSections(goal, remember.take(3), ask.take(3), text.trim())
    }

    fun render(s: PrepSections): String = buildString {
        s.goal?.let { appendLine("Goal: $it") }
        if (s.remember.isNotEmpty()) { appendLine("Remember:"); s.remember.forEach { appendLine("- $it") } }
        if (s.ask.isNotEmpty()) { appendLine("Ask:"); s.ask.forEach { appendLine("- $it") } }
    }.trim()

    /** The same plan without any AI: assembled from the invite, last time's note, open follow-ups and email highlights. */
    fun rules(i: PrepInput): PrepSections {
        val goal = if (!i.brief.aboutIsGuess) i.brief.about else null
        val remember = ArrayList<String>()
        i.lastSummary?.let { remember.add("Last time: $it") }
        i.emailLines.firstOrNull()?.let { remember.add(it) }
        if (i.brief.agenda.isNotEmpty() && remember.size < 3) remember.add("Agenda: " + i.brief.agenda.take(3).joinToString(", "))
        val ask = ArrayList<String>()
        i.openFollowups.take(3).forEach { ask.add("Is this done: $it") }
        if (ask.isEmpty() && i.brief.aboutIsGuess) ask.add("What is the goal of this meeting?")
        return PrepSections(goal, remember.take(3), ask.take(3))
    }
}
