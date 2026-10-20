package app.plait.data.memo

/**
 * A checklist kept as a written note: one item per line, "[x] " for done and "[ ] " for open. Storing it as text means a
 * list syncs, backs up, searches and exports like any other note.
 */
object Checklist {
    data class Item(val text: String, val done: Boolean)

    fun parse(text: String): List<Item> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        when {
            line.startsWith("[x]", ignoreCase = true) -> Item(line.drop(3).trim(), true)
            line.startsWith("[ ]") -> Item(line.drop(3).trim(), false)
            else -> Item(line.removePrefix("- ").removePrefix("• ").trim(), false)
        }
    }.filter { it.text.isNotEmpty() }

    fun format(items: List<Item>): String = items.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text }

    /** Plain lines as typed, one item per line, keeping ticks on items whose text did not change. */
    fun edit(old: String, lines: String): String {
        val was = parse(old).associate { it.text to it.done }
        return format(lines.lines().map { it.trim().removePrefix("- ") }.filter { it.isNotEmpty() }.map { Item(it, was[it] == true) })
    }

    /** A new list from plain lines. */
    fun fromPlain(lines: String): String = edit("", lines)

    fun plain(text: String): String = parse(text).joinToString("\n") { it.text }

    fun toggle(text: String, index: Int): String = format(parse(text).mapIndexed { i, it -> if (i == index) it.copy(done = !it.done) else it })

    fun add(text: String, item: String): String = format(parse(text) + Item(item.trim(), false))

    /** "2 of 5 done" */
    fun progress(text: String): String = parse(text).let { l -> "${l.count { it.done }} of ${l.size} done" }
}
