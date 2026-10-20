package app.plait.data.context

import app.plait.data.CalEvent
import app.plait.data.Guest
import app.plait.data.brief.BriefBuilder
import app.plait.data.brief.MeetingBrief
import app.plait.data.brief.Summarizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

enum class HighlightKind(val label: String, val weight: Double) {
    Decision("Decision", 3.0), Risk("Risk", 2.6), Deadline("Deadline", 2.6), Ask("Ask", 2.5),
    Action("To do", 2.0), Number("Number", 1.5), Attachment("Attachment", 1.0),
}

enum class Strength(val label: String) { Strong("Strong link"), Some("Some link"), Weak("Weak link") }

data class Highlight(val kind: HighlightKind, val text: String, val who: String?, val score: Double, val itemId: String)

data class ItemInsight(
    val itemId: String, val relevance: Double, val strength: Strength, val gist: String,
    val matched: List<String>, val people: List<String>,
)

data class ContextSummary(
    val overview: String,
    val relation: List<String>,
    val strength: Strength,
    val highlights: List<Highlight>,
    val toSettle: List<Highlight>,
    val items: List<ItemInsight>,
    val people: List<String>,
)

/** What a meeting is about, as words with weights, so any text can be scored against it. */
class MeetingTerms(
    val weights: Map<String, Double>,
    /** Token to the guest it names. */
    val guestTokens: Map<String, String>,
    val guestEmails: Map<String, String>,
    val companies: Set<String>,
    val date: LocalDate?,
    val time: String?,
)

object Terms {
    private val generic = setOf(
        "meeting", "call", "sync", "review", "weekly", "daily", "team", "discussion", "update", "updates", "catch", "check",
        "session", "standup", "stand", "monthly", "quarterly", "followup", "follow", "one", "chat", "touch", "base",
    )
    private val personalDomains = setOf("gmail", "googlemail", "outlook", "hotmail", "live", "yahoo", "icloud", "me", "mac", "proton", "protonmail", "aol", "msn", "gmx", "fastmail")

    fun stem(w: String): String = when {
        w.length > 4 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 4 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    fun tokens(s: String): List<String> =
        Regex("[a-zA-Z][a-zA-Z0-9']{2,}").findAll(s.lowercase()).map { stem(it.value.trim('\'')) }.filter { it.length >= 3 && it !in Summarizer.stop }.toList()

    fun of(ev: CalEvent, brief: MeetingBrief, zone: ZoneId): MeetingTerms {
        val w = HashMap<String, Double>()
        fun add(t: String, weight: Double) { if (weight > (w[t] ?: 0.0)) w[t] = weight }
        tokens(ev.title).filter { it !in generic }.forEach { add(it, 3.0) }
        brief.agenda.forEach { a -> tokens(a).filter { it !in generic }.forEach { add(it, 2.0) } }
        if (!brief.aboutIsGuess) tokens(brief.about).filter { it.length >= 3 && it !in generic }.forEach { add(it, 1.0) }
        ev.location?.takeIf { !it.startsWith("http") }?.let { loc -> tokens(loc).filter { it.length >= 4 }.forEach { add(it, 1.0) } }

        val guestTokens = HashMap<String, String>()
        val emails = HashMap<String, String>()
        for (g in ev.guests.filter { !it.self }) {
            g.label.lowercase().split(' ').filter { it.length >= 3 }.forEach { part -> guestTokens[stem(part)] = g.label; add(stem(part), 1.5) }
            g.email?.let { emails[it] = g.label }
        }
        val companies = BriefBuilder.externalGuests(ev).mapNotNull { it.domain }
            .map { it.substringBeforeLast('.').substringAfterLast('.') }.filter { it !in personalDomains }.toSet()
        companies.forEach { add(it, 2.0) }

        val start = ev.startMs.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone) }
        return MeetingTerms(w, guestTokens, emails, companies, start?.toLocalDate(), start?.let { "%02d:%02d".format(it.hour, it.minute) })
    }
}

/** Recognises when a sentence points at the meeting's day or time. */
object DateMentions {
    private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    /** Returns what matched ("Tuesday", "20 Oct", "10:30") or null. */
    fun find(sentence: String, date: LocalDate?, time: String?): String? {
        val s = sentence.lowercase()
        if (date != null) {
            val full = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
            if (Regex("\\b$full\\b|\\b${full.take(3)}\\b").containsMatchIn(s)) return full.replaceFirstChar { it.uppercase() }
            val d = date.dayOfMonth
            val mFull = months[date.monthValue - 1]
            val mShort = mFull.take(3)
            val dayMonth = Regex("\\b$d(st|nd|rd|th)?\\s+(of\\s+)?($mFull|$mShort)\\b|\\b($mFull|$mShort)\\.?\\s+$d(st|nd|rd|th)?\\b")
            if (dayMonth.containsMatchIn(s)) return "$d ${mFull.take(3).replaceFirstChar { it.uppercase() }}"
            val numeric = Regex("\\b(0?${date.monthValue}[/.-]0?$d|0?$d[/.-]0?${date.monthValue})([/.-]\\d{2,4})?\\b|\\b${date.year}-%02d-%02d\\b".format(date.monthValue, d))
            if (numeric.containsMatchIn(s)) return "$d ${mFull.take(3).replaceFirstChar { it.uppercase() }}"
        }
        if (time != null) {
            val h24 = time.substringBefore(':').toInt()
            val mm = time.substringAfter(':')
            val h12 = if (h24 % 12 == 0) 12 else h24 % 12
            val ampm = if (h24 < 12) "am" else "pm"
            val rx = Regex("\\b($h24|%02d)[:.]$mm\\b|\\b$h12[:.]$mm\\s*$ampm\\b|\\b$h12\\s*$ampm\\b".format(h24))
            if (mm == "00" || mm.isNotEmpty()) if (rx.containsMatchIn(s)) return time
        }
        return null
    }
}

object ContextAnalyzer {
    private val trivial = Regex("""^(thanks|thank you|great|sounds good|ok(ay)?|noted|perfect|cheers|got it|will do|no problem)\b[^.!?]{0,30}[.!]?$""", RegexOption.IGNORE_CASE)

    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)
    private val decision = rx("""\b(we('ve| have)? (decided|agreed|approved|confirmed)|(has|have|was|were) (been )?(approved|confirmed|agreed|signed off)|going with|let'?s go with|signed off|it'?s (settled|agreed|confirmed)|final(i[sz]ed)? (decision|plan|quote|price))\b""")
    private val risk = rx("""\b(risk|concern(ed|s)?|blockers?|blocked|delay(ed|s)?|slip(ped|ping)?|behind schedule|worried|problems?|issues?|can'?t|cannot|won'?t|unable|not (possible|able)|escalat\w+|overdue|at risk|cancel(l)?ed)\b""")
    private val deadline = rx("""\b((by|before|until|no later than|due( on| by)?)\s+(eod|eow|cob|end of (the )?(day|week|month|quarter)|monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|tonight|next (week|month)|today|\d{1,2}(st|nd|rd|th)?\b|[a-z]{3,9}\s+\d{1,2})|deadline|due)\b""")
    private val ask = rx("""(\?\s*$|\b(can|could|would|will) (you|we|someone)\b|\bplease\b|\bneed you to\b|\bdo you (know|have)\b|\bany chance\b|\blet me know\b|\bare you able\b)""")
    private val action = rx("""\b(need(s)? to|have to|has to|i'?ll|we'?ll|i will|we will|going to|will (send|share|check|confirm|update|follow)|to-?do|action item|follow(ing)? up|remember to|don'?t forget|make sure|should)\b""")
    private val number = rx("""([$€£]\s?\d[\d,.]*\s?[kKmMbB]?\b|\b\d+(\.\d+)?\s?%|\b\d[\d,]*\s?(users|seats|licen[cs]es|hours|days|weeks|months|people|units|tickets)\b)""")
    private val attachment = rx("""(\battach(ed|ment|ments)\b|\benclosed\b|\bsee the (doc|deck|file|spreadsheet|contract|quote|proposal)\b|\.(pdf|docx?|xlsx?|pptx?|csv)\b)""")

    fun kindOf(sentence: String): HighlightKind? = when {
        decision.containsMatchIn(sentence) -> HighlightKind.Decision
        risk.containsMatchIn(sentence) -> HighlightKind.Risk
        deadline.containsMatchIn(sentence) -> HighlightKind.Deadline
        ask.containsMatchIn(sentence) -> HighlightKind.Ask
        action.containsMatchIn(sentence) -> HighlightKind.Action
        number.containsMatchIn(sentence) -> HighlightKind.Number
        attachment.containsMatchIn(sentence) -> HighlightKind.Attachment
        else -> null
    }

    private class Sentence(val text: String, val who: String?, val itemId: String, val score: Double, val matched: List<String>, val dateHit: String?, val kind: HighlightKind?)

    private fun sentencesOf(text: String): List<String> =
        text.replace('\u2019', '\'').replace('\u2018', '\'').replace('\u201c', '"').replace('\u201d', '"')
            .split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim().trimStart('-', '•', '*', ' ') }
            .filter { it.split(' ').size in 3..45 && !trivial.matches(it) }

    fun score(sentence: String, terms: MeetingTerms): Pair<Double, List<String>> {
        val toks = Terms.tokens(sentence).toSet()
        val matched = toks.filter { it in terms.weights }
        var s = matched.sumOf { terms.weights.getValue(it) }
        val lower = sentence.lowercase()
        if (terms.guestEmails.keys.any { it in lower }) s += 1.5
        return s to matched
    }

    fun analyze(ev: CalEvent, brief: MeetingBrief, items: List<ContextItem>, zone: ZoneId): ContextSummary {
        val terms = Terms.of(ev, brief, zone)
        val all = ArrayList<Sentence>()
        val insights = ArrayList<ItemInsight>()
        val people = LinkedHashSet<String>()
        val termCounts = HashMap<String, Int>()
        val guestHits = LinkedHashSet<String>()
        var anyDate: String? = null
        var guestSender = false

        for (item in items) {
            val messages = Threads.split(item.body, item.from)
            val itemSentences = ArrayList<Sentence>()
            for (m in messages) {
                val who = m.from?.takeIf { it.isNotBlank() }
                if (who != null) people.add(who)
                for (s in sentencesOf(unwrapLinks(m.text))) {
                    val (base, matched) = score(s, terms)
                    val dateHit = DateMentions.find(s, terms.date, terms.time)
                    val total = base + if (dateHit != null) 1.5 else 0.0
                    itemSentences.add(Sentence(s.take(240), who, item.id, total, matched, dateHit, kindOf(s)))
                }
            }
            val headerAddrs = EmailParser.addresses(item.from + " " + item.to)
            headerAddrs.forEach { addr -> terms.guestEmails[addr]?.let { guestHits.add(it) } }
            itemSentences.forEach { s -> s.matched.forEach { t -> termCounts[t] = (termCounts[t] ?: 0) + 1; terms.guestTokens[t]?.let { guestHits.add(it) } } }
            val senderIsGuest = EmailParser.addresses(item.from).any { it in terms.guestEmails } ||
                (messages.firstOrNull()?.from?.lowercase()?.let { f -> terms.guestTokens.keys.any { it in f } } == true)
            if (senderIsGuest) guestSender = true
            itemSentences.firstNotNullOfOrNull { it.dateHit }?.let { if (anyDate == null) anyDate = it }

            // The subject, the sender and the other people on the email say as much as the sentences do.
            val (subjectScore, subjectMatched) = score(item.subject, terms)
            subjectMatched.forEach { t -> termCounts[t] = (termCounts[t] ?: 0) + 1 }
            val guestsOnEmail = headerAddrs.count { it in terms.guestEmails }
            val bonus = subjectScore.coerceAtMost(8.0) + (if (senderIsGuest) 2.0 else 0.0) + (guestsOnEmail - if (senderIsGuest) 1 else 0).coerceIn(0, 2) * 0.8
            val top = itemSentences.map { it.score }.sortedDescending().take(3).sum() + bonus
            val rel = (top / 9.0).coerceIn(0.0, 1.0)
            val strength = if (rel >= 0.55) Strength.Strong else if (rel >= 0.25) Strength.Some else Strength.Weak
            val gist = itemSentences.filter { it.score > 0 }.maxByOrNull { it.score }?.text
                ?: Summarizer.extract(messages.firstOrNull()?.text.orEmpty(), max = 1, maxChars = 160)
            val matchedTerms = itemSentences.flatMap { it.matched }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
            insights.add(ItemInsight(item.id, rel, strength, gist, matchedTerms, messages.mapNotNull { it.from }.distinct()))
            all.addAll(itemSentences)
        }

        val strengthById = insights.associate { it.itemId to it.strength }
        val highlights = all.filter { s ->
            val k = s.kind ?: return@filter false
            val itemStrength = strengthById[s.itemId] ?: Strength.Weak
            s.score > 0 || (itemStrength != Strength.Weak && k.weight >= 2.0)
        }.map { s ->
            val k = s.kind!!
            Highlight(k, s.text, s.who, k.weight + s.score * 0.5 + if (s.who != null && terms.guestTokens.keys.any { it in s.who.lowercase() }) 0.3 else 0.0, s.itemId)
        }.sortedByDescending { it.score }
            .distinctBy { it.text.lowercase().filter { c -> c.isLetterOrDigit() }.take(60) }
            .take(8)

        val toSettle = highlights.filter { it.kind in setOf(HighlightKind.Ask, HighlightKind.Risk, HighlightKind.Deadline, HighlightKind.Action) }.take(5)

        val relevant = all.filter { it.score > 0 }.sortedByDescending { it.score }.take(3)
        val overview = if (relevant.isNotEmpty()) {
            val order = all.withIndex().filter { (_, s) -> relevant.any { it === s } }.map { it.value }
            Summarizer.clip(order.joinToString(" ") { Summarizer.tidy(it.text) }, 300)
        } else {
            Summarizer.extract(items.joinToString("\n") { Threads.split(it.body, it.from).firstOrNull()?.text.orEmpty() }, max = 2, maxChars = 260)
        }

        val best = insights.maxOfOrNull { it.relevance } ?: 0.0
        val overall = if (best >= 0.55) Strength.Strong else if (best >= 0.25) Strength.Some else Strength.Weak
        val relation = buildList {
            termCounts.entries.sortedByDescending { it.value * (terms.weights[it.key] ?: 1.0) }.take(3).forEach { (t, n) ->
                add("Mentions ${t.replaceFirstChar { c -> c.uppercase() }}" + if (n > 1) " ×$n" else "")
            }
            guestHits.take(2).forEach { add("$it is in the thread") }
            if (guestSender && guestHits.isEmpty()) add("From a guest")
            anyDate?.let { add("Mentions $it") }
        }.distinct()

        return ContextSummary(overview, relation, overall, highlights, toSettle, insights, people.toList())
    }
}

/** Suggests which meeting a shared email belongs to. */
object EventMatcher {
    class Match(val event: CalEvent, val score: Double, val reasons: List<String>)

    fun rank(email: ParsedEmail, events: List<CalEvent>, zone: ZoneId, nowMs: Long, limit: Int = 6): List<Match> {
        val text = email.subject + "\n" + email.subject + "\n" + email.body
        val addrs = EmailParser.addresses(email.from + " " + email.to + " " + email.body).toSet()
        val dayMs = 24L * 3600 * 1000
        val candidates = events.filter { !it.allDay && it.startMs > 0 && it.startMs > nowMs - 3 * dayMs && it.startMs < nowMs + 21 * dayMs }
        val ranked = candidates.map { ev ->
            val brief = BriefBuilder.build(ev, emptyList(), nowMs)
            val terms = Terms.of(ev, brief, zone)
            val (textScore, matched) = ContextAnalyzer.score(text, terms)
            val reasons = ArrayList<String>()
            var score = textScore.coerceAtMost(14.0) * 0.6
            matched.filter { (terms.weights[it] ?: 0.0) >= 2.0 }.take(2).forEach { reasons.add("Mentions ${it.replaceFirstChar { c -> c.uppercase() }}") }
            val guestHits = ev.guests.filter { g -> g.email != null && g.email in addrs && !g.self }
            if (guestHits.isNotEmpty()) { score += 2.5 * guestHits.size.coerceAtMost(3); reasons.add("${guestHits.first().label} is a guest") }
            val domains = BriefBuilder.externalGuests(ev).mapNotNull { it.domain }.toSet()
            if (guestHits.isEmpty() && addrs.any { a -> a.substringAfter('@') in domains }) { score += 1.5; reasons.add("Same company as a guest") }
            val dt = (ev.startMs - nowMs).toDouble() / dayMs
            score += when {
                dt in 0.0..7.0 -> 1.0 * (1 - dt / 7.0)
                dt < 0 -> 0.3
                else -> 0.0
            }
            if (dt in 0.0..1.0) reasons.add("Coming up soon")
            Match(ev, score, reasons)
        }
        return ranked.sortedByDescending { it.score }.take(limit)
    }
}
