package com.nxredux.mobile.runner

import com.nxredux.mobile.options.N3dsOptions
import com.nxredux.mobile.options.NdsOptions

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
 * seeded half-folded, is already the DS one. Not with a second screen in use ([dualMode] other than OFF): the Dual
 * screen layouts stay the DS / 3DS's own, in the generic tabletop's game area.
 */
internal fun tabletopDualScreen(tag: String, dualMode: DualMode): Boolean =
    dualMode == DualMode.OFF && (NdsOptions.appliesTo(tag) || N3dsOptions.appliesTo(tag))
