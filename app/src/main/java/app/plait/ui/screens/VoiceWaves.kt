package app.plait.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.dp
import app.plait.audio.RecordingController
import app.plait.ui.theme.LocalIsDark
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

private const val POINTS = 96
private const val IDLE_AMP = 0.06f
private const val LOUD_AMP = 0.95f
private const val TAU = (2 * PI).toFloat()
private val FREQ = floatArrayOf(1.2f, 1.8f, 2.4f, 3.1f)
private val ALPHA = floatArrayOf(0.95f, 0.65f, 0.45f, 0.3f)
private val SPEED = floatArrayOf(1.4f, -1.0f, 1.9f, -2.3f)
private val SCALE = floatArrayOf(1f, 0.8f, 0.6f, 0.45f)
private val BRAND = longArrayOf(0xFF7C8BFF, 0xFFFF8F7A, 0xFFFFD166, 0xFF6FD3C4)

/** RMS of the first [n] samples in dBFS, mapped -52..-10 dB onto 0..1. Silence (or no samples) is 0. */
internal fun loudnessOf(buf: ShortArray, n: Int): Float {
    val count = minOf(n, buf.size)
    if (count <= 0) return 0f
    var sum = 0.0
    for (i in 0 until count) { val v = buf[i].toDouble(); sum += v * v }
    val rms = sqrt(sum / count)
    if (rms <= 0.0) return 0f
    val db = 20.0 * log10(rms / 32768.0)
    return ((db + 52.0) / 42.0).toFloat().coerceIn(0f, 1f)
}

/** Exponential smoothing that rises fast and falls slowly; independent of the frame rate for a steady target. */
internal fun smoothAttackRelease(current: Float, target: Float, dt: Float, attack: Float = 0.05f, release: Float = 0.25f): Float {
    val tau = if (target > current) attack else release
    return current + (target - current) * (1f - exp(-dt / tau))
}

/** sin(pi*x)^2: 0 at both edges, 1 in the middle. */
internal fun envelope(x: Float): Float { val s = sin(PI.toFloat() * x); return s * s }

internal fun waveY(centreY: Float, amplitude: Float, x: Float, freq: Float, phase: Float): Float =
    centreY + amplitude * envelope(x) * sin(freq * x * TAU + phase)

private class WaveState { var amp = 0f; var last = 0L; val phase = FloatArray(4) }

/**
 * Four layered waves that follow the voice. A frame loop updates one tick that only the draw block reads,
 * so every frame redraws the canvas without recomposing anything.
 */
@Composable
internal fun VoiceWaves(controller: RecordingController, paused: Boolean, reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val dark = LocalIsDark.current
    val density = LocalDensity.current
    val tick = remember { mutableLongStateOf(0L) }
    val state = remember { WaveState() }
    val paths = remember { Array(4) { Path() } }
    val stroke = remember(density) { Stroke(3f * density.density, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val colors = remember(dark) {
        Array(4) { i ->
            val c = Color(BRAND[i])
            // The front wave is the strongest on a light background, so it gets the darker stroke there.
            if (!dark && i == 0) Color(c.red * 0.85f, c.green * 0.85f, c.blue * 0.85f) else c
        }
    }
    val pausedNow = rememberUpdatedState(paused)
    val calm = rememberUpdatedState(reduceMotion)

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                val dt = if (state.last == 0L) 0f else ((now - state.last) / 1e9f).coerceAtMost(0.1f)
                state.last = now
                val still = pausedNow.value
                var target = if (still) 0f else IDLE_AMP + (LOUD_AMP - IDLE_AMP) * controller.loudness
                if (calm.value) target = minOf(target, 0.5f)
                state.amp = smoothAttackRelease(state.amp, target, dt)
                if (!still) {
                    val speed = if (calm.value) 0.4f else 1f
                    for (k in 0 until 4) state.phase[k] = (state.phase[k] + SPEED[k] * speed * dt) % TAU
                }
                tick.longValue = now
            }
        }
    }

    Canvas(modifier.height(220.dp)) {
        tick.longValue // the only read: a new frame invalidates drawing, not composition
        val w = size.width
        val cy = size.height / 2f
        val maxAmp = 80.dp.toPx()
        for (k in 3 downTo 0) {
            val a = maxAmp * state.amp * SCALE[k]
            val p = paths[k]
            p.reset()
            for (i in 0 until POINTS) {
                val x = i / (POINTS - 1f)
                val y = waveY(cy, a, x, FREQ[k], state.phase[k])
                if (i == 0) p.moveTo(0f, y) else p.lineTo(x * w, y)
            }
            // Both ends sit on the centre line, so the implicit closing edge of a fill is the baseline.
            drawPath(p, colors[k], alpha = 0.08f, style = Fill)
            drawPath(p, colors[k], alpha = ALPHA[k], style = stroke)
        }
    }
}
