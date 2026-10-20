package app.plait.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.drawWithContent

/**
 * Entrance animations only play while a screen is arriving. Rows that scroll into view later, or a
 * screen restored by Back, appear instantly: replaying them on every scroll was a main cause of
 * dropped frames.
 */
object Entrance {
    private var openedAt = 0L

    /** Call when the user moves forward to a new screen or tab. */
    fun arrived() { openedAt = System.currentTimeMillis() }

    fun playing(): Boolean = System.currentTimeMillis() - openedAt < WINDOW_MS

    private const val WINDOW_MS = 600L
}

/** Fades and lifts content in, staggered by [index]. Cheap: one layer, one animation, once. */
@Composable
fun Modifier.rise(index: Int = 0, offset: Dp = 14.dp): Modifier {
    val calm = app.plait.ui.glass.LocalGlassEnv.current.reduceMotion
    val damping = Motion.damping() - 0.08f
    val progress = remember { if (!calm && Entrance.playing()) Animatable(0f) else null } ?: return this
    LaunchedEffect(Unit) {
        delay(minOf(index, 5) * 45L)
        progress.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f))
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * offset.toPx()
    }
}

/**
 * Press feedback for anything tappable, the same as the red record button: the element lifts and grows on a
 * springy bounce, a soft light blooms under the finger, and a light haptic tick confirms the tap. The scale and
 * the light are drawn, not recomposed, so a press never costs a layout pass. Put it after `clip` so the light
 * follows the shape.
 */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.pressable(onClick: () -> Unit, role: Role = Role.Button, lift: Dp = 5.dp, onLongClick: (() -> Unit)? = null): Modifier {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val env = app.plait.ui.glass.LocalGlassEnv.current
    if (env.material) {
        // Material: a ripple on a springy squish, no glass bloom.
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val squish by androidx.compose.animation.core.animateFloatAsState(
            if (pressed) 0.96f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 500f), label = "squish",
        )
        val v = androidx.compose.ui.platform.LocalView.current
        return this.graphicsLayer { scaleX = squish; scaleY = squish }.combinedClickable(
            interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = role,
            onLongClick = onLongClick?.let { lc -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); lc() } },
            onClick = { v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); onClick() },
        )
    }
    val pressDamping = if (env.reduceMotion) 1f else 0.6f
    val press = remember(scope, pressDamping) { app.plait.ui.glass.GlassPress(scope, pressDamping) }
    val view = androidx.compose.ui.platform.LocalView.current
    return this
        .graphicsLayer {
            val sc = 1f + (lift.toPx() / size.maxDimension.coerceAtLeast(1f)) * press.progress
            scaleX = sc
            scaleY = sc
        }
        .then(press.bloom)
        .then(press.gesture)
        .combinedClickable(
            interactionSource = null, indication = null, role = role,
            onLongClick = onLongClick?.let { lc -> { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); lc() } },
            onClick = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        )
}

/** A soft ring that grows and fades behind a primary action. */
@Composable
fun PulseRing(color: Color, modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = CircleShape) {
    if (app.plait.ui.glass.LocalGlassEnv.current.reduceMotion) return
    val t = rememberInfiniteTransition(label = "pulse")
    val p = t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Restart), label = "p")
    Box(
        modifier
            .graphicsLayer {
                val s = 1f + p.value * 0.5f
                scaleX = s
                scaleY = s
                alpha = 0.5f * (1f - p.value)
            }
            .background(color, shape)
    )
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    background: Color,
    tint: Color,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
) {
    if (app.plait.ui.glass.LocalGlassEnv.current.material) {
        val plainBg = background == app.plait.ui.theme.plait.surface || background == app.plait.ui.theme.plait.surfaceHigh
        app.plait.ui.material.MIconButton(icon, description, onClick, Modifier, size, iconSize, if (plainBg) Color.Unspecified else background, tint)
        return
    }
    // Plain surface colours become clear glass; a strong colour (record red, accent) tints the glass.
    val plain = background == app.plait.ui.theme.plait.surface || background == app.plait.ui.theme.plait.surfaceHigh
    Box(
        Modifier
            .size(size)
            .fauxGlass(CircleShape, if (plain) Color.Unspecified else background.copy(alpha = 0.9f))
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(iconSize))
    }
}
