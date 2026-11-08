package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect

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

/**
 * A touch shortcut's pill on the overlay (with no frame its word needs a ground), window px: centred on the slot at
 * [cx], [cy] ([slotHw] x [slotHh] its half size), [STRIP_PILL_TALL] of its height, round its [textW] word with
 * [STRIP_PILL_PAD] each side, at least a pill's [STRIP_PILL_MIN_SHAPE] width, and within the slot less [STRIP_PILL_GAP].
 * [k]: window px per picture px.
 */
fun stripPill(cx: Float, cy: Float, slotHw: Float, slotHh: Float, textW: Float, k: Float): PxRect {
    val h = 2f * slotHh * STRIP_PILL_TALL
    val w = minOf(maxOf(textW + 2f * STRIP_PILL_PAD * k, h * STRIP_PILL_MIN_SHAPE), 2f * slotHw - STRIP_PILL_GAP * k)
    return PxRect(cx - w / 2f, cy - h / 2f, w, h)
}

private const val STRIP_PILL_TALL = 0.8f
private const val STRIP_PILL_PAD = 24f
private const val STRIP_PILL_MIN_SHAPE = 1.6f
private const val STRIP_PILL_GAP = 12f
