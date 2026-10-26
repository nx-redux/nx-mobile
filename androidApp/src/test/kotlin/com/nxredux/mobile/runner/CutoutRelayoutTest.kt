package com.nxredux.mobile.runner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A top cutout inset arriving after a layout rebuilds the pads only when the layout it gave would differ. */
class CutoutRelayoutTest {
    @Test fun android_9_10_portrait_rebuilds_only_for_a_new_band() {
        // A rotation to portrait laid out from the configuration change; the inset then leaves the band as it was.
        assertFalse(cutoutNeedsRelayout(sdk = 29, portrait = true, tabletop = false, builtBandPx = 812, bandPx = 812))
        assertFalse(cutoutNeedsRelayout(sdk = 28, portrait = true, tabletop = false, builtBandPx = 812, bandPx = 812))
        // The band read before the inset was known (onCreate, or a phone whose band follows the area): built again.
        assertTrue(cutoutNeedsRelayout(sdk = 29, portrait = true, tabletop = false, builtBandPx = 812, bandPx = 790))
    }

    @Test fun android_11_up_and_landscape_only_move_the_game() {
        assertFalse(cutoutNeedsRelayout(sdk = 30, portrait = true, tabletop = false, builtBandPx = 812, bandPx = 790))
        assertFalse(cutoutNeedsRelayout(sdk = 29, portrait = false, tabletop = false, builtBandPx = 812, bandPx = 790))
    }

    @Test fun tabletop_always_rebuilds() {
        assertTrue(cutoutNeedsRelayout(sdk = 34, portrait = true, tabletop = true, builtBandPx = 812, bandPx = 812))
    }
}
