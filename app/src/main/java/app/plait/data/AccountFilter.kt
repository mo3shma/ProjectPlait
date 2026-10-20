package app.plait.data

import app.plait.data.device.DeviceCalendar

/** Decides which of the phone's events Plait shows, by account and by calendar. */
object AccountFilter {
    /**
     * [enabled] is the set of account names the user picked, or null before they have picked (everything shows).
     * [hiddenCalendars] holds calendars switched off one by one.
     */
    fun visible(events: List<CalEvent>, enabled: Set<String>?, hiddenCalendars: Set<Long>): List<CalEvent> =
        if (enabled == null && hiddenCalendars.isEmpty()) events
        else events.filter { it.calId !in hiddenCalendars && (enabled == null || it.account in enabled) }

    private val noise = Regex(
        """holiday|birthday|anniversar|contacts|week numbers|moon|sunrise|astronom|sports|tv programmes|public holidays|feiertag|jours f""",
        RegexOption.IGNORE_CASE,
    )

    /** Calendars that clutter a work day (public holidays, contact birthdays, moon phases) start switched off. */
    fun defaultHidden(cals: List<DeviceCalendar.DeviceCal>): Set<Long> =
        cals.filter { noise.containsMatchIn(it.name) }.map { it.id }.toSet()

    /** One row per account for the hub, in a stable order, with how many calendars each has. */
    data class Account(val name: String, val type: String, val calendars: List<DeviceCalendar.DeviceCal>) {
        val source: Source get() = calendars.first().source
    }

    fun accounts(cals: List<DeviceCalendar.DeviceCal>): List<Account> =
        cals.groupBy { it.account to it.accountType }
            .map { (k, v) -> Account(k.first.ifBlank { "This phone" }, k.second, v) }
            .sortedWith(compareBy({ it.type == "LOCAL" }, { it.name.lowercase() }))
}
