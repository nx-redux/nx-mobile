package com.nxredux.mobile.skin

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The fold board as one slab unless half-folded ([SkinSeam], [SkinPlacement.slab]). */
class SkinSeamTest {
    private val pack = assertNotNull(SkinPackManifest.parse(File("../androidApp/src/main/assets/skins/nx-carbon/skin.json").readText()))
    private val stretchMax = 64f

    @Test
    fun theHingeShowsOnlyHalfFolded() {
        val flat = assertNotNull(SkinChoice.place(pack, 2256, 2504, 480, tablet = true, ds = true, foldable = true))
        assertTrue(flat.slab, "the Ultra flat: ${flat.spec.id}")
        val half = assertNotNull(SkinChoice.place(pack, 2504, 2256, 480, tablet = true, ds = true, hingeY = 1128f, foldable = true))
        assertFalse(half.slab, "the Ultra half-folded: ${half.spec.id}")
        val tablet = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true))
        assertTrue(tablet.slab, "a tablet: ${tablet.spec.id}")
        val phone = assertNotNull(SkinChoice.place(pack, 1080, 2400, 420, tablet = false, ds = true))
        assertFalse(phone.slab, "a phone's board has no hinge to hide: ${phone.spec.id}")
        val menuFlat = assertNotNull(SkinChoice.placeMenu(pack, 2256, 2504, 480, foldable = true, tablet = true))
        assertTrue(menuFlat.slab, menuFlat.spec.id)
        val menuHalf = assertNotNull(SkinChoice.placeMenu(pack, 2504, 2256, 480, foldable = true, tablet = true, hingeY = 1128f))
        assertFalse(menuHalf.slab, menuHalf.spec.id)
    }

    @Test
    fun theSlabCoversTheHingeRowAndMeetsTheBody() {
        for ((w, h, dpi) in listOf(Triple(2256, 2504, 480), Triple(1848, 2448, 420), Triple(2304, 2880, 420))) {
            val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = true, ds = true, foldable = true), "${w}x$h")
            val draws = SkinSeam.draws(p, stretchMax)
            val hinge = p.cells.filter { c -> draws.any { kotlin.math.abs(it.cell.src.x - c.src.x) < 0.5f } }
            assertTrue(draws.isNotEmpty(), "${w}x$h ${p.spec.id}")
            val rows = p.spec.tree.parts
            val at = rows.indexOfFirst { it.isHinge }
            val hingeTop = rows.take(at).sumOf { it.len }.toFloat()
            val hingeCells = p.cells.filter { kotlin.math.abs(it.src.y - hingeTop) < 0.5f }
            // each hinge column: a mirror reaching over the whole hinge row, and a fade above it
            assertEquals(hingeCells.size * 2, draws.size, "${w}x$h")
            for (c in hingeCells) {
                val mirror = draws.single { it.mirror && kotlin.math.abs(it.cell.dst.x - c.dst.x) < 0.5f }.cell.dst
                assertTrue(mirror.y < c.dst.y && mirror.y + mirror.height > c.dst.y + c.dst.height, "${w}x$h mirror $mirror over $c")
                val fade = draws.single { it.fade && kotlin.math.abs(it.cell.dst.x - c.dst.x) < 0.5f }.cell.dst
                assertEquals(mirror.y, fade.y, 0.5f)
                assertTrue(fade.y + fade.height <= c.dst.y, "${w}x$h the fade ends above the hinge")
            }
            assertTrue(hinge.isNotEmpty())
        }
    }

    @Test
    fun aBoardWithoutAHingeHasNoSlab() {
        val p = assertNotNull(SkinChoice.place(pack, 1080, 2400, 420, tablet = false, ds = true))
        assertEquals(emptyList(), SkinSeam.draws(p, stretchMax))
    }

    @Test
    fun withoutTheHingeTheTopScreenReachesTheFrameUnderIt() {
        // the hinge's own room above it, half-folded at the middle, against flat (no hinge drawn)
        val with = assertNotNull(SkinChoice.place(pack, 2256, 2504, 480, tablet = true, ds = true, hingeY = 1252f, foldable = true))
        val without = assertNotNull(SkinChoice.place(pack, 2256, 2504, 480, tablet = true, ds = true, foldable = true))
        val top = assertNotNull(with.glass(GlassScreen.TOP)).rect
        val slab = assertNotNull(without.glass(GlassScreen.TOP)).rect
        assertTrue(slab.height > top.height, "taller: $slab over $top")
        // its frame down to 16 dp over the touch screen's frame: the glasses that and two borders apart
        val touch = assertNotNull(without.glass(GlassScreen.TOUCH)).rect
        assertTrue(touch.y - (slab.y + slab.height) in 16f * 3f..16f * 3f + 200f, "$slab over $touch")
        val menu = assertNotNull(SkinChoice.placeMenu(pack, 2256, 2504, 480, foldable = true, tablet = true))
        val menuTop = assertNotNull(menu.glasses.firstOrNull { it.id == "menutop" }).rect
        val menuGlass = assertNotNull(menu.glasses.firstOrNull { it.id == "menu" }).rect
        assertTrue(menuGlass.y - (menuTop.y + menuTop.height) in 16f * 3f..16f * 3f + 200f, "$menuTop over $menuGlass")
    }

    @Test
    fun aWideTopScreensFrameIsNoTallerThanItsPicture() {
        // the 3DS's 5:3 top screen across the Fold's frame: its frame no taller than that plus 8 dp above and below, at the top
        for (hinge in listOf(null, 1224f)) {
            val p = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, hingeY = hinge, foldable = true, variant = SkinVariant.N3DS), "$hinge")
            val top = assertNotNull(p.glass(GlassScreen.TOP)).rect
            val cap = top.width * 240f / 400f + 2f * 8f * 420f / 160f
            // flat it reaches the cap; half-folded the room above the hinge is shorter still
            if (hinge == null) assertEquals(cap, top.height, 2f, "$top") else assertTrue(top.height <= cap + 2f, "$hinge $top")
            val touch = assertNotNull(p.glass(GlassScreen.TOUCH)).rect
            // flat the touch screen's frame reaches up under it, 16 dp between the frames (the glasses that and two borders apart);
            // half-folded it stays below the hinge
            if (hinge == null) assertTrue(touch.y - (top.y + top.height) in 42f..242f, "$top over $touch")
            else assertTrue(touch.y > hinge, "$touch")
            // the frame no taller than its 4:3 picture (no bars over and under the game: user), the row under it up with it
            assertEquals(touch.width * 3f / 4f, touch.height, 1f, "$hinge touch $touch")
            val menu = p.controls.single { it.spec.id == "MENU" }
            assertTrue(menu.y > touch.y + touch.height && menu.y - (touch.y + touch.height) < 200f, "$hinge row under the frame: $touch $menu")
        }
        // the DS's 4:3 fills the frame's height before its width: no cap
        val ds = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, foldable = true))
        val dsTop = assertNotNull(ds.glass(GlassScreen.TOP)).rect
        assertTrue(dsTop.height < dsTop.width * 192f / 256f, "$dsTop")
    }

    @Test
    fun onAWideWindowThe3dsTouchFrameWidensIntoTheRoom() {
        // the Ultra sideways, flat and half-folded: the touch screen held back by its height
        for (hinge in listOf(null, 1128f)) for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) {
            val name = "$v $hinge"
            val wide = assertNotNull(SkinChoice.place(pack, 2504, 2256, 480, tablet = true, ds = true, hingeY = hinge, foldable = true, variant = v), name)
            val render = wide.spec
            val c = wide.controls.associateBy { it.spec.id }
            val dp = 3f
            // the touch screen widened into the room between the side groups, its 4:3 picture in it with bars
            val touch = assertNotNull(wide.glass(GlassScreen.TOUCH)).rect
            assertTrue(touch.width / touch.height >= 4f / 3f - 0.01f, "$name $touch")
            val frame = wide.pieces.filter { it.piece >= 0 && render.pieces[it.piece].glass?.screen == GlassScreen.TOUCH && !it.strip }
            val left = wide.pieces.filter { it.piece >= 0 && render.pieces[it.piece].anchor == PieceAnchor.LEFT && render.pieces[it.piece].glass == null }
            val right = wide.pieces.filter { it.piece >= 0 && render.pieces[it.piece].anchor == PieceAnchor.RIGHT && render.pieces[it.piece].glass == null }
            // the side groups 32 dp from the window's edges, at least 16 dp from the frame
            assertEquals(32f * dp, left.minOf { it.dst.x }, 2f, "$name left edge")
            assertEquals(32f * dp, 2504f - right.maxOf { it.dst.x + it.dst.width }, 2f, "$name right edge")
            assertTrue(frame.minOf { it.dst.x } - left.maxOf { it.dst.x + it.dst.width } >= 16f * dp - 1f, "$name left gap")
            assertTrue(right.minOf { it.dst.x } - frame.maxOf { it.dst.x + it.dst.width } >= 16f * dp - 1f, "$name right gap")
            // L level with R, the stick with the face buttons, the d-pad under the stick (its place: the user's tweak,
            // SkinBakeTest.the3dsOnTheUltraSidewaysHasItsCapsInARowUnderTheTouchScreen)
            assertEquals(c.getValue("R").y, c.getValue("L").y, 1f, "$name L level with R")
            val face = (c.getValue("X").y + c.getValue("B").y) / 2f
            assertEquals(face, c.getValue("STICK").y, 1f, "$name stick level with the face")
            assertTrue(c.getValue("DPAD").y > c.getValue("STICK").y, "$name d-pad under the stick")
            val regular = assertNotNull(SkinChoice.place(pack, 2504, 2256, 480, tablet = true, ds = true, hingeY = hinge, foldable = true, variant = SkinVariant.N3DS_REGULAR))
            if (v == SkinVariant.N3DS) {
                // the New 3DS's buttons where the regular one's are; ZL / ZR beside L / R; the C-stick under the face
                val rc = regular.controls.associateBy { it.spec.id }
                rc.values.forEach { r -> c[r.spec.id]?.let { assertEquals(r.x, it.x, 1f, "$name ${r.spec.id} x"); assertEquals(r.y, it.y, 1f, "$name ${r.spec.id} y") } }
                assertEquals(c.getValue("L").y, c.getValue("L2").y, 1f, "$name ZL level with L"); assertTrue(c.getValue("L2").x > c.getValue("L").x, "$name ZL inside L")
                assertEquals(c.getValue("R").y, c.getValue("R2").y, 1f, "$name ZR level with R"); assertTrue(c.getValue("R2").x < c.getValue("R").x, "$name ZR inside R")
                assertTrue(c.getValue("CSTICK").y > face, "$name C-stick under the face")
                assertTrue(right.maxOf { it.dst.y + it.dst.height } <= 2256f - 16f * dp + 1f, "$name the C-stick in the window")
            }
            assertTrue(left.maxOf { it.dst.y + it.dst.height } <= 2256f - 16f * dp + 1f, "$name the d-pad in the window")
            // MIC, MENU, SWAP, SELECT, START in one row under the touch screen, centred on it
            val row = listOf("MIC", "MENU", "SWAP", "SELECT", "START").map { c.getValue(it) }
            row.zipWithNext().forEach { (a, b) -> assertEquals(a.y, b.y, 1f, "$name row level"); assertTrue(b.x > a.x, "$name row order") }
            assertEquals(touch.x + touch.width / 2f, c.getValue("SWAP").x, 2f, "$name SWAP in the middle")
            assertTrue(row.all { it.y > touch.y + touch.height }, "$name row under the touch screen")
        }
        // flat, the bottom half as half-folded: folding never moves the touch screen or the buttons
        for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) {
            val flat = assertNotNull(SkinChoice.place(pack, 2504, 2256, 480, tablet = true, ds = true, hingeY = null, foldable = true, variant = v))
            val half = assertNotNull(SkinChoice.place(pack, 2504, 2256, 480, tablet = true, ds = true, hingeY = 1128f, foldable = true, variant = v))
            val ft = assertNotNull(flat.glass(GlassScreen.TOUCH)).rect; val ht = assertNotNull(half.glass(GlassScreen.TOUCH)).rect
            listOf(ft.x to ht.x, ft.y to ht.y, ft.width to ht.width, ft.height to ht.height).forEach { (a, b) -> assertEquals(b, a, 0.5f, "$v touch $ft $ht") }
            val hc = half.controls.associateBy { it.spec.id }
            flat.controls.forEach { c -> hc[c.spec.id]?.let { assertEquals(it.x, c.x, 0.5f, "$v ${c.spec.id}"); assertEquals(it.y, c.y, 0.5f, "$v ${c.spec.id}") } }
        }
        val fold = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, hingeY = 1224f, foldable = true, variant = SkinVariant.N3DS))
        // the Fold: its frame as wide as the render's, taller up to the hinge's spacing (half-folded): the 4:3 picture
        // inside it
        val ft = assertNotNull(fold.glass(GlassScreen.TOUCH)).rect
        assertTrue(ft.width / ft.height <= 4f / 3f + 0.02f, "$ft")
    }

    @Test
    fun halfFoldedSidewaysTheMenusBottomScreenEndsWhereTheFlatOnesDoes() {
        // the Ultra sideways: flat the landscape shell, half-folded the fold board; the menu screen's bottom edge alike
        val flat = assertNotNull(SkinChoice.placeMenu(pack, 2504, 2256, 480, foldable = true, tablet = true))
        val half = assertNotNull(SkinChoice.placeMenu(pack, 2504, 2256, 480, foldable = true, tablet = true, hingeY = 1128f))
        assertEquals(SkinFamily.GRIP, flat.spec.family)
        val f = flat.glasses.first().rect
        val m = half.glasses.single { it.id == "menu" }.rect
        assertEquals(f.y + f.height, m.y + m.height, 2f, "flat $f half-folded $m")
    }

    @Test
    fun halfFoldedTheMenusBottomScreenStartsAsFarUnderTheHingeAsTheTopEndsAboveIt() {
        val half = assertNotNull(SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true, hingeY = 1224f))
        val top = half.glasses.single { it.id == "menutop" }.rect
        val menu = half.glasses.single { it.id == "menu" }.rect
        // the frames' borders apart (the top frame's bottom border 47, the menu piece's top border 22, picture px at k 1)
        val above = 1224f - (top.y + top.height + 47f)
        val below = (menu.y - 22f) - 1224f
        assertEquals(above, below, 2f, "top $top menu $menu")
    }

    @Test
    fun rowShapedGameBoardsSpaceTheRowAsTheMenuDoes() {
        // the DS, the regular 3DS, the GB and a one-screen game's boards, on the Fold and the Ultra (upright, sideways)
        for ((w, h, dpi, hinge) in listOf(Quad(1848, 2448, 420, 1224f), Quad(2256, 2504, 480, 1252f), Quad(2504, 2256, 480, 1128f))) {
            for (v in listOf(SkinVariant.DS, SkinVariant.N3DS_REGULAR, SkinVariant.GB, SkinVariant.SINGLE)) {
                // the 3DS on the Ultra sideways: its touch frame cut to its picture (onAWideWindowThe3dsTouchFrameFitsItsPictureAndTheDpadMovesIn)
                // the 3DS on the Ultra upright too: its touch screen cut to its picture, the row under it
                if (v == SkinVariant.N3DS_REGULAR && (w == 2504 || w == 2256)) continue
                val ds = v == SkinVariant.DS || v == SkinVariant.N3DS_REGULAR
                val p = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = true, ds = ds, picture = if (ds) null else 240f to 160f, hingeY = hinge, foldable = true, variant = v), "$v ${w}x$h")
                assertRow(p, w, dpi, "$v ${w}x$h")
            }
        }
    }

    @Test
    fun theMenusSideGroupsAndScreenSpaceTheRowEvenly() {
        // (window, dpi, hinge, expected gap in window px: the full 16 dp, or null where it must shrink)
        for ((w, h, dpi, hinge) in listOf(Quad(1848, 2448, 420, 1224f), Quad(2256, 2504, 480, 1252f), Quad(2504, 2256, 480, 1128f))) {
            val p = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = true, tablet = true, hingeY = hinge), "${w}x$h")
            assertRow(p, w, dpi, "menu ${w}x$h")
            assertTrue(p.glasses.single { it.id == "menu" }.rect.width >= 320f * dpi / 160f - 2f, "${w}x$h menu glass")
        }
    }

    /**
     * [p]'s row ([RowSpacing]): its edge gaps (to 32 dp) and inner gaps (to 16 dp) alike, the full ones with room; L / R
     * 16 dp in from their ring's outer edge and 16 dp over it; a column of two or more twice the inner gap from the
     * screen and 16 dp under its ring.
     */
    private fun assertRow(p: SkinPlacement, w: Int, dpi: Int, name: String) {
        val spec = p.spec
        fun pieces(a: PieceAnchor, glass: Boolean) = p.pieces.filter { it.piece >= 0 && spec.pieces[it.piece].anchor == a && (spec.pieces[it.piece].glass != null) == glass }
        val frame = pieces(PieceAnchor.CENTRE, true).let { ps -> ps.minOf { it.dst.y } to ps.maxOf { it.dst.y + it.dst.height } }
        // the side pieces beside the screen (a pad drawn under its corner, the Fold's 3DS d-pad, is not in the row)
        fun span(a: PieceAnchor, glass: Boolean) = pieces(a, glass).filter { glass || (it.dst.y + it.dst.height / 2f).let { cy -> cy > frame.first && cy < frame.second } }
            .let { ps -> ps.minOf { it.dst.x } to ps.maxOf { it.dst.x + it.dst.width } }
        val (l0, l1) = span(PieceAnchor.LEFT, false); val (f0, f1) = span(PieceAnchor.CENTRE, true); val (r0, r1) = span(PieceAnchor.RIGHT, false)
        val dp = dpi / 160f
        // the edge gaps alike (32 dp with room), the inner ones alike (16 dp with room), never the edge less than the inner
        val edge = l0; val inner = f0 - l1
        assertEquals(edge, w - r1, 2f, "$name edges"); assertEquals(inner, r0 - f1, 2f, "$name inner gaps")
        assertTrue(edge <= 32f * dp + 1f && inner <= 16f * dp + 1f && edge >= inner - 1f && inner >= 4f * dp - 1f, "$name edge $edge inner $inner")
        if (w > 1848) { assertEquals(32f * dp, edge, 1f, "$name edge"); assertEquals(16f * dp, inner, 1f, "$name inner") }
        val gaps = listOf(edge, inner)
        fun held(a: PieceAnchor, ids: Set<String>) = p.pieces.filter { pc -> pc.piece >= 0 && spec.pieces[pc.piece].anchor == a && spec.pieces[pc.piece].glass == null &&
            spec.controls.any { c -> c.id in ids && c.anchor == a && spec.pieces[pc.piece].let { s -> c.x >= s.x && c.x <= s.x + s.w * s.scale && c.y >= s.y && c.y <= s.y + s.h * s.scale } } }
        val lRing = held(PieceAnchor.LEFT, setOf("DPAD", "STICK")); val rRing = held(PieceAnchor.RIGHT, setOf("X", "Y", "A", "B"))
        held(PieceAnchor.LEFT, setOf("L")).takeIf { it.isNotEmpty() }?.let { l ->
            assertEquals(lRing.minOf { it.dst.x } + 16f * dp, l.minOf { it.dst.x }, 2f, "$name L 16 dp in from the d-pad's box")
            assertEquals(16f * dp, lRing.minOf { it.dst.y } - l.maxOf { it.dst.y + it.dst.height }, 2f, "$name L over its ring")
        }
        held(PieceAnchor.RIGHT, setOf("R")).takeIf { it.isNotEmpty() }?.let { r ->
            assertEquals(rRing.maxOf { it.dst.x + it.dst.width } - 16f * dp, r.maxOf { it.dst.x + it.dst.width }, 2f, "$name R 16 dp in from the face buttons' box")
            assertEquals(16f * dp, rRing.minOf { it.dst.y } - r.maxOf { it.dst.y + it.dst.height }, 2f, "$name R over its ring")
        }
        for (a in listOf(PieceAnchor.LEFT, PieceAnchor.RIGHT)) {
            val ids = setOf("START", "SELECT", "MENU", "MIC", "SWAP")
            if (spec.controls.count { it.id in ids && it.anchor == a } < 2) continue
            val col = held(a, ids)
            // (a column two gaps from the screen; a row, the DS's on its fold board, from the box's inner edge)
            val caps = spec.controls.filter { it.id in ids && it.anchor == a }
            val isRow = caps.all { kotlin.math.abs(it.y - caps[0].y) < 1f }
            if (!isRow && a == PieceAnchor.LEFT) assertEquals(2f * inner, f0 - col.maxOf { it.dst.x + it.dst.width }, 2f, "$name left column")
            else if (!isRow) assertEquals(2f * inner, col.minOf { it.dst.x } - f1, 2f, "$name right column")
            val ring = if (a == PieceAnchor.LEFT) lRing else rRing
            assertEquals(16f * dp, col.minOf { it.dst.y } - ring.maxOf { it.dst.y + it.dst.height }, 2f, "$name column under its ring")
        }
    }
    data class Quad(val a: Int, val b: Int, val c: Int, val d: Float)

    @Test
    fun aGameFoldBoardsFaceButtonsStandWhereTheMenusDo() {
        // the DS's and the regular 3DS's diamond and R where the main menu has them, the Fold and the Ultra (the 3DS's
        // START / SELECT in the row under its touch screen)
        for ((w, h, dpi) in listOf(Triple(1848, 2448, 420), Triple(2256, 2504, 480))) {
            val menu = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = true, tablet = true))
            val mc = menu.controls.associateBy { it.spec.id }
            // (the Ultra's DS takes the board's tablet twin, its clusters centred on the bottom screen: its own design)
            for (v in listOfNotNull(SkinVariant.DS.takeIf { w == 1848 }, SkinVariant.N3DS_REGULAR)) {
                val g = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = true, ds = true, foldable = true, variant = v)).controls.associateBy { it.spec.id }
                for (id in listOf("X", "Y", "A", "B", "R")) {
                    assertEquals(mc.getValue(id).x, g.getValue(id).x, 1f, "$v ${w}x$h $id x")
                    assertEquals(mc.getValue(id).y, g.getValue(id).y, 1f, "$v ${w}x$h $id y")
                }
            }
        }
    }

    @Test
    fun theFoldUprightLevelsThe3dsSidesWithASmallerDpadOnASlant() {
        // the touch screen cut to its picture's height: both sides level, the d-pad smaller, 8 dp under the stick
        for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) for (hinge in listOf(null, 1224f)) {
            val name = "$v $hinge"
            val p = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = false, ds = true, hingeY = hinge, foldable = true, variant = v), name)
            val c = p.controls.associateBy { it.spec.id }
            assertEquals(c.getValue("R").y, c.getValue("L").y, 1f, "$name L level with R")
            assertEquals((c.getValue("X").y + c.getValue("B").y) / 2f, c.getValue("STICK").y, 1f, "$name stick level with the face")
            // (the d-pad's size and place: the pack draws them, SkinBakeTest.the3dsOnTheFoldUprightHasItsDpadUpAndIn)
            fun ring(id: String) = c.getValue(id).let { k -> p.pieces.filter { it.piece >= 0 && p.spec.pieces[it.piece].glass == null &&
                kotlin.math.abs(it.dst.x + it.dst.width / 2f - k.x) < 2f && kotlin.math.abs(it.dst.y + it.dst.height / 2f - k.y) < 2f }.maxOf { it.dst.width } }
            val s = c.getValue("STICK"); val d = c.getValue("DPAD")
            assertTrue(d.spec.r < s.spec.r, "$name d-pad smaller than the stick")
            // (the boxes' least gap to the bottom: 4 dp)
            assertTrue(d.y + ring("DPAD") / 2f <= 2448f - 4f * 420f / 160f + 1f, "$name d-pad in the window: ${d.y + ring("DPAD") / 2f} stick ${s.y} L ${c.getValue("L").y} touch ${p.glass(GlassScreen.TOUCH)?.rect}")
        }
    }

    @Test
    fun theFoldUprightGivesTheNew3dsTheRegularPlaces() {
        // the regular 3DS's buttons; ZL / ZR beside L / R; the C-stick a full stick under the face, in toward the screen
        for (hinge in listOf(null, 1224f)) {
            fun at(v: SkinVariant) = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = false, ds = true, hingeY = hinge, foldable = true, variant = v), "$v $hinge")
            val n = at(SkinVariant.N3DS).controls.associateBy { it.spec.id }; val r = at(SkinVariant.N3DS_REGULAR).controls.associateBy { it.spec.id }
            // (both sides lift alike where the C-stick needs the height)
            val lift = r.getValue("X").y - n.getValue("X").y
            for (id in listOf("L", "R", "X", "Y", "A", "B", "STICK", "MIC", "MENU", "SELECT", "START")) {
                assertEquals(r.getValue(id).x, n.getValue(id).x, 1f, "$hinge $id x")
                if (id !in setOf("MIC", "MENU", "SELECT", "START")) assertEquals(r.getValue(id).y - lift, n.getValue(id).y, 1f, "$hinge $id y")
            }
            assertEquals(n.getValue("L").y, n.getValue("L2").y, 1f, "$hinge ZL level with L"); assertTrue(n.getValue("L2").x > n.getValue("L").x, "$hinge ZL inside L")
            assertEquals(n.getValue("R").y, n.getValue("R2").y, 1f, "$hinge ZR level with R"); assertTrue(n.getValue("R2").x < n.getValue("R").x, "$hinge ZR inside R")
            val nub = n.getValue("CSTICK")
            assertEquals(n.getValue("STICK").spec.r * 0.85f, nub.spec.r, 0.5f, "$hinge C-stick at the d-pad's 85%")
            // (in toward the screen as far as the frame and the row leave it: half-folded, none)
            assertTrue(nub.y > n.getValue("B").y && nub.x <= n.getValue("X").x + 1f, "$hinge C-stick under the face, in toward the screen")
        }
    }

    @Test
    fun theUltraUprightPuts3dsStartAndSelectInTheRowAndTheDpadNearerTheStick() {
        for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) for (hinge in listOf(null, 1252f)) {
            val name = "$v $hinge"
            val p = assertNotNull(SkinChoice.place(pack, 2256, 2504, 480, tablet = true, ds = true, hingeY = hinge, foldable = true, variant = v), name)
            val c = p.controls.associateBy { it.spec.id }
            val touch = assertNotNull(p.glass(GlassScreen.TOUCH)).rect
            // (MIC, MENU, SWAP, SELECT, START as on the Fold)
            val row = listOf("MIC", "MENU", "SWAP", "SELECT", "START").map { c.getValue(it) }
            row.zipWithNext().forEach { (a, b) -> assertEquals(a.y, b.y, 1f, "$name row level"); assertTrue(b.x > a.x, "$name row order") }
            assertTrue(row.all { it.y > touch.y + touch.height && it.x > touch.x && it.x < touch.x + touch.width }, "$name row under the touch screen")
            // (the d-pad: where the user's tweak put it, SkinBakeTest.the3dsOnTheUltraUprightIsAsTheFolds)
        }
    }

    @Test
    fun the3dsPhoneBoardsFollowTheMainMenu() {
        // a phone upright and sideways: the Circle Pad (on the d-pad's spot, the two swapped), the face and START / SELECT where
        // the menu has them; MENU stays with MIC and SWAP
        for ((w, h) in listOf(1080 to 2400, 2400 to 1080)) {
            val menu = assertNotNull(SkinChoice.placeMenu(pack, w, h, 420)).controls.associateBy { it.spec.id }
            for (v in listOf(SkinVariant.N3DS, SkinVariant.N3DS_REGULAR)) {
                val g = assertNotNull(SkinChoice.place(pack, w, h, 420, tablet = false, ds = true, variant = v)).controls.associateBy { it.spec.id }
                // (upright, the New 3DS's ZL / ZR stand where SELECT / START were: those in the row under the pad; sideways
                // the small caps in one row each side, the height short of two: user)
                val caps = if ((v == SkinVariant.N3DS && h > w) || w > h) emptyList() else listOf("START" to "START", "SELECT" to "SELECT")
                for ((id, its) in listOf("STICK" to "DPAD", "DPAD" to "DPAD", "X" to "X", "Y" to "Y", "A" to "A", "B" to "B") + caps) {
                    assertEquals(menu.getValue(its).x, g.getValue(id).x, 1f, "$v ${w}x$h $id x")
                    assertEquals(menu.getValue(its).y, g.getValue(id).y, 1f, "$v ${w}x$h $id y")
                }
            }
        }
    }

    @Test
    fun theArcadeFollowsTheMainMenuWithItsTurboCapsBesideTheShoulders() {
        // (window, dpi, foldable/tablet): the Fold's inner screen (fold board), a phone upright and sideways
        for ((w, h, dpi, fold) in listOf(Quad4(1848, 2448, 420, true), Quad4(1080, 2400, 420, false), Quad4(2400, 1080, 420, false))) {
            val menu = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = fold, tablet = fold), "${w}x$h menu")
            val game = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = fold, ds = false, picture = 304f to 224f, foldable = fold, variant = SkinVariant.ARCADE), "${w}x$h arcade")
            val mc = menu.controls.associateBy { it.spec.id }; val gc = game.controls.associateBy { it.spec.id }
            // a narrow upright (a phone): COIN and START in MENU's row, one each side of it
            val upright = !fold && h > w
            for (id in if (upright) listOf("DPAD", "X", "Y", "A", "B", "MENU") else listOf("DPAD", "X", "Y", "A", "B", "START", "SELECT", "MENU")) {
                assertEquals(mc.getValue(id).x, gc.getValue(id).x, 1.5f, "${w}x$h ${game.spec.id} $id x")
                assertEquals(mc.getValue(id).y, gc.getValue(id).y, 1.5f, "${w}x$h ${game.spec.id} $id y")
            }
            if (upright) {
                val m = gc.getValue("MENU")
                for (id in listOf("SELECT", "START")) assertEquals(m.y, gc.getValue(id).y, 1f, "${w}x$h $id in MENU's row")
                assertTrue(gc.getValue("SELECT").x < m.x && gc.getValue("START").x > m.x, "${w}x$h COIN, MENU, START")
                assertEquals(m.x - gc.getValue("SELECT").x, gc.getValue("START").x - m.x, 1f, "${w}x$h evenly")
            }
            // TB on L's inner side, TA on R's, 8 dp apart, level with them
            fun piece(id: String) = game.pieces.single { pc -> pc.piece >= 0 && game.spec.pieces[pc.piece].let { s -> s.glass == null &&
                game.spec.controls.any { c -> c.id == id && c.anchor == s.anchor && c.x >= s.x && c.x <= s.x + s.w * s.scale && c.y >= s.y && c.y <= s.y + s.h * s.scale } } }.dst
            val dp = dpi / 160f
            val l = piece("L"); val tb = piece("TURBO_B"); val r = piece("R"); val ta = piece("TURBO_A")
            assertEquals(8f * dp, tb.x - (l.x + l.width), 1.5f, "${w}x$h L TB"); assertEquals(l.y, tb.y, 1.5f, "${w}x$h TB level with L")
            assertEquals(8f * dp, r.x - (ta.x + ta.width), 1.5f, "${w}x$h TA R"); assertEquals(r.y, ta.y, 1.5f, "${w}x$h TA level with R")
        }
    }
    data class Quad4(val a: Int, val b: Int, val c: Int, val d: Boolean)

    @Test
    fun theDreamcastFollowsTheMainMenuWithItsSwapOverMenuAndStartAlone() {
        for ((w, h, dpi, fold) in listOf(Quad4(1848, 2448, 420, true), Quad4(2400, 1080, 420, false))) {
            val menu = assertNotNull(SkinChoice.placeMenu(pack, w, h, dpi, foldable = fold, tablet = fold), "${w}x$h menu")
            val game = assertNotNull(SkinChoice.place(pack, w, h, dpi, tablet = fold, ds = false, picture = 640f to 480f, foldable = fold, variant = SkinVariant.DC), "${w}x$h dc")
            val mc = menu.controls.associateBy { it.spec.id }; val gc = game.controls.associateBy { it.spec.id }
            // the fold board shows the stick and the d-pad both, at the menu's size
            // the bottom screen's frame as far from the window's bottom as the menu's
            fun frameEnd(q: SkinPlacement) = q.pieces.filter { it.piece >= 0 && q.spec.pieces[it.piece].glass != null }.maxOf { it.dst.y + it.dst.height }
            if (fold) assertEquals(frameEnd(menu), frameEnd(game), 1.5f, "${w}x$h frame's bottom")
            if (fold) assertEquals(menu.k, game.k, 0.001f, "${w}x$h the menu's size")
            else for (id in listOf("DPAD", "X", "Y", "A", "B")) {
                assertEquals(mc.getValue(id).x, gc.getValue(id).x, 1.5f, "${w}x$h ${game.spec.id} $id x"); assertEquals(mc.getValue(id).y, gc.getValue(id).y, 1.5f, "${w}x$h $id y")
            }
            fun ringAt(x: Float, y: Float) = game.pieces.filter { pc -> pc.piece >= 0 && !pc.knob && game.spec.pieces[pc.piece].glass == null }.map { it.dst }
                .filter { kotlin.math.abs(it.x + it.width / 2f - x) < 2f && kotlin.math.abs(it.y + it.height / 2f - y) < 2f }.maxBy { it.width }
            fun ringOf(id: String) = ringAt(gc.getValue(id).x, gc.getValue(id).y)
            fun piece(id: String) = game.pieces.filter { pc -> pc.piece >= 0 && game.spec.pieces[pc.piece].let { s -> s.glass == null &&
                game.spec.controls.any { c -> c.id == id && c.anchor == s.anchor && c.x >= s.x && c.x <= s.x + s.w * s.scale && c.y >= s.y && c.y <= s.y + s.h * s.scale } } }.map { it.dst }
            val dp = dpi / 160f
            val m = gc.getValue("MENU"); val start = gc.getValue("START")
            val face = ringAt(gc.getValue("X").x, gc.getValue("Y").y)
            val ring = if (fold) {
                // the d-pad under the stick; MENU under START on the right, right of the bottom screen
                val st = ringOf("STICK"); val pad = ringOf("DPAD")
                // (the d-pad as the user placed it: 16 dp toward the screen, its ring just under the stick's)
                assertEquals(16f * dp, (pad.x + pad.width / 2f) - (st.x + st.width / 2f), 2f, "${w}x$h d-pad toward the screen")
                assertTrue(pad.y - (st.y + st.height) >= 0f, "${w}x$h d-pad under the stick")
                // (the stick centred under the fold, the d-pad under it: no nearer the bottom than the least gap)
                assertTrue(pad.y + pad.height <= h - 4f * dp + 2f, "${w}x$h d-pad's end")
                val l2 = piece("L2").single(); assertEquals(16f * dp, st.y - (l2.y + l2.height), 2f, "${w}x$h L2 over the stick")
                assertTrue("STICK_SWAP" !in gc, "${w}x$h no swap")
                assertEquals(start.x, m.x, 1f, "${w}x$h START / MENU column"); assertTrue(start.y < m.y, "${w}x$h MENU under START")
                assertTrue(piece("START").any { r -> m.x in r.x..(r.x + r.width) && m.y in r.y..(r.y + r.height) && start.y in r.y..(r.y + r.height) }, "${w}x$h START and MENU in one well")
                assertTrue(start.x > game.glasses.single { it.screen == GlassScreen.PANEL }.rect.let { it.x + it.width }, "${w}x$h START right of the bottom screen")
                assertEquals(16f * dp, piece("START").minOf { it.y } - (face.y + face.height), 2f, "${w}x$h column under the face ring")
                st
            } else {
                val ring = ringOf("DPAD")
                // the swap over MENU in one column on the left, its top 16 dp under the d-pad's ring; START alone on the right,
                // level with its middle (side by side on a short landscape)
                val swap = gc.getValue("STICK_SWAP")
                assertTrue(kotlin.math.abs(swap.x - m.x) > 1f || swap.y < m.y, "${w}x$h the pair")
                assertEquals(16f * dp, piece("STICK_SWAP").minOf { it.y } - (ring.y + ring.height), 2f, "${w}x$h column under the ring")
                assertEquals((swap.y + m.y) / 2f, start.y, 1.5f, "${w}x$h START level with the column's middle")
                ring
            }
            // L2 / R2, the shoulders: 16 dp in from their rings
            val l2 = piece("L2").single(); val r2 = piece("R2").single()
            assertEquals(ring.x + 16f * dp, l2.x, 1.5f, "${w}x$h L2"); assertEquals(face.x + face.width - 16f * dp, r2.x + r2.width, 1.5f, "${w}x$h R2")
        }
    }
}
