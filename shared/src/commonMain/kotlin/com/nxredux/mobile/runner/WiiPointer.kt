package com.nxredux.mobile.runner

import kotlin.math.abs
import kotlin.math.sign

/**
 * A right stick moving the Wii's IR pointer: a full picture width per second at full tilt. One axis at a time, so the
 * per-event path allocates nothing.
 */
object WiiPointer {
    const val DEAD_ZONE = 0.15f

    /** One stick axis' pointer speed, in picture widths (or heights) per second; 0 inside [DEAD_ZONE]. */
    fun velocity(v: Float): Float {
        val a = abs(v).coerceAtMost(1f)
        if (a <= DEAD_ZONE) return 0f
        return sign(v) * (a - DEAD_ZONE) / (1f - DEAD_ZONE)
    }
}
