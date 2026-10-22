package app.plait.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.isSpecified
import app.plait.ui.GlassTone
import app.plait.ui.ToneStyles
import app.plait.ui.fauxGlass
import app.plait.ui.glassPanel
import app.plait.ui.pressable
import app.plait.ui.toneGlass
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import app.plait.ui.theme.LocalIsDark
import app.plait.ui.theme.PlaitType
import app.plait.ui.theme.plait
import app.plait.ui.theme.mContainer
import app.plait.ui.theme.mPrimaryContainer
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/** Glass effects off: a solid strong colour stays solid, anything else is a tinted glass body with a quieter rim. */
@Composable
private fun Modifier.flatControl(shape: androidx.compose.ui.graphics.Shape, tint: Color): Modifier =
    if (tint.isSpecified && tint.alpha >= 0.6f) fauxGlass(shape, tint)
    else if (tint.isSpecified) toneGlass(shape, ToneStyles.fromTint(tint, LocalIsDark.current, app.plait.ui.ToneStyles.FLAT_RIM))
    else toneGlass(shape, GlassTone.Lavender)

/** A round, interactive glass control for toolbars. Always give it a [description]. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = app.plait.ui.ControlHeights.IconButton,
    iconSize: Dp = 22.dp,
    variant: GlassVariant = GlassVariant.Regular,
    tint: Color = Color.Unspecified,
    iconTint: Color = plait.text,
    spinning: Boolean = false,
) {
    if (LocalGlassEnv.current.material) {
        val t = rememberInfiniteTransition(label = "spin")
        val deg = if (spinning) t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "deg").value else 0f
        app.plait.ui.material.MIconButton(icon, description, onClick, modifier, size, iconSize, tint, iconTint, deg)
        return
    }
    val flat = LocalGlassEnv.current.solid
    Box(
        modifier
            .size(size)
            .then(
                if (flat) Modifier.flatControl(CircleShape, tint).pressable(onClick)
                else Modifier.liquidGlass(backdrop, variant = variant, tint = tint, interactive = true)
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (spinning && !LocalGlassEnv.current.reduceMotion) {
            val t = rememberInfiniteTransition(label = "spin")
            val degrees = t.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "deg")
            Icon(icon, contentDescription = description, tint = iconTint, modifier = Modifier.size(iconSize).graphicsLayer { rotationZ = degrees.value })
        } else {
            Icon(icon, contentDescription = description, tint = iconTint, modifier = Modifier.size(iconSize))
        }
    }
}

/** A capsule glass button with a label, optionally tinted to mark the primary action. */
@Composable
fun GlassPillButton(
    label: String,
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Color.Unspecified,
    contentColor: Color = plait.text,
    height: Dp = app.plait.ui.ControlHeights.Medium,
    variant: GlassVariant = GlassVariant.Regular,
) {
    if (LocalGlassEnv.current.material) {
        app.plait.ui.material.MPill(label, onClick, modifier, icon, tint, contentColor, height)
        return
    }
    val flat = LocalGlassEnv.current.solid
    Row(
        modifier
            .then(
                if (flat) Modifier.flatControl(androidx.compose.foundation.shape.RoundedCornerShape(height / 2), tint).pressable(onClick)
                else Modifier.liquidGlass(backdrop, variant = variant, tint = tint, interactive = true)
                    .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick),
            )
            .height(height)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = contentColor, modifier = Modifier.size(22.dp))
        Text(label, style = PlaitType.bodyStrong, color = contentColor, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

data class GlassTab(val label: String, val icon: ImageVector? = null)

enum class TabContainer {
    /** A tab bar: the whole bar is glass and floats above content. */
    Glass,

    /** A segmented control in the content layer: only the knob turns to glass, while touched. */
    Solid,
}

/**
 * Tab bar / segmented control. Apple's controls "transform into Liquid Glass during
 * interaction": at rest the selected knob is a quiet pill; under a finger it lifts, grows and
 * refracts what is beneath it, then springs to the tab nearest where you let go.
 */
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    container: TabContainer = TabContainer.Glass,
    height: Dp = app.plait.ui.ControlHeights.Bar,
    selectedColor: Color = plait.accent,
) {
    val env = LocalGlassEnv.current
    if (env.material) {
        app.plait.ui.material.MTabs(tabs, selected, onSelect, modifier, height)
        return
    }
    val dark = LocalIsDark.current
    val scope = rememberCoroutineScope()
    val barBackdrop = rememberLayerBackdrop()
    val thumb = remember { Animatable(selected.toFloat(), 0.001f) }
    val press = remember { Animatable(0f, 0.001f) }
    val currentOnSelect by rememberUpdatedState(onSelect)

    val settle = spring<Float>(dampingRatio = 0.72f, stiffness = 380f, visibilityThreshold = 0.001f)
    val follow = spring<Float>(dampingRatio = 0.9f, stiffness = 520f, visibilityThreshold = 0.001f)
    val lift = spring<Float>(dampingRatio = 0.6f, stiffness = 300f, visibilityThreshold = 0.001f)

    LaunchedEffect(selected) { thumb.animateTo(selected.toFloat(), settle) }

    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val density = LocalDensity.current
        val padPx = with(density) { 4.dp.toPx() }
        val tabW = (constraints.maxWidth - 2 * padPx) / tabs.size
        val lastIndex = (tabs.size - 1).toFloat()

        val gestures = Modifier.pointerInput(tabs.size, tabW) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                fun target(x: Float) = ((x - padPx) / tabW - 0.5f).coerceIn(0f, lastIndex)
                scope.launch { press.animateTo(1f, lift) }
                scope.launch { thumb.animateTo(target(down.position.x).roundToInt().toFloat(), settle) }
                var lastX = down.position.x
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null && change.pressed) {
                        lastX = change.position.x
                        scope.launch { thumb.animateTo(target(lastX), follow) }
                    }
                } while (event.changes.any { it.pressed })
                val index = target(lastX).roundToInt()
                scope.launch { press.animateTo(0f, lift) }
                scope.launch { thumb.animateTo(index.toFloat(), settle) }
                currentOnSelect(index)
            }
        }

        // Container
        val containerModifier = when (container) {
            TabContainer.Glass -> if (env.material) Modifier.layerBackdrop(barBackdrop).clip(Capsule()).background(plait.mContainer)
                else Modifier.liquidGlass(backdrop, exported = barBackdrop)
            TabContainer.Solid -> Modifier
                .layerBackdrop(barBackdrop)
                .clip(Capsule())
                .background(plait.surface)
        }
        Box(Modifier.fillMaxSize().then(containerModifier).then(gestures))

        // Knob
        val materialKnob = plait.mPrimaryContainer
        val restFill = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)
        Box(
            Modifier
                .offset { IntOffset((padPx + thumb.value * tabW).roundToInt(), padPx.roundToInt()) }
                .size(width = with(density) { tabW.toDp() }, height = height - 8.dp)
                .drawBackdrop(
                    backdrop = barBackdrop,
                    shape = { Capsule() },
                    effects = {
                        if (!env.solid) {
                            val p = press.value
                            lens(10.dp.toPx() * p, 14.dp.toPx() * p)
                        }
                    },
                    highlight = { Highlight.Default.copy(alpha = press.value.coerceIn(0f, 1f)) },
                    shadow = { Shadow(alpha = press.value.coerceIn(0f, 1f)) },
                    innerShadow = { InnerShadow(radius = 8.dp * press.value, alpha = press.value.coerceIn(0f, 1f)) },
                    layerBlock = {
                        val s = 1f + (14.dp.toPx() / size.width) * press.value
                        scaleX = s
                        scaleY = s
                    },
                    onDrawSurface = { if (env.material) drawRect(materialKnob) else drawRect(restFill, alpha = (1f - press.value).coerceIn(0f, 1f)) },
                )
        )

        // Labels, drawn above the knob. Each is sized to fit inside it, and swells and lifts as the knob arrives,
        // so the spring on the knob also makes the label bounce.
        val liveIndex = thumb.value.roundToInt().coerceIn(0, tabs.size - 1)
        val tabWidthDp = with(density) { tabW.toDp() }
        Row(
            Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { i, tab ->
                val color by animateColorAsState(if (i == liveIndex) (if (env.material) plait.text else selectedColor) else plait.muted, label = "tab-color")
                val base = if (tab.icon != null) 11f else 14f
                // about 0.56 em per character, and 20 dp of air so the text always sits well inside the highlight
                val fit = ((tabWidthDp.value - 20f) / (tab.label.length * 0.56f)).coerceIn(9f, base)
                Column(
                    Modifier.weight(1f).fillMaxSize().semantics { contentDescription = tab.label }
                        .graphicsLayer {
                            val near = (1f - kotlin.math.abs(thumb.value - i)).coerceIn(0f, 1f)
                            val sc = 1f + 0.12f * near
                            scaleX = sc; scaleY = sc
                            translationY = -2.dp.toPx() * near
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (tab.icon != null) {
                        Icon(tab.icon, null, tint = color, modifier = Modifier.size(22.dp))
                        Text(tab.label, style = PlaitType.caption.copy(fontSize = fit.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1, softWrap = false)
                    } else {
                        Text(tab.label, style = PlaitType.label.copy(fontSize = fit.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

data class GlassMenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The "+" button and its menu. The button is [collapsedSize] (the bar's height) and turns 45 degrees into an "x" while the menu
 * is open. The menu is a glass panel in the screen's own layer, above the scrim the screen draws: it blurs [backdrop] (the
 * content layer), grows out of the button's corner on a spring, and has one 56 dp row per action with a tinted icon.
 * Material 3 Expressive uses its own floating-action-button menu; glass effects off give a flat tinted panel.
 */
@Composable
fun GlassMorphMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    collapsedIcon: ImageVector,
    collapsedDescription: String,
    actions: List<GlassMenuAction>,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    collapsedSize: Dp = app.plait.ui.ControlHeights.Fab,
    expandedWidth: Dp = 280.dp,
    tint: Color = plait.signal,
    iconTint: Color = Color(0xFF0E1316),
) {
    if (LocalGlassEnv.current.material) {
        app.plait.ui.material.MFabMenu(expanded, onExpandedChange, actions, modifier)
        return
    }
    val t by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f, visibilityThreshold = 0.001f),
        label = "menu",
    )
    val env = LocalGlassEnv.current
    val dark = LocalIsDark.current
    val density = LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val viewConfig = androidx.compose.ui.platform.LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val curExpanded by rememberUpdatedState(expanded)
    val curToggle by rememberUpdatedState(onExpandedChange)
    val curActions by rememberUpdatedState(actions)
    // The highlight pill follows the finger on the same springs as the tab bar's knob; it moves in a draw lambda, so nothing recomposes per frame.
    val pillPos = remember { Animatable(0f, 0.001f) }
    val pillShow = remember { Animatable(0f, 0.001f) }
    val panelTop = remember { floatArrayOf(0f) }
    val follow = spring<Float>(dampingRatio = 0.9f, stiffness = 520f, visibilityThreshold = 0.001f)
    val lift = spring<Float>(dampingRatio = 0.6f, stiffness = 300f, visibilityThreshold = 0.001f)
    LaunchedEffect(expanded) { if (!expanded) pillShow.snapTo(0f) }
    fun pillTo(i: Int) {
        scope.launch {
            if (i < 0) { pillShow.animateTo(0f, lift); return@launch }
            if (pillShow.value < 0.05f) pillPos.snapTo(i.toFloat()) else launch { pillPos.animateTo(i.toFloat(), follow) }
            pillShow.animateTo(1f, lift)
        }
    }
    val rowPx = with(density) { app.plait.ui.ControlHeights.Large.toPx() }
    val padPx = with(density) { 8.dp.toPx() }
    val gestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            fun hit(y: Float) = menuIndexAt(y, panelTop[0] + padPx, rowPx, curActions.size)
            var heldOpen = false
            var dragging = false
            if (!curExpanded) {
                // Press and hold on the button opens the menu and starts a drag; a quick tap is left to the button's own click.
                val ended = withTimeoutOrNull(viewConfig.longPressTimeoutMillis) { waitForUpOrCancellation(PointerEventPass.Initial); true }
                if (ended != null) return@awaitEachGesture
                heldOpen = true; dragging = true
                curToggle(true)
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
            var hover = if (dragging) -1 else hit(down.position.y).also { if (it >= 0) pillTo(it) }
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!dragging && (c.position - down.position).getDistance() > viewConfig.touchSlop) dragging = true
                if (c.pressed) {
                    if (dragging) {
                        val h = hit(c.position.y)
                        if (h != hover) {
                            hover = h; pillTo(h)
                            if (h >= 0) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        }
                        c.consume()
                    }
                } else {
                    if (dragging) {
                        c.consume()
                        if (hover >= 0) { curToggle(false); curActions[hover].onClick() } else if (heldOpen) curToggle(false)
                    }
                    pillTo(-1)
                    break
                }
            }
        }
    }
    Column(modifier.then(gestures), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (t > 0.01f) {
            Column(
                Modifier
                    .width(expandedWidth)
                    .onGloballyPositioned { panelTop[0] = it.positionInParent().y }
                    .graphicsLayer {
                        val s = 0.7f + 0.3f * t
                        scaleX = s; scaleY = s
                        alpha = t.coerceIn(0f, 1f)
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                    }
                    .glassPanel(app.plait.ui.PanelShape, backdrop)
                    .padding(8.dp),
            ) {
              Box {
                val restFill = if (dark) Color.White.copy(alpha = 0.16f) else Color(0xFF1E4FA0).copy(alpha = 0.13f)
                val rim = if (dark) Color.White.copy(alpha = 0.26f) else Color(0xFF1E4FA0).copy(alpha = 0.22f)
                Box(
                    Modifier
                        .offset { IntOffset(0, (pillPos.value * rowPx).roundToInt()) }
                        .fillMaxWidth()
                        .height(app.plait.ui.ControlHeights.Large)
                        .graphicsLayer {
                            alpha = pillShow.value.coerceIn(0f, 1f)
                            val s = 1f + 0.03f * pillShow.value
                            scaleX = s; scaleY = s
                        }
                        // A flat tint plus a thin rim on the menu's own surface; no backdrop sampling, so the page never shows through.
                        .background(restFill, androidx.compose.foundation.shape.CircleShape)
                        .border(1.dp, rim, androidx.compose.foundation.shape.CircleShape),
                )
                Column {
                    actions.forEachIndexed { i, action ->
                        app.plait.ui.MenuRow(action.label, action.icon, app.plait.ui.toneAt(i), { onExpandedChange(false); action.onClick() }, Modifier.fillMaxWidth())
                    }
                }
              }
            }
        }
        Box(
            Modifier
                .size(collapsedSize)
                .liquidGlass(backdrop, tint = tint, interactive = true)
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = { onExpandedChange(!expanded) })
                .semantics { contentDescription = if (expanded) "Close menu" else collapsedDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(collapsedIcon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = t * 45f })
        }
    }
}
