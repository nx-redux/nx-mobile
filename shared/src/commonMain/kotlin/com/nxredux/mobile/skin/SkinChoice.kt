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
    /** Spare width (dp each side) beside the 3DS touch screen's picture from which its frame is cut to it ([touchToItsPicture]). */
    private const val WIDE_TOUCH_DP = 40f
    /** The d-pad's and the New 3DS's C-stick's size on a fold board whose touch screen is cut to its picture's height ([touchToItsPictureHeight]). */
    private const val SMALL_DPAD = 0.85f
    /** The narrowest the main menu's bottom screen gets, dp: the narrowest the app's pages lay out for. */
    private const val MENU_MIN_DP = 320f
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
        tablet: Boolean = false, hingeY: Float? = null, twoScreens: Boolean = true,
    ): SkinPlacement? {
        if (windowW <= 0 || windowH <= 0 || windowW == windowH || densityDpi <= 0) return null
        val k = scaleFor(pack, densityDpi)
        val tuned = tunedFor(windowW, windowH, densityDpi, hingeY)
        val landscape = windowW > windowH
        // sideways, a book foldable's inner screen half-folded: the hinge runs across the window, so the two screens too
        val twoUp = if (landscape) hingeY != null && (foldable || tablet) && nearSquare(windowW, windowH) else tablet
        // sideways the flat menu is the landscape shell: half-folded, the bottom screen ends where that one's does, so
        // folding and opening keep its bottom edge in place
        val flatEnd = menuFlatEnd(pack, windowW, windowH, k, topInset, densityDpi)
        if (twoUp && twoScreens) shrunk(k) { s ->
            widest(tuned, pack, SkinFamily.STACKED, SkinVariant.MENU, fold = true) { spec ->
                foldPieces(spec.menuBesideTheDpad(), windowW, windowH, s, null, topInset, hingeY, densityDpi,
                    bottomMargin = shellMargin(pack), bottomGlass = flatEnd, row = rowSpacing(densityDpi, s))
            }
        }?.let { return it }
        return shell(tuned, pack, if (landscape) SkinFamily.GRIP else SkinFamily.STACKED, SkinVariant.MENU, windowW, windowH, k, densityDpi, null, topInset, rowSpacing(densityDpi, k), hingeY = hingeY)
    }

    /**
     * Whether a layout has a box size drawn for this window ([BoxWhen]): such a layout is the one the device was tuned
     * with, tried before its siblings (a box layout has no pieces to rule itself out by).
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
            .sortedWith(compareByDescending<SkinLayoutSpec> { tuned(it) }.thenByDescending { it.minRefH }.thenByDescending { it.height })
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
            .sortedWith(compareByDescending<SkinLayoutSpec> { tuned(it) }.thenByDescending { it.minRefH }.thenByDescending { it.width }).firstNotNullOfOrNull(placed)

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
     * touch screen then moves up to [FRAME_GAP_DP] under it, the controls centred in the height under the frame.
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
        val capPad = topScreen?.let { TOP_PAD_DP * densityDpi / 160f / k }
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

    /** Every control piece of this placement ending above [bottom], window px (the d-pad moved under the stick). */
    private fun SkinPlacement.clearOfTheBottom(bottom: Float): Boolean =
        pieces.filter { it.piece >= 0 && spec.pieces[it.piece].glass == null }.all { it.dst.y + it.dst.height <= bottom + 0.5f }

    /**
     * The d-pad under the stick ([bothFollowMenu]), [padGapDp] apart: the side holding the two (its shoulder too) lifts
     * so the d-pad ends [ROW_GAP_DP] above the window's bottom, never above the bottom screen's frame's top (a landscape
     * shell's: [ROW_GAP_DP] under the window's top); past that, the gap over the d-pad and the room under it come down together (to
     * [ROW_MIN_GAP_DP]).
     */
    private fun SkinPlacement.dpadInTheRoom(windowH: Float, dp: Float, padGapDp: Float = ROW_GAP_DP): SkinPlacement {
        if (boxed) return this
        fun centredOn(id: String): List<Int> {
            val c = controls.firstOrNull { it.spec.id == id } ?: return emptyList()
            return pieces.indices.filter { i -> pieces[i].let { p -> p.piece >= 0 && spec.pieces[p.piece].glass == null &&
                kotlin.math.abs(p.dst.x + p.dst.width / 2f - c.x) < 2f && kotlin.math.abs(p.dst.y + p.dst.height / 2f - c.y) < 2f } }
        }
        val pad = centredOn("DPAD"); val stick = centredOn("STICK")
        if (pad.isEmpty() || stick.isEmpty() || pad.any { it in stick }) return this
        val side = spec.pieces[pieces[stick.first()].piece].anchor
        val sideAt = pieces.indices.filter { i -> pieces[i].piece >= 0 && spec.pieces[pieces[i].piece].let { it.glass == null && it.anchor == side } }
        if (sideAt.isEmpty()) return this
        // the bottom screen's frame: the frame pieces in the window's lower half
        val frames = pieces.filter { it.piece >= 0 && spec.pieces[it.piece].glass != null && it.dst.y + it.dst.height / 2f > windowH / 2f }.map { it.dst }
        // a landscape shell (its screen's frame not a piece): up to [ROW_GAP_DP] under the window's top
        val frameTop = frames.minOfOrNull { it.y } ?: (ROW_GAP_DP * dp)
        fun box(ids: List<Int>) = ids.minOf { pieces[it].dst.y } to ids.maxOf { pieces[it].dst.y + pieces[it].dst.height }
        val (stickTop, stickBottom) = box(stick); val (padTop, padBottom) = box(pad)
        // what stands under the d-pad (the 3DS's MIC / MENU column) goes with it, and ends the side
        val under = sideAt.filter { it !in pad && pieces[it].dst.y >= padBottom - 0.5f }
        val padH = (under.maxOfOrNull { pieces[it].dst.y + pieces[it].dst.height } ?: padBottom).coerceAtLeast(padBottom) - padTop
        val gap = ROW_GAP_DP * dp
        val end = windowH - gap
        // the side lifted by [up], the d-pad by [up] + [closer] (its gap over it closed that much)
        val padGap = padGapDp * dp
        val wantUp = (stickBottom + padGap + padH) - end
        val room = box(sideAt).first - frameTop   // how far the side may go up
        val up = wantUp.coerceIn(0f, maxOf(0f, room))
        // what the lift cannot give: the gap and the room under the d-pad come down alike
        val short = wantUp - up
        val give = if (short > 0f) minOf(short / 2f, padGap - ROW_MIN_GAP_DP * dp) else 0f
        val gapNow = padGap - give
        val padUp = padTop - (stickBottom - up + gapNow)
        val other = if (side == PieceAnchor.LEFT) PieceAnchor.RIGHT else PieceAnchor.LEFT
        if (up == 0f && padUp == 0f) return sideInTheRoom(other, frameTop, end).columnsLevel()
        val sideIds = controls.filter { it.spec.anchor == side }.map { it.spec.id }.toSet()
        fun moved(i: Int) = when (i) { in pad, in under -> padUp; in sideAt -> up; else -> 0f }
        return copy(
            pieces = pieces.mapIndexed { i, p -> moved(i).let { m -> if (m == 0f) p else p.copy(dst = p.dst.copy(y = p.dst.y - m)) } },
            controls = controls.map { c -> when {
                c.spec.id == "DPAD" || (c.spec.anchor == side && c.y > padBottom) -> c.copy(y = c.y - padUp)
                c.spec.anchor == side && c.spec.id in sideIds -> c.copy(y = c.y - up)
                else -> c
            } },
        ).sideInTheRoom(other, frameTop, end).columnsLevel()
    }

    /**
     * The small caps' columns each side (the 3DS's MIC / MENU, SELECT / START / SWAP on its tall shell) level at their
     * foot: the higher one down to the lower one's (its well with it). As it is without a column each side.
     */
    private fun SkinPlacement.columnsLevel(): SkinPlacement {
        if (boxed) return this
        val ids = setOf("START", "SELECT", "MENU", "MIC", "SWAP", "STICK_SWAP", "DISK")
        fun caps(side: PieceAnchor) = controls.filter { it.spec.id in ids && it.spec.anchor == side }
        val l = caps(PieceAnchor.LEFT); val r = caps(PieceAnchor.RIGHT)
        if (l.isEmpty() || r.isEmpty()) return this
        val dy = l.maxOf { it.y } - r.maxOf { it.y }
        if (kotlin.math.abs(dy) < 0.5f) return this
        val (side, move) = if (dy > 0f) PieceAnchor.RIGHT to dy else PieceAnchor.LEFT to -dy
        val mine = if (side == PieceAnchor.LEFT) l else r
        val others = controls.filter { it.spec.anchor == side && it !in mine }
        // a column's pieces: those holding its caps and nothing else
        fun holds(d: com.nxredux.mobile.options.PxRect, c: PlacedControl) = c.x >= d.x && c.x <= d.x + d.width && c.y >= d.y && c.y <= d.y + d.height
        val at = pieces.indices.filter { i -> pieces[i].let { p -> p.piece >= 0 && spec.pieces[p.piece].let { it.glass == null && it.anchor == side } &&
            mine.any { holds(p.dst, it) } && others.none { holds(p.dst, it) } } }
        return copy(
            pieces = pieces.mapIndexed { i, p -> if (i in at) p.copy(dst = p.dst.copy(y = p.dst.y + move)) else p },
            controls = controls.map { if (it in mine) it.copy(y = it.y + move) else it },
        )
    }

    /**
     * The [side] group (the face buttons' side by the stick and the d-pad: the 3DS's taller with its C-stick nub) lifted
     * so its lowest piece ends by [end] (window px), no higher than [frameTop]; as it is where it ends there already.
     */
    private fun SkinPlacement.sideInTheRoom(side: PieceAnchor, frameTop: Float, end: Float): SkinPlacement {
        if (boxed) return this
        val at = pieces.indices.filter { i -> pieces[i].piece >= 0 && spec.pieces[pieces[i].piece].let { it.glass == null && it.anchor == side } }
        if (at.isEmpty()) return this
        val over = at.maxOf { pieces[it].dst.y + pieces[it].dst.height } - end
        val up = over.coerceAtMost(at.minOf { pieces[it].dst.y } - frameTop)
        if (up <= 0f) return this
        return copy(
            pieces = pieces.mapIndexed { i, p -> if (i in at) p.copy(dst = p.dst.copy(y = p.dst.y - up)) else p },
            controls = controls.map { c -> if (c.spec.anchor == side) c.copy(y = c.y - up) else c },
        )
    }

    /**
     * This flat board with [half]'s bottom half (the same board half-folded): its pieces under the lid (the touch screen,
     * the side groups, the row), its controls and its touch glass; the lid (the top screen's frame, a piece of no layout
     * piece) and the background this one's.
     */
    private fun SkinPlacement.bottomOf(half: SkinPlacement): SkinPlacement = copy(
        pieces = pieces.filter { it.piece < 0 } + half.pieces.filter { it.piece >= 0 },
        controls = half.controls,
        glasses = glasses.filter { it.screen != GlassScreen.TOUCH } + half.glasses.filter { it.screen == GlassScreen.TOUCH },
        sideIn = half.sideIn,
    )

    /**
     * SWAP between the shoulders ([startInTheRow]) level with L, its cap and well with it (the row rules move the
     * shoulders, not the centre's caps). As it is with SWAP elsewhere.
     */
    private fun SkinPlacement.swapLevelWithTheShoulders(): SkinPlacement {
        if (boxed) return this
        val menu = controls.firstOrNull { it.spec.id == "SWAP" } ?: return this
        val l = controls.firstOrNull { it.spec.id == "L" } ?: return this
        val reach = menu.spec.r * 1.5f
        if (kotlin.math.abs(menu.y - l.y) > reach) return this
        val dy = l.y - menu.y
        fun near(d: com.nxredux.mobile.options.PxRect) = kotlin.math.abs(d.x + d.width / 2f - menu.x) < reach && kotlin.math.abs(d.y + d.height / 2f - menu.y) < reach
        return copy(
            pieces = pieces.map { p -> if (p.piece >= 0 && spec.pieces[p.piece].anchor == PieceAnchor.CENTRE && spec.pieces[p.piece].glass == null && near(p.dst)) p.copy(dst = p.dst.copy(y = p.dst.y + dy)) else p },
            controls = controls.map { if (it === menu) it.copy(y = l.y) else it },
        )
    }

    /**
     * A landscape shell's small caps in two rows each side (the 3DS's: the stick / d-pad swap and MIC over MENU, SELECT
     * and START over SWAP) where the lower one would end nearer the window's bottom than [ROW_GAP_DP]: each side's in
     * one row in one well instead (the stick / d-pad swap, MIC, MENU; SWAP, SELECT, START), its outer end in line with
     * the side's ring, its inner end [ROW_GAP_DP] clear of the screen's frame (the caps nearer than drawn where that
     * needs), the words under the caps. As it is where the two rows fit, or the one would not.
     */
    private fun SkinPlacement.capsInOneRow(windowH: Float, dp: Float): SkinPlacement {
        if (boxed) return this
        fun all(id: String) = controls.filter { it.spec.id == id }
        fun one(id: String) = controls.firstOrNull { it.spec.id == id }
        val left = listOf("STICK_SWAP", "MIC", "MENU"); val right = listOf("SWAP", "SELECT", "START")
        val ids = left + right
        if (ids.any { one(it) == null }) return this
        val gap = ROW_GAP_DP * dp
        val lower = listOf(one("MENU")!!, one("SWAP")!!)
        if (lower.maxOf { it.y + it.spec.r } <= windowH - gap) return this
        val frame = pieces.filter { it.piece < 0 && !it.strip }.map { it.dst }
        if (frame.isEmpty()) return this
        fun holds(d: com.nxredux.mobile.options.PxRect, c: PlacedControl) = c.x >= d.x && c.x <= d.x + d.width && c.y >= d.y && c.y <= d.y + d.height
        fun centredAt(d: com.nxredux.mobile.options.PxRect, x: Float, y: Float) = kotlin.math.abs(d.x + d.width / 2f - x) < 2f && kotlin.math.abs(d.y + d.height / 2f - y) < 2f
        val mic = one("MIC")!!; val select = one("SELECT")!!
        val leftWell = pieces.indexOfFirst { it.piece >= 0 && holds(it.dst, mic) && holds(it.dst, one("STICK_SWAP")!!) }
        val rightWell = pieces.indexOfFirst { it.piece >= 0 && holds(it.dst, select) && holds(it.dst, one("START")!!) }
        if (leftWell < 0 || rightWell < 0) return this
        val stick = one("STICK") ?: one("DPAD") ?: return this
        val x = one("X") ?: return this; val b = one("B") ?: return this
        val ringLeft = pieces.filter { it.piece >= 0 && centredAt(it.dst, stick.x, stick.y) }.minOfOrNull { it.dst.x } ?: return this
        val ringRight = pieces.filter { it.piece >= 0 && centredAt(it.dst, x.x, (x.y + b.y) / 2f) }.maxOfOrNull { it.dst.x + it.dst.width } ?: return this
        val well = pieces[leftWell]
        val pad = well.dst.height / 2f
        val capW = pieces.filter { it.piece >= 0 && centredAt(it.dst, mic.x, mic.y) }.maxOfOrNull { it.dst.width } ?: return this
        val drawn = kotlin.math.abs(mic.x - one("STICK_SWAP")!!.x)
        val pitch = minOf(drawn, (frame.minOf { it.x } - gap - ringLeft - 2f * pad) / 2f, (ringRight - (frame.maxOf { it.x + it.width } + gap) - 2f * pad) / 2f)
        if (pitch < capW) return this
        val wellW = 2f * pad + 2f * pitch
        val xs = left.mapIndexed { i, id -> id to ringLeft + pad + i * pitch } + right.mapIndexed { i, id -> id to ringRight - wellW + pad + i * pitch }
        val at = xs.toMap()
        val rowY = mapOf("STICK_SWAP" to mic.y, "MIC" to mic.y, "MENU" to mic.y, "SWAP" to select.y, "SELECT" to select.y, "START" to select.y)
        // the pill cut to three caps: its ends as drawn, its flat middle stretched between them
        fun pill(w: SkinPiece, x0: Float): List<SkinPiece> {
            val sh = w.src.height; val end = sh / 2f; val sc = w.dst.height / sh
            return listOf(
                w.copy(src = w.src.copy(width = end), dst = w.dst.copy(x = x0, width = end * sc)),
                w.copy(src = w.src.copy(x = w.src.x + end, width = w.src.width - 2f * end), dst = w.dst.copy(x = x0 + end * sc, width = wellW - 2f * end * sc)),
                w.copy(src = w.src.copy(x = w.src.x + w.src.width - end, width = end), dst = w.dst.copy(x = x0 + wellW - end * sc, width = end * sc)),
            )
        }
        val words = mapOf("MENU" to mic, "SWAP" to select)
        val caps = ids.flatMap { all(it) }
        return copy(
            pieces = pieces.flatMapIndexed { i, p -> when (i) {
                leftWell -> pill(p, ringLeft)
                rightWell -> pill(p.copy(dst = p.dst.copy(y = select.y - pad)), ringRight - wellW)
                else -> caps.firstOrNull { p.piece >= 0 && centredAt(p.dst, it.x, it.y) }?.let { c ->
                    listOf(p.copy(dst = p.dst.copy(x = at.getValue(c.spec.id) - p.dst.width / 2f, y = rowY.getValue(c.spec.id) - p.dst.height / 2f)))
                } ?: listOf(p)
            } },
            controls = controls.map { c ->
                if (c !in caps) c else {
                    val w = words[c.spec.id]
                    c.copy(x = at.getValue(c.spec.id), y = rowY.getValue(c.spec.id),
                        spec = if (w == null) c.spec else c.spec.copy(labelDy = w.spec.labelDy, labelDx = w.spec.labelDx, labelH = w.spec.labelH))
                }
            },
        )
    }

    /**
     * The New 3DS's C-stick nub beside X (a landscape shell's, up-right of it) lowered toward as far from A as from X,
     * clear of X's cap by its reach, its pieces with it (off the shoulders). As it is without a nub.
     */
    private fun SkinPlacement.nubBetweenXAndA(): SkinPlacement {
        if (boxed) return this
        val nub = controls.firstOrNull { it.spec.id == "CSTICK" } ?: return this
        val x = controls.firstOrNull { it.spec.id == "X" } ?: return this
        val a = controls.firstOrNull { it.spec.id == "A" } ?: return this
        if (nub.x <= x.x || a.y == x.y) return this
        // as far from A as from X, or higher where that is nearer X than its cap and the stick's reach (its slop)
        val even = ((a.x * a.x - x.x * x.x) + (a.y * a.y - x.y * x.y) - 2f * nub.x * (a.x - x.x)) / (2f * (a.y - x.y))
        val clear = x.spec.r + nub.spec.r * 1.25f; val across = nub.x - x.x
        val ny = if (across >= clear) even else minOf(even, x.y - kotlin.math.sqrt(clear * clear - across * across))
        if (ny <= nub.y) return this
        // its pieces found by its knob (they may stand off its hit area's middle): the knob and the ring round it, both
        // centred on the nub's new spot
        val knob = pieces.firstOrNull { it.knob && it.knobOf == "CSTICK" } ?: return this
        val kx = knob.dst.x + knob.dst.width / 2f; val ky = knob.dst.y + knob.dst.height / 2f
        fun round(d: com.nxredux.mobile.options.PxRect) = kotlin.math.abs(d.x + d.width / 2f - kx) < 2f && kotlin.math.abs(d.y + d.height / 2f - ky) < 2f
        return copy(
            pieces = pieces.map { if (it.piece >= 0 && round(it.dst)) it.copy(dst = it.dst.copy(x = it.dst.x + nub.x - kx, y = it.dst.y + ny - ky)) else it },
            controls = controls.map { if (it === nub) it.copy(y = ny) else it },
        )
    }

    /**
     * The touch screen's frame cut to its 4:3 picture's height where its width holds the picture shorter than the glass
     * (the Fold upright: no bars over and under the game), its top kept (its gap to the top screen's frame), its strip
     * and the row under it up with its bottom. As it is where the picture fills the glass's height.
     */
    private fun SkinPlacement.touchToItsPictureHeight(): SkinPlacement {
        val g = glass(GlassScreen.TOUCH) ?: return this
        val cut = g.rect.height - g.rect.width * 3f / 4f
        if (cut < 0.5f) return this
        val gi = spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
        val frame = pieces.filter { it.piece == gi && !it.strip }
        if (frame.isEmpty()) return this
        val top = frame.minOf { it.dst.y }; val bottom = frame.maxOf { it.dst.y + it.dst.height }
        fun centre(i: Int) = pieces[i].piece >= 0 && spec.pieces[pieces[i].piece].let { it.glass == null && it.anchor == PieceAnchor.CENTRE }
        val moved = pieces.mapIndexed { i, p -> when {
            p.piece == gi && p.strip -> p.copy(dst = p.dst.copy(y = p.dst.y - cut))
            p.piece == gi && kotlin.math.abs(p.dst.y - top) < 0.5f -> p
            p.piece == gi && kotlin.math.abs(p.dst.y + p.dst.height - bottom) < 0.5f -> p.copy(dst = p.dst.copy(y = p.dst.y - cut))
            p.piece == gi -> p.copy(dst = p.dst.copy(height = p.dst.height - cut))
            centre(i) && p.dst.y >= bottom - 0.5f -> p.copy(dst = p.dst.copy(y = p.dst.y - cut))
            else -> p
        } }
        return copy(
            pieces = moved,
            glasses = glasses.map { if (it === g) it.copy(rect = it.rect.copy(height = it.rect.height - cut)) else it },
            controls = controls.map { c -> if (c.spec.id.startsWith("TOUCH_") || (c.spec.anchor == PieceAnchor.CENTRE && c.y > bottom)) c.copy(y = c.y - cut) else c },
        )
    }








    /**
     * Each side group moved whole so its pad stands where [menu] (the main menu's shell on this window) has it: the left
     * by the stick (or d-pad) to the menu's d-pad, the right by X to the menu's X; never nearer the screen's frame than
     * [ROW_GAP_DP], nor the window's bottom ([windowH]) than that (a word under a cap [ROW_MIN_GAP_DP]), both sides
     * down alike. As it is where either lacks them.
     */
    private fun SkinPlacement.sidesAsTheMenu(menu: SkinPlacement, windowH: Float, dp: Float): SkinPlacement {
        if (boxed) return this
        fun ctl(p: SkinPlacement, id: String) = p.controls.firstOrNull { it.spec.id == id }
        val frame = pieces.filter { it.piece < 0 && !it.strip }.map { it.dst }
        if (frame.isEmpty()) return this
        val gap = ROW_GAP_DP * dp
        fun sideOf(p: SkinPiece) = if (p.piece < 0 || spec.pieces[p.piece].glass != null) null else spec.pieces[p.piece].anchor
        // each side's move across, and down no further than its room over the window's bottom: both sides down alike, so
        // they stay level
        val moves = listOf(PieceAnchor.LEFT to ((ctl(this, "STICK") ?: ctl(this, "DPAD")) to ctl(menu, "DPAD")), PieceAnchor.RIGHT to (ctl(this, "X") to ctl(menu, "X")))
            .mapNotNull { (side, pair) ->
                val (own, theirs) = pair
                if (own == null || theirs == null) return@mapNotNull null
                val at = pieces.filter { sideOf(it) == side }
                if (at.isEmpty()) return@mapNotNull null
                val dx = if (side == PieceAnchor.LEFT) minOf(theirs.x - own.x, frame.minOf { it.x } - gap - at.maxOf { it.dst.x + it.dst.width })
                    else maxOf(theirs.x - own.x, frame.maxOf { it.x + it.width } + gap - at.minOf { it.dst.x })
                // the room under it: its pieces and caps [ROW_GAP_DP] over the bottom, a word under a cap [ROW_MIN_GAP_DP]
                val minGap = ROW_MIN_GAP_DP * dp
                val room = minOf(at.minOf { windowH - gap - (it.dst.y + it.dst.height) },
                    controls.filter { it.spec.anchor == side }.minOfOrNull { c ->
                        val cap = c.y + maxOf(c.spec.r, c.spec.hh)
                        val word = if (c.spec.labelDx == 0f && c.spec.labelH > 0f) cap + c.spec.labelDy + c.spec.labelH else cap
                        minOf(windowH - gap - cap, windowH - minGap - word)
                    } ?: Float.MAX_VALUE)
                Triple(side, dx, minOf(theirs.y - own.y, maxOf(0f, room)))
            }
        if (moves.isEmpty()) return this
        val dy = moves.minOf { it.third }
        val dxOf = moves.associate { it.first to it.second }
        fun dx(side: PieceAnchor?) = dxOf[side] ?: 0f
        val out = copy(
            pieces = pieces.map { p -> sideOf(p)?.takeIf { it in dxOf }?.let { side -> p.copy(dst = p.dst.copy(x = p.dst.x + dx(side), y = p.dst.y + dy)) } ?: p },
            controls = controls.map { c -> if (c.spec.anchor in dxOf) c.copy(x = c.x + dx(c.spec.anchor), y = c.y + dy) else c },
        )
        return out
    }

    /** Control [id] (the d-pad, a stick: its pieces, its hit area and a stick's travel) [f] its size, about its middle. */
    private fun SkinPlacement.scaled(id: String, f: Float): SkinPlacement {
        if (boxed) return this
        val c = controls.firstOrNull { it.spec.id == id } ?: return this
        // its pieces: those round its middle, or a stick's round its knob (they may stand off its hit area's)
        val knob = pieces.firstOrNull { it.knob && it.knobOf == id }?.dst
        val mx = knob?.let { it.x + it.width / 2f } ?: c.x; val my = knob?.let { it.y + it.height / 2f } ?: c.y
        fun centred(d: com.nxredux.mobile.options.PxRect) = kotlin.math.abs(d.x + d.width / 2f - mx) < 2f && kotlin.math.abs(d.y + d.height / 2f - my) < 2f
        return copy(
            pieces = pieces.map { p -> if (p.piece >= 0 && spec.pieces[p.piece].glass == null && centred(p.dst))
                p.copy(dst = p.dst.copy(x = c.x - p.dst.width * f / 2f, y = c.y - p.dst.height * f / 2f, width = p.dst.width * f, height = p.dst.height * f)) else p },
            controls = controls.map { if (it === c) it.copy(spec = it.spec.copy(r = it.spec.r * f, arm = it.spec.arm * f, capR = it.spec.capR * f,
                travel = it.spec.travel * f, knobTravel = it.spec.knobTravel * f)) else it },
        )
    }

    /** Window px each side of the touch screen's glass past its 4:3 picture (its height the limit); 0 without one. */
    private fun SkinPlacement.touchSpare(): Float =
        glass(GlassScreen.TOUCH)?.rect?.let { (it.width - it.height * 4f / 3f) / 2f } ?: 0f

    /**
     * The touch screen's frame cut to its 4:3 picture (where its height holds the picture narrower than the glass), its
     * strip's slots with it, centred where it was. As it is where the picture fills the glass's width.
     */
    private fun SkinPlacement.touchToItsPicture(): SkinPlacement {
        val g = glass(GlassScreen.TOUCH) ?: return this
        val cut = touchSpare()
        if (cut < 0.5f) return this
        val gi = spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
        val frame = pieces.filter { it.piece == gi && !it.strip }
        if (frame.isEmpty()) return this
        val left = frame.minOf { it.dst.x }; val right = frame.maxOf { it.dst.x + it.dst.width }
        val cx = g.rect.x + g.rect.width / 2f
        val ratio = (g.rect.width - 2f * cut) / g.rect.width
        fun across(x: Float) = cx + (x - cx) * ratio
        return copy(
            pieces = pieces.map { p -> when {
                p.piece != gi -> p
                p.strip -> p.copy(dst = p.dst.copy(x = across(p.dst.x + p.dst.width / 2f) - p.dst.width / 2f))
                kotlin.math.abs(p.dst.x - left) < 0.5f -> p.copy(dst = p.dst.copy(x = p.dst.x + cut))
                kotlin.math.abs(p.dst.x + p.dst.width - right) < 0.5f -> p.copy(dst = p.dst.copy(x = p.dst.x - cut))
                else -> p.copy(dst = p.dst.copy(x = p.dst.x + cut, width = p.dst.width - 2f * cut))
            } },
            glasses = glasses.map { if (it === g) it.copy(rect = it.rect.copy(x = it.rect.x + cut, width = it.rect.width - 2f * cut)) else it },
            controls = controls.map { c -> if (c.spec.id.startsWith("TOUCH_")) c.copy(spec = c.spec.copy(hw = c.spec.hw * ratio), x = across(c.x)) else c },
        )
    }

    /**
     * The 3DS board beside a touch screen cut to its picture ([touchToItsPicture], [touchToItsPictureHeight]): L level
     * with R and the stick with the face buttons; under the stick the d-pad, under the face buttons the New 3DS's
     * C-stick ([withNewControls]), each moved in toward the screen as far as the pieces level with it allow (the touch
     * screen's frame, the row under it; higher on its slant for it), [ROW_GAP_DP] clear of them, its ring
     * [PAIR_GAP_DP] from the one over it. Where one would end nearer the window's bottom than
     * [ROW_GAP_DP] both sides lift alike, no higher than the touch screen's frame; past that its gap gives, down to
     * [ROW_MIN_GAP_DP].
     */
    private fun SkinPlacement.padsStaggered(windowH: Float, dp: Float): SkinPlacement {
        if (boxed) return this
        fun centredAt(x: Float, y: Float) = pieces.indices.filter { i -> pieces[i].let { p -> p.piece >= 0 && spec.pieces[p.piece].glass == null &&
            kotlin.math.abs(p.dst.x + p.dst.width / 2f - x) < 2f && kotlin.math.abs(p.dst.y + p.dst.height / 2f - y) < 2f } }
        fun ctl(id: String) = controls.firstOrNull { it.spec.id == id }
        val stickC = ctl("STICK") ?: return this; val padC = ctl("DPAD") ?: return this
        val l = ctl("L") ?: return this; val r = ctl("R") ?: return this
        val x = ctl("X") ?: return this; val b = ctl("B") ?: return this
        val nubC = ctl("CSTICK")
        val stick = centredAt(stickC.x, stickC.y); val pad = centredAt(padC.x, padC.y)
        val nub = nubC?.let { centredAt(it.x, it.y) }.orEmpty()
        val fx = x.x; val fy = (x.y + b.y) / 2f
        val face = centredAt(fx, fy).filter { it !in nub }
        if (stick.isEmpty() || pad.isEmpty() || pad.any { it in stick } || face.isEmpty() || (nubC != null && nub.isEmpty())) return this
        val gi = spec.pieces.indexOfFirst { it.glass?.screen == GlassScreen.TOUCH }
        val frame = pieces.filter { it.piece == gi && !it.strip }.map { it.dst }
        if (frame.isEmpty()) return this
        fun sideOf(i: Int) = pieces[i].let { p -> if (p.piece < 0 || spec.pieces[p.piece].glass != null) null else spec.pieces[p.piece].anchor }
        fun holds(d: com.nxredux.mobile.options.PxRect, c: PlacedControl) = c.x >= d.x && c.x <= d.x + d.width && c.y >= d.y && c.y <= d.y + d.height
        // the left shoulder and its companion (the New 3DS's ZL)
        val shoulderIds = spec.shoulderIds() + spec.companionIds()
        val shoulders = controls.filter { it.spec.id in shoulderIds && it.spec.anchor == PieceAnchor.LEFT }
        val shoulderAt = pieces.indices.filter { i -> sideOf(i) == PieceAnchor.LEFT && shoulders.any { holds(pieces[i].dst, it) } }
        val gap = ROW_GAP_DP * dp; val pair = PAIR_GAP_DP * dp
        val shoulderDy = r.y - l.y
        val stickDy = fy - stickC.y
        fun width(ids: List<Int>) = ids.maxOf { pieces[it].dst.width }
        // one ring under another ([upper] wide), [inward] across: how far under it its middle goes for a gap [g]
        fun under(upper: Float, lower: Float, inward: Float, g: Float): Float { val d = (upper + lower) / 2f + g; return kotlin.math.sqrt(maxOf(0f, d * d - inward * inward)) }
        // the gap that ends a ring [lower] wide [drop] under the middle of the one over it, on its slant
        fun gapFor(upper: Float, lower: Float, inward: Float, drop: Float) =
            minOf(pair, maxOf(ROW_MIN_GAP_DP * dp, kotlin.math.sqrt(drop * drop + inward * inward) - (upper + lower) / 2f))
        val ring = width(stick); val padRing = width(pad); val faceRing = width(face)
        // as far in as the pieces level with it allow (the touch screen's frame, the row under it), [ROW_GAP_DP] clear:
        // found at its height for one reach, then checked again at the height the further reach gives it
        val blocks = frame + pieces.filter { p -> p.piece >= 0 && spec.pieces[p.piece].let { it.glass == null && it.anchor == PieceAnchor.CENTRE } }.map { it.dst }
        fun reach(y: Float): Float {
            val x0 = blocks.filter { it.x > stickC.x && it.y < y + padRing / 2f + gap && it.y + it.height > y - padRing / 2f - gap }.minOfOrNull { it.x }
                ?: return padRing / 2f
            return ((x0 - gap) - (stickC.x + padRing / 2f)).coerceIn(0f, padRing / 2f)
        }
        val padIn = reach(fy + under(ring, padRing, 0f, pair)).let { first -> minOf(first, reach(fy + under(ring, padRing, first, pair))) }
        val nubRing = if (nubC != null) width(nub) else 0f
        fun reachLeft(y: Float): Float {
            val x1 = blocks.filter { it.x + it.width < fx && it.y < y + nubRing / 2f + gap && it.y + it.height > y - nubRing / 2f - gap }.maxOfOrNull { it.x + it.width }
                ?: return nubRing / 2f
            return ((fx - nubRing / 2f) - (x1 + gap)).coerceIn(0f, nubRing / 2f)
        }
        val nubIn = if (nubC == null) 0f else reachLeft(fy + under(faceRing, nubRing, 0f, pair)).let { first -> minOf(first, reachLeft(fy + under(faceRing, nubRing, first, pair))) }
        // both sides' lift where a lower ring would end too low, then what its gap gives
        val end = windowH - gap
        val over = maxOf(fy + under(ring, padRing, padIn, pair) + padRing / 2f - end,
            if (nubC == null) Float.NEGATIVE_INFINITY else fy + under(faceRing, nubRing, nubIn, pair) + nubRing / 2f - end)
        val sides = pieces.indices.filter { sideOf(it) == PieceAnchor.LEFT || sideOf(it) == PieceAnchor.RIGHT }
        val top = sides.filter { it !in pad && it !in nub }.minOf { i -> pieces[i].dst.y + (if (i in shoulderAt) shoulderDy else if (i in stick) stickDy else 0f) }
        val up = over.coerceIn(0f, maxOf(0f, top - frame.minOf { it.y }))
        val padY = fy + under(ring, padRing, padIn, gapFor(ring, padRing, padIn, minOf(under(ring, padRing, padIn, pair), end + up - padRing / 2f - fy)))
        val nubY = if (nubC == null) 0f else fy + under(faceRing, nubRing, nubIn, gapFor(faceRing, nubRing, nubIn, minOf(under(faceRing, nubRing, nubIn, pair), end + up - nubRing / 2f - fy)))
        val padX = stickC.x + padIn; val nubX = fx - nubIn
        fun dy(i: Int) = when {
            i in pad -> padY - padC.y
            i in nub -> nubY - nubC!!.y
            i in shoulderAt -> shoulderDy
            i in stick -> stickDy
            else -> 0f
        } - (if (i in sides) up else 0f)
        fun dx(i: Int) = when { i in pad -> padX - padC.x; i in nub -> nubX - nubC!!.x; else -> 0f }
        return copy(
            pieces = pieces.mapIndexed { i, p -> if (dx(i) == 0f && dy(i) == 0f) p else p.copy(dst = p.dst.copy(x = p.dst.x + dx(i), y = p.dst.y + dy(i))) },
            controls = controls.map { c -> when {
                c === padC -> c.copy(x = padX, y = padY - up)
                c === nubC -> c.copy(x = nubX, y = nubY - up)
                c === stickC -> c.copy(y = fy - up)
                c in shoulders -> c.copy(y = c.y + shoulderDy - up)
                c.spec.anchor == PieceAnchor.LEFT || c.spec.anchor == PieceAnchor.RIGHT -> c.copy(y = c.y - up)
                else -> c
            } },
        )
    }

    /** The consoles whose layouts follow the main menu's (the arcade's, the Dreamcast's, the FDS's, the Game Boys', the DS's and the 3DS's), on the phone and landscape boards too. */
    private val FOLLOW_MENU = setOf(SkinVariant.ARCADE, SkinVariant.DC, SkinVariant.FDS, SkinVariant.GB, SkinVariant.GBA, SkinVariant.DS) + N3DS_VARIANTS

    /**
     * [variant]'s layout [this] following [menu] ([followMenu]) when it is one of [FOLLOW_MENU]'s, else as it is. The
     * DS's and the 3DS's MENU stays with its MIC and SWAP.
     */
    private fun SkinLayoutSpec.toMenu(variant: SkinVariant, menu: SkinLayoutSpec?): SkinLayoutSpec =
        if (variant in N3DS_VARIANTS) followMenu(menu, skip = setOf("MENU")).withStart(this).nubAsDrawn(this)
        else if (variant == SkinVariant.DS) followMenu(menu, skip = setOf("MENU")).withStart(this)
        else if (variant in FOLLOW_MENU) followMenu(menu) else this

    /** The row spacing for [variant]'s phone and landscape boards: [FOLLOW_MENU]'s only (the others keep their own). */
    private fun rowFor(variant: SkinVariant, densityDpi: Int, k: Float): RowSpacing? = if (variant in FOLLOW_MENU) rowSpacing(densityDpi, k) else null

    /** The main menu's layout for [game]'s board (the same family, render and height class), its reference. */
    private fun menuTwin(pack: SkinPackManifest, game: SkinLayoutSpec): SkinLayoutSpec? =
        pack.layouts.firstOrNull { it.family == game.family && it.variant == SkinVariant.MENU && !it.foldBoard && it.refW == game.refW && it.refH == game.refH && it.minRefH == game.minRefH }

    /** The main menu's landscape shell drawn at [game]'s size (the same render, any height class). */
    private fun menuShellOf(pack: SkinPackManifest, game: SkinLayoutSpec): SkinLayoutSpec? =
        pack.layouts.filter { it.family == game.family && it.variant == SkinVariant.MENU && !it.foldBoard && it.refW == game.refW && it.refH == game.refH }
            .maxByOrNull { it.minRefH }

    /** The main menu's fold board drawn at [game]'s size (the same render), the game fold boards' reference. */
    private fun menuFoldBoard(pack: SkinPackManifest, game: SkinLayoutSpec): SkinLayoutSpec? =
        pack.layouts.firstOrNull { it.family == SkinFamily.STACKED && it.variant == SkinVariant.MENU && it.foldBoard && it.refW == game.refW && it.refH == game.refH }

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
        // (never for the Dreamcast, the 3DS, the GameCube, the Wii pads, the PlayStations or the PSP: the shared set has
        // no stick, nor two screens)
        if (own != null || v == SkinVariant.DS || v == SkinVariant.SINGLE || v == SkinVariant.DC || v == SkinVariant.N3DS || v == SkinVariant.N3DS_REGULAR || v == SkinVariant.GC || v == SkinVariant.WII_CLASSIC || v == SkinVariant.WII_NUNCHUK || v == SkinVariant.WII_SIDEWAYS || v == SkinVariant.WII_UPRIGHT || v == SkinVariant.PS1 || v == SkinVariant.PSP) return own
        return placeAs(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, SkinVariant.SINGLE)
    }

    /**
     * [place], else the closest layout scaled down: [place] at [densityDpi] x 0.9^n (n = 1, 2, ...) until one fits, down
     * to half the dpi (a window no layout fits: split screen, freeform); null when even that fits none.
     */
    fun placeClosest(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>? = null, hingeY: Float? = null, topInset: Int = 0, foldable: Boolean = false,
        variant: SkinVariant? = null,
    ): SkinPlacement? {
        place(pack, windowW, windowH, densityDpi, tablet, ds, picture, hingeY, topInset, foldable, variant)?.let { return it }
        var dpi = densityDpi.toDouble()
        while (true) {
            dpi *= 0.9
            val d = Math.round(dpi).toInt()
            if (d < densityDpi / 2.0 || d <= 0) return null
            place(pack, windowW, windowH, d, tablet, ds, picture, hingeY, topInset, foldable, variant)?.let { return it }
        }
    }

    private fun placeAs(
        pack: SkinPackManifest, windowW: Int, windowH: Int, densityDpi: Int, tablet: Boolean, ds: Boolean,
        picture: Pair<Float, Float>?, hingeY: Float?, topInset: Int, foldable: Boolean, v: SkinVariant,
    ): SkinPlacement? {
        val k = scaleFor(pack, densityDpi)
        val tuned = tunedFor(windowW, windowH, densityDpi, hingeY)
        // the top screen's game size: the large layout's top frame takes its shape
        val top = when (v) { SkinVariant.N3DS, SkinVariant.N3DS_REGULAR -> 400f to 240f; SkinVariant.DS -> 256f to 192f; else -> null }
        val landscape = windowW > windowH
        // sideways: the main menu's landscape shell, the game in its screen frame (the strip under it; a DS's MIC, SWAP)
        // [both]: the Dreamcast's board with the stick and the d-pad both, tried first at every scale (shrunk to fit) over the
        // board showing one at a time
        val flatEnd by lazy { menuFlatEnd(pack, windowW, windowH, k, topInset, densityDpi) }
        fun foldBoardOf(both: Boolean?) = shrunk(k) { s ->
            widest(tuned, pack, SkinFamily.FLEX, v, fold = true, refH = if (both != null) Float.MAX_VALUE else windowH / s) { spec ->
                if (both != null && (spec.minRefH > 0) != both) return@widest null
                // a console following the menu takes the board drawn as the menu's (no tablet twin: the menu has none)
                if (both == null && v in FOLLOW_MENU && v !in N3DS_VARIANTS && v != SkinVariant.DS && spec.minRefH > 0) return@widest null
                // the main menu's fold board the reference: its spacing (so the side groups stand where the menu's do)
                // and its face buttons' height
                val menu = menuFoldBoard(pack, spec)
                val shaped = if (both == true) spec.bothFollowMenu(menu?.menuBesideTheDpad())
                    else if (v in N3DS_VARIANTS) spec.bothFollowMenu(menu?.menuBesideTheDpad(), menuUnderStart = false)
                    // the DS's drawn as the menu's already (its own START / SELECT and row): the face where the menu's is
                    else if (v == SkinVariant.DS) spec.faceAt(menu)
                    else spec.toMenu(v, menu?.menuBesideTheDpad()).faceAt(menu)
                // a screen frame with nothing under it as far from the window's bottom as the menu's
                fun fold(sp: SkinLayoutSpec, hinge: Float? = hingeY) = foldPieces(sp, windowW, windowH, s, picture, topInset, hinge, densityDpi, top, bottomMargin = shellMargin(pack),
                    bottomGlass = flatEnd, row = rowSpacing(densityDpi, s), centreControls = v !in N3DS_VARIANTS)
                val placed = fold(shaped)
                val dp = densityDpi / 160f
                // the 3DS's touch screen held back by its height (a wide window): the frame cut to its picture, the width
                // it gives up the side groups', START / SELECT in the row under it
                // the New 3DS's as the regular 3DS's board (its buttons where that one has them), with ZL / ZR and a
                // full C-stick under the face buttons: where the touch screen is cut to its picture
                val regular = if (v != SkinVariant.N3DS) null else pack.layouts.firstOrNull {
                    it.variant == SkinVariant.N3DS_REGULAR && it.family == spec.family && it.refW == spec.refW && it.refH == spec.refH && it.minRefH == spec.minRefH
                }?.copy(boxes = emptyList())   // its own pieces (its boxes are its own drawing, without the New 3DS's controls)
                // (a layout with boxes draws that already: its own boxes)
                val newBoard by lazy { if (spec.boxes.isNotEmpty()) null else regular?.let { reg -> reg.bothFollowMenu(menuFoldBoard(pack, reg)?.menuBesideTheDpad(), menuUnderStart = false)
                    .withNewControls(spec, PAIR_GAP_DP * dp / s) } }
                // which of the branches below: by the board as its pieces place (a layout with boxes: its own pieces, not
                // its boxes, which hold the touch screen already cut)
                val branch = if (spec.boxes.isEmpty()) placed else fold(shaped.copy(boxes = emptyList()))
                if (v in N3DS_VARIANTS && placed != null && branch != null && branch.touchSpare() >= WIDE_TOUCH_DP * dp) {
                    val caps = (newBoard ?: shaped).capsInTheRow(ROW_GAP_DP * dp / s)
                    // (a layout with boxes has no row under its touch screen, which then grows down: cut to its picture's
                    // height as well)
                    fun wide(hinge: Float?) = fold(caps, hinge)?.touchToItsPicture()?.let { if (spec.boxes.isNotEmpty()) it.touchToItsPictureHeight() else it }
                        ?.padsStaggered(windowH.toFloat(), dp)
                    val wide = wide(hingeY)
                    // flat: the bottom half as half-folded at the window's middle (the touch screen not grown up into the
                    // hinge's room), so folding never moves it; the flat lid kept
                    val half = if (hingeY == null && wide != null) wide(windowH / 2f) else null
                    (if (wide != null && half != null) wide.bottomOf(half) else wide)?.let { return@widest it }
                }
                // the touch screen's width holding its picture shorter than the glass (the Fold upright): the frame cut to
                // the picture's height, the sides level, a smaller d-pad and New 3DS C-stick on a slant
                if (v in N3DS_VARIANTS && placed != null && branch != null && branch.touchSpare() < 0f)
                    fold((newBoard ?: shaped).capsInTheRow(ROW_GAP_DP * dp / s))?.touchToItsPictureHeight()?.scaled("DPAD", SMALL_DPAD)?.scaled("CSTICK", SMALL_DPAD)
                        ?.padsStaggered(windowH.toFloat(), dp)?.let { return@widest it }
                // START / SELECT in the row under the touch screen; where its height holds it back with no width to
                // spare (the Ultra upright) the d-pad [PAIR_GAP_DP] under the stick
                if (v in N3DS_VARIANTS && placed != null)
                    fold(shaped.capsInTheRow(ROW_GAP_DP * dp / s))?.dpadInTheRoom(windowH.toFloat(), dp, PAIR_GAP_DP)?.let { return@widest it }
                placed?.let { if (both == true || v in N3DS_VARIANTS) it.dpadInTheRoom(windowH.toFloat(), dp) else it }
            }
        }
        val foldBoard = { if (v == SkinVariant.DC) foldBoardOf(true) ?: foldBoardOf(false) else foldBoardOf(null) }
        if (landscape) {
            // a book foldable's inner screen sideways: its hinge runs across the window, where the board's is. A DS or 3DS
            // takes the board flat too (the crease between its screens); a one-screen game only half-folded, as flat the
            // landscape shell gives it the window's whole height.
            if ((foldable || tablet) && nearSquare(windowW, windowH) && (hingeY != null || v in TWO_SCREEN)) foldBoard()?.let { return it }
            // the Dreamcast on a tablet / an inner screen: the stick and the d-pad both, as on its fold board
            if (v == SkinVariant.DC && (foldable || tablet)) shell(tuned, pack, SkinFamily.GRIP, v, windowW, windowH, k, densityDpi, picture, topInset, rowFor(v, densityDpi, k), hingeY = hingeY,
                refH = Float.MAX_VALUE, pick = { it.minRefH > 0 && it.controls.none { c -> c.id == "STICK_SWAP" } }) { spec -> spec.bothFollowMenu(menuShellOf(pack, spec)) }
                ?.dpadInTheRoom(windowH.toFloat(), densityDpi / 160f)?.let { return it }
            // the N64 on a tablet / an inner screen: the stick and the d-pad both, MENU and START under B / A (the left's
            // room under the stick is the d-pad's)
            if (v == SkinVariant.N64 && (foldable || tablet)) shell(tuned, pack, SkinFamily.GRIP, v, windowW, windowH, k, densityDpi, picture, topInset, rowSpacing(densityDpi, k), hingeY = hingeY,
                pick = { it.minRefH > 0 && it.controls.none { c -> c.id == "STICK_SWAP" } && it.controls.any { c -> c.id == "MENU" && c.anchor == PieceAnchor.RIGHT } })
                ?.let { return it }
            // the 3DS's tall shell (the stick and the d-pad both) where it fits: its small caps in a row each side; the New
            // 3DS's as the regular 3DS's shell with ZL / ZR and a full C-stick under the face buttons, as the d-pad under the stick
            if (v in N3DS_VARIANTS) shell(tuned, pack, SkinFamily.GRIP, v, windowW, windowH, k, densityDpi, picture, topInset, rowFor(v, densityDpi, k), hingeY = hingeY,
                pick = { it.controls.none { c -> c.id == "STICK_SWAP" } }) { spec ->
                val regular = if (v != SkinVariant.N3DS) null else pack.layouts.firstOrNull {
                    it.variant == SkinVariant.N3DS_REGULAR && it.family == spec.family && it.refW == spec.refW && it.refH == spec.refH && it.minRefH == spec.minRefH
                }?.copy(boxes = emptyList())   // its own pieces (its boxes are its own drawing, without the New 3DS's controls)
                val board = regular?.let { reg -> reg.bothFollowMenu(menuShellOf(pack, reg), menuUnderStart = false).withNewControls(spec, ROW_GAP_DP * densityDpi / 160f / k) }
                    ?: spec.bothFollowMenu(menuShellOf(pack, spec), menuUnderStart = false)
                board.capsSideways(pack.layouts.firstOrNull { it.variant == SkinVariant.N3DS_REGULAR && it.foldBoard })
            }?.dpadInTheRoom(windowH.toFloat(), densityDpi / 160f)?.let { return it }
            return shell(tuned, pack, SkinFamily.GRIP, v, windowW, windowH, k, densityDpi, picture, topInset, rowFor(v, densityDpi, k), hingeY = hingeY) { spec ->
                // the DS's and the 3DS's small-cap columns (the Fold cover's): a pair over one each side (the DS's
                // SELECT / START alone, its SWAP with MIC)
                val pairs = when {
                    !spec.id.endsWith("_col") -> null
                    v in N3DS_VARIANTS -> listOf(Triple("STICK_SWAP", "MIC", "MENU"), Triple("SELECT", "START", "SWAP"))
                    v == SkinVariant.DS -> listOf(Triple("MIC", "SWAP", "MENU"), Triple<String, String, String?>("SELECT", "START", null))
                    else -> null
                }
                spec.toMenu(v, menuTwin(pack, spec)).let { s -> pairs?.let { s.capsInPairs(pack.layouts.firstOrNull { it.variant == SkinVariant.N3DS_REGULAR && it.foldBoard }, it) } ?: s }
            }?.let { p -> if ((v in N3DS_VARIANTS || v == SkinVariant.DS) && p.spec.id.endsWith("_col")) placeMenu(pack, windowW, windowH, densityDpi, foldable = foldable, tablet = tablet)
                ?.let { p.sidesAsTheMenu(it, windowH.toFloat(), densityDpi / 160f).columnsLevel() } ?: p else p }
                // the DS's small-cap columns level at their foot (START / SELECT where the menu's are, MIC / MENU its own)
                ?.let { if (v == SkinVariant.DS) it.columnsLevel() else it }
                ?.let { if (v in N3DS_VARIANTS) it.capsInOneRow(windowH.toFloat(), densityDpi / 160f).nubBetweenXAndA() else it }
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
                    SkinSolver.placePieces(s, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), topScreen = top, row = rowFor(v, densityDpi, k),
                        roomLeft = r.left, roomRight = r.right, roomBottom = r.bottom)
                }
                else if (spec.frame != null) SkinSolver.placePieces(spec.toMenu(v, menuTwin(pack, spec)).let { s ->
                    // the New 3DS's ZL / ZR beside L / R where SELECT / START stood: those in the row under the pad, SWAP
                    // between the shoulders
                    if (v == SkinVariant.N3DS && family == SkinFamily.STACKED) s.startInTheRow(ROW_GAP_DP * densityDpi / 160f / k, pack.layouts.firstOrNull { it.variant == v && it.foldBoard }) else s
                }, windowW.toFloat(), windowH.toFloat(), k, picture, topInset.toFloat(), topScreen = top, row = rowFor(v, densityDpi, k))
                    ?.let { if (v == SkinVariant.N3DS && family == SkinFamily.STACKED) it.swapLevelWithTheShoulders() else it }
                else SkinSolver.place(spec, windowW.toFloat(), windowH.toFloat(), k, picture)
            }?.let { return it }
        }
        return null
    }
}
