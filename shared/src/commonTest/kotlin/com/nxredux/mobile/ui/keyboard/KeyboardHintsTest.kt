package com.nxredux.mobile.ui.keyboard

import com.nxredux.mobile.ui.nx.NxButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyboardHintsTest {
    private val entry = TextEntry(listOf(EntryField("Username", ""), EntryField("Password", "", password = true)))

    @Test fun keysHints() {
        val hints = keyboardHints(entry, "Sign in")
        // START and B are on the prompt's own words, not in the row.
        assertEquals(listOf(NxButton.A, NxButton.X, NxButton.Y), hints.map { it.button })
        assertEquals(listOf("Type", "Delete", "Space"), hints.map { it.label })
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

    @Test fun passwordFieldHintsShowOrHide() {
        val onPassword = entry.copy(active = 1, cursor = 0, focus = EntryFocus.Field)
        assertEquals("Show", keyboardHints(onPassword, "Sign in").single { it.button == NxButton.A }.label)
        assertEquals("Hide", keyboardHints(onPassword.copy(reveal = true), "Sign in").single { it.button == NxButton.A }.label)
        assertTrue(keyboardHints(entry.copy(focus = EntryFocus.Field), "Sign in").none { it.button == NxButton.A })
    }
}
