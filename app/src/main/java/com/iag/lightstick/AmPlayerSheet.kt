package com.iag.lightstick

import dev.amenhancer.glass.LiquidMotion

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One coordinate prevents a dismissed drag being added to a second closing animation. */
@Stable
internal class AmPlayerMotionState(private val scope: CoroutineScope, private val density: Float,
    private val fallbackTravel: Float = 0f) {
    var progress by mutableFloatStateOf(0f)
        private set
    var source by mutableStateOf(Rect.Zero)
        private set
    var artworkSource by mutableStateOf(Rect.Zero)
        private set
    var artworkTarget by mutableStateOf(Rect.Zero)
    private var settleJob: Job? = null
    private var releaseVelocity = 0f

    val offset get() = PlayerMotion.sheetOffset(progress, source.top)
    val sharedArtwork get() = !artworkSource.isEmpty && !artworkTarget.isEmpty

    fun setExpanded(expanded: Boolean, mini: Rect, artwork: Rect) {
        // Freeze coordinates before navigation exit moves the mini's layout.
        if (expanded && progress == 0f) {
            source = if (!mini.isEmpty && mini.top > 0f) mini else Rect(0f, fallbackTravel, 1f, fallbackTravel + 1f)
            artworkSource = artwork
        }
        val velocity = releaseVelocity
        releaseVelocity = 0f
        settle(if (expanded) 1f else 0f, velocity)
    }

    fun dragBy(delta: Float) {
        settleJob?.cancel()
        progress = PlayerMotion.dragProgress(progress, delta, source.top)
    }

    fun end(velocity: Float, onDismiss: () -> Unit) {
        val normalizedVelocity = if (source.top > 0f && velocity.isFinite()) -velocity / source.top else 0f
        if (PlayerMotion.shouldDismiss(offset, velocity, density)) {
            releaseVelocity = normalizedVelocity
            onDismiss()
        } else settle(1f, normalizedVelocity)
    }

    private fun settle(target: Float, velocity: Float = 0f) {
        settleJob?.cancel()
        settleJob = scope.launch {
            // Compose inherits the system animation duration scale, including 0x.
            animate(progress, target, initialVelocity = velocity, animationSpec = LiquidMotion.panel()) { value, _ ->
                progress = PlayerMotion.progress(value)
            }
            progress = target
        }
    }
}

internal val LocalPlayerMotion = staticCompositionLocalOf<AmPlayerMotionState?> { null }

@Composable
internal fun rememberAmPlayerMotion(): AmPlayerMotionState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val height = LocalWindowInfo.current.containerSize.height
    return remember(scope, density, height) { AmPlayerMotionState(scope, density, height.toFloat()) }
}

/** Host sheet, separately transformed artwork, and the literal host chrome fade. */
@Composable
internal fun AmPlayerSheet(
    visible: Boolean,
    origin: () -> Rect,
    artworkOrigin: () -> Rect,
    motion: AmPlayerMotionState,
    colorHex: String,
    playing: Boolean,
    onCovered: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(visible, motion) { motion.setExpanded(visible, origin(), artworkOrigin()) }
    val covered by remember(visible, motion) { derivedStateOf { visible && motion.progress == 1f } }
    val present by remember(visible, motion) { derivedStateOf { visible || motion.progress > 0f } }
    val coveredCallback by rememberUpdatedState(onCovered)
    LaunchedEffect(covered) { coveredCallback(covered) }
    if (!present) return

    val density = LocalDensity.current
    val coverScale by animateFloatAsState(if (playing) 1f else 0.93f, LiquidMotion.artwork(), label = "sharedCover")
    CompositionLocalProvider(LocalPlayerMotion provides motion) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) {})
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationY = motion.offset
                shape = RoundedCornerShape(topStart = PlayerMotion.sheetCorner(motion.progress).dp,
                    topEnd = PlayerMotion.sheetCorner(motion.progress).dp)
                clip = true
            }) { content() }

            // Only endpoint measurement changes layout; every motion frame updates one layer.
            val target = motion.artworkTarget
            if (motion.sharedArtwork) AmPlayerArtwork(colorHex,
                Modifier.size(with(density) { target.width.toDp() }, with(density) { target.height.toDp() })
                    .graphicsLayer {
                        val p = motion.progress
                        val source = motion.artworkSource
                        val frame = PlayerMotion.artwork(p, source.left, source.top, source.width, source.height,
                            target.left, target.top, target.width, target.height, coverScale)
                        transformOrigin = TransformOrigin(0f, 0f)
                        translationX = frame.x; translationY = frame.y
                        scaleX = frame.scaleX; scaleY = frame.scaleY
                        alpha = if (p > 0f && p < 1f) 1f else 0f
                        val radius = (5f + 5f * p) / frame.scaleX.coerceAtLeast(0.01f)
                        shape = RoundedCornerShape(radius.dp)
                        clip = true
                        shadowElevation = 15.dp.toPx() * p
                    }, inset = 16.dp)
        }
    }
}
