package app.plait.ui

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import app.plait.ui.glass.LocalGlassEnv

/**
 * One place that decides how springy the app feels. Liquid Glass is lively, Material 3 Expressive is bouncier, and with
 * "Reduce animations" on nothing overshoots.
 */
object Motion {
    /** Damping ratio for position and size changes. */
    @Composable fun damping(): Float {
        val env = LocalGlassEnv.current
        return if (env.reduceMotion) 1f else 0.8f
    }

    @Composable fun stiffness(): Float = if (LocalGlassEnv.current.reduceMotion) 900f else 380f

    @Composable fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = damping(), stiffness = stiffness())

    /** Milliseconds for fades; shorter when animations are reduced. */
    @Composable fun fadeMs(): Int = if (LocalGlassEnv.current.reduceMotion) 90 else 220
}
