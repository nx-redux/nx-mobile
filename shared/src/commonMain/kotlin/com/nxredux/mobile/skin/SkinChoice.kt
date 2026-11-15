package com.nxredux.mobile.skin

/**
 * Which of a pack's layouts a window gets, and that layout assembled to it.
 *
 * Landscape: the main menu's landscape shell, the game in its screen frame with the touch shortcut strip under it (a
 * DS's two screens stacked in it, MIC and SWAP beside MENU); a book foldable's inner screen sideways (its hinge across
 * the window) takes the fold board instead for a DS or 3DS, and for any game or the menu half-folded. Half-folded: the screen frame above the fold and the pad
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
    /** The smallest the fold board is drawn, as a share of its size at the screen's density, before the phone board. */
    private const val FOLD_MIN_SHRINK = 0.7f
    /** The longest a book foldable's inner screen is to its short side (the Galaxy Fold 7 / 8 Ultra 1.11, the Pixel Folds 1.04). */
    private const val NEAR_SQUARE = 1.2f
    /** The consoles with two screens, which the fold board's hinge falls between. */
    private val TWO_SCREEN = setOf(SkinVariant.DS, SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)
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
     * set (the Game Boys, the GBA), the DS's, or the shared one-screen set ([SINGLE_TAGS], and any console with none of its own). [gameCube]: a
     * GameCube disc run under the Wii tag (its pad is the GameCube's). [wiiPad]: the Wii game's resolved controller (each
     * Wii pad has its layout; the Classic Controller's until it resolves). [n3dsNew]: the resolved New 3DS Controls (the 3DS skin with
     * ZL / ZR and the C-stick nub, else the regular 3DS's).
     */
    fun variantFor(
        baseTag: String, ds: Boolean, arcadeSet: Boolean = false, gameCube: Boolean = false, wiiPad: com.nxredux.mobile.runner.WiiPad? = null,
        n3dsNew: Boolean = false,
    ): SkinVariant = when {
        ds -> SkinVariant.DS
        gameCube || baseTag == "GC" -> SkinVariant.GC
        baseTag == "WII" -> when (wiiPad) { com.nxredux.mobile.runner.WiiPad.CLASSIC -> SkinVariant.WII_CLASSIC; com.nxredux.mobile.runner.WiiPad.NUNCHUK -> SkinVariant.WII_NUNCHUK; com.nxredux.mobile.runner.WiiPad.SIDEWAYS -> SkinVariant.WII_SIDEWAYS; com.nxredux.mobile.runner.WiiPad.UPRIGHT -> SkinVariant.WII_UPRIGHT; null -> SkinVariant.WII_CLASSIC }   // unresolved: the Wii's default pad
        baseTag in GB_TAGS -> SkinVariant.GB
        baseTag in GBA_TAGS -> SkinVariant.GBA
        baseTag in ARCADE_TAGS -> SkinVariant.ARCADE
        baseTag == "FDS" -> SkinVariant.FDS
        baseTag == "3DS" -> if (n3dsNew) SkinVariant.N3DS else SkinVariant.N3DS_REGULAR   // the New 3DS controls (ZL ZR, C-stick) or not
        baseTag == "N64" -> SkinVariant.N64
        // pcsx_rearmed / SwanStation (a DualShock on port 1) and ARMSX2 (its DualShock 2): the same skin
        baseTag == "PS" || baseTag == "PSX" || baseTag == "PS2" -> SkinVariant.PS1
        baseTag == "PSP" -> SkinVariant.PSP
        baseTag == "P8" -> SkinVariant.P8   // fake-08: O (B) and X (A), START its pause
        baseTag == "PRBOOM" -> SkinVariant.DOOM   // PrBoom's Classic pad: the standard pad with L2 R2 L3 R3
        baseTag == "A5200" -> SkinVariant.A5200   // a5200: FIRE 1 / 2 (A / B), * # (Y / X), KEYPAD (L: its on-screen keypad)
        // flycast: a NAOMI / Atomiswave set takes the arcade skin, a disc the Dreamcast one ([DcGames.isArcadeSet])
        baseTag == "DC" -> if (arcadeSet) SkinVariant.ARCADE else SkinVariant.DC
        // every other console (an added core's tag too): the shared one-screen layout
        else -> SkinVariant.SINGLE
    }

    private val GB_TAGS = setOf("GB", "GBC", "SGB", "FC")   // the NES too: A B, fceumm's turbo on TA / TB, no DISK
    private val GBA_TAGS = setOf("GBA", "MGBA")
    private val ARCADE_TAGS = setOf("FBN")

    /**
     * The main menu's pad in a [windowW] x [windowH] px area at [densityDpi], built from pieces. Portrait: the stacked
     * menu layout, its buttons at their own size along the bottom and its screen frame (where the menu goes) over the
     * room above them. Landscape: the grip menu layout, one shell with the buttons in a column each side and the screen
     * frame between them. Null when the pack has no such layout or the area is too small for it (the screen then fills the
     * area). [topInset]: window px at the top the screen frame keeps clear of ([SkinSolver.placePieces]).
     * [foldable]: a device that half-folds. [tablet]: a tablet-class window (the Fold's inner screen too): upright, the
     * menu on two screens on the fold board (glasses `menutop` and `menu`), split at [hingeY] (window px; the window's
     * middle when null) when [twoScreens] (false while the menu already has a second display) and it fits.
     */
    fun placeMenu(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, topInset: Int = 0, foldable: Boolean = false,
        tablet: Boolean = false, hingeY: Float? = null, twoScreens: Boolean = true, hingeShown: Boolean = true,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || windowW == windowH || densityDpi <= 0) return null
        val k = scaleFor(pack, densityDpi)
        val landscape = windowW > windowH
        // sideways, a book foldable's inner screen half-folded: the hinge runs across the window, so the two screens too
        val twoUp = if (landscape) hingeY != null && (foldable || tablet) && nearSquare(windowW, windowH) else tablet
        if (twoUp && twoScreens) shrunk(k) { s ->
            widest(pack, SkinFamily.STACKED, SkinVariant.MENU, fold = true) { spec ->
                SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), s, null, topInset.toFloat(), hingeY ?: (windowH / 2f), hingeGap = noHingeGap(hingeShown))
            }
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

    /**
     * [placed] at scale [k], else at smaller scales down to [FOLD_MIN_SHRINK] of it: a dense inner screen (a large
     * Display size, a wider Fold, its inner screen sideways) keeps the fold board, a little smaller, over another board. A
     * scale within [SNAP] of 1 is tried at 1 first (drawn 1:1, crisp), then as it is.
     */
    private fun shrunk(k: Float, placed: (Float) -> SkinPlacement?): SkinPlacement? {
        val tried = mutableSetOf<Float>()
        fun attempt(s: Float) = if (tried.add(s)) placed(s) else null
        attempt(k)?.let { return it }
        var f = 1f
        while (true) {
            f *= 0.95f
            if (f < FOLD_MIN_SHRINK) return null
            val s = k * f
            attempt(if (kotlin.math.abs(s - 1f) < SNAP) 1f else s)?.let { return it }
            attempt(s)?.let { return it }
        }
    }

    /**
     * The fold board's top screen frame drawn without its hinge ([SkinSeam]; [hingeShown] false): its room runs down to
     * the board's middle; with the hinge, the frame's own gap above it.
     */
    private fun noHingeGap(hingeShown: Boolean): Float? = if (hingeShown) null else 0f

    /** A window no more than [NEAR_SQUARE] long to short: a book foldable's inner screen, either way up. */
    private fun nearSquare(w: Int, h: Int): Boolean = maxOf(w, h).toFloat() / minOf(w, h) <= NEAR_SQUARE

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
        variant: SkinVariant? = null, hingeShown: Boolean = true,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || densityDpi <= 0) return null
        val v = variant ?: if (ds) SkinVariant.DS else SkinVariant.SINGLE
        val own = placeAs(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, v, hingeShown)
        // a console's own set the pack lacks for this screen (an older pack, or a player's partly made one): the shared
        // one-screen set, decided per screen, so a missing landscape layout never leaves the game without a skin
        // (never for the Dreamcast, the 3DS, the GameCube, the Wii pads, the PlayStations or the PSP: the shared set has
        // no stick, nor two screens)
        if (own != null || v == SkinVariant.DS || v == SkinVariant.SINGLE || v == SkinVariant.DC || v == SkinVariant.N3DS || v == SkinVariant.N3DS_REGULAR || v == SkinVariant.GC || v == SkinVariant.WII_CLASSIC || v == SkinVariant.WII_NUNCHUK || v == SkinVariant.WII_SIDEWAYS || v == SkinVariant.WII_UPRIGHT || v == SkinVariant.PS1 || v == SkinVariant.PSP) return own
        return placeAs(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, SkinVariant.SINGLE, hingeShown)
    }

    /**
     * [place], else the closest layout scaled down: [place] at [densityDpi] x 0.9^n (n = 1, 2, ...) until one fits, down
     * to half the dpi (a window no layout fits: split screen, freeform); null when even that fits none.
     */
    fun placeClosest(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>? = null, hingeY: Float? = null, topInset: Int = 0, foldable: Boolean = false,
        variant: SkinVariant? = null, hingeShown: Boolean = true,
    ): SkinPlacement? {
        place(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, variant, hingeShown)?.let { return it }
        var dpi = densityDpi.toDouble()
        while (true) {
            dpi *= 0.9
            val d = Math.round(dpi).toInt()
            if (d < densityDpi / 2.0 || d <= 0) return null
            place(pack, windowW, windowH, d, tablet, ds, picture, hingeY, topInset, foldable, variant, hingeShown)?.let { return it }
        }
    }

    private fun placeAs(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>?, hingeY: Float?, topInset: Int, foldable: Boolean, v: SkinVariant, hingeShown: Boolean,
    ): SkinPlacement? {
        val k = scaleFor(pack, densityDpi)
        // the top screen's game size: the large layout's top frame takes its shape
        val top = when (v) { SkinVariant.N3DS, SkinVariant.N3DS_REGULAR -> 400f to 240f; SkinVariant.DS -> 256f to 192f; else -> null }
        val landscape = windowW > windowH
        // sideways: the main menu's landscape shell, the game in its screen frame (the strip under it; a DS's MIC, SWAP)
        val foldBoard = {
            shrunk(k) { s ->
                widest(pack, SkinFamily.FLEX, v, fold = true, refH = windowH / s) { spec ->
                    SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), s, picture, topInset.toFloat(), hingeY ?: (windowH / 2f), hingeGap = noHingeGap(hingeShown))
                }
            }
        }
        if (landscape) {
            // a book foldable's inner screen sideways: its hinge runs across the window, where the board's is. A DS or 3DS
            // takes the board flat too (the crease between its screens); a one-screen game only half-folded, as flat the
            // landscape shell gives it the window's whole height.
            if ((foldable || tablet) && nearSquare(windowW, windowH) && (hingeY != null || v in TWO_SCREEN)) foldBoard()?.let { return it }
            return shell(pack, SkinFamily.GRIP, v, windowW, windowH, k, picture, topInset)
        }
        // a foldable's inner screen or a tablet: the fold board, split at its hinge flat or not (the window's middle when flat)
        if (foldable || tablet) foldBoard()?.let { return it }
        // half-folded: the screen frame above the fold, the pad below it (a DS's touch screen with it)
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
                if (spec.frame != null) SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), topScreen = top)
                else SkinSolver.place(spec, windowW.toFloat(), windowH.toFloat(), k, picture)
            }?.let { return it }
        }
        return null
    }
}
