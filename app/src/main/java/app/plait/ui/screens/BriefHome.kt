package app.plait.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ai.Ai
import app.plait.data.CalEvent
import app.plait.data.CallLinks
import app.plait.data.Calendars
import app.plait.data.NoteStore
import app.plait.data.brief.BriefBuilder
import app.plait.data.brief.DayAnalytics
import app.plait.data.brief.DayBrief
import app.plait.data.brief.DayBriefBuilder
import app.plait.data.brief.DayBriefPrompt
import app.plait.data.brief.DayStats
import app.plait.data.brief.MeetingBrief
import app.plait.data.brief.MeetingKind
import app.plait.data.brief.Phase
import app.plait.data.context.ContextAnalyzer
import app.plait.data.context.ContextStore
import app.plait.data.context.HighlightKind
import app.plait.data.memo.MemoStore
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.HomeState
import app.plait.ui.PlaitButton
import app.plait.ui.PlaitChip
import app.plait.ui.glass.liquidGlass
import app.plait.ui.pressable
import app.plait.ui.fauxGlass
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

internal val LocalCardBackdrop = androidx.compose.runtime.compositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

/** Material 3: each part of the day is one of the scheme's container colours, flat and rounded. */
@Composable
private fun cardShell(material: Boolean, cs: androidx.compose.material3.ColorScheme, phase: Phase, backdrop: com.kyant.backdrop.Backdrop, tone: KindTone): Modifier =
    if (material) Modifier.clip(RoundedCornerShape(36.dp)).background(phaseContainer(cs, phase)) else Modifier.liquidGlass(
        backdrop, shape = { com.kyant.shapes.RoundedRectangle(44.dp) },
        tint = tone.bg.copy(alpha = 0.40f), lens = false,
        surfaceColor = Color.White.copy(alpha = if (app.plait.ui.theme.LocalIsDark.current) 0.6f else 0.07f),
    )

private fun phaseContainer(cs: androidx.compose.material3.ColorScheme, phase: Phase): Color =
    when (phase) { Phase.Morning -> cs.tertiaryContainer; Phase.Afternoon -> cs.primaryContainer; Phase.Evening -> cs.secondaryContainer }

private fun onContainer(cs: androidx.compose.material3.ColorScheme, phase: Phase): Color =
    when (phase) { Phase.Morning -> cs.onTertiaryContainer; Phase.Afternoon -> cs.onPrimaryContainer; Phase.Evening -> cs.onSecondaryContainer }

private fun Phase.tone() = when (this) {
    Phase.Morning -> KindTone(Color(0xFFFFD98A), Color(0xFF0E1316))
    Phase.Afternoon -> KindTone(Color(0xFF9ED8FF), Color(0xFF0E1316))
    Phase.Evening -> KindTone(Color(0xFFD0C4FF), Color(0xFF0E1316))
}
private fun Phase.icon(): ImageVector = when (this) {
    Phase.Morning -> Icons.Filled.WbSunny
    Phase.Afternoon -> Icons.Filled.WbTwilight
    Phase.Evening -> Icons.Filled.NightsStay
}

/** Everything the three cards share, worked out once per refresh. */
private class DayData(
    val events: List<CalEvent>,
    val briefs: Map<String, MeetingBrief>,
    val kinds: Map<String, MeetingKind>,
    val day: DayBrief,
    val stats: DayStats,
    val sense: String,
    val noted: Set<String>,
    val lines: Map<String, String>,
)

/**
 * Home is one card at a time, and the cards follow the day: a morning card listing what is coming, a
 * midday card of what is done and what is left, an evening card with how the day went and a nudge to
 * write down notes. It opens on the part of the day you are in; swipe sideways for the others.
 */
@Composable
internal fun BriefPage(
    today: TodayData,
    nowMs: Long,
    tomorrow: List<CalEvent>,
    cardBackdrop: com.kyant.backdrop.Backdrop,
    pager: androidx.compose.foundation.pager.PagerState,
    onOpenEvent: (String) -> Unit,
    onOpenNotes: (String) -> Unit,
    onOpenHub: () -> Unit,
    onAllowCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val noteStore = remember { NoteStore.get(context) }
    val memoStore = remember { MemoStore.get(context) }
    val voiceCount = noteStore.notes.size
    val memoCount = memoStore.items.size
    
    val data = remember(today, nowMs / 60_000, voiceCount, memoCount) {
        val notes = noteStore.notes.toList()
        val all = (listOfNotNull(today.upNext) + today.later + today.earlier).distinctBy { it.id }.sortedWith(compareBy({ !it.allDay }, { it.startMs }))
        val briefs = all.associate { it.id to BriefBuilder.build(it, notes, nowMs) }
        val flags = HashMap<String, String>()
        val lines = HashMap<String, String>()
        val kinds = briefs.mapValues { it.value.kind }
        val noted = (noteStore.notes.map { it.eventId } + memoStore.items.map { it.eventId }).toSet()
        DayData(
            all, briefs, kinds, DayBriefBuilder.build(all, briefs, flags, nowMs, zone),
            DayAnalytics.compute(all, kinds, noted, nowMs, zone), DayAnalytics.sense(all, nowMs, zone), noted, lines,
        )
    }

    // The on-device model can rewrite the morning summary in friendlier words. Only offered where it exists.
    var aiReady by remember { mutableStateOf(false) }
    var aiLabel by remember { mutableStateOf("the AI on this phone") }
    var aiBusy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.plait.ai.AiPrefs.load(context); Ai.status(context).let { aiReady = it.ready; aiLabel = it.label } }
    // Quietly write the prep for the next meeting when it is under two hours away and an AI is switched on.
    val upcoming = today.upNext
    LaunchedEffect(upcoming?.id, aiReady, HomeState.refreshTick) {
        val ev = upcoming ?: return@LaunchedEffect
        val soon = ev.startMs in 1..(nowMs + 2 * 3_600_000L) && ev.endMs > nowMs
        if (aiReady && soon && !HomeState.prepTried.contains(ev.id) && app.plait.data.prep.PrepStore.get(context).entries[ev.id] == null) {
            HomeState.prepTried.add(ev.id)
            if (app.plait.data.prep.PrepBuilder.generate(context, ev) != null) app.plait.reminder.Reminders.scheduleAsync(context)
        }
    }
    val dayKey = remember(nowMs / 3_600_000, data.events.size) { "${nowMs / 86_400_000}:${data.events.map { it.id }.hashCode()}" }
    val aiText = if (HomeState.aiDayKey == dayKey) HomeState.aiDayText else null
    // Write the day summary once per day with the AI the person chose. No button, no reminder.
    LaunchedEffect(dayKey, aiReady, HomeState.refreshTick) {
        if (aiReady && data.events.isNotEmpty() && HomeState.aiDayKey != dayKey && !aiBusy) {
            aiBusy = true
            val out = Ai.generate(context, DayBriefPrompt.build(data.events, data.briefs, data.day, zone), background = true)
            if (out != null) { HomeState.aiDayKey = dayKey; HomeState.aiDayText = out.text.take(600); HomeState.aiDayBy = out.label }
            aiBusy = false
        }
    }
    val join: (CalEvent) -> Unit = { ev ->
        val link = ev.callLink
        if (link == null) onOpenEvent(ev.id)
        else if (!CallLinks.open(context, link)) Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var openPhase by remember { mutableStateOf<Phase?>(null) }

    Column(Modifier.fillMaxSize().padding(top = statusTop + 132.dp)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 18.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 0,
            key = { it },
        ) { page ->
            val phase = Phase.entries[page]
            val tone = phase.tone()
            val material = app.plait.ui.glass.LocalGlassEnv.current.material
            val cs = androidx.compose.material3.MaterialTheme.colorScheme
            Box(
                Modifier.fillMaxSize().padding(bottom = 104.dp).navigationBarsPadding()
                    .graphicsLayer {
                        // Pages beside the open one sit slightly back, like cards in a stack.
                        val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                        val s = 1f - 0.07f * off
                        scaleX = s; scaleY = s; alpha = 1f - 0.4f * off
                    }
                    .then(cardShell(material, cs, phase, cardBackdrop, tone)),
            ) {
                val hour = remember(nowMs / 60_000) { Instant.ofEpochMilli(nowMs).atZone(zone).let { it.hour + it.minute / 60f } }
                // The animated sky sits over the tinted glass at moderate strength, so the glass colour always shows through.
                if (!app.plait.ui.glass.LocalGlassEnv.current.material) Box(Modifier.matchParentSize().clip(RoundedCornerShape(44.dp))) { PhaseSky(phase, hour, Modifier.matchParentSize(), active = pager.settledPage == page) }
                CompositionLocalProvider(LocalCardInk provides (if (material) onContainer(cs, phase) else cardInk(app.plait.ui.theme.LocalIsDark.current)), LocalCardBackdrop provides cardBackdrop) {
                // The whole day scrolls inside the card; tapping it opens the same in a bigger pop-up.
                Column(
                    Modifier.fillMaxSize().edgeFade().verticalScroll(rememberScrollState()).pressable({ openPhase = phase }, lift = 2.dp).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CardHeader(phase, nowMs, zone)
                    CardBody(phase, false, today, data, tomorrow, nowMs, zone, aiText, onOpenEvent, onOpenNotes, join, onOpenHub, onAllowCalendar)
                }
                }
            }
        }
    }

    openPhase?.let { ph ->
        val tone = ph.tone()
        val cs = androidx.compose.material3.MaterialTheme.colorScheme
        val material = app.plait.ui.glass.LocalGlassEnv.current.material
        val hour = remember(nowMs / 60_000) { Instant.ofEpochMilli(nowMs).atZone(zone).let { it.hour + it.minute / 60f } }
        // The open card looks like the card it grew from: the same container colour, or the same tinted sky.
        ExpandDialog({ openPhase = null }, fullScreen = true) { close ->
            Box(
                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 28.dp).clip(RoundedCornerShape(if (material) 36.dp else 44.dp))
                    .background(if (material) phaseContainer(cs, ph) else androidx.compose.ui.graphics.lerp(Color.White, tone.bg, 0.5f)),
            ) {
                if (!material) PhaseSky(ph, hour, Modifier.matchParentSize(), active = true)
                CompositionLocalProvider(LocalCardInk provides if (material) onContainer(cs, ph) else cardInk(app.plait.ui.theme.LocalIsDark.current)) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CardHeader(ph, nowMs, zone)
                        CardBody(ph, false, today, data, tomorrow, nowMs, zone, aiText, { openPhase = null; onOpenEvent(it) }, { openPhase = null; onOpenNotes(it) }, join, { openPhase = null; onOpenHub() }, onAllowCalendar)
                        CloseButton(close, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** Content fades out at the bottom of the card (and a little at the top) as it scrolls, instead of being cut off. */
private fun Modifier.edgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height
        val top = (14.dp.toPx() / h).coerceIn(0f, 0.2f)
        val bottom = (72.dp.toPx() / h).coerceIn(0f, 0.4f)
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, top to Color.Black, (1f - bottom) to Color.Black, 1f to Color.Transparent),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }

@Composable
private fun CardBody(
    phase: Phase, compact: Boolean, today: TodayData, data: DayData, tomorrow: List<CalEvent>, nowMs: Long, zone: ZoneId, aiText: String?,
    onOpenEvent: (String) -> Unit, onOpenNotes: (String) -> Unit, join: (CalEvent) -> Unit, onOpenHub: () -> Unit, onAllowCalendar: () -> Unit,
) {
    when (phase) {
        Phase.Morning -> MorningCard(today, data, nowMs, zone, aiText, compact, onOpenEvent, join, onOpenHub, onAllowCalendar)
        Phase.Afternoon -> AfternoonCard(data, nowMs, compact, onOpenEvent, join)
        Phase.Evening -> EveningCard(data, tomorrow, zone, compact, onOpenEvent, onOpenNotes)
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces shared by the cards
// ---------------------------------------------------------------------------------------------

@Composable
private fun CardHeader(phase: Phase, nowMs: Long, zone: ZoneId) {
    val ink = LocalCardInk.current
    val t = Instant.ofEpochMilli(nowMs).atZone(zone)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(phase.icon(), null, tint = ink, modifier = Modifier.size(22.dp))
        Text(phase.greeting + app.plait.data.Profile.firstName().let { if (it.isBlank()) "" else ", $it" }, style = PlaitType.bodyStrong.copy(fontSize = 17.sp), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            t.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).uppercase() + " ${t.dayOfMonth} " +
                t.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).uppercase(),
            style = PlaitType.monoLabel, color = ink.copy(alpha = 0.65f),
        )
    }
    // Where the clock is between 06:00 and 22:00.
    val target = (((t.hour * 60 + t.minute) - 6 * 60) / (16f * 60f)).coerceIn(0f, 1f)
    val frac by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 120f), label = "day-progress")
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawRoundRect(ink.copy(alpha = 0.14f), size = Size(size.width, size.height), cornerRadius = CornerRadius(size.height / 2))
        drawRoundRect(ink, size = Size((size.width * frac).coerceAtLeast(size.height), size.height), cornerRadius = CornerRadius(size.height / 2))
    }
}

@Composable
private fun Headline(text: String) {
    val ink = LocalCardInk.current
    Text(text, style = PlaitType.headline.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold), color = ink)
}

@Composable
private fun Body(text: String, maxLines: Int = Int.MAX_VALUE) {
    val ink = LocalCardInk.current
    Text(text, style = PlaitType.body.copy(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium), color = ink.copy(alpha = 0.85f), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun SectionLabel(text: String) {
    val ink = LocalCardInk.current
    Text(text.uppercase(), style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Soft(content: @Composable () -> Unit) {
    Box(Modifier.clip(CircleShape).background(LocalCardInk.current.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp)) { content() }
}

/**
 * One meeting as a tall banner: the title first and biggest, then when it is, who is coming (faces), where, what needs
 * attention, and Join for the next call.
 */
@Composable
private fun EventLine(
    ev: CalEvent, data: DayData, nowMs: Long, onOpen: () -> Unit, join: ((CalEvent) -> Unit)? = null, showStatus: Boolean = true, compact: Boolean = false,
) {
    val ink = LocalCardInk.current
    val done = ev.endMs in 1..nowMs && !ev.allDay
    val live = ev.startMs in 1..nowMs && ev.endMs > nowMs
    val brief = data.briefs[ev.id]
    val whenText = if (ev.allDay) "All day" else ev.time + (if (ev.endLabel.isNotBlank()) " – ${ev.endLabel}" else "")
    val where = ev.callLink?.provider?.label ?: ev.place
    Column(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 0.dp else 150.dp)
            .fauxGlass(RoundedCornerShape(28.dp), cardGlass(strong = live)).pressable(onOpen).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                ev.title, style = PlaitType.cardTitle.copy(fontSize = 23.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold),
                color = ink.copy(alpha = if (done) 0.6f else 1f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (showStatus && done) Icon(Icons.Filled.CheckCircle, "Done", tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
            else if (live) Soft { Text("Now", style = PlaitType.monoSmall.copy(fontSize = 12.sp), color = ink) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Schedule, null, tint = ink.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
            Text(whenText, style = PlaitType.monoTime.copy(fontSize = 15.sp), color = ink.copy(alpha = 0.85f), maxLines = 1)
            if (ev.duration.isNotBlank()) Soft { Text(ev.duration, style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = ink) }
        }
        if (where.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (ev.callLink != null) Icons.Filled.Videocam else Icons.Filled.Place, null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            Text(where, style = PlaitType.caption, color = ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val others = ev.guests.filter { !it.self }
        if (others.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                others.take(4).forEach { g ->
                    Box(Modifier.size(28.dp).background(avatarColor(g), CircleShape), contentAlignment = Alignment.Center) {
                        Text(g.initials, style = PlaitType.monoSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = Color(0xFF0E1316))
                    }
                }
            }
            Text(
                brief?.withLine?.takeIf { it.isNotBlank() } ?: "${others.size} guest${if (others.size == 1) "" else "s"}",
                style = PlaitType.caption, color = ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val flag = data.day.flags[ev.id]
        val noNotes = done && ev.id !in data.noted && data.kinds[ev.id] != MeetingKind.Personal
        if (flag != null || noNotes) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                flag?.let { f -> Soft { Text(f, style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = ink) } }
                if (noNotes) Soft { Text("No notes yet", style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = ink) }
            }
        }
        if (join != null && !done && ev.callLink != null) {
            val bd = LocalCardBackdrop.current
            if (bd != null) app.plait.ui.glass.GlassPillButton(
                label = if (live) "Join now" else "Join", onClick = { join(ev) }, backdrop = bd, icon = Icons.Filled.Videocam,
                tint = plait.accent, contentColor = plait.onAccent, height = 44.dp,
            ) else PlaitButton(if (live) "Join now" else "Join", { join(ev) }, style = ButtonStyle.Primary, size = ButtonSize.Small, icon = Icons.Filled.Videocam)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Morning: everything coming today, in order
// ---------------------------------------------------------------------------------------------

@Composable
private fun MorningCard(
    today: TodayData, data: DayData, nowMs: Long, zone: ZoneId, aiText: String?, compact: Boolean,
    onOpenEvent: (String) -> Unit, join: (CalEvent) -> Unit, onOpenHub: () -> Unit, onAllowCalendar: () -> Unit,
) {
    val ink = LocalCardInk.current
    val n = data.stats.meetings
    Headline(if (n == 0) "A clear day." else "${DayAnalytics.word(n)} meeting${if (n == 1) "" else "s"} today.")
    Body(data.sense, if (compact) 2 else Int.MAX_VALUE)
    if (!compact && data.day.summary.isNotBlank()) Body(aiText ?: data.day.summary)
    if (!compact && data.day.load.isNotEmpty()) DayStripOnCard(data.day, data.kinds, nowMs, zone)

    if (!Calendars.connected) {
        Column(Modifier.fillMaxWidth().fauxGlass(RoundedCornerShape(26.dp), cardGlass()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No calendar connected yet", style = PlaitType.bodyStrong, color = ink)
            Text("Connect your calendars and your real day shows up here.", style = PlaitType.body, color = ink.copy(alpha = 0.8f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaitButton("Use my phone's calendars", onAllowCalendar, style = ButtonStyle.Ink, size = ButtonSize.Small)
                PlaitButton("More options", onOpenHub, style = ButtonStyle.InkSoft, size = ButtonSize.Small)
            }
        }
    }
    Calendars.message?.let { Text(it, style = PlaitType.caption, color = ink) }

    if (data.events.isNotEmpty()) {
        if (!compact) SectionLabel("Today in order")
        val next = data.events.firstOrNull { !it.allDay && it.endMs > nowMs }
        val list = if (compact) data.events.filter { it.endMs > nowMs || it.allDay }.ifEmpty { data.events }.take(2) else data.events
        list.forEach { ev -> EventLine(ev, data, nowMs, { onOpenEvent(ev.id) }, join = if (ev.id == next?.id) join else null, compact = compact) }
        if (compact && data.events.size > list.size) Text("+${data.events.size - list.size} more · tap to open", style = PlaitType.caption, color = ink.copy(alpha = 0.7f))
    }
}

// ---------------------------------------------------------------------------------------------
// Midday: what is done, what is left
// ---------------------------------------------------------------------------------------------

@Composable
private fun AfternoonCard(data: DayData, nowMs: Long, compact: Boolean, onOpenEvent: (String) -> Unit, join: (CalEvent) -> Unit) {
    val s = data.stats
    Headline(
        when {
            s.meetings == 0 -> "Nothing on today."
            s.left == 0 -> "That's the day."
            s.done == 0 -> "Nothing done yet."
            else -> "${s.done} done, ${s.left} to go."
        }
    )
    Body(data.sense, if (compact) 2 else Int.MAX_VALUE)
    val doneEvents = data.events.filter { !it.allDay && it.endMs in 1..nowMs }
    val ahead = data.events.filter { !it.allDay && it.endMs > nowMs }
    if (compact) {
        val shown = ahead.take(2)
        shown.forEachIndexed { i, ev -> EventLine(ev, data, nowMs, { onOpenEvent(ev.id) }, join = null, compact = true) }
        if (ahead.size > shown.size || doneEvents.isNotEmpty()) Text("tap to open", style = PlaitType.caption, color = LocalCardInk.current.copy(alpha = 0.7f))
        return
    }
    if (doneEvents.isNotEmpty()) {
        SectionLabel("Done")
        doneEvents.forEach { EventLine(it, data, nowMs, { onOpenEvent(it.id) }) }
    }
    if (ahead.isNotEmpty()) {
        SectionLabel("Still ahead")
        ahead.forEachIndexed { i, ev -> EventLine(ev, data, nowMs, { onOpenEvent(ev.id) }, join = if (i == 0) join else null) }
    }
    if (s.meetings > 0) Body("So far: ${DayAnalytics.span(s.minutesSoFar)} in meetings.")
}

// ---------------------------------------------------------------------------------------------
// Evening: how the day went
// ---------------------------------------------------------------------------------------------

@Composable
private fun EveningCard(data: DayData, tomorrow: List<CalEvent>, zone: ZoneId, compact: Boolean, onOpenEvent: (String) -> Unit, onOpenNotes: (String) -> Unit) {
    val context = LocalContext.current
    val ink = LocalCardInk.current
    val s = data.stats
    Headline(if (s.meetings == 0) "A quiet day." else "${DayAnalytics.span(s.minutesSoFar)} in meetings.")
    Body(DayAnalytics.recap(s), if (compact) 2 else Int.MAX_VALUE)
    if (compact) {
        if (s.missingNotes.isNotEmpty()) Body("${s.missingNotes.size} meeting${if (s.missingNotes.size == 1) "" else "s"} without notes.", 1)
        if (tomorrow.isNotEmpty()) Body("Tomorrow: ${tomorrow.size} meeting${if (tomorrow.size == 1) "" else "s"}, first at ${tomorrow.first().time}.", 2)
        return
    }

    if (s.meetings > 0) {
        SectionLabel("The day in numbers")
        // Two tiles to a row, each half the card wide, so no space is left empty.
        val tiles = listOfNotNull(
            "${s.done}/${s.meetings}" to "meetings held",
            "${DayAnalytics.workdayShare(s.minutesSoFar)}%" to "of an 8 h workday",
            s.longest?.let { DayAnalytics.span((it.endMs - it.startMs) / 60_000) to "longest · " + it.title },
            (s.busiestHour?.let { app.plait.data.TimeFmt.hour(it) } ?: "–") to "busiest hour",
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (v, l) -> StatTile(v, l, Modifier.weight(1f).fillMaxHeight()) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (s.byKind.isNotEmpty()) {
            SectionLabel("Time by kind")
            KindBar(s)
        }
    }

    SectionLabel("Notes")
    if (s.missingNotes.isEmpty()) {
        Body(if (s.done == 0) "Nothing to write up yet." else "All caught up. Every finished meeting has a note.")
    } else {
        Body("Add a few lines while it's fresh. A voice note, some text or a photo all count.")
        s.missingNotes.take(6).forEach { ev ->
            Row(
                Modifier.fillMaxWidth().fauxGlass(RoundedCornerShape(24.dp), cardGlass()).padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ev.title, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(ev.time + if (ev.endLabel.isNotBlank()) " – ${ev.endLabel}" else "", style = PlaitType.monoSmall, color = ink.copy(alpha = 0.6f))
                }
                PlaitButton("Add note", { onOpenNotes(ev.id) }, style = ButtonStyle.Ink, size = ButtonSize.Small)
            }
        }
        if (s.missingNotes.size > 6) Text("+${s.missingNotes.size - 6} more", style = PlaitType.caption, color = ink.copy(alpha = 0.7f))
    }

    SectionLabel("Tomorrow")
    if (tomorrow.isEmpty()) Body("Tomorrow is clear.")
    else {
        val first = tomorrow.first()
        Body("${DayAnalytics.word(tomorrow.size)} meeting${if (tomorrow.size == 1) "" else "s"}. First is ${first.title} at ${first.time}.")
        PlaitButton("Open ${shortTitle(first.title)}", { onOpenEvent(first.id) }, style = ButtonStyle.InkSoft, size = ButtonSize.Small)
    }
    // A suggestion only: nothing is added to the calendar until you tap.
    val focus = remember(tomorrow) {
        val dayStart = java.time.LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        app.plait.data.prep.FocusTime.suggest(tomorrow.filter { !it.allDay }.map { it.startMs to it.endMs }, dayStart, 1, zone = zone).firstOrNull()
    }
    if (focus != null) {
        Body("You have ${app.plait.data.prep.FocusTime.lengthLabel(focus)} free tomorrow from ${app.plait.data.TimeFmt.clock(focus.startMs, zone)}.")
        PlaitButton(
            "Hold it as focus time",
            { Toast.makeText(context, app.plait.data.device.Booking.book(context, "Focus time", focus, zone), Toast.LENGTH_LONG).show() },
            style = ButtonStyle.Ink, size = ButtonSize.Small,
        )
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    val ink = LocalCardInk.current
    Column(modifier.fauxGlass(RoundedCornerShape(24.dp), cardGlass()).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = PlaitType.cardHeadline.copy(fontSize = 30.sp, lineHeight = 32.sp), color = ink, maxLines = 1)
        Text(label, style = PlaitType.caption, color = ink.copy(alpha = 0.7f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun KindBar(s: DayStats) {
    val ink = LocalCardInk.current
    val total = s.byKind.sumOf { it.second }.coerceAtLeast(1)
    val tones = s.byKind.map { it.first.tone().bg }
    Canvas(Modifier.fillMaxWidth().height(18.dp).clip(CircleShape)) {
        drawRect(ink.copy(alpha = 0.1f))
        var x = 0f
        s.byKind.forEachIndexed { i, (_, m) ->
            val w = size.width * m / total
            drawRect(ink.copy(alpha = 0.85f - 0.14f * i), Offset(x, 0f), Size((w - 2f).coerceAtLeast(2f), size.height))
            x += w
        }
    }
    s.byKind.take(4).forEachIndexed { i, (kind, m) ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(ink.copy(alpha = 0.85f - 0.14f * i), CircleShape))
            Text(kind.label, style = PlaitType.bodyStrong, color = ink, modifier = Modifier.weight(1f))
            Text(DayAnalytics.span(m), style = PlaitType.monoLabel, color = ink.copy(alpha = 0.75f))
        }
    }
}

/** The shape of the day: each meeting as a block between the first and last hour, with a tick for now. */
@Composable
private fun DayStripOnCard(day: DayBrief, kinds: Map<String, MeetingKind>, nowMs: Long, zone: ZoneId) {
    val ink = LocalCardInk.current
    val dayStart = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val nowFraction = ((nowMs - dayStart) / 60_000f - day.loadStartHour * 60f) / ((day.loadEndHour - day.loadStartHour) * 60f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(26.dp)) {
            val h = size.height
            drawRoundRect(ink.copy(alpha = 0.1f), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2))
            day.load.forEach { b ->
                val w = ((b.to - b.from) * size.width).coerceAtLeast(6f)
                drawRoundRect(ink.copy(alpha = 0.8f), Offset(b.from * size.width, 3f), Size(w, h - 6f), CornerRadius((h - 6f) / 2))
            }
            if (nowFraction in 0f..1f) drawRect(Color(0xFFE5432C), Offset(nowFraction * size.width - 1.5f, 0f), Size(3f, h))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(app.plait.data.TimeFmt.hour(day.loadStartHour), style = PlaitType.monoSmall, color = ink.copy(alpha = 0.6f))
            Text(app.plait.data.TimeFmt.hour(day.loadEndHour % 24), style = PlaitType.monoSmall, color = ink.copy(alpha = 0.6f))
        }
    }
}

/** A long meeting title cut at a word with "…", for a button label ("Open MOBIHITS & Mobizone…"). */
internal fun shortTitle(title: String, max: Int = 26): String {
    val t = title.trim().replace(Regex("\\s+"), " ")
    if (t.length <= max) return t
    return t.take(max).substringBeforeLast(' ').trimEnd(' ', '/', '&', '-', ',', '·', ':') + "…"
}
