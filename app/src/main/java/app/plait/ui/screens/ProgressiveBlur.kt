package app.plait.ui.screens

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity

/** Height of the blurred band at the bottom of a clipped card, and its strongest blur and tint. */
internal const val BlurBandDp = 88f
internal const val BlurMaxRadiusDp = 14f
internal const val BlurTintMax = 0.55f

/** AGSL needs API 33. On 31-32 a blur cannot be progressive, so those and older phones get the tint scrim only. */
internal fun usesShader(sdkInt: Int): Boolean = sdkInt >= 33

internal fun blurTint(dark: Boolean, material: Boolean, container: Color): Color =
    if (material) container else if (dark) Color(0xFF0E1316) else Color.White

internal fun bandStartOf(height: Float, bandPx: Float): Float = (height - bandPx).coerceAtLeast(0f)

private fun ramp(y: Float, height: Float, bandStart: Float): Float {
    if (height <= bandStart) return 0f
    val t = ((y - bandStart) / (height - bandStart)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

internal fun blurRadiusAt(y: Float, height: Float, bandStart: Float, maxRadius: Float): Float = ramp(y, height, bandStart) * maxRadius

internal fun tintAlphaAt(y: Float, height: Float, bandStart: Float, tintMax: Float = BlurTintMax): Float =
    ramp(y, height, bandStart) * tintMax.coerceAtMost(BlurTintMax)

// Golden-angle spiral, 16 taps; blur radius and tint share one smoothstep ramp. Content is premultiplied, so the tint
// is added as premultiplied colour over the dimmed blur.
internal const val BLUR_AGSL = """
uniform shader content;
uniform float2 size;
uniform float bandStart;
uniform float maxRadius;
uniform half4 tint;
uniform float tintMax;

half4 main(float2 p) {
    float t = smoothstep(bandStart, size.y, p.y);
    float r = t * maxRadius;
    half4 sum = half4(0.0);
    for (int i = 0; i < 16; i++) {
        float fi = float(i) + 0.5;
        float a = fi * 2.39996323;
        float d = sqrt(fi / 16.0) * r;
        sum += content.eval(p + d * float2(cos(a), sin(a)));
    }
    sum /= 16.0;
    half k = half(t * tintMax);
    return sum * (1.0 - k) + half4(tint.rgb * k, k);
}
"""

@RequiresApi(33)
private class BlurShader {
    val shader = RuntimeShader(BLUR_AGSL)
    fun effect(w: Float, h: Float, bandStart: Float, maxRadius: Float, tint: Color) = run {
        shader.setFloatUniform("size", w, h)
        shader.setFloatUniform("bandStart", bandStart)
        shader.setFloatUniform("maxRadius", maxRadius)
        shader.setFloatUniform("tint", tint.red, tint.green, tint.blue, 1f)
        shader.setFloatUniform("tintMax", BlurTintMax)
        RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

/**
 * Content sinks into frosted glass over its last [BlurBandDp]: progressively blurrier and tinted toward the bottom
 * edge. Off, it adds nothing. Draws only; touches pass through.
 */
@Composable
internal fun Modifier.progressiveBottomBlur(enabled: Boolean, tint: Color): Modifier {
    if (!enabled) return this
    val density = LocalDensity.current.density
    val band = BlurBandDp * density
    if (usesShader(Build.VERSION.SDK_INT)) {
        val fx = remember { if (Build.VERSION.SDK_INT >= 33) BlurShader() else null }
        return graphicsLayer {
            if (Build.VERSION.SDK_INT >= 33 && fx != null) {
                renderEffect = fx.effect(size.width, size.height, bandStartOf(size.height, band), BlurMaxRadiusDp * density, tint)
            }
        }
    }
    return drawWithContent {
        drawContent()
        val top = bandStartOf(size.height, band)
        drawRect(
            Brush.verticalGradient(listOf(tint.copy(alpha = 0f), tint.copy(alpha = BlurTintMax)), startY = top, endY = size.height),
            topLeft = Offset(0f, top), size = Size(size.width, size.height - top),
        )
    }
}
