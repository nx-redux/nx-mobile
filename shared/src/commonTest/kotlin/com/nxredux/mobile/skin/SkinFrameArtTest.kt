package com.nxredux.mobile.skin

import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A screen frame drawn from smaller art ([FrameSpec.srcW] / [FrameSpec.srcH]) and black over its opening ([FrameSpec.hole]). */
class SkinFrameArtTest {
    // a 1000 x 1600 frame round a glass 28 in from the sides and top, 90 above the foot (a strip's band), from 200 x 260 art
    private val frame = FrameSpec(sx = 100, sy = 50, w = 1000, h = 1600, x = 0, y = 0,
        glass = GlassSpec("top", GlassScreen.PICTURE, 28, 28, 944, 1482, corner = 20f), corner = 40, srcW = 200, srcH = 260, hole = listOf(20, 22, 20, 30))

    @Test
    fun theArtsSlicesStayInsideTheSmallerArt() {
        val slices = SkinSolver.frameSlices(frame, 0f, 0f, 1000f, 1600f, 1f) { x, y, w, h -> PxRect(x, y, w, h) }.filter { it.fill < 0f }
        assertEquals(8, slices.size)
        for (s in slices) assertTrue(s.src.x >= 100f && s.src.y >= 50f && s.src.x + s.src.width <= 300f && s.src.y + s.src.height <= 310f, "inside the art: ${s.src}")
        // the slices still cover the whole frame: its right and bottom edges where the frame's are
        assertEquals(1000f, slices.maxOf { it.dst.x + it.dst.width }, 0.01f)
        assertEquals(1600f, slices.maxOf { it.dst.y + it.dst.height }, 0.01f)
    }

    @Test
    fun theOpeningGoesBlackOverTheArtLast() {
        val all = SkinSolver.frameSlices(frame, 10f, 20f, 1000f, 1600f, 1f, cornerK = 2f) { x, y, w, h -> PxRect(x, y, w, h) }
        val fill = all.last()
        assertEquals(20f * 2f, fill.fill, "its corners: the glass's, window px")
        assertEquals(PxRect(30f, 42f, 960f, 1548f), fill.dst)
        assertEquals(1, all.count { it.fill >= 0f })
    }

    @Test
    fun noOpeningNoBlack() {
        val all = SkinSolver.frameSlices(frame.copy(hole = emptyList()), 0f, 0f, 1000f, 1600f, 1f) { x, y, w, h -> PxRect(x, y, w, h) }
        assertTrue(all.none { it.fill >= 0f })
    }

    @Test
    fun aHoleWiderThanItsFrameDrawsNoBlack() {
        val all = SkinSolver.frameSlices(frame.copy(hole = listOf(600, 22, 600, 22)), 0f, 0f, 1000f, 1600f, 1f) { x, y, w, h -> PxRect(x, y, w, h) }
        assertTrue(all.none { it.fill >= 0f })
    }

    @Test
    fun cornersWiderThanTheArtStayInsideIt() {
        // 60 px of art across for corners of 48 each side: each corner shares what the art has
        val slices = SkinSolver.frameSlices(frame.copy(srcW = 60, srcH = 70), 0f, 0f, 1000f, 1600f, 1f) { x, y, w, h -> PxRect(x, y, w, h) }.filter { it.fill < 0f }
        for (s in slices) assertTrue(s.src.x >= 100f && s.src.y >= 50f && s.src.x + s.src.width <= 160f && s.src.y + s.src.height <= 120f && s.src.width > 0f && s.src.height > 0f, "inside the art: ${s.src}")
        assertEquals(1000f, slices.maxOf { it.dst.x + it.dst.width }, 0.01f)
    }

}
