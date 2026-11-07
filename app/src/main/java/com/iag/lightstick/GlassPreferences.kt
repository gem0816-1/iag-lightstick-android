package com.iag.lightstick

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntSize

/** AM++ settings ranges/defaults, independent of Android rendering. */
@Immutable
data class GlassSettings(
    val enabled: Boolean = true,
    val collapseEnabled: Boolean = true,
    val bottomGapDp: Int = 16,
    val blurDp: Int = 4,
    val collapseRebound: Int = 35,
    val expandRebound: Int = 100,
    val miniRebound: Int = 100,
    val miniSinkDp: Int = 4,
) {
    fun normalized() = copy(bottomGapDp = bottomGapDp.coerceIn(0, 48),
        blurDp = blurDp.coerceIn(0, 24), collapseRebound = collapseRebound.coerceIn(0, 200),
        expandRebound = expandRebound.coerceIn(0, 200), miniRebound = miniRebound.coerceIn(0, 200),
        miniSinkDp = miniSinkDp.coerceIn(0, 16))

    fun save(context: Context) {
        val s = normalized()
        context.getSharedPreferences("glass", Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled).putBoolean("collapse", s.collapseEnabled)
            .putInt("bottomGap", s.bottomGapDp).putInt("blur", s.blurDp)
            .putInt("collapseRebound", s.collapseRebound).putInt("expandRebound", s.expandRebound)
            .putInt("miniRebound", s.miniRebound).putInt("miniSink", s.miniSinkDp).apply()
    }

    companion object {
        fun load(context: Context): GlassSettings {
            val p = context.getSharedPreferences("glass", Context.MODE_PRIVATE)
            return GlassSettings(p.getBoolean("enabled", true), p.getBoolean("collapse", true),
                p.getInt("bottomGap", 16), p.getInt("blur", 4), p.getInt("collapseRebound", 35),
                p.getInt("expandRebound", 100), p.getInt("miniRebound", 100), p.getInt("miniSink", 4)).normalized()
        }
    }
}

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }
val LocalMiniLayerSize = staticCompositionLocalOf { IntSize.Zero }
