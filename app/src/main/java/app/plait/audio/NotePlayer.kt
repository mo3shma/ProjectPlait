package app.plait.audio

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
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

    fun toggle() {
        if (isPlaying) pause() else play()
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
    fun sync() {
        player?.let { if (isPlaying) positionMs = it.currentPosition.toLong() }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        isPlaying = false
    }
}
