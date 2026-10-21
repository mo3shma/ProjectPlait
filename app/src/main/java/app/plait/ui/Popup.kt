package app.plait.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.plait.ui.glass.LocalGlassEnv

/** The corner of every pop-up: 28 dp in both styles. */
@Composable
fun popupShape(): Shape = RoundedCornerShape(28.dp)

/**
 * The surface of every pop-up, menu and sheet, in the style the user chose: real glass over the app when it is drawn by the
 * overlay layer ([LocalPopupBackdrop]) with glass effects on, a flat tinted panel with a rim otherwise, and the tonal
 * container in Material 3 Expressive. See [glassPanel].
 */
@Composable
fun Modifier.popupSurface(shape: Shape = popupShape()): Modifier = glassPanel(shape, LocalPopupBackdrop.current)

/**
 * One action in a pop-up menu: a 56 dp row with a tinted glass icon circle and an ink label (a Material tonal button in the
 * Material style). A destructive action is rose. [tone] picks the icon's colour.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MenuAction(label: String, icon: ImageVector, onClick: () -> Unit, danger: Boolean = false, modifier: Modifier = Modifier, tone: GlassTone = GlassTone.Lavender) {
    if (LocalGlassEnv.current.material) {
        val content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
            Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(12.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        val m = modifier.fillMaxWidth().heightIn(min = ControlHeights.Large)
        val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp)
        if (danger) Button(
            onClick = onClick, modifier = m, shapes = ButtonDefaults.shapes(), contentPadding = pad,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            content = content,
        ) else FilledTonalButton(onClick = onClick, modifier = m, shapes = ButtonDefaults.shapes(), contentPadding = pad, content = content)
        return
    }
    MenuRow(label, icon, if (danger) GlassTone.Rose else tone, onClick, modifier.fillMaxWidth())
}
