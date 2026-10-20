package app.plait.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import app.plait.ui.glass.liquidGlass
import app.plait.ui.theme.mContainer
import app.plait.ui.theme.mContainerHigh
import app.plait.ui.theme.LocalIsDark

/**
 * The look of Liquid Glass for small controls, drawn directly, like the clear glass pills over the home background: a
 * flat, see-through body (no milky gradient), a crisp light rim that fades from the top-left, and only a hairline of
 * sheen at the top edge. It samples nothing behind it, so it is cheap and safe inside cards and lists. [tint] colours
 * the glass: a strong tint (above 0.6) makes a solid coloured button like Join or Record, a light one a coloured pill;
 * leave it unspecified for clear glass.
 */
@Composable
fun Modifier.fauxGlass(shape: Shape, tint: Color = Color.Unspecified, strength: Float = 1f): Modifier {
    val dark = LocalIsDark.current
    val env = app.plait.ui.glass.LocalGlassEnv.current
    if (env.material) {
        // Material 3 Expressive: tonal, solid surfaces. A strong tint (a main action) becomes a filled button.
        val fill = if (tint.isSpecified && tint.alpha > 0.6f) tint.copy(alpha = 1f) else app.plait.ui.theme.LocalPlait.current.surfaceHigh
        return this.clip(shape).background(fill)
    }
    val strong = tint.isSpecified && tint.alpha > 0.6f
    // Clear glass: a flat, light veil, so what is behind still shows and nothing looks foggy.
    val clear = if (dark) Color.White.copy(alpha = 0.07f * strength) else Color.White.copy(alpha = 0.55f * strength)
    val edge = Brush.linearGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.55f else 0.95f), Color.White.copy(alpha = if (dark) 0.10f else 0.35f), Color.White.copy(alpha = if (dark) 0.30f else 0.7f)),
        start = Offset.Zero, end = Offset(220f, 220f),
    )
    // Like the record button: a coloured glow under a solid coloured button, so it reads as lit from within.
    val glow = if (!env.reduceMotion && strong) Modifier.shadow(10.dp, shape, clip = false, ambientColor = tint.copy(alpha = 1f), spotColor = tint.copy(alpha = 1f)) else Modifier
    return this
        .then(glow)
        .clip(shape)
        .background(clear)
        .then(
            when {
                strong -> Modifier.background(Brush.verticalGradient(listOf(tint.copy(alpha = (tint.alpha * 0.98f).coerceAtMost(1f)), tint.copy(alpha = (tint.alpha * 0.86f).coerceAtMost(1f)))))
                tint.isSpecified -> Modifier.background(tint.copy(alpha = tint.alpha.coerceAtMost(0.4f)))
                else -> Modifier
            }
        )
        .drawBehind {
            // A hairline of light along the top edge only.
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.35f), Color.Transparent), endY = size.height * 0.18f))
        }
        .border(1.dp, edge, shape)
}

/**
 * The surface of a card in the current style. Liquid Glass with a [backdrop] samples and blurs what is behind it;
 * without one it is drawn glass, which costs almost nothing; Material 3 Expressive and "glass effects off" give a
 * plain tonal surface. [tint] is the card's colour, carried gently in every style.
 */
@Composable
fun Modifier.plaitCard(
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    tint: Color = Color.Unspecified,
    backdrop: com.kyant.backdrop.Backdrop? = null,
): Modifier {
    val env = app.plait.ui.glass.LocalGlassEnv.current
    val p = app.plait.ui.theme.plait
    if (env.material || env.solid) {
        // One corner radius for every card in the Material style.
        val shape = if (env.material && shape is androidx.compose.foundation.shape.RoundedCornerShape) androidx.compose.foundation.shape.RoundedCornerShape(28.dp) else shape
        val base = if (env.material) p.surface else p.surface
        val fill = if (tint.isSpecified) androidx.compose.ui.graphics.lerp(base, tint.copy(alpha = 1f), if (env.material) 0.18f else 0.14f) else base
        return this.clip(shape).background(fill)
    }
    if (backdrop != null) {
        return this.liquidGlass(
            backdrop, shape = { shape }, tint = if (tint.isSpecified) tint.copy(alpha = 0.42f) else Color.Unspecified, lens = false,
        )
    }
    return this.fauxGlass(shape, if (tint.isSpecified) tint.copy(alpha = 0.30f) else Color.Unspecified, strength = 0.9f)
}
