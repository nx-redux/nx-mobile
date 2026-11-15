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
            // its bottom where the uncapped DS board's is: by the button row, the frame taller instead
            val ds = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, hingeY = hinge, foldable = true)).glass(GlassScreen.TOUCH)!!.rect
            assertEquals(ds.y + ds.height, touch.y + touch.height, 2f, "$hinge bottom: 3DS $touch DS $ds")
        }
        // the DS's 4:3 fills the frame's height before its width: no cap
        val ds = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, foldable = true))
        val dsTop = assertNotNull(ds.glass(GlassScreen.TOP)).rect
        assertTrue(dsTop.height < dsTop.width * 192f / 256f, "$dsTop")
    }

    @Test
    fun onAWideWindowTheSideGroupsMoveIn16DpAndTheTouchFrameWidens() {
        // the Ultra sideways, half-folded: a window much wider than the fold board's render
        // a board the row rules leave alone (the New 3DS's: its ZL / ZR and C-stick): the side groups' own spacing
        val wide = assertNotNull(SkinChoice.place(pack, 2504, 2256, 480, tablet = true, ds = true, hingeY = 1128f, foldable = true, variant = SkinVariant.N3DS))
        val render = pack.layouts.single { it.id == wide.spec.id }
        val k = wide.k
        val c = wide.controls.associateBy { it.spec.id }
        // the d-pad 16 dp (48 px at 480 dpi) in from where its anchor alone puts it; the A button likewise from the right
        val dpad = render.controls.single { it.id == "DPAD" }
        assertEquals((dpad.x + dpad.inward) * k + 48f, c.getValue("DPAD").x, 2f)
        val a = render.controls.single { it.id == "A" }
        val rightAnchor = (a.x + 2504f / k - render.refW - a.inward) * k
        assertEquals(rightAnchor - 48f, c.getValue("A").x, 2f)
        // the touch screen's frame wider than its 4:3 picture: the rest of the room
        val touch = assertNotNull(wide.glass(GlassScreen.TOUCH)).rect
        assertTrue(touch.width / touch.height > 4f / 3f + 0.1f, "$touch")
        assertTrue(touch.x > c.getValue("DPAD").x + c.getValue("DPAD").spec.r, "the frame clear of the d-pad: $touch")
        // 16 dp from the nearest piece of each group to the touch screen's frame
        val frame = wide.pieces.filter { it.piece >= 0 && render.pieces[it.piece].glass != null }
        val left = wide.pieces.filter { it.piece >= 0 && render.pieces[it.piece].anchor == PieceAnchor.LEFT && render.pieces[it.piece].glass == null }
        val right = wide.pieces.filter { it.piece >= 0 && render.pieces[it.piece].anchor == PieceAnchor.RIGHT && render.pieces[it.piece].glass == null }
        assertEquals(48f, frame.minOf { it.dst.x } - left.maxOf { it.dst.x + it.dst.width }, 2f)
        assertEquals(48f, right.minOf { it.dst.x } - frame.maxOf { it.dst.x + it.dst.width }, 2f)
        // the Fold upright, the render's width: nothing moves sideways
        val fold = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, hingeY = 1224f, foldable = true, variant = SkinVariant.N3DS))
        val foldDpad = fold.controls.single { it.spec.id == "DPAD" }
        val foldRender = pack.layouts.single { it.id == fold.spec.id }.controls.single { it.id == "DPAD" }
        assertEquals(foldRender.x + foldRender.inward, foldDpad.x, 1f)
        // its frame as wide as the render's, taller up to the hinge's spacing (half-folded): the 4:3 picture inside it
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
        fun span(a: PieceAnchor, glass: Boolean) = p.pieces.filter { it.piece >= 0 && spec.pieces[it.piece].anchor == a && (spec.pieces[it.piece].glass != null) == glass }
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
            if (a == PieceAnchor.LEFT) assertEquals(2f * inner, f0 - col.maxOf { it.dst.x + it.dst.width }, 2f, "$name left column")
            else assertEquals(2f * inner, col.minOf { it.dst.x } - f1, 2f, "$name right column")
            val ring = if (a == PieceAnchor.LEFT) lRing else rRing
            assertEquals(16f * dp, col.minOf { it.dst.y } - ring.maxOf { it.dst.y + it.dst.height }, 2f, "$name column under its ring")
        }
    }
    data class Quad(val a: Int, val b: Int, val c: Int, val d: Float)

    @Test
    fun aGameFoldBoardsFaceButtonsStandWhereTheMenusDo() {
        // the DS's and the regular 3DS's diamond, R and START / SELECT where the main menu has them, the Fold and the Ultra
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
                if (v == SkinVariant.N3DS_REGULAR) for (id in listOf("START", "SELECT")) assertEquals(mc.getValue(id).x, g.getValue(id).x, 1f, "$v ${w}x$h $id x")
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
            for (id in listOf("DPAD", "X", "Y", "A", "B", "START", "SELECT", "MENU")) {
                assertEquals(mc.getValue(id).x, gc.getValue(id).x, 1.5f, "${w}x$h ${game.spec.id} $id x")
                assertEquals(mc.getValue(id).y, gc.getValue(id).y, 1.5f, "${w}x$h ${game.spec.id} $id y")
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
            // the fold board shows the stick and the d-pad both, a little smaller where the two need the height: the stick
            // where the menu has its d-pad, level with the face buttons' middle
            if (fold) assertEquals(gc.getValue("Y").y, gc.getValue("STICK").y, 1.5f, "${w}x$h stick level with the face")
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
                // the d-pad under the stick, in line, 16 dp apart; MENU under START on the right, right of the bottom screen
                val st = ringOf("STICK"); val pad = ringOf("DPAD")
                assertEquals(st.x, pad.x, 1f, "${w}x$h d-pad in line")
                // 16 dp under it and over the window's bottom, both coming down alike (to 4 dp) where the height is short
                val gap = pad.y - (st.y + st.height); val under = h - (pad.y + pad.height)
                assertTrue(gap >= 4f * dp - 1f && gap <= 16f * dp + 1f, "${w}x$h d-pad under the stick $gap")
                if (gap < 16f * dp - 1f) assertEquals(gap, under, 2f, "${w}x$h room under the d-pad") else assertTrue(under >= 16f * dp - 1f, "${w}x$h room under")
                assertTrue("STICK_SWAP" !in gc, "${w}x$h no swap")
                assertEquals(start.x, m.x, 1f, "${w}x$h START / MENU column"); assertTrue(start.y < m.y, "${w}x$h MENU under START")
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
