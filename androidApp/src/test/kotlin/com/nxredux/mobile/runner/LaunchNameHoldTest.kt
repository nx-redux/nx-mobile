package com.nxredux.mobile.runner

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The launch name travels to the game screen in the intent and is drawn on the menu's pixels, kept inside the window.
 * The round trip runs on the extras' key/value encoding ([writeLaunchName] / [readLaunchName]): a Bundle needs a device.
 */
class LaunchNameHoldTest {
    private fun roundTrip(name: LaunchName): LaunchName? {
        val extras = HashMap<String, Any>()
        writeLaunchName(name) { k, v -> extras[k] = v }
        return readLaunchName { extras[it] }
    }

    @Test fun the_name_survives_the_intent_round_trip() {
        val name = LaunchName(
            listOf(LaunchLine("Super Mario", 48f, 612.5f), LaunchLine("World", 48f, 680f)),
            textSizePx = 62.4f, letterSpacingPx = -1.25f, bold = true, argb = 0xFFE4E4E7.toInt(),
        )
        assertEquals(name, roundTrip(name))
        val plain = name.copy(lines = name.lines.take(1), bold = false, argb = 0xFFB4FF39.toInt())
        assertEquals(plain, roundTrip(plain))
    }

    @Test fun no_name_extras_reads_as_null() {
        assertNull(readLaunchName { null })
        // Other launch extras only (a launch from a layout that reports no name).
        val others = mapOf<String, Any>("title" to "Tetris", "launch_id" to 7L)
        assertNull(readLaunchName { others[it] })
        // No lines: nothing to hold.
        assertNull(roundTrip(LaunchName(emptyList(), 40f, 0f, true, -1)))
    }

    @Test fun a_line_off_the_window_is_clamped_into_it() {
        // Inside: untouched.
        assertEquals(100f to 500f, clampLine(100f, 500f, 1080, 2400, 60f))
        // Past the right and bottom edges (a rotation since the capture): at least one text size stays in.
        assertEquals(1020f to 2400f, clampLine(3000f, 9000f, 1080, 2400, 60f))
        // Before the left and top edges: the line starts at 0 with its ascent inside.
        assertEquals(0f to 60f, clampLine(-40f, 10f, 1080, 2400, 60f))
        // A window smaller than the text: no crash, pinned at the start.
        assertEquals(0f to 60f, clampLine(500f, 500f, 30, 20, 60f))
    }

    @Test fun the_hold_lasts_at_least_its_minimum_from_when_it_was_shown() {
        assertEquals(LaunchTimeline_HOLD_MS, holdRemainingMs(shownAt = 1_000L, now = 1_000L))
        assertEquals(150L, holdRemainingMs(shownAt = 1_000L, now = 1_000L + LaunchTimeline_HOLD_MS - 150L))
        assertEquals(0L, holdRemainingMs(shownAt = 1_000L, now = 5_000L))   // the core took longer: fade at once
    }

    @Test fun the_hold_swallows_the_players_keys_but_volume() {
        listOf(KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT).forEach { assertTrue("$it", holdSwallows(it, 0L)) }
        assertTrue(holdSwallows(KeyEvent.KEYCODE_BUTTON_A, 60_000L))   // a hung core: only Back gets out
        listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
            .forEach { assertFalse("$it", holdSwallows(it, 0L)) }
    }

    @Test fun back_gets_out_only_after_the_give_up() {
        val giveUp = com.nxredux.mobile.ui.nx.LaunchTimeline.GIVE_UP_MS
        assertTrue(holdSwallows(KeyEvent.KEYCODE_BACK, 0L))
        assertTrue(holdSwallows(KeyEvent.KEYCODE_BACK, giveUp - 1))
        assertFalse(holdSwallows(KeyEvent.KEYCODE_BACK, giveUp))
    }

    @Test fun the_name_fades_ease_in_over_its_wall_clock_time() {
        val fade = com.nxredux.mobile.ui.nx.LaunchTimeline.NAME_FADE_MS
        assertEquals(1f, nameFadeAlpha(0L), 0f)
        assertEquals(0.75f, nameFadeAlpha(fade / 2), 0.001f)   // ease-in: slow first
        assertEquals(0f, nameFadeAlpha(fade), 0f)
        assertEquals(0f, nameFadeAlpha(fade * 3), 0f)
        assertEquals(1f, nameFadeAlpha(-5L), 0f)
    }

    @Test fun a_touch_that_starts_under_the_hold_is_swallowed_to_its_end() {
        val gate = HoldTouchGate()
        assertTrue(gate.swallows(MotionEvent.ACTION_DOWN, holdUp = true))
        assertTrue(gate.swallows(MotionEvent.ACTION_POINTER_DOWN, holdUp = true))
        assertTrue(gate.swallows(MotionEvent.ACTION_MOVE, holdUp = false))   // the hold went mid-press: still its gesture
        assertTrue(gate.swallows(MotionEvent.ACTION_UP, holdUp = false))
        assertFalse(gate.swallows(MotionEvent.ACTION_DOWN, holdUp = false))   // the next press reaches the pad
        assertFalse(gate.swallows(MotionEvent.ACTION_UP, holdUp = false))
        assertTrue(gate.swallows(MotionEvent.ACTION_DOWN, holdUp = true))
        assertTrue(gate.swallows(MotionEvent.ACTION_CANCEL, holdUp = true))
        assertFalse(gate.swallows(MotionEvent.ACTION_DOWN, holdUp = false))
    }

    @Test fun a_touch_already_going_when_the_hold_comes_up_goes_through() {
        val gate = HoldTouchGate()
        assertFalse(gate.swallows(MotionEvent.ACTION_DOWN, holdUp = false))
        assertFalse(gate.swallows(MotionEvent.ACTION_MOVE, holdUp = true))
        assertFalse(gate.swallows(MotionEvent.ACTION_UP, holdUp = true))
    }

    private companion object {
        val LaunchTimeline_HOLD_MS = com.nxredux.mobile.ui.nx.LaunchTimeline.HOLD_MS
    }
}
