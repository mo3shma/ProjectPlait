package app.plait.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.plait.ui.plaitCard
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Corner radius of a closed note row. */
internal const val NoteCardRadius = 28f

/** An opened note: where its row was ([from], null when it had no bounds) and how far it has grown (0 the row, 1 the whole area). */
internal class NoteOpen(val from: Rect?) {
    val progress = Animatable(0f)
}

/** Where a note with no row on screen grows from: the open area, a little smaller, so it fades and scales in. */
internal fun fallbackFrom(to: Rect, scale: Float = 0.9f): Rect =
    Rect(androidx.compose.ui.geometry.Offset(to.center.x - to.width * scale / 2, to.center.y - to.height * scale / 2), androidx.compose.ui.geometry.Size(to.width * scale, to.height * scale))

/** The note's content fades in over the later part of the growth, so it never shows squashed in a small card. */
internal fun noteContentAlpha(progress: Float): Float = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)

/** The row's rectangle if [from] is known, else the fallback; between it and [to] by [progress]. */
internal fun noteBounds(from: Rect?, to: Rect, progress: Float): Rect = expandedBounds(from ?: fallbackFrom(to), to, progress)

/** Same sizing as [expanding] (read during layout, never recomposes) for a note, whose source rectangle may be missing. */
internal fun Modifier.expandingNote(card: NoteOpen, top: Dp, bottom: Dp, side: Dp, drop: () -> Float): Modifier = layout { m, c ->
    val to = Rect(side.toPx(), top.toPx(), c.maxWidth - side.toPx(), c.maxHeight - bottom.toPx())
    val r = noteBounds(card.from, to, card.progress.value)
    val w = r.width.roundToInt().coerceAtLeast(0)
    val h = r.height.roundToInt().coerceAtLeast(0)
    val p = m.measure(Constraints.fixed(w, h))
    layout(c.maxWidth, c.maxHeight) { p.place(r.left.roundToInt(), (r.top + drop()).roundToInt()) }
}

/**
 * The tapped note row grown over the Notes area with the Home cards' spring, corner morph and pull-to-close. [content]
 * gets the close action (animates back into the row, then calls [onClosed]) and should scroll itself.
 */
@Composable
internal fun ExpandedNote(card: NoteOpen, onClosed: () -> Unit, content: @Composable (close: () -> Unit) -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spec = if (app.plait.ui.glass.LocalGlassEnv.current.reduceMotion) tween<Float>(180)
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
    val pull = remember(card) { Stretch(scope, bothEnds = false) { if (shouldClose(it, threshold)) latestClose() } }
    LaunchedEffect(card) { card.progress.animateTo(1f, spec) }
    androidx.activity.compose.BackHandler(enabled = !closing) { close() }
    // Quantised to half a dp so the shape only changes ~16 times over the whole morph.
    val radius by remember(card) { derivedStateOf { (morphRadius(NoteCardRadius, card.progress.value.coerceIn(0f, 1f)) * 2).roundToInt() / 2f } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 90.dp
    val dragState = rememberDraggableState { pull.drag(it) }
    // Takes every touch around the growing card, so the list underneath does not react.
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        Box(
            Modifier.fillMaxSize()
                .expandingNote(card, top = top, bottom = bottom, side = 18.dp) { rubberBand(pull.over, limit) }
                .plaitCard(RoundedCornerShape(radius.dp))
                .nestedScroll(pull)
                .draggable(dragState, Orientation.Vertical, onDragStopped = { pull.onPreFling(Velocity.Zero) }),
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = noteContentAlpha(card.progress.value) }) { content(close) }
        }
    }
}
