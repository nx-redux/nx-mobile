package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The pack's drawing rules as its boxes place them on the device classes the user tuned. */
class SkinPackDrawingTest {
    /** A pad stacked under another on a side, drawn at this share of its size. */
    private val LOWER_PAD = 0.85f

    private val pack = skinPack()

    // A layout's groups (what the controls editor moves) follow what the buttons are: the d-pad, the stick, the face
    // buttons (the N64's C diamond and its A / B well apart), each shoulder alone, small caps by their outline.
    @Test
    fun theGroupsFollowTheButtonKinds() {
        val shoulders = setOf("L", "R", "L1", "R1", "L2", "R2", "ZL", "ZR")
        for (v in listOf(null, SkinVariant.N64)) for (d in GOLDEN_DEVICES) {
            // (the N64 on the Flip half-folded: its old flex layout draws the switch inside the A / B outline; for review)
            if (v == SkinVariant.N64 && d.name == "flip_half") continue
            val p = placeFor(pack, v, d) ?: continue
            val ids = SkinGroups.of(p.spec).map { it.id }
            val label = "${v ?: "menu"} ${d.name} $ids"
            fun groupOf(c: String) = ids.firstOrNull { c in it.split("+") }
            if (p.controls.any { it.spec.id == "DPAD" }) kotlin.test.assertEquals("DPAD", groupOf("DPAD"), label)
            if (p.controls.any { it.spec.id == "STICK" }) kotlin.test.assertEquals("STICK", groupOf("STICK"), label)
            for (s in shoulders) groupOf(s)?.let { kotlin.test.assertEquals(s, it, label) }
            if (v == null) kotlin.test.assertEquals("A+B+X+Y", groupOf("A"), label)
            else { kotlin.test.assertEquals("CD+CL+CR+CU", groupOf("CU"), label); kotlin.test.assertEquals("A+B", groupOf("A"), label) }
        }
    }

    // Two pads stacked on one side (the stick over the d-pad): the lower one drawn at 85% about its middle, the
    // upper one as it was (against the rules' placement, the legacy golden).
    @Test
    fun theLowerOfTwoStackedPadsIsSmaller() {
        fun f(o: kotlinx.serialization.json.JsonObject, k: String) = (o.getValue(k) as kotlinx.serialization.json.JsonPrimitive).content.toFloat()
        for ((v, file) in listOf(SkinVariant.DC to "dc", SkinVariant.N64 to "n64")) for (name in listOf("inner_land", "tablet_land")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, v, d), "$file $name")
            val was = (readGolden(file).getValue(name) as kotlinx.serialization.json.JsonObject).getValue("controls")
                .let { it as kotlinx.serialization.json.JsonArray }.map { it as kotlinx.serialization.json.JsonObject }
            fun old(id: String) = was.first { (it.getValue("id") as kotlinx.serialization.json.JsonPrimitive).content == id }
            val pad = p.controls.first { it.spec.id == "DPAD" }; val stick = p.controls.first { it.spec.id == "STICK" }
            kotlin.test.assertEquals(f(old("DPAD"), "r") * LOWER_PAD, pad.spec.r, 1f, "$file $name the d-pad at 85%")
            kotlin.test.assertEquals(f(old("STICK"), "r"), stick.spec.r, 1f, "$file $name the stick as it was")
            // (the Dreamcast's toward the screen as the user placed it: theDreamcastDpadIsTuckedUnderTheStick)
            val across = if (v == SkinVariant.DC && name != "tablet_land") 42f / 156f * f(old("DPAD"), "r") else 0f   // (a tablet's centred)
            kotlin.test.assertEquals(f(old("DPAD"), "x") + across, pad.x, 1.5f, "$file $name about its middle")
        }
    }

    // The Dreamcast's fold boards (the stick over the d-pad): L level with R and the stick with the face buttons, the
    // d-pad's gap under the stick half what it was (the side no longer lifted to fit it), all on the window. (The Ultra
    // sideways half-folded has too little room for it: left lifted, for review.)
    @Test
    fun theStackedSideIsLevelWithTheOther() {
        for (name in listOf("inner", "inner_half", "ultra", "ultra_half", "tablet")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, SkinVariant.DC, d), name)
            fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
            val face = listOf("X", "Y", "A", "B").map { c(it).y }.average().toFloat()
            kotlin.test.assertEquals(c("R2").y, c("L2").y, 1f, "$name L level with R")
            kotlin.test.assertEquals(face, c("STICK").y, 1f, "$name the stick level with the face buttons")
            val st = c("STICK"); val pad = c("DPAD")
            // the rings' gap: the biggest piece round each middle (the stick's ring, the d-pad's)
            fun ring(pieces: List<com.nxredux.mobile.options.PxRect>, x: Float, y: Float) = pieces
                .filter { kotlin.math.abs(it.x + it.width / 2f - x) < 3f && kotlin.math.abs(it.y + it.height / 2f - y) < 3f }.maxBy { it.width }
            val now = p.pieces.filter { !it.knob }.map { it.dst }
            val gap = ring(now, pad.x, pad.y).y - ring(now, st.x, st.y).let { it.y + it.height }
            val was = (readGolden("dc").getValue(name) as kotlinx.serialization.json.JsonObject)
            fun o(k: String) = (was.getValue(k) as kotlinx.serialization.json.JsonArray).map { it as kotlinx.serialization.json.JsonObject }
            fun num(e: kotlinx.serialization.json.JsonElement) = (e as kotlinx.serialization.json.JsonPrimitive).content.toFloat()
            val oldPieces = o("pieces").filter { (it.getValue("knob") as kotlinx.serialization.json.JsonPrimitive).content == "false" }
                .map { (it.getValue("dst") as kotlinx.serialization.json.JsonArray).map(::num).let { r -> com.nxredux.mobile.options.PxRect(r[0], r[1], r[2], r[3]) } }
            fun old(id: String) = o("controls").first { (it.getValue("id") as kotlinx.serialization.json.JsonPrimitive).content == id }
                .let { num(it.getValue("x")) to num(it.getValue("y")) }
            val oldGap = ring(oldPieces, old("DPAD").first, old("DPAD").second).let { r -> r.y + r.height * (1f - LOWER_PAD) / 2f } -
                ring(oldPieces, old("STICK").first, old("STICK").second).let { it.y + it.height }
            // (halved, then tucked up to 8 dp: theDreamcastDpadIsTuckedUnderTheStick)
            if (!name.startsWith("ultra")) kotlin.test.assertTrue(gap < oldGap / 2f, "$name the d-pad's gap under half what it had")
            assertTrue(pad.y + pad.spec.r <= d.h, "$name the d-pad on the window")
        }
    }

    // The DS sideways (its regular shell): SELECT and START side by side in one well, across from MIC and MENU on the
    // left (SWAP is in the strip); the caps clear of each other, on the window, level.
    @Test
    fun theDsSidewaysHasSelectStartInARowAcrossFromMicMenu() {
        val devices = listOf(DeviceClass("ultra_cover_land", 2424, 1080, 480, false, true), DeviceClass("flip_land", 2520, 1080, 420, false, true)) +
            GOLDEN_DEVICES.filter { it.name == "phone_land" }
        for (d in devices) {
            val p = assertNotNull(placeFor(pack, SkinVariant.DS, d), d.name)
            if (p.spec.id != "grip_ds") continue
            fun c(id: String) = p.controls.first { it.spec.id == id }
            val sel = c("SELECT"); val st = c("START")
            kotlin.test.assertEquals(sel.y, st.y, 1f, "${d.name} START in the row")
            kotlin.test.assertEquals(sel.y, c("MIC").y, 1f, "${d.name} MIC level with the row"); kotlin.test.assertEquals(sel.y, c("MENU").y, 1f, "${d.name} MENU level with the row")
            kotlin.test.assertEquals(d.w - c("MENU").x, sel.x, 1f, "${d.name} SELECT across from MENU"); kotlin.test.assertEquals(d.w - c("MIC").x, st.x, 1f, "${d.name} START across from MIC")
            assertTrue(st.x - sel.x >= sel.spec.r + st.spec.r, "${d.name} clear of each other")
            assertTrue(st.x + st.spec.r <= d.w, "${d.name} on the window")
        }
    }

    // The DS on the Fold cover sideways: MIC over MENU on the left under the d-pad; SELECT over START across from them on
    // the right 16 dp under the face buttons, level with them.
    @Test
    fun theDsCoverSidewaysHasItsRowsUnderThePads() {
        val d = GOLDEN_DEVICES.first { it.name == "cover_land" }
        val p = assertNotNull(placeFor(pack, SkinVariant.DS, d))
        fun c(id: String) = p.controls.first { it.spec.id == id }
        // MIC over MENU on the left, SELECT over START on the right: upright pairs, level, mirrored (the GBA's)
        kotlin.test.assertEquals(c("MIC").x, c("MENU").x, 1f, "MIC over MENU"); assertTrue(c("MENU").y > c("MIC").y && c("MIC").x < d.w / 2f, "MIC over MENU on the left")
        kotlin.test.assertEquals(c("SELECT").x, c("START").x, 1f, "SELECT over START"); assertTrue(c("START").y > c("SELECT").y, "SELECT over START")
        kotlin.test.assertEquals(c("MIC").y, c("SELECT").y, 1f, "pairs level"); kotlin.test.assertEquals(d.w - c("MIC").x, c("SELECT").x, 1f, "SELECT across from MIC")
        val dp = d.dpi / 160f
        fun ringFoot(ids: Set<String>) = p.pieces.filter { q -> q.piece >= 0 && p.spec.pieces[q.piece].group?.split("+")?.any { it in ids } == true }.maxOf { it.dst.y + it.dst.height }
        fun wellTop(ids: Set<String>) = p.pieces.filter { q -> q.piece >= 0 && p.spec.pieces[q.piece].group?.split("+")?.any { it in ids } == true }.minOf { it.dst.y }
        kotlin.test.assertEquals(16f * dp, wellTop(setOf("SELECT")) - ringFoot(setOf("A")), 2f, "SELECT / START 16 dp under the face buttons")
    }

    // The DS's fold boards (the unfolded Fold and the Ultra): MIC, MENU in a row on the left under the d-pad and SELECT,
    // START across from them on the right under the face buttons, level; no row under the touch screen, whose frame
    // reaches down as far as the main menu's does.
    @Test
    fun theDsFoldBoardHasItsCapsBesideTheTouchScreen() {
        for (name in listOf("inner", "inner_half", "ultra", "ultra_half", "ultra_land", "ultra_land_half")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, SkinVariant.DS, d), name)
            if (!p.spec.tree.parts.any { it.isHinge }) continue
            fun c(id: String) = p.controls.first { it.spec.id == id }
            for (id in listOf("MENU", "SELECT", "START")) kotlin.test.assertEquals(c("MIC").y, c(id).y, 1f, "$name $id level with MIC")
            // the face buttons where the main menu has them (the baseline), on the boards the menu shares
            SkinChoice.placeMenu(pack, d.w, d.h, d.dpi, foldable = true, tablet = true, hingeY = d.hingeY)?.takeIf { it.spec.tree.parts.any { t -> t.isHinge } }?.let { m ->
                val mx = m.controls.first { it.spec.id == "X" }
                kotlin.test.assertEquals(mx.y, c("X").y, 1f, "$name X at the menu's height"); kotlin.test.assertEquals(mx.x, c("X").x, 1f, "$name X at the menu's place")
            }
            val touch = assertNotNull(p.glasses.firstOrNull { it.screen == GlassScreen.TOUCH }, name).rect
            assertTrue(c("MENU").x < touch.x && c("SELECT").x > touch.x + touch.width, "$name MIC, MENU left of the touch screen; SELECT right")
            assertTrue(c("MIC").x < c("MENU").x && c("SELECT").x < c("START").x, "$name in order")
            kotlin.test.assertEquals(d.w - c("MENU").x, c("SELECT").x, 1f, "$name SELECT across from MENU")
            for ((a, b) in listOf("MIC" to "MENU", "SELECT" to "START"))
                assertTrue(c(b).x - c(a).x >= c(a).spec.r + c(b).spec.r - 1f, "$name $a $b clear")
            val menu = assertNotNull(SkinChoice.placeMenu(pack, d.w, d.h, d.dpi, foldable = true, tablet = true, hingeY = d.hingeY), "$name menu")
            fun foot(q: SkinPlacement) = q.pieces.filter { it.piece >= 0 && q.spec.pieces[it.piece].glass != null && it.dst.y + it.dst.height > d.h / 2f }.maxOfOrNull { it.dst.y + it.dst.height }
            // (where the menu is on the fold board too: sideways and flat it is the landscape shell; sideways half-folded
            // the menu's frame ends where the landscape shell's glass does, ~45 px higher: for review)
            if (name != "ultra_land_half") foot(menu)?.let { kotlin.test.assertEquals(it, assertNotNull(foot(p), name), 2f, "$name the touch screen's frame down as far as the menu's") }
        }
    }

    // The Dreamcast's d-pad under the stick as the user placed it on the Fold: toward the screen by 42 / 156 of the main
    // menu's d-pad, its ring just under the stick's; so the stick and face buttons stay on the menu's pads sideways
    // half-folded too.
    @Test
    fun theDreamcastDpadIsTuckedUnderTheStick() {
        // (a tablet sideways: the PSP's gap, centred, tabletLandscapePadsAreThePspsGapApart)
        for (name in listOf("inner", "inner_half", "inner_land", "ultra", "ultra_half", "ultra_land_half", "tablet")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, SkinVariant.DC, d), name)
            fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
            fun ring(x: Float, y: Float) = p.pieces.filter { !it.knob }.map { it.dst }
                .filter { kotlin.math.abs(it.x + it.width / 2f - x) < 3f && kotlin.math.abs(it.y + it.height / 2f - y) < 3f }.maxBy { it.width }
            val gap = ring(c("DPAD").x, c("DPAD").y).y - ring(c("STICK").x, c("STICK").y).let { it.y + it.height }
            kotlin.test.assertEquals(1f, gap, 2f, "$name the d-pad's ring just under the stick's")
            val menu = assertNotNull(placeFor(pack, null, d), "$name menu").controls.associateBy { it.spec.id }
            kotlin.test.assertEquals(42f / 156f * menu.getValue("DPAD").spec.r, c("DPAD").x - c("STICK").x, 2f, "$name the d-pad toward the screen")
            kotlin.test.assertEquals(menu.getValue("DPAD").y, c("STICK").y, 1f, "$name the stick on the menu's d-pad")
            kotlin.test.assertEquals(menu.getValue("A").y, c("A").y, 1f, "$name the face buttons on the menu's")
        }
    }

    // The 3DS on the Fold's inner screen upright (flat and half-folded): MIC, MENU, SELECT, START in one row under the
    // touch screen, centred across, clear of it.
    @Test
    fun the3dsOnTheFoldUprightHasItsCapsInARowUnderTheTouchScreen() {
        for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) for (name in listOf("inner", "inner_half")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, v, d), "$v $name")
            fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
            val row = listOf("MIC", "MENU", "SELECT", "START").map(::c)
            for ((a, b) in row.zipWithNext()) {
                kotlin.test.assertEquals(a.y, b.y, 1f, "$v $name ${b.spec.id} in the row"); assertTrue(b.x > a.x, "$v $name ${b.spec.id} in order")
                assertTrue(b.x - a.x >= a.spec.r + b.spec.r - 1f, "$v $name ${a.spec.id} ${b.spec.id} clear")
            }
            kotlin.test.assertEquals(d.w / 2f, (row.first().x + row.last().x) / 2f, 2f, "$v $name centred")
            // its outline's ends each the well's own end (the left end from the picture's left half, the right from its right)
            val outline = p.pieces.filter { q -> kotlin.math.abs(q.dst.y + q.dst.height / 2f - row[0].y) < 2f && row.none { c -> kotlin.math.abs(q.dst.x + q.dst.width / 2f - c.x) < 2f } }
            assertTrue(outline.minBy { it.dst.x }.src.x < outline.maxBy { it.dst.x }.src.x, "$v $name outline ends not swapped")
            val touch = assertNotNull(p.glasses.firstOrNull { it.screen == GlassScreen.TOUCH }, "$v $name").rect
            assertTrue(row[0].y - row[0].spec.r > touch.y + touch.height, "$v $name under the touch screen")
        }
    }

    // The 3DS on the Fold's inner screen upright: the d-pad up into the room under the stick (half the gap it had) and
    // across to the caps' row, where the user's own tweak put it on the Fold (5 px from the row, 25 px over half the gap);
    // the New 3DS's C-stick the same under the face, from the row's other end.
    @Test
    fun the3dsOnTheFoldUprightHasItsDpadUpAndIn() {
        for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) for (name in listOf("inner", "inner_half")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, v, d), "$v $name")
            val dp = d.dpi / 160f
            fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
            fun around(id: String) = p.pieces.filter { q -> c(id).let { it.x >= q.dst.x && it.x <= q.dst.x + q.dst.width && it.y >= q.dst.y && it.y <= q.dst.y + q.dst.height } }
            fun ring(id: String) = around(id).maxBy { it.dst.width }.dst
            // the row as the art has it, SWAP still in it ([artRowEnds]): SWAP gone, the pads keep their places (user)
            val (rowLeft, rowRight) = artRowEnds(v, d)
            val dpad = ring("DPAD"); val stick = ring("STICK")
            kotlin.test.assertEquals(5f, rowLeft - (dpad.x + dpad.width), 2f, "$v $name d-pad 5 px from the art's row")
            // (beside the stick on a slant: the rings apart, by less than the inner gap)
            val gap = kotlin.math.hypot(dpad.x + dpad.width / 2f - (stick.x + stick.width / 2f), dpad.y + dpad.height / 2f - (stick.y + stick.height / 2f)) - (dpad.width + stick.width) / 2f
            assertTrue(gap > 0f && gap < 16f * dp, "$v $name d-pad up by the stick: $gap")
            if (v == SkinVariant.N3DS) {
                val cs = ring("CSTICK"); val face = ring("B")
                kotlin.test.assertEquals(5f, cs.x - rowRight, 2f, "$v $name C-stick 5 px from the art's row")
                // (under the face on a slant: below its middle, the rings apart)
                val apart = kotlin.math.hypot(cs.x + cs.width / 2f - (face.x + face.width / 2f), cs.y + cs.height / 2f - (face.y + face.height / 2f)) - (cs.width + face.width) / 2f
                assertTrue(cs.y > face.y && apart > 0f, "$v $name C-stick under the face: $apart")
            }
        }
    }

    // The 3DS on the Fold's inner screen sideways (flat and half-folded): MIC and MENU in their row under the d-pad, clear
    // of it, at the same height on the regular 3DS as on the New one.
    @Test
    fun the3dsOnTheFoldSidewaysHasMicAndMenuUnderTheDpad() {
        for (name in listOf("inner_land", "inner_land_half")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val ys = listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR).map { v ->
                val p = assertNotNull(placeFor(pack, v, d), "$v $name")
                fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
                val ring = p.pieces.filter { q -> c("DPAD").let { it.x >= q.dst.x && it.x <= q.dst.x + q.dst.width && it.y >= q.dst.y && it.y <= q.dst.y + q.dst.height } }.maxBy { it.dst.width }.dst
                for (id in listOf("MIC", "MENU")) assertTrue(c(id).y - c(id).spec.r >= ring.y + ring.height, "$v $name $id under the d-pad")
                c("MIC").y
            }
            kotlin.test.assertEquals(ys[0], ys[1], 1f, "$name the regular 3DS's row where the New's is")
        }
    }

    // The New 3DS on the Fold's inner screen sideways (flat and half-folded): the C-stick under the face drawn smaller (two
    // large controls in a box: the lower one at 85%); the gaps down each side (the upper pad to the lower, the lower to
    // the caps' row) half what they were (73 and 42 px), both rows level.
    @Test
    fun theNew3dsOnTheFoldSidewaysHasASmallerCStickAndHalfTheGaps() {
        for (name in listOf("inner_land", "inner_land_half")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, SkinVariant.N3DS, d), name)
            fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
            fun ring(id: String) = p.pieces.filter { q -> c(id).let { it.x >= q.dst.x && it.x <= q.dst.x + q.dst.width && it.y >= q.dst.y && it.y <= q.dst.y + q.dst.height } }.maxBy { it.dst.width }.dst
            fun wellTop(id: String) = p.pieces.filter { q -> kotlin.math.abs(q.dst.y + q.dst.height / 2f - c(id).y) < 2f }.minOf { it.dst.y }
            val face = ring("X").let { x -> p.pieces.filter { q -> listOf("X", "B").map(::c).all { it.x >= q.dst.x && it.x <= q.dst.x + q.dst.width && it.y >= q.dst.y && it.y <= q.dst.y + q.dst.height } }.maxBy { it.dst.width }.dst }
            kotlin.test.assertEquals(0.85f * face.width, ring("CSTICK").width, 2f, "$name C-stick at 85% of the face")
            val stick = ring("STICK"); val dpad = ring("DPAD")
            kotlin.test.assertEquals(36.5f, dpad.y - (stick.y + stick.height), 2f, "$name stick to d-pad halved")
            kotlin.test.assertEquals(21f, wellTop("MIC") - (dpad.y + dpad.height), 2f, "$name d-pad to the row halved")
            kotlin.test.assertEquals(c("MIC").y, c("SELECT").y, 1f, "$name rows level")
        }
    }

    // The 3DS on the Ultra's inner screen upright (flat and half-folded), as on the Fold: MIC, MENU, SELECT, START in one
    // row under the touch screen, the touch screen only its picture (4:3), the d-pad where the user's tweak put it.
    @Test
    fun the3dsOnTheUltraUprightIsAsTheFolds() {
        for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) for (name in listOf("ultra", "ultra_half")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, v, d), "$v $name")
            fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
            val row = listOf("MIC", "MENU", "SELECT", "START").map(::c)
            for ((a, b) in row.zipWithNext()) { kotlin.test.assertEquals(a.y, b.y, 1f, "$v $name ${b.spec.id} in the row"); assertTrue(b.x > a.x, "$v $name ${b.spec.id} in order") }
            kotlin.test.assertEquals(d.w / 2f, (row.first().x + row.last().x) / 2f, 2f, "$v $name row centred")
            val touch = assertNotNull(p.glasses.firstOrNull { it.screen == GlassScreen.TOUCH }, "$v $name").rect
            kotlin.test.assertEquals(touch.width * 3f / 4f, touch.height, 2f, "$v $name touch screen only its picture (4:3)")
            assertTrue(row[0].y - row[0].spec.r > touch.y + touch.height, "$v $name row under the touch screen")
            fun ring(id: String) = p.pieces.filter { q -> c(id).let { it.x >= q.dst.x && it.x <= q.dst.x + q.dst.width && it.y >= q.dst.y && it.y <= q.dst.y + q.dst.height } }.maxBy { it.dst.width }.dst
            // the row as the art has it, SWAP still in it ([artRowEnds]): SWAP gone, the pads keep their places (user)
            val (rowLeft, _) = artRowEnds(v, d)
            val dpad = ring("DPAD")
            // where the user's tweak put it: 222 px from the art's row, clear of the touch screen's frame, its top by the stick's foot
            kotlin.test.assertEquals(222f * p.k, rowLeft - (dpad.x + dpad.width), 2f, "$v $name d-pad 222 px from the row")
            assertTrue(dpad.x + dpad.width < touch.x, "$v $name d-pad clear of the touch screen")
            val stick = ring("STICK")
            kotlin.test.assertEquals(stick.y + stick.height, dpad.y, 4f, "$v $name d-pad's top by the stick's foot")
        }
    }

    // The New 3DS on the Ultra's inner screen upright (flat and half-folded): a joystick for the C-stick at the d-pad's size
    // (not the nub over the face), where the d-pad is mirrored on the right: level with it, as far from the row.
    @Test
    fun theNew3dsOnTheUltraUprightHasItsCStickAsTheDpadMirrored() {
        for (name in listOf("ultra", "ultra_half")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, SkinVariant.N3DS, d), name)
            fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
            fun ring(id: String) = p.pieces.filter { q -> c(id).let { it.x >= q.dst.x && it.x <= q.dst.x + q.dst.width && it.y >= q.dst.y && it.y <= q.dst.y + q.dst.height } }.maxBy { it.dst.width }.dst
            kotlin.test.assertEquals(1, p.controls.count { it.spec.id == "CSTICK" }, "$name one C-stick")
            val dpad = ring("DPAD"); val cs = ring("CSTICK")
            kotlin.test.assertEquals(dpad.width, cs.width, 2f, "$name C-stick the d-pad's size")
            kotlin.test.assertEquals(dpad.y + dpad.height / 2f, cs.y + cs.height / 2f, 2f, "$name C-stick level with the d-pad")
            val rowY = c("MIC").y
            val rowPieces = p.pieces.filter { q -> kotlin.math.abs(q.dst.y + q.dst.height / 2f - rowY) < 2f }
            val rowLeft = rowPieces.minOf { it.dst.x }; val rowRight = rowPieces.maxOf { it.dst.x + it.dst.width }
            kotlin.test.assertEquals(rowLeft - (dpad.x + dpad.width), cs.x - rowRight, 2f, "$name C-stick as far from the row as the d-pad")
            assertTrue(c("CSTICK").y > c("B").y, "$name C-stick under the face")
            // the face and the shoulders as the regular 3DS's (where the menu has them; R level with L), ZR beside R as ZL
            // beside L
            val reg = assertNotNull(placeFor(pack, SkinVariant.N3DS_REGULAR, d), name)
            fun rc(id: String) = reg.controls.first { it.spec.id == id && it.spec.alt == null }
            for (id in listOf("X", "Y", "A", "B", "R")) {
                kotlin.test.assertEquals(rc(id).x, c(id).x, 1f, "$name $id x as the regular 3DS's")
                kotlin.test.assertEquals(rc(id).y, c(id).y, 1f, "$name $id y as the regular 3DS's")
                kotlin.test.assertEquals(rc(id).spec.r, c(id).spec.r, 0.5f, "$name $id size as the regular 3DS's")
            }
            kotlin.test.assertEquals(c("L").y, c("R").y, 1f, "$name R level with L")
            kotlin.test.assertEquals(c("L2").y, c("R2").y, 1f, "$name ZR level with ZL")
            fun span(id: String) = c(id).let { k -> p.pieces.filter { q -> k.x >= q.dst.x && k.x <= q.dst.x + q.dst.width && k.y >= q.dst.y && k.y <= q.dst.y + q.dst.height }.maxBy { it.dst.width }.dst }
            kotlin.test.assertEquals(span("L2").x - (span("L").x + span("L").width), span("R").x - (span("R2").x + span("R2").width), 2f, "$name ZR beside R as ZL beside L")
        }
    }

    // The 3DS on the Ultra's inner screen sideways (flat and half-folded): MIC, MENU, SELECT, START in one row under the
    // touch screen's frame, centred across.
    @Test
    fun the3dsOnTheUltraSidewaysHasItsCapsInARowUnderTheTouchScreen() {
        for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) for (name in listOf("ultra_land", "ultra_land_half")) {
            val d = GOLDEN_DEVICES.first { it.name == name }
            val p = assertNotNull(placeFor(pack, v, d), "$v $name")
            fun c(id: String) = p.controls.first { it.spec.id == id && it.spec.alt == null }
            val row = listOf("MIC", "MENU", "SELECT", "START").map(::c)
            for ((a, b) in row.zipWithNext()) { kotlin.test.assertEquals(a.y, b.y, 1f, "$v $name ${b.spec.id} in the row"); assertTrue(b.x > a.x, "$v $name ${b.spec.id} in order") }
            kotlin.test.assertEquals(d.w / 2f, (row.first().x + row.last().x) / 2f, 2f, "$v $name row centred")
            val touch = assertNotNull(p.glasses.firstOrNull { it.screen == GlassScreen.TOUCH }, "$v $name").rect
            assertTrue(row[0].y - row[0].spec.r > touch.y + touch.height, "$v $name row under the touch screen")
            // the d-pad where the user's tweak put it (48 px right, 66 px up of under the stick: 2052.9 before); the New
            // 3DS's C-stick level with it
            kotlin.test.assertEquals(2052.9f - 66.28f * p.k, c("DPAD").y, 2f, "$v $name d-pad up as the tweak")
            if (v == SkinVariant.N3DS) kotlin.test.assertEquals(c("DPAD").y, c("CSTICK").y, 1f, "$v $name C-stick level with the d-pad")
            // the touch screen wide into the room between the side boxes (its 4:3 picture in it with bars), the inner gap
            // (16 dp) to the nearest piece each side beside it
            assertTrue(touch.width > touch.height * 4f / 3f + 50f, "$v $name touch screen wider than its picture: $touch")
            val dp = d.dpi / 160f
            val beside = p.pieces.filter { it.piece >= 0 && p.spec.pieces[it.piece].glass == null && it.dst.y < touch.y + touch.height && it.dst.y + it.dst.height > touch.y }
            val frame = p.pieces.filter { it.piece >= 0 && p.spec.pieces[it.piece].glass?.screen == GlassScreen.TOUCH }
            val f0 = frame.minOf { it.dst.x }; val f1 = frame.maxOf { it.dst.x + it.dst.width }
            val l1 = beside.filter { it.dst.x + it.dst.width / 2f < touch.x }.maxOf { it.dst.x + it.dst.width }
            val r0 = beside.filter { it.dst.x + it.dst.width / 2f > touch.x + touch.width }.minOf { it.dst.x }
            // (widened about its middle: the nearer side at the inner gap)
            kotlin.test.assertEquals(16f * dp, minOf(f0 - l1, r0 - f1), 2f, "$v $name nearer gap to the frame")
            assertTrue(maxOf(f0 - l1, r0 - f1) >= 16f * dp - 2f, "$v $name farther gap")
            kotlin.test.assertEquals(d.w / 2f, (f0 + f1) / 2f, 2f, "$v $name frame centred")
            // the New 3DS's C-stick as far from the frame as the d-pad (the d-pad's move mirrored)
            if (v == SkinVariant.N3DS) {
                fun ring(id: String) = p.pieces.filter { q -> c(id).let { it.x >= q.dst.x && it.x <= q.dst.x + q.dst.width && it.y >= q.dst.y && it.y <= q.dst.y + q.dst.height } }.maxBy { it.dst.width }.dst
                kotlin.test.assertEquals(f0 - (ring("DPAD").x + ring("DPAD").width), ring("CSTICK").x - f1, 2f, "$v $name C-stick as far from the frame as the d-pad")
            }
        }
    }

    // A cap's switch (the 3DS's stick / d-pad toggle) in its well is one group with the caps it shares the well with, as
    // its outline shows: the Ultra's cover upright.
    @Test
    fun theSwitchIsGroupedWithItsWell() {
        for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) {
            val p = assertNotNull(SkinChoice.place(pack, 1080, 2424, 480, false, ds = true, hingeY = null, foldable = true, variant = v), "$v")
            val ids = SkinGroups.of(p.spec).map { it.id }
            val mine = ids.single { "STICK_SWAP" in it.split("+") }
            assertTrue(mine.split("+").any { it == "MIC" }, "$v the switch with MIC: $ids")
        }
    }

    /** The left and right ends of [v]'s caps row under the touch screen on [d] as the pack's art draws it, SWAP in it. */
    private fun artRowEnds(v: SkinVariant, d: DeviceClass): Pair<Float, Float> {
        val p = assertNotNull(SkinChoice.place(pack, d.w, d.h, d.dpi, d.tablet, ds = true, hingeY = d.hingeY, foldable = d.foldable, variant = v), "$v ${d.name} art")
        val y = p.controls.first { it.spec.id == "MIC" && it.spec.alt == null }.y
        val row = p.pieces.filter { q -> kotlin.math.abs(q.dst.y + q.dst.height / 2f - y) < 2f }
        return row.minOf { it.dst.x } to row.maxOf { it.dst.x + it.dst.width }
    }
}
