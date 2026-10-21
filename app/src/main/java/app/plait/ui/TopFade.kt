package app.plait.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import app.plait.ui.theme.plait

/**
 * A soft fade from the screen's ground colour under a floating back button, so scrolling cards never run
 * behind it. It sits below the button in the stacking order and does not take touches.
 */
@Composable
fun TopFade(modifier: Modifier = Modifier) {
    val ground = plait.ground
    val h = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 76.dp
    Box(modifier.fillMaxWidth().height(h).background(Brush.verticalGradient(0f to ground.copy(alpha = 0.9f), 0.6f to ground.copy(alpha = 0.8f), 1f to ground.copy(alpha = 0f))))
}
