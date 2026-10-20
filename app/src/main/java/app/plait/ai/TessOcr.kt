package app.plait.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Reads Arabic (and English alongside it) with Tesseract, which ships with the app as two small language
 * files. ML Kit's reader has no Arabic, so this is the only route for Arabic pages. It is slower than ML Kit
 * (a few seconds) and works best on printed, well-lit text. Handwriting is poor.
 */
object TessOcr {
    class Result(val text: String, val confidence: Int)

    private fun dataDir(context: Context): File {
        val root = File(context.filesDir, "tess")
        val tessdata = File(root, "tessdata").also { it.mkdirs() }
        for (name in listOf("ara.traineddata", "eng.traineddata")) {
            val target = File(tessdata, name)
            if (!target.exists() || target.length() == 0L) {
                context.assets.open("tessdata/$name").use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
        return root
    }

    fun read(context: Context, image: File): Result? = runCatching {
        val bitmap = BitmapFactory.decodeFile(image.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: return null
        val api = TessBaseAPI()
        try {
            if (!api.init(dataDir(context).path, "ara+eng")) return null
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
            api.setImage(bitmap)
            val text = api.getUTF8Text().orEmpty()
            Result(text, api.meanConfidence())
        } finally {
            api.recycle()
            bitmap.recycle()
        }
    }.getOrNull()
}
