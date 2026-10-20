package app.plait.data.search

import kotlin.math.ln
import kotlin.math.sqrt

enum class DocKind(val label: String) { Event("Meeting"), Voice("Voice note"), Memo("Note"), Photo("Photo"), Email("Email"), Followup("Follow-up") }

/** One thing that can be found: a meeting, a note, a photo's text, an email. */
data class Doc(
    val id: String,
    val kind: DocKind,
    val eventId: String,
    val title: String,
    val text: String,
    val whenMs: Long = 0,
)

data class Hit(val doc: Doc, val score: Double, val snippet: String)

/**
 * Word search that works for English and Arabic with nothing downloaded. Text is normalised (case, accents, Arabic
 * diacritics and letter variants), lightly stemmed, and ranked with BM25. An optional [semantic] scorer, given the
 * query and a candidate, can lift results that mean the same thing without sharing words.
 */
class SearchIndex(private val docs: List<Doc>) {
    private val tokens: List<List<String>> = docs.map { tokenize(it.title + " " + it.text) }
    private val titleTokens: List<Set<String>> = docs.map { tokenize(it.title).toSet() }
    private val df = HashMap<String, Int>().also { m -> tokens.forEach { t -> t.toSet().forEach { m.merge(it, 1, Int::plus) } } }
    private val avgLen = tokens.map { it.size }.average().takeIf { !it.isNaN() && it > 0 } ?: 1.0

    fun search(query: String, limit: Int = 20, semantic: ((Doc) -> Double)? = null): List<Hit> {
        val q = tokenize(query).distinct()
        if (q.isEmpty() || docs.isEmpty()) return emptyList()
        val n = docs.size.toDouble()
        val raw = DoubleArray(docs.size)
        for (i in docs.indices) {
            val tf = HashMap<String, Int>().also { m -> tokens[i].forEach { m.merge(it, 1, Int::plus) } }
            var score = 0.0
            for (term in q) {
                val f = tf[term] ?: continue
                val idf = ln(1 + (n - (df[term] ?: 0) + 0.5) / ((df[term] ?: 0) + 0.5))
                score += idf * (f * 2.2) / (f + 1.2 * (0.25 + 0.75 * tokens[i].size / avgLen))
                if (term in titleTokens[i]) score += idf * 0.8
            }
            raw[i] = score
        }
        val top = raw.max().takeIf { it > 0 } ?: 0.0
        val scored = docs.indices.map { i ->
            val lexical = if (top > 0) raw[i] / top else 0.0
            val sem = semantic?.invoke(docs[i]) ?: 0.0
            val s = if (semantic != null) 0.6 * lexical + 0.4 * sem.coerceIn(0.0, 1.0) else lexical
            i to s
        }.filter { it.second > 0.05 }
        return scored.sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenByDescending { docs[it.first].whenMs })
            .take(limit).map { (i, s) -> Hit(docs[i], s, snippet(docs[i], q)) }
    }

    companion object {
        private val stop = setOf(
            "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "is", "are", "was", "were", "we", "i", "you", "it", "this", "that",
            "what", "when", "who", "how", "did", "do", "does", "about", "at", "by", "from", "be", "as", "my", "our",
            "في", "من", "على", "الى", "عن", "ما", "ماذا", "هل", "كان", "مع", "هذا", "هذه", "ذلك", "لا", "او", "و",
        )
        private val diacritics = Regex("[ً-ٰٟـ]")

        /** Lowercase, drop accents and Arabic marks, unify Arabic letter variants. */
        fun normalise(s: String): String {
            var t = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFKD)
            t = t.replace(Regex("\\p{Mn}"), "").replace(diacritics, "")
            return t.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ى', 'ي').replace('ة', 'ه')
                .map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }.joinToString("")
        }

        fun stem(w: String): String = when {
            w.length > 6 && w.endsWith("ing") -> w.dropLast(3)
            w.length > 5 && w.endsWith("ed") -> w.dropLast(2)
            w.length > 5 && w.endsWith("es") -> w.dropLast(2)
            w.length > 4 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
            w.length > 5 && w.startsWith("ال") -> w.drop(2)
            else -> w
        }

        fun tokenize(s: String): List<String> =
            normalise(s).split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length > 1 && it !in stop }.map { stem(it) }

        /** The sentence of [doc] that holds the most query words, trimmed. */
        fun snippet(doc: Doc, q: List<String>): String {
            val sentences = doc.text.split(Regex("(?<=[.!?؟\\n])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
            if (sentences.isEmpty()) return doc.title
            val best = sentences.maxByOrNull { s -> tokenize(s).toSet().count { it in q } } ?: sentences.first()
            return if (best.length <= 180) best else best.take(180).substringBeforeLast(' ') + "…"
        }

        fun cosine(a: FloatArray, b: FloatArray): Double {
            var dot = 0.0; var na = 0.0; var nb = 0.0
            for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
            return if (na == 0.0 || nb == 0.0) 0.0 else dot / (sqrt(na) * sqrt(nb))
        }
    }
}

/** The prompt for "ask your notes": the question and the passages found, with numbers so the answer can point to them. */
object AskPrompt {
    fun build(question: String, hits: List<Hit>, maxChars: Int = 2200): String {
        val head = "Answer the question using only the notes below. Cite the note numbers like [1]. If the notes don't say, answer: \"I can't find that in your notes.\" Keep it to three sentences.\n\n"
        val tail = "\n\nQuestion: $question\nAnswer:"
        val room = (maxChars - head.length - tail.length).coerceAtLeast(400)
        val per = room / hits.size.coerceAtLeast(1)
        val body = hits.mapIndexed { i, h -> "[${i + 1}] ${h.doc.kind.label} · ${h.doc.title}: ${h.doc.text.replace(Regex("\\s+"), " ").take(per - 40)}" }.joinToString("\n")
        return head + "Notes:\n" + body + tail
    }
}
