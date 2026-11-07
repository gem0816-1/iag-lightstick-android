package com.iag.lightstick

import kotlin.math.exp
import kotlin.math.ln

/** Pure geometry for the mini-to-player surface; layout is measured only at its endpoints. */
internal object PlayerMotion {
    fun progress(value: Float) = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

    /** AM++ FragmentPhoneGlassPolicy.navigationExit, independent of dock collapse. */
    fun navigationExit(value: Float, extent: Float) =
        (1f - exp(-20f * progress(value))) * extent.coerceAtLeast(0f)

    /** AM++ PhoneGlassSession: glass fades between 35% and 60% expansion. */
    fun miniAlpha(value: Float): Float {
        val t = ((progress(value) - 0.35f) / 0.25f).coerceIn(0f, 1f)
        return 1f - t * t * (3f - 2f * t)
    }

    /** Apple PlayerMainFragment.i.d: chrome fades separately from the shared artwork. */
    fun controlsAlpha(value: Float): Float {
        val p = progress(value)
        return if (p == 0f) 0f else (1f + 0.5f * ln(p)).coerceIn(0f, 1f)
    }

    /** Dragging and automatic transitions use a single expansion coordinate. */
    fun dragProgress(value: Float, deltaPx: Float, travelPx: Float): Float =
        if (!deltaPx.isFinite() || !travelPx.isFinite() || travelPx <= 0f) progress(value)
        else progress(progress(value) - deltaPx / travelPx)

    data class ArtworkFrame(val x: Float, val y: Float, val scaleX: Float, val scaleY: Float)
    fun artwork(value: Float, sourceX: Float, sourceY: Float, sourceWidth: Float, sourceHeight: Float,
        targetX: Float, targetY: Float, targetWidth: Float, targetHeight: Float, targetScale: Float): ArtworkFrame {
        val p = progress(value)
        val endWidth = targetWidth * targetScale
        val endHeight = targetHeight * targetScale
        val endX = targetX + (targetWidth - endWidth) / 2f
        val endY = targetY + (targetHeight - endHeight) / 2f
        return ArtworkFrame(sourceX + (endX - sourceX) * p, sourceY + (endY - sourceY) * p,
            (sourceWidth + (endWidth - sourceWidth) * p) / targetWidth.coerceAtLeast(1f),
            (sourceHeight + (endHeight - sourceHeight) * p) / targetHeight.coerceAtLeast(1f))
    }
    data class Frame(val x: Float, val y: Float, val scaleX: Float, val scaleY: Float, val corner: Float)
    fun frame(progress: Float, width: Float, height: Float,
        left: Float, top: Float, miniWidth: Float, miniHeight: Float): Frame {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 1f
        if (width <= 0f || height <= 0f || miniWidth <= 0f || miniHeight <= 0f)
            return Frame(0f, height.coerceAtLeast(0f) * (1f - p), 1f, 1f, 0f)
        return Frame(left * (1f - p), top * (1f - p),
            miniWidth / width + (1f - miniWidth / width) * p,
            miniHeight / height + (1f - miniHeight / height) * p, miniHeight / 2f * (1f - p))
    }
    fun contentAlpha(progress: Float) = if (progress.isFinite()) ((progress - 0.25f) / 0.55f).coerceIn(0f, 1f) else 1f
    /**
     * Apple Music player sheet: the surface rises from the mini-player's top edge instead of
     * scaling the whole player down, so text and controls keep their final size throughout.
     */
    fun sheetOffset(progress: Float, miniTop: Float): Float {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 1f
        return (if (miniTop.isFinite()) miniTop.coerceAtLeast(0f) else 0f) * (1f - p)
    }
    /** Rounded sheet corners while rising; square once the player fills the screen. */
    fun sheetCorner(progress: Float): Float {
        val p = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 1f
        return 16f * (1f - p)
    }
    fun shouldDismiss(distancePx: Float, velocityPx: Float, density: Float) =
        distancePx > 96f * density || (distancePx > 0f && velocityPx > 900f * density)
    fun cover(widthDp: Float, heightDp: Float, fontScale: Float): Float =
        minOf((widthDp - 48f).coerceAtLeast(0f), (heightDp - 390f * fontScale.coerceAtLeast(1f)).coerceAtLeast(112f), 400f)
}
