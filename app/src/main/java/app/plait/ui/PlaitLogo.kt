package app.plait.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.sin

/** The Plait mark: three strands plaited together on a round tile, the way calendars are woven into one. */
@Composable
fun PlaitLogo(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        drawCircle(Brush.linearGradient(listOf(Color(0xFF6C7CFF), Color(0xFFB07CFF)), Offset.Zero, Offset(w, w)))
        val colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFD98A), Color(0xFF9ED8FF))
        for (i in 0 until 3) {
            val p = Path()
            val x0 = w * 0.22f
            val x1 = w * 0.78f
            val steps = 40
            for (k in 0..steps) {
                val t = k / steps.toFloat()
                val x = x0 + (x1 - x0) * t
                val y = w * 0.5f + sin((t * 1.5f + i / 3f) * 2f * PI.toFloat()) * w * 0.17f
                if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            drawPath(p, colors[i], style = Stroke(width = w * 0.085f, cap = StrokeCap.Round))
        }
    }
}
