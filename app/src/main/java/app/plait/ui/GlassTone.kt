package app.plait.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ui.glass.LocalGlassEnv
import app.plait.ui.glass.liquidGlass
import app.plait.ui.theme.LocalIsDark
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.RoundedRectangle

/**
 * The one look for every control: a soft tinted glass pill with a crisp rim. [pastel] is what the tint looks like on a
 * light screen, [deep] the colour that is washed over a dark one.
 */
enum class GlassTone(val pastel: Color, val deep: Color) {
    Lavender(Color(0xFFD8DCFF), Color(0xFF8A96FF)),
    Mint(Color(0xFFCDEBDF), Color(0xFF3FBFA8)),
    Peach(Color(0xFFF7E6C9), Color(0xFFF2B35B)),
    Sky(Color(0xFFD3E6FF), Color(0xFF5B9BF0)),
    Rose(Color(0xFFF6D3E4), Color(0xFFE8725F)),
}

/** What a tinted control is drawn with: body colour, rim colour, and the ink of its label and icon. */
data class ToneStyle(val fill: Color, val rim: Color, val ink: Color)

object ToneStyles {
    /** Rim strength with glass effects off: the same shapes, a quieter edge. */
    const val FLAT_RIM = 0.6f

    /** Text is white on dark and black on light, always. */
    fun ink(dark: Boolean): Color = if (dark) Color.White else Color.Black

    fun resolve(tone: GlassTone, dark: Boolean, selected: Boolean = false, rimStrength: Float = 1f): ToneStyle {
        val fill = if (dark) tone.deep.copy(alpha = if (selected) 0.50f else 0.28f)
        else if (selected) lerp(tone.pastel, tone.deep, 0.32f) else tone.pastel.copy(alpha = 0.92f)
        return ToneStyle(fill, rim(tone.deep, dark, selected, rimStrength), ink(dark))
    }

    /** For a colour a call site already has (a note kind, a card's ink): a light wash of it. */
    fun fromTint(tint: Color, dark: Boolean, rimStrength: Float = 1f): ToneStyle =
        ToneStyle(tint.copy(alpha = tint.alpha.coerceIn(0.28f, 0.5f)), rim(tint.copy(alpha = 1f), dark, false, rimStrength), ink(dark))

    private fun rim(base: Color, dark: Boolean, selected: Boolean, strength: Float): Color {
        val a = (if (dark) 0.55f else 0.95f) * (if (selected) 1f else 0.9f) * strength
        return lerp(base, Color.White, if (dark) 0.45f else 0.8f).copy(alpha = a.coerceIn(0f, 1f))
    }
}

/** Small labels such as "Default" or a download size, drawn as tinted capsules. */
enum class BadgeKind { Default, Included, BestMatch, Size, Info, Warning }

fun badgeTone(kind: BadgeKind): GlassTone = when (kind) {
    BadgeKind.Default, BadgeKind.Included -> GlassTone.Mint
    BadgeKind.BestMatch -> GlassTone.Lavender
    BadgeKind.Size -> GlassTone.Sky
    BadgeKind.Info -> GlassTone.Peach
    BadgeKind.Warning -> GlassTone.Rose
}

/** One height scale for every control in both styles, so a button, a chip, an icon button and the bars always line up. */
object ControlHeights {
    val Small = 40.dp
    val Medium = 48.dp
    val Large = 56.dp
    /** The bottom navigation bar and the "+" button beside it. */
    val Bar = 64.dp
    val Fab = Bar
    /** Round buttons are as tall as the text buttons next to them. */
    val IconButton = Medium
}

/** A tinted glass body: flat colour, crisp 1 dp rim, no white veil. */
@Composable
fun Modifier.toneGlass(shape: Shape, style: ToneStyle): Modifier = this.clip(shape).background(style.fill).border(1.dp, style.rim, shape)

@Composable
fun Modifier.toneGlass(shape: Shape, tone: GlassTone, selected: Boolean = false): Modifier {
    val flat = LocalGlassEnv.current.solid
    return toneGlass(shape, ToneStyles.resolve(tone, LocalIsDark.current, selected, if (flat) ToneStyles.FLAT_RIM else 1f))
}

/** A small tinted capsule for a status or a size. */
@Composable
fun PlaitBadge(label: String, kind: BadgeKind = BadgeKind.Info, modifier: Modifier = Modifier) {
    val style = ToneStyles.resolve(badgeTone(kind), LocalIsDark.current, rimStrength = if (LocalGlassEnv.current.solid) ToneStyles.FLAT_RIM else 1f)
    Box(modifier.height(24.dp).toneGlass(CircleShape, style).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(label, style = PlaitType.caption.copy(fontSize = 12.sp, lineHeight = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = style.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An icon in a small tinted glass circle: the leading mark of a menu row. */
@Composable
fun ToneIcon(icon: ImageVector, tone: GlassTone, modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val style = ToneStyles.resolve(tone, LocalIsDark.current, rimStrength = if (LocalGlassEnv.current.solid) ToneStyles.FLAT_RIM else 1f)
    Box(modifier.size(size).toneGlass(CircleShape, style), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = style.ink, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * The surface of a menu or dialog. Liquid Glass with effects on and a [backdrop]: the glass blurs what is behind it under a
 * strong tint. Without a backdrop, or with effects off: a flat tinted panel. Both have the crisp rim and a soft shadow.
 * Material 3 Expressive: the tonal container, no blur.
 */
@Composable
fun Modifier.glassPanel(shape: Shape, backdrop: Backdrop?): Modifier {
    val env = LocalGlassEnv.current
    if (env.material) return this.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    val dark = LocalIsDark.current
    val rim = Brush.linearGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.5f else 0.95f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = if (dark) 0.3f else 0.7f)),
        start = Offset.Zero, end = Offset(600f, 600f), tileMode = androidx.compose.ui.graphics.TileMode.Clamp,
    )
    val shadow = this.shadow(24.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
    val body = if (backdrop != null && !env.solid) {
        val tintFill = if (dark) Color(0xFF121A1F).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.66f)
        shadow.liquidGlass(backdrop, shape = { shape }, lens = false, surfaceColor = tintFill)
    } else {
        val flat = lerp(plait.surfaceHigh, GlassTone.Lavender.deep, if (dark) 0.10f else 0.14f)
        shadow.clip(shape).background(flat)
    }
    return body.border(1.dp, rim, shape)
}

val PanelShape: Shape get() = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)

/** One row of a menu: a tinted icon, an ink label, 56 dp tall, lit when pressed. */
@Composable
fun MenuRow(label: String, icon: ImageVector, tone: GlassTone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.height(ControlHeights.Large).clip(CircleShape).pressable(onClick, lift = 2.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ToneIcon(icon, tone)
        Text(label, style = PlaitType.bodyStrong, color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun ToneStyle.lighter(k: Float): ToneStyle = copy(fill = fill.copy(alpha = fill.alpha * k))

/**
 * A card body in the tone: glass tint with a crisp rim (flat, quieter rim with effects off). [inner] is the lighter block
 * inside a row, [strong] the hero row. Material 3 Expressive: a tonal container, [rotation] picking secondary, tertiary or
 * primary so neighbours differ.
 */
@Composable
fun Modifier.toneSurface(shape: Shape, tone: GlassTone, rotation: Int = tone.ordinal, inner: Boolean = false, strong: Boolean = false): Modifier {
    val env = LocalGlassEnv.current
    if (env.material) {
        val cs = MaterialTheme.colorScheme
        val c = when (rotation.mod(3)) { 0 -> cs.secondaryContainer; 1 -> cs.tertiaryContainer; else -> cs.primaryContainer }
        return this.clip(shape).background(if (inner) lerp(c, cs.surface, 0.45f) else c)
    }
    val base = ToneStyles.resolve(tone, LocalIsDark.current, strong, if (env.solid) ToneStyles.FLAT_RIM else 1f)
    return toneGlass(shape, base.lighter(if (inner) 0.45f else 0.7f))
}

/** Menu rows take their tones in turn, so a list reads as coloured, not uniform. */
fun toneAt(index: Int): GlassTone = GlassTone.entries[index.mod(GlassTone.entries.size)]
