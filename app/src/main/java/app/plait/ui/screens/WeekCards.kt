package app.plait.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.plait.ai.Ai
import app.plait.data.NoteStore
import app.plait.data.brief.BriefBuilder
import app.plait.data.memo.MemoStore
import app.plait.data.week.BriefText
import app.plait.data.week.DayPreview
import app.plait.data.week.WeekAnalytics
import app.plait.data.week.WeekBriefs
import app.plait.data.week.WeekDay
import app.plait.data.week.WeekEvents
import app.plait.data.week.WeekPreviews
import app.plait.data.week.WeekStats
import app.plait.data.week.WorkWeek
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.theme.PlaitType
import kotlinx.coroutines.launch
import app.plait.R
import app.plait.i18n.AppLocale
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

// --- pure helpers ---------------------------------------------------------------------------------

private fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.FULL, AppLocale.locale)

internal fun dayOf(nowMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

/** Identifies a week for remembering what was opened. */
internal fun weekKey(days: List<LocalDate>): String = days.firstOrNull()?.toString().orEmpty()

/** (value, label) tiles of the recap; a tile whose value does not exist is left out. */
internal fun weekTiles(s: WeekStats): List<Pair<String, String>> = listOfNotNull(
    s.meetings.toString() to Txt.s(R.string.home_week_tile_meetings),
    WeekAnalytics.span(s.minutes) to Txt.s(R.string.home_week_tile_in_meetings),
    s.busiest?.let { dayName(it.date) to Txt.s(R.string.home_week_tile_busiest_day, WeekAnalytics.span(it.minutes)) },
    if (s.finishedMeetings > 0) Txt.s(R.string.common_n_of_m, s.notedMeetings, s.finishedMeetings) to Txt.s(R.string.home_week_tile_noted) else null,
    if (s.tasksDone + s.tasksOpen > 0) Txt.s(R.string.home_week_tile_done_value, s.tasksDone) to Txt.s(R.string.home_week_tile_tasks_open, s.tasksOpen) else null,
    s.longest?.let { WeekAnalytics.span((it.endMs - it.startMs) / 60_000) to Txt.s(R.string.home_tile_longest, Bidi.isolate(it.title)) },
)

/** Bar heights in dp for the strip: proportional to minutes, at least [minDp] for a day with meetings, 0 for none (drawn as a dot). */
internal fun barHeights(days: List<WeekDay>, maxDp: Float, minDp: Float = 6f): List<Float> {
    val top = days.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    return days.map { if (it.meetings == 0) 0f else maxOf(minDp, maxDp * it.minutes / top) }
}

// Arabic short day names are the full names, too wide for a bar's column: one letter there.
internal fun weekdayInitial(d: LocalDate): String =
    d.dayOfWeek.getDisplayName(if (AppLocale.isRtl) TextStyle.NARROW else TextStyle.SHORT, AppLocale.locale)

/** "3 meetings · first 9:00 AM Design review", or "Clear". */
internal fun previewDayLine(d: DayPreview, zone: ZoneId): String {
    val first = d.first
    if (d.meetings == 0 || first == null) return Txt.s(R.string.home_week_day_clear)
    return Txt.plural(R.plurals.home_week_day_line, d.meetings, d.meetings, Bidi.isolate(WeekBriefs.clock(first.startMs, zone)), Bidi.isolate(first.title.trim()))
}

// --- shared bits ----------------------------------------------------------------------------------

/** Weeks whose next-week brief was asked for, kept for the session (a page leaving the pager forgets its own state). */
private val openedWeeks = mutableStateListOf<String>()

@Composable
private fun rememberAiReady(): Boolean {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ready = Ai.status(context).ready }
    return ready
}

@Composable
private fun WrittenBy(by: String, canRefresh: Boolean, busy: Boolean, onRefresh: () -> Unit) {
    val ink = LocalCardInk.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.home_written_by, Bidi.isolate(by)), style = PlaitType.caption, color = ink.copy(alpha = 0.7f), modifier = Modifier.weight(1f, fill = false))
        if (canRefresh && !busy) Box(
            // clickable consumes the tap, so the card's press-to-open does not fire.
            Modifier.size(32.dp).clip(CircleShape).background(ink.copy(alpha = 0.12f)).clickable(role = Role.Button, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Refresh, stringResource(R.string.home_write_again_desc), tint = ink, modifier = Modifier.size(18.dp)) }
    }
}

// --- A: work-week recap ---------------------------------------------------------------------------

/** True on the last work day's evening card (the caller checks the phase). Reads [WorkWeek.startDay], so it follows a change. */
internal fun weekRecapDue(nowMs: Long, zone: ZoneId): Boolean = WorkWeek.isLastWorkDay(dayOf(nowMs, zone))

@Composable
internal fun WeekRecapBlock(nowMs: Long, zone: ZoneId) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noteStore = remember { NoteStore.get(context) }
    val memoStore = remember { MemoStore.get(context) }
    val noteCount = noteStore.notes.size
    val memoCount = memoStore.items.size
    val start = WorkWeek.startDay
    val stats = remember(nowMs / 60_000, noteCount, memoCount, start, app.plait.data.Calendars.events.hashCode()) {
        val days = WorkWeek.thisWorkWeek(dayOf(nowMs, zone), start)
        val events = WeekEvents.between(days.first(), days.last(), zone)
        val notes = noteStore.notes.toList()
        // Only the kind is needed: a full brief per meeting of the week cleaned every invite on the main thread.
        val kinds = events.associate { it.id to BriefBuilder.classify(it) }
        val noted = (notes.map { it.eventId } + memoStore.items.map { it.eventId }).toSet()
        val (done, open) = WeekEvents.checklistCounts(context, events)
        WeekAnalytics.compute(days, events, kinds, noted, done, open, nowMs, zone)
    }
    val firstName = app.plait.data.Profile.firstName().takeIf { it.isNotBlank() }
    val factsKey = WeekBriefs.recapFacts(stats, firstName)
    var brief by remember(factsKey) { mutableStateOf(BriefText(WeekBriefs.recapRules(stats, firstName), null)) }
    var busy by remember(factsKey) { mutableStateOf(false) }
    // Keyed on the facts: runs once per change, and is cheap when the text is cached.
    LaunchedEffect(factsKey) { brief = WeekBriefs.recap(context, stats, firstName) }
    val aiReady = rememberAiReady()

    SectionLabel(stringResource(R.string.home_week_your_work_week))
    Headline(stringResource(R.string.home_week_done))
    Body(brief.text)
    brief.by?.let { by ->
        WrittenBy(by, aiReady, busy) {
            busy = true
            scope.launch { brief = WeekBriefs.recap(context, stats, firstName, force = true); busy = false }
        }
    }
    if (stats.meetings == 0) return
    val ink = LocalCardInk.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        weekTiles(stats).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (v, l) -> StatTile(v, l, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
    val heights = barHeights(stats.perDay, 52f)
    Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.perDay.forEachIndexed { i, d ->
            val strong = d.date == stats.busiest?.date
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val h: Dp = if (heights[i] == 0f) 6.dp else heights[i].dp
                    Box(
                        Modifier.width(if (heights[i] == 0f) 6.dp else 22.dp).height(h)
                            .clip(if (heights[i] == 0f) CircleShape else RoundedCornerShape(6.dp))
                            .background(if (strong) ink else ink.copy(alpha = 0.35f)),
                    )
                }
                Text(weekdayInitial(d.date), style = PlaitType.caption, color = ink.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }
    SectionLabel(stringResource(R.string.common_today))
}

// --- B: next-week offer ---------------------------------------------------------------------------

/** Days off with an empty calendar: offers a look at next week; nothing is generated until the tap. */
@Composable
internal fun NextWeekOffer(nowMs: Long, zone: ZoneId, meetingsToday: Int) {
    val start = WorkWeek.startDay
    val today = dayOf(nowMs, zone)
    if (WorkWeek.isWorkDay(today, start) || meetingsToday > 0) return
    val days = remember(today, start) { WorkWeek.nextWorkWeek(today, start) }
    val key = weekKey(days)
    if (key !in openedWeeks) {
        PlaitButton(stringResource(R.string.home_week_brief_next), { openedWeeks.add(key) }, style = ButtonStyle.Ink, size = ButtonSize.Small, icon = Icons.Filled.AutoAwesome)
        return
    }
    NextWeekBrief(days, nowMs, zone)
}

@Composable
private fun NextWeekBrief(days: List<LocalDate>, nowMs: Long, zone: ZoneId) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ink = LocalCardInk.current
    val noteStore = remember { NoteStore.get(context) }
    val noteCount = noteStore.notes.size
    val preview = remember(nowMs / 60_000, noteCount, days, app.plait.data.Calendars.events.hashCode()) {
        val events = WeekEvents.between(days.first(), days.last(), zone)
        WeekPreviews.compute(days, events, events.associate { it.id to BriefBuilder.classify(it) }, zone)
    }
    val firstName = app.plait.data.Profile.firstName().takeIf { it.isNotBlank() }
    val factsKey = WeekBriefs.previewFacts(preview, firstName, zone)
    // The rules text is instant; the AI text replaces it when it arrives.
    var brief by remember(factsKey) { mutableStateOf(BriefText(WeekBriefs.previewRules(preview, firstName, zone), null)) }
    var busy by remember(factsKey) { mutableStateOf(false) }
    LaunchedEffect(factsKey) { brief = WeekBriefs.preview(context, preview, firstName, zone) }
    val aiReady = rememberAiReady()

    Body(brief.text)
    if (preview.meetings > 0) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        preview.perDay.forEach { d ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(dayName(d.date), style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = ink, modifier = Modifier.width(96.dp), maxLines = 1)
                Text(previewDayLine(d, zone), style = PlaitType.body, color = ink.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
    }
    brief.by?.let { by ->
        WrittenBy(by, aiReady, busy) {
            busy = true
            scope.launch { brief = WeekBriefs.preview(context, preview, firstName, zone, force = true); busy = false }
        }
    }
}
