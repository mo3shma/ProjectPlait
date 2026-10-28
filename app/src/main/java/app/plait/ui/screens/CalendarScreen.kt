package app.plait.ui.screens

import app.plait.ui.popupSurface
import app.plait.ui.plaitCard
import app.plait.ui.fauxGlass
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import app.plait.data.Source
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.plait.data.brief.BriefBuilder
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import app.plait.data.Agenda
import app.plait.data.CalEvent
import app.plait.ui.CircleIconButton
import app.plait.ui.glass.GlassTab
import app.plait.ui.glass.GlassTabBar
import app.plait.ui.glass.TabContainer
import app.plait.ui.pressable
import app.plait.ui.rise
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import com.kyant.backdrop.Backdrop
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.userText
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.drawscope.scale
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class CalView(@androidx.annotation.StringRes val label: Int) { Day(R.string.cal_view_day), Month(R.string.cal_view_month) }

/**
 * Which day, week or month is on screen, and which day (if any) is open in the pane.
 * Hoisted so the glass controls and the content share it.
 */
@Stable
class CalendarState(today: LocalDate) {
    var view by mutableStateOf(CalView.Month)
    var anchor by mutableStateOf(today)
    var selected by mutableStateOf<LocalDate?>(null)
    var direction by mutableIntStateOf(1)

    // The phone's region decides the first day (AppLocale keeps it in the default locale); AppLocale.locale has no region in English.
    val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    private fun shift(d: LocalDate, delta: Int): LocalDate = when (view) {
        CalView.Day -> d.plusDays(delta.toLong())
        CalView.Month -> d.plusMonths(delta.toLong())
    }

    fun step(delta: Int) {
        direction = if (delta >= 0) 1 else -1
        anchor = shift(anchor, delta)
        selected = selected?.let { shift(it, delta) }
    }

    /** Opens a day in the pane. In the day view there is no pane, so this just goes to that day. */
    fun select(date: LocalDate) {
        when (view) {
            CalView.Month -> if (YearMonth.from(date) != YearMonth.from(anchor)) {
                direction = if (date.isAfter(anchor)) 1 else -1
                anchor = date
            }
            CalView.Day -> {
                direction = if (date.isAfter(anchor)) 1 else -1
                anchor = date
                return
            }
        }
        selected = date
    }

    fun close() {
        selected = null
    }

    fun goToday(today: LocalDate) {
        direction = if (today.isAfter(anchor)) 1 else -1
        anchor = today
        if (selected != null) selected = today
    }

    fun switchTo(v: CalView) {
        if (v == view) return
        val keep = selected ?: anchor
        anchor = keep
        if (v != CalView.Month) selected = null
        view = v
    }
}

private val HOUR_HEIGHT = 56.dp

private fun headline(state: CalendarState): Pair<String, String> {
    val loc = AppLocale.locale
    return when (state.view) {
        CalView.Month -> state.anchor.year.toString() to state.anchor.month.getDisplayName(TextStyle.FULL, loc)
        CalView.Day -> state.anchor.dayOfWeek.getDisplayName(TextStyle.FULL, loc) to dayMonth(state.anchor)
    }
}

/** "5 October" (Arabic: "5 أكتوبر"). */
private fun dayMonth(d: LocalDate): String = d.format(app.plait.data.TimeFmt.fmt("d MMMM"))

/**
 * The Calendar tab. Day is a list of rows; Week and Month are grids. Tapping a day in a grid
 * slides a pane up over the bottom half while the calendar shrinks into the top half.
 */
@Composable
fun CalendarContent(
    state: CalendarState,
    events: List<CalEvent>,
    nowMs: Long,
    zone: ZoneId,
    backdrop: Backdrop,
    onOpenEvent: (String) -> Unit,
) {
    val today = remember(nowMs / 60_000) { Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate() }
    // The grid is laid out once and never resized. Tapping a day opens a sheet above it, so selecting a
    // day costs one small overlay, not a re-measure of 42 cells.
    val sheetOpen = state.selected != null && state.view == CalView.Month
    BackHandler(enabled = sheetOpen) { state.close() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            CalendarHeader(state, backdrop, onToday = { state.goToday(today) })
            // Months are pages: they follow the finger, fling and snap like any pager, both ways, with the next month alongside.
            val center = 1200
            val baseMonth = remember { YearMonth.from(state.anchor) }
            val months = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = center + monthsBetween(baseMonth, YearMonth.from(state.anchor)),
            ) { center * 2 }
            LaunchedEffect(months) {
                snapshotFlow { months.currentPage }.collect { page ->
                    val ym = baseMonth.plusMonths((page - center).toLong())
                    val now = YearMonth.from(state.anchor)
                    if (ym != now) {
                        state.direction = if (ym.isAfter(now)) 1 else -1
                        state.anchor = ym.atDay(minOf(state.anchor.dayOfMonth, ym.lengthOfMonth()))
                        state.selected = null
                    }
                }
            }
            LaunchedEffect(state.anchor, state.view) {
                if (state.view != CalView.Month) return@LaunchedEffect
                val target = center + monthsBetween(baseMonth, YearMonth.from(state.anchor))
                if (months.currentPage != target && !months.isScrollInProgress) months.animateScrollToPage(target)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 112.dp)
                    // A swipe to the right from the left edge would otherwise be Android's Back gesture, which made going back a
                    // month hard. While a day's events are open, Back must work, so the edge is left to the system then.
                    .then(if (sheetOpen) Modifier else Modifier.systemGestureExclusion()),
            ) {
                when (state.view) {
                    CalView.Day -> DayView(state, events, nowMs, zone, today, onOpenEvent)
                    CalView.Month -> androidx.compose.foundation.pager.HorizontalPager(
                        state = months,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                            state = months,
                            snapPositionalThreshold = 0.2f,
                            snapAnimationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f),
                        ),
                        key = { it },
                    ) { page ->
                        val ym = baseMonth.plusMonths((page - center).toLong())
                        MonthView(state, ym.atDay(1), events, nowMs, zone, today)
                    }
                }
            }
        }
        DaySheet(state, events, nowMs, zone, onOpenEvent)
    }
}

@Composable
private fun CalendarHeader(state: CalendarState, backdrop: Backdrop, onToday: () -> Unit) {
    val (kicker, title) = headline(state)
    val labels = CalView.entries.map { stringResource(it.label) }
    val tabs = remember(labels) { labels.map { GlassTab(it) } }
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp).padding(top = 76.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(kicker, style = PlaitType.monoLabel, color = plait.muted)
            PlaitButton(stringResource(R.string.common_today), onToday, style = ButtonStyle.Quiet, size = ButtonSize.Small)
        }
        Spacer(Modifier.height(4.dp))
        androidx.compose.animation.AnimatedContent(
            targetState = title,
            transitionSpec = {
                val up = state.direction >= 0
                (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)) { if (up) it / 2 else -it / 2 } +
                    androidx.compose.animation.fadeIn(androidx.compose.animation.core.spring(stiffness = 1600f))) togetherWith
                    (androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)) { if (up) -it / 2 else it / 2 } +
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.spring(stiffness = 1600f)))
            },
            label = "month-title",
        ) { t ->
            Text(
                t,
                // The month is always written out in full; a long one steps the size down instead of being cut.
                style = PlaitType.dateTitle.copy(fontSize = if (t.length > 11) 36.sp else 44.sp, lineHeight = if (t.length > 11) 40.sp else 46.sp),
                color = plait.text, maxLines = 2,
            )
        }
        Spacer(Modifier.height(12.dp))
        GlassTabBar(
            tabs = tabs,
            selected = state.view.ordinal,
            onSelect = { state.switchTo(CalView.entries[it]) },
            backdrop = backdrop,
            container = TabContainer.Glass,
            height = 52.dp,
            selectedColor = plait.text,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EmptyDay(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().plaitCard(RoundedCornerShape(28.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.cal_nothing_planned), style = PlaitType.cardTitle.copy(fontSize = 22.sp), color = plait.text)
        Text(stringResource(R.string.cal_free_day_body), style = PlaitType.body, color = plait.muted)
    }
}

// ---------------------------------------------------------------------------------------------
// Day: a day wheel beside the chosen day's timeline
// ---------------------------------------------------------------------------------------------

/**
 * The chosen day always sits in the middle of a vertical wheel. Drag the wheel up or down to roll to
 * another day (it coasts and settles on a day, with a tick as each day passes); tap a day to jump to it.
 * The chosen day's hours are on the right, so events are readable instead of squeezed into seven columns.
 */
@Composable
private fun DayView(
    state: CalendarState, events: List<CalEvent>, nowMs: Long, zone: ZoneId, today: LocalDate, onOpenEvent: (String) -> Unit,
) {
    val date = state.anchor
    val dayEvents = remember(events, date) { Agenda.eventsOn(events, date, zone) }
    val counts = remember(events, zone) {
        val m = HashMap<Long, Int>()
        for (e in events) {
            if (e.startMs <= 0) continue
            var d = Instant.ofEpochMilli(e.startMs).atZone(zone).toLocalDate().toEpochDay()
            val last = Instant.ofEpochMilli((e.endMs - 1).coerceAtLeast(e.startMs)).atZone(zone).toLocalDate().toEpochDay()
            var n = 0
            while (d <= last && n++ < 3) { m[d] = (m[d] ?: 0) + 1; d++ }
        }
        m
    }
    Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DayWheel(date, today, counts, { state.select(it) }, Modifier.width(84.dp).fillMaxHeight())
        DayTimeline(date, dayEvents, nowMs, zone, date == today, onOpenEvent, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun DayWheel(selected: LocalDate, today: LocalDate, counts: Map<Long, Int>, onSelect: (LocalDate) -> Unit, modifier: Modifier) {
    val density = LocalDensity.current
    val rowPx = with(density) { WHEEL_ROW.toPx() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pos = remember { Animatable(selected.toEpochDay().toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val center by remember { derivedStateOf { pos.value.roundToInt() } }

    // Moved from outside (arrows elsewhere, Today button, a tap): roll the wheel to the new day.
    LaunchedEffect(selected) {
        val target = selected.toEpochDay().toFloat()
        if (!dragging && abs(pos.value - target) > 0.001f) pos.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    LaunchedEffect(Unit) {
        snapshotFlow { center }.drop(1).collect { if (dragging) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    val drag = rememberDraggableState { delta -> scope.launch { pos.snapTo(pos.value - delta / rowPx) } }
    val radius = rowPx / (WHEEL_STEP_DEG * (PI.toFloat() / 180f))
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier.fauxGlass(shape)
            .draggable(
                state = drag, orientation = Orientation.Vertical,
                onDragStarted = { dragging = true; scope.launch { pos.stop() } },
                onDragStopped = { v ->
                    scope.launch {
                        val target = (pos.value - v / rowPx * 0.16f).roundToInt()
                        pos.animateTo(target.toFloat(), spring(dampingRatio = 0.78f, stiffness = 170f))
                        dragging = false
                        onSelect(LocalDate.ofEpochDay(target.toLong()))
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(WHEEL_ROW).padding(horizontal = 6.dp).clip(RoundedCornerShape(24.dp)).background(plait.accent.copy(alpha = 0.18f)))
        Text(
            LocalDate.ofEpochDay(center.toLong()).month.getDisplayName(TextStyle.FULL, AppLocale.locale),
            style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = plait.accent, maxLines = 1, softWrap = false,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        )
        for (k in -WHEEL_REACH..WHEEL_REACH) {
            val day = LocalDate.ofEpochDay((center + k).toLong())
            val count = counts[day.toEpochDay()] ?: 0
            Column(
                Modifier.fillMaxWidth().height(WHEEL_ROW)
                    .graphicsLayer {
                        val off = (center + k) - pos.value
                        val angle = (off * WHEEL_STEP_DEG).coerceIn(-85f, 85f)
                        val rad = angle * (PI.toFloat() / 180f)
                        translationY = radius * sin(rad)
                        rotationX = -angle
                        cameraDistance = 14f * density.density
                        val a = abs(off)
                        alpha = (1f - a * 0.26f).coerceIn(0f, 1f)
                        val sc = (1f - a * 0.07f).coerceIn(0.7f, 1f)
                        scaleX = sc; scaleY = sc
                    }
                    .pointerInput(day) { detectTapGestures { onSelect(day) } },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, AppLocale.locale).uppercase(AppLocale.locale),
                    style = PlaitType.monoSmall.copy(fontSize = 12.sp), color = if (day == today) plait.accent else plait.text.copy(alpha = 0.7f),
                )
                Text(day.dayOfMonth.toString(), style = PlaitType.cardHeadline.copy(fontSize = 28.sp, lineHeight = 30.sp), color = plait.text)
                Box(Modifier.padding(top = 3.dp).size(5.dp).background(if (count > 0) plait.accent else Color.Transparent, CircleShape))
            }
        }
    }
}

@Composable
private fun DayTimeline(
    date: LocalDate, events: List<CalEvent>, nowMs: Long, zone: ZoneId, isToday: Boolean,
    onOpenEvent: (String) -> Unit, modifier: Modifier,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState(with(density) { (HOUR_HEIGHT * 7f).roundToPx() })
    val dayStart = remember(date) { date.atStartOfDay(zone).toInstant().toEpochMilli() }
    val timed = remember(events) { events.filter { !it.allDay } }
    val placed = remember(timed) { Agenda.pack(timed) }
    val allDay = remember(events) { events.filter { it.allDay } }
    val line = plait.text.copy(alpha = 0.08f)
    val gutter = 36.dp

    Column(modifier) {
        if (allDay.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                allDay.take(3).forEach { e ->
                    Text(
                        e.title, style = PlaitType.label.copy(fontSize = 13.sp).userText(), color = Color(0xFF0E1316), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(e.source.color().copy(alpha = 0.9f))
                            .pressable({ onOpenEvent(e.id) }).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        if (events.isEmpty()) Text(stringResource(R.string.cal_nothing_planned), style = PlaitType.caption, color = plait.muted, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Box(
                Modifier.fillMaxWidth().height(HOUR_HEIGHT * 24)
                    .drawBehind {
                        val h = HOUR_HEIGHT.toPx()
                        // The hour labels sit in the start gutter: the left in English, the right in Arabic.
                        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                        val x0 = if (rtl) 0f else gutter.toPx()
                        val x1 = if (rtl) size.width - gutter.toPx() else size.width
                        repeat(24) { i -> drawLine(line, Offset(x0, i * h), Offset(x1, i * h), strokeWidth = 1f) }
                    },
            ) {
                repeat(24) { h ->
                    if (h > 0) Text(
                        app.plait.data.TimeFmt.hour(h), style = PlaitType.monoSmall.copy(fontSize = 10.sp), color = plait.muted,
                        modifier = Modifier.offset(y = HOUR_HEIGHT * h - 7.dp),
                    )
                }
                placed.forEach { p ->
                    val e = p.event
                    val startMin = ((e.startMs - dayStart) / 60_000f).coerceIn(0f, 1440f)
                    val endMin = ((e.endMs - dayStart) / 60_000f).coerceIn(0f, 1440f)
                    val top = HOUR_HEIGHT * (startMin / 60f)
                    val height = (HOUR_HEIGHT * ((endMin - startMin) / 60f)).coerceAtLeast(30.dp)
                    val tone = remember(e.id) { BriefBuilder.classify(e).tone() }
                    Row(Modifier.fillMaxWidth().padding(start = gutter).offset(y = top).height(height - 2.dp)) {
                        repeat(p.columns) { c ->
                            if (c == p.column) {
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().padding(horizontal = 1.dp).clip(RoundedCornerShape(14.dp))
                                        .graphicsLayer { alpha = if (Agenda.isPast(e, nowMs)) 0.6f else 1f }
                                        .background(tone.bg).pressable({ onOpenEvent(e.id) }).padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(e.title, style = PlaitType.label.copy(fontSize = 13.sp, lineHeight = 16.sp).userText(), color = tone.ink, maxLines = if (height >= 64.dp) 2 else 1, overflow = TextOverflow.Ellipsis)
                                    Text(e.time + if (e.endLabel.isNotBlank()) " – ${e.endLabel}" else "", style = PlaitType.monoSmall.copy(fontSize = 10.sp), color = tone.ink.copy(alpha = 0.7f), maxLines = 1)
                                }
                            } else Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (isToday) {
                    val minutes = ((nowMs - dayStart) / 60_000f).coerceIn(0f, 1440f)
                    Box(Modifier.padding(start = gutter).offset(y = HOUR_HEIGHT * (minutes / 60f)).fillMaxWidth().height(2.dp).background(plait.signal))
                }
            }
        }
    }
}

private val WHEEL_ROW = 68.dp
private const val WHEEL_REACH = 4
private const val WHEEL_STEP_DEG = 26f

// ---------------------------------------------------------------------------------------------
// Month: grid
// ---------------------------------------------------------------------------------------------

@Composable
private fun MonthView(
    state: CalendarState, anchor: LocalDate, events: List<CalEvent>, nowMs: Long, zone: ZoneId, today: LocalDate,
) {
    val cells = remember(anchor, state.firstDay) { Agenda.monthGrid(anchor, state.firstDay) }
    val month = YearMonth.from(anchor)
    val rows = cells.size / 7

    // Events per day are sorted out off the main thread, so the grid is on screen straight away and the dots
    // appear a moment later.
    val byDay by produceState<Map<LocalDate, List<CalEvent>>>(emptyMap(), cells, events) {
        value = withContext(Dispatchers.Default) { Agenda.byDay(events, cells, zone) }
    }

    // The whole month is drawn on one canvas: no node per cell, no layout work when a day is picked. The text
    // is measured once per month here and only painted afterwards.
    val measurer = rememberTextMeasurer()
    val text = plait.text
    val muted = plait.muted
    val accent = plait.accent
    val onAccent = plait.onAccent
    val surface = plait.surface
    val glassCells = !app.plait.ui.glass.LocalGlassEnv.current.material
    val dark = app.plait.ui.theme.LocalIsDark.current
    val sourceColors = Source.entries.associateWith { it.color() }
    val dayStyle = PlaitType.label.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val numbers = remember(cells, dayStyle) { cells.map { measurer.measure(it.dayOfMonth.toString(), dayStyle) } }
    val headers = remember(state.firstDay) {
        // English short names are three letters; Arabic ones are the whole name, never cut, and shrunk below if a column is too narrow.
        List(7) { measurer.measure(state.firstDay.plus(it.toLong()).getDisplayName(TextStyle.SHORT, AppLocale.locale).let { n -> if (AppLocale.isRtl) n else n.take(3) }, PlaitType.monoSmall) }
    }
    var pressed by remember { mutableIntStateOf(-1) }
    val density = LocalDensity.current
    // The grid is painted by hand, so it is mirrored by hand: the week runs right to left in Arabic.
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val monthDesc = stringResource(R.string.cal_month_desc)
    val headerH = with(density) { 30.dp.toPx() }
    val pad = with(density) { 2.dp.toPx() }

    Canvas(
        Modifier.fillMaxSize().padding(horizontal = 8.dp)
            .semantics { contentDescription = monthDesc }
            .pointerInput(cells, rtl) {
                detectTapGestures(
                    onPress = { p ->
                        val i = cellAt(p, size.width.toFloat(), size.height.toFloat(), headerH, rows, rtl)
                        pressed = i
                        tryAwaitRelease()
                        pressed = -1
                    },
                    onTap = { p ->
                        val i = cellAt(p, size.width.toFloat(), size.height.toFloat(), headerH, rows, rtl)
                        if (i in cells.indices) state.select(cells[i])
                    },
                )
            },
    ) {
        val cw = size.width / 7f
        val ch = (size.height - headerH) / rows
        for (c in 0 until 7) {
            val l = headers[c]
            val cx = (if (rtl) 6 - c else c) * cw + cw / 2f
            val cy = headerH / 2f
            val k = ((cw - 2 * pad) / l.size.width).coerceAtMost(1f)
            scale(k, k, Offset(cx, cy)) { drawText(l, muted, Offset(cx - l.size.width / 2f, cy - l.size.height / 2f)) }
        }
        val selected = state.selected
        for (i in cells.indices) {
            val d = cells[i]
            val col = if (rtl) 6 - i % 7 else i % 7
            val row = i / 7
            val x = col * cw + pad
            val y = headerH + row * ch + pad
            val w = cw - 2 * pad
            val h = ch - 2 * pad
            val inMonth = YearMonth.from(d) == month
            val isSel = d == selected
            val corner = CornerRadius(14.dp.toPx())
            val fill = when {
                i == pressed -> accent.copy(alpha = 0.30f)
                isSel -> accent.copy(alpha = 0.22f)
                else -> surface.copy(alpha = if (inMonth) 0.55f else 0.22f)
            }
            drawRoundRect(fill, Offset(x, y), Size(w, h), corner)
            if (glassCells) {
                // A glass cell: a light sheen across the top and a bright rim.
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.35f), Color.Transparent), startY = y, endY = y + h * 0.5f), Offset(x, y), Size(w, h), corner)
                drawRoundRect(Color.White.copy(alpha = if (dark) 0.22f else 0.6f), Offset(x, y), Size(w, h), corner, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            val num = numbers[i]
            val isToday = d == today
            val cx = x + w / 2f
            val topY = y + 5.dp.toPx()
            if (isToday) drawCircle(accent, 13.dp.toPx(), Offset(cx, topY + 13.dp.toPx()))
            drawText(
                num, if (isToday) onAccent else if (inMonth) text else muted,
                Offset(cx - num.size.width / 2f, topY + 13.dp.toPx() - num.size.height / 2f),
            )
            val list = byDay[d]
            if (!list.isNullOrEmpty()) {
                val dots = minOf(list.size, 3)
                val r = 3.dp.toPx()
                val gap = 4.dp.toPx()
                val total = dots * 2 * r + (dots - 1) * gap
                var dx = cx - total / 2f + r
                val dy = topY + 34.dp.toPx()
                for (k in 0 until dots) {
                    val e = list[k]
                    drawCircle(sourceColors.getValue(e.source).copy(alpha = if (Agenda.isPast(e, nowMs)) 0.45f else 1f), r, Offset(dx, dy))
                    dx += 2 * r + gap
                }
            }
        }
    }
}

/** Which cell of the month grid a touch at [p] lands on, or -1. */
internal fun cellAt(p: Offset, width: Float, height: Float, headerH: Float, rows: Int, rtl: Boolean = false): Int {
    if (p.y < headerH) return -1
    val col = (p.x / (width / 7f)).toInt().coerceIn(0, 6).let { if (rtl) 6 - it else it }
    val row = ((p.y - headerH) / ((height - headerH) / rows)).toInt().coerceIn(0, rows - 1)
    return row * 7 + col
}

// ---------------------------------------------------------------------------------------------
// The day sheet
// ---------------------------------------------------------------------------------------------

/**
 * Tapping a day in the month opens this sheet over the grid: that day's events, each one a tap away from its
 * page. It slides with a translation, so the grid underneath is never re-laid-out. Back or the scrim closes it,
 * and coming back from an event page finds it still open.
 */
@Composable
private fun DaySheet(state: CalendarState, events: List<CalEvent>, nowMs: Long, zone: ZoneId, onOpenEvent: (String) -> Unit) {
    val open = state.selected != null && state.view == CalView.Month
    val progress = remember { Animatable(if (open) 1f else 0f) }
    LaunchedEffect(open) { progress.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 520f, visibilityThreshold = 0.004f)) }
    var shown by remember { mutableStateOf(state.selected) }
    state.selected?.let { shown = it }
    val date = shown
    val visible by remember { derivedStateOf { progress.value > 0.004f } }
    if (date == null || !visible) return

    Box(Modifier.fillMaxSize()) {
        app.plait.ui.DimScrim(open, app.plait.ui.Dim.Sheet, { state.close() })
        val shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.62f)
                .graphicsLayer { translationY = (1f - progress.value) * size.height }
                .popupSurface(shape)
                .clickable(interactionSource = null, indication = null) {},
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .pointerInput(date) {
                        var dy = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dy = 0f },
                            onDragEnd = { if (dy > 60.dp.toPx()) state.close() },
                            onVerticalDrag = { _, d -> dy += d },
                        )
                    }
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.width(40.dp).height(5.dp).background(plait.muted.copy(alpha = 0.5f), CircleShape)) }
            val list = remember(events, date) { Agenda.eventsOn(events, date, zone) }
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, AppLocale.locale), style = PlaitType.monoLabel, color = plait.muted)
                    Text(
                        dayMonth(date),
                        style = PlaitType.section.copy(fontSize = 26.sp, lineHeight = 30.sp), color = plait.text, maxLines = 1,
                    )
                    Text(if (list.isEmpty()) stringResource(R.string.cal_nothing_planned) else pluralStringResource(R.plurals.cal_events, list.size, list.size), style = PlaitType.caption, color = plait.muted)
                }
                CircleIconButton(Icons.AutoMirrored.Filled.NavigateBefore, stringResource(R.string.cal_previous_day), { state.select(date.minusDays(1)) }, plait.surface, plait.text, size = 44.dp)
                CircleIconButton(Icons.AutoMirrored.Filled.NavigateNext, stringResource(R.string.cal_next_day), { state.select(date.plusDays(1)) }, plait.surface, plait.text, size = 44.dp)
            }
            if (list.isEmpty()) {
                EmptyDay(Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { ev -> EventRow(ev, onClick = { onOpenEvent(ev.id) }, past = Agenda.isPast(ev, nowMs)) }
                }
            }
        }
    }
}

private fun monthsBetween(a: YearMonth, b: YearMonth): Int = ((b.year - a.year) * 12 + (b.monthValue - a.monthValue))
