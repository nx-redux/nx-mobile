package com.nxredux.mobile.skin

/**
 * Which of a pack's layouts a window gets, and that layout assembled to it.
 *
 * Landscape: the main menu's landscape shell, the game in its screen frame with the touch shortcut strip under it (a
 * DS's two screens stacked in it, MIC and SWAP beside MENU). Half-folded: the screen frame above the fold and the pad
 * below it (the flex layouts; a DS's touch screen below the fold with the pad), on a board with nothing but plates there.
 * Portrait DS flat: the large-screen layout on a tablet-class window or a shorter phone, the stacked one on a tall phone
 * (at least 2:1), each falling back to the other when it does not fit. Portrait one-screen consoles: the stacked layout.
 * A foldable's inner screen upright ([place]'s foldable): the fold board's layouts first, split at its hinge flat and
 * half-folded alike, so folding never changes the board. The main menu keeps the phone board everywhere.
 * Null when nothing fits: the normal pad is used.
 */
object SkinChoice {
    private const val TALL = 2.0f
    private const val SNAP = 0.12f
    /** The smallest menu glass side, picture px: less and the menu would not be usable in it. */
    private const val MENU_MIN_GLASS = 400f

    /**
     * One-screen consoles a skin draws (its d-pad, A B X Y, L R, SELECT START cover their pads): no analog stick, no C
     * buttons, no second pair of shoulders. Others keep the normal pad. The DS has its own layouts; the 3DS keeps the
     * normal pad.
     */
    private val SINGLE_TAGS = setOf(
        "GB", "SGB", "GBC", "GBA", "MGBA", "FC", "FDS", "SFC", "SUPA", "MD", "GPGX", "SMS", "GG", "SG1000", "SEGACD", "32X",
        "NGP", "NGPC", "PCE", "VB", "LYNX", "A2600", "A7800", "PKM", "WS", "WSC", "COLECO", "FBN",
    )

    /** Whether a skin draws the pad for the console [baseTag] (upper case, GPGX prefix dropped): the DS or [SINGLE_TAGS]. */
    fun skinnable(baseTag: String, ds: Boolean): Boolean = ds || baseTag in SINGLE_TAGS

    /**
     * The main menu's pad in a [windowW] x [windowH] px area at [densityDpi], built from pieces. Portrait: the stacked
     * menu layout, its buttons at their own size along the bottom and its screen frame (where the menu goes) over the
     * room above them. Landscape: the grip menu layout, one shell with the buttons in a column each side and the screen
     * frame between them. Null when the pack has no such layout or the area is too small for it (the drawn pad, or
     * none, then). [topInset]: window px at the top the screen frame keeps clear of ([SkinSolver.placePieces]).
     * [foldable]: a device that half-folds (its inner screen may take the fold board's layout).
     */
    fun placeMenu(pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, topInset: Int = 0, foldable: Boolean = false): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || windowW == windowH || densityDpi <= 0) return null
        val k = scaleFor(pack, densityDpi)
        val family = if (windowW > windowH) SkinFamily.GRIP else SkinFamily.STACKED
        return shell(pack, family, SkinVariant.MENU, windowW, windowH, k, null, topInset, foldable)
    }

    /**
     * The tallest of [family]'s [variant] layouts built from pieces that fits (a pack may render more than one, so a tall
     * window stretches little), with a usable glass; a game's [picture] fitted in it.
     */
    private fun shell(
        pack: SkinPackManifest, family: SkinFamily, variant: SkinVariant, windowW: Int, windowH: Int, k: Float,
        picture: Pair<Float, Float>?, topInset: Int, foldable: Boolean = false,
    ): SkinPlacement? =
        pack.layouts.filter { it.family == family && it.variant == variant && it.frame != null && (foldable || !it.foldBoard) }
            .sortedByDescending { it.height }
            .firstNotNullOfOrNull { spec ->
                SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset = topInset.toFloat())?.takeIf { p ->
                    val g = p.glasses.first().rect
                    g.width / k >= MENU_MIN_GLASS && g.height / k >= MENU_MIN_GLASS
                }
            }

    /**
     * The first of [family]'s [variant] layouts, widest background first, that [placed] places: a big window gets the
     * board rendered for it (a background only grows, so a smaller window takes a smaller one).
     */
    private fun widest(pack: SkinPackManifest, family: SkinFamily, variant: SkinVariant, fold: Boolean, placed: (SkinLayoutSpec) -> SkinPlacement?): SkinPlacement? =
        pack.layouts.filter { it.family == family && it.variant == variant && it.foldBoard == fold }.sortedByDescending { it.width }.firstNotNullOfOrNull(placed)

    /** A layout on a foldable's board: its background has a hinge (an inner screen's two halves). */
    private val SkinLayoutSpec.foldBoard: Boolean get() = tree.parts.any { it.isHinge }

    /** Window px per picture px: [densityDpi] over the pack's, 1 within [SNAP] of it (drawn 1:1, crisp). */
    private fun scaleFor(pack: SkinPackManifest, densityDpi: Int): Float =
        (densityDpi.toFloat() / pack.refDpi).let { if (kotlin.math.abs(it - 1f) < SNAP) 1f else it }

    /**
     * [windowW] x [windowH] px at [densityDpi]; [tablet]: a tablet-class window; [ds]: a DS game (else one screen of
     * [picture] game px); [hingeY]: the fold's middle in window px while half-folded, else null; [topInset]: window px at
     * the top the screen frame keeps clear of (the camera cutout; [SkinSolver.placePieces]); [foldable]: a device that
     * half-folds.
     */
    fun place(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>? = null, hingeY: Float? = null, topInset: Int = 0, foldable: Boolean = false,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || densityDpi <= 0) return null
        val k = scaleFor(pack, densityDpi)
        val variant = if (ds) SkinVariant.DS else SkinVariant.SINGLE
        val landscape = windowW > windowH
        // sideways: the main menu's landscape shell, the game in its screen frame (the strip under it; a DS's MIC, SWAP)
        if (landscape) return shell(pack, SkinFamily.GRIP, variant, windowW, windowH, k, picture, topInset)
        // half-folded: the screen frame above the fold, the pad below it (a DS's touch screen with it)
        // a foldable's inner screen: the fold board, split at its hinge flat or not (the window's middle when flat)
        if (foldable) widest(pack, SkinFamily.FLEX, variant, fold = true) { spec ->
            SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), hingeY ?: (windowH / 2f))
        }?.let { return it }
        if (hingeY != null) return widest(pack, SkinFamily.FLEX, variant, fold = false) { spec ->
            if (spec.frame?.hinge?.let { it >= 0 } != true) null
            else SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), hingeY)
        }
        val families = when {
            !ds -> listOf(SkinFamily.STACKED)
            tablet -> listOf(SkinFamily.LARGE, SkinFamily.STACKED)
            windowH.toFloat() / windowW >= TALL -> listOf(SkinFamily.STACKED, SkinFamily.LARGE)
            else -> listOf(SkinFamily.LARGE, SkinFamily.STACKED)
        }
        for (family in families) {
            widest(pack, family, variant, fold = false) { spec ->
                if (spec.frame != null) SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat())
                else SkinSolver.place(spec, windowW.toFloat(), windowH.toFloat(), k, picture)
            }?.let { return it }
        }
        return null
    }
}
