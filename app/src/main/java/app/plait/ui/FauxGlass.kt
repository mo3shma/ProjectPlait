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
 * The look of Liquid Glass for small controls, drawn directly: a translucent gradient body, a bright
 * edge that fades from the top-left, and a soft sheen across the top. It samples nothing behind it, so it is
 * cheap and safe to put inside cards and lists (real refraction needs a backdrop layer and is used for the
 * floating bars and the home cards). [tint] colours the glass; leave it unspecified for clear glass.
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
    val base = if (dark) Color.White else Color.Black
    val top = if (tint.isSpecified) tint.copy(alpha = (tint.alpha * 0.95f).coerceAtMost(1f)) else base.copy(alpha = (if (dark) 0.20f else 0.08f) * strength)
    val bottom = if (tint.isSpecified) tint.copy(alpha = (tint.alpha * 0.78f).coerceAtMost(1f)) else base.copy(alpha = (if (dark) 0.09f else 0.04f) * strength)
    val edge = Brush.linearGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.70f else 0.95f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = if (dark) 0.34f else 0.6f)),
        start = Offset.Zero, end = Offset(220f, 220f),
    )
    // Like the record button: a coloured glow under tinted glass, so it reads as lit from within.
    val glow = if (!env.reduceMotion && tint.isSpecified && tint.alpha > 0.6f) Modifier.shadow(10.dp, shape, clip = false, ambientColor = tint.copy(alpha = 1f), spotColor = tint.copy(alpha = 1f)) else Modifier
    return this
        .then(glow)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, bottom)))
        .drawBehind {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.26f else 0.55f), Color.Transparent), endY = size.height * 0.5f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = if (dark) 0.10f else 0.18f)), startY = size.height * 0.7f))
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
