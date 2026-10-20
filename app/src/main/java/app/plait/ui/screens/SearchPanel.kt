package app.plait.ui.screens

import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import app.plait.ui.fauxGlass
import app.plait.ui.plaitCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ai.Ai
import app.plait.ai.AiPrefs
import app.plait.ai.Embedder
import app.plait.data.TimeFmt
import app.plait.data.search.AskPrompt
import app.plait.data.search.Doc
import app.plait.data.search.DocKind
import app.plait.data.search.Hit
import app.plait.data.search.SearchIndex
import app.plait.data.search.SearchSource
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.pressable
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val vectors = HashMap<String, FloatArray>()

/**
 * Search across meetings, voice notes, written notes, photo text and attached emails, all on the phone. Words always
 * work (English and Arabic); with the optional model it also matches by meaning (English). "Ask" has the chosen AI
 * answer from the passages found, citing them.
 */
@Composable
internal fun SearchPanel(onOpenAt: (eventId: String, tab: Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    var answerBy by remember { mutableStateOf<String?>(null) }
    var asked by remember { mutableStateOf(false) }
    var aiLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { AiPrefs.load(context); Ai.status(context).let { if (it.ready) aiLabel = it.label } }

    var alsoSearched by remember { mutableStateOf<List<String>>(emptyList()) }
    // With an AI chosen, Search (or the button) widens the question into related words, searches all of them, and answers
    // from what it finds, citing the notes.
    fun smart() {
        val q = query.trim()
        if (q.length < 2 || aiLabel == null || (asked && answer == null)) return
        asked = true; answer = null
        scope.launch {
            val s = Ai.status(context)
            val words = Ai.generate(
                context,
                "Give up to 6 search words or short phrases that would find notes answering this question, including synonyms and the " +
                    "main names in it. Reply with one comma-separated line only, nothing else.\nQuestion: $q",
            )?.text?.lines()?.firstOrNull { it.isNotBlank() }?.split(',')?.map { it.trim().trim('.', '"') }?.filter { it.length > 1 && !it.equals(q, true) }?.take(6).orEmpty()
            alsoSearched = words
            busy = true
            val found = withContext(Dispatchers.Default) {
                (runSearch(context, q) + words.flatMap { runSearch(context, it).take(5) }).distinctBy { it.doc.id }.take(20)
            }
            hits = found
            busy = false
            val r = if (found.isEmpty()) null else Ai.generate(context, AskPrompt.build(q, found.take(6), s.promptBudget))
            answer = r?.text?.trim() ?: if (found.isEmpty()) "Nothing in your notes matches that." else "No answer came back. The notes found are listed below."
            answerBy = r?.label
        }
    }

    LaunchedEffect(query) {
        answer = null; asked = false; alsoSearched = emptyList()
        if (query.trim().length < 2) { hits = emptyList(); return@LaunchedEffect }
        delay(220)
        busy = true
        hits = withContext(Dispatchers.Default) { runSearch(context, query) }
        busy = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            textStyle = PlaitType.body.copy(color = plait.text, textDirection = TextDirection.Content), cursorBrush = SolidColor(plait.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { smart() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().heightIn(min = 52.dp).fauxGlass(RoundedCornerShape(26.dp), plait.accent.copy(alpha = 0.16f)).padding(horizontal = 18.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search or ask: what did we decide with…", style = PlaitType.body, color = plait.muted, maxLines = 1)
                    inner()
                }
            },
        )
        if (query.trim().length >= 2) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaitButton("Clear", { query = "" }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                if (aiLabel != null) PlaitButton(
                    if (asked && answer == null) "Thinking…" else "Search with AI", { smart() },
                    style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = !(asked && answer == null),
                )
            }
            if (alsoSearched.isNotEmpty()) Text("Also searched: " + alsoSearched.joinToString(", "), style = PlaitType.caption, color = plait.muted)
        }
        answer?.let { a ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(plait.accent.copy(alpha = 0.16f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(a, style = PlaitType.body.copy(fontSize = 16.sp, lineHeight = 23.sp, textDirection = TextDirection.Content), color = plait.text)
            }
        }
        if (query.trim().length >= 2 && hits.isEmpty() && !busy) Text("Nothing found.", style = PlaitType.body, color = plait.muted)
        hits.forEachIndexed { i, h -> HitCard(i + 1, h, onOpenAt) }
    }
}

internal suspend fun runSearch(context: android.content.Context, query: String): List<Hit> {
    val docs = SearchSource.collect(context)
    val index = SearchIndex(docs)
    if (!Embedder.available(context)) return index.search(query, 20)
    // Meaning search: compare the question with the best word matches and the newest items.
    val candidates = (index.search(query, 40).map { it.doc } + docs.sortedByDescending { it.whenMs }.take(40)).distinctBy { it.id }
    val q = Embedder.embed(context, query) ?: return index.search(query, 20)
    for (d in candidates) if (vectors[d.id] == null) Embedder.embed(context, d.title + ". " + d.text)?.let { vectors[d.id] = it }
    return index.search(query, 20) { d -> vectors[d.id]?.let { SearchIndex.cosine(q, it) } ?: 0.0 }
}

@Composable
private fun HitCard(n: Int, hit: Hit, onOpenAt: (String, Int) -> Unit) {
    val tab = when (hit.doc.kind) { DocKind.Event -> 0; DocKind.Email -> 1; else -> 2 }
    Column(
        Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(24.dp)).pressable({ onOpenAt(hit.doc.eventId, tab) }).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("[$n]", style = PlaitType.monoSmall, color = plait.accent)
            Text(hit.doc.kind.label + if (hit.doc.whenMs > 0) " · " + TimeFmt.stamp(hit.doc.whenMs) else "", style = PlaitType.monoSmall, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(hit.doc.title, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = plait.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(hit.snippet, style = PlaitType.body.copy(fontSize = 14.sp, lineHeight = 20.sp, textDirection = TextDirection.Content), color = plait.text.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

/** What you said you would do, from your notes, with a tick and "tomorrow" on each. */
@Composable
internal fun FollowupsCard(onOpenAt: (eventId: String, tab: Int) -> Unit) {
    val context = LocalContext.current
    remember { app.plait.data.followups.FollowupState.load(context) }
    val notes = app.plait.data.NoteStore.get(context).notes
    val memos = app.plait.data.memo.MemoStore.get(context).items
    val all = remember(notes.size, memos.size, notes.map { it.ai }.hashCode()) { app.plait.data.followups.Followups.collect(notes.toList(), memos.toList()) }
    val now = System.currentTimeMillis()
    val open = all.filter { app.plait.data.followups.FollowupState.isOpen(it.id, now) }
    if (all.isEmpty()) return
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(30.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (open.isEmpty()) "Follow-ups · all done" else "Follow-ups · ${open.size} open", style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
        if (open.isEmpty()) Text("Nothing waiting on you. New ones appear as you take notes.", style = PlaitType.caption, color = plait.muted)
        open.take(8).forEach { f ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(plait.surfaceHigh.copy(alpha = 0.6f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(f.text, style = PlaitType.body.copy(textDirection = TextDirection.Content), color = plait.text)
                val from = listOfNotNull(f.title.ifBlank { null }).joinToString(" · ")
                if (from.isNotBlank()) Text(from, style = PlaitType.caption, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaitButton("Done", { app.plait.data.followups.FollowupState.markDone(context, f.id) }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                    PlaitButton("Tomorrow", {
                        val t = java.time.LocalDate.now().plusDays(1).atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        app.plait.data.followups.FollowupState.snooze(context, f.id, t)
                    }, size = ButtonSize.Small)
                    val slot = remember(f.id, app.plait.data.Calendars.events) {
                        app.plait.data.prep.FreeTime.find(app.plait.data.Calendars.events.filter { !it.allDay }.map { it.startMs to it.endMs }, System.currentTimeMillis(), 5, 30, limit = 1).firstOrNull()
                    }
                    if (slot != null) PlaitButton("Book 30 min · " + app.plait.data.prep.FreeTime.label(slot).substringBefore(" –"), {
                        android.widget.Toast.makeText(context, app.plait.data.device.Booking.book(context, app.plait.data.prep.FocusTime.todoTitle(f.text), slot), android.widget.Toast.LENGTH_LONG).show()
                    }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                    PlaitButton("Open", { onOpenAt(f.eventId, 2) }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                }
            }
        }
        if (open.size > 8) Text("+${open.size - 8} more", style = PlaitType.caption, color = plait.muted)
    }
}
