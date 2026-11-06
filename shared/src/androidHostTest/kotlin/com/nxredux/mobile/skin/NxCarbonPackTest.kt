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
    private val N3DS_LAYOUTS = 8
    private val dir = File("../androidApp/src/main/assets/skins/nx-carbon")
    private val pack = assertNotNull(SkinPackManifest.parse(File(dir, "skin.json").readText()))

    private fun place(name: String, w: Int, h: Int, dpi: Int = 420, tablet: Boolean = false, ds: Boolean = true, picture: Pair<Float, Float>? = null, hinge: Float? = null, foldable: Boolean = false): SkinPlacement? =
        SkinChoice.place(pack, w, h, dpi, tablet, ds, picture, hinge, foldable = foldable).also { p -> dump(name, w, h, p) }

    private fun dump(name: String, w: Int, h: Int, p: SkinPlacement?) {
        val out = System.getenv("SKIN_PREVIEW_DIR") ?: return
        val sb = StringBuilder()
        sb.append("{\"window\":[$w,$h],\"layout\":${p?.spec?.id?.let { "\"$it\"" }},\"image\":${p?.spec?.image?.let { "\"$it\"" }},\"cells\":[")
        p?.cells?.forEachIndexed { i, c ->
            if (i > 0) sb.append(',')
            sb.append("[${c.src.x},${c.src.y},${c.src.width},${c.src.height},${c.dst.x},${c.dst.y},${c.dst.width},${c.dst.height},${c.tileX},${c.tileY},${p.k}]")
        }
        sb.append("],\"parts\":${p?.spec?.parts?.let { "\"$it\"" }},\"pieces\":[")
        p?.pieces?.forEachIndexed { i, c ->
            if (i > 0) sb.append(',')
            sb.append("[${c.src.x},${c.src.y},${c.src.width},${c.src.height},${c.dst.x},${c.dst.y},${c.dst.width},${c.dst.height},${c.alt?.let { "\"$it\"" }}]")
        }
        sb.append("],\"glasses\":[")
        p?.glasses?.forEachIndexed { i, g -> if (i > 0) sb.append(','); sb.append("[\"${g.screen}\",${g.rect.x},${g.rect.y},${g.rect.width},${g.rect.height},${g.scale}]") }
        sb.append("],\"controls\":[")
        p?.controls?.forEachIndexed { i, c -> if (i > 0) sb.append(','); sb.append("[\"${c.spec.id}\",${c.x},${c.y},${c.spec.r},${c.spec.hw},${c.spec.hh},${c.spec.label?.let { "\"$it\"" }},${c.spec.labelDy},${c.spec.labelH},${c.spec.labelDx}]") }
        sb.append("]}")
        File(out).mkdirs(); File(out, "$name.json").writeText(sb.toString())
    }

    @Test
    fun everyLayoutParsesAndItsControlsAreInFixedSlices() {
        assertEquals(89 + N3DS_LAYOUTS, pack.layouts.size)   // 6 one-screen layouts, each with its gb, gba, arcade, fds, dc, n64, gc and Wii Classic twins, dc / n64 both-shown on the tall boards (fold, landscape tall), gc's on the fold board (its tall landscape always both; the Wii's fold board and tall landscape always both); the Wii Nunchuk's, sideways and upright Remote's in every family (the Nunchuk's fold board and tall landscape always both)
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
            assertEquals(6, ls.size, "$v")
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
            assertEquals("flex_${if (v == SkinVariant.SINGLE) "single" else v.name.lowercase()}_fold", p.spec.id, id)
            val panel = p.glasses.single { it.screen == GlassScreen.PANEL }
            assertEquals("panel", panel.id, id)
            val r = panel.rect
            val c = p.controls.associateBy { it.spec.id }
            val menu = c.getValue("MENU")
            assertTrue(r.y > hinge && r.y + r.height < menu.y - menu.spec.r, "$id panel $r hinge $hinge menu ${menu.y}")
            for (ctl in p.controls) {
                val hw = if (ctl.spec.r > 0f) ctl.spec.r else ctl.spec.hw
                val hh = if (ctl.spec.r > 0f) ctl.spec.r else ctl.spec.hh
                val apart = ctl.x + hw <= r.x || r.x + r.width <= ctl.x - hw || ctl.y + hh <= r.y || r.y + r.height <= ctl.y - hh
                assertTrue(apart, "$id ${ctl.spec.id} (${ctl.x}, ${ctl.y}) on the panel $r")
            }
            val pic = p.glass(GlassScreen.PICTURE)!!.rect
            assertTrue(pic.y + pic.height <= hinge - 62f, "$id picture $pic")
            assertTrue(c.keys.none { it.startsWith("TOUCH_") || it == "MIC" || it == "SWAP" }, "$id ${c.keys}")
            assertTrue(p.pieces.none { it.strip } && p.spec.frame?.strip == null, "$id strip")
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
        assertEquals("flex_ds_fold", ds.spec.id)
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
                // the fold board (user): DISK and MENU together under the d-pad, the pair centred on its column, clear of the panel
                val dp = p.controls.single { it.spec.id == "DPAD" }
                assertEquals(dp.x, (disk.x + menu.x) / 2, 1f, name); assertTrue(disk.y > dp.y, name)
                assertEquals(menu.y, disk.y, 1f, name); assertTrue(disk.x < menu.x, name)
                val r = p.glasses.single { it.screen == GlassScreen.PANEL }.rect
                for (c in p.controls) assertTrue(apart(box(c), floatArrayOf(r.x, r.y, r.x + r.width, r.y + r.height)), "$name ${c.spec.id} on the panel")
            } else {
                assertEquals(menu.y, disk.y, 1f, name); assertTrue(disk.x < menu.x, name)
            }
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
        val roomy = setOf("tablet", "tablet_land")
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
            // B up left of A; Z nearer the thumb than L (outside it, or under it)
            assertTrue(c("B").x < c("A").x && c("B").y < c("A").y, "$name B up left of A")
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
        // the same pieces and places as the sideways skin (user); the diamond's 1 2 B A and the shoulder's B on X Y B A, B
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
            assertEquals(c("MENU").y, c("HOME").y, 1f, "$name HOME beside MENU"); assertTrue(c("HOME").x > c("MENU").x, "$name HOME right of MENU")
            val small = p.spec.family == SkinFamily.STACKED || (p.spec.family == SkinFamily.FLEX && !p.spec.id.contains("fold"))
            assertEquals(if (small) listOf("−", "+") else listOf("SELECT", "START"), listOf(c("SELECT").spec.label, c("START").spec.label), name)
            if (name in roomy) {   // the original controller's order (user): the d-pad and the face on top, both full sticks under them
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
            // the stick smaller only where it shows with the d-pad (user); swapped with it, full size
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
        // both stick and d-pad only with real extra height (tablets); the Fold's inner screen swaps them like a phone
        val roomy = setOf("tablet", "tablet_land")
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
            if (name == "tablet") {   // the stack and the face centred on the bottom screen, L / R level (user)
                val r = p.glasses.single { it.screen == GlassScreen.PANEL }.rect
                val mid = r.y + r.height / 2f
                assertEquals(mid, (c("STICK").y + c("DPAD").y) / 2f, 20f * p.k * 3, "$name stack")
                assertEquals(mid, (c("X").y + c("B").y) / 2f, 20f * p.k * 3, "$name face")
                assertEquals(c("L2").y, c("R2").y, 1f, "$name L R")
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
                for (x in p.controls) assertTrue(apart(box(x), floatArrayOf(r.x, r.y, r.x + r.width, r.y + r.height)), "$name ${x.spec.id} on the panel")
            }
        }
    }

    @Test
    fun n3dsLayoutsInEveryFamily() {
        // the DS's layouts with the Circle Pad (Dreamcast's stick: both shown on tablets, else swapped with the d-pad), a
        // C-stick nub above the face, ZL / ZR beside L / R; MIC SWAP MENU where the DS has them (phone landscape: SWAP
        // joins SELECT START on the right, the switch cap goes left, user)
        val roomy = setOf("tablet", "tablet_land", "inner", "inner_half", "inner_land")   // the fold board and tall landscape: both always (user)
        for ((name, d) in consoleDevices + ("tablet_land" to Triple(2304, 1440, null))) {
            val tablet = name.startsWith("tablet")
            fun at(v: SkinVariant) = assertNotNull(SkinChoice.place(pack, d.first, d.second, if (tablet) 280 else 420, tablet, true, null,
                d.third, foldable = name.startsWith("inner"), variant = v), "$v $name")
            val ds = at(SkinVariant.DS)
            val p = at(SkinVariant.N3DS)
            dump("3ds_$name", d.first, d.second, p)
            dump("dsv_$name", d.first, d.second, ds)
            run {   // the DS's small caps as the 3DS's (user: match the 3DS)
                fun k(id: String) = ds.controls.first { it.spec.id == id }
                fun row(vararg ids: String) { for (i in 1 until ids.size) {
                    assertEquals(k(ids[0]).y, k(ids[i]).y, 1f, "ds $name ${ids[i]} in the row"); assertTrue(k(ids[i]).x > k(ids[i - 1]).x, "ds $name ${ids.toList()}") } }
                when {
                    name == "flip" -> row("MIC", "SWAP", "MENU")
                    name in setOf("inner", "inner_half", "tablet") -> row("MIC", "SWAP", "MENU", "START", "SELECT")
                    name == "cover" -> row("MIC", "MENU", "SWAP")
                    else -> {   // landscape: SELECT START over SWAP on the right, MIC MENU side by side on the left
                        row("MIC", "MENU"); row("SELECT", "START")
                        assertEquals(k("SELECT").x, k("SWAP").x, 1f, "ds $name SWAP under SELECT"); assertTrue(k("SWAP").y > k("SELECT").y, "ds $name")
                        assertTrue(k("MIC").x < d.first / 2f && k("SWAP").x > d.first / 2f, "ds $name sides")
                    }
                }
            }
            assertEquals(SkinVariant.N3DS, p.spec.variant, name)
            assertEquals(ds.spec.family, p.spec.family, name)
            assertEquals(ds.glasses.map { it.screen }, p.glasses.map { it.screen }, name)
            val dsIds = ds.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            val ids = p.controls.map { it.spec.id }.filterNot { it.startsWith("TOUCH_") }.toSet()
            assertEquals(dsIds + setOf("STICK", "CSTICK", "L2", "R2", "SWAP") + (if (name in roomy) emptySet() else setOf("STICK_SWAP")), ids, name)   // SWAP on the phone's too (user)
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
            assertTrue(c("CSTICK").y < c("X").y, "$name nub above X")
            if (foldBoard || tall) assertEquals(c("Y").x, c("CSTICK").x, 20f * p.k, "$name nub over Y's column, as the console (user)")
            else assertTrue(c("CSTICK").x > c("X").x && c("CSTICK").y < c("A").y - c("A").spec.r, "$name nub up-right of X, above A")
            fun row(vararg want: String) {
                val cs = want.map(::c)
                for (x in cs) assertEquals(cs[0].y, x.y, 1f, "$name ${x.spec.id} in the row")
                for (i in 1 until cs.size) assertTrue(cs[i].x > cs[i - 1].x, "$name ${want.toList()} in order")
            }
            if (name == "flip") {   // one group of four centred at the bottom (user)
                row("STICK_SWAP", "MIC", "SWAP", "MENU")
                assertEquals(d.first / 2f, (c("STICK_SWAP").x + c("MENU").x) / 2f, 30f * p.k, "$name group centred")
            }
            if (foldBoard) {   // one row under the bottom screen (user)
                row("MIC", "MENU", "SWAP")
                val t = p.glasses.single { it.screen == GlassScreen.TOUCH }.rect
                assertTrue(c("MENU").y > t.y + t.height, "$name row under the bottom screen")
                assertTrue(c("START").y > c("B").y && c("SELECT").y > c("B").y, "$name SELECT START under the face")
                assertEquals(c("START").x, c("SELECT").x, 1f, "$name START over SELECT, one column (user: as the console's)")
                assertEquals(c("Y").x, c("START").x, 1f, "$name the column on Y's (user)")
                for (id in listOf("SELECT", "START"))   // a clear gap under B's cap (user: they overlapped)
                    assertTrue(c(id).y - c("B").y > (c(id).spec.r + c("B").spec.r + 30f) * p.k, "$name $id clear of B")
                assertTrue(c("CSTICK").y - c("R").y > (c("CSTICK").spec.r * 1.25f + c("R").spec.hh + 30f) * p.k, "$name nub clear of ZR / R (user)")
                if (name == "tablet") assertTrue(t.width / p.k > 1000f, "$name bottom screen grown: ${t.width / p.k}")
            }
            assertTrue(c("CSTICK").spec.r < c("STICK").spec.r / 2f, "$name nub small")
            if (name in roomy) assertTrue(c("STICK").y < c("DPAD").y, "$name Circle Pad above the d-pad")
            else {
                assertEquals(c("DPAD").x, c("STICK").x, 0.5f, "$name stick x")
                assertEquals(c("DPAD").y, c("STICK").y, 0.5f, "$name stick y")
            }
            if (p.spec.family == SkinFamily.LARGE || p.spec.id.startsWith("flex_3ds") && !p.spec.id.contains("fold")) {   // above L / R (user: the cover)
                assertEquals(c("L").x, c("L2").x, 1f, "$name ZL over L"); assertTrue(c("L2").y < c("L").y, "$name ZL above L")
                assertEquals(c("R").x, c("R2").x, 1f, "$name ZR over R"); assertTrue(c("R2").y < c("R").y, "$name ZR above R")
            } else { assertEquals(c("L").y, c("L2").y, 1f, "$name ZL beside L"); assertEquals(c("R").y, c("R2").y, 1f, "$name ZR beside R") }
            if (name == "cover") row("STICK_SWAP", "MIC", "MENU", "SWAP")   // one group of four (user)
            if (tall) {   // MIC MENU side by side on the left, level with SWAP; SELECT START over SWAP on the right (user)
                row("SELECT", "START"); row("MIC", "MENU")
                assertEquals(c("SELECT").x, c("SWAP").x, 1f, name); assertTrue(c("SWAP").y > c("SELECT").y, name)
                assertEquals(c("MIC").y, c("SWAP").y, 1f, "$name MIC MENU level with SWAP")
                assertTrue(c("MIC").y - c("DPAD").y > (c("MIC").spec.r + c("DPAD").spec.r + 60f) * p.k, "$name MIC MENU clear of the d-pad")
            }
            if (name == "phone_land") {   // SWAP on the right with SELECT START, the switch cap on the left (user)
                assertTrue(c("SWAP").x > d.first / 2f, "$name SWAP right")
                assertTrue(c("STICK_SWAP").x < d.first / 2f, "$name switch left")
                // two rows (user): switch MIC over MENU (under MIC); SELECT START over SWAP (under SELECT)
                row("STICK_SWAP", "MIC"); row("SELECT", "START")
                assertEquals(c("MIC").x, c("MENU").x, 1f, "$name MENU under MIC"); assertTrue(c("MENU").y > c("MIC").y, "$name MENU under MIC")
                assertEquals(c("SELECT").x, c("SWAP").x, 1f, "$name SWAP under SELECT"); assertTrue(c("SWAP").y > c("SELECT").y, "$name SWAP under SELECT")
                assertTrue(c("MENU").y - c("MIC").y >= 139f * p.k, "$name room between the rows")
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
        // the inner screen: the fold board, split at its hinge flat and half-folded alike (folding changes nothing)
        for ((suffix, hinge) in listOf("flat" to null, "half" to 1224f)) {
            val ds = assertNotNull(place("fold_inner_$suffix", 1848, 2448, tablet = true, hinge = hinge, foldable = true))
            assertEquals("flex_ds_fold", ds.spec.id)
            assertHalfFolded(ds, 1224f)
            assertEquals(3.5f, ds.glass(GlassScreen.TOUCH)!!.scale, 0.01f)   // as the old tablet render: touch 3.5x
            // the top screen: the frame filling the half above the hinge (the DS top screen centred in it), as the menu's,
            // with shell round it as a clamshell's: clear of the hinge (its top ~62 px above the fold) by the shell
            val top = ds.glass(GlassScreen.TOP)!!.rect
            assertTrue(top.width > 1600f && top.height > 1000f, "top $top")
            assertTrue(top.y + top.height <= 1224f - 62f - 32f, "top $top: on the hinge")
            assertTrue(top.x >= 32f && top.x + top.width <= 1848f - 32f, "top $top: edge to edge")
            val gba = assertNotNull(place("fold_inner_${suffix}_gba", 1848, 2448, tablet = true, ds = false, picture = 240f to 160f, hinge = hinge, foldable = true))
            assertEquals("flex_single_fold", gba.spec.id)
            assertHalfFolded(gba, 1224f)
        }
        // the touch screen clear of SELECT START and the clusters
        val p = assertNotNull(place("fold_inner_ds", 1848, 2448, tablet = true, foldable = true))
        val t = p.glass(GlassScreen.TOUCH)!!.rect
        val c = p.controls.associateBy { it.spec.id }
        assertTrue(c.getValue("MENU").y - c.getValue("MENU").spec.r > t.y + t.height, "menu ${c.getValue("MENU").y} touch $t")
        // MIC SWAP MENU START SELECT in one row under the bottom screen (user)
        val five = listOf("MIC", "SWAP", "MENU", "START", "SELECT").map(c::getValue)
        for (i in 1 until five.size) { assertEquals(five[0].y, five[i].y, 1f, "one row"); assertTrue(five[i].x > five[i - 1].x, "in order") }
        assertTrue(c.getValue("DPAD").x + c.getValue("DPAD").spec.r < t.x, "dpad")
        // a tablet that does not fold takes the fold board too (the menu's board: a game never changes it)
        assertEquals("flex_ds_fold", assertNotNull(place("inner_size_tablet", 1848, 2448, tablet = true)).spec.id)
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
    private fun assertHalfFolded(p: SkinPlacement, hinge: Float) {
        val ds = p.glass(GlassScreen.TOP) != null
        assertEquals(if (ds) "flex_ds" else "flex_single", p.spec.id.removeSuffix("_wide").removeSuffix("_fold"))
        val above = assertNotNull(p.glass(if (ds) GlassScreen.TOP else GlassScreen.PICTURE)).rect
        assertTrue(above.y + above.height < hinge, "${p.spec.id} $above")
        if (ds) assertTrue(p.glass(GlassScreen.TOUCH)!!.rect.y > hinge)
        val below = p.controls.filter { it.spec.id in setOf("DPAD", "A", "L", "R", "START", "MENU") + if (ds) setOf("MIC", "SWAP", "TOUCH_1") else emptySet() }
        assertTrue(below.all { it.y > hinge }, "${p.spec.id} ${below.map { it.spec.id to it.y }}")
        // the strip, but on the fold board's one-screen layouts: their panel's Shortcuts do its job (no strip)
        val panel = p.glass(GlassScreen.PANEL)
        if (panel != null) assertTrue(panel.rect.y > hinge && p.controls.none { it.spec.id == "TOUCH_4" }, p.spec.id)
        else assertTrue(p.controls.any { it.spec.id == "TOUCH_4" })
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

    private fun twoScreens(name: String, p: SkinPlacement?, w: Int, h: Int, hinge: Float) {
        val placed = assertNotNull(p, name)
        dump(name, w, h, placed)
        // the board's own hinge is drawn where the fold is: its middle row at [hinge]
        val hc = placed.cells.filter { it.src.y in 1163f..1165f }
        assertTrue(hc.isNotEmpty() && hc.all { kotlin.math.abs(it.dst.y + it.dst.height / 2f - hinge) < 2f }, "$name hinge cells ${hc.map { it.dst }}")
        assertEquals("stacked_menu_fold", placed.spec.id, name)
        val top = placed.glasses.single { it.id == "menutop" }.rect
        val menu = placed.glasses.single { it.id == "menu" }.rect
        assertTrue(top.y + top.height < hinge && hinge < menu.y, "$name top $top menu $menu hinge $hinge")
        val c = placed.controls.associateBy { it.spec.id }
        assertTrue("MIC" !in c && "SWAP" !in c, name)
        assertTrue(c.getValue("MENU").y - c.getValue("MENU").spec.r > menu.y + menu.height, "$name menu glass $menu")
    }

    @Test
    fun foldInnerMenuIsTwoScreens() {
        twoScreens("menu_inner_two", SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true), 1848, 2448, 1224f)
        twoScreens("menu_inner_half", SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true, hingeY = 1236f), 1848, 2448, 1236f)
    }

    @Test
    fun tabletMenuIsTwoScreens() {
        twoScreens("menu_tab_two", SkinChoice.placeMenu(pack, 1440, 2304, 280, tablet = true), 1440, 2304, 1152f)
        // the tablet's wider room: the menu's touch screen grows (its shape kept); the Fold's keeps its own size
        val tab = assertNotNull(SkinChoice.placeMenu(pack, 1440, 2304, 280, tablet = true))
        val tg = tab.glasses.single { it.id == "menu" }.rect
        assertTrue(tg.width / tab.k > 1100f, "tablet menu glass $tg")
        assertEquals(944f / 740f, tg.width / tg.height, 0.02f)
        val fold = assertNotNull(SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true))
        assertEquals(944f, fold.glasses.single { it.id == "menu" }.rect.width, 1f)
    }

    @Test
    fun flipAndPhonesKeepOneScreenMenu() {
        assertEquals("stacked_menu", SkinChoice.placeMenu(pack, 1080, 2520, 420, foldable = true)?.spec?.id)
        assertEquals("stacked_menu", SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true, twoScreens = false)?.spec?.id)
        assertTrue(SkinChoice.placeMenu(pack, 2448, 1848, 420, tablet = true)?.spec?.id?.startsWith("grip_menu") == true)
    }

    @Test
    fun tabletGamesTakeTheFoldBoard() {
        assertEquals("flex_ds_fold", place("tab_two_ds", 1440, 2304, dpi = 280, tablet = true)?.spec?.id)
        assertEquals("flex_single_fold", place("tab_two_gba", 1440, 2304, dpi = 280, tablet = true, ds = false, picture = 240f to 160f)?.spec?.id)
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
            // the buttons keep their size and their side: L and the d-pad left, R and the face right, the small ones centred
            val c = p.controls.associateBy { it.spec.id }
            assertEquals((240f + 40f) * p.k, c.getValue("DPAD").x, 0.5f, name)
            assertEquals(w - (1080 - 942 + 40) * p.k, c.getValue("A").x, 0.5f, name)
            assertEquals(w / 2f, c.getValue("MENU").x, 0.5f, name)
            assertEquals((148f + 40f) * p.k, c.getValue("L").x, 0.5f, name)
            assertEquals(w - (1080 - 932 + 40) * p.k, c.getValue("R").x, 0.5f, name)
            assertEquals(8 + 1 + 7, p.pieces.size, name)   // the frame (corners and edges), the SELECT START well, then the seven button pieces
        }
        assertNull(SkinChoice.placeMenu(pack, 1080, 1100, 420))   // too short: the drawn pad
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
            assertEquals(if (h / p.k >= 1500f) "grip_menu_tall" else "grip_menu", p.spec.id, name)
            val g = p.glasses.single().rect
            val c = p.controls.associateBy { it.spec.id }
            assertEquals(setOf("DPAD", "A", "B", "X", "Y", "L", "R", "START", "SELECT", "MENU"), c.keys)
            // the screen between the columns, centred, the frame its margin from the top and bottom
            assertEquals(w / 2f, g.x + g.width / 2f, 3f * p.k, name)   // the frame's border: 28 left, 32 right
            assertTrue(c.values.all { it.x < g.x || it.x > g.x + g.width }, "$name glass $g")
            assertTrue(g.y >= 72f * p.k && g.y + g.height <= h - 72f * p.k, "$name glass $g")
            // the pads in their columns at the standard (portrait) size, one group at the middle of the height
            val spec = p.spec.controls.associateBy { it.id }
            assertEquals(72f * p.k, c.getValue("A").spec.r, 0.5f, name)
            assertEquals(spec.getValue("DPAD").x * p.k, c.getValue("DPAD").x, 0.5f, name)
            assertEquals(w - (p.spec.width - spec.getValue("X").x) * p.k, c.getValue("X").x, 0.5f, name)
            assertEquals(h / 2f - (p.spec.height / 2f - p.spec.controls.first { it.id == "DPAD" }.y) * p.k, c.getValue("DPAD").y, 0.5f, name)
            assertEquals(c.getValue("DPAD").y - (p.spec.controls.first { it.id == "DPAD" }.y - p.spec.controls.first { it.id == "L" }.y) * p.k, c.getValue("L").y, 0.5f, name)
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
