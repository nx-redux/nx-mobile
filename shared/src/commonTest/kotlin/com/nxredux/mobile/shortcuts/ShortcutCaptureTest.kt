package com.nxredux.mobile.shortcuts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutCaptureTest {
    private val menu = KeyCodes.BUTTON_MODE
    private val select = KeyCodes.BUTTON_SELECT
    private val r1 = KeyCodes.BUTTON_R1

    private fun started() = ShortcutCapture().apply { start() }

    @Test fun singleKeyBindsOnItsPress() {
        val c = started()
        assertEquals(CaptureResult.Bound(ShortcutBinding(null, r1)), c.onKey(r1, true))
        assertFalse(c.active)
    }

    @Test fun extraButtonsBindToo() {
        assertEquals(CaptureResult.Bound(ShortcutBinding(null, KeyCodes.BUTTON_1 + 2)), started().onKey(KeyCodes.BUTTON_1 + 2, true))
    }

    @Test fun menuOrSelectHeldMakesACombo() {
        val c = started()
        assertNull(c.onKey(menu, true))
        assertEquals(CaptureResult.Bound(ShortcutBinding(menu, r1)), c.onKey(r1, true))
        val d = started()
        assertNull(d.onKey(select, true))
        assertEquals(CaptureResult.Bound(ShortcutBinding(select, KeyCodes.BUTTON_X)), d.onKey(KeyCodes.BUTTON_X, true))
    }

    // L23: MENU alone opens the in-game menu, so pressing and releasing it binds nothing and keeps waiting.
    @Test fun aModifierAloneBindsNothing() {
        val c = started()
        assertNull(c.onKey(menu, true)); assertNull(c.onKey(menu, false))
        assertTrue(c.active)
        assertEquals(CaptureResult.Bound(ShortcutBinding(null, r1)), c.onKey(r1, true))   // released: the next key is single
    }

    @Test fun selectStartIsRefused() {
        val c = started()
        c.onKey(select, true)
        assertEquals(CaptureResult.Refused(ShortcutBinding(select, KeyCodes.BUTTON_START), "SELECT+START opens the menu"), c.onKey(KeyCodes.BUTTON_START, true))
        assertFalse(c.active)
    }

    // L23: MENU+SELECT is the handheld's reserved pair; either order is refused.
    @Test fun bothModifiersAreRefused() {
        val c = started()
        c.onKey(menu, true)
        assertEquals(CaptureResult.Refused(ShortcutBinding(menu, select), "MENU+SELECT is reserved"), c.onKey(select, true))
        val d = started()
        d.onKey(select, true)
        assertEquals(CaptureResult.Refused(ShortcutBinding(select, menu), "MENU+SELECT is reserved"), d.onKey(menu, true))
    }

    @Test fun bAndBackCancel() {
        assertEquals(CaptureResult.Cancelled, started().onKey(KeyCodes.BUTTON_B, true))
        assertEquals(CaptureResult.Cancelled, started().onKey(KeyCodes.BACK, true))
        val c = started()
        c.onKey(menu, true)
        assertEquals(CaptureResult.Bound(ShortcutBinding(menu, KeyCodes.BUTTON_B)), c.onKey(KeyCodes.BUTTON_B, true))   // MENU+B is a combo
    }

    @Test fun cancelEndsIt() {
        val c = started()
        c.cancel()
        assertFalse(c.active)
        assertFalse(c.takes(r1, true))
    }

    // The keys still down when it ends are swallowed until released: MENU's release after MENU+R1 must not open anything.
    @Test fun keysHeldPastTheEndStaySwallowedUntilReleased() {
        val c = started()
        c.onKey(menu, true); c.onKey(r1, true)
        assertFalse(c.active)
        assertTrue(c.takes(menu, false)); assertNull(c.onKey(menu, false))
        assertTrue(c.takes(r1, true)); assertNull(c.onKey(r1, false))
        assertFalse(c.takes(menu, false))
    }

    @Test fun theVolumeAndHomeKeysPassThrough() {
        val c = started()
        assertFalse(c.takes(24, true)); assertFalse(c.takes(25, true)); assertFalse(c.takes(3, true))
        assertTrue(c.takes(r1, true))
    }

    // A key held from before the capture started (DOWN scrolling the list, then A): its release is the menu's, or the
    // menu's held-direction repeat would never stop.
    @Test fun aReleaseOfAKeyHeldFromBeforeStartPassesThrough() {
        val c = started()
        assertFalse(c.takes(KeyCodes.DPAD_DOWN, false))
        assertTrue(c.takes(KeyCodes.DPAD_DOWN, true))
    }

    @Test fun idleTakesNothing() {
        assertFalse(ShortcutCapture().takes(r1, true))
    }
}
