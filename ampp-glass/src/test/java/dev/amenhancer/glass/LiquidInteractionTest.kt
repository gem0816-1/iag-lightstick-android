package dev.amenhancer.glass

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LiquidInteractionTest {
    private val instant = object : MotionDurationScale { override val scaleFactor = 0f }
    private val immediateClock = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(0L)
    }
    private fun motion(scope: CoroutineScope) = DampedDragAnimation(scope, 0f, 0f..3f, .001f,
        1f, 1.5f, {}, {}, { _, _ -> }, pressedScaleY = 1.45f)
    private suspend fun drain() { repeat(40) { yield() } }
    private fun assertIdle(m: DampedDragAnimation, target: Float) {
        assertEquals(target, m.value, 0f)
        assertEquals(target, m.targetValue, 0f)
        assertEquals(0f, m.velocity, 0f)
        assertEquals(0f, m.pressProgress, 0f)
        assertEquals(1f, m.scaleX, 0f)
        assertEquals(1f, m.scaleY, 0f)
        assertEquals(DampedDragAnimation.Phase.Idle, m.phase)
    }

    @Test fun repeatedSelectionUsesLatestTargetAndFullyReturnsToIdleWithAnimationsDisabled() =
        runBlocking(immediateClock + instant) {
            withTimeout(5000) {
                val m = motion(this)
                repeat(40) { m.animateToValue((it % 4).toFloat()) }
                drain()
                assertIdle(m, 3f)
            }
        }

    @Test fun releaseBeforeFirstFrameCommitsLatestDragAndIgnoresLatePointerSamples() =
        runBlocking(immediateClock + instant) {
            withTimeout(5000) {
                val m = motion(this)
                m.press()
                repeat(1000) { m.updateValue(it / 500f) }
                assertEquals(0f, m.value, 0f) // no movement jobs run per pointer event
                assertEquals(1.998f, m.targetValue, .00001f)
                m.release()
                m.updateValue(0f)
                drain()
                assertIdle(m, 1.998f)
            }
        }

    @Test fun invalidDragCannotPoisonAnimationCoordinates() = runBlocking(immediateClock + instant) {
        withTimeout(5000) {
            val m = motion(this)
            m.press(); m.updateValue(2f); m.updateValue(Float.NaN); m.updateValue(Float.POSITIVE_INFINITY)
            m.release(); drain()
            assertIdle(m, 2f)
        }
    }

    @Test fun boundariesStaySelectableAfterOutOfRangeInputAndRepeatedRelease() = runBlocking(immediateClock + instant) {
        withTimeout(5000) {
            val m = motion(this)
            m.animateToValue(100f); drain(); assertIdle(m, 3f)
            m.press(); m.updateValue(-100f); m.release(); m.release(); drain(); assertIdle(m, 0f)
            m.animateToValue(Float.NaN); drain(); assertIdle(m, 0f)
        }
    }

    @Test fun reversingARunningSpringKeepsCurrentPositionAndEndsAtTheNewTarget() {
        val clock = BroadcastFrameClock()
        val normal = object : MotionDurationScale { override val scaleFactor = 1f }
        runBlocking(clock + normal) {
            withTimeout(5000) {
                val m = motion(this)
                m.animateToValue(3f)
                repeat(6) { yield(); clock.sendFrame(it * 16_666_667L); Snapshot.sendApplyNotifications(); drain() }
                assertTrue(m.value > 0f && m.value < 3f)
                val before = m.value
                m.animateToValue(0f)
                assertEquals(before, m.value, 0f)
                repeat(240) { yield(); clock.sendFrame((it + 6) * 16_666_667L); Snapshot.sendApplyNotifications(); drain() }
                drain()
                assertIdle(m, 0f)
            }
        }
    }

    @Test fun aNewPressCancelsTheOldReleaseAndKeepsTheNewInteractionAlive() = runBlocking(immediateClock + instant) {
        withTimeout(5000) {
            val m = motion(this)
            m.animateToValue(3f)
            m.press(); m.updateValue(1f)
            drain()
            assertEquals(DampedDragAnimation.Phase.Dragging, m.phase)
            assertEquals(1f, m.value, 0f)
            assertEquals(1f, m.pressProgress, 0f)
            m.release(); drain(); assertIdle(m, 1f)
        }
    }

    @Test fun rapidNativePressReleaseCannotLeaveAnInflatedHighlight() = runBlocking(immediateClock + instant) {
        withTimeout(5000) {
            val highlight = InteractiveHighlight(this)
            repeat(30) {
                highlight.pressAt(Offset(10f, 20f))
                highlight.moveTo(Offset(50f, 60f))
                highlight.releasePress()
            }
            highlight.moveTo(Offset(999f, 999f))
            drain()
            assertEquals(0f, highlight.pressProgress, 0f)
            assertEquals(Offset.Zero, highlight.offset)
        }
    }

    @Test fun newHighlightPressSupersedesAllOldReturnJobs() = runBlocking(immediateClock + instant) {
        withTimeout(5000) {
            val highlight = InteractiveHighlight(this)
            highlight.pressAt(Offset(1f, 2f)); highlight.releasePress()
            highlight.pressAt(Offset(100f, 200f)); drain()
            assertEquals(1f, highlight.pressProgress, 0f)
            assertEquals(Offset.Zero, highlight.offset)
            highlight.moveTo(Offset(Float.NaN, 1f)); drain()
            assertEquals(Offset.Zero, highlight.offset)
            highlight.releasePress(); drain()
            assertEquals(0f, highlight.pressProgress, 0f)
        }
    }

    @Test fun phaseMachineFollowsTheRecoveredLiquidTabMotionStates() = runBlocking(immediateClock + instant) {
        withTimeout(5000) {
            val m = motion(this)
            assertEquals(DampedDragAnimation.Phase.Idle, m.phase)
            m.press()
            assertEquals(DampedDragAnimation.Phase.Preview, m.phase)
            m.updateValue(2f)
            assertEquals(DampedDragAnimation.Phase.Dragging, m.phase)
            m.release()
            assertEquals(DampedDragAnimation.Phase.Settling, m.phase)
            drain()
            assertIdle(m, 2f)
            m.animateToValue(0f)
            assertEquals(DampedDragAnimation.Phase.Settling, m.phase)
            drain()
            assertIdle(m, 0f)
        }
    }

    @Test fun renderScaleCarriesNoDeformationWhileIdle() = runBlocking(immediateClock + instant) {
        withTimeout(5000) {
            val m = motion(this)
            assertEquals(m.scaleX, m.renderScaleX, 0f)
            assertEquals(m.scaleY, m.renderScaleY, 0f)
            m.animateToValue(2f); drain()
            assertEquals(m.scaleX, m.renderScaleX, 0f)
            assertEquals(m.scaleY, m.renderScaleY, 0f)
        }
    }

    @Test fun dragReleasePreservesPressAndSettlesThroughTheFlight() {
        val clock = BroadcastFrameClock()
        val normal = object : MotionDurationScale { override val scaleFactor = 1f }
        runBlocking(clock + normal) {
            withTimeout(5000) {
                val m = motion(this)
                m.press(); m.updateValue(3f)
                repeat(4) { yield(); clock.sendFrame(it * 16_666_667L); Snapshot.sendApplyNotifications(); drain() }
                assertEquals(DampedDragAnimation.Phase.Dragging, m.phase)
                m.settleToValue(3f, trackDeformationVelocity = true)
                // Mid-flight: the pressed shape holds (recovered m9356) and the pill is not home yet.
                repeat(3) { yield(); clock.sendFrame((it + 4) * 16_666_667L); Snapshot.sendApplyNotifications(); drain() }
                assertTrue(m.value < 3f)
                assertEquals(DampedDragAnimation.Phase.Settling, m.phase)
                assertTrue(m.pressProgress > 0.5f)
                repeat(240) { yield(); clock.sendFrame((it + 7) * 16_666_667L); Snapshot.sendApplyNotifications(); drain() }
                assertIdle(m, 3f)
            }
        }
    }

    @Test fun selectionSettleStartsFromZeroVelocityButStillPreservesPressUntilArrival() {
        val clock = BroadcastFrameClock()
        val normal = object : MotionDurationScale { override val scaleFactor = 1f }
        runBlocking(clock + normal) {
            withTimeout(5000) {
                val m = motion(this)
                m.animateToValue(3f)
                repeat(6) { yield(); clock.sendFrame(it * 16_666_667L); Snapshot.sendApplyNotifications(); drain() }
                assertTrue(m.value < 3f)
                assertTrue(m.pressProgress > 0.5f)
                // A selection settles like the recovered m9335: no deformation-velocity tracking.
                assertEquals(0f, m.velocity, 0.0001f)
                repeat(240) { yield(); clock.sendFrame((it + 3) * 16_666_667L); Snapshot.sendApplyNotifications(); drain() }
                assertIdle(m, 3f)
            }
        }
    }
}
