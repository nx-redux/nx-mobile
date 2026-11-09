package com.nxredux.mobile.ui.keyboard

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyboardHintsTest {
    private val entry = TextEntry(listOf(EntryField("Username", ""), EntryField("Password", "", password = true)))

    @Test fun keysHints() {
        val hints = keyboardHints(entry, "Sign in")
        assertEquals(listOf(NxButton.A, NxButton.B, NxButton.Y, NxButton.X, NxButton.START, NxButton.MENU), hints.map { it.button })
        assertEquals(listOf("Type", "Delete", "Space", "Shift", "Done", "Cancel"), hints.map { it.label })
    }

    @Test fun fieldHints() {
        val hints = keyboardHints(entry.copy(focus = EntryFocus.Field), "Sign in")
        assertEquals("Move cursor", hints.first().label)
        assertTrue(hints.first().button == NxButton.LEFT || hints.first().button == NxButton.RIGHT)
        assertTrue(hints.none { it.label == "Type" })
    }

    @Test fun buttonHints() {
        assertEquals("Sign in", keyboardHints(entry.copy(focus = EntryFocus.Button(0)), "Sign in").single { it.button == NxButton.A }.label)
        assertEquals("Cancel", keyboardHints(entry.copy(focus = EntryFocus.Button(1)), "Sign in").single { it.button == NxButton.A }.label)
    }
}
