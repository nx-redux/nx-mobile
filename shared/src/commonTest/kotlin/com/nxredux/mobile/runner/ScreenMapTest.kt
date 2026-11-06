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

    @Test fun n3dsSwappedNumberingStillFindsTheTopScreen() {
        // Azahar numbers by prominence: swapped, the bottom screen is screen 0.
        val map = ScreenMap.from(listOf(view(80, 480, 640, 480, 0), view(0, 0, 800, 480, 1)), 800, 960, byShape = true)!!
        assertEquals(FrameRect(0f, 0f, 1f, 0.5f), map.src(NdsScreen.TOP))
        assertEquals(FrameRect(0.1f, 0.5f, 0.9f, 1f), map.src(NdsScreen.BOTTOM))
    }

    @Test fun rightEyeIsIgnored() {
        val map = ScreenMap.from(
            listOf(view(0, 0, 400, 240, 0, 1), view(400, 0, 400, 240, 0, 2), view(40, 240, 320, 240, 1)), 800, 480,
        )!!
        assertEquals(FrameRect(0f, 0f, 0.5f, 0.5f), map.src(NdsScreen.TOP))
    }
}

class ScreenMapRegionsTest {
    private val stack3x = ScreenMap.from(listOf(MapView(0, 0, 768, 576, 0, 0), MapView(0, 576, 768, 576, 1, 0)), 768, 1152)

    @Test fun plainStackMapLeavesDsRegionsUnchanged() {
        val regions = DualScreen.ndsPanelUp(1920f, 1080f, com.nxredux.mobile.options.NdsSettings.parse(emptyMap()))
        assertEquals(regions, regions.withMap(stack3x))
    }

    @Test fun movedBottomViewMovesOnlyTheBottomSource() {
        val gap = ScreenMap.from(listOf(MapView(0, 0, 768, 576, 0, 0), MapView(0, 624, 768, 576, 1, 0)), 768, 1200)!!
        val regions = DualScreen.ndsPanelUp(1920f, 1080f, com.nxredux.mobile.options.NdsSettings.parse(emptyMap()))
        val mapped = regions.withMap(gap)
        assertEquals(regions.map { it.dst }, mapped.map { it.dst })
        assertEquals(FrameRect(0f, 0.52f, 1f, 1f), mapped.first { it.screen == NdsScreen.BOTTOM }.src)
        assertEquals(FrameRect(0f, 0f, 1f, 0.48f), mapped.first { it.screen == NdsScreen.TOP }.src)
    }

    @Test fun noMapLeavesRegionsUnchanged() {
        val regions = DualScreen.ndsScreen(1280f, 960f, NdsScreen.BOTTOM)
        assertEquals(regions, regions.withMap(null))
    }
}
