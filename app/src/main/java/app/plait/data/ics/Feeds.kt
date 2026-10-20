package app.plait.data.ics

import app.plait.data.Source
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** A calendar subscribed to by link. */
data class Feed(val url: String, val name: String, val source: Source)

object Feeds {
    /** Accepts what people paste: webcal:// links, links with spaces around them. Null if it can't be a calendar link. */
    fun normalize(input: String): String? {
        var u = input.trim()
        if (u.startsWith("webcal://", ignoreCase = true)) u = "https://" + u.substring(9)
        if (u.startsWith("webcals://", ignoreCase = true)) u = "https://" + u.substring(10)
        if (u.startsWith("http://", ignoreCase = true)) u = "https://" + u.substring(7)
        if (!u.startsWith("https://", ignoreCase = true)) return null
        return try {
            val host = URI(u).host
            if (host.isNullOrBlank() || !host.contains('.')) null else u
        } catch (e: Exception) {
            null
        }
    }

    fun sourceFor(url: String): Source {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return when {
            "outlook.office365.com" in host || "outlook.live.com" in host || "outlook.office.com" in host ||
                host.endsWith("office365.com") || host.endsWith("microsoft.com") -> Source.Outlook
            host.endsWith("icloud.com") -> Source.ICloud
            host.endsWith("google.com") -> Source.Google
            else -> Source.Other
        }
    }

    fun fallbackName(url: String): String =
        runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("Calendar").ifBlank { "Calendar" }

    fun encode(feeds: List<Feed>): String {
        val arr = JSONArray()
        feeds.forEach { arr.put(JSONObject().put("url", it.url).put("name", it.name).put("source", it.source.name)) }
        return arr.toString()
    }

    fun decode(json: String?): List<Feed> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Feed(o.getString("url"), o.optString("name"), Source.valueOf(o.optString("source", "Other")))
            }
        }.getOrDefault(emptyList())
    }
}

class FeedException(message: String) : Exception(message)

/** Downloads a calendar feed. Blocking: call from a background dispatcher. */
object IcsClient {
    private const val MAX_BYTES = 12 * 1024 * 1024

    fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 25_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "text/calendar, text/plain, */*")
            conn.setRequestProperty("User-Agent", "Plait/0.3")
            val code = conn.responseCode
            if (code == 404 || code == 410) throw FeedException("That calendar link no longer works. Ask for a new one.")
            if (code in 401..403) throw FeedException("That calendar link needs a sign-in. Use a published or secret link instead.")
            if (code !in 200..299) throw FeedException("The calendar server answered $code.")
            val bytes = conn.inputStream.use { it.readNBytes(MAX_BYTES) }
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }
}
