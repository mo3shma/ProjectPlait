package app.plait.data

import android.content.Context
import android.content.Intent
import android.net.Uri

enum class CallProvider(val label: String, val short: String) {
    Meet("Google Meet", "Meet"),
    Zoom("Zoom", "Zoom"),
    Teams("Microsoft Teams", "Teams"),
    Webex("Webex", "Webex"),
}

data class CallLink(val provider: CallProvider, val url: String)

/** Finds video-call links in the free text of a calendar event. */
object CallLinks {
    // Scheme is optional ("meet.google.com/abc-defg-hij"); the lookbehind stops matches inside other words or hosts.
    private const val S = """(?<![\w.@/-])(?:https://)?"""

    private val patterns: List<Pair<CallProvider, Regex>> = listOf(
        CallProvider.Meet to Regex("""${S}meet\.google\.com/[a-z]{3}-[a-z]{4}-[a-z]{3}(?:\?[^\s<>"']*)?""", RegexOption.IGNORE_CASE),
        CallProvider.Zoom to Regex("""${S}[a-z0-9.-]*zoom\.(?:us|com)/(?:j|my|wc/join)/[^\s<>"']+""", RegexOption.IGNORE_CASE),
        CallProvider.Teams to Regex("""${S}teams\.(?:microsoft\.(?:com|us)|live\.com)/(?:l/meetup-join|l/meeting|meet)/[^\s<>"']+""", RegexOption.IGNORE_CASE),
        CallProvider.Webex to Regex("""${S}[a-z0-9-]+\.webex\.com/(?:meet|join|wbxmjs)/[^\s<>"']+""", RegexOption.IGNORE_CASE),
    )

    private val trailing = charArrayOf('.', ',', ';', ')', ']', '>', '!', '?')

    /** Outlook and Exchange rewrite every link in an invite to a Safe Links address that hides the real URL. */
    private val safeLink = Regex("""https://[a-z0-9.-]+\.safelinks\.protection\.outlook\.com/\?[^\s<>"']*""", RegexOption.IGNORE_CASE)

    fun unwrapSafeLinks(text: String): String = safeLink.replace(text) { m ->
        val target = m.value.substringAfter('?').split('&')
            .firstOrNull { it.startsWith("url=", ignoreCase = true) }
            ?.substringAfter('=')
        val decoded = target?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        decoded ?: m.value
    }

    /** Returns the first link found, checking [fields] in order (location before description). */
    fun find(vararg fields: String?): CallLink? {
        for (raw in fields) {
            if (raw.isNullOrBlank()) continue
            val text = unwrapSafeLinks(raw.replace("&amp;", "&"))
            var best: Pair<Int, CallLink>? = null
            for ((provider, regex) in patterns) {
                val m = regex.find(text) ?: continue
                val link = CallLink(provider, m.value.trimEnd(*trailing).let { if (it.startsWith("https://", true)) it else "https://$it" })
                if (best == null || m.range.first < best.first) best = m.range.first to link
            }
            if (best != null) return best.second
        }
        return null
    }

    fun open(context: Context, link: CallLink): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
