package app.plait

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import app.plait.share.ShareInbox
import app.plait.ui.screens.AttachSheet
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.plait.ui.Entrance
import app.plait.ui.screens.EventScreen
import app.plait.ui.screens.HubScreen
import app.plait.ui.screens.RecordScreen
import app.plait.ui.screens.TodayScreen
import app.plait.ui.glass.ProvideGlassEnv
import com.kyant.backdrop.backdrops.layerBackdrop
import app.plait.ui.theme.PlaitTheme

/** A meeting to open, asked for by a notification or the widget. */
object Launch {
    var event by androidx.compose.runtime.mutableStateOf<Pair<String, Int>?>(null)

    fun from(intent: Intent?) {
        val id = intent?.getStringExtra(app.plait.reminder.Reminders.EXTRA_EVENT) ?: return
        event = id to intent.getIntExtra("plait_tab", 0)
        intent.removeExtra(app.plait.reminder.Reminders.EXTRA_EVENT)
    }
}

/** Spatial (slide) and effects (fade) springs of the Material 3 Expressive motion scheme. */
private fun pageIn(dir: Int): EnterTransition {
    val calm = app.plait.ui.theme.Appearance.calm
    return slideInHorizontally(
        if (calm) tween(120) else androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f),
    ) { dir * it / (if (calm) 20 else 4) } + fadeIn(if (calm) tween(100) else androidx.compose.animation.core.spring(stiffness = 1600f))
}
private fun pageOut(dir: Int): ExitTransition {
    val calm = app.plait.ui.theme.Appearance.calm
    return slideOutHorizontally(
        if (calm) tween(120) else androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f),
    ) { dir * it / (if (calm) 24 else 6) } + fadeOut(if (calm) tween(80) else androidx.compose.animation.core.spring(stiffness = 1600f))
}

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShareInbox.handle(this, intent)
        Launch.from(intent)
        app.plait.ui.WidgetLaunch.from(intent)
    }

    override fun onStop() {
        super.onStop()
        // The widget shows the same day as the app; bring it up to date whenever you leave.
        val ctx = applicationContext
        Thread { runCatching { app.plait.widget.NextMeetingWidget.updateAll(ctx) } }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        app.plait.diag.Diag.init(this)
        // Phones stay upright; tablets and foldables can turn.
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp < 600) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        app.plait.data.sync.Tombstones.init(this)
        // Count any model files already on the phone (including ones copied into the drop folder), so nothing is downloaded twice.
        Thread { runCatching { app.plait.ai.ModelCenter.refresh(applicationContext); app.plait.ai.ModelFinder.scanThisPhone(applicationContext); app.plait.ai.ModelCenter.resumePending(applicationContext) } }.start()
        Entrance.arrived()
        if (savedInstanceState == null) { ShareInbox.handle(this, intent); Launch.from(intent); app.plait.ui.WidgetLaunch.from(intent) }
        setContent {
            PlaitTheme {
              ProvideGlassEnv {
                val nav = rememberNavController()
                val overlay = androidx.compose.runtime.remember { app.plait.ui.OverlayState() }
                val rootBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
                // Dialogs are drawn here, in the app's own composition, so glass can blur the screen behind them. The screen is only
                // recorded into the blur layer while a dialog is open and glass effects are on.
                val record = overlay.layers.isNotEmpty() && app.plait.ui.glassOverlayWanted()
                androidx.compose.runtime.CompositionLocalProvider(app.plait.ui.LocalOverlay provides overlay) {
                Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().then(if (record) Modifier.layerBackdrop(rootBackdrop) else Modifier)) {
                NavHost(
                    navController = nav,
                    startDestination = "today",
                    // Material 3 Expressive page motion in both styles: a spring slide with a quick fade. With "Reduce animations"
                    // the slide is small and quick, but it is still there as feedback.
                    enterTransition = { pageIn(1) },
                    exitTransition = { pageOut(-1) },
                    popEnterTransition = { pageIn(-1) },
                    popExitTransition = { pageOut(1) },
                ) {
                    composable("today") {
                        TodayScreen(
                            onOpenEvent = { id -> if (id.isNotEmpty()) { Entrance.arrived(); nav.navigate("event/${Uri.encode(id)}") } },
                            onOpenAt = { id, tab -> if (id.isNotEmpty()) { Entrance.arrived(); nav.navigate("event/${Uri.encode(id)}?tab=$tab") } },
                            onOpenNotes = { id -> Entrance.arrived(); nav.navigate("event/${Uri.encode(id)}?tab=2") },
                            onRecord = { id -> Entrance.arrived(); nav.navigate("record/${Uri.encode(id.ifEmpty { "_none_" })}") },
                            onOpenHub = { Entrance.arrived(); nav.navigate("hub") },
                        )
                    }
                    composable("hub") {
                        HubScreen(onBack = { nav.popBackStack() })
                    }
                    composable(
                        "event/{id}?tab={tab}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                        ),
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty()
                        EventScreen(
                            eventId = id,
                            startTab = entry.arguments?.getInt("tab") ?: 0,
                            onBack = { nav.popBackStack() },
                            onRecord = { id -> Entrance.arrived(); nav.navigate("record/${Uri.encode(id)}") },
                        )
                    }
                    composable(
                        "record/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        enterTransition = { slideInVertically(tween(450)) { it / 6 } + fadeIn(tween(450)) },
                        exitTransition = { ExitTransition.None },
                        popExitTransition = { slideOutVertically(tween(350)) { it / 6 } + fadeOut(tween(300)) },
                    ) { entry ->
                        val id = entry.arguments?.getString("id").orEmpty().let { if (it == "_none_") "" else it }
                        RecordScreen(
                            eventId = id,
                            onClose = { nav.popBackStack() },
                            onSaved = { _ ->
                                // Go back to where the recording started, so the new note is the first thing seen there.
                                // If it started elsewhere (Today, the Calendar), open the meeting without losing that spot.
                                val cameFrom = nav.previousBackStackEntry?.arguments?.getString("id")
                                if (id.isEmpty() || cameFrom == id) nav.popBackStack()
                                else {
                                    nav.popBackStack()
                                    Entrance.arrived()
                                    nav.navigate("event/${Uri.encode(id)}")
                                }
                            },
                        )
                    }
                }
                }
                // A soft fade under the status bar: scrolling content no longer collides with the clock and icons.
                val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val ground = app.plait.ui.theme.plait.ground
                // Under any scrim this fade paints nothing (its alpha is read in the draw lambda), so the dimmed top is the
                // scrim over the content, uniform edge to edge, with no light band left over it.
                val fade = androidx.compose.runtime.remember(ground) {
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(ground.copy(alpha = 0.92f), ground.copy(alpha = 0f)))
                }
                Box(
                    Modifier.align(androidx.compose.ui.Alignment.TopCenter).fillMaxWidth().height(statusTop + 12.dp)
                        .drawBehind {
                            val k = app.plait.ui.ScreenDim.fadeMultiplier(app.plait.ui.ScreenDim.alpha)
                            if (k > 0.001f) drawRect(fade, alpha = k)
                        },
                )
                if (app.plait.diag.FrameMonitor.enabled) {
                    androidx.compose.material3.Text(
                        app.plait.diag.FrameMonitor.summary, color = androidx.compose.ui.graphics.Color.White, fontSize = 11.sp,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopStart).statusBarsPadding().padding(start = 8.dp, top = 4.dp)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)).padding(4.dp),
                    )
                }
                Launch.event?.let { (id, tab) ->
                    androidx.compose.runtime.LaunchedEffect(id, tab) {
                        Entrance.arrived()
                        nav.navigate("event/${Uri.encode(id)}?tab=$tab")
                        Launch.event = null
                    }
                }
                // The 4x3 widget: back to the home screen, then a voice note, or the Notes tab with its editor open.
                app.plait.ui.WidgetLaunch.request?.let { kind ->
                    androidx.compose.runtime.LaunchedEffect(kind) {
                        app.plait.ui.WidgetLaunch.request = null
                        nav.popBackStack("today", false)
                        Entrance.arrived()
                        if (kind == "voice") nav.navigate("record/_none_")
                        else {
                            app.plait.ui.HomeState.tab = 2
                            app.plait.ui.HomeState.notesList.requestScrollToItem(0)
                            app.plait.ui.WidgetLaunch.editor = kind
                        }
                    }
                }
                app.plait.ui.screens.LaunchCards()
                ShareInbox.pending?.let { shared ->
                    AttachSheet(
                        shared = shared,
                        onAttached = { ev ->
                            ShareInbox.pending = null
                            nav.navigate("event/${Uri.encode(ev.id)}?tab=1")
                        },
                        onDismiss = { ShareInbox.pending = null },
                    )
                }
                app.plait.ui.OverlayHost(overlay, rootBackdrop)
                }
                }
              }
            }
        }
    }
}
