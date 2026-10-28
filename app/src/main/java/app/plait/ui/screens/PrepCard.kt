package app.plait.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ai.Ai
import app.plait.ai.AiPrefs
import app.plait.data.CalEvent
import app.plait.data.NoteStore
import app.plait.data.brief.MeetingBrief
import app.plait.data.followups.FollowupState
import app.plait.data.memo.MemoStore
import app.plait.data.prep.Prep
import app.plait.data.prep.PrepBuilder
import app.plait.data.prep.PrepEntry
import app.plait.data.prep.PrepStore
import app.plait.data.prep.SinceLast
import app.plait.reminder.Reminders
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.PlaitChip
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import app.plait.R
import app.plait.i18n.Bidi
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * A 30-second plan for the meeting: the goal, what to remember, what to raise. Written by the chosen AI when there is
 * one (and kept, so it can be edited or redone), or put together by rules from the invite, last time's note, open
 * follow-ups and attached emails.
 */
@Composable
internal fun PrepCard(ev: CalEvent, brief: MeetingBrief) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { PrepStore.get(context) }
    val saved = store.entries[ev.id]
    var aiLabel by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AiPrefs.load(context); Ai.status(context).let { aiLabel = if (it.ready) it.label else null } }

    val input = remember(ev.id, NoteStore.get(context).notes.size, MemoStore.get(context).items.size, FollowupState.done.size) { PrepBuilder.input(context, ev, brief) }
    val sections = remember(saved, input) { if (saved != null) Prep.parse(saved.text) else Prep.rules(input) }
    if (saved == null && !input.hasBasis) return // no invite text, notes, follow-ups or emails: no prep is invented

    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = plait.accent, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.home_prep_title), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
        }
        if (editing) {
            BasicTextField(
                value = draft, onValueChange = { draft = it },
                textStyle = PlaitType.body.copy(color = plait.text, textDirection = TextDirection.Content), cursorBrush = SolidColor(plait.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 140.dp).clip(RoundedCornerShape(20.dp)).background(plait.surfaceHigh).padding(14.dp)) { inner() } },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaitButton(stringResource(R.string.common_save), { store.put(ev.id, PrepEntry(draft.trim(), "You", System.currentTimeMillis())); editing = false }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = draft.isNotBlank())
                PlaitButton(stringResource(R.string.common_cancel), { editing = false }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
        } else {
            sections.goal?.let { Text(it, style = PlaitType.body.copy(fontSize = 16.sp, lineHeight = 23.sp, textDirection = TextDirection.Content), color = plait.text) }
            if (sections.remember.isNotEmpty()) { Text(stringResource(R.string.home_prep_remember), style = PlaitType.monoSmall, color = plait.muted); sections.remember.forEach { PrepBullet(it) } }
            if (sections.ask.isNotEmpty()) { Text(stringResource(R.string.home_prep_ask), style = PlaitType.monoSmall, color = plait.muted); sections.ask.forEach { PrepBullet(it) } }
            if (failed) Text(stringResource(R.string.home_prep_failed), style = PlaitType.caption, color = plait.signal)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                aiLabel?.let { label ->
                    PlaitButton(
                        if (busy) stringResource(R.string.home_prep_writing_with, Bidi.isolate(label))
                        else if (saved == null || saved.by == "You") stringResource(R.string.home_prep_write_with, Bidi.isolate(label))
                        else stringResource(R.string.home_prep_write_again),
                        {
                            if (!busy) scope.launch {
                                busy = true; failed = false
                                val e = PrepBuilder.generate(context, ev)
                                failed = e == null
                                if (e != null) Reminders.scheduleAsync(context)
                                busy = false
                            }
                        },
                        style = ButtonStyle.Primary, size = ButtonSize.Small, icon = Icons.Filled.AutoAwesome, enabled = !busy,
                    )
                }
                PlaitButton(stringResource(R.string.common_edit), { draft = saved?.text ?: Prep.render(sections); editing = true }, size = ButtonSize.Small)
                if (saved != null) PlaitButton(stringResource(R.string.home_prep_reset), { store.clear(ev.id) }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
        }
    }
}

@Composable
private fun PrepBullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 9.dp).size(6.dp).background(plait.accent, CircleShape))
        Text(text, style = PlaitType.body.copy(textDirection = TextDirection.Content), color = plait.text)
    }
}

/** Decisions and open follow-ups from earlier meetings in the same series. */
@Composable
internal fun SinceLastCard(ev: CalEvent) {
    val context = LocalContext.current
    remember { FollowupState.load(context) }
    val notes = NoteStore.get(context).notes
    val memos = MemoStore.get(context).items
    val since = remember(ev.id, notes.size, memos.size, FollowupState.done, FollowupState.snoozed) {
        SinceLast.compute(ev, notes.toList(), memos.toList()) { FollowupState.isOpen(it, System.currentTimeMillis()) }
    } ?: return
    if (since.isEmpty) return
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.home_since_title), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
        if (since.decisions.isNotEmpty()) { Text(stringResource(R.string.home_since_decided), style = PlaitType.monoSmall, color = plait.muted); since.decisions.forEach { PrepBullet(it) } }
        if (since.stillOpen.isNotEmpty()) {
            Text(stringResource(R.string.home_since_still_open), style = PlaitType.monoSmall, color = plait.muted)
            since.stillOpen.take(5).forEach { f ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrepBullet(f.text)
                    PlaitButton(stringResource(R.string.common_done), { FollowupState.markDone(context, f.id) }, size = ButtonSize.Small, style = ButtonStyle.Quiet)
                }
            }
        }
        Text(stringResource(R.string.home_since_footer), style = PlaitType.caption, color = plait.muted)
    }
}

/** Send this meeting, with its brief, notes and follow-ups, to anyone. */
@Composable
internal fun ShareCard(ev: CalEvent, brief: MeetingBrief) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.common_share), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
        Text(stringResource(R.string.home_share_body), style = PlaitType.caption, color = plait.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            app.plait.share.ShareFormat.entries.forEach { f ->
                PlaitButton(f.label, { runCatching { app.plait.share.ShareOut.share(context, ev, brief, f) } }, size = ButtonSize.Small, style = if (f == app.plait.share.ShareFormat.Pdf) ButtonStyle.Primary else ButtonStyle.Secondary)
            }
        }

        // Notes for a teammate to import into their own Plait. Only what you tick goes, and only when you send it.
        var team by remember { mutableStateOf(false) }
        var withMail by remember { mutableStateOf(false) }
        var withMedia by remember { mutableStateOf(false) }
        var pass by remember { mutableStateOf("") }
        PlaitButton(stringResource(if (team) R.string.home_share_hide_team else R.string.home_share_send_team), { team = !team }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
        if (team) {
            Text(stringResource(R.string.home_share_team_body), style = PlaitType.caption, color = plait.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaitChip(stringResource(R.string.home_share_attached_emails), withMail, { withMail = !withMail })
                PlaitChip(stringResource(R.string.home_share_recordings_photos), withMedia, { withMedia = !withMedia })
            }
            BasicTextField(
                value = pass, onValueChange = { pass = it }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                textStyle = PlaitType.body.copy(color = plait.text), cursorBrush = SolidColor(plait.accent), modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(22.dp)).background(plait.surfaceHigh).padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (pass.isEmpty()) Text(stringResource(R.string.home_share_passphrase), style = PlaitType.body, color = plait.muted)
                        inner()
                    }
                },
            )
            if (withMail) Text(stringResource(R.string.home_share_mail_warning), style = PlaitType.caption, color = plait.signal)
            PlaitButton(stringResource(R.string.home_share_send_file), {
                runCatching { app.plait.share.TeamShare.send(context, ev, app.plait.share.TeamShare.create(context, ev, withMail, withMedia, pass)) }
            }, style = ButtonStyle.Primary, size = ButtonSize.Small)
        }
    }
}
