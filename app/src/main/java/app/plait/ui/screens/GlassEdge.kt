package app.plait.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A clipped Home card dissolves into a tall, eased tint fade (white in light, grey in dark). No blur: identical on every card, no late pop. */
internal fun bandHeightDp(): Float = 130f

internal fun edgeTint(dark: Boolean, material: Boolean, container: Color): Color =
    if (material) container else if (dark) Color(0xFF3A3A3E) else Color.White

/** Opacity reached at the very bottom of the band. */
internal fun edgeTintAlpha(dark: Boolean, material: Boolean): Float = if (dark && !material) 0.96f else 0.97f

/** 0 at the top of the strip, 1 once past the feather; rises monotonically. */
internal fun featherAlphaAt(y: Float, featherPx: Float): Float = if (featherPx <= 0f) 1f else (y / featherPx).coerceIn(0f, 1f)

/** Smoothstep of [featherAlphaAt]: starts and ends flat, so the strip has no visible top edge. */
internal fun fadeCurve(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** The fade, bottom-aligned in the card. No pointer input, so taps reach the card's click handler. */
@Composable
internal fun BoxScope.GlassEdge(dark: Boolean, material: Boolean, container: Color) {
    val tint = edgeTint(dark, material, container)
    val a = edgeTintAlpha(dark, material)
    val brush = androidx.compose.runtime.remember(tint, a) {
        Brush.verticalGradient(colorStops = Array(9) { i -> (i / 8f) to tint.copy(alpha = fadeCurve(i / 8f) * a) })
    }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bandHeightDp().dp).background(brush))
}
