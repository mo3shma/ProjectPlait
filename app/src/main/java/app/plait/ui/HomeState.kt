package app.plait.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.plait.ui.screens.CalendarState
import java.time.LocalDate

/**
 * Where the user was on the home screen. It lives for the whole process, so coming Back from a
 * meeting, the hub or a recording returns to the same tab, scroll position and calendar day instead
 * of starting again at the top of Today.
 */
object HomeState {
    var tab by mutableIntStateOf(0)
    val briefList = LazyListState()
    val notesList = LazyListState()
    var showEarlier by mutableStateOf(false)

    /** Which settings sections are open. */
    val hubOpen = androidx.compose.runtime.mutableStateListOf<String>()
    /** The settings page that is open (null is the list of settings). */
    var hubSection by mutableStateOf<String?>(null)

    /** Which alphabet photos are read as. */
    var ocrLang by mutableStateOf(app.plait.data.memo.OcrLang.Auto)

    /** The card last open on the home screen, and the part of the day it was chosen in. */
    var page by mutableIntStateOf(-1)
    var pagePhase by mutableIntStateOf(-1)

    private var calendarState: CalendarState? = null
    fun calendar(today: LocalDate): CalendarState = calendarState ?: CalendarState(today).also { calendarState = it }

    /** The on-device AI's version of today's summary, keyed so it is dropped when the day changes. */
    var aiDayKey by mutableStateOf("")
    var aiDayText by mutableStateOf<String?>(null)
    var aiDayBy by mutableStateOf<String?>(null)

    /** Meetings whose prep has already been tried this session, so a failed AI call is not repeated in a loop. */
    val prepTried = HashSet<String>()

    /** Bumped by the refresh button: everything the AI wrote is written again from fresh data. */
    var refreshTick by mutableIntStateOf(0)
}
