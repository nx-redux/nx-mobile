package com.nxredux.mobile.skin

/**
 * Which of a pack's layouts a window gets, and that layout assembled to it.
 *
 * Landscape: the main menu's landscape shell, the game in its screen frame with the touch shortcut strip under it (a
 * DS's two screens stacked in it, MIC and SWAP beside MENU). Half-folded: the screen frame above the fold and the pad
 * below it (the flex layouts; a DS's touch screen below the fold with the pad), on a board with nothing but plates there.
 * Portrait DS flat: the large-screen layout on a tablet-class window or a shorter phone, the stacked one on a tall phone
 * (at least 2:1), each falling back to the other when it does not fit. Portrait one-screen consoles: the stacked layout.
 * A foldable's inner screen or a tablet upright ([place]'s foldable, tablet): the fold board's layouts first, split at its
 * hinge (the window's middle when flat), so folding or opening a game never changes the board. Its main menu is two
 * screens there ([placeMenu]'s tablet): the top screen in a frame above the hinge, the menu in a touch screen below. A
 * Flip's main screen is too narrow for the fold board and keeps the stacked layouts. The main menu elsewhere keeps the
 * phone board.
 * Null when nothing fits: the normal pad is used.
 */
object SkinChoice {
    private const val TALL = 2.0f
    private const val SNAP = 0.12f
    /** The smallest menu glass side, picture px: less and the menu would not be usable in it. */
    private const val MENU_MIN_GLASS = 400f

    /**
     * One-screen consoles a skin draws (its d-pad, A B X Y, L R, SELECT START cover their pads): no analog stick, no C
     * buttons, no second pair of shoulders. Others keep the normal pad. The DS and the 3DS have their own layouts.
     */
    private val SINGLE_TAGS = setOf(
        "GB", "SGB", "GBC", "GBA", "MGBA", "FC", "FDS", "SFC", "SUPA", "MD", "GPGX", "SMS", "GG", "SG1000", "SEGACD", "32X",
        "NGP", "NGPC", "PCE", "VB", "LYNX", "A2600", "A7800", "PKM", "WS", "WSC", "COLECO", "FBN",
    )

    /**
     * The skin layout set for the console [baseTag] (upper case, GPGX prefix dropped; [ds]: a DS game): its own button
     * set (the Game Boys, the GBA), the shared one-screen set ([SINGLE_TAGS]), the DS's, or null: no skin.
     */
    fun variantFor(baseTag: String, ds: Boolean, arcadeSet: Boolean = false): SkinVariant? = when {
        ds -> SkinVariant.DS
        baseTag in GB_TAGS -> SkinVariant.GB
        baseTag in GBA_TAGS -> SkinVariant.GBA
        baseTag in ARCADE_TAGS -> SkinVariant.ARCADE
        baseTag == "FDS" -> SkinVariant.FDS
        baseTag == "3DS" -> SkinVariant.N3DS
        baseTag == "N64" -> SkinVariant.N64
        // flycast: a NAOMI / Atomiswave set takes the arcade skin, a disc the Dreamcast one ([DcGames.isArcadeSet])
        baseTag == "DC" -> if (arcadeSet) SkinVariant.ARCADE else SkinVariant.DC
        baseTag in SINGLE_TAGS -> SkinVariant.SINGLE
        else -> null
    }

    private val GB_TAGS = setOf("GB", "GBC", "SGB", "FC")   // the NES too (user): A B, fceumm's turbo on TA / TB, no DISK
    private val GBA_TAGS = setOf("GBA", "MGBA")
    private val ARCADE_TAGS = setOf("FBN")

    /**
     * The main menu's pad in a [windowW] x [windowH] px area at [densityDpi], built from pieces. Portrait: the stacked
     * menu layout, its buttons at their own size along the bottom and its screen frame (where the menu goes) over the
     * room above them. Landscape: the grip menu layout, one shell with the buttons in a column each side and the screen
     * frame between them. Null when the pack has no such layout or the area is too small for it (the drawn pad, or
     * none, then). [topInset]: window px at the top the screen frame keeps clear of ([SkinSolver.placePieces]).
     * [foldable]: a device that half-folds. [tablet]: a tablet-class window (the Fold's inner screen too): upright, the
     * menu on two screens on the fold board (glasses `menutop` and `menu`), split at [hingeY] (window px; the window's
     * middle when null) when [twoScreens] (false while the menu already has a second display) and it fits.
     */
    fun placeMenu(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, topInset: Int = 0, foldable: Boolean = false,
        tablet: Boolean = false, hingeY: Float? = null, twoScreens: Boolean = true,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || windowW == windowH || densityDpi <= 0) return null
        val k = scaleFor(pack, densityDpi)
        val landscape = windowW > windowH
        if (!landscape && tablet && twoScreens) widest(pack, SkinFamily.STACKED, SkinVariant.MENU, fold = true) { spec ->
            SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, null, topInset.toFloat(), hingeY ?: (windowH / 2f))
        }?.let { return it }
        return shell(pack, if (landscape) SkinFamily.GRIP else SkinFamily.STACKED, SkinVariant.MENU, windowW, windowH, k, null, topInset)
    }

    /**
     * The tallest of [family]'s [variant] layouts built from pieces that fits (a pack may render more than one, so a tall
     * window stretches little), with a usable glass; a game's [picture] fitted in it.
     */
    private fun shell(
        pack: SkinPackManifest, family: SkinFamily, variant: SkinVariant, windowW: Int, windowH: Int, k: Float,
        picture: Pair<Float, Float>?, topInset: Int,
    ): SkinPlacement? =
        pack.layouts.filter { it.family == family && it.variant == variant && it.frame != null && !it.foldBoard && it.minRefH <= windowH / k }
            .sortedWith(compareByDescending<SkinLayoutSpec> { it.minRefH }.thenByDescending { it.height })
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
    private fun widest(
        pack: SkinPackManifest, family: SkinFamily, variant: SkinVariant, fold: Boolean, refH: Float = Float.MAX_VALUE,
        placed: (SkinLayoutSpec) -> SkinPlacement?,
    ): SkinPlacement? =
        pack.layouts.filter { it.family == family && it.variant == variant && it.foldBoard == fold && it.minRefH <= refH }
            .sortedWith(compareByDescending<SkinLayoutSpec> { it.minRefH }.thenByDescending { it.width }).firstNotNullOfOrNull(placed)

    /** A layout on a foldable's board: its background has a hinge (an inner screen's two halves). */
    private val SkinLayoutSpec.foldBoard: Boolean get() = tree.parts.any { it.isHinge }

    /** Window px per picture px: [densityDpi] over the pack's, 1 within [SNAP] of it (drawn 1:1, crisp). */
    private fun scaleFor(pack: SkinPackManifest, densityDpi: Int): Float =
        (densityDpi.toFloat() / pack.refDpi).let { if (kotlin.math.abs(it - 1f) < SNAP) 1f else it }

    /**
     * [windowW] x [windowH] px at [densityDpi]; [tablet]: a tablet-class window; [ds]: a DS game (else one screen of
     * [picture] game px); [hingeY]: the fold's middle in window px while half-folded, else null; [topInset]: window px at
     * the top the screen frame keeps clear of (the camera cutout; [SkinSolver.placePieces]); [foldable]: a device that
     * half-folds; [variant]: the console's layout set ([variantFor]; null: the DS's or the shared one-screen one by [ds]).
     */
    fun place(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>? = null, hingeY: Float? = null, topInset: Int = 0, foldable: Boolean = false,
        variant: SkinVariant? = null,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || densityDpi <= 0) return null
        val v = variant ?: if (ds) SkinVariant.DS else SkinVariant.SINGLE
        val own = placeAs(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, v)
        // a console's own set the pack lacks for this screen (an older pack, or a player's partly made one): the shared
        // one-screen set, decided per screen, so a missing landscape layout never leaves the game without a skin
        // (never for the Dreamcast or the 3DS: the shared set has no stick, nor two screens)
        if (own != null || v == SkinVariant.DS || v == SkinVariant.SINGLE || v == SkinVariant.DC || v == SkinVariant.N3DS) return own
        return placeAs(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, SkinVariant.SINGLE)
    }

    private fun placeAs(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>?, hingeY: Float?, topInset: Int, foldable: Boolean, v: SkinVariant,
    ): SkinPlacement? {
        val k = scaleFor(pack, densityDpi)
        val landscape = windowW > windowH
        // sideways: the main menu's landscape shell, the game in its screen frame (the strip under it; a DS's MIC, SWAP)
        if (landscape) return shell(pack, SkinFamily.GRIP, v, windowW, windowH, k, picture, topInset)
        // half-folded: the screen frame above the fold, the pad below it (a DS's touch screen with it)
        // a foldable's inner screen or a tablet: the fold board, split at its hinge flat or not (the window's middle when flat)
        if (foldable || tablet) widest(pack, SkinFamily.FLEX, v, fold = true, refH = windowH / k) { spec ->
            SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), hingeY ?: (windowH / 2f))
        }?.let { return it }
        if (hingeY != null) return widest(pack, SkinFamily.FLEX, v, fold = false) { spec ->
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
            widest(pack, family, v, fold = false) { spec ->
                if (spec.frame != null) SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat())
                else SkinSolver.place(spec, windowW.toFloat(), windowH.toFloat(), k, picture)
            }?.let { return it }
        }
        return null
    }
}
