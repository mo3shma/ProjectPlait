package app.plait.data.context

import app.plait.data.brief.DescriptionCleaner
import java.net.URLDecoder
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

/** An email reduced to what matters for a meeting. */
data class ParsedEmail(
    val subject: String,
    val from: String,
    val to: String,
    val dateMs: Long?,
    val body: String,
    val attachments: List<String> = emptyList(),
)

/** Reads a raw .eml (RFC 5322 with MIME) or the text of an email that was shared or pasted. */
object EmailParser {
    private val headerStart = Regex("^(from|to|cc|subject|date|sent|mime-version|content-type|message-id|received|reply-to|return-path|x-[a-z-]+):", RegexOption.IGNORE_CASE)

    /** True when [text] opens with a block of mail headers. */
    fun looksLikeEml(text: String): Boolean {
        val head = text.trimStart().lines().take(40)
        val headers = head.takeWhile { it.isNotBlank() }.filter { headerStart.containsMatchIn(it) }
        val names = headers.map { it.substringBefore(':').lowercase() }.toSet()
        return headers.size >= 3 && ("from" in names || "subject" in names) && ("date" in names || "mime-version" in names || "message-id" in names || "to" in names)
    }

    /** Text from a share sheet or the clipboard. Uses [subjectHint] (EXTRA_SUBJECT) when the text itself has no headers. */
    fun fromText(text: String, subjectHint: String? = null): ParsedEmail {
        if (looksLikeEml(text)) return parseEml(text)
        val lines = text.trim().lines()
        // "Subject: ..." and "From: ..." lines pasted above the body, without being a full header block.
        fun find(name: String) = lines.take(12).firstOrNull { it.startsWith("$name:", ignoreCase = true) }?.substringAfter(':')?.trim()
        val subject = subjectHint?.takeIf { it.isNotBlank() } ?: find("Subject") ?: ""
        return ParsedEmail(
            subject = subject, from = find("From").orEmpty(), to = find("To").orEmpty(), dateMs = null,
            body = text.trim(),
        )
    }

    // ---- MIME ----

    private class Part(val headers: Map<String, String>, val body: String)

    private fun splitHeaders(raw: String): Part {
        val text = raw.replace("\r\n", "\n").replace("\r", "\n")
        val gap = text.indexOf("\n\n")
        val head = if (gap >= 0) text.substring(0, gap) else text
        val body = if (gap >= 0) text.substring(gap + 2) else ""
        val unfolded = head.replace(Regex("\n[ \t]+"), " ")
        val map = LinkedHashMap<String, String>()
        for (line in unfolded.lines()) {
            val i = line.indexOf(':')
            if (i <= 0) continue
            map.putIfAbsent(line.substring(0, i).trim().lowercase(), line.substring(i + 1).trim())
        }
        return Part(map, body)
    }

    private fun param(header: String?, name: String): String? {
        header ?: return null
        val m = Regex("""(?i)\b$name\s*=\s*("([^"]*)"|[^;\s]+)""").find(header) ?: return null
        return m.groupValues[2].ifEmpty { m.groupValues[1] }.trim('"')
    }

    fun parseEml(raw: String): ParsedEmail {
        val root = splitHeaders(raw)
        val h = root.headers
        val attachments = ArrayList<String>()
        val body = bodyOf(root, attachments)
        val date = h["date"]?.let { d ->
            runCatching { ZonedDateTime.parse(d.replace(Regex("\\s*\\(.*\\)\\s*$"), "").trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
        }
        return ParsedEmail(
            subject = decodeWords(h["subject"].orEmpty()), from = decodeWords(h["from"].orEmpty()),
            to = decodeWords(h["to"].orEmpty()), dateMs = date, body = body.trim(), attachments = attachments.distinct(),
        )
    }

    private fun bodyOf(part: Part, attachments: MutableList<String>): String {
        val type = (part.headers["content-type"] ?: "text/plain").lowercase()
        val disposition = part.headers["content-disposition"].orEmpty().lowercase()
        if (disposition.startsWith("attachment") || (param(part.headers["content-disposition"], "filename") != null && !type.startsWith("multipart"))) {
            (param(part.headers["content-disposition"], "filename") ?: param(part.headers["content-type"], "name"))?.let { attachments.add(decodeWords(it)) }
            return ""
        }
        if (type.startsWith("multipart/")) {
            val boundary = param(part.headers["content-type"], "boundary") ?: return ""
            val pieces = part.body.split("--$boundary").drop(1).map { it.removePrefix("--").trimStart('\n') }.filter { it.isNotBlank() && !it.startsWith("--") }
            val texts = pieces.map { splitHeaders(it) }.map { child ->
                val ct = (child.headers["content-type"] ?: "text/plain").lowercase()
                Triple(ct, child, bodyOf(child, attachments))
            }
            if (type.startsWith("multipart/alternative")) {
                texts.firstOrNull { it.first.startsWith("text/plain") && it.third.isNotBlank() }?.let { return it.third }
                return texts.firstOrNull { it.third.isNotBlank() }?.third.orEmpty()
            }
            return texts.map { it.third }.filter { it.isNotBlank() }.joinToString("\n\n")
        }
        if (type.startsWith("message/rfc822")) return ""
        if (!type.startsWith("text/")) {
            param(part.headers["content-type"], "name")?.let { attachments.add(decodeWords(it)) }
            return ""
        }
        val bytes = decodeTransfer(part.body, part.headers["content-transfer-encoding"])
        val charset = param(part.headers["content-type"], "charset")?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        val text = String(bytes, charset)
        return if (type.startsWith("text/html")) HtmlText.toText(text) else text
    }

    private fun decodeTransfer(body: String, encoding: String?): ByteArray = when (encoding?.trim()?.lowercase()) {
        "base64" -> runCatching { Base64.getMimeDecoder().decode(body.trim()) }.getOrDefault(ByteArray(0))
        "quoted-printable" -> quotedPrintable(body)
        else -> body.toByteArray(Charsets.ISO_8859_1)
    }

    /** Bytes of a quoted-printable body (soft line breaks removed, =XX decoded). */
    fun quotedPrintable(input: String): ByteArray {
        val s = input.replace("=\n", "")
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3)
                val v = hex.toIntOrNull(16)
                if (v != null) { out.write(v); i += 3; continue }
            }
            out.write(c.code and 0xFF)
            i++
        }
        return out.toByteArray()
    }

    /** RFC 2047 encoded words in headers: =?UTF-8?B?...?= and =?UTF-8?Q?...?=. */
    fun decodeWords(value: String): String {
        if (!value.contains("=?")) return value
        val joined = value.replace(Regex("""\?=\s+=\?"""), "?==?")
        return Regex("""=\?([^?]+)\?([bBqQ])\?([^?]*)\?=""").replace(joined) { m ->
            val cs = runCatching { Charset.forName(m.groupValues[1]) }.getOrDefault(Charsets.UTF_8)
            val bytes = if (m.groupValues[2].equals("b", true)) runCatching { Base64.getDecoder().decode(m.groupValues[3]) }.getOrDefault(ByteArray(0))
            else quotedPrintable(m.groupValues[3].replace('_', ' '))
            String(bytes, cs)
        }
    }

    /** "Jo Park <jo@x.com>" to "Jo Park"; a bare address to its local part. */
    fun displayName(address: String): String {
        val a = address.trim()
        if (a.contains('<')) {
            val named = a.substringBefore('<').trim().trim('"', '\'').takeIf { it.isNotBlank() }
            if (named != null) return if (named.contains(',') && !named.contains('@')) named.split(',').map { it.trim() }.reversed().joinToString(" ") else named
        } else if (!a.contains('@')) {
            return a
        }
        return a.removePrefix("<").removeSuffix(">").substringAfter('<').substringBefore('>').substringBefore('@')
            .split('.', '_', '-').filter { it.isNotBlank() }.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private val addressRegex = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    fun addresses(text: String): List<String> = addressRegex.findAll(text).map { it.value.lowercase() }.distinct().toList()
}

/** Minimal HTML to text for email bodies. */
object HtmlText {
    fun toText(html: String): String {
        var t = html
            .replace(Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>"), "")
            .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>|</h[1-6]>|</blockquote>"), "\n")
            .replace(Regex("(?i)<li[^>]*>"), "\n- ")
            .replace(Regex("<[^>]+>"), "")
        for ((k, v) in DescriptionCleaner.entities) t = t.replace(k, v)
        t = t.replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        return t.replace(" ", " ").lines().joinToString("\n") { it.trim() }.replace(Regex("\n{3,}"), "\n\n").trim()
    }
}

/** Safe Links and tracking redirects hide the real URL; show the real one. */
fun unwrapLinks(text: String): String = app.plait.data.CallLinks.unwrapSafeLinks(text)
