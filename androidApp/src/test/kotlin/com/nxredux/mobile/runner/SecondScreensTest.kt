package com.nxredux.mobile.runner

import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondScreensTest {
    private fun facts(type: Int?, on: Boolean = true, flags: Int = 0, name: String = "Display") =
        DisplayFacts(id = 2, type = type, flags = flags, on = on, name = name)

    @Test fun internal_on_qualifies() = assertTrue(SecondScreenRule.qualifies(facts(DisplayFacts.INTERNAL), false))

    @Test fun external_qualifies() = assertTrue(SecondScreenRule.qualifies(facts(DisplayFacts.EXTERNAL), false))

    @Test fun wifi_never() {
        assertFalse(SecondScreenRule.qualifies(facts(DisplayFacts.WIFI), false))
        assertFalse(SecondScreenRule.qualifies(facts(DisplayFacts.WIFI), true))
    }

    @Test fun overlay_debug_only() {
        assertFalse(SecondScreenRule.qualifies(facts(DisplayFacts.OVERLAY), false))
        assertTrue(SecondScreenRule.qualifies(facts(DisplayFacts.OVERLAY), true))
    }

    @Test fun virtual_debug_only() {
        assertFalse(SecondScreenRule.qualifies(facts(DisplayFacts.VIRTUAL), false))
        assertTrue(SecondScreenRule.qualifies(facts(DisplayFacts.VIRTUAL), true))
    }

    @Test fun off_cover_screen_rejected() =
        assertFalse(SecondScreenRule.qualifies(facts(DisplayFacts.INTERNAL, on = false), false))

    @Test fun private_rejected() =
        assertFalse(SecondScreenRule.qualifies(facts(DisplayFacts.EXTERNAL, flags = Display.FLAG_PRIVATE), false))

    @Test fun unknown_type_wifi_name_rejected() {
        assertFalse(SecondScreenRule.qualifies(facts(null, name = "Wifi display"), false))
        assertFalse(SecondScreenRule.qualifies(facts(null, name = "Chromecast CAST"), false))
        assertTrue(SecondScreenRule.qualifies(facts(null, name = "Built-in Screen"), false))
    }

    // Onboarding's Bottom screen step: only a built-in (INTERNAL) second screen; attachments and monitors never.
    @Test fun only_an_internal_display_is_built_in() {
        assertTrue(SecondScreenRule.builtIn(facts(DisplayFacts.INTERNAL), debugInternal = false))
        assertFalse(SecondScreenRule.builtIn(facts(DisplayFacts.EXTERNAL), debugInternal = false))
        assertFalse(SecondScreenRule.builtIn(facts(null, name = "Built-in Screen"), debugInternal = false))
        assertFalse(SecondScreenRule.builtIn(facts(DisplayFacts.OVERLAY), debugInternal = false))
        assertFalse(SecondScreenRule.builtIn(facts(DisplayFacts.INTERNAL, on = false), debugInternal = false))
    }

    // A debug build's switch lets the emulator's simulated display stand in for a built-in one.
    @Test fun the_debug_switch_counts_simulated_displays_as_built_in() {
        assertTrue(SecondScreenRule.builtIn(facts(DisplayFacts.OVERLAY), debugInternal = true))
        assertTrue(SecondScreenRule.builtIn(facts(DisplayFacts.VIRTUAL), debugInternal = true))
        assertFalse(SecondScreenRule.builtIn(facts(DisplayFacts.EXTERNAL), debugInternal = true))
    }

    private fun at(id: Int, type: Int? = DisplayFacts.EXTERNAL) = DisplayFacts(id = id, type = type, flags = 0, on = true, name = "Display $id")
    private val main = at(Display.DEFAULT_DISPLAY, DisplayFacts.INTERNAL)

    // The game on the phone's own screen: the first qualifying other display.
    @Test fun on_the_default_display_the_first_qualifying_other_display_is_picked() {
        assertEquals(3, SecondScreenRule.pick(listOf(at(2, DisplayFacts.WIFI), at(3)), main, hostId = Display.DEFAULT_DISPLAY, debug = false))
        assertNull(SecondScreenRule.pick(emptyList(), main, hostId = Display.DEFAULT_DISPLAY, debug = false))
    }

    // The game moved to a monitor (DeX, a desktop window): that monitor is never its own second screen.
    @Test fun the_games_own_display_is_never_its_second_screen() {
        assertNull(SecondScreenRule.pick(listOf(at(2)), null, hostId = 2, debug = false))
        assertEquals(3, SecondScreenRule.pick(listOf(at(2), at(3)), main, hostId = 2, debug = false))
    }

    // The game on a monitor: the phone's own screen is the free one.
    @Test fun off_the_default_display_the_default_display_is_offered() {
        assertEquals(Display.DEFAULT_DISPLAY, SecondScreenRule.pick(listOf(at(2)), main, hostId = 2, debug = false))
        // An off default display does not qualify.
        assertNull(SecondScreenRule.pick(listOf(at(2)), main.copy(on = false), hostId = 2, debug = false))
    }
}
