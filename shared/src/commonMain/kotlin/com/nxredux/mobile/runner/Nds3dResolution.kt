package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.ScreenClass
import com.nxredux.mobile.options.NdsLayoutChoice
import com.nxredux.mobile.options.NdsSettings
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.options.offers
import kotlin.math.ceil
import kotlin.math.min

/** How the DS 3D Resolution is picked: [AUTO] follows the shown size up to the Max Scale, the others ignore the layout's cap. */
enum class Nds3dMode(val value: String, val label: String) {
    AUTO("auto", "Auto"), QUALITY("quality", "Quality"), PERFORMANCE("performance", "Performance");

    companion object {
        /** The mode stored as [v]; unknown or missing reads as [AUTO]. */
        fun parse(v: String?): Nds3dMode = entries.firstOrNull { it.value == v } ?: AUTO
    }
}

/** The internal resolution (whole scale of 256x192) the melonDS GL renderer draws the 3D at, from the layout and the display. */
object Nds3dResolution {
    /** The most the core's resolution option offers. */
    const val CORE_MAX = 8

    /** The Max Scale an unset game starts with. */
    const val DEFAULT_CAP = 4

    /** The scale of the biggest non-inset screen in [regions] (a part-way scale rounds up); null when there is none. */
    fun currentScale(regions: List<NdsRegion>): Int? = regions.filterNot { it.inset }
        .maxOfOrNull { ceil(it.dst.width / NdsLayout.FRAME_WIDTH - 0.001f).toInt() }

    /**
     * The biggest scale any layout offered on this display reaches, either way up (at least 1): what Quality can use and
     * what Auto and Performance are measured against.
     */
    fun displayMax(windowW: Float, windowH: Float, screen: ScreenClass): Int {
        var best = 1
        for ((w, h) in listOf(windowW to windowH, windowH to windowW)) {
            val landscape = w > h
            for (layout in NdsLayoutChoice.entries) {
                if (!offers(layout, landscape, screen)) continue
                val regions = NdsLayout.regions(PxRect(0f, 0f, w, h), layout, landscape, screen, NdsSettings(), 0f)
                best = maxOf(best, currentScale(regions) ?: 1)
            }
        }
        return best
    }

    /** Performance never goes above half the [displayMax], rounded up. */
    fun performanceCeiling(displayMax: Int): Int = ceil(displayMax / 2.0).toInt().coerceIn(1, CORE_MAX)

    /**
     * The scale to send for [mode]: [current] is the shown scale (null: no screens placed yet, so [displayMax] stands in),
     * [maxScale] the Auto cap and [scale] the Performance pick.
     */
    fun value(mode: Nds3dMode, maxScale: Int, scale: Int, current: Int?, displayMax: Int): Int {
        val v = when (mode) {
            Nds3dMode.AUTO -> minOf(current ?: displayMax, maxScale, displayMax)
            Nds3dMode.QUALITY -> min(current ?: displayMax, displayMax)
            Nds3dMode.PERFORMANCE -> min(scale, performanceCeiling(displayMax))
        }
        return v.coerceIn(1, CORE_MAX)
    }
}

/**
 * Decides when a new resolution goes to the core. A change from a window resize ([offer] with sizeChange) waits until it has
 * stopped for [settleMs] (a resize drags through many sizes); any other change goes at once. It never returns the value it
 * last returned.
 */
class Nds3dSender(private val settleMs: Long = 300) {
    private var last: Int? = null
    private var pending: Int? = null
    private var pendingAt = 0L

    /** A value to send now for [value], or null (unchanged, or held to settle after a size change). */
    fun offer(value: Int, sizeChange: Boolean, nowMs: Long): Int? {
        if (sizeChange) {
            pending = value
            pendingAt = nowMs
            return null
        }
        pending = null
        return send(value)
    }

    /** The pending value once it has settled, else null. */
    fun tick(nowMs: Long): Int? {
        val value = pending ?: return null
        if (nowMs - pendingAt < settleMs) return null
        pending = null
        return send(value)
    }

    private fun send(value: Int): Int? = if (value == last) null else value.also { last = it }
}
