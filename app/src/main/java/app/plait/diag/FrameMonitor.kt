package app.plait.diag

import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Measures how smoothly the screen is drawing, on the phone, for the "Show frame stats" switch in the hub. A frame slower
 * than 16.7 ms is a missed frame at 60 Hz. Counts are over the last two seconds.
 */
object FrameMonitor : Choreographer.FrameCallback {
    var enabled by mutableStateOf(false); private set
    var summary by mutableStateOf("")
    private var last = 0L
    private val window = ArrayDeque<Pair<Long, Long>>() // (time, frame length ns)

    fun turn(on: Boolean) {
        if (on == enabled) return
        enabled = on
        last = 0; window.clear()
        if (on) Choreographer.getInstance().postFrameCallback(this) else Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (last != 0L) {
            window.addLast(frameTimeNanos to (frameTimeNanos - last))
            while (window.isNotEmpty() && frameTimeNanos - window.first().first > 2_000_000_000L) window.removeFirst()
            if (window.size > 10) {
                val avg = window.sumOf { it.second } / window.size / 1_000_000.0
                val slow = window.count { it.second > 20_000_000L }
                val worst = window.maxOf { it.second } / 1_000_000
                summary = "%.1f ms avg · %d%% slow · worst %d ms".format(avg, slow * 100 / window.size, worst)
            }
        }
        last = frameTimeNanos
        if (enabled) Choreographer.getInstance().postFrameCallback(this)
    }
}
