package app.plait.ai

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which AI the user wants for writing and for speech. Kept on the phone. */
object AiPrefs {
    enum class TextChoice(val label: String) {
        Auto("Automatic"), Nano("Gemini Nano"), Local("Downloaded model"), Rules("Plait's own rules only"),
    }

    var textChoice by mutableStateOf(TextChoice.Auto); private set
    /** Which downloaded text model to use when [textChoice] is Local. */
    var textModelId by mutableStateOf<String?>(null); private set
    /** Which downloaded speech model transcribes voice notes afterwards, or null for the built-in live one only. */
    var speechModelId by mutableStateOf<String?>(null); private set
    /** "auto" lets Whisper work out the language, otherwise a code like "en" or "ar". */
    var speechLang by mutableStateOf("auto"); private set
    private var loaded = false

    val speechLanguages = listOf(
        "auto" to "Detect automatically", "en" to "English", "ar" to "Arabic", "fr" to "French", "es" to "Spanish",
        "de" to "German", "it" to "Italian", "pt" to "Portuguese", "nl" to "Dutch", "tr" to "Turkish", "ru" to "Russian",
        "fa" to "Persian", "ur" to "Urdu", "hi" to "Hindi", "id" to "Indonesian", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
    )

    /** Two languages spoken in one note: each sentence is written in its own language. The first is the main one. */
    val mixedLanguages = listOf(
        "ar+en" to "Arabic + English", "en+ar" to "English + Arabic", "fr+en" to "French + English", "fr+ar" to "French + Arabic",
        "es+en" to "Spanish + English", "de+en" to "German + English", "tr+en" to "Turkish + English", "hi+en" to "Hindi + English",
        "ur+en" to "Urdu + English",
    )

    fun languageName(code: String): String =
        (speechLanguages + mixedLanguages).firstOrNull { it.first == code }?.second?.let { if (code == "auto") "Any language" else it } ?: code

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        textChoice = runCatching { TextChoice.valueOf(p.getString("ai_text", "Auto").orEmpty()) }.getOrDefault(TextChoice.Auto)
        textModelId = p.getString("ai_text_model", null)
        speechModelId = p.getString("ai_speech_model", null)
        speechLang = p.getString("ai_speech_lang", "auto").orEmpty().ifBlank { "auto" }
    }

    fun setTextChoice(context: Context, c: TextChoice, modelId: String? = textModelId) {
        textChoice = c; textModelId = modelId
        prefs(context).edit().putString("ai_text", c.name).also { e -> if (modelId != null) e.putString("ai_text_model", modelId) }.apply()
    }

    /** A model that has just been downloaded or found becomes the default for everything of its kind. */
    fun makeDefault(context: Context, spec: ModelSpec) {
        load(context)
        when (spec.kind) {
            ModelKind.Text -> setTextChoice(context, TextChoice.Local, spec.id)
            ModelKind.Speech -> setSpeechModel(context, spec.id)
            ModelKind.Search -> Unit // meaning search is used automatically whenever its model is installed
        }
    }

    fun setSpeechModel(context: Context, id: String?) {
        speechModelId = id
        prefs(context).edit().also { e -> if (id == null) e.remove("ai_speech_model") else e.putString("ai_speech_model", id) }.apply()
    }

    fun setSpeechLang(context: Context, code: String) {
        speechLang = code
        prefs(context).edit().putString("ai_speech_lang", code).apply()
    }

    private fun prefs(c: Context) = c.getSharedPreferences("plait", Context.MODE_PRIVATE)
}
