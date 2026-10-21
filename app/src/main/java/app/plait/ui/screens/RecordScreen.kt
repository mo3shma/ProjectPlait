package app.plait.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.plait.audio.RecPhase
import app.plait.audio.RecordingHub
import app.plait.data.Calendars
import app.plait.ui.glass.GlassIconButton
import app.plait.ui.glass.GlassPillButton
import app.plait.ui.glass.GlassVariant
import app.plait.ui.glass.LocalGlassEnv
import app.plait.ui.glass.liquidGlass
import app.plait.ui.rise
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun RecordScreen(eventId: String, callMode: Boolean = false, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    val env = LocalGlassEnv.current
    val event = remember(eventId) { Calendars.find(eventId) ?: Calendars.FREE_NOTE }
    val controller = remember(eventId) { RecordingHub.open(context, event) }

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted) {
            // The notification permission only affects whether the "recording" banner shows.
            val wanted = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(wanted.toTypedArray())
        }
    }
    // Attaching to a session that is already live must not start a second one.
    LaunchedEffect(granted) {
        if (granted && controller.phase == RecPhase.Idle) RecordingHub.begin(context, controller)
    }

    val finish: () -> Unit = {
        controller.stop { id ->
            RecordingHub.finish(context, controller)
            if (id != null) onSaved(id) else onClose()
        }
    }
    BackHandler { if (controller.phase == RecPhase.Idle || controller.phase == RecPhase.Failed) { RecordingHub.finish(context, controller); onClose() } else finish() }

    val paused = controller.phase == RecPhase.Paused
    val saving = controller.phase == RecPhase.Saving
    val seconds = (controller.elapsedMs / 1000).toInt()

    val infinite = rememberInfiniteTransition(label = "rec")
    val breathe by infinite.animateFloat(0f, 1f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val blink by infinite.animateFloat(1f, 0.2f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "blink")
    val live = if (paused || env.reduceMotion) 0f else 1f

    Box(Modifier.fillMaxSize().background(plait.ground)) {
        // Content layer: orbs, timer, waveform and live transcript. This is the rich
        // background the Clear glass controls sit over.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(Modifier.align(Alignment.TopCenter).offset(y = 190.dp)) {
                listOf(460, 320, 190).forEachIndexed { i, d ->
                    val phase = (breathe + i * 0.33f) % 1f
                    // The orbs also swell with how loud the room is.
                    val loud = controller.levels.lastOrNull() ?: 0f
                    val s = 1f + (0.12f * phase + 0.18f * loud) * live
                    Box(
                        Modifier.align(Alignment.Center).size(d.dp)
                            .graphicsLayer { scaleX = s; scaleY = s; alpha = 0.22f - 0.12f * phase * live }
                            .background(plait.signal, CircleShape)
                    )
                }
            }

            Column(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 104.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).graphicsLayer { alpha = if (paused || saving) 1f else blink }.background(plait.signal, CircleShape))
                    val marks = controller.bookmarksMs.size
                    val label = when {
                        saving -> "Saving"
                        paused -> "Paused"
                        controller.phase == RecPhase.Failed -> "Can't record"
                        marks > 0 -> "Recording · $marks bookmark" + if (marks > 1) "s" else ""
                        else -> "Recording"
                    }
                    Text(label, style = PlaitType.monoLabel.copy(fontSize = 14.sp), color = plait.signal)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "%02d:%02d".format(seconds / 60, seconds % 60),
                    style = PlaitType.heroNumber.copy(fontSize = 96.sp, lineHeight = 96.sp, letterSpacing = (-4).sp, fontFeatureSettings = "tnum"),
                    color = plait.text,
                )
                Spacer(Modifier.height(34.dp))
                Waveform(controller.levels, paused || env.reduceMotion)
            }

            // Live transcript as one running paragraph. Whisper writes it a phrase at a time, after each pause.
            val context = androidx.compose.ui.platform.LocalContext.current
            val whisper = app.plait.ai.ModelCatalog.speechModels.any { app.plait.ai.ModelCenter.isInstalled(context, it.id) }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 190.dp).fillMaxWidth().height(220.dp).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                LanguagePicker()
                when {
                    controller.speechReady == null && granted && controller.phase != RecPhase.Failed ->
                        Text("Getting the speech model ready. Your recording has already started.", style = PlaitType.caption, color = plait.muted)
                    controller.speechReady == false && whisper ->
                        Text("Transcription isn't available on this device. The audio is still being saved.", style = PlaitType.caption, color = plait.muted)
                    controller.speechReady == true && controller.phase == RecPhase.Recording ->
                        Text(if (controller.speaking) "Hearing you" else if (callMode) "Listening to the call. Put it on speaker so everyone is heard." else "Listening. Silence isn't transcribed.", style = PlaitType.monoSmall, color = if (controller.speaking) plait.signal else plait.muted)
                }
                if (!whisper) {
                    val tiny = remember { app.plait.ai.ModelCatalog.byId("speech-whisper-tiny")!! }
                    val st = app.plait.ai.ModelCenter.state(tiny.id)
                    Text(
                        "To write your words, get a speech model. It runs on your phone. Your recording is saved either way.",
                        style = PlaitType.body, color = plait.muted,
                    )
                    if (st is app.plait.ai.ModelState.Downloading) {
                        Text("Downloading ${tiny.title}… ${(st.done * 100 / st.total.coerceAtLeast(1))}%. It carries on if you leave.", style = PlaitType.caption, color = plait.accent)
                    } else {
                        app.plait.ui.PlaitButton("Get ${tiny.title} · ${tiny.sizeLabel}", { app.plait.ai.ModelCenter.download(context, tiny) }, style = app.plait.ui.ButtonStyle.Primary, size = app.plait.ui.ButtonSize.Small)
                    }
                } else {
                    val running = remember(controller.lines.size) {
                        app.plait.data.Transcript.paragraphs(controller.lines.toList()).lastOrNull()?.text?.let { if (it.length > 220) "…" + it.takeLast(220).substringAfter(' ') else it }.orEmpty()
                    }
                    if (running.isNotEmpty()) Text(
                        running,
                        style = PlaitType.body.copy(fontSize = 18.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
                        color = plait.text, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!granted && asked) {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 36.dp).offset(y = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Filled.Mic, null, tint = plait.muted, modifier = Modifier.size(32.dp))
                    Text("Plait needs the microphone to record.", style = PlaitType.bodyStrong, color = plait.text, textAlign = TextAlign.Center)
                    Text("Voice notes stay on your phone. Allow microphone access in Settings to continue.", style = PlaitType.body, color = plait.muted, textAlign = TextAlign.Center)
                }
            }
            controller.failure?.let {
                Text(
                    it, style = PlaitType.bodyStrong, color = plait.text, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 36.dp).offset(y = 40.dp),
                )
            }
        }

        // Functional layer: toolbar
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp).rise(0),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                Icons.Filled.Close, "Close recorder",
                { if (controller.phase == RecPhase.Idle || controller.phase == RecPhase.Failed) { RecordingHub.finish(context, controller); onClose() } else finish() },
                backdrop, variant = GlassVariant.Clear,
            )
            Row(
                Modifier.widthIn(max = 210.dp).liquidGlass(backdrop, variant = GlassVariant.Clear).height(40.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).background(plait.google, CircleShape))
                Text(controller.eventTitle, style = PlaitType.caption.copy(fontWeight = FontWeight.SemiBold), color = plait.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Functional layer: controls. Clear glass over the rich orbs, with the stop action
        // tinted to mark it as primary. Spacing keeps the three shapes from touching.
        if (!granted && asked) {
            GlassPillButton(
                label = "Open settings",
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                backdrop = backdrop,
                tint = plait.accent,
                contentColor = plait.onAccent,
                height = 64.dp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp),
            )
        } else {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 22.dp).rise(3),
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    Icons.Filled.Bookmark, "Add a bookmark", { controller.bookmark() }, backdrop,
                    size = 64.dp, iconSize = 26.dp, variant = GlassVariant.Clear,
                )
                GlassIconButton(
                    Icons.Filled.Stop, "Stop and save", { finish() }, backdrop,
                    size = 88.dp, iconSize = 32.dp, tint = plait.signal, iconTint = Color(0xFF0E1316),
                )
                GlassIconButton(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    if (paused) "Resume recording" else "Pause recording",
                    { if (paused) controller.resume() else controller.pause() }, backdrop,
                    size = 64.dp, iconSize = 26.dp, variant = GlassVariant.Clear,
                )
            }
        }
    }
}

/** One bar per recent loudness sample, so the shape you see is the room you're in. */
@Composable
private fun Waveform(levels: List<Float>, still: Boolean) {
    Row(
        Modifier.width(300.dp).height(76.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        levels.forEach { level ->
            val h = 6f + (if (still) 0.05f else level) * 70f
            Box(
                Modifier.width(5.dp).height(h.dp)
                    .background(plait.text, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun Icon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String?, tint: Color, modifier: Modifier) {
    androidx.compose.material3.Icon(icon, description, tint = tint, modifier = modifier)
}
