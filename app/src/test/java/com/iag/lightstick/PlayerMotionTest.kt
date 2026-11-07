package com.iag.lightstick

import org.junit.Assert.*
import org.junit.Test

class PlayerMotionTest {
    @Test fun navigationMatchesLiteralReferenceExitSamples() {
        assertEquals(0f, PlayerMotion.navigationExit(0f, 123f), 0f)
        assertEquals(77.75083f, PlayerMotion.navigationExit(0.05f, 123f), 0.001f)
        assertEquals(106.35376f, PlayerMotion.navigationExit(0.1f, 123f), 0.001f)
        assertEquals(122.88784f, PlayerMotion.navigationExit(0.35f, 123f), 0.001f)
        assertEquals(0f, PlayerMotion.navigationExit(Float.NaN, 123f), 0f)
    }
    @Test fun miniChromeAndSharedArtworkUseDifferentReferenceTimings() {
        assertEquals(1f, PlayerMotion.miniAlpha(0.35f), 0f)
        assertEquals(0.5f, PlayerMotion.miniAlpha(0.475f), 0.001f)
        assertEquals(0f, PlayerMotion.miniAlpha(0.6f), 0f)
        assertEquals(0f, PlayerMotion.controlsAlpha(0f), 0f)
        assertEquals(0.6534264f, PlayerMotion.controlsAlpha(0.5f), 0.001f)
        assertEquals(1f, PlayerMotion.controlsAlpha(1f), 0f)
        assertEquals(0f, PlayerMotion.controlsAlpha(Float.NaN), 0f)
    }
    @Test fun dragThenReversalKeepsTheSameSurfaceCoordinate() {
        val dragged = PlayerMotion.dragProgress(1f, 120f, 750f)
        assertEquals(120f, PlayerMotion.sheetOffset(dragged, 750f), 0.001f)
        val returning = PlayerMotion.dragProgress(dragged, -120f, 750f)
        assertEquals(1f, returning, 0f)
        assertEquals(0f, PlayerMotion.sheetOffset(returning, 750f), 0f)
        assertEquals(dragged, PlayerMotion.dragProgress(dragged, Float.NaN, 750f), 0f)
    }
    @Test fun sharedArtworkMatchesBothMeasuredEndpointsIncludingPausedScale() {
        fun artwork(p: Float) = PlayerMotion.artwork(p, 32f, 750f, 30f, 30f,
            24f, 60f, 322f, 322f, 0.93f)
        val mini = artwork(0f)
        assertEquals(32f, mini.x, 0f); assertEquals(750f, mini.y, 0f)
        assertEquals(30f, mini.scaleX * 322f, 0.001f)
        val full = artwork(1f)
        assertEquals(35.27f, full.x, 0.001f); assertEquals(71.27f, full.y, 0.001f)
        assertEquals(0.93f, full.scaleX, 0.001f)
        (0..100).map { artwork(it / 100f) }.forEach {
            assertTrue(it.scaleX in mini.scaleX..full.scaleX)
            assertTrue(it.y in full.y..mini.y)
        }
    }
    private fun frame(p: Float) = PlayerMotion.frame(p, 400f, 900f, 16f, 750f, 368f, 43f)
    @Test fun endpointsMatchMeasuredMiniAndFullScreen() {
        val mini = frame(0f)
        assertEquals(16f, mini.x, 0.001f); assertEquals(750f, mini.y, 0.001f)
        assertEquals(368f, mini.scaleX * 400f, 0.001f); assertEquals(43f, mini.scaleY * 900f, 0.001f)
        val full = frame(1f)
        assertEquals(0f, full.x, 0f); assertEquals(0f, full.y, 0f)
        assertEquals(1f, full.scaleX, 0f); assertEquals(1f, full.scaleY, 0f); assertEquals(0f, full.corner, 0f)
    }
    @Test fun springOvershootNeverExposesOutsideBounds() {
        assertEquals(frame(0f), frame(-0.2f)); assertEquals(frame(1f), frame(1.2f))
        assertEquals(frame(1f), frame(Float.NaN))
        assertEquals(1f, PlayerMotion.contentAlpha(Float.NaN), 0f)
    }
    @Test fun reversingAtAnyIntermediateProgressKeepsOneGeometry() {
        (0..100).forEach {
            val f = frame(it / 100f)
            assertTrue(f.x in 0f..16f && f.y in 0f..750f)
            assertTrue(f.scaleX in 0.92f..1f && f.scaleY > 0f && f.scaleY <= 1f)
            assertTrue(PlayerMotion.contentAlpha(it / 100f) in 0f..1f)
        }
    }
    @Test fun missingMiniUsesFiniteSlideFallback() {
        val f = PlayerMotion.frame(0.5f, 400f, 900f, 0f, 0f, 0f, 0f)
        assertEquals(450f, f.y, 0f); assertEquals(1f, f.scaleY, 0f)
    }
    @Test fun dismissRequiresDownwardTravelOrDeliberateFling() {
        assertFalse(PlayerMotion.shouldDismiss(0f, 3000f, 2f))
        assertFalse(PlayerMotion.shouldDismiss(100f, -3000f, 2f))
        assertTrue(PlayerMotion.shouldDismiss(193f, 0f, 2f))
        assertTrue(PlayerMotion.shouldDismiss(8f, 1801f, 2f))
    }
    @Test fun coverFitsNarrowScreensAndMakesRoomForLargeFonts() {
        assertTrue(PlayerMotion.cover(393f, 850f, 1.5f) < PlayerMotion.cover(393f, 850f, 1f))
        listOf(96f, 320f, 393f, 800f).forEach { width ->
            assertTrue(PlayerMotion.cover(width, 850f, 1f) <= width - 48f)
        }
    }
}
