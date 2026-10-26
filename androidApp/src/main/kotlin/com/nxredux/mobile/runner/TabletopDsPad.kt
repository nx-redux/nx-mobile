package com.nxredux.mobile.runner

import com.nxredux.mobile.layout.TabletopDsPlacement
import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions
import kotlin.math.roundToInt

/**
 * The half-folded DS / 3DS pad halves' boxes, root px: each [widthPx] x [heightPx] at [topMarginPx] from the root's top,
 * against its own side edge (the right half mirrors the left); [insetPx] moves the buttons in from that edge.
 */
internal data class TabletopDsPadBoxes(val widthPx: Int, val heightPx: Int, val topMarginPx: Int, val insetPx: Float)

/**
 * The pad halves' boxes for placement [p] (window px; the root fills the window) in a root [rootH] tall, kept inside it;
 * null when [p] has no halves (a controller, or both screens stacked above the hinge with the portrait pad below).
 */
internal fun tabletopDsPadBoxes(p: TabletopDsPlacement, rootH: Int): TabletopDsPadBoxes? {
    val half = p.left ?: return null
    val h = half.boxHeightPx.roundToInt().coerceAtMost(rootH)
    val top = half.topMarginPx.roundToInt().coerceIn(0, rootH - h)
    return TabletopDsPadBoxes(half.boxWidthPx.roundToInt(), h, top, half.extraInsetPx)
}

/**
 * What a tabletop layout was built for: [halfFolded] (a tabletop split) and its [pad] (null when flat), a [controller] in
 * the layout, the root's size.
 */
internal data class TabletopBuild(val halfFolded: Boolean, val pad: TabletopPad?, val controller: Boolean, val rootW: Int, val rootH: Int)

/**
 * Whether the layout built for [old] must be laid out again now that it is [new]: while half-folded (before or after) any
 * change (the posture, the pad below the hinge, as when the game's console becomes known, a controller coming or going,
 * a new root size) does; a flat layout's changes are the flat layout's own (rotation, the game area's size). Nothing
 * built yet ([old] null): the first layout is still to come.
 */
internal fun tabletopRelayoutNeeded(old: TabletopBuild?, new: TabletopBuild): Boolean =
    old != null && (old.halfFolded || new.halfFolded) && old != new

/**
 * Whether a game of [tag] gets the DS / 3DS tabletop (the screens across the hinge, [TabletopPad.DS]). Read from the
 * layout's tag ([PadLayoutHost.layoutTag]: the intent's until the launch sets the game's), so onCreate's first layout,
 * seeded half-folded, is already the DS one.
 */
internal fun tabletopDualScreen(tag: String): Boolean = NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag)
