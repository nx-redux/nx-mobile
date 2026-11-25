package com.nxredux.mobile.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The tip that SELECT + Left / Right changes the 3DS screen layout ([LayoutTip.due]): each launch until hidden. */
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

    @Test fun namesTheLayoutChordAndTheSwap() {
        assertTrue(LayoutTip.TEXT.startsWith("SELECT + Left / Right"))
        assertTrue("L3 or SELECT + L swaps" in LayoutTip.TEXT)
    }

    @Test fun theWiiuTipNamesItsChordsWithoutL3() {
        assertEquals("SELECT + Left / Right changes the screen layout\nSELECT + L swaps the screens", LayoutTip.WIIU_TEXT)
        assertEquals(LayoutTip.WIIU_TEXT, LayoutTip.text(wiiu = true))
        assertEquals(LayoutTip.TEXT, LayoutTip.text(wiiu = false))
    }

    @Test fun theWiiuTipHasItsOwnHiddenFlag() {
        assertFalse(LayoutTip.hidden(wiiu = true, layoutTipHidden = true, layoutTipHiddenWiiu = false)) // a dismissed DS tip leaves the Wii U one
        assertTrue(LayoutTip.hidden(wiiu = true, layoutTipHidden = false, layoutTipHiddenWiiu = true))
        assertTrue(LayoutTip.hidden(wiiu = false, layoutTipHidden = true, layoutTipHiddenWiiu = false))
        assertFalse(LayoutTip.hidden(wiiu = false, layoutTipHidden = false, layoutTipHiddenWiiu = true)) // the Wii U flag leaves the DS / 3DS one
    }
}
