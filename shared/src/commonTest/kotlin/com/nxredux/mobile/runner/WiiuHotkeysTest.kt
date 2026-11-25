package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** The 3DS hotkeys' SELECT chords on the Wii U ([N3dsHotkeys] with wiiu): SELECT is the GamePad's minus. */
class WiiuHotkeysTest {
    private fun N3dsHotkeys.down(b: NxButton) = onButton(b, down = true)
    private fun N3dsHotkeys.up(b: NxButton) = onButton(b, down = false)
    private fun wiiu() = N3dsHotkeys(wiiu = true)
    private val forward = N3dsDecision(true)
    private val keep = N3dsDecision(false)
    private val selectTap = listOf(N3dsKeyEvent(N3dsKey.SELECT, true))
    private val selectUp = listOf(N3dsKeyEvent(N3dsKey.SELECT, false))

    @Test fun select_left_right_cycles_the_layout_and_select_never_reaches_the_game() {
        val h = wiiu()
        assertEquals(keep, h.down(NxButton.SELECT))
        assertEquals(N3dsDecision(false, N3dsAction.CycleLayout(1)), h.down(NxButton.RIGHT))
        assertEquals(keep, h.up(NxButton.RIGHT))
        assertEquals(N3dsDecision(false, N3dsAction.CycleLayout(-1)), h.down(NxButton.LEFT))
        assertEquals(keep, h.up(NxButton.LEFT))
        h.up(NxButton.SELECT); h.tick(N3dsHotkeys.SELECT_TAP_MS)
        assertEquals(emptyList(), h.takeKeys())
    }

    @Test fun select_l_swaps_the_screens_as_an_action_not_l3() {
        val h = wiiu()
        h.down(NxButton.SELECT)
        assertEquals(N3dsDecision(false, N3dsAction.SwapScreens), h.down(NxButton.L1))
        assertEquals(keep, h.up(NxButton.L1))
        h.up(NxButton.SELECT); h.tick(N3dsHotkeys.SELECT_TAP_MS)
        assertEquals(emptyList(), h.takeKeys())
    }

    @Test fun select_alone_reaches_the_game_on_release() {
        val h = wiiu()
        h.down(NxButton.SELECT)
        assertEquals(emptyList(), h.takeKeys())
        h.up(NxButton.SELECT)
        assertEquals(selectTap, h.takeKeys())
        h.tick(N3dsHotkeys.SELECT_TAP_MS)
        assertEquals(selectUp, h.takeKeys())
    }

    @Test fun l3_r_and_a_are_the_games() {
        val h = wiiu()
        for (b in listOf(NxButton.L3, NxButton.R3, NxButton.A, NxButton.L1, NxButton.LEFT)) {
            assertEquals(forward, h.down(b), "$b")
            assertEquals(forward, h.up(b), "$b")
        }
        // SELECT + R is no chord on the Wii U: R goes on, and SELECT still taps on release
        h.down(NxButton.SELECT)
        assertEquals(forward, h.down(NxButton.R1))
        assertEquals(forward, h.up(NxButton.R1))
        h.up(NxButton.SELECT)
        assertEquals(selectTap, h.takeKeys())
        assertFalse(h.stylus)
        assertNull(h.cursor)
    }

    @Test fun under_a_skin_placing_the_screens_select_left_right_is_kept_and_select_l_still_swaps() {
        val h = wiiu().apply { skinShown = true }
        h.down(NxButton.SELECT)
        assertEquals(keep, h.down(NxButton.RIGHT))
        h.up(NxButton.RIGHT)
        assertEquals(N3dsDecision(false, N3dsAction.SwapScreens), h.down(NxButton.L1))
        h.up(NxButton.L1)
        val laidOut = wiiu().apply { skinShown = true; skinLayouts = true }
        laidOut.down(NxButton.SELECT)
        assertEquals(N3dsDecision(false, N3dsAction.CycleLayout(-1)), laidOut.down(NxButton.LEFT))
    }
}
