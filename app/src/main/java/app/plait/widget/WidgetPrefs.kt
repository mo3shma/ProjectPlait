package app.plait.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** How solid the widgets' panel is. */
enum class WidgetOpacity { Clear, Soft, Solid }

/** Plait: the app's own light and dark glass. Wallpaper: the phone's Material You colours (Android 12 and up; Plait elsewhere). */
enum class WidgetTheme { Plait, Wallpaper }

/** The widget options in Settings. Every widget reads them in [NextMeetingWidget.updateAll]; each setter refreshes the widgets. */
object WidgetPrefs {
    var opacity by mutableStateOf(WidgetOpacity.Soft); private set
    var theme by mutableStateOf(WidgetTheme.Plait); private set
    /** Join button on the next-meeting widgets. */
    var showJoin by mutableStateOf(true); private set
    /** The "then" line under today's next meeting on the 4×2 widget. */
    var showLater by mutableStateOf(true); private set
    /** A dot under each day that has meetings on the month widget. */
    var showDots by mutableStateOf(true); private set
    private var loaded = false

    private fun prefs(context: Context) = context.getSharedPreferences("plait", Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        opacity = runCatching { WidgetOpacity.valueOf(p.getString("widget_opacity", null).orEmpty()) }.getOrDefault(WidgetOpacity.Soft)
        theme = runCatching { WidgetTheme.valueOf(p.getString("widget_theme", null).orEmpty()) }.getOrDefault(WidgetTheme.Plait)
        showJoin = p.getBoolean("widget_join", true)
        showLater = p.getBoolean("widget_later", true)
        showDots = p.getBoolean("widget_dots", true)
    }

    private fun changed(context: Context) {
        val app = context.applicationContext
        Thread { runCatching { NextMeetingWidget.updateAll(app) } }.start()
    }

    fun setOpacity(context: Context, v: WidgetOpacity) { opacity = v; prefs(context).edit().putString("widget_opacity", v.name).apply(); changed(context) }
    fun setTheme(context: Context, v: WidgetTheme) { theme = v; prefs(context).edit().putString("widget_theme", v.name).apply(); changed(context) }
    fun setShowJoin(context: Context, v: Boolean) { showJoin = v; prefs(context).edit().putBoolean("widget_join", v).apply(); changed(context) }
    fun setShowLater(context: Context, v: Boolean) { showLater = v; prefs(context).edit().putBoolean("widget_later", v).apply(); changed(context) }
    fun setShowDots(context: Context, v: Boolean) { showDots = v; prefs(context).edit().putBoolean("widget_dots", v).apply(); changed(context) }
}
