package com.nxredux.mobile

import android.view.KeyEvent
import com.nxredux.mobile.ui.nx.NxButton
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardKeysTest {
    @Test fun deleteIsBackspace() = assertEquals(KeyboardKeyAction.Backspace, keyboardKeyAction(KeyEvent.KEYCODE_DEL, 0))

    @Test fun enterIsDoneTabIsNext() {
        assertEquals(KeyboardKeyAction.Press(NxButton.START), keyboardKeyAction(KeyEvent.KEYCODE_ENTER, '\n'.code))
        assertEquals(KeyboardKeyAction.Press(NxButton.START), keyboardKeyAction(KeyEvent.KEYCODE_NUMPAD_ENTER, 0))
        assertEquals(KeyboardKeyAction.Press(NxButton.SELECT), keyboardKeyAction(KeyEvent.KEYCODE_TAB, '\t'.code))
    }

    @Test fun printableTypes() = assertEquals(KeyboardKeyAction.Type("a"), keyboardKeyAction(KeyEvent.KEYCODE_A, 'a'.code))

    @Test fun controlPasses() {
        assertEquals(KeyboardKeyAction.Pass, keyboardKeyAction(KeyEvent.KEYCODE_VOLUME_UP, 0))
        assertEquals(KeyboardKeyAction.Pass, keyboardKeyAction(KeyEvent.KEYCODE_FORWARD_DEL, 127))
    }

    @Test fun ctrlVPastesOtherChordsPass() {
        assertEquals(KeyboardKeyAction.Paste, keyboardKeyAction(KeyEvent.KEYCODE_V, 'v'.code, chord = true))
        assertEquals(KeyboardKeyAction.Pass, keyboardKeyAction(KeyEvent.KEYCODE_A, 'a'.code, chord = true))
    }
}
