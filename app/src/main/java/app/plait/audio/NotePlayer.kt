package app.plait.audio

import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** Plays one saved note. Create with remember(file) and call [release] when leaving. */
class NotePlayer(private val file: File, val durationMs: Long) {
    private var player: MediaPlayer? = null

    var isPlaying by mutableStateOf(false); private set
    var positionMs by mutableLongStateOf(0L); private set

    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Playback speed; pitch stays natural. Applied while playing (setting it on a paused player can start it). */
    var speed by mutableFloatStateOf(1f); private set

    fun cycleSpeed(): Float {
        speed = nextSpeed(speed)
        applySpeed()
        return speed
    }

    fun restoreSpeed(s: Float) { speed = if (s in SPEEDS) s else 1f }

    private fun applySpeed() {
        val p = player ?: return
        if (isPlaying) runCatching { p.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f) }
    }

    /** Jumps by [deltaMs] (negative = back), clamped to the note. */
    fun skip(deltaMs: Long) {
        sync(force = true)
        seekTo(seekTarget(positionMs, deltaMs, durationMs))
    }

    fun toggle() {
        if (isPlaying) pause() else play()
    }

    companion object {
        val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)
        const val SKIP_MS = 10_000L

        /** The next speed in 1x, 1.25x, 1.5x, 2x, wrapping; anything unknown goes to 1x. */
        fun nextSpeed(current: Float): Float = SPEEDS.getOrNull(SPEEDS.indexOf(current) + 1) ?: SPEEDS.first()

        fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long = (positionMs + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(0L))

        fun label(speed: Float): String = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')) + "x"
    }

    private fun play() {
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    this@NotePlayer.isPlaying = false
                    this@NotePlayer.positionMs = 0
                    runCatching { seekTo(0) }
                }
            }
        }.getOrNull()?.also { player = it } ?: return
        p.start()
        isPlaying = true
        applySpeed()
    }

    private fun pause() {
        player?.pause()
        isPlaying = false
        sync()
    }

    fun seekTo(ms: Long) {
        val p = player ?: runCatching {
            MediaPlayer().apply { setDataSource(file.absolutePath); prepare() }
        }.getOrNull()?.also { player = it } ?: return
        p.seekTo(ms.coerceIn(0, durationMs).toInt())
        positionMs = ms.coerceIn(0, durationMs)
    }

    /** Pulls the playback position into Compose state; called on a timer while playing. */
    fun sync(force: Boolean = false) {
        player?.let { if (isPlaying || force) positionMs = it.currentPosition.toLong() }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        isPlaying = false
    }
}
