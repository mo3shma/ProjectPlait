package app.plait.data

import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.plait.data.device.DeviceCalendar
import app.plait.reminder.Reminders
import app.plait.data.google.CalEventJson
import app.plait.data.google.GoogleApiException
import app.plait.data.google.GoogleCalendarClient
import app.plait.data.ics.Feed
import app.plait.data.ics.FeedException
import app.plait.data.ics.Feeds
import app.plait.data.ics.IcsClient
import app.plait.data.ics.IcsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.ZoneId

/**
 * What the app knows about the user's calendars. Three sources can be on at once: the calendars
 * already on the phone, a Google account read directly, and any number of calendars added by link
 * (Outlook, iCloud, anything that publishes an .ics feed). Real events replace the built-in
 * samples as soon as any source is on, and are cached so the agenda opens instantly and offline.
 */
object Calendars {
    var deviceOn by mutableStateOf(false); private set
    var googleOn by mutableStateOf(false); private set
    var feeds by mutableStateOf<List<Feed>>(emptyList()); private set
    var syncing by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var lastSyncMs by mutableLongStateOf(0L); private set

    private var deviceEvents by mutableStateOf<List<CalEvent>>(emptyList())
    private var googleEvents by mutableStateOf<List<CalEvent>>(emptyList())
    private var feedEvents by mutableStateOf<Map<String, List<CalEvent>>>(emptyMap())

    /** Account names the user chose to show, or null before they have chosen (everything shows). */
    var enabledAccounts by mutableStateOf<Set<String>?>(null); private set
    /** Phone calendars the user switched off in the hub. */
    var hidden by mutableStateOf<Set<Long>>(emptySet()); private set
    /** Where meetings created in Plait are saved (a phone calendar id), or 0 for "ask / not chosen". */
    var destinationId by mutableLongStateOf(0L); private set
    /** Whether voice-note summaries are written into the calendar event's description. */
    var syncNotes by mutableStateOf(false); private set
    /** Phone calendars as last read, for the hub. */
    var deviceCals by mutableStateOf<List<DeviceCalendar.DeviceCal>>(emptyList()); private set

    private val merged = derivedStateOf {
        (AccountFilter.visible(deviceEvents, enabledAccounts, hidden) + googleEvents + feedEvents.values.flatten())
            .distinctBy { it.id }
            .sortedWith(compareBy({ !it.allDay }, { it.startMs }))
    }
    val events: List<CalEvent> get() = merged.value

    val connected: Boolean get() = deviceOn || googleOn || feeds.isNotEmpty()

    private var loaded = false
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var deviceJob: Job? = null

    /** Past three months and next six, so the month view and "earlier" both have something to show. */
    fun window(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val today = Agenda.dayBounds(nowMs, zone).first
        return today - 90L * DAY to today + 180L * DAY
    }

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val prefs = prefs(context)
        lastSyncMs = prefs.getLong("last_sync", 0L)
        val legacy = prefs.getString("mode", null)
        deviceOn = (prefs.getBoolean("device_on", false) || legacy == "device") && DeviceCalendar.hasPermission(context)
        googleOn = prefs.getBoolean("google_on", false) || legacy == "google" || prefs.getBoolean("google_connected", false)
        feeds = Feeds.decode(prefs.getString("feeds", null))
        hidden = prefs.getStringSet("hidden_cals", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        enabledAccounts = if (prefs.contains("enabled_accounts")) prefs.getStringSet("enabled_accounts", emptySet()).orEmpty().toSet() else null
        destinationId = prefs.getLong("dest_cal", 0L)
        syncNotes = prefs.getBoolean("sync_notes", false)

        if (deviceOn) refreshDevice(context) // async: cached state shows first, events fill in a moment later
        if (googleOn) file(context, "events_cache.json").takeIf { it.exists() }?.let {
            runCatching { googleEvents = CalEventJson.decode(it.readText()) }
        }
        if (feeds.isNotEmpty()) file(context, "feeds_cache.json").takeIf { it.exists() }?.let {
            runCatching {
                val o = JSONObject(it.readText())
                feedEvents = o.keys().asSequence().associateWith { url -> CalEventJson.decode(o.getString(url)) }
            }
        }
    }

    fun find(id: String): CalEvent? = if (id.isEmpty()) FREE_NOTE else events.firstOrNull { it.id == id } ?: orphan(id)

    /** Stands in for "no meeting" when something that needs an event (the recorder) is used from the Notes tab. */
    val FREE_NOTE = CalEvent("", "", "", "Voice note", Source.Other, "Not linked to a meeting")

    /** A meeting that exists only as shared notes (the receiver's calendar has no copy): built from the notes' title and time. */
    private fun orphan(id: String): CalEvent? {
        val n = NoteStore.peek()?.notes?.firstOrNull { it.eventId == id } ?: return null
        return CalEvent(id, TimeFmt.stamp(n.createdAt).substringAfter(", "), "", n.title.ifBlank { "Shared notes" }, Source.Other, "Shared notes", startMs = n.createdAt, endMs = n.createdAt + 1)
    }

    fun report(text: String?) {
        message = text
    }

    // ---- This phone's calendars ----

    /**
     * Reads the phone's own calendars on a background thread (the permission must already be granted).
     * A refresh already in flight is left to finish rather than queued behind itself.
     */
    fun refreshDevice(context: Context) {
        if (!DeviceCalendar.hasPermission(context)) return
        if (deviceJob?.isActive == true) return
        val app = context.applicationContext
        deviceJob = ioScope.launch {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val (from, to) = window(now, zone)
            val result = runCatching { DeviceCalendar.query(app, from, to, zone) }
            deviceCals = DeviceCalendar.calendars(app)
            applyDefaults(app)
            result.onSuccess { list ->
                deviceEvents = list
                deviceOn = true
                lastSyncMs = now
                message = null
                prefs(app).edit().putBoolean("device_on", true).putLong("last_sync", now).apply()
                Reminders.scheduleAsync(app, force = false)
            }.onFailure {
                message = "Couldn't read the phone's calendars."
            }
        }
    }

    /** The first time calendars are seen, switch off the noisy ones (holidays, birthdays). Never touches later choices. */
    private fun applyDefaults(context: Context) {
        val p = prefs(context)
        if (p.getBoolean("defaults_done", false) || deviceCals.isEmpty()) return
        val extra = AccountFilter.defaultHidden(deviceCals)
        if (extra.isNotEmpty()) {
            hidden = hidden + extra
            p.edit().putStringSet("hidden_cals", hidden.map { it.toString() }.toSet()).apply()
        }
        p.edit().putBoolean("defaults_done", true).apply()
    }

    val accountsChosen: Boolean get() = enabledAccounts != null

    fun accountEnabled(name: String): Boolean = enabledAccounts?.contains(name) ?: true

    fun setAccountEnabled(context: Context, name: String, on: Boolean) {
        val all = AccountFilter.accounts(deviceCals).map { it.name }.toSet()
        val current = enabledAccounts ?: all
        setAccounts(context, if (on) current + name else current - name)
    }

    fun setAccounts(context: Context, names: Set<String>?) {
        enabledAccounts = names
        val e = prefs(context).edit()
        if (names == null) e.remove("enabled_accounts") else e.putStringSet("enabled_accounts", names)
        e.apply()
    }

    fun setCalendarVisible(context: Context, calId: Long, visible: Boolean) {
        hidden = if (visible) hidden - calId else hidden + calId
        prefs(context).edit().putStringSet("hidden_cals", hidden.map { it.toString() }.toSet()).apply()
    }

    fun setDestination(context: Context, calId: Long) {
        destinationId = calId
        prefs(context).edit().putLong("dest_cal", calId).apply()
    }

    fun setSyncNotes(context: Context, on: Boolean) {
        syncNotes = on
        prefs(context).edit().putBoolean("sync_notes", on).apply()
    }

    /** The calendar new meetings go to: the chosen one if it still exists, else the first writable account calendar. */
    fun destination(): DeviceCalendar.DeviceCal? =
        deviceCals.firstOrNull { it.id == destinationId && it.writable && accountEnabled(it.account) }
            ?: deviceCals.firstOrNull { it.writable && it.primary }
            ?: deviceCals.firstOrNull { it.writable && it.accountType != "LOCAL" }
            ?: deviceCals.firstOrNull { it.writable }

    fun disconnectDevice(context: Context) {
        deviceOn = false
        deviceEvents = emptyList()
        prefs(context).edit().putBoolean("device_on", false).remove("mode").apply()
    }

    // ---- Google, read directly ----

    suspend fun sync(context: Context, accessToken: String) {
        syncing = true
        message = null
        try {
            val now = System.currentTimeMillis()
            val (from, to) = window(now)
            val fetched = withContext(Dispatchers.IO) {
                GoogleCalendarClient(accessToken).fetch(from, to, ZoneId.systemDefault())
            }
            googleEvents = fetched
            googleOn = true
            lastSyncMs = now
            prefs(context).edit().putBoolean("google_on", true).putLong("last_sync", now).apply()
            withContext(Dispatchers.IO) { runCatching { file(context, "events_cache.json").writeText(CalEventJson.encode(fetched)) } }
        } catch (e: GoogleApiException) {
            message = when (e.code) {
                401 -> "Google signed you out. Tap sync to sign in again."
                403 -> "Google Calendar access is switched off for this app. Enable the Calendar API in Google Cloud."
                else -> e.message
            }
        } catch (e: java.io.IOException) {
            message = "Couldn't reach Google. Showing your last synced events."
        } finally {
            syncing = false
        }
    }

    fun disconnectGoogle(context: Context) {
        googleOn = false
        googleEvents = emptyList()
        prefs(context).edit().putBoolean("google_on", false).remove("google_connected").apply()
        file(context, "events_cache.json").delete()
    }

    // ---- Calendars added by link ----

    /** Adds a calendar link. Returns an error message to show, or null on success. */
    suspend fun addFeed(context: Context, rawUrl: String): String? {
        val url = Feeds.normalize(rawUrl) ?: return "That doesn't look like a calendar link. It should start with https:// or webcal://."
        if (feeds.any { it.url == url }) return "That calendar is already added."
        syncing = true
        try {
            val (name, events) = withContext(Dispatchers.IO) { fetchFeed(url) }
            val feed = Feed(url, name ?: Feeds.fallbackName(url), Feeds.sourceFor(url))
            feeds = feeds + feed
            feedEvents = feedEvents + (url to events)
            lastSyncMs = System.currentTimeMillis()
            persistFeeds(context)
            return null
        } catch (e: FeedException) {
            return e.message
        } catch (e: java.io.IOException) {
            return "Couldn't reach that link. Check it and your connection."
        } finally {
            syncing = false
        }
    }

    /** Imports a calendar file (.ics). It is kept on the phone and shown like any other calendar. */
    suspend fun importFile(context: Context, fileName: String, text: String): String? {
        if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) return "That file isn't a calendar (.ics) file."
        val (from, to) = window(System.currentTimeMillis())
        val key = "file://" + fileName
        val result = withContext(Dispatchers.Default) {
            IcsParser.parse(text, Source.Other, ZoneId.systemDefault(), from, to, fileName.substringBeforeLast('.'))
        }
        feeds = feeds.filter { it.url != key } + Feed(key, result.name ?: fileName.substringBeforeLast('.'), Source.Other)
        feedEvents = feedEvents + (key to result.events)
        lastSyncMs = System.currentTimeMillis()
        persistFeeds(context)
        return if (result.events.isEmpty()) "Imported, but no events fall in the next six months." else null
    }

    /** Adds calendar links from a backup that this phone does not have yet. Their events load on the next refresh. */
    fun adoptFeeds(context: Context, incoming: List<Feed>): Int {
        val fresh = incoming.filter { f -> feeds.none { it.url == f.url } }
        if (fresh.isEmpty()) return 0
        feeds = feeds + fresh
        persistFeeds(context)
        ioScope.launch { refreshFeeds(context) }
        return fresh.size
    }

    fun removeFeed(context: Context, feed: Feed) {
        feeds = feeds.filter { it.url != feed.url }
        feedEvents = feedEvents - feed.url
        persistFeeds(context)
    }

    /** Re-downloads every link. A feed that fails keeps its last good events. */
    suspend fun refreshFeeds(context: Context) {
        if (feeds.isEmpty()) return
        syncing = true
        try {
            var failed = 0
            val updated = feedEvents.toMutableMap()
            for (feed in feeds) {
                if (feed.url.startsWith("file://")) continue
                try {
                    updated[feed.url] = withContext(Dispatchers.IO) { fetchFeed(feed.url).second }
                } catch (e: Exception) {
                    failed++
                }
            }
            feedEvents = updated
            lastSyncMs = System.currentTimeMillis()
            message = if (failed > 0) "$failed calendar link${if (failed > 1) "s" else ""} couldn't be refreshed. Showing the last copy." else null
            persistFeeds(context)
        } finally {
            syncing = false
        }
    }

    private fun fetchFeed(url: String): Pair<String?, List<CalEvent>> {
        val text = IcsClient.fetch(url)
        if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) throw FeedException("That link isn't a calendar feed.")
        val (from, to) = window(System.currentTimeMillis())
        val feedSource = Feeds.sourceFor(url)
        val result = IcsParser.parse(text, feedSource, ZoneId.systemDefault(), from, to, Feeds.fallbackName(url))
        return result.name to result.events
    }

    private fun persistFeeds(context: Context) {
        prefs(context).edit().putString("feeds", Feeds.encode(feeds)).putLong("last_sync", lastSyncMs).apply()
        runCatching {
            val o = JSONObject()
            feedEvents.forEach { (url, list) -> o.put(url, CalEventJson.encode(list)) }
            file(context, "feeds_cache.json").writeText(o.toString())
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("plait", Context.MODE_PRIVATE)
    private fun file(context: Context, name: String) = File(context.filesDir, name)
    private const val DAY = 24L * 3600 * 1000
}
