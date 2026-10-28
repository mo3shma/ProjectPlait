package app.plait.data.context

import app.plait.ai.ArabicText

/** One message of a thread, with the quoted history and boilerplate removed. */
data class ThreadMessage(val from: String?, val dateText: String?, val text: String)

object Threads {
    private val wroteLine = Regex("""^\s*On\s.{4,200}?\bwrote:\s*$""", RegexOption.IGNORE_CASE)
    private val onStart = Regex("""^\s*On\s.{4,200}$""", RegexOption.IGNORE_CASE)
    private val originalMarker = Regex("""^\s*[-_=*]{2,}\s*(Original Message|Forwarded message|Forwarded Message|Begin forwarded message)[^-_=*]*[-_=*]*\s*$|^\s*Begin forwarded message:?\s*$""", RegexOption.IGNORE_CASE)
    private val headerLine = Regex("""^\s*\**\s*(From|Sent|Date|To|Cc|Subject)\s*:\**\s*(.*)$""", RegexOption.IGNORE_CASE)
    private val underscoreRule = Regex("""^\s*_{5,}\s*$""")

    // Arabic mail apps. Gmail: "في الثلاثاء، 20 أكتوبر 2026 في 10:15 ص تمت كتابة ما يلي بواسطة Jo Park <jo@x.com>:"; others:
    // "في 20 أكتوبر 2026، الساعة 10:15 ص، كتب أحمد علي <a@x.com>:" or "كتب أحمد <a@x.com> في ...:". Read without direction marks.
    private val bidiMarks = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
    private val arWrote = Regex("""^\s*(?:في|بتاريخ)\s+(.{4,200}?)[،,]?\s*(?:تمت كتاب[ةه] ما يلي بواسط[ةه]|كتبت?)\s+(.{1,120}?)\s*:\s*$""")
    private val arWroteFirst = Regex("""^\s*كتبت?\s+(.{1,120}?)\s+(?:في|بتاريخ)\s+(.{4,200}?)\s*:\s*$""")
    private val clock = Regex("""\d{1,2}[:.]\d{2}""")
    /** Outlook's Arabic marker lines and header names, as ArabicText.norm writes them. */
    private val arMarker = Regex("""^\s*[-_=*]{2,}\s*(الرساله الاصليه|رساله اصليه|الرساله المعاد توجيهها|رساله معاد توجيهها|رساله محوله|الرساله المحوله)[^-_=*]*[-_=*]*\s*$|^\s*بدايه الرساله (المعاد توجيهها|المحوله)\s*:?\s*$""")
    private val arHeaderLine = Regex("""^\s*\**\s*(من|المرسل|ارسلت|ارسل|تم الارسال|تاريخ الارسال|التاريخ|الي|المرسل اليه|نسخه|نسخه الي|الموضوع)\s*:\**\s*(.*)$""")
    private val arKeys = mapOf(
        "من" to "from", "المرسل" to "from", "ارسلت" to "sent", "ارسل" to "sent", "تم الارسال" to "sent", "تاريخ الارسال" to "sent",
        "التاريخ" to "date", "الي" to "to", "المرسل اليه" to "to", "نسخه" to "cc", "نسخه الي" to "cc", "الموضوع" to "subject",
    )

    private fun isMarker(line: String) = originalMarker.matches(line) || (ArabicText.has(line) && arMarker.matches(ArabicText.norm(line)))

    /** A mail header line ("From: ...", "من: ...") as (english key, value), or null. */
    private fun header(line: String): Pair<String, String>? {
        headerLine.matchEntire(line)?.let { return it.groupValues[1].lowercase() to it.groupValues[2].trim() }
        if (!ArabicText.has(line)) return null
        val m = arHeaderLine.matchEntire(ArabicText.norm(line)) ?: return null
        return arKeys.getValue(m.groupValues[1]) to line.replace(bidiMarks, "").substringAfter(':').trim().trim('*').trim()
    }

    /** An Arabic "... كتب Name <address>:" line as (name, date), or null. */
    private fun arabicWrote(line: String): Pair<String?, String?>? {
        if (!ArabicText.has(line)) return null
        val l = line.replace(bidiMarks, "")
        if ('@' !in l && !clock.containsMatchIn(l)) return null
        val (person, date) = arWrote.matchEntire(l)?.let { it.groupValues[2] to it.groupValues[1] }
            ?: arWroteFirst.matchEntire(l)?.let { it.groupValues[1] to it.groupValues[2] } ?: return null
        val name = (Regex("""([^,<>]*?)\s*<[^>]+>\s*$""").find(person)?.groupValues?.get(1) ?: person).trim().trim('"', '،', ',').trim()
        return name.takeIf { it.isNotBlank() && !it.first().isDigit() }?.let { EmailParser.displayName(it) } to date.trim().trimEnd('،', ',').takeIf { it.any(Char::isDigit) }
    }

    private class Sep(val start: Int, val end: Int, val from: String?, val date: String?)

    private fun fromWroteLine(line: String): Pair<String?, String?> {
        val body = line.trim().removePrefix("On ").removePrefix("on ").removeSuffix("wrote:").trim().trimEnd(',')
        // "Mon, 12 Oct 2026 at 10:15, Jo Park <jo@x.com>" -> name after the last comma-time part
        val person = Regex("""([^,<>]*?)\s*<[^>]+>\s*$""").find(body)?.groupValues?.get(1)?.trim()?.trim('"')
        val date = body.substringBefore('<').let { s ->
            Regex("""(?i)(\d{1,2}[:.]\d{2}\s*(am|pm)?)""").find(s)?.let { _ -> s.trim().trimEnd(',') }
        }
        val name = person?.let { p -> p.substringAfterLast(" at ").substringAfterLast(',').trim() }?.takeIf { it.isNotBlank() && !it.first().isDigit() }
        return name?.let { EmailParser.displayName(it) } to date
    }

    private fun findSeparator(lines: List<String>): Sep? {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (wroteLine.matches(line)) {
                val (from, date) = fromWroteLine(line)
                return Sep(i, i + 1, from, date)
            }
            if (onStart.matches(line) && i + 1 < lines.size && lines[i + 1].trim().equals("wrote:", ignoreCase = true)) {
                val (from, date) = fromWroteLine(line + " wrote:")
                return Sep(i, i + 2, from, date)
            }
            arabicWrote(line)?.let { (from, date) -> return Sep(i, i + 1, from, date) }
            if (isMarker(line) || underscoreRule.matches(line)) {
                // Followed by a header block?
                var j = i + 1
                while (j < lines.size && lines[j].isBlank()) j++
                val block = headerBlock(lines, j)
                if (block != null) return Sep(i, block.end, block.from, block.date)
                if (isMarker(line)) return Sep(i, i + 1, null, null)
            }
            val direct = headerBlock(lines, i)
            if (direct != null && i > 0) return Sep(i, direct.end, direct.from, direct.date)
            i++
        }
        return null
    }

    private class Block(val end: Int, val from: String?, val date: String?)

    /** Three or more mail header lines in a row (From/Sent/To/Subject), as Outlook writes them. */
    private fun headerBlock(lines: List<String>, start: Int): Block? {
        var j = start
        var count = 0
        var from: String? = null
        var date: String? = null
        var sawFrom = false
        while (j < lines.size) {
            val (key, v) = header(lines[j]) ?: break
            if (j == start && key != "from") return null
            if (key == "from") { from = EmailParser.displayName(v.replace(Regex("""\s*\[mailto:[^\]]*]"""), "")); sawFrom = true }
            if (key == "sent" || key == "date") date = v
            count++; j++
        }
        return if (sawFrom && count >= 3) Block(j, from, date) else null
    }

    private fun unquote(line: String): String = line.replaceFirst(Regex("^\\s*>+ ?"), "")

    fun split(body: String, fallbackFrom: String? = null): List<ThreadMessage> {
        val out = ArrayList<ThreadMessage>()
        var lines = body.replace("\r\n", "\n").replace("\r", "\n").lines()
        var from = fallbackFrom?.let { EmailParser.displayName(it) }
        var date: String? = null
        var guard = 0
        while (guard++ < 60) {
            val sep = findSeparator(lines)
            val pre = if (sep == null) lines else lines.subList(0, sep.start)
            out.add(ThreadMessage(from, date, Noise.strip(pre)))
            if (sep == null) break
            var rest = lines.drop(sep.end)
            val firstReal = rest.firstOrNull { it.isNotBlank() }
            if (firstReal != null && firstReal.trimStart().startsWith(">")) rest = rest.map(::unquote)
            lines = rest
            from = sep.from
            date = sep.date
        }
        return out.filter { it.text.isNotBlank() }
    }
}

/** Removes what is not the sender's own words: signatures, disclaimers, footers and quoted lines. */
object Noise {
    private val signoff = Regex("""^\s*(best( regards)?|kind regards|warm regards|regards|thanks( and regards)?|thank you|many thanks|cheers|sincerely|yours( sincerely| truly)?|with thanks|talk soon|best wishes|br)[,!.]?\s*$""", RegexOption.IGNORE_CASE)
    private val mobileSig = Regex("""^\s*(sent from my .*|get outlook for .*|sent from (outlook|mail|yahoo).*|sent with .*)$""", RegexOption.IGNORE_CASE)
    private val disclaimer = Regex("""(?i)(this (e-?mail|message|communication)( and any (attachments|files).*)? (is|are|may be|contains?) (confidential|privileged)|intended (solely )?for the (use of the )?(addressee|recipient)|if you (are not|have received).*(intended recipient|in error)|please consider the environment before printing|disclaimer:)""")
    private val footer = Regex("""(?i)(unsubscribe|view (this email )?in (your )?browser|you('re| are) receiving this (email|message) because|manage (your )?(email )?preferences)""")
    private val greeting = Regex("""^\s*(hi|hello|hey|dear|good (morning|afternoon|evening))\b[^.!?\n]{0,40}[,!]?\s*$""", RegexOption.IGNORE_CASE)

    // The same in Arabic, matched on ArabicText.norm (أإآ as ا, ى as ي, ة as ه, no tashkeel).
    private val signoffAr = Regex(
        """^\s*(مع (خالص |اطيب |اجمل |وافر )?(التحيات|التحيه|تحياتي|الشكر|التقدير|الاحترام|الود)( والتقدير| والاحترام)?|تحياتي( وتقديري| واحترامي)?|""" +
            """(مع )?الشكر( والتقدير)?|شكرا( جزيلا| لكم| لك| مقدما)?|وشكرا|خالص (الشكر|التحيات)|مع فائق (الاحترام|التقدير)|احترامي|""" +
            """تقبلوا (فائق |خالص )?(الاحترام|التحيات)|دمتم (بخير|سالمين)|بالتوفيق|وتفضلوا بقبول (فائق )?الاحترام)[،,!.]?\s*$""",
    )
    private val mobileSigAr = Regex("""^\s*(ارسلت من .*|ارسل من .*|مرسل من .*|تم الارسال من .*|احصل علي outlook .*)$""")
    private val disclaimerAr = Regex("""(هذه الرساله.{0,80}(سريه|سري|خاصه|مخصصه)|اذا (تلقيت|وصلتك|استلمت) هذه الرساله (عن طريق|بطريق|عن) الخطا|اخلاء (ال)?مسؤوليه|يرجي مراعاه البيئه قبل الطباعه|الغاء الاشتراك|عرض (هذه الرساله )?في المتصفح)""")
    private val greetingAr = Regex("""^\s*(مرحبا|اهلا|السلام عليكم|صباح الخير|مساء الخير|عزيزي|عزيزتي|الاعزاء|الزملاء الاعزاء|الزملاء الكرام|الساده الكرام|الساده|حضره|تحيه طيبه)(?!\p{L})[^.!?؟\n]{0,40}[،,!]?\s*$""")

    private fun ar(rx: Regex, line: String, whole: Boolean): Boolean =
        ArabicText.has(line) && ArabicText.norm(line).let { if (whole) rx.matches(it) else rx.containsMatchIn(it) }

    fun strip(lines: List<String>): String {
        var kept = lines.filter { !it.trimStart().startsWith(">") }.map { it.trimEnd() }
        // Signature delimiter.
        kept.indexOfFirst { it.trim() == "--" || it.trim() == "-- " }.takeIf { it >= 0 }?.let { kept = kept.subList(0, it) }
        // Disclaimers and footers swallow everything after them.
        kept.indexOfFirst { disclaimer.containsMatchIn(it) || footer.containsMatchIn(it) || ar(disclaimerAr, it, false) }.takeIf { it >= 0 }?.let { kept = kept.subList(0, it) }
        // Mobile footers.
        kept = kept.filter { !mobileSig.matches(it) && !ar(mobileSigAr, it, true) }
        // A sign-off near the end ends the message.
        val tailStart = (kept.size - 8).coerceAtLeast(0)
        val idx = (tailStart until kept.size).firstOrNull { signoff.matches(kept[it]) || ar(signoffAr, kept[it], true) }
        if (idx != null) kept = kept.subList(0, idx)
        // Greeting.
        val firstReal = kept.indexOfFirst { it.isNotBlank() }
        if (firstReal >= 0 && (greeting.matches(kept[firstReal]) || ar(greetingAr, kept[firstReal], true))) kept = kept.drop(firstReal + 1)
        return kept.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
    }
}
