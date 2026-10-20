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
        val namesOk = words.drop(1).map { it.trim(',', '.', ';', ':', '(', ')', '"') }.filter { it.length >= 3 && it[0].isUpperCase() }.all { it.lowercase() in src }
        if (!numbersOk || !namesOk) return null
        val cut = if (words.size > maxWords) words.take(maxWords).joinToString(" ").trimEnd(',', ';', ':') + "…" else v
        return cut.replaceFirstChar { it.uppercase() }
    }
}
