package app.plait.ui.screens

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import app.plait.data.CalEvent
import app.plait.data.CallProvider
import app.plait.data.Source

/** The colours of the service a meeting runs on, so its page is tinted like that service. */
internal object ServicePalette {
    private fun c(v: Long) = Color(0xFF000000 or v)

    fun of(ev: CalEvent): List<Color> = when (ev.callLink?.provider) {
        // Teams violet, Microsoft blue and Xbox green
        CallProvider.Teams -> listOf(c(0x5B5FC7), c(0x0078D4), c(0x3FAE2A), c(0x7B83EB))
        CallProvider.Meet -> listOf(c(0x1A73E8), c(0x34A853), c(0xFBBC04), c(0xEA4335))
        CallProvider.Zoom -> listOf(c(0x2D8CFF), c(0x0B5CFF), c(0x6BB1FF), c(0x1A4FBF))
        CallProvider.Webex -> listOf(c(0x00A0D1), c(0x07C1E4), c(0x00799C), c(0x38D9F5))
        null -> when (ev.source) {
            Source.Google -> listOf(c(0x4285F4), c(0x34A853), c(0xFBBC04), c(0xEA4335))
            Source.Outlook -> listOf(c(0x0078D4), c(0x2B88D8), c(0x50A0E6), c(0x106EBE))
            Source.ICloud -> listOf(c(0xFF9F0A), c(0xFF375F), c(0xBF5AF2), c(0x30D158))
            Source.Other -> listOf(c(0x7B8CDE), c(0x5CC8B4), c(0xE79A6B), c(0xB48AE0))
        }
    }
}

/** What the cards on a meeting page need to draw themselves: the glass backdrop and the service colours. */
internal class MeetingLook(val backdrop: Backdrop?, val colors: List<Color>) {
    fun tint(i: Int): Color = colors[i % colors.size]
}

internal val LocalMeetingLook = compositionLocalOf<MeetingLook?> { null }

/** The glass pill used for actions on a meeting page: the same look as Join and Record. */
@androidx.compose.runtime.Composable
internal fun PillButton(label: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, primary: Boolean = false) {
    val p = app.plait.ui.theme.plait
    val bd = LocalMeetingLook.current?.backdrop
    if (bd != null) app.plait.ui.glass.GlassPillButton(
        label, onClick, bd, icon = icon,
        tint = if (primary) p.accent else Color.Unspecified, contentColor = if (primary) p.onAccent else p.text, height = 48.dp,
    ) else app.plait.ui.PlaitButton(label, onClick, size = app.plait.ui.ButtonSize.Small, style = if (primary) app.plait.ui.ButtonStyle.Primary else app.plait.ui.ButtonStyle.Secondary, icon = icon)
}
