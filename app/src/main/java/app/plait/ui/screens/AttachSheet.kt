package app.plait.ui.screens

import app.plait.ui.popupSurface
import app.plait.ui.plaitCard
import app.plait.R
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import app.plait.i18n.userText
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.data.Calendars
import app.plait.data.CalEvent
import app.plait.data.brief.BriefBuilder
import app.plait.data.context.ContextItem
import app.plait.data.context.ContextStore
import app.plait.data.context.EmailParser
import app.plait.data.context.EventMatcher
import app.plait.data.context.Threads
import app.plait.share.SharedMail
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.pressable
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Appears over the app when an email is shared to Plait. Suggests the meeting it most likely
 * belongs to (by topic words, guests' addresses and how soon the meeting is) and attaches it on tap.
 */
@Composable
fun AttachSheet(shared: SharedMail, onAttached: (CalEvent) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val now = remember { System.currentTimeMillis() }
    val mail = shared.mail
    val matches = remember(shared) {
        val events = Calendars.events
        EventMatcher.rank(mail, events, zone, now)
    }
    val preview = remember(shared) {
        Threads.split(mail.body, mail.from).firstOrNull()?.text?.replace(Regex("\\s+"), " ")?.take(220).orEmpty()
    }
    val enter = remember { MutableTransitionState(false).apply { targetState = true } }

    app.plait.ui.DimScrim(true, app.plait.ui.Dim.Sheet, onDismiss)
    AnimatedVisibility(enter, enter = slideInVertically(spring(dampingRatio = 0.82f, stiffness = 320f)) { it } + fadeIn(tween(160))) {
        Box(Modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 760.dp)
                    .popupSurface(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                    .clickable(interactionSource = null, indication = null) {}
                    .navigationBarsPadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 40.dp, height = 5.dp).background(plait.muted.copy(alpha = 0.5f), CircleShape))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.note_attach_title), style = PlaitType.section.copy(fontSize = 28.sp, lineHeight = 32.sp), color = plait.text)
                    PlaitButton(stringResource(R.string.note_attach_discard), onDismiss, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                }

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(plait.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(mail.subject.ifBlank { stringResource(R.string.note_attach_shared_text) }, style = PlaitType.bodyStrong.userText(), color = plait.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val meta = buildList {
                        if (mail.from.isNotBlank()) add(Bidi.isolate(EmailParser.displayName(mail.from)))
                        if (mail.attachments.isNotEmpty()) add(Txt.plural(R.plurals.note_attach_attachments, mail.attachments.size))
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = PlaitType.monoSmall, color = plait.muted)
                    if (preview.isNotBlank()) Text(preview, style = PlaitType.body.userText(), color = plait.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }

                if (matches.isEmpty()) {
                    Text(
                        stringResource(R.string.note_attach_no_meetings),
                        style = PlaitType.body, color = plait.muted,
                    )
                } else {
                    Text(
                        stringResource(if (matches.first().score >= 3.0) R.string.note_attach_best_first else R.string.note_attach_pick),
                        style = PlaitType.bodyStrong, color = plait.text,
                    )
                    matches.forEachIndexed { i, m ->
                        MatchRow(m.event, m.reasons, best = i == 0 && m.score >= 3.0, zone = zone) {
                            val item = ContextItem(
                                UUID.randomUUID().toString(), m.event.id, mail.subject, mail.from, mail.to, mail.dateMs,
                                mail.body.take(60_000), System.currentTimeMillis(), shared.source, mail.attachments,
                            )
                            ContextStore.get(context).add(item)
                            Toast.makeText(context, Txt.s(R.string.note_attach_added, Bidi.isolate(m.event.title)), Toast.LENGTH_SHORT).show()
                            onAttached(m.event)
                        }
                    }
                }
                Text(stringResource(R.string.note_attach_stays), style = PlaitType.caption, color = plait.muted)
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun MatchRow(ev: CalEvent, reasons: List<String>, best: Boolean, zone: ZoneId, onClick: () -> Unit) {
    val kind = remember(ev.id) { BriefBuilder.classify(ev) }
    val tone = kind.tone()
    val whenText = remember(ev.id) {
        Instant.ofEpochMilli(ev.startMs).atZone(zone).format(app.plait.data.TimeFmt.fmt("EEE d MMM · h:mm a"))
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(tone.bg).pressable(onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KindPill(kind, tone)
            if (best) Text(stringResource(R.string.note_attach_best), style = PlaitType.monoSmall, color = tone.ink)
        }
        Text(ev.title, style = PlaitType.cardTitle.copy(fontSize = 20.sp, lineHeight = 23.sp).userText(), color = tone.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(whenText, style = PlaitType.monoSmall, color = tone.ink.copy(alpha = 0.7f))
        if (reasons.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reasons.take(3).forEach { r ->
                    Box(Modifier.height(24.dp).clip(CircleShape).background(tone.ink.copy(alpha = 0.12f)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(r, style = PlaitType.monoSmall.copy(fontSize = 10.sp), color = tone.ink)
                    }
                }
            }
        }
    }
}
