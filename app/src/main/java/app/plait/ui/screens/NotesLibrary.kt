package app.plait.ui.screens

import app.plait.ui.popupSurface
import app.plait.ui.plaitCard
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.plait.data.Calendars
import app.plait.data.NoteEdit
import app.plait.data.NoteStore
import app.plait.data.SavedNote
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoKind
import app.plait.data.memo.MemoStore
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.HomeState
import app.plait.ui.PlaitButton
import app.plait.ui.PlaitChip
import app.plait.ui.pressable
import app.plait.ui.rise
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait

/** One note of any kind, with what the library needs to show it. */
private class Entry(val key: String, val createdAt: Long, val eventId: String, val voice: SavedNote?, val memo: Memo?) {
    val photo get() = memo?.kind == MemoKind.Photo
    val list get() = memo?.kind == MemoKind.Checklist
    val icon: ImageVector get() = if (voice != null) Icons.Filled.Mic else if (photo) Icons.Filled.CameraAlt else if (list) Icons.Filled.Checklist else Icons.Filled.Edit
    val kindLabel get() = if (voice != null) "Voice" else if (photo) "Photo" else if (list) "List" else "Text"
    val title: String get() = when {
        voice != null -> if (eventId.isEmpty()) "Voice note" else voice.title.ifBlank { "Voice note" }
        list -> "Checklist · " + app.plait.data.memo.Checklist.progress(memo!!.text)
        else -> memo!!.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60) ?: if (photo) "Photo" else "Note"
    }
    val snippet: String get() = when {
        list -> app.plait.data.memo.Checklist.parse(memo!!.text).filter { !it.done }.joinToString(" · ") { it.text }.ifBlank { "All done" }
        voice != null -> voice.ai?.let { app.plait.ai.NoteAiPrompt.parse(it).summary } ?: voice.lines.firstOrNull()?.text ?: "No speech detected"
        else -> memo!!.text.lines().filter { it.isNotBlank() }.drop(1).joinToString(" ").trim()
            .ifBlank { if (photo && memo!!.text.isBlank()) "No text was found in this picture." else "" }
    }
}

private fun entries(voice: List<SavedNote>, memos: List<Memo>): List<Entry> =
    (voice.map { Entry("v" + it.id, it.createdAt, it.eventId, it, null) } + memos.map { Entry("m" + it.id, it.createdAt, it.eventId, null, it) })
        .sortedByDescending { it.createdAt }

private fun meetingTitle(eventId: String): String? =
    if (eventId.isEmpty()) null else Calendars.find(eventId)?.title?.takeIf { it.isNotBlank() }

/**
 * Every note you have taken, voice, written or photo, newest first, whether or not it belongs to a meeting. Tap to read or
 * play it, swipe to delete, press and hold for more, and link it to a meeting when you want it to show up there.
 */
@Composable
internal fun NotesLibrary(backdrop: com.kyant.backdrop.Backdrop, onOpenEvent: (String) -> Unit, onOpenAt: (String, Int) -> Unit, onRecord: (String) -> Unit) {
    val context = LocalContext.current
    val voiceStore = NoteStore.get(context)
    val memoStore = MemoStore.get(context)
    val undo = remember { UndoDelete() }
    var filter by rememberSaveable { mutableStateOf(0) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<Entry?>(null) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var linking by remember { mutableStateOf<Entry?>(null) }

    val filterTabs = remember { listOf("All", "Voice", "Text", "Photo", "Lists").map { app.plait.ui.glass.GlassTab(it) } }
    val all = entries(voiceStore.notes.toList(), memoStore.items.toList())
    val shown = all.filter { e ->
        !undo.isHidden(e.key) && when (filter) { 1 -> e.voice != null; 2 -> e.memo != null && !e.photo && !e.list; 3 -> e.photo; 4 -> e.list; else -> true }
    }
    val open = openKey?.let { k -> all.firstOrNull { it.key == k } }
    if (openKey != null && open == null && !undo.isHidden(openKey!!)) openKey = null

    val delete: (Entry) -> Unit = { e ->
        if (open?.key == e.key) openKey = null
        if (e.voice != null) undo.delete(e.key, "note") { voiceStore.delete(e.voice) }
        else if (e.memo != null) undo.delete(e.key, "note") { memoStore.delete(e.memo) }
    }

    Box(Modifier.fillMaxSize()) {
        if (open != null) {
            BackHandler { openKey = null }
            NoteDetail(open, voiceStore, memoStore, onBack = { openKey = null }, onOpenEvent = onOpenEvent, onLink = { linking = open }, onEdit = { editing = open })
        } else {
            LazyColumn(
                state = HomeState.notesList,
                modifier = Modifier.fillMaxSize(),
                contentPadding = listPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ScreenHeader("Notes", "Notes") }
                item { SearchPanel(onOpenAt) }
                item { AddNoteCard("", memoStore, backdrop) }
                item {
                    app.plait.ui.glass.GlassTabBar(
                        tabs = filterTabs, selected = filter, onSelect = { filter = it }, backdrop = backdrop,
                        container = app.plait.ui.glass.TabContainer.Glass, height = 48.dp, selectedColor = plait.text,
                    )
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (all.isEmpty()) "No notes yet. Write or photograph something above, or tap + to record." else "No notes of this kind.",
                            style = PlaitType.body, color = plait.muted, modifier = Modifier.rise(1),
                        )
                    }
                } else {
                    items(shown, key = { it.key }) { e ->
                        SwipeToDelete(onDelete = { delete(e) }, modifier = Modifier.animateItem()) {
                            LibraryCard(e, onClick = { openKey = e.key }, onLongClick = { actionsFor = e })
                        }
                    }
                }
            }
        }
        undo.Host(Modifier.align(Alignment.BottomCenter).padding(bottom = 116.dp))
    }

    actionsFor?.let { e ->
        ActionsDialog(
            e.title,
            listOfNotNull(
                NoteAction("Open", Icons.Filled.OpenInNew) { openKey = e.key },
                NoteAction(if (e.eventId.isEmpty()) "Link to a meeting" else "Change meeting", Icons.Filled.Link) { linking = e },
                if (e.eventId.isNotEmpty()) NoteAction("Unlink from meeting", Icons.Filled.LinkOff) { unlink(e, voiceStore, memoStore) } else null,
                if (!e.photo || e.memo?.text?.isNotBlank() == true) NoteAction(if (e.voice != null) "Edit transcript" else if (e.list) "Edit items" else "Edit text", Icons.Filled.Edit) { editing = e } else null,
                NoteAction("Delete", Icons.Filled.Delete, danger = true) { delete(e) },
            ),
            onDismiss = { actionsFor = null },
        )
    }
    editing?.let { e -> EditEntry(e, voiceStore, memoStore) { editing = null } }
    linking?.let { e -> LinkPicker(e, onPick = { id -> link(e, id, voiceStore, memoStore) }, onDismiss = { linking = null }) }
}

private fun link(e: Entry, eventId: String, voices: NoteStore, memos: MemoStore) {
    val title = Calendars.find(eventId)?.title.orEmpty()
    e.voice?.let { voices.update(it.copy(eventId = eventId, title = title.ifBlank { it.title })) }
    e.memo?.let { memos.update(it.copy(eventId = eventId)) }
}

private fun unlink(e: Entry, voices: NoteStore, memos: MemoStore) {
    e.voice?.let { voices.update(it.copy(eventId = "", title = "Voice note")) }
    e.memo?.let { memos.update(it.copy(eventId = "")) }
}

@Composable
private fun EditEntry(e: Entry, voices: NoteStore, memos: MemoStore, onDismiss: () -> Unit) {
    if (e.memo != null) { EditMemoDialog(e.memo, memos, onDismiss); return }
    EditTextDialog(
        if (e.voice != null) "Edit transcript" else "Edit note",
        e.voice?.let { NoteEdit.transcriptText(it) } ?: editableText(e.memo!!),
        onSave = { text ->
            e.voice?.let { voices.update(NoteEdit.applyTranscript(it, text)) }
            e.memo?.let { memos.update(editedMemo(it, text)) }
        },
        onDismiss = onDismiss,
    )
}

@Composable
private fun LibraryCard(e: Entry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val linked = meetingTitle(e.eventId)
    Column(
        Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(28.dp))
            .pressable(onClick, onLongClick = onLongClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(e.icon, null, tint = plait.accent, modifier = Modifier.size(18.dp))
            Text(e.title, style = PlaitType.bodyStrong, color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(if (e.voice != null) formatClock(e.voice.durationMs) else e.kindLabel, style = PlaitType.monoSmall.copy(fontSize = 13.sp), color = plait.signal, maxLines = 1)
        }
        if (e.snippet.isNotBlank()) Text(e.snippet, style = PlaitType.body.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content), color = plait.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ((e.voice?.author ?: e.memo?.author)?.let { "From $it · " } ?: "") + app.plait.data.TimeFmt.stamp(e.createdAt),
                style = PlaitType.monoSmall, color = plait.muted, maxLines = 1,
            )
        }
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(plait.surfaceHigh).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(if (linked != null) Icons.Filled.CalendarMonth else Icons.Filled.LinkOff, null, tint = plait.text, modifier = Modifier.size(14.dp))
            Text(linked ?: "Not linked to a meeting", style = PlaitType.caption, color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A note opened on its own: play or read it, see what was extracted, and choose which meeting (if any) it belongs to. */
@Composable
private fun NoteDetail(
    e: Entry, voices: NoteStore, memos: MemoStore, onBack: () -> Unit, onOpenEvent: (String) -> Unit, onLink: () -> Unit, onEdit: () -> Unit,
) {
    val linked = meetingTitle(e.eventId)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(listPadding()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PlaitButton("Notes", onBack, size = ButtonSize.Small, style = ButtonStyle.Quiet, icon = Icons.AutoMirrored.Filled.ArrowBack)
        Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(e.kindLabel.uppercase() + " NOTE · " + app.plait.data.TimeFmt.stamp(e.createdAt), style = PlaitType.monoLabel, color = plait.muted)
            Text(e.title, style = PlaitType.title.copy(fontSize = 34.sp, lineHeight = 36.sp), color = plait.text)
        }
        Column(
            Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(28.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Meeting", style = PlaitType.monoSmall, color = plait.muted)
            Text(linked ?: "Not linked to a meeting", style = PlaitType.bodyStrong, color = plait.text)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaitButton(if (linked == null) "Link to a meeting" else "Change meeting", onLink, size = ButtonSize.Small, style = ButtonStyle.Primary, icon = Icons.Filled.Link)
                if (linked != null) {
                    PlaitButton("Open meeting", { onOpenEvent(e.eventId) }, size = ButtonSize.Small, icon = Icons.Filled.CalendarMonth)
                    PlaitButton("Unlink", { unlink(e, voices, memos) }, size = ButtonSize.Small, style = ButtonStyle.Quiet, icon = Icons.Filled.LinkOff)
                }
            }
        }
        e.voice?.let { n ->
            RealNoteCard(n, voices, onLongPress = onEdit)
            TranscriptTab("", listOf(n))
        }
        e.memo?.let { m ->
            MemoCard(m, memos, onLongPress = onEdit)
        }
        if (e.voice != null || e.memo?.text?.isNotBlank() == true) {
            PlaitButton(if (e.voice != null) "Edit transcript" else "Edit text", onEdit, size = ButtonSize.Small, style = ButtonStyle.Quiet, icon = Icons.Filled.Edit)
        }
    }
}

/** Meetings close to the note's time come first, then the rest of the last month and the coming fortnight. */
@Composable
private fun LinkPicker(e: Entry, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val events = remember(e.key) {
        val now = System.currentTimeMillis()
        Calendars.events.filter { it.startMs in (now - 30L * 86_400_000)..(now + 14L * 86_400_000) }
            .sortedBy { kotlin.math.abs(it.startMs - e.createdAt) }.take(40)
    }
    Dialog(onDismissRequest = onDismiss) { PopIn {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).popupSurface().padding(vertical = 16.dp)) {
            Text("Link to a meeting", style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            if (events.isEmpty()) {
                Text("No meetings found around now. Connect a calendar to link notes.", style = PlaitType.body, color = plait.muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
            } else LazyColumn(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(events, key = { it.id }) { ev ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(20.dp))
                            .background(if (ev.id == e.eventId) plait.surface else androidx.compose.ui.graphics.Color.Transparent)
                            .pressable({ onDismiss(); onPick(ev.id) }, lift = 2.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(ev.title, style = PlaitType.bodyStrong, color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val day = java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date(ev.startMs))
                        Text("$day · ${ev.time}", style = PlaitType.monoSmall, color = plait.muted)
                    }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { PlaitButton("Cancel", onDismiss, size = ButtonSize.Small, style = ButtonStyle.Quiet) }
        }
    } }
}
