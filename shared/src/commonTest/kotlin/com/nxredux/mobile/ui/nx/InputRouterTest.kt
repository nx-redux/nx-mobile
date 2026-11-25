package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class InputRouterTest {
    @Test fun press_fires_once_on_down_and_repeats_while_held_for_directions() = runTest {
        val presses = ArrayList<NxButton>()
        val router = InputRouter(this, { presses += it }, initialDelayMs = 400, repeatMs = 90)
        router.onEvent(PadEvent.Down(NxButton.DOWN)); runCurrent()
        assertEquals(listOf(NxButton.DOWN), presses)
        advanceTimeBy(399); runCurrent()
        assertEquals(1, presses.size)
        advanceTimeBy(1); runCurrent()
        assertEquals(2, presses.size)
        advanceTimeBy(180); runCurrent()
        assertEquals(4, presses.size)
        router.onEvent(PadEvent.Up(NxButton.DOWN)); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(4, presses.size)
    }
    @Test fun non_direction_buttons_do_not_repeat() = runTest {
        val presses = ArrayList<NxButton>()
        val router = InputRouter(this, { presses += it })
        router.onEvent(PadEvent.Down(NxButton.A)); runCurrent()
        advanceTimeBy(2000); runCurrent()
        router.onEvent(PadEvent.Up(NxButton.A)); runCurrent()
        assertEquals(listOf(NxButton.A), presses)
    }
    @Test fun a_second_down_for_a_held_button_rearms_the_repeat() = runTest {
        val presses = ArrayList<NxButton>()
        val router = InputRouter(this, { presses += it }, initialDelayMs = 400, repeatMs = 90)
        router.onEvent(PadEvent.Down(NxButton.UP)); runCurrent()
        assertEquals(1, presses.size)                 // first press fires
        advanceTimeBy(300); runCurrent()
        assertEquals(1, presses.size)                 // still inside the initial delay, no repeat yet
        router.onEvent(PadEvent.Down(NxButton.UP)); runCurrent()
        assertEquals(2, presses.size)                 // second Down re-fires the press
        advanceTimeBy(399); runCurrent()
        assertEquals(2, presses.size)                 // initial delay restarted from the second Down
        advanceTimeBy(1); runCurrent()
        assertEquals(3, presses.size)                 // first repeat after the restarted delay
        advanceTimeBy(180); runCurrent()
        assertEquals(5, presses.size)                 // two more repeats at 90 ms
        router.onEvent(PadEvent.Up(NxButton.UP)); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertEquals(5, presses.size)                 // stops after Up
    }
    @Test fun release_all_after_a_down_stops_further_presses() = runTest {
        val presses = ArrayList<NxButton>()
        val router = InputRouter(this, { presses += it }, initialDelayMs = 400, repeatMs = 90)
        router.onEvent(PadEvent.Down(NxButton.UP)); runCurrent()
        assertEquals(1, presses.size)
        router.releaseAll(); runCurrent()
        advanceTimeBy(2000); runCurrent()
        assertEquals(1, presses.size)                 // no repeats survive releaseAll
        router.onEvent(PadEvent.Down(NxButton.UP)); runCurrent()
        assertEquals(2, presses.size)                 // held state was cleared, a later Down works again
        router.onEvent(PadEvent.Up(NxButton.UP)); runCurrent()
    }
    @Test fun repeats_go_to_on_repeat_and_the_first_press_to_on_press() = runTest {
        val presses = ArrayList<Pair<NxButton, Boolean>>()
        val router = InputRouter(this, { presses += it to false }, initialDelayMs = 400, repeatMs = 90, onRepeat = { presses += it to true })
        router.onEvent(PadEvent.Down(NxButton.RIGHT)); runCurrent()
        advanceTimeBy(400); runCurrent()
        advanceTimeBy(90); runCurrent()
        router.onEvent(PadEvent.Up(NxButton.RIGHT)); runCurrent()
        assertEquals(listOf(NxButton.RIGHT to false, NxButton.RIGHT to true, NxButton.RIGHT to true), presses)
        router.onEvent(PadEvent.Down(NxButton.RIGHT)); runCurrent()   // a new press is fresh again
        assertEquals(NxButton.RIGHT to false, presses.last())
        router.releaseAll()
    }

    // The main menu's SELECT + START: SELECT and START alone act on their release; both held together are MENU.
    @Test fun select_and_start_alone_act_on_release() = runTest {
        val presses = ArrayList<NxButton>()
        val router = InputRouter(this, { presses += it }, menuChord = true)
        router.onEvent(PadEvent.Down(NxButton.SELECT)); runCurrent()
        assertEquals(emptyList(), presses)                       // not yet: it may become the chord
        router.onEvent(PadEvent.Up(NxButton.SELECT)); runCurrent()
        assertEquals(listOf(NxButton.SELECT), presses)
        router.onEvent(PadEvent.Down(NxButton.START)); router.onEvent(PadEvent.Up(NxButton.START)); runCurrent()
        assertEquals(listOf(NxButton.SELECT, NxButton.START), presses)
    }

    @Test fun select_and_start_together_are_menu_once() = runTest {
        val presses = ArrayList<NxButton>()
        val router = InputRouter(this, { presses += it }, menuChord = true)
        router.onEvent(PadEvent.Down(NxButton.START)); router.onEvent(PadEvent.Down(NxButton.SELECT)); runCurrent()
        assertEquals(listOf(NxButton.MENU), presses)
        router.onEvent(PadEvent.Up(NxButton.SELECT)); router.onEvent(PadEvent.Up(NxButton.START)); runCurrent()
        assertEquals(listOf(NxButton.MENU), presses)            // neither release presses anything
    }

    @Test fun without_the_chord_select_and_start_press_on_down() = runTest {
        val presses = ArrayList<NxButton>()
        val router = InputRouter(this, { presses += it })
        router.onEvent(PadEvent.Down(NxButton.START)); runCurrent()
        assertEquals(listOf(NxButton.START), presses)
    }
}
