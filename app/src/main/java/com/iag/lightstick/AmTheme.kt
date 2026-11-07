package com.iag.lightstick

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import android.graphics.BitmapFactory
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

@Immutable
internal data class AmColors(
    val dark: Boolean, val background: Color, val ink: Color,
    val secondary: Color, val tertiary: Color, val separator: Color,
    val fill: Color, val track: Color, val glass: Color, val panel: Color,
    val selection: Color, val panelSecondary: Color,
    /** Apple Music key color: bottom navigation selection (#0088FF light / #0091FF dark). */
    val key: Color,
)

internal val AmLightColors = AmColors(false, Color.White, Color.Black,
    Color(0x993C3C43), Color(0x4D3C3C43), Color(0x333C3C43),
    Color(0xFFF2F2F7), Color(0xFFE8E8ED), Color(0xFFFAFAFA).copy(alpha = 0.4f),
    Color(0xFFFCFCFE), Color(0xFFE3F0FF), Color(0xFF62626A), Color(0xFF0088FF))
internal val AmDarkColors = AmColors(true, Color.Black, Color.White,
    Color(0x99EBEBF5), Color(0x4DEBEBF5), Color(0x54545458),
    Color(0xFF1C1C1E), Color(0xFF38383A), Color(0xFF121212).copy(alpha = 0.4f),
    Color(0xFF242426), Color(0xFF163551), Color(0xFFBEBEC6), Color(0xFF0091FF))
internal val LocalAmColors = staticCompositionLocalOf { AmLightColors }

internal val LocalPlayerArtwork = staticCompositionLocalOf<ImageBitmap?> { null }

@Composable
internal fun AmTheme(content: @Composable () -> Unit) {
    val resources = LocalResources.current
    val artwork = remember(resources) { BitmapFactory.decodeResource(resources, R.drawable.yingyuanbang)?.asImageBitmap() }
    CompositionLocalProvider(LocalPlayerArtwork provides artwork,
        LocalAmColors provides if (isSystemInDarkTheme()) AmDarkColors else AmLightColors,
        content = content)
}
