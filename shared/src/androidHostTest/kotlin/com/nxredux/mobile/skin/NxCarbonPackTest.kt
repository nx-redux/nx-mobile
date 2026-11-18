package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The built-in NX Carbon pack (androidApp's assets) on the devices it was made on and others: every layout parses, its
 * controls sit in fixed slices, and each window gets the layout and screen scales expected. With SKIN_PREVIEW_DIR set,
 * each placement is written there as JSON (for preview pictures).
 */
class NxCarbonPackTest {
    /** The 3DS's twins of the DS layouts (stacked, large and its wide, flex and its wide, the fold board, both landscapes), the fold board and the tall landscape with both sticks shown. */
    private val N3DS_LAYOUTS = 16   // the New 3DS twins and the regular 3DS twins, 8 each
    private val GRIP_COLUMNS = 21   // each landscape shell's twin with the small caps in a column (the Fold cover)
    /** How far (picture px) the frame's touch slots keep below the screen, so a press on one never lands on the picture. */
    private val SLOT_SCREEN_GAP = 12f

    /** A landscape layout with each side's small caps in one column (a `_col` twin, or a tall shell). */
    private fun SkinPlacement.columns() = spec.family == SkinFamily.GRIP && (spec.id.endsWith("_col") || spec.id.contains("_tall"))

    /** [top] over [under], one column. */
    private fun stacked(top: PlacedControl, under: PlacedControl, what: String) {
        assertEquals(top.x, under.x, 1f, "$what one column"); assertTrue(under.y > top.y, "$what in order")
    }
    private val dir = File("../androidApp/src/main/assets/skins/nx-carbon")
    private val pack = assertNotNull(SkinPackManifest.parse(File(dir, "skin.json").readText()))

    private fun place(name: String, w: Int, h: Int, dpi: Int = 420, tablet: Boolean = false, ds: Boolean = true, picture: Pair<Float, Float>? = null, hinge: Float? = null, foldable: Boolean = false): SkinPlacement? =
        SkinChoice.place(pack, w, h, dpi, tablet, ds, picture, hinge, foldable = foldable).also { p -> dump(name, w, h, p) }

    private fun dump(name: String, w: Int, h: Int, p: SkinPlacement?) {
        val out = System.getenv("SKIN_PREVIEW_DIR") ?: return
        File(out).mkdirs(); File(out, "$name.json").writeText(skinPreviewJson(w, h, p))
    }

    @Test
    fun everyLayoutParsesAndItsControlsAreInFixedSlices() {
        assertEquals(161 + N3DS_LAYOUTS + GRIP_COLUMNS, pack.layouts.size)   // the menu half-folded on a phone (flex_menu); 6 one-screen layouts, each with its gb, gba, arcade, fds, dc, n64, gc and Wii Classic twins, dc / n64 both-shown on the tall boards (fold, landscape tall), gc's on the fold board (its tall landscape always both; the Wii's fold board and tall landscape always both); the Wii Nunchuk's, sideways and upright Remote's in every family (the Nunchuk's fold board and tall landscape always both); the PlayStation's and the PSP's in every family (their fold board and tall landscape always both); PICO-8's, the Atari 5200's, Doom's, the Sega pad's two (6- and 3-button) and the Saturn's in every family; a tablet twin of each game fold board without one (17: the clusters centred on the bottom screen, user)
        for (spec in pack.layouts) {
            if (spec.frame != null) continue   // built from pieces: placed by placePieces (below)
            if (spec.scales.any { t -> t.any { it <= 0f } && spec.glasses.any { it.screen == GlassScreen.PICTURE } }) continue   // a picture glass grows to fit
            assertTrue(File(dir, spec.image).isFile, spec.image)
            // at its own size every control maps to itself and every glass to its own spot
            val p = assertNotNull(SkinSolver.place(spec, spec.width.toFloat(), spec.height.toFloat(), 1f, picture = spec.glasses.first().let { it.w.toFloat() to it.h.toFloat() }), spec.id)
            for (c in p.controls) {
                val s = spec.controls.first { it.id == c.spec.id }
                assertEquals(s.x, c.x, 1f, "${spec.id} ${s.id} x"); assertEquals(s.y, c.y, 1f, "${spec.id} ${s.id} y")
            }
        }
    }

    private val gbSet = setOf("DPAD", "A", "B", "X", "Y", "SELECT", "START", "MENU")
    private val gbaSet = gbSet + setOf("L", "R", "L2", "R2")

    // The screen hole's rounded corners are in the frame's picture: every corner of every glass must fall in a frame part
    // drawn as it is, never in an edge stretched from a 2 px slice (the corner then comes out square and the board shows
    // through it, as the cover's game frame did under its thick strip border)
    @Test
    fun glassCornersAreNeverInAStretchedFrameEdge() {
        val windows = listOf(Triple(1248, 1972, null), Triple(1080, 2520, null), Triple(1848, 2448, null), Triple(1848, 2448, 1236f), Triple(2520, 1080, null), Triple(2448, 1848, null))
        for (v in listOf(SkinVariant.SINGLE, SkinVariant.GB, SkinVariant.GBA, SkinVariant.DS)) for ((w, h, hinge) in windows) {
            val p = SkinChoice.place(pack, w, h, 420, false, v == SkinVariant.DS, if (v == SkinVariant.DS) null else 240f to 160f, hinge,
                foldable = w == 1848 || w == 2448, variant = v) ?: continue
            for (g in p.glasses.filter { it.corner > 0f }) {
                val r = g.rect; val c = g.corner
                val corners = listOf(r.x to r.y, r.x + r.width - c to r.y, r.x to r.y + r.height - c, r.x + r.width - c to r.y + r.height - c)
                for ((cx, cy) in corners) for (pc in p.pieces.filter { it.src.width == 2f || it.src.height == 2f }) {
                    val d = pc.dst
                    val overlap = d.x < cx + c - 0.5f && cx + 0.5f < d.x + d.width && d.y < cy + c - 0.5f && cy + 0.5f < d.y + d.height
                    assertTrue(!overlap, "${p.spec.id} ${w}x$h ${g.id} corner ($cx, $cy) in a stretched edge $d")
                }
            }
        }
    }

    @Test
    fun gameBoyLayoutsHaveTheirButtonsInEveryFamily() {
        for ((v, ids) in mapOf(SkinVariant.GB to gbSet, SkinVariant.GBA to gbaSet)) {
            val ls = pack.layouts.filter { it.variant == v }
            assertEquals(8, ls.size, "$v")   // six families, the fold board's tablet twin and the landscape column twin
            for (l in ls) assertEquals(ids, l.controls.map { it.id }.toSet(), l.id)
        }
    }

    // every device a GB or GBA game is played on: the console's own layout, never the shared one
    private val consoleDevices = listOf(
        "cover" to Triple(1248, 1972, null), "flip" to Triple(1080, 2520, null), "inner" to Triple(1848, 2448, null),
        "inner_half" to Triple(1848, 2448, 1236f), "tablet" to Triple(1440, 2304, null), "phone_land" to Triple(2520, 1080, null),
        "inner_land" to Triple(2448, 1848, null))

    private fun consolePlace(v: SkinVariant, name: String, d: Triple<Int, Int, Float?>): SkinPlacement {
        val tablet = name == "tablet"
        return assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, if (v == SkinVariant.GB) 160f to 144f else 240f to 160f,
            d.third, foldable = name.startsWith("inner"), variant = v), "$v $name")
    }

    @Test
    fun gameBoyGamesGetTheirLayoutOnEveryDevice() {
        for (v in listOf(SkinVariant.GB, SkinVariant.GBA)) for ((name, d) in consoleDevices) assertEquals(v, consolePlace(v, name, d).spec.variant, "$v $name")
    }

    @Test
    fun oneScreenGamesOnTheFoldBoardHaveTheBottomScreen() {
        val hinges = mapOf("inner" to 1224f, "inner_half" to 1236f, "tablet" to 1152f)
        for (v in listOf(SkinVariant.SINGLE, SkinVariant.GB, SkinVariant.GBA)) for ((name, hinge) in hinges) {
            val id = "$v $name"
            val p = consolePlace(v, name, consoleDevices.toMap().getValue(name))
            dump("fold_panel_${v.name.lowercase()}_$name", consoleDevices.toMap().getValue(name).first, consoleDevices.toMap().getValue(name).second, p)
            assertEquals("flex_${if (v == SkinVariant.SINGLE) "single" else v.name.lowercase()}_fold", p.spec.id.removeSuffix("_tab"), id)   // the tablet: its twin
            val panel = p.glasses.single { it.screen == GlassScreen.PANEL }
            assertEquals("panel", panel.id, id)
            val r = panel.rect
            val c = p.controls.associateBy { it.spec.id }
            val menu = c.getValue("MENU")
            // MENU under the panel, or beside the d-pad where the board follows the menu's (the Game Boy's)
            val under = menu.spec.anchor == PieceAnchor.CENTRE
            if (v == SkinVariant.GB) { assertTrue(!under && menu.spec.labelDx < 0f && menu.spec.labelDy == 0f, "$id MENU beside the d-pad, its word on the left") }
            assertTrue(r.y > hinge && (!under || r.y + r.height < menu.y - menu.spec.r), "$id panel $r hinge $hinge menu ${menu.y}")
            for (ctl in p.controls) {
                val hw = if (ctl.spec.r > 0f) ctl.spec.r else ctl.spec.hw
                val hh = if (ctl.spec.r > 0f) ctl.spec.r else ctl.spec.hh
                val apart = ctl.x + hw <= r.x || r.x + r.width <= ctl.x - hw || ctl.y + hh <= r.y || r.y + r.height <= ctl.y - hh
                assertTrue(apart, "$id ${ctl.spec.id} (${ctl.x}, ${ctl.y}) on the panel $r")
            }
            val pic = p.glass(GlassScreen.PICTURE)!!.rect
            // half-folded clear of the hinge; flat (no hinge drawn) the frame reaches the middle, the picture above it
            val folded = consoleDevices.toMap().getValue(name).third != null
            if (folded) assertTrue(pic.y + pic.height <= hinge - 62f, "$id picture $pic") else assertTrue(pic.y + pic.height < r.y, "$id picture $pic over the panel $r")
            assertEquals(!folded, p.slab, "$id slab")
            // the touch shortcut strip under the panel, as every game layout has; no MIC or SWAP
            assertTrue((1..4).all { "TOUCH_$it" in c } && c.keys.none { it == "MIC" || it == "SWAP" }, "$id ${c.keys}")
            val strip = (1..4).map { c.getValue("TOUCH_$it") }
            assertTrue(strip.all { it.y - it.spec.hh >= r.y + r.height && (!under || it.y + it.spec.hh <= menu.y - menu.spec.r) }, "$id strip under the panel, over MENU")
        }
    }

    @Test
    fun foldBoardTopScreensHaveTheSameShellAboveAsBeside() {
        val fold = pack.layouts.filter { it.tree.parts.any { p -> p.isHinge } && it.frame != null }
        assertTrue(fold.map { it.id }.containsAll(listOf("stacked_menu_fold", "flex_ds_fold", "flex_single_fold", "flex_gb_fold", "flex_gba_fold")), "${fold.map { it.id }}")
        for (l in fold) assertEquals(l.frame!!.side, l.frame!!.top, l.id)
        // placed on the inner screen: the frame's top margin as its left one
        val p = assertNotNull(SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true))
        val f = p.pieces.first()
        assertEquals(f.dst.x, f.dst.y, 1f)
    }

    @Test
    fun foldBoardDsAndMenuAreUnchanged() {
        val ds = assertNotNull(place("fold_ds_kept", 1848, 2448, tablet = true, foldable = true))
        assertEquals("flex_ds_fold", ds.spec.id.removeSuffix("_tab"))
        assertNotNull(ds.glass(GlassScreen.TOUCH))
        assertTrue(listOf("TOUCH_1", "TOUCH_2", "TOUCH_3", "TOUCH_4").all { t -> ds.controls.any { it.spec.id == t } })
        val menu = assertNotNull(SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true))
        assertEquals(setOf("menutop", "menu"), menu.glasses.map { it.id }.toSet())
    }

    @Test
    fun fdsLayoutsInEveryFamily() {
        val hinges = setOf("inner", "inner_half", "tablet")
        for ((name, d) in consoleDevices) {
            val tablet = name == "tablet"
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 256f to 240f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.FDS), name)
            dump("fds_$name", d.first, d.second, p)
            assertEquals(SkinVariant.FDS, p.spec.variant, name)
            assertEquals(setOf("DPAD", "A", "B", "X", "Y", "DISK", "SELECT", "START", "MENU"),
                p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet(), name)
            val disk = p.controls.single { it.spec.id == "DISK" }; val menu = p.controls.single { it.spec.id == "MENU" }
            assertEquals("DISK", disk.spec.label, name)
            fun box(c: PlacedControl) = (if (c.spec.r > 0f) c.spec.r else c.spec.hw).let { hw -> val hh = if (c.spec.r > 0f) c.spec.r else c.spec.hh
                floatArrayOf(c.x - hw, c.y - hh, c.x + hw, c.y + hh) }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            for (c in p.controls) if (c !== disk) assertTrue(apart(box(disk), box(c)), "$name DISK on ${c.spec.id}")
            if (name in hinges) {
                // the fold board: DISK over MENU under the d-pad, a column level with START / SELECT's, clear of the panel
                val dp = p.controls.single { it.spec.id == "DPAD" }
                stacked(disk, menu, "$name DISK over MENU"); assertTrue(disk.y > dp.y, name)
                val st = p.controls.single { it.spec.id == "START" }; val se = p.controls.single { it.spec.id == "SELECT" }
                assertEquals((st.y + se.y) / 2f, (disk.y + menu.y) / 2f, 1f, "$name level with START / SELECT")
                for (c in listOf(disk, menu)) { assertTrue(c.spec.labelDx < 0f, "$name ${c.spec.id} word on the left"); assertEquals(0f, c.spec.labelDy, "$name ${c.spec.id} word level") }
                val r = p.glasses.single { it.screen == GlassScreen.PANEL }.rect
                for (c in p.controls) assertTrue(apart(box(c), floatArrayOf(r.x, r.y, r.x + r.width, r.y + r.height)), "$name ${c.spec.id} on the panel")
            } else if (p.columns()) stacked(disk, menu, "$name DISK over MENU")
            else {
                assertEquals(menu.y, disk.y, 1f, name); assertTrue(disk.x < menu.x, name)
            }
        }
    }

    @Test
    fun gbaFollowsTheMenuItsL2R2BesideLR() {
        val devices = listOf(Triple(1080, 2400, false), Triple(1848, 2448, true), Triple(1440, 2304, true), Triple(2400, 1080, false), Triple(2448, 1848, true))
        for ((w, h, big) in devices) {
            val dpi = if (w == 1440) 280 else 420
            val menu = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = big && w != 1440, tablet = big))
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, big, false, 240f to 160f, null, foldable = big && w != 1440, variant = SkinVariant.GBA))
            val mc = menu.controls.associateBy { it.spec.id }; val gc = p.controls.associateBy { it.spec.id }
            for (id in listOf("DPAD", "Y", "START", "SELECT", "MENU")) {
                assertEquals(mc.getValue(id).x, gc.getValue(id).x, 1.5f, "${w}x$h $id x"); assertEquals(mc.getValue(id).y, gc.getValue(id).y, 1.5f, "${w}x$h $id y")
            }
            fun cap(id: String) = gc.getValue(id).let { c -> p.pieces.filter { pc -> pc.piece >= 0 && p.spec.pieces[pc.piece].glass == null &&
                c.x >= pc.dst.x && c.x <= pc.dst.x + pc.dst.width && c.y >= pc.dst.y && c.y <= pc.dst.y + pc.dst.height }.minBy { it.dst.width }.dst }
            val dp = dpi / 160f
            val l = cap("L"); val l2 = cap("L2"); val r = cap("R"); val r2 = cap("R2")
            assertEquals(8f * dp, l2.x - (l.x + l.width), 1.5f, "${w}x$h L L2"); assertEquals(l.y, l2.y, 1.5f, "${w}x$h L2 level")
            assertEquals(8f * dp, r.x - (r2.x + r2.width), 1.5f, "${w}x$h R2 R"); assertEquals(r.y, r2.y, 1.5f, "${w}x$h R2 level")
            assertEquals(mc.getValue("L").y, gc.getValue("L").y, 1.5f, "${w}x$h L at the menu's height")
        }
    }

    @Test
    fun fdsFallsBackToSingleWithoutItsLayouts() {
        val old = pack.copy(layouts = pack.layouts.filter { it.variant != SkinVariant.FDS })
        val p = assertNotNull(SkinChoice.place(old, 1080, 2520, 420, tablet = false, ds = false, picture = 256f to 240f, variant = SkinVariant.FDS))
        assertEquals(SkinVariant.SINGLE, p.spec.variant)
    }

    @Test
    fun arcadeLayoutsHaveTheirButtonsInEveryFamily() {
        val hinges = mapOf("inner" to 1224f, "inner_half" to 1236f, "tablet" to 1152f)
        for ((name, d) in consoleDevices) {
            val tablet = name == "tablet"
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 320f to 224f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.ARCADE), name)
            dump("arcade_$name", d.first, d.second, p)
            assertEquals(SkinVariant.ARCADE, p.spec.variant, name)
            assertEquals(setOf("DPAD", "A", "B", "X", "Y", "L", "R", "TURBO_B", "TURBO_A", "START", "SELECT", "MENU"),
                p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet(), name)
            assertEquals("COIN", p.controls.first { it.spec.id == "SELECT" }.spec.label, name)
            fun box(id: String) = p.controls.first { it.spec.id == id }.let { floatArrayOf(it.x - it.spec.hw, it.y - it.spec.hh, it.x + it.spec.hw, it.y + it.spec.hh) }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            val ids = listOf("L", "TURBO_B", "TURBO_A", "R")
            for (i in ids.indices) for (j in i + 1 until ids.size) assertTrue(apart(box(ids[i]), box(ids[j])), "$name ${ids[i]} ${ids[j]}")
            for (sh in ids) for (c in listOf("DPAD", "A", "B", "X", "Y")) {
                val o = p.controls.first { it.spec.id == c }
                assertTrue(apart(box(sh), floatArrayOf(o.x - o.spec.r, o.y - o.spec.r, o.x + o.spec.r, o.y + o.spec.r)), "$name $sh $c")
            }
            if (name in hinges) {
                val r = p.glasses.single { it.screen == GlassScreen.PANEL }.rect
                for (c in p.controls) {
                    val hw = if (c.spec.r > 0f) c.spec.r else c.spec.hw; val hh = if (c.spec.r > 0f) c.spec.r else c.spec.hh
                    assertTrue(apart(floatArrayOf(c.x - hw, c.y - hh, c.x + hw, c.y + hh), floatArrayOf(r.x, r.y, r.x + r.width, r.y + r.height)), "$name ${c.spec.id} on the panel")
                }
            }
        }
    }

    @Test
    fun n64LayoutsInEveryFamily() {
        // the stick as Dreamcast's (both only with real extra height); the C diamond, B / A in their own well, Z L R, START MENU
        // the tall landscape (the stick where the menu's d-pad is, the d-pad under it) and the Fold's inner screen upright
        // (the d-pad under the stick): both always out, no switch
        val roomy = setOf("tablet", "tablet_land", "inner_land", "inner", "inner_half")
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.N64), name)
            dump("n64_$name", d.first, d.second, p)
            assertEquals(SkinVariant.N64, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val base = setOf("STICK", "DPAD", "CU", "CL", "CR", "CD", "A", "B", "L2", "L", "R", "START", "MENU")
            assertEquals(if (name in roomy) base else base + "STICK_SWAP", ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.STICK) pc.spec.r * 1.25f else pc.spec.r
            fun round(a: PlacedControl, b: PlacedControl) = kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
            fun circle(pc: PlacedControl) = pc.spec.shape == ControlShape.CIRCLE || pc.spec.shape == ControlShape.STICK
            val face = setOf("CU", "CL", "CR", "CD", "A", "B")   // round caps: kept apart as circles (the diamond's touch areas share edges)
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) {
                        if (setOf(a.spec.id, b.spec.id) == setOf("A", "B")) assertTrue(round(a, b), "$name A B")
                        continue
                    }
                    // round touch areas (the stick's, the caps') as circles: B / A sit on the diagonal by the stick and the switch cap
                    assertTrue(if (circle(a) && circle(b)) round(a, b) else apart(box(a), box(b)), "$name ${a.spec.id} ${b.spec.id}")
                }
                for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
            }
            if (name in roomy) assertTrue(c("STICK").y < c("DPAD").y, "$name stick above the d-pad")
            // B and A apart: 8 dp between their edges
            assertTrue(kotlin.math.hypot(c("A").x - c("B").x, c("A").y - c("B").y) - c("A").spec.r - c("B").spec.r >= 8f * (if (tablet) 280 else 420) / 160f - 1f, "$name B A apart")
            // L / R where the main menu's are (Z starting where its L does), but the tablet's upright board's
            if (name != "tablet") {
                val m = assertNotNull(SkinChoice.placeMenu(pack, d.first, d.second, if (tablet) 280 else 420, foldable = name.startsWith("inner"),
                    tablet = tablet || name.startsWith("inner"), hingeY = d.third), "$name menu")
                fun mc(id: String) = m.controls.first { it.spec.id == id }
                assertEquals(mc("R").x, c("R").x, 1f, "$name R x"); assertEquals(mc("R").y, c("R").y, 1f, "$name R y")
                assertEquals(mc("L").x - mc("L").spec.hw, c("L2").x - c("L2").spec.hw, 1f, "$name Z from the menu's L"); assertEquals(mc("L").y, c("L").y, 1f, "$name L y")
            }
            // B up left of A (a phone sideways and the Flip upright: side by side, level (user)); Z nearer the thumb than L
            // (outside it, or under it)
            if (name == "phone_land" || name == "flip") { assertTrue(c("B").x < c("A").x, "$name B left of A"); assertEquals(c("B").y, c("A").y, 1f, "$name B level with A") }
            else assertTrue(c("B").x < c("A").x && c("B").y < c("A").y, "$name B up left of A")
            assertTrue(c("L2").x < c("L").x || c("L2").y > c("L").y, "$name Z outside or under L")
            assertEquals("MENU", c("MENU").spec.label, name)
        }
    }

    @Test
    fun wiiClassicPhonePortrait() {
        // the Classic Controller: the left stick and d-pad, the diamond, L ZL ZR R, the right stick, − + on top, the swap cap MENU HOME in the bottom row
        for (name in listOf("flip", "cover")) {
            val d = consoleDevices.toList().first { it.first == name }.second
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, 420, false, false, 640f to 480f, d.third, variant = SkinVariant.WII_CLASSIC), name)
            dump("wiic_$name", d.first, d.second, p)
            assertEquals(SkinVariant.WII_CLASSIC, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            assertEquals(setOf("STICK", "DPAD", "A", "B", "X", "Y", "L", "L2", "R2", "R", "CSTICK", "SELECT", "HOME", "START", "MENU", "STICK_SWAP"), ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            assertTrue(c("SELECT").x < c("START").x && c("SELECT").y == c("START").y, "$name − + in a row")
            assertTrue(c("STICK_SWAP").x < c("MENU").x && c("MENU").x < c("HOME").x && c("STICK_SWAP").y == c("MENU").y && c("HOME").y == c("MENU").y, "$name swap MENU HOME in a row")
            assertTrue(c("HOME").y > c("START").y, "$name HOME below + (the bottom row)")
            assertEquals(listOf("−", "+", "HOME"), listOf(c("SELECT"), c("START"), c("HOME")).map { it.spec.label }, name)
            assertTrue(c("CSTICK").spec.r > c("X").spec.capR, "$name the right stick's nub bigger than a face cap")
            assertTrue(c("SELECT").spec.labelH > 2 * c("MENU").spec.labelH && c("START").spec.labelH == c("SELECT").spec.labelH, "$name − + drawn larger than the words")
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                val face = setOf("A", "B", "X", "Y")
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    fun reach(pc: PlacedControl) = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                    fun box(pc: PlacedControl): FloatArray { val r = reach(pc); val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh; return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh) }
                    val round = a.spec.shape != ControlShape.RECT && b.spec.shape != ControlShape.RECT
                    val ok = if (round) kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
                        else box(a).let { x -> box(b).let { y -> x[2] <= y[0] || y[2] <= x[0] || x[3] <= y[1] || y[3] <= x[1] } }
                    assertTrue(ok, "$name ${a.spec.id} ${b.spec.id}")
                }
            }
        }
    }

    @Test
    fun doomLayoutsInEveryFamily() {
        // the standard pad (d-pad, A B X Y, L R, SELECT START) with L2 / R2 inside L / R and L3 / R3 by MENU
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.DOOM), name)
            dump("doom_$name", d.first, d.second, p)
            assertEquals(SkinVariant.DOOM, p.spec.variant, name)
            val cs = p.controls.filterNot { it.spec.id.startsWith("TOUCH_") }
            assertEquals(setOf("DPAD", "A", "B", "X", "Y", "L", "R", "L2", "R2", "SELECT", "START", "L3", "R3", "MENU"), cs.map { it.spec.id }.toSet(), name)
            fun c(id: String) = cs.first { it.spec.id == id }
            assertTrue(c("L").x < c("L2").x && c("R2").x < c("R").x, "$name L2 / R2 inside L / R")
            val face = setOf("A", "B", "X", "Y")
            for (i in cs.indices) for (j in i + 1 until cs.size) {
                val a = cs[i]; val b = cs[j]
                if (a.spec.id in face && b.spec.id in face) continue
                fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.RECT) 0f else pc.spec.r
                fun box(pc: PlacedControl): FloatArray { val r = reach(pc); val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh; return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh) }
                val round = a.spec.shape == ControlShape.CIRCLE && b.spec.shape == ControlShape.CIRCLE
                val ok = if (round) kotlin.math.hypot(a.x - b.x, a.y - b.y) >= a.spec.r + b.spec.r - 1f
                    else box(a).let { x -> box(b).let { y -> x[2] <= y[0] || y[2] <= x[0] || x[3] <= y[1] || y[3] <= x[1] } }
                assertTrue(ok, "$name ${a.spec.id} ${b.spec.id}")
            }
            for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
        }
    }

    @Test
    fun atari5200LayoutsInEveryFamily() {
        // the joystick (first; the d-pad by the swap cap, both on the fold board and the tall landscape), FIRE 1 (A) down left and
        // FIRE 2 (B) up right, * (Y) / # (X) on the shoulders, PAUSE START, KEYPAD (L) by MENU
        val roomy = setOf("inner", "inner_half", "tablet", "tablet_land", "inner_land")
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.A5200), name)
            dump("a5200_$name", d.first, d.second, p)
            assertEquals(SkinVariant.A5200, p.spec.variant, name)
            val cs = p.controls.filterNot { it.spec.id.startsWith("TOUCH_") }
            val base = setOf("STICK", "DPAD", "A", "B", "X", "Y", "SELECT", "START", "L", "MENU")
            assertEquals(if (name in roomy) base else base + "STICK_SWAP", cs.map { it.spec.id }.toSet(), name)
            fun c(id: String) = cs.first { it.spec.id == id }
            assertTrue(c("A").x < c("B").x && c("A").y > c("B").y, "$name FIRE 1 down left of FIRE 2")
            assertTrue(c("Y").spec.shape == ControlShape.RECT && c("X").spec.shape == ControlShape.RECT && c("Y").x < c("X").x, "$name * left, # right shoulders")
            assertEquals("KEYPAD", c("L").spec.label, name)
            if (name == "phone_land") { assertEquals(c("MENU").x, c("L").x, 1f, "$name MENU under KEYPAD"); assertTrue(c("MENU").y > c("L").y, name) }
            else if (p.columns()) stacked(c("L"), c("MENU"), "$name KEYPAD over MENU")
            else assertEquals(c("MENU").y, c("L").y, 1f, "$name KEYPAD beside MENU")
            for (useStick in listOf(true, false)) {
                val sc = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                for (i in sc.indices) for (j in i + 1 until sc.size) {
                    val a = sc[i]; val b = sc[j]
                    fun reach(pc: PlacedControl) = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                    fun box(pc: PlacedControl): FloatArray { val r = reach(pc); val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh; return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh) }
                    val round = a.spec.shape != ControlShape.RECT && b.spec.shape != ControlShape.RECT
                    val ok = if (round) kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
                        else box(a).let { x -> box(b).let { y -> x[2] <= y[0] || y[2] <= x[0] || x[3] <= y[1] || y[3] <= x[1] } }
                    assertTrue(ok, "$name ${a.spec.id} ${b.spec.id}")
                }
                for (x in sc) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
            }
            if (name in roomy) {
                assertTrue(c("STICK").y < c("DPAD").y, "$name the stick above the d-pad")
                assertEquals(c("STICK").y, (c("A").y + c("B").y) / 2f, 1f, "$name the pair level with the stick")
            } else assertTrue(c("STICK").spec.r > c("DPAD").spec.r, "$name the swapped stick full size")
        }
    }

    @Test
    fun pico8LayoutsInEveryFamily() {
        // the d-pad, O (B) down left and X (A) up right in the face's place, START beside MENU, no shoulders
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.P8), name)
            dump("p8_$name", d.first, d.second, p)
            assertEquals(SkinVariant.P8, p.spec.variant, name)
            val cs = p.controls.filterNot { it.spec.id.startsWith("TOUCH_") }
            assertEquals(setOf("DPAD", "B", "A", "START", "MENU"), cs.map { it.spec.id }.toSet(), name)
            fun c(id: String) = cs.first { it.spec.id == id }
            assertTrue(c("B").x < c("A").x && c("B").y > c("A").y, "$name O down left of X (user: rising to the right)")
            if (p.columns()) stacked(c("MENU"), c("START"), "$name MENU over START")
            else { assertEquals(c("MENU").y, c("START").y, 1f, "$name START beside MENU (user)"); assertTrue(c("START").x > c("MENU").x, "$name START right of MENU") }
            for (i in cs.indices) for (j in i + 1 until cs.size) {
                val a = cs[i]; val b = cs[j]
                if (a.spec.shape == ControlShape.DPAD || b.spec.shape == ControlShape.DPAD) {
                    val dp = if (a.spec.shape == ControlShape.DPAD) a else b; val o = if (dp === a) b else a
                    assertTrue(kotlin.math.abs(o.x - dp.x) > dp.spec.r + o.spec.r - 1f || kotlin.math.abs(o.y - dp.y) > dp.spec.r + o.spec.r - 1f, "$name d-pad ${o.spec.id}")
                } else assertTrue(kotlin.math.hypot(a.x - b.x, a.y - b.y) >= a.spec.r + b.spec.r - 1f, "$name ${a.spec.id} ${b.spec.id}")
            }
            for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
        }
    }

    @Test
    fun pspLayoutsInEveryFamily() {
        // △ ○ × □, L / R (no L2 R2), the one stick (swapped with the d-pad; both, the stick under the d-pad, on the fold board and
        // the tall landscape), no right stick, no L3 R3, SELECT START, MENU
        val roomy = setOf("inner", "inner_half", "tablet", "tablet_land", "inner_land")
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.PSP), name)
            dump("psp_$name", d.first, d.second, p)
            assertEquals(SkinVariant.PSP, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val base = setOf("DPAD", "STICK", "A", "B", "X", "Y", "L", "R", "SELECT", "START", "MENU")
            assertEquals(if (name in roomy) base else base + "STICK_SWAP", ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.STICK) pc.spec.r * 1.25f else pc.spec.r
            fun round(a: PlacedControl, b: PlacedControl) = kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
            fun circle(pc: PlacedControl) = pc.spec.shape == ControlShape.CIRCLE || pc.spec.shape == ControlShape.STICK
            val face = setOf("A", "B", "X", "Y")
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    assertTrue(if (circle(a) && circle(b)) round(a, b) else apart(box(a), box(b)), "$name ${a.spec.id} ${b.spec.id}")
                }
                for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
            }
            assertEquals(1, p.pieces.count { it.knob }, name)
            if (name in roomy) {   // the d-pad on top, the stick (smaller) close under it
                assertTrue(c("DPAD").y < c("STICK").y, "$name d-pad above the stick")
                assertEquals(c("DPAD").y, c("Y").y, 1f, "$name the face level with the d-pad")
                assertTrue(c("STICK").spec.r < c("DPAD").spec.r, "$name the stick scaled down")
                val ring = 206f * p.k
                val lower = (c("STICK").y - ring * c("STICK").spec.r / (170f * p.k)) - (c("DPAD").y + ring)
                assertEquals(28f * p.k, lower, 2f * p.k, "$name the stick close under the d-pad (user)")
            } else assertTrue(c("STICK").spec.r > c("DPAD").spec.r, "$name the swapped stick full size")
        }
    }

    @Test
    fun ps1LayoutsInEveryFamily() {
        // the d-pad (the left stick by the swap cap; both, the DualShock's way, on the fold board and the tall landscape), △ ○ × □,
        // L2 L1 / R1 R2, the right stick (the nub by △; its own stick where both show), SELECT START, L3 R3, MENU
        val roomy = setOf("inner", "inner_half", "tablet", "tablet_land", "inner_land")
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.PS1), name)
            dump("ps1_$name", d.first, d.second, p)
            assertEquals(SkinVariant.PS1, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val base = setOf("DPAD", "STICK", "A", "B", "X", "Y", "L", "L2", "R", "R2", "CSTICK", "SELECT", "START", "L3", "MENU", "R3")
            assertEquals(if (name in roomy) base else base + "STICK_SWAP", ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.STICK) pc.spec.r * 1.25f else pc.spec.r
            fun round(a: PlacedControl, b: PlacedControl) = kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
            fun circle(pc: PlacedControl) = pc.spec.shape == ControlShape.CIRCLE || pc.spec.shape == ControlShape.STICK
            val face = setOf("A", "B", "X", "Y")
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    // L3 / R3 tucked at their stick's ring (the two-stick boards): only clear of the stick's own touch circle
                    if (setOf(a.spec.id, b.spec.id).let { it == setOf("STICK", "L3") || it == setOf("CSTICK", "R3") }) {
                        val stick = if (a.spec.shape == ControlShape.STICK) a else b
                        assertTrue(kotlin.math.hypot(a.x - b.x, a.y - b.y) > stick.spec.r, "$name ${a.spec.id} ${b.spec.id}")
                        continue
                    }
                    assertTrue(if (circle(a) && circle(b)) round(a, b) else apart(box(a), box(b)), "$name ${a.spec.id} ${b.spec.id}")
                }
                for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
            }
            assertEquals(2, p.pieces.count { it.knob }, name)
            assertTrue(c("L2").x < c("L").x && c("R").x < c("R2").x, "$name L2 L1 / R1 R2")
            if (name in roomy) {   // the DualShock: d-pad and face level on top, both sticks smaller and level under them, one gap
                assertTrue(c("DPAD").y < c("STICK").y, "$name d-pad above the stick")
                assertEquals(c("DPAD").y, c("Y").y, 1f, "$name the face level with the d-pad")
                assertEquals(c("STICK").y, c("CSTICK").y, 1f, "$name the sticks level")
                assertTrue(c("STICK").spec.r < c("DPAD").spec.r, "$name sticks scaled down")
                val ring = 206f * p.k
                val upper = (c("DPAD").y - ring) - (c("L").y + c("L").spec.hh)
                val lower = (c("STICK").y - ring * c("STICK").spec.r / (170f * p.k)) - (c("DPAD").y + ring)
                assertEquals(upper, lower, 2f * p.k, "$name one gap")
            } else assertTrue(c("STICK").spec.r > c("DPAD").spec.r, "$name the swapped stick full size")
        }
    }

    @Test
    fun ps1PhonePortrait() {
        // L2 L1 | SELECT START | R1 R2; the d-pad (the left stick by the swap cap); △ ○ × □ with the right stick nub by △; the swap cap L3 MENU R3
        for (name in listOf("flip", "cover")) {
            val d = consoleDevices.toList().first { it.first == name }.second
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, 420, false, false, 640f to 480f, d.third, variant = SkinVariant.PS1), name)
            dump("ps1_$name", d.first, d.second, p)
            assertEquals(SkinVariant.PS1, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            assertEquals(setOf("DPAD", "STICK", "A", "B", "X", "Y", "L", "L2", "R", "R2", "CSTICK", "SELECT", "START", "STICK_SWAP", "L3", "MENU", "R3"), ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            assertTrue(c("L2").x < c("L").x && c("L").x < c("SELECT").x && c("START").x < c("R").x && c("R").x < c("R2").x, "$name L2 L1 SELECT START R1 R2")
            val row = listOf("STICK_SWAP", "L3", "MENU", "R3").map(::c)
            for (i in 1 until row.size) { assertEquals(row[0].y, row[i].y, 1f, "$name bottom row"); assertTrue(row[i].x > row[i - 1].x, "$name bottom row order") }
            assertTrue(c("CSTICK").x > c("X").x && c("CSTICK").y < c("A").y, "$name the right stick nub up and right of △")
            assertTrue(c("STICK").spec.r > c("DPAD").spec.r, "$name the swapped stick full size")
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                val face = setOf("A", "B", "X", "Y")
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    fun reach(pc: PlacedControl) = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                    fun box(pc: PlacedControl): FloatArray { val r = reach(pc); val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh; return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh) }
                    val round = a.spec.shape != ControlShape.RECT && b.spec.shape != ControlShape.RECT
                    val ok = if (round) kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
                        else box(a).let { x -> box(b).let { y -> x[2] <= y[0] || y[2] <= x[0] || x[3] <= y[1] || y[3] <= x[1] } }
                    assertTrue(ok, "$name ${a.spec.id} ${b.spec.id}")
                }
            }
        }
    }

    @Test
    fun wiiNunchukPhonePortrait() {
        // SHAKE N Z | − + | C SHAKE; the stick (the Remote's d-pad by the swap cap); the Remote's 1 2 B A diamond; the swap cap MENU HOME MOTION
        for (name in listOf("flip", "cover")) {
            val d = consoleDevices.toList().first { it.first == name }.second
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, 420, false, false, 640f to 480f, d.third, variant = SkinVariant.WII_NUNCHUK), name)
            dump("wiin_$name", d.first, d.second, p)
            assertEquals(SkinVariant.WII_NUNCHUK, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            assertEquals(setOf("STICK", "DPAD", "A", "B", "START", "SELECT", "L2", "Y", "X", "R2", "L", "R", "STICK_SWAP", "MENU", "HOME", "MOTION"), ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            assertTrue(c("L2").x < c("Y").x && c("Y").x < c("L").x && c("L").x < c("R").x && c("R").x < c("X").x && c("X").x < c("R2").x, "$name SHAKE N Z − + C SHAKE")
            assertEquals(listOf("−", "+"), listOf(c("L").spec.label, c("R").spec.label), name)
            assertTrue(c("START").y < c("A").y && c("SELECT").x < c("A").x && c("B").y > c("A").y, "$name 1 top, 2 left, B bottom, A right")
            val row = listOf("STICK_SWAP", "MENU", "HOME", "MOTION").map(::c)
            for (i in 1 until row.size) { assertEquals(row[0].y, row[i].y, 1f, "$name bottom row"); assertTrue(row[i].x > row[i - 1].x, "$name bottom row order") }
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                val face = setOf("A", "B", "START", "SELECT")
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    fun reach(pc: PlacedControl) = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                    fun box(pc: PlacedControl): FloatArray { val r = reach(pc); val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh; return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh) }
                    val round = a.spec.shape != ControlShape.RECT && b.spec.shape != ControlShape.RECT
                    val ok = if (round) kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
                        else box(a).let { x -> box(b).let { y -> x[2] <= y[0] || y[2] <= x[0] || x[3] <= y[1] || y[3] <= x[1] } }
                    assertTrue(ok, "$name ${a.spec.id} ${b.spec.id}")
                }
            }
        }
    }

    @Test
    fun wiiSidewaysLayoutsInEveryFamily() {
        // the d-pad alone, the 1 2 B A diamond, B (the trigger) on the left shoulder too and SHAKE on the right, − +, HOME, MOTION, MENU
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.WII_SIDEWAYS), name)
            dump("wiis_$name", d.first, d.second, p)
            assertEquals(SkinVariant.WII_SIDEWAYS, p.spec.variant, name)
            val cs = p.controls.filterNot { it.spec.id.startsWith("TOUCH_") }
            assertEquals(setOf("DPAD", "A", "B", "X", "Y", "R2", "SELECT", "START", "MENU", "HOME", "MOTION"), cs.map { it.spec.id }.toSet(), name)
            val ys = cs.filter { it.spec.id == "Y" }
            assertEquals(2, ys.size, "$name B in the diamond and on the shoulder")
            val shoulderB = ys.first { it.spec.shape == ControlShape.RECT }; val faceB = ys.first { it.spec.shape != ControlShape.RECT }
            fun c(id: String) = cs.first { it.spec.id == id && (id != "Y" || it.spec.shape != ControlShape.RECT) }
            assertTrue(shoulderB.x < c("R2").x && shoulderB.y < c("B").y, "$name B left shoulder, SHAKE right")
            assertTrue(c("B").y < c("X").y && c("A").x < c("X").x && faceB.y > c("X").y, "$name 1 top, 2 left, B bottom, A right")
            for (id in listOf("HOME", "MOTION")) assertTrue(cs.any { it.spec.id == id }, "$name $id")
            if (name in setOf("inner", "inner_half", "tablet")) for (id in listOf("SELECT", "START"))
                assertEquals(c("A").x, c(id).x, 1f, "$name $id on 2's (the diamond's left)")
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            fun round(a: PlacedControl, b: PlacedControl) = kotlin.math.hypot(a.x - b.x, a.y - b.y) >= a.spec.r + b.spec.r - 1f
            fun circle(pc: PlacedControl) = pc.spec.shape == ControlShape.CIRCLE
            val face = setOf(c("B"), c("A"), c("X"), faceB)
            for (i in cs.indices) for (j in i + 1 until cs.size) {
                val a = cs[i]; val b = cs[j]
                if (a in face && b in face) continue
                assertTrue(if (circle(a) && circle(b)) round(a, b) else apart(box(a), box(b)), "$name ${a.spec.id} ${b.spec.id}")
            }
            for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
        }
    }

    @Test
    fun wiiUprightIsTheSidewaysPictureWiredToTheUprightBinds() {
        // the same pieces and places as the sideways skin; the diamond's 1 2 B A and the shoulder's B on X Y B A, B
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            fun place(v: SkinVariant) = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = v), "$name $v")
            val s = place(SkinVariant.WII_SIDEWAYS); val u = place(SkinVariant.WII_UPRIGHT)
            assertEquals(SkinVariant.WII_UPRIGHT, u.spec.variant, name)
            assertEquals(s.pieces.map { it.dst }, u.pieces.map { it.dst }, "$name the same picture")
            val wire = mapOf("B" to "X", "A" to "Y", "Y" to "B", "X" to "A")   // sideways id -> upright id at the same place
            assertEquals(s.controls.map { (wire[it.spec.id] ?: it.spec.id) to (it.x to it.y) },
                u.controls.map { it.spec.id to (it.x to it.y) }, "$name the upright binds at the sideways places")
        }
    }

    @Test
    fun wiiNunchukLayoutsInEveryFamily() {
        // the stick and the Remote's d-pad (both, the stick on top, on the fold board and the tall landscape; else swapped by a
        // cap), the Remote's 1 2 B A diamond, SHAKE N Z | C SHAKE, − +, HOME, MOTION, MENU
        val roomy = setOf("inner", "inner_half", "tablet", "tablet_land", "inner_land")
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.WII_NUNCHUK), name)
            dump("wiin_$name", d.first, d.second, p)
            assertEquals(SkinVariant.WII_NUNCHUK, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val base = setOf("STICK", "DPAD", "A", "B", "START", "SELECT", "L2", "Y", "X", "R2", "L", "R", "MENU", "HOME", "MOTION")
            assertEquals(if (name in roomy) base else base + "STICK_SWAP", ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.STICK) pc.spec.r * 1.25f else pc.spec.r
            fun round(a: PlacedControl, b: PlacedControl) = kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
            fun circle(pc: PlacedControl) = pc.spec.shape == ControlShape.CIRCLE || pc.spec.shape == ControlShape.STICK
            val face = setOf("A", "B", "START", "SELECT")
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    assertTrue(if (circle(a) && circle(b)) round(a, b) else apart(box(a), box(b)), "$name ${a.spec.id} ${b.spec.id}")
                }
                for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
            }
            assertEquals(1, p.pieces.count { it.knob }, name)
            assertTrue(c("L2").x < c("Y").x && c("X").x < c("R2").x, "$name SHAKE N Z on the left, C SHAKE on the right")
            assertTrue(c("START").y < c("A").y && c("SELECT").x < c("A").x && c("B").y > c("A").y, "$name 1 top, 2 left, B bottom, A right")
            val small = p.spec.family == SkinFamily.STACKED || (p.spec.family == SkinFamily.FLEX && !p.spec.id.contains("fold"))
            if (small) assertEquals(listOf("−", "+"), listOf(c("L").spec.label, c("R").spec.label), name)
            else assertTrue(c("L").spec.label.isNullOrEmpty() && c("R").spec.label.isNullOrEmpty(), "$name − + marked caps carry no word")
            if (name in roomy) {   // the stick on top, the d-pad under it, both smaller
                assertTrue(c("STICK").y < c("DPAD").y, "$name stick above the d-pad")
                assertEquals(c("STICK").y, c("START").y, 1f, "$name 1 level with the stick (user)")
                assertTrue(c("STICK").spec.r < c("DPAD").spec.r, "$name the stick scaled down")
                val ring = 206f * p.k; val sring = ring * c("STICK").spec.r / (170f * p.k)
                val lower = (c("DPAD").y - ring) - (c("STICK").y + sring)
                assertEquals(28f * p.k, lower, 2f * p.k, "$name the d-pad close under the stick (user)")
            } else assertTrue(c("STICK").spec.r > c("DPAD").spec.r, "$name the swapped stick full size")
            if (name in setOf("inner", "inner_half", "tablet")) {   // the fold board: − + a column on 2's, MENU HOME MOTION one row
                assertEquals(c("SELECT").x, c("L").x, 1f, "$name − on 2's"); assertEquals(c("SELECT").x, c("R").x, 1f, "$name + on 2's")
                for (id in listOf("HOME", "MOTION")) assertEquals(c("MENU").y, c(id).y, 1f, "$name $id in MENU's row")
            }
        }
    }

    @Test
    fun wiiClassicLayoutsInEveryFamily() {
        // L ZL ZR R, the left stick and d-pad, the diamond, the right stick (the nub by X; its own stick under the face where
        // both show), − + (small caps: the signs; large caps: marked, the original controller's SELECT / START words), HOME with MENU
        val roomy = setOf("inner", "inner_half", "tablet", "tablet_land", "inner_land")   // the fold board and the tall landscape: both sticks
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.WII_CLASSIC), name)
            dump("wiic_$name", d.first, d.second, p)
            assertEquals(SkinVariant.WII_CLASSIC, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val base = setOf("STICK", "DPAD", "A", "B", "X", "Y", "L", "L2", "R2", "R", "CSTICK", "SELECT", "START", "HOME", "MENU")
            assertEquals(if (name in roomy) base else base + "STICK_SWAP", ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.STICK) pc.spec.r * 1.25f else pc.spec.r
            fun round(a: PlacedControl, b: PlacedControl) = kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
            fun circle(pc: PlacedControl) = pc.spec.shape == ControlShape.CIRCLE || pc.spec.shape == ControlShape.STICK
            val face = setOf("A", "B", "X", "Y")
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    assertTrue(if (circle(a) && circle(b)) round(a, b) else apart(box(a), box(b)), "$name ${a.spec.id} ${b.spec.id}")
                }
                for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
            }
            assertEquals(2, p.pieces.count { it.knob }, name)
            if (p.columns()) stacked(c("MENU"), c("HOME"), "$name MENU over HOME")
            else { assertEquals(c("MENU").y, c("HOME").y, 1f, "$name HOME beside MENU"); assertTrue(c("HOME").x > c("MENU").x, "$name HOME right of MENU") }
            val small = p.spec.family == SkinFamily.STACKED || (p.spec.family == SkinFamily.FLEX && !p.spec.id.contains("fold"))
            assertEquals(if (small) listOf("−", "+") else listOf("SELECT", "START"), listOf(c("SELECT").spec.label, c("START").spec.label), name)
            if (name in roomy) {   // the original controller's order: the d-pad and the face on top, both full sticks under them
                assertTrue(c("DPAD").y < c("STICK").y, "$name d-pad above the stick")
                assertEquals(c("DPAD").y, c("Y").y, 1f, "$name the face level with the d-pad")
                assertTrue(c("CSTICK").y > c("B").y, "$name right stick under the face")
                assertEquals(c("STICK").y, c("CSTICK").y, 1f, "$name the sticks level")
                assertEquals(c("STICK").spec.r, c("CSTICK").spec.r, 0.5f, "$name the right stick the left's size")
                val ring = 206f * p.k   // the d-pad's and the face's ring (outer), the sticks' at their scale
                val upper = (c("DPAD").y - ring) - (c("L").y + c("L").spec.hh)   // the placed sizes are the window's
                val lower = (c("STICK").y - ring * c("STICK").spec.r / (170f * p.k)) - (c("DPAD").y + ring)
                assertEquals(upper, lower, 2f * p.k, "$name L to the d-pad, the d-pad to the stick: one gap (user)")
            }
            // the stick smaller only where it shows with the d-pad; swapped with it, full size
            if (name in roomy) assertTrue(c("STICK").spec.r < c("DPAD").spec.r, "$name sticks scaled down")
            else assertTrue(c("STICK").spec.r > c("DPAD").spec.r, "$name the swapped stick full size")
        }
    }

    @Test
    fun gameCubeLayoutsInEveryFamily() {
        // the stick as Dreamcast's, the diamond, L R triggers (L2 R2) with Z (R) inside R or above it, the C-stick nub, START MENU;
        // both sticks on the tablet board and every tall landscape (the Fold's inner too), the C-stick then under the face
        val roomy = setOf("tablet", "tablet_land", "inner_land")
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.GC), name)
            dump("gc_$name", d.first, d.second, p)
            assertEquals(SkinVariant.GC, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val base = setOf("STICK", "DPAD", "A", "B", "X", "Y", "L2", "R2", "R", "CSTICK", "START", "MENU")
            assertEquals(if (name in roomy) base else base + "STICK_SWAP", ids, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.STICK) pc.spec.r * 1.25f else pc.spec.r
            fun round(a: PlacedControl, b: PlacedControl) = kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f
            fun circle(pc: PlacedControl) = pc.spec.shape == ControlShape.CIRCLE || pc.spec.shape == ControlShape.STICK
            val face = setOf("A", "B", "X", "Y")   // the diamond's caps share their edges (as on every layout)
            for (useStick in listOf(true, false)) {
                val cs = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    val a = cs[i]; val b = cs[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    assertTrue(if (circle(a) && circle(b)) round(a, b) else apart(box(a), box(b)), "$name ${a.spec.id} ${b.spec.id}")
                }
                for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
            }
            if (name in roomy) assertTrue(c("STICK").y < c("DPAD").y, "$name stick above the d-pad")
            assertTrue(c("R").x < c("R2").x || c("R").y < c("R2").y, "$name Z inside R or above it")
            assertEquals(2, p.pieces.count { it.knob }, name)
            if (name in roomy) assertTrue(c("CSTICK").y > c("B").y && kotlin.math.abs(c("CSTICK").y - c("DPAD").y) < 1f, "$name C-stick under the face, level with the d-pad")
        }
    }

    @Test
    fun dreamcastLayoutsInEveryFamily() {
        // both stick and d-pad on every fold / tablet board (the d-pad under the stick); a phone swaps them
        val roomy = (consoleDevices.map { it.first } + "tablet_land").filter { it.startsWith("inner") || it.startsWith("tablet") }.toSet()
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            val p = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, false, 640f to 480f,
                d.third, foldable = name.startsWith("inner"), variant = SkinVariant.DC), name)
            dump("dc_$name", d.first, d.second, p)
            assertEquals(SkinVariant.DC, p.spec.variant, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val base = setOf("STICK", "DPAD", "A", "B", "X", "Y", "L2", "R2", "START", "MENU")
            assertEquals(if (name in roomy) base else base + "STICK_SWAP", ids, name)
            assertEquals(1, p.pieces.count { it.knob }, name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            for (useStick in listOf(true, false)) {
                val shown = p.forStick(useStick)
                if (name !in roomy) assertEquals(1, shown.controls.count { it.spec.id == "STICK" || it.spec.id == "DPAD" }, "$name $useStick")
                val cs = shown.controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                val face = setOf("A", "B", "X", "Y")   // the diamond's caps share their edges (as on every layout)
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    if (cs[i].spec.id in face && cs[j].spec.id in face) continue
                    assertTrue(apart(box(cs[i]), box(cs[j])), "$name ${cs[i].spec.id} ${cs[j].spec.id}")
                }
                for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
            }
            if (name in roomy) assertTrue(c("STICK").y < c("DPAD").y, "$name stick above the d-pad")
            else {   // swapped: the stick where the d-pad is, its well and knob too (inward shift and all)
                assertEquals(c("DPAD").x, c("STICK").x, 0.5f, "$name stick x")
                assertEquals(c("DPAD").y, c("STICK").y, 0.5f, "$name stick y")
                fun mid(pc: SkinPiece) = (pc.dst.x + pc.dst.width / 2f) to (pc.dst.y + pc.dst.height / 2f)
                val (kx, ky) = mid(p.pieces.single { it.knob })
                assertEquals(c("DPAD").x, kx, 1f, "$name knob x"); assertEquals(c("DPAD").y, ky, 1f, "$name knob y")
                val dp = p.pieces.filter { it.alt == "dpad" }.maxBy { it.dst.width }
                val st = p.pieces.filter { it.alt == "stick" && !it.knob }.maxBy { it.dst.width }
                assertEquals(mid(dp).first, mid(st).first, 1f, "$name well x"); assertEquals(mid(dp).second, mid(st).second, 1f, "$name well y")
            }
            if (name == "tablet") {   // the face centred in the window's half under the fold (user), the stick level with it
                val mid = d.second * 3f / 4f
                assertTrue((c("X").y + c("B").y) / 2f <= mid + 1f, "$name face centred, or lifted to fit")
                assertEquals((c("X").y + c("B").y) / 2f, c("STICK").y, 1f, "$name the stick level with the face")
            }
            // the swap cap's word: what a tap gives (the d-pad while on the stick, and back)
            if (name !in roomy) {
                assertEquals("D-PAD", p.forStick(true).controls.single { it.spec.id == "STICK_SWAP" }.spec.label, name)
                assertEquals("STICK", p.forStick(false).controls.single { it.spec.id == "STICK_SWAP" }.spec.label, name)
            }
            // the stick nearly fills the d-pad's ring: its hole as big as the d-pad's reach, a small gap to the ring
            assertTrue(c("STICK").spec.r >= 160f * p.k, "$name stick ${c("STICK").spec.r}")
            if (name in setOf("inner", "inner_half", "tablet")) {
                val r = p.glasses.single { it.screen == GlassScreen.PANEL }.rect
                // the touch screen's strip labels sit on its frame's edge
                for (x in p.controls.filterNot { it.spec.id.startsWith("TOUCH_") }) assertTrue(apart(box(x), floatArrayOf(r.x, r.y, r.x + r.width, r.y + r.height)), "$name ${x.spec.id} on the panel")
            }
        }
    }

    // The DS / 3DS phone landscape on a window taller than the layout (a phone's): the rows of small caps under the
    // pads drop further into the extra height than the pads, so the rows spread apart and the room under MENU is used.
    @Test
    fun dsAnd3dsLandscapeRowsSpreadIntoTheExtraHeight() {
        for (v in listOf(SkinVariant.DS, SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) {
            val p = assertNotNull(SkinChoice.place(pack, 2400, 1080, 420, false, true, null, null, variant = v), "$v")
            val spec = pack.layouts.single { it.id == p.spec.id }
            fun placed(id: String) = p.controls.first { it.spec.id == id }.y
            fun ref(id: String) = spec.controls.first { it.id == id }.y
            val pad = "DPAD"
            val row1 = listOf("MIC", "MENU", "SELECT", "START", "SWAP").filter { id -> spec.controls.any { it.id == id } }
                .minBy { ref(it) }
            val row2 = listOf("MIC", "MENU", "SELECT", "START", "SWAP").filter { id -> spec.controls.any { it.id == id } }
                .maxBy { ref(it) }
            assertTrue(placed(row1) - placed(pad) > (ref(row1) - ref(pad)) * p.k + 20f, "$v ${p.spec.id}: pads to the first row")
            // (the 3DS's caps in one row each side, the height short of two; the DS's START / SELECT where the menu's are: user)
            if (v == SkinVariant.DS) {
                val menu = assertNotNull(SkinChoice.placeMenu(pack, 2400, 1080, 420)).controls.associateBy { it.spec.id }
                for (id in listOf("START", "SELECT")) assertEquals(menu.getValue(id).y, placed(id), 1f, "$v ${p.spec.id}: $id as the menu's")
            } else assertEquals(placed("MIC"), placed("MENU"), 1f, "$v ${p.spec.id}: one row")
            val last = p.controls.first { it.spec.id == row2 }
            assertTrue(last.y + last.spec.r + last.spec.labelDy + last.spec.labelH < 1080f, "$v ${p.spec.id}: its word on the window")
        }
    }

    // The New 3DS's landscape rows where the DS's and the regular 3DS's are: the nub needs no room down there.
    @Test
    fun new3dsLandscapeRowsSitWithTheDsOnes() {
        fun at(v: SkinVariant) = assertNotNull(SkinChoice.place(pack, 2400, 1080, 420, false, true, null, null, variant = v), "$v")
        val n = at(SkinVariant.N3DS); val r = at(SkinVariant.N3DS_REGULAR)
        for (id in listOf("MIC", "MENU", "SELECT", "START", "SWAP")) {
            assertEquals(r.controls.first { it.spec.id == id }.y, n.controls.first { it.spec.id == id }.y, 0.5f, id)
        }
    }

    // DS / 3DS landscape with the height for it (the Fold cover): each side one column, top to bottom the shoulders,
    // the pad, then its small caps one above the other on the screen's side of the pad, their words on the outer side.
    @Test
    fun dsAnd3dsLandscapeSmallCapsStackInAColumnWhereTheHeightAllows() {
        // the Fold cover, the unfolded Fold and a tablet in landscape
        val windows = listOf(Triple(1972, 1248, 420), Triple(2448, 1848, 420), Triple(2304, 1440, 280))
        for ((win, v) in windows.flatMap { w -> listOf(SkinVariant.DS, SkinVariant.N3DS, SkinVariant.N3DS_REGULAR).map { w to it } }) {
            val (ww, wh, dpi) = win
            val p = assertNotNull(SkinChoice.place(pack, ww, wh, dpi, dpi == 280, true, null, null, variant = v), "$v $win")
            fun c(id: String) = p.controls.first { it.spec.id == id }
            val left = listOfNotNull(p.controls.firstOrNull { it.spec.id == "STICK_SWAP" }, c("MIC"), c("MENU"))
            val right = listOf(c("SELECT"), c("START"), c("SWAP"))
            val pad = c("DPAD")
            val stick = p.controls.firstOrNull { it.spec.id == "STICK" }
            val padFoot = maxOf(pad.y + pad.spec.r, stick?.let { it.y + it.spec.r } ?: 0f)
            val face = listOf(c("Y"), c("A")).map { it.x }.average().toFloat()
            if (p.spec.id.endsWith("_col")) {
                // the DS and the 3DS on the cover: MIC, MENU (the 3DS's switch before them) in a row on the left and SWAP,
                // SELECT, START on the right, level (user); the pads where the main menu's are (user)
                val name = "$v $win ${p.spec.id}"
                if (v == SkinVariant.DS) {
                    for ((a, b) in listOf("MIC" to "MENU", "SWAP" to "SELECT", "SELECT" to "START")) {
                        assertEquals(c(a).y, c(b).y, 1f, "$name $a $b a row"); assertTrue(c(b).x > c(a).x, "$name $a before $b")
                    }
                    assertEquals(c("MIC").y, c("SELECT").y, 1f, "$name rows level")
                    assertTrue(c("SWAP").y + c("SWAP").spec.r + c("SWAP").spec.labelDy + c("SWAP").spec.labelH < wh, "$name words on the window")
                }
                if (v != SkinVariant.DS) {
                    for (row in listOf(listOf("STICK_SWAP", "MIC", "MENU"), listOf("SWAP", "SELECT", "START"))) for ((a, b) in row.zipWithNext()) {
                        assertEquals(c(a).y, c(b).y, 1f, "$name $a $b a row"); assertTrue(c(b).x > c(a).x, "$name $a before $b")
                        assertTrue(c(b).y + c(b).spec.r + c(b).spec.labelDy + c(b).spec.labelH < wh, "$name $b's word on the window")
                    }
                    assertEquals(c("MIC").y, c("SELECT").y, 1f, "$name rows level")
                }
                if (v == SkinVariant.DS) assertTrue(c("SWAP").x > ww / 2f, "$name SWAP on the right with SELECT / START")
                val menu = assertNotNull(SkinChoice.placeMenu(pack, ww, wh, dpi, foldable = true)).controls.associateBy { it.spec.id }
                val stick = p.controls.firstOrNull { it.spec.id == "STICK" } ?: c("DPAD")
                assertEquals(menu.getValue("DPAD").x, stick.x, 1f, "$name stick x as the menu's d-pad"); assertEquals(menu.getValue("DPAD").y, stick.y, 1f, "$name stick y")
                assertEquals(menu.getValue("X").x, c("X").x, 1f, "$name X x as the menu's"); assertEquals(menu.getValue("X").y, c("X").y, 1f, "$name X y")
                continue
            }
            if (v != SkinVariant.DS && p.spec.id.contains("_tall")) {
                // the 3DS's tall shell: a row each side under its lower pad (the right's the New 3DS's C-stick), level (user)
                val name = "$v $win ${p.spec.id}"
                val rows = listOf(listOf(c("MIC"), c("MENU")), listOf(c("SWAP"), c("SELECT"), c("START")))
                for (r in rows) for ((a, b) in r.zipWithNext()) { assertEquals(a.y, b.y, 1f, "$name ${b.spec.id} in the row"); assertTrue(b.x > a.x, "$name ${b.spec.id} in order") }
                assertEquals(c("MIC").y, c("SWAP").y, 1f, "$name rows level")
                val rightFoot = (p.controls.firstOrNull { it.spec.id == "CSTICK" }?.let { it.y + it.spec.r } ?: 0f).coerceAtLeast(c("B").y + c("B").spec.r)
                assertTrue(c("MIC").y - c("MIC").spec.r > padFoot && c("SWAP").y - c("SWAP").spec.r > rightFoot, "$name rows under the pads")
                assertTrue(c("MIC").y + c("MIC").spec.r + c("MIC").spec.labelDy + c("MIC").spec.labelH < wh, "$name words on the window")
                continue
            }
            for ((side, caps) in listOf("left" to left, "right" to right)) {
                val id = "$v $win ${p.spec.id} $side"
                assertTrue(caps.all { kotlin.math.abs(it.x - caps[0].x) < 1f }, "$id: one column")
                assertTrue(caps.zipWithNext().all { (a, b) -> b.y - a.y >= a.spec.r + b.spec.r }, "$id: top to bottom, apart")
                // under its side's own cluster (the right's: the face's, the left's: the d-pad's or the stick's)
                val foot = if (side == "left") padFoot else c("B").let { it.y + it.spec.r }
                assertTrue(caps[0].y - caps[0].spec.r > foot, "$id: under the pad")
                assertTrue(caps.last().y + caps.last().spec.r < wh - 20f, "$id: on the window")
                for (k in caps) assertTrue(k.spec.labelH > 0f && (if (side == "left") k.spec.labelDx < 0f else k.spec.labelDx > 0f), "$id ${k.spec.id}: its word outside")
                // the top two in an upright pair well (two grouped; of three, the top two)
                val mx = caps[0].x; val my = (caps[0].y + caps[1].y) / 2f
                assertTrue(p.pieces.any { w -> w.dst.height > w.dst.width * 1.5f && kotlin.math.abs(w.dst.x + w.dst.width / 2f - mx) < 2f &&
                    kotlin.math.abs(w.dst.y + w.dst.height / 2f - my) < 2f }, "$id: the top two in a well")
            }
            // the columns level at the foot: MENU with SWAP, MIC with START (the left's fewer caps start lower)
            for (i in 1..left.size) assertEquals(right[right.size - i].y, left[left.size - i].y, 1f, "$v $win ${left[left.size - i].spec.id} level with ${right[right.size - i].spec.id}")
            assertTrue(left[0].x > pad.x, "$v: the left column on the screen's side of the pad")
            assertTrue(right[0].x < face, "$v: the right column on the screen's side of the face")
            val b = c("B")
            assertTrue(right[0].y - right[0].spec.r - (b.y + b.spec.r) >= 20f * p.k, "$v $win: the column clear of B (user)")
            if (ww != 1972) continue
            // a phone's lower landscape keeps the rows
            val phone = assertNotNull(SkinChoice.place(pack, 2520, 1080, 420, false, true, null, null, variant = v), "$v phone")
            assertEquals(phone.controls.first { it.spec.id == "SELECT" }.y, phone.controls.first { it.spec.id == "START" }.y, 0.5f, "$v phone: SELECT START in a row")
        }
    }

    // The New 3DS's tall landscape (the unfolded Fold, a tablet): the regular 3DS's places, its C-stick under the face.
    @Test
    fun new3dsTallLandscapeTakesTheRegularPlacesWithTheCStickUnderTheFace() {
        for ((w, h, dpi) in listOf(Triple(2448, 1848, 420), Triple(2304, 1440, 280))) {
            fun at(v: SkinVariant) = assertNotNull(SkinChoice.place(pack, w, h, dpi, dpi == 280, true, null, null, foldable = dpi == 420, variant = v), "$v $w")
            val np = at(SkinVariant.N3DS)
            val n = np.controls.associateBy { it.spec.id }; val r = at(SkinVariant.N3DS_REGULAR).controls.associateBy { it.spec.id }
            // the screen centred between the side groups (the rows under their rings, not widening a side: user)
            val g = np.glasses.first().rect
            assertEquals(w / 2f, g.x + g.width / 2f, 12f, "$w screen centred: $g")
            // the regular 3DS's buttons; ZL / ZR beside L / R; a full C-stick under the face, in line, as the d-pad under the stick (user)
            for (id in listOf("L", "R", "X", "Y", "A", "B", "STICK", "DPAD")) {
                assertEquals(r.getValue(id).x, n.getValue(id).x, 1f, "$w $id x"); assertEquals(r.getValue(id).y, n.getValue(id).y, 1f, "$w $id y")
            }
            assertEquals(n.getValue("L").y, n.getValue("L2").y, 1f, "$w ZL level with L"); assertEquals(n.getValue("R").y, n.getValue("R2").y, 1f, "$w ZR level with R")
            val nub = n.getValue("CSTICK")
            assertEquals(n.getValue("X").x, nub.x, 1f, "$w C-stick in line with the face")
            // (two large controls in a box: the lower one, the C-stick under the face, at 85% (user))
            assertEquals(n.getValue("STICK").spec.r * 0.85f, nub.spec.r, 0.5f, "$w C-stick at 85%")
            assertEquals(n.getValue("DPAD").y - n.getValue("STICK").y, nub.y - (n.getValue("X").y + n.getValue("B").y) / 2f, 0.15f * (n.getValue("DPAD").y - n.getValue("STICK").y), "$w as far under the face as the d-pad under the stick")
        }
    }

    // Every stick's knob sits in its ring, the small ones too.
    @Test
    fun everyStickKnobHasItsRing() {
        for (l in pack.layouts) for (k in l.pieces.filter { it.knob }) {
            val cx = k.x + k.w * k.scale / 2f; val cy = k.y + k.h * k.scale / 2f
            assertTrue(l.pieces.any { r -> !r.knob && r.w * r.scale > k.w * k.scale && kotlin.math.abs(r.x + r.w * r.scale / 2f - cx) < 3f &&
                kotlin.math.abs(r.y + r.h * r.scale / 2f - cy) < 3f }, "${l.id} ${k.knobOf}: its ring")
        }
    }

    // The N64 on the unfolded Fold and a tablet in landscape: the stick at its full size (the short shell's) where the
    // main menu's d-pad is, the d-pad under it, both shown (no switch); the C diamond at the menu's face buttons' size
    // where they are, B / A, MENU and START under it; the screen frame the menu's.
    @Test
    fun n64TallLandscapeHasTheMenusPadsAtFullSize() {
        for ((w, h, dpi) in listOf(Triple(2448, 1848, 420), Triple(2504, 2256, 480), Triple(2304, 1440, 280))) {
            val tablet = dpi == 280
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet, false, 320f to 240f, null, foldable = !tablet, variant = SkinVariant.N64), "$w")
            val m = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = !tablet, tablet = tablet), "$w menu")
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun mc(id: String) = m.controls.first { it.spec.id == id }
            assertTrue(p.controls.none { it.spec.id == "STICK_SWAP" }, "$w no switch")
            assertTrue(p.pieces.none { it.alt != null } && p.controls.none { it.spec.alt != null }, "$w both shown")
            val st = c("STICK"); val dp = c("DPAD")
            assertEquals(mc("DPAD").x, st.x, 1f, "$w the stick on the menu's d-pad"); assertEquals(mc("DPAD").y, st.y, 1f, "$w the stick on the menu's d-pad")
            val full = pack.layouts.first { it.variant == SkinVariant.N64 && it.family == SkinFamily.GRIP && it.minRefH == 0 }.controls.first { it.id == "STICK" }.r
            assertEquals(full * p.k, st.spec.r, 1f, "$w the full stick")
            assertEquals(st.x, dp.x, 1f, "$w the d-pad under the stick")
            assertTrue(dp.y - dp.spec.r - (st.y + st.spec.r * 208f / 170f) >= 4f * dpi / 160f - 1f, "$w a gap between them")
            for ((mine, its) in listOf("CU" to "X", "CL" to "Y", "CR" to "A", "CD" to "B")) {
                assertEquals(mc(its).x, c(mine).x, 1f, "$w $mine on $its"); assertEquals(mc(its).y, c(mine).y, 1f, "$w $mine on $its")
                assertEquals(mc(its).spec.r, c(mine).spec.r, 1f, "$w $mine at $its's size")
            }
            assertEquals(mc("R").x, c("R").x, 1f, "$w R on the menu's"); assertEquals(mc("R").y, c("R").y, 1f, "$w R on the menu's")
            assertEquals(mc("L").x - mc("L").spec.hw, c("L2").x - c("L2").spec.hw, 1f, "$w Z starts where the menu's L does")
            assertEquals(mc("L").y, c("L").y, 1f, "$w L level with the menu's"); assertEquals(c("L").y, c("L2").y, 1f, "$w Z level with L")
            // (the Fold and the Ultra sideways: MENU on the left, START on the right, level, at the bottom (user); a tablet:
            // both under B / A)
            if (!tablet) {
                assertTrue(c("B").y > c("CD").y && c("MENU").x < w / 2f && c("START").x > w / 2f, "$w B under the diamond, MENU left, START right")
                assertEquals(c("MENU").y, c("START").y, 1f, "$w MENU level with START")
                assertTrue(c("MENU").y > c("DPAD").y && c("MENU").spec.labelDx < 0f && c("START").spec.labelDx > 0f, "$w under the pads, the words beside them")
            } else assertTrue(c("B").y > c("CD").y && c("MENU").y > c("A").y && c("START").y > c("MENU").y, "$w B / A, MENU, START under it")
            assertTrue(c("START").y + c("START").spec.r < h, "$w on the window")
            // the frame's slices (the pieces drawn from no part): as tall as the menu's
            fun span(q: SkinPlacement) = q.pieces.filter { it.piece < 0 && !it.strip }.let { f ->
                listOf(f.minOf { it.dst.x }, f.minOf { it.dst.y }, f.maxOf { it.dst.x + it.dst.width }, f.maxOf { it.dst.y + it.dst.height }) }
            span(p).zip(span(m)).forEachIndexed { i, (a, b) -> assertEquals(b, a, 1f, "$w the menu's frame, edge $i") }
            val g = assertNotNull(p.glass(GlassScreen.PICTURE)).rect; val mg = assertNotNull(m.glasses.firstOrNull()).rect
            assertEquals(mg.x, g.x, 1f, "$w the menu's glass left"); assertEquals(mg.width, g.width, 1f, "$w the menu's glass width")
        }
    }

    // The N64's screens are the other consoles' (the main menu's frame) on the covers, the Flip and a phone, sideways
    // and upright (but the Fold cover sideways, below): its side boxes as wide, its controls as tall;
    // sideways its L / R at the others' height, its stick on their d-pad, MENU under the stick.
    @Test
    fun n64ScreensAreTheOtherConsolesOnEveryShape() {
        data class Win(val w: Int, val h: Int, val dpi: Int, val fold: Boolean, val hinge: Float? = null)
        for (win in listOf(Win(1248, 1972, 420, true), Win(2424, 1080, 480, true), Win(1080, 2424, 480, true),
            Win(2520, 1080, 420, true), Win(1080, 2520, 420, true), Win(2400, 1080, 420, false), Win(1080, 2400, 420, false))) {
            fun placed(v: SkinVariant) = assertNotNull(SkinChoice.place(pack, win.w, win.h, win.dpi, false, false, 320f to 240f, win.hinge,
                foldable = win.fold, variant = v), "$win $v")
            val pn = placed(SkinVariant.N64); val pg = placed(SkinVariant.GBA)
            val n64 = pn.glasses.associate { it.screen to it.rect }; val gba = pg.glasses.associate { it.screen to it.rect }
            if (win.w > win.h && win.hinge == null) {
                for (id in listOf("L", "R"))
                    assertEquals(pg.controls.first { it.spec.id == id }.y, pn.controls.first { it.spec.id == id }.y, 1f, "$win $id level with the others'")
                // the stick on the others' d-pad (the main menu's)
                val st = pn.controls.first { it.spec.id == "STICK" }; val dp = pg.controls.first { it.spec.id == "DPAD" }
                assertEquals(dp.x, st.x, 1f, "$win the stick on the d-pad"); assertEquals(dp.y, st.y, 1f, "$win the stick on the d-pad")
                assertEquals(st.x, pn.controls.first { it.spec.id == "MENU" }.x, 1f, "$win MENU under the stick's middle (user)")
            }
            assertEquals(gba.keys, n64.keys, "$win the same screens")
            for ((k, g) in gba) {
                val r = n64.getValue(k)
                assertEquals(g.x, r.x, 1f, "$win $k left"); assertEquals(g.width, r.width, 1f, "$win $k width")
                assertEquals(g.y, r.y, 1f, "$win $k top"); assertEquals(g.height, r.height, 1f, "$win $k height")
            }
        }
    }

    // The N64 upright on the Ultra cover, the Flip and a phone: the stick on the main menu's d-pad, the C diamond on its
    // face buttons at their size, MENU under the stick, B / A side by side under the C diamond level with that row (user).
    @Test
    fun n64NarrowUprightHasItsPadsOnTheMenus() {
        for ((w, h, dpi) in listOf(Triple(1080, 2424, 480), Triple(1080, 2520, 420), Triple(1080, 2400, 420))) {
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, false, false, 320f to 240f, null, foldable = h != 2400, variant = SkinVariant.N64), "$h")
            val m = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = h != 2400, tablet = false), "$h menu")
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun mc(id: String) = m.controls.first { it.spec.id == id }
            assertEquals(mc("DPAD").x, c("STICK").x, 1f, "$h stick x"); assertEquals(mc("DPAD").y, c("STICK").y, 1f, "$h stick y")
            for ((mine, its) in listOf("CU" to "X", "CL" to "Y", "CR" to "A", "CD" to "B")) {
                assertEquals(mc(its).x, c(mine).x, 2f, "$h $mine on $its"); assertEquals(mc(its).y, c(mine).y, 2f, "$h $mine on $its")
                assertEquals(mc(its).spec.r, c(mine).spec.r, 1f, "$h $mine at $its's size")
            }
            assertEquals(c("STICK").x, c("MENU").x, 1f, "$h MENU under the stick")
            assertEquals(c("MENU").y, c("B").y, 1f, "$h B level with the row"); assertEquals(c("B").y, c("A").y, 1f, "$h B level with A")
            assertEquals(c("CU").x, (c("A").x + c("B").x) / 2f, 2f, "$h B / A under the C diamond")
            assertTrue(c("A").x + c("A").spec.r < w, "$h A on the window")
        }
    }

    // The DS on a phone half-folded (the Flip upright): the pads and MENU's row where the flat phone has them, the touch
    // screen up under the fold, the short L / R beside it and SELECT / START under it, all clear of one another (user).
    @Test
    fun dsOnAHalfFoldedPhoneKeepsTheFlatPhonesPads() {
        val (w, h, dpi, hinge) = listOf(1080, 2520, 420, 1260)
        val half = assertNotNull(SkinChoice.place(pack, w, h, dpi, false, true, null, hinge.toFloat(), foldable = true, variant = SkinVariant.DS))
        val flat = assertNotNull(SkinChoice.place(pack, w, h, dpi, false, true, null, null, foldable = true, variant = SkinVariant.DS))
        val m = half.controls.associateBy { it.spec.id }
        val f = flat.controls.associateBy { it.spec.id }
        for (id in listOf("DPAD", "X", "Y", "A", "B", "MIC", "SWAP", "MENU")) {
            assertEquals(f.getValue(id).x, m.getValue(id).x, 1f, "$id x"); assertEquals(f.getValue(id).y, m.getValue(id).y, 1f, "$id y")
        }
        val touch = half.glasses.first { it.id == "touch" }.rect
        // its frame: the smallest piece round it
        val frame = half.pieces.filter { p -> p.dst.x <= touch.x && p.dst.y <= touch.y && p.dst.x + p.dst.width >= touch.x + touch.width && p.dst.y + p.dst.height >= touch.y + touch.height }
            .minBy { it.dst.width * it.dst.height }.dst
        assertTrue(frame.y >= hinge, "the touch screen under the fold")
        for (id in listOf("L", "R")) {
            val c = m.getValue(id)
            assertEquals(f.getValue(id).y, c.y, 1f, "$id level with the flat phone's")
            assertTrue(c.x + c.spec.hw <= frame.x || c.x - c.spec.hw >= frame.x + frame.width, "$id beside the touch screen")
        }
        for (id in listOf("SELECT", "START")) assertTrue(m.getValue(id).y - m.getValue(id).spec.r >= frame.y + frame.height, "$id under the touch screen")
    }

    // The DS on the Fold cover upright: the touch screen's narrow frame (no black bars beside the screen) and the regular
    // L / R beside it, clear of it (user).
    @Test
    fun dsOnTheFoldCoverUprightHasTheRegularShouldersBesideANarrowTouchFrame() {
        val p = assertNotNull(SkinChoice.place(pack, 1248, 1972, 420, false, true, null, null, foldable = true, variant = SkinVariant.DS))
        assertEquals("large_ds", p.spec.id)
        val touch = p.glasses.first { it.id == "touch" }.rect
        val frame = p.pieces.filter { q -> q.dst.x <= touch.x && q.dst.y <= touch.y && q.dst.x + q.dst.width >= touch.x + touch.width && q.dst.y + q.dst.height >= touch.y + touch.height }
            .minBy { it.dst.width * it.dst.height }.dst
        assertTrue(touch.x - frame.x <= 24f && frame.x + frame.width - touch.x - touch.width <= 24f, "a thin border beside the screen")
        val c = p.controls.associateBy { it.spec.id }
        assertEquals(100f, c.getValue("L").spec.hw, 0.5f); assertEquals(100f, c.getValue("R").spec.hw, 0.5f)
        assertTrue(c.getValue("L").x + c.getValue("L").spec.hw < frame.x, "L beside the touch screen")
        assertTrue(c.getValue("R").x - c.getValue("R").spec.hw > frame.x + frame.width, "R beside the touch screen")
    }

    // A phone half-folded (the Flip upright): the main menu's screen above the fold and its pad below, where a game's
    // half-folded board has them, so opening a game moves nothing (user).
    @Test
    fun menuOnAHalfFoldedPhoneIsAboveTheFoldAsAGamesIs() {
        val (w, h, dpi, hinge) = listOf(1080, 2520, 420, 1260)
        val menu = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = true, hingeY = hinge.toFloat()))
        val game = assertNotNull(SkinChoice.place(pack, w, h, dpi, false, false, 240f to 160f, hinge.toFloat(), foldable = true, variant = SkinVariant.GBA))
        assertEquals("flex_menu", menu.spec.id)
        val mg = menu.glasses.first().rect
        val gg = game.glasses.first().rect
        assertTrue(mg.y + mg.height < hinge, "the menu's screen above the fold")
        assertEquals(gg.x, mg.x, 1f, "screen left"); assertEquals(gg.width, mg.width, 1f, "screen width"); assertEquals(gg.y, mg.y, 1f, "screen top")
        val m = menu.controls.associateBy { it.spec.id }
        val g = game.controls.associateBy { it.spec.id }
        for (id in listOf("DPAD", "X", "Y", "A", "B", "SELECT", "START", "MENU")) {
            assertEquals(g.getValue(id).x, m.getValue(id).x, 1f, "$id x"); assertEquals(g.getValue(id).y, m.getValue(id).y, 1f, "$id y")
            assertEquals(g.getValue(id).spec.r, m.getValue(id).spec.r, 1f, "$id size")
        }
        assertTrue(m.getValue("L").y > hinge && m.getValue("R").y > hinge, "the shoulders below the fold")
    }

    // The N64 on the Ultra's inner screen sideways half-folded, as upright half-folded: the stick on the main menu's d-pad,
    // the C diamond on its face buttons at their size, R level with L, the d-pad under the stick and no switch; MENU +
    // START a pair under the lower screen's frame, centred under it (user).
    @Test
    fun n64UltraSidewaysHalfFoldedIsAsUpright() {
        val (w, h, dpi, hinge) = listOf(2504, 2256, 480, 1128)
        val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, false, false, 320f to 240f, hinge.toFloat(), foldable = true, variant = SkinVariant.N64))
        val m = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = true, tablet = true, hingeY = hinge.toFloat()))
        fun c(id: String) = p.controls.first { it.spec.id == id }
        fun mc(id: String) = m.controls.first { it.spec.id == id }
        assertEquals(mc("DPAD").x, c("STICK").x, 1f, "stick x"); assertEquals(mc("DPAD").y, c("STICK").y, 1f, "stick y")
        for ((mine, its) in listOf("CU" to "X", "CL" to "Y", "CR" to "A", "CD" to "B")) {
            assertEquals(mc(its).x, c(mine).x, 2f, "$mine on $its"); assertEquals(mc(its).y, c(mine).y, 2f, "$mine on $its")
            assertEquals(mc(its).spec.r, c(mine).spec.r, 1f, "$mine at $its's size")
        }
        assertEquals(c("L").y, c("R").y, 1f, "R level with L")
        assertTrue(p.controls.none { it.spec.id == "STICK_SWAP" }, "no switch")
        assertTrue(c("DPAD").y - c("DPAD").spec.r > c("STICK").y + c("STICK").spec.r && c("DPAD").y + c("DPAD").spec.r < h, "the d-pad under the stick")
        val panel = assertNotNull(p.glass(GlassScreen.PANEL)).rect
        val menu = c("MENU"); val start = c("START")
        assertEquals(menu.y, start.y, 1f, "a pair"); assertTrue(menu.y - menu.spec.r > panel.y + panel.height, "under the lower screen")
        assertEquals(panel.x + panel.width / 2f, (menu.x + start.x) / 2f, 2f, "under its middle")
    }

    // The N64 on the Fold cover sideways: its screen as wide as the others' (a 4:3 picture held by that width), MENU and
    // START a pair in a bottom row under it, the d-pad under the stick on the left; the stick always out, no stick /
    // d-pad switch (user).
    @Test
    fun n64FoldCoverSidewaysHasTheDpadUnderTheStickAndMenuStartUnderTheScreen() {
        val p = assertNotNull(SkinChoice.place(pack, 1972, 1248, 420, false, false, 320f to 240f, null, foldable = true, variant = SkinVariant.N64))
        val g = assertNotNull(SkinChoice.place(pack, 1972, 1248, 420, false, false, 320f to 240f, null, foldable = true, variant = SkinVariant.GBA))
        val r = assertNotNull(p.glass(GlassScreen.PICTURE)).rect; val gr = assertNotNull(g.glass(GlassScreen.PICTURE)).rect
        assertEquals(gr.x, r.x, 1f, "as far left"); assertEquals(gr.width, r.width, 1f, "as wide")
        assertTrue(r.height >= r.width * 3f / 4f, "a 4:3 picture held by the width: ${r.height}")
        fun c(id: String) = p.controls.first { it.spec.id == id }
        val dpad = c("DPAD"); val stick = c("STICK"); val menu = c("MENU"); val start = c("START")
        assertTrue(p.controls.none { it.spec.id == "STICK_SWAP" }, "no switch")
        assertTrue(p.controls.filter { it.spec.id == "STICK" || it.spec.id == "DPAD" }.all { it.spec.alt == null }, "both always out")
        assertTrue(dpad.y - dpad.spec.r > stick.y + stick.spec.r && dpad.x < r.x, "the d-pad under the stick, left of the screen")
        assertEquals(stick.y, c("CL").y, 1f, "the stick level with the C diamond")
        // the stick on the main menu's d-pad, the C diamond on its face buttons
        val m = assertNotNull(SkinChoice.placeMenu(pack, 1972, 1248, 420, foldable = true, tablet = false))
        fun mc(id: String) = m.controls.first { it.spec.id == id }
        assertEquals(mc("DPAD").x, stick.x, 1f, "the stick on the menu's d-pad"); assertEquals(mc("DPAD").y, stick.y, 1f, "the stick on the menu's d-pad")
        for ((mine, its) in listOf("CU" to "X", "CL" to "Y", "CR" to "A", "CD" to "B")) {
            assertEquals(mc(its).x, c(mine).x, 2f, "$mine on $its"); assertEquals(mc(its).y, c(mine).y, 2f, "$mine on $its")
            assertEquals(mc(its).spec.r, c(mine).spec.r, 1f, "$mine at $its's size")
        }
        assertTrue(dpad.y + dpad.spec.r < 1248f, "the d-pad on the window")
        assertEquals(menu.y, start.y, 1f, "a pair"); assertTrue(menu.x < start.x, "MENU then START")
        assertEquals(r.x + r.width / 2f, (menu.x + start.x) / 2f, 2f, "under the screen's middle")
        assertTrue(menu.y - menu.spec.r > r.y + r.height, "under the screen")
        assertTrue(menu.spec.labelDy > 0f && start.spec.labelDy > 0f, "words under")
        // (the well: its two ends, taller than the caps, round MENU and START at their height)
        val ends = p.pieces.filter { w -> w.dst.height > 2f * menu.spec.r && kotlin.math.abs(w.dst.y + w.dst.height / 2f - menu.y) < 2f }
        assertTrue(ends.any { it.dst.x < menu.x - menu.spec.r } && ends.any { it.dst.x + it.dst.width > start.x + start.spec.r }, "MENU and START in a well")
    }

    // The N64 upright on an inner screen: its touch screen's frame the main menu's, cut short over MENU and START, the
    // d-pad under the stick; the C diamond and the shoulders as the menu's.
    @Test
    fun n64UprightFoldBoardHasTheMenusFrameAndItsMenuAndStart() {
        for ((w, h, dpi) in listOf(Triple(1848, 2448, 420), Triple(2256, 2504, 480))) {
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, false, false, 320f to 240f, null, foldable = true, variant = SkinVariant.N64), "$w")
            val m = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = true, tablet = true), "$w menu")
            fun lower(q: SkinPlacement) = q.pieces.filter { it.piece >= 0 && !it.strip && q.spec.pieces[it.piece].glass != null && it.dst.y + it.dst.height / 2f > h / 2f }
            val f = lower(p); val mf = lower(m)
            assertEquals(mf.minOf { it.dst.x }, f.minOf { it.dst.x }, 1f, "$w frame left"); assertEquals(mf.maxOf { it.dst.x + it.dst.width }, f.maxOf { it.dst.x + it.dst.width }, 1f, "$w frame right")
            assertEquals(mf.minOf { it.dst.y }, f.minOf { it.dst.y }, 1f, "$w frame top")
            fun c(id: String) = p.controls.first { it.spec.id == id }
            val menu = c("MENU"); val start = c("START")
            assertEquals(menu.y, start.y, 1f, "$w a pair"); assertTrue(menu.x < start.x, "$w MENU then START")
            // as the Fold cover sideways: the d-pad under the stick and no switch; MENU + START under the lower screen's
            // frame (which ends over them), centred under it (user)
            val stick = c("STICK"); val dpad = c("DPAD")
            assertTrue(p.controls.none { it.spec.id == "STICK_SWAP" }, "$w no switch")
            assertTrue(dpad.y - dpad.spec.r > stick.y + stick.spec.r, "$w the d-pad under the stick")
            assertTrue(dpad.x + dpad.spec.r < f.minOf { it.dst.x }, "$w the d-pad clear of the frame")
            assertTrue(menu.y - menu.spec.r > f.maxOf { it.dst.y + it.dst.height }, "$w MENU and START under the frame")
            assertEquals((f.minOf { it.dst.x } + f.maxOf { it.dst.x + it.dst.width }) / 2f, (menu.x + start.x) / 2f, 2f, "$w under its middle")
            assertTrue(start.y + start.spec.r + start.spec.labelDy < h, "$w on the window")
            // the C diamond as the menu's face buttons, B / A lower under it
            fun mc(id: String) = m.controls.first { it.spec.id == id }
            for ((mine, its) in listOf("CU" to "X", "CL" to "Y", "CR" to "A", "CD" to "B")) {
                assertEquals(mc(its).x, c(mine).x, 1f, "$w $mine on $its"); assertEquals(mc(its).y, c(mine).y, 1f, "$w $mine on $its")
                assertEquals(mc(its).spec.r, c(mine).spec.r, 1f, "$w $mine at $its's size")
            }
            assertTrue(c("B").y - c("B").spec.r > c("CD").y + c("CD").spec.r, "$w B under the diamond")
            assertTrue(c("A").y + c("A").spec.r < h, "$w A on the window")
            assertEquals(mc("DPAD").x, c("STICK").x, 1f, "$w the stick on the menu's d-pad"); assertEquals(mc("DPAD").y, c("STICK").y, 1f, "$w the stick on the menu's d-pad")
            assertEquals(mc("R").x, c("R").x, 1f, "$w R on the menu's"); assertEquals(mc("R").y, c("R").y, 1f, "$w R on the menu's")
            assertEquals(mc("L").x - mc("L").spec.hw, c("L2").x - c("L2").spec.hw, 1f, "$w Z starts where the menu's L does"); assertEquals(mc("L").y, c("L").y, 1f, "$w L level with the menu's")
        }
    }

    // A column's well holds SELECT and START where it has both (the PlayStation's R3 over them is not their pair).
    @Test
    fun aColumnsWellHoldsSelectAndStart() {
        val p = assertNotNull(SkinChoice.place(pack, 2448, 1848, 420, false, false, 320f to 240f, null, foldable = true, variant = SkinVariant.PS1))
        fun c(id: String) = p.controls.first { it.spec.id == id }
        val sel = c("SELECT"); val st = c("START")
        assertEquals(sel.x, st.x, 1f); assertTrue(c("R3").y < sel.y, "R3 over them")
        val my = (sel.y + st.y) / 2f
        assertTrue(p.pieces.any { w -> w.dst.height > w.dst.width * 1.5f && kotlin.math.abs(w.dst.x + w.dst.width / 2f - sel.x) < 2f &&
            kotlin.math.abs(w.dst.y + w.dst.height / 2f - my) < 2f }, "SELECT and START in a well")
    }

    // The GameCube's tall landscape (the unfolded Fold, a tablet): MENU over on the right with START, the pair in a
    // well, clear of the d-pad it overlapped on the left.
    @Test
    fun gameCubeTallLandscapeHasMenuWithStartOnTheRight() {
        for ((w, h, dpi) in listOf(Triple(2448, 1848, 420), Triple(2304, 1440, 280))) {
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, dpi == 280, false, 640f to 480f, null, foldable = dpi == 420, variant = SkinVariant.GC), "$w")
            fun c(id: String) = p.controls.first { it.spec.id == id }
            val menu = c("MENU"); val start = c("START")
            assertTrue(menu.x > w / 2f, "$w MENU right")
            stacked(menu, start, "$w MENU over START")
            val my = (menu.y + start.y) / 2f
            assertTrue(p.pieces.any { q -> q.dst.height > q.dst.width * 1.5f && kotlin.math.abs(q.dst.x + q.dst.width / 2f - menu.x) < 2f &&
                kotlin.math.abs(q.dst.y + q.dst.height / 2f - my) < 2f }, "$w the pair in a well")
        }
    }

    // The phone frame's touch shortcut band no taller than its words need: the
    // frame's bottom border at most 87 px, its words centred in the band's black over the rim; the slots'
    // touch areas reach down into the rim (their words drawn wordUp over their middle), clear of the screen.
    @Test
    fun thePhoneFramesShortcutBandIsThin() {
        for (l in pack.layouts) {
            val f = l.frame ?: continue
            val st = f.strip ?: continue
            if (l.id != "grip_gba" && l.id != "stacked_single") continue
            val bottom = f.h - f.glass.y - f.glass.h
            assertTrue(bottom <= 87, "${l.id} bottom border $bottom")
            val rim = f.glass.y   // the frame's own rim and groove, as at its top
            assertEquals((bottom - rim) / 2f + rim, (st.y + st.wordUp).toFloat(), 2f, "${l.id} words centred in the band")
            assertTrue(st.hh >= 30, "${l.id} touch slots still easy to press")
            assertTrue(bottom - st.y - st.hh >= SLOT_SCREEN_GAP, "${l.id} slots clear of the screen")
        }
        val p = assertNotNull(SkinChoice.place(pack, 2520, 1080, 480, false, false, 240f to 160f, null, variant = SkinVariant.GBA))
        val slot = p.controls.first { it.spec.id == "TOUCH_1" }
        assertEquals(-assertNotNull(p.spec.frame?.strip).wordUp * p.k, slot.spec.labelDy, 0.5f, "the slot's words over its middle")
    }

    // The N64 on a phone in portrait (the Flip): the switch and MENU on the left under the stick, so B / A drop
    // further from the stick, and sit further right.
    @Test
    fun n64PhonePortraitHasTheSwitchMenuAndStartInARowAndBaSideBySide() {
        val p = assertNotNull(SkinChoice.place(pack, 1080, 2520, 480, false, false, 320f to 240f, null, variant = SkinVariant.N64))
        assertEquals("stacked_n64", p.spec.id)
        fun c(id: String) = p.controls.first { it.spec.id == id }
        // the switch, MENU, START in a row of three centred under the stick; B at the window's middle (user)
        val st = c("STICK"); val sw = c("STICK_SWAP"); val menu = c("MENU"); val start = c("START")
        assertEquals(sw.y, menu.y, 1f); assertEquals(menu.y, start.y, 1f); assertTrue(sw.x < menu.x && menu.x < start.x)
        assertEquals(st.x, menu.x, 2f, "the row centred under the stick")
        assertTrue(sw.y - sw.spec.r > st.y + st.spec.r * 208f / 170f, "under the stick's ring")
        assertTrue(c("B").y - st.y >= 150f * p.k, "B lower, away from the stick")
        // B / A side by side, clear of START's end of the row (B right of the middle on this narrow window) (user)
        assertEquals(c("B").y, c("A").y, 1f, "B level with A"); assertTrue(c("B").x - c("B").spec.r > start.x + start.spec.r, "B / A clear of START")
        // its well clear of the C-buttons' ring: the ring's radius 208, the well's round B ~1.1 B
        val cx = listOf("CU", "CD", "CL", "CR").map { c(it).x }.average().toFloat()
        val cy = listOf("CU", "CD", "CL", "CR").map { c(it).y }.average().toFloat()
        assertTrue(kotlin.math.hypot(c("B").x - cx, c("B").y - cy) >= (208f + 1.1f * 67f) * p.k, "B's well clear of the C ring")
        assertTrue(c("A").y + c("A").spec.r < 2520f - 20f, "on the window")
    }

    @Test
    fun regular3dsIsThe3dsWithoutTheNewControls() {
        // New 3DS Controls off (the default): the 3DS layouts without ZL / ZR and the C-stick nub, L / R the DS's pills
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            fun at(v: SkinVariant) = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, true, null,
                d.third, foldable = name.startsWith("inner"), variant = v), "$v $name")
            val n = at(SkinVariant.N3DS); val p = at(SkinVariant.N3DS_REGULAR)
            dump("3dsr_$name", d.first, d.second, p)
            assertEquals(SkinVariant.N3DS_REGULAR, p.spec.variant, name)
            assertEquals(n.spec.family, p.spec.family, name)
            assertEquals(n.glasses.map { it.screen }, p.glasses.map { it.screen }, name)
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            assertEquals(n.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet() - setOf("L2", "R2", "CSTICK"), ids, name)
            assertEquals(1, p.pieces.count { it.knob }, "$name the Circle Pad's knob only")
            val cs = p.controls.filterNot { it.spec.id.startsWith("TOUCH_") }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; ControlShape.DPAD -> pc.spec.r; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            val face = setOf("A", "B", "X", "Y")
            for (useStick in listOf(true, false)) {
                val sc = p.forStick(useStick).controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                for (i in sc.indices) for (j in i + 1 until sc.size) {
                    val a = sc[i]; val b = sc[j]
                    if (a.spec.id in face && b.spec.id in face) continue
                    if (a.spec.shape == ControlShape.DPAD || b.spec.shape == ControlShape.DPAD) continue   // the d-pad's corners: the 3DS test's own rule
                    val round = a.spec.shape != ControlShape.RECT && b.spec.shape != ControlShape.RECT
                    fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.STICK) pc.spec.r * 1.25f else pc.spec.r
                    assertTrue(if (round) kotlin.math.hypot(a.x - b.x, a.y - b.y) >= reach(a) + reach(b) - 1f else apart(box(a), box(b)), "$name ${a.spec.id} ${b.spec.id}")
                }
            }
            for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
        }
    }

    @Test
    fun n3dsLayoutsInEveryFamily() {
        // the DS's layouts with the Circle Pad (Dreamcast's stick: both shown on tablets, else swapped with the d-pad), a
        // C-stick nub above the face, ZL / ZR beside L / R; MIC SWAP MENU where the DS has them (phone landscape: SWAP
        // joins SELECT START on the right, the switch cap goes left)
        val roomy = setOf("tablet", "tablet_land", "inner", "inner_half", "inner_land")   // the fold board and tall landscape: both always
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            fun at(v: SkinVariant) = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, true, null,
                d.third, foldable = name.startsWith("inner"), variant = v), "$v $name")
            val ds = at(SkinVariant.DS)
            val p = at(SkinVariant.N3DS)
            dump("3ds_$name", d.first, d.second, p)
            dump("dsv_$name", d.first, d.second, ds)
            run {   // the DS's small caps as the 3DS's
                fun k(id: String) = ds.controls.first { it.spec.id == id }
                fun row(vararg ids: String) { for (i in 1 until ids.size) {
                    assertEquals(k(ids[0]).y, k(ids[i]).y, 1f, "ds $name ${ids[i]} in the row"); assertTrue(k(ids[i]).x > k(ids[i - 1]).x, "ds $name ${ids.toList()}") } }
                when {
                    name == "flip" -> row("MIC", "SWAP", "MENU")
                    // the fold board: MIC MENU on the left, SWAP SELECT START on the right, level (user)
                    name in setOf("inner", "inner_half", "tablet") -> row("MIC", "MENU", "SWAP", "SELECT", "START")
                    name == "cover" -> row("MIC", "MENU", "SWAP")
                    name in setOf("inner_land", "tablet_land") -> {   // tall landscape: each side's caps in one column
                        fun column(vararg ids: String) { for (i in 1 until ids.size) {
                            assertEquals(k(ids[0]).x, k(ids[i]).x, 1f, "ds $name ${ids[i]} in the column"); assertTrue(k(ids[i]).y > k(ids[i - 1]).y, "ds $name ${ids.toList()}") } }
                        column("MIC", "MENU"); column("SELECT", "START", "SWAP")
                        assertTrue(k("MIC").x < d.first / 2f && k("SWAP").x > d.first / 2f, "ds $name sides")
                    }
                    else -> {   // landscape: SWAP SELECT START in a row on the right, MIC MENU side by side on the left
                        row("MIC", "MENU"); row("SWAP", "SELECT", "START")
                        assertTrue(k("MIC").x < d.first / 2f && k("SWAP").x > d.first / 2f, "ds $name sides")
                    }
                }
            }
            assertEquals(SkinVariant.N3DS, p.spec.variant, name)
            assertEquals(ds.spec.family, p.spec.family, name)
            assertEquals(ds.glasses.map { it.screen }, p.glasses.map { it.screen }, name)
            val dsIds = ds.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            assertEquals(dsIds + setOf("STICK", "CSTICK", "L2", "R2", "SWAP") + (if (name in roomy) emptySet() else setOf("STICK_SWAP")), ids, name)   // SWAP on the phone's too
            assertEquals(setOf("STICK", "CSTICK"), p.pieces.filter { it.knob }.map { it.knobOf }.toSet(), name)
            fun c(id: String) = p.controls.first { it.spec.id == id }
            fun box(pc: PlacedControl): FloatArray {
                val r = when (pc.spec.shape) { ControlShape.STICK -> pc.spec.r * 1.25f; ControlShape.RECT -> 0f; else -> pc.spec.r }
                val hw = if (r > 0f) r else pc.spec.hw; val hh = if (r > 0f) r else pc.spec.hh
                return floatArrayOf(pc.x - hw, pc.y - hh, pc.x + hw, pc.y + hh)
            }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            for (useStick in listOf(true, false)) {
                val shown = p.forStick(useStick)
                if (name !in roomy) assertEquals(1, shown.controls.count { it.spec.id == "STICK" || it.spec.id == "DPAD" }, "$name $useStick")
                val cs = shown.controls.filterNot { it.spec.id.startsWith("TOUCH_") }
                val face = setOf("A", "B", "X", "Y")
                for (i in cs.indices) for (j in i + 1 until cs.size) {
                    if (cs[i].spec.id in face && cs[j].spec.id in face) continue
                    // two round ones (caps, sticks with their slop) apart as circles; anything else by its box
                    fun round(pc: PlacedControl) = pc.spec.shape == ControlShape.CIRCLE || pc.spec.shape == ControlShape.STICK
                    fun reach(pc: PlacedControl) = if (pc.spec.shape == ControlShape.STICK) pc.spec.r * 1.25f else pc.spec.r
                    val ok = if (round(cs[i]) && round(cs[j])) kotlin.math.hypot(cs[i].x - cs[j].x, cs[i].y - cs[j].y) >= reach(cs[i]) + reach(cs[j])
                        else apart(box(cs[i]), box(cs[j]))
                    assertTrue(ok, "$name ${cs[i].spec.id} ${cs[j].spec.id}")
                }
                for (x in cs) assertTrue(x.x >= 0f && x.x <= d.first && x.y >= 0f && x.y <= d.second, "$name ${x.spec.id} off the window")
                for (g in p.glasses) for (x in cs) assertTrue(apart(box(x), floatArrayOf(g.rect.x, g.rect.y, g.rect.x + g.rect.width, g.rect.y + g.rect.height)), "$name ${x.spec.id} on the ${g.screen} glass")
            }
            val foldBoard = name in setOf("inner", "inner_half", "tablet")
            val tall = name in setOf("inner_land", "tablet_land")
            if (p.spec.id.startsWith("large_3ds")) {
                // the large layout: the nub centred between the stick and the face, level with the face's middle row
                assertTrue(c("CSTICK").x > c("STICK").x && c("CSTICK").x < c("Y").x, "$name nub between the stick and the face")
                assertEquals(c("A").y, c("CSTICK").y, 1f, "$name nub level with A")
            } else if (foldBoard || tall) assertTrue(c("CSTICK").y > c("B").y, "$name C-stick under the face (user)")
            else assertTrue(c("CSTICK").y < c("X").y, "$name nub above X")
            if (tall) assertEquals(c("X").x, c("CSTICK").x, 1f, "$name C-stick in line under the face, as the d-pad under the stick (user)")
            else if (!p.spec.id.startsWith("large_3ds") && !foldBoard) assertTrue(c("CSTICK").x > c("X").x && c("CSTICK").y < c("A").y - c("A").spec.r, "$name nub up-right of X, above A")
            fun row(vararg want: String) {
                val cs = want.map(::c)
                for (x in cs) assertEquals(cs[0].y, x.y, 1f, "$name ${x.spec.id} in the row")
                for (i in 1 until cs.size) assertTrue(cs[i].x > cs[i - 1].x, "$name ${want.toList()} in order")
            }
            if (name == "flip") {   // the row at the bottom, MENU in its middle; SWAP between the shoulders, nearest the screens (user)
                row("STICK_SWAP", "MIC", "MENU", "SELECT", "START")
                assertEquals(d.first / 2f, c("MENU").x, 30f * p.k, "$name MENU in the middle")
                row("L", "L2", "SWAP", "R2", "R")
                assertEquals(d.first / 2f, c("SWAP").x, 30f * p.k, "$name SWAP in the middle")
            }
            // (the tablet's own board: its caps' rows are still to review)
            if (foldBoard && name != "tablet") {   // one row under the bottom screen, SELECT / START in it (user)
                // (MIC, MENU, SWAP, SELECT, START, SWAP in the middle (user))
                val order = listOf("MIC", "MENU", "SWAP", "SELECT", "START")
                row(*order.toTypedArray())
                val t = p.glasses.single { it.screen == GlassScreen.TOUCH }.rect
                assertTrue(c("MENU").y > t.y + t.height, "$name row under the bottom screen")
                assertEquals(t.x + t.width / 2f, c(order[2]).x, 2f, "$name ${order[2]} in the middle")
            }
            if (foldBoard) assertEquals(c("STICK").spec.r * 0.85f, c("CSTICK").spec.r, 0.5f, "$name C-stick a stick at the d-pad's 85% (user)")
            // (two large controls in a box: the lower one, the C-stick under the face, at 85% (user))
            else if (tall) assertEquals(c("STICK").spec.r * 0.85f, c("CSTICK").spec.r, 0.5f, "$name C-stick at 85% (user)")
            else assertTrue(c("CSTICK").spec.r < c("STICK").spec.r / 2f, "$name nub small")
            if (name in roomy) assertTrue(c("STICK").y < c("DPAD").y, "$name Circle Pad above the d-pad")
            else {
                assertEquals(c("DPAD").x, c("STICK").x, 0.5f, "$name stick x")
                assertEquals(c("DPAD").y, c("STICK").y, 0.5f, "$name stick y")
            }
            if (p.spec.family == SkinFamily.LARGE || p.spec.id.startsWith("flex_3ds") && !p.spec.id.contains("fold")) {   // above L / R (the cover)
                assertEquals(c("L").x, c("L2").x, 1f, "$name ZL over L"); assertTrue(c("L2").y < c("L").y, "$name ZL above L")
                assertEquals(c("R").x, c("R2").x, 1f, "$name ZR over R"); assertTrue(c("R2").y < c("R").y, "$name ZR above R")
            } else { assertEquals(c("L").y, c("L2").y, 1f, "$name ZL beside L"); assertEquals(c("R").y, c("R2").y, 1f, "$name ZR beside R") }
            if (name == "cover") row("STICK_SWAP", "MIC", "MENU", "SWAP")   // one group of four
            if (tall) {   // each side's caps in a row under its pad: MIC MENU; SWAP SELECT START (user)
                row("MIC", "MENU"); row("SWAP", "SELECT", "START")
                assertTrue(c("MIC").y - c("DPAD").y > (c("MIC").spec.r + c("DPAD").spec.r) * p.k, "$name MIC clear of the d-pad")
                assertTrue(c("SWAP").y - c("CSTICK").y > c("SWAP").spec.r + c("CSTICK").spec.r * 1.25f, "$name SWAP clear of the C-stick")
            }
            if (name == "phone_land") {   // SWAP on the right with SELECT START, the switch cap on the left
                assertTrue(c("SWAP").x > d.first / 2f, "$name SWAP right")
                assertTrue(c("STICK_SWAP").x < d.first / 2f, "$name switch left")
                // the height short of two rows: one each side, switch MIC MENU; SWAP SELECT START (user)
                row("STICK_SWAP", "MIC", "MENU"); row("SWAP", "SELECT", "START")
                // the C-stick lowered toward A, clear of X by its reach (user)
                assertTrue(c("CSTICK").y > c("R").y + c("R").spec.hh + c("CSTICK").spec.r, "$name nub clear of R")
            }
        }
    }

    @Test
    fun gbaShoulderCapsDoNotOverlap() {
        for ((name, d) in consoleDevices) {
            val p = consolePlace(SkinVariant.GBA, name, d)
            fun box(id: String) = p.controls.first { it.spec.id == id }.let { floatArrayOf(it.x - it.spec.hw, it.y - it.spec.hh, it.x + it.spec.hw, it.y + it.spec.hh) }
            fun apart(a: FloatArray, b: FloatArray) = a[2] <= b[0] || b[2] <= a[0] || a[3] <= b[1] || b[3] <= a[1]
            val ids = listOf("L", "L2", "R2", "R")
            for (i in ids.indices) for (j in i + 1 until ids.size) assertTrue(apart(box(ids[i]), box(ids[j])), "$name ${ids[i]} ${ids[j]}")
            for (s in ids) for (c in listOf("DPAD", "A", "B", "X", "Y")) {
                val o = p.controls.first { it.spec.id == c }; val r = if (c == "DPAD") o.spec.r else o.spec.r
                assertTrue(apart(box(s), floatArrayOf(o.x - r, o.y - r, o.x + r, o.y + r)), "$name $s $c")
            }
        }
    }

    @Test
    fun foldInnerPortraitIsTheFoldBoardFlatAndHalfFolded() {
        // the inner screen: the fold board, split at its middle flat and half-folded alike; flat it is drawn without its
        // hinge (one slab) and the top screen's frame reaches the middle, half-folded it keeps clear of the hinge
        for ((suffix, hinge) in listOf("flat" to null, "half" to 1224f)) {
            val ds = assertNotNull(place("fold_inner_$suffix", 1848, 2448, tablet = true, hinge = hinge, foldable = true))
            assertEquals("flex_ds_fold", ds.spec.id.removeSuffix("_tab"))
            assertHalfFolded(ds, 1224f)
            assertEquals(3.5f, ds.glass(GlassScreen.TOUCH)!!.scale, 0.01f)   // as the old tablet render: touch 3.5x
            // the top screen: the frame filling the half above the hinge (the DS top screen centred in it), as the menu's,
            // with shell round it as a clamshell's: clear of the hinge (its top ~62 px above the fold) by the shell
            val top = ds.glass(GlassScreen.TOP)!!.rect
            assertTrue(top.width > 1600f && top.height > 1000f, "top $top")
            if (hinge != null) assertTrue(top.y + top.height <= 1224f - 62f - 32f, "top $top: on the hinge")
            else assertSlabGap(ds, top, ds.glass(GlassScreen.TOUCH)!!.rect)
            assertEquals(hinge == null, ds.slab, "slab $suffix")
            assertTrue(top.x >= 32f && top.x + top.width <= 1848f - 32f, "top $top: edge to edge")
            val gba = assertNotNull(place("fold_inner_${suffix}_gba", 1848, 2448, tablet = true, ds = false, picture = 240f to 160f, hinge = hinge, foldable = true))
            assertEquals("flex_single_fold", gba.spec.id.removeSuffix("_tab"))
            assertHalfFolded(gba, 1224f)
        }
        // the touch screen between the sides: MIC MENU left of it, SWAP SELECT START right of it, level (user)
        val p = assertNotNull(place("fold_inner_ds", 1848, 2448, tablet = true, foldable = true))
        val t = p.glass(GlassScreen.TOUCH)!!.rect
        val c = p.controls.associateBy { it.spec.id }
        val five = listOf("MIC", "MENU", "SWAP", "SELECT", "START").map(c::getValue)
        for (i in 1 until five.size) { assertEquals(five[0].y, five[i].y, 1f, "one height"); assertTrue(five[i].x > five[i - 1].x, "in order") }
        assertTrue(c.getValue("MENU").x + c.getValue("MENU").spec.r < t.x && c.getValue("SWAP").x - c.getValue("SWAP").spec.r > t.x + t.width, "beside the touch screen")
        assertTrue(c.getValue("DPAD").x + c.getValue("DPAD").spec.r < t.x, "dpad")
        // a tablet that does not fold takes the fold board too (the menu's board: a game never changes it)
        assertEquals("flex_ds_fold", assertNotNull(place("inner_size_tablet", 1848, 2448, tablet = true)).spec.id.removeSuffix("_tab"))
        // the main menu keeps the phone board, a foldable's inner screen too
        assertEquals("stacked_menu", assertNotNull(SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true)).spec.id)
        assertEquals("stacked_menu", assertNotNull(SkinChoice.placeMenu(pack, 1848, 2448, 420)).spec.id)
    }

    @Test
    fun flipIsStackedFlatAndFlexHalfFolded() {
        val flat = assertNotNull(place("flip_flat", 1080, 2520))
        assertEquals("stacked_ds", flat.spec.id)
        assertEquals(1080f - 2 * (40 + 30), flat.glass(GlassScreen.STACK)!!.rect.width, 1f)   // the window less the shell margins and the frame border
        val flex = assertNotNull(place("flip_flex", 1080, 2520, hinge = 1200f, foldable = true))
        assertHalfFolded(flex, 1200f)
        assertEquals("flex_ds", flex.spec.id)
        assertEquals(2f, flex.glass(GlassScreen.TOUCH)!!.scale)
        assertHalfFolded(assertNotNull(place("flip_flex_gba", 1080, 2520, ds = false, picture = 240f to 160f, hinge = 1200f)), 1200f)
    }

    /** Half-folded: the screen frame above the fold (a DS's top screen), the touch screen, the strip and the pad below it. */
    /**
     * Flat ([SkinPlacement.slab]) the top screen's frame runs past the middle down to 16 dp above the frame under it (its
     * glass's border apart: the glasses at least that far apart, no more than a frame's borders more).
     */
    private fun assertSlabGap(p: SkinPlacement, above: com.nxredux.mobile.options.PxRect, under: com.nxredux.mobile.options.PxRect, dpi: Int = 420) {
        val gap = under.y - (above.y + above.height)
        assertTrue(gap >= 16f * dpi / 160f && gap < 16f * dpi / 160f + 200f, "${p.spec.id} glasses $gap apart: $above over $under")
    }

    private fun assertHalfFolded(p: SkinPlacement, hinge: Float) {
        val ds = p.glass(GlassScreen.TOP) != null
        assertEquals(if (ds) "flex_ds" else "flex_single", p.spec.id.removeSuffix("_wide").removeSuffix("_fold"))
        val above = assertNotNull(p.glass(if (ds) GlassScreen.TOP else GlassScreen.PICTURE)).rect
        val under = p.glass(if (ds) GlassScreen.TOUCH else GlassScreen.PANEL)?.rect
        if (p.slab && under != null) assertSlabGap(p, above, under) else assertTrue(above.y + above.height < hinge, "${p.spec.id} $above")
        if (ds) assertTrue(p.glass(GlassScreen.TOUCH)!!.rect.y > hinge)
        val below = p.controls.filter { it.spec.id in setOf("DPAD", "A", "L", "R", "START", "MENU") + if (ds) setOf("MIC", "SWAP", "TOUCH_1") else emptySet() }
        assertTrue(below.all { it.y > hinge }, "${p.spec.id} ${below.map { it.spec.id to it.y }}")
        // the touch shortcut strip on every game layout (the fold board's one-screen ones: under the panel, below the hinge)
        val panel = p.glass(GlassScreen.PANEL)
        if (panel != null) assertTrue(panel.rect.y > hinge && p.controls.filter { it.spec.id.startsWith("TOUCH_") }.all { it.y > panel.rect.y + panel.rect.height }, p.spec.id)
        assertTrue(p.controls.any { it.spec.id == "TOUCH_4" }, p.spec.id)
    }

    @Test
    fun foldCoverIsTheLargeLayout() {
        val p = assertNotNull(place("fold_cover", 1248, 1972))
        assertEquals("large_ds", p.spec.id)
        // the top screen as big as fits its filling panel (past 4x), the touch screen at twice
        assertTrue(p.glasses[0].scale > 4f, "${p.glasses[0].scale}")
        assertEquals(2f, p.glasses[1].scale)
    }

    @Test
    fun landscapeIsTheMenuShell() {
        // sideways (the unfolded Fold, a tablet): the main menu's landscape shell, the game in its screen frame
        for ((name, w, h, dpi) in listOf(Quad("fold_land", 2448, 1848, 420), Quad("tab_s10_land", 2304, 1440, 280))) {
            val ds = assertNotNull(place(name, w, h, dpi = dpi, tablet = true), name)
            assertTrue(ds.spec.id.startsWith("grip_ds"), "$name ${ds.spec.id}")
            val dsIds = ds.controls.map { it.spec.id }
            assertTrue(dsIds.containsAll(listOf("MIC", "SWAP", "MENU", "TOUCH_1", "TOUCH_4", "DPAD", "A")), "$name $dsIds")
            val stack = assertNotNull(ds.glass(GlassScreen.STACK), name).rect
            println("$name ds glass $stack")
            val gba = assertNotNull(place(name + "_gba", w, h, dpi = dpi, tablet = true, ds = false, picture = 240f to 160f), name)
            assertTrue(gba.spec.id.startsWith("grip_single"), "$name ${gba.spec.id}")
            assertNotNull(gba.glass(GlassScreen.PICTURE), name)
            val gbaIds = gba.controls.map { it.spec.id }
            assertTrue("TOUCH_1" in gbaIds && "MIC" !in gbaIds, "$name $gbaIds")
            println("$name gba glass ${gba.glasses.single().rect}")
        }
        // phones sideways too (the Fold cover, the Flip, a 20:9 phone), a DS's screens stacked in the glass
        for ((name, w, h) in listOf(Triple("cover_land", 2520 - 100, 1080), Triple("flip_land", 2520, 1032), Triple("p20x9_land", 2400, 1080))) {
            val gba = assertNotNull(place(name + "_gba", w, h, ds = false, picture = 240f to 160f), name)
            assertTrue(gba.spec.id.startsWith("grip_single"), "$name ${gba.spec.id}")
            val ds = assertNotNull(place(name + "_ds", w, h), name)
            println("$name ds glass ${assertNotNull(ds.glass(GlassScreen.STACK), name).rect} gba ${gba.glasses.single().rect}")
        }
    }

    @Test
    fun otherPhonesGetALayout() {
        assertNotNull(place("phone_20x9", 1080, 2400))
        assertNotNull(place("phone_19x9", 1080, 2280))
        assertNotNull(place("phone_qhd", 1440, 3200, dpi = 560))
        assertNotNull(place("phone_narrow", 1080, 2340, dpi = 480))
        assertNotNull(place("phone_gba", 1080, 2400, ds = false, picture = 240f to 160f))
        assertNotNull(place("phone_snes", 1080, 2400, ds = false, picture = 256f to 224f))
        assertNotNull(place("tab_s10_portrait", 1440, 2304, dpi = 280, tablet = true))
        assertNotNull(place("tab_s10_land", 2304, 1440, dpi = 280, tablet = true))
        assertNotNull(place("phone_land_gba", 2400, 1080, ds = false, picture = 240f to 160f))
    }

    /** [p]'s row pieces: the d-pad / face rings, L, R and the right START / SELECT column (by the controls they hold). */
    private class RowPieces(val lRing: List<SkinPiece>, val rRing: List<SkinPiece>, val l: List<SkinPiece>, val r: List<SkinPiece>, val col: List<SkinPiece>)

    private fun rowPieces(p: SkinPlacement): RowPieces {
        val spec = p.spec
        fun held(a: PieceAnchor, ids: Set<String>) = p.pieces.filter { pc -> pc.piece >= 0 && spec.pieces[pc.piece].anchor == a && spec.pieces[pc.piece].glass == null &&
            spec.controls.any { c -> c.id in ids && c.anchor == a && spec.pieces[pc.piece].let { s -> c.x >= s.x && c.x <= s.x + s.w * s.scale && c.y >= s.y && c.y <= s.y + s.h * s.scale } } }
        return RowPieces(held(PieceAnchor.LEFT, setOf("DPAD")), held(PieceAnchor.RIGHT, setOf("X", "Y", "A", "B")), held(PieceAnchor.LEFT, setOf("L")),
            held(PieceAnchor.RIGHT, setOf("R")), held(PieceAnchor.RIGHT, setOf("START", "SELECT")))
    }

    private fun twoScreens(name: String, p: SkinPlacement?, w: Int, h: Int, hinge: Float) {
        val placed = assertNotNull(p, name)
        dump(name, w, h, placed)
        // the board's own hinge is drawn where the fold is: its middle row at [hinge]
        val hc = placed.cells.filter { it.src.y in 1163f..1165f }
        assertTrue(hc.isNotEmpty() && hc.all { kotlin.math.abs(it.dst.y + it.dst.height / 2f - hinge) < 2f }, "$name hinge cells ${hc.map { it.dst }}")
        assertEquals("stacked_menu_fold", placed.spec.id, name)
        val top = placed.glasses.single { it.id == "menutop" }.rect
        val menu = placed.glasses.single { it.id == "menu" }.rect
        if (placed.slab) assertSlabGap(placed, top, menu, if (w == 1440) 280 else 420)
        else assertTrue(top.y + top.height < hinge && hinge < menu.y, "$name top $top menu $menu hinge $hinge")
        val c = placed.controls.associateBy { it.spec.id }
        assertTrue("MIC" !in c && "SWAP" !in c, name)
        // MENU in the left group across from START / SELECT on the right (its label on its left); nothing under the
        // menu's screen, which reaches the side groups' bottom
        val m = c.getValue("MENU"); val st = c.getValue("START"); val se = c.getValue("SELECT")
        assertTrue(m.x + m.spec.r < menu.x && m.spec.labelDx < 0f, "$name MENU ${m.x} beside the glass $menu")
        // START / SELECT start 16 dp under their ring ([SkinSeamTest]); MENU, alone, level with the pair's middle
        assertEquals((st.y + se.y) / 2f, m.y, 1f, "$name MENU level with START / SELECT")
        // its inner edge as far from the screen as START / SELECT's (two gaps, [SkinSeamTest]); the caps differ in width
    }

    @Test
    fun foldInnerMenuIsTwoScreens() {
        twoScreens("menu_inner_two", SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true), 1848, 2448, 1224f)
        twoScreens("menu_inner_half", SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true, hingeY = 1236f), 1848, 2448, 1236f)
    }

    @Test
    fun tabletMenuIsTwoScreens() {
        twoScreens("menu_tab_two", SkinChoice.placeMenu(pack, 1440, 2304, 280, tablet = true), 1440, 2304, 1152f)
        // the tablet's wider room: the menu's touch screen grows
        val tab = assertNotNull(SkinChoice.placeMenu(pack, 1440, 2304, 280, tablet = true))
        val tg = tab.glasses.single { it.id == "menu" }.rect
        assertTrue(tg.width / tab.k > 944f, "tablet menu glass $tg")   // wider than drawn, the row's gaps round it
        // its shape kept as it grows, then taller down to the side groups' bottom (MENU beside the d-pad)
        assertTrue(tg.height >= tg.width * 740f / 944f - 1f, "tablet menu glass $tg")
        val fold = assertNotNull(SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true))
        // the side groups and the menu's screen spaced evenly across the Fold ([SkinSeamTest]): the screen at least 320 dp
        assertTrue(fold.glasses.single { it.id == "menu" }.rect.width >= 320f * 420f / 160f - 2f)
    }

    @Test
    fun flipAndPhonesKeepOneScreenMenu() {
        assertEquals("stacked_menu", SkinChoice.placeMenu(pack, 1080, 2520, 420, foldable = true)?.spec?.id)
        assertEquals("stacked_menu", SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true, twoScreens = false)?.spec?.id)
        assertTrue(SkinChoice.placeMenu(pack, 2448, 1848, 420, tablet = true)?.spec?.id?.startsWith("grip_menu") == true)
    }

    // The shared one-screen set (the SNES's buttons, any console without its own): the main menu's pads, shoulders and
    // caps where the menu has them on every device class, its screen the GBA's (user).
    @Test
    fun theSharedOneScreenSetIsTheMenusPad() {
        for (d in GOLDEN_DEVICES) {
            val menu = assertNotNull(placeFor(pack, null, d), "${d.name} menu").controls.associateBy { it.spec.id }
            val single = assertNotNull(placeFor(pack, SkinVariant.SINGLE, d), "${d.name} single")
            val c = single.controls.filter { !it.spec.id.startsWith("TOUCH_") }.associateBy { it.spec.id }
            assertEquals(menu.keys, c.keys, "${d.name} the menu's buttons")
            for ((id, m) in menu) {
                assertEquals(m.x, c.getValue(id).x, 1f, "${d.name} $id x"); assertEquals(m.y, c.getValue(id).y, 1f, "${d.name} $id y")
                assertEquals(m.spec.r, c.getValue(id).spec.r, 1f, "${d.name} $id r"); assertEquals(m.spec.hw, c.getValue(id).spec.hw, 1f, "${d.name} $id hw")
            }
            val gba = assertNotNull(placeFor(pack, SkinVariant.GBA, d), "${d.name} gba").glasses.first().rect
            val g = single.glasses.first().rect
            assertEquals(gba.x, g.x, 1f, "${d.name} screen x"); assertEquals(gba.y, g.y, 1f, "${d.name} screen y")
            assertEquals(gba.width, g.width, 1f, "${d.name} screen w"); assertEquals(gba.height, g.height, 1f, "${d.name} screen h")
        }
    }

    @Test
    fun theSegaPadSitsOnTheMenusSpots() {
        // Genesis / Sega CD / 32X: the d-pad and MENU on the menu's, the six (or A B C) round the face diamond's centre,
        // no shoulders, the screen as the shared set's; 6-button: MODE on SELECT's cap; 3-button: START beside MENU
        for (d in GOLDEN_DEVICES) for (six in listOf(true, false)) {
            val v = if (six) SkinVariant.MD else SkinVariant.MD3
            val menu = assertNotNull(placeFor(pack, null, d), "${d.name} menu").controls.associateBy { it.spec.id }
            val md = assertNotNull(placeFor(pack, v, d), "${d.name} $v")
            val c = md.controls.associateBy { it.spec.id }
            val dp = menu.getValue("DPAD")
            assertEquals(dp.x, c.getValue("DPAD").x, 1f, "${d.name} $v d-pad x"); assertEquals(dp.y, c.getValue("DPAD").y, 1f, "${d.name} $v d-pad y")
            val face = listOf("A", "B", "X", "Y").map { menu.getValue(it) }
            val fx = face.sumOf { it.x.toDouble() } / 4; val fy = face.sumOf { it.y.toDouble() } / 4
            // A B C on Y X A (the bottom row's middle B under the face's centre, a row's rise to the right)
            val b = c.getValue("X")
            assertEquals(fx.toFloat(), b.x, 1f, "${d.name} $v B x"); assertEquals(fy.toFloat() + 70f * md.k, b.y, 1f, "${d.name} $v B y")
            assertTrue(c.getValue("Y").x < b.x && b.x < c.getValue("A").x && c.getValue("A").y < c.getValue("Y").y, "${d.name} $v A B C rising right")
            if (six) {
                assertEquals(setOf("DPAD", "Y", "X", "A", "B", "L", "R", "SELECT", "START", "MENU"), c.keys.filterNot { it.startsWith("TOUCH_") }.toSet(), "${d.name} $v")
                assertTrue(listOf("B", "L", "R").all { c.getValue(it).y < b.y }, "${d.name} X Y Z above")
                assertEquals("MODE", c.getValue("SELECT").spec.label ?: "MODE", "${d.name} MODE")
                for (id in listOf("SELECT", "START", "MENU")) {
                    assertEquals(menu.getValue(id).x, c.getValue(id).x, 1f, "${d.name} $id x"); assertEquals(menu.getValue(id).y, c.getValue(id).y, 1f, "${d.name} $id y")
                }
            } else {
                assertEquals(setOf("DPAD", "Y", "X", "A", "START", "MENU"), c.keys.filterNot { it.startsWith("TOUCH_") }.toSet(), "${d.name} $v")
                val m = c.getValue("MENU"); val st = c.getValue("START"); val mm = menu.getValue("MENU")
                fun at(p: PlacedControl, q: PlacedControl) = kotlin.math.abs(p.x - q.x) < 1f && kotlin.math.abs(p.y - q.y) < 1f
                // in the bottom row the pair round the menu's MENU; where the menu has MENU beside the d-pad (sideways, the
                // fold board) MENU on the 6-button's MODE spot (the menu's SELECT) by START, under the face (user)
                val row = kotlin.math.abs(m.y - st.y) < 1f && kotlin.math.abs((m.x + st.x) / 2f - mm.x) < 1f
                assertTrue(row || (at(m, menu.getValue("SELECT")) && at(st, menu.getValue("START"))), "${d.name} MENU ${m.x},${m.y} START ${st.x},${st.y}")
            }
            val single = assertNotNull(placeFor(pack, SkinVariant.SINGLE, d), "${d.name} single").glasses.first().rect
            val g = md.glasses.first().rect
            assertEquals(single.x, g.x, 1f, "${d.name} $v screen x"); assertEquals(single.y, g.y, 1f, "${d.name} $v screen y")
            assertEquals(single.width, g.width, 1f, "${d.name} $v screen w")
            // 3-button: no MODE / START row under the screen where the shared set has one, so the screen may take its room
            if (six) assertEquals(single.height, g.height, 1f, "${d.name} $v screen h")
            else assertTrue(g.height >= single.height - 1f, "${d.name} $v screen h ${g.height} under ${single.height}")
        }
    }

    @Test
    fun theSaturnPadIsTheSixWithShoulders() {
        // the Genesis's six on the same spots, L / R (L2 / R2) where the menu has L / R, START by MENU as the 3-button's
        for (d in GOLDEN_DEVICES) {
            val menu = assertNotNull(placeFor(pack, null, d), "${d.name} menu").controls.associateBy { it.spec.id }
            val md = assertNotNull(placeFor(pack, SkinVariant.MD, d), "${d.name} md").controls.associateBy { it.spec.id }
            val md3 = assertNotNull(placeFor(pack, SkinVariant.MD3, d), "${d.name} md3").controls.associateBy { it.spec.id }
            val ss = assertNotNull(placeFor(pack, SkinVariant.SATURN, d), "${d.name} saturn")
            val c = ss.controls.associateBy { it.spec.id }
            assertEquals(setOf("DPAD", "Y", "X", "A", "B", "L", "R", "L2", "R2", "START", "MENU"), c.keys.filterNot { it.startsWith("TOUCH_") }.toSet(), d.name)
            for (id in listOf("DPAD", "Y", "X", "A", "B", "L", "R")) {
                assertEquals(md.getValue(id).x, c.getValue(id).x, 1f, "${d.name} $id x"); assertEquals(md.getValue(id).y, c.getValue(id).y, 1f, "${d.name} $id y")
            }
            for ((id, m) in listOf("L2" to "L", "R2" to "R")) {
                assertEquals(menu.getValue(m).x, c.getValue(id).x, 1f, "${d.name} $id x"); assertEquals(menu.getValue(m).y, c.getValue(id).y, 1f, "${d.name} $id y")
            }
            for (id in listOf("START", "MENU")) {
                assertEquals(md3.getValue(id).x, c.getValue(id).x, 1f, "${d.name} $id x"); assertEquals(md3.getValue(id).y, c.getValue(id).y, 1f, "${d.name} $id y")
            }
        }
    }

    @Test
    fun tabletClustersCentreOnTheBottomScreen() {
        // the tablet twins: the side clusters' span centred on the bottom screen, within a cap's radius (a capped top screen aside)
        // (the consoles following the menu take its fold board instead: the GBA, the shared one-screen set; the PS1 and the
        // PSP keep their d-pad and face on the menu's spots, their sticks under them)
        val menu = assertNotNull(SkinChoice.placeMenu(pack, 1440, 2304, 280, tablet = true)).controls.associateBy { it.spec.id }
        for (v in listOf(SkinVariant.PSP, SkinVariant.WII_SIDEWAYS, SkinVariant.N3DS_REGULAR, SkinVariant.PS1)) {
            val p = assertNotNull(SkinChoice.place(pack, 1440, 2304, 280, true, v == SkinVariant.N3DS_REGULAR, 640f to 480f, null, variant = v), "$v")
            assertTrue(p.spec.id.endsWith("_fold_tab"), "$v ${p.spec.id}")
            val screen = (p.glasses.firstOrNull { it.screen == GlassScreen.PANEL } ?: p.glass(GlassScreen.TOUCH)!!).rect
            val side = p.controls.filter { it.spec.id != "MENU" && it.y > screen.y - 200f && (it.x < screen.x || it.x > screen.x + screen.width) }
            fun ext(c: PlacedControl) = if (c.spec.shape == ControlShape.RECT) c.spec.hh else c.spec.r * (if (c.spec.shape == ControlShape.STICK) 1.07f else 1f)
            val mid = (side.minOf { it.y - ext(it) } + side.maxOf { it.y + ext(it) }) / 2f
            if (v == SkinVariant.PS1 || v == SkinVariant.PSP) for (id in listOf("DPAD", "Y")) {
                assertEquals(menu.getValue(id).x, p.controls.first { it.spec.id == id }.x, 1f, "$v $id x on the menu's")
                assertEquals(menu.getValue(id).y, p.controls.first { it.spec.id == id }.y, 1f, "$v $id y on the menu's")
            } else if (v == SkinVariant.N3DS_REGULAR) {
                // a capped top screen (the 3DS's wide one): the touch screen up under it, the controls centred in the height
                // between the top screen's frame and the window's bottom instead
                val top = p.glass(GlassScreen.TOP)!!.rect
                val all = p.controls.filter { it.y > top.y + top.height }
                val span = (all.minOf { it.y - ext(it) } + all.maxOf { it.y + ext(it) }) / 2f
                assertTrue(span > top.y + top.height && span < 2304f, "$v controls under the frame: $span")
                assertTrue(screen.y < mid - screen.height / 2f, "$v touch screen above the controls' middle: $screen $mid")
            } else assertEquals(screen.y + screen.height / 2f, mid, 40f * p.k, "$v clusters centred on the bottom screen")
        }
    }

    @Test
    fun tabletGamesTakeTheFoldBoard() {
        // the fold board's tablet twin: the clusters centred on the bottom screen there
        assertEquals("flex_ds_fold_tab", place("tab_two_ds", 1440, 2304, dpi = 280, tablet = true)?.spec?.id)
        assertEquals("flex_single_fold_tab", place("tab_two_gba", 1440, 2304, dpi = 280, tablet = true, ds = false, picture = 240f to 160f)?.spec?.id)
    }

    @Test
    fun theMenuPadFillsTheRestWithTheMenuAndKeepsOnlyThePadButtons() {
        for ((name, w, h, dpi) in listOf(
            Quad("menu_flip", 1080, 2520 - 120, 420), Quad("menu_cover", 1248, 1972 - 100, 420), Quad("menu_inner", 1848, 2448 - 100, 420),
            Quad("menu_20x9", 1080, 2400 - 100, 420), Quad("menu_qhd", 1440, 3200 - 140, 560), Quad("menu_tab", 1440, 2304 - 60, 280),
        )) {
            val p = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi), name)
            dump(name, w, h, p)
            assertEquals("stacked_menu", p.spec.id)
            val g = p.glasses.single().rect
            // the frame fills the room above the controls: the glass spans the width but for the border
            assertTrue(g.width > w - 160f * p.k && g.x + g.width <= w && g.y >= 0f, "$name glass $g")
            assertEquals(w / 2f, g.x + g.width / 2f, 20f * p.k, name)
            assertEquals(setOf("DPAD", "A", "B", "X", "Y", "L", "R", "START", "SELECT", "MENU"), p.controls.map { it.spec.id }.toSet())
            assertTrue(p.controls.all { it.y in (g.y + g.height)..h.toFloat() }, name)
            // the buttons keep their size and their side, the small ones centred; the rings the edge gap (32 dp where they
            // fit) from the edges, L / R 16 dp in from their rings' outer edges and 16 dp over them ([RowSpacing])
            val c = p.controls.associateBy { it.spec.id }
            assertEquals(w / 2f, c.getValue("MENU").x, 0.5f, name)
            val rp = rowPieces(p)
            val gap = rp.lRing.minOf { it.dst.x }
            assertTrue(gap <= 32f * dpi / 160f + 1f && gap > 0f, "$name gap $gap")
            assertEquals(gap, w - rp.rRing.maxOf { it.dst.x + it.dst.width }, 1f, "$name right ring")
            assertEquals(rp.lRing.minOf { it.dst.x } + 16f * dpi / 160f, rp.l.minOf { it.dst.x }, 1f, "$name L")
            assertEquals(rp.rRing.maxOf { it.dst.x + it.dst.width } - 16f * dpi / 160f, rp.r.maxOf { it.dst.x + it.dst.width }, 1f, "$name R")
            assertEquals(16f * dpi / 160f, rp.lRing.minOf { it.dst.y } - rp.l.maxOf { it.dst.y + it.dst.height }, 1f, "$name L over the d-pad")
            assertEquals(8 + 1 + 7, p.pieces.size, name)   // the frame (corners and edges), the SELECT START well, then the seven button pieces
        }
        assertNull(SkinChoice.placeMenu(pack, 1080, 1100, 420))   // too short: no menu layout fits
        // the game's choice never picks the menu layout
        assertEquals("stacked_single", place("game_single", 1080, 2400, ds = false, picture = 240f to 160f)?.spec?.id)
    }

    @Test
    fun aOneScreenGameKeepsItsPictureShapeWithTheFrameAndStripRoundIt() {
        for ((name, w, h, dpi) in listOf(
            Quad("flip", 1080, 2520 - 120, 420), Quad("20x9", 1080, 2400 - 100, 420), Quad("qhd", 1440, 3200 - 140, 560),
            Quad("cover", 1248, 1972 - 100, 420), Quad("inner", 1848, 2448 - 100, 420), Quad("tab", 1440, 2304 - 60, 280),
        )) for ((console, picture) in listOf("gba" to (240f to 160f), "snes" to (256f to 224f))) {
            val id = "game_${console}_$name"
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = false, ds = false, picture = picture), id)
            dump(id, w, h, p)
            assertEquals("stacked_single", p.spec.id)
            val g = p.glass(GlassScreen.PICTURE)!!.rect
            // the frame fills the room above the pad (the game places its picture inside); the scale: the picture as big as fits
            assertTrue(g.width > w - 160f * p.k, "$id glass $g")
            assertEquals(minOf(g.width / picture.first, g.height / picture.second), p.glass(GlassScreen.PICTURE)!!.scale, 0.01f, id)
            assertEquals(w / 2f, g.x + g.width / 2f, 20f * p.k, id)
            // the touch shortcut slots: an even share of the picture's width each, just under it, above the pad's buttons;
            // no label art (the app writes each slot's action)
            val slots = listOf("TOUCH_1", "TOUCH_2", "TOUCH_3", "TOUCH_4")
            val strip = p.controls.filter { it.spec.id in slots }.sortedBy { it.x }
            assertEquals(slots, strip.map { it.spec.id }, id)
            assertTrue(p.pieces.none { it.strip }, id)
            strip.forEachIndexed { i, c -> assertEquals(g.x + g.width * (2 * i + 1) / 8f, c.x, 1f, id) }
            assertTrue(strip.all { it.y > g.y + g.height && it.y < g.y + g.height + 140f * p.k }, id)
            val pad = p.controls.filter { it.spec.id !in slots }
            assertTrue(pad.all { it.y > strip.first().y }, id)
        }
    }

    @Test
    fun aDsGameOnATallPhoneKeepsBothScreensShapeWithMicBesideMenu() {
        for ((name, w, h, dpi) in listOf(
            Quad("ds_flip", 1080, 2520 - 120, 420), Quad("ds_20x9", 1080, 2400 - 100, 420), Quad("ds_qhd", 1440, 3200 - 140, 560),
            Quad("ds_narrow", 1080, 2340 - 100, 480),
        )) {
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = false, ds = true), name)
            dump(name, w, h, p)
            assertEquals("stacked_ds", p.spec.id, name)
            val g = p.glass(GlassScreen.STACK)!!.rect
            assertTrue(g.width > w - 160f * p.k, "$name glass $g")   // the frame fills the room; the screens go in it
            val ids = p.controls.map { it.spec.id }.toSet()
            assertTrue(ids.containsAll(listOf("MIC", "MENU", "TOUCH_1", "TOUCH_4", "DPAD", "A")), "$name $ids")
            // the d-pad's and the face buttons' rings (208 px) keep apart, however narrow the phone
            val c = p.controls.associateBy { it.spec.id }
            val faceX = (c.getValue("Y").x + c.getValue("A").x) / 2f
            assertTrue(faceX - c.getValue("DPAD").x >= 2 * 208f * p.k, "$name rings ${faceX - c.getValue("DPAD").x}")
        }
    }

    @Test
    fun aDsGameOnAShorterPhoneHasTheBigTopScreenAndTheTouchScreenAtTwiceBetweenLAndR() {
        for ((name, w, h, dpi) in listOf(
            Quad("dsl_cover", 1248, 1972, 420), Quad("dsl_cover_bars", 1248, 1972 - 100, 420), Quad("dsl_16x9", 1080, 1920, 420),
        )) {
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = false, ds = true), name)
            dump(name, w, h, p)
            assertEquals("large_ds", p.spec.id, name)
            val top = p.glass(GlassScreen.TOP)!!.rect
            val touch = p.glass(GlassScreen.TOUCH)!!
            // the top screen's panel fills the room above the touch screen: the width less a thin shell border
            assertTrue(top.width > w - 200 * (dpi / 420f), "$name ${top.width}")
            assertEquals(w / 2f, top.x + top.width / 2f, 1f, name)
            assertEquals(2f, touch.scale, 0.001f, name)
            assertTrue(top.y + top.height < touch.rect.y, name)
            assertEquals(w / 2f, touch.rect.x + touch.rect.width / 2f, 1f, name)
            val c = p.controls.associateBy { it.spec.id }
            assertTrue(c.keys.containsAll(listOf("MIC", "SWAP", "MENU", "TOUCH_1", "TOUCH_4", "L", "R")), "$name ${c.keys}")
            // L and R beside the touch screen, clear of it
            assertTrue(c.getValue("L").x + c.getValue("L").spec.hw < touch.rect.x, name)
            assertTrue(c.getValue("R").x - c.getValue("R").spec.hw > touch.rect.x + touch.rect.width, name)
        }
    }

    @Test
    fun aHeldCapIsShadedToItsOwnSize() {
        val p = assertNotNull(SkinChoice.placeMenu(pack, 1080, 2400, 420))
        val events = ArrayList<com.nxredux.mobile.ui.nx.PadEvent>()
        val touch = com.nxredux.mobile.ui.nx.SkinPadTouch({ events += it }, { _, _ -> }, {}).apply { placement = p }
        val a = p.controls.first { it.spec.id == "A" }
        assertTrue(touch.press(1, a.x, a.y))
        assertEquals(listOf(com.nxredux.mobile.ui.nx.SkinPressShape.Circle(a.x, a.y, 54f)), touch.pressedShapes())
        val d = p.controls.first { it.spec.id == "DPAD" }
        assertTrue(touch.press(2, d.x + 100f, d.y - 100f))   // up-right: two arms
        assertEquals(3, touch.pressedShapes().size)
        touch.releaseAll()
        assertEquals(emptyList(), touch.pressedShapes())
    }

    @Test
    fun theScreenFrameClearsATopInsetWhileTheBackgroundRunsUnderIt() {
        // the Fold cover in a game (cutout 110 px) and the main menu under the status bar (130 px)
        val game0 = assertNotNull(SkinChoice.place(pack, 1080, 2520, 420, tablet = false, ds = false, picture = 240f to 160f))
        val game = assertNotNull(SkinChoice.place(pack, 1080, 2520, 420, tablet = false, ds = false, picture = 240f to 160f, topInset = 110))
        val menu = assertNotNull(SkinChoice.placeMenu(pack, 1080, 2400, 420, topInset = 130))
        for ((p, inset) in listOf(game to 110f, menu to 130f)) {
            val frameTop = p.pieces.minOf { it.dst.y }
            assertEquals(inset, frameTop, 0.5f, "frame right under the inset")
            assertTrue(p.cells.any { it.dst.y <= 0.5f }, "background from the window's top")
        }
        // the pad stays where it was; only the screen's top comes down
        val c0 = game0.controls.first { it.spec.id == "A" }
        val c1 = game.controls.first { it.spec.id == "A" }
        assertEquals(c0.y, c1.y, 0.5f)
        val g0 = game0.glass(GlassScreen.PICTURE)!!.rect
        val g1 = game.glass(GlassScreen.PICTURE)!!.rect
        assertEquals(g0.y + g0.height, g1.y + g1.height, 0.5f)
    }

    @Test
    fun theLandscapeMenuIsOneShellWithThePadsBesideTheScreen() {
        for ((name, w, h, dpi) in listOf(
            Quad("land_20x9", 2400, 1080, 420), Quad("land_flip", 2520, 1080 - 48, 480), Quad("land_cover", 1972, 1248 - 80, 420), Quad("land_inner", 2448, 1848 - 80, 420),
            Quad("land_qhd", 3200, 1440, 560), Quad("land_tab", 2304, 1440, 280),
        )) {
            val p = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi), name)
            dump(name, w, h, p)
            val colH = pack.layouts.single { it.id == "grip_menu_col" }.minRefH
            assertEquals(if (h / p.k >= 1500f) "grip_menu_tall" else if (h / p.k >= colH) "grip_menu_col" else "grip_menu", p.spec.id, name)
            val g = p.glasses.single().rect
            val c = p.controls.associateBy { it.spec.id }
            assertEquals(setOf("DPAD", "A", "B", "X", "Y", "L", "R", "START", "SELECT", "MENU"), c.keys)
            // the screen between the columns, centred (the frame's border: 28 left, 32 right), the frame its margin from the
            // top and bottom; the edge and inner gaps round the rings and the frame ([RowSpacing])
            assertEquals(w / 2f, g.x + g.width / 2f, 3f * p.k, name)
            val rp = rowPieces(p)
            val fr = p.pieces.filter { it.piece < 0 && !it.strip }
            val gaps = listOf(rp.lRing.minOf { it.dst.x }, fr.minOf { it.dst.x } - rp.lRing.maxOf { it.dst.x + it.dst.width },
                rp.rRing.minOf { it.dst.x } - fr.maxOf { it.dst.x + it.dst.width }, w - rp.rRing.maxOf { it.dst.x + it.dst.width })
            // the edge gaps alike (to 32 dp), the inner ones alike (to 16 dp)
            assertEquals(gaps[0], gaps[3], 2f, "$name edges $gaps"); assertEquals(gaps[1], gaps[2], 2f, "$name inner $gaps")
            assertTrue(gaps[0] <= 32f * dpi / 160f + 1f && gaps[1] <= 16f * dpi / 160f + 1f, "$name gaps $gaps")
            assertEquals(rp.lRing.minOf { it.dst.x } + 16f * dpi / 160f, rp.l.minOf { it.dst.x }, 1f, "$name L")
            assertEquals(16f * dpi / 160f, rp.lRing.minOf { it.dst.y } - rp.l.maxOf { it.dst.y + it.dst.height }, 1f, "$name L over the d-pad")
            assertEquals(2f * gaps[1], rp.col.minOf { it.dst.x } - fr.maxOf { it.dst.x + it.dst.width }, 1f, "$name START / SELECT from the screen")
            assertEquals(16f * dpi / 160f, rp.col.minOf { it.dst.y } - rp.rRing.maxOf { it.dst.y + it.dst.height }, 1f, "$name START / SELECT under the face buttons")
            assertTrue(c.values.all { it.x < g.x || it.x > g.x + g.width }, "$name glass $g")
            assertTrue(g.y >= 72f * p.k && g.y + g.height <= h - 72f * p.k, "$name glass $g")
            // the pads in their columns at the standard (portrait) size, the main ones (the d-pad, the face buttons)
            // centred in the window's height (user), unless that would put a piece nearer its bottom than 4 dp
            assertEquals(72f * p.k, c.getValue("A").spec.r, 0.5f, name)
            val faceY = listOf("X", "Y", "A", "B").map { c.getValue(it).y }.average().toFloat()
            assertEquals(c.getValue("DPAD").y, faceY, 1f, "$name the d-pad level with the face buttons")
            assertTrue(c.getValue("DPAD").y <= h / 2f + 1f, "$name centred, or lifted to fit")
            // MENU, alone, level with the middle of START / SELECT
            assertEquals((c.getValue("START").y + c.getValue("SELECT").y) / 2f, c.getValue("MENU").y, 1f, name)
            assertTrue(c.getValue("L").y - 54f * p.k > 0f, name)
            assertTrue(c.values.all { it.x < g.x || it.x > g.x + g.width }, name)
            assertEquals(8 + 1 + 7, p.pieces.size, name)   // the frame, the SELECT START well, the buttons
        }
        // with the status bar over it, the frame starts right under it
        val bar = assertNotNull(SkinChoice.placeMenu(pack, 2400, 1080, 420, topInset = 90))
        assertEquals(90f, bar.pieces.minOf { it.dst.y }, 0.5f)
    }

    private data class Quad(val name: String, val w: Int, val h: Int, val dpi: Int)
}
