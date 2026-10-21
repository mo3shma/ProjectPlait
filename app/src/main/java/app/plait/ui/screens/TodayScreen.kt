package app.plait.ui.screens

import app.plait.ui.plaitCard
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.togetherWith
import app.plait.ui.PlaitMark
import androidx.compose.runtime.Composable
import android.widget.Toast
import androidx.compose.material.icons.filled.Add
import app.plait.ui.glass.GlassPillButton
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.data.Agenda
import app.plait.data.CalEvent
import app.plait.data.Calendars
import app.plait.data.NoteStore
import app.plait.data.SavedNote
import app.plait.data.Source
import app.plait.data.brief.BriefBuilder
import app.plait.ui.Entrance
import app.plait.ui.HomeState
import app.plait.ui.glass.GlassIconButton
import app.plait.ui.glass.GlassMenuAction
import app.plait.ui.glass.GlassMorphMenu
import app.plait.ui.glass.GlassTab
import app.plait.ui.glass.GlassTabBar
import app.plait.ui.pressable
import app.plait.ui.rise
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun Source.color(): Color = when (this) {
    Source.Google -> plait.google
    Source.Outlook -> plait.outlook
    Source.ICloud -> plait.icloud
    Source.Other -> plait.muted
}

/**
 * Home. Three tabs: the day's brief, the calendar, and voice notes. Which tab is open, how far each
 * list is scrolled and which calendar day is selected are kept in [HomeState], so Back from any
 * other screen lands exactly where you left.
 */
@Composable
fun TodayScreen(onOpenEvent: (String) -> Unit, onOpenNotes: (String) -> Unit, onOpenAt: (String, Int) -> Unit, onRecord: (String, Boolean) -> Unit, onOpenHub: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    val ambient = rememberLayerBackdrop()
    val zone = remember { java.time.ZoneId.systemDefault() }

    // Phone calendars are re-read every minute, linked calendars every 15 minutes.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    remember(context) { Calendars.load(context); app.plait.data.Profile.load(context) }
    LaunchedEffect(Unit) {
        if (Calendars.feeds.isNotEmpty()) Calendars.refreshFeeds(context)
        var ticks = 0
        while (true) {
            delay(60_000)
            nowMs = System.currentTimeMillis()
            if (Calendars.deviceOn) Calendars.refreshDevice(context)
            if (++ticks % 15 == 0) Calendars.refreshFeeds(context)
        }
    }
    val connected = Calendars.connected
    val events = Calendars.events
    val today = remember(nowMs / 60_000, events, connected) { TodayData.real(events, nowMs, zone) }
    val calendarEvents = remember(nowMs / 3_600_000, events, connected) { events }
    // Give the widget the same days the app shows, then refresh it.
    LaunchedEffect(calendarEvents) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { app.plait.widget.NextMeetingWidget.saveSnapshot(context, calendarEvents); app.plait.widget.NextMeetingWidget.updateAll(context) }
        }
    }
    val tomorrow = remember(calendarEvents, nowMs / 3_600_000) {
        Agenda.eventsOn(calendarEvents, java.time.LocalDate.now(zone).plusDays(1), zone).filter { !it.allDay }
    }
    val calendarState = remember { HomeState.calendar(java.time.LocalDate.now(zone)) }

    val googleSync = rememberGoogleSync()
    LaunchedEffect(Unit) { app.plait.data.sync.SyncEngine.requestAsync(context) }
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { app.plait.reminder.Reminders.scheduleAsync(context) }
    LaunchedEffect(Unit) {
        // Ask once, so alerts before meetings can show. Declining just means no alerts.
        val p = context.getSharedPreferences("plait", android.content.Context.MODE_PRIVATE)
        if (android.os.Build.VERSION.SDK_INT >= 33 && !p.getBoolean("asked_notifications", false) &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            p.edit().putBoolean("asked_notifications", true).apply()
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val calendarAccess = rememberCalendarAccess()
    LaunchedEffect(Calendars.googleOn) { if (Calendars.googleOn && Calendars.lastSyncMs > 0) googleSync() }
    val onSyncClick: () -> Unit = {
        if (!Calendars.connected) onOpenHub()
        else {
            if (Calendars.deviceOn) Calendars.refreshDevice(context)
            if (Calendars.googleOn) googleSync()
            if (Calendars.feeds.isNotEmpty()) scope.launch { Calendars.refreshFeeds(context) }
            // Sync with the other phones, and have the AI write its pieces again from the fresh data.
            app.plait.data.sync.SyncEngine.requestAsync(context, force = true)
            HomeState.aiDayKey = ""; HomeState.aiDayText = null
            HomeState.prepTried.clear()
            val prep = app.plait.data.prep.PrepStore.get(context)
            prep.entries.filterValues { it.by != "You" }.keys.toList().forEach { prep.clear(it) }
            HomeState.refreshTick++
            Toast.makeText(context, "Refreshing calendars…", Toast.LENGTH_SHORT).show()
        }
    }

    val tab = HomeState.tab
    val phaseNow = remember(nowMs / 600_000) { app.plait.data.brief.Phase.at(nowMs, zone) }
    // Which part of the day the feed shows: the one you are in, or the one last chosen in this part of the day.
    var phaseIdx by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableIntStateOf(if (HomeState.page >= 0 && HomeState.pagePhase == phaseNow.ordinal) HomeState.page else phaseNow.ordinal)
    }
    LaunchedEffect(phaseIdx) { HomeState.page = phaseIdx; HomeState.pagePhase = phaseNow.ordinal }
    var menuOpen by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    val scrim by animateFloatAsState(if (menuOpen) 0.45f else 0f, tween(220), label = "scrim")
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val phaseTabs = remember { app.plait.data.brief.Phase.entries.map { GlassTab(it.label) } }
    // On the Notes tab a voice note stands alone; elsewhere it goes to the meeting that is next.
    val recordTarget = if (tab == 2) "" else today.upNext?.id ?: ""

    Box(Modifier.fillMaxSize().background(plait.ground)) {
        // Content layer: everything glass refracts lives inside this box.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(Modifier.fillMaxSize().layerBackdrop(ambient)) { AmbientBackground() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            val calm = app.plait.ui.glass.LocalGlassEnv.current.reduceMotion
            androidx.compose.animation.AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    val slide = if (calm) 6 else 14
                    (androidx.compose.animation.slideInHorizontally(if (calm) androidx.compose.animation.core.tween(100) else androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f)) { dir * it / slide } +
                        androidx.compose.animation.fadeIn(if (calm) androidx.compose.animation.core.tween(90) else androidx.compose.animation.core.spring(stiffness = 1600f))) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(if (calm) 60 else 120))
                },
                label = "home-tab",
            ) { t ->
                when (t) {
                    0 -> BriefPage(today, nowMs, tomorrow, ambient, app.plait.data.brief.Phase.entries[phaseIdx], onOpenEvent, onOpenNotes, onOpenHub, calendarAccess)
                    1 -> CalendarContent(calendarState, calendarEvents, nowMs, zone, ambient, onOpenEvent)
                    else -> NotesLibrary(ambient, onOpenEvent, onOpenAt) { onRecord(it, false) }
                }
            }
            }
            }
            // Content fades out under the glass bars.
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(statusTop + 28.dp)
                    .background(Brush.verticalGradient(listOf(plait.ground.copy(alpha = 0.92f), Color.Transparent)))
            )
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, plait.ground.copy(alpha = 0.9f))))
            )
        }

        // Functional layer: glass. One row on every tab, with the same two round buttons at the right.
        Row(
            Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth().statusBarsPadding().padding(top = 12.dp, start = 18.dp, end = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaitMark(40.dp)
                Text("Plait", style = PlaitType.section.copy(fontSize = 26.sp, lineHeight = 28.sp), color = plait.text)
            }
            if (tab == 1) {
                GlassIconButton(Icons.Filled.ChevronLeft, "Previous", { calendarState.step(-1) }, backdrop)
                GlassIconButton(Icons.Filled.ChevronRight, "Next", { calendarState.step(1) }, backdrop)
            } else {
                GlassIconButton(
                    icon = Icons.Filled.Sync,
                    description = if (Calendars.connected) "Refresh calendars" else "Connect calendars",
                    onClick = onSyncClick,
                    backdrop = backdrop,
                    spinning = Calendars.syncing,
                )
            }
            GlassIconButton(Icons.Filled.Settings, "Settings", onOpenHub, backdrop)
        }

        if (tab == 0) {
            // Morning / Midday / Evening under the icons, as wide as the card; the feed below shows the chosen one.
            Box(Modifier.align(Alignment.TopCenter).widthIn(max = 720.dp).fillMaxWidth().statusBarsPadding().padding(top = 72.dp, start = 18.dp, end = 18.dp)) {
                GlassTabBar(
                    tabs = phaseTabs,
                    selected = phaseIdx,
                    onSelect = { phaseIdx = it },
                    backdrop = backdrop,
                    height = app.plait.ui.ControlHeights.Medium,
                )
            }
        }

        val tabs = remember {
            listOf(
                GlassTab("Brief", Icons.Filled.Today),
                GlassTab("Calendar", Icons.Filled.CalendarMonth),
                GlassTab("Notes", Icons.Filled.Description),
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp)) {
            GlassTabBar(
                tabs = tabs,
                selected = tab,
                onSelect = { if (it != HomeState.tab) { Entrance.arrived(); HomeState.tab = it } },
                backdrop = backdrop,
                modifier = Modifier.padding(end = 80.dp),
            )
        }

        if (menuOpen) androidx.activity.compose.BackHandler { menuOpen = false }
        if (scrim > 0.001f) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))
                    .clickable(interactionSource = null, indication = null) { menuOpen = false }
            )
        }

        // One round "+" button with three choices, as tall as the bar beside it and sharing its bottom inset; on a wide screen it
        // sits at the edge of the centred column.
        val sideGap = ((LocalConfiguration.current.screenWidthDp - 720) / 2).coerceAtLeast(0).dp
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp + sideGap, bottom = 14.dp)) {
            GlassMorphMenu(
                expanded = menuOpen,
                onExpandedChange = { menuOpen = it },
                collapsedIcon = Icons.Filled.Add,
                collapsedDescription = "Record or add",
                actions = listOf(
                    GlassMenuAction("Voice note", Icons.Filled.Mic) { onRecord(recordTarget, false) },
                    GlassMenuAction("Record this call", Icons.Filled.Videocam) { onRecord(recordTarget, true) },
                    GlassMenuAction("New meeting", Icons.Filled.EditCalendar) { showNew = true },
                ),
                backdrop = backdrop,
            )
        }

        NewMeetingSheet(
            visible = showNew,
            initialDate = calendarState.selected ?: java.time.LocalDate.now(zone),
            onDismiss = { showNew = false },
            onOpenHub = { showNew = false; onOpenHub() },
        )
    }
}

/** Soft colour fields behind everything: the standard-material background glass has to refract. */
@Composable
internal fun AmbientBackground(colors: List<Color>? = null) {
    if (app.plait.ui.glass.LocalGlassEnv.current.material) return
    val c = plait
    // On pure black the colour fields are kept softer so the black stays black.
    val k = if (app.plait.ui.theme.LocalIsDark.current && app.plait.ui.theme.Appearance.amoled) 0.55f else 1f
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val a = Brush.radialGradient(listOf((colors?.getOrNull(0) ?: c.accent).copy(alpha = 0.26f * k), Color.Transparent), Offset(size.width * 0.95f, size.height * 0.08f), size.width * 0.9f)
            val b = Brush.radialGradient(listOf((colors?.getOrNull(1) ?: c.signal).copy(alpha = 0.20f * k), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.72f), size.width * 0.85f)
            val d = Brush.radialGradient(listOf((colors?.getOrNull(2) ?: c.outlook).copy(alpha = 0.16f * k), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.5f), size.width * 0.7f)
            onDrawBehind {
                drawRect(a)
                drawRect(b)
                drawRect(d)
            }
        }
    )
}

@Composable
internal fun listPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Below the top row (logo, name and the round buttons), so headers never sit under it.
    return PaddingValues(start = 18.dp, end = 18.dp, top = top + 80.dp, bottom = 170.dp)
}

/** What the Brief tab shows: real calendar events once connected, built-in examples before that. */
internal class TodayData(val earlier: List<CalEvent>, val upNext: CalEvent?, val later: List<CalEvent>) {
    companion object {
        fun real(events: List<CalEvent>, nowMs: Long, zone: java.time.ZoneId): TodayData {
            val day = Agenda.today(events, nowMs, zone)
            val next = Agenda.upNext(day, nowMs)
            return TodayData(Agenda.earlier(day, nowMs).filter { it.id != next?.id }, next, Agenda.later(day, next, nowMs))
        }

    }
}

@Composable
internal fun ScreenHeader(kicker: String, title: String) {
    Column(Modifier.fillMaxWidth().rise(0)) {
        Text(kicker, style = PlaitType.monoLabel, color = plait.muted)
        Spacer(Modifier.height(6.dp))
        Text(title, style = PlaitType.dateTitle, color = plait.text)
    }
}

internal fun formatClock(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}


@Composable
internal fun EventRow(ev: CalEvent, onClick: () -> Unit, modifier: Modifier = Modifier, past: Boolean = false) {
    Row(
        modifier.graphicsLayer { alpha = if (past) 0.55f else 1f }.fillMaxWidth().plaitCard(RoundedCornerShape(28.dp)).pressable(onClick).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val tone = remember(ev.id, ev.title) { BriefBuilder.classify(ev).tone() }
        Column(
            Modifier.width(62.dp).clip(RoundedCornerShape(16.dp)).background(tone.bg).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(ev.time, style = PlaitType.monoTime.copy(fontSize = if (ev.time.length > 5) 11.sp else 14.sp), color = tone.ink, maxLines = 1, softWrap = false)
            if (ev.duration.isNotBlank()) Text(ev.duration, style = PlaitType.monoSmall, color = tone.ink.copy(alpha = 0.65f))
        }
        Column(Modifier.weight(1f)) {
            Text(ev.title, style = PlaitType.bodyStrong, color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(ev.source.color(), CircleShape))
                Text("${ev.source.label} · ${ev.place}", style = PlaitType.caption, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (ev.callLink != null) {
            Box(Modifier.size(44.dp).background(plait.surfaceHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Videocam, "Has a call link", tint = plait.accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}
