package app.plait.ui.screens

import app.plait.ui.popupSurface
import app.plait.R
import app.plait.i18n.userText
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import app.plait.ui.plaitCard
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ui.PlaitDialog
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.fauxGlass
import app.plait.ui.pressable
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Swipe a row from its end edge towards the start (right to left, or left to right in Arabic) to delete it. The row follows the finger over a red field with a bin
 * that pops once the swipe is far enough. Let go past a third of the width (or flick) and the row springs to the end and is
 * deleted (the caller offers Undo); let go before that and it bounces back, so a delete can be cancelled by lifting early.
 */
@Composable
fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, cornerRadius: Int = 28, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    // [offset] is measured towards the start edge (negative = moved towards start); [dir] turns it into screen pixels.
    val dir = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val offset = remember { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableStateOf(false) }
    val drag = rememberDraggableState { d ->
        scope.launch {
            // Past the end the finger meets a little resistance.
            val raw = offset.value + d * dir
            val next = if (raw < -width) -width + (raw + width) * 0.3f else raw.coerceAtMost(0f)
            offset.snapTo(next)
            val nowArmed = next < -width * 0.33f
            if (nowArmed != armed) { armed = nowArmed; view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
        }
    }
    val pop by androidx.compose.animation.core.animateFloatAsState(if (armed) 1.25f else 0.85f, spring(dampingRatio = 0.4f, stiffness = 500f), label = "bin")
    val red = plait.signal
    Box(
        modifier.fillMaxWidth().onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            // Only the strip the row has uncovered turns red, as a pill beside the row; nothing shows through the card.
            .drawBehind {
                val reveal = -offset.value - 8.dp.toPx()
                if (reveal > 0f) drawRoundRect(
                    red.copy(alpha = if (armed) 1f else 0.75f), Offset(if (dir > 0f) size.width - reveal else 0f, 0f), Size(reveal, size.height),
                    CornerRadius(minOf(cornerRadius.dp.toPx(), reveal / 2)),
                )
            },
    ) {
        Box(
            Modifier.align(Alignment.CenterEnd).size(56.dp).graphicsLayer {
                val reveal = -offset.value - 8.dp.toPx()
                alpha = (reveal / 56.dp.toPx()).coerceIn(0f, 1f)
                translationX = -((reveal - 56.dp.toPx()) / 2f).coerceAtLeast(0f) * dir
                scaleX = pop; scaleY = pop
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Delete, stringResource(R.string.common_delete), tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp)) }
        Box(
            Modifier.graphicsLayer { translationX = offset.value * dir }
                .draggable(
                    state = drag, orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        val v = velocity * dir
                        scope.launch {
                            if (offset.value < -width * 0.33f || v < -1800f) {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                // Overshoots the edge and settles, then the row is gone.
                                offset.animateTo(-width, spring(dampingRatio = 0.55f, stiffness = 260f), initialVelocity = v)
                                onDelete()
                                offset.snapTo(0f); armed = false
                            } else {
                                offset.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 380f), initialVelocity = v)
                                armed = false
                            }
                        }
                    },
                ),
        ) { content() }
    }
}

/** A delete that can be taken back for five seconds. Rows are hidden at once and only really deleted when the time runs out. */
class UndoDelete {
    val hidden = mutableStateListOf<String>()
    private var pending by mutableStateOf<Pending?>(null)
    private class Pending(val key: String, val label: String, val commit: () -> Unit)

    /** True while the screen is going away and the pending delete is being committed: a commit must not navigate then. */
    var leaving = false; private set

    fun isHidden(key: String) = key in hidden

    /** [label] is the whole message shown beside Undo, e.g. "Deleted note". */
    fun delete(key: String, label: String, commit: () -> Unit) {
        pending?.let { finish(it) }
        hidden.add(key)
        pending = Pending(key, label, commit)
    }

    private fun finish(p: Pending) { p.commit(); hidden.remove(p.key); if (pending === p) pending = null }

    private fun undo() { pending?.let { hidden.remove(it.key) }; pending = null }

    @Composable
    fun Host(modifier: Modifier = Modifier) {
        val p = pending
        // Leaving the screen within the five seconds makes the delete final instead of dropping it.
        DisposableEffect(Unit) { onDispose { pending?.let { leaving = true; finish(it); leaving = false } } }
        LaunchedEffect(p) { if (p != null) { delay(5000); if (pending === p) finish(p) } }
        AnimatedVisibility(
            visible = p != null, modifier = modifier,
            enter = slideInVertically(spring(dampingRatio = 0.6f, stiffness = 400f)) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            val shown = remember(p) { p }
            Row(
                Modifier.fauxGlass(RoundedCornerShape(30.dp)).padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(shown?.label ?: "", style = PlaitType.bodyStrong.copy(fontSize = 15.sp), color = plait.text, maxLines = 1)
                PlaitButton(stringResource(R.string.common_undo), { undo() }, style = ButtonStyle.Primary, size = ButtonSize.Small)
            }
        }
    }
}

class NoteAction(val label: String, val icon: ImageVector, val danger: Boolean = false, val onClick: () -> Unit)

/** What press-and-hold on a note offers: open it, edit it, delete it. */
@Composable
fun ActionsDialog(title: String, actions: List<NoteAction>, onDismiss: () -> Unit) {
    ExpandDialog(onDismiss) { close -> ActionsMenu(title, actions, close) }
}

/** The body of [ActionsDialog]: a title and one button per action, in the chosen style. */
@Composable
internal fun ActionsMenu(title: String, actions: List<NoteAction>, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).popupSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        // A long list (languages) scrolls inside the pop-up.
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.forEach { a -> app.plait.ui.MenuAction(a.label, a.icon, { onDismiss(); a.onClick() }, danger = a.danger) }
        }
    }
}

/** A text box in a sheet, for editing a note. */
@Composable
fun EditTextDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    PlaitDialog(onDismissRequest = onDismiss) { PopIn {
        Column(Modifier.fillMaxWidth().popupSurface().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = PlaitType.body.copy(color = plait.text, textDirection = TextDirection.Content), cursorBrush = SolidColor(plait.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner -> Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp).clip(RoundedCornerShape(20.dp)).background(plait.surface).padding(16.dp)) { inner() } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaitButton(stringResource(R.string.common_save), { onSave(text); onDismiss() }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = text.isNotBlank())
                PlaitButton(stringResource(R.string.common_cancel), onDismiss, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
        }
    } }
}

/** Opens its content with a springy scale-and-fade, the Material 3 Expressive way, instead of just appearing. */
@Composable
fun PopIn(content: @Composable () -> Unit) {
    val state = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        state,
        enter = androidx.compose.animation.scaleIn(spring(dampingRatio = 0.75f, stiffness = 380f), initialScale = 0.86f) +
            fadeIn(spring(stiffness = 1600f)),
    ) { content() }
}

/**
 * A pop-up that grows out of the card that opened it and shrinks back when it closes, instead of appearing and
 * vanishing. The dialog stays up until the closing animation has played; [content] gets the action that closes it.
 */
@Composable
fun ExpandDialog(onDismiss: () -> Unit, fullScreen: Boolean = false, content: @Composable (close: () -> Unit) -> Unit) {
    val state = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    val close: () -> Unit = { state.targetState = false }
    if (!state.targetState && state.isIdle && !state.currentState) androidx.compose.runtime.SideEffect { onDismiss() }
    val calm = app.plait.ui.glass.LocalGlassEnv.current.reduceMotion
    PlaitDialog(onDismissRequest = close, fullScreen = fullScreen) {
        AnimatedVisibility(
            state,
            enter = androidx.compose.animation.scaleIn(spring(dampingRatio = 0.8f, stiffness = 420f), initialScale = if (calm) 0.97f else 0.86f) +
                fadeIn(androidx.compose.animation.core.tween(160)),
            exit = androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(200, easing = androidx.compose.animation.core.FastOutLinearInEasing), targetScale = if (calm) 0.97f else 0.9f) +
                fadeOut(androidx.compose.animation.core.tween(180)),
        ) { content(close) }
    }
}

/** A solid, scrollable pop-up for the full version of a card: the card shows a short summary, tapping it opens this. */
@Composable
fun DetailDialog(title: String, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    ExpandDialog(onDismiss) { close ->
        Column(
            Modifier.fillMaxWidth().heightIn(max = 600.dp).popupSurface().padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = PlaitType.section.copy(fontSize = 22.sp), color = plait.text, modifier = Modifier.padding(horizontal = 24.dp))
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = content,
            )
            CloseButton(close, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        }
    }
}

/** Writes text the way it reads best: paragraphs with air between them, and "- " lines as bullets with a hanging indent. */
@Composable
fun FormattedText(text: String, maxLines: Int = Int.MAX_VALUE) {
    if (maxLines != Int.MAX_VALUE) {
        // In a card: the first lines, each kept as its own line or bullet, and an ellipsis if there is more.
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.take(maxLines).forEach { line ->
                if (line.startsWith("- ") || line.startsWith("• ")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(5.dp).background(plait.accent, androidx.compose.foundation.shape.CircleShape))
                    Text(line.drop(2), style = PlaitType.body.userText(), color = plait.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                } else Text(line, style = PlaitType.body.userText(), color = plait.text, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (lines.size > maxLines) Text("…", style = PlaitType.body, color = plait.muted)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.split(Regex("\\n\\s*\\n")).forEach { para ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                para.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                    if (line.startsWith("- ") || line.startsWith("• ")) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 9.dp).size(5.dp).background(plait.accent, androidx.compose.foundation.shape.CircleShape))
                        Text(line.drop(2), style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.weight(1f))
                    } else Text(line, style = PlaitType.body.userText(), color = plait.text, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Building or editing a checklist as tasks: type a task and add it, tick, retype or remove the ones already there. */
@Composable
fun ChecklistDialog(title: String, initial: List<app.plait.data.memo.Checklist.Item>, onSave: (List<app.plait.data.memo.Checklist.Item>) -> Unit, onDismiss: () -> Unit) {
    val items = remember { mutableStateListOf<app.plait.data.memo.Checklist.Item>().apply { addAll(initial) } }
    var draft by remember { mutableStateOf("") }
    val addDraft = { if (draft.isNotBlank()) { items.add(app.plait.data.memo.Checklist.Item(draft.trim(), false)); draft = "" } }
    PlaitDialog(onDismissRequest = onDismiss) { PopIn {
        Column(Modifier.fillMaxWidth().popupSurface().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = PlaitType.section.copy(fontSize = 20.sp), color = plait.text)
            Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (items.isEmpty()) Text(stringResource(R.string.note_no_tasks), style = PlaitType.caption, color = plait.muted)
                items.forEachIndexed { i, item ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(plait.surface).padding(start = 6.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).pressable({ items[i] = item.copy(done = !item.done) }, lift = 2.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                if (item.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, stringResource(if (item.done) R.string.note_item_done else R.string.note_item_not_done),
                                tint = if (item.done) plait.accent else plait.muted, modifier = Modifier.size(22.dp),
                            )
                        }
                        BasicTextField(
                            value = item.text, onValueChange = { items[i] = item.copy(text = it) }, singleLine = true,
                            textStyle = PlaitType.body.copy(color = plait.text, textDirection = TextDirection.Content), cursorBrush = SolidColor(plait.accent),
                            modifier = Modifier.weight(1f),
                        )
                        Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).pressable({ items.removeAt(i) }, lift = 2.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, stringResource(R.string.note_remove_task), tint = plait.muted, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (!item.done) DueChip(item.dueMs, { items[i] = items[i].copy(dueMs = it) }, Modifier.padding(start = 54.dp, bottom = 6.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = draft, onValueChange = { draft = it }, singleLine = true,
                    textStyle = PlaitType.body.copy(color = plait.text, textDirection = TextDirection.Content), cursorBrush = SolidColor(plait.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addDraft() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(plait.surface).padding(horizontal = 16.dp, vertical = 14.dp)) {
                            if (draft.isEmpty()) Text(stringResource(R.string.note_add_task_hint), style = PlaitType.body, color = plait.muted)
                            inner()
                        }
                    },
                )
                app.plait.ui.CircleIconButton(Icons.Filled.Add, stringResource(R.string.note_add_task), { addDraft() }, plait.accent, plait.onAccent, size = 48.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlaitButton(stringResource(R.string.common_save), {
                    addDraft()
                    val clean = items.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }
                    if (clean.isNotEmpty()) onSave(clean)
                    onDismiss()
                }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = items.isNotEmpty() || draft.isNotBlank())
                PlaitButton(stringResource(R.string.common_cancel), onDismiss, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
        }
    } }
}

/**
 * Close for pop-ups, drawn like Join and the record button: a calm translucent red pill with a thin light rim and dark
 * label, the same height as Join and the full width of the pop-up.
 */
@Composable
fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (app.plait.ui.glass.LocalGlassEnv.current.material) {
        app.plait.ui.material.MPill(stringResource(R.string.common_close), onClick, modifier, Icons.Filled.Close, plait.signal, androidx.compose.ui.graphics.Color(0xFF0E1316), 56.dp)
        return
    }
    val shape = androidx.compose.foundation.shape.CircleShape
    val fill = androidx.compose.ui.graphics.lerp(plait.surfaceHigh, plait.signal, 0.72f)
    val rim = androidx.compose.ui.graphics.lerp(plait.signal, androidx.compose.ui.graphics.Color.White, 0.45f).copy(alpha = 0.85f)
    Row(
        modifier.height(64.dp).clip(shape).background(fill).border(1.dp, rim, shape).pressable(onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        Icon(Icons.Filled.Close, null, tint = androidx.compose.ui.graphics.Color(0xFF0E1316), modifier = Modifier.size(22.dp))
        Text(stringResource(R.string.common_close), style = PlaitType.bodyStrong, color = androidx.compose.ui.graphics.Color(0xFF0E1316))
    }
}
