package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect

/**
 * The in-game menu's room over this skin when it sits in [glass]: the glass and the touch shortcut strip right under it
 * (the strip hides while the menu is open, so the menu takes its band down to the frame's foot). A strip elsewhere (the
 * DS's, under its touch screen) leaves the glass as it is.
 */
fun SkinPlacement.menuRect(glass: PlacedGlass): PxRect {
    val g = glass.rect
    val bottom = g.y + g.height
    val under = controls.filter { c ->
        c.spec.id.startsWith(STRIP_SLOT) && c.x >= g.x && c.x <= g.x + g.width &&
            c.y > bottom && c.y - c.spec.hh - bottom <= 2f * c.spec.hh   // right under it, not past another screen
    }
    if (under.isEmpty()) return g
    return PxRect(g.x, g.y, g.width, under.maxOf { it.y + it.spec.hh } - g.y)
}

/** The touch shortcut slots' control ids: TOUCH_1..TOUCH_4. */
private const val STRIP_SLOT = "TOUCH_"
