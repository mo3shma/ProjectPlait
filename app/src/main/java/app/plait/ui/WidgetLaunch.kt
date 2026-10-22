package app.plait.ui

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What the 4x3 widget asks the app to start: a voice note, or a new text note / checklist in the Notes tab. One-shot, consumed after use. */
object WidgetLaunch {
    const val ACTION = "app.plait.QUICK_ADD"
    const val EXTRA_KIND = "kind"
    val KINDS = listOf("voice", "list", "text")

    /** Set by MainActivity, consumed by the navigation host. */
    var request by mutableStateOf<String?>(null)

    /** Set once the Notes tab is showing, consumed by the Notes tab's add card, which opens the matching editor. */
    var editor by mutableStateOf<String?>(null)

    /** Unknown or missing kinds are ignored. */
    fun parse(kind: String?): String? = kind?.takeIf { it in KINDS }

    fun from(intent: Intent?) {
        if (intent?.action != ACTION) return
        request = parse(intent.getStringExtra(EXTRA_KIND))
        intent.action = null // a recreated activity must not replay it
    }
}
