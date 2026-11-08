package com.nxredux.mobile.ui.nx

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The DS / 3DS mic button in the screen's bottom-left corner ([micCornerShown]): only where no pad draws its own MIC. */
class MicCornerTest {
    private val mic = PadMic(on = false, onTap = {})

    @Test fun shownWithAMicAndNoPadDrawn() {   // a controller attached: no skin, no skin buttons (user)
        assertTrue(micCornerShown(mic, padDrawn = false))
    }

    @Test fun hiddenUnderAPadWithItsOwnMicButton() {
        assertFalse(micCornerShown(mic, padDrawn = true))
    }

    @Test fun hiddenWithoutAMic() {   // not a DS / 3DS, or netplay
        assertFalse(micCornerShown(null, padDrawn = false))
    }
}
