package app.plait.data.brief

import app.plait.data.CalEvent
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.SavedNote

/** What kind of meeting this looks like. Also drives the card colour. */
enum class MeetingKind(val label: String) {
    OneOnOne("1:1"), Standup("Check-in"), Review("Review"), Planning("Planning"), Interview("Interview"),
    External("External"), Social("Social"), Personal("Personal"), Focus("Focus"), General("Meeting"),
}

data class LastTime(val whenMs: Long, val summary: String, val followUps: Int)

data class MeetingBrief(
    val kind: MeetingKind,
    /** Why you are meeting, in a sentence or two. */
    val about: String,
    /** True when [about] was worked out from the title and guests because the invite said nothing. */
    val aboutIsGuess: Boolean,
    val agenda: List<String>,
    val chips: List<String>,
    /** "with Priya, Sam +2" */
    val withLine: String,
    /** "3 of 5 accepted" */
    val rsvpLine: String,
    val lastTime: LastTime?,
)

object BriefBuilder {
    private val personalDomains = setOf(
        "gmail.com", "googlemail.com", "outlook.com", "hotmail.com", "live.com", "yahoo.com", "icloud.com", "me.com",
        "mac.com", "proton.me", "protonmail.com", "aol.com", "msn.com", "gmx.com", "fastmail.com",
    )

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val interview = rx("""\b(interview|screening|candidate|hiring|recruit\w*)\b""")
    private val social = rx("""\b(lunch|dinner|coffee|drinks|breakfast|brunch|birthday|party|happy hour|team building|social|catch[- ]?up over)\b""")
    private val personal = rx("""\b(gym|run|running|climb\w*|yoga|doctor|dentist|dental|appointment|therapy|physio|flight|haircut|school|pickup|workout|swim\w*|medical)\b""")
    private val focus = rx("""\b(focus|deep work|heads[- ]down|writing time|no meetings|blocked)\b""")
    private val oneOnOne = rx("""(\b1\s?[:\-/]\s?1\b|\b1on1\b|\bone[- ]on[- ]one\b|\b1-2-1\b)""")
    private val standup = rx("""\b(stand[- ]?up|daily|scrum|sync|check[- ]?in|huddle|weekly|catch[- ]?up)\b""")
    private val review = rx("""\b(review|retro|retrospective|demo|showcase|post[- ]?mortem|feedback|critique|audit|walk[- ]?through)\b""")
    private val planning = rx("""\b(planning|plan|kick[- ]?off|roadmap|strategy|brainstorm|workshop|offsite|okrs?|prioriti[sz]\w*|grooming|refinement|estimation|design sprint)\b""")
    private val external = rx("""\b(vendor|customer|client|partner|prospect|sales|renewal|contract|quote|proposal|supplier)\b""")

    fun others(e: CalEvent): List<Guest> = e.guests.filter { !it.self }

    private fun selfDomain(e: CalEvent): String? =
        e.guests.firstOrNull { it.self }?.domain
            ?: e.guests.mapNotNull { it.domain }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** Guests whose address is on a different company domain from yours. */
    fun externalGuests(e: CalEvent): List<Guest> {
        val mine = selfDomain(e) ?: return emptyList()
        return e.guests.filter { !it.self && it.domain != null && it.domain != mine }
    }

    fun classify(e: CalEvent): MeetingKind {
        val t = e.title
        val total = e.guests.size
        return when {
            interview.containsMatchIn(t) -> MeetingKind.Interview
            social.containsMatchIn(t) -> MeetingKind.Social
            personal.containsMatchIn(t) -> MeetingKind.Personal
            focus.containsMatchIn(t) -> MeetingKind.Focus
            oneOnOne.containsMatchIn(t) -> MeetingKind.OneOnOne
            standup.containsMatchIn(t) -> MeetingKind.Standup
            review.containsMatchIn(t) -> MeetingKind.Review
            planning.containsMatchIn(t) -> MeetingKind.Planning
            external.containsMatchIn(t) -> MeetingKind.External
            externalGuests(e).let { it.isNotEmpty() && it.size * 2 >= others(e).size } -> MeetingKind.External
            total == 2 -> MeetingKind.OneOnOne
            else -> MeetingKind.General
        }
    }

    private class Parsed(val purpose: String?, val agenda: List<String>, val rest: String)

    private val heading = Regex(
        """^(agenda|topics?|discussion(?: points)?|objectives?|goals?|purpose|to discuss|what we.ll cover|outcomes?|decisions?(?: needed)?|context|background)\s*[:\-–]\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val purposeHeading = Regex("^(purpose|objectives?|goals?|context|background|why)$", RegexOption.IGNORE_CASE)
    private val bullet = Regex("""^([-*•·▪‣◦–]|\d+[.)]|\[ ?[xX]? ?\])\s+(.*)$""")

    private fun parse(cleaned: String): Parsed {
        val lines = cleaned.lines()
        var purpose: String? = null
        val agenda = ArrayList<String>()
        val used = HashSet<Int>()
        var i = 0
        while (i < lines.size) {
            val h = heading.matchEntire(lines[i].trim())
            if (h != null) {
                val name = h.groupValues[1].lowercase().substringBefore(' ')
                val inline = h.groupValues[2].trim()
                used.add(i)
                if (inline.isNotEmpty() && purposeHeading.matches(h.groupValues[1].trim().substringBefore(' '))) {
                    if (purpose == null) purpose = inline
                } else if (inline.isNotEmpty() && bullet.matchEntire(inline) == null) {
                    if (purpose == null && name !in setOf("agenda", "topic", "topics")) purpose = inline
                    else if (name in setOf("agenda", "topic", "topics")) agenda.add(inline)
                }
                var j = i + 1
                while (j < lines.size) {
                    val b = bullet.matchEntire(lines[j].trim())
                    if (b == null) break
                    agenda.add(b.groupValues[2].trim()); used.add(j); j++
                }
                i = j
                continue
            }
            i++
        }
        // Bullets with no heading still read as an agenda when there are at least two in a row.
        if (agenda.isEmpty()) {
            var run = ArrayList<Pair<Int, String>>()
            fun flush() {
                if (run.size >= 2 && agenda.isEmpty()) { run.forEach { agenda.add(it.second); used.add(it.first) } }
                run = ArrayList()
            }
            lines.forEachIndexed { idx, l ->
                val b = bullet.matchEntire(l.trim())
                if (b != null) run.add(idx to b.groupValues[2].trim()) else flush()
            }
            flush()
        }
        val rest = lines.filterIndexed { idx, _ -> idx !in used }.joinToString("\n").trim()
        return Parsed(purpose, agenda.take(6), rest)
    }

    private fun domainName(d: String): String =
        d.substringBeforeLast('.').substringAfterLast('.').replaceFirstChar { it.uppercase() }

    fun build(e: CalEvent, notes: List<SavedNote> = emptyList(), nowMs: Long = System.currentTimeMillis()): MeetingBrief {
        val kind = classify(e)
        val cleaned = DescriptionCleaner.clean(e.description)
        val parsed = parse(cleaned)

        val fromInvite: String? = when {
            !parsed.purpose.isNullOrBlank() -> Summarizer.clip(parsed.purpose, 240)
            parsed.rest.isNotBlank() -> Summarizer.extract(parsed.rest, max = 2, maxChars = 240).ifBlank { null }
            else -> null
        }
        val about = fromInvite ?: guess(e, kind)

        val ext = externalGuests(e)
        val chips = buildList {
            if (e.recurring) add("Repeats")
            if (ext.isNotEmpty()) {
                val named = ext.mapNotNull { it.domain }.filter { it !in personalDomains }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                add(if (named != null) "External · ${domainName(named)}" else "External guests")
            }
            if (e.guests.size > 2) add("${e.guests.size} people")
        }

        return MeetingBrief(
            kind = kind, about = about, aboutIsGuess = fromInvite == null,
            agenda = parsed.agenda, chips = chips,
            withLine = withLine(e), rsvpLine = rsvpLine(e),
            lastTime = lastTime(e, notes, nowMs),
        )
    }

    fun withLine(e: CalEvent): String {
        val o = others(e)
        if (o.isEmpty()) return ""
        val first = o.take(2).joinToString(", ") { it.label.substringBefore(' ') }
        return "with $first" + if (o.size > 2) " +${o.size - 2}" else ""
    }

    fun rsvpLine(e: CalEvent): String {
        val g = e.guests
        if (g.size < 2) return ""
        val accepted = g.count { it.rsvp == Rsvp.Accepted }
        val declined = g.count { it.rsvp == Rsvp.Declined }
        val pending = g.count { it.rsvp == Rsvp.Pending || it.rsvp == Rsvp.Tentative }
        return buildList {
            add("$accepted of ${g.size} accepted")
            if (declined > 0) add("$declined declined")
            if (pending > 0 && accepted + declined + pending == g.size && pending < g.size) add("$pending waiting")
        }.joinToString(" · ")
    }

    private fun topic(title: String): String = title.substringAfter(':', title).trim().ifBlank { title }

    /** Plain statements about what we can tell from the title and guest list, worded as guesses. */
    fun guess(e: CalEvent, kind: MeetingKind): String {
        val o = others(e)
        val who = o.firstOrNull()?.label ?: topic(e.title).substringAfter("with ", "").trim().ifBlank { "someone" }
        val count = e.guests.size
        return when (kind) {
            MeetingKind.OneOnOne -> "A one-to-one with $who. The invite doesn't say what it's about."
            MeetingKind.Standup -> "A regular check-in${if (count > 2) " with $count people" else ""}. No agenda was added, so expect updates and blockers."
            MeetingKind.Review -> "Looks like a review of ${topic(e.title)}. The invite doesn't say more."
            MeetingKind.Planning -> "Looks like a planning session: ${topic(e.title)}. No agenda was added."
            MeetingKind.Interview -> "Looks like an interview${if (o.isNotEmpty()) " with ${o.take(2).joinToString(" and ") { it.label }}" else ""}. Check the guest list for who is joining."
            MeetingKind.External -> {
                val ext = externalGuests(e).mapNotNull { it.domain }.filter { it !in personalDomains }.firstOrNull()
                "A meeting with people outside your organisation${if (ext != null) " (${domainName(ext)})" else ""}. The invite doesn't say what it covers."
            }
            MeetingKind.Social -> if (o.isNotEmpty()) "Time with ${o.take(3).joinToString(", ") { it.label.substringBefore(' ') }}." else "Social time."
            MeetingKind.Personal -> "A personal appointment on your calendar."
            MeetingKind.Focus -> "Time set aside for focused work."
            MeetingKind.General -> if (count > 2) "A meeting with $count people. The invite has no description." else "The invite has no description."
        }
    }

    /** A title with numbers and punctuation removed, so "Weekly sync 14" and "Weekly sync 21" are the same series. */
    fun seriesKey(title: String): String = normalise(title)

    private fun normalise(title: String): String =
        title.lowercase().replace(Regex("[0-9]+|[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** The latest earlier voice note recorded for a meeting with the same title. */
    fun lastTime(e: CalEvent, notes: List<SavedNote>, nowMs: Long): LastTime? {
        val key = normalise(e.title)
        if (key.isBlank()) return null
        val before = if (e.startMs > 0) e.startMs else nowMs
        val match = notes.filter { it.eventId != e.id && it.createdAt < before && normalise(it.title) == key && it.lines.isNotEmpty() }
            .maxByOrNull { it.createdAt } ?: return null
        val texts = match.lines.map { it.text }
        val actions = Summarizer.actionItems(texts)
        val bullets = Summarizer.bullets(texts, max = 2)
        val summary = (actions.firstOrNull()?.let { listOf(it) } ?: emptyList()).plus(bullets).distinct().take(2).joinToString(" ")
        return LastTime(match.createdAt, Summarizer.clip(summary, 220), actions.size)
    }
}
