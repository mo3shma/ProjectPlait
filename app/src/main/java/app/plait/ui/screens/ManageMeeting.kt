package app.plait.ui.screens

import app.plait.ui.popupSurface
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ui.PlaitDialog
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.device.EventEdit
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import java.util.Calendar
import androidx.compose.ui.res.stringResource
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.Txt
import app.plait.i18n.userText

/**
 * What you can do to a meeting. One you organise: edit its details, move it, cancel it (one occurrence or all). Someone
 * else's: answer Going, Maybe or Decline. Works for meetings in the phone's calendars and for ones read straight from
 * Google (which asks once for permission to change events). Anything destructive asks first.
 */
@Composable
internal fun ManageMeetingMenu(ev: CalEvent, onDismiss: () -> Unit, onGone: () -> Unit, undo: UndoDelete? = null) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val google = ev.id.startsWith("g:")
    val mine = EventEdit.isMine(ev)
    val manage = if (google) mine else EventEdit.canManage(context, ev)
    val hasSelf = ev.guests.any { it.self }
    var editing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Boolean>?>(null) }
    var pending by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }

    val finish: (Boolean, String, Boolean) -> Unit = { ok, msg, gone ->
        Toast.makeText(context, if (ok) msg else Txt.s(R.string.sheet_manage_failed), Toast.LENGTH_LONG).show()
        if (ok) { Calendars.refreshDevice(context); if (gone) onGone() }
    }
    // Google needs a token with permission to edit; the first time, Google shows its consent screen.
    val consent = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = app.plait.data.google.GoogleAuth.tokenFrom(context, r.data)
        val job = pending
        pending = null
        if (token != null && job != null) scope.launch { job(token) } else Toast.makeText(context, Txt.s(R.string.sheet_manage_no_permission), Toast.LENGTH_SHORT).show()
    }
    /** Runs a change on the right calendar: the phone's, or Google's with a write token. */
    fun run(msg: String, gone: Boolean = false, phone: () -> Boolean, web: (app.plait.data.google.GoogleWrite) -> Unit) {
        if (!google) { finish(phone(), msg, gone); return }
        val job: suspend (String) -> Unit = { token ->
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { web(app.plait.data.google.GoogleWrite(token)) }.isSuccess }
            if (ok) runCatching { Calendars.sync(context, token) }
            finish(ok, msg, gone)
        }
        scope.launch {
            when (val a = app.plait.data.google.GoogleAuth.authorizeWrite(context)) {
                is app.plait.data.google.AuthOutcome.Token -> job(a.accessToken)
                is app.plait.data.google.AuthOutcome.NeedsConsent -> { pending = job; consent.launch(androidx.activity.result.IntentSenderRequest.Builder(a.pendingIntent).build()) }
                is app.plait.data.google.AuthOutcome.Failed -> Toast.makeText(context, a.reason, Toast.LENGTH_LONG).show()
            }
        }
    }
    val told = stringResource(R.string.sheet_manage_told)
    val reschedule = {
        // The phone's own date and time pickers; the meeting keeps its length.
        val c = Calendar.getInstance().apply { timeInMillis = ev.startMs }
        val length = (ev.endMs - ev.startMs).coerceAtLeast(15 * 60_000L)
        android.app.DatePickerDialog(context, { _, y, m, d ->
            android.app.TimePickerDialog(context, { _, h, min ->
                val start = Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                run(Txt.s(R.string.sheet_manage_moved), phone = { EventEdit.reschedule(context, ev, start, start + length) }, web = { it.reschedule(ev, start, start + length) })
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun ask(question: String, act: () -> Unit) { confirm = question to { act(); true } }
    // Cancelling or deleting waits five seconds with an Undo bar (the calendar is only changed when it runs out).
    val undoKey = "ev" + ev.id
    fun final(label: String, act: (gone: Boolean) -> Unit) {
        if (undo == null) act(true) else undo.delete(undoKey, label) { act(!undo.leaving) }
    }
    val actions = buildList {
        if (manage) {
            add(NoteAction(stringResource(R.string.sheet_manage_edit), Icons.Filled.Edit) { editing = true })
            add(NoteAction(stringResource(if (ev.recurring) R.string.sheet_manage_reschedule_one else R.string.sheet_manage_reschedule), Icons.Filled.Schedule) { reschedule() })
            if (ev.recurring) {
                add(NoteAction(stringResource(R.string.sheet_manage_cancel_one), Icons.Filled.Cancel, danger = true) {
                    ask(Txt.s(R.string.sheet_manage_cancel_one_q)) { final(Txt.s(R.string.sheet_manage_undo_cancelled)) { g -> run(told, g, { EventEdit.cancelOccurrence(context, ev) }, { it.cancelOccurrence(ev) }) } }
                })
                add(NoteAction(stringResource(R.string.sheet_manage_delete_series), Icons.Filled.DeleteForever, danger = true) {
                    ask(Txt.s(R.string.sheet_manage_delete_series_q)) { final(Txt.s(R.string.sheet_manage_undo_series)) { g -> run(told, g, { EventEdit.delete(context, ev) }, { it.delete(ev, wholeSeries = true) }) } }
                })
            } else {
                val withGuests = ev.guests.any { !it.self }
                add(NoteAction(stringResource(if (withGuests) R.string.sheet_manage_cancel_meeting else R.string.common_delete), Icons.Filled.DeleteForever, danger = true) {
                    ask(Txt.s(if (withGuests) R.string.sheet_manage_cancel_q else R.string.sheet_manage_delete_q)) { final(Txt.s(if (withGuests) R.string.sheet_manage_undo_cancelled else R.string.sheet_manage_undo_deleted)) { g -> run(told, g, { EventEdit.delete(context, ev) }, { it.delete(ev, wholeSeries = false) }) } }
                })
            }
        } else if (hasSelf) {
            listOf(
                Triple(R.string.sheet_rsvp_answer_going to R.string.sheet_rsvp_done_going, Icons.Filled.CheckCircle, app.plait.data.Rsvp.Accepted),
                Triple(R.string.sheet_rsvp_answer_maybe to R.string.sheet_rsvp_done_maybe, Icons.AutoMirrored.Filled.HelpOutline, app.plait.data.Rsvp.Tentative),
                Triple(R.string.sheet_rsvp_answer_decline to R.string.sheet_rsvp_done_decline, Icons.Filled.Cancel, app.plait.data.Rsvp.Declined),
            ).forEach { (label, icon, answer) ->
                add(NoteAction(stringResource(label.first), icon, danger = answer == app.plait.data.Rsvp.Declined) {
                    run(Txt.s(label.second), phone = { EventEdit.respond(context, ev, answer) }, web = { it.respond(ev, answer) })
                })
            }
        }
        if (!google) add(NoteAction(stringResource(R.string.sheet_manage_open_calendar), Icons.AutoMirrored.Filled.OpenInNew) { EventEdit.openInCalendar(context, ev) })
    }
    // The menu closes as soon as something is picked; the whole thing ends once no follow-up dialog is open.
    var menuVisible by remember { mutableStateOf(true) }
    if (menuVisible) ActionsDialog(stringResource(if (manage) R.string.sheet_manage_title else R.string.sheet_manage_your_answer), actions, onDismiss = { menuVisible = false })
    androidx.compose.runtime.LaunchedEffect(menuVisible, editing, confirm, pending, undo?.isHidden(undoKey)) { if (!menuVisible && !editing && confirm == null && pending == null && undo?.isHidden(undoKey) != true) onDismiss() }

    if (editing) EditEventDialog(ev, canMeet = google && mine, onSave = { t, p, d, meet ->
        run(Txt.s(R.string.sheet_manage_saved), phone = { EventEdit.updateDetails(context, ev, t, p, d) }, web = {
            it.updateDetails(ev, t, p, d)
            // The details are already saved; a Meet link that Google refuses must not undo that.
            if (meet) runCatching { it.addMeet(ev, java.util.UUID.randomUUID().toString()) }.onFailure { e ->
                val why = app.plait.data.google.GoogleEventBody.whyNoMeet(e)
                android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(context, Txt.s(R.string.sheet_meet_failed, why), Toast.LENGTH_LONG).show() }
            }
        })
    }, onDismiss = { editing = false })
    confirm?.let { (question, act) ->
        DetailDialog(stringResource(R.string.sheet_confirm_title), { confirm = null }) {
            Text(question, style = PlaitType.body, color = plait.text)
            PlaitButton(stringResource(R.string.common_yes), { scope.launch { act() }; confirm = null }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Danger)
        }
    }
}

@Composable
private fun EditEventDialog(ev: CalEvent, canMeet: Boolean, onSave: (String, String, String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(ev.title) }
    var place by remember { mutableStateOf(ev.location ?: ev.place) }
    var notes by remember { mutableStateOf(ev.description.orEmpty()) }
    // Call link: 0 keep as it is, 1 make a Google Meet link, 2 add a pasted link.
    var callMode by remember { mutableStateOf(0) }
    var linkText by remember { mutableStateOf("") }
    val has = ev.callLink
    val parsed = if (callMode == 2) app.plait.data.CallLinkInput.parse(linkText) else null
    PlaitDialog(onDismissRequest = onDismiss) { PopIn {
        Column(Modifier.fillMaxWidth().popupSurface().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sheet_edit_title), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
            Field(stringResource(R.string.common_title), title, 1) { title = it }
            Field(stringResource(R.string.sheet_edit_place), place, 1) { place = it }
            Field(stringResource(R.string.sheet_edit_description), notes, 6) { notes = it }
            Text(stringResource(R.string.sheet_edit_call_link).uppercase(AppLocale.locale), style = PlaitType.monoSmall, color = plait.muted)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                app.plait.ui.PlaitChip(if (has != null) stringResource(R.string.sheet_edit_keep, has.provider.short) else stringResource(R.string.sheet_edit_none), callMode == 0, { callMode = 0 })
                // A meeting that already has a call keeps it: the location and Google's own link are read before anything added here.
                if (has == null && canMeet) app.plait.ui.PlaitChip(stringResource(R.string.sheet_google_meet), callMode == 1, { callMode = 1 })
                if (has == null) app.plait.ui.PlaitChip(stringResource(R.string.sheet_edit_add_link), callMode == 2, { callMode = 2 })
            }
            if (callMode == 2) {
                Field(stringResource(R.string.sheet_edit_link_hint), linkText, 1) { linkText = it }
                (parsed as? app.plait.data.CallLinkInput.Parsed.Bad)?.takeIf { linkText.isNotBlank() }?.let { Text(it.reason, style = PlaitType.caption, color = plait.signal) }
                (parsed as? app.plait.data.CallLinkInput.Parsed.Ok)?.let {
                    Text(it.provider?.let { p -> stringResource(R.string.sheet_edit_link_of, p.label) } ?: stringResource(R.string.sheet_edit_link_other), style = PlaitType.caption, color = plait.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaitButton(stringResource(R.string.common_save), {
                    val ok = parsed as? app.plait.data.CallLinkInput.Parsed.Ok
                    val (p2, n2) = if (ok != null) app.plait.data.CallLinkInput.place(ok.url, place.trim(), notes.trim()) else place.trim() to notes.trim()
                    onSave(title.trim(), p2, n2, callMode == 1); onDismiss()
                }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = title.isNotBlank() && (callMode != 2 || parsed is app.plait.data.CallLinkInput.Parsed.Ok))
                PlaitButton(stringResource(R.string.common_cancel), onDismiss, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
        }
    } }
}

@Composable
private fun Field(label: String, value: String, lines: Int, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(AppLocale.locale), style = PlaitType.monoSmall, color = plait.muted)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = lines == 1, maxLines = lines,
            textStyle = PlaitType.body.copy(color = plait.text).userText(), cursorBrush = SolidColor(plait.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).background(plait.surface).padding(horizontal = 16.dp, vertical = 13.dp)) { inner() } },
        )
    }
}
