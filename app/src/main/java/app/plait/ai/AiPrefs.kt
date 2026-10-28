package app.plait.ai

import android.content.Context
import androidx.annotation.StringRes
import app.plait.R
import app.plait.i18n.Txt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A fresh install uses Plait's own rules; a saved choice (including the old "Auto") is kept. */
internal fun textChoiceOf(saved: String?): AiPrefs.TextChoice =
    runCatching { AiPrefs.TextChoice.valueOf(saved.orEmpty()) }.getOrDefault(AiPrefs.TextChoice.Rules)

/** Which AI the user wants for writing and for speech. Kept on the phone. */
object AiPrefs {
    enum class TextChoice(@StringRes private val res: Int) {
        Auto(R.string.common_automatic), Nano(R.string.ai_engine_nano), Local(R.string.ai_choice_local), Rules(R.string.ai_choice_rules);

        val label: String get() = Txt.s(res)
    }

    var textChoice by mutableStateOf(TextChoice.Rules); private set
    /** Which downloaded text model to use when [textChoice] is Local. */
    var textModelId by mutableStateOf<String?>(null); private set
    /** Which downloaded speech model transcribes voice notes afterwards, or null for none (a recording is then saved as audio only, unless a Whisper model is installed). */
    var speechModelId by mutableStateOf<String?>(null); private set
    /** "auto" lets Whisper work out the language, otherwise a code like "en" or "ar". */
    var speechLang by mutableStateOf("auto"); private set
    private var loaded = false

    private val names = mapOf(
        "en" to R.string.ai_lang_en, "ar" to R.string.ai_lang_ar, "fr" to R.string.ai_lang_fr, "es" to R.string.ai_lang_es,
        "de" to R.string.ai_lang_de, "it" to R.string.ai_lang_it, "pt" to R.string.ai_lang_pt, "nl" to R.string.ai_lang_nl,
        "tr" to R.string.ai_lang_tr, "ru" to R.string.ai_lang_ru, "fa" to R.string.ai_lang_fa, "ur" to R.string.ai_lang_ur,
        "hi" to R.string.ai_lang_hi, "id" to R.string.ai_lang_id, "zh" to R.string.ai_lang_zh, "ja" to R.string.ai_lang_ja,
        "ko" to R.string.ai_lang_ko,
    )

    /** Code to name, in the app's language (read it each time: the language can change). */
    val speechLanguages: List<Pair<String, String>>
        get() = listOf("auto" to Txt.s(R.string.ai_lang_auto)) + names.map { (code, res) -> code to Txt.s(res) }

    /** Two languages spoken in one note: each sentence is written in its own language. The first is the main one. */
    val mixedLanguages: List<Pair<String, String>>
        get() = listOf("ar+en", "en+ar", "fr+en", "fr+ar", "es+en", "de+en", "tr+en", "hi+en", "ur+en").map { code ->
            code to Txt.s(R.string.ai_lang_pair, Txt.s(names.getValue(code.substringBefore('+'))), Txt.s(names.getValue(code.substringAfter('+'))))
        }

    fun languageName(code: String): String =
        (speechLanguages + mixedLanguages).firstOrNull { it.first == code }?.second?.let { if (code == "auto") Txt.s(R.string.ai_lang_any) else it } ?: code

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        textChoice = textChoiceOf(p.getString("ai_text", null))
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
