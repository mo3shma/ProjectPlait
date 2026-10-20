package app.plait.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs a downloaded model on the phone with Google's MediaPipe LLM runtime. The model is loaded when first needed
 * and let go after two idle minutes, because it holds as much memory as the file is big.
 */
object LocalLlm {
    private val lock = Mutex()
    private var llm: LlmInference? = null
    private var loadedId: String? = null
    private var idle: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Wraps a plain prompt in the chat format the model was trained on. */
    fun chat(spec: ModelSpec, prompt: String, system: String? = null): String = when {
        spec.id.startsWith("text-qwen") ->
            "<|im_start|>system\n${system ?: "You are a careful assistant. Answer only from the text you are given."}<|im_end|>\n" +
                "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        else -> if (system != null) "$system\n\n$prompt" else prompt
    }

    /** Null if the model cannot be loaded or produced nothing. */
    suspend fun generate(context: Context, spec: ModelSpec, prompt: String, system: String? = null, temperature: Float? = null): String? = lock.withLock {
        withContext(Dispatchers.Default) {
            runCatching {
                idle?.cancel()
                val engine = load(context.applicationContext, spec)
                val text = chat(spec, prompt, system)
                val out = if (temperature == null) engine.generateResponse(text) else {
                    // A low temperature for tasks with rules: the model sticks to the likeliest words instead of inventing.
                    val opts = com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions.builder()
                        .setTemperature(temperature).setTopK(20).setTopP(0.9f).setRandomSeed(7).build()
                    com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.createFromOptions(engine, opts).use { s -> s.addQueryChunk(text); s.generateResponse() }
                }
                out.trim().takeIf { it.isNotBlank() }
            }.onFailure { release() }.getOrNull()
        }.also { scheduleRelease() }
    }

    private fun load(context: Context, spec: ModelSpec): LlmInference {
        if (loadedId == spec.id) llm?.let { return it }
        llm?.close()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(ModelCenter.file(context, spec.id, spec.files.first().name).path)
            .setMaxTokens(1280)
            .build()
        return LlmInference.createFromOptions(context, options).also { llm = it; loadedId = spec.id }
    }

    private fun scheduleRelease() {
        idle?.cancel()
        idle = scope.launch { delay(120_000); lock.withLock { release() } }
    }

    fun release() {
        runCatching { llm?.close() }
        llm = null
        loadedId = null
    }
}
