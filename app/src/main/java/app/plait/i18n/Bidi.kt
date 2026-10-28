package app.plait.i18n

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

/**
 * Text people wrote or received (note text, an invite, a meeting title, a name) keeps its own direction: an Arabic note in the
 * English app reads right to left and sits at the right, an English name in the Arabic app reads left to right. Compose's
 * default is the app's direction, so use this for user content, never for the app's own words.
 *
 *     Text(note.text, style = PlaitType.body.userText())
 */
fun TextStyle.userText(): TextStyle = copy(textDirection = TextDirection.Content)

object Bidi {
    /**
     * Wraps a name, number, time or title that goes into a translated sentence in a Unicode isolate (FSI ... PDI), so a Latin
     * name or a "3:30 PM" inside Arabic, or an Arabic name inside an English sentence, cannot reorder the words around it.
     * English output is left exactly as it was, so only the Arabic app gets the (invisible) marks.
     *
     *     Txt.s(R.string.meet_with_name, Bidi.isolate(name))
     */
    fun isolate(s: String): String = if (AppLocale.isRtl && s.isNotEmpty()) "⁨$s⁩" else s

    /**
     * Pieces of user text (phrases of a transcript, items of a list) shown as one line or paragraph. When they mix Arabic and
     * Latin script each piece keeps its own direction, so "Ahmed: the numbers look good." inside an Arabic paragraph keeps its
     * full stop at its end. Text in one script is joined exactly as before, in either app language.
     */
    fun join(parts: List<String>, separator: String = " "): String {
        val mixed = parts.any { p -> p.any { it in '؀'..'ۿ' } } && parts.any { p -> p.any { it in 'A'..'Z' || it in 'a'..'z' } }
        return if (mixed) parts.joinToString(separator) { "⁨$it⁩" } else parts.joinToString(separator)
    }
}
