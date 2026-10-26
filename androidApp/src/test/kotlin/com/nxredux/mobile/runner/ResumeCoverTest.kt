package com.nxredux.mobile.runner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeCoverTest {
    // Our own screenshot: the game view scaled down to at most 1280 on its long side (SaveStates.thumbSize).
    @Test fun aScreenshotOfThisViewFillsIt() {
        assertTrue(ResumeCover.fillsView(1280, 576, 2400, 1080))
        assertTrue(ResumeCover.fillsView(763, 1280, 1080, 1812))
        assertTrue(ResumeCover.fillsView(1080, 1240, 1080, 1240))
    }

    // Another shape (taken on another screen, in the other orientation, on the handheld): fitted inside, not stretched.
    @Test fun aScreenshotOfAnotherShapeIsFittedInside() {
        assertFalse(ResumeCover.fillsView(1280, 720, 2400, 1080))   // the handheld's 16:9 on a 20:9 phone
        assertFalse(ResumeCover.fillsView(576, 1280, 2400, 1080))   // taken in portrait, resumed in landscape
    }

    @Test fun anEmptySizeNeverFills() {
        assertFalse(ResumeCover.fillsView(0, 0, 1080, 1240))
        assertFalse(ResumeCover.fillsView(1080, 1240, 0, 0))
    }
}
