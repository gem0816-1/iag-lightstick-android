/*
 * Derived from AndroidLiquidGlass / Backdrop 2.0.1
 * (https://github.com/Kyant0/AndroidLiquidGlass), commit
 * 65ab177e90e5c1d8c62e70cf7755841982da65f6, Apache License 2.0.
 * Changed by AM++: tap-only native reselection, independent pressed Y scale,
 * and cancellation of obsolete motion/press jobs with complete release cleanup.
 * IAG v0.9.18: shared motion roles, coalesced frame requests, monotonic velocity and explicit interaction phases.
 * IAG v0.9.21: interaction phases and settle behavior follow the recovered Coolapk
 * `LiquidTabMotionState`/`LiquidTabMotionPhase` bytecode (capture/coolapk-exact-20261031):
 * Preview/Dragging split, press preserved until the pill arrives, selection settles without
 * deformation-velocity tracking while a drag release keeps tracking the animated position's
 * velocity, and the render scale applies the velocity deformation whenever the phase is not Idle.
 * The spring numbers remain this project's LiquidMotion roles; the packed `o49` spec values were
 * not recoverable from bytecode. See backdrop/UPSTREAM.md and THIRD_PARTY_NOTICES.md.
 */

package com.kyant.backdrop.catalog.utils

import dev.amenhancer.glass.LiquidMotion

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeSource

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    // AM++: observe a completed tap on the floating indicator for native tab reselection.
    val onTap: (() -> Unit)? = null,
    val pressedScaleY: Float = pressedScale,
) {

    init {
        require(valueRange.start.isFinite() && valueRange.endInclusive.isFinite() && valueRange.start < valueRange.endInclusive)
        require(initialValue.isFinite() && initialValue in valueRange)
    }

    /** Recovered `LiquidTabMotionPhase`: Idle, Preview (pressed in place), Dragging, Settling. */
    enum class Phase { Idle, Preview, Dragging, Settling }
    var phase = Phase.Idle
        private set

    private val valueAnimationSpec =
        LiquidMotion.position(visibilityThreshold)
    private val velocityAnimationSpec =
        LiquidMotion.feedback(visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        LiquidMotion.position(0.001f)
    private val scaleXAnimationSpec =
        LiquidMotion.deformationX(0.001f)
    private val scaleYAnimationSpec =
        LiquidMotion.deformationY(0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation =
        Animatable(0f, 5f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private var motionJob: Job? = null
    private var velocityJob: Job? = null
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var velocityGeneration = 0
    private var dragFrameJob: Job? = null
    private var pendingDragValue: Float? = null
    private var requestedValue = initialValue
    private val velocityClock = TimeSource.Monotonic.markNow()

    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = requestedValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    /**
     * Recovered render scale (LiquidTabMotionState m9344/m9345): the deformation velocity applies
     * whenever the phase is not Idle — during the drag and through the settle flight — not only
     * while the finger is down. The coefficients are this project's established port of the
     * velocity squeeze.
     */
    val renderScaleX: Float
        get() {
            val deformation = if (phase == Phase.Idle) 0f else velocity / 10f
            return scaleX / (1f - (deformation * 0.75f).coerceIn(-0.2f, 0.2f))
        }
    val renderScaleY: Float
        get() {
            val deformation = if (phase == Phase.Idle) 0f else velocity / 10f
            return scaleY * (1f - (deformation * 0.25f).coerceIn(-0.2f, 0.2f))
        }

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        var distance = 0f
        inspectDragGestures(
            onDragStart = { down ->
                distance = 0f
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                if (distance <= viewConfiguration.touchSlop) onTap?.invoke()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            distance += dragAmount.getDistance()
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        phase = Phase.Preview
        releaseJob?.cancel()
        pressJob?.cancel()
        dragFrameJob?.cancel()
        pendingDragValue = null
        val generation = ++velocityGeneration
        velocityJob?.cancel()
        velocityTracker.resetTracking()
        velocityJob = animationScope.launch {
            if (generation == velocityGeneration) resetVelocity()
        }
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScaleY, scaleYAnimationSpec) }
        }
    }

    fun release() {
        if (phase == Phase.Idle) return
        phase = Phase.Settling
        dragFrameJob?.cancel()
        pendingDragValue?.let { pendingDragValue = null; moveTo(it) }
        releaseJob?.cancel()
        releaseJob = animationScope.launch {
            withFrameNanos { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { abs(valueAnimation.value - valueAnimation.targetValue) }
                    .first { it < threshold }
            }
            // Invalidate queued drag-frame writes before returning to the idle shape.
            velocityGeneration++
            velocityJob?.cancelAndJoin()
            pressJob?.cancelAndJoin()
            coroutineScope {
                launch { resetVelocity() }
                launch {
                    pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
                    pressProgressAnimation.snapTo(0f)
                }
                launch {
                    scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec)
                    scaleXAnimation.snapTo(initialScale)
                }
                launch {
                    scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec)
                    scaleYAnimation.snapTo(initialScale)
                }
            }
            motionJob?.join()
            phase = Phase.Idle
        }
    }

    fun updateValue(value: Float) {
        // The recovered drag path (m9338) promotes the phase to Dragging from either Preview
        // or an ongoing drag; a stale generation must not resurrect a finished interaction.
        if ((phase != Phase.Preview && phase != Phase.Dragging) || !value.isFinite()) return
        phase = Phase.Dragging
        requestedValue = value.coerceIn(valueRange)
        pendingDragValue = requestedValue
        // Accumulate all pointer deltas immediately, but retarget at most once per frame.
        // Avoid one coroutine/velocity update per touch sample on high-rate digitizers.
        if (dragFrameJob?.isActive == true) return
        dragFrameJob = animationScope.launch {
            withFrameNanos { }
            val latest = pendingDragValue
            pendingDragValue = null
            if ((phase == Phase.Preview || phase == Phase.Dragging) && latest != null) moveTo(latest)
        }
    }

    private fun moveTo(targetValue: Float) {
        val generation = velocityGeneration
        motionJob = animationScope.launch {
            // Retarget the running spring through Animatable's own cancellation.
            // Eager cancellation on every move discards its frame time and velocity.
            if (generation == velocityGeneration) {
                valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity(generation) }
            }
        }
    }

    fun animateToValue(value: Float) {
        if (!value.isFinite()) return
        val targetValue = value.coerceIn(valueRange)
        if (phase == Phase.Idle && this.value == targetValue) return
        requestedValue = targetValue
        beginSettling()
        press()
        motionJob = animationScope.launch {
            coroutineScope {
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                // Always reset, even when a pending velocity write has not started yet.
                launch { resetVelocity() }
            }
        }
        release()
    }

    /**
     * Recovered settle (m9340/m9359): a drag release keeps tracking the animated position's
     * velocity so the deformation follows the pill through the flight, and the pressed shape is
     * preserved until the pill arrives (m9356). A plain selection (m9335) settles without the
     * tracker and starts from zero velocity — use [animateToValue] for that.
     */
    fun settleToValue(value: Float, trackDeformationVelocity: Boolean) {
        if (!value.isFinite()) return
        val targetValue = value.coerceIn(valueRange)
        if (phase == Phase.Idle && this.value == targetValue) return
        // Only a genuine drag release (recovered m9340) tracks the pill's velocity; anything
        // else settles like a selection, from zero velocity (m9335).
        val fromDrag = trackDeformationVelocity && phase == Phase.Dragging
        requestedValue = targetValue
        beginSettling()
        if (fromDrag) {
            // The pressed shape is already in place from the drag; re-pressing would zero the
            // velocity this settle is supposed to follow.
            val generation = velocityGeneration
            motionJob = animationScope.launch {
                valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity(generation) }
            }
        } else {
            press()
            motionJob = animationScope.launch {
                coroutineScope {
                    launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                    launch { resetVelocity() }
                }
            }
        }
        release()
    }

    private fun beginSettling() {
        velocityGeneration++
        dragFrameJob?.cancel()
        pendingDragValue = null
        motionJob?.cancel()
        velocityJob?.cancel()
    }

    private suspend fun resetVelocity() {
        velocityAnimation.animateTo(0f, velocityAnimationSpec)
        velocityAnimation.snapTo(0f)
    }

    private fun updateVelocity(generation: Int) {
        if (generation != velocityGeneration) return
        velocityTracker.addPosition(
            velocityClock.elapsedNow().inWholeMilliseconds,
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        velocityJob = animationScope.launch {
            if (generation == velocityGeneration) {
                velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
            }
        }
    }
}
