package com.nxredux.mobile.skin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkinSolverTest {
    /**
     * 1000 x 1000: a glass row (frame 100 | glass 800 | frame 100, 600 tall incl. 100 frames) over a filler row (100) and
     * a controls row (300): d-pad in the left 300, a repeat 400 between, A in the right 300.
     */
    private fun spec(scales: List<List<Float>> = listOf(listOf(3f)), glassH: Int = 400, hinge: Boolean = false) = SkinLayoutSpec(
        id = "t", family = SkinFamily.STACKED, variant = SkinVariant.DS, image = "t.png", width = 1000, height = 1000,
        tree = SliceNode(SliceSplit.ROWS, listOf(
            SlicePart(100, node = row3("fixed")),
            SlicePart(glassH, "glass:g", node = row3("fixed")),
            SlicePart(100, node = row3("fixed")),
            SlicePart(if (hinge) 100 else 100, if (hinge) "hinge" else "repeat", node = SliceNode(SliceSplit.COLS, listOf(SlicePart(1000, "repeat")))),
            SlicePart(300 - (glassH - 400), node = SliceNode(SliceSplit.COLS, listOf(SlicePart(300), SlicePart(400, "repeat"), SlicePart(300)))),
        )),
        glasses = listOf(GlassSpec("g", GlassScreen.PICTURE, 100, 100, 800, glassH)),
        scales = scales,
        controls = listOf(ControlSpec("DPAD", ControlShape.DPAD, 150f, 850f, r = 100f), ControlSpec("A", ControlShape.CIRCLE, 850f, 850f, r = 50f)),
    )

    private fun row3(frame: String) = SliceNode(SliceSplit.COLS, listOf(SlicePart(100, frame), SlicePart(800, "glass:g"), SlicePart(100, frame)))

    @Test
    fun sameSizeKeepsEverything() {
        val p = assertNotNull(SkinSolver.place(spec(listOf(listOf(0f))), 1000f, 1000f, 1f, picture = 800f to 400f))
        val g = p.glasses.single()
        assertEquals(100f, g.rect.x, 0.5f); assertEquals(800f, g.rect.width, 0.5f); assertEquals(400f, g.rect.height, 0.5f)
        assertEquals(850f, p.controls.first { it.spec.id == "A" }.x, 0.5f)
        assertTrue(p.cells.all { kotlin.math.abs(it.src.width - it.dst.width) < 0.5f && kotlin.math.abs(it.src.height - it.dst.height) < 0.5f })
    }

    @Test
    fun widerWindowGrowsGlassAndMovesTheRightControls() {
        // 1200 wide: the fill glass takes the width (1000 x 500), the controls' repeat the 200, A follows the right edge
        val p = assertNotNull(SkinSolver.place(spec(listOf(listOf(0f))), 1200f, 1200f, 1f, picture = 800f to 400f))
        val g = p.glasses.single()
        assertEquals(1000f, g.rect.width, 1f); assertEquals(500f, g.rect.height, 1f)
        assertEquals(1050f, p.controls.first { it.spec.id == "A" }.x, 1f)
        assertEquals(150f, p.controls.first { it.spec.id == "DPAD" }.x, 0.5f)
        // the controls sit at the bottom: the filler row took the rest of the height (1200 - 1100 + 100 - 100)
        assertEquals(1050f, p.controls.first { it.spec.id == "A" }.y, 1f)
    }

    @Test
    fun wholeScaleTakesTheFirstThatFits() {
        // game 200 x 100: 5x (1000 wide) can't fit 1000 - 200 of frames; 4x (800 x 400) does
        val p = assertNotNull(SkinSolver.place(spec(listOf(listOf(5f), listOf(4f))), 1000f, 1000f, 1f, picture = 200f to 100f))
        assertEquals(4f, p.glasses.single().scale)
    }

    @Test
    fun densityScalesPictureNotGlass() {
        // k = 2: the window is 2000 x 2000 for the same picture; a 3x 256 x 192... picture glass is in window px
        val p = assertNotNull(SkinSolver.place(spec(listOf(listOf(4f))), 2000f, 2000f, 2f, picture = 400f to 200f))
        assertEquals(1600f, p.glasses.single().rect.width, 0.5f)
        assertEquals(1700f, p.controls.first { it.spec.id == "A" }.x, 0.5f)
        assertEquals(100f, p.controls.first { it.spec.id == "A" }.spec.r, 0.01f)
    }

    @Test
    fun tooSmallDoesNotFit() {
        assertNull(SkinSolver.place(spec(listOf(listOf(3f))), 500f, 800f, 1f, picture = 800f to 400f))
    }

    @Test
    fun hingeLandsOnTheFold() {
        // the hinge's middle at y 750: its top at 700, the glass above it, the controls below
        val s = spec(listOf(listOf(1f)), hinge = true).let { sp ->
            // a repeat row above the glass so the hinge can move
            sp.copy(tree = sp.tree.copy(parts = listOf(SlicePart(0, "repeat", SliceNode(SliceSplit.COLS, listOf(SlicePart(1000, "repeat"))))) + sp.tree.parts))
        }
        val p = assertNotNull(SkinSolver.place(s, 1000f, 1100f, 1f, picture = 800f to 400f, hingeY = 750f))
        assertEquals(200f, p.glasses.single().rect.y, 1f)   // hinge top 700: the repeat above takes 700 - 600
        assertEquals(950f, p.controls.first { it.spec.id == "A" }.y, 1f)
    }
}
