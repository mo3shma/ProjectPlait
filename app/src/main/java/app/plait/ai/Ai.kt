package app.plait.ai

import android.content.Context
import app.plait.R
import app.plait.i18n.Txt

/**
 * The one door to "the AI". It picks the engine the user chose (Gemini Nano, a downloaded model, or none), falls back
 * sensibly when that one is not available, and says which engine answered so the screen can be honest about it.
 */
object Ai {
    enum class Engine { Rules, Nano, Local }

    data class Status(val engine: Engine, val label: String, val note: String? = null, val modelId: String? = null) {
        val ready: Boolean get() = engine != Engine.Rules
        /** Characters of source text a prompt may carry; small models have short memory. */
        val promptBudget: Int get() = if (engine == Engine.Local) 2200 else 3200
    }

    data class Result(val text: String, val label: String)

    private fun bestInstalledLocal(context: Context): ModelSpec? =
        ModelCatalog.textModels.filter { it.supported && ModelCenter.isInstalled(context, it.id) }.maxByOrNull { it.quality }

    suspend fun status(context: Context): Status {
        AiPrefs.load(context)
        val nano = OnDeviceAi.state() == OnDeviceAi.State.Ready
        val chosenLocal = ModelCatalog.byId(AiPrefs.textModelId)?.takeIf { ModelCenter.isInstalled(context, it.id) }
        val rules = Txt.s(R.string.ai_engine_rules)
        val nanoName = Txt.s(R.string.ai_engine_nano)
        fun auto(note: String? = null): Status = when {
            nano -> Status(Engine.Nano, nanoName, note)
            bestInstalledLocal(context) != null -> bestInstalledLocal(context)!!.let { Status(Engine.Local, it.title, note, it.id) }
            else -> Status(Engine.Rules, rules, note)
        }
        return when (AiPrefs.textChoice) {
            AiPrefs.TextChoice.Rules -> Status(Engine.Rules, rules)
            AiPrefs.TextChoice.Nano -> if (nano) Status(Engine.Nano, nanoName) else auto(Txt.s(R.string.ai_engine_nano_not_ready))
            AiPrefs.TextChoice.Local -> chosenLocal?.let { Status(Engine.Local, it.title, null, it.id) } ?: auto(Txt.s(R.string.ai_engine_model_missing))
            AiPrefs.TextChoice.Auto -> auto()
        }
    }

    suspend fun ready(context: Context): Boolean = status(context).ready

    /** Automatic writing (day summary, prep, About) uses the smallest downloaded model when this is on. */
    fun lightAuto(context: Context): Boolean = context.getSharedPreferences("plait", Context.MODE_PRIVATE).getBoolean("ai_light_auto", true)
    fun setLightAuto(context: Context, on: Boolean) { context.getSharedPreferences("plait", Context.MODE_PRIVATE).edit().putBoolean("ai_light_auto", on).apply() }

    /** Runs [prompt] on the chosen engine. Null when there is no engine or it gave no answer. */
    suspend fun generate(context: Context, prompt: String, background: Boolean = false, system: String? = null, temperature: Float? = null): Result? {
        val s = status(context)
        if (background) kotlinx.coroutines.delay(1500) // let the screen settle before the model starts working
        // Writing nobody asked for runs on the smallest downloaded model, so a big one does not slow the phone down.
        val local = if (background && s.engine == Engine.Local && lightAuto(context)) {
            ModelCatalog.textModels.filter { it.supported && ModelCenter.isInstalled(context, it.id) }.minByOrNull { it.bytes } ?: ModelCatalog.byId(s.modelId)
        } else ModelCatalog.byId(s.modelId)
        val text = when (s.engine) {
            Engine.Rules -> null
            Engine.Nano -> OnDeviceAi.generate(if (system != null) "$system\n\n$prompt" else prompt)
            Engine.Local -> local?.let { LocalLlm.generate(context, it, prompt, system, temperature) }
        }
        return text?.let { Result(it, s.label) }
    }
}
