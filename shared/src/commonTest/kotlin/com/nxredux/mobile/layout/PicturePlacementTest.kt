package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import com.nxredux.mobile.options.ScreenScaling
import kotlin.test.Test
import kotlin.test.assertEquals

class PicturePlacementTest {
    private val window = PxRect(0f, 0f, 2400f, 1080f)

    private fun assertRect(expected: PxRect, actual: PxRect) {
        assertEquals(expected.x, actual.x, 0.01f, "x")
        assertEquals(expected.y, actual.y, 0.01f, "y")
        assertEquals(expected.width, actual.width, 0.01f, "width")
        assertEquals(expected.height, actual.height, 0.01f, "height")
    }

    @Test fun arcade_vertical_4x_centred() {
        val frame = CoreFrame(256f, 224f, 0.75f, rotated = true)
        val r = picture(window, frame, ConsoleScale.WHOLE_HEIGHT, ScreenScaling.AUTO, window, null)
        assertRect(PxRect(816f, 28f, 768f, 1024f), r)
    }

    @Test fun metal_slug_4x() {
        val frame = CoreFrame(304f, 224f, 4f / 3f, rotated = false)
        val r = picture(window, frame, ConsoleScale.WHOLE_HEIGHT, ScreenScaling.AUTO, window, null)
        assertEquals(1194.67f, r.width, 0.01f)
        assertEquals(896f, r.height, 0.01f)
    }

    @Test fun gba_native_6x() {
        val frame = CoreFrame(240f, 160f, 1.5f, rotated = false)
        val r = picture(window, frame, ConsoleScale.NATIVE, ScreenScaling.AUTO, window, null)
        assertRect(PxRect(480f, 60f, 1440f, 960f), r)
    }

    @Test fun fill_n64_fits_space() {
        val frame = CoreFrame(640f, 480f, 4f / 3f, rotated = false)
        val space = PxRect(372f, 0f, 1656f, 1080f)
        val r = picture(space, frame, ConsoleScale.FILL, ScreenScaling.AUTO, window, null)
        assertRect(PxRect(372f + (1656f - 1440f) / 2f, 0f, 1440f, 1080f), r)
    }

    @Test fun override_aspect_ignores_space() {
        val frame = CoreFrame(640f, 480f, 4f / 3f, rotated = false)
        val space = PxRect(0f, 0f, 500f, 1080f)
        val r = picture(space, frame, ConsoleScale.FILL, ScreenScaling.ASPECT, window, null)
        assertRect(PxRect(480f, 0f, 1440f, 1080f), r)
    }

    @Test fun controller_uses_whole_window_centred() {
        val frame = CoreFrame(304f, 224f, 4f / 3f, rotated = false)
        val r = picture(window, frame, ConsoleScale.WHOLE_HEIGHT, ScreenScaling.AUTO, window, null)
        assertRect(PxRect((2400f - 1194.67f) / 2f, 92f, 1194.67f, 896f), r)
    }

    @Test fun overlay_box_counts_as_picture() {
        val frame = CoreFrame(256f, 224f, 4f / 3f, rotated = false)
        val space = PxRect(372f, 0f, 1656f, 1080f)
        val r = picture(space, frame, ConsoleScale.WHOLE_HEIGHT, ScreenScaling.AUTO, window, 16f / 9f)
        assertRect(PxRect(372f, (1080f - 931.5f) / 2f, 1656f, 931.5f), r)
    }
}
