package com.nxredux.mobile.options

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PspOptionsTest {
    @Test fun local_wireless_is_on_unless_turned_off() {
        assertTrue(PspOptions.localWireless(emptyMap()))
        assertTrue(PspOptions.localWireless(mapOf(PspOptions.LOCAL_WIRELESS to "on")))
        assertFalse(PspOptions.localWireless(mapOf(PspOptions.LOCAL_WIRELESS to "off")))
    }

    @Test fun applies_to_the_psp_tag_only() {
        assertTrue(PspOptions.appliesTo("PSP"))
        assertTrue(PspOptions.appliesTo(" psp "))
        assertFalse(PspOptions.appliesTo("3DS"))
        assertFalse(PspOptions.appliesTo("NDS"))
    }

    @Test fun the_psp_console_settings_carry_the_row_and_other_consoles_do_not() {
        assertTrue(PspOptions.localWirelessRow in ConsoleSettings.tableFor("PSP"))
        assertTrue(PspOptions.localWirelessRow in ConsoleSettings.all)
        assertFalse(PspOptions.localWirelessRow in ConsoleSettings.tableFor("NDS"))
        assertFalse(PspOptions.localWirelessRow in ConsoleSettings.tableFor("SFC"))
        assertEquals("Local Wireless", PspOptions.localWirelessRow.label)
    }
}
