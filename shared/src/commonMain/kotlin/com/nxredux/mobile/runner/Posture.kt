package com.nxredux.mobile.runner

/** The device posture the game screen lays itself out for (Flex mode). Android supplies it; iOS reports [Flat]. */
sealed interface Posture {
    data object Flat : Posture
    /** Half-folded with a horizontal hinge; the hinge band's top and bottom edges in window px. */
    data class Tabletop(val hingeTop: Int, val hingeBottom: Int) : Posture
}

/** Either half must be at least this share of the window height, or the fold is treated as flat. */
private const val MIN_HALF_SHARE = 0.25f

/**
 * A FoldingFeature reduced to plain values: tabletop only when half-opened with a horizontal hinge whose band ([top],
 * [bottom], window px) lies inside the window and leaves both halves at least [MIN_HALF_SHARE] of [windowH].
 */
fun postureFrom(halfOpened: Boolean, horizontal: Boolean, top: Int, bottom: Int, windowH: Int): Posture {
    if (!halfOpened || !horizontal || windowH <= 0) return Posture.Flat
    if (top < 0 || bottom > windowH || bottom < top) return Posture.Flat
    val min = windowH * MIN_HALF_SHARE
    if (top < min || windowH - bottom < min) return Posture.Flat
    return Posture.Tabletop(top, bottom)
}

/** Which controls fill the half below the hinge. */
enum class TabletopPad { PORTRAIT, CLUSTERS, NONE }

/** The game area ([gameTop]..[gameBottom]) above the hinge and the pad area from [padTop] to the window bottom, px. */
data class TabletopLayout(val gameTop: Int, val gameBottom: Int, val padTop: Int, val pad: TabletopPad)

/**
 * The tabletop split for [posture] in a [windowW] x [windowH] window, or null when the screen keeps its flat layout
 * (flat, or a DS game). A narrow window (portrait) gets the portrait pad below the hinge, a wide one (landscape) the two
 * clusters; a connected controller leaves the lower half empty. The game starts below [cutoutTop] in portrait.
 */
fun tabletopLayout(
    posture: Posture, windowW: Int, windowH: Int, cutoutTop: Int,
    controllerConnected: Boolean, isDs: Boolean,
): TabletopLayout? {
    if (posture !is Posture.Tabletop || isDs) return null
    val portrait = windowH > windowW
    val pad = when {
        controllerConnected -> TabletopPad.NONE
        portrait -> TabletopPad.PORTRAIT
        else -> TabletopPad.CLUSTERS
    }
    return TabletopLayout(
        gameTop = if (portrait) cutoutTop else 0,
        gameBottom = posture.hingeTop,
        padTop = posture.hingeBottom,
        pad = pad,
    )
}

/**
 * The tabletop split of an area that starts at window y [areaTop] and is [areaW] x [areaH] (the main app's shell, inside
 * the system bars), for a [posture] in window px; null when flat or the hinge does not split the area into two halves of
 * at least the minimum share. Positions are area-relative; the main app has no DS layout.
 */
fun tabletopInArea(posture: Posture, areaTop: Int, areaW: Int, areaH: Int, controllerConnected: Boolean): TabletopLayout? {
    if (posture !is Posture.Tabletop) return null
    val local = postureFrom(halfOpened = true, horizontal = true, top = posture.hingeTop - areaTop, bottom = posture.hingeBottom - areaTop, windowH = areaH)
    return tabletopLayout(local, areaW, areaH, cutoutTop = 0, controllerConnected = controllerConnected, isDs = false)
}

/** The hinge angle range (degrees) read as half-opened when the fold itself reports flat: entered inside, left outside. */
private const val HINGE_ENTER_MIN = 30f
private const val HINGE_ENTER_MAX = 150f
private const val HINGE_LEAVE_MIN = 25f
private const val HINGE_LEAVE_MAX = 155f

/**
 * [postureFrom] with a hinge angle fallback, for foldables whose HALF_OPENED state is not visible to apps (the Fold
 * reports its feature flat at a laptop angle). A reported [halfOpened] wins. Otherwise a [hingeAngle] (degrees, null:
 * no sensor) in 30..150 reads as half-opened; once [previous] is tabletop it stays so until the angle leaves 25..155,
 * so the posture does not flicker at the edges.
 */
fun postureFromHinge(
    halfOpened: Boolean, horizontal: Boolean, top: Int, bottom: Int, windowH: Int,
    hingeAngle: Float?, previous: Posture,
): Posture {
    val half = halfOpened || hingeAngle != null && (
        if (previous is Posture.Tabletop) hingeAngle in HINGE_LEAVE_MIN..HINGE_LEAVE_MAX
        else hingeAngle in HINGE_ENTER_MIN..HINGE_ENTER_MAX
    )
    return postureFrom(half, horizontal, top, bottom, windowH)
}

/**
 * The posture a new window starts from, before its own reading arrives: [hint] (window px) as the menu's window last
 * read it for a [hintW] x [hintH] window, kept only when this window is the same size (same display, same orientation)
 * and the hinge still splits it ([postureFrom]); else [Posture.Flat], as without a hint.
 */
fun seededPosture(hint: Posture?, hintW: Int, hintH: Int, windowW: Int, windowH: Int): Posture {
    if (hint !is Posture.Tabletop || hintW != windowW || hintH != windowH) return Posture.Flat
    return postureFrom(halfOpened = true, horizontal = true, top = hint.hingeTop, bottom = hint.hingeBottom, windowH = windowH)
}
