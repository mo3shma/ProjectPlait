package app.plait.ai

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns text into a list of numbers so that texts with similar meaning sit close together. English only: text that is
 * mostly Arabic gets null, so search keeps to its word matching (which normalises Arabic) instead of comparing noise.
 */
object Embedder {
    const val MODEL_ID = "search-use"
    private var embedder: TextEmbedder? = null

    fun available(context: Context) = ModelCenter.isInstalled(context, MODEL_ID)

    /** True when the English-only model can say something useful about [text]. */
    fun understands(text: String): Boolean = ArabicText.share(text) < 0.5

    suspend fun embed(context: Context, text: String): FloatArray? = withContext(Dispatchers.Default) {
        if (!understands(text)) return@withContext null
        runCatching {
            val e = embedder ?: TextEmbedder.createFromOptions(
                context.applicationContext,
                TextEmbedder.TextEmbedderOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(ModelCenter.file(context, MODEL_ID, "embedder.tflite").path).build())
                    .build(),
            ).also { embedder = it }
            e.embed(text.take(600)).embeddingResult().embeddings().first().floatEmbedding()
        }.getOrNull()
    }

    fun release() { runCatching { embedder?.close() }; embedder = null }
}
