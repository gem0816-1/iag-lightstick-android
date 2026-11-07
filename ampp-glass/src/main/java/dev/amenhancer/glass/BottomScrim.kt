package dev.amenhancer.glass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import dev.amenhancer.backdrop.VibrantBlur

private fun smoothstep(value: Float): Float {
    val t = value.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * Vertical ramp built from sampled values of [smoothstep]: fully faded at the top of the strip and
 * at full strength at the screen edge. Sampling keeps the curve identical in both gradients.
 */
private fun fadeRamp(alpha: Float, color: Color) = Brush.verticalGradient(
    *Array(GlassPolicy.BOTTOM_SCRIM_STOPS) { index ->
        val t = index / (GlassPolicy.BOTTOM_SCRIM_STOPS - 1f)
        t to color.copy(alpha = alpha * smoothstep(t))
    },
)

/**
 * Bottom fade behind the navigation strip: the scene under the tabs is blurred and washed out
 * toward the screen edge. The blur itself is masked by the same smooth curve, so the blurred
 * layer dissolves into the sharp content instead of ending on a hard edge.
 */
@Composable
fun BottomScrim(backdrop: Backdrop) {
    val scrim = if (isSystemInDarkTheme()) Color(0xFF121212) else Color(0xFFFAFAFA)
    // Keep the same fade and blur, but don't rebuild brushes/effect prefixes per draw.
    val mask = remember { fadeRamp(1f, Color.Black) }
    val wash = remember(scrim) { fadeRamp(GlassPolicy.BOTTOM_SCRIM_WASH_ALPHA, scrim) }
    val radiusPx = with(LocalDensity.current) { GlassPolicy.BOTTOM_SCRIM_BLUR_DP.dp.toPx() }
    val material = remember(radiusPx) { VibrantBlur(radiusPx) }
    val layerPaint = remember { Paint() }
    Spacer(
        Modifier.fillMaxSize().drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            effects = {
                material.applyTo(this)
            },
            highlight = null,
            shadow = null,
            onDrawBackdrop = { drawBackdrop ->
                // DstIn keeps the blurred backdrop where the mask is opaque, so the mask alpha
                // becomes the blur's own fade instead of a separate overlay.
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                    drawBackdrop()
                    drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    canvas.restore()
                }
            },
            onDrawSurface = {
                drawRect(brush = wash)
            },
        ),
    )
}
