package app.plait.ui.screens

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
import app.plait.ai.ModelCatalog
import app.plait.ai.ModelCenter
import app.plait.ai.ModelSpec
import app.plait.ai.ModelState
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import app.plait.ui.BadgeKind
import app.plait.ui.GlassTone
import app.plait.ui.PlaitBadge
import app.plait.ui.toneSurface
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait

/**
 * Shows until the phone has a Whisper model that suits it and Qwen3 0.6B (or the user says "Not now"). On a phone with
 * less memory than Turbo likes, a safer Whisper counts in Turbo's place.
 */
internal fun betterVoiceVisible(dismissed: Boolean, speechInstalled: Boolean, qwenInstalled: Boolean): Boolean =
    !dismissed && !(speechInstalled && qwenInstalled)

internal const val QWEN = "text-qwen3-0.6b"
internal const val TURBO = "speech-whisper-turbo"
private const val DISMISSED = "better_voice_dismissed"

private fun prefs(c: Context) = c.getSharedPreferences("plait", Context.MODE_PRIVATE)

@Composable
internal fun BetterVoiceCard() {
    val context = LocalContext.current
    var dismissed by remember { mutableStateOf(prefs(context).getBoolean(DISMISSED, false)) }
    val qwen = remember { ModelCatalog.byId(QWEN)!! }
    val turbo = remember { ModelCatalog.byId(TURBO)!! }
    val lowMem = remember { totalMemory(context) < TURBO_MEM }
    val safer = remember { ModelCatalog.byId(speechPickFor(totalMemory(context)).takeIf { it != TURBO } ?: "speech-whisper-small")!! }
    fun installed(s: ModelSpec) = ModelCenter.state(s.id) is ModelState.Installed || ModelCenter.isInstalled(context, s.id)
    val speechOk = installed(turbo) || (lowMem && installed(safer))
    if (!betterVoiceVisible(dismissed, speechOk, installed(qwen))) return

    fun missing(vararg specs: ModelSpec) = specs.filter { !installed(it) }
    fun get(list: List<ModelSpec>) = list.forEach { ModelCenter.download(context, it) }
    fun label(list: List<ModelSpec>) = list.sumOf { it.bytes }.let { ModelCatalog.formatSize(it) }
    // A safer Whisper already installed on a small phone takes Turbo's place, so only Qwen is left to get.
    val main = missing(if (lowMem && installed(safer)) safer else turbo, qwen)
    val busy = listOf(turbo, safer, qwen).any { ModelCenter.state(it.id) is ModelState.Downloading }

    Column(
        Modifier.fillMaxWidth().toneSurface(RoundedCornerShape(24.dp), GlassTone.Mint, 1, strong = true).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Better results on voice notes", style = PlaitType.bodyStrong, color = plait.text)
        Text(
            "Whisper Turbo writes the transcript and Qwen3 0.6B writes the summary and finds the tasks, all on your phone.",
            style = PlaitType.caption, color = plait.muted,
        )
        listOf(turbo, qwen).forEach { m ->
            val st = ModelCenter.state(m.id)
            val note = when {
                installed(m) -> "Ready"
                st is ModelState.Downloading -> "Downloading… ${st.done * 100 / st.total.coerceAtLeast(1)}%"
                st is ModelState.Failed -> st.reason
                else -> m.sizeLabel
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(m.title, style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = plait.text)
                PlaitBadge(note, if (st is ModelState.Failed) BadgeKind.Warning else if (installed(m)) BadgeKind.Included else BadgeKind.Size)
            }
        }
        if (lowMem) Text("This phone has less memory than Turbo likes; ${safer.title} is a safer choice.", style = PlaitType.caption, color = plait.muted)
        if (busy) {
            Text("It carries on if you leave Plait.", style = PlaitType.caption, color = plait.muted)
        } else FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaitButton(
                (if (main.size > 1) "Get both" else "Get ${main.first().title}") + " · ${label(main)}",
                { get(main) }, style = ButtonStyle.Primary, size = ButtonSize.Small,
            )
            if (lowMem && !installed(safer) && !installed(turbo)) {
                val alt = missing(safer, qwen)
                PlaitButton("Get ${safer.title} + ${qwen.title} · ${label(alt)}", { get(alt) }, style = ButtonStyle.Tinted, tone = GlassTone.Mint, size = ButtonSize.Small)
            }
            PlaitButton("Not now", { dismissed = true; prefs(context).edit().putBoolean(DISMISSED, true).apply() }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
        }
    }
}
