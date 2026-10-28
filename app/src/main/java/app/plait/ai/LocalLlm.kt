package app.plait.ai

import android.content.Context
import app.plait.R
import app.plait.i18n.Txt
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
 * Runs a downloaded model on the phone, with Google's MediaPipe LLM runtime (.task models) or LiteRT-LM (.litertlm
 * models, 64-bit phones). The model is loaded when first needed and let go after two idle minutes, because it holds
 * as much memory as the file is big.
 */
object LocalLlm {
    private val lock = Mutex()
    private var llm: LlmInference? = null
    private var lm: com.google.ai.edge.litertlm.Engine? = null
    private var loadedId: String? = null
    private var idle: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Wraps a plain prompt in the chat format the model was trained on. */
    fun chat(spec: ModelSpec, prompt: String, system: String? = null): String = when {
        spec.id.startsWith("text-qwen") ->
            "<|im_start|>system\n${system ?: Txt.s(R.string.ai_llm_default_system)}<|im_end|>\n" +
                "<|im_start|>user\n$prompt<|im_end|>\n<|im_start|>assistant\n"
        else -> if (system != null) "$system\n\n$prompt" else prompt
    }

    /** Null if the model cannot be loaded or produced nothing. */
    suspend fun generate(context: Context, spec: ModelSpec, prompt: String, system: String? = null, temperature: Float? = null): String? = lock.withLock {
        withContext(Dispatchers.Default) {
            runCatching {
                idle?.cancel()
                if (spec.runtime == Runtime.LiteRtLm) return@runCatching liteRt(context.applicationContext, spec, prompt, system, temperature)
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

    /**
     * LiteRT-LM: the rules go in the system instruction, the same careful sampling as MediaPipe when a temperature is
     * given, and the model's "thinking" is off so the answer is just the answer.
     */
    private fun liteRt(context: Context, spec: ModelSpec, prompt: String, system: String?, temperature: Float?): String? {
        val engine = if (loadedId == spec.id && lm != null) lm!! else {
            release()
            com.google.ai.edge.litertlm.Engine(
                com.google.ai.edge.litertlm.EngineConfig(
                    modelPath = ModelCenter.file(context, spec.id, spec.files.first().name).path,
                    backend = com.google.ai.edge.litertlm.Backend.CPU(),
                    cacheDir = context.cacheDir.path,
                ),
            ).also { it.initialize(); lm = it; loadedId = spec.id }
        }
        val sampler = if (temperature != null) com.google.ai.edge.litertlm.SamplerConfig(20, 0.9, temperature.toDouble(), 7)
            else com.google.ai.edge.litertlm.SamplerConfig(40, 0.95, 0.7, 0)
        val config = com.google.ai.edge.litertlm.ConversationConfig(
            systemInstruction = com.google.ai.edge.litertlm.Contents.of(system ?: Txt.s(R.string.ai_llm_default_system)),
            samplerConfig = sampler,
            thinkingConfig = com.google.ai.edge.litertlm.ThinkingConfig(false),
        )
        return engine.createConversation(config).use { conv ->
            val reply = conv.sendMessage(prompt)
            stripThinking(reply.contents.contents.filterIsInstance<com.google.ai.edge.litertlm.Content.Text>().joinToString("") { it.text })
        }.trim().takeIf { it.isNotBlank() }
    }

    /** Some models still write their reasoning between <think> tags; only the answer after it is kept. */
    internal fun stripThinking(text: String): String =
        text.replace(Regex("(?s)<think>.*?</think>"), "").substringAfter("</think>").trim()

    private fun load(context: Context, spec: ModelSpec): LlmInference {
        if (loadedId == spec.id) llm?.let { return it }
        release()
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
        runCatching { lm?.close() }
        llm = null
        lm = null
        loadedId = null
    }
}
