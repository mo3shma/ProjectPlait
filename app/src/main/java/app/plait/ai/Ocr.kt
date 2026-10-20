package app.plait.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.plait.data.memo.OcrChoice
import app.plait.data.memo.OcrLang
import app.plait.data.memo.OcrText
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Reads the text in a photo with ML Kit's on-device recogniser. The model ships inside the app, so it
 * works offline and the picture never leaves the phone. It reads printed text well and handwriting poorly,
 * and only Latin scripts.
 */
object Ocr {
    /** Copies the picture at [source] into [target], scaled so its long side is at most 2048 px. */
    suspend fun saveScaled(context: Context, source: Uri, target: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
            val bmp = resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@runCatching false
            val rotated = rotate(context, source, bmp)
            val scale = 2048f / maxOf(rotated.width, rotated.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true) else rotated
            target.parentFile?.mkdirs()
            target.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }.getOrDefault(false)
    }

    private fun rotate(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                when (androidx.exifinterface.media.ExifInterface(it).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (degrees == 0f) return bmp
        val m = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /**
     * The text in the picture, tidied, or null when every reader failed. An empty string means no text was found.
     * [lang] chooses the alphabet; Auto reads with both and keeps the better one.
     */
    suspend fun read(context: Context, file: File, lang: OcrLang = OcrLang.Auto): String? = withContext(Dispatchers.Default) {
        val latin = if (lang != OcrLang.Arabic) readLatin(context, file) else null
        val arabic = if (lang != OcrLang.Latin) TessOcr.read(context, file) else null
        OcrChoice.pick(lang, latin, arabic?.text?.let { OcrText.tidy(it) }, arabic?.confidence ?: 0)
    }

    private suspend fun readLatin(context: Context, file: File): String? = runCatching {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            suspendCancellableCoroutine<String?> { cont ->
                recogniser.process(image)
                    .addOnSuccessListener { cont.resume(OcrText.tidy(it.text)) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } finally {
            recogniser.close()
        }
    }.getOrNull()
}
