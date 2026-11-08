package com.nxredux.mobile.skin

import com.nxredux.mobile.options.NdsScreen
import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.runner.DualScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The 3DS screens in a pad skin's glasses: the 5:3 top and 4:3 touch screen, each at its own shape. */
class SkinN3dsTest {
    @Test
    fun plainStackIs400By480() {
        assertTrue(SkinN3ds.isPlainStack(400, 480))
        assertTrue(SkinN3ds.isPlainStack(1200, 1440))
        assertFalse(SkinN3ds.isPlainStack(256, 384))
        assertFalse(SkinN3ds.isPlainStack(0, 0))
        assertFalse(SkinN3ds.isPlainStack(800, 240))
    }

    @Test
    fun stackGlassGivesTopOverTouch() {
        val r = SkinN3ds.regions(PxRect(0f, 0f, 500f, 600f), null, null, NdsScreen.TOP)!!
        val top = r.single { it.screen == NdsScreen.TOP }
        val bottom = r.single { it.screen == NdsScreen.BOTTOM }
        assertEquals(PxRect(0f, 0f, 500f, 300f), top.dst)
        assertEquals(PxRect(50f, 300f, 400f, 300f), bottom.dst)
        assertEquals(DualScreen.n3dsSrc(NdsScreen.TOP), top.src)
        assertEquals(DualScreen.n3dsSrc(NdsScreen.BOTTOM), bottom.src)
        assertTrue(bottom.touch); assertFalse(top.touch)
        assertTrue(top.shaded); assertFalse(bottom.shaded)
    }

    // A stack glass wider than the stack (landscape): the top screen across the glass's whole width at its top, the
    // touch screen in the height left under it.
    @Test
    fun aWideStackGlassGivesTheTopItsWholeWidth() {
        val r = SkinN3ds.regions(PxRect(0f, 0f, 976f, 1094f), null, null, NdsScreen.TOP)!!
        val top = r.single { it.screen == NdsScreen.TOP }.dst
        val bottom = r.single { it.screen == NdsScreen.BOTTOM }.dst
        assertEquals(0f, top.x); assertEquals(0f, top.y); assertEquals(976f, top.width)
        assertEquals(585.6f, top.height, 0.01f)
        assertEquals(585.6f, bottom.y, 0.01f)
        assertEquals(508.4f, bottom.height, 0.01f)
        assertEquals(508.4f * 4f / 3f, bottom.width, 0.01f)
        assertEquals((976f - bottom.width) / 2f, bottom.x, 0.01f)
    }

    @Test
    fun aVeryWideStackGlassKeepsRoomForTheTouchScreen() {   // the top screen at most 60% of the height
        val r = SkinN3ds.regions(PxRect(0f, 0f, 1200f, 600f), null, null, NdsScreen.TOP)!!
        assertEquals(PxRect(300f, 0f, 600f, 360f), r.single { it.screen == NdsScreen.TOP }.dst)
        assertEquals(PxRect(440f, 360f, 320f, 240f), r.single { it.screen == NdsScreen.BOTTOM }.dst)
    }

    // A regular phone's landscape glass: the player's layout in it.
    @Test
    fun sideBySideInAStackGlass() {   // the big screen left, both one height
        val r = SkinN3ds.regions(PxRect(0f, 0f, 1440f, 600f), null, null, NdsScreen.TOP, layout = "side_by_side")!!
        val top = r.single { it.screen == NdsScreen.TOP }.dst; val bottom = r.single { it.screen == NdsScreen.BOTTOM }.dst
        assertEquals(PxRect(0f, 60f, 800f, 480f), top)
        assertEquals(PxRect(800f, 60f, 640f, 480f), bottom)
        assertTrue(r.single { it.screen == NdsScreen.BOTTOM }.touch)
    }

    @Test
    fun sideBySideSwappedPutsTheTouchScreenLeft() {
        val r = SkinN3ds.regions(PxRect(0f, 0f, 1440f, 600f), null, null, NdsScreen.BOTTOM, layout = "side_by_side")!!
        assertEquals(0f, r.single { it.screen == NdsScreen.BOTTOM }.dst.x)
        assertEquals(640f, r.single { it.screen == NdsScreen.TOP }.dst.x)
    }

    @Test
    fun stackedInAStackGlassIsOneSize() {   // one above the other at the same scale, centred
        val r = SkinN3ds.regions(PxRect(0f, 0f, 1440f, 600f), null, null, NdsScreen.TOP, layout = "stacked")!!
        val top = r.single { it.screen == NdsScreen.TOP }.dst; val bottom = r.single { it.screen == NdsScreen.BOTTOM }.dst
        assertEquals(top.height, bottom.height, 0.01f); assertEquals(600f, top.height + bottom.height, 0.01f)
        assertEquals(500f, top.width, 0.01f); assertEquals(400f, bottom.width, 0.01f)
    }

    @Test
    fun singleScreenInAStackGlassIsTheBigOneAlone() {
        val r = SkinN3ds.regions(PxRect(0f, 0f, 1440f, 600f), null, null, NdsScreen.TOP, layout = "single_screen")!!
        assertEquals(listOf(NdsScreen.TOP), r.map { it.screen })
        assertEquals(PxRect(220f, 0f, 1000f, 600f), r.single().dst)
    }

    @Test
    fun topAndTouchGlasses() {
        val r = SkinN3ds.regions(null, PxRect(0f, 0f, 600f, 300f), PxRect(100f, 320f, 400f, 300f), NdsScreen.TOP)!!
        assertEquals(PxRect(50f, 0f, 500f, 300f), r.single { it.screen == NdsScreen.TOP }.dst)
        assertEquals(PxRect(100f, 320f, 400f, 300f), r.single { it.screen == NdsScreen.BOTTOM }.dst)
    }

    @Test
    fun swappedScreensKeepTheirAspects() {
        val r = SkinN3ds.regions(PxRect(0f, 0f, 500f, 600f), null, null, NdsScreen.BOTTOM)!!
        assertEquals(PxRect(50f, 0f, 400f, 300f), r.single { it.screen == NdsScreen.BOTTOM }.dst)
        assertEquals(PxRect(0f, 300f, 500f, 300f), r.single { it.screen == NdsScreen.TOP }.dst)
    }

    @Test
    fun noGlassesNoRegions() {
        assertNull(SkinN3ds.regions(null, PxRect(0f, 0f, 600f, 300f), null, NdsScreen.TOP))
    }

    @Test
    fun notAPlainStackGivesTheWholePicture() {
        assertEquals(PxRect(0f, 225f, 500f, 150f), SkinN3ds.whole(PxRect(0f, 0f, 500f, 600f), null, 800, 240))
        assertNull(SkinN3ds.whole(null, null, 800, 240))
    }

    @Test
    fun theStackIsCutOnlyWhileTheCoreIsPinnedToIt() {   // netplay keeps the player's layout: maybe swapped, drawn whole
        assertTrue(SkinN3ds.cutsStack(400, 480, pinned = true))
        assertFalse(SkinN3ds.cutsStack(400, 480, pinned = false))
        assertFalse(SkinN3ds.cutsStack(800, 240, pinned = true))
    }
}
