package app.plait.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import app.plait.ui.theme.mContainerHigh
import app.plait.ui.theme.mPrimaryContainer

enum class ButtonStyle {
    Primary, Secondary, Quiet, Danger,
    /** Dark button for use on the pastel home cards. */
    Ink,
    /** Soft dark-tinted button for use on the pastel home cards. */
    InkSoft,
}

private val CardInk = androidx.compose.ui.graphics.Color(0xFF0E1316)

/** Two sizes only, so buttons look the same everywhere. */
enum class ButtonSize(val minHeight: Dp, val hPad: Dp, val vPad: Dp, val text: TextUnit, val icon: Dp) {
    Regular(48.dp, 22.dp, 12.dp, 15.sp, 20.dp),
    Small(36.dp, 16.dp, 8.dp, 13.sp, 16.dp),
}

/**
 * The one button, in Liquid Glass. It is as wide as its label plus padding (never stretched to the container),
 * at least [ButtonSize.minHeight] tall, and a long label wraps in balanced lines instead of being cut off or
 * leaving a lone word. It dips, lights up and ticks when pressed.
 */
@Composable
fun PlaitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Secondary,
    size: ButtonSize = ButtonSize.Regular,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    if (app.plait.ui.glass.LocalGlassEnv.current.material) {
        app.plait.ui.material.MButton(label, onClick, modifier, style, size, icon, enabled)
        return
    }
    val shape = RoundedCornerShape(size.minHeight / 2)
    val cardInk = app.plait.ui.screens.LocalCardInk.current
    val (tint, fg) = when (style) {
        ButtonStyle.Primary -> plait.accent.copy(alpha = 0.92f) to plait.onAccent
        ButtonStyle.Secondary, ButtonStyle.Quiet -> Color.Unspecified to plait.text
        ButtonStyle.Danger -> plait.signal.copy(alpha = 0.92f) to Color.White
        ButtonStyle.Ink -> cardInk.copy(alpha = 0.9f) to (if (cardInk.luminance() > 0.5f) CardInk else Color(0xFFF7F3EA))
        ButtonStyle.InkSoft -> Color.White.copy(alpha = 0.42f) to cardInk
    }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = size.minHeight, minWidth = size.minHeight * 1.6f)
            .fauxGlass(shape, tint, strength = if (style == ButtonStyle.Quiet) 0.6f else 1f)
            .pressable({ if (enabled) onClick() })
            .padding(horizontal = size.hPad, vertical = size.vPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(size.icon))
        Text(
            label,
            style = PlaitType.label.copy(
                fontSize = size.text, lineHeight = size.text * 1.25f, fontWeight = FontWeight.Bold,
                lineBreak = LineBreak.Heading,
            ),
            color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/**
 * Delete that cannot be hit by accident and always reads the same: a small trash button; the first tap turns it
 * into a red "Delete" that must be tapped again, and it goes back by itself after three seconds.
 */
@Composable
fun ConfirmDeleteButton(onConfirm: () -> Unit, modifier: Modifier = Modifier, label: String = "Delete") {
    var armed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(armed) {
        if (armed) { kotlinx.coroutines.delay(3000); armed = false }
    }
    PlaitButton(
        if (armed) label else "",
        { if (armed) { armed = false; onConfirm() } else armed = true },
        modifier = modifier,
        style = if (armed) ButtonStyle.Danger else ButtonStyle.Quiet,
        size = ButtonSize.Small,
        icon = Icons.Filled.Delete,
    )
}

/** A selectable pill (filters, durations, days). Same height and type as a small button; the chosen one is solid. */
@Composable
fun PlaitChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (app.plait.ui.glass.LocalGlassEnv.current.material) {
        app.plait.ui.material.MChip(label, on, onClick, modifier)
        return
    }
    val shape = RoundedCornerShape(ButtonSize.Small.minHeight / 2)
    val material = app.plait.ui.glass.LocalGlassEnv.current.material
    Row(
        modifier
            .defaultMinSize(minHeight = ButtonSize.Small.minHeight)
            .then(if (material) Modifier.clip(shape).background(if (on) plait.mPrimaryContainer else plait.mContainerHigh) else if (on) Modifier.clip(shape).background(plait.text) else Modifier.fauxGlass(shape, strength = 0.8f))
            .pressable(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = PlaitType.label.copy(fontSize = 13.sp, lineHeight = 16.sp, lineBreak = LineBreak.Heading), color = if (on && !material) plait.ground else plait.text,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}
