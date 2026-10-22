package app.plait.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import app.plait.ui.glass.GlassIconButton
import app.plait.ui.glass.GlassTab
import app.plait.ui.glass.GlassTabBar
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.draw.drawWithCache
import app.plait.data.quotes.Quotes
import app.plait.data.brief.tidyTitle
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

/** Each kind of card has its own colour; two tints make a diagonal gradient on glass. */
private fun FeedCardKind.tints(phase: Phase): Pair<Color, Color> = when (this) {
    FeedCardKind.Meetings -> phase.tone().bg.let { it to it }
    FeedCardKind.Tasks -> if (phase == Phase.Morning) Color(0xFFFF9E8F) to Color(0xFFD9A5F0) else Color(0xFF8EE3B5) to Color(0xFF8CCBF0)
    FeedCardKind.Day -> Color(0xFFD0C4FF).let { it to it }
    FeedCardKind.Tomorrow -> Color(0xFFFFC59E) to Color(0xFFF4A6C0)
    FeedCardKind.WeekRecap -> Color(0xFFFFD166) to Color(0xFFFF9E8F)
}

/** Material 3: a different scheme container per kind, so neighbouring cards never match. */
private fun FeedCardKind.container(cs: androidx.compose.material3.ColorScheme, phase: Phase): Color = when (this) {
    FeedCardKind.Meetings -> if (phase == Phase.Morning) cs.tertiaryContainer else cs.primaryContainer
    FeedCardKind.Tasks, FeedCardKind.Day -> cs.secondaryContainer
    FeedCardKind.Tomorrow -> cs.tertiaryContainer
    FeedCardKind.WeekRecap -> cs.primaryContainer
}

private fun FeedCardKind.onContainer(cs: androidx.compose.material3.ColorScheme, phase: Phase): Color = when (this) {
    FeedCardKind.Meetings -> if (phase == Phase.Morning) cs.onTertiaryContainer else cs.onPrimaryContainer
    FeedCardKind.Tasks, FeedCardKind.Day -> cs.onSecondaryContainer
    FeedCardKind.Tomorrow -> cs.onTertiaryContainer
    FeedCardKind.WeekRecap -> cs.onPrimaryContainer
}

private val CardGlassShape: Shape = com.kyant.shapes.RoundedRectangle(GlassCardRadius.dp)
private val CardGlassShapeOf: () -> Shape = { CardGlassShape }

@Composable
private fun cardShell(material: Boolean, cs: androidx.compose.material3.ColorScheme, kind: FeedCardKind, phase: Phase, backdrop: com.kyant.backdrop.Backdrop, a: Color, b: Color, glassShape: () -> Shape): Modifier =
    if (material) Modifier.clip(RoundedCornerShape(MaterialCardRadius.dp)).background(kind.container(cs, phase)) else Modifier.liquidGlass(
        backdrop, shape = glassShape,
        tint = androidx.compose.ui.graphics.lerp(a, b, 0.5f).copy(alpha = 0.40f), lens = false,
        surfaceColor = Color.White.copy(alpha = if (app.plait.ui.theme.LocalIsDark.current) 0.6f else 0.07f),
    )

/** The two tints of a glass card as a diagonal wash over the glass. */
private fun Modifier.diagonalTint(a: Color, b: Color): Modifier = drawWithCache {
    val brush = Brush.linearGradient(listOf(a.copy(alpha = 0.32f), b.copy(alpha = 0.32f)), Offset.Zero, Offset(size.width, size.height))
    onDrawBehind { drawRect(brush) }
}

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

/** Quote text size and line height in sp by length: the shorter the quote, the bigger, so each fills its space. */
internal fun quoteSizes(length: Int): Pair<Int, Int> = when {
    length <= 60 -> 32 to 40
    length <= 100 -> 28 to 36
    else -> 24 to 32
}

/** A quote under an otherwise bare empty state, filling the card's spare height. Stable for the day and the slot. */
@Composable
internal fun QuoteBlock(phase: Phase, kind: FeedCardKind) {
    val ink = LocalCardInk.current
    val day = java.time.LocalDate.now().toEpochDay()
    val q = remember(phase, kind, day) { Quotes.pick(day, quoteSlot(phase, kind)) }
    val (size, line) = quoteSizes(q.text.length)
    Column(Modifier.fillRest().fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        // The mark's ink sits in the top half of its line, so only that half takes room.
        Text(
            "“", style = PlaitType.body.copy(fontSize = 72.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold), color = ink.copy(alpha = 0.25f),
            modifier = Modifier.layout { m, c -> val p = m.measure(c); layout(p.width, p.height / 2) { p.place(0, 0) } },
        )
        Text(q.text, style = PlaitType.body.copy(fontSize = size.sp, lineHeight = line.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium), color = ink.copy(alpha = 0.9f))
        Spacer(Modifier.height(12.dp))
        Text("- ${q.author}", style = PlaitType.body.copy(fontSize = 16.sp, lineHeight = 22.sp), color = ink.copy(alpha = 0.7f))
    }
}

/** Everything the cards share, worked out once per refresh. */
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
 * Home pages sideways through the parts of the day; each is a vertical feed of equal-size cards: the active card takes
 * three quarters of the height and its neighbour peeks, faded, in the rest. A tapped card opens over the whole area.
 */
@Composable
internal fun BriefPage(
    today: TodayData,
    nowMs: Long,
    tomorrow: List<CalEvent>,
    cardBackdrop: com.kyant.backdrop.Backdrop,
    onOpenEvent: (String) -> Unit,
    onOpenNotes: (String) -> Unit,
    onOpenHub: () -> Unit,
    onAllowCalendar: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    remember(context) { app.plait.data.week.WorkWeek.load(context) }
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

    // Morning, Midday and Evening side by side; Home opens on the part of the day it is, or the one last chosen in it.
    val phaseNow = remember(nowMs / 600_000) { Phase.at(nowMs, zone) }
    val nowPhase by rememberUpdatedState(phaseNow)
    val pager = rememberPagerState(initialPage = initialPhasePage(HomeState.page, HomeState.pagePhase, phaseNow.ordinal)) { Phase.entries.size }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { HomeState.page = it; HomeState.pagePhase = nowPhase.ordinal } }

    var open by remember { mutableStateOf<OpenCard?>(null) }
    val root = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val lastDay = weekRecapDue(nowMs, zone)
    val content: @Composable (Phase, FeedCardKind, Boolean) -> Unit = { ph, kind, full ->
        CardContent(kind, ph, full, data, tomorrow, nowMs, zone, aiText, onOpenEvent, onOpenNotes, join, onOpenHub, onAllowCalendar)
    }

    Box(Modifier.fillMaxSize().onPlaced { root[0] = it }) {
        // A sideways swipe changes the part of the day, an upward one the card: the two pagers take different axes.
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(top = statusTop + 132.dp, bottom = 104.dp).navigationBarsPadding()
                .graphicsLayer { alpha = 1f - (open?.progress?.value ?: 0f).coerceIn(0f, 1f) },
            contentPadding = PaddingValues(horizontal = 18.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 0,
            userScrollEnabled = open == null,
            key = { it },
        ) { page ->
            val ph = Phase.entries[page]
            val cards = remember(ph, lastDay) { feedCards(ph, lastDay) }
            val feed = rememberPagerState { cards.size }
            CardFeed(
                cards, feed,
                Modifier.fillMaxSize().graphicsLayer {
                    // Pages beside the open one sit slightly back, like cards in a stack.
                    val off = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
                    val s = peekScale(off)
                    scaleX = s; scaleY = s; alpha = if (off > 0.999f) 0f else phaseAlpha(off)
                },
                enabled = open == null,
            ) { index, kind ->
                val shown = open
                val hidden = shown != null && shown.phase == ph && shown.kind == kind
                FeedCard(
                    kind, ph, active = pager.settledPage == page && feed.settledPage == index && !hidden, hidden = hidden, nowMs, zone, cardBackdrop,
                    onExpand = { at -> root[0]?.let { r -> open = OpenCard(ph, kind, r.localBoundingBoxOf(at, clipBounds = false)) } },
                ) { content(ph, kind, false) }
            }
        }
        PhaseChips(pager, cardBackdrop, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp, start = 18.dp, end = 18.dp)) { open?.progress?.value ?: 0f }
        open?.let { o ->
            ExpandedFeedCard(o, nowMs, zone, cardBackdrop, statusTop, onClosed = { open = null }) { content(o.phase, o.kind, true) }
        }
    }
}

/** Morning / Midday / Evening above the feed, following the pager; they fade while a card is open. */
@Composable
private fun PhaseChips(pager: PagerState, backdrop: com.kyant.backdrop.Backdrop, modifier: Modifier, open: () -> Float) {
    val scope = rememberCoroutineScope()
    val tabs = remember { Phase.entries.map { GlassTab(it.label) } }
    Box(modifier.fillMaxWidth().graphicsLayer { alpha = 1f - open().coerceIn(0f, 1f) }) {
        GlassTabBar(
            tabs = tabs,
            selected = pager.currentPage,
            onSelect = { scope.launch { pager.animateScrollToPage(it) } },
            backdrop = backdrop,
            height = app.plait.ui.ControlHeights.Medium,
        )
    }
}

/** A card's shell: its own tint, the animated sky where it has one, and the ink and backdrop its content reads. */
@Composable
private fun CardSurface(
    kind: FeedCardKind, phase: Phase, backdrop: com.kyant.backdrop.Backdrop, nowMs: Long, zone: ZoneId, skyActive: Boolean,
    glassShape: () -> Shape, modifier: Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val material = app.plait.ui.glass.LocalGlassEnv.current.material
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    val (a, b) = kind.tints(phase)
    Box(modifier.then(cardShell(material, cs, kind, phase, backdrop, a, b, glassShape))) {
        if (!material) Box(Modifier.matchParentSize().graphicsLayer { shape = glassShape(); clip = true }) {
            if (a != b) Box(Modifier.matchParentSize().diagonalTint(a, b))
            // The animated sky sits over the tinted glass at moderate strength, so the glass colour always shows through.
            if (kind == FeedCardKind.Meetings || kind == FeedCardKind.Day) {
                val hour = remember(nowMs / 60_000) { Instant.ofEpochMilli(nowMs).atZone(zone).let { it.hour + it.minute / 60f } }
                PhaseSky(phase, hour, Modifier.matchParentSize(), active = skyActive)
            }
        }
        CompositionLocalProvider(LocalCardInk provides (if (material) kind.onContainer(cs, phase) else cardInk(app.plait.ui.theme.LocalIsDark.current)), LocalCardBackdrop provides backdrop) {
            content()
        }
    }
}

/**
 * One card of the feed. Its content does not scroll: it is cut at the card's edge, sinking into a progressive blur when
 * there is more. A tap anywhere that is not a control of its own opens the card in full.
 */
@Composable
private fun FeedCard(
    kind: FeedCardKind, phase: Phase, active: Boolean, hidden: Boolean, nowMs: Long, zone: ZoneId, backdrop: com.kyant.backdrop.Backdrop,
    onExpand: (LayoutCoordinates) -> Unit, content: @Composable () -> Unit,
) {
    val at = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val latestExpand by rememberUpdatedState(onExpand)
    val expand: () -> Unit = remember { { at[0]?.let { latestExpand(it) } } }
    val press = remember { MutableInteractionSource() }
    val squeeze = remember { Animatable(1f) }
    LaunchedEffect(press) {
        val spec = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
        press.interactions.collect { i -> if (i is PressInteraction) launch { squeeze.animateTo(if (i is PressInteraction.Press) 0.985f else 1f, spec) } }
    }
    var cardHeight by remember { mutableIntStateOf(0) }
    var contentHeight by remember { mutableIntStateOf(0) }
    val cut = overflows(contentHeight, cardHeight)
    CardSurface(
        kind, phase, backdrop, nowMs, zone, skyActive = active, glassShape = CardGlassShapeOf,
        modifier = Modifier.fillMaxSize().onPlaced { at[0] = it }
            .graphicsLayer { scaleX = squeeze.value; scaleY = squeeze.value; alpha = if (hidden) 0f else 1f }
            .clickable(interactionSource = press, indication = null, onClickLabel = "Show all", onClick = expand),
    ) {
        val env = app.plait.ui.glass.LocalGlassEnv.current
        val dark = app.plait.ui.theme.LocalIsDark.current
        Box(Modifier.fillMaxSize().onSizeChanged { cardHeight = it.height }.graphicsLayer { shape = CardGlassShapeOf(); clip = true }) {
            CardColumn(
                Modifier.fillMaxSize().growDown().onSizeChanged { contentHeight = it.height }
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 36.dp),
            ) {
                content()
            }
            if (cut) GlassEdge(dark, env.material, kind.container(androidx.compose.material3.MaterialTheme.colorScheme, phase))
        }
    }
}

/**
 * The tapped card grown over the whole Home area: same shell, the full content scrolling, a close button. Back, the
 * button or a pull down from the top of the content past [PullToCloseDp] shrinks it back into its place in the feed.
 */
@Composable
private fun ExpandedFeedCard(
    card: OpenCard, nowMs: Long, zone: ZoneId, backdrop: com.kyant.backdrop.Backdrop, statusTop: androidx.compose.ui.unit.Dp,
    onClosed: () -> Unit, content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spec = if (app.plait.ui.glass.LocalGlassEnv.current.reduceMotion) androidx.compose.animation.core.tween<Float>(180)
        else spring(dampingRatio = ExpandDamping, stiffness = ExpandStiffness)
    var closing by remember(card) { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch { card.progress.animateTo(0f, spec); onClosed() }
        }
    }
    val latestClose by rememberUpdatedState(close)
    val threshold = with(density) { PullToCloseDp.dp.toPx() }
    val limit = with(density) { RubberLimit.toPx() }
    val bar = with(density) { 48.dp.toPx() }
    val pull = remember(card) { Stretch(scope, bothEnds = false) { if (shouldClose(it, threshold)) latestClose() } }
    LaunchedEffect(card) { card.progress.animateTo(1f, spec) }
    androidx.activity.compose.BackHandler(enabled = !closing) { close() }
    // Glass corners morph from the card's to the open radius; shapes are kept per half dp so no frame allocates one.
    val shapes = remember(card) { arrayOfNulls<Shape>(((GlassCardRadius - OpenRadius) * 2).toInt() + 1) }
    val glassShape: () -> Shape = remember(card) {
        {
            val r = morphRadius(GlassCardRadius, card.progress.value.coerceIn(0f, 1f))
            val i = ((r - OpenRadius) * 2f).roundToInt().coerceIn(0, shapes.size - 1)
            shapes[i] ?: com.kyant.shapes.RoundedRectangle((OpenRadius + i / 2f).dp).also { shapes[i] = it }
        }
    }
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val dragState = rememberDraggableState { pull.drag(it) }
    // Takes every touch around the growing card, so nothing underneath reacts.
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        CardSurface(
            // The sky's web layer starts once the card is fully open, not on the first frame (target still 0) or while it moves.
            card.kind, card.phase, backdrop, nowMs, zone, skyActive = !closing && card.progress.targetValue == 1f && !card.progress.isRunning, glassShape = glassShape,
            modifier = Modifier.fillMaxSize()
                .expanding(card, top = statusTop + 72.dp, bottom = nav + 90.dp, side = 18.dp) { rubberBand(pull.over, limit) }
                .nestedScroll(pull)
                .draggable(dragState, Orientation.Vertical, onDragStopped = { pull.onPreFling(Velocity.Zero) }),
        ) {
            CardColumn(Modifier.fillMaxSize().pushDown { (bar * card.progress.value.coerceIn(0f, 1f)).roundToInt() }.verticalScroll(rememberScrollState()).padding(20.dp)) {
                content()
            }
            GlassIconButton(
                Icons.Filled.ExpandMore, "Close", close, backdrop,
                Modifier.align(Alignment.TopEnd).padding(12.dp).graphicsLayer { alpha = card.progress.value.coerceIn(0f, 1f) },
                size = app.plait.ui.ControlHeights.Medium, tint = cardGlass(), iconTint = LocalCardInk.current,
            )
        }
    }
}

/** What a card shows; [full] is the open card, which lists everything instead of the first few. */
@Composable
private fun CardContent(
    kind: FeedCardKind, phase: Phase, full: Boolean, data: DayData, tomorrow: List<CalEvent>, nowMs: Long, zone: ZoneId, aiText: String?,
    onOpenEvent: (String) -> Unit, onOpenNotes: (String) -> Unit, join: (CalEvent) -> Unit, onOpenHub: () -> Unit, onAllowCalendar: () -> Unit,
) {
    when (kind) {
        FeedCardKind.Meetings -> {
            CardHeader(phase, nowMs, zone)
            if (phase == Phase.Morning) MorningCard(data, nowMs, zone, aiText, onOpenEvent, join) else AfternoonCard(data, nowMs, phase, onOpenEvent, join)
            NextWeekOffer(nowMs, zone, data.stats.meetings)
            CalendarSetupCard(data, onOpenHub, onAllowCalendar)
        }
        FeedCardKind.Tasks -> AttentionContent(phase, nowMs, onOpenEvent, full)
        FeedCardKind.WeekRecap -> WeekRecapBlock(nowMs, zone)
        FeedCardKind.Day -> {
            CardHeader(phase, nowMs, zone)
            DayCard(data, phase, full, onOpenNotes)
            CalendarSetupCard(data, onOpenHub, onAllowCalendar)
        }
        FeedCardKind.Tomorrow -> {
            TomorrowCard(phase, tomorrow, zone, onOpenEvent)
            NextWeekOffer(nowMs, zone, data.stats.meetings)
        }
    }
}

/** Shown on every phase's card when there is nothing to read yet: no calendar connected, or none on the phone. */
@Composable
private fun CalendarSetupCard(data: DayData, onOpenHub: () -> Unit, onAllowCalendar: () -> Unit) {
    val ink = LocalCardInk.current
    if (!Calendars.connected) {
        Column(Modifier.fillMaxWidth().fauxGlass(RoundedCornerShape(26.dp), cardGlass()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No calendar connected yet", style = PlaitType.bodyStrong, color = ink)
            Text("Connect your calendars and your real day shows up here.", style = PlaitType.body, color = ink.copy(alpha = 0.8f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaitButton("Use my phone's calendars", onAllowCalendar, style = ButtonStyle.Ink, size = ButtonSize.Small)
                PlaitButton("More options", onOpenHub, style = ButtonStyle.InkSoft, size = ButtonSize.Small)
            }
        }
    } else if (data.events.isEmpty() && Calendars.deviceCals.isEmpty() && Calendars.feeds.isEmpty() && !Calendars.googleOn) {
        // Allowed to read calendars, but the phone has none: say so, instead of a card that claims a free day.
        Column(Modifier.fillMaxWidth().fauxGlass(RoundedCornerShape(26.dp), cardGlass()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("No calendars found on this phone", style = PlaitType.bodyStrong, color = ink)
            Text("Add an account, or paste a calendar link, and your real day shows up here.", style = PlaitType.body, color = ink.copy(alpha = 0.8f))
            PlaitButton("Add an account or link", onOpenHub, style = ButtonStyle.Ink, size = ButtonSize.Small)
        }
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
internal fun Headline(text: String) {
    val ink = LocalCardInk.current
    Text(text, style = PlaitType.headline.copy(fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold), color = ink)
}

@Composable
internal fun Body(text: String, maxLines: Int = Int.MAX_VALUE) {
    val ink = LocalCardInk.current
    Text(text, style = PlaitType.body.copy(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium), color = ink.copy(alpha = 0.85f), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun SectionLabel(text: String) {
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
    data: DayData, nowMs: Long, zone: ZoneId, aiText: String?,
    onOpenEvent: (String) -> Unit, join: (CalEvent) -> Unit,
) {
    val ink = LocalCardInk.current
    val n = data.stats.meetings
    Headline(if (n == 0) "A clear day." else "${DayAnalytics.word(n)} meeting${if (n == 1) "" else "s"} today.")
    Body(data.sense)
    if (n == 0) QuoteBlock(Phase.Morning, FeedCardKind.Meetings)
    if (data.day.summary.isNotBlank()) Body(aiText ?: data.day.summary)
    if (data.day.load.isNotEmpty()) DayStripOnCard(data.day, data.kinds, nowMs, zone)

    Calendars.message?.let { Text(it, style = PlaitType.caption, color = ink) }

    if (data.events.isNotEmpty()) {
        SectionLabel("Today in order")
        val next = data.events.firstOrNull { !it.allDay && it.endMs > nowMs }
        data.events.forEach { ev -> EventLine(ev, data, nowMs, { onOpenEvent(ev.id) }, join = if (ev.id == next?.id) join else null) }
    }
}

// ---------------------------------------------------------------------------------------------
// Midday: what is done, what is left
// ---------------------------------------------------------------------------------------------

@Composable
private fun AfternoonCard(data: DayData, nowMs: Long, phase: Phase, onOpenEvent: (String) -> Unit, join: (CalEvent) -> Unit) {
    val s = data.stats
    Headline(
        when {
            s.meetings == 0 -> "Nothing on today."
            s.left == 0 -> "That's the day."
            s.done == 0 -> "Nothing done yet."
            else -> "${s.done} done, ${s.left} to go."
        }
    )
    Body(data.sense)
    if (s.meetings == 0) QuoteBlock(phase, FeedCardKind.Meetings)
    val doneEvents = data.events.filter { !it.allDay && it.endMs in 1..nowMs }
    val ahead = data.events.filter { !it.allDay && it.endMs > nowMs }
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
// Evening: how the day went, then tomorrow
// ---------------------------------------------------------------------------------------------

@Composable
private fun DayCard(data: DayData, phase: Phase, full: Boolean, onOpenNotes: (String) -> Unit) {
    val ink = LocalCardInk.current
    val s = data.stats
    Headline(if (s.meetings == 0) "A quiet day." else "${DayAnalytics.span(s.minutesSoFar)} in meetings.")
    Body(DayAnalytics.recap(s))
    if (s.meetings == 0) QuoteBlock(phase, FeedCardKind.Day)

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
        val shown = if (full) s.missingNotes.size else 6
        s.missingNotes.take(shown).forEach { ev ->
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
        if (s.missingNotes.size > shown) Text("+${s.missingNotes.size - shown} more", style = PlaitType.caption, color = ink.copy(alpha = 0.7f))
    }
}

@Composable
private fun TomorrowCard(phase: Phase, tomorrow: List<CalEvent>, zone: ZoneId, onOpenEvent: (String) -> Unit) {
    val context = LocalContext.current
    SectionLabel("Tomorrow")
    if (tomorrow.isEmpty()) {
        Headline("Tomorrow is clear.")
        QuoteBlock(phase, FeedCardKind.Tomorrow)
    } else {
        val first = tomorrow.first()
        val title = tidyTitle(first.title)
        Headline("${DayAnalytics.word(tomorrow.size)} meeting${if (tomorrow.size == 1) "" else "s"} tomorrow.")
        Body("First is $title at ${first.time}.")
        PlaitButton("Open ${shortTitle(title)}", { onOpenEvent(first.id) }, style = ButtonStyle.InkSoft, size = ButtonSize.Small)
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
internal fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
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
