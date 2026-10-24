package com.nxredux.mobile.runner

import com.nxredux.mobile.shortcuts.ClaimedInput
import com.nxredux.mobile.shortcuts.EffectiveShortcuts
import com.nxredux.mobile.shortcuts.KeyCodes
import com.nxredux.mobile.shortcuts.ShortcutAction
import com.nxredux.mobile.shortcuts.ShortcutBinding

/** What [ShortcutKeys] makes of a key: on to the game ([Pass]), dropped ([Swallow]), a shortcut, or the menu's MENU tap. */
sealed interface KeyVerdict {
    data object Pass : KeyVerdict
    data object Swallow : KeyVerdict
    data class Fire(val action: ShortcutAction, val down: Boolean) : KeyVerdict
    data object OpenMenu : KeyVerdict
}

/**
 * The game's shortcut bindings over raw key codes (as press-to-bind captured them). A key that triggers a binding, alone
 * or with MENU held, fires it and never reaches the core, its release included, even when MENU let go first (L22:
 * MENU+Y must not also zoom the N64 camera). MENU is the only combo modifier ([ShortcutBinding.MODIFIERS]); SELECT is
 * the game's. MENU still opens the menu, except that with a MENU combo bound ([holdsMenu]) MENU waits for its release
 * and opens the menu only on a tap: released within [MENU_TAP_MS] ([now]) with no combo fired while it was held. Built-in
 * hotkeys never match: [EffectiveShortcuts.bindings] leaves them out ([EffectiveShortcuts.blocked]), and a key bound
 * alone stays the console's while its built-in chord's modifier is held ([claimed]: SELECT+R1 still swaps the 3DS
 * screens with R1 bound alone). A fired key's repeats are swallowed, never fired again.
 */
class ShortcutKeys(private val effective: EffectiveShortcuts, claimed: List<ClaimedInput>, private val now: () -> Long) {
    /** The built-in chords (`ConsoleHotkeys.claimed`), which a key bound alone must not take from the console. */
    private val chords: Set<ShortcutBinding> = claimed.mapNotNullTo(HashSet()) { c -> c.binding.takeIf { it.modifier != null } }
    /** Those chords' modifiers (SELECT): tracked while held, though they pass and make no combo of their own. */
    private val chordModifiers: Set<Int> = chords.mapNotNullTo(HashSet()) { it.modifier }

    /** A MENU combo is bound: MENU is held back until its release ([KeyVerdict.OpenMenu]). */
    val holdsMenu: Boolean = effective.bindings.values.any { it.modifier == KeyCodes.BUTTON_MODE }

    private val held = HashSet<Int>()                       // modifiers and chord modifiers down now
    private val fired = HashMap<Int, ShortcutAction>()     // trigger keys down now, and what their release ends
    private var menuDown = false
    private var menuComboFired = false
    private var menuDownAt = 0L

    /** [repeat]: a key repeat, which fires nothing of its own (a key held before MENU went down stays the game's). */
    fun onDown(keyCode: Int, repeat: Boolean = false): KeyVerdict {
        if (keyCode in fired) return KeyVerdict.Swallow
        if (repeat) return if (keyCode == KeyCodes.BUTTON_MODE && holdsMenu) KeyVerdict.Swallow else KeyVerdict.Pass
        if (keyCode == KeyCodes.BUTTON_MODE && holdsMenu) {
            if (!menuDown) { menuDown = true; menuComboFired = false; menuDownAt = now() }
            return KeyVerdict.Swallow
        }
        if (keyCode in ShortcutBinding.MODIFIERS || keyCode in chordModifiers) { held += keyCode; return KeyVerdict.Pass }
        // A combo with MENU held wins over the same key bound alone.
        val hit = effective.bindings.entries.firstOrNull { (_, b) -> b.keyCode == keyCode && b.modifier?.let(::comboHeld) == true }
            ?: effective.bindings.entries.firstOrNull { (_, b) -> b.keyCode == keyCode && b.modifier == null }
                ?.takeUnless { chords.any { c -> c.keyCode == keyCode && c.modifier?.let(::modifierHeld) == true } }
            ?: return KeyVerdict.Pass
        fired[keyCode] = hit.key
        if (hit.value.modifier == KeyCodes.BUTTON_MODE) menuComboFired = true
        return KeyVerdict.Fire(hit.key, true)
    }

    fun onUp(keyCode: Int): KeyVerdict {
        fired.remove(keyCode)?.let { return KeyVerdict.Fire(it, false) }
        if (keyCode == KeyCodes.BUTTON_MODE && holdsMenu) {
            val tap = menuDown && !menuComboFired && now() - menuDownAt < MENU_TAP_MS
            menuDown = false
            return if (tap) KeyVerdict.OpenMenu else KeyVerdict.Swallow
        }
        held -= keyCode
        return KeyVerdict.Pass
    }

    /** The menu or the focus took the buttons, so their releases never come here: nothing is held from now. */
    fun releaseAll() {
        held.clear()
        fired.clear()
        menuDown = false
        menuComboFired = false
    }

    private fun modifierHeld(m: Int): Boolean = if (m == KeyCodes.BUTTON_MODE) menuDown else m in held

    /** A combo's modifier is held, and it is one ([ShortcutBinding.MODIFIERS]): a SELECT combo built in code never fires. */
    private fun comboHeld(m: Int): Boolean = m in ShortcutBinding.MODIFIERS && modifierHeld(m)

    companion object {
        /** The longest MENU press that still opens the menu with a MENU combo bound: a longer hold opens nothing. */
        const val MENU_TAP_MS = 400L
    }
}
