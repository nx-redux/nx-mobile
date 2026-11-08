package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * No controller and no pad on screen (the full-screen menu of a game without a skin): the hint bar keeps only the hints
 * a tap cannot stand in for ([touchOnlyHints]); everything else is a tap on the row, or Back.
 */
class InGameTouchHintsTest {
    private val root = MenuPage(PageKind.Root, "", emptyList())

    @Test fun rowHintsGoOnTouch() {
        val hints = listOf(Hint(NxButton.A, "Select"), Hint(NxButton.B, "Resume"))
        assertEquals(emptyList<Hint>(), touchOnlyHints(root, hints))
        val quit = listOf(Hint(NxButton.A, "Save & Quit"), Hint(NxButton.X, "Quit"), Hint(NxButton.B, "Resume"))
        assertEquals(emptyList<Hint>(), touchOnlyHints(root, quit))   // a tap on Quit opens the dialog with both
    }

    @Test fun achievementsKeepTheirFilterAndMute() {
        val page = MenuPage(PageKind.Achievements, "Achievements", emptyList())
        val hints = listOf(Hint(NxButton.Y, "Locked only"), Hint(NxButton.X, "Mute"), Hint(NxButton.B, "Back"))
        assertEquals(listOf(Hint(NxButton.Y, "Locked only"), Hint(NxButton.X, "Mute")), touchOnlyHints(page, hints))
    }

    @Test fun shortcutsKeepNoneAndDefault() {
        val page = MenuPage(PageKind.Shortcuts, "Shortcuts", emptyList())
        val hints = listOf(Hint(NxButton.LEFT, "Change"), Hint(NxButton.X, "None"), Hint(NxButton.Y, "Default"), Hint(NxButton.B, "Back"))
        assertEquals(listOf(Hint(NxButton.X, "None"), Hint(NxButton.Y, "Default")), touchOnlyHints(page, hints))
    }
}
