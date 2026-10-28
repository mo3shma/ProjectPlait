package app.plait.ui.screens

import app.plait.ui.popupSurface
import app.plait.ui.plaitCard
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import app.plait.data.Calendars
import app.plait.data.CallLinkInput
import app.plait.data.google.AuthOutcome
import app.plait.data.google.GoogleAuth
import app.plait.data.google.GoogleEventBody
import app.plait.data.google.GoogleWrite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import app.plait.data.device.DeviceCalendar
import app.plait.data.device.DeviceWrite
import app.plait.data.device.MeetingDraft
import app.plait.data.device.MeetingGuests
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.PlaitChip
import app.plait.ui.pressable
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import app.plait.i18n.userText

/**
 * Makes a new meeting and saves it to the calendar chosen in the hub. Android's sync then pushes it
 * to the account (Google, Outlook, iCloud) and sends invites to the guests.
 */
@Composable
fun NewMeetingSheet(visible: Boolean, initialDate: LocalDate, onDismiss: () -> Unit, onOpenHub: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val allow = rememberCalendarAccess()
    var title by remember { mutableStateOf("") }
    var quick by remember { mutableStateOf("") }
    var findOpen by remember { mutableStateOf(false) }
    var findDays by remember { mutableIntStateOf(7) }
    var date by remember(initialDate, visible) { mutableStateOf(initialDate) }
    var startMin by remember(visible) { mutableIntStateOf(nextHalfHour()) }
    var duration by remember { mutableIntStateOf(30) }
    var allDay by remember { mutableStateOf(false) }
    var guests by remember { mutableStateOf("") }
    var place by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    // Call link: 0 none, 1 a Google Meet link made by Google, 2 a link pasted in.
    var callMode by remember { mutableIntStateOf(0) }
    var linkText by remember { mutableStateOf("") }
    var doneNote by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<(suspend (String?) -> Unit)?>(null) }
    val consent = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val job = pending
        pending = null
        if (job != null) scope.launch { job(GoogleAuth.tokenFrom(context, r.data)) }
    }

    LaunchedEffect(visible) { if (visible && DeviceCalendar.hasPermission(context)) Calendars.refreshDevice(context) }
    val dest = Calendars.destination()
    val canWrite = DeviceWrite.hasPermission(context)

    // Meet is made by Google for the account's own calendar, so it needs a Google calendar that is connected in the hub.
    val meetOk = Calendars.googleOn && dest != null && dest.accountType == "com.google" && dest.primary
    val reset = { title = ""; guests = ""; place = ""; notes = ""; allDay = false; error = null; callMode = 0; linkText = ""; doneNote = null; saving = false }
    LaunchedEffect(visible) { if (!visible && doneNote != null) reset() }

    // [note] is set when something did not go as asked; the meeting is still saved and the note stays on screen.
    val saveToPhone: (MeetingDraft, String?) -> Unit = { draft, note ->
        val calId = dest?.id ?: DeviceWrite.ensureLocalCalendar(context)
        if (calId == null) {
            error = Txt.s(R.string.sheet_new_no_calendar)
            saving = false
        } else when (val r = DeviceWrite.insert(context, calId, draft)) {
            is DeviceWrite.Result.Saved -> {
                Calendars.refreshDevice(context)
                if (note == null) {
                    Toast.makeText(context, Txt.s(R.string.sheet_new_saved_to, Bidi.isolate(r.calendar)), Toast.LENGTH_LONG).show()
                    reset(); onDismiss()
                } else { saving = false; doneNote = note }
            }
            is DeviceWrite.Result.Failed -> { error = r.reason; saving = false }
        }
    }

    // Creates the event on Google with a Meet request; if Google says no, the event is made without it (and as a last
    // resort on the phone) so the meeting is never lost.
    val saveWithMeet: (MeetingDraft) -> Unit = { draft ->
        val account = dest?.account.orEmpty()
        val job: suspend (String?) -> Unit = job@{ token ->
            if (token == null) { saveToPhone(draft, Txt.s(R.string.sheet_new_meet_not_approved)); return@job }
            var why: String? = null
            val made = withContext(Dispatchers.IO) {
                val w = GoogleWrite(token)
                runCatching { w.create(account, draft, java.time.ZoneId.systemDefault(), UUID.randomUUID().toString()) }
                    .recoverCatching { e -> why = GoogleEventBody.whyNoMeet(e); w.create(account, draft, java.time.ZoneId.systemDefault(), null) }
                    .getOrNull()
            }
            if (made == null) { saveToPhone(draft, Txt.s(R.string.sheet_new_meet_phone, why ?: Txt.s(R.string.sheet_new_google_unreachable))); return@job }
            runCatching { Calendars.sync(context, token) } // so Join shows right away
            val note = when {
                why != null -> Txt.s(R.string.sheet_meet_failed, why)
                made.meetUrl == null -> Txt.s(R.string.sheet_new_meet_pending)
                else -> null
            }
            if (note == null) { Toast.makeText(context, Txt.s(R.string.sheet_new_saved_meet), Toast.LENGTH_LONG).show(); reset(); onDismiss() }
            else { saving = false; doneNote = note }
        }
        scope.launch {
            when (val a = GoogleAuth.authorizeWrite(context)) {
                is AuthOutcome.Token -> job(a.accessToken)
                is AuthOutcome.NeedsConsent -> { pending = job; consent.launch(androidx.activity.result.IntentSenderRequest.Builder(a.pendingIntent).build()) }
                is AuthOutcome.Failed -> saveToPhone(draft, Txt.s(R.string.sheet_meet_failed, a.reason))
            }
        }
    }

    val save: () -> Unit = {
        if (!saving) {
            val mode = if (callMode == 1 && !meetOk) 0 else callMode
            val link = if (mode == 2) CallLinkInput.parse(linkText) else null
            val (placed, notesWithLink) = (link as? CallLinkInput.Parsed.Ok)?.let { CallLinkInput.place(it.url, place, notes) } ?: (place to notes)
            val draft = MeetingDraft(
                title = title, date = date, start = LocalTime.of(startMin / 60, startMin % 60), durationMin = duration,
                allDay = allDay, guests = MeetingGuests.parse(guests), place = placed, notes = notesWithLink,
            )
            val problem = draft.problem() ?: (link as? CallLinkInput.Parsed.Bad)?.reason
            if (problem != null) error = problem
            else if (!canWrite || !DeviceCalendar.hasPermission(context)) allow()
            else {
                saving = true
                if (mode == 1) saveWithMeet(draft) else saveToPhone(draft, null)
            }
        }
    }

    app.plait.ui.DimScrim(visible, app.plait.ui.Dim.Sheet, onDismiss)
    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) { it } + fadeIn(tween(140)),
        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(160)),
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 760.dp)
                    .popupSurface(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .clickable(interactionSource = null, indication = null) {}
                    .navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 40.dp, height = 5.dp).background(plait.muted.copy(alpha = 0.5f), CircleShape))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.sheet_new_title), style = PlaitType.section.copy(fontSize = 28.sp, lineHeight = 32.sp), color = plait.text)
                    PlaitButton(stringResource(R.string.common_cancel), onDismiss, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                }

                // Quick add: type it in one line and Plait fills in the form below.
                Field(quick, { quick = it }, stringResource(R.string.sheet_new_quick_hint))
                if (quick.isNotBlank()) {
                    PlaitButton(
                        stringResource(R.string.sheet_new_fill),
                        {
                            val today = java.time.LocalDate.now()
                            val rules = app.plait.data.device.QuickAdd.parse(quick, today)
                            val text = quick
                            fun apply(r: app.plait.data.device.QuickResult) {
                                if (r.title.isNotBlank()) title = r.title
                                r.date?.let { date = it }
                                r.time?.let { startMin = it.hour * 60 + it.minute }
                                r.durationMin?.let { duration = it.coerceIn(5, 600) }
                                if (r.guests.isNotEmpty()) guests = r.guests.joinToString(", ")
                                if (r.place.isNotBlank()) place = r.place
                                if (r.allDay) allDay = true
                                quick = ""; error = null
                            }
                            apply(rules)
                            // The rules cover plain English. If they found no day or time, the default AI gets a go.
                            if (rules.date == null && rules.time == null && text.length > 12) scope.launch {
                                val ai = app.plait.ai.Ai.generate(context, app.plait.data.device.QuickAdd.aiPrompt(text, today))
                                ai?.let { app.plait.data.device.QuickAdd.parseAi(it.text) }?.let { apply(it) }
                            }
                        },
                        style = ButtonStyle.Primary, size = ButtonSize.Small,
                    )
                }
                Field(title, { title = it; error = null }, stringResource(R.string.common_title), capitalize = true)

                // When
                val dayFmt = app.plait.data.TimeFmt.fmt("EEE d MMM")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ‹ and › are bidi-mirrored characters: Arabic text already draws them flipped.
                    Stepper("‹", stringResource(R.string.common_previous)) { date = date.minusDays(1) }
                    Text(date.format(dayFmt), style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.weight(1f))
                    Stepper("›", stringResource(R.string.common_next)) { date = date.plusDays(1) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.common_today), date == LocalDate.now()) { date = LocalDate.now() }
                    Pill(stringResource(R.string.common_tomorrow), date == LocalDate.now().plusDays(1)) { date = LocalDate.now().plusDays(1) }
                    Pill(stringResource(R.string.common_all_day), allDay) { allDay = !allDay }
                }
                if (!allDay) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stepper("−", stringResource(R.string.sheet_new_earlier)) { startMin = (startMin - 15).mod(24 * 60) }
                        Text(app.plait.data.TimeFmt.clock(startMin), style = PlaitType.monoTime.copy(fontSize = 22.sp), color = plait.text, modifier = Modifier.weight(1f))
                        Stepper("+", stringResource(R.string.sheet_new_later)) { startMin = (startMin + 15).mod(24 * 60) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30, 45, 60, 90, 120).forEach { m -> Pill(if (m < 60) pluralStringResource(R.plurals.sheet_new_minutes, m, m) else if (m % 60 == 0) pluralStringResource(R.plurals.sheet_new_hours, m / 60, m / 60) else stringResource(R.string.sheet_new_hour_and_half), duration == m) { duration = m } }
                    }
                }

                // Find a time: gaps in your own calendars where this length fits.
                PlaitButton(stringResource(if (findOpen) R.string.sheet_new_hide_free else R.string.sheet_new_find_time), { findOpen = !findOpen }, size = ButtonSize.Small, style = ButtonStyle.Quiet)
                if (findOpen) {
                    val busy = remember(Calendars.events) { Calendars.events.filter { !it.allDay && it.endMs > it.startMs }.map { it.startMs to it.endMs } }
                    val slots = remember(busy, duration, findDays, allDay) {
                        app.plait.data.prep.FreeTime.find(busy, System.currentTimeMillis(), findDays, duration.coerceAtLeast(15), workDay = { app.plait.data.week.WorkWeek.isWorkDay(it) })
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(plait.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(pluralStringResource(R.plurals.sheet_new_free_for, duration, duration), style = PlaitType.caption, color = plait.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlaitChip(stringResource(R.string.sheet_new_next_7), findDays == 7, { findDays = 7 })
                            PlaitChip(stringResource(R.string.sheet_new_next_14), findDays == 14, { findDays = 14 })
                        }
                        if (slots.isEmpty()) Text(stringResource(R.string.sheet_new_no_free), style = PlaitType.body, color = plait.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slots.forEach { s ->
                                val local = java.time.Instant.ofEpochMilli(s.startMs).atZone(java.time.ZoneId.systemDefault())
                                val picked = date == local.toLocalDate() && startMin == local.hour * 60 + local.minute
                                PlaitChip(app.plait.data.prep.FreeTime.label(s), picked, {
                                    date = local.toLocalDate(); startMin = local.hour * 60 + local.minute; allDay = false
                                })
                            }
                        }
                        val chooser = stringResource(R.string.sheet_new_send_options_chooser)
                        if (slots.isNotEmpty()) PlaitButton(stringResource(R.string.sheet_new_send_options), {
                            val text = app.plait.data.prep.FreeTime.shareText(title, slots.take(4))
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
                            runCatching { context.startActivity(android.content.Intent.createChooser(send, chooser).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }, size = ButtonSize.Small)
                    }
                }

                Field(guests, { guests = it; error = null }, stringResource(R.string.sheet_new_guests_hint), keyboard = KeyboardType.Email)
                // Call link
                Text(stringResource(R.string.sheet_edit_call_link), style = PlaitType.monoSmall, color = plait.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.sheet_edit_none), callMode == 0) { callMode = 0; error = null }
                    if (meetOk) Pill(stringResource(R.string.sheet_google_meet), callMode == 1) { callMode = 1; error = null }
                    Pill(stringResource(R.string.sheet_edit_add_link), callMode == 2) { callMode = 2; error = null }
                }
                if (!meetOk) Text(stringResource(R.string.sheet_new_meet_needs_google), style = PlaitType.caption, color = plait.muted)
                if (callMode == 1 && meetOk) Text(stringResource(R.string.sheet_new_meet_made_on_save), style = PlaitType.caption, color = plait.muted)
                if (callMode == 2) {
                    Field(linkText, { linkText = it; error = null }, stringResource(R.string.sheet_new_link_hint), keyboard = KeyboardType.Uri)
                    (CallLinkInput.parse(linkText) as? CallLinkInput.Parsed.Ok)?.let {
                        Text(it.provider?.let { p -> stringResource(R.string.sheet_edit_link_of, p.label) } ?: stringResource(R.string.sheet_edit_link_other), style = PlaitType.caption, color = plait.muted)
                    }
                }
                Field(place, { place = it }, stringResource(R.string.sheet_new_place_hint))
                Field(notes, { notes = it }, stringResource(R.string.common_notes), capitalize = true, singleLine = false)

                // Where it is saved
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(plait.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.sheet_new_saved_to_label), style = PlaitType.monoSmall, color = plait.muted)
                    Text(
                        dest?.let { Bidi.isolate(it.name) + " · " + Bidi.isolate(it.account) } ?: stringResource(if (DeviceCalendar.hasPermission(context)) R.string.sheet_new_local_only else R.string.sheet_new_allow_to_choose),
                        style = PlaitType.bodyStrong, color = plait.text,
                    )
                    Text(
                        if (dest != null && dest.accountType != "LOCAL") stringResource(R.string.sheet_new_syncs_to, dest.source.label) else stringResource(R.string.sheet_new_stays_local),
                        style = PlaitType.caption, color = plait.muted,
                    )
                    PlaitButton(stringResource(R.string.sheet_new_change_hub), onOpenHub, style = ButtonStyle.Secondary, size = ButtonSize.Small)
                }

                error?.let { Text(it, style = PlaitType.caption, color = plait.signal) }
                doneNote?.let { Text(it, style = PlaitType.caption, color = plait.text) }
                if (doneNote != null) HubButton(stringResource(R.string.common_done), primary = true, onClick = { reset(); onDismiss() })
                else HubButton(stringResource(if (!canWrite) R.string.sheet_new_allow_saving else if (saving) R.string.sheet_new_saving else R.string.sheet_new_save), primary = true, onClick = save)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

private fun nextHalfHour(): Int {
    val now = LocalTime.now()
    val m = now.hour * 60 + now.minute
    return ((m / 30) + 1) * 30 % (24 * 60)
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, capitalize: Boolean = false, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        textStyle = PlaitType.body.copy(color = plait.text).userText(), cursorBrush = SolidColor(plait.accent),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = if (capitalize) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
        ),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(22.dp)).background(plait.surface).padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(hint, style = PlaitType.body, color = plait.muted, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
private fun Stepper(label: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(plait.surface).pressable(onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(label, style = PlaitType.section, color = plait.text)
    }
}

@Composable
private fun Pill(label: String, on: Boolean, onClick: () -> Unit) = PlaitChip(label, on, onClick)
