package app.plait.ui.screens

import app.plait.ui.plaitCard
import app.plait.R
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import app.plait.i18n.userText
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ai.Ai
import app.plait.data.CalEvent
import app.plait.data.brief.MeetingBrief
import app.plait.data.context.AiPrompt
import app.plait.data.context.ContextAnalyzer
import app.plait.data.context.ContextItem
import app.plait.data.context.ContextStore
import app.plait.data.context.EmailParser
import app.plait.data.context.ExampleEmail
import app.plait.data.context.Highlight
import app.plait.data.context.HighlightKind
import app.plait.data.context.Strength
import app.plait.data.context.Threads
import app.plait.data.Calendars
import app.plait.share.ShareInbox
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.ConfirmDeleteButton
import app.plait.ui.PlaitButton
import app.plait.ui.pressable
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import com.google.mlkit.genai.common.DownloadStatus
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

@Composable
private fun HighlightKind.color(): Color = when (this) {
    HighlightKind.Decision -> plait.outlook
    HighlightKind.Risk -> plait.signal
    HighlightKind.Deadline -> plait.icloud
    HighlightKind.Ask -> plait.accent
    HighlightKind.Action -> plait.google
    HighlightKind.Number -> plait.muted
    HighlightKind.Attachment -> plait.muted
}

@Composable
private fun Strength.color(): Color = when (this) {
    Strength.Strong -> plait.outlook
    Strength.Some -> plait.icloud
    Strength.Weak -> plait.muted
}

/**
 * Emails and notes attached to this meeting, read on the phone: how they relate to the meeting,
 * what was decided, asked, risky or due, and what is left to settle. Optional help from the
 * phone's built-in AI when the phone has one.
 */
@Composable
internal fun ContextTab(ev: CalEvent, brief: MeetingBrief) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ContextStore.get(context) }
    val zone = remember { ZoneId.systemDefault() }
    val items = store.items.filter { it.eventId == ev.id }
    val summary = remember(items.map { it.id to it.body.length }, ev.id) {
        if (items.isEmpty()) null else ContextAnalyzer.analyze(ev, brief, items, zone)
    }
    var pasteOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (items.isEmpty() && !pasteOpen) {
            EmptyContext(
                demo = !Calendars.connected,
                onPaste = { pasteOpen = true },
                onExample = {
                    val m = ExampleEmail.forEvent(ev, zone)
                    store.add(ContextItem(UUID.randomUUID().toString(), ev.id, m.subject, m.from, m.to, m.dateMs, m.body, System.currentTimeMillis(), "example", m.attachments))
                },
            )
        }
        if (pasteOpen) {
            PasteCard(
                onCancel = { pasteOpen = false },
                onAdd = { subject, text ->
                    val m = EmailParser.fromText(text, subject.ifBlank { null })
                    store.add(
                        ContextItem(UUID.randomUUID().toString(), ev.id, m.subject, m.from, m.to, m.dateMs, m.body.take(60_000), System.currentTimeMillis(), "pasted", m.attachments)
                    )
                    pasteOpen = false
                },
            )
        }

        if (summary != null) {
            SummaryCard(summary.overview, summary.strength, summary.relation, items.size)

            if (summary.highlights.isNotEmpty()) HighlightsCard(summary.highlights)
            if (summary.toSettle.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.meet_ctx_to_settle_title), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
                    summary.toSettle.forEach { h ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.padding(top = 9.dp).size(6.dp).background(h.kind.color(), CircleShape))
                            Text(h.text, style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            AiCard(ev, brief, items, store)

            Text(stringResource(R.string.meet_ctx_emails), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
            items.forEach { item ->
                val insight = summary.items.firstOrNull { it.itemId == item.id }
                ItemCard(item, insight?.strength, insight?.gist, onRemove = { store.remove(item) })
            }
            if (!pasteOpen) AddMore(onPaste = { pasteOpen = true })
        }

        Text(
            stringResource(R.string.meet_ctx_privacy),
            style = PlaitType.caption, color = plait.muted, modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun EmptyContext(demo: Boolean, onPaste: () -> Unit, onExample: () -> Unit) {
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Filled.Email, null, tint = plait.accent, modifier = Modifier.size(28.dp))
        Text(stringResource(R.string.meet_ctx_add_title), style = PlaitType.section.copy(fontSize = 22.sp), color = plait.text)
        Text(
            stringResource(R.string.meet_ctx_add_body),
            style = PlaitType.body, color = plait.muted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.meet_ctx_from_mail_app), style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = plait.text)
            Text(
                stringResource(R.string.meet_ctx_from_mail_body),
                style = PlaitType.caption, color = plait.muted,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaitButton(stringResource(R.string.meet_ctx_paste_button), onPaste, style = ButtonStyle.Primary)
            if (demo) PlaitButton(stringResource(R.string.meet_ctx_try_example), onExample)
        }
    }
}

@Composable
private fun PasteCard(onCancel: () -> Unit, onAdd: (subject: String, text: String) -> Unit) {
    val context = LocalContext.current
    var subject by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.meet_ctx_paste_title), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text, modifier = Modifier.padding(horizontal = 4.dp))
        Field(subject, { subject = it }, stringResource(R.string.meet_ctx_subject_hint), singleLine = true)
        Field(text, { text = it }, stringResource(R.string.meet_ctx_paste_hint), singleLine = false, minHeight = 150)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaitButton(stringResource(R.string.meet_ctx_from_clipboard), { ShareInbox.clipboardText(context)?.let { text = it } ?: Toast.makeText(context, Txt.s(R.string.meet_ctx_nothing_to_paste), Toast.LENGTH_SHORT).show() }, size = ButtonSize.Small)
            PlaitButton(stringResource(R.string.common_cancel), onCancel, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            PlaitButton(stringResource(R.string.common_add), { onAdd(subject, text) }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = text.isNotBlank())
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, singleLine: Boolean, minHeight: Int = 52) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(plait.surfaceHigh).padding(horizontal = 16.dp, vertical = 14.dp)) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = singleLine,
            textStyle = PlaitType.body.copy(color = plait.text).userText(), cursorBrush = SolidColor(plait.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().heightIn(min = (minHeight - 28).dp),
            decorationBox = { inner ->
                Box { if (value.isEmpty()) Text(hint, style = PlaitType.body, color = plait.muted); inner() }
            },
        )
    }
}

@Composable
private fun SummaryCard(overview: String, strength: Strength, relation: List<String>, count: Int) {
    val tone = strength.color()
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.meet_ctx_what_email_says), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text, modifier = Modifier.weight(1f))
            Row(
                Modifier.height(28.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).background(tone, CircleShape))
                Text(strength.label, style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = plait.text)
            }
        }
        if (overview.isNotBlank()) Text(overview, style = PlaitType.body.copy(fontSize = 16.sp, lineHeight = 23.sp).userText(), color = plait.text)
        if (relation.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                relation.forEach { chip ->
                    Box(Modifier.height(28.dp).clip(CircleShape).background(plait.surfaceHigh).padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                        Text(chip, style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = plait.text)
                    }
                }
            }
        }
        if (strength == Strength.Weak) {
            Text(
                stringResource(R.string.meet_ctx_weak),
                style = PlaitType.caption, color = plait.muted,
            )
        }
        Text(
            stringResource(R.string.meet_ctx_found_by) + if (count > 1) " " + pluralStringResource(R.plurals.meet_ctx_emails_combined, count, count) else "",
            style = PlaitType.caption, color = plait.muted,
        )
    }
}

@Composable
private fun HighlightsCard(highlights: List<Highlight>) {
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.meet_ctx_highlights), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
        highlights.forEach { h ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.height(22.dp).clip(CircleShape).background(h.kind.color().copy(alpha = 0.18f)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text(h.kind.label, style = PlaitType.monoSmall.copy(fontSize = 10.sp), color = h.kind.color())
                    }
                    h.who?.let { Text(it, style = PlaitType.caption.userText(), color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                Text(h.text, style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AddMore(onPaste: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(26.dp)).pressable(onPaste).padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.meet_ctx_add_another), style = PlaitType.bodyStrong, color = plait.accent)
        Text(stringResource(R.string.meet_ctx_paste), style = PlaitType.monoLabel, color = plait.muted)
    }
}

@Composable
private fun ItemCard(item: ContextItem, strength: Strength?, gist: String?, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val messages = remember(item.id) { if (open) Threads.split(unwrapLinksSafe(item.body), item.from) else emptyList() }
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(28.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(item.subject.ifBlank { stringResource(R.string.meet_ctx_no_subject) }, style = PlaitType.bodyStrong.userText(), color = plait.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            strength?.let {
                Spacer(Modifier.size(10.dp))
                Box(Modifier.size(10.dp).background(it.color(), CircleShape))
            }
        }
        val meta = buildList {
            if (item.from.isNotBlank()) add(EmailParser.displayName(item.from))
            item.dateMs?.let { add(java.text.SimpleDateFormat("EEE d MMM", app.plait.i18n.AppLocale.locale).format(java.util.Date(it))) }
            add(if (item.source == "example") Txt.s(R.string.meet_ctx_example) else item.source)
        }.joinToString(" · ") { Bidi.isolate(it) }
        Text(meta, style = PlaitType.monoSmall, color = plait.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!gist.isNullOrBlank() && !open) Text(gist, style = PlaitType.body.userText(), color = plait.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (item.attachments.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.attachments.take(4).forEach { name ->
                    Box(Modifier.height(26.dp).clip(CircleShape).background(plait.surfaceHigh).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text("📎 $name".replace("📎 ", ""), style = PlaitType.monoSmall.copy(fontSize = 10.sp), color = plait.muted, maxLines = 1)
                    }
                }
            }
        }
        if (open) {
            messages.forEach { m ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(plait.surfaceHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(Bidi.isolate(m.from ?: stringResource(R.string.meet_ctx_message)) + (m.dateText?.let { " · ${Bidi.isolate(it)}" } ?: ""), style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = plait.muted)
                    Text(m.text.take(900) + if (m.text.length > 900) "…" else "", style = PlaitType.body.copy(fontSize = 14.sp, lineHeight = 20.sp).userText(), color = plait.text, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaitButton(stringResource(if (open) R.string.meet_ctx_hide else R.string.meet_ctx_read), { open = !open }, style = ButtonStyle.Secondary, size = ButtonSize.Small)
            ConfirmDeleteButton(onRemove, label = stringResource(R.string.common_remove))
        }
    }
}

private fun unwrapLinksSafe(s: String) = app.plait.data.context.unwrapLinks(s)

// ---------------------------------------------------------------------------------------------
// The phone's built-in AI
// ---------------------------------------------------------------------------------------------

@Composable
private fun AiCard(ev: CalEvent, brief: MeetingBrief, items: List<ContextItem>, store: ContextStore) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Ai.Status?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var by by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { status = Ai.status(context) }
    val saved = items.firstNotNullOfOrNull { it.aiSummary }
    val sections = remember(saved) { saved?.let { AiPrompt.parse(it) } }

    // With no AI ready and nothing saved there is nothing to show, so the card does not appear at all.
    if (status?.ready != true && sections == null) return
    Column(Modifier.fillMaxWidth().plaitCard(RoundedCornerShape(32.dp)).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.AutoAwesome, null, tint = plait.accent, modifier = Modifier.size(22.dp))
            Text(stringResource(R.string.meet_ctx_ask_ai), style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
        }
        if (sections != null) {
            sections.relation?.let { Text(it, style = PlaitType.body.copy(fontSize = 16.sp, lineHeight = 23.sp).userText(), color = plait.text) }
            if (sections.keyPoints.isNotEmpty()) {
                Text(stringResource(R.string.meet_ctx_key_points), style = PlaitType.monoSmall, color = plait.muted)
                sections.keyPoints.forEach { Bullet(it) }
            }
            if (sections.toSettle.isNotEmpty()) {
                Text(stringResource(R.string.meet_ctx_to_settle), style = PlaitType.monoSmall, color = plait.muted)
                sections.toSettle.forEach { Bullet(it) }
            }
            if (sections.relation == null && sections.keyPoints.isEmpty()) Text(sections.raw, style = PlaitType.body.userText(), color = plait.text)
        }
        val s = status
        when {
            s == null || !s.ready -> Unit // no AI chosen or ready: say nothing, the card below stays as it is
            else -> {
                ActionButton(if (busy) stringResource(R.string.meet_ctx_thinking_with, s.label) else stringResource(if (sections == null) R.string.meet_ctx_explain else R.string.meet_ctx_ask_again), enabled = !busy) {
                    scope.launch {
                        busy = true; error = null
                        val out = Ai.generate(context, AiPrompt.build(ev, brief, items, maxChars = s.promptBudget))
                        if (out == null) error = Txt.s(R.string.meet_ctx_nothing_back)
                        else { store.setEventAi(ev.id, out.text); by = out.label }
                        busy = false
                    }
                }
            }
        }
        error?.let { Text(it, style = PlaitType.caption, color = plait.signal) }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 9.dp).size(6.dp).background(plait.accent, CircleShape))
        Text(text, style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    PlaitButton(label, onClick, style = ButtonStyle.Primary, enabled = enabled)
}
