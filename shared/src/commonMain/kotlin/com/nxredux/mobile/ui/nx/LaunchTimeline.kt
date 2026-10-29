package com.nxredux.mobile.ui.nx

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutCubic
import kotlin.math.hypot
import kotlin.math.max

/**
 * Timing for the launch sequence: a feathered circle spreads from the selected name, the name's
 * colour follows, the game screen holds the name on black until its first frame, then fades the name.
 * A refused launch retreats the spread instead; on the way back from the game the menu's black fades out.
 * Pure numbers and curves, no Compose state, so the timeline can be tested directly.
 */
object LaunchTimeline {
    /** How long the circle takes to reach the far corner. */
    const val SPREAD_MS = 450L

    /** Soft edge of the circle, in dp. */
    const val FEATHER_DP = 90f

    /** Colour waits this long after the spread starts. */
    const val COLOR_DELAY_MS = 80L

    /** Colour fade length, after the delay. */
    const val COLOR_MS = 400L

    /** The game screen's hold: at least this long on black with the name, longer while the game has not drawn. */
    const val HOLD_MS = 400L

    /** The hold's name fades out in this time, once the game drew and the hold's minimum is up. */
    const val NAME_FADE_MS = 180L

    /** A refused launch (the game never opened): the spread runs backwards over the menu in this time. */
    const val RETREAT_MS = 300L

    /** Back from the game: the menu overlay's black fades out in this time (the game screen's close fade too). */
    const val RETURN_FADE_MS = 150L

    /** Fade used instead of the spread when reduced motion is on. */
    const val REDUCED_FADE_MS = 150L

    /**
     * Stop waiting for the game's first frame after this long and reveal anyway. Above the launch's wait for a previous
     * game's process to end (10 s, then 1 s after a kill).
     */
    const val GIVE_UP_MS = 15_000L
}

private val ColorEase = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Radius that just covers the farthest corner from (cx, cy) inside a w×h box,
 * plus the feather, so the soft edge leaves the screen as the circle finishes.
 */
fun launchEndRadius(cx: Float, cy: Float, w: Float, h: Float, featherPx: Float): Float {
    val far = hypot(max(cx, w - cx), max(cy, h - cy))
    return far + featherPx
}

/** Circle radius at [elapsedMs]: ease-out cubic over [LaunchTimeline.SPREAD_MS], clamped to [endRadius]. */
fun launchRadius(elapsedMs: Long, endRadius: Float): Float {
    val t = (elapsedMs.coerceIn(0L, LaunchTimeline.SPREAD_MS).toFloat() / LaunchTimeline.SPREAD_MS)
    return EaseOutCubic.transform(t) * endRadius
}

/**
 * Colour mix (0..1) at [elapsedMs]: nothing for [LaunchTimeline.COLOR_DELAY_MS],
 * then a symmetric ease-in-out over [LaunchTimeline.COLOR_MS].
 */
fun launchColorFraction(elapsedMs: Long): Float {
    val into = elapsedMs - LaunchTimeline.COLOR_DELAY_MS
    if (into <= 0L) return 0f
    if (into >= LaunchTimeline.COLOR_MS) return 1f
    return ColorEase.transform(into.toFloat() / LaunchTimeline.COLOR_MS)
}
