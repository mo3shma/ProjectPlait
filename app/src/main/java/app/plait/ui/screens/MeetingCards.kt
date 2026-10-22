package app.plait.ui.screens

import app.plait.ui.plaitCard
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.plait.ui.ButtonStyle
import app.plait.ui.ButtonSize
import app.plait.ui.PlaitButton
import androidx.compose.material.icons.filled.Reply
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.data.Agenda
import app.plait.data.CalEvent
import app.plait.data.Guest
import app.plait.data.Rsvp
import app.plait.data.brief.MeetingBrief
import app.plait.data.brief.MeetingKind
import app.plait.ui.PulseRing
import app.plait.ui.pressable
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait

/** Fill and ink for a card. Colour tells you the kind of meeting at a glance. */
class KindTone(val bg: Color, val ink: Color)

private val Ink = Color(0xFF0E1316)

fun MeetingKind.tone(): KindTone = KindTone(
    when (this) {
        MeetingKind.OneOnOne -> Color(0xFFD9C2FF)
        MeetingKind.Standup -> Color(0xFFA8E6CF)
        MeetingKind.Review -> Color(0xFFB7C3FF)
        MeetingKind.Planning -> Color(0xFFFFD98A)
        MeetingKind.Interview -> Color(0xFFFFB3A3)
        MeetingKind.External -> Color(0xFF9ED8FF)
        MeetingKind.Social -> Color(0xFFFFB8D6)
        MeetingKind.Personal -> Color(0xFFD3E6A8)
        MeetingKind.Focus -> Color(0xFFC8D3E0)
        MeetingKind.General -> Color(0xFFE6DAC3)
    },
    Ink,
)

private val avatarPalette = listOf(
    Color(0xFFB7C3FF), Color(0xFFA8E6CF), Color(0xFFFFD98A), Color(0xFFFFB3A3), Color(0xFFD9C2FF), Color(0xFF9ED8FF), Color(0xFFFFB8D6),
)

fun avatarColor(g: Guest): Color = avatarPalette[Math.floorMod((g.email ?: g.name).hashCode(), avatarPalette.size)]

@Composable
internal fun KindPill(kind: MeetingKind, tone: KindTone) {
    Box(
        Modifier.height(26.dp).clip(CircleShape).background(tone.ink).padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) { Text(kind.label, style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = tone.bg) }
}

@Composable
private fun SoftChip(text: String, tone: KindTone) {
    Box(
        Modifier.height(26.dp).clip(CircleShape).background(tone.ink.copy(alpha = 0.12f)).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = tone.ink) }
}

/** Overlapping initials for the people in the meeting, you excluded. */
@Composable
internal fun GuestStack(guests: List<Guest>, tone: KindTone, max: Int = 4) {
    val others = guests.filter { !it.self }
    if (others.isEmpty()) return
    val shown = others.take(max)
    Row(horizontalArrangement = Arrangement.spacedBy((-9).dp), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { g ->
            Box(
                Modifier.size(32.dp).border(2.dp, tone.bg, CircleShape).clip(CircleShape).background(tone.ink.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center,
            ) { Text(g.initials, style = PlaitType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = tone.bg) }
        }
        if (others.size > shown.size) {
            Box(
                Modifier.size(32.dp).border(2.dp, tone.bg, CircleShape).clip(CircleShape).background(tone.ink.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Text("+${others.size - shown.size}", style = PlaitType.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold), color = tone.ink) }
        }
    }
}

/**
 * The Today card for a meeting: coloured by kind, with the reason for meeting, who is coming, and
 * (for the next one) a countdown and a join button. [hero] is the large "Up next" version.
 */
@Composable
fun MeetingCard(
    ev: CalEvent,
    brief: MeetingBrief,
    nowMs: Long,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    past: Boolean = false,
    onOpen: () -> Unit,
    onJoin: (() -> Unit)? = null,
    contextLine: String? = null,
) {
    val tone = brief.kind.tone()
    val ink = tone.ink
    val shape = RoundedCornerShape(if (hero) 36.dp else 30.dp)
    val withLine = brief.withLine
    val hasCall = ev.callLink != null

    Box(
        modifier.fillMaxWidth().graphicsLayer { alpha = if (past) 0.6f else 1f }
            .clip(shape).background(tone.bg).pressable(onOpen),
    ) {
        if (hero) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 52.dp, y = (-66).dp)
                    .size(220.dp).background(Color.White.copy(alpha = 0.28f), CircleShape)
            )
        }
        Column(Modifier.padding(if (hero) 24.dp else 20.dp)) {
            if (hero) HeroTop(ev, brief, nowMs, tone) else CompactTop(ev, brief, tone, hasCall)

            Spacer(Modifier.height(if (hero) 18.dp else 10.dp))
            Text(
                ev.title,
                style = if (hero) PlaitType.cardTitle else PlaitType.cardTitle.copy(fontSize = 21.sp, lineHeight = 24.sp),
                color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                (if (ev.endLabel.isNotBlank()) "${ev.time} – ${ev.endLabel}" else ev.time) + "  ·  ${ev.place}",
                style = PlaitType.caption.copy(fontWeight = FontWeight.Medium), color = ink.copy(alpha = 0.72f),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(if (hero) 16.dp else 10.dp))
            if (hero) Text("Why you're meeting", style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = ink.copy(alpha = 0.6f))
            Text(
                brief.about,
                style = PlaitType.body.copy(fontSize = if (hero) 16.sp else 14.sp, lineHeight = if (hero) 22.sp else 20.sp),
                color = ink.copy(alpha = 0.88f),
                fontStyle = if (brief.aboutIsGuess) FontStyle.Italic else FontStyle.Normal,
                maxLines = if (hero) 4 else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (hero) 2.dp else 0.dp),
            )
            if (hero && brief.agenda.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                brief.agenda.take(3).forEach { item ->
                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.padding(top = 8.dp).size(5.dp).background(ink.copy(alpha = 0.6f), CircleShape))
                        Text(item, style = PlaitType.body.copy(fontSize = 14.sp, lineHeight = 19.sp), color = ink.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (brief.agenda.size > 3) Text("+${brief.agenda.size - 3} more", style = PlaitType.monoSmall, color = ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 3.dp, start = 13.dp))
            }

            if (contextLine != null) {
                Spacer(Modifier.height(if (hero) 14.dp else 8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Filled.Email, null, tint = ink.copy(alpha = 0.75f), modifier = Modifier.padding(top = 2.dp).size(16.dp))
                    Column {
                        Text("From your emails", style = PlaitType.monoSmall.copy(fontSize = 10.sp), color = ink.copy(alpha = 0.6f))
                        Text(contextLine, style = PlaitType.body.copy(fontSize = 13.sp, lineHeight = 18.sp), color = ink.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            val showGuests = ev.guests.any { !it.self }
            if (showGuests || (hero && onJoin != null)) {
                Spacer(Modifier.height(if (hero) 18.dp else 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GuestStack(ev.guests, tone)
                        if (showGuests) Column(Modifier.weight(1f, fill = false)) {
                            Text(withLine, style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (hero && brief.rsvpLine.isNotBlank()) Text(brief.rsvpLine, style = PlaitType.caption.copy(fontSize = 12.sp), color = ink.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (hero && onJoin != null) JoinButton(tone, hasCall, onJoin)
                }
            }
        }
    }
}

@Composable
private fun HeroTop(ev: CalEvent, brief: MeetingBrief, nowMs: Long, tone: KindTone) {
    val progress = remember { Animatable(0f) }
    val target = Agenda.ringProgress(ev, nowMs)
    LaunchedEffect(target) { progress.animateTo(target, tween(1600, delayMillis = 200, easing = FastOutSlowInEasing)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (ev.startMs in 1..nowMs) "Happening now" else "Up next", style = PlaitType.monoLabel, color = tone.ink.copy(alpha = 0.65f))
                KindPill(brief.kind, tone)
            }
            Spacer(Modifier.height(8.dp))
            Text(Agenda.countdown(ev, nowMs), style = PlaitType.heroNumber, color = tone.ink)
        }
        val ink = tone.ink
        Canvas(Modifier.size(64.dp)) {
            val w = 6.dp.toPx()
            val inset = w / 2
            val sz = Size(size.width - w, size.height - w)
            drawArc(ink.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
            drawArc(ink, -90f, 360f * progress.value, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
    if (brief.chips.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            brief.chips.take(3).forEach { SoftChip(it, tone) }
        }
    }
}

@Composable
private fun CompactTop(ev: CalEvent, brief: MeetingBrief, tone: KindTone, hasCall: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KindPill(brief.kind, tone)
        if (ev.duration.isNotBlank()) Text(ev.duration, style = PlaitType.monoSmall, color = tone.ink.copy(alpha = 0.65f))
        brief.chips.firstOrNull()?.let { SoftChip(it, tone) }
        Spacer(Modifier.weight(1f))
        if (hasCall) Icon(Icons.Filled.Videocam, "Has a call link", tint = tone.ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun JoinButton(tone: KindTone, hasCall: Boolean, onJoin: () -> Unit) {
    app.plait.ui.PlaitButton(
        if (hasCall) "Join now" else "Open", onJoin, style = app.plait.ui.ButtonStyle.Ink,
        icon = if (hasCall) Icons.Filled.Videocam else null,
    )
}

/**
 * The home page card: the meeting's title set large and heavy, one line saying what matters about
 * it ([takeaway], from the attached emails when there are some), and a flag when it needs prep.
 */
@Composable
fun BriefCard(
    ev: CalEvent,
    brief: MeetingBrief,
    nowMs: Long,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    flag: String? = null,
    takeaway: String,
    takeawayFromEmail: Boolean = false,
    onOpen: () -> Unit,
    onJoin: (() -> Unit)? = null,
) {
    val tone = brief.kind.tone()
    val ink = tone.ink
    val hasCall = ev.callLink != null
    val live = ev.startMs in 1..nowMs && ev.endMs > nowMs
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(if (hero) 44.dp else 36.dp)).background(tone.bg).pressable(onOpen),
    ) {
        if (hero) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 70.dp, y = (-80).dp)
                    .size(250.dp).background(Color.White.copy(alpha = 0.26f), CircleShape)
            )
        }
        Column(Modifier.padding(if (hero) 26.dp else 22.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (ev.allDay) "All day" else if (ev.endLabel.isNotBlank()) "${ev.time} – ${ev.endLabel}" else ev.time,
                    style = PlaitType.monoTime.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = ink,
                )
                Spacer(Modifier.weight(1f))
                if (!ev.allDay && ev.startMs > 0 && ev.endMs > nowMs) {
                    Box(Modifier.height(28.dp).clip(CircleShape).background(ink).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (live) "Now" else "in " + Agenda.countdown(ev, nowMs),
                            style = PlaitType.monoSmall.copy(fontSize = 12.sp), color = tone.bg,
                        )
                    }
                }
            }
            Spacer(Modifier.height(if (hero) 20.dp else 14.dp))
            Text(
                ev.title,
                style = if (hero) PlaitType.heroHeadline else PlaitType.cardHeadline,
                color = ink, maxLines = if (hero) 4 else 3, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                if (takeawayFromEmail) Icon(Icons.Filled.Email, null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.padding(top = 3.dp).size(18.dp))
                Text(
                    takeaway,
                    style = PlaitType.bodyStrong.copy(fontSize = if (hero) 18.sp else 16.sp, lineHeight = if (hero) 25.sp else 22.sp, fontWeight = FontWeight.SemiBold),
                    color = ink.copy(alpha = 0.9f),
                    fontStyle = if (!takeawayFromEmail && brief.aboutIsGuess) FontStyle.Italic else FontStyle.Normal,
                    maxLines = if (hero) 4 else 3, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KindPill(brief.kind, tone)
                if (flag != null) SoftChip(flag, tone)
                brief.chips.take(2).forEach { SoftChip(it, tone) }
                if (ev.place.isNotBlank()) SoftChip(ev.place, tone)
            }
            val others = ev.guests.any { !it.self }
            if (others || (hero && onJoin != null)) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GuestStack(ev.guests, tone)
                        if (others) Text(brief.withLine, style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    }
                    if (hero && onJoin != null) JoinButton(tone, hasCall, onJoin)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Event page: the brief
// ---------------------------------------------------------------------------------------------

@Composable
internal fun BriefTab(ev: CalEvent, brief: MeetingBrief, onRecord: () -> Unit, notes: List<app.plait.data.SavedNote>, memos: List<app.plait.data.memo.Memo>) {
    val desc = remember(ev.id, ev.description) {
        ev.description?.let { d ->
            val t = if (d.contains('<')) androidx.core.text.HtmlCompat.fromHtml(d.replace(Regex("(?is)<(style|script|head)[^>]*>.*?</\\1>"), ""), androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString() else d
            t.lines().map { it.trim() }.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
        }.orEmpty()
    }
    val look = LocalMeetingLook.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GuestsCard(ev, brief)
        val notesText = remember(notes, memos) {
            (notes.filter { it.eventId == ev.id }.map { n -> n.ai?.let { app.plait.ai.NoteAiPrompt.parse(it).summary } ?: n.lines.joinToString(" ") { it.text } } + memos.filter { it.eventId == ev.id }.map { if (it.kind == app.plait.data.memo.MemoKind.Checklist) app.plait.data.memo.Checklist.plain(it.text) else it.text })
                .filter { it.isNotBlank() }.joinToString("\n").take(1500)
        }
        MeetingInfoCard(ev, desc, brief, notesText, onRecord)
        SuggestedTasksCard(ev)
        val count = notes.size + memos.size
        var open by remember { mutableStateOf(false) }
        val items = remember(notes, memos) {
            notes.map { n ->
                ("Voice note · " + app.plait.data.TimeFmt.stamp(n.createdAt)) to (n.ai?.let { app.plait.ai.NoteAiPrompt.parse(it).summary }
                    ?: app.plait.data.brief.Summarizer.bullets(n.lines.map { it.text }, 3).joinToString(" ").ifBlank { "No speech was detected." })
            } + memos.map { m ->
                val photo = m.kind == app.plait.data.memo.MemoKind.Photo
                if (m.kind == app.plait.data.memo.MemoKind.Checklist) {
                    app.plait.data.memo.Checklist.heading(m.title, m.text) to
                        app.plait.data.memo.Checklist.parse(m.text).filter { !it.done }.joinToString(" · ") { it.text }.ifBlank { "All done." }
                } else ((if (photo) "Photo · " else "Note · ") + app.plait.data.TimeFmt.stamp(m.createdAt)) to m.text.ifBlank { "No text was found in the picture." }
            }
        }
        Column(
            Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(30.dp), look?.tint(2) ?: Color.Unspecified, look?.backdrop)
                .pressable({ open = true }, lift = 2.dp).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Notes summary", style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
            if (count == 0) Text("No notes are linked to this meeting yet.", style = PlaitType.body, color = plait.muted)
            else {
                Text(if (count == 1) "1 note linked" else "$count notes linked", style = PlaitType.monoSmall, color = plait.muted)
                items.take(2).forEach { (l, t) -> SummaryLine(l, t, maxLines = 2) }
                if (items.size > 2 || items.any { it.second.length > 110 }) Text("Tap to read all", style = PlaitType.caption, color = plait.muted)
            }
        }
        if (open) DetailDialog("Notes summary", { open = false }) {
            if (items.isEmpty()) Text("No notes are linked to this meeting yet. Record, write, photograph or start a list from the Notes tab.", style = PlaitType.body, color = plait.muted)
            items.forEach { (l, t) -> SummaryLine(l, t) }
        }
    }
}

@Composable
private fun SummaryLine(label: String, text: String, maxLines: Int = Int.MAX_VALUE) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = PlaitType.monoSmall, color = plait.muted)
        FormattedText(text, maxLines)
    }
}

internal fun sendMail(context: android.content.Context, to: List<String>, subject: String, body: String? = null) {
    val i = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
        .putExtra(android.content.Intent.EXTRA_EMAIL, to.toTypedArray()).putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
    if (body != null) i.putExtra(android.content.Intent.EXTRA_TEXT, body)
    runCatching { context.startActivity(i) }.onFailure { android.widget.Toast.makeText(context, "No email app found", android.widget.Toast.LENGTH_SHORT).show() }
}

@Composable
private fun GuestsCard(ev: CalEvent, brief: MeetingBrief) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val others = ev.guests.filter { !it.self && !it.email.isNullOrBlank() }
    val organizer = others.firstOrNull { it.organizer }
    val invite = remember(ev.id) { app.plait.data.context.ContextStore.get(context).forEvent(ev.id).firstOrNull() }
    val subject = "Re: " + ev.title
    val look = LocalMeetingLook.current
    var all by remember { mutableStateOf(false) }
    val pickContact = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        val picked = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS, android.provider.ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) to c.getString(1) else null
            }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        val (email, name) = picked
        if (app.plait.data.device.Invite.add(context, ev, email, name)) {
            app.plait.data.Calendars.refreshDevice(context)
            android.widget.Toast.makeText(context, "Added ${name ?: email}. Your calendar app sends the invitation when it syncs.", android.widget.Toast.LENGTH_LONG).show()
        } else sendMail(context, listOf(email), "Invitation: ${ev.title}", "Join me:\n\n" + app.plait.data.device.Invite.text(ev))
    }
    val shown = ev.guests.take(3)
    val more = ev.guests.size - shown.size
    Column(
        Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp), look?.tint(0) ?: Color.Unspecified, look?.backdrop)
            .pressable({ all = true }, lift = 2.dp)
            .padding(vertical = 20.dp, horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Guests", style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
                if (ev.guests.isNotEmpty()) Text("${ev.guests.size}", style = PlaitType.monoLabel, color = plait.muted)
            }
            if (brief.rsvpLine.isNotBlank()) Text(brief.rsvpLine, style = PlaitType.caption, color = plait.muted, modifier = Modifier.padding(top = 2.dp))
        }
        if (others.isNotEmpty() || invite != null) {
            FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (invite != null) {
                    val to = Regex("[\\w.+-]+@[\\w.-]+\\.\\w+").find(invite.from)?.value
                    PillButton("Reply to the invite email", { sendMail(context, listOfNotNull(to), if (invite.subject.startsWith("Re:", true)) invite.subject else "Re: " + invite.subject) }, Icons.Filled.Reply, primary = true)
                }
                if (organizer != null) PillButton("Reply to organizer", { sendMail(context, listOf(organizer.email!!), subject) }, Icons.Filled.Reply, primary = invite == null)
                if (others.size > 1 || (others.size == 1 && organizer == null)) PillButton("Email everyone", { sendMail(context, others.map { it.email!! }, ev.title) }, Icons.Filled.Email)
            }
        }
        FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Add people", {
                // Android's own contact picker: it lists the contacts from every account on the phone and gives Plait only the one picked.
                runCatching { pickContact.launch(android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI)) }
            }, Icons.Filled.PersonAdd)
            PillButton("Share link", {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_SUBJECT, ev.title).putExtra(android.content.Intent.EXTRA_TEXT, app.plait.data.device.Invite.text(ev))
                context.startActivity(android.content.Intent.createChooser(send, "Share meeting"))
            }, Icons.Filled.Share)
        }
        if (ev.guests.isEmpty()) {
            Text(
                "The calendar didn't list any guests for this event. They only show up when the invite includes them.",
                style = PlaitType.body, color = plait.muted, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        shown.forEach { g -> GuestRow(g) { sendMail(context, listOf(it), subject) } }
        if (more > 0) Text("+$more more · tap to see everyone", style = PlaitType.caption, color = plait.muted, modifier = Modifier.padding(horizontal = 4.dp))
    }
    if (all) DetailDialog("Guests · ${ev.guests.size}", { all = false }) {
        ev.guests.forEach { g -> GuestRow(g) { sendMail(context, listOf(it), subject) } }
    }
}

@Composable
private fun GuestRow(g: app.plait.data.Guest, onEmail: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .then(if (!g.self && !g.email.isNullOrBlank()) Modifier.pressable({ onEmail(g.email!!) }, lift = 2.dp) else Modifier)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).background(avatarColor(g), CircleShape), contentAlignment = Alignment.Center) {
            Text(g.initials, style = PlaitType.label.copy(fontWeight = FontWeight.Bold), color = Ink)
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (g.self) "${g.label} (you)".replace("You (you)", "You") else g.label,
                style = PlaitType.bodyStrong, color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                if (g.organizer) add("Organizer")
                if (g.optional) add("Optional")
                g.email?.let { add(it) }
            }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = PlaitType.caption, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val (label, color) = when (g.rsvp) {
            Rsvp.Accepted -> "Going" to plait.outlook
            Rsvp.Declined -> "Declined" to plait.signal
            Rsvp.Tentative -> "Maybe" to plait.icloud
            Rsvp.Pending -> "Waiting" to plait.muted
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Text(label, style = PlaitType.monoSmall, color = plait.muted)
        }
    }
}
