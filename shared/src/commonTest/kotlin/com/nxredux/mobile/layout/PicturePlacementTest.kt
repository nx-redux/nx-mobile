package com.nxredux.mobile.layout

import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals

/** Screen Scaling Auto: every console fitted at its aspect to the space (Native is the whole-number one). */
class PicturePlacementTest {
    private val window = PxRect(0f, 0f, 2400f, 1080f)

    private fun assertRect(expected: PxRect, actual: PxRect) {
        assertEquals(expected.x, actual.x, 0.01f, "x")
        assertEquals(expected.y, actual.y, 0.01f, "y")
        assertEquals(expected.width, actual.width, 0.01f, "width")
        assertEquals(expected.height, actual.height, 0.01f, "height")
    }

    @Test fun arcade_vertical_fills_the_height() {
        val frame = CoreFrame(256f, 224f, 0.75f, rotated = true)
        assertRect(PxRect(795f, 0f, 810f, 1080f), picture(window, frame, null))   // x 795 centred
    }

    @Test fun gba_fills_the_height_not_6x() {
        val frame = CoreFrame(240f, 160f, 1.5f, rotated = false)
        assertRect(PxRect(390f, 0f, 1620f, 1080f), picture(window, frame, null))
    }

    @Test fun gba_in_portrait_takes_the_full_width() {
        // a 1080-wide phone's space above the band: 4.5x, not 4x
        val frame = CoreFrame(240f, 160f, 1.5f, rotated = false)
        assertRect(PxRect(0f, 0f, 1080f, 720f), picture(PxRect(0f, 0f, 1080f, 900f), frame, null).copy(y = 0f))
    }

    @Test fun n64_fits_space() {
        val frame = CoreFrame(640f, 480f, 4f / 3f, rotated = false)
        val space = PxRect(372f, 0f, 1656f, 1080f)
        assertRect(PxRect(372f + (1656f - 1440f) / 2f, 0f, 1440f, 1080f), picture(space, frame, null))
    }

    @Test fun overlay_box_counts_as_picture() {
        val frame = CoreFrame(256f, 224f, 4f / 3f, rotated = false)
        val space = PxRect(372f, 0f, 1656f, 1080f)
        assertRect(PxRect(372f, 74f, 1656f, 931.5f), picture(space, frame, 16f / 9f))   // y 74.25 rounded down
    }

    @Test fun position_lands_on_whole_pixels() {
        val frame = CoreFrame(320f, 240f, 4f / 3f, rotated = false)
        val r = picture(PxRect(310.25f, 0f, 1777f, 1081f), frame, null)
        assertEquals(kotlin.math.floor(r.x), r.x)
        assertEquals(kotlin.math.floor(r.y), r.y)
    }
}
