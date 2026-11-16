package com.nxredux.mobile.runner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What Save on quit off changes in the game process: the slot a recreated game starts from, and the Wii Menu exit. */
class SaveOnQuitOffTest {
    private fun slot(recreated: Boolean, saved: Int?, launch: Int?, saveOnQuit: Boolean, netplay: Boolean = false) =
        SaveSession.startSlot(recreated, saved, launch, saveOnQuit, netplay)

    @Test fun a_first_launch_takes_the_launch_slot_either_way() {
        assertEquals(2, slot(recreated = false, saved = null, launch = 2, saveOnQuit = false))
        assertEquals(2, slot(recreated = false, saved = null, launch = 2, saveOnQuit = true))
    }

    @Test fun recreated_after_a_kill_without_save_on_quit_starts_fresh() {
        assertNull(slot(recreated = true, saved = null, launch = 2, saveOnQuit = false))
    }

    @Test fun recreated_with_save_on_quit_still_falls_back_to_the_launch_slot() {
        assertEquals(2, slot(recreated = true, saved = null, launch = 2, saveOnQuit = true))
    }

    @Test fun a_slot_still_loading_at_the_kill_is_kept() {
        assertEquals(2, slot(recreated = true, saved = 2, launch = 2, saveOnQuit = false))
    }

    @Test fun netplay_always_starts_cold() {
        assertNull(slot(recreated = true, saved = 9, launch = 2, saveOnQuit = true, netplay = true))
    }

    @Test fun the_wii_menu_exit_keeps_the_old_hidden_state_without_save_on_quit() {
        assertFalse(WiiDolphinController.dropsResumePointOnCoreEnd(homeSaved = false, saveOnQuit = false))
        assertTrue(WiiDolphinController.dropsResumePointOnCoreEnd(homeSaved = false, saveOnQuit = true))
        assertFalse(WiiDolphinController.dropsResumePointOnCoreEnd(homeSaved = true, saveOnQuit = true))
    }
}
