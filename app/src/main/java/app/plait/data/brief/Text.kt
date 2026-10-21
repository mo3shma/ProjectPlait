package app.plait.data.brief

import app.plait.data.CallLinks

/** Turns a raw invite body (often HTML, always cluttered with join instructions) into the words a person wrote. */
object DescriptionCleaner {
    private val boilerplate: List<Regex> = listOf(
        """^microsoft teams( meeting)?\b.*""",
        """^need help\??.*""",
        """^join (the )?(meeting|microsoft teams|zoom|google meet|with google meet|on your computer|on a video|by phone|from your)\b.*""",
        """^join( zoom| teams| google meet)?:?\s*https?://.*""",
        """^click here to join.*""",
        """^(meeting|webinar) id\b.*""",
        """^(meeting )?(pass ?code|password|pin|access code)\s*[:#].*""",
        """^dial[- ]?in\b.*""",
        """^(join )?by phone\b.*""",
        """^one tap mobile.*""",
        """^find a local number.*""",
        """^phone conference id.*""",
        """^(tenant key|video id|system reference)\b.*""",
        """^for organi[sz]ers\b.*""",
        """^meeting options\b.*""",
        """^reset dial.*""",
        """^learn more\b.*""",
        """^invitation from google calendar.*""",
        """^you are receiving this (email|message).*""",
        """^to stop receiving.*""",
        """^forwarding this invitation.*""",
        """^reply for\b.*""",
        """^view all guest info.*""",
        """^rsvp\b.*""",
        """^or (call|dial)\b.*""",
        """^topic:.*""",
        """^time:.*""",
        """^https?://\S+$""",
        """^[_=\-\s]{4,}$""",
        """^[(+]?\d[\d\s\-().+,#;x]{8,}(\s+\w+.*)?$""", // dial-in numbers
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    internal val entities = mapOf("&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'", "&#x27;" to "'")

    /** CSS or code that leaked out of an HTML invite ("color: #1a73e8 !important;", "a { … }"). */
    fun isCode(line: String): Boolean =
        line.contains('{') || line.contains('}') || line.contains("!important") || Regex("#[0-9a-fA-F]{3,6}\\b").containsMatchIn(line) ||
            Regex("^[a-z-]+\\s*:\\s*[^ ]+;$").matches(line)

    fun clean(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var t = CallLinks.unwrapSafeLinks(raw.replace("&amp;", "&"))
        // Style sheets, scripts and comments are not words anyone wrote (Outlook and Google put CSS in invites).
        t = t.replace(Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>"), "").replace(Regex("(?s)<!--.*?-->"), "")
        if (Regex("<[a-zA-Z/!][^>]*>").containsMatchIn(t)) {
            t = t.replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>|</h[1-6]>|</ul>|</ol>"), "\n")
                .replace(Regex("(?i)<li[^>]*>"), "\n- ")
                .replace(Regex("<[^>]+>"), "")
        }
        for ((k, v) in entities) t = t.replace(k, v)
        t = t.replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        t = t.replace("\r", "\n").replace("\t", " ").replace(" ", " ")

        val kept = ArrayList<String>()
        for (rawLine in t.split('\n')) {
            val line = rawLine.trim().replace(Regex("[ ]{2,}"), " ")
            if (line.isEmpty()) { if (kept.lastOrNull()?.isNotEmpty() == true) kept.add(""); continue }
            if (boilerplate.any { it.matches(line) } || isCode(line)) continue
            // Inline links add nothing to a summary.
            val noUrls = line.replace(Regex("""https?://\S+"""), "").replace(Regex("\\s{2,}"), " ").trim()
            if (noUrls.isNotEmpty()) kept.add(noUrls)
        }
        while (kept.lastOrNull()?.isEmpty() == true) kept.removeAt(kept.lastIndex)
        return kept.joinToString("\n").trim()
    }
}

/** Sentence ranking on frequency alone. No model, no network, same answer every time. */
object Summarizer {
    internal val stop = (
        "a about above after again all also am an and any are as at be because been before being below between both but by can " +
            "could did do does doing down during each few for from further had has have having he her here hers him his how i if in " +
            "into is it its just let like me more most my no nor not now of off on once only or other our out over own same she " +
            "should so some such than that the their them then there these they this those through to too under until up us very " +
            "was we were what when where which while who whom why will with would you your yours get got going"
        ).split(' ').toSet()

    private val actionPattern = Regex(
        """\b(need(s)? to|have to|has to|will|going to|should|must|let'?s|follow(ing)? up|action item|to-?do|remember to|don'?t forget|""" +
            """(by|before|until) (monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|tonight|next week|end of (the )?(day|week|month)))\b""",
        RegexOption.IGNORE_CASE,
    )

    fun sentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+|\\n+")).map { it.trim().trimStart('-', '•', '*', ' ') }.filter { it.split(' ').size >= 3 }

    private fun words(s: String): List<String> =
        Regex("[a-zA-Z']{3,}").findAll(s.lowercase()).map { it.value }.filter { it !in stop }.toList()

    /** The [max] most representative sentences, kept in their original order and trimmed to [maxChars]. */
    fun extract(text: String, max: Int = 2, maxChars: Int = 240): String {
        val all = sentences(text)
        if (all.isEmpty()) return ""
        if (all.size <= max) return clip(all.joinToString(" "), maxChars)

        val freq = HashMap<String, Int>()
        all.forEach { s -> words(s).forEach { w -> freq[w] = (freq[w] ?: 0) + 1 } }
        val scored = all.mapIndexed { i, s ->
            val ws = words(s)
            val base = if (ws.isEmpty()) 0.0 else ws.sumOf { (freq[it] ?: 0).toDouble() } / Math.pow(ws.size.toDouble(), 0.6)
            val position = if (i == 0) 1.35 else if (i == 1) 1.1 else 1.0
            i to base * position
        }
        val chosen = scored.sortedByDescending { it.second }.take(max).map { it.first }.sorted()
        return clip(chosen.joinToString(" ") { all[it] }, maxChars)
    }

    /** Short bullets for a voice-note transcript. */
    fun bullets(lines: List<String>, max: Int = 3): List<String> {
        val joined = lines.joinToString(". ") { it.trim().trimEnd('.') }.ifBlank { return emptyList() }
        val all = sentences(joined)
        if (all.size <= max) return all.map(::tidy)
        val freq = HashMap<String, Int>()
        all.forEach { s -> words(s).forEach { w -> freq[w] = (freq[w] ?: 0) + 1 } }
        val top = all.mapIndexed { i, s ->
            val ws = words(s)
            i to (if (ws.isEmpty()) 0.0 else ws.sumOf { (freq[it] ?: 0).toDouble() } / Math.pow(ws.size.toDouble(), 0.6))
        }.sortedByDescending { it.second }.take(max).map { it.first }.sorted()
        return top.map { tidy(all[it]) }
    }

    /** Sentences that sound like something someone has to do. */
    fun actionItems(lines: List<String>, max: Int = 5): List<String> =
        sentences(lines.joinToString(". ") { it.trim().trimEnd('.') })
            .filter { actionPattern.containsMatchIn(it) }
            .map(::tidy)
            .distinct()
            .take(max)

    fun tidy(s: String): String = s.trim().trimEnd(',', ';').replaceFirstChar { it.uppercase() }.let { if (it.last() in ".!?") it else "$it." }

    fun clip(s: String, max: Int): String {
        if (s.length <= max) return s
        val cut = s.take(max).substringBeforeLast(' ', s.take(max)).trimEnd(',', ';', ':', '.')
        return "$cut…"
    }
}

/** Titles from invites carry stray spaces; collapse them before a title goes into a sentence. */
fun tidyTitle(title: String): String = title.trim().replace(Regex("\\s+"), " ")
