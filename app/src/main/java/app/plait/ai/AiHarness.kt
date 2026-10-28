package app.plait.ai

import android.content.Context
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.Txt
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
 *
 * The answer is written in the app's language whatever the material's language: in Arabic every system message gets
 * [withLanguage]'s rule (Modern Standard Arabic, names, numbers and quotes kept as written, JSON keys left in English), and
 * [checked] drops a field that is not mostly Arabic.
 */
object AiHarness {
    /** Pulls the first {...} object out of what the model wrote. */
    fun parseJson(text: String): JSONObject? {
        val t = text.replace("\\n", "\n").replace("```json", "").replace("```", "")
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val s = t.substring(start, end + 1)
        // Models writing Arabic sometimes separate items with the Arabic comma.
        return runCatching { JSONObject(s) }.getOrNull() ?: runCatching { JSONObject(s.replace(Regex("([\"\\]}])\\s*،"), "$1,")) }.getOrNull()
    }

    suspend fun json(context: Context, system: String, user: String, background: Boolean = true): JSONObject? {
        val sys = withLanguage(system)
        val first = Ai.generate(context, user, background, sys, temperature = 0.2f) ?: return null
        parseJson(first.text)?.let { return it }
        val retry = Ai.generate(context, "$user\n\n${Txt.s(R.string.ai_retry_json)}", false, sys, temperature = 0.1f)
        return retry?.let { parseJson(it.text) }
    }

    /** [system] with the rule to write in the app's language. English prompts are left exactly as they are. */
    fun withLanguage(system: String): String {
        if (!AppLocale.isRtl) return system
        val rule = Txt.s(R.string.ai_rule_output_language)
        return if (system.contains(rule)) system else "$system\n$rule"
    }

    private val code = Regex("(?i)(https?://|www\\.|@media|!important|#[0-9a-f]{3,6}\\b|[{};<>]|passcode|meeting id|dial[- ]?in|\\+\\d{6,})")

    /** What a model writes when it has nothing: never shown. */
    private val nothing = setOf("لا يوجد", "لا توجد", "لا شيء", "لا شي", "لا احد", "لا ينطبق", "غير متوفر", "غير متاح", "غير مذكور")

    /** A title before an Arabic name ("السيد أحمد", "للدكتورة سارة"): the name after it must be in the source. */
    private val arabicTitle = Regex("(?<!\\p{L})[وف]?[بكل]?(?:ال|ل)(?:سيد|سيده|استاذ|استاذه|دكتور|دكتوره|مهندس|مهندسه)\\s+(\\p{L}+)|(?<!\\p{L})د\\.\\s*(\\p{L}+)")

    private const val PUNCT = ",.;:()\"،؛؟«»"

    /**
     * A field the model wrote, if it is fit to show: plain words, at most [maxWords], and every number and every
     * capitalised word (after the first) found in [source]. Arabic answers: digits are compared (and shown) as 0-9, a Latin
     * name counts even with an Arabic prefix stuck to it ("لـGoogle"), an Arabic name after a title (السيد، الدكتورة...) must be
     * in the source once clitics, marks and letter variants are set aside, and in the Arabic app a field that is not
     * mostly Arabic is dropped.
     */
    fun checked(value: String?, source: String, maxWords: Int): String? {
        val v = value?.let(ArabicText::westernDigits)?.trim()?.trim('"', '\'', '-', ' ', '«', '»')?.replace(Regex("\\s+"), " ") ?: return null
        if (v.isEmpty() || v.equals("none", true) || v.equals("n/a", true) || code.containsMatchIn(v)) return null
        val arabic = ArabicText.has(v)
        if (arabic && ArabicText.norm(v).trimEnd('.', '!') in nothing) return null
        if (AppLocale.isRtl && ArabicText.share(v) < 0.3) return null
        val words = v.split(' ')
        val src = ArabicText.westernDigits(source).lowercase()
        val numbersOk = Regex("\\d[\\d:.,/]*").findAll(v).all { it.value.trimEnd('.', ',') in src }
        // Capitalised words that do not start a sentence must be in the source: they are likely names. In Arabic there
        // are no capitals, so a Latin word is a name wherever it stands.
        val namesOk = words.indices.filter { i -> (arabic || i > 0) && (i == 0 || words[i - 1].last() !in ".!?:") }
            .map { latin(words[it]) }.filter { it.length >= 3 && it[0].isUpperCase() }.all { it.lowercase() in src }
        if (!numbersOk || !namesOk || (arabic && !titledNamesOk(v, source))) return null
        val cut = if (words.size > maxWords) words.take(maxWords).joinToString(" ").trimEnd(',', ';', ':', '،', '؛') + "…" else v
        return cut.replaceFirstChar { it.uppercase() }
    }

    /** A word without punctuation, and without an Arabic prefix or tatweel stuck to a Latin word ("وZoom", "بـGoogle"). */
    private fun latin(word: String): String {
        val w = word.trim { it in PUNCT }
        val rest = w.trimStart { ArabicText.isLetter(it) || it == 'ـ' }
        return if (rest.isNotEmpty() && rest[0] in 'A'..'z') rest else w
    }

    private fun titledNamesOk(value: String, source: String): Boolean {
        val names = arabicTitle.findAll(ArabicText.norm(value)).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.filter(ArabicText::has).toList()
        if (names.isEmpty()) return true
        val vocab = ArabicText.vocabulary(source)
        return names.all { n -> ArabicText.bare(n).any { it in vocab } }
    }

    /**
     * A short paragraph the model wrote, if it is fit to show: the same checks as [checked], then cut back to whole
     * sentences, at most [maxSentences] and [maxWords]. A first sentence that is too long on its own is cut with "…".
     */
    fun paragraph(value: String?, source: String, maxWords: Int, maxSentences: Int = 2): String? {
        val v = checked(value, source, Int.MAX_VALUE) ?: return null
        val sentences = v.split(Regex("(?<=[.!?؟])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        val kept = ArrayList<String>()
        var words = 0
        for (s in sentences.take(maxSentences)) {
            val n = s.split(' ').size
            if (kept.isNotEmpty() && words + n > maxWords) break
            kept += s; words += n
        }
        val text = kept.joinToString(" ")
        val all = text.split(' ')
        val out = if (all.size > maxWords) all.take(maxWords).joinToString(" ").trimEnd(',', ';', ':', '.', '،', '؛') + "…" else text
        return if (out.last() in ".!?…؟") out else "$out."
    }

    private val generic = setOf(
        "meeting", "call", "sync", "chat", "catch", "catchup", "check", "checkin", "weekly", "daily", "monthly", "quarterly",
        "the", "and", "with", "for", "about", "team", "session", "time", "invite", "invitation", "update", "updates", "follow",
    ) + listOf(
        "اجتماع", "مكالمة", "لقاء", "مزامنة", "دردشة", "متابعة", "أسبوعي", "يومي", "شهري", "ربعي", "فريق", "جلسة", "وقت", "دعوة",
        "تحديث", "تحديثات", "حول", "بشأن", "عن", "مع",
    ).map(ArabicText::stem)

    /** The words of a title that say what it is about, without "weekly", "sync", "meeting" and the like. */
    fun topicWords(title: String): List<String> =
        ArabicText.norm(title).split(Regex("[^\\p{L}\\p{N}]+")).map { if (ArabicText.has(it)) ArabicText.stem(it) else it }
            .filter { it.length >= 3 && it !in generic && !it.all(Char::isDigit) }

    /**
     * True when a guess stays on the title's subject: it shares at least one of the title's topic words (by its first
     * five letters, so "revising" matches "revision"). A title with no topic words at all lets any careful guess through.
     */
    fun onTopic(guess: String, title: String): Boolean {
        val topics = topicWords(title)
        if (topics.isEmpty()) return true
        val g = ArabicText.norm(guess).split(Regex("[^\\p{L}\\p{N}]+")).map { if (ArabicText.has(it)) ArabicText.stem(it) else it }.filter { it.length >= 3 }
        return topics.any { t -> val a = t.take(5); g.any { w -> val b = w.take(5); a.startsWith(b) || b.startsWith(a) } }
    }
}
