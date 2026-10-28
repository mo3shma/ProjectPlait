package app.plait.ui

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

enum class ButtonStyle {
    Primary, Secondary, Quiet, Danger,
    /** Dark button for use on the pastel home cards. */
    Ink,
    /** Soft dark-tinted button for use on the pastel home cards. */
    InkSoft,
    /** Tinted glass in the colour of the `tone` argument (Secondary is the same in lavender). */
    Tinted,
}

private val CardInk = androidx.compose.ui.graphics.Color(0xFF0E1316)

/** Three sizes, from [ControlHeights], so buttons, chips and icon buttons line up everywhere. */
enum class ButtonSize(val minHeight: Dp, val hPad: Dp, val vPad: Dp, val text: TextUnit, val icon: Dp) {
    Regular(ControlHeights.Medium, 22.dp, 12.dp, 15.sp, 20.dp),
    Small(ControlHeights.Small, 16.dp, 8.dp, 13.sp, 16.dp),
    Large(ControlHeights.Large, 26.dp, 14.dp, 16.sp, 22.dp),
}

/**
 * The one button. Primary, Ink and Danger are solid strong colour; Secondary, Tinted and InkSoft are tinted glass pills with
 * a crisp rim; Quiet is text with a glass highlight when pressed. It is as wide as its label plus padding (never stretched to
 * the container), at least [ButtonSize.minHeight] tall, and a long label wraps in balanced lines.
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
    tone: GlassTone = GlassTone.Lavender,
) {
    val env = app.plait.ui.glass.LocalGlassEnv.current
    if (env.material) {
        app.plait.ui.material.MButton(label, onClick, modifier, style, size, icon, enabled, tone)
        return
    }
    val shape = RoundedCornerShape(size.minHeight / 2)
    val cardInk = app.plait.ui.screens.LocalCardInk.current
    val dark = app.plait.ui.theme.LocalIsDark.current
    val rimK = if (env.solid) ToneStyles.FLAT_RIM else 1f
    val strong: Color? = when (style) {
        ButtonStyle.Primary -> plait.accent.copy(alpha = 0.92f)
        ButtonStyle.Danger -> plait.signal.copy(alpha = 0.92f)
        ButtonStyle.Ink -> cardInk.copy(alpha = 0.9f)
        else -> null
    }
    val soft: ToneStyle? = when (style) {
        ButtonStyle.Secondary, ButtonStyle.Tinted -> ToneStyles.resolve(tone, dark, rimStrength = rimK)
        ButtonStyle.InkSoft -> ToneStyle(cardInk.copy(alpha = 0.14f), cardInk.copy(alpha = 0.5f * rimK), cardInk)
        else -> null
    }
    val fg = when (style) {
        ButtonStyle.Primary -> plait.onAccent
        ButtonStyle.Danger -> Color.White
        ButtonStyle.Ink -> if (cardInk.luminance() > 0.5f) CardInk else Color(0xFFF7F3EA)
        ButtonStyle.InkSoft -> cardInk
        else -> plait.text
    }
    val body = when {
        strong != null -> Modifier.fauxGlass(shape, strong)
        soft != null -> Modifier.toneGlass(shape, soft)
        else -> Modifier.clip(shape) // Quiet: text only, the press light comes from pressable
    }
    Row(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = size.minHeight, minWidth = size.minHeight * 1.6f)
            .then(body)
            .pressable({ if (enabled) onClick() })
            .padding(horizontal = size.hPad, vertical = size.vPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(size.icon))
        if (label.isNotEmpty()) Text(
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
fun ConfirmDeleteButton(onConfirm: () -> Unit, modifier: Modifier = Modifier, label: String = androidx.compose.ui.res.stringResource(app.plait.R.string.common_delete)) {
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

/** A selectable pill (filters, durations, days): a small tinted glass pill; the chosen one has a stronger tint and rim. */
@Composable
fun PlaitChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, tone: GlassTone = GlassTone.Lavender) {
    val env = app.plait.ui.glass.LocalGlassEnv.current
    if (env.material) {
        app.plait.ui.material.MChip(label, on, onClick, modifier)
        return
    }
    val style = ToneStyles.resolve(tone, app.plait.ui.theme.LocalIsDark.current, on, if (env.solid) ToneStyles.FLAT_RIM else 1f)
    Row(
        modifier
            .defaultMinSize(minHeight = ControlHeights.Small)
            .toneGlass(RoundedCornerShape(ControlHeights.Small / 2), style)
            .pressable(onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label, style = PlaitType.label.copy(fontSize = 13.sp, lineHeight = 16.sp, lineBreak = LineBreak.Heading, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold),
            color = style.ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}
