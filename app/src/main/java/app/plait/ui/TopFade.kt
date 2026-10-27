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
 * The screen's ground colour behind a pinned top row (48dp controls at status bar + 12dp): near solid down to
 * status bar + 56dp, so scrolling text disappears under the row, then a 16dp fade that ends at + 72dp, above
 * where lists and expanded cards start. Put it inside the content layer that glass refracts, below the row in
 * the stacking order. Drawing only: no pointer input, so it never takes touches.
 */
@Composable
fun TopFade(modifier: Modifier = Modifier) {
    val ground = plait.ground
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val h = top + 72.dp
    val solid = (top + 56.dp) / h
    Box(modifier.fillMaxWidth().height(h).background(Brush.verticalGradient(0f to ground.copy(alpha = 0.96f), solid to ground.copy(alpha = 0.96f), 1f to ground.copy(alpha = 0f))))
}
