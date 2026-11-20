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
        val wide = assertNotNull(SkinChoice.place(pack, 2504, 2256, 480, tablet = true, ds = true, hingeY = 1128f, foldable = true))
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
        val fold = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, hingeY = 1224f, foldable = true))
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
    fun theDsGamesSpacingIsTheLeastEveryFoldBoardKeeps() {
        // the Fold upright half-folded, the board's own width: the game and the menu alike
        val game = assertNotNull(SkinChoice.place(pack, 1848, 2448, 420, tablet = true, ds = true, hingeY = 1224f, foldable = true))
        val menu = assertNotNull(SkinChoice.placeMenu(pack, 1848, 2448, 420, foldable = true, tablet = true, hingeY = 1224f))
        for (p in listOf(game, menu)) {
            val spec = p.spec
            val frame = p.pieces.filter { it.piece >= 0 && spec.pieces[it.piece].glass != null }
            val left = p.pieces.filter { it.piece >= 0 && spec.pieces[it.piece].anchor == PieceAnchor.LEFT && spec.pieces[it.piece].glass == null }
            val right = p.pieces.filter { it.piece >= 0 && spec.pieces[it.piece].anchor == PieceAnchor.RIGHT && spec.pieces[it.piece].glass == null }
            assertTrue(left.minOf { it.dst.x } >= 32f - 0.5f, "${spec.id} left edge")
            assertTrue(1848f - right.maxOf { it.dst.x + it.dst.width } >= 32f - 0.5f, "${spec.id} right edge")
            assertTrue(frame.minOf { it.dst.x } - left.maxOf { it.dst.x + it.dst.width } >= 6f - 0.5f, "${spec.id} left gap")
            assertTrue(right.minOf { it.dst.x } - frame.maxOf { it.dst.x + it.dst.width } >= 6f - 0.5f, "${spec.id} right gap")
        }
    }
}
