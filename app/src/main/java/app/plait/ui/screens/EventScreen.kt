package app.plait.ui.screens

import app.plait.ui.plaitCard
import app.plait.R
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import app.plait.i18n.userText
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ai.NoteAiPrompt
import app.plait.ai.Ai
import app.plait.ai.AiPrefs
import app.plait.audio.NotePlayer
import app.plait.audio.NoteProcessor
import app.plait.ui.ButtonSize
import app.plait.ui.TextUtil
import app.plait.ui.ButtonStyle
import app.plait.ui.ConfirmDeleteButton
import app.plait.ui.PlaitButton
import app.plait.ui.pressable
import kotlinx.coroutines.launch
import app.plait.data.CallLinks
import app.plait.data.brief.BriefBuilder
import app.plait.data.brief.Summarizer
import app.plait.data.NoteStore
import app.plait.data.memo.MemoStore
import app.plait.data.Calendars
import app.plait.data.SavedNote
import app.plait.data.Source
import app.plait.ui.CircleIconButton
import app.plait.ui.glass.GlassIconButton
import app.plait.ui.glass.GlassPillButton
import app.plait.ui.glass.GlassTab
import app.plait.ui.glass.GlassTabBar
import app.plait.ui.glass.TabContainer
import app.plait.ui.glass.liquidGlass
import app.plait.ui.rise
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import kotlinx.coroutines.delay
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun EventScreen(eventId: String, startTab: Int = 0, onBack: () -> Unit, onRecord: (String) -> Unit) {
    val context = LocalContext.current
    val found = remember(eventId) { if (eventId.isEmpty()) null else Calendars.find(eventId) }
    if (found == null) { LaunchedEffect(Unit) { onBack() }; return }
    val ev = found
    val link = ev.callLink
    val store = remember { NoteStore.get(context) }
    val notes = store.notes.filter { it.eventId == ev.id }
    val memoStore = remember { MemoStore.get(context) }
    val brief = remember(ev.id, store.notes.size) { BriefBuilder.build(ev, store.notes.toList()) }
    val backdrop = rememberLayerBackdrop()
    val ambient = rememberLayerBackdrop()
    // A repeating meeting carries the notes and lists of its earlier meetings forward.
    val earlierVoice = store.notes.filter { app.plait.data.Series.carriesTo(ev, it.eventId, it.createdAt, it.title) { id -> Calendars.find(id) } }
    val earlierMemos = memoStore.items.filter { app.plait.data.Series.carriesTo(ev, it.eventId, it.createdAt) { id -> Calendars.find(id) } }
    val look = remember(ev.id) { MeetingLook(ambient, ServicePalette.of(ev)) }
    var manageOpen by remember { mutableStateOf(false) }
    val undo = remember { UndoDelete() }
    var tab by rememberSaveable(eventId) { mutableIntStateOf(when (startTab) { 2 -> 1; 3 -> 2; else -> 0 }) }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tBrief = stringResource(R.string.meet_tab_brief)
    val tNotes = stringResource(R.string.common_notes)
    val tTranscript = stringResource(R.string.meet_tab_transcript)
    val tabs = remember(tBrief, tNotes, tTranscript) { listOf(GlassTab(tBrief), GlassTab(tNotes), GlassTab(tTranscript)) }

    Box(Modifier.fillMaxSize().background(plait.ground)) {
        // Content layer
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(Modifier.fillMaxSize().layerBackdrop(ambient)) { AmbientBackground(look.colors) }
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxHeight().align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = statusTop + 84.dp, bottom = 170.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Column(Modifier.rise(1)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KindPill(brief.kind, brief.kind.tone())
                            if (brief.withLine.isNotBlank()) Text(brief.withLine, style = PlaitType.caption, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(Bidi.isolate(eventDate(ev)) + " · ${Bidi.isolate(ev.time)}" + (if (ev.duration.isNotBlank()) " · ${Bidi.isolate(ev.duration)}" else ""),style = PlaitType.monoLabel, color = plait.muted)
                        app.plait.data.ZoneHint.line(ev)?.let { Text(it, style = PlaitType.monoLabel, color = plait.muted, modifier = Modifier.padding(top = 4.dp)) }
                        Spacer(Modifier.height(10.dp))
                        Text(ev.title, style = PlaitType.title.copy(fontSize = 44.sp, lineHeight = 43.sp).userText(), color = plait.text)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (link != null) "${link.provider.label} · ${Bidi.isolate(link.url.removePrefix("https://"))}" else ev.place,
                            style = PlaitType.monoLabel.let { if (link == null) it.userText() else it }, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item {
                    GlassTabBar(
                        tabs = tabs,
                        selected = tab,
                        onSelect = { tab = it },
                        backdrop = ambient,
                        container = TabContainer.Glass,
                        height = 56.dp,
                        selectedColor = plait.text,
                        modifier = Modifier.rise(2),
                    )
                }
                item {
                  androidx.compose.runtime.CompositionLocalProvider(LocalMeetingLook provides look) {
                    when (tab) {
                        0 -> BriefTab(ev, brief, { onRecord(ev.id) }, notes + earlierVoice, memoStore.items.filter { it.eventId == ev.id } + earlierMemos)
                        1 -> NotesTab(ev.id, notes, store, undo, earlierVoice, earlierMemos) { onRecord(ev.id) }
                        else -> TranscriptTab(ev.id, notes)
                    }
                  }
                }
            }
            // Scroll edge effect; inside the backdrop so the glass toolbar refracts the faded content, not the text.
            app.plait.ui.TopFade(Modifier.align(Alignment.TopCenter))
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(180.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, plait.ground.copy(alpha = 0.92f))))
            )
        }

        undo.Host(Modifier.align(Alignment.BottomCenter).padding(bottom = 112.dp))

        // Functional layer: toolbar. Back, then the account pill at its own width; the manage button sits at the far right.
        val canManageHere = ev.id.startsWith("d:") || ev.id.startsWith("g:")
        Box(Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp).rise(0)) {
            Row(
                Modifier.align(Alignment.CenterStart).padding(end = if (canManageHere) 60.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.meet_back_to_today), onBack, backdrop)
                val account = TextUtil.shortenAccount(ev.account.ifBlank { ev.source.label }.let { if (it.contains('@') || !ev.id.startsWith("g:")) it else ev.source.label })
                if (app.plait.ui.glass.LocalGlassEnv.current.material) {
                    androidx.compose.material3.Surface(
                        shape = CircleShape, color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Row(Modifier.padding(start = 8.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(32.dp).background(ev.source.color(), CircleShape), contentAlignment = Alignment.Center) {
                                Text(ev.source.initial, style = PlaitType.label.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            }
                            Text(account, style = PlaitType.bodyStrong.copy(fontSize = 14.sp).userText(), color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                } else {
                    Row(
                        Modifier.height(48.dp).liquidGlass(backdrop).padding(start = 16.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(10.dp).background(ev.source.color(), CircleShape))
                        Text(account, style = PlaitType.bodyStrong.copy(fontSize = 14.sp).userText(), color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (canManageHere) Box(Modifier.align(Alignment.CenterEnd)) {
                GlassIconButton(Icons.Filled.EditCalendar, stringResource(R.string.meet_manage_meeting), { manageOpen = true }, backdrop)
            }
        }
        if (manageOpen) ManageMeetingMenu(ev, onDismiss = { manageOpen = false }, onGone = onBack)

        // Functional layer: primary actions
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp).rise(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (link != null) {
                GlassPillButton(
                    label = stringResource(R.string.meet_join_provider, link.provider.short),
                    onClick = {
                        if (!CallLinks.open(context, link)) {
                            Toast.makeText(context, Txt.s(R.string.meet_no_app_for_link), Toast.LENGTH_SHORT).show()
                        }
                    },
                    backdrop = backdrop,
                    icon = Icons.Filled.Videocam,
                    tint = plait.accent,
                    contentColor = plait.onAccent,
                    height = 64.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            GlassIconButton(
                Icons.Filled.Mic, stringResource(R.string.meet_record), { onRecord(ev.id) }, backdrop,
                size = 64.dp, iconSize = 28.dp, tint = plait.signal, iconTint = Color(0xFF0E1316),
            )
        }
    }
}

private fun eventDate(ev: app.plait.data.CalEvent): String =
    if (ev.startMs > 0) java.text.SimpleDateFormat("EEE d MMM", app.plait.i18n.AppLocale.locale).format(java.util.Date(ev.startMs)) else ""

@Composable
internal fun EmptyHint(text: String) {
    Text(text, style = PlaitType.body, color = plait.muted, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun NotesTab(
    eventId: String, notes: List<SavedNote>, store: NoteStore, undo: UndoDelete,
    earlierVoice: List<SavedNote>, earlierMemos: List<app.plait.data.memo.Memo>, onRecord: () -> Unit,
) {
    val context = LocalContext.current
    val memos = remember { MemoStore.get(context) }
    val mine = memos.items.filter { it.eventId == eventId && !undo.isHidden("m" + it.id) }
    val voice = notes.filter { !undo.isHidden("v" + it.id) }
    var actionsFor by remember { mutableStateOf<Any?>(null) }
    var editing by remember { mutableStateOf<Any?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AddNoteCard(eventId, memos, LocalMeetingLook.current?.backdrop, onRecord)
        app.plait.data.Calendars.find(eventId)?.let { SuggestedTasksCard(it) }
        mine.forEach { m ->
            key("m" + m.id) {
                SwipeToDelete(onDelete = { undo.delete("m" + m.id, Txt.s(R.string.note_deleted)) { memos.delete(m) } }, cornerRadius = 30) { MemoCard(m, memos, onLongPress = { actionsFor = m }) }
            }
        }
        voice.forEach { n ->
            key("v" + n.id) {
                SwipeToDelete(onDelete = { undo.delete("v" + n.id, Txt.s(R.string.note_deleted)) { store.delete(n) } }, cornerRadius = 30) { RealNoteCard(n, store, onLongPress = { actionsFor = n }) }
            }
        }
        if (voice.isEmpty() && mine.isEmpty()) {
            EmptyHint(stringResource(R.string.note_empty_meeting))
        }
        if (earlierVoice.isNotEmpty() || earlierMemos.isNotEmpty()) {
            Text(stringResource(R.string.note_earlier_in_series), style = PlaitType.monoLabel, color = plait.muted, modifier = Modifier.padding(top = 8.dp))
            earlierMemos.forEach { m -> key("em" + m.id) { MemoCard(m, memos, onLongPress = { actionsFor = m }) } }
            earlierVoice.forEach { n -> key("ev" + n.id) { RealNoteCard(n, store, onLongPress = { actionsFor = n }) } }
        }
    }
    actionsFor?.let { target ->
        ActionsDialog(
            stringResource(R.string.common_note),
            listOf(
                NoteAction(stringResource(if (target is SavedNote) R.string.note_edit_transcript else R.string.note_edit_text), Icons.Filled.Edit) { editing = target },
                NoteAction(stringResource(R.string.common_delete), Icons.Filled.Delete, danger = true) {
                    if (target is SavedNote) undo.delete("v" + target.id, Txt.s(R.string.note_deleted)) { store.delete(target) }
                    else if (target is app.plait.data.memo.Memo) undo.delete("m" + target.id, Txt.s(R.string.note_deleted)) { memos.delete(target) }
                },
            ),
            onDismiss = { actionsFor = null },
        )
    }
    (editing as? app.plait.data.memo.Memo)?.let { m -> EditMemoDialog(m, memos) { editing = null }; return }
    editing?.let { target ->
        EditTextDialog(
            stringResource(if (target is SavedNote) R.string.note_edit_transcript else R.string.note_edit_note),
            if (target is SavedNote) app.plait.data.NoteEdit.transcriptText(target) else editableText(target as app.plait.data.memo.Memo),
            onSave = { text ->
                if (target is SavedNote) store.update(app.plait.data.NoteEdit.applyTranscript(target, text))
                else if (target is app.plait.data.memo.Memo) memos.update(editedMemo(target, text))
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
internal fun RealNoteCard(note: SavedNote, store: NoteStore, onLongPress: () -> Unit) {
    val player = remember(note.id) { NotePlayer(store.audioFile(note), note.durationMs) }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player.isPlaying) {
        while (player.isPlaying) {
            player.sync()
            delay(100)
        }
    }
    val bars = remember(note.id) {
        val rnd = java.util.Random(note.id.hashCode().toLong())
        List(28) { 10 + rnd.nextInt(34) }
    }
    val hasAudio = remember(note.id) { store.audioFile(note).exists() }
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(30.dp)).pressable({}, lift = 2.dp, onLongClick = onLongPress).padding(20.dp)) {
        if (!hasAudio) Text(stringResource(R.string.note_recording_elsewhere), style = PlaitType.caption, color = plait.muted)
        // Playback controls and the waveform run left to right in every language, like any media player.
        else androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CircleIconButton(
                if (player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                stringResource(if (player.isPlaying) R.string.note_pause else R.string.note_play),
                { player.toggle() }, plait.signal, Color(0xFF0E1316), size = 56.dp,
            )
            Row(Modifier.weight(1f).height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                bars.forEachIndexed { i, h ->
                    Box(
                        Modifier.weight(1f).height(h.dp)
                            .background(if (i < player.progress * bars.size) plait.signal else plait.surfaceHigh, RoundedCornerShape(2.dp))
                    )
                }
            }
            Text(formatClock(if (player.isPlaying || player.positionMs > 0) player.positionMs else note.durationMs), style = PlaitType.monoSmall.copy(fontSize = 13.sp), color = plait.muted)
        } }
        if (hasAudio) {
            val speedPrefs = LocalContext.current.getSharedPreferences("plait", android.content.Context.MODE_PRIVATE)
            LaunchedEffect(player) { player.restoreSpeed(speedPrefs.getFloat("play_speed", 1f)) }
            Spacer(Modifier.height(10.dp))
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Icons.Filled.Replay10, stringResource(R.string.note_back_10), { player.skip(-NotePlayer.SKIP_MS) }, plait.surfaceHigh, plait.text, size = 44.dp)
                PlaitButton(
                    NotePlayer.label(player.speed),
                    { speedPrefs.edit().putFloat("play_speed", player.cycleSpeed()).apply() },
                    size = ButtonSize.Small,
                )
                CircleIconButton(Icons.Filled.Forward10, stringResource(R.string.note_forward_10), { player.skip(NotePlayer.SKIP_MS) }, plait.surfaceHigh, plait.text, size = 44.dp)
            }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val stamp = Bidi.isolate(app.plait.data.TimeFmt.stamp(note.createdAt))
            Text(
                note.author?.let { stringResource(R.string.note_from_recorded_at, Bidi.isolate(it), stamp) } ?: stringResource(R.string.note_recorded_at, stamp),
                style = PlaitType.caption, color = plait.muted, modifier = Modifier.weight(1f),
            )
        }
        val texts = remember(note.id) { note.lines.map { it.text } }
        val bullets = remember(note.id) { Summarizer.bullets(texts, 3) }
        val actions = remember(note.id) { Summarizer.actionItems(texts) }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var aiReady by remember { mutableStateOf(false) }
        val speechSpec = remember(AiPrefs.speechModelId) { app.plait.ai.ModelCatalog.byId(AiPrefs.speechModelId)?.takeIf { app.plait.ai.ModelCenter.isInstalled(context, it.id) } }
        var aiBusy by remember { mutableStateOf(false) }
        LaunchedEffect(note.id, note.ai) { AiPrefs.load(context); if (note.lines.size >= 2) aiReady = Ai.ready(context) }
        val ai = remember(note.ai) { note.ai?.let { NoteAiPrompt.parse(it) } }
        NoteProcessor.working[note.id]?.let { status ->
            Spacer(Modifier.height(12.dp))
            Text(status, style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = plait.accent)
        }
        if (speechSpec == null && hasAudio && NoteProcessor.working[note.id] == null) SpeechUpgradeCard()
        if (speechSpec != null && NoteProcessor.working[note.id] == null) {
            Spacer(Modifier.height(10.dp))
            // Pick the language, then the note is transcribed again in it (and summarised again from the new text).
            LanguagePicker(
                label = stringResource(if (NoteProcessor.hasWhisper(context, note.id)) R.string.note_transcribe_again else R.string.note_improve_transcript, AiPrefs.languageName(AiPrefs.speechLang)),
                onPicked = {
                    scope.launch {
                        if (NoteProcessor.improveNow(context, store, note, speechSpec)) store.notes.firstOrNull { it.id == note.id }?.let { NoteProcessor.summariseNow(context, store, it) }
                    }
                },
            )
        }
        if (ai != null) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, tint = plait.accent, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.common_summary), style = PlaitType.section.copy(fontSize = 18.sp), color = plait.text)
            }
            ai.summary?.let { Text(it, style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.padding(top = 6.dp)) }
            if (ai.decisions.isNotEmpty()) {
                Text(stringResource(R.string.note_decisions), style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 10.dp))
                ai.decisions.forEach { Text("• $it", style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.padding(top = 3.dp)) }
            }
            if (ai.followUps.isNotEmpty()) {
                Text(stringResource(R.string.note_follow_ups), style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 10.dp))
                ai.followUps.forEach { Text("• $it", style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.padding(top = 3.dp)) }
            }
            if (aiReady && NoteProcessor.working[note.id] == null) {
                Spacer(Modifier.height(8.dp))
                PlaitButton(
                    stringResource(if (aiBusy) R.string.note_writing else R.string.note_write_again),
                    { if (!aiBusy) { aiBusy = true; scope.launch { NoteProcessor.summariseNow(context, store, note); aiBusy = false } } },
                    size = ButtonSize.Small, style = ButtonStyle.Quiet, icon = Icons.Filled.AutoAwesome,
                )
            }
        } else {
            if (aiReady && NoteProcessor.working[note.id] == null) {
                Spacer(Modifier.height(12.dp))
                PlaitButton(
                    stringResource(if (aiBusy) R.string.note_summarising else R.string.note_summarise),
                    { if (!aiBusy) { aiBusy = true; scope.launch { NoteProcessor.summariseNow(context, store, note); aiBusy = false } } },
                    size = ButtonSize.Small, icon = Icons.Filled.AutoAwesome,
                )
            }
        }
        if (ai == null && bullets.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.common_summary), style = PlaitType.section.copy(fontSize = 18.sp), color = plait.text)
            Spacer(Modifier.height(6.dp))
            bullets.forEach { b ->
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(5.dp).background(plait.accent, CircleShape))
                    Text(b, style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.weight(1f))
                }
            }
        }
        if (ai == null && actions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.note_follow_ups), style = PlaitType.section.copy(fontSize = 18.sp), color = plait.text)
            Spacer(Modifier.height(6.dp))
            actions.forEach { a ->
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(5.dp).background(plait.signal, CircleShape))
                    Text(a, style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.weight(1f))
                }
            }
        }
        if (note.bookmarksMs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                note.bookmarksMs.take(4).forEach { ms ->
                    Box(
                        Modifier.height(36.dp).clip(CircleShape).background(plait.surfaceHigh)
                            .pressable({ player.seekTo(ms) }).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(formatClock(ms), style = PlaitType.monoSmall.copy(fontSize = 12.sp), color = plait.accent) }
                }
            }
        }
    }
}

@Composable
internal fun TranscriptTab(eventId: String, notes: List<SavedNote>) {
    if (notes.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            notes.forEach { n ->
                Text(
                    stringResource(R.string.note_recorded_at, Bidi.isolate(app.plait.data.TimeFmt.stamp(n.createdAt))),
                    style = PlaitType.monoLabel, color = plait.muted,
                )
                if (n.lines.isEmpty()) EmptyHint(n.silentReason ?: stringResource(R.string.note_no_speech_in_note))
                else Column(
                    Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Phrases read as paragraphs; a new one starts only after a real pause.
                    app.plait.data.Transcript.paragraphs(n.lines).forEach { p ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(formatClock(p.atMs), style = PlaitType.monoSmall.copy(fontSize = 12.sp), color = plait.muted, modifier = Modifier.width(44.dp).padding(top = 3.dp))
                            Column(Modifier.weight(1f)) {
                                // Each paragraph keeps its own direction, so Arabic and English lines both sit and flow right.
                                Text(
                                    p.text, style = PlaitType.body.userText(),
                                    color = if (p.shaky) plait.muted else plait.text, modifier = Modifier.fillMaxWidth(),
                                )
                                if (p.shaky) Text(stringResource(R.string.note_partly_unclear), style = PlaitType.caption, color = plait.muted)
                            }
                        }
                    }
                }
            }
        }
        return
    }
    EmptyHint(stringResource(R.string.note_transcripts_empty))
}
