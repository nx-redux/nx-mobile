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
            sb.append("[${c.src.x},${c.src.y},${c.src.width},${c.src.height},${c.dst.x},${c.dst.y},${c.dst.width},${c.dst.height}]")
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
        assertEquals(18, pack.layouts.size)
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

    @Test
    fun foldInnerPortraitIsTheFoldBoardFlatAndHalfFolded() {
        // the inner screen: the fold board, split at its hinge flat and half-folded alike (folding changes nothing)
        for ((suffix, hinge) in listOf("flat" to null, "half" to 1224f)) {
            val ds = assertNotNull(place("fold_inner_$suffix", 1848, 2448, tablet = true, hinge = hinge, foldable = true))
            assertEquals("flex_ds_fold", ds.spec.id)
            assertHalfFolded(ds, 1224f)
            assertEquals(3.5f, ds.glass(GlassScreen.TOUCH)!!.scale, 0.01f)   // as the old tablet render: top 5x, touch 3.5x
            assertEquals(5f, ds.glass(GlassScreen.TOP)!!.scale, 0.01f)
            val gba = assertNotNull(place("fold_inner_${suffix}_gba", 1848, 2448, tablet = true, ds = false, picture = 240f to 160f, hinge = hinge, foldable = true))
            assertEquals("flex_single_fold", gba.spec.id)
            assertHalfFolded(gba, 1224f)
        }
        // the touch screen clear of SELECT START and the clusters
        val p = assertNotNull(place("fold_inner_ds", 1848, 2448, tablet = true, foldable = true))
        val t = p.glass(GlassScreen.TOUCH)!!.rect
        val c = p.controls.associateBy { it.spec.id }
        assertTrue(c.getValue("MENU").y - c.getValue("MENU").spec.r > t.y + t.height, "menu ${c.getValue("MENU").y} touch $t")
        assertTrue(c.getValue("MIC").x == c.getValue("SWAP").x && c.getValue("MENU").x > t.x, "mic swap left, menu middle")
        assertTrue(c.getValue("DPAD").x + c.getValue("DPAD").spec.r < t.x, "dpad")
        assertTrue(c.getValue("START").x - c.getValue("START").spec.r > t.x + t.width, "start")
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
        assertTrue(p.controls.any { it.spec.id == "TOUCH_4" })
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
        twoScreens("menu_inner_half", SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true, hingeY = 1300f), 1848, 2448, 1300f)
    }

    @Test
    fun tabletMenuIsTwoScreens() {
        twoScreens("menu_tab_two", SkinChoice.placeMenu(pack, 1440, 2304, 280, tablet = true), 1440, 2304, 1152f)
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
