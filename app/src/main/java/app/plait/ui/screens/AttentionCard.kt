package app.plait.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.plait.ui.glass.LocalGlassEnv
import app.plait.ui.glass.liquidGlass
import app.plait.ui.theme.LocalIsDark
import app.plait.ui.theme.PlaitType

private const val MaxRows = 4

/** The second, contrasting card under the meetings brief: coral on Morning, mint on Midday. */
internal fun Phase.attentionTone() = when (this) {
    Phase.Afternoon -> KindTone(Color(0xFFA8E6C1), Color(0xFF0E1316))
    else -> KindTone(Color(0xFFFFB4A2), Color(0xFF0E1316))
}

/** "From <meeting> · 3 days ago", "Free note · today", "Suggested from <meeting>". Overdue-ness lives only in the wording. */
internal fun attentionCaption(item: AttentionItem, nowMs: Long): String {
    val title = item.eventTitle.trim()
    val ago = AttentionTasks.ago(item.sinceMs, nowMs)
    return when {
        item.kind == AttentionItem.Kind.Suggestion -> if (title.isBlank()) "Suggested · $ago" else "Suggested from $title"
        title.isBlank() -> "Free note · $ago"
        else -> "From $title · $ago"
    }
}

@Composable
internal fun AttentionCard(phase: Phase, nowMs: Long, onOpenEvent: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val material = LocalGlassEnv.current.material
    val cs = MaterialTheme.colorScheme
    val backdrop = LocalCardBackdrop.current
    val tone = phase.attentionTone()
    val morning = phase == Phase.Morning
    val ink = if (material) (if (morning) cs.onSecondaryContainer else cs.onTertiaryContainer) else cardInk(LocalIsDark.current)

    // Both stores are Compose state lists: reading them here re-runs the collect when a note, memo or idea changes.
    val memos = MemoStore.get(context).items.toList()
    val ideas = TaskIdeas.items.toList()
    val items = remember(HomeState.refreshTick, memos, ideas, nowMs / 60_000) { AttentionTasks.now(context, nowMs) }

    val shell = when {
        material -> Modifier.clip(RoundedCornerShape(28.dp)).background(if (morning) cs.secondaryContainer else cs.tertiaryContainer)
        backdrop == null -> Modifier.clip(RoundedCornerShape(32.dp)).background(tone.bg.copy(alpha = 0.40f))
        else -> Modifier.liquidGlass(
            backdrop, shape = { com.kyant.shapes.RoundedRectangle(32.dp) },
            tint = tone.bg.copy(alpha = 0.40f), lens = false,
            surfaceColor = Color.White.copy(alpha = if (LocalIsDark.current) 0.6f else 0.07f),
        )
    }

    CompositionLocalProvider(LocalCardInk provides ink) {
        Column(
            modifier.fillMaxWidth().heightIn(min = 96.dp, max = 260.dp).then(shell)
                .verticalScroll(rememberScrollState()).animateContentSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.TaskAlt, null, tint = ink, modifier = Modifier.size(18.dp))
                }
                Text("Needs your attention", style = PlaitType.bodyStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) Box(Modifier.clip(CircleShape).background(ink.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("${items.size}", style = PlaitType.monoSmall.copy(fontSize = 12.sp), color = ink)
                }
            }
            if (items.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = ink.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                    Text("Nothing needs you right now.", style = PlaitType.body, color = ink.copy(alpha = 0.8f))
                }
            } else {
                items.take(MaxRows).forEach { item ->
                    androidx.compose.runtime.key(item.id) { AttentionRow(item, nowMs, ink, onOpenEvent) }
                }
                if (items.size > MaxRows) Text("+${items.size - MaxRows} more", style = PlaitType.caption, color = ink.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun AttentionRow(item: AttentionItem, nowMs: Long, ink: Color, onOpenEvent: (String) -> Unit) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.kind == AttentionItem.Kind.Open) {
            // A 40.dp touch area around the 28.dp ring.
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .clickable(role = Role.Button) { AttentionTasks.tick(context, item) }
                    .semantics { contentDescription = "Mark done: ${item.text}" },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(28.dp).border(2.dp, ink.copy(alpha = 0.55f), CircleShape)) }
        }
        Column(
            Modifier.weight(1f).then(if (item.eventId.isBlank()) Modifier else Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { onOpenEvent(item.eventId) }),
        ) {
            Text(item.text, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.SemiBold), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(attentionCaption(item, nowMs), style = PlaitType.caption, color = ink.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.kind == AttentionItem.Kind.Suggestion) {
            PlaitButton("Add", { AttentionTasks.add(context, item) }, style = ButtonStyle.Ink, size = ButtonSize.Small)
            PlaitButton("Dismiss", { AttentionTasks.dismiss(context, item) }, style = ButtonStyle.InkSoft, size = ButtonSize.Small)
        }
    }
}
