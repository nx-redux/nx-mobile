package com.nxredux.mobile.runner

import com.nxredux.mobile.shortcuts.ConsoleHotkeys
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutBinding
import com.nxredux.mobile.shortcuts.ShortcutOverrides
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.Shortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutKeysTest {
    private val r1 = KeyCodes.BUTTON_R1
    private val select = KeyCodes.BUTTON_SELECT
    private val menu = KeyCodes.BUTTON_MODE

    private var clock = 0L

    private fun keys(vararg bound: Pair<ShortcutAction, ShortcutBinding>, tag: String = "GBA") = ShortcutKeys(
        Shortcuts.effective(ShortcutSet(bound.toMap(), emptyMap()), ShortcutOverrides.NONE, ConsoleHotkeys.claimed(tag), canRewind = true),
        ConsoleHotkeys.claimed(tag),
        now = { clock },
    )

    @Test fun singleKeyFires() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, r1))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, true), k.onDown(103))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, false), k.onUp(103))
    }

    @Test fun comboFires() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(menu, r1))
        assertEquals(KeyVerdict.Swallow, k.onDown(menu))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, true), k.onDown(103))
        // The modifier let go first: the trigger's release is still the shortcut's (L22), never the game's.
        assertEquals(KeyVerdict.Swallow, k.onUp(menu))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, false), k.onUp(103))
    }

    @Test fun comboKeyAlonePasses() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(menu, r1))
        assertEquals(KeyVerdict.Pass, k.onDown(r1))
        assertEquals(KeyVerdict.Pass, k.onUp(r1))
    }

    @Test fun comboWinsOverSingleOnTheSameKey() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, r1), ShortcutAction.RESET to ShortcutBinding(menu, r1))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, true), k.onDown(r1))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, false), k.onUp(r1))
        k.onDown(menu)
        assertEquals(KeyVerdict.Fire(ShortcutAction.RESET, true), k.onDown(r1))
    }

    // MENU is the only combo modifier: a SELECT combo (one built in code; a stored one decodes to nothing) never fires,
    // SELECT and the key both reach the game.
    @Test fun aSelectComboNeverFires() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(select, r1))
        assertEquals(KeyVerdict.Pass, k.onDown(select))
        assertEquals(KeyVerdict.Pass, k.onDown(r1))
        assertEquals(KeyVerdict.Pass, k.onUp(r1))
    }

    @Test fun repeatOfAFiredKeyIsSwallowed() {
        val k = keys(ShortcutAction.HOLD_FF to ShortcutBinding(null, r1))
        k.onDown(r1)
        assertEquals(KeyVerdict.Swallow, k.onDown(r1, repeat = true))
    }

    @Test fun repeatOfAKeyHeldBeforeTheModifierStaysTheGames() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(menu, r1))
        assertEquals(KeyVerdict.Pass, k.onDown(r1))
        k.onDown(menu)
        assertEquals(KeyVerdict.Pass, k.onDown(r1, repeat = true))
    }

    @Test fun menuComboDefersMenu() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(menu, r1))
        assertTrue(k.holdsMenu)
        assertEquals(KeyVerdict.Swallow, k.onDown(menu))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, true), k.onDown(r1))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, false), k.onUp(r1))
        assertEquals(KeyVerdict.Swallow, k.onUp(menu))
    }

    @Test fun menuTapOpensMenu() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(menu, r1))
        clock = 1_000
        assertEquals(KeyVerdict.Swallow, k.onDown(menu))
        assertEquals(KeyVerdict.Swallow, k.onDown(menu, repeat = true))
        clock = 1_399
        assertEquals(KeyVerdict.OpenMenu, k.onUp(menu))
    }

    // A held MENU (no combo pressed) is not a tap: it opens nothing.
    @Test fun aHeldMenuOpensNoMenu() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(menu, r1))
        clock = 1_000
        k.onDown(menu)
        clock = 1_500
        assertEquals(KeyVerdict.Swallow, k.onUp(menu))
    }

    @Test fun noMenuComboKeepsMenu() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, r1))
        assertFalse(k.holdsMenu)
        assertEquals(KeyVerdict.Pass, k.onDown(menu))
    }

    @Test fun blockedNeverFires() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, KeyCodes.BUTTON_R2), tag = "NDS")
        assertEquals(KeyVerdict.Pass, k.onDown(KeyCodes.BUTTON_R2))
        assertEquals(KeyVerdict.Pass, k.onUp(KeyCodes.BUTTON_R2))
    }

    @Test fun releaseAllForgetsHeldKeys() {
        val k = keys(ShortcutAction.HOLD_FF to ShortcutBinding(menu, r1))
        k.onDown(menu); k.onDown(r1)
        k.releaseAll()   // the menu took the buttons: their releases go to it
        assertEquals(KeyVerdict.Pass, k.onUp(r1))
        assertEquals(KeyVerdict.Pass, k.onDown(r1))   // MENU no longer held
        assertEquals(KeyVerdict.Swallow, k.onUp(menu))   // a MENU release with no press seen opens nothing
    }

    @Test fun builtInChordBeatsAKeyBoundAlone3ds() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, r1), tag = "3DS")
        assertEquals(KeyVerdict.Pass, k.onDown(select))
        // SELECT+R1 swaps the 3DS screens: R1 is the console's while SELECT is held, its release too.
        assertEquals(KeyVerdict.Pass, k.onDown(r1))
        assertEquals(KeyVerdict.Pass, k.onUp(r1))
        k.onUp(select)
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, true), k.onDown(r1))
    }

    @Test fun builtInChordBeatsADirectionBoundAloneDs() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, KeyCodes.DPAD_UP), tag = "NDS")
        k.onDown(select)
        assertEquals(KeyVerdict.Pass, k.onDown(KeyCodes.DPAD_UP))
        assertEquals(KeyVerdict.Pass, k.onUp(KeyCodes.DPAD_UP))
    }

    @Test fun noChordClaimedKeepsTheKeyBoundAlone() {
        val k = keys(ShortcutAction.TOGGLE_FF to ShortcutBinding(null, r1), tag = "GB")
        k.onDown(select)
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, true), k.onDown(r1))
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, false), k.onUp(r1))
    }
}
