package app.plait.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** A clipped Home card ends in a frosted liquid-glass strip the content sinks behind. */
internal fun bandHeightDp(): Float = 72f

internal fun edgeTint(dark: Boolean, material: Boolean, container: Color): Color =
    if (material) container else if (dark) Color(0xFF0E1316) else Color.White

internal fun edgeTintAlpha(dark: Boolean, material: Boolean): Float = if (material) 0.25f else if (dark) 0.30f else 0.22f

/** Blur radius in dp, or null when the strip must go without blur (glass off, Material style handled by the caller, API < 31). */
internal fun edgeBlurRadiusDp(glassOn: Boolean, sdkInt: Int): Float? = if (glassOn && sdkInt >= 31) 18f else null

/** 0 at the top of the strip, 1 once past the feather; rises monotonically. */
internal fun featherAlphaAt(y: Float, featherPx: Float): Float = if (featherPx <= 0f) 1f else (y / featherPx).coerceIn(0f, 1f)

/** Smoothstep of [featherAlphaAt]: starts and ends flat, so the strip has no visible top edge. */
internal fun fadeCurve(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

private val EdgeShape: () -> Shape = { RectangleShape }

/**
 * The strip itself, bottom-aligned in the card. [backdrop] is the layer the card content is recorded into; null means no
 * blur, just the feathered tint. It has no pointer input, so taps reach the card's click handler.
 */
@Composable
internal fun BoxScope.GlassEdge(backdrop: LayerBackdrop?, blurDp: Float?, dark: Boolean, material: Boolean, container: Color) {
    val tint = edgeTint(dark, material, container).copy(alpha = edgeTintAlpha(dark, material))
    // Blur is only passed for the active card; when it arrives (or leaves) the two strips cross-fade instead of popping.
    val blurOn = backdrop != null && blurDp != null
    val mix by androidx.compose.animation.core.animateFloatAsState(if (blurOn) 1f else 0f, androidx.compose.animation.core.tween(220), label = "edgeBlur")
    var lastBackdrop by remember { mutableStateOf(backdrop) }
    var lastBlur by remember { mutableStateOf(blurDp) }
    if (blurOn) { lastBackdrop = backdrop; lastBlur = blurDp }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bandHeightDp().dp)) {
        fun body(a: () -> Float) = Modifier.matchParentSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = a() }
            .drawWithCache {
                // The whole band is the ramp: 0 at its top, full at the very bottom, eased so no line shows.
                val stops = Array(9) { i -> (i / 8f) to Color.Black.copy(alpha = fadeCurve(i / 8f)) }
                val mask = Brush.verticalGradient(colorStops = stops, startY = 0f, endY = size.height.coerceAtLeast(1f))
                onDrawWithContent {
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            }
        val bd = lastBackdrop; val bl = lastBlur
        if (mix < 1f) Box(body { 1f - mix }.background(tint))
        if (mix > 0f && bd != null && bl != null) {
            Box(body { mix }.drawBackdrop(bd, EdgeShape, effects = { blur(bl.dp.toPx()) }, onDrawSurface = { drawRect(tint) }))
        }
    }
}
