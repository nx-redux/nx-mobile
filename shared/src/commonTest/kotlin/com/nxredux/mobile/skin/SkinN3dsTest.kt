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
}
