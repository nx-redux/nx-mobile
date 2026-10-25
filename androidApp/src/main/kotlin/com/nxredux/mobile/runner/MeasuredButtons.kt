package com.nxredux.mobile.runner

import androidx.compose.ui.geometry.Rect
import com.nxredux.mobile.layout.LayoutConstants
import com.nxredux.mobile.settings.PadGroup
import kotlin.math.abs

/**
 * The landscape pad's buttons as drawn, from each half's group reports ([com.nxredux.mobile.ui.nx.PadGroupBoundsListener]:
 * placed rects after the Pad layout editor's moves and scales, in the half's own view px): how far each group reaches in
 * from its half's content edge, less the 0.2-unit edge nudge the reach's margin already counts. The pad's width is the
 * wider half's, so both halves keep one reach and the picture stays centred.
 */
internal class MeasuredButtons {
    private val left = mutableMapOf<PadGroup, Float>()
    private val right = mutableMapOf<PadGroup, Float>()

    /** The width last taken by [accept]; null until both halves have reported a group. */
    var accepted: Float? = null
        private set

    /** A new pad: nothing measured yet. */
    fun clear() {
        left.clear()
        right.clear()
        accepted = null
    }

    /** A left-half group at [placed] in a pad whose content box is [pad]; [Rect.Zero] (the group left the pad) forgets it. */
    fun onLeft(group: PadGroup, placed: Rect, pad: Rect, unitPx: Float) =
        record(left, group, placed, placed.right - pad.left, unitPx)

    /** As [onLeft], for the right half (its buttons reach in from its right edge). */
    fun onRight(group: PadGroup, placed: Rect, pad: Rect, unitPx: Float) =
        record(right, group, placed, pad.right - placed.left, unitPx)

    private fun record(side: MutableMap<PadGroup, Float>, group: PadGroup, placed: Rect, extent: Float, unitPx: Float) {
        if (placed == Rect.Zero) side.remove(group)
        else side[group] = (extent - LayoutConstants.EDGE_NUDGE_UNITS * unitPx).coerceAtLeast(0f)
    }

    /** The wider half's buttons width, px; null until both halves have a group. */
    fun widthPx(): Float? {
        val l = left.values.maxOrNull() ?: return null
        val r = right.values.maxOrNull() ?: return null
        return maxOf(l, r)
    }

    /**
     * Takes [widthPx] as [accepted] when it is the first or has moved at least [MIN_CHANGE_PX] from it: true then, and
     * the picture should be laid out again. Smaller moves (sub-pixel layout noise) keep the old width, so nothing jumps.
     */
    fun accept(): Boolean {
        val now = widthPx() ?: return false
        val old = accepted
        if (old != null && abs(now - old) < MIN_CHANGE_PX) return false
        accepted = now
        return true
    }

    companion object {
        const val MIN_CHANGE_PX = 2f
    }
}
