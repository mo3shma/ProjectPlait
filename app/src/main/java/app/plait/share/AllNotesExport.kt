package app.plait.share

import android.content.Context
import android.net.Uri
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.NoteStore
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.data.context.ContextStore
import app.plait.data.memo.MemoStore
import app.plait.data.share.MeetingExport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Every meeting that has a note, as a folder of Markdown files in a zip (plus an index). Any Markdown reader on a computer
 * (Obsidian, VS Code, a browser) can open it, which is the read-only desktop view of your notes.
 */
object AllNotesExport {
    fun write(context: Context, uri: Uri): Int {
        val notes = NoteStore.get(context).notes.toList()
        val memos = MemoStore.get(context).items.toList()
        val mail = ContextStore.get(context).items.toList()
        val ids = (notes.map { it.eventId } + memos.map { it.eventId } + mail.map { it.eventId }).distinct()
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val index = StringBuilder("# Plait notes\n\n")
        var count = 0
        (context.contentResolver.openOutputStream(uri) ?: throw java.io.IOException("Couldn't write to that location.")).use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                val used = HashSet<String>()
                for (id in ids) {
                    val n = notes.filter { it.eventId == id }
                    val title = Calendars.find(id)?.title ?: n.firstOrNull()?.title ?: "Meeting"
                    val ev = Calendars.events.firstOrNull { it.id == id } ?: CalEvent(id, "", "", title, Source.Other, "", startMs = n.minOfOrNull { it.createdAt } ?: memos.firstOrNull { it.eventId == id }?.createdAt ?: 0L)
                    val brief = BriefBuilder.build(ev, notes, System.currentTimeMillis())
                    val whenMs = if (ev.startMs > 0) ev.startMs else n.firstOrNull()?.createdAt ?: 0L
                    var name = (if (whenMs > 0) day.format(Date(whenMs)) + " " else "") + title.replace(Regex("[^\\p{L}\\p{N} ._-]+"), "").trim().take(60).ifBlank { "Meeting" }
                    var k = 2
                    while (!used.add(name)) name = name.substringBefore(" (") + " (${k++})"
                    zip.putNextEntry(ZipEntry("$name.md"))
                    zip.write(MeetingExport.markdown(ShareOut.data(context, ev, brief)).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    index.append("- [").append(title).append("](").append(java.net.URLEncoder.encode("$name.md", "UTF-8").replace("+", "%20")).append(")\n")
                    count++
                }
                zip.putNextEntry(ZipEntry("index.md")); zip.write(index.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
        }
        return count
    }
}
