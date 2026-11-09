package com.nxredux.mobile

import android.view.KeyEvent
import com.nxredux.mobile.ui.nx.NxButton

/** What a physical keyboard's key does while the NX keyboard types a prompt. */
sealed interface KeyboardKeyAction {
    data class Type(val text: String) : KeyboardKeyAction
    data object Backspace : KeyboardKeyAction
    data object Paste : KeyboardKeyAction
    data class Press(val button: NxButton) : KeyboardKeyAction
    data object Pass : KeyboardKeyAction
}

/**
 * A physical key ([keyCode], its printable [unicode] or 0) while the NX keyboard types: Backspace deletes, Enter is
 * Done, Tab is Next, a printable character types, the rest passes on (volume, the pad's own keys). With Ctrl, Alt or
 * Meta held ([chord]) only Ctrl+V means something (Paste); a chord never types its letter.
 */
internal fun keyboardKeyAction(keyCode: Int, unicode: Int, chord: Boolean = false): KeyboardKeyAction = if (chord) {
    if (keyCode == KeyEvent.KEYCODE_V) KeyboardKeyAction.Paste else KeyboardKeyAction.Pass
} else when (keyCode) {
    KeyEvent.KEYCODE_DEL -> KeyboardKeyAction.Backspace
    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> KeyboardKeyAction.Press(NxButton.START)
    KeyEvent.KEYCODE_TAB -> KeyboardKeyAction.Press(NxButton.SELECT)
    else -> if (unicode > 31 && unicode != 127) KeyboardKeyAction.Type(String(Character.toChars(unicode))) else KeyboardKeyAction.Pass
}
