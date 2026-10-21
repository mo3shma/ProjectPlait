package app.plait.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import app.plait.data.brief.Phase
import kotlin.math.roundToInt

/** Corner radius of an open card, and of a closed one in each style. */
internal const val OpenRadius = 36f
internal const val GlassCardRadius = 44f
internal const val MaterialCardRadius = 36f

/** A downward pull from the top of an open card past this closes it. */
internal const val PullToCloseDp = 120f

/** The open card's spring, both ways. */
internal const val ExpandDamping = 0.8f
internal const val ExpandStiffness = 380f

/** One tapped card while it is open: where it started and how far it has grown (0 the card, 1 the whole Home area). */
internal class OpenCard(val phase: Phase, val kind: FeedCardKind, val from: Rect) {
    val progress = Animatable(0f)
}

internal fun expandedBounds(from: Rect, to: Rect, progress: Float): Rect = lerp(from, to, progress)

/** Corner radius in dp at [progress], from the card's radius to [OpenRadius]. */
internal fun morphRadius(cardRadius: Float, progress: Float): Float = cardRadius + (OpenRadius - cardRadius) * progress

internal fun shouldClose(pullPx: Float, thresholdPx: Float): Boolean = pullPx > thresholdPx

/**
 * Sizes and places the card between its rectangle in the feed and the Home area (the parent's size less the insets), by
 * [OpenCard.progress]; [drop] moves it down while it is being pulled to close. Read during layout, so the animation
 * never recomposes.
 */
internal fun Modifier.expanding(card: OpenCard, top: Dp, bottom: Dp, side: Dp, drop: () -> Float): Modifier = layout { m, c ->
    val to = Rect(side.toPx(), top.toPx(), c.maxWidth - side.toPx(), c.maxHeight - bottom.toPx())
    val r = expandedBounds(card.from, to, card.progress.value)
    val w = r.width.roundToInt().coerceAtLeast(0)
    val h = r.height.roundToInt().coerceAtLeast(0)
    val p = m.measure(Constraints.fixed(w, h))
    layout(c.maxWidth, c.maxHeight) { p.place(r.left.roundToInt(), (r.top + drop()).roundToInt()) }
}

/** Pushes the content down by [by] px (the open card's top bar fading in) and shortens it to match. */
internal fun Modifier.pushDown(by: () -> Int): Modifier = layout { m, c ->
    val off = by().coerceIn(0, c.maxHeight)
    val p = m.measure(c.copy(minHeight = (c.minHeight - off).coerceAtLeast(0), maxHeight = c.maxHeight - off))
    layout(c.maxWidth, c.maxHeight) { p.place(0, off) }
}
