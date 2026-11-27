package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect

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
    /** Flat, between the fold board's top screen frame and the frame under it, dp ([foldPieces]). */
    private const val FRAME_GAP_DP = 16f
    /** Above and below a DS / 3DS top screen across its frame's whole width, dp: the most black its frame shows ([foldPieces]). */
    private const val TOP_PAD_DP = 8f
    /** On a window wider than the fold board's render, how far its side groups move in toward the bottom screen, dp at most. */
    private const val SIDE_INSET_DP = 16f
    /** From each window edge to its side group, dp with room ([RowSpacing.edge]). */
    private const val ROW_EDGE_DP = 32f
    /** From each side group to the screen between them, dp with room ([RowSpacing.gap]). */
    private const val ROW_GAP_DP = 16f
    /** Between a shoulder and its companion beside it (the arcade's L TB, TA R), dp. */
    private const val PAIR_GAP_DP = 8f
    /** The least those gaps shrink to, dp, before the board is drawn smaller. */
    private const val ROW_MIN_GAP_DP = 4f
    /** The narrowest the main menu's bottom screen gets, dp: the narrowest the app's pages lay out for. */
    private const val MENU_MIN_DP = 320f
    /** The longest a book foldable's inner screen is to its short side (the Galaxy Fold 7 / 8 Ultra 1.11, the Pixel Folds 1.04). */
    private const val NEAR_SQUARE = 1.2f
    /** The smallest menu glass side, picture px: less and the menu would not be usable in it. */
    private const val MENU_MIN_GLASS = 400f

    /**
     * The skin layout set for the console [baseTag] (upper case, GPGX prefix dropped; [ds]: a DS game): its own button
     * set (the Game Boys, the GBA), the DS's, or the shared one-screen set (any console with none of its own). [gameCube]: a
     * GameCube disc run under the Wii tag (its pad is the GameCube's). [wiiPad]: the Wii game's resolved controller (each
     * Wii pad has its layout; the Classic Controller's until it resolves). [n3dsNew]: the resolved New 3DS Controls (the 3DS skin with
     * ZL / ZR and the C-stick nub, else the regular 3DS's). [segaThree]: a Sega pad game resolved to the 3-button pad.
     */
    fun variantFor(
        baseTag: String, ds: Boolean, arcadeSet: Boolean = false, gameCube: Boolean = false, wiiPad: com.nxredux.mobile.runner.WiiPad? = null,
        n3dsNew: Boolean = false, segaThree: Boolean = false,
    ): SkinVariant = when {
        ds -> SkinVariant.DS
        gameCube || baseTag == "GC" -> SkinVariant.GC
        baseTag == "WII" -> when (wiiPad) { com.nxredux.mobile.runner.WiiPad.CLASSIC -> SkinVariant.WII_CLASSIC; com.nxredux.mobile.runner.WiiPad.NUNCHUK -> SkinVariant.WII_NUNCHUK; com.nxredux.mobile.runner.WiiPad.SIDEWAYS -> SkinVariant.WII_SIDEWAYS; com.nxredux.mobile.runner.WiiPad.UPRIGHT -> SkinVariant.WII_UPRIGHT; null -> SkinVariant.WII_CLASSIC }   // unresolved: the Wii's default pad
        baseTag in GB_TAGS -> SkinVariant.GB
        baseTag in GBA_TAGS -> SkinVariant.GBA
        baseTag in ARCADE_TAGS -> SkinVariant.ARCADE
        baseTag == "FDS" -> SkinVariant.FDS
        baseTag == "3DS" -> if (n3dsNew) SkinVariant.N3DS else SkinVariant.N3DS_REGULAR   // the New 3DS controls (ZL ZR, C-stick) or not
        baseTag == "WIIU" -> SkinVariant.WIIU
        baseTag == "N64" -> SkinVariant.N64
        // pcsx_rearmed / SwanStation (a DualShock on port 1) and ARMSX2 (its DualShock 2): the same skin
        baseTag == "PS" || baseTag == "PSX" || baseTag == "PS2" -> SkinVariant.PS1
        baseTag == "PSP" -> SkinVariant.PSP
        baseTag in MD_TAGS -> if (segaThree) SkinVariant.MD3 else SkinVariant.MD   // unknown: the six (the core's 6-button default)
        baseTag in SATURN_TAGS -> SkinVariant.SATURN
        baseTag == "P8" -> SkinVariant.P8   // fake-08: O (B) and X (A), START its pause
        baseTag == "PRBOOM" -> SkinVariant.DOOM   // PrBoom's Classic pad: the standard pad with L2 R2 L3 R3
        baseTag == "SMS" || baseTag == "SG1000" -> SkinVariant.SMS   // picodrive: 1 (B), 2 (A), START the pause
        baseTag == "GG" -> SkinVariant.GG
        baseTag == "A7800" -> SkinVariant.A7800   // prosystem: 1 (B), 2 (A), SELECT, START the console's pause
        baseTag == "NGP" || baseTag == "NGPC" -> SkinVariant.NGP   // race: A (B), B (A), START its OPTION
        baseTag == "PCE" -> SkinVariant.PCE   // pce_fast: II (B), I (A), SELECT, RUN (START)
        baseTag == "A2600" -> SkinVariant.A2600   // stella2014: Fire (B), SELECT, RESET (START)
        baseTag == "LYNX" -> SkinVariant.LYNX   // handy: B, A, Option 1 / 2 (L / R), Pause (START)
        baseTag == "PKM" -> SkinVariant.PKM   // pokemini: B, A, Shake (L), C (R); Power on SELECT (the menu's Reset does it)
        baseTag == "VB" -> SkinVariant.VB   // beetle-vb: the right d-pad as L2 R2 L3 R3 ([com.nxredux.mobile.ui.nx.VB_SECOND_DPAD])
        baseTag == "WS" || baseTag == "WSC" -> SkinVariant.WS   // beetle-wswan: the Y cursor as R2 L2 L R ([com.nxredux.mobile.ui.nx.WS_SECOND_DPAD])
        baseTag == "COLECO" -> SkinVariant.COLECO   // gearcoleco: Yellow (B), Red (A), its keypad ([com.nxredux.mobile.ui.nx.colecoKeypad])
        baseTag == "A5200" -> SkinVariant.A5200   // a5200: FIRE 1 / 2 (A / B), * # (Y / X), KEYPAD (L: its on-screen keypad)
        // flycast: a NAOMI / Atomiswave set takes the arcade skin, a disc the Dreamcast one ([DcGames.isArcadeSet])
        baseTag == "DC" -> if (arcadeSet) SkinVariant.ARCADE else SkinVariant.DC
        // every other console (an added core's tag too): the shared one-screen layout
        else -> SkinVariant.SINGLE
    }

    private val GB_TAGS = setOf("GB", "GBC", "SGB", "FC")   // the NES too: A B, fceumm's turbo on TA / TB, no DISK
    private val GBA_TAGS = setOf("GBA", "MGBA")
    private val MD_TAGS = setOf("MD", "GPGX", "SEGACD", "32X")   // the Sega pad's ([com.nxredux.mobile.runner.PadMap.isSegaPadTag])
    // the Saturn's tags (SS: as other frontends name it): the Genesis's six on the same spots ([com.nxredux.mobile.runner.PadMap]
    // sends each to the button the Saturn core names that letter), its L / R on L2 / R2
    private val SATURN_TAGS = setOf("SATURN", "SS")
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
        tablet: Boolean = false, hingeY: Float? = null, twoScreens: Boolean = true,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || windowW == windowH || densityDpi <= 0) return null
        val k = scaleFor(pack, densityDpi)
        val tuned = tunedFor(windowW, windowH, densityDpi, hingeY)
        val landscape = windowW > windowH
        // sideways, a book foldable's inner screen: the hinge runs across the window, so the two screens too, flat or not
        val twoUp = if (landscape) (foldable || tablet) && nearSquare(windowW, windowH) else tablet
        // sideways the flat menu is the landscape shell: half-folded, the bottom screen ends where that one's does, so
        // folding and opening keep its bottom edge in place
        val flatEnd = menuFlatEnd(pack, windowW, windowH, k, topInset, densityDpi)
        if (twoUp && twoScreens) shrunk(k) { s ->
            widest(tuned, pack, SkinFamily.STACKED, SkinVariant.MENU, fold = true) { spec ->
                foldPieces(spec, windowW, windowH, s, null, topInset, hingeY, densityDpi,
                    bottomMargin = shellMargin(pack), bottomGlass = flatEnd, row = rowSpacing(densityDpi, s))
            }
        }?.let { return it }
        // a phone half-folded: the screen frame above the fold and the pad below it, as a game's half-folded board has them,
        // so opening a game moves nothing
        if (!landscape && !tablet && hingeY != null) widest(tuned, pack, SkinFamily.FLEX, SkinVariant.MENU, fold = false) { spec ->
            if (spec.frame?.hinge?.let { it >= 0 } != true) null
            else if (spec.boxes.isNotEmpty()) SkinBoxes.place(spec, windowW, windowH, k, densityDpi, hingeY) { s, r ->
                SkinSolver.placePieces(s, windowW.toFloat(), windowH.toFloat(), k, null, topInset.toFloat(), hingeY, roomLeft = r.left, roomRight = r.right, roomBottom = r.bottom)
            }
            else SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, null, topInset.toFloat(), hingeY)
        }?.let { return it }
        return shell(tuned, pack, if (landscape) SkinFamily.GRIP else SkinFamily.STACKED, SkinVariant.MENU, windowW, windowH, k, densityDpi, null, topInset, rowSpacing(densityDpi, k), hingeY = hingeY)
    }

    /**
     * Whether a layout has a box size drawn for this window ([BoxWhen]): such a layout is the one the device was tuned
     * with, tried before its siblings (a box layout has no pieces to rule itself out by). Untuned, a box layout still
     * goes before a sibling built from pieces, whose pads sit off the menu's spots.
     */
    private fun tunedFor(windowW: Int, windowH: Int, densityDpi: Int, hingeY: Float?): (SkinLayoutSpec) -> Boolean {
        val dp = densityDpi / 160f
        val shape = DeviceShape(windowW / dp, windowH / dp, hingeY != null)
        return { spec -> spec.boxes.any { b -> b.sizes.any { it.`when`?.matches(shape) == true } } }
    }

    /**
     * The tallest of [family]'s [variant] layouts built from pieces that fits (a pack may render more than one, so a tall
     * window stretches little), with a usable glass; a game's [picture] fitted in it.
     */
    private fun shell(
        tuned: (SkinLayoutSpec) -> Boolean, pack: SkinPackManifest, family: SkinFamily, variant: SkinVariant, windowW: Int, windowH: Int, k: Float, densityDpi: Int,
        picture: Pair<Float, Float>?, topInset: Int, row: RowSpacing? = null, refH: Float = windowH / k,
        hingeY: Float? = null, pick: (SkinLayoutSpec) -> Boolean = { true }, adapt: (SkinLayoutSpec) -> SkinLayoutSpec = { it },
    ): SkinPlacement? =
        pack.layouts.filter { it.family == family && it.variant == variant && it.frame != null && !it.foldBoard && it.minRefH <= refH && pick(it) }
            .sortedWith(compareByDescending<SkinLayoutSpec> { tuned(it) }.thenByDescending { it.boxes.isNotEmpty() }.thenByDescending { it.minRefH }.thenByDescending { it.height })
            .firstNotNullOfOrNull { spec ->
                // a layout with boxes: placed as its boxes have it, never adapted
                // (the hinge for the box sizes drawn half-folded: a shell itself ignores it)
                (if (spec.boxes.isNotEmpty()) SkinBoxes.place(spec, windowW, windowH, k, densityDpi, hingeY) { s, r ->
                    SkinSolver.placePieces(s, windowW.toFloat(), windowH.toFloat(), k, picture, topInset = topInset.toFloat(), row = row,
                        roomLeft = r.left, roomRight = r.right, roomBottom = r.bottom)
                } else SkinSolver.placePieces(adapt(spec), windowW.toFloat(), windowH.toFloat(), k, picture, topInset = topInset.toFloat(), row = row))?.takeIf { p ->
                    val g = p.glasses.first().rect
                    g.width / k >= MENU_MIN_GLASS && g.height / k >= MENU_MIN_GLASS
                }
            }

    /**
     * The first of [family]'s [variant] layouts, widest background first, that [placed] places: a big window gets the
     * board rendered for it (a background only grows, so a smaller window takes a smaller one).
     */
    private fun widest(
        tuned: (SkinLayoutSpec) -> Boolean, pack: SkinPackManifest, family: SkinFamily, variant: SkinVariant, fold: Boolean, refH: Float = Float.MAX_VALUE,
        placed: (SkinLayoutSpec) -> SkinPlacement?,
    ): SkinPlacement? =
        pack.layouts.filter { it.family == family && it.variant == variant && it.foldBoard == fold && it.minRefH <= refH }
            .sortedWith(compareByDescending<SkinLayoutSpec> { tuned(it) }.thenByDescending { it.boxes.isNotEmpty() }.thenByDescending { it.minRefH }.thenByDescending { it.width }).firstNotNullOfOrNull(placed)

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
     * A fold board's [spec] placed at scale [k]. Half-folded ([hingeY] set) it is split at the fold, its hinge drawn and
     * the top screen's frame clear of it. Flat (a tablet too) the board is one slab ([SkinSeam]): the hinge's height goes
     * to the top screen, its frame down to [FRAME_GAP_DP] above the frame under it (the touch screen, the panel), the
     * same on every device. A DS / 3DS top screen's frame is no taller than its picture needs ([TOP_PAD_DP]); flat, the
     * touch screen then moves up to [FRAME_GAP_DP] under it, the controls centred in the height under the frame. A
     * tablet's board ([TABLET_BOARD]) keeps the main menu's frames instead, for every console.
     */
    private fun foldPieces(
        spec: SkinLayoutSpec, windowW: Int, windowH: Int, k: Float, picture: Pair<Float, Float>?, topInset: Int, hingeY: Float?, densityDpi: Int,
        topScreen: Pair<Float, Float>? = null, bottomMargin: Float? = null, bottomGlass: Float? = null, row: RowSpacing? = null,
        centreControls: Boolean = true, room: BoxRoom? = null,
    ): SkinPlacement? {
        // a layout with boxes: its screens by these rules in the room the boxes leave
        if (spec.boxes.isNotEmpty()) return SkinBoxes.place(spec, windowW, windowH, k, densityDpi, hingeY) { s, r ->
            foldPieces(s, windowW, windowH, k, picture, topInset, hingeY, densityDpi, topScreen, bottomMargin, bottomGlass, row, centreControls, r)
        }
        val w = windowW.toFloat(); val h = windowH.toFloat()
        // a DS / 3DS top screen: its frame no taller than the screen across its width, plus a little ([TOP_PAD_DP])
        val capPad = topScreen?.takeUnless { spec.id.endsWith(TABLET_BOARD) }?.let { TOP_PAD_DP * densityDpi / 160f / k }
        val sideInset = SIDE_INSET_DP * densityDpi / 160f / k
        if (hingeY != null) return SkinSolver.placePieces(spec, w, h, k, picture, topInset.toFloat(), hingeY, topScreen, capPad = capPad, sideInset = sideInset, bottomMargin = bottomMargin, bottomGlass = bottomGlass, row = row, centreControls = centreControls, roomLeft = room?.left, roomRight = room?.right, roomBottom = room?.bottom)
        val middle = h / 2f
        val first = SkinSolver.placePieces(spec, w, h, k, picture, topInset.toFloat(), middle, topScreen, hingeGap = 0f, capPad = capPad, sideInset = sideInset, bottomMargin = bottomMargin, bottomGlass = bottomGlass, row = row, centreControls = centreControls, roomLeft = room?.left, roomRight = room?.right, roomBottom = room?.bottom) ?: return null
        // the top of the frame under the middle: the highest of the pieces with a screen below it
        val under = first.pieces.filter { it.piece >= 0 && spec.pieces[it.piece].glass != null && it.dst.y > middle }.minOfOrNull { it.dst.y }
            ?: return first.copy(slab = true)
        // only where there is room: the frame never ends above the middle for it (a frame under nearer than the gap)
        val gap = minOf(0f, (middle - (under - FRAME_GAP_DP * densityDpi / 160f)) / k)
        return (SkinSolver.placePieces(spec, w, h, k, picture, topInset.toFloat(), middle, topScreen, hingeGap = gap, capPad = capPad, capLift = true, sideInset = sideInset, bottomMargin = bottomMargin, bottomGlass = bottomGlass, row = row, centreControls = centreControls, roomLeft = room?.left, roomRight = room?.right, roomBottom = room?.bottom) ?: first).copy(slab = true)
    }

    /**
     * A board's spacing ([RowSpacing]) at [densityDpi] and scale [k]: [ROW_EDGE_DP] from the window's edges, [ROW_GAP_DP]
     * between the side groups and the screen, the gaps no smaller than [ROW_MIN_GAP_DP]; the screen no narrower than
     * [MENU_MIN_DP].
     */
    private fun rowSpacing(densityDpi: Int, k: Float): RowSpacing {
        val px = densityDpi / 160f / k
        // every board's screen the menu's least, so a game's side groups stand where the menu's do (a touch screen's picture
        // fits in it, a little smaller where it is narrower than the picture)
        return RowSpacing(ROW_EDGE_DP * px, ROW_GAP_DP * px, ROW_MIN_GAP_DP * px, MENU_MIN_DP * px, PAIR_GAP_DP * px)
    }

    /**
     * The row under the touch screen's frame (the centred pieces and controls below it) up to [gap] under the frame, where
     * it is lower. With [shoulders] (the tablet upright's GamePad: room to spare), the frame first grows down to [gap] over
     * the shoulder buttons and widens to the GamePad at that height ([touchAcross]), so the GamePad has no black bars.
     */
    private fun SkinPlacement.rowUnderTouch(gap: Float, shoulders: Boolean = false): SkinPlacement {
        val gi = spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
        val frame = pieces.filter { it.piece == gi && gi >= 0 && !it.strip }
        val top0 = frame.minOfOrNull { it.dst.y } ?: return this
        val bottom = frame.maxOf { it.dst.y + it.dst.height }
        fun row(p: SkinPiece) = p.piece >= 0 && p.piece != gi && spec.pieces[p.piece].let { it.glass == null && it.anchor == PieceAnchor.CENTRE } && p.dst.y >= bottom - 0.5f
        val rowIds = controls.filter { it.spec.anchor == PieceAnchor.CENTRE && it.y > bottom }.toSet()
        val shoulderTop = if (!shoulders) null else controls.filter { it.spec.id in SHOULDER_IDS }.flatMap { c ->
            pieces.filter { p -> p.piece >= 0 && p.piece != gi && p.dst.width < 300f && c.x in p.dst.x..(p.dst.x + p.dst.width) && c.y in p.dst.y..(p.dst.y + p.dst.height) }
        }.minOfOrNull { it.dst.y }
        val down = shoulderTop?.let { (it - gap) - bottom }?.takeIf { it > 0.5f } ?: 0f
        val g = glass(GlassScreen.TOUCH)
        val stretched = if (down == 0f) this else copy(
            pieces = pieces.map { p -> when {
                p.piece != gi || gi < 0 -> p
                p.strip || kotlin.math.abs(p.dst.y + p.dst.height - bottom) < 0.5f -> p.copy(dst = p.dst.copy(y = p.dst.y + down))
                kotlin.math.abs(p.dst.y - top0) < 0.5f -> p
                else -> p.copy(dst = p.dst.copy(height = p.dst.height + down))
            } },
            glasses = glasses.map { if (it === g) it.copy(rect = it.rect.copy(height = it.rect.height + down)) else it },
            controls = controls.map { c -> if (c.spec.id.startsWith("TOUCH_")) c.copy(y = c.y + down) else c },
        )
        val widened = if (shoulders) stretched.touchAcross(gi, gap, rowIds.map { it.spec.id }.toSet()) else stretched
        val rowTop = pieces.filter(::row).minOfOrNull { it.dst.y } ?: return widened
        val up = rowTop - (bottom + down + gap)
        if (up < 0.5f && down == 0f) return widened
        return widened.copy(
            pieces = widened.pieces.mapIndexed { i, p -> if (row(pieces[i])) p.copy(dst = p.dst.copy(y = p.dst.y - up)) else p },
            controls = widened.controls.mapIndexed { i, c -> if (controls[i] in rowIds) c.copy(y = c.y - up) else c },
        )
    }

    /**
     * The touch screen's frame (piece [gi]) widened about its middle to its 16:9 picture at the glass's height (no black
     * either side), never past the top screen's frame nor nearer than [gap] to a control's touch area beside it; the
     * controls in [moving] go under it, so they are not in the way.
     */
    private fun SkinPlacement.touchAcross(gi: Int, gap: Float, moving: Set<String>): SkinPlacement {
        val frame = pieces.filter { it.piece == gi && gi >= 0 && !it.strip }
        val top = pieces.filter { it.piece == -1 && !it.strip && it.fill < 0f }
        if (frame.isEmpty() || top.isEmpty()) return this
        val l0 = frame.minOf { it.dst.x }; val r0 = frame.maxOf { it.dst.x + it.dst.width }
        val y0 = frame.minOf { it.dst.y }; val y1 = frame.maxOf { it.dst.y + it.dst.height }
        var left = top.minOf { it.dst.x }; var right = top.maxOf { it.dst.x + it.dst.width }
        for (c in controls) {
            if (c.spec.id.startsWith("TOUCH_") || c.spec.id in moving) continue
            val (hw, hh) = c.touchHalf()
            if (c.y + hh <= y0 || c.y - hh >= y1) continue
            if (c.x < (l0 + r0) / 2f) left = maxOf(left, c.x + hw + gap) else right = minOf(right, c.x - hw - gap)
        }
        val g = glass(GlassScreen.TOUCH) ?: return this
        val d = minOf(l0 - left, right - r0, (g.rect.height * 16f / 9f - g.rect.width) / 2f)
        if (d < 0.5f) return this
        val gw = g.rect.width + 2f * d
        val slots = controls.filter { it.spec.id.startsWith("TOUCH_") }
        val share = gw / slots.size.coerceAtLeast(1)
        return copy(
            pieces = pieces.map { p -> when {
                p.piece != gi || p.strip -> p
                kotlin.math.abs(p.dst.x - l0) < 0.5f -> p.copy(dst = p.dst.copy(x = p.dst.x - d))
                kotlin.math.abs(p.dst.x + p.dst.width - r0) < 0.5f -> p.copy(dst = p.dst.copy(x = p.dst.x + d))
                else -> p.copy(dst = p.dst.copy(x = p.dst.x - d, width = p.dst.width + 2f * d))
            } },
            glasses = glasses.map { if (it === g) it.copy(rect = it.rect.copy(x = it.rect.x - d, width = gw)) else it },
            controls = controls.map { c -> slots.indexOf(c).let { i -> if (i < 0) c else c.copy(x = g.rect.x - d + share * (i + 0.5f), spec = c.spec.copy(hw = share / 2f)) } },
        )
    }

    /**
     * This placement with [twin]'s touch screen frame (the 3DS twin's bottom frame on the same window), the GamePad
     * letterboxed in it. Not where the two frames are cut differently, or where the twin's taller strip would sit on a
     * button (the Fold half-folded).
     */
    private fun SkinPlacement.touchAs(twin: SkinPlacement): SkinPlacement {
        val gi = spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
        val ti = twin.spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
        val mine = pieces.filter { it.piece == gi && gi >= 0 }; val theirs = twin.pieces.filter { it.piece == ti && ti >= 0 }
        val g = glass(GlassScreen.TOUCH); val tg = twin.glass(GlassScreen.TOUCH)
        if (mine.isEmpty() || mine.size != theirs.size || g == null || tg == null) return this
        val at = mine.zip(theirs).toMap()
        val slots = twin.controls.filter { it.spec.id.startsWith("TOUCH_") }.associateBy { it.spec.id }
        val onAButton = slots.values.any { sl -> controls.any { c -> !c.spec.id.startsWith("TOUCH_") && run {
            val (hw, hh) = c.touchHalf()
            c.x + hw > sl.x - sl.spec.hw && c.x - hw < sl.x + sl.spec.hw && c.y + hh > sl.y - sl.spec.hh && c.y - hh < sl.y + sl.spec.hh
        } } }
        if (onAButton) return this
        return copy(
            pieces = pieces.map { p -> at[p]?.let { t -> p.copy(dst = t.dst) } ?: p },
            glasses = glasses.map { if (it === g) it.copy(rect = tg.rect) else it },
            controls = controls.map { c -> slots[c.spec.id] ?: c },
        )
    }

    /**
     * The touch screen's frame grown up (its bottom, strip and row kept) to [twin]'s gap between its top and touch
     * glasses under this one's top glass. As it is where that gap is already as small.
     */
    private fun SkinPlacement.touchUpToGap(twin: SkinPlacement): SkinPlacement {
        val top = glass(GlassScreen.TOP) ?: return this
        val g = glass(GlassScreen.TOUCH) ?: return this
        val tt = twin.glass(GlassScreen.TOP) ?: return this
        val tg = twin.glass(GlassScreen.TOUCH) ?: return this
        val grow = g.rect.y - (top.rect.y + top.rect.height + (tg.rect.y - (tt.rect.y + tt.rect.height)))
        if (grow < 0.5f) return this
        val gi = spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
        val frame = pieces.filter { it.piece == gi && !it.strip }
        if (frame.isEmpty()) return this
        val ft = frame.minOf { it.dst.y }; val fb = frame.maxOf { it.dst.y + it.dst.height }
        return copy(
            pieces = pieces.map { p -> when {
                p.piece != gi || p.strip -> p
                kotlin.math.abs(p.dst.y - ft) < 0.5f -> p.copy(dst = p.dst.copy(y = p.dst.y - grow))
                kotlin.math.abs(p.dst.y + p.dst.height - fb) < 0.5f -> p
                else -> p.copy(dst = p.dst.copy(y = p.dst.y - grow, height = p.dst.height + grow))
            } },
            glasses = glasses.map { if (it === g) it.copy(rect = it.rect.copy(y = it.rect.y - grow, height = it.rect.height + grow)) else it },
        )
    }

    /** The sets the shared one-screen set never stands in for: itself, and those with a stick or two screens. */
    private val NO_SHARED_FALLBACK = setOf(
        SkinVariant.DS, SkinVariant.SINGLE, SkinVariant.DC, SkinVariant.N3DS, SkinVariant.N3DS_REGULAR, SkinVariant.GC,
        SkinVariant.WII_CLASSIC, SkinVariant.WII_NUNCHUK, SkinVariant.WII_SIDEWAYS, SkinVariant.WII_UPRIGHT, SkinVariant.WIIU,
        SkinVariant.PS1, SkinVariant.PSP,
    )

    /** The 3DS's top screen, game px. */
    private val N3DS_TOP = 400f to 240f

    /** The shoulder buttons' control ids. */
    private val SHOULDER_IDS = setOf("L", "R", "L2", "R2")

    /** The Wii U's one screen frame's shape: the TV over the GamePad, both 16:9. */
    const val WIIU_STACK_ASPECT = 16f / 18f

    /**
     * The Wii U's one screen frame (its stack glass) on the roomy sideways shell on a half-folded foldable cut to the
     * screens' [aspect], so there are no bars round them; elsewhere (flat, as a tablet) the frame is the 3DS's, the
     * screens centred in it.
     */
    private fun SkinPlacement.wiiuStackTo(aspect: Float): SkinPlacement {
        val frame = spec.frame ?: return this
        val g = glass(GlassScreen.STACK) ?: return this
        if (spec.variant != SkinVariant.WIIU || aspect <= 0f || !spec.id.endsWith("_tall")) return this
        val fg = frame.glass
        val bl = fg.x * k; val bt = fg.y * k; val br = (frame.w - fg.x - fg.w) * k; val bb = (frame.h - fg.y - fg.h) * k
        val w = minOf(g.rect.width, g.rect.height * aspect); val h = w / aspect
        val cut = PxRect(g.rect.x + (g.rect.width - w) / 2f, g.rect.y + (g.rect.height - h) / 2f, w, h)
        val slices = SkinSolver.frameSlices(frame, cut.x - bl, cut.y - bt, w + bl + br, h + bt + bb, k) { x, y, w_, h_ -> PxRect(x, y, w_, h_) }
        val first = pieces.indexOfFirst { it.piece == -1 && !it.strip }.coerceAtLeast(0)
        val rest = pieces.filterNot { it.piece == -1 && !it.strip }
        val down = (cut.y + h) - (g.rect.y + g.rect.height)
        val slots = controls.filter { c -> frame.strip?.labels?.any { it.id == c.spec.id } == true }
        val share = w / slots.size.coerceAtLeast(1)
        return copy(pieces = rest.take(first) + slices + rest.drop(first), glasses = glasses.map { if (it === g) it.copy(rect = cut) else it },
            controls = controls.map { c -> slots.indexOf(c).let { i -> if (i < 0) c else c.copy(x = cut.x + share * (i + 0.5f), y = c.y + down, spec = c.spec.copy(hw = share / 2f)) } })
    }

    /** Sideways: window px between the menu's landscape shell's screen and the window's bottom (null upright). */
    private fun menuFlatEnd(pack: SkinPackManifest, windowW: Int, windowH: Int, k: Float, topInset: Int, densityDpi: Int): Float? =
        if (windowW <= windowH) null else shell(tunedFor(windowW, windowH, densityDpi, null), pack, SkinFamily.GRIP, SkinVariant.MENU, windowW, windowH, k, densityDpi, null, topInset, rowSpacing(densityDpi, k))
            ?.glasses?.firstOrNull()?.rect?.let { windowH - (it.y + it.height) }

    /**
     * The menu's landscape shell's frame margin from the window's bottom, picture px: the margin the main menu's bottom
     * screen keeps on the fold board too, so folding or turning the device keeps the screen as far from the edge.
     */
    private fun shellMargin(pack: SkinPackManifest): Float? =
        pack.layouts.firstOrNull { it.family == SkinFamily.GRIP && it.variant == SkinVariant.MENU && it.frame != null }
            ?.frame?.let { f -> (if (f.bottom >= 0) f.bottom else f.top).takeIf { it >= 0 }?.toFloat() }

    /** A window no more than [NEAR_SQUARE] long to short: a book foldable's inner screen, either way up. */
    private fun nearSquare(w: Int, h: Int): Boolean = maxOf(w, h).toFloat() / minOf(w, h) <= NEAR_SQUARE

    /** Window px per picture px: [densityDpi] over the pack's, 1 within [SNAP] of it (drawn 1:1, crisp). */
    private fun scaleFor(pack: SkinPackManifest, densityDpi: Int): Float =
        (densityDpi.toFloat() / pack.refDpi).let { if (kotlin.math.abs(it - 1f) < SNAP) 1f else it }

    /**
     * [windowW] x [windowH] px at [densityDpi]; [tablet]: a tablet-class window; [ds]: a DS game (else one screen of
     * [picture] game px); [hingeY]: the fold's middle in window px while half-folded, else null; [topInset]: window px at
     * the top the screen frame keeps clear of (the camera cutout; [SkinSolver.placePieces]); [foldable]: a device that
     * half-folds; [variant]: the console's layout set ([variantFor]; null: the DS's or the shared one-screen one by [ds]);
     * [stackAspect]: the Wii U's one screen frame cut to that shape (the TV over the GamePad; the player's layout where it
     * applies, [com.nxredux.mobile.runner.WiiuLayout.skinAspect]).
     */
    fun place(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>? = null, hingeY: Float? = null, topInset: Int = 0, foldable: Boolean = false,
        variant: SkinVariant? = null, stackAspect: Float = WIIU_STACK_ASPECT,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || densityDpi <= 0) return null
        val v = variant ?: if (ds) SkinVariant.DS else SkinVariant.SINGLE
        val own = placeAs(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, v)?.let { if (foldable && hingeY != null) it.wiiuStackTo(stackAspect) else it }
        // a console's own set the pack lacks for this screen (an older pack, or a player's partly made one): the shared
        // one-screen set, decided per screen, so a missing landscape layout never leaves the game without a skin
        if (own != null || v in NO_SHARED_FALLBACK) return own
        return placeAs(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, SkinVariant.SINGLE)
    }

    /**
     * [place], else the closest layout scaled down: [place] at [densityDpi] x 0.9^n (n = 1, 2, ...) until one fits, down
     * to half the dpi (a window no layout fits: split screen, freeform); null when even that fits none.
     */
    fun placeClosest(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>? = null, hingeY: Float? = null, topInset: Int = 0, foldable: Boolean = false,
        variant: SkinVariant? = null, stackAspect: Float = WIIU_STACK_ASPECT,
    ): SkinPlacement? {
        place(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, variant, stackAspect)?.let { return it }
        var dpi = densityDpi.toDouble()
        while (true) {
            dpi *= 0.9
            val d = Math.round(dpi).toInt()
            if (d < densityDpi / 2.0 || d <= 0) return null
            place(pack, windowW, windowH, d, tablet, ds, picture, hingeY, topInset, foldable, variant, stackAspect)?.let { return it }
        }
    }

    private fun placeAs(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>?, hingeY: Float?, topInset: Int, foldable: Boolean, v: SkinVariant,
    ): SkinPlacement? {
        val k = scaleFor(pack, densityDpi)
        val tuned = tunedFor(windowW, windowH, densityDpi, hingeY)
        val dp = densityDpi / 160f
        val row = rowSpacing(densityDpi, k)
        // the top screen's game size: the large layout's top frame takes its shape
        val top = when (v) { SkinVariant.N3DS, SkinVariant.N3DS_REGULAR -> N3DS_TOP; SkinVariant.DS -> 256f to 192f; SkinVariant.WIIU -> 1280f to 720f; else -> null }
        val landscape = windowW > windowH
        // sideways: the main menu's landscape shell, the game in its screen frame (the strip under it; a DS's MIC, SWAP)
        // [both]: the Dreamcast's board with the stick and the d-pad both, tried first at every scale (shrunk to fit) over the
        // board showing one at a time
        val flatEnd by lazy { menuFlatEnd(pack, windowW, windowH, k, topInset, densityDpi) }
        fun foldBoardOf(both: Boolean?) = shrunk(k) { s ->
            widest(tuned, pack, SkinFamily.FLEX, v, fold = true, refH = if (both != null) Float.MAX_VALUE else windowH / s) { spec ->
                if (both != null && (spec.minRefH > 0) != both) return@widest null
                // a screen frame with nothing under it as far from the window's bottom as the menu's
                fun fold(hinge: Float? = hingeY) = foldPieces(spec, windowW, windowH, s, picture, topInset, hinge, densityDpi, top, bottomMargin = shellMargin(pack),
                    bottomGlass = flatEnd, row = rowSpacing(densityDpi, s), centreControls = v !in N3DS_BOARD)
                val placed = fold() ?: return@widest null
                // upright: the GamePad in the 3DS twin's bottom frame (flat, as tall as the row under it leaves room for), its row
                // right under it, as the 3DS's row follows its touch frame
                // (half-folded upright: the GameCube's frame, the GamePad between bars, as sideways)
                if (v == SkinVariant.WIIU && !landscape && spec.boxes.isNotEmpty() && (hingeY == null || spec.id.endsWith(TABLET_BOARD))) {
                    val twin = pack.layouts.firstOrNull { it.id == spec.id.replace("_wiiugp", "_3ds") }.takeIf { !spec.id.endsWith(TABLET_BOARD) }
                    val theirs = twin?.let { t -> foldPieces(t, windowW, windowH, s, picture, topInset, hingeY, densityDpi, N3DS_TOP, bottomMargin = shellMargin(pack),
                        bottomGlass = flatEnd, row = rowSpacing(densityDpi, s), centreControls = false) }
                    val under = (theirs?.let { placed.touchAs(it) } ?: placed).rowUnderTouch(SkinBoxes.GAP_DP * dp, shoulders = spec.id.endsWith(TABLET_BOARD))
                    // flat: up under the TV's shorter frame, the 3DS's gap between the glasses (the menu's)
                    return@widest if (hingeY == null && theirs != null) under.touchUpToGap(theirs) else under
                }
                // the 3DS's touch screen: its 4:3 picture between bars in the frame as placed (sideways and half-folded the
                // GameCube's / N64's; flat upright the menu's width, as tall as the row under it leaves room for)
                placed
            }
        }
        val foldBoard = { if (v == SkinVariant.DC) foldBoardOf(true) ?: foldBoardOf(false) else foldBoardOf(null) }
        if (landscape) {
            // a book foldable's inner screen sideways: its hinge runs across the window, where the board's is, flat too
            // (controls beside the game would sit on the crease)
            if ((foldable || tablet) && nearSquare(windowW, windowH)) foldBoard()?.let { return it }
            // the Dreamcast, the N64 and the 3DS on a tablet / an inner screen: their shells with the stick and the d-pad both
            if (v == SkinVariant.DC && (foldable || tablet)) shell(tuned, pack, SkinFamily.GRIP, v, windowW, windowH, k, densityDpi, picture, topInset, row, hingeY = hingeY,
                refH = Float.MAX_VALUE, pick = { it.minRefH > 0 && it.controls.none { c -> c.id == "STICK_SWAP" } })?.let { return it }
            if (v == SkinVariant.N64 && (foldable || tablet)) shell(tuned, pack, SkinFamily.GRIP, v, windowW, windowH, k, densityDpi, picture, topInset, row, hingeY = hingeY,
                pick = { it.minRefH > 0 && it.controls.none { c -> c.id == "STICK_SWAP" } && it.controls.any { c -> c.id == "MENU" && c.anchor == PieceAnchor.RIGHT } })
                ?.let { return it }
            if (v in N3DS_VARIANTS) shell(tuned, pack, SkinFamily.GRIP, v, windowW, windowH, k, densityDpi, picture, topInset, row, hingeY = hingeY,
                pick = { it.controls.none { c -> c.id == "STICK_SWAP" } })?.let { return it }
            return shell(tuned, pack, SkinFamily.GRIP, v, windowW, windowH, k, densityDpi, picture, topInset, row, hingeY = hingeY)
        }
        // a foldable's inner screen or a tablet: the fold board, split at its hinge flat or not (the window's middle when flat)
        if (foldable || tablet) foldBoard()?.let { return it }
        // half-folded: the screen frame above the fold, the pad below it (a DS's touch screen with it)
        if (hingeY != null) return widest(tuned, pack, SkinFamily.FLEX, v, fold = false) { spec ->
            if (spec.frame?.hinge?.let { it >= 0 } != true) null
            else if (spec.boxes.isNotEmpty()) SkinBoxes.place(spec, windowW, windowH, k, densityDpi, hingeY) { s, r ->
                SkinSolver.placePieces(s, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), hingeY, roomLeft = r.left, roomRight = r.right, roomBottom = r.bottom)
            }
            else SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), hingeY)
        }
        val families = when {
            !ds -> listOf(SkinFamily.STACKED)
            tablet -> listOf(SkinFamily.LARGE, SkinFamily.STACKED)
            windowH.toFloat() / windowW >= TALL -> listOf(SkinFamily.STACKED, SkinFamily.LARGE)
            else -> listOf(SkinFamily.LARGE, SkinFamily.STACKED)
        }
        for (family in families) {
            widest(tuned, pack, family, v, fold = false) { spec ->
                if (spec.frame != null && spec.boxes.isNotEmpty()) SkinBoxes.place(spec, windowW, windowH, k, densityDpi, hingeY) { s, r ->
                    SkinSolver.placePieces(s, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), topScreen = top, row = row,
                        roomLeft = r.left, roomRight = r.right, roomBottom = r.bottom)
                }
                else if (spec.frame != null) SkinSolver.placePieces(spec, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), topScreen = top, row = row)
                else SkinSolver.place(spec, windowW.toFloat(), windowH.toFloat(), k, picture)
            }?.let { return it }
        }
        return null
    }

}
