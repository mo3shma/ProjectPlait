package app.plait.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.plait.ui.glass.LocalGlassEnv
import app.plait.ui.theme.LocalIsDark
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait

/** The corner of every pop-up: Material's extra-large dialog shape, or the rounder glass one. */
@Composable
fun popupShape(): Shape = RoundedCornerShape(if (LocalGlassEnv.current.material) 28.dp else 34.dp)

/**
 * The surface of every pop-up, menu and sheet, in the style the user chose. Liquid Glass: a solid, readable panel
 * that is lit from the top and has the same light rim as the glass controls. Material 3 Expressive: the tonal
 * high container, as Material dialogs use.
 */
@Composable
fun Modifier.popupSurface(shape: Shape = popupShape()): Modifier {
    if (LocalGlassEnv.current.material) return this.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    val dark = LocalIsDark.current
    val base = plait.surfaceHigh
    val top = lerp(base, Color.White, if (dark) 0.06f else 0.5f)
    val rim = Brush.linearGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.42f else 0.95f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = if (dark) 0.22f else 0.6f)),
        start = Offset.Zero, end = Offset(600f, 600f),
    )
    return this
        .shadow(28.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(top, base)))
        .drawBehind { drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.08f else 0.3f), Color.Transparent), endY = 140f)) }
        .border(1.dp, rim, shape)
}

/**
 * One action in a pop-up menu, drawn as a full-width button of the chosen style: a glass pill like Join and Record,
 * or a Material tonal button. A destructive action is red in both.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MenuAction(label: String, icon: ImageVector, onClick: () -> Unit, danger: Boolean = false, modifier: Modifier = Modifier) {
    if (LocalGlassEnv.current.material) {
        val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(12.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        val m = modifier.fillMaxWidth().heightIn(min = 56.dp)
        val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp)
        if (danger) Button(
            onClick = onClick, modifier = m, shapes = ButtonDefaults.shapes(), contentPadding = pad,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            content = content,
        ) else FilledTonalButton(onClick = onClick, modifier = m, shapes = ButtonDefaults.shapes(), contentPadding = pad, content = content)
        return
    }
    val ink = if (danger) Color(0xFF0E1316) else plait.text
    Row(
        modifier.fillMaxWidth().height(56.dp)
            .fauxGlass(CircleShape, if (danger) lerp(plait.surfaceHigh, plait.signal, 0.72f) else Color.Unspecified)
            .pressable(onClick, lift = 2.dp)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
        Text(label, style = PlaitType.bodyStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
