package app.plait.ui.screens

import app.plait.ui.fauxGlass
import app.plait.ui.plaitCard
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.height
import app.plait.ui.CircleIconButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.plait.ai.Ocr
import app.plait.data.memo.Memo
import app.plait.data.memo.MemoKind
import app.plait.data.memo.MemoStore
import app.plait.data.memo.OcrLang
import app.plait.data.memo.OcrText
import app.plait.ui.HomeState
import app.plait.ui.PlaitChip
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.pressable
import app.plait.ui.popupSurface
import app.plait.ui.PlaitButton
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The ways to add a note, as one row of glass pills like Join and Record: speak it (when [onRecord] is given), write it, or
 * photograph it (the text is read from the picture). Choosing a picture and its language is in a pop-up, so the row stays small.
 */
@Composable
internal fun AddNoteCard(eventId: String, memos: MemoStore, backdrop: com.kyant.backdrop.Backdrop? = null, onRecord: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var writing by remember { mutableStateOf(false) }
    var photoMenu by remember { mutableStateOf(false) }
    var listing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var pendingCamera by remember { mutableStateOf<File?>(null) }
    var lang by remember { mutableStateOf(HomeState.ocrLang) }
    // The 4x3 widget's "Text note" / "Task list": open that editor on the Notes tab's own card.
    if (eventId.isEmpty()) app.plait.ui.WidgetLaunch.editor?.let { k ->
        LaunchedEffect(k) { if (k == "text") writing = true else listing = true; app.plait.ui.WidgetLaunch.editor = null }
    }

    val addPhoto: (Uri) -> Unit = { uri ->
        scope.launch {
            busy = true
            val id = UUID.randomUUID().toString()
            val target = memos.newImageFile(id)
            val saved = Ocr.saveScaled(context, uri, target)
            if (!saved) {
                Toast.makeText(context, "Couldn't open that picture.", Toast.LENGTH_SHORT).show()
            } else {
                val text = Ocr.read(context, target, lang)
                val photo = Memo(id, eventId, MemoKind.Photo, System.currentTimeMillis(), text.orEmpty(), target.name)
                memos.add(photo)
                if (!text.isNullOrBlank()) app.plait.audio.NoteProcessor.afterMemoSaved(context, photo)
                app.plait.data.sync.SyncEngine.requestAsync(context, force = true)
                if (text.isNullOrBlank()) Toast.makeText(context, "Saved. No text was found in the picture.", Toast.LENGTH_LONG).show()
            }
            busy = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingCamera
        if (ok && file != null) addPhoto(Uri.fromFile(file))
        pendingCamera = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) addPhoto(uri) }

    // The shared tinted glass button; the tone tells the four kinds of note apart at a glance.
    @Composable
    fun Pill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, tone: app.plait.ui.GlassTone, onClick: () -> Unit) =
        PlaitButton(label, onClick, modifier, style = ButtonStyle.Tinted, icon = icon, tone = tone)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two equal pills to a row, so every one has room for its icon and its word.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
            val m = Modifier.weight(1f)
            if (onRecord != null) Pill("Voice", Icons.Filled.Mic, m, app.plait.ui.GlassTone.Rose, onRecord)
            Pill("Write", Icons.Filled.Edit, m, app.plait.ui.GlassTone.Lavender) { writing = true }
            Pill("Photo", Icons.Filled.CameraAlt, m, app.plait.ui.GlassTone.Mint) { photoMenu = true }
            Pill("List", Icons.Filled.Checklist, m, app.plait.ui.GlassTone.Peach) { listing = true }
        }
        if (busy) Text(if (lang == OcrLang.Latin) "Reading the picture…" else "Reading the picture. Arabic takes a few seconds.", style = PlaitType.caption, color = plait.muted)
    }

    if (writing) EditTextDialog("New note", "", onSave = { t ->
        val note = Memo(UUID.randomUUID().toString(), eventId, MemoKind.Text, System.currentTimeMillis(), t.trim())
        memos.add(note)
        app.plait.audio.NoteProcessor.afterMemoSaved(context, note)
        app.plait.data.sync.SyncEngine.requestAsync(context, force = true)
    }, onDismiss = { writing = false })

    if (listing) ChecklistDialog("New checklist", emptyList(), onSave = { items ->
        memos.add(Memo(UUID.randomUUID().toString(), eventId, MemoKind.Checklist, System.currentTimeMillis(), app.plait.data.memo.Checklist.format(items)))
        app.plait.data.sync.SyncEngine.requestAsync(context, force = true)
    }, onDismiss = { listing = false })

    if (photoMenu) DetailDialog("Add a photo", { photoMenu = false }) {
        Text("The text in the picture is read on this phone and kept so you can copy it.", style = PlaitType.caption, color = plait.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlaitButton("Take photo", {
                photoMenu = false
                val dir = memos.dir.also { it.mkdirs() }
                val file = File(dir, "capture-${UUID.randomUUID()}.jpg")
                pendingCamera = file
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
            }, size = ButtonSize.Small, icon = Icons.Filled.CameraAlt, enabled = !busy)
            PlaitButton("Choose", { photoMenu = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, size = ButtonSize.Small, icon = Icons.Filled.Image, enabled = !busy)
        }
        Text("Language in the picture", style = PlaitType.monoSmall, color = plait.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OcrLang.entries.forEach { l -> PlaitChip(l.label, lang == l, { lang = l; HomeState.ocrLang = l }) }
        }
    }
}

@Composable
internal fun MemoCard(memo: Memo, memos: MemoStore, onLongPress: () -> Unit) {
    if (memo.kind == MemoKind.Checklist) { ChecklistCard(memo, memos, onLongPress); return }
    val context = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, memo.id) {
        value = memos.image(memo)?.takeIf { it.exists() }?.let { f ->
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull()
            }
        }
    }
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(30.dp)).pressable({}, lift = 2.dp, onLongClick = onLongPress).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (memo.kind == MemoKind.Photo) Icons.Filled.CameraAlt else Icons.Filled.Edit, null, tint = plait.accent, modifier = Modifier.size(18.dp))
            Text(
                (memo.author?.let { "From $it · " } ?: "") + (if (memo.kind == MemoKind.Photo) "Photo · " else "Note · ") +
                    app.plait.data.TimeFmt.stamp(memo.createdAt),
                style = PlaitType.caption, color = plait.muted,
            )
        }
        thumb?.let {
            Image(it, "Photo for this meeting", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(20.dp)))
        }
        if (memo.text.isNotBlank()) {
            if (memo.kind == MemoKind.Photo) Text("Text found in the photo", style = PlaitType.monoSmall, color = plait.muted)
            SelectionContainer { Text(memo.text, style = PlaitType.body.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content), color = plait.text, modifier = Modifier.fillMaxWidth()) }
        } else if (memo.kind == MemoKind.Photo) {
            Text("No text was found in this picture.", style = PlaitType.body, color = plait.muted)
        }
        MemoSummary(memo)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (memo.text.isNotBlank()) PlaitButton("Copy text", { copy(context, memo.text) }, size = ButtonSize.Small)
        }
    }
}

/** The AI (or rules) summary of a text or photo note, with what wrote it and a button to write it again. */
@Composable
internal fun MemoSummary(memo: Memo) {
    if (memo.kind == MemoKind.Checklist) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var upToDate by remember(memo.id) { mutableStateOf(false) }
    val busy = app.plait.audio.NoteProcessor.working[memo.id]
    val run: () -> Unit = {
        scope.launch { if (!app.plait.audio.NoteProcessor.summariseMemoNow(context, memo)) { upToDate = true; kotlinx.coroutines.delay(2000); upToDate = false } }
    }
    if (memo.ai != null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Summary", style = PlaitType.monoSmall, color = plait.muted)
            memo.aiBy?.let { app.plait.ui.PlaitBadge(it) }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            if (upToDate) Text("Already up to date", style = PlaitType.caption, color = plait.muted)
            else if (busy == null) CircleIconButton(Icons.Filled.Refresh, "Summarise again", run, app.plait.ui.GlassTone.Lavender.deep, plait.text, size = 32.dp, iconSize = 18.dp)
        }
        Text(memo.ai, style = PlaitType.body.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content), color = plait.text, modifier = Modifier.fillMaxWidth())
    } else if (busy == null && app.plait.data.memo.MemoAi.worthAnalysing(memo)) {
        PlaitButton("Summarise", run, size = ButtonSize.Small, style = ButtonStyle.Tinted)
    }
    busy?.let { Text(it, style = PlaitType.caption, color = plait.muted) }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Plait note", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

/** The text to put in an edit box for a note: a checklist is edited as plain lines, one item each. */
internal fun editableText(memo: Memo): String = if (memo.kind == MemoKind.Checklist) app.plait.data.memo.Checklist.plain(memo.text) else memo.text

/** What to save back after editing: a checklist keeps the ticks on the items whose text did not change. */
internal fun editedMemo(memo: Memo, text: String): Memo =
    memo.copy(text = if (memo.kind == MemoKind.Checklist) app.plait.data.memo.Checklist.edit(memo.text, text) else text.trim())

/** A meeting's checklist: tick items off, add one at the bottom. It can hold the agenda, the outcome, or follow-up tasks. */
@Composable
internal fun ChecklistCard(memo: Memo, memos: MemoStore, onLongPress: () -> Unit) {
    val context = LocalContext.current
    val items = app.plait.data.memo.Checklist.parse(memo.text)
    var draft by remember(memo.id) { mutableStateOf("") }
    var renaming by remember(memo.id) { mutableStateOf(false) }
    val save: (String) -> Unit = { t -> memos.update(memo.copy(text = t)); app.plait.data.sync.SyncEngine.requestAsync(context, force = true) }
    val done = items.count { it.done }
    val progress by androidx.compose.animation.core.animateFloatAsState(
        if (items.isEmpty()) 0f else done / items.size.toFloat(), androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f), label = "list",
    )
    Column(
        Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(30.dp)).pressable({}, lift = 2.dp, onLongClick = onLongPress).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Checklist, null, tint = plait.accent, modifier = Modifier.size(20.dp))
            Text(
                app.plait.data.memo.Checklist.label(memo.title), style = PlaitType.section.copy(fontSize = 18.sp), color = plait.text,
                maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).pressable({ renaming = true }, lift = 2.dp),
            )
            Text("$done of ${items.size}", style = PlaitType.monoSmall, color = plait.muted)
            CircleIconButton(Icons.Filled.Edit, "Rename checklist", { renaming = true }, plait.surfaceHigh, plait.text, size = 32.dp, iconSize = 16.dp)
        }
        if (renaming) RenameDialog(memo.title.orEmpty(), onSave = { t ->
            memos.update(memo.copy(title = t.trim().ifEmpty { null }))
            app.plait.data.sync.SyncEngine.requestAsync(context, force = true)
        }, onDismiss = { renaming = false })
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(plait.surfaceHigh)) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(RoundedCornerShape(3.dp)).background(plait.accent))
        }
        items.forEachIndexed { i, item ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).pressable({ save(app.plait.data.memo.Checklist.toggle(memo.text, i)) }, lift = 2.dp)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (item.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, if (item.done) "Done" else "Not done",
                    tint = if (item.done) plait.accent else plait.muted, modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        item.text, style = PlaitType.body.copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content,
                            textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
                        color = if (item.done) plait.muted else plait.text,
                    )
                    if (!item.done) DueChip(item.dueMs, { save(app.plait.data.memo.Checklist.setDue(memo.text, i, it)) })
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = draft, onValueChange = { draft = it }, singleLine = true,
                textStyle = PlaitType.body.copy(color = plait.text), cursorBrush = SolidColor(plait.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (draft.isNotBlank()) { save(app.plait.data.memo.Checklist.add(memo.text, draft)); draft = "" } }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(plait.surfaceHigh).padding(horizontal = 16.dp, vertical = 12.dp)) {
                        if (draft.isEmpty()) Text("Add an item", style = PlaitType.body, color = plait.muted)
                        inner()
                    }
                },
            )
            CircleIconButton(Icons.Filled.Add, "Add item", { if (draft.isNotBlank()) { save(app.plait.data.memo.Checklist.add(memo.text, draft)); draft = "" } }, plait.accent, plait.onAccent, size = 44.dp, iconSize = 20.dp)
        }
    }
}

/** Name a checklist; saving an empty box puts the default "Checklist" back. */
@Composable
private fun RenameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    app.plait.ui.PlaitDialog(onDismissRequest = onDismiss) { PopIn {
        Column(Modifier.fillMaxWidth().popupSurface().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Rename checklist", style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
            BasicTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = PlaitType.body.copy(color = plait.text, textDirection = androidx.compose.ui.text.style.TextDirection.Content), cursorBrush = SolidColor(plait.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSave(text); onDismiss() }),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(plait.surface).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        if (text.isEmpty()) Text("Checklist", style = PlaitType.body, color = plait.muted)
                        inner()
                    }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaitButton("Save", { onSave(text); onDismiss() }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                PlaitButton("Cancel", onDismiss, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
        }
    } }
}

/** Edit any written note: a checklist opens the task editor, anything else the text box. */
@Composable
internal fun EditMemoDialog(memo: Memo, memos: MemoStore, onDismiss: () -> Unit) {
    if (memo.kind == MemoKind.Checklist) ChecklistDialog("Edit checklist", app.plait.data.memo.Checklist.parse(memo.text), onSave = { items ->
        memos.update(memo.copy(text = app.plait.data.memo.Checklist.format(items)))
    }, onDismiss = onDismiss)
    else {
        val context = LocalContext.current
        EditTextDialog("Edit note", memo.text, onSave = { t ->
            val edited = memo.copy(text = t.trim())
            memos.update(edited)
            app.plait.audio.NoteProcessor.afterMemoSaved(context, edited)
        }, onDismiss = onDismiss)
    }
}

/** One colour per kind of note, used lightly on the add-note pills. */
internal object NoteColors {
    val voice = androidx.compose.ui.graphics.Color(0xFFE8725F)
    val write = androidx.compose.ui.graphics.Color(0xFF8A96FF)
    val photo = androidx.compose.ui.graphics.Color(0xFF3FBFA8)
    val list = androidx.compose.ui.graphics.Color(0xFFF2B35B)
}
