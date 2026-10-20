package app.plait.data.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One new thing: a bold lead ("Suggested tasks.") and the sentence that explains it. */
data class NewsItem(val lead: String, val detail: String)

data class News(val version: String, val title: String, val items: List<NewsItem>)

/**
 * "What's new" after an update, and the first-run tour. The notes come from CHANGELOG.md, which is bundled with the
 * app, so they are written once for GitHub, the release and the app.
 */
object WhatsNew {
    /** What to show on top of the app right now. */
    enum class Show { Nothing, Tour, News }

    var show by mutableStateOf(Show.Nothing)

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("plait", Context.MODE_PRIVATE)

    private fun versionCode(c: Context): Long = runCatching {
        val p = c.packageManager.getPackageInfo(c.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
    }.getOrDefault(0L)

    /** Decides on launch: the tour for a new install, the news after an update, otherwise nothing. */
    fun decide(context: Context) {
        val p = prefs(context)
        val code = versionCode(context)
        if (!p.contains("tour_done")) {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            val freshInstall = info == null || info.firstInstallTime == info.lastUpdateTime
            if (freshInstall) { show = Show.Tour; return }
            // Someone who had Plait before the tour existed: they get the news, and can take the tour from Settings.
            p.edit().putBoolean("tour_done", true).apply()
        }
        if (p.getLong("news_seen", 0L) < code) {
            show = if (current(context) != null) Show.News else Show.Nothing
            if (show == Show.Nothing) p.edit().putLong("news_seen", code).apply()
        }
    }

    fun tourDone(context: Context) {
        prefs(context).edit().putBoolean("tour_done", true).putLong("news_seen", versionCode(context)).apply()
        show = Show.Nothing
    }

    fun newsSeen(context: Context) {
        prefs(context).edit().putLong("news_seen", versionCode(context)).apply()
        show = Show.Nothing
    }

    /** This version's notes, from the bundled changelog. */
    fun current(context: Context): News? {
        val version = Updater.currentVersion(context)
        val md = runCatching { context.assets.open("CHANGELOG.md").bufferedReader().readText() }.getOrNull() ?: return null
        return section(md, version)
    }

    /** The "## <version>: <title>" section of a changelog. */
    fun section(md: String, version: String): News? {
        val lines = md.lines()
        val start = lines.indexOfFirst { it.startsWith("## $version:") || it.trim() == "## $version" }
        if (start < 0) return null
        val title = lines[start].substringAfter(':', "").trim()
        val body = lines.drop(start + 1).takeWhile { !it.startsWith("## ") }.joinToString("\n")
        return News(version, title, items(body)).takeIf { it.items.isNotEmpty() }
    }

    /** Bullets like "- **Lead.** Detail. More detail." become a lead and the first sentence of the detail. */
    fun items(body: String): List<NewsItem> =
        body.lines().map { it.trim() }.filter { it.startsWith("- ") || it.startsWith("* ") }.map { raw ->
            val t = raw.drop(2).trim()
            val m = Regex("^\\*\\*(.+?)\\*\\*\\s*(.*)$").find(t)
            val lead = (m?.groupValues?.get(1) ?: "").trim()
            val rest = (m?.groupValues?.get(2) ?: t).replace("**", "").replace("`", "").trim()
            val first = rest.split(Regex("(?<=[.!?])\\s+")).firstOrNull().orEmpty()
            if (lead.isEmpty()) NewsItem(first, "") else NewsItem(lead, first)
        }.filter { it.lead.isNotBlank() }.take(6)
}
