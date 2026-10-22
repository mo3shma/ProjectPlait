package app.plait.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.plait.data.brief.Phase
import app.plait.ui.glass.LocalGlassEnv
import kotlin.math.PI
import kotlin.math.sin

/** Text colour on the home cards: dark on the bright morning and noon skies, white on the dusk. */
val LocalCardInk = compositionLocalOf { Color(0xFF0E1316) }

/** White in dark mode and black in light mode, on every card. The skies are drawn dark or pale to match. */
internal fun cardInk(dark: Boolean): Color = Color.Black // the cards keep their light colours in dark mode too

/** The tint of glass rows and tiles on a card: smoked in dark mode, frosted white in light mode. */
@Composable
internal fun cardGlass(strong: Boolean = false): Color =
    Color.White.copy(alpha = if (strong) 0.7f else 0.55f)

/** A slow 0..1 loop; holds still when the person has animations switched off. */
@Composable
private fun loop(ms: Int, reverse: Boolean = true): State<Float> {
    if (LocalGlassEnv.current.reduceMotion) return remember { mutableFloatStateOf(0.5f) }
    return rememberInfiniteTransition(label = "sky").animateFloat(
        0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), if (reverse) RepeatMode.Reverse else RepeatMode.Restart), label = "t",
    )
}

/** The four colours of the sky from top to bottom for this phase, theme and hour. */
private fun skyStops(phase: Phase, hour: Float, dark: Boolean): List<Color> {
    fun c(v: Long) = Color(v or 0xFF000000)
    return when (phase) {
        Phase.Morning -> if (dark) listOf(c(0x1E1535), c(0x5A2748), c(0x9A3F3A), c(0xB85A2C)) else listOf(c(0xFFF4D6), c(0xFFE0B8), c(0xFFCBB0), c(0xFFB8B8))
        Phase.Afternoon -> if (dark) listOf(c(0x06162B), c(0x0B2B52), c(0x0F3C68), c(0x134C7E)) else listOf(c(0xA9DBFF), c(0xC4E6FF), c(0xD6EEFF), c(0xEAF7FF))
        Phase.Evening -> {
            // 17:00 is dusk, 22:00 is night.
            val n = ((hour - 17f) / 5f).coerceIn(0f, 1f)
            fun m(x: Long, y: Long) = androidx.compose.ui.graphics.lerp(c(x), c(y), n)
            if (dark) listOf(m(0x3B2A7A, 0x05060F), m(0xB4468C, 0x111238), m(0xFF7A59, 0x2A1B50), m(0xFFB46B, 0x3A2250))
            else listOf(m(0xDDD5FF, 0xC4B8F4), m(0xF7CBE6, 0xE6AED6), m(0xFFD6C4, 0xF4BBA8), m(0xFFEACB, 0xF8D6AC))
        }
    }
}

/**
 * The sky's colour field, drawn by Compose so it always moves: the gradient slowly flows between its stops and
 * back. The sun, rays, clouds and stars are laid over it by the GSAP layer below. [hour] is the local hour with minutes
 * as a fraction.
 */
@Composable
private fun PhaseSkyCanvas(phase: Phase, hour: Float, dark: Boolean, modifier: Modifier = Modifier) {
    // With glass effects off (the default) the sky is a still gradient: nothing redraws every frame.
    val still = LocalGlassEnv.current.solid
    val flow = if (still) remember { mutableFloatStateOf(0.5f) } else loop(8000)
    val stops = remember(phase, hour.toInt() * 4, dark) { skyStops(phase, hour, dark) }
    Canvas(modifier) {
        val t = flow.value
        val shifted = stops.mapIndexed { i, col -> androidx.compose.ui.graphics.lerp(col, stops[minOf(i + 1, stops.size - 1)], 0.55f * t) }
        drawRect(
            Brush.verticalGradient(shifted, startY = -size.height * 0.12f * t, endY = size.height * (1f + 0.12f * (1f - t))),
            alpha = 0.78f,
        )
    }
}

/**
 * The sky is a small web page animated with GSAP (bundled in assets, no network). It sits behind the card's
 * content and takes no touches. If the phone has no usable WebView, the drawn version above is used instead.
 */
@Composable
fun PhaseSky(phase: Phase, hour: Float, modifier: Modifier = Modifier, active: Boolean = true) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val still = LocalGlassEnv.current.reduceMotion
    // The home cards look the same in dark mode as in light: pale skies with dark text.
    val dark = false
    val name = when (phase) { Phase.Morning -> "morning"; Phase.Afternoon -> "noon"; Phase.Evening -> "evening" }
    val hourKey = (hour * 4).toInt() // the evening darkens a quarter hour at a time
    val env = LocalGlassEnv.current
    // The web layer only runs on the page you are looking at, and not at all with reduced animations, plain surfaces or Material.
    val webAllowed = !env.reduceMotion && !env.solid
    // One WebView per card, built the first time the card is active and then only paused/hidden, never rebuilt on a flip.
    var web by remember { mutableStateOf<android.webkit.WebView?>(null) }
    androidx.compose.runtime.LaunchedEffect(webAllowed, active) {
        if (webAllowed && active && web == null) {
            web = runCatching {
                object : android.webkit.WebView(context) {
                    // The sky is decoration: it must never take a touch, or the card under it cannot be tapped.
                    override fun onTouchEvent(event: android.view.MotionEvent?) = false
                    override fun dispatchTouchEvent(event: android.view.MotionEvent?) = false
                }.apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = true
                    isClickable = false; isFocusable = false; isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                }
            }.getOrNull()?.also { v -> SkyWebs.add(v) { if (web === v) web = null } }
        } else if (!webAllowed && web != null) {
            web?.let { SkyWebs.release(it) }; web = null
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { web?.let { SkyWebs.release(it) } } }
    // A drawn sky is always underneath, so there is colour even if the web layer is slow or unavailable.
    PhaseSkyCanvas(phase, hour, dark, modifier)
    val view = web ?: return
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { view },
        modifier = modifier,
        update = {
            // onPause/onResume are per view; pauseTimers/resumeTimers are process-wide and are not used.
            if (active) {
                SkyWebs.touch(it)
                it.visibility = android.view.View.VISIBLE; it.onResume()
                val url = "file:///android_asset/sky/sky.html#$name,${hourKey / 4f},${if (still) 1 else 0}"
                if (it.tag != url) { it.tag = url; it.loadUrl("about:blank"); it.loadUrl(url) }
            } else {
                it.onPause(); it.visibility = android.view.View.INVISIBLE
            }
        },
    )
}

/** The live sky WebViews, least recently active first. Past [MAX] the oldest is destroyed and its card falls back to the drawn sky. */
private object SkyWebs {
    private const val MAX = 2
    private val live = LinkedHashMap<android.webkit.WebView, () -> Unit>()

    fun add(v: android.webkit.WebView, onEvicted: () -> Unit) {
        live[v] = onEvicted
        while (live.size > MAX) {
            val old = live.keys.first { it !== v }
            live.remove(old)?.invoke()
            destroy(old)
        }
    }

    fun touch(v: android.webkit.WebView) { live.remove(v)?.let { live[v] = it } }

    fun release(v: android.webkit.WebView) { if (live.remove(v) != null) destroy(v) }

    private fun destroy(v: android.webkit.WebView) { v.stopLoading(); v.loadUrl("about:blank"); v.destroy() }
}
