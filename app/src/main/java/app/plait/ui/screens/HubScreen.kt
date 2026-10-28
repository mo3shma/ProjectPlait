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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import app.plait.ui.BadgeKind
import app.plait.ui.GlassTone
import app.plait.ui.PlaitBadge
import app.plait.ui.ToneIcon
import app.plait.ui.ToneStyles
import app.plait.ui.lighter
import app.plait.ui.toneGlass
import app.plait.ui.toneSurface
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
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import app.plait.i18n.userText
import androidx.compose.ui.res.pluralStringResource
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
import app.plait.data.backup.AutoBackup
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
        else Calendars.report(Txt.s(R.string.settings_calendar_access_needed))
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
        else Calendars.report(Txt.s(R.string.settings_google_access_denied))
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
    val tone = LocalHubLook.current.tone
    val dark = app.plait.ui.theme.LocalIsDark.current
    val flat = app.plait.ui.glass.LocalGlassEnv.current.solid
    val track = ToneStyles.resolve(tone, dark, selected = on, rimStrength = if (flat) ToneStyles.FLAT_RIM else 1f)
    Box(
        Modifier.size(width = 54.dp, height = 32.dp).toneGlass(CircleShape, if (on) track.copy(fill = tone.deep.copy(alpha = if (dark) 0.75f else 0.85f)) else track.lighter(0.8f))
            .pressable(onToggle, Role.Switch).padding(3.dp),
    ) {
        Box(Modifier.offset(x = x).size(24.dp).background(if (on) Color.White else track.ink.copy(alpha = 0.55f), CircleShape))
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
    remember(context) { Calendars.load(context); app.plait.data.Profile.load(context); app.plait.data.week.WorkWeek.load(context); app.plait.widget.WidgetPrefs.load(context) }
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
            fileNote = if (text == null) Txt.s(R.string.settings_import_file_unreadable) else Calendars.importFile(context, name, text) ?: Txt.s(R.string.settings_import_file_done, Bidi.isolate(name))
        }
    }
    fun addAccount(types: Array<String>?) {
        val intent = Intent(Settings.ACTION_ADD_ACCOUNT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (types != null) intent.putExtra(Settings.EXTRA_ACCOUNT_TYPES, types)
        runCatching { context.startActivity(intent) }.onFailure { Calendars.report(Txt.s(R.string.settings_add_account_failed)) }
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
            backupNote = result.fold({ pendingImport = null; backupSummary(it, saved = false) }, {
                if (it is BackupManager.NeedsPassphrase) pendingImport = uri
                it.message ?: Txt.s(R.string.settings_backup_unreadable)
            })
            backupBusy = false
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            backupBusy = true
            backupNote = runCatching { withContext(Dispatchers.IO) { BackupManager.export(context, uri, includeMedia, passphrase) } }
                .fold({ backupSummary(it, saved = true) }, { it.message ?: Txt.s(R.string.settings_backup_save_failed) })
            backupBusy = false
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) runImport(uri) }
    val exportMarkdown = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            backupBusy = true
            backupNote = runCatching { withContext(Dispatchers.IO) { app.plait.share.AllNotesExport.write(context, uri) } }
                .fold({ Txt.plural(R.plurals.settings_markdown_saved, it) }, { it.message ?: Txt.s(R.string.settings_markdown_save_failed) })
            backupBusy = false
        }
    }
    val cals = Calendars.deviceCals
    val hasAccess = DeviceCalendar.hasPermission(context)
    val canWrite = DeviceWrite.hasPermission(context)
    val byAccount = cals.groupBy { it.account to it.accountType }
    val dest = Calendars.destination()

    val accountList = AccountFilter.accounts(cals)
    val accountsSummary = if (accountList.isEmpty()) stringResource(if (hasAccess) R.string.settings_accounts_none else R.string.settings_accounts_needs_access)
        else stringResource(R.string.settings_accounts_shown, if (Calendars.accountsChosen) accountList.count { Calendars.accountEnabled(it.name) } else accountList.size, accountList.size)
    val sendSummary = dest?.let { Bidi.isolate(TextUtil.shortenAccount(it.name + " · " + it.account, 34)) } ?: stringResource(R.string.settings_send_not_chosen)
    val aiSummary = AiPrefs.let { p ->
        val text = when (p.textChoice) {
            AiPrefs.TextChoice.Auto -> stringResource(R.string.common_automatic)
            AiPrefs.TextChoice.Nano -> stringResource(R.string.settings_ai_gemini_nano)
            AiPrefs.TextChoice.Local -> app.plait.ai.ModelCatalog.byId(p.textModelId)?.title ?: stringResource(R.string.settings_ai_downloaded_model)
            AiPrefs.TextChoice.Rules -> stringResource(R.string.settings_ai_rules_only)
        }
        aiSummaryText(text, app.plait.ai.ModelCatalog.byId(p.speechModelId)?.title)
    }
    val aiBadge = aiRecommendedBadge(
        app.plait.ai.ModelCenter.state(QWEN) is app.plait.ai.ModelState.Installed || app.plait.ai.ModelCenter.isInstalled(context, QWEN),
        app.plait.ai.ModelCenter.state(TURBO) is app.plait.ai.ModelState.Installed || app.plait.ai.ModelCenter.isInstalled(context, TURBO),
    )
    val remindersSummary = remindersSummaryText(app.plait.reminder.ReminderPrefs.before(context), app.plait.reminder.ReminderPrefs.leadMin(context), app.plait.reminder.ReminderPrefs.nudge(context))
    val styleGlass = stringResource(R.string.settings_style_glass)
    val styleMaterial = stringResource(R.string.settings_style_material)
    val appearanceSummary = listOfNotNull(
        if (app.plait.ui.theme.Appearance.style == app.plait.ui.theme.UiStyle.Glass) styleGlass else styleMaterial,
        if (!app.plait.ui.theme.Appearance.glass) stringResource(R.string.settings_appearance_no_glass) else null,
        if (app.plait.ui.theme.Appearance.calm) stringResource(R.string.settings_appearance_calm) else null,
    ).joinToString(" · ")
    val youSummary = app.plait.data.Profile.name.ifBlank { stringResource(R.string.settings_you_summary) }
    val syncSummary = if (app.plait.data.sync.SyncEngine.configured(context)) app.plait.data.sync.SyncEngine.status.let {
        if (it.isBlank()) stringResource(R.string.common_on) else stringResource(R.string.settings_sync_summary_status, it)
    } else stringResource(R.string.common_off)
    val language = remember { AppLocale.choice(context) }
    val languageNames = mapOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_english),
        "ar" to stringResource(R.string.settings_language_arabic),
    )
    val languageNote = stringResource(R.string.settings_language_note)
    val addSummary = stringResource(R.string.settings_add_summary)
    val importSummary = stringResource(R.string.settings_import_summary)
    val diagSummary = stringResource(R.string.settings_diag_summary)
    val advSummary = stringResource(R.string.settings_adv_google)
    val versionLabel = stringResource(R.string.settings_updates_version, app.plait.data.update.Updater.currentVersion(context))
    val weekSummary = app.plait.data.week.WorkWeek.label()
    val panelNames = listOf(
        app.plait.widget.WidgetOpacity.Clear to stringResource(R.string.settings_widgets_panel_clear),
        app.plait.widget.WidgetOpacity.Soft to stringResource(R.string.settings_widgets_panel_soft),
        app.plait.widget.WidgetOpacity.Solid to stringResource(R.string.settings_widgets_panel_solid),
    )
    val colourNames = listOf(
        app.plait.widget.WidgetTheme.Plait to stringResource(R.string.settings_widgets_colours_plait),
        app.plait.widget.WidgetTheme.Wallpaper to stringResource(R.string.settings_widgets_colours_wallpaper),
    )
    val widgetsSummary = stringResource(
        R.string.settings_widgets_summary,
        panelNames.first { it.first == app.plait.widget.WidgetPrefs.opacity }.second, colourNames.first { it.first == app.plait.widget.WidgetPrefs.theme }.second,
    )

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
            CompositionLocalProvider(LocalHubLook provides (section?.let { HubLook(hubTone(it), hubSectionIds.indexOf(it)) } ?: HubLook(GlassTone.Sky, 0))) { LazyColumn(
                Modifier.widthIn(max = 720.dp).fillMaxHeight().align(Alignment.TopCenter)
                    .graphicsLayer { val p = enter.value; translationX = (1f - p) * 72.dp.toPx() * slideFrom * (if (calm) 0f else 1f) * (if (AppLocale.isRtl) -1f else 1f); alpha = p.coerceIn(0f, 1f) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = statusTop + 76.dp, bottom = 60.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (section == null) item {
                    Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.common_settings), style = PlaitType.dateTitle, color = plait.text)
                        Text(
                            if (Calendars.connected) stringResource(
                                if (Calendars.syncing) R.string.settings_connected_summary_syncing else R.string.settings_connected_summary,
                                pluralStringResource(R.plurals.settings_events, Calendars.events.size, Calendars.events.size),
                                (cals.count { it.id !in Calendars.hidden } + Calendars.feeds.size + (if (Calendars.googleOn) 1 else 0)).let { pluralStringResource(R.plurals.settings_calendars, it, it) },
                            )
                            else stringResource(R.string.settings_nothing_connected),
                            style = PlaitType.body, color = plait.muted,
                        )
                        Calendars.message?.let { Text(it, style = PlaitType.caption, color = plait.signal) }
                    }
                }

                if (section == null && (!hasAccess || !canWrite)) {
                    item {
                        HubCard {
                            Text(stringResource(if (!hasAccess) R.string.settings_access_title_read else R.string.settings_access_title_write), style = PlaitType.bodyStrong, color = plait.text)
                            Text(
                                stringResource(R.string.settings_access_body),
                                style = PlaitType.body, color = plait.muted,
                            )
                            HubButton(stringResource(if (!hasAccess) R.string.settings_access_button_read else R.string.settings_access_button_write), primary = true, onClick = calendarAccess)
                        }
                    }
                }

                fold(section, "accounts", R.string.settings_accounts_title, accountsSummary) {
                val accounts = AccountFilter.accounts(cals)
                if (accounts.isNotEmpty()) {
                    item {
                        FoldNote(stringResource(if (Calendars.accountsChosen) R.string.settings_accounts_note_chosen else R.string.settings_accounts_note_all))
                    }
                    accounts.forEachIndexed { i, acct ->
                        item(key = "acct-${acct.name}-${acct.type}") {
                            val on = Calendars.accountEnabled(acct.name)
                            var open by remember { mutableStateOf(false) }
                            val look = LocalHubLook.current
                            HubCard(Modifier.rise(i + 1)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(
                                        Modifier.size(40.dp).background(acct.source.color().copy(alpha = 0.22f), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(acct.source.initial, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = acct.source.color()) }
                                    Column(Modifier.weight(1f)) {
                                        Text(TextUtil.shortenAccount(acct.name, 30), style = PlaitType.bodyStrong.userText(), color = plait.text, maxLines = 1, softWrap = false)
                                        Text(
                                            acct.source.label + " · " + pluralStringResource(R.plurals.settings_calendars, acct.calendars.size, acct.calendars.size),
                                            style = PlaitType.caption, color = plait.muted, maxLines = 1,
                                        )
                                    }
                                    PlaitSwitch(on, { Calendars.setAccountEnabled(context, acct.name, !on) }, stringResource(R.string.settings_show_item, Bidi.isolate(acct.name)))
                                }
                                if (on) {
                                    HubBtn(stringResource(if (open) R.string.settings_calendars_hide else R.string.settings_calendars_choose), { open = !open }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                                    if (open) acct.calendars.forEach { cal ->
                                        val chosen = dest?.id == cal.id
                                        Column(
                                            Modifier.fillMaxWidth().toneSurface(RoundedCornerShape(20.dp), look.tone, look.rotation, inner = true).padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                Box(Modifier.size(12.dp).background(Color(cal.color or 0xFF000000.toInt()), CircleShape))
                                                Text(cal.name, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Medium).userText(), color = plait.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                PlaitSwitch(cal.id !in Calendars.hidden, { Calendars.setCalendarVisible(context, cal.id, cal.id in Calendars.hidden) }, stringResource(R.string.settings_show_item, Bidi.isolate(cal.name)))
                                            }
                                            if (cal.writable) {
                                                HubChip(
                                                    stringResource(if (chosen) R.string.settings_save_here_chosen else R.string.settings_save_here),
                                                    chosen, { Calendars.setDestination(context, cal.id) },
                                                )
                                            } else Text(stringResource(R.string.settings_read_only), style = PlaitType.caption, color = plait.muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (hasAccess) {
                    item { Text(stringResource(R.string.settings_no_calendars), style = PlaitType.body, color = plait.muted) }
                }

                }

                fold(section, "add", R.string.settings_add_title, addSummary) {
                item { FoldNote(stringResource(R.string.settings_add_note)) }
                item {
                    HubCard {
                        HubButton(stringResource(R.string.settings_add_google), primary = true) { addAccount(arrayOf("com.google")) }
                        HubButton(stringResource(R.string.settings_add_outlook)) { addAccount(null) }
                        Text(
                            stringResource(R.string.settings_add_help),
                            style = PlaitType.caption, color = plait.muted,
                        )
                    }
                }

                }

                fold(section, "import", R.string.settings_import_title, importSummary) {
                item { FoldNote(stringResource(R.string.settings_import_note)) }
                item {
                    HubCard {
                        HubField(
                            url, { url = it; linkError = null }, stringResource(R.string.settings_import_link_hint),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addLink() }),
                            trailing = { HubBtn(stringResource(if (Calendars.syncing) R.string.settings_import_adding else R.string.common_add), addLink, style = ButtonStyle.Primary, size = ButtonSize.Small) },
                        )
                        linkError?.let { Text(it, style = PlaitType.caption, color = plait.signal) }
                        HubButton(stringResource(R.string.settings_import_file)) { pickFile.launch(arrayOf("text/calendar", "application/octet-stream", "text/plain", "*/*")) }
                        fileNote?.let { Text(it, style = PlaitType.caption, color = plait.muted) }
                        Calendars.feeds.forEach { feed ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(10.dp).background(feed.source.color(), CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Text(feed.name, style = PlaitType.body.userText(), color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (feed.url.startsWith("file://")) stringResource(R.string.settings_import_file_on_phone) else feed.source.label + " · " + Bidi.isolate(Feeds.fallbackName(feed.url)), style = PlaitType.caption, color = plait.muted, maxLines = 1)
                                }
                                ConfirmDeleteButton({ Calendars.removeFeed(context, feed) }, label = stringResource(R.string.common_remove))
                            }
                        }
                        Text(
                            stringResource(R.string.settings_import_help),
                            style = PlaitType.caption, color = plait.muted,
                        )
                    }
                }

                }

                fold(section, "send", R.string.settings_send_title, sendSummary) {
                item { FoldNote(stringResource(R.string.settings_send_note)) }
                item {
                    HubCard {
                        Text(
                            if (dest != null) stringResource(
                                R.string.settings_send_dest, Bidi.isolate(dest.name), Bidi.isolate(dest.account),
                                stringResource(when (dest.source) {
                                    app.plait.data.Source.Google -> R.string.settings_send_google_calendar
                                    app.plait.data.Source.Outlook -> R.string.settings_send_outlook
                                    app.plait.data.Source.ICloud -> R.string.settings_send_icloud
                                    else -> R.string.settings_send_your_calendar_app
                                }),
                            )
                            else stringResource(if (hasAccess) R.string.settings_send_no_writable else R.string.settings_send_allow_first),
                            style = PlaitType.body, color = plait.text,
                        )
                        if (dest == null && hasAccess && canWrite) {
                            HubButton(stringResource(R.string.settings_send_create_local)) {
                                val id = DeviceWrite.ensureLocalCalendar(context)
                                if (id != null) { Calendars.setDestination(context, id); Calendars.refreshDevice(context) }
                                else Calendars.report(Txt.s(R.string.settings_send_create_failed))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_send_notes_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_send_notes_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(Calendars.syncNotes, { Calendars.setSyncNotes(context, !Calendars.syncNotes) }, stringResource(R.string.settings_send_notes_switch))
                        }
                    }
                }

                }

                fold(section, "ai", R.string.settings_ai_title, aiSummary, badge = aiBadge) {
                item {
                    var light by remember { mutableStateOf(app.plait.ai.Ai.lightAuto(context)) }
                    HubCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_ai_light_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_ai_light_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(light, { light = !light; app.plait.ai.Ai.setLightAuto(context, light) }, stringResource(R.string.settings_ai_light_title))
                        }
                    }
                }
                aiSection()

                }

                fold(section, "reminders", R.string.settings_reminders_title, remindersSummary) {
                item { FoldNote(stringResource(R.string.settings_reminders_note)) }
                item {
                    HubCard {
                        var before by remember { mutableStateOf(app.plait.reminder.ReminderPrefs.before(context)) }
                        var lead by remember { mutableStateOf(app.plait.reminder.ReminderPrefs.leadMin(context)) }
                        var nudge by remember { mutableStateOf(app.plait.reminder.ReminderPrefs.nudge(context)) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_reminders_before_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_reminders_before_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(before, { before = !before; app.plait.reminder.ReminderPrefs.setBefore(context, before) }, stringResource(R.string.settings_reminders_before_switch))
                        }
                        if (before) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5, 10, 15, 30).forEach { m -> HubChip(pluralStringResource(R.plurals.settings_reminders_lead, m, m), lead == m, { lead = m; app.plait.reminder.ReminderPrefs.setLead(context, m) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_reminders_nudge_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_reminders_nudge_body, Bidi.isolate(app.plait.data.TimeFmt.clock(java.time.LocalTime.of(18, 0)))), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(nudge, { nudge = !nudge; app.plait.reminder.ReminderPrefs.setNudge(context, nudge) }, stringResource(R.string.settings_reminders_nudge_title))
                        }
                        Text(stringResource(R.string.settings_reminders_widget), style = PlaitType.caption, color = plait.muted)
                    }
                }

                }

                fold(section, "week", R.string.settings_week_title, weekSummary) {
                item { FoldNote(stringResource(R.string.settings_week_note)) }
                item {
                    HubCard {
                        Text(stringResource(R.string.settings_week_first_day), style = PlaitType.bodyStrong, color = plait.text)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            java.time.DayOfWeek.entries.forEach { d ->
                                HubChip(d.getDisplayName(java.time.format.TextStyle.FULL, AppLocale.locale), app.plait.data.week.WorkWeek.startDay == d, { app.plait.data.week.WorkWeek.set(context, d) })
                            }
                        }
                    }
                }

                }

                fold(section, "appearance", R.string.settings_appearance_title, appearanceSummary) {
                item {
                    HubCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_amoled_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_amoled_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(app.plait.ui.theme.Appearance.amoled, { app.plait.ui.theme.Appearance.setAmoled(context, !app.plait.ui.theme.Appearance.amoled) }, stringResource(R.string.settings_amoled_title))
                        }
                    }
                }
                item {
                    HubCard {
                        Text(stringResource(R.string.settings_style_title), style = PlaitType.bodyStrong, color = plait.text)
                        Text(stringResource(R.string.settings_style_body), style = PlaitType.caption, color = plait.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HubChip(styleGlass, app.plait.ui.theme.Appearance.style == app.plait.ui.theme.UiStyle.Glass, { app.plait.ui.theme.Appearance.setStyle(context, app.plait.ui.theme.UiStyle.Glass); askRestart = app.plait.ui.theme.Appearance.style != app.plait.ui.theme.Appearance.activeStyle })
                            HubChip(styleMaterial, app.plait.ui.theme.Appearance.style == app.plait.ui.theme.UiStyle.Material, { app.plait.ui.theme.Appearance.setStyle(context, app.plait.ui.theme.UiStyle.Material); askRestart = app.plait.ui.theme.Appearance.style != app.plait.ui.theme.Appearance.activeStyle })
                        }
                        if (app.plait.ui.theme.Appearance.style != app.plait.ui.theme.Appearance.activeStyle) {
                            Text(stringResource(R.string.settings_style_restart_note), style = PlaitType.caption, color = plait.signal)
                            HubBtn(stringResource(R.string.settings_restart_now), { app.plait.ui.material.restartApp(context) }, size = ButtonSize.Small, style = ButtonStyle.Primary)
                        }
                    }
                }
                item {
                    HubCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_glass_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_glass_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(app.plait.ui.theme.Appearance.glass, { app.plait.ui.theme.Appearance.setGlass(context, !app.plait.ui.theme.Appearance.glass) }, stringResource(R.string.settings_glass_title))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_calm_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_calm_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(app.plait.ui.theme.Appearance.calm, { app.plait.ui.theme.Appearance.setCalm(context, !app.plait.ui.theme.Appearance.calm) }, stringResource(R.string.settings_calm_title))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_solid_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_solid_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(app.plait.ui.theme.Appearance.solid, { app.plait.ui.theme.Appearance.setSolid(context, !app.plait.ui.theme.Appearance.solid) }, stringResource(R.string.settings_solid_title))
                        }
                    }
                }

                }

                fold(section, "widgets", R.string.settings_widgets_title, widgetsSummary) {
                item { FoldNote(stringResource(R.string.settings_widgets_note)) }
                item {
                    HubCard {
                        val widgets = app.plait.widget.WidgetPrefs
                        Text(stringResource(R.string.settings_widgets_panel), style = PlaitType.bodyStrong, color = plait.text)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            panelNames.forEach { (o, name) -> HubChip(name, widgets.opacity == o, { widgets.setOpacity(context, o) }) }
                        }
                        Text(stringResource(R.string.settings_widgets_colours), style = PlaitType.bodyStrong, color = plait.text)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            colourNames.forEach { (t, name) -> HubChip(name, widgets.theme == t, { widgets.setTheme(context, t) }) }
                        }
                        Text(stringResource(R.string.settings_widgets_colours_note), style = PlaitType.caption, color = plait.muted)
                        Text(stringResource(R.string.settings_widgets_shows), style = PlaitType.bodyStrong, color = plait.text)
                        listOf(
                            Triple(R.string.settings_widgets_join, widgets.showJoin, { v: Boolean -> widgets.setShowJoin(context, v) }),
                            Triple(R.string.settings_widgets_later, widgets.showLater, { v: Boolean -> widgets.setShowLater(context, v) }),
                            Triple(R.string.settings_widgets_dots, widgets.showDots, { v: Boolean -> widgets.setShowDots(context, v) }),
                        ).forEach { (label, on, set) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(label), style = PlaitType.body, color = plait.text, modifier = Modifier.weight(1f))
                                PlaitSwitch(on, { set(!on) }, stringResource(label))
                            }
                        }
                    }
                }

                }

                fold(section, "language", R.string.settings_language_title, languageNames.getValue(language)) {
                item { FoldNote(languageNote) }
                item {
                    HubCard {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            languageNames.forEach { (tag, name) -> HubChip(name, language == tag, { if (tag != language) AppLocale.choose(context, tag) }) }
                        }
                    }
                }

                }

                fold(section, "you", R.string.settings_you_title, youSummary) {
                item { FoldNote(stringResource(R.string.settings_you_note)) }
                item {
                    HubCard {
                        var name by remember { mutableStateOf(app.plait.data.Profile.name) }
                        Text(stringResource(R.string.settings_you_name), style = PlaitType.bodyStrong, color = plait.text)
                        HubField(name, { name = it; app.plait.data.Profile.setName(context, it) }, stringResource(R.string.settings_you_name_hint))
                        Text(stringResource(R.string.settings_you_device, Bidi.isolate(app.plait.data.Profile.device)), style = PlaitType.caption, color = plait.muted)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_backup_media_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_backup_media_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(includeMedia, { includeMedia = !includeMedia }, stringResource(R.string.settings_backup_media_title))
                        }
                        Text(stringResource(R.string.settings_backup_pass_title), style = PlaitType.bodyStrong, color = plait.text)
                        HubField(passphrase, { passphrase = it }, stringResource(R.string.settings_backup_pass_hint), password = true)
                        Text(stringResource(R.string.settings_backup_pass_body), style = PlaitType.caption, color = plait.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            HubBtn(
                                stringResource(if (backupBusy) R.string.common_working else R.string.settings_backup_export),
                                { exportFile.launch("plait-backup-" + java.time.LocalDate.now() + ".zip") },
                                style = ButtonStyle.Primary, enabled = !backupBusy,
                            )
                            HubBtn(stringResource(R.string.settings_backup_markdown), { exportMarkdown.launch("plait-notes-" + java.time.LocalDate.now() + ".zip") }, style = ButtonStyle.Quiet, enabled = !backupBusy)
                            HubBtn(stringResource(R.string.settings_backup_import), { importFile.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, enabled = !backupBusy)
                            pendingImport?.let { u -> HubBtn(stringResource(R.string.settings_backup_retry), { runImport(u) }, style = ButtonStyle.Primary, enabled = !backupBusy && passphrase.isNotEmpty()) }
                        }
                        backupNote?.let { Text(it, style = PlaitType.caption, color = plait.text) }
                        AutoBackup.last(context).takeIf { it > 0 }?.let { Text(stringResource(R.string.settings_backup_last, Bidi.isolate(app.plait.data.TimeFmt.fmt("EEE d MMM").format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())))), style = PlaitType.caption, color = plait.muted) }
                        var autoBackup by remember { mutableStateOf(AutoBackup.enabled(context)) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_backup_auto_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_backup_auto_body), style = PlaitType.caption, color = plait.muted)
                                if (!AutoBackup.canRun(context)) Text(stringResource(R.string.settings_backup_auto_needs_sync), style = PlaitType.caption, color = plait.signal)
                            }
                            PlaitSwitch(autoBackup, { if (autoBackup || AutoBackup.canRun(context)) { autoBackup = !autoBackup; AutoBackup.setEnabled(context, autoBackup) } }, stringResource(R.string.settings_backup_auto_title))
                        }
                        Text(
                            stringResource(R.string.settings_backup_import_note),
                            style = PlaitType.caption, color = plait.muted,
                        )
                    }
                }

                }

                fold(section, "sync", R.string.settings_sync_title, syncSummary) {
                item { FoldNote(stringResource(R.string.settings_sync_note)) }
                item { SyncCard() }

                }

                fold(section, "diag", R.string.settings_diag_title, diagSummary) {
                item { FoldNote(stringResource(R.string.settings_diag_note)) }
                item {
                    HubCard {
                        var checks by remember { mutableStateOf<List<app.plait.diag.Check>?>(null) }
                        var showLog by remember { mutableStateOf(false) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HubBtn(stringResource(R.string.settings_diag_run), { scope.launch { checks = app.plait.diag.SelfCheck.run(context) } }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                            HubBtn(stringResource(if (showLog) R.string.settings_diag_hide_log else R.string.settings_diag_show_log), { showLog = !showLog }, size = ButtonSize.Small)
                            HubBtn(stringResource(if (app.plait.diag.FrameMonitor.enabled) R.string.settings_diag_hide_frames else R.string.settings_diag_show_frames), { app.plait.diag.FrameMonitor.turn(!app.plait.diag.FrameMonitor.enabled) }, size = ButtonSize.Small, style = ButtonStyle.Quiet)
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
                            val empty = stringResource(R.string.settings_diag_empty_log)
                            val log = remember(showLog) { app.plait.diag.Diag.read().ifBlank { empty } }
                            Text(log.takeLast(2500), style = PlaitType.monoSmall, color = plait.text.copy(alpha = 0.8f))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val shareLog = stringResource(R.string.settings_diag_share_log)
                                HubBtn(shareLog, {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, app.plait.diag.Diag.read())
                                    runCatching { context.startActivity(android.content.Intent.createChooser(send, shareLog).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }, size = ButtonSize.Small)
                                HubBtn(stringResource(R.string.common_clear), { app.plait.diag.Diag.clear(); showLog = false }, size = ButtonSize.Small, style = ButtonStyle.Quiet)
                            }
                        }
                    }
                }

                }

                fold(section, "updates", R.string.settings_updates_title, versionLabel) {
                if (!app.plait.BuildConfig.PLAY) item { FoldNote(stringResource(R.string.settings_updates_note)) }
                item {
                    HubCard {
                        val st = app.plait.data.update.Updater.state
                        var showUpdate by remember { mutableStateOf(false) }
                        var showNews by remember { mutableStateOf(false) }
                        Text(versionLabel, style = PlaitType.bodyStrong, color = plait.text)
                        if (!app.plait.BuildConfig.PLAY) Text(
                            when (st) {
                                is app.plait.data.update.UpdateState.Checking -> stringResource(R.string.common_checking)
                                is app.plait.data.update.UpdateState.UpToDate -> stringResource(R.string.settings_update_latest)
                                is app.plait.data.update.UpdateState.Available -> stringResource(R.string.settings_update_ready, Bidi.isolate(st.info.version))
                                is app.plait.data.update.UpdateState.Downloading -> stringResource(R.string.settings_update_downloading, Bidi.isolate(st.info.version))
                                is app.plait.data.update.UpdateState.NeedsPermission -> stringResource(R.string.settings_update_waiting)
                                is app.plait.data.update.UpdateState.Installing -> stringResource(R.string.settings_update_installing)
                                is app.plait.data.update.UpdateState.Failed -> st.reason
                                else -> stringResource(R.string.settings_update_idle)
                            },
                            style = PlaitType.caption, color = plait.muted,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val waiting = st is app.plait.data.update.UpdateState.Available || st is app.plait.data.update.UpdateState.Downloading ||
                                st is app.plait.data.update.UpdateState.NeedsPermission || (st is app.plait.data.update.UpdateState.Failed && st.info != null)
                            if (app.plait.BuildConfig.PLAY) Unit
                            else if (waiting) HubBtn(stringResource(R.string.settings_update_button), { showUpdate = true }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                            else HubBtn(stringResource(R.string.settings_update_check), { app.plait.data.update.Updater.check(context) }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                            HubBtn(stringResource(R.string.settings_update_whats_new), { showNews = true }, size = ButtonSize.Small)
                            HubBtn(stringResource(R.string.settings_update_tour), { app.plait.data.update.WhatsNew.show = app.plait.data.update.WhatsNew.Show.Tour }, size = ButtonSize.Small)
                        }
                        if (!app.plait.BuildConfig.PLAY) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            var auto by remember { mutableStateOf(app.plait.data.update.Updater.autoCheck(context)) }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.settings_update_auto_title), style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_update_auto_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(auto, { auto = !auto; app.plait.data.update.Updater.setAutoCheck(context, auto) }, stringResource(R.string.settings_update_auto_title))
                        }
                        if (showUpdate) UpdatePrompt(forceOpen = true) { showUpdate = false }
                        if (showNews) {
                            val news = remember { app.plait.data.update.WhatsNew.current(context) }
                            if (news != null) WhatsNewDialog(news) { showNews = false }
                            else DetailDialog(stringResource(R.string.settings_update_whats_new), { showNews = false }) { Text(stringResource(R.string.settings_update_no_notes), style = PlaitType.body, color = plait.text) }
                        }
                    }
                }
                }

                fold(section, "adv", R.string.settings_adv_title, advSummary) {
                item { FoldNote(stringResource(R.string.settings_adv_note)) }
                item {
                    HubCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(advSummary, style = PlaitType.bodyStrong, color = plait.text)
                                Text(stringResource(R.string.settings_adv_body), style = PlaitType.caption, color = plait.muted)
                            }
                            PlaitSwitch(Calendars.googleOn, { if (Calendars.googleOn) Calendars.disconnectGoogle(context) else googleSync() }, advSummary)
                        }
                    }
                }
                }

            } }
            // Inside the backdrop so the glass back button refracts the faded content, not the text.
            app.plait.ui.TopFade(Modifier.align(Alignment.TopCenter))
        }
        Box(Modifier.statusBarsPaddingCompat().padding(start = 18.dp, top = 12.dp)) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), goBack, backdrop)
        }
        if (askRestart) DetailDialog(stringResource(R.string.settings_restart_title), { askRestart = false }) {
            Text(
                stringResource(R.string.settings_restart_body, if (app.plait.ui.theme.Appearance.style == app.plait.ui.theme.UiStyle.Material) styleMaterial else styleGlass),
                style = PlaitType.body, color = plait.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HubBtn(stringResource(R.string.settings_restart_now), { app.plait.ui.material.restartApp(context) }, size = ButtonSize.Small, style = ButtonStyle.Primary)
                HubBtn(stringResource(R.string.common_later), { askRestart = false }, size = ButtonSize.Small, style = ButtonStyle.Quiet)
            }
        }
    }
}

@Composable
private fun Modifier.statusBarsPaddingCompat(): Modifier = this.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())

/** "Added 2 voice notes, 1 email." after an import, "Saved a backup with ..." after an export. */
internal fun backupSummary(r: BackupManager.Report, saved: Boolean): String {
    val parts = listOfNotNull(
        r.notes.takeIf { it > 0 }?.let { Txt.plural(R.plurals.settings_backup_voice_notes, it) },
        r.memos.takeIf { it > 0 }?.let { Txt.plural(R.plurals.settings_backup_memos, it) },
        r.emails.takeIf { it > 0 }?.let { Txt.plural(R.plurals.settings_backup_emails, it) },
        r.feeds.takeIf { it > 0 }?.let { Txt.plural(R.plurals.settings_backup_feeds, it) },
    )
    return if (parts.isEmpty()) Txt.s(if (saved) R.string.settings_backup_saved_nothing else R.string.settings_backup_added_nothing)
    else Txt.s(if (saved) R.string.settings_backup_saved else R.string.settings_backup_added, parts.joinToString(Txt.s(R.string.settings_list_separator)))
}

@Composable
internal fun SectionTitle(title: String, detail: String) {
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = PlaitType.section, color = plait.text)
        Text(detail, style = PlaitType.caption, color = plait.muted)
    }
}

@Composable
internal fun HubCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val look = LocalHubLook.current
    Column(
        modifier.fillMaxWidth().toneSurface(RoundedCornerShape(24.dp), look.tone, look.rotation, inner = true).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
internal fun HubButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    HubBtn(label, onClick, style = if (primary) ButtonStyle.Primary else ButtonStyle.Tinted)
}

/** The hub's buttons and chips take the open section's tone. */
@Composable
internal fun HubBtn(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: ButtonStyle = ButtonStyle.Tinted,
    size: ButtonSize = ButtonSize.Regular, icon: ImageVector? = null, enabled: Boolean = true,
) = PlaitButton(label, onClick, modifier, style, size, icon, enabled, LocalHubLook.current.tone)

@Composable
internal fun HubChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    PlaitChip(label, on, onClick, modifier, LocalHubLook.current.tone)

/** A text field in the glass look: tinted body, crisp rim, an optional trailing action inside it. */
@Composable
internal fun HubField(
    value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val look = LocalHubLook.current
    Row(
        modifier.fillMaxWidth().toneSurface(RoundedCornerShape(26.dp), look.tone, look.rotation).padding(start = 16.dp, end = if (trailing != null) 5.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = PlaitType.body.copy(color = plait.text), cursorBrush = SolidColor(plait.accent),
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(Modifier.height(50.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = PlaitType.body, color = plait.muted, maxLines = 1)
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}

/** What the open section looks like: its tone, and its place in the list (Material rotates containers by it). */
internal data class HubLook(val tone: GlassTone, val rotation: Int)
internal val LocalHubLook = androidx.compose.runtime.compositionLocalOf { HubLook(GlassTone.Sky, 0) }

internal val hubSectionIds = listOf("accounts", "add", "import", "send", "ai", "reminders", "week", "appearance", "widgets", "language", "you", "sync", "diag", "updates", "adv")

/** Neighbouring sections never share a tone. */
internal fun hubTone(id: String): GlassTone = when (id) {
    "accounts", "appearance", "sync" -> GlassTone.Sky
    "add", "send", "you", "widgets" -> GlassTone.Mint
    "import", "week", "diag" -> GlassTone.Peach
    "ai", "updates" -> GlassTone.Lavender
    else -> GlassTone.Rose
}

private fun hubIcon(id: String): ImageVector = when (id) {
    "accounts" -> Icons.Filled.AccountCircle
    "add" -> Icons.Filled.PersonAdd
    "import" -> Icons.Filled.FileDownload
    "send" -> Icons.Filled.Event
    "ai" -> Icons.Filled.AutoAwesome
    "reminders" -> Icons.Filled.Notifications
    "week" -> Icons.Filled.DateRange
    "appearance" -> Icons.Filled.Palette
    "language" -> Icons.Filled.Language
    "you" -> Icons.Filled.Person
    "sync" -> Icons.Filled.Sync
    "diag" -> Icons.Filled.CheckCircle
    "updates" -> Icons.Filled.Info
    else -> Icons.Filled.Tune
}

internal fun aiSummaryText(writing: String, voice: String?) =
    Txt.s(R.string.settings_ai_summary, Bidi.isolate(writing), voice?.let { Bidi.isolate(it) } ?: Txt.s(R.string.settings_ai_voice_none))

internal fun remindersSummaryText(before: Boolean, leadMin: Int, nudge: Boolean): String {
    if (!before) return Txt.s(R.string.common_off)
    val lead = Txt.plural(R.plurals.settings_reminders_lead, leadMin)
    return if (nudge) Txt.s(R.string.settings_reminders_summary_nudge, lead) else lead
}

/** The hub's AI row asks for the pair that works best, until either is on the phone. */
internal fun aiRecommendedBadge(qwenInstalled: Boolean, turboInstalled: Boolean): String? =
    if (!qwenInstalled && !turboInstalled) Txt.s(R.string.settings_ai_badge) else null

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
            Text(engine.folderName?.let { stringResource(R.string.settings_sync_with, Bidi.isolate(it)) } ?: stringResource(R.string.settings_sync_on), style = PlaitType.bodyStrong, color = plait.text)
            if (engine.status.isNotBlank()) Text(engine.status, style = PlaitType.caption, color = plait.accent)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_backup_media_title), style = PlaitType.bodyStrong, color = plait.text)
                    Text(stringResource(R.string.settings_sync_media_body), style = PlaitType.caption, color = plait.muted)
                }
                PlaitSwitch(media, { media = !media; engine.setIncludeMedia(context, media) }, stringResource(R.string.settings_sync_media_switch))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HubBtn(stringResource(if (engine.running) R.string.common_syncing else R.string.settings_sync_now), { scope.launch { engine.run(context) } }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = !engine.running)
                HubBtn(stringResource(R.string.settings_sync_stop), { engine.disconnect(context); connected = false }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
            Text(stringResource(R.string.settings_sync_when), style = PlaitType.caption, color = plait.muted)
        } else {
            Text(stringResource(R.string.settings_sync_setup), style = PlaitType.caption, color = plait.muted)
            HubBtn(stringResource(if (folder == null) R.string.settings_sync_choose_folder else R.string.settings_sync_folder_chosen), { pick.launch(null) }, size = ButtonSize.Small)
            HubField(pass, { pass = it }, stringResource(R.string.settings_sync_pass_hint), password = true)
            HubBtn(stringResource(R.string.settings_sync_start), {
                folder?.let { engine.connect(context, it, pass); connected = true; scope.launch { engine.run(context) } }
            }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = folder != null && pass.length >= 6)
            if (pass.isNotEmpty() && pass.length < 6) Text(stringResource(R.string.settings_sync_pass_short), style = PlaitType.caption, color = plait.signal)
        }
    }
}


/**
 * One settings section. In the list it is a single row; tapping it opens the section on its own page (and Back returns to
 * the list). Only the open section's items are built.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.fold(
    current: String?, id: String, @androidx.annotation.StringRes title: Int, summary: String, badge: String? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    if (current == null) {
        item(key = "fold-$id", contentType = "fold") {
            FoldHeader(id, stringResource(title), summary, badge) { app.plait.ui.HomeState.hubSection = id }
        }
    } else if (current == id) {
        item(key = "page-$id") {
            Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToneIcon(hubIcon(id), hubTone(id), size = 48.dp)
                    Text(stringResource(title), style = PlaitType.dateTitle.copy(fontSize = 32.sp, lineHeight = 36.sp), color = plait.text, modifier = Modifier.weight(1f))
                }
                Text(summary, style = PlaitType.body, color = plait.muted)
            }
        }
        content()
    }
}

@Composable
private fun FoldHeader(id: String, title: String, summary: String, badge: String?, onOpen: () -> Unit) {
    val tone = hubTone(id)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .toneSurface(RoundedCornerShape(28.dp), tone, hubSectionIds.indexOf(id), strong = id == "ai")
            .pressable(onOpen, Role.Button, lift = 3.dp).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ToneIcon(hubIcon(id), tone, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PlaitType.bodyStrong, color = plait.text)
            Text(summary, style = PlaitType.caption, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (badge != null) PlaitBadge(badge, BadgeKind.BestMatch, Modifier.padding(top = 4.dp))
        }
        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.NavigateNext, stringResource(R.string.common_open_item, title), tint = plait.muted, modifier = Modifier.size(26.dp))
    }
}

/** The explanation at the top of an open section. */
@Composable
internal fun FoldNote(text: String) {
    Text(text, style = PlaitType.caption, color = plait.muted, modifier = Modifier.padding(horizontal = 4.dp))
}
