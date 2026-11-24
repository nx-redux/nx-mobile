package com.nxredux.mobile.skinlayout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnRefusedTest {
    @Test fun landscapeLetterboxedInForcedPortrait() {
        // AYANEO held in portrait: the landscape editor's window sits 960x720 inside a 960x1280 screen
        assertTrue(turnRefused(landscape = true, maxW = 960, maxH = 1280))
    }

    @Test fun portraitLetterboxedInForcedLandscape() {
        assertTrue(turnRefused(landscape = false, maxW = 1280, maxH = 960))
    }

    @Test fun screenTurnedAsAsked() {
        assertFalse(turnRefused(landscape = true, maxW = 1280, maxH = 960))
        assertFalse(turnRefused(landscape = false, maxW = 960, maxH = 1280))
    }

    @Test fun squareOrUnknownAreaRefusesNeither() {
        assertFalse(turnRefused(landscape = true, maxW = 2000, maxH = 2000))
        assertFalse(turnRefused(landscape = false, maxW = 0, maxH = 0))
    }
}
