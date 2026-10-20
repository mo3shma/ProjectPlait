package app.plait.ui.screens

import android.app.ActivityManager
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

/** Whisper Small on phones with the memory for it, Base on smaller ones. */
internal fun recommendedSpeech(context: Context): ModelSpec {
    val info = ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
    val id = if (info.totalMem >= 5_500_000_000L) "speech-whisper-small" else "speech-whisper-base"
    return ModelCatalog.byId(id)!!
}

/**
 * Shown on a voice note while only the rough live transcript exists: what is wrong, which language the user speaks,
 * and one tap to get Whisper, which then rewrites this note and the others on the phone.
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
        Text("This transcript is rough", style = PlaitType.bodyStrong, color = plait.text)
        Text(
            "The built-in recogniser is small and English only, so it mishears accents. ${spec.title} understands accents and Arabic, " +
                "runs on your phone, and rewrites this note and your others.",
            style = PlaitType.caption, color = plait.muted,
        )
        Text("I mostly speak", style = PlaitType.monoSmall, color = plait.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("en" to "English", "ar" to "Arabic", "auto" to "Both").forEach { (code, label) ->
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
