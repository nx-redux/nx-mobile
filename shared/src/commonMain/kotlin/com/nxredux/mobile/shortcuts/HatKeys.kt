package com.nxredux.mobile.shortcuts

import com.nxredux.mobile.ui.nx.AxisDirections

/**
 * A controller's hat (AXIS_HAT_X / AXIS_HAT_Y) as d-pad key presses and releases, for the shortcuts, which match key
 * codes: most pads send their d-pad only as the hat, never as `KEYCODE_DPAD_*` keys. A release comes before the press
 * on the same axis (Left straight to Right).
 */
class HatKeys {
    @PublishedApi internal var x = 0
    @PublishedApi internal var y = 0

    /** The hat now reads ([hatX], [hatY]): [onKey] gets each direction that went down or up since the last read. */
    inline fun update(hatX: Float, hatY: Float, onKey: (keyCode: Int, down: Boolean) -> Unit) {
        val nx = AxisDirections.step(hatX)
        val ny = AxisDirections.step(hatY)
        if (nx != x) {
            code(x, KeyCodes.DPAD_LEFT, KeyCodes.DPAD_RIGHT)?.let { onKey(it, false) }
            x = nx
            code(nx, KeyCodes.DPAD_LEFT, KeyCodes.DPAD_RIGHT)?.let { onKey(it, true) }
        }
        if (ny != y) {
            code(y, KeyCodes.DPAD_UP, KeyCodes.DPAD_DOWN)?.let { onKey(it, false) }
            y = ny
            code(ny, KeyCodes.DPAD_UP, KeyCodes.DPAD_DOWN)?.let { onKey(it, true) }
        }
    }

    /** Forgets the hat's last reading (its release went elsewhere): the next direction read is a press. */
    fun reset() { x = 0; y = 0 }

    @PublishedApi internal fun code(dir: Int, negative: Int, positive: Int): Int? = when (dir) {
        -1 -> negative
        1 -> positive
        else -> null
    }
}
