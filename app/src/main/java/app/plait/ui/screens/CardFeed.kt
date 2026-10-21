package app.plait.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.plait.data.brief.Phase
import app.plait.ui.glass.LocalGlassEnv
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

/** What a card in the Home feed shows. Every phase has its own list, see [feedCards]. */
internal enum class FeedCardKind { Meetings, Tasks, Day, Tomorrow, WeekRecap }

internal fun feedCards(phase: Phase, lastWorkDay: Boolean): List<FeedCardKind> = when (phase) {
    Phase.Morning, Phase.Afternoon -> listOf(FeedCardKind.Meetings, FeedCardKind.Tasks)
    Phase.Evening -> if (lastWorkDay) listOf(FeedCardKind.WeekRecap, FeedCardKind.Day, FeedCardKind.Tomorrow) else listOf(FeedCardKind.Day, FeedCardKind.Tomorrow)
}

/** Every card is this share of the feed's height; the rest shows the neighbour peeking. */
internal const val CardShare = 0.75f
internal fun cardExtent(areaHeight: Float): Float = areaHeight * CardShare

/** First card sits at the top, every other card at the bottom: the neighbour peeks below the first and above the rest. */
internal fun snapStart(index: Int, layoutSize: Int, itemSize: Int): Int = if (index == 0) 0 else layoutSize - itemSize

private object CardSnap : SnapPosition {
    override fun position(layoutSize: Int, itemSize: Int, beforeContentPadding: Int, afterContentPadding: Int, itemIndex: Int, itemCount: Int): Int =
        snapStart(itemIndex, layoutSize, itemSize)
}

/** [offset] is 0 for the active card and 1 for one a page away. */
internal fun peekAlpha(offset: Float): Float = 1f + (0.35f - 1f) * offset.coerceIn(0f, 1f)
internal fun peekScale(offset: Float): Float = 1f + (0.94f - 1f) * offset.coerceIn(0f, 1f)

/** Quotes differ per card and phase: [Quotes.pick] spreads slots, so a feed never shows one quote twice. */
internal fun quoteSlot(phase: Phase, kind: FeedCardKind): Int = phase.ordinal + when (kind) {
    FeedCardKind.Tasks -> 3
    FeedCardKind.Tomorrow -> 4
    else -> 0
}

/**
 * Equal-size cards, one scroll: the active card takes 3/4 of the height and its neighbour peeks, faded, in the other
 * quarter. Snapping is a spring, so it settles with a bounce.
 */
@Composable
internal fun CardFeed(cards: List<FeedCardKind>, state: PagerState, modifier: Modifier = Modifier, card: @Composable (index: Int, kind: FeedCardKind) -> Unit) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.currentPage }.drop(1).collect { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    }
    val calm = LocalGlassEnv.current.reduceMotion
    BoxWithConstraints(modifier) {
        val fling = PagerDefaults.flingBehavior(
            state = state,
            snapAnimationSpec = if (calm) tween(160) else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        )
        VerticalPager(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp),
            pageSize = PageSize.Fixed(maxHeight * CardShare),
            pageSpacing = 12.dp,
            flingBehavior = fling,
            snapPosition = CardSnap,
            key = { cards[it].ordinal },
        ) { page ->
            // Pages are fixed-size, so a page's content fills it.
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().peekFade(state, page)) { card(page, cards[page]) }
        }
    }
}

/**
 * A card away from the active one fades, shrinks a little towards the edge facing the centre, and dissolves toward the
 * screen edge it points to. Everything is read inside the draw/layer lambdas, so scrolling never recomposes.
 */
private fun Modifier.peekFade(state: PagerState, page: Int): Modifier = this
    .graphicsLayer {
        val d = page - state.currentPage - state.currentPageOffsetFraction
        val o = abs(d)
        val s = peekScale(o)
        alpha = peekAlpha(o)
        scaleX = s
        scaleY = s
        transformOrigin = TransformOrigin(0.5f, if (d > 0f) 0f else 1f)
        compositingStrategy = CompositingStrategy.Offscreen
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
