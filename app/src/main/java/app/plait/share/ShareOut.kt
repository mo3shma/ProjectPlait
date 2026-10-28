package app.plait.share

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import app.plait.R
import app.plait.data.CalEvent
import app.plait.data.NoteDigest
import app.plait.data.NoteStore
import app.plait.data.TimeFmt
import app.plait.data.brief.MeetingBrief
import app.plait.data.context.ContextStore
import app.plait.data.followups.FollowupState
import app.plait.data.followups.Followups
import app.plait.data.memo.MemoKind
import app.plait.data.memo.MemoStore
import app.plait.data.prep.PrepStore
import app.plait.data.share.ExportData
import app.plait.data.share.MeetingExport
import app.plait.data.share.TextBlock
import app.plait.data.share.VoiceBlock
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date

enum class ShareFormat(val mime: String, val ext: String) {
    Pdf("application/pdf", "pdf"), Markdown("text/markdown", "md"), Text("text/plain", "txt");

    /** Button label: PDF and Markdown are names and stay as they are; Text is in the app's language. */
    val label: String get() = when (this) { Pdf -> "PDF"; Markdown -> "Markdown"; Text -> Txt.s(R.string.share_format_text) }
}

/** Shares a meeting with its brief, notes and follow-ups as a file or a message. Built on the phone and handed to the share sheet. */
object ShareOut {
    fun data(context: Context, ev: CalEvent, brief: MeetingBrief): ExportData {
        FollowupState.load(context)
        val notes = NoteStore.get(context).notes.filter { it.eventId == ev.id }.sortedBy { it.createdAt }
        val memos = MemoStore.get(context).items.filter { it.eventId == ev.id }.sortedBy { it.createdAt }
        val emails = ContextStore.get(context).forEvent(ev.id)
        val day = SimpleDateFormat("EEE d MMM", app.plait.i18n.AppLocale.locale)
        val whenLine = if (ev.startMs > 0) Txt.s(R.string.share_when, day.format(Date(ev.startMs)), if (ev.allDay) Txt.s(R.string.share_all_day) else TimeFmt.clock(ev.startMs) + " – " + TimeFmt.clock(ev.endMs)) else ev.time
        val now = System.currentTimeMillis()
        return ExportData(
            title = ev.title, whenLine = whenLine, place = ev.place,
            guests = ev.guests.filter { !it.self }.map { it.label },
            about = if (brief.aboutIsGuess) "" else brief.about, agenda = brief.agenda,
            prep = PrepStore.get(context).entries[ev.id]?.text,
            voice = notes.map { VoiceBlock(TimeFmt.stamp(it.createdAt), NoteDigest.text(it).ifBlank { null }, app.plait.data.Transcript.text(it.lines)) },
            written = memos.filter { it.text.isNotBlank() }.map { TextBlock(TimeFmt.stamp(it.createdAt), Txt.s(if (it.kind == MemoKind.Photo) R.string.common_photo else R.string.common_note), if (it.kind == MemoKind.Checklist) app.plait.data.memo.Checklist.export(it.text) else it.text) },
            emails = emails.map { it.subject.ifBlank { Txt.s(R.string.share_no_subject) } + if (it.from.isNotBlank()) " — ${it.from}" else "" },
            followups = Followups.collect(notes, memos).filter { FollowupState.isOpen(it.id, now) }.map { it.text },
        )
    }

    fun share(context: Context, ev: CalEvent, brief: MeetingBrief, format: ShareFormat) {
        val d = data(context, ev, brief)
        val send = Intent(Intent.ACTION_SEND).setType(format.mime).putExtra(Intent.EXTRA_SUBJECT, ev.title)
        if (format == ShareFormat.Text) {
            send.putExtra(Intent.EXTRA_TEXT, MeetingExport.plain(d))
        } else {
            val dir = File(context.cacheDir, "share").also { it.mkdirs() }
            dir.listFiles()?.forEach { it.delete() } // only the latest file is kept
            val file = File(dir, ev.title.replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(40).ifBlank { "meeting" } + "." + format.ext)
            if (format == ShareFormat.Pdf) PdfWriter.write(file, d.title, MeetingExport.sections(d)) else file.writeText(MeetingExport.markdown(d))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            send.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, Txt.s(R.string.share_chooser, Bidi.isolate(ev.title))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * A plain, readable A4 PDF: a title, then headed sections, paginated. Android lays the text out (StaticLayout, the system
 * fonts with their Arabic fallback), so Arabic is shaped and joined as on screen. Each paragraph takes its direction from its
 * first letter; one with none (a time, a bullet of digits) follows the app's language, and lines start on that side.
 */
object PdfWriter {
    fun write(file: File, title: String, sections: List<Pair<String, String>>) {
        val w = 595; val h = 842; val m = 48f
        val textW = (w - 2 * m).toInt()
        val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val headP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(40, 60, 160) }
        val bodyP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.rgb(20, 20, 20) }
        val doc = PdfDocument()
        var number = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, number).create())
        var y = m

        fun newPage() { doc.finishPage(page); number++; page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, number).create()); y = m }

        fun block(text: String, paint: TextPaint, after: Float) {
            if (text.isBlank()) return
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, textW).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (AppLocale.isRtl) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(2f, 1f).build()
            var line = 0
            while (line < layout.lineCount) {
                val avail = h - m - y
                var end = line
                while (end < layout.lineCount && layout.getLineBottom(end) - layout.getLineTop(line) <= avail) end++
                if (end == line) { newPage(); continue }
                val c = page.canvas
                c.save()
                c.translate(m, y - layout.getLineTop(line))
                c.clipRect(0f, layout.getLineTop(line).toFloat(), textW.toFloat(), layout.getLineBottom(end - 1).toFloat())
                layout.draw(c)
                c.restore()
                y += (layout.getLineBottom(end - 1) - layout.getLineTop(line)).toFloat()
                line = end
                if (line < layout.lineCount) newPage()
            }
            y += after
        }

        block(title, titleP, 10f)
        for ((heading, body) in sections) {
            if (heading.isNotBlank()) block(heading, headP, 4f)
            block(body, bodyP, 14f)
        }
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }
}
