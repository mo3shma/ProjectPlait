package app.plait.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.plait.ui.glass.LocalGlassEnv
import com.kyant.backdrop.Backdrop

/** One open dialog, drawn by [OverlayHost]. */
@Stable
class OverlayLayer(val content: @Composable () -> Unit)

/** Dialogs register here so they are drawn in the app's own composition (where glass can blur the screen), not in a window. */
@Stable
class OverlayState { val layers = mutableStateListOf<OverlayLayer>() }

/** The one set of scrim strengths: a menu or dialog, and a bottom sheet. */
object Dim {
    const val Menu = 0.45f
    const val Sheet = 0.55f
}

/**
 * Every live [DimScrim] registers a reader of its animated alpha here. [alpha] is the strongest one; the root status fade
 * reads it inside a draw lambda, so a changing dim redraws that one rect and recomposes nothing.
 */
object ScreenDim {
    private val readers = mutableStateListOf<() -> Float>()
    fun register(reader: () -> Float) { readers.add(reader) }
    fun unregister(reader: () -> Float) { readers.remove(reader) }
    val alpha: Float get() = maxOfReaders(readers)

    /** The strongest of [readers]; an index loop, so no iterator is allocated per frame. */
    internal fun maxOfReaders(readers: List<() -> Float>): Float {
        var m = 0f
        for (i in readers.indices) m = maxOf(m, readers[i]())
        return m
    }

    /** How much of the light status fade survives under a dim: gone once the dim reaches 0.4, so Dim.Menu and Dim.Sheet both hide it. */
    fun fadeMultiplier(dim: Float): Float = 1f - minOf(1f, dim * 2.5f).coerceAtLeast(0f)
}

/**
 * The single scrim for every sheet, menu and dialog: an edge-to-edge rect (behind the status and navigation bars) whose alpha
 * animates in the draw lambda, so it never recomposes per frame. It publishes its alpha to [ScreenDim] and a tap calls
 * [onDismiss] (taps pass through when it is hidden or [onDismiss] is null).
 */
@Composable
fun DimScrim(visible: Boolean, level: Float, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    // Starts hidden and flips on after the first frame, so a scrim that appears with its screen still fades in.
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { on = visible }
    val a = animateFloatAsState(if (on && visible) level else 0f, tween(160), label = "dim")
    val reader = remember { { a.value } }
    DisposableEffect(reader) {
        ScreenDim.register(reader)
        onDispose { ScreenDim.unregister(reader) }
    }
    // Nothing is composed while no dim is showing, so a hidden scrim can never sit over the app and swallow touches.
    val showing by remember { androidx.compose.runtime.derivedStateOf { visible || a.value > 0.001f } }
    if (showing) {
        Box(
            modifier.fillMaxSize()
                .drawBehind { val v = a.value; if (v > 0.001f) drawRect(Color.Black, alpha = v) }
                .then(if (visible && onDismiss != null) Modifier.clickable(interactionSource = null, indication = null) { onDismiss() } else Modifier),
        )
    }
}

val LocalOverlay =staticCompositionLocalOf<OverlayState?> { null }

/** The layer a pop-up blurs. Only provided inside [OverlayHost], so a panel never samples itself. */
val LocalPopupBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Draws every open dialog above the app. Put it last in the root box; [backdrop] is the layer that holds the app. */
@Composable
fun OverlayHost(state: OverlayState, backdrop: Backdrop) {
    for (layer in state.layers) {
        androidx.compose.runtime.key(layer) {
            CompositionLocalProvider(LocalPopupBackdrop provides backdrop) { layer.content() }
        }
    }
}

/**
 * A dialog in the overlay layer: a scrim, the content centred (it lifts above the keyboard), Back and a tap outside close it
 * with [onDismissRequest]. Without a host (a preview) it falls back to a platform dialog.
 */
@Composable
fun PlaitDialog(onDismissRequest: () -> Unit, fullScreen: Boolean = false, content: @Composable () -> Unit) {
    val host = LocalOverlay.current
    if (host == null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = !fullScreen)) { content() }
        return
    }
    val dismiss by rememberUpdatedState(onDismissRequest)
    val body by rememberUpdatedState(content)
    val full by rememberUpdatedState(fullScreen)
    // Built once: it reads the latest callbacks through the states above, so recomposing the caller writes nothing.
    val layer = remember {
        OverlayLayer {
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            BackHandler { dismiss() }
            Box(Modifier.fillMaxSize()) {
            DimScrim(shown, Dim.Menu, { dismiss() })
            Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
                // Swallows taps on the panel itself so they do not reach the scrim.
                Box(
                    Modifier.padding(horizontal = if (full) 0.dp else 24.dp, vertical = 16.dp).widthIn(max = if (full) 2000.dp else 560.dp)
                        .clickable(interactionSource = null, indication = null) {},
                ) { body() }
            }
            }
        }
    }
    DisposableEffect(layer) {
        host.layers.add(layer)
        onDispose { host.layers.remove(layer) }
    }
}


@Composable
fun glassOverlayWanted(): Boolean = app.plait.ui.theme.Appearance.glass && !LocalGlassEnv.current.material
