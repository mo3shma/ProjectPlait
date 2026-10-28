package app.plait.data.brief

import app.plait.data.CalEvent
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.SavedNote
import app.plait.R
import app.plait.i18n.Bidi
import app.plait.i18n.Txt

/** What kind of meeting this looks like. Also drives the card colour. */
enum class MeetingKind(private val res: Int) {
    OneOnOne(R.string.gen_kind_one_on_one), Standup(R.string.gen_kind_standup), Review(R.string.gen_kind_review),
    Planning(R.string.gen_kind_planning), Interview(R.string.gen_kind_interview), External(R.string.gen_kind_external),
    Social(R.string.gen_kind_social), Personal(R.string.gen_kind_personal), Focus(R.string.gen_kind_focus), General(R.string.gen_kind_general);

    /** The tag in the app's language ("1:1", "Review"). */
    val label: String get() = Txt.s(res)
}

/** "A, B and C" in the app's language, each item kept in its own direction. */
internal fun listText(items: List<String>): String {
    val iso = items.map(Bidi::isolate)
    return if (iso.size < 2) iso.joinToString() else Txt.s(R.string.gen_and, iso.dropLast(1).joinToString(Txt.s(R.string.gen_list_sep)), iso.last())
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
            if (e.recurring) add(Txt.s(R.string.gen_chip_repeats))
            if (ext.isNotEmpty()) {
                val named = ext.mapNotNull { it.domain }.filter { it !in personalDomains }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                add(if (named != null) Txt.s(R.string.gen_chip_external_org, Bidi.isolate(domainName(named))) else Txt.s(R.string.gen_chip_external_guests))
            }
            if (e.guests.size > 2) add(Txt.plural(R.plurals.gen_chip_people, e.guests.size))
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
        val first = o.take(2).joinToString(Txt.s(R.string.gen_list_sep)) { Bidi.isolate(it.label.substringBefore(' ')) }
        return if (o.size > 2) Txt.s(R.string.gen_with_more, first, o.size - 2) else Txt.s(R.string.gen_with, first)
    }

    fun rsvpLine(e: CalEvent): String {
        val g = e.guests
        if (g.size < 2) return ""
        val accepted = g.count { it.rsvp == Rsvp.Accepted }
        val declined = g.count { it.rsvp == Rsvp.Declined }
        val pending = g.count { it.rsvp == Rsvp.Pending || it.rsvp == Rsvp.Tentative }
        return buildList {
            add(Txt.s(R.string.gen_rsvp_accepted, accepted, g.size))
            if (declined > 0) add(Txt.s(R.string.gen_rsvp_declined, declined))
            if (pending > 0 && accepted + declined + pending == g.size && pending < g.size) add(Txt.s(R.string.gen_rsvp_waiting, pending))
        }.joinToString(" · ")
    }

    private fun topic(title: String): String = title.substringAfter(':', title).trim().ifBlank { title }

    /** Plain statements about what we can tell from the title and guest list, worded as guesses. */
    fun guess(e: CalEvent, kind: MeetingKind): String {
        val o = others(e)
        val who = o.firstOrNull()?.label?.let(Bidi::isolate)
            ?: topic(e.title).substringAfter("with ", "").trim().takeIf { it.isNotBlank() }?.let(Bidi::isolate)
            ?: Txt.s(R.string.gen_guess_someone)
        val count = e.guests.size
        val subject = Bidi.isolate(topic(e.title))
        return when (kind) {
            MeetingKind.OneOnOne -> Txt.s(R.string.gen_guess_one_on_one, who)
            MeetingKind.Standup -> if (count > 2) Txt.plural(R.plurals.gen_guess_standup_people, count) else Txt.s(R.string.gen_guess_standup)
            MeetingKind.Review -> Txt.s(R.string.gen_guess_review, subject)
            MeetingKind.Planning -> Txt.s(R.string.gen_guess_planning, subject)
            MeetingKind.Interview -> if (o.isNotEmpty()) Txt.s(R.string.gen_guess_interview_with, listText(o.take(2).map { it.label })) else Txt.s(R.string.gen_guess_interview)
            MeetingKind.External -> {
                val ext = externalGuests(e).mapNotNull { it.domain }.filter { it !in personalDomains }.firstOrNull()
                if (ext != null) Txt.s(R.string.gen_guess_external_org, Bidi.isolate(domainName(ext))) else Txt.s(R.string.gen_guess_external)
            }
            MeetingKind.Social -> if (o.isNotEmpty()) Txt.s(R.string.gen_guess_social_with, o.take(3).joinToString(Txt.s(R.string.gen_list_sep)) { Bidi.isolate(it.label.substringBefore(' ')) }) else Txt.s(R.string.gen_guess_social)
            MeetingKind.Personal -> Txt.s(R.string.gen_guess_personal)
            MeetingKind.Focus -> Txt.s(R.string.gen_guess_focus)
            MeetingKind.General -> if (count > 2) Txt.plural(R.plurals.gen_guess_general_people, count) else Txt.s(R.string.gen_guess_general)
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
