package com.nxredux.mobile.runner

import com.nxredux.mobile.shortcuts.HatKeys
import com.nxredux.mobile.shortcuts.KeyCodes

/**
 * P1's hat through [ShortcutKeys], as its d-pad keys would go: MENU + a direction fires its shortcut. A direction that
 * fired stays out of the core until the hat lets it go ([x], [y] read 0 on its axis), as a fired key's release does.
 * Each edge's verdict goes to [act], with its key code.
 */
internal class HatShortcuts(private val act: (KeyVerdict, Int) -> Unit) {
    private val hat = HatKeys()
    private var heldX = false
    private var heldY = false

    /** The hat now reads ([hatX], [hatY]). */
    fun update(hatX: Float, hatY: Float, keys: ShortcutKeys) {
        hat.update(hatX, hatY) { code, down ->
            val verdict = if (down) keys.onDown(code) else keys.onUp(code)
            // Swallow: a pad that sends the d-pad as keys too fired it on the key first.
            val held = down && (verdict is KeyVerdict.Fire || verdict == KeyVerdict.Swallow)
            if (code == KeyCodes.DPAD_LEFT || code == KeyCodes.DPAD_RIGHT) heldX = held else heldY = held
            act(verdict, code)
        }
    }

    /** [hatX] as the core gets it: 0 while a fired direction on that axis is held. */
    fun x(hatX: Float): Float = if (heldX) 0f else hatX
    fun y(hatY: Float): Float = if (heldY) 0f else hatY

    /** [ShortcutKeys.releaseAll]: the hat's releases go to the menu now, so nothing stays held back or read as held. */
    fun releaseAll() { heldX = false; heldY = false; hat.reset() }
}
