package app.plait.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ui.PlaitDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.plait.ai.ModelCatalog
import app.plait.data.update.News
import app.plait.data.update.NewsItem
import app.plait.data.update.UpdateInfo
import app.plait.data.update.UpdateState
import app.plait.data.update.Updater
import app.plait.data.update.WhatsNew
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.PlaitButton
import app.plait.ui.popupSurface
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import app.plait.R
import app.plait.i18n.Bidi
import app.plait.i18n.userText

/** A list of "what's new" items: a coloured dot, the lead in bold, the explanation under it. */
@Composable
private fun NewsList(items: List<NewsItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.padding(top = 7.dp).size(8.dp).background(plait.accent, CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.lead.trimEnd('.'), style = PlaitType.bodyStrong.userText(), color = plait.text)
                    if (item.detail.isNotBlank()) Text(item.detail, style = PlaitType.body.userText(), color = plait.muted)
                }
            }
        }
    }
}

/** The card after an update: what this version brings. */
@Composable
fun WhatsNewDialog(news: News, onDone: () -> Unit) {
    PlaitDialog(onDismissRequest = onDone) { PopIn {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).popupSurface().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.sheet_news_title, Bidi.isolate(news.version)), style = PlaitType.bodyStrong, color = plait.muted)
            Text(news.title.replaceFirstChar { it.uppercase() }, style = PlaitType.section.userText(), color = plait.text)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { NewsList(news.items) }
            PlaitButton(stringResource(R.string.sheet_news_got_it), onDone, Modifier.fillMaxWidth(), style = ButtonStyle.Primary)
        }
    } }
}

/**
 * The update card: shown by itself when a newer Plait is out (once per version each launch, until "Later"), and from Settings.
 * It downloads with progress, asks Android for permission to install the first time, and hands over to the installer.
 */
@Composable
fun UpdatePrompt(forceOpen: Boolean = false, onClose: () -> Unit = {}) {
    val context = LocalContext.current
    val state = Updater.state
    val info: UpdateInfo? = when (state) {
        is UpdateState.Available -> state.info
        is UpdateState.Downloading -> state.info
        is UpdateState.NeedsPermission -> state.info
        is UpdateState.Installing -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    }
    var hidden by remember(info?.version) { mutableStateOf(false) }
    val autoShow = info != null && !hidden && true
    if (info == null || !(forceOpen || autoShow)) return
    val close = { hidden = true; onClose() }

    // Back from Android's "install unknown apps" screen: carry on if it was allowed.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) Updater.resumeInstall(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    PlaitDialog(onDismissRequest = close) { PopIn {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).popupSurface().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.SystemUpdate, null, tint = plait.accent, modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.sheet_update_ready, Bidi.isolate(info.version)), style = PlaitType.section, color = plait.text)
            }
            Text(if (info.bytes > 0) stringResource(R.string.sheet_update_have_size, Bidi.isolate(Updater.currentVersion(context)), Bidi.isolate(ModelCatalog.formatSize(info.bytes))) else stringResource(R.string.sheet_update_have, Bidi.isolate(Updater.currentVersion(context))), style = PlaitType.caption, color = plait.muted)
            val items = remember(info.notes) { WhatsNew.items(info.notes) }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (items.isNotEmpty()) NewsList(items) else if (info.notes.isNotBlank()) Text(info.notes.take(600), style = PlaitType.body.userText(), color = plait.text)
            }
            when (state) {
                is UpdateState.Downloading -> {
                    val total = info.bytes.coerceAtLeast(1)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(plait.surface)) {
                            Box(Modifier.fillMaxWidth((state.done.toFloat() / total).coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(plait.accent))
                        }
                        Text(stringResource(R.string.sheet_update_downloading, Bidi.isolate(ModelCatalog.formatSize(state.done)), Bidi.isolate(ModelCatalog.formatSize(info.bytes))), style = PlaitType.caption, color = plait.muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlaitButton(stringResource(R.string.sheet_update_hide), close, style = ButtonStyle.Secondary, size = ButtonSize.Small)
                        PlaitButton(stringResource(R.string.common_cancel), { Updater.cancel() }, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                    }
                }
                is UpdateState.NeedsPermission -> {
                    Text(stringResource(R.string.sheet_update_permission), style = PlaitType.body, color = plait.text)
                    PlaitButton(stringResource(R.string.sheet_update_allow), {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) runCatching {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }, Modifier.fillMaxWidth(), style = ButtonStyle.Primary)
                }
                is UpdateState.Installing -> Text(stringResource(R.string.sheet_update_installing), style = PlaitType.body, color = plait.muted)
                is UpdateState.Failed -> {
                    Text(state.reason, style = PlaitType.body, color = plait.signal)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlaitButton(stringResource(R.string.common_try_again), { Updater.download(context, info) }, style = ButtonStyle.Primary, size = ButtonSize.Small)
                        PlaitButton(stringResource(R.string.common_later), close, style = ButtonStyle.Quiet, size = ButtonSize.Small)
                    }
                }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaitButton(stringResource(R.string.sheet_update_now), { Updater.download(context, info) }, style = ButtonStyle.Primary)
                    PlaitButton(stringResource(R.string.common_later), close, style = ButtonStyle.Quiet)
                }
            }
        }
    } }
}

private class TourPage(val icon: ImageVector, val colors: List<Color>, @androidx.annotation.StringRes val title: Int, @androidx.annotation.StringRes val text: Int)

private val tourPages = listOf(
    TourPage(Icons.Filled.CalendarMonth, listOf(Color(0xFFD8EFFF), Color(0xFFA9D4FF)), R.string.sheet_tour_1_title, R.string.sheet_tour_1_text),
    TourPage(Icons.Filled.WbSunny, listOf(Color(0xFFFFF1C9), Color(0xFFFFC9A8)), R.string.sheet_tour_2_title, R.string.sheet_tour_2_text),
    TourPage(Icons.Filled.Groups, listOf(Color(0xFFE9DEFF), Color(0xFFF6C9DF)), R.string.sheet_tour_3_title, R.string.sheet_tour_3_text),
    TourPage(Icons.Filled.Mic, listOf(Color(0xFFFFD2C9), Color(0xFFE8725F)), R.string.sheet_tour_4_title, R.string.sheet_tour_4_text),
    TourPage(Icons.Filled.Lock, listOf(Color(0xFFC9F2D9), Color(0xFF7FD8A5)), R.string.sheet_tour_5_title, R.string.sheet_tour_5_text),
    TourPage(Icons.Filled.AutoAwesome, listOf(Color(0xFFC7CCFF), Color(0xFF8A96FF)), R.string.sheet_tour_6_title, R.string.sheet_tour_6_text),
)

/** The first-run tour: a few pages that show what Plait does, ending with connecting the phone's calendars. */
@Composable
fun OnboardingTour(onDone: () -> Unit) {
    val pager = rememberPagerState { tourPages.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The tour closes when Android answers, so the answer is not lost with the tour.
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[android.Manifest.permission.READ_CALENDAR] == true) app.plait.data.Calendars.refreshDevice(context)
        onDone()
    }
    val connect = { ask.launch(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR)) }
    val last = pager.currentPage == tourPages.lastIndex
    Box(Modifier.fillMaxSize().background(plait.ground)) {
        AmbientBackground()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Plait", style = PlaitType.section, color = plait.text, modifier = Modifier.weight(1f))
                if (!last) PlaitButton(stringResource(R.string.sheet_tour_skip), onDone, style = ButtonStyle.Quiet, size = ButtonSize.Small)
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                val p = tourPages[i]
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(150.dp).clip(CircleShape).background(Brush.linearGradient(p.colors)), contentAlignment = Alignment.Center) {
                        Icon(p.icon, null, tint = Color(0xFF0E1316), modifier = Modifier.size(64.dp))
                    }
                    Spacer(Modifier.height(36.dp))
                    Text(stringResource(p.title), style = PlaitType.title.copy(fontSize = 32.sp, lineHeight = 34.sp), color = plait.text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(p.text), style = PlaitType.body.copy(fontSize = 17.sp, lineHeight = 25.sp), color = plait.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                tourPages.indices.forEach { i ->
                    Box(Modifier.size(width = if (i == pager.currentPage) 24.dp else 8.dp, height = 8.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) plait.accent else plait.muted.copy(alpha = 0.35f)))
                }
            }
            if (last) {
                PlaitButton(stringResource(R.string.sheet_tour_use_calendars), { connect() }, Modifier.fillMaxWidth(), style = ButtonStyle.Primary)
                Spacer(Modifier.height(10.dp))
                PlaitButton(stringResource(R.string.common_not_now), onDone, Modifier.fillMaxWidth(), style = ButtonStyle.Quiet)
            } else {
                PlaitButton(stringResource(R.string.common_next), { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth(), style = ButtonStyle.Primary)
            }
        }
    }
}

/** On launch: the tour, the news after an update, or a waiting update. One at a time. */
@Composable
fun LaunchCards() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        WhatsNew.decide(context)
        Updater.checkInBackground(context)
    }
    when (WhatsNew.show) {
        WhatsNew.Show.Tour -> {
            val prefs = context.getSharedPreferences("plait", android.content.Context.MODE_PRIVATE)
            var setupDone by remember { mutableStateOf(prefs.getBoolean("firstrun_setup_done", false)) }
            if (setupDone) OnboardingTour { WhatsNew.tourDone(context) }
            else FirstRunSetup { prefs.edit().putBoolean("firstrun_setup_done", true).apply(); setupDone = true }
        }
        WhatsNew.Show.News -> {
            val news = remember { WhatsNew.current(context) }
            if (news != null) WhatsNewDialog(news) { WhatsNew.newsSeen(context) } else WhatsNew.newsSeen(context)
        }
        WhatsNew.Show.Nothing -> { UpdatePrompt(); BackupReminder() }
    }
}
