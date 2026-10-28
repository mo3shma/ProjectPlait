package app.plait.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.plait.R
import app.plait.ai.ModelCatalog
import app.plait.ai.ModelCenter
import app.plait.ai.ModelSpec
import app.plait.ai.ModelState
import app.plait.i18n.AppLocale
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.popupSurface
import kotlinx.coroutines.launch
import app.plait.i18n.Bidi
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait

/** The two models worth one tap: Whisper Base for speech, and the smallest Qwen the phone can run for writing. */
private fun recommended(): List<ModelSpec> = listOfNotNull(
    ModelCatalog.byId("speech-whisper-base"),
    ModelCatalog.textModels.firstOrNull { it.id == "text-qwen3-0.6b" && it.supported } ?: ModelCatalog.byId("text-qwen25-0.5b"),
)

/**
 * First run, before the tour: the language, then one tap for the recommended models (with a Wi-Fi-only choice that is on to
 * begin with), and a plain statement of what works with no model.
 */
@Composable
fun FirstRunSetup(onDone: () -> Unit) {
    val context = LocalContext.current
    val language = remember { AppLocale.choice(context) }
    val names = listOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_english),
        "ar" to stringResource(R.string.settings_language_arabic),
    )
    val models = remember { recommended() }
    val total = ModelCatalog.formatSize(models.sumOf { it.bytes })
    var wifiOnly by remember { mutableStateOf(true) }
    val states = models.map { ModelCenter.state(it.id) }
    val started = states.any { it is ModelState.Downloading } || states.all { it is ModelState.Installed }
    val failed = states.filterIsInstance<ModelState.Failed>().firstOrNull()

    Column(
        Modifier.fillMaxSize().background(plait.ground).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Plait", style = PlaitType.section, color = plait.text)
        Text(stringResource(R.string.settings_firstrun_lang_title), style = PlaitType.section, color = plait.text)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            names.forEach { (tag, name) -> HubChip(name, language == tag, { if (tag != language) AppLocale.choose(context, tag) }) }
        }

        Text(stringResource(R.string.settings_firstrun_models_title), style = PlaitType.section, color = plait.text, modifier = Modifier.padding(top = 10.dp))
        Text(stringResource(R.string.settings_firstrun_models_body), style = PlaitType.body, color = plait.muted)
        models.forEach { m ->
            Text(stringResource(R.string.settings_firstrun_model_row, Bidi.isolate(m.title), Bidi.isolate(m.sizeLabel)), style = PlaitType.bodyStrong, color = plait.text)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_wifi_only_title), style = PlaitType.bodyStrong, color = plait.text)
                Text(stringResource(R.string.settings_wifi_only_body), style = PlaitType.caption, color = plait.muted)
            }
            PlaitSwitch(wifiOnly, { wifiOnly = !wifiOnly }, stringResource(R.string.settings_wifi_only_title))
        }
        PlaitButton(
            stringResource(R.string.settings_firstrun_download, total),
            { ModelCenter.setWifiOnly(context, wifiOnly); models.forEach { ModelCenter.download(context, it) } },
            Modifier.fillMaxWidth(), style = ButtonStyle.Primary, enabled = !started,
        )
        if (started) Text(stringResource(R.string.settings_firstrun_downloading), style = PlaitType.caption, color = plait.muted)
        failed?.let { Text(it.reason, style = PlaitType.caption, color = plait.signal) }

        Text(stringResource(R.string.settings_firstrun_nomodel_title), style = PlaitType.bodyStrong, color = plait.text, modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(R.string.settings_firstrun_nomodel_body), style = PlaitType.caption, color = plait.muted)
        PlaitButton(stringResource(R.string.settings_firstrun_continue), onDone, Modifier.fillMaxWidth(), style = if (started) ButtonStyle.Primary else ButtonStyle.Quiet)
    }
}

/** Once a week at most, when the last backup is old (or never was): offers to make one into the sync folder. */
@Composable
fun BackupReminder() {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        show = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.data.backup.AutoBackup.shouldRemind(context) }
        if (show) app.plait.data.backup.AutoBackup.reminded(context)
    }
    if (!show) return
    val days = app.plait.data.backup.AutoBackup.staleDays(context) ?: 0
    val canRun = app.plait.data.backup.AutoBackup.canRun(context)
    app.plait.ui.PlaitDialog(onDismissRequest = { show = false }) { PopIn {
        Column(Modifier.fillMaxWidth().popupSurface().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.settings_backup_remind_title), style = PlaitType.section, color = plait.text)
            Text(
                note ?: if (days == 0) stringResource(R.string.settings_backup_never) else app.plait.i18n.Txt.plural(R.plurals.settings_backup_old, days),
                style = PlaitType.body, color = plait.muted,
            )
            if (note == null && !canRun) Text(stringResource(R.string.settings_backup_remind_where), style = PlaitType.caption, color = plait.muted)
            if (note == null && canRun) PlaitButton(stringResource(R.string.settings_backup_remind_now), {
                scope.launch {
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app.plait.data.backup.AutoBackup.backUpNow(context) }
                    note = app.plait.i18n.Txt.s(if (ok) R.string.settings_backup_remind_done else R.string.settings_backup_remind_failed)
                }
            }, Modifier.fillMaxWidth(), style = ButtonStyle.Primary)
            PlaitButton(stringResource(R.string.common_close), { show = false }, Modifier.fillMaxWidth(), style = ButtonStyle.Quiet)
        }
    } }
}
