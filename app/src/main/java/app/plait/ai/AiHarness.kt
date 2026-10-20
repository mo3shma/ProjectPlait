package app.plait.ai

import android.content.Context
import org.json.JSONObject

/**
 * The one way Plait asks a model for something it will show as fact:
 *  1. the input is cleaned in code first (HTML, styles, links, dial-ins never reach the model);
 *  2. the rules go in the system message, the material in the user message, fenced so it cannot pose as instructions;
 *  3. a low temperature, so the model keeps to the likeliest words;
 *  4. the answer must be one JSON object with fixed keys; a broken answer gets one retry with a reminder;
 *  5. every field is checked in code (length, no code or links, no names or numbers that are not in the source) and
 *     anything that fails is dropped, never shown.
 * When nothing survives, the caller falls back to plain rules.
 */
object AiHarness {
    /** Pulls the first {...} object out of what the model wrote. */
    fun parseJson(text: String): JSONObject? {
        val t = text.replace("\\n", "\n").replace("```json", "").replace("```", "")
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(t.substring(start, end + 1)) }.getOrNull()
    }

    suspend fun json(context: Context, system: String, user: String, background: Boolean = true): JSONObject? {
        val first = Ai.generate(context, user, background, system, temperature = 0.2f) ?: return null
        parseJson(first.text)?.let { return it }
        val retry = Ai.generate(context, "$user\n\nYour last reply was not a single JSON object. Reply with the JSON object only.", false, system, temperature = 0.1f)
        return retry?.let { parseJson(it.text) }
    }

    private val code = Regex("(?i)(https?://|www\\.|@media|!important|#[0-9a-f]{3,6}\\b|[{};<>]|passcode|meeting id|dial[- ]?in|\\+\\d{6,})")

    /**
     * A field the model wrote, if it is fit to show: plain words, at most [maxWords], and every number and every
     * capitalised word (after the first) found in [source].
     */
    fun checked(value: String?, source: String, maxWords: Int): String? {
        val v = value?.trim()?.trim('"', '\'', '-', ' ')?.replace(Regex("\\s+"), " ") ?: return null
        if (v.isEmpty() || v.equals("none", true) || v.equals("n/a", true) || code.containsMatchIn(v)) return null
        val words = v.split(' ')
        val src = source.lowercase()
        val numbersOk = Regex("\\d[\\d:.,/]*").findAll(v).all { it.value.trimEnd('.', ',') in src }
        // Capitalised words that do not start a sentence must be in the source: they are likely names.
        val namesOk = words.indices.drop(1).filter { i -> words[i - 1].last() !in ".!?:" }
            .map { words[it].trim(',', '.', ';', ':', '(', ')', '"') }.filter { it.length >= 3 && it[0].isUpperCase() }.all { it.lowercase() in src }
        if (!numbersOk || !namesOk) return null
        val cut = if (words.size > maxWords) words.take(maxWords).joinToString(" ").trimEnd(',', ';', ':') + "…" else v
        return cut.replaceFirstChar { it.uppercase() }
    }

    /**
     * A short paragraph the model wrote, if it is fit to show: the same checks as [checked], then cut back to whole
     * sentences, at most [maxSentences] and [maxWords]. A first sentence that is too long on its own is cut with "…".
     */
    fun paragraph(value: String?, source: String, maxWords: Int, maxSentences: Int = 2): String? {
        val v = checked(value, source, Int.MAX_VALUE) ?: return null
        val sentences = v.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val kept = ArrayList<String>()
        var words = 0
        for (s in sentences.take(maxSentences)) {
            val n = s.split(' ').size
            if (kept.isNotEmpty() && words + n > maxWords) break
            kept += s; words += n
        }
        val text = kept.joinToString(" ")
        val all = text.split(' ')
        val out = if (all.size > maxWords) all.take(maxWords).joinToString(" ").trimEnd(',', ';', ':', '.') + "…" else text
        return if (out.last() in ".!?…") out else "$out."
    }

    private val generic = setOf(
        "meeting", "call", "sync", "chat", "catch", "catchup", "check", "checkin", "weekly", "daily", "monthly", "quarterly",
        "the", "and", "with", "for", "about", "team", "session", "time", "invite", "invitation", "update", "updates", "follow",
    )

    /** The words of a title that say what it is about, without "weekly", "sync", "meeting" and the like. */
    fun topicWords(title: String): List<String> =
        title.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 && it !in generic && !it.all(Char::isDigit) }

    /**
     * True when a guess stays on the title's subject: it shares at least one of the title's topic words (by its first
     * five letters, so "revising" matches "revision"). A title with no topic words at all lets any careful guess through.
     */
    fun onTopic(guess: String, title: String): Boolean {
        val topics = topicWords(title)
        if (topics.isEmpty()) return true
        val g = guess.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }
        return topics.any { t -> val a = t.take(5); g.any { w -> val b = w.take(5); a.startsWith(b) || b.startsWith(a) } }
    }
}
