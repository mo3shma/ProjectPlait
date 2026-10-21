package app.plait.data.memo

import app.plait.ai.Ai

/** The plain logic behind summaries and task ideas for written and photo notes. */
object MemoAi {
    private val link = Regex("""(?i)(https?://|www\.)\S+|\S+@\S+\.\S+""")
    private const val MIN_LETTERS = 25
    private const val MIN_WORDS = 6
    private const val MAX_WORDS = 45

    fun hash(text: String): Int = text.trim().replace(Regex("""\s+"""), " ").hashCode()

    /** Text notes and photo notes with enough to say: not a bare number, link or code. */
    fun worthAnalysing(memo: Memo): Boolean = memo.kind != MemoKind.Checklist && worthAnalysing(memo.text)

    fun worthAnalysing(text: String): Boolean {
        val t = link.replace(text, " ")
        return t.count { it.isLetter() } >= MIN_LETTERS && t.split(Regex("""\s+""")).count { it.any(Char::isLetter) } >= MIN_WORDS
    }

    /** Same shape as the About card's engine id, so "the engine changed" means the same thing everywhere. */
    fun engineId(st: Ai.Status): String = "${st.engine.name}:${st.modelId.orEmpty()}"

    /** True when [memo] has no summary for its current text (and, when [checkEngine] is given, that engine). */
    fun needsSummary(memo: Memo, checkEngine: String? = null): Boolean =
        memo.ai == null || memo.aiHash != hash(memo.text) || (checkEngine != null && memo.aiEngine != checkEngine)

    /** Without an AI: the first one or two meaningful sentences, at most about 45 words. */
    fun rulesSummary(text: String): String? {
        val sentences = link.replace(text, " ").split(Regex("""(?<=[.!?؟۔])\s+|\n+"""))
            .map { it.trim().trimStart('-', '•', '*', '·', ' ').replace(Regex("""\s+"""), " ") }
            .filter { s -> s.count { it.isLetter() } >= 8 && s.split(' ').size >= 3 }
        if (sentences.isEmpty()) return null
        val kept = ArrayList<String>()
        var words = 0
        for (s in sentences.take(2)) {
            val n = s.split(' ').size
            if (kept.isNotEmpty() && words + n > MAX_WORDS) break
            kept += s; words += n
        }
        val all = kept.joinToString(" ").split(' ')
        val out = if (all.size > MAX_WORDS) all.take(MAX_WORDS).joinToString(" ").trimEnd(',', ';', ':', '.', '،') + "…" else all.joinToString(" ")
        return if (out.last() in ".!?…؟۔") out else "$out."
    }

    /** Ignores a second trigger for the same note within 2 seconds (an editor that saves twice, a double tap). */
    object Guard {
        private val last = HashMap<String, Long>()
        @Synchronized fun allow(id: String, now: Long = System.currentTimeMillis()): Boolean {
            val p = last[id]
            if (p != null && now - p < 2000) return false
            last[id] = now
            return true
        }
    }
}
