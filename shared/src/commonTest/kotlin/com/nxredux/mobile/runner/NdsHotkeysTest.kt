package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NdsHotkeysTest {
    private fun NdsHotkeys.down(b: NxButton) = onButton(b, down = true)
    private fun NdsHotkeys.up(b: NxButton) = onButton(b, down = false)
    private fun stylusOn() = NdsHotkeys().also { it.down(NxButton.L2); it.up(NxButton.L2) }
    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 0.001f, "expected $expected, got $actual")

    @Test fun r2_swaps_and_never_reaches_the_core() {
        val h = NdsHotkeys()
        assertEquals(NdsDecision(false, NdsAction.SwapScreens), h.down(NxButton.R2))
        assertEquals(NdsDecision(false), h.up(NxButton.R2))
    }

    @Test fun l3_and_select_l1_swap_as_on_the_3ds() {
        val h = NdsHotkeys()
        assertEquals(NdsDecision(false, NdsAction.SwapScreens), h.down(NxButton.L3))
        assertEquals(NdsDecision(false), h.up(NxButton.L3))
        assertEquals(NdsDecision(true), h.down(NxButton.L1))                    // L alone is the game's
        assertEquals(NdsDecision(true), h.up(NxButton.L1))
        h.down(NxButton.SELECT)
        assertEquals(NdsDecision(false, NdsAction.SwapScreens), h.down(NxButton.L1))
        assertEquals(NdsDecision(false), h.up(NxButton.L1))
    }

    @Test fun select_r1_toggles_the_stylus() {
        val h = NdsHotkeys()
        assertEquals(NdsDecision(true), h.down(NxButton.R1))                    // R alone is the game's
        h.up(NxButton.R1)
        h.down(NxButton.SELECT)
        assertEquals(NdsDecision(false, NdsAction.Stylus(true)), h.down(NxButton.R1))
        assertEquals(NdsDecision(false), h.up(NxButton.R1))
        assertTrue(h.stylus)
        assertEquals(NdsDecision(false, NdsAction.Stylus(false)), h.down(NxButton.R1))
        assertFalse(h.stylus)
    }

    @Test fun the_right_stick_moves_the_pen_in_stylus_mode_only() {
        val h = NdsHotkeys()
        assertFalse(h.onRightStick(1f, 0f))                                     // not taken: it goes on to the core
        val x = h.pen.x
        assertFalse(h.tick(100))
        near(x, h.pen.x)
        val s = stylusOn()
        assertTrue(s.onRightStick(1f, 0f))
        assertTrue(s.tick(100))
        near(x + NdsHotkeys.PEN_MAX_SPEED * 0.1f, s.pen.x)
        s.onRightStick(0f, 0f)
        assertFalse(s.tick(100))
    }

    @Test fun r3_taps_at_the_pen_in_stylus_mode_only() {
        val h = NdsHotkeys()
        assertEquals(NdsDecision(true), h.down(NxButton.R3))
        assertFalse(h.pen.pressed)
        h.up(NxButton.R3)
        val s = stylusOn()
        assertEquals(NdsDecision(false), s.down(NxButton.R3))
        assertTrue(s.pen.pressed)
        assertEquals(NdsDecision(false), s.up(NxButton.R3))
        assertFalse(s.pen.pressed)
    }

    @Test fun l2_toggles_the_stylus_and_never_reaches_the_core() {
        val h = NdsHotkeys()
        assertFalse(h.stylus)                                                   // off at every launch
        assertEquals(NdsDecision(false, NdsAction.Stylus(true)), h.down(NxButton.L2))
        assertEquals(NdsDecision(false), h.up(NxButton.L2))
        assertTrue(h.stylus)
        assertEquals(NdsDecision(false, NdsAction.Stylus(false)), h.down(NxButton.L2))
        assertFalse(h.stylus)
    }

    @Test fun select_left_right_cycles_the_layout_and_select_still_reaches_the_core() {
        val h = NdsHotkeys()
        assertEquals(NdsDecision(true), h.down(NxButton.SELECT))
        assertEquals(NdsDecision(false, NdsAction.CycleLayout(1)), h.down(NxButton.RIGHT))
        assertEquals(NdsDecision(false), h.up(NxButton.RIGHT))
        assertEquals(NdsDecision(false, NdsAction.CycleLayout(-1)), h.down(NxButton.LEFT))
        assertEquals(NdsDecision(false), h.up(NxButton.LEFT))
        assertEquals(NdsDecision(true), h.up(NxButton.SELECT))
        assertEquals(NdsDecision(true), h.down(NxButton.RIGHT))                 // SELECT up: Right is the game's again
    }

    // Under a pad skin that places the screens itself (the Fold's, a tablet's, portrait) SELECT + Left / Right does
    // nothing; where the skin takes the layout (a regular phone's landscape) it cycles it.
    @Test fun under_a_skin_select_left_right_cycles_only_where_the_skin_takes_the_layout() {
        val placed = NdsHotkeys().apply { skinShown = true; skinLayouts = false }
        placed.onButton(NxButton.SELECT, true)
        assertEquals(NdsDecision(false), placed.onButton(NxButton.RIGHT, true))
        val takes = NdsHotkeys().apply { skinShown = true; skinLayouts = true }
        takes.onButton(NxButton.SELECT, true)
        assertEquals(NdsDecision(false, NdsAction.CycleLayout(1)), takes.onButton(NxButton.RIGHT, true))
    }

    @Test fun select_up_down_cycle_the_corner() {
        val h = NdsHotkeys()
        h.down(NxButton.SELECT)
        assertEquals(NdsDecision(false, NdsAction.CycleCorner(1)), h.down(NxButton.DOWN))
        assertEquals(NdsDecision(false), h.up(NxButton.DOWN))
        assertEquals(NdsDecision(false, NdsAction.CycleCorner(-1)), h.down(NxButton.UP))
        assertEquals(NdsDecision(false), h.up(NxButton.UP))
        assertEquals(NdsDecision(true), h.up(NxButton.SELECT))
        assertEquals(NdsDecision(true), h.down(NxButton.DOWN))                  // SELECT up: Down is the game's again
    }

    @Test fun select_up_down_beat_stylus_mode() {
        val h = stylusOn()
        h.down(NxButton.SELECT)
        assertEquals(NdsDecision(false, NdsAction.CycleCorner(1)), h.down(NxButton.DOWN))
        assertFalse(h.tick(100))                                                // the chord does not move the pen
    }

    @Test fun select_start_is_left_to_the_menu_chord() {
        val h = NdsHotkeys()
        h.down(NxButton.SELECT)
        // Forwarded, not consumed: GameActivity checks SELECT+START before the hotkeys and opens the menu.
        assertEquals(NdsDecision(true), h.down(NxButton.START))
    }

    @Test fun a_release_follows_its_press() {
        val h = NdsHotkeys()
        assertEquals(NdsDecision(true), h.down(NxButton.LEFT))                  // before SELECT: the game has it
        h.down(NxButton.SELECT)
        assertEquals(NdsDecision(true), h.up(NxButton.LEFT))                    // so the game gets its release
        h.down(NxButton.RIGHT)                                                  // consumed by the chord
        h.up(NxButton.SELECT)
        assertTrue(h.consumes(NxButton.RIGHT))
        assertEquals(NdsDecision(false), h.up(NxButton.RIGHT))                  // its release is consumed too
        assertFalse(h.consumes(NxButton.RIGHT))

        assertEquals(NdsDecision(true), h.down(NxButton.UP))                    // before SELECT: the game has it
        h.down(NxButton.SELECT)
        assertEquals(NdsDecision(true), h.up(NxButton.UP))                      // so the game gets its release
        h.down(NxButton.DOWN)                                                   // consumed by the corner chord
        h.down(NxButton.UP)
        h.up(NxButton.SELECT)
        assertTrue(h.consumes(NxButton.DOWN))
        assertEquals(NdsDecision(false), h.up(NxButton.DOWN))                   // releases consumed after SELECT is up
        assertEquals(NdsDecision(false), h.up(NxButton.UP))
        assertEquals(NdsDecision(true), h.down(NxButton.UP))                    // and the next press is the game's
    }

    @Test fun stylus_mode_takes_the_dpad_and_a_and_forwards_the_rest() {
        val h = stylusOn()
        assertEquals(NdsDecision(false), h.down(NxButton.UP))
        assertEquals(NdsDecision(false), h.down(NxButton.A))
        assertTrue(h.pen.pressed)
        assertEquals(NdsDecision(true), h.down(NxButton.B))
        assertEquals(NdsDecision(true), h.down(NxButton.X))
        assertEquals(NdsDecision(true), h.down(NxButton.START))
        assertEquals(NdsDecision(false), h.up(NxButton.A))
        assertFalse(h.pen.pressed)
        assertEquals(NdsDecision(false), h.up(NxButton.UP))
    }

    @Test fun without_the_stylus_the_dpad_and_a_go_to_the_game() {
        val h = NdsHotkeys()
        assertEquals(NdsDecision(true), h.down(NxButton.UP))
        assertEquals(NdsDecision(true), h.down(NxButton.A))
        assertFalse(h.pen.pressed)
        assertFalse(h.tick(100))
    }

    @Test fun the_pen_moves_faster_the_longer_a_direction_is_held() {
        val h = stylusOn()
        val x0 = h.pen.x
        h.down(NxButton.RIGHT)
        assertTrue(h.tick(100))
        near(x0 + NdsHotkeys.PEN_START_SPEED * 0.1f, h.pen.x)
        val x1 = h.pen.x
        h.tick(100)
        near(x1 + (NdsHotkeys.PEN_START_SPEED + NdsHotkeys.PEN_ACCEL * 0.1f) * 0.1f, h.pen.x)
        h.tick(5_000)                                                           // long hold: capped
        h.up(NxButton.RIGHT)
        h.down(NxButton.LEFT)                                                   // a new direction starts slow again
        val x2 = h.pen.x
        h.tick(100)
        near(x2 - NdsHotkeys.PEN_START_SPEED * 0.1f, h.pen.x)
        h.up(NxButton.LEFT)
        assertFalse(h.tick(100))                                                // nothing held: no move
    }

    @Test fun the_pen_is_clamped_to_the_bottom_screen() {
        val h = stylusOn()
        h.down(NxButton.LEFT); h.down(NxButton.UP)
        h.tick(60_000)
        assertEquals(0f, h.pen.x); assertEquals(0f, h.pen.y)
        h.up(NxButton.LEFT); h.up(NxButton.UP)
        h.down(NxButton.RIGHT); h.down(NxButton.DOWN)
        h.tick(60_000)
        assertEquals(255f, h.pen.x); assertEquals(191f, h.pen.y)
        assertFalse(h.tick(100))                                                // already in the corner
    }

    @Test fun the_left_stick_moves_the_pen_in_stylus_mode_only() {
        val off = NdsHotkeys()
        assertFalse(off.onStick(1f, 0f))
        val h = stylusOn()
        val x0 = h.pen.x
        assertTrue(h.onStick(1f, 0f))
        h.tick(100)
        near(x0 + NdsHotkeys.PEN_MAX_SPEED * 0.1f, h.pen.x)
        assertTrue(h.onStick(0.1f, 0f))                                         // inside the dead zone
        assertFalse(h.tick(100))
    }

    @Test fun turning_the_stylus_on_ignores_a_stick_already_held() {
        val h = NdsHotkeys()
        h.onStick(1f, 0f)                                                       // held while stylus mode was off
        h.down(NxButton.L2)
        assertFalse(h.tick(100))                                                // the pen waits for a new stick reading
        assertTrue(h.onStick(1f, 0f))
        assertTrue(h.tick(100))
    }

    @Test fun holding_a_while_moving_drags() {
        val h = stylusOn()
        h.down(NxButton.A); h.down(NxButton.DOWN)
        assertTrue(h.tick(100))
        assertTrue(h.pen.pressed)
    }

    @Test fun turning_the_stylus_off_releases_the_pen() {
        val h = stylusOn()
        h.down(NxButton.A); h.down(NxButton.RIGHT)
        h.down(NxButton.L2)
        assertFalse(h.stylus)
        assertFalse(h.pen.pressed)
        assertFalse(h.tick(100))
        assertEquals(NdsDecision(false), h.up(NxButton.A))                      // its press was consumed
        assertEquals(NdsDecision(false), h.up(NxButton.RIGHT))
    }

    @Test fun release_all_forgets_held_buttons() {
        val h = stylusOn()
        h.down(NxButton.SELECT); h.down(NxButton.A)
        h.releaseAll()
        assertTrue(h.stylus)                                                    // the mode stays
        assertFalse(h.pen.pressed)
        assertFalse(h.consumes(NxButton.A))
        assertEquals(NdsDecision(true), h.down(NxButton.B))
        assertEquals(NdsDecision(false), h.down(NxButton.RIGHT))                // SELECT forgotten: a pen move, not a chord
    }

    @Test fun the_pen_maps_into_the_bottom_half_of_the_frame() {
        val pen = NdsPen(0f, 0f, pressed = true)
        near(0.5f / 256f, pen.frameX)
        near(0.5f + 0.5f / 192f * 0.5f, pen.frameY)
        val corner = NdsPen(255f, 191f, pressed = true)
        near(255.5f / 256f, corner.frameX)
        near(0.5f + 191.5f / 192f * 0.5f, corner.frameY)
    }

    @Test fun a_joystick_direction_is_the_hat_else_the_left_stick() {
        val h = NdsHotkeys()
        assertEquals(0, h.joystickDirection(hat = 0f, stick = 0.3f))            // inside the threshold
        assertEquals(-1, h.joystickDirection(hat = -1f, stick = 0f))
        assertEquals(1, h.joystickDirection(hat = 0f, stick = 0.9f))            // the stick stands in for a d-pad
        assertEquals(-1, h.joystickDirection(hat = -1f, stick = 0.9f))          // the hat wins
    }

    @Test fun in_stylus_mode_the_left_stick_is_the_pen_not_a_direction() {
        val h = stylusOn()
        assertEquals(0, h.joystickDirection(hat = 0f, stick = 0.9f))
        assertEquals(1, h.joystickDirection(hat = 1f, stick = 0f))
    }

    @Test fun l2_toggles_the_stylus() {
        val h = NdsHotkeys()
        assertEquals(NdsDecision(false, NdsAction.Stylus(true)), h.onButton(NxButton.L2, down = true))
        assertTrue(h.stylus)
    }
}
