package app.plait.ui.glass

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import app.plait.ui.theme.LocalIsDark
import app.plait.ui.theme.mContainerHigh
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Liquid Glass for Android, modelled on Apple's two variants
 * (Human Interface Guidelines › Materials):
 *
 *  - [Regular] blurs and adjusts luminosity of what is behind it so text stays legible.
 *  - [Clear] is highly translucent and belongs over visually rich backgrounds; Apple
 *    suggests a ~35% dimming layer when the content behind is bright.
 *
 * Apple publishes no optical parameters, so refraction, blur radii and spring values here
 * are tuned by eye. Backdrop capture, blur and lens refraction come from
 * io.github.kyant0:backdrop (Apache-2.0); the press behaviour follows that project's
 * LiquidButton example.
 */
enum class GlassVariant { Regular, Clear }

/** Accessibility and platform state that decides how much glass we can draw. */
@Stable
class GlassEnv(
    /** System animations are off: no looping decoration. */
    val reduceMotion: Boolean,
    /** High text contrast is on, the OS cannot blur, or glass is switched off: use plain surfaces. */
    val solid: Boolean,
    /** The Material 3 Expressive style is chosen: tonal surfaces and bouncier springs instead of glass. */
    val material: Boolean = false,
)

val LocalGlassEnv = compositionLocalOf { GlassEnv(reduceMotion = false, solid = false) }

@Composable
fun ProvideGlassEnv(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val glassOn = app.plait.ui.theme.Appearance.glass
    val material = app.plait.ui.theme.Appearance.activeStyle == app.plait.ui.theme.UiStyle.Material
    val calm = app.plait.ui.theme.Appearance.calm
    val env = remember(context, glassOn, material, calm) {
        val resolver = context.contentResolver
        val animScale = runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        val highContrast = runCatching {
            Settings.Secure.getInt(resolver, "high_text_contrast_enabled", 0) == 1
        }.getOrDefault(false)
        GlassEnv(
            reduceMotion = animScale == 0f || calm,
            solid = highContrast || Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !glassOn || material,
            material = material,
        )
    }
    CompositionLocalProvider(LocalGlassEnv provides env, content = content)
}

/**
 * Tracks a finger on a glass element. Apple's interactive glass scales up slightly, bounces
 * and lights up under the touch; [progress] drives the scale and [position] the light.
 * Pointer events are observed on the Initial pass and never consumed, so clicks still work.
 */
@Stable
class GlassPress(private val scope: CoroutineScope, damping: Float = 0.5f) {
    private val progressAnim = Animatable(0f, 0.001f)
    private val positionAnim = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private val pressSpec = spring<Float>(dampingRatio = damping, stiffness = 300f, visibilityThreshold = 0.001f)

    val progress: Float get() = progressAnim.value
    val position: Offset get() = positionAnim.value

    val gesture: Modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            scope.launch { positionAnim.snapTo(down.position) }
            scope.launch { progressAnim.animateTo(1f, pressSpec) }
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && change.pressed) {
                    scope.launch { positionAnim.snapTo(change.position) }
                }
            } while (event.changes.any { it.pressed })
            scope.launch { progressAnim.animateTo(0f, pressSpec) }
        }
    }

    /** Soft specular bloom under the finger. */
    val bloom: Modifier = Modifier.drawWithContent {
        val p = progressAnim.value
        if (p > 0f) {
            drawRect(Color.White.copy(alpha = 0.07f * p), blendMode = BlendMode.Plus)
            val center = Offset(
                positionAnim.value.x.fastCoerceIn(0f, size.width),
                positionAnim.value.y.fastCoerceIn(0f, size.height),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.24f * p), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 1.3f,
                ),
                blendMode = BlendMode.Plus,
            )
        }
        drawContent()
    }
}

/**
 * Draws Liquid Glass behind this element. [backdrop] must be the layer that holds the
 * content scrolling underneath (see `Modifier.layerBackdrop`).
 *
 * @param tint Suggests prominence (Apple: "assign a tint color to suggest prominence").
 * @param interactive Reacts to touch with a springy lift and a light bloom.
 * @param dim Dark layer (0–1) behind Clear glass over bright content; Apple suggests 0.35.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: Backdrop,
    shape: () -> Shape = { Capsule() },
    variant: GlassVariant = GlassVariant.Regular,
    tint: Color = Color.Unspecified,
    interactive: Boolean = false,
    dim: Float = 0f,
    lift: Dp = 4.dp,
    exported: LayerBackdrop? = null,
    /** Skip the refraction shader (big surfaces): the glass still blurs, tints and catches light at its edge. */
    lens: Boolean = true,
    /** Replaces the default frosted body colour. */
    surfaceColor: Color? = null,
): Modifier {
    val env = LocalGlassEnv.current
    val dark = LocalIsDark.current
    val scope = rememberCoroutineScope()
    val press = remember(scope) { GlassPress(scope) }

    val surface = when {
        surfaceColor != null -> surfaceColor
        env.material -> app.plait.ui.theme.LocalPlait.current.surfaceHigh
        env.solid -> if (dark) Color(0xFF1F2A31).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f)
        variant == GlassVariant.Regular ->
            if (dark) (if (app.plait.ui.theme.Appearance.amoled) Color.Black.copy(alpha = 0.42f) else Color(0xFF121A1F).copy(alpha = 0.34f)) else Color(0xFFFAFAFA).copy(alpha = 0.42f)
        else -> Color.Transparent
    }

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = shape,
            exportedBackdrop = exported,
            effects = {
                if (!env.solid) {
                    val minDim = size.minDimension
                    val refractionHeight = minOf(16.dp.toPx(), minDim * 0.45f)
                    val refractionAmount = minOf(32.dp.toPx(), minDim * 0.9f)
                    when (variant) {
                        GlassVariant.Regular -> {
                            vibrancy()
                            blur(if (lens) 10.dp.toPx() else 18.dp.toPx())
                            if (lens) lens(refractionHeight, refractionAmount)
                        }
                        GlassVariant.Clear -> {
                            blur(2.dp.toPx())
                            lens(refractionHeight, refractionAmount)
                        }
                    }
                }
            },
            layerBlock = if (interactive) {
                {
                    val s = 1f + (lift.toPx() / size.maxDimension) * press.progress
                    scaleX = s
                    scaleY = s
                }
            } else {
                null
            },
            onDrawSurface = {
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
                val tinted = tint.isSpecified && tint.alpha > 0f
                if (tinted && env.solid && !env.material) {
                    // Glass off: the plain surface goes underneath and the colour on top, so Join, Record and other
                    // coloured controls keep their colour (and their label stays readable).
                    if (surface != Color.Transparent) drawRect(surface)
                    drawRect(tint.copy(alpha = if (tint.alpha >= 0.6f) 1f else tint.alpha))
                } else {
                    if (tinted) {
                        if (env.material) {
                            // Material 3: a main action is a solid filled container, a soft tint is a light wash.
                            drawRect(tint.copy(alpha = if (tint.alpha >= 0.6f) 1f else 0.30f))
                        } else {
                            drawRect(tint, blendMode = BlendMode.Hue)
                            drawRect(tint.copy(alpha = 0.75f * tint.alpha))
                        }
                    }
                    if (surface != Color.Transparent) drawRect(surface)
                }
            },
        )
        .then(if (interactive) press.bloom.then(press.gesture) else Modifier)
}

val GlassCircle: () -> Shape = { CircleShape }
