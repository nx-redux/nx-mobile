package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenMapTest {
    private fun view(x: Int, y: Int, w: Int, h: Int, screen: Int, eye: Int = 0) = MapView(x, y, w, h, screen, eye)

    @Test fun dsStackAt3x() {
        val map = ScreenMap.from(listOf(view(0, 0, 768, 576, 0), view(0, 576, 768, 576, 1)), 768, 1152)!!
        assertEquals(FrameRect(0f, 0f, 1f, 0.5f), map.src(NdsScreen.TOP))
        assertEquals(FrameRect(0f, 0.5f, 1f, 1f), map.src(NdsScreen.BOTTOM))
        assertEquals(768 to 576, map.topSizePx)
    }

    @Test fun n3dsStackAt2x() {
        val map = ScreenMap.from(listOf(view(0, 0, 800, 480, 0), view(80, 480, 640, 480, 1)), 800, 960)!!
        assertEquals(FrameRect(0f, 0f, 1f, 0.5f), map.src(NdsScreen.TOP))
        assertEquals(FrameRect(0.1f, 0.5f, 0.9f, 1f), map.src(NdsScreen.BOTTOM))
        assertEquals(640 to 480, map.bottomSizePx)
    }

    @Test fun rectOutsideFrameIsNull() {
        assertNull(ScreenMap.from(listOf(view(0, 0, 768, 576, 0), view(0, 576, 768, 576, 1)), 768, 1151))
    }

    @Test fun missingScreenIsNull() {
        assertNull(ScreenMap.from(listOf(view(0, 0, 768, 576, 0)), 768, 1152))
    }

    @Test fun emptyIsNull() {
        assertNull(ScreenMap.from(emptyList(), 768, 1152))
    }

    @Test fun rightEyeIsIgnored() {
        val map = ScreenMap.from(
            listOf(view(0, 0, 400, 240, 0, 1), view(400, 0, 400, 240, 0, 2), view(40, 240, 320, 240, 1)), 800, 480,
        )!!
        assertEquals(FrameRect(0f, 0f, 0.5f, 0.5f), map.src(NdsScreen.TOP))
    }
}
