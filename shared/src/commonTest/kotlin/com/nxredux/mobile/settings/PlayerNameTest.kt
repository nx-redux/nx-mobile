package com.nxredux.mobile.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerNameTest {
    @Test fun a_saved_name_wins_over_the_devices() {
        assertEquals("Syu", PlayerName.resolve("Syu", "Galaxy Z Fold"))
    }

    @Test fun no_saved_name_uses_the_devices_cut_to_ten() {
        assertEquals("Galaxy Z F", PlayerName.resolve("", "Galaxy Z Fold7"))
        assertEquals("Retroid Po", PlayerName.resolve(null, "Retroid Pocket Nova"))
        assertEquals("Galaxy Z", PlayerName.resolve("   ", "Galaxy Z  Fold"))
    }

    @Test fun neither_falls_back() {
        assertEquals("Player", PlayerName.resolve(null, null))
        assertEquals("Player", PlayerName.resolve(" ", "\n"))
    }

    @Test fun clean_drops_line_breaks_and_trims() {
        assertEquals("AB", PlayerName.clean(" A\nB\t "))
        assertEquals("1234567890", PlayerName.clean("12345678901234"))
    }
}
