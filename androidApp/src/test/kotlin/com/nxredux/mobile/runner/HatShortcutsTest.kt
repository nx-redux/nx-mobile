package com.nxredux.mobile.runner

import com.nxredux.mobile.shortcuts.ConsoleHotkeys
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutBinding
import com.nxredux.mobile.shortcuts.ShortcutOverrides
import com.nxredux.mobile.shortcuts.ShortcutSet
import com.nxredux.mobile.shortcuts.Shortcuts
import org.junit.Assert.assertEquals
import org.junit.Test

class HatShortcutsTest {
    private val menu = KeyCodes.BUTTON_MODE
    private val keys = ShortcutKeys(
        Shortcuts.effective(
            ShortcutSet(mapOf(ShortcutAction.TOGGLE_FF to ShortcutBinding(menu, KeyCodes.DPAD_RIGHT)), emptyMap()),
            ShortcutOverrides.NONE, ConsoleHotkeys.claimed("GBA"), canRewind = true,
        ),
        ConsoleHotkeys.claimed("GBA"),
        now = { 0L },
    )
    private val verdicts = mutableListOf<KeyVerdict>()
    private val hat = HatShortcuts { v, _ -> verdicts += v }

    @Test fun menuAndADirectionFiresAndHoldsItBackFromTheCore() {
        keys.onDown(menu)
        hat.update(1f, 0f, keys)
        assertEquals(listOf(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, true)), verdicts)
        assertEquals(0f, hat.x(1f))
        hat.update(0f, 0f, keys)
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, false), verdicts.last())
        assertEquals(1f, hat.x(1f))
    }

    @Test fun aDirectionAlonePassesToTheCore() {
        hat.update(1f, 0f, keys)
        assertEquals(listOf<KeyVerdict>(KeyVerdict.Pass), verdicts)
        assertEquals(1f, hat.x(1f))
    }

    @Test fun anotherAxisStillReachesTheCore() {
        keys.onDown(menu)
        hat.update(1f, 1f, keys)
        assertEquals(0f, hat.x(1f))
        assertEquals(1f, hat.y(1f))
    }

    // A pad that sends the d-pad as keys and as the hat: the key fired first, so the hat's Right stays out of the core too.
    @Test fun aDirectionFiredByItsKeyIsHeldBackOnTheHat() {
        keys.onDown(menu)
        keys.onDown(KeyCodes.DPAD_RIGHT)
        hat.update(1f, 0f, keys)
        assertEquals(0f, hat.x(1f))
        keys.onUp(KeyCodes.DPAD_RIGHT)
        hat.update(0f, 0f, keys)
        assertEquals(1f, hat.x(1f))
    }

    // The menu took the hat's release: the next MENU+Right is a press again.
    @Test fun releaseAllForgetsTheHat() {
        hat.update(1f, 0f, keys)
        hat.releaseAll()
        keys.onDown(menu)
        hat.update(1f, 0f, keys)
        assertEquals(KeyVerdict.Fire(ShortcutAction.TOGGLE_FF, true), verdicts.last())
    }
}
