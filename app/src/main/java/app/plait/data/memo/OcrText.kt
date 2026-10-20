package app.plait.data.memo

/** Tidies what the text reader returns so a photographed page reads like text, not like a pile of line fragments. */
object OcrText {
    fun tidy(raw: String): String {
        val lines = raw.replace(' ', ' ').lines().map { it.trim().replace(Regex("[ \\t]+"), " ") }
        val out = StringBuilder()
        var blank = 0
        for ((i, line) in lines.withIndex()) {
            if (line.isEmpty()) { blank++; continue }
            if (out.isNotEmpty()) {
                val prev = out.last()
                out.append(
                    when {
                        blank > 0 -> "\n\n"
                        isListItem(line) -> "\n"
                        prev == '-' && out.length > 1 && out[out.length - 2].isLetter() && line.first().isLowerCase() -> { out.setLength(out.length - 1); "" } // hyphenated word split over two lines
                        prev in ".:!?;" -> "\n"
                        line.first().isLowerCase() || prev == ',' -> " "
                        lines.getOrNull(i - 1)?.length ?: 0 > 45 -> " " // a long line that wraps
                        else -> "\n"
                    }
                )
            }
            out.append(line)
            blank = 0
        }
        return out.toString().trim()
    }

    private fun isListItem(line: String) = Regex("""^([-*•·▪]|\d{1,2}[.)])\s""").containsMatchIn(line)

    /** A short line saying what the note is about, for the list. */
    fun gist(text: String, max: Int = 90): String {
        val first = text.lineSequence().map { it.trim() }.firstOrNull { it.length > 3 } ?: return ""
        return if (first.length <= max) first else first.take(max).substringBeforeLast(' ') + "…"
    }
}
