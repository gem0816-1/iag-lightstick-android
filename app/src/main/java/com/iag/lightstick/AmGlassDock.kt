package com.iag.lightstick

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.amenhancer.glass.GlassBarCollapseLayout
import dev.amenhancer.glass.GlassBarCollapsePolicy
import dev.amenhancer.glass.GlassBarCollapseSprings
import dev.amenhancer.glass.GlassPolicy
import kotlin.math.roundToInt
import dev.amenhancer.glass.BottomScrim

/** AM++ phone stack, driven by the host's single collapse spring. */
@Composable
fun AmGlassDock(
    backdrop: Backdrop,
    selected: Int,
    onSelect: (Int) -> Unit,
    miniVisible: Boolean,
    collapseProgress: () -> Float,
    onExpandRequest: () -> Unit,
    modifier: Modifier = Modifier,
    playerExpansion: () -> Float = { 0f },
    miniPlayer: @Composable () -> Unit,
) {
    val settings = LocalGlassSettings.current
    val bottomInset = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    var expandedMiniSize by remember { mutableStateOf(IntSize.Zero) }
    val reserveMiniLayer by remember(collapseProgress) {
        derivedStateOf { val p = collapseProgress(); p != 0f && p != 1f }
    }
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = GlassPolicy.HORIZONTAL_DP.dp)
            .padding(bottom = settings.bottomGapDp.dp),
        content = {
            // Preserve the reference's expanded measurement even when the dock is only 43dp
            // tall. Its own 13dp drop then bottom-aligns the compact glass and its hit region.
            Box(Modifier.fillMaxWidth().height(GlassPolicy.NAV_HEIGHT_DP.dp).graphicsLayer {
                translationY = PlayerMotion.navigationExit(playerExpansion(), GlassPolicy.occupiedHeight(
                    density = density, bottomInset = bottomInset, miniVisible = miniVisible,
                    bottomGapDp = settings.bottomGapDp, collapse = collapseProgress(),
                ).toFloat())
            }.onSizeChanged {
                expandedMiniSize = IntSize(it.width, (it.height * GlassPolicy.MINI_HEIGHT_DP.toFloat() / GlassPolicy.NAV_HEIGHT_DP).roundToInt())
            }) {
                AmLiquidBottomTabs(
                    backdrop = backdrop,
                    selected = selected,
                    onSelect = onSelect,
                    collapseProgress = collapseProgress,
                    onExpandRequest = onExpandRequest,
                )
            }
            if (miniVisible) {
                // Drawn after the nav: the mini owns overlapping input during the transition.
                // Neither this wrapper nor the dock clips pressed material or spring rebound.
                Box(Modifier.fillMaxWidth().height(GlassPolicy.MINI_HEIGHT_DP.dp).graphicsLayer {
                    alpha = PlayerMotion.miniAlpha(playerExpansion())
                }) {
                    CompositionLocalProvider(LocalMiniLayerSize provides if (reserveMiniLayer) expandedMiniSize else IntSize.Zero) { miniPlayer() }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val navHeight = GlassPolicy.NAV_HEIGHT_DP.dp.roundToPx()
        val miniHeight = GlassPolicy.MINI_HEIGHT_DP.dp.roundToPx()
        val gap = GlassPolicy.GAP_DP.dp.toPx()
        val rawProgress = collapseProgress()
        val progress = GlassBarCollapsePolicy.progress(rawProgress)
        val expandedHeight = navHeight + if (miniVisible) miniHeight + gap else 0f
        val stackHeight = GlassBarCollapsePolicy.lerp(
            expandedHeight,
            GlassBarCollapsePolicy.ROW_DP.dp.toPx(),
            progress,
        ).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val nav = measurables[0].measure(Constraints.fixed(width, navHeight))

        val collapsedControls = GlassBarCollapseLayout.controls(
            rowWidthPx = width.toFloat(),
            rowHeightPx = navHeight.toFloat(),
            density = density,
            tabsCount = 4,
            progress = 1f,
            collapseReboundScale = settings.collapseRebound / 100f,
            expandReboundScale = settings.expandRebound / 100f,
        )
        // AM++ defaults: mini rebound 100%, with no detached search reserve in IAG.
        val miniFraction = GlassBarCollapsePolicy.sprungFraction(rawProgress, settings.miniRebound / 100f, settings.miniRebound / 100f)
        val miniStart = GlassBarCollapsePolicy.miniStartMargin(
            0f, collapsedControls.capsuleWidth, gap, miniFraction,
        ).roundToInt().coerceIn(0, (width - 1).coerceAtLeast(0))
        val mini = if (miniVisible) {
            measurables[1].measure(Constraints.fixed((width - miniStart).coerceAtLeast(0), miniHeight))
        } else null
        val miniDrop = GlassBarCollapsePolicy.miniDrop(
            miniHeight.toFloat(), GlassBarCollapsePolicy.ROW_DP.dp.toPx(), rawProgress,
        ) + GlassBarCollapsePolicy.miniSinkOvershoot(
            settings.miniSinkDp.dp.toPx(), GlassBarCollapseSprings.CollapseOvershoot, rawProgress,
        )
        layout(width, stackHeight) {
            nav.placeRelative(0, stackHeight - navHeight)
            mini?.placeRelative(miniStart, miniDrop.roundToInt())
        }
    }
}

/** A moving footer requests layout locally, without recomposing the four pages. */
@Composable
fun AmDockScrim(backdrop: Backdrop, height: () -> androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Layout(content = { BottomScrim(backdrop) }, modifier = modifier.fillMaxWidth()) { children, constraints ->
        val h = height().roundToPx().coerceIn(0, constraints.maxHeight)
        val child = children.single().measure(Constraints.fixed(constraints.maxWidth, h))
        layout(child.width, h) { child.placeRelative(0, 0) }
    }
}
