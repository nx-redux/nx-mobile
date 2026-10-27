package com.nxredux.mobile.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The unusual A shown on a focused Settings row ([settingsRowAction]); the guessable ones show nothing. */
class SettingsRowActionTest {
    @Test fun unusualActionsAreNamed() {
        assertEquals("Copy", settingsRowAction(SettingsRow(SettingsKind.COPY_INFO, "Copy device info"), SettingsSection.SYSTEM))
        assertEquals("Check", settingsRowAction(SettingsRow(SettingsKind.APP_UPDATE, "Check for updates"), SettingsSection.SYSTEM))
        assertEquals("Reset", settingsRowAction(SettingsRow(SettingsKind.RESET_DEFAULTS, "Reset to defaults"), SettingsSection.APPEARANCE))
        assertEquals("Clear all", settingsRowAction(SettingsRow(SettingsKind.RESET_DEFAULTS, "Clear all"), SettingsSection.SHORTCUTS))
        assertEquals("Remove", settingsRowAction(SettingsRow(SettingsKind.EXTRA, "Roms"), SettingsSection.LIBRARY))
    }

    @Test fun guessableActionsShowNothing() {
        assertNull(settingsRowAction(SettingsRow(SettingsKind.SAVES, "Saves"), SettingsSection.LIBRARY))          // opens a page
        assertNull(settingsRowAction(SettingsRow(SettingsKind.DEBUG_LOG, "Debug log", "Off"), SettingsSection.SYSTEM))   // a switch
        assertNull(settingsRowAction(SettingsRow(SettingsKind.INFO, "Version", "0.5.0"), SettingsSection.SYSTEM))
    }
}
