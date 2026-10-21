package app.plait.ui.screens

import app.plait.ui.plaitCard
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.FlowRow
import app.plait.data.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.plait.data.AccountFilter
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.graphics.graphicsLayer
import app.plait.ai.AiPrefs
import app.plait.data.Calendars
import app.plait.data.device.DeviceCalendar
import app.plait.data.device.DeviceWrite
import app.plait.data.google.AuthOutcome
import app.plait.data.google.GoogleAuth
import app.plait.data.ics.Feeds
import app.plait.ui.glass.GlassIconButton
import app.plait.ui.ButtonSize
import app.plait.ui.PlaitChip
import app.plait.ui.TextUtil
import app.plait.ui.ButtonStyle
import app.plait.ui.ConfirmDeleteButton
import app.plait.ui.PlaitButton
import app.plait.ui.pressable
import app.plait.ui.rise
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/** Asks for calendar access (read, and write for meetings made in Plait), then reads the calendars. */
@Composable
internal fun rememberCalendarAccess(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.READ_CALENDAR] == true || DeviceCalendar.hasPermission(context)) Calendars.refreshDevice(context)
        else Calendars.report("Plait needs calendar access to show your events. You can allow it in Settings.")
    }
    return {
        if (DeviceCalendar.hasPermission(context) && DeviceWrite.hasPermission(context)) Calendars.refreshDevice(context)
        else launcher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
}

/** Signs in to Google (once) and reads the calendar straight from Google. */
@Composable
internal fun rememberGoogleSync(): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val token = GoogleAuth.tokenFrom(context, result.data)
        if (token != null) scope.launch { Calendars.sync(context, token) }
        else Calendars.report("Calendar access wasn't granted.")
    }
    return {
        if (!Calendars.syncing) scope.launch {
            when (val r = GoogleAuth.authorize(context)) {
                is AuthOutcome.Token -> Calendars.sync(context, r.accessToken)
                is AuthOutcome.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(r.pendingIntent).build())
                is AuthOutcome.Failed -> Calendars.report(r.reason)
            }
        }
    }
}

@Composable
internal fun PlaitSwitch(on: Boolean, onToggle: () -> Unit, label: String) {
    if (app.plait.ui.glass.LocalGlassEnv.current.material) { app.plait.ui.material.MSwitch(on, onToggle, label); return }
    val x by animateDpAsState(if (on) 22.dp else 0.dp, label = "switch")
    Box(
        Modifier.size(width = 54.dp, height = 32.dp).clip(CircleShape)
            .background(if (on) plait.accent else plait.surfaceHigh)
            .pressable(onToggle, Role.Switch).padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(26.dp).background(if (on) plait.onAccent else plait.muted, CircleShape))
    }
}

/**
 * The hub: every account and calendar Plait can see, with switches to include or leave out each
 * one, ways to add more (accounts on the phone, calendar links, files), and where meetings made in
 * Plait are saved. Android's own sync carries those back to the account.
 */
@Composable
fun HubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    remember(context) { Calendars.load(context); app.plait.data.Profile.load(context) }
    val calendarAccess = rememberCalendarAccess()
    val googleSync = rememberGoogleSync()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Coming back from Android's "add account" screen: read the calendars again.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME && DeviceCalendar.hasPermission(context)) Calendars.refreshDevice(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    var askRestart by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    var fileNote by remember { mutableStateOf<String?>(null) }
    val addLink: () -> Unit = {
        if (url.isNotBlank() && !Calendars.syncing) scope.launch {
            val problem = Calendars.addFeed(context, url)
            linkError = problem
            if (problem == null) url = ""
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "calendar.ics"
            val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            fileNote = if (text == null) "Couldn't read that file." else Calendars.importFile(context, name, text) ?: "Imported $name."
        }
    }
    fun addAccount(types: Array<String>?) {
        val intent = Intent(Settings.ACTION_ADD_ACCOUNT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (types != null) intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, types)
        runCatching { context.startActivity(intent) }.onFailure { Calendars.report("Couldn't open the phone's account settings.") }
    }

    var includeMedia by remember { mutableStateOf(true) }
    var backupNote by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val runImport: (Uri) -> Unit = { uri ->
        scope.launch {
            backupBusy = true
            val result = runCatching { withContext(Dispatchers.IO) { BackupManager.import(context, uri, passphrase) } }
            backupNote = result.fold({ pendingImport = null; BackupManager.summary(it, "Added") }, {
                if (it is BackupManager.NeedsPassphrase) pendingImport = uri
                it.message ?: "Couldn't read that backup."
            })
            backupBusy = false
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            backupBusy = true
            backupNote = runCatching { withContext(Dispatchers.IO) { BackupManager.export(context, uri, includeMedia, passphrase) } }
                .fold({ BackupManager.summary(it, "Saved a backup with") }, { it.message ?: "Couldn't save the backup." })
            backupBusy = false
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) runImport(uri) }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            backupBusy = true
            backupNote = runCatching { withContext(Dispatchers.IO) { app.plait.share.AllNotesExport.write(context, uri) } }
                .fold({ "Saved $it meeting${if (it == 1) "" else "s"} as Markdown files, readable on any computer." }, { it.message ?: "Couldn't save the files." })
            backupBusy = false
        }
    }
    val cals = Calendars.deviceCals
    val hasAccess = DeviceCalendar.hasPermission(context)
    val canWrite = DeviceWrite.hasPermission(context)
    val byAccount = cals.groupBy { it.account to it.accountType }
    val dest = Calendars.destination()

    val accountList = AccountFilter.accounts(cals)
    val accountsSummary = if (accountList.isEmpty()) (if (hasAccess) "None found yet" else "Needs calendar access")
        else (if (Calendars.accountsChosen) accountList.count { Calendars.accountEnabled(it.name) } else accountList.size).toString() + " of " + accountList.size + " shown"
    val sendSummary = dest?.let { TextUtil.shortenAccount(it.name + " · " + it.account, 34) } ?: "Not chosen yet"
    val aiSummary = AiPrefs.let { p ->
        val text = when (p.textChoice) {
            AiPrefs.TextChoice.Auto -> "Automatic"
            AiPrefs.TextChoice.Nano -> "Gemini Nano"
            AiPrefs.TextChoice.Local -> app.plait.ai.ModelCatalog.byId(p.textModelId)?.title ?: "Downloaded model"
            AiPrefs.TextChoice.Rules -> "Rules only"
        }
        "Writing: $text · Voice: " + (app.plait.ai.ModelCatalog.byId(p.speechModelId)?.title ?: "none yet")
    }
    val remindersSummary = if (app.plait.reminder.ReminderPrefs.before(context)) "${app.plait.reminder.ReminderPrefs.leadMin(context)} min before" + (if (app.plait.reminder.ReminderPrefs.nudge(context)) " · evening reminder" else "") else "Off"
    val appearanceSummary = (if (app.plait.ui.theme.Appearance.style == app.plait.ui.theme.UiStyle.Glass) "Liquid Glass" else "Material 3 Expressive") + (if (!app.plait.ui.theme.Appearance.glass) " · no glass effects" else "") + (if (app.plait.ui.theme.Appearance.calm) " · calm" else "")
    val youSummary = app.plait.data.Profile.name.ifBlank { "Name, backup, import" }
    val syncSummary = if (app.plait.data.sync.SyncEngine.configured(context)) "On" + app.plait.data.sync.SyncEngine.status.let { if (it.isBlank()) "" else " · $it" } else "Off"

    val section = app.plait.ui.HomeState.hubSection
    val goBack: () -> Unit = { if (section != null) app.plait.ui.HomeState.hubSection = null else onBack() }
    androidx.activity.compose.BackHandler(section != null) { app.plait.ui.HomeState.hubSection = null }
    // Opening a page slides it in from the right, going back brings the list in from the left.
    val enter = remember(section) { androidx.compose.animation.core.Animatable(0f) }
    val calm = app.plait.ui.glass.LocalGlassEnv.current.reduceMotion
    val settingsSpring = app.plait.ui.Motion.spatial<Float>()
    androidx.compose.runtime.LaunchedEffect(section) { enter.animateTo(1f, if (calm) androidx.compose.animation.core.tween(90) else settingsSpring) }
    val slideFrom = if (section != null) 1f else -1f

    Box(Modifier.fillMaxSize().background(plait.ground)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AmbientBackground()
            LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxHeight().align(Alignment.TopCenter)
                    .graphicsLayer { val p = enter.value; translationX = (1f - p) * 72.dp.toPx() * slideFrom * (if (calm) 0f else 1f); alpha = p.coerceIn(0f, 1f) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = statusTop + 76.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (section == null) item {
                    Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SETTINGS", style = PlaitType.monoLabel, color = plait.muted)
                        Text("Settings", style = PlaitType.title, color = plait.text)
                        Text(
                            if (Calendars.connected) countLabel(Calendars.events.size, "event") + " from " +
                                countLabel(cals.count { it.id !in Calendars.hidden } + Calendars.feeds.size + (if (Calendars.googleOn) 1 else 0), "calendar") +
                                (if (Calendars.syncing) " · syncing…" else "")
                            else "Nothing connected yet. Pick a way below.",
                            style = PlaitType.body, color = plait.muted,
                        )
                        Calendars.message?.let { Text(it, style = PlaitType.caption, color = plait.signal) }
                    }
                }

                if (section == null && (!hasAccess || !canWrite)) {
                    item {
                        HubCard {
                            Text(if (!hasAccess) "Allow calendar access" else "Allow saving to your calendar", style = PlaitType.bodyStrong, color = plait.text)
                            Text(
                                "Plait reads the calendars already on this phone, and saves meetings you create in Plait to the one you choose. Nothing goes through a Plait server.",
                                style = PlaitType.body, color = plait.muted,
                            )
                            HubButton(if (!hasAccess) "Allow access" else "Allow saving", primary = true, onClick = calendarAccess)
                        }
                    }
                }

                fold(section, "accounts", "Accounts to show", accountsSummary) {
                val accounts = AccountFilter.accounts(cals)
                if (accounts.isNotEmpty()) {
                    item {
                        FoldNote(if (Calendars.accountsChosen) "Only the accounts switched on appear in Plait. The rest stay out of your agenda."
                            else "Everything on this phone is showing. Switch off the accounts you don't want here.",
                        )
                    }
                    accounts.forEachIndexed { i, acct ->
                        item(key = "acct-${acct.name}-${acct.type}") {
                            val on = Calendars.accountEnabled(acct.name)
                            var open by remember { mutableStateOf(false) }
                            HubCard(Modifier.rise(i + 1)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        Modifier.size(40.dp).background(acct.source.color().copy(alpha = 0.22f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(acct.source.label.take(1), style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = acct.source.color()) }
                                    Column(Modifier.weight(1f)) {
                                        Text(TextUtil.shortenAccount(acct.name, 30), style = PlaitType.bodyStrong, color = plait.text, maxLines = 1, softWrap = false)
                                        Text(
                                            acct.source.label + " · " + acct.calendars.size + (if (acct.calendars.size == 1) " calendar" else " calendars"),
                                            style = PlaitType.caption, color = plait.muted, maxLines = 1,
                                        )
                                    }
                                    PlaitSwitch(on, { Calendars.setAccountEnabled(context, acct.name, !on) }, "Show ${acct.name}")
                                }
                                if (on) {
                                    PlaitButton(if (open) "Hide calendars" else "Choose calendars", { open = !open }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                                    if (open) acct.calendars.forEach { cal ->
                                        val chosen = dest?.id == cal.id
                                        Column(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(plait.surfaceHigh.copy(alpha = 0.6f)).padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(Modifier.size(12.dp).background(Color(cal.color or 0xFF000000.toInt()), CircleShape))
                                                Text(cal.name, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Medium), color = plait.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                PlaitSwitch(cal.id !in Calendars.hidden, { Calendars.setCalendarVisible(context, cal.id, cal.id in Calendars.hidden) }, "Show ${cal.name}")
                                            }
                                            if (cal.writable) {
                                                PlaitChip(
                                                    if (chosen) "✓  New meetings are saved here" else "Save new meetings here",
                                                    chosen, { Calendars.setDestination(context, cal.id) },
                                                )
                                            } else Text("Read only", style = PlaitType.caption, color = plait.muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (hasAccess) {
                    item { Text("No calendars were found on this phone yet. Add an account below.", style = PlaitType.body, color = plait.muted) }
                }

                }

                fold(section, "add", "Add an account", "Google, Outlook, Exchange") {
                item { FoldNote("Accounts are added in Android, so Plait never sees a password. Their calendars appear here by themselves.") }
                item {
                    HubCard {
                        HubButton("Add a Google account", primary = true) { addAccount(arrayOf("com.google")) }
                        HubButton("Add Outlook, Microsoft 365 or Exchange") { addAccount(null) }
                        Text(
                            "For Outlook: install the Outlook app and sign in there, or choose Exchange in the list Android shows. " +
                                "For iCloud: use a calendar link below (iCloud has no sign-in Android can use).",
                            style = PlaitType.caption, color = plait.muted,
                        )
                    }
                }

                }

                fold(section, "import", "Import a calendar", "By link or from a file") {
                item { FoldNote("A link keeps itself up to date. A file is a one-time copy.") }
                item {
                    HubCard {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(plait.surfaceHigh).padding(start = 16.dp, end = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BasicTextField(
                                value = url, onValueChange = { url = it; linkError = null }, singleLine = true,
                                textStyle = PlaitType.body.copy(color = plait.text), cursorBrush = SolidColor(plait.accent),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addLink() }),
                                modifier = Modifier.weight(1f),
                                decorationBox = { inner ->
                                    Box(Modifier.height(50.dp), contentAlignment = Alignment.CenterStart) {
                                        if (url.isEmpty()) Text("Paste a calendar link (https:// or webcal://)", style = PlaitType.body, color = plait.muted, maxLines = 1)
                                        inner()
                                    }
                                },
                            )
                            PlaitButton(if (Calendars.syncing) "Adding…" else "Add", addLink, style = ButtonStyle.Primary, size = ButtonSize.Small)
                        }
                        linkError?.let { Text(it, style = PlaitType.caption, color = plait.signal) }
                        HubButton("Import a .ics file") { pickFile.launch(arrayOf("text/calendar", "application/octet-stream", "text/plain", "*/*")) }
                        fileNote?.let { Text(it, style = PlaitType.caption, color = plait.muted) }
                        Calendars.feeds.forEach { feed ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(10.dp).background(feed.source.color(), CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Text(feed.name, style = PlaitType.body, color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (feed.url.startsWith("file://")) "File on this phone" else feed.source.label + " · " + Feeds.fallbackName(feed.url), style = PlaitType.caption, color = plait.muted, maxLines = 1)
                                }
                                ConfirmDeleteButton({ Calendars.removeFeed(context, feed) }, label = "Remove")
                            }
                        }
                        Text(
                            "Outlook: Outlook on the web → Settings → Calendar → Shared calendars → Publish → copy the ICS link. " +
                                "iCloud: Calendar app → share → Public Calendar. Google: calendar Settings → Integrate calendar → Secret address in iCal format. " +
                                "Anyone with a published link can read that calendar.",
                            style = PlaitType.caption, color = plait.muted,
                        )
                    }
                }

                }

                fold(section, "send", "Where new meetings are saved", sendSummary) {
                item { FoldNote("What you create in Plait goes to the same place as everything else.") }
                item {
                    HubCard {
                        Text(
                            if (dest != null) "New meetings are saved to ${dest.name} (${dest.account}). Android syncs them to that account, so they show up in " +
                                (when (dest.source) { app.plait.data.Source.Google -> "Google Calendar"; app.plait.data.Source.Outlook -> "Outlook"; app.plait.data.Source.ICloud -> "iCloud"; else -> "your calendar app" }) + " and invite your guests."
                            else if (hasAccess) "No calendar on this phone can take new meetings yet. Add an account above, or keep them on this phone only."
                            else "Allow calendar access first.",
                            style = PlaitType.body, color = plait.text,
                        )
                        if (dest == null && hasAccess && canWrite) {
                            HubButton("Create a calendar on this phone only") {
                                val id = DeviceWrite.ensureLocalCalendar(context)
                                if (id != null) { Calendars.setDestination(context, id); Calendars.refreshDevice(context) }
                                else Calendars.report("Couldn't create a calendar on this phone.")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Add my notes to the event", style = PlaitType.bodyStrong, color = plait.text)
                                Text("After a voice note, the summary and follow-ups are written into the calendar event's description.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(Calendars.syncNotes, { Calendars.setSyncNotes(context, !Calendars.syncNotes) }, "Add notes to calendar events")
                        }
                    }
                }

                }

                fold(section, "ai", "AI on this phone", aiSummary) {
                item {
                    var light by remember { mutableStateOf(app.plait.ai.Ai.lightAuto(context)) }
                    HubCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Lighter model for automatic writing", style = PlaitType.bodyStrong, color = plait.text)
                                Text("Day summaries, prep and About use the smallest downloaded model, so a big one does not slow the phone. Questions and summaries you ask for still use your chosen model.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(light, { light = !light; app.plait.ai.Ai.setLightAuto(context, light) }, "Lighter model for automatic writing")
                        }
                    }
                }
                aiSection()

                }

                fold(section, "reminders", "Reminders and widget", remindersSummary) {
                item { FoldNote("Alerts come from the phone's alarm clock, so they work with Plait closed.") }
                item {
                    HubCard {
                        var before by remember { mutableStateOf(app.plait.reminder.ReminderPrefs.before(context)) }
                        var lead by remember { mutableStateOf(app.plait.reminder.ReminderPrefs.leadMin(context)) }
                        var nudge by remember { mutableStateOf(app.plait.reminder.ReminderPrefs.nudge(context)) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Before each meeting", style = PlaitType.bodyStrong, color = plait.text)
                                Text("A heads-up with a line about what it is for, and a Join button.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(before, { before = !before; app.plait.reminder.ReminderPrefs.setBefore(context, before) }, "Reminders before meetings")
                        }
                        if (before) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 30).forEach { m -> PlaitChip("$m min before", lead == m, { lead = m; app.plait.reminder.ReminderPrefs.setLead(context, m) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Evening note reminder", style = PlaitType.bodyStrong, color = plait.text)
                                Text("At 6:00 PM, if finished meetings have no notes.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(nudge, { nudge = !nudge; app.plait.reminder.ReminderPrefs.setNudge(context, nudge) }, "Evening note reminder")
                        }
                        Text("Widget: press and hold the home screen, choose Widgets, then Plait. It shows the next meeting with a live countdown and a Join button.", style = PlaitType.caption, color = plait.muted)
                    }
                }

                }

                fold(section, "appearance", "Appearance", appearanceSummary) {
                item {
                    HubCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Pure black in dark mode", style = PlaitType.bodyStrong, color = plait.text)
                                Text("True black background for OLED screens: deeper contrast, and the pixels switch off to save battery.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(app.plait.ui.theme.Appearance.amoled, { app.plait.ui.theme.Appearance.setAmoled(context, !app.plait.ui.theme.Appearance.amoled) }, "Pure black in dark mode")
                        }
                    }
                }
                item {
                    HubCard {
                        Text("Style", style = PlaitType.bodyStrong, color = plait.text)
                        Text("Liquid Glass is translucent and refracts what is behind it. Material 3 Expressive is solid, tonal and bouncier.", style = PlaitType.caption, color = plait.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlaitChip("Liquid Glass", app.plait.ui.theme.Appearance.style == app.plait.ui.theme.UiStyle.Glass, { app.plait.ui.theme.Appearance.setStyle(context, app.plait.ui.theme.UiStyle.Glass); askRestart = app.plait.ui.theme.Appearance.style != app.plait.ui.theme.Appearance.activeStyle })
                            PlaitChip("Material 3 Expressive", app.plait.ui.theme.Appearance.style == app.plait.ui.theme.UiStyle.Material, { app.plait.ui.theme.Appearance.setStyle(context, app.plait.ui.theme.UiStyle.Material); askRestart = app.plait.ui.theme.Appearance.style != app.plait.ui.theme.Appearance.activeStyle })
                        }
                        if (app.plait.ui.theme.Appearance.style != app.plait.ui.theme.Appearance.activeStyle) {
                            Text("Restart Plait to finish switching the whole app to this style.", style = PlaitType.caption, color = plait.signal)
                            PlaitButton("Restart now", { app.plait.ui.material.restartApp(context) }, size = ButtonSize.Small, style = ButtonStyle.Primary)
                        }
                    }
                }
                item {
                    HubCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Glass effects", style = PlaitType.bodyStrong, color = plait.text)
                                Text("Blur and refraction. Turn off for plain surfaces, which is lighter on mid-range phones.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(app.plait.ui.theme.Appearance.glass, { app.plait.ui.theme.Appearance.setGlass(context, !app.plait.ui.theme.Appearance.glass) }, "Glass effects")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Reduce animations", style = PlaitType.bodyStrong, color = plait.text)
                                Text("No looping skies, no bounce. Pages still slide a little so you can see where you went.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(app.plait.ui.theme.Appearance.calm, { app.plait.ui.theme.Appearance.setCalm(context, !app.plait.ui.theme.Appearance.calm) }, "Reduce animations")
                        }
                    }
                }

                }

                fold(section, "you", "You and backups", youSummary) {
                item { FoldNote("Your name is used in greetings. A backup moves your notes and settings to another phone.") }
                item {
                    HubCard {
                        var name by remember { mutableStateOf(app.plait.data.Profile.name) }
                        Text("Your name", style = PlaitType.monoSmall, color = plait.muted)
                        BasicTextField(
                            value = name, onValueChange = { name = it; app.plait.data.Profile.setName(context, it) }, singleLine = true,
                            textStyle = PlaitType.body.copy(color = plait.text), cursorBrush = SolidColor(plait.accent),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(22.dp)).background(plait.surfaceHigh).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                    if (name.isEmpty()) Text("What should Plait call you?", style = PlaitType.body, color = plait.muted)
                                    inner()
                                }
                            },
                        )
                        Text("This phone: ${app.plait.data.Profile.device}", style = PlaitType.caption, color = plait.muted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Include recordings and photos", style = PlaitType.bodyStrong, color = plait.text)
                                Text("Makes the file larger, but the audio and pictures come with it.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(includeMedia, { includeMedia = !includeMedia }, "Include recordings and photos")
                        }
                        Text("Passphrase (optional)", style = PlaitType.monoSmall, color = plait.muted)
                        BasicTextField(
                            value = passphrase, onValueChange = { passphrase = it }, singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            textStyle = PlaitType.body.copy(color = plait.text), cursorBrush = SolidColor(plait.accent),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(22.dp)).background(plait.surfaceHigh).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                    if (passphrase.isEmpty()) Text("Locks the file. Leave empty for none.", style = PlaitType.body, color = plait.muted)
                                    inner()
                                }
                            },
                        )
                        Text("With a passphrase the file is encrypted (AES-256). Nobody, including us, can recover it if you forget it.", style = PlaitType.caption, color = plait.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlaitButton(
                                if (backupBusy) "Working…" else "Export backup",
                                { exportFile.launch("plait-backup-" + java.time.LocalDate.now() + ".zip") },
                                style = ButtonStyle.Primary, enabled = !backupBusy,
                            )
                            PlaitButton("Notes as Markdown for a computer", { exportMarkdown.launch("plait-notes-" + java.time.LocalDate.now() + ".zip") }, style = ButtonStyle.Quiet, enabled = !backupBusy)
                            PlaitButton("Import backup", { importFile.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !backupBusy)
                            pendingImport?.let { u -> PlaitButton("Try again with this passphrase", { runImport(u) }, style = ButtonStyle.Primary, enabled = !backupBusy && passphrase.isNotEmpty()) }
                        }
                        backupNote?.let { Text(it, style = PlaitType.caption, color = plait.text) }
                        Text(
                            "Importing only adds what this phone doesn't have; nothing here is replaced. Without a passphrase the file isn't encrypted, so keep it private. Syncing live between phones will come later.",
                            style = PlaitType.caption, color = plait.muted,
                        )
                    }
                }

                }

                fold(section, "sync", "Sync between your phones", syncSummary) {
                item { FoldNote("No Plait server. Pick a folder that a cloud app you already use keeps in step on your phones, such as Google Drive, Dropbox, Nextcloud or Syncthing. Plait puts only sealed files in it.") }
                item { SyncCard() }

                }

                fold(section, "diag", "Check this phone", "Self-check, log, frame rate") {
                item { FoldNote("For testing on a real device. Everything stays on the phone unless you share it.") }
                item {
                    HubCard {
                        var checks by remember { mutableStateOf<List<app.plait.diag.Check>?>(null) }
                        var showLog by remember { mutableStateOf(false) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlaitButton("Run self-check", { scope.launch { checks = app.plait.diag.SelfCheck.run(context) } }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                            PlaitButton(if (showLog) "Hide log" else "Show log", { showLog = !showLog }, size = ButtonSize.Small)
                            PlaitButton(if (app.plait.diag.FrameMonitor.enabled) "Hide frame stats" else "Show frame stats", { app.plait.diag.FrameMonitor.turn(!app.plait.diag.FrameMonitor.enabled) }, size = ButtonSize.Small, style = ButtonStyle.Quiet)
                        }
                        checks?.forEach { c ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                                Text(if (c.ok) "✓" else "✗", style = PlaitType.bodyStrong, color = if (c.ok) plait.accent else plait.signal)
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Medium), color = plait.text)
                                    Text(c.detail, style = PlaitType.caption, color = plait.muted)
                                }
                            }
                        }
                        if (showLog) {
                            val log = remember(showLog) { app.plait.diag.Diag.read().ifBlank { "Nothing logged yet." } }
                            Text(log.takeLast(2500), style = PlaitType.monoSmall, color = plait.text.copy(alpha = 0.8f))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlaitButton("Share log", {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, app.plait.diag.Diag.read())
                                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share log").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }, size = ButtonSize.Small)
                                PlaitButton("Clear", { app.plait.diag.Diag.clear(); showLog = false }, size = ButtonSize.Small, style = ButtonStyle.Quiet)
                            }
                        }
                    }
                }

                }

                fold(section, "updates", "Updates and help", "Plait ${app.plait.data.update.Updater.currentVersion(context)}") {
                item { FoldNote("Plait checks GitHub for a new version twice a day at most and installs it when you say so. The check sends nothing about you.") }
                item {
                    HubCard {
                        val st = app.plait.data.update.Updater.state
                        var showUpdate by remember { mutableStateOf(false) }
                        var showNews by remember { mutableStateOf(false) }
                        Text("Plait ${app.plait.data.update.Updater.currentVersion(context)}", style = PlaitType.bodyStrong, color = plait.text)
                        Text(
                            when (st) {
                                is app.plait.data.update.UpdateState.Checking -> "Checking…"
                                is app.plait.data.update.UpdateState.UpToDate -> "You have the latest version."
                                is app.plait.data.update.UpdateState.Available -> "Plait ${st.info.version} is ready to install."
                                is app.plait.data.update.UpdateState.Downloading -> "Downloading Plait ${st.info.version}…"
                                is app.plait.data.update.UpdateState.NeedsPermission -> "Waiting for permission to install."
                                is app.plait.data.update.UpdateState.Installing -> "Installing…"
                                is app.plait.data.update.UpdateState.Failed -> st.reason
                                else -> "Tap Check to look for a new version."
                            },
                            style = PlaitType.caption, color = plait.muted,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val waiting = st is app.plait.data.update.UpdateState.Available || st is app.plait.data.update.UpdateState.Downloading ||
                                st is app.plait.data.update.UpdateState.NeedsPermission || (st is app.plait.data.update.UpdateState.Failed && st.info != null)
                            if (waiting) PlaitButton("Update", { showUpdate = true }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                            else PlaitButton("Check for updates", { app.plait.data.update.Updater.check(context) }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                            PlaitButton("What's new", { showNews = true }, size = ButtonSize.Small)
                            PlaitButton("Take the tour", { app.plait.data.update.WhatsNew.show = app.plait.data.update.WhatsNew.Show.Tour }, size = ButtonSize.Small)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            var auto by remember { mutableStateOf(app.plait.data.update.Updater.autoCheck(context)) }
                            Column(Modifier.weight(1f)) {
                                Text("Check for updates by itself", style = PlaitType.bodyStrong, color = plait.text)
                                Text("When Plait opens, at most twice a day.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(auto, { auto = !auto; app.plait.data.update.Updater.setAutoCheck(context, auto) }, "Check for updates by itself")
                        }
                        if (showUpdate) UpdatePrompt(forceOpen = true) { showUpdate = false }
                        if (showNews) {
                            val news = remember { app.plait.data.update.WhatsNew.current(context) }
                            if (news != null) WhatsNewDialog(news) { showNews = false }
                            else DetailDialog("What's new", { showNews = false }) { Text("The notes for this version aren't in this build.", style = PlaitType.body, color = plait.text) }
                        }
                    }
                }
                }

                fold(section, "adv", "Advanced", "Read Google directly") {
                item { FoldNote("Only if you set up Google Cloud yourself (see the README). The steps above need none of this.") }
                item {
                    HubCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Read Google directly", style = PlaitType.bodyStrong, color = plait.text)
                                Text("Includes Meet links. Read only.", style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(Calendars.googleOn, { if (Calendars.googleOn) Calendars.disconnectGoogle(context) else googleSync() }, "Read Google directly")
                        }
                    }
                }
                }

            }
        }
        app.plait.ui.TopFade(Modifier.align(Alignment.TopCenter))
        Box(Modifier.statusBarsPaddingCompat().padding(start = 18.dp, top = 12.dp)) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", goBack, backdrop)
        }
        if (askRestart) DetailDialog("Restart to apply", { askRestart = false }) {
            Text(
                "Plait builds the whole interface in one style, so it needs to start again to switch to " +
                    (if (app.plait.ui.theme.Appearance.style == app.plait.ui.theme.UiStyle.Material) "Material 3 Expressive" else "Liquid Glass") + ".",
                style = PlaitType.body, color = plait.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaitButton("Restart now", { app.plait.ui.material.restartApp(context) }, size = ButtonSize.Small, style = ButtonStyle.Primary)
                PlaitButton("Later", { askRestart = false }, size = ButtonSize.Small, style = ButtonStyle.Quiet)
            }
        }
    }
}

@Composable
private fun Modifier.statusBarsPaddingCompat(): Modifier = this.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())

/** "1 event", "5 events". */
internal fun countLabel(n: Int, noun: String): String = "$n $noun" + if (n == 1) "" else "s"

@Composable
internal fun SectionTitle(title: String, detail: String) {
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = PlaitType.section, color = plait.text)
        Text(detail, style = PlaitType.caption, color = plait.muted)
    }
}

@Composable
internal fun HubCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().plaitCard(RoundedCornerShape(28.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun HubButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    PlaitButton(label, onClick, style = if (primary) ButtonStyle.Primary else ButtonStyle.Secondary)
}


@Composable
private fun SyncCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = app.plait.data.sync.SyncEngine
    var pass by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<Uri?>(null) }
    var media by remember { mutableStateOf(engine.includeMedia(context)) }
    var connected by remember { mutableStateOf(engine.configured(context)) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) folder = uri }
    HubCard {
        if (connected) {
            Text("Syncing" + (engine.folderName?.let { " with “$it”" } ?: ""), style = PlaitType.bodyStrong, color = plait.text)
            if (engine.status.isNotBlank()) Text(engine.status, style = PlaitType.caption, color = plait.accent)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Include recordings and photos", style = PlaitType.bodyStrong, color = plait.text)
                    Text("Bigger files, but audio and pictures follow you. Otherwise only text syncs.", style = PlaitType.caption, color = plait.muted)
                }
                PlaitSwitch(media, { media = !media; engine.setIncludeMedia(context, media) }, "Sync recordings and photos")
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaitButton(if (engine.running) "Syncing…" else "Sync now", { scope.launch { engine.run(context) } }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = !engine.running)
                PlaitButton("Stop syncing", { engine.disconnect(context); connected = false }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
            Text("Plait syncs when it opens and after you save a note. Deleting something on one phone removes it on the others.", style = PlaitType.caption, color = plait.muted)
        } else {
            Text("Set up on every phone with the same folder and the same passphrase. The passphrase encrypts the files; if you forget it they can't be opened.", style = PlaitType.caption, color = plait.muted)
            PlaitButton(if (folder == null) "Choose the sync folder" else "Folder chosen ✓", { pick.launch(null) }, size = ButtonSize.Small)
            androidx.compose.foundation.text.BasicTextField(
                value = pass, onValueChange = { pass = it }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                textStyle = PlaitType.body.copy(color = plait.text), cursorBrush = androidx.compose.ui.graphics.SolidColor(plait.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(22.dp)).background(plait.surfaceHigh).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        if (pass.isEmpty()) Text("Sync passphrase", style = PlaitType.body, color = plait.muted)
                        inner()
                    }
                },
            )
            PlaitButton("Start syncing", {
                folder?.let { engine.connect(context, it, pass); connected = true; scope.launch { engine.run(context) } }
            }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = folder != null && pass.length >= 6)
            if (pass.isNotEmpty() && pass.length < 6) Text("Use at least 6 characters.", style = PlaitType.caption, color = plait.signal)
        }
    }
}


/**
 * One settings section. In the list it is a single row; tapping it opens the section on its own page (and Back returns to
 * the list). Only the open section's items are built.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.fold(current: String?, id: String, title: String, summary: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    if (current == null) {
        item(key = "fold-$id", contentType = "fold") {
            FoldHeader(title, summary) { app.plait.ui.HomeState.hubSection = id }
        }
    } else if (current == id) {
        item(key = "page-$id") {
            Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SETTINGS", style = PlaitType.monoLabel, color = plait.muted)
                Text(title, style = PlaitType.title.copy(fontSize = 36.sp, lineHeight = 38.sp), color = plait.text)
                Text(summary, style = PlaitType.body, color = plait.muted)
            }
        }
        content()
    }
}

@Composable
private fun FoldHeader(title: String, summary: String, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(26.dp))
            .pressable(onOpen, Role.Button, lift = 3.dp).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PlaitType.section.copy(fontSize = 19.sp, lineHeight = 23.sp), color = plait.text)
            Text(summary, style = PlaitType.caption, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        androidx.compose.material3.Icon(
            Icons.Filled.ChevronRight, "Open $title", tint = plait.muted,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** The explanation at the top of an open section. */
@Composable
internal fun FoldNote(text: String) {
    Text(text, style = PlaitType.caption, color = plait.muted, modifier = Modifier.padding(horizontal = 4.dp))
}
