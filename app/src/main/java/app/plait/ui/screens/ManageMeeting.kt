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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.HelpOutline
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

/**
 * What you can do to a meeting. One you organise: edit its details, move it, cancel it (one occurrence or all). Someone
 * else's: answer Going, Maybe or Decline. Works for meetings in the phone's calendars and for ones read straight from
 * Google (which asks once for permission to change events). Anything destructive asks first.
 */
@Composable
internal fun ManageMeetingMenu(ev: CalEvent, onDismiss: () -> Unit, onGone: () -> Unit) {
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
        Toast.makeText(context, if (ok) msg else "Couldn't change it. Try from the calendar app.", Toast.LENGTH_LONG).show()
        if (ok) { Calendars.refreshDevice(context); if (gone) onGone() }
    }
    // Google needs a token with permission to edit; the first time, Google shows its consent screen.
    val consent = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val token = app.plait.data.google.GoogleAuth.tokenFrom(context, r.data)
        val job = pending
        pending = null
        if (token != null && job != null) scope.launch { job(token) } else Toast.makeText(context, "Permission to change the meeting wasn't given.", Toast.LENGTH_SHORT).show()
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
    val told = "Done. Guests are told."
    val reschedule = {
        // The phone's own date and time pickers; the meeting keeps its length.
        val c = Calendar.getInstance().apply { timeInMillis = ev.startMs }
        val length = (ev.endMs - ev.startMs).coerceAtLeast(15 * 60_000L)
        android.app.DatePickerDialog(context, { _, y, m, d ->
            android.app.TimePickerDialog(context, { _, h, min ->
                val start = Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
                run("Moved. Guests are told.", phone = { EventEdit.reschedule(context, ev, start, start + length) }, web = { it.reschedule(ev, start, start + length) })
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), android.text.format.DateFormat.is24HourFormat(context)).show()
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }
    fun ask(question: String, act: () -> Unit) { confirm = question to { act(); true } }
    val actions = buildList {
        if (manage) {
            add(NoteAction("Edit details", Icons.Filled.Edit) { editing = true })
            add(NoteAction(if (ev.recurring) "Reschedule this one" else "Reschedule", Icons.Filled.Schedule) { reschedule() })
            if (ev.recurring) {
                add(NoteAction("Cancel this one", Icons.Filled.Cancel, danger = true) {
                    ask("Cancel this meeting on its date? The rest of the series stays.") { run(told, true, { EventEdit.cancelOccurrence(context, ev) }, { it.cancelOccurrence(ev) }) }
                })
                add(NoteAction("Delete the whole series", Icons.Filled.DeleteForever, danger = true) {
                    ask("Delete every meeting in this series? Guests get a cancellation.") { run(told, true, { EventEdit.delete(context, ev) }, { it.delete(ev, wholeSeries = true) }) }
                })
            } else {
                val withGuests = ev.guests.any { !it.self }
                add(NoteAction(if (withGuests) "Cancel meeting" else "Delete", Icons.Filled.DeleteForever, danger = true) {
                    ask(if (withGuests) "Cancel this meeting? Guests get a cancellation." else "Delete this meeting?") { run(told, true, { EventEdit.delete(context, ev) }, { it.delete(ev, wholeSeries = false) }) }
                })
            }
        } else if (hasSelf) {
            listOf(
                Triple("Going", Icons.Filled.CheckCircle, app.plait.data.Rsvp.Accepted),
                Triple("Maybe", Icons.Filled.HelpOutline, app.plait.data.Rsvp.Tentative),
                Triple("Decline", Icons.Filled.Cancel, app.plait.data.Rsvp.Declined),
            ).forEach { (label, icon, answer) ->
                add(NoteAction("Answer: $label", icon, danger = answer == app.plait.data.Rsvp.Declined) {
                    run("Answered $label. The organizer is told.", phone = { EventEdit.respond(context, ev, answer) }, web = { it.respond(ev, answer) })
                })
            }
        }
        if (!google) add(NoteAction("Open in calendar app", Icons.Filled.OpenInNew) { EventEdit.openInCalendar(context, ev) })
    }
    // The menu closes as soon as something is picked; the whole thing ends once no follow-up dialog is open.
    var menuVisible by remember { mutableStateOf(true) }
    if (menuVisible) ActionsDialog(if (manage) "Manage meeting" else "Your answer", actions, onDismiss = { menuVisible = false })
    androidx.compose.runtime.LaunchedEffect(menuVisible, editing, confirm, pending) { if (!menuVisible && !editing && confirm == null && pending == null) onDismiss() }

    if (editing) EditEventDialog(ev, onSave = { t, p, d ->
        run("Saved. Guests are told.", phone = { EventEdit.updateDetails(context, ev, t, p, d) }, web = { it.updateDetails(ev, t, p, d) })
    }, onDismiss = { editing = false })
    confirm?.let { (question, act) ->
        DetailDialog("Are you sure?", { confirm = null }) {
            Text(question, style = PlaitType.body, color = plait.text)
            PlaitButton("Yes", { scope.launch { act() }; confirm = null }, modifier = Modifier.fillMaxWidth(), style = ButtonStyle.Danger)
        }
    }
}

@Composable
private fun EditEventDialog(ev: CalEvent, onSave: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(ev.title) }
    var place by remember { mutableStateOf(ev.location ?: ev.place) }
    var notes by remember { mutableStateOf(ev.description.orEmpty()) }
    PlaitDialog(onDismissRequest = onDismiss) { PopIn {
        Column(Modifier.fillMaxWidth().popupSurface().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Edit meeting", style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
            Field("Title", title, 1) { title = it }
            Field("Place", place, 1) { place = it }
            Field("Description", notes, 6) { notes = it }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaitButton("Save", { onSave(title.trim(), place.trim(), notes.trim()); onDismiss() }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = title.isNotBlank())
                PlaitButton("Cancel", onDismiss, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
        }
    } }
}

@Composable
private fun Field(label: String, value: String, lines: Int, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = PlaitType.monoSmall, color = plait.muted)
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = lines == 1, maxLines = lines,
            textStyle = PlaitType.body.copy(color = plait.text), cursorBrush = SolidColor(plait.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(20.dp)).background(plait.surface).padding(horizontal = 16.dp, vertical = 13.dp)) { inner() } },
        )
    }
}
