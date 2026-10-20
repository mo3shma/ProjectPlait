@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package app.plait.ui.material

import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.RowScope as RowScopeAlias
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.plait.ui.ButtonSize
import app.plait.ui.ButtonStyle
import app.plait.ui.glass.GlassMenuAction
import app.plait.ui.glass.GlassTab
import app.plait.ui.theme.OneSans

/** The whole app under Material 3 Expressive: the phone's colours, the expressive spring motion, and One Sans for every style. */
@Composable
fun ExpressiveTheme(scheme: ColorScheme, content: @Composable () -> Unit) {
    val t = Typography()
    val typography = t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = OneSans), displayMedium = t.displayMedium.copy(fontFamily = OneSans),
        displaySmall = t.displaySmall.copy(fontFamily = OneSans), headlineLarge = t.headlineLarge.copy(fontFamily = OneSans),
        headlineMedium = t.headlineMedium.copy(fontFamily = OneSans), headlineSmall = t.headlineSmall.copy(fontFamily = OneSans),
        titleLarge = t.titleLarge.copy(fontFamily = OneSans), titleMedium = t.titleMedium.copy(fontFamily = OneSans),
        titleSmall = t.titleSmall.copy(fontFamily = OneSans), bodyLarge = t.bodyLarge.copy(fontFamily = OneSans),
        bodyMedium = t.bodyMedium.copy(fontFamily = OneSans), bodySmall = t.bodySmall.copy(fontFamily = OneSans),
        labelLarge = t.labelLarge.copy(fontFamily = OneSans), labelMedium = t.labelMedium.copy(fontFamily = OneSans),
        labelSmall = t.labelSmall.copy(fontFamily = OneSans),
    )
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = typography, content = content)
}

/** The one Material button, used wherever the app has a labelled button. */
@Composable
fun MButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: ButtonStyle = ButtonStyle.Secondary,
    size: ButtonSize = ButtonSize.Regular, icon: ImageVector? = null, enabled: Boolean = true,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        if (label.isNotEmpty()) Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val m = modifier.heightIn(min = size.minHeight)
    val pad = if (label.isEmpty()) PaddingValues(horizontal = 12.dp) else ButtonDefaults.ContentPadding
    when (style) {
        ButtonStyle.Primary -> Button(onClick = onClick, modifier = m, enabled = enabled, shapes = ButtonDefaults.shapes(), contentPadding = pad, content = content)
        // On a home card the buttons take the card's own ink, so they always stand out from the card's container colour.
        ButtonStyle.Ink -> {
            val ink = app.plait.ui.screens.LocalCardInk.current
            Button(
                onClick = onClick, modifier = m, enabled = enabled, shapes = ButtonDefaults.shapes(), contentPadding = pad,
                colors = ButtonDefaults.buttonColors(containerColor = ink, contentColor = if (ink.luminance() > 0.5f) Color(0xFF15131A) else Color(0xFFF7F3EA)), content = content,
            )
        }
        ButtonStyle.InkSoft -> {
            val ink = app.plait.ui.screens.LocalCardInk.current
            OutlinedButton(
                onClick = onClick, modifier = m, enabled = enabled, shapes = ButtonDefaults.shapes(), contentPadding = pad,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = ink.copy(alpha = 0.14f), contentColor = ink),
                border = androidx.compose.foundation.BorderStroke(1.dp, ink.copy(alpha = 0.5f)), content = content,
            )
        }
        ButtonStyle.Danger -> Button(
            onClick = onClick, modifier = m, enabled = enabled, shapes = ButtonDefaults.shapes(), contentPadding = pad,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError), content = content,
        )
        ButtonStyle.Secondary -> FilledTonalButton(onClick = onClick, modifier = m, enabled = enabled, shapes = ButtonDefaults.shapes(), contentPadding = pad, content = content)
        ButtonStyle.Quiet -> TextButton(onClick = onClick, modifier = m, enabled = enabled, shapes = ButtonDefaults.shapes(), contentPadding = pad, content = content)
    }
}

/** A labelled pill with an optional strong colour: the Join / Record / reply buttons. */
@Composable
fun MPill(label: String, onClick: () -> Unit, modifier: Modifier, icon: ImageVector?, tint: Color, contentColor: Color, height: Dp) {
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
    val m = modifier.heightIn(min = height)
    if (tint.isSpecified) Button(
        onClick = onClick, modifier = m, shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.buttonColors(containerColor = tint.copy(alpha = 1f), contentColor = contentColor), content = content,
    ) else FilledTonalButton(onClick = onClick, modifier = m, shapes = ButtonDefaults.shapes(), content = content)
}

/** A round icon button; filled with [tint] when one is given (the red record button), otherwise tonal. */
@Composable
fun MIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier, size: Dp, iconSize: Dp, tint: Color, iconTint: Color, spinDegrees: Float = 0f) {
    val m = modifier.size(size).semantics { contentDescription = description }
    val body: @Composable () -> Unit = { Icon(icon, null, Modifier.size(iconSize).graphicsLayer { rotationZ = spinDegrees }) }
    if (tint.isSpecified) FilledIconButton(
        onClick = onClick, modifier = m, shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = tint.copy(alpha = 1f), contentColor = iconTint), content = body,
    ) else FilledTonalIconButton(onClick = onClick, modifier = m, shapes = IconButtonDefaults.shapes(), content = body)
}

/** The tab switch: Material's navigation bar when the tabs have icons, a segmented button row otherwise. */
@Composable
fun MTabs(tabs: List<GlassTab>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier, height: Dp) {
    if (tabs.any { it.icon != null }) {
        ShortNavigationBar(modifier.clip(androidx.compose.foundation.shape.CircleShape), windowInsets = WindowInsets(0)) {
            tabs.forEachIndexed { i, tab ->
                ShortNavigationBarItem(
                    selected = selected == i, onClick = { onSelect(i) },
                    icon = { tab.icon?.let { Icon(it, null) } }, label = { Text(tab.label, maxLines = 1) },
                )
            }
        }
    } else {
        // Round toggle pills side by side; the chosen one is filled and they morph as they are pressed.
        androidx.compose.foundation.layout.Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEachIndexed { i, tab ->
                androidx.compose.material3.ToggleButton(
                    checked = selected == i, onCheckedChange = { onSelect(i) },
                    modifier = Modifier.weight(1f).heightIn(min = height),
                    shapes = androidx.compose.material3.ToggleButtonDefaults.shapes(),
                ) { Text(tab.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** The "+" button: Material's floating action button menu with the same choices. */
@Composable
fun MFabMenu(expanded: Boolean, onExpandedChange: (Boolean) -> Unit, actions: List<GlassMenuAction>, modifier: Modifier) {
    FloatingActionButtonMenu(
        expanded = expanded, modifier = modifier,
        button = {
            ToggleFloatingActionButton(checked = expanded, onCheckedChange = onExpandedChange) {
                Icon(if (expanded) Icons.Filled.Close else Icons.Filled.Add, if (expanded) "Close menu" else "Record or add")
            }
        },
    ) {
        actions.forEach { a ->
            FloatingActionButtonMenuItem(
                onClick = { onExpandedChange(false); a.onClick() },
                text = { Text(a.label) },
                icon = { Icon(a.icon, null) },
            )
        }
    }
}

/** A tonal filter chip. */
@Composable
fun MChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    FilterChip(selected = on, onClick = onClick, label = { Text(label, maxLines = 1) }, modifier = modifier, shape = androidx.compose.foundation.shape.CircleShape)
}

@Composable
fun MSwitch(on: Boolean, onToggle: () -> Unit, label: String) {
    androidx.compose.material3.Switch(checked = on, onCheckedChange = { onToggle() }, modifier = Modifier.semantics { contentDescription = label })
}

/** Starts the app again so a different style is built from scratch. */
fun restartApp(context: android.content.Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(launch)
    (context as? android.app.Activity)?.finishAffinity()
    Runtime.getRuntime().exit(0)
}
