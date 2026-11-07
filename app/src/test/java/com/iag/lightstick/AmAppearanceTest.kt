package com.iag.lightstick

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class AmAppearanceTest {
    private fun contrast(ink: Color, background: Color): Float {
        val a = ink.compositeOver(background).luminance()
        val b = background.luminance()
        return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
    }
    @Test fun primaryLabelsPassInBothAppearances() {
        listOf(AmLightColors, AmDarkColors).forEach { colors ->
            listOf(colors.background, colors.fill, colors.panel).forEach { surface ->
                assertTrue("Primary contrast must be >= 7", contrast(colors.ink, surface) >= 7f)
            }
        }
    }
    @Test fun readablePanelsProtectSecondaryTextFromBrightAndDarkBackdrops() {
        listOf(AmLightColors, AmDarkColors).forEach { colors ->
            listOf(Color.White, Color.Black).forEach { behind ->
                val surface = colors.panel.copy(alpha = 0.94f).compositeOver(behind)
                val ratio = contrast(colors.panelSecondary, surface)
                println("menu dark=${colors.dark}, backdrop=$behind, contrast=$ratio")
                assertTrue("Menu secondary contrast must be >= 4.5, actual=$ratio", ratio >= 4.5f)
            }
        }
    }
}
