package app.plait.ui.screens

import android.app.ActivityManager
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.SwapHoriz
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.plait.ai.AiPrefs
import app.plait.ai.ModelCatalog
import app.plait.ai.ModelCenter
import app.plait.ai.ModelKind
import app.plait.ai.ModelSpec
import app.plait.ai.ModelState
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.fauxGlass
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait

/** Turbo on phones with 8 GB (reported as about 7.4), Small from about 6 GB, Base below that. */
internal fun speechPickFor(totalMem: Long): String = when {
    totalMem >= TURBO_MEM -> "speech-whisper-turbo"
    totalMem >= 5_500_000_000L -> "speech-whisper-small"
    else -> "speech-whisper-base"
}

internal const val TURBO_MEM = 7_000_000_000L

internal fun totalMemory(context: Context): Long =
    ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }.totalMem

internal fun recommendedSpeech(context: Context): ModelSpec = ModelCatalog.byId(speechPickFor(totalMemory(context)))!!

/**
 * Shown on a voice note that has no transcript: what is missing, which language the user speaks, and one tap to get
 * Whisper, which then transcribes this note and the others on the phone.
 */
@Composable
internal fun SpeechUpgradeCard() {
    val context = LocalContext.current
    AiPrefs.load(context)
    val anyInstalled = ModelCatalog.speechModels.any { ModelCenter.state(it.id) is ModelState.Installed || ModelCenter.isInstalled(context, it.id) }
    var hidden by remember { mutableStateOf(false) }
    if (anyInstalled || hidden) return
    val spec = remember { recommendedSpeech(context) }
    val state = ModelCenter.state(spec.id)
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp).fauxGlass(RoundedCornerShape(22.dp), plait.accent.copy(alpha = 0.16f)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("This note has no transcript yet", style = PlaitType.bodyStrong, color = plait.text)
        Text(
            "Nothing has written this recording down yet. Get ${spec.title}: it runs on your phone, understands accents and Arabic, " +
                "and writes the transcript of this note and your others, then the summary and tasks follow.",
            style = PlaitType.caption, color = plait.muted,
        )
        Text("I mostly speak", style = PlaitType.bodyStrong, color = plait.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en" to "English", "ar" to "Arabic", "ar+en" to "Both, mixed").forEach { (code, label) ->
                PlaitButton(label, { AiPrefs.setSpeechLang(context, code) }, style = if (AiPrefs.speechLang == code) ButtonStyle.Primary else ButtonStyle.Secondary, size = ButtonSize.Small)
            }
        }
        when (state) {
            is ModelState.Downloading -> Text(
                "Downloading ${spec.title}… ${(state.done * 100 / state.total.coerceAtLeast(1))}%. It carries on if you leave Plait.",
                style = PlaitType.caption, color = plait.accent,
            )
            else -> {
                if (state is ModelState.Failed) Text(state.reason, style = PlaitType.caption, color = plait.signal)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaitButton("Get ${spec.title} · ${spec.sizeLabel}", { ModelCenter.download(context, spec) }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                    PlaitButton("Not now", { hidden = true }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                }
            }
        }
    }
}

/**
 * The language voice notes are transcribed in: a pill that shows the current one and opens the list. Telling Whisper
 * the language makes it far more accurate than letting it guess, especially with an accent.
 */
@Composable
internal fun LanguagePicker(modifier: Modifier = Modifier, label: String? = null, onPicked: (String) -> Unit = {}) {
    val context = LocalContext.current
    AiPrefs.load(context)
    var open by remember { mutableStateOf(false) }
    PlaitButton(
        label ?: "Language: ${AiPrefs.languageName(AiPrefs.speechLang)}", { open = true }, modifier,
        style = ButtonStyle.Secondary, size = ButtonSize.Small, icon = androidx.compose.material.icons.Icons.Filled.Translate,
    )
    if (open) ActionsDialog(
        "I'm speaking",
        // One language, "any", then two languages mixed in one note.
        (AiPrefs.speechLanguages.take(3) + AiPrefs.mixedLanguages.take(2) + AiPrefs.speechLanguages.drop(3) + AiPrefs.mixedLanguages.drop(2)).map { (code, name) ->
            val label = if ('+' in code) "$name (mixed)" else name
            NoteAction(
                if (code == AiPrefs.speechLang) "$label  ✓" else label,
                when { code == "auto" -> androidx.compose.material.icons.Icons.Filled.AutoAwesome; '+' in code -> androidx.compose.material.icons.Icons.Filled.SwapHoriz; else -> androidx.compose.material.icons.Icons.Filled.Translate },
            ) { AiPrefs.setSpeechLang(context, code); onPicked(code) }
        },
        onDismiss = { open = false },
    )
}
