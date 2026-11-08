package com.nxredux.mobile.runner

/**
 * 3DS SWAP done by the app while it draws the screens from Azahar's video view map (the core stays unswapped): the
 * player's Swap Screen Mode as Azahar applies it (Toggle flips on each press, Hold swaps while held), starting from
 * their stored starting screen.
 */
object N3dsSwap {
    private const val SWAP_SCREEN = "citra_swap_screen"
    private const val TOP_HEIGHT = 240

    fun initial(values: Map<String, String>): Boolean = values[SWAP_SCREEN] == "Bottom"

    fun next(swapped: Boolean, mode: String, down: Boolean): Boolean =
        if (mode == "Hold") down else if (down) !swapped else swapped

    /** Azahar's resolution factor from its map's top screen height (unswapped, 240 px at 1x). */
    fun factorFromTopHeight(heightPx: Int): Int = (heightPx / TOP_HEIGHT).coerceAtLeast(1)
}
