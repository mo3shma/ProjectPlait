package app.plait.ai

/**
 * Arabic for the AI checks and the email heuristics. [norm] and [bare] are for comparing only; text shown to people is
 * never rewritten, except [westernDigits] (Plait always shows 0-9). English text passes through [norm] as plain lowercase.
 */
object ArabicText {
    private val marks = Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]") // tashkeel, Quranic marks, tatweel
    private val bidi = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val word = Regex("[\\u0621-\\u063A\\u0641-\\u064A\\u0671-\\u06D3\\u064B-\\u065F\\u0670\\u0640]+")

    fun isLetter(c: Char): Boolean = c in 'ء'..'ي' || c in 'ٱ'..'ۓ'

    fun has(s: String): Boolean = s.any(::isLetter)

    /** Share of the letters in [s] that are Arabic, 0..1 (1 when there are no letters). */
    fun share(s: String): Double {
        val letters = s.count { it.isLetter() }
        return if (letters == 0) 1.0 else s.count(::isLetter).toDouble() / letters
    }

    /** ٠-٩ and ۰-۹ as 0-9, the Arabic decimal and thousands marks as . and , and ٪ as %. */
    fun westernDigits(s: String): String {
        if (s.none { it in '٠'..'٬' || it in '۰'..'۹' }) return s
        return buildString(s.length) {
            for (c in s) append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '٫' -> '.'
                    '٬' -> ','
                    '٪' -> '%'
                    else -> c
                },
            )
        }
    }

    /** For comparing: lowercase, Western digits, no tashkeel, tatweel or direction marks, and أإآٱ->ا, ى->ي, ة->ه. */
    fun norm(s: String): String {
        val t = westernDigits(s).lowercase()
        if (!has(t)) return t.replace(bidi, "")
        return t.replace(marks, "").replace(bidi, "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ٱ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
    }

    /** The Arabic words of [s], as written (marks included). */
    fun words(s: String): List<String> = word.findAll(s).map { it.value }.toList()

    /**
     * The ways a (normalised) word can be read once its attached clitics are taken off: and/so (و ف), by/to/like (ب ل ك),
     * the article (ال, and لل for ل + ال). "وبالميزانيه" gives itself, "بالميزانيه", "الميزانيه" and "ميزانيه". The word
     * itself always comes first, so a name that starts with one of these letters (وليد) still matches itself.
     */
    fun bare(word: String): List<String> {
        val out = LinkedHashSet<String>()
        fun article(w: String) {
            out += w
            if (w.length >= 4 && w.startsWith("ال")) out += w.drop(2)
            if (w.length >= 4 && w.startsWith("لل")) { out += "ا" + w.drop(1); out += w.drop(2) }
        }
        fun prep(w: String) {
            article(w)
            if (w.length >= 4 && w[0] in "بلك") article(w.drop(1))
        }
        prep(word)
        if (word.length >= 4 && word[0] in "وف") prep(word.drop(1))
        return out.toList()
    }

    /** A light stem for matching words of a title against words of an email: clitics off, then a plural or ending. */
    fun stem(word: String): String {
        var w = bare(norm(word)).minByOrNull { it.length } ?: return ""
        w = when {
            w.length >= 5 && (w.endsWith("ات") || w.endsWith("ون") || w.endsWith("ين")) -> w.dropLast(2)
            w.length >= 4 && w.endsWith("ه") -> w.dropLast(1)
            else -> w
        }
        return w
    }

    /** Every [bare] reading of every Arabic word in [s], and the other words lowercased: what a name is looked up in. */
    fun vocabulary(s: String): Set<String> {
        val n = norm(s)
        val out = HashSet<String>()
        n.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.forEach { w -> if (has(w)) out += bare(w) else out += w }
        return out
    }
}
