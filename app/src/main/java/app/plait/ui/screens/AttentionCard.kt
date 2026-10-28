package app.plait.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.data.brief.Phase
import app.plait.data.memo.MemoStore
import app.plait.data.tasks.AttentionItem
import app.plait.data.tasks.AttentionTasks
import app.plait.data.tasks.TaskIdeas
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.HomeState
import app.plait.ui.PlaitButton
import app.plait.ui.theme.PlaitType
import app.plait.R
import app.plait.i18n.Bidi
import app.plait.i18n.Txt
import app.plait.i18n.userText
import androidx.compose.ui.res.stringResource

private const val MaxRows = 8

/** "From <meeting> · 3 days ago", "Free note · today", "Suggested from <meeting>". Overdue-ness lives only in the wording. */
internal fun attentionCaption(item: AttentionItem, nowMs: Long): String {
    val title = Bidi.isolate(item.eventTitle.trim())
    val ago = AttentionTasks.ago(item.sinceMs, nowMs)
    return when {
        item.kind == AttentionItem.Kind.Suggestion -> if (title.isBlank()) Txt.s(R.string.home_att_suggested_ago, ago) else Txt.s(R.string.home_att_suggested_from, title)
        title.isBlank() -> Txt.s(R.string.home_att_free_note, ago)
        else -> Txt.s(R.string.home_att_from, title, ago)
    }
}

/** What the Tasks card lists: urgent first, then suggestions, then the other open items; the count is the urgent ones when there are any. */
internal class AttentionView(val count: Int, val rows: List<AttentionItem>, val more: Int)

internal fun attentionView(items: List<AttentionItem>, max: Int = MaxRows): AttentionView {
    val (urgent, rest) = items.partition { it.urgent }
    val ordered = urgent + rest.filter { it.kind == AttentionItem.Kind.Suggestion } + rest.filter { it.kind != AttentionItem.Kind.Suggestion }
    return AttentionView(if (urgent.isNotEmpty()) urgent.size else items.size, ordered.take(max), (items.size - max).coerceAtLeast(0))
}

/** Rows the Tasks card shows in the feed; open, it lists every one. */
internal const val CollapsedRows = 4

/** The inside of the Tasks card; the shell, its colour and [LocalCardInk] come from the feed. [full] is the open card. */
@Composable
internal fun AttentionContent(phase: Phase, nowMs: Long, onOpenEvent: (String) -> Unit, full: Boolean = false) {
    val context = LocalContext.current
    val ink = LocalCardInk.current
    // Both stores are Compose state lists: reading them here re-runs the collect when a note, memo or idea changes.
    val memos = MemoStore.get(context).items.toList()
    val ideas = TaskIdeas.items.toList()
    val items = remember(HomeState.refreshTick, memos, ideas, nowMs / 60_000) { AttentionTasks.now(context, nowMs) }
    val view = remember(items, full) { attentionView(items, if (full) items.size else CollapsedRows) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.TaskAlt, null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Text(stringResource(R.string.home_att_title), style = PlaitType.bodyStrong.copy(fontSize = 17.sp), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (items.isNotEmpty()) Box(Modifier.clip(CircleShape).background(ink.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text("${view.count}", style = PlaitType.monoSmall.copy(fontSize = 12.sp), color = ink)
        }
    }
    if (items.isEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.CheckCircle, null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.home_att_empty), style = PlaitType.body, color = ink.copy(alpha = 0.8f))
        }
        QuoteBlock(phase, FeedCardKind.Tasks)
    } else {
        view.rows.forEach { item ->
            androidx.compose.runtime.key(item.id) { AttentionRow(item, nowMs, ink, onOpenEvent) }
        }
        if (view.more > 0) Text(stringResource(R.string.home_n_more, view.more), style = PlaitType.caption, color = ink.copy(alpha = 0.7f))
    }
}

@Composable
private fun AttentionRow(item: AttentionItem, nowMs: Long, ink: Color, onOpenEvent: (String) -> Unit) {
    val context = LocalContext.current
    // Open lines that are not urgent are the backlog: listed, but quieter.
    val quiet = item.kind == AttentionItem.Kind.Open && !item.urgent
    val markDone = stringResource(R.string.home_att_mark_done, Bidi.isolate(item.text))
    Row(Modifier.fillMaxWidth().alpha(if (quiet) 0.7f else 1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.kind == AttentionItem.Kind.Open) {
            // A 40.dp touch area around the 28.dp ring.
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .clickable(role = Role.Button) { AttentionTasks.tick(context, item) }
                    .semantics { contentDescription = markDone },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(28.dp).border(2.dp, ink.copy(alpha = 0.55f), CircleShape)) }
        }
        Column(
            Modifier.weight(1f).then(if (item.eventId.isBlank()) Modifier else Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onOpenEvent(item.eventId) }),
        ) {
            Text(item.text, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.SemiBold).userText(), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val due = item.dueMs
            if (item.urgent && due != null) {
                val overdue = due < nowMs
                Box(
                    Modifier.padding(vertical = 3.dp).clip(CircleShape).background(if (overdue) Color(0xFFD93A22) else Color(0xFFFF7A66).copy(alpha = 0.38f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(AttentionTasks.dueLabel(due, nowMs), style = PlaitType.monoSmall.copy(fontSize = 11.sp), color = if (overdue) Color.White else ink, maxLines = 1)
                }
            }
            Text(attentionCaption(item, nowMs), style = PlaitType.caption, color = ink.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.kind == AttentionItem.Kind.Suggestion) {
            PlaitButton(stringResource(R.string.common_add), { AttentionTasks.add(context, item) }, style = ButtonStyle.Ink, size = ButtonSize.Small)
            PlaitButton(stringResource(R.string.common_dismiss), { AttentionTasks.dismiss(context, item) }, style = ButtonStyle.InkSoft, size = ButtonSize.Small)
        }
    }
}
