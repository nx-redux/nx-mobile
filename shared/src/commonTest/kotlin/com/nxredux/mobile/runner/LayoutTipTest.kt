package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tip that SELECT + Left / Right changes the 3DS screen layout ([LayoutTip.due], user): each launch until hidden. */
class LayoutTipTest {
    @Test fun dueOnAFirst3dsGameWhereTheChordWorks() {
        assertTrue(LayoutTip.due(n3ds = true, skinShown = false, skinLayouts = false, hidden = false))   // no skin
        assertTrue(LayoutTip.due(n3ds = true, skinShown = true, skinLayouts = true, hidden = false))     // a regular phone's landscape skin
    }

    @Test fun notWhereTheChordDoesNothingOrOnceShown() {
        assertFalse(LayoutTip.due(n3ds = true, skinShown = true, skinLayouts = false, hidden = false))   // the Fold's skin places the screens
        assertFalse(LayoutTip.due(n3ds = true, skinShown = false, skinLayouts = false, hidden = true))   // hidden for good from its button
        assertFalse(LayoutTip.due(n3ds = false, skinShown = false, skinLayouts = false, hidden = false)) // not a 3DS game
    }

    @Test fun eachConsoleNamesItsOwnSwap() {   // the 3DS's SELECT + R, the DS's R2 (user: the swap in the same tip)
        assertTrue("SELECT + R" in LayoutTip.text(ds = false))
        assertTrue("R2" in LayoutTip.text(ds = true))
        assertTrue(LayoutTip.text(ds = true).startsWith("SELECT + Left / Right"))
    }
}
