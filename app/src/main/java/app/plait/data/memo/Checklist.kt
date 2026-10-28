package app.plait.data.memo

import app.plait.R
import app.plait.data.TimeFmt
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A checklist kept as a written note: one item per line, "[x] " for done and "[ ] " for open. Storing it as text means a
 * list syncs, backs up, searches and exports like any other note.
 *
 * A due date is a trailing token on the line, " ⏰2026-10-25T17:00" (wall-clock time on the device that set it), so older
 * versions just show it as part of the text. Everything that reads an item goes through [parse], which strips it into
 * [Item.dueMs]; code that reads raw lines uses [stripDue].
 */
object Checklist {
    /** [dueMs]: epoch millis, null when there is no due date. Whole minutes only. */
    data class Item(val text: String, val done: Boolean, val dueMs: Long? = null)

    private val token = Regex("""\s*⏰(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})$""")
    private val isoMinute = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.ROOT)

    /** [line] without a trailing due token. */
    fun stripDue(line: String): String = line.replace(token, "").trim()

    private fun split(body: String, zone: ZoneId): Item {
        val m = token.find(body) ?: return Item(body, false)
        val ms = runCatching { LocalDateTime.parse(m.groupValues[1]).atZone(zone).toInstant().toEpochMilli() }.getOrNull() ?: return Item(body, false)
        return Item(body.removeRange(m.range).trim(), false, ms)
    }

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<Item> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        when {
            line.startsWith("[x]", ignoreCase = true) -> split(line.drop(3).trim(), zone).copy(done = true)
            line.startsWith("[ ]") -> split(line.drop(3).trim(), zone)
            else -> split(line.removePrefix("- ").removePrefix("• ").trim(), zone)
        }
    }.filter { it.text.isNotEmpty() }

    fun format(items: List<Item>, zone: ZoneId = ZoneId.systemDefault()): String = items.joinToString("\n") {
        (if (it.done) "[x] " else "[ ] ") + it.text + (it.dueMs?.let { ms -> " ⏰" + isoMinute.format(Instant.ofEpochMilli(ms).atZone(zone)) } ?: "")
    }

    /** "due Sun 25 Oct, 5:00 PM" */
    fun dueText(dueMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Txt.s(R.string.note_list_due, Bidi.isolate(TimeFmt.fmt("EEE d MMM, h:mm a").format(Instant.ofEpochMilli(dueMs).atZone(zone))))

    /** The list as people read it in an export: "[ ] Send deck (due Sun 25 Oct, 5:00 PM)". */
    fun export(text: String, zone: ZoneId = ZoneId.systemDefault()): String = parse(text, zone).joinToString("\n") {
        (if (it.done) "[x] " else "[ ] ") + it.text + (it.dueMs?.let { ms -> " (" + dueText(ms, zone) + ")" } ?: "")
    }

    /** Plain lines as typed, one item per line, keeping ticks and due dates on items whose text did not change. */
    fun edit(old: String, lines: String): String {
        val was = parse(old).associateBy { it.text }
        return format(lines.lines().map { it.trim().removePrefix("- ") }.filter { it.isNotEmpty() }.map { Item(it, was[it]?.done == true, was[it]?.dueMs) })
    }

    /** A new list from plain lines. */
    fun fromPlain(lines: String): String = edit("", lines)

    fun plain(text: String): String = parse(text).joinToString("\n") { it.text }

    fun toggle(text: String, index: Int): String = format(parse(text).mapIndexed { i, it -> if (i == index) it.copy(done = !it.done) else it })

    fun add(text: String, item: String, dueMs: Long? = null): String = format(parse(text) + Item(item.trim(), false, dueMs))

    fun setDue(text: String, index: Int, dueMs: Long?): String = format(parse(text).mapIndexed { i, it -> if (i == index) it.copy(dueMs = dueMs) else it })

    /** The name to show for a list: the user's own title, or "Checklist" when it is empty or blank. */
    fun label(title: String?): String = title?.trim().orEmpty().ifEmpty { Txt.s(R.string.note_list_untitled) }

    /** "Groceries · 2 of 5 done" */
    fun heading(title: String?, text: String): String = Bidi.join(listOf(label(title), progress(text)), " · ") // an Arabic title in the English app too

    /** "2 of 5 done" */
    fun progress(text: String): String = parse(text).let { l -> Txt.s(R.string.note_list_progress, l.count { it.done }, l.size) }
}
