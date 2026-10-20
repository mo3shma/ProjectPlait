package app.plait.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The phone's built-in model (Gemini Nano through Android's AICore), reached with Google's ML Kit
 * Prompt API. It runs on the device: free, offline once downloaded, and nothing is sent anywhere.
 * Only some phones have it (recent Pixels, Galaxy S-series and a growing list); everywhere else
 * [state] says Unavailable and Plait uses its own rule-based reading instead.
 */
object OnDeviceAi {
    enum class State { Unavailable, Downloadable, Downloading, Ready }

    private val client: GenerativeModel? by lazy { runCatching { Generation.getClient() }.getOrNull() }

    suspend fun state(): State {
        val c = client ?: return State.Unavailable
        return runCatching {
            when (c.checkStatus()) {
                FeatureStatus.AVAILABLE -> State.Ready
                FeatureStatus.DOWNLOADABLE -> State.Downloadable
                FeatureStatus.DOWNLOADING -> State.Downloading
                else -> State.Unavailable
            }
        }.getOrDefault(State.Unavailable)
    }

    fun download(): Flow<DownloadStatus> = client?.let { runCatching { it.download() }.getOrNull() } ?: emptyFlow()

    /** Null when the model is missing, busy, or refused the request. */
    suspend fun generate(prompt: String): String? {
        val c = client ?: return null
        return runCatching { c.generateContent(prompt).candidates.firstOrNull()?.text?.trim() }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
