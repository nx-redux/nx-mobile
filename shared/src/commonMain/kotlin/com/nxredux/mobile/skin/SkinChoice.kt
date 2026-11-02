package com.nxredux.mobile.skin

/**
 * Which of a pack's layouts a window gets, and that layout assembled to it.
 *
 * Landscape: the grip. Portrait DS: half-folded, the tablet layout on a tablet-class window and the flex layout on a
 * phone, the fold under the hinge; flat, the tablet layout on a tablet-class window, else the stacked phone layout on a
 * tall phone (at least 2:1) and the large-screen one on a shorter one, each falling back to the other when it does not
 * fit. Portrait one-screen consoles: the stacked layout, flat only (half-folded they keep the normal pad). Null when
 * nothing fits: the normal pad is used.
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
     */
    fun placeMenu(pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, topInset: Int = 0): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || windowW == windowH || densityDpi <= 0) return null
        val k = scaleFor(pack, densityDpi)
        val family = if (windowW > windowH) SkinFamily.GRIP else SkinFamily.STACKED
        // the tallest background that fits (a pack may render more than one, so a tall window stretches little)
        return pack.layouts.filter { it.family == family && it.variant == SkinVariant.MENU && it.frame != null }
            .sortedByDescending { it.height }
            .firstNotNullOfOrNull { spec ->
                SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, topInset = topInset.toFloat())?.takeIf { p ->
                    val g = p.glasses.single().rect
                    g.width / k >= MENU_MIN_GLASS && g.height / k >= MENU_MIN_GLASS
                }
            }
    }

    /** Window px per picture px: [densityDpi] over the pack's, 1 within [SNAP] of it (drawn 1:1, crisp). */
    private fun scaleFor(pack: SkinPackManifest, densityDpi: Int): Float =
        (densityDpi.toFloat() / pack.refDpi).let { if (kotlin.math.abs(it - 1f) < SNAP) 1f else it }

    /**
     * [windowW] x [windowH] px at [densityDpi]; [tablet]: a tablet-class window; [ds]: a DS game (else one screen of
     * [picture] game px); [hingeY]: the fold's middle in window px while half-folded, else null; [topInset]: window px at
     * the top the screen frame keeps clear of (the camera cutout; [SkinSolver.placePieces]).
     */
    fun place(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>? = null, hingeY: Float? = null, topInset: Int = 0,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || densityDpi <= 0) return null
        val k = scaleFor(pack, densityDpi)
        val variant = if (ds) SkinVariant.DS else SkinVariant.SINGLE
        val landscape = windowW > windowH
        val families = when {
            landscape -> listOf(SkinFamily.GRIP)
            !ds -> if (hingeY == null) listOf(SkinFamily.STACKED) else emptyList()
            hingeY != null -> listOf(if (tablet) SkinFamily.TABLET else SkinFamily.FLEX)
            tablet -> listOf(SkinFamily.TABLET, SkinFamily.LARGE)
            windowH.toFloat() / windowW >= TALL -> listOf(SkinFamily.STACKED, SkinFamily.LARGE)
            else -> listOf(SkinFamily.LARGE, SkinFamily.STACKED)
        }
        for (family in families) {
            val spec = pack.layouts.firstOrNull { it.family == family && it.variant == variant } ?: continue
            val hinge = hingeY.takeIf { family == SkinFamily.TABLET || family == SkinFamily.FLEX }
            val placed = if (spec.frame != null) SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat())
            else SkinSolver.place(spec, windowW.toFloat(), windowH.toFloat(), k, picture, hinge)
            placed?.let { return it }
        }
        return null
    }
}
