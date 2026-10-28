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
import androidx.compose.ui.res.stringResource
import app.plait.R
import app.plait.i18n.Bidi
import app.plait.i18n.Txt

/**
 * The AI part of the hub: choose what writes summaries (Gemini Nano, a downloaded model, or Plait's own rules) and
 * what transcribes voice notes, with the size of every download shown up front. Nothing downloads until you tap it.
 */
fun LazyListScope.aiSection() {
    item { BetterVoiceCard() }
    item {
        val context = LocalContext.current
        remember { AiPrefs.load(context); ModelCenter.refresh(context) }
        FoldNote(stringResource(R.string.settings_ai_intro))
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
        Text(stringResource(R.string.settings_ai_defaults), style = PlaitType.bodyStrong.copy(fontSize = 17.sp), color = plait.text)
        DefaultRow(stringResource(R.string.settings_ai_writing), (status?.label ?: "…") + (status?.note?.let { " · $it" } ?: ""), stringResource(R.string.settings_ai_writing_covers))
        DefaultRow(stringResource(R.string.settings_ai_voice_notes), speech?.title ?: stringResource(R.string.settings_ai_none_yet), stringResource(R.string.settings_ai_voice_covers))
        DefaultRow(stringResource(R.string.common_search), stringResource(if (search != null) R.string.settings_ai_search_meaning else R.string.settings_ai_search_words), stringResource(R.string.settings_ai_search_covers))
        Text(stringResource(R.string.settings_ai_defaults_note), style = PlaitType.caption, color = plait.muted)
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
        val comma = Txt.s(R.string.settings_list_separator)
        val names = r.copied.joinToString(Txt.s(R.string.settings_list_separator_semi)) { Bidi.isolate(it) }
        note = when {
            r.copied.isEmpty() && r.partial.isNotEmpty() -> Txt.s(R.string.settings_ai_nothing_new, where, r.partial.joinToString(comma))
            r.copied.isEmpty() -> Txt.s(R.string.settings_ai_no_files, where)
            r.completed.isNotEmpty() -> Txt.plural(R.plurals.settings_ai_found_ready, r.copied.size, r.copied.size, where, names, r.completed.joinToString(comma) { it.title })
            else -> Txt.plural(R.plurals.settings_ai_found, r.copied.size, r.copied.size, where, names)
        }
    }
    val folder = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch { busy = true; val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.ai.ModelFinder.scanTree(context, uri) }; show(r, Txt.s(R.string.settings_ai_where_folder)); busy = false }
    }
    val files = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { busy = true; val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.ai.ModelFinder.scanFiles(context, uris) }; show(r, Txt.s(R.string.settings_ai_where_files)); busy = false }
    }
    // On opening, quietly count anything that is already in Plait's folders.
    LaunchedEffect(Unit) {
        val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.ai.ModelFinder.scanThisPhone(context) }
        if (r.copied.isNotEmpty()) show(r, Txt.s(R.string.settings_ai_where_phone))
    }
    val look = LocalHubLook.current
    Column(Modifier.fillMaxWidth().toneSurface(RoundedCornerShape(24.dp), look.tone, look.rotation, inner = true).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.settings_ai_existing_title), style = PlaitType.bodyStrong.copy(fontSize = 17.sp), color = plait.text)
        Text(
            stringResource(R.string.settings_ai_existing_body),
            style = PlaitType.body.copy(fontSize = 14.sp, lineHeight = 20.sp), color = plait.text.copy(alpha = 0.85f),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HubBtn(stringResource(if (busy) R.string.settings_ai_looking else R.string.settings_ai_choose_folder), { folder.launch(null) }, size = ButtonSize.Small, enabled = !busy)
            HubBtn(stringResource(R.string.settings_ai_choose_files), { files.launch(arrayOf("*/*")) }, size = ButtonSize.Small, enabled = !busy)
            HubBtn(stringResource(R.string.settings_ai_check_phone), { scope.launch { busy = true; val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.ai.ModelFinder.scanThisPhone(context) }; show(r, Txt.s(R.string.settings_ai_where_phone)); busy = false } }, style = ButtonStyle.Quiet, size = ButtonSize.Small, enabled = !busy)
        }
        if (drop != null) Text(stringResource(R.string.settings_ai_drop_folder, Bidi.isolate(drop)), style = PlaitType.caption, color = plait.muted)
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
        Text(stringResource(R.string.settings_ai_text_title), style = PlaitType.bodyStrong, color = plait.text)
        status?.let {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlaitBadge(stringResource(R.string.settings_ai_using_now), BadgeKind.Included)
                Text(it.label + (it.note?.let { n -> " · $n" } ?: ""), style = PlaitType.caption, color = plait.muted, modifier = Modifier.weight(1f))
            }
        }

        OptionCard(
            selected = AiPrefs.textChoice == AiPrefs.TextChoice.Auto, title = stringResource(R.string.common_automatic),
            body = stringResource(R.string.settings_ai_auto_body),
            onSelect = { AiPrefs.setTextChoice(context, AiPrefs.TextChoice.Auto) },
        )
        OptionCard(
            selected = AiPrefs.textChoice == AiPrefs.TextChoice.Nano, title = stringResource(R.string.settings_ai_gemini_nano), badges = listOf(BadgeSpec(stringResource(R.string.settings_ai_nano_badge), BadgeKind.Info)),
            body = stringResource(R.string.settings_ai_nano_body),
            meta = stringResource(R.string.settings_ai_nano_meta),
            onSelect = { AiPrefs.setTextChoice(context, AiPrefs.TextChoice.Nano) },
        ) {
            when (nano) {
                OnDeviceAi.State.Ready -> StatusLine(stringResource(R.string.settings_ai_ready))
                OnDeviceAi.State.Downloadable, OnDeviceAi.State.Downloading -> {
                    Text(stringResource(R.string.settings_ai_nano_not_fetched), style = PlaitType.caption, color = plait.muted)
                    HubBtn(nanoNote ?: stringResource(R.string.settings_ai_nano_download), {
                        scope.launch {
                            nanoNote = Txt.s(R.string.settings_ai_downloading)
                            OnDeviceAi.download().collect { s ->
                                nanoNote = when (s) {
                                    is DownloadStatus.DownloadProgress -> Txt.s(R.string.settings_ai_downloaded_mb, (s.totalBytesDownloaded / 1_000_000).toInt())
                                    is DownloadStatus.DownloadFailed -> Txt.s(R.string.settings_ai_download_failed_retry)
                                    else -> nanoNote
                                }
                            }
                            nano = OnDeviceAi.state(); nanoNote = null
                        }
                    }, style = ButtonStyle.Primary, size = ButtonSize.Small, enabled = nanoNote == null)
                }
                OnDeviceAi.State.Unavailable -> StatusLine(stringResource(R.string.settings_ai_unavailable), BadgeKind.Warning)
                null -> StatusLine(stringResource(R.string.common_checking), BadgeKind.Info)
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
            selected = AiPrefs.textChoice == AiPrefs.TextChoice.Rules, title = stringResource(R.string.settings_ai_rules_title), badges = listOf(BadgeSpec(stringResource(R.string.common_default), BadgeKind.Default), BadgeSpec(stringResource(R.string.settings_ai_zero_mb), BadgeKind.Size)),
            body = stringResource(R.string.settings_ai_rules_body),
            onSelect = { AiPrefs.setTextChoice(context, AiPrefs.TextChoice.Rules) },
        )
    }
}

@Composable
private fun SpeechCards() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_ai_voice_notes), style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 8.dp))
        Text(
            stringResource(R.string.settings_ai_speech_body),
            style = PlaitType.caption, color = plait.muted,
        )
        OptionCard(
            selected = AiPrefs.speechModelId == null, title = stringResource(R.string.settings_ai_no_transcription), badges = noTranscriptionBadges(AiPrefs.speechModelId == null),
            body = stringResource(R.string.settings_ai_no_transcription_body),
            onSelect = { AiPrefs.setSpeechModel(context, null) },
        )
        ModelCatalog.speechModels.forEach { spec ->
            ModelOption(spec, selected = AiPrefs.speechModelId == spec.id, onSelect = { AiPrefs.setSpeechModel(context, spec.id) })
        }
        Text(stringResource(R.string.settings_ai_speech_language), style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 4.dp))
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
        Text(stringResource(R.string.common_search), style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.settings_ai_search_body), style = PlaitType.caption, color = plait.muted)
        ModelCatalog.searchModels.forEach { spec -> ModelOption(spec, selected = ModelCenter.state(spec.id) is ModelState.Installed, onSelect = null) }
    }
}

@Composable
private fun StorageNote() {
    val context = LocalContext.current
    val used = ModelCatalog.all.filter { ModelCenter.state(it.id) is ModelState.Installed }.sumOf { it.bytes }
    Text(
        if (used > 0) stringResource(R.string.settings_ai_storage_used, Bidi.isolate(ModelCatalog.formatSize(used))) else stringResource(R.string.settings_ai_storage_none),
        style = PlaitType.caption, color = plait.muted,
    )
    if (ModelCenter.isMetered(context)) Text(stringResource(R.string.settings_ai_metered), style = PlaitType.caption, color = plait.signal)
    var wifiOnly by remember { mutableStateOf(ModelCenter.wifiOnly(context)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_wifi_only_title), style = PlaitType.bodyStrong, color = plait.text)
            Text(stringResource(R.string.settings_wifi_only_body), style = PlaitType.caption, color = plait.muted)
        }
        PlaitSwitch(wifiOnly, { wifiOnly = !wifiOnly; ModelCenter.setWifiOnly(context, wifiOnly) }, stringResource(R.string.settings_wifi_only_title))
    }
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
        meta = listOfNotNull(
            spec.strengths, spec.limits,
            if (spec.ramGb > 0) stringResource(R.string.settings_ai_ram, spec.ramGb.toString()) else null,
            stringResource(R.string.settings_ai_license, spec.license),
        ).joinToString(" "),
        onSelect = if (installed) onSelect else null,
    ) {
        when (state) {
            is ModelState.Installed -> {
                StatusLine(stringResource(R.string.settings_ai_downloaded))
                HubBtn(stringResource(R.string.settings_ai_delete_frees, Bidi.isolate(spec.sizeLabel)), { ModelCenter.delete(context, spec.id) }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
            is ModelState.Downloading -> {
                val frac = if (state.total > 0) (state.done.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                val tone = LocalHubLook.current.tone
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(plait.text.copy(alpha = 0.12f))) {
                    Box(Modifier.fillMaxWidth(frac).height(8.dp).clip(CircleShape).background(tone.deep))
                }
                StatusLine(stringResource(R.string.settings_ai_progress, Bidi.isolate(ModelCatalog.formatSize(state.done)), Bidi.isolate(ModelCatalog.formatSize(state.total)), (frac * 100).toInt()), BadgeKind.Size)
                Text(stringResource(R.string.settings_ai_keep_open), style = PlaitType.caption, color = plait.muted)
                HubBtn(stringResource(R.string.common_cancel), { ModelCenter.cancel(context, spec.id) }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
            else -> {
                (state as? ModelState.Failed)?.let { StatusLine(stringResource(R.string.settings_ai_download_failed), BadgeKind.Warning); Text(it.reason, style = PlaitType.caption, color = plait.muted) }
                val metered = ModelCenter.isMetered(context) && spec.bytes > 50_000_000L
                HubBtn(
                    stringResource(if (metered && confirmData) R.string.settings_ai_download_metered else R.string.settings_ai_download_size, Bidi.isolate(spec.sizeLabel)),
                    { if (metered && !confirmData) confirmData = true else { confirmData = false; ModelCenter.download(context, spec) } },
                    style = ButtonStyle.Primary, size = ButtonSize.Small,
                )
                if (metered && !confirmData) Text(stringResource(R.string.settings_ai_metered_size), style = PlaitType.caption, color = plait.muted)
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
                    badges.forEach { PlaitBadge(if (it.kind == BadgeKind.Size) Bidi.isolate(it.label) else it.label, it.kind) }
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
    if (included) BadgeSpec(Txt.s(R.string.settings_ai_included), BadgeKind.Included) else if (isDefault) BadgeSpec(Txt.s(R.string.common_default), BadgeKind.Default) else null,
    BadgeSpec(sizeLabel, BadgeKind.Size),
)

internal fun noTranscriptionBadges(selected: Boolean): List<BadgeSpec> =
    (if (selected) listOf(BadgeSpec(Txt.s(R.string.common_default), BadgeKind.Default)) else emptyList()) + BadgeSpec(Txt.s(R.string.settings_ai_nothing_to_download), BadgeKind.Info)
