package com.nxredux.mobile.runner

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class N3dsHotkeysTest {
    private fun N3dsHotkeys.down(b: NxButton) = onButton(b, down = true)
    private fun N3dsHotkeys.up(b: NxButton) = onButton(b, down = false)
    private fun N3dsHotkeys.chordStylus() = also {
        down(NxButton.SELECT); down(NxButton.L1); up(NxButton.L1); up(NxButton.SELECT); takeKeys()
    }
    private fun stylusOn() = N3dsHotkeys().chordStylus()
    private fun near(expected: Float, actual: Float) = assertTrue(abs(expected - actual) < 0.001f, "expected $expected, got $actual")
    private fun press(k: N3dsKey) = N3dsKeyEvent(k, true)
    private fun release(k: N3dsKey) = N3dsKeyEvent(k, false)
    private val forward = N3dsDecision(true)
    private val keep = N3dsDecision(false)

    // ---- SELECT, held back for chords

    // ---- under a pad skin: the touch screen is under the finger and the skin places the screens

    @Test fun under_a_skin_select_l_does_nothing() {
        val h = N3dsHotkeys().apply { skinShown = true }
        h.down(NxButton.SELECT)
        assertEquals(keep, h.down(NxButton.L1))                                 // kept from the core, no Stylus action
        assertFalse(h.stylus)
        h.up(NxButton.L1); h.up(NxButton.SELECT); h.tick(N3dsHotkeys.SELECT_TAP_MS)
        assertEquals(emptyList(), h.takeKeys())                                 // a chord: no SELECT tap
    }

    // A regular phone's landscape skin lays the screens out by the player's layout (user): SELECT + Left / Right cycles it
    @Test fun under_a_skin_that_takes_the_layout_select_left_right_cycles_it() {
        val h = N3dsHotkeys().apply { skinShown = true; skinLayouts = true }
        h.down(NxButton.SELECT)
        assertEquals(N3dsDecision(false, N3dsAction.CycleLayout(1)), h.down(NxButton.RIGHT))
        h.up(NxButton.RIGHT)
        assertEquals(N3dsDecision(false, N3dsAction.CycleLayout(-1)), h.down(NxButton.LEFT))
        h.up(NxButton.LEFT)
        assertEquals(keep, h.down(NxButton.L1))   // the stylus still not, under a skin
        assertFalse(h.stylus)
    }

    @Test fun under_a_skin_select_left_right_does_nothing() {
        val h = N3dsHotkeys().apply { skinShown = true }
        h.down(NxButton.SELECT)
        assertEquals(keep, h.down(NxButton.RIGHT))
        h.up(NxButton.RIGHT)
        assertEquals(keep, h.down(NxButton.LEFT))
        h.up(NxButton.LEFT); h.up(NxButton.SELECT); h.tick(N3dsHotkeys.SELECT_TAP_MS)
        assertEquals(emptyList(), h.takeKeys())
    }

    @Test fun under_a_skin_select_r_still_swaps() {
        val h = N3dsHotkeys().apply { skinShown = true }
        h.down(NxButton.SELECT)
        assertEquals(keep, h.down(NxButton.R1))
        h.up(NxButton.R1); h.up(NxButton.SELECT)
        assertEquals(listOf(press(N3dsKey.L3), release(N3dsKey.L3)), h.takeKeys())
    }

    @Test fun a_select_tap_reaches_the_core_as_a_short_tap_on_release() {
        val h = N3dsHotkeys()
        assertEquals(keep, h.down(NxButton.SELECT))
        assertTrue(h.ticking)                                                   // the hold is timed for the cancel
        h.tick(100)
        assertEquals(emptyList(), h.takeKeys())                                 // nothing yet: it may start a chord
        assertEquals(keep, h.up(NxButton.SELECT))
        assertEquals(listOf(press(N3dsKey.SELECT)), h.takeKeys())
        h.tick(N3dsHotkeys.SELECT_TAP_MS - 1)
        assertEquals(emptyList(), h.takeKeys())                                 // held for a few frames
        h.tick(1)
        assertEquals(listOf(release(N3dsKey.SELECT)), h.takeKeys())
        assertFalse(h.ticking)
    }

    @Test fun a_chord_consumes_select_for_that_hold() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        assertEquals(N3dsDecision(false, N3dsAction.CycleLayout(1)), h.down(NxButton.RIGHT))
        assertEquals(keep, h.up(NxButton.RIGHT))
        h.tick(1_000)                                                           // a long chord never forwards SELECT
        assertEquals(N3dsDecision(false, N3dsAction.CycleLayout(-1)), h.down(NxButton.LEFT))
        h.up(NxButton.LEFT)
        h.up(NxButton.SELECT)
        h.tick(1_000)
        assertEquals(emptyList(), h.takeKeys())
        assertEquals(forward, h.down(NxButton.RIGHT))                           // SELECT up: Right is the game's again
    }

    @Test fun select_held_alone_up_to_the_cancel_threshold_still_taps_on_release() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.tick(N3dsHotkeys.SELECT_CANCEL_MS - 16)
        h.tick(16)                                                              // held exactly the threshold
        assertTrue(h.ticking)                                                   // not past it yet: still timing
        assertEquals(emptyList(), h.takeKeys())                                 // deciding on a chord: no Select yet
        assertEquals(keep, h.up(NxButton.SELECT))
        assertEquals(listOf(press(N3dsKey.SELECT)), h.takeKeys())               // at the threshold: a tap on release
        h.tick(N3dsHotkeys.SELECT_TAP_MS)
        assertEquals(listOf(release(N3dsKey.SELECT)), h.takeKeys())
        assertFalse(h.ticking)
    }

    @Test fun select_held_past_the_cancel_threshold_sends_nothing_on_release() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.tick(N3dsHotkeys.SELECT_CANCEL_MS)
        h.tick(1)                                                               // one ms past it: cancelled
        assertFalse(h.ticking)                                                  // nothing left to time
        assertEquals(keep, h.up(NxButton.SELECT))
        h.tick(1_000)
        assertEquals(emptyList(), h.takeKeys())
        assertFalse(h.ticking)
        h.down(NxButton.SELECT)                                                 // the next hold is timed afresh
        h.tick(N3dsHotkeys.SELECT_CANCEL_MS)
        h.up(NxButton.SELECT)
        assertEquals(listOf(press(N3dsKey.SELECT)), h.takeKeys())
    }

    @Test fun a_cancelled_hold_can_still_chord() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.tick(5_000)
        assertEquals(keep, h.down(NxButton.R1))
        assertEquals(listOf(press(N3dsKey.L3)), h.takeKeys())
        h.up(NxButton.R1)
        h.up(NxButton.SELECT)
        assertEquals(listOf(release(N3dsKey.L3)), h.takeKeys())
    }

    @Test fun ticks_while_select_is_held_never_count_toward_a_tap_already_running() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.up(NxButton.SELECT)                                                   // tap 1 running
        h.down(NxButton.SELECT)                                                 // ends it, a new hold starts
        h.takeKeys()
        h.tick(N3dsHotkeys.SELECT_CANCEL_MS + 1)
        h.up(NxButton.SELECT)
        assertEquals(emptyList(), h.takeKeys())                                 // the new hold was the long one
    }

    @Test fun zl_zr_while_select_is_held_go_to_the_core_and_select_still_taps() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        assertEquals(forward, h.down(NxButton.L2))                              // a trigger is not a chord
        assertEquals(forward, h.down(NxButton.R2))
        assertEquals(forward, h.up(NxButton.L2))
        assertEquals(forward, h.up(NxButton.R2))
        assertEquals(keep, h.up(NxButton.SELECT))
        assertEquals(listOf(press(N3dsKey.SELECT)), h.takeKeys())               // SELECT was not consumed
    }

    @Test fun a_long_hold_then_a_chord_delivers_no_select() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.tick(5_000)
        assertEquals(N3dsDecision(false, N3dsAction.CycleLayout(1)), h.down(NxButton.RIGHT))
        h.up(NxButton.RIGHT)
        h.up(NxButton.SELECT)
        h.tick(1_000)
        assertEquals(emptyList(), h.takeKeys())
    }

    @Test fun select_released_mid_chord_ends_the_chord_and_the_held_button_goes_where_it_went() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.down(NxButton.R1)
        assertEquals(listOf(press(N3dsKey.L3)), h.takeKeys())
        h.up(NxButton.SELECT)
        assertEquals(emptyList(), h.takeKeys())                                 // no Select tap after a chord
        assertEquals(keep, h.up(NxButton.R1))
        assertEquals(listOf(release(N3dsKey.L3)), h.takeKeys())                 // released as it was pressed
        assertEquals(forward, h.down(NxButton.R1))                              // R alone is the game's
    }

    @Test fun select_with_other_buttons_lets_them_through_and_still_taps() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        assertEquals(forward, h.down(NxButton.UP))
        assertEquals(forward, h.down(NxButton.A))
        assertEquals(forward, h.up(NxButton.A))
        h.up(NxButton.SELECT)
        assertEquals(listOf(press(N3dsKey.SELECT)), h.takeKeys())
    }

    @Test fun a_new_select_during_a_tap_ends_the_tap_first() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.up(NxButton.SELECT)
        h.down(NxButton.SELECT)
        assertEquals(listOf(press(N3dsKey.SELECT), release(N3dsKey.SELECT)), h.takeKeys())
        h.down(NxButton.L1)                                                     // and the new hold can be a chord
        assertTrue(h.stylus)
    }

    @Test fun select_start_the_menu_chord_keeps_select_from_the_core() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        assertEquals(forward, h.down(NxButton.START))                           // the host opens the menu instead
        h.up(NxButton.START)
        h.up(NxButton.SELECT)
        h.tick(1_000)
        assertEquals(emptyList(), h.takeKeys())
        assertEquals(forward, h.down(NxButton.START))                           // START alone is the game's
    }

    @Test fun the_menu_opening_mid_hold_clears_the_held_back_select() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.releaseAll()                                                          // the host's SELECT + START opened the menu
        h.up(NxButton.SELECT)
        h.tick(1_000)
        assertEquals(emptyList(), h.takeKeys())                                 // no tap on the release that follows
        assertFalse(h.ticking)
    }

    @Test fun select_repeats_are_always_kept() {
        assertTrue(N3dsHotkeys().consumes(NxButton.SELECT))
    }

    // ---- the chords

    @Test fun select_r_goes_to_the_core_as_l3_the_swap_screens_button() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        assertEquals(keep, h.down(NxButton.R1))
        assertEquals(listOf(press(N3dsKey.L3)), h.takeKeys())
        assertTrue(h.consumes(NxButton.R1))
        h.up(NxButton.R1)
        assertEquals(listOf(release(N3dsKey.L3)), h.takeKeys())
        assertFalse(h.consumes(NxButton.R1))
    }

    @Test fun l2_r2_are_zl_zr_and_always_reach_the_core() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        for (b in listOf(NxButton.L2, NxButton.R2)) {
            assertEquals(forward, h.down(b))
            assertEquals(forward, h.up(b))
        }
        val s = stylusOn()
        assertEquals(forward, s.down(NxButton.L2))
        assertEquals(forward, s.down(NxButton.R2))
    }

    @Test fun select_r_is_l3_in_stylus_mode_too() {
        val h = stylusOn()
        h.down(NxButton.SELECT)
        h.down(NxButton.R1)
        h.up(NxButton.R1)
        assertEquals(listOf(press(N3dsKey.L3), release(N3dsKey.L3)), h.takeKeys())
    }

    @Test fun select_l_toggles_the_stylus_and_l_never_reaches_the_core() {
        val h = N3dsHotkeys()
        assertFalse(h.stylus)                                                   // off at every launch
        h.down(NxButton.SELECT)
        assertEquals(N3dsDecision(false, N3dsAction.Stylus(true)), h.down(NxButton.L1))
        assertEquals(keep, h.up(NxButton.L1))
        assertTrue(h.stylus)
        assertEquals(N3dsDecision(false, N3dsAction.Stylus(false)), h.down(NxButton.L1))
        assertFalse(h.stylus)
        h.up(NxButton.L1)
        h.up(NxButton.SELECT)
        assertEquals(emptyList(), h.takeKeys())
        assertEquals(forward, h.down(NxButton.L1))                              // L alone is the game's
    }

    @Test fun a_release_goes_where_its_press_went() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.down(NxButton.RIGHT)                                                  // consumed as a chord
        h.up(NxButton.SELECT)
        assertEquals(keep, h.up(NxButton.RIGHT))                                // never pressed in the core: kept out
        h.down(NxButton.LEFT)
        h.down(NxButton.SELECT)
        assertEquals(forward, h.up(NxButton.LEFT))                              // pressed in the core: released there
    }

    // ---- stylus mode

    @Test fun no_cursor_outside_stylus_mode() {
        val h = N3dsHotkeys()
        assertEquals(forward, h.down(NxButton.RIGHT))
        assertEquals(forward, h.down(NxButton.A))
        assertFalse(h.onStick(1f, 0f))
        assertFalse(h.tick(100))
        assertNull(h.cursor)
    }

    @Test fun stylus_a_touches_as_r3_and_its_release_follows() {
        val h = stylusOn()
        assertEquals(keep, h.down(NxButton.A))
        assertEquals(keep, h.up(NxButton.A))
        assertEquals(listOf(press(N3dsKey.R3), release(N3dsKey.R3)), h.takeKeys())
        assertEquals(forward, h.down(NxButton.B))                               // other buttons pass through
    }

    @Test fun leaving_stylus_mode_while_a_is_held_lifts_the_touch_and_keeps_its_release() {
        val h = stylusOn()
        h.down(NxButton.A)
        h.down(NxButton.SELECT)
        h.down(NxButton.L1)
        assertEquals(listOf(press(N3dsKey.R3), release(N3dsKey.R3)), h.takeKeys())
        assertEquals(keep, h.up(NxButton.A))
        assertEquals(emptyList(), h.takeKeys())
    }

    @Test fun stylus_dpad_drives_the_right_stick_from_just_past_the_deadzone_and_speeds_up() {
        val h = stylusOn()
        h.deadzone = 0.15f
        assertEquals(keep, h.down(NxButton.RIGHT))
        assertTrue(h.ticking)
        val start = h.cursor!!
        near(0.15f + N3dsHotkeys.CURSOR_START, start.first)
        near(0f, start.second)
        assertTrue(h.tick(500))
        near(0.15f + N3dsHotkeys.CURSOR_START + N3dsHotkeys.CURSOR_ACCEL * 0.5f, h.cursor!!.first)
        h.tick(10_000)
        near(1f, h.cursor!!.first)                                              // capped at full deflection
        assertFalse(h.tick(100))                                                // nothing changes at the cap
        h.down(NxButton.UP)
        near(-1f, h.cursor!!.second)                                            // a diagonal keeps the speed held
        h.up(NxButton.RIGHT)
        h.up(NxButton.UP)
        assertNull(h.cursor)                                                    // released: the hardware right stick again
        assertFalse(h.ticking)
        h.down(NxButton.DOWN)
        near(0.15f + N3dsHotkeys.CURSOR_START, h.cursor!!.second)               // a fresh press starts slow again
    }

    @Test fun stylus_left_stick_goes_as_the_right_stick_as_is() {
        val h = stylusOn()
        assertTrue(h.onStick(0.5f, -0.25f))                                     // taken: not the circle pad
        assertEquals(0.5f to -0.25f, h.cursor)
        h.onStick(0f, 0f)
        assertNull(h.cursor)
    }

    @Test fun the_dpad_wins_over_the_stick() {
        val h = stylusOn()
        h.onStick(0.5f, 0.5f)
        h.down(NxButton.LEFT)
        near(-(N3dsHotkeys.DEFAULT_DEADZONE + N3dsHotkeys.CURSOR_START), h.cursor!!.first)
        near(0f, h.cursor!!.second)
    }

    @Test fun turning_the_stylus_on_or_off_stops_the_cursor() {
        val h = N3dsHotkeys()
        h.onStick(1f, 0f)
        h.chordStylus()
        assertNull(h.cursor)                                                    // a stick already held does not move it at once
        h.down(NxButton.RIGHT)
        h.chordStylus()
        assertNull(h.cursor)
    }

    // ---- menu / pause

    @Test fun release_all_releases_every_key_sent_and_keeps_the_mode() {
        val h = stylusOn()
        h.down(NxButton.A)
        h.down(NxButton.SELECT)
        h.tick(1_000)                                                           // SELECT held back, never in the core
        h.down(NxButton.R1)
        h.down(NxButton.RIGHT)                                                  // a chord: cycles, no cursor
        h.takeKeys()
        h.releaseAll()
        assertEquals(
            setOf(release(N3dsKey.R3), release(N3dsKey.L3)),
            h.takeKeys().toSet(),
        )
        assertTrue(h.stylus)
        assertNull(h.cursor)
        assertFalse(h.ticking)
        assertEquals(forward, h.up(NxButton.RIGHT))
        assertFalse(h.consumes(NxButton.RIGHT))
        assertEquals(keep, h.down(NxButton.RIGHT))                              // SELECT forgotten: the cursor, not a cycle
        h.up(NxButton.SELECT)
        assertEquals(emptyList(), h.takeKeys())                                 // a release after it sends nothing
    }

    @Test fun release_all_ends_a_tap_in_progress() {
        val h = N3dsHotkeys()
        h.down(NxButton.SELECT)
        h.up(NxButton.SELECT)
        h.takeKeys()
        h.releaseAll()
        assertEquals(listOf(release(N3dsKey.SELECT)), h.takeKeys())
        h.tick(100)
        assertEquals(emptyList(), h.takeKeys())
    }

    @Test fun joystick_directions_come_from_the_hat_only() {
        val h = N3dsHotkeys()
        assertEquals(1, h.joystickDirection(1f))
        assertEquals(-1, h.joystickDirection(-0.8f))
        assertEquals(0, h.joystickDirection(0.3f))
    }

    @Test fun the_stylus_pill_toggles_like_select_l1() {
        val h = N3dsHotkeys()
        assertEquals(N3dsAction.Stylus(true), h.toggleStylus())
        assertTrue(h.stylus)
        assertEquals(N3dsAction.Stylus(false), h.toggleStylus())
        assertFalse(h.stylus)
        assertEquals(emptyList(), h.takeKeys())   // nothing held, nothing to lift
    }
}
