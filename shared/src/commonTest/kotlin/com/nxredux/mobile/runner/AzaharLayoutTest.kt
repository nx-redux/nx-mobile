package com.nxredux.mobile.runner

import com.nxredux.mobile.options.NdsScreen.BOTTOM
import com.nxredux.mobile.options.NdsScreen.TOP
import com.nxredux.mobile.options.PxRect
import kotlin.test.Test
import kotlin.test.assertEquals

class AzaharLayoutTest {

    private fun rect(x: Int, y: Int, w: Int, h: Int) = PxRect(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat())

    private fun frame(w: Int, h: Int, vararg placements: Placement) = Composition(w, h, placements.toList())

    @Test
    fun stackedAtOneX() {
        assertEquals(
            frame(400, 480, Placement(TOP, rect(0, 0, 400, 240)), Placement(BOTTOM, rect(40, 240, 320, 240))),
            AzaharLayout.compose("default", 1, 1f, false),
        )
    }

    @Test
    fun stackedAtThreeX() {
        assertEquals(
            frame(1200, 1440, Placement(TOP, rect(0, 0, 1200, 720)), Placement(BOTTOM, rect(120, 720, 960, 720))),
            AzaharLayout.compose("default", 3, 1f, false),
        )
    }

    // The bottom screen is the large one and narrower than the frame, so it is centred.
    @Test
    fun stackedSwappedAtOneX() {
        assertEquals(
            frame(400, 480, Placement(TOP, rect(0, 240, 400, 240)), Placement(BOTTOM, rect(40, 0, 320, 240))),
            AzaharLayout.compose("default", 1, 1f, true),
        )
    }

    @Test
    fun stackedSwappedAtThreeX() {
        assertEquals(
            frame(1200, 1440, Placement(TOP, rect(0, 720, 1200, 720)), Placement(BOTTOM, rect(120, 0, 960, 720))),
            AzaharLayout.compose("default", 3, 1f, true),
        )
    }

    @Test
    fun largeTwiceAtOneX() {
        assertEquals(
            frame(400, 360, Placement(TOP, rect(0, 0, 400, 240)), Placement(BOTTOM, rect(120, 240, 160, 120))),
            AzaharLayout.compose("default", 1, 2f, false),
        )
    }

    @Test
    fun largeTwiceAtThreeX() {
        assertEquals(
            frame(1200, 1080, Placement(TOP, rect(0, 0, 1200, 720)), Placement(BOTTOM, rect(360, 720, 480, 360))),
            AzaharLayout.compose("default", 3, 2f, false),
        )
    }

    // The frame narrows to the large bottom screen: max(320, 400 / 2) = 320.
    @Test
    fun largeTwiceSwappedAtOneX() {
        assertEquals(
            frame(320, 360, Placement(TOP, rect(60, 240, 200, 120)), Placement(BOTTOM, rect(0, 0, 320, 240))),
            AzaharLayout.compose("default", 1, 2f, true),
        )
    }

    @Test
    fun largeTwiceSwappedAtThreeX() {
        assertEquals(
            frame(960, 1080, Placement(TOP, rect(180, 720, 600, 360)), Placement(BOTTOM, rect(0, 0, 960, 720))),
            AzaharLayout.compose("default", 3, 2f, true),
        )
    }

    // Small screen: 320 / 1.37 = 233.58 -> 233, 240 / 1.37 = 175.18 -> 175. x = 400 / 2 - 233 / 2 = 84.
    @Test
    fun largeFractionalAtOneX() {
        assertEquals(
            frame(400, 415, Placement(TOP, rect(0, 0, 400, 240)), Placement(BOTTOM, rect(84, 240, 233, 175))),
            AzaharLayout.compose("default", 1, 1.37f, false),
        )
    }

    // The truncated 233 x 175 is scaled after truncation (699 x 525), and x = 1200 / 2 - 699 / 2 = 251, not 3 * 84.
    @Test
    fun largeFractionalAtThreeX() {
        assertEquals(
            frame(1200, 1245, Placement(TOP, rect(0, 0, 1200, 720)), Placement(BOTTOM, rect(251, 720, 699, 525))),
            AzaharLayout.compose("default", 3, 1.37f, false),
        )
    }

    // Small top screen: 400 / 1.37 = 291.97 -> 291; x = 320 / 2 - 291 / 2 = 15.
    @Test
    fun largeFractionalSwappedAtOneX() {
        assertEquals(
            frame(320, 415, Placement(TOP, rect(15, 240, 291, 175)), Placement(BOTTOM, rect(0, 0, 320, 240))),
            AzaharLayout.compose("default", 1, 1.37f, true),
        )
    }

    @Test
    fun largeFractionalSwappedAtThreeX() {
        assertEquals(
            frame(960, 1245, Placement(TOP, rect(44, 720, 873, 525)), Placement(BOTTOM, rect(0, 0, 960, 720))),
            AzaharLayout.compose("default", 3, 1.37f, true),
        )
    }

    @Test
    fun singleTopScreen() {
        assertEquals(frame(400, 240, Placement(TOP, rect(0, 0, 400, 240))), AzaharLayout.compose("single_screen", 1, 1f, false))
        assertEquals(frame(1200, 720, Placement(TOP, rect(0, 0, 1200, 720))), AzaharLayout.compose("single_screen", 3, 1f, false))
    }

    @Test
    fun singleBottomScreenWhenSwapped() {
        assertEquals(frame(320, 240, Placement(BOTTOM, rect(0, 0, 320, 240))), AzaharLayout.compose("single_screen", 1, 1f, true))
        assertEquals(frame(960, 720, Placement(BOTTOM, rect(0, 0, 960, 720))), AzaharLayout.compose("single_screen", 3, 1f, true))
    }

    @Test
    fun singleIgnoresProportion() {
        assertEquals(AzaharLayout.compose("single_screen", 3, 1f, false), AzaharLayout.compose("single_screen", 3, 2f, false))
    }

    @Test
    fun sideBySide() {
        assertEquals(
            frame(720, 240, Placement(TOP, rect(0, 0, 400, 240)), Placement(BOTTOM, rect(400, 0, 320, 240))),
            AzaharLayout.compose("side_by_side", 1, 1f, false),
        )
        assertEquals(
            frame(2160, 720, Placement(TOP, rect(0, 0, 1200, 720)), Placement(BOTTOM, rect(1200, 0, 960, 720))),
            AzaharLayout.compose("side_by_side", 3, 1f, false),
        )
    }

    @Test
    fun sideBySideSwapped() {
        assertEquals(
            frame(720, 240, Placement(TOP, rect(320, 0, 400, 240)), Placement(BOTTOM, rect(0, 0, 320, 240))),
            AzaharLayout.compose("side_by_side", 1, 1f, true),
        )
        assertEquals(
            frame(2160, 720, Placement(TOP, rect(960, 0, 1200, 720)), Placement(BOTTOM, rect(0, 0, 960, 720))),
            AzaharLayout.compose("side_by_side", 3, 1f, true),
        )
    }

    @Test
    fun sideBySideIgnoresProportion() {
        assertEquals(AzaharLayout.compose("side_by_side", 3, 1f, true), AzaharLayout.compose("side_by_side", 3, 2f, true))
    }

    @Test
    fun unknownLayoutIsDefault() {
        assertEquals(AzaharLayout.compose("default", 3, 2f, true), AzaharLayout.compose("hybrid_screen", 3, 2f, true))
    }

    @Test
    fun factorTenStacked() {
        assertEquals(
            frame(4000, 4800, Placement(TOP, rect(0, 0, 4000, 2400)), Placement(BOTTOM, rect(400, 2400, 3200, 2400))),
            AzaharLayout.compose("default", 10, 1f, false),
        )
    }
}
