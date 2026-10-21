package app.plait.ui.screens

import app.plait.ui.plaitCard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ai.Ai
import app.plait.ai.AiPrefs
import app.plait.ai.ModelCatalog
import app.plait.ai.ModelCenter
import app.plait.ai.ModelSpec
import app.plait.ai.ModelState
import app.plait.ai.OnDeviceAi
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.BadgeKind
import app.plait.ui.PlaitBadge
import app.plait.ui.ToneStyles
import app.plait.ui.toneGlass
import app.plait.ui.toneSurface
import app.plait.ui.pressable
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import com.google.mlkit.genai.common.DownloadStatus
import kotlinx.coroutines.launch

/**
 * The AI part of the hub: choose what writes summaries (Gemini Nano, a downloaded model, or Plait's own rules) and
 * what transcribes voice notes, with the size of every download shown up front. Nothing downloads until you tap it.
 */
fun LazyListScope.aiSection() {
    item { BetterVoiceCard() }
    item {
        val context = LocalContext.current
        remember { AiPrefs.load(context); ModelCenter.refresh(context) }
        FoldNote("Pick what writes your summaries and what transcribes your voice. It all runs on the phone and nothing is uploaded. Downloads happen once, only when you tap them.",
        )
    }
    item { DefaultsCard() }
    item { ExistingModels() }
    item { TextAiCards() }
    item { SpeechCards() }
    item { SearchCard() }
    item { StorageNote() }
}

// ---------------------------------------------------------------------------------------------

/** What is used for what, in one place. Choosing a model below makes it the default for all of these. */
@Composable
private fun DefaultsCard() {
    val context = LocalContext.current
    var status by remember { mutableStateOf<Ai.Status?>(null) }
    LaunchedEffect(AiPrefs.textChoice, AiPrefs.textModelId, ModelCenter.states.toMap()) { status = Ai.status(context) }
    val speech = ModelCatalog.byId(AiPrefs.speechModelId)?.takeIf { ModelCenter.state(it.id) is ModelState.Installed }
    val search = ModelCatalog.searchModels.firstOrNull { ModelCenter.state(it.id) is ModelState.Installed }
    val look = LocalHubLook.current
    Column(Modifier.fillMaxWidth().toneSurface(RoundedCornerShape(24.dp), look.tone, look.rotation, inner = true, strong = true).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Your defaults", style = PlaitType.bodyStrong.copy(fontSize = 17.sp), color = plait.text)
        DefaultRow("Writing", (status?.label ?: "…") + (status?.note?.let { " · $it" } ?: ""), "Summaries, prep, follow-ups, answers, the morning brief")
        DefaultRow("Voice notes", speech?.title ?: "None yet", "Transcripts of everything you record")
        DefaultRow("Search", if (search != null) "Words and meaning" else "Words only", "Finding notes, emails and meetings")
        Text("Pick a model below, or download one, and it becomes the default for every one of these. Change it any time.", style = PlaitType.caption, color = plait.muted)
    }
}

@Composable
private fun DefaultRow(what: String, engine: String, covers: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(what, style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = plait.muted, modifier = Modifier.padding(top = 1.dp))
            Text(engine, style = PlaitType.bodyStrong.copy(fontWeight = FontWeight.Bold), color = plait.text, modifier = Modifier.weight(1f))
        }
        Text(covers, style = PlaitType.caption, color = plait.muted)
    }
}

/**
 * Finds models the phone already has, so they are not downloaded again: it checks Plait's own folders on opening, and
 * you can point it at a folder or files you downloaded earlier (for example from Hugging Face), or copy files over USB into
 * the drop folder shown here.
 */
@Composable
private fun ExistingModels() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val drop = remember { app.plait.ai.ModelFinder.dropFolder(context)?.path }
    fun show(r: app.plait.ai.ModelFinder.Report, where: String) {
        note = when {
            r.copied.isNotEmpty() -> "Found ${r.copied.size} file${if (r.copied.size == 1) "" else "s"} $where and added ${r.copied.size.let { if (it == 1) "it" else "them" }} without downloading: " +
                r.copied.joinToString("; ") + if (r.completed.isNotEmpty()) ". Ready: " + r.completed.joinToString { it.title } + "." else "."
            r.partial.isNotEmpty() -> "Nothing new $where. Already complete on this phone: ${r.partial.joinToString()}."
            else -> "No model files $where. The file names and sizes must match the downloads exactly."
        }
    }
    val folder = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { busy = true; val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.ai.ModelFinder.scanTree(context, uri) }; show(r, "in that folder"); busy = false }
    }
    val files = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { busy = true; val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.ai.ModelFinder.scanFiles(context, uris) }; show(r, "among those files"); busy = false }
    }
    // On opening, quietly count anything that is already in Plait's folders.
    LaunchedEffect(Unit) {
        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.ai.ModelFinder.scanThisPhone(context) }
        if (r.copied.isNotEmpty()) show(r, "on this phone")
    }
    val look = LocalHubLook.current
    Column(Modifier.fillMaxWidth().toneSurface(RoundedCornerShape(24.dp), look.tone, look.rotation, inner = true).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Already have a model?", style = PlaitType.bodyStrong.copy(fontSize = 17.sp), color = plait.text)
        Text(
            "Plait never downloads a model it can already find. Point it at files you downloaded earlier and it will copy them in instead of fetching them again.",
            style = PlaitType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = plait.text.copy(alpha = 0.85f),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HubBtn(if (busy) "Looking…" else "Choose a folder", { folder.launch(null) }, size = ButtonSize.Small, enabled = !busy)
            HubBtn("Choose files", { files.launch(arrayOf("*/*")) }, size = ButtonSize.Small, enabled = !busy)
            HubBtn("Check this phone", { scope.launch { busy = true; val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.ai.ModelFinder.scanThisPhone(context) }; show(r, "on this phone"); busy = false } }, style = ButtonStyle.Quiet, size = ButtonSize.Small, enabled = !busy)
        }
        if (drop != null) Text("Or copy the files over USB into: $drop", style = PlaitType.caption, color = plait.muted)
        note?.let { Text(it, style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = plait.text) }
    }
}

@Composable
private fun TextAiCards() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Ai.Status?>(null) }
    var nano by remember { mutableStateOf<OnDeviceAi.State?>(null) }
    var nanoNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(AiPrefs.textChoice, AiPrefs.textModelId, ModelCenter.states.toMap()) {
        nano = OnDeviceAi.state(); status = Ai.status(context)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Summaries and answers", style = PlaitType.bodyStrong, color = plait.text)
        status?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaitBadge("Using now", BadgeKind.Included)
                Text(it.label + (it.note?.let { n -> " · $n" } ?: ""), style = PlaitType.caption, color = plait.muted, modifier = Modifier.weight(1f))
            }
        }

        OptionCard(
            selected = AiPrefs.textChoice == AiPrefs.TextChoice.Auto, title = "Automatic",
            body = "Gemini Nano when the phone has it, then a downloaded model, then Plait's own rules.",
            onSelect = { AiPrefs.setTextChoice(context, AiPrefs.TextChoice.Auto) },
        )
        OptionCard(
            selected = AiPrefs.textChoice == AiPrefs.TextChoice.Nano, title = "Gemini Nano", badges = listOf(BadgeSpec("Built into Android", BadgeKind.Info)),
            body = "Google's model that ships with some recent phones. Free, private, and no download inside Plait.",
            meta = "Size is set by Android and isn't reported to apps.",
            onSelect = { AiPrefs.setTextChoice(context, AiPrefs.TextChoice.Nano) },
        ) {
            when (nano) {
                OnDeviceAi.State.Ready -> StatusLine("Ready on this phone")
                OnDeviceAi.State.Downloadable, OnDeviceAi.State.Downloading -> {
                    Text("Your phone can run it but Android hasn't fetched it yet", style = PlaitType.caption, color = plait.muted)
                    HubBtn(nanoNote ?: "Download with Android", {
                        scope.launch {
                            nanoNote = "Downloading…"
                            OnDeviceAi.download().collect { s ->
                                nanoNote = when (s) {
                                    is DownloadStatus.DownloadProgress -> "Downloaded ${s.totalBytesDownloaded / 1_000_000} MB"
                                    is DownloadStatus.DownloadFailed -> "The download failed. Tap to try again."
                                    else -> nanoNote
                                }
                            }
                            nano = OnDeviceAi.state(); nanoNote = null
                        }
                    }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = nanoNote == null)
                }
                OnDeviceAi.State.Unavailable -> StatusLine("Not available on this phone", BadgeKind.Warning)
                null -> StatusLine("Checking…", BadgeKind.Info)
            }
        }
        ModelCatalog.textModels.filter { it.supported }.forEach { spec ->
            ModelOption(
                spec = spec,
                selected = AiPrefs.textChoice == AiPrefs.TextChoice.Local && AiPrefs.textModelId == spec.id,
                onSelect = { AiPrefs.setTextChoice(context, AiPrefs.TextChoice.Local, spec.id) },
            )
        }
        OptionCard(
            selected = AiPrefs.textChoice == AiPrefs.TextChoice.Rules, title = "Plait's own rules only", badges = listOf(BadgeSpec("Default", BadgeKind.Default), BadgeSpec("0 MB", BadgeKind.Size)),
            body = "No AI model. Plait picks out dates, decisions, risks and follow-ups with fixed rules. Fast and light, but it can't write sentences.",
            onSelect = { AiPrefs.setTextChoice(context, AiPrefs.TextChoice.Rules) },
        )
    }
}

@Composable
private fun SpeechCards() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Voice notes", style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Recording only records. When you stop, a Whisper model writes the transcript from the saved audio, then your writing model summarises it and suggests tasks. It works in many languages. Without one, recordings are saved as audio only.",
            style = PlaitType.caption, color = plait.muted,
        )
        OptionCard(
            selected = AiPrefs.speechModelId == null, title = "No transcription", badges = noTranscriptionBadges(AiPrefs.speechModelId == null),
            body = "Nothing chosen. Plait uses a Whisper model if one is installed; otherwise the recording is saved as audio.",
            onSelect = { AiPrefs.setSpeechModel(context, null) },
        )
        ModelCatalog.speechModels.forEach { spec ->
            ModelOption(spec, selected = AiPrefs.speechModelId == spec.id, onSelect = { AiPrefs.setSpeechModel(context, spec.id) })
        }
        Text("Language of your voice notes", style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (AiPrefs.speechLanguages + AiPrefs.mixedLanguages).forEach { (code, name) ->
                HubChip(name, AiPrefs.speechLang == code, { AiPrefs.setSpeechLang(context, code) })
            }
        }
    }
}

@Composable
private fun SearchCard() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Search", style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 8.dp))
        Text("Word search always works. This optional model also finds notes by meaning.", style = PlaitType.caption, color = plait.muted)
        ModelCatalog.searchModels.forEach { spec -> ModelOption(spec, selected = ModelCenter.state(spec.id) is ModelState.Installed, onSelect = null) }
    }
}

@Composable
private fun StorageNote() {
    val context = LocalContext.current
    val used = ModelCatalog.all.filter { ModelCenter.state(it.id) is ModelState.Installed }.sumOf { it.bytes }
    Text(
        if (used > 0) "Models on this phone: ${ModelCatalog.formatSize(used)}. Deleting one gives the space back." else "No models downloaded.",
        style = PlaitType.caption, color = plait.muted,
    )
    if (ModelCenter.isMetered(context)) Text("You're on mobile data. Large downloads are better on Wi-Fi.", style = PlaitType.caption, color = plait.signal)
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun StatusLine(text: String, kind: BadgeKind = BadgeKind.Included) = PlaitBadge(text, kind)

/** A downloadable model: size up front, what it is good and bad at, and download, cancel or delete. */
@Composable
private fun ModelOption(spec: ModelSpec, selected: Boolean, onSelect: (() -> Unit)?) {
    val context = LocalContext.current
    val state = ModelCenter.state(spec.id)
    var confirmData by remember { mutableStateOf(false) }
    val installed = state is ModelState.Installed
    OptionCard(
        selected = selected && installed || (onSelect == null && installed), title = spec.title,
        badges = modelBadges(spec.sizeLabel, isDefault = selected && installed && onSelect != null, included = onSelect == null && installed),
        body = spec.blurb,
        meta = buildString {
            append(spec.strengths).append(" ").append(spec.limits)
            if (spec.ramGb > 0) append(" Works best with ${spec.ramGb} GB of RAM or more.")
            append(" Licence: ${spec.license}.")
        },
        onSelect = if (installed) onSelect else null,
    ) {
        when (state) {
            is ModelState.Installed -> {
                StatusLine("Downloaded")
                HubBtn("Delete (frees ${spec.sizeLabel})", { ModelCenter.delete(context, spec.id) }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
            is ModelState.Downloading -> {
                val frac = if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                val tone = LocalHubLook.current.tone
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(plait.text.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(CircleShape).background(tone.deep))
                }
                StatusLine("${ModelCatalog.formatSize(state.done)} of ${ModelCatalog.formatSize(state.total)} · ${(frac * 100).toInt()}%", BadgeKind.Size)
                Text("Keep Plait open. It carries on from here if interrupted.", style = PlaitType.caption, color = plait.muted)
                HubBtn("Cancel", { ModelCenter.cancel(context, spec.id) }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
            else -> {
                (state as? ModelState.Failed)?.let { StatusLine("Download failed", BadgeKind.Warning); Text(it.reason, style = PlaitType.caption, color = plait.muted) }
                val metered = ModelCenter.isMetered(context) && spec.bytes > 50_000_000L
                HubBtn(
                    if (metered && confirmData) "Download ${spec.sizeLabel} on mobile data" else "Download · ${spec.sizeLabel}",
                    { if (metered && !confirmData) confirmData = true else { confirmData = false; ModelCenter.download(context, spec) } },
                    style = ButtonStyle.Primary, size = ButtonSize.Small,
                )
                if (metered && !confirmData) Text("You're on mobile data. Wi-Fi is better for this size.", style = PlaitType.caption, color = plait.muted)
            }
        }
    }
}

/** One choice in a list: a tone-tinted selector on the left, title and badges on top, details beneath. */
@Composable
private fun OptionCard(
    selected: Boolean, title: String, body: String, onSelect: (() -> Unit)?,
    badges: List<BadgeSpec> = emptyList(), meta: String? = null, extra: @Composable (() -> Unit)? = null,
) {
    val look = LocalHubLook.current
    val ink = ToneStyles.ink(app.plait.ui.theme.LocalIsDark.current)
    Column(
        Modifier.fillMaxWidth()
            .toneSurface(RoundedCornerShape(24.dp), look.tone, look.rotation, inner = true, strong = selected)
            .then(if (onSelect != null) Modifier.pressable(onSelect, androidx.compose.ui.semantics.Role.RadioButton, lift = 3.dp) else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 1.dp).size(24.dp).toneGlass(CircleShape, look.tone, selected), contentAlignment = Alignment.Center) {
                if (selected) Box(Modifier.size(10.dp).background(ink, CircleShape))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = PlaitType.bodyStrong.copy(fontSize = 17.sp), color = plait.text)
                if (badges.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { PlaitBadge(it.label, it.kind) }
                }
                Text(body, style = PlaitType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = plait.text.copy(alpha = 0.85f))
            }
        }
        if (meta != null) Text(meta, style = PlaitType.caption, color = plait.muted)
        extra?.invoke()
    }
}

internal data class BadgeSpec(val label: String, val kind: BadgeKind)

/** "Default" for the model in use, "Included" for an optional one that is on the phone, and always its size. */
internal fun modelBadges(sizeLabel: String, isDefault: Boolean, included: Boolean = false): List<BadgeSpec> = listOfNotNull(
    if (included) BadgeSpec("Included", BadgeKind.Included) else if (isDefault) BadgeSpec("Default", BadgeKind.Default) else null,
    BadgeSpec(sizeLabel, BadgeKind.Size),
)

internal fun noTranscriptionBadges(selected: Boolean): List<BadgeSpec> =
    (if (selected) listOf(BadgeSpec("Default", BadgeKind.Default)) else emptyList()) + BadgeSpec("Nothing to download", BadgeKind.Info)
