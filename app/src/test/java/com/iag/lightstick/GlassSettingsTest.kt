package com.iag.lightstick

import org.junit.Assert.*
import org.junit.Test

class GlassSettingsTest {
    @Test fun defaultsMatchAmppAndGlassStartsEnabled() {
        val s = GlassSettings()
        assertTrue(s.enabled)
        assertTrue(s.collapseEnabled)
        assertEquals(listOf(16, 4, 35, 100, 100, 4),
            listOf(s.bottomGapDp, s.blurDp, s.collapseRebound, s.expandRebound, s.miniRebound, s.miniSinkDp))
    }

    @Test fun savedValuesOutsideTheReferenceRangesAreBounded() {
        val s = GlassSettings(bottomGapDp = 999, blurDp = -5, collapseRebound = -1,
            expandRebound = 300, miniRebound = Int.MAX_VALUE, miniSinkDp = Int.MIN_VALUE).normalized()
        assertEquals(listOf(48, 0, 0, 200, 200, 0),
            listOf(s.bottomGapDp, s.blurDp, s.collapseRebound, s.expandRebound, s.miniRebound, s.miniSinkDp))
    }

    @Test fun disablingGlassRetainsTunedParametersForTheNextEnable() {
        val tuned = GlassSettings(bottomGapDp = 31, blurDp = 13, collapseRebound = 71,
            expandRebound = 42, miniRebound = 180, miniSinkDp = 0)
        val disabled = tuned.copy(enabled = false).normalized()
        assertFalse(disabled.enabled)
        assertEquals(tuned, disabled.copy(enabled = true))
    }

    @Test fun zeroBlurAndZeroReboundRemainValidChoices() {
        val s = GlassSettings(bottomGapDp = 0, blurDp = 0, collapseRebound = 0,
            expandRebound = 0, miniRebound = 0, miniSinkDp = 0)
        assertEquals(s, s.normalized())
    }
}
