package app.plait.ai

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Turns text into a list of numbers so that texts with similar meaning sit close together. English only. */
object Embedder {
    const val MODEL_ID = "search-use"
    private var embedder: TextEmbedder? = null

    fun available(context: Context) = ModelCenter.isInstalled(context, MODEL_ID)

    suspend fun embed(context: Context, text: String): FloatArray? = withContext(Dispatchers.Default) {
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
