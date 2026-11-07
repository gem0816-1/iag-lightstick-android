package dev.amenhancer.glass

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/** Shared motion roles. Parameters retain this project's existing Backdrop/AM springs;
 * Coolapk's packed business bytecode does NOT establish its numerical spring parameters.
 * Compose supplies cancellation, frame timing and the system's MotionDurationScale.
 */
object LiquidMotion {
    fun <T> position(threshold: T? = null): SpringSpec<T> = spring(1f, 1000f, threshold)
    fun <T> feedback(threshold: T? = null): SpringSpec<T> = spring(0.5f, 300f, threshold)
    fun <T> deformationX(threshold: T? = null): SpringSpec<T> = spring(0.6f, 250f, threshold)
    fun <T> deformationY(threshold: T? = null): SpringSpec<T> = spring(0.7f, 250f, threshold)
    fun <T> panel(threshold: T? = null): SpringSpec<T> = spring(1f, 420f, threshold)
    fun <T> artwork(threshold: T? = null): SpringSpec<T> = spring(0.86f, 300f, threshold)
    fun <T> button(threshold: T? = null): SpringSpec<T> = spring(0.6f, 600f, threshold)
    fun <T> recenter(threshold: T? = null): SpringSpec<T> = spring(1f, 300f, threshold)
    fun collapse(expanding: Boolean): SpringSpec<Float> = spring(
        GlassBarCollapseSprings.dampingRatio(if (expanding) GlassBarCollapseSprings.ExpandBounce else GlassBarCollapseSprings.CollapseBounce),
        GlassBarCollapseSprings.Stiffness, 0.001f)
    fun <T> enter(): TweenSpec<T> = tween(160)
    fun <T> exit(): TweenSpec<T> = tween(140)
}

/** Verified packaged resource values, not claims about runtime placement or colors. */
object CoolapkNavigationMetrics {
    const val HeightDp = 56
    const val ContentPaddingDp = 4
}
