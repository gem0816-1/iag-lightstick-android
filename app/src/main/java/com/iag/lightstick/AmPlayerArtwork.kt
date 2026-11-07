package com.iag.lightstick

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stable project artwork; the bitmap is decoded once by the host theme. */
@Composable
internal fun AmPlayerArtwork(colorHex: String, modifier: Modifier = Modifier, inset: Dp = 1.dp) {
    val led = remember(colorHex) { runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Gray) }
    val field = remember(led) { Brush.linearGradient(listOf(lerp(Color(0xFF61616B), led, 0.35f), lerp(Color(0xFF292A33), led, 0.2f))) }
    Box(modifier.background(field), contentAlignment = Alignment.Center) {
        LocalPlayerArtwork.current?.let { Image(it, null, Modifier.fillMaxSize().padding(inset), contentScale = ContentScale.Fit) }
    }
}
