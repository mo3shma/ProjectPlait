package app.plait.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.plait.data.brief.Phase
import app.plait.ui.glass.LocalGlassEnv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

/** What a card in the Home feed shows. Every phase has its own list, see [feedCards]. */
internal enum class FeedCardKind { Meetings, Tasks, Day, Tomorrow, WeekRecap }

internal fun feedCards(phase: Phase, lastWorkDay: Boolean): List<FeedCardKind> = when (phase) {
    Phase.Morning, Phase.Afternoon -> listOf(FeedCardKind.Meetings, FeedCardKind.Tasks)
    Phase.Evening -> if (lastWorkDay) listOf(FeedCardKind.WeekRecap, FeedCardKind.Day, FeedCardKind.Tomorrow) else listOf(FeedCardKind.Day, FeedCardKind.Tomorrow)
}

/** The phase page Home opens on: the one last chosen, if it was chosen in the part of the day it is now, else the current one. */
internal fun initialPhasePage(savedPage: Int, savedPhase: Int, nowPhase: Int): Int = if (savedPage >= 0 && savedPhase == nowPhase) savedPage else nowPhase

/** Every card is this share of the feed's height; the rest shows the neighbour peeking. */
internal const val CardShare = 0.75f
internal fun cardExtent(areaHeight: Float): Float = areaHeight * CardShare

/** A release past this share of a card moves to the next one, so a short flick is enough. */
internal const val FlickThreshold = 0.2f
/** The snap overshoots a little, visibly but not much. */
internal const val SnapDamping = 0.5f
/** Pulling past the first or last card moves the feed by at most this share of the finger's travel. */
internal const val RubberShare = 0.35f
internal val RubberLimit = 120.dp
/** At the far end of a drag the cards squeeze to this scale, and neighbours trail the finger by up to [NeighbourLag]. */
internal const val DragSquish = 0.98f
internal val NeighbourLag = 12.dp

/** First card sits at the top, every other card at the bottom: the neighbour peeks below the first and above the rest. */
internal fun snapStart(index: Int, layoutSize: Int, itemSize: Int): Int = if (index == 0) 0 else layoutSize - itemSize

private object CardSnap : SnapPosition {
    override fun position(layoutSize: Int, itemSize: Int, beforeContentPadding: Int, afterContentPadding: Int, itemIndex: Int, itemCount: Int): Int =
        snapStart(itemIndex, layoutSize, itemSize)
}

/** [offset] is 0 for the active card and 1 for one a page away. */
internal fun peekAlpha(offset: Float): Float = 1f + (0.35f - 1f) * offset.coerceIn(0f, 1f)
internal fun peekScale(offset: Float): Float = 1f + (0.94f - 1f) * offset.coerceIn(0f, 1f)

/** A phase page beside the open one sits back: smaller and fainter, by how far away it is. */
internal fun phaseAlpha(offset: Float): Float = 1f - 0.4f * offset.coerceIn(0f, 1f)

/** How far a pull of [drag] px past an end moves the feed: about [RubberShare] of it at first, never past [limit]. */
internal fun rubberBand(drag: Float, limit: Float): Float =
    if (limit <= 0f) 0f else sign(drag) * limit * (1f - exp(-RubberShare * abs(drag) / limit))

/** Scale of the cards mid-drag: 1 at rest, [DragSquish] halfway between cards or at half the rubber band. */
internal fun squish(pageFraction: Float, rubber: Float, limit: Float): Float {
    val s = maxOf(2f * abs(pageFraction), if (limit > 0f) 2f * abs(rubber) / limit else 0f).coerceAtMost(1f)
    return 1f - (1f - DragSquish) * s
}

/** How far a neighbour trails the finger: opposite to [travel], at most [max]. */
internal fun neighbourLag(travel: Float, max: Float): Float = (-travel * 0.1f).coerceIn(-max, max)

/** Quotes differ per card and phase: [Quotes.pick] spreads slots, so a feed never shows one quote twice. */
internal fun quoteSlot(phase: Phase, kind: FeedCardKind): Int = phase.ordinal + when (kind) {
    FeedCardKind.Tasks -> 3
    FeedCardKind.Tomorrow -> 4
    else -> 0
}

/** True when a card's content is taller than the card, so it is cut and offers "Show all". */
internal fun overflows(contentHeight: Int, cardHeight: Int): Boolean = cardHeight > 0 && contentHeight > cardHeight

/**
 * The drag a scrolling child did not use. Pulled past an end, it lands in [over] (the caller shows [rubberBand] of it)
 * and springs back on release; [travel] is the finger's way in this drag, for the parallax. [bothEnds] false only
 * takes pulls downward, past the top.
 */
@Stable
internal class Stretch(private val scope: CoroutineScope, private val bothEnds: Boolean, private val onRelease: (Float) -> Unit = {}) : NestedScrollConnection {
    var over by mutableFloatStateOf(0f)
        private set
    var travel by mutableFloatStateOf(0f)
        private set
    private var settling: Job? = null

    /** A drag on something that does not scroll: all of it is leftover. */
    fun drag(dy: Float) {
        val taken = onPreScroll(Offset(0f, dy), NestedScrollSource.UserInput).y
        onPostScroll(Offset.Zero, Offset(0f, dy - taken), NestedScrollSource.UserInput)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        settling?.cancel()
        val dy = available.y
        travel += dy
        // Pulled past an end and coming back: undo the pull before anything scrolls.
        if (over == 0f || dy == 0f || sign(dy) == sign(over)) return Offset.Zero
        val take = if (abs(dy) >= abs(over)) -over else dy
        over += take
        return Offset(0f, take)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y == 0f || (!bothEnds && available.y < 0f)) return Offset.Zero
        over += available.y
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val pulled = over
        if (pulled != 0f) onRelease(pulled)
        settle()
        // A release while pulled is the spring's: the content stays where it is.
        return if (pulled != 0f) available else Velocity.Zero
    }

    private fun settle() {
        val back = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
        settling = scope.launch {
            launch { animate(over, 0f, animationSpec = back) { v, _ -> over = v } }
            animate(travel, 0f, animationSpec = back) { v, _ -> travel = v }
        }
    }
}

/**
 * Equal-size cards, one scroll: the active card takes 3/4 of the height and its neighbour peeks, faded, in the other
 * quarter. A short flick moves one card and the snap bounces; past the first or last card the feed stretches and springs back.
 */
@Composable
internal fun CardFeed(
    cards: List<FeedCardKind>, state: PagerState, modifier: Modifier = Modifier, enabled: Boolean = true,
    card: @Composable (index: Int, kind: FeedCardKind) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.currentPage }.drop(1).collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    val calm = LocalGlassEnv.current.reduceMotion
    val scope = rememberCoroutineScope()
    val stretch = remember(scope) { Stretch(scope, bothEnds = true) }
    val density = LocalDensity.current
    val limit = with(density) { RubberLimit.toPx() }
    val lag = with(density) { NeighbourLag.toPx() }
    BoxWithConstraints(modifier.nestedScroll(stretch)) {
        val fling = PagerDefaults.flingBehavior(
            state = state,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
            snapAnimationSpec = if (calm) tween(160) else spring(dampingRatio = SnapDamping, stiffness = Spring.StiffnessLow, visibilityThreshold = 1f),
            snapPositionalThreshold = FlickThreshold,
        )
        VerticalPager(
            state = state,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = rubberBand(stretch.over, limit) },
            pageSize = PageSize.Fixed(maxHeight * CardShare),
            pageSpacing = 12.dp,
            flingBehavior = fling,
            userScrollEnabled = enabled,
            snapPosition = CardSnap,
            key = { cards[it].ordinal },
            // The ends stretch through [stretch]; the platform glow would fight it.
            overscrollEffect = null,
        ) { page ->
            // Pages are fixed-size, so a page's content fills it.
            Box(Modifier.fillMaxSize().peekFade(state, page, stretch, limit, lag)) { card(page, cards[page]) }
        }
    }
}

/**
 * A card away from the active one fades, shrinks a little towards the edge facing the centre, and dissolves toward the
 * screen edge it points to. Mid-drag every card squeezes a little and the neighbours trail the finger. Everything is
 * read inside the draw/layer lambdas, so scrolling never recomposes.
 */
private fun Modifier.peekFade(state: PagerState, page: Int, stretch: Stretch, limit: Float, lag: Float): Modifier = this
    .graphicsLayer {
        val d = page - state.currentPage - state.currentPageOffsetFraction
        val o = abs(d)
        val s = peekScale(o) * squish(state.currentPageOffsetFraction, rubberBand(stretch.over, limit), limit)
        alpha = peekAlpha(o)
        scaleX = s
        scaleY = s
        translationY = neighbourLag(stretch.travel, lag) * o.coerceAtMost(1f)
        transformOrigin = TransformOrigin(0.5f, if (d > 0f) 0f else 1f)
        compositingStrategy = if (o > 0.001f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithCache {
        // The visible peek is about a third of a card: the erase ramp covers that stretch, nearest edge untouched.
        val h = size.height
        val below = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = h * 0.34f)
        val above = Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = h * 0.66f, endY = h)
        onDrawWithContent {
            drawContent()
            val d = page - state.currentPage - state.currentPageOffsetFraction
            val o = abs(d).coerceIn(0f, 1f)
            if (o > 0.001f) drawRect(if (d > 0f) below else above, alpha = o * 0.9f, blendMode = BlendMode.DstOut)
        }
    }

/** Marks the one child of a [CardColumn] that takes the spare height. */
private object FillRest : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = FillRest
}

internal fun Modifier.fillRest(): Modifier = this.then(FillRest)

/**
 * A card's content: a column with 12 dp gaps whose [fillRest] child takes the height left over (but never less than it
 * needs), so a quote on an otherwise empty card fills it. Its minimum height is the card's; it may grow past that.
 */
@Composable
internal fun CardColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier, CardColumnPolicy)
}

private val CardColumnPolicy = MeasurePolicy { ms: List<Measurable>, c: Constraints ->
    val gap = 12.dp.roundToPx()
    val free = Constraints(maxWidth = c.maxWidth)
    val fill = ms.indexOfFirst { it.parentData === FillRest }
    val ps = arrayOfNulls<Placeable>(ms.size)
    var used = gap * (ms.size - 1).coerceAtLeast(0)
    for (i in ms.indices) if (i != fill) ps[i] = ms[i].measure(free).also { used += it.height }
    if (fill >= 0) {
        val h = maxOf(ms[fill].minIntrinsicHeight(c.maxWidth), c.minHeight - used)
        ps[fill] = ms[fill].measure(Constraints(maxWidth = c.maxWidth, minHeight = h, maxHeight = h))
        used += h
    }
    layout(c.maxWidth, used.coerceIn(c.minHeight, c.maxHeight)) {
        var y = 0
        for (p in ps) { p!!.place(0, y); y += p.height + gap }
    }
}

/** Keeps at least the card's height but lets the content run past the bottom (the card clips it); placed from the top. */
internal fun Modifier.growDown(): Modifier = layout { m, c ->
    val p = m.measure(c.copy(maxHeight = Constraints.Infinity))
    layout(p.width, c.maxHeight) { p.place(0, 0) }
}
