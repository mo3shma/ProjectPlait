package app.plait.data

enum class Source(val label: String) { Google("Google"), Outlook("Outlook"), ICloud("iCloud"), Other("Phone") }

data class Person(val initials: String, val name: String, val role: String, val rsvp: String, val source: Source)

data class TranscriptLine(val time: String, val who: String, val text: String, val mine: Boolean = false)

enum class Rsvp { Accepted, Declined, Tentative, Pending }

/** Someone invited to a meeting, as the calendar reports them. */
data class Guest(
    val name: String,
    val email: String? = null,
    val rsvp: Rsvp = Rsvp.Pending,
    val organizer: Boolean = false,
    val self: Boolean = false,
    val optional: Boolean = false,
) {
    /** A readable name even when the invite only carried an address. */
    val label: String
        get() = name.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.split('.', '_', '-')?.filter { it.isNotBlank() }
                ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            ?: "Guest"

    val initials: String
        get() = label.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }

    val domain: String? get() = email?.substringAfter('@', "")?.lowercase()?.takeIf { it.isNotBlank() }
}

data class CalEvent(
    val id: String,
    /** Start as shown to the user, e.g. "10:30 AM", or "All day". */
    val time: String,
    val duration: String,
    val title: String,
    val source: Source,
    val place: String,
    val location: String? = null,
    val description: String? = null,
    /** Video-call URL supplied by the calendar itself (Meet hangout link, conference entry point). */
    val conferenceUri: String? = null,
    val minutesAway: Int = 0,
    val endLabel: String = "",
    /** Epoch millis. Zero for built-in sample events. */
    val startMs: Long = 0,
    val endMs: Long = 0,
    val allDay: Boolean = false,
    /** Everyone invited, organizer included (flagged). Empty when the calendar didn't say. */
    val guests: List<Guest> = emptyList(),
    /** Part of a repeating series, so earlier notes from the same series are relevant. */
    val recurring: Boolean = false,
    /** The phone calendar this came from, so it can be switched off in the hub. Zero for other sources. */
    val calId: Long = 0L,
    /** The account that owns the calendar ("me@work.com"), when the phone says. */
    val account: String = "",
) {
    /** Detected from the event's own fields, never typed in by hand. */
    val callLink: CallLink? get() = CallLinks.find(conferenceUri, location, description)
}

data class VoiceNote(val eventId: String, val title: String, val duration: String, val whenLabel: String, val snippet: String)

data class DayLoad(val day: String, val date: String, val count: Int, val today: Boolean = false)
