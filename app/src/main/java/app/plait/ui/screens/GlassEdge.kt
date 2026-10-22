package app.plait.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
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
internal const val EdgeFeatherDp = 20f

internal fun edgeTint(dark: Boolean, material: Boolean, container: Color): Color =
    if (material) container else if (dark) Color(0xFF0E1316) else Color.White

internal fun edgeTintAlpha(dark: Boolean, material: Boolean): Float = if (material) 0.25f else if (dark) 0.30f else 0.22f

internal fun rimAlpha(dark: Boolean): Float = if (dark) 0.18f else 0.35f

/** Blur radius in dp, or null when the strip must go without blur (glass off, Material style handled by the caller, API < 31). */
internal fun edgeBlurRadiusDp(glassOn: Boolean, sdkInt: Int): Float? = if (glassOn && sdkInt >= 31) 18f else null

/** 0 at the top of the strip, 1 once past the feather; rises monotonically. */
internal fun featherAlphaAt(y: Float, featherPx: Float): Float = if (featherPx <= 0f) 1f else (y / featherPx).coerceIn(0f, 1f)

private val EdgeShape: () -> Shape = { RectangleShape }

/**
 * The strip itself, bottom-aligned in the card. [backdrop] is the layer the card content is recorded into; null means no
 * blur, just the feathered tint. It has no pointer input, so taps reach the card's click handler.
 */
@Composable
internal fun BoxScope.GlassEdge(backdrop: LayerBackdrop?, blurDp: Float?, dark: Boolean, material: Boolean, container: Color) {
    val tint = edgeTint(dark, material, container).copy(alpha = edgeTintAlpha(dark, material))
    val rim = rimAlpha(dark)
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bandHeightDp().dp)) {
        val body = Modifier.matchParentSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val feather = EdgeFeatherDp.dp.toPx()
                val mask = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black), startY = 0f, endY = feather.coerceAtLeast(1f),
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            }
        if (backdrop != null && blurDp != null) {
            Box(body.drawBackdrop(backdrop, EdgeShape, effects = { blur(blurDp.dp.toPx()) }, onDrawSurface = { drawRect(tint) }))
        } else {
            Box(body.background(tint))
        }
        // Rim and soft top highlight sit just under the feather, outside the mask, so they stay crisp.
        Box(
            Modifier.matchParentSize().drawWithCache {
                val y = EdgeFeatherDp.dp.toPx()
                val glow = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = rim * 0.35f), Color.Transparent), startY = y, endY = y + 14.dp.toPx(),
                )
                onDrawBehind {
                    drawRect(glow, topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, 14.dp.toPx()))
                    drawRect(Color.White.copy(alpha = rim), topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                }
            },
        )
    }
}
