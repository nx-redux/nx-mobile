package com.nxredux.mobile.skin

/**
 * This placement as the overlay over a full-screen game: its buttons and the strip's labels, no board ([SkinPlacement.cells]),
 * screen frame or piece holding a screen. The controls stay as placed; the glasses too, though nothing is drawn in them
 * (a touch on no button reaches the game anywhere).
 */
fun SkinPlacement.bare(): SkinPlacement = copy(
    cells = emptyList(),
    pieces = pieces.filter { p ->
        when {
            p.strip -> true
            p.piece < 0 -> false   // the screen frame's slices
            else -> spec.pieces.getOrNull(p.piece)?.glass == null
        }
    },
)

/** What the game draws over itself for a pad: the whole [SKIN], its [BARE] buttons alone, or [NONE] (a controller). */
enum class OverlayMode { SKIN, BARE, NONE }

/**
 * The pad over the game: none with a [controller]; the whole skin when Pad skin is on ([padSkinOn]) and a layout is
 * [placed] for the window; else the skin's bare buttons over the full-screen game.
 */
fun overlayMode(padSkinOn: Boolean, placed: Boolean, controller: Boolean): OverlayMode = when {
    controller -> OverlayMode.NONE
    padSkinOn && placed -> OverlayMode.SKIN
    else -> OverlayMode.BARE
}
