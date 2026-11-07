package com.iag.lightstick

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.amenhancer.glass.CoolapkNavigationMetrics
import dev.amenhancer.glass.GlassNavigation
import dev.amenhancer.glass.GlassTab
import dev.amenhancer.glass.LiquidMotion

/** Resource-aligned liquid bar; retain IAG destinations, palette, mini player and collapse.
 * This is an adaptation using the existing open-source renderer, not recovered Coolapk code.
 */
@Composable
internal fun CoolapkBottomNavigation(backdrop: Backdrop, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, collapseProgress: (() -> Float)? = null,
    onExpandRequest: (() -> Unit)? = null) {
    val settings = LocalGlassSettings.current
    val colors = LocalAmColors.current
    val tabs = remember { listOf(
        GlassTab(0, "控制", Icons.Filled.PlayCircle, true),
        GlassTab(1, "编排", Icons.Filled.LibraryMusic, true),
        GlassTab(2, "律动", Icons.Filled.Radio, true),
        GlassTab(3, "设备", Icons.Filled.FlashlightOn, true),
    ) }
    if (settings.enabled) {
        Box(modifier.fillMaxWidth().height(CoolapkNavigationMetrics.HeightDp.dp)) {
            GlassNavigation(tabs, selected, colors.key, colors.ink, backdrop,
                onSelect = { onSelect(it); it }, panelHeight = CoolapkNavigationMetrics.HeightDp.dp,
                panelBlur = settings.blurDp.dp, collapseProgress = collapseProgress,
                onExpandRequest = onExpandRequest,
                collapseReboundScale = settings.collapseRebound / 100f,
                expandReboundScale = settings.expandRebound / 100f)
        }
        return
    }
    // The glass-off path has the same moving indicator and selected-state transition.
    // Read position in the layer, so an animation frame never remeasures the tab row.
    val position = animateFloatAsState(selected.toFloat(), LiquidMotion.position(), label = "navigationPosition")
    val direction = LocalLayoutDirection.current
    Box(modifier.fillMaxWidth().height(CoolapkNavigationMetrics.HeightDp.dp)
        .clip(CircleShape).background(colors.fill).padding(CoolapkNavigationMetrics.ContentPaddingDp.dp)) {
        Layout(content = {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val logical = position.value.coerceIn(0f, (tabs.size - 1).toFloat())
                translationX = logical * size.width * if (direction == androidx.compose.ui.unit.LayoutDirection.Ltr) 1f else -1f
            }.clip(CircleShape).background(colors.panel))
        }, modifier = Modifier.matchParentSize()) { children, constraints ->
            val w = constraints.maxWidth / tabs.size
            val child = children.single().measure(Constraints.fixed(w, constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) { child.placeRelative(0, 0) }
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            tabs.forEach { tab ->
                val tint by animateColorAsState(if (selected == tab.id) colors.key else colors.ink,
                    LiquidMotion.position(), label = "navigationTint")
                Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                    .selectable(selected == tab.id, role = Role.Tab, onClick = { onSelect(tab.id) }),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(tab.icon!!, contentDescription = null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(tint))
                    // Same IconText label the glass path draws: 16sp, bold while selected.
                    BasicText(tab.title, style = TextStyle(fontSize = 16.sp, color = tint,
                        fontWeight = if (selected == tab.id) FontWeight.Bold else FontWeight.Normal),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
