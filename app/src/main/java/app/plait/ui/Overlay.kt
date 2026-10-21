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

val LocalOverlay = staticCompositionLocalOf<OverlayState?> { null }

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
            val scrim by animateFloatAsState(if (shown) 0.5f else 0f, tween(200), label = "dialog-scrim")
            BackHandler { dismiss() }
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))
                    .clickable(interactionSource = null, indication = null) { dismiss() }
                    .systemBarsPadding().imePadding(),
                contentAlignment = Alignment.Center,
            ) {
                // Swallows taps on the panel itself so they do not reach the scrim.
                Box(
                    Modifier.padding(horizontal = if (full) 0.dp else 24.dp, vertical = 16.dp).widthIn(max = if (full) 2000.dp else 560.dp)
                        .clickable(interactionSource = null, indication = null) {},
                ) { body() }
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
