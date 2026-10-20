package app.plait.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import app.plait.R

/** Palette beyond Material's roles: calendar sources and the record signal. */
data class PlaitColors(
    val ground: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val signal: Color,
    val google: Color,
    val outlook: Color,
    val icloud: Color,
)

val DarkPlait = PlaitColors(
    ground = Color(0xFF0E1316),
    surface = Color(0xFF172026),
    surfaceHigh = Color(0xFF1F2A31),
    text = Color(0xFFFFFFFF),
    muted = Color(0xFFB4BFC5),
    accent = Color(0xFFB7C3FF),
    accentSoft = Color(0xFFC9D2FF),
    onAccent = Color(0xFF0E1316),
    signal = Color(0xFFFF6A55),
    google = Color(0xFF7FB0FF),
    outlook = Color(0xFF4FD6C0),
    icloud = Color(0xFFFFC15E),
)

/** Dark theme on pure black, so an OLED screen switches its pixels off and the glass has real depth to sit on. */
val AmoledPlait = DarkPlait.copy(
    ground = Color(0xFF000000),
    surface = Color(0xFF0D0E10),
    surfaceHigh = Color(0xFF17191C),
    text = Color(0xFFFFFFFF),
    muted = Color(0xFFAEB8BE),
    onAccent = Color(0xFF000000),
)

/** Look and feel of the controls: Liquid Glass, or solid Material 3 Expressive surfaces with bouncier motion. */
enum class UiStyle { Glass, Material }

/** The user's look choices, kept on the phone. */
object Appearance {
    var amoled by androidx.compose.runtime.mutableStateOf(true); private set
    /** The style you picked. It takes effect after the app restarts. */
    var style by androidx.compose.runtime.mutableStateOf(UiStyle.Glass); private set
    /** The style this run of the app is using: read once at start, so the whole UI is built in one style. */
    var activeStyle: UiStyle = UiStyle.Glass; private set
    /** Glass effects on. Off draws plain translucent surfaces, which is lighter on mid-range phones. */
    var glass by androidx.compose.runtime.mutableStateOf(true); private set
    /** Fewer and shorter animations; nothing loops. */
    var calm by androidx.compose.runtime.mutableStateOf(false); private set
    private var loaded = false

    private fun prefs(c: android.content.Context) = c.getSharedPreferences("plait", android.content.Context.MODE_PRIVATE)

    fun load(context: android.content.Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        amoled = p.getBoolean("amoled", true)
        style = runCatching { UiStyle.valueOf(p.getString("style", "Glass")!!) }.getOrDefault(UiStyle.Glass)
        activeStyle = style
        // Phones that report low memory start with the lighter settings.
        val low = (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager).isLowRamDevice
        glass = p.getBoolean("glass", false)
        calm = p.getBoolean("calm", low)
    }

    fun setAmoled(context: android.content.Context, on: Boolean) { amoled = on; prefs(context).edit().putBoolean("amoled", on).apply() }
    fun setStyle(context: android.content.Context, v: UiStyle) { style = v; prefs(context).edit().putString("style", v.name).apply() }
    fun setGlass(context: android.content.Context, on: Boolean) { glass = on; prefs(context).edit().putBoolean("glass", on).apply() }
    fun setCalm(context: android.content.Context, on: Boolean) { calm = on; prefs(context).edit().putBoolean("calm", on).apply() }
}

val LightPlait = PlaitColors(
    ground = Color(0xFFF4F6F7),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFE8EDEF),
    text = Color(0xFF000000),
    muted = Color(0xFF3F4B52),
    accent = Color(0xFF3D4FD1),
    accentSoft = Color(0xFFC9D2FF),
    onAccent = Color(0xFFFFFFFF),
    signal = Color(0xFFE5432C),
    google = Color(0xFF2F6FD6),
    outlook = Color(0xFF0E9C89),
    icloud = Color(0xFFC98A12),
)

/** Tonal surface roles for the Material 3 Expressive style, mixed from the app's own ground and accent. */
val PlaitColors.mContainer: Color get() = androidx.compose.ui.graphics.lerp(ground, accent, 0.08f)
val PlaitColors.mContainerHigh: Color get() = androidx.compose.ui.graphics.lerp(ground, accent, 0.15f)
val PlaitColors.mPrimaryContainer: Color get() = androidx.compose.ui.graphics.lerp(ground, accent, 0.34f)

val LocalPlait = staticCompositionLocalOf { DarkPlait }
val LocalIsDark = staticCompositionLocalOf { true }

/**
 * AR One Sans (SIL Open Font Licence 1.1) is the one typeface used everywhere in the app. It is a variable font with
 * weights 400 to 700, so lighter and heavier requests are mapped to the nearest end.
 */
@OptIn(ExperimentalTextApi::class)
val OneSans = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { w ->
        Font(
            R.font.ar_one_sans, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w.coerceIn(400, 700))),
        )
    }
)
val Display = OneSans
val Body = OneSans
val Mono = OneSans

object PlaitType {
    val heroNumber = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 64.sp, lineHeight = 60.sp, letterSpacing = (-2.5).sp)
    val dateTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 52.sp, lineHeight = 52.sp, letterSpacing = (-1.6).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = (-1.4).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    val section = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.4).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    val cardTitle = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 26.sp, lineHeight = 29.sp, letterSpacing = (-0.5).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    /** Page headline on the home brief. */
    val headline = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 46.sp, lineHeight = 44.sp, letterSpacing = (-2).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    /** Meeting titles on brief cards. */
    val cardHeadline = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 32.sp, lineHeight = 32.sp, letterSpacing = (-1.1).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    val heroHeadline = TextStyle(fontFamily = Display, fontWeight = FontWeight(800), fontSize = 40.sp, lineHeight = 39.sp, letterSpacing = (-1.6).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 22.sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph)
    val bodyStrong = TextStyle(fontFamily = Body, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 20.sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 13.sp, lineHeight = 17.sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph)
    val monoLabel = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.4.sp)
    val monoTime = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp)
    val monoSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp)
}

@Composable
fun PlaitTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Appearance.load(context)
    if (Appearance.activeStyle == UiStyle.Material) {
        // Material 3 Expressive: the phone's own Material You colours where it has them, expressive motion and shapes,
        // and every colour the app uses is read from that scheme.
        val scheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (dark) androidx.compose.material3.dynamicDarkColorScheme(context) else androidx.compose.material3.dynamicLightColorScheme(context)
        } else if (dark) darkColorScheme(primary = DarkPlait.accent, onPrimary = DarkPlait.onAccent) else lightColorScheme(primary = LightPlait.accent, onPrimary = LightPlait.onAccent)
        val base = if (dark) DarkPlait else LightPlait
        val p = base.copy(
            ground = scheme.surface, surface = scheme.surfaceContainer, surfaceHigh = scheme.surfaceContainerHigh,
            text = if (dark) Color.White else Color.Black, muted = scheme.onSurfaceVariant,
            accent = scheme.primary, accentSoft = scheme.primaryContainer, onAccent = scheme.onPrimary,
        )
        CompositionLocalProvider(LocalPlait provides p, LocalIsDark provides dark, androidx.compose.material3.LocalContentColor provides p.text) {
            app.plait.ui.material.ExpressiveTheme(scheme.copy(onSurface = p.text, onBackground = p.text), content)
        }
        return
    }
    val p = when {
        !dark -> LightPlait
        Appearance.amoled -> AmoledPlait
        else -> DarkPlait
    }
    val scheme = if (dark) {
        darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.ground, surface = p.surface, onSurface = p.text, onBackground = p.text, error = p.signal)
    } else {
        lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.ground, surface = p.surface, onSurface = p.text, onBackground = p.text, error = p.signal)
    }
    // Text with no colour of its own follows the theme too, so it can never end up dark on dark or light on light.
    CompositionLocalProvider(LocalPlait provides p, LocalIsDark provides dark, androidx.compose.material3.LocalContentColor provides p.text) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** The colours of the current theme. */
val plait: PlaitColors
    @Composable
    @ReadOnlyComposable
    get() = LocalPlait.current
