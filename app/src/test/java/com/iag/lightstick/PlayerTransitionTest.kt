package com.iag.lightstick

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PlayerTransitionTest {
    @Test fun reversingDuringOpeningRetainsTheCurrentCoordinate() {
        val frames = Channel<Long>(Channel.UNLIMITED)
        val frameClock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.receive())
        }
        val normalMotion = object : MotionDurationScale { override val scaleFactor = 1f }
        runBlocking(frameClock + normalMotion) {
            val motion = AmPlayerMotionState(this, 1f)
            motion.setExpanded(true, mini, art)
            yield()
            repeat(4) { frames.send(it * 16_666_667L); yield() }
            assertTrue(motion.progress > 0f && motion.progress < 1f)
            val intermediate = motion.progress
            motion.setExpanded(false, Rect.Zero, Rect.Zero)
            assertEquals(intermediate, motion.progress, 0f)
            repeat(90) { frames.send((it + 4) * 16_666_667L); yield() }
            assertEquals(0f, motion.progress, 0f)
            assertEquals(art, motion.artworkSource)
        }
    }

    @Test fun missingMiniLayoutUsesScreenTravelAndStillAllowsDismiss() = runBlocking(clock + noMotion) {
        val motion = AmPlayerMotionState(this, 1f, fallbackTravel = 800f)
        motion.setExpanded(true, Rect.Zero, Rect.Zero)
        yield()
        motion.dragBy(120f)
        assertEquals(120f, motion.offset, 0.001f)
        var dismissed = false
        motion.end(0f) { dismissed = true }
        assertTrue(dismissed)
    }

    private val clock = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(0L)
    }
    private val noMotion = object : MotionDurationScale { override val scaleFactor = 0f }
    private val mini = Rect(16f, 750f, 384f, 793f)
    private val art = Rect(32f, 756f, 62f, 786f)

    @Test fun animationScaleZeroAndRepeatedOpenCloseReachExactEndpoints() = runBlocking(clock + noMotion) {
        val motion = AmPlayerMotionState(this, 1f)
        repeat(3) {
            motion.setExpanded(true, mini, art)
            yield()
            assertEquals(1f, motion.progress, 0f)
            motion.dragBy(120f)
            val beforeDismiss = motion.offset
            assertEquals(120f, beforeDismiss, 0.001f)
            var dismissed = false
            motion.end(0f) { dismissed = true }
            assertTrue(dismissed)
            motion.setExpanded(false, mini, art)
            // Requesting close must not add the 120px drag to another sheet translation.
            assertEquals(beforeDismiss, motion.offset, 0.001f)
            yield()
            assertEquals(0f, motion.progress, 0f)
            assertEquals(750f, motion.offset, 0f)
        }
    }

    @Test fun aSmallCanceledDragSettlesBackAndKeepsTheFrozenArtworkOrigin() = runBlocking(clock + noMotion) {
        val motion = AmPlayerMotionState(this, 1f)
        motion.setExpanded(true, mini, art)
        yield()
        motion.dragBy(20f)
        motion.end(0f) { fail("Small drags should return to the player") }
        yield()
        assertEquals(1f, motion.progress, 0f)
        motion.setExpanded(false, Rect.Zero, Rect.Zero)
        assertEquals(art, motion.artworkSource)
        yield()
        assertEquals(0f, motion.progress, 0f)
    }
}
